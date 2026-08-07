package com.territorywars.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.territorywars.di.GameContainer;
import com.territorywars.network.PreferenceManager;
import com.territorywars.theme.ThemeManager;

/**
 * Settings state mirroring {@link PreferenceManager} with immediate write-back.
 */
public class SettingsViewModel extends ViewModel {

    private final PreferenceManager preferences;

    private final MutableLiveData<Integer> themeMode = new MutableLiveData<>();
    private final MutableLiveData<Boolean> music = new MutableLiveData<>();
    private final MutableLiveData<Boolean> sound = new MutableLiveData<>();
    private final MutableLiveData<Integer> graphics = new MutableLiveData<>();
    private final MutableLiveData<Integer> fps = new MutableLiveData<>();
    private final MutableLiveData<Float> sensitivity = new MutableLiveData<>();
    private final MutableLiveData<String> language = new MutableLiveData<>();

    public SettingsViewModel(GameContainer container) {
        this.preferences = container.getPreferences();
        themeMode.setValue(preferences.getThemeMode());
        music.setValue(preferences.isMusicEnabled());
        sound.setValue(preferences.isSoundEnabled());
        graphics.setValue(preferences.getGraphicsQuality());
        fps.setValue(preferences.getFpsCap());
        sensitivity.setValue(preferences.getSensitivity());
        language.setValue(preferences.getLanguage());
    }

    public void setTheme(int theme) {
        themeMode.setValue(theme);
        preferences.setThemeMode(theme);
        ThemeManager.applyTheme(theme);
    }

    public void setMusic(boolean on) {
        music.setValue(on);
        preferences.setMusicEnabled(on);
    }

    public void setSound(boolean on) {
        sound.setValue(on);
        preferences.setSoundEnabled(on);
    }

    public void setGraphics(int quality) {
        graphics.setValue(quality);
        preferences.setGraphicsQuality(quality);
    }

    public void setFps(int fps) {
        this.fps.setValue(fps);
        preferences.setFpsCap(fps);
    }

    public void setSensitivity(float sens) {
        sensitivity.setValue(sens);
        preferences.setSensitivity(sens);
    }

    public void setLanguage(String code) {
        language.setValue(code);
        preferences.setLanguage(code);
    }

    public LiveData<Integer> getThemeMode() { return themeMode; }

    public LiveData<Boolean> getMusic() { return music; }

    public LiveData<Boolean> getSound() { return sound; }

    public LiveData<Integer> getGraphics() { return graphics; }

    public LiveData<Integer> getFps() { return fps; }

    public LiveData<Float> getSensitivity() { return sensitivity; }

    public LiveData<String> getLanguage() { return language; }
}