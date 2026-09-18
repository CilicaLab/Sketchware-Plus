package sketchware.plus.ai;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.design.DesignActivity;
import com.besome.sketch.editor.LogicEditorActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileWriter;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.Ix;
import a.a.a.Jx;
import a.a.a.Ox;
import a.a.a.eC;
import a.a.a.hC;
import a.a.a.iC;
import a.a.a.jC;
import a.a.a.jq;
import a.a.a.yq;
import a.a.a.wq;
import mod.hey.studios.util.Helper;
import sketchware.plus.R;
import com.besome.sketch.beans.ComponentBean;
import com.google.android.material.snackbar.Snackbar;
import mod.hilal.saif.components.ComponentsHandler;
import mod.hilal.saif.blocks.BlocksHandler;
import sketchware.plus.tools.ViewBeanParser;
import sketchware.plus.tools.ViewBeanFactory;
import sketchware.plus.util.library.BuiltInLibraryManager;
import sketchware.plus.utility.ActivityTracker;
import sketchware.plus.utility.FilePathUtil;
import sketchware.plus.utility.HapticManager;
import sketchware.plus.utility.SketchwareUtil;
import sketchware.plus.utility.AttributeConstants;
import sketchware.plus.utility.FileUtil;
import sketchware.plus.utility.GsonUtils;
import mod.agus.jcoderz.editor.manage.library.locallibrary.ManageLocalLibrary;
import sketchware.plus.lib.iconcreator.PatternBackgroundView;
import sketchware.plus.managers.inject.InjectRootLayoutManager;
import java.io.File;
import androidx.compose.ui.platform.ComposeView;
import androidx.compose.ui.platform.ViewCompositionStrategy;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import sketchware.plus.ai.specialists.ChatSpecialist;
import sketchware.plus.ai.specialists.CodeSpecialist;
import sketchware.plus.ai.specialists.ComponentSpecialist;
import sketchware.plus.ai.specialists.LayoutSpecialist;
import sketchware.plus.ai.specialists.LibrarySpecialist;
import sketchware.plus.ai.specialists.ManifestSpecialist;

public class SkAssistantFragment extends Fragment {

    public String scId;
    public ProjectFileBean projectFile;
    public RecyclerView recyclerView;
    public MessageAdapter adapter;
    public final List<Message> messages = new ArrayList<>();
    private String historyPath;
    private String logPath;
    public Button btnUndo;
    private MaterialButton btnSend;
    private EditText etInput;
    private View tokenUsageContainer;
    private TextView tvTokenUsage;
    private int lastPromptTokens = 0;
    private int lastCompletionTokens = 0;
    private int lastTotalTokens = 0;
    public ProjectSnapshot undoSnapshot;
    private int retryCount = 0;
    private static final int MAX_RETRIES = 2;
    private volatile boolean isRequestCanceled = false;

    public LayoutSpecialist layoutSpecialist;
    public ComponentSpecialist componentSpecialist;
    public CodeSpecialist codeSpecialist;
    public LibrarySpecialist librarySpecialist;
    public ManifestSpecialist manifestSpecialist;
    public ChatSpecialist chatSpecialist;
    private ToolOrchestrator toolOrchestrator;

    public static SkAssistantFragment newInstance(String scId, ProjectFileBean projectFile) {
        SkAssistantFragment fragment = new SkAssistantFragment();
        Bundle args = new Bundle();
        args.putString("scId", scId);
        args.putParcelable("projectFile", projectFile);
        fragment.setArguments(args);
        return fragment;
    }

    private boolean isSessionInitialized = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            scId = getArguments().getString("scId");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                projectFile = getArguments().getParcelable("projectFile", ProjectFileBean.class);
            } else {
                projectFile = getArguments().getParcelable("projectFile");
            }
        }

        initializeSession();
        initializeSpecialists();
    }

    private void initializeSpecialists() {
        layoutSpecialist = new LayoutSpecialist(this);
        componentSpecialist = new ComponentSpecialist(this);
        codeSpecialist = new CodeSpecialist(this);
        librarySpecialist = new LibrarySpecialist(this);
        manifestSpecialist = new ManifestSpecialist(this);
        chatSpecialist = new ChatSpecialist(this);
        toolOrchestrator = new ToolOrchestrator(this);
    }

    private void initializeSession() {
        if (isSessionInitialized) return;
        if (projectFile == null) return;
        if (scId == null || scId.isEmpty()) return;

        isSessionInitialized = true;
        String projectMyscPath = wq.d(scId);
        if (!projectMyscPath.endsWith(File.separator)) {
            projectMyscPath += File.separator;
        }

        String histF;
        if (projectFile.getXmlName().endsWith(".xml")) {
            histF = projectFile.getXmlName().substring(0, projectFile.getXmlName().length() - 4);
        } else {
            histF = projectFile.getXmlName();
        }
        historyPath = projectMyscPath + "sk_assistant_chat_" + histF + ".json";
        logPath = projectMyscPath + "data.log";
        AiViewTreeManager.setLogPath(logPath);
        new Thread(this::loadHistory).start();
        log("Assistant Session Started - Activity: " + projectFile.getXmlName());
    }

    public void setProjectFile(ProjectFileBean projectFile) {
        if (this.projectFile != null && !this.projectFile.getXmlName().equals(projectFile.getXmlName())) {
            // Activity changed, reset session to avoid context leakage from previous activity
            isSessionInitialized = false;
        }
        this.projectFile = projectFile;
        if (isAdded()) {
            initializeSession();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.sk_assistant_panel, container, false);

        PatternBackgroundView patternBg = view.findViewById(R.id.pattern_bg);
        if (patternBg != null) {
            patternBg.setPattern(patternBg.convertVectorToBitmap(getContext(), R.drawable.ic_dot_pattern, 30, 30));
            int dotColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface, 0xFF000000);
            patternBg.setColor(dotColor);
            patternBg.setOpacity(40); // Back to subtle since we have better contrast now
        }

        View rootLayout = view.findViewById(R.id.root_layout);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            boolean isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            int keyboardHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            
            // Adjust bottom padding to avoid keyboard. 
            // We use padding instead of margin to keep the background within the safe area if needed, 
            // but for this specific layout, padding on root works best.
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), keyboardHeight);
            
            if (isKeyboardVisible && !messages.isEmpty()) {
                recyclerView.postDelayed(() -> {
                    if (adapter.getItemCount() > 0) {
                        recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
                    }
                }, 100);
            }
            
            return insets;
        });

        recyclerView = view.findViewById(R.id.chat_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new MessageAdapter(messages, this);
        recyclerView.setAdapter(adapter);

        tokenUsageContainer = view.findViewById(R.id.token_usage_container);
        tvTokenUsage = view.findViewById(R.id.tv_token_usage);
        tvTokenUsage.setOnClickListener(v -> showTokenDetails());

        btnUndo = view.findViewById(R.id.btn_undo);
        btnUndo.setOnClickListener(v -> {
            HapticManager.vibrateRun(v);
            undoApply();
        });

        view.findViewById(R.id.btn_clear).setOnClickListener(v -> {
            HapticManager.vibrateStop(v);
            clearChat();
        });

        etInput = view.findViewById(R.id.et_input);
        btnSend = view.findViewById(R.id.btn_send);

        btnSend.setOnClickListener(v -> {
            HapticManager.vibrateRun(v);
            if (isReasoningActive()) {
                cancelCurrentRequest();
            } else {
                String prompt = etInput.getText().toString().trim();
                if (!prompt.isEmpty()) {
                    sendMessage(prompt);
                    etInput.setText("");
                    hideKeyboard();
                }
            }
        });


        return view;
    }

    private void hideKeyboard() {
        View view = getActivity() != null ? getActivity().getCurrentFocus() : null;
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    private void sendMessage(String prompt) {
        log("User Prompt: " + prompt);
        isRequestCanceled = false;
        Context context = getContext();
        if (context == null) return;
        
        SharedPreferences aiPref = context.getSharedPreferences("P12", Context.MODE_PRIVATE);
        String model = aiPref.getString("P12I5", "");

        // Add user message to local history
        messages.add(new Message("user", prompt));
        adapter.notifyItemInserted(messages.size() - 1);
        recyclerView.scrollToPosition(messages.size() - 1);

        setStatus(model +" is reasoning...");
        jC.projectOperationsExecutor.execute(() -> {
            try { Thread.sleep(600); } catch (Exception ignored) {}
            if (getActivity() != null && !isRequestCanceled) {
                getActivity().runOnUiThread(() -> routeRequest(prompt));
            }
        });
    }

    /**
     * Lightweight intent router. Decides whether this message is a layout request,
     * component request, code help, etc., and routes to the dedicated specialist.
     */
    private void routeRequest(String prompt) {
        toolOrchestrator.start(prompt);
    }

    public void setStatus(String status) {
        final boolean isThinking = status != null && !status.isEmpty();
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                updateSendButtonState(isThinking);
                // Remove existing status message if any
                for (int i = messages.size() - 1; i >= 0; i--) {
                    if ("status".equals(messages.get(i).role)) {
                        messages.remove(i);
                        adapter.notifyItemRemoved(i);
                    }
                }

                if (status != null && !status.isEmpty()) {
                    Message statusMsg = new Message("status", status);
                    messages.add(statusMsg);
                    adapter.notifyItemInserted(messages.size() - 1);
                    recyclerView.scrollToPosition(messages.size() - 1);
                }
                
                // Toggle File/Activity selector clickable state based on AI status
                if (getActivity() instanceof DesignActivity da) {
                    View fileNameContainer = da.findViewById(R.id.file_name_container);
                    if (fileNameContainer != null) {
                        fileNameContainer.setEnabled(!isThinking);
                        fileNameContainer.setAlpha(isThinking ? 0.5f : 1.0f);
                    }
                }
            });
        }
    }

    private void updateSendButtonState(boolean isThinking) {
        if (btnSend == null) return;
        Context context = getContext();
        if (context == null) return;

        if (isThinking) {
            btnSend.setIconResource(R.drawable.ic_mtrl_stop);
            btnSend.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.scolor_red_01)));
            btnSend.setIconTintResource(android.R.color.white);
        } else {
            btnSend.setIconResource(R.drawable.paper_plane_48);
            btnSend.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.color_primary)));
            btnSend.setIconTintResource(android.R.color.white);
        }
    }

    private boolean isReasoningActive() {
        for (Message msg : messages) {
            if ("status".equals(msg.role)) return true;
        }
        return false;
    }

    public void cancelCurrentRequest() {
        isRequestCanceled = true;
        AiClient.cancelCurrentRequest();
        setStatus(null);
        

        
        messages.add(new Message("system", "AI response has been stopped."));
        adapter.notifyItemInserted(messages.size() - 1);
        recyclerView.scrollToPosition(messages.size() - 1);
    }

    public void cancelSKRequests() {
        Context context = getContext();
        if (context == null) return;
        btnSend.setIconResource(R.drawable.paper_plane_48);
        btnSend.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.color_primary)));
        btnSend.setIconTintResource(android.R.color.white);

    }


    public void updateTokenUsage(int prompt, int completion, int total) {
        this.lastPromptTokens = prompt;
        this.lastCompletionTokens = completion;
        this.lastTotalTokens = total;

        Activity activity = getActivity();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            Context context = getContext();
            if (context == null) return;
            
            SharedPreferences aiPref = context.getSharedPreferences("P12", Context.MODE_PRIVATE);
            int limit = aiPref.getInt("P12I10", 32768);

            if (tokenUsageContainer != null) {
                tokenUsageContainer.setVisibility(View.VISIBLE);
            }
            if (tvTokenUsage != null) {
                tvTokenUsage.setText(formatTokenLabel(total, limit));
            }
        });
    }

    private String formatTokenLabel(int used, int limit) {
        return formatValue(used) + " / " + formatValue(limit);
    }

    private String formatValue(int value) {
        if (value >= 1000000) return (value / 1000000) + "M";
        if (value >= 1000) return (value / 1000) + "k";
        return String.valueOf(value);
    }

    private void showTokenDetails() {
        if (getContext() == null) return;
        SharedPreferences aiPref = getContext().getSharedPreferences("P12", Context.MODE_PRIVATE);
        int limit = aiPref.getInt("P12I10", 32768);

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.token_usage_dialog, null);
        TextView tvSummary = dialogView.findViewById(R.id.tv_token_summary);
        ProgressBar pbUsage = dialogView.findViewById(R.id.pb_token_usage);
        TextView tvDetails = dialogView.findViewById(R.id.tv_token_details);

        int percent = (int) ((lastTotalTokens / (float) limit) * 100);
        tvSummary.setText(String.format(Locale.US, "Context Usage: %d%%", percent));
        pbUsage.setProgress(Math.min(percent, 100));

        String details = String.format(Locale.US,
                "Total Tokens: %d\n" +
                        "Prompt (In): %d\n" +
                        "Completion (Out): %d\n" +
                        "Context Limit: %d",
                lastTotalTokens, lastPromptTokens, lastCompletionTokens, limit);
        tvDetails.setText(details);

        new MaterialAlertDialogBuilder(getContext())
                .setTitle("Token Usage Details")
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .show();
    }

    public JSONArray getChatHistory() {
        JSONArray chatHistory = new JSONArray();
        int startIndex = Math.max(0, messages.size() - 4);
        try {
            for (int i = startIndex; i < messages.size(); i++) {
                Message msg = messages.get(i);
                if ("status".equals(msg.role)) continue;
                JSONObject msgObj = new JSONObject();
                msgObj.put("role", msg.role.equals("system") ? "assistant" : msg.role);
                msgObj.put("content", "user".equals(msg.role) ? msg.content : msg.jsonData != null ? msg.jsonData.optString("summary", msg.content) : msg.content);
                chatHistory.put(msgObj);
            }
        } catch (Exception e) {
            log("History construction failed: " + e.getMessage());
        }
        return chatHistory;
    }


    public void executeRequest(String systemPrompt, String prompt, String context, String category) {
        Context c = getContext();
        if (c == null || isRequestCanceled) return;
        
        AiClient.askAi(c, systemPrompt, "USER REQUEST: " + prompt + "\n\nCONTEXT:\n" + context, AiClient.AiTemperatureType.ASSISTANT_MODE, new AiClient.AiCallback() {
            @Override
            public void onSuccess(String response) {
                if (isRequestCanceled) return;
                log("AI Response (" + category + "): " + response);
                setStatus(null);
                handleAiResponse(response, category);
            }

            @Override
            public void onSuccess(String response, int promptTokens, int completionTokens, int totalTokens) {
                if (isRequestCanceled) return;
                log("AI Response (" + category + "): " + response);
                setStatus(null);
                updateTokenUsage(promptTokens, completionTokens, totalTokens);
                handleAiResponse(response, category);
            }

            @Override
            public void onError(String error) {
                log("AI Error (" + category + "): " + error);
                setStatus(null);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!"Canceled".equals(error)) {
                            SketchwareUtil.toastError("SK Error: " + error);
                            messages.add(new Message("system", "Error: " + error));
                            adapter.notifyItemInserted(messages.size() - 1);
                            recyclerView.scrollToPosition(messages.size() - 1);
                        }
                    });
                }
            }

            @Override
            public void onRetry(int retryCount, long delayMillis) {
                setStatus("Rate limit hit. Retrying in " + String.format(Locale.US, "%.1f", delayMillis / 1000.0) + "s... (Attempt " + retryCount + ")");
            }
        });
    }

    public void addSystemMessage(String content) {
        messages.add(new Message("system", content));
        adapter.notifyItemInserted(messages.size() - 1);
        recyclerView.scrollToPosition(messages.size() - 1);
    }

    public void addAssistantMessage(String content) {
        if (content == null || content.trim().isEmpty()) return;
        messages.add(new Message("assistant", content));
        adapter.notifyItemInserted(messages.size() - 1);
        recyclerView.scrollToPosition(messages.size() - 1);
        saveHistory();
    }

    public void setUndoVisible(boolean visible) {
        if (btnUndo != null) {
            btnUndo.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public void handleXmlError(String editedXml, String errorMessage, String targetXmlName) {
        if (retryCount < MAX_RETRIES) {
            retryCount++;
            messages.add(new Message("system", "XML Parse Error. Auto-retrying fix (" + retryCount + "/" + MAX_RETRIES + ")..."));
            adapter.notifyItemInserted(messages.size() - 1);
            autoFixXml(editedXml, errorMessage, targetXmlName);
        } else {
            retryCount = 0;
            new MaterialAlertDialogBuilder(getContext())
                    .setTitle("Parse Error")
                    .setMessage(errorMessage)
                    .setPositiveButton("Fix", (d, w) -> layoutSpecialist.applyXml(editedXml, targetXmlName))
                    .setNegativeButton("Close", null)
                    .show();
        }
    }

    public void handleAiResponse(String response, String categoryFallback) {
        if (getActivity() == null) return;
        ActivityTracker.recordActivity(ActivityTracker.DEFAULT_FILE_PATH);
        getActivity().runOnUiThread(() -> {
            try {
                // Bulletproof JSON extraction via Regex (Finds outermost { })
                String cleanJson = response;
                Matcher matcher = Pattern.compile("\\{.*\\}", Pattern.DOTALL).matcher(response);
                if (matcher.find()) {
                    cleanJson = matcher.group(0);
                }

                JSONObject json = new JSONObject(cleanJson);

                // Dynamic category if provided by new prompt
                String actualCategory = json.optString("category", categoryFallback);
                String summary = json.optString("summary", "Done.");

                Message msg = new Message("assistant", summary);
                msg.jsonData = json;
                msg.category = actualCategory;
                messages.add(msg);

                adapter.notifyItemInserted(messages.size() - 1);
                recyclerView.scrollToPosition(messages.size() - 1);
                saveHistory();
                cancelSKRequests();


                // Auto-dispatch for autonomous specialists (like CodeSpecialist)
                if ("LOGIC_ENGINEER".equals(actualCategory) && json.has("actions")) {
                    JSONArray actions = json.optJSONArray("actions");
                    if (actions != null && actions.length() > 0) {
                        boolean hasOnlyAutonomousActions = true;
                        for (int i = 0; i < actions.length(); i++) {
                            JSONObject action = actions.optJSONObject(i);
                            if (action != null) {
                                String type = normalizeActionType(action.optString("type"));
                                if ("ADD_JAVA_COMMAND".equals(type) || "ADD_BLOCK".equals(type) || "ADD_IMPORT".equals(type)) {
                                    hasOnlyAutonomousActions = false;
                                    break;
                                }
                            }
                        }
                        
                        if (hasOnlyAutonomousActions) {
                            dispatchJsonActions(json, actualCategory);
                            msg.wasApplied = true;
                        }
                    }
                }

                retryCount = 0; // reset retry counter on success
            } catch (Exception e) {
                SketchwareUtil.toastError("SK Parse Error: " + e.getMessage());
                if (retryCount < MAX_RETRIES) {
                    retryCount++;
                    setStatus("Fixing JSON schema...");
                    retryFixJson(categoryFallback, response);
                } else {
                    retryCount = 0;
                    setStatus(null);
                    // Fallback to plain text chat if JSON is completely broken
                    // We set ignoreTags to true because if JSON parsing failed, 
                    // any embedded tags are likely unreliable "jibberish".
                    messages.add(new Message("assistant", response, null, true));
                    adapter.notifyItemInserted(messages.size() - 1);
                    recyclerView.scrollToPosition(messages.size() - 1);
                }
            }
        });
    }

    private void retryFixJson(String category, String malformedResponse) {
        String systemPrompt = "You are a JSON repair assistant. The user provided a malformed response. " +
                "Return ONLY the fixed, valid JSON object for the category: " + category + ".\n" +
                "Original Response:\n" + malformedResponse;

        Context c = getContext();
        if (c == null) return;

        AiClient.askAi(c, systemPrompt, "Please fix the JSON and return only the valid object.", 0.1f, new AiClient.AiCallback() {
            @Override
            public void onSuccess(String response) {
                if (isRequestCanceled) return;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> handleAiResponse(response, category));
                }
            }

            @Override
            public void onError(String error) {
                log("Retry Fix ERROR: " + error);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!"Canceled".equals(error)) {
                            handleAiResponse(malformedResponse, category); // Fallback to raw
                        }
                    });
                }
            }

            @Override
            public void onRetry(int retryCount, long delayMillis) {
                setStatus("Repairing response... rate limit hit, retrying in " + String.format(Locale.US, "%.1f", delayMillis / 1000.0) + "s...");
            }
        });
    }

    public String gatherScopedContext(Context context, String category, String prompt) {
        try {
            eC dataManager = jC.a(scId);
            hC fileManager = jC.b(scId);
            iC libraryManager = jC.c(scId);

            StringBuilder sb = new StringBuilder();
            sb.append("Current Activity: ").append(projectFile.getXmlName()).append("\n");

            synchronized (dataManager) {
                synchronized (fileManager) {
                    synchronized (libraryManager) {
                        yq workspace = new yq(context, scId);
                        workspace.a(libraryManager, fileManager, dataManager);

                        if ("UI_DESIGNER".equals(category)) {
                            sb.append("Current Layout XML:\n");
                            sb.append(SketchwareXmlBridge.getRawXml(context, scId, projectFile)).append("\n\n");

                            InjectRootLayoutManager rootManager = new InjectRootLayoutManager(scId);
                            ViewBean rootBean = rootManager.toBean(projectFile.getXmlName());
                            ArrayList<ViewBean> views = dataManager.d(projectFile.getXmlName());

                            sb.append("View Hierarchy (JSON):\n");
                            sb.append(AiViewTreeManager.buildViewTree(views, rootBean).toString(2)).append("\n");
                        } else if ("LOGIC_ENGINEER".equals(category) || "IMPORT_LOGIC".equals(category)) {
                            String javaCode = new Jx(workspace.N, projectFile, dataManager).generateCode(false, scId);
                            sb.append("Current Java:\n").append(javaCode).append("\n");

                            // Current Logic Events
                            ArrayList<EventBean> events = dataManager.i.get(projectFile.getJavaName());
                            if (events != null) {
                                sb.append("\nCURRENT LOGIC EVENTS (Use as 'eventKey' in ADD_BLOCK):\n");
                                for (EventBean e : events) {
                                    sb.append("- ").append(e.getEventKey()).append(" (").append(EventBean.getEventTypeName(e.eventType)).append(")\n");
                                }
                            }

                            // Block Catalog Snippet
                            ArrayList<HashMap<String, Object>> blockList = new ArrayList<>();
                            BlocksHandler.builtInBlocks(blockList);
                            sb.append("\nCOMMON BLOCK OPCODES:\n");
                            int count = 0;
                            for (HashMap<String, Object> block : blockList) {
                                String opCode = (String) block.get("name");
                                String spec = (String) block.get("spec");
                                if (opCode != null && spec != null) {
                                    if (opCode.contains("Toast") || opCode.contains("setVar") || opCode.equals("addSourceDirectly") || opCode.contains("intent")) {
                                        sb.append("- ").append(opCode).append(": ").append(spec).append("\n");
                                        if (count++ > 15) break;
                                    }
                                }
                            }
                        } else if ("LIBRARY_MANAGER".equals(category)) {
                            sb.append("Enabled: firebase=").append(libraryManager.d().useYn)
                                    .append(", appcompat=").append(libraryManager.c().useYn).append("\n");
                        } else if ("SYSTEM_MANIFEST".equals(category)) {
                            sb.append("Current Conceptual Manifest (AST Model):\n");
                            try {
                                BuiltInLibraryManager builtInLibManager = new BuiltInLibraryManager(scId);
                                Ix manifestGenerator = new Ix(dataManager.l, fileManager.b(), builtInLibManager);
                                manifestGenerator.setYq(workspace);
                                sb.append(manifestGenerator.a()).append("\n");
                            } catch (Exception e) {
                                sb.append("Error generating manifest preview: ").append(e.getMessage()).append("\n");
                            }
                        } else if ("CUSTOM_DESIGN".equals(category) || "COMPONENT_ARCHITECT".equals(category)) {
                            sb.append("Custom Views: ").append(fileManager.d.size()).append("\n");
                            ArrayList<ComponentBean> components = dataManager.e(projectFile.getJavaName());
                            sb.append("Current Components:\n");
                            for (ComponentBean c : components) {
                                sb.append("- id:").append(c.componentId).append(", type:").append(c.type).append("\n");
                            }
                            if ("COMPONENT_ARCHITECT".equals(category)) {
                                sb.append("\nAVAILABLE COMPONENT TYPES AND THEIR REQUIREMENTS:\n")
                                        .append("- INTENT: No extra params.\n")
                                        .append("- SHAREDPREF: Use the component ID as the file name.\n")
                                        .append("- CALENDAR: No extra params.\n")
                                        .append("- VIBRATOR: No extra params.\n")
                                        .append("- TIMERTASK: No extra params.\n")
                                        .append("- FIREBASE: Use the component ID as the data path.\n")
                                        .append("- DIALOG: No extra params.\n")
                                        .append("- MEDIAPLAYER: No extra params.\n")
                                        .append("- SOUNDPOOL: No extra params.\n")
                                        .append("- OBJECTANIMATOR: No extra params.\n")
                                        .append("- GYROSCOPE: No extra params.\n")
                                        .append("- FIREBASE_AUTH: No extra params.\n")
                                        .append("- INTERSTITIAL_AD: No extra params.\n")
                                        .append("- FIREBASE_STORAGE: Use the component ID as the storage bucket.\n")
                                        .append("- CAMERA: No extra params.\n")
                                        .append("- FILE_PICKER: Use 'image/*' as default mime type if not specified.\n")
                                        .append("- REQUEST_NETWORK: No extra params.\n")
                                        .append("- TEXT_TO_SPEECH: No extra params.\n")
                                        .append("- SPEECH_TO_TEXT: No extra params.\n")
                                        .append("- BLUETOOTH_CONNECT: No extra params.\n")
                                        .append("- LOCATION_MANAGER: No extra params.\n");

                                ArrayList<HashMap<String, Object>> customComps = ComponentsHandler.getCustomComponents();
                                if (!customComps.isEmpty()) {
                                    sb.append("\nCUSTOM COMPONENT TYPES:\n");
                                    for (HashMap<String, Object> comp : customComps) {
                                        sb.append("- ").append(comp.get("typeName")).append(": ").append(comp.get("description")).append("\n");
                                    }
                                }
                            }
                        } else {
                            return gatherContext(context, prompt); // Fallback to original broad context
                        }
                    }
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error gathering context: " + e.getMessage();
        }
    }

    private String gatherContext(Context context, String userPrompt) {
        LogicEditorActivity logicActivity = null;
        Activity activity = getActivity();
        if (activity instanceof LogicEditorActivity) {
            logicActivity = (LogicEditorActivity) activity;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Activity: ").append(projectFile.getActivityName()).append("\n");

        try {
            eC dataManager = jC.a(scId);
            hC fileManager = jC.b(scId);
            iC libraryManager = jC.c(scId);
            
            synchronized (dataManager) {
                synchronized (fileManager) {
                    synchronized (libraryManager) {
                        yq workspace = new yq(context, scId);
                        workspace.a(libraryManager, fileManager, dataManager);

                        sb.append("Project ID: ").append(scId).append("\n");
                        sb.append("Current View: ").append(projectFile.getXmlName()).append("\n");

                        // Generated XML
                        sb.append("Current Layout XML:\n")
                                .append(SketchwareXmlBridge.getRawXml(context, scId, projectFile))
                                .append("\n\n");

                        // Generated Java (only if relevant or requested)
                        boolean needsJava = userPrompt.toLowerCase().contains("java")
                                || userPrompt.toLowerCase().contains("code")
                                || userPrompt.toLowerCase().contains("logic")
                                || userPrompt.toLowerCase().contains("import");

                        if (needsJava || userPrompt.length() < 10) {
                            String javaCode = new Jx(workspace.N, projectFile, dataManager).generateCode(false, scId);
                            if (javaCode.length() > 5000 && !needsJava) {
                                sb.append("Current Java Code: [Omitted for length, ask specifically to see it]\n");
                            } else {
                                sb.append("Current Java Code:\n").append(javaCode).append("\n");
                            }
                        }

                        // Custom Views
                        if (!fileManager.d.isEmpty()) {
                            sb.append("\nCustom Views:\n");
                            for (ProjectFileBean cv : fileManager.d) {
                                Ox cvOx = new Ox(workspace.N, cv);
                                cvOx.a(dataManager.d(cv.getXmlName()), dataManager.h(cv.getXmlName()));
                                sb.append("- ").append(cv.getXmlName()).append(":\n").append(cvOx.b()).append("\n");
                            }
                        }

                        // Local Libraries
                        ManageLocalLibrary localLib = new ManageLocalLibrary(scId);
                        if (!localLib.list.isEmpty()) {
                            sb.append("\nEnabled Local Libraries:\n");
                            for (HashMap<String, Object> lib : localLib.list) {
                                sb.append("- ").append(lib.get("name")).append(" (").append(lib.get("packageName")).append(")\n");
                            }
                        }

                        // Manifest Injections
                        String injectionPath = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + scId + "/Injection/androidmanifest/attributes.json";
                        if (FileUtil.isExistFile(injectionPath)) {
                            sb.append("\nManifest Attributes:\n").append(FileUtil.readFile(injectionPath)).append("\n");
                        }
                        String componentPath = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + scId + "/Injection/androidmanifest/app_components.txt";
                        if (FileUtil.isExistFile(componentPath)) {
                            sb.append("\nManifest Components:\n").append(FileUtil.readFile(componentPath)).append("\n");
                        }

                        // Current Imports
                        ArrayList<BlockBean> importBlocks = dataManager.a(projectFile.getJavaName(), "Import");
                        if (!importBlocks.isEmpty()) {
                            sb.append("\nCurrent Imports:\n");
                            for (BlockBean b : importBlocks) {
                                if ("createImport".equals(b.opCode) && !b.parameters.isEmpty()) {
                                    sb.append("import ").append(b.parameters.get(0)).append(";\n");
                                }
                            }
                        }

                        if (logicActivity != null && logicActivity.o != null) {
                            sb.append("\nBlocks: ").append(logicActivity.o.getBlocks().size()).append(" blocks on pane.\n");
                        }
                    }
                }
            }

        } catch (Exception e) {
            sb.append("Error gathering context: ").append(e.getMessage());
        }

        return sb.toString();
    }


    private void autoFixXml(String failedXml, String errorMessage, String targetXmlName) {
        String systemPrompt = "You are a coding assistant specializing in Sketchware Plus. The user provided XML that failed to parse. FIX the XML so it parses correctly. " + getXmlRules();
        String userPrompt = "Failed XML:\n" + failedXml + "\n\nError Message: " + errorMessage + "\n\nPlease provide the corrected XML.";

        Context context = getContext();
        if (context == null) return;
        
        AiClient.askAi(context, systemPrompt, userPrompt, AiClient.AiTemperatureType.CODE_GENERATION, new AiClient.AiCallback() {
            @Override
            public void onSuccess(String response) {
                if (isRequestCanceled) return;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        messages.add(new Message("assistant", response, targetXmlName));
                        adapter.notifyItemInserted(messages.size() - 1);
                        recyclerView.scrollToPosition(messages.size() - 1);
                        saveHistory();
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!"Canceled".equals(error)) {
                            log("Auto-fix ERROR: " + error);
                            SketchwareUtil.toastError("Auto-fix Error: " + error);
                            messages.add(new Message("system", "Auto-fix failed: " + error));
                            adapter.notifyItemInserted(messages.size() - 1);
                        }
                    });
                }
            }

            @Override
            public void onRetry(int retryCount, long delayMillis) {
                setStatus("Auto-fixing layout... rate limit hit, retrying in " + String.format(Locale.US, "%.1f", delayMillis / 1000.0) + "s...");
            }
        });
    }

    private String getXmlRules() {
        return "Rules for Sketchware XML:\n" +
                "- Match the structure of Sketchware's generated XML exactly. Match the property style exactly.\n" +
                "- Do NOT use standard Android Studio XML conventions if they differ from the example.\n" +
                "- Use ONLY these attributes: " + AttributeConstants.BUILT_IN_ATTRIBUTES + "\n" +
                "- Use RelativeLayout attributes for RelativeLayout children: " + AttributeConstants.RELATIVE_ATTRIBUTES + "\n" +
                "- Do NOT use standard Android Studio namespaces like app: unless necessary for specialized components.\n" +
                "- Provide the FULL XML layout code when suggesting changes.";
    }


    private void undoApply() {
        if (undoSnapshot != null) {
            String xmlName = projectFile.getXmlName();
            undoSnapshot.restore(scId, xmlName, getContext());

            undoSnapshot = null;
            btnUndo.setVisibility(View.GONE);

            messages.add(new Message("system", "Changes reverted."));
            adapter.notifyItemInserted(messages.size() - 1);
            recyclerView.scrollToPosition(messages.size() - 1);

            refreshDesigner();
        }
    }

    public void refreshDesigner() {
        if (getActivity() instanceof DesignActivity) {
            ((DesignActivity) getActivity()).refresh();
        }
    }

    private void clearChat() {
        new MaterialAlertDialogBuilder(getContext())
                .setTitle("Clear Chat")
                .setMessage("Are you sure you want to clear the chat history?")
                .setPositiveButton("Clear", (dialog, which) -> {
                    messages.clear();
                    adapter.notifyDataSetChanged();
                    saveHistory();

                    undoSnapshot = null;
                    if (btnUndo != null) btnUndo.setVisibility(View.GONE);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public void log(String message) {
        if (logPath == null) return;
        try {
            File file = new File(logPath);
            if (!file.exists()) file.createNewFile();
            FileWriter writer = new FileWriter(file, true);
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            writer.write("[" + timestamp + "] " + message + "\n");
            writer.close();
        } catch (Exception ignored) {}
    }

    public String normalizeActionType(String type) {
        if (type == null) return "";
        String value = type.trim();
        if (value.isEmpty()) return "";
        value = value.replace('-', '_').replace(' ', '_');
        return value.toUpperCase(Locale.US);
    }

    public void dispatchJsonActions(JSONObject data, String category) {
        JSONArray actions = data.optJSONArray("actions");
        if (actions == null) return;

        log("Dispatching AI Actions: " + actions.toString());
        try {
            // Snapshot before applying any changes
            undoSnapshot = new ProjectSnapshot(scId, projectFile.getXmlName());

            for (int i = 0; i < actions.length(); i++) {
                JSONObject action = actions.getJSONObject(i);
                if (action == null) continue;

                switch (category) {
                    case "UI_DESIGNER":
                        layoutSpecialist.handleAction(action);
                        break;
                    case "COMPONENT_ARCHITECT":
                        componentSpecialist.handleAction(action);
                        break;
                    case "LOGIC_ENGINEER":
                        codeSpecialist.handleAction(action);
                        break;
                    case "LIBRARY_MANAGER":
                        librarySpecialist.handleAction(action);
                        break;
                    case "SYSTEM_MANIFEST":
                        manifestSpecialist.handleAction(action);
                        break;
                    default:
                        chatSpecialist.handleAction(action);
                        break;
                }
            }
        } catch (Exception e) {
            SketchwareUtil.toastError("Action dispatch failed: " + e.getMessage());
            if (getView() != null) {
                Snackbar.make(getView(), "Action dispatch failed: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
            }
        }
    }

    public void handleModifyAction(String value) {
        // value: id=btn, field=text, value=Hello
        String[] parts = value.split(",");
        String id = "", field = "", val = "";
        for (String p : parts) {
            String[] pair = p.split("=");
            if (pair.length == 2) {
                String k = pair[0].trim();
                String v = pair[1].trim();
                switch (k) {
                    case "id" -> id = v;
                    case "field" -> field = v;
                    case "value" -> val = v;
                }
            }
        }
        layoutSpecialist.applyModifyView(id, field, val, true);
    }

    public void handleMoveAction(String value) {
        // value: id=btn, newParent=root, newIndex=0
        String[] parts = value.split(",");
        String id = "", newParent = "root";
        int newIndex = -1;
        for (String p : parts) {
            String[] pair = p.split("=");
            if (pair.length == 2) {
                String k = pair[0].trim();
                String v = pair[1].trim();
                switch (k) {
                    case "id" -> id = v;
                    case "newParent" -> newParent = v;
                    case "newIndex" -> {
                        try { newIndex = Integer.parseInt(v); } catch (Exception ignored) {}
                    }
                }
            }
        }
        layoutSpecialist.applyMoveView(id, newParent, newIndex, true);
    }

    public void handleAddAction(String value) {
        // type=Button, parent=root, index=0, id=btn, attributes={text=Hello}
        String[] parts = value.split(",");
        String type = "", parent = "root", id = "";
        int index = -1;
        Map<String, String> attrs = new HashMap<>();

        for (String p : parts) {
            String[] pair = p.split("=");
            if (pair.length == 2) {
                String k = pair[0].trim();
                String v = pair[1].trim();
                if ("type".equals(k)) type = v;
                else if ("parent".equals(k)) parent = v;
                else if ("id".equals(k)) id = v;
                else if ("index".equals(k)) {
                    try { index = Integer.parseInt(v); } catch (Exception ignored) {}
                }
                else if ("attributes".equals(k)) {
                    // attributes={a=b;c=d}
                    String attrStr = v.substring(1, v.length() - 1);
                    String[] pairs = attrStr.split(";");
                    for (String ap : pairs) {
                        String[] apair = ap.split(":");
                        if (apair.length == 2) attrs.put(apair[0].trim(), apair[1].trim());
                    }
                }
            }
        }
        layoutSpecialist.applyAddView(type, parent, index, id, attrs, true);
    }

    public void showBlockSelector(List<String> blocks, String targetXmlName) {
        String[] items = new String[blocks.size()];
        for (int i = 0; i < blocks.size(); i++) {
            String firstLine = blocks.get(i).split("\n")[0];
            items[i] = "Block " + (i + 1) + ": " + (firstLine.length() > 30 ? firstLine.substring(0, 30) : firstLine);
        }

        new MaterialAlertDialogBuilder(getContext())
                .setTitle("Select XML Block")
                .setItems(items, (d, which) -> layoutSpecialist.applyXml(blocks.get(which), targetXmlName))
                .show();
    }


    private void loadHistory() {
        final List<Message> loadedMessages = new ArrayList<>();
        if (FileUtil.isExistFile(historyPath)) {
            try {
                String content = FileUtil.readFile(historyPath);
                JSONArray array = new JSONArray(content);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    loadedMessages.add(new Message(obj.getString("role"), obj.getString("content")));
                }
            } catch (Exception ignored) {}
        }
        
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                messages.clear();
                messages.addAll(loadedMessages);
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            });
        }
    }

    private void saveHistory() {
        try {
            JSONArray array = new JSONArray();
            for (Message msg : messages) {
                if ("status".equals(msg.role)) continue;
                JSONObject obj = new JSONObject();
                obj.put("role", msg.role);
                obj.put("content", msg.content);
                array.put(obj);
            }
            FileUtil.writeFile(historyPath, array.toString());
        } catch (Exception ignored) {}
    }

    public static class Message {
        String role;
        String content;
        String category;
        String targetXmlName;
        List<String> xmlBlocks;
        List<Action> actions;
        JSONObject jsonData;
        boolean wasApplied;

        static class Action {
            String type;
            String value;
            Action(String type, String value) { this.type = type; this.value = value; }
        }

        Message(String role, String content) {
            this(role, content, null, false);
        }

        Message(String role, String content, String targetXmlName) {
            this(role, content, targetXmlName, false);
        }

        Message(String role, String content, String targetXmlName, boolean ignoreTags) {
            this.role = role;
            this.content = content;
            this.targetXmlName = targetXmlName;
            this.xmlBlocks = ignoreTags ? new ArrayList<>() : extractAllXml(content);
            this.actions = ignoreTags ? new ArrayList<>() : extractActions(content);
        }

        static List<Action> extractActions(String text) {
            List<Action> actions = new ArrayList<>();
            if (text == null) return actions;

            String[] tags = {"CUSTOM_VIEW", "LIBRARY", "IMPORT", "PERMISSION", "LOCAL_LIBRARY", "MANIFEST_ATTR", "MANIFEST_COMPONENT", "MODIFY_VIEW", "ADD_VIEW", "DELETE_VIEW", "MOVE_VIEW"};
            for (String tag : tags) {
                Pattern p = Pattern.compile("\\[" + tag + ":\\s*([^\\]]+)\\]");
                Matcher m = p.matcher(text);
                while (m.find()) {
                    String val = m.group(1).trim();
                    if (!val.isEmpty()) {
                        actions.add(new Action(tag, val));
                    }
                }
            }
            return actions;
        }

        static List<String> extractAllXml(String text) {
            List<String> blocks = new ArrayList<>();
            if (text == null) return blocks;

            int index = 0;
            while ((index = text.indexOf("```", index)) != -1) {
                int blockStart = index + 3;
                if (text.startsWith("xml", blockStart)) blockStart += 3;
                int blockEnd = text.indexOf("```", blockStart);
                if (blockEnd == -1) break;

                String sub = text.substring(blockStart, blockEnd).trim();
                if (isValidXmlStructure(sub)) {
                    blocks.add(sub);
                }
                index = blockEnd + 3;
            }

            if (blocks.isEmpty()) {
                int start = text.indexOf("<");
                int end = text.lastIndexOf(">");
                if (start != -1 && end > start) {
                    String sub = text.substring(start, end + 1).trim();
                    if (isValidXmlStructure(sub)) {
                        blocks.add(sub);
                    }
                }
            }
            return blocks;
        }

        private static boolean isValidXmlStructure(String sub) {
            String trimmed = sub.trim();
            if (!(trimmed.startsWith("<") && (trimmed.endsWith(">") || trimmed.endsWith("/>")))) {
                return false;
            }

            // Check for common Android XML markers
            boolean hasNamespace = trimmed.contains("xmlns:android=");
            boolean hasAndroidAttr = trimmed.contains("android:");
            boolean hasLayoutAttr = trimmed.contains("layout_width") || trimmed.contains("layout_height");

            // Keywords
            boolean hasKeywords = trimmed.contains("Layout") || trimmed.contains("View") ||
                    trimmed.contains("Button") || trimmed.contains("Text") ||
                    trimmed.contains("ImageView") || trimmed.contains("CheckBox");

            return hasKeywords && (hasNamespace || hasAndroidAttr || hasLayoutAttr || (trimmed.endsWith("/>") && trimmed.length() > 30));
        }
    }

    public static class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int VIEW_TYPE_MESSAGE = 0;
        private static final int VIEW_TYPE_STATUS = 1;
        
        private final List<Message> messages;
        private final SkAssistantFragment fragment;

        MessageAdapter(List<Message> messages, SkAssistantFragment fragment) {
            this.messages = messages;
            this.fragment = fragment;
        }

        @Override
        public int getItemViewType(int position) {
            if ("status".equals(messages.get(position).role)) {
                return VIEW_TYPE_STATUS;
            }
            return VIEW_TYPE_MESSAGE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_STATUS) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.sk_assistant_status_item, parent, false);
                return new StatusViewHolder(v);
            }
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.sk_message_item, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof StatusViewHolder) {
                StatusViewHolder statusHolder = (StatusViewHolder) holder;
                String status = messages.get(position).content;
                
                OrbState state = OrbState.WORKING;
                if (status.toLowerCase().contains("searching")) state = OrbState.SEARCHING;
                else if (status.toLowerCase().contains("mapping") || status.toLowerCase().contains("grasping")) state = OrbState.WEAVING;

                OrbState finalState = state;
                ThinkingOrbHelper.setStatusContent(statusHolder.composeView, finalState, status);
                return;
            }

            ViewHolder msgHolder = (ViewHolder) holder;
            Message msg = messages.get(position);
            String contentText = msg.jsonData != null ? msg.jsonData.optString("summary", msg.content) : msg.content;

            msgHolder.cardAssistant.setVisibility(View.GONE);
            msgHolder.cardUser.setVisibility(View.GONE);
            msgHolder.actionContainer.setVisibility(View.GONE);

            if ("user".equals(msg.role)) {
                msgHolder.cardUser.setVisibility(View.VISIBLE);
                msgHolder.tvContentRight.setText(contentText);
            } else {
                msgHolder.cardAssistant.setVisibility(View.VISIBLE);
                msgHolder.tvRoleLeft.setText(msg.role.toUpperCase());
                msgHolder.tvContentLeft.setText(contentText);

                // Set up actions
                boolean hasActions = false;
                msgHolder.btnApply.setVisibility(View.GONE);
                msgHolder.btnRevert.setVisibility(msg.wasApplied ? View.VISIBLE : View.GONE);

                if (msg.wasApplied) {
                    hasActions = true;
                    msgHolder.btnRevert.setOnClickListener(v -> {
                        HapticManager.vibrateRun(v);
                        fragment.undoApply();
                    });
                }

                if (msg.role.equals("assistant")) {
                    if (msg.jsonData != null && !msg.wasApplied) {
                        JSONArray jsonActions = msg.jsonData.optJSONArray("actions");
                        boolean hasRealActions = false;
                        if (jsonActions != null && jsonActions.length() > 0) {
                            for (int i = 0; i < jsonActions.length(); i++) {
                                JSONObject action = jsonActions.optJSONObject(i);
                                if (action != null) {
                                    String actionType = fragment.normalizeActionType(action.optString("type"));
                                    if (!"CHAT_MSG".equals(actionType) && !"MISSION_COMPLETE".equals(actionType)) {
                                        hasRealActions = true;
                                        break;
                                    }
                                }
                            }
                        }

                        if (hasRealActions) {
                            hasActions = true;
                            msgHolder.btnApply.setVisibility(View.VISIBLE);
                            String btnText = "Apply Changes";
                            if ("COMPONENT_ARCHITECT".equals(msg.category)) {
                                btnText = "Add Component";
                            } else if ("UI_DESIGNER".equals(msg.category)) {
                                btnText = "Apply Layout";
                            } else if ("LOGIC_ENGINEER".equals(msg.category)) {
                                btnText = "Continue";
                            }
                            msgHolder.btnApply.setText(btnText);
                            msgHolder.btnApply.setOnClickListener(v -> {
                                HapticManager.vibrateRun(v);
                                fragment.dispatchJsonActions(msg.jsonData, msg.category);
                                msg.wasApplied = true;
                                notifyItemChanged(position);
                            });
                        }
                    } else if (!msg.xmlBlocks.isEmpty() && !msg.wasApplied) {
                        hasActions = true;
                        msgHolder.btnApply.setVisibility(View.VISIBLE);
                        msgHolder.btnApply.setText("Apply Layout");
                        msgHolder.btnApply.setOnClickListener(v -> {
                            HapticManager.vibrateRun(v);
                            if (msg.xmlBlocks.size() == 1) {
                                fragment.layoutSpecialist.applyXml(msg.xmlBlocks.get(0), msg.targetXmlName);
                            } else {
                                fragment.showBlockSelector(msg.xmlBlocks, msg.targetXmlName);
                            }
                        });
                    } else if (!msg.actions.isEmpty() && !msg.wasApplied) {
                        hasActions = true;
                        msgHolder.btnApply.setVisibility(View.VISIBLE);
                        Message.Action action = msg.actions.get(0);
                        msgHolder.btnApply.setText("Run: " + action.type);
                        msgHolder.btnApply.setOnClickListener(v -> {
                            HapticManager.vibrateRun(v);
                            switch(action.type) {
                                case "CUSTOM_VIEW":
                                    String xml = !msg.xmlBlocks.isEmpty() ? msg.xmlBlocks.get(0) : null;
                                    fragment.layoutSpecialist.applyCustomView(action.value, xml);
                                    break;
                                case "LIBRARY": fragment.librarySpecialist.applyLibrary(action.value); break;
                                case "LOCAL_LIBRARY": fragment.librarySpecialist.applyLocalLibrary(action.value); break;
                                case "IMPORT": fragment.codeSpecialist.applyImport(action.value); break;
                                case "PERMISSION": fragment.manifestSpecialist.applyPermission(action.value); break;
                                case "MANIFEST_ATTR": fragment.manifestSpecialist.applyManifestInjection("ATTR", action.value); break;
                                case "MANIFEST_COMPONENT": fragment.manifestSpecialist.applyManifestInjection("COMPONENT", action.value); break;
                                case "MODIFY_VIEW": fragment.handleModifyAction(action.value); break;
                                case "MOVE_VIEW": fragment.handleMoveAction(action.value); break;
                                case "ADD_VIEW": fragment.handleAddAction(action.value); break;
                                case "DELETE_VIEW": fragment.layoutSpecialist.applyDeleteView(action.value, true); break;
                            }
                        });
                    }
                }

                if (hasActions) {
                    msgHolder.actionContainer.setVisibility(View.VISIBLE);
                }
            }
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvRoleLeft, tvContentLeft, tvContentRight;
            View cardAssistant, cardUser, actionContainer;
            Button btnApply, btnRevert;

            ViewHolder(View itemView) {
                super(itemView);
                tvRoleLeft = itemView.findViewById(R.id.tv_role_left);
                tvContentLeft = itemView.findViewById(R.id.tv_content_left);
                tvContentRight = itemView.findViewById(R.id.tv_content_right);
                cardAssistant = itemView.findViewById(R.id.card_assistant);
                cardUser = itemView.findViewById(R.id.card_user);
                actionContainer = itemView.findViewById(R.id.action_container);
                btnApply = itemView.findViewById(R.id.btn_apply);
                btnRevert = itemView.findViewById(R.id.btn_revert);
            }
        }

        static class StatusViewHolder extends RecyclerView.ViewHolder {
            ComposeView composeView;

            StatusViewHolder(View itemView) {
                super(itemView);
                composeView = itemView.findViewById(R.id.compose_status);
                composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed.INSTANCE);
            }
        }
    }
}