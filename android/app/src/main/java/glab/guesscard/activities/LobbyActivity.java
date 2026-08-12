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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import glab.guesscard.ModernFButton;
import glab.guesscard.R;
import glab.guesscard.adapters.LobbyPlayerAdapter;
import glab.guesscard.firebase.FirebaseManager;
import glab.guesscard.models.ChatMessage;
import glab.guesscard.models.GameMode;
import glab.guesscard.models.MatchResult;
import glab.guesscard.models.RoomInfo;
import glab.guesscard.socket.GameSocketClient;
import glab.guesscard.socket.GameSocketListener;
import glab.guesscard.views.PlayerAvatarView;

/**
 * Lobby Activity: shows online room players with avatars, unique room code,
 * ready toggle, invite friends, and auto-start countdown.
 * Uses host UID as persistent room key so re-login reconnects to same room.
 */
public class LobbyActivity extends BaseActivity implements GameSocketListener {
    public static final String EXTRA_MODE = "extra_mode";
    public static final String EXTRA_ROOM_CODE = "extra_room_code";

    private GameSocketClient socket;
    private FirebaseManager firebaseManager;
    private RoomInfo room;
    private String currentRoomId;

    private TextView roomCodeText;
    private TextView roomUniqueIdText;
    private TextView roomModeText;
    private LinearLayout avatarContainer;
    private RecyclerView playersList;
    private RecyclerView rvInviteFriends;
    private LobbyPlayerAdapter playerAdapter;
    private ModernFButton readyButton;
    private boolean isLocallyReady = false;
    private boolean isHost = false;

    private android.os.CountDownTimer startCountdownTimer;
    private boolean isCountingDown = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby);

        socket = container().getSocketClient();
        firebaseManager = container().getFirebaseManager();
        socket.setListener(this);

        roomCodeText = findViewById(R.id.roomCodeText);
        roomUniqueIdText = findViewById(R.id.roomUniqueIdText);
        roomModeText = findViewById(R.id.roomModeText);
        avatarContainer = findViewById(R.id.playerAvatarContainer);
        playersList = findViewById(R.id.playersList);
        rvInviteFriends = findViewById(R.id.rvInviteFriends);
        readyButton = findViewById(R.id.readyButton);

        // Copy room code on tap
        View codeContainer = findViewById(R.id.roomCodeContainer);
        if (codeContainer != null) {
            codeContainer.setOnClickListener(v -> {
                String code = room != null && room.code != null ? room.code : 
                    (roomCodeText != null ? roomCodeText.getText().toString() : "");
                if (!code.isEmpty() && !code.equals("------")) {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) 
                        getSystemService(Context.CLIPBOARD_SERVICE);
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
                }
            });
        }

        if (!socket.isConnected()) socket.connect();

        String mode = getIntent().getStringExtra(EXTRA_MODE);
        String code = getIntent().getStringExtra(EXTRA_ROOM_CODE);

        if (code != null && !code.isEmpty()) {
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
        if (uid != null && firebaseManager != null) {
            firebaseManager.listenForRoomInvites(uid, (rId, code, sender) -> runOnUiThread(() -> {
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
        cancelCountdown();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelCountdown();
        if (socket != null) socket.setListener(null);
    }

    // ── GAME SOCKET LISTENER ───────────────────────────────────────────────

    @Override
    public void onConnected() {
        String mode = getIntent().getStringExtra(EXTRA_MODE);
        String code = getIntent().getStringExtra(EXTRA_ROOM_CODE);
        if (code != null && !code.isEmpty()) {
            socket.joinRoomByCode(code);
        } else {
            socket.createRoom(mode != null ? mode : "ANIMALS", null);
        }
    }

    @Override public void onDisconnected() {
        runOnUiThread(() -> {
            Toast.makeText(this, "Connection lost. Reconnecting...", Toast.LENGTH_SHORT).show();
        });
    }

    @Override public void onChatMessage(ChatMessage message) {}
    
    @Override public void onTurnStarted(String nextTurnPlayerId, int currentRound, boolean switchedPositions) {}
    
    @Override public void onQuestionAsked(String question, String askerName) {}
    
    @Override public void onAnswerGiven(String question, String answer, String answererName) {}
    
    @Override public void onGuessResult(String guessedBy, String guessedByName, String guess, 
                                         boolean isCorrect, int scoreAwarded, String cardAnswer, String cardCategory) {}

    @Override
    public void onGameEnd(MatchResult result) {}

    @Override
    public void onPlayerJoined(String userId, String username, String avatarId) {
        runOnUiThread(() -> {
            Toast.makeText(this, username + " joined!", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onPlayerLeft(String userId, String username) {
        runOnUiThread(() -> {
            Toast.makeText(this, username + " left the room", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onGameStart(String mode, long durationMs) {
        runOnUiThread(() -> {
            cancelCountdown();
            String rId = currentRoomId != null ? currentRoomId : "online_room";
            GameMode gm;
            try { gm = GameMode.valueOf(mode); } catch (Exception e) { gm = GameMode.ANIMALS; }
            startActivity(GameActivity.intent(this, gm, rId, durationMs));
            finish();
        });
    }

    @Override
    public void onRoomJoined(RoomInfo room) {
        this.room = room;
        this.currentRoomId = room.roomId;
        this.isHost = room.hostId != null && room.hostId.equals(prefs().getUserId());
        runOnUiThread(() -> updateRoomUI(room));
    }

    @Override
    public void onRoomUpdated(RoomInfo room) {
        this.room = room;
        this.isHost = room.hostId != null && room.hostId.equals(prefs().getUserId());
        runOnUiThread(() -> updateRoomUI(room));
    }

    private void updateRoomUI(RoomInfo room) {
        if (room == null) return;
        ensureLocalPlayerInList(room);

        // Show room code and unique identifier
        if (roomCodeText != null) {
            String code = room.code;
            if (code == null || code.isEmpty()) {
                code = room.uniqueCode != null ? room.uniqueCode : room.roomId;
            }
            roomCodeText.setText(code);
        }
        if (roomUniqueIdText != null) {
            String display = room.uniqueCode != null ? room.uniqueCode : 
                           (room.hostId != null ? "Host: " + room.hostId.substring(0, Math.min(8, room.hostId.length())) : "");
            roomUniqueIdText.setText(display);
            roomUniqueIdText.setVisibility(View.VISIBLE);
        }
        if (roomModeText != null)
            roomModeText.setText((room.mode != null ? room.mode : "ANIMALS") + 
                " Mode (" + room.players.size() + "/" + room.maxPlayers + " Players)");

        TextView tvStatus = findViewById(R.id.tvLobbyStatus);
        ModernFButton btnStart = findViewById(R.id.btnStartMatch);
        int count = room.players != null ? room.players.size() : 1;

        if (readyButton != null) {
            readyButton.setVisibility(count >= 2 ? View.VISIBLE : View.GONE);
        }
        if (btnStart != null) {
            // Only host sees start button
            btnStart.setVisibility(isHost && count >= 2 ? View.VISIBLE : View.GONE);
        }

        if (tvStatus != null && !isCountingDown) {
            if (count < 2) {
                tvStatus.setText("Waiting for other players to join (Need " + 
                    room.maxPlayers + " players)... Share the room code above!");
                tvStatus.setTextColor(android.graphics.Color.parseColor("#F59E0B"));
            } else {
                int readyCount = 0;
                for (RoomInfo.LobbyPlayer p : room.players) {
                    if (p.isReady) readyCount++;
                }
                tvStatus.setText(count + " Players (" + readyCount + " ready). Tap Ready to start!");
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
            self.avatarId = prefs().getAvatarFileName();
            self.isReady = isLocallyReady;
            self.isHost = isHost;
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

        for (RoomInfo.LobbyPlayer p : room.players) {
            PlayerAvatarView avatar = new PlayerAvatarView(this);
            avatar.setPlayerData(p.username, p.score, 1, p.isReady, false);
            boolean isSelf = p.userId != null && p.userId.equals(selfUid);
            String avatarFile = p.avatarId != null ? p.avatarId : 
                (isSelf ? prefs().getAvatarFileName() : "avatar_01.png");
            avatar.setAvatarBitmap(glab.guesscard.utils.AvatarManager.getInstance()
                .getAvatarByName(this, avatarFile));
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
                if (currentRoomId != null && fUid != null && room != null) {
                    String code = room.code;
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
