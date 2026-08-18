package glab.guesscard.activities;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import glab.guesscard.ModernFButton;
import glab.guesscard.R;
import glab.guesscard.utils.AvatarManager;

/**
 * Victory / Final Results Activity for Guess the Card party game.
 * Uses RecyclerView to show final standings, base scores, bonus points based on question efficiency,
 * and updates player match history & stats in Firebase.
 */
public class WinnerActivity extends BaseActivity {

    public static class StandingsEntry {
        public String uid;
        public String username;
        public String avatarFileName;
        public int baseScore;
        public int bonusPoints;
        public int finalScore;
        public int questionsUsed;

        public StandingsEntry(String uid, String username, int baseScore, int questionsUsed, String avatarFileName) {
            this.uid = uid;
            this.username = username;
            this.baseScore = baseScore;
            this.questionsUsed = questionsUsed;
            this.avatarFileName = avatarFileName != null ? avatarFileName : "avatar_01.png";
            // Bonus points formula: Fewer questions asked to guess = MORE bonus points!
            this.bonusPoints = Math.max(0, (20 - questionsUsed) * 10);
            this.finalScore = baseScore + bonusPoints;
        }
    }

    private ValueEventListener waitingForHostListener;
    private RecyclerView rvWinnerLeaderboard;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_winner);

        String winnerName = getIntent().getStringExtra("winnerName");
        int finalScore = getIntent().getIntExtra("finalScore", 0);
        String roomId = getIntent().getStringExtra("roomId");
        String mode = getIntent().getStringExtra("mode");

        if (winnerName == null || winnerName.isEmpty()) winnerName = "YOU";

        try {
            container().getAudio().playSound(glab.guesscard.audio.GameAudio.Sound.VICTORY);
        } catch (Exception ignored) {}

        TextView tvWinnerName = findViewById(R.id.tvWinnerName);
        TextView tvFinalScore = findViewById(R.id.tvFinalScore);
        rvWinnerLeaderboard = findViewById(R.id.rvWinnerLeaderboard);

        if (rvWinnerLeaderboard != null) {
            rvWinnerLeaderboard.setLayoutManager(new LinearLayoutManager(this));
        }

        if (tvWinnerName != null) {
            tvWinnerName.setText(winnerName + " is the Winner! 👑");
        }

        if (tvFinalScore != null) {
            tvFinalScore.setText("Top Score: " + finalScore + " pts");
        }

        // Fetch live room players & standings from Firebase, calculate bonus points, and populate RecyclerView
        loadLiveRoomStandings(roomId, winnerName, finalScore, mode);

        ModernFButton btnPlayAgain = findViewById(R.id.btnPlayAgain);
        ModernFButton btnReturnToLobby = findViewById(R.id.btnReturnToLobby);
        ModernFButton btnHome = findViewById(R.id.btnHome);

        if (btnPlayAgain != null) {
            btnPlayAgain.setOnClickListener(v -> handlePlayAgain(roomId, mode, btnPlayAgain));
        }

        if (btnReturnToLobby != null) {
            btnReturnToLobby.setOnClickListener(v -> handlePlayAgain(roomId, mode, btnReturnToLobby));
        }

        if (btnHome != null) {
            btnHome.setOnClickListener(v -> {
                if (roomId != null && !roomId.isEmpty() && container() != null && container().getFirebaseManager() != null) {
                    container().getFirebaseManager().deleteRoom(roomId);
                }
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }
    }

    private void loadLiveRoomStandings(String roomId, String winnerName, int topScore, String mode) {
        if (roomId == null || roomId.isEmpty()) {
            List<StandingsEntry> fallback = new ArrayList<>();
            fallback.add(new StandingsEntry("self", winnerName, topScore, 5, "avatar_01.png"));
            populateLeaderboardRecyclerView(fallback, winnerName, mode);
            return;
        }

        container().getFirebaseManager().getRoomRef(roomId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<StandingsEntry> standings = new ArrayList<>();
                        long qUsed = 20 - (snapshot.child("questionsRemaining").exists() ?
                                snapshot.child("questionsRemaining").getValue(Long.class) : 10);

                        if (snapshot.child("players").exists()) {
                            for (DataSnapshot pSnap : snapshot.child("players").getChildren()) {
                                String uid = pSnap.child("uid").getValue(String.class);
                                if (uid == null) uid = pSnap.getKey();
                                String name = pSnap.child("displayName").getValue(String.class);
                                String avatar = pSnap.child("avatarFileName").getValue(String.class);
                                Long score = pSnap.child("score").getValue(Long.class);

                                standings.add(new StandingsEntry(
                                        uid,
                                        name != null ? name : "Player",
                                        score != null ? score.intValue() : 0,
                                        (int) qUsed,
                                        avatar
                                ));
                            }
                        }

                        if (standings.isEmpty()) {
                            standings.add(new StandingsEntry("self", winnerName, topScore, 5, "avatar_01.png"));
                        }

                        // Sort standings descending by finalScore (Base + Bonus)
                        Collections.sort(standings, (e1, e2) -> Integer.compare(e2.finalScore, e1.finalScore));

                        populateLeaderboardRecyclerView(standings, winnerName, mode);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        List<StandingsEntry> fallback = new ArrayList<>();
                        fallback.add(new StandingsEntry("self", winnerName, topScore, 5, "avatar_01.png"));
                        populateLeaderboardRecyclerView(fallback, winnerName, mode);
                    }
                });
    }

    private void populateLeaderboardRecyclerView(List<StandingsEntry> standings, String winnerName, String mode) {
        if (rvWinnerLeaderboard != null) {
            rvWinnerLeaderboard.setAdapter(new WinnerAdapter(standings));
        }

        // Save Match History & Update Stats in Firebase for Current User
        String myUid = prefs().getUserId();
        if (myUid != null && container() != null && container().getFirebaseManager() != null) {
            int myRank = 1;
            int myScore = 0;
            for (int i = 0; i < standings.size(); i++) {
                if (standings.get(i).uid != null && standings.get(i).uid.equals(myUid)) {
                    myRank = i + 1;
                    myScore = standings.get(i).finalScore;
                    break;
                }
            }
            boolean isWinner = myRank == 1;
            container().getFirebaseManager().saveMatchHistory(
                    myUid,
                    winnerName,
                    myScore,
                    mode != null ? mode : "ANIMALS",
                    isWinner,
                    myRank,
                    standings.size()
            );
        }
    }

    private void handlePlayAgain(String roomId, String mode, ModernFButton button) {
        String uid = prefs().getUserId();
        if (roomId == null || roomId.isEmpty() || uid == null) {
            Intent intent = new Intent(this, LobbyActivity.class);
            if (mode != null) intent.putExtra(LobbyActivity.EXTRA_MODE, mode);
            startActivity(intent);
            finish();
            return;
        }

        container().getFirebaseManager().getRoomRef(roomId).child("hostUid")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String hostUid = snapshot.getValue(String.class);
                        boolean isHost = uid.equals(hostUid) || roomId.equals(uid);

                        if (isHost) {
                            Map<String, Object> resetData = new HashMap<>();
                            resetData.put("status", "WAITING");
                            resetData.put("currentRound", 1);
                            resetData.put("questionsRemaining", 20);
                            resetData.put("secretCard", null);
                            resetData.put("qaHistory", null);

                            container().getFirebaseManager().getRoomRef(roomId).updateChildren(resetData, (error, ref) -> {
                                Intent intent = new Intent(WinnerActivity.this, LobbyActivity.class);
                                intent.putExtra(LobbyActivity.EXTRA_ROOM_ID, roomId);
                                if (mode != null) intent.putExtra(LobbyActivity.EXTRA_MODE, mode);
                                startActivity(intent);
                                finish();
                            });
                        } else {
                            container().getFirebaseManager().getRoomRef(roomId).child("status")
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot statusSnap) {
                                            String status = statusSnap.getValue(String.class);
                                            if ("WAITING".equalsIgnoreCase(status) || "OPEN".equalsIgnoreCase(status)) {
                                                Intent intent = new Intent(WinnerActivity.this, LobbyActivity.class);
                                                intent.putExtra(LobbyActivity.EXTRA_ROOM_ID, roomId);
                                                if (mode != null) intent.putExtra(LobbyActivity.EXTRA_MODE, mode);
                                                startActivity(intent);
                                                finish();
                                            } else {
                                                if (button != null) {
                                                    button.setText("Waiting for host to restart... ⏳");
                                                    button.setEnabled(false);
                                                    button.setButtonColor(Color.parseColor("#1E293B"));
                                                    button.setTextColor(Color.parseColor("#F59E0B"));
                                                }
                                                Toast.makeText(WinnerActivity.this, "Waiting for host to recreate lobby...", Toast.LENGTH_SHORT).show();

                                                if (waitingForHostListener != null) {
                                                    container().getFirebaseManager().getRoomRef(roomId).child("status").removeEventListener(waitingForHostListener);
                                                }
                                                waitingForHostListener = new ValueEventListener() {
                                                    @Override
                                                    public void onDataChange(@NonNull DataSnapshot sSnap) {
                                                        String st = sSnap.getValue(String.class);
                                                        if ("WAITING".equalsIgnoreCase(st) || "OPEN".equalsIgnoreCase(st)) {
                                                            container().getFirebaseManager().getRoomRef(roomId).child("status").removeEventListener(this);
                                                            Intent intent = new Intent(WinnerActivity.this, LobbyActivity.class);
                                                            intent.putExtra(LobbyActivity.EXTRA_ROOM_ID, roomId);
                                                            if (mode != null) intent.putExtra(LobbyActivity.EXTRA_MODE, mode);
                                                            startActivity(intent);
                                                            finish();
                                                        }
                                                    }
                                                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                                                };
                                                container().getFirebaseManager().getRoomRef(roomId).child("status").addValueEventListener(waitingForHostListener);
                                            }
                                        }
                                        @Override public void onCancelled(@NonNull DatabaseError error) {}
                                    });
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        String roomId = getIntent().getStringExtra("roomId");
        if (waitingForHostListener != null && roomId != null && container().getFirebaseManager() != null) {
            container().getFirebaseManager().getRoomRef(roomId).child("status").removeEventListener(waitingForHostListener);
        }
    }

    // ── RECYCLER VIEW ADAPTER FOR LEADERBOARD STANDINGS ──────────────────────

    private class WinnerAdapter extends RecyclerView.Adapter<WinnerAdapter.VH> {
        private final List<StandingsEntry> list;
        private final String[] ranks = new String[]{"1st 👑", "2nd 🥈", "3rd 🥉", "4th", "5th"};
        private final String[] colors = new String[]{"#F59E0B", "#94A3B8", "#D97706", "#64748B", "#475569"};

        WinnerAdapter(List<StandingsEntry> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_winner_standing, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            StandingsEntry entry = list.get(position);
            holder.tvRank.setText(position < ranks.length ? ranks[position] : (position + 1) + "th");
            holder.tvRank.setTextColor(Color.parseColor(position < colors.length ? colors[position] : "#94A3B8"));

            holder.tvName.setText(entry.username);
            holder.tvBreakdown.setText("Base: " + entry.baseScore + " • Bonus: +" + entry.bonusPoints + " (" + entry.questionsUsed + " Qs)");
            holder.tvTotalScore.setText(entry.finalScore + " pts");

            AvatarManager.getInstance().loadAvatarIntoImageView(WinnerActivity.this, holder.ivAvatar, entry.avatarFileName);
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvRank, tvName, tvBreakdown, tvTotalScore;
            ImageView ivAvatar;

            VH(@NonNull View itemView) {
                super(itemView);
                tvRank = itemView.findViewById(R.id.tvStandingRank);
                tvName = itemView.findViewById(R.id.tvStandingName);
                tvBreakdown = itemView.findViewById(R.id.tvStandingBreakdown);
                tvTotalScore = itemView.findViewById(R.id.tvStandingTotalScore);
                ivAvatar = itemView.findViewById(R.id.ivStandingAvatar);
            }
        }
    }
}
