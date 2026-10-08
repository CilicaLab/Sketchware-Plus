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
                            "1. Identify the component Type and ID (name) from the request. and \n" +
                            "   - SharedPref, Firebase, Firebase Storage: the ID is the filename/path param. Never ask for a separate filename.\n" +
                            "   - FILE_PICKER: default mime type is 'image/*' if unspecified.\n" +
                            "2. Execute: only when BOTH Type and ID are clear, emit ADD_COMPONENT.\n\n" +
                            "Respond with JSON only:\n" +
                            "{\n" +
                            "  \"category\": \"COMPONENT_ARCHITECT\",\n" +
                            "  \"thought_process\": \"how you identified the component\",\n" +
                            "  \"summary\": \"confirmation of what was added\",\n" +
                            "  \"actions\": [{\"type\":\"ADD_COMPONENT\",\"componentType\":\"...\",\"id\":\"...\",\"params\":[...]}]\n" +
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
            applyAddComponent(action.getString("componentType"), action.getString("id"), params);
        }
    }

    public void applyAddComponent(String typeStr, String id, List<String> params) {
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

            final List<String> finalParams = new ArrayList<>(params);
            if (finalParams.isEmpty()) {
                if (typeStr.equalsIgnoreCase("SHAREDPREF") ||
                        typeStr.equalsIgnoreCase("FIREBASE") ||
                        typeStr.equalsIgnoreCase("FIREBASE_STORAGE")) {
                    finalParams.add(id);
                } else if (typeStr.equalsIgnoreCase("FILE_PICKER")) {
                    finalParams.add("image/*");
                }
            }

            eC dataManager = jC.a(getScId());
            String javaName = getProjectFile().getJavaName();

            new MaterialAlertDialogBuilder(getContext())
                    .setTitle("Add Component")
                    .setMessage("Add " + typeStr + " component with ID '" + id + "'?")
                    .setPositiveButton("Add", (dialog, which) -> {
                        fragment.undoSnapshot = new ProjectSnapshot(getScId(), getProjectFile().getXmlName());
                        if (finalParams.isEmpty()) {
                            dataManager.a(javaName, type, id);
                        } else {
                            dataManager.a(javaName, type, id, finalParams.get(0));
                        }
                        dataManager.k(); 
                        fragment.addSystemMessage("Component '" + id + "' (" + typeStr + ") added.");
                        fragment.setUndoVisible(true);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Exception e) {
            fragment.addSystemMessage("Error adding component: " + e.getMessage());
        }
    }

    public void applyAddComponent(int type, String id) {
        try {
            eC dataManager = jC.a(getScId());
            String javaName = getProjectFile().getJavaName();
            String typeName = ComponentBean.getComponentTypeName(type);

            fragment.undoSnapshot = new ProjectSnapshot(getScId(), getProjectFile().getXmlName());
            
            List<String> params = new ArrayList<>();
            if (type == ComponentBean.COMPONENT_TYPE_SHAREDPREF ||
                type == ComponentBean.COMPONENT_TYPE_FIREBASE ||
                type == ComponentBean.COMPONENT_TYPE_FIREBASE_STORAGE) {
                params.add(id);
            } else if (type == ComponentBean.COMPONENT_TYPE_FILE_PICKER) {
                params.add("image/*");
            }

            if (params.isEmpty()) {
                dataManager.a(javaName, type, id);
            } else {
                dataManager.a(javaName, type, id, params.get(0));
            }
            
            dataManager.k();

            fragment.addSystemMessage("Component '" + id + "' (" + typeName + ") added automatically.");
            fragment.setUndoVisible(true);
            
        } catch (Exception e) {
            fragment.addSystemMessage("Error adding component: " + e.getMessage());
        }
    }
}
