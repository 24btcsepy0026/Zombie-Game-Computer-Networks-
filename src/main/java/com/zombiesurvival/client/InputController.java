package com.zombiesurvival.client;

import com.zombiesurvival.shared.MoveCommand;

import java.awt.event.*;
import java.util.*;

/**
 * Listens for WASD / Arrow key presses on the JFrame
 * and continuously sends MoveCommands to the server.
 */
public class InputController implements KeyListener {

    private static final int SPEED = 5;
    private final GameClient client;
    private final Set<Integer> held = Collections.synchronizedSet(new HashSet<>());

    public InputController(GameClient client) {
        this.client = client;
        Thread t = new Thread(() -> {
            while (true) {
                int dx = 0, dy = 0;
                synchronized (held) {
                    if (held.contains(KeyEvent.VK_W) || held.contains(KeyEvent.VK_UP))    dy -= SPEED;
                    if (held.contains(KeyEvent.VK_S) || held.contains(KeyEvent.VK_DOWN))  dy += SPEED;
                    if (held.contains(KeyEvent.VK_A) || held.contains(KeyEvent.VK_LEFT))  dx -= SPEED;
                    if (held.contains(KeyEvent.VK_D) || held.contains(KeyEvent.VK_RIGHT)) dx += SPEED;
                }
                if (dx != 0 || dy != 0) client.sendMessage(new MoveCommand(dx, dy));
                try { Thread.sleep(33); } catch (InterruptedException ignored) {}
            }
        }, "InputSender");
        t.setDaemon(true);
        t.start();
    }

    @Override public void keyTyped(KeyEvent e) {}
    @Override public void keyPressed(KeyEvent e)  { synchronized (held) { held.add(e.getKeyCode()); } }
    @Override public void keyReleased(KeyEvent e) { synchronized (held) { held.remove(e.getKeyCode()); } }
}
