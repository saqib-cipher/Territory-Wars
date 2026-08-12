package glab.guesscard.activities;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import glab.guesscard.GuessCardApp;
import glab.guesscard.di.GameContainer;
import glab.guesscard.network.PreferenceManager;

/**
 * Base activity: edge-to-edge insets, DI container accessor, and audio lifecycle.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-enable audio when returning to this activity
        try {
            glab.guesscard.audio.GameAudio audio = container().getAudio();
            if (audio != null) {
                audio.setSoundEnabled(true);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        applyEdgeToEdgeInsets();
    }

    protected void applyEdgeToEdgeInsets() {
        View root = findViewById(android.R.id.content);
        if (root != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
                androidx.core.graphics.Insets insets = windowInsets.getInsets(
                        androidx.core.view.WindowInsetsCompat.Type.systemBars());
                v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                return windowInsets;
            });
        }
    }

    protected GameContainer container() {
        return GuessCardApp.from(this);
    }

    protected PreferenceManager prefs() {
        return container().getPreferences();
    }
}
