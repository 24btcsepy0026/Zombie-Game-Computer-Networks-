package com.zombiesurvival.server;

import com.zombiesurvival.shared.*;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Core game loop running at ~30 FPS on a dedicated thread.
 * Responsible for: role assignment, collision detection, health/damage,
 * pickup spawning & collection, scoring, and broadcasting state.
 */
public class GameEngine implements Runnable {

    private static final int  TPS          = 30;
    private static final long TICK_MS      = 1000 / TPS;
    private static final int  DAMAGE_PER_TICK   = 1;   // HP lost per tick while in zombie contact
    private static final int  HEAL_TICKS        = 30;  // ticks between safe-zone heals (+1 HP each)
    private static final int  SCORE_TICKS       = 30;  // ticks between survivor score awards (+1)
    private static final int  GAME_DURATION_S   = 120;
    private static final int  RESET_DELAY_S     = 10;

    // Player spawn tiles (tx, ty) – index 0 = zombie center, rest = survivor corners
    private static final int[][] SPAWNS = {
        {12, 9}, // zombie center
        {2, 2}, {21, 2}, {2, 14}, {21, 14},
        {2, 8},  {22, 8}, {12, 1}, {12, 16}
    };

    private final GameState gameState;
    private final GameServer server;
    private int  tickCount    = 0;
    private long gameOverTime = 0;
    private boolean running   = true;

    public GameEngine(GameState gameState, GameServer server) {
        this.gameState = gameState;
        this.server    = server;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void run() {
        while (running) {
            long start = System.currentTimeMillis();
            tick();
            long elapsed = System.currentTimeMillis() - start;
            long sleep   = TICK_MS - elapsed;
            if (sleep > 0) {
                try { Thread.sleep(sleep); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void tick() {
        tickCount++;
        switch (gameState.getCurrentPhase()) {
            case WAITING:   tickWaiting();  break;
            case PLAYING:   tickPlaying();  break;
            case GAME_OVER: tickGameOver(); break;
        }
        // Broadcast updated state to all clients
        server.broadcastToAll(new GameStateUpdate(gameState));
    }

    // ── Waiting phase ─────────────────────────────────────────────────────────

    private void tickWaiting() {
        // Stays in lobby phase until host triggers forceStartGame via StartGameMessage
    }
    
    /** Public method to force game start immediately (called from server). */
    public void forceStartGame() {
        if (gameState.getCurrentPhase() == GameState.Phase.WAITING) {
            startGame();
        }
    }

    private void startGame() {
        List<Player> players = new ArrayList<>(gameState.getPlayers().values());
        if (players.isEmpty()) return;

        // Reset state
        tickCount = 0;
        gameState.setTimeRemaining(GAME_DURATION_S);
        gameState.setWinnerMessage("");
        gameState.setGameOverCountdown(RESET_DELAY_S);
        spawnPickups();

        Random rand = new Random();

        if (gameState.getGameMode() == GameState.GameMode.MULTIPLAYER && players.size() >= 2) {
            // ── MULTIPLAYER: one real player becomes the zombie ────────────────
            // Pick a random zombie from the real players
            int zombieIdx = rand.nextInt(players.size());

            for (int i = 0; i < players.size(); i++) {
                Player p = players.get(i);
                p.setHealth(100);
                p.setScore(0);
                if (i == zombieIdx) {
                    p.setRole(Player.Role.ZOMBIE);
                    p.setInfected(true);
                    int[] sp = SPAWNS[0];
                    p.setX(sp[0] * GameState.TILE_SIZE + 2);
                    p.setY(sp[1] * GameState.TILE_SIZE + 2);
                } else {
                    p.setRole(Player.Role.SURVIVOR);
                    p.setInfected(false);
                    int[] sp = SPAWNS[(i % (SPAWNS.length - 1)) + 1];
                    p.setX(sp[0] * GameState.TILE_SIZE + 2);
                    p.setY(sp[1] * GameState.TILE_SIZE + 2);
                }
            }
            gameState.setCurrentPhase(GameState.Phase.PLAYING);
            String msg = "=== MULTIPLAYER GAME STARTED! One of you is the zombie! ===";
            gameState.addChatMessage(msg);
            server.broadcastToAll(new ChatBroadcast(msg));
            System.out.println(msg);

        } else {
            // ── SOLO (or fallback): all real players are survivors + AI zombies ─
            int spawnIdx = 0;
            for (Player p : players) {
                p.setHealth(100);
                p.setScore(0);
                p.setRole(Player.Role.SURVIVOR);
                p.setInfected(false);
                int[] sp = SPAWNS[(spawnIdx % (SPAWNS.length - 1)) + 1];
                p.setX(sp[0] * GameState.TILE_SIZE + 2);
                p.setY(sp[1] * GameState.TILE_SIZE + 2);
                spawnIdx++;
            }

            int numZombies = 3 + rand.nextInt(5); // 3–7 AI zombies
            for (int i = 0; i < numZombies; i++) {
                String zombieId = "zombie_" + uuid();
                int[] sp = SPAWNS[0];
                int zx = (sp[0] + rand.nextInt(3) - 1) * GameState.TILE_SIZE + rand.nextInt(20);
                int zy = (sp[1] + rand.nextInt(3) - 1) * GameState.TILE_SIZE + rand.nextInt(20);
                Player zombie = new Player(zombieId, "Zombie #" + (i + 1), zx, zy, Player.Role.ZOMBIE);
                zombie.setInfected(true);
                gameState.getPlayers().put(zombieId, zombie);
            }

            gameState.setCurrentPhase(GameState.Phase.PLAYING);
            String msg = "=== SURVIVAL MODE! Survive against " + numZombies + " AI zombies! ===";
            gameState.addChatMessage(msg);
            server.broadcastToAll(new ChatBroadcast(msg));
            System.out.println(msg);
        }
    }


    private void spawnPickups() {
        gameState.getPickups().clear();
        int ts = GameState.TILE_SIZE;
        int off = 5; // pixel offset from tile corner
        List<Pickup> pickups = new ArrayList<>();
        // Medkits
        pickups.add(new Pickup(uuid(), Pickup.Type.MEDKIT,  5 * ts + off,  1 * ts + off));
        pickups.add(new Pickup(uuid(), Pickup.Type.MEDKIT, 20 * ts + off,  1 * ts + off));
        pickups.add(new Pickup(uuid(), Pickup.Type.MEDKIT,  1 * ts + off,  8 * ts + off));
        pickups.add(new Pickup(uuid(), Pickup.Type.MEDKIT, 22 * ts + off,  8 * ts + off));
        pickups.add(new Pickup(uuid(), Pickup.Type.MEDKIT, 12 * ts + off,  7 * ts + off));
        pickups.add(new Pickup(uuid(), Pickup.Type.MEDKIT, 12 * ts + off, 10 * ts + off));
        // Keys
        pickups.add(new Pickup(uuid(), Pickup.Type.KEY,     5 * ts + off, 16 * ts + off));
        pickups.add(new Pickup(uuid(), Pickup.Type.KEY,    20 * ts + off, 16 * ts + off));
        pickups.add(new Pickup(uuid(), Pickup.Type.KEY,     1 * ts + off,  4 * ts + off));
        pickups.add(new Pickup(uuid(), Pickup.Type.KEY,    22 * ts + off,  4 * ts + off));
        gameState.setPickups(Collections.synchronizedList(pickups));
    }

    private String uuid() { return java.util.UUID.randomUUID().toString().substring(0, 8); }

    // ── Playing phase ─────────────────────────────────────────────────────────

    private long lastSecond = System.currentTimeMillis();

    private void tickPlaying() {
        List<Player> all       = new ArrayList<>(gameState.getPlayers().values());
        List<Player> zombies   = new ArrayList<>();
        List<Player> survivors = new ArrayList<>();

        for (Player p : all) {
            if (p.isInfected()) zombies.add(p);
            else                survivors.add(p);
        }

        // Timer countdown (once per second)
        long now = System.currentTimeMillis();
        if (now - lastSecond >= 1000) {
            gameState.decrementTime();
            lastSecond = now;
            // Award 1 pt per second to each living survivor
            for (Player s : survivors) s.addScore(1);
        }

        // AI: Move zombies towards nearest survivor
        moveAIZombies(zombies, survivors);

        // Zombie ↔ Survivor collision: deal damage
        for (Player z : zombies) {
            for (Player s : survivors) {
                if (overlaps(z, s)) {
                    s.addHealth(-DAMAGE_PER_TICK);
                    if (s.getHealth() <= 0) {
                        infectPlayer(s, z);
                    }
                }
            }
        }

        // Safe-zone healing for survivors
        if (tickCount % HEAL_TICKS == 0) {
            for (Player s : survivors) {
                int cx = s.getX() + Player.SIZE / 2;
                int cy = s.getY() + Player.SIZE / 2;
                if (gameState.isSafeZoneAt(cx, cy)) {
                    s.addHealth(2); // +2 HP per second in safe zone
                }
            }
        }

        // Pickup collection
        List<Pickup> pickups = gameState.getPickups();
        synchronized (pickups) {
            for (Pickup pk : pickups) {
                if (pk.isCollected()) continue;
                for (Player p : all) {
                    if (overlapsPickup(p, pk)) {
                        pk.setCollected(true);
                        applyPickup(p, pk);
                        break;
                    }
                }
            }
        }

        // Win condition check
        checkWinConditions(survivors, zombies);
    }

    private void moveAIZombies(List<Player> zombies, List<Player> survivors) {
        if (survivors.isEmpty()) return;
        
        for (Player zombie : zombies) {
            // Only move AI zombies (not player-controlled ones)
            if (!zombie.getId().startsWith("zombie_")) continue;
            
            // Find nearest survivor
            Player nearest = null;
            double minDist = Double.MAX_VALUE;
            
            for (Player survivor : survivors) {
                double dx = survivor.getX() - zombie.getX();
                double dy = survivor.getY() - zombie.getY();
                double dist = Math.sqrt(dx * dx + dy * dy);
                
                if (dist < minDist) {
                    minDist = dist;
                    nearest = survivor;
                }
            }
            
            if (nearest != null) {
                // Move towards nearest survivor
                int dx = nearest.getX() - zombie.getX();
                int dy = nearest.getY() - zombie.getY();
                double dist = Math.sqrt(dx * dx + dy * dy);
                
                if (dist > 0) {
                    // Normalize and move at zombie speed (1.5 pixels per tick)
                    double speed = 1.5;
                    int moveX = (int) (speed * dx / dist);
                    int moveY = (int) (speed * dy / dist);
                    
                    int newX = zombie.getX() + moveX;
                    int newY = zombie.getY() + moveY;
                    
                    // Check if new position is valid (not wall)
                    int cx = newX + Player.SIZE / 2;
                    int cy = newY + Player.SIZE / 2;
                    
                    if (!gameState.isWallAt(cx, cy)) {
                        zombie.setX(newX);
                        zombie.setY(newY);
                    }
                }
            }
        }
    }

    private void infectPlayer(Player survivor, Player byZombie) {
        // Player dies instead of becoming zombie (we have AI zombies only)
        survivor.setHealth(0);
        String msg = "** " + survivor.getName() + " was killed by " + byZombie.getName() + "! **";
        gameState.addChatMessage(msg);
        server.broadcastToAll(new ChatBroadcast(msg));
        System.out.println(msg);
    }

    private void applyPickup(Player p, Pickup pk) {
        if (pk.getType() == Pickup.Type.MEDKIT) {
            p.addHealth(40);
            p.addScore(10);
            gameState.addChatMessage("[" + p.getName() + "] picked up a Medkit!");
        } else {
            p.addScore(50);
            gameState.addChatMessage("[" + p.getName() + "] found a Key! +50 points");
        }
    }

    private void checkWinConditions(List<Player> survivors, List<Player> zombies) {
        // Check if any survivors are still alive (health > 0)
        boolean anyAlive = false;
        for (Player s : survivors) {
            if (s.getHealth() > 0) {
                anyAlive = true;
                break;
            }
        }
        
        if (!anyAlive) {
            endGame("☣  ZOMBIES WIN!  All survivors eliminated.");
        } else if (gameState.getTimeRemaining() <= 0) {
            endGame("🏆  SURVIVORS WIN!  Time ran out.");
        }
    }

    private void endGame(String msg) {
        gameState.setWinnerMessage(msg);
        gameState.setCurrentPhase(GameState.Phase.GAME_OVER);
        gameState.setGameOverCountdown(RESET_DELAY_S);
        gameOverTime = System.currentTimeMillis();
        gameState.addChatMessage("=== " + msg + " ===");
        server.broadcastToAll(new ChatBroadcast("=== " + msg + " ==="));
        System.out.println("GAME OVER: " + msg);
    }

    // ── Game-Over phase ───────────────────────────────────────────────────────

    private void tickGameOver() {
        long elapsed = System.currentTimeMillis() - gameOverTime;
        int countdown = (int) Math.max(0, RESET_DELAY_S - elapsed / 1000);
        gameState.setGameOverCountdown(countdown);
        if (countdown <= 0) resetGame();
    }

    private void resetGame() {
        for (Player p : gameState.getPlayers().values()) {
            p.setRole(Player.Role.SURVIVOR);
            p.setHealth(100);
            p.setScore(0);
        }
        gameState.getPickups().clear();
        gameState.getChatLog().clear();
        gameState.setTimeRemaining(GAME_DURATION_S);
        gameState.setWinnerMessage("");
        gameState.setCurrentPhase(GameState.Phase.WAITING);
        System.out.println("Game reset → WAITING for players.");
    }

    // ── Collision helpers ─────────────────────────────────────────────────────

    private boolean overlaps(Player a, Player b) {
        return a.getX() < b.getX() + Player.SIZE &&
               a.getX() + Player.SIZE > b.getX() &&
               a.getY() < b.getY() + Player.SIZE &&
               a.getY() + Player.SIZE > b.getY();
    }

    private boolean overlapsPickup(Player p, Pickup pk) {
        return p.getX() < pk.getX() + Pickup.SIZE &&
               p.getX() + Player.SIZE > pk.getX() &&
               p.getY() < pk.getY() + Pickup.SIZE &&
               p.getY() + Player.SIZE > pk.getY();
    }
}
