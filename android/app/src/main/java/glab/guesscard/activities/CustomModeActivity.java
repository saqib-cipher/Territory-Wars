package glab.guesscard.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import glab.guesscard.ModernFButton;
import glab.guesscard.R;
import glab.guesscard.models.Card;
import glab.guesscard.models.CustomMode;
import glab.guesscard.models.GameMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom Mode Creation Activity for Guess the Card party game.
 * Allows host to build custom card packs (College, Marvel, Anime, etc.),
 * edit cards, set question limits, set round timers, and launch multiplayer or local rooms.
 */
public class CustomModeActivity extends BaseActivity {

    private EditText etModeName;
    private EditText etQuestionLimit;
    private EditText etRoundTimer;
    private EditText etCardWord;
    private RecyclerView rvCustomCards;

    private CustomMode customMode;
    private CustomCardsAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_mode);

        etModeName = findViewById(R.id.etModeName);
        etQuestionLimit = findViewById(R.id.etQuestionLimit);
        etRoundTimer = findViewById(R.id.etRoundTimer);
        etCardWord = findViewById(R.id.etCardWord);
        rvCustomCards = findViewById(R.id.rvCustomCards);

        customMode = new CustomMode();
        // Sample default cards for demonstration
        customMode.addCard("Professor");
        customMode.addCard("Exam");
        customMode.addCard("Laptop");

        adapter = new CustomCardsAdapter(customMode.cards, position -> {
            customMode.removeCard(position);
            adapter.notifyDataSetChanged();
        });

        rvCustomCards.setLayoutManager(new LinearLayoutManager(this));
        rvCustomCards.setAdapter(adapter);

        findViewById(R.id.btnAddCard).setOnClickListener(v -> addCardFromInput());
        findViewById(R.id.btnExportPack).setOnClickListener(v -> exportPackJson());
        findViewById(R.id.btnSaveCustomMode).setOnClickListener(v -> saveAndHost());
    }

    private void addCardFromInput() {
        String word = etCardWord.getText().toString().trim();
        if (word.isEmpty()) return;

        customMode.addCard(word);
        adapter.notifyDataSetChanged();
        etCardWord.setText("");
    }

    private void exportPackJson() {
        try {
            String json = customMode.toJson().toString(2);
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                    getSystemService(CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("CustomPackJSON", json);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "JSON copied to clipboard!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to export JSON", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveAndHost() {
        String name = etModeName.getText().toString().trim();
        if (!name.isEmpty()) customMode.name = name;

        try {
            int qLimit = Integer.parseInt(etQuestionLimit.getText().toString().trim());
            customMode.questionLimit = qLimit;
        } catch (Exception ignored) {}

        try {
            int timer = Integer.parseInt(etRoundTimer.getText().toString().trim());
            customMode.roundTimeSeconds = timer;
        } catch (Exception ignored) {}

        if (customMode.cards.isEmpty()) {
            Toast.makeText(this, "Add at least 1 card to save pack", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, LobbyActivity.class);
        intent.putExtra(LobbyActivity.EXTRA_MODE, GameMode.CUSTOM.name());
        startActivity(intent);
        finish();
    }

    private static class CustomCardsAdapter extends RecyclerView.Adapter<CustomCardsAdapter.ViewHolder> {

        interface OnDeleteListener {
            void onDelete(int position);
        }

        private final List<Card> cards;
        private final OnDeleteListener deleteListener;

        CustomCardsAdapter(List<Card> cards, OnDeleteListener deleteListener) {
            this.cards = cards != null ? cards : new ArrayList<>();
            this.deleteListener = deleteListener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Card card = cards.get(position);
            TextView tv = holder.itemView.findViewById(android.R.id.text1);
            tv.setText((position + 1) + ". " + card.word);
            tv.setTextColor(android.graphics.Color.WHITE);
            tv.setPadding(24, 24, 24, 24);

            holder.itemView.setOnClickListener(v -> {
                if (deleteListener != null) deleteListener.onDelete(position);
            });
        }

        @Override
        public int getItemCount() {
            return cards.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ViewHolder(View view) {
                super(view);
            }
        }
    }
}
