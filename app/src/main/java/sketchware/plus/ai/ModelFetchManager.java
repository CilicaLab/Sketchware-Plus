package sketchware.plus.ai;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import sketchware.plus.databinding.DialogCreateNewFileLayoutBinding;

public class ModelFetchManager {

    private final SkAssistantFragment fragment;

    public ModelFetchManager(SkAssistantFragment fragment) {
        this.fragment = fragment;
    }

    public void showQuickModelSelectorDialog() {
        Context context = fragment.getContext();
        if (context == null) return;
        SharedPreferences aiPref = context.getSharedPreferences("P12", Context.MODE_PRIVATE);
        String apiKey = aiPref.getString("P12I3", "");
        String provider = aiPref.getString("P12_PROVIDER", "custom");

        if (apiKey.isEmpty()) {
            Toast.makeText(context, "Please set your AI API Key in System Settings first", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean isGoogle = "google".equalsIgnoreCase(provider) || apiKey.startsWith("AIzaSy");
        String title = isGoogle ? "Google AI Model" : "AI Model Selection";

        new MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(title)
                .setItems(new String[]{"Fetch Available Models", "Type Custom Model Name"}, (dialog, which) -> {
                    if (which == 0) {
                        if (isGoogle) {
                            fetchAndPickGoogleModels(apiKey);
                        } else {
                            String endpoint = aiPref.getString("P12I4", "");
                            fetchAndPickCustomModels(apiKey, endpoint);
                        }
                    } else {
                        showCustomModelInputDialog();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public void showCustomModelInputDialog() {
        Context context = fragment.getContext();
        if (context == null) return;
        SharedPreferences aiPref = context.getSharedPreferences("P12", Context.MODE_PRIVATE);
        String currentModel = aiPref.getString("P12I5", "");

        var binding = DialogCreateNewFileLayoutBinding.inflate(fragment.getLayoutInflater());
        binding.chipGroupTypes.setVisibility(View.GONE);
        binding.textInputLayout.setHint("AI Model Name");
        binding.inputText.setText(currentModel);

        new MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle("Enter AI Model Name")
                .setView(binding.getRoot())
                .setPositiveButton("Save", (dialog, which) -> {
                    if (binding.inputText.getText() != null) {
                        String newModel = binding.inputText.getText().toString().trim();
                        aiPref.edit().putString("P12I5", newModel).apply();
                        fragment.updateCurrentModelBadge();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public void fetchAndPickGoogleModels(String apiKey) {
        if (fragment.getContext() == null) return;
        ProgressDialog progressDialog = new ProgressDialog(fragment.requireContext());
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
                fetchAndPickGoogleModelsFallback(apiKey, progressDialog);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!response.isSuccessful() || response.body() == null) {
                    fetchAndPickGoogleModelsFallback(apiKey, progressDialog);
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
                        fetchAndPickGoogleModelsFallback(apiKey, progressDialog);
                        return;
                    }

                    Collections.sort(models);
                    if (fragment.isAdded()) {
                        fragment.requireActivity().runOnUiThread(() -> {
                            progressDialog.dismiss();
                            showModelPickerDialog(models);
                        });
                    }
                } catch (Exception e) {
                    fetchAndPickGoogleModelsFallback(apiKey, progressDialog);
                }
            }
        });
    }

    public void fetchAndPickGoogleModelsFallback(String apiKey, ProgressDialog progressDialog) {
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
                if (fragment.isAdded()) {
                    fragment.requireActivity().runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(fragment.requireContext(), "Failed to fetch models: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (response.body() == null) {
                        if (fragment.isAdded()) {
                            fragment.requireActivity().runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(fragment.requireContext(), "Empty response from server", Toast.LENGTH_SHORT).show();
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
                        if (fragment.isAdded()) {
                            fragment.requireActivity().runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(fragment.requireContext(), finalErrorMsg, Toast.LENGTH_LONG).show();
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
                    if (fragment.isAdded()) {
                        fragment.requireActivity().runOnUiThread(() -> {
                            progressDialog.dismiss();
                            if (models.isEmpty()) {
                                Toast.makeText(fragment.requireContext(), "No compatible Gemini models found", Toast.LENGTH_SHORT).show();
                            } else {
                                showModelPickerDialog(models);
                            }
                        });
                    }
                } catch (Exception e) {
                    if (fragment.isAdded()) {
                        fragment.requireActivity().runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(fragment.requireContext(), "Error parsing models: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            }
        });
    }

    public void fetchAndPickCustomModels(String apiKey, String endpoint) {
        if (endpoint.isEmpty()) {
            Toast.makeText(fragment.requireContext(), "Please set your AI Endpoint URL in System Settings first", Toast.LENGTH_SHORT).show();
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(fragment.requireContext());
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
                if (fragment.isAdded()) {
                    fragment.requireActivity().runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(fragment.requireContext(), "Failed to fetch models: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (response.body() == null) {
                        if (fragment.isAdded()) {
                            fragment.requireActivity().runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(fragment.requireContext(), "Empty response from server", Toast.LENGTH_SHORT).show();
                            });
                        }
                        return;
                    }

                    String body = response.body().string();
                    if (!response.isSuccessful()) {
                        if (fragment.isAdded()) {
                            fragment.requireActivity().runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(fragment.requireContext(), "Failed to fetch models: HTTP " + response.code(), Toast.LENGTH_SHORT).show();
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
                    if (fragment.isAdded()) {
                        fragment.requireActivity().runOnUiThread(() -> {
                            progressDialog.dismiss();
                            if (models.isEmpty()) {
                                Toast.makeText(fragment.requireContext(), "No models returned by server", Toast.LENGTH_SHORT).show();
                            } else {
                                showModelPickerDialog(models);
                            }
                        });
                    }
                } catch (Exception e) {
                    if (fragment.isAdded()) {
                        fragment.requireActivity().runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(fragment.requireContext(), "Error parsing response: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            }
        });
    }

    public void showModelPickerDialog(List<String> models) {
        if (models == null || models.isEmpty() || !fragment.isAdded()) return;

        String[] items = models.toArray(new String[0]);
        SharedPreferences aiPref = fragment.requireContext().getSharedPreferences("P12", Context.MODE_PRIVATE);
        String currentModel = aiPref.getString("P12I5", "");

        int checkedItem = -1;
        for (int i = 0; i < items.length; i++) {
            if (items[i].equalsIgnoreCase(currentModel)) {
                checkedItem = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle("Select AI Model")
                .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                    String selectedModel = items[which];
                    aiPref.edit().putString("P12I5", selectedModel).apply();
                    fragment.updateCurrentModelBadge();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
