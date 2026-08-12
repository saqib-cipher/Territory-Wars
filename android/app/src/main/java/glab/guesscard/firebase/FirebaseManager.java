package glab.guesscard.firebase;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central Firebase manager for Guess Card.
 * Handles Authentication (Google + Anonymous) and Realtime Database operations.
 */
public class FirebaseManager {

    private static final String TAG = "FirebaseManager";

    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onFailure(String message);
    }

    public interface DataCallback<T> {
        void onResult(T data);
    }

    private final FirebaseAuth auth;
    private final DatabaseReference database;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public FirebaseManager() {
        this.auth = FirebaseAuth.getInstance();
        this.database = FirebaseDatabase.getInstance().getReference();
    }

    // ── AUTH ────────────────────────────────────────────────────────────────

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public boolean isSignedIn() {
        return auth.getCurrentUser() != null;
    }

    public boolean isAnonymous() {
        FirebaseUser u = auth.getCurrentUser();
        return u != null && u.isAnonymous();
    }

    /** Sign in anonymously (offline/guest play). */
    public void signInAnonymous(AuthCallback callback) {
        auth.signInAnonymously()
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user != null) {
                        saveUserToDatabase(user, "Guest_" + user.getUid().substring(0, 5));
                    }
                    if (callback != null) callback.onSuccess(user);
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e.getMessage());
                });
    }

    /** Sign in with Google credential (Firebase token obtained from GoogleSignIn flow). */
    public void signInWithGoogle(String idToken, AuthCallback callback) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user != null) {
                        saveUserToDatabase(user, user.getDisplayName());
                    }
                    if (callback != null) callback.onSuccess(user);
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e.getMessage());
                });
    }

    public void signOut() {
        auth.signOut();
    }

    // ── DATABASE ────────────────────────────────────────────────────────────

    /** Save or update user profile in /users/{uid}/ */
    public void saveUserToDatabase(FirebaseUser user, String displayName) {
        if (user == null) return;
        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", user.getUid());
        userData.put("displayName", displayName != null ? displayName : "Player");
        userData.put("email", user.getEmail() != null ? user.getEmail() : "");
        userData.put("photoUrl", user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "");
        userData.put("isAnonymous", user.isAnonymous());
        userData.put("lastSeen", ServerValue.TIMESTAMP);

        database.child("users").child(user.getUid()).updateChildren(userData)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to save user: " + e.getMessage()));
    }

    public interface ErrorCallback {
        void onError(String message);
    }

    /** Get a user's profile from the database. */
    public void getUserProfile(String uid, DataCallback<Map<String, Object>> callback) {
        getUserProfile(uid, callback, null);
    }

    public void getUserProfile(String uid, DataCallback<Map<String, Object>> callback, ErrorCallback errorCallback) {
        database.child("users").child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (callback != null) {
                    //noinspection unchecked
                    callback.onResult((Map<String, Object>) snapshot.getValue());
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                if (errorCallback != null) errorCallback.onError(error.getMessage());
            }
        });
    }

    /** Update the user's display name. */
    public void updateDisplayName(String uid, String name) {
        database.child("users").child(uid).child("displayName").setValue(name);
    }

    /** Save match score to /leaderboard/ */
    public void submitScore(String uid, String displayName, int score, String mode) {
        DatabaseReference ref = database.child("leaderboard").child(uid);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Long existing = snapshot.child("score").getValue(Long.class);
                if (existing == null || score > existing) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("uid", uid);
                    entry.put("displayName", displayName);
                    entry.put("score", score);
                    entry.put("mode", mode);
                    entry.put("timestamp", ServerValue.TIMESTAMP);
                    ref.setValue(entry);
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Score submit error: " + error.getMessage());
            }
        });
    }

    /** Get top N leaderboard entries. Returns reference for real-time listening. */
    public com.google.firebase.database.Query getLeaderboardRef(int limit) {
        return database.child("leaderboard").limitToLast(limit);
    }

    /** Create/update a game room in /rooms/{roomId}/ */
    public DatabaseReference getRoomRef(String roomId) {
        return database.child("rooms").child(roomId);
    }

    /** Save match result to /users/{uid}/gameHistory/ */
    public void saveGameHistory(String uid, String mode, int score, boolean won, String cardGuessed) {
        if (uid == null) return;
        DatabaseReference ref = database.child("users").child(uid).child("gameHistory").push();
        Map<String, Object> item = new HashMap<>();
        item.put("mode", mode);
        item.put("score", score);
        item.put("won", won);
        item.put("cardGuessed", cardGuessed != null ? cardGuessed : "");
        item.put("timestamp", ServerValue.TIMESTAMP);
        ref.setValue(item);
    }

    /** Retrieve past game history for user. */
    public void getGameHistory(String uid, DataCallback<List<Map<String, Object>>> callback) {
        if (uid == null) {
            if (callback != null) callback.onResult(new ArrayList<>());
            return;
        }
        database.child("users").child(uid).child("gameHistory")
                .orderByChild("timestamp")
                .limitToLast(20)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        List<Map<String, Object>> history = new ArrayList<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            //noinspection unchecked
                            Map<String, Object> val = (Map<String, Object>) child.getValue();
                            if (val != null) history.add(0, val);
                        }
                        if (callback != null) callback.onResult(history);
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        if (callback != null) callback.onResult(new ArrayList<>());
                    }
                });
    }

    /** Add a friend under /users/{uid}/friends/{friendUid} */
    public void addFriend(String uid, String friendUid) {
        if (uid == null || friendUid == null) return;
        database.child("users").child(uid).child("friends").child(friendUid).setValue(true);
        database.child("users").child(friendUid).child("friends").child(uid).setValue(true);
    }

    /** Remove a friend */
    public void removeFriend(String uid, String friendUid) {
        if (uid == null || friendUid == null) return;
        database.child("users").child(uid).child("friends").child(friendUid).removeValue();
        database.child("users").child(friendUid).child("friends").child(uid).removeValue();
    }

    /** Get user's friend list with profiles */
    public void getFriends(String uid, DataCallback<List<Map<String, Object>>> callback) {
        if (uid == null) {
            if (callback != null) callback.onResult(new ArrayList<>());
            return;
        }
        database.child("users").child(uid).child("friends").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<String> friendUids = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    friendUids.add(child.getKey());
                }
                if (friendUids.isEmpty()) {
                    if (callback != null) callback.onResult(new ArrayList<>());
                    return;
                }
                List<Map<String, Object>> friendsProfiles = new ArrayList<>();
                final int[] loaded = {0};
                for (String fUid : friendUids) {
                    getUserProfile(fUid, profile -> {
                        if (profile != null) friendsProfiles.add(profile);
                        loaded[0]++;
                        if (loaded[0] >= friendUids.size() && callback != null) {
                            callback.onResult(friendsProfiles);
                        }
                    });
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                if (callback != null) callback.onResult(new ArrayList<>());
            }
        });
    }

    public interface InviteCallback {
        void onInviteReceived(String roomId, String roomCode, String senderName);
    }

    /** Send room invitation to a friend */
    public void sendRoomInvite(String friendUid, String roomId, String roomCode, String senderName) {
        if (friendUid == null || roomId == null) return;
        DatabaseReference ref = database.child("users").child(friendUid).child("invites").push();
        Map<String, Object> invite = new HashMap<>();
        invite.put("roomId", roomId);
        invite.put("roomCode", roomCode != null ? roomCode : "");
        invite.put("senderName", senderName != null ? senderName : "A Friend");
        invite.put("timestamp", ServerValue.TIMESTAMP);
        ref.setValue(invite);
    }

    /** Listen for real-time room invitations for current user */
    public ValueEventListener listenForRoomInvites(String uid, InviteCallback callback) {
        if (uid == null || callback == null) return null;
        DatabaseReference ref = database.child("users").child(uid).child("invites");
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    //noinspection unchecked
                    Map<String, Object> val = (Map<String, Object>) child.getValue();
                    if (val != null) {
                        String rId = (String) val.get("roomId");
                        String code = (String) val.get("roomCode");
                        String sender = (String) val.get("senderName");
                        callback.onInviteReceived(rId, code, sender);
                        child.getRef().removeValue();
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        };
        ref.addValueEventListener(listener);
        return listener;
    }

    /** Search users by UID or Username */
    public void searchUsers(String query, DataCallback<List<Map<String, Object>>> callback) {
        if (query == null || query.trim().isEmpty()) {
            if (callback != null) callback.onResult(new ArrayList<>());
            return;
        }
        String q = query.trim();
        database.child("users").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<Map<String, Object>> results = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    //noinspection unchecked
                    Map<String, Object> u = (Map<String, Object>) child.getValue();
                    if (u != null) {
                        String uid = (String) u.get("uid");
                        String name = (String) u.get("displayName");
                        if ((uid != null && uid.equalsIgnoreCase(q)) ||
                            (name != null && name.toLowerCase().contains(q.toLowerCase()))) {
                            results.add(u);
                        }
                    }
                }
                if (callback != null) callback.onResult(results);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                if (callback != null) callback.onResult(new ArrayList<>());
            }
        });
    }

    /** Set player presence in a room. */
    public void joinRoom(String roomId, String uid, String displayName) {
        Map<String, Object> playerData = new HashMap<>();
        playerData.put("uid", uid);
        playerData.put("displayName", displayName);
        playerData.put("joinedAt", ServerValue.TIMESTAMP);
        playerData.put("ready", false);
        database.child("rooms").child(roomId).child("players").child(uid).setValue(playerData);
    }

    /** Remove player from room. */
    public void leaveRoom(String roomId, String uid) {
        database.child("rooms").child(roomId).child("players").child(uid).removeValue();
    }

    /** Post a Q&A event to the room's history. */
    public void postQuestionAnswer(String roomId, String askerUid, String askerName,
                                   String question, String answererUid, String answererName, String answer) {
        Map<String, Object> qa = new HashMap<>();
        qa.put("askerUid", askerUid);
        qa.put("askerName", askerName);
        qa.put("question", question);
        qa.put("answererUid", answererUid);
        qa.put("answererName", answererName);
        qa.put("answer", answer);
        qa.put("timestamp", ServerValue.TIMESTAMP);
        database.child("rooms").child(roomId).child("qaHistory").push().setValue(qa);
    }

    public DatabaseReference getDatabaseRef() {
        return database;
    }
}
