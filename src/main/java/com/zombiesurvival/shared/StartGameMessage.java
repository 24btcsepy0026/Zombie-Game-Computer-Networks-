package com.zombiesurvival.shared;

/**
 * Sent from client -> server to request the game to start in a specific mode.
 * SOLO      = AI zombies, single human survivor
 * MULTIPLAYER = one human randomly becomes zombie, rest are survivors
 */
public class StartGameMessage implements NetworkMessage {
    private static final long serialVersionUID = 1L;
    private final GameState.GameMode mode;

    public StartGameMessage(GameState.GameMode mode) { this.mode = mode; }
    public GameState.GameMode getMode() { return mode; }
}
