package com.zombiesurvival.shared;

/**
 * Sent server → client to tell a client their own assigned player ID.
 */
public class AssignIdMessage implements NetworkMessage {
    private static final long serialVersionUID = 1L;
    private String playerId;

    public AssignIdMessage(String playerId) {
        this.playerId = playerId;
    }

    public String getPlayerId() { return playerId; }
}
