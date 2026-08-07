package com.territorywars.repository;

import com.territorywars.network.ApiClient;
import com.territorywars.network.ApiService;
import com.territorywars.network.PreferenceManager;

import java.util.Map;

public class Repository {
    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Throwable t);
    }

    private final ApiClient apiClient;
    private final PreferenceManager preferenceManager;

    public Repository(ApiClient apiClient, PreferenceManager preferenceManager) {
        this.apiClient = apiClient;
        this.preferenceManager = preferenceManager;
    }

    public void equipItem(String id, Callback<Void> callback) {
        if (callback != null) callback.onSuccess(null);
    }

    public void reportMatch(Map<String, Object> body, Callback<ApiService.MatchReportResponse> callback) {
        if (callback != null) callback.onSuccess(new ApiService.MatchReportResponse());
    }
}
