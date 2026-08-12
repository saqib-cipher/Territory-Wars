package glab.guesscard.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.ArrayList;

import glab.guesscard.ModernFButton;
import glab.guesscard.R;
import glab.guesscard.audio.GameAudio;

/**
 * Victory / Final Results Activity for Guess the Card.
 * Shows winner announcement, score breakdown with standings,
 * card results, and Play Again / Home buttons.
 */
public class WinnerActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_winner);

        String winnerName = getIntent().getStringExtra("winnerName");
        int finalScore = getIntent().getIntExtra("finalScore", 0);
        String matchId = getIntent().getStringExtra("matchId");
        String mode = getIntent().getStringExtra("mode");
        ArrayList<String> standingsNames = getIntent().getStringArrayListExtra("standingsNames");
        ArrayList<Integer> standingsScores = getIntent().getIntegerArrayListExtra("standingsScores");

        // Play victory sound
        try {
            GameAudio audio = container().getAudio();
            if (audio != null) {
                audio.setSoundEnabled(true);
                audio.playSound(GameAudio.Sound.VICTORY);
            }
        } catch (Exception ignored) {}

        TextView tvWinnerName = findViewById(R.id.tvWinnerName);
        TextView tvFinalScore = findViewById(R.id.tvFinalScore);
        TextView tvMatchInfo = findViewById(R.id.tvMatchInfo);
        LinearLayout standingsContainer = findViewById(R.id.standingsContainer);

        if (tvWinnerName != null) {
            tvWinnerName.setText(winnerName != null ? "🏆 " + winnerName + " Wins!" : "🏆 Match Complete!");
        }

        if (tvFinalScore != null) {
            tvFinalScore.setText(finalScore + " pts");
        }

        if (tvMatchInfo != null && matchId != null) {
            String modeText = mode != null ? mode : "ANIMALS";
            tvMatchInfo.setText(modeText + " Mode • Match complete");
            tvMatchInfo.setVisibility(View.VISIBLE);
        }

        // Show standings
        if (standingsContainer != null && standingsNames != null && standingsScores != null) {
            standingsContainer.removeAllViews();
            for (int i = 0; i < standingsNames.size() && i < standingsScores.size(); i++) {
                String entry = standingsNames.get(i);
                int score = standingsScores.get(i);
                String[] parts = entry.split(":", 2);
                String name = parts.length > 0 ? parts[0] : "Player";

                View row = getLayoutInflater().inflate(R.layout.item_standing_row, standingsContainer, false);
                TextView tvRank = row.findViewById(R.id.tvStandingRank);
                TextView tvName = row.findViewById(R.id.tvStandingName);
                TextView tvScore = row.findViewById(R.id.tvStandingScore);

                if (tvRank != null) tvRank.setText(String.valueOf(i + 1));
                if (tvName != null) tvName.setText(name);
                if (tvScore != null) tvScore.setText(score + " pts");

                // Highlight winner
                if (i == 0) {
                    row.setBackgroundColor(android.graphics.Color.parseColor("#1A3B2E"));
                    if (tvRank != null) tvRank.setTextColor(android.graphics.Color.parseColor("#F59E0B"));
                }

                standingsContainer.addView(row);
            }
            standingsContainer.setVisibility(View.VISIBLE);
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
