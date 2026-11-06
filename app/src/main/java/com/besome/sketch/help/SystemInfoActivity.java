package com.besome.sketch.help;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.besome.sketch.lib.ui.PropertyOneLineItem;
import com.besome.sketch.lib.ui.PropertyTwoLineItem;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;

import a.a.a.GB;
import a.a.a.mB;
import mod.hey.studios.util.Helper;
import sketchware.plus.R;
import sketchware.plus.databinding.ActivitySystemInfoBinding;
import sketchware.plus.utility.FileUtil;

public class SystemInfoActivity extends BaseAppCompatActivity {

    private ActivitySystemInfoBinding binding;
    private TextView sketchwareStats;
    private TextView deviceStats;
    private LinearProgressIndicator progress;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivitySystemInfoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));

        addStorageInfoCard();

        addApiLevelInfo();
        addAndroidVersionNameInfo();
        addScreenResolutionInfo();
        addScreenDpiInfo();
        addModelNameInfo();
        addDeveloperOptionsShortcut();
    }

    private void addStorageInfoCard() {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(24, 16, 24, 16);
        card.setLayoutParams(cardParams);
        card.setRadius(24f);
        card.setElevation(2f);
        card.setCardBackgroundColor(MaterialColors.getColor(card, R.attr.colorSurfaceContainer, 0xFFE0E0E0));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("Storage Usage");
        title.setTextSize(18f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(MaterialColors.getColor(title, com.google.android.material.R.attr.colorOnSurface, 0xFF000000));
        layout.addView(title);

        deviceStats = new TextView(this);
        deviceStats.setText("Device Storage: Calculating...");
        deviceStats.setTextSize(13f);
        deviceStats.setSingleLine(true);
        deviceStats.setEllipsize(android.text.TextUtils.TruncateAt.END);
        deviceStats.setTextColor(MaterialColors.getColor(deviceStats, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF666666));
        deviceStats.setPadding(0, 16, 0, 8);
        layout.addView(deviceStats);

        progress = new LinearProgressIndicator(this);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        progressParams.setMargins(0, 0, 0, 16);
        progress.setLayoutParams(progressParams);
        progress.setProgress(0);
        progress.setTrackCornerRadius(8);
        layout.addView(progress);

        sketchwareStats = new TextView(this);
        sketchwareStats.setText(".sketchware Folder: Calculating...");
        sketchwareStats.setTextSize(13f);
        sketchwareStats.setSingleLine(true);
        sketchwareStats.setEllipsize(android.text.TextUtils.TruncateAt.END);
        sketchwareStats.setTypeface(null, android.graphics.Typeface.BOLD);
        sketchwareStats.setTextColor(MaterialColors.getColor(sketchwareStats, R.attr.colorPrimary, 0xFF6200EE));
        sketchwareStats.setPadding(0, 8, 0, 16);
        layout.addView(sketchwareStats);

        // Clean Cache Button
        MaterialButton cleanButton = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cleanButton.setLayoutParams(btnParams);
        cleanButton.setText("Clean Cache");
        cleanButton.setOnClickListener(v -> showClearCacheConfirmationDialog());
        layout.addView(cleanButton);

        card.addView(layout);
        binding.content.addView(card, 0);

        loadStorageInfo();
    }

    private void loadStorageInfo() {
        if (deviceStats != null) deviceStats.setText("Device Storage: Measuring storage capacity...");
        if (sketchwareStats != null) sketchwareStats.setText(".sketchware Folder: Initializing scan...");
        if (progress != null) {
            progress.setIndeterminate(true);
        }

        new Thread(() -> {
            long deviceTotal = 0;
            long deviceFree = 0;
            try {
                StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
                deviceTotal = stat.getTotalBytes();
                deviceFree = stat.getAvailableBytes();
            } catch (Exception ignored) {}
            long deviceUsed = deviceTotal - deviceFree;
            int deviceUsedPercent = deviceTotal > 0 ? (int) ((deviceUsed * 100) / deviceTotal) : 0;

            final long finalDeviceUsed = deviceUsed;
            final long finalDeviceTotal = deviceTotal;
            final int finalPercent = deviceUsedPercent;

            runOnUiThread(() -> {
                if (deviceStats != null) {
                    deviceStats.setText("Device Storage: " + formatSize(finalDeviceUsed) + " used of " + formatSize(finalDeviceTotal) + " (" + finalPercent + "%)");
                }
                if (progress != null) {
                    progress.setIndeterminate(false);
                    progress.setProgress(finalPercent);
                }
            });

            long sketchwareSize = 0;
            try {
                File sketchwareDir = new File(FileUtil.getExternalStorageDir(), ".sketchware");
                long[] state = new long[]{0, System.currentTimeMillis()};
                sketchwareSize = getDirSizeWithProgress(sketchwareDir, sketchwareDir, state);
            } catch (Exception ignored) {}

            final long finalSketchwareSize = sketchwareSize;

            runOnUiThread(() -> {
                if (sketchwareStats != null) {
                    sketchwareStats.setText(".sketchware Folder: " + formatSize(finalSketchwareSize));
                }
            });
        }).start();
    }

    private void showClearCacheConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear Sketchware Cache")
                .setMessage("This will clean temporary files, build bins, and download APK caches inside the .sketchware folder.\n\nYour projects, source code, blocks, and user files will NOT be affected.")
                .setPositiveButton("Clear", (dialog, which) -> clearSketchwareCache())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearSketchwareCache() {
        if (sketchwareStats != null) sketchwareStats.setText(".sketchware Folder: Cleaning cache...");
        new Thread(() -> {
            try {
                File sketchwareDir = new File(FileUtil.getExternalStorageDir(), ".sketchware");
                if (sketchwareDir.exists()) {
                    // 1. Clean .sketchware/temp/
                    runOnUiThread(() -> {
                        if (sketchwareStats != null) sketchwareStats.setText(".sketchware Folder: Cleaning temporary files (temp/)...");
                    });
                    File tempDir = new File(sketchwareDir, "temp");
                    deleteRecursive(tempDir);

                    // 2. Clean .sketchware/download/apk/
                    runOnUiThread(() -> {
                        if (sketchwareStats != null) sketchwareStats.setText(".sketchware Folder: Cleaning APK download cache (download/apk/)...");
                    });
                    File apkDir = new File(new File(sketchwareDir, "download"), "apk");
                    deleteRecursive(apkDir);

                    // 3. Clean bin / gen build folders inside mysc/<sc_id>/
                    File myscDir = new File(sketchwareDir, "mysc");
                    if (myscDir.exists() && myscDir.isDirectory()) {
                        File[] projects = myscDir.listFiles();
                        if (projects != null) {
                            for (File proj : projects) {
                                if (proj.isDirectory()) {
                                    final String projName = proj.getName();
                                    runOnUiThread(() -> {
                                        if (sketchwareStats != null) sketchwareStats.setText(".sketchware Folder: Cleaning build cache for project #" + projName + "...");
                                    });
                                    File binDir = new File(proj, "bin");
                                    deleteRecursive(binDir);
                                    File genDir = new File(proj, "gen");
                                    deleteRecursive(genDir);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            runOnUiThread(() -> {
                Toast.makeText(this, "Sketchware cache cleared successfully!", Toast.LENGTH_SHORT).show();
                loadStorageInfo(); // Recalculate and update UI automatically
            });
        }).start();
    }

    private boolean isSymlink(File f) {
        try {
            File parent = f.getParentFile();
            File canon = parent == null ? f : new File(parent.getCanonicalFile(), f.getName());
            return !canon.getCanonicalFile().equals(canon.getAbsoluteFile());
        } catch (IOException e) {
            return true;
        }
    }

    private void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory() && !isSymlink(f)) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }

    private long getDirSizeWithProgress(File dir, File baseDir, long[] state) {
        long size = 0;
        if (dir != null && dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        String relativePath = baseDir != null ? baseDir.toPath().relativize(file.toPath()).toString().replace('\\', '/') : file.getName();
                        if (relativePath.length() > 22) {
                            relativePath = "..." + relativePath.substring(relativePath.length() - 19);
                        }
                        long now = System.currentTimeMillis();
                        if (now - state[1] > 80) { // Throttle UI updates to max once per ~80ms
                            state[1] = now;
                            long currentSize = state[0] + size;
                            final String displayPath = relativePath;
                            runOnUiThread(() -> {
                                if (sketchwareStats != null) {
                                    sketchwareStats.setText(".sketchware Folder: Scanning " + displayPath + "... (" + formatSize(currentSize) + ")");
                                }
                            });
                        }
                        size += getDirSizeWithProgress(file, baseDir, state);
                    } else {
                        long length = file.length();
                        size += length;
                        state[0] += length;
                    }
                }
            }
        }
        return size;
    }

    private String formatSize(long sizeBytes) {
        if (sizeBytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(sizeBytes) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(sizeBytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    private void addInfo(int key, String name, String description) {
        PropertyTwoLineItem propertyTwoLineItem = new PropertyTwoLineItem(this);
        propertyTwoLineItem.setKey(key);
        propertyTwoLineItem.setName(name);
        propertyTwoLineItem.setDesc(description);
        binding.content.addView(propertyTwoLineItem);
    }

    private void addAndroidVersionNameInfo() {
        addInfo(1, Helper.getResString(R.string.system_information_title_android_version),
                GB.b() + "(" + VERSION.RELEASE + ")");
    }

    private void addApiLevelInfo() {
        addInfo(0, Helper.getResString(R.string.system_information_title_android_version),
                "API - " + VERSION.SDK_INT);
    }

    private void addDeveloperOptionsShortcut() {
        PropertyOneLineItem propertyOneLineItem = new PropertyOneLineItem(this);
        propertyOneLineItem.setKey(5);
        propertyOneLineItem.setName(Helper.getResString(R.string.system_information_developer_options));
        binding.content.addView(propertyOneLineItem);
        propertyOneLineItem.setOnClickListener(v -> {
            if (!mB.a()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                    startActivity(intent);
                } catch (ActivityNotFoundException ignored) {
                }
            }
        });
    }

    private void addScreenDpiInfo() {
        float[] dpiXY = GB.b(this);
        addInfo(3, Helper.getResString(R.string.system_information_dpi), String.valueOf(dpiXY[0]));
    }

    private void addModelNameInfo() {
        addInfo(4, Helper.getResString(R.string.system_information_model_name), Build.MODEL);
    }

    private void addScreenResolutionInfo() {
        int[] widthHeight = GB.c(this);
        addInfo(2, Helper.getResString(R.string.system_information_system_resolution),
                widthHeight[0] + " x " + widthHeight[1]);
    }
}
