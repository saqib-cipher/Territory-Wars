package com.territorywars.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.territorywars.database.RecentMatchEntity;
import com.territorywars.models.User;
import com.territorywars.network.ApiService;
import com.territorywars.repository.Repository;

import java.util.List;

public class HomeViewModel extends ViewModel {
    private final Repository repository;
    private final MutableLiveData<User> profile = new MutableLiveData<>();
    private final MutableLiveData<List<RecentMatchEntity>> recentMatches = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public HomeViewModel(Repository repository) {
        this.repository = repository;
    }

    public LiveData<User> getProfile() { return profile; }
    public LiveData<List<RecentMatchEntity>> getRecentMatches() { return recentMatches; }
    public LiveData<String> getError() { return error; }
    public void refresh() {}

    public void claimDailyReward(Repository.Callback<ApiService.DailyRewardResponse> callback) {
        if (callback != null) callback.onSuccess(new ApiService.DailyRewardResponse());
    }
}
