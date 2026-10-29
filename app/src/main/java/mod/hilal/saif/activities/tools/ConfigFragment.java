package mod.hilal.saif.activities.tools;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.topjohnwu.superuser.Shell;

import a.a.a.qA;
import mod.hey.studios.util.Helper;
import sketchware.plus.R;
import sketchware.plus.databinding.DialogCreateNewFileLayoutBinding;
import sketchware.plus.databinding.FragmentConfigBinding;
import sketchware.plus.utility.HapticManager;

public class ConfigFragment extends qA {

    private FragmentConfigBinding binding;
    private View snackbarView;

    public void setSnackbarView(View snackbarView) {
        this.snackbarView = snackbarView;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentConfigBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeSettings();
        setupClickListeners();
        applySegmentedStyle();
    }

    private void initializeSettings() {
        // Switches
        binding.switchBuiltInBlocks.setChecked(ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_SHOW_BUILT_IN_BLOCKS));
        binding.switchAlwaysShowBlocks.setChecked(ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_ALWAYS_SHOW_BLOCKS));
        binding.switchShowEverySingleBlock.setChecked(ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_SHOW_EVERY_SINGLE_BLOCK));
        binding.switchUseAsdHighlighter.setChecked(ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_USE_ASD_HIGHLIGHTER));

        binding.switchRootAutoInstall.setChecked(ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_ROOT_AUTO_INSTALL_PROJECTS));
        binding.switchRootAutoOpen.setChecked(ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_ROOT_AUTO_OPEN_AFTER_INSTALLING));
        binding.switchUseNewVersionControl.setChecked(ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_USE_NEW_VERSION_CONTROL));

        // Summaries
        updateBackupDirSummary();
        updateBackupFilenameSummary();
    }

    private void setupClickListeners() {
        // Built-in blocks
        binding.cardBuiltInBlocks.setOnClickListener(v -> binding.switchBuiltInBlocks.setChecked(!binding.switchBuiltInBlocks.isChecked()));
        binding.switchBuiltInBlocks.setOnCheckedChangeListener((buttonView, isChecked) -> {
            HapticManager.vibrateSwitch(buttonView, isChecked);
            ConfigActivity.setSetting(ConfigActivity.SETTING_SHOW_BUILT_IN_BLOCKS, isChecked);
        });

        // Always show variable blocks
        binding.cardAlwaysShowBlocks.setOnClickListener(v -> binding.switchAlwaysShowBlocks.setChecked(!binding.switchAlwaysShowBlocks.isChecked()));
        binding.switchAlwaysShowBlocks.setOnCheckedChangeListener((buttonView, isChecked) -> {
            HapticManager.vibrateSwitch(buttonView, isChecked);
            ConfigActivity.setSetting(ConfigActivity.SETTING_ALWAYS_SHOW_BLOCKS, isChecked);
        });

        // Show every single block
        binding.cardShowEverySingleBlock.setOnClickListener(v -> binding.switchShowEverySingleBlock.setChecked(!binding.switchShowEverySingleBlock.isChecked()));
        binding.switchShowEverySingleBlock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            HapticManager.vibrateSwitch(buttonView, isChecked);
            ConfigActivity.setSetting(ConfigActivity.SETTING_SHOW_EVERY_SINGLE_BLOCK, isChecked);
        });

        // Block text input highlighting
        binding.cardUseAsdHighlighter.setOnClickListener(v -> binding.switchUseAsdHighlighter.setChecked(!binding.switchUseAsdHighlighter.isChecked()));
        binding.switchUseAsdHighlighter.setOnCheckedChangeListener((buttonView, isChecked) -> {
            HapticManager.vibrateSwitch(buttonView, isChecked);
            ConfigActivity.setSetting(ConfigActivity.SETTING_USE_ASD_HIGHLIGHTER, isChecked);
        });

        // Root auto install
        binding.cardRootAutoInstall.setOnClickListener(v -> binding.switchRootAutoInstall.setChecked(!binding.switchRootAutoInstall.isChecked()));
        binding.switchRootAutoInstall.setOnCheckedChangeListener((buttonView, isChecked) -> {
            HapticManager.vibrateSwitch(buttonView, isChecked);
            ConfigActivity.setSetting(ConfigActivity.SETTING_ROOT_AUTO_INSTALL_PROJECTS, isChecked);
            if (isChecked) {
                Shell.getShell(shell -> {
                    if (!shell.isRoot()) {
                        if (snackbarView != null) {
                            Snackbar.make(snackbarView, "Couldn't acquire root access", BaseTransientBottomBar.LENGTH_SHORT).show();
                        }
                        binding.switchRootAutoInstall.setChecked(false);
                    }
                });
            }
        });

        // Root auto open after install
        binding.cardRootAutoOpen.setOnClickListener(v -> binding.switchRootAutoOpen.setChecked(!binding.switchRootAutoOpen.isChecked()));
        binding.switchRootAutoOpen.setOnCheckedChangeListener((buttonView, isChecked) -> {
            HapticManager.vibrateSwitch(buttonView, isChecked);
            ConfigActivity.setSetting(ConfigActivity.SETTING_ROOT_AUTO_OPEN_AFTER_INSTALLING, isChecked);
        });

        // Use new version control
        binding.cardUseNewVersionControl.setOnClickListener(v -> binding.switchUseNewVersionControl.setChecked(!binding.switchUseNewVersionControl.isChecked()));
        binding.switchUseNewVersionControl.setOnCheckedChangeListener((buttonView, isChecked) -> {
            HapticManager.vibrateSwitch(buttonView, isChecked);
            ConfigActivity.setSetting(ConfigActivity.SETTING_USE_NEW_VERSION_CONTROL, isChecked);
        });

        // Backup directory card
        binding.cardBackupDir.setOnClickListener(v -> showBackupDirDialog());

        // Backup filename format card
        binding.cardBackupFilename.setOnClickListener(v -> showBackupFilenameDialog());
    }

    private void updateBackupDirSummary() {
        String path = ConfigActivity.getBackupPath();
        binding.tvBackupDirSummary.setText(path.isEmpty() ? "The default directory is /Internal storage/.sketchware/backups/." : path);
    }

    private void updateBackupFilenameSummary() {
        String filename = ConfigActivity.getBackupFileName();
        binding.tvBackupFilenameSummary.setText(filename.isEmpty() ? "Default is \"$projectName v$versionName ($pkgName, $versionCode) $time(yyyy-MM-dd'T'HHmmss)\"" : filename);
    }

    private void showBackupDirDialog() {
        DialogCreateNewFileLayoutBinding dialogBinding = DialogCreateNewFileLayoutBinding.inflate(getLayoutInflater());
        dialogBinding.inputText.setText(ConfigActivity.getBackupPath());
        dialogBinding.chipGroupTypes.setVisibility(View.GONE);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogBinding.getRoot())
                .setTitle("Backup directory")
                .setMessage("Directory inside /Internal storage/, e.g. .sketchware/backups")
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.common_word_save, null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(
                    Helper.getDialogDismissListener(dialogInterface));
            Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(view -> {
                ConfigActivity.setSetting(ConfigActivity.SETTING_BACKUP_DIRECTORY, Helper.getText(dialogBinding.inputText));
                updateBackupDirSummary();
                dialog.dismiss();
            });

            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            dialogBinding.inputText.requestFocus();
        });
        dialog.show();
    }

    private void showBackupFilenameDialog() {
        DialogCreateNewFileLayoutBinding dialogBinding = DialogCreateNewFileLayoutBinding.inflate(getLayoutInflater());
        dialogBinding.chipGroupTypes.setVisibility(View.GONE);
        dialogBinding.inputText.setText(ConfigActivity.getBackupFileName());

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogBinding.getRoot())
                .setTitle("Backup filename format")
                .setMessage("This defines how SWB backup files get named.\n" +
                        "Available variables:\n" +
                        " - $projectName - Project name\n" +
                        " - $versionCode - App version code\n" +
                        " - $versionName - App version name\n" +
                        " - $pkgName - App package name\n" +
                        " - $timeInMs - Time during backup in milliseconds\n" +
                        "\n" +
                        "Additionally, you can format your own time like this using Java's date formatter syntax:\n" +
                        "$time(yyyy-MM-dd'T'HHmmss)\n")
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.common_word_save, null)
                .setNeutralButton(R.string.common_word_reset, (dialogInterface, which) -> {
                    ConfigActivity.DataStore.getInstance().putString(ConfigActivity.SETTING_BACKUP_FILENAME, null);
                    updateBackupFilenameSummary();
                    if (snackbarView != null) {
                        Snackbar.make(snackbarView, "Reset to default complete.", BaseTransientBottomBar.LENGTH_SHORT).show();
                    }
                })
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(
                    Helper.getDialogDismissListener(dialog));
            Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(view -> {
                ConfigActivity.setSetting(ConfigActivity.SETTING_BACKUP_FILENAME, Helper.getText(dialogBinding.inputText));
                updateBackupFilenameSummary();
                dialog.dismiss();
            });
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            dialogBinding.inputText.requestFocus();
        });
        dialog.show();
    }

    private void applySegmentedStyle() {
        if (!isAdded() || binding == null) return;

        // Section 1: Block Settings
        sketchware.plus.utility.SegmentedListHelper.applySegmentedGroup(requireContext(),
                binding.cardBuiltInBlocks,
                binding.cardAlwaysShowBlocks,
                binding.cardShowEverySingleBlock,
                binding.cardUseAsdHighlighter
        );

        // Section 2: Advanced Settings
        sketchware.plus.utility.SegmentedListHelper.applySegmentedGroup(requireContext(),
                binding.cardRootAutoInstall,
                binding.cardRootAutoOpen,
                binding.cardUseNewVersionControl
        );

        // Section 3: Backup Settings
        sketchware.plus.utility.SegmentedListHelper.applySegmentedGroup(requireContext(),
                binding.cardBackupDir,
                binding.cardBackupFilename
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
