package com.territorywars.models;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Room information broadcast by the matchmaking service.
 */
public class RoomInfo {

    @SerializedName("roomId")
    public String roomId;

    @SerializedName("code")
    public String code;

    @SerializedName("mode")
    public String mode;

    @SerializedName("mapId")
    public String mapId;

    @SerializedName("maxPlayers")
    public int maxPlayers;

    @SerializedName("hostId")
    public String hostId;

    @SerializedName("players")
    public List<LobbyPlayer> players;

    @SerializedName("countdownSeconds")
    public int countdownSeconds;

    public static class LobbyPlayer {
        @SerializedName("userId")
        public String userId;

        @SerializedName("username")
        public String username;

        @SerializedName("avatarId")
        public String avatarId;

        @SerializedName("isReady")
        public boolean isReady;

        @SerializedName("isHost")
        public boolean isHost;
    }

    public LobbyPlayer self(String myUserId) {
        if (players == null) return null;
        for (LobbyPlayer p : players) {
            if (Integer.toString(p.userId.hashCode()).equals(myUserId)) return p;
        }
        return null;
    }

    /** Parses a socket payload. */
    public static RoomInfo fromJson(org.json.JSONObject json) {
        RoomInfo room = new RoomInfo();
        room.roomId = json.optString("roomId");
        room.code = json.optString("code");
        room.mode = json.optString("mode");
        room.mapId = json.optString("mapId");
        room.maxPlayers = json.optInt("maxPlayers", 8);
        room.hostId = json.optString("hostId");
        room.countdownSeconds = json.optInt("countdownSeconds");
        room.players = new java.util.ArrayList<>();
        org.json.JSONArray arr = json.optJSONArray("players");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject p = arr.optJSONObject(i);
                if (p == null) continue;
                LobbyPlayer lp = new LobbyPlayer();
                lp.userId = p.optString("userId");
                lp.username = p.optString("username", "Player");
                lp.avatarId = p.optString("avatarId");
                lp.isReady = p.optBoolean("isReady");
                lp.isHost = p.optBoolean("isHost");
                room.players.add(lp);
            }
        }
        return room;
    }
}