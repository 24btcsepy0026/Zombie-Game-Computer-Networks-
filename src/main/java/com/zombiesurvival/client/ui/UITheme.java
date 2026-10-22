package com.zombiesurvival.client.ui;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.Random;

/**
 * Central UI Theme for Zombie Escape.
 * Manages fonts, color palettes, vector icons, dark scrollbars,
 * and procedural texture generators (film grain, vignette, fog).
 */
public final class UITheme {

    // ─────────────────────────────────────────────────────────────────────────
    // FONT CONFIGURATION
    // To change or swap the font across the entire game, change FONT_RESOURCE_NAME
    // or FONT_FILE_NAME below, or set USE_CUSTOM_FONT = false to use system fallback.
    // ─────────────────────────────────────────────────────────────────────────
    public static final String FONT_RESOURCE_NAME = "/fonts/Creepster-Regular.ttf";
    public static final String FONT_FILE_NAME     = "resources/fonts/Creepster-Regular.ttf";
    public static final String FALLBACK_FAMILY    = "Impact";
    public static final boolean USE_CUSTOM_FONT   = true;

    // Loaded Base Font Family
    private static Font baseHorrorFont;
    public static final String ACTIVE_FONT_FAMILY;

    // Pre-allocated Font Instances (Performance: No allocations during paint)
    public static final Font FONT_TITLE_LARGE;
    public static final Font FONT_TITLE_MEDIUM;
    public static final Font FONT_BUTTON;
    public static final Font FONT_BUTTON_SUB;
    public static final Font FONT_BADGE;
    public static final Font FONT_HUD_TIMER;
    public static final Font FONT_HUD_SCORE;
    public static final Font FONT_HUD_SCORE_LABEL;
    public static final Font FONT_HUD_HP;
    public static final Font FONT_HUD_LABEL;
    public static final Font FONT_MINIMAP_LABEL;
    public static final Font FONT_CHAT_HEADER;
    public static final Font FONT_CHAT_MSG;
    public static final Font FONT_FOOTER;

    // ─────────────────────────────────────────────────────────────────────────
    // PIXEL FONT CONFIGURATION (Minecraft-Style In-Game HUD, Chat, Minimap)
    // To swap the pixel font, change PIXEL_FONT_FILE_NAME below or drop another
    // TTF into resources/fonts/PressStart2P-Regular.ttf.
    // ─────────────────────────────────────────────────────────────────────────
    public static final String PIXEL_FONT_RESOURCE_NAME = "/fonts/PressStart2P-Regular.ttf";
    public static final String PIXEL_FONT_FILE_NAME     = "resources/fonts/PressStart2P-Regular.ttf";
    public static final boolean USE_CUSTOM_PIXEL_FONT   = true;

    private static Font basePixelFont;
    public static final Font FONT_PIXEL_TITLE;
    public static final Font FONT_PIXEL_HUD;
    public static final Font FONT_PIXEL_SMALL;

    // ─────────────────────────────────────────────────────────────────────────
    // COLOR PALETTE
    // ─────────────────────────────────────────────────────────────────────────
    public static final Color COLOR_BG_DARK           = new Color(8, 9, 14);
    public static final Color COLOR_BG_PANEL          = new Color(16, 18, 26, 235);
    public static final Color COLOR_PANEL_BORDER      = new Color(50, 56, 72);

    public static final Color COLOR_BLOOD_RED         = new Color(185, 25, 25);
    public static final Color COLOR_BLOOD_RED_BRT     = new Color(235, 45, 45);
    public static final Color COLOR_BLOOD_RED_DARK    = new Color(90, 15, 15);

    public static final Color COLOR_TOXIC_GREEN       = new Color(55, 185, 65);
    public static final Color COLOR_TOXIC_GREEN_BRT   = new Color(80, 235, 95);
    public static final Color COLOR_TOXIC_GREEN_DARK  = new Color(20, 65, 25);
    public static final Color COLOR_TOXIC_GLOW        = new Color(60, 225, 75, 140);

    public static final Color COLOR_GOLD              = new Color(245, 195, 45);
    public static final Color COLOR_GOLD_BRT          = new Color(255, 220, 90);

    public static final Color COLOR_TEXT_MAIN         = new Color(230, 235, 245);
    public static final Color COLOR_TEXT_MUTED        = new Color(150, 155, 175);
    public static final Color COLOR_TEXT_SHADOW       = new Color(0, 0, 0, 200);

    public static final Color COLOR_BTN_BORDER_NORMAL = new Color(60, 66, 84);
    public static final Color COLOR_BTN_BORDER_HOVER  = new Color(95, 180, 115);
    public static final Color COLOR_BTN_BORDER_SEL    = new Color(75, 230, 95);

    public static final TexturePaint NOISE_TEXTURE;

    static {
        Font loaded = null;
        if (USE_CUSTOM_FONT) {
            // 1. Try ClassLoader resource stream
            try (InputStream is = UITheme.class.getResourceAsStream(FONT_RESOURCE_NAME)) {
                if (is != null) {
                    loaded = Font.createFont(Font.TRUETYPE_FONT, is);
                }
            } catch (Exception ignored) {}

            // 2. Try file path in workspace
            if (loaded == null) {
                File file = new File(FONT_FILE_NAME);
                if (file.exists()) {
                    try {
                        loaded = Font.createFont(Font.TRUETYPE_FONT, file);
                    } catch (Exception ignored) {}
                }
            }

            // 3. Try src/main/resources alternative path
            if (loaded == null) {
                File file = new File("src/main/resources/fonts/Creepster-Regular.ttf");
                if (file.exists()) {
                    try {
                        loaded = Font.createFont(Font.TRUETYPE_FONT, file);
                    } catch (Exception ignored) {}
                }
            }
        }

        // Register or fallback
        if (loaded != null) {
            baseHorrorFont = loaded;
            try {
                GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                ge.registerFont(loaded);
            } catch (Exception ignored) {}
            ACTIVE_FONT_FAMILY = loaded.getFamily();
        } else {
            baseHorrorFont = new Font(FALLBACK_FAMILY, Font.BOLD, 12);
            ACTIVE_FONT_FAMILY = FALLBACK_FAMILY;
        }

        // ── Load Pixel Font (Minecraft-style Arena HUD, Chat, Minimap) ──
        Font loadedPixel = null;
        if (USE_CUSTOM_PIXEL_FONT) {
            try (InputStream is = UITheme.class.getResourceAsStream(PIXEL_FONT_RESOURCE_NAME)) {
                if (is != null) {
                    loadedPixel = Font.createFont(Font.TRUETYPE_FONT, is);
                }
            } catch (Exception ignored) {}

            if (loadedPixel == null) {
                File file = new File(PIXEL_FONT_FILE_NAME);
                if (file.exists()) {
                    try {
                        loadedPixel = Font.createFont(Font.TRUETYPE_FONT, file);
                    } catch (Exception ignored) {}
                }
            }
            if (loadedPixel == null) {
                File file = new File("src/main/resources/fonts/PressStart2P-Regular.ttf");
                if (file.exists()) {
                    try {
                        loadedPixel = Font.createFont(Font.TRUETYPE_FONT, file);
                    } catch (Exception ignored) {}
                }
            }
        }

        if (loadedPixel != null) {
            basePixelFont = loadedPixel;
            try {
                GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                ge.registerFont(loadedPixel);
            } catch (Exception ignored) {}
        } else {
            basePixelFont = new Font(Font.MONOSPACED, Font.BOLD, 12);
        }

        FONT_PIXEL_TITLE = basePixelFont.deriveFont(Font.BOLD, 14f);
        FONT_PIXEL_HUD   = basePixelFont.deriveFont(Font.BOLD, 11f);
        FONT_PIXEL_SMALL = basePixelFont.deriveFont(Font.PLAIN, 8f);

        // Initialize reusable Font instances
        FONT_TITLE_LARGE     = baseHorrorFont.deriveFont(Font.BOLD, 68f);
        FONT_TITLE_MEDIUM    = baseHorrorFont.deriveFont(Font.BOLD, 26f);
        FONT_BUTTON          = baseHorrorFont.deriveFont(Font.BOLD, 24f);
        FONT_BUTTON_SUB      = new Font("Segoe UI", Font.PLAIN, 12);
        FONT_BADGE           = baseHorrorFont.deriveFont(Font.BOLD, 15f);
        FONT_HUD_TIMER       = baseHorrorFont.deriveFont(Font.BOLD, 30f);
        FONT_HUD_SCORE       = baseHorrorFont.deriveFont(Font.BOLD, 26f);
        FONT_HUD_SCORE_LABEL = new Font("Segoe UI", Font.BOLD, 10);
        FONT_HUD_HP          = baseHorrorFont.deriveFont(Font.BOLD, 16f);
        FONT_HUD_LABEL       = new Font("Segoe UI", Font.BOLD, 10);
        FONT_MINIMAP_LABEL   = basePixelFont.deriveFont(Font.BOLD, 9f);
        FONT_CHAT_HEADER     = baseHorrorFont.deriveFont(Font.BOLD, 16f);
        FONT_CHAT_MSG        = new Font("Segoe UI", Font.PLAIN, 12);
        FONT_FOOTER          = new Font("Segoe UI", Font.PLAIN, 12);

        // Precompute a 64x64 noise texture pattern for button overlay
        NOISE_TEXTURE = createNoiseTexturePattern(64, 64);
    }

    private UITheme() {}

    // ─────────────────────────────────────────────────────────────────────────
    // CUSTOM VECTOR GRAPHICS2D ICONS (No missing glyph boxes)
    // ─────────────────────────────────────────────────────────────────────────

    /** Draws a sharp play triangle */
    public static void drawPlayIcon(Graphics2D g2, int x, int y, int size, Color color) {
        g2.setColor(color);
        int[] xp = { x, x + size, x };
        int[] yp = { y, y + size / 2, y + size };
        g2.fillPolygon(xp, yp, 3);
    }

    /** Draws a mechanical gear / cog icon */
    public static void drawGearIcon(Graphics2D g2, int x, int y, int size, Color color) {
        g2.setColor(color);
        int cx = x + size / 2;
        int cy = y + size / 2;
        int outerR = size / 2;
        int innerR = size / 4;
        int teeth = 6;
        Path2D p = new Path2D.Float();
        for (int i = 0; i < teeth * 2; i++) {
            double angle = i * Math.PI / teeth;
            double r = (i % 2 == 0) ? outerR : outerR * 0.72;
            double px = cx + Math.cos(angle) * r;
            double py = cy + Math.sin(angle) * r;
            if (i == 0) p.moveTo(px, py);
            else p.lineTo(px, py);
        }
        p.closePath();
        g2.fill(p);

        // Center hole
        g2.setColor(new Color(20, 22, 32));
        g2.fillOval(cx - innerR, cy - innerR, innerR * 2, innerR * 2);
    }

    /** Draws a clean left arrow icon */
    public static void drawBackArrowIcon(Graphics2D g2, int x, int y, int size, Color color) {
        g2.setColor(color);
        int[] xp = { x + size, x, x + size };
        int[] yp = { y, y + size / 2, y + size };
        g2.fillPolygon(xp, yp, 3);
        g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(x + 2, y + size / 2, x + size + 4, y + size / 2);
    }

    /** Draws a chat speech bubble icon */
    public static void drawChatBubbleIcon(Graphics2D g2, int x, int y, int w, int h, Color color) {
        g2.setColor(color);
        g2.fillRoundRect(x, y, w, h - 3, 6, 6);
        int[] xp = { x + 3, x + 8, x + 3 };
        int[] yp = { y + h - 3, y + h - 3, y + h };
        g2.fillPolygon(xp, yp, 3);
        // Inner line dots
        g2.setColor(new Color(20, 24, 30));
        g2.fillRect(x + 3, y + 4, w - 6, 2);
    }

    /** Draws a paper airplane / send arrow icon */
    public static void drawSendIcon(Graphics2D g2, int x, int y, int size, Color color) {
        g2.setColor(color);
        Path2D p = new Path2D.Float();
        p.moveTo(x, y);
        p.lineTo(x + size, y + size / 2f);
        p.lineTo(x, y + size);
        p.lineTo(x + size * 0.35f, y + size / 2f);
        p.closePath();
        g2.fill(p);
    }

    /** Draws a player silhouette icon (head + shoulders) */
    public static void drawPlayerSilhouetteIcon(Graphics2D g2, int x, int y, int size, Color color) {
        g2.setColor(color);
        int headSize = (int)(size * 0.45);
        int headX = x + (size - headSize) / 2;
        int headY = y;
        g2.fillOval(headX, headY, headSize, headSize);

        int bodyW = size;
        int bodyH = (int)(size * 0.55);
        int bodyX = x;
        int bodyY = y + headSize + 1;
        g2.fillArc(bodyX, bodyY, bodyW, bodyH * 2, 0, 180);
    }

    /** Draws a stopwatch icon */
    public static void drawStopwatchIcon(Graphics2D g2, int x, int y, int size, Color color) {
        g2.setColor(color);
        g2.setStroke(new BasicStroke(1.8f));
        g2.drawOval(x + 1, y + 3, size - 2, size - 4);
        g2.drawLine(x + size / 2, y, x + size / 2, y + 3);
        g2.drawLine(x + size / 2, y + 3 + (size - 4) / 2, x + size / 2, y + 6);
        g2.drawLine(x + size / 2, y + 3 + (size - 4) / 2, x + size / 2 + 3, y + 3 + (size - 4) / 2);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PROCEDURAL TEXTURE & ATMOSPHERE CACHING
    // ─────────────────────────────────────────────────────────────────────────

    private static TexturePaint createNoiseTexturePattern(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(1337);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int alpha = r.nextInt(28);
                int shade = r.nextInt(255);
                img.setRGB(x, y, (alpha << 24) | (shade << 16) | (shade << 8) | shade);
            }
        }
        return new TexturePaint(img, new Rectangle(0, 0, w, h));
    }

    /**
     * Procedural film grain overlay (transparent with fine noise dots).
     * Cached in BufferedImage; only recreate on component resize.
     */
    public static BufferedImage createFilmGrainImage(int w, int h, float density) {
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        Random rng = new Random(4242);
        int[] pixels = new int[w * h];
        int alphaBase = (int) (density * 25);
        for (int i = 0; i < pixels.length; i++) {
            if (rng.nextFloat() < 0.35f) {
                int val = rng.nextInt(60);
                int a = rng.nextInt(Math.max(1, alphaBase));
                pixels[i] = (a << 24) | (val << 16) | (val << 8) | val;
            } else {
                pixels[i] = 0;
            }
        }
        img.setRGB(0, 0, w, h, pixels, 0, w);
        return img;
    }

    /**
     * Procedural vignette image (dark corners and edges).
     * Cached in BufferedImage; only recreate on component resize.
     */
    public static BufferedImage createVignetteImage(int w, int h) {
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        float radius = (float) Math.hypot(w / 2.0, h / 2.0);
        float[] dist = { 0.0f, 0.55f, 0.85f, 1.0f };
        Color[] colors = {
            new Color(0, 0, 0, 0),
            new Color(0, 0, 0, 40),
            new Color(0, 0, 0, 160),
            new Color(0, 0, 0, 240)
        };
        RadialGradientPaint p = new RadialGradientPaint(w / 2f, h / 2f, radius, dist, colors);
        g2.setPaint(p);
        g2.fillRect(0, 0, w, h);
        g2.dispose();
        return img;
    }

    /**
     * Procedural intense green toxic fog glow at the bottom.
     * Cached in BufferedImage; only recreate on component resize.
     */
    public static BufferedImage createFogGlowImage(int w, int h) {
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Bottom radial glow
        float cx = w / 2f;
        float cy = h;
        float rx = w * 0.75f;
        float[] dist = { 0.0f, 0.45f, 1.0f };
        Color[] colors = {
            new Color(45, 120, 35, 115),
            new Color(25, 80, 22, 55),
            new Color(10, 35, 12, 0)
        };
        RadialGradientPaint rgp = new RadialGradientPaint(cx, cy, rx, dist, colors);
        g2.setPaint(rgp);
        g2.fillRect(0, 0, w, h);

        // Secondary linear gradient rising up
        int fogH = (int)(h * 0.35);
        GradientPaint gp = new GradientPaint(
            0, h - fogH, new Color(20, 65, 20, 0),
            0, h, new Color(30, 95, 25, 65)
        );
        g2.setPaint(gp);
        g2.fillRect(0, h - fogH, w, fogH);

        g2.dispose();
        return img;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CUSTOM DARK SCROLLBAR UI
    // ─────────────────────────────────────────────────────────────────────────

    public static class DarkScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            this.thumbColor = new Color(45, 52, 68);
            this.thumbHighlightColor = new Color(65, 75, 98);
            this.thumbDarkShadowColor = new Color(25, 30, 40);
            this.trackColor = new Color(14, 15, 22);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return createZeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return createZeroButton();
        }

        private JButton createZeroButton() {
            JButton btn = new JButton();
            btn.setPreferredSize(new Dimension(0, 0));
            btn.setMinimumSize(new Dimension(0, 0));
            btn.setMaximumSize(new Dimension(0, 0));
            return btn;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            g.setColor(trackColor);
            g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isThumbRollover() ? thumbHighlightColor : thumbColor);
            g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y + 2, thumbBounds.width - 4, thumbBounds.height - 4, 6, 6);
            g2.setColor(new Color(60, 70, 90));
            g2.drawRoundRect(thumbBounds.x + 2, thumbBounds.y + 2, thumbBounds.width - 4, thumbBounds.height - 4, 6, 6);
            g2.dispose();
        }
    }
}
