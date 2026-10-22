package sketchware.plus.ai;

import android.content.Context;

import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import a.a.a.eC;
import a.a.a.hC;
import a.a.a.iC;
import a.a.a.jC;
import a.a.a.yq;
import sketchware.plus.managers.inject.InjectRootLayoutManager;

public class ProjectSnapshot {
    public ArrayList<ViewBean> views;
    public ArrayList<ProjectFileBean> activities;
    public ArrayList<ProjectFileBean> customViews;
    public ProjectLibraryBean firebase;
    public ProjectLibraryBean compat;
    public ProjectLibraryBean admob;
    public ProjectLibraryBean googleMap;
    public InjectRootLayoutManager.Root rootLayout;
    public HashMap<String, ArrayList<ComponentBean>> componentsMap;
    public int permissions;

    public ProjectSnapshot(String scId, String xmlName) {
        eC dataManager = jC.a(scId);
        hC fileManager = jC.b(scId);
        iC libraryManager = jC.c(scId);
        InjectRootLayoutManager rootManager = new InjectRootLayoutManager(scId);

        synchronized (dataManager) {
            synchronized (fileManager) {
                synchronized (libraryManager) {
                    this.views = new ArrayList<>();
                    for (ViewBean v : dataManager.d(xmlName)) {
                        this.views.add(v.clone());
                    }
                    this.activities = new ArrayList<>(fileManager.c);
                    this.customViews = new ArrayList<>(fileManager.d);
                    this.firebase = libraryManager.d().clone();
                    this.compat = libraryManager.c().clone();
                    this.admob = libraryManager.b().clone();
                    this.googleMap = libraryManager.e().clone();
                    this.rootLayout = rootManager.getLayoutByFileName(xmlName);
                    this.permissions = dataManager.l.q;

                    this.componentsMap = new HashMap<>();
                    if (dataManager.h != null) {
                        for (Map.Entry<String, ArrayList<ComponentBean>> entry : dataManager.h.entrySet()) {
                            ArrayList<ComponentBean> list = new ArrayList<>();
                            if (entry.getValue() != null) {
                                for (ComponentBean cb : entry.getValue()) {
                                    if (cb != null) {
                                        list.add(new ComponentBean(cb.type, cb.componentId, cb.param1, cb.param2, cb.param3));
                                    }
                                }
                            }
                            this.componentsMap.put(entry.getKey(), list);
                        }
                    }
                }
            }
        }
    }

    public void restore(String scId, String xmlName, Context context) {
        eC dataManager = jC.a(scId);
        hC fileManager = jC.b(scId);
        iC libraryManager = jC.c(scId);
        InjectRootLayoutManager rootManager = new InjectRootLayoutManager(scId);

        dataManager.c.put(xmlName, views);
        fileManager.c = activities;
        fileManager.d = customViews;
        libraryManager.a(admob);
        libraryManager.b(compat);
        libraryManager.c(firebase);
        libraryManager.d(googleMap);
        if (rootLayout != null) {
            rootManager.set(xmlName, rootLayout);
        }
        dataManager.l.q = permissions;

        if (componentsMap != null) {
            dataManager.h = new HashMap<>();
            for (Map.Entry<String, ArrayList<ComponentBean>> entry : componentsMap.entrySet()) {
                ArrayList<ComponentBean> list = new ArrayList<>();
                if (entry.getValue() != null) {
                    for (ComponentBean cb : entry.getValue()) {
                        if (cb != null) {
                            list.add(new ComponentBean(cb.type, cb.componentId, cb.param1, cb.param2, cb.param3));
                        }
                    }
                }
                dataManager.h.put(entry.getKey(), list);
            }
        }

        yq workspace = new yq(context, scId);
        dataManager.n(workspace.projectMyscPath + "view");
        fileManager.l();
        libraryManager.k();
    }
}
