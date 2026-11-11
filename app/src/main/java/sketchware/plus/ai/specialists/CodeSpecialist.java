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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import a.a.a.eC;
import a.a.a.jC;
import a.a.a.yq;
import a.a.a.wq;
import sketchware.plus.ai.AssistantImportInjector;
import sketchware.plus.ai.ProjectStructureAide;
import sketchware.plus.ai.LogicIrBridge;
import sketchware.plus.ai.ProjectSnapshot;
import sketchware.plus.ai.SkAssistantFragment;
import sketchware.plus.ai.SourceCodeAide;
import mod.hilal.saif.blocks.BlocksHandler;
import sketchware.plus.utility.FileUtil;
import sketchware.plus.utility.SketchwareUtil;

public class CodeSpecialist extends BaseSpecialist {

    private String originalPrompt = null;
    private String refinedGoal = null;
    private boolean isProcessing = false;

    private List<String> stepHistory = new ArrayList<>();

    private int iterationCount = 0;
    private static final int MAX_ITERATIONS = 30;
    private AlertDialog activePatchDialog = null;
    private static final List<String> VALID_COMMANDS = List.of(
            "insert", "add", "replace", "find-replace", "find-replace-first", "find-replace-all"
    );

    public CodeSpecialist(SkAssistantFragment fragment) {
        super(fragment);
        log("[CodeSpecialist] Initialized.");
    }

    private String formatJson(JSONObject json) {
        if (json == null) return "null";
        try {
            return json.toString(2);
        } catch (Exception e) {
            return json.toString();
        }
    }

    private String formatJsonArray(JSONArray array) {
        if (array == null) return "null";
        try {
            return array.toString(2);
        } catch (Exception e) {
            return array.toString();
        }
    }

    @Override
    public void process(String prompt, String reasoning) {
        log("================================================================================");
        log("[CodeSpecialist.process] Triggered.");
        log("[CodeSpecialist.process] User Input Prompt:\n" + prompt);
        log("[CodeSpecialist.process] Input Reasoning:\n" + reasoning);

        if (isProcessing) {
            log("[CodeSpecialist.process] WARNING: Already processing another operation, ignoring this call.");
            return;
        }

        if (originalPrompt == null) {
            originalPrompt = prompt;
            log("[CodeSpecialist.process] Set originalPrompt: " + originalPrompt);
        }

        if (reasoning != null && !reasoning.isEmpty()) {
            setStatus(reasoning);
        } else {
            setStatus("Thinking...");
        }

        log("[CodeSpecialist.process] State -> refinedGoal: " + refinedGoal);
        log("[CodeSpecialist.process] State -> iterationCount: " + iterationCount + " / " + MAX_ITERATIONS);
        log("[CodeSpecialist.process] State -> stepHistory count: " + stepHistory.size());
        if (!stepHistory.isEmpty()) {
            log("[CodeSpecialist.process] Current Step History:\n  * " + String.join("\n  * ", stepHistory));
        }

        Context androidContext = getContext();
        if (androidContext == null) {
            log("[CodeSpecialist.process] ERROR: Android Context is null. Aborting process.");
            return;
        }

        isProcessing = true;
        jC.projectOperationsExecutor.execute(() -> {
            try {
                ProjectFileBean projectFile = getProjectFile();
                if (projectFile == null) {
                    log("[CodeSpecialist.process] ERROR: ProjectFileBean is null. Aborting.");
                    return;
                }

                String currentJava = projectFile.getJavaName();
                log("[CodeSpecialist.process] Current Java Target File: " + currentJava);

                String contextStr = fragment.gatherScopedContext(androidContext, "LOGIC_ENGINEER", originalPrompt);
                log("[CodeSpecialist.process] Gathered Context Length: " + (contextStr != null ? contextStr.length() : 0) + " characters.");

                // Phase 1: Task Refinement
                if (refinedGoal == null) {
                    log("[CodeSpecialist.process] PHASE 1: Requesting Refinement Plan from AI...");
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

                    log("[CodeSpecialist.process] Sending Refine Prompt to AI:\n" + refinePrompt);
                    Activity activity = fragment.getActivity();
                    if (activity == null) {
                        log("[CodeSpecialist.process] ERROR: Activity is null, cannot dispatch refine prompt.");
                        return;
                    }
                    activity.runOnUiThread(() -> fragment.executeRequest(refinePrompt, originalPrompt, contextStr, "LOGIC_ENGINEER"));
                } else {
                    // Phase 2: Step-by-step execution
                    log("[CodeSpecialist.process] PHASE 2: Requesting Next Step Execution from AI for Goal: " + refinedGoal);

                    String systemPrompt = "You are the Logic Engineer for Sketchware Plus.\n" +
                            "REFINED TECHNICAL GOAL: " + refinedGoal + "\n" +
                            "PREVIOUS STEPS HISTORY:\n" + String.join("\n", stepHistory) + "\n\n" +
                            "INSTRUCTIONS:\n" +
                            "1. Review the code/project state provided in context.\n" +
                            "2. Decide the SINGLE NEXT ACTION or sequence of actions to take toward the goal.\n" +
                            "3. Copy 'javaName' (e.g. 'MainActivity.java') and 'eventKey' (e.g. 'onCreate_initializeLogic') EXACTLY from the context lists.\n" +
                            "4. Available Actions:\n" +
                            "   - REGISTER_EVENT: {\"type\": \"REGISTER_EVENT\", \"javaName\": \"MainActivity.java\", \"targetKind\": \"view|component|activity\", \"targetId\": \"button1\", \"eventName\": \"onClick\"}\n" +
                            "   - ADD_VARIABLES: {\"type\": \"ADD_VARIABLES\", \"javaName\": \"MainActivity.java\", \"items\": [{\"name\": \"count\", \"type\": \"number\"}, {\"name\": \"name\", \"type\": \"string\"}]}\n" +
                            "   - ADD_LISTS: {\"type\": \"ADD_LISTS\", \"javaName\": \"MainActivity.java\", \"items\": [{\"name\": \"userList\", \"type\": \"string\"}]}\n" +
                            "   - ADD_MORE_BLOCK: {\"type\": \"ADD_MORE_BLOCK\", \"javaName\": \"MainActivity.java\", \"name\": \"greetUser\", \"params\": [{\"name\": \"username\", \"type\": \"string\"}], \"returnType\": \"void\", \"body_java\": \"...\"} (Manual code in 'body_java' is placed inside an 'Add source directly' block. You can also use ADD_BLOCK with opCode 'addSourceDirectly'.)\n" +
                            "   - CREATE_ACTIVITY: {\"type\": \"CREATE_ACTIVITY\", \"name\": \"DetailActivity\", \"options\": {\"toolbar\": true, \"fullscreen\": false}, \"orientation\": 0, \"keyboard\": 0, \"layout_xml\": \"...\"}\n" +
                            "   - GET_PROJECT_STRUCTURE: {\"type\": \"GET_PROJECT_STRUCTURE\", \"javaName\": \"MainActivity.java\"}\n" +
                            "   - GET_LOGIC_IR: {\"type\": \"GET_LOGIC_IR\", \"javaName\": \"MainActivity.java\", \"eventKey\": \"onCreate_initializeLogic\"}\n" +
                            "   - APPLY_LOGIC_IR: {\"type\": \"APPLY_LOGIC_IR\", \"javaName\": \"MainActivity.java\", \"eventKey\": \"button1_onClick\", \"javaCode\": \"...\"}\n" +
                            "   - ADD_BLOCK: {\"type\": \"ADD_BLOCK\", \"javaName\": \"MainActivity.java\", \"eventKey\": \"onCreate_initializeLogic\", \"opCode\": \"showMessage\", \"parameters\": [\"Hello\"]}\n" +
                            "   - ADD_IMPORT: {\"type\": \"ADD_IMPORT\", \"package\": \"java.util.ArrayList\"}\n" +
                            "   - ADD_JAVA_COMMAND: {\"type\": \"ADD_JAVA_COMMAND\", \"javaName\": \"MainActivity.java\", \"reference\": \"...\", \"distance\": 0, \"after\": 1, \"before\": 0, \"command\": \"insert\", \"inputCode\": \"...\"}\n" +
                            "   - SEARCH_METHOD: {\"type\": \"SEARCH_METHOD\", \"javaName\": \"MainActivity.java\", \"methodName\": \"initialize\"}\n" +
                            "   - MISSION_COMPLETE: {\"type\": \"MISSION_COMPLETE\", \"summary\": \"Final description of completed task.\"}\n" +
                            "5. Output ONLY valid JSON containing your thought process and an 'actions' array.\n\n" +
                            "RESPONSE FORMAT (JSON):\n" +
                            "{\n" +
                            "  \"category\": \"LOGIC_ENGINEER\",\n" +
                            "  \"summary\": \"Short description...\",\n" +
                            "  \"actions\": [\n" +
                            "    { \"type\": \"REGISTER_EVENT\", \"targetId\": \"button1\", \"eventName\": \"onClick\" },\n" +
                            "    { \"type\": \"APPLY_LOGIC_IR\", \"javaName\": \"MainActivity.java\", \"eventKey\": \"button1_onClick\", \"javaCode\": \"Toast.makeText(getApplicationContext(), \\\"Clicked!\\\", Toast.LENGTH_SHORT).show();\" }\n" +
                            "  ]\n" +
                            "}\n";

                    log("[CodeSpecialist.process] Sending Step Prompt to AI:\n" + systemPrompt);
                    Activity activity = fragment.getActivity();
                    if (activity == null) {
                        log("[CodeSpecialist.process] ERROR: Activity is null, cannot dispatch step prompt.");
                        return;
                    }

                    if (refinedGoal != null) {
                        iterationCount++;
                        log("[CodeSpecialist.process] Iteration Count updated to: " + iterationCount + " / " + MAX_ITERATIONS);
                        if (iterationCount > MAX_ITERATIONS + 1) {
                            log("[CodeSpecialist.process] MAX_ITERATIONS reached (" + iterationCount + "). Aborting logic sequence.");
                            activity.runOnUiThread(() -> {
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
            } catch (Exception e) {
                log("[CodeSpecialist.process] ERROR during execution: " + e.getMessage());
            } finally {
                isProcessing = false;
                log("[CodeSpecialist.process] Execution block finished. isProcessing reset to false.");
            }
        });
    }

    @Override
    public void handleAction(JSONObject action) throws JSONException {
        log("--------------------------------------------------------------------------------");
        if (action == null) {
            log("[CodeSpecialist.handleAction] Received NULL action!");
            return;
        }

        log("[CodeSpecialist.handleAction] RAW Action JSON received from AI:\n" + formatJson(action));

        String rawType = action.optString("type", "");
        String type = rawType.toUpperCase().replace("-", "_").replace(" ", "_");
        String summary = action.optString("summary", "");
        String thoughtProcess = action.optString("thought_process", "");

        log("[CodeSpecialist.handleAction] Action Type: " + rawType + " -> Normalized: " + type);
        log("[CodeSpecialist.handleAction] Summary: " + summary);
        log("[CodeSpecialist.handleAction] Thought Process: " + thoughtProcess);

        String status = !summary.isEmpty() ? summary : thoughtProcess;
        if (!status.isEmpty()) setStatus(status);

        switch (type) {
            case "CODE_REFINEMENT":
                log("[CodeSpecialist.handleAction -> CODE_REFINEMENT] Executing CODE_REFINEMENT...");
                refinedGoal = action.optString("refined_goal");
                String explanation = action.optString("explanation");
                log("  - New Refined Goal: " + refinedGoal);
                log("  - Explanation: " + explanation);
                log("  - Has Next Step Action: " + action.has("next_step_action"));

                fragment.getActivity().runOnUiThread(() -> {
                    fragment.addSystemMessage("--- MISSION PLAN ---\n" + explanation + "\n\nTarget: " + refinedGoal);
                    try {
                        if (action.has("next_step_action")) {
                            JSONObject nextAction = action.getJSONObject("next_step_action");
                            log("[CodeSpecialist.handleAction -> CODE_REFINEMENT] Dispatching nested next_step_action:\n" + formatJson(nextAction));
                            handleAction(nextAction);
                        }
                    } catch (Exception e) {
                        log("[CodeSpecialist.handleAction -> CODE_REFINEMENT] ERROR triggering nested next_step_action: " + e.getMessage());
                    }
                });
                break;

            case "GET_LOGIC_IR":
                log("[CodeSpecialist.handleAction -> GET_LOGIC_IR] Executing GET_LOGIC_IR...");
                applyGetLogicIr(action);
                break;

            case "APPLY_LOGIC_IR":
                log("[CodeSpecialist.handleAction -> APPLY_LOGIC_IR] Executing APPLY_LOGIC_IR...");
                applyLogicIrWithGuardrails(action);
                break;

            case "ADD_JAVA_COMMAND":
                log("[CodeSpecialist.handleAction -> ADD_JAVA_COMMAND] Executing ADD_JAVA_COMMAND...");
                applyAddJavaCommand(action);
                break;

            case "SEARCH_METHOD":
                log("[CodeSpecialist.handleAction -> SEARCH_METHOD] Executing SEARCH_METHOD...");
                applySearchMethod(action.optString("javaName"), action.optString("methodName"));
                break;

            case "GET_CODE_RANGE":
                log("[CodeSpecialist.handleAction -> GET_CODE_RANGE] Executing GET_CODE_RANGE...");
                applyGetCodeRange(action.optString("javaName"), action.optInt("startLine"), action.optInt("endLine"));
                break;

            case "FIND_FIELD":
                log("[CodeSpecialist.handleAction -> FIND_FIELD] Executing FIND_FIELD...");
                applyFindField(action.optString("javaName"), action.optString("fieldName"));
                break;

            case "GET_PATCH_META":
                log("[CodeSpecialist.handleAction -> GET_PATCH_META] Executing GET_PATCH_META...");
                applyGetPatchMeta(action.optString("javaName"), action.optInt("targetLine"));
                break;

            case "MISSION_COMPLETE":
                log("[CodeSpecialist.handleAction -> MISSION_COMPLETE] Executing MISSION_COMPLETE...");
                String completeSummary = action.optString("summary");
                log("  - Mission Summary: " + completeSummary);
                log("  - Iterations Completed: " + iterationCount);
                log("  - Final Step History (" + stepHistory.size() + " items):\n  * " + String.join("\n  * ", stepHistory));

                fragment.getActivity().runOnUiThread(() -> {
                    if (activePatchDialog != null && activePatchDialog.isShowing()) {
                        activePatchDialog.dismiss();
                        activePatchDialog = null;
                        log("[CodeSpecialist.handleAction -> MISSION_COMPLETE] Dismissed active patch dialog.");
                    }
                    fragment.addSystemMessage("Mission Accomplished: " + completeSummary);
                    refinedGoal = null;
                    originalPrompt = null;
                    iterationCount = 0;
                    stepHistory.clear();
                    setStatus("Done");
                    fragment.cancelSKRequests();
                    log("[CodeSpecialist.handleAction -> MISSION_COMPLETE] State reset and requests cancelled.");
                });
                break;

            case "CHAT_MSG":
                log("[CodeSpecialist.handleAction -> CHAT_MSG] Executing CHAT_MSG...");
                String chatContent = action.optString("content");
                log("  - Chat Content: " + chatContent);
                fragment.getActivity().runOnUiThread(() -> fragment.addSystemMessage(chatContent));
                break;

            case "ADD_BLOCK":
                log("[CodeSpecialist.handleAction -> ADD_BLOCK] Executing ADD_BLOCK...");
                applyAddBlock(action.getString("eventKey"), action.getString("opCode"), action.optJSONArray("parameters"), true);
                break;

            case "ADD_IMPORT":
                log("[CodeSpecialist.handleAction -> ADD_IMPORT] Executing ADD_IMPORT...");
                applyImport(action.optString("package", action.optString("name", "")));
                break;

            case "REGISTER_EVENT": {
                log("[CodeSpecialist.handleAction -> REGISTER_EVENT] Registering event...");
                String javaName = action.optString("javaName", getProjectFile() != null ? getProjectFile().getJavaName() : "MainActivity.java");
                String targetKind = action.optString("targetKind", "view");
                String targetId = action.optString("targetId", "");
                String eventName = action.optString("eventName", "");

                fragment.undoSnapshot = new ProjectSnapshot(getScId(), getProjectFile() != null ? getProjectFile().getXmlName() : "main.xml");
                fragment.setUndoVisible(true);

                String result = ProjectStructureAide.registerEvent(getContext(), getScId(), javaName, targetKind, targetId, eventName);
                log("  - Register Event Result: " + result);
                stepHistory.add("REGISTER_EVENT: " + result);

                fragment.getActivity().runOnUiThread(() -> {
                    fragment.addSystemMessage("Registered Event: " + result);
                    fragment.refreshDesigner();
                });

                if (refinedGoal != null) {
                    process(originalPrompt, "Event registered: " + result + ". Proceeding to next step...");
                }
                break;
            }

            case "ADD_VARIABLES": {
                log("[CodeSpecialist.handleAction -> ADD_VARIABLES] Adding variables...");
                String javaName = action.optString("javaName", getProjectFile() != null ? getProjectFile().getJavaName() : "MainActivity.java");
                JSONArray items = action.optJSONArray("items");
                if (items == null && action.has("name")) {
                    items = new JSONArray();
                    try {
                        JSONObject item = new JSONObject();
                        item.put("name", action.optString("name"));
                        item.put("type", action.optString("type", "string"));
                        items.put(item);
                    } catch (Exception ignored) {}
                }

                fragment.undoSnapshot = new ProjectSnapshot(getScId(), getProjectFile() != null ? getProjectFile().getXmlName() : "main.xml");
                fragment.setUndoVisible(true);

                String result = ProjectStructureAide.addVariables(getContext(), getScId(), javaName, items);
                log("  - Add Variables Result: " + result);
                stepHistory.add("ADD_VARIABLES: " + result);

                fragment.getActivity().runOnUiThread(() -> {
                    fragment.addSystemMessage("Add Variables: " + result);
                    fragment.refreshDesigner();
                });

                if (refinedGoal != null) {
                    process(originalPrompt, "Variables result: " + result + ". Proceeding to next step...");
                }
                break;
            }

            case "ADD_LISTS": {
                log("[CodeSpecialist.handleAction -> ADD_LISTS] Adding lists...");
                String javaName = action.optString("javaName", getProjectFile() != null ? getProjectFile().getJavaName() : "MainActivity.java");
                JSONArray items = action.optJSONArray("items");
                if (items == null && action.has("name")) {
                    items = new JSONArray();
                    try {
                        JSONObject item = new JSONObject();
                        item.put("name", action.optString("name"));
                        item.put("type", action.optString("type", "string"));
                        items.put(item);
                    } catch (Exception ignored) {}
                }

                fragment.undoSnapshot = new ProjectSnapshot(getScId(), getProjectFile() != null ? getProjectFile().getXmlName() : "main.xml");
                fragment.setUndoVisible(true);

                String result = ProjectStructureAide.addLists(getContext(), getScId(), javaName, items);
                log("  - Add Lists Result: " + result);
                stepHistory.add("ADD_LISTS: " + result);

                fragment.getActivity().runOnUiThread(() -> {
                    fragment.addSystemMessage("Add Lists: " + result);
                    fragment.refreshDesigner();
                });

                if (refinedGoal != null) {
                    process(originalPrompt, "Lists result: " + result + ". Proceeding to next step...");
                }
                break;
            }

            case "ADD_MORE_BLOCK": {
                log("[CodeSpecialist.handleAction -> ADD_MORE_BLOCK] Creating More Block...");
                String javaName = action.optString("javaName", getProjectFile() != null ? getProjectFile().getJavaName() : "MainActivity.java");
                String mbName = action.optString("name", action.optString("funcName", ""));
                JSONArray params = action.optJSONArray("params");
                String returnType = action.optString("returnType", null);
                String bodyJava = action.optString("body_java", action.optString("bodyJava", null));

                fragment.undoSnapshot = new ProjectSnapshot(getScId(), getProjectFile() != null ? getProjectFile().getXmlName() : "main.xml");
                fragment.setUndoVisible(true);

                String result = ProjectStructureAide.addMoreBlock(getContext(), getScId(), javaName, mbName, params, returnType, bodyJava);
                log("  - Add More Block Result: " + result);
                stepHistory.add("ADD_MORE_BLOCK: " + result);

                fragment.getActivity().runOnUiThread(() -> {
                    fragment.addSystemMessage("Created More Block: " + result);
                    fragment.refreshDesigner();
                });

                if (refinedGoal != null) {
                    process(originalPrompt, "More Block result: " + result + ". Proceeding to next step...");
                }
                break;
            }

            case "CREATE_ACTIVITY": {
                log("[CodeSpecialist.handleAction -> CREATE_ACTIVITY] Creating activity...");
                String actName = action.optString("name", action.optString("activityName", ""));
                JSONObject options = action.optJSONObject("options");
                int orientation = action.optInt("orientation", 0);
                int keyboardSetting = action.optInt("keyboard", 0);
                String layoutXml = action.optString("layout_xml", action.optString("xml", null));

                fragment.undoSnapshot = new ProjectSnapshot(getScId(), getProjectFile() != null ? getProjectFile().getXmlName() : "main.xml");
                fragment.setUndoVisible(true);

                String result = ProjectStructureAide.createActivity(getContext(), getScId(), actName, options, orientation, keyboardSetting, layoutXml);
                log("  - Create Activity Result: " + result);
                stepHistory.add("CREATE_ACTIVITY: " + result);

                fragment.getActivity().runOnUiThread(() -> {
                    fragment.addSystemMessage("Create Activity: " + result);
                    fragment.refreshDesigner();
                });

                if (refinedGoal != null) {
                    process(originalPrompt, "Activity created: " + result + ". Proceeding to next step...");
                }
                break;
            }

            case "GET_PROJECT_STRUCTURE": {
                log("[CodeSpecialist.handleAction -> GET_PROJECT_STRUCTURE] Reading project structure...");
                String javaName = action.optString("javaName", getProjectFile() != null ? getProjectFile().getJavaName() : "MainActivity.java");
                String summaryStr = ProjectStructureAide.getProjectStructureSummary(getScId(), javaName);
                log("  - Structure Summary:\n" + summaryStr);
                stepHistory.add("GET_PROJECT_STRUCTURE:\n" + summaryStr);

                if (refinedGoal != null) {
                    process(originalPrompt, "Project structure retrieved:\n" + summaryStr + "\nProceeding to next step...");
                }
                break;
            }

            default:
                log("[CodeSpecialist.handleAction] WARNING: Unknown action type '" + type + "'. Raw action JSON:\n" + formatJson(action));
                break;
        }
    }

    private void applyGetLogicIr(JSONObject action) {
        String javaName = action.optString("javaName", getProjectFile() != null ? getProjectFile().getJavaName() : "main");
        String eventKey = LogicIrBridge.normalizeEventKey(action.optString("eventKey", "onCreate_initializeLogic"));
        log("[CodeSpecialist.applyGetLogicIr] Input -> javaName: " + javaName + ", eventKey: " + eventKey);

        jC.projectOperationsExecutor.execute(() -> {
            String scId = getScId();
            log("[CodeSpecialist.applyGetLogicIr] Loading IR from project (scId: " + scId + ", javaName: " + javaName + ", eventKey: " + eventKey + ")...");
            JSONObject ir = LogicIrBridge.loadIrFromProject(scId, javaName, eventKey);
            log("[CodeSpecialist.applyGetLogicIr -> RETURNED TO AI/UI] Loaded IR JSON Payload:\n" + formatJson(ir));

            String javaCode = LogicIrBridge.irToJava(ir);
            log("[CodeSpecialist.applyGetLogicIr -> RETURNED TO AI/UI] Generated Java Code Representation:\n" + (javaCode.isEmpty() ? "(Empty)" : javaCode));

            fragment.getActivity().runOnUiThread(() -> {
                fragment.addSystemMessage("--- EVENT LOGIC IR (" + javaName + " / " + eventKey + ") ---\n" +
                        "Java Representation:\n" + (javaCode.isEmpty() ? "(Empty)" : javaCode) + "\n\n" +
                        "IR JSON:\n" + ir.toString());
                log("[CodeSpecialist.applyGetLogicIr] Event Logic IR dispatched to UI system message.");
            });
        });
    }

    private void applyLogicIrWithGuardrails(JSONObject action) {
        Context context = getContext();
        if (context == null || fragment.getActivity() == null) {
            log("[CodeSpecialist.applyLogicIrWithGuardrails] ERROR: Context or Activity is null!");
            return;
        }

        String javaName = action.optString("javaName", getProjectFile() != null ? getProjectFile().getJavaName() : "main");
        String eventKey = LogicIrBridge.normalizeEventKey(action.optString("eventKey", "onCreate_initializeLogic"));
        log("[CodeSpecialist.applyLogicIrWithGuardrails] Input -> javaName: " + javaName + ", eventKey: " + eventKey);

        JSONObject proposedIr = action.optJSONObject("ir");
        if (proposedIr == null && action.has("javaCode")) {
            String suppliedJava = action.optString("javaCode");
            log("[CodeSpecialist.applyLogicIrWithGuardrails] Converting supplied javaCode to IR:\n" + suppliedJava);
            proposedIr = LogicIrBridge.javaToIr(suppliedJava);
        }

        if (proposedIr == null) {
            log("[CodeSpecialist.applyLogicIrWithGuardrails] GUARDRAIL ERROR: No valid IR JSON or javaCode supplied!");
            fragment.getActivity().runOnUiThread(() -> {
                fragment.addSystemMessage(" Guardrail Error: No valid IR JSON or javaCode supplied for APPLY_LOGIC_IR.");
            });
            return;
        }

        log("[CodeSpecialist.applyLogicIrWithGuardrails] Proposed IR JSON Payload:\n" + formatJson(proposedIr));
        setStatus("Validating proposed logic...");

        String scId = getScId();

        // Guardrail Step 1: Dry-run validation
        log("[CodeSpecialist.applyLogicIrWithGuardrails] Step 1: Performing dry-run validation...");
        LogicIrBridge.ValidationResult validation = LogicIrBridge.validateIr(proposedIr, scId, javaName);
        log("[CodeSpecialist.applyLogicIrWithGuardrails] Step 1 Validation Result -> isValid: " + validation.isValid +
                ", blockCount: " + validation.blockCount + ", errorMessage: " + validation.errorMessage);
        log("[CodeSpecialist.applyLogicIrWithGuardrails] Step 1 Generated Java:\n" + validation.generatedJava);

        if (!validation.isValid) {
            log("[CodeSpecialist.applyLogicIrWithGuardrails] GUARDRAIL ERROR: Logic validation failed! Reason: " + validation.errorMessage);
            fragment.getActivity().runOnUiThread(() -> {
                fragment.addSystemMessage(" Logic Guardrail Error: Proposed AI logic failed validation.\nReason: " + validation.errorMessage);
            });
            return;
        }

        // Guardrail Step 2: Generate Current vs Proposed Diff
        log("[CodeSpecialist.applyLogicIrWithGuardrails] Step 2: Loading current IR from project to build diff...");
        JSONObject currentIr = LogicIrBridge.loadIrFromProject(scId, javaName, eventKey);
        String currentJava = LogicIrBridge.irToJava(currentIr);
        String proposedJava = validation.generatedJava;

        log("[CodeSpecialist.applyLogicIrWithGuardrails] Step 2 Current Logic Java:\n" + (currentJava.isEmpty() ? "(Empty logic)" : currentJava));
        log("[CodeSpecialist.applyLogicIrWithGuardrails] Step 2 Proposed Logic Java:\n" + proposedJava);

        JSONObject finalIr = proposedIr;

        // Guardrail Step 3: User Confirmation & Diff Dialog
        log("[CodeSpecialist.applyLogicIrWithGuardrails] Step 3: Presenting UI Confirmation Dialog to user...");
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
                        log("[CodeSpecialist.applyLogicIrWithGuardrails] Step 4: User APPROVED logic change dialog.");
                        fragment.undoSnapshot = new ProjectSnapshot(getScId(), getProjectFile() != null ? getProjectFile().getXmlName() : "main.xml");
                        fragment.setUndoVisible(true);

                        // Guardrail Step 4: Backup original blocks before saving
                        ArrayList<BlockBean> originalBlocks = jC.a(scId).a(javaName, eventKey);
                        log("[CodeSpecialist.applyLogicIrWithGuardrails] Backed up " + (originalBlocks != null ? originalBlocks.size() : 0) + " original blocks.");

                        boolean success = LogicIrBridge.saveIrToProject(scId, javaName, eventKey, finalIr);
                        if (success) {
                            log("[CodeSpecialist.applyLogicIrWithGuardrails] SUCCESS: Logic saved to project file!");
                            fragment.setUndoVisible(true);
                            fragment.addSystemMessage(" Logic applied successfully to " + eventKey + " (" + validation.blockCount + " blocks).");
                        } else {
                            log("[CodeSpecialist.applyLogicIrWithGuardrails] ERROR: Failed to save logic! Restoring original blocks...");
                            fragment.addSystemMessage(" Failed to persist logic. Restoring original blocks.");
                            if (originalBlocks != null) {
                                eC manager = jC.a(scId);
                                if (manager != null) {
                                    manager.a(javaName, eventKey, originalBlocks);
                                    manager.k();
                                    log("[CodeSpecialist.applyLogicIrWithGuardrails] Restored original blocks.");
                                }
                            }
                        }
                    })
                    .setNegativeButton("Reject", (dialog, which) -> {
                        log("[CodeSpecialist.applyLogicIrWithGuardrails] User REJECTED logic change dialog.");
                        fragment.addSystemMessage(" Logic modification rejected by user.");
                    })
                    .setCancelable(false)
                    .show();
        });
    }

    private void applySearchMethod(String javaName, String methodName) {
        log("[CodeSpecialist.applySearchMethod] Input -> javaName: " + javaName + ", methodName: " + methodName);
        setStatus("Searching method: " + methodName);
        jC.projectOperationsExecutor.execute(() -> {
            String source = getGeneratedSource(javaName);
            log("[CodeSpecialist.applySearchMethod] Source Code Retrieved (" + (source != null ? source.length() : 0) + " chars)");
            if (source != null) {
                log("[CodeSpecialist.applySearchMethod] Invoking SourceCodeAide.findMethod...");
                JSONObject result = SourceCodeAide.findMethod(source, methodName);
                log("[CodeSpecialist.applySearchMethod -> RETURNED TO AI/UI] SourceCodeAide.findMethod Payload:\n" + formatJson(result));
                displaySourceAideResult("Search Method: " + methodName, result);
            } else {
                log("[CodeSpecialist.applySearchMethod] ERROR: Generated source code is null for " + javaName);
            }
        });
    }

    private void applyGetCodeRange(String javaName, int start, int end) {
        log("[CodeSpecialist.applyGetCodeRange] Input -> javaName: " + javaName + ", startLine: " + start + ", endLine: " + end);
        setStatus("Reading code range from " + start + " to " + end);
        jC.projectOperationsExecutor.execute(() -> {
            String source = getGeneratedSource(javaName);
            log("[CodeSpecialist.applyGetCodeRange] Source Code Retrieved (" + (source != null ? source.length() : 0) + " chars)");
            if (source != null) {
                log("[CodeSpecialist.applyGetCodeRange] Invoking SourceCodeAide.getLineRange...");
                JSONObject result = SourceCodeAide.getLineRange(source, start, end);
                log("[CodeSpecialist.applyGetCodeRange -> RETURNED TO AI/UI] SourceCodeAide.getLineRange Payload:\n" + formatJson(result));
                displaySourceAideResult("Code Range: " + start + "-" + end, result);
            } else {
                log("[CodeSpecialist.applyGetCodeRange] ERROR: Generated source code is null for " + javaName);
            }
        });
    }

    private void applyFindField(String javaName, String fieldName) {
        log("[CodeSpecialist.applyFindField] Input -> javaName: " + javaName + ", fieldName: " + fieldName);
        setStatus("Finding field: " + fieldName);
        jC.projectOperationsExecutor.execute(() -> {
            String source = getGeneratedSource(javaName);
            log("[CodeSpecialist.applyFindField] Source Code Retrieved (" + (source != null ? source.length() : 0) + " chars)");
            if (source != null) {
                log("[CodeSpecialist.applyFindField] Invoking SourceCodeAide.findField...");
                JSONObject result = SourceCodeAide.findField(source, fieldName);
                log("[CodeSpecialist.applyFindField -> RETURNED TO AI/UI] SourceCodeAide.findField Payload:\n" + formatJson(result));
                displaySourceAideResult("Find Field: " + fieldName, result);
            } else {
                log("[CodeSpecialist.applyFindField] ERROR: Generated source code is null for " + javaName);
            }
        });
    }

    private void applyGetPatchMeta(String javaName, int targetLine) {
        log("[CodeSpecialist.applyGetPatchMeta] Input -> javaName: " + javaName + ", targetLine: " + targetLine);
        setStatus("Calculating patch metadata from line: " + targetLine);
        jC.projectOperationsExecutor.execute(() -> {
            String source = getGeneratedSource(javaName);
            log("[CodeSpecialist.applyGetPatchMeta] Source Code Retrieved (" + (source != null ? source.length() : 0) + " chars)");
            if (source != null) {
                log("[CodeSpecialist.applyGetPatchMeta] Invoking SourceCodeAide.getCommandBlockMeta...");
                JSONObject result = SourceCodeAide.getCommandBlockMeta(source, targetLine);
                log("[CodeSpecialist.applyGetPatchMeta -> RETURNED TO AI/UI] SourceCodeAide.getCommandBlockMeta Payload:\n" + formatJson(result));
                displaySourceAideResult("Patch Meta for Line: " + targetLine, result);
            } else {
                log("[CodeSpecialist.applyGetPatchMeta] ERROR: Generated source code is null for " + javaName);
            }
        });
    }

    private void displaySourceAideResult(String title, JSONObject result) {
        log("[CodeSpecialist.displaySourceAideResult -> RETURNED TO AI/UI] Title: '" + title + "' | Status: '" + result.optString("status") + "'");
        log("[CodeSpecialist.displaySourceAideResult -> RETURNED TO AI/UI] Full Return Object:\n" + formatJson(result));

        fragment.getActivity().runOnUiThread(() -> {
            try {
                if ("success".equals(result.optString("status"))) {
                    String formatted = formatJson(result);
                    fragment.addSystemMessage("--- " + title + " ---\n" + formatted);
                    stepHistory.add(title + ": Success");
                    log("[CodeSpecialist.displaySourceAideResult] Appended to stepHistory: " + title + ": Success");
                } else {
                    fragment.addSystemMessage(title + " Failed: " + result.optString("message"));
                    stepHistory.add(title + ": Failed");
                    log("[CodeSpecialist.displaySourceAideResult] Appended to stepHistory: " + title + ": Failed");
                }
            } catch (Exception e) {
                log("[CodeSpecialist.displaySourceAideResult] ERROR formatting result: " + e.getMessage());
                fragment.addSystemMessage("Error parsing result: " + e.getMessage());
            }
        });
    }

    private String getGeneratedSource(String javaName) {
        try {
            ProjectFileBean projectFile = getProjectFile();
            if (projectFile == null) {
                log("[CodeSpecialist.getGeneratedSource] ProjectFileBean is null!");
                return null;
            }
            String scId = getScId();
            yq workspace = new yq(getContext(), scId);
            String fullJavaName = javaName.endsWith(".java") ? javaName : javaName + ".java";
            String path = workspace.projectMyscPath + "app" + File.separator + "src" + File.separator + "main" + File.separator + "java" + File.separator + fullJavaName;
            log("[CodeSpecialist.getGeneratedSource] Looking up source file at path: " + path);
            if (FileUtil.isExistFile(path)) {
                String content = FileUtil.readFile(path);
                log("[CodeSpecialist.getGeneratedSource] Source file read successfully (" + (content != null ? content.length() : 0) + " chars).");
                return content;
            } else {
                log("[CodeSpecialist.getGeneratedSource] Source file does NOT exist at path: " + path);
            }
        } catch (Exception e) {
            log("[CodeSpecialist.getGeneratedSource] ERROR reading source: " + e.getMessage());
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

        log("[CodeSpecialist.applyAddJavaCommand] Input Arguments:");
        log("  - javaName: " + javaName);
        log("  - reference: '" + reference + "'");
        log("  - distance: " + distance);
        log("  - after (front): " + after);
        log("  - before (back): " + before);
        log("  - command: '" + command + "'");
        log("  - inputCode:\n" + inputCode);

        if (!VALID_COMMANDS.contains(command.toLowerCase())) {
            log("[CodeSpecialist.applyAddJavaCommand] ERROR: Invalid Java Command '" + command + "'. Allowed commands: " + VALID_COMMANDS);
            fragment.getActivity().runOnUiThread(() ->
                    fragment.addSystemMessage("Invalid Java Command: " + command));
            return;
        }

        jC.projectOperationsExecutor.execute(() -> {
            log("[CodeSpecialist.applyAddJavaCommand] Invoking SourceCodeAide.addJavaCommandToManager...");
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

            log("[CodeSpecialist.applyAddJavaCommand -> RETURNED TO AI/UI] Return Payload from SourceCodeAide:\n" + formatJson(result));

            fragment.getActivity().runOnUiThread(() -> {
                if ("success".equals(result.optString("status"))) {
                    fragment.addSystemMessage(" Java Command added successfully (" + command + " near '" + reference + "')");
                    String stepMsg = "ADD_JAVA_COMMAND: " + command + " near '" + reference + "'";
                    stepHistory.add(stepMsg);
                    log("[CodeSpecialist.applyAddJavaCommand] Added to stepHistory: " + stepMsg);
                } else {
                    fragment.addSystemMessage(" Failed to add Java Command: " + result.optString("message"));
                    stepHistory.add("ADD_JAVA_COMMAND: Failed");
                    log("[CodeSpecialist.applyAddJavaCommand] Added to stepHistory: ADD_JAVA_COMMAND: Failed (Reason: " + result.optString("message") + ")");
                }
            });
        });
    }

    public void applyAddBlock(String eventKey, String opCode, JSONArray jParams, boolean isSync) {
        final String normalizedEventKey = LogicIrBridge.normalizeEventKey(eventKey);
        log("[CodeSpecialist.applyAddBlock] Input Arguments:");
        log("  - eventKey: " + eventKey + " (normalized: " + normalizedEventKey + ")");
        log("  - opCode: " + opCode);
        log("  - jParams: " + (jParams != null ? jParams.toString() : "null"));
        log("  - isSync: " + isSync);

        if (normalizedEventKey == null || opCode == null) {
            log("[CodeSpecialist.applyAddBlock] ERROR: eventKey or opCode is null!");
            return;
        }

        fragment.undoSnapshot = new ProjectSnapshot(getScId(), getProjectFile() != null ? getProjectFile().getXmlName() : "main.xml");
        fragment.setUndoVisible(true);
        log("[CodeSpecialist.applyAddBlock] Snapshot created for undo.");

        Runnable r = () -> {
            String scId = getScId();
            eC dataManager = jC.a(scId);
            String javaName = getProjectFile() != null ? getProjectFile().getJavaName() : "MainActivity.java";
            log("[CodeSpecialist.applyAddBlock] Target Java Name: " + javaName + ", Event Key: " + normalizedEventKey);

            synchronized (dataManager) {
                boolean registered = LogicIrBridge.ensureEventRegistered(dataManager, javaName, normalizedEventKey);
                if (!registered) {
                    log("[CodeSpecialist.applyAddBlock] ERROR: Could not register event " + normalizedEventKey + " for " + javaName);
                    return;
                }
                ArrayList<BlockBean> blocks = dataManager.a(javaName, normalizedEventKey);
                if (blocks == null) {
                    blocks = new ArrayList<>();
                }

                int maxId = 0;
                for (BlockBean b : blocks) {
                    try { maxId = Math.max(maxId, Integer.parseInt(b.id)); } catch (Exception ignored) {}
                }
                String newBlockId = String.valueOf(maxId + 1);
                log("[CodeSpecialist.applyAddBlock] Calculated new Block ID: " + newBlockId + " (existing blocks: " + blocks.size() + ")");

                ArrayList<HashMap<String, Object>> blockList = new ArrayList<>();
                BlocksHandler.builtInBlocks(blockList);
                String type = " ", typeName = "", spec = "";
                boolean foundInBuiltIn = false;
                for (HashMap<String, Object> bDef : blockList) {
                    if (opCode.equals(bDef.get("name"))) {
                        type = (String) bDef.getOrDefault("type", " ");
                        typeName = (String) bDef.getOrDefault("typeName", "");
                        spec = (String) bDef.getOrDefault("spec", "");
                        foundInBuiltIn = true;
                        break;
                    }
                }

                // If opCode not found in built-in blocks, fall back to addSourceDirectly
                // so that the AI's manual code is always safely placed inside a valid block.
                if (!foundInBuiltIn) {
                    log("[CodeSpecialist.applyAddBlock] opCode '" + opCode + "' not found in built-in blocks. Falling back to addSourceDirectly with opCode as code parameter.");
                    // Look up addSourceDirectly definition
                    for (HashMap<String, Object> bDef : blockList) {
                        if ("addSourceDirectly".equals(bDef.get("name"))) {
                            type = (String) bDef.getOrDefault("type", "e");
                            typeName = (String) bDef.getOrDefault("typeName", "");
                            spec = (String) bDef.getOrDefault("spec", "");
                            break;
                        }
                    }
                    // Build raw code from opCode + params so the intent is preserved
                    StringBuilder rawCode = new StringBuilder(opCode);
                    if (jParams != null && jParams.length() > 0) {
                        rawCode.append("(");
                        for (int i = 0; i < jParams.length(); i++) {
                            try {
                                if (i > 0) rawCode.append(", ");
                                rawCode.append(jParams.getString(i));
                            } catch (Exception ignored) {}
                        }
                        rawCode.append(")");
                    }
                    BlockBean block = new BlockBean(newBlockId, spec, type, typeName, "addSourceDirectly");
                    block.parameters.add(rawCode.toString());
                    blocks.add(block);
                    dataManager.k();
                    log("[CodeSpecialist.applyAddBlock] Fallback addSourceDirectly block added with code: " + rawCode);
                } else {
                    log("[CodeSpecialist.applyAddBlock] Matched Built-in Block Definition -> opCode: " + opCode + ", type: '" + type + "', typeName: '" + typeName + "', spec: '" + spec + "'");

                    BlockBean block = new BlockBean(newBlockId, spec, type, typeName, opCode);
                    if (jParams != null) {
                        for (int i = 0; i < jParams.length(); i++) {
                            try {
                                String paramVal = jParams.getString(i);
                                block.parameters.add(paramVal);
                                log("  - Parameter [" + i + "]: " + paramVal);
                            } catch (Exception e) {
                                log("  - Parameter [" + i + "] ERROR parsing: " + e.getMessage());
                            }
                        }
                    }
                    blocks.add(block);
                    dataManager.k();
                    log("[CodeSpecialist.applyAddBlock] BlockBean [id=" + newBlockId + ", opCode=" + opCode + "] added and dataManager saved successfully!");
                }
            }
        };

        if (isSync) {
            r.run();
        } else {
            jC.projectOperationsExecutor.execute(r);
        }
    }

    public void applyImport(String importPath) {
        log("[CodeSpecialist.applyImport] Input -> importPath: " + importPath);

        if (importPath == null || importPath.trim().isEmpty()) {
            log("[CodeSpecialist.applyImport] ERROR: importPath is empty or null!");
            return;
        }

        fragment.undoSnapshot = new ProjectSnapshot(getScId(), getProjectFile() != null ? getProjectFile().getXmlName() : "main");
        String javaName = getProjectFile() != null ? getProjectFile().getJavaName() : "main";
        log("[CodeSpecialist.applyImport] Injecting import into " + javaName + " (scId: " + getScId() + ")...");

        boolean success = AssistantImportInjector.injectImports(getScId(), javaName, new String[]{importPath}, fragment.getActivity());
        log("[CodeSpecialist.applyImport] AssistantImportInjector Result -> Success: " + success + " for importPath: " + importPath);

        if (success) {
            fragment.addSystemMessage("Import added: " + importPath);
        } else {
            fragment.addSystemMessage("Import already exists or failed to add: " + importPath);
        }
    }
}
