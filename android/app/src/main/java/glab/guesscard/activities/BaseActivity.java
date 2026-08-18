package glab.guesscard.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;

import glab.guesscard.GuessCardApp;
import glab.guesscard.di.GameContainer;
import glab.guesscard.firebase.FirebaseManager;
import glab.guesscard.network.PreferenceManager;

/**
 * Base activity: edge-to-edge insets, DI container accessor, and global room invite listener.
 * Automatically displays room invitations anywhere across the app unless the player is inside an active match (GameActivity)
 * or is offline/not logged in.
 */
public abstract class BaseActivity extends AppCompatActivity {

    private ChildEventListener globalInviteListener;
    private String activeListeningUid;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupGlobalInviteListener();
    }

    @Override
    protected void onPause() {
        super.onPause();
        removeGlobalInviteListener();
    }

    private void setupGlobalInviteListener() {
        // Do NOT show room invitations if the user is currently playing a live match
        if (this instanceof GameActivity) return;

        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        String uid = firebaseUser != null ? firebaseUser.getUid() : prefs().getUserId();
        if (uid == null || uid.isEmpty() || "offline".equalsIgnoreCase(uid)) return;

        FirebaseManager fm = container().getFirebaseManager();
        if (fm == null) return;

        removeGlobalInviteListener();
        activeListeningUid = uid;

        globalInviteListener = fm.listenForRoomInvites(uid, (rId, code, sender) -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;

            new AlertDialog.Builder(this)
                    .setTitle("Room Invitation ✉️")
                    .setMessage((sender != null && !sender.isEmpty() ? sender : "A friend")
                            + " invited you to join their game room (" + (code != null ? code : "") + ")!")
                    .setPositiveButton("Join", (dialog, which) -> {
                        dialog.dismiss();
                        // If already in a lobby, leave the current lobby first
                        if (BaseActivity.this instanceof LobbyActivity) {
                            ((LobbyActivity) BaseActivity.this).removePresence();
                            finish();
                        }

                        Intent intent = new Intent(BaseActivity.this, LobbyActivity.class);
                        if (rId != null && !rId.isEmpty()) intent.putExtra(LobbyActivity.EXTRA_ROOM_ID, rId);
                        if (code != null && !code.isEmpty()) intent.putExtra(LobbyActivity.EXTRA_ROOM_CODE, code);
                        startActivity(intent);
                    })
                    .setNegativeButton("Ignore", (dialog, which) -> dialog.dismiss())
                    .setCancelable(true)
                    .show();
        }));
    }

    private void removeGlobalInviteListener() {
        if (globalInviteListener != null && activeListeningUid != null) {
            if (container() != null && container().getFirebaseManager() != null) {
                container().getFirebaseManager().removeRoomInviteListener(activeListeningUid, globalInviteListener);
            }
            globalInviteListener = null;
            activeListeningUid = null;
        }
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        applyEdgeToEdgeInsets();
    }

    protected void applyEdgeToEdgeInsets() {
        View root = findViewById(android.R.id.content);
        if (root != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
                androidx.core.graphics.Insets insets = windowInsets.getInsets(
                        androidx.core.view.WindowInsetsCompat.Type.systemBars());
                v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                return windowInsets;
            });
        }
    }

    protected GameContainer container() {
        return GuessCardApp.from(this);
    }

    protected PreferenceManager prefs() {
        return container().getPreferences();
    }
}
