package com.zombiesurvival.server;

import com.zombiesurvival.shared.*;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Core game loop running at ~30 TPS.
 * Handles: role assignment, collision, damage, healing, pickups,
 *          stamina regen, speed boost, timer, win/reset.
 */
public class GameEngine implements Runnable {

    private static final int TPS              = 30;
    private static final int TICK_MS          = 1000 / TPS;
    private static final int GAME_DURATION    = 120;   // seconds
    private static final int RESET_COUNTDOWN  = 10;    // seconds after game over

    // Damage & healing
    private static final int DAMAGE_PER_TICK  = 2;     // zombie drains 2 HP/tick from adjacent survivor
    private static final int SAFE_HEAL_PER_TICK = 1;   // safe zone heals 1 HP/tick
    private static final int ZOMBIE_SLOW_FRAMES = 3;   // zombie in safe zone: skip 1 in 3 move frames

    // Pickup values
    private static final int MEDKIT_HP    = 40;
    private static final int MEDKIT_SCORE = 10;
    private static final int KEY_SCORE    = 50;
    private static final int ARMOR_AMT    = 40;
    private static final int ARMOR_SCORE  = 15;
    private static final int SPEED_SCORE  = 15;
    private static final int SPEED_TICKS  = 150;       // 5 seconds

    // Map of player → slow-frame counter for safe-zone zombie slowdown
    private final Map<String, Integer> slowFrames = new HashMap<>();

    // Spawn order: [col, row] — all verified grass tiles on 30×22 map
    private static final int[][] SPAWNS = {
        {15, 11}, // zombie (center)
        { 2,  2}, {27,  2}, { 2, 19}, {27, 19},
        { 2, 11}, {27, 11}, {15,  1}, {15, 20},
    };

    // Pickup spawn definitions: [col, row, type-ordinal]
    // Types: 0=MEDKIT 1=KEY 2=ARMOR 3=SPEED_BOOST
    private static final int[][] PICKUP_SPAWNS = {
        // Medkits
        { 4, 11, 0}, {25, 11, 0},   // center sides
        {14,  3, 0}, {14, 18, 0},   // top/bottom center
        { 1,  4, 0}, {28,  4, 0},   // top corners
        // Keys
        { 1,  1, 1}, {28,  1, 1},   // top corners
        { 1, 20, 1}, {28, 20, 1},   // bottom corners
        // Armor
        {10,  8, 2}, {18, 14, 2},   // near room entrances
        // Speed boost
        {10,  2, 3}, {10, 19, 3},   // spread around map
    };

    private final GameServer server;
    private final GameState  state;
    private       int        tickCount = 0;
    private       int        gameOverTicks = 0;
    private       boolean    gameStartSoundPending = false;

    public GameEngine(GameServer server, GameState state) {
        this.server = server;
        this.state  = state;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            long start = System.currentTimeMillis();
            tick();
            long elapsed = System.currentTimeMillis() - start;
            long sleep   = TICK_MS - elapsed;
            if (sleep > 0) {
                try { Thread.sleep(sleep); } catch (InterruptedException e) { break; }
            }
        }
    }

    // ── Main tick ─────────────────────────────────────────────────────────────

    private void tick() {
        tickCount++;
        switch (state.getCurrentPhase()) {
            case WAITING   -> tickWaiting();
            case PLAYING   -> tickPlaying();
            case GAME_OVER -> tickGameOver();
        }
        server.broadcastState(state);
    }

    // ── Phase: WAITING ────────────────────────────────────────────────────────

    private void tickWaiting() {
        if (state.getPlayers().size() >= 2) startGame();
    }

    private void startGame() {
        List<Player> players = new ArrayList<>(state.getPlayers().values());
        Collections.shuffle(players);

        // Assign zombie to first player, rest are survivors
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            int[] sp = SPAWNS[Math.min(i, SPAWNS.length - 1)];
            p.setX(sp[0] * GameState.TILE_SIZE + 2);
            p.setY(sp[1] * GameState.TILE_SIZE + 2);
            p.setHealth(100);
            p.setStamina(100);
            p.setArmor(0);
            p.setSpeedBoost(0);
            p.setScore(0);
            if (i == 0) {
                p.setRole(Player.Role.ZOMBIE);
                p.setInfected(true);
            } else {
                p.setRole(Player.Role.SURVIVOR);
                p.setInfected(false);
            }
        }

        spawnPickups();
        state.setTimeRemaining(GAME_DURATION);
        state.setCurrentPhase(GameState.Phase.PLAYING);
        gameStartSoundPending = true;
        System.out.println("[GameEngine] Game started! " + players.size() + " players.");
    }

    // ── Phase: PLAYING ────────────────────────────────────────────────────────

    private void tickPlaying() {
        // Countdown timer (every TPS ticks = 1 second)
        if (tickCount % TPS == 0 && state.getTimeRemaining() > 0) {
            state.setTimeRemaining(state.getTimeRemaining() - 1);
        }

        for (Player p : state.getPlayers().values()) {
            tickSpeedBoost(p);
            tickSafeZone(p);
            tickZombieContact(p);
            tickPickupCollection(p);
        }

        checkWinConditions();
    }

    /** Drain speed-boost ticks */
    private void tickSpeedBoost(Player p) {
        p.tickSpeedBoost();
    }

    /** Safe-zone effects: heal survivors, slow zombies */
    private void tickSafeZone(Player p) {
        int cx = p.getX() + Player.SIZE / 2;
        int cy = p.getY() + Player.SIZE / 2;
        if (!GameState.isSafeZoneAt(cx, cy)) return;

        if (!p.isInfected()) {
            // Heal survivors (once per second)
            if (tickCount % TPS == 0 && p.getHealth() < 100) {
                p.addHealth(SAFE_HEAL_PER_TICK);
            }
        }
        // Zombie slowdown tracked in ClientHandler via flag; safe zone flag is implicit
    }

    /** Zombie infects survivors it touches */
    private void tickZombieContact(Player zombie) {
        if (!zombie.isInfected()) return;

        for (Player survivor : state.getPlayers().values()) {
            if (survivor.isInfected()) continue;
            if (overlaps(zombie, survivor)) {
                // Armor absorbs 50 % of damage
                int dmg = DAMAGE_PER_TICK;
                if (survivor.getArmor() > 0) {
                    dmg = Math.max(1, DAMAGE_PER_TICK / 2);
                    survivor.drainArmor(1);
                }
                survivor.addHealth(-dmg);

                if (survivor.getHealth() <= 0) {
                    // Infect!
                    survivor.setRole(Player.Role.ZOMBIE);
                    survivor.setInfected(true);
                    survivor.setHealth(60);
                    survivor.setArmor(0);
                    survivor.setStamina(80);
                    zombie.addScore(30);
                    System.out.println("[GameEngine] " + survivor.getName() + " infected by " + zombie.getName());
                }
            }
        }
    }

    private boolean overlaps(Player a, Player b) {
        int ax1 = a.getX(), ay1 = a.getY(), ax2 = ax1 + Player.SIZE, ay2 = ay1 + Player.SIZE;
        int bx1 = b.getX(), by1 = b.getY(), bx2 = bx1 + Player.SIZE, by2 = by1 + Player.SIZE;
        return ax1 < bx2 && ax2 > bx1 && ay1 < by2 && ay2 > by1;
    }

    /** Check pickup collisions for a player */
    private void tickPickupCollection(Player p) {
        for (Pickup pk : state.getPickups()) {
            if (pk.isCollected()) continue;
            if (!aabbOverlaps(p.getX(), p.getY(), Player.SIZE, Player.SIZE,
                              pk.getX(), pk.getY(), Pickup.SIZE, Pickup.SIZE)) continue;
            pk.setCollected(true);
            applyPickup(p, pk);
        }
    }

    private boolean aabbOverlaps(int ax, int ay, int aw, int ah,
                                  int bx, int by, int bw, int bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    private void applyPickup(Player p, Pickup pk) {
        switch (pk.getType()) {
            case MEDKIT      -> { p.addHealth(MEDKIT_HP);    p.addScore(MEDKIT_SCORE); }
            case KEY         -> { p.addScore(KEY_SCORE); }
            case ARMOR       -> { p.addArmor(ARMOR_AMT);     p.addScore(ARMOR_SCORE); }
            case SPEED_BOOST -> { p.setSpeedBoost(SPEED_TICKS); p.addScore(SPEED_SCORE); }
        }
        System.out.printf("[GameEngine] %s collected %s%n", p.getName(), pk.getType());
    }

    /** Check timer expiry and all-infected win conditions */
    private void checkWinConditions() {
        long survivorCount = state.getPlayers().values().stream().filter(p -> !p.isInfected()).count();
        long zombieCount   = state.getPlayers().values().stream().filter(Player::isInfected).count();

        if (survivorCount == 0 && zombieCount > 0) {
            endGame("☣  ZOMBIES WIN!");
        } else if (state.getTimeRemaining() <= 0 && survivorCount > 0) {
            endGame("🧍  SURVIVORS WIN!");
        }
    }

    // ── Phase: GAME_OVER ──────────────────────────────────────────────────────

    private void tickGameOver() {
        gameOverTicks++;
        if (gameOverTicks % TPS == 0) {
            int cd = state.getGameOverCountdown() - 1;
            state.setGameOverCountdown(Math.max(0, cd));
            if (cd <= 0) resetGame();
        }
    }

    private void endGame(String message) {
        state.setWinnerMessage(message);
        state.setCurrentPhase(GameState.Phase.GAME_OVER);
        state.setGameOverCountdown(RESET_COUNTDOWN);
        gameOverTicks = 0;
        System.out.println("[GameEngine] Game over — " + message);
    }

    private void resetGame() {
        for (Player p : state.getPlayers().values()) {
            p.setRole(Player.Role.SURVIVOR);
            p.setInfected(false);
            p.setHealth(100);
            p.setStamina(100);
            p.setArmor(0);
            p.setSpeedBoost(0);
            p.setScore(0);
        }
        state.getPickups().clear();
        slowFrames.clear();
        state.setTimeRemaining(GAME_DURATION);
        state.setWinnerMessage("");
        state.setGameOverCountdown(RESET_COUNTDOWN);
        state.setCurrentPhase(GameState.Phase.WAITING);
        tickCount = 0;
        System.out.println("[GameEngine] Game reset — waiting for players.");
    }

    // ── Pickup spawning ───────────────────────────────────────────────────────

    private void spawnPickups() {
        state.getPickups().clear();
        Pickup.Type[] types = Pickup.Type.values();
        for (int i = 0; i < PICKUP_SPAWNS.length; i++) {
            int[] s = PICKUP_SPAWNS[i];
            Pickup.Type t = types[s[2]];
            int px = s[0] * GameState.TILE_SIZE + (GameState.TILE_SIZE - Pickup.SIZE) / 2;
            int py = s[1] * GameState.TILE_SIZE + (GameState.TILE_SIZE - Pickup.SIZE) / 2;
            state.getPickups().add(new Pickup("pk" + i, t, px, py));
        }
        System.out.println("[GameEngine] Spawned " + state.getPickups().size() + " pickups.");
    }
}
