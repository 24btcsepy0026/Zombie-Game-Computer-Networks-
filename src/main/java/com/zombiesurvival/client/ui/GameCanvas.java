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

    // ── Cached Minecraft-style static world map ────────────────────────────
    private BufferedImage cachedMapImage;
    private int           cachedW = -1;
    private int           cachedH = -1;
    private final BufferedImage darknessBuffer = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);

    // ── State ───────────────────────────────────────────────────────────────
    private volatile GameState  gameState;
    private volatile String     myPlayerId;
    private int                 animTick = 0;

    // Per-player facing angle tracked from position deltas
    private final Map<String, Double>  facingAngles  = new ConcurrentHashMap<>();
    private final Map<String, float[]> lastPositions = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════════
    //  Constructor
    // ═══════════════════════════════════════════════════════════════════════

    public GameCanvas() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(5, 10, 5));
        buildCachedMap();
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
            case WAITING, PLAYING -> renderGame(g2, gs);
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
    //  Screen: Game World (Cached Minecraft-Style Biome Arena)
    // ═══════════════════════════════════════════════════════════════════════

    private void buildCachedMap() {
        cachedW = W;
        cachedH = H;
        cachedMapImage = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D mg = cachedMapImage.createGraphics();
        mg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        mg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // 1. Render all floor, healing plus, and obstacle tiles across the 4 corner biomes & plaza
        for (int row = 0; row < GameState.MAP_HEIGHT; row++) {
            for (int col = 0; col < GameState.MAP_WIDTH; col++) {
                int tile = GameState.MAP[row][col];
                int px = col * TS;
                int py = row * TS;
                BiomeTheme.Biome b = BiomeTheme.getBiomeAt(col, row);

                if (tile == 1) {
                    boolean isOuterWall = (row == 0 || row == GameState.MAP_HEIGHT - 1 ||
                                           col == 0 || col == GameState.MAP_WIDTH - 1);
                    if (isOuterWall) {
                        mg.drawImage(TextureFactory.getWallTile(b), px, py, null);
                    } else {
                        mg.drawImage(TextureFactory.getObstacleTile(b, col, row), px, py, null);
                    }
                } else if (tile == 2) {
                    mg.drawImage(TextureFactory.getHealingTile(), px, py, null);
                } else {
                    mg.drawImage(TextureFactory.getFloorTile(b, col, row), px, py, null);
                }
            }
        }

        // 2. Render static decorations (Chest/Planks for crates, Leaves/Cactus for bushes)
        for (int[] pos : TREES) {
            BiomeTheme.Biome b = BiomeTheme.getBiomeAt(pos[0], pos[1]);
            mg.drawImage(TextureFactory.getBushTile(b), pos[0] * TS, pos[1] * TS, null);
        }
        for (int[] pos : CRATES) {
            mg.drawImage(TextureFactory.getChestTile(), pos[0] * TS, pos[1] * TS, null);
        }

        mg.dispose();
    }

    private void renderGame(Graphics2D g, GameState gs) {
        if (cachedMapImage == null || getWidth() != cachedW || getHeight() != cachedH) {
            buildCachedMap();
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        Graphics2D world = (Graphics2D) g.create();
        Player localPlayer = myPlayerId == null ? null : gs.getPlayer(myPlayerId);
        if (localPlayer != null && gs.getCurrentPhase() != GameState.Phase.GAME_OVER) {
            double zoom = 1.6;
            double viewWidth = W / zoom;
            double viewHeight = H / zoom;
            double cameraX = Math.max(0, Math.min(W - viewWidth,
                localPlayer.getX() + Player.SIZE / 2.0 - viewWidth / 2));
            double cameraY = Math.max(0, Math.min(H - viewHeight,
                localPlayer.getY() + Player.SIZE / 2.0 - viewHeight / 2));
            world.scale(zoom, zoom);
            world.translate(-cameraX, -cameraY);
        }

        world.drawImage(cachedMapImage, 0, 0, null);
        drawPickups(world, gs);
        boolean lobby = gs.getCurrentPhase() == GameState.Phase.WAITING;
        if (!lobby) drawPlayers(world, gs);
        drawSpotlight(world, gs, lobby);
        if (lobby) drawPlayers(world, gs);
        world.dispose();
        drawMinimap(g, gs);
        drawZombieVignette(g, gs);
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

        // ── Zombie aura (ensures zombies are always clearly visible) ─────
        if (zombie) {
            float zg = 0.5f + 0.5f * (float) Math.sin(animTick * 0.08 + p.getX() * 0.04);
            g.setColor(new Color(220, 45, 45, (int)(65 + 35 * zg)));
            g.setStroke(new BasicStroke(1.8f));
            g.drawOval(cx - 18, cy - 18, 36, 36);
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
        int mmW = 126, mmH = 90;
        int mmX = W - mmW - 10, mmY = H - mmH - 10;
        int pX = mmX - 4, pY = mmY - 18, pW = mmW + 8, pH = mmH + 22;

        // Scanner panel background
        g.setColor(new Color(6, 10, 14, 230));
        g.fillRoundRect(pX, pY, pW, pH, 8, 8);

        // Toxic radar border with glow
        g.setColor(new Color(25, 75, 30));
        g.drawRoundRect(pX, pY, pW, pH, 8, 8);
        g.setColor(new Color(55, 195, 70, 140));
        g.drawRoundRect(pX + 1, pY + 1, pW - 2, pH - 2, 7, 7);

        // Corner bracket accents (tactical scanner style)
        int cLen = 6;
        g.setColor(UITheme.COLOR_TOXIC_GREEN_BRT);
        // Top-Left
        g.drawLine(pX, pY + cLen, pX, pY);
        g.drawLine(pX, pY, pX + cLen, pY);
        // Top-Right
        g.drawLine(pX + pW - cLen, pY, pX + pW, pY);
        g.drawLine(pX + pW, pY, pX + pW, pY + cLen);
        // Bottom-Left
        g.drawLine(pX, pY + pH - cLen, pX, pY + pH);
        g.drawLine(pX, pY + pH, pX + cLen, pY + pH);
        // Bottom-Right
        g.drawLine(pX + pW - cLen, pY + pH, pX + pW, pY + pH);
        g.drawLine(pX + pW, pY + pH - cLen, pX + pW, pY + pH);

        // "MINIMAP" label using pre-allocated horror font
        g.setFont(UITheme.FONT_MINIMAP_LABEL);
        g.setColor(Color.BLACK);
        g.drawString("MINIMAP", mmX + 1, mmY - 4);
        g.setColor(UITheme.COLOR_TOXIC_GREEN_BRT);
        g.drawString("MINIMAP", mmX, mmY - 5);

        // Tile grid
        float sx = (float) mmW / GameState.MAP_WIDTH;
        float sy = (float) mmH / GameState.MAP_HEIGHT;
        for (int row = 0; row < GameState.MAP_HEIGHT; row++) {
            for (int col = 0; col < GameState.MAP_WIDTH; col++) {
                int tile = GameState.MAP[row][col];
                int tx = mmX + (int)(col * sx), ty = mmY + (int)(row * sy);
                int tw = Math.max(1, (int) sx + 1), th = Math.max(1, (int) sy + 1);
                switch (tile) {
                    case 0 -> {
                        BiomeTheme.Biome b = BiomeTheme.getBiomeAt(col, row);
                        Color mc = switch (b) {
                            case FOREST -> BiomeTheme.FOREST_FLOOR_BASE;
                            case DESERT -> BiomeTheme.DESERT_FLOOR_BASE;
                            case SNOW   -> BiomeTheme.SNOW_FLOOR_BASE;
                            case NETHER -> BiomeTheme.NETHER_FLOOR_BASE;
                            case PLAZA  -> BiomeTheme.PLAZA_FLOOR_BASE;
                        };
                        g.setColor(mc);
                    }
                    case 1 -> g.setColor(new Color(40, 42, 50));
                    case 2 -> g.setColor(BiomeTheme.HEALING_TILE_PLUS);
                }
                g.fillRect(tx, ty, tw, th);
            }
        }

        // Pickups
        for (Pickup pk : new ArrayList<>(gs.getPickups())) {
            if (pk.isCollected()) continue;
            int px = mmX + (int)((float) pk.getX() / GameState.CANVAS_W * mmW);
            int py = mmY + (int)((float) pk.getY() / GameState.CANVAS_H * mmH);
            g.setColor(pk.getType() == Pickup.Type.MEDKIT ? new Color(225, 45, 45) : UITheme.COLOR_GOLD_BRT);
            g.fillRect(px - 1, py - 1, 3, 3);
        }

        // Players
        for (Player p : gs.getPlayers().values()) {
            int px = mmX + (int)((float) p.getX() / GameState.CANVAS_W * mmW);
            int py = mmY + (int)((float) p.getY() / GameState.CANVAS_H * mmH);
            boolean me = p.getId().equals(myPlayerId);
            g.setColor(p.isInfected() ? UITheme.COLOR_TOXIC_GREEN_BRT : new Color(65, 145, 255));
            g.fillOval(px - 2, py - 2, 5, 5);
            if (me) {
                g.setColor(Color.WHITE);
                g.drawOval(px - 4, py - 4, 9, 9);
            }
        }
    }

    // ── Survivor Spotlight (Balanced lighting & Flashlight travels with survivors) ──

    private void drawSpotlight(Graphics2D g, GameState gs, boolean lobby) {
        if (gs.getCurrentPhase() == GameState.Phase.GAME_OVER) return;

        Graphics2D dg = darknessBuffer.createGraphics();
        dg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Reset the ambient layer before cutting out the spotlights.
        dg.setComposite(AlphaComposite.Src);
        dg.setColor(lobby ? new Color(3, 5, 12, 235) : new Color(10, 14, 22, 58));
        dg.fillRect(0, 0, W, H);

        // 2. Punch out transparent circular spotlights around the lit characters
        dg.setComposite(AlphaComposite.DstOut);
        float spotRadius = lobby ? 72.0f : 185.0f;

        for (Player p : gs.getPlayers().values()) {
            if (lobby || !p.isInfected()) {
                float cx = p.getX() + Player.SIZE / 2.0f;
                float cy = p.getY() + Player.SIZE / 2.0f;

                RadialGradientPaint rgp = new RadialGradientPaint(
                    cx, cy, spotRadius,
                    new float[]{ 0.0f, 0.45f, 0.8f, 1.0f },
                    new Color[]{
                        new Color(0, 0, 0, 255), // Fully clear in center
                        new Color(0, 0, 0, 190),
                        new Color(0, 0, 0, 65),
                        new Color(0, 0, 0, 0)
                    }
                );
                dg.setPaint(rgp);
                dg.fill(new Ellipse2D.Float(cx - spotRadius, cy - spotRadius, spotRadius * 2, spotRadius * 2));
            }
        }
        dg.dispose();

        // 3. Render balanced ambient layer with punched spotlights onto canvas
        g.drawImage(darknessBuffer, 0, 0, null);

        // 4. Subtle warm flashlight ambient aura over survivors
        for (Player p : gs.getPlayers().values()) {
            if (!p.isInfected()) {
                float cx = p.getX() + Player.SIZE / 2.0f;
                float cy = p.getY() + Player.SIZE / 2.0f;
                Paint saved = g.getPaint();
                RadialGradientPaint halo = new RadialGradientPaint(
                    cx, cy, spotRadius,
                    new float[]{ 0.0f, 0.35f, 0.7f, 1.0f },
                    new Color[]{
                        new Color(255, 235, 170, 25),
                        new Color(255, 215, 120, 12),
                        new Color(240, 190, 80, 4),
                        new Color(0, 0, 0, 0)
                    }
                );
                g.setPaint(halo);
                g.fill(new Ellipse2D.Float(cx - spotRadius, cy - spotRadius, spotRadius * 2, spotRadius * 2));
                g.setPaint(saved);
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
    //  Utilities
    // ═══════════════════════════════════════════════════════════════════════
    // ═══════════════════════════════════════════════════════════════════════

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private void drawCentered(Graphics2D g, String text, int cx, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, cx - fm.stringWidth(text) / 2, y);
    }
}
