package com.zombiesurvival.shared;

public class ChatMessage implements NetworkMessage {
    private static final long serialVersionUID = 1L;
    private String senderName;
    private String message;

    public ChatMessage(String senderName, String message) {
        this.senderName = senderName;
        this.message = message;
    }

    public String getSenderName() { return senderName; }
    public String getMessage() { return message; }
}
