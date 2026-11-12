package sketchware.plus.ai.specialists;

import android.app.Activity;
import android.content.Context;
import android.os.Looper;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import a.a.a.jC;
import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import sketchware.plus.ai.SkAssistantFragment;
import sketchware.plus.utility.GsonUtils;

public class LibrarySpecialist extends BaseSpecialist {

    public LibrarySpecialist(SkAssistantFragment fragment) {
        super(fragment);
    }

    @Override
    public void process(String prompt, String reasoning) {
        setStatus("Checking library dependencies...");
        Context ctx = getContext();
        if (ctx == null) return;

        jC.projectOperationsExecutor.execute(() -> {
            String contextStr = fragment.gatherScopedContext(ctx, "LIBRARY_MANAGER", prompt);
            List<String> enabled = namesOf(LocalLibrariesUtil.getLocalLibraries(getScId()));

            String systemPrompt = "You are the Sketchware Plus Library Manager. You can enable and disable LOCAL libraries.\n" +
                    "Pick from ENABLED LOCAL LIBRARIES to disable, or AVAILABLE TO ENABLE to enable.\n" +
                    "Use the EXACT folder name, including version.\n" +
                    "Only refuse built-in libs (AppCompat, Firebase, etc.), you can't touch those.\n\n" +
                    "Reply with JSON only. type is ENABLE_LOCAL_LIBRARY or DISABLE_LOCAL_LIBRARY:\n" +
                    "{\"category\":\"LIBRARY_MANAGER\",\"summary\":\"Disabling local library: x\",\"actions\":[{\"type\":\"DISABLE_LOCAL_LIBRARY\",\"name\":\"x\"}]}\n" +
                    "Reasoning: " + reasoning;

            Activity activity = fragment.getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> fragment.executeRequest(systemPrompt, prompt, contextStr, "LIBRARY_MANAGER"));
            }
        });
    }

    @Override
    public void handleAction(JSONObject action) {
        String name = action.optString("name", action.optString("libraryName", ""));
        if (name.isEmpty()) return;

        switch (action.optString("type").toUpperCase(Locale.ROOT)) {
            case "ENABLE_LOCAL_LIBRARY":
                applyLocalLibrary(name);
                break;
            case "DISABLE_LOCAL_LIBRARY":
                disableLocalLibrary(name);
                break;
            case "ENABLE_LIBRARY":
                applyLibrary(name);
                break;
        }
    }

    public void applyLibrary(String name) {
        fragment.addSystemMessage("I can't enable or disable built-in libraries (like " + name + "). Manage them manually in the Library Manager.");
    }

    public void applyLocalLibrary(String libName) {
        toggle(libName, true);
    }

    public void disableLocalLibrary(String libName) {
        toggle(libName, false);
    }

    private void toggle(String query, boolean enable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            jC.projectOperationsExecutor.execute(() -> toggle(query, enable));
            return;
        }
        try {
            ArrayList<HashMap<String, Object>> libs = LocalLibrariesUtil.getLocalLibraries(getScId());
            List<String> pool = enable ? LocalLibrariesUtil.getAllLocalLibraryNames() : namesOf(libs);

            String name = match(pool, query);
            if (name == null) {
                fragment.addSystemMessage(enable
                        ? "No local library matching '" + query + "' in /.sketchware/libs/local_libs/"
                        : "No enabled local library matching '" + query + "'.");
                return;
            }

            int idx = namesOf(libs).indexOf(name);
            if (enable == (idx >= 0)) {
                fragment.addSystemMessage("Local library '" + name + "' is already " + (enable ? "enabled." : "disabled."));
                return;
            }

            if (enable) libs.add(LocalLibrariesUtil.createLibraryMap(name, null));
            else libs.remove(idx);

            LocalLibrariesUtil.rewriteLocalLibFile(getScId(), GsonUtils.getGson().toJson(libs));
            fragment.addSystemMessage("Local library '" + name + "' " + (enable ? "enabled" : "disabled") + " successfully.");
            fragment.setUndoVisible(true);
            fragment.refreshDesigner();
        } catch (Exception e) {
            fragment.addSystemMessage("Error " + (enable ? "enabling" : "disabling") + " local library: " + e.getMessage());
        }
    }

    /** Exact (case-insensitive) > starts-with > contains. */
    private static String match(List<String> names, String query) {
        String q = query.toLowerCase(Locale.ROOT);
        String starts = null, contains = null;
        for (String n : names) {
            String l = n.toLowerCase(Locale.ROOT);
            if (l.equals(q)) return n;
            if (starts == null && l.startsWith(q)) starts = n;
            if (contains == null && l.contains(q)) contains = n;
        }
        return starts != null ? starts : contains;
    }

    private static List<String> namesOf(List<HashMap<String, Object>> libs) {
        List<String> out = new ArrayList<>();
        for (HashMap<String, Object> lib : libs) {
            Object n = lib.get("name");
            if (n instanceof String) out.add((String) n);
        }
        return out;
    }
}