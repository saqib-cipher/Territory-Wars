package glab.guesscard.network;

import android.util.Log;

import glab.guesscard.BuildConfig;

import java.util.HashMap;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static final String TAG = "ApiClient";
    private final ApiService apiService;

    public ApiClient(PreferenceManager preferenceManager) {
        OkHttpClient client = new OkHttpClient.Builder().build();
        String baseUrl = "https://territory-wars-9o4f.onrender.com/v1/";
        if (baseUrl != null && !baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        this.apiService = retrofit.create(ApiService.class);
    }

    public ApiService getApi() {
        return apiService;
    }

    public static ApiError parseError(Response response) {
        return new ApiError(response.code(), response.message());
    }

    public static ApiError parseError(retrofit2.Response<?> response) {
        return new ApiError(response.code(), response.message());
    }

    /** Callback interface for profile updates. */
    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    /** Update profile (username + avatar) on the backend server. */
    public static void updateProfile(String username, String avatarFileName, Callback<Void> callback, Callback<String> errorCallback) {
        // The backend update happens via the REST endpoint. We use a simple OkHttp call.
        // For now, profile sync is handled through Firebase RTDB which is the primary store.
        // The server API sync will be added when auth token flow is complete.
        Log.d(TAG, "Profile sync requested: " + username + " / " + avatarFileName);
        if (callback != null) callback.onSuccess(null);
    }
}
