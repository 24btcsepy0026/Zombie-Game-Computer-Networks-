package com.zombiesurvival.client.ui;

import com.zombiesurvival.shared.GameState;
import com.zombiesurvival.shared.Player;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Bottom HUD bar — 88 px tall, full window width.
 *
 *  LEFT (280 px)  Role badge | HP bar | Stamina bar | Armor dots
 *  CENTER          ⏱ Timer | Phase label
 *  RIGHT (235 px)  Score | Player count
 */
public class HudPanel extends JPanel {

    private static final Color BG_TOP    = new Color(10, 12, 18);
    private static final Color BG_BOT    = new Color(6,   8, 13);
    private static final Color SEPARATOR = new Color(38, 44, 58);

    private GameState gameState;
    private String    myPlayerId;

    public HudPanel() {
        setPreferredSize(new Dimension(0, 88));
        setBackground(BG_TOP);
    }

    public void setData(GameState state, String playerId) {
        this.gameState  = state;
        this.myPlayerId = playerId;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int W = getWidth(), H = getHeight();

        // Gradient background
        g2.setPaint(new GradientPaint(0, 0, BG_TOP, 0, H, BG_BOT));
        g2.fillRect(0, 0, W, H);
        g2.setColor(SEPARATOR);
        g2.fillRect(0, 0, W, 1);
        g2.setColor(new Color(255, 255, 255, 7));
        g2.fillRect(0, 1, W, 1);

        if (gameState == null) { g2.dispose(); return; }

        Player me     = (myPlayerId != null) ? gameState.getPlayer(myPlayerId) : null;
        boolean zombie = (me != null) && me.isInfected();

        drawLeft  (g2, me, zombie, H);
        drawCenter(g2, W, H);
        drawRight (g2, me, W, H);

        g2.dispose();
    }

    // ── LEFT: role + HP + Stamina + Armor ────────────────────────────────────

    private void drawLeft(Graphics2D g, Player me, boolean zombie, int H) {
        int lx = 16;

        // Role badge
        Color roleAccent = zombie ? new Color(52, 198, 62) : new Color(62, 132, 255);
        Color roleBg     = zombie ? new Color(12, 42, 12)  : new Color(10, 26, 56);
        Color roleBdr    = zombie ? new Color(36, 118, 43) : new Color(36, 85, 172);
        String roleText  = zombie ? "\u2623 ZOMBIE" : "\uD83E\uDDCD SURVIVOR";

        g.setFont(new Font("Segoe UI", Font.BOLD, 13));
        FontMetrics rfm = g.getFontMetrics();
        int rbW = rfm.stringWidth(roleText) + 22;

        g.setColor(roleBg);
        g.fill(new RoundRectangle2D.Float(lx, 10, rbW, 22, 11, 11));
        g.setColor(roleBdr);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new RoundRectangle2D.Float(lx, 10, rbW, 22, 11, 11));
        g.setStroke(new BasicStroke(1f));
        g.setColor(roleAccent);
        g.drawString(roleText, lx+11, 26);

        // HP
        if (me != null) {
            g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            g.setColor(new Color(138, 144, 165));
            g.drawString(me.getName(), lx, 46);
        }

        int hbX = lx+22, hbY = 50, hbW = 162, hbH = 9;
        g.setFont(new Font("Segoe UI", Font.BOLD, 10));
        g.setColor(new Color(110, 115, 138));
        g.drawString("HP", lx, hbY+hbH-1);

        g.setColor(new Color(18, 22, 34));
        g.fill(new RoundRectangle2D.Float(hbX, hbY, hbW, hbH, 4, 4));

        int hp = (me != null) ? me.getHealth() : 0;
        if (hp > 0) {
            Color fill = hp>60 ? new Color(36,188,58) : hp>30 ? new Color(218,148,16) : new Color(208,38,38);
            g.setPaint(new GradientPaint(hbX, hbY, fill, hbX, hbY+hbH, fill.darker()));
            g.fill(new RoundRectangle2D.Float(hbX, hbY, Math.max(1, hbW*hp/100), hbH, 4, 4));
            // Shine
            g.setColor(new Color(255,255,255,28));
            g.fill(new RoundRectangle2D.Float(hbX+1, hbY+1, Math.max(1, hbW*hp/100-2), hbH/2, 2, 2));
        }

        int hpNumX = hbX + hbW + 5;
        g.setFont(new Font("Segoe UI", Font.BOLD, 11));
        Color hpTxt = hp>60 ? new Color(52,208,78) : hp>30 ? new Color(228,162,28) : new Color(222,52,52);
        g.setColor(hpTxt);
        g.drawString(hp + "%", hpNumX, hbY+hbH-1);

        // Stamina
        if (me != null) {
            int sX = hbX, sY = hbY + 13, sW = hbW, sH = 6;
            g.setFont(new Font("Segoe UI", Font.BOLD, 9));
            g.setColor(new Color(92, 96, 118));
            g.drawString("SP", lx, sY+sH-1);
            g.setColor(new Color(15, 18, 28));
            g.fill(new RoundRectangle2D.Float(sX, sY, sW, sH, 3, 3));
            int st = me.getStamina();
            if (st > 0) {
                Color stFill = st > 50 ? new Color(48, 155, 220) : new Color(220, 170, 48);
                g.setColor(stFill);
                g.fill(new RoundRectangle2D.Float(sX, sY, Math.max(1, sW*st/100), sH, 3, 3));
                g.setColor(new Color(255, 255, 255, 22));
                g.fill(new RoundRectangle2D.Float(sX+1, sY+1, Math.max(1, sW*st/100-2), sH/2, 2, 2));
            }
        }

        // Armor indicator
        if (me != null && me.getArmor() > 0) {
            int aY = hbY + 30;
            g.setFont(new Font("Segoe UI", Font.BOLD, 9));
            g.setColor(new Color(65, 130, 210));
            g.drawString("\uD83D\uDEE1 " + me.getArmor(), lx, aY);
        }
    }

    // ── CENTER: timer ─────────────────────────────────────────────────────────

    private void drawCenter(Graphics2D g, int W, int H) {
        int t = gameState.getTimeRemaining();
        String timerStr = String.format("\u23F1  %02d:%02d", t/60, t%60);
        boolean playing = gameState.getCurrentPhase() == GameState.Phase.PLAYING;
        boolean urgent  = playing && t < 30;

        g.setFont(new Font("Consolas", Font.BOLD, 29));
        FontMetrics tfm = g.getFontMetrics();
        int timerX = (W - tfm.stringWidth(timerStr)) / 2;

        if (urgent) {
            g.setColor(new Color(222, 38, 38, 52));
            g.drawString(timerStr, timerX-1, 44-1);
            g.drawString(timerStr, timerX+1, 44+1);
        }
        g.setColor(urgent ? new Color(232, 52, 52) : new Color(222, 226, 238));
        g.drawString(timerStr, timerX, 44);

        String phase = switch (gameState.getCurrentPhase()) {
            case WAITING   -> "WAITING FOR PLAYERS";
            case PLAYING   -> "SURVIVAL MODE";
            case GAME_OVER -> "GAME OVER";
        };
        g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        FontMetrics pfm = g.getFontMetrics();
        g.setColor(new Color(80, 88, 112));
        g.drawString(phase, (W - pfm.stringWidth(phase))/2, 64);

        // Sprint hint
        g.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        g.setColor(new Color(55, 62, 82));
        String hint = "SHIFT = Sprint";
        FontMetrics hfm = g.getFontMetrics();
        g.drawString(hint, (W - hfm.stringWidth(hint))/2, 76);
    }

    // ── RIGHT: score + speed boost + player count ─────────────────────────────

    private void drawRight(Graphics2D g, Player me, int W, int H) {
        int rx = W - 16;

        // Score
        g.setFont(new Font("Segoe UI", Font.BOLD, 10));
        FontMetrics slm = g.getFontMetrics();
        g.setColor(new Color(118, 110, 52));
        g.drawString("SCORE", rx - slm.stringWidth("SCORE"), 48);

        String sv = (me != null) ? String.valueOf(me.getScore()) : "0";
        g.setFont(new Font("Segoe UI", Font.BOLD, 23));
        FontMetrics svm = g.getFontMetrics();
        g.setColor(new Color(215, 180, 25));
        g.drawString(sv, rx - svm.stringWidth(sv), 30);

        // Speed boost countdown
        if (me != null && me.hasSpeedBoost()) {
            int secs = (me.getSpeedBoostTicks() + 29) / 30;
            String boosted = "\u26A1 " + secs + "s";
            g.setFont(new Font("Segoe UI", Font.BOLD, 12));
            FontMetrics bm = g.getFontMetrics();
            g.setColor(new Color(240, 205, 0));
            g.drawString(boosted, rx - bm.stringWidth(boosted), 63);
        }

        // Player count
        int pc = gameState.getPlayers().size();
        String pcStr = "\uD83D\uDC64 " + pc + " connected";
        g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        FontMetrics pcm = g.getFontMetrics();
        g.setColor(new Color(88, 98, 125));
        g.drawString(pcStr, rx - pcm.stringWidth(pcStr), 78);
    }
}
