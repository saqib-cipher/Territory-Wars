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
import androidx.annotation.NonNull;
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

    private static final int PROFILE_CACHE_SIZE = 200;

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
    private final glab.guesscard.network.PreferenceManager preferenceManager;

    /** In-memory cache of player profiles (uid -> profile map) to avoid repeated RTDB reads. */
    private final android.util.LruCache<String, Map<String, Object>> profileCache =
            new android.util.LruCache<>(PROFILE_CACHE_SIZE);

    public FirebaseManager(glab.guesscard.network.PreferenceManager preferenceManager) {
        this.auth = FirebaseAuth.getInstance();
        this.database = FirebaseDatabase.getInstance().getReference();
        this.preferenceManager = preferenceManager;
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
        cacheUserProfile(user.getUid(), userData);
    }

    public interface ErrorCallback {
        void onError(String message);
    }

    /** Get a user's profile from the database. */
    public void getUserProfile(String uid, DataCallback<Map<String, Object>> callback) {
        getUserProfile(uid, callback, null);
    }

    public void getUserProfile(String uid, DataCallback<Map<String, Object>> callback, ErrorCallback errorCallback) {
        if (uid == null) {
            if (callback != null) callback.onResult(null);
            return;
        }
        Map<String, Object> cached = profileCache.get(uid);
        if (cached == null && preferenceManager != null) {
            String diskJson = preferenceManager.getCachedProfile(uid);
            if (diskJson != null && !diskJson.isEmpty()) {
                try {
                    Map<String, Object> diskProfile = jsonToMap(new org.json.JSONObject(diskJson));
                    if (diskProfile != null) {
                        profileCache.put(uid, diskProfile);
                        cached = diskProfile;
                    }
                } catch (Exception ignored) {}
            }
        }
        if (cached != null) {
            if (callback != null) callback.onResult(cached);
            return;
        }
        database.child("users").child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                //noinspection unchecked
                Map<String, Object> profile = (Map<String, Object>) snapshot.getValue();
                if (profile != null) cacheUserProfile(uid, profile);
                if (callback != null) callback.onResult(profile);
            }
            @Override
            public void onCancelled(DatabaseError error) {
                if (errorCallback != null) errorCallback.onError(error.getMessage());
            }
        });
    }

    /** Store a player profile into the local cache so later lookups skip the network. */
    public void cacheUserProfile(String uid, Map<String, Object> profile) {
        if (uid == null || profile == null) return;
        profileCache.put(uid, profile);
        if (preferenceManager != null) {
            preferenceManager.cacheProfile(uid, new org.json.JSONObject(profile).toString());
        }
    }

    /** Invalidate a cached profile (e.g. after an edit). */
    public void invalidateUserProfile(String uid) {
        if (uid == null) return;
        profileCache.remove(uid);
        if (preferenceManager != null) preferenceManager.clearCachedProfile(uid);
    }

    /** Refresh the cached profile with fresh values (e.g. right after a profile edit). */
    public void refreshCachedProfile(String uid, String displayName, String avatarFileName) {
        if (uid == null) return;
        Map<String, Object> profile = profileCache.get(uid);
        if (profile == null) {
            profile = new HashMap<>();
            profile.put("uid", uid);
        }
        if (displayName != null) profile.put("displayName", displayName);
        if (avatarFileName != null) profile.put("avatarFileName", avatarFileName);
        profileCache.put(uid, profile);
        if (preferenceManager != null) {
            preferenceManager.cacheProfile(uid, new org.json.JSONObject(profile).toString());
        }
    }

    /** Convert a flat JSONObject into a String->Object map (values stay JSON types). */
    private Map<String, Object> jsonToMap(org.json.JSONObject json) {
        if (json == null) return null;
        try {
            Map<String, Object> map = new HashMap<>();
            java.util.Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                map.put(key, json.get(key));
            }
            return map;
        } catch (org.json.JSONException e) {
            return null;
        }
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

    /** Check whether two users are friends. */
    public void isFriend(String uid, String targetUid, DataCallback<Boolean> callback) {
        if (uid == null || targetUid == null) {
            if (callback != null) callback.onResult(false);
            return;
        }
        database.child("users").child(uid).child("friends").child(targetUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (callback != null) callback.onResult(snapshot.exists());
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (callback != null) callback.onResult(false);
                    }
                });
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

    /** Search users by UID or Username (excludes the current user). */
    public void searchUsers(String query, DataCallback<List<Map<String, Object>>> callback) {
        searchUsers(null, query, callback);
    }

    /** Search users by UID or Username. Pass currentUid to exclude yourself from results. */
    public void searchUsers(String currentUid, String query, DataCallback<List<Map<String, Object>>> callback) {
        if (query == null || query.trim().isEmpty()) {
            if (callback != null) callback.onResult(new ArrayList<>());
            return;
        }
        String q = query.trim().toLowerCase();
        long onlineCutoff = System.currentTimeMillis() - 5 * 60 * 1000L;
        database.child("users").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<Map<String, Object>> results = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String uid = child.getKey();
                    if (currentUid != null && currentUid.equals(uid)) continue;
                    String name = child.child("displayName").getValue(String.class);
                    String avatar = child.child("avatarFileName").getValue(String.class);
                    Long lastSeen = child.child("lastSeen").getValue(Long.class);

                    if ((uid != null && uid.toLowerCase().contains(q)) ||
                        (name != null && name.toLowerCase().contains(q))) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("uid", uid);
                        map.put("displayName", name != null ? name : "Player");
                        map.put("avatarFileName", avatar != null ? avatar : "avatar_01.png");
                        map.put("status", lastSeen != null && lastSeen >= onlineCutoff ? "Online" : "Offline");
                        cacheUserProfile(uid, map);
                        results.add(map);
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

    public interface QuickMatchCallback {
        void onRoomFound(String roomId, String roomCode, String mode);
    }

    /** Find an open room matching a specific mode (< 5 players). Returns roomId = host UID root node. */
    public void findOpenRoomByMode(String modeStr, QuickMatchCallback callback) {
        database.child("rooms").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    String rMode = child.child("mode").getValue(String.class);
                    String status = child.child("status").getValue(String.class);
                    long pCount = child.child("players").getChildrenCount();
                    if ("FINISHED".equalsIgnoreCase(status) || "PLAYING".equalsIgnoreCase(status) || "IN_PROGRESS".equalsIgnoreCase(status)) {
                        continue;
                    }
                    if (pCount > 0 && pCount < 5 && (modeStr == null || modeStr.equalsIgnoreCase(rMode))) {
                        String rId = child.getKey();
                        String code = child.child("code").getValue(String.class);
                        if (rId != null) {
                            if (callback != null) callback.onRoomFound(rId, code, rMode != null ? rMode : modeStr);
                            return;
                        }
                    }
                }
                if (callback != null) callback.onRoomFound(null, null, null);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (callback != null) callback.onRoomFound(null, null, null);
            }
        });
    }

    /** Sync user profile & avatar from Firebase RTDB upon re-login */
    public void syncUserProfileOnLogin(String uid, glab.guesscard.network.PreferenceManager prefs, Runnable onComplete) {
        if (uid == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        database.child("users").child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("displayName").getValue(String.class);
                    String avatarFile = snapshot.child("avatarFileName").getValue(String.class);
                    if (name != null && !name.isEmpty()) prefs.saveUsername(name);
                    if (avatarFile != null && !avatarFile.isEmpty()) prefs.saveAvatarFileName(avatarFile);
                    //noinspection unchecked
                    cacheUserProfile(uid, (Map<String, Object>) snapshot.getValue());
                }
                if (onComplete != null) onComplete.run();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (onComplete != null) onComplete.run();
            }
        });
    }

    public interface RoomCreateCallback {
        void onRoomCreated(String roomId, String roomCode, String mode);
    }

    public interface RoomLookupCallback {
        void onRoomFound(String roomId, String roomCode, String mode);
    }

    /** Generate a 6-digit numeric code that is not used by any existing room. */
    public void generateUniqueRoomCode(DataCallback<String> callback) {
        database.child("rooms").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                java.util.Set<String> used = new java.util.HashSet<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String existingCode = child.child("code").getValue(String.class);
                    if (existingCode != null) used.add(existingCode);
                }
                if (callback != null) callback.onResult(generateUnusedCode(used));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (callback != null) callback.onResult(generateUnusedCode(null));
            }
        });
    }

    private String generateUnusedCode(java.util.Set<String> used) {
        if (used == null) used = new java.util.HashSet<>();
        for (int attempt = 0; attempt < 50; attempt++) {
            String candidate = String.valueOf(100000 + new java.util.Random().nextInt(900000));
            if (!used.contains(candidate)) return candidate;
        }
        return String.valueOf(100000 + new java.util.Random().nextInt(900000));
    }

    /**
     * Create or reuse the host's room under /rooms/{hostUid}/ (host UID is the root node).
     * The join code is stored in /rooms/{hostUid}/code and is guaranteed unique:
     * existing codes are checked first and a new one is generated on collision.
     */
    public void getOrCreateHostRoom(String hostUid, String hostName, String avatarFileName, String roomName, String mode, RoomCreateCallback callback) {
        if (hostUid == null || hostUid.isEmpty()) {
            FirebaseUser u = getCurrentUser();
            if (u != null) {
                hostUid = u.getUid();
            } else {
                hostUid = "User_" + String.valueOf(100000 + new java.util.Random().nextInt(900000));
            }
        }
        final String hUid = hostUid;
        final String hName = hostName != null ? hostName : "Player";
        final String aFile = avatarFileName != null ? avatarFileName : "avatar_01.png";
        final String roomTitle = normalizeRoomName(roomName, hName);
        final String modeStr = mode != null ? mode : "ANIMALS";
        final DatabaseReference roomRef = database.child("rooms").child(hUid);

        // Reuse the host's existing room if it is still in the lobby and the host is in it.
        roomRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()
                        && snapshot.child("players").hasChild(hUid)
                        && "LOBBY".equalsIgnoreCase(getStringOrEmpty(snapshot.child("status").getValue(String.class)))) {
                    String existingCode = snapshot.child("code").getValue(String.class);
                    String existingMode = snapshot.child("mode").getValue(String.class);
                    if (existingCode != null && !existingCode.isEmpty()) {
                        roomRef.child("name").setValue(roomTitle);
                        upsertHostPlayer(roomRef, hUid, hName, aFile);
                        if (callback != null) callback.onRoomCreated(hUid, existingCode, existingMode != null ? existingMode : modeStr);
                        return;
                    }
                }
                createRoomWithUniqueCode(roomRef, hUid, hName, aFile, roomTitle, modeStr, callback);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                createRoomWithUniqueCode(roomRef, hUid, hName, aFile, roomTitle, modeStr, callback);
            }
        });
    }

    private String normalizeRoomName(String roomName, String hostName) {
        String name = roomName != null ? roomName.trim() : "";
        if (name.isEmpty()) {
            name = hostName != null && !hostName.isEmpty() ? hostName + "'s Room" : "My Room";
        }
        if (name.length() > 30) name = name.substring(0, 30);
        return name;
    }

    private void createRoomWithUniqueCode(DatabaseReference roomRef, String hUid, String hName, String aFile, String roomName, String modeStr, RoomCreateCallback callback) {
        database.child("rooms").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                java.util.Set<String> used = new java.util.HashSet<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String c = child.child("code").getValue(String.class);
                    if (c != null) used.add(c);
                }
                String code = generateUnusedCode(used);
                writeRoomWithCode(roomRef, hUid, hName, aFile, roomName, modeStr, code, callback);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                writeRoomWithCode(roomRef, hUid, hName, aFile, roomName, modeStr, generateUnusedCode(null), callback);
            }
        });
    }

    private void writeRoomWithCode(DatabaseReference roomRef, String hUid, String hName, String aFile, String roomName, String modeStr, String code, RoomCreateCallback callback) {
        Map<String, Object> roomData = new HashMap<>();
        roomData.put("roomId", hUid);
        roomData.put("name", roomName);
        roomData.put("code", code);
        roomData.put("mode", modeStr);
        roomData.put("hostUid", hUid);
        roomData.put("status", "LOBBY");
        roomData.put("createdAt", ServerValue.TIMESTAMP);
        roomRef.updateChildren(roomData);

        // Code index so /roomCodes/{code} resolves instantly to the host's room.
        database.child("roomCodes").child(code).setValue(hUid);
        upsertHostPlayer(roomRef, hUid, hName, aFile);

        if (callback != null) callback.onRoomCreated(hUid, code, modeStr);
    }

    /** Write (or refresh) the host's presence under /rooms/{hostUid}/players/{hostUid}. */
    private void upsertHostPlayer(DatabaseReference roomRef, String hostUid, String hostName, String avatarFileName) {
        Map<String, Object> playerData = new HashMap<>();
        playerData.put("uid", hostUid);
        playerData.put("displayName", hostName);
        playerData.put("avatarFileName", avatarFileName);
        playerData.put("joinedAt", ServerValue.TIMESTAMP);
        playerData.put("ready", false);
        roomRef.child("players").child(hostUid).setValue(playerData);
    }

    /** Upload room details & host player presence to /rooms/{hostUid} with a unique join code. */
    public void createRoomOnFirebase(String roomId, String modeStr, String hostUid, String hostName, String avatarFileName, String roomName, RoomCreateCallback callback) {
        getOrCreateHostRoom(hostUid != null ? hostUid : roomId, hostName, avatarFileName, roomName, modeStr, callback);
    }

    /** Check whether a room still has a free slot and is not already in progress. */
    public void hasRoomSlot(String roomId, DataCallback<Boolean> callback) {
        if (roomId == null) {
            if (callback != null) callback.onResult(false);
            return;
        }
        database.child("rooms").child(roomId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    if (callback != null) callback.onResult(false);
                    return;
                }
                String status = snapshot.child("status").getValue(String.class);
                boolean inProgress = "PLAYING".equalsIgnoreCase(status) || "IN_PROGRESS".equalsIgnoreCase(status);
                long pCount = snapshot.child("players").getChildrenCount();
                if (callback != null) callback.onResult(!inProgress && pCount < 5);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (callback != null) callback.onResult(false);
            }
        });
    }

    /** Resolve a join code to the host's room node: /rooms/{hostUid}/code. */
    public void findRoomByCode(String code, RoomLookupCallback callback) {
        if (code == null || code.trim().isEmpty()) {
            if (callback != null) callback.onRoomFound(null, null, null);
            return;
        }
        final String c = code.trim().toUpperCase();
        database.child("roomCodes").child(c).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String hostUid = snapshot.getValue(String.class);
                if (hostUid != null && !hostUid.isEmpty()) {
                    database.child("rooms").child(hostUid).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot roomSnap) {
                            if (roomSnap.exists()) {
                                String roomMode = roomSnap.child("mode").getValue(String.class);
                                if (callback != null) callback.onRoomFound(hostUid, c, roomMode);
                            } else {
                                if (callback != null) callback.onRoomFound(null, null, null);
                            }
                        }
                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            if (callback != null) callback.onRoomFound(null, null, null);
                        }
                    });
                } else {
                    scanRoomsForCode(c, callback);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                scanRoomsForCode(c, callback);
            }
        });
    }

    /** Fallback lookup: scan /rooms for the first room whose code matches. */
    private void scanRoomsForCode(String code, RoomLookupCallback callback) {
        database.child("rooms").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    String c = child.child("code").getValue(String.class);
                    if (c != null && c.equalsIgnoreCase(code)) {
                        String hostUid = child.getKey();
                        String roomMode = child.child("mode").getValue(String.class);
                        if (callback != null) callback.onRoomFound(hostUid, c, roomMode);
                        return;
                    }
                }
                if (callback != null) callback.onRoomFound(null, null, null);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (callback != null) callback.onRoomFound(null, null, null);
            }
        });
    }

    /** Delete room under /rooms/{roomId} plus its /roomCodes index entry. */
    public void deleteRoom(String roomId) {
        if (roomId == null) return;
        final DatabaseReference roomRef = database.child("rooms").child(roomId);
        roomRef.child("code").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String code = snapshot.getValue(String.class);
                if (code != null) database.child("roomCodes").child(code).removeValue();
                roomRef.removeValue();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                roomRef.removeValue();
            }
        });
    }

    /** Save match result and player stats in Firebase RTDB */
    public void saveMatchHistory(String uid, String winnerName, int score, String mode) {
        if (uid == null) return;
        DatabaseReference userRef = database.child("users").child(uid);
        Map<String, Object> match = new HashMap<>();
        match.put("winnerName", winnerName);
        match.put("score", score);
        match.put("mode", mode);
        match.put("timestamp", ServerValue.TIMESTAMP);
        userRef.child("history").push().setValue(match);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long played = snapshot.child("gamesPlayed").exists() ? snapshot.child("gamesPlayed").getValue(Long.class) : 0;
                long won = snapshot.child("gamesWon").exists() ? snapshot.child("gamesWon").getValue(Long.class) : 0;
                userRef.child("gamesPlayed").setValue(played + 1);
                if (winnerName != null && winnerName.contains("YOU")) {
                    userRef.child("gamesWon").setValue(won + 1);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
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

    private String getStringOrEmpty(String value) {
        return value != null ? value : "";
    }
}
