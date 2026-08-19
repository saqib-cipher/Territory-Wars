package glab.guesscard.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

import glab.guesscard.R;
import glab.guesscard.fragments.GameFragment;
import glab.guesscard.models.GameMode;

public class GameActivity extends BaseActivity {
    public static final String EXTRA_MODE = "extra_mode";
    public static final String EXTRA_ROOM_ID = "extra_room_id";
    public static final String EXTRA_DURATION = "extra_duration";
    /** The UID of the player who is the Answerer at match start. Passed from LobbyActivity. */
    public static final String EXTRA_ANSWERER_UID = "extra_answerer_uid";

    public static Intent intent(Context context, GameMode mode, String roomId, long durationMillis) {
        Intent intent = new Intent(context, GameActivity.class);
        intent.putExtra(EXTRA_MODE, mode.name());
        intent.putExtra(EXTRA_ROOM_ID, roomId);
        intent.putExtra(EXTRA_DURATION, durationMillis);
        return intent;
    }

    public static Intent intent(Context context, GameMode mode, String roomId, long durationMillis, String answererUid) {
        Intent intent = intent(context, mode, roomId, durationMillis);
        intent.putExtra(EXTRA_ANSWERER_UID, answererUid);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new GameFragment())
                    .commit();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Clear away flag when app comes back to foreground
        String roomId = getIntent().getStringExtra(EXTRA_ROOM_ID);
        String myUid = prefs().getUserId();
        if (roomId != null && !roomId.isEmpty() && myUid != null && container() != null && container().getFirebaseManager() != null) {
            container().getFirebaseManager().getRoomRef(roomId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    if (!snapshot.exists()) {
                        runOnUiThread(() -> {
                            Toast.makeText(GameActivity.this, "Match has ended", Toast.LENGTH_SHORT).show();
                            returnToMain();
                        });
                        return;
                    }
                    String status = snapshot.child("status").getValue(String.class);
                    if ("CLOSED".equalsIgnoreCase(status) || "ABANDONED".equalsIgnoreCase(status) || "FINISHED".equalsIgnoreCase(status)) {
                        runOnUiThread(() -> {
                            Toast.makeText(GameActivity.this, "Match has ended", Toast.LENGTH_SHORT).show();
                            returnToMain();
                        });
                    } else {
                        // Match still running — clear away flag
                        container().getFirebaseManager().getRoomRef(roomId)
                                .child("players").child(myUid).child("isAway").setValue(false);
                    }
                }
                @Override public void onCancelled(DatabaseError error) {}
            });
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Mark player as away only when app truly goes to background (not just a dialog overlay)
        String roomId = getIntent().getStringExtra(EXTRA_ROOM_ID);
        String myUid = prefs().getUserId();
        if (roomId != null && !roomId.isEmpty() && myUid != null && container() != null && container().getFirebaseManager() != null) {
            container().getFirebaseManager().getRoomRef(roomId)
                    .child("players").child(myUid).child("isAway").setValue(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop sounds on pause (covers dialog overlays, home button, etc.)
        if (container() != null && container().getAudio() != null) {
            container().getAudio().stopAllSounds();
        }
    }

    @Override
    public void onBackPressed() {
        String roomId = getIntent().getStringExtra(EXTRA_ROOM_ID);
        boolean isOnlineGame = roomId != null && !roomId.isEmpty();

        if (isOnlineGame) {
            showLeaveConfirmationDialog(roomId);
        } else {
            // Offline mode: just leave freely
            if (container() != null && container().getAudio() != null) {
                container().getAudio().stopAllSounds();
            }
            super.onBackPressed();
            returnToMain();
        }
    }

    /**
     * Shows leave confirmation dialog for online games.
     * - Deducts 50 points from the leaving player's score.
     * - Updates room status to CLOSED for host action.
     */
    private void showLeaveConfirmationDialog(String roomId) {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ Leave Match?")
                .setMessage("Leaving in the middle of the match will cost you −50 points!\n\n"
                        + "Your team may also be affected if players are too few.")
                .setPositiveButton("Leave Anyway", (d, w) -> {
                    d.dismiss();
                    abandonOnlineMatch(roomId);
                })
                .setNegativeButton("Stay", (d, w) -> d.dismiss())
                .setCancelable(false)
                .show();
    }

    private void abandonOnlineMatch(String roomId) {
        if (container() != null && container().getAudio() != null) {
            container().getAudio().stopAllSounds();
        }

        String myUid = prefs().getUserId();
        if (myUid == null) {
            returnToMain();
            return;
        }

        // Deduct 50 points from the leaver
        container().getFirebaseManager().getRoomRef(roomId)
                .child("players").child(myUid).child("score")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        Long current = snapshot.getValue(Long.class);
                        long deducted = Math.max(0, (current != null ? current : 0L) - 50);
                        snapshot.getRef().setValue(deducted);
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {}
                });

        // Remove the player from the room
        container().getFirebaseManager().getRoomRef(roomId)
                .child("players")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        long playerCount = snapshot.getChildrenCount();

                        if (playerCount <= 2) {
                            // Only host is left — mark status as CLOSED so host gets the abandoned dialog
                            Map<String, Object> closeData = new HashMap<>();
                            closeData.put("status", "CLOSED");
                            container().getFirebaseManager().getRoomRef(roomId).updateChildren(closeData);
                        }
                        snapshot.child(myUid).getRef().removeValue();
                        returnToMain();
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        returnToMain();
                    }
                });
    }

    public void returnToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
