package com.territorywars.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.territorywars.network.PreferenceManager;
import com.territorywars.theme.ThemeManager;

/**
 * Minimal splash that decides where the user goes next: Auth (first run /
 * logged out) or Main.
 */
public class SplashActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager prefs = prefs();
        Class<?> target = prefs.isLoggedIn() ? MainActivity.class : AuthActivity.class;

        Intent intent = new Intent(this, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}