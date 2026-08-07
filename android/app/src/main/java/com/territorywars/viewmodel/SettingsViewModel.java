package com.territorywars.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.territorywars.repository.Repository;

public class SettingsViewModel extends ViewModel {
    private final Repository repository;
    private final MutableLiveData<Boolean> music = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> sound = new MutableLiveData<>(true);

    public SettingsViewModel(Repository repository) {
        this.repository = repository;
    }

    public void setMusic(boolean b) { music.setValue(b); }
    public LiveData<Boolean> getMusic() { return music; }
    public void setSound(boolean b) { sound.setValue(b); }
    public LiveData<Boolean> getSound() { return sound; }
    public void setFps(int fps) {}
    public void setSensitivity(float val) {}
}
