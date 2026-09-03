package glab.guesscard.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import glab.guesscard.ModernFButton;
import glab.guesscard.R;
import glab.guesscard.firebase.FirebaseManager;
import glab.guesscard.firebase.FriendshipManager;

public class PublicProfileActivity extends BaseActivity {
    public static final String EXTRA_TARGET_UID = "extra_target_uid";

    public static Intent intent(Context context, String targetUid) {
        Intent intent = new Intent(context, PublicProfileActivity.class);
        intent.putExtra(EXTRA_TARGET_UID, targetUid);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_public_profile);

        String targetUid = getIntent().getStringExtra(EXTRA_TARGET_UID);

        android.widget.ImageView imgAvatar = findViewById(R.id.imgPublicAvatar);
        TextView tvName = findViewById(R.id.tvPublicUsername);
        TextView tvUid = findViewById(R.id.tvPublicUid);
        TextView tvStatus = findViewById(R.id.tvPublicStatus);
        TextView tvLevel = findViewById(R.id.tvPublicLevel);
        TextView tvPlayed = findViewById(R.id.tvPublicGamesPlayed);
        TextView tvWins = findViewById(R.id.tvPublicWins);
        TextView tvRate = findViewById(R.id.tvPublicWinRate);
        ModernFButton btnClose = findViewById(R.id.btnClosePublicProfile);
        final ModernFButton btnFriend = findViewById(R.id.btnFriendAction);

        if (btnClose != null) btnClose.setOnClickListener(v -> finish());

        String myUid = prefs().getUserId();
        FirebaseManager mgr = container().getFirebaseManager();

        if (targetUid != null) {
            tvUid.setText("UID: " + targetUid);

            if (btnFriend != null) {
                if (myUid != null && myUid.equals(targetUid)) {
                    btnFriend.setVisibility(View.GONE);
                } else {
                    btnFriend.setVisibility(View.VISIBLE);
                    refreshFriendState(btnFriend, myUid, targetUid);
                    btnFriend.setOnClickListener(v -> {
                        if (myUid == null) return;
                        Object tag = btnFriend.getTag();
                        String targetName = tvName != null ? tvName.getText().toString() : "Player";
                        if (tag == FirebaseManager.FriendshipStatus.FRIENDS) {
                            FriendshipManager.removeFriend(this, myUid, targetUid, targetName, new FriendshipManager.FriendshipActionCallback() {
                                @Override
                                public void onSuccess() {
                                    refreshFriendState(btnFriend, myUid, targetUid);
                                }

                                @Override
                                public void onError(String message) {}
                            });
                        } else if (tag == FirebaseManager.FriendshipStatus.REQUEST_RECEIVED) {
                            FriendshipManager.acceptFriendRequest(this, myUid, targetUid, targetName, new FriendshipManager.FriendshipActionCallback() {
                                @Override
                                public void onSuccess() {
                                    refreshFriendState(btnFriend, myUid, targetUid);
                                }

                                @Override
                                public void onError(String message) {}
                            });
                        } else if (tag == FirebaseManager.FriendshipStatus.REQUEST_SENT) {
                            // Already requested
                            return;
                        } else {
                            FriendshipManager.sendFriendRequest(this, myUid, targetUid, targetName, new FriendshipManager.FriendshipActionCallback() {
                                @Override
                                public void onSuccess() {
                                    refreshFriendState(btnFriend, myUid, targetUid);
                                }

                                @Override
                                public void onError(String message) {}
                            });
                        }
                    });
                }
            }

            mgr.getUserProfile(targetUid, data -> {
                if (data != null) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        String name = (String) data.getOrDefault("displayName", data.getOrDefault("username", "Player"));
                        tvName.setText(name);

                        String avatarFile = (String) data.getOrDefault("avatarFileName", "avatar1.png");
                        if (imgAvatar != null) {
                            glab.guesscard.utils.AvatarManager.getInstance().loadAvatarIntoImageView(this, imgAvatar, avatarFile);
                        }

                        // Check live presence status from socket + profile lastSeen
                        boolean isOnlineFromDb = Boolean.TRUE.equals(data.get("isOnline"));
                        Long lastSeenTs = null;
                        Object lsObj = data.get("lastSeen");
                        if (lsObj instanceof Long) lastSeenTs = (Long) lsObj;

                        final Long finalLastSeen = lastSeenTs;
                        if (container() != null && container().getSocketClient() != null) {
                            container().getSocketClient().checkOnlineStatus(
                                    java.util.Collections.singletonList(targetUid), statusMap -> runOnUiThread(() -> {
                                        if (isFinishing() || isDestroyed()) return;
                                        boolean online = (statusMap != null && Boolean.TRUE.equals(statusMap.get(targetUid))) || isOnlineFromDb;
                                        if (tvStatus != null) {
                                            tvStatus.setText(FirebaseManager.formatLastSeen(online, finalLastSeen));
                                            tvStatus.setTextColor(online
                                                    ? android.graphics.Color.parseColor("#10B981")
                                                    : android.graphics.Color.parseColor("#94A3B8"));
                                        }
                                    }));
                        } else {
                            if (tvStatus != null) {
                                tvStatus.setText(FirebaseManager.formatLastSeen(isOnlineFromDb, finalLastSeen));
                            }
                        }

                        Object lvlObj = data.get("level");
                        int level = lvlObj instanceof Long ? ((Long) lvlObj).intValue() : 1;
                        tvLevel.setText("Level " + level);

                        Object playedObj = data.get("gamesPlayed");
                        int played = playedObj instanceof Long ? ((Long) playedObj).intValue() : 0;
                        tvPlayed.setText(String.valueOf(played));

                        Object winsObj = data.get("gamesWon");
                        int wins = winsObj instanceof Long ? ((Long) winsObj).intValue() : 0;
                        tvWins.setText(String.valueOf(wins));

                        int rate = played > 0 ? (wins * 100 / played) : 0;
                        tvRate.setText(rate + "%");
                    });
                }
            });
        }
    }

    private void refreshFriendState(ModernFButton btnFriend, String myUid, String targetUid) {
        container().getFirebaseManager().checkFriendshipStatus(myUid, targetUid, status -> runOnUiThread(() -> {
            if (btnFriend == null || isFinishing() || isDestroyed()) return;
            btnFriend.setTag(status);
            if (status == FirebaseManager.FriendshipStatus.FRIENDS) {
                btnFriend.setText("Remove Friend");
                btnFriend.setEnabled(true);
                btnFriend.setAlpha(1.0f);
            } else if (status == FirebaseManager.FriendshipStatus.REQUEST_SENT) {
                btnFriend.setText("Requested ⏳");
                btnFriend.setEnabled(false);
                btnFriend.setAlpha(0.7f);
            } else if (status == FirebaseManager.FriendshipStatus.REQUEST_RECEIVED) {
                btnFriend.setText("Accept Request 🤝");
                btnFriend.setEnabled(true);
                btnFriend.setAlpha(1.0f);
            } else {
                btnFriend.setText("+ Add Friend");
                btnFriend.setEnabled(true);
                btnFriend.setAlpha(1.0f);
            }
        }));
    }
}
