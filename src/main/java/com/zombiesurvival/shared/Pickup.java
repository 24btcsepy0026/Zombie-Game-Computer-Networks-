package com.zombiesurvival.shared;

import java.io.Serializable;

/** All collectable items in the world. */
public class Pickup implements Serializable {
    private static final long serialVersionUID = 3L;

    public static final int SIZE = 22;

    public enum Type {
        // Healing
        MEDKIT,       // +50 HP
        BANDAGE,      // +20 HP (instant)
        // Score
        KEY,          // +50 score
        // Legacy / protective
        ARMOR,        // +40 armor (legacy vest)
        SPEED_BOOST,  // +5s speed boost
        // Wearable equipment
        HELMET,       // -30 % headshot damage
        VEST,         // -40 % bullet damage
        BOOTS,        // +20 % permanent speed
        // Weapons
        PISTOL,       // gives pistol + 30 ammo
        SHOTGUN,      // gives shotgun + 10 ammo
        RIFLE,        // gives rifle + 15 ammo
        BAT,          // gives baseball bat
        // Ammo
        PISTOL_AMMO,  // +30 ammo
        RIFLE_AMMO,   // +15 ammo
        SHOTGUN_AMMO, // +10 ammo
        // Throwables
        GRENADE,      // +1 grenade
        MOLOTOV       // +1 molotov
    }

    private final String  id;
    private final Type    type;
    private final float   x, y;
    private       boolean collected;

    public Pickup(String id, Type type, float x, float y) {
        this.id   = id;
        this.type = type;
        this.x    = x;
        this.y    = y;
    }

    public String  getId()                   { return id; }
    public Type    getType()                 { return type; }
    public int     getX()                    { return (int) x; }
    public int     getY()                    { return (int) y; }
    public boolean isCollected()             { return collected; }
    public void    setCollected(boolean c)   { this.collected = c; }
}
