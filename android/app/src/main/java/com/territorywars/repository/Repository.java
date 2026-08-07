package com.territorywars.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.territorywars.models.Achievement;
import com.territorywars.models.Friend;
import com.territorywars.models.LeaderboardEntry;
import com.territorywars.models.MatchResult;
import com.territorywars.models.PlayerProfile;
import com.territorywars.models.ShopItem;
import com.territorywars.network.ApiClient;
import com.territorywars.network.ApiService;
import com.territorywars.network.PreferenceManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;
import retrofit2.Call;

/**
 * Single source of truth consumed by ViewModels.
 *
 * <p>Wraps the {@link ApiService}, moves calls off the main thread, and posts
 * results back on the main thread. Network failures are surfaced as user
 * readable messages via {@link Result}.</p>
 */
public class Repository {

    /** Small result wrapper used across async boundaries. */
    public interface Callback<T> {
        void onResult(T value);

        void onError(String message);
    }

    private final ApiClient apiClient;
    private final PreferenceManager preferences;
    private final ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public Repository(PreferenceManager preferences, ApiClient apiClient) {
        this.preferences = preferences;
        this.apiClient = apiClient;
        this.executor = Executors.newFixedThreadPool(2);
    }

    private String token() {
        return "Bearer " + preferences.getToken();
    }

    // ---- Profile ----

    public void getProfile(Callback<PlayerProfile> callback) {
        executor.execute(() -> execute(
                apiClient.getApi().getProfile(token()),
                callback));
    }

    public void updateUsername(String username, Callback<PlayerProfile> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        executor.execute(() -> execute(
                apiClient.getApi().updateProfile(token(), body),
                callback));
    }

    // ---- Leaderboard ----

    public void getLeaderboard(String scope, int limit, Callback<List<LeaderboardEntry>> callback) {
        executor.execute(() -> execute(
                apiClient.getApi().getLeaderboard(token(), scope, limit),
                callback));
    }

    // ---- Friends ----

    public void getFriends(Callback<List<Friend>> callback) {
        executor.execute(() -> execute(apiClient.getApi().getFriends(token()), callback));
    }

    public void addFriend(String usernameOrId, Callback<Void> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("target", usernameOrId);
        executor.execute(() -> execute(apiClient.getApi().sendFriendRequest(token(), body), callback));
    }

    public void acceptFriend(String userId, Callback<Void> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("userId", userId);
        executor.execute(() -> execute(apiClient.getApi().acceptFriendRequest(token(), body), callback));
    }

    public void removeFriend(String userId, Callback<Void> callback) {
        executor.execute(() -> execute(apiClient.getApi().removeFriend(token(), userId), callback));
    }

    // ---- Shop / Inventory ----

    public void getShop(Callback<List<ShopItem>> callback) {
        executor.execute(() -> execute(apiClient.getApi().getShop(token()), callback));
    }

    public void getInventory(Callback<List<ShopItem>> callback) {
        executor.execute(() -> execute(apiClient.getApi().getInventory(token()), callback));
    }

    public void equipItem(String itemId, Callback<Void> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("itemId", itemId);
        executor.execute(() -> execute(apiClient.getApi().equipItem(token(), body), callback));
    }

    public void buyWithCoins(String itemId, Callback<PlayerProfile> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("itemId", itemId);
        executor.execute(() -> execute(apiClient.getApi().buyWithCoins(token(), body), callback));
    }

    // ---- Achievements ----

    public void getAchievements(Callback<List<Achievement>> callback) {
        executor.execute(() -> execute(apiClient.getApi().getAchievements(token()), callback));
    }

    // ---- Matches ----

    public void getMatchHistory(int limit, Callback<List<MatchResult>> callback) {
        executor.execute(() -> execute(apiClient.getApi().getMatchHistory(token(), limit), callback));
    }

    public void reportMatch(Map<String, Object> result, Callback<ApiService.MatchReportResponse> callback) {
        executor.execute(() -> execute(
                apiClient.getApi().reportMatch(token(), result),
                callback));
    }

    // ---- Daily rewards ----

    public void claimDailyReward(Callback<ApiService.DailyRewardResponse> callback) {
        executor.execute(() -> execute(
                apiClient.getApi().claimDailyReward(token()),
                callback));
    }

    // ---- Cloud save ----

    public void uploadCloudSave(Map<String, Object> data, Callback<Void> callback) {
        executor.execute(() -> execute(
                apiClient.getApi().uploadCloudSave(token(), data),
                callback));
    }

    public void fetchCloudSave(Callback<ApiService.CloudSaveResponse> callback) {
        executor.execute(() -> execute(apiClient.getApi().getCloudSave(token()), callback));
    }

    // ---- Internal helpers ----

    private <T> void execute(Call<T> call, Callback<T> callback) {
        executor.execute(() -> {
            try {
                Response<T> response = call.execute();
                if (response.isSuccessful() && response.body() != null) {
                    mainHandler.post(() -> callback.onResult(response.body()));
                } else {
                    String msg = ApiClient.parseError(response) != null
                            ? ApiClient.parseError(response).message : "Request failed";
                    String finalMsg = msg != null ? msg : "Request failed";
                    mainHandler.post(() -> callback.onError(finalMsg));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(
                        e.getMessage() != null ? e.getMessage() : "Network error"));
            }
        });
    }
}