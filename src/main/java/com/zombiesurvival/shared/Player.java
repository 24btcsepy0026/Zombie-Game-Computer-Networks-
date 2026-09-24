package com.zombiesurvival.shared;

import java.io.Serializable;

/**
 * Full player model — shared between server and clients via serialization.
 * Fields added vs original: weapon system, equipment (helmet/vest/boots),
 * zombie abilities (lunge/howl), kills, float position for smooth movement.
 */
public class Player implements Serializable {
    private static final long serialVersionUID = 5L;

    public enum Role { ZOMBIE, SURVIVOR }

    public static final int SIZE = 28;

    // ── Identity ──────────────────────────────────────────────────────────────
    private final String id;
    private final String name;

    // ── Position (float for smooth sub-pixel movement) ────────────────────────
    private float x, y;

    // ── Core stats ────────────────────────────────────────────────────────────
    private Role    role;
    private boolean infected;
    private int     health;
    private int     stamina;
    private int     armor;          // legacy armor value (used by vest internally)
    private int     speedBoostTicks;
    private int     score;
    private int     kills;

    // ── Weapon system ─────────────────────────────────────────────────────────
    private Weapon  currentWeapon  = Weapon.FIST;
    private int     pistolAmmo     = 0;
    private int     rifleAmmo      = 0;
    private int     shotgunAmmo    = 0;
    private int     grenades       = 0;
    private int     molotovs       = 0;
    private int     fireCooldown   = 0;  // ticks until next shot allowed

    // ── Equipment ─────────────────────────────────────────────────────────────
    private boolean hasHelmet      = false;
    private boolean hasVest        = false;
    private boolean hasBoots       = false;

    // ── Zombie special abilities ──────────────────────────────────────────────
    private int     lungeCooldown  = 0;   // ticks remaining
    private int     howlCooldown   = 0;
    private boolean stunned        = false;
    private int     stunnedTicks   = 0;

    // ── Constructor ───────────────────────────────────────────────────────────

    public Player(String id, String name, float x, float y, Role role) {
        this.id       = id;
        this.name     = name;
        this.x        = x;
        this.y        = y;
        this.role     = role;
        this.infected = (role == Role.ZOMBIE);
        this.health   = 100;
        this.stamina  = 100;
        this.armor    = 0;
        this.score    = 0;
        this.kills    = 0;
    }

    // ── Identity ──────────────────────────────────────────────────────────────
    public String getId()   { return id; }
    public String getName() { return name; }

    // ── Position ──────────────────────────────────────────────────────────────
    public float getXf()          { return x; }
    public float getYf()          { return y; }
    public int   getX()           { return (int) x; }
    public int   getY()           { return (int) y; }
    public void  setX(float x)    { this.x = x; }
    public void  setY(float y)    { this.y = y; }
    public void  setX(int   x)    { this.x = x; }
    public void  setY(int   y)    { this.y = y; }

    // ── Role / infection ──────────────────────────────────────────────────────
    public Role    getRole()           { return role; }
    public void    setRole(Role r)     { this.role = r; if (r == Role.ZOMBIE) infected = true; }
    public boolean isInfected()        { return infected; }
    public void    setInfected(boolean v) { this.infected = v; }

    // ── Health ────────────────────────────────────────────────────────────────
    public int  getHealth()      { return health; }
    public void setHealth(int h) { this.health = Math.max(0, Math.min(100, h)); }
    public void addHealth(int d) { setHealth(health + d); }

    // ── Stamina ───────────────────────────────────────────────────────────────
    public int  getStamina()       { return stamina; }
    public void setStamina(int s)  { this.stamina = Math.max(0, Math.min(100, s)); }
    public void drainStamina(int d){ setStamina(stamina - d); }
    public void regenStamina(int d){ setStamina(stamina + d); }

    // ── Armor (legacy field, now vest gives 50 protection) ────────────────────
    public int  getArmor()        { return armor; }
    public void setArmor(int a)   { this.armor = Math.max(0, Math.min(60, a)); }
    public void addArmor(int a)   { setArmor(armor + a); }
    public void drainArmor(int d) { setArmor(armor - d); }

    // ── Speed boost ───────────────────────────────────────────────────────────
    public int     getSpeedBoostTicks() { return speedBoostTicks; }
    public void    setSpeedBoost(int t) { this.speedBoostTicks = t; }
    public boolean hasSpeedBoost()      { return speedBoostTicks > 0; }
    public void    tickSpeedBoost()     { if (speedBoostTicks > 0) speedBoostTicks--; }

    // ── Score / kills ─────────────────────────────────────────────────────────
    public int  getScore()        { return score; }
    public void setScore(int s)   { this.score = s; }
    public void addScore(int pts) { this.score += pts; }
    public int  getKills()        { return kills; }
    public void addKill()         { kills++; }
    public void setKills(int k)   { this.kills = k; }

    // ── Weapon ────────────────────────────────────────────────────────────────
    public Weapon getCurrentWeapon()          { return currentWeapon; }
    public void   setCurrentWeapon(Weapon w)  { this.currentWeapon = w; }

    public int     getPistolAmmo()             { return pistolAmmo; }
    public void    setPistolAmmo(int a)        { this.pistolAmmo   = Math.max(0, a); }
    public void    addPistolAmmo(int a)        { pistolAmmo  += a; }
    public boolean usePistolAmmo()             { if (pistolAmmo  > 0) { pistolAmmo--;  return true; } return false; }

    public int     getRifleAmmo()              { return rifleAmmo; }
    public void    setRifleAmmo(int a)         { this.rifleAmmo    = Math.max(0, a); }
    public void    addRifleAmmo(int a)         { rifleAmmo   += a; }
    public boolean useRifleAmmo()              { if (rifleAmmo   > 0) { rifleAmmo--;   return true; } return false; }

    public int     getShotgunAmmo()            { return shotgunAmmo; }
    public void    setShotgunAmmo(int a)       { this.shotgunAmmo  = Math.max(0, a); }
    public void    addShotgunAmmo(int a)       { shotgunAmmo += a; }
    public boolean useShotgunAmmo()            { if (shotgunAmmo > 0) { shotgunAmmo--; return true; } return false; }

    public int     getGrenades()               { return grenades; }
    public void    addGrenades(int a)          { grenades    += a; }
    public boolean useGrenade()                { if (grenades   > 0) { grenades--;    return true; } return false; }

    public int     getMolotovs()               { return molotovs; }
    public void    addMolotovs(int a)          { molotovs    += a; }
    public boolean useMolotov()                { if (molotovs   > 0) { molotovs--;    return true; } return false; }

    public int     getFireCooldown()           { return fireCooldown; }
    public void    setFireCooldown(int t)      { this.fireCooldown = t; }
    public void    tickFireCooldown()          { if (fireCooldown > 0) fireCooldown--; }
    public boolean canFire()                   { return fireCooldown == 0; }

    /** Returns how much ammo the player has for their current weapon. */
    public int currentAmmo() {
        return switch (currentWeapon) {
            case PISTOL  -> pistolAmmo;
            case RIFLE   -> rifleAmmo;
            case SHOTGUN -> shotgunAmmo;
            case GRENADE -> grenades;
            case MOLOTOV -> molotovs;
            default      -> -1; // unlimited (fist / bat)
        };
    }

    /** Attempt to consume 1 round of the current weapon. Returns false if out of ammo. */
    public boolean consumeAmmo() {
        return switch (currentWeapon) {
            case PISTOL  -> usePistolAmmo();
            case RIFLE   -> useRifleAmmo();
            case SHOTGUN -> useShotgunAmmo();
            case GRENADE -> useGrenade();
            case MOLOTOV -> useMolotov();
            default      -> true; // fist / bat always available
        };
    }

    // ── Equipment ─────────────────────────────────────────────────────────────
    public boolean hasHelmet()          { return hasHelmet; }
    public void    setHelmet(boolean b) { this.hasHelmet = b; }
    public boolean hasVest()            { return hasVest; }
    public void    setVest(boolean b)   { this.hasVest   = b; }
    public boolean hasBoots()           { return hasBoots; }
    public void    setBoots(boolean b)  { this.hasBoots  = b; }

    // ── Zombie abilities ──────────────────────────────────────────────────────
    public int  getLungeCooldown()          { return lungeCooldown; }
    public void setLungeCooldown(int t)     { this.lungeCooldown = t; }
    public void tickLungeCooldown()         { if (lungeCooldown > 0) lungeCooldown--; }
    public boolean canLunge()               { return lungeCooldown == 0; }

    public int  getHowlCooldown()           { return howlCooldown; }
    public void setHowlCooldown(int t)      { this.howlCooldown  = t; }
    public void tickHowlCooldown()          { if (howlCooldown  > 0) howlCooldown--;  }
    public boolean canHowl()                { return howlCooldown == 0; }

    public boolean isStunned()              { return stunned && stunnedTicks > 0; }
    public void    setStunned(int ticks)    { this.stunned = true; this.stunnedTicks = ticks; }
    public void    tickStun()               { if (stunnedTicks > 0) { stunnedTicks--; if (stunnedTicks == 0) stunned = false; } }

    /** Effective speed multiplier combining all sources. */
    public float speedMultiplier() {
        if (isStunned()) return 0f;
        float m = 1.0f;
        if (hasSpeedBoost()) m *= 1.5f;
        if (hasBoots)        m *= 1.2f;
        return m;
    }
}
