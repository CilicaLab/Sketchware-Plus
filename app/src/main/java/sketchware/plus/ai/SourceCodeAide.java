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
import mod.hey.studios.util.Helper;
import sketchware.plus.utility.FileUtil;
import sketchware.plus.utility.GsonUtils;
import mod.hilal.saif.blocks.BlocksHandler;

/**
 * A specialized utility class to assist the AI in analyzing source code
 * and calculating parameters for Sketchware Command Blocks.
 */
public class SourceCodeAide {

    /**
     * Adds a Java command to the global Java Command Manager file.
     */
    public static JSONObject addJavaCommandToManager(Context context, String scId, String javaName,
                                                     String reference, int distance, int front, int back,
                                                     String command, String inputCode) {
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
     * Extracts a specific range of lines.
     */
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
}
