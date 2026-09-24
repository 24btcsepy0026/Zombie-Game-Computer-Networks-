
package com.zombiesurvival.client;

import com.zombiesurvival.client.ui.GameScreen;
import com.zombiesurvival.client.ui.MainMenuScreen;
import com.zombiesurvival.shared.*;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;

/**
 * Entry point for the game client.
 * Shows main menu, then asks for server IP and player name, connects via TCP, drives the UI.
 */
public class GameClient {

    private static final int PORT = 8080;

    private Socket             socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;
    private GameScreen         gameScreen;
    private JFrame             menuFrame;
    private MainMenuScreen     currentMenuScreen;
    private String             myPlayerId;
    private GameState          lastGameState;

    public void start(String serverIp, String playerName) {
        try {
            socket = new Socket(serverIp, PORT);
            out    = new ObjectOutputStream(socket.getOutputStream());
            in     = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(menuFrame,
                "Could not connect to server at " + serverIp + ":" + PORT +
                "\n\nMake sure the server is running.",
                "Connection Failed", JOptionPane.ERROR_MESSAGE);
            // Reset menu to initial state
            if (currentMenuScreen != null) {
                currentMenuScreen.setWaitingMode(false);
            }
            return;
        }

        // DON'T close menu frame - keep it visible
        // DON'T show game screen - stay on menu

        // Send join request
        sendMessage(new JoinRequest(playerName));

        // Listen for server messages on a background thread
        new Thread(this::receiveLoop, "ClientReceiver").start();
    }

    private void receiveLoop() {
        try {
            while (true) {
                Object obj = in.readObject();
                if (obj instanceof AssignIdMessage aim) {
                    myPlayerId = aim.getPlayerId();
                } else if (obj instanceof GameStateUpdate gsu) {
                    GameState state = gsu.getState();
                    lastGameState = state; // Store for later
                    
                    // Update connected players list on menu
                    if (currentMenuScreen != null && currentMenuScreen.isVisible()) {
                        java.util.List<String> playerNames = new java.util.ArrayList<>();
                        for (Player p : state.getPlayers().values()) {
                            playerNames.add(p.getName());
                        }
                        SwingUtilities.invokeLater(() -> 
                            currentMenuScreen.setConnectedPlayers(playerNames)
                        );
                    }
                    
                    // Update game screen if it's open
                    if (gameScreen != null) {
                        gameScreen.updateState(state, myPlayerId);
                    }
                } else if (obj instanceof ChatBroadcast cb) {
                    if (gameScreen != null) {
                        gameScreen.getChatPanel().addMessage(cb.getText());
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(null,
                    "Disconnected from server.", "Connection Lost",
                    JOptionPane.WARNING_MESSAGE));
            System.exit(0);
        }
    }

    public void sendMessage(NetworkMessage msg) {
        try {
            synchronized (out) {
                out.reset();
                out.writeObject(msg);
                out.flush();
            }
        } catch (IOException e) {
            System.err.println("Send error: " + e.getMessage());
        }
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GameClient client = new GameClient();
            client.showMainMenu();
        });
    }

    private void showMainMenu() {
        menuFrame = new JFrame("ZOMBIE ESCAPE");
        menuFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        currentMenuScreen = new MainMenuScreen(option -> {
            switch (option) {
                case "Start Game" -> showConnectionDialogAndWait();
                case "Go to Lobby" -> goToGameLobby();
                case "Invite Members" -> showInviteDialog();
                case "Settings" -> showSettingsDialog();
                case "Exit" -> System.exit(0);
            }
        });
        
        menuFrame.add(currentMenuScreen);
        menuFrame.pack();
        menuFrame.setLocationRelativeTo(null);
        menuFrame.setVisible(true);
        currentMenuScreen.requestFocusInWindow();
    }

    private void goToGameLobby() {
        // Close menu frame
        if (menuFrame != null) {
            menuFrame.dispose();
        }

        // Show game screen
        SwingUtilities.invokeLater(() -> {
            gameScreen = new GameScreen(this);
            gameScreen.setVisible(true);
            
            // If we have stored game state, update it
            if (lastGameState != null) {
                gameScreen.updateState(lastGameState, myPlayerId);
            }
        });
    }

    private void showConnectionDialogAndWait() {
        JTextField ipField = new JTextField("localhost", 20);
        JTextField nameField = new JTextField(System.getProperty("user.name", "Player"), 20);
        
        JPanel panel = new JPanel(new java.awt.GridLayout(2, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(new JLabel("Server IP:"));   
        panel.add(ipField);
        panel.add(new JLabel("Your Name:"));   
        panel.add(nameField);

        int result = JOptionPane.showConfirmDialog(menuFrame, panel,
            "☣ Connect to Server", JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String ip   = ipField.getText().trim();
            String name = nameField.getText().trim();
            if (ip.isEmpty())   ip   = "localhost";
            if (name.isEmpty()) name = "Player";

            // Switch to waiting mode - show only "Invite Members"
            if (currentMenuScreen != null) {
                currentMenuScreen.setWaitingMode(true);
                currentMenuScreen.setPlayerName(name);
            }
            
            // Connect to server
            start(ip, name);
        }
    }

    private void startGameDirectly() {
        // This method is no longer used
    }

    private static void showConnectionDialog() {
        GameClient client = new GameClient();
        
        JTextField ipField = new JTextField("localhost", 20);
        JTextField nameField = new JTextField(System.getProperty("user.name", "Player"), 20);
        
        JPanel panel = new JPanel(new java.awt.GridLayout(2, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(new JLabel("Server IP:"));   
        panel.add(ipField);
        panel.add(new JLabel("Your Name:"));   
        panel.add(nameField);

        int result = JOptionPane.showConfirmDialog(null, panel,
            "☣ Connect to Server", JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String ip   = ipField.getText().trim();
            String name = nameField.getText().trim();
            if (ip.isEmpty())   ip   = "localhost";
            if (name.isEmpty()) name = "Player";

            client.start(ip, name);
        } else {
            System.exit(0);
        }
    }

    private void showInviteDialog() {
        // Get local IP for invitations
        String localIp = "localhost";
        try {
            localIp = java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            // Keep default
        }

        String message = "Share this information with friends:\n\n" +
                        "Server IP: " + localIp + "\n" +
                        "Port: " + PORT + "\n\n" +
                        "They can connect using 'Start Game' option.";
        
        JTextArea textArea = new JTextArea(message);
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setBackground(new Color(240, 240, 240));
        textArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JOptionPane.showMessageDialog(menuFrame, textArea,
            "Invite Friends", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showSettingsDialog() {
        JPanel panel = new JPanel(new java.awt.GridLayout(3, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JCheckBox soundCheck = new JCheckBox("Enable Sound", true);
        JCheckBox musicCheck = new JCheckBox("Enable Music", true);
        JSlider volumeSlider = new JSlider(0, 100, 70);
        volumeSlider.setMajorTickSpacing(25);
        volumeSlider.setPaintTicks(true);
        volumeSlider.setPaintLabels(true);
        
        panel.add(soundCheck);
        panel.add(new JLabel());
        panel.add(musicCheck);
        panel.add(new JLabel());
        panel.add(new JLabel("Volume:"));
        panel.add(volumeSlider);
        
        JOptionPane.showMessageDialog(menuFrame, panel,
            "Settings", JOptionPane.PLAIN_MESSAGE);
    }
}
