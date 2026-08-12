package glab.guesscard.socket;

import glab.guesscard.models.ChatMessage;
import glab.guesscard.models.MatchResult;
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
    /** Full game end with standings, winner, and match history. */
    default void onGameEnd(MatchResult result) {}

    // Guess the Card real-time event callbacks
    /** Called when a player asks a question (online mode). */
    default void onQuestionAsked(String question, String askerName) {}
    /** Called when an answerer responds YES/NO (online mode). */
    default void onAnswerGiven(String question, String answer, String answererName) {}
    /** Called when a guess is submitted and result is known (includes card answer for display). */
    default void onGuessResult(String guessedBy, String guessedByName, String guess, boolean isCorrect, int scoreAwarded, String cardAnswer, String cardCategory) {}
    /** Engine compat: offline only (one-arg guess result). */
    default void onGuessResult(boolean isCorrect, String guess, int scoreAwarded, int totalScore) {}
    /** Called when turn switches (position rotation). */
    default void onTurnStarted(String nextTurnPlayerId, int currentRound, boolean switchedPositions) {}
    /** Player joined the room. */
    default void onPlayerJoined(String userId, String username, String avatarId) {}
    /** Player left the room. */
    default void onPlayerLeft(String userId, String username) {}
}

