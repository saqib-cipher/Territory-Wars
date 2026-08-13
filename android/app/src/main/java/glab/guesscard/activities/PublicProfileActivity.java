package glab.guesscard.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import glab.guesscard.ModernFButton;
import glab.guesscard.R;
import glab.guesscard.firebase.FirebaseManager;

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
                        boolean isFriendNow = btnFriend.getTag() != null && Boolean.TRUE.equals(btnFriend.getTag());
                        if (isFriendNow) {
                            mgr.removeFriend(myUid, targetUid);
                            Toast.makeText(this, "Friend removed", Toast.LENGTH_SHORT).show();
                        } else {
                            mgr.addFriend(myUid, targetUid);
                            Toast.makeText(this, "Friend added!", Toast.LENGTH_SHORT).show();
                        }
                        refreshFriendState(btnFriend, myUid, targetUid);
                    });
                }
            }

            mgr.getUserProfile(targetUid, data -> {
                if (data != null) {
                    runOnUiThread(() -> {
                        String name = (String) data.getOrDefault("displayName", "Player");
                        tvName.setText(name);

                        String avatarFile = (String) data.getOrDefault("avatarFileName", "avatar_01.png");
                        if (imgAvatar != null) {
                            glab.guesscard.utils.AvatarManager.getInstance().loadAvatarIntoImageView(this, imgAvatar, avatarFile);
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
        container().getFirebaseManager().isFriend(myUid, targetUid, isFriend -> runOnUiThread(() -> {
            if (btnFriend == null) return;
            btnFriend.setTag(isFriend);
            if (isFriend != null && isFriend) {
                btnFriend.setText("Remove Friend");
                btnFriend.setAlpha(1.0f);
            } else {
                btnFriend.setText("Add Friend");
                btnFriend.setAlpha(1.0f);
            }
        }));
    }
}
