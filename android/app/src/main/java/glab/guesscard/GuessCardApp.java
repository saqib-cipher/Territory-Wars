package glab.guesscard;

import android.app.Application;
import android.content.Context;
import com.google.android.gms.ads.MobileAds;
import com.google.firebase.FirebaseApp;
import glab.guesscard.di.GameContainer;
import glab.guesscard.notifications.NotificationHelper;

/**
 * Guess Card – Application entry point.
 * Initialises Firebase, Ads, Notifications, and the DI container.
 */
public class GuessCardApp extends Application {

    private GameContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        container = new GameContainer(this);
        initAds();
        NotificationHelper.ensureChannels(this);
    }

    public GameContainer getContainer() {
        if (container == null) container = new GameContainer(this);
        return container;
    }

    /** Convenience accessor used by Fragments and Activities. */
    public static GameContainer from(Context context) {
        return ((GuessCardApp) context.getApplicationContext()).getContainer();
    }

    private void initAds() {
        MobileAds.initialize(this, status -> {
            // Ads load lazily — nothing urgent here
        });
    }
}
