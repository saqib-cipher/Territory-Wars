package com.territorywars.activities;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.annotation.Nullable;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.auth.GoogleAuthProvider;
import com.territorywars.R;
import com.territorywars.auth.AuthManager;
/**
* Guest / Google sign-in.
*
* <p>Google flow: Firebase Auth handles the ID token, then the backend
* exchanges it for our JWT. Guests get a device-anonymous account.</p>
*/
public class AuthActivity extends BaseActivity {
private static final int RC_GOOGLE_SIGN_IN = 9001;
private AuthManager authManager;
private GoogleSignInClient googleSignInClient;
private ProgressBar progress;
@Override
protected void onCreate(@Nullable Bundle savedInstanceState) {
super.onCreate(savedInstanceState);
setContentView(R.layout.activity_auth);
authManager = container().getAuthManager();
progress = findViewById(R.id.authProgress);
GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
.requestIdToken(getString(com.territorywars.R.string.default_web_client_id))
.requestEmail()
.build();
googleSignInClient = GoogleSignIn.getClient(this, gso);
com.territorywars.ModernFButton guestButton = findViewById(R.id.loginGuestButton);
com.territorywars.ModernFButton googleButton = findViewById(R.id.loginGoogleButton);
guestButton.setOnClickListener(v -> loginAsGuest());
googleButton.setOnClickListener(v -> googleSignInClient.signOut()
.addOnCompleteListener(task ->
startActivityForResult(googleSignInClient.getSignInIntent(), RC_GOOGLE_SIGN_IN)));
}
private void loginAsGuest() {
setLoading(true);
authManager.loginGuest(new AuthManager.AuthCallback() {
@Override
public void onSuccess(String token, String userId, String username, boolean guest) {
goMain();
}
@Override
public void onFailure(String message) {
setLoading(false);
Toast.makeText(AuthActivity.this, message, Toast.LENGTH_SHORT).show();
}
});
}
@Override
protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
super.onActivityResult(requestCode, resultCode, data);
if (requestCode == RC_GOOGLE_SIGN_IN) {
Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
try {
GoogleSignInAccount account = task.getResult(ApiException.class);
firebaseSignIn(account);
} catch (ApiException e) {
Toast.makeText(this, "Google sign-in cancelled", Toast.LENGTH_SHORT).show();
}
}
}
/** Exchanges the Google credential for a Firebase ID token, then logs in. */
private void firebaseSignIn(GoogleSignInAccount account) {
setLoading(true);
FirebaseAuth.getInstance()
.signInWithCredential(GoogleAuthProvider.getCredential(account.getIdToken(), null))
.addOnCompleteListener(task -> {
if (!task.isSuccessful()) {
setLoading(false);
Toast.makeText(this, "Firebase sign-in failed", Toast.LENGTH_SHORT).show();
return;
}
FirebaseUser user = task.getResult().getUser();
if (user == null) return;
user.getIdToken(false).addOnCompleteListener(tokenTask -> {
GetTokenResult result = tokenTask.getResult();
if (result == null) {
setLoading(false);
return;
}
exchangeToken(result.getToken(), user.getDisplayName());
});
});
}
private void exchangeToken(String firebaseIdToken, String displayName) {
authManager.loginWithGoogle(firebaseIdToken, displayName, new AuthManager.AuthCallback() {
@Override
public void onSuccess(String token, String userId, String username, boolean guest) {
goMain();
}
@Override
public void onFailure(String message) {
setLoading(false);
Toast.makeText(AuthActivity.this, message, Toast.LENGTH_SHORT).show();
}
});
}
private void goMain() {
Intent intent = new Intent(this, MainActivity.class);
intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
startActivity(intent);
}
private void setLoading(boolean loading) {
progress.setVisibility(loading ? View.VISIBLE : View.GONE);
findViewById(R.id.loginGuestButton).setEnabled(!loading);
findViewById(R.id.loginGoogleButton).setEnabled(!loading);
}
}
