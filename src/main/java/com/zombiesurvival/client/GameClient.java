
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
    private String             myPlayerId;

    public void start(String serverIp, String playerName) {
        try {
            socket = new Socket(serverIp, PORT);
            out    = new ObjectOutputStream(socket.getOutputStream());
            in     = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                "Could not connect to server at " + serverIp + ":" + PORT +
                "\n\nMake sure the server is running.",
                "Connection Failed", JOptionPane.ERROR_MESSAGE);
            return; // Return to menu instead of exiting
        }

        // Close menu frame if open
        if (menuFrame != null) {
            menuFrame.dispose();
        }

        // Build and show UI on EDT
        SwingUtilities.invokeLater(() -> {
            gameScreen = new GameScreen(this);
            gameScreen.setVisible(true);
        });

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
                    if (gameScreen != null)
                        gameScreen.updateState(gsu.getState(), myPlayerId);
                } else if (obj instanceof ChatBroadcast cb) {
                    if (gameScreen != null)
                        gameScreen.getChatPanel().addMessage(cb.getText());
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
        
        MainMenuScreen menuScreen = new MainMenuScreen(option -> {
            switch (option) {
                case "Start Game" -> showConnectionDialog();
                case "Invite Members" -> showInviteDialog();
                case "Settings" -> showSettingsDialog();
                case "Exit" -> System.exit(0);
            }
        });
        
        menuFrame.add(menuScreen);
        menuFrame.pack();
        menuFrame.setLocationRelativeTo(null);
        menuFrame.setVisible(true);
        menuScreen.requestFocusInWindow();
    }

    private void showConnectionDialog() {
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

            start(ip, name);
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
