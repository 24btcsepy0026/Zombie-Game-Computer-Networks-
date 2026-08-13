package com.zombiesurvival.shared;

public class JoinRequest implements NetworkMessage {
    private static final long serialVersionUID = 1L;
    private String playerName;

    public JoinRequest(String playerName) {
        this.playerName = playerName;
    }

    public String getPlayerName() {
        return playerName;
    }
}
