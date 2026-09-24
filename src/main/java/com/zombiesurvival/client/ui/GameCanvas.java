package com.zombiesurvival.client.ui;

import com.zombiesurvival.shared.*;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main game rendering canvas (800 × 576 px).
 *
 * Features:
 *  - Pre-rendered tile textures (grass with blades, brick walls, medical-cross safe-zone)
 *  - Wall depth shadows (light from top-left)
 *  - Decorative trees and crates
 *  - Direction-aware player sprites (head tracks movement)
 *  - Animated pickups with glow
 *  - Corner minimap
 *  - Atmospheric lobby & cinematic game-over overlay
 */
public class GameCanvas extends JPanel {

    private static final int W  = GameState.CANVAS_W;   // 800
    private static final int H  = GameState.CANVAS_H;   // 576
    private static final int TS = GameState.TILE_SIZE;  // 32

    // ── Decorative element positions [col, row] ─────────────────────────────
    // All verified to sit on GRASS tiles (MAP value == 0)
    private static final int[][] TREES = {
        {7,2},{17,2},{7,15},{17,15},{23,6},{1,6},{1,12},{23,12},{6,8},{18,8}
    };
    private static final int[][] CRATES = {
        {8,7},{16,10},{6,9},{18,6},{2,15},{22,15}
    };

    // ── Pre-rendered tile textures (created once in constructor) ────────────
    private final BufferedImage tileGrassA;
    private final BufferedImage tileGrassB;
    private final BufferedImage tileWall;
    private final BufferedImage tileSafe;

    // ── State ───────────────────────────────────────────────────────────────
    private volatile GameState  gameState;
    private volatile String     myPlayerId;
    private int                 animTick = 0;
    private int                 mouseX = -1, mouseY = -1;

    // Per-player facing angle tracked from position deltas
    private final Map<String, Double>  facingAngles  = new ConcurrentHashMap<>();
    private final Map<String, float[]> lastPositions = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════════
    //  Constructor
    // ═══════════════════════════════════════════════════════════════════════

    public GameCanvas() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(5, 10, 5));
        tileGrassA = makeTileGrass(0);
        tileGrassB = makeTileGrass(1);
        tileWall   = makeTileWall();
        tileSafe   = makeTileSafe();
        // Mouse tracking for menu hover effects
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override public void mouseMoved(java.awt.event.MouseEvent e) {
                mouseX = e.getX(); mouseY = e.getY();
            }
        });
        // ~30 FPS render loop
        new javax.swing.Timer(33, e -> { animTick++; repaint(); }).start();
    }

    // ── Public API ──────────────────────────────────────────────────────────

    public void setMyPlayerId(String id) { this.myPlayerId = id; }

    public void setGameState(GameState s) {
        if (s != null) trackFacing(s);
        this.gameState = s;
    }

    // ── Facing-direction tracking ────────────────────────────────────────────

    private void trackFacing(GameState newState) {
        for (Player p : newState.getPlayers().values()) {
            float[] prev = lastPositions.get(p.getId());
            if (prev != null) {
                double dx = p.getX() - prev[0], dy = p.getY() - prev[1];
                if (dx * dx + dy * dy > 0.5)
                    facingAngles.put(p.getId(), Math.atan2(dy, dx));
            }
            lastPositions.put(p.getId(), new float[]{p.getX(), p.getY()});
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Paint dispatcher
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        GameState gs = gameState;
        if (gs == null) { renderConnecting(g2); g2.dispose(); return; }

        switch (gs.getCurrentPhase()) {
            case WAITING   -> renderLobby(g2, gs);
            case PLAYING   -> renderGame(g2, gs);
            case GAME_OVER -> { renderGame(g2, gs); renderGameOver(g2, gs); }
        }
        g2.dispose();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Screen: Connecting
    // ═══════════════════════════════════════════════════════════════════════

    private void renderConnecting(Graphics2D g) {
        g.setColor(new Color(5, 10, 5));
        g.fillRect(0, 0, W, H);
        float p = 0.5f + 0.5f * (float) Math.sin(animTick * 0.09);
        g.setColor(new Color(40, 180, 60, (int)(140 + 115 * p)));
        g.setFont(new Font("Segoe UI", Font.BOLD, 22));
        drawCentered(g, "Connecting to server...", W / 2, H / 2);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Screen: Lobby
    // ═══════════════════════════════════════════════════════════════════════

    private void renderLobby(Graphics2D g, GameState gs) {
        // ── Dark atmospheric background ──
        g.setColor(new Color(8, 5, 12));
        g.fillRect(0, 0, W, H);

        // ── Pulsing red/green atmospheric fog ──
        Paint saved = g.getPaint();
        float fogPulse = 0.5f + 0.5f * (float) Math.sin(animTick * 0.04);
        g.setPaint(new RadialGradientPaint(W * 0.5f, H * 0.32f, 340,
            new float[]{0f, 1f}, new Color[]{new Color(130, 8, 8, (int)(42 * fogPulse)), new Color(0, 0, 0, 0)}));
        g.fillRect(0, 0, W, H);
        g.setPaint(new RadialGradientPaint(W * 0.2f, H * 0.75f, 260,
            new float[]{0f, 1f}, new Color[]{new Color(8, 80, 12, (int)(32 * fogPulse)), new Color(0, 0, 0, 0)}));
        g.fillRect(0, 0, W, H);
        g.setPaint(new RadialGradientPaint(W * 0.8f, H * 0.65f, 240,
            new float[]{0f, 1f}, new Color[]{new Color(90, 8, 8, (int)(28 * fogPulse)), new Color(0, 0, 0, 0)}));
        g.fillRect(0, 0, W, H);
        g.setPaint(saved);

        // ── Floating blood/ash particles ──
        Random rng = new Random(99L);
        for (int i = 0; i < 130; i++) {
            int px = rng.nextInt(W), py = rng.nextInt(H);
            float phase = (float) Math.sin(animTick * 0.032 + i * 0.45);
            int a = (int)(18 + 42 * phase);
            boolean isRed = rng.nextBoolean();
            g.setColor(isRed ? new Color(185, 25, 25, Math.max(0, Math.min(255, a)))
                             : new Color(28, 130, 38, Math.max(0, Math.min(255, a))));
            int drift = (int)(5 * Math.sin(animTick * 0.018 + i * 0.7));
            g.fillOval(px + drift, py, 2, 2);
        }

        // ── Zombies in 4 corners ──
        drawCornerZombie(g, 18, 18, 1);
        drawCornerZombie(g, W - 72, 18, 2);
        drawCornerZombie(g, 18, H - 130, 3);
        drawCornerZombie(g, W - 72, H - 130, 4);

        // ── Title glow ──
        float titlePulse = 0.6f + 0.4f * (float) Math.sin(animTick * 0.055);
        saved = g.getPaint();
        g.setPaint(new RadialGradientPaint(W / 2f, 105, 220,
            new float[]{0f, 1f}, new Color[]{new Color(210, 0, 0, (int)(55 * titlePulse)), new Color(0, 0, 0, 0)}));
        g.fillRect(0, 30, W, 150);
        g.setPaint(saved);

        // ── Title: "ZOMBIE ESCAPE" ──
        g.setFont(new Font("Impact", Font.BOLD, 74));
        // Deep shadow
        g.setColor(new Color(0, 0, 0, 210));
        drawCentered(g, "ZOMBIE ESCAPE", W / 2 + 5, 118 + 5);
        // Main red title
        g.setColor(new Color(220, 18, 18));
        drawCentered(g, "ZOMBIE ESCAPE", W / 2, 118);
        // Bright highlight overlay (pulsing)
        g.setColor(new Color(255, 55, 55, (int)(70 + 70 * titlePulse)));
        drawCentered(g, "ZOMBIE ESCAPE", W / 2, 116);

        // ── Subtitle ──
        g.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        g.setColor(new Color(170, 75, 75));
        drawCentered(g, "Survive the horde  \u2022  Multiplayer  \u2022  TCP/IP", W / 2, 148);

        // ── Divider ──
        g.setColor(new Color(110, 18, 18, 110));
        g.fillRect(W / 2 - 190, 162, 380, 2);

        // ── Menu Buttons ──
        int btnW = 280, btnH = 48;
        int btnX = (W - btnW) / 2;
        drawMenuButton(g, "\u25B6  START GAME", btnX, 185, btnW, btnH,
                       new Color(18, 155, 38), new Color(28, 210, 50));
        drawMenuButton(g, "\u2709  INVITE MEMBERS", btnX, 248, btnW, btnH,
                       new Color(38, 85, 200), new Color(48, 110, 245));

        // ── Player List ──
        Collection<Player> players = gs.getPlayers().values();
        int listY = 330;
        g.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g.setColor(new Color(190, 55, 55));
        drawCentered(g, "\u26A1 CONNECTED PLAYERS  (" + players.size() + ")", W / 2, listY);
        g.setColor(new Color(85, 18, 18, 90));
        g.fillRect(W / 2 - 140, listY + 8, 280, 1);

        for (Player p : players) {
            listY += 34;
            boolean isMe = p.getId().equals(myPlayerId);

            // Avatar glow for self
            int ax = W / 2 - 110, ay = listY - 16;
            if (isMe) {
                g.setColor(new Color(210, 35, 35, 35));
                g.fillOval(ax - 4, ay - 4, 35, 35);
            }
            g.setColor(new Color(42, 10, 10));
            g.fillOval(ax, ay, 27, 27);
            g.setColor(isMe ? new Color(225, 45, 45) : new Color(145, 38, 38));
            g.fillOval(ax + 2, ay + 2, 23, 23);
            g.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g.setColor(Color.WHITE);
            String init = p.getName().substring(0, 1).toUpperCase();
            drawCentered(g, init, ax + 13, ay + 17);

            // Name
            g.setFont(new Font("Segoe UI", isMe ? Font.BOLD : Font.PLAIN, 14));
            g.setColor(isMe ? Color.WHITE : new Color(210, 165, 165));
            g.drawString(p.getName() + (isMe ? "  \u2605 YOU" : ""), W / 2 - 75, listY);
        }

        // ── Status indicator ──
        if (players.size() < 2) {
            float wp = 0.5f + 0.5f * (float) Math.sin(animTick * 0.072);
            g.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            g.setColor(new Color(210, 75, 75, (int)(80 + 175 * wp)));
            drawCentered(g, "\u2620  Waiting for players...  (press Start when ready)", W / 2, H - 55);
        } else {
            float wp = 0.5f + 0.5f * (float) Math.sin(animTick * 0.1);
            g.setFont(new Font("Segoe UI", Font.BOLD, 15));
            g.setColor(new Color(38, 225, 58, (int)(120 + 135 * wp)));
            drawCentered(g, "\u2714  Ready to go!  Game starts now!", W / 2, H - 55);
        }

        // ── Controls hint bar ──
        g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g.setColor(new Color(105, 65, 65));
        drawCentered(g, "Move: WASD / Arrows  \u2022  Chat: Click panel \u2192 type \u2192 Enter  \u2022  Collect \u2764 Medkits & \uD83D\uDD11 Keys", W / 2, H - 28);
    }

    // ── Corner zombie sprite ────────────────────────────────────────────────

    private void drawCornerZombie(Graphics2D g, int cx, int cy, int id) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(animTick * 0.055 + id * 1.5);
        int armWave = (int)(5 * Math.sin(animTick * 0.065 + id * 1.2));
        int bodyBob = (int)(2 * Math.sin(animTick * 0.04 + id * 0.9));
        cy += bodyBob;

        // Eerie red aura
        Paint saved = g.getPaint();
        g.setPaint(new RadialGradientPaint(cx + 27f, cy + 40f, 65,
            new float[]{0f, 1f}, new Color[]{new Color(170, 8, 8, (int)(32 + 22 * pulse)), new Color(0, 0, 0, 0)}));
        g.fillOval(cx - 20, cy - 15, 95, 110);
        g.setPaint(saved);

        // Ground shadow
        g.setColor(new Color(0, 0, 0, 55));
        g.fillOval(cx + 6, cy + 92, 42, 12);

        // Legs (shambling)
        g.setColor(new Color(36, 50, 26));
        g.fillRoundRect(cx + 14, cy + 62, 10, 30, 4, 4);
        g.fillRoundRect(cx + 30, cy + 64, 10, 28, 4, 4);

        // Body
        g.setColor(new Color(46, 70, 34));
        g.fillRoundRect(cx + 9, cy + 28, 36, 40, 10, 10);
        // Tattered rips
        g.setColor(new Color(32, 48, 22));
        g.drawLine(cx + 14, cy + 38, cx + 20, cy + 55);
        g.drawLine(cx + 36, cy + 34, cx + 40, cy + 52);
        g.setColor(new Color(90, 15, 10, 80));
        g.fillOval(cx + 22, cy + 48, 8, 6);

        // Arms (reaching out with animation)
        g.setColor(new Color(48, 72, 36));
        g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(cx + 9,  cy + 38, cx - 6,  cy + 30 + armWave);
        g.drawLine(cx - 6,  cy + 30 + armWave, cx - 10, cy + 22 + armWave);
        g.drawLine(cx + 45, cy + 38, cx + 60, cy + 30 - armWave);
        g.drawLine(cx + 60, cy + 30 - armWave, cx + 64, cy + 22 - armWave);
        // Claw fingers
        g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(cx - 10, cy + 22 + armWave, cx - 14, cy + 17 + armWave);
        g.drawLine(cx - 10, cy + 22 + armWave, cx - 8,  cy + 16 + armWave);
        g.drawLine(cx + 64, cy + 22 - armWave, cx + 68, cy + 17 - armWave);
        g.drawLine(cx + 64, cy + 22 - armWave, cx + 62, cy + 16 - armWave);
        g.setStroke(new BasicStroke(1.0f));

        // Head
        g.setColor(new Color(52, 78, 40));
        g.fillOval(cx + 12, cy + 2, 30, 30);
        // Scars
        g.setColor(new Color(88, 20, 15, 120));
        g.drawLine(cx + 18, cy + 6, cx + 22, cy + 14);

        // Eyes (bright red, pulsing glow)
        int eyeAlpha = (int)(190 + 65 * pulse);
        g.setColor(new Color(255, 12, 0, eyeAlpha));
        g.fillOval(cx + 18, cy + 13, 8, 7);
        g.fillOval(cx + 30, cy + 13, 8, 7);
        // Bright pupil center
        g.setColor(new Color(255, 80, 40, eyeAlpha));
        g.fillOval(cx + 20, cy + 15, 4, 4);
        g.fillOval(cx + 32, cy + 15, 4, 4);
        // Eye glow effect
        saved = g.getPaint();
        g.setPaint(new RadialGradientPaint(cx + 27f, cy + 17f, 22,
            new float[]{0f, 1f}, new Color[]{new Color(255, 0, 0, (int)(50 * pulse)), new Color(0, 0, 0, 0)}));
        g.fillOval(cx + 8, cy + 2, 38, 34);
        g.setPaint(saved);

        // Mouth (open, teeth visible)
        g.setColor(new Color(22, 6, 6));
        g.fillArc(cx + 20, cy + 24, 14, 9, 0, -180);
        g.setColor(new Color(210, 210, 190));
        g.fillRect(cx + 23, cy + 24, 2, 3);
        g.fillRect(cx + 27, cy + 24, 2, 3);
        g.fillRect(cx + 31, cy + 24, 2, 3);
    }

    // ── Menu button with hover glow ─────────────────────────────────────────

    private void drawMenuButton(Graphics2D g, String text, int x, int y, int bw, int bh,
                                Color baseColor, Color hoverColor) {
        boolean hovered = mouseX >= x && mouseX <= x + bw && mouseY >= y && mouseY <= y + bh;
        Color col = hovered ? hoverColor : baseColor;

        // Hover glow
        if (hovered) {
            Paint saved = g.getPaint();
            g.setPaint(new RadialGradientPaint(x + bw / 2f, y + bh / 2f, bw / 2f + 30,
                new float[]{0f, 1f},
                new Color[]{new Color(col.getRed(), col.getGreen(), col.getBlue(), 45), new Color(0, 0, 0, 0)}));
            g.fillRect(x - 35, y - 25, bw + 70, bh + 50);
            g.setPaint(saved);
        }

        // Button body
        g.setColor(new Color(col.getRed() / 6, col.getGreen() / 6, col.getBlue() / 6, 225));
        g.fill(new RoundRectangle2D.Float(x, y, bw, bh, 14, 14));

        // Border
        g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), hovered ? 210 : 110));
        g.setStroke(new BasicStroke(hovered ? 2.2f : 1.4f));
        g.draw(new RoundRectangle2D.Float(x, y, bw, bh, 14, 14));
        g.setStroke(new BasicStroke(1.0f));

        // Text
        g.setFont(new Font("Segoe UI", Font.BOLD, 17));
        g.setColor(hovered ? Color.WHITE : new Color(210, 210, 210));
        drawCentered(g, text, x + bw / 2, y + bh / 2 + 6);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Screen: Game World
    // ═══════════════════════════════════════════════════════════════════════

    private void renderGame(Graphics2D g, GameState gs) {
        drawTiles(g);
        drawWallShadows(g);
        drawDecorations(g);
        drawPickups(g, gs);
        drawPlayers(g, gs);
        drawMinimap(g, gs);
        drawZombieVignette(g, gs);
    }

    // ── Tile rendering ───────────────────────────────────────────────────────

    private void drawTiles(Graphics2D g) {
        for (int row = 0; row < GameState.MAP_HEIGHT; row++) {
            for (int col = 0; col < GameState.MAP_WIDTH; col++) {
                int tile = GameState.MAP[row][col];
                int px = col * TS, py = row * TS;
                switch (tile) {
                    case 0 -> g.drawImage((row + col) % 2 == 0 ? tileGrassA : tileGrassB, px, py, null);
                    case 1 -> g.drawImage(tileWall, px, py, null);
                    case 2 -> {
                        g.drawImage(tileSafe, px, py, null);
                        // Animated green glow overlay
                        float gl = 0.3f + 0.22f * (float) Math.sin(animTick * 0.055 + col * 0.35 + row * 0.35);
                        g.setColor(new Color(28, 198, 105, (int)(72 * gl)));
                        g.fillRect(px, py, TS, TS);
                    }
                }
            }
        }
    }

    // ── Wall depth shadows (simulates top-left sunlight) ────────────────────

    private void drawWallShadows(Graphics2D g) {
        for (int row = 0; row < GameState.MAP_HEIGHT - 1; row++) {
            for (int col = 0; col < GameState.MAP_WIDTH - 1; col++) {
                if (GameState.MAP[row][col] == 1) {
                    // Shadow below wall
                    if (GameState.MAP[row + 1][col] != 1) {
                        int px = col * TS, py = (row + 1) * TS;
                        g.setColor(new Color(0, 0, 0, 72));
                        g.fillRect(px, py, TS, 7);
                    }
                    // Shadow to the right of wall
                    if (GameState.MAP[row][col + 1] != 1) {
                        int px = (col + 1) * TS, py = row * TS;
                        g.setColor(new Color(0, 0, 0, 55));
                        g.fillRect(px, py, 7, TS);
                    }
                }
            }
        }
    }

    // ── Decorative trees and crates ──────────────────────────────────────────

    private void drawDecorations(Graphics2D g) {
        for (int[] pos : TREES) {
            int px = pos[0] * TS, py = pos[1] * TS;
            // Ground shadow
            g.setColor(new Color(0, 0, 0, 62));
            g.fillOval(px + 2, py + 14, 28, 14);
            // Trunk
            g.setColor(new Color(85, 56, 26));
            g.fillOval(px + 11, py + 10, 10, 12);
            // Canopy layers (dark → light → highlight)
            g.setColor(new Color(20, 72, 20));
            g.fillOval(px,      py,     32, 32);
            g.setColor(new Color(28, 98, 28));
            g.fillOval(px + 4,  py + 4, 24, 24);
            g.setColor(new Color(42, 128, 42));
            g.fillOval(px + 9,  py + 7, 14, 14);
            // Rim shadow
            g.setColor(new Color(0, 0, 0, 38));
            g.setStroke(new BasicStroke(1.5f));
            g.drawOval(px + 1, py + 3, 30, 30);
            g.setStroke(new BasicStroke(1.0f));
        }
        for (int[] pos : CRATES) {
            int px = pos[0] * TS + 4, py = pos[1] * TS + 4;
            // Cast shadow
            g.setColor(new Color(0, 0, 0, 55));
            g.fillRect(px + 3, py + 22, 24, 5);
            // Wood body
            g.setColor(new Color(112, 78, 36));
            g.fillRect(px, py, 24, 22);
            // Plank separators
            g.setColor(new Color(82, 56, 24));
            g.drawRect(px, py, 24, 22);
            g.drawLine(px,      py + 11, px + 24, py + 11);
            g.drawLine(px + 12, py,      px + 12, py + 22);
            // Wood highlight
            g.setColor(new Color(140, 102, 50));
            g.drawLine(px + 1, py + 1, px + 22, py + 1);
            g.drawLine(px + 1, py + 1, px + 1,  py + 20);
        }
    }

    // ── Pickups ──────────────────────────────────────────────────────────────

    private void drawPickups(Graphics2D g, GameState gs) {
        List<Pickup> pickups = new ArrayList<>(gs.getPickups());
        for (Pickup pk : pickups) {
            if (pk.isCollected()) continue;
            int bob = (int)(3.5 * Math.sin(animTick * 0.09 + (pk.getX() + pk.getY()) * 0.018));
            int px = pk.getX(), py = pk.getY() + bob, s = Pickup.SIZE;

            float gl = 0.5f + 0.5f * (float) Math.sin(animTick * 0.10);
            Paint saved = g.getPaint();

            if (pk.getType() == Pickup.Type.MEDKIT) {
                // Glow halo
                g.setPaint(new RadialGradientPaint(px + s/2f, py + s/2f, s + 8,
                    new float[]{0f,1f}, new Color[]{new Color(220,0,0,(int)(50*gl)), new Color(0,0,0,0)}));
                g.fillOval(px - 8, py - 8, s + 16, s + 16);
                g.setPaint(saved);
                // Body
                g.setColor(new Color(200, 28, 28));
                g.fill(new RoundRectangle2D.Float(px, py, s, s, 5, 5));
                g.setColor(new Color(240, 68, 68));
                g.setStroke(new BasicStroke(1.2f));
                g.draw(new RoundRectangle2D.Float(px, py, s, s, 5, 5));
                g.setStroke(new BasicStroke(1.0f));
                // White cross
                int arm = s / 3;
                g.setColor(Color.WHITE);
                g.fillRect(px + arm, py + 3,   arm, s - 6);
                g.fillRect(px + 3,   py + arm, s - 6, arm);
            } else {
                // Gold key glow
                g.setPaint(new RadialGradientPaint(px + s/2f, py + s/2f, s + 7,
                    new float[]{0f,1f}, new Color[]{new Color(220,180,0,(int)(55*gl)), new Color(0,0,0,0)}));
                g.fillOval(px - 7, py - 7, s + 14, s + 14);
                g.setPaint(saved);
                // Key ring
                int r = s / 2 - 1;
                g.setColor(new Color(205, 165, 0));
                g.fillOval(px, py, r + 4, r + 4);
                g.setColor(new Color(255, 218, 45));
                g.setStroke(new BasicStroke(1.5f));
                g.drawOval(px + 1, py + 1, r + 2, r + 2);
                g.setStroke(new BasicStroke(1.0f));
                // Hole in ring
                g.setColor(new Color(36, 92, 36));
                g.fillOval(px + 4, py + 4, r - 4, r - 4);
                // Stem and teeth
                g.setColor(new Color(205, 165, 0));
                g.fillRect(px + r,          py + r / 2,     r + 2, s / 4);
                g.fillRect(px + r + r / 2,  py + r / 2 - 3, s / 5, s / 5);
                g.fillRect(px + r + r * 3/4, py + r / 2 - 3, s / 5, s / 5);
            }
        }
    }

    // ── Players ──────────────────────────────────────────────────────────────

    private void drawPlayers(Graphics2D g, GameState gs) {
        List<Player> all = new ArrayList<>(gs.getPlayers().values());
        // Survivors first, zombies on top
        all.sort((a, b) -> Boolean.compare(a.isInfected(), b.isInfected()));
        for (Player p : all) drawOnePlayer(g, p, gs);
    }

    private void drawOnePlayer(Graphics2D g, Player p, GameState gs) {
        int cx = p.getX() + Player.SIZE / 2;
        int cy = p.getY() + Player.SIZE / 2;
        boolean zombie = p.isInfected();
        boolean isMe   = p.getId().equals(myPlayerId);

        // Facing angle (default: face up)
        double angle = facingAngles.getOrDefault(p.getId(), -Math.PI / 2.0);
        // Head offset (8.5 px toward facing direction)
        float hDx = (float)(8.5 * Math.cos(angle));
        float hDy = (float)(8.5 * Math.sin(angle));
        // Arm offset (perpendicular)
        double perp = angle + Math.PI / 2.0;
        float aDx = (float)(9.5 * Math.cos(perp));
        float aDy = (float)(9.5 * Math.sin(perp));

        // ── Safe-zone heal ring ──────────────────────────────────────────
        if (!zombie && gs.isSafeZoneAt(cx, cy)) {
            float sg = 0.35f + 0.45f * (float) Math.sin(animTick * 0.09);
            g.setColor(new Color(45, 220, 115, (int)(115 * sg)));
            g.setStroke(new BasicStroke(2.5f));
            g.drawOval(cx - 20, cy - 20, 40, 40);
            g.setStroke(new BasicStroke(1.0f));
        }

        // ── "You" glow ring ──────────────────────────────────────────────
        if (isMe) {
            float mg = 0.5f + 0.5f * (float) Math.sin(animTick * 0.11);
            g.setColor(new Color(255, 255, 255, (int)(45 + 85 * mg)));
            g.setStroke(new BasicStroke(2.0f));
            g.drawOval(cx - 19, cy - 19, 38, 38);
            g.setStroke(new BasicStroke(1.0f));
        }

        // ── Shadow ───────────────────────────────────────────────────────
        g.setColor(new Color(0, 0, 0, 88));
        g.fillOval(cx - 14, cy - 8, 28, 17);

        // ── Back-leg (opposite of facing) ────────────────────────────────
        Color legCol = zombie ? new Color(50, 64, 32) : new Color(36, 50, 78);
        g.setColor(legCol);
        g.fillOval((int)(cx - hDx) - 5, (int)(cy - hDy) - 4, 11, 10);

        // ── Body / torso ─────────────────────────────────────────────────
        Color bodyCol = zombie ? new Color(72, 92, 46) : new Color(46, 70, 115);
        Color bodyBdr = zombie ? new Color(48, 66, 28) : new Color(26, 48, 88);
        g.setColor(bodyCol);
        g.fillOval(cx - 11, cy - 11, 22, 22);
        g.setColor(bodyBdr);
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(cx - 11, cy - 11, 22, 22);
        g.setStroke(new BasicStroke(1.0f));
        // Equipment detail
        if (!zombie) {
            g.setColor(new Color(30, 46, 76));
            g.fillRect(cx - 9, cy - 2, 18, 3);
        }

        // ── Arms ─────────────────────────────────────────────────────────
        Color armCol = zombie ? new Color(84, 105, 52) : new Color(55, 82, 128);
        g.setColor(armCol);
        g.fillOval((int)(cx + aDx) - 5, (int)(cy + aDy) - 5, 10, 10);
        g.fillOval((int)(cx - aDx) - 5, (int)(cy - aDy) - 5, 10, 10);

        // ── Head ─────────────────────────────────────────────────────────
        int hx = (int)(cx + hDx), hy = (int)(cy + hDy);
        Color skinCol = zombie ? new Color(128, 156, 78) : new Color(210, 172, 128);
        Color skinBdr = zombie ? new Color(92, 118, 55) : new Color(172, 135, 95);
        g.setColor(skinCol);
        g.fillOval(hx - 9, hy - 9, 18, 18);
        g.setColor(skinBdr);
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(hx - 9, hy - 9, 18, 18);
        g.setStroke(new BasicStroke(1.0f));

        // ── Eyes ─────────────────────────────────────────────────────────
        float ep  = (float)(3.8 * Math.cos(perp)),  ep2 = (float)(3.8 * Math.sin(perp));
        float ef  = (float)(2.5 * Math.cos(angle)), ef2 = (float)(2.5 * Math.sin(angle));
        // Whites
        g.setColor(new Color(240, 242, 248));
        g.fillOval((int)(hx + ep + ef) - 3, (int)(hy + ep2 + ef2) - 3, 6, 6);
        g.fillOval((int)(hx - ep + ef) - 3, (int)(hy - ep2 + ef2) - 3, 6, 6);
        // Pupils
        Color pupilCol = zombie ? new Color(218, 28, 28) : new Color(18, 18, 62);
        g.setColor(pupilCol);
        g.fillOval((int)(hx + ep + ef) - 1, (int)(hy + ep2 + ef2) - 1, 3, 3);
        g.fillOval((int)(hx - ep + ef) - 1, (int)(hy - ep2 + ef2) - 1, 3, 3);

        // ── Helmet (survivor) / Zombie wound ─────────────────────────────
        if (!zombie) {
            g.setColor(new Color(48, 70, 44));
            g.fillArc(hx - 10, hy - 11, 20, 14, 0, 180);
            g.setColor(new Color(34, 52, 30));
            g.drawArc(hx - 10, hy - 11, 20, 14, 0, 180);
            // Helmet rim highlight
            g.setColor(new Color(72, 104, 66));
            g.drawLine(hx - 10, hy - 4, hx + 10, hy - 4);
        } else {
            // Wound / blood
            g.setColor(new Color(178, 18, 18, 195));
            g.fillOval(hx + (int)ef,     hy + (int)ef2,     6, 3);
            g.fillOval(hx + (int)ef - 2, hy + (int)ef2 + 4, 3, 2);
        }

        // ── Name tag ─────────────────────────────────────────────────────
        g.setFont(new Font("Segoe UI", Font.BOLD, 10));
        FontMetrics fm = g.getFontMetrics();
        String label = p.getName();
        int tw = fm.stringWidth(label);
        int tagX = cx - tw / 2, tagY = cy - 29;
        g.setColor(new Color(0, 0, 0, 178));
        g.fillRoundRect(tagX - 4, tagY - 12, tw + 8, 14, 4, 4);
        g.setColor(zombie ? new Color(100, 238, 100) : new Color(175, 215, 255));
        g.drawString(label, tagX, tagY);

        // ── Health bar ───────────────────────────────────────────────────
        int hbW = Player.SIZE + 10, hbX = cx - hbW / 2, hbY = cy - 38;
        // Background track
        g.setColor(new Color(15, 18, 28, 195));
        g.fillRoundRect(hbX, hbY, hbW, 5, 2, 2);
        // Colored fill
        int hp = p.getHealth();
        if (hp > 0) {
            Color hpC = hp > 60 ? new Color(38, 192, 62) : hp > 30 ? new Color(222, 152, 18) : new Color(212, 42, 42);
            g.setColor(hpC);
            g.fillRoundRect(hbX, hbY, Math.max(1, hbW * hp / 100), 5, 2, 2);
        }
    }

    // ── Minimap (bottom-right corner) ────────────────────────────────────────

    private void drawMinimap(Graphics2D g, GameState gs) {
        int mmW = 122, mmH = 88;
        int mmX = W - mmW - 8, mmY = H - mmH - 8;

        // Panel background
        g.setColor(new Color(5, 8, 5, 215));
        g.fillRoundRect(mmX - 3, mmY - 16, mmW + 6, mmH + 19, 7, 7);
        g.setColor(new Color(42, 58, 42, 200));
        g.setStroke(new BasicStroke(1.0f));
        g.drawRoundRect(mmX - 3, mmY - 16, mmW + 6, mmH + 19, 7, 7);

        // "MINIMAP" label
        g.setFont(new Font("Segoe UI", Font.BOLD, 9));
        g.setColor(new Color(85, 128, 85));
        g.drawString("MINIMAP", mmX, mmY - 4);

        // Tile grid
        float sx = (float) mmW / GameState.MAP_WIDTH;
        float sy = (float) mmH / GameState.MAP_HEIGHT;
        for (int row = 0; row < GameState.MAP_HEIGHT; row++) {
            for (int col = 0; col < GameState.MAP_WIDTH; col++) {
                int tile = GameState.MAP[row][col];
                int tx = mmX + (int)(col * sx), ty = mmY + (int)(row * sy);
                int tw = Math.max(1, (int) sx + 1), th = Math.max(1, (int) sy + 1);
                switch (tile) {
                    case 0 -> g.setColor(new Color(30, 82, 30));
                    case 1 -> g.setColor(new Color(70, 74, 90));
                    case 2 -> g.setColor(new Color(20, 105, 65));
                }
                g.fillRect(tx, ty, tw, th);
            }
        }

        // Pickups
        for (Pickup pk : new ArrayList<>(gs.getPickups())) {
            if (pk.isCollected()) continue;
            int px = mmX + (int)((float) pk.getX() / GameState.CANVAS_W * mmW);
            int py = mmY + (int)((float) pk.getY() / GameState.CANVAS_H * mmH);
            g.setColor(pk.getType() == Pickup.Type.MEDKIT ? new Color(215, 38, 38) : new Color(215, 178, 0));
            g.fillRect(px - 1, py - 1, 3, 3);
        }

        // Players
        for (Player p : gs.getPlayers().values()) {
            int px = mmX + (int)((float) p.getX() / GameState.CANVAS_W * mmW);
            int py = mmY + (int)((float) p.getY() / GameState.CANVAS_H * mmH);
            boolean me = p.getId().equals(myPlayerId);
            g.setColor(p.isInfected() ? new Color(55, 205, 68) : new Color(62, 132, 255));
            g.fillOval(px - 2, py - 2, 5, 5);
            if (me) {
                g.setColor(Color.WHITE);
                g.drawOval(px - 4, py - 4, 9, 9);
            }
        }
    }

    // ── Zombie red-vignette ───────────────────────────────────────────────────

    private void drawZombieVignette(Graphics2D g, GameState gs) {
        if (myPlayerId == null) return;
        Player me = gs.getPlayer(myPlayerId);
        if (me == null || !me.isInfected()) return;
        float str = 0.5f + 0.14f * (float) Math.sin(animTick * 0.068);
        Paint saved = g.getPaint();
        g.setPaint(new RadialGradientPaint(
            new Point2D.Float(W / 2f, H / 2f), W * 0.72f,
            new float[]{0.38f, 1.0f},
            new Color[]{new Color(0,0,0,0), new Color(152, 22, 22, (int)(215 * str))}
        ));
        g.fillRect(0, 0, W, H);
        g.setPaint(saved);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Screen: Game Over overlay
    // ═══════════════════════════════════════════════════════════════════════

    private void renderGameOver(Graphics2D g, GameState gs) {
        // Cinematic dim
        g.setColor(new Color(0, 0, 0, 190));
        g.fillRect(0, 0, W, H);

        boolean zombiesWin = gs.getWinnerMessage().contains("ZOMBIE");
        Color   winCol     = zombiesWin ? new Color(55, 215, 75) : new Color(70, 148, 255);

        // ── Winner banner ──
        g.setFont(new Font("Impact", Font.PLAIN, 56));
        g.setColor(new Color(0, 0, 0, 170));
        drawCentered(g, gs.getWinnerMessage(), W / 2 + 4, 116 + 4);
        g.setColor(winCol);
        drawCentered(g, gs.getWinnerMessage(), W / 2, 116);

        // ── Scoreboard panel ──
        List<Player> sorted = new ArrayList<>(gs.getPlayers().values());
        sorted.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));

        int panW = 510, panH = 48 + sorted.size() * 40 + 22;
        int panX = (W - panW) / 2, panY = 136;

        g.setColor(new Color(10, 13, 22, 240));
        g.fill(new RoundRectangle2D.Float(panX, panY, panW, panH, 12, 12));
        g.setColor(new Color(52, 60, 82));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new RoundRectangle2D.Float(panX, panY, panW, panH, 12, 12));
        g.setStroke(new BasicStroke(1.0f));

        // Header row
        int hY = panY + 30;
        g.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g.setColor(new Color(135, 145, 175));
        g.drawString("#",       panX + 22,  hY);
        g.drawString("PLAYER",  panX + 58,  hY);
        g.drawString("ROLE",    panX + 245, hY);
        g.drawString("SCORE",   panX + 405, hY);
        g.setColor(new Color(45, 55, 78));
        g.drawLine(panX + 14, panY + 36, panX + panW - 14, panY + 36);

        // Data rows
        int rowY = panY + 36;
        for (int i = 0; i < sorted.size(); i++) {
            Player p  = sorted.get(i);
            rowY     += 40;
            boolean me = p.getId().equals(myPlayerId);

            if (me) {
                g.setColor(new Color(32, 42, 72, 135));
                g.fillRoundRect(panX + 10, rowY - 24, panW - 20, 32, 6, 6);
            }

            // Medal colors for top 3
            Color rankCol = i == 0 ? new Color(222, 188, 28) :
                            i == 1 ? new Color(190, 192, 210) :
                            i == 2 ? new Color(198, 128, 55) : new Color(125, 130, 155);
            g.setFont(new Font("Segoe UI", Font.BOLD, 16));
            g.setColor(rankCol);
            g.drawString(String.valueOf(i + 1), panX + 22, rowY);

            g.setFont(new Font("Segoe UI", me ? Font.BOLD : Font.PLAIN, 15));
            g.setColor(me ? Color.WHITE : new Color(192, 198, 218));
            g.drawString(p.getName() + (me ? " \u2605" : ""), panX + 58, rowY);

            String roleStr = p.isInfected() ? "\u2623 Zombie" : "\uD83E\uDDCD Survivor";
            Color  roleCol = p.isInfected() ? new Color(70, 205, 78) : new Color(75, 142, 255);
            g.setFont(new Font("Segoe UI", Font.BOLD, 13));
            g.setColor(roleCol);
            g.drawString(roleStr, panX + 245, rowY);

            g.setFont(new Font("Segoe UI", Font.BOLD, 15));
            g.setColor(new Color(220, 185, 28));
            g.drawString(String.valueOf(p.getScore()), panX + 405, rowY);
        }

        // ── Countdown ──
        int cd = gs.getGameOverCountdown();
        g.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        g.setColor(new Color(95, 105, 130));
        drawCentered(g, "New game begins in " + cd + " second" + (cd == 1 ? "" : "s") + "...",
                     W / 2, panY + panH + 28);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Tile texture generators (called once in constructor)
    // ═══════════════════════════════════════════════════════════════════════

    private static BufferedImage makeTileGrass(int variant) {
        BufferedImage img = new BufferedImage(TS, TS, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        Color base = (variant == 0) ? new Color(18, 58, 18) : new Color(24, 68, 24);
        g.setColor(base);
        g.fillRect(0, 0, TS, TS);
        Random rng = new Random(variant == 0 ? 111222L : 333444L);
        // Grass blades (short vertical lines)
        for (int i = 0; i < 22; i++) {
            int gx = 1 + rng.nextInt(TS - 3), gy = 1 + rng.nextInt(TS - 5);
            int sh = rng.nextInt(3);
            Color gc = sh == 0 ? new Color(12, 42, 12) : sh == 1 ? base : new Color(30, 78, 30);
            g.setColor(new Color(gc.getRed(), gc.getGreen(), gc.getBlue(), 82));
            int bh = 3 + rng.nextInt(4);
            g.fillRect(gx,     gy,     1, bh);
            g.fillRect(gx + 1, gy + 1, 1, bh - 1);
        }
        // Dirt/pebble patches
        for (int i = 0; i < 6; i++) {
            int gx = rng.nextInt(TS - 3), gy = rng.nextInt(TS - 2);
            g.setColor(new Color(22, 40, 16, 52));
            g.fillOval(gx, gy, 4, 3);
        }
        g.dispose();
        return img;
    }

    private static BufferedImage makeTileWall() {
        BufferedImage img = new BufferedImage(TS, TS, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        // Stone base
        g.setColor(new Color(78, 82, 97));
        g.fillRect(0, 0, TS, TS);
        // Mortar joints
        g.setColor(new Color(54, 58, 70));
        g.fillRect(0,  10, TS, 2); // row 1 horizontal
        g.fillRect(0,  22, TS, 2); // row 2 horizontal
        g.fillRect(16,  0, 2, 10); // row 0 vertical
        g.fillRect( 8, 12, 2, 10); // row 1 left vertical
        g.fillRect(24, 12, 2, 10); // row 1 right vertical
        g.fillRect(16, 24, 2,  8); // row 2 vertical
        // Procedural stone texture
        Random rng = new Random(42L);
        for (int i = 0; i < 30; i++) {
            int nx = rng.nextInt(TS), ny = rng.nextInt(TS);
            int sh = rng.nextInt(32) - 16;
            g.setColor(new Color(clamp(78+sh), clamp(82+sh), clamp(97+sh), 85));
            g.fillRect(nx, ny, 2, 2);
        }
        // Bevel highlight (top-left)
        g.setColor(new Color(115, 120, 138));
        g.drawLine(0, 0, TS - 1, 0);
        g.drawLine(0, 0, 0, TS - 1);
        // Bevel shadow (bottom-right)
        g.setColor(new Color(46, 48, 60));
        g.drawLine(0, TS - 1, TS - 1, TS - 1);
        g.drawLine(TS - 1, 0, TS - 1, TS - 1);
        g.dispose();
        return img;
    }

    private static BufferedImage makeTileSafe() {
        BufferedImage img = new BufferedImage(TS, TS, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Base teal-green
        g.setColor(new Color(16, 90, 56));
        g.fillRect(0, 0, TS, TS);
        // Subtle grid lines
        g.setColor(new Color(12, 74, 46));
        for (int i = 8; i < TS; i += 8) {
            g.drawLine(i, 0, i, TS);
            g.drawLine(0, i, TS, i);
        }
        // Medical cross (muted)
        g.setColor(new Color(36, 148, 90, 155));
        g.fillRect(13, 6,  6, 20); // vertical
        g.fillRect(6, 13, 20,  6); // horizontal
        g.dispose();
        return img;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Utilities
    // ═══════════════════════════════════════════════════════════════════════

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private void drawCentered(Graphics2D g, String text, int cx, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, cx - fm.stringWidth(text) / 2, y);
    }
}
