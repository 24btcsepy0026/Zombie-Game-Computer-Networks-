package com.zombiesurvival.shared;

import java.io.Serializable;

/** A projectile fired by a player — tracked server-side and broadcast each tick. */
public class Bullet implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int SIZE = 5; // visual radius

    private final String id;
    private float  x, y;
    private final float  dx, dy;      // velocity pixels/tick
    private final int    damage;
    private final String ownerId;
    private final Weapon weapon;
    private int    ticksLeft;

    public Bullet(String id, float x, float y, float dx, float dy,
                  int damage, String ownerId, Weapon weapon, int ticksLeft) {
        this.id       = id;
        this.x        = x;
        this.y        = y;
        this.dx       = dx;
        this.dy       = dy;
        this.damage   = damage;
        this.ownerId  = ownerId;
        this.weapon   = weapon;
        this.ticksLeft = ticksLeft;
    }

    public String getId()       { return id; }
    public float  getX()        { return x; }
    public float  getY()        { return y; }
    public void   setX(float x) { this.x = x; }
    public void   setY(float y) { this.y = y; }
    public float  getDx()       { return dx; }
    public float  getDy()       { return dy; }
    public int    getDamage()   { return damage; }
    public String getOwnerId()  { return ownerId; }
    public Weapon getWeapon()   { return weapon; }
    public int    getTicksLeft(){ return ticksLeft; }
    public void   setTicksLeft(int t) { this.ticksLeft = t; }
    public boolean isExpired()  { return ticksLeft <= 0; }
}
