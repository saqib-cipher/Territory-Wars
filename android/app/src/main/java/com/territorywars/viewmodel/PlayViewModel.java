package com.territorywars.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.territorywars.models.GameMode;
import com.territorywars.repository.Repository;

public class PlayViewModel extends ViewModel {
    private final Repository repository;
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<GameMode> joinEvent = new MutableLiveData<>();

    public PlayViewModel(Repository repository) {
        this.repository = repository;
    }

    public void quickMatch() {}
    public void quickRanked() {}
    public void createPrivateRoom() {}
    public LiveData<String> getError() { return error; }
    public LiveData<GameMode> getJoinEvent() { return joinEvent; }
}
