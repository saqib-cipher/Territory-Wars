package com.territorywars.theme;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.DynamicColors;

/**
 * Central theme controller.
 *
 * Supports the four Settings options:
 * <ul>
 *   <li>System  — follows the OS light/dark preference</li>
 *   <li>Light   — always light</li>
 *   <li>Dark    — always dark</li>
 *   <li>Dynamic — light/dark from system AND Material You colour extraction</li>
 * </ul>
 */
public final class ThemeManager {

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;
    public static final int THEME_DYNAMIC = 3;

    private ThemeManager() {
    }

    /**
     * Maps the stored theme integer to the AppCompat night-mode value.
     */
    public static void applyTheme(int theme) {
        switch (theme) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case THEME_DYNAMIC:
            case THEME_SYSTEM:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    /**
     * Should be invoked early in Activity.onCreate(). When dynamic colour is
     * enabled on Android 12+, Material You palettes are used instead of the
     * static basalt palette declared in themes.xml.
     */
    public static void applyDynamicColor(Activity activity, int themeMode) {
        if (themeMode == THEME_DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivitiesIfAvailable(activity.getApplication());
        }
    }
}