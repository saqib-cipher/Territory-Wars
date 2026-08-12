package glab.guesscard.views;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import glab.guesscard.ModernFButton;

/**
 * Custom Guess Input Dialog for Guess the Card party game.
 * Zero Material 3 dependency. Replaces default dialogs with party game visual styling.
 */
public class CustomGuessDialog extends Dialog {

    public interface GuessCallback {
        void onSubmitGuess(String guessText);
    }

    private final GuessCallback callback;
    private EditText guessInput;
    private View dialogCardView;

    public CustomGuessDialog(Context context, GuessCallback callback) {
        super(context);
        this.callback = callback;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        FrameLayout root = new FrameLayout(getContext());
        root.setPadding(32, 32, 32, 32);

        dialogCardView = new LinearLayout(getContext());
        ((LinearLayout) dialogCardView).setOrientation(LinearLayout.VERTICAL);
        dialogCardView.setPadding(48, 48, 48, 48);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E1B4B")); // Deep indigo background
        bg.setCornerRadius(32f);
        bg.setStroke(4, Color.parseColor("#6366F1"));
        dialogCardView.setBackground(bg);

        TextView title = new TextView(getContext());
        title.setText("WHAT'S YOUR GUESS?");
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 16);

        guessInput = new EditText(getContext());
        guessInput.setHint("e.g. Lion, USA, Pizza...");
        guessInput.setHintTextColor(Color.parseColor("#94A3B8"));
        guessInput.setTextColor(Color.WHITE);
        guessInput.setTextSize(16);
        guessInput.setPadding(24, 20, 24, 20);

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Color.parseColor("#312E81"));
        inputBg.setCornerRadius(16f);
        inputBg.setStroke(2, Color.parseColor("#4F46E5"));
        guessInput.setBackground(inputBg);

        LinearLayout btnRow = new LinearLayout(getContext());
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, 24, 0, 0);

        ModernFButton cancelBtn = new ModernFButton(getContext());
        cancelBtn.setText("Cancel");
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, 96, 1f);
        cancelLp.setMargins(0, 0, 12, 0);

        ModernFButton submitBtn = new ModernFButton(getContext());
        submitBtn.setText("Submit Guess 🚀");
        LinearLayout.LayoutParams submitLp = new LinearLayout.LayoutParams(0, 96, 1.5f);
        submitLp.setMargins(12, 0, 0, 0);

        btnRow.addView(cancelBtn, cancelLp);
        btnRow.addView(submitBtn, submitLp);

        ((LinearLayout) dialogCardView).addView(title);
        ((LinearLayout) dialogCardView).addView(guessInput);
        ((LinearLayout) dialogCardView).addView(btnRow);

        root.addView(dialogCardView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        setContentView(root);

        cancelBtn.setOnClickListener(v -> dismiss());
        submitBtn.setOnClickListener(v -> submitCurrentGuess());

        guessInput.setOnEditorActionListener((v, actionId, event) -> {
            submitCurrentGuess();
            return true;
        });

        if (getWindow() != null) {
            getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        guessInput.requestFocus();

        animateShow();
    }

    private void submitCurrentGuess() {
        if (guessInput == null) return;
        String guess = guessInput.getText().toString().trim();
        if (!guess.isEmpty() && callback != null) {
            callback.onSubmitGuess(guess);
            dismiss();
        }
    }

    private void animateShow() {
        if (dialogCardView == null) return;
        dialogCardView.setScaleX(0.7f);
        dialogCardView.setScaleY(0.7f);
        dialogCardView.setAlpha(0f);

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(dialogCardView, View.SCALE_X, 0.7f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(dialogCardView, View.SCALE_Y, 0.7f, 1.0f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(dialogCardView, View.ALPHA, 0f, 1.0f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY, alpha);
        set.setDuration(350);
        set.setInterpolator(new OvershootInterpolator(1.2f));
        set.start();
    }
}
