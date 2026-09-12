package sketchware.plus.ai.specialists;

import android.app.Activity;
import android.content.Context;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import a.a.a.eC;
import a.a.a.jC;
import a.a.a.yq;
import a.a.a.wq;
import java.io.File;
import sketchware.plus.ai.ProjectSnapshot;
import sketchware.plus.ai.SkAssistantFragment;
import mod.hilal.saif.blocks.BlocksHandler;
import sketchware.plus.ai.SourceCodeAide;
import sketchware.plus.utility.SketchwareUtil;

public class CodeSpecialist extends BaseSpecialist {

    private String originalPrompt = null;
    private String refinedGoal = null;
    private boolean isProcessing = false;

    public CodeSpecialist(SkAssistantFragment fragment) {
        super(fragment);
    }

    @Override
    public void process(String prompt, String reasoning) {
        if (isProcessing) {
            log("CodeSpecialist: Already processing, ignoring call.");
            return;
        }

        if (originalPrompt == null) {
            originalPrompt = prompt;
        }

        if (reasoning != null && !reasoning.isEmpty()) {
            setStatus(reasoning);
        } else {
            setStatus("Thinking...");
        }
        
        log("CodeSpecialist: process() triggered. Goal set: " + (refinedGoal != null));
        
        Context androidContext = getContext();
        if (androidContext == null) return;

        isProcessing = true;
        jC.projectOperationsExecutor.execute(() -> {
            try {
                ProjectFileBean projectFile = fragment.projectFile;
                if (projectFile == null) return;

                String currentJava = projectFile.getJavaName();
                String contextStr = fragment.gatherScopedContext(androidContext, "CODE_EDIT", originalPrompt);

                // Phase 1: Task Refinement
                if (refinedGoal == null) {
                    log("CodeSpecialist: Requesting Refinement");
                    String refinePrompt = "You are the Task Architect for Sketchware Plus.\n" +
                            "USER REQUEST: " + originalPrompt + "\n\n" +
                            "GOAL: Analyze the user's intent and refine it into a precise technical plan for patching Java source code.\n" +
                            "1. Explain what you understand the user wants to do.\n" +
                            "2. Create a 'Refined Technical Goal' for persistent memory.\n" +
                            "3. Mention that you will use Java Commands to apply changes to the project's generated source code.\n\n" +
                            "CRITICAL: You MUST include an 'actions' array in your response.\n\n" +
                            "RESPONSE FORMAT (JSON):\n" +
                            "{\n" +
                            "  \"category\": \"CODE_EDIT\",\n" +
                            "  \"summary\": \"Refining mission...\",\n" +
                            "  \"actions\": [\n" +
                            "    {\n" +
                            "      \"type\": \"CODE_REFINEMENT\",\n" +
                            "      \"explanation\": \"I understand you want to...\",\n" +
                            "      \"refined_goal\": \"Specific plan for " + currentJava + "...\",\n" +
                            "      \"next_step_action\": {\"type\":\"SEARCH_METHOD\", \"javaName\":\"" + currentJava + "\", \"methodName\":\"onCreate\"}\n" +
                            "    }\n" +
                            "  ]\n" +
                            "}";

                    Activity activity = fragment.getActivity();
                    if (activity != null) {
                        activity.runOnUiThread(() -> fragment.executeRequest(refinePrompt, originalPrompt, contextStr, "CODE_EDIT"));
                    }
                    return;
                }

                // Phase 2: Iterative Execution
                log("CodeSpecialist: Requesting Next Step for: " + refinedGoal);
                String systemPrompt = "You are the Iterative Code Specialist for Sketchware Plus.\n" +
                        "MISSION: " + refinedGoal + "\n\n" +
                        "CAPABILITIES (Include these within the 'actions' array in your JSON):\n" +
                        "1. SEARCH_METHOD: Finds a method's content and line range. Needs 'javaName' and 'methodName'.\n" +
                        "2. FIND_FIELD: Finds where a variable is declared. Needs 'javaName' and 'fieldName'.\n" +
                        "3. GET_CODE_RANGE: Reads a specific block of lines. Needs 'javaName', 'startLine', 'endLine'.\n" +
                        "4. GET_PATCH_META: Returns exact 'reference' and line offsets for a specific line number. Needs 'javaName', 'targetLine'.\n" +
                        "5. ADD_JAVA_COMMAND: Creates a persistent code patch in the 'Java Command Manager'.\n" +
                        "   - Needs: 'javaName', 'reference', 'distance', 'front', 'back', 'command', and 'inputCode'.\n" +
                        "   - 'reference': The EXACT string in the file to use as an anchor. DO NOT use placeholders like 'REFERENCE'.\n" +
                        "   - 'command': Must be one of: insert, add, replace, find-replace, find-replace-first, find-replace-all.\n" +
                        "6. MISSION_COMPLETE: Signal that the task is finished. Needs 'summary'.\n\n" +
                        "STRATEGY:\n" +
                        "- If the user specifies a clear code snippet to replace (e.g., 'replace void test() { } with ...'), you can skip SEARCH_METHOD and call ADD_JAVA_COMMAND directly using that snippet as the 'reference'.\n" +
                        "- Otherwise, SEARCH first, then use GET_PATCH_META to get a precise 'reference' string.\n" +
                        "- Apply only ONE code patch per response.\n\n" +
                        "CRITICAL: DO NOT use native tools or function calling. You MUST return a single JSON object in the following format:\n" +
                        "{\n" +
                        "  \"category\": \"CODE_EDIT\",\n" +
                        "  \"thought_process\": \"Briefly explain your reasoning here.\",\n" +
                        "  \"summary\": \"A short status message for the user.\",\n" +
                        "  \"actions\": [\n" +
                        "    { \"type\": \"ADD_JAVA_COMMAND\", \"javaName\": \"MainActivity.java\", \"reference\": \"public void onCreate(Bundle savedInstanceState) {\", \"distance\": 0, \"front\": 0, \"back\": 0, \"command\": \"add\", \"inputCode\": \"// new logic\" }\n" +
                        "  ]\n" +
                        "}";

                Activity activity = fragment.getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> fragment.executeRequest(systemPrompt, originalPrompt, contextStr, "CODE_EDIT"));
                }
            } finally {
                isProcessing = false;
            }
        });
    }

    @Override
    public void handleAction(JSONObject action) throws JSONException {
        String type = action.optString("type", "").toUpperCase().replace("-", "_").replace(" ", "_");
        log("CodeSpecialist: handleAction: " + type);

        // Update thinking orb
        String status = action.optString("summary", action.optString("thought_process", ""));
        if (!status.isEmpty()) setStatus(status);

        switch (type) {
            case "CODE_REFINEMENT":
                refinedGoal = action.optString("refined_goal");
                String explanation = action.optString("explanation");
                fragment.getActivity().runOnUiThread(() -> {
                    fragment.addSystemMessage("--- MISSION PLAN ---\n" + explanation + "\n\nTarget: " + refinedGoal);
                    try {
                        if (action.has("next_step_action")) {
                            handleAction(action.getJSONObject("next_step_action"));
                        }
                    } catch (Exception e) {
                        log("CodeSpecialist: Step trigger failed: " + e.getMessage());
                    }
                });
                break;

            case "ADD_JAVA_COMMAND":
                applyAddJavaCommand(action);
                break;

            case "SEARCH_METHOD":
                applySearchMethod(action.optString("javaName"), action.optString("methodName"));
                break;

            case "GET_CODE_RANGE":
                applyGetCodeRange(action.optString("javaName"), action.optInt("startLine"), action.optInt("endLine"));
                break;

            case "FIND_FIELD":
                applyFindField(action.optString("javaName"), action.optString("fieldName"));
                break;

            case "GET_PATCH_META":
                applyGetPatchMeta(action.optString("javaName"), action.optInt("targetLine"));
                break;
            
            case "MISSION_COMPLETE":
                fragment.getActivity().runOnUiThread(() -> {
                    fragment.addSystemMessage("Mission Accomplished: " + action.optString("summary"));
                    refinedGoal = null;
                    originalPrompt = null;
                    setStatus("Done");
                });
                break;

            case "CHAT_MSG":
                fragment.getActivity().runOnUiThread(() -> fragment.addSystemMessage(action.optString("content")));
                break;

            case "ADD_BLOCK":
                applyAddBlock(action.getString("eventKey"), action.getString("opCode"), action.optJSONArray("parameters"), true);
                break;

            case "ADD_IMPORT":
                applyImport(action.optString("package", action.optString("name", "")));
                break;
        }
    }

    private void applySearchMethod(String javaName, String methodName) {
        log("CodeSpecialist: Searching method: " + methodName);
        setStatus("Searching method: " + methodName);
        jC.projectOperationsExecutor.execute(() -> {
            String source = getGeneratedSource(javaName);
            if (source != null) {
                JSONObject result = SourceCodeAide.findMethod(source, methodName);
                displaySourceAideResult("Search Method: " + methodName, result);
            }
        });
    }

    private void applyGetCodeRange(String javaName, int start, int end) {
        log("CodeSpecialist: Getting range: " + start + "-" + end);
        setStatus("Reading code range...");
        jC.projectOperationsExecutor.execute(() -> {
            String source = getGeneratedSource(javaName);
            if (source != null) {
                JSONObject result = SourceCodeAide.getLineRange(source, start, end);
                displaySourceAideResult("Code Range: " + start + "-" + end, result);
            }
        });
    }

    private void applyFindField(String javaName, String fieldName) {
        log("CodeSpecialist: Finding field: " + fieldName);
        setStatus("Locating variable: " + fieldName);
        jC.projectOperationsExecutor.execute(() -> {
            String source = getGeneratedSource(javaName);
            if (source != null) {
                JSONObject result = SourceCodeAide.findField(source, fieldName);
                displaySourceAideResult("Find Field: " + fieldName, result);
            }
        });
    }

    private void applyGetPatchMeta(String javaName, int targetLine) {
        log("CodeSpecialist: Getting patch meta for line: " + targetLine);
        setStatus("Calculating patch parameters...");
        jC.projectOperationsExecutor.execute(() -> {
            String source = getGeneratedSource(javaName);
            if (source != null) {
                JSONObject result = SourceCodeAide.getCommandBlockMeta(source, targetLine);
                displaySourceAideResult("Patch Metadata (Line " + targetLine + ")", result);
            }
        });
    }

    private void applyAddJavaCommand(JSONObject action) {
        String javaName = action.optString("javaName");
        String reference = action.optString("reference");
        Context context = getContext();
        if (context == null) return;

        fragment.getActivity().runOnUiThread(() -> {
            new MaterialAlertDialogBuilder(context)
                    .setTitle("Confirm Code Patch")
                    .setMessage("Mission: " + refinedGoal + "\n\nFile: " + javaName + "\nRef: " + reference + "\nCmd: " + action.optString("command") + "\n\nApply this step?")
                    .setCancelable(false)
                    .setPositiveButton("Apply", (dialog, which) -> {
                        jC.projectOperationsExecutor.execute(() -> {
                            fragment.undoSnapshot = new ProjectSnapshot(scId, projectFile.getXmlName());
                            JSONObject result = SourceCodeAide.addJavaCommandToManager(
                                    context, scId, javaName, reference,
                                    action.optInt("distance"), action.optInt("front"), action.optInt("back"),
                                    action.optString("command"), action.optString("inputCode")
                            );
                            
                            fragment.getActivity().runOnUiThread(() -> {
                                if ("success".equals(result.optString("status"))) {
                                    fragment.addSystemMessage("Patch applied to Java Command Manager. Refreshing viewer...");
                                    fragment.refreshDesigner();
                                    process(originalPrompt, "Step applied, continuing loop.");
                                } else {
                                    fragment.addSystemMessage("Error applying patch: " + result.optString("message"));
                                }
                            });
                        });
                    })
                    .setNegativeButton("Abort Mission", (dialog, which) -> {
                        refinedGoal = null;
                        originalPrompt = null;
                        fragment.addSystemMessage("Mission aborted.");
                    })
                    .show();
        });
    }

    private String getGeneratedSource(String javaName) {
        Context context = getContext();
        if (context == null) return null;
        try {
            yq workspace = new yq(context, scId);
            return workspace.getFileSrc(javaName, jC.b(scId), jC.a(scId), jC.c(scId));
        } catch (Exception e) {
            fragment.getActivity().runOnUiThread(() -> fragment.addSystemMessage("Error generating source for " + javaName));
            return null;
        }
    }

    private void displaySourceAideResult(String title, JSONObject result) {
        fragment.getActivity().runOnUiThread(() -> {
            try {
                if ("success".equals(result.optString("status")) || result.has("content") || result.has("snippets")) {
                    fragment.addSystemMessage("--- " + title + " ---\n" + result.optString("content", result.toString(2)));
                    process(originalPrompt, "Information gathered: " + title);
                } else {
                    fragment.addSystemMessage(title + ": Not found in code.");
                    process(originalPrompt, "Search failed for: " + title + ". Trying alternative...");
                }
            } catch (Exception ignored) {}
        });
    }

    public void applyAddBlock(String eventKey, String opCode, JSONArray jParams, boolean isSync) {
        Runnable r = () -> {
            eC dataManager = jC.a(scId);
            String javaName = projectFile.getJavaName();
            ArrayList<BlockBean> blocks = dataManager.a(javaName, eventKey);
            if (blocks == null) {
                dataManager.a(javaName, EventBean.EVENT_TYPE_ACTIVITY, 0, javaName, eventKey.contains("_") ? eventKey.split("_")[1] : eventKey);
                blocks = dataManager.a(javaName, eventKey);
            }
            if (blocks == null) return;

            int maxId = 0;
            for (BlockBean b : blocks) {
                try { maxId = Math.max(maxId, Integer.parseInt(b.id)); } catch (Exception ignored) {}
            }
            String newBlockId = String.valueOf(maxId + 1);

            ArrayList<HashMap<String, Object>> blockList = new ArrayList<>();
            BlocksHandler.builtInBlocks(blockList);
            String type = " ", typeName = "", spec = "";
            for (HashMap<String, Object> bDef : blockList) {
                if (opCode.equals(bDef.get("name"))) {
                    type = (String) bDef.getOrDefault("type", " ");
                    typeName = (String) bDef.getOrDefault("typeName", "");
                    spec = (String) bDef.getOrDefault("spec", "");
                    break;
                }
            }

            BlockBean block = new BlockBean(newBlockId, spec, type, typeName, opCode);
            if (jParams != null) {
                for (int i = 0; i < jParams.length(); i++) {
                    try { block.parameters.add(jParams.getString(i)); } catch (Exception ignored) {}
                }
            }
            blocks.add(block);
            dataManager.k();
            fragment.getActivity().runOnUiThread(fragment::refreshDesigner);
        };
        if (isSync) r.run();
    }

    public void applyImport(String importPath) {
        if (importPath == null || importPath.isEmpty()) return;
        fragment.undoSnapshot = new ProjectSnapshot(scId, projectFile.getXmlName());
        eC dataManager = jC.a(scId);
        dataManager.a(projectFile.getJavaName(), EventBean.EVENT_TYPE_ACTIVITY, 0, "", "Import");
        ArrayList<BlockBean> blocks = dataManager.a(projectFile.getJavaName(), "Import");
        BlockBean importBlock = new BlockBean("0", "none", " ", "createImport");
        importBlock.parameters.add(importPath);
        blocks.add(importBlock);
        dataManager.k();
        fragment.addSystemMessage("Import added: " + importPath);
    }
}
