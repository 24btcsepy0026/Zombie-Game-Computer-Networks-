package com.zombiesurvival.shared;

import java.io.Serializable;

/**
 * Represents a collectible item on the map.
 * MEDKIT: restores 40 HP  (+10 score)
 * KEY:    +50 score
 */
public class Pickup implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type { MEDKIT, KEY }

    public static final int SIZE = 22; // pixel size

    private String id;
    private Type type;
    private int x, y;      // top-left pixel position
    private boolean collected;

    public Pickup(String id, Type type, int x, int y) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.collected = false;
    }

    public String getId()       { return id; }
    public Type   getType()     { return type; }
    public int    getX()        { return x; }
    public int    getY()        { return y; }
    public boolean isCollected(){ return collected; }
    public void setCollected(boolean c) { this.collected = c; }
}
