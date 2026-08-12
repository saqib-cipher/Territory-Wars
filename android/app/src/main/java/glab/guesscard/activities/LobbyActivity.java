package glab.guesscard.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;

import glab.guesscard.ModernFButton;
import glab.guesscard.R;
import glab.guesscard.adapters.LobbyPlayerAdapter;
import glab.guesscard.firebase.FirebaseManager;
import glab.guesscard.models.ChatMessage;
import glab.guesscard.models.GameMode;
import glab.guesscard.models.RoomInfo;
import glab.guesscard.socket.GameSocketClient;
import glab.guesscard.socket.GameSocketListener;
import glab.guesscard.views.PlayerAvatarView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lobby Activity: shows online room players with custom item_lobby_player layout,
 * friend room invitations, ready toggle, and auto-start 5s countdown timer.
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
    private RecyclerView rvInviteFriends;
    private LobbyPlayerAdapter playerAdapter;
    private ModernFButton readyButton;
    private boolean isLocallyReady = false;
    private DatabaseReference roomPlayersRef;
    private ChildEventListener playersListener;

    private android.os.CountDownTimer startCountdownTimer;
    private boolean isCountingDown = false;
    private ValueEventListener inviteListener;

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
        rvInviteFriends = findViewById(R.id.rvInviteFriends);
        readyButton = findViewById(R.id.readyButton);

        View codeContainer = findViewById(R.id.roomCodeContainer);
        if (codeContainer != null) {
            codeContainer.setOnClickListener(v -> {
                String code = roomCodeText != null ? roomCodeText.getText().toString() : "";
                if (!code.isEmpty() && !code.equals("------")) {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("Room Code", code);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(this, "Copied Room Code: " + code, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        playerAdapter = new LobbyPlayerAdapter();
        if (playersList != null) {
            playersList.setLayoutManager(new LinearLayoutManager(this));
            playersList.setAdapter(playerAdapter);
        }

        if (rvInviteFriends != null) {
            rvInviteFriends.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            loadFriendsToInvite();
        }

        readyButton.setOnClickListener(v -> toggleReady());
        View leave = findViewById(R.id.leaveRoomButton);
        if (leave != null) leave.setOnClickListener(v -> leaveRoom());

        ModernFButton btnStart = findViewById(R.id.btnStartMatch);
        if (btnStart != null) {
            btnStart.setOnClickListener(v -> {
                if (socket != null && socket.isConnected()) {
                    socket.startGame();
                } else {
                    String modeStr = getIntent().getStringExtra(EXTRA_MODE);
                    onGameStart(modeStr != null ? modeStr : "ANIMALS", 60_000L);
                }
            });
        }

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

        setupInviteListener();
    }

    private void setupInviteListener() {
        String uid = prefs().getUserId();
        if (uid != null) {
            inviteListener = firebaseManager.listenForRoomInvites(uid, (rId, code, sender) -> runOnUiThread(() -> {
                new MaterialAlertDialogBuilder(LobbyActivity.this)
                        .setTitle("Room Invitation")
                        .setMessage(sender + " invited you to join their game room (" + code + ").")
                        .setPositiveButton("Join", (d, w) -> {
                            Intent intent = new Intent(LobbyActivity.this, LobbyActivity.class);
                            intent.putExtra(EXTRA_ROOM_CODE, code);
                            startActivity(intent);
                            finish();
                        })
                        .setNegativeButton("Ignore", null)
                        .show();
            }));
        }
    }

    private void loadFriendsToInvite() {
        String uid = prefs().getUserId();
        if (uid == null || firebaseManager == null) return;
        firebaseManager.getFriends(uid, list -> runOnUiThread(() -> {
            if (rvInviteFriends == null) return;
            if (list == null || list.isEmpty()) {
                View header = findViewById(R.id.tvInviteFriendsHeader);
                if (header != null) header.setVisibility(View.GONE);
                rvInviteFriends.setVisibility(View.GONE);
            } else {
                View header = findViewById(R.id.tvInviteFriendsHeader);
                if (header != null) header.setVisibility(View.VISIBLE);
                rvInviteFriends.setVisibility(View.VISIBLE);
                rvInviteFriends.setAdapter(new InviteFriendsAdapter(list));
            }
        }));
    }

    private void toggleReady() {
        isLocallyReady = !isLocallyReady;
        readyButton.setText(isLocallyReady ? "Ready!" : "I'm Ready");

        if (socket != null && socket.isConnected() && room != null) {
            for (RoomInfo.LobbyPlayer p : room.players) {
                if (p.userId != null && p.userId.equals(prefs().getUserId())) {
                    p.isReady = isLocallyReady;
                    break;
                }
            }
            socket.setReady(isLocallyReady);
            updateRoomUI(room);
        }
    }

    private void leaveRoom() {
        if (socket != null && socket.isConnected()) {
            socket.leaveRoom();
        }
        removePresence();
        cancelCountdown();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelCountdown();
        removePresence();
    }

    private void listenToRoomPresence(String roomId) {
        if (roomId == null) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = user != null ? user.getUid() : prefs().getUserId();
        String name = user != null && user.getDisplayName() != null ? user.getDisplayName() : prefs().getUsername();
        String avatarFile = prefs().getAvatarFileName();

        if (uid != null) {
            Map<String, Object> pMap = new HashMap<>();
            pMap.put("uid", uid);
            pMap.put("displayName", name);
            pMap.put("avatarFileName", avatarFile);
            pMap.put("ready", isLocallyReady);
            pMap.put("joinedAt", ServerValue.TIMESTAMP);
            firebaseManager.getRoomRef(roomId).child("players").child(uid).setValue(pMap);
        }

        roomPlayersRef = firebaseManager.getRoomRef(roomId).child("players");
        playersListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                refreshRoomPresence(roomId);
            }
            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                refreshRoomPresence(roomId);
            }
            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                refreshRoomPresence(roomId);
            }
            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        roomPlayersRef.addChildEventListener(playersListener);
    }

    private void refreshRoomPresence(String roomId) {
        firebaseManager.getRoomRef(roomId).child("players").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<RoomInfo.LobbyPlayer> list = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String pUid = child.child("uid").getValue(String.class);
                    String name = child.child("displayName").getValue(String.class);
                    Boolean ready = child.child("ready").getValue(Boolean.class);
                    RoomInfo.LobbyPlayer p = new RoomInfo.LobbyPlayer();
                    p.userId = pUid;
                    p.username = name != null ? name : "Player";
                    p.isReady = Boolean.TRUE.equals(ready);
                    list.add(p);
                }
                if (room != null) {
                    room.players = list;
                    updateRoomUI(room);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void removePresence() {
        if (currentRoomId != null && roomPlayersRef != null && playersListener != null) {
            roomPlayersRef.removeEventListener(playersListener);
            String uid = prefs().getUserId();
            if (uid != null) roomPlayersRef.child(uid).removeValue();
        }
    }

    // ── GAME SOCKET LISTENER ───────────────────────────────────────────────

    @Override
    public void onConnected() {
        String mode = getIntent().getStringExtra(EXTRA_MODE);
        String code = getIntent().getStringExtra(EXTRA_ROOM_CODE);
        if (code != null) {
            socket.joinRoomByCode(code);
        } else {
            socket.createRoom(mode != null ? mode : "ANIMALS", null);
        }
    }

    @Override public void onDisconnected() {}
    @Override public void onChatMessage(ChatMessage message) {}
    @Override public void onTurnStarted(String nextTurnPlayerId, int currentRound) {}
    @Override public void onQuestionAsked(String question, String askerName) {}
    @Override public void onAnswerGiven(String question, String answer, String answererName) {}
    @Override public void onGuessResult(String guessedBy, String guess, boolean isCorrect, int scoreAwarded, String cardAnswer) {}
    @Override public void onGameEnd(glab.guesscard.models.MatchResult result) {}

    @Override
    public void onGameStart(String mode, long durationMs) {
        runOnUiThread(() -> {
            cancelCountdown();
            String rId = currentRoomId != null ? currentRoomId : "online_room";
            startActivity(GameActivity.intent(this, GameMode.valueOf(mode), rId, durationMs));
            finish();
        });
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
        ensureLocalPlayerInList(room);

        if (room.code == null || room.code.isEmpty() || room.code.startsWith("GC-")) {
            room.code = String.valueOf(100000 + new java.util.Random().nextInt(900000));
        }

        if (roomCodeText != null) roomCodeText.setText(room.code);
        if (roomModeText != null)
            roomModeText.setText((room.mode != null ? room.mode : "ANIMALS") + " Mode (" + room.players.size() + "/5 Players)");

        TextView tvStatus = findViewById(R.id.tvLobbyStatus);
        ModernFButton btnStart = findViewById(R.id.btnStartMatch);
        int count = room.players != null ? room.players.size() : 1;

        if (readyButton != null) {
            readyButton.setVisibility(count >= 2 ? View.VISIBLE : View.GONE);
        }
        if (btnStart != null) {
            btnStart.setVisibility(count >= 2 ? View.VISIBLE : View.GONE);
        }

        if (tvStatus != null && !isCountingDown) {
            if (count < 2) {
                tvStatus.setText("Waiting for other players to join (Need 2+ players)...");
                tvStatus.setTextColor(android.graphics.Color.parseColor("#F59E0B"));
            } else {
                tvStatus.setText(count + " Players Connected. Tap Ready to start match.");
                tvStatus.setTextColor(android.graphics.Color.parseColor("#38BDF8"));
            }
        }

        playerAdapter.submitList(room.players);
        renderAvatarSlots(room);
        checkAutoStart(room);
    }

    private void ensureLocalPlayerInList(RoomInfo room) {
        if (room == null) return;
        if (room.players == null) room.players = new ArrayList<>();
        String myUid = prefs().getUserId();
        String myName = prefs().getUsername();
        boolean found = false;
        for (RoomInfo.LobbyPlayer p : room.players) {
            if (p.userId != null && p.userId.equals(myUid)) {
                found = true;
                break;
            }
        }
        if (!found && myUid != null) {
            RoomInfo.LobbyPlayer self = new RoomInfo.LobbyPlayer();
            self.userId = myUid;
            self.username = myName != null ? myName : "Player";
            self.isReady = isLocallyReady;
            room.players.add(0, self);
        }
    }

    private void checkAutoStart(RoomInfo room) {
        if (room == null || room.players == null || room.players.size() < 2) {
            cancelCountdown();
            return;
        }

        boolean allReady = true;
        for (RoomInfo.LobbyPlayer p : room.players) {
            if (!p.isReady) {
                allReady = false;
                break;
            }
        }

        if (allReady) {
            if (!isCountingDown) {
                isCountingDown = true;
                TextView tvStatus = findViewById(R.id.tvLobbyStatus);
                startCountdownTimer = new android.os.CountDownTimer(5000, 1000) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                        long secs = (millisUntilFinished / 1000) + 1;
                        if (tvStatus != null) {
                            tvStatus.setText("All players ready! Starting match in " + secs + "s...");
                            tvStatus.setTextColor(android.graphics.Color.parseColor("#10B981"));
                        }
                    }

                    @Override
                    public void onFinish() {
                        isCountingDown = false;
                        if (socket != null && socket.isConnected()) {
                            socket.startGame();
                        } else {
                            String modeStr = getIntent().getStringExtra(EXTRA_MODE);
                            onGameStart(modeStr != null ? modeStr : "ANIMALS", 60_000L);
                        }
                    }
                }.start();
            }
        } else {
            cancelCountdown();
        }
    }

    private void cancelCountdown() {
        if (startCountdownTimer != null) {
            startCountdownTimer.cancel();
            startCountdownTimer = null;
        }
        isCountingDown = false;
    }

    private void renderAvatarSlots(RoomInfo room) {
        if (avatarContainer == null || room == null || room.players == null) return;
        avatarContainer.removeAllViews();
        String selfUid = prefs().getUserId();
        String selfAvatarFile = prefs().getAvatarFileName();

        for (RoomInfo.LobbyPlayer p : room.players) {
            PlayerAvatarView avatar = new PlayerAvatarView(this);
            avatar.setPlayerData(p.username, p.score, 1, p.isReady, false);
            boolean isSelf = p.userId != null && p.userId.equals(selfUid);
            String avatarFile = isSelf ? selfAvatarFile : "avatar_01.png";
            avatar.setAvatarBitmap(glab.guesscard.utils.AvatarManager.getInstance().getAvatarByName(this, avatarFile));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            avatarContainer.addView(avatar, lp);
        }
    }

    // ── INVITE FRIENDS ADAPTER ──────────────────────────────────────────────

    private class InviteFriendsAdapter extends RecyclerView.Adapter<InviteFriendsAdapter.VH> {
        private final List<Map<String, Object>> friends;

        InviteFriendsAdapter(List<Map<String, Object>> friends) {
            this.friends = friends;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ModernFButton btn = new ModernFButton(parent.getContext());
            btn.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            btn.setTextSize(11.0f);
            return new VH(btn);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Map<String, Object> friend = friends.get(position);
            String fUid = (String) friend.getOrDefault("uid", "");
            String fName = (String) friend.getOrDefault("displayName", "Friend");

            holder.button.setText("Invite " + fName);
            holder.button.setOnClickListener(v -> {
                if (currentRoomId != null && fUid != null) {
                    String code = roomCodeText != null ? roomCodeText.getText().toString() : "";
                    String myName = prefs().getUsername();
                    firebaseManager.sendRoomInvite(fUid, currentRoomId, code, myName);
                    Toast.makeText(LobbyActivity.this, "Invitation sent to " + fName, Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return friends.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ModernFButton button;
            VH(View itemView) {
                super(itemView);
                button = (ModernFButton) itemView;
            }
        }
    }
}
