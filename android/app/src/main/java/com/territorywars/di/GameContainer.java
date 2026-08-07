package com.territorywars.di;

import android.content.Context;

import com.territorywars.ads.AdManager;
import com.territorywars.auth.AuthManager;
import com.territorywars.billing.BillingManager;
import com.territorywars.database.AppDatabase;
import com.territorywars.network.ApiClient;
import com.territorywars.network.PreferenceManager;
import com.territorywars.repository.Repository;
import com.territorywars.socket.GameSocketClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manual dependency injection container.
 * Keeps the graph explicit and testable without a DI framework.
 *
 * <p>All heavy singletons (database, socket, billing) are created lazily and
 * shared across the app.</p>
 */
public class GameContainer {

    private final Context context;
    private final ExecutorService ioExecutor;

    private AppDatabase database;
    private PreferenceManager preferences;
    private ApiClient apiClient;
    private Repository repository;
    private GameSocketClient socketClient;
    private AuthManager authManager;
    private BillingManager billingManager;
    private AdManager adManager;

    public GameContainer(Context context) {
        this.context = context.getApplicationContext();
        this.ioExecutor = Executors.newFixedThreadPool(4);
        this.preferences = new PreferenceManager(context.getApplicationContext());
        this.apiClient = new ApiClient();
        this.repository = new Repository(preferences, apiClient);
        this.authManager = new AuthManager(repository, preferences);
        this.socketClient = new GameSocketClient(preferences);
        this.billingManager = new BillingManager(context.getApplicationContext());
    }

    public Context getContext() { return context; }

    public ExecutorService getIoExecutor() { return ioExecutor; }

    public PreferenceManager getPreferences() { return preferences; }

    public ApiClient getApiClient() { return apiClient; }

    public Repository getRepository() { return repository; }

    public GameSocketClient getSocketClient() { return socketClient; }

    public AuthManager getAuthManager() { return authManager; }

    public BillingManager getBillingManager() { return billingManager; }

    public AppDatabase getDatabase() {
        if (database == null) database = AppDatabase.getInstance(context);
        return database;
    }

    public AdManager getAdManager() {
        if (adManager == null) adManager = new AdManager(context);
        return adManager;
    }
}