package com.zombiesurvival.server;

import com.zombiesurvival.shared.*;

import java.io.*;
import java.net.Socket;

/**
 * Handles one connected client.
 * Reads: JoinRequest, MoveCommand, ChatMessage, ShootCommand, AbilityCommand
 */
public class ClientHandler implements Runnable {

    private static final int BASE_SPEED      = 5;
    private static final int SPRINT_PCTX10   = 18;   // 1.8×
    private static final int BOOST_PCTX10    = 15;   // 1.5×
    private static final int BOUNDS_PAD      = 2;

    private final Socket       socket;
    private final GameServer   server;
    private final GameState    state;
    private final GameEngine   engine;
    private final String       playerId;
    private ObjectOutputStream out;
    private ObjectInputStream  in;

    public ClientHandler(Socket socket, GameServer server, GameState state,
                         GameEngine engine, String playerId) {
        this.socket   = socket;
        this.server   = server;
        this.state    = state;
        this.engine   = engine;
        this.playerId = playerId;
    }

    // ── Streams ───────────────────────────────────────────────────────────────

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
        try { out.writeObject(msg); out.flush(); out.reset(); }
        catch (IOException ignored) {}
    }

    // ── Main loop ─────────────────────────────────────────────────────────────

    @Override
    public void run() {
        try {
            Object first = in.readObject();
            if (first instanceof JoinRequest jr) {
                Player p = new Player(playerId, jr.getPlayerName(),
                                      GameState.TILE_SIZE * 2f,
                                      GameState.TILE_SIZE * 2f,
                                      Player.Role.SURVIVOR);
                state.getPlayers().put(playerId, p);
                send(new AssignIdMessage(playerId));
                System.out.println("[ClientHandler] " + jr.getPlayerName() + " joined as " + playerId);
            }

            while (!socket.isClosed()) {
                Object msg = in.readObject();
                if      (msg instanceof MoveCommand   mc) handleMove(mc);
                else if (msg instanceof ShootCommand  sc) handleShoot(sc);
                else if (msg instanceof AbilityCommand ac) handleAbility(ac);
                else if (msg instanceof ChatMessage   cm) server.broadcastChat(cm.getMessage(), playerId);
            }
        } catch (EOFException | java.net.SocketException ignored) {
        } catch (Exception e) {
            System.err.println("[ClientHandler] Error: " + e.getMessage());
        } finally { cleanup(); }
    }

    // ── Move ──────────────────────────────────────────────────────────────────

    private void handleMove(MoveCommand mc) {
        if (state.getCurrentPhase() != GameState.Phase.PLAYING) return;
        Player p = state.getPlayer(playerId);
        if (p == null || p.getHealth() <= 0 || p.isStunned()) return;

        int dx = mc.getDx(), dy = mc.getDy();
        if (dx == 0 && dy == 0) { p.regenStamina(1); return; }

        float spd = p.speedMultiplier();
        int   cx  = p.getX() + Player.SIZE / 2;
        int   cy  = p.getY() + Player.SIZE / 2;
        boolean inSafe = GameState.isSafeZoneAt(cx, cy);
        boolean zombie = p.isInfected();
        boolean sprint = mc.isSprinting() && !zombie && p.getStamina() > 0;

        if (sprint)               p.drainStamina(2);
        else if (!mc.isSprinting()) p.regenStamina(1);

        int pct = 10;
        if (zombie && inSafe)        pct = 4;
        else if (sprint && spd > 1f) pct = (int)(SPRINT_PCTX10 * spd);
        else if (sprint)             pct = SPRINT_PCTX10;
        else if (spd > 1f)           pct = (int)(10 * spd);

        int sdx = dx * pct / 10, sdy = dy * pct / 10;
        if (dx != 0 && sdx == 0) sdx = dx > 0 ? 1 : -1;
        if (dy != 0 && sdy == 0) sdy = dy > 0 ? 1 : -1;

        int newX = collide(p.getX(), p.getY(), sdx, 0,   true);
        int newY = collide(newX,     p.getY(), 0,   sdy, false);
        p.setX(newX);
        p.setY(newY);
    }

    private int collide(int px, int py, int dx, int dy, boolean horiz) {
        int nx = px + dx, ny = py + dy;
        nx = Math.max(BOUNDS_PAD, Math.min(GameState.CANVAS_W - Player.SIZE - BOUNDS_PAD, nx));
        ny = Math.max(BOUNDS_PAD, Math.min(GameState.CANVAS_H - Player.SIZE - BOUNDS_PAD, ny));
        int s = Player.SIZE;
        boolean blocked =
            GameState.isWallAt(nx,     ny    ) || GameState.isWallAt(nx+s-1, ny    ) ||
            GameState.isWallAt(nx,     ny+s-1) || GameState.isWallAt(nx+s-1, ny+s-1);
        if (blocked) return horiz ? px : py;
        return horiz ? nx : ny;
    }

    // ── Shoot ─────────────────────────────────────────────────────────────────

    private void handleShoot(ShootCommand sc) {
        if (state.getCurrentPhase() != GameState.Phase.PLAYING) return;
        Player p = state.getPlayer(playerId);
        if (p == null || p.isInfected() || p.getHealth() <= 0 || !p.canFire()) return;

        Weapon w = p.getCurrentWeapon();
        if (w.isMelee() || w.isThrowable()) {
            // Melee: instant damage in front
            handleMeleeAttack(p, sc.getDirX(), sc.getDirY(), w);
            p.setFireCooldown(w.fireDelay);
            return;
        }
        if (!p.consumeAmmo()) return; // no ammo

        // Shotgun: multiple pellets
        if (w == Weapon.SHOTGUN) {
            for (int i = 0; i < w.pellets; i++) {
                float spread = (i - w.pellets / 2) * 8f; // degrees
                engine.spawnBullet(playerId,
                    p.getXf() + Player.SIZE/2f,
                    p.getYf() + Player.SIZE/2f,
                    sc.getDirX(), sc.getDirY(), w, spread);
            }
        } else {
            engine.spawnBullet(playerId,
                p.getXf() + Player.SIZE/2f,
                p.getYf() + Player.SIZE/2f,
                sc.getDirX(), sc.getDirY(), w, 0);
        }
        p.setFireCooldown(w.fireDelay);
    }

    private void handleMeleeAttack(Player attacker, float dirX, float dirY, Weapon w) {
        float ax = attacker.getXf() + Player.SIZE/2f + dirX * w.range;
        float ay = attacker.getYf() + Player.SIZE/2f + dirY * w.range;
        for (Player target : state.getPlayers().values()) {
            if (target.getId().equals(playerId)) continue;
            float tx = target.getXf() + Player.SIZE/2f;
            float ty = target.getYf() + Player.SIZE/2f;
            float d  = (float) Math.sqrt((tx-ax)*(tx-ax) + (ty-ay)*(ty-ay));
            if (d <= w.range + Player.SIZE) {
                int dmg = w.damage;
                if (target.hasVest() || target.getArmor() > 0) {
                    dmg = Math.max(1, dmg * 6 / 10);
                    if (target.getArmor() > 0) target.drainArmor(3);
                }
                target.addHealth(-dmg);
                if (target.getHealth() <= 0) {
                    if (!target.isInfected()) {
                        // survivor hit by zombie melee
                        target.setRole(Player.Role.ZOMBIE);
                        target.setInfected(true);
                        target.setHealth(60);
                        state.addKillFeedEntry(new KillFeedEntry(
                            attacker.getName(), target.getName(), KillFeedEntry.Method.MELEE));
                        attacker.addScore(100);
                        attacker.addKill();
                    } else {
                        target.setHealth(30); // zombie survives at low hp
                    }
                }
            }
        }
        // Also hit NPC zombies
        for (NpcZombie npc : state.getNpcZombies().values()) {
            if (!npc.isAlive()) continue;
            float nx = npc.getX() + NpcZombie.SIZE/2f, ny = npc.getY() + NpcZombie.SIZE/2f;
            float d = (float) Math.sqrt((nx-ax)*(nx-ax) + (ny-ay)*(ny-ay));
            if (d <= w.range + NpcZombie.SIZE) {
                npc.addHealth(-w.damage);
                if (!npc.isAlive()) {
                    npc.setAlive(false);
                    attacker.addScore(30);
                    attacker.addKill();
                    state.addKillFeedEntry(new KillFeedEntry(
                        attacker.getName(), "Zombie NPC", KillFeedEntry.Method.MELEE));
                }
            }
        }
    }

    // ── Ability ───────────────────────────────────────────────────────────────

    private void handleAbility(AbilityCommand ac) {
        if (state.getCurrentPhase() != GameState.Phase.PLAYING) return;
        switch (ac.getAbilityType()) {
            case LUNGE -> engine.handleLunge(playerId);
            case HOWL  -> engine.handleHowl(playerId);
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    private void cleanup() {
        Player p = state.getPlayers().remove(playerId);
        System.out.println("[ClientHandler] " + (p != null ? p.getName() : playerId) + " disconnected.");
        server.removeClient(this);
        try { socket.close(); } catch (IOException ignored) {}
    }
}
