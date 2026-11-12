package sketchware.plus.ai;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.core.CorePlugin;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.ImagesPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;

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

import android.widget.Toast;

import java.io.IOException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import sketchware.plus.databinding.DialogCreateNewFileLayoutBinding;
import sketchware.plus.utility.HapticManager;

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
import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import dev.aldi.sayuti.editor.manage.LocalLibrary;
import mod.hey.studios.util.Helper;
import sketchware.plus.BuildConfig;
import sketchware.plus.R;
import sketchware.plus.activities.about.models.AboutResponseModel;
import sketchware.plus.utility.Network;
import com.google.gson.Gson;
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
import sketchware.plus.utility.AssistantPatternManager;
import sketchware.plus.utility.FileUtil;
import sketchware.plus.utility.GsonUtils;
import android.transition.TransitionManager;
import android.widget.LinearLayout;
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
    private FloatingActionButton fabScrollBottom;
    private ObjectAnimator fabBounceAnimator;
    public final List<Message> messages = new ArrayList<>();
    private String historyPath;
    public Button btnUndo;
    private MaterialButton btnSend;
    private EditText etInput;
    private String lastUserPrompt = "";
    private View tokenUsageContainer;
    private TextView tvTokenUsage;
    private MaterialCardView modelSelectorContainer;
    private TextView tvCurrentModel;
    private int lastPromptTokens = 0;
    private int lastCompletionTokens = 0;
    private int lastTotalTokens = 0;
    public ProjectSnapshot undoSnapshot;
    private int retryCount = 0;
    private static final int MAX_RETRIES = 2;
    private volatile boolean isRequestCanceled = false;
    private volatile boolean isAiRunning = false;
    private boolean isSendButtonThinking = false;

    public long aiRunStartTime = 0;
    private final Handler elapsedTimerHandler = new Handler(Looper.getMainLooper());
    private Runnable elapsedTimerRunnable;

    private void startElapsedTimer() {
        stopElapsedTimer();
        elapsedTimerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAiRunning || getActivity() == null) return;
                for (int i = messages.size() - 1; i >= 0; i--) {
                    Message msg = messages.get(i);
                    if ("status".equals(msg.role) && !msg.isLogFinished && msg.steps != null && !msg.steps.isEmpty()) {
                        long elapsed = System.currentTimeMillis() - aiRunStartTime;
                        msg.elapsedSeconds = (int) Math.max(1, (elapsed + 500) / 1000);
                        if (adapter != null) {
                            adapter.notifyItemChanged(i, "payload_timer");
                        }
                        break;
                    }
                }
                elapsedTimerHandler.postDelayed(this, 1000);
            }
        };
        elapsedTimerHandler.postDelayed(elapsedTimerRunnable, 1000);
    }

    private void stopElapsedTimer() {
        if (elapsedTimerRunnable != null) {
            elapsedTimerHandler.removeCallbacks(elapsedTimerRunnable);
            elapsedTimerRunnable = null;
        }
    }

    public LayoutSpecialist layoutSpecialist;
    public ComponentSpecialist componentSpecialist;
    public CodeSpecialist codeSpecialist;
    public LibrarySpecialist librarySpecialist;
    public ManifestSpecialist manifestSpecialist;
    public ChatSpecialist chatSpecialist;
    private ToolOrchestrator toolOrchestrator;
    private ModelFetchManager modelFetchManager;
    private XmlParsingHelper xmlParsingHelper;

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
        modelFetchManager = new ModelFetchManager(this);
        xmlParsingHelper = new XmlParsingHelper(this, layoutSpecialist);
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
        new Thread(this::loadHistory).start();
        log("Assistant Session Started - Activity: " + projectFile.getXmlName());
    }

    public void setProjectFile(ProjectFileBean projectFile) {
        if (this.projectFile != null && !this.projectFile.getXmlName().equals(projectFile.getXmlName())) {
            // Activity changed, reset session to avoid context leakage from previous activity
            //I will change this in the future to make it work with all the activities without having to restart the session
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
            int selectedPattern = AssistantPatternManager.getSelectedPattern(getContext());
            int drawableRes = AssistantPatternManager.getPatternDrawableRes(selectedPattern);
            if (drawableRes != 0) {
                patternBg.setVisibility(View.VISIBLE);
                patternBg.setPattern(patternBg.convertVectorToBitmap(getContext(), drawableRes, 30, 30));
                int dotColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface, 0xFF000000);
                patternBg.setColor(dotColor);
                patternBg.setOpacity(40);
            } else {
                patternBg.setVisibility(View.GONE);
            }
        }

        View rootLayout = view.findViewById(R.id.root_layout);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            boolean isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            int keyboardHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            

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
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }
        adapter = new MessageAdapter(messages, this);
        recyclerView.setAdapter(adapter);

        fabScrollBottom = view.findViewById(R.id.fab_scroll_bottom);
        if (fabScrollBottom != null) {
            fabScrollBottom.setOnClickListener(v -> {
                HapticManager.vibrateRun(v);
                scrollToBottom(true);
            });
        }

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                updateFabScrollBottomVisibility();
            }
        });

        scrollToBottom(true);

        tokenUsageContainer = view.findViewById(R.id.token_usage_container);
        tvTokenUsage = view.findViewById(R.id.tv_token_usage);
        tvTokenUsage.setOnClickListener(v -> showTokenDetails());

        modelSelectorContainer = view.findViewById(R.id.model_selector_container);
        tvCurrentModel = view.findViewById(R.id.tv_current_model);
        updateCurrentModelBadge();

        if (tvCurrentModel != null) {
            tvCurrentModel.setOnClickListener(v -> {
                if (getContext() == null) return;
                SharedPreferences aiPref = getContext().getSharedPreferences("P12", Context.MODE_PRIVATE);
                String provider = aiPref.getString("P12_PROVIDER", "custom");
                String apiKey = "";
                if ("groq".equalsIgnoreCase(provider) || "grog".equalsIgnoreCase(provider)) {
                    apiKey = aiPref.getString("P12_GROQ_API_KEY", "").trim();
                    if (apiKey.isEmpty()) apiKey = aiPref.getString("P12I3", "").trim();
                } else if ("google".equalsIgnoreCase(provider)) {
                    apiKey = aiPref.getString("P12_GEMINI_API_KEY", "").trim();
                    if (apiKey.isEmpty()) apiKey = aiPref.getString("P12I3", "").trim();
                } else {
                    apiKey = aiPref.getString("P12I3", "").trim();
                }
                String endpoint = aiPref.getString("P12I4", "").trim();

                boolean isGoogle = "google".equalsIgnoreCase(provider) || apiKey.startsWith("AIzaSy");
                boolean isFetchable = isGoogle ? !apiKey.isEmpty() : !endpoint.isEmpty();

                if (isFetchable) {
                    HapticManager.vibrateRun(v);
                    modelFetchManager.showQuickModelSelectorDialog();
                }
            });
        }

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
            if (isAiRunning || isSendButtonThinking || isReasoningActive()) {
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

        btnSend.setOnLongClickListener(v -> {
            HapticManager.vibrateRun(v);
            return true;
        });




        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateCurrentModelBadge();
        scrollToBottom();
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

    private void startFabBounceAnimation() {
        if (fabScrollBottom == null) return;
        if (fabBounceAnimator == null) {
            fabBounceAnimator = ObjectAnimator.ofFloat(fabScrollBottom, "translationY", 0f, -10f, 0f);
            fabBounceAnimator.setDuration(1200);
            fabBounceAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            fabBounceAnimator.setRepeatCount(ValueAnimator.INFINITE);
            fabBounceAnimator.setRepeatMode(ValueAnimator.RESTART);
        }
        if (!fabBounceAnimator.isRunning()) {
            fabBounceAnimator.start();
        }
    }

    private void stopFabBounceAnimation() {
        if (fabBounceAnimator != null) {
            fabBounceAnimator.cancel();
            if (fabScrollBottom != null) {
                fabScrollBottom.setTranslationY(0f);
            }
        }
    }

    public void updateFabScrollBottomVisibility() {
        if (fabScrollBottom == null || recyclerView == null || adapter == null) return;

        RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
        if (lm instanceof LinearLayoutManager) {
            LinearLayoutManager llm = (LinearLayoutManager) lm;
            int lastVisiblePos = llm.findLastVisibleItemPosition();
            int totalItems = adapter.getItemCount();

            // Threshold: Only show FAB if user has scrolled up past 4 message items from the bottom
            if (totalItems > 0 && lastVisiblePos != RecyclerView.NO_POSITION && (totalItems - 1 - lastVisiblePos) > 4) {
                if (fabScrollBottom.getVisibility() != View.VISIBLE) {
                    fabScrollBottom.setVisibility(View.VISIBLE);
                    fabScrollBottom.setAlpha(0f);
                    fabScrollBottom.animate().alpha(1f).setDuration(150).start();
                }
                startFabBounceAnimation();
            } else {
                if (fabScrollBottom.getVisibility() == View.VISIBLE) {
                    fabScrollBottom.animate().alpha(0f).setDuration(150)
                            .withEndAction(() -> {
                                fabScrollBottom.setVisibility(View.GONE);
                                stopFabBounceAnimation();
                            })
                            .start();
                } else {
                    stopFabBounceAnimation();
                }
            }
        }
    }

    public boolean isAtBottom() {
        if (recyclerView == null || adapter == null) return true;
        RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
        if (lm instanceof LinearLayoutManager llm) {
            int lastVisiblePos = llm.findLastVisibleItemPosition();
            int totalItems = adapter.getItemCount();
            if (totalItems <= 0 || lastVisiblePos == RecyclerView.NO_POSITION) return true;
            return (totalItems - 1 - lastVisiblePos) <= 3;
        }
        return true;
    }

    public void scrollToBottom() {
        scrollToBottom(false);
    }

    public void scrollToBottom(boolean force) {
        if (recyclerView == null || messages == null || messages.isEmpty()) return;
        if (!force && !isAtBottom()) {
            updateFabScrollBottomVisibility();
            return;
        }
        recyclerView.post(() -> {
            if (adapter != null && messages.size() > 0) {
                int targetPos = messages.size() - 1;
                recyclerView.scrollToPosition(targetPos);
                recyclerView.postDelayed(() -> {
                    if (adapter != null && messages.size() > 0) {
                        recyclerView.scrollToPosition(messages.size() - 1);
                        updateFabScrollBottomVisibility();
                    }
                }, 50);
            }
        });
    }

    void sendMessage(String prompt) {
        log("User Prompt: " + prompt);
        this.lastUserPrompt = prompt;
        isRequestCanceled = false;
        isAiRunning = true;
        Context context = getContext();
        if (context == null) return;
        
        SharedPreferences aiPref = context.getSharedPreferences("P12", Context.MODE_PRIVATE);
        String model = aiPref.getString("P12I5", "");


        messages.add(new Message("user", prompt));
        adapter.notifyItemInserted(messages.size() - 1);
        scrollToBottom(true);
        saveHistory();

        aiRunStartTime = System.currentTimeMillis();
        startElapsedTimer();

        setStatus(model +" is reasoning...");
        jC.projectOperationsExecutor.execute(() -> {
            try { Thread.sleep(600); } catch (Exception ignored) {}
            if (getActivity() != null && !isRequestCanceled) {
                getActivity().runOnUiThread(() -> routeRequest(prompt));
            } else {
                finishAiRun();
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
        final boolean isStatusPresent = status != null && !status.isEmpty();
        final boolean isThinking = (isAiRunning || isStatusPresent) && !isRequestCanceled;
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                updateSendButtonState(isThinking);

                int existingStatusIndex = -1;
                for (int i = messages.size() - 1; i >= 0; i--) {
                    Message m = messages.get(i);
                    if ("status".equals(m.role) && !m.isLogFinished) {
                        existingStatusIndex = i;
                        break;
                    }
                }

                if (status != null && !status.isEmpty()) {
                    if (existingStatusIndex != -1) {
                        Message statusMsg = messages.get(existingStatusIndex);
                        statusMsg.content = status;
                        adapter.notifyItemChanged(existingStatusIndex);
                    } else {
                        Message statusMsg = new Message("status", status);
                        messages.add(statusMsg);
                        adapter.notifyItemInserted(messages.size() - 1);
                    }
                    scrollToBottom(false);
                } else {
                    if (existingStatusIndex != -1) {
                        Message statusMsg = messages.get(existingStatusIndex);
                        // Rule: The activity log replaces the status row ONLY when steps exist,
                        // otherwise fall back to current orb-only status auto-dismiss behavior.
                        if (statusMsg.steps != null && !statusMsg.steps.isEmpty()) {
                            statusMsg.isLogFinished = true;
                            if (statusMsg.elapsedSeconds == 0 && aiRunStartTime > 0) {
                                statusMsg.elapsedSeconds = (int) Math.max(1, (System.currentTimeMillis() - aiRunStartTime + 500) / 1000);
                            }
                            statusMsg.isExpanded = false;
                            for (ActivityStep s : statusMsg.steps) {
                                if (s.status == ActivityStep.Status.RUNNING) {
                                    s.status = ActivityStep.Status.DONE;
                                }
                            }
                            adapter.notifyItemChanged(existingStatusIndex, "payload_steps");
                        } else {
                            messages.remove(existingStatusIndex);
                            adapter.notifyItemRemoved(existingStatusIndex);
                        }
                    }
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

    public void finishAiRun() {
        isAiRunning = false;
        stopElapsedTimer();
        setStatus(null);
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> updateSendButtonState(false));
        }
    }

    private void updateSendButtonState(boolean isThinking) {
        if (btnSend == null) return;
        Context context = getContext();
        if (context == null) return;

        if (isSendButtonThinking == isThinking) {
            return;
        }
        isSendButtonThinking = isThinking;

//        if (isThinking) {
//            HapticManager.startTicking(1000);
//        } else {
//            HapticManager.stopTicking();
//        }

        int targetIcon = isThinking ? R.drawable.ic_mtrl_stop : R.drawable.ic_mtrl_send;
        int targetColor = ContextCompat.getColor(context, isThinking ? R.color.scolor_red_01 : R.color.color_primary);

        btnSend.animate()
                .scaleX(0f)
                .scaleY(0f)
                .rotation(isThinking ? 90f : -90f)
                .setDuration(150)
                .withEndAction(() -> {
                    btnSend.setIconResource(targetIcon);
                    btnSend.setBackgroundTintList(ColorStateList.valueOf(targetColor));
                    btnSend.setIconTintResource(android.R.color.white);
                    btnSend.setRotation(isThinking ? -90f : 90f);
                    btnSend.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .rotation(0f)
                            .setDuration(150)
                            .start();
                })
                .start();
    }

    private boolean isReasoningActive() {
        for (Message msg : messages) {
            if ("status".equals(msg.role) && !msg.isLogFinished) return true;
        }
        return false;
    }

    public void cancelCurrentRequest() {
        isRequestCanceled = true;
        isAiRunning = false;
        stopElapsedTimer();
        if (toolOrchestrator != null) {
            toolOrchestrator.cancel();
        }
        AiClient.cancelCurrentRequest();
        setStatus(null);
        updateSendButtonState(false);

        boolean alreadyAdded = false;
        if (!messages.isEmpty()) {
            Message last = messages.get(messages.size() - 1);
            if ("system".equals(last.role) && "AI response has been stopped.".equals(last.content)) {
                alreadyAdded = true;
            }
        }
        if (!alreadyAdded) {
            messages.add(new Message("system", "AI response has been stopped."));
            if (adapter != null) {
                adapter.notifyItemInserted(messages.size() - 1);
                scrollToBottom();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        saveHistory();
//        cancelCurrentRequest();
    }

    @Override
    public void onStop() {
        super.onStop();
        saveHistory();
//        cancelCurrentRequest();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopElapsedTimer();
        saveHistory();
        cancelCurrentRequest();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelCurrentRequest();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        cancelCurrentRequest();
    }

    public void cancelSKRequests() {
        Context context = getContext();
        if (context == null) return;
        if (btnSend == null) return;

        isSendButtonThinking = false;
        int targetIcon = R.drawable.ic_mtrl_send;
        int targetColor = ContextCompat.getColor(context, R.color.color_primary);

        btnSend.animate()
                .scaleX(0f)
                .scaleY(0f)
                .rotation(90f)
                .setDuration(150)
                .withEndAction(() -> {
                    btnSend.setIconResource(targetIcon);
                    btnSend.setBackgroundTintList(ColorStateList.valueOf(targetColor));
                    btnSend.setIconTintResource(android.R.color.white);
                    btnSend.setRotation(-90f);
                    btnSend.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .rotation(0f)
                            .setDuration(150)
                            .start();
                })
                .start();
    }


    public void updateTokenUsage(int prompt, int completion, int total) {
        this.lastPromptTokens = prompt;
        this.lastCompletionTokens = completion;
        this.lastTotalTokens = total > 0 ? total : (prompt + completion);

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
                tvTokenUsage.setText(formatTokenLabel(lastTotalTokens, limit));
            }
        });
    }

    private String formatTokenLabel(int used, int limit) {
        return formatValue(used) + " / " + formatValue(limit);
    }

    private String formatValue(int value) {
        if (value >= 1000000) {
            float m = value / 1000000.0f;
            return (m % 1 == 0) ? String.format(Locale.US, "%.0fM", m) : String.format(Locale.US, "%.1fM", m);
        }
        if (value >= 1000) {
            float k = value / 1000.0f;
            return (k % 1 == 0) ? String.format(Locale.US, "%.0fk", k) : String.format(Locale.US, "%.1fk", k);
        }
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

        int percent = limit > 0 ? (int) ((lastTotalTokens / (float) limit) * 100) : 0;
        tvSummary.setText(String.format(Locale.US, "Context Usage: %d%%", percent));
        pbUsage.setProgress(Math.min(percent, 100));

        String details = String.format(Locale.US,
                "Total Tokens: %,d\n" +
                        "Prompt Tokens (In): %,d\n" +
                        "Completion Tokens (Out): %,d\n" +
                        "Context Limit: %,d",
                lastTotalTokens, lastPromptTokens, lastCompletionTokens, limit);
        tvDetails.setText(details);

        new MaterialAlertDialogBuilder(getContext())
                .setTitle("Token Usage Details")
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .show();
    }

    public void updateCurrentModelBadge() {
        if (tvCurrentModel == null || getContext() == null) return;
        SharedPreferences aiPref = getContext().getSharedPreferences("P12", Context.MODE_PRIVATE);
        String provider = aiPref.getString("P12_PROVIDER", "custom");
        String apiKey = "";
        if ("groq".equalsIgnoreCase(provider) || "grog".equalsIgnoreCase(provider)) {
            apiKey = aiPref.getString("P12_GROQ_API_KEY", "").trim();
            if (apiKey.isEmpty()) apiKey = aiPref.getString("P12I3", "").trim();
        } else if ("google".equalsIgnoreCase(provider)) {
            apiKey = aiPref.getString("P12_GEMINI_API_KEY", "").trim();
            if (apiKey.isEmpty()) apiKey = aiPref.getString("P12I3", "").trim();
        } else {
            apiKey = aiPref.getString("P12I3", "").trim();
        }
        String endpoint = aiPref.getString("P12I4", "").trim();
        String model = aiPref.getString("P12I5", "").trim();

        boolean isGoogle = "google".equalsIgnoreCase(provider) || apiKey.startsWith("AIzaSy");
        boolean isFetchable = isGoogle ? !apiKey.isEmpty() : !endpoint.isEmpty();

        if (isFetchable) {
            tvCurrentModel.setClickable(true);
            tvCurrentModel.setFocusable(true);
            if (modelSelectorContainer != null) {
                modelSelectorContainer.setClickable(true);
                modelSelectorContainer.setFocusable(true);
                modelSelectorContainer.setAlpha(1.0f);
            }
            if (model.isEmpty()) {
                tvCurrentModel.setText(isGoogle ? "Select Gemini Model" : "Select Model");
            } else {
                tvCurrentModel.setText(model);
            }
        } else {
            tvCurrentModel.setClickable(false);
            tvCurrentModel.setFocusable(false);
            if (modelSelectorContainer != null) {
                modelSelectorContainer.setClickable(false);
                modelSelectorContainer.setFocusable(false);
                modelSelectorContainer.setAlpha(0.6f);
            }
            if (model.isEmpty()) {
                tvCurrentModel.setText(isGoogle ? "Set API Key First" : "Set Endpoint First");
            } else {
                tvCurrentModel.setText(model);
            }
        }

        checkBuildVersionStatus();
    }

    private static int cachedShaPos = -2;

    private void checkBuildVersionStatus() {
        if (getContext() == null || tvCurrentModel == null) return;

        if (cachedShaPos != -2) {
            applyBuildStatusColor(cachedShaPos);
            return;
        }

        applyBuildStatusColor(0);

        String commitsUrl = String.format(Helper.getResString(R.string.link_github_commits_url), 30, 1);
        new Network().get(commitsUrl, response -> {
            try {
                if (response != null && getContext() != null) {
                    Gson gson = new Gson();
                    AboutResponseModel.CommitDetails[] commitDetailsArray = gson.fromJson(response, AboutResponseModel.CommitDetails[].class);
                    if (commitDetailsArray != null && commitDetailsArray.length > 0) {
                        int shaPos = -1;
                        for (int i = 0; i < commitDetailsArray.length; i++) {
                            if (commitDetailsArray[i].getSha().equals(BuildConfig.GIT_HASH)) {
                                shaPos = i;
                                break;
                            }
                        }
                        cachedShaPos = shaPos;
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> applyBuildStatusColor(cachedShaPos));
                        }
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    private void applyBuildStatusColor(int shaPos) {
        if (tvCurrentModel == null) return;

        int bgColor, textColor;
        if (shaPos == 0) {
            // Current
            bgColor = MaterialColors.getColor(tvCurrentModel, R.attr.colorAmberContainer);
            textColor = MaterialColors.getColor(tvCurrentModel, R.attr.colorOnAmberContainer);
        } else {
            // shaPos > 0 or -1 (not found) -> new update
            bgColor = MaterialColors.getColor(tvCurrentModel, R.attr.colorCoolGreenContainer);
            textColor = MaterialColors.getColor(tvCurrentModel, R.attr.colorOnCoolGreenContainer);
        }

        if (modelSelectorContainer != null) {
            modelSelectorContainer.setCardBackgroundColor(bgColor);
        }
        tvCurrentModel.setTextColor(textColor);
    }

    public JSONArray getChatHistory() {
        JSONArray chatHistory = new JSONArray();
        int startIndex = Math.max(0, messages.size() - 2);
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
                finishAiRun();
                handleAiResponse(response, category);
            }

            @Override
            public void onSuccess(String response, int promptTokens, int completionTokens, int totalTokens) {
                if (isRequestCanceled) return;
                log("AI Response (" + category + "): " + response);
                finishAiRun();
                updateTokenUsage(promptTokens, completionTokens, totalTokens);
                handleAiResponse(response, category);
            }

            @Override
            public void onError(String error) {
                log("AI Error (" + category + "): " + error);
                finishAiRun();
                if (!isRequestCanceled && !"Canceled".equals(error)) {
                    restoreFailedPromptToEditText(error);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            messages.add(new Message("system", "Error: " + error));
                            adapter.notifyItemInserted(messages.size() - 1);
                            scrollToBottom();
                        });
                    }
                }
            }

            @Override
            public void onRetry(int retryCount, long delayMillis) {
                SharedPreferences aiPref = getContext() != null ? getContext().getSharedPreferences("P12", Context.MODE_PRIVATE) : null;
                String model = aiPref != null ? aiPref.getString("P12I5", "AI") : "AI";
                if (model == null || model.isEmpty()) model = "AI";

                final String finalModel = model;
                final long totalSeconds = Math.max(1L, delayMillis / 1000L);
                for (int sec = 1; sec <= totalSeconds; sec++) {
                    final int currentSec = sec;
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (getActivity() != null) {
                            setStatus(finalModel + " is generating... " + currentSec + "s");
                        }
                    }, currentSec * 1000L);
                }
                setStatus(finalModel + " is generating... 1s");
            }
        });
    }

    public void addSystemMessage(String content) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> addSystemMessage(content));
            }
            return;
        }
        messages.add(new Message("system", content));
        adapter.notifyItemInserted(messages.size() - 1);
        scrollToBottom();
    }

    public void restoreFailedPromptToEditText(String error) {
        if (error == null) return;
        String lower = error.toLowerCase();
        boolean isNetworkError = lower.contains("unable to resolve host")
                || lower.contains("no address associated with hostname")
                || lower.contains("unknownhostexception")
                || lower.contains("connectexception")
                || lower.contains("failed to connect")
                || lower.contains("unable to resolve")
                || lower.contains("no internet connection")
                || lower.contains("sockettimeoutexception")
                || lower.contains("network error")
                || lower.contains("ai request failed");

        if (isNetworkError && lastUserPrompt != null && !lastUserPrompt.trim().isEmpty()) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (etInput != null) {
                        etInput.setText(lastUserPrompt);
                        etInput.setSelection(etInput.getText().length());
                        etInput.requestFocus();
                    }
                    if (messages != null && !messages.isEmpty()) {
                        int lastIdx = messages.size() - 1;
                        Message lastMsg = messages.get(lastIdx);
                        if ("user".equals(lastMsg.role) && lastUserPrompt.equals(lastMsg.content)) {
                            messages.remove(lastIdx);
                            adapter.notifyItemRemoved(lastIdx);
                        }
                    }
                });
            }
        }
    }

    public void addAssistantMessage(String content) {
        if (content == null || content.trim().isEmpty()) return;
        appendAssistantMessage(new Message("assistant", content));
    }

    void appendAssistantMessage(Message msg) {
        messages.add(msg);
        adapter.notifyItemInserted(messages.size() - 1);
        scrollToBottom();
        saveHistory();
        HapticManager.vibrateAssistantDone();
    }

    public void setUndoVisible(boolean visible) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> setUndoVisible(visible));
            }
            return;
        }
        if (btnUndo != null) {
            btnUndo.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public void handleXmlError(String editedXml, String errorMessage, String targetXmlName) {
        xmlParsingHelper.handleXmlError(editedXml, errorMessage, targetXmlName);
    }

    public void handleAiResponse(String response, String categoryFallback) {
        if (getActivity() == null) return;
        ActivityTracker.recordActivity(ActivityTracker.DEFAULT_FILE_PATH);
        getActivity().runOnUiThread(() -> {
            boolean isLayoutResponse = "UI_DESIGNER".equals(categoryFallback) || response.contains("<layout_xml>");
            try {
                String layoutXml = null;
                if (response.contains("<layout_xml>")) {
                    Matcher xmlMatcher = Pattern.compile("<layout_xml>([\\s\\S]*?)</layout_xml>", Pattern.CASE_INSENSITIVE).matcher(response);
                    if (xmlMatcher.find()) {
                        layoutXml = xmlMatcher.group(1).trim();
                    }
                }

                // Bulletproof JSON extraction via Regex (Finds outermost { })
                String cleanJson = response;
                Matcher matcher = Pattern.compile("\\{.*\\}", Pattern.DOTALL).matcher(response);
                if (matcher.find()) {
                    cleanJson = matcher.group(0);
                }

                JSONObject json;
                try {
                    json = new JSONObject(cleanJson);
                } catch (Exception e) {
                    if (layoutXml != null) {
                        json = new JSONObject();
                        json.put("category", "UI_DESIGNER");
                        json.put("summary", "Applied layout XML");
                        JSONArray actions = new JSONArray();
                        JSONObject action = new JSONObject();
                        action.put("type", "UPDATE_LAYOUT_FULL_XML");
                        action.put("xml", layoutXml);
                        actions.put(action);
                        json.put("actions", actions);
                    } else {
                        throw e;
                    }
                }

                if (layoutXml != null && json.has("actions")) {
                    JSONArray actions = json.getJSONArray("actions");
                    for (int i = 0; i < actions.length(); i++) {
                        JSONObject act = actions.getJSONObject(i);
                        if ("UPDATE_LAYOUT_FULL_XML".equals(normalizeActionType(act.optString("type"))) && (!act.has("xml") || act.getString("xml").isEmpty())) {
                            act.put("xml", layoutXml);
                        }
                    }
                }

                // Dynamic category if provided by new prompt
                String actualCategory = json.optString("category", categoryFallback);
                String summary = json.optString("summary", "Done.");

                Message msg = new Message("assistant", summary);
                msg.jsonData = json;
                msg.category = actualCategory;
                appendAssistantMessage(msg);
                cancelSKRequests();


                // Auto dispatch for autonomous specialists (like CodeSpecialist)
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
                if (isLayoutResponse) {
                    messages.add(new Message("system", "Layout response parse error: " + e.getMessage() + ". Please re-ask request."));
                    adapter.notifyItemInserted(messages.size() - 1);
                    scrollToBottom();
                    setStatus(null);
                    retryCount = 0;
                    return;
                }
                messages.add(new Message("system", e.getMessage()));
                adapter.notifyItemInserted(messages.size() - 1);
                scrollToBottom();
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
                    appendAssistantMessage(new Message("assistant", response, null, true));
                }
            }
        });
    }

    private void retryFixJson(String category, String malformedResponse) {
        String systemPrompt = "The user provided a malformed response. " +
                "Return ONLY the fixed, valid JSON object for the IDE.\n" +
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
                SharedPreferences aiPref = getContext() != null ? getContext().getSharedPreferences("P12", Context.MODE_PRIVATE) : null;
                String model = aiPref != null ? aiPref.getString("P12I5", "AI") : "AI";
                if (model == null || model.isEmpty()) model = "AI";

                final String finalModel = model;
                final long totalSeconds = Math.max(1L, delayMillis / 1000L);
                for (int sec = 1; sec <= totalSeconds; sec++) {
                    final int currentSec = sec;
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (getActivity() != null) {
                            setStatus(finalModel + " is generating... " + currentSec + "s");
                        }
                    }, currentSec * 1000L);
                }
                setStatus(finalModel + " is generating... 1s");
            }
        });
    }

    private String buildLibraryContext() {
        List<String> available = LocalLibrariesUtil.getAllLocalLibraryNames();
        List<String> enabled = new ArrayList<>();
        for (HashMap<String, Object> lib : LocalLibrariesUtil.getLocalLibraries(scId)) {
            Object n = lib.get("name");
            if (n instanceof String) enabled.add((String) n);
        }

        StringBuilder sb = new StringBuilder("ENABLED LOCAL LIBRARIES (can be disabled):\n");
        if (enabled.isEmpty()) sb.append("none\n");
        for (String n : enabled) sb.append("- ").append(n).append("\n");

        sb.append("\nAVAILABLE TO ENABLE:\n");
        boolean any = false;
        for (String n : available) {
            if (!enabled.contains(n)) {
                sb.append("- ").append(n).append("\n");
                any = true;
            }
        }
        if (!any) sb.append("none\n");
        return sb.toString();
    }

    public String gatherScopedContext(Context context, String category, String prompt) {
        try {
            if ("LIBRARY_MANAGER".equals(category)) return buildLibraryContext();
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
                            sb.append("Current Java:\n").append(javaCode).append("\n\n");

                            sb.append(ProjectStructureAide.getProjectStructureSummary(scId, projectFile.getJavaName())).append("\n");

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
                        } /*else if ("LIBRARY_MANAGER".equals(category)) {
                            List<String> available = LocalLibrariesUtil.getAllLocalLibraryNames();
                            if (available.isEmpty()) {
                                sb.append("No local libraries found in /.sketchware/libs/local_libs/.\n");
                            } else {
                                ArrayList<HashMap<String, Object>> enabled = LocalLibrariesUtil.getLocalLibraries(scId);
                                sb.append("AVAILABLE LOCAL LIBRARIES:\n");
                                for (String libName : available) {
                                    boolean isEnabled = false;
                                    for (HashMap<String, Object> lib : enabled) {
                                        if (libName.equals(lib.get("name"))) {
                                            isEnabled = true;
                                            break;
                                        }
                                    }
                                    sb.append("- ").append(libName).append(isEnabled ? " [ENABLED]" : " [AVAILABLE]").append("\n");
                                }
                            }
                        }*/ else if ("SYSTEM_MANIFEST".equals(category)) {
                            sb.append("Manifest Overview: Full manifest XML is omitted from context to save tokens.\n");
                            sb.append("Use the SEARCH_MANIFEST action (with query: \"permission\", \"activity\", \"service\", \"receiver\", or a specific keyword) to inspect sections of AndroidManifest.xml.\n");
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

                        // Generated Java (only if absolutely relevant)
                        boolean needsJava = userPrompt.toLowerCase().contains("java code")
                                || userPrompt.toLowerCase().contains("full source")
                                || userPrompt.toLowerCase().contains("generated code");

                        if (needsJava) {
                            String javaCode = new Jx(workspace.N, projectFile, dataManager).generateCode(false, scId);
                            if (javaCode.length() > 8000) {
                                sb.append("Current Java Code: [Omitted for length, too many tokens. Use read_method instead]\n");
                            } else {
                                sb.append("Current Java Code:\n").append(javaCode).append("\n");
                            }
                        } else {
                            sb.append("Java Context: Full source omitted to save tokens. Use 'list_methods' or 'read_method' to inspect logic.\n");
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
                        List<String> available = LocalLibrariesUtil.getAllLocalLibraryNames();
                        if (!available.isEmpty()) {
                            ArrayList<HashMap<String, Object>> enabled = LocalLibrariesUtil.getLocalLibraries(scId);
                            sb.append("\nLocal Libraries:\n");
                            for (String libName : available) {
                                boolean isEnabled = false;
                                for (HashMap<String, Object> lib : enabled) {
                                    if (libName.equals(lib.get("name"))) {
                                        isEnabled = true;
                                        break;
                                    }
                                }
                                if (isEnabled) {
                                    sb.append("- ").append(libName).append(" [ENABLED]\n");
                                } else if (userPrompt.toLowerCase().contains("library") || userPrompt.toLowerCase().contains("enable")) {
                                    sb.append("- ").append(libName).append(" [AVAILABLE]\n");
                                }
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
                                if (("customImport".equals(b.opCode) || "customImport2".equals(b.opCode) || "createImport".equals(b.opCode))
                                        && b.parameters != null && !b.parameters.isEmpty()) {
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




    String getXmlRules() {
        return "Rules for Sketchware plus XML:\n" +
                "- Match the structure of Sketchware's generated XML. Match the property style.\n" +
                "- Always make sure your changes are nice looking as possible .\n" +
                "- Provide the FULL XML layout code when suggesting changes.";
    }

    boolean isRequestCanceled() {
        return isRequestCanceled;
    }

    void appendSystemMessage(String text) {
        messages.add(new Message("system", text));
        adapter.notifyItemInserted(messages.size() - 1);
        scrollToBottom();
    }


    private void undoApply() {
        if (undoSnapshot != null) {
            String xmlName = projectFile.getXmlName();
            undoSnapshot.restore(scId, xmlName, getContext());

            undoSnapshot = null;
            btnUndo.setVisibility(View.GONE);

            messages.add(new Message("system", "Changes reverted."));
            adapter.notifyItemInserted(messages.size() - 1);
            scrollToBottom();

            refreshDesigner();
        }
    }

    public void refreshDesigner() {
            Activity activity = getActivity();
             if (activity instanceof DesignActivity da) {
            activity.runOnUiThread(da::refresh);
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
        android.util.Log.d("SkAssistant", message);
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
            refreshDesigner();
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
                scrollToBottom(true);
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
        List<ActivityStep> steps;
        boolean isLogFinished;
        int elapsedSeconds;
        boolean isExpanded = true;

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

            String[] tags = {"CUSTOM_VIEW", "LIBRARY", "IMPORT", "LOCAL_LIBRARY", "MODIFY_VIEW", "ADD_VIEW", "DELETE_VIEW", "MOVE_VIEW"};
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
        private static final int VIEW_TYPE_ACTIVITY_LOG = 2;
        
        private final List<Message> messages;
        private final SkAssistantFragment fragment;
        private Markwon markwon;
        private int lastAnimatedPosition = -1;

        private void runPopInAnimation(View view) {
            if (view == null) return;
            view.setScaleX(0.8f);
            view.setScaleY(0.8f);
            view.setAlpha(0f);
            view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(1.0f)
                    .setDuration(550)
                    .setInterpolator(new OvershootInterpolator(1.8f))
                    .start();
        }

        MessageAdapter(List<Message> messages, SkAssistantFragment fragment) {
            this.messages = messages;
            this.fragment = fragment;
        }

        @Override
        public int getItemViewType(int position) {
            Message msg = messages.get(position);
            if ("status".equals(msg.role)) {
                if (msg.steps != null && !msg.steps.isEmpty()) {
                    return VIEW_TYPE_ACTIVITY_LOG;
                }
                return VIEW_TYPE_STATUS;
            }
            return VIEW_TYPE_MESSAGE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_ACTIVITY_LOG) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_activity_log_card, parent, false);
                return new ActivityLogViewHolder(v);
            }
            if (viewType == VIEW_TYPE_STATUS) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.sk_assistant_status_item, parent, false);
                return new StatusViewHolder(v);
            }
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.sk_message_item, parent, false);
            return new ViewHolder(v);
        }

        private OrbState getOrbStateForMessage(Message msg) {
            if (msg.isLogFinished) {
                boolean hasFailed = false;
                if (msg.steps != null) {
                    for (ActivityStep s : msg.steps) {
                        if (s.status == ActivityStep.Status.FAILED) {
                            hasFailed = true;
                            break;
                        }
                    }
                }
                return hasFailed ? OrbState.ERROR : OrbState.SUCCESS;
            } else {
                String currentLabel = msg.content;
                if (msg.steps != null) {
                    for (int i = msg.steps.size() - 1; i >= 0; i--) {
                        if (msg.steps.get(i).status == ActivityStep.Status.RUNNING) {
                            currentLabel = msg.steps.get(i).label;
                            break;
                        }
                    }
                }
                return resolveOrbState(currentLabel);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
            if (!payloads.isEmpty() && holder instanceof ActivityLogViewHolder) {
                ActivityLogViewHolder logHolder = (ActivityLogViewHolder) holder;
                Message msg = messages.get(position);

                int elapsed = msg.elapsedSeconds;
                if (elapsed <= 0 && fragment.aiRunStartTime > 0) {
                    elapsed = (int) Math.max(1, (System.currentTimeMillis() - fragment.aiRunStartTime) / 1000);
                }
                logHolder.tvElapsed.setText("Worked for " + elapsed + "s");

                if (payloads.contains("payload_steps")) {
                    logHolder.tvTitle.setText(msg.isLogFinished ? "Processing complete" : "Working...");
                    OrbState state = getOrbStateForMessage(msg);
                    if (logHolder.currentOrbState != state) {
                        logHolder.currentOrbState = state;
                        ThinkingOrbHelper.setStatusContent(logHolder.composeOrb, state, null);
                    }
                    if (logHolder.rvSteps.getAdapter() instanceof ActivityLogAdapter) {
                        ((ActivityLogAdapter) logHolder.rvSteps.getAdapter()).notifyDataSetChanged();
                    } else if (msg.steps != null) {
                        logHolder.rvSteps.setAdapter(new ActivityLogAdapter(msg.steps));
                    }
                }
                return;
            }
            super.onBindViewHolder(holder, position, payloads);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof ActivityLogViewHolder) {
                ActivityLogViewHolder logHolder = (ActivityLogViewHolder) holder;
                Message msg = messages.get(position);

                logHolder.tvTitle.setText(msg.isLogFinished ? "Processing complete" : "Working...");

                int elapsed = msg.elapsedSeconds;
                if (elapsed <= 0 && fragment.aiRunStartTime > 0) {
                    elapsed = (int) Math.max(1, (System.currentTimeMillis() - fragment.aiRunStartTime) / 1000);
                }
                logHolder.tvElapsed.setText("Worked for " + elapsed + "s");

                OrbState state = getOrbStateForMessage(msg);
                if (logHolder.currentOrbState != state) {
                    logHolder.currentOrbState = state;
                    ThinkingOrbHelper.setStatusContent(logHolder.composeOrb, state, null);
                }

                logHolder.ivChevron.setRotation(msg.isExpanded ? 90f : 0f);
                logHolder.layoutBody.setVisibility(msg.isExpanded ? View.VISIBLE : View.GONE);

                logHolder.layoutHeader.setOnClickListener(v -> {
                    msg.isExpanded = !msg.isExpanded;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        TransitionManager.beginDelayedTransition((ViewGroup) logHolder.itemView);
                    }
                    logHolder.layoutBody.setVisibility(msg.isExpanded ? View.VISIBLE : View.GONE);
                    logHolder.ivChevron.animate().rotation(msg.isExpanded ? 90f : 0f).setDuration(200).start();
                });

                if (logHolder.rvSteps.getAdapter() instanceof ActivityLogAdapter) {
                    ((ActivityLogAdapter) logHolder.rvSteps.getAdapter()).notifyDataSetChanged();
                } else if (msg.steps != null) {
                    logHolder.rvSteps.setAdapter(new ActivityLogAdapter(msg.steps));
                }
                return;
            }

            if (holder instanceof StatusViewHolder) {
                StatusViewHolder statusHolder = (StatusViewHolder) holder;
                String status = messages.get(position).content;
                OrbState state = resolveOrbState(status);
                ThinkingOrbHelper.setStatusContent(statusHolder.composeView, state, status);
                return;
            }

            ViewHolder msgHolder = (ViewHolder) holder;
            Message msg = messages.get(position);
            String contentText = msg.jsonData != null ? msg.jsonData.optString("summary", msg.content) : msg.content;

            msgHolder.cardAssistant.setVisibility(View.GONE);
            msgHolder.cardUser.setVisibility(View.GONE);
            msgHolder.actionContainer.setVisibility(View.GONE);

            View.OnLongClickListener copyLongClickListener = v -> {
                HapticManager.vibrateRun(v);

                View popupView = LayoutInflater.from(v.getContext()).inflate(R.layout.layout_pill_copy_popup, null);

                popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                int popupWidth = popupView.getMeasuredWidth();
                int popupHeight = popupView.getMeasuredHeight();

                PatternBackgroundView patternBg = popupView.findViewById(R.id.pattern_bg);
                if (patternBg != null) {
                    ViewGroup.LayoutParams lp = patternBg.getLayoutParams();
                    lp.width = popupWidth;
                    lp.height = popupHeight;
                    patternBg.setLayoutParams(lp);

                    int selectedPattern = AssistantPatternManager.getSelectedPattern(v.getContext());
                    int drawableRes = AssistantPatternManager.getPatternDrawableRes(selectedPattern);
                    if (drawableRes != 0) {
                        patternBg.setVisibility(View.VISIBLE);
                        patternBg.setPattern(patternBg.convertVectorToBitmap(v.getContext(), drawableRes, 30, 30));
                        int dotColor = MaterialColors.getColor(popupView, com.google.android.material.R.attr.colorOnSurface, 0xFF000000);
                        patternBg.setColor(dotColor);
                        patternBg.setOpacity(40);
                    } else {
                        patternBg.setVisibility(View.GONE);
                    }
                }

                PopupWindow popupWindow = new PopupWindow(
                        popupView,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        true
                );
                popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                popupWindow.setElevation(16f);

                popupView.findViewById(R.id.btn_copy_action).setOnClickListener(btn -> {
                    HapticManager.vibrateRun(btn);
                    ClipboardManager clipboard = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        ClipData clip = ClipData.newPlainText("Message", contentText);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(v.getContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
                    }
                    popupWindow.dismiss();
                });

                View resendAction = popupView.findViewById(R.id.btn_resend_action);
                if (resendAction != null) {
                    resendAction.setVisibility("user".equals(msg.role) ? View.VISIBLE : View.GONE);
                    resendAction.setOnClickListener(btn -> {
                        HapticManager.vibrateRun(btn);
                        if (fragment != null) {
                            fragment.sendMessage(contentText);
                        }
                        popupWindow.dismiss();
                    });
                }

                int xOffset = (v.getWidth() - popupWidth) / 2;
                int yOffset = -(v.getHeight() + popupHeight + 12);

                popupWindow.showAsDropDown(v, xOffset, yOffset);
                return true;
            };

            msgHolder.cardUser.setOnLongClickListener(copyLongClickListener);
            msgHolder.tvContentRight.setOnLongClickListener(copyLongClickListener);
            msgHolder.cardAssistant.setOnLongClickListener(copyLongClickListener);
            msgHolder.tvContentLeft.setOnLongClickListener(copyLongClickListener);
            msgHolder.tvRoleLeft.setOnLongClickListener(copyLongClickListener);

            if ("user".equals(msg.role)) {
                msgHolder.cardUser.setVisibility(View.VISIBLE);
                msgHolder.tvContentRight.setText(contentText);
                if (position > lastAnimatedPosition) {
                    lastAnimatedPosition = position;
                    runPopInAnimation(msgHolder.cardUser);
                } else {
                    msgHolder.cardUser.setScaleX(1f);
                    msgHolder.cardUser.setScaleY(1f);
                    msgHolder.cardUser.setAlpha(1f);
                }
            } else {
                msgHolder.cardAssistant.setVisibility(View.VISIBLE);
                msgHolder.tvRoleLeft.setText(msg.role.toUpperCase());
                if (position > lastAnimatedPosition) {
                    lastAnimatedPosition = position;
                    runPopInAnimation(msgHolder.cardAssistant);
                } else {
                    msgHolder.cardAssistant.setScaleX(1f);
                    msgHolder.cardAssistant.setScaleY(1f);
                    msgHolder.cardAssistant.setAlpha(1f);
                }

                Context ctx = msgHolder.tvContentLeft.getContext();
                SharedPreferences aiPref = ctx.getSharedPreferences("P12", Context.MODE_PRIVATE);
                boolean isMarkdownEnabled = aiPref.getBoolean("P12_MARKDOWN_ENABLED", true);

                if (isMarkdownEnabled) {
                    if (markwon == null) {
                        markwon = Markwon.builder(ctx)
                                .usePlugin(CorePlugin.create())
                                .usePlugin(TablePlugin.create(ctx))
                                .usePlugin(TaskListPlugin.create(ctx))
                                .usePlugin(LinkifyPlugin.create())
                                .usePlugin(HtmlPlugin.create())
                                .usePlugin(ImagesPlugin.create())
                                .usePlugin(new AbstractMarkwonPlugin() {
                                    @Override
                                    public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
                                        int codeBg = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSurfaceVariant, 0xFF333333);
                                        int codeText = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFE0E0E0);
                                        builder.codeBlockBackgroundColor(codeBg)
                                               .codeTextColor(codeText)
                                               .codeBlockTypeface(Typeface.MONOSPACE)
                                               .codeTypeface(Typeface.MONOSPACE);
                                    }
                                })
                                .build();
                    }
                    markwon.setMarkdown(msgHolder.tvContentLeft, contentText);
                } else {
                    msgHolder.tvContentLeft.setText(contentText);
                }

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

        static class ActivityLogViewHolder extends RecyclerView.ViewHolder {
            ComposeView composeOrb;
            TextView tvTitle;
            TextView tvElapsed;
            ImageView ivChevron;
            LinearLayout layoutHeader;
            LinearLayout layoutBody;
            RecyclerView rvSteps;
            OrbState currentOrbState;

            ActivityLogViewHolder(View itemView) {
                super(itemView);
                composeOrb = itemView.findViewById(R.id.compose_header_orb);
                tvTitle = itemView.findViewById(R.id.tv_header_title);
                tvElapsed = itemView.findViewById(R.id.tv_header_elapsed);
                ivChevron = itemView.findViewById(R.id.iv_chevron);
                layoutHeader = itemView.findViewById(R.id.layout_header);
                layoutBody = itemView.findViewById(R.id.layout_body);
                rvSteps = itemView.findViewById(R.id.rv_steps);
                composeOrb.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed.INSTANCE);
                rvSteps.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
                RecyclerView.ItemAnimator childAnimator = rvSteps.getItemAnimator();
                if (childAnimator instanceof SimpleItemAnimator) {
                    ((SimpleItemAnimator) childAnimator).setSupportsChangeAnimations(false);
                }
            }
        }
    }

    public static OrbState resolveOrbState(String text) {
        OrbState state = OrbState.WORKING;
        if (text != null) {
            String lower = text.toLowerCase(java.util.Locale.US);
            if (lower.contains("error") || lower.contains("fail") || lower.contains("retry") || lower.contains("rate limit")) {
                state = OrbState.ERROR;
            } else if (lower.contains("searching") || lower.contains("fetching") || lower.contains("browsing") || lower.contains("web")) {
                state = OrbState.SEARCHING;
            } else if (lower.contains("mapping") || lower.contains("weaving")) {
                state = OrbState.WEAVING;
            } else if (lower.contains("thinking") || lower.contains("reasoning") || lower.contains("analyzing") || lower.contains("grasping")) {
                state = OrbState.THINKING;
            } else if (lower.contains("building") || lower.contains("applying") || lower.contains("patching") || lower.contains("injecting") || lower.contains("adding")) {
                state = OrbState.BUILDING;
            } else if (lower.contains("complete") || lower.contains("success") || lower.contains("done") || lower.contains("finished")) {
                state = OrbState.SUCCESS;
            }
        }
        return state;
    }

    public static int getToolIconRes(String toolName) {
        if (toolName == null) return R.drawable.ic_mtrl_terminal;
        String lower = toolName.toLowerCase(java.util.Locale.US);
        if (lower.contains("search") || lower.contains("find") || lower.contains("grep")) {
            return R.drawable.ic_mtrl_search;
        } else if (lower.contains("web") || lower.contains("url")) {
            return R.drawable.ic_mtrl_web;
        } else if (lower.contains("layout") || lower.contains("view")) {
            return R.drawable.ic_mtrl_design;
        } else if (lower.contains("java") || lower.contains("code") || lower.contains("method") || lower.contains("outline")) {
            return R.drawable.ic_mtrl_code;
        } else if (lower.contains("manifest") || lower.contains("permission")) {
            return R.drawable.ic_mtrl_shield_lock;
        } else if (lower.contains("component") || lower.contains("variable") || lower.contains("list") || lower.contains("block")) {
            return R.drawable.ic_mtrl_component;
        } else if (lower.contains("device")) {
            return R.drawable.ic_mtrl_devices;
        } else if (lower.contains("history")) {
            return R.drawable.ic_mtrl_history;
        }
        return R.drawable.ic_mtrl_terminal;
    }

    public static String getFriendlyToolLabel(String toolName) {
        if (toolName == null || toolName.trim().isEmpty()) return "Executing operation";
        switch (toolName) {
            case "read_file":
                return "Reading code-editing behavior";
            case "search_web":
            case "web_search":
                return "Searching the web";
            case "read_url":
                return "Reading webpage content";
            case "list_files":
                return "Listing project files";
            case "find_files":
                return "Finding files in project";
            case "get_layout_xml":
                return "Inspecting layout XML";
            case "update_layout_xml":
                return "Updating layout UI";
            case "apply_layout_xml":
                return "Applying layout changes";
            case "find_manifest_entries":
                return "Searching AndroidManifest";
            case "add_java_imports":
                return "Adding Java imports";
            case "add_source_code":
                return "Adding Java source code";
            case "read_method":
                return "Reading Java method";
            case "list_methods":
                return "Listing methods in file";
            case "view_file_outline":
                return "Inspecting file outline";
            case "grep_code":
                return "Searching codebase";
            case "modify_java_file":
            case "patch_java_file":
                return "Patching Java file";
            case "modify_manifest_xml":
            case "patch_manifest_xml":
                return "Modifying AndroidManifest";
            case "toggle_local_library":
                return "Configuring local library";
            case "add_manifest_permission":
                return "Adding Android permission";
            case "add_sketchware_component":
                return "Adding Sketchware component";
            case "list_components":
                return "Listing components";
            case "apply_custom_view":
                return "Applying custom view";
            case "get_device_info":
                return "Querying device metadata";
            case "search_chat_history":
                return "Searching chat history";
            case "get_project_structure":
                return "Inspecting project structure";
            case "add_variables":
                return "Adding project variables";
            case "add_lists":
                return "Adding project lists";
            case "add_more_block":
                return "Creating More Block function";
            case "register_event":
                return "Registering event listener";
            case "create_activity":
                return "Creating new activity";
            default:
                return "Running " + toolName;
        }
    }

    public void onToolExecutionStarted(String toolName) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            int existingStatusIndex = -1;
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message m = messages.get(i);
                if ("status".equals(m.role) && !m.isLogFinished) {
                    existingStatusIndex = i;
                    break;
                }
            }

            Message statusMsg;
            if (existingStatusIndex != -1) {
                statusMsg = messages.get(existingStatusIndex);
            } else {
                statusMsg = new Message("status", "Working...");
                messages.add(statusMsg);
                existingStatusIndex = messages.size() - 1;
                adapter.notifyItemInserted(existingStatusIndex);
            }

            if (statusMsg.steps == null) {
                statusMsg.steps = new ArrayList<>();
            }

            String label = getFriendlyToolLabel(toolName);
            statusMsg.content = label;
            ActivityStep step = new ActivityStep(toolName, label, ActivityStep.Status.RUNNING, System.currentTimeMillis());
            statusMsg.steps.add(step);
            statusMsg.isExpanded = true;

            if (statusMsg.steps.size() == 1) {
                adapter.notifyItemChanged(existingStatusIndex);
            } else {
                adapter.notifyItemChanged(existingStatusIndex, "payload_steps");
            }
            scrollToBottom(false);
        });
    }

    public void onToolExecutionFinished(String toolName, boolean success) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            int existingStatusIndex = -1;
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message m = messages.get(i);
                if ("status".equals(m.role) && !m.isLogFinished) {
                    existingStatusIndex = i;
                    break;
                }
            }

            if (existingStatusIndex == -1) return;
            Message statusMsg = messages.get(existingStatusIndex);
            if (statusMsg.steps == null || statusMsg.steps.isEmpty()) return;

            for (int i = statusMsg.steps.size() - 1; i >= 0; i--) {
                ActivityStep s = statusMsg.steps.get(i);
                if (s.status == ActivityStep.Status.RUNNING && (toolName == null || toolName.equals(s.toolName))) {
                    s.status = success ? ActivityStep.Status.DONE : ActivityStep.Status.FAILED;
                    break;
                }
            }

            adapter.notifyItemChanged(existingStatusIndex, "payload_steps");
        });
    }
}