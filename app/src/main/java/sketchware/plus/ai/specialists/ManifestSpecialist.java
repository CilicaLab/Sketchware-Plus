package sketchware.plus.ai.specialists;

import android.app.Activity;
import android.content.Context;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

import a.a.a.Ix;
import a.a.a.eC;
import a.a.a.jC;
import a.a.a.jq;
import a.a.a.yq;
import sketchware.plus.ai.ProjectSnapshot;
import sketchware.plus.ai.SkAssistantFragment;
import sketchware.plus.util.library.BuiltInLibraryManager;
import sketchware.plus.utility.FileUtil;
import sketchware.plus.utility.GsonUtils;
import mod.hey.studios.util.Helper;

public class ManifestSpecialist extends BaseSpecialist {

    public ManifestSpecialist(SkAssistantFragment fragment) {
        super(fragment);
    }

    @Override
    public void process(String prompt, String reasoning) {
        setStatus("Analyzing manifest requirements...");
        Context androidContext = getContext();
        if (androidContext == null) return;

        jC.projectOperationsExecutor.execute(() -> {
            try { Thread.sleep(800); } catch (Exception ignored) {}
            String contextStr = fragment.gatherScopedContext(androidContext, "SYSTEM_MANIFEST", prompt);

            String systemPrompt = "You are the Sketchware Plus Manifest Specialist.\n" +
                    "Help the user add permissions or manifest injections.\n" +
                    "For permissions, use ADD_PERMISSION with the full android.permission string.\n\n" +
                    "RESPONSE CONTRACT:\n" +
                    "{\n" +
                    "  \"category\": \"SYSTEM_MANIFEST\",\n" +
                    "  \"actions\": [ {\"type\":\"ADD_PERMISSION\",\"name\":\"...\"} ]\n" +
                    "}\n" +
                    "Reasoning: " + reasoning;

            Activity activity = fragment.getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> fragment.executeRequest(systemPrompt, prompt, contextStr, "SYSTEM_MANIFEST"));
            }
        });
    }

    @Override
    public void handleAction(JSONObject action) throws JSONException {
        if ("ADD_PERMISSION".equals(action.optString("type"))) {
            applyPermission(action.getString("name"));
        }
    }

    public void applyPermission(String permission) {
        new MaterialAlertDialogBuilder(getContext())
                .setTitle("Add Permission")
                .setMessage("Add '" + permission + "' to manifest?")
                .setPositiveButton("Add", (dialog, which) -> {
                    String xmlName = projectFile.getXmlName();
                    fragment.undoSnapshot = new ProjectSnapshot(scId, xmlName);
                    eC dataManager = jC.a(scId);

                    int permMask = 0;
                    if (permission.contains("CALL_PHONE")) permMask = jq.PERMISSION_CALL_PHONE;
                    else if (permission.contains("INTERNET")) permMask = jq.PERMISSION_INTERNET;
                    else if (permission.contains("VIBRATE")) permMask = jq.PERMISSION_VIBRATE;
                    else if (permission.contains("ACCESS_NETWORK_STATE")) permMask = jq.PERMISSION_ACCESS_NETWORK_STATE;
                    else if (permission.contains("CAMERA")) permMask = jq.PERMISSION_CAMERA;
                    else if (permission.contains("READ_EXTERNAL_STORAGE")) permMask = jq.PERMISSION_READ_EXTERNAL_STORAGE;
                    else if (permission.contains("WRITE_EXTERNAL_STORAGE")) permMask = jq.PERMISSION_WRITE_EXTERNAL_STORAGE;
                    else if (permission.contains("RECORD_AUDIO")) permMask = jq.PERMISSION_RECORD_AUDIO;
                    else if (permission.contains("BLUETOOTH")) permMask = jq.PERMISSION_BLUETOOTH;
                    else if (permission.contains("BLUETOOTH_ADMIN")) permMask = jq.PERMISSION_BLUETOOTH_ADMIN;
                    else if (permission.contains("ACCESS_FINE_LOCATION")) permMask = jq.PERMISSION_ACCESS_FINE_LOCATION;

                    if (permMask != 0) {
                        dataManager.l.addPermission(permMask);
                    }

                    yq workspace = new yq(getContext(), scId);
                    BuiltInLibraryManager builtInLibManager = new BuiltInLibraryManager(scId);
                    Ix ix = new Ix(dataManager.l, jC.b(scId).c, builtInLibManager);
                    ix.setYq(workspace);
                    workspace.a("AndroidManifest.xml", ix.a());

                    fragment.addSystemMessage("Permission added.");
                    fragment.setUndoVisible(true);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public void applyManifestInjection(String type, String value) {
        new MaterialAlertDialogBuilder(getContext())
                .setTitle("Manifest Injection")
                .setMessage("Add " + type + ": " + value + "?")
                .setPositiveButton("Add", (dialog, which) -> {
                    try {
                        if ("ATTR".equals(type)) {
                            String path = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + scId + "/Injection/androidmanifest/attributes.json";
                            ArrayList<HashMap<String, Object>> data = new ArrayList<>();
                            if (FileUtil.isExistFile(path)) {
                                data = GsonUtils.getGson().fromJson(FileUtil.readFile(path), Helper.TYPE_MAP_LIST);
                            }

                            HashMap<String, Object> item = new HashMap<>();
                            item.put("name", "_application_attrs"); 
                            item.put("value", value);
                            data.add(item);
                            FileUtil.writeFile(path, GsonUtils.getGson().toJson(data));
                        } else if ("COMPONENT".equals(type)) {
                            String path = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + scId + "/Injection/androidmanifest/app_components.txt";
                            String content = FileUtil.isExistFile(path) ? FileUtil.readFile(path) : "";
                            content += "\n" + value;
                            FileUtil.writeFile(path, content);
                        }
                        fragment.addSystemMessage("Manifest injection added.");
                    } catch (Exception e) {
                        fragment.addSystemMessage("Error injecting manifest: " + e.getMessage());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
