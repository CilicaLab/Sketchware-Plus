package sketchware.plus.utility;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.Map;

import sketchware.plus.SketchApplication;

public class BuildStatsManager {
    private static final String PREF_NAME = "build_stats";
    private static final String KEY_STATS = "last_stats";
    private static final String KEY_TOTAL_TIME = "total_time";
    private static final String KEY_PEAK_MEM = "peak_memory";
    private static final String KEY_RESULT = "result";

    private static final Map<String, Long> lastBuildStats = new HashMap<>();
    private static long lastBuildTotalTime = 0;
    private static long peakMemoryMB = 0;
    private static String lastBuildResult = "None";
    private static boolean isLoaded = false;

    private static synchronized void ensureLoaded() {
        if (!isLoaded) {
            load();
            isLoaded = true;
        }
    }

    public static synchronized void recordStat(String key, long durationMs) {
        ensureLoaded();
        lastBuildStats.put(key, durationMs);
        updateMemoryStats();
        save();
    }

    public static synchronized void updateMemoryStats() {
        ensureLoaded();
        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        if (used > peakMemoryMB) {
            peakMemoryMB = used;
            save();
        }
    }

    public static synchronized void setTotalTime(long totalTimeMs) {
        ensureLoaded();
        lastBuildTotalTime = totalTimeMs;
        save();
    }

    public static synchronized void setResult(String result) {
        ensureLoaded();
        lastBuildResult = result;
        save();
    }

    public static synchronized Map<String, Long> getLastBuildStats() {
        ensureLoaded();
        return new HashMap<>(lastBuildStats);
    }

    public static synchronized long getLastBuildTotalTime() {
        ensureLoaded();
        return lastBuildTotalTime;
    }

    public static synchronized long getPeakMemoryMB() {
        ensureLoaded();
        return peakMemoryMB;
    }

    public static synchronized String getLastBuildResult() {
        ensureLoaded();
        return lastBuildResult;
    }

    public static void clear() {
        lastBuildStats.clear();
        lastBuildTotalTime = 0;
        peakMemoryMB = 0;
        save();
    }

    private static void save() {
        Context context = SketchApplication.getContext();
        if (context == null) return;

        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        pref.edit()
                .putString(KEY_STATS, new Gson().toJson(lastBuildStats))
                .putLong(KEY_TOTAL_TIME, lastBuildTotalTime)
                .putLong(KEY_PEAK_MEM, peakMemoryMB)
                .putString(KEY_RESULT, lastBuildResult)
                .apply();
    }

    private static void load() {
        Context context = SketchApplication.getContext();
        if (context == null) return;

        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        lastBuildTotalTime = pref.getLong(KEY_TOTAL_TIME, 0);
        peakMemoryMB = pref.getLong(KEY_PEAK_MEM, 0);
        lastBuildResult = pref.getString(KEY_RESULT, "None");

        String statsJson = pref.getString(KEY_STATS, "{}");
        try {
            Map<String, Long> loaded = new Gson().fromJson(statsJson, new TypeToken<HashMap<String, Long>>() {}.getType());
            if (loaded != null) {
                lastBuildStats.clear();
                lastBuildStats.putAll(loaded);
            }
        } catch (Exception ignored) {}
    }
}
