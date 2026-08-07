package com.territorywars.network;

import com.territorywars.BuildConfig;

import okhttp3.OkHttpClient;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private final ApiService apiService;

    public ApiClient(PreferenceManager preferenceManager) {
        OkHttpClient client = new OkHttpClient.Builder().build();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
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
}
