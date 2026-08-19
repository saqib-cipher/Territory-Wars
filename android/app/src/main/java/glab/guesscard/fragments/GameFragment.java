package glab.guesscard.fragments;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import glab.guesscard.GuessCardApp;
import glab.guesscard.ModernFButton;
import glab.guesscard.R;
import glab.guesscard.activities.GameActivity;
import glab.guesscard.activities.WinnerActivity;
import glab.guesscard.di.GameContainer;
import glab.guesscard.game.GuessTheCardEngine;
import glab.guesscard.views.CustomGuessDialog;
import glab.guesscard.firebase.FirebaseManager;
import glab.guesscard.models.Card;
import glab.guesscard.models.GameMode;
import glab.guesscard.models.MatchResult;
import glab.guesscard.models.RoomInfo;
import glab.guesscard.network.PreferenceManager;
import glab.guesscard.socket.GameSocketClient;
import glab.guesscard.socket.GameSocketListener;
import glab.guesscard.utils.AvatarManager;
import glab.guesscard.utils.HapticsHelper;
import glab.guesscard.utils.VoiceRecognitionHelper;
import glab.guesscard.views.GameCardView;
import glab.guesscard.views.QuestionBubbleView;
import glab.guesscard.views.TimerView;

/**
 * GameFragment: Full 20-Questions Real-Time Card Guessing Experience.
 *
 * ONLINE MODE:
 * - One player is designated the ANSWERER (holds secret card, answers YES/MAYBE/NO).
 * - Other players are the QUESTIONERS (ask questions, submit guesses).
 * - Real-time speakerphone audio routing for live party voice conversations.
 * - Custom player cards with live flickering speaking mic indicators.
 * - Player click popup menu for Mute, Add Friend, Report, and Host Kick.
 * - Answer buttons visible only when an unanswered question is pending.
 * - Automatic match pause overlay if any player switches apps or becomes inactive.
 */
public class GameFragment extends Fragment implements GameSocketListener, GuessTheCardEngine.EngineListener {

    private static final int REQ_RECORD_AUDIO = 200;

    private GameSocketClient socket;
    private PreferenceManager preferences;
    private FirebaseManager firebaseManager;
    private GuessTheCardEngine offlineEngine;
    private VoiceRecognitionHelper voiceHelper;
    private AudioManager audioManager;
    private glab.guesscard.audio.PartyVoiceCallManager partyVoiceCallManager;

    private TextView tvRoundIndicator;
    private TextView tvQuestionsLeft;
    private TextView tvCurrentPlayer;
    private TimerView timerView;
    private LinearLayout gamePlayerContainer;
    private GameCardView gameCardView;
    private RecyclerView rvQuestionHistory;
    private View answerButtonContainer;
    private TextView tvAnswererWaitingPrompt;
    private ModernFButton btnGuess;
    private ModernFButton btnVoiceQuestion;
    private ModernFButton btnAnswerYes;
    private ModernFButton btnAnswerMaybe;
    private ModernFButton btnAnswerNo;

    private View pauseOverlayContainer;
    private TextView tvPauseSubtitle;
    private ModernFButton btnHostKickInactive;

    private boolean isOfflineMode = false;
    private boolean isGuesser = true;
    private boolean isSelfMuted = false;
    private boolean isOthersMuted = false;
    private boolean isVoiceListening = false;
    private boolean hasActiveGameplayStarted = false;
    private boolean isMatchPaused = false;
    private boolean hasNavigatedToResults = false;

    private final Set<String> mutedPlayersSet = new HashSet<>();
    private final Set<String> speakingPlayersSet = new HashSet<>();
    private final Set<String> awayPlayersSet = new HashSet<>();

    private QuestionHistoryAdapter historyAdapter;
    private String currentRoomId;
    private String currentHostUid = "";
    private GameMode currentGameMode = GameMode.ANIMALS;

    private String currentSecretCard = "";
    private String currentAnswererUid = "";
    private int currentRoundNumber = 1;
    private int totalRoundsCount = 5;
    private int remainingQuestionsCount = 20;
    private List<RoomInfo.LobbyPlayer> roomPlayersList = new ArrayList<>();
    private ValueEventListener roomLiveListener;

    private CountDownTimer roundTimer;
    private long remainingTimerMillis = 60000L;

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

        // 🔊 Real-time Conversation Audio Route: Set to Speakerphone for party conversation
        setupSpeakerphoneAudio();

        tvRoundIndicator = view.findViewById(R.id.tvRoundIndicator);
        tvQuestionsLeft = view.findViewById(R.id.tvQuestionsLeft);
        tvCurrentPlayer = view.findViewById(R.id.tvCurrentPlayer);
        timerView = view.findViewById(R.id.timerView);
        gamePlayerContainer = view.findViewById(R.id.gamePlayerContainer);
        gameCardView = view.findViewById(R.id.gameCardView);
        rvQuestionHistory = view.findViewById(R.id.rvQuestionHistory);
        answerButtonContainer = view.findViewById(R.id.answerButtonContainer);
        tvAnswererWaitingPrompt = view.findViewById(R.id.tvAnswererWaitingPrompt);
        btnGuess = view.findViewById(R.id.btnGuess);
        btnVoiceQuestion = view.findViewById(R.id.btnVoiceQuestion);

        pauseOverlayContainer = view.findViewById(R.id.pauseOverlayContainer);
        tvPauseSubtitle = view.findViewById(R.id.tvPauseSubtitle);
        btnHostKickInactive = view.findViewById(R.id.btnHostKickInactive);

        historyAdapter = new QuestionHistoryAdapter();
        if (rvQuestionHistory != null) {
            rvQuestionHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
            rvQuestionHistory.setAdapter(historyAdapter);
        }

        bindActionButtons(view);
        setupVoiceButton();
        setupMuteControls(view);
        setupPauseOverlayControls();
        initGameMode();
    }

    private void setupSpeakerphoneAudio() {
        try {
            audioManager = (AudioManager) requireContext().getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                audioManager.setSpeakerphoneOn(true);
            }
        } catch (Exception ignored) {}
    }

    private void setupMuteControls(View view) {
        View voiceContainer = view.findViewById(R.id.voiceControlsContainer);
        if (voiceContainer != null) {
            voiceContainer.setVisibility(isOfflineMode ? View.GONE : View.VISIBLE);
        }

        ModernFButton btnMuteSelf = view.findViewById(R.id.btnMuteSelf);
        ModernFButton btnMuteAll = view.findViewById(R.id.btnMuteAll);

        if (btnMuteSelf != null) {
            btnMuteSelf.setOnClickListener(v -> {
                isSelfMuted = !isSelfMuted;
                btnMuteSelf.setText(isSelfMuted ? "Mic Off" : "Mic On");
                btnMuteSelf.setButtonColor(isSelfMuted ? Color.parseColor("#EF4444") : Color.parseColor("#10B981"));
                if (partyVoiceCallManager != null) partyVoiceCallManager.setSelfMuted(isSelfMuted);
                renderPlayerAvatarsFromList(roomPlayersList, currentAnswererUid);
                Toast.makeText(requireContext(), isSelfMuted ? "Microphone muted" : "Microphone unmuted", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnMuteAll != null) {
            btnMuteAll.setOnClickListener(v -> {
                isOthersMuted = !isOthersMuted;
                btnMuteAll.setText(isOthersMuted ? "Audio Muted" : "Mute Others");
                btnMuteAll.setButtonColor(isOthersMuted ? Color.parseColor("#EF4444") : Color.parseColor("#334155"));
                if (partyVoiceCallManager != null) partyVoiceCallManager.setOthersMuted(isOthersMuted);
                Toast.makeText(requireContext(), isOthersMuted ? "All incoming audio muted" : "Incoming audio unmuted", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void setupPauseOverlayControls() {
        if (btnHostKickInactive != null) {
            btnHostKickInactive.setButtonColor(Color.parseColor("#EF4444"));
            btnHostKickInactive.setShadowColor(Color.parseColor("#991B1B"));
            btnHostKickInactive.setOnClickListener(v -> {
                // Kick the away player to resume
                if (!awayPlayersSet.isEmpty()) {
                    String awayUid = awayPlayersSet.iterator().next();
                    kickInactivePlayer(awayUid);
                } else if (currentRoomId != null) {
                    firebaseManager.getRoomRef(currentRoomId).child("isPaused").setValue(false);
                }
            });
        }
    }

    private void initGameMode() {
        Intent intent = getActivity() != null ? getActivity().getIntent() : null;
        String modeStr = intent != null ? intent.getStringExtra(GameActivity.EXTRA_MODE) : null;
        currentRoomId = intent != null ? intent.getStringExtra(GameActivity.EXTRA_ROOM_ID) : null;
        String initialAnswererUid = intent != null ? intent.getStringExtra(GameActivity.EXTRA_ANSWERER_UID) : null;

        if (modeStr != null) {
            try { currentGameMode = GameMode.valueOf(modeStr); } catch (Exception ignored) {}
        }

        View voiceContainer = getView() != null ? getView().findViewById(R.id.voiceControlsContainer) : null;

        if (currentRoomId == null || currentRoomId.isEmpty() || "offline".equalsIgnoreCase(currentRoomId)) {
            // ── OFFLINE PRACTICE MODE ──
            isOfflineMode = true;
            if (voiceContainer != null) voiceContainer.setVisibility(View.GONE);
            if (btnVoiceQuestion != null) btnVoiceQuestion.setVisibility(View.GONE);
            offlineEngine = new GuessTheCardEngine();
            offlineEngine.setListener(this);
            offlineEngine.startOfflinePractice(currentGameMode);
        } else {
            // ── ONLINE MULTIPLAYER MODE ──
            isOfflineMode = false;
            String myUid = preferences != null ? preferences.getUserId() : "";
            if (initialAnswererUid != null && !initialAnswererUid.isEmpty()) {
                currentAnswererUid = initialAnswererUid;
                isGuesser = !(myUid != null && myUid.equals(initialAnswererUid));
            }

            if (socket != null) {
                socket.setListener(this);
                // Rejoin the room on the socket so server maps socket.data.roomId.
                // This is critical: without it, voice_audio_chunk has no roomId to broadcast to.
                socket.rejoinRoom(currentRoomId);
            }
            renderQuickQuestionChips(currentGameMode);
            if (voiceContainer != null) voiceContainer.setVisibility(View.VISIBLE);

            // Start Realtime Group Voice Chat.
            // emitVoiceJoin is deferred internally until socket connect completes.
            try {
                if (partyVoiceCallManager != null) partyVoiceCallManager.release();
                partyVoiceCallManager = new glab.guesscard.audio.PartyVoiceCallManager(requireContext(), firebaseManager, socket, currentRoomId, myUid);
                partyVoiceCallManager.setVoiceActivityListener((userId, isSpeaking) -> {
                    if (!isAdded() || getActivity() == null) return;
                    requireActivity().runOnUiThread(() -> {
                        if (isSpeaking) speakingPlayersSet.add(userId);
                        else speakingPlayersSet.remove(userId);
                        updateSpeakingMicIndicator(userId, isSpeaking);
                    });
                });
                partyVoiceCallManager.startVoiceChat();
            } catch (Exception ignored) {}

            updateRoleControls();
            startCountdownTimer(60000L);
            listenToOnlineRoom(currentRoomId);
        }
    }

    private void startCountdownTimer(long millis) {
        if (roundTimer != null) roundTimer.cancel();
        remainingTimerMillis = millis;

        roundTimer = new CountDownTimer(millis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingTimerMillis = millisUntilFinished;
                if (timerView != null && isAdded()) {
                    int sec = (int) (millisUntilFinished / 1000);
                    timerView.setTime(sec, 60);
                }
            }

            @Override
            public void onFinish() {
                if (timerView != null && isAdded()) {
                    timerView.setTime(0, 60);
                }
                if (!isGuesser && currentRoomId != null && hasActiveGameplayStarted) {
                    advanceToNextRound();
                }
            }
        };
        if (!isMatchPaused) {
            roundTimer.start();
        }
    }

    private void pauseTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
        }
    }

    private void resumeTimer() {
        if (remainingTimerMillis > 1000) {
            startCountdownTimer(remainingTimerMillis);
        }
    }

    /**
     * Real-time Firebase Room listener for Online Multiplayer.
     */
    private void listenToOnlineRoom(String roomId) {
        if (roomId == null) return;
        roomLiveListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded() || getActivity() == null) return;

                String status = snapshot.child("status").getValue(String.class);

                if ("PLAYING".equalsIgnoreCase(status) || "IN_PROGRESS".equalsIgnoreCase(status)) {
                    hasActiveGameplayStarted = true;
                }

                // FINISHED = game completed normally → show WinnerActivity
                if ("FINISHED".equalsIgnoreCase(status)) {
                    if (!hasNavigatedToResults) {
                        hasNavigatedToResults = true;
                        showGameFinished();
                    }
                    return;
                }

                // CLOSED = room closed by host or player quit → show abandoned dialog
                if ("CLOSED".equalsIgnoreCase(status) || "ABANDONED".equalsIgnoreCase(status)) {
                    if (hasActiveGameplayStarted && !hasNavigatedToResults && !isOfflineMode) {
                        hasNavigatedToResults = true;
                        requireActivity().runOnUiThread(() -> {
                            if (!isAdded() || getActivity() == null) return;
                            String myUid = preferences != null ? preferences.getUserId() : "";
                            boolean isHost = myUid != null && myUid.equals(currentHostUid);
                            showMatchAbandonedDialog(isHost);
                        });
                    }
                    return;
                }

                String hostUid = snapshot.child("hostUid").getValue(String.class);
                if (hostUid != null) currentHostUid = hostUid;

                // Match Pause state
                Boolean paused = snapshot.child("isPaused").getValue(Boolean.class);
                String pausedName = snapshot.child("pausedPlayerName").getValue(String.class);
                isMatchPaused = Boolean.TRUE.equals(paused);

                String card = snapshot.child("secretCard").getValue(String.class);
                String ansUid = snapshot.child("answererUid").getValue(String.class);
                Long rNum = snapshot.child("currentRound").getValue(Long.class);
                Long totR = snapshot.child("totalRounds").getValue(Long.class);
                Long qRem = snapshot.child("questionsRemaining").getValue(Long.class);

                if (card != null && !card.isEmpty()) currentSecretCard = card;
                if (ansUid != null && !ansUid.isEmpty()) currentAnswererUid = ansUid;
                if (rNum != null) currentRoundNumber = rNum.intValue();
                if (totR != null) totalRoundsCount = totR.intValue();
                if (qRem != null) remainingQuestionsCount = qRem.intValue();

                // Read all players in room
                List<RoomInfo.LobbyPlayer> pList = new ArrayList<>();
                awayPlayersSet.clear();
                if (snapshot.child("players").exists()) {
                    for (DataSnapshot pSnap : snapshot.child("players").getChildren()) {
                        RoomInfo.LobbyPlayer lp = new RoomInfo.LobbyPlayer();
                        lp.userId = pSnap.child("uid").getValue(String.class);
                        if (lp.userId == null) lp.userId = pSnap.getKey();
                        lp.username = pSnap.child("displayName").getValue(String.class);
                        lp.avatarFileName = pSnap.child("avatarFileName").getValue(String.class);
                        Long scoreVal = pSnap.child("score").getValue(Long.class);
                        lp.score = scoreVal != null ? scoreVal.intValue() : 0;
                        if (lp.userId != null && lp.userId.equals(currentAnswererUid)) {
                            lp.isHost = true;
                        }
                        Boolean away = pSnap.child("isAway").getValue(Boolean.class);
                        if (Boolean.TRUE.equals(away) && lp.userId != null) {
                            awayPlayersSet.add(lp.userId);
                        }
                        pList.add(lp);
                    }
                }
                roomPlayersList = pList;

                // Read Q&A History
                List<RoomInfo.QuestionItem> historyList = new ArrayList<>();
                boolean hasUnansweredQuestion = false;
                if (snapshot.child("qaHistory").exists()) {
                    for (DataSnapshot qSnap : snapshot.child("qaHistory").getChildren()) {
                        RoomInfo.QuestionItem item = new RoomInfo.QuestionItem();
                        item.question = qSnap.child("question").getValue(String.class);
                        item.answer = qSnap.child("answer").getValue(String.class);
                        item.askerName = qSnap.child("askerName").getValue(String.class);
                        item.answererName = qSnap.child("answererName").getValue(String.class);
                        historyList.add(item);

                        if (item.answer == null || item.answer.contains("...") || item.answer.contains("Waiting")) {
                            hasUnansweredQuestion = true;
                        }
                    }
                }

                final boolean hasPendingQ = hasUnansweredQuestion;

                requireActivity().runOnUiThread(() -> {
                    String myUid = preferences != null ? preferences.getUserId() : "";
                    boolean isAnswerer = myUid != null && myUid.equals(currentAnswererUid);
                    isGuesser = !isAnswerer;

                    if (tvRoundIndicator != null) {
                        tvRoundIndicator.setText("ROUND " + currentRoundNumber + " / " + totalRoundsCount);
                    }
                    if (tvQuestionsLeft != null) {
                        tvQuestionsLeft.setText(String.valueOf(remainingQuestionsCount));
                    }

                    // Card Display
                    if (gameCardView != null && currentSecretCard != null && !currentSecretCard.isEmpty()) {
                        gameCardView.setCardData(currentSecretCard, currentGameMode.name(), isGuesser);
                    }

                    // Role Controls: Hide answer buttons if there is no pending question
                    updateRoleControls();
                    if (!isGuesser) {
                        if (hasPendingQ) {
                            if (answerButtonContainer != null) answerButtonContainer.setVisibility(View.VISIBLE);
                            if (tvAnswererWaitingPrompt != null) tvAnswererWaitingPrompt.setVisibility(View.GONE);
                        } else {
                            if (answerButtonContainer != null) answerButtonContainer.setVisibility(View.GONE);
                            if (tvAnswererWaitingPrompt != null) tvAnswererWaitingPrompt.setVisibility(View.VISIBLE);
                        }
                    }

                    renderPlayerAvatarsFromList(roomPlayersList, currentAnswererUid);

                    // Pause Overlay handling
                    if (pauseOverlayContainer != null) {
                        if (isMatchPaused) {
                            pauseOverlayContainer.setVisibility(View.VISIBLE);
                            if (tvPauseSubtitle != null) {
                                tvPauseSubtitle.setText((pausedName != null ? pausedName : "A player") + " is away / switched app");
                            }
                            boolean isHost = myUid.equals(currentHostUid) || (roomPlayersList.size() > 0 && myUid.equals(roomPlayersList.get(0).userId));
                            if (btnHostKickInactive != null) {
                                btnHostKickInactive.setVisibility(isHost ? View.VISIBLE : View.GONE);
                            }
                            pauseTimer();
                        } else {
                            pauseOverlayContainer.setVisibility(View.GONE);
                            resumeTimer();
                        }
                    }

                    if (tvCurrentPlayer != null) {
                        if (isAnswerer) {
                            tvCurrentPlayer.setText("👑 You are the Answerer! Respond to questions.");
                            tvCurrentPlayer.setTextColor(Color.parseColor("#F59E0B"));
                        } else {
                            tvCurrentPlayer.setText("🎯 You are a Guesser! Ask questions to discover the secret card.");
                            tvCurrentPlayer.setTextColor(Color.parseColor("#38BDF8"));
                        }
                    }

                    historyAdapter.setItems(historyList);
                    if (rvQuestionHistory != null && !historyList.isEmpty()) {
                        rvQuestionHistory.smoothScrollToPosition(historyList.size() - 1);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        firebaseManager.getRoomRef(roomId).addValueEventListener(roomLiveListener);
    }

    /**
     * Renders custom player cards in gamePlayerContainer with real drawables and speaking indicators.
     */
    private void renderPlayerAvatarsFromList(List<RoomInfo.LobbyPlayer> players, String answererUid) {
        if (gamePlayerContainer == null || players == null || !isAdded()) return;
        gamePlayerContainer.removeAllViews();
        String myUid = preferences != null ? preferences.getUserId() : "";

        for (RoomInfo.LobbyPlayer p : players) {
            View card = LayoutInflater.from(requireContext()).inflate(R.layout.item_game_player, gamePlayerContainer, false);
            ImageView ivAvatar = card.findViewById(R.id.ivPlayerAvatar);
            ImageView ivCrown = card.findViewById(R.id.ivCrownBadge);
            ImageView ivSpeaking = card.findViewById(R.id.ivSpeakingMic);
            ImageView ivMute = card.findViewById(R.id.ivMuteBadge);
            View ring = card.findViewById(R.id.turnHighlightRing);
            TextView tvName = card.findViewById(R.id.tvPlayerName);
            TextView tvScore = card.findViewById(R.id.tvPlayerScore);

            tvName.setText(p.username != null ? p.username : "Player");
            tvScore.setText(p.score + " pts");
            AvatarManager.getInstance().loadAvatarIntoImageView(requireContext(), ivAvatar, p.avatarFileName);

            boolean isAnswerer = p.userId != null && p.userId.equals(answererUid);
            ivCrown.setVisibility(isAnswerer ? View.VISIBLE : View.GONE);
            ring.setVisibility(isAnswerer ? View.VISIBLE : View.GONE);

            // Mute status
            boolean isMuted = mutedPlayersSet.contains(p.userId) || (p.userId != null && p.userId.equals(myUid) && isSelfMuted);
            ivMute.setVisibility(isMuted ? View.VISIBLE : View.GONE);

            // Speaking indicator (Flickering mic animation)
            boolean isSpeaking = speakingPlayersSet.contains(p.userId) || (p.userId != null && p.userId.equals(myUid) && isVoiceListening);
            if (isSpeaking && !isMuted) {
                ivSpeaking.setVisibility(View.VISIBLE);
                startMicFlicker(ivSpeaking);
            } else {
                ivSpeaking.setVisibility(View.GONE);
                stopMicFlicker(ivSpeaking);
            }

            // Click opens custom interaction popup without navigating to profile
            card.setTag(p.userId);
            card.setOnClickListener(v -> showPlayerActionDialog(p));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            gamePlayerContainer.addView(card, lp);
        }
    }

    private void updateSpeakingMicIndicator(String userId, boolean isSpeaking) {
        if (gamePlayerContainer == null || userId == null || !isAdded()) return;
        View card = gamePlayerContainer.findViewWithTag(userId);
        if (card != null) {
            ImageView ivSpeaking = card.findViewById(R.id.ivSpeakingMic);
            ImageView ivMute = card.findViewById(R.id.ivMuteBadge);
            String myUid = preferences != null ? preferences.getUserId() : "";
            boolean isMuted = mutedPlayersSet.contains(userId) || (userId.equals(myUid) && isSelfMuted);
            if (ivSpeaking != null) {
                if (isSpeaking && !isMuted) {
                    ivSpeaking.setVisibility(View.VISIBLE);
                    startMicFlicker(ivSpeaking);
                } else {
                    ivSpeaking.setVisibility(View.GONE);
                    stopMicFlicker(ivSpeaking);
                }
            }
        }
    }

    private void startMicFlicker(View micView) {
        if (micView == null) return;
        ObjectAnimator flicker = (ObjectAnimator) micView.getTag();
        if (flicker == null) {
            flicker = ObjectAnimator.ofFloat(micView, View.ALPHA, 1.0f, 0.2f, 1.0f);
            flicker.setDuration(450);
            flicker.setRepeatCount(ValueAnimator.INFINITE);
            micView.setTag(flicker);
        }
        if (!flicker.isRunning()) flicker.start();
    }

    private void stopMicFlicker(View micView) {
        if (micView == null) return;
        ObjectAnimator flicker = (ObjectAnimator) micView.getTag();
        if (flicker != null && flicker.isRunning()) {
            flicker.cancel();
            micView.setAlpha(1.0f);
        }
    }

    /**
     * Shows a popup dialog for player interaction: Mute, Add Friend, Report, or Host Kick.
     */
    private void showPlayerActionDialog(RoomInfo.LobbyPlayer target) {
        if (target == null || target.userId == null || !isAdded()) return;
        String myUid = preferences != null ? preferences.getUserId() : "";
        boolean isSelf = target.userId.equals(myUid);

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_player_action, null);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        ImageView ivAvatar = dialogView.findViewById(R.id.ivDialogAvatar);
        ImageView ivCrown = dialogView.findViewById(R.id.ivDialogCrown);
        TextView tvName = dialogView.findViewById(R.id.tvDialogUsername);
        TextView tvScore = dialogView.findViewById(R.id.tvDialogScore);
        ModernFButton btnMute = dialogView.findViewById(R.id.btnDialogMute);
        ModernFButton btnAddFriend = dialogView.findViewById(R.id.btnDialogAddFriend);
        ModernFButton btnReport = dialogView.findViewById(R.id.btnDialogReport);
        ModernFButton btnKick = dialogView.findViewById(R.id.btnDialogKick);
        ModernFButton btnClose = dialogView.findViewById(R.id.btnDialogClose);

        tvName.setText(target.username != null ? target.username : "Player");
        tvScore.setText("Score: " + target.score + " pts");
        AvatarManager.getInstance().loadAvatarIntoImageView(requireContext(), ivAvatar, target.avatarFileName);

        boolean isTargetAnswerer = target.userId.equals(currentAnswererUid);
        ivCrown.setVisibility(isTargetAnswerer ? View.VISIBLE : View.GONE);

        if (isSelf) {
            btnMute.setText(isSelfMuted ? "Unmute My Mic" : "Mute My Mic");
            btnMute.setButtonColor(isSelfMuted ? Color.parseColor("#10B981") : Color.parseColor("#EF4444"));
            btnMute.setOnClickListener(v -> {
                isSelfMuted = !isSelfMuted;
                if (partyVoiceCallManager != null) partyVoiceCallManager.setSelfMuted(isSelfMuted);
                renderPlayerAvatarsFromList(roomPlayersList, currentAnswererUid);
                dialog.dismiss();
            });
            btnAddFriend.setVisibility(View.GONE);
            btnReport.setVisibility(View.GONE);
            btnKick.setVisibility(View.GONE);
        } else {
            boolean isTargetMuted = mutedPlayersSet.contains(target.userId);
            btnMute.setText(isTargetMuted ? "Unmute Player Audio" : "Mute Player Audio");
            btnMute.setButtonColor(isTargetMuted ? Color.parseColor("#10B981") : Color.parseColor("#EF4444"));
            btnMute.setOnClickListener(v -> {
                if (isTargetMuted) {
                    mutedPlayersSet.remove(target.userId);
                    if (partyVoiceCallManager != null) partyVoiceCallManager.setPlayerMuted(target.userId, false);
                } else {
                    mutedPlayersSet.add(target.userId);
                    if (partyVoiceCallManager != null) partyVoiceCallManager.setPlayerMuted(target.userId, true);
                }
                renderPlayerAvatarsFromList(roomPlayersList, currentAnswererUid);
                Toast.makeText(requireContext(), (isTargetMuted ? "Unmuted " : "Muted ") + target.username, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });

            // Friendship check
            firebaseManager.checkFriendshipStatus(myUid, target.userId, status -> {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (status == FirebaseManager.FriendshipStatus.FRIENDS) {
                        btnAddFriend.setText("Friends ✓");
                        btnAddFriend.setEnabled(false);
                        btnAddFriend.setButtonColor(Color.parseColor("#1E293B"));
                        btnAddFriend.setTextColor(Color.parseColor("#38BDF8"));
                    } else if (status == FirebaseManager.FriendshipStatus.REQUEST_SENT) {
                        btnAddFriend.setText("Request Sent");
                        btnAddFriend.setEnabled(false);
                        btnAddFriend.setButtonColor(Color.parseColor("#1E293B"));
                        btnAddFriend.setTextColor(Color.parseColor("#94A3B8"));
                    } else {
                        btnAddFriend.setText("+ Add Friend");
                        btnAddFriend.setEnabled(true);
                        btnAddFriend.setButtonColor(Color.parseColor("#2563EB"));
                        btnAddFriend.setOnClickListener(v -> {
                            firebaseManager.sendFriendRequest(myUid, target.userId);
                            btnAddFriend.setText("Request Sent");
                            btnAddFriend.setEnabled(false);
                            Toast.makeText(requireContext(), "Friend request sent to " + target.username, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            });

            btnReport.setButtonColor(Color.parseColor("#334155"));
            btnReport.setOnClickListener(v -> {
                dialog.dismiss();
                showReportDialog(target);
            });

            // Host Kick button for inactive player
            boolean isHost = myUid.equals(currentHostUid) || (roomPlayersList.size() > 0 && myUid.equals(roomPlayersList.get(0).userId));
            if (isHost && awayPlayersSet.contains(target.userId)) {
                btnKick.setVisibility(View.VISIBLE);
                btnKick.setButtonColor(Color.parseColor("#DC2626"));
                btnKick.setOnClickListener(v -> {
                    kickInactivePlayer(target.userId);
                    dialog.dismiss();
                });
            } else {
                btnKick.setVisibility(View.GONE);
            }
        }

        btnClose.setButtonColor(Color.parseColor("#1E293B"));
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showReportDialog(RoomInfo.LobbyPlayer target) {
        String[] reasons = new String[]{"Inappropriate Voice/Text", "AFK / Inactive", "Spamming", "Cheating"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Report " + (target.username != null ? target.username : "Player"))
                .setItems(reasons, (d, which) -> {
                    Toast.makeText(requireContext(), "Report submitted. Thank you for keeping the game fair!", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void kickInactivePlayer(String targetUid) {
        if (currentRoomId == null || targetUid == null) return;
        firebaseManager.getRoomRef(currentRoomId).child("players").child(targetUid).removeValue();
        awayPlayersSet.remove(targetUid);
        if (awayPlayersSet.isEmpty()) {
            firebaseManager.getRoomRef(currentRoomId).child("isPaused").setValue(false);
        }
        Toast.makeText(requireContext(), "Player removed from match", Toast.LENGTH_SHORT).show();
    }

    // ── VOICE RECOGNITION ──────────────────────────────────────────────────

    private void setupVoiceButton() {
        if (btnVoiceQuestion == null) return;
        btnVoiceQuestion.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(),
                        new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
                return;
            }
            if (voiceHelper != null && voiceHelper.isListening()) {
                voiceHelper.stopListening();
                isVoiceListening = false;
                if (partyVoiceCallManager != null) partyVoiceCallManager.resumeRecording();
                if (btnVoiceQuestion != null) {
                    btnVoiceQuestion.setText("🎤");
                    btnVoiceQuestion.setButtonColor(Color.parseColor("#3B82F6"));
                }
                renderPlayerAvatarsFromList(roomPlayersList, currentAnswererUid);
                Toast.makeText(requireContext(), "Voice input stopped", Toast.LENGTH_SHORT).show();
            } else {
                startVoiceRecognition();
            }
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
                    isVoiceListening = false;
                    if (partyVoiceCallManager != null) partyVoiceCallManager.resumeRecording();
                    if (btnVoiceQuestion != null) {
                        btnVoiceQuestion.setText("🎤");
                        btnVoiceQuestion.setButtonColor(Color.parseColor("#3B82F6"));
                    }
                    renderPlayerAvatarsFromList(roomPlayersList, currentAnswererUid);
                    askQuestion(text);
                    Toast.makeText(requireContext(), "Asked: " + text, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String message) {
                    isVoiceListening = false;
                    if (partyVoiceCallManager != null) partyVoiceCallManager.resumeRecording();
                    if (btnVoiceQuestion != null) {
                        btnVoiceQuestion.setText("🎤");
                        btnVoiceQuestion.setButtonColor(Color.parseColor("#3B82F6"));
                    }
                    renderPlayerAvatarsFromList(roomPlayersList, currentAnswererUid);
                    Toast.makeText(requireContext(), "Voice error: " + message, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onListeningStarted() {
                    isVoiceListening = true;
                    if (partyVoiceCallManager != null) partyVoiceCallManager.pauseRecording();
                    if (btnVoiceQuestion != null) {
                        btnVoiceQuestion.setText("🔴");
                        btnVoiceQuestion.setButtonColor(Color.parseColor("#EF4444"));
                    }
                    renderPlayerAvatarsFromList(roomPlayersList, currentAnswererUid);
                }

                @Override
                public void onListeningStopped() {
                    isVoiceListening = false;
                    if (partyVoiceCallManager != null) partyVoiceCallManager.resumeRecording();
                    if (btnVoiceQuestion != null) {
                        btnVoiceQuestion.setText("🎤");
                        btnVoiceQuestion.setButtonColor(Color.parseColor("#3B82F6"));
                    }
                    renderPlayerAvatarsFromList(roomPlayersList, currentAnswererUid);
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

        List<String> questions = new ArrayList<>();
        if (mode == GameMode.ANIMALS) {
            questions.add("Does it have 4 legs?");
            questions.add("Is it a domestic pet?");
            questions.add("Does it live in water?");
            questions.add("Can it fly?");
            questions.add("Is it a wild carnivore?");
            questions.add("Is it larger than a human?");
        } else if (mode == GameMode.FOOD) {
            questions.add("Is it sweet or a fruit?");
            questions.add("Is it served hot?");
            questions.add("Eaten with hands?");
            questions.add("Is it vegetarian?");
            questions.add("Is it fast food?");
        } else if (mode == GameMode.COUNTRIES) {
            questions.add("Is it in Asia or Europe?");
            questions.add("Is it an island nation?");
            questions.add("Population over 100M?");
            questions.add("Is it in the Americas?");
        } else if (mode == GameMode.CELEBRITIES) {
            questions.add("Is this person an athlete?");
            questions.add("Is this person an actor/singer?");
            questions.add("From North America or Europe?");
            questions.add("An entrepreneur or leader?");
        } else {
            questions.add("Is it bigger than a human?");
            questions.add("Found indoors?");
            questions.add("Is it man-made?");
            questions.add("Used daily?");
        }

        for (String q : questions) {
            ModernFButton chip = new ModernFButton(requireContext());
            chip.setText(q);
            chip.setTextSize(11.0f);
            chip.setButtonColor(Color.parseColor("#1E293B"));
            chip.setShadowColor(Color.parseColor("#0F172A"));
            chip.setCornerRadiusDp(14f);
            chip.setPadding(24, 12, 24, 12);
            chip.setTextColor(Color.parseColor("#E2E8F0"));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    Math.round(38 * getResources().getDisplayMetrics().density));
            lp.setMargins(0, 0, 12, 0);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                HapticsHelper.vibrateClick(chip);
                askQuestion(q);
            });

            container.addView(chip);
        }
    }

    private void askQuestion(String questionText) {
        if (questionText == null || questionText.trim().isEmpty()) return;
        String cleanQ = questionText.trim();

        if (isOfflineMode && offlineEngine != null) {
            offlineEngine.askQuestionLocally(cleanQ);
            return;
        }

        if (currentRoomId != null) {
            String myUid = preferences != null ? preferences.getUserId() : "";
            String myName = preferences != null ? preferences.getUsername() : "Guesser";

            RoomInfo.QuestionItem qItem = new RoomInfo.QuestionItem();
            qItem.question = cleanQ;
            qItem.answer = "Waiting for answer...";
            qItem.askerUid = myUid;
            qItem.askerName = myName;

            firebaseManager.getRoomRef(currentRoomId).child("qaHistory").push().setValue(qItem);

            int updatedCount = Math.max(0, remainingQuestionsCount - 1);
            remainingQuestionsCount = updatedCount;
            if (tvQuestionsLeft != null) tvQuestionsLeft.setText(String.valueOf(updatedCount));
            firebaseManager.getRoomRef(currentRoomId).child("questionsRemaining").setValue(updatedCount);

            // Deduct 1 point from player's score for asking a question (minimum 0)
            if (myUid != null && !myUid.isEmpty()) {
                firebaseManager.getRoomRef(currentRoomId).child("players").child(myUid).child("score")
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                Long current = snapshot.getValue(Long.class);
                                long updated = Math.max(0L, (current != null ? current : 20L) - 1L);
                                snapshot.getRef().setValue(updated);
                            }
                            @Override public void onCancelled(@NonNull DatabaseError error) {}
                        });
            }

            if (socket != null && socket.isConnected()) {
                socket.askQuestion(cleanQ);
            }
        }
    }

    // ── ACTION BUTTONS (YES / MAYBE / NO / GUESS) ─────────────────────────

    private void bindActionButtons(View root) {
        btnAnswerYes = root.findViewById(R.id.btnAnswerYes);
        btnAnswerMaybe = root.findViewById(R.id.btnAnswerMaybe);
        btnAnswerNo = root.findViewById(R.id.btnAnswerNo);

        if (btnAnswerYes != null) {
            btnAnswerYes.setText("YES 👍");
            btnAnswerYes.setButtonColor(Color.parseColor("#10B981"));
            btnAnswerYes.setShadowColor(Color.parseColor("#047857"));
            btnAnswerYes.setOnClickListener(v -> sendAnswer("YES 👍"));
        }
        if (btnAnswerMaybe != null) {
            btnAnswerMaybe.setText("MAYBE 🤷");
            btnAnswerMaybe.setButtonColor(Color.parseColor("#F59E0B"));
            btnAnswerMaybe.setShadowColor(Color.parseColor("#B45309"));
            btnAnswerMaybe.setOnClickListener(v -> sendAnswer("MAYBE 🤷"));
        }
        if (btnAnswerNo != null) {
            btnAnswerNo.setText("NO 👎");
            btnAnswerNo.setButtonColor(Color.parseColor("#EF4444"));
            btnAnswerNo.setShadowColor(Color.parseColor("#B91C1C"));
            btnAnswerNo.setOnClickListener(v -> sendAnswer("NO 👎"));
        }

        if (btnGuess != null) {
            btnGuess.setButtonColor(Color.parseColor("#2563EB"));
            btnGuess.setShadowColor(Color.parseColor("#1D4ED8"));
            btnGuess.setOnClickListener(v -> {
                CustomGuessDialog dialog = new CustomGuessDialog(requireContext(), this::submitGuessOnlineOrOffline);
                dialog.show();
            });
        }
    }

    private void sendAnswer(String answer) {
        HapticsHelper.vibrateClick(btnGuess);
        if (isOfflineMode && offlineEngine != null) {
            offlineEngine.answerQuestionLocally(answer);
        } else if (currentRoomId != null) {
            String myUid = preferences != null ? preferences.getUserId() : "";
            String myName = preferences != null ? preferences.getUsername() : "Answerer";

            firebaseManager.getRoomRef(currentRoomId).child("qaHistory")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            DataSnapshot targetSnap = null;
                            for (DataSnapshot child : snapshot.getChildren()) {
                                String ans = child.child("answer").getValue(String.class);
                                if (ans == null || ans.contains("Waiting") || ans.equals("...")) {
                                    targetSnap = child;
                                }
                            }
                            if (targetSnap != null) {
                                targetSnap.getRef().child("answer").setValue(answer);
                                targetSnap.getRef().child("answererUid").setValue(myUid);
                                targetSnap.getRef().child("answererName").setValue(myName);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {}
                    });

            if (socket != null && socket.isConnected()) {
                socket.answerQuestion(answer);
            }
        }
    }

    private void submitGuessOnlineOrOffline(String guessText) {
        if (guessText == null || guessText.trim().isEmpty()) return;
        String cleanGuess = guessText.trim();

        if (isOfflineMode && offlineEngine != null) {
            offlineEngine.submitGuess(cleanGuess);
            return;
        }

        if (currentRoomId == null) return;
        String myUid = preferences != null ? preferences.getUserId() : "";
        String myName = preferences != null ? preferences.getUsername() : "Player";

        boolean isCorrect = currentSecretCard != null && !currentSecretCard.isEmpty() &&
                cleanGuess.equalsIgnoreCase(currentSecretCard.trim());

        if (isCorrect) {
            try {
                glab.guesscard.GuessCardApp.from(requireContext()).getAudio().stopAllSounds();
                glab.guesscard.GuessCardApp.from(requireContext()).getAudio().playSound(glab.guesscard.audio.GameAudio.Sound.CORRECT);
            } catch (Exception ignored) {}

            pauseTimer();
            int pts = 100 + remainingQuestionsCount;
            if (gameCardView != null) {
                gameCardView.animateCorrectGuess(() -> {
                    Context ctx = getContext();
                    if (ctx != null) {
                        Toast.makeText(ctx, "🎉 Correct Guess! +" + pts + " pts", Toast.LENGTH_LONG).show();
                    }
                });
            }

            if (myUid != null) {
                firebaseManager.getRoomRef(currentRoomId).child("players").child(myUid).child("score")
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                Long current = snapshot.getValue(Long.class);
                                long updated = (current != null ? current : 0L) + pts;
                                snapshot.getRef().setValue(updated);
                            }
                            @Override public void onCancelled(@NonNull DatabaseError error) {}
                        });
            }

            RoomInfo.QuestionItem correctItem = new RoomInfo.QuestionItem();
            correctItem.question = "🎯 GUESS: " + cleanGuess;
            correctItem.answer = "CORRECT!";
            correctItem.askerName = myName;
            firebaseManager.getRoomRef(currentRoomId).child("qaHistory").push().setValue(correctItem);

            if (socket != null && socket.isConnected()) {
                socket.submitGuess(cleanGuess);
            }

            if (getView() != null) {
                getView().postDelayed(this::advanceToNextRound, 3000);
            }
        } else {
            if (gameCardView != null) gameCardView.animateWrongGuess();
            Context ctx = getContext();
            if (ctx != null) {
                Toast.makeText(ctx, "❌ Wrong Guess: " + cleanGuess, Toast.LENGTH_SHORT).show();
            }

            RoomInfo.QuestionItem wrongItem = new RoomInfo.QuestionItem();
            wrongItem.question = "🎯 GUESS: " + cleanGuess;
            wrongItem.answer = "WRONG";
            wrongItem.askerName = myName;
            firebaseManager.getRoomRef(currentRoomId).child("qaHistory").push().setValue(wrongItem);
        }
    }

    private void advanceToNextRound() {
        if (currentRoomId == null) return;
        try {
            glab.guesscard.GuessCardApp.from(requireContext()).getAudio().stopAllSounds();
        } catch (Exception ignored) {}
        int nextRound = currentRoundNumber + 1;

        if (nextRound > totalRoundsCount) {
            firebaseManager.getRoomRef(currentRoomId).child("status").setValue("FINISHED");
            showGameFinished();
            return;
        }

        // Rotate Answerer to the next player
        String nextAnswererUid = currentAnswererUid;
        if (!roomPlayersList.isEmpty()) {
            int currentIdx = 0;
            for (int i = 0; i < roomPlayersList.size(); i++) {
                if (roomPlayersList.get(i).userId != null && roomPlayersList.get(i).userId.equals(currentAnswererUid)) {
                    currentIdx = i;
                    break;
                }
            }
            int nextIdx = (currentIdx + 1) % roomPlayersList.size();
            nextAnswererUid = roomPlayersList.get(nextIdx).userId;
        }

        Card nextCard = Card.getRandomCardForMode(currentGameMode);

        Map<String, Object> nextRoundData = new HashMap<>();
        nextRoundData.put("secretCard", nextCard.word);
        nextRoundData.put("secretCardCategory", nextCard.category);
        nextRoundData.put("answererUid", nextAnswererUid);
        nextRoundData.put("currentRound", nextRound);
        nextRoundData.put("questionsRemaining", 20);

        firebaseManager.getRoomRef(currentRoomId).updateChildren(nextRoundData);
        startCountdownTimer(60000L);
    }

    private void showGameFinished() {
        if (!isAdded() || getActivity() == null) return;
        androidx.fragment.app.FragmentActivity activity = getActivity();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            if (!isAdded() || getContext() == null) return;
            
            if (socket != null) {
                socket.clearPendingRoom();
            }

            String winner = "Player";
            int topScore = 0;
            for (RoomInfo.LobbyPlayer p : roomPlayersList) {
                if (p.score >= topScore) {
                    topScore = p.score;
                    winner = p.username;
                }
            }

            Intent intent = new Intent(requireContext(), WinnerActivity.class);
            intent.putExtra("winnerName", winner);
            intent.putExtra("finalScore", topScore);
            intent.putExtra("roomId", currentRoomId);
            intent.putExtra("mode", currentGameMode.name());
            startActivity(intent);
            activity.finish();
        });
    }

    private void showMatchAbandonedDialog(boolean isHost) {
        if (!isAdded() || getActivity() == null) return;
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_match_abandoned, null);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = dialogView.findViewById(R.id.tvAbandonedTitle);
        TextView tvMsg = dialogView.findViewById(R.id.tvAbandonedMessage);
        View containerHost = dialogView.findViewById(R.id.containerHostActions);
        View containerPlayer = dialogView.findViewById(R.id.containerPlayerActions);
        ModernFButton btnReturnLobby = dialogView.findViewById(R.id.btnReturnToLobby);
        ModernFButton btnCloseRoom = dialogView.findViewById(R.id.btnCloseRoom);
        ModernFButton btnBackHome = dialogView.findViewById(R.id.btnBackToHome);

        if (isHost) {
            if (tvTitle != null) tvTitle.setText("Players Left the Match 🚪");
            if (tvMsg != null) tvMsg.setText("All other players have left or disconnected from the match.\n\nAs the host, you can return to the lobby to wait for new players, or close the room.");
            if (containerHost != null) containerHost.setVisibility(View.VISIBLE);
            if (containerPlayer != null) containerPlayer.setVisibility(View.GONE);

            if (btnReturnLobby != null) {
                btnReturnLobby.setButtonColor(Color.parseColor("#10B981"));
                btnReturnLobby.setShadowColor(Color.parseColor("#059669"));
                btnReturnLobby.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (currentRoomId != null && firebaseManager != null) {
                        Map<String, Object> lobbyReset = new HashMap<>();
                        lobbyReset.put("status", "WAITING");
                        lobbyReset.put("isPaused", false);
                        lobbyReset.put("currentRound", 1);
                        lobbyReset.put("questionsRemaining", 20);
                        firebaseManager.getRoomRef(currentRoomId).updateChildren(lobbyReset);
                        firebaseManager.getRoomRef(currentRoomId).child("qaHistory").removeValue();

                        // Prune all quit/disconnected players so only the active host remains in the lobby
                        String myUid = preferences != null ? preferences.getUserId() : "";
                        firebaseManager.getRoomRef(currentRoomId).child("players").addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot snapshot) {
                                for (DataSnapshot pSnap : snapshot.getChildren()) {
                                    String pUid = pSnap.getKey();
                                    if (pUid != null && !pUid.equals(myUid)) {
                                        pSnap.getRef().removeValue();
                                    } else if (pUid != null && pUid.equals(myUid)) {
                                        pSnap.child("isReady").getRef().setValue(false);
                                        pSnap.child("isAway").getRef().setValue(false);
                                        pSnap.child("score").getRef().setValue(0);
                                    }
                                }
                            }
                            @Override public void onCancelled(DatabaseError error) {}
                        });
                    }
                    if (getActivity() != null) {
                        Intent intent = new Intent(requireContext(), glab.guesscard.activities.LobbyActivity.class);
                        intent.putExtra(glab.guesscard.activities.LobbyActivity.EXTRA_ROOM_ID, currentRoomId);
                        startActivity(intent);
                        getActivity().finish();
                    }
                });
            }

            if (btnCloseRoom != null) {
                btnCloseRoom.setButtonColor(Color.parseColor("#EF4444"));
                btnCloseRoom.setShadowColor(Color.parseColor("#991B1B"));
                btnCloseRoom.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (socket != null) {
                        socket.leaveRoom();
                        socket.clearPendingRoom();
                    }
                    if (currentRoomId != null && firebaseManager != null) {
                        firebaseManager.deleteRoom(currentRoomId);
                    }
                    if (getActivity() != null) {
                        Intent intent = new Intent(requireContext(), glab.guesscard.activities.MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                        getActivity().finish();
                    }
                });
            }
        } else {
            if (tvTitle != null) tvTitle.setText("Match Incomplete 🚪");
            if (tvMsg != null) tvMsg.setText("The host or other players have left the match. The game cannot continue.");
            if (containerHost != null) containerHost.setVisibility(View.GONE);
            if (containerPlayer != null) containerPlayer.setVisibility(View.VISIBLE);

            if (btnBackHome != null) {
                btnBackHome.setButtonColor(Color.parseColor("#2563EB"));
                btnBackHome.setShadowColor(Color.parseColor("#1D4ED8"));
                btnBackHome.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (socket != null) {
                        socket.leaveRoom();
                        socket.clearPendingRoom();
                    }
                    String myUid = preferences != null ? preferences.getUserId() : "";
                    if (currentRoomId != null && myUid != null && firebaseManager != null) {
                        firebaseManager.getRoomRef(currentRoomId).child("players").child(myUid).removeValue();
                    }
                    if (getActivity() != null) {
                        Intent intent = new Intent(requireContext(), glab.guesscard.activities.MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                        getActivity().finish();
                    }
                });
            }
        }

        dialog.show();
    }

    private void updateRoleControls() {
        View voiceContainer = getView() != null ? getView().findViewById(R.id.voiceControlsContainer) : null;
        View quickHeader = getView() != null ? getView().findViewById(R.id.tvQuickQuestionsHeader) : null;
        View quickScroll = getView() != null ? getView().findViewById(R.id.quickQuestionsContainer) : null;

        if (voiceContainer != null) {
            voiceContainer.setVisibility(isOfflineMode ? View.GONE : View.VISIBLE);
        }

        if (answerButtonContainer != null && btnGuess != null) {
            if (isOfflineMode) {
                answerButtonContainer.setVisibility(View.GONE);
                btnGuess.setVisibility(View.VISIBLE);
                if (btnVoiceQuestion != null) btnVoiceQuestion.setVisibility(View.GONE);
            } else if (isGuesser) {
                answerButtonContainer.setVisibility(View.GONE);
                btnGuess.setVisibility(View.VISIBLE);
                if (btnVoiceQuestion != null) btnVoiceQuestion.setVisibility(View.VISIBLE);
                if (quickHeader != null) quickHeader.setVisibility(View.VISIBLE);
                if (quickScroll != null && quickScroll.getParent() instanceof View) {
                    ((View) quickScroll.getParent()).setVisibility(View.VISIBLE);
                }
            } else {
                btnGuess.setVisibility(View.GONE);
                if (btnVoiceQuestion != null) btnVoiceQuestion.setVisibility(View.GONE);
                if (quickHeader != null) quickHeader.setVisibility(View.GONE);
                if (quickScroll != null && quickScroll.getParent() instanceof View) {
                    ((View) quickScroll.getParent()).setVisibility(View.GONE);
                }
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Pause handling: mark player away so match pauses instead of instantly terminating
        if (currentRoomId != null && !isOfflineMode && preferences != null) {
            String myUid = preferences.getUserId();
            String myName = preferences.getUsername();
            if (myUid != null) {
                firebaseManager.getRoomRef(currentRoomId).child("players").child(myUid).child("isAway").setValue(true);
                firebaseManager.getRoomRef(currentRoomId).child("isPaused").setValue(true);
                firebaseManager.getRoomRef(currentRoomId).child("pausedPlayerName").setValue(myName);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (currentRoomId != null && !isOfflineMode && preferences != null) {
            String myUid = preferences.getUserId();
            if (myUid != null) {
                firebaseManager.getRoomRef(currentRoomId).child("players").child(myUid).child("isAway").setValue(false);
                // Check if other players are still away
                firebaseManager.getRoomRef(currentRoomId).child("players").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        boolean anyAway = false;
                        for (DataSnapshot pSnap : snapshot.getChildren()) {
                            Boolean away = pSnap.child("isAway").getValue(Boolean.class);
                            if (Boolean.TRUE.equals(away)) {
                                anyAway = true;
                                break;
                            }
                        }
                        if (!anyAway && currentRoomId != null) {
                            firebaseManager.getRoomRef(currentRoomId).child("isPaused").setValue(false);
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
            }
        }
    }

    // ── SOCKET CALLBACKS ──────────────────────────────────────────────────

    @Override
    public void onRoomUpdated(RoomInfo room) {
        if (room == null || !isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (tvRoundIndicator != null)
                tvRoundIndicator.setText("ROUND " + room.currentRound + " / " + (room.totalRounds > 0 ? room.totalRounds : 5));
            if (tvQuestionsLeft != null)
                tvQuestionsLeft.setText(String.valueOf(room.questionsRemaining));

            isGuesser = room.isGuesser;
            if (gameCardView != null) gameCardView.setCardData(room.card, room.mode, isGuesser);

            updateRoleControls();
            if (room.players != null) {
                renderPlayerAvatarsFromList(room.players, room.currentTurnPlayerId);
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
            historyAdapter.updateLastAnswer(answer, answererName);
        });
    }

    @Override
    public void onGuessResult(String guessedBy, String guess, boolean isCorrect, int scoreAwarded, String cardAnswer) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (isCorrect) {
                int pts = scoreAwarded > 0 ? scoreAwarded : 100;
                gameCardView.animateCorrectGuess(() -> {
                    Toast.makeText(requireContext(), "🎉 " + guessedBy + " guessed it correctly! +" + pts + " pts", Toast.LENGTH_LONG).show();
                });
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
        showGameFinished();
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
            if (rvQuestionHistory != null) {
                rvQuestionHistory.smoothScrollToPosition(Math.max(0, historyAdapter.getItemCount() - 1));
            }
        });
    }

    @Override
    public void onGameOver(int finalScore) {
        showGameFinished();
    }

    @Override
    public void onDestroyView() {
        try {
            glab.guesscard.GuessCardApp.from(requireContext()).getAudio().stopAllSounds();
        } catch (Exception ignored) {}
        if (partyVoiceCallManager != null) {
            partyVoiceCallManager.release();
            partyVoiceCallManager = null;
        }
        if (currentRoomId != null && roomLiveListener != null && firebaseManager != null) {
            firebaseManager.getRoomRef(currentRoomId).removeEventListener(roomLiveListener);
        }
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
        if (audioManager != null) {
            try {
                audioManager.setSpeakerphoneOn(false);
                audioManager.setMode(AudioManager.MODE_NORMAL);
            } catch (Exception ignored) {}
        }
        if (socket != null) {
            socket.clearPendingRoom();
            socket.setListener(null);
        }
        if (offlineEngine != null) offlineEngine.stop();
        if (voiceHelper != null) voiceHelper.release();
        super.onDestroyView();
    }

    // ── QUESTION HISTORY ADAPTER ───────────────────────────────────────────

    private static class QuestionHistoryAdapter extends RecyclerView.Adapter<QuestionHistoryAdapter.ViewHolder> {
        private final List<RoomInfo.QuestionItem> items = new ArrayList<>();

        public void setItems(List<RoomInfo.QuestionItem> list) {
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
