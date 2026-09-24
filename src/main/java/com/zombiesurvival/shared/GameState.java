package com.zombiesurvival.shared;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Authoritative game state broadcast to all clients every tick.
 *
 * Map: 80 cols × 60 rows  →  2560 × 1920 px  (tile = 32 px)
 * Tile: 0=Grass  1=Wall  2=Safe Zone
 *
 * Zones:
 *  Urban    — top-left  (rows  1-28, cols  1-38)
 *  Hospital — top-right (rows  1-28, cols 41-78)
 *  Forest   — bot-left  (rows 31-58, cols  1-38)
 *  Factory  — bot-right (rows 31-58, cols 41-78)
 */
public class GameState implements Serializable {
    private static final long serialVersionUID = 7L;

    // ── Map constants ─────────────────────────────────────────────────────────
    public static final int TILE_SIZE  = 32;
    public static final int MAP_WIDTH  = 80;
    public static final int MAP_HEIGHT = 60;
    public static final int CANVAS_W   = MAP_WIDTH  * TILE_SIZE;  // 2560
    public static final int CANVAS_H   = MAP_HEIGHT * TILE_SIZE;  // 1920
    public static final int VIEWPORT_W = 960;
    public static final int VIEWPORT_H = 704;

    // ── Tile map ──────────────────────────────────────────────────────────────
    public static final int[][] MAP = new int[MAP_HEIGHT][MAP_WIDTH];

    static { buildMap(); }

    private static void buildMap() {
        // --- 0: fill all grass ---
        for (int[] row : MAP) Arrays.fill(row, 0);

        // --- Border walls ---
        for (int c = 0; c < MAP_WIDTH;  c++) { MAP[0][c] = 1; MAP[MAP_HEIGHT-1][c] = 1; }
        for (int r = 0; r < MAP_HEIGHT; r++) { MAP[r][0] = 1; MAP[r][MAP_WIDTH-1]  = 1; }

        // ═══════════════════════════════════════════
        // URBAN ZONE (rows 1-28, cols 1-38)
        // ═══════════════════════════════════════════
        addBuilding( 2,  2, 10,  7);   // A – corner block
        addBuilding(15,  2,  9,  6);   // B – top centre
        addBuilding(27,  2,  9,  5);   // C – top right urban
        addBuilding( 2, 12,  7,  8);   // D – left mid
        addBuilding(12, 13, 12,  7);   // E – centre large
        addBuilding(27, 11,  9,  7);   // F – right mid
        addBuilding( 2, 23,  9,  5);   // G – bottom left
        addBuilding(14, 23, 12,  5);   // H – bottom centre
        addBuilding(28, 22,  8,  6);   // I – bottom right
        // Urban safe zone (abandoned clinic)
        fillSafe( 7,  5,  3,  2);

        // ═══════════════════════════════════════════
        // HOSPITAL ZONE (rows 1-28, cols 41-78)
        // ═══════════════════════════════════════════
        addBuilding(41,  1, 38, 27);   // hospital outer wall
        // Interior cross-walls
        addBuilding(43,  3, 10,  7);   // ward NW
        addBuilding(56,  3, 10,  7);   // ward NE
        addBuilding(68,  3,  9,  7);   // ward far-NE
        addBuilding(43, 14, 10,  7);   // ward SW
        addBuilding(56, 14, 10,  7);   // ward SE
        addBuilding(68, 14,  9,  7);   // ward far-SE
        // Hospital safe zones (wards)
        fillSafe(44,  4,  8,  5);
        fillSafe(57,  4,  8,  5);
        fillSafe(44, 15,  8,  5);
        fillSafe(57, 15,  8,  5);
        // Open lower area
        addBuilding(43, 23,  8,  4);
        addBuilding(55, 23, 10,  4);
        addBuilding(68, 23,  9,  4);
        fillSafe(44, 24,  5,  2);

        // ═══════════════════════════════════════════
        // FOREST ZONE (rows 31-58, cols 1-38)
        // ═══════════════════════════════════════════
        // Scattered tree-cluster walls
        addSmallCluster( 8, 32);
        addSmallCluster(20, 36);
        addSmallCluster( 5, 43);
        addSmallCluster(30, 41);
        addSmallCluster(15, 50);
        addSmallCluster(28, 54);
        addSmallCluster( 9, 57);
        // Forest safe zones (clearings)
        fillSafe( 4, 35,  6,  4);
        fillSafe(24, 48,  6,  4);

        // ═══════════════════════════════════════════
        // FACTORY / BUNKER ZONE (rows 31-58, cols 41-78)
        // ═══════════════════════════════════════════
        addBuilding(41, 31, 38, 28);   // factory outer wall
        // Interior machinery
        addBuilding(43, 33,  9,  6);
        addBuilding(55, 33, 10,  6);
        addBuilding(67, 33, 10,  6);
        addBuilding(43, 43, 13,  8);
        addBuilding(59, 43, 17,  8);
        // Bunker (final safe zone, inner hollow)
        addBuilding(49, 50, 14,  7);   // bunker walls
        fillSafe(50, 51, 12,  5);      // bunker interior safe

        // ═══════════════════════════════════════════
        // ZONE CONNECTORS (corridors) — clear out walls
        // ═══════════════════════════════════════════
        // Horizontal highway: rows 29-30
        for (int r = 29; r <= 30; r++)
            for (int c = 1; c < MAP_WIDTH-1; c++)
                if (MAP[r][c] == 1) MAP[r][c] = 0;

        // Vertical highway: cols 39-40
        for (int r = 1; r < MAP_HEIGHT-1; r++) {
            MAP[r][39] = 0;
            MAP[r][40] = 0;
        }

        // Clear spawn platform areas
        clearArea(14, 14, 4);   // urban spawn zone
        clearArea(60,  5, 4);   // hospital spawn zone
        clearArea(15, 42, 4);   // forest spawn zone
        clearArea(60, 42, 4);   // factory spawn zone
        clearArea(39, 29, 5);   // crossroads
    }

    /** Hollow rectangle with door gaps on each side. */
    private static void addBuilding(int col, int row, int w, int h) {
        for (int c = col; c < col+w && c < MAP_WIDTH;  c++) {
            if (row >= 0 && row < MAP_HEIGHT)       MAP[row][c]     = 1;
            if (row+h-1 >= 0 && row+h-1 < MAP_HEIGHT) MAP[row+h-1][c] = 1;
        }
        for (int r = row; r < row+h && r < MAP_HEIGHT; r++) {
            if (col >= 0 && col < MAP_WIDTH)        MAP[r][col]     = 1;
            if (col+w-1 >= 0 && col+w-1 < MAP_WIDTH) MAP[r][col+w-1] = 1;
        }
        // Door gaps
        int mc = col + w/2, mr = row + h/2;
        if (row+h-1 < MAP_HEIGHT)   MAP[row+h-1][mc] = 0;
        if (row >= 0)               MAP[row][mc]      = 0;
        if (col+w-1 < MAP_WIDTH)    MAP[mr][col+w-1]  = 0;
        if (col >= 0)               MAP[mr][col]       = 0;
    }

    /** 2×2 solid tree cluster. */
    private static void addSmallCluster(int col, int row) {
        for (int r = row; r <= row+1 && r < MAP_HEIGHT-1; r++)
            for (int c = col; c <= col+1 && c < MAP_WIDTH-1; c++)
                MAP[r][c] = 1;
    }

    /** Fill area with safe zone (only over existing grass). */
    private static void fillSafe(int col, int row, int w, int h) {
        for (int r = row; r < row+h && r < MAP_HEIGHT; r++)
            for (int c = col; c < col+w && c < MAP_WIDTH; c++)
                if (MAP[r][c] == 0) MAP[r][c] = 2;
    }

    /** Clear an area to grass (e.g., spawn points). */
    private static void clearArea(int col, int row, int radius) {
        for (int r = row-radius; r <= row+radius; r++)
            for (int c = col-radius; c <= col+radius; c++)
                if (r >= 1 && r < MAP_HEIGHT-1 && c >= 1 && c < MAP_WIDTH-1)
                    MAP[r][c] = 0;
    }

    // ── Static helpers ────────────────────────────────────────────────────────

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

    // ── Phase ─────────────────────────────────────────────────────────────────
    public enum Phase { WAITING, PLAYING, GAME_OVER }

    // ── State fields ──────────────────────────────────────────────────────────
    private Phase  currentPhase      = Phase.WAITING;
    private int    timeRemaining      = 180;   // 3 minutes
    private String winnerMessage      = "";
    private int    gameOverCountdown  = 10;

    // Players
    private final Map<String, Player>   players   = new ConcurrentHashMap<>();
    // Pickups
    private final List<Pickup>          pickups   = new CopyOnWriteArrayList<>();
    // Bullets (server-side projectile objects)
    private final List<Bullet>          bullets   = new CopyOnWriteArrayList<>();
    // NPC zombies
    private final Map<String, NpcZombie> npcZombies = new ConcurrentHashMap<>();
    // Kill feed (last 8 entries)
    private final List<KillFeedEntry>   killFeed  = new CopyOnWriteArrayList<>();

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Phase  getCurrentPhase()              { return currentPhase; }
    public void   setCurrentPhase(Phase p)       { this.currentPhase = p; }

    public int    getTimeRemaining()             { return timeRemaining; }
    public void   setTimeRemaining(int t)        { this.timeRemaining = t; }

    public String getWinnerMessage()             { return winnerMessage; }
    public void   setWinnerMessage(String m)     { this.winnerMessage = m; }

    public int    getGameOverCountdown()         { return gameOverCountdown; }
    public void   setGameOverCountdown(int c)    { this.gameOverCountdown = c; }

    public Map<String, Player>   getPlayers()    { return players; }
    public Player                getPlayer(String id) { return id != null ? players.get(id) : null; }

    public List<Pickup>          getPickups()    { return pickups; }
    public List<Bullet>          getBullets()    { return bullets; }
    public Map<String, NpcZombie> getNpcZombies(){ return npcZombies; }
    public List<KillFeedEntry>   getKillFeed()   { return killFeed; }

    /** Add a kill-feed entry, keeping at most 8. */
    public void addKillFeedEntry(KillFeedEntry e) {
        killFeed.add(e);
        while (killFeed.size() > 8) killFeed.remove(0);
    }
}
