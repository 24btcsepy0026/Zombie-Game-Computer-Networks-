package com.zombiesurvival.shared;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Authoritative game state broadcast to all clients each tick.
 *
 * Map: 30 cols × 22 rows  →  960 × 704 px
 * Tile: 0=GRASS  1=WALL  2=SAFE_ZONE
 */
public class GameState implements Serializable {
    private static final long serialVersionUID = 5L;

    // ── Map constants ────────────────────────────────────────────────────────
    public static final int TILE_SIZE  = 32;
    public static final int MAP_WIDTH  = 30;
    public static final int MAP_HEIGHT = 22;
    public static final int CANVAS_W   = MAP_WIDTH  * TILE_SIZE; // 960
    public static final int CANVAS_H   = MAP_HEIGHT * TILE_SIZE; // 704

    /**
     * 30 × 22 tile map.
     * Layout: outer wall border, 4 safe-zone clusters, 2 mirrored central "rooms",
     * corner wall clusters, and plenty of open space for chasing/fleeing.
     *
     * 0 = Grass (walkable open ground)
     * 1 = Wall  (solid blocker — has 3D depth faces in client renderer)
     * 2 = Safe Zone (heals survivors, slows zombies)
     */
    public static final int[][] MAP = {
        // col:  0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29
        {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1}, // row  0
        {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1}, // row  1
        {1,0,0,1,1,0,0,0,0,0,0,1,1,1,0,0,0,1,1,1,0,0,0,0,0,1,1,0,0,1}, // row  2
        {1,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,1}, // row  3
        {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1}, // row  4
        {1,0,0,0,0,2,2,2,2,0,0,0,0,0,0,0,0,0,0,0,0,2,2,2,2,0,0,0,0,1}, // row  5
        {1,0,0,0,0,2,2,2,2,0,0,0,0,0,0,0,0,0,0,0,0,2,2,2,2,0,0,0,0,1}, // row  6
        {1,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,1}, // row  7
        {1,0,0,1,1,0,0,0,0,0,0,0,1,0,0,0,0,1,0,0,0,0,0,0,1,1,0,0,0,1}, // row  8
        {1,0,0,1,1,0,0,0,0,0,0,0,1,0,0,0,0,1,0,0,0,0,0,0,1,1,0,0,0,1}, // row  9
        {1,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,1}, // row 10
        {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1}, // row 11  (center open)
        {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1}, // row 12  (center open)
        {1,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,1}, // row 13
        {1,0,0,1,1,0,0,0,0,0,0,0,1,0,0,0,0,1,0,0,0,0,0,0,1,1,0,0,0,1}, // row 14
        {1,0,0,1,1,0,0,0,0,0,0,0,1,0,0,0,0,1,0,0,0,0,0,0,1,1,0,0,0,1}, // row 15
        {1,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,1}, // row 16
        {1,0,0,0,0,2,2,2,2,0,0,0,0,0,0,0,0,0,0,0,0,2,2,2,2,0,0,0,0,1}, // row 17
        {1,0,0,0,0,2,2,2,2,0,0,0,0,0,0,0,0,0,0,0,0,2,2,2,2,0,0,0,0,1}, // row 18
        {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1}, // row 19
        {1,0,0,1,1,0,0,0,0,0,0,1,1,1,0,0,0,1,1,1,0,0,0,0,0,1,1,0,0,1}, // row 20
        {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1}, // row 21
    };

    // ── Phase ────────────────────────────────────────────────────────────────
    public enum Phase { WAITING, PLAYING, GAME_OVER }

    // ── State fields ─────────────────────────────────────────────────────────
    private Phase   currentPhase    = Phase.WAITING;
    private int     timeRemaining   = 120;
    private String  winnerMessage   = "";
    private int     gameOverCountdown = 10;

    private final Map<String, Player>   players = new ConcurrentHashMap<>();
    private final List<Pickup>          pickups = new CopyOnWriteArrayList<>();

    // ── Helpers ───────────────────────────────────────────────────────────────

    public static boolean isWallAt(int px, int py) {
        int col = px / TILE_SIZE, row = py / TILE_SIZE;
        if (col < 0 || col >= MAP_WIDTH || row < 0 || row >= MAP_HEIGHT) return true;
        return MAP[row][col] == 1;
    }

    public static boolean isSafeZoneAt(int px, int py) {
        int col = px / TILE_SIZE, row = py / TILE_SIZE;
        if (col < 0 || col >= MAP_WIDTH || row < 0 || row >= MAP_HEIGHT) return false;
        return MAP[row][col] == 2;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Phase  getCurrentPhase()    { return currentPhase; }
    public void   setCurrentPhase(Phase p) { this.currentPhase = p; }

    public int    getTimeRemaining()   { return timeRemaining; }
    public void   setTimeRemaining(int t) { this.timeRemaining = t; }

    public String getWinnerMessage()   { return winnerMessage; }
    public void   setWinnerMessage(String m) { this.winnerMessage = m; }

    public int    getGameOverCountdown()        { return gameOverCountdown; }
    public void   setGameOverCountdown(int c)   { this.gameOverCountdown = c; }

    public Map<String, Player> getPlayers() { return players; }
    public Player getPlayer(String id)      { return id != null ? players.get(id) : null; }

    public List<Pickup> getPickups() { return pickups; }
}
