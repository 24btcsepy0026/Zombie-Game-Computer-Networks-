package com.zombiesurvival.client.ui;

import com.zombiesurvival.client.InputController;
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
 * Main rendering canvas with camera system (viewport 960×704 over 2560×1920 world).
 *
 * Render order:
 *  1. Tile pass (grass / wall / safe-zone textures)
 *  2. 3-D wall faces
 *  3. Decorative trees & crates
 *  4. Pickups (glow + bob)
 *  5. NPC zombies
 *  6. Player sprites (direction-aware)
 *  7. Bullets (trails)
 *  8. Particles (blood / sparkle / infection)
 *  9. Dynamic lighting overlay
 * 10. Kill feed (top-right, world-space)
 * 11. Minimap (corner, screen-space)
 * 12. Crosshair
 * 13. Zombie vignette
 * 14. Lobby / game-over overlays
 */
public class GameCanvas extends JPanel {

    // ── Viewport ──────────────────────────────────────────────────────────────
    public static final int VW  = GameState.VIEWPORT_W;  // 960
    public static final int VH  = GameState.VIEWPORT_H;  // 704
    private static final int TS  = GameState.TILE_SIZE;
    private static final int MW  = GameState.MAP_WIDTH;
    private static final int MH  = GameState.MAP_HEIGHT;
    private static final int CW  = GameState.CANVAS_W;   // 2560
    private static final int CH  = GameState.CANVAS_H;   // 1920

    // ── Camera (world-space top-left of viewport) ─────────────────────────────
    private float camX = 0, camY = 0;
    private float tgtX = 0, tgtY = 0;

    // ── Tile textures ─────────────────────────────────────────────────────────
    private final BufferedImage tileGrassA, tileGrassB, tileWall, tileSafe, tileRoad;

    // ── State ─────────────────────────────────────────────────────────────────
    private volatile GameState gameState;
    private volatile GameState prevState;
    private volatile String    myPlayerId;
    private int animTick = 0;

    // Facing angles & last positions per player
    private final Map<String, Double>  facingAngles  = new ConcurrentHashMap<>();
    private final Map<String, float[]> lastPositions = new ConcurrentHashMap<>();

    // Mouse crosshair (screen space)
    private volatile int mouseScreenX = VW/2, mouseScreenY = VH/2;

    // ── Particles ─────────────────────────────────────────────────────────────
    private final List<float[]> particles = new ArrayList<>();
    private final ConcurrentLinkedQueue<float[]> particleEvents = new ConcurrentLinkedQueue<>();

    // ── Screen shake ──────────────────────────────────────────────────────────
    private final AtomicInteger shakeRequest = new AtomicInteger(0);
    private int shakeFrames = 0, shakeIntensity = 3;

    // ── Dynamic lighting ──────────────────────────────────────────────────────
    private BufferedImage lightMap;

    // ── Input controller reference (for camera offset updates) ───────────────
    private InputController inputController;

    // ── Sound trigger tracking ────────────────────────────────────────────────
    private int     lastTimeRemaining = Integer.MAX_VALUE;
    private boolean warningPlayed     = false;

    // ═══════════════════════════════════════════════════════════════════════════

    public GameCanvas() {
        setPreferredSize(new Dimension(VW, VH));
        setBackground(new Color(5, 10, 5));

        tileGrassA = makeTileGrass(0);
        tileGrassB = makeTileGrass(1);
        tileWall   = makeTileWall();
        tileSafe   = makeTileSafe();
        tileRoad   = makeTileRoad();

        // Track mouse for crosshair
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override public void mouseMoved  (java.awt.event.MouseEvent e) { mouseScreenX = e.getX(); mouseScreenY = e.getY(); }
            @Override public void mouseDragged(java.awt.event.MouseEvent e) { mouseScreenX = e.getX(); mouseScreenY = e.getY(); }
        });

        new javax.swing.Timer(33, e -> {
            animTick++;
            drainParticleEvents();
            updateParticles();
            repaint();
        }).start();
    }

    public void setInputController(InputController ic) { this.inputController = ic; }
    public void setMyPlayerId(String id)               { this.myPlayerId = id; }

    public void setGameState(GameState next) {
        if (next != null) {
            detectEvents(gameState, next);
            trackFacing(next);
        }
        prevState  = gameState;
        gameState  = next;
        updateCamera();
    }

    // ── Camera ────────────────────────────────────────────────────────────────

    private void updateCamera() {
        GameState gs = gameState;
        if (gs == null) return;
        Player me = gs.getPlayer(myPlayerId);
        if (me != null) {
            tgtX = me.getXf() + Player.SIZE / 2f - VW / 2f;
            tgtY = me.getYf() + Player.SIZE / 2f - VH / 2f;
        }
        tgtX = Math.max(0, Math.min(tgtX, CW - VW));
        tgtY = Math.max(0, Math.min(tgtY, CH - VH));
        camX += (tgtX - camX) * 0.12f;
        camY += (tgtY - camY) * 0.12f;
        if (inputController != null) inputController.updateCameraOffset(camX, camY);
    }

    // ── Event detection ───────────────────────────────────────────────────────

    private void detectEvents(GameState prev, GameState next) {
        if (prev == null) return;
        for (Player np : next.getPlayers().values()) {
            Player pp = prev.getPlayer(np.getId());
            if (pp == null) continue;
            float cx = np.getXf() + Player.SIZE / 2f;
            float cy = np.getYf() + Player.SIZE / 2f;
            if (np.getHealth() < pp.getHealth()) {
                particleEvents.offer(new float[]{cx, cy, 0});
                if (np.getId().equals(myPlayerId))
                    shakeRequest.set(Math.min(18, (pp.getHealth() - np.getHealth()) * 2 + 4));
            }
            if (!pp.isInfected() && np.isInfected()) {
                particleEvents.offer(new float[]{cx, cy, 3});
                if (np.getId().equals(myPlayerId)) shakeRequest.set(22);
            }
        }
        for (Pickup nPk : new ArrayList<>(next.getPickups())) {
            if (!nPk.isCollected()) continue;
            for (Pickup pPk : new ArrayList<>(prev.getPickups())) {
                if (!pPk.getId().equals(nPk.getId()) || pPk.isCollected()) continue;
                float cx = nPk.getX() + Pickup.SIZE / 2f;
                float cy = nPk.getY() + Pickup.SIZE / 2f;
                particleEvents.offer(new float[]{cx, cy, nPk.getType() == Pickup.Type.MEDKIT ? 1 : 2});
                break;
            }
        }
    }

    private void drainParticleEvents() {
        float[] ev;
        Random rng = new Random();
        while ((ev = particleEvents.poll()) != null) {
            switch ((int)ev[2]) {
                case 0 -> spawnBlood(ev[0], ev[1], rng);
                case 1 -> spawnSparkles(ev[0], ev[1], new Color(220,40,40), rng);
                case 2 -> spawnSparkles(ev[0], ev[1], new Color(220,185,20), rng);
                case 3 -> spawnInfection(ev[0], ev[1], rng);
            }
        }
    }

    private void spawnBlood(float cx, float cy, Random rng) {
        for (int i = 0; i < 10; i++) {
            float a = rng.nextFloat() * 6.28f, spd = 1.4f + rng.nextFloat() * 2.8f;
            particles.add(new float[]{cx, cy, (float)Math.cos(a)*spd, (float)Math.sin(a)*spd,
                14+rng.nextInt(18), 30, 185, 18, 18, 3+rng.nextInt(4), 0});
        }
    }

    private void spawnSparkles(float cx, float cy, Color col, Random rng) {
        for (int i = 0; i < 12; i++) {
            float a = rng.nextFloat() * 6.28f, spd = 1.8f + rng.nextFloat() * 3.2f;
            particles.add(new float[]{cx, cy, (float)Math.cos(a)*spd, (float)Math.sin(a)*spd - 1.5f,
                18+rng.nextInt(20), 40, col.getRed(), col.getGreen(), col.getBlue(), 4+rng.nextInt(5), 1});
        }
    }

    private void spawnInfection(float cx, float cy, Random rng) {
        for (int i = 0; i < 14; i++) {
            float a = rng.nextFloat() * 6.28f, spd = 2.2f + rng.nextFloat() * 3.5f;
            particles.add(new float[]{cx, cy, (float)Math.cos(a)*spd, (float)Math.sin(a)*spd - 1f,
                22+rng.nextInt(25), 50, 70+rng.nextInt(60), 180+rng.nextInt(60), 35, 3+rng.nextInt(5), 1});
        }
    }

    private void updateParticles() {
        particles.removeIf(p -> { p[0]+=p[2]; p[1]+=p[3]; p[3]+=0.06f; p[2]*=0.93f; p[4]--; return p[4]<=0; });
    }

    private void trackFacing(GameState s) {
        for (Player p : s.getPlayers().values()) {
            float[] prev = lastPositions.get(p.getId());
            if (prev != null) {
                double dx = p.getXf()-prev[0], dy = p.getYf()-prev[1];
                if (dx*dx+dy*dy > 0.5) facingAngles.put(p.getId(), Math.atan2(dy, dx));
            }
            lastPositions.put(p.getId(), new float[]{p.getXf(), p.getYf()});
        }
    }

    // ── Paint ─────────────────────────────────────────────────────────────────

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
            g2.translate((int)((Math.random()*2-1)*shakeIntensity),
                         (int)((Math.random()*2-1)*shakeIntensity));
            shakeFrames--;
        }

        GameState gs = gameState;
        if (gs == null) { renderConnecting(g2); g2.dispose(); return; }

        switch (gs.getCurrentPhase()) {
            case WAITING   -> renderLobby(g2, gs);
            case PLAYING   -> renderGame(g2, gs);
            case GAME_OVER -> { renderGame(g2, gs); renderGameOver(g2, gs); }
        }

        g2.setTransform(origXform);
        g2.dispose();
    }

    // ── Connecting ────────────────────────────────────────────────────────────

    private void renderConnecting(Graphics2D g) {
        g.setColor(new Color(5,10,5));
        g.fillRect(0,0,VW,VH);
        float p = 0.5f + 0.5f*(float)Math.sin(animTick*0.09);
        g.setColor(new Color(40,180,60,(int)(140+115*p)));
        g.setFont(new Font("Segoe UI", Font.BOLD, 22));
        drawCentered(g, "Connecting to server...", VW/2, VH/2);
    }

    // ── Lobby ─────────────────────────────────────────────────────────────────

    private void renderLobby(Graphics2D g, GameState gs) {
        g.setColor(new Color(5, 9, 5));  g.fillRect(0, 0, VW, VH);
        Paint sv = g.getPaint();
        g.setPaint(new RadialGradientPaint(VW*.28f, VH*.38f, 320,
            new float[]{0f,1f}, new Color[]{new Color(28,70,28,38), new Color(0,0,0,0)}));
        g.fillRect(0,0,VW,VH);
        g.setPaint(new RadialGradientPaint(VW*.72f, VH*.60f, 300,
            new float[]{0f,1f}, new Color[]{new Color(90,22,22,30), new Color(0,0,0,0)}));
        g.fillRect(0,0,VW,VH);
        g.setPaint(sv);

        // Biohazard glyph
        float glph = 0.5f+0.5f*(float)Math.sin(animTick*0.065);
        g.setFont(new Font("Segoe UI Symbol", Font.PLAIN, (int)(90+10*glph)));
        g.setColor(new Color(165,20,20,(int)(75+65*glph)));
        drawCentered(g, "☣", VW/2, 138);

        // Title
        g.setFont(new Font("Impact", Font.PLAIN, 65));
        g.setColor(new Color(0,0,0,195)); drawCentered(g, "ZOMBIE SURVIVAL", VW/2+4, 212);
        g.setColor(new Color(232,48,48)); drawCentered(g, "ZOMBIE SURVIVAL", VW/2, 208);

        g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        g.setColor(new Color(100,160,100));
        drawCentered(g, "Real-time Multiplayer  •  Weapons  •  NPC Zombies  •  Big Map  •  Java TCP/IP", VW/2, 232);

        g.setColor(new Color(50,85,50)); g.fillRect(VW/2-220,244,440,1);

        // Player list
        Collection<Player> players = gs.getPlayers().values();
        int listY = 268;
        g.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g.setColor(new Color(95,168,95));
        drawCentered(g, "CONNECTED PLAYERS  (" + players.size() + ")", VW/2, listY);
        for (Player p : players) {
            listY += 36;
            boolean isMe = p.getId().equals(myPlayerId);
            int ax = VW/2-120, ay = listY-18;
            g.setColor(isMe ? new Color(55,185,70) : new Color(42,148,55));
            g.fillOval(ax+2, ay+2, 24, 24);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Segoe UI", Font.BOLD, 11));
            drawCentered(g, p.getName().substring(0,1).toUpperCase(), ax+14, ay+18);
            g.setFont(new Font("Segoe UI", isMe ? Font.BOLD : Font.PLAIN, 13));
            g.setColor(isMe ? Color.WHITE : new Color(172,210,172));
            g.drawString(p.getName() + (isMe ? "  ★ YOU" : ""), VW/2-82, listY);
        }

        float wp = 0.5f+0.5f*(float)Math.sin(animTick*0.072);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        g.setColor(new Color(130,195,130,(int)(80+175*wp)));
        drawCentered(g, "●  Waiting for players...  (minimum 2 to start)", VW/2, VH-110);

        int bx = VW/2-290, by = VH-96;
        g.setColor(new Color(12,22,12)); g.fillRoundRect(bx,by,580,74,10,10);
        g.setColor(new Color(38,62,38)); g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(bx,by,580,74,10,10);
        g.setFont(new Font("Segoe UI", Font.BOLD, 11));
        g.setColor(new Color(115,185,115));
        drawCentered(g, "CONTROLS", VW/2, by+16);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g.setColor(new Color(148,185,148));
        drawCentered(g, "Move: WASD/Arrows  •  SHIFT: Sprint  •  LMB: Shoot  •  F: Melee  •  Q: Lunge (Z)  •  E: Howl (Z)", VW/2, by+34);
        drawCentered(g, "1-5: Switch Weapon  •  ❤ Medkit  •  🦺 Vest  •  ⛑ Helmet  •  👟 Boots  •  🔑 Key +50pts", VW/2, by+52);
        drawCentered(g, "🧟 NPC Zombies roam the map — kill them for loot and score!", VW/2, by+70);
    }

    // ── Game world ────────────────────────────────────────────────────────────

    private void renderGame(Graphics2D g, GameState gs) {
        int cx = (int)camX, cy = (int)camY;

        // Apply camera transform — all world-space drawing uses this
        g.translate(-cx, -cy);

        // Clip to what's visible + 1-tile margin
        int startCol = Math.max(0, cx/TS - 1),   endCol = Math.min(MW-1, (cx+VW)/TS + 1);
        int startRow = Math.max(0, cy/TS - 1),   endRow = Math.min(MH-1, (cy+VH)/TS + 1);

        drawTiles     (g, startCol, endCol, startRow, endRow);
        drawWallFaces (g, startCol, endCol, startRow, endRow);
        drawDecorations(g, startCol, endCol, startRow, endRow);
        drawPickups   (g, gs);
        drawNpcs      (g, gs);
        drawPlayers   (g, gs);
        drawBullets   (g, gs);
        drawParticles (g);
        drawDynLighting(g, gs);
        drawKillFeed  (g, gs);

        // Restore to screen space
        g.translate(cx, cy);

        drawMinimap   (g, gs);
        drawCrosshair (g, gs);
        drawZombieVig (g, gs);
    }

    // ── 1. Tiles ──────────────────────────────────────────────────────────────

    private void drawTiles(Graphics2D g, int sc, int ec, int sr, int er) {
        for (int row = sr; row <= er; row++) {
            for (int col = sc; col <= ec; col++) {
                int tile = GameState.MAP[row][col];
                int px = col*TS, py = row*TS;
                switch (tile) {
                    case 0 -> g.drawImage((row+col)%2==0 ? tileGrassA : tileGrassB, px, py, null);
                    case 1 -> g.drawImage(tileWall, px, py, null);
                    case 2 -> {
                        g.drawImage(tileSafe, px, py, null);
                        float gl = 0.3f+0.22f*(float)Math.sin(animTick*0.055+col*0.35+row*0.35);
                        g.setColor(new Color(28,198,105,(int)(68*gl)));
                        g.fillRect(px, py, TS, TS);
                    }
                }
            }
        }
    }

    // ── 2. Wall faces (pseudo-3D) ─────────────────────────────────────────────

    private void drawWallFaces(Graphics2D g, int sc, int ec, int sr, int er) {
        for (int row = sr; row <= er; row++) {
            for (int col = sc; col <= ec; col++) {
                if (GameState.MAP[row][col] != 1) continue;
                int px = col*TS, py = row*TS;
                if (row+1 < MH && GameState.MAP[row+1][col] != 1) {
                    g.setColor(new Color(30,33,44)); g.fillRect(px, py+TS, TS, 11);
                    Paint sv = g.getPaint();
                    g.setPaint(new GradientPaint(0,py+TS+11,new Color(0,0,0,65),0,py+TS+17,new Color(0,0,0,0)));
                    g.fillRect(px,py+TS+11,TS,6); g.setPaint(sv);
                    g.setColor(new Color(55,58,72)); g.drawLine(px,py+TS,px+TS-1,py+TS);
                }
                if (col+1 < MW && GameState.MAP[row][col+1] != 1) {
                    g.setColor(new Color(50,53,65)); g.fillRect(px+TS, py+3, 7, TS-3);
                    Paint sv = g.getPaint();
                    g.setPaint(new GradientPaint(px+TS+7,0,new Color(0,0,0,52),px+TS+13,0,new Color(0,0,0,0)));
                    g.fillRect(px+TS+7,py+3,6,TS-3); g.setPaint(sv);
                }
            }
        }
    }

    // ── 3. Decorations ────────────────────────────────────────────────────────

    private void drawDecorations(Graphics2D g, int sc, int ec, int sr, int er) {
        // Draw trees on grass tiles throughout the map (pattern-based)
        for (int row = sr; row <= er; row++) {
            for (int col = sc; col <= ec; col++) {
                if (GameState.MAP[row][col] != 0) continue;
                // Simple deterministic decoration placement
                int hash = (row * 73856093) ^ (col * 19349663);
                if (Math.abs(hash) % 40 == 0) drawTree(g, col*TS, row*TS);
                else if (Math.abs(hash) % 55 == 1) drawCrate(g, col*TS+4, row*TS+4);
            }
        }
    }

    private void drawTree(Graphics2D g, int px, int py) {
        g.setColor(new Color(0,0,0,55)); g.fillOval(px+2,py+14,28,14);
        g.setColor(new Color(82,54,24)); g.fillOval(px+11,py+10,10,12);
        g.setColor(new Color(18,68,18)); g.fillOval(px,py,32,32);
        g.setColor(new Color(26,95,26)); g.fillOval(px+4,py+4,24,24);
        g.setColor(new Color(40,125,40)); g.fillOval(px+9,py+7,14,14);
    }

    private void drawCrate(Graphics2D g, int px, int py) {
        g.setColor(new Color(0,0,0,50)); g.fillRect(px+3,py+22,24,5);
        g.setColor(new Color(108,74,34)); g.fillRect(px,py,24,22);
        g.setColor(new Color(78,52,22)); g.drawRect(px,py,24,22);
        g.drawLine(px,py+11,px+24,py+11); g.drawLine(px+12,py,px+12,py+22);
        g.setColor(new Color(138,98,48)); g.drawLine(px+1,py+1,px+22,py+1);
    }

    // ── 4. Pickups ────────────────────────────────────────────────────────────

    private void drawPickups(Graphics2D g, GameState gs) {
        for (Pickup pk : new ArrayList<>(gs.getPickups())) {
            if (pk.isCollected()) continue;
            int bob = (int)(3.5*Math.sin(animTick*0.09+(pk.getX()+pk.getY())*0.018));
            int px = pk.getX(), py = pk.getY()+bob, s = Pickup.SIZE;
            float gl = 0.5f+0.5f*(float)Math.sin(animTick*0.10);
            Paint sv = g.getPaint();
            Color glowCol = pickupGlowColor(pk.getType());
            g.setPaint(new RadialGradientPaint(px+s/2f, py+s/2f, s+10,
                new float[]{0f,1f}, new Color[]{new Color(glowCol.getRed(),glowCol.getGreen(),glowCol.getBlue(),(int)(55*gl)), new Color(0,0,0,0)}));
            g.fillOval(px-10, py-10, s+20, s+20);
            g.setPaint(sv);
            drawPickupIcon(g, px, py, s, pk.getType());
        }
    }

    private Color pickupGlowColor(Pickup.Type t) {
        return switch (t) {
            case MEDKIT, BANDAGE -> new Color(220,0,0);
            case KEY             -> new Color(220,180,0);
            case HELMET, VEST    -> new Color(80,140,255);
            case BOOTS           -> new Color(100,230,100);
            case PISTOL, PISTOL_AMMO -> new Color(180,200,255);
            case SHOTGUN, SHOTGUN_AMMO -> new Color(255,195,100);
            case RIFLE, RIFLE_AMMO -> new Color(100,255,130);
            case BAT             -> new Color(200,150,80);
            case GRENADE         -> new Color(255,120,0);
            case MOLOTOV         -> new Color(255,80,0);
            default              -> new Color(220,220,0);
        };
    }

    private void drawPickupIcon(Graphics2D g, int px, int py, int s, Pickup.Type t) {
        Color fill = pickupGlowColor(t);
        g.setColor(fill.darker());
        g.fill(new RoundRectangle2D.Float(px, py, s, s, 6, 6));
        g.setColor(fill);
        g.setStroke(new BasicStroke(1.3f));
        g.draw(new RoundRectangle2D.Float(px, py, s, s, 6, 6));
        g.setStroke(new BasicStroke(1f));
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 12));
        String icon = switch (t) {
            case MEDKIT        -> "❤";
            case BANDAGE       -> "+";
            case KEY           -> "🔑";
            case HELMET        -> "⛑";
            case VEST          -> "🦺";
            case BOOTS         -> "👟";
            case ARMOR         -> "🛡";
            case SPEED_BOOST   -> "⚡";
            case PISTOL        -> "🔫";
            case SHOTGUN       -> "S";
            case RIFLE         -> "R";
            case BAT           -> "🏏";
            case PISTOL_AMMO   -> "•";
            case RIFLE_AMMO    -> "R•";
            case SHOTGUN_AMMO  -> "S•";
            case GRENADE       -> "💣";
            case MOLOTOV       -> "🔥";
        };
        FontMetrics fm = g.getFontMetrics();
        g.drawString(icon, px + (s-fm.stringWidth(icon))/2, py + s - 4);
    }

    // ── 5. NPC Zombies ────────────────────────────────────────────────────────

    private void drawNpcs(Graphics2D g, GameState gs) {
        for (NpcZombie npc : gs.getNpcZombies().values()) {
            if (!npc.isAlive()) continue;
            float nx = npc.getX(), ny = npc.getY();
            int cx = (int)nx + NpcZombie.SIZE/2;
            int cy = (int)ny + NpcZombie.SIZE/2;

            // Shadow
            g.setColor(new Color(0,0,0,80)); g.fillOval(cx-12, cy-6, 24, 14);

            // Body (NPC zombie is darker/meaner looking)
            g.setColor(new Color(50,70,30)); g.fillOval(cx-10,cy-10,20,20);
            g.setColor(new Color(35,55,15)); g.setStroke(new BasicStroke(1.5f));
            g.drawOval(cx-10,cy-10,20,20); g.setStroke(new BasicStroke(1f));

            // Head
            double ang = npc.getFacingAngle();
            int hx = cx+(int)(7*Math.cos(ang)), hy = cy+(int)(7*Math.sin(ang));
            g.setColor(new Color(100,125,55)); g.fillOval(hx-7,hy-7,14,14);
            g.setColor(new Color(200,20,20)); g.fillOval(hx+1,hy-1,4,3); // red eye

            // HP bar
            g.setColor(new Color(15,15,15)); g.fillRect(cx-12,cy-20,24,4);
            int hpW = (int)(24f * npc.getHealth() / NpcZombie.MAX_HEALTH);
            g.setColor(new Color(200,40,40)); g.fillRect(cx-12,cy-20,Math.max(1,hpW),4);

            // "NPC" label
            g.setFont(new Font("Segoe UI", Font.BOLD, 8));
            g.setColor(new Color(200,90,90));
            g.drawString("NPC", cx-8, cy-22);
        }
    }

    // ── 6. Players ────────────────────────────────────────────────────────────

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

        if (boosted) {
            float ba = 0.4f+0.4f*(float)Math.sin(animTick*0.15);
            g.setColor(new Color(255,220,0,(int)(90*ba)));
            g.setStroke(new BasicStroke(3f)); g.drawOval(cx-18,cy-18,36,36);
            g.setStroke(new BasicStroke(1f));
        }
        if (!zombie && GameState.isSafeZoneAt(cx,cy)) {
            float sg = 0.35f+0.45f*(float)Math.sin(animTick*0.09);
            g.setColor(new Color(45,220,115,(int)(115*sg)));
            g.setStroke(new BasicStroke(2.5f)); g.drawOval(cx-20,cy-20,40,40);
            g.setStroke(new BasicStroke(1f));
        }
        if (isMe) {
            float mg = 0.5f+0.5f*(float)Math.sin(animTick*0.11);
            g.setColor(new Color(255,255,255,(int)(45+85*mg)));
            g.setStroke(new BasicStroke(2f)); g.drawOval(cx-19,cy-19,38,38);
            g.setStroke(new BasicStroke(1f));
        }
        if (p.isStunned()) {
            g.setColor(new Color(255,220,0,120));
            g.setStroke(new BasicStroke(2f)); g.drawOval(cx-22,cy-22,44,44);
            g.setStroke(new BasicStroke(1f));
        }

        // Shadow
        g.setColor(new Color(0,0,0,85)); g.fillOval(cx-14,cy-8,28,17);

        // Legs
        Color legCol = zombie ? new Color(48,62,30) : new Color(34,48,76);
        g.setColor(legCol); g.fillOval((int)(cx-hDx)-5,(int)(cy-hDy)-4,11,10);

        // Body
        Color bodyCol = zombie ? new Color(70,90,44) : new Color(44,68,112);
        Color bodyBdr = zombie ? new Color(46,64,26) : new Color(24,46,86);
        g.setColor(bodyCol); g.fillOval(cx-11,cy-11,22,22);
        g.setColor(bodyBdr); g.setStroke(new BasicStroke(1.5f)); g.drawOval(cx-11,cy-11,22,22);
        g.setStroke(new BasicStroke(1f));
        if (!zombie) { g.setColor(new Color(28,44,74)); g.fillRect(cx-9,cy-2,18,3); }

        // Arms (weapon visible for survivors)
        Color armCol = zombie ? new Color(80,102,50) : new Color(52,80,125);
        g.setColor(armCol);
        g.fillOval((int)(cx+aDx)-5,(int)(cy+aDy)-5,10,10);
        g.fillOval((int)(cx-aDx)-5,(int)(cy-aDy)-5,10,10);
        // Weapon in hand
        if (!zombie && p.getCurrentWeapon() != Weapon.FIST) {
            drawWeaponInHand(g, cx, cy, angle, p.getCurrentWeapon());
        }

        // Head
        int hx = (int)(cx+hDx), hy = (int)(cy+hDy);
        Color skinCol = zombie ? new Color(126,154,76) : new Color(208,170,126);
        Color skinBdr = zombie ? new Color(90,116,54)  : new Color(170,132,94);
        g.setColor(skinCol); g.fillOval(hx-9,hy-9,18,18);
        g.setColor(skinBdr); g.setStroke(new BasicStroke(1.5f)); g.drawOval(hx-9,hy-9,18,18);
        g.setStroke(new BasicStroke(1f));

        // Eyes
        float ep=(float)(3.8*Math.cos(perp)), ep2=(float)(3.8*Math.sin(perp));
        float ef=(float)(2.5*Math.cos(angle)), ef2=(float)(2.5*Math.sin(angle));
        g.setColor(new Color(240,242,248));
        g.fillOval((int)(hx+ep+ef)-3,(int)(hy+ep2+ef2)-3,6,6);
        g.fillOval((int)(hx-ep+ef)-3,(int)(hy-ep2+ef2)-3,6,6);
        g.setColor(zombie ? new Color(215,25,25) : new Color(16,16,60));
        g.fillOval((int)(hx+ep+ef)-1,(int)(hy+ep2+ef2)-1,3,3);
        g.fillOval((int)(hx-ep+ef)-1,(int)(hy-ep2+ef2)-1,3,3);

        // Helmet / wound
        if (!zombie) {
            Color hCol = p.hasHelmet() ? new Color(65,135,200) : (p.hasVest() ? new Color(80,120,180) : new Color(46,68,42));
            g.setColor(hCol);   g.fillArc(hx-10,hy-11,20,14,0,180);
            g.setColor(hCol.darker()); g.drawArc(hx-10,hy-11,20,14,0,180);
        } else {
            g.setColor(new Color(175,16,16,195));
            g.fillOval(hx+(int)ef,hy+(int)ef2,6,3); g.fillOval(hx+(int)ef-2,hy+(int)ef2+4,3,2);
        }

        // Name tag
        g.setFont(new Font("Segoe UI", Font.BOLD, 10));
        FontMetrics fm = g.getFontMetrics();
        String label = p.getName() + (zombie ? " ☣" : "");
        int tw = fm.stringWidth(label);
        int tagX = cx-tw/2, tagY = cy-32;
        g.setColor(new Color(0,0,0,175)); g.fillRoundRect(tagX-4,tagY-12,tw+8,14,4,4);
        g.setColor(zombie ? new Color(100,238,100) : new Color(175,215,255));
        g.drawString(label, tagX, tagY);

        // HP bar (above name)
        int hbW=Player.SIZE+14, hbX=cx-hbW/2, hbY=cy-44;
        g.setColor(new Color(15,18,28,195)); g.fillRoundRect(hbX,hbY,hbW,5,2,2);
        int hp = p.getHealth();
        if (hp>0) {
            Color hpc = hp>60?new Color(36,190,60):hp>30?new Color(220,150,16):new Color(210,40,40);
            g.setColor(hpc); g.fillRoundRect(hbX,hbY,Math.max(1,hbW*hp/100),5,2,2);
        }
        if (p.getArmor()>0 || p.hasVest()) {
            int abY=hbY+7; g.setColor(new Color(12,15,28,190)); g.fillRoundRect(hbX,abY,hbW,3,1,1);
            int arVal = p.hasVest() ? 60 : p.getArmor();
            int arMax = p.hasVest() ? 60 : 60;
            g.setColor(new Color(65,135,210)); g.fillRoundRect(hbX,abY,Math.max(1,hbW*arVal/arMax),3,1,1);
        }
    }

    private void drawWeaponInHand(Graphics2D g, int cx, int cy, double angle, Weapon w) {
        double perp = angle + Math.PI/2.0;
        int ax = cx + (int)(9.5*Math.cos(perp)) + (int)(5*Math.cos(angle));
        int ay = cy + (int)(9.5*Math.sin(perp)) + (int)(5*Math.sin(angle));

        AffineTransform old = g.getTransform();
        g.translate(ax, ay);
        g.rotate(angle);
        Color wc = switch (w) {
            case PISTOL  -> new Color(180,200,255);
            case SHOTGUN -> new Color(255,195,100);
            case RIFLE   -> new Color(100,230,130);
            case BAT     -> new Color(160,110,60);
            default      -> Color.GRAY;
        };
        g.setColor(wc);
        if (w == Weapon.BAT) {
            g.setStroke(new BasicStroke(3f)); g.drawLine(0, 0, 16, 0); g.setStroke(new BasicStroke(1f));
        } else if (w.isMelee()) {
            g.setStroke(new BasicStroke(3f)); g.drawLine(0, 0, 14, 0); g.setStroke(new BasicStroke(1f));
        } else {
            g.fillRoundRect(0,-2,w==Weapon.RIFLE?18:12,5,2,2);
            g.setColor(wc.darker()); g.fillRect(w==Weapon.RIFLE?14:10,-4,3,3);
        }
        g.setTransform(old);
    }

    // ── 7. Bullets ────────────────────────────────────────────────────────────

    private void drawBullets(Graphics2D g, GameState gs) {
        for (Bullet b : new ArrayList<>(gs.getBullets())) {
            float bx = b.getX(), by = b.getY();
            Color col = switch (b.getWeapon()) {
                case PISTOL  -> new Color(255,230,100);
                case SHOTGUN -> new Color(255,180,60);
                case RIFLE   -> new Color(100,255,200);
                default      -> new Color(255,255,100);
            };
            // Trail
            g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 80));
            g.setStroke(new BasicStroke(2f));
            g.drawLine((int)(bx-b.getDx()*2),(int)(by-b.getDy()*2),(int)bx,(int)by);
            g.setStroke(new BasicStroke(1f));
            // Head
            g.setColor(col);
            g.fillOval((int)bx-3,(int)by-3,6,6);
        }
    }

    // ── 8. Particles ──────────────────────────────────────────────────────────

    private void drawParticles(Graphics2D g) {
        for (float[] p : new ArrayList<>(particles)) {
            float alpha = p[4]/p[5];
            int a = (int)(alpha*200);
            if (a<=0) continue;
            g.setColor(new Color((int)p[6],(int)p[7],(int)p[8],a));
            int sz = (int)(p[9]*alpha)+1;
            g.fillOval((int)p[0]-sz/2,(int)p[1]-sz/2,sz,sz);
        }
    }

    // ── 9. Dynamic lighting ───────────────────────────────────────────────────

    private void drawDynLighting(Graphics2D g, GameState gs) {
        if (lightMap==null||lightMap.getWidth()!=VW||lightMap.getHeight()!=VH)
            lightMap = new BufferedImage(VW, VH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D lg = lightMap.createGraphics();
        lg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        lg.setComposite(AlphaComposite.Clear); lg.fillRect(0,0,VW,VH);
        lg.setComposite(AlphaComposite.Src);
        lg.setColor(new Color(0,0,0,155)); lg.fillRect(0,0,VW,VH);
        lg.setComposite(AlphaComposite.DstOut);

        for (Player p : gs.getPlayers().values()) {
            int wx = p.getX()+Player.SIZE/2, wy = p.getY()+Player.SIZE/2;
            int sx = wx-(int)camX, sy = wy-(int)camY;
            boolean isMe = p.getId().equals(myPlayerId);
            float rad = isMe ? (p.isInfected() ? 110 : 170) : (p.isInfected() ? 80 : 115);
            if (p.hasSpeedBoost()) rad *= 1.15f;
            if (sx+rad<0||sx-rad>VW||sy+rad<0||sy-rad>VH) continue;
            try {
                lg.setPaint(new RadialGradientPaint(sx, sy, rad,
                    new float[]{0f,0.5f,1f},
                    new Color[]{new Color(255,255,255,255),new Color(255,255,255,160),new Color(255,255,255,0)}));
                lg.fillOval((int)(sx-rad),(int)(sy-rad),(int)(rad*2),(int)(rad*2));
            } catch (Exception ignored) {}
        }
        // NPC zombies emit faint red light
        for (NpcZombie npc : gs.getNpcZombies().values()) {
            if (!npc.isAlive()) continue;
            int sx = (int)(npc.getX()+NpcZombie.SIZE/2-camX);
            int sy = (int)(npc.getY()+NpcZombie.SIZE/2-camY);
            if (sx<-60||sx>VW+60||sy<-60||sy>VH+60) continue;
            try {
                lg.setPaint(new RadialGradientPaint(sx,sy,50,
                    new float[]{0f,1f}, new Color[]{new Color(255,80,0,120),new Color(0,0,0,0)}));
                lg.fillOval(sx-50,sy-50,100,100);
            } catch (Exception ignored) {}
        }
        lg.dispose();

        // Composite to main canvas (screen space)
        AffineTransform old = g.getTransform();
        g.setTransform(new AffineTransform()); // reset to screen space
        g.setComposite(AlphaComposite.SrcOver);
        g.drawImage(lightMap, (int)camX, (int)camY, null); // align with camera
        // Actually draw in screen space
        g.drawImage(lightMap, 0, 0, null);
        g.setTransform(old);
    }

    // ── 10. Kill feed ─────────────────────────────────────────────────────────

    private void drawKillFeed(Graphics2D g, GameState gs) {
        List<KillFeedEntry> feed = new ArrayList<>(gs.getKillFeed());
        if (feed.isEmpty()) return;
        // Draw in world space top-right (offset by camera so it stays on screen)
        int kx = (int)camX + VW - 270;
        int ky = (int)camY + 14;
        for (int i = feed.size()-1; i >= 0; i--) {
            KillFeedEntry e = feed.get(i);
            long age = System.currentTimeMillis() - e.getTimestamp();
            float alpha = Math.max(0f, 1f - age / 6000f);
            int a = (int)(alpha * 200);
            if (a <= 0) continue;
            String text = e.getKillerName() + " " + e.icon() + " " + e.getVictimName();
            g.setFont(new Font("Segoe UI", Font.BOLD, 11));
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(text);
            g.setColor(new Color(0,0,0,(int)(a*0.7f)));
            g.fillRoundRect(kx-tw-8, ky-13, tw+12, 16, 4, 4);
            g.setColor(new Color(220,220,220,a));
            g.drawString(text, kx-tw, ky);
            ky += 22;
        }
    }

    // ── 11. Minimap ───────────────────────────────────────────────────────────

    private void drawMinimap(Graphics2D g, GameState gs) {
        int mmW=190, mmH=140, mmX=VW-mmW-10, mmY=10;
        float sx = mmW / (float)CW, sy = mmH / (float)CH;

        g.setColor(new Color(0,0,0,175)); g.fillRoundRect(mmX-2,mmY-2,mmW+4,mmH+4,6,6);
        g.setColor(new Color(15,20,15,220)); g.fillRect(mmX,mmY,mmW,mmH);

        // Tiles (zoomed out — sample every 4 tiles)
        for (int row = 0; row < MH; row+=2) {
            for (int col = 0; col < MW; col+=2) {
                int tile = GameState.MAP[row][col];
                Color tc = tile==1 ? new Color(50,55,70) : tile==2 ? new Color(28,95,60) : new Color(25,42,25);
                g.setColor(tc);
                g.fillRect(mmX+(int)(col*TS*sx), mmY+(int)(row*TS*sy), Math.max(1,(int)(2*TS*sx)), Math.max(1,(int)(2*TS*sy)));
            }
        }

        // Viewport rectangle
        g.setColor(new Color(255,255,255,40));
        g.drawRect(mmX+(int)(camX*sx), mmY+(int)(camY*sy), (int)(VW*sx), (int)(VH*sy));

        // NPC zombies
        for (NpcZombie npc : gs.getNpcZombies().values()) {
            if (!npc.isAlive()) continue;
            g.setColor(new Color(180,40,40,180));
            g.fillOval(mmX+(int)(npc.getX()*sx)-2, mmY+(int)(npc.getY()*sy)-2,4,4);
        }

        // Players
        for (Player p : gs.getPlayers().values()) {
            boolean isMe = p.getId().equals(myPlayerId);
            g.setColor(p.isInfected() ? new Color(80,210,80) : (isMe ? Color.WHITE : new Color(100,160,255)));
            int mx = mmX+(int)(p.getXf()*sx), my = mmY+(int)(p.getYf()*sy);
            g.fillOval(mx-3,my-3,6,6);
            if (isMe) { g.setColor(new Color(255,255,255,100)); g.drawOval(mx-5,my-5,10,10); }
        }

        g.setColor(new Color(60,90,60)); g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(mmX-2,mmY-2,mmW+4,mmH+4,6,6);
        g.setFont(new Font("Segoe UI", Font.BOLD, 9)); g.setColor(new Color(80,120,80));
        g.drawString("MAP", mmX+4, mmY+mmH+12);
    }

    // ── 12. Crosshair ─────────────────────────────────────────────────────────

    private void drawCrosshair(Graphics2D g, GameState gs) {
        Player me = gs.getPlayer(myPlayerId);
        if (me == null || me.isInfected()) return;
        int mx = mouseScreenX, my = mouseScreenY;
        g.setColor(new Color(255,255,255,180));
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(mx-10,my,mx-4,my); g.drawLine(mx+4,my,mx+10,my);
        g.drawLine(mx,my-10,mx,my-4); g.drawLine(mx,my+4,mx,my+10);
        g.drawOval(mx-4,my-4,8,8);
        g.setStroke(new BasicStroke(1f));
    }

    // ── 13. Zombie vignette ───────────────────────────────────────────────────

    private void drawZombieVig(Graphics2D g, GameState gs) {
        Player me = gs.getPlayer(myPlayerId);
        if (me == null || !me.isInfected()) return;
        float pulse = 0.3f+0.25f*(float)Math.sin(animTick*0.07);
        Paint sv = g.getPaint();
        g.setPaint(new RadialGradientPaint(VW/2f,VH/2f,Math.max(VW,VH)*0.6f,
            new float[]{0.45f,1f}, new Color[]{new Color(0,0,0,0),new Color(140,0,0,(int)(140*pulse))}));
        g.fillRect(0,0,VW,VH);
        g.setPaint(sv);
    }

    // ── 14. Game over overlay ─────────────────────────────────────────────────

    private void renderGameOver(Graphics2D g, GameState gs) {
        // Dark overlay
        g.setColor(new Color(0,0,0,160)); g.fillRect(0,0,VW,VH);

        String winner = gs.getWinnerMessage();
        boolean survWin = winner.contains("SURVIVOR");
        Color titleCol  = survWin ? new Color(80,200,100) : new Color(210,50,50);

        g.setFont(new Font("Impact", Font.PLAIN, 62));
        g.setColor(new Color(0,0,0,180)); drawCentered(g,winner,VW/2+3,VH/2-60+3);
        g.setColor(titleCol); drawCentered(g,winner,VW/2,VH/2-60);

        g.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        g.setColor(new Color(180,185,200));
        drawCentered(g,"Resetting in " + gs.getGameOverCountdown() + " seconds...", VW/2, VH/2);

        // Top 3 scoreboard
        List<Player> ranked = new ArrayList<>(gs.getPlayers().values());
        ranked.sort((a,b) -> b.getScore()-a.getScore());
        int sy = VH/2+38;
        g.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g.setColor(new Color(160,165,185));
        drawCentered(g,"─── SCOREBOARD ───", VW/2, sy); sy += 26;
        for (int i=0;i<Math.min(ranked.size(),5);i++) {
            Player p = ranked.get(i);
            String line = (i+1)+".  "+p.getName()+"   "+p.getScore()+" pts   "+p.getKills()+" kills";
            g.setColor(i==0?new Color(215,180,25):i==1?new Color(180,185,195):i==2?new Color(180,140,80):new Color(130,135,155));
            g.setFont(new Font("Segoe UI", i<3 ? Font.BOLD : Font.PLAIN, 12));
            drawCentered(g,line,VW/2,sy); sy+=22;
        }
    }

    private void drawCentered(Graphics2D g, String text, int cx, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, cx-fm.stringWidth(text)/2, y);
    }

    // ── Tile texture builders ─────────────────────────────────────────────────

    private BufferedImage makeTileGrass(int variant) {
        BufferedImage img = new BufferedImage(TS,TS,BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        Color base = variant==0 ? new Color(32,55,24) : new Color(28,50,20);
        g.setColor(base); g.fillRect(0,0,TS,TS);
        Random rng = new Random(variant*997L);
        for (int i=0;i<28;i++) {
            int bx=rng.nextInt(TS), by=rng.nextInt(TS), bs=rng.nextInt(4)+1;
            Color bc = new Color(Math.min(255,base.getRed()+rng.nextInt(18)-9),
                                 Math.min(255,base.getGreen()+rng.nextInt(20)-10),
                                 Math.min(255,base.getBlue()+rng.nextInt(12)-6));
            g.setColor(bc); g.fillOval(bx,by,bs,bs);
        }
        g.dispose(); return img;
    }

    private BufferedImage makeTileWall() {
        BufferedImage img = new BufferedImage(TS,TS,BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(58,62,78)); g.fillRect(0,0,TS,TS);
        g.setColor(new Color(48,52,66));
        g.fillRect(0,0,TS,14); g.fillRect(16,14,TS,18);
        g.setColor(new Color(72,76,92));
        g.drawRect(1,1,TS-3,12);
        g.drawRect(17,15,TS-18,16);
        g.setColor(new Color(35,38,52)); g.drawRect(0,0,TS-1,TS-1);
        g.dispose(); return img;
    }

    private BufferedImage makeTileSafe() {
        BufferedImage img = new BufferedImage(TS,TS,BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(22,55,32)); g.fillRect(0,0,TS,TS);
        g.setColor(new Color(18,42,26));
        for (int i=0;i<TS;i+=8) { g.drawLine(i,0,i,TS); g.drawLine(0,i,TS,i); }
        g.setColor(new Color(28,80,44,80));
        g.fillRect(2,2,TS-4,TS-4);
        g.dispose(); return img;
    }

    private BufferedImage makeTileRoad() {
        BufferedImage img = new BufferedImage(TS,TS,BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(40,40,42)); g.fillRect(0,0,TS,TS);
        g.setColor(new Color(52,52,54)); g.drawRect(0,0,TS-1,TS-1);
        g.dispose(); return img;
    }
}
