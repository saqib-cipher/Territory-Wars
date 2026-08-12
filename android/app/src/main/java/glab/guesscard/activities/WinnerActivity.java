package glab.guesscard.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.annotation.Nullable;

import glab.guesscard.R;

/**
 * Victory / Final Results Activity for Guess the Card party game.
 * Shows trophy animation, winner avatar, score breakdown, saves player history, and cleans up room on RTDB.
 */
public class WinnerActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_winner);

        String winnerName = getIntent().getStringExtra("winnerName");
        int finalScore = getIntent().getIntExtra("finalScore", 0);
        String roomId = getIntent().getStringExtra("roomId");
        String mode = getIntent().getStringExtra("mode");

        if (winnerName == null || winnerName.isEmpty()) winnerName = "YOU";

        try {
            container().getAudio().playSound(glab.guesscard.audio.GameAudio.Sound.VICTORY);
        } catch (Exception ignored) {}

        TextView tvWinnerName = findViewById(R.id.tvWinnerName);
        TextView tvFinalScore = findViewById(R.id.tvFinalScore);

        if (tvWinnerName != null) {
            tvWinnerName.setText(winnerName + " is the Winner! 🎉");
        }

        if (tvFinalScore != null) {
            tvFinalScore.setText("Final Score: " + finalScore + " pts");
        }

        // Save history & clean up room on server
        String uid = prefs().getUserId();
        if (container() != null && container().getFirebaseManager() != null) {
            container().getFirebaseManager().saveMatchHistory(uid, winnerName, finalScore, mode != null ? mode : "ANIMALS");
            if (roomId != null && !roomId.isEmpty()) {
                container().getFirebaseManager().deleteRoom(roomId);
            }
        }

        findViewById(R.id.btnPlayAgain).setOnClickListener(v -> {
            Intent intent = new Intent(this, LobbyActivity.class);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.btnHome).setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}
