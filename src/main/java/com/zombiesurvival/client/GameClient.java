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
 * Connects via TCP, coordinates menus, server initialization, and drives the UI.
 */
public class GameClient {

    public static final int PORT = 8080;

    private static volatile boolean serverStarted = false;

    public static void setServerStarted(boolean started) {
        serverStarted = started;
    }

    private Socket             socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;
    private GameScreen         gameScreen;
    private JFrame             menuFrame;
    private MainMenuScreen     currentMenuScreen;
    private String             myPlayerId;
    private GameState          lastGameState;
    private volatile boolean   closingSession = false;
    private volatile boolean   voxel3dActive  = false;

    public JFrame getMenuFrame() {
        return menuFrame;
    }

    public GameState getLastGameState() {
        return lastGameState;
    }

    public String getMyPlayerId() {
        return myPlayerId;
    }

    public void disconnect() {
        closingSession = true;
        voxel3dActive = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
        socket = null;
        out = null;
        in = null;
        myPlayerId = null;
        lastGameState = null;
        closingSession = false;
    }

    /**
     * Start connection to server with default port.
     */
    public boolean start(String serverIp, String playerName) {
        return start(serverIp, PORT, playerName);
    }

    /**
     * Start connection to server with custom port.
     */
    public boolean start(String serverIp, int port, String playerName) {
        try {
            socket = new Socket(serverIp, port);
            out    = new ObjectOutputStream(socket.getOutputStream());
            in     = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(menuFrame,
                "Could not connect to server at " + serverIp + ":" + port +
                "\n\nMake sure the host has started the server or game.",
                "Connection Failed", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        // Send join request
        sendMessage(new JoinRequest(playerName));

        // Listen for server messages on a background thread
        new Thread(this::receiveLoop, "ClientReceiver").start();
        return true;
    }

    private void receiveLoop() {
        try {
            while (true) {
                Object obj = in.readObject();
                if (obj instanceof AssignIdMessage aim) {
                    myPlayerId = aim.getPlayerId();
                } else if (obj instanceof GameStateUpdate gsu) {
                    GameState state = gsu.getState();
                    lastGameState = state;

                    // Update connected players and host name on menu if visible
                    if (currentMenuScreen != null && currentMenuScreen.isShowing()) {
                        java.util.List<String> playerNames = new java.util.ArrayList<>();
                        for (Player p : state.getPlayers().values()) {
                            playerNames.add(p.getName());
                        }
                        String host = state.getHostPlayerName();
                        SwingUtilities.invokeLater(() -> {
                            currentMenuScreen.setHostPlayerName(host);
                            currentMenuScreen.setConnectedPlayers(playerNames);
                        });
                    }

                    // Auto-open game screen when the game transitions to PLAYING (2D only)
                    if (!voxel3dActive && state.getCurrentPhase() == GameState.Phase.PLAYING && gameScreen == null) {
                        SwingUtilities.invokeLater(this::openGameScreen);
                    }

                    // Update game screen if open
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
            if (!closingSession) {
                SwingUtilities.invokeLater(() -> {
                    if (gameScreen != null) {
                        JOptionPane.showMessageDialog(gameScreen,
                            "Disconnected from server.", "Connection Lost",
                            JOptionPane.WARNING_MESSAGE);
                    }
                });
                System.exit(0);
            }
        }
    }

    public void sendMessage(NetworkMessage msg) {
        if (out == null) return;
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

    // ── Integrated Server Helper ─────────────────────────────────────────────

    private synchronized void ensureLocalServerRunning() {
        if (serverStarted) return;

        // Check if port 8080 is already active
        try (Socket s = new Socket("localhost", PORT)) {
            serverStarted = true;
            return;
        } catch (IOException ignored) {}

        Thread serverThread = new Thread(() -> {
            try {
                System.out.println("Starting integrated server on port " + PORT + "...");
                new com.zombiesurvival.server.GameServer().start();
            } catch (Exception e) {
                System.err.println("Server exception: " + e.getMessage());
            }
        }, "IntegratedServer");
        serverThread.setDaemon(true);
        serverThread.start();
        serverStarted = true;

        try {
            Thread.sleep(600);
        } catch (InterruptedException ignored) {}
    }

    // ── Flow Actions ─────────────────────────────────────────────────────────

    public void openGameScreen() {
        if (gameScreen != null) return;

        if (menuFrame != null) {
            menuFrame.setVisible(false);
            if (currentMenuScreen != null) {
                currentMenuScreen.cleanup();
            }
            menuFrame.dispose();
            menuFrame = null;
        }

        gameScreen = new GameScreen(this);
        gameScreen.setVisible(true);

        if (lastGameState != null) {
            gameScreen.updateState(lastGameState, myPlayerId);
        }
    }

    private void handlePlaySolo() {
        ensureLocalServerRunning();
        String name = System.getProperty("user.name", "Survivor");
        voxel3dActive = true;
        if (start("localhost", name)) {
            // Signal solo mode: server will spawn AI zombies on the spot
            sendMessage(new StartGameMessage(GameState.GameMode.SOLO));
            com.zombiesurvival.client.voxel.VoxelGameWindow.launch(this, this::disconnect);
        } else {
            voxel3dActive = false;
        }
    }

    private void handleInviteMembersHost() {
        ensureLocalServerRunning();
        String name = System.getProperty("user.name", "Host");
        if (socket == null || socket.isClosed()) {
            start("localhost", name);
        }
        if (currentMenuScreen != null) {
            currentMenuScreen.setPlayerName(name);
            currentMenuScreen.setHostPlayerName(name);
            currentMenuScreen.setView(MainMenuScreen.ViewState.INVITE_LOBBY);
        }
    }

    private void handleJoinLobby() {
        JTextField ipField = new JTextField(currentMenuScreen != null ? currentMenuScreen.getLocalIpAddress() : "localhost", 16);
        JTextField portField = new JTextField(String.valueOf(PORT), 6);
        JTextField nameField = new JTextField(System.getProperty("user.name", "Player"), 16);

        JPanel panel = new JPanel(new GridLayout(3, 2, 8, 8));
        panel.add(new JLabel("Host IP Address:"));
        panel.add(ipField);
        panel.add(new JLabel("Port:"));
        panel.add(portField);
        panel.add(new JLabel("Your Name:"));
        panel.add(nameField);

        int result = JOptionPane.showConfirmDialog(menuFrame, panel,
            "Join Multiplayer Lobby", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String ip = ipField.getText().trim();
            String name = nameField.getText().trim();
            int port = PORT;
            try {
                port = Integer.parseInt(portField.getText().trim());
            } catch (Exception ignored) {}

            if (ip.isEmpty()) ip = "localhost";
            if (name.isEmpty()) name = "Player";

            if (start(ip, port, name)) {
                if (currentMenuScreen != null) {
                    currentMenuScreen.setPlayerName(name);
                    currentMenuScreen.setView(MainMenuScreen.ViewState.CLIENT_LOBBY);
                }
            }
        }
    }

    private void handleStartMultiplayerMatch() {
        int count = (lastGameState != null) ? lastGameState.getPlayers().size() : 1;
        GameState.GameMode mode = (count >= 2) ? GameState.GameMode.MULTIPLAYER : GameState.GameMode.SOLO;
        sendMessage(new StartGameMessage(mode));
        openGameScreen();
    }

    private void handleOpenSettings() {
        JPanel panel = new JPanel(new GridLayout(3, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JCheckBox soundCheck = new JCheckBox("Enable Sound Effects", true);
        JCheckBox musicCheck = new JCheckBox("Enable Ambient Music", true);
        JSlider volumeSlider = new JSlider(0, 100, 75);
        volumeSlider.setMajorTickSpacing(25);
        volumeSlider.setPaintTicks(true);
        volumeSlider.setPaintLabels(true);

        panel.add(soundCheck);
        panel.add(new JLabel());
        panel.add(musicCheck);
        panel.add(new JLabel());
        panel.add(new JLabel("Master Volume:"));
        panel.add(volumeSlider);

        JOptionPane.showMessageDialog(menuFrame, panel,
            "Settings", JOptionPane.PLAIN_MESSAGE);
    }

    // ── Entry Point & Menu ───────────────────────────────────────────────────

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GameClient client = new GameClient();
            client.showMainMenu();
        });
    }

    public void showMainMenu() {
        menuFrame = new JFrame("ZOMBIE ESCAPE");
        menuFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        currentMenuScreen = new MainMenuScreen(new MainMenuScreen.MenuActionListener() {
            @Override
            public void onPlaySolo() {
                handlePlaySolo();
            }

            @Override
            public void onInviteMembersHost() {
                handleInviteMembersHost();
            }

            @Override
            public void onJoinLobby() {
                handleJoinLobby();
            }

            @Override
            public void onStartMultiplayerGame() {
                handleStartMultiplayerMatch();
            }

            @Override
            public void onOpenSettings() {
                handleOpenSettings();
            }
        });

        menuFrame.add(currentMenuScreen);
        menuFrame.pack();
        menuFrame.setLocationRelativeTo(null);
        menuFrame.setVisible(true);
        currentMenuScreen.requestFocusInWindow();
    }
}
