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
import sketchware.plus.ai.ProjectSnapshot;
import sketchware.plus.ai.SkAssistantFragment;
import mod.hilal.saif.components.ComponentsHandler;

public class ComponentSpecialist extends BaseSpecialist {

    public ComponentSpecialist(SkAssistantFragment fragment) {
        super(fragment);
    }

    @Override
    public void process(String prompt, String reasoning) {
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
        if ("ADD_COMPONENT".equals(action.optString("type"))) {
            List<String> params = new ArrayList<>();
            JSONArray jParams = action.optJSONArray("params");
            if (jParams != null) {
                for (int j = 0; j < jParams.length(); j++) params.add(jParams.getString(j));
            }
            int count = action.optInt("count", 1);
            applyAddComponent(action.getString("componentType"), action.getString("id"), params, count);
        }
    }

    public void applyAddComponent(String typeStr, String id, List<String> params) {
        applyAddComponent(typeStr, id, params, 1);
    }

    public void applyAddComponent(String typeStr, String id, List<String> params, int count) {
        try {
            int typeVal = -1;
            try {
                Field field = ComponentBean.class.getField("COMPONENT_TYPE_" + typeStr.toUpperCase());
                typeVal = field.getInt(null);
            } catch (Exception e) {
                typeVal = ComponentsHandler.id(typeStr);
            }

            if (typeVal == -1) {
                fragment.addSystemMessage("Unknown component type: " + typeStr);
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
                        fragment.undoSnapshot = new ProjectSnapshot(getScId(), getProjectFile().getXmlName());
                        applyBatchAddComponent(type, id, actualCount);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Exception e) {
            fragment.addSystemMessage("Error adding component: " + e.getMessage());
        }
    }

    public void applyAddComponent(int type, String id) {
        applyBatchAddComponent(type, id, 1);
    }

    public int applyBatchAddComponent(int type, String baseId, int count) {
        try {
            eC dataManager = jC.a(getScId());
            String javaName = getProjectFile().getJavaName();
            String typeName = ComponentBean.getComponentTypeName(type);

            fragment.undoSnapshot = new ProjectSnapshot(getScId(), getProjectFile().getXmlName());

            List<String> addedIds = new ArrayList<>();
            int actualCount = Math.max(1, Math.min(count, 1000));

            for (int i = 0; i < actualCount; i++) {
                String requestedId = (actualCount == 1) ? baseId : (baseId + (i + 1));
                String resolvedId = resolveUniqueComponentId(getScId(), javaName, requestedId);

                List<String> params = new ArrayList<>();
                if (type == ComponentBean.COMPONENT_TYPE_SHAREDPREF ||
                        type == ComponentBean.COMPONENT_TYPE_FIREBASE ||
                        type == ComponentBean.COMPONENT_TYPE_FIREBASE_STORAGE) {
                    params.add(resolvedId);
                } else if (type == ComponentBean.COMPONENT_TYPE_FILE_PICKER) {
                    params.add("image/*");
                }

                if (params.isEmpty()) {
                    dataManager.a(javaName, type, resolvedId);
                } else {
                    dataManager.a(javaName, type, resolvedId, params.get(0));
                }
                addedIds.add(resolvedId);
            }

            dataManager.k();

            if (actualCount == 1) {
                fragment.addSystemMessage("Component '" + addedIds.get(0) + "' (" + typeName + ") added.");
            } else {
                fragment.addSystemMessage("Added " + actualCount + " " + typeName + " components ('" + addedIds.get(0) + "' to '" + addedIds.get(addedIds.size() - 1) + "').");
            }
            fragment.setUndoVisible(true);
            return actualCount;
        } catch (Exception e) {
            fragment.addSystemMessage("Error adding components: " + e.getMessage());
            return 0;
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
