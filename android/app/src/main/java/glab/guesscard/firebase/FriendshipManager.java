package glab.guesscard.firebase;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ServerValue;

import glab.guesscard.GuessCardApp;
import glab.guesscard.di.GameContainer;
import glab.guesscard.socket.GameSocketClient;

/**
 * Centralized Manager for all Friendship operations:
 * - Sending friend requests (writes to Firebase RTDB + emits real-time socket event)
 * - Accepting friend requests (mutual friendship in RTDB + emits socket notification so both users see each other live)
 * - Declining friend requests
 * - Removing friends
 * - Checking friendship status
 */
public class FriendshipManager {

    public interface FriendshipActionCallback {
        void onSuccess();
        void onError(String message);
    }

    private static String resolveUid(Context context, @Nullable String uid) {
        if (uid != null && !uid.isEmpty() && !"offline".equalsIgnoreCase(uid)) {
            return uid;
        }
        FirebaseUser fUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fUser != null) {
            String resolved = fUser.getUid();
            if (context != null) {
                GameContainer container = GuessCardApp.from(context);
                if (container != null && container.getPreferences() != null) {
                    container.getPreferences().saveUserId(resolved);
                }
            }
            return resolved;
        }
        if (context != null) {
            GameContainer container = GuessCardApp.from(context);
            if (container != null && container.getPreferences() != null) {
                return container.getPreferences().getUserId();
            }
        }
        return null;
    }

    /**
     * Send a friend request to a target user.
     * Writes to /users/{targetUid}/friendRequests/{myUid}
     * Writes to /users/{myUid}/sentRequests/{targetUid}
     * Emits 'sendFriendRequest' over socket for instant real-time delivery.
     */
    public static void sendFriendRequest(Context context,
                                         String rawMyUid,
                                         String targetUid,
                                         @Nullable String targetName,
                                         @Nullable FriendshipActionCallback callback) {
        final String myUid = resolveUid(context, rawMyUid);
        if (myUid == null || targetUid == null || myUid.equals(targetUid)) {
            if (callback != null) callback.onError("Invalid user IDs");
            return;
        }

        GameContainer container = GuessCardApp.from(context);
        FirebaseManager fm = container.getFirebaseManager();
        GameSocketClient sc = container.getSocketClient();

        // Check if already friends or self or if target already requested me
        fm.checkFriendshipStatus(myUid, targetUid, status -> {
            if (status == FirebaseManager.FriendshipStatus.SELF) {
                if (context != null) Toast.makeText(context, "Cannot add yourself as a friend", Toast.LENGTH_SHORT).show();
                if (callback != null) callback.onError("Cannot add self");
                return;
            }
            if (status == FirebaseManager.FriendshipStatus.FRIENDS) {
                if (context != null) Toast.makeText(context, "Already friends!", Toast.LENGTH_SHORT).show();
                if (callback != null) callback.onSuccess();
                return;
            }
            if (status == FirebaseManager.FriendshipStatus.REQUEST_RECEIVED) {
                // If target already sent a request to me, simply accept it!
                acceptFriendRequest(context, myUid, targetUid, targetName, callback);
                return;
            }

            // Write to Firebase RTDB
            fm.getDatabaseRef().child("users").child(targetUid).child("friendRequests").child(myUid)
                    .setValue(ServerValue.TIMESTAMP);
            fm.getDatabaseRef().child("users").child(myUid).child("sentRequests").child(targetUid)
                    .setValue(ServerValue.TIMESTAMP);

            // Real-time backend socket event
            if (sc != null) {
                sc.connect();
                sc.sendFriendRequest(targetUid);
            }

            String name = targetName != null && !targetName.isEmpty() ? targetName : "Player";
            if (context != null) {
                Toast.makeText(context, "Friend request sent to " + name + " ✉️", Toast.LENGTH_SHORT).show();
            }

            if (callback != null) callback.onSuccess();
        });
    }

    /**
     * Accept a pending friend request from a sender.
     * Establishes mutual friendship under /users/{myUid}/friends/{senderUid} and /users/{senderUid}/friends/{myUid}.
     * Cleans up friendRequests and sentRequests nodes.
     * Emits 'friendRequestAccepted' so the original sender immediately updates their UI to show friendship.
     */
    public static void acceptFriendRequest(Context context,
                                           String rawMyUid,
                                           String senderUid,
                                           @Nullable String senderName,
                                           @Nullable FriendshipActionCallback callback) {
        final String myUid = resolveUid(context, rawMyUid);
        if (myUid == null || senderUid == null) {
            if (callback != null) callback.onError("Invalid user IDs");
            return;
        }

        GameContainer container = GuessCardApp.from(context);
        FirebaseManager fm = container.getFirebaseManager();
        GameSocketClient sc = container.getSocketClient();

        // Mutual friendship in RTDB
        fm.addFriend(myUid, senderUid);

        // Notify the original sender via socket
        if (sc != null) {
            sc.connect();
            sc.notifyFriendRequestAccepted(senderUid);
        }

        String name = senderName != null && !senderName.isEmpty() ? senderName : "Player";
        if (context != null) {
            Toast.makeText(context, "Accepted friend request from " + name + " 🤝", Toast.LENGTH_SHORT).show();
        }

        if (callback != null) callback.onSuccess();
    }

    /**
     * Decline a pending friend request.
     */
    public static void declineFriendRequest(Context context,
                                            String rawMyUid,
                                            String senderUid,
                                            @Nullable FriendshipActionCallback callback) {
        final String myUid = resolveUid(context, rawMyUid);
        if (myUid == null || senderUid == null) {
            if (callback != null) callback.onError("Invalid user IDs");
            return;
        }

        GameContainer container = GuessCardApp.from(context);
        FirebaseManager fm = container.getFirebaseManager();

        fm.declineFriendRequest(myUid, senderUid);

        if (context != null) {
            Toast.makeText(context, "Declined friend request", Toast.LENGTH_SHORT).show();
        }

        if (callback != null) callback.onSuccess();
    }

    /**
     * Remove an existing friend.
     */
    public static void removeFriend(Context context,
                                    String rawMyUid,
                                    String friendUid,
                                    @Nullable String friendName,
                                    @Nullable FriendshipActionCallback callback) {
        final String myUid = resolveUid(context, rawMyUid);
        if (myUid == null || friendUid == null) {
            if (callback != null) callback.onError("Invalid user IDs");
            return;
        }

        GameContainer container = GuessCardApp.from(context);
        FirebaseManager fm = container.getFirebaseManager();

        fm.removeFriend(myUid, friendUid);

        String name = friendName != null && !friendName.isEmpty() ? friendName : "Friend";
        if (context != null) {
            Toast.makeText(context, "Removed friend: " + name, Toast.LENGTH_SHORT).show();
        }

        if (callback != null) callback.onSuccess();
    }
}
