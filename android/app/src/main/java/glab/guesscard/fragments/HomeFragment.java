package glab.guesscard.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import glab.guesscard.ModernFButton;
import glab.guesscard.R;
import glab.guesscard.activities.GameActivity;
import glab.guesscard.activities.LobbyActivity;
import glab.guesscard.di.GameContainer;
import glab.guesscard.models.GameMode;
import glab.guesscard.network.PreferenceManager;

/**
 * Home screen for Guess the Card party game:
 * Displays live active game rooms in real-time.
 */
public class HomeFragment extends Fragment {

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

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        // Anyone with a Firebase account (including guests) can browse and join online rooms.
        boolean isOnline = user != null;

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

        // Offline Practice Button
        View btnOffline = view.findViewById(R.id.btnOfflinePractice);
        if (btnOffline != null) {
            btnOffline.setOnClickListener(v -> {
                startActivity(GameActivity.intent(
                        requireContext(), GameMode.OFFLINE, "practice", 60_000L));
            });
        }

        listenToActiveRooms(view);
    }

    private void listenToActiveRooms(View view) {
        RecyclerView rv = view.findViewById(R.id.rvActiveRooms);
        TextView tvNo = view.findViewById(R.id.tvNoActiveRooms);
        View onlineContainer = view.findViewById(R.id.onlineModesContainer);
        if (rv == null) return;

        if (onlineContainer != null) onlineContainer.setVisibility(View.VISIBLE);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        glab.guesscard.GuessCardApp.from(requireContext()).getFirebaseManager().getDatabaseRef().child("rooms")
                .addValueEventListener(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                        if (getActivity() == null || !isAdded()) return;
                        java.util.List<java.util.Map<String, Object>> openRooms = new java.util.ArrayList<>();
                        java.util.List<java.util.Map<String, Object>> fullRooms = new java.util.ArrayList<>();

                        for (com.google.firebase.database.DataSnapshot child : snapshot.getChildren()) {
                            String rId = child.getKey();
                            String code = child.child("code").getValue(String.class);
                            String roomName = child.child("name").getValue(String.class);
                            String mode = child.child("mode").getValue(String.class);
                            String hostUid = child.child("hostUid").getValue(String.class);
                            String status = child.child("status").getValue(String.class);

                            if ("FINISHED".equalsIgnoreCase(status)) {
                                continue;
                            }

                            long pCount = child.child("players").getChildrenCount();
                            if (pCount == 0) {
                                pCount = 1;
                            }

                            String hostName = "Host";
                            String hostAvatar = "avatar_01.png";
                            if (child.child("players").exists()) {
                                for (com.google.firebase.database.DataSnapshot p : child.child("players").getChildren()) {
                                    String n = p.child("displayName").getValue(String.class);
                                    String a = p.child("avatarFileName").getValue(String.class);
                                    if (n != null && !n.isEmpty()) {
                                        hostName = n;
                                        if (a != null) hostAvatar = a;
                                        break;
                                    }
                                }
                            }

                            // Names of players already in the room
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
                            boolean isFull = pCount >= 5 || "IN_PROGRESS".equalsIgnoreCase(status) || "PLAYING".equalsIgnoreCase(status);
                            roomMap.put("isFull", isFull);

                            if (isFull) {
                                fullRooms.add(roomMap);
                            } else {
                                openRooms.add(roomMap);
                            }
                        }

                        java.util.List<java.util.Map<String, Object>> allRooms = new java.util.ArrayList<>(openRooms);
                        allRooms.addAll(fullRooms);

                        getActivity().runOnUiThread(() -> {
                            if (allRooms.isEmpty()) {
                                if (tvNo != null) tvNo.setVisibility(View.VISIBLE);
                                rv.setVisibility(View.GONE);
                            } else {
                                if (tvNo != null) tvNo.setVisibility(View.GONE);
                                rv.setVisibility(View.VISIBLE);
                                rv.setAdapter(new ActiveRoomsAdapter(allRooms));
                            }
                        });
                    }

                    @Override
                    public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
                });
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
            String name = String.valueOf(r.getOrDefault("name", "ROOM"));
            String mode = String.valueOf(r.getOrDefault("mode", "ANIMALS"));
            String hostName = String.valueOf(r.getOrDefault("hostName", "Host"));
            String hostAvatar = String.valueOf(r.getOrDefault("hostAvatar", "avatar_01.png"));
            String code = String.valueOf(r.getOrDefault("code", "------"));
            String playersNames = String.valueOf(r.getOrDefault("playersNames", ""));
            long pCount = (long) r.getOrDefault("playersCount", 1L);
            boolean isFull = Boolean.TRUE.equals(r.get("isFull"));

            holder.tvName.setText(name);
            holder.tvHost.setText("Host: " + hostName);
            holder.tvCode.setText("Code: " + code);
            holder.tvPlayers.setText(pCount + "/5 Players");
            if (playersNames.isEmpty()) {
                holder.tvPlayersList.setVisibility(View.GONE);
            } else {
                holder.tvPlayersList.setVisibility(View.VISIBLE);
                holder.tvPlayersList.setText("Joined: " + playersNames);
            }

            glab.guesscard.utils.AvatarManager.getInstance().loadAvatarIntoImageView(requireContext(), holder.imgAvatar, hostAvatar);

            if (isFull) {
                holder.tvPlayers.setText(pCount >= 5 ? "FULL (5/5)" : "IN GAME");
                holder.tvPlayers.setBackgroundColor(android.graphics.Color.parseColor("#7F1D1D"));
                holder.btnJoin.setEnabled(false);
                holder.btnJoin.setAlpha(0.4f);
                holder.btnJoin.setText("FULL");
            } else {
                holder.btnJoin.setEnabled(true);
                holder.btnJoin.setAlpha(1.0f);
                holder.btnJoin.setText("JOIN");
                holder.btnJoin.setOnClickListener(v -> {
                    Intent intent = new Intent(requireContext(), LobbyActivity.class);
                    intent.putExtra(LobbyActivity.EXTRA_MODE, mode);
                    String roomId = String.valueOf(r.getOrDefault("roomId", ""));
                    if (roomId != null && !roomId.isEmpty()) {
                        intent.putExtra(LobbyActivity.EXTRA_ROOM_ID, roomId);
                    }
                    intent.putExtra(LobbyActivity.EXTRA_ROOM_CODE, code);
                    intent.putExtra(LobbyActivity.EXTRA_ROOM_NAME, name);
                    startActivity(intent);
                });
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            android.widget.ImageView imgAvatar;
            TextView tvName, tvHost, tvCode, tvPlayers, tvPlayersList;
            ModernFButton btnJoin;

            VH(@NonNull View itemView) {
                super(itemView);
                imgAvatar = itemView.findViewById(R.id.imgActiveRoomHostAvatar);
                tvName = itemView.findViewById(R.id.tvActiveRoomName);
                tvHost = itemView.findViewById(R.id.tvActiveRoomHost);
                tvCode = itemView.findViewById(R.id.tvActiveRoomCode);
                tvPlayers = itemView.findViewById(R.id.tvActiveRoomPlayers);
                tvPlayersList = itemView.findViewById(R.id.tvActiveRoomPlayersList);
                btnJoin = itemView.findViewById(R.id.btnJoinActiveRoom);
            }
        }
    }
}
