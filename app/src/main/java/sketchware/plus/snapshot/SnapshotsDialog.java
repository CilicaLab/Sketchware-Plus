package sketchware.plus.snapshot;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import sketchware.plus.R;

public class SnapshotsDialog {

    private final Context context;
    private final String scId;
    private final SnapshotManager manager;

    private AlertDialog dialog;
    private MaterialSwitch switchSuperSnapshot;
    private TextView tvStorageInfo;
    private TextView tvEmptySnapshots;
    private ProgressBar progressLoading;
    private RecyclerView recyclerSnapshots;
    private SnapshotsAdapter adapter;

    public SnapshotsDialog(@NonNull Context context, @NonNull String scId) {
        this.context = context;
        this.scId = scId;
        this.manager = SnapshotManager.getInstance(context);
    }

    public void show() {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_snapshots, null);

        switchSuperSnapshot = view.findViewById(R.id.switch_super_snapshot);
        tvStorageInfo = view.findViewById(R.id.tv_storage_info);
        tvEmptySnapshots = view.findViewById(R.id.tv_empty_snapshots);
        progressLoading = view.findViewById(R.id.progress_loading);
        recyclerSnapshots = view.findViewById(R.id.recycler_snapshots);

        MaterialButton btnCreateManual = view.findViewById(R.id.btn_create_manual);
        MaterialButton btnClearAll = view.findViewById(R.id.btn_clear_all);

        switchSuperSnapshot.setChecked(manager.isSnapshotEnabled(scId));
        switchSuperSnapshot.setOnCheckedChangeListener((buttonView, isChecked) -> {
            manager.setSnapshotEnabled(scId, isChecked);
            Toast.makeText(context, isChecked ? "Super Snapshot enabled" : "Super Snapshot disabled", Toast.LENGTH_SHORT).show();
        });

        recyclerSnapshots.setLayoutManager(new LinearLayoutManager(context));
        adapter = new SnapshotsAdapter();
        recyclerSnapshots.setAdapter(adapter);

        btnCreateManual.setOnClickListener(v -> createManualSnapshot());
        btnClearAll.setOnClickListener(v -> confirmClearAll());

        dialog = new MaterialAlertDialogBuilder(context)
                .setView(view)
                .setPositiveButton("Close", null)
                .create();

        dialog.show();

        loadSnapshots();
        updateStorageSize();
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private void updateStorageSize() {
        new Thread(() -> {
            long size = manager.getStorageSize(scId);
            String formatted = Formatter.formatFileSize(context, size);
            mainHandler.post(() -> {
                if (dialog != null && dialog.isShowing()) {
                    tvStorageInfo.setText("Storage size: " + formatted);
                }
            });
        }).start();
    }

    private void loadSnapshots() {
        progressLoading.setVisibility(View.VISIBLE);
        recyclerSnapshots.setVisibility(View.GONE);
        tvEmptySnapshots.setVisibility(View.GONE);

        new Thread(() -> {
            List<SnapshotEntry> snapshots = manager.getSnapshots(scId);
            mainHandler.post(() -> {
                if (dialog != null && dialog.isShowing()) {
                    progressLoading.setVisibility(View.GONE);
                    if (snapshots.isEmpty()) {
                        tvEmptySnapshots.setVisibility(View.VISIBLE);
                        recyclerSnapshots.setVisibility(View.GONE);
                    } else {
                        tvEmptySnapshots.setVisibility(View.GONE);
                        recyclerSnapshots.setVisibility(View.VISIBLE);
                        adapter.setItems(snapshots);
                    }
                }
            });
        }).start();
    }

    private void createManualSnapshot() {
        progressLoading.setVisibility(View.VISIBLE);
        manager.createManualSnapshot(scId, "Manual Snapshot", new SnapshotManager.SnapshotCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(context, "Snapshot created successfully", Toast.LENGTH_SHORT).show();
                loadSnapshots();
                updateStorageSize();
            }

            @Override
            public void onError(String message) {
                progressLoading.setVisibility(View.GONE);
                Toast.makeText(context, "Failed: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmClearAll() {
        new MaterialAlertDialogBuilder(context)
                .setTitle("Clear All Snapshots")
                .setMessage("Are you sure you want to delete all snapshots for this project? This cannot be undone.")
                .setPositiveButton("Clear All", (d, which) -> {
                    progressLoading.setVisibility(View.VISIBLE);
                    manager.clearSnapshots(scId, new SnapshotManager.SnapshotCallback() {
                        @Override
                        public void onSuccess() {
                            Toast.makeText(context, "All snapshots cleared", Toast.LENGTH_SHORT).show();
                            loadSnapshots();
                            updateStorageSize();
                        }

                        @Override
                        public void onError(String message) {
                            progressLoading.setVisibility(View.GONE);
                            Toast.makeText(context, "Failed: " + message, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmRestore(SnapshotEntry entry) {
        new MaterialAlertDialogBuilder(context)
                .setTitle("Restore Snapshot")
                .setMessage("Restore project to '" + entry.getName() + "'?\n\nA safety snapshot of your current project state will be created automatically before restoring.")
                .setPositiveButton("Restore", (d, which) -> performRestore(entry))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performRestore(SnapshotEntry entry) {
        progressLoading.setVisibility(View.VISIBLE);
        manager.restoreSnapshot(scId, entry, new SnapshotManager.SnapshotCallback() {
            @Override
            public void onSuccess() {
                progressLoading.setVisibility(View.GONE);
                Toast.makeText(context, "Project restored successfully!", Toast.LENGTH_LONG).show();
                if (context instanceof Activity) {
                    Activity act = (Activity) context;
                    if (act.getClass().getSimpleName().contains("DesignActivity")) {
                        act.recreate();
                    }
                }
                loadSnapshots();
                updateStorageSize();
            }

            @Override
            public void onError(String message) {
                progressLoading.setVisibility(View.GONE);
                new MaterialAlertDialogBuilder(context)
                        .setTitle("Restore Failed")
                        .setMessage("Failed to restore project: " + message)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    private void deleteSingleSnapshot(SnapshotEntry entry) {
        progressLoading.setVisibility(View.VISIBLE);
        manager.deleteSnapshot(scId, entry.getId(), new SnapshotManager.SnapshotCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(context, "Snapshot deleted", Toast.LENGTH_SHORT).show();
                loadSnapshots();
                updateStorageSize();
            }

            @Override
            public void onError(String message) {
                progressLoading.setVisibility(View.GONE);
                Toast.makeText(context, "Failed to delete snapshot", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- Inner Adapter Class ---

    private class SnapshotsAdapter extends RecyclerView.Adapter<SnapshotsAdapter.ViewHolder> {

        private final List<SnapshotEntry> items = new ArrayList<>();
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault());

        public void setItems(List<SnapshotEntry> newItems) {
            items.clear();
            if (newItems != null) {
                items.addAll(newItems);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_snapshot, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SnapshotEntry entry = items.get(position);

            holder.tvName.setText(entry.getName());
            holder.tvDate.setText(dateFormat.format(new Date(entry.getTimestamp())));

            int count = entry.getChangedFiles() != null ? entry.getChangedFiles().size() : 0;
            holder.tvFilesCount.setText(count + " file" + (count == 1 ? "" : "s") + " tracked");

            holder.btnRestore.setOnClickListener(v -> confirmRestore(entry));
            holder.btnDelete.setOnClickListener(v -> deleteSingleSnapshot(entry));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView tvName;
            final TextView tvDate;
            final TextView tvFilesCount;
            final MaterialButton btnRestore;
            final ImageButton btnDelete;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_snapshot_name);
                tvDate = itemView.findViewById(R.id.tv_snapshot_date);
                tvFilesCount = itemView.findViewById(R.id.tv_changed_files_count);
                btnRestore = itemView.findViewById(R.id.btn_restore);
                btnDelete = itemView.findViewById(R.id.btn_delete);
            }
        }
    }
}
