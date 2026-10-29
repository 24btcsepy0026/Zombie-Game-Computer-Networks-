package com.zombiesurvival.client.ui;

import com.zombiesurvival.client.GameClient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Cinematic full-screen game launcher.
 * Replaces the plain JOptionPane with a stunning movie-quality connect screen.
 */
public class GameLauncher extends JFrame {

    // ── Spawn particles ───────────────────────────────────────────────────────
    private static class Particle {
        float x, y, vx, vy, life, maxLife, size;
        Color color;
        Particle(float x, float y, float vx, float vy, float life, float size, Color color) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.life = life; this.maxLife = life; this.size = size; this.color = color;
        }
        boolean tick() { x += vx; y += vy; vy += 0.04f; vx *= 0.97f; life--; return life <= 0; }
        float alpha() { return Math.max(0, life / maxLife); }
    }

    private final LauncherPanel panel;

    public GameLauncher() {
        setUndecorated(true);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        panel = new LauncherPanel(this);
        setContentPane(panel);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setVisible(true);
        panel.requestFocusInWindow();
    }

    // ── Inner panel ───────────────────────────────────────────────────────────

    private static class LauncherPanel extends JPanel {

        private static final Color C_BG1    = new Color(4, 4, 8);
        private static final Color C_BG2    = new Color(12, 4, 4);
        private static final Color C_RED    = new Color(220, 38, 38);
        private static final Color C_REDF   = new Color(255, 80, 80);
        private static final Color C_DARK   = new Color(10, 10, 18);
        private static final Color C_GREEN  = new Color(30, 220, 80);
        private static final Color C_FIELD  = new Color(20, 22, 30);
        private static final Color C_BORDER = new Color(60, 65, 85);
        private static final Color C_LABEL  = new Color(160, 170, 200);

        private final JFrame owner;
        private final javax.swing.Timer animTimer;
        private int tick = 0;

        private final List<Particle> particles = new ArrayList<>();
        private final Random rng = new Random();

        // Input fields
        private final JTextField ipField   = new JTextField("localhost");
        private final JTextField nameField = new JTextField(System.getProperty("user.name", "Player"));

        // State
        private boolean hoverPlay = false;
        private boolean hoverExit = false;

        LauncherPanel(JFrame owner) {
            this.owner = owner;
            setLayout(null);
            setBackground(C_BG1);
            setFocusable(true);

            styleField(ipField);
            styleField(nameField);
            add(ipField);
            add(nameField);

            // ESC to quit
            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) System.exit(0);
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) tryConnect();
                }
            });

            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    Rectangle play = playRect();
                    Rectangle exit = exitRect();
                    if (play != null && play.contains(e.getPoint())) tryConnect();
                    if (exit != null && exit.contains(e.getPoint())) System.exit(0);
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    Rectangle play = playRect();
                    Rectangle exit = exitRect();
                    boolean np = play != null && play.contains(e.getPoint());
                    boolean nx = exit != null && exit.contains(e.getPoint());
                    if (np != hoverPlay || nx != hoverExit) { hoverPlay = np; hoverExit = nx; repaint(); }
                }
            });

            animTimer = new javax.swing.Timer(16, e -> {
                tick++;
                // Spawn ambient particles
                if (tick % 3 == 0) {
                    int W = getWidth(), H = getHeight();
                    if (W > 0) {
                        float px = rng.nextFloat() * W;
                        float py = H + 10;
                        float vx = (rng.nextFloat() - 0.5f) * 0.8f;
                        float vy = -(0.5f + rng.nextFloat() * 1.5f);
                        int type  = rng.nextInt(3);
                        Color col = type == 0 ? new Color(200, 30, 30, 140)
                                   : type == 1 ? new Color(30, 180, 60, 100)
                                              : new Color(200, 100, 20, 80);
                        float sz  = 2 + rng.nextFloat() * 5;
                        particles.add(new Particle(px, py, vx, vy, 120 + rng.nextInt(100), sz, col));
                    }
                }
                particles.removeIf(Particle::tick);
                repaint();
            });
            animTimer.start();
        }

        private void styleField(JTextField f) {
            f.setBackground(C_FIELD);
            f.setForeground(Color.WHITE);
            f.setCaretColor(C_GREEN);
            f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(C_BORDER, 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
            f.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        }

        @Override
        public void doLayout() {
            int W = getWidth(), H = getHeight();
            if (W <= 0 || H <= 0) return;
            int cx = W / 2;
            int formTop = H / 2 + 10;
            int fw = 320, fh = 38;
            ipField  .setBounds(cx - fw/2, formTop + 34, fw, fh);
            nameField.setBounds(cx - fw/2, formTop + 34 + fh + 20 + 22, fw, fh);
        }

        private Rectangle playRect() {
            int W = getWidth(), H = getHeight();
            if (W <= 0) return null;
            int cx = W / 2;
            int btnY = H / 2 + 195;
            return new Rectangle(cx - 140, btnY, 120, 48);
        }

        private Rectangle exitRect() {
            int W = getWidth(), H = getHeight();
            if (W <= 0) return null;
            int cx = W / 2;
            int btnY = H / 2 + 195;
            return new Rectangle(cx + 20, btnY, 120, 48);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int W = getWidth(), H = getHeight();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

            // ── Background gradient ──────────────────────────────────────────
            g2.setPaint(new GradientPaint(0, 0, C_BG2, W, H, C_BG1));
            g2.fillRect(0, 0, W, H);

            // ── Grid overlay (cinematic) ──────────────────────────────────────
            g2.setColor(new Color(255, 255, 255, 5));
            int gs = 60;
            for (int x = 0; x < W; x += gs) g2.drawLine(x, 0, x, H);
            for (int y = 0; y < H; y += gs) g2.drawLine(0, y, W, y);

            // ── Ambient particles ─────────────────────────────────────────────
            for (Particle p : particles) {
                float a = p.alpha();
                Color c = p.color;
                g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int)(c.getAlpha()*a)));
                float s = p.size * a;
                g2.fillOval((int)(p.x - s/2), (int)(p.y - s/2), (int)s, (int)s);
            }

            // ── Blood splatter decorations ────────────────────────────────────
            drawBloodSplatters(g2, W, H);

            // ── Vignette ─────────────────────────────────────────────────────
            RadialGradientPaint vignette = new RadialGradientPaint(
                W / 2f, H / 2f, Math.max(W, H) * 0.7f,
                new float[]{0.4f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 200)});
            g2.setPaint(vignette);
            g2.fillRect(0, 0, W, H);

            int cx = W / 2;

            // ── Bio glyph ─────────────────────────────────────────────────────
            float glow = 0.5f + 0.5f * (float) Math.sin(tick * 0.04);
            int glyphSize = (int)(80 + 12 * glow);
            g2.setFont(new Font("Segoe UI Symbol", Font.PLAIN, glyphSize));
            g2.setColor(new Color(200, 30, 30, (int)(60 + 80 * glow)));
            drawShadow(g2, "☣", cx, H/2 - 190, new Color(0, 0, 0, 60), 4);
            drawCentered(g2, "☣", cx, H/2 - 190);

            // ── Title ─────────────────────────────────────────────────────────
            g2.setFont(new Font("Impact", Font.PLAIN, 84));
            drawShadow(g2, "ZOMBIE", cx, H/2 - 100, new Color(0, 0, 0, 150), 5);
            g2.setPaint(new GradientPaint(cx-220, H/2-160, C_RED, cx+220, H/2-90, C_REDF));
            drawCentered(g2, "ZOMBIE", cx, H/2 - 100);

            g2.setFont(new Font("Impact", Font.PLAIN, 40));
            g2.setColor(new Color(210, 210, 220));
            drawCentered(g2, "SURVIVAL", cx, H/2 - 58);

            // ── Subtitle ─────────────────────────────────────────────────────
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            g2.setColor(new Color(120, 130, 160));
            drawCentered(g2, "Multiplayer • Weapons • NPC Zombies • Big Map • Real-time Networking", cx, H/2 - 32);

            // ── Separator ─────────────────────────────────────────────────────
            g2.setPaint(new GradientPaint(cx-200,0,new Color(0,0,0,0),cx,0,new Color(180,40,40,180)));
            g2.fillRect(cx-200, H/2-16, 200, 1);
            g2.setPaint(new GradientPaint(cx,0,new Color(180,40,40,180),cx+200,0,new Color(0,0,0,0)));
            g2.fillRect(cx, H/2-16, 200, 1);

            // ── Form card ─────────────────────────────────────────────────────
            int cardW = 400, cardH = 240;
            int cardX = cx - cardW/2, cardY = H/2;
            g2.setColor(new Color(12, 12, 22, 220));
            g2.fillRoundRect(cardX, cardY, cardW, cardH, 18, 18);
            g2.setColor(new Color(50, 55, 80, 180));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(cardX, cardY, cardW, cardH, 18, 18);
            g2.setStroke(new BasicStroke(1f));

            // IP label
            g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
            g2.setColor(C_LABEL);
            g2.drawString("SERVER IP", cardX + 40, cardY + 28);
            // Name label
            g2.drawString("YOUR NAME", cardX + 40, cardY + 28 + 38 + 16 + 24);

            // ── Buttons ───────────────────────────────────────────────────────
            Rectangle play = playRect();
            Rectangle exit = exitRect();

            if (play != null) {
                drawButton(g2, play, "▶  CONNECT", hoverPlay, C_RED, new Color(180, 25, 25));
            }
            if (exit != null) {
                drawButton(g2, exit, "✕  EXIT", hoverExit, new Color(45, 48, 60), new Color(35, 38, 50));
            }

            // ── Bottom credits ─────────────────────────────────────────────────
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            g2.setColor(new Color(70, 75, 95));
            drawCentered(g2, "Press ENTER to connect  •  ESC to quit  •  Computer Networks Project", cx, H - 18);

            // ── Corner decoration ──────────────────────────────────────────────
            drawCornerDecor(g2, W, H);

            g2.dispose();
        }

        private void drawButton(Graphics2D g, Rectangle r, String label, boolean hover, Color base, Color baseDark) {
            Color bg   = hover ? base     : baseDark;
            Color bdr  = hover ? C_REDF   : new Color(base.getRed(), base.getGreen(), base.getBlue(), 160);
            Color text = hover ? Color.WHITE : new Color(200, 200, 220);

            if (hover) {
                g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 40));
                g.fillRoundRect(r.x - 4, r.y - 4, r.width + 8, r.height + 8, 14, 14);
            }
            g.setColor(bg);
            g.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
            g.setColor(bdr);
            g.setStroke(new BasicStroke(hover ? 1.8f : 1f));
            g.drawRoundRect(r.x, r.y, r.width, r.height, 10, 10);
            g.setStroke(new BasicStroke(1f));
            g.setFont(new Font("Segoe UI", Font.BOLD, 14));
            g.setColor(text);
            drawCentered(g, label, r.x + r.width/2, r.y + r.height/2 + 5);
        }

        private void drawBloodSplatters(Graphics2D g, int W, int H) {
            Random r2 = new Random(42); // fixed seed for stable decorations
            for (int i = 0; i < 12; i++) {
                float bx = r2.nextFloat() * W;
                float by = r2.nextFloat() * H;
                float bsz = 8 + r2.nextFloat() * 30;
                float f = 0.5f + 0.5f * (float) Math.sin(tick * 0.025 + i * 0.9);
                g.setColor(new Color(140, 15, 15, (int)(12 + 10 * f)));
                g.fillOval((int)(bx - bsz/2), (int)(by - bsz/2), (int)bsz, (int)bsz);
            }
        }

        private void drawCornerDecor(Graphics2D g, int W, int H) {
            g.setColor(new Color(180, 30, 30, 35));
            int s = 120;
            // Top-left
            g.setStroke(new BasicStroke(1f));
            g.drawLine(12, 12, 12+s, 12); g.drawLine(12, 12, 12, 12+s);
            // Bottom-right
            g.drawLine(W-12, H-12, W-12-s, H-12); g.drawLine(W-12, H-12, W-12, H-12-s);
            g.setStroke(new BasicStroke(1f));
        }

        private void drawShadow(Graphics2D g, String text, int cx, int y, Color shadow, int offset) {
            Color old = g.getColor();
            g.setColor(shadow);
            drawCentered(g, text, cx + offset, y + offset);
            g.setColor(old);
        }

        private void drawCentered(Graphics2D g, String text, int cx, int y) {
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, cx - fm.stringWidth(text) / 2, y);
        }

        private void tryConnect() {
            String ip   = ipField.getText().trim();
            String name = nameField.getText().trim();
            if (ip.isEmpty())   ip   = "localhost";
            if (name.isEmpty()) name = "Player";
            String finalIp   = ip;
            String finalName = name;
            animTimer.stop();
            owner.dispose();
            new GameClient().start(finalIp, finalName);
        }
    }
}
