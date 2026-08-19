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
    private ChildEventListener globalFriendRequestListener;
    private String activeListeningUid;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupGlobalInviteListeners();
    }

    @Override
    protected void onPause() {
        super.onPause();
        removeGlobalInviteListeners();
    }

    private void setupGlobalInviteListeners() {
        // Do NOT show room invitations if the user is currently playing a live match
        if (this instanceof GameActivity) return;

        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        String uid = firebaseUser != null ? firebaseUser.getUid() : prefs().getUserId();
        if (uid == null || uid.isEmpty() || "offline".equalsIgnoreCase(uid)) return;

        FirebaseManager fm = container().getFirebaseManager();
        if (fm == null) return;

        if (container() != null && container().getSocketClient() != null) {
            container().getSocketClient().connect();
        }

        removeGlobalInviteListeners();
        activeListeningUid = uid;

        globalInviteListener = fm.listenForRoomInvites(uid, (inviteId, rId, code, senderUid, sender, senderAvatar, mode) -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            glab.guesscard.notifications.GlobalInvitationManager.showRoomInviteDialog(
                    this, rId, code, senderUid, sender, senderAvatar, mode, null);
        }));

        globalFriendRequestListener = fm.listenForFriendRequests(uid, (senderUid, senderName, senderAvatar) -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            glab.guesscard.notifications.GlobalInvitationManager.showFriendRequestDialog(
                    this, senderUid, senderName, senderAvatar, null);
        }));
    }

    private void removeGlobalInviteListeners() {
        if (activeListeningUid != null && container() != null && container().getFirebaseManager() != null) {
            if (globalInviteListener != null) {
                container().getFirebaseManager().removeRoomInviteListener(activeListeningUid, globalInviteListener);
            }
            if (globalFriendRequestListener != null) {
                container().getFirebaseManager().removeFriendRequestListener(activeListeningUid, globalFriendRequestListener);
            }
            globalInviteListener = null;
            globalFriendRequestListener = null;
            activeListeningUid = null;
        }
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        applyEdgeToEdgeInsets();
    }

    /**
     * Override in subclasses to apply window inset padding where needed.
     * The default does nothing — transparent statusbar is handled via WindowCompat.
     */
    protected void applyEdgeToEdgeInsets() {
        // no-op — subclasses can override to apply safe-area insets to specific views
    }

    protected GameContainer container() {
        return GuessCardApp.from(this);
    }

    protected PreferenceManager prefs() {
        return container().getPreferences();
    }
}
