package glab.guesscard.views;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.CycleInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import glab.guesscard.utils.HapticsHelper;

/**
 * Custom 3D Animated Game Card View for Guess the Card party game.
 * Zero Material 3 dependency. Built using custom Canvas drawing, GradientDrawable,
 * and Animator APIs.
 */
public class GameCardView extends FrameLayout {

    private TextView cardCategoryText;
    private TextView cardContentText;
    private TextView cardSubtextText;
    private View cardBackgroundView;

    private boolean isGuesser = false;
    private String cardWord = "";
    private String category = "Animals";

    public GameCardView(Context context) {
        super(context);
        init(context);
    }

    public GameCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public GameCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setPadding(32, 32, 32, 32);

        cardBackgroundView = new View(context) {
            private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF rect = new RectF();

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                rect.set(16, 16, getWidth() - 16, getHeight() - 16);

                // Deep vibrant purple-indigo gradient
                LinearGradient shader = new LinearGradient(
                        0, 0, getWidth(), getHeight(),
                        Color.parseColor("#2E1065"),
                        Color.parseColor("#4C1D95"),
                        Shader.TileMode.CLAMP
                );
                bgPaint.setShader(shader);
                canvas.drawRoundRect(rect, 36, 36, bgPaint);

                // Glowing gold/purple border
                borderPaint.setStyle(Paint.Style.STROKE);
                borderPaint.setStrokeWidth(8f);
                borderPaint.setColor(isGuesser ? Color.parseColor("#F59E0B") : Color.parseColor("#A855F7"));
                canvas.drawRoundRect(rect, 36, 36, borderPaint);
            }
        };

        addView(cardBackgroundView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Text Layout Container
        android.widget.LinearLayout textContainer = new android.widget.LinearLayout(context);
        textContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
        textContainer.setGravity(Gravity.CENTER);
        textContainer.setPadding(48, 48, 48, 48);

        cardCategoryText = new TextView(context);
        cardCategoryText.setTextSize(14);
        cardCategoryText.setTextColor(Color.parseColor("#D8B4FE"));
        cardCategoryText.setGravity(Gravity.CENTER);
        cardCategoryText.setLetterSpacing(0.15f);
        cardCategoryText.setText("ANIMALS");

        cardContentText = new TextView(context);
        cardContentText.setTextSize(36);
        cardContentText.setTypeface(null, android.graphics.Typeface.BOLD);
        cardContentText.setTextColor(Color.WHITE);
        cardContentText.setGravity(Gravity.CENTER);
        cardContentText.setText("?????");
        cardContentText.setPadding(0, 24, 0, 24);

        cardSubtextText = new TextView(context);
        cardSubtextText.setTextSize(14);
        cardSubtextText.setTextColor(Color.parseColor("#C084FC"));
        cardSubtextText.setGravity(Gravity.CENTER);
        cardSubtextText.setText("Ask questions to figure out your card!");

        textContainer.addView(cardCategoryText);
        textContainer.addView(cardContentText);
        textContainer.addView(cardSubtextText);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        addView(textContainer, lp);

        setCameraDistance(10000 * context.getResources().getDisplayMetrics().density);

        animateEntry();
    }

    public void animateFlipCard(Runnable onMidFlip) {
        try {
            glab.guesscard.GuessCardApp.from(getContext()).getAudio().playSound(glab.guesscard.audio.GameAudio.Sound.CARD_FLIP);
        } catch (Exception ignored) {}
        HapticsHelper.vibrateClick(this);

        ObjectAnimator flipOut = ObjectAnimator.ofFloat(this, View.ROTATION_Y, 0f, 90f);
        flipOut.setDuration(180);
        flipOut.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator flipIn = ObjectAnimator.ofFloat(this, View.ROTATION_Y, -90f, 0f);
        flipIn.setDuration(180);
        flipIn.setInterpolator(new AccelerateDecelerateInterpolator());

        flipOut.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (onMidFlip != null) {
                    onMidFlip.run();
                }
                flipIn.start();
            }
        });

        flipOut.start();
    }

    public void setCardData(String word, String category, boolean isGuesser) {
        String newWord = word != null ? word : "";
        String newCategory = category != null ? category : "Category";
        boolean isInitialLoad = this.cardWord.isEmpty();
        boolean changed = !this.cardWord.equals(newWord) || this.isGuesser != isGuesser || !this.category.equalsIgnoreCase(newCategory);

        if (changed && !isInitialLoad) {
            // New round or card rotation: animate flip
            animateFlipCard(() -> {
                this.cardWord = newWord;
                this.category = newCategory;
                this.isGuesser = isGuesser;

                cardCategoryText.setText(this.category.toUpperCase());

                if (isGuesser) {
                    cardContentText.setText("?????");
                    cardContentText.setTextColor(Color.parseColor("#F59E0B"));
                    cardSubtextText.setText("Hold phone up! Ask questions to guess your card.");
                } else {
                    cardContentText.setText(cardWord);
                    cardContentText.setTextColor(Color.WHITE);
                    cardSubtextText.setText("Help the guessing player figure out this card!");
                }

                if (cardBackgroundView != null) {
                    cardBackgroundView.invalidate();
                }
            });
        } else {
            // Initial match entry or same round content update: render directly without flipping!
            this.cardWord = newWord;
            this.category = newCategory;
            this.isGuesser = isGuesser;
            cardCategoryText.setText(this.category.toUpperCase());
            if (isGuesser) {
                cardContentText.setText("?????");
                cardContentText.setTextColor(Color.parseColor("#F59E0B"));
                cardSubtextText.setText("Hold phone up! Ask questions to guess your card.");
            } else {
                cardContentText.setText(cardWord);
                cardContentText.setTextColor(Color.WHITE);
                cardSubtextText.setText("Help the guessing player figure out this card!");
            }
            if (cardBackgroundView != null) {
                cardBackgroundView.invalidate();
            }
        }
    }

    public void animateEntry() {
        setAlpha(0f);
        setScaleX(0.85f);
        setScaleY(0.85f);

        ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(this, View.ALPHA, 0f, 1f);
        ObjectAnimator scaleXAnim = ObjectAnimator.ofFloat(this, View.SCALE_X, 0.85f, 1.0f);
        ObjectAnimator scaleYAnim = ObjectAnimator.ofFloat(this, View.SCALE_Y, 0.85f, 1.0f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(alphaAnim, scaleXAnim, scaleYAnim);
        set.setDuration(350);
        set.setInterpolator(new OvershootInterpolator(1.1f));
        set.start();
    }

    public void animateCorrectGuess(Runnable onComplete) {
        try {
            glab.guesscard.GuessCardApp.from(getContext()).getAudio().playSound(glab.guesscard.audio.GameAudio.Sound.CORRECT);
        } catch (Exception ignored) {}
        HapticsHelper.vibrateCorrect(getContext());

        cardContentText.setText(cardWord);
        cardContentText.setTextColor(Color.parseColor("#10B981"));

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(this, View.SCALE_X, 1.0f, 1.15f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(this, View.SCALE_Y, 1.0f, 1.15f, 1.0f);
        ObjectAnimator rot = ObjectAnimator.ofFloat(this, View.ROTATION, 0f, 5f, -5f, 0f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY, rot);
        set.setDuration(500);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (onComplete != null) onComplete.run();
            }
        });
        set.start();
    }

    public void animateWrongGuess() {
        try {
            glab.guesscard.GuessCardApp.from(getContext()).getAudio().playSound(glab.guesscard.audio.GameAudio.Sound.WRONG);
        } catch (Exception ignored) {}
        HapticsHelper.vibrateWrong(getContext());
        ObjectAnimator shake = ObjectAnimator.ofFloat(this, View.TRANSLATION_X, 0f, 25f);
        shake.setDuration(350);
        shake.setInterpolator(new CycleInterpolator(3));
        shake.start();
    }
}
