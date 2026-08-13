package com.zombiesurvival.shared;

public class MoveCommand implements NetworkMessage {
    private static final long serialVersionUID = 1L;
    private int dx, dy;

    public MoveCommand(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public int getDx() { return dx; }
    public int getDy() { return dy; }
}
