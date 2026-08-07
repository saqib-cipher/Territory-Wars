package com.territorywars.fragments;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.territorywars.R;
import com.territorywars.activities.GameActivity;
import com.territorywars.adapters.ScoreboardAdapter;
import com.territorywars.di.GameContainer;
import com.territorywars.game.engine.Game;
import com.territorywars.game.entities.Player;
import com.territorywars.game.tiles.Tile;
import com.territorywars.game.view.GameView;
import com.territorywars.game.view.MiniMapView;
import com.territorywars.viewmodel.GameViewModel;
import com.territorywars.viewmodel.ViewModelFactory;

import java.util.Locale;

/**
 * In-game fragment: renders the game, updates the HUD, and publishes the end
 * of a match (result bottom sheet + ad cadence).
 */
public class GameFragment extends Fragment implements GameView.GameListener {

    private GameViewModel viewModel;
    private GameView gameView;
    private MiniMapView miniMap;
    private TextView timerText;
    private TextView scoreText;
    private MaterialButton pauseButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_game, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        GameContainer container = com.territorywars.TerritoryWarsApp.from(requireContext());
        viewModel = new ViewModelProvider(this, new ViewModelFactory(container))
                .get(GameViewModel.class);

        gameView = view.findViewById(R.id.gameView);
        miniMap = view.findViewById(R.id.miniMap);
        timerText = view.findViewById(R.id.matchTimer);
        scoreText = view.findViewById(R.id.scoreText);
        pauseButton = view.findViewById(R.id.pauseButton);

        gameView.setGameListener(this);
        pauseButton.setOnClickListener(v -> onPause());

        Game game = viewModel.getGame().getValue();
        if (game == null) {
            // started as offline practice from Home or Play
            if (savedInstanceState == null) viewModel.startOfflineGame();
            game = viewModel.getGame().getValue();
        }
        if (game != null) {
            gameView.setGame(game);
            miniMap.setGame(game);
        }

        viewModel.getFinishedResult().observe(getViewLifecycleOwner(),
                result -> showMatchResult(result));

        // fallback: bind game after async start
        viewModel.getGame().observe(getViewLifecycleOwner(), g -> {
            if (g != null && gameView != null) {
                gameView.setGame(g);
                miniMap.setGame(g);
            }
        });

        gameView.start();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (gameView != null) gameView.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (gameView != null) gameView.stop();
    }

    @Override
    public void onDestroyView() {
        if (gameView != null) {
            gameView.stop();
            gameView.setGameListener(null);
        }
        super.onDestroyView();
    }

    // ---- GameView.GameListener ----

    @Override
    public void onTileCaptured(Player.TileCaptured capture, Tile tile) {
        if (tile != null) {
            scoreText.setText(tile.owner + " · " + tile.gridX + "," + tile.gridY);
        }
    }

    @Override
    public void onMatchEnded() {
        viewModel.onMatchFinished();
    }

    // ---- HUD / dialogs ----

    private void onPause() {
        gameView.stop();
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Paused")
                .setMessage("Timer is not running.")
                .setPositiveButton("Resume", (d, w) -> { gameView.start();
                    showTimerTick(); })
                .setNegativeButton("Quit", (d, w) -> requireActivity().finish())
                .show();
    }

    private void showTimerTick() {
        // placeholder: full HUD timer is driven by the GameViewModel in a full build
    }

    /** Displays the result bottom sheet after a finished match. */
    private void showMatchResult(com.territorywars.models.MatchResult result) {
        androidx.recyclerview.widget.RecyclerView list = getView().findViewById(R.id.scoreboardList);
        list.setVisibility(View.VISIBLE);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(new ScoreboardAdapter(1, result.score,
                result.tilesCaptured, result.xpEarned, result.coinsEarned));
    }
}