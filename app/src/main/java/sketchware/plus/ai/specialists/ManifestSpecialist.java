package sketchware.plus.ai.specialists;

import android.app.Activity;
import android.content.Context;

import org.json.JSONObject;

import java.util.ArrayList;

import a.a.a.Ix;
import a.a.a.eC;
import a.a.a.jC;
import a.a.a.jq;
import a.a.a.yq;
import mod.hey.studios.util.Helper;
import sketchware.plus.ai.ProjectSnapshot;
import sketchware.plus.ai.SkAssistantFragment;
import sketchware.plus.util.library.BuiltInLibraryManager;
import sketchware.plus.utility.FilePathUtil;
import sketchware.plus.utility.FileUtil;
import sketchware.plus.utility.GsonUtils;

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
                    "Help the user inspect, read, and modify AndroidManifest.xml and its sections.\n" +
                    "To modify AndroidManifest.xml, use the `add_xml_patch` tool targeting `xmlName: \"AndroidManifest.xml\"` (which registers the command in the XML Command Manager).\n\n" +
                    "Reasoning: " + reasoning;

            Activity activity = fragment.getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> fragment.executeRequest(systemPrompt, prompt, contextStr, "SYSTEM_MANIFEST"));
            }
        });
    }

    @Override
    public void handleAction(JSONObject action) {
        // Read-only specialist
    }

    public void applyPermission(String permission) {
        try {
            String xmlName = getProjectFile().getXmlName();
            fragment.undoSnapshot = new ProjectSnapshot(getScId(), xmlName);
            eC dataManager = jC.a(getScId());

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

            try {
                FilePathUtil pathUtil = new FilePathUtil();
                String permissionFilePath = pathUtil.getPathPermission(getScId());
                ArrayList<String> permList = new ArrayList<>();
                if (FileUtil.isExistFile(permissionFilePath)) {
                    String existingContent = FileUtil.readFile(permissionFilePath);
                    if (!existingContent.trim().isEmpty()) {
                        permList = GsonUtils.getGson().fromJson(existingContent, Helper.TYPE_STRING);
                    }
                }
                if (permList == null) {
                    permList = new ArrayList<>();
                }
                if (!permList.contains(permission)) {
                    permList.add(permission);
                    FileUtil.writeFile(permissionFilePath, GsonUtils.getGson().toJson(permList));
                }
            } catch (Exception ex) {
                log("Failed to write to Permission Manager json: " + ex.getMessage());
            }

            yq workspace = new yq(getContext(), getScId());
            BuiltInLibraryManager builtInLibManager = new BuiltInLibraryManager(getScId());
            Ix ix = new Ix(dataManager.l, jC.b(getScId()).c, builtInLibManager);
            ix.setYq(workspace);
            workspace.a("AndroidManifest.xml", ix.a());

            fragment.addSystemMessage("Permission '" + permission + "' added automatically.");
            fragment.setUndoVisible(true);
        } catch (Exception e) {
            fragment.addSystemMessage("Error adding permission: " + e.getMessage());
        }
    }
}
