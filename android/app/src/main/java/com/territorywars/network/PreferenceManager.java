package com.territorywars.network;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Encrypted local key/value storage.
 *
 * Stores auth tokens and lightweight, device-side settings such as the
 * selected theme, graphics quality and mute flags. Server-authoritative data
 * (coins, XP) is always re-fetched from the backend.
 */
public class PreferenceManager {

    private static final String FILE_NAME = "territory_wars_prefs";
    private static final String KEY_TOKEN = "auth_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_GUEST = "is_guest";
    private static final String KEY_THEME = "theme_mode";
    private static final String KEY_MUSIC = "music_enabled";
    private static final String KEY_SOUND = "sound_enabled";
    private static final String KEY_GFX = "graphics_quality";
    private static final String KEY_FPS = "fps_cap";
    private static final String KEY_SENS = "sensitivity";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_PREMIUM_UNTIL = "premium_expiry_millis";
    private static final String KEY_ADS_REMOVED = "ads_removed";
    private static final String KEY_MATCHES_PLAYED = "matches_played_counter";
    private static final String KEY_LAST_DAILY_CLAIM = "last_daily_claim";

    private final SharedPreferences prefs;

    public PreferenceManager(Context context) {
        SharedPreferences delegate = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            prefs = EncryptedSharedPreferences.create(
                    context,
                    FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException e) {
            // Plain fallback only used if the cipher is unavailable; never cache secrets
            // marked as sensitive in that case.
            prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
        }
    }

    // ---- Auth ----

    public void saveSession(String token, String userId, String username, boolean guest) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_USERNAME, username)
                .putBoolean(KEY_GUEST, guest)
                .apply();
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }

    public String getToken() { return prefs.getString(KEY_TOKEN, null); }

    public String getUserId() { return prefs.getString(KEY_USER_ID, null); }

    public String getUsername() { return prefs.getString(KEY_USERNAME, "Player"); }

    public boolean isGuest() { return prefs.getBoolean(KEY_GUEST, true); }

    public boolean isLoggedIn() {
        String token = getToken();
        return token != null && !token.isEmpty();
    }

    // ---- Settings ----

    public int getThemeMode() { return prefs.getInt(KEY_THEME, 3 /* dynamic */); }

    public void setThemeMode(int mode) { prefs.edit().putInt(KEY_THEME, mode).apply(); }

    public boolean isMusicEnabled() { return prefs.getBoolean(KEY_MUSIC, true); }

    public void setMusicEnabled(boolean on) { prefs.edit().putBoolean(KEY_MUSIC, on).apply(); }

    public boolean isSoundEnabled() { return prefs.getBoolean(KEY_SOUND, true); }

    public void setSoundEnabled(boolean on) { prefs.edit().putBoolean(KEY_SOUND, on).apply(); }

    public int getFpsCap() { return prefs.getInt(KEY_FPS, 60); }

    public void setFpsCap(int fps) { prefs.edit().putInt(KEY_FPS, fps).apply(); }

    public int getGraphicsQuality() { return prefs.getInt(KEY_GFX, 1); }

    public void setGraphicsQuality(int q) { prefs.edit().putInt(KEY_GFX, q).apply(); }

    public float getSensitivity() { return prefs.getFloat(KEY_SENS, 1f); }

    public void setSensitivity(float s) { prefs.edit().putFloat(KEY_SENS, s).apply(); }

    /** ISO 639-1 language code, "en" by default. */
    public String getLanguage() { return prefs.getString(KEY_LANGUAGE, "en"); }

    public void setLanguage(String code) { prefs.edit().putString(KEY_LANGUAGE, code).apply(); }

    // ---- Monetisation ----

    public boolean isAdsRemoved() { return prefs.getBoolean(KEY_ADS_REMOVED, false); }

    public void setAdsRemoved(boolean removed) { prefs.edit().putBoolean(KEY_ADS_REMOVED, removed).apply(); }

    public boolean isPremiumActive() {
        return prefs.getLong(KEY_PREMIUM_UNTIL, 0L) > System.currentTimeMillis();
    }

    public void setPremiumExpiry(long epochMillis) {
        prefs.edit().putLong(KEY_PREMIUM_UNTIL, epochMillis).apply();
    }

    // ---- Interstitial cadence ----

    public int incrementMatchesPlayed() {
        int count = prefs.getInt(KEY_MATCHES_PLAYED, 0) + 1;
        prefs.edit().putInt(KEY_MATCHES_PLAYED, count).apply();
        return count;
    }

    // ---- Daily rewards ----

    public long getLastDailyClaim() { return prefs.getLong(KEY_LAST_DAILY_CLAIM, 0L); }

    public void markDailyClaimed() {
        prefs.edit().putLong(KEY_LAST_DAILY_CLAIM, System.currentTimeMillis()).apply();
    }
}