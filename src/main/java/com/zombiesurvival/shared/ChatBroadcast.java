package com.zombiesurvival.shared;

/**
 * Sent server → ALL clients to broadcast a formatted chat line.
 * Text is pre-formatted by the server as "[Name]: message".
 */
public class ChatBroadcast implements NetworkMessage {
    private static final long serialVersionUID = 1L;
    private String text;

    public ChatBroadcast(String text) {
        this.text = text;
    }

    public String getText() { return text; }
}
