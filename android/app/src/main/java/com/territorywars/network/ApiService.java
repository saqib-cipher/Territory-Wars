package com.territorywars.network;

import com.territorywars.models.MatchResult;
import com.territorywars.models.User;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface ApiService {
    class AuthResponse {
        public String token;
        public String userId;
        public String username;
        public boolean isGuest;
    }

    class DailyRewardResponse {
        public int amount;
        public String rewardType;
        public int rewardCoins;
        public int rewardGems;
        public int streak;
    }

    class MatchReportResponse {
        public int xpEarned;
        public int coinsEarned;
        public int newLevel;
    }

    @GET("v1/profile")
    Call<User> getProfile();

    @POST("v1/auth/guest")
    Call<AuthResponse> loginGuest();

    @POST("v1/auth/google")
    Call<AuthResponse> loginGoogle(@Body Map<String, String> body);

    @POST("v1/match/report")
    Call<Void> reportMatch(@Body MatchResult result);
}
