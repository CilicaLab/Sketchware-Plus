package sketchware.plus.ai;

import android.app.Activity;
import android.content.Intent;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.editor.LogicEditorActivity;

import java.util.ArrayList;

import a.a.a.eC;
import a.a.a.jC;
import sketchware.plus.utility.SketchwareUtil;

/**
 * A utility designed to assist the AI assistant in seamlessly injecting
 * imports directly into the user's active screen/activity data.
 */
public class AssistantImportInjector {

    /**
     * Injects one or more package imports into the specified project screen data.
     * Uses the same block structure, cleaning, and linking logic as the Component Manager (br.java).
     *
     * @param scId Project ID (sc_id). If null, attempts to resolve from hostActivity.
     * @param javaName Target activity Java name (e.g. "main" or "MainActivity.java"). If null, attempts to resolve from hostActivity.
     * @param importsToInject An array of import declarations or package strings (e.g., "java.util.List" or "import android.util.Log;").
     * @param hostActivity Optional host activity for showing UI feedback toast.
     * @return true if one or more imports were newly added; false if already existing or on failure.
     */
    public static boolean injectImports(String scId, String javaName, String[] importsToInject, Activity hostActivity) {
        if (importsToInject == null || importsToInject.length == 0) {
            return false;
        }

        // Fallback scId and javaName resolution if not explicitly passed
        if (scId == null || scId.isEmpty() || javaName == null || javaName.isEmpty()) {
            if (hostActivity instanceof LogicEditorActivity) {
                LogicEditorActivity editor = (LogicEditorActivity) hostActivity;
                if (scId == null || scId.isEmpty()) {
                    scId = editor.getIntent().getStringExtra("sc_id");
                }
                if (javaName == null || javaName.isEmpty()) {
                    ProjectFileBean fileBean = editor.M;
                    if (fileBean != null) {
                        javaName = fileBean.getJavaName();
                    }
                }
            } else if (hostActivity != null) {
                Intent intent = hostActivity.getIntent();
                if (intent != null && (scId == null || scId.isEmpty())) {
                    scId = intent.getStringExtra("sc_id");
                }
            }
        }

        if (scId == null || scId.isEmpty() || javaName == null || javaName.isEmpty()) {
            return false;
        }

        eC screenConfigHandler = jC.a(scId);
        if (screenConfigHandler == null) {
            return false;
        }

        synchronized (screenConfigHandler) {
            EventBean importEvent = null;
            for (EventBean event : screenConfigHandler.g(javaName)) {
                if (event.eventType == EventBean.EVENT_TYPE_ACTIVITY && "Import".equals(event.eventName)) {
                    importEvent = event;
                    break;
                }
            }
            if (importEvent == null) {
                EventBean eventBean = new EventBean(EventBean.EVENT_TYPE_ACTIVITY, 0, "Import", "Import");
                screenConfigHandler.a(javaName, eventBean);
                importEvent = eventBean;
            }

            String eventKey = importEvent.getEventKey();
            ArrayList<BlockBean> blocks = screenConfigHandler.a(javaName, eventKey);
            if (blocks == null) {
                blocks = new ArrayList<>();
            }

            boolean updated = false;

            for (String singleImport : importsToInject) {
                if (singleImport == null || singleImport.trim().isEmpty()) {
                    continue;
                }

                // Clean the import line matching br.java / custom import logic
                String cleanedImport = singleImport.trim();
                if (cleanedImport.startsWith("import ")) {
                    cleanedImport = cleanedImport.substring(7);
                }
                if (cleanedImport.endsWith(";")) {
                    cleanedImport = cleanedImport.substring(0, cleanedImport.length() - 1);
                }
                cleanedImport = cleanedImport.trim();

                if (cleanedImport.isEmpty()) {
                    continue;
                }

                // Check for existing custom or create import blocks
                boolean exists = false;
                for (BlockBean b : blocks) {
                    if (("customImport".equals(b.opCode) || "customImport2".equals(b.opCode) || "createImport".equals(b.opCode))
                            && b.parameters != null && b.parameters.contains(cleanedImport)) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    // Calculate next available ID to guarantee uniqueness
                    int maxId = 0;
                    for (BlockBean b : blocks) {
                        try {
                            int bId = Integer.parseInt(b.id);
                            if (bId > maxId) {
                                maxId = bId;
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                    int idCounter = maxId + 1;

                    BlockBean importBlock = new BlockBean(String.valueOf(idCounter), "import %s.import", " ", "", "customImport");
                    importBlock.parameters.add(cleanedImport);
                    importBlock.nextBlock = -1;
                    importBlock.subStack1 = -1;
                    importBlock.subStack2 = -1;

                    if (!blocks.isEmpty()) {
                        BlockBean lastBlock = blocks.get(blocks.size() - 1);
                        lastBlock.nextBlock = idCounter;
                    }

                    blocks.add(importBlock);
                    updated = true;
                }
            }

            if (updated) {
                screenConfigHandler.a(javaName, eventKey, blocks);
                screenConfigHandler.k();

                if (hostActivity != null) {
                    hostActivity.runOnUiThread(() -> {
                        try {
                            SketchwareUtil.toast("Assistant successfully added required imports!");
                        } catch (Exception ignored) {}
                    });
                }
                return true;
            }
        }
        return false;
    }

    public static boolean injectImportsToCurrentActivity(Activity activeActivity, String[] importsToInject) {
        return injectImports(null, null, importsToInject, activeActivity);
    }

    public static boolean injectImports(String scId, String javaName, String[] importsToInject) {
        return injectImports(scId, javaName, importsToInject, null);
    }
}
