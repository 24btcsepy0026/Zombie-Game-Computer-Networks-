package com.zombiesurvival.client.ui;

import com.zombiesurvival.client.GameClient;
import com.zombiesurvival.client.InputController;
import com.zombiesurvival.shared.GameState;

import javax.swing.*;
import java.awt.*;

/**
 * Main application JFrame.
 * Layout:  CENTER=GameCanvas | EAST=ChatPanel | SOUTH=HudPanel
 */
public class GameScreen extends JFrame {

    private final GameCanvas gameCanvas;
    private final HudPanel   hudPanel;
    private final ChatPanel  chatPanel;

    public GameScreen(GameClient client) {
        super("☣ Zombie Survival");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setBackground(new Color(10, 12, 18));

        gameCanvas = new GameCanvas();
        hudPanel   = new HudPanel();
        chatPanel  = new ChatPanel(client);

        add(gameCanvas, BorderLayout.CENTER);
        add(hudPanel,   BorderLayout.SOUTH);
        add(chatPanel,  BorderLayout.EAST);

        // Keyboard input for movement — InputController registers its own listener
        InputController ic = new InputController(client, this);
        ic.start();
        setFocusable(true);
        requestFocusInWindow();

        pack();
        setLocationRelativeTo(null);
        setResizable(false);
    }

    /**
     * Called from the network thread → dispatches to EDT.
     */
    public void updateState(GameState state, String myPlayerId) {
        SwingUtilities.invokeLater(() -> {
            gameCanvas.setGameState(state);
            gameCanvas.setMyPlayerId(myPlayerId);
            hudPanel.setData(state, myPlayerId);
            hudPanel.repaint();
        });
    }

    public ChatPanel getChatPanel() { return chatPanel; }
}
