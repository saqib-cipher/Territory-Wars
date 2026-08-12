package glab.guesscard.game;

import android.os.Handler;
import android.os.Looper;

import glab.guesscard.models.Card;
import glab.guesscard.models.GameMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Local Game Engine for Offline Practice Mode in Guess the Card.
 * Runs completely locally without requiring a server connection.
 */
public class GuessTheCardEngine {

    public interface EngineListener {
        void onCardChanged(Card card, boolean isGuesser);
        void onTimerTick(int secondsRemaining);
        void onQuestionAdded(String question, String answer, int questionsRemaining);
        void onGuessResult(boolean isCorrect, String guess, int scoreAwarded, int totalScore);
        void onGameOver(int finalScore);
    }

    private EngineListener listener;
    private GameMode gameMode = GameMode.ANIMALS;
    private Card currentCard;
    private int score = 0;
    private int questionsRemaining = 10;
    private int secondsRemaining = 60;
    private int questionLimit = 10;
    private boolean isGuesser = true;
    private boolean isRunning = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;
            secondsRemaining--;
            if (listener != null) listener.onTimerTick(secondsRemaining);

            if (secondsRemaining <= 0) {
                // Timeout -> Wrong guess penalty or next round
                submitGuess("");
            } else {
                handler.postDelayed(this, 1000);
            }
        }
    };

    public void setListener(EngineListener listener) {
        this.listener = listener;
    }

    public void startOfflinePractice(GameMode mode) {
        this.gameMode = mode;
        this.score = 0;
        this.questionLimit = (mode == GameMode.FOOD) ? 8 : 10;

        startNewRound();
    }

    public void startNewRound() {
        handler.removeCallbacks(timerRunnable);
        this.currentCard = Card.getRandomCardForMode(gameMode);
        this.questionsRemaining = questionLimit;
        this.secondsRemaining = 60;
        this.isGuesser = true;
        this.isRunning = true;

        if (listener != null) {
            listener.onCardChanged(currentCard, isGuesser);
            listener.onTimerTick(secondsRemaining);
        }

        handler.postDelayed(timerRunnable, 1000);
    }

    public void askQuestionLocally(String questionText) {
        if (questionsRemaining <= 0 || !isRunning) return;
        questionsRemaining--;

        String simulatedAnswer = getSmartAnswerForQuestion(questionText);
        if (listener != null) {
            listener.onQuestionAdded(questionText, simulatedAnswer, questionsRemaining);
        }
    }

    private String getSmartAnswerForQuestion(String question) {
        if (currentCard == null || question == null) return getRandomAnswer();
        String q = question.toLowerCase();
        String word = currentCard.word != null ? currentCard.word.toLowerCase() : "";

        // 4 legs check
        if (q.contains("4 legs") || q.contains("four legs")) {
            if (word.equals("lion") || word.equals("tiger") || word.equals("elephant") ||
                word.equals("dog") || word.equals("cat") || word.equals("horse") ||
                word.equals("giraffe") || word.equals("zebra") || word.equals("crocodile") ||
                word.equals("panda") || word.equals("bear") || word.equals("wolf") ||
                word.equals("rabbit") || word.equals("turtle")) {
                return "YES 👍";
            }
            return "NO 👎";
        }

        // Domestic pet check
        if (q.contains("pet") || q.contains("domestic")) {
            if (word.equals("dog") || word.equals("cat") || word.equals("rabbit")) {
                return "YES 👍";
            }
            return "NO 👎";
        }

        // Water check
        if (q.contains("water") || q.contains("ocean")) {
            if (word.equals("shark") || word.equals("dolphin") || word.equals("penguin") ||
                word.equals("crocodile") || word.equals("turtle")) {
                return "YES 👍";
            }
            return "NO 👎";
        }

        // Fly check
        if (q.contains("fly") || q.contains("air")) {
            if (word.equals("eagle")) return "YES 👍";
            return "NO 👎";
        }

        // Sweet / dessert check
        if (q.contains("sweet") || q.contains("dessert") || q.contains("fruit")) {
            if (word.equals("apple") || word.equals("banana") || word.equals("banana pro") ||
                word.equals("mango") || word.equals("ice cream") || word.equals("chocolate") ||
                word.equals("cake")) {
                return "YES 👍";
            }
            return "NO 👎";
        }

        // Hot / spicy check
        if (q.contains("hot") || q.contains("spicy")) {
            if (word.equals("pizza") || word.equals("burger") || word.equals("biryani") ||
                word.equals("pasta") || word.equals("sandwich") || word.equals("taco") ||
                word.equals("noodles") || word.equals("dosa")) {
                return "YES 👍";
            }
            return "NO 👎";
        }

        // Hands check
        if (q.contains("hands") || q.contains("hand")) {
            if (word.equals("pizza") || word.equals("burger") || word.equals("sandwich") ||
                word.equals("taco") || word.equals("apple") || word.equals("banana") ||
                word.equals("mango") || word.equals("dosa")) {
                return "YES 👍";
            }
            return "NO 👎";
        }

        return getRandomAnswer();
    }

    public void answerQuestionLocally(String answerText) {
        if (questionsRemaining <= 0 || !isRunning) return;
        questionsRemaining--;
        if (listener != null) {
            listener.onQuestionAdded("Is it valid?", answerText, questionsRemaining);
        }
    }

    public void submitGuess(String guessText) {
        handler.removeCallbacks(timerRunnable);
        boolean isCorrect = currentCard != null && currentCard.matchesGuess(guessText);
        int scoreAwarded = 0;

        if (isCorrect) {
            int speedBonus = Math.max(0, secondsRemaining / 2);
            int remainingQBonus = questionsRemaining * 5;
            scoreAwarded = 100 + speedBonus + remainingQBonus;
            score += scoreAwarded;
        }

        if (listener != null) {
            listener.onGuessResult(isCorrect, guessText, scoreAwarded, score);
        }
    }

    public void stop() {
        isRunning = false;
        handler.removeCallbacks(timerRunnable);
    }

    private String getRandomAnswer() {
        String[] options = {"YES 👍", "NO 👎", "MAYBE 🤔"};
        return options[(int) (Math.random() * options.length)];
    }

    public Card getCurrentCard() {
        return currentCard;
    }

    public int getScore() {
        return score;
    }

    public int getQuestionsRemaining() {
        return questionsRemaining;
    }

    public int getSecondsRemaining() {
        return secondsRemaining;
    }
}
