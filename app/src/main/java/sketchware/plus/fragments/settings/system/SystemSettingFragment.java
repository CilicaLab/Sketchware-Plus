package sketchware.plus.fragments.settings.system;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import a.a.a.qA;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import sketchware.plus.R;
import sketchware.plus.databinding.DialogCreateNewFileLayoutBinding;
import sketchware.plus.databinding.FragmentSystemSettingsBinding;
import sketchware.plus.utility.HapticManager;

public class SystemSettingFragment extends qA {

    private static final String PREFS_NAME = "P12";
    private FragmentSystemSettingsBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSystemSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        checkAndMigratePreferences();
        initializeSettings();
        setupClickListeners();
    }

    private SharedPreferences getPrefs() {
        return requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private void checkAndMigratePreferences() {
        SharedPreferences prefs = getPrefs();
        String[] aiKeys = {"P12I7", "P12I8", "P12I9"};
        for (String key : aiKeys) {
            if (prefs.contains(key)) {
                try {
                    prefs.getString(key, "");
                } catch (ClassCastException e) {
                    int oldVal = prefs.getInt(key, 20);
                    prefs.edit().putString(key, String.valueOf(oldVal)).apply();
                }
            }
        }
    }

    private void initializeSettings() {
        SharedPreferences prefs = getPrefs();

        // Switches
        binding.switchVibration.setChecked(prefs.getBoolean("P12I0", true));
        binding.switchAutoSave.setChecked(prefs.getBoolean("P12I2", false));
        binding.switchBuildDetails.setChecked(prefs.getBoolean("P12I6", false));
        binding.switchAiMarkdown.setChecked(prefs.getBoolean("P12_MARKDOWN_ENABLED", true));

        // Summaries
        updateAiProviderSummary();
        updateEditTextSummary("P12I3", binding.tvAiApiKeySummary);
        updateEditTextSummary("P12I4", binding.tvAiEndpointUrlSummary);
        updateEditTextSummary("P12I5", binding.tvAiModelNameSummary);

        updateAiFocusSummary("P12I7", binding.tvAiErrorExplanationTempSummary);
        updateAiFocusSummary("P12I8", binding.tvAiCodeGenerationTempSummary);
        updateAiFocusSummary("P12I9", binding.tvAiAssistantModeTempSummary);
    }

    private void setupClickListeners() {
        // Vibration Switch
        binding.cardVibration.setOnClickListener(v -> binding.switchVibration.setChecked(!binding.switchVibration.isChecked()));
        binding.switchVibration.setOnCheckedChangeListener((buttonView, isChecked) -> {
            HapticManager.vibrateSwitch(buttonView, isChecked);
            getPrefs().edit().putBoolean("P12I0", isChecked).apply();
        });

        // Auto Save Switch
        binding.cardAutoSave.setOnClickListener(v -> binding.switchAutoSave.setChecked(!binding.switchAutoSave.isChecked()));
        binding.switchAutoSave.setOnCheckedChangeListener((buttonView, isChecked) -> {
            HapticManager.vibrateSwitch(buttonView, isChecked);
            getPrefs().edit().putBoolean("P12I2", isChecked).apply();
        });

        // Show Build Details Switch
        binding.cardBuildDetails.setOnClickListener(v -> binding.switchBuildDetails.setChecked(!binding.switchBuildDetails.isChecked()));
        binding.switchBuildDetails.setOnCheckedChangeListener((buttonView, isChecked) -> {
            HapticManager.vibrateSwitch(buttonView, isChecked);
            getPrefs().edit().putBoolean("P12I6", isChecked).apply();
        });

        // AI Markdown Switch
        binding.cardAiMarkdown.setOnClickListener(v -> binding.switchAiMarkdown.setChecked(!binding.switchAiMarkdown.isChecked()));
        binding.switchAiMarkdown.setOnCheckedChangeListener((buttonView, isChecked) -> {
            HapticManager.vibrateSwitch(buttonView, isChecked);
            getPrefs().edit().putBoolean("P12_MARKDOWN_ENABLED", isChecked).apply();
        });

        // AI Provider Card
        binding.cardAiProvider.setOnClickListener(v -> showAiProviderDialog());

        // AI API Key Card
        binding.cardAiApiKey.setOnClickListener(v -> showEditTextDialog("P12I3", "AI API Key", binding.tvAiApiKeySummary));

        // AI Endpoint URL Card
        binding.cardAiEndpointUrl.setOnClickListener(v -> showEditTextDialog("P12I4", "AI Endpoint URL", binding.tvAiEndpointUrlSummary));

        // AI Model Name Card
        binding.cardAiModelName.setOnClickListener(v -> handleAiModelNameClick());

        // Fetch Models Card
        binding.cardAiFetchModels.setOnClickListener(v -> fetchAiModels());

        // Temperature Cards
        binding.cardAiErrorExplanationTemp.setOnClickListener(v ->
                showAiFocusDialog("P12I7", "SK Error Explanation Temperature", binding.tvAiErrorExplanationTempSummary));
        binding.cardAiCodeGenerationTemp.setOnClickListener(v ->
                showAiFocusDialog("P12I8", "SK Code Generation Temperature", binding.tvAiCodeGenerationTempSummary));
        binding.cardAiAssistantModeTemp.setOnClickListener(v ->
                showAiFocusDialog("P12I9", "SK Assistant Mode Temperature", binding.tvAiAssistantModeTempSummary));
    }

    private void showAiProviderDialog() {
        String currentProvider = getPrefs().getString("P12_PROVIDER", "custom");

        String[] items = new String[]{
                "Groq (Recommended)",
                "Google AI (Gemini)",
                "Custom (OpenAI Compatible)"
        };
        String[] values = new String[]{"groq", "google", "custom"};

        int checkedItem = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equalsIgnoreCase(currentProvider)) {
                checkedItem = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select AI Provider")
                .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                    String selectedProvider = values[which];
                    SharedPreferences sp = getPrefs();
                    sp.edit().putString("P12_PROVIDER", selectedProvider).apply();
                    updateAiProviderSummary();

                    if ("google".equalsIgnoreCase(selectedProvider)) {
                        sp.edit().putString("P12I4", "https://generativelanguage.googleapis.com/v1beta/openai/").apply();
                    } else if ("groq".equalsIgnoreCase(selectedProvider) || "grog".equalsIgnoreCase(selectedProvider)) {
                        sp.edit().putString("P12I4", "https://api.groq.com/openai/v1").apply();
                        String currentModel = sp.getString("P12I5", "");
                        if (currentModel.isEmpty()) {
                            sp.edit().putString("P12I5", "openai/gpt-oss-20b").apply();
                        }
                    }

                    updateEditTextSummary("P12I4", binding.tvAiEndpointUrlSummary);
                    updateEditTextSummary("P12I5", binding.tvAiModelNameSummary);

                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateAiProviderSummary() {
        String provider = getPrefs().getString("P12_PROVIDER", "custom");
        if ("groq".equalsIgnoreCase(provider) || "grog".equalsIgnoreCase(provider)) {
            binding.tvAiProviderSummary.setText("Groq (Recommended)");
        } else if ("google".equalsIgnoreCase(provider)) {
            binding.tvAiProviderSummary.setText("Google AI (Gemini)");
        } else {
            binding.tvAiProviderSummary.setText("Custom (OpenAI Compatible)");
        }
    }

    private void handleAiModelNameClick() {
        String provider = getPrefs().getString("P12_PROVIDER", "custom");
        String apiKey = getPrefs().getString("P12I3", "");
        if ("google".equalsIgnoreCase(provider) || apiKey.startsWith("AIzaSy")) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("AI Model Selection")
                    .setItems(new String[]{"Fetch Available Models from Google AI", "Type Custom Model Name"}, (dialog, which) -> {
                        if (which == 0) {
                            fetchAiModels();
                        } else {
                            showEditTextDialog("P12I5", "AI Model Name", binding.tvAiModelNameSummary);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            showEditTextDialog("P12I5", "AI Model Name", binding.tvAiModelNameSummary);
        }
    }

    private void updateEditTextSummary(String key, TextView summaryView) {
        String value = getPrefs().getString(key, "");
        if (value.isEmpty()) {
            summaryView.setText("Not set");
        } else {
            if ("P12I3".equals(key)) {
                if (value.length() > 8) {
                    summaryView.setText(value.substring(0, 4) + "...." + value.substring(value.length() - 4));
                } else {
                    summaryView.setText("********");
                }
            } else {
                summaryView.setText(value);
            }
        }
    }

    private void showEditTextDialog(String key, String title, TextView summaryView) {
        DialogCreateNewFileLayoutBinding dialogBinding = DialogCreateNewFileLayoutBinding.inflate(getLayoutInflater());
        dialogBinding.chipGroupTypes.setVisibility(View.GONE);
        dialogBinding.textInputLayout.setHint(title);

        if ("P12I3".equals(key)) {
            dialogBinding.inputText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }

        String currentValue = getPrefs().getString(key, "");
        dialogBinding.inputText.setText(currentValue);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Save", (dialog, which) -> {
                    if (dialogBinding.inputText.getText() != null) {
                        String newValue = dialogBinding.inputText.getText().toString().trim();
                        getPrefs().edit().putString(key, newValue).apply();
                        updateEditTextSummary(key, summaryView);

                        if ("P12I3".equals(key) && !newValue.isEmpty()) {
                            String provider = getPrefs().getString("P12_PROVIDER", "custom");
                            if ("google".equalsIgnoreCase(provider) || newValue.startsWith("AIzaSy")) {
                                new MaterialAlertDialogBuilder(requireContext())
                                        .setTitle("Fetch Google AI Models")
                                        .setMessage("API Key saved! Would you like to fetch available Gemini models now?")
                                        .setPositiveButton("Fetch Models", (d, w) -> fetchGoogleAiModels(newValue))
                                        .setNegativeButton("Later", null)
                                        .show();
                            }
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateAiFocusSummary(String key, TextView summaryView) {
        String value = getPrefs().getString(key, "20");
        String[] entries = getResources().getStringArray(R.array.ai_focus_entries);
        String[] values = getResources().getStringArray(R.array.ai_focus_values);

        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                summaryView.setText(entries[i]);
                return;
            }
        }
        summaryView.setText("Default");
    }

    private void showAiFocusDialog(String key, String title, TextView summaryView) {
        String[] entries = getResources().getStringArray(R.array.ai_focus_entries);
        String[] descriptions = getResources().getStringArray(R.array.ai_focus_descriptions);
        String[] values = getResources().getStringArray(R.array.ai_focus_values);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(), R.layout.item_ai_focus_selection, R.id.title, entries) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView desc = view.findViewById(R.id.description);
                desc.setText(descriptions[position]);
                return view;
            }
        };

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setAdapter(adapter, (dialog, which) -> {
                    getPrefs().edit().putString(key, values[which]).apply();
                    updateAiFocusSummary(key, summaryView);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void fetchAiModels() {
        SharedPreferences prefs = getPrefs();
        String apiKey = prefs.getString("P12I3", "").trim();
        String endpoint = prefs.getString("P12I4", "").trim();
        String provider = prefs.getString("P12_PROVIDER", "custom");

        if (apiKey.isEmpty()) {
            Toast.makeText(requireContext(), "Please set your AI API Key first", Toast.LENGTH_SHORT).show();
            return;
        }

        if ("google".equalsIgnoreCase(provider) || apiKey.startsWith("AIzaSy")) {
            fetchGoogleAiModels(apiKey);
        } else {
            fetchCustomOpenAiModels(apiKey, endpoint);
        }
    }

    private void fetchGoogleAiModels(String apiKey) {
        ProgressDialog progressDialog = new ProgressDialog(requireContext());
        progressDialog.setMessage("Fetching available Gemini models...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/openai/models")
                .addHeader("Authorization", "Bearer " + apiKey)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                fetchGoogleAiModelsFallback(apiKey, progressDialog);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!response.isSuccessful() || response.body() == null) {
                    fetchGoogleAiModelsFallback(apiKey, progressDialog);
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);
                    JSONArray data = json.optJSONArray("data");

                    List<String> models = new ArrayList<>();
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject modelObj = data.getJSONObject(i);
                            String id = modelObj.optString("id");
                            if (id.contains("gemini")) {
                                models.add(id);
                            }
                        }
                    }

                    if (models.isEmpty()) {
                        fetchGoogleAiModelsFallback(apiKey, progressDialog);
                        return;
                    }

                    Collections.sort(models);
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            progressDialog.dismiss();
                            showModelPickerDialog(models);
                        });
                    }
                } catch (Exception e) {
                    fetchGoogleAiModelsFallback(apiKey, progressDialog);
                }
            }
        });
    }

    private void fetchGoogleAiModelsFallback(String apiKey, ProgressDialog progressDialog) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(requireContext(), "Failed to fetch models: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (response.body() == null) {
                        if (isAdded()) {
                            requireActivity().runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(requireContext(), "Empty response from server", Toast.LENGTH_SHORT).show();
                            });
                        }
                        return;
                    }

                    String responseBody = response.body().string();
                    if (!response.isSuccessful()) {
                        String errorMsg = "API request failed (" + response.code() + ")";
                        try {
                            JSONObject errorJson = new JSONObject(responseBody);
                            if (errorJson.has("error")) {
                                errorMsg = errorJson.getJSONObject("error").optString("message", errorMsg);
                            }
                        } catch (Exception ignored) {
                        }
                        String finalErrorMsg = errorMsg;
                        if (isAdded()) {
                            requireActivity().runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(requireContext(), finalErrorMsg, Toast.LENGTH_LONG).show();
                            });
                        }
                        return;
                    }

                    JSONObject json = new JSONObject(responseBody);
                    JSONArray modelsArray = json.optJSONArray("models");
                    List<String> models = new ArrayList<>();

                    if (modelsArray != null) {
                        for (int i = 0; i < modelsArray.length(); i++) {
                            JSONObject m = modelsArray.getJSONObject(i);
                            String name = m.optString("name");
                            JSONArray methods = m.optJSONArray("supportedGenerationMethods");

                            boolean supportsGenerate = false;
                            if (methods != null) {
                                for (int j = 0; j < methods.length(); j++) {
                                    if ("generateContent".equals(methods.getString(j))) {
                                        supportsGenerate = true;
                                        break;
                                    }
                                }
                            }

                            if (supportsGenerate) {
                                if (name.startsWith("models/")) {
                                    name = name.substring(7);
                                }
                                models.add(name);
                            }
                        }
                    }

                    Collections.sort(models);
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            progressDialog.dismiss();
                            if (models.isEmpty()) {
                                Toast.makeText(requireContext(), "No compatible Gemini models found", Toast.LENGTH_SHORT).show();
                            } else {
                                showModelPickerDialog(models);
                            }
                        });
                    }
                } catch (Exception e) {
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(requireContext(), "Error parsing models: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            }
        });
    }

    private void fetchCustomOpenAiModels(String apiKey, String endpoint) {
        if (endpoint.isEmpty()) {
            Toast.makeText(requireContext(), "Please set your AI Endpoint URL first", Toast.LENGTH_SHORT).show();
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(requireContext());
        progressDialog.setMessage("Fetching available models...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        String url = endpoint;
        if (!url.endsWith("/")) {
            url += "/";
        }
        url += "models";

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        Request.Builder builder = new Request.Builder().url(url).get();
        if (!apiKey.isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + apiKey);
        }

        client.newCall(builder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(requireContext(), "Failed to fetch models: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (response.body() == null) {
                        if (isAdded()) {
                            requireActivity().runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(requireContext(), "Empty response from server", Toast.LENGTH_SHORT).show();
                            });
                        }
                        return;
                    }
                    String body = response.body().string();
                    if (!response.isSuccessful()) {
                        if (isAdded()) {
                            requireActivity().runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(requireContext(), "Failed to fetch models: HTTP " + response.code(), Toast.LENGTH_SHORT).show();
                            });
                        }
                        return;
                    }

                    JSONObject json = new JSONObject(body);
                    JSONArray data = json.optJSONArray("data");

                    List<String> models = new ArrayList<>();
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject m = data.getJSONObject(i);
                            String id = m.optString("id");
                            if (!id.isEmpty()) {
                                models.add(id);
                            }
                        }
                    }

                    Collections.sort(models);
                    List<String> finalModels = models;
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            progressDialog.dismiss();
                            if (finalModels.isEmpty()) {
                                Toast.makeText(requireContext(), "No models returned by server", Toast.LENGTH_SHORT).show();
                            } else {
                                showModelPickerDialog(finalModels);
                            }
                        });
                    }
                } catch (Exception e) {
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(requireContext(), "Error parsing response: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            }
        });
    }

    private void showModelPickerDialog(List<String> models) {
        if (models == null || models.isEmpty() || !isAdded()) return;

        String[] items = models.toArray(new String[0]);
        String currentModel = getPrefs().getString("P12I5", "");
        int checkedItem = -1;
        for (int i = 0; i < items.length; i++) {
            if (items[i].equalsIgnoreCase(currentModel)) {
                checkedItem = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select AI Model")
                .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                    String selectedModel = items[which];
                    getPrefs().edit().putString("P12I5", selectedModel).apply();
                    updateEditTextSummary("P12I5", binding.tvAiModelNameSummary);
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
