package glab.guesscard.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

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

        TextView tvName = findViewById(R.id.tvPublicUsername);
        TextView tvUid = findViewById(R.id.tvPublicUid);
        TextView tvLevel = findViewById(R.id.tvPublicLevel);
        TextView tvPlayed = findViewById(R.id.tvPublicGamesPlayed);
        TextView tvWins = findViewById(R.id.tvPublicWins);
        TextView tvRate = findViewById(R.id.tvPublicWinRate);
        ModernFButton btnClose = findViewById(R.id.btnClosePublicProfile);

        if (btnClose != null) btnClose.setOnClickListener(v -> finish());

        if (targetUid != null) {
            tvUid.setText("UID: " + targetUid);
            container().getFirebaseManager().getUserProfile(targetUid, data -> {
                if (data != null) {
                    runOnUiThread(() -> {
                        String name = (String) data.getOrDefault("displayName", "Player");
                        tvName.setText(name);

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
}
