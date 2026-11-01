package sketchware.plus.ai;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.Ix;
import a.a.a.eC;
import a.a.a.hC;
import a.a.a.jC;
import a.a.a.yq;
import a.a.a.wq;
import sketchware.plus.util.library.BuiltInLibraryManager;

public class SketchwareManifestBridge {

    private static String logPath;

    public static void setLogPath(String path) {
        logPath = path;
    }

    public static void log(String scId, String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String formatted = "[" + timestamp + "] [SketchwareManifestBridge] " + message + "\n";

        if (logPath != null) {
            try {
                File file = new File(logPath);
                if (!file.exists()) file.createNewFile();
                FileWriter writer = new FileWriter(file, true);
                writer.write(formatted);
                writer.close();
            } catch (Exception ignored) {}
        }

        if (scId != null && !scId.isEmpty()) {
            try {
                String primaryPath = wq.b(scId) + File.separator + "data.log";
                File primaryFile = new File(primaryPath);
                if (primaryFile.getParentFile() != null && !primaryFile.getParentFile().exists()) {
                    primaryFile.getParentFile().mkdirs();
                }
                if (!primaryFile.exists()) primaryFile.createNewFile();
                FileWriter writer1 = new FileWriter(primaryFile, true);
                writer1.write(formatted);
                writer1.close();

                String secondaryPath = wq.d(scId) + File.separator + "data.log";
                File secondaryFile = new File(secondaryPath);
                if (secondaryFile.getParentFile() != null && !secondaryFile.getParentFile().exists()) {
                    secondaryFile.getParentFile().mkdirs();
                }
                if (!secondaryFile.exists()) secondaryFile.createNewFile();
                FileWriter writer2 = new FileWriter(secondaryFile, true);
                writer2.write(formatted);
                writer2.close();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Generates the raw AndroidManifest.xml for the project exactly as Sketchware builds it.
     */
    public static String getRawManifest(Context context, String scId) {
        log(scId, "[getRawManifest] Starting manifest generation for scId: " + scId);
        try {
            eC dataManager = jC.a(scId);
            hC fileManager = jC.b(scId);
            BuiltInLibraryManager builtInLibManager = new BuiltInLibraryManager(scId);

            yq workspace = new yq(context, scId);
            Ix ix = new Ix(dataManager.l, fileManager.c, builtInLibManager);
            ix.setYq(workspace);

            String manifest = ix.a();
            boolean success = manifest != null && !manifest.isEmpty();
            String result = success ? manifest : "<!-- Empty manifest -->";
            log(scId, "[getRawManifest] Success: " + success + " | Generated Manifest Length: " + (manifest != null ? manifest.length() : 0) + " chars.");
            return result;
        } catch (Exception e) {
            log(scId, "[getRawManifest] ERROR generating manifest: " + e.getMessage());
            e.printStackTrace();
            return "<!-- Error generating manifest: " + e.getMessage() + " -->";
        }
    }

    /**
     * Extracts specific sections from the generated AndroidManifest.xml.
     * @param section "permissions", "components", "application_attributes", "activities", or "full"
     */
    public static String getManifestSection(Context context, String scId, String section) {
        log(scId, "[getManifestSection] Requested Section: '" + section + "' for scId: " + scId);
        String fullManifest = getRawManifest(context, scId);
        if (fullManifest.startsWith("<!-- Error")) {
            log(scId, "[getManifestSection] Aborted due to raw manifest error.");
            return fullManifest;
        }

        String sec = section != null ? section.toLowerCase().trim() : "full";
        log(scId, "[getManifestSection] Normalized Section: " + sec);

        switch (sec) {
            case "permissions": {
                StringBuilder sb = new StringBuilder("<!-- PERMISSIONS -->\n");
                Matcher m = Pattern.compile("<uses-permission[^>]*/>", Pattern.CASE_INSENSITIVE).matcher(fullManifest);
                int count = 0;
                while (m.find()) {
                    sb.append(m.group()).append("\n");
                    count++;
                }
                log(scId, "[getManifestSection -> permissions] Found " + count + " permissions.");
                return sb.toString().trim();
            }
            case "components":
            case "activities": {
                StringBuilder sb = new StringBuilder("<!-- COMPONENTS & ACTIVITIES -->\n");
                Pattern p = Pattern.compile("<(activity|service|receiver|provider)[\\s\\S]*?</\\1>|<(activity|service|receiver|provider)[^>]*/>", Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(fullManifest);
                int count = 0;
                while (m.find()) {
                    sb.append(m.group()).append("\n\n");
                    count++;
                }
                log(scId, "[getManifestSection -> components/activities] Found " + count + " components/activities.");
                return sb.toString().trim();
            }
            case "application_attributes": {
                Matcher m = Pattern.compile("<application[^>]*>", Pattern.CASE_INSENSITIVE).matcher(fullManifest);
                if (m.find()) {
                    String attr = m.group();
                    log(scId, "[getManifestSection -> application_attributes] Found application attributes tag.");
                    return "<!-- APPLICATION ATTRIBUTES -->\n" + attr;
                }
                log(scId, "[getManifestSection -> application_attributes] Application tag not found.");
                return "<!-- Application tag not found -->";
            }
            default:
                log(scId, "[getManifestSection -> full] Returning full manifest.");
                return fullManifest;
        }
    }

    /**
     * Searches for entries in the AndroidManifest.xml matching a query.
     */
    public static String searchManifest(Context context, String scId, String query) {
        log(scId, "[searchManifest] Input Query: '" + query + "' for scId: " + scId);
        String fullManifest = getRawManifest(context, scId);
        if (fullManifest.startsWith("<!-- Error")) {
            log(scId, "[searchManifest] Aborted due to raw manifest error.");
            return fullManifest;
        }

        if (query == null || query.trim().isEmpty()) {
            log(scId, "[searchManifest] Query is empty, returning full manifest.");
            return fullManifest;
        }

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

        log(scId, "[searchManifest] Search completed for '" + query + "' -> Match count: " + count);

        if (count == 0) {
            return "No entries matching '" + query + "' found in AndroidManifest.xml.";
        }
        return sb.toString().trim();
    }
}
