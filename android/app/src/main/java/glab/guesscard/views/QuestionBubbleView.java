package glab.guesscard.views;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import glab.guesscard.models.RoomInfo;

/**
 * Custom Speech Bubble view for question history entries.
 * Shows who asked (askerName), the question text, who answered (answererName), and the YES/NO badge.
 */
public class QuestionBubbleView extends LinearLayout {

    private TextView tvAsker;
    private TextView questionText;
    private TextView tvAnswerer;
    private TextView answerBadge;

    public QuestionBubbleView(Context context) {
        super(context);
        init(context);
    }

    public QuestionBubbleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public QuestionBubbleView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        setPadding(24, 14, 24, 14);

        int marginBottom = Math.round(8 * context.getResources().getDisplayMetrics().density);
        LayoutParams selfLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        selfLp.bottomMargin = marginBottom;
        setLayoutParams(selfLp);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(20f);
        bg.setStroke(1, Color.parseColor("#334155"));
        setBackground(bg);

        // Asker name row
        tvAsker = new TextView(context);
        tvAsker.setTextSize(10);
        tvAsker.setTextColor(Color.parseColor("#38BDF8"));
        tvAsker.setTypeface(null, Typeface.BOLD);
        addView(tvAsker);

        // Question text
        questionText = new TextView(context);
        questionText.setTextSize(14);
        questionText.setTextColor(Color.WHITE);
        LayoutParams qLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        qLp.topMargin = 4;
        addView(questionText, qLp);

        // Answer row (answerer name + badge side by side)
        LinearLayout answerRow = new LinearLayout(context);
        answerRow.setOrientation(HORIZONTAL);
        answerRow.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams rowLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = 8;
        addView(answerRow, rowLp);

        tvAnswerer = new TextView(context);
        tvAnswerer.setTextSize(10);
        tvAnswerer.setTextColor(Color.parseColor("#94A3B8"));
        answerRow.addView(tvAnswerer, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        answerBadge = new TextView(context);
        answerBadge.setTextSize(12);
        answerBadge.setTypeface(null, Typeface.BOLD);
        answerBadge.setPadding(20, 8, 20, 8);
        answerBadge.setGravity(Gravity.CENTER);
        answerRow.addView(answerBadge);
    }

    public void setQuestion(RoomInfo.QuestionItem item) {
        if (item == null) return;

        // Asker
        String asker = item.askerName != null ? item.askerName : (item.askedBy != null ? item.askedBy : "");
        tvAsker.setVisibility(asker.isEmpty() ? GONE : VISIBLE);
        tvAsker.setText(asker + " asked:");

        questionText.setText("\"" + (item.question != null ? item.question : "") + "\"");

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(14f);

        if (item.answer == null || item.answer.isEmpty() || item.answer.equals("...")) {
            answerBadge.setText("WAITING...");
            answerBadge.setTextColor(Color.parseColor("#94A3B8"));
            badgeBg.setColor(Color.parseColor("#334155"));
            tvAnswerer.setText("");
        } else {
            String ans = item.answer;
            answerBadge.setText(ans);

            // Answerer display
            String answerer = item.answererName != null ? item.answererName : "";
            tvAnswerer.setVisibility(answerer.isEmpty() ? GONE : VISIBLE);
            tvAnswerer.setText(answerer + " answered:");

            // Color by answer type
            String ansUpper = ans.toUpperCase();
            if (ansUpper.contains("YES")) {
                badgeBg.setColor(Color.parseColor("#10B981"));
                answerBadge.setTextColor(Color.WHITE);
            } else if (ansUpper.contains("NO")) {
                badgeBg.setColor(Color.parseColor("#EF4444"));
                answerBadge.setTextColor(Color.WHITE);
            } else if (ansUpper.contains("MAYBE")) {
                badgeBg.setColor(Color.parseColor("#F59E0B"));
                answerBadge.setTextColor(Color.WHITE);
            } else {
                badgeBg.setColor(Color.parseColor("#64748B"));
                answerBadge.setTextColor(Color.WHITE);
            }
        }
        answerBadge.setBackground(badgeBg);
    }
}
