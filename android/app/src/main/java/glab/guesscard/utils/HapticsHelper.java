package glab.guesscard.utils;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;

/**
 * Haptic feedback helper for card flips, button clicks, correct/wrong guesses, and timer pulses.
 */
public class HapticsHelper {

    public static void vibrateClick(View view) {
        if (view != null) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    public static void vibrateCorrect(Context context) {
        vibratePattern(context, new long[]{0, 50, 50, 100});
    }

    public static void vibrateWrong(Context context) {
        vibratePattern(context, new long[]{0, 80, 40, 80});
    }

    public static void vibrateTimerWarning(Context context) {
        vibratePattern(context, new long[]{0, 40});
    }

    private static void vibratePattern(Context context, long[] pattern) {
        if (context == null) return;
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } else {
            vibrator.vibrate(pattern, -1);
        }
    }
}
