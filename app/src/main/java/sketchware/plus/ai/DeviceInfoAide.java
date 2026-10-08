package sketchware.plus.ai;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Utility helper that collects real-time device information (date, time, hardware model,
 * Android OS version, display metrics, battery, and memory/storage statistics) for the SK Assistant.
 */
public class DeviceInfoAide {

    /**
     * Gathers device metadata based on the requested category.
     * @param context App context.
     * @param args Arguments containing optional "category" (all, time, hardware, display, battery, memory).
     * @return JSONObject containing the requested device information.
     */
    public static JSONObject getDeviceInfo(Context context, JSONObject args) {
        JSONObject result = new JSONObject();
        String category = args != null ? args.optString("category", "all").toLowerCase().trim() : "all";

        try {
            boolean includeAll = category.equals("all");

            if (includeAll || category.contains("time") || category.contains("date")) {
                result.put("time_and_date", getTimeAndDateInfo());
            }

            if (includeAll || category.contains("hardware") || category.contains("system") || category.contains("os")) {
                result.put("hardware_and_os", getHardwareAndOsInfo());
            }

            if (includeAll || category.contains("display") || category.contains("screen")) {
                result.put("display", getDisplayInfo(context));
            }

            if (includeAll || category.contains("battery") || category.contains("power")) {
                result.put("battery", getBatteryInfo(context));
            }

            if (includeAll || category.contains("memory") || category.contains("storage") || category.contains("ram")) {
                result.put("memory_and_storage", getMemoryAndStorageInfo(context));
            }

            result.put("status", "success");
        } catch (Exception e) {
            try {
                result.put("status", "error");
                result.put("message", e.getMessage());
            } catch (Exception ignored) {}
        }

        return result;
    }

    private static JSONObject getTimeAndDateInfo() throws Exception {
        JSONObject timeObj = new JSONObject();
        long now = System.currentTimeMillis();
        Date currentDate = new Date(now);

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss (z)", Locale.US);
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.US);

        TimeZone tz = TimeZone.getDefault();

        timeObj.put("current_date", dateFormat.format(currentDate));
        timeObj.put("current_time", timeFormat.format(currentDate));
        timeObj.put("day_of_week", dayFormat.format(currentDate));
        timeObj.put("timezone_id", tz.getID());
        timeObj.put("timezone_display", tz.getDisplayName(false, TimeZone.SHORT, Locale.US));
        timeObj.put("gmt_offset_hours", tz.getOffset(now) / (1000.0 * 60.0 * 60.0));
        timeObj.put("uptime_seconds", SystemClock.elapsedRealtime() / 1000);

        return timeObj;
    }

    private static JSONObject getHardwareAndOsInfo() throws Exception {
        JSONObject sysObj = new JSONObject();

        sysObj.put("manufacturer", Build.MANUFACTURER);
        sysObj.put("brand", Build.BRAND);
        sysObj.put("model", Build.MODEL);
        sysObj.put("device_name", Build.DEVICE);
        sysObj.put("product_name", Build.PRODUCT);
        sysObj.put("android_version", Build.VERSION.RELEASE);
        sysObj.put("api_level", Build.VERSION.SDK_INT);

        JSONArray abis = new JSONArray();
        if (Build.SUPPORTED_ABIS != null) {
            for (String abi : Build.SUPPORTED_ABIS) {
                abis.put(abi);
            }
        }
        sysObj.put("supported_abis", abis);

        return sysObj;
    }

    private static JSONObject getDisplayInfo(Context context) throws Exception {
        JSONObject displayObj = new JSONObject();
        if (context == null) return displayObj;

        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        if (wm != null && wm.getDefaultDisplay() != null) {
            wm.getDefaultDisplay().getMetrics(dm);
        }

        int widthPx = dm.widthPixels;
        int heightPx = dm.heightPixels;
        float density = dm.density;

        displayObj.put("width_pixels", widthPx);
        displayObj.put("height_pixels", heightPx);
        displayObj.put("density_dpi", dm.densityDpi);
        displayObj.put("density_scale", density);
        displayObj.put("width_dp", (int) (widthPx / (density > 0 ? density : 1)));
        displayObj.put("height_dp", (int) (heightPx / (density > 0 ? density : 1)));
        displayObj.put("orientation", widthPx > heightPx ? "Landscape" : "Portrait");

        return displayObj;
    }

    private static JSONObject getBatteryInfo(Context context) throws Exception {
        JSONObject batteryObj = new JSONObject();
        if (context == null) return batteryObj;

        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);

        int level = -1;
        int scale = -1;
        int status = -1;
        int chargePlug = -1;

        if (batteryStatus != null) {
            level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        }

        float batteryPct = (level >= 0 && scale > 0) ? ((float) level / (float) scale) * 100f : -1f;
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;

        String plugState = "Discharging";
        if (isCharging) {
            if (chargePlug == BatteryManager.BATTERY_PLUGGED_AC) plugState = "AC Charging";
            else if (chargePlug == BatteryManager.BATTERY_PLUGGED_USB) plugState = "USB Charging";
            else if (chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS) plugState = "Wireless Charging";
            else plugState = "Charging";
        }

        batteryObj.put("level_percent", String.format(Locale.US, "%.1f%%", batteryPct));
        batteryObj.put("is_charging", isCharging);
        batteryObj.put("power_source", plugState);

        return batteryObj;
    }

    private static JSONObject getMemoryAndStorageInfo(Context context) throws Exception {
        JSONObject memObj = new JSONObject();

        // Memory RAM
        if (context != null) {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(memInfo);
                memObj.put("ram_total_mb", memInfo.totalMem / (1024 * 1024));
                memObj.put("ram_available_mb", memInfo.availMem / (1024 * 1024));
                memObj.put("ram_low_memory", memInfo.lowMemory);
            }
        }

        // Internal Storage
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long blockSize = stat.getBlockSizeLong();
            long totalBlocks = stat.getBlockCountLong();
            long availableBlocks = stat.getAvailableBlocksLong();

            memObj.put("storage_total_mb", (totalBlocks * blockSize) / (1024 * 1024));
            memObj.put("storage_available_mb", (availableBlocks * blockSize) / (1024 * 1024));
        } catch (Exception ignored) {}

        return memObj;
    }
}
