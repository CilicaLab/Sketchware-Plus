package sketchware.plus.utility;

import android.app.Activity;
import android.net.Uri;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.adapters.ProjectsAdapter;
import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import a.a.a.bB;
import a.a.a.eC;
import a.a.a.hC;
import a.a.a.jC;
import a.a.a.lC;
import a.a.a.mB;
import a.a.a.wB;
import a.a.a.wq;
import a.a.a.yB;
import sketchware.plus.R;
import sketchware.plus.databinding.MyprojectsItemBinding;

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

        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(activity);
        dialogBuilder.setTitle("Select Project");

        RecyclerView recyclerView = new RecyclerView(activity);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        int padH = (int) wB.a(activity, 16f);
        recyclerView.setPadding(padH, 0, padH, 0);
        recyclerView.setClipToPadding(true);
        recyclerView.setVerticalFadingEdgeEnabled(true);
        recyclerView.setFadingEdgeLength((int) wB.a(activity, 32f));

        androidx.appcompat.app.AlertDialog dialog = dialogBuilder.setView(recyclerView).create();

        ProjectListAdapter adapter = new ProjectListAdapter(activity, otherProjects, project -> {
            dialog.dismiss();
            String sourceScId = yB.c(project, "sc_id");
            showViewSelectionDialog(activity, currentScId, sourceScId, onImportSuccess);
        });
        recyclerView.setAdapter(adapter);

        dialog.show();
    }

    private static class ProjectListAdapter extends RecyclerView.Adapter<ProjectListAdapter.ViewHolder> {
        private final Activity activity;
        private final ArrayList<HashMap<String, Object>> projects;
        private final OnProjectSelectedListener listener;

        public interface OnProjectSelectedListener {
            void onSelected(HashMap<String, Object> project);
        }

        public ProjectListAdapter(Activity activity, ArrayList<HashMap<String, Object>> projects, OnProjectSelectedListener listener) {
            this.activity = activity;
            this.projects = projects;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MyprojectsItemBinding binding = MyprojectsItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.itemView.setBackgroundResource(ProjectsAdapter.getShapedBackgroundForList(projects, position));
            HashMap<String, Object> projectMap = projects.get(position);
            String scId = yB.c(projectMap, "sc_id");

            holder.binding.imgIcon.setImageResource(R.drawable.default_icon);

            if (yB.a(projectMap, "custom_icon")) {
                String iconFolder = wq.e() + File.separator + scId;
                File iconFile = new File(iconFolder, "icon.png");
                if (iconFile.exists()) {
                    try {
                        String providerPath = activity.getPackageName() + ".provider";
                        Uri uri = FileProvider.getUriForFile(activity, providerPath, iconFile);
                        holder.binding.imgIcon.setImageURI(uri);
                    } catch (Exception e) {
                        holder.binding.imgIcon.setImageResource(R.drawable.default_icon);
                    }
                }
            }

            String version = " - " + yB.c(projectMap, "sc_ver_name") + " (" + yB.c(projectMap, "sc_ver_code") + ")";
            holder.binding.appName.setText(yB.c(projectMap, "my_ws_name") + version);
            holder.binding.projectName.setText(yB.c(projectMap, "my_app_name"));
            holder.binding.packageName.setText(yB.c(projectMap, "my_sc_pkg_name"));
            holder.binding.tvPublished.setVisibility(View.VISIBLE);
            holder.binding.tvPublished.setText(scId);
            holder.binding.expand.setVisibility(View.GONE);

            holder.itemView.setOnClickListener(v -> {
                if (!mB.a()) {
                    listener.onSelected(projectMap);
                }
            });
        }

        @Override
        public int getItemCount() {
            return projects.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final MyprojectsItemBinding binding;

            public ViewHolder(@NonNull MyprojectsItemBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    private static void showViewSelectionDialog(Activity activity, String currentScId, String sourceScId, Runnable onImportSuccess) {
        hC sourceHc = new hC(sourceScId);
        sourceHc.i();
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

        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(activity);
        dialogBuilder.setTitle("Select View to Import");

        RecyclerView recyclerView = new RecyclerView(activity);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        int padH = (int) wB.a(activity, 16f);
        recyclerView.setPadding(padH, 0, padH, 0);
        recyclerView.setClipToPadding(true);
        recyclerView.setVerticalFadingEdgeEnabled(true);
        recyclerView.setFadingEdgeLength((int) wB.a(activity, 32f));

        androidx.appcompat.app.AlertDialog dialog = dialogBuilder.setView(recyclerView).create();

        ViewListAdapter adapter = new ViewListAdapter(allSourceFiles, fileBean -> {
            dialog.dismiss();
            importView(activity, currentScId, sourceScId, fileBean, onImportSuccess);
        });
        recyclerView.setAdapter(adapter);

        dialog.show();
    }

    private static class ViewListAdapter extends RecyclerView.Adapter<ViewListAdapter.ViewHolder> {
        private final ArrayList<ProjectFileBean> viewFiles;
        private final OnViewSelectedListener listener;

        public interface OnViewSelectedListener {
            void onSelected(ProjectFileBean fileBean);
        }

        public ViewListAdapter(ArrayList<ProjectFileBean> viewFiles, OnViewSelectedListener listener) {
            this.viewFiles = viewFiles;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MyprojectsItemBinding binding = MyprojectsItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.itemView.setBackgroundResource(ProjectsAdapter.getShapedBackgroundForList(viewFiles, position));
            ProjectFileBean fileBean = viewFiles.get(position);

            boolean isCustom = (fileBean.fileType == ProjectFileBean.PROJECT_FILE_TYPE_CUSTOM_VIEW
                    || fileBean.fileType == ProjectFileBean.PROJECT_FILE_TYPE_DRAWER);
            String typeLabel = isCustom ? "Custom View" : "Activity";

            holder.binding.imgIcon.setImageResource(isCustom ? R.drawable.ic_mtrl_view_horizontal : R.drawable.sketch_app_icon);
            holder.binding.appName.setText(fileBean.fileName);
            holder.binding.projectName.setText(typeLabel);
            holder.binding.packageName.setText(fileBean.getXmlName());
            holder.binding.tvPublished.setVisibility(View.GONE);
            holder.binding.expand.setVisibility(View.GONE);

            holder.itemView.setOnClickListener(v -> {
                if (!mB.a()) {
                    listener.onSelected(fileBean);
                }
            });
        }

        @Override
        public int getItemCount() {
            return viewFiles.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final MyprojectsItemBinding binding;

            public ViewHolder(@NonNull MyprojectsItemBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    private static void importView(Activity activity, String currentScId, String sourceScId, ProjectFileBean sourceFile, Runnable onImportSuccess) {
        hC targetFileMgr = jC.b(currentScId);
        ArrayList<String> existingNames = getExistingScreenNames(targetFileMgr);

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

        targetFileMgr.a(importedFile);

        // Initialize source file manager and data manager independently
        hC sourceFileMgr = new hC(sourceScId);
        sourceFileMgr.i();

        eC sourceData = new eC(sourceScId);
        sourceData.g();
        sourceData.e();

        eC targetData = jC.a(currentScId);

        // 1. Copy Layout Widgets
        ArrayList<ViewBean> sourceViews = sourceData.d(sourceFile.getXmlName());
        if (sourceViews != null && !sourceViews.isEmpty()) {
            ArrayList<ViewBean> clonedViews = eC.a(sourceViews);
            for (ViewBean view : clonedViews) {
                targetData.a(importedFile.getXmlName(), view);
                if (view.type == ViewBean.VIEW_TYPE_WIDGET_BUTTON
                        && importedFile.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY) {
                    targetData.a(importedFile.getJavaName(), 1, view.type, view.id, "onClick");
                }
            }
        }

        // 2. Copy Logic Assets (Variables, Lists, Components, More Blocks, Events & Blocks)
        copyLogicAssets(sourceData, sourceFile.getJavaName(), targetData, importedFile.getJavaName());

        // 3. Handle Navigation Drawer if present
        if (sourceFile.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_DRAWER)) {
            if (targetFileMgr.b(importedFile.getDrawerXmlName()) == null) {
                targetFileMgr.a(2, importedFile.getDrawerName());
            }
            ArrayList<ViewBean> sourceDrawerViews = sourceData.d(sourceFile.getDrawerXmlName());
            if (sourceDrawerViews != null && !sourceDrawerViews.isEmpty()) {
                ArrayList<ViewBean> clonedDrawerViews = eC.a(sourceDrawerViews);
                for (ViewBean view : clonedDrawerViews) {
                    targetData.a(importedFile.getDrawerXmlName(), view);
                }
            }
            copyLogicAssets(sourceData, sourceFile.getDrawerXmlName(), targetData, importedFile.getDrawerXmlName());
        }

        targetFileMgr.j();
        targetFileMgr.l();
        targetData.k();

        bB.a(activity.getApplicationContext(), "Imported view '" + importedFile.fileName + "' with logic successfully!", bB.TOAST_NORMAL).show();

        if (onImportSuccess != null) {
            onImportSuccess.run();
        }
    }

    private static void copyLogicAssets(eC sourceData, String sourceJavaName, eC targetData, String targetJavaName) {
        if (sourceJavaName == null || sourceJavaName.isEmpty() || targetJavaName == null || targetJavaName.isEmpty()) {
            return;
        }

        // 1. Copy Variables
        ArrayList<Pair<Integer, String>> sourceVars = sourceData.j(sourceJavaName);
        if (sourceVars != null) {
            for (Pair<Integer, String> var : sourceVars) {
                targetData.c(targetJavaName, var.first, var.second);
            }
        }

        // 2. Copy Lists
        ArrayList<Pair<Integer, String>> sourceLists = sourceData.k(sourceJavaName);
        if (sourceLists != null) {
            for (Pair<Integer, String> list : sourceLists) {
                targetData.b(targetJavaName, list.first, list.second);
            }
        }

        // 3. Copy Components
        ArrayList<ComponentBean> sourceComponents = sourceData.e(sourceJavaName);
        if (sourceComponents != null) {
            for (ComponentBean comp : sourceComponents) {
                ComponentBean clonedComp = new ComponentBean(comp.type, comp.componentId, comp.param1, comp.param2, comp.param3);
                targetData.a(targetJavaName, clonedComp);
            }
        }

        // 4. Copy More Blocks
        ArrayList<Pair<String, String>> sourceMoreBlocks = sourceData.i(sourceJavaName);
        if (sourceMoreBlocks != null) {
            for (Pair<String, String> mb : sourceMoreBlocks) {
                String mbName = mb.first;
                String spec = mb.second;
                targetData.a(targetJavaName, mbName, spec);

                String mbKey = mbName + "_moreBlock";
                ArrayList<BlockBean> mbBlocks = sourceData.a(sourceJavaName, mbKey);
                if (mbBlocks != null && !mbBlocks.isEmpty()) {
                    targetData.a(targetJavaName, mbKey, cloneBlocks(mbBlocks));
                }
            }
        }

        // 5. Copy Events
        ArrayList<EventBean> sourceEvents = sourceData.g(sourceJavaName);
        if (sourceEvents != null) {
            for (EventBean event : sourceEvents) {
                EventBean clonedEvent = new EventBean(event.eventType, event.targetType, event.targetId, event.eventName);
                targetData.a(targetJavaName, clonedEvent);
            }
        }

        // 6. Copy Event Logic Blocks
        HashMap<String, ArrayList<BlockBean>> sourceBlockMap = sourceData.b(sourceJavaName);
        if (sourceBlockMap != null) {
            for (Map.Entry<String, ArrayList<BlockBean>> entry : sourceBlockMap.entrySet()) {
                String eventKey = entry.getKey();
                ArrayList<BlockBean> blocks = entry.getValue();
                if (blocks != null && !blocks.isEmpty()) {
                    targetData.a(targetJavaName, eventKey, cloneBlocks(blocks));
                }
            }
        }
    }

    private static ArrayList<BlockBean> cloneBlocks(ArrayList<BlockBean> sourceBlocks) {
        if (sourceBlocks == null) return new ArrayList<>();
        ArrayList<BlockBean> cloned = new ArrayList<>();
        for (BlockBean source : sourceBlocks) {
            BlockBean block = new BlockBean();
            block.copy(source);
            cloned.add(block);
        }
        return cloned;
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
