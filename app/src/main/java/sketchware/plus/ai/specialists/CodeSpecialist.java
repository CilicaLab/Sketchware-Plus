package sketchware.plus.ai.specialists;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.ProjectFileBean;

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

import sketchware.plus.ai.LogicIrBridge;
import sketchware.plus.ai.ProjectSnapshot;
import sketchware.plus.ai.SkAssistantFragment;
import mod.hilal.saif.blocks.BlocksHandler;
import sketchware.plus.ai.SourceCodeAide;
import sketchware.plus.utility.FileUtil;
import sketchware.plus.utility.SketchwareUtil;

public class CodeSpecialist extends BaseSpecialist {

    private String originalPrompt = null;
    private String refinedGoal = null;
    private boolean isProcessing = false;

    private List<String> stepHistory = new ArrayList<>();

    private int iterationCount = 0;
    private static final int MAX_ITERATIONS = 6;
    private AlertDialog activePatchDialog = null;
    private static final List<String> VALID_COMMANDS = List.of(
            "insert", "add", "replace", "find-replace", "find-replace-first", "find-replace-all"
    );


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
                ProjectFileBean projectFile = getProjectFile();
                if (projectFile == null) return;

                String currentJava = projectFile.getJavaName();
                String contextStr = fragment.gatherScopedContext(androidContext, "LOGIC_ENGINEER", originalPrompt);

                // Phase 1: Task Refinement
                if (refinedGoal == null) {
                    log("CodeSpecialist: Requesting Refinement");
                    String refinePrompt = "You are the Task Architect for Sketchware Plus.\n" +
                            "USER REQUEST: " + originalPrompt + "\n\n" +
                            "GOAL: Analyze the user's intent and refine it into a precise technical plan for patching Java source code.\n" +
                            "1. Explain what you understand the user wants to do.\n" +
                            "2. Create a 'Refined Technical Goal' for persistent memory.\n" +
                            "3. Mention that you will use Java Commands or Logic IR to apply changes safely to the project.\n\n" +
                            "CRITICAL: You MUST include an 'actions' array in your response.\n\n" +
                            "RESPONSE FORMAT (JSON):\n" +
                            "{\n" +
                            "  \"category\": \"LOGIC_ENGINEER\",\n" +
                            "  \"summary\": \"Refining mission...\",\n" +
                            "  \"actions\": [\n" +
                            "    {\n" +
                            "      \"type\": \"CODE_REFINEMENT\",\n" +
                            "      \"explanation\": \"I understand you want to...\",\n" +
                            "      \"refined_goal\": \"Precise technical summary...\",\n" +
                            "      \"next_step_action\": { ... }\n" +
                            "    }\n" +
                            "  ]\n" +
                            "}\n";

                    Activity activity = fragment.getActivity();
                    if (activity == null) return;
                    activity.runOnUiThread(() -> fragment.executeRequest(refinePrompt, originalPrompt, contextStr, "LOGIC_ENGINEER"));
                } else {
                    // Phase 2: Step-by-step execution
                    log("CodeSpecialist: Requesting Next Step for: " + refinedGoal);

                    String systemPrompt = "You are the Logic Engineer for Sketchware Plus.\n" +
                            "REFINED TECHNICAL GOAL: " + refinedGoal + "\n" +
                            "PREVIOUS STEPS HISTORY:\n" + String.join("\n", stepHistory) + "\n\n" +
                            "INSTRUCTIONS:\n" +
                            "1. Review the code/project state provided in context.\n" +
                            "2. Decide the SINGLE NEXT ACTION to take toward the goal.\n" +
                            "3. Available Actions:\n" +
                            "   - GET_LOGIC_IR: {\"type\": \"GET_LOGIC_IR\", \"javaName\": \"main\", \"eventKey\": \"onCreate\"}\n" +
                            "   - APPLY_LOGIC_IR: {\"type\": \"APPLY_LOGIC_IR\", \"javaName\": \"main\", \"eventKey\": \"onCreate\", \"javaCode\": \"...\"}\n" +
                            "   - SEARCH_METHOD: {\"type\": \"SEARCH_METHOD\", \"javaName\": \"main\", \"methodName\": \"initialize\"}\n" +
                            "   - GET_CODE_RANGE: {\"type\": \"GET_CODE_RANGE\", \"javaName\": \"main\", \"startLine\": 10, \"endLine\": 30}\n" +
                            "   - FIND_FIELD: {\"type\": \"FIND_FIELD\", \"javaName\": \"main\", \"fieldName\": \"myButton\"}\n" +
                            "   - ADD_JAVA_COMMAND: {\"type\": \"ADD_JAVA_COMMAND\", \"javaName\": \"main\", \"reference\": \"...\", \"distance\": 0, \"after\": 1, \"before\": 0, \"command\": \"insert\", \"inputCode\": \"...\"}\n" +
                            "   - MISSION_COMPLETE: {\"type\": \"MISSION_COMPLETE\", \"summary\": \"Final description of completed task.\"}\n" +
                            "4. Output ONLY valid JSON containing your thought process and an 'actions' array.\n\n" +
                            "RESPONSE FORMAT (JSON):\n" +
                            "{\n" +
                            "  \"category\": \"LOGIC_ENGINEER\",\n" +
                            "  \"summary\": \"Short description...\",\n" +
                            "  \"actions\": [\n" +
                            "    { \"type\": \"...\", ... }\n" +
                            "  ]\n" +
                            "}\n";

                    Activity activity = fragment.getActivity();
                    if (activity == null) return;

                    if (refinedGoal != null) {
                        iterationCount++;
                        if (iterationCount > MAX_ITERATIONS) {
                            fragment.getActivity().runOnUiThread(() -> {
                                fragment.addSystemMessage("Stopped after " + MAX_ITERATIONS +
                                        " steps without a completion signal. Reply to continue or rephrase the goal.");
                                refinedGoal = null;
                                originalPrompt = null;
                                isProcessing = false;
                            });
                            return;
                        }
                    }
                    activity.runOnUiThread(() -> fragment.executeRequest(systemPrompt, originalPrompt, contextStr, "LOGIC_ENGINEER"));
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

            case "GET_LOGIC_IR":
                applyGetLogicIr(action);
                break;

            case "APPLY_LOGIC_IR":
                applyLogicIrWithGuardrails(action);
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
                    if (activePatchDialog != null && activePatchDialog.isShowing()) {
                        activePatchDialog.dismiss();
                        activePatchDialog = null;
                    }
                    fragment.addSystemMessage("Mission Accomplished: " + action.optString("summary"));
                    refinedGoal = null;
                    originalPrompt = null;
                    iterationCount = 0;
                    stepHistory.clear();
                    setStatus("Done");
                    fragment.cancelSKRequests();
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

    private void applyGetLogicIr(JSONObject action) {
        String javaName = action.optString("javaName", getProjectFile() != null ? getProjectFile().getJavaName() : "main");
        String eventKey = action.optString("eventKey", "onCreate");

        jC.projectOperationsExecutor.execute(() -> {
            String scId = getScId();
            JSONObject ir = LogicIrBridge.loadIrFromProject(scId, javaName, eventKey);
            String javaCode = LogicIrBridge.irToJava(ir);

            fragment.getActivity().runOnUiThread(() -> {
                fragment.addSystemMessage("--- EVENT LOGIC IR (" + javaName + " / " + eventKey + ") ---\n" +
                        "Java Representation:\n" + (javaCode.isEmpty() ? "(Empty)" : javaCode) + "\n\n" +
                        "IR JSON:\n" + ir.toString());
            });
        });
    }

    private void applyLogicIrWithGuardrails(JSONObject action) {
        Context context = getContext();
        if (context == null || fragment.getActivity() == null) return;

        String javaName = action.optString("javaName", getProjectFile() != null ? getProjectFile().getJavaName() : "main");
        String eventKey = action.optString("eventKey", "onCreate");

        JSONObject proposedIr = action.optJSONObject("ir");
        if (proposedIr == null && action.has("javaCode")) {
            proposedIr = LogicIrBridge.javaToIr(action.optString("javaCode"));
        }

        if (proposedIr == null) {
            fragment.getActivity().runOnUiThread(() -> {
                fragment.addSystemMessage(" Guardrail Error: No valid IR JSON or javaCode supplied for APPLY_LOGIC_IR.");
            });
            return;
        }

        setStatus("Validating proposed logic...");

        // Guardrail Step 1: Dry-run validation
        LogicIrBridge.ValidationResult validation = LogicIrBridge.validateIr(proposedIr);
        if (!validation.isValid) {
            fragment.getActivity().runOnUiThread(() -> {
                fragment.addSystemMessage(" Logic Guardrail Error: Proposed AI logic failed validation.\nReason: " + validation.errorMessage);
            });
            return;
        }

        // Guardrail Step 2: Generate Current vs Proposed Diff
        String scId = getScId();
        JSONObject currentIr = LogicIrBridge.loadIrFromProject(scId, javaName, eventKey);
        String currentJava = LogicIrBridge.irToJava(currentIr);
        String proposedJava = validation.generatedJava;

        JSONObject finalIr = proposedIr;

        // Guardrail Step 3: User Confirmation & Diff Dialog
        fragment.getActivity().runOnUiThread(() -> {
            ScrollView scrollView = new ScrollView(context);
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(32, 32, 32, 32);

            TextView header = new TextView(context);
            header.setText("Target: " + javaName + " (" + eventKey + ")\nProposed Blocks: " + validation.blockCount);
            header.setTextSize(14);
            header.setTypeface(null, Typeface.BOLD);

            TextView currentTitle = new TextView(context);
            currentTitle.setText("\n--- CURRENT LOGIC ---");
            currentTitle.setTypeface(null, Typeface.BOLD);

            TextView currentCode = new TextView(context);
            currentCode.setText(currentJava.isEmpty() ? "(Empty logic)" : currentJava);
            currentCode.setTypeface(Typeface.MONOSPACE);
            currentCode.setTextSize(12);

            TextView proposedTitle = new TextView(context);
            proposedTitle.setText("\n--- PROPOSED NEW LOGIC ---");
            proposedTitle.setTypeface(null, Typeface.BOLD);

            TextView proposedCode = new TextView(context);
            proposedCode.setText(proposedJava);
            proposedCode.setTypeface(Typeface.MONOSPACE);
            proposedCode.setTextSize(12);

            layout.addView(header);
            layout.addView(currentTitle);
            layout.addView(currentCode);
            layout.addView(proposedTitle);
            layout.addView(proposedCode);
            scrollView.addView(layout);

            new MaterialAlertDialogBuilder(context)
                    .setTitle("AI Logic Change Confirmation")
                    .setView(scrollView)
                    .setPositiveButton("Apply Logic", (dialog, which) -> {
                        // Guardrail Step 4: Backup original blocks before saving
                        ArrayList<BlockBean> originalBlocks = jC.a(scId).a(javaName, eventKey);

                        boolean success = LogicIrBridge.saveIrToProject(scId, javaName, eventKey, finalIr);
                        if (success) {
                            fragment.addSystemMessage(" Logic applied successfully to " + eventKey + " (" + validation.blockCount + " blocks).");
                            fragment.refreshDesigner();
                        } else {
                            fragment.addSystemMessage(" Failed to persist logic. Restoring original blocks.");
                            if (originalBlocks != null) {
                                eC manager = jC.a(scId);
                                if (manager != null) {
                                    manager.a(javaName, eventKey, originalBlocks);
                                    manager.k();
                                }
                            }
                        }
                    })
                    .setNegativeButton("Reject", (dialog, which) -> {
                        fragment.addSystemMessage(" Logic modification rejected by user.");
                    })
                    .setCancelable(false)
                    .show();
        });
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
        setStatus("Finding field: " + fieldName);
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
        setStatus("Calculating patch metadata...");
        jC.projectOperationsExecutor.execute(() -> {
            String source = getGeneratedSource(javaName);
            if (source != null) {
                JSONObject result = SourceCodeAide.getCommandBlockMeta(source, targetLine);
                displaySourceAideResult("Patch Meta for Line: " + targetLine, result);
            }
        });
    }

    private void displaySourceAideResult(String title, JSONObject result) {
        fragment.getActivity().runOnUiThread(() -> {
            try {
                if ("success".equals(result.optString("status"))) {
                    String formatted = result.toString(2);
                    fragment.addSystemMessage("--- " + title + " ---\n" + formatted);
                    stepHistory.add(title + ": Success");
                } else {
                    fragment.addSystemMessage( title + " Failed: " + result.optString("message"));
                    stepHistory.add(title + ": Failed");
                }
            } catch (Exception e) {
                fragment.addSystemMessage("Error parsing result: " + e.getMessage());
            }
        });
    }

    private String getGeneratedSource(String javaName) {
        try {
            ProjectFileBean projectFile = getProjectFile();
            if (projectFile == null) return null;
            String scId = getScId();
            yq workspace = new yq(getContext(), scId);
            String fullJavaName = javaName.endsWith(".java") ? javaName : javaName + ".java";
            String path = workspace.projectMyscPath + "app" + File.separator + "src" + File.separator + "main" + File.separator + "java" + File.separator + fullJavaName;
            if (FileUtil.isExistFile(path)) {
                return FileUtil.readFile(path);
            }
        } catch (Exception e) {
            log("CodeSpecialist: Error reading source: " + e.getMessage());
        }
        return null;
    }

    private void applyAddJavaCommand(JSONObject action) {
        String javaName = action.optString("javaName", getProjectFile() != null ? getProjectFile().getJavaName() : "main");
        String reference = action.optString("reference", "");
        int distance = action.optInt("distance", 0);
        int after = action.optInt("after", 0);
        int before = action.optInt("before", 0);
        String command = action.optString("command", "insert");
        String inputCode = action.optString("inputCode", "");

        if (!VALID_COMMANDS.contains(command.toLowerCase())) {
            fragment.getActivity().runOnUiThread(() ->
                    fragment.addSystemMessage("Invalid Java Command: " + command));
            return;
        }

        jC.projectOperationsExecutor.execute(() -> {
            JSONObject result = SourceCodeAide.addJavaCommandToManager(
                    getContext(),
                    getScId(),
                    javaName,
                    reference,
                    distance,
                    after,
                    before,
                    command,
                    inputCode
            );

            fragment.getActivity().runOnUiThread(() -> {
                if ("success".equals(result.optString("status"))) {
                    fragment.addSystemMessage(" Java Command added successfully (" + command + " near '" + reference + "')");
                    stepHistory.add("ADD_JAVA_COMMAND: " + command + " near '" + reference + "'");
                    fragment.refreshDesigner();
                } else {
                    fragment.addSystemMessage(" Failed to add Java Command: " + result.optString("message"));
                    stepHistory.add("ADD_JAVA_COMMAND: Failed");
                }
            });
        });
    }

    public void applyAddBlock(String eventKey, String opCode, JSONArray jParams, boolean isSync) {
        if (eventKey == null || opCode == null) return;
        fragment.undoSnapshot = new ProjectSnapshot(getScId(), getProjectFile().getXmlName());
        Runnable r = () -> {
            eC dataManager = jC.a(getScId());
            String javaName = getProjectFile().getJavaName();
            ArrayList<BlockBean> blocks = dataManager.a(javaName, eventKey);
            if (blocks == null) {
                dataManager.a(javaName, EventBean.EVENT_TYPE_ACTIVITY, 0, "", eventKey);
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
        fragment.undoSnapshot = new ProjectSnapshot(getScId(), getProjectFile().getXmlName());
        eC dataManager = jC.a(getScId());
        dataManager.a(getProjectFile().getJavaName(), EventBean.EVENT_TYPE_ACTIVITY, 0, "", "Import");
        ArrayList<BlockBean> blocks = dataManager.a(getProjectFile().getJavaName(), "Import");
        BlockBean importBlock = new BlockBean("0", "none", " ", "createImport");
        importBlock.parameters.add(importPath);
        blocks.add(importBlock);
        dataManager.k();
        fragment.addSystemMessage("Import added: " + importPath);
    }
}
