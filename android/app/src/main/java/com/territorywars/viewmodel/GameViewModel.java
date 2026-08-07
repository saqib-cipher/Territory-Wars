package com.territorywars.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.territorywars.di.GameContainer;
import com.territorywars.game.engine.Game;
import com.territorywars.models.GameMode;
import com.territorywars.models.MatchResult;
import com.territorywars.network.ApiService;
import com.territorywars.network.PreferenceManager;
import com.territorywars.repository.Repository;

import java.util.HashMap;
import java.util.Map;

/**
 * Owns the live match: builds the {@link Game} for the chosen mode, exposes
 * ticker state, and reports results so rewards can be granted.
 */
public class GameViewModel extends ViewModel {

    public static final long DEFAULT_MATCH_DURATION_MS = 120_000L;

    private final Repository repository;
    private final PreferenceManager preferences;

    private final MutableLiveData<Game> game = new MutableLiveData<>();
    private final MutableLiveData<MatchResult> finishedResult = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private int playerCount = 6;

    public GameViewModel(GameContainer container) {
        this.repository = container.getRepository();
        this.preferences = container.getPreferences();
    }

    /** Builds the simulation for offline practice or online mirror. */
    public void startOfflineGame() {
        Game g = new Game(GameMode.OFFLINE, "practice_islands", DEFAULT_MATCH_DURATION_MS);
        g.startMatch(System.currentTimeMillis(), playerCount);
        game.setValue(g);
    }

    public void startOnlineGame(String mapId, long durationMs, int expectedPlayers) {
        Game g = new Game(GameMode.CLASSIC, mapId, durationMs);
        g.startMatch(System.currentTimeMillis(), expectedPlayers);
        game.setValue(g);
    }

    /** Called by the HUD when the timer hits zero. */
    public void onMatchFinished() {
        Game g = game.getValue();
        if (g == null) return;

        boolean won = g.winner() == g.localPlayer;
        MatchResult result = new MatchResult();
        result.matchId = "local-" + System.currentTimeMillis();
        result.mode = g.mode.name();
        result.won = won;
        result.rank = g.myRank();
        result.score = g.localPlayer != null ? g.localPlayer.score : 0;
        result.tilesCaptured = g.localPlayer != null ? g.localPlayer.tilesCaptured : 0;
        result.xpEarned = 50 + result.score / 10;
        result.coinsEarned = 100 + result.score;
        result.durationSeconds = (int) (g.matchDurationMillis / 1000L);
        result.playedAtEpochMillis = System.currentTimeMillis();

        finishedResult.setValue(result);
        reportToServer(result);
    }

    private void reportToServer(MatchResult result) {
        Map<String, Object> body = new HashMap<>();
        body.put("matchId", result.matchId);
        body.put("mode", result.mode);
        body.put("won", result.won);
        body.put("rank", result.rank);
        body.put("score", result.score);
        body.put("tilesCaptured", result.tilesCaptured);
        repository.reportMatch(body, new Repository.Callback<ApiService.MatchReportResponse>() {
            @Override
            public void onResult(ApiService.MatchReportResponse value) {
                // rewards are server-authoritative; HUD refreshes profile next.
            }

            @Override
            public void onError(String message) {
                error.setValue(message);
            }
        });
    }

    /** Called after every finished match for the interstitial cadence. */
    public boolean shouldShowInterstitial() {
        int count = preferences.incrementMatchesPlayed();
        return count % 3 == 0;
    }

    public LiveData<Game> getGame() {
        return game;
    }

    public LiveData<MatchResult> getFinishedResult() {
        return finishedResult;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void setPlayerCount(int count) {
        this.playerCount = count;
    }
}