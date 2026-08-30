package com.zombiesurvival.client.ui;

import com.zombiesurvival.client.SoundManager;
import com.zombiesurvival.shared.*;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main rendering canvas — 960 × 704 px.
 *
 * Visual features (in render order):
 *  1. Tile pass  — pre-rendered grass / brick-wall / safe-zone textures
 *  2. 3-D wall faces  — bottom and right depth faces on every exposed wall edge
 *  3. Ambient-occlusion shadows under walls
 *  4. Decorative trees & crates
 *  5. Pickups with glow and bob animation
 *  6. Direction-aware player sprites (head tracks movement)
 *  7. Particles  — blood / sparkle / infection burst
 *  8. Dynamic lighting overlay  — darkness with per-player light radii
 *  9. Minimap (corner)
 * 10. Zombie red-vignette
 * 11. Lobby / game-over overlays
 */
public class GameCanvas extends JPanel {

    // ── Canvas / tile constants ───────────────────────────────────────────────
    private static final int W   = GameState.CANVAS_W;    // 960
    private static final int H   = GameState.CANVAS_H;    // 704
    private static final int TS  = GameState.TILE_SIZE;   // 32
    private static final int MW  = GameState.MAP_WIDTH;   // 30
    private static final int MH  = GameState.MAP_HEIGHT;  // 22

    // ── Decorative positions [col, row] — verified grass on 30×22 map ────────
    private static final int[][] TREES = {
        { 7, 3},{21, 3},{14, 4},
        { 1,11},{28,11},
        { 9,11},{19,11},
        {14,19},{ 9,18},{19,18}
    };
    private static final int[][] CRATES = {
        { 9, 4},{20, 4},{ 1, 7},
        { 9,19},{20,19},{28,15}
    };

    // ── Tile textures (pre-rendered once) ────────────────────────────────────
    private final BufferedImage tileGrassA, tileGrassB, tileWall, tileSafe;

    // ── State ─────────────────────────────────────────────────────────────────
    private volatile GameState  gameState;
    private volatile GameState  prevState;
    private volatile String     myPlayerId;
    private int                 animTick = 0;

    // Facing angle per player
    private final Map<String, Double>  facingAngles  = new ConcurrentHashMap<>();
    private final Map<String, float[]> lastPositions = new ConcurrentHashMap<>();

    // ── Particles ─────────────────────────────────────────────────────────────
    // Format: [x, y, vx, vy, life, maxLife, r, g, b, size, type]
    private final List<float[]> particles = new ArrayList<>();

    // Thread-safe event queue (populated from network thread, drained on EDT)
    // Format: [x, y, eventType]  types: 0=blood, 1=medkit-spark, 2=pickup-spark, 3=infection
    private final ConcurrentLinkedQueue<float[]> particleEvents = new ConcurrentLinkedQueue<>();

    // ── Screen shake ──────────────────────────────────────────────────────────
    private final AtomicInteger shakeRequest = new AtomicInteger(0);
    private int shakeFrames = 0, shakeIntensity = 3;

    // ── Dynamic lighting ──────────────────────────────────────────────────────
    private BufferedImage lightMap;

    // ── Sound trigger tracking ────────────────────────────────────────────────
    private int lastTimeRemaining = Integer.MAX_VALUE;
    private boolean warningPlayed = false;

    // ═══════════════════════════════════════════════════════════════════════════
    //  Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    public GameCanvas() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(5, 10, 5));
        tileGrassA = makeTileGrass(0);
        tileGrassB = makeTileGrass(1);
        tileWall   = makeTileWall();
        tileSafe   = makeTileSafe();

        new javax.swing.Timer(33, e -> {
            animTick++;
            drainParticleEvents();
            updateParticles();
            handleSoundTriggers();
            repaint();
        }).start();
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public void setMyPlayerId(String id) { this.myPlayerId = id; }

    public void setGameState(GameState next) {
        if (next != null) {
            detectEvents(gameState, next);
            trackFacing(next);
        }
        this.prevState = this.gameState;
        this.gameState = next;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Event detection (network thread → EDT queue)
    // ═══════════════════════════════════════════════════════════════════════════

    private void detectEvents(GameState prev, GameState next) {
        if (prev == null) return;

        // Player damage / infection
        for (Player np : next.getPlayers().values()) {
            Player pp = prev.getPlayer(np.getId());
            if (pp == null) continue;
            float cx = np.getX() + Player.SIZE / 2f;
            float cy = np.getY() + Player.SIZE / 2f;

            if (np.getHealth() < pp.getHealth()) {
                particleEvents.offer(new float[]{cx, cy, 0}); // blood
                if (np.getId().equals(myPlayerId)) {
                    int dmg = pp.getHealth() - np.getHealth();
                    shakeRequest.set(Math.min(18, dmg * 2 + 4));
                    SoundManager.playHit();
                }
            }
            if (!pp.isInfected() && np.isInfected()) {
                particleEvents.offer(new float[]{cx, cy, 3}); // infection burst
                if (np.getId().equals(myPlayerId)) {
                    shakeRequest.set(22);
                    SoundManager.playInfected();
                }
            }
        }

        // Pickup collected
        for (Pickup nPk : new ArrayList<>(next.getPickups())) {
            if (!nPk.isCollected()) continue;
            for (Pickup pPk : new ArrayList<>(prev.getPickups())) {
                if (!pPk.getId().equals(nPk.getId()) || pPk.isCollected()) continue;
                float cx = nPk.getX() + Pickup.SIZE / 2f;
                float cy = nPk.getY() + Pickup.SIZE / 2f;
                float evType = nPk.getType() == Pickup.Type.MEDKIT ? 1 : 2;
                particleEvents.offer(new float[]{cx, cy, evType});
                if (nPk.getType() == Pickup.Type.MEDKIT) SoundManager.playMedkit();
                else                                      SoundManager.playPickup();
                break;
            }
        }

        // Game phase transitions
        if (prev.getCurrentPhase() == GameState.Phase.WAITING &&
            next.getCurrentPhase() == GameState.Phase.PLAYING) {
            SoundManager.playGameStart();
            warningPlayed = false;
        }
        if (prev.getCurrentPhase() == GameState.Phase.PLAYING &&
            next.getCurrentPhase() == GameState.Phase.GAME_OVER) {
            boolean survivorsWin = next.getWinnerMessage().contains("SURVIVOR");
            SoundManager.playGameOver(survivorsWin);
        }
    }

    // ── Particle event drain (EDT) ─────────────────────────────────────────────

    private void drainParticleEvents() {
        float[] ev;
        Random rng = new Random();
        while ((ev = particleEvents.poll()) != null) {
            int type = (int) ev[2];
            float cx = ev[0], cy = ev[1];
            switch (type) {
                case 0 -> spawnBlood(cx, cy, rng);
                case 1 -> spawnSparkles(cx, cy, new Color(220, 40, 40), rng);
                case 2 -> spawnSparkles(cx, cy, new Color(220, 185, 20), rng);
                case 3 -> spawnInfection(cx, cy, rng);
            }
        }
    }

    private void spawnBlood(float cx, float cy, Random rng) {
        for (int i = 0; i < 10; i++) {
            float a = rng.nextFloat() * (float)(Math.PI * 2);
            float spd = 1.4f + rng.nextFloat() * 2.8f;
            particles.add(new float[]{
                cx, cy, (float)Math.cos(a)*spd, (float)Math.sin(a)*spd,
                14+rng.nextInt(18), 30, 185, 18, 18, 3+rng.nextInt(4), 0
            });
        }
    }

    private void spawnSparkles(float cx, float cy, Color col, Random rng) {
        for (int i = 0; i < 12; i++) {
            float a = rng.nextFloat() * (float)(Math.PI * 2);
            float spd = 1.8f + rng.nextFloat() * 3.2f;
            particles.add(new float[]{
                cx, cy, (float)Math.cos(a)*spd, (float)Math.sin(a)*spd - 1.5f,
                18+rng.nextInt(20), 40, col.getRed(), col.getGreen(), col.getBlue(),
                4+rng.nextInt(5), 1
            });
        }
    }

    private void spawnInfection(float cx, float cy, Random rng) {
        for (int i = 0; i < 14; i++) {
            float a = rng.nextFloat() * (float)(Math.PI * 2);
            float spd = 2.2f + rng.nextFloat() * 3.5f;
            particles.add(new float[]{
                cx, cy, (float)Math.cos(a)*spd, (float)Math.sin(a)*spd - 1f,
                22+rng.nextInt(25), 50,
                70 + rng.nextInt(60), 180 + rng.nextInt(60), 35,
                3+rng.nextInt(5), 1
            });
        }
    }

    // ── Particle update ────────────────────────────────────────────────────────

    private void updateParticles() {
        Iterator<float[]> it = particles.iterator();
        while (it.hasNext()) {
            float[] p = it.next();
            p[0] += p[2];         // x += vx
            p[1] += p[3];         // y += vy
            p[3] += 0.06f;        // gravity
            p[2] *= 0.93f;        // friction
            p[4]--;               // life
            if (p[4] <= 0) it.remove();
        }
    }

    // ── Sound triggers (timer tick) ────────────────────────────────────────────

    private void handleSoundTriggers() {
        GameState gs = gameState;
        if (gs == null || gs.getCurrentPhase() != GameState.Phase.PLAYING) return;
        int t = gs.getTimeRemaining();
        if (t != lastTimeRemaining) {
            if (t <= 30 && !warningPlayed) { SoundManager.playWarning(); warningPlayed = true; }
            if (t > 0 && t <= 10)          SoundManager.playTick();
            lastTimeRemaining = t;
        }
    }

    // ── Facing tracker ────────────────────────────────────────────────────────

    private void trackFacing(GameState s) {
        for (Player p : s.getPlayers().values()) {
            float[] prev = lastPositions.get(p.getId());
            if (prev != null) {
                double dx = p.getX() - prev[0], dy = p.getY() - prev[1];
                if (dx*dx + dy*dy > 0.5) facingAngles.put(p.getId(), Math.atan2(dy, dx));
            }
            lastPositions.put(p.getId(), new float[]{p.getX(), p.getY()});
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Paint dispatcher
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        // Screen shake
        int req = shakeRequest.getAndSet(0);
        if (req > 0) { shakeFrames = req; shakeIntensity = req > 15 ? 7 : 3; }
        AffineTransform origXform = g2.getTransform();
        if (shakeFrames > 0) {
            int sx = (int)((Math.random()*2-1) * shakeIntensity);
            int sy = (int)((Math.random()*2-1) * shakeIntensity);
            g2.translate(sx, sy);
            shakeFrames--;
        }

        GameState gs = gameState;
        if (gs == null) { renderConnecting(g2); g2.dispose(); return; }

        switch (gs.getCurrentPhase()) {
            case WAITING   -> renderLobby(g2, gs);
            case PLAYING   -> { renderGame(g2, gs); }
            case GAME_OVER -> { renderGame(g2, gs); renderGameOver(g2, gs); }
        }

        g2.setTransform(origXform);
        g2.dispose();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Connecting splash
    // ═══════════════════════════════════════════════════════════════════════════

    private void renderConnecting(Graphics2D g) {
        g.setColor(new Color(5, 10, 5));
        g.fillRect(0, 0, W, H);
        float p = 0.5f + 0.5f*(float)Math.sin(animTick*0.09);
        g.setColor(new Color(40, 180, 60, (int)(140+115*p)));
        g.setFont(new Font("Segoe UI", Font.BOLD, 22));
        drawCentered(g, "Connecting to server...", W/2, H/2);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Lobby
    // ═══════════════════════════════════════════════════════════════════════════

    private void renderLobby(Graphics2D g, GameState gs) {
        g.setColor(new Color(5, 9, 5));
        g.fillRect(0, 0, W, H);

        // Radial atmosphere
        Paint sv = g.getPaint();
        g.setPaint(new RadialGradientPaint(W*.28f, H*.38f, 320,
            new float[]{0f,1f}, new Color[]{new Color(28,70,28,38), new Color(0,0,0,0)}));
        g.fillRect(0, 0, W, H);
        g.setPaint(new RadialGradientPaint(W*.72f, H*.60f, 300,
            new float[]{0f,1f}, new Color[]{new Color(90,22,22,30), new Color(0,0,0,0)}));
        g.fillRect(0, 0, W, H);
        g.setPaint(sv);

        // Floating particles
        Random rng = new Random(77L);
        for (int i = 0; i < 100; i++) {
            int px = rng.nextInt(W), py = rng.nextInt(H);
            float ph = (float)Math.sin(animTick*0.048 + i*0.38);
            int a = (int)(28 + 38*ph);
            g.setColor(new Color(45, 160, 55, Math.max(0,a)));
            g.fillOval(px, py, 2, 2);
        }

        // Biohazard glyph
        float glph = 0.5f + 0.5f*(float)Math.sin(animTick*0.065);
        g.setFont(new Font("Segoe UI Symbol", Font.PLAIN, (int)(90+10*glph)));
        g.setColor(new Color(165, 20, 20, (int)(75+65*glph)));
        drawCentered(g, "\u2623", W/2, 138);

        // Title
        g.setFont(new Font("Impact", Font.PLAIN, 65));
        g.setColor(new Color(0, 0, 0, 195));
        drawCentered(g, "ZOMBIE SURVIVAL", W/2+4, 208+4);
        g.setColor(new Color(232, 48, 48));
        drawCentered(g, "ZOMBIE SURVIVAL", W/2, 208);

        // Subtitle
        g.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        g.setColor(new Color(100, 160, 100));
        drawCentered(g, "Real-time Multiplayer  \u2022  Java TCP/IP  \u2022  Dynamic Lighting  \u2022  Sprint", W/2, 232);

        // Divider
        g.setColor(new Color(50, 85, 50));
        g.fillRect(W/2 - 200, 244, 400, 1);

        // Player list
        Collection<Player> players = gs.getPlayers().values();
        int listY = 270;
        g.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g.setColor(new Color(95, 168, 95));
        drawCentered(g, "CONNECTED PLAYERS  (" + players.size() + ")", W/2, listY);

        for (Player p : players) {
            listY += 38;
            boolean isMe = p.getId().equals(myPlayerId);
            int ax = W/2 - 125, ay = listY - 19;
            g.setColor(new Color(18, 52, 18));
            g.fillOval(ax, ay, 28, 28);
            g.setColor(isMe ? new Color(55, 185, 70) : new Color(42, 148, 55));
            g.fillOval(ax+2, ay+2, 24, 24);
            g.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g.setColor(Color.WHITE);
            drawCentered(g, p.getName().substring(0,1).toUpperCase(), ax+14, ay+18);
            g.setFont(new Font("Segoe UI", isMe ? Font.BOLD : Font.PLAIN, 14));
            g.setColor(isMe ? Color.WHITE : new Color(172, 210, 172));
            g.drawString(p.getName() + (isMe ? "  \u2605 YOU" : ""), W/2 - 85, listY);
        }

        // Waiting text
        float wp = 0.5f + 0.5f*(float)Math.sin(animTick*0.072);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        g.setColor(new Color(130, 195, 130, (int)(80+175*wp)));
        drawCentered(g, "\u25CF  Waiting for players...  (minimum 2 to start)", W/2, H - 110);

        // Controls box
        int bx = W/2 - 270, by = H - 96;
        g.setColor(new Color(12, 22, 12));
        g.fillRoundRect(bx, by, 540, 74, 10, 10);
        g.setColor(new Color(38, 62, 38));
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(bx, by, 540, 74, 10, 10);
        g.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g.setColor(new Color(115, 185, 115));
        drawCentered(g, "CONTROLS", W/2, by+18);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        g.setColor(new Color(148, 185, 148));
        drawCentered(g, "Move: WASD / Arrow Keys     SHIFT: Sprint (drains stamina)     Chat: Click \u2192 type \u2192 Enter", W/2, by+38);
        drawCentered(g, "\u2764 Medkit: +40 HP    \uD83D\uDEE1 Armor: absorbs damage    \u26A1 Speed: 1.5\u00D7    \uD83D\uDD11 Key: +50 score", W/2, by+56);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Game world
    // ═══════════════════════════════════════════════════════════════════════════

    private void renderGame(Graphics2D g, GameState gs) {
        drawTiles(g);
        drawWallFaces3D(g);
        drawDecorations(g);
        drawPickups(g, gs);
        drawPlayers(g, gs);
        drawParticles(g);
        drawDynamicLighting(g, gs);
        drawMinimap(g, gs);
        drawZombieVignette(g, gs);
    }

    // ── 1. Tiles ──────────────────────────────────────────────────────────────

    private void drawTiles(Graphics2D g) {
        for (int row = 0; row < MH; row++) {
            for (int col = 0; col < MW; col++) {
                int tile = GameState.MAP[row][col];
                int px = col*TS, py = row*TS;
                switch (tile) {
                    case 0 -> g.drawImage((row+col)%2==0 ? tileGrassA : tileGrassB, px, py, null);
                    case 1 -> g.drawImage(tileWall, px, py, null);
                    case 2 -> {
                        g.drawImage(tileSafe, px, py, null);
                        float gl = 0.3f + 0.22f*(float)Math.sin(animTick*0.055 + col*0.35 + row*0.35);
                        g.setColor(new Color(28, 198, 105, (int)(68*gl)));
                        g.fillRect(px, py, TS, TS);
                    }
                }
            }
        }
    }

    // ── 2. 3-D wall faces ─────────────────────────────────────────────────────

    private void drawWallFaces3D(Graphics2D g) {
        for (int row = 0; row < MH; row++) {
            for (int col = 0; col < MW; col++) {
                if (GameState.MAP[row][col] != 1) continue;
                int px = col*TS, py = row*TS;

                // Bottom face — dark slab below exposed wall bottom
                if (row+1 < MH && GameState.MAP[row+1][col] != 1) {
                    // Solid bottom face
                    g.setColor(new Color(30, 33, 44));
                    g.fillRect(px, py + TS, TS, 11);
                    // Ambient-occlusion gradient fade below face
                    Paint sv = g.getPaint();
                    g.setPaint(new GradientPaint(0, py+TS+11, new Color(0,0,0,65),
                                                 0, py+TS+17, new Color(0,0,0,0)));
                    g.fillRect(px, py+TS+11, TS, 6);
                    g.setPaint(sv);
                    // Top-highlight on face
                    g.setColor(new Color(55, 58, 72));
                    g.drawLine(px, py+TS, px+TS-1, py+TS);
                }

                // Right face — slightly lighter slab to the right
                if (col+1 < MW && GameState.MAP[row][col+1] != 1) {
                    g.setColor(new Color(50, 53, 65));
                    g.fillRect(px+TS, py+3, 7, TS-3);
                    Paint sv = g.getPaint();
                    g.setPaint(new GradientPaint(px+TS+7, 0, new Color(0,0,0,52),
                                                 px+TS+13, 0, new Color(0,0,0,0)));
                    g.fillRect(px+TS+7, py+3, 6, TS-3);
                    g.setPaint(sv);
                }
            }
        }
    }

    // ── 3. Decorations ────────────────────────────────────────────────────────

    private void drawDecorations(Graphics2D g) {
        for (int[] pos : TREES) {
            int px = pos[0]*TS, py = pos[1]*TS;
            g.setColor(new Color(0, 0, 0, 60));
            g.fillOval(px+2, py+14, 28, 14);
            g.setColor(new Color(82, 54, 24));
            g.fillOval(px+11, py+10, 10, 12);
            g.setColor(new Color(18, 68, 18));
            g.fillOval(px, py, 32, 32);
            g.setColor(new Color(26, 95, 26));
            g.fillOval(px+4, py+4, 24, 24);
            g.setColor(new Color(40, 125, 40));
            g.fillOval(px+9, py+7, 14, 14);
            g.setColor(new Color(0, 0, 0, 36));
            g.setStroke(new BasicStroke(1.5f));
            g.drawOval(px+1, py+3, 30, 30);
            g.setStroke(new BasicStroke(1f));
        }
        for (int[] pos : CRATES) {
            int px = pos[0]*TS+4, py = pos[1]*TS+4;
            g.setColor(new Color(0, 0, 0, 52));
            g.fillRect(px+3, py+22, 24, 5);
            g.setColor(new Color(108, 74, 34));
            g.fillRect(px, py, 24, 22);
            g.setColor(new Color(78, 52, 22));
            g.drawRect(px, py, 24, 22);
            g.drawLine(px, py+11, px+24, py+11);
            g.drawLine(px+12, py, px+12, py+22);
            g.setColor(new Color(138, 98, 48));
            g.drawLine(px+1, py+1, px+22, py+1);
        }
    }

    // ── 4. Pickups ────────────────────────────────────────────────────────────

    private void drawPickups(Graphics2D g, GameState gs) {
        for (Pickup pk : new ArrayList<>(gs.getPickups())) {
            if (pk.isCollected()) continue;
            int bob = (int)(3.5*Math.sin(animTick*0.09 + (pk.getX()+pk.getY())*0.018));
            int px = pk.getX(), py = pk.getY()+bob, s = Pickup.SIZE;
            float gl = 0.5f + 0.5f*(float)Math.sin(animTick*0.10);
            Paint sv = g.getPaint();

            switch (pk.getType()) {
                case MEDKIT -> {
                    g.setPaint(new RadialGradientPaint(px+s/2f, py+s/2f, s+9,
                        new float[]{0f,1f}, new Color[]{new Color(220,0,0,(int)(55*gl)), new Color(0,0,0,0)}));
                    g.fillOval(px-9, py-9, s+18, s+18);
                    g.setPaint(sv);
                    g.setColor(new Color(198, 26, 26));
                    g.fill(new RoundRectangle2D.Float(px, py, s, s, 5, 5));
                    g.setColor(new Color(238, 65, 65));
                    g.setStroke(new BasicStroke(1.2f));
                    g.draw(new RoundRectangle2D.Float(px, py, s, s, 5, 5));
                    g.setStroke(new BasicStroke(1f));
                    int arm = s/3;
                    g.setColor(Color.WHITE);
                    g.fillRect(px+arm, py+3, arm, s-6);
                    g.fillRect(px+3, py+arm, s-6, arm);
                }
                case KEY -> {
                    g.setPaint(new RadialGradientPaint(px+s/2f, py+s/2f, s+8,
                        new float[]{0f,1f}, new Color[]{new Color(220,180,0,(int)(58*gl)), new Color(0,0,0,0)}));
                    g.fillOval(px-8, py-8, s+16, s+16);
                    g.setPaint(sv);
                    int r = s/2-1;
                    g.setColor(new Color(202, 162, 0));
                    g.fillOval(px, py, r+4, r+4);
                    g.setColor(new Color(252, 215, 42));
                    g.setStroke(new BasicStroke(1.5f));
                    g.drawOval(px+1, py+1, r+2, r+2);
                    g.setStroke(new BasicStroke(1f));
                    g.setColor(new Color(30, 88, 30));
                    g.fillOval(px+4, py+4, r-4, r-4);
                    g.setColor(new Color(202, 162, 0));
                    g.fillRect(px+r, py+r/2, r+2, s/4);
                    g.fillRect(px+r+r/2, py+r/2-3, s/5, s/5);
                    g.fillRect(px+r+r*3/4, py+r/2-3, s/5, s/5);
                }
                case ARMOR -> {
                    g.setPaint(new RadialGradientPaint(px+s/2f, py+s/2f, s+8,
                        new float[]{0f,1f}, new Color[]{new Color(80,140,255,(int)(55*gl)), new Color(0,0,0,0)}));
                    g.fillOval(px-8, py-8, s+16, s+16);
                    g.setPaint(sv);
                    // Shield shape
                    int[] sx = {px+s/2, px+s, px+s, px+s/2, px, px};
                    int[] sy = {py, py+s/4, py+s*3/4, py+s, py+s*3/4, py+s/4};
                    g.setColor(new Color(55, 105, 200));
                    g.fillPolygon(sx, sy, 6);
                    g.setColor(new Color(100, 155, 255));
                    g.drawPolygon(sx, sy, 6);
                    g.setColor(Color.WHITE);
                    g.setFont(new Font("Segoe UI", Font.BOLD, 11));
                    drawCentered(g, "\uD83D\uDEE1", px+s/2, py+s-3);
                }
                case SPEED_BOOST -> {
                    g.setPaint(new RadialGradientPaint(px+s/2f, py+s/2f, s+8,
                        new float[]{0f,1f}, new Color[]{new Color(255,220,0,(int)(58*gl)), new Color(0,0,0,0)}));
                    g.fillOval(px-8, py-8, s+16, s+16);
                    g.setPaint(sv);
                    g.setColor(new Color(220, 185, 0));
                    g.fillOval(px, py, s, s);
                    g.setColor(new Color(255, 240, 60));
                    g.drawOval(px+1, py+1, s-2, s-2);
                    g.setColor(Color.WHITE);
                    g.setFont(new Font("Segoe UI", Font.BOLD, 13));
                    drawCentered(g, "\u26A1", px+s/2, py+s-2);
                }
            }
        }
    }

    // ── 5. Players ────────────────────────────────────────────────────────────

    private void drawPlayers(Graphics2D g, GameState gs) {
        List<Player> all = new ArrayList<>(gs.getPlayers().values());
        all.sort((a, b) -> Boolean.compare(a.isInfected(), b.isInfected()));
        for (Player p : all) drawOnePlayer(g, p, gs);
    }

    private void drawOnePlayer(Graphics2D g, Player p, GameState gs) {
        int cx = p.getX() + Player.SIZE/2;
        int cy = p.getY() + Player.SIZE/2;
        boolean zombie = p.isInfected();
        boolean isMe   = p.getId().equals(myPlayerId);
        boolean boosted = p.hasSpeedBoost();

        double angle = facingAngles.getOrDefault(p.getId(), -Math.PI/2.0);
        float hDx = (float)(8.5*Math.cos(angle)), hDy = (float)(8.5*Math.sin(angle));
        double perp = angle + Math.PI/2.0;
        float aDx = (float)(9.5*Math.cos(perp)), aDy = (float)(9.5*Math.sin(perp));

        // Speed boost aura
        if (boosted) {
            float ba = 0.4f + 0.4f*(float)Math.sin(animTick*0.15);
            g.setColor(new Color(255, 220, 0, (int)(90*ba)));
            g.setStroke(new BasicStroke(3.0f));
            g.drawOval(cx-18, cy-18, 36, 36);
            g.setStroke(new BasicStroke(1f));
        }

        // Safe-zone ring
        if (!zombie && gs.isSafeZoneAt(cx, cy)) {
            float sg = 0.35f + 0.45f*(float)Math.sin(animTick*0.09);
            g.setColor(new Color(45, 220, 115, (int)(115*sg)));
            g.setStroke(new BasicStroke(2.5f));
            g.drawOval(cx-20, cy-20, 40, 40);
            g.setStroke(new BasicStroke(1f));
        }

        // "You" ring
        if (isMe) {
            float mg = 0.5f + 0.5f*(float)Math.sin(animTick*0.11);
            g.setColor(new Color(255, 255, 255, (int)(45+85*mg)));
            g.setStroke(new BasicStroke(2.0f));
            g.drawOval(cx-19, cy-19, 38, 38);
            g.setStroke(new BasicStroke(1f));
        }

        // Shadow
        g.setColor(new Color(0, 0, 0, 88));
        g.fillOval(cx-14, cy-8, 28, 17);

        // Back-leg
        Color legCol = zombie ? new Color(48, 62, 30) : new Color(34, 48, 76);
        g.setColor(legCol);
        g.fillOval((int)(cx-hDx)-5, (int)(cy-hDy)-4, 11, 10);

        // Body / torso
        Color bodyCol = zombie ? new Color(70, 90, 44) : new Color(44, 68, 112);
        Color bodyBdr = zombie ? new Color(46, 64, 26) : new Color(24, 46, 86);
        g.setColor(bodyCol);
        g.fillOval(cx-11, cy-11, 22, 22);
        g.setColor(bodyBdr);
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(cx-11, cy-11, 22, 22);
        g.setStroke(new BasicStroke(1f));
        if (!zombie) {
            g.setColor(new Color(28, 44, 74));
            g.fillRect(cx-9, cy-2, 18, 3);
        }

        // Arms
        Color armCol = zombie ? new Color(80, 102, 50) : new Color(52, 80, 125);
        g.setColor(armCol);
        g.fillOval((int)(cx+aDx)-5, (int)(cy+aDy)-5, 10, 10);
        g.fillOval((int)(cx-aDx)-5, (int)(cy-aDy)-5, 10, 10);

        // Head
        int hx = (int)(cx+hDx), hy = (int)(cy+hDy);
        Color skinCol = zombie ? new Color(126, 154, 76) : new Color(208, 170, 126);
        Color skinBdr = zombie ? new Color(90, 116, 54)  : new Color(170, 132, 94);
        g.setColor(skinCol);
        g.fillOval(hx-9, hy-9, 18, 18);
        g.setColor(skinBdr);
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(hx-9, hy-9, 18, 18);
        g.setStroke(new BasicStroke(1f));

        // Eyes
        float ep  = (float)(3.8*Math.cos(perp)),  ep2 = (float)(3.8*Math.sin(perp));
        float ef  = (float)(2.5*Math.cos(angle)), ef2 = (float)(2.5*Math.sin(angle));
        g.setColor(new Color(240, 242, 248));
        g.fillOval((int)(hx+ep+ef)-3, (int)(hy+ep2+ef2)-3, 6, 6);
        g.fillOval((int)(hx-ep+ef)-3, (int)(hy-ep2+ef2)-3, 6, 6);
        Color pupilCol = zombie ? new Color(215, 25, 25) : new Color(16, 16, 60);
        g.setColor(pupilCol);
        g.fillOval((int)(hx+ep+ef)-1, (int)(hy+ep2+ef2)-1, 3, 3);
        g.fillOval((int)(hx-ep+ef)-1, (int)(hy-ep2+ef2)-1, 3, 3);

        // Helmet (survivor) / wound (zombie)
        if (!zombie) {
            g.setColor(p.getArmor() > 0 ? new Color(65, 135, 200) : new Color(46, 68, 42));
            g.fillArc(hx-10, hy-11, 20, 14, 0, 180);
            g.setColor(p.getArmor() > 0 ? new Color(42, 100, 165) : new Color(32, 50, 28));
            g.drawArc(hx-10, hy-11, 20, 14, 0, 180);
            g.setColor(p.getArmor() > 0 ? new Color(88, 165, 232) : new Color(68, 100, 64));
            g.drawLine(hx-10, hy-4, hx+10, hy-4);
        } else {
            g.setColor(new Color(175, 16, 16, 195));
            g.fillOval(hx+(int)ef, hy+(int)ef2, 6, 3);
            g.fillOval(hx+(int)ef-2, hy+(int)ef2+4, 3, 2);
        }

        // Name tag
        g.setFont(new Font("Segoe UI", Font.BOLD, 10));
        FontMetrics fm = g.getFontMetrics();
        String label = p.getName();
        int tw = fm.stringWidth(label);
        int tagX = cx-tw/2, tagY = cy-30;
        g.setColor(new Color(0, 0, 0, 178));
        g.fillRoundRect(tagX-4, tagY-12, tw+8, 14, 4, 4);
        g.setColor(zombie ? new Color(100, 238, 100) : new Color(175, 215, 255));
        g.drawString(label, tagX, tagY);

        // Health bar
        int hbW = Player.SIZE+10, hbX = cx-hbW/2, hbY = cy-40;
        g.setColor(new Color(15, 18, 28, 195));
        g.fillRoundRect(hbX, hbY, hbW, 5, 2, 2);
        int hp = p.getHealth();
        if (hp > 0) {
            Color hpC = hp>60 ? new Color(36,190,60) : hp>30 ? new Color(220,150,16) : new Color(210,40,40);
            g.setColor(hpC);
            g.fillRoundRect(hbX, hbY, Math.max(1, hbW*hp/100), 5, 2, 2);
        }

        // Armor bar (thin, blue, below HP bar)
        if (p.getArmor() > 0) {
            int abY = hbY + 7;
            g.setColor(new Color(12, 15, 28, 190));
            g.fillRoundRect(hbX, abY, hbW, 3, 1, 1);
            g.setColor(new Color(65, 135, 210));
            g.fillRoundRect(hbX, abY, Math.max(1, hbW*p.getArmor()/60), 3, 1, 1);
        }
    }

    // ── 6. Particles ──────────────────────────────────────────────────────────

    private void drawParticles(Graphics2D g) {
        for (float[] p : new ArrayList<>(particles)) {
            float alpha = p[4] / p[5];
            int a = (int)(alpha * 200);
            if (a <= 0) continue;
            g.setColor(new Color((int)p[6], (int)p[7], (int)p[8], a));
            int sz = (int)(p[9] * alpha) + 1;
            if (p[10] == 1) {
                // Sparkle — draw a small + shape for variety
                int x = (int)p[0], y = (int)p[1];
                g.fillOval(x-sz/2, y-sz/2, sz, sz);
            } else {
                g.fillOval((int)p[0], (int)p[1], sz, sz);
            }
        }
    }

    // ── 7. Dynamic lighting ───────────────────────────────────────────────────

    private void drawDynamicLighting(Graphics2D g, GameState gs) {
        if (lightMap == null || lightMap.getWidth() != W || lightMap.getHeight() != H) {
            lightMap = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        }

        Graphics2D lg = lightMap.createGraphics();
        lg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Fill with darkness
        lg.setComposite(AlphaComposite.Clear);
        lg.fillRect(0, 0, W, H);
        lg.setComposite(AlphaComposite.Src);
        lg.setColor(new Color(0, 0, 0, 168));
        lg.fillRect(0, 0, W, H);

        // Punch light holes (DstOut: src alpha erases dst)
        lg.setComposite(AlphaComposite.DstOut);

        // Player lights
        for (Player p : gs.getPlayers().values()) {
            int cx = p.getX() + Player.SIZE/2;
            int cy = p.getY() + Player.SIZE/2;
            boolean isMe = p.getId().equals(myPlayerId);
            float rad = isMe ? (p.isInfected() ? 108 : 162) : (p.isInfected() ? 78 : 112);
            if (p.hasSpeedBoost()) rad *= 1.15f;

            if (cx+rad < 0 || cx-rad > W || cy+rad < 0 || cy-rad > H) continue;
            try {
                lg.setPaint(new RadialGradientPaint(cx, cy, rad,
                    new float[]{0f, 0.48f, 1f},
                    new Color[]{new Color(255,255,255,255), new Color(255,255,255,155), new Color(255,255,255,0)}));
                lg.fillOval((int)(cx-rad), (int)(cy-rad), (int)(rad*2), (int)(rad*2));
            } catch (Exception ignored) {}
        }

        // Safe-zone ambient lights (green-tinted)
        for (int row = 0; row < MH; row++) {
            for (int col = 0; col < MW; col++) {
                if (GameState.MAP[row][col] != 2) continue;
                float cx = col*TS + TS*0.5f, cy = row*TS + TS*0.5f;
                float r = TS * 2.4f;
                try {
                    lg.setPaint(new RadialGradientPaint(cx, cy, r,
                        new float[]{0f, 1f},
                        new Color[]{new Color(255,255,255,118), new Color(255,255,255,0)}));
                    lg.fillOval((int)(cx-r), (int)(cy-r), (int)(r*2), (int)(r*2));
                } catch (Exception ignored) {}
            }
        }

        // Pickup glow lights
        for (Pickup pk : new ArrayList<>(gs.getPickups())) {
            if (pk.isCollected()) continue;
            float cx = pk.getX() + Pickup.SIZE * 0.5f, cy = pk.getY() + Pickup.SIZE * 0.5f;
            float r = 32f;
            try {
                lg.setPaint(new RadialGradientPaint(cx, cy, r,
                    new float[]{0f, 1f},
                    new Color[]{new Color(255,255,255,78), new Color(255,255,255,0)}));
                lg.fillOval((int)(cx-r), (int)(cy-r), (int)(r*2), (int)(r*2));
            } catch (Exception ignored) {}
        }

        lg.dispose();
        g.drawImage(lightMap, 0, 0, null);
    }

    // ── 8. Minimap ────────────────────────────────────────────────────────────

    private void drawMinimap(Graphics2D g, GameState gs) {
        int mmW = 130, mmH = 95;
        int mmX = W - mmW - 8, mmY = H - mmH - 8;

        g.setColor(new Color(5, 8, 5, 215));
        g.fillRoundRect(mmX-3, mmY-17, mmW+6, mmH+20, 7, 7);
        g.setColor(new Color(42, 58, 42, 200));
        g.drawRoundRect(mmX-3, mmY-17, mmW+6, mmH+20, 7, 7);
        g.setFont(new Font("Segoe UI", Font.BOLD, 9));
        g.setColor(new Color(82, 128, 82));
        g.drawString("MINIMAP", mmX, mmY-4);

        float sx = (float)mmW/MW, sy = (float)mmH/MH;
        for (int row = 0; row < MH; row++) {
            for (int col = 0; col < MW; col++) {
                int tile = GameState.MAP[row][col];
                int tx = mmX+(int)(col*sx), ty = mmY+(int)(row*sy);
                int tw = Math.max(1,(int)sx+1), th = Math.max(1,(int)sy+1);
                switch (tile) {
                    case 0 -> g.setColor(new Color(28, 78, 28));
                    case 1 -> g.setColor(new Color(68, 72, 88));
                    case 2 -> g.setColor(new Color(18, 102, 62));
                }
                g.fillRect(tx, ty, tw, th);
            }
        }

        for (Pickup pk : new ArrayList<>(gs.getPickups())) {
            if (pk.isCollected()) continue;
            int px = mmX + (int)((float)pk.getX()/GameState.CANVAS_W*mmW);
            int py = mmY + (int)((float)pk.getY()/GameState.CANVAS_H*mmH);
            switch (pk.getType()) {
                case MEDKIT      -> g.setColor(new Color(212, 35, 35));
                case KEY         -> g.setColor(new Color(212, 175, 0));
                case ARMOR       -> g.setColor(new Color(65, 120, 215));
                case SPEED_BOOST -> g.setColor(new Color(240, 210, 0));
            }
            g.fillRect(px-1, py-1, 3, 3);
        }

        for (Player p : gs.getPlayers().values()) {
            int px = mmX + (int)((float)p.getX()/GameState.CANVAS_W*mmW);
            int py = mmY + (int)((float)p.getY()/GameState.CANVAS_H*mmH);
            boolean me = p.getId().equals(myPlayerId);
            g.setColor(p.isInfected() ? new Color(52, 202, 65) : new Color(58, 130, 255));
            g.fillOval(px-2, py-2, 5, 5);
            if (me) { g.setColor(Color.WHITE); g.drawOval(px-4, py-4, 9, 9); }
        }
    }

    // ── 9. Zombie vignette ────────────────────────────────────────────────────

    private void drawZombieVignette(Graphics2D g, GameState gs) {
        if (myPlayerId == null) return;
        Player me = gs.getPlayer(myPlayerId);
        if (me == null || !me.isInfected()) return;
        float str = 0.5f + 0.14f*(float)Math.sin(animTick*0.068);
        Paint sv = g.getPaint();
        g.setPaint(new RadialGradientPaint(
            new Point2D.Float(W/2f, H/2f), W*0.72f,
            new float[]{0.38f, 1f},
            new Color[]{new Color(0,0,0,0), new Color(148, 20, 20, (int)(215*str))}));
        g.fillRect(0, 0, W, H);
        g.setPaint(sv);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Game Over overlay
    // ═══════════════════════════════════════════════════════════════════════════

    private void renderGameOver(Graphics2D g, GameState gs) {
        g.setColor(new Color(0, 0, 0, 188));
        g.fillRect(0, 0, W, H);

        boolean sWin = gs.getWinnerMessage().contains("SURVIVOR");
        Color winCol = sWin ? new Color(65, 148, 255) : new Color(52, 212, 72);

        g.setFont(new Font("Impact", Font.PLAIN, 58));
        g.setColor(new Color(0, 0, 0, 172));
        drawCentered(g, gs.getWinnerMessage(), W/2+4, 124+4);
        g.setColor(winCol);
        drawCentered(g, gs.getWinnerMessage(), W/2, 124);

        List<Player> sorted = new ArrayList<>(gs.getPlayers().values());
        sorted.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));

        int panW = 520, panH = 52 + sorted.size()*42 + 24;
        int panX = (W-panW)/2, panY = 144;

        g.setColor(new Color(10, 13, 22, 242));
        g.fill(new RoundRectangle2D.Float(panX, panY, panW, panH, 12, 12));
        g.setColor(new Color(50, 58, 80));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new RoundRectangle2D.Float(panX, panY, panW, panH, 12, 12));
        g.setStroke(new BasicStroke(1f));

        int hY = panY+32;
        g.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g.setColor(new Color(132, 142, 172));
        g.drawString("#",       panX+24, hY);
        g.drawString("PLAYER",  panX+60, hY);
        g.drawString("ROLE",    panX+252, hY);
        g.drawString("SCORE",   panX+415, hY);
        g.setColor(new Color(44, 53, 76));
        g.drawLine(panX+14, panY+38, panX+panW-14, panY+38);

        int rowY = panY+38;
        for (int i = 0; i < sorted.size(); i++) {
            Player p = sorted.get(i);
            rowY += 42;
            boolean me = p.getId().equals(myPlayerId);
            if (me) {
                g.setColor(new Color(30, 40, 70, 132));
                g.fillRoundRect(panX+10, rowY-26, panW-20, 34, 6, 6);
            }
            Color rankCol = i==0 ? new Color(220,185,25) : i==1 ? new Color(190,192,210) :
                            i==2 ? new Color(196,126,52) : new Color(122,128,152);
            g.setFont(new Font("Segoe UI", Font.BOLD, 16));
            g.setColor(rankCol);
            g.drawString(String.valueOf(i+1), panX+24, rowY);
            g.setFont(new Font("Segoe UI", me ? Font.BOLD : Font.PLAIN, 15));
            g.setColor(me ? Color.WHITE : new Color(190, 196, 216));
            g.drawString(p.getName() + (me ? " \u2605" : ""), panX+60, rowY);
            String roleStr = p.isInfected() ? "\u2623 Zombie" : "\uD83E\uDDCD Survivor";
            Color  roleCol = p.isInfected() ? new Color(68, 202, 76) : new Color(72, 140, 255);
            g.setFont(new Font("Segoe UI", Font.BOLD, 13));
            g.setColor(roleCol);
            g.drawString(roleStr, panX+252, rowY);
            g.setFont(new Font("Segoe UI", Font.BOLD, 15));
            g.setColor(new Color(218, 182, 25));
            g.drawString(String.valueOf(p.getScore()), panX+415, rowY);
        }

        int cd = gs.getGameOverCountdown();
        g.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        g.setColor(new Color(92, 102, 128));
        drawCentered(g, "New game begins in " + cd + " second" + (cd==1?"":"s") + "...",
                     W/2, panY+panH+30);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Tile texture generators (called once in constructor)
    // ═══════════════════════════════════════════════════════════════════════════

    private static BufferedImage makeTileGrass(int v) {
        BufferedImage img = new BufferedImage(TS, TS, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        Color base = v==0 ? new Color(32, 92, 32) : new Color(39, 103, 39);
        g.setColor(base);
        g.fillRect(0, 0, TS, TS);
        Random rng = new Random(v==0 ? 111222L : 333444L);
        for (int i = 0; i < 22; i++) {
            int gx = 1+rng.nextInt(TS-3), gy = 1+rng.nextInt(TS-5);
            int sh = rng.nextInt(3);
            Color gc = sh==0 ? new Color(22,73,22) : sh==1 ? base : new Color(48,118,48);
            g.setColor(new Color(gc.getRed(), gc.getGreen(), gc.getBlue(), 80));
            int bh = 3+rng.nextInt(4);
            g.fillRect(gx, gy, 1, bh); g.fillRect(gx+1, gy+1, 1, bh-1);
        }
        for (int i = 0; i < 6; i++) {
            int gx = rng.nextInt(TS-3), gy = rng.nextInt(TS-2);
            g.setColor(new Color(38, 66, 28, 50));
            g.fillOval(gx, gy, 4, 3);
        }
        g.dispose();
        return img;
    }

    private static BufferedImage makeTileWall() {
        BufferedImage img = new BufferedImage(TS, TS, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        // Top face (visible surface of wall)
        g.setColor(new Color(76, 80, 95));
        g.fillRect(0, 0, TS, TS);
        // Mortar lines
        g.setColor(new Color(52, 56, 68));
        g.fillRect(0,  10, TS, 2); g.fillRect(0,  22, TS, 2);
        g.fillRect(16,  0, 2, 10); g.fillRect( 8, 12, 2, 10);
        g.fillRect(24, 12, 2, 10); g.fillRect(16, 24, 2,  8);
        // Procedural surface noise
        Random rng = new Random(42L);
        for (int i = 0; i < 32; i++) {
            int nx = rng.nextInt(TS), ny = rng.nextInt(TS);
            int sh = rng.nextInt(34)-17;
            g.setColor(new Color(clamp(76+sh), clamp(80+sh), clamp(95+sh), 82));
            g.fillRect(nx, ny, 2, 2);
        }
        // Bevel highlight top-left, shadow bottom-right
        g.setColor(new Color(112, 118, 135));
        g.drawLine(0, 0, TS-1, 0); g.drawLine(0, 0, 0, TS-1);
        g.setColor(new Color(44, 47, 58));
        g.drawLine(0, TS-1, TS-1, TS-1); g.drawLine(TS-1, 0, TS-1, TS-1);
        g.dispose();
        return img;
    }

    private static BufferedImage makeTileSafe() {
        BufferedImage img = new BufferedImage(TS, TS, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(14, 88, 54));
        g.fillRect(0, 0, TS, TS);
        g.setColor(new Color(10, 72, 44));
        for (int i = 8; i < TS; i += 8) { g.drawLine(i, 0, i, TS); g.drawLine(0, i, TS, i); }
        g.setColor(new Color(34, 146, 88, 152));
        g.fillRect(13, 6,  6, 20);
        g.fillRect(6,  13, 20, 6);
        g.dispose();
        return img;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private void drawCentered(Graphics2D g, String text, int cx, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, cx - fm.stringWidth(text)/2, y);
    }
}
