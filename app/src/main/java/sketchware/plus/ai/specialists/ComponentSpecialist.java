package sketchware.plus.ai.specialists;

import android.app.Activity;
import android.content.Context;

import com.besome.sketch.beans.ComponentBean;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import a.a.a.eC;
import a.a.a.jC;
import com.besome.sketch.design.DesignActivity;
import sketchware.plus.ai.ProjectSnapshot;
import sketchware.plus.ai.SkAssistantFragment;
import mod.hilal.saif.components.ComponentsHandler;

public class ComponentSpecialist extends BaseSpecialist {

    public ComponentSpecialist(SkAssistantFragment fragment) {
        super(fragment);
    }

    @Override
    public void process(String prompt, String reasoning) {
        log("[ComponentSpecialist.process] ENTRY - prompt: " + prompt + ", reasoning: " + reasoning);
        setStatus("Checking component requirements...");
        Context androidContext = getContext();
        if (androidContext == null) return;

        jC.projectOperationsExecutor.execute(() -> {
            try { Thread.sleep(1000); } catch (Exception ignored) {}
            String contextStr = fragment.gatherScopedContext(androidContext, "COMPONENT_ARCHITECT", prompt);
            String systemPrompt =
                    "COMPONENT EXPERT AGENT\n" +
                            "1. Identify the component Type, ID (name prefix), and optional Count from the request.\n" +
                            "   - SharedPref, Firebase, Firebase Storage: the ID is the filename/path param. Never ask for a separate filename.\n" +
                            "   - FILE_PICKER: default mime type is 'image/*' if unspecified.\n" +
                            "2. Execute: emit ADD_COMPONENT with componentType, id, params, and optional count.\n\n" +
                            "Respond with JSON only:\n" +
                            "{\n" +
                            "  \"category\": \"COMPONENT_ARCHITECT\",\n" +
                            "  \"thought_process\": \"how you identified the component\",\n" +
                            "  \"summary\": \"confirmation of what was added\",\n" +
                            "  \"actions\": [{\"type\":\"ADD_COMPONENT\",\"componentType\":\"...\",\"id\":\"...\",\"count\":1,\"params\":[...]}]\n" +
                            "}\n\n" +
                            "Classification reasoning: " + reasoning;

            Activity activity = fragment.getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> fragment.executeRequest(systemPrompt, prompt, contextStr, "COMPONENT_ARCHITECT"));
            }
        });
    }

    @Override
    public void handleAction(JSONObject action) throws JSONException {
        log("[ComponentSpecialist.handleAction] ENTRY - action: " + action);
        String normType = fragment.normalizeActionType(action.optString("type"));
        if ("ADD_COMPONENT".equals(normType)) {
            List<String> params = new ArrayList<>();
            JSONArray jParams = action.optJSONArray("params");
            if (jParams != null) {
                for (int j = 0; j < jParams.length(); j++) params.add(jParams.getString(j));
            }
            int count = action.optInt("count", 1);
            String typeStr = action.optString("componentType", action.optString("type", ""));
            String id = action.optString("id", null);
            applyAddComponent(typeStr, id, params, count);
        } else if ("REMOVE_COMPONENT".equals(normType) || "DELETE_COMPONENT".equals(normType)) {
            String componentId = action.optString("componentId", action.optString("id", null));
            String javaName = action.optString("javaName", null);
            applyRemoveComponent(javaName, componentId);
        }
    }

    public void applyAddComponent(String typeStr, String id, List<String> params) {
        applyAddComponent(typeStr, id, params, 1);
    }

    public void applyAddComponent(String typeStr, String id, List<String> params, int count) {
        log("[ComponentSpecialist.applyAddComponent] ENTRY - typeStr: " + typeStr + ", id: " + id + ", count: " + count);
        try {
            int typeVal = -1;
            try {
                Field field = ComponentBean.class.getField("COMPONENT_TYPE_" + typeStr.toUpperCase());
                typeVal = field.getInt(null);
            } catch (Exception e) {
                typeVal = ComponentsHandler.id(typeStr);
            }

            if (typeVal == -1 || !isValidComponentType(typeVal)) {
                log("[ComponentSpecialist.applyAddComponent] ERROR: Unknown or non-existent component type: " + typeStr);
                fragment.addSystemMessage("Unknown or non-existent component type: " + typeStr + ". Use list_components to view valid types.");
                return;
            }
            final int type = typeVal;
            final int actualCount = Math.max(1, Math.min(count, 1000));

            String message = (actualCount == 1)
                    ? "Add " + typeStr + " component with ID '" + id + "'?"
                    : "Add " + actualCount + " " + typeStr + " components with base ID '" + id + "'?";

            new MaterialAlertDialogBuilder(getContext())
                    .setTitle("Add Component(s)")
                    .setMessage(message)
                    .setPositiveButton("Add", (dialog, which) -> {
                        log("[ComponentSpecialist.applyAddComponent] User confirmed adding component(s)");
                        fragment.undoSnapshot = new ProjectSnapshot(getScId(), getProjectFile().getXmlName());
                        applyBatchAddComponent(type, id, actualCount, params);
                    })
                    .setNegativeButton("Cancel", (d, w) -> log("[ComponentSpecialist.applyAddComponent] User canceled adding component(s)"))
                    .show();
        } catch (Exception e) {
            log("[ComponentSpecialist.applyAddComponent] ERROR: " + e.getMessage());
            fragment.addSystemMessage("Error adding component: " + e.getMessage());
        }
    }

    public void applyAddComponent(int type, String id) {
        applyBatchAddComponent(type, id, 1, null);
    }

    public List<String> applyBatchAddComponent(int type, String baseId, int count) {
        return applyBatchAddComponent(null, type, baseId, count, null);
    }

    public List<String> applyBatchAddComponent(int type, String baseId, int count, List<String> customParams) {
        return applyBatchAddComponent(null, type, baseId, count, customParams);
    }

    public static boolean isValidComponentType(int type) {
        if (type <= 0) return false;
        try {
            String name = ComponentBean.getComponentTypeName(type);
            return name != null && !name.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    public List<String> applyBatchAddComponent(String rawJavaName, int type, String baseId, int count, List<String> customParams) {
        log("[ComponentSpecialist.applyBatchAddComponent] ENTRY - javaName: " + rawJavaName + ", type: " + type + ", baseId: " + baseId + ", count: " + count);
        String defaultJava = getProjectFile() != null ? getProjectFile().getJavaName() : "MainActivity.java";
        String javaName = sketchware.plus.ai.ProjectStructureAide.findActivityJavaName(getScId(), rawJavaName, defaultJava);
        if (javaName == null) javaName = defaultJava;
        if (baseId == null || baseId.trim().isEmpty()) {
            String typeName = ComponentBean.getComponentTypeName(type);
            baseId = typeName != null && !typeName.isEmpty() ? typeName.toLowerCase().replaceAll("[^a-zA-Z0-9]", "") : "comp";
        }
        List<String> addedIds = new ArrayList<>();
        if (!isValidComponentType(type)) {
            String errMsg = "Error: Component type ID " + type + " does not exist. Use list_components to check valid component types.";
            log("[ComponentSpecialist.applyBatchAddComponent] " + errMsg);
            fragment.addSystemMessage(errMsg);
            return addedIds;
        }
        try {
            eC dataManager = jC.a(getScId());
            String typeName = ComponentBean.getComponentTypeName(type);

            fragment.undoSnapshot = new ProjectSnapshot(getScId(), getProjectFile() != null ? getProjectFile().getXmlName() : "main.xml");

            int actualCount = Math.max(1, Math.min(count, 1000));

            for (int i = 0; i < actualCount; i++) {
                String requestedId = (actualCount == 1) ? baseId : (baseId + (i + 1));
                String resolvedId = resolveUniqueComponentId(getScId(), javaName, requestedId);

                List<String> params = new ArrayList<>();
                if (customParams != null && i < customParams.size() && customParams.get(i) != null && !customParams.get(i).isEmpty()) {
                    params.add(customParams.get(i));
                } else if (customParams != null && !customParams.isEmpty() && customParams.get(0) != null && !customParams.get(0).isEmpty()) {
                    params.add(customParams.get(0));
                } else {
                    if (type == ComponentBean.COMPONENT_TYPE_SHAREDPREF ||
                            type == ComponentBean.COMPONENT_TYPE_FIREBASE ||
                            type == ComponentBean.COMPONENT_TYPE_FIREBASE_STORAGE) {
                        params.add(resolvedId);
                    } else if (type == ComponentBean.COMPONENT_TYPE_FILE_PICKER) {
                        params.add("image/*");
                    }
                }

                if (params.isEmpty()) {
                    dataManager.a(javaName, type, resolvedId);
                } else {
                    dataManager.a(javaName, type, resolvedId, params.get(0));
                }
                addedIds.add(resolvedId);
            }

            dataManager.k();

            Activity activity = fragment.getActivity();
            if (activity instanceof DesignActivity da) {
                da.refreshComponents();
            }

            if (actualCount == 1) {
                fragment.addSystemMessage("Component '" + addedIds.get(0) + "' (" + typeName + ") added to " + javaName + ".");
            } else {
                fragment.addSystemMessage("Added " + actualCount + " " + typeName + " components ('" + addedIds.get(0) + "' to '" + addedIds.get(addedIds.size() - 1) + "') to " + javaName + ".");
            }
            fragment.setUndoVisible(true);
        } catch (Exception e) {
            fragment.addSystemMessage("Error adding components: " + e.getMessage());
        }
        return addedIds;
    }

    public boolean applyRemoveComponent(String rawJavaName, String componentId) {
        log("[ComponentSpecialist.applyRemoveComponent] ENTRY - javaName: " + rawJavaName + ", componentId: " + componentId);
        if (componentId == null || componentId.trim().isEmpty()) {
            log("[ComponentSpecialist.applyRemoveComponent] ERROR: Empty componentId");
            return false;
        }
        String defaultJava = getProjectFile() != null ? getProjectFile().getJavaName() : "MainActivity.java";
        String javaName = sketchware.plus.ai.ProjectStructureAide.findActivityJavaName(getScId(), rawJavaName, defaultJava);
        if (javaName == null) javaName = defaultJava;

        try {
            eC dataManager = jC.a(getScId());
            if (dataManager == null) return false;

            ArrayList<ComponentBean> existing = dataManager.e(javaName);
            if (existing == null || existing.isEmpty()) {
                log("[ComponentSpecialist.applyRemoveComponent] No components in " + javaName);
                return false;
            }

            ComponentBean toRemove = null;
            for (ComponentBean cb : existing) {
                if (cb != null && componentId.equalsIgnoreCase(cb.componentId)) {
                    toRemove = cb;
                    break;
                }
            }

            if (toRemove != null) {
                fragment.undoSnapshot = new ProjectSnapshot(getScId(), getProjectFile() != null ? getProjectFile().getXmlName() : "main.xml");
                dataManager.b(javaName, toRemove);
                dataManager.k();

                Activity activity = fragment.getActivity();
                if (activity instanceof DesignActivity da) {
                    da.refreshComponents();
                }

                fragment.addSystemMessage("Component '" + toRemove.componentId + "' removed from " + javaName + ".");
                fragment.setUndoVisible(true);
                return true;
            } else {
                log("[ComponentSpecialist.applyRemoveComponent] Component '" + componentId + "' not found in " + javaName);
                return false;
            }
        } catch (Exception e) {
            log("[ComponentSpecialist.applyRemoveComponent] ERROR: " + e.getMessage());
            fragment.addSystemMessage("Error removing component: " + e.getMessage());
            return false;
        }
    }

    public static String resolveUniqueComponentId(String scId, String javaName, String requestedId) {
        if (scId == null || javaName == null || requestedId == null) return requestedId;
        eC dataManager = jC.a(scId);
        if (dataManager == null) return requestedId;

        ArrayList<ComponentBean> existing = dataManager.e(javaName);
        if (existing == null || existing.isEmpty()) return requestedId;

        boolean exists = false;
        for (ComponentBean cb : existing) {
            if (cb != null && requestedId.equals(cb.componentId)) {
                exists = true;
                break;
            }
        }

        if (!exists) return requestedId;

        int counter = 2;
        String newId;
        while (true) {
            newId = requestedId + "_" + counter;
            boolean collision = false;
            for (ComponentBean cb : existing) {
                if (cb != null && newId.equals(cb.componentId)) {
                    collision = true;
                    break;
                }
            }
            if (!collision) {
                return newId;
            }
            counter++;
        }
    }
}
