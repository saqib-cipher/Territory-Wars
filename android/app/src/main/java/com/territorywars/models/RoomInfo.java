package com.territorywars.models;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class RoomInfo {
    public String roomId;
    public String code;
    public String name;
    public String hostId;
    public int maxPlayers;

    public static class LobbyPlayer {
        public String id;
        public String userId;
        public String username;
        public boolean isReady;
        public boolean isHost;
    }

    public List<LobbyPlayer> players = new ArrayList<>();

    public static RoomInfo fromJson(JSONObject json) {
        RoomInfo r = new RoomInfo();
        if (json != null) {
            r.roomId = json.optString("roomId");
            r.code = json.optString("code", r.roomId);
            r.name = json.optString("name");
            r.hostId = json.optString("hostId");
        }
        return r;
    }
}
