package com.zombiesurvival.client.ui;

import com.zombiesurvival.shared.GameState;
import com.zombiesurvival.shared.Player;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Bottom HUD bar — 80 px tall, full window width.
 *
 * Layout:
 *   LEFT  (270 px)  Role badge + player name + health bar
 *   CENTER           Timer + phase label
 *   RIGHT  (220 px)  Score + connected player count
 */
public class HudPanel extends JPanel {

    private static final Color BG_TOP    = new Color(10, 12, 18);
    private static final Color BG_BOT    = new Color(6,   8, 13);
    private static final Color SEPARATOR = new Color(38, 44, 58);

    private GameState gameState;
    private String    myPlayerId;

    public HudPanel() {
        setPreferredSize(new Dimension(0, 80));
        setBackground(BG_TOP);
    }

    public void setData(GameState state, String playerId) {
        this.gameState   = state;
        this.myPlayerId  = playerId;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int W = getWidth(), H = getHeight();

        // ── Background gradient ──
        g2.setPaint(new GradientPaint(0, 0, BG_TOP, 0, H, BG_BOT));
        g2.fillRect(0, 0, W, H);
        // Top separator line
        g2.setColor(SEPARATOR);
        g2.fillRect(0, 0, W, 1);
        // Subtle inner glow on separator
        g2.setColor(new Color(255, 255, 255, 8));
        g2.fillRect(0, 1, W, 1);

        if (gameState == null) { g2.dispose(); return; }

        Player me = (myPlayerId != null) ? gameState.getPlayer(myPlayerId) : null;
        boolean zombie = (me != null) && me.isInfected();

        // ══════════════════════════════════════════════
        //  LEFT SECTION  (x: 14 → 280)
        // ══════════════════════════════════════════════
        drawLeftSection(g2, me, zombie, W, H);

        // ══════════════════════════════════════════════
        //  CENTER SECTION
        // ══════════════════════════════════════════════
        drawCenterSection(g2, W, H);

        // ══════════════════════════════════════════════
        //  RIGHT SECTION  (x: W-220 → W-14)
        // ══════════════════════════════════════════════
        drawRightSection(g2, me, W, H);

        g2.dispose();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void drawLeftSection(Graphics2D g, Player me, boolean zombie, int W, int H) {
        int lx = 16;

        // ── Role badge (pill-shaped) ──
        Color roleAccent = zombie ? new Color(55, 200, 65)  : new Color(65, 135, 255);
        Color roleBg     = zombie ? new Color(14, 44, 14)   : new Color(12, 28, 58);
        Color roleBdr    = zombie ? new Color(38, 120, 45)  : new Color(38, 88, 175);
        String roleText  = zombie ? "☣  ZOMBIE"            : "\uD83E\uDDCD  SURVIVOR";

        g.setFont(new Font("Segoe UI", Font.BOLD, 13));
        FontMetrics rfm = g.getFontMetrics();
        int rbW = rfm.stringWidth(roleText) + 20;

        g.setColor(roleBg);
        g.fill(new RoundRectangle2D.Float(lx, 11, rbW, 22, 11, 11));
        g.setColor(roleBdr);
        g.setStroke(new java.awt.BasicStroke(1.2f));
        g.draw(new RoundRectangle2D.Float(lx, 11, rbW, 22, 11, 11));
        g.setStroke(new java.awt.BasicStroke(1.0f));
        g.setColor(roleAccent);
        g.drawString(roleText, lx + 10, 27);

        // ── Player name ──
        if (me != null) {
            g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            g.setColor(new Color(145, 150, 170));
            g.drawString(me.getName(), lx, 48);
        }

        // ── Health bar ──
        int hbX = lx, hbY = 53, hbW = 165, hbH = 10;
        // "HP" label
        g.setFont(new Font("Segoe UI", Font.BOLD, 10));
        g.setColor(new Color(115, 120, 140));
        g.drawString("HP", hbX, hbY + hbH - 1);
        hbX += 22;

        // Background track
        g.setColor(new Color(20, 24, 36));
        g.fill(new RoundRectangle2D.Float(hbX, hbY, hbW, hbH, 5, 5));

        // Colored fill
        int hp = (me != null) ? me.getHealth() : 0;
        if (hp > 0) {
            Color hpFill = hp > 60 ? new Color(38, 190, 60) : hp > 30 ? new Color(220, 150, 18) : new Color(210, 40, 40);
            Color hpGlow = hp > 60 ? new Color(60, 220, 85) : hp > 30 ? new Color(240, 175, 40) : new Color(240, 68, 68);
            // Gradient fill
            g.setPaint(new GradientPaint(hbX, hbY, hpFill, hbX, hbY + hbH, hpFill.darker()));
            g.fill(new RoundRectangle2D.Float(hbX, hbY, Math.max(1, hbW * hp / 100), hbH, 5, 5));
            // Shine stripe
            g.setColor(new Color(255, 255, 255, 30));
            g.fill(new RoundRectangle2D.Float(hbX + 1, hbY + 1, Math.max(1, hbW * hp / 100 - 2), hbH / 2, 3, 3));
            // Glow edge
            g.setColor(new Color(hpGlow.getRed(), hpGlow.getGreen(), hpGlow.getBlue(), 80));
            g.setStroke(new java.awt.BasicStroke(1.0f));
            g.draw(new RoundRectangle2D.Float(hbX, hbY, Math.max(1, hbW * hp / 100), hbH, 5, 5));
            g.setStroke(new java.awt.BasicStroke(1.0f));
        }
        // HP number
        hbX += hbW + 5;
        g.setFont(new Font("Segoe UI", Font.BOLD, 11));
        Color hpTextCol = hp > 60 ? new Color(55, 210, 80) : hp > 30 ? new Color(230, 165, 30) : new Color(225, 55, 55);
        g.setColor(hpTextCol);
        g.drawString(hp + "%", hbX, hbY + hbH - 1);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void drawCenterSection(Graphics2D g, int W, int H) {
        int t    = gameState.getTimeRemaining();
        int mins = t / 60, secs = t % 60;
        String timerStr = String.format("⏱  %02d:%02d", mins, secs);
        boolean playing = gameState.getCurrentPhase() == GameState.Phase.PLAYING;
        boolean urgent  = playing && t < 30;

        // Timer text
        g.setFont(new Font("Consolas", Font.BOLD, 28));
        FontMetrics tfm   = g.getFontMetrics();
        int timerX        = (W - tfm.stringWidth(timerStr)) / 2;
        // Glow effect for urgent
        if (urgent) {
            g.setColor(new Color(220, 40, 40, 55));
            g.setFont(new Font("Consolas", Font.BOLD, 28));
            g.drawString(timerStr, timerX - 1, 43 - 1);
            g.drawString(timerStr, timerX + 1, 43 + 1);
        }
        g.setColor(urgent ? new Color(235, 55, 55) : new Color(225, 228, 240));
        g.drawString(timerStr, timerX, 43);

        // Phase label
        String phase = switch (gameState.getCurrentPhase()) {
            case WAITING   -> "WAITING FOR PLAYERS";
            case PLAYING   -> "SURVIVAL MODE";
            case GAME_OVER -> "GAME OVER";
        };
        g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        FontMetrics pfm = g.getFontMetrics();
        g.setColor(new Color(85, 92, 115));
        g.drawString(phase, (W - pfm.stringWidth(phase)) / 2, 62);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void drawRightSection(Graphics2D g, Player me, int W, int H) {
        int rx = W - 16; // right-align anchor

        // ── Score display ──
        String scoreLabel = "SCORE";
        String scoreVal   = (me != null) ? String.valueOf(me.getScore()) : "0";

        g.setFont(new Font("Segoe UI", Font.BOLD, 10));
        FontMetrics slm = g.getFontMetrics();
        g.setColor(new Color(120, 112, 55));
        g.drawString(scoreLabel, rx - slm.stringWidth(scoreLabel), 48);

        g.setFont(new Font("Segoe UI", Font.BOLD, 22));
        FontMetrics svm = g.getFontMetrics();
        g.setColor(new Color(218, 182, 28));
        g.drawString(scoreVal, rx - svm.stringWidth(scoreVal), 30);

        // ── Player count ──
        int pc = gameState.getPlayers().size();
        String pcStr = "\uD83D\uDC64 " + pc + " player" + (pc == 1 ? "" : "s");
        g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        FontMetrics pcm = g.getFontMetrics();
        g.setColor(new Color(95, 105, 130));
        g.drawString(pcStr, rx - pcm.stringWidth(pcStr), 66);
    }
}
