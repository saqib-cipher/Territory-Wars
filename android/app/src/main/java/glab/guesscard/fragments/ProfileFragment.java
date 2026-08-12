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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.SimpleDateFormat;
import java.util.Date;
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
        loadGameHistory(view, container);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) {
            GameContainer container = glab.guesscard.GuessCardApp.from(requireContext());
            ImageView avatarView = getView().findViewById(R.id.profileAvatar);
            String avatarFile = container.getPreferences().getAvatarFileName();
            glab.guesscard.utils.AvatarManager.getInstance().loadAvatarIntoImageView(requireContext(), avatarView, avatarFile);
            TextView username = getView().findViewById(R.id.profileUsername);
            if (username != null) username.setText(container.getPreferences().getUsername());
        }
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
                    rv.setAdapter(new HistoryAdapter(list));
                }
            });
        });
    }

    private void showRenameDialog() {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("New username");
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit Profile")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) ->
                        viewModel.rename(input.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── GAME HISTORY ADAPTER ───────────────────────────────────────────────

    private static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private final List<Map<String, Object>> items;
        private final SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());

        HistoryAdapter(List<Map<String, Object>> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(
                    android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, Object> item = items.get(position);
            String mode = String.valueOf(item.getOrDefault("mode", "ANIMALS"));
            Object scoreObj = item.get("score");
            int score = scoreObj instanceof Long ? ((Long) scoreObj).intValue() : 0;
            boolean won = Boolean.TRUE.equals(item.get("won"));
            Object timeObj = item.get("timestamp");
            long time = timeObj instanceof Long ? (Long) timeObj : System.currentTimeMillis();
            String card = String.valueOf(item.getOrDefault("cardGuessed", ""));

            String title = (won ? "🏆 WIN" : "❌ MATCH") + " — " + mode + " Mode (+" + score + " pts)";
            String sub = (card.isEmpty() ? "" : "Card: " + card + " | ") + sdf.format(new Date(time));

            holder.text1.setText(title);
            holder.text1.setTextColor(won ? android.graphics.Color.parseColor("#10B981")
                                          : android.graphics.Color.parseColor("#F59E0B"));
            holder.text2.setText(sub);
            holder.text2.setTextColor(android.graphics.Color.parseColor("#94A3B8"));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1, text2;
            ViewHolder(View itemView) {
                super(itemView);
                text1 = itemView.findViewById(android.R.id.text1);
                text2 = itemView.findViewById(android.R.id.text2);
            }
        }
    }
}