package glab.guesscard.socket;

import glab.guesscard.models.ChatMessage;
import glab.guesscard.models.RoomInfo;

/**
 * Receives asynchronous socket events for Guess the Card party game.
 */
public interface GameSocketListener {
    default void onConnected() {}
    default void onDisconnected() {}
    default void onError(String message) {}
    default void onRoomJoined(RoomInfo room) {}
    default void onRoomUpdated(RoomInfo room) {}
    default void onChatMessage(ChatMessage message) {}
    default void onGameStart(String mode, long durationMillis) {}
    default void onGameEnd(glab.guesscard.models.MatchResult result) {}
    default void onPublicRoomsList(java.util.List<RoomInfo> rooms) {}

    // Guess the Card real-time event callbacks
    /** Called when a player asks a question (online mode). */
    default void onQuestionAsked(String question, String askerName) {}
    /** Called when an answerer responds YES/NO (online mode). */
    default void onAnswerGiven(String question, String answer, String answererName) {}
    /** Called when a guess is submitted and result is known. */
    default void onGuessResult(String guessedBy, String guess, boolean isCorrect, int scoreAwarded, String cardAnswer) {}
    /** Engine compat: offline only (one-arg guess result). */
    default void onTurnStarted(String nextTurnPlayerId, int currentRound) {}
    default void onRoomInviteReceived(String roomId, String code, String senderId, String senderName, String senderAvatar, String mode) {}
    default void onFriendRequestReceived(String senderId, String senderName, String senderAvatar) {}
    /** Called when someone accepted your friend request (keys are the acceptor). */
    default void onFriendRequestAccepted(String acceptorId, String acceptorName, String acceptorAvatar) {}
}

