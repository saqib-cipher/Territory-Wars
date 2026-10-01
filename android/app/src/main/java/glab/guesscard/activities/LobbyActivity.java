package glab.guesscard.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import glab.guesscard.ModernFButton;
import glab.guesscard.R;
import glab.guesscard.adapters.LobbyPlayerAdapter;
import glab.guesscard.firebase.FirebaseManager;
import glab.guesscard.models.ChatMessage;
import glab.guesscard.models.GameMode;
import glab.guesscard.models.RoomInfo;
import glab.guesscard.socket.GameSocketClient;
import glab.guesscard.socket.GameSocketListener;
import glab.guesscard.utils.AvatarManager;

/**
 * Lobby for multiplayer Guess the Card:
 * Displays player list, ready states, room code, host controls (transfer host, kick),
 * and starts game when everyone is ready.
 */
public class LobbyActivity extends BaseActivity implements GameSocketListener {

    public static final String EXTRA_MODE = "extra_mode";
    public static final String EXTRA_ROOM_ID = "extra_room_id";
    public static final String EXTRA_ROOM_CODE = "extra_room_code";
    public static final String EXTRA_ROOM_NAME = "extra_room_name";
    public static final String EXTRA_IS_BLUETOOTH = "extra_is_bluetooth";

    private GameSocketClient socket;
    private FirebaseManager firebaseManager;
    private glab.guesscard.bluetooth.BluetoothMeshManager bluetoothMeshManager;
    private boolean isBluetoothMode = false;
    private LobbyPlayerAdapter playerAdapter;
    private ModernFButton roomCodeText;
    private TextView roomNameText;
    private TextView roomModeText;
    private ModernFButton readyButton;
    private RecyclerView playersList;
    private RecyclerView rvInviteFriends;

    private RoomInfo room;
    private boolean isLocallyReady = false;
    private String currentRoomId;
    private String currentRoomCode;
    private String currentRoomName;
    private ValueEventListener statusListener;
    private ValueEventListener codeListener;
    private ValueEventListener nameListener;
    private ValueEventListener hostUidListener;
    private ValueEventListener kickListener;
    private DatabaseReference roomPlayersRef;
    private ChildEventListener playersListener;
    private CountDownTimer startCountdownTimer;
    private boolean isCountingDown = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby);

        isBluetoothMode = getIntent().getBooleanExtra(EXTRA_IS_BLUETOOTH, false);
        socket = container().getSocketClient();
        firebaseManager = container().getFirebaseManager();
        bluetoothMeshManager = container().getBluetoothMeshManager();

        if (isBluetoothMode) {
            bluetoothMeshManager.setGameListener(this);
        } else {
            socket.setListener(this);
            if (!socket.isConnected()) socket.connect();
        }

        roomCodeText = findViewById(R.id.roomCodeText);
        roomNameText = findViewById(R.id.roomNameText);
        roomModeText = findViewById(R.id.roomModeText);
        readyButton = findViewById(R.id.readyButton);
        playersList = findViewById(R.id.playersList);
        ModernFButton btnOpenInviteFriends = findViewById(R.id.btnOpenInviteFriends);

        if (btnOpenInviteFriends != null) {
            btnOpenInviteFriends.setOnClickListener(v -> showFriendsInviteDialog());
        }

        if (roomCodeText != null) {
            roomCodeText.setOnClickListener(v -> {
                String code = roomCodeText != null ? roomCodeText.getText().toString() : "";
                if (!code.isEmpty() && !code.equals("------")) {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, code);
                    startActivity(Intent.createChooser(shareIntent, "Share Room Code"));
                }
            });
        }

        playerAdapter = new LobbyPlayerAdapter();
        playerAdapter.setOnPlayerClickListener(this::handlePlayerClick);

        if (playersList != null) {
            playersList.setLayoutManager(new LinearLayoutManager(this));
            playersList.setAdapter(playerAdapter);
        }

        updateReadyButtonUI();
        if (readyButton != null) {
            readyButton.setOnClickListener(v -> toggleReady());
        }

        View leave = findViewById(R.id.leaveRoomButton);
        if (leave != null) leave.setOnClickListener(v -> leaveRoom());

        ModernFButton btnStart = findViewById(R.id.btnStartMatch);
        if (btnStart != null) {
            btnStart.setOnClickListener(v -> startMatchByHost());
        }

        if (!socket.isConnected()) socket.connect();

        initRoomFromIntent();
    }

    private void updateReadyButtonUI() {
        if (readyButton == null) return;
        if (isLocallyReady) {
            readyButton.setText("Cancel Ready 🔴");
            readyButton.setButtonColor(Color.parseColor("#EF4444"));
            readyButton.setShadowColor(Color.parseColor("#B91C1C"));
        } else {
            readyButton.setText("I'm Ready 🟢");
            readyButton.setButtonColor(Color.parseColor("#10B981"));
            readyButton.setShadowColor(Color.parseColor("#047857"));
        }
    }

    private void handlePlayerClick(RoomInfo.LobbyPlayer target) {
        if (target == null || target.userId == null) return;
        String myUid = prefs().getUserId();
        String hostUid = (room != null && room.hostId != null && !room.hostId.isEmpty()) ? room.hostId : currentRoomId;
        boolean isHost = myUid != null && myUid.equals(hostUid);
        boolean isSelf = myUid != null && myUid.equals(target.userId);

        if (isHost && !isSelf && !target.isLeft) {
            String targetName = target.username != null ? target.username : "Player";
            String[] options = new String[]{"👑 Transfer Host", "🚫 Kick Player", "👤 View Profile"};
            new AlertDialog.Builder(this)
                    .setTitle("Manage " + targetName)
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            transferHostTo(target.userId, targetName);
                        } else if (which == 1) {
                            kickPlayerFromRoom(target.userId, targetName);
                        } else if (which == 2) {
                            startActivity(PublicProfileActivity.intent(this, target.userId));
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            startActivity(PublicProfileActivity.intent(this, target.userId));
        }
    }

    private void transferHostTo(String targetUid, String targetName) {
        if (currentRoomId == null || targetUid == null) return;
        firebaseManager.getRoomRef(currentRoomId).child("hostUid").setValue(targetUid);
        firebaseManager.getUserProfile(targetUid, data -> {
            if (data != null) {
                String n = (String) data.get("displayName");
                String a = (String) data.get("avatarFileName");
                if (n != null) firebaseManager.getRoomRef(currentRoomId).child("hostName").setValue(n);
                if (a != null) firebaseManager.getRoomRef(currentRoomId).child("hostAvatar").setValue(a);
            }
        });
        Toast.makeText(this, "Host role transferred to " + targetName, Toast.LENGTH_SHORT).show();
        if (room != null) {
            room.hostId = targetUid;
            updateRoomUI(room);
        }
        refreshRoomPresence(currentRoomId);
    }

    private void kickPlayerFromRoom(String targetUid, String targetName) {
        if (currentRoomId == null || targetUid == null) return;
        firebaseManager.getRoomRef(currentRoomId).child("players").child(targetUid).removeValue();
        firebaseManager.getRoomRef(currentRoomId).child("kicked").child(targetUid).setValue(true);
        Toast.makeText(this, targetName + " has been kicked from the room", Toast.LENGTH_SHORT).show();
        refreshRoomPresence(currentRoomId);
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

        if (isBluetoothMode) {
            currentRoomId = roomIdExtra != null ? roomIdExtra : ("bt_" + codeExtra);
            currentRoomCode = codeExtra != null ? codeExtra : "BT-MESH";
            if (roomCodeText != null) roomCodeText.setText(currentRoomCode);
            if (roomNameText != null && currentRoomName != null) roomNameText.setText(currentRoomName);
            if (roomModeText != null && mode != null) roomModeText.setText("Mode: " + mode);

            if (bluetoothMeshManager != null && bluetoothMeshManager.isHosting()) {
                room = bluetoothMeshManager.getCurrentRoomInfo();
                if (room == null) {
                    room = new RoomInfo();
                    room.roomId = currentRoomId;
                    room.code = currentRoomCode;
                    room.name = currentRoomName;
                    room.mode = mode != null ? mode : "ANIMALS";
                    room.hostId = prefs().getUserId();
                }
                ensureLocalPlayerInList(room);
                updateRoomUI(room);
            }
            return;
        }

        if (roomIdExtra != null && !roomIdExtra.isEmpty()) {
            currentRoomId = roomIdExtra;
            currentRoomCode = codeExtra != null ? codeExtra : null;
            if (roomCodeText != null && currentRoomCode != null) roomCodeText.setText(currentRoomCode);
            listenToRoomPresence(currentRoomId);
            if (socket != null && socket.isConnected()) {
                if (currentRoomCode != null && !currentRoomCode.isEmpty()) socket.joinRoomByCode(currentRoomCode);
            }
        } else if (codeExtra != null && !codeExtra.isEmpty()) {
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

    private void loadFriendsToInvite() {
        // Handled dynamically via floating friends invite dialog (btnOpenInviteFriends)
    }

    private void toggleReady() {
        isLocallyReady = !isLocallyReady;
        updateReadyButtonUI();

        if (isBluetoothMode) {
            if (bluetoothMeshManager != null) {
                bluetoothMeshManager.toggleReady(isLocallyReady);
            }
            String myUid = prefs().getUserId();
            if (room != null && room.players != null) {
                for (RoomInfo.LobbyPlayer p : room.players) {
                    if (p.userId != null && p.userId.equals(myUid)) {
                        p.isReady = isLocallyReady;
                        break;
                    }
                }
                updateRoomUI(room);
            }
            return;
        }

        String myUid = prefs().getUserId();
        if (currentRoomId != null && myUid != null) {
            firebaseManager.getRoomRef(currentRoomId).child("players").child(myUid).child("ready").setValue(isLocallyReady);
        }

        if (socket != null && socket.isConnected()) {
            socket.setReady(isLocallyReady);
        }

        if (room != null && room.players != null) {
            for (RoomInfo.LobbyPlayer p : room.players) {
                if (p.userId != null && p.userId.equals(myUid)) {
                    p.isReady = isLocallyReady;
                    break;
                }
            }
            updateRoomUI(room);
        }
    }

    private boolean isStartingGame = false;

    private void leaveRoom() {
        isStartingGame = false;
        if (isBluetoothMode) {
            if (bluetoothMeshManager != null) bluetoothMeshManager.stopAllConnections();
            cancelCountdown();
            finish();
            return;
        }

        String myUid = prefs().getUserId();
        String hostUid = (room != null && room.hostId != null && !room.hostId.isEmpty()) ? room.hostId : currentRoomId;
        boolean isHost = myUid != null && myUid.equals(hostUid);

        if (isHost && currentRoomId != null) {
            // Host left intentionally: mark room CLOSED and delete from database so all players return home
            firebaseManager.getRoomRef(currentRoomId).child("status").setValue("CLOSED");
            firebaseManager.deleteRoom(currentRoomId);
        } else {
            removePresence();
        }

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
        if (isStartingGame) {
            // Match is starting: do NOT delete room and do NOT remove presence!
            return;
        }

        String myUid = prefs().getUserId();
        String hostUid = (room != null && room.hostId != null && !room.hostId.isEmpty()) ? room.hostId : currentRoomId;
        boolean isHost = myUid != null && myUid.equals(hostUid);

        if (isHost && currentRoomId != null) {
            firebaseManager.getRoomRef(currentRoomId).child("status").setValue("CLOSED");
            firebaseManager.deleteRoom(currentRoomId);
        } else {
            removePresence();
        }
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

        // Status Listener for match start or host left/room closed
        if (statusListener != null) firebaseManager.getRoomRef(roomId).child("status").removeEventListener(statusListener);
        statusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isFinishing()) return;
                String status = snapshot.getValue(String.class);
                if ("CLOSED".equalsIgnoreCase(status) || "DELETED".equalsIgnoreCase(status)) {
                    Toast.makeText(LobbyActivity.this, "Host has left the room. Returning to Home...", Toast.LENGTH_LONG).show();
                    removePresence();
                    finish();
                    return;
                }
                if ("PLAYING".equalsIgnoreCase(status) || "IN_PROGRESS".equalsIgnoreCase(status)) {
                    launchGame();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        firebaseManager.getRoomRef(roomId).child("status").addValueEventListener(statusListener);

        // Kicked listener for current player
        if (uid != null) {
            if (kickListener != null) firebaseManager.getRoomRef(roomId).child("kicked").child(uid).removeEventListener(kickListener);
            kickListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (isFinishing()) return;
                    Boolean kicked = snapshot.getValue(Boolean.class);
                    if (Boolean.TRUE.equals(kicked)) {
                        Toast.makeText(LobbyActivity.this, "You have been removed from the room by the host.", Toast.LENGTH_LONG).show();
                        removePresence();
                        finish();
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            };
            firebaseManager.getRoomRef(roomId).child("kicked").child(uid).addValueEventListener(kickListener);
        }

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
                    if (room != null) {
                        room.hostId = hUid;
                        for (RoomInfo.LobbyPlayer p : room.players) {
                            p.isHost = hUid.equals(p.userId);
                        }
                        updateRoomUI(room);
                    }
                    refreshRoomPresence(roomId);
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

        String hostUid = (room.hostId != null && !room.hostId.isEmpty()) ? room.hostId : currentRoomId;
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
                btnStart.setText(allReady ? "START MATCH 🚀" : "Waiting for players (" + nonHostReadyCount + "/" + nonHostTotalCount + " ready)...");
                btnStart.setOnClickListener(v -> startMatchByHost());
            }
        } else {
            if (btnStart != null) btnStart.setVisibility(View.GONE);
            if (readyButton != null) {
                readyButton.setVisibility(View.VISIBLE);
                updateReadyButtonUI();
                readyButton.setOnClickListener(v -> toggleReady());
            }
        }

        playerAdapter.submitList(room.players, hostUid);
        loadFriendsToInvite();
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
                p.isReady = isLocallyReady;
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

    private String matchAnswererUid = null;

    private void startMatchByHost() {
        if (currentRoomId == null) return;
        String modeStr = room != null && room.mode != null ? room.mode : "ANIMALS";
        GameMode gameMode = GameMode.ANIMALS;
        try { gameMode = GameMode.valueOf(modeStr); } catch (Exception ignored) {}

        glab.guesscard.models.Card initialCard = glab.guesscard.models.Card.getRandomCardForMode(gameMode);
        String hostUid = (room != null && room.hostId != null && !room.hostId.isEmpty()) ? room.hostId : prefs().getUserId();
        matchAnswererUid = hostUid;

        if (isBluetoothMode) {
            if (bluetoothMeshManager != null) {
                bluetoothMeshManager.startBluetoothGame(modeStr, hostUid, initialCard != null ? initialCard.word : "");
            }
            isStartingGame = true;
            Intent intent = GameActivity.intent(this, gameMode, currentRoomId, 60_000L, matchAnswererUid);
            intent.putExtra(EXTRA_IS_BLUETOOTH, true);
            startActivity(intent);
            finish();
            return;
        }

        Map<String, Object> matchInit = new HashMap<>();
        matchInit.put("status", "PLAYING");
        matchInit.put("secretCard", initialCard.word);
        matchInit.put("secretCardCategory", initialCard.category);
        matchInit.put("answererUid", hostUid);
        matchInit.put("currentRound", 1);
        int totalPlayersCount = (room != null && room.players != null && !room.players.isEmpty()) ? room.players.size() : 1;
        matchInit.put("totalRounds", totalPlayersCount);
        matchInit.put("questionsRemaining", 20);
        matchInit.put("startedAt", ServerValue.TIMESTAMP);

        // Give 20 initial bonus points to each player at the start of match
        if (room != null && room.players != null) {
            for (RoomInfo.LobbyPlayer p : room.players) {
                if (p.userId != null) {
                    firebaseManager.getRoomRef(currentRoomId).child("players").child(p.userId).child("score").setValue(20);
                }
            }
        }

        // Write to Firebase first, then launch game after write completes to avoid race condition
        firebaseManager.getRoomRef(currentRoomId).updateChildren(matchInit, (error, ref) -> {
            if (socket != null && socket.isConnected()) socket.startGame();
            launchGame();
        });
    }

    private void launchGame() {
        if (isFinishing()) return;
        isStartingGame = true;
        String modeStr = room != null && room.mode != null ? room.mode : "ANIMALS";
        Intent intent = GameActivity.intent(this, GameMode.valueOf(modeStr), currentRoomId, 60_000L, matchAnswererUid);
        intent.putExtra(EXTRA_IS_BLUETOOTH, isBluetoothMode);
        startActivity(intent);
        finish();
    }

    public void removePresence() {
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
            Intent intent = GameActivity.intent(this, GameMode.valueOf(mode), rId, durationMs, matchAnswererUid);
            intent.putExtra(EXTRA_IS_BLUETOOTH, isBluetoothMode);
            startActivity(intent);
            finish();
        });
    }

    @Override
    public void onRoomJoined(RoomInfo room) {
        this.room = room;
        runOnUiThread(() -> {
            if (room != null) {
                if (room.code == null || room.code.isEmpty()) room.code = currentRoomCode;
                if (room.mode == null) room.mode = getIntent().getStringExtra(EXTRA_MODE);
                if (currentRoomId == null || currentRoomId.isEmpty()) {
                    String roomIdExtra = getIntent().getStringExtra(EXTRA_ROOM_ID);
                    if (roomIdExtra != null && !roomIdExtra.isEmpty()) {
                        currentRoomId = roomIdExtra;
                    } else {
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

    private final java.util.Set<String> sentInvitesSet = new java.util.HashSet<>();

    private final Map<String, Long> inviteCooldownMap = new HashMap<>();
    private final Map<String, Boolean> inviteFriendsOnline = new java.util.concurrent.ConcurrentHashMap<>();
    private AlertDialog friendsInviteDialog = null;

    private void showFriendsInviteDialog() {
        String myUid = prefs().getUserId();
        if (myUid == null || isFinishing()) return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_lobby_friends_invite, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
            lp.gravity = android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL;
            dialog.getWindow().setAttributes(lp);
        }

        View btnClose = dialogView.findViewById(R.id.btnCloseFriendsDialog);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        RecyclerView rvFriends = dialogView.findViewById(R.id.rvLobbyFriendsList);
        TextView tvEmpty = dialogView.findViewById(R.id.tvNoFriendsFound);

        if (rvFriends != null) {
            rvFriends.setLayoutManager(new LinearLayoutManager(this));
        }

        firebaseManager.getFriends(myUid, friendsList -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (friendsList == null || friendsList.isEmpty()) {
                if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
                if (rvFriends != null) rvFriends.setVisibility(View.GONE);
            } else {
                // Online status comes from the BACKEND socket (not firebase lastSeen)
                List<String> uids = new ArrayList<>();
                for (Map<String, Object> f : friendsList) {
                    Object uid = f.get("uid");
                    if (uid != null) uids.add(String.valueOf(uid));
                }
                GameSocketClient sc = container() != null ? container().getSocketClient() : null;
                if (sc != null && !uids.isEmpty()) {
                    sc.checkOnlineStatus(uids, statusMap -> runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed() || statusMap == null) return;
                        inviteFriendsOnline.putAll(statusMap);
                        bindFriendsInviteList(rvFriends, tvEmpty, friendsList);
                    }));
                } else {
                    bindFriendsInviteList(rvFriends, tvEmpty, friendsList);
                }
            }
        }));

        friendsInviteDialog = dialog;
        dialog.show();
    }

    private void bindFriendsInviteList(RecyclerView rvFriends, TextView tvEmpty, List<Map<String, Object>> friendsList) {
        if (isFinishing() || isDestroyed()) return;
        if (friendsList.isEmpty()) {
            if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
            if (rvFriends != null) rvFriends.setVisibility(View.GONE);
        } else {
            if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
            if (rvFriends != null) {
                rvFriends.setVisibility(View.VISIBLE);
                rvFriends.setAdapter(new LobbyFriendsInviteDialogAdapter(friendsList));
            }
        }
    }

    // ── FLOATING INVITE FRIENDS DIALOG ADAPTER ──────────────────────────────

    private class LobbyFriendsInviteDialogAdapter extends RecyclerView.Adapter<LobbyFriendsInviteDialogAdapter.VH> {
        private final List<Map<String, Object>> friends;

        LobbyFriendsInviteDialogAdapter(List<Map<String, Object>> friends) {
            this.friends = friends;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lobby_friend_invite, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Map<String, Object> friend = friends.get(position);
            String fUid = (String) friend.getOrDefault("uid", "");
            String fName = (String) friend.getOrDefault("displayName", "Friend");
            String fAvatar = (String) friend.getOrDefault("avatarFileName", "avatar1.png");

            holder.tvName.setText(fName);
            AvatarManager.getInstance().loadAvatarIntoImageView(LobbyActivity.this, holder.ivAvatar, fAvatar);

            // Backend socket status (live, not firebase lastSeen)
            boolean isOnline = Boolean.TRUE.equals(inviteFriendsOnline.get(fUid));

            if (isOnline) {
                holder.vOnlineDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#10B981")));
                holder.tvStatus.setText("🟢 Online");
                holder.tvStatus.setTextColor(Color.parseColor("#10B981"));
            } else {
                holder.vOnlineDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#64748B")));
                holder.tvStatus.setText("⚪ Offline");
                holder.tvStatus.setTextColor(Color.parseColor("#94A3B8"));
            }

            // Check if already in lobby
            boolean isAlreadyInLobby = false;
            if (room != null && room.players != null && fUid != null) {
                for (RoomInfo.LobbyPlayer p : room.players) {
                    if (fUid.equals(p.userId) && !p.isLeft) {
                        isAlreadyInLobby = true;
                        break;
                    }
                }
            }

            Long cooldownEnd = inviteCooldownMap.get(fUid);
            long now = System.currentTimeMillis();

            if (isAlreadyInLobby) {
                holder.btnAction.setText("In Lobby");
                holder.btnAction.setEnabled(false);
                holder.btnAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#1E293B")));
                holder.btnAction.setTextColor(Color.parseColor("#64748B"));
            } else if (cooldownEnd != null && cooldownEnd > now) {
                long remainingSec = (cooldownEnd - now) / 1000 + 1;
                holder.btnAction.setText("Invited (" + remainingSec + "s)");
                holder.btnAction.setEnabled(false);
                holder.btnAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#1E293B")));
                holder.btnAction.setTextColor(Color.parseColor("#38BDF8"));
                startInviteCooldownTimer(holder.btnAction, fUid, cooldownEnd - now);
            } else {
                holder.btnAction.setText("+ Invite");
                holder.btnAction.setEnabled(true);
                holder.btnAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#2563EB")));
                holder.btnAction.setTextColor(Color.WHITE);

                holder.btnAction.setOnClickListener(v -> {
                    if (currentRoomId != null && fUid != null && !fUid.isEmpty()) {
                        long cd = System.currentTimeMillis() + 10000L;
                        inviteCooldownMap.put(fUid, cd);

                        String code = roomCodeText != null ? roomCodeText.getText().toString() : "";
                        String myName = prefs().getUsername();
                        String myAvatar = prefs().getAvatarFileName();
                        String mode = room != null && room.mode != null ? room.mode : "ANIMALS";

                        firebaseManager.sendRoomInvite(fUid, currentRoomId, code, myName, myAvatar, mode);
                        if (socket != null) {
                            socket.sendRoomInvite(fUid, currentRoomId, code, mode);
                        }

                        Toast.makeText(LobbyActivity.this, "Invitation sent to " + fName + "! ✉️", Toast.LENGTH_SHORT).show();
                        notifyItemChanged(holder.getAdapterPosition());
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return friends.size();
        }

        class VH extends RecyclerView.ViewHolder {
            com.google.android.material.imageview.ShapeableImageView ivAvatar;
            View vOnlineDot;
            TextView tvName, tvStatus;
            com.google.android.material.button.MaterialButton btnAction;

            VH(View v) {
                super(v);
                ivAvatar = v.findViewById(R.id.ivLobbyFriendAvatar);
                vOnlineDot = v.findViewById(R.id.vLobbyOnlineDot);
                tvName = v.findViewById(R.id.tvLobbyFriendName);
                tvStatus = v.findViewById(R.id.tvLobbyFriendStatus);
                btnAction = v.findViewById(R.id.btnInviteFriendAction);
            }
        }
    }

    private void startInviteCooldownTimer(com.google.android.material.button.MaterialButton btn, String fUid, long millis) {
        new CountDownTimer(millis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (btn != null) {
                    btn.setText("Invited (" + (millisUntilFinished / 1000 + 1) + "s)");
                }
            }

            @Override
            public void onFinish() {
                inviteCooldownMap.remove(fUid);
                if (btn != null) {
                    btn.setText("+ Invite");
                    btn.setEnabled(true);
                    btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#2563EB")));
                    btn.setTextColor(Color.WHITE);
                }
            }
        }.start();
    }
}
