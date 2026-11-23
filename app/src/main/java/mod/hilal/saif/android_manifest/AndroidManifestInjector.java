package mod.hilal.saif.android_manifest;

import static sketchware.plus.utility.GsonUtils.getGson;

import android.os.Environment;

import com.google.gson.JsonParseException;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.hey.studios.util.Helper;
import sketchware.plus.utility.FileUtil;
import sketchware.plus.utility.SketchwareUtil;
import sketchware.plus.xml.XmlBuilder;

public class AndroidManifestInjector {

    public static File getPathAndroidManifestAttributeInjection(String sc_id) {
        return new File(Environment.getExternalStorageDirectory(),
                ".sketchware" + File.separator + "data" + File.separator + sc_id + File.separator +
                        "Injection" + File.separator + "androidmanifest" + File.separator + "attributes.json");
    }

    public static File getPathAndroidManifestLauncherActivity(String sc_id) {
        return new File(Environment.getExternalStorageDirectory(),
                ".sketchware" + File.separator + "data" + File.separator + sc_id + File.separator +
                        "Injection" + File.separator + "androidmanifest" + File.separator + "activity_launcher.txt");
    }

    public static File getPathAndroidManifestActivitiesComponents(String sc_id) {
        return new File(Environment.getExternalStorageDirectory(),
                ".sketchware" + File.separator + "data" + File.separator + sc_id + File.separator +
                        "Injection" + File.separator + "androidmanifest" + File.separator + "activities_components.json");
    }

    public static File getPathAndroidManifestAppComponents(String sc_id) {
        return new File(Environment.getExternalStorageDirectory(),
                ".sketchware" + File.separator + "data" + File.separator + sc_id + File.separator +
                        "Injection" + File.separator + "androidmanifest" + File.separator + "app_components.txt");
    }

    private static ArrayList<HashMap<String, Object>> readAndroidManifestAttributeInjections(String sc_id) {
        ArrayList<HashMap<String, Object>> attributes;

        parseAttributes:
        {
            String errorMessage;

            try {
                File injections = getPathAndroidManifestAttributeInjection(sc_id);

                if (injections.exists()) {
                    attributes = getGson().fromJson(FileUtil.readFile(injections.getAbsolutePath()), Helper.TYPE_MAP_LIST);

                    if (attributes == null) {
                        errorMessage = "result == null";
                        // fall-through for shared error handling
                    } else {
                        break parseAttributes;
                    }
                } else {
                    return new ArrayList<>();
                }
            } catch (JsonParseException e) {
                errorMessage = e.toString();
                // fall-through for shared error handling
            }

            attributes = new ArrayList<>();
            SketchwareUtil.toastError("Failed to parse AndroidManifest attribute injections; Reason: " + errorMessage);
        }

        return attributes;
    }

    public static void getP(XmlBuilder nx, String id) {
        ArrayList<HashMap<String, Object>> attributes = readAndroidManifestAttributeInjections(id);

        for (int i = 0; i < attributes.size(); i++) {
            HashMap<String, Object> attribute = attributes.get(i);
            Object name = attribute.get("name");

            if (name instanceof String) {
                Object value = attribute.get("value");

                if (value instanceof String) {
                    if ("_application_permissions".equals(name)) {
                        String permVal = ((String) value).trim();
                        if (!permVal.isEmpty()) {
                            XmlBuilder usesPermissionTag = new XmlBuilder("uses-permission");
                            if (permVal.contains("=")) {
                                usesPermissionTag.addAttributeValue(permVal);
                            } else {
                                usesPermissionTag.addAttribute("android", "name", permVal);
                            }
                            nx.addChildNode(usesPermissionTag);
                        }
                    }
                } else {
                    SketchwareUtil.toastError("Invalid AndroidManifest attribute injection value in attribute #" + (i + 1));
                }
            } else {
                SketchwareUtil.toastError("Invalid AndroidManifest attribute injection name in attribute #" + (i + 1));
            }
        }
    }

    public static void getAppAttrs(XmlBuilder nx, String projectId) {
        addToApp(nx, projectId);
    }

    public static boolean getActivityAttrs(XmlBuilder nx, String projectId, String actName) {
        ArrayList<HashMap<String, Object>> attributes = readAndroidManifestAttributeInjections(projectId);
        String className = actName.substring(0, actName.indexOf(".java"));

        for (int i = 0; i < attributes.size(); i++) {
            HashMap<String, Object> attribute = attributes.get(i);
            Object name = attribute.get("name");

            if (name instanceof String) {
                if (className.equals(name)) {
                    addToAct(nx, projectId, actName);
                    return true;
                }
            } else {
                SketchwareUtil.toastError("Invalid AndroidManifest attribute injection name in attribute #" + (i + 1));
            }
        }

        return false;
    }

    public static boolean isActivityThemeUsed(XmlBuilder nx, String projectId, String actName) {
        return isActivityAttributeUsed("android:theme", projectId, actName);
    }

    public static boolean isActivityOrientationUsed(XmlBuilder nx, String projectId, String actName) {
        return isActivityAttributeUsed("android:screenOrientation", projectId, actName);
    }

    public static boolean isActivityKeyboardUsed(XmlBuilder nx, String projectId, String actName) {
        return isActivityAttributeUsed("android:windowSoftInputMode", projectId, actName);
    }

    public static boolean isActivityExportedUsed(String sc_id, String activityName) {
        return isActivityAttributeUsed("android:exported", sc_id, activityName);
    }

    public static boolean isActivityAttributeUsed(String attribute, String sc_id, String activityName) {
        ArrayList<HashMap<String, Object>> attributes = readAndroidManifestAttributeInjections(sc_id);
        String className = activityName.substring(0, activityName.indexOf(".java"));

        for (int i = 0; i < attributes.size(); i++) {
            HashMap<String, Object> attributeMap = attributes.get(i);
            Object name = attributeMap.get("name");

            if (name instanceof String) {
                if (className.equals(name) || "_apply_for_all_activities".equals(name)) {
                    Object value = attributeMap.get("value");

                    if (value instanceof String) {
                        if (((String) value).contains(attribute)) {
                            return true;
                        }
                    } else {
                        SketchwareUtil.toastError("Invalid AndroidManifest attribute injection value in attribute #" + (i + 1));
                    }
                }
            } else {
                SketchwareUtil.toastError("Invalid AndroidManifest attribute injection name in attribute #" + (i + 1));
            }
        }

        return false;
    }

    public static String getLauncherActivity(String projectId) {
        File launcherActivityFile = getPathAndroidManifestLauncherActivity(projectId);

        if (launcherActivityFile.exists()) {
            String launcherActivity = FileUtil.readFile(launcherActivityFile.getAbsolutePath());

            if (!launcherActivity.contains(" ") && !launcherActivity.contains(".")) {
                return launcherActivity;
            }
        }

        return "main";
    }

    public static void setLauncherActivity(String projectId, String a) {
        FileUtil.writeFile(getPathAndroidManifestLauncherActivity(projectId).getAbsolutePath(),
                a);
    }

    public static String mHolder(String m, String projectId) {
        ArrayList<String> manifestLines = new ArrayList<>(Arrays.asList(m.split("\n")));

        String path = getPathAndroidManifestActivitiesComponents(projectId).getAbsolutePath();
        if (FileUtil.isExistFile(path)) {
            ArrayList<HashMap<String, Object>> data = getGson().fromJson(FileUtil.readFile(path), Helper.TYPE_MAP_LIST);
            for (int i = 0; i < data.size(); i++) {
                HashMap<String, Object> activityComponents = data.get(i);

                Object name = activityComponents.get("name");

                if (name instanceof String) {
                    Object value = activityComponents.get("value");

                    if (value instanceof String) {
                        if (!((String) value).trim().isEmpty()) {
                            for (int k = 3; k < manifestLines.size(); k++) {
                                String line = manifestLines.get(k);
                                String _line = manifestLines.get(k - 1);
                                if (line.contains("android:name=\"") && line.contains((String) name) && _line.contains("<activity")) {
                                    for (int q = k; q < manifestLines.size(); q++) {
                                        String v = manifestLines.get(q);
                                        String v2 = manifestLines.get(q - 1);
                                        if (v.matches("^		<[a-zA-Z_-]+[^>]")) {
                                            boolean hasShortClosing = false, spaceBeforeClosing = false;

                                            if (v2.contains("\"/>")) {
                                                hasShortClosing = true;
                                            } else if (v2.contains("\" />")) {
                                                hasShortClosing = true;
                                                spaceBeforeClosing = true;
                                            }

                                            if (hasShortClosing) {
                                                manifestLines.set(q - 1, manifestLines.get(q - 1).replace(spaceBeforeClosing ? "\" />" : "\"/>", "\">"));
                                                manifestLines.set(q - 1, manifestLines.get(q - 1) + "\r\n" + value + "\r\n" + "</activity>");
                                            } else {
                                                manifestLines.set(q - 2, manifestLines.get(q - 2) + "\r\n" + value);
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        File appComponents = getPathAndroidManifestAppComponents(projectId);
        if (appComponents.exists()) {
            String appComponentsContent;
            if (!(appComponentsContent = FileUtil.readFile(appComponents.getAbsolutePath())).trim().isEmpty()) {
                String str2 = manifestLines.get(manifestLines.size() - 3);
                String str3 = str2 + "\r\n" + appComponentsContent;
                manifestLines.set(manifestLines.size() - 3, str3);
            }
        }

        //assemble
        StringBuilder returnValue = new StringBuilder();
        for (String manifestLine : manifestLines) {
            returnValue.append("\n").append(manifestLine);
        }
        return cleanManifestXml(returnValue.toString());
    }

    /**
     * Checks if a registrar class is programmatically registered by FirebaseApp in firebase-common.
     * Modern firebase-common (23.0.0+) registers FirebaseCommonRegistrar and ExecutorsRegistrar directly
     * in Java code. Including them in AndroidManifest.xml causes ComponentDiscovery to instantiate them a second time,
     * triggering "Multiple components provide com.google.firebase.platforminfo.UserAgentPublisher" at runtime.
     */
    private static boolean isProgrammaticallyRegisteredInFirebaseApp(String registrarClass) {
        if (registrarClass == null) {
            return false;
        }

        return "com.google.firebase.FirebaseCommonRegistrar".equals(registrarClass)
                || "com.google.firebase.FirebaseCommonKtxRegistrar".equals(registrarClass)
                || "com.google.firebase.ktx.FirebaseCommonKtxRegistrar".equals(registrarClass)
                || "com.google.firebase.ktx.FirebaseCommonLegacyRegistrar".equals(registrarClass)
                || "com.google.firebase.concurrent.ExecutorsRegistrar".equals(registrarClass)
                || "com.google.firebase.FirebaseExecutorsRegistrar".equals(registrarClass);
    }

    /**
     * Maps Firebase ComponentRegistrar class names to their canonical registrar class.
     * Firebase KTX and legacy registrars provide duplicate components (such as UserAgentPublisher,
     * FirebaseAuth, etc.) which cause runtime crashes with:
     * "java.lang.IllegalArgumentException: Multiple components provide com.google.firebase.platforminfo.UserAgentPublisher."
     */
    private static String getCanonicalRegistrar(String registrarClass) {
        if (registrarClass == null) {
            return null;
        }

        // Common / Core
        if ("com.google.firebase.FirebaseCommonRegistrar".equals(registrarClass)
                || "com.google.firebase.FirebaseCommonKtxRegistrar".equals(registrarClass)
                || "com.google.firebase.ktx.FirebaseCommonKtxRegistrar".equals(registrarClass)
                || "com.google.firebase.ktx.FirebaseCommonLegacyRegistrar".equals(registrarClass)) {
            return "com.google.firebase.FirebaseCommonRegistrar";
        }

        // Auth
        if ("com.google.firebase.auth.FirebaseAuthRegistrar".equals(registrarClass)
                || "com.google.firebase.auth.FirebaseAuthKtxRegistrar".equals(registrarClass)
                || "com.google.firebase.auth.ktx.FirebaseAuthKtxRegistrar".equals(registrarClass)) {
            return "com.google.firebase.auth.FirebaseAuthRegistrar";
        }

        // Database
        if ("com.google.firebase.database.FirebaseDatabaseRegistrar".equals(registrarClass)
                || "com.google.firebase.database.FirebaseDatabaseKtxRegistrar".equals(registrarClass)
                || "com.google.firebase.database.ktx.FirebaseDatabaseKtxRegistrar".equals(registrarClass)) {
            return "com.google.firebase.database.FirebaseDatabaseRegistrar";
        }

        // Storage
        if ("com.google.firebase.storage.StorageRegistrar".equals(registrarClass)
                || "com.google.firebase.storage.StorageKtxRegistrar".equals(registrarClass)
                || "com.google.firebase.storage.ktx.StorageKtxRegistrar".equals(registrarClass)) {
            return "com.google.firebase.storage.StorageRegistrar";
        }

        // Messaging
        if ("com.google.firebase.messaging.FirebaseMessagingRegistrar".equals(registrarClass)
                || "com.google.firebase.messaging.FirebaseMessagingKtxRegistrar".equals(registrarClass)
                || "com.google.firebase.messaging.ktx.FirebaseMessagingKtxRegistrar".equals(registrarClass)) {
            return "com.google.firebase.messaging.FirebaseMessagingRegistrar";
        }

        // Firestore
        if ("com.google.firebase.firestore.FirestoreRegistrar".equals(registrarClass)
                || "com.google.firebase.firestore.FirestoreKtxRegistrar".equals(registrarClass)
                || "com.google.firebase.firestore.ktx.FirestoreKtxRegistrar".equals(registrarClass)) {
            return "com.google.firebase.firestore.FirestoreRegistrar";
        }

        // Crashlytics
        if ("com.google.firebase.crashlytics.CrashlyticsRegistrar".equals(registrarClass)
                || "com.google.firebase.crashlytics.CrashlyticsKtxRegistrar".equals(registrarClass)
                || "com.google.firebase.crashlytics.ktx.CrashlyticsKtxRegistrar".equals(registrarClass)) {
            return "com.google.firebase.crashlytics.CrashlyticsRegistrar";
        }

        // Remote Config
        if ("com.google.firebase.remoteconfig.RemoteConfigRegistrar".equals(registrarClass)
                || "com.google.firebase.remoteconfig.RemoteConfigKtxRegistrar".equals(registrarClass)
                || "com.google.firebase.remoteconfig.ktx.RemoteConfigKtxRegistrar".equals(registrarClass)) {
            return "com.google.firebase.remoteconfig.RemoteConfigRegistrar";
        }

        // Installations
        if ("com.google.firebase.installations.FirebaseInstallationsRegistrar".equals(registrarClass)
                || "com.google.firebase.installations.FirebaseInstallationsKtxRegistrar".equals(registrarClass)
                || "com.google.firebase.installations.ktx.FirebaseInstallationsKtxRegistrar".equals(registrarClass)) {
            return "com.google.firebase.installations.FirebaseInstallationsRegistrar";
        }

        // InAppMessaging
        if ("com.google.firebase.inappmessaging.FirebaseInAppMessagingRegistrar".equals(registrarClass)
                || "com.google.firebase.inappmessaging.FirebaseInAppMessagingKtxRegistrar".equals(registrarClass)
                || "com.google.firebase.inappmessaging.ktx.FirebaseInAppMessagingKtxRegistrar".equals(registrarClass)) {
            return "com.google.firebase.inappmessaging.FirebaseInAppMessagingRegistrar";
        }

        // AppCheck
        if ("com.google.firebase.appcheck.FirebaseAppCheckRegistrar".equals(registrarClass)
                || "com.google.firebase.appcheck.FirebaseAppCheckKtxRegistrar".equals(registrarClass)
                || "com.google.firebase.appcheck.ktx.FirebaseAppCheckKtxRegistrar".equals(registrarClass)) {
            return "com.google.firebase.appcheck.FirebaseAppCheckRegistrar";
        }

        // Functions
        if ("com.google.firebase.functions.FunctionsRegistrar".equals(registrarClass)
                || "com.google.firebase.functions.FunctionsKtxRegistrar".equals(registrarClass)
                || "com.google.firebase.functions.ktx.FunctionsKtxRegistrar".equals(registrarClass)) {
            return "com.google.firebase.functions.FunctionsRegistrar";
        }

        // Perf
        if ("com.google.firebase.perf.FirebasePerfRegistrar".equals(registrarClass)
                || "com.google.firebase.perf.FirebasePerfKtxRegistrar".equals(registrarClass)
                || "com.google.firebase.perf.ktx.FirebasePerfKtxRegistrar".equals(registrarClass)) {
            return "com.google.firebase.perf.FirebasePerfRegistrar";
        }

        return registrarClass;
    }

    /**
     * Sanitizes and deduplicates Firebase metadata and providers in AndroidManifest.xml.
     * Prevents runtime crashes caused by duplicate FirebaseInitProvider or duplicate / conflicting
     * ComponentRegistrar meta-data entries (e.g. "Multiple components provide com.google.firebase.platforminfo.UserAgentPublisher").
     */
    public static String cleanManifestXml(String rawXml) {
        if (rawXml == null || rawXml.isEmpty()) {
            return rawXml;
        }

        String cleanedXml = rawXml;

        // 1. Deduplicate FirebaseInitProvider
        if (cleanedXml.contains("com.google.firebase.provider.FirebaseInitProvider")) {
            int firstIdx = cleanedXml.indexOf("com.google.firebase.provider.FirebaseInitProvider");
            int nextIdx = cleanedXml.indexOf("com.google.firebase.provider.FirebaseInitProvider", firstIdx + 1);
            while (nextIdx != -1) {
                int blockStart = cleanedXml.lastIndexOf("<provider", nextIdx);
                int blockEnd = cleanedXml.indexOf("/>", nextIdx);
                if (blockEnd != -1) {
                    int endWithTag = cleanedXml.indexOf("</provider>", nextIdx);
                    if (endWithTag != -1 && endWithTag < blockEnd + 20) {
                        blockEnd = endWithTag + "</provider>".length();
                    } else {
                        blockEnd += 2;
                    }
                }
                if (blockStart != -1 && blockEnd != -1 && blockEnd > blockStart) {
                    cleanedXml = cleanedXml.substring(0, blockStart) + cleanedXml.substring(blockEnd);
                } else {
                    break;
                }
                nextIdx = cleanedXml.indexOf("com.google.firebase.provider.FirebaseInitProvider", firstIdx + 1);
            }
        }

        // 2. Deduplicate and normalize Firebase ComponentRegistrar meta-data tags
        if (cleanedXml.contains("com.google.firebase.components:")) {
            Set<String> seenCanonicalRegistrars = new HashSet<>();
            Pattern metaDataPattern = Pattern.compile(
                "<meta-data\\s+[^>]*?android:name=[\"']com\\.google\\.firebase\\.components:([^\"']+)[\"'][^>]*?(/?>|></meta-data>)",
                Pattern.DOTALL
            );
            Matcher matcher = metaDataPattern.matcher(cleanedXml);
            StringBuffer sb = new StringBuffer();

            while (matcher.find()) {
                String registrarClass = matcher.group(1);
                String canonicalClass = getCanonicalRegistrar(registrarClass);
                boolean isDuplicate = isProgrammaticallyRegisteredInFirebaseApp(registrarClass)
                        || seenCanonicalRegistrars.contains(canonicalClass);

                if (isDuplicate) {
                    matcher.appendReplacement(sb, "");
                } else {
                    seenCanonicalRegistrars.add(canonicalClass);
                    String matchedText = matcher.group(0);
                    if (matchedText != null) {
                        // If the registrar class was a KTX or legacy variant, normalize the tag to the canonical class
                        if (!canonicalClass.equals(registrarClass)) {
                            matchedText = matchedText.replace("com.google.firebase.components:" + registrarClass,
                                    "com.google.firebase.components:" + canonicalClass);
                        }
                        matcher.appendReplacement(sb, Matcher.quoteReplacement(matchedText));
                    }
                }
            }
            matcher.appendTail(sb);
            cleanedXml = sb.toString();
        }

        // 3. Deduplicate ComponentDiscoveryService service tags
        if (cleanedXml.contains("com.google.firebase.components.ComponentDiscoveryService")) {
            Pattern servicePattern = Pattern.compile(
                "<service\\s+[^>]*?android:name=[\"']com\\.google\\.firebase\\.components\\.ComponentDiscoveryService[\"'][^>]*?>(.*?)</service>",
                Pattern.DOTALL
            );
            Matcher serviceMatcher = servicePattern.matcher(cleanedXml);
            if (serviceMatcher.find()) {
                int firstEnd = serviceMatcher.end();
                String prefix = cleanedXml.substring(0, firstEnd);
                String rest = cleanedXml.substring(firstEnd);

                String cleanedRest = rest.replaceAll(
                    "<service\\s+[^>]*?android:name=[\"']com\\.google\\.firebase\\.components\\.ComponentDiscoveryService[\"'][^>]*?>(.*?)</service>",
                    ""
                ).replaceAll(
                    "<service\\s+[^>]*?android:name=[\"']com\\.google\\.firebase\\.components\\.ComponentDiscoveryService[\"'][^>]*?/>",
                    ""
                );
                cleanedXml = prefix + cleanedRest;
            }
        }

        return cleanedXml;
    }

    public static void addToApp(XmlBuilder nx, String projectId) {
        ArrayList<HashMap<String, Object>> attributes = readAndroidManifestAttributeInjections(projectId);

        boolean themeInjected = false;
        for (int i = 0; i < attributes.size(); i++) {
            HashMap<String, Object> attribute = attributes.get(i);
            Object name = attribute.get("name");

            if (name instanceof String) {
                if ("_application_attrs".equals(name)) {
                    Object value = attribute.get("value");

                    if (value instanceof String) {
                        nx.addAttributeValue((String) value);

                        if (!themeInjected && ((String) value).contains("android:theme")) {
                            themeInjected = true;
                        }
                    } else {
                        SketchwareUtil.toastError("Invalid AndroidManifest attribute injection value in attribute #" + (i + 1));
                    }
                }
            } else {
                SketchwareUtil.toastError("Invalid AndroidManifest attribute injection name in attribute #" + (i + 1));
            }
        }

        if (!themeInjected) {
            nx.addAttributeValue("android:theme=\"@style/AppTheme\"");
        }
    }

    public static void addToAct(XmlBuilder nx, String projectId, String actName) {
        ArrayList<HashMap<String, Object>> attributes = readAndroidManifestAttributeInjections(projectId);
        String className = actName.substring(0, actName.indexOf(".java"));

        for (int i = 0; i < attributes.size(); i++) {
            HashMap<String, Object> attribute = attributes.get(i);
            Object name = attribute.get("name");

            if (name instanceof String) {
                if (className.equals(name) || "_apply_for_all_activities".equals(name)) {
                    Object value = attribute.get("value");

                    if (value instanceof String) {
                        nx.addAttributeValue((String) value);
                    } else {
                        SketchwareUtil.toastError("Invalid AndroidManifest attribute injection value in attribute #" + (i + 1));
                    }
                }
            } else {
                SketchwareUtil.toastError("Invalid AndroidManifest attribute injection name in attribute #" + (i + 1));
            }
        }
    }
}
