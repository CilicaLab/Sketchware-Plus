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

import a.a.a.wq;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Utility class to bridge Sketchware's internal ViewBean system with AI-friendly JSON structures.
 * Provides serialization to nested trees and safe deserialization of AI-generated actions.
 */
public class AiViewTreeManager {

    private static String logPath;
    private static String currentScId;

    public static void setLogPath(String path) {
        logPath = path;
    }

    public static void setScId(String scId) {
        currentScId = scId;
    }

    public static void log(String scId, String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String formatted = "[" + timestamp + "] [AiViewTreeManager] " + message + "\n";
        android.util.Log.d("SketchwarePlusAI", message);

        if (logPath != null) {
            try {
                File file = new File(logPath);
                if (!file.exists()) file.createNewFile();
                FileWriter writer = new FileWriter(file, true);
                writer.write(formatted);
                writer.close();
            } catch (Exception ignored) {}
        }

        String targetScId = (scId != null && !scId.isEmpty()) ? scId : currentScId;
        if (targetScId != null && !targetScId.isEmpty()) {
            try {
                String primaryPath = wq.b(targetScId) + File.separator + "data.log";
                File primaryFile = new File(primaryPath);
                if (primaryFile.getParentFile() != null && !primaryFile.getParentFile().exists()) {
                    primaryFile.getParentFile().mkdirs();
                }
                if (!primaryFile.exists()) primaryFile.createNewFile();
                FileWriter writer1 = new FileWriter(primaryFile, true);
                writer1.write(formatted);
                writer1.close();

                String secondaryPath = wq.d(targetScId) + File.separator + "data.log";
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

    public static void log(String message) {
        log(null, message);
    }

    /**
     * Serializes a flat ArrayList of ViewBeans into a strictly nested JSON tree.
     * This helps the LLM understand the visual hierarchy.
     */
    public static JSONObject buildViewTree(ArrayList<ViewBean> views, ViewBean root) {
        log("buildViewTree ENTRY - views count: " + (views != null ? views.size() : 0) + ", root: " + (root != null ? root.id : "null"));
        if (root == null) return new JSONObject();
        
        try {
            JSONObject json = new JSONObject();
            json.put("id", root.id);
            json.put("type", ViewBean.getViewTypeName(root.type));
            
            JSONObject attrs = new JSONObject();
            attrs.put("layout_width", getDimenString(root.layout.width));
            attrs.put("layout_height", getDimenString(root.layout.height));
            
            if (root.layout.orientation != LayoutBean.ORIENTATION_NONE) {
                attrs.put("orientation", root.layout.orientation == LayoutBean.ORIENTATION_VERTICAL ? "vertical" : "horizontal");
            }
            
            if (root.layout.weight > 0) {
                attrs.put("layout_weight", String.valueOf(root.layout.weight));
            }

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

            if (root.layout.backgroundResource != null && !root.layout.backgroundResource.isEmpty()) {
                attrs.put("background", "@drawable/" + root.layout.backgroundResource);
            }
            
            if (root.enabled == 0) attrs.put("enabled", "false");
            if (root.clickable == 1) attrs.put("clickable", "true");

            json.put("attributes", attrs);

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
            
            if (json.has("actions") && json.optJSONArray("actions") != null) {
                JSONArray actions = json.getJSONArray("actions");
                for (int i = 0; i < actions.length(); i++) {
                    handleSingleAction(actions.getJSONObject(i), currentViews);
                }
            } else if (json.has("action")) {
                handleSingleAction(json, currentViews);
            }
        } catch (Exception e) {
            log("Error processing AI action: " + e.getMessage());
            android.util.Log.e("SketchwarePlusAI", "Error processing AI action: " + e.getMessage(), e);
            SketchwareUtil.toastError("Action processing failed: " + e.getMessage());
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
                
                int type = ViewBeanParser.getViewTypeByClassName(typeStr);
                ViewBean bean = new ViewBean(id, type);
                bean.parent = parentId;
                bean.index = index;
                
                ViewBean parent = findViewById(views, parentId);
                if (parent != null) {
                    bean.parentType = parent.type;
                } else if (!"root".equals(parentId)) {
                    bean.parent = "root";
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

    public static boolean isAppNamespaceAttr(String key) {
        if (key == null) return false;
        String clean = key;
        if (clean.startsWith("app:")) clean = clean.substring(4);
        return clean.equals("adSize") || clean.equals("adUnitId") || clean.startsWith("card") || clean.startsWith("contentPadding");
    }

    private static void applyAttributesWithWhitelist(ViewBean bean, JSONObject attrs) throws Exception {
        log("applyAttributesWithWhitelist ENTRY - bean id: " + (bean != null ? bean.id : "null") + ", attrs: " + attrs);
        if (attrs == null) return;
        
        Map<String, String> cleanedMap = new HashMap<>();
        Iterator<String> keys = attrs.keys();
        
        while (keys.hasNext()) {
            String originalKey = keys.next();
            String key = originalKey;
            
            if (key.startsWith("android:")) key = key.substring(8);
            else if (key.startsWith("app:")) key = key.substring(4);
            
            if (AttributeConstants.BUILT_IN_ATTRIBUTES.contains(key) || isRelativeAttr(key) || isAppNamespaceAttr(originalKey)) {
                String finalKey = "android:" + key;
                if (key.startsWith("ad") || key.startsWith("card") || key.startsWith("contentPadding")) {
                    finalKey = "app:" + key;
                }
                
                cleanedMap.put(finalKey, attrs.getString(originalKey));
            }
        }
        
        log("applyAttributesWithWhitelist - applied cleanedMap size: " + cleanedMap.size());
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
        if (views == null) return null;
        for (ViewBean v : views) {
            if (id.equals(v.id)) return v;
        }
        return null;
    }

    private static void deleteViewRecursive(String id, ArrayList<ViewBean> views) {
        log("deleteViewRecursive ENTRY - id: " + id + ", views size: " + (views != null ? views.size() : 0));
        if (views == null) return;
        ViewBean target = null;
        for (int i = 0; i < views.size(); i++) {
            if (views.get(i).id.equals(id)) {
                target = views.remove(i);
                break;
            }
        }
        
        if (target != null) {
            log("deleteViewRecursive - deleted target view: " + target.id);
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
