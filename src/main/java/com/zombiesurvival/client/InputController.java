package com.zombiesurvival.client;

import com.zombiesurvival.shared.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles keyboard (WASD / arrows / Shift / Q / E) and mouse input.
 *
 * Movement:  WASD / Arrow keys, Shift to sprint
 * Shoot:     Left mouse click — fires toward mouse cursor
 * Melee:     F key — swings current melee weapon toward mouse
 * Lunge:     Q  — zombie lunge ability
 * Howl:      E  — zombie howl ability
 * Weapon 1-4: 1=Fist 2=Pistol 3=Shotgun 4=Rifle
 */
public class InputController {

    private static final int MOVE_SPEED = 5;
    private static final int MOVE_HZ    = 30; // ticks per second

    private final GameClient        client;
    private final JFrame            frame;
    private final Set<Integer>      held       = ConcurrentHashMap.newKeySet();
    private       javax.swing.Timer moveTimer;

    // Mouse state (relative to game canvas)
    private volatile float mouseWorldX = 0, mouseWorldY = 0;
    private volatile float camOffsetX  = 0, camOffsetY  = 0;

    public InputController(GameClient client, JFrame frame) {
        this.client = client;
        this.frame  = frame;

        // ── Keyboard ──────────────────────────────────────────────────────────
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                held.add(e.getKeyCode());
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_Q -> client.sendMessage(new AbilityCommand(AbilityCommand.Type.LUNGE));
                    case KeyEvent.VK_E -> client.sendMessage(new AbilityCommand(AbilityCommand.Type.HOWL));
                    case KeyEvent.VK_F -> sendMeleeAttack();
                    case KeyEvent.VK_1 -> switchWeapon(Weapon.FIST);
                    case KeyEvent.VK_2 -> switchWeapon(Weapon.PISTOL);
                    case KeyEvent.VK_3 -> switchWeapon(Weapon.SHOTGUN);
                    case KeyEvent.VK_4 -> switchWeapon(Weapon.RIFLE);
                    case KeyEvent.VK_5 -> switchWeapon(Weapon.BAT);
                }
            }
            @Override
            public void keyReleased(KeyEvent e) { held.remove(e.getKeyCode()); }
        });

        // ── Mouse (left click = shoot) ────────────────────────────────────────
        frame.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) sendShootFromFrame(e.getPoint());
            }
        });

        // Track mouse for aim direction on canvas, continuous fire on drag
        frame.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e)   { updateMousePos(e.getPoint()); }
            @Override
            public void mouseDragged(MouseEvent e) {
                updateMousePos(e.getPoint());
                if (SwingUtilities.isLeftMouseButton(e)) sendShootFromFrame(e.getPoint());
            }
        });
    }

    public void start() {
        moveTimer = new javax.swing.Timer(1000 / MOVE_HZ, e -> sendMove());
        moveTimer.start();
    }

    public void stop() { if (moveTimer != null) moveTimer.stop(); }

    /** Called by GameCanvas whenever its camera position changes. */
    public void updateCameraOffset(float camX, float camY) {
        this.camOffsetX = camX;
        this.camOffsetY = camY;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void updateMousePos(Point p) {
        // Convert frame-relative point → canvas-relative → world position
        Insets ins = frame.getInsets();
        mouseWorldX = (p.x - ins.left) + camOffsetX;
        mouseWorldY = (p.y - ins.top)  + camOffsetY;
    }

    private void sendMove() {
        int dx = 0, dy = 0;
        if (held.contains(KeyEvent.VK_A) || held.contains(KeyEvent.VK_LEFT))  dx -= MOVE_SPEED;
        if (held.contains(KeyEvent.VK_D) || held.contains(KeyEvent.VK_RIGHT)) dx += MOVE_SPEED;
        if (held.contains(KeyEvent.VK_W) || held.contains(KeyEvent.VK_UP))    dy -= MOVE_SPEED;
        if (held.contains(KeyEvent.VK_S) || held.contains(KeyEvent.VK_DOWN))  dy += MOVE_SPEED;
        if (dx == 0 && dy == 0) return;
        boolean sprint = held.contains(KeyEvent.VK_SHIFT);
        client.sendMessage(new MoveCommand(dx, dy, sprint));
    }

    private void sendShootFromFrame(Point framePoint) {
        // Frame-relative point includes the title bar (insets.top).
        // Subtract insets to get canvas-relative screen position.
        Insets ins = frame.getInsets();
        Point canvasPoint = new Point(framePoint.x - ins.left, framePoint.y - ins.top);
        sendShoot(canvasPoint);
    }

    private void sendShoot(Point screenPoint) {
        // Direction vector from player centre → mouse (in world space)
        float px = client.getMyPlayerWorldX();
        float py = client.getMyPlayerWorldY();
        float wx = screenPoint.x + camOffsetX;
        float wy = screenPoint.y + camOffsetY;
        float dx = wx - px, dy = wy - py;
        float len = (float) Math.sqrt(dx*dx + dy*dy);
        if (len < 0.01f) return;
        Weapon w = client.getMyCurrentWeapon();
        client.sendMessage(new ShootCommand(dx / len, dy / len, w));
    }

    private void sendMeleeAttack() {
        float px = client.getMyPlayerWorldX();
        float py = client.getMyPlayerWorldY();
        float dx = mouseWorldX - px, dy = mouseWorldY - py;
        float len = (float) Math.sqrt(dx*dx + dy*dy);
        if (len < 0.01f) { dx = 1; dy = 0; len = 1; }
        Weapon w = client.getMyCurrentWeapon();
        if (!w.isMelee()) w = Weapon.FIST;
        client.sendMessage(new ShootCommand(dx / len, dy / len, w));
    }

    private void switchWeapon(Weapon w) {
        // Local hint only — server authoritative weapon is set via pickups
        client.setLocalWeaponHint(w);
    }
}
