package glab.guesscard.animations;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * Reusable motion utilities for Material 3 expressive transitions
 * (scale-pop, slide-in, staggered item reveal).
 */
public final class Animations {

    private Animations() {
    }

    private static final DecelerateInterpolator DECELERATE = new DecelerateInterpolator();
    private static final OvershootInterpolator OVERSHOOT = new OvershootInterpolator();

    /** Scale-pop entrance for cards / rewards. */
    public static void popIn(View view) {
        view.setScaleX(0.8f);
        view.setScaleY(0.8f);
        view.setAlpha(0f);
        view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(320L)
                .setInterpolator(OVERSHOOT)
                .start();
    }

    /** Slide-in from a starting vertical offset. */
    public static void slideIn(View view, float startTranslationY) {
        view.setTranslationY(startTranslationY);
        view.setAlpha(0f);
        view.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(260L)
                .setInterpolator(DECELERATE)
                .start();
    }

    /** Staggered reveal for recycler rows. */
    public static void staggerReveal(View view, int index) {
        view.setAlpha(0f);
        view.animate()
                .alpha(1f)
                .setStartDelay(index * 60L)
                .setDuration(240L)
                .start();
    }

    /** Gentle endless pulse for "claimable" highlight. */
    public static void pulse(View view) {
        ValueAnimator anim = ValueAnimator.ofFloat(1f, 1.04f, 1f);
        anim.setDuration(1200L);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(DECELERATE);
        anim.addUpdateListener(a -> {
            float s = (float) a.getAnimatedValue();
            view.setScaleX(s);
            view.setScaleY(s);
        });
        anim.start();
    }
}