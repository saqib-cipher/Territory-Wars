package glab.guesscard.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import glab.guesscard.models.GameMode;
import glab.guesscard.repository.Repository;

public class PlayViewModel extends ViewModel {
    private final Repository repository;
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<GameMode> joinEvent = new MutableLiveData<>();

    public PlayViewModel(Repository repository) {
        this.repository = repository;
    }

    public void quickMatch() {
        joinEvent.setValue(GameMode.ANIMALS);
    }

    public void quickRanked() {
        joinEvent.setValue(GameMode.CELEBRITIES);
    }

    public void createPrivateRoom() {
        joinEvent.setValue(GameMode.FOOD);
    }
    public LiveData<String> getError() { return error; }
    public LiveData<GameMode> getJoinEvent() { return joinEvent; }
}
