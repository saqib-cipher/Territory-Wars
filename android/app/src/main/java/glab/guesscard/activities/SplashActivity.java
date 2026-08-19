package glab.guesscard.activities;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import glab.guesscard.R;

/**
 * Splash: Displays branded loading screen with animated logo & progress indicator,
 * checks Firebase auth state, syncs cloud profile, and routes smoothly to Auth or Main.
 */
public class SplashActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView logo = findViewById(R.id.splashLogo);
        if (logo != null) {
            ObjectAnimator pulse = ObjectAnimator.ofFloat(logo, View.ALPHA, 0.4f, 1.0f);
            pulse.setDuration(800);
            pulse.setRepeatCount(ObjectAnimator.INFINITE);
            pulse.setRepeatMode(ObjectAnimator.REVERSE);
            pulse.start();
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String uid = user.getUid();
            container().getFirebaseManager().syncUserProfileOnLogin(uid, prefs(), () -> {
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        } else {
            // Brief 800ms presentation delay for brand feel if logging in for first time
            findViewById(R.id.splashLogo).postDelayed(() -> {
                if (isFinishing()) return;
                Intent intent = new Intent(SplashActivity.this, AuthActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }, 800);
        }
    }
}
