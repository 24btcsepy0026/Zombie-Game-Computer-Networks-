package com.zombiesurvival.shared;

import java.io.Serializable;

public class Player implements Serializable {
    private static final long serialVersionUID = 2L;

    public enum Role { ZOMBIE, SURVIVOR }

    public static final int SIZE = 28; // pixel size (square hitbox)

    private String id;
    private String name;
    private int x, y;
    private Role role;
    private boolean infected;
    private int health;
    private int score;

    public Player(String id, String name, int x, int y, Role role) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.role = role;
        this.infected = (role == Role.ZOMBIE);
        this.health = 100;
        this.score = 0;
    }

    // --- Getters & Setters ---

    public String getId()   { return id; }
    public String getName() { return name; }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public Role getRole() { return role; }
    public void setRole(Role role) {
        this.role = role;
        if (role == Role.ZOMBIE) this.infected = true;
    }

    public boolean isInfected() { return infected; }
    public void setInfected(boolean v) { this.infected = v; }

    public int getHealth() { return health; }
    public void setHealth(int h) { this.health = Math.max(0, Math.min(100, h)); }
    public void addHealth(int delta) { setHealth(this.health + delta); }

    public int getScore() { return score; }
    public void setScore(int s) { this.score = s; }
    public void addScore(int pts) { this.score += pts; }
}
