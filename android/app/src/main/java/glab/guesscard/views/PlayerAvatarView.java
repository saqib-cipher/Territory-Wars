package glab.guesscard.views;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Custom PlayerAvatarView displaying avatar circle, username, score/level badge, ready status,
 * mic icon, and animated glowing border/pulse ring for the active turn player.
 */
public class PlayerAvatarView extends LinearLayout {

    private View avatarCircle;
    private TextView nameText;
    private TextView scoreText;
    private TextView readyText;
    private TextView levelText;
    private ObjectAnimator pulseAnim;

    private boolean isTurn = false;
    private boolean isMuted = false;
    private android.graphics.Bitmap avatarBitmap;

    public void setAvatarBitmap(android.graphics.Bitmap bitmap) {
        this.avatarBitmap = bitmap;
        if (avatarCircle != null) avatarCircle.invalidate();
    }

    public PlayerAvatarView(Context context) {
        super(context);
        init(context);
    }

    public PlayerAvatarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PlayerAvatarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setPadding(10, 8, 10, 8);

        avatarCircle = new View(context) {
            private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF bounds = new RectF();

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                int size = Math.min(getWidth(), getHeight());
                bounds.set(8, 8, size - 8, size - 8);

                bgPaint.setColor(Color.parseColor("#3B82F6"));
                canvas.drawOval(bounds, bgPaint);

                borderPaint.setStyle(Paint.Style.STROKE);
                borderPaint.setStrokeWidth(isTurn ? 10f : 4f);
                borderPaint.setColor(isTurn ? Color.parseColor("#F59E0B") : Color.parseColor("#64748B"));
                canvas.drawOval(bounds, borderPaint);

                if (avatarBitmap != null) {
                    canvas.drawBitmap(avatarBitmap, null, bounds, null);
                } else {
                    // Avatar letter placeholder
                    textPaint.setColor(Color.WHITE);
                    textPaint.setTextSize(size * 0.42f);
                    textPaint.setTextAlign(Paint.Align.CENTER);
                    textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

                    String initial = (nameText.getText() != null && nameText.getText().length() > 0)
                            ? String.valueOf(nameText.getText().charAt(0)).toUpperCase()
                            : "P";
                    float yPos = (getHeight() / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f);
                    canvas.drawText(initial, getWidth() / 2f, yPos, textPaint);
                }
            }
        };

        LinearLayout.LayoutParams circleLp = new LinearLayout.LayoutParams(92, 92);
        circleLp.gravity = Gravity.CENTER_HORIZONTAL;
        addView(avatarCircle, circleLp);

        nameText = new TextView(context);
        nameText.setTextSize(11);
        nameText.setTextColor(Color.WHITE);
        nameText.setGravity(Gravity.CENTER);
        nameText.setSingleLine(true);
        nameText.setText("Player");

        levelText = new TextView(context);
        levelText.setTextSize(9);
        levelText.setTextColor(Color.parseColor("#38BDF8"));
        levelText.setGravity(Gravity.CENTER);
        levelText.setText("Lvl 1");

        scoreText = new TextView(context);
        scoreText.setTextSize(10);
        scoreText.setTextColor(Color.parseColor("#F59E0B"));
        scoreText.setGravity(Gravity.CENTER);
        scoreText.setText("0 pts");

        readyText = new TextView(context);
        readyText.setTextSize(10);
        readyText.setTextColor(Color.parseColor("#10B981"));
        readyText.setGravity(Gravity.CENTER);
        readyText.setVisibility(GONE);

        addView(nameText);
        addView(levelText);
        addView(scoreText);
        addView(readyText);
    }

    public void setPlayerData(String username, int score, boolean isReady, boolean isTurn) {
        setPlayerData(username, score, 1, isReady, isTurn);
    }

    public void setPlayerData(String username, int score, int level, boolean isReady, boolean isTurn) {
        if (username != null) nameText.setText(username);
        levelText.setText("Lvl " + level);
        scoreText.setText(score + " pts");
        readyText.setText(isReady ? "READY ✓" : "NOT READY");
        readyText.setVisibility(isReady ? VISIBLE : GONE);

        this.isTurn = isTurn;
        avatarCircle.invalidate();

        if (isTurn) {
            startTurnPulse();
        } else {
            stopTurnPulse();
        }
    }

    public void setMuted(boolean muted) {
        this.isMuted = muted;
        nameText.setText((isMuted ? "🔇 " : "🎤 ") + (nameText.getText() != null ? nameText.getText().toString().replace("🔇 ", "").replace("🎤 ", "") : ""));
    }

    private void startTurnPulse() {
        if (pulseAnim == null) {
            pulseAnim = ObjectAnimator.ofFloat(avatarCircle, View.SCALE_X, 1.0f, 1.12f);
            pulseAnim.setDuration(600);
            pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
            pulseAnim.setRepeatMode(ValueAnimator.REVERSE);
        }
        if (!pulseAnim.isRunning()) pulseAnim.start();
    }

    private void stopTurnPulse() {
        if (pulseAnim != null && pulseAnim.isRunning()) {
            pulseAnim.cancel();
            avatarCircle.setScaleX(1.0f);
            avatarCircle.setScaleY(1.0f);
        }
    }
}
