package sketchware.plus.ai.specialists;

import android.app.Activity;
import android.content.Context;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import a.a.a.Ix;
import a.a.a.eC;
import a.a.a.jC;
import a.a.a.jq;
import a.a.a.yq;
import mod.hey.studios.util.Helper;
import sketchware.plus.ai.ProjectSnapshot;
import sketchware.plus.ai.SkAssistantFragment;
import sketchware.plus.ai.SourceCodeAide;
import sketchware.plus.util.library.BuiltInLibraryManager;
import sketchware.plus.utility.FilePathUtil;
import sketchware.plus.utility.FileUtil;
import sketchware.plus.utility.GsonUtils;

public class ManifestSpecialist extends BaseSpecialist {

    private final List<String> stepHistory = new ArrayList<>();

    public ManifestSpecialist(SkAssistantFragment fragment) {
        super(fragment);
        log("[ManifestSpecialist] Initialized successfully:).");
    }

    public List<String> getStepHistory() {
        return stepHistory;
    }

    private String formatJson(JSONObject json) {
        if (json == null) return "null";
        try {
            return json.toString(2);
        } catch (Exception e) {
            return json.toString();
        }
    }

    @Override
    public void process(String prompt, String reasoning) {
        log("================================================================================");
        log("[ManifestSpecialist.process] Triggered.");
        log("[ManifestSpecialist.process] Input Prompt:\n" + prompt);
        log("[ManifestSpecialist.process] Input Reasoning:\n" + reasoning);
        log("[ManifestSpecialist.process] Current Project ScId: " + getScId());

        setStatus("Analyzing manifest requirements...");
        Context androidContext = getContext();
        if (androidContext == null) {
            log("[ManifestSpecialist.process] ERROR: Android Context is null!");
            return;
        }

        jC.projectOperationsExecutor.execute(() -> {
            try { Thread.sleep(800); } catch (Exception ignored) {}
            String contextStr = fragment.gatherScopedContext(androidContext, "SYSTEM_MANIFEST", prompt);
            log("[ManifestSpecialist.process] Gathered Context Length: " + (contextStr != null ? contextStr.length() : 0) + " chars.");

            String systemPrompt = "You are the Sketchware Plus Manifest Specialist.\n" +
                    "Help the user inspect, search, read, and modify AndroidManifest.xml and its sections.\n" +
                    "Full manifest XML is omitted from context to save tokens.\n\n" +
                    "INSTRUCTIONS:\n" +
                    "1. Use SEARCH_MANIFEST to search for permissions, activities, services, receivers, intent-filters, or manifest tags.\n" +
                    "2. Available Actions:\n" +
                    "   - SEARCH_MANIFEST: {\"type\": \"SEARCH_MANIFEST\", \"query\": \"permission|activity|service|receiver|INTERNET|keyword|all\"}\n" +
                    "   - ADD_PERMISSION: {\"type\": \"ADD_PERMISSION\", \"permission\": \"android.permission.INTERNET\"}\n" +
                    "   - ADD_XML_PATCH: {\"type\": \"ADD_XML_PATCH\", \"xmlName\": \"AndroidManifest.xml\", \"reference\": \"...\", \"command\": \"insert\", \"inputCode\": \"...\"}\n\n" +
                    "Reasoning: " + reasoning;

            log("[ManifestSpecialist.process] Sending System Prompt to AI:\n" + systemPrompt);

            Activity activity = fragment.getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> fragment.executeRequest(systemPrompt, prompt, contextStr, "SYSTEM_MANIFEST"));
            }
        });
    }

    @Override
    public void handleAction(JSONObject action) {
        log("--------------------------------------------------------------------------------");
        if (action == null) {
            log("[ManifestSpecialist.handleAction] Received null action!");
            return;
        }

        log("[ManifestSpecialist.handleAction] RAW Action JSON received from AI:\n" + formatJson(action));
        String rawType = action.optString("type", "");
        String type = rawType.toUpperCase().replace("-", "_").replace(" ", "_");
        String summary = action.optString("summary", "");
        String thoughtProcess = action.optString("thought_process", "");

        log("[ManifestSpecialist.handleAction] Action Type: " + rawType + " -> Normalized: " + type);
        log("[ManifestSpecialist.handleAction] Summary: " + summary);
        log("[ManifestSpecialist.handleAction] Thought Process: " + thoughtProcess);

        String status = !summary.isEmpty() ? summary : thoughtProcess;
        if (!status.isEmpty()) setStatus(status);

        switch (type) {
            case "SEARCH_MANIFEST":
            case "SEARCH_XML":
            case "READ_MANIFEST":
                log("[ManifestSpecialist.handleAction] Executing SEARCH_MANIFEST...");
                applySearchManifest(action.optString("query", "all"));
                break;

            case "ADD_PERMISSION":
                log("[ManifestSpecialist.handleAction] Executing ADD_PERMISSION...");
                applyPermission(action.optString("permission"));
                break;

            case "ADD_XML_PATCH":
            case "PATCH_MANIFEST_XML":
                log("[ManifestSpecialist.handleAction] Executing PATCH_MANIFEST_XML...");
                applyAddXmlPatch(action);
                break;

            default:
                log("[ManifestSpecialist.handleAction] WARNING: Unknown action type '" + type + "'. Raw action JSON:\n" + formatJson(action));
                break;
        }
    }

    private void applySearchManifest(String query) {
        log("[ManifestSpecialist.applySearchManifest] Input Query: '" + query + "'");
        setStatus("Searching manifest for: " + query);

        jC.projectOperationsExecutor.execute(() -> {
            Context context = getContext();
            String scId = getScId();
            log("[ManifestSpecialist.applySearchManifest] Generating live AndroidManifest.xml for scId: " + scId + "...");
            String manifestXml = SourceCodeAide.getGeneratedManifest(context, scId);

            if (manifestXml != null) {
                log("[ManifestSpecialist.applySearchManifest] Manifest generated successfully (" + manifestXml.length() + " chars). Invoking SourceCodeAide.searchManifest...");
                JSONObject result = SourceCodeAide.searchManifest(manifestXml, query);
                log("[ManifestSpecialist.applySearchManifest -> RETURNED TO AI/UI] Search Result Payload:\n" + formatJson(result));

                Activity activity = fragment.getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        try {
                            String formatted = formatJson(result);
                            fragment.addSystemMessage("--- Manifest Search Results (" + query + ") ---\n" + formatted);
                            String stepMsg = "SEARCH_MANIFEST: " + query + " (" + result.optString("status") + ")";
                            stepHistory.add(stepMsg);
                            log("[ManifestSpecialist.applySearchManifest] Added to stepHistory: " + stepMsg);
                        } catch (Exception e) {
                            log("[ManifestSpecialist.applySearchManifest] ERROR parsing search results: " + e.getMessage());
                            fragment.addSystemMessage("Error parsing search results: " + e.getMessage());
                        }
                    });
                }
            } else {
                log("[ManifestSpecialist.applySearchManifest] ERROR: Generated manifest XML is null!");
                Activity activity = fragment.getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() ->
                            fragment.addSystemMessage("Error: Could not generate AndroidManifest.xml for search."));
                }
            }
        });
    }

    private void applyAddXmlPatch(JSONObject action) {
        String xmlName = action.optString("xmlName", "AndroidManifest.xml");
        String reference = action.optString("reference", "");
        int distance = action.optInt("distance", 0);
        int after = action.optInt("after", 0);
        int before = action.optInt("before", 0);
        String command = action.optString("command", "insert");
        String inputCode = action.optString("inputCode", "");

        log("[ManifestSpecialist.applyAddXmlPatch] Input Arguments:");
        log("  - xmlName: " + xmlName);
        log("  - reference: '" + reference + "'");
        log("  - distance: " + distance);
        log("  - after (front): " + after);
        log("  - before (back): " + before);
        log("  - command: '" + command + "'");
        log("  - inputCode:\n" + inputCode);

        jC.projectOperationsExecutor.execute(() -> {
            log("[ManifestSpecialist.applyAddXmlPatch] Invoking SourceCodeAide.addXmlCommandToManager...");
            JSONObject result = SourceCodeAide.addXmlCommandToManager(
                    getContext(),
                    getScId(),
                    xmlName,
                    reference,
                    distance,
                    after,
                    before,
                    command,
                    inputCode
            );

            log("[ManifestSpecialist.applyAddXmlPatch -> RETURNED TO AI/UI] Result Payload:\n" + formatJson(result));

            Activity activity = fragment.getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    if ("success".equals(result.optString("status"))) {
                        fragment.addSystemMessage(" Manifest XML Patch added successfully (" + command + " near '" + reference + "')");
                        log("[ManifestSpecialist.applyAddXmlPatch] Success: XML patch applied.");
                    } else {
                        fragment.addSystemMessage(" Failed to apply Manifest XML Patch: " + result.optString("message"));
                        log("[ManifestSpecialist.applyAddXmlPatch] Failed: " + result.optString("message"));
                    }
                });
            }
        });
    }

    public void applyPermission(String permission) {
        log("[ManifestSpecialist.applyPermission] Input -> permission: " + permission);
        if (permission == null || permission.trim().isEmpty()) {
            log("[ManifestSpecialist.applyPermission] ERROR: permission string is null or empty!");
            return;
        }

        try {
            String xmlName = getProjectFile() != null ? getProjectFile().getXmlName() : "main";
            fragment.undoSnapshot = new ProjectSnapshot(getScId(), xmlName);
            log("[ManifestSpecialist.applyPermission] Snapshot created for undo.");

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

            log("[ManifestSpecialist.applyPermission] Matched permission bitmask mask: " + permMask);
            if (permMask != 0) {
                dataManager.l.addPermission(permMask);
            }

            try {
                FilePathUtil pathUtil = new FilePathUtil();
                String permissionFilePath = pathUtil.getPathPermission(getScId());
                log("[ManifestSpecialist.applyPermission] Writing permission to JSON file at: " + permissionFilePath);

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
                    log("[ManifestSpecialist.applyPermission] Permission added to permission JSON file.");
                } else {
                    log("[ManifestSpecialist.applyPermission] Permission already exists in permission JSON file.");
                }
            } catch (Exception ex) {
                log("[ManifestSpecialist.applyPermission] ERROR writing to Permission Manager json: " + ex.getMessage());
            }

            yq workspace = new yq(getContext(), getScId());
            BuiltInLibraryManager builtInLibManager = new BuiltInLibraryManager(getScId());
            Ix ix = new Ix(dataManager.l, jC.b(getScId()).c, builtInLibManager);
            ix.setYq(workspace);
            String regeneratedManifest = ix.a();
            workspace.a("AndroidManifest.xml", regeneratedManifest);
            log("[ManifestSpecialist.applyPermission] Regenerated Manifest successfully (" + (regeneratedManifest != null ? regeneratedManifest.length() : 0) + " chars) and updated workspace.");

            fragment.addSystemMessage("Permission '" + permission + "' added automatically.");
            fragment.setUndoVisible(true);
            log("[ManifestSpecialist.applyPermission] Permission '" + permission + "' successfully applied.");
        } catch (Exception e) {
            log("[ManifestSpecialist.applyPermission] FATAL ERROR: " + e.getMessage());
            fragment.addSystemMessage("Error adding permission: " + e.getMessage());
        }
    }
}
