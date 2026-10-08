package com.zombiesurvival;

import com.zombiesurvival.client.GameClient;
import com.zombiesurvival.server.GameServer;

import javax.swing.*;

/**
 * Unified launcher - starts both server and client in one application.
 * Perfect for single-player mode!
 */
public class ZombieGame {
    
    public static void main(String[] args) {
        // Start server in background thread
        Thread serverThread = new Thread(() -> {
            System.out.println("Starting integrated server...");
            GameServer server = new GameServer();
            server.start();
        }, "IntegratedServer");
        serverThread.setDaemon(true); // Server stops when client exits
        serverThread.start();
        
        // Wait a moment for server to start
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // Start client on main thread (GUI needs main thread)
        SwingUtilities.invokeLater(() -> {
            System.out.println("Starting client...");
            GameClient.main(new String[]{});
        });
    }
}
