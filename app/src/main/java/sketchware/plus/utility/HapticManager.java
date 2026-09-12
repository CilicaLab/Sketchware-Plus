package sketchware.plus.utility;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

import sketchware.plus.SketchApplication;

public class HapticManager {

    /**
     * Unique haptic feedback for "Save" action.
     * Feels firm and permanent.
     */
    public static void vibrateSave(View v) {
        if (v != null) {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        } else {
            vibrateLegacy(80);
        }
    }

    /**
     * Unique haptic feedback for "Run" action.
     * Sharp and quick.
     */
    public static void vibrateRun(View v) {
        if (v != null) {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        } else {
            vibrateLegacy(30);
        }
    }

    /**
     * Unique haptic feedback for "Stop" action.
     * Distinguishable double-pulse.
     */
    public static void vibrateStop(View v) {
        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                v.performHapticFeedback(HapticFeedbackConstants.REJECT);
            } else {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                v.postDelayed(() -> v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY), 100);
            }
        } else {
            vibrateLegacy(new long[]{0, 30, 100, 30}, -1);
        }
    }

    private static void vibrateLegacy(long duration) {
        Context context = SketchApplication.getContext();
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    private static void vibrateLegacy(long[] pattern, int repeat) {
        Context context = SketchApplication.getContext();
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat));
        }
    }
}
