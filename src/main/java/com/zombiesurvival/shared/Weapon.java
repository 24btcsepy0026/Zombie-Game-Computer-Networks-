package com.zombiesurvival.shared;

import java.io.Serializable;

/**
 * All weapons available in the game.
 * damage      = HP removed per hit / per bullet
 * range       = bullet travel distance in pixels (0 = melee/instant)
 * fireDelay   = ticks between consecutive shots (30 TPS)
 * pellets     = projectile count per shot (shotgun spread)
 */
public enum Weapon implements Serializable {
    FIST    (15,   32,  8, 1, "Fist"),
    PISTOL  (25,  300,  8, 1, "Pistol"),
    SHOTGUN (18,  160, 18, 3, "Shotgun"),   // 3 pellets
    RIFLE   (45,  600, 15, 1, "Rifle"),
    BAT     (40,   36,  9, 1, "Baseball Bat"),
    GRENADE (90,  120,  1, 1, "Grenade"),   // throwable, area damage
    MOLOTOV (12,  100,  1, 1, "Molotov");   // throwable, DoT

    public final int    damage;
    public final int    range;
    public final int    fireDelay;
    public final int    pellets;
    public final String displayName;

    Weapon(int damage, int range, int fireDelay, int pellets, String displayName) {
        this.damage      = damage;
        this.range       = range;
        this.fireDelay   = fireDelay;
        this.pellets     = pellets;
        this.displayName = displayName;
    }

    public boolean isRanged()   { return range > 36; }
    public boolean isMelee()    { return range <= 36; }
    public boolean isThrowable(){ return this == GRENADE || this == MOLOTOV; }
}
