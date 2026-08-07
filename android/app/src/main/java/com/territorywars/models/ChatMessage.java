package com.territorywars.models;

import com.google.gson.annotations.SerializedName;

/**
 * Lobby / in-game chat message.
 */
public class ChatMessage {

    @SerializedName("from")
    public String fromUserId;

    @SerializedName("username")
    public String username;

    @SerializedName("text")
    public String text;

    @SerializedName("roomId")
    public String roomId;

    @SerializedName("timestamp")
    public long timestamp;

    public ChatMessage() {
    }

    public ChatMessage(String fromUserId, String username, String text, long timestamp) {
        this.fromUserId = fromUserId;
        this.username = username;
        this.text = text;
        this.timestamp = timestamp;
    }

    /** Parses a socket payload. */
    public static ChatMessage fromJson(org.json.JSONObject json) {
        ChatMessage msg = new ChatMessage();
        msg.fromUserId = json.optString("from");
        msg.username = json.optString("username", "Player");
        msg.text = json.optString("text", "");
        msg.roomId = json.optString("roomId");
        msg.timestamp = json.optLong("timestamp", System.currentTimeMillis());
        return msg;
    }
}