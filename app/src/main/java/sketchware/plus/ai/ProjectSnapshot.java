package sketchware.plus.ai;

import android.content.Context;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.beans.ViewBean;

import java.io.BufferedReader;
import java.io.StringReader;
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
    public String logicData;
    public String viewData;
    public ArrayList<ViewBean> views;
    public ArrayList<ProjectFileBean> activities;
    public ArrayList<ProjectFileBean> customViews;
    public ProjectLibraryBean firebase;
    public ProjectLibraryBean compat;
    public ProjectLibraryBean admob;
    public ProjectLibraryBean googleMap;
    public InjectRootLayoutManager.Root rootLayout;
    public HashMap<String, ArrayList<ComponentBean>> componentsMap;
    public HashMap<String, ArrayList<EventBean>> eventsMap;
    public HashMap<String, HashMap<String, ArrayList<BlockBean>>> blocksMap;
    public int permissions;

    public ProjectSnapshot(String scId, String xmlName) {
        eC dataManager = jC.a(scId);
        hC fileManager = jC.b(scId);
        iC libraryManager = jC.c(scId);
        InjectRootLayoutManager rootManager = new InjectRootLayoutManager(scId);

        synchronized (dataManager) {
            synchronized (fileManager) {
                synchronized (libraryManager) {
                    // Capture full serialized logic and view states via eC string buffers
                    try {
                        StringBuffer logicBuf = new StringBuffer();
                        dataManager.a(logicBuf);
                        this.logicData = logicBuf.toString();

                        StringBuffer viewBuf = new StringBuffer();
                        dataManager.b(viewBuf);
                        this.viewData = viewBuf.toString();
                    } catch (Exception ignored) {}

                    this.views = new ArrayList<>();
                    ArrayList<ViewBean> currentViews = dataManager.d(xmlName);
                    if (currentViews != null) {
                        for (ViewBean v : currentViews) {
                            if (v != null) {
                                this.views.add(v.clone());
                            }
                        }
                    }

                    // Deep copy activities and custom views so subsequent mutations do not affect snapshot
                    this.activities = new ArrayList<>();
                    if (fileManager.c != null) {
                        for (ProjectFileBean pfb : fileManager.c) {
                            if (pfb != null) {
                                ProjectFileBean copy = new ProjectFileBean(pfb.fileType, pfb.fileName);
                                copy.copy(pfb);
                                this.activities.add(copy);
                            }
                        }
                    }

                    this.customViews = new ArrayList<>();
                    if (fileManager.d != null) {
                        for (ProjectFileBean pfb : fileManager.d) {
                            if (pfb != null) {
                                ProjectFileBean copy = new ProjectFileBean(pfb.fileType, pfb.fileName);
                                copy.copy(pfb);
                                this.customViews.add(copy);
                            }
                        }
                    }

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

                    // Snapshot Events (dataManager.i)
                    this.eventsMap = new HashMap<>();
                    if (dataManager.i != null) {
                        for (Map.Entry<String, ArrayList<EventBean>> entry : dataManager.i.entrySet()) {
                            ArrayList<EventBean> eventList = new ArrayList<>();
                            if (entry.getValue() != null) {
                                for (EventBean eb : entry.getValue()) {
                                    if (eb != null) {
                                        eventList.add(new EventBean(eb.eventType, eb.targetType, eb.targetId, eb.eventName));
                                    }
                                }
                            }
                            this.eventsMap.put(entry.getKey(), eventList);
                        }
                    }

                    // Snapshot Logic Blocks (dataManager.d)
                    this.blocksMap = new HashMap<>();
                    if (dataManager.d != null) {
                        for (Map.Entry<String, HashMap<String, ArrayList<BlockBean>>> javaEntry : dataManager.d.entrySet()) {
                            HashMap<String, ArrayList<BlockBean>> javaBlocks = new HashMap<>();
                            if (javaEntry.getValue() != null) {
                                for (Map.Entry<String, ArrayList<BlockBean>> eventEntry : javaEntry.getValue().entrySet()) {
                                    ArrayList<BlockBean> blockList = new ArrayList<>();
                                    if (eventEntry.getValue() != null) {
                                        for (BlockBean bb : eventEntry.getValue()) {
                                            if (bb != null) {
                                                blockList.add(bb.clone());
                                            }
                                        }
                                    }
                                    javaBlocks.put(eventEntry.getKey(), blockList);
                                }
                            }
                            this.blocksMap.put(javaEntry.getKey(), javaBlocks);
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

        synchronized (dataManager) {
            synchronized (fileManager) {
                synchronized (libraryManager) {
                    // 1. Restore full logic and view data from serialized buffers if available
                    dataManager.b(); // Clear current memory state

                    if (logicData != null && !logicData.isEmpty()) {
                        try {
                            dataManager.a(new BufferedReader(new StringReader(logicData)));
                        } catch (Exception ignored) {}
                    }

                    if (viewData != null && !viewData.isEmpty()) {
                        try {
                            dataManager.b(new BufferedReader(new StringReader(viewData)));
                        } catch (Exception ignored) {}
                    }

                    dataManager.c.put(xmlName, views);

                    fileManager.c = new ArrayList<>();
                    if (this.activities != null) {
                        for (ProjectFileBean pfb : this.activities) {
                            if (pfb != null) {
                                ProjectFileBean copy = new ProjectFileBean(pfb.fileType, pfb.fileName);
                                copy.copy(pfb);
                                fileManager.c.add(copy);
                            }
                        }
                    }

                    fileManager.d = new ArrayList<>();
                    if (this.customViews != null) {
                        for (ProjectFileBean pfb : this.customViews) {
                            if (pfb != null) {
                                ProjectFileBean copy = new ProjectFileBean(pfb.fileType, pfb.fileName);
                                copy.copy(pfb);
                                fileManager.d.add(copy);
                            }
                        }
                    }

                    fileManager.j(); // Rebuild java and xml lists
                    fileManager.l(); // Persist activities list to disk
                    dataManager.a(fileManager); // Sync fileManager with dataManager

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
                            this.componentsMap.put(entry.getKey(), list);
                        }
                    }

                    if (eventsMap != null) {
                        dataManager.i = new HashMap<>();
                        for (Map.Entry<String, ArrayList<EventBean>> entry : eventsMap.entrySet()) {
                            ArrayList<EventBean> eventList = new ArrayList<>();
                            if (entry.getValue() != null) {
                                for (EventBean eb : entry.getValue()) {
                                    if (eb != null) {
                                        eventList.add(new EventBean(eb.eventType, eb.targetType, eb.targetId, eb.eventName));
                                    }
                                }
                            }
                            dataManager.i.put(entry.getKey(), eventList);
                        }
                    }

                    if (blocksMap != null) {
                        dataManager.d = new HashMap<>();
                        for (Map.Entry<String, HashMap<String, ArrayList<BlockBean>>> javaEntry : blocksMap.entrySet()) {
                            HashMap<String, ArrayList<BlockBean>> javaBlocks = new HashMap<>();
                            if (javaEntry.getValue() != null) {
                                for (Map.Entry<String, ArrayList<BlockBean>> eventEntry : javaEntry.getValue().entrySet()) {
                                    ArrayList<BlockBean> blockList = new ArrayList<>();
                                    if (eventEntry.getValue() != null) {
                                        for (BlockBean bb : eventEntry.getValue()) {
                                            if (bb != null) {
                                                blockList.add(bb.clone());
                                            }
                                        }
                                    }
                                    javaBlocks.put(eventEntry.getKey(), blockList);
                                }
                            }
                            dataManager.d.put(javaEntry.getKey(), javaBlocks);
                        }
                    }

                    dataManager.k(); // Persist restored state to disk
                }
            }
        }

        yq workspace = new yq(context, scId);
        dataManager.n(workspace.projectMyscPath + "view");
        fileManager.l();
        libraryManager.k();
    }

    public String toStateSummaryString() {
        StringBuilder sb = new StringBuilder();
        sb.append("--- Project State Summary ---\n");
        sb.append("Activities: ");
        if (activities != null && !activities.isEmpty()) {
            java.util.List<String> actNames = new java.util.ArrayList<>();
            for (ProjectFileBean pf : activities) {
                if (pf != null) actNames.add(pf.getJavaName());
            }
            sb.append(String.join(", ", actNames));
        } else {
            sb.append("none");
        }
        sb.append("\nCustom Views: ");
        if (customViews != null && !customViews.isEmpty()) {
            java.util.List<String> cvNames = new java.util.ArrayList<>();
            for (ProjectFileBean pf : customViews) {
                if (pf != null) cvNames.add(pf.getJavaName());
            }
            sb.append(String.join(", ", cvNames));
        } else {
            sb.append("none");
        }
        sb.append("\nCurrent Layout Views (Count: ").append(views != null ? views.size() : 0).append("): ");
        if (views != null && !views.isEmpty()) {
            java.util.List<String> viewInfo = new java.util.ArrayList<>();
            for (ViewBean v : views) {
                if (v != null) {
                    viewInfo.add(v.id + " (" + v.getClass().getSimpleName() + ")");
                }
            }
            sb.append(String.join(", ", viewInfo));
        } else {
            sb.append("none");
        }
        sb.append("\nActive Components: ");
        if (componentsMap != null && !componentsMap.isEmpty()) {
            java.util.List<String> compInfo = new java.util.ArrayList<>();
            for (Map.Entry<String, ArrayList<ComponentBean>> entry : componentsMap.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    java.util.List<String> ids = new java.util.ArrayList<>();
                    for (ComponentBean cb : entry.getValue()) {
                        if (cb != null) {
                            ids.add(cb.componentId + " [" + ComponentBean.getComponentTypeName(cb.type) + "]");
                        }
                    }
                    compInfo.add(entry.getKey() + " -> {" + String.join(", ", ids) + "}");
                }
            }
            sb.append(String.join("; ", compInfo));
        } else {
            sb.append("none");
        }
        return sb.toString();
    }
}
