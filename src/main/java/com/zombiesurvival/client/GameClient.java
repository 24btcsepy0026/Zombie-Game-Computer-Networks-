
package com.zombiesurvival.client;

import com.zombiesurvival.client.ui.GameScreen;
import com.zombiesurvival.shared.*;

import javax.swing.*;
import java.io.*;
import java.net.Socket;

/**
 * Entry point for the game client.
 * Asks for server IP and player name, connects via TCP, drives the UI.
 */
public class GameClient {

    private static final int PORT = 8080;

    private Socket             socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;
    private GameScreen         gameScreen;
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
            System.exit(1);
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
        // Server IP dialog
        JTextField ipField = new JTextField("localhost", 18);
        JTextField nameField = new JTextField(System.getProperty("user.name", "Player"), 18);
        JPanel panel = new JPanel(new java.awt.GridLayout(2, 2, 6, 6));
        panel.add(new JLabel("Server IP:"));   panel.add(ipField);
        panel.add(new JLabel("Your Name:"));   panel.add(nameField);

        int result = JOptionPane.showConfirmDialog(null, panel,
            "☣ Zombie Survival – Connect", JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) System.exit(0);

        String ip   = ipField.getText().trim();
        String name = nameField.getText().trim();
        if (ip.isEmpty())   ip   = "localhost";
        if (name.isEmpty()) name = "Player";

        new GameClient().start(ip, name);
    }
}
