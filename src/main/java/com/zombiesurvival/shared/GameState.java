package com.zombiesurvival.shared;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;

/**
 * Full game state shared between server and all clients.
 * Contains the static tile map, all players, all pickups, chat log, timer, and phase.
 */
public class GameState implements Serializable {
    private static final long serialVersionUID = 2L;

    public enum Phase { WAITING, PLAYING, GAME_OVER }

    // ── Map constants ──────────────────────────────────────────────────────────
    public static final int TILE_SIZE  = 32;
    public static final int MAP_WIDTH  = 25;
    public static final int MAP_HEIGHT = 18;
    public static final int CANVAS_W   = MAP_WIDTH  * TILE_SIZE; // 800
    public static final int CANVAS_H   = MAP_HEIGHT * TILE_SIZE; // 576

    // 0 = GRASS  |  1 = WALL  |  2 = SAFE_ZONE
    public static final int[][] MAP = {
        {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1},
        {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
        {1,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,1},
        {1,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,1},
        {1,0,0,0,0,0,0,0,0,0,2,2,2,2,0,0,0,0,0,0,0,0,0,0,1},
        {1,0,0,0,0,0,0,0,0,0,2,2,2,2,0,0,0,0,0,0,0,0,0,0,1},
        {1,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,1},
        {1,0,0,1,1,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,0,0,0,0,1},
        {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,1},
        {1,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,1},
        {1,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
        {1,0,0,0,0,0,0,0,0,0,2,2,2,2,0,0,0,0,0,0,0,0,0,0,1},
        {1,0,0,0,0,0,0,0,0,0,2,2,2,2,0,0,0,0,0,0,0,0,0,0,1},
        {1,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,0,0,1},
        {1,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,0,0,1},
        {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
        {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
        {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1}
    };

    // ── Runtime state ──────────────────────────────────────────────────────────
    private Phase currentPhase = Phase.WAITING;
    private ConcurrentHashMap<String, Player> players = new ConcurrentHashMap<>();
    private List<Pickup> pickups = Collections.synchronizedList(new ArrayList<>());
    private List<String> chatLog = Collections.synchronizedList(new ArrayList<>());
    private int timeRemaining  = 120;
    private int gameOverCountdown = 10;
    private String winnerMessage = "";

    // ── Players ────────────────────────────────────────────────────────────────
    public Phase getCurrentPhase() { return currentPhase; }
    public void  setCurrentPhase(Phase p) { this.currentPhase = p; }

    public ConcurrentHashMap<String, Player> getPlayers() { return players; }
    public void   addPlayer(Player p)      { players.put(p.getId(), p); }
    public void   removePlayer(String id)  { players.remove(id); }
    public Player getPlayer(String id)     { return players.get(id); }

    // ── Pickups ────────────────────────────────────────────────────────────────
    public List<Pickup> getPickups()                  { return pickups; }
    public void         setPickups(List<Pickup> list) { this.pickups = list; }

    // ── Chat ──────────────────────────────────────────────────────────────────
    public List<String> getChatLog() { return chatLog; }
    public void addChatMessage(String msg) {
        chatLog.add(msg);
        if (chatLog.size() > 60) chatLog.remove(0);
    }

    // ── Timer ─────────────────────────────────────────────────────────────────
    public int  getTimeRemaining()   { return timeRemaining; }
    public void setTimeRemaining(int t) { this.timeRemaining = t; }
    public void decrementTime()      { if (timeRemaining > 0) timeRemaining--; }

    public int  getGameOverCountdown()    { return gameOverCountdown; }
    public void setGameOverCountdown(int v){ this.gameOverCountdown = v; }

    // ── Winner ────────────────────────────────────────────────────────────────
    public String getWinnerMessage()        { return winnerMessage; }
    public void   setWinnerMessage(String s){ this.winnerMessage = s; }

    // ── Map helpers ───────────────────────────────────────────────────────────
    public boolean isWallAt(int px, int py) {
        int tx = px / TILE_SIZE, ty = py / TILE_SIZE;
        if (tx < 0 || tx >= MAP_WIDTH || ty < 0 || ty >= MAP_HEIGHT) return true;
        return MAP[ty][tx] == 1;
    }

    public boolean isSafeZoneAt(int px, int py) {
        int tx = px / TILE_SIZE, ty = py / TILE_SIZE;
        if (tx < 0 || tx >= MAP_WIDTH || ty < 0 || ty >= MAP_HEIGHT) return false;
        return MAP[ty][tx] == 2;
    }
}
