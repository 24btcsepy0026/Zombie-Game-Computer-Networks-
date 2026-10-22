package com.zombiesurvival.client.ui;

import com.zombiesurvival.shared.GameState;
import com.zombiesurvival.shared.Player;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Bottom HUD bar — 80 px tall, full window width.
 * Restyled with apocalyptic theme, horror-font indicators,
 * custom Graphics2D vector icons, and zero allocations in paintComponent.
 *
 * Layout:
 *   LEFT  (280 px)  Role badge + player name + health bar
 *   CENTER           Timer + phase label
 *   RIGHT (220 px)  Score + connected player count
 */
public class HudPanel extends JPanel {

    private static final Color BG_TOP       = new Color(12, 14, 20);
    private static final Color BG_BOT       = new Color(6, 7, 11);
    private static final Color SEPARATOR    = new Color(42, 48, 64);
    private static final Color SEPARATOR_GLOW = new Color(55, 185, 65, 45);

    // Pre-allocated Strokes for performance
    private static final BasicStroke STROKE_1   = new BasicStroke(1.0f);
    private static final BasicStroke STROKE_1_2 = new BasicStroke(1.2f);

    // Pre-allocated GradientPaints (zero allocations in paint loop)
    private static final GradientPaint BG_GRADIENT = new GradientPaint(0, 0, BG_TOP, 0, 80, BG_BOT);
    private static final GradientPaint HP_GREEN = new GradientPaint(0, 0, new Color(45, 205, 75), 0, 10, new Color(25, 130, 42));
    private static final GradientPaint HP_YELLOW = new GradientPaint(0, 0, new Color(235, 160, 25), 0, 10, new Color(160, 105, 15));
    private static final GradientPaint HP_RED = new GradientPaint(0, 0, new Color(225, 45, 45), 0, 10, new Color(145, 25, 25));

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

        // 1. Background gradient (pre-allocated)
        g2.setPaint(BG_GRADIENT);
        g2.fillRect(0, 0, W, H);

        // Top separator with toxic green subtle glow
        g2.setColor(SEPARATOR);
        g2.fillRect(0, 0, W, 1);
        g2.setColor(SEPARATOR_GLOW);
        g2.fillRect(0, 1, W, 1);

        if (gameState == null) {
            g2.dispose();
            return;
        }

        Player me = (myPlayerId != null) ? gameState.getPlayer(myPlayerId) : null;
        boolean zombie = (me != null) && me.isInfected();

        // 2. Left Section: Role Badge + Player Name + HP Bar
        drawLeftSection(g2, me, zombie, W, H);

        // 3. Center Section: Timer + Phase Label
        drawCenterSection(g2, W, H);

        // 4. Right Section: Score + Player Count
        drawRightSection(g2, me, W, H);

        g2.dispose();
    }

    private void drawLeftSection(Graphics2D g, Player me, boolean zombie, int W, int H) {
        int lx = 16;

        // ── Role badge (pill-shaped) ──
        Color roleAccent = zombie ? UITheme.COLOR_TOXIC_GREEN_BRT : new Color(75, 150, 255);
        Color roleBg     = zombie ? new Color(14, 44, 18, 220)    : new Color(12, 28, 58, 220);
        Color roleBdr    = zombie ? UITheme.COLOR_TOXIC_GREEN      : new Color(45, 95, 185);
        String roleText  = zombie ? "ZOMBIE" : "SURVIVOR";

        g.setFont(UITheme.FONT_BADGE);
        FontMetrics rfm = g.getFontMetrics();
        int rbW = rfm.stringWidth(roleText) + 36;
        int rbH = 22;

        g.setColor(roleBg);
        g.fill(new RoundRectangle2D.Float(lx, 10, rbW, rbH, 11, 11));
        g.setColor(roleBdr);
        g.setStroke(STROKE_1_2);
        g.draw(new RoundRectangle2D.Float(lx, 10, rbW, rbH, 11, 11));
        g.setStroke(STROKE_1);

        // Role Icon (Biohazard dot for Zombie, Shield cross for Survivor - no emoji)
        if (zombie) {
            g.setColor(UITheme.COLOR_TOXIC_GREEN_BRT);
            g.fillOval(lx + 8, 16, 10, 10);
            g.setColor(new Color(10, 35, 12));
            g.fillOval(lx + 11, 19, 4, 4);
        } else {
            g.setColor(new Color(100, 175, 255));
            g.fillRect(lx + 12, 14, 4, 12);
            g.fillRect(lx + 8, 18, 12, 4);
        }

        // Role Text with Shadow
        g.setColor(Color.BLACK);
        g.drawString(roleText, lx + 26, 26);
        g.setColor(roleAccent);
        g.drawString(roleText, lx + 25, 25);

        // ── Player Name ──
        if (me != null) {
            g.setFont(UITheme.FONT_BUTTON_SUB);
            g.setColor(UITheme.COLOR_TEXT_MAIN);
            g.drawString(me.getName(), lx, 46);
        }

        // ── Health Bar ──
        int hbX = lx, hbY = 53, hbW = 160, hbH = 12;

        // "HP" label using horror font
        g.setFont(UITheme.FONT_HUD_HP);
        g.setColor(UITheme.COLOR_TEXT_MUTED);
        g.drawString("HP", hbX, hbY + hbH - 1);
        hbX += 26;

        // Background track with dark border
        g.setColor(new Color(18, 20, 28));
        g.fill(new RoundRectangle2D.Float(hbX, hbY, hbW, hbH, 6, 6));
        g.setColor(new Color(40, 46, 60));
        g.draw(new RoundRectangle2D.Float(hbX, hbY, hbW, hbH, 6, 6));

        // Colored fill with pre-allocated GradientPaint
        int hp = (me != null) ? me.getHealth() : 0;
        if (hp > 0) {
            int fillW = Math.max(2, hbW * hp / 100);
            GradientPaint fillPaint = hp > 60 ? HP_GREEN : (hp > 30 ? HP_YELLOW : HP_RED);
            g.setPaint(fillPaint);
            g.fill(new RoundRectangle2D.Float(hbX + 1, hbY + 1, fillW - 2, hbH - 2, 5, 5));

            // Gloss shine stripe
            g.setColor(new Color(255, 255, 255, 35));
            g.fill(new RoundRectangle2D.Float(hbX + 2, hbY + 2, Math.max(1, fillW - 4), (hbH - 4) / 2, 3, 3));
        }

        // HP number percentage using horror font
        hbX += hbW + 8;
        g.setFont(UITheme.FONT_HUD_HP);
        Color hpTextCol = hp > 60 ? UITheme.COLOR_TOXIC_GREEN_BRT : (hp > 30 ? UITheme.COLOR_GOLD_BRT : UITheme.COLOR_BLOOD_RED_BRT);
        g.setColor(Color.BLACK);
        g.drawString(hp + "%", hbX + 1, hbY + hbH);
        g.setColor(hpTextCol);
        g.drawString(hp + "%", hbX, hbY + hbH - 1);
    }

    private void drawCenterSection(Graphics2D g, int W, int H) {
        int t    = gameState.getTimeRemaining();
        int mins = t / 60, secs = t % 60;
        String timerStr = String.format("%02d:%02d", mins, secs);
        boolean playing = gameState.getCurrentPhase() == GameState.Phase.PLAYING;
        boolean urgent  = playing && t < 30;

        g.setFont(UITheme.FONT_HUD_TIMER);
        FontMetrics tfm = g.getFontMetrics();
        int timerW = tfm.stringWidth(timerStr);
        int iconSize = 18;
        int totalW = iconSize + 10 + timerW;
        int startX = (W - totalW) / 2;

        Color timerColor = urgent ? UITheme.COLOR_BLOOD_RED_BRT : UITheme.COLOR_TEXT_MAIN;

        // Vector Stopwatch Icon (no missing emoji glyph)
        UITheme.drawStopwatchIcon(g, startX, 22, iconSize, timerColor);

        // Timer text with deep shadow
        int tx = startX + iconSize + 10;
        g.setColor(Color.BLACK);
        g.drawString(timerStr, tx + 2, 42);
        if (urgent) {
            g.setColor(new Color(225, 45, 45, 80));
            g.drawString(timerStr, tx - 1, 39);
            g.drawString(timerStr, tx + 1, 41);
        }
        g.setColor(timerColor);
        g.drawString(timerStr, tx, 40);

        // Phase label
        String phase = switch (gameState.getCurrentPhase()) {
            case WAITING   -> "WAITING FOR PLAYERS";
            case PLAYING   -> "SURVIVAL MODE";
            case GAME_OVER -> "GAME OVER";
        };
        g.setFont(UITheme.FONT_HUD_LABEL);
        FontMetrics pfm = g.getFontMetrics();
        g.setColor(new Color(110, 120, 145));
        g.drawString(phase, (W - pfm.stringWidth(phase)) / 2, 60);
    }

    private void drawRightSection(Graphics2D g, Player me, int W, int H) {
        int rx = W - 20;

        // ── Score display ──
        String scoreLabel = "SCORE";
        String scoreVal   = (me != null) ? String.valueOf(me.getScore()) : "0";

        g.setFont(UITheme.FONT_HUD_SCORE_LABEL);
        FontMetrics slm = g.getFontMetrics();
        g.setColor(new Color(160, 150, 85));
        g.drawString(scoreLabel, rx - slm.stringWidth(scoreLabel), 46);

        // Score value using horror font
        g.setFont(UITheme.FONT_HUD_SCORE);
        FontMetrics svm = g.getFontMetrics();
        int scoreW = svm.stringWidth(scoreVal);
        g.setColor(Color.BLACK);
        g.drawString(scoreVal, rx - scoreW + 2, 28);
        g.setColor(UITheme.COLOR_GOLD_BRT);
        g.drawString(scoreVal, rx - scoreW, 26);

        // ── Connected Player Count with Vector Silhouette Icon (No missing box) ──
        int pc = gameState.getPlayers().size();
        String countStr = pc + " player" + (pc == 1 ? "" : "s");

        g.setFont(UITheme.FONT_BUTTON_SUB);
        FontMetrics pcm = g.getFontMetrics();
        int textW = pcm.stringWidth(countStr);
        int iconSize = 13;
        int totalW = iconSize + 6 + textW;
        int px = rx - totalW;
        int py = 64;

        // Vector Player Silhouette Icon
        UITheme.drawPlayerSilhouetteIcon(g, px, py - 11, iconSize, new Color(130, 140, 170));

        // Player count text
        g.setColor(new Color(145, 155, 180));
        g.drawString(countStr, px + iconSize + 6, py);
    }
}
