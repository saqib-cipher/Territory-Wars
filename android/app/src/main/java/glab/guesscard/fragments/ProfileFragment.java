package glab.guesscard.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import glab.guesscard.R;
import glab.guesscard.di.GameContainer;
import glab.guesscard.models.PlayerProfile;
import glab.guesscard.viewmodel.ProfileViewModel;
import glab.guesscard.viewmodel.ViewModelFactory;

/**
 * Profile: avatar, username, level + XP, statistics, edit, achievements, game history.
 */
public class ProfileFragment extends Fragment {

    private ProfileViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        GameContainer container = glab.guesscard.GuessCardApp.from(requireContext());
        viewModel = new ViewModelProvider(this, new ViewModelFactory(container))
                .get(ProfileViewModel.class);

        MaterialButton edit = view.findViewById(R.id.editProfileButton);
        if (edit != null) {
            edit.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), glab.guesscard.activities.EditProfileActivity.class);
                startActivity(intent);
            });
        }

        MaterialButton btnFriends = view.findViewById(R.id.btnViewFriends);
        if (btnFriends != null) {
            btnFriends.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), glab.guesscard.activities.FriendsActivity.class);
                startActivity(intent);
            });
        }

        TextView tvUid = view.findViewById(R.id.tvUserUid);
        View uidContainer = view.findViewById(R.id.uidContainer);
        String uid = container.getPreferences().getUserId();
        if (tvUid != null) tvUid.setText("UID: " + (uid != null ? uid : "Guest"));

        if (uidContainer != null) {
            uidContainer.setOnClickListener(v -> {
                if (uid != null && !uid.isEmpty()) {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("User UID", uid);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(requireContext(), "Copied UID to clipboard", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        ImageView avatarView = view.findViewById(R.id.profileAvatar);
        if (avatarView != null) {
            avatarView.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), glab.guesscard.activities.EditProfileActivity.class);
                startActivity(intent);
            });
        }

        viewModel.getProfile().observe(getViewLifecycleOwner(), profile -> bind(view, profile));
        viewModel.getError().observe(getViewLifecycleOwner(),
                msg -> Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show());

        viewModel.loadProfile();
        loadPendingInvites(view, container);
        loadGameHistory(view, container);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null && isAdded()) {
            GameContainer container = glab.guesscard.GuessCardApp.from(requireContext());
            String uid = container.getPreferences().getUserId();
            container.getFirebaseManager().syncUserProfileOnLogin(uid, container.getPreferences(), () -> {
                if (getActivity() == null || !isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    ImageView avatarView = getView().findViewById(R.id.profileAvatar);
                    String avatarFile = container.getPreferences().getAvatarFileName();
                    glab.guesscard.utils.AvatarManager.getInstance().loadAvatarIntoImageView(requireContext(), avatarView, avatarFile);
                    TextView username = getView().findViewById(R.id.profileUsername);
                    if (username != null) username.setText(container.getPreferences().getUsername());
                });
            });
            loadPendingInvites(getView(), container);
        }
    }

    private void loadPendingInvites(View view, GameContainer container) {
        TextView tvHeader = view.findViewById(R.id.tvPendingHeader);
        RecyclerView rvPending = view.findViewById(R.id.rvPendingInvites);
        if (rvPending == null) return;

        String uid = container.getPreferences().getUserId();
        container.getFirebaseManager().getPendingRoomInvites(uid, invites -> {
            if (getActivity() == null || !isAdded()) return;
            container.getFirebaseManager().getPendingFriendRequests(uid, requests -> {
                if (getActivity() == null || !isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    MaterialButton btnFriends = view.findViewById(R.id.btnViewFriends);
                    int reqCount = (requests != null) ? requests.size() : 0;
                    if (btnFriends != null) {
                        btnFriends.setText(reqCount > 0 ? "Friends 👥 (" + reqCount + ")" : "Friends 👥");
                    }

                    boolean hasPending = (invites != null && !invites.isEmpty()) || (requests != null && !requests.isEmpty());
                    if (hasPending) {
                        if (tvHeader != null) tvHeader.setVisibility(View.VISIBLE);
                        rvPending.setVisibility(View.VISIBLE);
                        rvPending.setLayoutManager(new LinearLayoutManager(requireContext()));
                        rvPending.setAdapter(new PendingInvitesAdapter(invites, requests, container));
                    } else {
                        if (tvHeader != null) tvHeader.setVisibility(View.GONE);
                        rvPending.setVisibility(View.GONE);
                    }
                });
            });
        });
    }

    private void bind(View view, PlayerProfile profile) {
        TextView username = view.findViewById(R.id.profileUsername);
        TextView level = view.findViewById(R.id.profileLevel);
        LinearProgressIndicator xp = view.findViewById(R.id.xpProgress);

        if (username != null) username.setText(profile.username);
        if (level != null) level.setText("Level " + profile.level);
        if (xp != null) {
            xp.setMax(Math.max(1, profile.xpToNext));
            xp.setProgressCompat(profile.xp, true);
        }
    }

    private void loadGameHistory(View view, GameContainer container) {
        RecyclerView rv = view.findViewById(R.id.rvGameHistory);
        TextView tvNo = view.findViewById(R.id.tvNoHistory);
        if (rv == null) return;

        String uid = container.getPreferences().getUserId();
        container.getFirebaseManager().getGameHistory(uid, list -> {
            if (getActivity() == null || !isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (list == null || list.isEmpty()) {
                    if (tvNo != null) tvNo.setVisibility(View.VISIBLE);
                    rv.setVisibility(View.GONE);
                } else {
                    if (tvNo != null) tvNo.setVisibility(View.GONE);
                    rv.setVisibility(View.VISIBLE);
                    rv.setLayoutManager(new LinearLayoutManager(requireContext()));
                    rv.setAdapter(new HistoryAdapter(list, uid, this::showMatchDetailsDialog));
                }
            });
        });
    }

    // ── PENDING INVITATIONS & REQUESTS ADAPTER ──────────────────────────────

    private class PendingInvitesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_ROOM_INVITE = 1;
        private static final int TYPE_FRIEND_REQUEST = 2;

        private final List<Map<String, Object>> roomInvites;
        private final List<Map<String, Object>> friendRequests;
        private final GameContainer container;

        PendingInvitesAdapter(List<Map<String, Object>> roomInvites,
                              List<Map<String, Object>> friendRequests,
                              GameContainer container) {
            this.roomInvites = roomInvites != null ? roomInvites : java.util.Collections.emptyList();
            this.friendRequests = friendRequests != null ? friendRequests : java.util.Collections.emptyList();
            this.container = container;
        }

        @Override
        public int getItemViewType(int position) {
            if (position < roomInvites.size()) return TYPE_ROOM_INVITE;
            return TYPE_FRIEND_REQUEST;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_ROOM_INVITE) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_room_invite, parent, false);
                return new RoomInviteViewHolder(v);
            } else {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend_request, parent, false);
                return new FriendRequestViewHolder(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            String myUid = container.getPreferences().getUserId();

            if (getItemViewType(position) == TYPE_ROOM_INVITE) {
                Map<String, Object> invite = roomInvites.get(position);
                RoomInviteViewHolder vh = (RoomInviteViewHolder) holder;

                String key = (String) invite.get("key");
                String senderName = (String) invite.getOrDefault("senderName", "A Friend");
                String senderAvatar = (String) invite.getOrDefault("senderAvatar", "avatar1.png");
                String roomCode = (String) invite.getOrDefault("roomCode", "");
                String roomId = (String) invite.getOrDefault("roomId", "");

                vh.tvName.setText(senderName);
                vh.tvRoom.setText("Invited you to Room #" + (roomCode.isEmpty() ? roomId : roomCode));
                glab.guesscard.utils.AvatarManager.getInstance().loadAvatarIntoImageView(requireContext(), vh.ivAvatar, senderAvatar);

                vh.btnJoin.setOnClickListener(v -> {
                    container.getFirebaseManager().declineRoomInvite(myUid, key);
                    Intent intent = new Intent(requireContext(), glab.guesscard.activities.LobbyActivity.class);
                    if (!roomId.isEmpty()) intent.putExtra(glab.guesscard.activities.LobbyActivity.EXTRA_ROOM_ID, roomId);
                    if (!roomCode.isEmpty()) intent.putExtra(glab.guesscard.activities.LobbyActivity.EXTRA_ROOM_CODE, roomCode);
                    startActivity(intent);
                });

                vh.btnDismiss.setOnClickListener(v -> {
                    container.getFirebaseManager().declineRoomInvite(myUid, key);
                    if (getView() != null) loadPendingInvites(getView(), container);
                });
            } else {
                int reqPos = position - roomInvites.size();
                Map<String, Object> profile = friendRequests.get(reqPos);
                FriendRequestViewHolder vh = (FriendRequestViewHolder) holder;

                String senderUid = (String) profile.get("uid");
                String name = (String) profile.getOrDefault("username", "Player");
                String avatar = (String) profile.getOrDefault("avatarFileName", "avatar1.png");

                vh.tvName.setText(name);
                glab.guesscard.utils.AvatarManager.getInstance().loadAvatarIntoImageView(requireContext(), vh.ivAvatar, avatar);

                vh.btnAccept.setOnClickListener(v -> {
                    container.getFirebaseManager().addFriend(myUid, senderUid);
                    Toast.makeText(requireContext(), "Accepted friend request!", Toast.LENGTH_SHORT).show();
                    if (getView() != null) loadPendingInvites(getView(), container);
                });

                vh.btnDecline.setOnClickListener(v -> {
                    container.getFirebaseManager().declineFriendRequest(myUid, senderUid);
                    if (getView() != null) loadPendingInvites(getView(), container);
                });
            }
        }

        @Override
        public int getItemCount() {
            return roomInvites.size() + friendRequests.size();
        }

        static class RoomInviteViewHolder extends RecyclerView.ViewHolder {
            ImageView ivAvatar;
            TextView tvName, tvRoom;
            com.google.android.material.button.MaterialButton btnDismiss, btnJoin;

            RoomInviteViewHolder(View v) {
                super(v);
                ivAvatar = v.findViewById(R.id.ivInviteSenderAvatar);
                tvName = v.findViewById(R.id.tvInviteSenderName);
                tvRoom = v.findViewById(R.id.tvInviteRoomCode);
                btnDismiss = v.findViewById(R.id.btnDismissInvite);
                btnJoin = v.findViewById(R.id.btnJoinInvite);
            }
        }

        static class FriendRequestViewHolder extends RecyclerView.ViewHolder {
            ImageView ivAvatar;
            TextView tvName;
            com.google.android.material.button.MaterialButton btnDecline, btnAccept;

            FriendRequestViewHolder(View v) {
                super(v);
                ivAvatar = v.findViewById(R.id.ivReqAvatar);
                tvName = v.findViewById(R.id.tvReqUsername);
                btnDecline = v.findViewById(R.id.btnDeclineReq);
                btnAccept = v.findViewById(R.id.btnAcceptReq);
            }
        }
    }

    private void showMatchDetailsDialog(Map<String, Object> item) {
        if (item == null || getContext() == null || !isAdded()) return;

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_match_details, null);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvBadge = dialogView.findViewById(R.id.tvMatchDetailBadge);
        TextView tvSecretCard = dialogView.findViewById(R.id.tvMatchSecretCard);
        TextView tvWinnerInfo = dialogView.findViewById(R.id.tvMatchWinnerInfo);
        TextView tvTimestamp = dialogView.findViewById(R.id.tvMatchTimestamp);
        RecyclerView rvStandings = dialogView.findViewById(R.id.rvMatchDetailsStandings);
        View btnClose = dialogView.findViewById(R.id.btnCloseMatchDetails);
        View btnDone = dialogView.findViewById(R.id.btnDoneMatchDetails);

        String mode = String.valueOf(item.getOrDefault("mode", "ANIMALS"));
        String winnerName = String.valueOf(item.getOrDefault("winnerName", "Winner"));
        String secretCard = String.valueOf(item.getOrDefault("secretCard", ""));
        Object timeObj = item.get("timestamp");
        long time = timeObj instanceof Long ? (Long) timeObj : System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault());

        String myUid = glab.guesscard.GuessCardApp.from(requireContext()).getPreferences().getUserId();
        boolean isMeWinner = false;

        //noinspection unchecked
        List<Map<String, Object>> standings = (List<Map<String, Object>>) item.get("standings");
        if (standings != null && myUid != null) {
            for (Map<String, Object> p : standings) {
                if (myUid.equals(p.get("uid"))) {
                    isMeWinner = Boolean.TRUE.equals(p.get("isWinner")) || (p.get("rank") instanceof Number && ((Number) p.get("rank")).intValue() == 1);
                    break;
                }
            }
        } else {
            isMeWinner = Boolean.TRUE.equals(item.get("won")) || Boolean.TRUE.equals(item.get("isWinner"));
        }

        if (tvBadge != null) {
            tvBadge.setText(mode.toUpperCase() + " MODE • " + (isMeWinner ? "VICTORY 🏆" : "COMPLETED ✓"));
            tvBadge.setBackgroundColor(isMeWinner ? android.graphics.Color.parseColor("#10B981") : android.graphics.Color.parseColor("#3B82F6"));
        }

        if (tvSecretCard != null) {
            String cardText = (secretCard != null && !secretCard.isEmpty() && !"null".equalsIgnoreCase(secretCard)) ? secretCard : "Hidden";
            tvSecretCard.setText("🎯 Secret Card: " + cardText);
        }

        if (tvWinnerInfo != null) {
            tvWinnerInfo.setText("👑 Winner: " + (winnerName != null && !winnerName.isEmpty() ? winnerName : "Player"));
        }

        if (tvTimestamp != null) {
            tvTimestamp.setText("📅 " + sdf.format(new Date(time)));
        }

        if (rvStandings != null) {
            rvStandings.setLayoutManager(new LinearLayoutManager(requireContext()));
            if (standings != null && !standings.isEmpty()) {
                rvStandings.setAdapter(new MatchStandingsAdapter(standings));
            } else {
                // Synthesize a single row entry from legacy record
                List<Map<String, Object>> singleList = new ArrayList<>();
                Map<String, Object> legacyEntry = new HashMap<>();
                legacyEntry.put("rank", 1);
                legacyEntry.put("username", winnerName);
                legacyEntry.put("score", item.get("score"));
                legacyEntry.put("questionsUsed", 0);
                singleList.add(legacyEntry);
                rvStandings.setAdapter(new MatchStandingsAdapter(singleList));
            }
        }

        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());
        if (btnDone != null) btnDone.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private class MatchStandingsAdapter extends RecyclerView.Adapter<MatchStandingsAdapter.VH> {
        private final List<Map<String, Object>> standingsList;

        MatchStandingsAdapter(List<Map<String, Object>> list) {
            this.standingsList = list;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_match_details_player, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Map<String, Object> player = standingsList.get(position);
            Object rankObj = player.get("rank");
            int rank = rankObj instanceof Number ? ((Number) rankObj).intValue() : (position + 1);
            String name = String.valueOf(player.getOrDefault("username", "Player"));
            String avatar = String.valueOf(player.getOrDefault("avatarFileName", "avatar1.png"));
            Object scoreObj = player.get("score");
            int score = scoreObj instanceof Number ? ((Number) scoreObj).intValue() : 0;
            Object qUsedObj = player.get("questionsUsed");
            int qUsed = qUsedObj instanceof Number ? ((Number) qUsedObj).intValue() : 0;

            holder.tvRank.setText("#" + rank);
            if (rank == 1) {
                holder.tvRank.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F59E0B")));
                holder.tvRank.setText("👑");
            } else {
                holder.tvRank.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#334155")));
            }

            holder.tvName.setText(name);
            holder.tvQuestions.setText(qUsed > 0 ? ("Questions: " + qUsed) : "Completed");
            holder.tvScore.setText(score + " pts");
            glab.guesscard.utils.AvatarManager.getInstance().loadAvatarIntoImageView(requireContext(), holder.ivAvatar, avatar);
        }

        @Override
        public int getItemCount() {
            return standingsList.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvRank, tvName, tvQuestions, tvScore;
            com.google.android.material.imageview.ShapeableImageView ivAvatar;

            VH(View v) {
                super(v);
                tvRank = v.findViewById(R.id.tvPlayerRank);
                ivAvatar = v.findViewById(R.id.ivPlayerAvatar);
                tvName = v.findViewById(R.id.tvPlayerName);
                tvQuestions = v.findViewById(R.id.tvPlayerQuestionsUsed);
                tvScore = v.findViewById(R.id.tvPlayerScore);
            }
        }
    }

    interface OnMatchClickListener {
        void onMatchClicked(Map<String, Object> match);
    }

    // ── GAME HISTORY ADAPTER ───────────────────────────────────────────────

    private static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private final List<Map<String, Object>> items;
        private final String currentUid;
        private final OnMatchClickListener listener;
        private final SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());

        HistoryAdapter(List<Map<String, Object>> items, String currentUid, OnMatchClickListener listener) {
            this.items = items;
            this.currentUid = currentUid;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.item_match_history, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, Object> item = items.get(position);
            String mode = String.valueOf(item.getOrDefault("mode", "ANIMALS"));
            Object scoreObj = item.get("score");
            int score = scoreObj instanceof Number ? ((Number) scoreObj).intValue() : 0;
            boolean won = Boolean.TRUE.equals(item.get("won")) || Boolean.TRUE.equals(item.get("isWinner"));

            // Check standings to find current player's personal score and win status if present
            //noinspection unchecked
            List<Map<String, Object>> standings = (List<Map<String, Object>>) item.get("standings");
            if (standings != null && currentUid != null) {
                for (Map<String, Object> p : standings) {
                    if (currentUid.equals(p.get("uid"))) {
                        Object sc = p.get("score");
                        if (sc instanceof Number) score = ((Number) sc).intValue();
                        won = Boolean.TRUE.equals(p.get("isWinner")) || (p.get("rank") instanceof Number && ((Number) p.get("rank")).intValue() == 1);
                        break;
                    }
                }
            }

            Object timeObj = item.get("timestamp");
            long time = timeObj instanceof Long ? (Long) timeObj : System.currentTimeMillis();
            String card = String.valueOf(item.getOrDefault("secretCard", item.getOrDefault("cardGuessed", "")));
            String winner = String.valueOf(item.getOrDefault("winnerName", ""));

            holder.tvMode.setText(mode.toUpperCase() + " Mode");
            holder.tvBadge.setText(won ? "WIN (+" + score + " pts)" : "LOSS (+" + score + " pts)");
            holder.tvBadge.setBackgroundColor(won ? android.graphics.Color.parseColor("#10B981")
                                                   : android.graphics.Color.parseColor("#EF4444"));

            String details;
            if (card != null && !card.isEmpty() && !"null".equalsIgnoreCase(card)) {
                details = "Secret Card: " + card + (!winner.isEmpty() ? " • Winner: " + winner : "");
            } else if (!winner.isEmpty()) {
                details = "Winner: " + winner;
            } else {
                details = "Played match";
            }
            holder.tvCard.setText(details);
            holder.tvTimestamp.setText(sdf.format(new Date(time)));

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onMatchClicked(item);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvMode, tvBadge, tvCard, tvTimestamp;

            ViewHolder(View itemView) {
                super(itemView);
                tvMode = itemView.findViewById(R.id.tvMatchMode);
                tvBadge = itemView.findViewById(R.id.tvResultBadge);
                tvCard = itemView.findViewById(R.id.tvMatchCard);
                tvTimestamp = itemView.findViewById(R.id.tvMatchTimestamp);
            }
        }
    }
}