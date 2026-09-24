package com.zombiesurvival.client.ui;

import com.zombiesurvival.client.GameClient;
import com.zombiesurvival.client.InputController;
import com.zombiesurvival.shared.GameState;

import javax.swing.*;
import java.awt.*;

/**
 * Main application JFrame.
 * Layout:  CENTER=GameCanvas | EAST=ChatPanel | SOUTH=HudPanel
 *
 * The InputController is registered on this frame AND linked to the
 * GameCanvas so it receives camera-offset updates for aim direction.
 */
public class GameScreen extends JFrame {

    private final GameCanvas gameCanvas;
    private final HudPanel   hudPanel;
    private final ChatPanel  chatPanel;

    public GameScreen(GameClient client) {
        super("☣ Zombie Survival");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setBackground(new Color(8, 10, 16));

        gameCanvas = new GameCanvas();
        hudPanel   = new HudPanel();
        chatPanel  = new ChatPanel(client);

        add(gameCanvas, BorderLayout.CENTER);
        add(hudPanel,   BorderLayout.SOUTH);
        add(chatPanel,  BorderLayout.EAST);

        // InputController handles keyboard + mouse; link to canvas for camera offset
        InputController ic = new InputController(client, this);
        gameCanvas.setInputController(ic);
        ic.start();

        setFocusable(true);
        requestFocusInWindow();

        pack();
        setLocationRelativeTo(null);
        setResizable(false);
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
