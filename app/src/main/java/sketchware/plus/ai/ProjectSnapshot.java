package sketchware.plus.ai;

import android.content.Context;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;

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
        rootManager.set(xmlName, rootLayout);
        dataManager.l.q = permissions;

        yq workspace = new yq(context, scId);
        dataManager.n(workspace.projectMyscPath + "view");
        fileManager.l();
        libraryManager.k();
    }
}
