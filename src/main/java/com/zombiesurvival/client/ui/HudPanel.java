package com.zombiesurvival.client.ui;

import com.zombiesurvival.shared.*;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Collection;

/**
 * Bottom HUD — 108 px tall.
 *
 *  LEFT   Role badge | HP | Stamina | Equipment (helmet/vest/boots)
 *  CENTRE Timer | Phase
 *  RIGHT  Weapon bar (icon + ammo) | Score | Kills
 *  BOTTOM Zombie abilities cooldown (lunge / howl) when playing zombie
 */
public class HudPanel extends JPanel {

    private static final Color BG_TOP    = new Color(8,  10, 16);
    private static final Color BG_BOT    = new Color(4,   6, 10);
    private static final Color SEP       = new Color(36, 42, 56);

    private GameState gameState;
    private String    myPlayerId;

    public HudPanel() {
        setPreferredSize(new Dimension(0, 108));
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

        g2.setPaint(new GradientPaint(0, 0, BG_TOP, 0, H, BG_BOT));
        g2.fillRect(0, 0, W, H);
        g2.setColor(SEP);
        g2.fillRect(0, 0, W, 1);

        if (gameState == null) { g2.dispose(); return; }

        Player me     = (myPlayerId != null) ? gameState.getPlayer(myPlayerId) : null;
        boolean zombie = (me != null) && me.isInfected();

        drawLeft  (g2, me, zombie);
        drawCenter(g2, W);
        drawRight (g2, me, W);

        g2.dispose();
    }

    // ── LEFT: role + bars + equipment ────────────────────────────────────────

    private void drawLeft(Graphics2D g, Player me, boolean zombie) {
        int lx = 14;

        // Role badge
        Color acc = zombie ? new Color(52, 200, 62)  : new Color(62, 132, 255);
        Color bg  = zombie ? new Color(12, 42, 12)   : new Color(10, 26, 56);
        Color bdr = zombie ? new Color(36, 118, 43)  : new Color(36, 85, 172);
        String roleText = zombie ? "☣ ZOMBIE" : "🧍 SURVIVOR";

        g.setFont(new Font("Segoe UI", Font.BOLD, 12));
        FontMetrics rfm = g.getFontMetrics();
        int rbW = rfm.stringWidth(roleText) + 20;
        g.setColor(bg);   g.fill(new RoundRectangle2D.Float(lx, 8, rbW, 20, 10, 10));
        g.setColor(bdr);  g.setStroke(new BasicStroke(1.2f));
        g.draw(new RoundRectangle2D.Float(lx, 8, rbW, 20, 10, 10));
        g.setStroke(new BasicStroke(1f));
        g.setColor(acc);  g.drawString(roleText, lx+10, 23);

        if (me == null) return;

        // Player name
        g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g.setColor(new Color(130, 138, 162));
        g.drawString(me.getName(), lx, 42);

        // HP bar
        drawBar(g, lx+22, 46, 150, 9, me.getHealth(), 100,
                new Color(36,188,58), new Color(218,148,16), new Color(208,38,38));
        g.setFont(new Font("Segoe UI", Font.BOLD, 10));
        g.setColor(new Color(100, 108, 132)); g.drawString("HP", lx, 55);

        // Stamina bar
        drawBar(g, lx+22, 60, 150, 6, me.getStamina(), 100,
                new Color(48, 155, 220), new Color(220, 170, 48), new Color(200, 80, 30));
        g.setFont(new Font("Segoe UI", Font.BOLD, 9));
        g.setColor(new Color(88, 96, 118)); g.drawString("SP", lx, 67);

        // Armor bar (if any)
        if (me.getArmor() > 0) {
            drawBar(g, lx+22, 70, 150, 4, me.getArmor(), 60,
                    new Color(65, 135, 210), new Color(65, 135, 210), new Color(65, 135, 210));
            g.setFont(new Font("Segoe UI", Font.BOLD, 9));
            g.setColor(new Color(65, 135, 210)); g.drawString("AR", lx, 75);
        }

        // Equipment icons
        int ex = lx + 178;
        g.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
        if (me.hasHelmet()) { g.setColor(new Color(200, 200, 255)); g.drawString("⛑", ex,    24); }
        if (me.hasVest())   { g.setColor(new Color(100, 160, 255)); g.drawString("🦺", ex,   46); }
        if (me.hasBoots())  { g.setColor(new Color(160, 230, 160)); g.drawString("👟", ex,   68); }

        // Stun indicator
        if (me.isStunned()) {
            g.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g.setColor(new Color(255, 220, 0));
            g.drawString("★ STUNNED", lx, 90);
        }

        // Speed boost
        if (me.hasSpeedBoost()) {
            int secs = (me.getSpeedBoostTicks() + 29) / 30;
            g.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g.setColor(new Color(240, 205, 0));
            g.drawString("⚡ " + secs + "s", lx + (me.isStunned() ? 110 : 0), 90);
        }

        // Zombie abilities (Q / E cooldowns)
        if (zombie) {
            drawAbilityBar(g, lx, 94, "Q:Lunge", me.getLungeCooldown(), 450, new Color(220, 60, 60));
            drawAbilityBar(g, lx + 110, 94, "E:Howl",  me.getHowlCooldown(),  900, new Color(200, 100, 220));
        }
    }

    private void drawBar(Graphics2D g, int x, int y, int w, int h, int val, int max,
                         Color hi, Color mid, Color lo) {
        g.setColor(new Color(18, 22, 34));
        g.fill(new RoundRectangle2D.Float(x, y, w, h, 4, 4));
        if (val > 0) {
            int pct = val * 100 / max;
            Color fill = pct > 60 ? hi : pct > 30 ? mid : lo;
            g.setPaint(new GradientPaint(x, y, fill, x, y+h, fill.darker()));
            g.fill(new RoundRectangle2D.Float(x, y, Math.max(2, w * val / max), h, 4, 4));
            g.setColor(new Color(255, 255, 255, 22));
            g.fill(new RoundRectangle2D.Float(x+1, y+1, Math.max(2, w * val / max - 2), h/2, 2, 2));
        }
    }

    private void drawAbilityBar(Graphics2D g, int x, int y, String label, int cd, int maxCd, Color col) {
        g.setFont(new Font("Segoe UI", Font.BOLD, 9));
        g.setColor(cd == 0 ? col : new Color(100, 100, 100));
        g.drawString(label, x, y);
        int bx = x, by = y + 2, bw = 95, bh = 5;
        g.setColor(new Color(18, 22, 34));
        g.fillRoundRect(bx, by, bw, bh, 2, 2);
        if (cd < maxCd) {
            int filled = bw * (maxCd - cd) / maxCd;
            g.setColor(cd == 0 ? col : col.darker());
            g.fillRoundRect(bx, by, Math.max(2, filled), bh, 2, 2);
        }
    }

    // ── CENTRE: timer ─────────────────────────────────────────────────────────

    private void drawCenter(Graphics2D g, int W) {
        int t = gameState.getTimeRemaining();
        String ts = String.format("⏱  %02d:%02d", t / 60, t % 60);
        boolean playing = gameState.getCurrentPhase() == GameState.Phase.PLAYING;
        boolean urgent  = playing && t < 30;

        g.setFont(new Font("Consolas", Font.BOLD, 28));
        FontMetrics tfm = g.getFontMetrics();
        int tx = (W - tfm.stringWidth(ts)) / 2;
        if (urgent) {
            g.setColor(new Color(222, 38, 38, 50));
            g.drawString(ts, tx-1, 38-1); g.drawString(ts, tx+1, 38+1);
        }
        g.setColor(urgent ? new Color(232, 52, 52) : new Color(220, 225, 238));
        g.drawString(ts, tx, 38);

        String phase = switch (gameState.getCurrentPhase()) {
            case WAITING   -> "WAITING FOR PLAYERS";
            case PLAYING   -> "SURVIVAL MODE";
            case GAME_OVER -> "GAME OVER";
        };
        g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g.setColor(new Color(75, 84, 108));
        drawCentered(g, phase, W/2, 56);

        // Hint row
        g.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        g.setColor(new Color(50, 58, 78));
        drawCentered(g, "WASD=Move  SHIFT=Sprint  LMB=Shoot  F=Melee  Q=Lunge  E=Howl  1-5=Weapon", W/2, 74);

        // Survivor / zombie counts
        if (gameState.getCurrentPhase() == GameState.Phase.PLAYING) {
            long surv = gameState.getPlayers().values().stream().filter(p -> !p.isInfected()).count();
            long zomb = gameState.getPlayers().values().stream().filter(Player::isInfected).count();
            String counts = "🧍 " + surv + "  ☣ " + zomb;
            g.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g.setColor(new Color(100, 185, 100));
            drawCentered(g, counts, W/2, 92);
        }
    }

    // ── RIGHT: weapon bar + score + kills ─────────────────────────────────────

    private void drawRight(Graphics2D g, Player me, int W) {
        int rx = W - 14;

        // Score
        g.setFont(new Font("Segoe UI", Font.BOLD, 9));
        FontMetrics sm = g.getFontMetrics();
        g.setColor(new Color(115, 108, 50));
        g.drawString("SCORE", rx - sm.stringWidth("SCORE"), 18);

        String sv = (me != null) ? String.valueOf(me.getScore()) : "0";
        g.setFont(new Font("Segoe UI", Font.BOLD, 22));
        FontMetrics svm = g.getFontMetrics();
        g.setColor(new Color(215, 180, 25));
        g.drawString(sv, rx - svm.stringWidth(sv), 40);

        // Kills
        String kv = (me != null) ? "🔫 " + me.getKills() + " kills" : "";
        g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        FontMetrics kvm = g.getFontMetrics();
        g.setColor(new Color(185, 100, 100));
        g.drawString(kv, rx - kvm.stringWidth(kv), 56);

        // Weapon info
        if (me != null) {
            Weapon w     = me.getCurrentWeapon();
            int    ammo  = me.currentAmmo();
            String wName = w.displayName;
            String ammoStr = ammo < 0 ? "∞" : String.valueOf(ammo);
            String wLine  = wName + "  [" + ammoStr + "]";

            Color wCol = switch (w) {
                case PISTOL  -> new Color(180, 200, 255);
                case SHOTGUN -> new Color(255, 195, 100);
                case RIFLE   -> new Color(100, 230, 130);
                case BAT     -> new Color(220, 160, 80);
                default      -> new Color(160, 160, 160);
            };

            g.setFont(new Font("Segoe UI", Font.BOLD, 11));
            FontMetrics wfm = g.getFontMetrics();
            g.setColor(ammo == 0 && ammo >= 0 ? new Color(220, 50, 50) : wCol);
            g.drawString(wLine, rx - wfm.stringWidth(wLine), 74);

            // Low-ammo warning
            if (ammo == 0 && ammo >= 0) {
                g.setFont(new Font("Segoe UI", Font.BOLD, 9));
                g.setColor(new Color(220, 60, 60));
                String warn = "NO AMMO!";
                g.drawString(warn, rx - g.getFontMetrics().stringWidth(warn), 88);
            }
        }

        // Player count
        int pc = gameState.getPlayers().size();
        String pcStr = "👤 " + pc + " players";
        g.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        FontMetrics pcm = g.getFontMetrics();
        g.setColor(new Color(80, 90, 115));
        g.drawString(pcStr, rx - pcm.stringWidth(pcStr), 100);
    }

    private void drawCentered(Graphics2D g, String text, int cx, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, cx - fm.stringWidth(text) / 2, y);
    }
}
