package com.territorywars.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.territorywars.di.GameContainer;
import com.territorywars.models.MatchResult;
import com.territorywars.models.PlayerProfile;
import com.territorywars.network.ApiService;
import com.territorywars.repository.Repository;

import java.util.List;

/**
 * Home screen state: profile summary + recent matches + daily reward status.
 */
public class HomeViewModel extends ViewModel {

    private final Repository repository;

    private final MutableLiveData<PlayerProfile> profile = new MutableLiveData<>();
    private final MutableLiveData<List<MatchResult>> recentMatches = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public HomeViewModel(GameContainer container) {
        this.repository = container.getRepository();
    }

    public void refresh() {
        loading.setValue(true);
        repository.getProfile(new Repository.Callback<PlayerProfile>() {
            @Override
            public void onResult(PlayerProfile value) {
                profile.setValue(value);
                loading.setValue(false);
            }

            @Override
            public void onError(String message) {
                error.setValue(message);
                loading.setValue(false);
            }
        });
        repository.getMatchHistory(20, new Repository.Callback<List<MatchResult>>() {
            @Override
            public void onResult(List<MatchResult> value) {
                recentMatches.setValue(value);
            }

            @Override
            public void onError(String message) {
                // non-critical
            }
        });
    }

    public LiveData<PlayerProfile> getProfile() {
        return profile;
    }

    public LiveData<List<MatchResult>> getRecentMatches() {
        return recentMatches;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Boolean> isLoading() {
        return loading;
    }

    /** Claims the daily reward; UI shows a dialog on success. */
    public void claimDailyReward(Repository.Callback<ApiService.DailyRewardResponse> callback) {
        repository.claimDailyReward(callback);
    }
}