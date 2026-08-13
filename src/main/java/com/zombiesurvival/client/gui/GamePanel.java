package com.zombiesurvival.client.gui;

import com.zombiesurvival.shared.GameState;
import com.zombiesurvival.shared.Player;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

public class GamePanel extends JPanel {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    
    private GameState gameState;

    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.DARK_GRAY);
    }

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        if (gameState == null) {
            drawCenteredText(g2d, "Connecting to server...", Color.WHITE);
            return;
        }

        if (gameState.getCurrentPhase() == GameState.Phase.WAITING) {
            drawCenteredText(g2d, "Waiting for more players... (" + gameState.getPlayers().size() + ")", Color.WHITE);
        } else if (gameState.getCurrentPhase() == GameState.Phase.PLAYING) {
            // Draw Timer
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 20));
            g2d.drawString("Time: " + gameState.getTimeRemaining(), 10, 30);
            
            // Draw Players
            Collection<Player> players = gameState.getPlayers().values();
            for (Player p : players) {
                if (p.isInfected()) {
                    g2d.setColor(Color.RED); // Zombie
                } else {
                    g2d.setColor(Color.GREEN); // Survivor
                }
                
                g2d.fillRect(p.getX(), p.getY(), p.getWidth(), p.getHeight());
                
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("Arial", Font.PLAIN, 12));
                g2d.drawString(p.getName(), p.getX(), p.getY() - 5);
            }
        } else if (gameState.getCurrentPhase() == GameState.Phase.GAME_OVER) {
            drawCenteredText(g2d, "GAME OVER! " + gameState.getWinnerMessage(), Color.YELLOW);
        }
    }
    
    private void drawCenteredText(Graphics2D g2d, String text, Color color) {
        g2d.setColor(color);
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        FontMetrics metrics = g2d.getFontMetrics();
        int x = (WIDTH - metrics.stringWidth(text)) / 2;
        int y = ((HEIGHT - metrics.getHeight()) / 2) + metrics.getAscent();
        g2d.drawString(text, x, y);
    }
}
