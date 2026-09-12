package mod.khaled.logcat;

import static sketchware.plus.utility.FileUtil.createNewFileIfNotPresent;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.hey.studios.util.Helper;
import sketchware.plus.R;
import sketchware.plus.databinding.ActivityLogcatreaderBinding;
import sketchware.plus.databinding.EasyDeleteEdittextBinding;
import sketchware.plus.databinding.ViewLogcatItemBinding;
import sketchware.plus.lib.base.BaseTextWatcher;
import sketchware.plus.utility.FileUtil;
import sketchware.plus.utility.SketchwareUtil;

public class LogReaderActivity extends BaseAppCompatActivity {

    private final BroadcastReceiver logger = new Logger(this);
    private final Pattern logPattern = Pattern.compile("^(.*\\d) ([VADEIW]) (.*): (.*)");
    private final ArrayList<HashMap<String, Object>> mainList = new ArrayList<>();
    private String pkgFilter = "";
    private String packageName = "sketchware.plus";
    private boolean autoScroll = true;
    private ArrayList<String> pkgFilterList = new ArrayList<>();

    private final String[] logLevels = {"Verbose", "Debug", "Info", "Warning", "Error", "Assert"};
    private final String[] logLevelValues = {"V", "D", "I", "W", "E", "A"};
    private final boolean[] selectedLogLevels = {true, true, true, true, true, true};

    private ActivityLogcatreaderBinding binding;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = ActivityLogcatreaderBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initialize();
    }

    private void initialize() {
        binding.logsRecyclerView.setAdapter(new Adapter(new ArrayList<>(), this));

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("sketchware.plus.ACTION_NEW_DEBUG_LOG");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logger, intentFilter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(logger, intentFilter);
        }

        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        binding.topAppBar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_clear) {
                mainList.clear();
                ((Adapter) binding.logsRecyclerView.getAdapter()).deleteAll();
            } else if (id == R.id.action_auto_scroll) {
                autoScroll = !item.isChecked();
                item.setChecked(autoScroll);
                if (autoScroll) {
                    binding.logsRecyclerView.getLayoutManager().scrollToPosition(binding.logsRecyclerView.getAdapter().getItemCount() - 1);
                }
            } else if (id == R.id.action_filter) {
                showFilterDialog();
            } else if (id == R.id.action_filter_level) {
                showLogLevelFilterDialog();
            } else if (id == R.id.action_export) {
                exportLogcat(mainList);
            }
            return true;
        });

        binding.searchInput.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilters();
            }
        });
    }

    private void applyFilters() {
        String query = Helper.getText(binding.searchInput).toLowerCase();
        ArrayList<HashMap<String, Object>> filteredList = new ArrayList<>();

        for (HashMap<String, Object> m : mainList) {
            if (matchesFilters(m, query)) {
                filteredList.add(m);
            }
        }
        binding.logsRecyclerView.setAdapter(new Adapter(filteredList, this));
    }

    private boolean matchesFilters(HashMap<String, Object> map, String query) {
        boolean pkgMatch = pkgFilterList.isEmpty() || (map.containsKey("pkgName") && pkgFilterList.contains(map.get("pkgName").toString()));

        boolean levelMatch = true;
        if (map.containsKey("type")) {
            String type = map.get("type").toString();
            levelMatch = false;
            for (int i = 0; i < logLevelValues.length; i++) {
                if (logLevelValues[i].equals(type)) {
                    levelMatch = selectedLogLevels[i];
                    break;
                }
            }
        }

        boolean searchMatch = query.isEmpty() || map.get("logRaw").toString().toLowerCase().contains(query);

        return pkgMatch && levelMatch && searchMatch;
    }

    void showLogLevelFilterDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Filter by log level")
                .setIcon(R.drawable.ic_mtrl_filter)
                .setMultiChoiceItems(logLevels, selectedLogLevels, (dialog, which, isChecked) -> {
                    selectedLogLevels[which] = isChecked;
                })
                .setPositiveButton("Apply", (dialog, which) -> applyFilters())
                .setNegativeButton("Cancel", null)
                .show();
    }

    void showFilterDialog() {
        var dialogBinding = EasyDeleteEdittextBinding.inflate(getLayoutInflater());
        View view = dialogBinding.getRoot();

        dialogBinding.imgDelete.setVisibility(View.GONE);

        var builder = new MaterialAlertDialogBuilder(this)
                .setTitle("Filter by package name")
                .setMessage("For multiple package names, separate them with a comma (,).")
                .setIcon(R.drawable.ic_mtrl_filter)
                .setView(view)
                .setPositiveButton("Apply", (dialog, which) -> {
                    pkgFilter = Helper.getText(dialogBinding.easyEdInput);
                    pkgFilterList = new ArrayList<>(Arrays.asList(pkgFilter.split(",")));
                    applyFilters();
                })
                .setNeutralButton("Reset", (dialog, which) -> {
                    pkgFilter = "";
                    pkgFilterList.clear();
                    dialogBinding.easyEdInput.setText("");
                    applyFilters();
                })
                .setNegativeButton("Cancel", null)
                .create();

        builder.show();
    }

    private String safeGet(HashMap<String, Object> log, String key) {
        Object value = log.get(key);
        return value != null ? value.toString() : "";
    }

    private void exportLogcat(ArrayList<HashMap<String, Object>> logs) {
        if (logs.isEmpty()) {
            SketchwareUtil.toastError("Nothing to Export");
            return;
        }
        try {
            String fileName = Calendar.getInstance(Locale.ENGLISH).getTimeInMillis() + ".txt";
            String filePath = Environment.getExternalStorageDirectory() + "/.sketchware/logcat/" + packageName + "/" + fileName;
            String stars = "*".repeat(95);
            String blank = " ".repeat(87);
            createNewFileIfNotPresent(filePath);
            StringBuilder contentBuilder = new StringBuilder();
            String formattedDate = new SimpleDateFormat("yyyy/MM/dd 'at' HH:mm:ss", Locale.ENGLISH).format(new Date());

            contentBuilder.append(stars).append("\n");
            contentBuilder.append(stars).append("\n");
            contentBuilder.append("**").append(blank).append("**");
            contentBuilder.append("\n**    Exported logcat reader for ").append(packageName).append(" on ").append(formattedDate).append("  **\n");
            contentBuilder.append("**").append(blank).append("**\n");
            contentBuilder.append(stars).append("\n");
            contentBuilder.append(stars).append("\n");

            for (HashMap<String, Object> log : logs) {
                String date = safeGet(log, "date");
                String type = safeGet(log, "type");
                String tag = safeGet(log, "header");
                String body = safeGet(log, "body");

                if (!type.isEmpty()) {
                    contentBuilder.append("\n\n|-- Log Type: ").append(type).append("\n");
                    contentBuilder.append("    |-- Date: ").append(date).append("\n");
                    contentBuilder.append("    |-- Tag: ").append(tag).append("\n");
                    contentBuilder.append("    |-- Message: ").append(body).append("\n");
                    contentBuilder.append("------------------------------------------------");
                }

            }
            FileUtil.writeFile(filePath, contentBuilder.toString());
            SketchwareUtil.toast("Logcat exported successfully: " + filePath);
        } catch (Exception ex) {
            SketchwareUtil.toastError("Something went wrong!");
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        binding.searchInput.clearFocus();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(logger);
    }

    private static class Logger extends BroadcastReceiver {
        private final WeakReference<LogReaderActivity> activityRef;

        public Logger(LogReaderActivity activity) {
            activityRef = new WeakReference<>(activity);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            LogReaderActivity activity = activityRef.get();
            if (activity == null) return;
            HashMap<String, Object> map = new HashMap<>();
            if (intent.hasExtra("log") && intent.getStringExtra("log") != null) {
                if (intent.hasExtra("packageName")) {
                    map.put("pkgName", intent.getStringExtra("packageName"));
                    activity.packageName = intent.getStringExtra("packageName");
                }
                map.put("logRaw", intent.getStringExtra("log"));
                if (intent.getStringExtra("log") == null) return;

                Matcher matcher = activity.logPattern.matcher(intent.getStringExtra("log"));
                if (matcher.matches()) {
                    //group 1 = pid, time stuff & idk
                    //group 2 = log type (a,d,e,i,w)
                    //group 3 = log tag,headers
                    //group 4 = everything else
                    map.put("date", matcher.group(1).trim());
                    map.put("type", matcher.group(2).trim());
                    map.put("header", matcher.group(3));
                    map.put("body", matcher.group(4));
                    map.put("culturedLog", "true");
                }

                activity.mainList.add(map);
                if (activity.matchesFilters(map, Helper.getText(activity.binding.searchInput).toLowerCase())) {
                    ((Adapter) activity.binding.logsRecyclerView.getAdapter()).updateList(map);
                }
            }
        }
    }

    private static class Adapter extends RecyclerView.Adapter<Adapter.ViewHolder> {
        private final ArrayList<HashMap<String, Object>> data;
        private final WeakReference<LogReaderActivity> activityRef;

        public Adapter(ArrayList<HashMap<String, Object>> data, LogReaderActivity activity) {
            this.data = data;
            this.activityRef = new WeakReference<>(activity);
        }

        public void updateList(HashMap<String, Object> _map) {
            LogReaderActivity activity = activityRef.get();
            if (activity == null) return;
            data.add(_map);
            activity.binding.logsRecyclerView.getAdapter().notifyItemInserted(data.size() + 1);

            if (activity.autoScroll) {
                activity.binding.logsRecyclerView.getLayoutManager().scrollToPosition(data.size() - 1);
                activity.binding.appBarLayout.setExpanded(false);
            }

            activity.binding.noContentLayout.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
        }

        public void deleteAll() {
            LogReaderActivity activity = activityRef.get();
            if (activity == null) return;
            data.clear();
            activity.binding.logsRecyclerView.getAdapter().notifyDataSetChanged();
            activity.binding.noContentLayout.setVisibility(View.VISIBLE);
        }

        @Override
        @NonNull
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            var listBinding = ViewLogcatItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            var layoutParams = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            listBinding.getRoot().setLayoutParams(layoutParams);
            return new ViewHolder(listBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            LogReaderActivity activity = activityRef.get();
            if (activity == null) return;
            var binding = holder.listBinding;

            if (data.get(position).containsKey("pkgName")) {
                binding.pkgName.setText(data.get(position).get("pkgName").toString());
                binding.pkgName.setVisibility(View.VISIBLE);
            } else {
                binding.pkgName.setVisibility(View.GONE);
            }
            if (data.get(position).containsKey("culturedLog")) {
                binding.dateHeader.setVisibility(View.VISIBLE);
                binding.type.setText(data.get(position).get("type").toString());
                binding.dateHeader.setText(data.get(position).get("date").toString() + " | " + data.get(position).get("header").toString());
                switch (Objects.requireNonNull(data.get(position).get("type")).toString()) {
                    case "A" -> binding.type.setBackgroundColor(0xFF9C27B0);
                    case "D" -> binding.type.setBackgroundColor(0xFF2196F3);
                    case "E" -> binding.type.setBackgroundColor(0xFFF44336);
                    case "I" -> binding.type.setBackgroundColor(0xFF4CAF50);
                    case "V" -> binding.type.setBackgroundColor(0xFF000000);
                    case "W" -> binding.type.setBackgroundColor(0xFFFFC107);
                    default -> {
                        binding.type.setBackgroundColor(0xFF000000);
                        binding.type.setText("U");
                    }
                }
                binding.log.setText(data.get(position).get("body").toString());
                try {
                    if (data.get(position).get("date").toString().equals(data.get(position + 1).get("date").toString())) {
//                        binding.divider.setVisibility(View.GONE);
                        try {
                            if (data.get(position).get("pkgName").toString().equals(data.get(position + 1).get("pkgName").toString())) {
                                binding.pkgName.setVisibility(View.GONE);
                            } else {
                                binding.pkgName.setVisibility(View.VISIBLE);
                            }
                        } catch (Exception ignored) {
                            binding.pkgName.setVisibility(View.VISIBLE);
                        }
                        try {
                            if (data.get(position).get("header").toString().equals(data.get(position + 1).get("header").toString())) {
                                binding.dateHeader.setVisibility(View.GONE);
                            } else {
                                binding.dateHeader.setVisibility(View.VISIBLE);
                            }
                        } catch (Exception ignored) {
                            binding.dateHeader.setVisibility(View.VISIBLE);
                        }
                    }
                } catch (Exception ignored) {
                }
            } else {
                binding.log.setText(data.get(position).get("logRaw").toString());
                binding.type.setBackgroundColor(0xFF000000);
                binding.type.setText("U");
                binding.dateHeader.setVisibility(View.GONE);
            }
            binding.getRoot().setOnLongClickListener(v -> {
                SketchwareUtil.toast("Copied to clipboard");
                ((ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("clipboard", data.get(position).get("logRaw").toString()));
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        private static class ViewHolder extends RecyclerView.ViewHolder {
            private final ViewLogcatItemBinding listBinding;

            public ViewHolder(@NonNull ViewLogcatItemBinding listBinding) {
                super(listBinding.getRoot());
                this.listBinding = listBinding;
            }
        }
    }
}
