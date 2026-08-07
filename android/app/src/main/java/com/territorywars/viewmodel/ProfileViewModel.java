package com.territorywars.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.territorywars.di.GameContainer;
import com.territorywars.models.PlayerProfile;
import com.territorywars.repository.Repository;

/**
 * Profile screen state (avatar, username, level, XP, statistics).
 */
public class ProfileViewModel extends ViewModel {

    private final Repository repository;

    private final MutableLiveData<PlayerProfile> profile = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public ProfileViewModel(GameContainer container) {
        this.repository = container.getRepository();
    }

    public void loadProfile() {
        repository.getProfile(new Repository.Callback<PlayerProfile>() {
            @Override
            public void onResult(PlayerProfile value) {
                profile.setValue(value);
            }

            @Override
            public void onError(String message) {
                error.setValue(message);
            }
        });
    }

    public void rename(String newName) {
        repository.updateUsername(newName, new Repository.Callback<PlayerProfile>() {
            @Override
            public void onResult(PlayerProfile value) {
                profile.setValue(value);
            }

            @Override
            public void onError(String message) {
                error.setValue(message);
            }
        });
    }

    public LiveData<PlayerProfile> getProfile() {
        return profile;
    }

    public LiveData<String> getError() {
        return error;
    }
}