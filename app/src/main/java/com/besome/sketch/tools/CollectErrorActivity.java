package com.besome.sketch.tools;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import sketchware.plus.BuildConfig;
import sketchware.plus.R;

public class CollectErrorActivity extends BaseAppCompatActivity {
    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        final String error = (intent != null && intent.hasExtra("error")) ? intent.getStringExtra("error") : "Unknown error";

        var dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.common_error_an_error_occurred)
                .setMessage("An error occurred while running Sketchware Plus. Would you like to send this crash report?\n\n" + error)
                .setPositiveButton("Send", (dialogInterface, which) -> sendCrashReport(error))
                .setNeutralButton("Copy", (dialogInterface, which) -> copyToClipboard(error))
                .setNegativeButton("Cancel", (dialogInterface, which) -> finish())
                .setCancelable(false)
                .show();

        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setTextIsSelectable(true);
        }
    }

    private String hashStackTrace(String stackTrace) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(stackTrace.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(stackTrace.hashCode());
        }
    }

    private boolean isAlreadySent(String hash) {
        SharedPreferences prefs = getSharedPreferences("crash_sent", MODE_PRIVATE);
        return prefs.getBoolean(hash, false);
    }

    private void markSent(String hash) {
        SharedPreferences prefs = getSharedPreferences("crash_sent", MODE_PRIVATE);
        prefs.edit().putBoolean(hash, true).apply();
    }

    private void sendCrashReport(String error) {
        final String hash = hashStackTrace(error);
        if (isAlreadySent(hash)) {
            Toast.makeText(this, "Already reported", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String versionName = BuildConfig.VERSION_NAME;
        String deviceModel = Build.MODEL;
        String androidVersion = Build.VERSION.RELEASE;

        String rawReport = "App Version: " + versionName + "\n" +
                "Device Model: " + deviceModel + "\n" +
                "Android Version: " + androidVersion + "\n\n" +
                "Stack Trace:\n" + error;

        String reportText = rawReport.length() > 3500 ? rawReport.substring(0, 3500) : rawReport;

        OkHttpClient client = new OkHttpClient();
        RequestBody body = RequestBody.create(reportText, MediaType.parse("text/plain; charset=utf-8"));
        Request request = new Request.Builder()
                .url("https://sk-crash.adoboerich91.workers.dev")
                .post(body)
                .build();

        new Thread(() -> {
            try (Response response = client.newCall(request).execute()) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        markSent(hash);
                        Toast.makeText(CollectErrorActivity.this, "Crash report sent successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(CollectErrorActivity.this, "Failed to send crash report", Toast.LENGTH_SHORT).show();
                    }
                    finish();
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(CollectErrorActivity.this, "Error sending cloud crash report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }).start();
    }

    private void copyToClipboard(String error) {
        String versionName = BuildConfig.VERSION_NAME;
        String deviceModel = Build.MODEL;
        String androidVersion = Build.VERSION.RELEASE;

        String deviceInfo = "Sketchware Plus " + versionName + "\n"
                + "Android: " + androidVersion + "\n"
                + "Model: " + deviceModel;

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("error", deviceInfo + "\n\n```\n" + error + "\n```");
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
    }
}
