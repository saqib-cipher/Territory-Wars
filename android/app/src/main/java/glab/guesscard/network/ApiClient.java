package glab.guesscard.network;

import glab.guesscard.BuildConfig;

import okhttp3.OkHttpClient;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
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
}
