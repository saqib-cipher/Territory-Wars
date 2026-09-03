package glab.guesscard.bluetooth;

import org.json.JSONObject;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Model representing a local offline Bluetooth Mesh Game Room.
 */
public class BluetoothRoomModel implements Serializable {

    public static final String BLE_ADVERTISE_PREFIX = "GTC_";
    public static final String SERVICE_UUID_STRING = "e0cbf06c-cd8b-4647-bb8a-263b43f0f974";

    public String roomId;
    public String roomCode;
    public String roomName;
    public String gameMode;
    public String hostUid;
    public String hostName;
    public String hostAvatar;
    public String hostDeviceAddress;
    public int playersCount = 1;
    public int maxPlayers = 5;
    public boolean isFull = false;
    public long lastSeenTimestamp = System.currentTimeMillis();

    public BluetoothRoomModel() {}

    public BluetoothRoomModel(String roomCode, String roomName, String gameMode, String hostUid, String hostName, String hostAvatar, String hostDeviceAddress) {
        this.roomCode = roomCode;
        this.roomName = (roomName != null && !roomName.trim().isEmpty()) ? roomName.trim() : (gameMode + " MESH");
        this.gameMode = gameMode != null ? gameMode : "ANIMALS";
        this.hostUid = hostUid != null ? hostUid : "host_bt";
        this.hostName = hostName != null && !hostName.isEmpty() ? hostName : "Host";
        this.hostAvatar = hostAvatar != null && !hostAvatar.isEmpty() ? hostAvatar : "avatar_01.png";
        this.hostDeviceAddress = hostDeviceAddress;
        this.roomId = "bt_" + (hostDeviceAddress != null ? hostDeviceAddress.replace(":", "") : roomCode);
    }

    /**
     * Compact BLE Advertisement String (fits in BLE local name/service payload)
     * Format: GTC_<code4>_<modeChar>_<pCount>_<hostNameCut>
     */
    public String toCompactBleName() {
        String codeShort = roomCode != null && roomCode.length() >= 4 ? roomCode.substring(0, 4) : "ABCD";
        char modeChar = gameMode != null && !gameMode.isEmpty() ? gameMode.charAt(0) : 'A';
        String safeHost = hostName != null ? hostName.replace("_", "").replace(" ", "") : "Host";
        if (safeHost.length() > 6) safeHost = safeHost.substring(0, 6);
        return BLE_ADVERTISE_PREFIX + codeShort + "_" + modeChar + "_" + playersCount + "_" + safeHost;
    }

    /**
     * Parses a compact BLE name back into a room model.
     */
    public static BluetoothRoomModel fromCompactBleName(String bleName, String deviceAddress) {
        if (bleName == null || !bleName.startsWith(BLE_ADVERTISE_PREFIX)) return null;
        try {
            String payload = bleName.substring(BLE_ADVERTISE_PREFIX.length());
            String[] parts = payload.split("_");
            if (parts.length >= 3) {
                String code = parts[0];
                char modeChar = parts[1].charAt(0);
                int pCount = Integer.parseInt(parts[2]);
                String host = parts.length >= 4 ? parts[3] : "Host";

                String mode = "ANIMALS";
                if (modeChar == 'F') mode = "FOOD";
                else if (modeChar == 'C') mode = "COUNTRIES";
                else if (modeChar == 'E') mode = "CELEBRITIES";

                BluetoothRoomModel m = new BluetoothRoomModel(code, mode + " MESH ROOM", mode, "host_" + deviceAddress, host, "avatar_01.png", deviceAddress);
                m.playersCount = pCount;
                m.isFull = pCount >= 5;
                return m;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("roomId", roomId);
            obj.put("roomCode", roomCode);
            obj.put("roomName", roomName);
            obj.put("gameMode", gameMode);
            obj.put("hostUid", hostUid);
            obj.put("hostName", hostName);
            obj.put("hostAvatar", hostAvatar);
            obj.put("hostDeviceAddress", hostDeviceAddress);
            obj.put("playersCount", playersCount);
            obj.put("maxPlayers", maxPlayers);
            obj.put("isFull", isFull);
        } catch (Exception ignored) {}
        return obj;
    }

    public static BluetoothRoomModel fromJson(JSONObject obj) {
        if (obj == null) return null;
        BluetoothRoomModel m = new BluetoothRoomModel();
        m.roomId = obj.optString("roomId");
        m.roomCode = obj.optString("roomCode");
        m.roomName = obj.optString("roomName");
        m.gameMode = obj.optString("gameMode", "ANIMALS");
        m.hostUid = obj.optString("hostUid");
        m.hostName = obj.optString("hostName", "Host");
        m.hostAvatar = obj.optString("hostAvatar", "avatar_01.png");
        m.hostDeviceAddress = obj.optString("hostDeviceAddress");
        m.playersCount = obj.optInt("playersCount", 1);
        m.maxPlayers = obj.optInt("maxPlayers", 5);
        m.isFull = obj.optBoolean("isFull", false);
        return m;
    }

    /**
     * Converts to standard Map format for unified Home active rooms RecyclerView adapter.
     */
    public Map<String, Object> toRoomMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("roomId", roomId);
        map.put("name", roomName != null ? roomName : (gameMode + " MESH ROOM"));
        map.put("code", roomCode != null ? roomCode : "BT-MESH");
        map.put("mode", gameMode != null ? gameMode : "ANIMALS");
        map.put("hostUid", hostUid != null ? hostUid : "");
        map.put("hostName", hostName != null ? hostName : "Host");
        map.put("hostAvatar", hostAvatar != null ? hostAvatar : "avatar_01.png");
        map.put("playersCount", (long) playersCount);
        map.put("playersNames", "Bluetooth Mesh Nearby");
        map.put("status", isFull ? "IN_PROGRESS" : "WAITING");
        map.put("isFull", isFull);
        map.put("isBluetoothMesh", true);
        map.put("deviceAddress", hostDeviceAddress);
        return map;
    }
}
