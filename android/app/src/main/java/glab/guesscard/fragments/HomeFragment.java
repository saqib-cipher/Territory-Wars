package glab.guesscard.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Random;

import glab.guesscard.ModernFButton;
import glab.guesscard.R;
import glab.guesscard.activities.GameActivity;
import glab.guesscard.activities.LobbyActivity;
import glab.guesscard.di.GameContainer;
import glab.guesscard.models.GameMode;
import glab.guesscard.network.PreferenceManager;

/**
 * Home screen for Guess the Card party game:
 * Displays quick actions, live active game rooms in real-time, and offline practice mode.
 */
public class HomeFragment extends Fragment {

    private final java.util.List<java.util.Map<String, Object>> firebaseRoomsList = new java.util.ArrayList<>();
    private final java.util.List<java.util.Map<String, Object>> bluetoothRoomsList = new java.util.ArrayList<>();
    private String currentNetworkFilter = "ALL"; // "ALL", "ONLINE", "BLUETOOTH"

    private RecyclerView rvActiveRooms;
    private View emptyView;
    private TextView tvLiveRoomsCount;
    private View layoutBluetoothScanning;
    private ModernFButton btnFilterAll;
    private ModernFButton btnFilterOnline;
    private ModernFButton btnFilterBluetooth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        GameContainer appContainer = glab.guesscard.GuessCardApp.from(requireContext());
        PreferenceManager prefs = appContainer.getPreferences();

        View onlineContainer = view.findViewById(R.id.onlineModesContainer);
        View offlineContainer = view.findViewById(R.id.offlinePracticeContainer);

        if (onlineContainer != null) onlineContainer.setVisibility(View.VISIBLE);
        if (offlineContainer != null) offlineContainer.setVisibility(View.GONE);

        TextView tvPlayerName = view.findViewById(R.id.tvPlayerName);
        if (tvPlayerName != null) {
            String username = prefs.getUsername();
            if (username != null && !username.isEmpty()) {
                tvPlayerName.setText("Welcome back, " + username);
            } else {
                tvPlayerName.setText("Welcome to Guess Card!");
            }
        }

        // Quick Actions on Home Screen
        ModernFButton btnQuickMatch = view.findViewById(R.id.btnHomeQuickMatch);
        if (btnQuickMatch != null) {
            btnQuickMatch.setOnClickListener(v -> performQuickMatch());
        }

        ModernFButton btnCreateRoom = view.findViewById(R.id.btnHomeCreateRoom);
        if (btnCreateRoom != null) {
            btnCreateRoom.setOnClickListener(v -> showCreateRoomOptionDialog());
        }

        ModernFButton btnEmptyCreateRoom = view.findViewById(R.id.btnEmptyCreateRoom);
        if (btnEmptyCreateRoom != null) {
            btnEmptyCreateRoom.setOnClickListener(v -> showCreateRoomOptionDialog());
        }

        // Offline Practice Button
        View btnOffline = view.findViewById(R.id.btnOfflinePractice);
        if (btnOffline != null) {
            btnOffline.setOnClickListener(v -> {
                startActivity(GameActivity.intent(
                        requireContext(), GameMode.OFFLINE, "practice", 60_000L));
            });
        }

        setupFilterButtons(view);
        setupRoomRecyclerView(view);
        listenToFirebaseActiveRooms();
    }

    private void setupFilterButtons(View view) {
        btnFilterAll = view.findViewById(R.id.btnFilterAllRooms);
        btnFilterOnline = view.findViewById(R.id.btnFilterOnlineRooms);
        btnFilterBluetooth = view.findViewById(R.id.btnFilterBluetoothRooms);
        layoutBluetoothScanning = view.findViewById(R.id.layoutBluetoothScanning);

        updateFilterButtonStyles();

        if (btnFilterAll != null) {
            btnFilterAll.setOnClickListener(v -> {
                currentNetworkFilter = "ALL";
                updateFilterButtonStyles();
                refreshCombinedRoomsList();
            });
        }

        if (btnFilterOnline != null) {
            btnFilterOnline.setOnClickListener(v -> {
                currentNetworkFilter = "ONLINE";
                updateFilterButtonStyles();
                refreshCombinedRoomsList();
            });
        }

        if (btnFilterBluetooth != null) {
            btnFilterBluetooth.setOnClickListener(v -> {
                currentNetworkFilter = "BLUETOOTH";
                updateFilterButtonStyles();
                refreshCombinedRoomsList();
            });
        }
    }

    private void updateFilterButtonStyles() {
        int activeColor = android.graphics.Color.parseColor("#3B82F6");
        int inactiveColor = android.graphics.Color.parseColor("#1E293B");

        if (btnFilterAll != null) {
            btnFilterAll.setButtonColor("ALL".equals(currentNetworkFilter) ? activeColor : inactiveColor);
        }
        if (btnFilterOnline != null) {
            btnFilterOnline.setButtonColor("ONLINE".equals(currentNetworkFilter) ? activeColor : inactiveColor);
        }
        if (btnFilterBluetooth != null) {
            btnFilterBluetooth.setButtonColor("BLUETOOTH".equals(currentNetworkFilter) ? android.graphics.Color.parseColor("#8B5CF6") : inactiveColor);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        startBluetoothDiscovery();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopBluetoothDiscovery();
    }

    private void startBluetoothDiscovery() {
        if (!isAdded() || getContext() == null) return;
        glab.guesscard.bluetooth.BluetoothMeshManager bmMgr = glab.guesscard.GuessCardApp.from(requireContext()).getBluetoothMeshManager();

        if (glab.guesscard.bluetooth.BluetoothPermissionHelper.hasBluetoothPermissions(requireContext()) && bmMgr.isBluetoothEnabled()) {
            if (layoutBluetoothScanning != null) layoutBluetoothScanning.setVisibility(View.VISIBLE);
            bmMgr.startRoomDiscovery(discoveredRooms -> {
                if (!isAdded() || getContext() == null) return;
                requireActivity().runOnUiThread(() -> {
                    bluetoothRoomsList.clear();
                    for (glab.guesscard.bluetooth.BluetoothRoomModel r : discoveredRooms) {
                        bluetoothRoomsList.add(r.toRoomMap());
                    }
                    refreshCombinedRoomsList();
                });
            });
        } else {
            if (layoutBluetoothScanning != null) layoutBluetoothScanning.setVisibility(View.GONE);
        }
    }

    private void stopBluetoothDiscovery() {
        if (!isAdded() || getContext() == null) return;
        glab.guesscard.bluetooth.BluetoothMeshManager bmMgr = glab.guesscard.GuessCardApp.from(requireContext()).getBluetoothMeshManager();
        bmMgr.stopRoomDiscovery();
        if (layoutBluetoothScanning != null) layoutBluetoothScanning.setVisibility(View.GONE);
    }

    private void performQuickMatch() {
        Context context = getContext();
        if (context == null) return;
        Toast.makeText(context, "Scanning for open public rooms...", Toast.LENGTH_SHORT).show();
        glab.guesscard.GuessCardApp.from(context).getFirebaseManager().findOpenRoomByMode(null, (roomId, roomCode, mode) -> {
            if (!isAdded()) return;
            androidx.fragment.app.FragmentActivity activity = getActivity();
            if (activity == null) return;
            activity.runOnUiThread(() -> {
                if (!isAdded() || getContext() == null) return;
                if (roomId != null && roomCode != null && !roomCode.isEmpty()) {
                    Toast.makeText(requireContext(), "Joining public room...", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(requireContext(), LobbyActivity.class);
                    intent.putExtra(LobbyActivity.EXTRA_ROOM_ID, roomId);
                    intent.putExtra(LobbyActivity.EXTRA_ROOM_CODE, roomCode);
                    if (mode != null) intent.putExtra(LobbyActivity.EXTRA_MODE, mode);
                    startActivity(intent);
                } else {
                    Toast.makeText(requireContext(), "No open public room with available slots found. Tap 'Create Room' to host one!", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    /**
     * Create Room Option Selection Dialog:
     * - Network Type: Online (Cloud/Internet) vs Bluetooth Mesh (Offline/Local P2P)
     * - Game Mode: Animals, Food, Countries, Celebrities
     * - Room Name Input
     */
    private void showCreateRoomOptionDialog() {
        String[] modes = new String[]{"ANIMALS 🐾", "FOOD & DISHES 🍔", "COUNTRIES 🌍", "CELEBRITIES 🎬"};
        final GameMode[] modeEnums = new GameMode[]{GameMode.ANIMALS, GameMode.FOOD, GameMode.COUNTRIES, GameMode.CELEBRITIES};

        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        final android.widget.EditText etName = new android.widget.EditText(requireContext());
        etName.setHint("Room name (e.g. Fun Night)");
        etName.setSingleLine(true);
        etName.setMaxLines(1);
        etName.setText("");
        etName.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);
        layout.addView(etName, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

        // ── Network Mode Selection ──
        android.widget.TextView tvNetworkLabel = new android.widget.TextView(requireContext());
        tvNetworkLabel.setText("Select Network Connection");
        tvNetworkLabel.setTextColor(0xFF38BDF8);
        tvNetworkLabel.setTextSize(13);
        tvNetworkLabel.setPadding(0, pad / 2, 0, pad / 4);
        layout.addView(tvNetworkLabel);

        final android.widget.RadioGroup rgNetwork = new android.widget.RadioGroup(requireContext());
        android.widget.RadioButton rbOnline = new android.widget.RadioButton(requireContext());
        rbOnline.setText("🌐 Online Room (Internet Required)");
        rbOnline.setTextColor(0xFFFFFFFF);
        rbOnline.setId(android.view.View.generateViewId());
        rbOnline.setChecked(true);
        rgNetwork.addView(rbOnline);

        android.widget.RadioButton rbBluetooth = new android.widget.RadioButton(requireContext());
        rbBluetooth.setText("📡 Bluetooth Mesh (Offline • No Internet)");
        rbBluetooth.setTextColor(0xFFC084FC);
        rbBluetooth.setId(android.view.View.generateViewId());
        rgNetwork.addView(rbBluetooth);
        layout.addView(rgNetwork);

        // ── Game Mode Selection ──
        android.widget.TextView tvModeLabel = new android.widget.TextView(requireContext());
        tvModeLabel.setText("Select Game Mode");
        tvModeLabel.setTextColor(0xFFF59E0B);
        tvModeLabel.setTextSize(13);
        tvModeLabel.setPadding(0, pad / 2, 0, pad / 4);
        layout.addView(tvModeLabel);

        final int[] selectedModeIndex = {0};
        final android.widget.RadioGroup rgModes = new android.widget.RadioGroup(requireContext());
        for (int i = 0; i < modes.length; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(requireContext());
            rb.setText(modes[i]);
            rb.setTextColor(0xFFFFFFFF);
            rb.setId(android.view.View.generateViewId());
            rgModes.addView(rb);
            if (i == 0) rb.setChecked(true);
        }
        rgModes.setOnCheckedChangeListener((group, checkedId) -> {
            int count = group.getChildCount();
            for (int i = 0; i < count; i++) {
                if (group.getChildAt(i).getId() == checkedId) {
                    selectedModeIndex[0] = i;
                    break;
                }
            }
        });
        layout.addView(rgModes);

        new AlertDialog.Builder(requireContext())
                .setTitle("Create Game Room")
                .setView(layout)
                .setPositiveButton("Create", (dialog, which) -> {
                    String roomName = etName.getText().toString().trim();
                    boolean isBluetooth = rgNetwork.getCheckedRadioButtonId() == rbBluetooth.getId();
                    if (isBluetooth) {
                        createBluetoothHostRoom(modeEnums[selectedModeIndex[0]], roomName);
                    } else {
                        createOnlineHostRoom(modeEnums[selectedModeIndex[0]], roomName);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createOnlineHostRoom(GameMode mode, String roomName) {
        Context context = getContext();
        if (context == null) return;
        String uid = glab.guesscard.GuessCardApp.from(context).getPreferences().getUserId();
        String name = glab.guesscard.GuessCardApp.from(context).getPreferences().getUsername();
        String avatarFile = glab.guesscard.GuessCardApp.from(context).getPreferences().getAvatarFileName();

        glab.guesscard.GuessCardApp.from(context).getFirebaseManager()
                .getOrCreateHostRoom(uid, name, avatarFile, roomName, mode.name(), (roomId, roomCode, roomMode) -> {
                    if (!isAdded()) return;
                    androidx.fragment.app.FragmentActivity activity = getActivity();
                    if (activity == null) return;
                    activity.runOnUiThread(() -> {
                        if (!isAdded() || getContext() == null) return;
                        Intent intent = new Intent(requireContext(), LobbyActivity.class);
                        intent.putExtra(LobbyActivity.EXTRA_MODE, mode.name());
                        intent.putExtra(LobbyActivity.EXTRA_ROOM_ID, roomId);
                        intent.putExtra(LobbyActivity.EXTRA_ROOM_CODE, roomCode);
                        intent.putExtra(LobbyActivity.EXTRA_ROOM_NAME, roomName);
                        intent.putExtra("extra_is_bluetooth", false);
                        startActivity(intent);
                    });
                });
    }

    private void createBluetoothHostRoom(GameMode mode, String roomName) {
        Context context = getContext();
        if (context == null) return;

        if (!glab.guesscard.bluetooth.BluetoothPermissionHelper.hasBluetoothPermissions(context)) {
            glab.guesscard.bluetooth.BluetoothPermissionHelper.showPermissionExplanationDialog(getActivity(), () -> {
                glab.guesscard.bluetooth.BluetoothPermissionHelper.requestBluetoothPermissions(getActivity(), glab.guesscard.bluetooth.BluetoothPermissionHelper.REQ_BLUETOOTH_PERMISSIONS);
            });
            return;
        }

        if (!glab.guesscard.bluetooth.BluetoothPermissionHelper.isBluetoothEnabled()) {
            glab.guesscard.bluetooth.BluetoothPermissionHelper.promptEnableBluetooth(getActivity());
            return;
        }

        String roomCode = String.valueOf(100000 + new Random().nextInt(900000));
        String finalName = (roomName != null && !roomName.trim().isEmpty()) ? roomName.trim() : (mode.name() + " MESH");

        glab.guesscard.bluetooth.BluetoothMeshManager bmMgr = glab.guesscard.GuessCardApp.from(context).getBluetoothMeshManager();
        bmMgr.startHostRoom(roomCode, finalName, mode.name());

        Intent intent = new Intent(requireContext(), LobbyActivity.class);
        intent.putExtra(LobbyActivity.EXTRA_MODE, mode.name());
        intent.putExtra(LobbyActivity.EXTRA_ROOM_ID, "bt_" + roomCode);
        intent.putExtra(LobbyActivity.EXTRA_ROOM_CODE, roomCode);
        intent.putExtra(LobbyActivity.EXTRA_ROOM_NAME, finalName);
        intent.putExtra("extra_is_bluetooth", true);
        startActivity(intent);
    }

    private void setupRoomRecyclerView(View view) {
        rvActiveRooms = view.findViewById(R.id.rvActiveRooms);
        emptyView = view.findViewById(R.id.tvNoActiveRooms);
        tvLiveRoomsCount = view.findViewById(R.id.tvLiveRoomsCount);
        if (rvActiveRooms != null) {
            rvActiveRooms.setLayoutManager(new LinearLayoutManager(requireContext()));
        }
    }

    private void listenToFirebaseActiveRooms() {
        glab.guesscard.GuessCardApp.from(requireContext()).getFirebaseManager().getDatabaseRef().child("rooms")
                .addValueEventListener(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                        if (!isAdded() || getContext() == null) return;
                        firebaseRoomsList.clear();

                        for (com.google.firebase.database.DataSnapshot child : snapshot.getChildren()) {
                            String rId = child.getKey();
                            String code = child.child("code").getValue(String.class);
                            String roomName = child.child("name").getValue(String.class);
                            String mode = child.child("mode").getValue(String.class);
                            String hostUid = child.child("hostUid").getValue(String.class);
                            String status = child.child("status").getValue(String.class);
                            String hostName = child.child("hostName").getValue(String.class);
                            String hostAvatar = child.child("hostAvatar").getValue(String.class);

                            long pCount = child.child("players").getChildrenCount();
                            if (pCount == 0) {
                                pCount = 1;
                            }

                            if (hostName == null || hostName.isEmpty()) {
                                hostName = "Host";
                                if (child.child("players").exists()) {
                                    for (com.google.firebase.database.DataSnapshot p : child.child("players").getChildren()) {
                                        String n = p.child("displayName").getValue(String.class);
                                        String a = p.child("avatarFileName").getValue(String.class);
                                        if (n != null && !n.isEmpty()) {
                                            hostName = n;
                                            if (a != null && (hostAvatar == null || hostAvatar.isEmpty())) {
                                                hostAvatar = a;
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                            if (hostAvatar == null || hostAvatar.isEmpty()) {
                                hostAvatar = "avatar_01.png";
                            }

                            StringBuilder joined = new StringBuilder();
                            int shown = 0;
                            if (child.child("players").exists()) {
                                for (com.google.firebase.database.DataSnapshot p : child.child("players").getChildren()) {
                                    if (shown >= 3) break;
                                    String pn = p.child("displayName").getValue(String.class);
                                    if (pn != null && !pn.isEmpty()) {
                                        if (joined.length() > 0) joined.append(", ");
                                        joined.append(pn);
                                        shown++;
                                    }
                                }
                            }
                            if (pCount > shown) joined.append(" +").append(pCount - shown).append(" more");

                            java.util.Map<String, Object> roomMap = new java.util.HashMap<>();
                            roomMap.put("roomId", rId);
                            roomMap.put("name", (roomName != null && !roomName.trim().isEmpty()) ? roomName.trim() : ((mode != null ? mode : "ANIMALS") + " ROOM"));
                            roomMap.put("code", code != null && !code.isEmpty() ? code : rId);
                            roomMap.put("mode", mode != null ? mode : "ANIMALS");
                            roomMap.put("hostUid", hostUid != null ? hostUid : "");
                            roomMap.put("hostName", hostName);
                            roomMap.put("hostAvatar", hostAvatar);
                            roomMap.put("playersCount", pCount);
                            roomMap.put("playersNames", joined.toString());
                            roomMap.put("status", status != null ? status : "WAITING");

                            boolean isFull = pCount >= 5 || "IN_PROGRESS".equalsIgnoreCase(status) || "PLAYING".equalsIgnoreCase(status) || "FINISHED".equalsIgnoreCase(status);
                            roomMap.put("isFull", isFull);
                            roomMap.put("isBluetoothMesh", false);

                            firebaseRoomsList.add(roomMap);
                        }

                        refreshCombinedRoomsList();
                    }

                    @Override
                    public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
                });
    }

    private void refreshCombinedRoomsList() {
        if (!isAdded() || getContext() == null) return;
        java.util.List<java.util.Map<String, Object>> displayedRooms = new java.util.ArrayList<>();

        if ("ALL".equals(currentNetworkFilter)) {
            displayedRooms.addAll(bluetoothRoomsList);
            displayedRooms.addAll(firebaseRoomsList);
        } else if ("ONLINE".equals(currentNetworkFilter)) {
            displayedRooms.addAll(firebaseRoomsList);
        } else if ("BLUETOOTH".equals(currentNetworkFilter)) {
            displayedRooms.addAll(bluetoothRoomsList);
        }

        String myUid = glab.guesscard.GuessCardApp.from(getContext()).getPreferences().getUserId();
        java.util.Collections.sort(displayedRooms, (r1, r2) -> {
            boolean isBt1 = Boolean.TRUE.equals(r1.get("isBluetoothMesh"));
            boolean isBt2 = Boolean.TRUE.equals(r2.get("isBluetoothMesh"));
            if (isBt1 != isBt2) {
                return isBt1 ? -1 : 1; // Nearby Bluetooth rooms at top
            }

            boolean isHost1 = myUid != null && myUid.equals(r1.get("hostUid"));
            boolean isHost2 = myUid != null && myUid.equals(r2.get("hostUid"));
            if (isHost1 != isHost2) {
                return isHost1 ? -1 : 1;
            }

            boolean isFull1 = Boolean.TRUE.equals(r1.get("isFull"));
            boolean isFull2 = Boolean.TRUE.equals(r2.get("isFull"));
            if (isFull1 != isFull2) {
                return isFull1 ? 1 : -1;
            }
            long c1 = (long) r1.getOrDefault("playersCount", 1L);
            long c2 = (long) r2.getOrDefault("playersCount", 1L);
            return Long.compare(c1, c2);
        });

        androidx.fragment.app.FragmentActivity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (!isAdded() || getContext() == null) return;
                if (tvLiveRoomsCount != null) {
                    tvLiveRoomsCount.setText(displayedRooms.size() + " Rooms");
                }
                if (displayedRooms.isEmpty()) {
                    if (emptyView != null) emptyView.setVisibility(View.VISIBLE);
                    if (rvActiveRooms != null) rvActiveRooms.setVisibility(View.GONE);
                } else {
                    if (emptyView != null) emptyView.setVisibility(View.GONE);
                    if (rvActiveRooms != null) {
                        rvActiveRooms.setVisibility(View.VISIBLE);
                        rvActiveRooms.setAdapter(new ActiveRoomsAdapter(displayedRooms));
                    }
                }
            });
        }
    }

    private class ActiveRoomsAdapter extends RecyclerView.Adapter<ActiveRoomsAdapter.VH> {
        private final java.util.List<java.util.Map<String, Object>> items;

        ActiveRoomsAdapter(java.util.List<java.util.Map<String, Object>> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_active_room, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            java.util.Map<String, Object> r = items.get(position);
            Context ctx = holder.itemView.getContext();
            String name = String.valueOf(r.getOrDefault("name", "ROOM"));
            String mode = String.valueOf(r.getOrDefault("mode", "ANIMALS"));
            String hostName = String.valueOf(r.getOrDefault("hostName", "Host"));
            String hostAvatar = String.valueOf(r.getOrDefault("hostAvatar", "avatar_01.png"));
            String code = String.valueOf(r.getOrDefault("code", "------"));
            String playersNames = String.valueOf(r.getOrDefault("playersNames", ""));
            String hostUid = String.valueOf(r.getOrDefault("hostUid", ""));
            String status = String.valueOf(r.getOrDefault("status", "WAITING"));
            long pCount = (long) r.getOrDefault("playersCount", 1L);
            boolean isFull = Boolean.TRUE.equals(r.get("isFull"));
            boolean isBluetooth = Boolean.TRUE.equals(r.get("isBluetoothMesh"));
            String deviceAddress = String.valueOf(r.getOrDefault("deviceAddress", ""));

            String myUid = glab.guesscard.GuessCardApp.from(ctx).getPreferences().getUserId();
            boolean isHost = myUid != null && !myUid.isEmpty() && myUid.equals(hostUid);

            holder.tvName.setText(name);
            holder.tvHost.setText(isHost ? "Host: You (Host)" : "Host: " + hostName);
            holder.tvCode.setText("Code: " + code);
            holder.tvPlayers.setText(pCount + "/5 Players");

            if (holder.tvMeshBadge != null) {
                holder.tvMeshBadge.setVisibility(isBluetooth ? View.VISIBLE : View.GONE);
            }

            if (playersNames.isEmpty()) {
                holder.tvPlayersList.setVisibility(View.GONE);
            } else {
                holder.tvPlayersList.setVisibility(View.VISIBLE);
                holder.tvPlayersList.setText("Joined: " + playersNames);
            }

            glab.guesscard.utils.AvatarManager.getInstance().loadAvatarIntoImageView(ctx, holder.imgAvatar, hostAvatar);

            if (isFull && !isHost) {
                boolean inGame = "IN_PROGRESS".equalsIgnoreCase(status) || "PLAYING".equalsIgnoreCase(status);
                holder.tvPlayers.setText(inGame ? "IN GAME" : "FULL (5/5)");
                holder.tvPlayers.setBackgroundColor(android.graphics.Color.parseColor("#7F1D1D"));
                holder.btnJoin.setEnabled(false);
                holder.btnJoin.setAlpha(0.4f);
                holder.btnJoin.setText(inGame ? "IN GAME" : "FULL");
            } else {
                holder.btnJoin.setEnabled(true);
                holder.btnJoin.setAlpha(1.0f);
                holder.btnJoin.setText(isHost ? "RE-ENTER" : (isBluetooth ? "JOIN 📡" : "JOIN"));
                if (isHost) {
                    holder.btnJoin.setButtonColor(android.graphics.Color.parseColor("#F59E0B"));
                } else if (isBluetooth) {
                    holder.btnJoin.setButtonColor(android.graphics.Color.parseColor("#8B5CF6"));
                } else {
                    holder.btnJoin.setButtonColor(android.graphics.Color.parseColor("#3B82F6"));
                }
                holder.btnJoin.setOnClickListener(v -> {
                    if (isBluetooth) {
                        if (!glab.guesscard.bluetooth.BluetoothPermissionHelper.hasBluetoothPermissions(ctx)) {
                            glab.guesscard.bluetooth.BluetoothPermissionHelper.requestBluetoothPermissions(getActivity(), glab.guesscard.bluetooth.BluetoothPermissionHelper.REQ_BLUETOOTH_PERMISSIONS);
                            return;
                        }
                        glab.guesscard.bluetooth.BluetoothMeshManager bmMgr = glab.guesscard.GuessCardApp.from(ctx).getBluetoothMeshManager();
                        bmMgr.joinBluetoothRoom(deviceAddress, code, mode);

                        Intent intent = new Intent(ctx, LobbyActivity.class);
                        intent.putExtra(LobbyActivity.EXTRA_MODE, mode);
                        intent.putExtra(LobbyActivity.EXTRA_ROOM_ID, "bt_" + code);
                        intent.putExtra(LobbyActivity.EXTRA_ROOM_CODE, code);
                        intent.putExtra(LobbyActivity.EXTRA_ROOM_NAME, name);
                        intent.putExtra("extra_is_bluetooth", true);
                        ctx.startActivity(intent);
                    } else {
                        Intent intent = new Intent(ctx, LobbyActivity.class);
                        intent.putExtra(LobbyActivity.EXTRA_MODE, mode);
                        String roomId = String.valueOf(r.getOrDefault("roomId", ""));
                        if (roomId != null && !roomId.isEmpty()) {
                            intent.putExtra(LobbyActivity.EXTRA_ROOM_ID, roomId);
                        }
                        intent.putExtra(LobbyActivity.EXTRA_ROOM_CODE, code);
                        intent.putExtra(LobbyActivity.EXTRA_ROOM_NAME, name);
                        intent.putExtra("extra_is_bluetooth", false);
                        ctx.startActivity(intent);
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            android.widget.ImageView imgAvatar;
            TextView tvName, tvHost, tvCode, tvPlayers, tvPlayersList, tvMeshBadge;
            ModernFButton btnJoin;

            VH(@NonNull View itemView) {
                super(itemView);
                imgAvatar = itemView.findViewById(R.id.imgActiveRoomHostAvatar);
                tvName = itemView.findViewById(R.id.tvActiveRoomName);
                tvHost = itemView.findViewById(R.id.tvActiveRoomHost);
                tvCode = itemView.findViewById(R.id.tvActiveRoomCode);
                tvPlayers = itemView.findViewById(R.id.tvActiveRoomPlayers);
                tvPlayersList = itemView.findViewById(R.id.tvActiveRoomPlayersList);
                tvMeshBadge = itemView.findViewById(R.id.tvActiveRoomMeshBadge);
                btnJoin = itemView.findViewById(R.id.btnJoinActiveRoom);
            }
        }
    }
}
