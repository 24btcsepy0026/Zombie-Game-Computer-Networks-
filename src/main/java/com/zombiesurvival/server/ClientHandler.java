package com.zombiesurvival.server;

import com.zombiesurvival.shared.*;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final GameState gameState;
    private final GameServer server;
    private ObjectOutputStream out;
    private ObjectInputStream  in;
    private String playerId;
    private String playerName;

    public ClientHandler(Socket socket, GameState gameState, GameServer server) {
        this.socket    = socket;
        this.gameState = gameState;
        this.server    = server;
        try {
            // Output must be created before input to avoid deadlock
            out = new ObjectOutputStream(socket.getOutputStream());
            in  = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getPlayerId() { return playerId; }

    @Override
    public void run() {
        try {
            while (true) {
                Object obj = in.readObject();
                if (obj instanceof NetworkMessage) {
                    handleMessage((NetworkMessage) obj);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            // Client disconnected
        } finally {
            server.removeClient(this);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void handleMessage(NetworkMessage msg) {
        if (msg instanceof JoinRequest) {
            JoinRequest jr = (JoinRequest) msg;
            this.playerName = jr.getPlayerName();
            this.playerId   = java.util.UUID.randomUUID().toString();

            // Spawn at a default position; GameEngine.startGame() will reassign
            int spawnX = 1 * GameState.TILE_SIZE + 2;
            int spawnY = 1 * GameState.TILE_SIZE + 2;
            Player newPlayer = new Player(playerId, playerName, spawnX, spawnY, Player.Role.SURVIVOR);
            gameState.addPlayer(newPlayer);

            // Tell this client its own ID
            sendMessage(new AssignIdMessage(playerId));

            // Broadcast join notification
            String joinMsg = "** " + playerName + " joined the game **";
            gameState.addChatMessage(joinMsg);
            server.broadcastToAll(new ChatBroadcast(joinMsg));
            System.out.println("[JOIN] " + playerName + " (" + playerId + ")");

        } else if (msg instanceof MoveCommand) {
            Player p = gameState.getPlayer(playerId);
            if (p == null || gameState.getCurrentPhase() != GameState.Phase.PLAYING) return;

            MoveCommand mc = (MoveCommand) msg;
            int dx = mc.getDx();
            int dy = mc.getDy();

            // Slow zombies in the safe zone
            if (p.isInfected()) {
                int cx = p.getX() + Player.SIZE / 2;
                int cy = p.getY() + Player.SIZE / 2;
                if (gameState.isSafeZoneAt(cx, cy)) {
                    dx = dx / 2;
                    dy = dy / 2;
                }
            }

            int newX = Math.max(0, Math.min(GameState.CANVAS_W - Player.SIZE, p.getX() + dx));
            int newY = Math.max(0, Math.min(GameState.CANVAS_H - Player.SIZE, p.getY() + dy));

            // Wall collision: test all 4 corners
            if (!wallHit(newX, newY)) {
                p.setX(newX);
                p.setY(newY);
            } else {
                // Try sliding: move only along X
                if (!wallHit(newX, p.getY())) {
                    p.setX(newX);
                } else if (!wallHit(p.getX(), newY)) {
                    p.setY(newY);
                }
            }

        } else if (msg instanceof ChatMessage) {
            ChatMessage cm = (ChatMessage) msg;
            String line = "[" + playerName + "]: " + cm.getMessage();
            gameState.addChatMessage(line);
            server.broadcastToAll(new ChatBroadcast(line));
        }
    }

    private boolean wallHit(int x, int y) {
        int s = Player.SIZE - 1;
        return gameState.isWallAt(x,     y    )
            || gameState.isWallAt(x + s, y    )
            || gameState.isWallAt(x,     y + s)
            || gameState.isWallAt(x + s, y + s);
    }

    public void sendMessage(NetworkMessage msg) {
        try {
            synchronized (out) {
                out.reset();
                out.writeObject(msg);
                out.flush();
            }
        } catch (IOException e) {
            // client likely disconnected
        }
    }
}
