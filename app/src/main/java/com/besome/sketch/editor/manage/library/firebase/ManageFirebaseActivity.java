package com.besome.sketch.editor.manage.library.firebase;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;

import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.DB;
import a.a.a.GB;
import a.a.a.bB;
import a.a.a.lC;
import a.a.a.yB;
import org.json.JSONArray;
import org.json.JSONObject;
import sketchware.plus.filepicker.FilePickerCallback;
import sketchware.plus.filepicker.FilePickerDialogFragment;
import sketchware.plus.filepicker.FilePickerOptions;
import mod.hey.studios.util.Helper;
import sketchware.plus.R;
import sketchware.plus.utility.FileUtil;
import sketchware.plus.utility.SketchwareUtil;

public class ManageFirebaseActivity extends BaseAppCompatActivity implements View.OnClickListener {
    private final String realtime_db = "realtime_db";
    private final String app_id = "app_id";
    private final String api_key = "api_key";
    private final String storage_bucket = "storage_bucket";
    private MaterialSwitch libSwitch;
    private TextView tvProjectId;
    private TextView tvAppId;
    private TextView tvApiKey;
    private TextView tvStorageUrl;
    private DB s = null;
    private ProjectLibraryBean firebaseLibraryBean;
    private final ActivityResultLauncher<Intent> openSettings = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            assert result.getData() != null;
            initializeLibrary(result.getData().getParcelableExtra("firebase"));
        }
    });
    private String sc_id;

    private void initializeLibrary(ProjectLibraryBean libraryBean) {
        firebaseLibraryBean = libraryBean;
        configure();
    }

    private void goToConsole() {
        if (GB.h(getApplicationContext())) {
            try {
                Uri consoleUrl = Uri.parse("https://console.firebase.google.com");
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setData(consoleUrl);
                startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
                downloadChromeDialog();
            }
        } else {
            bB.a(getApplicationContext(), Helper.getResString(R.string.common_message_check_network), bB.TOAST_NORMAL).show();
        }
    }

    private void openDoc() {
        try {
            s.a("P1I15", true);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setData(Uri.parse("https://sketchware-pro.vercel.app/docs/components/Firebase/connect-to-firebase"));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            downloadChromeDialog();
        }
    }

    private void downloadChromeDialog() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setIcon(R.drawable.chrome_96);
        dialog.setTitle(Helper.getResString(R.string.title_compatible_chrome_browser));
        dialog.setMessage(Helper.getResString(R.string.message_compatible_chrome_brower));
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_ok), (v, which) -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://details?id=com.android.chrome"));
            startActivity(intent);
            v.dismiss();
        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.show();
    }

    private void configureLibraryDialog() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(Helper.getResString(R.string.common_word_warning));
        dialog.setIcon(R.drawable.delete_96);
        dialog.setMessage(Helper.getResString(R.string.design_library_firebase_dialog_description_confirm_uncheck_firebase));
        dialog.setCancelable(false);
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_delete), (v, which) -> {
            firebaseLibraryBean.useYn = "N";
            libSwitch.setChecked(false);
            v.dismiss();
        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), (v, which) -> {
            libSwitch.setChecked(true);
            v.dismiss();
        });
        dialog.show();
    }

    private void finishWithResult() {
        Intent i = new Intent();
        i.putExtra("firebase", firebaseLibraryBean);
        setResult(RESULT_OK, i);
        finish();
    }

    private static boolean has(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private boolean hasFirebaseDetails() {
        return has(firebaseLibraryBean.data)
                || has(firebaseLibraryBean.reserved1)
                || has(firebaseLibraryBean.reserved2)
                || has(firebaseLibraryBean.reserved3);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.btn_console) {
            goToConsole();
        } else if (id == R.id.btn_import) {
            showImportJsonDialog();
        } else if (id == R.id.layout_switch) {
            //Enable Disable Firebase
            if (libSwitch.isChecked() || hasFirebaseDetails()) {
                libSwitch.setChecked(!libSwitch.isChecked());
                if ("Y".equals(firebaseLibraryBean.useYn) && !libSwitch.isChecked()) {
                    configureLibraryDialog();
                } else {
                    firebaseLibraryBean.useYn = "Y";
                }
            } else {
                SketchwareUtil.toast("Configure Firebase settings first, either by importing google-services.json, " +
                        "or by manually entering the project's details.", Toast.LENGTH_LONG);
            }
        }
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manage_library_manage_firebase);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        findViewById(R.id.layout_main_logo).setVisibility(View.GONE);
        getSupportActionBar().setTitle(Helper.getResString(R.string.design_library_firebase_title_firebase_manager));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finishWithResult());
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishWithResult();
            }
        });

        s = new DB(getApplicationContext(), "P1");
        firebaseLibraryBean = getIntent().getParcelableExtra("firebase");
        sc_id = savedInstanceState != null ? savedInstanceState.getString("sc_id") : getIntent().getStringExtra("sc_id");
        LinearLayout switchLayout = findViewById(R.id.layout_switch);
        switchLayout.setOnClickListener(this);
        libSwitch = findViewById(R.id.lib_switch);
        ((TextView) findViewById(R.id.tv_enable)).setText(Helper.getResString(R.string.design_library_settings_title_enabled));
        ((TextView) findViewById(R.id.tv_title_project_id)).setText(
                Helper.getResString(R.string.design_library_firebase_title_project_id));
        ((TextView) findViewById(R.id.tv_title_app_id)).setText(
                Helper.getResString(R.string.design_library_firebase_title_app_id));
        ((TextView) findViewById(R.id.tv_title_api_key)).setText(
                Helper.getResString(R.string.design_library_firebase_title_api_key));
        ((TextView) findViewById(R.id.tv_title_storage_url)).setText(
                Helper.getResString(R.string.design_library_firebase_title_storage_bucket_url));
        tvProjectId = findViewById(R.id.tv_project_id);
        tvAppId = findViewById(R.id.tv_app_id);
        tvApiKey = findViewById(R.id.tv_api_key);
        tvStorageUrl = findViewById(R.id.tv_storage_url);
        Button btnImport = findViewById(R.id.btn_import);
        btnImport.setOnClickListener(this);
        Button btnConsole = findViewById(R.id.btn_console);
        btnConsole.setText(Helper.getResString(R.string.design_library_firebase_button_goto_firebase_console));
        btnConsole.setOnClickListener(this);
        configure();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Help").setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_help)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Config").setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_settings)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem menuItem) {
        String title = menuItem.getTitle().toString();
        switch (title) {
            case "Help":
                openDoc();
                break;

            case "Config":
                toFirebaseActivity();
                break;

            default:
                return false;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    private void toFirebaseActivity() {
        Intent intent = new Intent(getApplicationContext(), FirebaseActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("firebase", firebaseLibraryBean);
        openSettings.launch(intent);
    }

    private void configure() {
        // Enable the switch only if valid Firebase details are present
        boolean hasDetails = hasFirebaseDetails();
        libSwitch.setChecked(hasDetails);
        firebaseLibraryBean.useYn = hasDetails ? "Y" : "N";

        if (firebaseLibraryBean.data != null) {
            tvProjectId.setText(firebaseLibraryBean.data); //Realtime Database Url
        }

        if (firebaseLibraryBean.reserved1 != null) {
            tvAppId.setText(firebaseLibraryBean.reserved1); //APP ID
        }

        if (firebaseLibraryBean.reserved2 != null) {
            tvApiKey.setText(firebaseLibraryBean.reserved2); //API Key
        }

        if (firebaseLibraryBean.reserved3 != null) {
            tvStorageUrl.setText(firebaseLibraryBean.reserved3); //Storage Bucket
        }
    }

    public void configureImportFirebaseConfigFromJson(String type, String value) {
        if (value != null) {
            value = value.trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1).trim();
            } else {
                if (value.startsWith("\"")) value = value.substring(1).trim();
                if (value.endsWith("\"")) value = value.substring(0, value.length() - 1).trim();
            }
            if (value.endsWith(",")) value = value.substring(0, value.length() - 1).trim();
        } else {
            value = "";
        }
        switch (type) {
            case realtime_db:
                firebaseLibraryBean.data = value;
                break;
            case app_id:
                firebaseLibraryBean.reserved1 = value;
                break;
            case api_key:
                firebaseLibraryBean.reserved2 = value;
                break;
            case storage_bucket:
                firebaseLibraryBean.reserved3 = value;
                break;
        }
    }

    private void showImportJsonDialog() {
        FilePickerOptions options = new FilePickerOptions();
        options.setExtensions(new String[]{"json"});
        options.setTitle("Select your google-services.json");

        FilePickerCallback callback = new FilePickerCallback() {
            @Override
            public void onFilesSelected(@NonNull List<? extends File> files) {
                if (!files.isEmpty()) {
                    File file = files.get(0);
                    String filePath = file.getAbsolutePath();
                    String fileContent = FileUtil.readFile(filePath);
                    parseDataFromGoogleServicesJson(fileContent);
                }
            }
        };

        new FilePickerDialogFragment(options, callback).show(getSupportFragmentManager(), "filePicker");
    }

    private void parseDataFromGoogleServicesJson(String _data) {
        StringBuilder notFoundLog = new StringBuilder();
        boolean hasNullConfig = false;
        notFoundLog.append("The google-services.json file you selected does not contain the following configurations:\n");

        String storageBucket = "";
        String appId = "";
        String apiKey = "";
        String realtimeDb = "";
        String projectId = "";

        try {
            JSONObject root = new JSONObject(_data);
            if (root.has("project_info")) {
                JSONObject projectInfo = root.getJSONObject("project_info");
                storageBucket = projectInfo.optString("storage_bucket", "");
                realtimeDb = projectInfo.optString("firebase_url", "");
                projectId = projectInfo.optString("project_id", "");
            }

            if (root.has("client")) {
                JSONArray clientArray = root.getJSONArray("client");
                JSONObject selectedClient = null;

                String currentPkg = "";
                try {
                    if (sc_id != null && !sc_id.isEmpty()) {
                        currentPkg = yB.c(lC.b(sc_id), "my_sc_pkg_name");
                    }
                } catch (Exception ignored) {}

                if (!currentPkg.isEmpty()) {
                    for (int i = 0; i < clientArray.length(); i++) {
                        JSONObject clientObj = clientArray.getJSONObject(i);
                        if (clientObj.has("client_info")) {
                            JSONObject clientInfo = clientObj.getJSONObject("client_info");
                            if (clientInfo.has("android_client_info")) {
                                String pkg = clientInfo.getJSONObject("android_client_info").optString("package_name", "");
                                if (currentPkg.equals(pkg)) {
                                    selectedClient = clientObj;
                                    break;
                                }
                            }
                        }
                    }
                }

                if (selectedClient == null && clientArray.length() > 0) {
                    selectedClient = clientArray.getJSONObject(0);
                }

                if (selectedClient != null) {
                    if (selectedClient.has("client_info")) {
                        appId = selectedClient.getJSONObject("client_info").optString("mobilesdk_app_id", "");
                    }
                    if (selectedClient.has("api_key")) {
                        JSONArray apiKeyArray = selectedClient.getJSONArray("api_key");
                        if (apiKeyArray.length() > 0) {
                            apiKey = apiKeyArray.getJSONObject(0).optString("current_key", "");
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (storageBucket.isEmpty()) {
            Matcher storageBucketMatcher = Pattern.compile("\"storage_bucket\": ?\"([^\"]+)\"").matcher(_data);
            if (storageBucketMatcher.find()) storageBucket = storageBucketMatcher.group(1);
        }
        if (appId.isEmpty()) {
            Matcher appIdMatcher = Pattern.compile("\"mobilesdk_app_id\": ?\"([^\"]+)\"").matcher(_data);
            if (appIdMatcher.find()) appId = appIdMatcher.group(1);
        }
        if (apiKey.isEmpty()) {
            Matcher apiKeyMatcher = Pattern.compile("\"current_key\": ?\"([^\"]+)\"").matcher(_data);
            if (apiKeyMatcher.find()) apiKey = apiKeyMatcher.group(1);
        }
        if (realtimeDb.isEmpty()) {
            Matcher rtdbMatcher = Pattern.compile("\"firebase_url\": ?\"([^\"]+)\"").matcher(_data);
            if (rtdbMatcher.find()) realtimeDb = rtdbMatcher.group(1);
        }
        if (projectId.isEmpty()) {
            Matcher projectIdMatcher = Pattern.compile("\"project_id\": ?\"([^\"]+)\"").matcher(_data);
            if (projectIdMatcher.find()) projectId = projectIdMatcher.group(1);
        }

        if (!storageBucket.isEmpty()) {
            configureImportFirebaseConfigFromJson(storage_bucket, storageBucket);
        } else {
            notFoundLog.append("StorageBucket, ");
            configureImportFirebaseConfigFromJson(storage_bucket, "");
            hasNullConfig = true;
        }

        if (!appId.isEmpty()) {
            configureImportFirebaseConfigFromJson(app_id, appId);
        } else {
            notFoundLog.append("App ID, ");
            configureImportFirebaseConfigFromJson(app_id, "");
            hasNullConfig = true;
        }

        if (!apiKey.isEmpty()) {
            configureImportFirebaseConfigFromJson(api_key, apiKey);
        } else {
            notFoundLog.append("API Key, ");
            configureImportFirebaseConfigFromJson(api_key, "");
            hasNullConfig = true;
        }

        if (!realtimeDb.isEmpty()) {
            configureImportFirebaseConfigFromJson(realtime_db, realtimeDb.replace("https://", ""));
        } else if (!projectId.isEmpty()) {
            configureImportFirebaseConfigFromJson(realtime_db, projectId);
        } else {
            configureImportFirebaseConfigFromJson(realtime_db, "");
        }

        if (firebaseLibraryBean.reserved1 != null && !firebaseLibraryBean.reserved1.isEmpty()) {
            libSwitch.setChecked(true);
            firebaseLibraryBean.useYn = "Y";
        }

        if (hasNullConfig) SketchwareUtil.toastError(notFoundLog.toString());
        configure();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", sc_id);
        super.onSaveInstanceState(outState);
    }
}
