package glab.guesscard.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;

/**
 * Splash: checks Firebase auth state and routes to Auth or Main.
 */
public class SplashActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean isSignedIn = FirebaseAuth.getInstance().getCurrentUser() != null;
        Class<?> target = isSignedIn ? MainActivity.class : AuthActivity.class;

        Intent intent = new Intent(this, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
