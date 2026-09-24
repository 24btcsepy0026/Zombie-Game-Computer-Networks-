package com.zombiesurvival.shared;

/** Movement delta sent from client to server every input tick (~30 Hz). */
public class MoveCommand implements NetworkMessage {
    private static final long serialVersionUID = 2L;

    private final int     dx, dy;
    private final boolean sprinting;

    public MoveCommand(int dx, int dy)                    { this(dx, dy, false); }
    public MoveCommand(int dx, int dy, boolean sprinting) {
        this.dx        = dx;
        this.dy        = dy;
        this.sprinting = sprinting;
    }

    public int     getDx()         { return dx; }
    public int     getDy()         { return dy; }
    public boolean isSprinting()   { return sprinting; }
}
