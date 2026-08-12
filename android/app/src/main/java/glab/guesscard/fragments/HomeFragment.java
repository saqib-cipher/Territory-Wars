package glab.guesscard.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import glab.guesscard.R;
import glab.guesscard.activities.CustomModeActivity;
import glab.guesscard.activities.GameActivity;
import glab.guesscard.activities.LobbyActivity;
import glab.guesscard.di.GameContainer;
import glab.guesscard.models.GameMode;
import glab.guesscard.network.PreferenceManager;

/**
 * Home screen for Guess the Card party game:
 * Mode selection (Animals, Food, Countries, Celebrities, Custom) and Quick Action buttons.
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

        TextView tvPlayerName = view.findViewById(R.id.tvPlayerName);
        if (tvPlayerName != null) {
            String username = prefs.getUsername();
            String userId   = prefs.getUserId();
            if (username != null && !username.isEmpty() && !"Offline Player".equals(username)) {
                tvPlayerName.setText("Welcome back, " + username);
            } else if ("offline_user".equals(userId)) {
                tvPlayerName.setText("Playing Offline Mode");
            } else {
                tvPlayerName.setText("Party Game for 2 - 5 Players");
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
            if (customCard.getBackground() != null) {
                customCard.setBackground(customCard.getBackground().mutate());
            }
            customCard.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), CustomModeActivity.class);
                startActivity(intent);
            });
        }

        // Quick Multiplayer
        View btnMultiplayer = view.findViewById(R.id.btnQuickMultiplayer);
        if (btnMultiplayer != null) {
            btnMultiplayer.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), LobbyActivity.class);
                intent.putExtra(LobbyActivity.EXTRA_MODE, GameMode.ANIMALS.name());
                startActivity(intent);
            });
        }

        // Quick Offline Practice
        View btnOffline = view.findViewById(R.id.btnQuickOffline);
        if (btnOffline != null) {
            btnOffline.setOnClickListener(v -> {
                startActivity(GameActivity.intent(
                        requireContext(), GameMode.ANIMALS, "practice", 60_000L));
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
                Intent intent = new Intent(requireContext(), LobbyActivity.class);
                intent.putExtra(LobbyActivity.EXTRA_MODE, mode.name());
                startActivity(intent);
            });
        }
    }
}
