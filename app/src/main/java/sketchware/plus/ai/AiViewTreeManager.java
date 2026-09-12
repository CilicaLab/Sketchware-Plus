package sketchware.plus.ai;

import com.besome.sketch.beans.LayoutBean;
import com.besome.sketch.beans.ViewBean;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import sketchware.plus.tools.ViewBeanFactory;
import sketchware.plus.tools.ViewBeanParser;
import sketchware.plus.utility.AttributeConstants;
import sketchware.plus.utility.SketchwareUtil;

/**
 * Utility class to bridge Sketchware's internal ViewBean system with AI-friendly JSON structures.
 * Provides serialization to nested trees and safe deserialization of AI-generated actions.
 */
public class AiViewTreeManager {

    private static String logPath;

    public static void setLogPath(String path) {
        logPath = path;
    }

    private static void log(String message) {
        if (logPath == null) return;
        try {
            java.io.File file = new java.io.File(logPath);
            if (!file.exists()) file.createNewFile();
            java.io.FileWriter writer = new java.io.FileWriter(file, true);
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(new java.util.Date());
            writer.write("[" + timestamp + "] [Bridge] " + message + "\n");
            writer.close();
        } catch (Exception ignored) {}
    }

    /**
     * Serializes a flat ArrayList of ViewBeans into a strictly nested JSON tree.
     * This helps the LLM understand the visual hierarchy.
     */
    public static JSONObject buildViewTree(ArrayList<ViewBean> views, ViewBean root) {
        if (root == null) return new JSONObject();
        
        try {
            JSONObject json = new JSONObject();
            json.put("id", root.id);
            json.put("type", ViewBean.getViewTypeName(root.type));
            
            JSONObject attrs = new JSONObject();
            // Basic Layout Attributes
            attrs.put("layout_width", getDimenString(root.layout.width));
            attrs.put("layout_height", getDimenString(root.layout.height));
            
            if (root.layout.orientation != LayoutBean.ORIENTATION_NONE) {
                attrs.put("orientation", root.layout.orientation == LayoutBean.ORIENTATION_VERTICAL ? "vertical" : "horizontal");
            }
            
            if (root.layout.weight > 0) {
                attrs.put("layout_weight", String.valueOf(root.layout.weight));
            }

            // Text Attributes (if applicable)
            if (root.text != null) {
                if (root.text.text != null && !root.text.text.isEmpty()) {
                    attrs.put("text", root.text.text);
                }
                if (root.text.textSize > 0) {
                    attrs.put("textSize", root.text.textSize + "sp");
                }
                if (root.text.hint != null && !root.text.hint.isEmpty()) {
                    attrs.put("hint", root.text.hint);
                }
            }

            // Background & Interaction
            if (root.layout.backgroundResource != null && !root.layout.backgroundResource.isEmpty()) {
                attrs.put("background", "@drawable/" + root.layout.backgroundResource);
            }
            
            if (root.enabled == 0) attrs.put("enabled", "false");
            if (root.clickable == 1) attrs.put("clickable", "true");

            json.put("attributes", attrs);

            // Nested Children
            JSONArray childrenArr = new JSONArray();
            for (ViewBean v : views) {
                if (root.id.equals(v.parent)) {
                    childrenArr.put(buildViewTree(views, v));
                }
            }
            
            if (childrenArr.length() > 0) {
                json.put("children", childrenArr);
            }
            
            return json;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static String getDimenString(int val) {
        if (val == LayoutBean.LAYOUT_MATCH_PARENT) return "match_parent";
        if (val == LayoutBean.LAYOUT_WRAP_CONTENT) return "wrap_content";
        return val + "dp";
    }

    /**
     * Processes JSON actions from the AI to manipulate the ViewBean array safely.
     * Supports ADD, UPDATE, and DELETE actions.
     */
    public static void processAiAction(String actionJson, ArrayList<ViewBean> currentViews) {
        try {
            log("Processing AI action: " + actionJson);
            JSONObject json = new JSONObject(actionJson);
            
            // Support both single action or list of actions
            if (json.has("actions") && json.optJSONArray("actions") != null) {
                JSONArray actions = json.getJSONArray("actions");
                for (int i = 0; i < actions.length(); i++) {
                    handleSingleAction(actions.getJSONObject(i), currentViews);
                }
            } else if (json.has("action")) {
                handleSingleAction(json, currentViews);
            }
        } catch (Exception ignored) {
            // Silently fail to avoid crashing the main thread
        }
    }

    private static void handleSingleAction(JSONObject actionObj, ArrayList<ViewBean> views) throws Exception {
        String action = actionObj.optString("action").toUpperCase();
        String id = actionObj.optString("id");
        
        log("Single Action: " + action + " on ID: " + id);
        if (id.isEmpty()) return;

        switch (action) {
            case "ADD":
                String typeStr = actionObj.optString("type");
                String parentId = actionObj.optString("parent", "root");
                int index = actionObj.optInt("index", -1);
                
                // Convert string type to Sketchware internal type ID
                int type = ViewBeanParser.getViewTypeByClassName(typeStr);
                ViewBean bean = new ViewBean(id, type);
                bean.parent = parentId;
                bean.index = index;
                
                // Inherit parent type context
                ViewBean parent = findViewById(views, parentId);
                if (parent != null) {
                    bean.parentType = parent.type;
                } else if (!"root".equals(parentId)) {
                    bean.parent = "root"; // Fallback safety
                }
                
                applyAttributesWithWhitelist(bean, actionObj.optJSONObject("attributes"));
                views.add(bean);
                break;

            case "UPDATE":
                ViewBean target = findViewById(views, id);
                if (target != null) {
                    log("Updating attributes for " + id + ": " + actionObj.optJSONObject("attributes"));
                    applyAttributesWithWhitelist(target, actionObj.optJSONObject("attributes"));
                } else {
                    log("UPDATE ERROR: View " + id + " not found");
                    SketchwareUtil.toastError("Bridge: View " + id + " not found!");
                }
                break;

            case "DELETE":
                deleteViewRecursive(id, views);
                break;
        }
    }

    private static void applyAttributesWithWhitelist(ViewBean bean, JSONObject attrs) throws Exception {
        if (attrs == null) return;
        
        Map<String, String> cleanedMap = new HashMap<>();
        Iterator<String> keys = attrs.keys();
        
        while (keys.hasNext()) {
            String originalKey = keys.next();
            String key = originalKey;
            
            // Strip common hallucinations
            if (key.startsWith("android:")) key = key.substring(8);
            else if (key.startsWith("app:")) key = key.substring(4);
            
            // Validate against Sketchware whitelist
            if (AttributeConstants.BUILT_IN_ATTRIBUTES.contains(key) || isRelativeAttr(key)) {
                // ViewBeanFactory expects namespaced keys for its internal processing
                String finalKey = "android:" + key;
                if (key.startsWith("ad") || key.startsWith("card") || key.startsWith("contentPadding")) {
                    finalKey = "app:" + key;
                }
                
                cleanedMap.put(finalKey, attrs.getString(originalKey));
            }
        }
        
        if (!cleanedMap.isEmpty()) {
            new ViewBeanFactory(bean).applyAttributes(cleanedMap);
        }
    }

    private static boolean isRelativeAttr(String key) {
        for (String attr : AttributeConstants.RELATIVE_ATTRIBUTES) {
            if (attr.equals(key)) return true;
        }
        return false;
    }

    private static ViewBean findViewById(ArrayList<ViewBean> views, String id) {
        for (ViewBean v : views) {
            if (id.equals(v.id)) return v;
        }
        return null;
    }

    private static void deleteViewRecursive(String id, ArrayList<ViewBean> views) {
        ViewBean target = null;
        for (int i = 0; i < views.size(); i++) {
            if (views.get(i).id.equals(id)) {
                target = views.remove(i);
                break;
            }
        }
        
        if (target != null) {
            // Recursively remove any view that considers this ID as parent
            List<String> children = new ArrayList<>();
            for (ViewBean v : views) {
                if (id.equals(v.parent)) {
                    children.add(v.id);
                }
            }
            for (String childId : children) {
                deleteViewRecursive(childId, views);
            }
        }
    }
}
