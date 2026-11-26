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

    private final JPanel lobbyControls = new JPanel(new FlowLayout(FlowLayout.LEFT));

    public GameScreen(GameClient client, String inviteAddress, Runnable startMatch) {
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

        lobbyControls.add(new JLabel("Lobby  |  Move with WASD or arrow keys"));
        if (inviteAddress != null) {
            lobbyControls.add(new JLabel("  Invite address: " + inviteAddress));
            JButton startButton = new JButton("Start Match");
            startButton.setFocusable(false);
            startButton.addActionListener(e -> startMatch.run());
            lobbyControls.add(startButton);
        }
        lobbyControls.setVisible(false);
        add(lobbyControls, BorderLayout.NORTH);

        // Keyboard input for movement (attached to window, not sub-panel)
        InputController ic = new InputController(client);
        addKeyListener(ic);
        setFocusable(true);

        pack();
        setLocationRelativeTo(null);
        setResizable(false);
        addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowLostFocus(java.awt.event.WindowEvent event) {
                ic.clearHeldKeys();
            }
        });
    }

    /**
     * Called from the network thread → dispatches to EDT.
     */
    public void updateState(GameState state, String myPlayerId) {
        SwingUtilities.invokeLater(() -> {
            gameCanvas.setGameState(state);
            gameCanvas.setMyPlayerId(myPlayerId);
            boolean waiting = state.getCurrentPhase() == GameState.Phase.WAITING;
            if (lobbyControls.isVisible() != waiting) {
                lobbyControls.setVisible(waiting);
                pack();
            }
            hudPanel.setData(state, myPlayerId);
            hudPanel.repaint();
        });
    }

    public ChatPanel getChatPanel() { return chatPanel; }
}
