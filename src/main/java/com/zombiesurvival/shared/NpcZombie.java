package com.zombiesurvival.shared;

import java.io.Serializable;

/** AI-controlled zombie roaming the map — managed entirely by the server. */
public class NpcZombie implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int SIZE       = 26;
    public static final int MAX_HEALTH = 60;

    private final String id;
    private float  x, y;
    private int    health;
    private boolean alive;
    private double facingAngle; // used by client for rendering direction

    public NpcZombie(String id, float x, float y) {
        this.id     = id;
        this.x      = x;
        this.y      = y;
        this.health = MAX_HEALTH;
        this.alive  = true;
    }

    public String  getId()          { return id; }
    public float   getX()           { return x; }
    public float   getY()           { return y; }
    public int     getXi()          { return (int) x; }
    public int     getYi()          { return (int) y; }
    public void    setX(float x)    { this.x = x; }
    public void    setY(float y)    { this.y = y; }
    public int     getHealth()      { return health; }
    public void    setHealth(int h) { this.health = Math.max(0, Math.min(MAX_HEALTH, h)); }
    public void    addHealth(int d) { setHealth(health + d); }
    public boolean isAlive()        { return alive && health > 0; }
    public void    setAlive(boolean a){ this.alive = a; }
    public double  getFacingAngle() { return facingAngle; }
    public void    setFacingAngle(double a) { this.facingAngle = a; }
}
