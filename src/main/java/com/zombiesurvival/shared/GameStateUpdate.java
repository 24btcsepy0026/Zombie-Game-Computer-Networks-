package com.zombiesurvival.shared;

public class GameStateUpdate implements NetworkMessage {
    private static final long serialVersionUID = 1L;
    private GameState state;

    public GameStateUpdate(GameState state) {
        this.state = state;
    }

    public GameState getState() { return state; }
}
