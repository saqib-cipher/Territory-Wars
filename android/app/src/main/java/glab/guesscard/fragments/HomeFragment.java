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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import glab.guesscard.R;
import glab.guesscard.activities.CustomModeActivity;
import glab.guesscard.activities.GameActivity;
import glab.guesscard.activities.LobbyActivity;
import glab.guesscard.di.GameContainer;
import glab.guesscard.models.GameMode;
import glab.guesscard.network.PreferenceManager;

/**
 * Home screen for Guess the Card party game:
 * Displays online game modes when authenticated/online, or offline practice mode when offline.
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

        // Mode Card Handlers
        bindModeCard(view, R.id.modeAnimals, GameMode.ANIMALS);
        bindModeCard(view, R.id.modeFood, GameMode.FOOD);
        bindModeCard(view, R.id.modeCountries, GameMode.COUNTRIES);
        bindModeCard(view, R.id.modeCelebrities, GameMode.CELEBRITIES);

        // Custom Mode Creator
        View customCard = view.findViewById(R.id.modeCustom);
        if (customCard != null) {
            customCard.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), CustomModeActivity.class);
                startActivity(intent);
            });
        }

        // Offline Practice Button
        View btnOffline = view.findViewById(R.id.btnOfflinePractice);
        if (btnOffline != null) {
            btnOffline.setOnClickListener(v -> {
                startActivity(GameActivity.intent(
                        requireContext(), GameMode.OFFLINE, "practice", 60_000L));
            });
        }
    }

    private void bindModeCard(View root, int viewId, GameMode mode) {
        View card = root.findViewById(viewId);
        if (card != null) {
            if (card.getBackground() != null) {
                card.setBackground(card.getBackground().mutate());
            }
            card.setOnClickListener(v -> {
                // Socket.IO server handles room creation with host UID as persistent key.
                // No need to pre-create Firebase rooms — the lobby connects to socket server directly.
                Intent intent = new Intent(requireContext(), LobbyActivity.class);
                intent.putExtra(LobbyActivity.EXTRA_MODE, mode.name());
                startActivity(intent);
            });
        }
    }
}
