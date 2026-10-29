package com.zombiesurvival.client.ui;

import com.zombiesurvival.shared.*;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/**
 * Last-Day-on-Earth-style survival HUD.
 *
 * Layout (dark military/survivor aesthetic):
 *
 *  ┌────────────────────────────────────────────────────────────────────────┐
 *  │ [PORTRAIT]  HP ██████░░  SP ████░░░   ──  ⏱ TIMER  ──   WEAPON  SCORE│
 *  │  Role tag   Equipment icons            Phase         Ammo    Kills     │
 *  └────────────────────────────────────────────────────────────────────────┘
 *
 * Colors: dark brown (#1a1208), parchment text, blood-red HP, amber stamina
 */
public class HudPanel extends JPanel {

    // ── LDOE Color Palette ────────────────────────────────────────────────────
    private static final Color BG1        = new Color(18, 14,  8);   // very dark brown
    private static final Color BG2        = new Color(12,  9,  5);   // darker bottom
    private static final Color BORDER_TOP = new Color(55, 44, 25);   // amber border line
    private static final Color DIVIDER    = new Color(38, 30, 16);   // column dividers
    private static final Color TEXT_DIM   = new Color(120,108, 75);  // label text
    private static final Color TEXT_MAIN  = new Color(200,185,130);  // main text
    private static final Color TEXT_HI    = new Color(225,205,140);  // highlighted text
    private static final Color RED_HI     = new Color(200, 40, 40);  // blood red
    private static final Color AMB_HI     = new Color(200,160, 30);  // amber
    private static final Color GRN_HI     = new Color( 60,185, 80);  // survivor green
    private static final Color ZMB_HI     = new Color( 80,200, 60);  // zombie green

    private GameState gameState;
    private String    myPlayerId;
    private int       animTick = 0;

    public HudPanel() {
        setPreferredSize(new Dimension(0, 112));
        setBackground(BG1);
        // Pulse the timer
        new javax.swing.Timer(100, e -> { animTick++; repaint(); }).start();
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

        // Background
        g2.setPaint(new GradientPaint(0, 0, BG1, 0, H, BG2));
        g2.fillRect(0, 0, W, H);

        // Top border line (amber glow)
        g2.setPaint(new GradientPaint(0, 0, new Color(0,0,0,0), W/4, 0, BORDER_TOP));
        g2.fillRect(0, 0, W/2, 1);
        g2.setPaint(new GradientPaint(W/2, 0, BORDER_TOP, W, 0, new Color(0,0,0,0)));
        g2.fillRect(W/2, 0, W/2, 1);

        // Noise texture (LDOE worn-panel feel)
        for (int x = 0; x < W; x += 2) {
            int al = (int)(6 + 4 * Math.random());
            g2.setColor(new Color(255,255,255, al));
            g2.drawLine(x, 0, x, H);
        }

        if (gameState == null) { g2.dispose(); return; }
        Player me     = (myPlayerId != null) ? gameState.getPlayer(myPlayerId) : null;
        boolean zombie = (me != null) && me.isInfected();

        // Column layout
        drawPortrait(g2, me, zombie, H);
        drawBars    (g2, me, zombie);
        drawEquip   (g2, me);
        drawTimer   (g2, W, H);
        drawWeapon  (g2, me, W, H);
        drawStats   (g2, me, W, H);

        g2.dispose();
    }

    // ── Left: Portrait circle ─────────────────────────────────────────────────
    private void drawPortrait(Graphics2D g, Player me, boolean zombie, int H) {
        int px = 12, py = 8, pr = 44;
        // Outer ring (role color)
        Color ring = zombie ? ZMB_HI : GRN_HI;
        g.setColor(new Color(ring.getRed(), ring.getGreen(), ring.getBlue(), 80));
        g.setStroke(new BasicStroke(2.5f)); g.drawOval(px, py, pr, pr); g.setStroke(new BasicStroke(1f));
        // Circle fill
        g.setPaint(new RadialGradientPaint(px+pr/2, py+pr/2, pr/2,
            new float[]{0f, 1f},
            new Color[]{new Color(30,25,15), new Color(12,10,6)}));
        g.fillOval(px, py, pr, pr);
        // Icon
        g.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 22));
        g.setColor(ring);
        String ico = zombie ? "☣" : "⚔";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(ico, px + (pr - fm.stringWidth(ico)) / 2, py + 30);

        // Role label under portrait
        g.setFont(new Font("Segoe UI", Font.BOLD, 9));
        String role = zombie ? "ZOMBIE" : "SURVIVOR";
        g.setColor(ring);
        fm = g.getFontMetrics();
        g.drawString(role, px + (pr - fm.stringWidth(role)) / 2, py + pr + 12);

        // Name
        if (me != null) {
            g.setFont(new Font("Segoe UI", Font.PLAIN, 9));
            g.setColor(TEXT_DIM);
            String name = me.getName().length() > 10 ? me.getName().substring(0, 10) : me.getName();
            g.drawString(name, px + 1, py + pr + 22);
        }
    }

    // ── Status bars (HP, Stamina, Armor) ─────────────────────────────────────
    private void drawBars(Graphics2D g, Player me, boolean zombie) {
        int bx = 68, by = 10;

        // HP
        drawLabel(g, "HP", bx, by + 13);
        int hp = me != null ? me.getHealth() : 0;
        Color hpCol = hp > 60 ? new Color(50, 185, 60) : hp > 30 ? new Color(195, 150, 20) : RED_HI;
        drawBar(g, bx + 18, by, 140, 11, hp, 100, hpCol);
        g.setFont(new Font("Segoe UI", Font.BOLD, 9));
        g.setColor(TEXT_HI);
        g.drawString(hp + "%", bx + 162, by + 10);

        // Stamina
        by += 18;
        drawLabel(g, "SP", bx, by + 12);
        int sp = me != null ? me.getStamina() : 100;
        drawBar(g, bx + 18, by, 140, 9, sp, 100, AMB_HI);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        g.setColor(TEXT_DIM); g.drawString(sp + "", bx + 162, by + 9);

        // Armor
        if (me != null && me.getArmor() > 0) {
            by += 14;
            drawLabel(g, "AR", bx, by + 10);
            drawBar(g, bx + 18, by, 140, 7, me.getArmor(), 60, new Color(70, 130, 200));
            g.setFont(new Font("Segoe UI", Font.PLAIN, 8));
            g.setColor(new Color(70, 130, 200)); g.drawString(me.getArmor() + "", bx + 162, by + 8);
        }

        // Zombie abilities
        if (zombie && me != null) {
            int ay = 64;
            drawAbility(g, bx, ay,      "Q Lunge", me.getLungeCooldown(), 450, new Color(200,60,60));
            drawAbility(g, bx+110, ay,  "E Howl",  me.getHowlCooldown(),  900, new Color(160,80,200));
        }

        // Status icons
        int sx = bx;
        if (me != null && me.isStunned()) {
            g.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g.setColor(new Color(255, 220, 0));
            g.drawString("★STUN", sx, 100); sx += 50;
        }
        if (me != null && me.hasSpeedBoost()) {
            int secs = (me.getSpeedBoostTicks() + 29) / 30;
            g.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g.setColor(AMB_HI); g.drawString("⚡" + secs + "s", sx, 100);
        }
    }

    private void drawLabel(Graphics2D g, String label, int x, int y) {
        g.setFont(new Font("Segoe UI", Font.BOLD, 9));
        g.setColor(TEXT_DIM);
        g.drawString(label, x, y);
    }

    private void drawBar(Graphics2D g, int x, int y, int w, int h, int val, int max, Color col) {
        // Background track
        g.setColor(new Color(10, 8, 5));
        g.fill(new RoundRectangle2D.Float(x, y, w, h, 3, 3));
        // Subtle notches
        g.setColor(new Color(30, 25, 15));
        for (int i = 1; i < 10; i++) g.drawLine(x + w*i/10, y, x + w*i/10, y+h);
        // Fill
        if (val > 0) {
            int fw = Math.max(2, w * val / max);
            g.setPaint(new GradientPaint(x, y, col.brighter(), x, y+h, col.darker().darker()));
            g.fill(new RoundRectangle2D.Float(x, y, fw, h, 3, 3));
            // Shine
            g.setColor(new Color(255, 255, 255, 20));
            g.fill(new RoundRectangle2D.Float(x+1, y+1, Math.max(2, fw-2), h/2, 2, 2));
        }
        // Border
        g.setColor(new Color(50, 40, 22, 150));
        g.draw(new RoundRectangle2D.Float(x, y, w, h, 3, 3));
    }

    private void drawAbility(Graphics2D g, int x, int y, String label, int cd, int maxCd, Color col) {
        boolean ready = cd == 0;
        g.setFont(new Font("Segoe UI", Font.BOLD, 9));
        g.setColor(ready ? col : TEXT_DIM);
        g.drawString(label + (ready?" ✓":" "+((cd+29)/30)+"s"), x, y);
        int bw = 98, bh = 5;
        g.setColor(new Color(10,8,5)); g.fillRoundRect(x, y+2, bw, bh, 2, 2);
        if (cd < maxCd) {
            int fw = bw * (maxCd - cd) / maxCd;
            g.setColor(ready ? col : col.darker().darker());
            g.fillRoundRect(x, y+2, Math.max(2,fw), bh, 2, 2);
        }
    }

    // ── Equipment icons ────────────────────────────────────────────────────────
    private void drawEquip(Graphics2D g, Player me) {
        if (me == null) return;
        int ex = 248, ey = 12;
        drawEquipSlot(g, ex,    ey,    me.hasHelmet(), "⛑", "HLM", new Color(160,175,210));
        drawEquipSlot(g, ex+38, ey,    me.hasVest(),   "🦺","VST", new Color(80,140,200));
        drawEquipSlot(g, ex,    ey+40, me.hasBoots(),  "👟","BTS", new Color(120,200,120));
    }

    private void drawEquipSlot(Graphics2D g, int x, int y, boolean has, String icon, String label, Color col) {
        // Slot background
        Color bg = has ? new Color(col.getRed()/6, col.getGreen()/6, col.getBlue()/6) : new Color(15,12,8);
        g.setColor(bg); g.fillRoundRect(x, y, 32, 32, 5, 5);
        g.setColor(has ? col : new Color(40,32,18));
        g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(x, y, 32, 32, 5, 5);
        g.setStroke(new BasicStroke(1f));
        if (has) {
            g.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(col);
            g.drawString(icon, x + (32-fm.stringWidth(icon))/2, y+22);
        } else {
            g.setFont(new Font("Segoe UI", Font.PLAIN, 8));
            g.setColor(new Color(45,38,22));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(label, x+(32-fm.stringWidth(label))/2, y+19);
        }
    }

    // ── Center: Timer ─────────────────────────────────────────────────────────
    private void drawTimer(Graphics2D g, int W, int H) {
        int cx = W / 2;
        int t  = gameState.getTimeRemaining();
        boolean playing = gameState.getCurrentPhase() == GameState.Phase.PLAYING;
        boolean urgent  = playing && t < 30;

        // Skull or clock icon
        g.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 16));
        g.setColor(urgent ? RED_HI : TEXT_DIM);
        drawCentered(g, urgent ? "💀" : "⏱", cx, 25);

        // Time
        String ts = String.format("%02d:%02d", t / 60, t % 60);
        g.setFont(new Font("Consolas", Font.BOLD, 30));
        FontMetrics fm = g.getFontMetrics();
        if (urgent) {
            float pu = 0.5f + 0.5f*(float)Math.sin(animTick * 0.8);
            g.setColor(new Color(200, 30, 30, (int)(50+50*pu)));
            g.drawString(ts, cx - fm.stringWidth(ts)/2 - 1, 44);
        }
        g.setColor(urgent ? RED_HI : TEXT_HI);
        g.drawString(ts, cx - fm.stringWidth(ts)/2, 44);

        // Phase label
        String phase = switch (gameState.getCurrentPhase()) {
            case WAITING   -> "WAITING FOR PLAYERS";
            case PLAYING   -> "SURVIVAL MODE";
            case GAME_OVER -> "GAME OVER";
        };
        g.setFont(new Font("Segoe UI", Font.BOLD, 9));
        g.setColor(TEXT_DIM);
        drawCentered(g, phase, cx, 58);

        // Player counts
        if (playing) {
            long surv = gameState.getPlayers().values().stream().filter(p -> !p.isInfected()).count();
            long zomb = gameState.getPlayers().values().stream().filter(Player::isInfected).count();
            g.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g.setColor(GRN_HI);
            drawCentered(g, "🧍 " + surv + "   ☣ " + zomb, cx, 74);
        }

        // Controls hint
        g.setFont(new Font("Segoe UI", Font.PLAIN, 8));
        g.setColor(new Color(65, 55, 32));
        drawCentered(g, "WASD=Move  LMB=Shoot  F=Melee  1-5=Weapon  Q=Lunge  E=Howl  F11=Fullscreen", cx, 90);

        // Divider lines
        g.setColor(DIVIDER);
        g.drawLine(cx - 185, 2, cx - 185, H - 4);
        g.drawLine(cx + 185, 2, cx + 185, H - 4);
    }

    // ── Right: Weapon display ─────────────────────────────────────────────────
    private void drawWeapon(Graphics2D g, Player me, int W, int H) {
        if (me == null) return;
        int wx = W - 185;

        Weapon w    = me.getCurrentWeapon();
        int    ammo = me.currentAmmo();

        // Weapon name
        g.setFont(new Font("Segoe UI", Font.BOLD, 13));
        Color wc = switch (w) {
            case PISTOL  -> new Color(165,190,240);
            case SHOTGUN -> new Color(240,180,80);
            case RIFLE   -> new Color(80,215,115);
            case BAT     -> new Color(205,145,65);
            default      -> TEXT_DIM;
        };
        g.setColor(ammo == 0 && ammo >= 0 ? RED_HI : wc);
        g.drawString(w.displayName, wx, 22);

        // Ammo count
        String ammoStr = ammo < 0 ? "∞" : String.valueOf(ammo);
        g.setFont(new Font("Consolas", Font.BOLD, 24));
        g.setColor(ammo == 0 && ammo >= 0 ? RED_HI : TEXT_HI);
        g.drawString(ammoStr, wx, 52);

        // Ammo label
        g.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        g.setColor(TEXT_DIM);
        g.drawString(ammo < 0 ? "MELEE / INFINITE" : "ROUNDS", wx + 2, 66);

        // Empty warning
        if (ammo == 0 && ammo >= 0) {
            g.setFont(new Font("Segoe UI", Font.BOLD, 10));
            float pu = 0.5f + 0.5f*(float)Math.sin(animTick * 0.9);
            g.setColor(new Color(220, 50, 50, (int)(150+105*pu)));
            g.drawString("⚠ NO AMMO!", wx, 82);
        }

        // Weapon type indicator (ammo bars)
        if (ammo > 0) {
            int maxBullets = switch (w) {
                case PISTOL -> 15; case SHOTGUN -> 6; case RIFLE -> 30; default -> 1;
            };
            int bars = Math.min(ammo, maxBullets);
            for (int i = 0; i < Math.min(bars, 15); i++) {
                g.setColor(wc);
                g.fillRect(wx + i * 7, 88, 5, 8);
            }
            if (ammo > 15) {
                g.setFont(new Font("Segoe UI", Font.PLAIN, 8));
                g.setColor(TEXT_DIM); g.drawString("+" + (ammo-15), wx + 110, 96);
            }
        }
    }

    // ── Far right: Score & Kills ───────────────────────────────────────────────
    private void drawStats(Graphics2D g, Player me, int W, int H) {
        int rx = W - 12;

        // Score
        g.setFont(new Font("Segoe UI", Font.BOLD, 9)); g.setColor(TEXT_DIM);
        String sl = "SCORE"; FontMetrics slm = g.getFontMetrics();
        g.drawString(sl, rx - slm.stringWidth(sl), 18);

        String sv = me != null ? String.valueOf(me.getScore()) : "0";
        g.setFont(new Font("Segoe UI", Font.BOLD, 26)); g.setColor(AMB_HI);
        FontMetrics svm = g.getFontMetrics();
        g.drawString(sv, rx - svm.stringWidth(sv), 46);

        // Kills
        String kv = me != null ? me.getKills() + " kills" : "0 kills";
        g.setFont(new Font("Segoe UI", Font.PLAIN, 10)); g.setColor(RED_HI);
        FontMetrics kvm = g.getFontMetrics();
        g.drawString(kv, rx - kvm.stringWidth(kv), 64);

        // Player count
        int pc = gameState.getPlayers().size();
        String pcs = pc + " players";
        g.setFont(new Font("Segoe UI", Font.PLAIN, 9)); g.setColor(TEXT_DIM);
        FontMetrics pcm = g.getFontMetrics();
        g.drawString(pcs, rx - pcm.stringWidth(pcs), 80);

        // NPC count
        long npcAlive = gameState.getNpcZombies().values().stream().filter(NpcZombie::isAlive).count();
        String npc = "NPC: " + npcAlive;
        g.setFont(new Font("Segoe UI", Font.PLAIN, 9)); g.setColor(new Color(160,80,40));
        FontMetrics nm = g.getFontMetrics();
        g.drawString(npc, rx - nm.stringWidth(npc), 94);
    }

    private void drawCentered(Graphics2D g, String text, int cx, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, cx - fm.stringWidth(text) / 2, y);
    }
}
