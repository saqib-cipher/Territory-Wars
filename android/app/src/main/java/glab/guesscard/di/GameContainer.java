package glab.guesscard.di;

import android.content.Context;

import glab.guesscard.ads.AdManager;
import glab.guesscard.firebase.FirebaseManager;
import glab.guesscard.network.PreferenceManager;
import glab.guesscard.repository.Repository;
import glab.guesscard.socket.GameSocketClient;

/**
 * Manual dependency injection container for Guess Card.
 * Owns all singleton dependencies and wires them together.
 */
public class GameContainer {

    private static GameContainer instance;

    private final PreferenceManager preferences;
    private final Repository repository;
    private final FirebaseManager firebaseManager;
    private final GameSocketClient socketClient;
    private final AdManager adManager;

    public GameContainer(Context context) {
        Context appCtx = context.getApplicationContext();
        this.preferences = new PreferenceManager(appCtx);
        this.firebaseManager = new FirebaseManager();
        this.repository = new Repository(preferences, firebaseManager);
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
    public Repository getRepository() { return repository; }
    public FirebaseManager getFirebaseManager() { return firebaseManager; }
    public GameSocketClient getSocketClient() { return socketClient; }
    public AdManager getAdManager() { return adManager; }
}
