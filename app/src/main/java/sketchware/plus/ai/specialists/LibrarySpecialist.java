package sketchware.plus.ai.specialists;

import android.app.Activity;
import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.iC;
import a.a.a.jC;
import sketchware.plus.ai.ProjectSnapshot;
import sketchware.plus.ai.SkAssistantFragment;
import sketchware.plus.utility.FileUtil;
import mod.agus.jcoderz.editor.manage.library.locallibrary.ManageLocalLibrary;
import dev.aldi.sayuti.editor.manage.LocalLibrary;
import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import sketchware.plus.utility.FilePathUtil;
import sketchware.plus.utility.GsonUtils;

public class LibrarySpecialist extends BaseSpecialist {

    public LibrarySpecialist(SkAssistantFragment fragment) {
        super(fragment);
    }

    @Override
    public void process(String prompt, String reasoning) {
        setStatus("Checking library dependencies...");
        Context androidContext = getContext();
        if (androidContext == null) return;

        jC.projectOperationsExecutor.execute(() -> {
            try { Thread.sleep(800); } catch (Exception ignored) {}
            String contextStr = fragment.gatherScopedContext(androidContext, "LIBRARY_MANAGER", prompt);

            String systemPrompt = "You are the Sketchware Plus Library Manager.\n" +
                    "Identify which LOCAL library the user wants to enable or disable by looking at the 'AVAILABLE LOCAL LIBRARIES' list in the context.\n" +
                    "IMPORTANT: Provide the EXACT folder name from that list, including any version numbers.\n" +
                    "NOTE: You CANNOT manage built-in libraries (AppCompat, Firebase, etc.) as you don't have access to them.\n\n" +
                    "RESPONSE CONTRACT:\n" +
                    "{\n" +
                    "  \"category\": \"LIBRARY_MANAGER\",\n" +
                    "  \"summary\": \"[Enabling/Disabling] local library: [name]\",\n" +
                    "  \"actions\": [ {\"type\":\"[ENABLE_LOCAL_LIBRARY/DISABLE_LOCAL_LIBRARY]\",\"name\":\"...\"} ]\n" +
                    "}\n" +
                    "Reasoning: " + reasoning;

            Activity activity = fragment.getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> fragment.executeRequest(systemPrompt, prompt, contextStr, "LIBRARY_MANAGER"));
            }
        });
    }

    @Override
    public void handleAction(JSONObject action) throws JSONException {
        String type = action.optString("type");
        String name = action.getString("name");
        if ("ENABLE_LOCAL_LIBRARY".equals(type)) {
            applyLocalLibrary(name);
        } else if ("DISABLE_LOCAL_LIBRARY".equals(type)) {
            disableLocalLibrary(name);
        } else if ("ENABLE_LIBRARY".equals(type)) {
            applyLibrary(name);
        }
    }

    public void applyLibrary(String name) {
        fragment.addSystemMessage("I'm sorry, I cannot enable or disable built-in libraries (like " + name + ") because I do not have access to them. Please manage them manually through the Library Manager.");
    }

    public void applyLocalLibrary(String libName) {
        try {
            List<LocalLibrary> allAvailable = LocalLibrariesUtil.getAllLocalLibraries();
            LocalLibrary target = null;
            
            // 1. Try exact match (case insensitive)
            for (LocalLibrary ll : allAvailable) {
                if (ll.getName().equalsIgnoreCase(libName)) {
                    target = ll;
                    break;
                }
            }

            // 2. Try 'starts with' match
            if (target == null) {
                for (LocalLibrary ll : allAvailable) {
                    if (ll.getName().toLowerCase().startsWith(libName.toLowerCase())) {
                        target = ll;
                        break;
                    }
                }
            }

            // 3. Try 'contains' match
            if (target == null) {
                for (LocalLibrary ll : allAvailable) {
                    if (ll.getName().toLowerCase().contains(libName.toLowerCase())) {
                        target = ll;
                        break;
                    }
                }
            }

            if (target == null) {
                fragment.addSystemMessage("Could not find a local library matching '" + libName + "' in /.sketchware/libs/local_libs/");
                return;
            }

            String actualName = target.getName();
            if (!actualName.equals(libName)) {
                fragment.addSystemMessage("Auto-matched library: " + actualName);
            }
            
            ArrayList<HashMap<String, Object>> enabledLibs = LocalLibrariesUtil.getLocalLibraries(getScId());
            boolean alreadyEnabled = false;
            for (HashMap<String, Object> lib : enabledLibs) {
                if (actualName.equals(lib.get("name"))) {
                    alreadyEnabled = true;
                    break;
                }
            }

            if (!alreadyEnabled) {
                HashMap<String, Object> newLib = LocalLibrariesUtil.createLibraryMap(actualName, null);
                enabledLibs.add(newLib);
                LocalLibrariesUtil.rewriteLocalLibFile(getScId(), GsonUtils.getGson().toJson(enabledLibs));

                fragment.addSystemMessage("Local library '" + actualName + "' enabled successfully.");
                fragment.setUndoVisible(true);
            } else {
                fragment.addSystemMessage("Local library '" + actualName + "' is already enabled in this project.");
            }
        } catch (Exception e) {
            fragment.addSystemMessage("Error enabling local library: " + e.getMessage());
        }
    }

    public void disableLocalLibrary(String libName) {
        try {
            ArrayList<HashMap<String, Object>> enabledLibs = LocalLibrariesUtil.getLocalLibraries(getScId());
            boolean removed = false;
            String actualName = "";

            for (int i = 0; i < enabledLibs.size(); i++) {
                HashMap<String, Object> lib = enabledLibs.get(i);
                String currentName = (String) lib.get("name");
                if (currentName.equalsIgnoreCase(libName) || currentName.toLowerCase().contains(libName.toLowerCase())) {
                    actualName = currentName;
                    enabledLibs.remove(i);
                    removed = true;
                    break;
                }
            }

            if (removed) {
                LocalLibrariesUtil.rewriteLocalLibFile(getScId(), GsonUtils.getGson().toJson(enabledLibs));
                fragment.addSystemMessage("Local library '" + actualName + "' disabled successfully.");
                fragment.setUndoVisible(true);
            } else {
                fragment.addSystemMessage("Local library matching '" + libName + "' is not currently enabled.");
            }
        } catch (Exception e) {
            fragment.addSystemMessage("Error disabling local library: " + e.getMessage());
        }
    }
}
