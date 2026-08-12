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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
        boolean isSignedIn = user != null && !user.isAnonymous();

        TextView tvHint = view.findViewById(R.id.tvSignInHint);
        ModernFButton quickMatch = view.findViewById(R.id.quickMatchButton);
        ModernFButton createRoom = view.findViewById(R.id.createRoomButton);
        ModernFButton joinRoom = view.findViewById(R.id.joinRoomButton);
        ModernFButton offline = view.findViewById(R.id.offlineButton);

        if (!isSignedIn) {
            if (tvHint != null) tvHint.setVisibility(View.VISIBLE);
            lockOnlineButton(quickMatch);
            lockOnlineButton(createRoom);
            lockOnlineButton(joinRoom);
        } else {
            if (tvHint != null) tvHint.setVisibility(View.GONE);
            if (quickMatch != null) quickMatch.setOnClickListener(v -> performQuickMatch());
            if (createRoom != null) createRoom.setOnClickListener(v -> launchRandomLobby());
            if (joinRoom != null) joinRoom.setOnClickListener(v -> showJoinDialog());
        }

        if (offline != null) {
            offline.setOnClickListener(v -> openGameOffline());
        }
    }

    private void lockOnlineButton(ModernFButton btn) {
        if (btn == null) return;
        btn.setEnabled(false);
        btn.setAlpha(0.4f);
        btn.setOnClickListener(v ->
                Toast.makeText(requireContext(), "Sign in to play online", Toast.LENGTH_SHORT).show());
    }

    private void performQuickMatch() {
        Toast.makeText(requireContext(), "Searching for available public room...", Toast.LENGTH_SHORT).show();
        GuessCardApp.from(requireContext()).getFirebaseManager().findOpenRoomByMode(null, (roomId, roomCode, mode) -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (roomCode != null && !roomCode.isEmpty()) {
                    Toast.makeText(requireContext(), "Joining public room...", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(requireContext(), LobbyActivity.class);
                    intent.putExtra(LobbyActivity.EXTRA_ROOM_CODE, roomCode);
                    if (mode != null) intent.putExtra(LobbyActivity.EXTRA_MODE, mode);
                    startActivity(intent);
                } else {
                    Toast.makeText(requireContext(), "No open room found. Creating new room...", Toast.LENGTH_SHORT).show();
                    launchRandomLobby();
                }
            });
        });
    }

    private void launchRandomLobby() {
        GameMode[] modes = new GameMode[]{GameMode.ANIMALS, GameMode.FOOD, GameMode.COUNTRIES, GameMode.CELEBRITIES};
        GameMode randomMode = modes[new Random().nextInt(modes.length)];
        String newRoomId = String.valueOf(100000 + new Random().nextInt(900000));
        String uid = GuessCardApp.from(requireContext()).getPreferences().getUserId();
        String name = GuessCardApp.from(requireContext()).getPreferences().getUsername();
        String avatarFile = GuessCardApp.from(requireContext()).getPreferences().getAvatarFileName();

        GuessCardApp.from(requireContext()).getFirebaseManager()
                .createRoomOnFirebase(newRoomId, randomMode.name(), uid, name, avatarFile);

        Intent intent = new Intent(requireContext(), LobbyActivity.class);
        intent.putExtra(LobbyActivity.EXTRA_MODE, randomMode.name());
        intent.putExtra(LobbyActivity.EXTRA_ROOM_CODE, newRoomId);
        startActivity(intent);
    }

    private void showJoinDialog() {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("6-digit numeric room code");
        new MaterialAlertDialogBuilder(requireContext())
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
