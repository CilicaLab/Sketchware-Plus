package sketchware.plus.ai;

import android.content.Context;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.Ix;
import a.a.a.eC;
import a.a.a.hC;
import a.a.a.jC;
import a.a.a.yq;
import sketchware.plus.util.library.BuiltInLibraryManager;

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
}
