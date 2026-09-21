package sketchware.plus.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.ProjectFileBean;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

import a.a.a.jC;
import a.a.a.yq;
import mod.hilal.saif.components.ComponentsHandler;
import sketchware.plus.utility.SketchwareUtil;

/**
 * The core engine of the autonomous SK Assistant.
 * Handles the "Thought -> Tool Call -> Execution -> Result" loop.
 */
public class ToolOrchestrator {

    private final SkAssistantFragment fragment;
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private static final int MAX_ITERATIONS = 10;
    private int iterationCount = 0;
    private boolean isCanceled = false;

    private static final String[] THINKING_PHRASES = {
        "Analyzing project structure...",
        "Scanning source code...",
        "Reasoning about the task...",
        "Formulating a plan...",
        "Mapping project dependencies...",
        "Architecting changes...",
        "Deep diving into logic...",
        "Validating Sketchware context...",
        "Thinking ahead..."
    };

    public ToolOrchestrator(SkAssistantFragment fragment) {
        this.fragment = fragment;
        this.context = fragment.getContext();
    }

    /**
     * Starts the autonomous session with the user's prompt.
     */
    public void start(String userPrompt) {
        iterationCount = 0;
        isCanceled = false;
        
        // Start a fresh history for this specific request only to prevent context distraction
        JSONArray freshSessionHistory = new JSONArray();
        try {
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);
            freshSessionHistory.put(userMsg);
        } catch (JSONException ignored) {}

        executeLoop(freshSessionHistory);
    }

    public void cancel() {
        isCanceled = true;
        AiClient.cancelCurrentRequest();
    }

    private void executeLoop(JSONArray chatHistory) {
        if (isCanceled) return;
        
        if (iterationCount >= MAX_ITERATIONS) {
            fragment.addSystemMessage("maxed out reasoning limit. Adjust your request or reply to proceed.");
            fragment.setStatus(null);
            return;
        }

        iterationCount++;
        fragment.setStatus(getRandomThinkingPhrase());

        String systemPrompt = "You are the Autonomous SK Assistant for Sketchware Plus.\n" +
                "Project Context: " + fragment.scId + " (" + fragment.projectFile.getJavaName() + ")\n\n" +
                "STRATEGY & RULES:\n" +
                "1. GATHER INFO FIRST: Use 'get_layout_xml', 'list_methods', 'list_project_files', 'search_project_files', 'search_in_code', or 'list_available_components' to see existing project structure.\n" +
                "2. NO HALLUCINATION: Only call tools that are explicitly defined in the provided tools list. Do NOT invent tool names.\n" +
                "3. NO REDUNDANT SEARCH: Do NOT search the web for Sketchware-specific component IDs. Use 'list_available_components' to find the correct IDs for both built-in and local components.\n" +
                "4. ACTION-ORIENTED: Once you know what to do, use the tool IMMEDIATELY. Don't waste reasoning steps on web searches if a tool provides the info.\n" +
                "5. MODIFY CODE: To patch logic, use 'read_method' to find the anchor, then 'add_java_patch'.\n" +
                "6. LIBRARY MANAGEMENT: You can ONLY manage LOCAL libraries using 'manage_local_library'. Check 'AVAILABLE LOCAL LIBRARIES' in the context first. You CANNOT enable or disable built-in libraries (AppCompat, Firebase, AdMob, Google Maps) as you don't have access to them.\n" +
                "7. PRECISION: When enabling or disabling a local library, use the EXACT folder name from the available list (including version numbers).\n" +
                "8. COMPLETION: Summarize your changes once done. Don't call tools in the final response.\n" +
                "9. JSON STRICTNESS: Ensure all tool arguments are valid JSON objects stringified.";

        JSONArray tools = AssistantToolRegistry.getAllTools();

        AiClient.askAi(context, systemPrompt, chatHistory, AiClient.AiTemperatureType.ASSISTANT_MODE, tools, new AiClient.AiCallback() {
            @Override
            public void onSuccess(String response, int promptTokens, int completionTokens, int totalTokens) {
                mainHandler.post(() -> {
                    fragment.updateTokenUsage(promptTokens, completionTokens, totalTokens);
                    fragment.addAssistantMessage(response);
                    fragment.setStatus(null);
                });
            }

            @Override
            public void onToolCall(JSONArray toolCalls, String thought, int promptTokens, int completionTokens, int totalTokens) {
                mainHandler.post(() -> {
                    fragment.updateTokenUsage(promptTokens, completionTokens, totalTokens);
                    if (thought != null && !thought.trim().isEmpty()) {
                        fragment.setStatus(thought.trim());
                    }
                    handleToolCalls(toolCalls, thought, chatHistory);
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    fragment.setStatus(null);
                    fragment.addSystemMessage("Error: " + error);
                });
            }

            @Override
            public void onRetry(int retryCount, long delayMillis) {
                mainHandler.post(() -> {
                    fragment.setStatus("Rate limit reached. Retrying in " + String.format("%.1f", delayMillis / 1000.0) + "s... (Attempt " + retryCount + ")");
                });
            }

            @Override
            public void onSuccess(String response) {
                onSuccess(response, 0, 0, 0);
            }
        });
    }

    private void handleToolCalls(JSONArray toolCalls, String thought, JSONArray chatHistory) {
        if (isCanceled) return;

        jC.projectOperationsExecutor.execute(() -> {
            try {
                // Prepare sanitized tool calls for history
                JSONArray sanitizedToolCalls = new JSONArray();
                for (int i = 0; i < toolCalls.length(); i++) {
                    JSONObject originalCall = toolCalls.getJSONObject(i);
                    JSONObject sanitizedCall = new JSONObject(originalCall.toString());
                    
                    // CRITICAL: Ensure 'arguments' is a STRING, even if the model sent an object
                    if (sanitizedCall.has("function")) {
                        JSONObject function = sanitizedCall.getJSONObject("function");
                        Object args = function.opt("arguments");
                        if (args != null && !(args instanceof String)) {
                            function.put("arguments", args.toString());
                        }
                    }
                    sanitizedToolCalls.put(sanitizedCall);
                }

                // Add the tool_calls message from assistant to history
                JSONObject assistantMsg = new JSONObject();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", thought); // Include thoughts if present
                assistantMsg.put("tool_calls", sanitizedToolCalls);
                chatHistory.put(assistantMsg);

                for (int i = 0; i < toolCalls.length(); i++) {
                    JSONObject call = toolCalls.getJSONObject(i);
                    String id = call.getString("id");
                    JSONObject function = call.getJSONObject("function");
                    String name = function.getString("name");
                    
                    Object argsRaw = function.get("arguments");
                    JSONObject args;
                    if (argsRaw instanceof String) {
                        String argsStr = (String) argsRaw;
                        // Robust repair for common model glitches like {""}, {" "}, or missing closing brace
                        String trimmed = argsStr.trim();
                        if (trimmed.equals("{\"\"}") || trimmed.equals("{ \"\" }") || trimmed.equals("{}") || trimmed.isEmpty()) {
                            argsStr = "{}";
                        }
                        try {
                            args = new JSONObject(argsStr);
                        } catch (JSONException e) {
                            args = new JSONObject(); // Fallback to empty for malformed arguments
                        }
                    } else if (argsRaw instanceof JSONObject) {
                        args = (JSONObject) argsRaw;
                    } else {
                        args = new JSONObject();
                    }

                    fragment.setStatus("Executing: " + name + "...");
                    String result = dispatchTool(name, args);

                    // Add tool result to history
                    JSONObject toolResultMsg = new JSONObject();
                    toolResultMsg.put("role", "tool");
                    toolResultMsg.put("tool_call_id", id);
                    toolResultMsg.put("name", name);
                    toolResultMsg.put("content", result);
                    chatHistory.put(toolResultMsg);
                }

                mainHandler.post(() -> executeLoop(chatHistory));

            } catch (Exception e) {
                mainHandler.post(() -> {
                    fragment.setStatus(null);
                    fragment.addSystemMessage("Tool Dispatch Error: " + e.getMessage());
                });
            }
        });
    }

    private String dispatchTool(String name, JSONObject args) throws Exception {
        switch (name) {
            case "web_search":
                return WebSearchAide.searchWeb(args.getString("query")).toString();
                
            case "web_browse":
                return WebSearchAide.browseWebPage(args.getString("url")).toString();

            case "list_project_files":
                JSONObject fileList = new JSONObject();
                JSONArray files = new JSONArray();
                for (ProjectFileBean pf : jC.b(fragment.scId).b()) {
                    JSONObject file = new JSONObject();
                    file.put("java", pf.getJavaName());
                    file.put("xml", pf.getXmlName());
                    files.put(file);
                }
                fileList.put("files", files);
                return fileList.toString();

            case "search_project_files":
                String sQuery = args.getString("query").toLowerCase();
                JSONObject sResult = new JSONObject();
                JSONArray sFiles = new JSONArray();
                for (ProjectFileBean pf : jC.b(fragment.scId).b()) {
                    if (pf.getJavaName().toLowerCase().contains(sQuery) || pf.getXmlName().toLowerCase().contains(sQuery)) {
                        JSONObject file = new JSONObject();
                        file.put("java", pf.getJavaName());
                        file.put("xml", pf.getXmlName());
                        sFiles.put(file);
                    }
                }
                sResult.put("files", sFiles);
                return sResult.toString();

            case "get_layout_xml":
                return SketchwareXmlBridge.getRawXml(context, fragment.scId, fragment.projectFile);

            case "apply_layout_xml":
                String xml = args.getString("xml");
                String summary = args.optString("summary", "Modified layout");
                boolean success = SketchwareXmlBridge.applyAiXmlToSketchware(context, fragment.scId, fragment.projectFile.getXmlName(), xml);
                return success ? "Success: " + summary : "Error: Failed to apply XML. Check syntax.";

            case "inject_imports":
                JSONArray pkgs = args.getJSONArray("packages");
                String[] pArray = new String[pkgs.length()];
                for(int i=0; i<pkgs.length(); i++) pArray[i] = pkgs.getString(i);
                boolean injected = AssistantImportInjector.injectImportsToCurrentActivity(fragment.getActivity(), pArray);
                return injected ? "Imports injected successfully." : "Imports already exist or injection failed.";

            case "list_methods":
            case "get_full_code":
            case "read_method":
            case "search_in_code":
                String javaName = args.optString("javaName", fragment.projectFile.getJavaName());
                String source = new yq(context, fragment.scId).getFileSrc(
                    javaName, 
                    jC.b(fragment.scId),
                    jC.a(fragment.scId),
                    jC.c(fragment.scId)
                );
                if ("list_methods".equals(name)) {
                    return SourceCodeAide.listMethods(source).toString();
                } else if ("get_full_code".equals(name)) {
                    return source;
                } else if ("search_in_code".equals(name)) {
                    return SourceCodeAide.findSnippet(source, args.getString("query"), args.optInt("contextLines", 2)).toString();
                } else {
                    return SourceCodeAide.findMethod(source, args.getString("methodName")).toString();
                }

            case "add_java_patch":
                JSONObject patch = SourceCodeAide.addJavaCommandToManager(
                    context, fragment.scId, args.getString("javaName"),
                    args.getString("reference"), args.optInt("distance", 0),
                    args.optInt("front", 0), args.optInt("back", 0),
                    args.getString("command"), args.getString("inputCode")
                );
                return patch.toString();

            case "manage_local_library":
                String libName = args.getString("libraryName");
                boolean libEnabled = args.getBoolean("enabled");
                mainHandler.post(() -> {
                    if (libEnabled) fragment.librarySpecialist.applyLocalLibrary(libName);
                    else fragment.librarySpecialist.disableLocalLibrary(libName);
                });
                return "Request to " + (libEnabled ? "enable" : "disable") + " local library '" + libName + "' sent.";

            case "add_permission":
                String perm = args.getString("permission");
                mainHandler.post(() -> fragment.manifestSpecialist.applyPermission(perm));
                return "Permission " + perm + " added successfully.";

            case "add_component":
                int typeId = args.getInt("type");
                String compId = args.getString("id");
                mainHandler.post(() -> fragment.componentSpecialist.applyAddComponent(typeId, compId));
                return "Component " + compId + " added successfully.";

            case "list_available_components":
                JSONObject components = new JSONObject();
                JSONArray builtIn = new JSONArray();
                // Core types from ComponentBean
                int[] coreTypes = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 30, 31, 32, 33};
                for (int type : coreTypes) {
                    JSONObject c = new JSONObject();
                    c.put("id", type);
                    c.put("name", ComponentBean.getComponentTypeName(type));
                    builtIn.put(c);
                }
                components.put("built_in", builtIn);

                JSONArray local = new JSONArray();
                ArrayList<HashMap<String, Object>> custom = ComponentsHandler.getCustomComponents();
                if (custom != null) {
                    for (HashMap<String, Object> map : custom) {
                        JSONObject c = new JSONObject();
                        c.put("id", Integer.parseInt(map.get("id").toString()));
                        c.put("name", map.get("name"));
                        c.put("typeName", map.get("typeName"));
                        local.put(c);
                    }
                }
                components.put("local_custom", local);
                return components.toString();

            case "apply_custom_view":
                String cvName = args.getString("name");
                String cvXml = args.getString("xml");
                mainHandler.post(() -> fragment.layoutSpecialist.applyCustomView(cvName, cvXml));
                return "Prompting user to apply custom view: " + cvName;

            default:
                return "Error: Unknown tool '" + name + "'";
        }
    }

    private String getRandomThinkingPhrase() {
        int index = (int) (Math.random() * THINKING_PHRASES.length);
        return THINKING_PHRASES[index] + "....";
    }
}
