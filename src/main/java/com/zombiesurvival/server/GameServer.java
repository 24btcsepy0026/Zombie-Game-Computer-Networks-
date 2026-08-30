package com.zombiesurvival.server;

import com.zombiesurvival.shared.*;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class GameServer {

    public static final int PORT = 8080;

    private final GameState gameState = new GameState();
    private final CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final GameEngine gameEngine;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final AtomicLong playerCounter = new AtomicLong(1);

    public GameServer() {
        // GameEngine(server, state)
        this.gameEngine = new GameEngine(this, gameState);
    }

    public void start() {
        System.out.println("=== Zombie Survival Server ===");
        System.out.println("Listening on port " + PORT + "...");
        new Thread(gameEngine, "GameEngine").start();

        try (ServerSocket ss = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = ss.accept();
                System.out.println("[+] Client connected: " + socket.getInetAddress());
                String playerId = "p" + playerCounter.getAndIncrement();
                ClientHandler handler = new ClientHandler(socket, this, gameState, playerId);
                if (!handler.setupStreams()) { continue; }
                clients.add(handler);
                pool.execute(handler);
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    /** Broadcast full game state snapshot to all clients. */
    public void broadcastState(GameState state) {
        GameStateUpdate upd = new GameStateUpdate(state);
        for (ClientHandler c : clients) c.send(upd);
    }

    /** Broadcast a chat line to all clients. */
    public void broadcastChat(String text, String senderId) {
        Player sender = gameState.getPlayer(senderId);
        String name   = (sender != null) ? sender.getName() : senderId;
        ChatBroadcast cb = new ChatBroadcast("[" + name + "] " + text);
        for (ClientHandler c : clients) c.send(cb);
    }

    /** Called by ClientHandler when its socket closes. */
    public void removeClient(ClientHandler handler) {
        clients.remove(handler);
        System.out.println("[-] Client removed. Active: " + clients.size());
    }

    public CopyOnWriteArrayList<ClientHandler> getClients() { return clients; }

    public static void main(String[] args) {
        new GameServer().start();
    }
}
