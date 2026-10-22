package sketchware.plus.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;

import com.besome.sketch.beans.HistoryViewBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.Ox;
import a.a.a.cC;
import a.a.a.eC;
import a.a.a.hC;
import a.a.a.iC;
import a.a.a.jC;
import a.a.a.yq;

import sketchware.plus.managers.inject.InjectRootLayoutManager;
import sketchware.plus.tools.ViewBeanParser;
import sketchware.plus.utility.relativelayout.CircularDependencyDetector;

public class SketchwareXmlBridge {

    public static String convertToAndroidStudioXml(Context context, String scId, ProjectFileBean projectFile) {
        String rawXml = getRawXml(context, scId, projectFile);
        if (rawXml.startsWith("<!-- Error")) return rawXml;
        return prepareXmlForAi(scId, projectFile.getXmlName(), rawXml);
    }

    public static String getRawXml(Context context, String scId, ProjectFileBean projectFile) {
        try {
            eC dataManager = jC.a(scId);
            hC fileManager = jC.b(scId);
            iC libraryManager = jC.c(scId);

            yq workspace = new yq(context, scId);
            workspace.a(libraryManager, fileManager, dataManager);
            workspace.N.g = false;

            String xmlName = projectFile.getXmlName();
            ArrayList<ViewBean> viewBeans = dataManager.d(xmlName);
            ViewBean viewFab = dataManager.h(xmlName);

            Ox ox = new Ox(workspace.N, projectFile);
            ox.setExcludeAppCompat(true);
            ox.a(eC.a(viewBeans), viewFab);

            return ox.b();
        } catch (Exception e) {
            e.printStackTrace();
            return "<!-- Error generating raw XML: " + e.getMessage() + " -->";
        }
    }

    public static boolean applyAiXmlToSketchware(Context context, String scId, String xmlName, String aiXml) throws Exception {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            final boolean[] result = new boolean[1];
            final Exception[] exception = new Exception[1];
            CountDownLatch latch = new CountDownLatch(1);
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    result[0] = applyAiXmlToSketchwareInternal(context, scId, xmlName, aiXml);
                } catch (Exception e) {
                    exception[0] = e;
                } finally {
                    latch.countDown();
                }
            });
            boolean awaited = latch.await(10, TimeUnit.SECONDS);
            if (!awaited) {
                throw new RuntimeException("Timeout applying AI XML on main thread");
            }
            if (exception[0] != null) throw exception[0];
            return result[0];
        }
        return applyAiXmlToSketchwareInternal(context, scId, xmlName, aiXml);
    }

    private static boolean applyAiXmlToSketchwareInternal(Context context, String scId, String xmlName, String aiXml) throws Exception {
        String sanitizedXml = sanitizeAiXmlInput(aiXml);
        String stableXml = reRootWithOriginalConfig(scId, xmlName, sanitizedXml);

        ViewBeanParser parser = new ViewBeanParser(stableXml);
        parser.setSkipRoot(true);

        ArrayList<ViewBean> newBeans = parser.parse();

        if (newBeans == null) {
            throw new IllegalArgumentException("Failed to parse AI XML into valid ViewBeans.");
        }

        eC dataManager = jC.a(scId);
        ArrayList<ViewBean> filteredBeans = new ArrayList<>();
        ViewBean fabBean = null;
        for (ViewBean b : newBeans) {
            if (b.id != null && (b.id.equals("_fab") || b.type == ViewBean.VIEW_TYPE_WIDGET_FAB || "FloatingActionButton".equals(ViewBean.getViewTypeName(b.type)))) {
                fabBean = b;
                AiViewTreeManager.log("Detected FAB bean in parsed XML: " + b.id);
            } else {
                filteredBeans.add(b);
            }
        }
        newBeans = filteredBeans;
        if (fabBean != null) {
            dataManager.j.put(xmlName, fabBean);
            ProjectFileBean pf = jC.b(scId).b(xmlName);
            if (pf != null) {
                pf.options |= ProjectFileBean.OPTION_ACTIVITY_FAB;
                jC.b(scId).j();
            }
        }

        ArrayList<ViewBean> oldBeans = dataManager.d(xmlName);
        for (ViewBean newBean : newBeans) {
            ViewBean oldBean = null;
            for (ViewBean ob : oldBeans) {
                if (ob.id != null && ob.id.equals(newBean.id)) {
                    oldBean = ob;
                    break;
                }
            }
            if (oldBean != null) {
                if (newBean.text != null && oldBean.text != null) {
                    if ((newBean.text.text == null || newBean.text.text.isEmpty()) && (oldBean.text.text != null && !oldBean.text.text.isEmpty())) {
                        newBean.text.text = oldBean.text.text;
                        AiViewTreeManager.log("Merged text for view " + newBean.id + " from old bean");
                    }
                    if ((newBean.text.hint == null || newBean.text.hint.isEmpty()) && (oldBean.text.hint != null && !oldBean.text.hint.isEmpty())) {
                        newBean.text.hint = oldBean.text.hint;
                        AiViewTreeManager.log("Merged hint for view " + newBean.id + " from old bean");
                    }
                    if (newBean.text.textSize <= 0 && oldBean.text.textSize > 0) {
                        newBean.text.textSize = oldBean.text.textSize;
                        AiViewTreeManager.log("Merged textSize for view " + newBean.id + " from old bean");
                    }
                }
                if (newBean.layout != null && oldBean.layout != null) {
                    if ((newBean.layout.backgroundResource == null || newBean.layout.backgroundResource.isEmpty()) && (oldBean.layout.backgroundResource != null && !oldBean.layout.backgroundResource.isEmpty())) {
                        newBean.layout.backgroundResource = oldBean.layout.backgroundResource;
                        AiViewTreeManager.log("Merged background for view " + newBean.id + " from old bean");
                    }
                }
            }
        }

        for (ViewBean oldBean : oldBeans) {
            boolean found = false;
            for (ViewBean newBean : newBeans) {
                if (oldBean.id != null && oldBean.id.equals(newBean.id)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                for (ViewBean newBean : newBeans) {
                    if (newBean.type == oldBean.type &&
                            ((newBean.parent != null && newBean.parent.equals(oldBean.parent)) || (newBean.parent == null && oldBean.parent == null)) &&
                            newBean.index == oldBean.index &&
                            newBeans.stream().noneMatch(b -> b.id != null && b.id.equals(oldBean.id))) {
                        AiViewTreeManager.log("Preserving old view ID " + oldBean.id + " for renamed/unmatched view of type " + oldBean.type);
                        newBean.id = oldBean.id;
                        break;
                    }
                }
            }
        }

        for (ViewBean viewBean : newBeans) {
            CircularDependencyDetector detector = new CircularDependencyDetector(newBeans, viewBean);
            for (String attr : viewBean.parentAttributes.keySet()) {
                String targetId = viewBean.parentAttributes.get(attr);
                if (!detector.isLegalAttribute(targetId, attr)) {
                    throw new IllegalArgumentException("Circular dependency found in \"" + viewBean.name + "\"\nPlease resolve the issue before saving");
                }
            }
        }

        Pair<String, Map<String, String>> root = parser.getRootAttributes();
        InjectRootLayoutManager rootLayoutManager = new InjectRootLayoutManager(scId);
        rootLayoutManager.set(xmlName, InjectRootLayoutManager.toRoot(root));

        HistoryViewBean historyBean = new HistoryViewBean();
        historyBean.actionOverride(newBeans, oldBeans);

        cC historyManager = cC.c(scId);
        if (!historyManager.c.containsKey(xmlName)) {
            historyManager.e(xmlName);
        }
        historyManager.a(xmlName);
        historyManager.a(xmlName, historyBean);

        dataManager.c.put(xmlName, newBeans);

        yq workspace = new yq(context, scId);
        dataManager.n(workspace.projectMyscPath + "view");

        return true;
    }

    private static String prepareXmlForAi(String scId, String xmlName, String rawXml) {
        if (rawXml == null) return "";
        
        InjectRootLayoutManager rootManager = new InjectRootLayoutManager(scId);
        InjectRootLayoutManager.Root root = rootManager.getLayoutByFileName(xmlName);
        
        String children = extractChildren(rawXml);
        
        StringBuilder sb = new StringBuilder();
        sb.append("<!-- ACTIVITY ROOT: ").append(root.getClassName()).append(" -->\n");
        sb.append("<!-- You can change this root type or add containers inside it -->\n");
        sb.append("<").append(root.getClassName());
        
        Map<String, String> attrs = root.getAttributes();
        if (attrs.containsKey("android:orientation")) {
            sb.append(" android:orientation=\"").append(attrs.get("android:orientation")).append("\"");
        }
        
        if (children.isEmpty()) {
            sb.append(" />");
        } else {
            sb.append(">\n");
            sb.append("    <!-- ADD OR MODIFY VIEWS INSIDE HERE -->\n");
            sb.append(children);
            sb.append("\n</").append(root.getClassName()).append(">");
        }
        
        return sb.toString().trim();
    }

    private static String extractChildren(String xml) {
        int firstStart = xml.indexOf("<");
        if (firstStart == -1) return "";
        
        int firstEnd = xml.indexOf(">", firstStart);
        if (firstEnd == -1) return "";
        
        if (xml.substring(firstStart, firstEnd + 1).endsWith("/>")) return "";
        
        int lastStart = xml.lastIndexOf("</");
        if (lastStart > firstEnd) {
            return xml.substring(firstEnd + 1, lastStart).trim();
        }
        return "";
    }

    public static String reRootWithOriginalConfig(String scId, String xmlName, String aiXml) {
        InjectRootLayoutManager rootManager = new InjectRootLayoutManager(scId);
        InjectRootLayoutManager.Root originalRoot = rootManager.getLayoutByFileName(xmlName);
        
        String trimmedAiXml = aiXml.trim();
        
        String aiRootTag = null;
        Pattern p = Pattern.compile("^<([\\w\\.]+)", Pattern.MULTILINE);
        Matcher m = p.matcher(trimmedAiXml);
        if (m.find()) {
            aiRootTag = m.group(1);
        }

        int topLevelCount = 0;
        Matcher topLevelMatcher = Pattern.compile("^<([\\w\\.]+)", Pattern.MULTILINE).matcher(trimmedAiXml);
        while (topLevelMatcher.find()) {
            topLevelCount++;
        }

        String innerContent;
        String finalRootClass = originalRoot.getClassName();
        Map<String, String> finalAttrs = new LinkedHashMap<>(originalRoot.getAttributes());

        if (aiRootTag != null && topLevelCount == 1 && (aiRootTag.equals(originalRoot.getClassName()) || aiRootTag.contains("Layout") || aiRootTag.contains("View") || aiRootTag.contains("Scroll") || aiRootTag.contains("Card"))) {
            innerContent = extractChildren(trimmedAiXml);
            if (innerContent.isEmpty() && !trimmedAiXml.contains("</")) {
                innerContent = trimmedAiXml;
            } else {
                finalRootClass = aiRootTag;
                int firstEnd = trimmedAiXml.indexOf(">");
                if (firstEnd != -1) {
                    String rootOpenTag = trimmedAiXml.substring(0, firstEnd);
                    Pattern attrPattern = Pattern.compile("([\\w:]+)=[\"']([^\"']*)[\"']");
                    Matcher attrMatcher = attrPattern.matcher(rootOpenTag);
                    while (attrMatcher.find()) {
                        String k = attrMatcher.group(1);
                        String v = attrMatcher.group(2);
                        if (k != null && !k.startsWith("xmlns:")) {
                            finalAttrs.put(k, v);
                        }
                    }
                }
            }
        } else {
            innerContent = trimmedAiXml;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("<").append(finalRootClass).append(" ");
        sb.append("xmlns:android=\"http://schemas.android.com/apk/res/android\" ");
        sb.append("xmlns:app=\"http://schemas.android.com/apk/res-auto\" ");
        sb.append("xmlns:tools=\"http://schemas.android.com/tools\" ");
        
        for (Map.Entry<String, String> entry : finalAttrs.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().startsWith("xmlns:")) {
                sb.append(entry.getKey()).append("=\"").append(entry.getValue()).append("\" ");
            }
        }
        
        sb.append(">\n").append(innerContent).append("\n</").append(finalRootClass).append(">");
        return sb.toString();
    }

    public static String sanitizeAiXmlInput(String aiResponse) {
        if (aiResponse == null) return "";

        String trimmed = aiResponse.trim();

        trimmed = trimmed.replaceAll("(?s)<\\?xml.*?[?]>", "").trim();
        trimmed = trimmed.replaceAll("(?s)<!--.*?-->", "").trim();

        if (trimmed.contains("```")) {
            Pattern pattern = Pattern.compile("```(?:xml)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(trimmed);
            if (matcher.find()) {
                trimmed = matcher.group(1).trim();
            }
        }

        trimmed = trimmed.replaceAll("(?s)<\\?xml.*?[?]>", "").trim();
        trimmed = trimmed.replaceAll("(?s)<!--.*?-->", "").trim();

        int start = trimmed.indexOf("<");
        int end = trimmed.lastIndexOf(">");
        if (start != -1 && end > start) {
            return trimmed.substring(start, end + 1).trim();
        }

        return trimmed;
    }
}
