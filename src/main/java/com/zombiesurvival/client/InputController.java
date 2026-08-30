package com.zombiesurvival.client;

import com.zombiesurvival.shared.MoveCommand;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Polls held keys at 30 Hz and sends MoveCommand with dx/dy/sprinting.
 * WASD + Arrow keys for movement, Shift to sprint.
 */
public class InputController {

    private static final int SPEED    = 5;
    private static final int TICK_MS  = 33; // ~30 Hz

    private final GameClient          client;
    private final Set<Integer>        held = ConcurrentHashMap.newKeySet();
    private       javax.swing.Timer   timer;

    public InputController(GameClient client, JFrame frame) {
        this.client = client;
        frame.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed (KeyEvent e) { held.add(e.getKeyCode()); }
            @Override public void keyReleased(KeyEvent e) { held.remove(e.getKeyCode()); }
        });
    }

    public void start() {
        timer = new javax.swing.Timer(TICK_MS, e -> sendMove());
        timer.start();
    }

    public void stop() {
        if (timer != null) timer.stop();
    }

    private void sendMove() {
        int dx = 0, dy = 0;
        if (held.contains(KeyEvent.VK_LEFT)  || held.contains(KeyEvent.VK_A)) dx -= SPEED;
        if (held.contains(KeyEvent.VK_RIGHT) || held.contains(KeyEvent.VK_D)) dx += SPEED;
        if (held.contains(KeyEvent.VK_UP)    || held.contains(KeyEvent.VK_W)) dy -= SPEED;
        if (held.contains(KeyEvent.VK_DOWN)  || held.contains(KeyEvent.VK_S)) dy += SPEED;

        if (dx == 0 && dy == 0) return;

        boolean sprinting = held.contains(KeyEvent.VK_SHIFT);
        client.sendMessage(new MoveCommand(dx, dy, sprinting));
    }
}
