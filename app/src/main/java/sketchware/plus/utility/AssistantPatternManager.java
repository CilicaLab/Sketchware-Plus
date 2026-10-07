package sketchware.plus.utility;

import android.content.Context;
import sketchware.plus.R;

public class AssistantPatternManager {

    private static final String PREF_NAME = "app_settings";
    private static final String KEY_PATTERN = "sk_assistant_bg_pattern";

    public static final String[] PATTERN_NAMES = {
            "Dot", "Star", "Square", "Hexagon", "Triangle", "None"
    };

    public static final int[] PATTERN_DRAWABLES = {
            R.drawable.ic_dot_pattern,
            R.drawable.ic_pattern_star,
            R.drawable.ic_pattern_square,
            R.drawable.ic_pattern_hexagon,
            R.drawable.ic_pattern_triangle,
            0
    };

    public static int getSelectedPattern(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_PATTERN, 0);
    }

    public static void setSelectedPattern(Context context, int index) {
        if (index < 0 || index >= PATTERN_NAMES.length) return;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_PATTERN, index)
                .apply();
    }

    public static int getPatternDrawableRes(int index) {
        if (index < 0 || index >= PATTERN_DRAWABLES.length) return R.drawable.ic_dot_pattern;
        return PATTERN_DRAWABLES[index];
    }
}
