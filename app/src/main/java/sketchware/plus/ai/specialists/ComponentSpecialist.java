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

            String systemPrompt = "COMPONENT EXPERT AGENT PROTOCOL:\n" +
                    "1. IDENTIFY: Determine the Component Type and ID (name) from the user's request and chat history.\n" +
                    "2. VALIDATE (STRICT):\n" +
                    "   - If the COMPONENT TYPE is missing or ambiguous, ask 'Which component would you like to add?'.\n" +
                    "   - If the ID (name) is missing, ask 'What name should I give to the [Type] component?'.\n" +
                    "   - For components like SharedPref, Firebase, and Firebase Storage, ALWAYS use the component ID (name) as the required parameter (filename/path). DO NOT ask the user for a separate filename.\n" +
                    "   - For FILE_PICKER, if the mime type is not specified, use 'image/*' as default.\n" +
                    "3. EXECUTE: Only if BOTH Type and ID are clearly specified, use ADD_COMPONENT.\n\n" +
                    "RESPONSE CONTRACT:\n" +
                    "{\n" +
                    "  \"category\": \"COMPONENT_ARCHITECT\",\n" +
                    "  \"thought_process\": \"Logic for identifying the component\",\n" +
                    "  \"summary\": \"Confirmation of adding the component\",\n" +
                    "  \"actions\": [ {\"type\":\"ADD_COMPONENT\",\"componentType\":\"...\",\"id\":\"...\",\"params\":[...] } ]\n" +
                    "}\n" +
                    "Important: params should contain the ID if it's a component that requires a filename/path.\n\n" +
                    "Classification Reasoning: " + reasoning;

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

            eC dataManager = jC.a(scId);
            String javaName = projectFile.getJavaName();

            new MaterialAlertDialogBuilder(getContext())
                    .setTitle("Add Component")
                    .setMessage("Add " + typeStr + " component with ID '" + id + "'?")
                    .setPositiveButton("Add", (dialog, which) -> {
                        fragment.undoSnapshot = new ProjectSnapshot(scId, projectFile.getXmlName());
                        if (finalParams.isEmpty()) {
                            dataManager.a(javaName, type, id);
                        } else {
                            dataManager.a(javaName, type, id, finalParams.get(0));
                        }
                        dataManager.k(); 
                        fragment.refreshDesigner();
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
            eC dataManager = jC.a(scId);
            String javaName = projectFile.getJavaName();
            String typeName = ComponentBean.getComponentTypeName(type);

            fragment.undoSnapshot = new ProjectSnapshot(scId, projectFile.getXmlName());
            
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
            fragment.refreshDesigner();
            fragment.addSystemMessage("Component '" + id + "' (" + typeName + ") added automatically.");
            fragment.setUndoVisible(true);
            
        } catch (Exception e) {
            fragment.addSystemMessage("Error adding component: " + e.getMessage());
        }
    }
}
