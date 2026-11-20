package sketchware.plus.dialogs;

import static mod.hey.studios.build.BuildSettings.SETTING_ANDROID_JAR_PATH;
import static mod.hey.studios.build.BuildSettings.SETTING_CLASSPATH;
import static mod.hey.studios.build.BuildSettings.SETTING_DEXER;
import static mod.hey.studios.build.BuildSettings.SETTING_ENABLE_LOGCAT;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION_10;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION_11;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION_17;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION_1_7;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION_1_8;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION_1_9;
import static mod.hey.studios.build.BuildSettings.SETTING_NO_HTTP_LEGACY;
import static mod.hey.studios.build.BuildSettings.SETTING_NO_WARNINGS;
import static mod.hey.studios.build.BuildSettings.SETTING_TEST_RUN_ALIAS_PASSWORD;
import static mod.hey.studios.build.BuildSettings.SETTING_TEST_RUN_KEYSTORE_ALIAS;
import static mod.hey.studios.build.BuildSettings.SETTING_TEST_RUN_KEYSTORE_PASSWORD;
import static mod.hey.studios.build.BuildSettings.SETTING_TEST_RUN_KEYSTORE_PATH;
import static mod.hey.studios.build.BuildSettings.SETTING_TEST_RUN_KEY_PASSWORD;
import static mod.hey.studios.build.BuildSettings.SETTING_TEST_RUN_SIGNING_ALGORITHM;
import static mod.hey.studios.build.BuildSettings.SETTING_TEST_RUN_SIGNING_TYPE;
import static mod.hey.studios.build.BuildSettings.SETTING_TEST_RUN_SIGNING_TYPE_CUSTOM;
import static mod.hey.studios.build.BuildSettings.SETTING_TEST_RUN_SIGNING_TYPE_TESTKEY;
import static mod.hey.studios.build.BuildSettings.SETTING_TEST_RUN_SIGN_V1;
import static mod.hey.studios.build.BuildSettings.SETTING_TEST_RUN_SIGN_V2;
import static mod.hey.studios.build.BuildSettings.SETTING_TEST_RUN_SIGN_V3;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.File;

import mod.hey.studios.build.BuildSettings;
import sketchware.plus.databinding.ProjectConfigLayoutBinding;
import sketchware.plus.filepicker.FilePickerCallback;
import sketchware.plus.filepicker.FilePickerDialogFragment;
import sketchware.plus.filepicker.FilePickerOptions;
import sketchware.plus.utility.SketchwareUtil;

public class BuildSettingsBottomSheet extends BottomSheetDialogFragment {
    public static final String TAG = BuildSettingsBottomSheet.class.getSimpleName();
    private static int totalViews = 0;

    private static final int VIEW_ANDROIR_JAR_PATH = totalViews++;
    private static final int VIEW_CLASS_PATH = totalViews++;
    private static final int VIEW_DEXER = totalViews++;
    private static final int VIEW_JAVA_VERSION = totalViews++;
    private static final int VIEW_NO_WARNINGS = totalViews++;
    private static final int VIEW_NO_HTTP_LEGACY = totalViews++;
    private static final int VIEW_ENABLE_LOGCAT = totalViews++;
    private View[] views;

    private ProjectConfigLayoutBinding binding;
    private BuildSettings projectSettings;

    public static BuildSettingsBottomSheet newInstance(String sc_id) {
        BuildSettingsBottomSheet sheet = new BuildSettingsBottomSheet();
        Bundle arguments = new Bundle();
        arguments.putString("sc_id", sc_id);
        sheet.setArguments(arguments);
        return sheet;
    }

    public static String[] getAvailableJavaVersions() {
        return new String[]{SETTING_JAVA_VERSION_1_7, SETTING_JAVA_VERSION_1_8, SETTING_JAVA_VERSION_1_9, SETTING_JAVA_VERSION_10, SETTING_JAVA_VERSION_11, SETTING_JAVA_VERSION_17};
    }

    public static void handleJavaVersionChange(String choice) {
        if (!choice.equals(SETTING_JAVA_VERSION_1_7)) {
            SketchwareUtil.toast("Don't forget to enable D8 to be able to compile Java 8+ code");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle arguments = getArguments();
        projectSettings = new BuildSettings(arguments.getString("sc_id"));
        views = new View[totalViews];
    }

    @Override
    public void onStart() {
        super.onStart();
        android.app.Dialog dialog = getDialog();
        if (dialog instanceof com.google.android.material.bottomsheet.BottomSheetDialog) {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet)
                        .setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            }
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = ProjectConfigLayoutBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews();

        binding.noWarnings.setOnClickListener(v -> binding.cbNoWarnings.performClick());
        binding.noHttpLegacy.setOnClickListener(v -> binding.cbNoHttpLegacy.performClick());
        binding.enableLogcat.setOnClickListener(v -> binding.cbEnableLogcat.performClick());

        binding.tilAndroidJar.getEditText().setText(projectSettings.getValue(SETTING_ANDROID_JAR_PATH, ""));
        binding.tilClasspath.getEditText().setText(projectSettings.getValue(SETTING_CLASSPATH, ""));

        setRadioGroupOptions(binding.rgDexer, new String[]{"Dx", "D8"}, SETTING_DEXER, "Dx");
        setRadioGroupOptions(binding.rgJavaVersion, getAvailableJavaVersions(), SETTING_JAVA_VERSION, "1.7");

        setCheckboxValue(binding.cbNoWarnings, SETTING_NO_WARNINGS, true);
        setCheckboxValue(binding.cbNoHttpLegacy, SETTING_NO_HTTP_LEGACY, false);
        setCheckboxValue(binding.cbEnableLogcat, SETTING_ENABLE_LOGCAT, true);

        String signingType = projectSettings.getValue(SETTING_TEST_RUN_SIGNING_TYPE, SETTING_TEST_RUN_SIGNING_TYPE_TESTKEY);
        boolean isCustom = SETTING_TEST_RUN_SIGNING_TYPE_CUSTOM.equals(signingType);
        if (isCustom) {
            binding.rbTestRunCustom.setChecked(true);
            binding.cardTestRunCustomKeystore.setVisibility(View.VISIBLE);
        } else {
            binding.rbTestRunTestkey.setChecked(true);
            binding.cardTestRunCustomKeystore.setVisibility(View.GONE);
        }

        binding.rgTestRunSigning.setOnCheckedChangeListener((group, checkedId) -> {
            boolean customChecked = checkedId == binding.rbTestRunCustom.getId();
            binding.cardTestRunCustomKeystore.setVisibility(customChecked ? View.VISIBLE : View.GONE);
        });

        // Setup signing algorithm dropdown adapter with presets
        String[] algoPresets = new String[]{"SHA256withRSA", "SHA1withRSA", "SHA256withECDSA", "SHA512withRSA", "MD5withRSA"};
        ArrayAdapter<String> algoAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, algoPresets);
        binding.actTestRunSigningAlgorithm.setAdapter(algoAdapter);

        // Load existing values into UI
        binding.etTestRunKeystorePath.setText(projectSettings.getValue(SETTING_TEST_RUN_KEYSTORE_PATH, ""));
        binding.etTestRunKeystorePassword.setText(projectSettings.getValue(SETTING_TEST_RUN_KEYSTORE_PASSWORD, ""));
        binding.etTestRunKeystoreAlias.setText(projectSettings.getValue(SETTING_TEST_RUN_KEYSTORE_ALIAS, ""));
        binding.etTestRunAliasPassword.setText(projectSettings.getValue(SETTING_TEST_RUN_ALIAS_PASSWORD, ""));
        binding.actTestRunSigningAlgorithm.setText(projectSettings.getValue(SETTING_TEST_RUN_SIGNING_ALGORITHM, "SHA256withRSA"), false);

        binding.cbTestRunV1.setChecked(projectSettings.getValue(SETTING_TEST_RUN_SIGN_V1, "true").equals("true"));
        binding.cbTestRunV2.setChecked(projectSettings.getValue(SETTING_TEST_RUN_SIGN_V2, "true").equals("true"));
        binding.cbTestRunV3.setChecked(projectSettings.getValue(SETTING_TEST_RUN_SIGN_V3, "true").equals("true"));

        binding.btnSelectTestRunKeystore.setOnClickListener(v -> {
            FilePickerOptions options = new FilePickerOptions();
            FilePickerCallback callback = new FilePickerCallback() {
                @Override
                public void onFileSelected(@NonNull File file) {
                    binding.etTestRunKeystorePath.setText(file.getAbsolutePath());
                }
            };
            new FilePickerDialogFragment(options, callback).show(getParentFragmentManager(), "filePickerTestRunKeystore");
        });

        binding.btnCancel.setOnClickListener(v -> dismiss());
        binding.btnSave.setOnClickListener(v -> {
            projectSettings.setValues(views);
            String selectedType = binding.rbTestRunCustom.isChecked()
                    ? SETTING_TEST_RUN_SIGNING_TYPE_CUSTOM
                    : SETTING_TEST_RUN_SIGNING_TYPE_TESTKEY;
            projectSettings.setValue(SETTING_TEST_RUN_SIGNING_TYPE, selectedType);
            projectSettings.setValue(SETTING_TEST_RUN_KEYSTORE_PATH, binding.etTestRunKeystorePath.getText() != null ? binding.etTestRunKeystorePath.getText().toString().trim() : "");
            projectSettings.setValue(SETTING_TEST_RUN_KEYSTORE_PASSWORD, binding.etTestRunKeystorePassword.getText() != null ? binding.etTestRunKeystorePassword.getText().toString() : "");
            projectSettings.setValue(SETTING_TEST_RUN_KEYSTORE_ALIAS, binding.etTestRunKeystoreAlias.getText() != null ? binding.etTestRunKeystoreAlias.getText().toString().trim() : "");
            projectSettings.setValue(SETTING_TEST_RUN_ALIAS_PASSWORD, binding.etTestRunAliasPassword.getText() != null ? binding.etTestRunAliasPassword.getText().toString() : "");
            projectSettings.setValue(SETTING_TEST_RUN_KEY_PASSWORD, binding.etTestRunAliasPassword.getText() != null ? binding.etTestRunAliasPassword.getText().toString() : "");
            projectSettings.setValue(SETTING_TEST_RUN_SIGNING_ALGORITHM, binding.actTestRunSigningAlgorithm.getText() != null ? binding.actTestRunSigningAlgorithm.getText().toString().trim() : "SHA256withRSA");
            projectSettings.setValue(SETTING_TEST_RUN_SIGN_V1, Boolean.toString(binding.cbTestRunV1.isChecked()));
            projectSettings.setValue(SETTING_TEST_RUN_SIGN_V2, Boolean.toString(binding.cbTestRunV2.isChecked()));
            projectSettings.setValue(SETTING_TEST_RUN_SIGN_V3, Boolean.toString(binding.cbTestRunV3.isChecked()));
            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void initializeViews() {
        binding.tilAndroidJar.getEditText().setTag(SETTING_ANDROID_JAR_PATH);
        binding.tilClasspath.getEditText().setTag(SETTING_CLASSPATH);
        binding.rgDexer.setTag(SETTING_DEXER);
        binding.rgJavaVersion.setTag(SETTING_JAVA_VERSION);
        binding.cbNoWarnings.setTag(SETTING_NO_WARNINGS);
        binding.cbNoHttpLegacy.setTag(SETTING_NO_HTTP_LEGACY);
        binding.cbEnableLogcat.setTag(SETTING_ENABLE_LOGCAT);

        views[VIEW_ANDROIR_JAR_PATH] = binding.tilAndroidJar.getEditText();
        views[VIEW_CLASS_PATH] = binding.tilClasspath.getEditText();
        views[VIEW_DEXER] = binding.rgDexer;
        views[VIEW_ENABLE_LOGCAT] = binding.cbEnableLogcat;
        views[VIEW_JAVA_VERSION] = binding.rgJavaVersion;
        views[VIEW_NO_HTTP_LEGACY] = binding.cbNoHttpLegacy;
        views[VIEW_NO_WARNINGS] = binding.cbNoWarnings;
    }

    private void setRadioGroupOptions(RadioGroup radioGroup, String[] options, String key, String defaultValue) {
        radioGroup.removeAllViews();
        String value = projectSettings.getValue(key, defaultValue);
        for (String option : options) {
            RadioButton radioButton = new RadioButton(radioGroup.getContext());
            radioButton.setText(option);
            radioButton.setId(View.generateViewId());
            radioButton.setLayoutParams(new RadioGroup.LayoutParams(0, -2, 1f));
            if (value.equals(option)) {
                radioButton.setChecked(true);
            }
            radioButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!isChecked) return;
                if (key.equals(SETTING_JAVA_VERSION)) {
                    handleJavaVersionChange(option);
                }
            });
            radioGroup.addView(radioButton);
        }
    }

    private void setCheckboxValue(CheckBox checkBox, String key, boolean defaultValue) {
        String value = projectSettings.getValue(key, defaultValue ? "true" : "false");
        checkBox.setChecked(value.equals("true"));

        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (key.equals(SETTING_NO_HTTP_LEGACY)) {
                    SketchwareUtil.toast("Note that this option may cause issues if RequestNetwork component is used");
                }
            }
        });
    }
}
