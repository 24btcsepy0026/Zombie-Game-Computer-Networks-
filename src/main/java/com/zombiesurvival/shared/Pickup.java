package com.zombiesurvival.shared;

import java.io.Serializable;

public class Pickup implements Serializable {
    private static final long serialVersionUID = 3L;

    public enum Type { MEDKIT, KEY, ARMOR, SPEED_BOOST }

    public static final int SIZE = 20;

    private final String id;
    private final Type   type;
    private int  x, y;
    private boolean collected;

    public Pickup(String id, Type type, int x, int y) {
        this.id   = id;
        this.type = type;
        this.x    = x;
        this.y    = y;
        this.collected = false;
    }

    public String  getId()         { return id; }
    public Type    getType()       { return type; }
    public int     getX()         { return x; }
    public int     getY()         { return y; }
    public boolean isCollected()  { return collected; }
    public void    setCollected(boolean v) { this.collected = v; }
}
