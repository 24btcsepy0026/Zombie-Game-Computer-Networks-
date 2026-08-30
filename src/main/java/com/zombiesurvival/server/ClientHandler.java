package com.zombiesurvival.server;

import com.zombiesurvival.shared.*;

import java.io.*;
import java.net.Socket;

/**
 * Handles one connected client: reads incoming messages and applies movement.
 * Sprint multiplier: 1.8× speed when sprinting with stamina remaining.
 * Safe-zone zombie penalty: 60 % reduced speed.
 */
public class ClientHandler implements Runnable {

    private static final int BASE_SPEED     = 5;
    private static final int SPRINT_PCTX10  = 18; // 1.8×  → multiply by 18, divide by 10
    private static final int BOOST_PCTX10   = 15; // 1.5× speed boost
    private static final int ZOMBIE_SAFE_PCT = 40; // 40 % of normal in safe zone
    private static final int BOUNDS_PAD     = 2;

    private final Socket             socket;
    private final GameServer         server;
    private final GameState          state;
    private final String             playerId;
    private ObjectOutputStream       out;
    private ObjectInputStream        in;

    public ClientHandler(Socket socket, GameServer server, GameState state, String playerId) {
        this.socket   = socket;
        this.server   = server;
        this.state    = state;
        this.playerId = playerId;
    }

    // ── Connection setup ──────────────────────────────────────────────────────

    public boolean setupStreams() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in  = new ObjectInputStream(socket.getInputStream());
            return true;
        } catch (IOException e) {
            System.err.println("[ClientHandler] Stream setup failed: " + e.getMessage());
            return false;
        }
    }

    public void send(Object msg) {
        if (out == null) return;
        try {
            out.writeObject(msg);
            out.flush();
            out.reset();
        } catch (IOException ignored) {}
    }

    // ── Main receive loop ─────────────────────────────────────────────────────

    @Override
    public void run() {
        try {
            // Wait for JoinRequest
            Object first = in.readObject();
            if (first instanceof JoinRequest jr) {
                Player p = new Player(playerId, jr.getPlayerName(),
                                      GameState.TILE_SIZE * 2,
                                      GameState.TILE_SIZE * 2,
                                      Player.Role.SURVIVOR);
                state.getPlayers().put(playerId, p);
                send(new AssignIdMessage(playerId));
                System.out.println("[ClientHandler] " + jr.getPlayerName() + " joined as " + playerId);
            }

            // Main message loop
            while (!socket.isClosed()) {
                Object msg = in.readObject();
                if      (msg instanceof MoveCommand mc) handleMove(mc);
                else if (msg instanceof ChatMessage  cm) server.broadcastChat(cm.getMessage(), playerId);
            }
        } catch (EOFException | java.net.SocketException ignored) {
            // Client disconnected
        } catch (Exception e) {
            System.err.println("[ClientHandler] Error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    // ── Movement handler ──────────────────────────────────────────────────────

    private void handleMove(MoveCommand mc) {
        if (state.getCurrentPhase() != GameState.Phase.PLAYING) return;
        Player p = state.getPlayer(playerId);
        if (p == null || p.getHealth() <= 0) return;

        int dx = mc.getDx();
        int dy = mc.getDy();
        if (dx == 0 && dy == 0) { p.regenStamina(1); return; }

        // ── Determine speed multiplier ────────────────────────────────────────
        int cx = p.getX() + Player.SIZE / 2;
        int cy = p.getY() + Player.SIZE / 2;
        boolean inSafe  = GameState.isSafeZoneAt(cx, cy);
        boolean zombie  = p.isInfected();
        boolean sprint  = mc.isSprinting() && !zombie && p.getStamina() > 0;
        boolean boosted = p.hasSpeedBoost();

        // Stamina drain/regen
        if (sprint) {
            p.drainStamina(2);
        } else if (!mc.isSprinting()) {
            p.regenStamina(1);
        }

        // Scale dx/dy: base=5, sprint=9, speedboost=7.5, zombie-in-safe=2
        int pct = 10; // default 1.0× (multiply by pct, divide by 10)
        if (zombie && inSafe) {
            pct = 4;  // 0.4× — greatly slowed in safe zone
        } else if (sprint && boosted) {
            pct = 20; // 2.0× — sprint + boost
        } else if (sprint) {
            pct = SPRINT_PCTX10; // 1.8×
        } else if (boosted) {
            pct = BOOST_PCTX10;  // 1.5×
        }

        int scaledDx = dx * pct / 10;
        int scaledDy = dy * pct / 10;

        // Ensure at least 1-pixel movement in intended direction if non-zero
        if (dx != 0 && scaledDx == 0) scaledDx = dx > 0 ? 1 : -1;
        if (dy != 0 && scaledDy == 0) scaledDy = dy > 0 ? 1 : -1;

        // ── Collision-aware movement ───────────────────────────────────────────
        int newX = clampAndCollide(p.getX(), p.getY(), scaledDx, 0, true);
        int newY = clampAndCollide(newX,     p.getY(), 0, scaledDy, false);

        p.setX(newX);
        p.setY(newY);
    }

    /**
     * Moves along one axis, stopping before walls.
     * Returns the new x (if horizontal) or y (if vertical).
     */
    private int clampAndCollide(int px, int py, int dx, int dy, boolean horizontal) {
        int nx = px + dx, ny = py + dy;

        // Canvas bounds
        nx = Math.max(BOUNDS_PAD, Math.min(GameState.CANVAS_W - Player.SIZE - BOUNDS_PAD, nx));
        ny = Math.max(BOUNDS_PAD, Math.min(GameState.CANVAS_H - Player.SIZE - BOUNDS_PAD, ny));

        // Wall check (all 4 corners of the player bounding box)
        int s = Player.SIZE;
        boolean blocked =
            GameState.isWallAt(nx,         ny        ) ||
            GameState.isWallAt(nx + s - 1, ny        ) ||
            GameState.isWallAt(nx,         ny + s - 1) ||
            GameState.isWallAt(nx + s - 1, ny + s - 1);

        if (blocked) return horizontal ? px : py;
        return horizontal ? nx : ny;
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    private void cleanup() {
        Player p = state.getPlayers().remove(playerId);
        System.out.println("[ClientHandler] " + (p != null ? p.getName() : playerId) + " disconnected.");
        server.removeClient(this);
        try { socket.close(); } catch (IOException ignored) {}
    }
}
