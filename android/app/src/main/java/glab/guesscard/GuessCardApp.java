package glab.guesscard;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.MobileAds;
import com.google.firebase.FirebaseApp;

import java.lang.ref.WeakReference;

import glab.guesscard.di.GameContainer;
import glab.guesscard.notifications.NotificationHelper;

/**
 * Guess Card – Application entry point.
 * Initialises Firebase, Ads, Notifications, DI container,
 * and maintains reference to the foreground Activity for global dialogs.
 */
public class GuessCardApp extends Application {

    private GameContainer container;
    private static WeakReference<Activity> currentForegroundActivity = null;

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        container = new GameContainer(this);
        initAds();
        NotificationHelper.ensureChannels(this);
        registerForegroundTracker();
    }

    private void registerForegroundTracker() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                currentForegroundActivity = new WeakReference<>(activity);
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                currentForegroundActivity = new WeakReference<>(activity);
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {}

            @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                currentForegroundActivity = new WeakReference<>(activity);
            }
            @Override public void onActivityStopped(@NonNull Activity activity) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
            @Override public void onActivityDestroyed(@NonNull Activity activity) {
                if (currentForegroundActivity != null && currentForegroundActivity.get() == activity) {
                    currentForegroundActivity = null;
                }
            }
        });
    }

    @Nullable
    public static Activity getCurrentActivity() {
        return currentForegroundActivity != null ? currentForegroundActivity.get() : null;
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
