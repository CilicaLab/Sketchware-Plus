package com.besome.sketch.tools;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.PopupMenu;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import mod.hey.studios.util.CompileLogHelper;
import mod.hey.studios.util.Helper;
import mod.jbk.diagnostic.CompileErrorSaver;
import mod.jbk.util.AddMarginOnApplyWindowInsetsListener;
import sketchware.plus.ai.AiClient;
import sketchware.plus.databinding.CompileLogBinding;
import sketchware.plus.utility.HapticManager;
import sketchware.plus.utility.SketchwareUtil;

public class CompileLogActivity extends BaseAppCompatActivity {

    private static final String PREFERENCE_WRAPPED_TEXT = "wrapped_text";
    private static final String PREFERENCE_USE_MONOSPACED_FONT = "use_monospaced_font";
    private static final String PREFERENCE_FONT_SIZE = "font_size";
    private CompileErrorSaver compileErrorSaver;
    private SharedPreferences logViewerPreferences;
    private String fullErrorLogs;

    private CompileLogBinding binding;

    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = CompileLogBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.optionsLayout,
                new AddMarginOnApplyWindowInsetsListener(WindowInsetsCompat.Type.navigationBars(), WindowInsetsCompat.CONSUMED));

        logViewerPreferences = getPreferences(Context.MODE_PRIVATE);

        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));

        if (getIntent().getBooleanExtra("showingLastError", false)) {
            binding.topAppBar.setTitle("Last compile log");
        } else {
            binding.topAppBar.setTitle("Compile log");
        }

        String sc_id = getIntent().getStringExtra("sc_id");
        if (sc_id == null) {
            finish();
            return;
        }

        compileErrorSaver = new CompileErrorSaver(sc_id);

        if (compileErrorSaver.logFileExists()) {
            binding.clearButton.setOnClickListener(v -> {
                HapticManager.vibrateRun(v);
                if (compileErrorSaver.logFileExists()) {
                    compileErrorSaver.deleteSavedLogs();
                    getIntent().removeExtra("error");
                    fullErrorLogs = null;
                    SketchwareUtil.toast("Compile logs have been cleared.");
                } else {
                    SketchwareUtil.toast("No compile logs found.");
                }

                setErrorText();
            });
        }

        if (AiClient.isAiEnabled(this)) {
            binding.skExplainButton.setVisibility(View.VISIBLE);
            binding.skExplainButton.setOnClickListener(v -> {
                HapticManager.vibrateRun(v);
                explainCurrentTab();
            });
        } else {
            binding.skExplainButton.setVisibility(View.GONE);
        }

        final String wrapTextLabel = "Wrap text";
        final String monospacedFontLabel = "Monospaced font";
        final String fontSizeLabel = "Font size";

        PopupMenu options = new PopupMenu(this, binding.formatButton);
        options.getMenu().add(wrapTextLabel).setCheckable(true).setChecked(getWrappedTextPreference());
        options.getMenu().add(monospacedFontLabel).setCheckable(true).setChecked(getMonospacedFontPreference());
        options.getMenu().add(fontSizeLabel);

        options.setOnMenuItemClickListener(menuItem -> {
            switch (menuItem.getTitle().toString()) {
                case wrapTextLabel -> {
                    menuItem.setChecked(!menuItem.isChecked());
                    toggleWrapText(menuItem.isChecked());
                }
                case monospacedFontLabel -> {
                    menuItem.setChecked(!menuItem.isChecked());
                    toggleMonospacedText(menuItem.isChecked());
                }
                case fontSizeLabel -> changeFontSizeDialog();
                default -> {
                    return false;
                }
            }

            return true;
        });

        binding.formatButton.setOnClickListener(v -> {
            HapticManager.vibrateRun(v);
            options.show();
        });

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("All"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Java / Kotlin"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("XML / Layout"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Dex / R8"));

        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                updateFilteredLogText();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        applyLogViewerPreferences();

        setErrorText();
    }

    private void setErrorText() {
        String error = getIntent().getStringExtra("error");
        if (error == null) error = compileErrorSaver.getLogsFromFile();
        fullErrorLogs = error;

        if (error == null || error.isEmpty()) {
            binding.noContentLayout.setVisibility(View.VISIBLE);
            binding.optionsLayout.setVisibility(View.GONE);
            binding.tabLayout.setVisibility(View.GONE);
            binding.errVScroll.setVisibility(View.GONE);
            return;
        }

        binding.optionsLayout.setVisibility(View.VISIBLE);
        binding.noContentLayout.setVisibility(View.GONE);
        binding.tabLayout.setVisibility(View.VISIBLE);
        binding.errVScroll.setVisibility(View.VISIBLE);

        updateFilteredLogText();
    }

    private void updateFilteredLogText() {
        if (fullErrorLogs == null) return;
        int currentTab = binding.tabLayout.getSelectedTabPosition();
        String filtered = CompileLogHelper.filterLogs(fullErrorLogs, Math.max(0, currentTab));
        binding.tvCompileLog.setText(CompileLogHelper.getColoredLogs(this, filtered));
        binding.tvCompileLog.setTextIsSelectable(true);
    }

    private void explainCurrentTab() {
        if (fullErrorLogs == null || fullErrorLogs.isEmpty()) {
            SketchwareUtil.toast("No errors to explain");
            return;
        }
        int currentTab = binding.tabLayout.getSelectedTabPosition();
        String currentTabText = CompileLogHelper.filterLogs(fullErrorLogs, Math.max(0, currentTab));
        if (currentTabText == null || currentTabText.isEmpty() || currentTabText.startsWith("No entries found")) {
            SketchwareUtil.toast("No errors found in this tab");
            return;
        }
        explainErrorsWithSk(currentTabText);
    }

    private void applyLogViewerPreferences() {
        toggleWrapText(getWrappedTextPreference());
        toggleMonospacedText(getMonospacedFontPreference());
        binding.tvCompileLog.setTextSize(getFontSizePreference());
    }

    private boolean getWrappedTextPreference() {
        return logViewerPreferences.getBoolean(PREFERENCE_WRAPPED_TEXT, false);
    }

    private boolean getMonospacedFontPreference() {
        return logViewerPreferences.getBoolean(PREFERENCE_USE_MONOSPACED_FONT, true);
    }

    private int getFontSizePreference() {
        return logViewerPreferences.getInt(PREFERENCE_FONT_SIZE, 11);
    }

    private void toggleWrapText(boolean isChecked) {
        logViewerPreferences.edit().putBoolean(PREFERENCE_WRAPPED_TEXT, isChecked).apply();

        if (isChecked) {
            binding.errVScroll.removeAllViews();
            if (binding.tvCompileLog.getParent() != null) {
                ((ViewGroup) binding.tvCompileLog.getParent()).removeView(binding.tvCompileLog);
            }
            binding.errVScroll.addView(binding.tvCompileLog);
        } else {
            binding.errVScroll.removeAllViews();
            if (binding.tvCompileLog.getParent() != null) {
                ((ViewGroup) binding.tvCompileLog.getParent()).removeView(binding.tvCompileLog);
            }
            binding.errHScroll.removeAllViews();
            binding.errHScroll.addView(binding.tvCompileLog);
            binding.errVScroll.addView(binding.errHScroll);
        }
    }

    private void toggleMonospacedText(boolean isChecked) {
        logViewerPreferences.edit().putBoolean(PREFERENCE_USE_MONOSPACED_FONT, isChecked).apply();

        if (isChecked) {
            binding.tvCompileLog.setTypeface(Typeface.MONOSPACE);
        } else {
            binding.tvCompileLog.setTypeface(Typeface.DEFAULT);
        }
    }

    private void changeFontSizeDialog() {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(10);
        picker.setMaxValue(70);
        picker.setWrapSelectorWheel(false);
        picker.setValue(getFontSizePreference());

        LinearLayout layout = new LinearLayout(this);
        layout.addView(picker, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select font size")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    logViewerPreferences.edit().putInt(PREFERENCE_FONT_SIZE, picker.getValue()).apply();

                    binding.tvCompileLog.setTextSize((float) picker.getValue());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public void explainErrorsWithSk(String errorLogs) {
        k();
        SketchwareUtil.toast("Asking SK to explain errors...");
        String systemPrompt = "You are an expert Android developer specializing in Sketchware Plus. The user is using Sketchware Plus, a mobile IDE, which does not use a traditional Gradle/Groovy build system for its project configuration. Explain the following compilation errors clearly and provide specific instructions on how to fix them within the context of Sketchware (e.g., checking blocks, custom code, or local libraries). Avoid suggestions related to editing build.gradle or standard Android Studio IDE settings.";
        String userPrompt = "Compilation logs:\n" + errorLogs;

        AiClient.askAi(this, systemPrompt, userPrompt, AiClient.AiTemperatureType.ERROR_EXPLANATION, new AiClient.AiCallback() {
            @Override
            public void onSuccess(String response) {
                runOnUiThread(() -> {
                    h();
                    showSkExplanationDialog(response);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    h();
                    String lower = error != null ? error.toLowerCase() : "";
                    if (lower.contains("too large") || lower.contains("token") || lower.contains("413") || lower.contains("exceed") || lower.contains("payload")) {
                        new MaterialAlertDialogBuilder(CompileLogActivity.this)
                                .setTitle("Log Too Large")
                                .setMessage("The compile log is too large for the AI model to process at once. Please switch to a specific category tab (such as Java / Kotlin or XML / Layout) to narrow down the errors, and try again.")
                                .setPositiveButton("Got it", null)
                                .show();
                    } else {
                        SketchwareUtil.toastError(error);
                    }
                });
            }

            @Override
            public void onRetry(int retryCount, long delayMillis) {
                runOnUiThread(() -> {
                    SketchwareUtil.toast("Rate limit hit. Retrying in " + (delayMillis / 1000) + "s...");
                });
            }
        });
    }

    @SuppressLint("RestrictedApi")
    private void showSkExplanationDialog(String explanation) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("SKError Explanation")
                .setMessage(explanation)
                .setPositiveButton("Dismiss", null)
                .show();
    }
}
