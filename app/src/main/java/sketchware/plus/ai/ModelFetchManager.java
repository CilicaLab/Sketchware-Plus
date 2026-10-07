package sketchware.plus.ai;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
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
        boolean isGroq = "groq".equalsIgnoreCase(provider) || "grog".equalsIgnoreCase(provider) || aiPref.getString("P12I4", "").contains("groq.com");
        String title = isGoogle ? "Google AI Model" : (isGroq ? "Groq AI Model" : "AI Model Selection");

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

        SharedPreferences aiPref = fragment.requireContext().getSharedPreferences("P12", Context.MODE_PRIVATE);
        String provider = aiPref.getString("P12_PROVIDER", "custom");
        String endpoint = aiPref.getString("P12I4", "");
        boolean isGroq = "groq".equalsIgnoreCase(provider) || "grog".equalsIgnoreCase(provider) || endpoint.contains("groq.com");

        String currentModel = aiPref.getString("P12I5", "");

        int checkedItem = -1;
        for (int i = 0; i < models.size(); i++) {
            if (models.get(i).equalsIgnoreCase(currentModel)) {
                checkedItem = i;
                break;
            }
        }

        GroqModelAdapter adapter = new GroqModelAdapter(fragment.requireContext(), models, isGroq);

        new MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(isGroq ? "Select Groq Model" : "Select AI Model")
                .setSingleChoiceItems(adapter, checkedItem, (dialog, which) -> {
                    String selectedRawModel = models.get(which);
                    aiPref.edit().putString("P12I5", selectedRawModel).apply();
                    fragment.updateCurrentModelBadge();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static class GroqModelAdapter extends ArrayAdapter<String> {
        private final List<String> models;
        private final boolean isGroq;

        public GroqModelAdapter(Context context, List<String> models, boolean isGroq) {
            super(context, android.R.layout.simple_list_item_2, models);
            this.models = models;
            this.isGroq = isGroq;
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            }
            String modelId = models.get(position);
            TextView text1 = view.findViewById(android.R.id.text1);
            TextView text2 = view.findViewById(android.R.id.text2);

            if (isGroq) {
                boolean supportsTools = supportsToolCalling(modelId);
                String tag = supportsTools ? "[Supports Tool Calling]" : "[No Tool Calling]";
                String desc = getGroqModelDescription(modelId);

                text1.setText(modelId);
                text1.setTypeface(null, Typeface.BOLD);
                text1.setTextSize(15);
                
                text2.setText(tag + "  " + desc);
                text2.setTextSize(12);
                text2.setVisibility(View.VISIBLE);
            } else {
                text1.setText(modelId);
                text1.setTypeface(null, Typeface.BOLD);
                text2.setVisibility(View.GONE);
            }

            return view;
        }
    }

    private static boolean supportsToolCalling(String modelId) {
        String lower = modelId.toLowerCase();
        return lower.contains("gpt-oss-20b") || lower.contains("gpt-oss-120b") || lower.contains("qwen");
    }

    private static String getGroqModelDescription(String modelId) {
        switch (modelId.toLowerCase()) {
            case "openai/gpt-oss-20b":
                return "General-purpose language/reasoning model with native function and tool calling capabilities.";
            case "openai/gpt-oss-120b":
                return "Larger variant with full agentic, function calling, and web search tool support.";
            case "qwen/qwen3.8-27b":
                return "Multimodal/agentic model supporting structured function and tool calling.";
            case "allam-2-7b":
                return "Text generation/chat model without structured function calling support.";
            case "canopylabs/orpheus-arabic-saudi":
            case "canopylabs/orpheus-v1-english":
                return "Specialized voice/audio generation model.";
            case "meta-llama/llama-prompt-guard-2-22m":
            case "meta-llama/llama-prompt-guard-2-86m":
                return "Utility classifier for prompt injection and safety filtering.";
            case "openai/gpt-oss-safeguard-20b":
                return "Moderation and guardrail model meant solely for text safety filtering.";
            case "whisper-large-v3":
            case "whisper-large-v3-turbo":
                return "Audio speech-to-text transcription model.";
            default:
                return "Groq AI model";
        }
    }
}
