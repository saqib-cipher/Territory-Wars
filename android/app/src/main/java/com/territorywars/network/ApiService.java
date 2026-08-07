package com.territorywars.network;

import com.territorywars.BuildConfig;
import com.google.gson.annotations.SerializedName;
import com.territorywars.models.Achievement;
import com.territorywars.models.Friend;
import com.territorywars.models.LeaderboardEntry;
import com.territorywars.models.MatchResult;
import com.territorywars.models.PlayerProfile;
import com.territorywars.models.ShopItem;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * REST API definition matching the /v1 routes exposed by the backend.
 */
public interface ApiService {

    // ---- Auth ----

    @POST("auth/login/guest")
    Call<AuthResponse> loginGuest();

    @POST("auth/login/google")
    Call<AuthResponse> loginGoogle(@Body Map<String, String> body);

    // ---- Profile ----

    @GET("profile")
    Call<PlayerProfile> getProfile(@Header("Authorization") String token);

    @POST("profile")
    Call<PlayerProfile> updateProfile(@Header("Authorization") String token, @Body Map<String, String> fields);

    // ---- Leaderboard ----

    @GET("leaderboard")
    Call<List<LeaderboardEntry>> getLeaderboard(
            @Header("Authorization") String token,
            @Query("scope") String scope,     // global | friends | weekly | monthly
            @Query("limit") int limit);

    // ---- Friends ----

    @GET("friends")
    Call<List<Friend>> getFriends(@Header("Authorization") String token);

    @POST("friends/request")
    Call<Void> sendFriendRequest(@Header("Authorization") String token, @Body Map<String, String> body);

    @POST("friends/accept")
    Call<Void> acceptFriendRequest(@Header("Authorization") String token, @Body Map<String, String> body);

    @DELETE("friends/{userId}")
    Call<Void> removeFriend(@Header("Authorization") String token, @Path("userId") String userId);

    // ---- Inventory / Shop ----

    @GET("shop")
    Call<List<ShopItem>> getShop(@Header("Authorization") String token);

    @GET("inventory")
    Call<List<ShopItem>> getInventory(@Header("Authorization") String token);

    @POST("inventory/equip")
    Call<Void> equipItem(@Header("Authorization") String token, @Body Map<String, String> body);

    @POST("shop/buy/coins")
    Call<PlayerProfile> buyWithCoins(@Header("Authorization") String token, @Body Map<String, String> body);

    // ---- Achievements ----

    @GET("achievements")
    Call<List<Achievement>> getAchievements(@Header("Authorization") String token);

    // ---- Matches ----

    @GET("match/history")
    Call<List<MatchResult>> getMatchHistory(@Header("Authorization") String token, @Query("limit") int limit);

    @POST("match/report")
    Call<MatchReportResponse> reportMatch(@Header("Authorization") String token, @Body Map<String, Object> result);

    // ---- Daily rewards ----

    @POST("daily-reward/claim")
    Call<DailyRewardResponse> claimDailyReward(@Header("Authorization") String token);

    // ---- Purchases ----

    @POST("purchase/verify")
    Call<PurchaseResult> verifyPurchase(@Header("Authorization") String token, @Body PurchaseRequest request);

    // ---- Cloud save ----

    @GET("cloud-save")
    Call<CloudSaveResponse> getCloudSave(@Header("Authorization") String token);

    @POST("cloud-save")
    Call<Void> uploadCloudSave(@Header("Authorization") String token, @Body Map<String, Object> data);

    // ---- DTOs ----

    class DailyRewardResponse {
        @SerializedName("rewardType")
        public String rewardType;
        @SerializedName("amount")
        public long amount;
        @SerializedName("streak")
        public int streak;
    }

    class MatchReportResponse {
        @SerializedName("xpEarned")
        public int xpEarned;
        @SerializedName("coinsEarned")
        public long coinsEarned;
        @SerializedName("rankChange")
        public int rankChange;
        @SerializedName("seasonProgress")
        public int seasonProgress;
    }

    class PurchaseRequest {
        @SerializedName("productId")
        public String productId;
        @SerializedName("purchaseToken")
        public String purchaseToken;
        @SerializedName("packageName")
        public String packageName;
        @SerializedName("subscription")
        public boolean subscription;
    }

    class PurchaseResult {
        @SerializedName("territoryId")
        public String territoryId;
        @SerializedName("granted")
        public String granted;
        @SerializedName("acknowledged")
        public boolean acknowledged;
    }

    class CloudSaveResponse {
        @SerializedName("state")
        public String state;
        @SerializedName("isPremium")
        public boolean isPremium;
    }

    class AuthResponse {
        @SerializedName("token")
        public String token;
        @SerializedName("userId")
        public String userId;
        @SerializedName("username")
        public String username;
        @SerializedName("isGuest")
        public boolean isGuest;
    }
}