package sketchware.plus.utility;

import android.app.Activity;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;

import a.a.a.bB;
import a.a.a.eC;
import a.a.a.hC;
import a.a.a.jC;
import a.a.a.lC;
import a.a.a.yB;

public class ProjectViewImporter {

    public static void showImportDialog(Activity activity, String currentScId, Runnable onImportSuccess) {
        ArrayList<HashMap<String, Object>> projects = lC.a();
        ArrayList<HashMap<String, Object>> otherProjects = new ArrayList<>();

        for (HashMap<String, Object> proj : projects) {
            String scId = yB.c(proj, "sc_id");
            if (scId != null && !scId.equals(currentScId)) {
                otherProjects.add(proj);
            }
        }

        if (otherProjects.isEmpty()) {
            bB.a(activity.getApplicationContext(), "No other projects available to import views from.", bB.TOAST_NORMAL).show();
            return;
        }

        CharSequence[] projectTitles = new CharSequence[otherProjects.size()];
        for (int i = 0; i < otherProjects.size(); i++) {
            HashMap<String, Object> proj = otherProjects.get(i);
            String wsName = yB.c(proj, "my_ws_name");
            String appName = yB.c(proj, "my_app_name");
            String scId = yB.c(proj, "sc_id");
            String name = (wsName != null && !wsName.isEmpty()) ? wsName : appName;
            projectTitles[i] = name + " (ID: " + scId + ")";
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle("Select Project")
                .setItems(projectTitles, (dialog, which) -> {
                    HashMap<String, Object> selectedProj = otherProjects.get(which);
                    String sourceScId = yB.c(selectedProj, "sc_id");
                    showViewSelectionDialog(activity, currentScId, sourceScId, onImportSuccess);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void showViewSelectionDialog(Activity activity, String currentScId, String sourceScId, Runnable onImportSuccess) {
        hC sourceHc = jC.b(sourceScId);
        ArrayList<ProjectFileBean> activities = sourceHc.b();
        ArrayList<ProjectFileBean> customViews = sourceHc.c();

        ArrayList<ProjectFileBean> allSourceFiles = new ArrayList<>();
        if (activities != null) {
            allSourceFiles.addAll(activities);
        }
        if (customViews != null) {
            allSourceFiles.addAll(customViews);
        }

        if (allSourceFiles.isEmpty()) {
            bB.a(activity.getApplicationContext(), "Selected project has no views to import.", bB.TOAST_NORMAL).show();
            return;
        }

        CharSequence[] viewTitles = new CharSequence[allSourceFiles.size()];
        for (int i = 0; i < allSourceFiles.size(); i++) {
            ProjectFileBean fileBean = allSourceFiles.get(i);
            String typeLabel = (fileBean.fileType == ProjectFileBean.PROJECT_FILE_TYPE_CUSTOM_VIEW
                    || fileBean.fileType == ProjectFileBean.PROJECT_FILE_TYPE_DRAWER)
                    ? "Custom View" : "Activity";
            viewTitles[i] = fileBean.fileName + " [" + typeLabel + "]";
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle("Select View to Import")
                .setItems(viewTitles, (dialog, which) -> {
                    ProjectFileBean selectedSourceFile = allSourceFiles.get(which);
                    importView(activity, currentScId, sourceScId, selectedSourceFile, onImportSuccess);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void importView(Activity activity, String currentScId, String sourceScId, ProjectFileBean sourceFile, Runnable onImportSuccess) {
        hC targetHc = jC.b(currentScId);
        ArrayList<String> existingNames = getExistingScreenNames(targetHc);

        String newFileName = sourceFile.fileName;
        int count = 1;
        while (existingNames.contains(newFileName)) {
            newFileName = sourceFile.fileName + "_" + count;
            count++;
        }

        ProjectFileBean importedFile;
        if (sourceFile.fileType == ProjectFileBean.PROJECT_FILE_TYPE_CUSTOM_VIEW
                || sourceFile.fileType == ProjectFileBean.PROJECT_FILE_TYPE_DRAWER) {
            importedFile = new ProjectFileBean(ProjectFileBean.PROJECT_FILE_TYPE_CUSTOM_VIEW, newFileName);
        } else {
            importedFile = new ProjectFileBean(sourceFile.fileType, newFileName, sourceFile.orientation, sourceFile.keyboardSetting, sourceFile.options);
        }

        targetHc.a(importedFile);

        if (sourceFile.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_DRAWER)) {
            if (targetHc.b(importedFile.getDrawerXmlName()) == null) {
                targetHc.a(2, importedFile.getDrawerName());
            }
            ArrayList<ViewBean> sourceDrawerViews = jC.a(sourceScId).d(sourceFile.getDrawerXmlName());
            if (sourceDrawerViews != null && !sourceDrawerViews.isEmpty()) {
                ArrayList<ViewBean> clonedDrawerViews = eC.a(sourceDrawerViews);
                for (ViewBean view : clonedDrawerViews) {
                    jC.a(currentScId).a(importedFile.getDrawerXmlName(), view);
                }
            }
        }

        ArrayList<ViewBean> sourceViews = jC.a(sourceScId).d(sourceFile.getXmlName());
        if (sourceViews != null && !sourceViews.isEmpty()) {
            ArrayList<ViewBean> clonedViews = eC.a(sourceViews);
            for (ViewBean view : clonedViews) {
                jC.a(currentScId).a(importedFile.getXmlName(), view);
                if (view.type == ViewBean.VIEW_TYPE_WIDGET_BUTTON
                        && importedFile.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY) {
                    jC.a(currentScId).a(importedFile.getJavaName(), 1, view.type, view.id, "onClick");
                }
            }
        }

        jC.b(currentScId).j();
        jC.b(currentScId).l();
        jC.a(currentScId).k();

        bB.a(activity.getApplicationContext(), "Imported view '" + importedFile.fileName + "' successfully!", bB.TOAST_NORMAL).show();

        if (onImportSuccess != null) {
            onImportSuccess.run();
        }
    }

    private static ArrayList<String> getExistingScreenNames(hC targetHc) {
        ArrayList<String> screenNames = new ArrayList<>();
        ArrayList<ProjectFileBean> activities = targetHc.b();
        if (activities != null) {
            for (ProjectFileBean projectFileBean : activities) {
                screenNames.add(projectFileBean.fileName);
            }
        }
        ArrayList<ProjectFileBean> customViews = targetHc.c();
        if (customViews != null) {
            for (ProjectFileBean projectFileBean : customViews) {
                screenNames.add(projectFileBean.fileName);
            }
        }
        return screenNames;
    }
}
