package glab.guesscard.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import glab.guesscard.di.GameContainer;
import glab.guesscard.models.LeaderboardEntry;
import glab.guesscard.repository.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Leaderboard state; scope switches between global/friends/weekly/monthly.
 */
public class LeaderboardViewModel extends ViewModel {

    private final Repository repository;

    private final MutableLiveData<List<LeaderboardEntry>> entries =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> scope = new MutableLiveData<>("global");
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public LeaderboardViewModel(GameContainer container) {
        this.repository = container.getRepository();
    }

    public void setScope(String scope) {
        this.scope.setValue(scope);
        load();
    }

    public void load() {
        String s = scope.getValue();
        if (s == null) s = "global";
        repository.getLeaderboard(s, 50, new Repository.Callback<List<LeaderboardEntry>>() {
            @Override
            public void onSuccess(List<LeaderboardEntry> value) {
                entries.setValue(value);
            }

            @Override
            public void onError(Throwable t) {
                error.setValue(t != null ? t.getMessage() : "Error");
            }
        });
    }

    public LiveData<List<LeaderboardEntry>> getEntries() {
        return entries;
    }

    public LiveData<String> getScope() {
        return scope;
    }

    public LiveData<String> getError() {
        return error;
    }
}