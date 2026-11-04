package sketchware.plus.security;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;

import mod.jbk.build.BuildProgressReceiver;
import sketchware.plus.utility.FileUtil;

public class SecurityGuardHandler {

    private static final Type MAP_TYPE = new TypeToken<HashMap<String, Object>>(){}.getType();

    private final String configPath;

    public SecurityGuardHandler(String scId) {
        configPath = FileUtil.getExternalStorageDir().concat("/.sketchware/data/" + scId + "/security_guard");
        if (!FileUtil.isExistFile(configPath)) {
            FileUtil.writeFile(configPath, getDefaultConfig());
        }
    }

    private String getDefaultConfig() {
        HashMap<String, Object> config = new HashMap<>();
        config.put("package_lock", false);
        config.put("signature_check", false);
        config.put("auto_signature_sync", true);
        config.put("expected_signature", "");
        config.put("root_detection", false);
        config.put("emulator_detection", false);
        config.put("anti_debug", false);
        return new Gson().toJson(config);
    }

    private HashMap<String, Object> getConfig() {
        try {
            if (FileUtil.isExistFile(configPath)) {
                HashMap<String, Object> m = new Gson().fromJson(FileUtil.readFile(configPath), MAP_TYPE);
                if (m != null) return m;
            }
        } catch (Exception e) {
            android.util.Log.e("SecurityGuard", "config read failed: " + e, e);
        }
        return new HashMap<>();
    }

    public boolean isFeatureEnabled(String key) {
        Object val = getConfig().get(key);
        return val instanceof Boolean && (Boolean) val;
    }

    public String getFeatureString(String key) {
        Object val = getConfig().get(key);
        return val instanceof String ? (String) val : "";
    }

    public void setFeatureValue(String key, Object value) {
        HashMap<String, Object> config = getConfig();
        config.put(key, value);
        FileUtil.writeFile(configPath, new Gson().toJson(config));
    }

    public void reportProgress(BuildProgressReceiver receiver) {
        if (isFeatureEnabled("package_lock")) reportWithDelay(receiver, "Locking package identity...");
        if (isFeatureEnabled("signature_check")) reportWithDelay(receiver, "Applying anti-cloning shield...");
        if (isFeatureEnabled("root_detection")) reportWithDelay(receiver, "Injecting root detection...");
        if (isFeatureEnabled("emulator_detection")) reportWithDelay(receiver, "Injecting emulator protection...");
        if (isFeatureEnabled("anti_debug")) reportWithDelay(receiver, "Hardening anti-debugger...");
    }

    private void reportWithDelay(BuildProgressReceiver receiver, String message) {
        receiver.onProgress(message, 2);
        try {
            Thread.sleep(400);
        } catch (InterruptedException ignored) {}
    }
}