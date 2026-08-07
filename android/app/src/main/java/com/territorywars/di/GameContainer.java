package com.territorywars.di;

import android.content.Context;

import com.territorywars.ads.AdManager;
import com.territorywars.auth.AuthManager;
import com.territorywars.network.ApiClient;
import com.territorywars.network.PreferenceManager;
import com.territorywars.repository.Repository;
import com.territorywars.socket.GameSocketClient;

public class GameContainer {
    private static GameContainer instance;

    private final PreferenceManager preferences;
    private final ApiClient apiClient;
    private final Repository repository;
    private final AuthManager authManager;
    private final GameSocketClient socketClient;
    private final AdManager adManager;

    public GameContainer(Context context) {
        Context appCtx = context.getApplicationContext();
        this.preferences = new PreferenceManager(appCtx);
        this.apiClient = new ApiClient(preferences);
        this.repository = new Repository(apiClient, preferences);
        this.authManager = new AuthManager(apiClient, preferences);
        this.socketClient = new GameSocketClient(preferences);
        this.adManager = new AdManager(appCtx);
    }

    public static synchronized GameContainer getInstance(Context context) {
        if (instance == null) {
            instance = new GameContainer(context);
        }
        return instance;
    }

    public PreferenceManager getPreferences() { return preferences; }
    public ApiClient getApiClient() { return apiClient; }
    public Repository getRepository() { return repository; }
    public AuthManager getAuthManager() { return authManager; }
    public GameSocketClient getSocketClient() { return socketClient; }
    public AdManager getAdManager() { return adManager; }
}
