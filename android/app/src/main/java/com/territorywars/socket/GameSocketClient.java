package com.territorywars.socket;
import android.util.Log;
import androidx.annotation.Nullable;
import com.territorywars.BuildConfig;
import com.territorywars.models.ChatMessage;
import com.territorywars.models.MatchResult;
import com.territorywars.models.RoomInfo;
import com.territorywars.network.PreferenceManager;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Map;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
/**
* Thin wrapper around the Socket.IO client.
*
* <p>Handles connection lifecycle, authentication handshake and typed event
* fan-out to {@link GameSocketListener}. Also carries the reliable-command
* layer used for {@code playerMove}, {@code captureTile} and friends.</p>
*/
public class GameSocketClient {
private static final String TAG = "GameSocketClient";
private final PreferenceManager preferences;
private Socket socket;
private GameSocketListener listener;
private final Emitter.Listener onConnect = args -> {
Log.d(TAG, "connected");
if (listener != null) listener.onConnected();
};
private final Emitter.Listener onDisconnect = args -> {
Log.d(TAG, "disconnected: " + args);
if (listener != null) listener.onDisconnected();
};
private final Emitter.Listener onConnectError = args -> {
Log.e(TAG, "connect_error: " + args);
if (listener != null) listener.onError(args.length > 0 ? String.valueOf(args[0]) : "connect_error");
};
private final Emitter.Listener onRoomJoined = args -> {
if (args.length > 0 && args[0] instanceof JSONObject) {
RoomInfo room = RoomInfo.fromJson((JSONObject) args[0]);
if (listener != null) listener.onRoomJoined(room);
}
};
private final Emitter.Listener onRoomUpdate = args -> {
if (args.length > 0 && args[0] instanceof JSONObject) {
RoomInfo room = RoomInfo.fromJson((JSONObject) args[0]);
if (listener != null) listener.onRoomUpdated(room);
}
};
private final Emitter.Listener onChat = args -> {
if (args.length > 0 && args[0] instanceof JSONObject) {
listener.onChatMessage(ChatMessage.fromJson((JSONObject) args[0]));
}
};
private final Emitter.Listener onGameStart = args -> {
if (args.length > 0 && args[0] instanceof JSONObject) {
JSONObject data = (JSONObject) args[0];
String mapId = data.optString("mapId", "classic_plains");
long duration = data.optLong("durationMillis", 120_000L);
if (listener != null) listener.onGameStart(mapId, duration);
}
};
private final Emitter.Listener onGameEnd = args -> {
if (args.length > 0 && args[0] instanceof JSONObject) {
JSONObject data = (JSONObject) args[0];
MatchResult result = new MatchResult();
result.matchId = data.optString("roomId", "remote");
result.playedAtEpochMillis = data.optLong("endedAt", System.currentTimeMillis());
if (listener != null) listener.onGameEnd(result);
}
};
private final Emitter.Listener onScoreUpdate = args -> {
if (args.length > 0 && args[0] instanceof JSONObject) {
JSONObject data = (JSONObject) args[0];
if (listener != null) {
listener.onScoreUpdate(0,
data.optInt("tiles", 0),
data.optInt("score", 0));
}
}
};
public GameSocketClient(PreferenceManager preferences) {
this.preferences = preferences;
}
/** Registers the UI-facing listener. Call before {@link #connect()}. */
public void setListener(@Nullable GameSocketListener listener) {
this.listener = listener;
}
public boolean isConnected() {
return socket != null && socket.connected();
}
public void connect() {
if (socket != null && socket.connected()) return;
try {
IO.Options options = IO.Options.builder()
.setTransports(new String[]{"websocket"})
.setAuth(buildAuth())
.build();
socket = IO.socket(BuildConfig.SOCKET_URL, options);
socket.on(Socket.EVENT_CONNECT, onConnect);
socket.on(Socket.EVENT_DISCONNECT, onDisconnect);
socket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
socket.on("roomJoined", onRoomJoined);
socket.on("roomUpdate", onRoomUpdate);
socket.on("chatMessage", onChat);
socket.on("gameStart", onGameStart);
socket.on("gameEnd", onGameEnd);
socket.on("scoreUpdate", onScoreUpdate);
socket.connect();
} catch (Exception e) {
Log.e(TAG, "failed to create socket", e);
}
}
public void disconnect() {
if (socket != null) {
socket.off();
socket.disconnect();
socket = null;
}
}
// ---- Room commands ----
public void joinRoom(String roomId) {
emit("joinRoom", roomId);
}
public void joinRoomByCode(String code) {
emit("joinRoomByCode", code);
}
public void createRoom(String mode, String mapId) {
emit("createRoom", mode, mapId);
}
public void leaveRoom() {
emit("leaveRoom");
}
public void setReady(boolean ready) {
emit("setReady", ready);
}
public void sendChat(String roomId, String text) {
emit("chatMessage", roomId, text);
}
// ---- In-game events ----
/** Broadcasts the local player position in the given room. */
public void sendPlayerMove(String roomId, float x, float y, float vx, float vy) {
JSONObject data = new JSONObject();
try {
data.put("roomId", roomId);
data.put("x", x);
data.put("y", y);
data.put("vx", vx);
data.put("vy", vy);
} catch (JSONException ignored) {
}
emit("playerMove", data);
}
public void sendCaptureTile(String roomId, int tx, int ty) {
JSONObject data = new JSONObject();
try {
data.put("roomId", roomId);
data.put("tx", tx);
data.put("ty", ty);
} catch (JSONException ignored) {
}
emit("captureTile", data);
}
public void sendPowerupCollected(String roomId, String powerupId) {
JSONObject data = new JSONObject();
try {
data.put("roomId", roomId);
data.put("powerupId", powerupId);
} catch (JSONException ignored) {
}
emit("powerupCollected", data);
}
// ---- Internals ----
private Map<String, String> buildAuth() {
return java.util.Collections.singletonMap("token", preferences.getToken());
}
private void emit(String event, Object... args) {
if (socket == null || !socket.connected()) {
Log.w(TAG, "socket not connected; dropping " + event);
return;
}
socket.emit(event, args);
}
}
