package sketchware.plus.store.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import a.a.a.wq;
import dev.aldi.sayuti.block.ExtraBlockFile;
import mod.hey.studios.editor.manage.block.v2.BlockLoader;
import mod.hilal.saif.components.ComponentsHandler;
import sketchware.plus.R;
import sketchware.plus.store.adapters.ContentNamesAdapter;
import sketchware.plus.store.dialogs.ConflictDialog;
import sketchware.plus.store.dialogs.ReportDialog;
import sketchware.plus.store.models.StoreItem;
import sketchware.plus.store.network.SupabaseClient;
import sketchware.plus.store.repository.RepositoryCallback;
import sketchware.plus.store.repository.RepositoryProvider;
import sketchware.plus.store.repository.StoreRepository;
import sketchware.plus.store.widget.StateView;
import sketchware.plus.utility.FileUtil;

public class ItemDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ITEM_ID = "extra_item_id";
    public static final String EXTRA_STORE_ITEM = "extra_store_item";

    public static void start(Context context, String itemId) {
        Intent intent = new Intent(context, ItemDetailActivity.class);
        intent.putExtra(EXTRA_ITEM_ID, itemId);
        context.startActivity(intent);
    }

    public static void start(Context context, StoreItem item) {
        Intent intent = new Intent(context, ItemDetailActivity.class);
        intent.putExtra(EXTRA_STORE_ITEM, item);
        intent.putExtra(EXTRA_ITEM_ID, item.getId());
        context.startActivity(intent);
    }

    private StoreRepository repository;
    private StoreItem currentItem;
    private String itemId;

    private MaterialToolbar toolbar;
    private TextView tvBadgeType;
    private TextView tvItemName;
    private TextView tvItemAuthor;
    private TextView tvItemDownloads;
    private TextView tvItemTags;
    private TextView tvItemDesc;
    private TextView tvContentsHeader;
    private RecyclerView rvContents;
    private MaterialButton btnReport;
    private MaterialButton btnInstall;
    private StateView stateView;
    private ContentNamesAdapter contentsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_detail);

        repository = RepositoryProvider.getRepository(this);

        if (getIntent().hasExtra(EXTRA_STORE_ITEM)) {
            currentItem = (StoreItem) getIntent().getSerializableExtra(EXTRA_STORE_ITEM);
        }
        if (getIntent().hasExtra(EXTRA_ITEM_ID)) {
            itemId = getIntent().getStringExtra(EXTRA_ITEM_ID);
        } else if (currentItem != null) {
            itemId = currentItem.getId();
        }

        initViews();
        setupToolbar();
        setupRecyclerView();
        setupListeners();

        if (currentItem != null) {
            displayItemDetails(currentItem);
        } else if (itemId != null) {
            loadItemDetails();
        } else {
            stateView.showError("Invalid item selected", v -> finish());
        }
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvBadgeType = findViewById(R.id.tv_badge_type);
        tvItemName = findViewById(R.id.tv_item_name);
        tvItemAuthor = findViewById(R.id.tv_item_author);
        tvItemDownloads = findViewById(R.id.tv_item_downloads);
        tvItemTags = findViewById(R.id.tv_item_tags);
        tvItemDesc = findViewById(R.id.tv_item_desc);
        tvContentsHeader = findViewById(R.id.tv_contents_header);
        rvContents = findViewById(R.id.rv_contents);
        btnReport = findViewById(R.id.btn_report);
        btnInstall = findViewById(R.id.btn_install);
        stateView = findViewById(R.id.state_view);
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
        contentsAdapter = new ContentNamesAdapter();
        rvContents.setLayoutManager(new LinearLayoutManager(this));
        rvContents.setAdapter(contentsAdapter);
    }

    private void setupListeners() {
        btnReport.setOnClickListener(v -> {
            if (itemId != null) {
                ReportDialog.show(this, itemId, () -> {
                    Snackbar.make(btnReport, "Report recorded", Snackbar.LENGTH_SHORT).show();
                });
            }
        });

        btnInstall.setOnClickListener(v -> {
            if (currentItem == null) return;
            checkConflictAndInstall();
        });
    }

    private void loadItemDetails() {
        stateView.showLoading("Loading details...");
        repository.getItemDetails(itemId, new RepositoryCallback<StoreItem>() {
            @Override
            public void onSuccess(StoreItem item) {
                stateView.showContent();
                currentItem = item;
                displayItemDetails(item);
            }

            @Override
            public void onError(String error) {
                stateView.showError(error, v -> loadItemDetails());
            }
        });
    }

    private void displayItemDetails(StoreItem item) {
        tvBadgeType.setText(item.getType() != null ? item.getType().toUpperCase(Locale.US) : "PACK");
        tvItemName.setText(item.getName());

        String dateStr = new SimpleDateFormat("MMM dd, yyyy", Locale.US).format(new Date(item.getCreatedAt()));
        tvItemAuthor.setText("Uploaded by " + (item.getAuthor() != null ? item.getAuthor() : "Anonymous") + " • " + dateStr);

        String downloadsText = NumberFormat.getInstance(Locale.US).format(item.getDownloads()) + " downloads";
        tvItemDownloads.setText(downloadsText);

        tvItemDesc.setText(item.getDescription());

        // Tags
        if (item.getTags() != null && !item.getTags().isEmpty()) {
            StringBuilder tagsSb = new StringBuilder();
            for (String tag : item.getTags()) {
                tagsSb.append("#").append(tag).append("  ");
            }
            tvItemTags.setText(tagsSb.toString().trim());
            tvItemTags.setVisibility(View.VISIBLE);
        } else {
            tvItemTags.setVisibility(View.GONE);
        }

        // Contents
        int count = item.getContentNames() != null ? item.getContentNames().size() : 0;
        String typeLabel = StoreItem.TYPE_COMPONENT.equalsIgnoreCase(item.getType()) ? "Components" : "Blocks";
        tvContentsHeader.setText("Included " + typeLabel + " (" + count + ")");

        if (item.getContentNames() != null) {
            contentsAdapter.setNames(item.getContentNames());
        }
    }

    private boolean validateImportJson(String jsonStr) {
        if (jsonStr == null) return false;
        // Size limit check (max 5MB)
        if (jsonStr.getBytes().length > 5 * 1024 * 1024) {
            Toast.makeText(this, "Import failed: File exceeds size limit", Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            JsonObject obj = SupabaseClient.getInstance().getGson().fromJson(jsonStr, JsonObject.class);
            if (obj == null) {
                Toast.makeText(this, "Import failed: Invalid JSON structure", Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void checkConflictAndInstall() {
        if (currentItem == null) return;
        String json = currentItem.getJson();
        if (json == null || json.isEmpty() || json.equals("{}")) {
            installPack("installed successfully", json);
            return;
        }

        if (!validateImportJson(json)) {
            return;
        }

        boolean hasConflict = checkLocalConflict(currentItem);

        if (hasConflict) {
            ConflictDialog.show(this, currentItem.getName(), action -> {
                switch (action) {
                    case REPLACE:
                        installPack("replaced", json);
                        break;
                    case RENAME:
                        showRenameDialog(json);
                        break;
                    case SKIP:
                        Toast.makeText(this, "Installation skipped", Toast.LENGTH_SHORT).show();
                        break;
                }
            });
        } else {
            installPack("installed successfully", json);
        }
    }

    private boolean checkLocalConflict(StoreItem item) {
        try {
            JsonObject obj = SupabaseClient.getInstance().getGson().fromJson(item.getJson(), JsonObject.class);
            if (obj == null) return false;

            if (StoreItem.TYPE_BLOCK.equalsIgnoreCase(item.getType()) && obj.has("blocks")) {
                File paletteFile = ExtraBlockFile.EXTRA_BLOCKS_PALETTE_FILE;
                if (paletteFile.exists()) {
                    String paletteContent = FileUtil.readFile(paletteFile.getAbsolutePath());
                    if (paletteContent != null && paletteContent.contains(item.getName())) {
                        return true;
                    }
                }
            } else if (StoreItem.TYPE_COMPONENT.equalsIgnoreCase(item.getType()) && obj.has("components")) {
                File compFile = new File(wq.getCustomComponent());
                if (compFile.exists()) {
                    String compContent = FileUtil.readFile(compFile.getAbsolutePath());
                    if (compContent != null && compContent.contains(item.getName())) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void showRenameDialog(String originalJson) {
        final TextInputEditText input = new TextInputEditText(this);
        input.setText(currentItem.getName() + " (2)");

        new MaterialAlertDialogBuilder(this)
                .setTitle("Rename Item")
                .setMessage("Enter a new name for this pack to avoid conflict:")
                .setView(input)
                .setPositiveButton("Install", (dialog, which) -> {
                    String newName = input.getText() != null ? input.getText().toString().trim() : "";
                    if (!newName.isEmpty()) {
                        currentItem.setName(newName);
                        String updatedJson = originalJson;
                        try {
                            JsonObject obj = SupabaseClient.getInstance().getGson().fromJson(originalJson, JsonObject.class);
                            if (obj.has("blocks") && obj.getAsJsonArray("blocks").size() > 0) {
                                obj.getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("name", newName);
                            } else if (obj.has("components") && obj.getAsJsonArray("components").size() > 0) {
                                obj.getAsJsonArray("components").get(0).getAsJsonObject().addProperty("name", newName);
                            }
                            updatedJson = SupabaseClient.getInstance().getGson().toJson(obj);
                        } catch (Exception ignored) {}
                        installPack("renamed & installed", updatedJson);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void installPack(String statusMessage, String jsonToInstall) {
        try {
            JsonObject obj = SupabaseClient.getInstance().getGson().fromJson(jsonToInstall, JsonObject.class);
            if (obj != null) {
                if (StoreItem.TYPE_BLOCK.equalsIgnoreCase(currentItem.getType()) && obj.has("blocks")) {
                    File blockFile = ExtraBlockFile.EXTRA_BLOCKS_DATA_FILE;
                    File paletteFile = ExtraBlockFile.EXTRA_BLOCKS_PALETTE_FILE;
                    if (blockFile.getParentFile() != null) {
                        blockFile.getParentFile().mkdirs();
                    }
                    if (paletteFile.getParentFile() != null) {
                        paletteFile.getParentFile().mkdirs();
                    }

                    List<Map<String, Object>> existingBlocks = new ArrayList<>();
                    if (blockFile.exists()) {
                        String content = FileUtil.readFile(blockFile.getAbsolutePath());
                        if (content != null && !content.isEmpty()) {
                            List<Map<String, Object>> parsed = SupabaseClient.getInstance().getGson().fromJson(content, new TypeToken<List<Map<String, Object>>>(){}.getType());
                            if (parsed != null) existingBlocks.addAll(parsed);
                        }
                    }

                    List<Map<String, Object>> newBlocks = SupabaseClient.getInstance().getGson().fromJson(obj.getAsJsonArray("blocks"), new TypeToken<List<Map<String, Object>>>(){}.getType());
                    if (newBlocks != null) {
                        existingBlocks.addAll(newBlocks);
                        FileUtil.writeFile(blockFile.getAbsolutePath(), SupabaseClient.getInstance().getGson().toJson(existingBlocks));
                    }

                    List<Map<String, Object>> existingPalettes = new ArrayList<>();
                    if (paletteFile.exists()) {
                        String pContent = FileUtil.readFile(paletteFile.getAbsolutePath());
                        if (pContent != null && !pContent.isEmpty()) {
                            List<Map<String, Object>> pParsed = SupabaseClient.getInstance().getGson().fromJson(pContent, new TypeToken<List<Map<String, Object>>>(){}.getType());
                            if (pParsed != null) existingPalettes.addAll(pParsed);
                        }
                    }
                    Map<String, Object> newPalette = new HashMap<>();
                    newPalette.put("name", currentItem.getName());
                    newPalette.put("color", "#ff8a55d7");
                    existingPalettes.add(newPalette);
                    FileUtil.writeFile(paletteFile.getAbsolutePath(), SupabaseClient.getInstance().getGson().toJson(existingPalettes));

                    BlockLoader.refresh();
                } else if (StoreItem.TYPE_COMPONENT.equalsIgnoreCase(currentItem.getType()) && obj.has("components")) {
                    File compFile = new File(wq.getCustomComponent());
                    if (compFile.getParentFile() != null) {
                        compFile.getParentFile().mkdirs();
                    }

                    List<Map<String, Object>> existingComps = new ArrayList<>();
                    if (compFile.exists()) {
                        String content = FileUtil.readFile(compFile.getAbsolutePath());
                        if (content != null && !content.isEmpty()) {
                            List<Map<String, Object>> parsed = SupabaseClient.getInstance().getGson().fromJson(content, new TypeToken<List<Map<String, Object>>>(){}.getType());
                            if (parsed != null) existingComps.addAll(parsed);
                        }
                    }

                    List<Map<String, Object>> newComps = SupabaseClient.getInstance().getGson().fromJson(obj.getAsJsonArray("components"), new TypeToken<List<Map<String, Object>>>(){}.getType());
                    if (newComps != null) {
                        existingComps.addAll(newComps);
                        FileUtil.writeFile(compFile.getAbsolutePath(), SupabaseClient.getInstance().getGson().toJson(existingComps));
                    }

                    ComponentsHandler.refreshCachedCustomComponents();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        currentItem.setDownloads(currentItem.getDownloads() + 1);
        if (itemId != null) {
            repository.incrementDownloads(itemId);
        } else if (currentItem.getId() != null) {
            repository.incrementDownloads(currentItem.getId());
        }
        tvItemDownloads.setText(NumberFormat.getInstance(Locale.US).format(currentItem.getDownloads()) + " downloads");
        btnInstall.setText("Installed");
        btnInstall.setEnabled(false);
        Snackbar.make(btnInstall, "Pack " + statusMessage + " successfully!", Snackbar.LENGTH_LONG).show();
    }
}
