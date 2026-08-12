package glab.guesscard.activities;

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

import glab.guesscard.ModernFButton;
import glab.guesscard.R;
import glab.guesscard.firebase.FirebaseManager;

/**
 * Auth screen: Google sign-in, Anonymous (Guest), or Offline mode.
 * Offline mode signs in anonymously with Firebase so the app still works
 * but online features remain disabled until a real account is used.
 */
public class AuthActivity extends BaseActivity {

    private static final int RC_GOOGLE_SIGN_IN = 9001;

    private FirebaseManager firebaseManager;
    private GoogleSignInClient googleSignInClient;
    private ProgressBar progress;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        firebaseManager = container().getFirebaseManager();
        progress = findViewById(R.id.authProgress);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        ModernFButton googleButton = findViewById(R.id.loginGoogleButton);
        ModernFButton guestButton = findViewById(R.id.loginGuestButton);
        ModernFButton offlineButton = findViewById(R.id.playOfflineButton);

        if (googleButton != null) {
            googleButton.setOnClickListener(v ->
                googleSignInClient.signOut().addOnCompleteListener(task ->
                    startActivityForResult(googleSignInClient.getSignInIntent(), RC_GOOGLE_SIGN_IN)));
        }

        if (guestButton != null) {
            guestButton.setOnClickListener(v -> loginAnonymous());
        }

        if (offlineButton != null) {
            offlineButton.setOnClickListener(v -> {
                // Offline mode: anonymous sign-in but flagged as offline-only
                prefs().saveSession("offline", "offline_user", "Offline Player", true);
                goMain();
            });
        }
    }

    private void loginAnonymous() {
        setLoading(true);
        firebaseManager.signInAnonymous(new FirebaseManager.AuthCallback() {
            @Override
            public void onSuccess(com.google.firebase.auth.FirebaseUser user) {
                if (user != null) {
                    prefs().saveSession(user.getUid(), user.getUid(),
                            "Guest_" + user.getUid().substring(0, 5), true);
                }
                goMain();
            }

            @Override
            public void onFailure(String message) {
                setLoading(false);
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(AuthActivity.this)
                        .setTitle("Sign-in Failed")
                        .setMessage("Could not connect: " + message + "\n\nPlay offline instead?")
                        .setPositiveButton("Play Offline", (d, w) -> {
                            prefs().saveSession("offline", "offline_user", "Offline Player", true);
                            goMain();
                        })
                        .setNegativeButton("Retry", (d, w) -> loginAnonymous())
                        .show();
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
                setLoading(true);
                firebaseManager.signInWithGoogle(account.getIdToken(), new FirebaseManager.AuthCallback() {
                    @Override
                    public void onSuccess(com.google.firebase.auth.FirebaseUser user) {
                        if (user != null) {
                            prefs().saveSession(user.getUid(), user.getUid(),
                                    user.getDisplayName() != null ? user.getDisplayName() : "Player", false);
                        }
                        goMain();
                    }

                    @Override
                    public void onFailure(String message) {
                        setLoading(false);
                        Toast.makeText(AuthActivity.this, "Google sign-in failed: " + message, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (ApiException e) {
                Toast.makeText(this, "Google sign-in cancelled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void goMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void setLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        View google = findViewById(R.id.loginGoogleButton);
        View guest = findViewById(R.id.loginGuestButton);
        View offline = findViewById(R.id.playOfflineButton);
        if (google != null) google.setEnabled(!loading);
        if (guest != null) guest.setEnabled(!loading);
        if (offline != null) offline.setEnabled(!loading);
    }
}
