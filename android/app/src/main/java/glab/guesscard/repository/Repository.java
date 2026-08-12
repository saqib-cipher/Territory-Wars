package glab.guesscard.repository;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import glab.guesscard.firebase.FirebaseManager;
import glab.guesscard.models.Friend;
import glab.guesscard.models.LeaderboardEntry;
import glab.guesscard.models.PlayerProfile;
import glab.guesscard.models.ShopItem;
import glab.guesscard.network.PreferenceManager;

/**
 * Data repository backed by Firebase Realtime Database.
 * Provides user profiles, leaderboard, friends, and inventory.
 */
public class Repository {

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Throwable t);
    }

    private final PreferenceManager preferences;
    private final FirebaseManager firebase;

    public Repository(PreferenceManager preferences, FirebaseManager firebase) {
        this.preferences = preferences;
        this.firebase = firebase;
    }

    // ── PROFILE ────────────────────────────────────────────────────────────

    public void getProfile(Callback<PlayerProfile> callback) {
        String uid = getCurrentUid();
        if (uid == null) {
            callback.onError(new Exception("Not signed in"));
            return;
        }
        firebase.getUserProfile(uid, data -> {
            if (data == null) {
                PlayerProfile p = new PlayerProfile();
                p.username = "Player";
                p.level = 1;
                p.xp = 0;
                p.xpToNext = 100;
                callback.onSuccess(p);
            } else {
                PlayerProfile p = new PlayerProfile();
                p.username = (String) data.getOrDefault("displayName", "Player");
                Object lvl = data.get("level");
                p.level = lvl instanceof Long ? ((Long) lvl).intValue() : 1;
                Object xp = data.get("xp");
                p.xp = xp instanceof Long ? ((Long) xp).intValue() : 0;
                p.xpToNext = Math.max(100, (p.level * 100));
                callback.onSuccess(p);
            }
        }, msg -> callback.onError(new Exception(msg)));
    }

    public void updateUsername(String newName, Callback<PlayerProfile> callback) {
        String uid = getCurrentUid();
        if (uid == null) {
            callback.onError(new Exception("Not signed in"));
            return;
        }
        firebase.updateDisplayName(uid, newName);
        preferences.saveSession(preferences.getToken(), uid, newName, false);
        getProfile(callback);
    }

    // ── LEADERBOARD ────────────────────────────────────────────────────────

    public void getLeaderboard(String scope, int limit, Callback<List<LeaderboardEntry>> callback) {
        firebase.getLeaderboardRef(limit).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<LeaderboardEntry> list = new ArrayList<>();
                int rank = 1;
                for (DataSnapshot child : snapshot.getChildren()) {
                    LeaderboardEntry entry = new LeaderboardEntry();
                    entry.rank = rank++;
                    entry.username = child.child("displayName").getValue(String.class);
                    if (entry.username == null) entry.username = "Unknown";
                    Long scoreVal = child.child("score").getValue(Long.class);
                    entry.score = scoreVal != null ? scoreVal.intValue() : 0;
                    list.add(0, entry); // reverse so highest is first
                }
                callback.onSuccess(list);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                callback.onError(new Exception(error.getMessage()));
            }
        });
    }

    // ── FRIENDS ────────────────────────────────────────────────────────────

    public void getFriends(Callback<List<Friend>> callback) {
        callback.onSuccess(new ArrayList<>());
    }

    public void addFriend(String usernameOrId, Callback<Void> callback) {
        callback.onSuccess(null);
    }

    public void acceptFriend(String userId, Callback<Void> callback) {
        callback.onSuccess(null);
    }

    public void removeFriend(String userId, Callback<Void> callback) {
        callback.onSuccess(null);
    }

    // ── INVENTORY ──────────────────────────────────────────────────────────

    public void getInventory(Callback<List<ShopItem>> callback) {
        callback.onSuccess(new ArrayList<>());
    }

    public void equipItem(String id, Callback<Void> callback) {
        callback.onSuccess(null);
    }

    public void uploadCloudSave(Map<String, Object> data, Callback<Void> callback) {
        if (callback != null) callback.onSuccess(null);
    }

    public void reportMatch(Map<String, Object> body, Callback<Void> callback) {
        if (callback != null) callback.onSuccess(null);
    }

    // ── HELPERS ────────────────────────────────────────────────────────────

    private String getCurrentUid() {
        if (firebase.getCurrentUser() != null) {
            return firebase.getCurrentUser().getUid();
        }
        return preferences.getUserId();
    }
}
