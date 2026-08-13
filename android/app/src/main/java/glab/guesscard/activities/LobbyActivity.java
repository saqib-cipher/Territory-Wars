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
    public static final String EXTRA_ROOM_ID = "extra_room_id";
    public static final String EXTRA_ROOM_NAME = "extra_room_name";

    private GameSocketClient socket;
    private FirebaseManager firebaseManager;
    private RoomInfo room;
    private String currentRoomId;
    private String currentRoomCode;   // unique join code from /rooms/{hostUid}/code
    private String currentRoomName;

    private TextView roomCodeText;
    private TextView roomNameText;
    private TextView roomModeText;
    private LinearLayout avatarContainer;
    private RecyclerView playersList;
    private RecyclerView rvInviteFriends;
    private LobbyPlayerAdapter playerAdapter;
    private ModernFButton readyButton;
    private boolean isLocallyReady = false;
    private DatabaseReference roomPlayersRef;
    private ChildEventListener playersListener;
    private ValueEventListener statusListener;
    private ValueEventListener codeListener;
    private ValueEventListener hostUidListener;
    private ValueEventListener nameListener;

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
        roomNameText = findViewById(R.id.roomNameText);
        roomModeText = findViewById(R.id.roomModeText);
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

        initRoomFromIntent();
        setupInviteListener();
    }

    /**
     * Resolve the room from intent extras.
     * Room root node is the HOST UID: /rooms/{hostUid} with its unique code at /rooms/{hostUid}/code.
     */
    private void initRoomFromIntent() {
        final String mode = getIntent().getStringExtra(EXTRA_MODE);
        String roomIdExtra = getIntent().getStringExtra(EXTRA_ROOM_ID);
        String codeExtra = getIntent().getStringExtra(EXTRA_ROOM_CODE);
        String roomNameExtra = getIntent().getStringExtra(EXTRA_ROOM_NAME);
        currentRoomName = roomNameExtra;

        if (roomIdExtra != null && !roomIdExtra.isEmpty()) {
            // Already know the host-uid room node (host created it or invite carried it).
            currentRoomId = roomIdExtra;
            currentRoomCode = codeExtra != null ? codeExtra : null;
            if (roomCodeText != null && currentRoomCode != null) roomCodeText.setText(currentRoomCode);
            listenToRoomPresence(currentRoomId);
            if (socket != null && socket.isConnected()) {
                if (currentRoomCode != null && !currentRoomCode.isEmpty()) socket.joinRoomByCode(currentRoomCode);
            }
        } else if (codeExtra != null && !codeExtra.isEmpty()) {
            // Resolve join code -> host UID root node, then verify a slot is available.
            firebaseManager.findRoomByCode(codeExtra, (rId, rCode, rMode) -> runOnUiThread(() -> {
                if (rId == null) {
                    Toast.makeText(LobbyActivity.this, "Room not found. The code may be invalid or the room was closed.", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                firebaseManager.hasRoomSlot(rId, hasSlot -> runOnUiThread(() -> {
                    if (isFinishing()) return;
                    if (hasSlot == null || !hasSlot) {
                        Toast.makeText(LobbyActivity.this, "Room is full or already in progress. Try another room.", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    currentRoomId = rId;
                    currentRoomCode = rCode != null ? rCode : codeExtra;
                    if (room == null) {
                        room = new RoomInfo();
                        room.roomId = currentRoomId;
                    }
                    room.code = currentRoomCode;
                    room.mode = mode != null ? mode : (rMode != null ? rMode : "ANIMALS");
                    if (roomCodeText != null) roomCodeText.setText(currentRoomCode);
                    listenToRoomPresence(currentRoomId);
                    if (socket != null && socket.isConnected()) socket.joinRoomByCode(currentRoomCode);
                }));
            }));
        } else {
            // Host flow: create/reuse room under /rooms/{hostUid}/.
            String hostUid = prefs().getUserId();
            String hostName = prefs().getUsername();
            String avatarFile = prefs().getAvatarFileName();
            final String selectedMode = mode != null && !mode.isEmpty() ? mode : "ANIMALS";
            firebaseManager.getOrCreateHostRoom(hostUid, hostName, avatarFile, currentRoomName, selectedMode, (rId, roomCode, rMode) -> {
                runOnUiThread(() -> {
                    if (rId == null) {
                        Toast.makeText(LobbyActivity.this, "Could not create room. Please sign in again.", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    currentRoomId = rId;
                    currentRoomCode = roomCode;
                    if (room == null) {
                        room = new RoomInfo();
                        room.roomId = currentRoomId;
                    }
                    room.roomId = currentRoomId;
                    room.code = roomCode;
                    room.mode = rMode != null ? rMode : selectedMode;
                    if (roomCodeText != null) roomCodeText.setText(roomCode);
                    listenToRoomPresence(currentRoomId);
                });
            });
            if (socket != null && socket.isConnected()) socket.createRoom(selectedMode, null);
        }
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
                            if (rId != null && !rId.isEmpty()) intent.putExtra(EXTRA_ROOM_ID, rId);
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
            firebaseManager.getRoomRef(roomId).child("players").child(uid).onDisconnect().removeValue();
        }

        // Status Listener for match start
        if (statusListener != null) firebaseManager.getRoomRef(roomId).child("status").removeEventListener(statusListener);
        statusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.getValue(String.class);
                if ("PLAYING".equalsIgnoreCase(status) || "IN_PROGRESS".equalsIgnoreCase(status)) {
                    launchGame();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        firebaseManager.getRoomRef(roomId).child("status").addValueEventListener(statusListener);

        // Code Listener for unique room code from /rooms/{hostUid}/code
        if (codeListener != null) firebaseManager.getRoomRef(roomId).child("code").removeEventListener(codeListener);
        codeListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String uniqueCode = snapshot.getValue(String.class);
                if (uniqueCode != null && !uniqueCode.isEmpty()) {
                    currentRoomCode = uniqueCode;
                    if (room != null) room.code = uniqueCode;
                    if (roomCodeText != null) roomCodeText.setText(uniqueCode);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        firebaseManager.getRoomRef(roomId).child("code").addValueEventListener(codeListener);

        // Room Name Listener from /rooms/{hostUid}/name
        if (nameListener != null) firebaseManager.getRoomRef(roomId).child("name").removeEventListener(nameListener);
        nameListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String roomName = snapshot.getValue(String.class);
                if (roomName != null && !roomName.isEmpty()) {
                    currentRoomName = roomName;
                    if (room != null) room.name = roomName;
                    if (roomNameText != null) roomNameText.setText(roomName);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        firebaseManager.getRoomRef(roomId).child("name").addValueEventListener(nameListener);

        // Host UID Listener from /rooms/{hostUid}/hostUid
        if (hostUidListener != null) firebaseManager.getRoomRef(roomId).child("hostUid").removeEventListener(hostUidListener);
        hostUidListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String hUid = snapshot.getValue(String.class);
                if (hUid != null && !hUid.isEmpty()) {
                    if (room != null) room.hostId = hUid;
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        firebaseManager.getRoomRef(roomId).child("hostUid").addValueEventListener(hostUidListener);

        if (roomPlayersRef != null && playersListener != null) {
            roomPlayersRef.removeEventListener(playersListener);
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
                int total = (int) snapshot.getChildrenCount();
                if (total == 0) {
                    if (room == null) {
                        room = new RoomInfo();
                        room.roomId = roomId;
                        room.code = currentRoomCode != null ? currentRoomCode : "------";
                        room.mode = getIntent().getStringExtra(EXTRA_MODE);
                    }
                    ensureLocalPlayerInList(room);
                    updateRoomUI(room);
                    return;
                }
                final int[] count = {0};
                for (DataSnapshot child : snapshot.getChildren()) {
                    String pUid = child.child("uid").getValue(String.class);
                    if (pUid == null) pUid = child.getKey();
                    String name = child.child("displayName").getValue(String.class);
                    String avatarFile = child.child("avatarFileName").getValue(String.class);
                    Boolean ready = child.child("ready").getValue(Boolean.class);

                    final String uidFinal = pUid;
                    final boolean readyFinal = Boolean.TRUE.equals(ready);

                    firebaseManager.getUserProfile(pUid, userMap -> {
                        RoomInfo.LobbyPlayer p = new RoomInfo.LobbyPlayer();
                        p.userId = uidFinal;
                        if (userMap != null) {
                            String uName = (String) userMap.get("displayName");
                            String uAv = (String) userMap.get("avatarFileName");
                            p.username = uName != null ? uName : (name != null ? name : "Player");
                            p.avatarFileName = uAv != null ? uAv : (avatarFile != null ? avatarFile : "avatar_01.png");
                        } else {
                            p.username = name != null ? name : "Player";
                            p.avatarFileName = avatarFile != null ? avatarFile : "avatar_01.png";
                        }
                        p.isReady = readyFinal;
                        list.add(p);
                        count[0]++;
                        if (count[0] >= total) {
                            runOnUiThread(() -> {
                                if (room == null) {
                                    room = new RoomInfo();
                                    room.roomId = roomId;
                                    room.code = currentRoomCode != null ? currentRoomCode : "------";
                                    room.mode = getIntent().getStringExtra(EXTRA_MODE);
                                }
                                room.players = list;
                                ensureLocalPlayerInList(room);
                                updateRoomUI(room);
                            });
                        }
                    });
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateRoomUI(RoomInfo room) {
        if (room == null) return;
        ensureLocalPlayerInList(room);

        String hostUid = room.hostId != null && !room.hostId.isEmpty() ? room.hostId : currentRoomId;
        ensureHostPlayerInList(room, hostUid);

        if (room.code == null || room.code.isEmpty()) {
            room.code = currentRoomCode != null ? currentRoomCode : "------";
        }

        if (roomNameText != null) {
            String displayName = room.name != null && !room.name.isEmpty()
                    ? room.name : (currentRoomName != null && !currentRoomName.isEmpty() ? currentRoomName : null);
            if (displayName != null) roomNameText.setText(displayName);
        }

        if (roomCodeText != null) roomCodeText.setText(room.code);
        if (roomModeText != null)
            roomModeText.setText((room.mode != null ? room.mode : "ANIMALS") + " Mode (" + room.players.size() + "/5 Players)");

        ModernFButton btnStart = findViewById(R.id.btnStartMatch);
        String myUid = prefs().getUserId();
        boolean isHost = myUid != null && myUid.equals(hostUid);

        int nonHostReadyCount = 0;
        int nonHostTotalCount = 0;
        for (RoomInfo.LobbyPlayer p : room.players) {
            if (p.userId != null && !p.userId.equals(hostUid) && !p.isLeft) {
                nonHostTotalCount++;
                if (p.isReady) nonHostReadyCount++;
            }
        }

        if (isHost) {
            if (readyButton != null) readyButton.setVisibility(View.GONE);
            if (btnStart != null) {
                btnStart.setVisibility(View.VISIBLE);
                boolean allReady = nonHostTotalCount > 0 && nonHostReadyCount == nonHostTotalCount;
                btnStart.setEnabled(allReady);
                btnStart.setAlpha(allReady ? 1.0f : 0.4f);
                btnStart.setText(allReady ? "START MATCH 🚀" : "Waiting for players to be ready...");
                btnStart.setOnClickListener(v -> startMatchByHost());
            }
        } else {
            if (btnStart != null) btnStart.setVisibility(View.GONE);
            if (readyButton != null) {
                readyButton.setVisibility(View.VISIBLE);
                readyButton.setText(isLocallyReady ? "Cancel Ready 🔴" : "I'm Ready 🟢");
                readyButton.setOnClickListener(v -> toggleReady());
            }
        }

        playerAdapter.submitList(room.players, hostUid);
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
            self.avatarFileName = prefs().getAvatarFileName();
            self.isReady = isLocallyReady;
            room.players.add(0, self);
        }
    }

    private void ensureHostPlayerInList(RoomInfo room, String hostUid) {
        if (room == null || hostUid == null) return;
        if (room.players == null) room.players = new ArrayList<>();
        boolean foundHost = false;
        for (RoomInfo.LobbyPlayer p : room.players) {
            if (p.userId != null && p.userId.equals(hostUid)) {
                foundHost = true;
                p.isHost = true;
                break;
            }
        }
        if (!foundHost) {
            RoomInfo.LobbyPlayer hostPlayer = new RoomInfo.LobbyPlayer();
            hostPlayer.userId = hostUid;
            hostPlayer.username = "Host";
            hostPlayer.avatarFileName = "avatar_01.png";
            hostPlayer.isHost = true;
            hostPlayer.isLeft = true;
            room.players.add(0, hostPlayer);
        }
    }

    private void startMatchByHost() {
        if (currentRoomId == null) return;
        firebaseManager.getRoomRef(currentRoomId).child("status").setValue("PLAYING");
        if (socket != null && socket.isConnected()) socket.startGame();
        launchGame();
    }

    private void launchGame() {
        if (isFinishing()) return;
        String modeStr = room != null && room.mode != null ? room.mode : "ANIMALS";
        startActivity(GameActivity.intent(this, GameMode.valueOf(modeStr), currentRoomId, 60_000L));
        finish();
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
        // The socket room id is a server UUID; NEVER use it as the Firebase room node.
        // The Firebase room node is the host UID and comes from the intent extras.
        runOnUiThread(() -> {
            if (room != null) {
                if (room.code == null || room.code.isEmpty()) room.code = currentRoomCode;
                if (room.mode == null) room.mode = getIntent().getStringExtra(EXTRA_MODE);
                if (currentRoomId == null || currentRoomId.isEmpty()) {
                    String roomIdExtra = getIntent().getStringExtra(EXTRA_ROOM_ID);
                    if (roomIdExtra != null && !roomIdExtra.isEmpty()) {
                        currentRoomId = roomIdExtra;
                    } else {
                        // Host flow fallback: the Firebase room root is the host UID.
                        currentRoomId = prefs().getUserId();
                    }
                }
                room.roomId = currentRoomId;
            }
            updateRoomUI(room);
            if (currentRoomId != null) listenToRoomPresence(currentRoomId);
        });
    }

    @Override
    public void onRoomUpdated(RoomInfo room) {
        this.room = room;
        runOnUiThread(() -> updateRoomUI(room));
    }

    private void cancelCountdown() {
        if (startCountdownTimer != null) {
            startCountdownTimer.cancel();
            startCountdownTimer = null;
        }
        isCountingDown = false;
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
