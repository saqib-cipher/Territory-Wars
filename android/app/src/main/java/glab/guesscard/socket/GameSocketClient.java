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
        Log.e(TAG, "connect_error: " + (args.length > 0 ? args[0] : ""));
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

    public boolean isConnected() {
        return socket != null && socket.connected();
    }

    public void connect() {
        if (socket != null && socket.connected()) return;
        try {
            okhttp3.OkHttpClient okHttpClient = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build();

            IO.Options options = new IO.Options();
            options.transports = new String[]{"websocket", "polling"};
            options.auth = buildAuth();
            options.query = buildQuery();
            options.reconnection = true;
            options.reconnectionAttempts = 20;
            options.reconnectionDelay = 1000;
            options.callFactory = okHttpClient;
            options.webSocketFactory = okHttpClient;

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
        emit("leaveRoom");
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

    public void sendRoomInvite(String targetUserId) {
        emit("sendRoomInvite", targetUserId);
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

    private Map<String, String> buildAuth() {
        Map<String, String> auth = new java.util.HashMap<>();
        String token = preferences != null ? preferences.getToken() : null;
        String uid = preferences != null ? preferences.getUserId() : null;
        String name = preferences != null ? preferences.getUsername() : null;
        if (token != null) auth.put("token", token);
        if (uid != null) auth.put("uid", uid);
        if (name != null) auth.put("name", name);
        return auth;
    }

    private String buildQuery() {
        String uid = preferences != null ? preferences.getUserId() : "";
        String name = preferences != null ? preferences.getUsername() : "Player";
        return "uid=" + (uid != null ? uid : "") + "&name=" + (name != null ? name : "Player");
    }

    private void emit(String event, Object... args) {
        if (socket == null || !socket.connected()) {
            Log.w(TAG, "socket not connected for " + event + "; attempting auto-reconnect");
            try {
                if (socket != null) socket.connect();
                else connect();
            } catch (Exception ignored) {}
            return;
        }
        socket.emit(event, args);
    }
}
