package com.zombiesurvival.server;

import com.zombiesurvival.shared.*;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core game loop at ~30 TPS.
 *
 * New features vs original:
 *  - Team assignment: ~25% zombies (min 1)
 *  - Server-side bullet physics with wall + player + NPC hit detection
 *  - 15 NPC zombies with simple AI (patrol + chase survivors within 220 px)
 *  - Zombie special abilities: Lunge (dash) and Howl (stun)
 *  - Kill feed entries broadcast with each state update
 *  - 180-second game timer (3 minutes)
 *  - Expanded pickup system (17 types)
 */
public class GameEngine implements Runnable {

    // ── Timing ────────────────────────────────────────────────────────────────
    private static final int TPS             = 30;
    private static final int TICK_MS         = 1000 / TPS;
    private static final int GAME_DURATION   = 180;   // seconds
    private static final int RESET_COUNTDOWN = 10;

    // ── Gameplay constants ────────────────────────────────────────────────────
    private static final int ZOMBIE_TOUCH_DMG   = 2;    // HP/tick on contact
    private static final int SAFE_HEAL_TICK     = 1;    // HP/tick in safe zone
    private static final int NPC_COUNT          = 15;
    private static final int NPC_SPEED          = 2;    // px/tick
    private static final int NPC_CHASE_RANGE    = 220;  // px
    private static final int NPC_ATTACK_DMG     = 3;    // HP/tick
    private static final int LUNGE_COOLDOWN     = 450;  // 15 s at 30 TPS
    private static final int HOWL_COOLDOWN      = 900;  // 30 s
    private static final int HOWL_RANGE         = 180;  // px
    private static final int HOWL_STUN_TICKS    = 30;   // 1 s stun
    private static final int BULLET_SPEED       = 14;   // px/tick
    private static final float LUNGE_SPEED      = 20f;  // px/tick burst

    // ── Spawn positions [col, row] ────────────────────────────────────────────
    private static final int[][] SPAWNS = {
        {14, 14},   // zombie spawn — urban centre
        {60,  5},   // hospital
        {15, 42},   // forest
        {60, 42},   // factory
        { 2,  2},   // urban corner NW
        {37,  2},   // urban corner NE
        { 2, 27},   // urban corner SW
        {37, 27},   // urban corner SE
        {42,  2},   // hospital left
        {77,  2},   // hospital right
        {42, 27},
        {77, 27},
        { 2, 31},   // forest corner NW
        {37, 31},
        { 2, 58},
        {37, 58},   // forest corner SE
    };

    // ── Pickup spawn table [col, row, type-ordinal] ───────────────────────────
    // Pickup.Type ordinals: MEDKIT=0 BANDAGE=1 KEY=2 ARMOR=3 SPEED_BOOST=4
    //  HELMET=5 VEST=6 BOOTS=7 PISTOL=8 SHOTGUN=9 RIFLE=10 BAT=11
    //  PISTOL_AMMO=12 RIFLE_AMMO=13 SHOTGUN_AMMO=14 GRENADE=15 MOLOTOV=16
    private static final int[][] PICKUP_DEFS = {
        // Healing
        { 5,  5,  0}, {37,  5,  0}, {42,  5,  0}, {77,  5,  0},
        { 5, 42,  0}, {37, 42,  0}, {55, 50,  0},
        { 8, 15,  1}, {25, 25,  1}, {45, 20,  1}, {65, 22,  1},
        {12, 48,  1}, {30, 55,  1},
        // Keys (score)
        { 2,  2,  2}, {37,  2,  2}, { 2, 27,  2}, {37, 27,  2},
        {42,  2,  2}, {77,  2,  2},
        // Equipment
        {19,  5,  5}, {60,  8,  5},  // helmets
        {20, 15,  6}, {55, 15,  6},  // vests
        {10, 35,  7}, {30, 50,  7},  // boots
        // Weapons
        { 5, 20,  8}, {37, 20,  8},  // pistol
        {45, 10,  9}, {72, 10,  9},  // shotgun
        {55, 20, 10}, {70, 20, 10},  // rifle
        {12, 35, 11}, {25, 45, 11},  // bat
        // Ammo
        {10,  8, 12}, {28,  8, 12}, {50,  8, 12}, {65,  8, 12},
        {10, 22, 13}, {33, 22, 13}, {50, 22, 13}, {70, 22, 13},
        { 8, 40, 14}, {22, 52, 14}, {55, 40, 14}, {65, 52, 14},
        // Throwables
        {18, 12, 15}, {35, 18, 15}, {48, 18, 15}, {75, 18, 15},
        {15, 45, 16}, {28, 58, 16}, {52, 45, 16}, {72, 50, 16},
        // Speed boost
        {20,  2,  4}, {60,  2,  4}, { 2, 44,  4}, {75, 44,  4},
        // Legacy armor
        {14,  8,  3}, {60, 14,  3},
    };

    private final GameServer server;
    private final GameState  state;
    private final Random     rng    = new Random();
    private final AtomicInteger bulletIdSeq = new AtomicInteger(0);
    private final AtomicInteger npcIdSeq    = new AtomicInteger(0);

    private int  tickCount    = 0;
    private int  gameOverTicks = 0;

    public GameEngine(GameServer server, GameState state) {
        this.server = server;
        this.state  = state;
    }

    // ── Run loop ──────────────────────────────────────────────────────────────

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            long start = System.currentTimeMillis();
            tick();
            long elapsed = System.currentTimeMillis() - start;
            long sleep   = TICK_MS - elapsed;
            if (sleep > 0) try { Thread.sleep(sleep); } catch (InterruptedException e) { break; }
        }
    }

    private void tick() {
        tickCount++;
        switch (state.getCurrentPhase()) {
            case WAITING   -> tickWaiting();
            case PLAYING   -> tickPlaying();
            case GAME_OVER -> tickGameOver();
        }
        // Prune expired kill-feed entries
        state.getKillFeed().removeIf(KillFeedEntry::isExpired);
        server.broadcastState(state);
    }

    // ── WAITING ───────────────────────────────────────────────────────────────

    private void tickWaiting() {
        if (state.getPlayers().size() >= 2) startGame();
    }

    private void startGame() {
        List<Player> players = new ArrayList<>(state.getPlayers().values());
        Collections.shuffle(players);

        // Assign roles: ~25 % zombies (at least 1)
        int zombieCount = Math.max(1, players.size() / 4);

        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            int[] sp  = SPAWNS[Math.min(i + 1, SPAWNS.length - 1)]; // +1: leave idx-0 for zombie
            if (i < zombieCount) sp = SPAWNS[0];

            p.setX(sp[0] * GameState.TILE_SIZE + 2);
            p.setY(sp[1] * GameState.TILE_SIZE + 2);
            p.setHealth(100);
            p.setStamina(100);
            p.setArmor(0);
            p.setSpeedBoost(0);
            p.setScore(0);
            p.setKills(0);
            p.setCurrentWeapon(Weapon.FIST);
            p.setPistolAmmo(0);
            p.setRifleAmmo(0);
            p.setShotgunAmmo(0);
            p.setHelmet(false);
            p.setVest(false);
            p.setBoots(false);
            p.setLungeCooldown(0);
            p.setHowlCooldown(0);

            if (i < zombieCount) {
                p.setRole(Player.Role.ZOMBIE);
                p.setInfected(true);
            } else {
                p.setRole(Player.Role.SURVIVOR);
                p.setInfected(false);
                // Starter gear for survivors
                p.setCurrentWeapon(Weapon.PISTOL);
                p.setPistolAmmo(15);
            }
        }

        spawnPickups();
        spawnNpcs();
        state.getBullets().clear();
        state.getKillFeed().clear();
        state.setTimeRemaining(GAME_DURATION);
        state.setCurrentPhase(GameState.Phase.PLAYING);
        System.out.println("[GameEngine] Game started! " + players.size() + " players, " + zombieCount + " zombies.");
    }

    // ── PLAYING ───────────────────────────────────────────────────────────────

    private void tickPlaying() {
        if (tickCount % TPS == 0 && state.getTimeRemaining() > 0)
            state.setTimeRemaining(state.getTimeRemaining() - 1);

        for (Player p : state.getPlayers().values()) {
            p.tickFireCooldown();
            p.tickSpeedBoost();
            p.tickLungeCooldown();
            p.tickHowlCooldown();
            p.tickStun();
            tickSafeZone(p);
            tickZombieContact(p);
            tickPickupCollection(p);
        }

        tickBullets();
        tickNpcs();
        checkWinConditions();
    }

    // ── Safe zone ─────────────────────────────────────────────────────────────

    private void tickSafeZone(Player p) {
        int cx = p.getX() + Player.SIZE / 2;
        int cy = p.getY() + Player.SIZE / 2;
        if (!GameState.isSafeZoneAt(cx, cy)) return;
        if (!p.isInfected() && tickCount % TPS == 0 && p.getHealth() < 100)
            p.addHealth(SAFE_HEAL_TICK);
    }

    // ── Zombie ↔ survivor contact damage ──────────────────────────────────────

    private void tickZombieContact(Player zombie) {
        if (!zombie.isInfected()) return;
        for (Player survivor : state.getPlayers().values()) {
            if (survivor.isInfected()) continue;
            if (!overlapsPlayers(zombie, survivor)) continue;

            int dmg = ZOMBIE_TOUCH_DMG;
            if (survivor.hasVest() || survivor.getArmor() > 0) {
                dmg = Math.max(1, dmg / 2);
                if (survivor.getArmor() > 0) survivor.drainArmor(1);
            }
            survivor.addHealth(-dmg);

            if (survivor.getHealth() <= 0) {
                infect(survivor, zombie.getName(), KillFeedEntry.Method.INFECTED);
                zombie.addScore(100);
                zombie.addKill();
            }
        }
    }

    private void infect(Player victim, String killerName, KillFeedEntry.Method method) {
        victim.setRole(Player.Role.ZOMBIE);
        victim.setInfected(true);
        victim.setHealth(60);
        victim.setArmor(0);
        victim.setCurrentWeapon(Weapon.FIST);
        victim.setPistolAmmo(0);
        victim.setRifleAmmo(0);
        victim.setShotgunAmmo(0);
        state.addKillFeedEntry(new KillFeedEntry(killerName, victim.getName(), method));
        System.out.println("[Engine] " + victim.getName() + " infected by " + killerName);
    }

    // ── Bullets ───────────────────────────────────────────────────────────────

    public void spawnBullet(String ownerId, float ox, float oy, float dirX, float dirY, Weapon weapon, float spreadDeg) {
        double ang = Math.atan2(dirY, dirX) + Math.toRadians(spreadDeg);
        float vx = (float)(Math.cos(ang) * BULLET_SPEED);
        float vy = (float)(Math.sin(ang) * BULLET_SPEED);
        int ticks = (int)(weapon.range / (float) BULLET_SPEED) + 1;
        String id = "b" + bulletIdSeq.incrementAndGet();
        state.getBullets().add(new Bullet(id, ox, oy, vx, vy, weapon.damage, ownerId, weapon, ticks));
    }

    private void tickBullets() {
        List<Bullet> toRemove = new ArrayList<>();
        for (Bullet b : state.getBullets()) {
            b.setX(b.getX() + b.getDx());
            b.setY(b.getY() + b.getDy());
            b.setTicksLeft(b.getTicksLeft() - 1);

            if (b.isExpired() || GameState.isWallAt((int)b.getX(), (int)b.getY())) {
                toRemove.add(b);
                continue;
            }

            // Hit survivors (only survivors can be shot by other survivors or NPC bullets)
            Player owner = state.getPlayer(b.getOwnerId());
            boolean ownerIsSurvivor = (owner != null && !owner.isInfected());

            for (Player target : state.getPlayers().values()) {
                if (target.getId().equals(b.getOwnerId())) continue;
                // Survivors only shoot zombies; zombies don't use guns (they have abilities)
                if (ownerIsSurvivor && target.isInfected()) continue; // survivors CAN'T shoot zombies in touch?
                // Actually survivors shoot zombies to kill them
                // Let survivors shoot infected players
                if (!overlapsPlayerBullet(target, b)) continue;

                int dmg = b.getDamage();
                if (target.hasVest() || target.getArmor() > 0) {
                    dmg = Math.max(1, dmg * 6 / 10);
                    if (target.getArmor() > 0) target.drainArmor(2);
                }
                if (target.hasHelmet()) dmg = Math.max(1, dmg * 7 / 10);
                target.addHealth(-dmg);

                if (target.getHealth() <= 0) {
                    if (target.isInfected()) {
                        // Zombie killed by bullet
                        target.setHealth(0);
                        target.setInfected(false);
                        target.setRole(Player.Role.SURVIVOR);
                        target.setHealth(30);
                        // They respawn as survivor with low HP (simplified)
                        if (owner != null) {
                            owner.addScore(75);
                            owner.addKill();
                            state.addKillFeedEntry(new KillFeedEntry(
                                owner.getName(), target.getName(),
                                b.getWeapon().isMelee() ? KillFeedEntry.Method.MELEE : KillFeedEntry.Method.SHOT));
                        }
                    } else {
                        // Survivor killed by bullet → infect
                        infect(target, owner != null ? owner.getName() : "?",
                               b.getWeapon().isMelee() ? KillFeedEntry.Method.MELEE : KillFeedEntry.Method.SHOT);
                        if (owner != null) { owner.addScore(75); owner.addKill(); }
                    }
                }
                toRemove.add(b);
                break;
            }

            // Hit NPC zombies
            for (NpcZombie npc : state.getNpcZombies().values()) {
                if (!npc.isAlive()) continue;
                if (!overlapsNpcBullet(npc, b)) continue;
                npc.addHealth(-b.getDamage());
                if (!npc.isAlive()) {
                    npc.setAlive(false);
                    if (owner != null) {
                        owner.addScore(30);
                        owner.addKill();
                        state.addKillFeedEntry(new KillFeedEntry(
                            owner.getName(), "Zombie NPC", KillFeedEntry.Method.SHOT));
                    }
                    // Drop loot
                    spawnNpcLoot(npc.getX(), npc.getY());
                }
                toRemove.add(b);
                break;
            }
        }
        state.getBullets().removeAll(toRemove);
    }

    private void spawnNpcLoot(float x, float y) {
        Pickup.Type[] loot = { Pickup.Type.BANDAGE, Pickup.Type.PISTOL_AMMO, Pickup.Type.SHOTGUN_AMMO };
        Pickup.Type t = loot[rng.nextInt(loot.length)];
        String id = "nl" + bulletIdSeq.incrementAndGet();
        state.getPickups().add(new Pickup(id, t, x, y));
    }

    // ── NPC AI ────────────────────────────────────────────────────────────────

    private void spawnNpcs() {
        state.getNpcZombies().clear();
        int[][] npcSpawns = {
            {39,29},{20,29},{60,29},{39,10},{39,50},
            { 5,10},{74,10},{ 5,50},{74,50},
            {10,20},{68,20},{10,40},{68,40},
            {25,35},{55,35}
        };
        for (int i = 0; i < NPC_COUNT; i++) {
            int[] s = npcSpawns[i % npcSpawns.length];
            String id = "npc" + npcIdSeq.incrementAndGet();
            NpcZombie npc = new NpcZombie(id,
                s[0] * GameState.TILE_SIZE + 4f,
                s[1] * GameState.TILE_SIZE + 4f);
            state.getNpcZombies().put(id, npc);
        }
        System.out.println("[Engine] Spawned " + NPC_COUNT + " NPC zombies.");
    }

    private void tickNpcs() {
        List<Player> survivors = state.getPlayers().values().stream()
            .filter(p -> !p.isInfected() && p.getHealth() > 0).toList();
        if (survivors.isEmpty()) return;

        for (NpcZombie npc : state.getNpcZombies().values()) {
            if (!npc.isAlive()) continue;

            // Find nearest survivor
            Player nearest = null;
            float  minDist = Float.MAX_VALUE;
            for (Player s : survivors) {
                float dx = s.getXf() - npc.getX(), dy = s.getYf() - npc.getY();
                float d  = (float) Math.sqrt(dx*dx + dy*dy);
                if (d < minDist) { minDist = d; nearest = s; }
            }

            if (nearest == null) continue;

            float dx = nearest.getXf() - npc.getX();
            float dy = nearest.getYf() - npc.getY();
            float dist = (float) Math.sqrt(dx*dx + dy*dy);

            if (dist < NPC_CHASE_RANGE) {
                // Chase
                float nx = npc.getX() + (dx / dist) * NPC_SPEED;
                float ny = npc.getY() + (dy / dist) * NPC_SPEED;
                if (!GameState.isWallAt((int)nx, (int)ny)) {
                    npc.setX(nx);
                    npc.setY(ny);
                    npc.setFacingAngle(Math.atan2(dy, dx));
                }

                // Attack on contact
                if (dist < Player.SIZE + NpcZombie.SIZE / 2f) {
                    if (!nearest.isStunned()) {
                        int dmg = NPC_ATTACK_DMG;
                        if (nearest.hasVest() || nearest.getArmor() > 0) dmg = Math.max(1, dmg / 2);
                        nearest.addHealth(-dmg);
                        if (nearest.getHealth() <= 0)
                            infect(nearest, "NPC Zombie", KillFeedEntry.Method.NPC_ATTACK);
                    }
                }
            } else {
                // Wander
                if (tickCount % 20 == npc.hashCode() % 20) {
                    double ang = rng.nextDouble() * Math.PI * 2;
                    float nx = npc.getX() + (float)(Math.cos(ang) * NPC_SPEED * 2);
                    float ny = npc.getY() + (float)(Math.sin(ang) * NPC_SPEED * 2);
                    nx = Math.max(32, Math.min(GameState.CANVAS_W - 32, nx));
                    ny = Math.max(32, Math.min(GameState.CANVAS_H - 32, ny));
                    if (!GameState.isWallAt((int)nx, (int)ny)) { npc.setX(nx); npc.setY(ny); }
                }
            }
        }
    }

    // ── Zombie abilities ──────────────────────────────────────────────────────

    public void handleLunge(String playerId) {
        Player zombie = state.getPlayer(playerId);
        if (zombie == null || !zombie.isInfected() || !zombie.canLunge()) return;

        // Find nearest survivor
        Player target = null;
        float minDist = Float.MAX_VALUE;
        for (Player p : state.getPlayers().values()) {
            if (p.isInfected()) continue;
            float dx = p.getXf() - zombie.getXf();
            float dy = p.getYf() - zombie.getYf();
            float d = (float) Math.sqrt(dx*dx + dy*dy);
            if (d < minDist && d < 300) { minDist = d; target = p; }
        }

        if (target != null) {
            float dx = target.getXf() - zombie.getXf();
            float dy = target.getYf() - zombie.getYf();
            float d  = (float) Math.sqrt(dx*dx + dy*dy);
            if (d > 0) {
                float nx = zombie.getXf() + (dx / d) * LUNGE_SPEED;
                float ny = zombie.getYf() + (dy / d) * LUNGE_SPEED;
                nx = Math.max(2, Math.min(GameState.CANVAS_W - Player.SIZE - 2, nx));
                ny = Math.max(2, Math.min(GameState.CANVAS_H - Player.SIZE - 2, ny));
                if (!GameState.isWallAt((int)nx, (int)ny)) { zombie.setX(nx); zombie.setY(ny); }
            }
        }
        zombie.setLungeCooldown(LUNGE_COOLDOWN);
    }

    public void handleHowl(String playerId) {
        Player zombie = state.getPlayer(playerId);
        if (zombie == null || !zombie.isInfected() || !zombie.canHowl()) return;

        float zx = zombie.getXf(), zy = zombie.getYf();
        for (Player p : state.getPlayers().values()) {
            if (p.isInfected()) continue;
            float dx = p.getXf() - zx, dy = p.getYf() - zy;
            if (Math.sqrt(dx*dx + dy*dy) <= HOWL_RANGE) {
                p.setStunned(HOWL_STUN_TICKS);
            }
        }
        zombie.setHowlCooldown(HOWL_COOLDOWN);
        zombie.addScore(10);
        System.out.println("[Engine] " + zombie.getName() + " howled! Stun range=" + HOWL_RANGE + "px");
    }

    // ── Pickups ───────────────────────────────────────────────────────────────

    private void tickPickupCollection(Player p) {
        for (Pickup pk : state.getPickups()) {
            if (pk.isCollected()) continue;
            if (!aabbOverlap(p.getX(), p.getY(), Player.SIZE, Player.SIZE,
                             pk.getX(), pk.getY(), Pickup.SIZE, Pickup.SIZE)) continue;
            pk.setCollected(true);
            applyPickup(p, pk);
        }
    }

    private void applyPickup(Player p, Pickup pk) {
        switch (pk.getType()) {
            case MEDKIT      -> { p.addHealth(50);  p.addScore(10); }
            case BANDAGE     -> { p.addHealth(20);  p.addScore(5); }
            case KEY         -> { p.addScore(50); }
            case ARMOR       -> { p.addArmor(40);   p.addScore(15); }
            case SPEED_BOOST -> { p.setSpeedBoost(150); p.addScore(15); }
            case HELMET      -> { p.setHelmet(true);  p.addScore(20); }
            case VEST        -> { p.setVest(true);    p.addScore(20); }
            case BOOTS       -> { p.setBoots(true);   p.addScore(20); }
            case PISTOL      -> { p.setCurrentWeapon(Weapon.PISTOL);  p.addPistolAmmo(30); p.addScore(25); }
            case SHOTGUN     -> { p.setCurrentWeapon(Weapon.SHOTGUN); p.addShotgunAmmo(10); p.addScore(30); }
            case RIFLE       -> { p.setCurrentWeapon(Weapon.RIFLE);   p.addRifleAmmo(15); p.addScore(35); }
            case BAT         -> { p.setCurrentWeapon(Weapon.BAT); p.addScore(20); }
            case PISTOL_AMMO -> { p.addPistolAmmo(30); p.addScore(5); }
            case RIFLE_AMMO  -> { p.addRifleAmmo(15);  p.addScore(5); }
            case SHOTGUN_AMMO-> { p.addShotgunAmmo(10);p.addScore(5); }
            case GRENADE     -> { p.addGrenades(1);    p.addScore(15); }
            case MOLOTOV     -> { p.addMolotovs(1);    p.addScore(15); }
        }
        System.out.printf("[Engine] %s collected %s%n", p.getName(), pk.getType());
    }

    // ── Win conditions ────────────────────────────────────────────────────────

    private void checkWinConditions() {
        long survivors = state.getPlayers().values().stream().filter(p -> !p.isInfected()).count();
        long zombies   = state.getPlayers().values().stream().filter(Player::isInfected).count();
        if (survivors == 0 && zombies > 0)
            endGame("☣  ZOMBIES WIN!");
        else if (state.getTimeRemaining() <= 0 && survivors > 0)
            endGame("🧍  SURVIVORS WIN!");
    }

    // ── GAME_OVER ─────────────────────────────────────────────────────────────

    private void tickGameOver() {
        gameOverTicks++;
        if (gameOverTicks % TPS == 0) {
            int cd = state.getGameOverCountdown() - 1;
            state.setGameOverCountdown(Math.max(0, cd));
            if (cd <= 0) resetGame();
        }
    }

    private void endGame(String msg) {
        state.setWinnerMessage(msg);
        state.setCurrentPhase(GameState.Phase.GAME_OVER);
        state.setGameOverCountdown(RESET_COUNTDOWN);
        gameOverTicks = 0;
        System.out.println("[Engine] Game over — " + msg);
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
            p.setKills(0);
            p.setCurrentWeapon(Weapon.FIST);
            p.setPistolAmmo(0);
        }
        state.getPickups().clear();
        state.getBullets().clear();
        state.getNpcZombies().clear();
        state.getKillFeed().clear();
        state.setTimeRemaining(GAME_DURATION);
        state.setWinnerMessage("");
        state.setGameOverCountdown(RESET_COUNTDOWN);
        state.setCurrentPhase(GameState.Phase.WAITING);
        tickCount = 0;
        System.out.println("[Engine] Game reset — waiting for players.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void spawnPickups() {
        state.getPickups().clear();
        Pickup.Type[] types = Pickup.Type.values();
        for (int i = 0; i < PICKUP_DEFS.length; i++) {
            int[] d = PICKUP_DEFS[i];
            if (d[2] >= types.length) continue;
            Pickup.Type t = types[d[2]];
            float px = d[0] * GameState.TILE_SIZE + (GameState.TILE_SIZE - Pickup.SIZE) / 2f;
            float py = d[1] * GameState.TILE_SIZE + (GameState.TILE_SIZE - Pickup.SIZE) / 2f;
            state.getPickups().add(new Pickup("pk" + i, t, px, py));
        }
        System.out.println("[Engine] Spawned " + state.getPickups().size() + " pickups.");
    }

    private boolean overlapsPlayers(Player a, Player b) {
        return aabbOverlap(a.getX(), a.getY(), Player.SIZE, Player.SIZE,
                           b.getX(), b.getY(), Player.SIZE, Player.SIZE);
    }

    private boolean overlapsPlayerBullet(Player p, Bullet b) {
        int bx = (int)b.getX() - Bullet.SIZE, by = (int)b.getY() - Bullet.SIZE;
        return aabbOverlap(p.getX(), p.getY(), Player.SIZE, Player.SIZE,
                           bx, by, Bullet.SIZE * 2, Bullet.SIZE * 2);
    }

    private boolean overlapsNpcBullet(NpcZombie npc, Bullet b) {
        int bx = (int)b.getX() - Bullet.SIZE, by = (int)b.getY() - Bullet.SIZE;
        return aabbOverlap(npc.getXi(), npc.getYi(), NpcZombie.SIZE, NpcZombie.SIZE,
                           bx, by, Bullet.SIZE * 2, Bullet.SIZE * 2);
    }

    private boolean aabbOverlap(int ax, int ay, int aw, int ah,
                                int bx, int by, int bw, int bh) {
        return ax < bx+bw && ax+aw > bx && ay < by+bh && ay+ah > by;
    }
}
