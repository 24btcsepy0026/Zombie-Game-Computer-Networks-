package com.zombiesurvival.shared;

/** Sent by the client when the local player fires their weapon. */
public class ShootCommand implements NetworkMessage {
    private static final long serialVersionUID = 1L;

    private final float  dirX, dirY; // normalized direction vector
    private final Weapon weapon;

    public ShootCommand(float dirX, float dirY, Weapon weapon) {
        this.dirX   = dirX;
        this.dirY   = dirY;
        this.weapon = weapon;
    }

    public float  getDirX()  { return dirX; }
    public float  getDirY()  { return dirY; }
    public Weapon getWeapon(){ return weapon; }
}
