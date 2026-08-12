package glab.guesscard.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import glab.guesscard.di.GameContainer;
import glab.guesscard.models.GameMode;
import glab.guesscard.models.MatchResult;
import glab.guesscard.network.ApiService;
import glab.guesscard.network.PreferenceManager;
import glab.guesscard.repository.Repository;

import java.util.HashMap;
import java.util.Map;

/**
 * ViewModel for Guess the Card game matches. Handles reporting final match metrics
 * and interstitial ad cadence.
 */
public class GameViewModel extends ViewModel {
    public static final long DEFAULT_MATCH_DURATION_MS = 60_000L;

    private final Repository repository;
    private final PreferenceManager preferences;
    private final MutableLiveData<MatchResult> finishedResult = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public GameViewModel(GameContainer container) {
        this.repository = container.getRepository();
        this.preferences = container.getPreferences();
    }

    public void onMatchFinished(String matchId, String mode, int score, boolean won) {
        MatchResult result = new MatchResult();
        result.matchId = matchId != null ? matchId : "local-" + System.currentTimeMillis();
        result.mode = mode != null ? mode : GameMode.ANIMALS.name();
        result.won = won;
        result.score = score;
        result.xpEarned = 50 + score / 10;
        result.coinsEarned = 100 + score;
        result.playedAtEpochMillis = System.currentTimeMillis();

        finishedResult.setValue(result);
        reportToServer(result);
    }

    private void reportToServer(MatchResult result) {
        Map<String, Object> body = new HashMap<>();
        body.put("matchId", result.matchId);
        body.put("mode", result.mode);
        body.put("won", result.won);
        body.put("score", result.score);

        repository.reportMatch(body, new Repository.Callback<Void>() {
            @Override
            public void onSuccess(Void value) {}

            @Override
            public void onError(Throwable t) {
                error.setValue(t != null ? t.getMessage() : "Error");
            }
        });
    }

    public boolean shouldShowInterstitial() {
        int count = preferences.incrementMatchesPlayed();
        return count % 3 == 0;
    }

    public LiveData<MatchResult> getFinishedResult() {
        return finishedResult;
    }

    public LiveData<String> getError() {
        return error;
    }
}
