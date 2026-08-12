package glab.guesscard.services;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import glab.guesscard.di.GameContainer;
import glab.guesscard.repository.Repository;

import java.util.HashMap;
import java.util.Map;

/**
 * Cloud-save sync. Uploads lightweight client state (settings, cosmetic
 * picks) whenever the profile changes; keeps a periodic safety net.
 */
public class CloudSaveService {

    private static final long SYNC_PERIOD_MS = 10 * 60 * 1000L;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable syncTask = this::syncNow;

    public CloudSaveService(Context context) {
        this.context = context.getApplicationContext();
    }

    public void start() {
        handler.removeCallbacks(syncTask);
        handler.postDelayed(syncTask, SYNC_PERIOD_MS);
    }

    public void stop() {
        handler.removeCallbacks(syncTask);
    }

    /** Pushes a snapshot of local, non-authoritative progress. */
    public void syncNow() {
        GameContainer container = glab.guesscard.GuessCardApp.from(context);
        if (!container.getPreferences().isLoggedIn()) return;

        Map<String, Object> data = new HashMap<>();
        data.put("settings", snapshotSettings(container));
        data.put("clientVersion", "1.0.0");

        container.getRepository().uploadCloudSave(data, new Repository.Callback<Void>() {
            @Override
            public void onSuccess(Void value) {
                // nothing to surface; next periodic run happens automatically
            }

            @Override
            public void onError(Throwable t) {
                // transient failure; the next interval retries
            }
        });
        handler.postDelayed(syncTask, SYNC_PERIOD_MS);
    }

    private Map<String, Object> snapshotSettings(GameContainer container) {
        Map<String, Object> settings = new HashMap<>();
        settings.put("theme", container.getPreferences().getThemeMode());
        settings.put("graphics", container.getPreferences().getGraphicsQuality());
        settings.put("fps", container.getPreferences().getFpsCap());
        return settings;
    }
}