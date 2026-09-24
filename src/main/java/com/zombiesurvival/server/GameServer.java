package com.zombiesurvival.server;

import com.zombiesurvival.shared.*;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.util.concurrent.*;

public class GameServer {

    public static final int PORT = 8080;

    private final GameState gameState = new GameState();
    private final CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final GameEngine gameEngine;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public GameServer() {
        this.gameEngine = new GameEngine(gameState, this);
    }

    public void start() {
        System.out.println("=== Zombie Survival Server ===");
        System.out.println("Listening on port " + PORT + "...");
        new Thread(gameEngine, "GameEngine").start();

        try (ServerSocket ss = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = ss.accept();
                System.out.println("[+] Client connected: " + socket.getInetAddress());
                ClientHandler handler = new ClientHandler(socket, gameState, this);
                clients.add(handler);
                pool.execute(handler);
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    /** Broadcast a message to every connected client. */
    public void broadcastToAll(NetworkMessage msg) {
        for (ClientHandler c : clients) c.sendMessage(msg);
    }
    
    /** Force the game to start immediately. */
    public void startGameNow() {
        gameEngine.forceStartGame();
    }

    /** Called when a client disconnects. */
    public void removeClient(ClientHandler handler) {
        clients.remove(handler);
        if (handler.getPlayerId() != null) {
            Player p = gameState.getPlayer(handler.getPlayerId());
            String name = (p != null) ? p.getName() : "Unknown";
            gameState.removePlayer(handler.getPlayerId());
            gameState.addChatMessage("** " + name + " disconnected **");
            System.out.println("[-] " + name + " disconnected. Players: " + clients.size());
        }
    }

    public CopyOnWriteArrayList<ClientHandler> getClients() { return clients; }

    public static void main(String[] args) {
        new GameServer().start();
    }
}
