package sketchware.plus.store.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

import a.a.a.wq;
import dev.aldi.sayuti.block.ExtraBlockFile;
import sketchware.plus.R;
import sketchware.plus.store.adapters.LocalItemsAdapter;
import sketchware.plus.store.auth.SessionManager;
import sketchware.plus.store.models.LocalItem;
import sketchware.plus.store.models.StoreItem;
import sketchware.plus.store.models.StoreUser;
import sketchware.plus.store.network.SupabaseClient;
import sketchware.plus.store.repository.RepositoryCallback;
import sketchware.plus.store.repository.RepositoryProvider;
import sketchware.plus.store.repository.StoreRepository;
import sketchware.plus.utility.FileUtil;
import sketchware.plus.utility.GsonUtils;

public class UploadActivity extends AppCompatActivity {

    public static void start(Context context) {
        Intent intent = new Intent(context, UploadActivity.class);
        context.startActivity(intent);
    }

    private StoreRepository repository;
    private LocalItemsAdapter localItemsAdapter;

    private MaterialToolbar toolbar;
    private RadioGroup rgType;
    private RadioButton rbBlock;
    private RadioButton rbComponent;
    private TextInputLayout tilName;
    private TextInputEditText etName;
    private TextInputLayout tilDescription;
    private TextInputEditText etDescription;
    private TextInputEditText etTags;
    private TextView tvLocalSectionTitle;
    private MaterialButton btnSelectItems;
    private TextView tvPreviewSelected;
    private MaterialButton btnSubmit;
    private ProgressBar pbSubmit;

    private String selectedType = StoreItem.TYPE_BLOCK;
    private StoreUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_upload);

        repository = RepositoryProvider.getRepository(this);

        initViews();
        setupToolbar();
        setupRecyclerView();
        setupListeners();

        loadCurrentUser();
        loadLocalItems();
    }

    private boolean isLoggedIn() {
        if (SessionManager.getInstance(this).isLoggedIn()) {
            return true;
        }
        return !RepositoryProvider.isUseSupabase();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        rgType = findViewById(R.id.rg_type);
        rbBlock = findViewById(R.id.rb_block);
        rbComponent = findViewById(R.id.rb_component);
        tilName = findViewById(R.id.til_name);
        etName = findViewById(R.id.et_name);
        tilDescription = findViewById(R.id.til_description);
        etDescription = findViewById(R.id.et_description);
        etTags = findViewById(R.id.et_tags);
        tvLocalSectionTitle = findViewById(R.id.tv_local_section_title);
        btnSelectItems = findViewById(R.id.btn_select_items);
        tvPreviewSelected = findViewById(R.id.tv_preview_selected);
        btnSubmit = findViewById(R.id.btn_submit);
        pbSubmit = findViewById(R.id.pb_submit);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        localItemsAdapter = new LocalItemsAdapter();
        localItemsAdapter.setOnSelectionChangedListener(selectedItems -> updatePreview(selectedItems));
    }

    private void setupListeners() {
        rgType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_block) {
                selectedType = StoreItem.TYPE_BLOCK;
            } else {
                selectedType = StoreItem.TYPE_COMPONENT;
            }
            boolean isBlock = selectedType.equalsIgnoreCase(StoreItem.TYPE_BLOCK);
            tvLocalSectionTitle.setText("Select Local " + (isBlock ? "Block Palettes" : "Components") + " to Include");
            btnSelectItems.setText(isBlock ? "Select Block Palettes" : "Select Components");
            loadLocalItems();
        });

        btnSelectItems.setOnClickListener(v -> showItemSelectionDialog());
        btnSubmit.setOnClickListener(v -> submitPack());
    }

    private void showItemSelectionDialog() {
        if (localItemsAdapter.getItemCount() == 0) {
            Toast.makeText(this, "No items available to select", Toast.LENGTH_SHORT).show();
            return;
        }

        RecyclerView rvDialog = new RecyclerView(this);
        rvDialog.setLayoutManager(new LinearLayoutManager(this));
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        rvDialog.setPadding(padding, padding / 2, padding, padding / 2);
        rvDialog.setAdapter(localItemsAdapter);

        new MaterialAlertDialogBuilder(this)
                .setTitle(selectedType.equalsIgnoreCase(StoreItem.TYPE_BLOCK) ? "Select Block Palettes" : "Select Components")
                .setView(rvDialog)
                .setPositiveButton("Confirm", (dialog, which) -> {
                    updatePreview(localItemsAdapter.getSelectedItems());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadCurrentUser() {
        repository.getCurrentUser(new RepositoryCallback<StoreUser>() {
            @Override
            public void onSuccess(StoreUser user) {
                currentUser = user;
            }

            @Override
            public void onError(String error) {
                currentUser = null;
            }
        });
    }

    /**
     * Loads local custom block palettes or components from Sketchware storage.
     */
    private void loadLocalItems() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<LocalItem> localList = new ArrayList<>();
            try {
                if (StoreItem.TYPE_BLOCK.equals(selectedType)) {
                    File paletteFile = ExtraBlockFile.EXTRA_BLOCKS_PALETTE_FILE;
                    if (paletteFile.exists() && paletteFile.isFile()) {
                        String content = FileUtil.readFile(paletteFile.getAbsolutePath());
                        if (content != null && !content.isEmpty()) {
                            Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
                            List<Map<String, Object>> palettes = GsonUtils.getGson().fromJson(content, listType);
                            if (palettes != null) {
                                for (int i = 0; i < palettes.size(); i++) {
                                    Map<String, Object> map = palettes.get(i);
                                    if (map != null) {
                                        Object nameObj = map.get("name");
                                        String paletteName = nameObj != null ? nameObj.toString() : "Palette " + (i + 1);
                                        localList.add(new LocalItem("palette_" + i, paletteName, StoreItem.TYPE_BLOCK));
                                    }
                                }
                            }
                        }
                    }
                    if (localList.isEmpty()) {
                        File blockFile = ExtraBlockFile.EXTRA_BLOCKS_DATA_FILE;
                        if (blockFile.exists() && blockFile.isFile()) {
                            String content = FileUtil.readFile(blockFile.getAbsolutePath());
                            if (content != null && !content.isEmpty()) {
                                Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
                                List<Map<String, Object>> customBlocks = GsonUtils.getGson().fromJson(content, listType);
                                Set<String> paletteIndices = new HashSet<>();
                                if (customBlocks != null) {
                                    for (Map<String, Object> block : customBlocks) {
                                        if (block != null && block.get("palette") != null) {
                                            paletteIndices.add(block.get("palette").toString());
                                        }
                                    }
                                    for (String pIndex : paletteIndices) {
                                        localList.add(new LocalItem("palette_" + pIndex, "Palette (Index " + pIndex + ")", StoreItem.TYPE_BLOCK));
                                    }
                                }
                            }
                        }
                    }
                } else if (StoreItem.TYPE_COMPONENT.equals(selectedType)) {
                    String componentFilePath = wq.getCustomComponent();
                    File componentFile = new File(componentFilePath);
                    if (componentFile.exists() && componentFile.isFile()) {
                        String content = FileUtil.readFile(componentFile.getAbsolutePath());
                        if (content != null && !content.isEmpty()) {
                            Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
                            List<Map<String, Object>> customComponents = GsonUtils.getGson().fromJson(content, listType);
                            if (customComponents != null) {
                                for (int i = 0; i < customComponents.size(); i++) {
                                    Map<String, Object> map = customComponents.get(i);
                                    if (map != null) {
                                        Object nameObj = map.get("name");
                                        Object typeObj = map.get("type");
                                        String displayName = nameObj != null ? nameObj.toString() : (typeObj != null ? typeObj.toString() : "Component " + (i + 1));
                                        localList.add(new LocalItem("comp_" + i, displayName, StoreItem.TYPE_COMPONENT));
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            runOnUiThread(() -> {
                localItemsAdapter.setItems(localList);
                updatePreview(new ArrayList<>());
            });
        });
    }

    private void updatePreview(List<LocalItem> selectedItems) {
        if (selectedItems == null || selectedItems.isEmpty()) {
            tvPreviewSelected.setText("No items selected yet. Select items above to include them in the pack.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < selectedItems.size(); i++) {
                sb.append(selectedItems.get(i).getName());
                if (i < selectedItems.size() - 1) {
                    sb.append(", ");
                }
            }
            tvPreviewSelected.setText(sb.toString());
        }
    }

    private void submitPack() {
        tilName.setError(null);
        tilDescription.setError(null);

        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String description = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";
        String tagsRaw = etTags.getText() != null ? etTags.getText().toString().trim() : "";

        // Validation
        if (TextUtils.isEmpty(name)) {
            tilName.setError("Pack name is required");
            etName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(description) || description.length() < 15) {
            tilDescription.setError("Description must be at least 15 characters long");
            etDescription.requestFocus();
            return;
        }

        List<LocalItem> selectedLocalItems = localItemsAdapter.getSelectedItems();
        if (selectedLocalItems.isEmpty()) {
            Toast.makeText(this, "Please select at least one local item to include", Toast.LENGTH_SHORT).show();
            return;
        }

        // Tags parsing
        List<String> tags = new ArrayList<>();
        if (!TextUtils.isEmpty(tagsRaw)) {
            String[] split = tagsRaw.split("[,\\s]+");
            tags.addAll(Arrays.asList(split));
        }

        // Selected content names
        List<String> contentNames = new ArrayList<>();
        for (LocalItem item : selectedLocalItems) {
            contentNames.add(item.getName());
        }

        String author = currentUser != null ? currentUser.getUsername() : "SketchwareDev";

        String exportedJson = "{\"blocks\":[],\"components\":[]}";
        try {
            if (StoreItem.TYPE_BLOCK.equals(selectedType)) {
                File blockFile = ExtraBlockFile.EXTRA_BLOCKS_DATA_FILE;
                File paletteFile = ExtraBlockFile.EXTRA_BLOCKS_PALETTE_FILE;
                if (blockFile.exists()) {
                    String content = FileUtil.readFile(blockFile.getAbsolutePath());
                    List<Map<String, Object>> allBlocks = GsonUtils.getGson().fromJson(content, new TypeToken<List<Map<String, Object>>>(){}.getType());
                    
                    List<Map<String, Object>> palettes = new ArrayList<>();
                    if (paletteFile.exists()) {
                        String paletteContent = FileUtil.readFile(paletteFile.getAbsolutePath());
                        palettes = GsonUtils.getGson().fromJson(paletteContent, new TypeToken<List<Map<String, Object>>>(){}.getType());
                    }

                    List<Map<String, Object>> selectedBlocks = new ArrayList<>();
                    if (allBlocks != null) {
                        for (int i = 0; i < allBlocks.size(); i++) {
                            Map<String, Object> map = allBlocks.get(i);
                            if (map != null && map.get("palette") != null) {
                                String blockPalette = map.get("palette").toString();
                                for (LocalItem selected : selectedLocalItems) {
                                    boolean matches = false;
                                    if (palettes != null) {
                                        for (int p = 0; p < palettes.size(); p++) {
                                            Map<String, Object> pMap = palettes.get(p);
                                            if (pMap != null && pMap.get("name") != null) {
                                                String pName = pMap.get("name").toString();
                                                if (selected.getName().equals(pName) && blockPalette.equals(String.valueOf(p + 9))) {
                                                    matches = true;
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                    if (!matches && blockPalette.equals(selected.getId().replace("palette_", ""))) {
                                        matches = true;
                                    }
                                    if (matches) {
                                        selectedBlocks.add(map);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    Map<String, Object> exportMap = new HashMap<>();
                    exportMap.put("blocks", selectedBlocks);
                    exportedJson = GsonUtils.getGson().toJson(exportMap);
                }
            } else if (StoreItem.TYPE_COMPONENT.equals(selectedType)) {
                String componentFilePath = wq.getCustomComponent();
                File componentFile = new File(componentFilePath);
                if (componentFile.exists()) {
                    String content = FileUtil.readFile(componentFile.getAbsolutePath());
                    List<Map<String, Object>> allComps = GsonUtils.getGson().fromJson(content, new TypeToken<List<Map<String, Object>>>(){}.getType());
                    List<Map<String, Object>> selectedComps = new ArrayList<>();
                    if (allComps != null) {
                        for (int i = 0; i < allComps.size(); i++) {
                            Map<String, Object> map = allComps.get(i);
                            if (map != null) {
                                Object nameObj = map.get("name");
                                Object typeObj = map.get("type");
                                String displayName = nameObj != null ? nameObj.toString() : (typeObj != null ? typeObj.toString() : "Component " + (i + 1));
                                for (LocalItem selected : selectedLocalItems) {
                                    if (selected.getName().equals(displayName)) {
                                        selectedComps.add(map);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    Map<String, Object> exportMap = new HashMap<>();
                    exportMap.put("components", selectedComps);
                    exportedJson = GsonUtils.getGson().toJson(exportMap);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        StoreItem newItem = new StoreItem(
                null,
                name,
                author,
                description,
                selectedType,
                tags,
                0,
                System.currentTimeMillis(),
                false, // pending approval
                exportedJson,
                contentNames
        );

        // Show progress state
        btnSubmit.setEnabled(false);
        btnSubmit.setText("");
        pbSubmit.setVisibility(View.VISIBLE);

        repository.uploadItem(newItem, new RepositoryCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                pbSubmit.setVisibility(View.GONE);
                btnSubmit.setEnabled(true);
                btnSubmit.setText("Submit Pack for Approval");

                // Pending approval success state
                new MaterialAlertDialogBuilder(UploadActivity.this)
                        .setTitle("Pending Approval")
                        .setMessage("Your " + selectedType.toLowerCase() + " pack '" + name + "' has been submitted successfully!\n\nIt is now pending administrator review before it will be visible in the public store.")
                        .setPositiveButton("OK", (dialog, which) -> finish())
                        .setCancelable(false)
                        .show();
            }

            @Override
            public void onError(String error) {
                pbSubmit.setVisibility(View.GONE);
                btnSubmit.setEnabled(true);
                btnSubmit.setText("Submit Pack for Approval");
                Toast.makeText(UploadActivity.this, "Failed to upload: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
