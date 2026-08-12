package glab.guesscard.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import glab.guesscard.di.GameContainer;
import glab.guesscard.models.PlayerProfile;
import glab.guesscard.repository.Repository;

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
            public void onSuccess(PlayerProfile value) {
                profile.setValue(value);
            }

            @Override
            public void onError(Throwable t) {
                error.setValue(t != null ? t.getMessage() : "Error");
            }
        });
    }

    public void rename(String newName) {
        repository.updateUsername(newName, new Repository.Callback<PlayerProfile>() {
            @Override
            public void onSuccess(PlayerProfile value) {
                profile.setValue(value);
            }

            @Override
            public void onError(Throwable t) {
                error.setValue(t != null ? t.getMessage() : "Error");
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