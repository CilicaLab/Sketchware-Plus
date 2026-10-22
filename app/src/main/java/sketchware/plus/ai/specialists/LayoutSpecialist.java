package sketchware.plus.ai.specialists;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.EditText;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import a.a.a.eC;
import a.a.a.hC;
import a.a.a.jC;
import a.a.a.yq;
import sketchware.plus.ai.AiClient;
import sketchware.plus.ai.AiViewTreeManager;
import sketchware.plus.ai.LayoutPreviewer;
import sketchware.plus.ai.ProjectSnapshot;
import sketchware.plus.ai.SkAssistantFragment;
import sketchware.plus.ai.SketchwareXmlBridge;
import sketchware.plus.managers.inject.InjectRootLayoutManager;
import sketchware.plus.tools.ViewBeanParser;
import sketchware.plus.tools.ViewBeanFactory;
import sketchware.plus.utility.AttributeConstants;
import sketchware.plus.utility.SketchwareUtil;

public class LayoutSpecialist extends BaseSpecialist {

    public LayoutSpecialist(SkAssistantFragment fragment) {
        super(fragment);
    }

    @Override
    public void process(String prompt, String reasoning) {
        setStatus("Grasping layout request...");
        Context androidContext = getContext();
        if (androidContext == null) return;

        jC.projectOperationsExecutor.execute(() -> {
            String currentXml = SketchwareXmlBridge.getRawXml(androidContext, getScId(), getProjectFile());

            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append("Current Activity: ").append(getProjectFile().getXmlName()).append("\n");
            contextBuilder.append("Current Layout XML:\n").append(currentXml).append("\n\n");

            try {
                InjectRootLayoutManager rootManager = new InjectRootLayoutManager(getScId());
                ViewBean rootBean = rootManager.toBean(getProjectFile().getXmlName());
                ArrayList<ViewBean> views = jC.a(getScId()).d(getProjectFile().getXmlName());
                contextBuilder.append("View Hierarchy (JSON):\n");
                contextBuilder.append(AiViewTreeManager.buildViewTree(views, rootBean).toString(2)).append("\n");
            } catch (Exception e) {
                log("Hierarchy gathering failed: " + e.getMessage());
            }

            String context = contextBuilder.toString();

            String systemPrompt = """
    You are an expert Android Layout/UI Specialist. Analyze the user's request and update the layout XML strictly following the constraints below.

    CRITICAL RULES:
    1. KEEP every existing view, id, text, hint, textSize, colors and attribute exactly as given unless user asked to change.
    2. Never remove or rename IDs.
    3. Never output <?xml, comments, or markdown.
    4. Only change what user asked.
    5. Output MUST be valid JSON containing <layout_xml>...</layout_xml> or actions. Do not wrap response in markdown code blocks.
    
    LAYOUT RULES:
    """ + getLayoutRules() + """

    RESPONSE CONTRACT (JSON ONLY):
    {
      "category": "UI_DESIGNER",
      "thought_process": "<Brief UI decisions detailing layout logic step-by-step>",
      "summary": "<One-line display for summary user>",
      "actions": [
        {
          "type": "UPDATE_LAYOUT_FULL_XML",
          "xml": "<Complete Android XML properly escaped>"
        }
      ]
    }
    """;
            Activity activity = fragment.getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    Context c = getContext();
                    if (c == null) return;
                    AiClient.askAi(c, systemPrompt, "USER REQUEST: " + prompt + "\n\nPROJECT CONTEXT:\n" + context, AiClient.AiTemperatureType.ASSISTANT_MODE, new AiClient.AiCallback() {
                        @Override
                        public void onSuccess(String response) {
                            setStatus(null);
                            fragment.handleAiResponse(response, "UI_DESIGNER");
                        }

                        @Override
                        public void onSuccess(String response, int promptTokens, int completionTokens, int totalTokens) {
                            setStatus(null);
                            fragment.updateTokenUsage(promptTokens, completionTokens, totalTokens);
                            fragment.handleAiResponse(response, "UI_DESIGNER");
                        }

                        @Override
                        public void onError(String error) {
                            log("Layout AI Error: " + error);
                            setStatus(null);
                            fragment.addSystemMessage("Error: " + error);
                        }

                        @Override
                        public void onRetry(int retryCount, long delayMillis) {
                            setStatus("Rate limit hit. Retrying in " + String.format(Locale.US, "%.1f", delayMillis / 1000.0) + "s... (Attempt " + retryCount + ")");
                        }
                    });
                });
            }
        });
    }

    @Override
    public void handleAction(JSONObject action) throws JSONException {
        String type = fragment.normalizeActionType(action.optString("type"));
        switch (type) {
            case "MODIFY_VIEW":
                applyModifyView(action.getString("id"), action.getString("field"), action.getString("value"), false);
                break;
            case "MOVE_VIEW":
                applyMoveView(action.getString("id"), action.getString("newParent"), action.optInt("newIndex", -1), false);
                break;
            case "ADD_VIEW":
                Map<String, String> attrs = new HashMap<>();
                JSONObject attrObj = action.optJSONObject("attributes");
                if (attrObj != null) {
                    Iterator<String> keys = attrObj.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        attrs.put(k, attrObj.getString(k));
                    }
                }
                applyAddView(action.getString("viewType"), action.optString("parent", "root"), action.optInt("index", -1), action.getString("id"), attrs, false);
                break;
            case "DELETE_VIEW":
                applyDeleteView(action.getString("id"), false);
                break;
            case "UPDATE_LAYOUT_FULL_XML":
                applyXml(action.getString("xml"), getProjectFile().getXmlName());
                break;
            case "CUSTOM_VIEW":
                applyCustomView(action.getString("name"), action.optString("xml"));
                break;
        }
    }

    private String getLayoutRules() {
        return "LAYOUT ARCHITECTURE:\n" +
                "- KEEP every existing view, id, text, hint, textSize, colors and attribute exactly as given unless user asked to change.\n" +
                "- Never remove or rename IDs.\n" +
                "- The outermost view in the XML is the Activity's Root Container.\n" +
                "- You CAN change the Root Container type if the content requires it.\n\n";
    }

    public void applyXml(String xml, String targetXmlName) {
        String finalXmlName = targetXmlName != null ? targetXmlName : getProjectFile().getXmlName();
        String title = "Review & Apply Layout" + (targetXmlName != null ? " to " + targetXmlName : "");
        showPreviewDialog(title, xml, finalXmlName);
    }

    private void showPreviewDialog(String title, String xml, String finalXmlName) {
        View previewView = LayoutPreviewer.createPreview(getContext(), getScId(), xml, finalXmlName);

        if (previewView == null) {
            SketchwareUtil.toastError("Failed to render preview. Falling back to code editor.");
            showCodeEditorDialog(title, xml, finalXmlName);
            return;
        }

        new MaterialAlertDialogBuilder(getContext())
                .setTitle(title)
                .setView(previewView)
                .setPositiveButton("Apply", (dialog, which) -> {
                    try {
                        fragment.undoSnapshot = new ProjectSnapshot(getScId(), finalXmlName);
                        SketchwareXmlBridge.applyAiXmlToSketchware(getContext(), getScId(), finalXmlName, xml);
                        fragment.addSystemMessage("Layout applied to " + finalXmlName);
                        fragment.setUndoVisible(true);
                        fragment.refreshDesigner();
                    } catch (Exception e) {
                        fragment.handleXmlError(xml, e.getMessage(), finalXmlName);
                    }
                })
                .setNeutralButton("Edit Code", (dialog, which) -> {
                    showCodeEditorDialog(title, xml, finalXmlName);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCodeEditorDialog(String title, String xml, String finalXmlName) {
        final EditText codeInput = new EditText(getContext());
        codeInput.setText(xml);
        codeInput.setTypeface(Typeface.MONOSPACE);
        codeInput.setTextSize(12);
        codeInput.setPadding(32, 32, 32, 32);

        new MaterialAlertDialogBuilder(getContext())
                .setTitle(title)
                .setView(codeInput)
                .setPositiveButton("Apply", (dialog, which) -> {
                    String editedXml = codeInput.getText().toString();
                    try {
                        fragment.undoSnapshot = new ProjectSnapshot(getScId(), finalXmlName);
                        SketchwareXmlBridge.applyAiXmlToSketchware(getContext(), getScId(), finalXmlName, editedXml);
                        fragment.addSystemMessage("Layout applied to " + finalXmlName);
                        fragment.setUndoVisible(true);
                        fragment.refreshDesigner();
                    } catch (Exception e) {
                        fragment.handleXmlError(editedXml, e.getMessage(), finalXmlName);
                    }
                })
                .setNeutralButton("Preview", (dialog, which) -> {
                    showPreviewDialog(title, codeInput.getText().toString(), finalXmlName);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public void applyModifyView(String viewId, String fieldPath, String newValue, boolean snapshot) {
        try {
            String tempField = fieldPath;
            if (tempField.startsWith("android:")) tempField = tempField.substring(8);
            if (tempField.startsWith("app:")) tempField = tempField.substring(4);
            final String cleanField = tempField;

            if (!AttributeConstants.BUILT_IN_ATTRIBUTES.contains(cleanField) &&
                    !AttributeConstants.RELATIVE_ATTRIBUTES.contains(fieldPath) &&
                    !AttributeConstants.RELATIVE_ATTRIBUTES.contains(cleanField) &&
                    !AiViewTreeManager.isAppNamespaceAttr(fieldPath)) {
                fragment.addSystemMessage("Attribute '" + fieldPath + "' is not whitelisted.");
                return;
            }

            eC dataManager = jC.a(getScId());
            InjectRootLayoutManager rootManager = new InjectRootLayoutManager(getScId());
            ArrayList<ViewBean> views = dataManager.d(getProjectFile().getXmlName());
            ViewBean target = null;

            if ("root".equals(viewId)) {
                target = rootManager.toBean(getProjectFile().getXmlName());
            } else {
                for (ViewBean v : views) {
                    if (viewId.equals(v.id)) {
                        target = v;
                        break;
                    }
                }
            }

            if (target == null) {
                fragment.addSystemMessage("View '" + viewId + "' not found.");
                return;
            }

            ViewBean finalTarget = target;
            String finalKey = fieldPath;
            if (!fieldPath.contains(":")) {
                finalKey = "android:" + cleanField;
                if (AiViewTreeManager.isAppNamespaceAttr(fieldPath) || cleanField.startsWith("ad") || cleanField.startsWith("card") || cleanField.startsWith("contentPadding")) {
                    finalKey = "app:" + cleanField;
                }
            }

            final String finalValue = normalizeValue(cleanField, newValue);

            if (!snapshot) {
                if ("root".equals(viewId)) {
                    InjectRootLayoutManager.Root currentRoot = rootManager.getLayoutByFileName(getProjectFile().getXmlName());
                    String rootClass = currentRoot != null ? currentRoot.getClassName() : finalTarget.convert;
                    Map<String, String> rootAttrs = currentRoot != null ? new LinkedHashMap<>(currentRoot.getAttributes()) : new LinkedHashMap<>();
                    rootAttrs.put(finalKey, finalValue);
                    finalTarget.convert = rootClass;
                    new ViewBeanFactory(finalTarget).applyAttributes(rootAttrs);
                    rootManager.set(getProjectFile().getXmlName(), new InjectRootLayoutManager.Root(rootClass, rootAttrs));
                } else {
                    HashMap<String, String> attr = new HashMap<>();
                    attr.put(finalKey, finalValue);
                    new ViewBeanFactory(finalTarget).applyAttributes(attr);
                }
                dataManager.n(new yq(getContext(), getScId()).projectMyscPath + "view");
                fragment.refreshDesigner();
                return;
            }

            String summary = "Modify '" + viewId + "': set " + cleanField + " = " + finalValue;
            String finalActionKey = finalKey;
            new MaterialAlertDialogBuilder(getContext())
                    .setTitle("Apply Modification")
                    .setMessage(summary)
                    .setPositiveButton("Apply", (dialog, which) -> {
                        fragment.undoSnapshot = new ProjectSnapshot(getScId(), getProjectFile().getXmlName());
                        if ("root".equals(viewId)) {
                            InjectRootLayoutManager.Root currentRoot = rootManager.getLayoutByFileName(getProjectFile().getXmlName());
                            String rootClass = currentRoot != null ? currentRoot.getClassName() : finalTarget.convert;
                            Map<String, String> rootAttrs = currentRoot != null ? new LinkedHashMap<>(currentRoot.getAttributes()) : new LinkedHashMap<>();
                            rootAttrs.put(finalActionKey, finalValue);
                            finalTarget.convert = rootClass;
                            new ViewBeanFactory(finalTarget).applyAttributes(rootAttrs);
                            rootManager.set(getProjectFile().getXmlName(), new InjectRootLayoutManager.Root(rootClass, rootAttrs));
                        } else {
                            HashMap<String, String> attr = new HashMap<>();
                            attr.put(finalActionKey, finalValue);
                            new ViewBeanFactory(finalTarget).applyAttributes(attr);
                        }
                        dataManager.n(new yq(getContext(), getScId()).projectMyscPath + "view");
                        fragment.addSystemMessage("View '" + viewId + "' modified.");
                        fragment.setUndoVisible(true);
                        fragment.refreshDesigner();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Exception e) {
            fragment.addSystemMessage("Error: " + e.getMessage());
        }
    }

    public void applyAddView(String type, String parentId, int index, String newId, Map<String, String> attributes, boolean snapshot) {
        try {
            if (newId == null || !newId.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
                fragment.addSystemMessage("Invalid view ID: '" + newId + "'. Must be a valid Java identifier with no spaces.");
                return;
            }

            Map<String, String> normalizedAttrs = new HashMap<>();
            for (String key : attributes.keySet()) {
                String cleanKey = key;
                if (cleanKey.startsWith("android:")) cleanKey = cleanKey.substring(8);
                else if (cleanKey.startsWith("app:")) cleanKey = cleanKey.substring(4);

                if (!AttributeConstants.BUILT_IN_ATTRIBUTES.contains(cleanKey) &&
                        !AttributeConstants.RELATIVE_ATTRIBUTES.contains(key) &&
                        !AttributeConstants.RELATIVE_ATTRIBUTES.contains(cleanKey) &&
                        !AiViewTreeManager.isAppNamespaceAttr(key)) {
                    fragment.addSystemMessage("Skipped non-whitelisted attribute '" + key + "'.");
                    continue;
                }

                String finalKey = key;
                if (!key.contains(":")) {
                    finalKey = "android:" + cleanKey;
                    if (AiViewTreeManager.isAppNamespaceAttr(key) || cleanKey.startsWith("ad") || cleanKey.startsWith("card") || cleanKey.startsWith("contentPadding")) {
                        finalKey = "app:" + cleanKey;
                    }
                }
                normalizedAttrs.put(finalKey, normalizeValue(cleanKey, attributes.get(key)));
            }

            eC dataManager = jC.a(getScId());
            ArrayList<ViewBean> views = dataManager.d(getProjectFile().getXmlName());

            for (ViewBean v : views) {
                if (newId.equals(v.id)) {
                    fragment.addSystemMessage("ID '" + newId + "' already exists.");
                    return;
                }
            }

            String actualParentId = parentId;
            if (!"root".equals(actualParentId)) {
                boolean parentExists = false;
                for (ViewBean v : views) {
                    if (actualParentId.equals(v.id)) {
                        parentExists = true;
                        break;
                    }
                }
                if (!parentExists) {
                    fragment.addSystemMessage("Parent '" + parentId + "' not found, falling back to root.");
                    actualParentId = "root";
                }
            }

            int viewType = ViewBeanParser.getViewTypeByClassName(type);
            ViewBean bean = new ViewBean(newId, viewType);
            bean.parent = actualParentId;
            bean.index = index;

            if ("root".equals(actualParentId)) {
                InjectRootLayoutManager rootManager = new InjectRootLayoutManager(getScId());
                bean.parentType = ViewBeanParser.getViewTypeByClassName(rootManager.getLayoutByFileName(getProjectFile().getXmlName()).getClassName());
            } else {
                bean.parentType = ViewBean.VIEW_TYPE_LAYOUT_LINEAR; 
                for (ViewBean v : views) {
                    if (actualParentId.equals(v.id)) {
                        bean.parentType = v.type;
                        break;
                    }
                }
            }

            new ViewBeanFactory(bean).applyAttributes(normalizedAttrs);

            if (!snapshot) {
                dataManager.a(getProjectFile().getXmlName(), bean);
                dataManager.n(new yq(getContext(), getScId()).projectMyscPath + "view");
                fragment.refreshDesigner();
                return;
            }

            new MaterialAlertDialogBuilder(getContext())
                    .setTitle("Add View")
                    .setMessage("Add " + type + " '" + newId + "' to " + actualParentId + "?")
                    .setPositiveButton("Add", (dialog, which) -> {
                        fragment.undoSnapshot = new ProjectSnapshot(getScId(), getProjectFile().getXmlName());
                        dataManager.a(getProjectFile().getXmlName(), bean);
                        dataManager.n(new yq(getContext(), getScId()).projectMyscPath + "view");
                        fragment.addSystemMessage("View '" + newId + "' added.");
                        fragment.setUndoVisible(true);
                        fragment.refreshDesigner();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Exception e) {
            fragment.addSystemMessage("Error: " + e.getMessage());
        }
    }

    public void applyDeleteView(String viewId, boolean snapshot) {
        try {
            eC dataManager = jC.a(getScId());
            ArrayList<ViewBean> views = dataManager.d(getProjectFile().getXmlName());
            ViewBean target = null;
            for (ViewBean v : views) {
                if (viewId.equals(v.id)) {
                    target = v;
                    break;
                }
            }

            if (target == null) {
                fragment.addSystemMessage("View '" + viewId + "' not found.");
                return;
            }

            ViewBean finalTarget = target;
            if (!snapshot) {
                ArrayList<ViewBean> affected = dataManager.b(getProjectFile().getXmlName(), finalTarget);
                for (int i = affected.size() - 1; i >= 0; i--) {
                    dataManager.a(getProjectFile(), affected.get(i));
                }
                dataManager.n(new yq(getContext(), getScId()).projectMyscPath + "view");
                fragment.refreshDesigner();
                return;
            }

            new MaterialAlertDialogBuilder(getContext())
                    .setTitle("Delete View")
                    .setMessage("Delete view '" + viewId + "' and all its children?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        fragment.undoSnapshot = new ProjectSnapshot(getScId(), getProjectFile().getXmlName());
                        ArrayList<ViewBean> affected = dataManager.b(getProjectFile().getXmlName(), finalTarget);
                        for (int i = affected.size() - 1; i >= 0; i--) {
                            dataManager.a(getProjectFile(), affected.get(i));
                        }
                        dataManager.n(new yq(getContext(), getScId()).projectMyscPath + "view");
                        fragment.addSystemMessage("View '" + viewId + "' deleted.");
                        fragment.setUndoVisible(true);
                        fragment.refreshDesigner();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Exception e) {
            fragment.addSystemMessage("Error: " + e.getMessage());
        }
    }

    public void applyMoveView(String viewId, String newParentId, int newIndex, boolean snapshot) {
        try {
            eC dataManager = jC.a(getScId());
            ArrayList<ViewBean> views = dataManager.d(getProjectFile().getXmlName());
            ViewBean target = null;
            for (ViewBean v : views) {
                if (viewId.equals(v.id)) {
                    target = v;
                    break;
                }
            }

            if (target == null) {
                fragment.addSystemMessage("View '" + viewId + "' not found.");
                return;
            }

            // Check cycle (moving view into itself or descendants)
            if (isDescendantOrSelf(viewId, newParentId, views)) {
                SketchwareUtil.toastError("Error: Cannot move view '" + viewId + "' into itself or its descendants.");
                return;
            }

            if (!"root".equals(newParentId)) {
                boolean parentExists = false;
                for (ViewBean v : views) {
                    if (newParentId.equals(v.id)) {
                        parentExists = true;
                        break;
                    }
                }
                if (!parentExists) {
                    SketchwareUtil.toastError("Error: New parent '" + newParentId + "' not found");
                    return;
                }
            }

            ViewBean finalTarget = target;
            Runnable moveAction = () -> {
                fragment.undoSnapshot = new ProjectSnapshot(getScId(), getProjectFile().getXmlName());
                finalTarget.parent = newParentId;
                if ("root".equals(newParentId)) {
                    InjectRootLayoutManager rootManager = new InjectRootLayoutManager(getScId());
                    finalTarget.parentType = ViewBeanParser.getViewTypeByClassName(rootManager.getLayoutByFileName(getProjectFile().getXmlName()).getClassName());
                } else {
                    for (ViewBean v : views) {
                        if (newParentId.equals(v.id)) {
                            finalTarget.parentType = v.type;
                            break;
                        }
                    }
                }

                // Re-index siblings in old and new parents so indexes stay contiguous
                Map<String, List<ViewBean>> group = new HashMap<>();
                for (ViewBean v : views) {
                    String pKey = v.parent != null ? v.parent : "root";
                    group.computeIfAbsent(pKey, k -> new ArrayList<>()).add(v);
                }
                for (Map.Entry<String, List<ViewBean>> entry : group.entrySet()) {
                    List<ViewBean> siblings = entry.getValue();
                    for (int i = 0; i < siblings.size(); i++) {
                        siblings.get(i).index = i;
                    }
                }
                if (newIndex >= 0) {
                    List<ViewBean> newParentSiblings = group.get(newParentId);
                    if (newParentSiblings != null) {
                        newParentSiblings.remove(finalTarget);
                        int idx = Math.min(newIndex, newParentSiblings.size());
                        newParentSiblings.add(idx, finalTarget);
                        for (int i = 0; i < newParentSiblings.size(); i++) {
                            newParentSiblings.get(i).index = i;
                        }
                    }
                }

                dataManager.n(new yq(getContext(), getScId()).projectMyscPath + "view");
                fragment.addSystemMessage("View '" + viewId + "' moved.");
                fragment.setUndoVisible(true);
                fragment.refreshDesigner();
            };

            if (!snapshot) {
                moveAction.run();
                return;
            }

            new MaterialAlertDialogBuilder(getContext())
                    .setTitle("Move View")
                    .setMessage("Move '" + viewId + "' to parent '" + newParentId + "' at index " + newIndex + "?")
                    .setPositiveButton("Move", (dialog, which) -> moveAction.run())
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Exception e) {
            fragment.addSystemMessage("Error moving view: " + e.getMessage());
        }
    }

    private boolean isDescendantOrSelf(String targetId, String potentialParentId, ArrayList<ViewBean> views) {
        if (targetId.equals(potentialParentId)) return true;
        String currParent = potentialParentId;
        while (!"root".equals(currParent) && currParent != null) {
            if (currParent.equals(targetId)) return true;
            ViewBean pBean = null;
            for (ViewBean v : views) {
                if (v.id != null && v.id.equals(currParent)) {
                    pBean = v;
                    break;
                }
            }
            if (pBean == null) break;
            currParent = pBean.parent;
        }
        return false;
    }

    public void applyCustomView(String name, String initialXml) {
        new MaterialAlertDialogBuilder(getContext())
                .setTitle("Create Custom View")
                .setMessage("Create '" + name + "'?" + (initialXml != null ? " with initial layout?" : ""))
                .setPositiveButton("Create", (dialog, which) -> {
                    String xmlName = getProjectFile().getXmlName();
                    fragment.undoSnapshot = new ProjectSnapshot(getScId(), xmlName);
                    hC fileManager = jC.b(getScId());

                    boolean exists = false;
                    for (ProjectFileBean cv : fileManager.d) {
                        if (cv.fileName.equals(name)) {
                            exists = true;
                            break;
                        }
                    }

                    if (!exists) {
                        fileManager.d.add(new ProjectFileBean(ProjectFileBean.PROJECT_FILE_TYPE_CUSTOM_VIEW, name));
                        fileManager.j();
                        fileManager.l();
                    }

                    try {
                        SketchwareXmlBridge.applyAiXmlToSketchware(getContext(), getScId(), name + ".xml", initialXml != null ? initialXml : "<LinearLayout android:layout_width=\"match_parent\" android:layout_height=\"match_parent\" android:orientation=\"vertical\"/>");
                        fragment.addSystemMessage("Custom View '" + name + "' " + (exists ? "updated" : "created") + ".");
                        fragment.setUndoVisible(true);
                        fragment.refreshDesigner();
                    } catch (Exception e) {
                        SketchwareUtil.toastError("Failed to create Custom View: " + e.getMessage());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String normalizeValue(String field, String value) {
        if (value == null) return null;
        String f = field.toLowerCase();
        if (f.contains("color") || f.equals("background")) {
            if (!value.startsWith("#") && !value.startsWith("@") && !value.startsWith("?")) {
                try {
                    int color = Color.parseColor(value);
                    return String.format("#%08X", color);
                } catch (Exception ignored) {}
            }
        }
        return value;
    }
}
