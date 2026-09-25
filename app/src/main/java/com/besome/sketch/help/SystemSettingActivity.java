package com.besome.sketch.help;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import mod.hey.studios.util.Helper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import sketchware.plus.R;
import sketchware.plus.databinding.DialogCreateNewFileLayoutBinding;
import sketchware.plus.databinding.PreferenceActivityBinding;

public class SystemSettingActivity extends BaseAppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        var binding = PreferenceActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.topAppBar.setTitle(R.string.main_drawer_title_system_settings);
        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        getSupportFragmentManager().beginTransaction()
                .replace(binding.fragmentContainer.getId(), new PreferenceFragment())
                .commit();

        {
            View view1 = binding.appBarLayout;
            int left = view1.getPaddingLeft();
            int top = view1.getPaddingTop();
            int right = view1.getPaddingRight();
            int bottom = view1.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(view1, (v, i) -> {
                Insets insets = i.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(left + insets.left, top + insets.top, right + insets.right, bottom);
                return i;
            });
        }

        {
            View view1 = binding.fragmentContainer;
            int left = view1.getPaddingLeft();
            int top = view1.getPaddingTop();
            int right = view1.getPaddingRight();
            int bottom = view1.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(view1, (v, i) -> {
                Insets insets = i.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(left + insets.left, top, right + insets.right, bottom + insets.bottom);
                return i;
            });
        }
    }

    public static class PreferenceFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            getPreferenceManager().setSharedPreferencesName("P12");
            var prefs = getPreferenceManager().getSharedPreferences();

            if (prefs != null) {
                String[] aiKeys = {"P12I7", "P12I8", "P12I9"};
                for (String key : aiKeys) {
                    if (prefs.contains(key)) {
                        try {
                            // Try to get as string, if it fails it might be the old int
                            prefs.getString(key, "");
                        } catch (ClassCastException e) {
                            int oldVal = prefs.getInt(key, 20);
                            prefs.edit().putString(key, String.valueOf(oldVal)).apply();
                        }
                    }
                }
            }
            setPreferencesFromResource(R.xml.preferences_system_settings, rootKey);

            setupAiProviderPreference("P12_PROVIDER");
            setupAiFocusPreference("P12I7");
            setupAiFocusPreference("P12I8");
            setupAiFocusPreference("P12I9");

            setupEditTextPreference("P12I3", "AI API Key");
            setupEditTextPreference("P12I4", "AI Endpoint URL");
            setupEditTextPreference("P12I5", "AI Model Name");

            Preference fetchPref = findPreference("P12_FETCH_MODELS");
            if (fetchPref != null) {
                fetchPref.setOnPreferenceClickListener(p -> {
                    fetchAiModels();
                    return true;
                });
            }
        }

        private void setupAiProviderPreference(String key) {
            ListPreference pref = findPreference(key);
            if (pref == null) return;

            pref.setOnPreferenceChangeListener((preference, newValue) -> {
                String provider = (String) newValue;
                var sp = getPreferenceManager().getSharedPreferences();
                if (sp != null) {
                    if ("google".equalsIgnoreCase(provider)) {
                        sp.edit()
                                .putString("P12I4", "https://generativelanguage.googleapis.com/v1beta/openai/")
                                .apply();
                        Preference endpointPref = findPreference("P12I4");
                        if (endpointPref != null) {
                            updateEditTextSummary(endpointPref);
                        }
                    }
                }
                return true;
            });
        }

        private void setupEditTextPreference(String key, String title) {
            Preference pref = findPreference(key);
            if (pref == null) return;

            updateEditTextSummary(pref);
            pref.setOnPreferenceClickListener(p -> {
                if (key.equals("P12I5")) {
                    var sp = getPreferenceManager().getSharedPreferences();
                    String provider = sp != null ? sp.getString("P12_PROVIDER", "custom") : "custom";
                    String apiKey = sp != null ? sp.getString("P12I3", "") : "";
                    if ("google".equalsIgnoreCase(provider) || apiKey.startsWith("AIzaSy")) {
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle("AI Model Selection")
                                .setItems(new String[]{"Fetch Available Models from Google AI", "Type Custom Model Name"}, (dialog, which) -> {
                                    if (which == 0) {
                                        fetchAiModels();
                                    } else {
                                        showEditTextDialog(pref, title);
                                    }
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                        return true;
                    }
                }
                showEditTextDialog(pref, title);
                return true;
            });
        }

        private void updateEditTextSummary(Preference pref) {
            if (getPreferenceManager().getSharedPreferences() == null) return;
            String value = getPreferenceManager().getSharedPreferences().getString(pref.getKey(), "");
            if (value.isEmpty()) {
                pref.setSummary("Not set");
            } else {
                if (pref.getKey().equals("P12I3")) {
                    // Mask API key
                    if (value.length() > 8) {
                        pref.setSummary(value.substring(0, 4) + "...." + value.substring(value.length() - 4));
                    } else {
                        pref.setSummary("********");
                    }
                } else {
                    pref.setSummary(value);
                }
            }
        }

        private void showEditTextDialog(Preference pref, String title) {
            var binding = DialogCreateNewFileLayoutBinding.inflate(getLayoutInflater());
            binding.chipGroupTypes.setVisibility(View.GONE);
            binding.textInputLayout.setHint(title);
            
            if (pref.getKey().equals("P12I3")) {
                binding.inputText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }

            var prefs = getPreferenceManager().getSharedPreferences();
            if (prefs != null) {
                String currentValue = prefs.getString(pref.getKey(), "");
                binding.inputText.setText(currentValue);
            }

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(title)
                    .setView(binding.getRoot())
                    .setPositiveButton("Save", (dialog, which) -> {
                        if (binding.inputText.getText() != null) {
                            String newValue = binding.inputText.getText().toString().trim();
                            var sp = getPreferenceManager().getSharedPreferences();
                            if (sp != null) {
                                sp.edit().putString(pref.getKey(), newValue).apply();
                                updateEditTextSummary(pref);

                                if (pref.getKey().equals("P12I3") && !newValue.isEmpty()) {
                                    String provider = sp.getString("P12_PROVIDER", "custom");
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
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        private void setupAiFocusPreference(String key) {
            Preference pref = findPreference(key);
            if (pref == null) return;

            updateAiFocusSummary(pref);
            pref.setOnPreferenceClickListener(p -> {
                showAiFocusDialog(pref);
                return true;
            });
        }

        private void updateAiFocusSummary(Preference pref) {
            if (getPreferenceManager().getSharedPreferences() == null) return;
            String value = getPreferenceManager().getSharedPreferences().getString(pref.getKey(), "20");
            String[] entries = getResources().getStringArray(R.array.ai_focus_entries);
            String[] values = getResources().getStringArray(R.array.ai_focus_values);

            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(value)) {
                    pref.setSummary(entries[i]);
                    return;
                }
            }
        }

        private void showAiFocusDialog(Preference pref) {
            String[] entries = getResources().getStringArray(R.array.ai_focus_entries);
            String[] descriptions = getResources().getStringArray(R.array.ai_focus_descriptions);
            String[] values = getResources().getStringArray(R.array.ai_focus_values);

            var adapter = new ArrayAdapter<String>(requireContext(), R.layout.item_ai_focus_selection, R.id.title, entries) {
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
                    .setTitle("Model Thinking Temperature")
                    .setAdapter(adapter, (dialog, which) -> {
                        if (getPreferenceManager().getSharedPreferences() != null) {
                            getPreferenceManager().getSharedPreferences().edit()
                                    .putString(pref.getKey(), values[which])
                                    .apply();
                            updateAiFocusSummary(pref);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        private void fetchAiModels() {
            var prefs = getPreferenceManager().getSharedPreferences();
            if (prefs == null) return;

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
                            } catch (Exception ignored) {}
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
            Preference modelPref = findPreference("P12I5");

            var prefs = getPreferenceManager().getSharedPreferences();
            String currentModel = prefs != null ? prefs.getString("P12I5", "") : "";
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
                        if (getPreferenceManager().getSharedPreferences() != null) {
                            getPreferenceManager().getSharedPreferences().edit()
                                    .putString("P12I5", selectedModel)
                                    .apply();
                            if (modelPref != null) {
                                updateEditTextSummary(modelPref);
                            }
                        }
                        dialog.dismiss();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }
    }
}
