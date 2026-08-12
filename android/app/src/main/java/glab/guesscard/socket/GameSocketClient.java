package glab.guesscard.socket;

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
 * and guess submission.
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
            socket.on("questionAsked", onQuestionAsked);
            socket.on("questionAnswered", onQuestionAnswered);
            socket.on("guessResult", onGuessResult);
            socket.on("turnStarted", onTurnStarted);
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

    public void createRoom(String mode, Object customConfig) {
        emit("createRoom", mode, customConfig);
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
