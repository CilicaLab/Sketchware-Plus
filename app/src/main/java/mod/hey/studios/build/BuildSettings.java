package mod.hey.studios.build;

import java.io.Serializable;

import mod.hey.studios.project.ProjectSettings;
import sketchware.plus.utility.FileUtil;

public class BuildSettings extends ProjectSettings implements Serializable {

    public static final String SETTING_ANDROID_JAR_PATH = "android_jar";
    public static final String SETTING_CLASSPATH = "classpath";
    public static final String SETTING_DEXER = "dexer";
    public static final String SETTING_JAVA_VERSION = "java_ver";
    public static final String SETTING_NO_HTTP_LEGACY = "no_http_legacy";
    public static final String SETTING_NO_WARNINGS = "no_warn";
    public static final String SETTING_ENABLE_LOGCAT = "enable_logcat";

    public static final String SETTING_DEXER_D8 = "D8";
    public static final String SETTING_DEXER_DX = "Dx";
    public static final String SETTING_JAVA_VERSION_1_7 = "1.7";
    public static final String SETTING_JAVA_VERSION_1_8 = "1.8";
    public static final String SETTING_JAVA_VERSION_1_9 = "1.9";
    public static final String SETTING_JAVA_VERSION_10 = "10";
    public static final String SETTING_JAVA_VERSION_11 = "11";
    public static final String SETTING_JAVA_VERSION_17 = "17";

    public static final String SETTING_TEST_RUN_SIGNING_TYPE = "test_run_signing_type";
    public static final String SETTING_TEST_RUN_SIGNING_TYPE_TESTKEY = "testkey";
    public static final String SETTING_TEST_RUN_SIGNING_TYPE_CUSTOM = "custom";

    public static final String SETTING_TEST_RUN_KEYSTORE_PATH = "test_run_keystore_path";
    public static final String SETTING_TEST_RUN_KEYSTORE_PASSWORD = "test_run_keystore_pass";
    public static final String SETTING_TEST_RUN_KEYSTORE_ALIAS = "test_run_keystore_alias";
    public static final String SETTING_TEST_RUN_KEY_PASSWORD = "test_run_key_pass";
    public static final String SETTING_TEST_RUN_ALIAS_PASSWORD = "test_run_alias_pass";
    public static final String SETTING_TEST_RUN_SIGNING_ALGORITHM = "test_run_signing_algorithm";
    public static final String SETTING_TEST_RUN_SIGN_V1 = "test_run_sign_v1";
    public static final String SETTING_TEST_RUN_SIGN_V2 = "test_run_sign_v2";
    public static final String SETTING_TEST_RUN_SIGN_V3 = "test_run_sign_v3";

    public BuildSettings(String sc_id) {
        super(sc_id);
    }

    @Override
    public String getPath() {
        return FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/build_config";
    }
}
