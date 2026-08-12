package glab.guesscard.network;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceManager {
    private static final String PREF_NAME = "guesscard_prefs";
    private static final String KEY_TOKEN = "auth_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_IS_GUEST = "is_guest";
    private static final String KEY_THEME = "theme_mode";
    private static final String KEY_ADS_REMOVED = "ads_removed";
    private static final String KEY_PREMIUM = "premium_active";
    private static final String KEY_MATCHES = "matches_played";

    private final SharedPreferences prefs;

    public PreferenceManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveSession(String token, String userId, String username, boolean isGuest) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_USERNAME, username)
                .putBoolean(KEY_IS_GUEST, isGuest)
                .apply();
    }

    public void saveToken(String token) {
        prefs.edit().putString(KEY_TOKEN, token).apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    /** Kept for compatibility; primary auth check should use FirebaseAuth.getCurrentUser() */
    public boolean isLoggedIn() {
        return getToken() != null && !getToken().isEmpty();
    }

    public void saveUserId(String userId) {
        prefs.edit().putString(KEY_USER_ID, userId).apply();
    }

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, "Player");
    }

    public void saveUsername(String username) {
        prefs.edit().putString(KEY_USERNAME, username).apply();
    }

    private static final String KEY_AVATAR_INDEX = "avatar_index";
    private static final String KEY_AVATAR_FILE = "avatar_file_name";

    public int getAvatarIndex() {
        return prefs.getInt(KEY_AVATAR_INDEX, 0);
    }

    public void saveAvatarIndex(int index) {
        prefs.edit().putInt(KEY_AVATAR_INDEX, index).apply();
    }

    public String getAvatarFileName() {
        return prefs.getString(KEY_AVATAR_FILE, "avatar_01.png");
    }

    public void saveAvatarFileName(String fileName) {
        prefs.edit().putString(KEY_AVATAR_FILE, fileName).apply();
    }

    public int getThemeMode() {
        return prefs.getInt(KEY_THEME, 0);
    }

    public void setThemeMode(int mode) {
        prefs.edit().putInt(KEY_THEME, mode).apply();
    }

    public boolean isAdsRemoved() {
        return prefs.getBoolean(KEY_ADS_REMOVED, false);
    }

    public void setAdsRemoved(boolean removed) {
        prefs.edit().putBoolean(KEY_ADS_REMOVED, removed).apply();
    }

    public boolean isPremiumActive() {
        return prefs.getBoolean(KEY_PREMIUM, false);
    }

    public void setPremiumActive(boolean active) {
        prefs.edit().putBoolean(KEY_PREMIUM, active).apply();
    }

    public int incrementMatchesPlayed() {
        int val = prefs.getInt(KEY_MATCHES, 0) + 1;
        prefs.edit().putInt(KEY_MATCHES, val).apply();
        return val;
    }

    public String getGraphicsQuality() { return "HIGH"; }
    public int getFpsCap() { return 60; }

    public void clearSession() {
        prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_USERNAME)
                .remove(KEY_IS_GUEST)
                .apply();
    }
}
