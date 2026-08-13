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
        boolean isOnline = user != null && !user.isAnonymous();

        View onlineContainer = view.findViewById(R.id.onlineModesContainer);
        View offlineContainer = view.findViewById(R.id.offlinePracticeContainer);

        if (isOnline) {
            if (onlineContainer != null) onlineContainer.setVisibility(View.VISIBLE);
            if (offlineContainer != null) offlineContainer.setVisibility(View.GONE);
        } else {
            if (onlineContainer != null) onlineContainer.setVisibility(View.GONE);
            if (offlineContainer != null) offlineContainer.setVisibility(View.VISIBLE);
        }

        TextView tvPlayerName = view.findViewById(R.id.tvPlayerName);
        if (tvPlayerName != null) {
            String username = prefs.getUsername();
            if (isOnline && username != null && !username.isEmpty()) {
                tvPlayerName.setText("Welcome back, " + username);
            } else {
                tvPlayerName.setText("Playing Offline Mode");
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
        if (rv == null) return;

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
                            String mode = child.child("mode").getValue(String.class);
                            String hostUid = child.child("hostUid").getValue(String.class);
                            String status = child.child("status").getValue(String.class);

                            if ("FINISHED".equalsIgnoreCase(status)) {
                                continue;
                            }

                            long pCount = child.child("players").getChildrenCount();

                            String hostName = "Host";
                            String hostAvatar = "avatar_01.png";
                            if (hostUid != null && child.child("players").hasChild(hostUid)) {
                                com.google.firebase.database.DataSnapshot hSnap = child.child("players").child(hostUid);
                                String n = hSnap.child("displayName").getValue(String.class);
                                String a = hSnap.child("avatarFileName").getValue(String.class);
                                if (n != null) hostName = n;
                                if (a != null) hostAvatar = a;
                            }

                            java.util.Map<String, Object> roomMap = new java.util.HashMap<>();
                            roomMap.put("roomId", rId);
                            roomMap.put("code", code != null ? code : rId);
                            roomMap.put("mode", mode != null ? mode : "ANIMALS");
                            roomMap.put("hostUid", hostUid != null ? hostUid : "");
                            roomMap.put("hostName", hostName);
                            roomMap.put("hostAvatar", hostAvatar);
                            roomMap.put("playersCount", pCount);
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
            String mode = String.valueOf(r.getOrDefault("mode", "ANIMALS"));
            String hostName = String.valueOf(r.getOrDefault("hostName", "Host"));
            String hostAvatar = String.valueOf(r.getOrDefault("hostAvatar", "avatar_01.png"));
            String code = String.valueOf(r.getOrDefault("code", "------"));
            long pCount = (long) r.getOrDefault("playersCount", 1L);
            boolean isFull = Boolean.TRUE.equals(r.get("isFull"));

            holder.tvMode.setText(mode.toUpperCase() + " MODE");
            holder.tvHost.setText("Host: " + hostName);
            holder.tvCode.setText("Code: " + code);
            holder.tvPlayers.setText(pCount + "/5 Players");

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
                    intent.putExtra(LobbyActivity.EXTRA_ROOM_CODE, code);
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
            TextView tvMode, tvHost, tvCode, tvPlayers;
            ModernFButton btnJoin;

            VH(@NonNull View itemView) {
                super(itemView);
                imgAvatar = itemView.findViewById(R.id.imgActiveRoomHostAvatar);
                tvMode = itemView.findViewById(R.id.tvActiveRoomMode);
                tvHost = itemView.findViewById(R.id.tvActiveRoomHost);
                tvCode = itemView.findViewById(R.id.tvActiveRoomCode);
                tvPlayers = itemView.findViewById(R.id.tvActiveRoomPlayers);
                btnJoin = itemView.findViewById(R.id.btnJoinActiveRoom);
            }
        }
    }
}
