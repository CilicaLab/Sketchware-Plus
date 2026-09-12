package sketchware.plus.utility;

import android.os.Environment;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Persists activity data (date -> activity count) to a JSON file.
 */
public class ActivityTracker {
    public static final String DEFAULT_FILE_PATH = Environment.getExternalStorageDirectory() + "/.sketchware/data/system/activity.json";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    /**
     * Increments activity count for today in the specified file.
     */
    public static void recordActivity(String filePath) {
        try {
            Map<String, Integer> data = loadActivity(filePath);
            String today = DATE_FORMAT.format(new Date());
            
            int currentCount = data.containsKey(today) ? data.get(today) : 0;
            data.put(today, currentCount + 1);
            
            saveActivity(filePath, data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads the full activity history from the specified file.
     */
    public static Map<String, Integer> loadActivity(String filePath) {
        Map<String, Integer> result = new HashMap<>();
        try {
            if (FileUtil.isExistFile(filePath)) {
                String content = FileUtil.readFile(filePath);
                if (!content.trim().isEmpty()) {
                    JSONObject json = new JSONObject(content);
                    Iterator<String> keys = json.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        result.put(key, json.getInt(key));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Internal method to save the map as a JSON object.
     */
    private static void saveActivity(String filePath, Map<String, Integer> data) {
        try {
            JSONObject json = new JSONObject();
            for (Map.Entry<String, Integer> entry : data.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }
            FileUtil.writeFile(filePath, json.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
