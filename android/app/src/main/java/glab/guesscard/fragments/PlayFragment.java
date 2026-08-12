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
        // Quick match: create a room on the socket server. Other players can join.
        launchRandomLobby();
    }

    private void launchRandomLobby() {
        GameMode[] modes = new GameMode[]{GameMode.ANIMALS, GameMode.FOOD, GameMode.COUNTRIES, GameMode.CELEBRITIES};
        GameMode randomMode = modes[new Random().nextInt(modes.length)];

        // Socket.IO server will create a persistent room using host UID
        Intent intent = new Intent(requireContext(), LobbyActivity.class);
        intent.putExtra(LobbyActivity.EXTRA_MODE, randomMode.name());
        startActivity(intent);
    }

    private void showJoinDialog() {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("Enter room code (e.g. ABC12)");
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Join Room by Code")
                .setMessage("Enter the 5-character room code shared by the host.")
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
