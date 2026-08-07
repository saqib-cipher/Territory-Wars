package com.territorywars.activities;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.territorywars.R;
import com.territorywars.adapters.ChatAdapter;
import com.territorywars.adapters.LobbyPlayerAdapter;
import com.territorywars.models.ChatMessage;
import com.territorywars.models.GameMode;
import com.territorywars.models.RoomInfo;
import com.territorywars.socket.GameSocketClient;
import com.territorywars.socket.GameSocketListener;
import java.util.ArrayList;
/**
* Lobby: room code, player list, ready toggle, chat, invite.
*/
public class LobbyActivity extends BaseActivity implements GameSocketListener {
public static final String EXTRA_MODE = "extra_mode";
public static final String EXTRA_ROOM_CODE = "extra_room_code";
private GameSocketClient socket;
private RoomInfo room;
private TextView roomCodeText;
private RecyclerView playersList;
private LobbyPlayerAdapter playerAdapter;
private ChatAdapter chatAdapter;
private EditText chatInput;
private MaterialButton readyButton;
@Override
protected void onCreate(@Nullable Bundle savedInstanceState) {
super.onCreate(savedInstanceState);
setContentView(R.layout.activity_lobby);
socket = container().getSocketClient();
socket.setListener(this);
roomCodeText = findViewById(R.id.roomCodeText);
playersList = findViewById(R.id.playersList);
chatInput = findViewById(R.id.chatInput);
readyButton = findViewById(R.id.readyButton);
playerAdapter = new LobbyPlayerAdapter();
playersList.setLayoutManager(new LinearLayoutManager(this));
playersList.setAdapter(playerAdapter);
chatAdapter = new ChatAdapter();
readyButton.setOnClickListener(v -> toggleReady());
findViewById(R.id.sendChatButton).setOnClickListener(v -> sendChat());
findViewById(R.id.leaveRoomButton).setOnClickListener(v -> leaveRoom());
// join
if (!socket.isConnected()) socket.connect();
String mode = getIntent().getStringExtra(EXTRA_MODE);
String code = getIntent().getStringExtra(EXTRA_ROOM_CODE);
if (code != null) {
socket.joinRoomByCode(code);
} else if (mode != null) {
socket.createRoom(mode, "classic_plains");
}
}
private void toggleReady() {
if (room == null) return;
for (RoomInfo.LobbyPlayer p : room.players) {
if (p.userId.equals(prefs().getUserId())) {
socket.setReady(!p.isReady);
return;
}
}
}
private void sendChat() {
String text = chatInput.getText().toString().trim();
if (text.isEmpty() || room == null) return;
socket.sendChat(room.roomId, text);
chatInput.setText("");
}
private void leaveRoom() {
socket.leaveRoom();
finish();
}
// ---- Socket events ----
@Override
public void onRoomJoined(RoomInfo room) {
this.room = room;
roomCodeText.setText(room.code);
playerAdapter.submitList(room.players);
updateReadyState();
}
@Override
public void onRoomUpdated(RoomInfo room) {
this.room = room;
playerAdapter.submitList(room.players);
updateReadyState();
}
@Override
public void onChatMessage(ChatMessage message) {
chatAdapter.add(message);
}
@Override
public void onGameStart(String mapId, long durationMillis) {
startActivity(GameActivity.intent(this, GameMode.CLASSIC, mapId, durationMillis));
finish();
}
private void updateReadyState() {
if (room == null) return;
for (RoomInfo.LobbyPlayer p : room.players) {
if (p.userId.equals(prefs().getUserId())) {
readyButton.setText(p.isReady ? "Ready ✓" : "Ready");
return;
}
}
}
@Override
protected void onDestroy() {
socket.setListener(null);
super.onDestroy();
}
}
