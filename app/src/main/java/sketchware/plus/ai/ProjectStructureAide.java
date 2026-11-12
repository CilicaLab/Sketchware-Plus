package sketchware.plus.ai;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.util.Pair;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.LogicEditorActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import a.a.a.eC;
import a.a.a.hC;
import a.a.a.jC;
import a.a.a.uq;
import mod.hey.studios.moreblock.ReturnMoreblockManager;
import mod.hilal.saif.blocks.BlocksHandler;

/**
 * Static helper utilities for SK Assistant project structure operations.
 * Handles canonical javaName resolution, structure reporting, and safe eC/hC mutations.
 */
public class ProjectStructureAide {

    private static final String TAG = "ProjectStructureAide";

    private static final Set<String> JAVA_KEYWORDS = new HashSet<>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super",
            "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while"
    ));

    private static final Set<String> RESERVED_LITERALS = new HashSet<>(Arrays.asList(
            "true", "false", "null"
    ));

    private static final Set<String> GENERATED_METHOD_NAMES = new HashSet<>(Arrays.asList(
            "oncreate", "onstart", "onresume", "onpause", "onstop", "ondestroy", "onbackpressed",
            "initialize", "initializelogic", "onactivityresult"
    ));

    /**
     * Returns the default activity javaName for the project (first activity or MainActivity.java).
     */
    public static String getDefaultJavaName(String scId) {
        if (scId != null && !scId.trim().isEmpty()) {
            hC fileManager = jC.b(scId);
            if (fileManager != null && fileManager.c != null) {
                for (ProjectFileBean pfb : fileManager.c) {
                    if (pfb != null && pfb.getJavaName() != null) return pfb.getJavaName();
                }
            }
        }
        return "MainActivity.java";
    }

    /**
     * Resolves various alias forms ("main", "main.xml", "MainActivity", "MainActivity.java")
     * into the canonical Java file key used by eC maps (e.g. "MainActivity.java").
     * Checks against the actual activity list in the project.
     * Returns defaultJavaName if input is empty, or null if input does not match any existing activity.
     */
    public static String findActivityJavaName(String scId, String input, String defaultJavaName) {
        if (scId != null && !scId.trim().isEmpty()) {
            hC fileManager = jC.b(scId);
            if (fileManager != null && fileManager.c != null && !fileManager.c.isEmpty()) {
                if (input == null || input.trim().isEmpty()) {
                    if (defaultJavaName != null && !defaultJavaName.trim().isEmpty()) {
                        for (ProjectFileBean pfb : fileManager.c) {
                            if (pfb != null && defaultJavaName.equalsIgnoreCase(pfb.getJavaName())) {
                                return pfb.getJavaName();
                            }
                        }
                    }
                    return fileManager.c.get(0).getJavaName();
                }

                String trimmed = input.trim();
                for (ProjectFileBean pfb : fileManager.c) {
                    if (pfb == null) continue;
                    if (trimmed.equalsIgnoreCase(pfb.fileName)
                            || trimmed.equalsIgnoreCase(pfb.getJavaName())
                            || trimmed.equalsIgnoreCase(pfb.getXmlName())
                            || trimmed.equalsIgnoreCase(pfb.getActivityName())) {
                        return pfb.getJavaName();
                    }
                }
                return null;
            }
        }

        if (input == null || input.trim().isEmpty()) {
            return defaultJavaName != null ? defaultJavaName : "MainActivity.java";
        }
        return null;
    }

    /**
     * Legacy resolution fallback for callers that do not pass a default javaName.
     */
    public static String resolveJavaName(String scId, String input) {
        String res = findActivityJavaName(scId, input, null);
        if (res != null) return res;
        if (input == null || input.trim().isEmpty()) return getDefaultJavaName(scId);
        String trimmed = input.trim();
        if (trimmed.endsWith(".java")) return trimmed;
        if (trimmed.endsWith(".xml")) return ProjectFileBean.getJavaName(trimmed.substring(0, trimmed.length() - 4));
        return ProjectFileBean.getJavaName(trimmed);
    }

    /**
     * Returns a comma-separated list of all activity names available in the project.
     */
    public static String getAvailableActivitiesList(String scId) {
        if (scId != null && !scId.trim().isEmpty()) {
            hC fileManager = jC.b(scId);
            if (fileManager != null && fileManager.c != null && !fileManager.c.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (ProjectFileBean pfb : fileManager.c) {
                    if (pfb != null) names.add(pfb.getJavaName());
                }
                return String.join(", ", names);
            }
        }
        return "MainActivity.java";
    }

    /**
     * Returns the XML layout file name (e.g. "main.xml") corresponding to the given activity javaName or alias.
     */
    public static String getXmlNameForActivity(String scId, String rawJavaName) {
        if (rawJavaName == null || rawJavaName.trim().isEmpty()) {
            return "main.xml";
        }
        String trimmed = rawJavaName.trim();
        if (trimmed.endsWith(".xml")) {
            return trimmed;
        }
        String canonJava = resolveJavaName(scId, trimmed);
        if (scId != null && !scId.trim().isEmpty()) {
            hC fileManager = jC.b(scId);
            if (fileManager != null && fileManager.c != null) {
                for (ProjectFileBean pfb : fileManager.c) {
                    if (pfb != null && (canonJava.equalsIgnoreCase(pfb.getJavaName())
                            || trimmed.equalsIgnoreCase(pfb.fileName)
                            || trimmed.equalsIgnoreCase(pfb.getActivityName()))) {
                        return pfb.getXmlName();
                    }
                }
            }
        }
        String base = canonJava;
        if (base.endsWith(".java")) base = base.substring(0, base.length() - 5);
        if (base.endsWith("Activity")) base = base.substring(0, base.length() - 8);
        if (base.startsWith("Activity")) base = base.substring(8);
        String lowerBase = base.toLowerCase();
        if (lowerBase.isEmpty()) lowerBase = "main";
        return lowerBase + ".xml";
    }

    /**
     * Validates a function or identifier name against Java syntax rules, reserved keywords,
     * generated activity methods, and existing project identifiers.
     *
     * @return null if valid, or an error description string if invalid.
     */
    public static String validateName(Context context, String scId, String javaName, String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Name cannot be empty.";
        }
        String trimmed = name.trim();
        if (trimmed.length() > 100) {
            return "Name length must be between 1 and 100 characters.";
        }
        if (!Character.isLetter(trimmed.charAt(0))) {
            return "Name '" + trimmed + "' must start with a letter.";
        }
        if (!trimmed.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
            return "Name '" + trimmed + "' can only contain letters, numbers, and underscores.";
        }

        if (RESERVED_LITERALS.contains(trimmed)) {
            return "Name '" + trimmed + "' is a reserved literal value.";
        }

        if (JAVA_KEYWORDS.contains(trimmed)) {
            return "Name '" + trimmed + "' is a Java reserved keyword.";
        }

        String lower = trimmed.toLowerCase();

        if (GENERATED_METHOD_NAMES.contains(lower)) {
            return "Name '" + trimmed + "' collides with a generated activity method name.";
        }

        if (scId != null && !scId.trim().isEmpty()) {
            eC dataManager = jC.a(scId);
            hC fileManager = jC.b(scId);

            if (fileManager != null) {
                ProjectFileBean targetPfb = fileManager.a(javaName);
                if (targetPfb == null) {
                    return "Error: Unknown activity '" + javaName + "'. Available activities: " + getAvailableActivitiesList(scId);
                }
                if (dataManager != null) {
                    ArrayList<String> takenNames = dataManager.a(targetPfb);
                    if (takenNames != null) {
                        for (String taken : takenNames) {
                            if (lower.equals(taken.toLowerCase())) {
                                return "Name '" + trimmed + "' conflicts with an existing variable, list, component, function, or view ID in " + javaName + ".";
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Auto-generates a unique valid name (for variables, lists, functions, or activities)
     * and checks against existing identifiers or activities to guarantee no collisions.
     */
    public static String generateUniqueName(String scId, String javaName, String basePrefix, boolean isActivity) {
        if (basePrefix == null || basePrefix.trim().isEmpty()) {
            basePrefix = "item";
        }
        basePrefix = basePrefix.trim();

        if (isActivity) {
            hC fileManager = jC.b(scId);
            if (fileManager == null) return basePrefix;

            Set<String> taken = new HashSet<>();
            if (fileManager.c != null) {
                for (ProjectFileBean pfb : fileManager.c) {
                    if (pfb != null) {
                        if (pfb.fileName != null) taken.add(pfb.fileName.toLowerCase());
                        if (pfb.getActivityName() != null) taken.add(pfb.getActivityName().toLowerCase());
                    }
                }
            }
            if (fileManager.a != null) {
                for (String fname : fileManager.a) {
                    if (fname != null) {
                        String clean = fname.endsWith(".java") ? fname.substring(0, fname.length() - 5) : fname;
                        if (clean.endsWith(".xml")) clean = clean.substring(0, clean.length() - 4);
                        taken.add(clean.toLowerCase());
                    }
                }
            }

            String candidate = basePrefix;
            if (!taken.contains(candidate.toLowerCase())) {
                return candidate;
            }

            int counter = 2;
            while (true) {
                candidate = basePrefix + counter;
                if (!taken.contains(candidate.toLowerCase())) {
                    return candidate;
                }
                counter++;
            }
        } else {
            eC dataManager = jC.a(scId);
            if (dataManager == null) return basePrefix;

            hC fileManager = jC.b(scId);
            ProjectFileBean targetPfb = fileManager != null ? fileManager.a(javaName) : null;
            ArrayList<String> takenNames = dataManager.a(targetPfb);
            Set<String> taken = new HashSet<>();
            if (takenNames != null) {
                for (String t : takenNames) {
                    if (t != null) taken.add(t.toLowerCase());
                }
            }

            String candidate = basePrefix;
            if (!taken.contains(candidate.toLowerCase())) {
                return candidate;
            }

            int counter = 2;
            while (true) {
                candidate = basePrefix + counter;
                if (!taken.contains(candidate.toLowerCase())) {
                    return candidate;
                }
                counter++;
            }
        }
    }

    /**
     * Reads and returns project structure details as a formatted string.
     */
    public static String getProjectStructureSummary(String scId, String rawJavaName) {
        String defaultJava = getDefaultJavaName(scId);
        String javaName = findActivityJavaName(scId, rawJavaName, defaultJava);
        if (javaName == null) {
            return "Error: Unknown activity '" + rawJavaName + "'. Available activities: " + getAvailableActivitiesList(scId);
        }

        eC dataManager = jC.a(scId);
        hC fileManager = jC.b(scId);

        StringBuilder sb = new StringBuilder();
        sb.append("=== Project Structure Summary ===\n");

        if (fileManager != null) {
            sb.append("Activities: ");
            if (fileManager.c != null && !fileManager.c.isEmpty()) {
                List<String> actList = new ArrayList<>();
                for (ProjectFileBean pfb : fileManager.c) {
                    if (pfb != null) actList.add(pfb.getJavaName() + " (" + pfb.fileName + ")");
                }
                sb.append(String.join(", ", actList));
            } else {
                sb.append("none");
            }
            sb.append("\nCustom Views: ");
            if (fileManager.d != null && !fileManager.d.isEmpty()) {
                List<String> cvList = new ArrayList<>();
                for (ProjectFileBean pfb : fileManager.d) {
                    if (pfb != null) cvList.add(pfb.getJavaName() + " (" + pfb.fileName + ")");
                }
                sb.append(String.join(", ", cvList));
            } else {
                sb.append("none");
            }
            sb.append("\n");
        }

        if (dataManager != null) {
            sb.append("\nTarget Activity: ").append(javaName).append("\n");
            String xmlName = getXmlNameForActivity(scId, javaName);

            ArrayList<Pair<Integer, String>> vars = dataManager.k(javaName);
            sb.append("Variables: ");
            if (vars != null && !vars.isEmpty()) {
                List<String> varStrings = new ArrayList<>();
                for (Pair<Integer, String> p : vars) {
                    String typeName = switch (p.first) {
                        case 0 -> "boolean";
                        case 1 -> "number";
                        case 2 -> "String";
                        case 3 -> "Map";
                        default -> "unknown";
                    };
                    varStrings.add(p.second + " (" + typeName + ")");
                }
                sb.append(String.join(", ", varStrings));
            } else {
                sb.append("none");
            }

            ArrayList<Pair<Integer, String>> lists = dataManager.j(javaName);
            sb.append("\nLists: ");
            if (lists != null && !lists.isEmpty()) {
                List<String> listStrings = new ArrayList<>();
                for (Pair<Integer, String> p : lists) {
                    String typeName = switch (p.first) {
                        case 1 -> "ListInt";
                        case 2 -> "ListString";
                        case 3 -> "ListMap";
                        default -> "unknown";
                    };
                    listStrings.add(p.second + " (" + typeName + ")");
                }
                sb.append(String.join(", ", listStrings));
            } else {
                sb.append("none");
            }

            ArrayList<Pair<String, String>> mbs = dataManager.i(javaName);
            sb.append("\nMore Blocks: ");
            if (mbs != null && !mbs.isEmpty()) {
                List<String> mbStrings = new ArrayList<>();
                for (Pair<String, String> p : mbs) {
                    mbStrings.add(p.first + " [spec: " + p.second + "]");
                }
                sb.append(String.join("; ", mbStrings));
            } else {
                sb.append("none");
            }

            ArrayList<EventBean> events = dataManager.g(javaName);
            sb.append("\nRegistered Events: ");
            if (events != null && !events.isEmpty()) {
                List<String> evStrings = new ArrayList<>();
                for (EventBean eb : events) {
                    if (eb != null) {
                        evStrings.add(eb.getEventKey() + " (" + EventBean.getEventTypeName(eb.eventType) + ")");
                    }
                }
                sb.append(String.join(", ", evStrings));
            } else {
                sb.append("none");
            }

            ArrayList<ComponentBean> components = dataManager.e(javaName);
            sb.append("\nComponents: ");
            if (components != null && !components.isEmpty()) {
                List<String> compStrings = new ArrayList<>();
                for (ComponentBean cb : components) {
                    if (cb != null) {
                        compStrings.add(cb.componentId + " (" + ComponentBean.getComponentTypeName(cb.type) + ")");
                    }
                }
                sb.append(String.join(", ", compStrings));
            } else {
                sb.append("none");
            }

            ArrayList<ViewBean> views = dataManager.d(xmlName);
            sb.append("\nViews in Layout (").append(xmlName).append("): ");
            if (views != null && !views.isEmpty()) {
                List<String> viewStrings = new ArrayList<>();
                for (ViewBean vb : views) {
                    if (vb != null) {
                        viewStrings.add(vb.id + " (" + ViewBean.getViewTypeName(vb.type) + ")");
                    }
                }
                sb.append(String.join(", ", viewStrings));
            } else {
                sb.append("none");
            }
        }

        return sb.toString();
    }

    /**
     * Adds variables to the specified activity. Idempotent.
     */
    public static String addVariables(Context context, String scId, String rawJavaName, JSONArray items) {
        String defaultJava = getDefaultJavaName(scId);
        String javaName = findActivityJavaName(scId, rawJavaName, defaultJava);
        if (javaName == null) {
            return "Error: Unknown activity '" + rawJavaName + "'. Available activities: " + getAvailableActivitiesList(scId);
        }

        eC dataManager = jC.a(scId);
        if (dataManager == null) {
            return "Error: Project data manager not found for scId: " + scId;
        }

        if (items == null || items.length() == 0) {
            return "Error: No variable items provided.";
        }

        List<String> added = new ArrayList<>();
        List<String> existing = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        synchronized (dataManager) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;

                String varName = item.optString("name", "").trim();
                if (varName.isEmpty()) {
                    varName = generateUniqueName(scId, javaName, "var", false);
                }
                String typeStr = item.optString("type", "").trim().toLowerCase();

                int typeInt;
                switch (typeStr) {
                    case "boolean", "bool" -> typeInt = 0;
                    case "number", "double", "int", "integer" -> typeInt = 1;
                    case "string", "str" -> typeInt = 2;
                    case "map" -> typeInt = 3;
                    default -> {
                        errors.add(varName + ": Unknown variable type '" + typeStr + "'. Valid types: boolean, number, string, map.");
                        continue;
                    }
                }

                if (dataManager.f(javaName, typeInt, varName)) {
                    existing.add(varName);
                    continue;
                }

                String err = validateName(context, scId, javaName, varName);
                if (err != null) {
                    errors.add(varName + ": " + err);
                    continue;
                }

                dataManager.c(javaName, typeInt, varName);
                added.add(varName + " (" + typeStr + ")");
            }

            dataManager.k();
        }

        StringBuilder res = new StringBuilder();
        if (!added.isEmpty()) {
            res.append("Successfully added variables: ").append(String.join(", ", added)).append(". ");
        }
        if (!existing.isEmpty()) {
            res.append("Variables already exist (skipped): ").append(String.join(", ", existing)).append(". ");
        }
        if (!errors.isEmpty()) {
            res.append("Failed items: ").append(String.join("; ", errors)).append(".");
        }
        return res.toString();
    }

    /**
     * Adds lists to the specified activity. Idempotent.
     */
    public static String addLists(Context context, String scId, String rawJavaName, JSONArray items) {
        String defaultJava = getDefaultJavaName(scId);
        String javaName = findActivityJavaName(scId, rawJavaName, defaultJava);
        if (javaName == null) {
            return "Error: Unknown activity '" + rawJavaName + "'. Available activities: " + getAvailableActivitiesList(scId);
        }

        eC dataManager = jC.a(scId);
        if (dataManager == null) {
            return "Error: Project data manager not found for scId: " + scId;
        }

        if (items == null || items.length() == 0) {
            return "Error: No list items provided.";
        }

        List<String> added = new ArrayList<>();
        List<String> existing = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        synchronized (dataManager) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;

                String listName = item.optString("name", "").trim();
                if (listName.isEmpty()) {
                    listName = generateUniqueName(scId, javaName, "list", false);
                }
                String typeStr = item.optString("type", "").trim().toLowerCase();

                int typeInt;
                switch (typeStr) {
                    case "number", "double", "int", "integer" -> typeInt = 1;
                    case "string", "str" -> typeInt = 2;
                    case "map" -> typeInt = 3;
                    default -> {
                        errors.add(listName + ": Unknown list type '" + typeStr + "'. Valid types: number, string, map.");
                        continue;
                    }
                }

                if (dataManager.e(javaName, typeInt, listName)) {
                    existing.add(listName);
                    continue;
                }

                String err = validateName(context, scId, javaName, listName);
                if (err != null) {
                    errors.add(listName + ": " + err);
                    continue;
                }

                dataManager.b(javaName, typeInt, listName);
                added.add(listName + " (" + typeStr + ")");
            }

            dataManager.k();
        }

        StringBuilder res = new StringBuilder();
        if (!added.isEmpty()) {
            res.append("Successfully added lists: ").append(String.join(", ", added)).append(". ");
        }
        if (!existing.isEmpty()) {
            res.append("Lists already exist (skipped): ").append(String.join(", ", existing)).append(". ");
        }
        if (!errors.isEmpty()) {
            res.append("Failed items: ").append(String.join("; ", errors)).append(".");
        }
        return res.toString();
    }

    public static String addMoreBlock(Context context, String scId, String rawJavaName, String name, JSONArray params, String returnType, String bodyJava) {
        return addMoreBlock(context, scId, rawJavaName, name, params, returnType, bodyJava, false, null);
    }

    /**
     * Adds a custom function (More Block) to the activity.
     */
    public static String addMoreBlock(Context context, String scId, String rawJavaName, String name, JSONArray params, String returnType, String bodyJava, boolean allowEmptyOnBodyError, SkAssistantFragment fragment) {
        JSONObject resJson = new JSONObject();
        JSONArray warnings = new JSONArray();

        String defaultJava = (fragment != null && fragment.projectFile != null) ? fragment.projectFile.getJavaName() : getDefaultJavaName(scId);
        String javaName = findActivityJavaName(scId, rawJavaName, defaultJava);
        if (javaName == null) {
            try {
                resJson.put("ok", false);
                resJson.put("error", "Unknown activity '" + rawJavaName + "'. Available activities: " + getAvailableActivitiesList(scId));
            } catch (Exception ignored) {}
            return resJson.toString();
        }

        eC dataManager = jC.a(scId);
        if (dataManager == null) {
            try {
                resJson.put("ok", false);
                resJson.put("error", "Project data manager not found for scId: " + scId);
            } catch (Exception ignored) {}
            return resJson.toString();
        }

        String funcName = name != null ? name.trim() : "";
        if (funcName.isEmpty()) {
            funcName = generateUniqueName(scId, javaName, "myFunction", false);
        }
        String err = validateName(context, scId, javaName, funcName);
        if (err != null) {
            try {
                resJson.put("ok", false);
                resJson.put("error", "Error validating function name: " + err);
            } catch (Exception ignored) {}
            return resJson.toString();
        }

        String rTypeLower = returnType != null ? returnType.trim().toLowerCase() : "void";
        String returnTypeEncoded;

        if (rTypeLower.isEmpty() || "void".equals(rTypeLower) || " ".equals(rTypeLower)) {
            returnTypeEncoded = "";
        } else if ("string".equals(rTypeLower) || "str".equals(rTypeLower) || "s".equals(rTypeLower)) {
            returnTypeEncoded = "[String]";
        } else if ("number".equals(rTypeLower) || "double".equals(rTypeLower) || "int".equals(rTypeLower) || "integer".equals(rTypeLower) || "d".equals(rTypeLower)) {
            returnTypeEncoded = "[double]";
        } else if ("boolean".equals(rTypeLower) || "bool".equals(rTypeLower) || "b".equals(rTypeLower)) {
            returnTypeEncoded = "[boolean]";
        } else if ("map".equals(rTypeLower) || "a|map".equals(rTypeLower)) {
            returnTypeEncoded = "[a|Map]";
        } else if ("list string".equals(rTypeLower) || "liststring".equals(rTypeLower) || "l|list string".equals(rTypeLower)) {
            returnTypeEncoded = "[l|List String]";
        } else if ("list map".equals(rTypeLower) || "listmap".equals(rTypeLower) || "l|list map".equals(rTypeLower)) {
            returnTypeEncoded = "[l|List Map]";
        } else if ("view".equals(rTypeLower) || "v|view".equals(rTypeLower)) {
            returnTypeEncoded = "[v|View]";
        } else {
            try {
                resJson.put("ok", false);
                resJson.put("error", "Error: Unknown return type '" + returnType + "'. Valid types: void, string, double, boolean, map, list string, list map, view.");
            } catch (Exception ignored) {}
            return resJson.toString();
        }

        StringBuilder specBuilder = new StringBuilder(funcName).append(returnTypeEncoded);
        Set<String> seenParams = new HashSet<>();

        if (params != null && params.length() > 0) {
            for (int i = 0; i < params.length(); i++) {
                String pName;
                String pType;

                Object item = params.opt(i);
                if (item instanceof JSONObject pObj) {
                    pName = pObj.optString("name", "").trim();
                    pType = pObj.optString("type", "string").trim().toLowerCase();
                } else if (item instanceof String strItem) {
                    pName = strItem.trim();
                    pType = "string";
                } else {
                    try {
                        resJson.put("ok", false);
                        resJson.put("error", "Invalid parameter item at index " + i + ": expected parameter object or string name.");
                    } catch (Exception ignored) {}
                    return resJson.toString();
                }

                if (pName.isEmpty() || !pName.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
                    try {
                        resJson.put("ok", false);
                        resJson.put("error", "Error: Invalid parameter name '" + pName + "' at index " + i + ". Must start with a letter and contain only alphanumeric characters or underscores.");
                    } catch (Exception ignored) {}
                    return resJson.toString();
                }

                if (RESERVED_LITERALS.contains(pName)) {
                    try {
                        resJson.put("ok", false);
                        resJson.put("error", "Error: Parameter name '" + pName + "' is a reserved literal value.");
                    } catch (Exception ignored) {}
                    return resJson.toString();
                }

                if (JAVA_KEYWORDS.contains(pName)) {
                    try {
                        resJson.put("ok", false);
                        resJson.put("error", "Error: Parameter name '" + pName + "' is a Java reserved keyword.");
                    } catch (Exception ignored) {}
                    return resJson.toString();
                }

                if (pName.equalsIgnoreCase(funcName)) {
                    try {
                        resJson.put("ok", false);
                        resJson.put("error", "Error: Parameter name '" + pName + "' cannot be equal to the function name.");
                    } catch (Exception ignored) {}
                    return resJson.toString();
                }

                String pLower = pName.toLowerCase();
                if (seenParams.contains(pLower)) {
                    try {
                        resJson.put("ok", false);
                        resJson.put("error", "Error: Duplicate parameter name '" + pName + "' in function definition.");
                    } catch (Exception ignored) {}
                    return resJson.toString();
                }
                seenParams.add(pLower);

                String pToken = switch (pType) {
                    case "boolean", "bool" -> "%b." + pName;
                    case "number", "double", "int", "integer", "d" -> "%d." + pName;
                    case "string", "str", "s" -> "%s." + pName;
                    case "map" -> "%m.map." + pName;
                    case "list string", "liststring" -> "%m.listString." + pName;
                    case "list map", "listmap" -> "%m.listMap." + pName;
                    case "view" -> "%m.view." + pName;
                    default -> null;
                };

                if (pToken == null) {
                    try {
                        resJson.put("ok", false);
                        resJson.put("error", "Error: Unknown parameter type '" + pType + "' for parameter '" + pName + "'. Valid types: string, number, boolean, map, list string, list map, view.");
                    } catch (Exception ignored) {}
                    return resJson.toString();
                }

                specBuilder.append(" ").append(pToken);
            }
        }

        String spec = specBuilder.toString();
        String bodyKeyBase = funcName + returnTypeEncoded;

        synchronized (dataManager) {
            ArrayList<Pair<String, String>> existingMbs = dataManager.i(javaName);
            if (existingMbs != null) {
                for (Pair<String, String> mb : existingMbs) {
                    if (mb != null && mb.first != null) {
                        String existingName = ReturnMoreblockManager.getMbName(mb.first);
                        if (funcName.equalsIgnoreCase(existingName)) {
                            try {
                                resJson.put("ok", false);
                                resJson.put("error", "More block '" + funcName + "' already exists in " + javaName + ".");
                            } catch (Exception ignored) {}
                            return resJson.toString();
                        }
                    }
                }
            }

            String blockKey = bodyKeyBase + "_moreBlock";
            ArrayList<BlockBean> initialBlocks = new ArrayList<>();

            String bodyError = null;
            int convertedCount = 0;

            if (bodyJava != null && !bodyJava.trim().isEmpty()) {
                try {
                    // Manual code in More Block uses the 'Add source directly' block
                    String opCode = "addSourceDirectly";
                    ArrayList<HashMap<String, Object>> blockList = new ArrayList<>();
                    BlocksHandler.builtInBlocks(blockList);
                    String type = " ", typeName = "", specDef = "add source directly %s.inputOnly";
                    int blockColor = Color.parseColor("#5cb722");
                    for (HashMap<String, Object> bDef : blockList) {
                        if (opCode.equals(bDef.get("name"))) {
                            type = (String) bDef.getOrDefault("type", " ");
                            typeName = (String) bDef.getOrDefault("typeName", "");
                            specDef = (String) bDef.getOrDefault("spec", specDef);
                            String colorStr = (String) bDef.get("color");
                            if (colorStr != null) {
                                try {
                                    blockColor = Color.parseColor(colorStr);
                                } catch (Exception ignored) {}
                            }
                            break;
                        }
                    }

                    BlockBean rawBlock = new BlockBean("1", specDef, type, typeName, opCode);
                    rawBlock.color = blockColor;
                    rawBlock.nextBlock = -1;
                    rawBlock.subStack1 = -1;
                    rawBlock.subStack2 = -1;
                    rawBlock.parameters = new ArrayList<>();
                    rawBlock.parameters.add(bodyJava.trim());
                    initialBlocks.add(rawBlock);
                    convertedCount = 1;
                } catch (Exception e) {
                    bodyError = e.getMessage();
                }

                if (bodyError != null) {
                    Log.w(TAG, "bodyJava processing failed for More Block '" + funcName + "': " + bodyError);
                    if (!allowEmptyOnBodyError) {
                        try {
                            resJson.put("ok", false);
                            resJson.put("error", "Failed to add bodyJava into 'Add source directly' block: " + bodyError);
                        } catch (Exception ignored) {}
                        return resJson.toString();
                    } else {
                        warnings.put("bodyJava failed to apply (" + bodyError + "), created empty function.");
                    }
                }
            }

            dataManager.a(javaName, bodyKeyBase, spec);
            dataManager.a(javaName, blockKey, initialBlocks);
            dataManager.k();

            if (fragment != null && fragment.getActivity() instanceof LogicEditorActivity logicEditorActivity) {
                try {
                    logicEditorActivity.c(bodyKeyBase, spec);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to refresh open LogicEditorActivity palette", e);
                }
            }

            try {
                resJson.put("ok", true);
                resJson.put("name", funcName);
                resJson.put("spec", spec);
                resJson.put("javaName", javaName);
                resJson.put("blocksCreated", convertedCount + 1);
                resJson.put("warnings", warnings);
            } catch (Exception ignored) {}

            return resJson.toString();
        }
    }

    public static String registerEvent(Context context, String scId, String rawJavaName, String targetKind, String targetId, String eventName) {
        String defaultJava = getDefaultJavaName(scId);
        String javaName = findActivityJavaName(scId, rawJavaName, defaultJava);
        if (javaName == null) {
            return "Error: Unknown activity '" + rawJavaName + "'. Available activities: " + getAvailableActivitiesList(scId);
        }

        eC dataManager = jC.a(scId);
        if (dataManager == null) {
            return "Error: Project data manager not found for scId: " + scId;
        }

        if (eventName == null || eventName.trim().isEmpty()) {
            return "Error: eventName cannot be empty.";
        }

        String target = targetId != null ? targetId.trim() : "";
        String evName = eventName.trim();
        String kind = targetKind != null ? targetKind.trim().toLowerCase() : "";

        if (kind.isEmpty()) {
            boolean isComp = false;
            ArrayList<ComponentBean> comps = dataManager.e(javaName);
            if (comps != null) {
                for (ComponentBean cb : comps) {
                    if (cb != null && target.equalsIgnoreCase(cb.componentId)) {
                        isComp = true;
                        break;
                    }
                }
            }
            if (!isComp && LogicIrBridge.isKnownComponentEvent(evName)) {
                isComp = true;
            }
            kind = isComp ? "component" : "view";
        }

        synchronized (dataManager) {
            boolean registered = LogicIrBridge.ensureEventRegistered(dataManager, javaName, kind, target, evName);

            if (!registered) {
                return "Error: Failed to register event for target '" + target + "' (" + kind + ") and event '" + evName + "'. Please verify that target exists and event name is valid.";
            }

            dataManager.k();
            String eventKey = ("activity".equals(kind) && ("onCreate".equalsIgnoreCase(target) || "initializeLogic".equalsIgnoreCase(evName)))
                    ? "onCreate_initializeLogic" : target + "_" + evName;

            return "Event '" + eventKey + "' registered successfully for target '" + target + "'. Use 'eventKey': '" + eventKey + "' when calling APPLY_LOGIC_IR or ADD_BLOCK.";
        }
    }

    public static String createActivity(Context context, String scId, String name, JSONObject options, int orientation, int keyboardSetting, String layoutXml) {
        String canonName = name != null ? name.trim() : "";
        if (canonName.isEmpty()) {
            canonName = generateUniqueName(scId, null, "SubActivity", true);
        }
        if (canonName.endsWith(".java")) {
            canonName = canonName.substring(0, canonName.length() - 5);
        } else if (canonName.endsWith(".xml")) {
            canonName = canonName.substring(0, canonName.length() - 4);
        }

        if (!canonName.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
            return "Error: Invalid activity name '" + name + "'. Must start with a letter and contain only alphanumeric characters or underscores.";
        }

        if ("main".equalsIgnoreCase(canonName) || "debug".equalsIgnoreCase(canonName)) {
            return "Error: Activity name '" + canonName + "' is reserved.";
        }

        hC fileManager = jC.b(scId);
        eC dataManager = jC.a(scId);
        if (fileManager == null || dataManager == null) {
            return "Error: Project file manager not available for scId: " + scId;
        }

        String targetXml = canonName + ".xml";

        synchronized (dataManager) {
            synchronized (fileManager) {
                if (fileManager.a != null) {
                    for (String fname : fileManager.a) {
                        if (targetXml.equalsIgnoreCase(fname) || canonName.equalsIgnoreCase(fname)) {
                            return "Error: Activity or layout name '" + canonName + "' already exists in project file list.";
                        }
                    }
                }

                if (fileManager.c != null) {
                    for (ProjectFileBean pfb : fileManager.c) {
                        if (pfb != null && (canonName.equalsIgnoreCase(pfb.fileName) || canonName.equalsIgnoreCase(pfb.getActivityName()) || targetXml.equalsIgnoreCase(pfb.getXmlName()))) {
                            return "Error: Activity '" + canonName + "' (" + pfb.getJavaName() + ") already exists.";
                        }
                    }
                }

                if (fileManager.d != null) {
                    for (ProjectFileBean pfb : fileManager.d) {
                        if (pfb != null && (targetXml.equalsIgnoreCase(pfb.getXmlName()) || canonName.equalsIgnoreCase(pfb.fileName))) {
                            return "Error: Custom view or layout '" + canonName + "' already exists.";
                        }
                    }
                }

                boolean toolbar = options == null || options.optBoolean("toolbar", true);
                boolean fullscreen = options != null && options.optBoolean("fullscreen", false);
                boolean drawer = options != null && options.optBoolean("drawer", false);
                boolean fab = options != null && options.optBoolean("fab", false);

                ProjectFileBean newActivity = new ProjectFileBean(
                        ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY,
                        canonName,
                        orientation,
                        keyboardSetting,
                        toolbar,
                        fullscreen,
                        fab,
                        drawer
                );

                ProjectFileBean drawerBean = null;

                try {
                    if (fileManager.c == null) {
                        fileManager.c = new ArrayList<>();
                    }
                    fileManager.c.add(newActivity);

                    if (drawer) {
                        drawerBean = new ProjectFileBean(ProjectFileBean.PROJECT_FILE_TYPE_CUSTOM_VIEW, newActivity.getDrawerName());
                        if (fileManager.d == null) {
                            fileManager.d = new ArrayList<>();
                        }
                        if (!fileManager.d.contains(drawerBean)) {
                            fileManager.d.add(drawerBean);
                        }
                    }

                    fileManager.j();
                    fileManager.l();
                    dataManager.a(fileManager);

                    String xmlName = newActivity.getXmlName();
                    if (layoutXml != null && !layoutXml.trim().isEmpty()) {
                        try {
                            SketchwareXmlBridge.applyAiXmlToSketchware(context, scId, xmlName, layoutXml);
                        } catch (Exception e) {
                            Log.w(TAG, "createActivity: Layout XML application failed, injecting default layout", e);
                            injectDefaultLayout(dataManager, xmlName);
                        }
                    } else {
                        injectDefaultLayout(dataManager, xmlName);
                    }

                    dataManager.k();
                    return "Activity '" + newActivity.getJavaName() + "' (" + xmlName + ") created successfully.";
                } catch (Exception e) {
                    Log.e(TAG, "createActivity failed, performing rollback", e);
                    if (fileManager.c != null) {
                        fileManager.c.remove(newActivity);
                    }
                    if (drawer && drawerBean != null && fileManager.d != null) {
                        fileManager.d.remove(drawerBean);
                    }
                    fileManager.j();
                    fileManager.l();
                    dataManager.a(fileManager);
                    return "Error creating activity '" + canonName + "': " + e.getMessage() + ". (Rollback performed).";
                }
            }
        }
    }

    private static void injectDefaultLayout(eC dataManager, String xmlName) {
        ArrayList<ViewBean> views = new ArrayList<>();
        ViewBean rootBean = new ViewBean();
        rootBean.type = ViewBean.VIEW_TYPE_LAYOUT_LINEAR;
        rootBean.parent = "root";
        rootBean.index = 0;
        rootBean.layout.width = -1;
        rootBean.layout.height = -1;
        rootBean.layout.orientation = 1;
        views.add(rootBean);
        dataManager.c.put(xmlName, views);
    }
}
