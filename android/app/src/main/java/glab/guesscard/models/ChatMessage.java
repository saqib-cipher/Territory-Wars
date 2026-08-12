package glab.guesscard.models;

import org.json.JSONObject;

public class ChatMessage {
    public String sender;
    public String message;
    public long timestamp;

    public ChatMessage(String sender, String message, long timestamp) {
        this.sender = sender;
        this.message = message;
        this.timestamp = timestamp;
    }

    public static ChatMessage fromJson(JSONObject json) {
        if (json == null) return new ChatMessage("System", "", System.currentTimeMillis());
        String s = json.optString("sender", "System");
        String m = json.optString("message", "");
        long t = json.optLong("timestamp", System.currentTimeMillis());
        return new ChatMessage(s, m, t);
    }
}
