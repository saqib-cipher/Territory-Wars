package com.territorywars.socket;
import com.territorywars.models.ChatMessage;
import com.territorywars.models.RoomInfo;
/**
* Receives asynchronous socket events. Fragments/Activities implement this and
* forward results to their ViewModel.
*/
public interface GameSocketListener {
default void onConnected() {
}
default void onDisconnected() {
}
default void onError(String message) {
}
default void onRoomJoined(RoomInfo room) {
}
default void onRoomUpdated(RoomInfo room) {
}
default void onChatMessage(ChatMessage message) {
}
default void onGameStart(String mapId, long durationMillis) {
}
default void onGameEnd(com.territorywars.models.MatchResult result) {
}
default void onScoreUpdate(int rank, int tilesCaptured, int totalScore) {
}
}
