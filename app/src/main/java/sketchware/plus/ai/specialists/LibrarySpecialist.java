package sketchware.plus.ai.specialists;

import android.app.Activity;
import android.content.Context;

import com.besome.sketch.beans.ProjectLibraryBean;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.iC;
import a.a.a.jC;
import sketchware.plus.ai.ProjectSnapshot;
import sketchware.plus.ai.SkAssistantFragment;
import sketchware.plus.utility.FileUtil;
import mod.agus.jcoderz.editor.manage.library.locallibrary.ManageLocalLibrary;
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
            String contextStr = fragment.gatherScopedContext(androidContext, "LIBRARY_EDIT", prompt);

            String systemPrompt = "You are the Sketchware Plus Library Manager.\n" +
                    "Identify which Sketchware built-in library the user wants to enable.\n" +
                    "AVAILABLE LIBRARIES: appcompat, firebase, admob, googlemap.\n\n" +
                    "RESPONSE CONTRACT:\n" +
                    "{\n" +
                    "  \"category\": \"LIBRARY_EDIT\",\n" +
                    "  \"summary\": \"Enabling library: [name]\",\n" +
                    "  \"actions\": [ {\"type\":\"ENABLE_LIBRARY\",\"name\":\"...\"} ]\n" +
                    "}\n" +
                    "Reasoning: " + reasoning;

            Activity activity = fragment.getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> fragment.executeRequest(systemPrompt, prompt, contextStr, "LIBRARY_EDIT"));
            }
        });
    }

    @Override
    public void handleAction(JSONObject action) throws JSONException {
        if ("ENABLE_LIBRARY".equals(action.optString("type"))) {
            applyLibrary(action.getString("name"));
        }
    }

    public void applyLibrary(String name) {
        new MaterialAlertDialogBuilder(getContext())
                .setTitle("Enable Library")
                .setMessage("Enable '" + name + "' library?")
                .setPositiveButton("Enable", (dialog, which) -> {
                    String xmlName = projectFile.getXmlName();
                    fragment.undoSnapshot = new ProjectSnapshot(scId, xmlName);
                    iC libraryManager = jC.c(scId);
                    ProjectLibraryBean lib = null;
                    if (name.equalsIgnoreCase("appcompat")) lib = libraryManager.c();
                    else if (name.equalsIgnoreCase("firebase")) lib = libraryManager.d();
                    else if (name.equalsIgnoreCase("admob")) lib = libraryManager.b();
                    else if (name.equalsIgnoreCase("googlemap")) lib = libraryManager.e();

                    if (lib != null) {
                        lib.useYn = "Y";
                        libraryManager.k();
                        fragment.addSystemMessage("Library '" + name + "' enabled.");
                        fragment.setUndoVisible(true);
                    } else {
                        fragment.addSystemMessage("Unknown library: " + name);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public void applyLocalLibrary(String libName) {
        new MaterialAlertDialogBuilder(getContext())
                .setTitle("Enable Local Library")
                .setMessage("Enable local library '" + libName + "'?")
                .setPositiveButton("Enable", (dialog, which) -> {
                    try {
                        String localLibsDir = FileUtil.getExternalStorageDir() + "/.sketchware/libs/local_libs";
                        File libFolder = new File(localLibsDir, libName);
                        if (!libFolder.exists()) {
                            fragment.addSystemMessage("Local library folder not found: " + libName);
                            return;
                        }

                        ManageLocalLibrary manager = new ManageLocalLibrary(scId);
                        boolean alreadyEnabled = false;
                        for (HashMap<String, Object> lib : manager.list) {
                            if (libName.equals(lib.get("name"))) {
                                alreadyEnabled = true;
                                break;
                            }
                        }

                        if (!alreadyEnabled) {
                            HashMap<String, Object> newLib = new HashMap<>();
                            newLib.put("name", libName);
                            String pkgName = libName; 
                            File manifest = new File(libFolder, "AndroidManifest.xml");
                            if (manifest.exists()) {
                                String content = FileUtil.readFile(manifest.getAbsolutePath());
                                Pattern p = Pattern.compile("package=\"([^\"]+)\"");
                                Matcher m = p.matcher(content);
                                if (m.find()) pkgName = m.group(1);
                            }
                            newLib.put("packageName", pkgName);
                            newLib.put("jarPath", new File(libFolder, "classes.jar").getAbsolutePath());
                            newLib.put("dexPath", new File(libFolder, "classes.dex").getAbsolutePath());
                            newLib.put("resPath", new File(libFolder, "res").getAbsolutePath());
                            newLib.put("assetsPath", new File(libFolder, "assets").getAbsolutePath());

                            manager.list.add(newLib);
                            String configPath = new FilePathUtil().getPathLocalLibrary(scId);
                            FileUtil.writeFile(configPath, GsonUtils.getGson().toJson(manager.list));

                            fragment.addSystemMessage("Local library '" + libName + "' enabled.");
                            fragment.setUndoVisible(true);
                        } else {
                            fragment.addSystemMessage("Local library '" + libName + "' is already enabled.");
                        }
                    } catch (Exception e) {
                        fragment.addSystemMessage("Error enabling local library: " + e.getMessage());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
