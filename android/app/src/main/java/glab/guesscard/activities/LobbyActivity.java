package glab.guesscard.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import glab.guesscard.ModernFButton;
import glab.guesscard.R;
import glab.guesscard.adapters.ChatAdapter;
import glab.guesscard.adapters.LobbyPlayerAdapter;
import glab.guesscard.firebase.FirebaseManager;
import glab.guesscard.models.ChatMessage;
import glab.guesscard.models.GameMode;
import glab.guesscard.models.RoomInfo;
import glab.guesscard.socket.GameSocketClient;
import glab.guesscard.socket.GameSocketListener;
import glab.guesscard.views.PlayerAvatarView;

/**
 * Lobby Activity: shows online room players (via Firebase RTDB presence),
 * live chat, and ready toggle. Transitions to GameActivity on start.
 */
public class LobbyActivity extends BaseActivity implements GameSocketListener {
    public static final String EXTRA_MODE = "extra_mode";
    public static final String EXTRA_ROOM_CODE = "extra_room_code";

    private GameSocketClient socket;
    private FirebaseManager firebaseManager;
    private RoomInfo room;
    private String currentRoomId;

    private TextView roomCodeText;
    private TextView roomModeText;
    private LinearLayout avatarContainer;
    private RecyclerView playersList;
    private RecyclerView chatList;
    private LobbyPlayerAdapter playerAdapter;
    private ChatAdapter chatAdapter;
    private EditText chatInput;
    private ModernFButton readyButton;
    private boolean isLocallyReady = false;
    private DatabaseReference roomPlayersRef;
    private ChildEventListener playersListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby);

        socket = container().getSocketClient();
        firebaseManager = container().getFirebaseManager();
        socket.setListener(this);

        roomCodeText = findViewById(R.id.roomCodeText);
        roomModeText = findViewById(R.id.roomModeText);
        avatarContainer = findViewById(R.id.playerAvatarContainer);
        playersList = findViewById(R.id.playersList);
        chatList = findViewById(R.id.chatList);
        chatInput = findViewById(R.id.chatInput);
        readyButton = findViewById(R.id.readyButton);

        playerAdapter = new LobbyPlayerAdapter();
        playersList.setLayoutManager(new LinearLayoutManager(this));
        playersList.setAdapter(playerAdapter);

        chatAdapter = new ChatAdapter();
        if (chatList != null) {
            chatList.setLayoutManager(new LinearLayoutManager(this));
            chatList.setAdapter(chatAdapter);
        }

        readyButton.setOnClickListener(v -> toggleReady());
        View send = findViewById(R.id.sendChatButton);
        if (send != null) send.setOnClickListener(v -> sendChat());
        View leave = findViewById(R.id.leaveRoomButton);
        if (leave != null) leave.setOnClickListener(v -> leaveRoom());

        if (!socket.isConnected()) socket.connect();

        String mode = getIntent().getStringExtra(EXTRA_MODE);
        String code = getIntent().getStringExtra(EXTRA_ROOM_CODE);

        if (code != null) {
            socket.joinRoomByCode(code);
        } else if (mode != null) {
            socket.createRoom(mode, null);
        } else {
            socket.createRoom("ANIMALS", null);
        }
    }

    private void toggleReady() {
        isLocallyReady = !isLocallyReady;
        readyButton.setText(isLocallyReady ? "✓ Ready!" : "I'm Ready");

        if (socket != null && socket.isConnected() && room != null) {
            for (RoomInfo.LobbyPlayer p : room.players) {
                if (p.userId != null && p.userId.equals(prefs().getUserId())) {
                    p.isReady = isLocallyReady;
                    socket.setReady(isLocallyReady);
                    // Update RTDB presence too
                    if (currentRoomId != null) {
                        firebaseManager.getRoomRef(currentRoomId)
                                .child("players")
                                .child(p.userId)
                                .child("ready")
                                .setValue(isLocallyReady);
                    }
                    break;
                }
            }
        } else {
            // Offline practice — start immediately
            String modeStr = getIntent().getStringExtra(EXTRA_MODE);
            onGameStart(modeStr != null ? modeStr : "ANIMALS", 60_000L);
        }
    }

    private void sendChat() {
        if (chatInput == null) return;
        String text = chatInput.getText().toString().trim();
        if (text.isEmpty() || room == null) return;
        socket.sendChat(room.roomId, text);
        chatInput.setText("");
    }

    private void leaveRoom() {
        // Remove from Firebase RTDB presence
        FirebaseUser user = firebaseManager.getCurrentUser();
        if (user != null && currentRoomId != null) {
            firebaseManager.leaveRoom(currentRoomId, user.getUid());
        }
        socket.leaveRoom();
        finish();
    }

    /** Listen to Firebase RTDB player presence for this room. */
    private void listenToRoomPresence(String roomId) {
        if (roomPlayersRef != null && playersListener != null) {
            roomPlayersRef.removeEventListener(playersListener);
        }
        roomPlayersRef = firebaseManager.getRoomRef(roomId).child("players");
        playersListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String prev) {
                String name = snapshot.child("displayName").getValue(String.class);
                if (name != null) {
                    chatAdapter.add(new ChatMessage("System", name + " joined the room 👋", System.currentTimeMillis()));
                }
            }
            @Override
            public void onChildChanged(DataSnapshot s, String p) {}
            @Override
            public void onChildRemoved(DataSnapshot snapshot) {
                String name = snapshot.child("displayName").getValue(String.class);
                if (name != null) {
                    chatAdapter.add(new ChatMessage("System", name + " left the room", System.currentTimeMillis()));
                }
            }
            @Override
            public void onChildMoved(DataSnapshot s, String p) {}
            @Override
            public void onCancelled(DatabaseError e) {}
        };
        roomPlayersRef.addChildEventListener(playersListener);

        // Join RTDB room
        FirebaseUser user = firebaseManager.getCurrentUser();
        if (user != null) {
            String displayName = user.getDisplayName() != null ? user.getDisplayName()
                    : prefs().getUsername();
            firebaseManager.joinRoom(roomId, user.getUid(), displayName);
        }
    }

    @Override
    public void onRoomJoined(RoomInfo room) {
        this.room = room;
        this.currentRoomId = room.roomId;
        runOnUiThread(() -> {
            updateRoomUI(room);
            listenToRoomPresence(room.roomId);
        });
    }

    @Override
    public void onRoomUpdated(RoomInfo room) {
        this.room = room;
        runOnUiThread(() -> updateRoomUI(room));
    }

    private void updateRoomUI(RoomInfo room) {
        if (room == null) return;
        if (roomCodeText != null) roomCodeText.setText(room.code != null ? room.code : "------");
        if (roomModeText != null)
            roomModeText.setText((room.mode != null ? room.mode : "ANIMALS") + " Mode (" + room.players.size() + "/5 Players)");
        playerAdapter.submitList(room.players);
        renderAvatarSlots(room);
    }

    private void renderAvatarSlots(RoomInfo room) {
        if (avatarContainer == null) return;
        avatarContainer.removeAllViews();
        for (RoomInfo.LobbyPlayer p : room.players) {
            PlayerAvatarView avatar = new PlayerAvatarView(this);
            avatar.setPlayerData(p.username, p.score, p.isReady, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            avatarContainer.addView(avatar, lp);
        }
    }

    @Override
    public void onChatMessage(ChatMessage message) {
        runOnUiThread(() -> chatAdapter.add(message));
    }

    @Override
    public void onGameStart(String mode, long durationMillis) {
        GameMode gameMode = GameMode.ANIMALS;
        try { gameMode = GameMode.valueOf(mode.toUpperCase()); } catch (Exception ignored) {}
        startActivity(GameActivity.intent(this, gameMode, currentRoomId != null ? currentRoomId : "offline", durationMillis));
        finish();
    }

    @Override
    protected void onDestroy() {
        if (roomPlayersRef != null && playersListener != null) {
            roomPlayersRef.removeEventListener(playersListener);
        }
        socket.setListener(null);
        super.onDestroy();
    }
}
