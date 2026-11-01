package sketchware.plus.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.content.Context;
import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.EventBean;
import a.a.a.eC;
import a.a.a.jC;
import a.a.a.yq;
import a.a.a.wq;
import a.a.a.hC;
import a.a.a.Ix;
import sketchware.plus.util.library.BuiltInLibraryManager;
import mod.hey.studios.util.Helper;
import sketchware.plus.utility.FileUtil;
import sketchware.plus.utility.GsonUtils;
import mod.hilal.saif.blocks.BlocksHandler;

/**
 * A specialized utility class to assist the AI in analyzing source code
 * and calculating parameters for Sketchware Command Blocks.
 */
public class SourceCodeAide {

    public static void setLogPath(String path) {
    }

    public static void setScId(String scId) {
    }

    public static void log(String scId, String message) {
        android.util.Log.d("SourceCodeAide", message);
    }

    public static void log(String message) {
        log(null, message);
    }

    /**
     * Adds a Java command to the global Java Command Manager file.
     */
    public static JSONObject addJavaCommandToManager(Context context, String scId, String javaName,
                                                     String reference, int distance, int front, int back,
                                                     String command, String inputCode) {
        if (javaName != null && (javaName.toLowerCase().endsWith(".xml") || javaName.equalsIgnoreCase("AndroidManifest.xml"))) {
            return addXmlCommandToManager(context, scId, javaName, reference, distance, front, back, command, inputCode);
        }

        JSONObject result = new JSONObject();
        try {
            String commandPath = wq.b(scId) + "/java_command";
            ArrayList<HashMap<String, Object>> commands = new ArrayList<>();
            if (FileUtil.isExistFile(commandPath)) {
                String content = FileUtil.readFile(commandPath);
                if (!content.isEmpty() && !content.equals("[]")) {
                    commands = GsonUtils.getGson().fromJson(content, Helper.TYPE_MAP_LIST);
                }
            }

            HashMap<String, Object> map = new HashMap<>();
            map.put("reference", reference);
            map.put("distance", distance);
            map.put("after", front);
            map.put("before", back);
            map.put("command", command);
            map.put("input", ">" + javaName + "\n" + inputCode);

            commands.add(map);
            FileUtil.writeFile(commandPath, GsonUtils.getGson().toJson(commands));

            result.put("status", "success");
        } catch (Exception e) {
            try {
                result.put("status", "error");
                result.put("message", e.getMessage());
            } catch (Exception ignored) {}
        }
        return result;
    }

    /**
     * Adds an XML command to the global XML Command Manager file (for AndroidManifest.xml, layout XMLs, resources).
     */
    public static JSONObject addXmlCommandToManager(Context context, String scId, String xmlName,
                                                    String reference, int distance, int front, int back,
                                                    String command, String inputCode) {
        JSONObject result = new JSONObject();
        try {
            String commandPath = wq.b(scId) + "/command";
            ArrayList<HashMap<String, Object>> commands = new ArrayList<>();
            if (FileUtil.isExistFile(commandPath)) {
                String content = FileUtil.readFile(commandPath);
                if (!content.isEmpty() && !content.equals("[]")) {
                    commands = GsonUtils.getGson().fromJson(content, Helper.TYPE_MAP_LIST);
                }
            }

            HashMap<String, Object> map = new HashMap<>();
            map.put("reference", reference);
            map.put("distance", distance);
            map.put("after", front);
            map.put("before", back);
            map.put("command", command);
            map.put("input", ">" + xmlName + "\n" + inputCode);

            commands.add(map);
            FileUtil.writeFile(commandPath, GsonUtils.getGson().toJson(commands));

            result.put("status", "success");
        } catch (Exception e) {
            try {
                result.put("status", "error");
                result.put("message", e.getMessage());
            } catch (Exception ignored) {}
        }
        return result;
    }

    /**
     * Searches for a method by name and returns its full content and line range.
     * @param source The full source code content.
     * @param methodName The name of the method to find.
     * @return A JSONObject containing "startLine", "endLine", and "content".
     */
    public static JSONObject findMethod(String source, String methodName) {
        JSONObject result = new JSONObject();
        String[] lines = source.split("\n");
        
        // Matches: visibility? static? type methodName ( args ) {
        String regex = "(public|protected|private|static|\\s) +[\\w<>\\[\\]]+\\s+" + methodName + "\\s*\\(.*\\)\\s*\\{";
        Pattern pattern = Pattern.compile(regex);
        
        int start = -1;
        int end = -1;
        int braceCount = 0;
        boolean foundStart = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!foundStart) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    start = i;
                    foundStart = true;
                    braceCount = countChar(line, '{') - countChar(line, '}');
                    if (braceCount == 0 && line.contains("}")) { // Single line method
                        end = i;
                        break;
                    }
                }
            } else {
                braceCount += countChar(line, '{') - countChar(line, '}');
                if (braceCount <= 0) {
                    end = i;
                    break;
                }
            }
        }

        try {
            if (start != -1 && end != -1) {
                result.put("status", "success");
                result.put("startLine", start);
                result.put("endLine", end);
                result.put("methodName", methodName);
                
                StringBuilder methodContent = new StringBuilder();
                for (int i = start; i <= end; i++) {
                    methodContent.append(lines[i]).append("\n");
                }
                result.put("content", methodContent.toString());
            } else {
                result.put("status", "not_found");
            }
        } catch (Exception ignored) {}

        return result;
    }

    /**
     * Lists all method names declared in the source code.
     */
    public static JSONObject listMethods(String source) {
        JSONObject result = new JSONObject();
        JSONArray methods = new JSONArray();
        String regex = "(public|protected|private|static|\\s) +[\\w<>\\[\\]]+\\s+([\\w$]+)\\s*\\(";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(source);
        
        while (matcher.find()) {
            methods.put(matcher.group(2));
        }

        try {
            result.put("status", "success");
            result.put("methods", methods);
            result.put("count", methods.length());
        } catch (Exception ignored) {}
        return result;
    }

    /**
     * Generates a lightweight class outline (class declaration, fields, method signatures with line numbers)
     * to prevent context window bloat when analyzing large source files.
     */
    public static JSONObject getClassOutline(String source) {
        JSONObject result = new JSONObject();
        if (source == null) return result;
        String[] lines = source.split("\n");
        JSONArray fieldsArray = new JSONArray();
        JSONArray methodsArray = new JSONArray();
        
        String pkg = "";
        String classDecl = "";
        int importCount = 0;

        Pattern pkgPattern = Pattern.compile("^\\s*package\\s+([\\w.]+);");
        Pattern importPattern = Pattern.compile("^\\s*import\\s+");
        Pattern classPattern = Pattern.compile("^\\s*(public|protected|private)?\\s*(abstract|final)?\\s*class\\s+([\\w$]+)");
        Pattern methodPattern = Pattern.compile("^\\s*(public|protected|private|static|final|synchronized|\\s)*\\s+([\\w<>\\[\\]]+)\\s+([\\w$]+)\\s*\\(([^)]*)\\)\\s*(throws\\s+[\\w.,\\s]+)?\\s*\\{?");
        Pattern fieldPattern = Pattern.compile("^\\s*(public|protected|private|static|final|transient|volatile|\\s)*\\s+([\\w<>\\[\\]]+)\\s+([\\w$]+)\\s*(=|;)");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            if (pkg.isEmpty()) {
                Matcher m = pkgPattern.matcher(line);
                if (m.find()) {
                    pkg = m.group(1);
                    continue;
                }
            }
            if (importPattern.matcher(line).find()) {
                importCount++;
                continue;
            }
            if (classDecl.isEmpty()) {
                Matcher m = classPattern.matcher(line);
                if (m.find()) {
                    classDecl = line.trim();
                    continue;
                }
            }
            
            Matcher mm = methodPattern.matcher(line);
            if (mm.find()) {
                String methodName = mm.group(3);
                String returnType = mm.group(2);
                if (!"if".equals(methodName) && !"for".equals(methodName) && !"while".equals(methodName) && !"switch".equals(methodName) && !"catch".equals(methodName)) {
                    try {
                        JSONObject mObj = new JSONObject();
                        mObj.put("name", methodName);
                        mObj.put("returnType", returnType);
                        mObj.put("line", i);
                        mObj.put("signature", line.trim());
                        methodsArray.put(mObj);
                    } catch (Exception ignored) {}
                    continue;
                }
            }

            Matcher fm = fieldPattern.matcher(line);
            if (fm.find()) {
                String fieldName = fm.group(3);
                if (!"return".equals(fieldName) && !"new".equals(fieldName)) {
                    try {
                        JSONObject fObj = new JSONObject();
                        fObj.put("name", fieldName);
                        fObj.put("line", i);
                        fObj.put("declaration", line.trim());
                        fieldsArray.put(fObj);
                    } catch (Exception ignored) {}
                }
            }
        }

        try {
            result.put("status", "success");
            result.put("package", pkg);
            result.put("classDeclaration", classDecl);
            result.put("importCount", importCount);
            result.put("totalLines", lines.length);
            result.put("fields", fieldsArray);
            result.put("methods", methodsArray);
        } catch (Exception ignored) {}

        return result;
    }
    public static JSONObject getLineRange(String source, int startLine, int endLine) {
        JSONObject result = new JSONObject();
        String[] lines = source.split("\n");
        
        int s = Math.max(0, startLine);
        int e = Math.min(lines.length - 1, endLine);

        StringBuilder sb = new StringBuilder();
        for (int i = s; i <= e; i++) {
            sb.append(lines[i]).append("\n");
        }

        try {
            result.put("status", "success");
            result.put("start", s);
            result.put("end", e);
            result.put("content", sb.toString());
            result.put("totalLines", lines.length);
        } catch (Exception ignored) {}

        return result;
    }

    /**
     * Searches for a snippet containing a specific keyword.
     */
    public static JSONObject findSnippet(String source, String keyword, int contextLines) {
        JSONObject result = new JSONObject();
        String[] lines = source.split("\n");
        JSONArray snippets = new JSONArray();

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(keyword)) {
                try {
                    int start = Math.max(0, i - contextLines);
                    int end = Math.min(lines.length - 1, i + contextLines);
                    
                    JSONObject snip = new JSONObject();
                    snip.put("line", i);
                    snip.put("content", getSnippetRange(lines, start, end));
                    snippets.put(snip);
                } catch (Exception ignored) {}
            }
        }

        try {
            result.put("count", snippets.length());
            result.put("snippets", snippets);
        } catch (Exception ignored) {}
        
        return result;
    }

    /**
     * Finds where a field (variable) is declared.
     */
    public static JSONObject findField(String source, String fieldName) {
        JSONObject result = new JSONObject();
        String[] lines = source.split("\n");
        
        // Matches: visibility? static? type fieldName [= ...];
        String regex = "(public|protected|private|static|\\s) +[\\w<>\\[\\]]+\\s+" + fieldName + "\\s*[;=]";
        Pattern pattern = Pattern.compile(regex);

        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = pattern.matcher(lines[i]);
            if (matcher.find()) {
                try {
                    result.put("status", "success");
                    result.put("line", i);
                    result.put("content", lines[i].trim());
                    return result;
                } catch (Exception ignored) {}
            }
        }

        try { result.put("status", "not_found"); } catch (Exception ignored) {}
        return result;
    }

    /**
     * Helps calculate Command Block parameters for a specific line.
     */
    public static JSONObject getCommandBlockMeta(String source, int targetLine) {
        JSONObject result = new JSONObject();
        String[] lines = source.split("\n");
        
        if (targetLine < 0 || targetLine >= lines.length) {
            return result;
        }

        String line = lines[targetLine].trim();
        try {
            result.put("status", "success");
            result.put("lineIndex", targetLine);
            result.put("reference", line);
            result.put("distance", 0);
            result.put("backendContext", getSnippetRange(lines, targetLine - 3, targetLine - 1));
            result.put("frontendContext", getSnippetRange(lines, targetLine + 1, targetLine + 3));
        } catch (Exception ignored) {}

        return result;
    }

    private static int countChar(String s, char c) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) count++;
        }
        return count;
    }

    private static String getSnippetRange(String[] lines, int start, int end) {
        int s = Math.max(0, start);
        int e = Math.min(lines.length - 1, end);
        if (s > e) return "";

        StringBuilder sb = new StringBuilder();
        for (int i = s; i <= e; i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString();
    }

    /**
     * Generates the live AndroidManifest.xml string for a project using Ix.
     */
    public static String getGeneratedManifest(Context context, String scId) {
        if (context == null || scId == null || scId.isEmpty()) return null;
        try {
            eC dataManager = jC.a(scId);
            hC fileManager = jC.b(scId);
            yq workspace = new yq(context, scId);
            BuiltInLibraryManager builtInLibManager = new BuiltInLibraryManager(scId);
            Ix manifestGenerator = new Ix(dataManager.l, fileManager.b(), builtInLibManager);
            manifestGenerator.setYq(workspace);
            return manifestGenerator.a();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Searches through AndroidManifest.xml by query keyword, XML tag name, or permission string.
     * @param manifestXml The full AndroidManifest.xml source code.
     * @param query Search query term (e.g. "permission", "activity", "INTERNET", "service", "all").
     * @return JSONObject containing status, match count, and list of matching XML snippets.
     */
    public static JSONObject searchManifest(String manifestXml, String query) {
        JSONObject result = new JSONObject();
        if (manifestXml == null || manifestXml.trim().isEmpty()) {
            try {
                result.put("status", "error");
                result.put("message", "Manifest XML content is empty or null.");
            } catch (Exception ignored) {}
            return result;
        }

        String q = (query != null ? query.trim() : "all").toLowerCase();
        String[] lines = manifestXml.split("\n");
        JSONArray matches = new JSONArray();

        try {
            if (q.isEmpty() || q.equals("all") || q.equals("summary") || q.equals("outline")) {
                int permissionsCount = 0;
                int activitiesCount = 0;
                int servicesCount = 0;
                int receiversCount = 0;
                int providersCount = 0;

                JSONArray permissionsList = new JSONArray();
                JSONArray activitiesList = new JSONArray();

                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    String lineLower = line.toLowerCase();
                    if (lineLower.contains("<uses-permission")) {
                        permissionsCount++;
                        permissionsList.put("Line " + (i + 1) + ": " + line);
                    } else if (lineLower.contains("<activity")) {
                        activitiesCount++;
                        activitiesList.put("Line " + (i + 1) + ": " + line);
                    } else if (lineLower.contains("<service")) {
                        servicesCount++;
                    } else if (lineLower.contains("<receiver")) {
                        receiversCount++;
                    } else if (lineLower.contains("<provider")) {
                        providersCount++;
                    }
                }

                result.put("status", "success");
                result.put("query", q);
                result.put("totalLines", lines.length);
                result.put("permissionsCount", permissionsCount);
                result.put("activitiesCount", activitiesCount);
                result.put("servicesCount", servicesCount);
                result.put("receiversCount", receiversCount);
                result.put("providersCount", providersCount);
                result.put("permissions", permissionsList);
                result.put("activities", activitiesList);
                return result;
            }

            // Keyword / Tag search
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (line.toLowerCase().contains(q)) {
                    int start = Math.max(0, i - 2);
                    int end = Math.min(lines.length - 1, i + 2);

                    JSONObject match = new JSONObject();
                    match.put("line", i + 1);
                    match.put("matchLine", line.trim());
                    match.put("snippet", getSnippetRange(lines, start, end));
                    matches.put(match);
                }
            }

            result.put("status", "success");
            result.put("query", query);
            result.put("count", matches.length());
            result.put("matches", matches);

            if (matches.length() == 0) {
                result.put("status", "not_found");
                result.put("message", "No elements or lines matching '" + query + "' found in AndroidManifest.xml.");
            }
        } catch (Exception e) {
            try {
                result.put("status", "error");
                result.put("message", e.getMessage());
            } catch (Exception ignored) {}
        }

        return result;
    }
}
