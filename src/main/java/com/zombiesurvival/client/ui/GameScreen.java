package com.zombiesurvival.client.ui;

import com.zombiesurvival.client.GameClient;
import com.zombiesurvival.client.InputController;
import com.zombiesurvival.shared.GameState;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Main game window.
 * Layout:  CENTER=GameCanvas | EAST=ChatPanel | SOUTH=HudPanel
 *
 * Features:
 *  - Launches maximised (near-fullscreen)
 *  - F11 toggles true full-screen exclusive mode
 *  - ESC exits full-screen
 */
public class GameScreen extends JFrame {

    private final GameCanvas gameCanvas;
    private final HudPanel   hudPanel;
    private final ChatPanel  chatPanel;

    private boolean fullScreen = false;
    private GraphicsDevice gd;

    public GameScreen(GameClient client) {
        super("☣ Zombie Survival");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setBackground(new Color(5, 5, 10));

        gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        gameCanvas = new GameCanvas();
        hudPanel   = new HudPanel();
        chatPanel  = new ChatPanel(client);

        setLayout(new BorderLayout(0, 0));
        add(gameCanvas, BorderLayout.CENTER);
        add(hudPanel,   BorderLayout.SOUTH);
        add(chatPanel,  BorderLayout.EAST);

        // InputController handles keyboard + mouse; link to canvas for camera offset
        InputController ic = new InputController(client, this);
        gameCanvas.setInputController(ic);
        ic.start();

        // F11 = toggle full-screen, ESC = leave full-screen
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), "toggleFS");
        getRootPane().getActionMap().put("toggleFS", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { toggleFullScreen(); }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "exitFS");
        getRootPane().getActionMap().put("exitFS", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (fullScreen) toggleFullScreen();
            }
        });

        setFocusable(true);
        setResizable(true);

        // Start maximised for a big-screen feel
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
        requestFocusInWindow();
    }

    private void toggleFullScreen() {
        if (!fullScreen) {
            // Enter full-screen
            if (gd.isFullScreenSupported()) {
                dispose();
                setUndecorated(true);
                gd.setFullScreenWindow(this);
                fullScreen = true;
                setVisible(true);
                requestFocusInWindow();
            }
        } else {
            // Exit full-screen
            gd.setFullScreenWindow(null);
            dispose();
            setUndecorated(false);
            setExtendedState(JFrame.MAXIMIZED_BOTH);
            setVisible(true);
            fullScreen = false;
            requestFocusInWindow();
        }
    }

    /** Called from network thread → dispatch to EDT. */
    public void updateState(GameState state, String myPlayerId) {
        SwingUtilities.invokeLater(() -> {
            gameCanvas.setMyPlayerId(myPlayerId);
            gameCanvas.setGameState(state);
            hudPanel.setData(state, myPlayerId);
            hudPanel.repaint();
        });
    }

    public ChatPanel getChatPanel() { return chatPanel; }
}
