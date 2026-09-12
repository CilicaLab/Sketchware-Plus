package sketchware.plus.utility;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

public class AppIconManager {

    private static final String PREF_NAME = "app_settings";
    private static final String KEY_SELECTED_ICON = "selected_app_icon";

    public static final String[] LAUNCHER_ALIASES = {
            "sketchware.plus.LauncherOriginal",
            "sketchware.plus.LauncherBlue",
            "sketchware.plus.LauncherPurple",
            "sketchware.plus.LauncherDark",
            "sketchware.plus.LauncherLight",
            "sketchware.plus.LauncherGold",
            "sketchware.plus.LauncherRed",
            "sketchware.plus.LauncherGreen",
            "sketchware.plus.LauncherPink",
            "sketchware.plus.LauncherPlatinum",
            "sketchware.plus.LauncherOrange",
            "sketchware.plus.LauncherCyan"
    };

    public static final String[] ICON_NAMES = {
            "Original", "Deep Blue", "Purple", "Dark", "Light", "Gold", "Red", "Green", "Pink", "Platinum", "Orange", "Cyan"
    };

    public static final int[] ICON_DRAWABLES = {
            sketchware.plus.R.mipmap.ic_launcher_original,
            sketchware.plus.R.mipmap.ic_launcher_blue,
            sketchware.plus.R.mipmap.ic_launcher_purple,
            sketchware.plus.R.mipmap.ic_launcher_dark,
            sketchware.plus.R.mipmap.ic_launcher_light,
            sketchware.plus.R.mipmap.ic_launcher_gold,
            sketchware.plus.R.mipmap.ic_launcher_red,
            sketchware.plus.R.mipmap.ic_launcher_green,
            sketchware.plus.R.mipmap.ic_launcher_pink,
            sketchware.plus.R.mipmap.ic_launcher_platinum,
            sketchware.plus.R.mipmap.ic_launcher_orange,
            sketchware.plus.R.mipmap.ic_launcher_cyan
    };

    public static void switchIcon(Context context, int index) {
        if (index < 0 || index >= LAUNCHER_ALIASES.length) return;

        PackageManager pm = context.getPackageManager();
        String packageName = context.getPackageName();

        // Enable the selected one
        pm.setComponentEnabledSetting(
                new ComponentName(packageName, LAUNCHER_ALIASES[index]),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
        );

        // Disable others
        for (int i = 0; i < LAUNCHER_ALIASES.length; i++) {
            if (i != index) {
                pm.setComponentEnabledSetting(
                        new ComponentName(packageName, LAUNCHER_ALIASES[i]),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                );
            }
        }

        saveSelectedIcon(context, index);
    }

    public static int getSelectedIcon(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_SELECTED_ICON, 0);
    }

    private static void saveSelectedIcon(Context context, int index) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_SELECTED_ICON, index).apply();
    }
}
