package com.territorywars.auth;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import com.territorywars.network.ApiClient;
import com.territorywars.network.ApiError;
import com.territorywars.network.ApiService;
import com.territorywars.network.PreferenceManager;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Response;
/**
* Handles Guest and Google sign-in and persists the JWT issued by the backend.
*/
public class AuthManager {
public interface AuthCallback {
void onSuccess(String token, String userId, String username, boolean guest);
void onFailure(String message);
}
private final ApiClient apiClient;
private final PreferenceManager preferences;
private final ExecutorService executor = Executors.newSingleThreadExecutor();
private final Handler mainHandler = new Handler(Looper.getMainLooper());
public AuthManager(ApiClient apiClient, PreferenceManager preferences) {
this.apiClient = apiClient;
this.preferences = preferences;
}
public void loginGuest(@NonNull AuthCallback callback) {
executor.execute(() -> {
try {
Response<ApiService.AuthResponse> response =
apiClient.getApi().loginGuest().execute();
if (response.isSuccessful() && response.body() != null) {
handle(response.body());
mainHandler.post(() -> callback.onSuccess(
response.body().token,
response.body().userId,
response.body().username,
response.body().isGuest));
} else {
fail(callback, ApiClient.parseError(response));
}
} catch (Exception e) {
fail(callback, e.getMessage() != null ? e.getMessage() : "Network error");
}
});
}
/**
* Called from AuthActivity after Firebase Google sign-in yields an ID token.
*/
public void loginWithGoogle(@NonNull String idToken, @NonNull String displayName,
@NonNull AuthCallback callback) {
executor.execute(() -> {
try {
Map<String, String> body = new HashMap<>();
body.put("idToken", idToken);
body.put("username", displayName);
Response<ApiService.AuthResponse> response =
apiClient.getApi().loginGoogle(body).execute();
if (response.isSuccessful() && response.body() != null) {
handle(response.body());
mainHandler.post(() -> callback.onSuccess(
response.body().token,
response.body().userId,
response.body().username,
response.body().isGuest));
} else {
fail(callback, ApiClient.parseError(response));
}
} catch (Exception e) {
fail(callback, e.getMessage() != null ? e.getMessage() : "Network error");
}
});
}
private void handle(ApiService.AuthResponse response) {
preferences.saveSession(response.token, response.userId, response.username, response.isGuest);
}
private void fail(AuthCallback callback, ApiError error) {
String message = error != null ? error.message : "Network error";
mainHandler.post(() -> callback.onFailure(message != null ? message : "Network error"));
}
private void fail(AuthCallback callback, String message) {
mainHandler.post(() -> callback.onFailure(message));
}
}
