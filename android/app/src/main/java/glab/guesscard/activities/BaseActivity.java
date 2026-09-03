package glab.guesscard.activities;

import android.app.Activity;
import android.os.Bundle;

import androidx.annotation.Nullable;
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
 * Base activity: edge-to-edge insets, DI container accessor, and global real-time event listener.
 * Automatically displays room invitations & friend requests ANYWHERE across the app
 * (unless the player is inside an active match) using both BACKEND Socket and Firebase RTDB.
 */
public abstract class BaseActivity extends AppCompatActivity {

    private ChildEventListener globalInviteListener;
    private ChildEventListener globalFriendRequestListener;
    private String activeListeningUid;

    private static final glab.guesscard.socket.GameSocketListener APP_GLOBAL_SOCKET_LISTENER = new glab.guesscard.socket.GameSocketListener() {
        @Override
        public void onFriendRequestReceived(String senderId, String senderName, String senderAvatar) {
            Activity current = GuessCardApp.getCurrentActivity();
            if (current instanceof BaseActivity && !current.isFinishing() && !current.isDestroyed()) {
                current.runOnUiThread(() -> {
                    ((BaseActivity) current).onGlobalFriendRequestReceived(senderId, senderName, senderAvatar);
                    glab.guesscard.notifications.GlobalInvitationManager.showFriendRequestDialog(
                            current, senderId, senderName, senderAvatar, null);
                });
            } else {
                glab.guesscard.notifications.GlobalInvitationManager.showFriendRequestDialog(
                        null, senderId, senderName, senderAvatar, null);
            }
        }

        @Override
        public void onRoomInviteReceived(String roomId, String code, String senderId, String senderName, String senderAvatar, String mode) {
            Activity current = GuessCardApp.getCurrentActivity();
            if (current instanceof BaseActivity && !current.isFinishing() && !current.isDestroyed()) {
                current.runOnUiThread(() -> {
                    ((BaseActivity) current).onGlobalRoomInviteReceived(roomId, code, senderId, senderName, senderAvatar, mode);
                    glab.guesscard.notifications.GlobalInvitationManager.showRoomInviteDialog(
                            current, roomId, code, senderId, senderName, senderAvatar, mode, null);
                });
            } else {
                glab.guesscard.notifications.GlobalInvitationManager.showRoomInviteDialog(
                        null, roomId, code, senderId, senderName, senderAvatar, mode, null);
            }
        }

        @Override
        public void onFriendRequestAccepted(String acceptorId, String acceptorName, String acceptorAvatar) {
            Activity current = GuessCardApp.getCurrentActivity();
            if (current instanceof BaseActivity && !current.isFinishing() && !current.isDestroyed()) {
                current.runOnUiThread(() -> {
                    ((BaseActivity) current).onGlobalFriendRequestAccepted(acceptorId, acceptorName, acceptorAvatar);
                });
            }
        }
    };

    /** Hook: subclasses can refresh their UI when a friend request arrives. */
    protected void onGlobalFriendRequestReceived(String senderId, String senderName, String senderAvatar) {}

    /** Hook: subclasses can refresh their UI when a room invite arrives. */
    protected void onGlobalRoomInviteReceived(String roomId, String code, String senderId, String senderName, String senderAvatar, String mode) {}

    /** Hook: someone accepted our friend request. */
    protected void onGlobalFriendRequestAccepted(String acceptorId, String acceptorName, String acceptorAvatar) {}

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
        String uid = firebaseUser != null ? firebaseUser.getUid() : (prefs() != null ? prefs().getUserId() : null);
        if (uid == null || uid.isEmpty() || "offline".equalsIgnoreCase(uid)) return;

        FirebaseManager fm = container().getFirebaseManager();
        if (fm == null) return;

        removeGlobalInviteListeners();
        activeListeningUid = uid;
        fm.startFriendshipSync(uid);

        if (container() != null && container().getSocketClient() != null) {
            container().getSocketClient().setGlobalEventListener(APP_GLOBAL_SOCKET_LISTENER);
            container().getSocketClient().connect();
        }

        globalInviteListener = fm.listenForRoomInvites(uid, (inviteId, rId, code, senderUid, sender, senderAvatar, mode) -> {
            glab.guesscard.notifications.GlobalInvitationManager.showRoomInviteDialog(
                    this, rId, code, senderUid, sender, senderAvatar, mode, null);
        });

        globalFriendRequestListener = fm.listenForFriendRequests(uid, (senderUid, senderName, senderAvatar) -> {
            glab.guesscard.notifications.GlobalInvitationManager.showFriendRequestDialog(
                    this, senderUid, senderName, senderAvatar, null);
        });
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
