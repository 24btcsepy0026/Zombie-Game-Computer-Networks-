package com.zombiesurvival.shared;

import java.io.Serializable;

public class Player implements Serializable {
    private static final long serialVersionUID = 3L;

    public enum Role { ZOMBIE, SURVIVOR }

    public static final int SIZE = 28;

    private String id;
    private String name;
    private int x, y;
    private Role role;
    private boolean infected;
    private int health;
    private int stamina;
    private int armor;
    private int speedBoostTicks;
    private int score;

    public Player(String id, String name, int x, int y, Role role) {
        this.id       = id;
        this.name     = name;
        this.x = x;   this.y = y;
        this.role     = role;
        this.infected = (role == Role.ZOMBIE);
        this.health   = 100;
        this.stamina  = 100;
        this.armor    = 0;
        this.speedBoostTicks = 0;
        this.score    = 0;
    }

    public String getId()   { return id; }
    public String getName() { return name; }

    public int  getX() { return x; }  public void setX(int x) { this.x = x; }
    public int  getY() { return y; }  public void setY(int y) { this.y = y; }

    public Role getRole() { return role; }
    public void setRole(Role r) { this.role = r; if (r == Role.ZOMBIE) this.infected = true; }

    public boolean isInfected()          { return infected; }
    public void    setInfected(boolean v){ this.infected = v; }

    public int  getHealth()  { return health; }
    public void setHealth(int h) { this.health = Math.max(0, Math.min(100, h)); }
    public void addHealth(int d) { setHealth(health + d); }

    public int  getStamina()  { return stamina; }
    public void setStamina(int s) { this.stamina = Math.max(0, Math.min(100, s)); }
    public void drainStamina(int d) { setStamina(stamina - d); }
    public void regenStamina(int d) { setStamina(stamina + d); }

    public int  getArmor()   { return armor; }
    public void setArmor(int a) { this.armor = Math.max(0, Math.min(60, a)); }
    public void addArmor(int a) { setArmor(armor + a); }
    public void drainArmor(int d) { setArmor(armor - d); }

    public int     getSpeedBoostTicks() { return speedBoostTicks; }
    public void    setSpeedBoost(int t) { this.speedBoostTicks = t; }
    public boolean hasSpeedBoost()      { return speedBoostTicks > 0; }
    public void    tickSpeedBoost()     { if (speedBoostTicks > 0) speedBoostTicks--; }

    public int  getScore()   { return score; }
    public void setScore(int s)   { this.score = s; }
    public void addScore(int pts) { this.score += pts; }
}
