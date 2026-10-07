package sketchware.plus.ai;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.Ix;
import a.a.a.eC;
import a.a.a.hC;
import a.a.a.jC;
import a.a.a.jq;
import a.a.a.yq;
import mod.hey.studios.util.Helper;
import sketchware.plus.util.library.BuiltInLibraryManager;
import sketchware.plus.utility.FilePathUtil;
import sketchware.plus.utility.FileUtil;
import sketchware.plus.utility.GsonUtils;

public class SketchwareManifestBridge {

    /**
     * Generates the raw AndroidManifest.xml for the project exactly as Sketchware builds it.
     */
    public static String getRawManifest(Context context, String scId) {
        try {
            eC dataManager = jC.a(scId);
            hC fileManager = jC.b(scId);
            BuiltInLibraryManager builtInLibManager = new BuiltInLibraryManager(scId);

            yq workspace = new yq(context, scId);
            Ix ix = new Ix(dataManager.l, fileManager.c, builtInLibManager);
            ix.setYq(workspace);

            String manifest = ix.a();
            return manifest != null ? manifest : "<!-- Empty manifest -->";
        } catch (Exception e) {
            e.printStackTrace();
            return "<!-- Error generating manifest: " + e.getMessage() + " -->";
        }
    }

    /**
     * Extracts specific sections from the generated AndroidManifest.xml.
     * @param section "permissions", "components", "application_attributes", "activities", or "full"
     */
    public static String getManifestSection(Context context, String scId, String section) {
        String fullManifest = getRawManifest(context, scId);
        if (fullManifest.startsWith("<!-- Error")) return fullManifest;

        String sec = section != null ? section.toLowerCase().trim() : "full";

        switch (sec) {
            case "permissions": {
                StringBuilder sb = new StringBuilder("<!-- PERMISSIONS -->\n");
                Matcher m = Pattern.compile("<uses-permission[^>]*/>", Pattern.CASE_INSENSITIVE).matcher(fullManifest);
                while (m.find()) {
                    sb.append(m.group()).append("\n");
                }
                return sb.toString().trim();
            }
            case "components":
            case "activities": {
                StringBuilder sb = new StringBuilder("<!-- COMPONENTS & ACTIVITIES -->\n");
                Pattern p = Pattern.compile("<(activity|service|receiver|provider)[\\s\\S]*?</\\1>|<(activity|service|receiver|provider)[^>]*/>", Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(fullManifest);
                while (m.find()) {
                    sb.append(m.group()).append("\n\n");
                }
                return sb.toString().trim();
            }
            case "application_attributes": {
                Matcher m = Pattern.compile("<application[^>]*>", Pattern.CASE_INSENSITIVE).matcher(fullManifest);
                if (m.find()) {
                    return "<!-- APPLICATION ATTRIBUTES -->\n" + m.group();
                }
                return "<!-- Application tag not found -->";
            }
            default:
                return fullManifest;
        }
    }

    /**
     * Searches for entries in the AndroidManifest.xml matching a query.
     */
    public static String searchManifest(Context context, String scId, String query) {
        String fullManifest = getRawManifest(context, scId);
        if (fullManifest.startsWith("<!-- Error")) return fullManifest;

        if (query == null || query.trim().isEmpty()) return fullManifest;

        String qLower = query.toLowerCase().trim();
        String[] lines = fullManifest.split("\n");
        StringBuilder sb = new StringBuilder("<!-- SEARCH RESULTS FOR '" + query + "' -->\n");
        int count = 0;

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].toLowerCase().contains(qLower)) {
                int start = Math.max(0, i - 2);
                int end = Math.min(lines.length - 1, i + 2);
                sb.append("Line ").append(i + 1).append(":\n");
                for (int j = start; j <= end; j++) {
                    sb.append(j == i ? " > " : "   ").append(lines[j]).append("\n");
                }
                sb.append("\n");
                count++;
            }
        }

        if (count == 0) {
            return "No entries matching '" + query + "' found in AndroidManifest.xml.";
        }
        return sb.toString().trim();
    }

    /**
     * Parses AI-provided manifest XML/snippets and safely applies permissions and manifest injections
     * without damaging existing permission/injection logic.
     */
    public static boolean applyAiManifestToSketchware(Context context, String scId, String aiManifestXml) throws Exception {
        String sanitized = SketchwareXmlBridge.sanitizeAiXmlInput(aiManifestXml);
        if (sanitized == null || sanitized.trim().isEmpty()) {
            throw new IllegalArgumentException("Manifest XML input is empty.");
        }

        boolean updated = false;

        // 1. Process <uses-permission> tags
        Matcher permMatcher = Pattern.compile("<uses-permission[^>]*android:name=\"([^\"]+)\"[^>]*/>", Pattern.CASE_INSENSITIVE).matcher(sanitized);
        while (permMatcher.find()) {
            String permName = permMatcher.group(1);
            if (permName != null && !permName.trim().isEmpty()) {
                addPermission(context, scId, permName.trim());
                updated = true;
            }
        }

        // 2. Process Application Attributes inside <application ...>
        Matcher appAttrMatcher = Pattern.compile("<application([^>]*)>", Pattern.CASE_INSENSITIVE).matcher(sanitized);
        if (appAttrMatcher.find()) {
            String attrs = appAttrMatcher.group(1);
            if (attrs != null && !attrs.trim().isEmpty()) {
                addApplicationAttributeInjection(scId, attrs.trim());
                updated = true;
            }
        }

        // 3. Process Custom App Components (<activity>, <service>, <receiver>, <provider>)
        Pattern compPattern = Pattern.compile("<(activity|service|receiver|provider)[\\s\\S]*?</\\1>|<(activity|service|receiver|provider)[^>]*/>", Pattern.CASE_INSENSITIVE);
        Matcher compMatcher = compPattern.matcher(sanitized);
        while (compMatcher.find()) {
            String compXml = compMatcher.group().trim();
            if (!compXml.isEmpty()) {
                addComponentInjection(scId, compXml);
                updated = true;
            }
        }

        // 4. Regenerate manifest in workspace
        eC dataManager = jC.a(scId);
        hC fileManager = jC.b(scId);
        BuiltInLibraryManager builtInLibManager = new BuiltInLibraryManager(scId);
        yq workspace = new yq(context, scId);
        Ix ix = new Ix(dataManager.l, fileManager.c, builtInLibManager);
        ix.setYq(workspace);
        workspace.a("AndroidManifest.xml", ix.a());

        return updated;
    }

    private static void addPermission(Context context, String scId, String permission) {
        eC dataManager = jC.a(scId);
        int permMask = 0;
        if (permission.contains("CALL_PHONE")) permMask = jq.PERMISSION_CALL_PHONE;
        else if (permission.contains("INTERNET")) permMask = jq.PERMISSION_INTERNET;
        else if (permission.contains("VIBRATE")) permMask = jq.PERMISSION_VIBRATE;
        else if (permission.contains("ACCESS_NETWORK_STATE")) permMask = jq.PERMISSION_ACCESS_NETWORK_STATE;
        else if (permission.contains("CAMERA")) permMask = jq.PERMISSION_CAMERA;
        else if (permission.contains("READ_EXTERNAL_STORAGE")) permMask = jq.PERMISSION_READ_EXTERNAL_STORAGE;
        else if (permission.contains("WRITE_EXTERNAL_STORAGE")) permMask = jq.PERMISSION_WRITE_EXTERNAL_STORAGE;
        else if (permission.contains("RECORD_AUDIO")) permMask = jq.PERMISSION_RECORD_AUDIO;
        else if (permission.contains("BLUETOOTH")) permMask = jq.PERMISSION_BLUETOOTH;
        else if (permission.contains("BLUETOOTH_ADMIN")) permMask = jq.PERMISSION_BLUETOOTH_ADMIN;
        else if (permission.contains("ACCESS_FINE_LOCATION")) permMask = jq.PERMISSION_ACCESS_FINE_LOCATION;

        if (permMask != 0) {
            dataManager.l.addPermission(permMask);
        }

        try {
            FilePathUtil pathUtil = new FilePathUtil();
            String permissionFilePath = pathUtil.getPathPermission(scId);
            ArrayList<String> permList = new ArrayList<>();
            if (FileUtil.isExistFile(permissionFilePath)) {
                String existingContent = FileUtil.readFile(permissionFilePath);
                if (!existingContent.trim().isEmpty()) {
                    permList = GsonUtils.getGson().fromJson(existingContent, Helper.TYPE_STRING);
                }
            }
            if (permList == null) permList = new ArrayList<>();
            if (!permList.contains(permission)) {
                permList.add(permission);
                FileUtil.writeFile(permissionFilePath, GsonUtils.getGson().toJson(permList));
            }
        } catch (Exception ignored) {}
    }

    private static void addApplicationAttributeInjection(String scId, String value) {
        try {
            String path = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + scId + "/Injection/androidmanifest/attributes.json";
            ArrayList<HashMap<String, Object>> data = new ArrayList<>();
            if (FileUtil.isExistFile(path)) {
                data = GsonUtils.getGson().fromJson(FileUtil.readFile(path), Helper.TYPE_MAP_LIST);
            }
            if (data == null) data = new ArrayList<>();

            HashMap<String, Object> item = new HashMap<>();
            item.put("name", "_application_attrs");
            item.put("value", value);
            data.add(item);
            FileUtil.writeFile(path, GsonUtils.getGson().toJson(data));
        } catch (Exception ignored) {}
    }

    private static void addComponentInjection(String scId, String value) {
        try {
            String path = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + scId + "/Injection/androidmanifest/app_components.txt";
            String content = FileUtil.isExistFile(path) ? FileUtil.readFile(path) : "";
            if (!content.contains(value)) {
                content += "\n" + value;
                FileUtil.writeFile(path, content.trim());
            }
        } catch (Exception ignored) {}
    }
}
