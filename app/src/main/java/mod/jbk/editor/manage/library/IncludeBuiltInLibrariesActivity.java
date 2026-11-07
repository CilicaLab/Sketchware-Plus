package mod.jbk.editor.manage.library;

import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.TransitionManager;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.transition.MaterialFadeThrough;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import a.a.a.MA;
import mod.hey.studios.util.Helper;
import mod.jbk.build.BuiltInLibraries;
import mod.jbk.util.LogUtil;
import sketchware.plus.R;
import sketchware.plus.databinding.DialogSelectLibrariesBinding;
import sketchware.plus.databinding.ManageLibraryExcludeBuiltinLibrariesBinding;
import sketchware.plus.utility.FileUtil;
import sketchware.plus.utility.HapticManager;
import sketchware.plus.utility.SketchwareUtil;

public class IncludeBuiltInLibrariesActivity extends BaseAppCompatActivity {
    private static final String TAG = "IncludeBuiltInLibraries";

    private ManageLibraryExcludeBuiltinLibrariesBinding binding;
    private String sc_id;
    private boolean isIncludingEnabled;
    private List<BuiltInLibraries.BuiltInLibrary> includedLibraries;
    private Pair<Boolean, List<BuiltInLibraries.BuiltInLibrary>> config;

    private static File getConfigPath(String sc_id) {
        return new File(Environment.getExternalStorageDirectory(),
                ".sketchware" + File.separator + "data" + File.separator + sc_id + File.separator + "included_library");
    }

    private static void saveConfig(String sc_id, boolean isIncludingEnabled, List<BuiltInLibraries.BuiltInLibrary> includedLibraries) {
        List<String> includedLibraryNames = includedLibraries.stream()
                .map(BuiltInLibraries.BuiltInLibrary::getName)
                .collect(Collectors.toList());
        Pair<Boolean, List<String>> config = new Pair<>(isIncludingEnabled, includedLibraryNames);
        FileUtil.writeFile(getConfigPath(sc_id).getAbsolutePath(), new Gson().toJson(config));
    }

    @Nullable
    private static Pair<Boolean, List<BuiltInLibraries.BuiltInLibrary>> readConfig(String sc_id) {
        File configPath = getConfigPath(sc_id);
        if (configPath.isFile()) {
            String content = FileUtil.readFile(configPath.getAbsolutePath());

            String errorMessage;
            try {
                Pair<Boolean, List<String>> config = new Gson().fromJson(content, new TypeToken<>() {
                });
                if (config != null) {
                    List<BuiltInLibraries.BuiltInLibrary> libraries = config.second.stream()
                            .map(s -> {
                                Optional<BuiltInLibraries.BuiltInLibrary> library = BuiltInLibraries.BuiltInLibrary.ofName(s);
                                return library.orElse(null);
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    return new Pair<>(config.first, libraries);
                }
                errorMessage = "read config was null";
            } catch (Exception e) {
                errorMessage = Log.getStackTraceString(e);
            }

            LogUtil.e(TAG, "Couldn't parse config: " + errorMessage);
        }
        return null;
    }

    public static boolean isIncludingEnabled(String sc_id) {
        Pair<Boolean, List<BuiltInLibraries.BuiltInLibrary>> config = readConfig(sc_id);
        if (config != null) {
            return config.first;
        } else {
            return false;
        }
    }

    @NonNull
    public static List<BuiltInLibraries.BuiltInLibrary> getIncludedLibraries(String sc_id) {
        Pair<Boolean, List<BuiltInLibraries.BuiltInLibrary>> config = readConfig(sc_id);
        if (config != null) {
            return config.second;
        } else {
            return Collections.emptyList();
        }
    }

    @DrawableRes
    public static int getItemIcon() {
        return R.drawable.ic_mtrl_add;
    }

    public static String getItemTitle() {
        return "Include built-in libraries";
    }

    public static String getDefaultItemDescription() {
        return "Force-include built-in libraries";
    }

    public static String getSelectedLibrariesItemDescription() {
        return "%1$d/%2$d built-in libraries included";
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isStoragePermissionGranted()) {
            finish();
            return;
        }

        binding = ManageLibraryExcludeBuiltinLibrariesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState == null) {
            sc_id = getIntent().getStringExtra("sc_id");
        } else {
            sc_id = savedInstanceState.getString("sc_id");
        }

        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Include built-in libraries");
        binding.toolbar.setNavigationOnClickListener(view -> onBackPressed());

        binding.tvEnable.setText(Helper.getResString(R.string.design_library_settings_title_enabled));

        binding.excludeLibrary.setText("Include library");

        ViewGroup noContentLayout = binding.noContent;
        if (noContentLayout.getChildCount() >= 2) {
            if (noContentLayout.getChildAt(0) instanceof TextView tv1) {
                tv1.setText("No included built-in libraries found");
            }
            if (noContentLayout.getChildAt(1) instanceof TextView tv2) {
                tv2.setText("Enable this feature if disabled, and tap the floating action button to select built-in libraries to include.");
            }
        }

        binding.tvTitle.setText("Included built-in libraries");

        ViewGroup actualContentLayout = binding.actualContent;
        View lastChild = actualContentLayout.getChildAt(actualContentLayout.getChildCount() - 1);
        if (lastChild instanceof TextView warningTv) {
            warningTv.setText("Built-in libraries will be force-included in your project build dependencies.");
        }

        binding.excludeLibrary.setOnClickListener(v -> showSelectBuiltInLibrariesDialog());
        binding.layoutSwitchCard.setOnClickListener(v -> binding.libSwitch.setChecked(!binding.libSwitch.isChecked()));
        binding.libSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            HapticManager.vibrateSwitch(buttonView);
            isIncludingEnabled = isChecked;
            refresh();
        });
        config = readConfig(sc_id);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Reset")
                .setIcon(AppCompatResources.getDrawable(this, R.drawable.history_24px))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem menuItem) {
        String title = menuItem.getTitle().toString();
        if (title.equals("Reset")) {
            showResetDialog();
        } else {
            return false;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (config != null && config.first.equals(isIncludingEnabled) && config.second.equals(includedLibraries)) {
            super.onBackPressed();
        } else {
            k();
            try {
                new Handler(Looper.myLooper()).postDelayed(() ->
                        new SaveConfigTask(this).execute(), 500);
            } catch (Exception e) {
                onSaveError(e);
            }
        }
    }

    private void onSaveError(Throwable throwable) {
        String errorMessage = "Couldn't save configuration: " + throwable.getMessage();
        LogUtil.e(TAG, errorMessage, throwable);
        onSaveError(errorMessage);
    }

    private void onSaveError(String errorMessage) {
        SketchwareUtil.toastError(errorMessage);
        h();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("sc_id", sc_id);
        outState.putBoolean("isIncludingEnabled", isIncludingEnabled);
        outState.putParcelableArrayList("includedLibraryNames", new ArrayList<>(includedLibraries));
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (savedInstanceState == null) {
            isIncludingEnabled = isIncludingEnabled(sc_id);
            includedLibraries = getIncludedLibraries(sc_id);
        } else {
            isIncludingEnabled = savedInstanceState.getBoolean("isIncludingEnabled");
            includedLibraries = savedInstanceState.getParcelableArrayList("includedLibraryNames");
        }

        refresh();
    }

    private void refresh() {
        binding.libSwitch.setChecked(isIncludingEnabled);

        if (isIncludingEnabled) {
            binding.excludeLibrary.show();
        } else {
            binding.excludeLibrary.hide();
        }

        String libraries = includedLibraries.stream()
                .map(BuiltInLibraries.BuiltInLibrary::getName)
                .collect(Collectors.joining(", "));

        libraries = isIncludingEnabled ? libraries : "";

        MaterialFadeThrough transition = new MaterialFadeThrough();
        TransitionManager.beginDelayedTransition(binding.content, transition);

        binding.actualContent.setVisibility(libraries.isEmpty() ? View.GONE : View.VISIBLE);
        binding.noContent.setVisibility(libraries.isEmpty() ? View.VISIBLE : View.GONE);
        binding.itemDesc.setText(libraries);
    }

    private void showResetDialog() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setIcon(R.drawable.rollback_96);
        dialog.setTitle(Helper.getResString(R.string.common_word_reset));
        dialog.setMessage("Reset included built-in libraries? This action cannot be undone.");
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_reset), (v, which) -> {
            saveConfig(sc_id, false, Collections.emptyList());
            binding.libSwitch.setChecked(false);
            includedLibraries = Collections.emptyList();
            refresh();
            v.dismiss();
        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.show();
    }

    private void showSelectBuiltInLibrariesDialog() {
        DialogSelectLibrariesBinding binding = DialogSelectLibrariesBinding.inflate(getLayoutInflater());

        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle("Select built-in libraries to include");

        TypedArray typedArray = obtainStyledAttributes(null, new int[0]);
        try {
            //noinspection JavaReflectionMemberAccess
            Method method = View.class.getDeclaredMethod("initializeScrollbars", TypedArray.class);
            method.setAccessible(true);
            method.invoke(binding.recyclerView, typedArray);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            LogUtil.e(TAG, "Couldn't add scrollbars to RecyclerView", e);
        }
        typedArray.recycle();
        binding.recyclerView.setVerticalScrollBarEnabled(true);

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(null));
        BuiltInLibraryAdapter adapter = new BuiltInLibraryAdapter(includedLibraries);
        adapter.setHasStableIds(true);
        binding.recyclerView.setAdapter(adapter);

        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        dialog.setView(binding.getRoot());
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_save), (v, which) -> {
            includedLibraries = adapter.getSelectedBuiltInLibraries();
            v.dismiss();
            refresh();
        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);

        dialog.show();
    }

    private static class SaveConfigTask extends MA {
        private final WeakReference<IncludeBuiltInLibrariesActivity> activity;

        public SaveConfigTask(IncludeBuiltInLibrariesActivity activity) {
            super(activity);
            this.activity = new WeakReference<>(activity);
            activity.a(this);
        }

        @Override
        public void a() {
            var activity = this.activity.get();
            if (activity != null) {
                activity.h();
                activity.setResult(RESULT_OK);
                activity.finish();
            }
        }

        @Override
        public void a(String s) {
            var activity = this.activity.get();
            if (activity != null) {
                activity.onSaveError("Couldn't save configuration: " + s);
            }
        }

        @Override
        public void b() {
            var activity = this.activity.get();
            if (activity != null) {
                saveConfig(activity.sc_id, activity.isIncludingEnabled, activity.includedLibraries);
            }
        }
    }

    private static class BuiltInLibraryAdapter extends RecyclerView.Adapter<BuiltInLibraryAdapter.ViewHolder> {
        private final List<BuiltInLibraries.BuiltInLibrary> libraries;
        private final Map<Integer, Void> checkedIndices;
        private List<BuiltInLibraries.BuiltInLibrary> filteredLibraries;

        public BuiltInLibraryAdapter(List<BuiltInLibraries.BuiltInLibrary> includedLibraries) {
            libraries = Arrays.asList(BuiltInLibraries.KNOWN_BUILT_IN_LIBRARIES);
            libraries.sort(Comparator.comparing(BuiltInLibraries.BuiltInLibrary::getName, String.CASE_INSENSITIVE_ORDER));
            filteredLibraries = new ArrayList<>(libraries);
            checkedIndices = new HashMap<>();

            for (BuiltInLibraries.BuiltInLibrary includedLibrary : includedLibraries) {
                int index = libraries.indexOf(includedLibrary);
                if (index >= 0) {
                    checkedIndices.put(index, null);
                }
            }
        }

        @Override
        public int getItemCount() {
            return filteredLibraries.size();
        }

        @Override
        public long getItemId(int position) {
            return libraries.indexOf(filteredLibraries.get(position));
        }

        @Override
        @NonNull
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.manage_library_exclude_builtin_libraries_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            BuiltInLibraries.BuiltInLibrary library = filteredLibraries.get(position);
            int originalPosition = libraries.indexOf(library);
            holder.selected.setChecked(checkedIndices.containsKey(originalPosition));
            holder.name.setText(library.getName());
            Optional<String> packageName = library.getPackageName();
            if (packageName.isPresent()) {
                holder.packageName.setVisibility(View.VISIBLE);
                holder.packageName.setText(packageName.get());
            } else {
                holder.packageName.setVisibility(View.GONE);
            }

            View.OnClickListener selectingListener = v -> {
                CheckBox selected = holder.selected;
                if (v.getId() != R.id.chk_select) {
                    selected.setChecked(!selected.isChecked());
                }

                if (selected.isChecked()) {
                    checkedIndices.put(originalPosition, null);
                } else {
                    checkedIndices.remove(originalPosition);
                }
            };
            holder.selected.setOnClickListener(selectingListener);
            holder.selectableItem.setOnClickListener(selectingListener);
        }

        public List<BuiltInLibraries.BuiltInLibrary> getSelectedBuiltInLibraries() {
            Set<Integer> checkedIndicesKeySet = checkedIndices.keySet();
            List<BuiltInLibraries.BuiltInLibrary> selectedLibraries = new ArrayList<>(checkedIndicesKeySet.size());
            for (int i : checkedIndicesKeySet) {
                selectedLibraries.add(libraries.get(i));
            }
            return selectedLibraries;
        }

        public void filter(String query) {
            filteredLibraries = new ArrayList<>();
            for (BuiltInLibraries.BuiltInLibrary library : libraries) {
                if (library.getName().toLowerCase().contains(query.toLowerCase())) {
                    filteredLibraries.add(library);
                }
            }
            notifyDataSetChanged();
        }

        private static class ViewHolder extends RecyclerView.ViewHolder {
            public final LinearLayout selectableItem;
            public final CheckBox selected;
            public final TextView name;
            public final TextView packageName;

            public ViewHolder(View itemView) {
                super(itemView);
                selectableItem = itemView.findViewById(R.id.view_item);
                selected = itemView.findViewById(R.id.chk_select);
                name = itemView.findViewById(R.id.tv_screen_name);
                packageName = itemView.findViewById(R.id.tv_activity_name);
            }
        }
    }
}
