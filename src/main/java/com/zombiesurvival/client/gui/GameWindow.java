package com.zombiesurvival.client.gui;

import com.zombiesurvival.client.GameClient;
import com.zombiesurvival.client.InputController;
import com.zombiesurvival.shared.GameState;

import javax.swing.*;
import java.awt.*;

public class GameWindow extends JFrame {
    private GamePanel gamePanel;
    private GameClient client;

    public GameWindow(GameClient client) {
        this.client = client;
        setTitle("Zombie Survival");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        gamePanel = new GamePanel();
        add(gamePanel, BorderLayout.CENTER);
        
        pack(); // sizes the frame so that all its contents are at or above their preferred sizes
        setLocationRelativeTo(null); // center on screen

        // Setup input controller
        InputController inputController = new InputController(client);
        addKeyListener(inputController);
        
        // Timer for steady rendering (approx 60 FPS)
        Timer timer = new Timer(16, e -> gamePanel.repaint());
        timer.start();
    }

    public void updateGameState(GameState state) {
        gamePanel.setGameState(state);
    }
}
