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

import glab.guesscard.GuessCardApp;
import glab.guesscard.ModernFButton;
import glab.guesscard.R;
import glab.guesscard.activities.GameActivity;
import glab.guesscard.activities.WinnerActivity;
import glab.guesscard.audio.GameAudio;
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
 * - Online mode: YES/NO answers, voice-to-text question asking, position switching,
 *   card reveal on wrong/correct, winner announcement, chat disabled.
 * - Offline mode: AI-simulated smart answers. No realtime chat, no online features.
 */
public class GameFragment extends Fragment implements GameSocketListener, GuessTheCardEngine.EngineListener {

    private static final int REQ_RECORD_AUDIO = 200;

    private GameSocketClient socket;
    private PreferenceManager preferences;
    private FirebaseManager firebaseManager;
    private GameAudio audio;
    private GuessTheCardEngine offlineEngine;
    private VoiceRecognitionHelper voiceHelper;

    private TextView tvRoundIndicator;
    private TextView tvQuestionsLeft;
    private TextView tvCurrentPlayer;
    private TextView tvCardReveal;
    private TimerView timerView;
    private LinearLayout gamePlayerContainer;
    private GameCardView gameCardView;
    private RecyclerView rvQuestionHistory;
    private View answerButtonContainer;
    private ModernFButton btnGuess;
    private ModernFButton btnVoiceQuestion;
    private ModernFButton btnPass;

    private boolean isOfflineMode = false;
    private boolean isGuesser = true;
    private boolean isSelfMuted = false;
    private boolean isOthersMuted = false;
    private QuestionHistoryAdapter historyAdapter;
    private String currentRoomId;
    private String currentMode = "ANIMALS";

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
        audio = appContainer.getAudio();

        // Ensure audio is enabled
        if (audio != null) {
            audio.setSoundEnabled(true);
        }

        tvRoundIndicator = view.findViewById(R.id.tvRoundIndicator);
        tvQuestionsLeft = view.findViewById(R.id.tvQuestionsLeft);
        tvCurrentPlayer = view.findViewById(R.id.tvCurrentPlayer);
        tvCardReveal = view.findViewById(R.id.tvCardReveal);
        timerView = view.findViewById(R.id.timerView);
        gamePlayerContainer = view.findViewById(R.id.gamePlayerContainer);
        gameCardView = view.findViewById(R.id.gameCardView);
        rvQuestionHistory = view.findViewById(R.id.rvQuestionHistory);
        answerButtonContainer = view.findViewById(R.id.answerButtonContainer);
        btnGuess = view.findViewById(R.id.btnGuess);
        btnVoiceQuestion = view.findViewById(R.id.btnVoiceQuestion);
        btnPass = view.findViewById(R.id.btnPassTurn);

        // Hide card reveal initially
        if (tvCardReveal != null) tvCardReveal.setVisibility(View.GONE);

        historyAdapter = new QuestionHistoryAdapter();
        if (rvQuestionHistory != null) {
            rvQuestionHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
            rvQuestionHistory.setAdapter(historyAdapter);
        }

        bindActionButtons(view);
        setupVoiceButton();
        setupMuteControls(view);
        initGameMode();
    }

    private void setupMuteControls(View view) {
        ModernFButton btnMuteSelf = view.findViewById(R.id.btnMuteSelf);
        ModernFButton btnMuteOthers = view.findViewById(R.id.btnMuteOthers);

        if (btnMuteSelf != null) {
            btnMuteSelf.setOnClickListener(v -> {
                isSelfMuted = !isSelfMuted;
                btnMuteSelf.setText(isSelfMuted ? "🔇 Mic Off" : "🎤 Mic On");
                Toast.makeText(requireContext(), isSelfMuted ? "Microphone muted" : "Microphone unmuted 🎤", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnMuteOthers != null) {
            btnMuteOthers.setOnClickListener(v -> {
                isOthersMuted = !isOthersMuted;
                btnMuteOthers.setText(isOthersMuted ? "🔇 Muted" : "🔊 Audio On");
                Toast.makeText(requireContext(), isOthersMuted ? "Incoming audio muted 🔇" : "Incoming audio enabled 🔊", Toast.LENGTH_SHORT).show();
            });
        }

        // Hide mute controls in offline mode
        if (isOfflineMode) {
            if (btnMuteSelf != null) btnMuteSelf.setVisibility(View.GONE);
            if (btnMuteOthers != null) btnMuteOthers.setVisibility(View.GONE);
        }
    }

    private void initGameMode() {
        Intent intent = getActivity() != null ? getActivity().getIntent() : null;
        String modeStr = intent != null ? intent.getStringExtra(GameActivity.EXTRA_MODE) : null;
        currentRoomId = intent != null ? intent.getStringExtra(GameActivity.EXTRA_ROOM_ID) : null;

        GameMode selectedMode = GameMode.ANIMALS;
        if (modeStr != null) {
            try { selectedMode = GameMode.valueOf(modeStr); } catch (Exception ignored) {}
        }
        currentMode = selectedMode.name();

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
            if (btnPass != null) btnPass.setVisibility(View.GONE);
            // Hide chat-related controls in offline
            View chatContainer = getView() != null ? getView().findViewById(R.id.chatContainer) : null;
            if (chatContainer != null) chatContainer.setVisibility(View.GONE);
        } else {
            isOfflineMode = false;
            socket.setListener(this);
            renderQuickQuestionChips(selectedMode);
            if (btnVoiceQuestion != null) btnVoiceQuestion.setVisibility(View.VISIBLE);
            if (btnPass != null) btnPass.setVisibility(View.VISIBLE);
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
        if (isSelfMuted) {
            Toast.makeText(requireContext(), "Unmute your mic first!", Toast.LENGTH_SHORT).show();
            return;
        }
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

        if (btnPass != null) {
            btnPass.setOnClickListener(v -> {
                if (!isOfflineMode && socket != null) {
                    socket.passTurn();
                    Toast.makeText(requireContext(), "Turn passed", Toast.LENGTH_SHORT).show();
                }
            });
        }

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
        }
    }

    private void updateRoleControls() {
        if (answerButtonContainer != null && btnGuess != null) {
            if (isGuesser) {
                answerButtonContainer.setVisibility(View.GONE);
                btnGuess.setVisibility(View.VISIBLE);
                if (btnVoiceQuestion != null && !isOfflineMode)
                    btnVoiceQuestion.setVisibility(View.VISIBLE);
                if (btnPass != null && !isOfflineMode)
                    btnPass.setVisibility(View.VISIBLE);
            } else {
                answerButtonContainer.setVisibility(View.VISIBLE);
                btnGuess.setVisibility(View.GONE);
                if (btnVoiceQuestion != null) btnVoiceQuestion.setVisibility(View.GONE);
                if (btnPass != null) btnPass.setVisibility(View.GONE);
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
            if (gameCardView != null) {
                gameCardView.setCardData(room.card, room.mode, isGuesser);
                // Hide card reveal when new round starts
                gameCardView.setVisibility(View.VISIBLE);
            }
            if (tvCardReveal != null) tvCardReveal.setVisibility(View.GONE);

            updateRoleControls();
            renderPlayerAvatars(room);

            if (tvCurrentPlayer != null) {
                if (isGuesser) {
                    tvCurrentPlayer.setText("Your turn to ask questions and guess!");
                } else {
                    tvCurrentPlayer.setText("Answer the questions — YES or NO");
                }
            }

            if (room.questionHistory != null) {
                historyAdapter.setItems(room.questionHistory);
            }
        });
    }

    @Override
    public void onTurnStarted(String nextTurnPlayerId, int currentRound, boolean switchedPositions) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (switchedPositions) {
                Toast.makeText(requireContext(), "Position switched! New turn begins...", Toast.LENGTH_SHORT).show();
            }
            // Play turn sound
            if (audio != null) audio.playSound(GameAudio.Sound.CARD_FLIP);
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
            if (audio != null) audio.playSound(GameAudio.Sound.QUESTION_YES);
        });
    }

    @Override
    public void onAnswerGiven(String question, String answer, String answererName) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            historyAdapter.updateLastAnswer(answer, answererName);
            if (audio != null) {
                audio.playSound(answer.contains("YES") ? GameAudio.Sound.QUESTION_YES : GameAudio.Sound.QUESTION_NO);
            }
        });
    }

    @Override
    public void onGuessResult(String guessedBy, String guessedByName, String guess,
                               boolean isCorrect, int scoreAwarded, String cardAnswer, String cardCategory) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            // Always reveal the card
            if (tvCardReveal != null && cardAnswer != null && !cardAnswer.isEmpty()) {
                tvCardReveal.setText("The card was: " + cardAnswer + 
                    (cardCategory != null && !cardCategory.isEmpty() ? " (" + cardCategory + ")" : ""));
                tvCardReveal.setVisibility(View.VISIBLE);
                tvCardReveal.setTextColor(isCorrect ? 
                    android.graphics.Color.parseColor("#10B981") : 
                    android.graphics.Color.parseColor("#EF4444"));
            }

            if (isCorrect) {
                int pts = scoreAwarded > 0 ? scoreAwarded : 100;
                gameCardView.animateCorrectGuess(() -> {
                    Toast.makeText(requireContext(), 
                        "🎉 " + guessedByName + " guessed \"" + cardAnswer + "\" correctly! +" + pts + " pts\nSwitching positions...", 
                        Toast.LENGTH_LONG).show();
                });
                if (audio != null) audio.playSound(GameAudio.Sound.CORRECT);

                // Save history to Firebase
                if (firebaseManager != null && preferences != null) {
                    firebaseManager.saveGameHistory(preferences.getUserId(), currentMode, pts, true, cardAnswer);
                }
            } else {
                gameCardView.animateWrongGuess();
                Toast.makeText(requireContext(), 
                    "❌ " + guessedByName + " guessed \"" + guess + "\" — wrong! The card was: " + cardAnswer, 
                    Toast.LENGTH_LONG).show();
                if (audio != null) audio.playSound(GameAudio.Sound.WRONG);
            }
        });
    }

    @Override
    public void onGuessResult(boolean isCorrect, String guess, int scoreAwarded, int totalScore) {
        // Offline engine callback
        onGuessResult("You", "You", guess, isCorrect, scoreAwarded, 
            offlineEngine != null && offlineEngine.getCurrentCard() != null ? offlineEngine.getCurrentCard().word : "",
            offlineEngine != null && offlineEngine.getCurrentCard() != null ? offlineEngine.getCurrentCard().category : "");
    }

    @Override
    public void onGameEnd(MatchResult result) {
        if (!isAdded()) return;

        // Play victory/end sound
        if (audio != null) {
            audio.playSound(GameAudio.Sound.VICTORY);
        }

        // Save match to Firebase history
        if (firebaseManager != null && preferences != null) {
            String myUid = preferences.getUserId();
            boolean iWon = result.winner != null && myUid != null && myUid.equals(result.winner.userId);
            int myScore = 0;
            if (result.standings != null) {
                for (MatchResult.StandingsEntry se : result.standings) {
                    if (myUid != null && myUid.equals(se.userId)) {
                        myScore = se.score;
                        break;
                    }
                }
            }
            result.score = myScore;
            result.won = iWon;
            firebaseManager.saveGameHistory(myUid, result.mode != null ? result.mode : currentMode, 
                myScore, iWon, "");
            // Submit score to leaderboard
            firebaseManager.submitScore(myUid, preferences.getUsername(), myScore, result.mode != null ? result.mode : currentMode);
        }

        requireActivity().runOnUiThread(() -> {
            Intent intent = new Intent(requireContext(), WinnerActivity.class);
            if (result.winner != null) {
                intent.putExtra("winnerName", result.winner.username);
                intent.putExtra("winnerId", result.winner.userId);
                intent.putExtra("winnerScore", result.winner.score);
            }
            intent.putExtra("finalScore", result.score);
            intent.putExtra("matchId", result.matchId);
            intent.putExtra("mode", result.mode != null ? result.mode : currentMode);
            // Pass standings as serializable
            if (result.standings != null) {
                ArrayList<String> names = new ArrayList<>();
                ArrayList<Integer> scores = new ArrayList<>();
                for (MatchResult.StandingsEntry se : result.standings) {
                    names.add(se.username + ":" + se.userId);
                    scores.add(se.score);
                }
                intent.putStringArrayListExtra("standingsNames", names);
                intent.putIntegerArrayListExtra("standingsScores", scores);
            }
            startActivity(intent);
            if (getActivity() != null) getActivity().finish();
        });
    }

    private void renderPlayerAvatars(RoomInfo room) {
        if (gamePlayerContainer == null || room == null || room.players == null) return;
        gamePlayerContainer.removeAllViews();

        for (RoomInfo.LobbyPlayer p : room.players) {
            PlayerAvatarView avatar = new PlayerAvatarView(requireContext());
            boolean isCurrentTurn = p.userId != null && p.userId.equals(room.currentTurnPlayerId);
            avatar.setPlayerData(p.username, p.score, 1, p.isReady, isCurrentTurn);
            if (p.userId != null && preferences != null && p.userId.equals(preferences.getUserId())) {
                avatar.setMuted(isSelfMuted);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            gamePlayerContainer.addView(avatar, lp);
        }
    }

    // ── OFFLINE ENGINE CALLBACKS ───────────────────────────────────────────

    @Override
    public void onCardChanged(Card card, boolean isGuesser) {
        if (!isAdded() || card == null) return;
        this.isGuesser = isGuesser;
        requireActivity().runOnUiThread(() -> {
            if (gameCardView != null) gameCardView.setCardData(card.word, card.category, isGuesser);
            if (tvCardReveal != null) tvCardReveal.setVisibility(View.GONE);
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
            if (rvQuestionHistory != null) {
                rvQuestionHistory.smoothScrollToPosition(Math.max(0, historyAdapter.getItemCount() - 1));
            }
        });
    }

    @Override
    public void onGameOver(int finalScore) {
        if (!isAdded()) return;
        if (audio != null) audio.playSound(GameAudio.Sound.VICTORY);

        // Save offline game to history
        if (firebaseManager != null && preferences != null) {
            Card currentCard = offlineEngine != null ? offlineEngine.getCurrentCard() : null;
            String cardWord = currentCard != null ? currentCard.word : "";
            firebaseManager.saveGameHistory(preferences.getUserId(), currentMode, finalScore, true, cardWord);
        }

        requireActivity().runOnUiThread(() -> {
            Intent intent = new Intent(requireContext(), WinnerActivity.class);
            intent.putExtra("winnerName", "You");
            intent.putExtra("finalScore", finalScore);
            startActivity(intent);
            if (getActivity() != null) getActivity().finish();
        });
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
