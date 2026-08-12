package glab.guesscard.views;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.CycleInterpolator;

import glab.guesscard.utils.HapticsHelper;

/**
 * Custom Animated Circular Timer View.
 * Displays real-time server turn countdown with smooth arc progress,
 * color shifts, and 15s warning / 5s heartbeat pulse animations.
 */
public class TimerView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private int totalSeconds = 60;
    private int remainingSeconds = 60;
    private float progressRatio = 1.0f;

    public TimerView(Context context) {
        super(context);
        init();
    }

    public TimerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TimerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(12f);
        trackPaint.setColor(Color.parseColor("#334155"));

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(14f);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(Color.parseColor("#10B981"));

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(32f);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textPaint.setColor(Color.WHITE);
    }

    public void setTime(int remaining, int total) {
        this.totalSeconds = Math.max(1, total);
        this.remainingSeconds = Math.max(0, remaining);
        this.progressRatio = (float) this.remainingSeconds / (float) this.totalSeconds;

        // Color transition
        if (progressRatio > 0.5f) {
            progressPaint.setColor(Color.parseColor("#10B981")); // Emerald green
        } else if (progressRatio > 0.25f) {
            progressPaint.setColor(Color.parseColor("#F59E0B")); // Amber yellow
        } else {
            progressPaint.setColor(Color.parseColor("#EF4444")); // Crimson red
        }

        // Pulse warning at 15s and 5s
        if (this.remainingSeconds == 15 || this.remainingSeconds == 5) {
            triggerWarningPulse();
        }

        invalidate();
    }

    private void triggerWarningPulse() {
        HapticsHelper.vibrateTimerWarning(getContext());
        ObjectAnimator pulse = ObjectAnimator.ofFloat(this, View.SCALE_X, 1.0f, 1.15f, 1.0f);
        pulse.setDuration(300);
        pulse.setInterpolator(new CycleInterpolator(1));
        pulse.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int padding = 20;
        int size = Math.min(getWidth(), getHeight());
        int strokeWidth = 14;

        oval.set(padding + strokeWidth, padding + strokeWidth,
                size - padding - strokeWidth, size - padding - strokeWidth);

        // Draw track arc
        canvas.drawArc(oval, 0, 360, false, trackPaint);

        // Draw progress arc
        float sweepAngle = 360f * progressRatio;
        canvas.drawArc(oval, -90, sweepAngle, false, progressPaint);

        // Draw remaining seconds text
        String text = String.valueOf(remainingSeconds);
        float yPos = (getHeight() / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f);
        canvas.drawText(text, getWidth() / 2f, yPos, textPaint);
    }
}
