package com.zombiesurvival.client;

import com.zombiesurvival.client.ui.GameScreen;
import com.zombiesurvival.client.ui.MainMenuScreen;
import com.zombiesurvival.shared.*;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for the game client.
 * Shows the Zombie Escape menu (Solo / Host / Join), connects via TCP,
 * and drives the main GameScreen UI.
 */
public class GameClient {

    public static final int PORT = 8080;

    private static volatile boolean serverStarted = false;

    private Socket             socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;
    private GameScreen         gameScreen;
    private JFrame             menuFrame;
    private MainMenuScreen     currentMenuScreen;
    private String             myPlayerId;
    private GameState          lastGameState;

    // Cached local player info (updated from GameStateUpdate)
    private volatile float  myX = 0, myY = 0;
    private volatile Weapon myWeapon = Weapon.FIST;

    private volatile boolean intentionalDisconnect = false;

    public boolean start(String serverIp, String playerName) {
        return start(serverIp, PORT, playerName);
    }

    public boolean start(String serverIp, int port, String playerName) {
        disconnect();
        intentionalDisconnect = false;
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

        sendMessage(new JoinRequest(playerName));
        new Thread(this::receiveLoop, "ClientReceiver").start();
        return true;
    }

    public synchronized void disconnect() {
        intentionalDisconnect = true;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        socket = null;
        out = null;
        in = null;
        myPlayerId = null;
        lastGameState = null;
        if (currentMenuScreen != null) {
            currentMenuScreen.setConnectedPlayers(new ArrayList<>());
            currentMenuScreen.setHostPlayerName("");
        }
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

                    // Cache local player position and weapon
                    Player me = state.getPlayer(myPlayerId);
                    if (me != null) {
                        myX      = me.getXf() + Player.SIZE / 2f;
                        myY      = me.getYf() + Player.SIZE / 2f;
                        myWeapon = me.getCurrentWeapon();
                    }

                    // Update connected players & host name on lobby menu if visible
                    if (currentMenuScreen != null && currentMenuScreen.isShowing()) {
                        List<String> playerNames = new ArrayList<>();
                        for (Player p : state.getPlayers().values()) {
                            playerNames.add(p.getName());
                        }
                        String host = state.getHostPlayerName();
                        SwingUtilities.invokeLater(() -> {
                            currentMenuScreen.setHostPlayerName(host);
                            currentMenuScreen.setConnectedPlayers(playerNames);
                        });
                    }

                    // Auto-open game screen when server transitions to PLAYING
                    if (state.getCurrentPhase() == GameState.Phase.PLAYING && gameScreen == null) {
                        SwingUtilities.invokeLater(this::openGameScreen);
                    }

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
            if (intentionalDisconnect) return;
            SwingUtilities.invokeLater(() -> {
                if (gameScreen != null) {
                    JOptionPane.showMessageDialog(gameScreen,
                        "Disconnected from server.", "Connection Lost",
                        JOptionPane.WARNING_MESSAGE);
                    System.exit(0);
                } else if (currentMenuScreen != null) {
                    JOptionPane.showMessageDialog(menuFrame,
                        "Disconnected from host lobby.", "Lobby Closed",
                        JOptionPane.WARNING_MESSAGE);
                    currentMenuScreen.setView(MainMenuScreen.ViewState.MULTIPLAYER);
                }
            });
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

    // ── Integrated Server Helper ───────────────────────────────────────────────

    private synchronized void ensureLocalServerRunning() {
        if (serverStarted) return;

        // Check if port 8080 is already listening
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

    // ── Menu & Lobby Flow ──────────────────────────────────────────────────────

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
        if (start("localhost", name)) {
            sendMessage(new StartGameMessage(GameState.GameMode.SOLO));
            openGameScreen();
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
        JTextField ipField   = new JTextField(currentMenuScreen != null ? currentMenuScreen.getLocalIpAddress() : "localhost", 16);
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
            String ip   = ipField.getText().trim();
            String name = nameField.getText().trim();
            int port    = PORT;
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
        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JCheckBox soundCheck   = new JCheckBox("Enable Sound Effects", SoundManager.isEnabled());
        int currentVol = Math.round(SoundManager.getVolume() * 100f);
        JSlider   volumeSlider = new JSlider(0, 100, currentVol);
        volumeSlider.setMajorTickSpacing(25);
        volumeSlider.setPaintTicks(true);
        volumeSlider.setPaintLabels(true);

        panel.add(soundCheck);
        panel.add(new JLabel());
        panel.add(new JLabel("Master Volume:"));
        panel.add(volumeSlider);

        int res = JOptionPane.showConfirmDialog(menuFrame, panel,
            "Settings", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            SoundManager.setEnabled(soundCheck.isSelected());
            SoundManager.setVolume(volumeSlider.getValue() / 100f);
        }
    }

    // ── Helpers for InputController ────────────────────────────────────────────

    public float  getMyPlayerWorldX()   { return myX; }
    public float  getMyPlayerWorldY()   { return myY; }
    public Weapon getMyCurrentWeapon()  { return myWeapon; }

    /** Hint from key press (1–5); server overrides via pickups. */
    public void setLocalWeaponHint(Weapon w) { this.myWeapon = w; }

    // ── Entry point & Menu ─────────────────────────────────────────────────────

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
            @Override public void onPlaySolo()               { handlePlaySolo(); }
            @Override public void onInviteMembersHost()      { handleInviteMembersHost(); }
            @Override public void onJoinLobby()              { handleJoinLobby(); }
            @Override public void onStartMultiplayerGame()   { handleStartMultiplayerMatch(); }
            @Override public void onOpenSettings()           { handleOpenSettings(); }
            @Override public void onLeaveLobby()             { disconnect(); }
        });

        menuFrame.add(currentMenuScreen);
        menuFrame.pack();
        menuFrame.setLocationRelativeTo(null);
        menuFrame.setVisible(true);
        currentMenuScreen.requestFocusInWindow();
    }
}
