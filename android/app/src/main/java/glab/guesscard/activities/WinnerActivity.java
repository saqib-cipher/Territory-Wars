package glab.guesscard.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.annotation.Nullable;

import glab.guesscard.R;

/**
 * Victory / Final Results Activity for Guess the Card party game.
 * Shows trophy animation, winner avatar, score breakdown, and Play Again / Home buttons.
 */
public class WinnerActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_winner);

        String winnerName = getIntent().getStringExtra("winnerName");
        int finalScore = getIntent().getIntExtra("finalScore", 0);

        try {
            container().getAudio().playSound(glab.guesscard.audio.GameAudio.Sound.VICTORY);
        } catch (Exception ignored) {}

        TextView tvWinnerName = findViewById(R.id.tvWinnerName);
        TextView tvFinalScore = findViewById(R.id.tvFinalScore);

        if (tvWinnerName != null && winnerName != null) {
            tvWinnerName.setText(winnerName + " is the Winner!");
        }

        if (tvFinalScore != null) {
            tvFinalScore.setText("Final Score: " + finalScore + " pts");
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
