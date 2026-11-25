package mod.jbk.build;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import a.a.a.Jp;
import a.a.a.ProjectBuilder;
import mod.jbk.util.LogUtil;
import sketchware.plus.SketchApplication;

public class BuiltInLibraries {
    private static final String TAG = "BuiltInLibraries";

    public static final File EXTRACTED_COMPILE_ASSETS_PATH = new File(SketchApplication.getContext().getFilesDir(), "libs");
    public static final File EXTRACTED_BUILT_IN_LIBRARIES_PATH = new File(EXTRACTED_COMPILE_ASSETS_PATH, "libs");
    public static final File EXTRACTED_BUILT_IN_LIBRARY_DEX_FILES_PATH = new File(EXTRACTED_COMPILE_ASSETS_PATH, "dexs");

    private static final String ASSET_BASE = "libs/";
    private static final String DEXS_ARCHIVE = "dexs.zip";
    private static final String LIBS_ARCHIVE = "libs.zip";
    private static final String TESTKEY_ARCHIVE = "testkey.zip";
    private static final String ANDROID_JAR_ARCHIVE = "android.jar.zip";
    private static final String CORE_LAMBDA_STUBS_JAR = "core-lambda-stubs.jar";

    private static final int MAX_EXTRACT_ATTEMPTS = 3;
    private static final long MIN_FREE_SPACE_BYTES = 64L * 1024 * 1024;

    // None final so that field values won't be optimized into code, and to allow easy changing of library names due to that

    public static String ANDROIDX_ACTIVITY = "activity-1.11.0";
    public static String ANDROIDX_ANNOTATION_EXPERIMENTAL = "annotation-experimental-1.4.1";
    public static String ANDROIDX_ANNOTATION_JVM = "annotation-jvm-1.9.1";
    public static String ANDROIDX_APPCOMPAT = "appcompat-1.7.1";
    public static String ANDROIDX_APPCOMPAT_RESOURCES = "appcompat-resources-1.7.1";
    public static String ANDROIDX_ASYNCLAYOUTINFLATER = "asynclayoutinflater-1.0.0";
    public static String ANDROIDX_BROWSER = "browser-1.4.0";
    public static String ANDROIDX_CARDVIEW = "cardview-1.0.0";
    public static String ANDROIDX_COLLECTION_JVM = "collection-jvm-1.4.2";
    public static String ANDROIDX_CONCURRENT_FUTURES = "concurrent-futures-1.0.0";
    public static String ANDROIDX_CONSTRAINTLAYOUT = "constraintlayout-2.2.1";
    public static String ANDROIDX_CONSTRAINTLAYOUT_CORE = "constraintlayout-core-1.1.1";
    public static String ANDROIDX_COORDINATORLAYOUT = "coordinatorlayout-1.3.0";
    public static String ANDROIDX_CORE = "core-1.17.0";
    public static String ANDROIDX_CORE_COMMON = "core-common-2.2.0";
    public static String ANDROIDX_CORE_KTX = "core-ktx-1.17.0";
    public static String ANDROIDX_CORE_RUNTIME = "core-runtime-2.2.0";
    public static String ANDROIDX_CORE_VIEWTREE = "core-viewtree-1.0.0";
    public static String ANDROIDX_CURSORADAPTER = "cursoradapter-1.0.0";
    public static String ANDROIDX_CUSTOMVIEW = "customview-1.1.0";
    public static String ANDROIDX_CUSTOMVIEW_POOLINGCONTAINER = "customview-poolingcontainer-1.0.0";
    public static String ANDROIDX_DATASTORE = "datastore-1.1.7";
    public static String ANDROIDX_DATASTORE_CORE = "datastore-core-1.1.7";
    public static String ANDROIDX_DATASTORE_PREFERENCES = "datastore-preferences-1.1.7";
    public static String ANDROIDX_DATASTORE_PREFERENCES_CORE = "datastore-preferences-core-1.1.7";
    public static String ANDROIDX_DOCUMENTFILE = "documentfile-1.0.0";
    public static String ANDROIDX_DRAWERLAYOUT = "drawerlayout-1.1.1";
    public static String ANDROIDX_DYNAMICANIMATION = "dynamicanimation-1.1.0";
    public static String ANDROIDX_EMOJI2 = "emoji2-1.3.0";
    public static String ANDROIDX_EMOJI2_VIEWS_HELPER = "emoji2-views-helper-1.3.0";
    public static String ANDROIDX_EXIFINTERFACE = "exifinterface-1.3.6";
    public static String ANDROIDX_FRAGMENT = "fragment-1.6.1";
    public static String ANDROIDX_GRAPHICS_SHAPES_ANDROID = "graphics-shapes-android-1.0.1";
    public static String ANDROIDX_INTERPOLATOR = "interpolator-1.0.0";
    public static String ANDROIDX_LEGACY_SUPPORT_CORE_UI = "legacy-support-core-ui-1.0.0";
    public static String ANDROIDX_LEGACY_SUPPORT_CORE_UTILS = "legacy-support-core-utils-1.0.0";
    public static String ANDROIDX_LEGACY_SUPPORT_V13 = "legacy-support-v13-1.0.0";
    public static String ANDROIDX_LEGACY_SUPPORT_V4 = "legacy-support-v4-1.0.0";
    public static String ANDROIDX_LIFECYCLE_COMMON = "lifecycle-common-2.6.2";
    public static String ANDROIDX_LIFECYCLE_LIVEDATA = "lifecycle-livedata-2.6.2";
    public static String ANDROIDX_LIFECYCLE_LIVEDATA_CORE = "lifecycle-livedata-core-2.6.2";
    public static String ANDROIDX_LIFECYCLE_PROCESS = "lifecycle-process-2.6.2";
    public static String ANDROIDX_LIFECYCLE_RUNTIME = "lifecycle-runtime-2.6.2";
    public static String ANDROIDX_LIFECYCLE_SERVICE = "lifecycle-service-2.6.2";
    public static String ANDROIDX_LIFECYCLE_VIEWMODEL = "lifecycle-viewmodel-2.6.2";
    public static String ANDROIDX_LIFECYCLE_VIEWMODEL_SAVEDSTATE = "lifecycle-viewmodel-savedstate-2.6.2";
    public static String ANDROIDX_LOADER = "loader-1.0.0";
    public static String ANDROIDX_LOCALBROADCASTMANAGER = "localbroadcastmanager-1.0.0";
    public static String ANDROIDX_MEDIA = "media-1.0.0";
    public static String ANDROIDX_MULTIDEX = "multidex-2.0.1";
    public static String ANDROIDX_RECYCLERVIEW = "recyclerview-1.4.0";
    public static String ANDROIDX_ROOM_COMMON = "room-common-2.2.5";
    public static String ANDROIDX_ROOM_RUNTIME = "room-runtime-2.2.5";
    public static String ANDROIDX_SAVEDSTATE = "savedstate-1.2.1";
    public static String ANDROIDX_SLIDINGPANELAYOUT = "slidingpanelayout-1.0.0";
    public static String ANDROIDX_SQLITE = "sqlite-2.1.0";
    public static String ANDROIDX_SQLITE_FRAMEWORK = "sqlite-framework-2.1.0";
    public static String ANDROIDX_STARTUP_RUNTIME = "startup-runtime-1.2.0";
    public static String ANDROIDX_SWIPEREFRESHLAYOUT = "swiperefreshlayout-1.0.0";
    public static String ANDROIDX_TRACING = "tracing-1.2.0";
    public static String ANDROIDX_TRANSITION = "transition-1.5.0";
    public static String ANDROIDX_VECTORDRAWABLE = "vectordrawable-1.1.0";
    public static String ANDROIDX_VECTORDRAWABLE_ANIMATED = "vectordrawable-animated-1.1.0";
    public static String ANDROIDX_VERSIONEDPARCELABLE = "versionedparcelable-1.1.1";
    public static String ANDROIDX_VIEWPAGER = "viewpager-1.0.0";
    public static String ANDROIDX_VIEWPAGER2 = "viewpager2-1.1.0-beta02";
    public static String ANDROIDX_WORK_RUNTIME = "work-runtime-2.7.0";
    public static String ANDROID_YOUTUBE_PLAYER = "android-youtube-player-10.0.5";
    public static String AUTO_VALUE_ANNOTATIONS = "auto-value-annotations-1.6.5";
    public static String CIRCLEIMAGEVIEW = "circleimageview-3.1.0";
    public static String CODEVIEW = "CodeView-0.4.0";
    public static String ERROR_PRONE_ANNOTATIONS = "error_prone_annotations-2.38.0";
    public static String FIREBASE_AI = "firebase-ai-18.1.0";
    public static String FIREBASE_ANALYTICS = "firebase-analytics-24.0.0";
    public static String FIREBASE_ANNOTATIONS = "firebase-annotations-18.0.0";
    public static String FIREBASE_APPCHECK = "firebase-appcheck-20.0.0";
    public static String FIREBASE_APPCHECK_INTEROP = "firebase-appcheck-interop-17.1.0";
    public static String FIREBASE_APPCHECK_DEBUG = "firebase-appcheck-debug-20.0.0";
    public static String FIREBASE_APPCHECK_DEBUG_TESTING = "firebase-appcheck-debug-testing-20.0.0";
    public static String FIREBASE_APPCHECK_PLAYINTEGRITY = "firebase-appcheck-playintegrity-20.0.0";
    public static String FIREBASE_APPCHECK_RECAPTCHA = "firebase-appcheck-recaptcha-20.0.0";
    public static String FIREBASE_AUTH = "firebase-auth-25.0.0";
    public static String FIREBASE_AUTH_INTEROP = "firebase-auth-interop-18.0.0";
    public static String FIREBASE_COMMON = "firebase-common-23.0.0";
    public static String FIREBASE_COMPONENTS = "firebase-components-19.0.0";
    public static String FIREBASE_CONFIG = "firebase-config-24.0.1";
    public static String FIREBASE_CRASHLYTICS = "firebase-crashlytics-21.0.1";
    public static String FIREBASE_CRASHLYTICS_NDK = "firebase-crashlytics-ndk-21.0.1";
    public static String FIREBASE_DATABASE = "firebase-database-23.0.0";
    public static String FIREBASE_DATABASE_COLLECTION = "firebase-database-collection-17.0.1";
    public static String FIREBASE_DATACONNECT = "firebase-dataconnect-18.0.1";
    public static String FIREBASE_DATATRANSPORT = "firebase-datatransport-21.0.0";
    public static String FIREBASE_ENCODERS = "firebase-encoders-18.0.0";
    public static String FIREBASE_ENCODERS_JSON = "firebase-encoders-json-18.0.1";
    public static String FIREBASE_ENCODERS_PROTO = "firebase-encoders-proto-16.0.0";
    public static String TRANSPORT_API = "transport-api-5.0.0";
    public static String TRANSPORT_BACKEND_CCT = "transport-backend-cct-5.0.0";
    public static String TRANSPORT_RUNTIME = "transport-runtime-5.0.0";
    public static String FIREBASE_FIRESTORE = "firebase-firestore-27.1.0";
    public static String FIREBASE_FUNCTIONS = "firebase-functions-23.0.0";
    public static String FIREBASE_IID = "firebase-iid-19.0.0";
    public static String FIREBASE_IID_INTEROP = "firebase-iid-interop-17.0.0";
    public static String FIREBASE_INAPPMESSAGING = "firebase-inappmessaging-23.0.0";
    public static String FIREBASE_INAPPMESSAGING_DISPLAY = "firebase-inappmessaging-display-23.0.0";
    public static String FIREBASE_INSTALLATIONS = "firebase-installations-20.0.0";
    public static String FIREBASE_INSTALLATIONS_INTEROP = "firebase-installations-interop-18.0.0";
    public static String FIREBASE_MEASUREMENT_CONNECTOR = "firebase-measurement-connector-18.0.0";
    public static String FIREBASE_MESSAGING = "firebase-messaging-26.0.1";
    public static String FIREBASE_MESSAGING_DIRECTBOOT = "firebase-messaging-directboot-26.0.1";
    public static String FIREBASE_PERF = "firebase-perf-23.0.1";
    public static String FIREBASE_PNV = "firebase-pnv-17.0.0";
    public static String FIREBASE_STORAGE = "firebase-storage-23.0.0";
    public static String GLIDE = "glide-5.0.4";
    public static String GLIDE_ANNOTATIONS = "annotations-5.0.4";
    public static String GLIDE_DISKLRUCACHE = "disklrucache-5.0.4";
    public static String GLIDE_GIFDECODER = "gifdecoder-5.0.4";
    public static String GSON = "gson-2.13.1";
    public static String HTTP_LEGACY_ANDROID = "http-legacy-android-28";
    public static String JETBRAINS_ANNOTATIONS = "annotations-23.0.0";
    public static String JETBRAINS_KOTLINX_COROUTINES_ANDROID = "kotlinx-coroutines-android-1.8.1";
    public static String JETBRAINS_KOTLINX_COROUTINES_CORE_JVM = "kotlinx-coroutines-core-jvm-1.8.1";
    public static String JETBRAINS_KOTLIN_STDLIB = "kotlin-stdlib-2.2.0";
    public static String JSPECIFY = "jspecify-1.0.0";
    public static String LOTTIE = "lottie-6.6.10";
    public static String MATERIAL = "material-1.13.0";
    public static String OKHTTP_ANDROID = "okhttp-android-5.1.0";
    public static String OKIO_JVM = "okio-jvm-3.15.0";
    public static String OTPVIEW = "OTPView-0.1.0";
    public static String PATTERN_LOCK_VIEW = "pattern-lock-view";
    public static String PLAY_SERVICES_ADS = "play-services-ads-22.2.0";
    public static String PLAY_SERVICES_ADS_BASE = "play-services-ads-base-22.2.0";
    public static String PLAY_SERVICES_ADS_IDENTIFIER = "play-services-ads-identifier-18.0.0";
    public static String PLAY_SERVICES_ADS_LITE = "play-services-ads-lite-22.2.0";
    public static String PLAY_SERVICES_APPSET = "play-services-appset-16.0.1";
    public static String PLAY_SERVICES_AUTH = "play-services-auth-19.0.0";
    public static String PLAY_SERVICES_AUTH_API_PHONE = "play-services-auth-api-phone-17.0.0";
    public static String PLAY_SERVICES_AUTH_BASE = "play-services-auth-base-17.0.0";
    public static String PLAY_SERVICES_BASE = "play-services-base-18.9.0";
    public static String PLAY_SERVICES_BASEMENT = "play-services-basement-18.9.0";
    public static String PLAY_SERVICES_CLOUD_MESSAGING = "play-services-cloud-messaging-17.4.0";
    public static String PLAY_SERVICES_GASS = "play-services-gass-20.0.0";
    public static String PLAY_SERVICES_GCM = "play-services-gcm-17.0.0";
    public static String PLAY_SERVICES_IID = "play-services-iid-17.0.0";
    public static String PLAY_SERVICES_LOCATION = "play-services-location-18.0.0";
    public static String PLAY_SERVICES_MAPS = "play-services-maps-17.0.1";
    public static String PLAY_SERVICES_MEASUREMENT_BASE = "play-services-measurement-base-20.1.2";
    public static String PLAY_SERVICES_MEASUREMENT_SDK_API = "play-services-measurement-sdk-api-20.1.2";
    public static String PLAY_SERVICES_PLACES_PLACEREPORT = "play-services-places-placereport-17.0.0";
    public static String PLAY_SERVICES_STATS = "play-services-stats-17.0.0";
    public static String PLAY_SERVICES_TASKS = "play-services-tasks-18.4.0";
    public static String USER_MESSAGING_PLATFORM = "user-messaging-platform-2.0.0";
    public static String WAVE_SIDE_BAR = "wave-side-bar";

    public static final BuiltInLibrary[] KNOWN_BUILT_IN_LIBRARIES = {
            new BuiltInLibrary(ANDROIDX_ACTIVITY, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_CORE_KTX, ANDROIDX_CORE_VIEWTREE,
                    ANDROIDX_LIFECYCLE_COMMON, ANDROIDX_LIFECYCLE_RUNTIME,
                    ANDROIDX_LIFECYCLE_VIEWMODEL_SAVEDSTATE, ANDROIDX_LIFECYCLE_VIEWMODEL,
                    ANDROIDX_SAVEDSTATE, ANDROIDX_TRACING, JETBRAINS_KOTLIN_STDLIB,
                    JETBRAINS_KOTLINX_COROUTINES_CORE_JVM), "androidx.activity"),

            new BuiltInLibrary(ANDROIDX_ANNOTATION_EXPERIMENTAL, List.of(JETBRAINS_KOTLIN_STDLIB)),

            new BuiltInLibrary(ANDROIDX_ANNOTATION_JVM, List.of(JETBRAINS_KOTLIN_STDLIB)),

            new BuiltInLibrary(ANDROIDX_APPCOMPAT, List.of(ANDROIDX_ACTIVITY, ANDROIDX_ANNOTATION_JVM, ANDROIDX_APPCOMPAT_RESOURCES,
                    ANDROIDX_COLLECTION_JVM, ANDROIDX_CORE_KTX, ANDROIDX_CORE,
                    ANDROIDX_CURSORADAPTER, ANDROIDX_CUSTOMVIEW, ANDROIDX_DRAWERLAYOUT,
                    ANDROIDX_EMOJI2_VIEWS_HELPER, ANDROIDX_EMOJI2, ANDROIDX_FRAGMENT,
                    ANDROIDX_LIFECYCLE_RUNTIME, ANDROIDX_LIFECYCLE_VIEWMODEL,
                    ANDROIDX_SAVEDSTATE, JETBRAINS_KOTLIN_STDLIB), "androidx.appcompat"),

            new BuiltInLibrary(ANDROIDX_APPCOMPAT_RESOURCES, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_APPCOMPAT, ANDROIDX_COLLECTION_JVM,
                    ANDROIDX_CORE, ANDROIDX_VECTORDRAWABLE_ANIMATED,
                    ANDROIDX_VECTORDRAWABLE), "androidx.appcompat.resources"),

            new BuiltInLibrary(ANDROIDX_ASYNCLAYOUTINFLATER, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_CORE)),

            new BuiltInLibrary(ANDROIDX_BROWSER, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_COLLECTION_JVM, ANDROIDX_CONCURRENT_FUTURES,
                    ANDROIDX_CORE, ANDROIDX_INTERPOLATOR), "androidx.browser"),

            new BuiltInLibrary(ANDROIDX_CARDVIEW, List.of(ANDROIDX_ANNOTATION_JVM), "androidx.cardview"),

            new BuiltInLibrary(ANDROIDX_COLLECTION_JVM, List.of(ANDROIDX_ANNOTATION_JVM, JETBRAINS_KOTLIN_STDLIB)),

            new BuiltInLibrary(ANDROIDX_CONCURRENT_FUTURES, List.of(ANDROIDX_ANNOTATION_JVM)),

            new BuiltInLibrary(ANDROIDX_CONSTRAINTLAYOUT, List.of(ANDROIDX_APPCOMPAT, ANDROIDX_CONSTRAINTLAYOUT_CORE, ANDROIDX_CORE), "androidx.constraintlayout.widget"),

            new BuiltInLibrary(ANDROIDX_CONSTRAINTLAYOUT_CORE, List.of(ANDROIDX_ANNOTATION_JVM)),

            new BuiltInLibrary(ANDROIDX_COORDINATORLAYOUT, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_COLLECTION_JVM, ANDROIDX_CORE,
                    ANDROIDX_CUSTOMVIEW, JSPECIFY), "androidx.coordinatorlayout"),

            new BuiltInLibrary(ANDROIDX_CORE, List.of(ANDROIDX_ANNOTATION_EXPERIMENTAL, ANDROIDX_ANNOTATION_JVM,
                    ANDROIDX_COLLECTION_JVM, ANDROIDX_CONCURRENT_FUTURES,
                    ANDROIDX_CORE_KTX, ANDROIDX_CORE_VIEWTREE, ANDROIDX_INTERPOLATOR,
                    ANDROIDX_LIFECYCLE_COMMON, ANDROIDX_LIFECYCLE_RUNTIME,
                    ANDROIDX_TRACING, ANDROIDX_VERSIONEDPARCELABLE,
                    JETBRAINS_KOTLIN_STDLIB, JETBRAINS_KOTLINX_COROUTINES_CORE_JVM,
                    JSPECIFY), "androidx.core"),

            new BuiltInLibrary(ANDROIDX_CORE_COMMON, List.of(ANDROIDX_ANNOTATION_JVM)),

            new BuiltInLibrary(ANDROIDX_CORE_KTX, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_CORE, JETBRAINS_KOTLIN_STDLIB)),

            new BuiltInLibrary(ANDROIDX_CORE_RUNTIME, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_CORE_COMMON)),

            new BuiltInLibrary(ANDROIDX_CORE_VIEWTREE, List.of(JETBRAINS_KOTLIN_STDLIB), "androidx.core.viewtree"),

            new BuiltInLibrary(ANDROIDX_CURSORADAPTER, List.of(ANDROIDX_ANNOTATION_JVM)),

            new BuiltInLibrary(ANDROIDX_CUSTOMVIEW, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_COLLECTION_JVM, ANDROIDX_CORE)),

            new BuiltInLibrary(ANDROIDX_CUSTOMVIEW_POOLINGCONTAINER, List.of(ANDROIDX_CORE_KTX, JETBRAINS_KOTLIN_STDLIB), "androidx.customview.poolingcontainer"),

            new BuiltInLibrary(ANDROIDX_DATASTORE, List.of(ANDROIDX_DATASTORE_CORE, JETBRAINS_KOTLIN_STDLIB)),

            new BuiltInLibrary(ANDROIDX_DATASTORE_CORE, List.of(JETBRAINS_KOTLIN_STDLIB, JETBRAINS_KOTLINX_COROUTINES_CORE_JVM)),

            new BuiltInLibrary(ANDROIDX_DATASTORE_PREFERENCES, List.of(ANDROIDX_DATASTORE_PREFERENCES_CORE, ANDROIDX_DATASTORE)),

            new BuiltInLibrary(ANDROIDX_DATASTORE_PREFERENCES_CORE, List.of(ANDROIDX_DATASTORE_CORE, JETBRAINS_KOTLIN_STDLIB, JETBRAINS_KOTLINX_COROUTINES_CORE_JVM)),

            new BuiltInLibrary(ANDROIDX_DOCUMENTFILE, List.of(ANDROIDX_ANNOTATION_JVM)),

            new BuiltInLibrary(ANDROIDX_DRAWERLAYOUT, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_CORE, ANDROIDX_CUSTOMVIEW), "androidx.drawerlayout"),

            new BuiltInLibrary(ANDROIDX_DYNAMICANIMATION, List.of(ANDROIDX_COLLECTION_JVM, ANDROIDX_CORE, JSPECIFY)),

            new BuiltInLibrary(ANDROIDX_EMOJI2, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_COLLECTION_JVM, ANDROIDX_CORE,
                    ANDROIDX_EMOJI2_VIEWS_HELPER, ANDROIDX_LIFECYCLE_COMMON,
                    ANDROIDX_LIFECYCLE_PROCESS, ANDROIDX_STARTUP_RUNTIME)),

            new BuiltInLibrary(ANDROIDX_EMOJI2_VIEWS_HELPER, List.of(ANDROIDX_COLLECTION_JVM, ANDROIDX_CORE, ANDROIDX_EMOJI2)),

            new BuiltInLibrary(ANDROIDX_EXIFINTERFACE, List.of(ANDROIDX_ANNOTATION_JVM)),

            new BuiltInLibrary(ANDROIDX_FRAGMENT, List.of(ANDROIDX_ACTIVITY, ANDROIDX_ANNOTATION_EXPERIMENTAL, ANDROIDX_ANNOTATION_JVM,
                    ANDROIDX_COLLECTION_JVM, ANDROIDX_CORE_KTX,
                    ANDROIDX_LIFECYCLE_LIVEDATA_CORE, ANDROIDX_LIFECYCLE_RUNTIME,
                    ANDROIDX_LIFECYCLE_VIEWMODEL_SAVEDSTATE, ANDROIDX_LIFECYCLE_VIEWMODEL,
                    ANDROIDX_LOADER, ANDROIDX_SAVEDSTATE, ANDROIDX_VIEWPAGER,
                    JETBRAINS_KOTLIN_STDLIB), "androidx.fragment"),

            new BuiltInLibrary(ANDROIDX_GRAPHICS_SHAPES_ANDROID, List.of(ANDROIDX_ANNOTATION_EXPERIMENTAL, ANDROIDX_ANNOTATION_JVM,
                    ANDROIDX_COLLECTION_JVM, ANDROIDX_CORE_KTX, JETBRAINS_KOTLIN_STDLIB)),

            new BuiltInLibrary(ANDROIDX_INTERPOLATOR, List.of(ANDROIDX_ANNOTATION_JVM)),

            new BuiltInLibrary(ANDROIDX_LEGACY_SUPPORT_CORE_UI, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_ASYNCLAYOUTINFLATER, ANDROIDX_COORDINATORLAYOUT,
                    ANDROIDX_CORE, ANDROIDX_CURSORADAPTER, ANDROIDX_CUSTOMVIEW,
                    ANDROIDX_DRAWERLAYOUT, ANDROIDX_INTERPOLATOR,
                    ANDROIDX_LEGACY_SUPPORT_CORE_UTILS, ANDROIDX_SLIDINGPANELAYOUT,
                    ANDROIDX_SWIPEREFRESHLAYOUT, ANDROIDX_VIEWPAGER)),

            new BuiltInLibrary(ANDROIDX_LEGACY_SUPPORT_CORE_UTILS, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_CORE, ANDROIDX_DOCUMENTFILE, ANDROIDX_LOADER,
                    ANDROIDX_LOCALBROADCASTMANAGER)),

            new BuiltInLibrary(ANDROIDX_LEGACY_SUPPORT_V13, List.of(ANDROIDX_LEGACY_SUPPORT_V4)),

            new BuiltInLibrary(ANDROIDX_LEGACY_SUPPORT_V4, List.of(ANDROIDX_CORE, ANDROIDX_FRAGMENT, ANDROIDX_LEGACY_SUPPORT_CORE_UI,
                    ANDROIDX_LEGACY_SUPPORT_CORE_UTILS, ANDROIDX_MEDIA)),

            new BuiltInLibrary(ANDROIDX_LIFECYCLE_COMMON, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_LIFECYCLE_LIVEDATA_CORE,
                    ANDROIDX_LIFECYCLE_LIVEDATA, ANDROIDX_LIFECYCLE_PROCESS,
                    ANDROIDX_LIFECYCLE_RUNTIME, ANDROIDX_LIFECYCLE_SERVICE,
                    ANDROIDX_LIFECYCLE_VIEWMODEL_SAVEDSTATE, ANDROIDX_LIFECYCLE_VIEWMODEL,
                    JETBRAINS_KOTLIN_STDLIB, JETBRAINS_KOTLINX_COROUTINES_ANDROID)),

            new BuiltInLibrary(ANDROIDX_LIFECYCLE_LIVEDATA, List.of(ANDROIDX_CORE_COMMON, ANDROIDX_CORE_RUNTIME, ANDROIDX_LIFECYCLE_COMMON,
                    ANDROIDX_LIFECYCLE_LIVEDATA_CORE, ANDROIDX_LIFECYCLE_PROCESS,
                    ANDROIDX_LIFECYCLE_RUNTIME, ANDROIDX_LIFECYCLE_SERVICE,
                    ANDROIDX_LIFECYCLE_VIEWMODEL_SAVEDSTATE, ANDROIDX_LIFECYCLE_VIEWMODEL,
                    JETBRAINS_KOTLIN_STDLIB)),

            new BuiltInLibrary(ANDROIDX_LIFECYCLE_LIVEDATA_CORE, List.of(ANDROIDX_CORE_COMMON, ANDROIDX_CORE_RUNTIME, ANDROIDX_LIFECYCLE_COMMON,
                    ANDROIDX_LIFECYCLE_LIVEDATA, ANDROIDX_LIFECYCLE_PROCESS,
                    ANDROIDX_LIFECYCLE_RUNTIME, ANDROIDX_LIFECYCLE_SERVICE,
                    ANDROIDX_LIFECYCLE_VIEWMODEL_SAVEDSTATE, ANDROIDX_LIFECYCLE_VIEWMODEL,
                    JETBRAINS_KOTLIN_STDLIB)),

            new BuiltInLibrary(ANDROIDX_LIFECYCLE_PROCESS, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_LIFECYCLE_COMMON,
                    ANDROIDX_LIFECYCLE_LIVEDATA_CORE, ANDROIDX_LIFECYCLE_LIVEDATA,
                    ANDROIDX_LIFECYCLE_RUNTIME, ANDROIDX_LIFECYCLE_SERVICE,
                    ANDROIDX_LIFECYCLE_VIEWMODEL_SAVEDSTATE, ANDROIDX_LIFECYCLE_VIEWMODEL,
                    ANDROIDX_STARTUP_RUNTIME, JETBRAINS_KOTLIN_STDLIB)),

            new BuiltInLibrary(ANDROIDX_LIFECYCLE_RUNTIME, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_CORE_COMMON, ANDROIDX_CORE_RUNTIME,
                    ANDROIDX_LIFECYCLE_COMMON, ANDROIDX_LIFECYCLE_LIVEDATA_CORE,
                    ANDROIDX_LIFECYCLE_LIVEDATA, ANDROIDX_LIFECYCLE_PROCESS,
                    ANDROIDX_LIFECYCLE_SERVICE, ANDROIDX_LIFECYCLE_VIEWMODEL_SAVEDSTATE,
                    ANDROIDX_LIFECYCLE_VIEWMODEL, JETBRAINS_KOTLIN_STDLIB), "androidx.lifecycle.runtime"),

            new BuiltInLibrary(ANDROIDX_LIFECYCLE_SERVICE, List.of(ANDROIDX_LIFECYCLE_COMMON, ANDROIDX_LIFECYCLE_LIVEDATA_CORE,
                    ANDROIDX_LIFECYCLE_LIVEDATA, ANDROIDX_LIFECYCLE_PROCESS,
                    ANDROIDX_LIFECYCLE_RUNTIME, ANDROIDX_LIFECYCLE_VIEWMODEL_SAVEDSTATE,
                    ANDROIDX_LIFECYCLE_VIEWMODEL, JETBRAINS_KOTLIN_STDLIB)),

            new BuiltInLibrary(ANDROIDX_LIFECYCLE_VIEWMODEL, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_LIFECYCLE_COMMON,
                    ANDROIDX_LIFECYCLE_LIVEDATA_CORE, ANDROIDX_LIFECYCLE_LIVEDATA,
                    ANDROIDX_LIFECYCLE_PROCESS, ANDROIDX_LIFECYCLE_RUNTIME,
                    ANDROIDX_LIFECYCLE_SERVICE, ANDROIDX_LIFECYCLE_VIEWMODEL_SAVEDSTATE,
                    JETBRAINS_KOTLIN_STDLIB), "androidx.lifecycle.viewmodel"),

            new BuiltInLibrary(ANDROIDX_LIFECYCLE_VIEWMODEL_SAVEDSTATE, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_CORE_KTX, ANDROIDX_LIFECYCLE_COMMON,
                    ANDROIDX_LIFECYCLE_LIVEDATA_CORE, ANDROIDX_LIFECYCLE_LIVEDATA,
                    ANDROIDX_LIFECYCLE_PROCESS, ANDROIDX_LIFECYCLE_RUNTIME,
                    ANDROIDX_LIFECYCLE_SERVICE, ANDROIDX_LIFECYCLE_VIEWMODEL,
                    ANDROIDX_SAVEDSTATE, JETBRAINS_KOTLIN_STDLIB,
                    JETBRAINS_KOTLINX_COROUTINES_ANDROID)),

            new BuiltInLibrary(ANDROIDX_LOADER, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_CORE, ANDROIDX_LIFECYCLE_LIVEDATA,
                    ANDROIDX_LIFECYCLE_VIEWMODEL)),

            new BuiltInLibrary(ANDROIDX_LOCALBROADCASTMANAGER, List.of(ANDROIDX_ANNOTATION_JVM)),

            new BuiltInLibrary(ANDROIDX_MEDIA, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_CORE, ANDROIDX_VERSIONEDPARCELABLE), "androidx.media"),

            new BuiltInLibrary(ANDROIDX_MULTIDEX),

            new BuiltInLibrary(ANDROIDX_RECYCLERVIEW, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_COLLECTION_JVM, ANDROIDX_CORE,
                    ANDROIDX_CUSTOMVIEW_POOLINGCONTAINER, ANDROIDX_CUSTOMVIEW,
                    ANDROIDX_VIEWPAGER2), "androidx.recyclerview"),

            new BuiltInLibrary(ANDROIDX_ROOM_COMMON, List.of(ANDROIDX_ANNOTATION_JVM)),

            new BuiltInLibrary(ANDROIDX_ROOM_RUNTIME, List.of(ANDROIDX_CORE_RUNTIME, ANDROIDX_ROOM_COMMON, ANDROIDX_SQLITE_FRAMEWORK,
                    ANDROIDX_SQLITE)),

            new BuiltInLibrary(ANDROIDX_SAVEDSTATE, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_CORE_COMMON, ANDROIDX_LIFECYCLE_COMMON,
                    JETBRAINS_KOTLIN_STDLIB), "androidx.savedstate"),

            new BuiltInLibrary(ANDROIDX_SLIDINGPANELAYOUT, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_CORE, ANDROIDX_CUSTOMVIEW)),

            new BuiltInLibrary(ANDROIDX_SQLITE, List.of(ANDROIDX_ANNOTATION_JVM)),

            new BuiltInLibrary(ANDROIDX_SQLITE_FRAMEWORK, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_SQLITE)),

            new BuiltInLibrary(ANDROIDX_STARTUP_RUNTIME, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_LIFECYCLE_COMMON, ANDROIDX_TRACING), "androidx.startup"),

            new BuiltInLibrary(ANDROIDX_SWIPEREFRESHLAYOUT, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_CORE, ANDROIDX_INTERPOLATOR)),

            new BuiltInLibrary(ANDROIDX_TRACING, List.of(ANDROIDX_ANNOTATION_JVM)),

            new BuiltInLibrary(ANDROIDX_TRANSITION, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_COLLECTION_JVM, ANDROIDX_CORE,
                    ANDROIDX_DYNAMICANIMATION), "androidx.transition"),

            new BuiltInLibrary(ANDROIDX_VECTORDRAWABLE, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_COLLECTION_JVM, ANDROIDX_CORE)),

            new BuiltInLibrary(ANDROIDX_VECTORDRAWABLE_ANIMATED, List.of(ANDROIDX_COLLECTION_JVM, ANDROIDX_INTERPOLATOR, ANDROIDX_VECTORDRAWABLE)),

            new BuiltInLibrary(ANDROIDX_VERSIONEDPARCELABLE, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_COLLECTION_JVM)),

            new BuiltInLibrary(ANDROIDX_VIEWPAGER, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_CORE, ANDROIDX_CUSTOMVIEW)),

            new BuiltInLibrary(ANDROIDX_VIEWPAGER2, List.of(ANDROIDX_ANNOTATION_EXPERIMENTAL, ANDROIDX_ANNOTATION_JVM,
                    ANDROIDX_COLLECTION_JVM, ANDROIDX_CORE, ANDROIDX_FRAGMENT,
                    ANDROIDX_RECYCLERVIEW), "androidx.viewpager2"),

            new BuiltInLibrary(ANDROIDX_WORK_RUNTIME, List.of(ANDROIDX_ANNOTATION_EXPERIMENTAL, ANDROIDX_CORE, ANDROIDX_LIFECYCLE_LIVEDATA,
                    ANDROIDX_LIFECYCLE_SERVICE, ANDROIDX_ROOM_RUNTIME,
                    ANDROIDX_SQLITE_FRAMEWORK, ANDROIDX_SQLITE, ANDROIDX_STARTUP_RUNTIME), "androidx.work"),

            new BuiltInLibrary(ANDROID_YOUTUBE_PLAYER, List.of(ANDROIDX_APPCOMPAT, ANDROIDX_LIFECYCLE_COMMON, ANDROIDX_RECYCLERVIEW,
                    JETBRAINS_KOTLIN_STDLIB), "com.pierfrancescosoffritti.androidyoutubeplayer"),

            new BuiltInLibrary(AUTO_VALUE_ANNOTATIONS),

            new BuiltInLibrary(CIRCLEIMAGEVIEW, List.of(), "de.hdodenhof.circleimageview"),

            new BuiltInLibrary(CODEVIEW, List.of(), "br.tiagohm.codeview"),

            new BuiltInLibrary(ERROR_PRONE_ANNOTATIONS),

            new BuiltInLibrary(FIREBASE_AI, List.of(FIREBASE_COMMON, FIREBASE_COMPONENTS, JETBRAINS_KOTLIN_STDLIB)),

            new BuiltInLibrary(FIREBASE_ANALYTICS, List.of(FIREBASE_COMMON, PLAY_SERVICES_MEASUREMENT_BASE, PLAY_SERVICES_MEASUREMENT_SDK_API)),

            new BuiltInLibrary(FIREBASE_ANNOTATIONS),

            new BuiltInLibrary(FIREBASE_APPCHECK, List.of(FIREBASE_COMMON, FIREBASE_COMPONENTS, PLAY_SERVICES_TASKS)),

            new BuiltInLibrary(FIREBASE_APPCHECK_DEBUG, List.of(FIREBASE_APPCHECK, FIREBASE_COMMON)),

            new BuiltInLibrary(FIREBASE_APPCHECK_DEBUG_TESTING, List.of(FIREBASE_APPCHECK_DEBUG)),

            new BuiltInLibrary(FIREBASE_APPCHECK_PLAYINTEGRITY, List.of(FIREBASE_APPCHECK, FIREBASE_COMMON)),

            new BuiltInLibrary(FIREBASE_APPCHECK_RECAPTCHA, List.of(FIREBASE_APPCHECK, FIREBASE_COMMON)),

            new BuiltInLibrary(FIREBASE_APPCHECK_INTEROP, List.of(FIREBASE_COMMON)),

            new BuiltInLibrary(FIREBASE_AUTH, List.of(ANDROIDX_COLLECTION_JVM, ANDROIDX_FRAGMENT, ANDROIDX_LOCALBROADCASTMANAGER,
                    PLAY_SERVICES_BASE, PLAY_SERVICES_BASEMENT, PLAY_SERVICES_TASKS,
                    FIREBASE_AUTH_INTEROP, FIREBASE_APPCHECK_INTEROP, FIREBASE_COMMON)),

            new BuiltInLibrary(FIREBASE_AUTH_INTEROP, List.of(PLAY_SERVICES_BASE, PLAY_SERVICES_BASEMENT, PLAY_SERVICES_TASKS, FIREBASE_COMMON)),

            new BuiltInLibrary(FIREBASE_COMMON, List.of(PLAY_SERVICES_BASEMENT, PLAY_SERVICES_TASKS, AUTO_VALUE_ANNOTATIONS,
                    FIREBASE_COMPONENTS, FIREBASE_ANNOTATIONS)),

            new BuiltInLibrary(FIREBASE_COMPONENTS, List.of(ANDROIDX_ANNOTATION_JVM)),

            new BuiltInLibrary(FIREBASE_CONFIG, List.of(FIREBASE_COMMON, FIREBASE_COMPONENTS, FIREBASE_INSTALLATIONS)),

            new BuiltInLibrary(FIREBASE_CRASHLYTICS, List.of(FIREBASE_COMMON, FIREBASE_COMPONENTS, FIREBASE_INSTALLATIONS, FIREBASE_ENCODERS)),

            new BuiltInLibrary(FIREBASE_CRASHLYTICS_NDK, List.of(FIREBASE_CRASHLYTICS)),

            new BuiltInLibrary(FIREBASE_DATABASE, List.of(ANDROIDX_ANNOTATION_JVM, PLAY_SERVICES_BASE, PLAY_SERVICES_BASEMENT,
                    PLAY_SERVICES_TASKS, FIREBASE_AUTH_INTEROP, FIREBASE_APPCHECK_INTEROP, FIREBASE_COMMON,
                    FIREBASE_COMPONENTS, FIREBASE_DATABASE_COLLECTION)),

            new BuiltInLibrary(FIREBASE_DATABASE_COLLECTION, List.of(PLAY_SERVICES_BASE)),

            new BuiltInLibrary(FIREBASE_DATATRANSPORT, List.of(FIREBASE_COMMON, FIREBASE_COMPONENTS, TRANSPORT_API, TRANSPORT_BACKEND_CCT, TRANSPORT_RUNTIME)),

            new BuiltInLibrary(FIREBASE_ENCODERS, List.of()),

            new BuiltInLibrary(FIREBASE_ENCODERS_JSON, List.of(FIREBASE_ENCODERS)),

            new BuiltInLibrary(FIREBASE_ENCODERS_PROTO, List.of(FIREBASE_ENCODERS)),

            new BuiltInLibrary(FIREBASE_FIRESTORE, List.of(FIREBASE_COMMON, FIREBASE_COMPONENTS, FIREBASE_DATABASE_COLLECTION, FIREBASE_AUTH_INTEROP, FIREBASE_APPCHECK_INTEROP)),

            new BuiltInLibrary(FIREBASE_FUNCTIONS, List.of(FIREBASE_COMMON, FIREBASE_COMPONENTS, FIREBASE_AUTH_INTEROP, PLAY_SERVICES_TASKS)),

            new BuiltInLibrary(FIREBASE_IID, List.of(ANDROIDX_COLLECTION_JVM, ANDROIDX_CORE, ANDROIDX_LEGACY_SUPPORT_CORE_UTILS,
                    PLAY_SERVICES_BASEMENT, PLAY_SERVICES_STATS, PLAY_SERVICES_TASKS,
                    FIREBASE_COMMON, FIREBASE_IID_INTEROP)),

            new BuiltInLibrary(FIREBASE_IID_INTEROP, List.of(PLAY_SERVICES_BASE, PLAY_SERVICES_BASEMENT)),

            new BuiltInLibrary(FIREBASE_INAPPMESSAGING, List.of(FIREBASE_COMMON, FIREBASE_COMPONENTS, FIREBASE_INSTALLATIONS)),

            new BuiltInLibrary(FIREBASE_INAPPMESSAGING_DISPLAY, List.of(FIREBASE_INAPPMESSAGING, ANDROIDX_APPCOMPAT, GLIDE)),

            new BuiltInLibrary(FIREBASE_INSTALLATIONS, List.of(FIREBASE_COMMON, FIREBASE_COMPONENTS, FIREBASE_INSTALLATIONS_INTEROP,
                    PLAY_SERVICES_TASKS, ANDROIDX_DATASTORE_PREFERENCES, ANDROIDX_DATASTORE_PREFERENCES_CORE)),

            new BuiltInLibrary(FIREBASE_INSTALLATIONS_INTEROP, List.of(FIREBASE_COMMON)),

            new BuiltInLibrary(FIREBASE_MEASUREMENT_CONNECTOR, List.of(PLAY_SERVICES_BASEMENT)),

            new BuiltInLibrary(FIREBASE_MESSAGING, List.of(ANDROIDX_COLLECTION_JVM, ANDROIDX_CORE, PLAY_SERVICES_BASEMENT,
                    PLAY_SERVICES_TASKS, PLAY_SERVICES_CLOUD_MESSAGING, FIREBASE_COMMON, FIREBASE_IID,
                    FIREBASE_MEASUREMENT_CONNECTOR, FIREBASE_DATATRANSPORT, FIREBASE_ENCODERS_JSON, FIREBASE_ENCODERS_PROTO,
                    FIREBASE_INSTALLATIONS, FIREBASE_INSTALLATIONS_INTEROP), "com.google.firebase.messaging"),

            new BuiltInLibrary(FIREBASE_MESSAGING_DIRECTBOOT, List.of(FIREBASE_MESSAGING)),

            new BuiltInLibrary(FIREBASE_PERF, List.of(FIREBASE_COMMON, FIREBASE_COMPONENTS, FIREBASE_INSTALLATIONS)),

            new BuiltInLibrary(FIREBASE_PNV, List.of(FIREBASE_COMMON, FIREBASE_COMPONENTS)),

            new BuiltInLibrary(FIREBASE_STORAGE, List.of(ANDROIDX_ANNOTATION_JVM, PLAY_SERVICES_BASE, PLAY_SERVICES_TASKS,
                    FIREBASE_AUTH_INTEROP, FIREBASE_APPCHECK_INTEROP, FIREBASE_COMMON)),

            new BuiltInLibrary(GLIDE, List.of(ANDROIDX_EXIFINTERFACE, ANDROIDX_FRAGMENT, ANDROIDX_TRACING,
                    ANDROIDX_VECTORDRAWABLE_ANIMATED, GLIDE_ANNOTATIONS,
                    GLIDE_DISKLRUCACHE, GLIDE_GIFDECODER), "com.bumptech.glide"),

            new BuiltInLibrary(GLIDE_ANNOTATIONS),

            new BuiltInLibrary(GLIDE_DISKLRUCACHE),

            new BuiltInLibrary(GLIDE_GIFDECODER, List.of(ANDROIDX_ANNOTATION_JVM)),

            new BuiltInLibrary(GSON, List.of(ERROR_PRONE_ANNOTATIONS)),

            new BuiltInLibrary(HTTP_LEGACY_ANDROID),

            new BuiltInLibrary(JETBRAINS_ANNOTATIONS),

            new BuiltInLibrary(JETBRAINS_KOTLINX_COROUTINES_ANDROID, List.of(JETBRAINS_KOTLIN_STDLIB, JETBRAINS_KOTLINX_COROUTINES_CORE_JVM)),

            new BuiltInLibrary(JETBRAINS_KOTLINX_COROUTINES_CORE_JVM, List.of(JETBRAINS_KOTLIN_STDLIB, JETBRAINS_ANNOTATIONS)),

            new BuiltInLibrary(JETBRAINS_KOTLIN_STDLIB, List.of(JETBRAINS_ANNOTATIONS)),

            new BuiltInLibrary(JSPECIFY),

            new BuiltInLibrary(LOTTIE, List.of(ANDROIDX_APPCOMPAT, OKIO_JVM), "com.airbnb.lottie"),

            new BuiltInLibrary(MATERIAL, List.of(ANDROIDX_ACTIVITY, ANDROIDX_ANNOTATION_EXPERIMENTAL, ANDROIDX_ANNOTATION_JVM,
                    ANDROIDX_APPCOMPAT, ANDROIDX_CARDVIEW, ANDROIDX_CONSTRAINTLAYOUT,
                    ANDROIDX_COORDINATORLAYOUT, ANDROIDX_CORE, ANDROIDX_DRAWERLAYOUT,
                    ANDROIDX_DYNAMICANIMATION, ANDROIDX_FRAGMENT,
                    ANDROIDX_GRAPHICS_SHAPES_ANDROID, ANDROIDX_LIFECYCLE_RUNTIME,
                    ANDROIDX_RECYCLERVIEW, ANDROIDX_TRANSITION, ANDROIDX_VECTORDRAWABLE,
                    ANDROIDX_VIEWPAGER2, ERROR_PRONE_ANNOTATIONS, JETBRAINS_KOTLIN_STDLIB), "com.google.android.material"),

            new BuiltInLibrary(OKHTTP_ANDROID, List.of(ANDROIDX_ANNOTATION_JVM, ANDROIDX_STARTUP_RUNTIME, OKIO_JVM,
                    JETBRAINS_KOTLIN_STDLIB)),

            new BuiltInLibrary(OKIO_JVM, List.of(JETBRAINS_KOTLIN_STDLIB)),

            new BuiltInLibrary(OTPVIEW, List.of(ANDROIDX_APPCOMPAT, ANDROIDX_CORE_KTX), "affan.ahmad.otp"),

            new BuiltInLibrary(PATTERN_LOCK_VIEW, List.of(ANDROIDX_CORE, JETBRAINS_ANNOTATIONS), "com.andrognito.patternlockview"),

            new BuiltInLibrary(PLAY_SERVICES_ADS, List.of(ANDROIDX_BROWSER, ANDROIDX_COLLECTION_JVM, ANDROIDX_CORE, PLAY_SERVICES_ADS_BASE,
                    PLAY_SERVICES_ADS_IDENTIFIER, PLAY_SERVICES_ADS_LITE,
                    PLAY_SERVICES_APPSET, PLAY_SERVICES_BASEMENT, PLAY_SERVICES_TASKS), "com.google.android.gms.ads.impl"),

            new BuiltInLibrary(PLAY_SERVICES_ADS_BASE, List.of(PLAY_SERVICES_BASEMENT)),

            new BuiltInLibrary(PLAY_SERVICES_ADS_IDENTIFIER, List.of(PLAY_SERVICES_BASEMENT)),

            new BuiltInLibrary(PLAY_SERVICES_ADS_LITE, List.of(ANDROIDX_WORK_RUNTIME, PLAY_SERVICES_ADS_BASE, PLAY_SERVICES_BASEMENT,
                    PLAY_SERVICES_MEASUREMENT_SDK_API, USER_MESSAGING_PLATFORM), "com.google.android.gms.ads"),

            new BuiltInLibrary(PLAY_SERVICES_APPSET, List.of(PLAY_SERVICES_BASE, PLAY_SERVICES_BASEMENT, PLAY_SERVICES_TASKS)),

            new BuiltInLibrary(PLAY_SERVICES_AUTH, List.of(ANDROIDX_FRAGMENT, ANDROIDX_LOADER, PLAY_SERVICES_AUTH_API_PHONE,
                    PLAY_SERVICES_AUTH_BASE, PLAY_SERVICES_BASE, PLAY_SERVICES_BASEMENT,
                    PLAY_SERVICES_TASKS), "com.google.android.gms.auth.api"),

            new BuiltInLibrary(PLAY_SERVICES_AUTH_API_PHONE, List.of(PLAY_SERVICES_BASE, PLAY_SERVICES_BASEMENT, PLAY_SERVICES_TASKS)),

            new BuiltInLibrary(PLAY_SERVICES_AUTH_BASE, List.of(ANDROIDX_COLLECTION_JVM, PLAY_SERVICES_BASE, PLAY_SERVICES_BASEMENT,
                    PLAY_SERVICES_TASKS)),

            new BuiltInLibrary(PLAY_SERVICES_BASE, List.of(ANDROIDX_COLLECTION_JVM, ANDROIDX_CORE, ANDROIDX_FRAGMENT, PLAY_SERVICES_BASEMENT,
                    PLAY_SERVICES_TASKS), "com.google.android.gms.base"),

            new BuiltInLibrary(PLAY_SERVICES_BASEMENT, List.of(ANDROIDX_COLLECTION_JVM, ANDROIDX_CORE, ANDROIDX_FRAGMENT), "com.google.android.gms.common"),

            new BuiltInLibrary(PLAY_SERVICES_CLOUD_MESSAGING, List.of(PLAY_SERVICES_BASE, PLAY_SERVICES_BASEMENT, PLAY_SERVICES_TASKS)),

            new BuiltInLibrary(PLAY_SERVICES_GASS, List.of(PLAY_SERVICES_ADS_BASE, PLAY_SERVICES_ADS_IDENTIFIER, PLAY_SERVICES_ADS_LITE,
                    PLAY_SERVICES_BASEMENT, PLAY_SERVICES_TASKS)),

            new BuiltInLibrary(PLAY_SERVICES_GCM, List.of(ANDROIDX_COLLECTION_JVM, ANDROIDX_CORE, ANDROIDX_LEGACY_SUPPORT_CORE_UTILS,
                    PLAY_SERVICES_BASE, PLAY_SERVICES_BASEMENT, PLAY_SERVICES_IID,
                    PLAY_SERVICES_STATS), "com.google.android.gms.gcm"),

            new BuiltInLibrary(PLAY_SERVICES_IID, List.of(ANDROIDX_COLLECTION_JVM, ANDROIDX_CORE, PLAY_SERVICES_BASE,
                    PLAY_SERVICES_BASEMENT, PLAY_SERVICES_STATS, PLAY_SERVICES_TASKS)),

            new BuiltInLibrary(PLAY_SERVICES_LOCATION, List.of(PLAY_SERVICES_BASE, PLAY_SERVICES_BASEMENT, PLAY_SERVICES_PLACES_PLACEREPORT,
                    PLAY_SERVICES_TASKS)),

            new BuiltInLibrary(PLAY_SERVICES_MAPS, List.of(ANDROIDX_FRAGMENT, PLAY_SERVICES_BASE, PLAY_SERVICES_BASEMENT), "com.google.android.gms.maps"),

            new BuiltInLibrary(PLAY_SERVICES_MEASUREMENT_BASE, List.of(PLAY_SERVICES_BASEMENT)),

            new BuiltInLibrary(PLAY_SERVICES_MEASUREMENT_SDK_API, List.of(PLAY_SERVICES_BASEMENT, PLAY_SERVICES_MEASUREMENT_BASE)),

            new BuiltInLibrary(PLAY_SERVICES_PLACES_PLACEREPORT, List.of(PLAY_SERVICES_BASEMENT)),

            new BuiltInLibrary(PLAY_SERVICES_STATS, List.of(ANDROIDX_LEGACY_SUPPORT_CORE_UTILS, PLAY_SERVICES_BASEMENT)),

            new BuiltInLibrary(PLAY_SERVICES_TASKS, List.of(PLAY_SERVICES_BASEMENT)),

            new BuiltInLibrary(USER_MESSAGING_PLATFORM, List.of(ANDROIDX_ANNOTATION_JVM, PLAY_SERVICES_ADS_IDENTIFIER, PLAY_SERVICES_BASEMENT)),

            new BuiltInLibrary(TRANSPORT_API, List.of(ANDROIDX_ANNOTATION_JVM)),

            new BuiltInLibrary(TRANSPORT_BACKEND_CCT, List.of(TRANSPORT_API, TRANSPORT_RUNTIME)),

            new BuiltInLibrary(TRANSPORT_RUNTIME, List.of(TRANSPORT_API)),

            new BuiltInLibrary(WAVE_SIDE_BAR, List.of(), "com.sayuti.lib"),

    };

    /**
     * Lazily built name -> library index so {@link BuiltInLibrary#ofName(String)} is O(1).
     */
    private static final class LibraryIndex {
        static final Map<String, BuiltInLibrary> BY_NAME = build();

        private static Map<String, BuiltInLibrary> build() {
            Map<String, BuiltInLibrary> map = new HashMap<>();
            for (BuiltInLibrary library : KNOWN_BUILT_IN_LIBRARIES) {
                map.putIfAbsent(library.getName(), library);
            }
            return map;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Path helpers
    // ---------------------------------------------------------------------------------------------

    public static File getLibraryPath(String libraryName) {
        return new File(EXTRACTED_BUILT_IN_LIBRARIES_PATH, libraryName);
    }

    public static String getLibraryPathString(String libraryName) {
        return getLibraryPath(libraryName).getAbsolutePath();
    }

    public static File getLibraryClassesJarPath(String libraryName) {
        return new File(getLibraryPath(libraryName), "classes.jar");
    }

    public static String getLibraryClassesJarPathString(String libraryName) {
        return getLibraryClassesJarPath(libraryName).getAbsolutePath();
    }

    public static File getLibraryDexFile(String libraryName) {
        return new File(EXTRACTED_BUILT_IN_LIBRARY_DEX_FILES_PATH, libraryName + ".dex");
    }

    public static String getLibraryDexFilePath(String libraryName) {
        return getLibraryDexFile(libraryName).getAbsolutePath();
    }

    /**
     * @throws IllegalArgumentException Thrown if the specified library doesn't have any assets.
     */
    public static File getLibraryAssets(String libraryName) {
        Jp library = new Jp(libraryName);

        if (library.hasAssets()) {
            return new File(getLibraryPath(libraryName), "assets");
        } else {
            throw new IllegalArgumentException("Built-in library '" + libraryName + "' doesn't have any assets.");
        }
    }

    /**
     * @throws IllegalArgumentException Thrown if the specified library doesn't have any assets.
     */
    public static String getLibraryAssetsPath(String libraryName) {
        return getLibraryAssets(libraryName).getAbsolutePath();
    }

    public static File getLibraryResources(String libraryName) {
        Jp library = new Jp(libraryName);

        if (library.hasResources()) {
            return new File(getLibraryPath(libraryName), "res");
        } else {
            throw new IllegalArgumentException("Built-in library '" + libraryName + "' doesn't have any resources.");
        }
    }

    public static String getLibraryResourcesPath(String libraryName) {
        return getLibraryResources(libraryName).getAbsolutePath();
    }

    public static File getLibraryProguardConfiguration(String libraryName) {
        return new File(getLibraryPath(libraryName), "proguard.txt");
    }

    public static String getLibraryProguardConfigurationPath(String libraryName) {
        return getLibraryProguardConfiguration(libraryName).getAbsolutePath();
    }

    // ---------------------------------------------------------------------------------------------
    // Extraction
    //
    // How it works (and why it no longer half-extracts):
    //  1. Every archive is unzipped into a temp dir first, never straight into the live dir.
    //  2. The temp dir is validated, then swapped into place.
    //  3. Only AFTER the swap succeeds is a stamp file (app install time) written.
    //  4. On the next build, "valid" means: stamp matches this install AND the content looks sane.
    //     A crash/kill/low-storage mid-extract leaves no stamp, so it re-extracts cleanly.
    //  5. A new APK install changes the stamp, so updated assets always get re-extracted.
    // ---------------------------------------------------------------------------------------------

    public static void extractCompileAssets(@NonNull BuildProgressReceiver... progressReceivers) {
        synchronized (ProjectBuilder.ASSET_LOCK) {
            ensureDirectory(EXTRACTED_COMPILE_ASSETS_PATH);
            ensureFreeSpace(EXTRACTED_COMPILE_ASSETS_PATH);

            maybeExtractAndroidJar(progressReceivers);

            ensureDirectoryArchive(DEXS_ARCHIVE, EXTRACTED_BUILT_IN_LIBRARY_DEX_FILES_PATH,
                    "Extracting built-in libraries' DEX files...", 4, progressReceivers);

            ensureDirectoryArchive(LIBS_ARCHIVE, EXTRACTED_BUILT_IN_LIBRARIES_PATH,
                    "Extracting built-in libraries' resources...", 5, progressReceivers);

            maybeExtractCoreLambdaStubsJar();

            ensureDirectoryArchive(TESTKEY_ARCHIVE, new File(EXTRACTED_COMPILE_ASSETS_PATH, "testkey"),
                    "Extracting built-in signing keys...", 6, progressReceivers);
        }
    }

    public static void maybeExtractAndroidJar(@NonNull BuildProgressReceiver... receivers) {
        synchronized (ProjectBuilder.ASSET_LOCK) {
            ensureDirectory(EXTRACTED_COMPILE_ASSETS_PATH);

            File targetJar = new File(EXTRACTED_COMPILE_ASSETS_PATH, "android.jar");
            File stampFile = stampFileFor(ANDROID_JAR_ARCHIVE);
            String stamp = currentStamp(ANDROID_JAR_ARCHIVE);

            if (isStampCurrent(stampFile, stamp) && isValidZipArchive(targetJar)) {
                return;
            }

            String assetPath = ASSET_BASE + ANDROID_JAR_ARCHIVE;
            File tempDir = tempDirFor("android.jar");

            extractWithRetry(assetPath, () -> {
                report(receivers, "Extracting built-in android.jar...", 7);

                stampFile.delete();
                // Also wipes the case where android.jar somehow ended up being a directory.
                deleteRecursively(targetJar);
                deleteRecursively(tempDir);

                unzipAsset(assetPath, tempDir);

                File extractedJar = findFile(tempDir, "android.jar");
                if (extractedJar == null) {
                    throw new IOException("'" + assetPath + "' doesn't contain an android.jar");
                }
                if (!isValidZipArchive(extractedJar)) {
                    throw new IOException("android.jar from '" + assetPath + "' is corrupted");
                }
                if (!extractedJar.renameTo(targetJar)) {
                    copyFile(extractedJar, targetJar);
                }
                deleteRecursively(tempDir);

                if (!isValidZipArchive(targetJar)) {
                    throw new IOException("Extracted android.jar is missing or corrupted at " + targetJar.getAbsolutePath());
                }
                writeStamp(stampFile, stamp);
            }, tempDir, targetJar);
        }
    }

    public static void maybeExtractCoreLambdaStubsJar() {
        synchronized (ProjectBuilder.ASSET_LOCK) {
            String assetPath = ASSET_BASE + CORE_LAMBDA_STUBS_JAR;
            File target = new File(EXTRACTED_COMPILE_ASSETS_PATH, CORE_LAMBDA_STUBS_JAR);
            try {
                // Same copy-if-changed helper the rest of the builder uses.
                ProjectBuilder.hasFileChanged(assetPath, target.getAbsolutePath());
            } catch (Throwable t) {
                LogUtil.w(TAG, "Failed to copy " + assetPath, t);
            }
            if (!target.isFile() || target.length() == 0) {
                LogUtil.w(TAG, CORE_LAMBDA_STUBS_JAR + " is missing at " + target.getAbsolutePath());
            }
        }
    }

    private static void ensureDirectoryArchive(String archiveName, File targetDir, String progressMessage,
                                               int progressStep, BuildProgressReceiver[] receivers) {
        File stampFile = stampFileFor(archiveName);
        String stamp = currentStamp(archiveName);

        if (isStampCurrent(stampFile, stamp) && isDirectoryValid(targetDir)) {
            return;
        }

        String assetPath = ASSET_BASE + archiveName;
        File tempDir = tempDirFor(archiveName);

        LogUtil.w(TAG, "'" + archiveName + "' needs (re-)extraction into " + targetDir.getAbsolutePath());

        extractWithRetry(assetPath, () -> {
            report(receivers, progressMessage, progressStep);

            stampFile.delete();
            deleteRecursively(tempDir);

            unzipAsset(assetPath, tempDir);
            if (!isDirectoryValid(tempDir)) {
                throw new IOException("Extracted directory is empty for asset filename: " + assetPath);
            }

            deleteRecursively(targetDir);
            if (!tempDir.renameTo(targetDir)) {
                try {
                    copyDirectory(tempDir, targetDir);
                    deleteRecursively(tempDir);
                } catch (Exception e) {
                    throw new IOException("Failed to move/copy " + tempDir + " to " + targetDir + ": " + e.getMessage(), e);
                }
            }
            if (!isDirectoryValid(targetDir)) {
                throw new IOException("Extracted directory is missing or empty for asset filename: " + assetPath);
            }
            writeStamp(stampFile, stamp);
        }, tempDir, targetDir);
    }

    @FunctionalInterface
    private interface ExtractAction {
        void run() throws Exception;
    }

    private static void extractWithRetry(String assetPath, ExtractAction action, File... cleanupOnFailure) {
        Throwable last = null;
        for (int attempt = 1; attempt <= MAX_EXTRACT_ATTEMPTS; attempt++) {
            try {
                action.run();
                return;
            } catch (Throwable e) {
                last = e;
                LogUtil.w(TAG, "Failed attempt " + attempt + "/" + MAX_EXTRACT_ATTEMPTS + " extracting asset: " + assetPath, e);
                for (File file : cleanupOnFailure) {
                    deleteRecursively(file);
                }
                if (attempt < MAX_EXTRACT_ATTEMPTS) {
                    try {
                        Thread.sleep(150L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        LogUtil.e(TAG, "CRITICAL: Permanent failure extracting asset: " + assetPath, last);
        throw new RuntimeException("Failed to extract '" + assetPath + "' after " + MAX_EXTRACT_ATTEMPTS
                + " attempts: " + (last != null ? last.getMessage() : "unknown error")
                + ". If this says the asset doesn't exist, the APK was built without it.", last);
    }

    private static void unzipAsset(String assetPath, File destDir) throws IOException {
        ensureDirectory(destDir);

        String destRoot = destDir.getCanonicalPath();
        String destPrefix = destRoot + File.separator;
        byte[] buffer = new byte[64 * 1024];
        int fileCount = 0;

        Context context = SketchApplication.getContext();
        try (InputStream raw = context.getAssets().open(assetPath);
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw, 64 * 1024))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                File out = new File(destDir, name);
                String canonical = out.getCanonicalPath();

                // Zip Slip guard
                if (!canonical.equals(destRoot) && !canonical.startsWith(destPrefix)) {
                    throw new IOException("Blocked unsafe zip entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    ensureDirectory(out);
                    continue;
                }

                File parent = out.getParentFile();
                if (parent != null) {
                    ensureDirectory(parent);
                }

                if (out.exists() && out.isDirectory()) {
                    deleteRecursively(out);
                }

                try (OutputStream os = new FileOutputStream(out)) {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        os.write(buffer, 0, read);
                    }
                    os.flush();
                }
                fileCount++;
            }
        }

        if (fileCount == 0) {
            throw new IOException("Archive '" + assetPath + "' contained no files");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Stamp + validation helpers
    // ---------------------------------------------------------------------------------------------

    private static String currentStamp(String archiveName) {
        try {
            Context context = SketchApplication.getContext();
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            long assetSize = 0;
            try (InputStream is = context.getAssets().open(ASSET_BASE + archiveName)) {
                assetSize = is.available();
            } catch (Throwable ignored) {
            }
            return info.lastUpdateTime + "/" + info.versionName + "/" + assetSize;
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private static File stampFileFor(String archiveName) {
        return new File(EXTRACTED_COMPILE_ASSETS_PATH, ".stamp-" + archiveName);
    }

    private static File tempDirFor(String name) {
        return new File(EXTRACTED_COMPILE_ASSETS_PATH, ".tmp-" + name);
    }

    private static boolean isStampCurrent(File stampFile, String expected) {
        if (!stampFile.isFile()) {
            return false;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(stampFile), StandardCharsets.UTF_8))) {
            return expected.equals(reader.readLine());
        } catch (IOException e) {
            return false;
        }
    }

    private static void writeStamp(File stampFile, String stamp) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(stampFile)) {
            fos.write(stamp.getBytes(StandardCharsets.UTF_8));
            fos.flush();
            fos.getFD().sync();
        }
    }

    private static boolean isDirectoryValid(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return false;
        }
        String[] files = dir.list();
        return files != null && files.length > 0;
    }

    private static boolean isValidZipArchive(File file) {
        if (file == null || !file.isFile() || file.length() == 0) {
            return false;
        }
        try (ZipFile zip = new ZipFile(file)) {
            return zip.entries().hasMoreElements();
        } catch (IOException e) {
            return false;
        }
    }

    private static void ensureFreeSpace(File dir) {
        long free = dir.getUsableSpace();
        if (free > 0 && free < MIN_FREE_SPACE_BYTES) {
            throw new RuntimeException(new IOException("Not enough free storage to extract built-in libraries ("
                    + (free / (1024 * 1024)) + " MB free)"));
        }
    }

    private static void report(BuildProgressReceiver[] receivers, String message, int step) {
        for (BuildProgressReceiver receiver : receivers) {
            receiver.onProgress(message, step);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // File helpers (plain java.io, no obfuscated utility classes)
    // ---------------------------------------------------------------------------------------------

    private static void ensureDirectory(File dir) {
        if (dir == null) return;
        if (dir.isDirectory()) return;

        File parent = dir.getParentFile();
        if (parent != null) {
            ensureDirectory(parent);
        }

        if (dir.exists() && !dir.isDirectory()) {
            deleteRecursively(dir);
        }

        if (!dir.exists()) {
            if (!dir.mkdir() && !dir.isDirectory()) {
                if (!dir.mkdirs() && !dir.isDirectory()) {
                    throw new RuntimeException(new IOException("Failed to create directory " + dir));
                }
            }
        }
    }

    private static void copyDirectory(File src, File dest) throws IOException {
        if (src == null || !src.exists()) {
            return;
        }
        if (src.isDirectory()) {
            ensureDirectory(dest);
            File[] children = src.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyDirectory(child, new File(dest, child.getName()));
                }
            }
        } else {
            File parent = dest.getParentFile();
            if (parent != null) {
                ensureDirectory(parent);
            }
            if (dest.exists() && dest.isDirectory()) {
                deleteRecursively(dest);
            }
            try (InputStream in = new FileInputStream(src);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[64 * 1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.flush();
            }
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete() && file.exists()) {
            LogUtil.w(TAG, "Couldn't delete " + file.getAbsolutePath());
        }
    }

    private static File findFile(File root, String name) {
        File[] children = root.listFiles();
        if (children == null) {
            return null;
        }
        for (File child : children) {
            if (child.isFile() && child.getName().equals(name)) {
                return child;
            }
        }
        for (File child : children) {
            if (child.isDirectory()) {
                File found = findFile(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void copyFile(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Model
    // ---------------------------------------------------------------------------------------------

    public static class BuiltInLibrary implements Parcelable {
        public static final Creator<BuiltInLibrary> CREATOR = new Creator<>() {
            @Override
            public BuiltInLibrary createFromParcel(Parcel in) {
                return new BuiltInLibrary(in);
            }

            @Override
            public BuiltInLibrary[] newArray(int size) {
                return new BuiltInLibrary[size];
            }
        };

        private final String name;
        private final List<String> dependencyNames;
        private final String packageName;
        private final boolean hasResources;

        /**
         * @param packageName Can be <code>null</code> for no resources, though then
         *                    {@link #BuiltInLibrary(String, List)} is advised.
         */
        public BuiltInLibrary(String name, List<String> dependencyNames, String packageName) {
            this.name = name;
            this.dependencyNames = dependencyNames;
            this.packageName = packageName;
            hasResources = packageName != null;
        }

        /**
         * Constructs a {@link BuiltInLibrary} with specified dependencies but no resources.
         */
        public BuiltInLibrary(String name, List<String> dependencyNames) {
            this(name, dependencyNames, null);
        }

        /**
         * Constructs a {@link BuiltInLibrary} with no dependencies and resources.
         */
        public BuiltInLibrary(String name) {
            this(name, List.of());
        }

        protected BuiltInLibrary(Parcel in) {
            name = in.readString();
            dependencyNames = in.createStringArrayList();
            packageName = in.readString();
            hasResources = in.readInt() != 0;
        }

        public static Optional<BuiltInLibrary> ofName(String name) {
            return Optional.ofNullable(LibraryIndex.BY_NAME.get(name));
        }

        public String getName() {
            return name;
        }

        public List<String> getDependencyNames() {
            return dependencyNames;
        }

        public Optional<String> getPackageName() {
            return Optional.ofNullable(packageName);
        }

        public boolean hasResources() {
            return hasResources;
        }

        @Override
        @NonNull
        public String toString() {
            return "BuiltInLibrary{" +
                    "name='" + name + '\'' +
                    ", dependencyNames=" + dependencyNames +
                    ", packageName='" + packageName + '\'' +
                    ", hasResources=" + hasResources +
                    '}';
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(name);
            dest.writeStringList(dependencyNames);
            dest.writeString(packageName);
            dest.writeInt(hasResources ? 1 : 0);
        }
    }
}
