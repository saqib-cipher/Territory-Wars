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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Random;

import glab.guesscard.GuessCardApp;
import glab.guesscard.ModernFButton;
import glab.guesscard.R;
import glab.guesscard.activities.GameActivity;
import glab.guesscard.activities.LobbyActivity;
import glab.guesscard.models.GameMode;

public class PlayFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_play, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        // Guests (anonymous Firebase accounts) can also create/join online rooms.
        boolean isSignedIn = user != null;

        TextView tvHint = view.findViewById(R.id.tvSignInHint);
        ModernFButton quickMatch = view.findViewById(R.id.quickMatchButton);
        ModernFButton createRoom = view.findViewById(R.id.createRoomButton);
        ModernFButton btnCustom = view.findViewById(R.id.customModeButton);
        ModernFButton joinRoom = view.findViewById(R.id.joinRoomButton);
        ModernFButton offline = view.findViewById(R.id.offlineButton);

        if (!isSignedIn) {
            if (tvHint != null) tvHint.setVisibility(View.VISIBLE);
            lockOnlineButton(quickMatch);
            lockOnlineButton(createRoom);
            lockOnlineButton(btnCustom);
            lockOnlineButton(joinRoom);
        } else {
            if (tvHint != null) tvHint.setVisibility(View.GONE);
            if (quickMatch != null) quickMatch.setOnClickListener(v -> performQuickMatch());
            if (createRoom != null) createRoom.setOnClickListener(v -> showCreateRoomModeDialog());
            if (btnCustom != null) btnCustom.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), glab.guesscard.activities.CustomModeActivity.class);
                startActivity(intent);
            });
            if (joinRoom != null) joinRoom.setOnClickListener(v -> showJoinDialog());
        }

        if (offline != null) {
            offline.setOnClickListener(v -> openGameOffline());
        }
    }

    private void showCreateRoomModeDialog() {
        String[] modes = new String[]{"ANIMALS 🐾", "FOOD & DISHES 🍔", "COUNTRIES 🌍", "CELEBRITIES 🎬"};
        final GameMode[] modeEnums = new GameMode[]{GameMode.ANIMALS, GameMode.FOOD, GameMode.COUNTRIES, GameMode.CELEBRITIES};

        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        final android.widget.EditText etName = new android.widget.EditText(requireContext());
        etName.setHint("Room name (e.g. Friday Night)");
        etName.setSingleLine(true);
        etName.setMaxLines(1);
        etName.setText("");
        etName.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);
        layout.addView(etName, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

        android.widget.TextView tvModeLabel = new android.widget.TextView(requireContext());
        tvModeLabel.setText("Select Game Mode");
        tvModeLabel.setTextColor(0xFFF59E0B);
        tvModeLabel.setTextSize(13);
        tvModeLabel.setPadding(0, pad, 0, pad / 2);
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
                .setTitle("Create Room")
                .setView(layout)
                .setPositiveButton("Create", (dialog, which) -> {
                    String roomName = etName.getText().toString().trim();
                    createHostRoomForMode(modeEnums[selectedModeIndex[0]], roomName);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createHostRoomForMode(GameMode mode, String roomName) {
        String uid = GuessCardApp.from(requireContext()).getPreferences().getUserId();
        String name = GuessCardApp.from(requireContext()).getPreferences().getUsername();
        String avatarFile = GuessCardApp.from(requireContext()).getPreferences().getAvatarFileName();

        GuessCardApp.from(requireContext()).getFirebaseManager()
                .getOrCreateHostRoom(uid, name, avatarFile, roomName, mode.name(), (roomId, roomCode, roomMode) -> {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        Intent intent = new Intent(requireContext(), LobbyActivity.class);
                        intent.putExtra(LobbyActivity.EXTRA_MODE, mode.name());
                        intent.putExtra(LobbyActivity.EXTRA_ROOM_ID, roomId);
                        intent.putExtra(LobbyActivity.EXTRA_ROOM_CODE, roomCode);
                        intent.putExtra(LobbyActivity.EXTRA_ROOM_NAME, roomName);
                        startActivity(intent);
                    });
                });
    }

    private void lockOnlineButton(ModernFButton btn) {
        if (btn == null) return;
        btn.setEnabled(false);
        btn.setAlpha(0.4f);
        btn.setOnClickListener(v ->
                Toast.makeText(requireContext(), "Sign in to play online", Toast.LENGTH_SHORT).show());
    }

    private void performQuickMatch() {
        Toast.makeText(requireContext(), "Scanning for open public rooms...", Toast.LENGTH_SHORT).show();
        GuessCardApp.from(requireContext()).getFirebaseManager().findOpenRoomByMode(null, (roomId, roomCode, mode) -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (roomId != null && roomCode != null && !roomCode.isEmpty()) {
                    Toast.makeText(requireContext(), "Joining public room...", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(requireContext(), LobbyActivity.class);
                    intent.putExtra(LobbyActivity.EXTRA_ROOM_ID, roomId);
                    intent.putExtra(LobbyActivity.EXTRA_ROOM_CODE, roomCode);
                    if (mode != null) intent.putExtra(LobbyActivity.EXTRA_MODE, mode);
                    startActivity(intent);
                } else {
                    Toast.makeText(requireContext(), "No open public room with available slots found. Use 'Create Room' to host a game!", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void launchRandomLobby() {
        GameMode[] modes = new GameMode[]{GameMode.ANIMALS, GameMode.FOOD, GameMode.COUNTRIES, GameMode.CELEBRITIES};
        GameMode randomMode = modes[new Random().nextInt(modes.length)];
        String uid = GuessCardApp.from(requireContext()).getPreferences().getUserId();
        if (uid == null || uid.isEmpty()) {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u != null) uid = u.getUid();
        }
        String name = GuessCardApp.from(requireContext()).getPreferences().getUsername();
        String avatarFile = GuessCardApp.from(requireContext()).getPreferences().getAvatarFileName();
        String roomName = (name != null && !name.isEmpty()) ? name + "'s Room" : "My Room";

        GuessCardApp.from(requireContext()).getFirebaseManager()
                .getOrCreateHostRoom(uid, name, avatarFile, roomName, randomMode.name(), (roomId, roomCode, roomMode) -> {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        Intent intent = new Intent(requireContext(), LobbyActivity.class);
                        intent.putExtra(LobbyActivity.EXTRA_MODE, randomMode.name());
                        if (roomId != null) intent.putExtra(LobbyActivity.EXTRA_ROOM_ID, roomId);
                        if (roomCode != null) intent.putExtra(LobbyActivity.EXTRA_ROOM_CODE, roomCode);
                        intent.putExtra(LobbyActivity.EXTRA_ROOM_NAME, roomName);
                        startActivity(intent);
                    });
                });
    }

    private void showJoinDialog() {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("6-digit numeric room code");
        new AlertDialog.Builder(requireContext())
                .setTitle("Join Room")
                .setView(input)
                .setPositiveButton("Join", (dialog, which) -> {
                    String code = input.getText().toString().trim();
                    if (code.length() < 4) {
                        Toast.makeText(requireContext(), "Enter a valid room code", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent intent = new Intent(requireContext(), LobbyActivity.class);
                    intent.putExtra(LobbyActivity.EXTRA_ROOM_CODE, code);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openGameOffline() {
        startActivity(GameActivity.intent(
                requireContext(), GameMode.OFFLINE, "offline", 90_000L));
    }
}
