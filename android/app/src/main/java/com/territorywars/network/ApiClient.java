package com.territorywars.network;

import androidx.annotation.Nullable;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Retrofit factory. Builds the {@link ApiService} and offers helpers to unwrap
 * API errors into {@link ApiError}.
 */
public class ApiClient {

    private final ApiService apiService;
    private final OkHttpClient httpClient;

    public ApiClient() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

        httpClient = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(ApiService.class);
    }

    public ApiService getApi() {
        return apiService;
    }

    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * Converts a failed Retrofit response into a user-presentable {@link ApiError}.
     */
    @Nullable
    public static ApiError parseError(Response<?> response) {
        if (response.errorBody() == null) return null;
        try {
            return new com.google.gson.Gson()
                    .fromJson(response.errorBody().string(), ApiError.class);
        } catch (IOException e) {
            return null;
        }
    }
}