package sketchware.plus.ai;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import a.a.a.eC;
import a.a.a.hC;
import a.a.a.jC;
import a.a.a.uq;
import mod.hey.studios.moreblock.ReturnMoreblockManager;

/**
 * Static helper utilities for SK Assistant project structure operations.
 * Handles canonical javaName resolution, structure reporting, and safe eC/hC mutations.
 */
public class ProjectStructureAide {

    private static final String TAG = "ProjectStructureAide";

    /**
     * Resolves various alias forms ("main", "main.xml", "MainActivity", "MainActivity.java")
     * into the canonical Java file key used by eC maps (e.g. "MainActivity.java").
     */
    public static String resolveJavaName(String scId, String input) {
        if (input == null || input.trim().isEmpty()) {
            return "MainActivity.java";
        }
        String trimmed = input.trim();
        if (scId != null && !scId.trim().isEmpty()) {
            hC fileManager = jC.b(scId);
            if (fileManager != null) {
                if (fileManager.c != null) {
                    for (ProjectFileBean pfb : fileManager.c) {
                        if (pfb == null) continue;
                        if (trimmed.equalsIgnoreCase(pfb.fileName)
                                || trimmed.equalsIgnoreCase(pfb.getJavaName())
                                || trimmed.equalsIgnoreCase(pfb.getXmlName())
                                || trimmed.equalsIgnoreCase(pfb.getActivityName())) {
                            return pfb.getJavaName();
                        }
                    }
                }
                if (fileManager.d != null) {
                    for (ProjectFileBean pfb : fileManager.d) {
                        if (pfb == null) continue;
                        if (trimmed.equalsIgnoreCase(pfb.fileName)
                                || trimmed.equalsIgnoreCase(pfb.getJavaName())
                                || trimmed.equalsIgnoreCase(pfb.getXmlName())
                                || trimmed.equalsIgnoreCase(pfb.getActivityName())) {
                            return pfb.getJavaName();
                        }
                    }
                }
            }
        }

        // Fallback heuristics when scId is not available or file is not in list
        if (trimmed.endsWith(".java")) {
            return trimmed;
        }
        if (trimmed.endsWith(".xml")) {
            String base = trimmed.substring(0, trimmed.length() - 4);
            return ProjectFileBean.getJavaName(base);
        }
        return ProjectFileBean.getJavaName(trimmed);
    }

    /**
     * Validates an identifier name against Sketchware PropertyNameValidator rules,
     * Java keywords, reserved method names, and existing project identifiers.
     *
     * @return null if valid, or an error description string if invalid.
     */
    public static String validateName(Context context, String scId, String javaName, String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Name cannot be empty.";
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 100) {
            return "Name length must be between 1 and 100 characters.";
        }
        if (!Character.isLetter(trimmed.charAt(0))) {
            return "Name must start with a letter.";
        }
        if (!trimmed.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
            return "Name can only contain letters, numbers, and underscores.";
        }

        String lower = trimmed.toLowerCase();

        // 1. Check Java reserved keywords (uq.b)
        if (uq.b != null) {
            for (String reserved : uq.b) {
                if (lower.equals(reserved.toLowerCase())) {
                    return "Name '" + trimmed + "' is a Java reserved keyword.";
                }
            }
        }

        // 2. Check Android / Sketchware reserved method names (uq.a())
        if (uq.a() != null) {
            for (String reserved : uq.a()) {
                if (lower.equals(reserved.toLowerCase())) {
                    return "Name '" + trimmed + "' is a reserved method name.";
                }
            }
        }

        // 3. Check existing file names and project-wide identifiers
        if (scId != null && !scId.isEmpty()) {
            eC dataManager = jC.a(scId);
            hC fileManager = jC.b(scId);

            if (fileManager != null && fileManager.a != null) {
                for (String fname : fileManager.a) {
                    if (lower.equals(fname.toLowerCase())) {
                        return "Name '" + trimmed + "' conflicts with an existing file name.";
                    }
                }
            }

            if (dataManager != null) {
                ProjectFileBean targetPfb = null;
                if (fileManager != null) {
                    targetPfb = fileManager.a(javaName);
                }
                ArrayList<String> takenNames = dataManager.a(targetPfb);
                if (takenNames != null) {
                    for (String taken : takenNames) {
                        if (lower.equals(taken.toLowerCase())) {
                            return "Name '" + trimmed + "' conflicts with an existing variable, list, component, function, or view ID.";
                        }
                    }
                }
            }
        }

        return null; // Valid
    }

    /**
     * Reads and returns project structure details as a formatted string.
     */
    public static String getProjectStructureSummary(String scId, String rawJavaName) {
        String javaName = resolveJavaName(scId, rawJavaName);
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
            String xmlName = ProjectFileBean.getXmlName(javaName);

            // Variables (dataManager.k(javaName))
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

            // Lists (dataManager.j(javaName))
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

            // More Blocks (dataManager.i(javaName))
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

            // Registered Events (dataManager.g(javaName))
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

            // Components (dataManager.e(javaName))
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

            // View IDs (dataManager.d(xmlName))
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
        String javaName = resolveJavaName(scId, rawJavaName);
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
                String typeStr = item.optString("type", "string").trim().toLowerCase();

                int typeInt = switch (typeStr) {
                    case "boolean", "bool" -> 0;
                    case "number", "double", "int", "integer" -> 1;
                    case "string", "str" -> 2;
                    case "map" -> 3;
                    default -> 2;
                };

                // Check exists in eC
                if (dataManager.f(javaName, typeInt, varName)) {
                    existing.add(varName);
                    continue;
                }

                String err = validateName(context, scId, javaName, varName);
                if (err != null) {
                    errors.add(varName + ": " + err);
                    continue;
                }

                // eC.c(javaName, type, name) adds variable
                dataManager.c(javaName, typeInt, varName);
                added.add(varName + " (" + typeStr + ")");
            }

            dataManager.k(); // Persist changes
        }

        StringBuilder res = new StringBuilder();
        if (!added.isEmpty()) {
            res.append("Successfully added variables: ").append(String.join(", ", added)).append(". ");
        }
        if (!existing.isEmpty()) {
            res.append("Variables already exist: ").append(String.join(", ", existing)).append(". ");
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
        String javaName = resolveJavaName(scId, rawJavaName);
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
                String typeStr = item.optString("type", "string").trim().toLowerCase();

                int typeInt = switch (typeStr) {
                    case "number", "double", "int", "integer" -> 1; // ListInt
                    case "string", "str" -> 2; // ListString
                    case "map" -> 3; // ListMap
                    default -> 2;
                };

                // Check exists in eC
                if (dataManager.f(javaName, typeInt, listName)) {
                    existing.add(listName);
                    continue;
                }

                String err = validateName(context, scId, javaName, listName);
                if (err != null) {
                    errors.add(listName + ": " + err);
                    continue;
                }

                // eC.b(javaName, type, name) adds list
                dataManager.b(javaName, typeInt, listName);
                added.add(listName + " (" + typeStr + ")");
            }

            dataManager.k(); // Persist changes
        }

        StringBuilder res = new StringBuilder();
        if (!added.isEmpty()) {
            res.append("Successfully added lists: ").append(String.join(", ", added)).append(". ");
        }
        if (!existing.isEmpty()) {
            res.append("Lists already exist: ").append(String.join(", ", existing)).append(". ");
        }
        if (!errors.isEmpty()) {
            res.append("Failed items: ").append(String.join("; ", errors)).append(".");
        }
        return res.toString();
    }

    /**
     * Adds a custom function (More Block) to the activity.
     */
    public static String addMoreBlock(Context context, String scId, String rawJavaName, String name, JSONArray params, String returnType, String bodyJava) {
        String javaName = resolveJavaName(scId, rawJavaName);
        eC dataManager = jC.a(scId);
        if (dataManager == null) {
            return "Error: Project data manager not found for scId: " + scId;
        }

        String funcName = name != null ? name.trim() : "";
        String err = validateName(context, scId, javaName, funcName);
        if (err != null) {
            return "Error validating function name: " + err;
        }

        synchronized (dataManager) {
            // Check if function already exists in eC.i(javaName)
            ArrayList<Pair<String, String>> existingMbs = dataManager.i(javaName);
            if (existingMbs != null) {
                for (Pair<String, String> mb : existingMbs) {
                    String existingName = ReturnMoreblockManager.getMbName(mb.first);
                    if (funcName.equalsIgnoreCase(existingName)) {
                        return "More block '" + funcName + "' already exists in " + javaName + ".";
                    }
                }
            }

            // Build spec and signature
            StringBuilder specBuilder = new StringBuilder(funcName);
            if (returnType != null && !returnType.trim().isEmpty() && !"void".equalsIgnoreCase(returnType.trim())) {
                specBuilder.append("[").append(returnType.trim()).append("]");
            }

            String signature = specBuilder.toString();

            if (params != null && params.length() > 0) {
                for (int i = 0; i < params.length(); i++) {
                    JSONObject p = params.optJSONObject(i);
                    if (p == null) continue;

                    String pName = p.optString("name", "").trim();
                    String pType = p.optString("type", "string").trim().toLowerCase();

                    String pToken = switch (pType) {
                        case "boolean", "bool" -> "%b." + pName;
                        case "number", "double", "int", "integer" -> "%d." + pName;
                        case "string", "str" -> "%s." + pName;
                        default -> "%s." + pName;
                    };

                    specBuilder.append(" ").append(pToken);
                }
            }

            String spec = specBuilder.toString();

            // 1. Add pair to eC.a(javaName, spec, signature)
            dataManager.a(javaName, spec, signature);

            // 2. Register event in eC.a(javaName, EventBean)
            EventBean eb = new EventBean(EventBean.EVENT_TYPE_ETC, -1, funcName, "moreBlock");
            dataManager.a(javaName, eb);

            // 3. Initialize block list for funcName_moreBlock
            String blockKey = funcName + "_moreBlock";
            ArrayList<BlockBean> initialBlocks = new ArrayList<>();
            BlockBean root = new BlockBean("0", spec, "f", "definedFunc", "definedFunc");
            initialBlocks.add(root);

            if (bodyJava != null && !bodyJava.trim().isEmpty()) {
                // If body Java is provided, attempt conversion and validation
                try {
                    JSONObject ir = LogicIrBridge.javaToIr(bodyJava);
                    if (ir != null && ir.has("body")) {
                        LogicIrBridge.ValidationResult vResult = LogicIrBridge.validateIr(ir, scId, javaName);
                        if (vResult.isValid) {
                            ArrayList<BlockBean> convertedBlocks = LogicIrBridge.irToBlocks(ir);
                            if (convertedBlocks != null && !convertedBlocks.isEmpty()) {
                                initialBlocks.addAll(convertedBlocks);
                            }
                        } else {
                            Log.w(TAG, "addMoreBlock: bodyJava validation failed -> " + vResult.errorMessage);
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "addMoreBlock: Exception parsing bodyJava", e);
                }
            }

            dataManager.a(javaName, blockKey, initialBlocks);
            dataManager.k(); // Persist changes
            return "More block '" + funcName + "' created successfully in " + javaName + ".";
        }
    }

    /**
     * Registers an event for a target view, component, or activity.
     */
    public static String registerEvent(Context context, String scId, String rawJavaName, String targetKind, String targetId, String eventName) {
        String javaName = resolveJavaName(scId, rawJavaName);
        eC dataManager = jC.a(scId);
        if (dataManager == null) {
            return "Error: Project data manager not found for scId: " + scId;
        }

        if (eventName == null || eventName.trim().isEmpty()) {
            return "Error: eventName cannot be empty.";
        }

        String target = targetId != null ? targetId.trim() : "";
        String evName = eventName.trim();
        String kind = targetKind != null ? targetKind.trim().toLowerCase() : "view";

        String eventKey;
        if ("activity".equals(kind)) {
            if ("onCreate".equalsIgnoreCase(target) || "initializeLogic".equalsIgnoreCase(evName)) {
                eventKey = "onCreate_initializeLogic";
            } else if ("Import".equalsIgnoreCase(target) || "Import".equalsIgnoreCase(evName)) {
                eventKey = "Import_Import";
            } else {
                eventKey = evName + "_" + evName;
            }
        } else {
            eventKey = target + "_" + evName;
        }

        synchronized (dataManager) {
            boolean registered = LogicIrBridge.ensureEventRegistered(dataManager, javaName, eventKey);

            if (!registered) {
                return "Error: Failed to register event '" + eventKey + "'. Please verify that target '" + target + "' exists and event '" + evName + "' is valid for it.";
            }

            dataManager.k(); // Persist
            return "Event '" + eventKey + "' registered successfully. Use 'eventKey': '" + eventKey + "' when calling APPLY_LOGIC_IR or ADD_BLOCK.";
        }
    }

    /**
     * Creates a new Activity in the project.
     */
    public static String createActivity(Context context, String scId, String name, JSONObject options, int orientation, int keyboardSetting, String layoutXml) {
        if (name == null || name.trim().isEmpty()) {
            return "Error: Activity name cannot be empty.";
        }

        String canonName = name.trim();
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

        synchronized (dataManager) {
            synchronized (fileManager) {
                // Check if activity already exists
                if (fileManager.c != null) {
                    for (ProjectFileBean pfb : fileManager.c) {
                        if (pfb != null && (canonName.equalsIgnoreCase(pfb.fileName) || canonName.equalsIgnoreCase(pfb.getActivityName()))) {
                            return "Activity '" + canonName + "' (" + pfb.getJavaName() + ") already exists.";
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

                if (fileManager.c == null) {
                    fileManager.c = new ArrayList<>();
                }
                fileManager.c.add(newActivity);

                if (drawer) {
                    ProjectFileBean drawerBean = new ProjectFileBean(ProjectFileBean.PROJECT_FILE_TYPE_CUSTOM_VIEW, newActivity.getDrawerName());
                    if (fileManager.d == null) {
                        fileManager.d = new ArrayList<>();
                    }
                    if (!fileManager.d.contains(drawerBean)) {
                        fileManager.d.add(drawerBean);
                    }
                }

                fileManager.j(); // Rebuild java and xml name lists
                fileManager.l(); // Persist activity file list to disk
                dataManager.a(fileManager); // Sync file list into dataManager

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

                dataManager.k(); // Persist logic & view data
                return "Activity '" + newActivity.getJavaName() + "' (" + xmlName + ") created successfully.";
            }
        }
    }

    private static void injectDefaultLayout(eC dataManager, String xmlName) {
        ArrayList<ViewBean> views = new ArrayList<>();
        ViewBean rootBean = new ViewBean();
        rootBean.id = "linear1";
        rootBean.type = ViewBean.VIEW_TYPE_LAYOUT_LINEAR;
        rootBean.parent = "root";
        rootBean.index = 0;
        rootBean.layout.width = -1; // MATCH_PARENT
        rootBean.layout.height = -1; // MATCH_PARENT
        rootBean.layout.orientation = 1; // VERTICAL
        views.add(rootBean);
        dataManager.c.put(xmlName, views);
    }
}
