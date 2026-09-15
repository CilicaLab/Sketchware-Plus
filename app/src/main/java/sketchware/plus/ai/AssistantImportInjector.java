package sketchware.plus.ai;

import android.app.Activity;

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
 * imports directly into the user's active editor screen/activity data.
 */
public class AssistantImportInjector {

    /**
     * Injects one or more specific package imports into the currently active activity screen.
     * Keeps any existing imports safe and automatically cleans up duplicate entries.
     *
     * @param activeActivity The currently open Context/Activity (should be an instance of LogicEditorActivity).
     * @param importsToInject An array of fully qualified import declarations or package strings
     *                        (e.g., "java.util.HashMap" or "import android.util.Log;").
     * @return true if injection was fully successful and updated; false otherwise.
     */
    public static boolean injectImportsToCurrentActivity(Activity activeActivity, String[] importsToInject) {
        if (!(activeActivity instanceof LogicEditorActivity)) {
            return false;
        }

        try {
            LogicEditorActivity editor = (LogicEditorActivity) activeActivity;

            // Extract the Project ID (sc_id) and current screen metadata file bean
            String scId = editor.getIntent().getStringExtra("sc_id");
            ProjectFileBean currentFileBean = editor.M;

            if (scId == null || scId.isEmpty() || currentFileBean == null) {
                return false;
            }

            String javaName = currentFileBean.getJavaName();

            // Obtain the core Sketchware Project Screen Configuration Handler (eC) via jC
            eC screenConfigHandler = jC.a(scId);

            if (screenConfigHandler == null) {
                return false;
            }

            synchronized (screenConfigHandler) {
                // Fetch the existing list of manually declared imports for this activity screen
                // Sketchware internally tracks screen imports inside eC using the screen's javaName
                // Sketchware tracks blocks for specific events using dataManager.a(javaName, eventKey)
                // Let's inject a customImport block into the screen context under an Import activity block sequence
                screenConfigHandler.a(javaName, EventBean.EVENT_TYPE_ACTIVITY, 0, "", "Import");
                ArrayList<BlockBean> blocks = screenConfigHandler.a(javaName, "Import");
                if (blocks == null) {
                    blocks = new ArrayList<>();
                }

                boolean updated = false;

                for (String singleImport : importsToInject) {
                    if (singleImport == null || singleImport.trim().isEmpty()) {
                        continue;
                    }

                    String cleanedImport = singleImport.trim();
                    if (cleanedImport.startsWith("import ")) {
                        cleanedImport = cleanedImport.substring(7);
                    }
                    if (cleanedImport.endsWith(";")) {
                        cleanedImport = cleanedImport.substring(0, cleanedImport.length() - 1);
                    }
                    cleanedImport = cleanedImport.trim();

                    // Prevent duplicate blocks
                    boolean exists = false;
                    for (BlockBean b : blocks) {
                        if (("customImport".equals(b.opCode) || "customImport2".equals(b.opCode)) 
                                && b.parameters != null && b.parameters.contains(cleanedImport)) {
                            exists = true;
                            break;
                        }
                    }

                    if (!exists) {
                        BlockBean importBlock = new BlockBean("0", "none", " ", "customImport");
                        importBlock.parameters.add(cleanedImport);
                        blocks.add(importBlock);
                        updated = true;
                    }
                }

                if (updated) {
                    screenConfigHandler.k();

                    activeActivity.runOnUiThread(() -> {
                        try {
                            SketchwareUtil.toast("Assistant successfully added required imports!");
                        } catch (Exception ignored) {}
                    });
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}