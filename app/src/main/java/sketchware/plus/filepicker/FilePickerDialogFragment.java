package sketchware.plus.filepicker;

import android.app.Dialog;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import sketchware.plus.R;
import sketchware.plus.utility.FileUtil;

public class FilePickerDialogFragment extends BottomSheetDialogFragment {

    private final FilePickerOptions options;
    private final FilePickerCallback callback;

    private File currentDirectory;
    private FilePickerAdapter adapter;
    private TextView tvCurrentPath;
    private int sortMode = 0; // 0=A-Z, 1=Z-A, 2=Modified Newest, 3=Modified Oldest, 4=Size Largest, 5=Size Smallest

    public FilePickerDialogFragment(FilePickerOptions options, FilePickerCallback callback) {
        this.options = options != null ? options : new FilePickerOptions();
        this.callback = callback;
        this.currentDirectory = this.options.getInitialDirectory();
        if (this.currentDirectory == null || !this.currentDirectory.exists() || !this.currentDirectory.canRead()) {
            this.currentDirectory = Environment.getExternalStorageDirectory();
            if (this.currentDirectory == null || !this.currentDirectory.exists()) {
                this.currentDirectory = new File("/storage/emulated/0");
            }
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) dialogInterface;
            View bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
                layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                bottomSheet.setLayoutParams(layoutParams);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_file_picker, container, false);

        tvCurrentPath = view.findViewById(R.id.tv_current_path);
        TextView tvTitle = view.findViewById(R.id.tv_title);
        if (tvTitle != null && options.getTitle() != null) {
            tvTitle.setText(options.getTitle());
        }

        RecyclerView recyclerView = view.findViewById(R.id.recycler_files);
        MaterialButton btnUp = view.findViewById(R.id.btn_up);
        MaterialButton btnSort = view.findViewById(R.id.btn_sort);
        MaterialButton btnSelect = view.findViewById(R.id.btn_select);
        MaterialButton btnCancel = view.findViewById(R.id.btn_cancel);
        TextView tvSelection = view.findViewById(R.id.tv_selection);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
        adapter = new FilePickerAdapter(options.isMultipleSelection(), options.getSelectionMode(), file -> {
            if (file.isDirectory()) {
                loadDirectory(file);
            }
        });
        recyclerView.setAdapter(adapter);

        adapter.setOnSelectionChangedListener(count -> {
            btnSelect.setEnabled(count > 0);
            if (tvSelection != null) {
                if (count == 0) {
                    tvSelection.setText("Nothing selected");
                } else if (count == 1) {
                    tvSelection.setText("1 item selected");
                } else {
                    tvSelection.setText(count + " items selected");
                }
            }
        });

        btnUp.setOnClickListener(v -> {
            File parent = currentDirectory.getParentFile();
            if (parent != null) {
                String path = parent.getAbsolutePath();
                if (path.equals("/") || path.equals("/system") || path.equals("/data")) {
                    Toast.makeText(requireContext(), "Cannot navigate above storage root", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (parent.canRead() || parent.listFiles() != null) {
                    loadDirectory(parent);
                } else {
                    checkPermissionAndReload(parent);
                }
            } else {
                Toast.makeText(requireContext(), "Cannot read parent directory", Toast.LENGTH_SHORT).show();
            }
        });

        btnSort.setOnClickListener(v -> {
            String[] sortOptions = new String[]{
                    "A to Z",
                    "Z to A",
                    "Last modified (Newest first)",
                    "Last modified (Oldest first)",
                    "Size (Largest first)",
                    "Size (Smallest first)"
            };
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Sort by")
                    .setSingleChoiceItems(sortOptions, sortMode, (dialog, which) -> {
                        sortMode = which;
                        loadDirectory(currentDirectory);
                        dialog.dismiss();
                    })
                    .show();
        });

        btnSelect.setOnClickListener(v -> {
            if (callback != null) {
                List<File> selected = new ArrayList<>(adapter.getSelectedFiles());
                callback.onFilesSelected(selected);
                if (!selected.isEmpty()) {
                    callback.onFileSelected(selected.get(0));
                }
            }
            dismiss();
        });

        btnCancel.setOnClickListener(v -> dismiss());

        loadDirectory(currentDirectory);
        return view;
    }

    private void loadDirectory(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            Toast.makeText(requireContext(), "Directory does not exist or is invalid", Toast.LENGTH_SHORT).show();
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            checkPermissionAndReload(dir);
            return;
        }

        currentDirectory = dir;
        tvCurrentPath.setText(currentDirectory.getAbsolutePath());

        List<File> fileList = new ArrayList<>();
        List<File> dirList = new ArrayList<>();

        for (File f : files) {
            if (f.isDirectory()) {
                dirList.add(f);
            } else if (isValidExtension(f)) {
                fileList.add(f);
            }
        }

        switch (sortMode) {
            case 0: // A to Z
                Collections.sort(dirList, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                Collections.sort(fileList, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                break;
            case 1: // Z to A
                Collections.sort(dirList, (a, b) -> b.getName().compareToIgnoreCase(a.getName()));
                Collections.sort(fileList, (a, b) -> b.getName().compareToIgnoreCase(a.getName()));
                break;
            case 2: // Last modified (Newest first)
                Collections.sort(dirList, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                Collections.sort(fileList, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                break;
            case 3: // Last modified (Oldest first / reversed)
                Collections.sort(dirList, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                Collections.sort(fileList, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                break;
            case 4: // Size (Largest first)
                Collections.sort(dirList, (a, b) -> Long.compare(b.length(), a.length()));
                Collections.sort(fileList, (a, b) -> Long.compare(b.length(), a.length()));
                break;
            case 5: // Size (Smallest first)
                Collections.sort(dirList, (a, b) -> Long.compare(a.length(), b.length()));
                Collections.sort(fileList, (a, b) -> Long.compare(a.length(), b.length()));
                break;
        }

        List<File> combined = new ArrayList<>();
        combined.addAll(dirList);
        combined.addAll(fileList);

        adapter.setItems(combined);
    }

    private void checkPermissionAndReload(File dir) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Permission Required")
                    .setMessage("Reading directory '" + dir.getName() + "' requires 'All Files Access' permission on Android 11+. Would you like to grant it?")
                    .setPositiveButton("Grant", (dialog, which) -> {
                        try {
                            FileUtil.requestAllFilesAccessPermission(requireContext());
                        } catch (Exception e) {
                            Toast.makeText(requireContext(), "Failed to open permission settings", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            View view = getView();
            if (view != null) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.REJECT);
            }
        }
    }

    private boolean isValidExtension(File file) {
        String[] ext = options.getExtensions();
        if (ext == null || ext.length == 0) return true;
        String name = file.getName().toLowerCase(Locale.ROOT);
        for (String e : ext) {
            if (name.endsWith("." + e.toLowerCase(Locale.ROOT).replace(".", ""))) {
                return true;
            }
        }
        return false;
    }
}
