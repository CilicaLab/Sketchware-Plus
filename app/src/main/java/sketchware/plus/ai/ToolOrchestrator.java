package sketchware.plus.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.ProjectFileBean;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.eC;
import a.a.a.jC;
import a.a.a.yq;
import mod.hilal.saif.components.ComponentsHandler;
import sketchware.plus.ai.specialists.ComponentSpecialist;
import sketchware.plus.utility.HapticManager;
import sketchware.plus.utility.SketchwareUtil;

/**
 * The core engine of the autonomous SK Assistant.
 * Handles the "Thought -> Tool Call -> Execution -> Result" loop with optimized execution.
 * 
 * Improvements:
 * - ToolExecutor runs read-only tools in parallel for speed
 * - Tool results are cached to avoid redundant API calls
 * - Adaptive rate limiting adjusts delays based on tool type
 * - Execution metrics help AI make smarter decisions
 */
public class ToolOrchestrator {

    private final SkAssistantFragment fragment;
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ToolExecutor toolExecutor;
    
    private static final int MAX_ITERATIONS = 30;
    private int iterationCount = 0;
    private boolean isCanceled = false;
    
    private String baseSystemPrompt = null;
    private String lastAssistantMessage = "";
    private ExecutionMetrics executionMetrics = new ExecutionMetrics();
    private String lastToolName = null;
    private String lastToolArgs = null;
    private int duplicateToolCallCount = 0;
    private int consecutiveReadSearchCount = 0;

    public ToolOrchestrator(SkAssistantFragment fragment) {
        this.fragment = fragment;
        this.context = fragment.getContext();
        this.toolExecutor = new ToolExecutor(this);
    }

    public SkAssistantFragment getFragment() {
        return fragment;
    }

    public Context getContext() {
        return context;
    }

    /**
     * Starts the autonomous session with the user's prompt.
     */
    public void start(String userPrompt) {
        if (fragment != null) {
            fragment.log("ToolOrchestrator.start - userPrompt: " + userPrompt);
        }
        iterationCount = 0;
        isCanceled = false;
        executionMetrics = new ExecutionMetrics();
        lastAssistantMessage = "";
        baseSystemPrompt = null;  // Reset for fresh build
        lastToolName = null;
        lastToolArgs = null;
        duplicateToolCallCount = 0;
        consecutiveReadSearchCount = 0;

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
        if (fragment != null) {
            fragment.log("ToolOrchestrator.cancel called");
            fragment.setStatus(null);
        }
        isCanceled = true;
        AiClient.cancelCurrentRequest();
    }

    public boolean isCanceled() {
        return isCanceled;
    }

    private void executeLoop(JSONArray chatHistory) {
        if (fragment != null) {
            fragment.log("ToolOrchestrator.executeLoop - iteration: " + iterationCount);
        }
        if (isCanceled) return;
        
        if (iterationCount >= MAX_ITERATIONS) {
            fragment.setStatus("Reasoning limit reached (10 iterations). Summarizing progress...");

            // Prepare history for summary generation by appending a summary request prompt
            JSONArray summaryHistory = new JSONArray();
            for (int i = 0; i < chatHistory.length(); i++) {
                try {
                    summaryHistory.put(chatHistory.getJSONObject(i));
                } catch (JSONException ignored) {}
            }

            try {
                JSONObject summaryPrompt = new JSONObject();
                summaryPrompt.put("role", "user");
                summaryPrompt.put("content", "Reasoning limit reached (10/10 iterations). Summarize concisely everything that has been accomplished so far, including files, layouts, or settings modified, and what remains to be done. Do NOT attempt to call any tools.");
                summaryHistory.put(summaryPrompt);
            } catch (JSONException ignored) {}

            String summarySystemPrompt = baseSystemPrompt != null ? baseSystemPrompt : buildBaseSystemPrompt();
            summarySystemPrompt += "\n\nIMPORTANT: You have reached the maximum reasoning iteration limit. Summarize all accomplishments and changes made so far. Do NOT call any tools.";

            // Execute a final summary call with tools = null so the LLM responds with a text summary
            AiClient.askAi(context, summarySystemPrompt, summaryHistory, AiClient.AiTemperatureType.ASSISTANT_MODE, null, new AiClient.AiCallback() {
                @Override
                public void onSuccess(String response, int promptTokens, int completionTokens, int totalTokens) {
                    executionMetrics.recordApiCall(promptTokens, completionTokens, totalTokens);
                    mainHandler.post(() -> {
                        fragment.updateTokenUsage(promptTokens, completionTokens, totalTokens);
                        fragment.addSystemMessage(" Reasoning limit reached (10/10 iterations).\n\nSummary of accomplishments:\n" + response);
                        fragment.setStatus(null);
                    });
                }

                @Override
                public void onError(String error) {
                    mainHandler.post(() -> {
                        fragment.setStatus(null);
                        fragment.addSystemMessage(" Reasoning limit reached (10/10 iterations).\n" + executionMetrics.summary());
                    });
                }

                @Override
                public void onSuccess(String response) {
                    onSuccess(response, 0, 0, 0);
                }
            });
            return;
        }

        iterationCount++;
        fragment.setStatus("Picturing...");

        // Build base system prompt once and cache it
        if (baseSystemPrompt == null) {
            baseSystemPrompt = buildBaseSystemPrompt();
        }
        
        // Add dynamic metrics context
        String metricsContext = buildMetricsContext();
        String projectStateSummary = buildProjectStateSummary(chatHistory);
        String finalSystemPrompt = baseSystemPrompt + metricsContext + "\n\nPROJECT STATE SUMMARY:\n" + projectStateSummary;

        // Prune older large tool outputs in history to remain within rate limits (ITPM / TPM limits)
        pruneHistory(chatHistory);

        JSONArray tools = AssistantToolRegistry.getAllTools();

        AiClient.askAi(context, finalSystemPrompt, chatHistory, AiClient.AiTemperatureType.ASSISTANT_MODE, tools, new AiClient.AiCallback() {
            @Override
            public void onSuccess(String response, int promptTokens, int completionTokens, int totalTokens) {
                if (isCanceled) return;
                lastAssistantMessage = response;
                executionMetrics.recordApiCall(promptTokens, completionTokens, totalTokens);
                
                mainHandler.post(() -> {
                    if (isCanceled) return;
                    fragment.updateTokenUsage(promptTokens, completionTokens, totalTokens);
                    fragment.addAssistantMessage(response);
                    fragment.finishAiRun();
                });
            }

            @Override
            public void onToolCall(JSONArray toolCalls, String thought, int promptTokens, int completionTokens, int totalTokens) {
                if (isCanceled) return;
                executionMetrics.recordApiCall(promptTokens, completionTokens, totalTokens);
                
                mainHandler.post(() -> {
                    if (isCanceled) return;
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
                    fragment.finishAiRun();
                    if (!isCanceled && !"Canceled".equals(error)) {
                        fragment.restoreFailedPromptToEditText(error);
                        fragment.addSystemMessage("Error: " + error);
                    }
                });
            }

            @Override
            public void onRetry(int retryCount, long delayMillis) {
                mainHandler.post(() -> {
                    if (isCanceled) return;
                    SharedPreferences aiPref = fragment.getContext() != null ? fragment.getContext().getSharedPreferences("P12", Context.MODE_PRIVATE) : null;
                    String model = aiPref != null ? aiPref.getString("P12I5", "AI") : "AI";
                    if (model == null || model.isEmpty()) model = "AI";

                    final String finalModel = model;
                    final long totalSeconds = Math.max(1L, delayMillis / 1000L);
                    fragment.setStatus(finalModel + " is truncating... 1s");
                    for (int sec = 1; sec <= totalSeconds; sec++) {
                        final int currentSec = sec;
                        mainHandler.postDelayed(() -> {
                            if (!isCanceled) {
                                fragment.setStatus(finalModel + " is generating... " + currentSec + "s");
                            }
                        }, currentSec * 1000L);
                    }
                });
            }

            @Override
            public void onModelSwitched(String newModel) {
                mainHandler.post(() -> {
                    fragment.updateCurrentModelBadge();

                   /** fragment.addSystemMessage("Rate limit reached (>10s). Switched active model to " + newModel);***/
                });
            }

            @Override
            public void onSuccess(String response) {
                onSuccess(response, 0, 0, 0);
            }
        });
    }

    private String buildBaseSystemPrompt() {
        if (fragment.getActivity() instanceof SkAssistantDialog.CodeEditorProvider) {
            SkAssistantDialog.CodeEditorProvider p = (SkAssistantDialog.CodeEditorProvider) fragment.getActivity();
            return "You're SK Assistant, helping edit " + p.getFileName() + " in Sketchware Plus's code editor.\n" +
                    "Current file:\n" + p.getCodeText() + "\n\n" +
                    "If the user is just chatting or asking something, answer normally. " +
                    "If they want a change, use update_layout_xml, modify_java_file or modify_manifest_xml with the full updated code in 'xml' or 'inputCode'. " +
                    "Use search_chat_history only if you need earlier context. " +
                    "Only use tools you were given, never repeat a tool call that already worked, and finish with a short summary.\n" +
                    "Always keep the 'package <name>;' line at the top of Java/Kotlin files.";
        }
        return "You're SK Assistant, helping with the Sketchware Plus project " + fragment.scId +
                " (" + fragment.projectFile.getJavaName() + ").\n\n" +
                "If the user is just chatting or asking for advice, answer normally. " +
                "For project work, look before you change things (get_layout_xml, view_file_outline, list_methods, find_manifest_entries, list_files, find_files, grep_code, list_components) " +
                "and reuse what you already found instead of re-fetching. Don't loop on searches, stop after about 6 and answer. " +
                "Use search_chat_history only if you need earlier context. Only use tools you were given, and use list_components for component IDs instead of web searching.\n\n" +
                "Important stuff:\n" +
                "- Layout/view XML goes through update_layout_xml only. AndroidManifest.xml goes through modify_manifest_xml only.\n" +
                "- toggle_local_library works on local libs only, using the exact folder name (with version) from AVAILABLE LOCAL LIBRARIES. Built-in libs are read-only.\n" +
                "- Tool args must be strict valid JSON, never raw XML tool tags.\n" +
                "- Never repeat a tool call that already succeeded. Do it once, then reply.\n" +
                "- Refuse anything destructive, out of scope, or likely to corrupt the project, and say why.\n\n" +
                "Good to know: if the user doesn't name a new variable/list/activity/More Block/component, leave the name empty and it gets auto-generated. " +
                "For a More Block with manual Java, put the code in 'body_java' of add_more_block. " +
                "For an existing More Block or event, use add_source_code with 'moreBlockName' or 'eventKey'.\n\n" +
                "When done, give a short summary and stop calling tools.";
    }

    private String buildMetricsContext() {
        return "\n\nIter " + iterationCount + "/" + MAX_ITERATIONS +
                ", tokens used: " + executionMetrics.totalTokensUsed;
    }

    private void pruneHistory(JSONArray chatHistory) {
        int len = chatHistory.length();
        if (len <= 6) return;

        for (int i = 0; i < len - 4; i++) {
            try {
                JSONObject msg = chatHistory.getJSONObject(i);
                String role = msg.optString("role", "");
                String content = msg.optString("content", "");

                if ("tool".equals(role) && content != null && !content.isEmpty()) {
                    msg.put("content", compressToolResult(msg.optString("name", "tool"), content));
                } else if ("assistant".equals(role) && content != null && content.length() > 1500) {
                    msg.put("content", content.substring(0, 700) + "\n... [assistant reasoning compressed] ...\n" + content.substring(Math.max(0, content.length() - 300)));
                }
            } catch (JSONException ignored) {}
        }
    }

    private String buildProjectStateSummary(JSONArray chatHistory) {
        ArrayList<String> recentFiles = new ArrayList<>();
        ArrayList<String> recentActions = new ArrayList<>();
        ArrayList<String> contextNotes = new ArrayList<>();

        for (int i = 0; i < chatHistory.length(); i++) {
            try {
                JSONObject msg = chatHistory.getJSONObject(i);
                String content = msg.optString("content", "");
                String role = msg.optString("role", "");
                String toolName = msg.optString("name", "");

                if ("tool".equals(role) && !toolName.isEmpty()) {
                    recentActions.add(toolName);
                }

                if (content != null && !content.trim().isEmpty()) {
                    Matcher fileMatcher = Pattern.compile("([A-Za-z0-9_./-]+\\.(?:java|kt|xml|gradle))").matcher(content);
                    while (fileMatcher.find()) {
                        String fileName = fileMatcher.group(1);
                        if (!recentFiles.contains(fileName)) {
                            recentFiles.add(fileName);
                        }
                    }

                    if (content.contains("AndroidManifest") || content.contains("uses-permission") || content.contains("application")) {
                        contextNotes.add("manifest");
                    }
                    if (content.contains("LinearLayout") || content.contains("RelativeLayout") || content.contains("layout") || content.contains("android:")) {
                        contextNotes.add("layout");
                    }
                    if (content.contains("onCreate") || content.contains("method") || content.contains("class")) {
                        contextNotes.add("code");
                    }
                }
            } catch (JSONException ignored) {}
        }

        StringBuilder summary = new StringBuilder();
        try {
            if (fragment != null && fragment.scId != null) {
                String xmlName = fragment.projectFile != null ? fragment.projectFile.getXmlName() : "main.xml";
                ProjectSnapshot snapshot = new ProjectSnapshot(fragment.scId, xmlName);
                summary.append(snapshot.toStateSummaryString()).append("\n\n");
            }
        } catch (Exception ignored) {}

        summary.append("Recent tool actions: ").append(recentActions.isEmpty() ? "none" : joinLast(recentActions, 6));
        if (!recentFiles.isEmpty()) {
            summary.append("\nFiles recently referenced: ").append(joinLast(recentFiles, 8));
        }
        if (!contextNotes.isEmpty()) {
            summary.append("\nCurrent context: ").append(joinLast(contextNotes, 4));
        }
        summary.append("\nKeep only the latest relevant facts. Do not re-read large XML or source blobs unless required.");
        return summary.toString();
    }

    private String compressToolResult(String toolName, String content) {
        if (content == null) {
            return "";
        }

        String raw = content.trim();
        if (raw.isEmpty()) {
            return raw;
        }

        final int maxChars = 900;
        if (raw.length() <= maxChars) {
            return raw;
        }

        String snippet = raw;
        String prefix = raw.substring(0, Math.min(350, raw.length()));
        String suffix = raw.substring(Math.max(0, raw.length() - 220));
        snippet = prefix + "\n... [compressed tool output; omitted middle section] ...\n" + suffix;

        if (snippet.length() > maxChars) {
            snippet = snippet.substring(0, maxChars - 80) + "\n... [truncated]";
        }

        return "Tool output for '" + toolName + "' compressed to a relevant snippet:\n" + snippet;
    }

    private String joinLast(ArrayList<String> values, int maxCount) {
        if (values.isEmpty()) {
            return "none";
        }

        int start = Math.max(0, values.size() - maxCount);
        StringBuilder result = new StringBuilder();
        for (int i = start; i < values.size(); i++) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(values.get(i));
        }
        return result.toString();
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
                    
                    if (sanitizedCall.has("function")) {
                        JSONObject function = sanitizedCall.getJSONObject("function");
                        String cleanFnName = AiClient.sanitizeToolName(function.optString("name", ""));
                        if (!cleanFnName.isEmpty()) {
                            function.put("name", cleanFnName);
                        }
                        // CRITICAL: Ensure 'arguments' is a STRING, even if the model sent an object
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
                assistantMsg.put("content", thought != null ? thought : JSONObject.NULL);
                assistantMsg.put("tool_calls", sanitizedToolCalls);
                chatHistory.put(assistantMsg);

                // Check for duplicate tool call loop
                if (toolCalls.length() == 1) {
                    try {
                        JSONObject call = toolCalls.getJSONObject(0);
                        JSONObject fn = call.optJSONObject("function");
                        if (fn != null) {
                            String tName = fn.optString("name", "");
                            String tArgs = fn.optString("arguments", "");
                            if (tName.equals(lastToolName) && tArgs.equals(lastToolArgs)) {
                                duplicateToolCallCount++;
                                if (duplicateToolCallCount >= 2) {
                                    JSONObject toolResultMsg = new JSONObject();
                                    toolResultMsg.put("role", "tool");
                                    toolResultMsg.put("tool_call_id", call.optString("id", "call_loop_prevented"));
                                    toolResultMsg.put("name", tName);
                                    toolResultMsg.put("content", "Error: Duplicate tool call loop detected. You have already executed this exact action successfully. Stop calling tools and reply to the user now.");
                                    chatHistory.put(toolResultMsg);

                                    mainHandler.post(() -> {
                                        fragment.setStatus(null);
                                        executeLoop(chatHistory);
                                    });
                                    return;
                                }
                            } else {
                                lastToolName = tName;
                                lastToolArgs = tArgs;
                                duplicateToolCallCount = 0;
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // Check for excessive consecutive read/search operations (anti-searching loop)
                boolean allReadOrSearch = true;
                for (int i = 0; i < toolCalls.length(); i++) {
                    JSONObject call = toolCalls.optJSONObject(i);
                    JSONObject fn = call != null ? call.optJSONObject("function") : null;
                    String tName = fn != null ? fn.optString("name", "") : "";
                    if (!tName.matches("(list_.*|get_.*|read_.*|search_.*|web_.*)")) {
                        allReadOrSearch = false;
                        break;
                    }
                }

                if (allReadOrSearch) {
                    consecutiveReadSearchCount += toolCalls.length();
                    if (consecutiveReadSearchCount >= 10) {
                        try {
                            JSONObject toolResultMsg = new JSONObject();
                            toolResultMsg.put("role", "tool");
                            toolResultMsg.put("tool_call_id", toolCalls.getJSONObject(0).optString("id", "call_search_limit"));
                            toolResultMsg.put("name", toolCalls.getJSONObject(0).optJSONObject("function").optString("name", "search"));
                            toolResultMsg.put("content", "Error: You have performed too many search/read operations in a row. Stop searching history or files, synthesize what you have found, and provide your final response to the user now.");
                            chatHistory.put(toolResultMsg);

                            mainHandler.post(() -> {
                                fragment.setStatus(null);
                                executeLoop(chatHistory);
                            });
                            return;
                        } catch (Exception ignored) {}
                    }
                } else {
                    consecutiveReadSearchCount = 0;
                }

                // Use ToolExecutor for optimized parallel/sequential execution
                toolExecutor.executeBatch(toolCalls, new ToolExecutor.Callback() {
                    @Override
                    public void onToolStart(String toolName) {
                        mainHandler.post(() -> {
                            fragment.setStatus("Executing: " + toolName + "...");
                            HapticManager.vibrateToolCall();
                        });
                    }

                    @Override
                    public void onComplete(List<ToolExecutor.ToolResult> results) {
                        if (isCanceled) return;

                        try {
                            boolean hasWriteTools = false;
                            for (ToolExecutor.ToolResult result : results) {
                                // Add to history
                                JSONObject toolResultMsg = new JSONObject();
                                toolResultMsg.put("role", "tool");
                                toolResultMsg.put("tool_call_id", result.toolCallId);
                                toolResultMsg.put("name", AiClient.sanitizeToolName(result.toolName));
                                toolResultMsg.put("content", result.content != null ? result.content : "");

                                // Track metrics
                                executionMetrics.toolsExecuted++;
                                if (result.fromCache) {
                                    executionMetrics.cacheHits++;
                                }

                                chatHistory.put(toolResultMsg);

                                // Check if tool is a write tool (not read-only)
                                if (!result.toolName.matches("(list_.*|get_.*|read_.*|search_.*|web_.*)")) {
                                    hasWriteTools = true;
                                }

                                // Update UI with per-tool timing
                                mainHandler.post(() -> {
                                    String statusMsg = "Completed: " + result.toolName;
                                    fragment.setStatus(statusMsg);
                                });
                            }

                            // Refresh designer ONCE per batch if write tools were executed
                            if (hasWriteTools) {
                                mainHandler.post(() -> {
                                    fragment.refreshDesigner();
                                });
                            }

                            mainHandler.postDelayed(() -> {
                                if (isCanceled) return;
                                executeLoop(chatHistory);
                            }, 800);

                        } catch (Exception e) {
                            mainHandler.post(() -> {
                                fragment.setStatus(null);
                                fragment.addSystemMessage("Tool Result Error: " + e.getMessage());
                            });
                        }
                    }
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    fragment.setStatus(null);
                    fragment.addSystemMessage("Tool Dispatch Error: " + e.getMessage());
                });
            }
        });
    }

    public String dispatchTool(String name, JSONObject args) throws Exception {
        switch (name) {
            case "get_device_info":
                return DeviceInfoAide.getDeviceInfo(context, args).toString();

            case "web_search":
                return WebSearchAide.searchWeb(args.getString("query")).toString();
                
            case "read_url":
            case "web_browse":
                return WebSearchAide.browseWebPage(args.getString("url")).toString();

            case "list_files":
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

            case "find_files":
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

            case "read_layout_xml":
            case "get_layout_xml":
                if (fragment.getActivity() instanceof SkAssistantDialog.CodeEditorProvider) {
                    return ((SkAssistantDialog.CodeEditorProvider) fragment.getActivity()).getCodeText();
                }
                return SketchwareXmlBridge.getRawXml(context, fragment.scId, fragment.projectFile);

            case "update_layout_xml":
            case "apply_layout_xml":
                if (fragment.getActivity() instanceof SkAssistantDialog.CodeEditorProvider) {
                    SkAssistantDialog.CodeEditorProvider provider = (SkAssistantDialog.CodeEditorProvider) fragment.getActivity();
                    String xml = args.getString("xml");
                    provider.setCodeText(xml);
                    return "Success: Layout XML updated directly in editor for " + provider.getFileName();
                }
                fragment.undoSnapshot = new ProjectSnapshot(fragment.scId, fragment.projectFile != null ? fragment.projectFile.getXmlName() : "main.xml");
                fragment.setUndoVisible(true);
                String xml = args.getString("xml");
                String summary = args.optString("summary", "Modified layout");
                final boolean[] success = new boolean[1];
                runOnMainSync(() -> {
                    try {
                        success[0] = SketchwareXmlBridge.applyAiXmlToSketchware(context, fragment.scId, fragment.projectFile.getXmlName(), xml);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                if (success[0]) {
                    runOnMainSync(() -> fragment.refreshDesigner());
                }
                return success[0] ? "Success: " + summary : "Error: Failed to apply XML. Check syntax.";

            // case "get_manifest_xml":
            //     String mSection = args.optString("section", "full");
            //     return SketchwareManifestBridge.getManifestSection(context, fragment.scId, mSection);

            case "find_manifest_entries":
            case "search_manifest":
                return SketchwareManifestBridge.searchManifest(context, fragment.scId, args.getString("query"));

            case "add_java_imports":
            case "add_imports":
            case "inject_imports":
                fragment.undoSnapshot = new ProjectSnapshot(fragment.scId, fragment.projectFile != null ? fragment.projectFile.getXmlName() : "main.xml");
                fragment.setUndoVisible(true);
                JSONArray pkgs = args.getJSONArray("packages");
                String targetJavaName = args.optString("javaName", fragment.projectFile != null ? fragment.projectFile.getJavaName() : "main");
                String[] pArray = new String[pkgs.length()];
                for(int i=0; i<pkgs.length(); i++) pArray[i] = pkgs.getString(i);
                boolean injected = AssistantImportInjector.injectImports(fragment.scId, targetJavaName, pArray, fragment.getActivity());
                if (injected) runOnMainSync(() -> fragment.refreshDesigner());
                return injected ? "Imports injected successfully." : "Imports already exist or injection failed.";

            case "add_source_code":
            case "insert_source_code":
            case "add_source_directly":
            case "add_onCreate_code":
                fragment.undoSnapshot = new ProjectSnapshot(fragment.scId, fragment.projectFile != null ? fragment.projectFile.getXmlName() : "main.xml");
                fragment.setUndoVisible(true);
                String codeToAdd = args.getString("code");
                String targetJava = args.optString("javaName", fragment.projectFile != null ? fragment.projectFile.getJavaName() : "main");
                String mbName = args.optString("moreBlockName", args.optString("moreBlock", ""));
                String evKey = args.optString("eventKey", "");
                if (!mbName.isEmpty()) {
                    evKey = mbName.endsWith("_moreBlock") ? mbName : (mbName + "_moreBlock");
                }
                boolean added = AssistantImportInjector.injectSourceDirectly(fragment.scId, targetJava, evKey, codeToAdd, fragment.getActivity());
                if (added) runOnMainSync(() -> fragment.refreshDesigner());
                return added ? "Code successfully added via 'Add source directly' block." : "Failed to add code via 'Add source directly'.";

            case "list_methods":
            case "get_full_code":
            case "view_file_outline":
            case "get_class_outline":
            case "read_method":
            case "grep_code":
            case "search_in_code":
                String source;
                if (fragment.getActivity() instanceof SkAssistantDialog.CodeEditorProvider) {
                    source = ((SkAssistantDialog.CodeEditorProvider) fragment.getActivity()).getCodeText();
                } else {
                    String javaName = args.optString("javaName", fragment.projectFile.getJavaName());
                    source = new yq(context, fragment.scId).getFileSrc(
                        javaName, 
                        jC.b(fragment.scId),
                        jC.a(fragment.scId),
                        jC.c(fragment.scId)
                    );
                }
                if ("list_methods".equals(name)) {
                    return SourceCodeAide.listMethods(source).toString();
                } else if ("view_file_outline".equals(name) || "get_class_outline".equals(name) || "get_full_code".equals(name)) {
                    if (source == null) return "Source not found.";
                    String[] lines = source.split("\n");
                    if (lines.length > 100 || "view_file_outline".equals(name) || "get_class_outline".equals(name)) {
                        return SourceCodeAide.getClassOutline(source).toString();
                    }
                    return source;
                } else if ("grep_code".equals(name) || "search_in_code".equals(name)) {
                    return SourceCodeAide.findSnippet(source, args.getString("query"), args.optInt("contextLines", 2)).toString();
                } else {
                    return SourceCodeAide.findMethod(source, args.getString("methodName")).toString();
                }

            case "modify_java_file":
            case "patch_java_file":
            case "modify_manifest_xml":
            case "patch_manifest_xml":
            case "add_java_patch":
            case "add_xml_patch":
                String ref = args.optString("reference", "");
                String input = args.optString("inputCode", "");
                if (ref.isEmpty() || input.isEmpty()) {
                    return "Error: 'reference' and 'inputCode' parameters cannot be empty.";
                }

                if ("modify_manifest_xml".equals(name) || "patch_manifest_xml".equals(name) || "add_xml_patch".equals(name)) {
                    String targetXml = args.optString("xmlName", "");
                    if (!"AndroidManifest.xml".equalsIgnoreCase(targetXml)) {
                        return "Error: '" + name + "' is strictly prohibited for layout/view XML files like '" + targetXml + "'. To modify Activity views and layouts, you MUST use the 'update_layout_xml' tool.";
                    }
                    String manifestSrc = SourceCodeAide.getGeneratedManifest(context, fragment.scId);
                    if (manifestSrc != null && !manifestSrc.contains(ref)) {
                        return "Error: Reference anchor line '" + ref + "' was not found in AndroidManifest.xml. Please use find_manifest_entries to find the exact existing tag or line before patching.";
                    }
                } else {
                    String javaName = args.optString("javaName", fragment.projectFile != null ? fragment.projectFile.getJavaName() : "main");
                    String javaSrc;
                    if (fragment.getActivity() instanceof SkAssistantDialog.CodeEditorProvider) {
                        javaSrc = ((SkAssistantDialog.CodeEditorProvider) fragment.getActivity()).getCodeText();
                    } else {
                        javaSrc = new yq(context, fragment.scId).getFileSrc(
                            javaName, 
                            jC.b(fragment.scId),
                            jC.a(fragment.scId),
                            jC.c(fragment.scId)
                        );
                    }
                    if (javaSrc != null && !javaSrc.contains(ref)) {
                        return "Error: Reference anchor line '" + ref + "' was not found in " + javaName + ". Please use grep_code or read_method to find the exact existing line before patching.";
                    }
                }

                if (fragment.getActivity() instanceof SkAssistantDialog.CodeEditorProvider) {
                    SkAssistantDialog.CodeEditorProvider provider = (SkAssistantDialog.CodeEditorProvider) fragment.getActivity();
                    String inputCode = args.optString("inputCode", args.optString("command", ""));
                    if (inputCode.isEmpty()) {
                        inputCode = args.toString();
                    }
                    String currentCode = provider.getCodeText();
                    String finalCode;
                    if (!ref.isEmpty() && currentCode != null && currentCode.contains(ref)) {
                        finalCode = currentCode.replace(ref, inputCode);
                    } else {
                        finalCode = inputCode;
                    }
                    provider.setCodeText(finalCode);
                    return "Success: Code/XML patched directly in editor for " + provider.getFileName();
                }
                fragment.undoSnapshot = new ProjectSnapshot(fragment.scId, fragment.projectFile != null ? fragment.projectFile.getXmlName() : "main.xml");
                fragment.setUndoVisible(true);
                JSONObject patch = ("modify_java_file".equals(name) || "patch_java_file".equals(name) || "add_java_patch".equals(name)) ? SourceCodeAide.addJavaCommandToManager(
                    context, fragment.scId, args.getString("javaName"),
                    args.getString("reference"), args.optInt("distance", 0),
                    args.optInt("front", 0), args.optInt("back", 0),
                    args.getString("command"), args.getString("inputCode")
                ) : SourceCodeAide.addXmlCommandToManager(
                    context, fragment.scId, args.getString("xmlName"),
                    args.getString("reference"), args.optInt("distance", 0),
                    args.optInt("front", 0), args.optInt("back", 0),
                    args.getString("command"), args.getString("inputCode")
                );
                runOnMainSync(() -> fragment.refreshDesigner());
                return patch.toString();

            case "toggle_local_library":
            case "manage_local_library":
                fragment.undoSnapshot = new ProjectSnapshot(fragment.scId, fragment.projectFile != null ? fragment.projectFile.getXmlName() : "main.xml");
                fragment.setUndoVisible(true);
                String libName = args.getString("libraryName");
                boolean libEnabled = args.getBoolean("enabled");
                runOnMainSync(() -> {
                    if (libEnabled) fragment.librarySpecialist.applyLocalLibrary(libName);
                    else fragment.librarySpecialist.disableLocalLibrary(libName);
                    fragment.refreshDesigner();
                });
                return "Request to " + (libEnabled ? "enable" : "disable") + " local library '" + libName + "' completed.";

            case "add_manifest_permission":
            case "add_permission":
                fragment.undoSnapshot = new ProjectSnapshot(fragment.scId, fragment.projectFile != null ? fragment.projectFile.getXmlName() : "main.xml");
                fragment.setUndoVisible(true);
                String perm = args.getString("permission");
                runOnMainSync(() -> {
                    fragment.manifestSpecialist.applyPermission(perm);
                    fragment.refreshDesigner();
                });
                return "Permission " + perm + " added successfully.";

            case "add_sketchware_component":
            case "add_component": {
                int typeId = args.getInt("type");
                if (!ComponentSpecialist.isValidComponentType(typeId)) {
                    return "Error: Component type ID " + typeId + " does not exist. Please use list_components to check valid component types.";
                }
                fragment.undoSnapshot = new ProjectSnapshot(fragment.scId, fragment.projectFile != null ? fragment.projectFile.getXmlName() : "main.xml");
                fragment.setUndoVisible(true);
                String requestedId = args.getString("id");
                int count = args.optInt("count", 1);
                
                final List<String> addedIds = new ArrayList<>();
                runOnMainSync(() -> {
                    addedIds.addAll(fragment.componentSpecialist.applyBatchAddComponent(typeId, requestedId, count));
                    fragment.refreshDesigner();
                });
                
                String typeName = ComponentBean.getComponentTypeName(typeId);
                if (addedIds.size() == 1) {
                    return "Component '" + addedIds.get(0) + "' (" + typeName + ") added successfully.";
                } else {
                    return "Successfully added " + addedIds.size() + " " + typeName + " components in batch: " + addedIds.toString() + ".";
                }
            }

            case "list_components":
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
                runOnMainSync(() -> {
                    fragment.layoutSpecialist.applyCustomView(cvName, cvXml);
                    fragment.refreshDesigner();
                });
                return "Applied custom view: " + cvName;

            case "get_project_structure": {
                String javaName = args.optString("javaName", fragment.projectFile != null ? fragment.projectFile.getJavaName() : "MainActivity.java");
                return ProjectStructureAide.getProjectStructureSummary(fragment.scId, javaName);
            }

            case "add_variables": {
                fragment.undoSnapshot = new ProjectSnapshot(fragment.scId, fragment.projectFile != null ? fragment.projectFile.getXmlName() : "main.xml");
                fragment.setUndoVisible(true);
                String javaName = args.optString("javaName", fragment.projectFile != null ? fragment.projectFile.getJavaName() : "MainActivity.java");
                JSONArray items = args.optJSONArray("items");
                final String[] res = new String[1];
                runOnMainSync(() -> {
                    res[0] = ProjectStructureAide.addVariables(getContext(), fragment.scId, javaName, items);
                    fragment.refreshDesigner();
                });
                return res[0];
            }

            case "add_lists": {
                fragment.undoSnapshot = new ProjectSnapshot(fragment.scId, fragment.projectFile != null ? fragment.projectFile.getXmlName() : "main.xml");
                fragment.setUndoVisible(true);
                String javaName = args.optString("javaName", fragment.projectFile != null ? fragment.projectFile.getJavaName() : "MainActivity.java");
                JSONArray items = args.optJSONArray("items");
                final String[] res = new String[1];
                runOnMainSync(() -> {
                    res[0] = ProjectStructureAide.addLists(getContext(), fragment.scId, javaName, items);
                    fragment.refreshDesigner();
                });
                return res[0];
            }

            case "add_more_block": {
                String rawMbName = args.optString("name", null);
                if (rawMbName == null || rawMbName.trim().isEmpty()) {
                    JSONObject errJson = new JSONObject();
                    try {
                        errJson.put("ok", false);
                        errJson.put("error", "Missing required argument 'name' for add_more_block.");
                    } catch (Exception ignored) {}
                    return errJson.toString();
                }

                String defaultJava = (fragment.projectFile != null) ? fragment.projectFile.getJavaName() : "MainActivity.java";
                String rawJavaName = args.optString("javaName", defaultJava);
                String resolvedJavaName = ProjectStructureAide.findActivityJavaName(fragment.scId, rawJavaName, defaultJava);

                if (resolvedJavaName != null) {
                    fragment.undoSnapshot = new ProjectSnapshot(fragment.scId, resolvedJavaName);
                    fragment.setUndoVisible(true);
                }

                JSONArray params = args.optJSONArray("params");
                String returnType = args.optString("returnType", null);
                String bodyJava = args.optString("body_java", null);
                boolean allowEmptyOnBodyError = args.optBoolean("allow_empty_on_body_error", false);

                final String[] res = new String[1];
                final String finalJavaName = rawJavaName;
                runOnMainSync(() -> {
                    res[0] = ProjectStructureAide.addMoreBlock(getContext(), fragment.scId, finalJavaName, rawMbName, params, returnType, bodyJava, allowEmptyOnBodyError, fragment);
                    fragment.refreshDesigner();
                });
                return res[0];
            }

            case "register_event": {
                fragment.undoSnapshot = new ProjectSnapshot(fragment.scId, fragment.projectFile != null ? fragment.projectFile.getXmlName() : "main.xml");
                fragment.setUndoVisible(true);
                String javaName = args.optString("javaName", fragment.projectFile != null ? fragment.projectFile.getJavaName() : "MainActivity.java");
                String targetKind = args.optString("targetKind", "view");
                String targetId = args.getString("targetId");
                String eventName = args.getString("eventName");
                final String[] res = new String[1];
                runOnMainSync(() -> {
                    res[0] = ProjectStructureAide.registerEvent(getContext(), fragment.scId, javaName, targetKind, targetId, eventName);
                    fragment.refreshDesigner();
                });
                return res[0];
            }

            case "create_activity": {
                fragment.undoSnapshot = new ProjectSnapshot(fragment.scId, fragment.projectFile != null ? fragment.projectFile.getXmlName() : "main.xml");
                fragment.setUndoVisible(true);
                String actName = args.getString("name");
                JSONObject options = args.optJSONObject("options");
                int orientation = args.optInt("orientation", 0);
                int keyboardSetting = args.optInt("keyboard", 0);
                String layoutXml = args.optString("layout_xml", null);
                final String[] res = new String[1];
                runOnMainSync(() -> {
                    res[0] = ProjectStructureAide.createActivity(getContext(), fragment.scId, actName, options, orientation, keyboardSetting, layoutXml);
                    fragment.refreshDesigner();
                });
                return res[0];
            }

            case "search_chat_history":
            case "search_conversation":
            case "get_chat_history":
                return searchChatHistory(args);

            default:
                return "Error: Unknown tool '" + name + "'";
        }
    }

    private String searchChatHistory(JSONObject args) throws JSONException {
        if (fragment == null || fragment.messages == null) {
            JSONObject emptyRes = new JSONObject();
            emptyRes.put("total_messages", 0);
            emptyRes.put("matches_found", 0);
            emptyRes.put("results", new JSONArray());
            return emptyRes.toString();
        }

        String query = args.optString("query", "").trim().toLowerCase();
        String roleFilter = args.optString("role", "all").toLowerCase();
        int limit = Math.min(Math.max(1, args.optInt("limit", 15)), 50);
        int startIndex = args.optInt("startIndex", 0);

        List<SkAssistantFragment.Message> messageList;
        synchronized (fragment.messages) {
            messageList = new ArrayList<>(fragment.messages);
        }

        int totalMessages = messageList.size();
        if (startIndex < 0) {
            startIndex = Math.max(0, totalMessages + startIndex);
        } else if (startIndex >= totalMessages && totalMessages > 0) {
            startIndex = Math.max(0, totalMessages - limit);
        }

        JSONArray results = new JSONArray();

        for (int i = startIndex; i < totalMessages; i++) {
            if (results.length() >= limit) {
                break;
            }

            SkAssistantFragment.Message msg = messageList.get(i);
            if (msg == null || msg.role == null || msg.content == null) continue;

            String msgRole = msg.role.toLowerCase();

            // Filter status messages unless explicitly requested
            if ("status".equals(msgRole) && !"status".equals(roleFilter)) {
                continue;
            }

            // Role filtering
            if (!"all".equals(roleFilter) && !msgRole.equals(roleFilter)) {
                continue;
            }

            String content = msg.content;
            if (!query.isEmpty() && !content.toLowerCase().contains(query)) {
                continue;
            }

            JSONObject matchObj = new JSONObject();
            matchObj.put("index", i);
            matchObj.put("role", msg.role);

            // Truncate extremely long messages to prevent prompt token overflow
            if (content.length() > 2000) {
                matchObj.put("content", content.substring(0, 1000) + "\n... [content truncated, total length: " + content.length() + " chars] ...\n" + content.substring(content.length() - 500));
            } else {
                matchObj.put("content", content);
            }

            results.put(matchObj);
        }

        JSONObject resultObj = new JSONObject();
        resultObj.put("total_messages", totalMessages);
        resultObj.put("matches_found", results.length());
        resultObj.put("results", results);

        return resultObj.toString();
    }

    private void runOnMainSync(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            CountDownLatch latch = new CountDownLatch(1);
            mainHandler.post(() -> {
                try {
                    runnable.run();
                } finally {
                    latch.countDown();
                }
            });
            try {
                latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private String resolveUniqueComponentId(String scId, String javaName, String requestedId) {
        if (scId == null || javaName == null || requestedId == null) return requestedId;
        eC dataManager = jC.a(scId);
        if (dataManager == null) return requestedId;

        ArrayList<ComponentBean> existing = dataManager.e(javaName);
        if (existing == null || existing.isEmpty()) return requestedId;

        boolean exists = false;
        for (ComponentBean cb : existing) {
            if (cb != null && requestedId.equals(cb.componentId)) {
                exists = true;
                break;
            }
        }

        if (!exists) return requestedId;

        int counter = 2;
        String newId;
        while (true) {
            newId = requestedId + "_" + counter;
            boolean collision = false;
            for (ComponentBean cb : existing) {
                if (cb != null && newId.equals(cb.componentId)) {
                    collision = true;
                    break;
                }
            }
            if (!collision) {
                return newId;
            }
            counter++;
        }
    }

    // ========== EXECUTION METRICS INNER CLASS ==========
    
    /**
     * Tracks execution metrics for the current session.
     * Provides AI with awareness of performance and efficiency.
     */
    public static class ExecutionMetrics {
        public long sessionStartTime = System.currentTimeMillis();
        public int totalApiCalls = 0;
        public long totalTokensUsed = 0;
        public int toolsExecuted = 0;
        public int cacheHits = 0;
        
        public void recordApiCall(int promptTokens, int completionTokens, int totalTokens) {
            totalApiCalls++;
            totalTokensUsed += totalTokens;
        }
        
        public String summary() {
            long duration = System.currentTimeMillis() - sessionStartTime;
            double cacheHitRate = toolsExecuted > 0 ? (double) cacheHits / toolsExecuted * 100 : 0;
            return String.format(
                "Session Summary: %dms | API calls: %d | Tokens: %d | Tools: %d | Cache hits: %d (%.1f%%)",
                duration, totalApiCalls, totalTokensUsed, toolsExecuted, cacheHits, cacheHitRate
            );
        }
    }
}
