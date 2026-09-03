package glab.guesscard.socket;

import android.util.Base64;
import android.util.Log;
import androidx.annotation.Nullable;
import glab.guesscard.BuildConfig;
import glab.guesscard.models.ChatMessage;
import glab.guesscard.models.MatchResult;
import glab.guesscard.models.RoomInfo;
import glab.guesscard.network.PreferenceManager;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Map;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

/**
 * Socket.IO client for Guess the Card real-time multiplayer.
 * Handles connection lifecycle, room synchronization, question/answer exchanges,
 * guess submission, and WebRTC/Voice signaling.
 */
public class GameSocketClient {
    private static final String TAG = "GameSocketClient";
    private final PreferenceManager preferences;
    private Socket socket;
    private GameSocketListener listener;
    /**
     * Global (backend-driven) listener used by BaseActivity for friend requests
     * and room invitations on ANY screen. Kept separate from {@link #listener}
     * so game/lobby screens can install their own listener without losing it.
     */
    private GameSocketListener globalListener;
    // Pending room/voice to rejoin after (re)connect
    private String pendingRoomId;
    private String pendingVoiceRoomId;

    public interface VoiceListener {
        void onVoiceAudioReceived(String senderUid, byte[] audioPcm);
        void onPlayerSpeaking(String userId, boolean isSpeaking);
    }
    private VoiceListener voiceListener;

    public void setVoiceListener(@Nullable VoiceListener voiceListener) {
        this.voiceListener = voiceListener;
    }

    private final Emitter.Listener onConnect = args -> {
        Log.d(TAG, "connected");
        // Rejoin room + voice channel after (re)connect so server maps socket.data.roomId
        if (pendingRoomId != null) {
            socket.emit("joinRoom", pendingRoomId);
        }
        if (pendingVoiceRoomId != null) {
            socket.emit("voice_join", pendingVoiceRoomId);
        }
        if (listener != null) listener.onConnected();
    };
    private final Emitter.Listener onDisconnect = args -> {
        Log.d(TAG, "disconnected: " + (args.length > 0 ? args[0] : ""));
        if (listener != null) listener.onDisconnected();
    };
    private final Emitter.Listener onConnectError = args -> {
        if (args.length > 0) {
            Object arg = args[0];
            if (arg instanceof Throwable) {
                Throwable t = (Throwable) arg;
                Log.e(TAG, "connect_error: " + t.getMessage(), t);
                if (t.getCause() != null) {
                    Log.e(TAG, "connect_error cause: " + t.getCause().getMessage(), t.getCause());
                }
            } else if (arg instanceof JSONObject) {
                Log.e(TAG, "connect_error (json): " + arg);
            } else {
                Log.e(TAG, "connect_error: " + arg);
            }
        }
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
        if (args.length > 0 && args[0] instanceof JSONObject && listener != null) {
            listener.onChatMessage(ChatMessage.fromJson((JSONObject) args[0]));
        }
    };
    private final Emitter.Listener onGameStart = args -> {
        if (args.length > 0 && args[0] instanceof JSONObject) {
            JSONObject data = (JSONObject) args[0];
            String mode = data.optString("mode", "ANIMALS");
            long duration = data.optLong("durationMillis", 60_000L);
            if (listener != null) listener.onGameStart(mode, duration);
        }
    };
    private final Emitter.Listener onGameEnd = args -> {
        if (args.length > 0 && args[0] instanceof JSONObject) {
            JSONObject data = (JSONObject) args[0];
            MatchResult result = new MatchResult();
            result.matchId = data.optString("roomId", "remote");
            result.playedAtEpochMillis = System.currentTimeMillis();
            if (listener != null) listener.onGameEnd(result);
        }
    };

    private final Emitter.Listener onPublicRoomsList = args -> {
        if (args.length > 0 && args[0] instanceof org.json.JSONArray && listener != null) {
            org.json.JSONArray array = (org.json.JSONArray) args[0];
            java.util.List<RoomInfo> rooms = new java.util.ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj != null) {
                    rooms.add(RoomInfo.fromJson(obj));
                }
            }
            listener.onPublicRoomsList(rooms);
        }
    };

    private final Emitter.Listener onQuestionAsked = args -> {
        if (args.length > 0 && args[0] instanceof JSONObject && listener != null) {
            JSONObject data = (JSONObject) args[0];
            listener.onQuestionAsked(
                    data.optString("question"),
                    data.optString("askedBy"));
        }
    };
    private final Emitter.Listener onQuestionAnswered = args -> {
        if (args.length > 0 && args[0] instanceof JSONObject && listener != null) {
            JSONObject data = (JSONObject) args[0];
            listener.onAnswerGiven(
                    data.optString("question"),
                    data.optString("answer"),
                    data.optString("answererName", "Answerer"));
        }
    };
    private final Emitter.Listener onGuessResult = args -> {
        if (args.length > 0 && args[0] instanceof JSONObject && listener != null) {
            JSONObject data = (JSONObject) args[0];
            listener.onGuessResult(
                    data.optString("guessedBy"),
                    data.optString("guess"),
                    data.optBoolean("isCorrect", false),
                    data.optInt("scoreAwarded", 0),
                    data.optString("cardAnswer", ""));
        }
    };
    private final Emitter.Listener onTurnStarted = args -> {
        if (args.length > 0 && args[0] instanceof JSONObject && listener != null) {
            JSONObject data = (JSONObject) args[0];
            listener.onTurnStarted(
                    data.optString("currentTurnPlayerId"),
                    data.optInt("currentRound", 1));
        }
    };

    private final Emitter.Listener onVoiceAudioReceived = args -> {
        if (args.length > 0 && args[0] instanceof JSONObject && voiceListener != null) {
            JSONObject data = (JSONObject) args[0];
            String sender = data.optString("sender");
            String base64 = data.optString("data");
            if (base64 != null && !base64.isEmpty()) {
                try {
                    byte[] pcm = Base64.decode(base64, Base64.NO_WRAP);
                    voiceListener.onVoiceAudioReceived(sender, pcm);
                } catch (Exception ignored) {}
            }
        }
    };

    private final Emitter.Listener onVoicePlayerSpeaking = args -> {
        if (args.length > 0 && args[0] instanceof JSONObject && voiceListener != null) {
            JSONObject data = (JSONObject) args[0];
            voiceListener.onPlayerSpeaking(data.optString("userId"), data.optBoolean("isSpeaking", false));
        }
    };

    public interface LiveKitTokenCallback {
        void onLiveKitTokenReceived(String livekitUrl, String token, String roomId);
        void onLiveKitTokenError(String message);
    }
    private LiveKitTokenCallback liveKitCallback;

    public void requestLiveKitToken(String roomId, LiveKitTokenCallback callback) {
        this.liveKitCallback = callback;
        emit("get_livekit_token", roomId);
    }

    private final Emitter.Listener onLiveKitTokenReceived = args -> {
        if (args.length > 0 && args[0] instanceof JSONObject && liveKitCallback != null) {
            JSONObject data = (JSONObject) args[0];
            String url = data.optString("url");
            String token = data.optString("token");
            String roomId = data.optString("roomId");
            liveKitCallback.onLiveKitTokenReceived(url, token, roomId);
        }
    };

    private final Emitter.Listener onLiveKitTokenError = args -> {
        if (args.length > 0 && args[0] instanceof JSONObject && liveKitCallback != null) {
            JSONObject data = (JSONObject) args[0];
            liveKitCallback.onLiveKitTokenError(data.optString("message", "Token error"));
        }
    };

    private final Emitter.Listener onLiveKitTokenFallback = args -> {
        if (args.length > 0 && args[0] instanceof JSONObject && liveKitCallback != null) {
            JSONObject data = (JSONObject) args[0];
            liveKitCallback.onLiveKitTokenError(data.optString("message", "Fallback to direct voice"));
        }
    };

    public GameSocketClient(PreferenceManager preferences) {
        this.preferences = preferences;
    }

    public void setListener(@Nullable GameSocketListener listener) {
        this.listener = listener;
    }

    private String lastConnectedUid = null;

    /** Global listener receives friend requests + room invitations regardless of screen. */
    public void setGlobalEventListener(@Nullable GameSocketListener listener) {
        this.globalListener = listener;
    }

    public boolean isConnected() {
        return socket != null && socket.connected();
    }

    public void connect() {
        String currentUid = getResolvedUid();
        if (socket != null && socket.connected() && currentUid.equals(lastConnectedUid)) {
            return;
        }

        if (socket != null) {
            try {
                socket.off();
                socket.disconnect();
            } catch (Exception ignored) {}
            socket = null;
        }

        try {
            lastConnectedUid = currentUid;
            IO.Options options = IO.Options.builder()
                    .setTransports(new String[]{"polling", "websocket"})
                    .setAuth(buildAuth())
                    .setQuery(buildQuery())
                    .setReconnection(true)
                    .setReconnectionAttempts(30)
                    .setReconnectionDelay(1000)
                    .build();

            Log.d(TAG, "connecting to socket URL: " + BuildConfig.SOCKET_URL + " with UID: " + currentUid);
            socket = IO.socket(BuildConfig.SOCKET_URL, options);
            socket.on(Socket.EVENT_CONNECT, onConnect);
            socket.on(Socket.EVENT_DISCONNECT, onDisconnect);
            socket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
            socket.on("roomJoined", onRoomJoined);
            socket.on("roomUpdate", onRoomUpdate);
            socket.on("chatMessage", onChat);
            socket.on("gameStart", onGameStart);
            socket.on("gameEnd", onGameEnd);
            socket.on("questionAsked", onQuestionAsked);
            socket.on("questionAnswered", onQuestionAnswered);
            socket.on("guessResult", onGuessResult);
            socket.on("turnStarted", onTurnStarted);
            socket.on("voice_audio_received", onVoiceAudioReceived);
            socket.on("voice_player_speaking", onVoicePlayerSpeaking);
            socket.on("livekit_token_received", onLiveKitTokenReceived);
            socket.on("livekit_token_error", onLiveKitTokenError);
            socket.on("livekit_token_fallback", onLiveKitTokenFallback);
            socket.on("publicRoomsList", onPublicRoomsList);
            socket.on("roomInvitation", args -> {
                if (args.length > 0 && args[0] instanceof JSONObject) {
                    JSONObject d = (JSONObject) args[0];
                    String rId = d.optString("roomId");
                    String code = d.optString("code");
                    String sId = d.optString("senderId");
                    String sName = d.optString("senderName");
                    String sAvatar = d.optString("senderAvatar", "avatar1.png");
                    String mode = d.optString("mode", "ANIMALS");
                    if (listener != null) listener.onRoomInviteReceived(rId, code, sId, sName, sAvatar, mode);
                    if (globalListener != null) globalListener.onRoomInviteReceived(rId, code, sId, sName, sAvatar, mode);
                }
            });
            socket.on("friendRequestReceived", args -> {
                if (args.length > 0 && args[0] instanceof JSONObject) {
                    JSONObject d = (JSONObject) args[0];
                    String sId = d.optString("senderId");
                    String sName = d.optString("senderName", "Player");
                    String sAvatar = d.optString("senderAvatar", "avatar1.png");
                    if (listener != null) listener.onFriendRequestReceived(sId, sName, sAvatar);
                    if (globalListener != null) globalListener.onFriendRequestReceived(sId, sName, sAvatar);
                }
            });
            socket.on("friendRequestAccepted", args -> {
                if (args.length > 0 && args[0] instanceof JSONObject) {
                    JSONObject d = (JSONObject) args[0];
                    String aId = d.optString("acceptorId");
                    String aName = d.optString("acceptorName", "Player");
                    String aAvatar = d.optString("acceptorAvatar", "avatar1.png");
                    if (listener != null) listener.onFriendRequestAccepted(aId, aName, aAvatar);
                    if (globalListener != null) globalListener.onFriendRequestAccepted(aId, aName, aAvatar);
                }
            });
            socket.connect();
        } catch (Exception e) {
            Log.e(TAG, "failed to create socket", e);
        }
    }

    public interface OnlineStatusCallback {
        void onStatusResult(Map<String, Boolean> statusMap);
    }

    public void checkOnlineStatus(java.util.List<String> userIds, OnlineStatusCallback callback) {
        if (userIds == null || userIds.isEmpty() || callback == null) {
            if (callback != null) callback.onStatusResult(new java.util.HashMap<>());
            return;
        }
        if (!isConnected()) connect();
        if (socket != null) {
            org.json.JSONArray arr = new org.json.JSONArray(userIds);
            socket.emit("checkOnlineStatus", arr, (io.socket.client.Ack) args -> {
                Map<String, Boolean> map = new java.util.HashMap<>();
                if (args.length > 0 && args[0] instanceof JSONObject) {
                    JSONObject obj = (JSONObject) args[0];
                    for (String uid : userIds) {
                        map.put(uid, obj.optBoolean(uid, false));
                    }
                }
                callback.onStatusResult(map);
            });
        }
    }

    public void sendFriendRequest(String targetUserId) {
        if (targetUserId == null || targetUserId.isEmpty()) return;
        if (!isConnected()) connect();
        try {
            JSONObject obj = new JSONObject();
            obj.put("targetUserId", targetUserId);
            emit("sendFriendRequest", obj);
        } catch (Exception ignored) {}
    }

    /**
     * Tell the backend that the current user accepted a friend request from
     * {@code originalSenderUid} so the sender's UI updates to "Friends".
     */
    public void notifyFriendRequestAccepted(String originalSenderUid) {
        if (originalSenderUid == null || originalSenderUid.isEmpty()) return;
        if (!isConnected()) connect();
        try {
            JSONObject obj = new JSONObject();
            obj.put("targetUserId", originalSenderUid);
            emit("friendRequestAccepted", obj);
        } catch (Exception ignored) {}
    }

    public void sendRoomInvite(String targetUserId) {
        sendRoomInvite(targetUserId, null, null, null);
    }

    public void sendRoomInvite(String targetUserId, @Nullable String roomId, @Nullable String roomCode, @Nullable String mode) {
        if (targetUserId == null || targetUserId.isEmpty()) return;
        if (!isConnected()) connect();
        try {
            JSONObject obj = new JSONObject();
            obj.put("targetUserId", targetUserId);
            if (roomId != null && !roomId.isEmpty()) obj.put("roomId", roomId);
            if (roomCode != null && !roomCode.isEmpty()) obj.put("code", roomCode);
            if (mode != null && !mode.isEmpty()) obj.put("mode", mode);
            emit("sendRoomInvite", obj);
        } catch (Exception ignored) {
            emit("sendRoomInvite", targetUserId);
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
    public void requestPublicRooms() {
        if (!isConnected()) connect();
        emit("getPublicRooms");
    }

    public void joinRoom(String roomId) {
        emit("joinRoom", roomId);
    }

    public void joinRoomByCode(String code) {
        emit("joinRoomByCode", code);
    }

    public void createRoom(String mode, Object customConfig) {
        emit("createRoom", mode, customConfig);
    }

    public void leaveRoom() {
        this.pendingRoomId = null;
        this.pendingVoiceRoomId = null;
        emit("leaveRoom");
    }

    public void clearPendingRoom() {
        this.pendingRoomId = null;
        this.pendingVoiceRoomId = null;
    }

    public void setReady(boolean ready) {
        emit("setReady", ready);
    }

    public void startGame() {
        emit("startGame");
    }

    public void sendChat(String roomId, String text) {
        emit("chatMessage", roomId, text);
    }

    // ---- Voice commands ----
    /**
     * Join the voice channel. Stores roomId so it is re-emitted automatically
     * after every (re)connect — fixes the "audio never arrives" race condition
     * where voice_join fires before socket authentication completes.
     */
    public void emitVoiceJoin(String roomId) {
        this.pendingVoiceRoomId = roomId;
        if (isConnected()) {
            socket.emit("voice_join", roomId);
        } else {
            connect(); // onConnect will flush pendingVoiceRoomId
        }
    }

    /**
     * Tell the server which room this socket belongs to.
     * Required when entering GameFragment from an intent (no fresh joinRoom socket call).
     */
    public void rejoinRoom(String roomId) {
        this.pendingRoomId = roomId;
        if (isConnected()) {
            socket.emit("joinRoom", roomId);
        } else {
            connect();
        }
    }

    public void emitVoiceLeave(String roomId) {
        this.pendingVoiceRoomId = null;
        emit("voice_leave", roomId);
    }

    public void emitVoiceSpeaking(String roomId, boolean isSpeaking) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("roomId", roomId);
            obj.put("isSpeaking", isSpeaking);
            emit("voice_speaking", obj);
        } catch (Exception ignored) {}
    }

    public void emitVoiceAudioChunk(String roomId, String base64Pcm, long ts) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("roomId", roomId);
            obj.put("data", base64Pcm);
            obj.put("ts", ts);
            emit("voice_audio_chunk", obj);
        } catch (Exception ignored) {}
    }

    // ---- Gameplay commands ----
    public void askQuestion(String questionText) {
        emit("askQuestion", questionText);
    }

    public void answerQuestion(String answer) {
        emit("answerQuestion", answer);
    }

    public void submitGuess(String guessText) {
        emit("submitGuess", guessText);
    }

    public void passTurn() {
        emit("passTurn");
    }

    public String getResolvedUid() {
        String uid = preferences != null ? preferences.getUserId() : null;
        if (uid == null || uid.isEmpty() || "offline".equalsIgnoreCase(uid)) {
            com.google.firebase.auth.FirebaseUser fUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (fUser != null) {
                uid = fUser.getUid();
                if (preferences != null) preferences.saveUserId(uid);
            }
        }
        return uid != null ? uid : "";
    }

    public String getResolvedUsername() {
        String name = preferences != null ? preferences.getUsername() : null;
        if (name == null || name.isEmpty() || "Player".equals(name)) {
            com.google.firebase.auth.FirebaseUser fUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (fUser != null && fUser.getDisplayName() != null && !fUser.getDisplayName().isEmpty()) {
                name = fUser.getDisplayName();
                if (preferences != null) preferences.saveUsername(name);
            }
        }
        return name != null && !name.isEmpty() ? name : "Player";
    }

    public String getResolvedAvatar() {
        String avatar = preferences != null ? preferences.getAvatarFileName() : null;
        return avatar != null && !avatar.isEmpty() ? avatar : "avatar1.png";
    }

    private Map<String, String> buildAuth() {
        Map<String, String> auth = new java.util.HashMap<>();
        String token = preferences != null ? preferences.getToken() : null;
        String uid = getResolvedUid();
        String name = getResolvedUsername();
        String avatar = getResolvedAvatar();
        if (token != null && !token.isEmpty()) auth.put("token", token);
        if (uid != null && !uid.isEmpty()) auth.put("uid", uid);
        if (name != null && !name.isEmpty()) auth.put("name", name);
        if (avatar != null && !avatar.isEmpty()) auth.put("avatarFileName", avatar);
        return auth;
    }

    private String buildQuery() {
        try {
            String uid = getResolvedUid();
            String name = getResolvedUsername();
            String avatar = getResolvedAvatar();
            String encUid = java.net.URLEncoder.encode(uid != null ? uid : "", "UTF-8");
            String encName = java.net.URLEncoder.encode(name != null ? name : "Player", "UTF-8");
            String encAvatar = java.net.URLEncoder.encode(avatar != null ? avatar : "avatar1.png", "UTF-8");
            return "uid=" + encUid + "&name=" + encName + "&avatarFileName=" + encAvatar;
        } catch (Exception e) {
            return "name=Player";
        }
    }

    private void emit(String event, Object... args) {
        if (socket == null || !socket.connected()) {
            Log.w(TAG, "socket not connected for " + event + "; attempting auto-reconnect");
            try {
                connect();
            } catch (Exception ignored) {}
            return;
        }
        socket.emit(event, args);
    }
}
