package glab.guesscard.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseUser;

import glab.guesscard.GuessCardApp;
import glab.guesscard.ModernFButton;
import glab.guesscard.R;
import glab.guesscard.activities.GameActivity;
import glab.guesscard.activities.WinnerActivity;
import glab.guesscard.di.GameContainer;
import glab.guesscard.firebase.FirebaseManager;
import glab.guesscard.game.GuessTheCardEngine;
import glab.guesscard.models.Card;
import glab.guesscard.models.GameMode;
import glab.guesscard.models.MatchResult;
import glab.guesscard.models.RoomInfo;
import glab.guesscard.network.PreferenceManager;
import glab.guesscard.socket.GameSocketClient;
import glab.guesscard.socket.GameSocketListener;
import glab.guesscard.utils.HapticsHelper;
import glab.guesscard.utils.VoiceRecognitionHelper;
import glab.guesscard.views.CustomGuessDialog;
import glab.guesscard.views.GameCardView;
import glab.guesscard.views.PlayerAvatarView;
import glab.guesscard.views.QuestionBubbleView;
import glab.guesscard.views.TimerView;

/**
 * In-Game Fragment for Guess Card.
 * - Online mode: YES/NO answers, voice-to-text question asking, shows who answered
 * - Offline mode: AI-simulated smart answers, category question chips
 */
public class GameFragment extends Fragment implements GameSocketListener, GuessTheCardEngine.EngineListener {

    private static final int REQ_RECORD_AUDIO = 200;

    private GameSocketClient socket;
    private PreferenceManager preferences;
    private FirebaseManager firebaseManager;
    private GuessTheCardEngine offlineEngine;
    private VoiceRecognitionHelper voiceHelper;

    private TextView tvRoundIndicator;
    private TextView tvQuestionsLeft;
    private TextView tvCurrentPlayer;
    private TimerView timerView;
    private LinearLayout gamePlayerContainer;
    private GameCardView gameCardView;
    private RecyclerView rvQuestionHistory;
    private View answerButtonContainer;
    private ModernFButton btnGuess;
    private ModernFButton btnVoiceQuestion;

    private boolean isOfflineMode = false;
    private boolean isGuesser = true;
    private QuestionHistoryAdapter historyAdapter;
    private String currentRoomId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_game, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        GameContainer appContainer = GuessCardApp.from(requireContext());
        socket = appContainer.getSocketClient();
        preferences = appContainer.getPreferences();
        firebaseManager = appContainer.getFirebaseManager();

        tvRoundIndicator = view.findViewById(R.id.tvRoundIndicator);
        tvQuestionsLeft = view.findViewById(R.id.tvQuestionsLeft);
        tvCurrentPlayer = view.findViewById(R.id.tvCurrentPlayer);
        timerView = view.findViewById(R.id.timerView);
        gamePlayerContainer = view.findViewById(R.id.gamePlayerContainer);
        gameCardView = view.findViewById(R.id.gameCardView);
        rvQuestionHistory = view.findViewById(R.id.rvQuestionHistory);
        answerButtonContainer = view.findViewById(R.id.answerButtonContainer);
        btnGuess = view.findViewById(R.id.btnGuess);
        btnVoiceQuestion = view.findViewById(R.id.btnVoiceQuestion);

        historyAdapter = new QuestionHistoryAdapter();
        if (rvQuestionHistory != null) {
            rvQuestionHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
            rvQuestionHistory.setAdapter(historyAdapter);
        }

        bindActionButtons(view);
        setupVoiceButton();
        initGameMode();
    }

    private void initGameMode() {
        Intent intent = getActivity() != null ? getActivity().getIntent() : null;
        String modeStr = intent != null ? intent.getStringExtra(GameActivity.EXTRA_MODE) : null;
        currentRoomId = intent != null ? intent.getStringExtra(GameActivity.EXTRA_ROOM_ID) : null;

        GameMode selectedMode = GameMode.ANIMALS;
        if (modeStr != null) {
            try { selectedMode = GameMode.valueOf(modeStr); } catch (Exception ignored) {}
        }

        if (selectedMode == GameMode.OFFLINE || socket == null || !socket.isConnected()) {
            isOfflineMode = true;
            offlineEngine = new GuessTheCardEngine();
            offlineEngine.setListener(this);
            GameMode offlineGameMode = (selectedMode == GameMode.OFFLINE) ? GameMode.ANIMALS : selectedMode;
            offlineEngine.startOfflinePractice(offlineGameMode);
            renderQuickQuestionChips(offlineGameMode);
            updateRoleControls();
            if (tvCurrentPlayer != null) tvCurrentPlayer.setText("Practice Mode — Guess the card!");
            if (btnVoiceQuestion != null) btnVoiceQuestion.setVisibility(View.GONE);
        } else {
            isOfflineMode = false;
            socket.setListener(this);
            renderQuickQuestionChips(selectedMode);
            if (btnVoiceQuestion != null) btnVoiceQuestion.setVisibility(View.VISIBLE);
        }
    }

    // ── VOICE ─────────────────────────────────────────────────────────────

    private void setupVoiceButton() {
        if (btnVoiceQuestion == null) return;
        btnVoiceQuestion.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(),
                        new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
                return;
            }
            startVoiceRecognition();
        });
    }

    private void startVoiceRecognition() {
        if (voiceHelper == null) {
            voiceHelper = new VoiceRecognitionHelper(requireContext(), new VoiceRecognitionHelper.Listener() {
                @Override
                public void onResult(String text) {
                    if (btnVoiceQuestion != null) btnVoiceQuestion.setText("🎤");
                    askQuestion(text);
                    Toast.makeText(requireContext(), "Asked: " + text, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String message) {
                    if (btnVoiceQuestion != null) btnVoiceQuestion.setText("🎤");
                    Toast.makeText(requireContext(), "Voice error: " + message, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onListeningStarted() {
                    if (btnVoiceQuestion != null) btnVoiceQuestion.setText("🔴");
                }

                @Override
                public void onListeningStopped() {
                    if (btnVoiceQuestion != null) btnVoiceQuestion.setText("🎤");
                }
            });
        }
        voiceHelper.startListening();
    }

    // ── QUICK QUESTION CHIPS ───────────────────────────────────────────────

    private void renderQuickQuestionChips(GameMode mode) {
        if (getView() == null) return;
        LinearLayout container = getView().findViewById(R.id.quickQuestionsContainer);
        if (container == null) return;
        container.removeAllViews();

        java.util.List<String> questions = new java.util.ArrayList<>();
        if (mode == GameMode.ANIMALS) {
            questions.add("Does it have 4 legs? 🐾");
            questions.add("Is it a domestic pet? 🐶");
            questions.add("Does it live in water? 🌊");
            questions.add("Can it fly? 🦅");
            questions.add("Is it a wild carnivore? 🦁");
            questions.add("Is it a reptile? 🐍");
            questions.add("Is it larger than a human? 🐘");
            questions.add("Is it found in forests? 🌲");
        } else if (mode == GameMode.FOOD) {
            questions.add("Is it sweet or a fruit? 🍬");
            questions.add("Is it served hot? 🌶️");
            questions.add("Eaten with hands? 🍔");
            questions.add("Is it vegetarian? 🥗");
            questions.add("Is it a main meal? 🍛");
            questions.add("Does it have cheese? 🧀");
            questions.add("Is it fast food? 🍟");
        } else if (mode == GameMode.COUNTRIES) {
            questions.add("Is it in Asia or Europe? 🌍");
            questions.add("Is it an island nation? 🏝️");
            questions.add("Population over 100M? 👥");
            questions.add("Famous for football? ⚽");
            questions.add("Is it in the Americas? 🌎");
            questions.add("Famous for landmarks? 🏛️");
        } else if (mode == GameMode.CELEBRITIES) {
            questions.add("Is this person an athlete? ⚽");
            questions.add("Is this person an actor/singer? 🎬");
            questions.add("From North America or Europe? 🌐");
            questions.add("Won world championships? 🏆");
            questions.add("An entrepreneur or leader? 🚀");
        } else {
            questions.add("Is it bigger than a human? 📏");
            questions.add("Found indoors? 🏠");
            questions.add("Is it man-made? ⚡");
            questions.add("Used daily? 📱");
        }

        for (String qText : questions) {
            ModernFButton chip = new ModernFButton(requireContext());
            chip.setText(qText);
            chip.setTextSize(12);
            chip.setButtonColor(android.graphics.Color.parseColor("#1E293B"));
            chip.setShadowHeightDp(2f);
            chip.setCornerRadiusDp(12f);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    Math.round(42 * getResources().getDisplayMetrics().density));
            lp.setMargins(0, 0, 12, 0);

            chip.setOnClickListener(v -> {
                chip.setVisibility(View.GONE);
                HapticsHelper.vibrateClick(chip);
                askQuestion(qText);
            });
            container.addView(chip, lp);
        }
    }

    private void askQuestion(String questionText) {
        if (isOfflineMode && offlineEngine != null) {
            offlineEngine.askQuestionLocally(questionText);
        } else if (socket != null) {
            socket.askQuestion(questionText);
        }
    }

    // ── ACTION BUTTONS ────────────────────────────────────────────────────

    private void bindActionButtons(View root) {
        ModernFButton btnYes = root.findViewById(R.id.btnAnswerYes);
        ModernFButton btnNo = root.findViewById(R.id.btnAnswerNo);

        if (btnYes != null) btnYes.setOnClickListener(v -> sendAnswer("YES 👍"));
        if (btnNo != null) btnNo.setOnClickListener(v -> sendAnswer("NO 👎"));

        if (btnGuess != null) {
            btnGuess.setOnClickListener(v -> {
                CustomGuessDialog dialog = new CustomGuessDialog(requireContext(), guessText -> {
                    if (isOfflineMode && offlineEngine != null) {
                        offlineEngine.submitGuess(guessText);
                    } else if (socket != null) {
                        socket.submitGuess(guessText);
                    }
                });
                dialog.show();
            });
        }
    }

    private void sendAnswer(String answer) {
        HapticsHelper.vibrateClick(btnGuess);
        if (isOfflineMode && offlineEngine != null) {
            offlineEngine.answerQuestionLocally(answer);
        } else if (socket != null) {
            socket.answerQuestion(answer);
            // Post to Firebase RTDB for history
            if (currentRoomId != null && firebaseManager.getCurrentUser() != null) {
                FirebaseUser user = firebaseManager.getCurrentUser();
                String name = user.getDisplayName() != null ? user.getDisplayName() : preferences.getUsername();
                firebaseManager.postQuestionAnswer(currentRoomId, "", "", "Answer", user.getUid(), name, answer);
            }
        }
    }

    private void updateRoleControls() {
        if (answerButtonContainer != null && btnGuess != null) {
            if (isGuesser) {
                // Guesser: sees GUESS button + question chips + mic
                answerButtonContainer.setVisibility(View.GONE);
                btnGuess.setVisibility(View.VISIBLE);
                if (btnVoiceQuestion != null && !isOfflineMode)
                    btnVoiceQuestion.setVisibility(View.VISIBLE);
            } else {
                // Answerer: sees YES/NO buttons only
                answerButtonContainer.setVisibility(View.VISIBLE);
                btnGuess.setVisibility(View.GONE);
                if (btnVoiceQuestion != null) btnVoiceQuestion.setVisibility(View.GONE);
            }
        }
    }

    // ── SOCKET CALLBACKS ──────────────────────────────────────────────────

    @Override
    public void onRoomUpdated(RoomInfo room) {
        if (room == null || !isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (tvRoundIndicator != null)
                tvRoundIndicator.setText("ROUND " + room.currentRound + " / " + room.totalRounds);
            if (tvQuestionsLeft != null)
                tvQuestionsLeft.setText(String.valueOf(room.questionsRemaining));

            isGuesser = room.isGuesser;
            if (gameCardView != null) gameCardView.setCardData(room.card, room.mode, isGuesser);

            updateRoleControls();
            renderPlayerAvatars(room);

            if (tvCurrentPlayer != null) {
                tvCurrentPlayer.setText(isGuesser ? "Your turn to ask questions!" : "Answer the questions — YES or NO");
            }

            if (room.questionHistory != null) {
                historyAdapter.setItems(room.questionHistory);
            }
        });
    }

    @Override
    public void onQuestionAsked(String question, String askerName) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            RoomInfo.QuestionItem item = new RoomInfo.QuestionItem();
            item.question = question;
            item.answer = "...";
            item.askerName = askerName;
            historyAdapter.add(item);
        });
    }

    @Override
    public void onAnswerGiven(String question, String answer, String answererName) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            // Update the last item with the actual answer and who answered
            historyAdapter.updateLastAnswer(answer, answererName);
        });
    }

    private void renderPlayerAvatars(RoomInfo room) {
        if (gamePlayerContainer == null || room.players == null) return;
        gamePlayerContainer.removeAllViews();

        for (RoomInfo.LobbyPlayer p : room.players) {
            PlayerAvatarView avatar = new PlayerAvatarView(requireContext());
            boolean isCurrentTurn = p.userId != null && p.userId.equals(room.currentTurnPlayerId);
            avatar.setPlayerData(p.username, p.score, p.isReady, isCurrentTurn);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            gamePlayerContainer.addView(avatar, lp);
        }
    }

    @Override
    public void onGuessResult(String guessedBy, String guess, boolean isCorrect, int scoreAwarded, String cardAnswer) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (isCorrect) {
                gameCardView.animateCorrectGuess(() ->
                        Toast.makeText(requireContext(), "🎉 " + guessedBy + " guessed it! +" + scoreAwarded + " pts", Toast.LENGTH_LONG).show());
            } else {
                gameCardView.animateWrongGuess();
                Toast.makeText(requireContext(), "❌ Wrong guess by " + guessedBy + ": " + guess, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onGuessResult(boolean isCorrect, String guess, int scoreAwarded, int totalScore) {
        onGuessResult("You", guess, isCorrect, scoreAwarded, "");
    }

    @Override
    public void onGameEnd(MatchResult result) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            Intent intent = new Intent(requireContext(), WinnerActivity.class);
            intent.putExtra("winnerName", "Winner");
            intent.putExtra("finalScore", result.score);
            startActivity(intent);
            requireActivity().finish();
        });
    }

    // ── OFFLINE ENGINE CALLBACKS ───────────────────────────────────────────

    @Override
    public void onCardChanged(Card card, boolean isGuesser) {
        if (!isAdded() || card == null) return;
        this.isGuesser = isGuesser;
        requireActivity().runOnUiThread(() -> {
            if (gameCardView != null) gameCardView.setCardData(card.word, card.category, isGuesser);
            updateRoleControls();
        });
    }

    @Override
    public void onTimerTick(int secondsRemaining) {
        if (timerView != null && isAdded()) {
            requireActivity().runOnUiThread(() -> timerView.setTime(secondsRemaining, 60));
        }
    }

    @Override
    public void onQuestionAdded(String question, String answer, int questionsRemaining) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (tvQuestionsLeft != null) tvQuestionsLeft.setText(String.valueOf(questionsRemaining));
            RoomInfo.QuestionItem item = new RoomInfo.QuestionItem();
            item.question = question;
            item.answer = answer;
            item.askerName = "You";
            item.answererName = "AI";
            historyAdapter.add(item);
            // Scroll to bottom
            if (rvQuestionHistory != null) {
                rvQuestionHistory.smoothScrollToPosition(Math.max(0, historyAdapter.getItemCount() - 1));
            }
        });
    }

    @Override
    public void onGameOver(int finalScore) {
        if (!isAdded()) return;
        MatchResult result = new MatchResult();
        result.score = finalScore;
        onGameEnd(result);
    }

    @Override
    public void onDestroyView() {
        if (socket != null) socket.setListener(null);
        if (offlineEngine != null) offlineEngine.stop();
        if (voiceHelper != null) voiceHelper.release();
        super.onDestroyView();
    }

    // ── QUESTION HISTORY ADAPTER ───────────────────────────────────────────

    private static class QuestionHistoryAdapter extends RecyclerView.Adapter<QuestionHistoryAdapter.ViewHolder> {
        private final java.util.List<RoomInfo.QuestionItem> items = new java.util.ArrayList<>();

        public void setItems(java.util.List<RoomInfo.QuestionItem> list) {
            this.items.clear();
            if (list != null) this.items.addAll(list);
            notifyDataSetChanged();
        }

        public void add(RoomInfo.QuestionItem item) {
            items.add(item);
            notifyItemInserted(items.size() - 1);
        }

        /** Update the last item's answer and answererName (used when server confirms the answer). */
        public void updateLastAnswer(String answer, String answererName) {
            if (items.isEmpty()) return;
            int last = items.size() - 1;
            items.get(last).answer = answer;
            items.get(last).answererName = answererName;
            notifyItemChanged(last);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            QuestionBubbleView view = new QuestionBubbleView(parent.getContext());
            ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            view.setLayoutParams(lp);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bubbleView.setQuestion(items.get(position));
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            QuestionBubbleView bubbleView;
            ViewHolder(QuestionBubbleView view) {
                super(view);
                this.bubbleView = view;
            }
        }
    }
}
