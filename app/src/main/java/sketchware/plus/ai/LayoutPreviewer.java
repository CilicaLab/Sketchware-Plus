package sketchware.plus.ai;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.ItemView;
import com.besome.sketch.editor.view.ViewPane;

import java.util.ArrayList;

import a.a.a.jC;
import sketchware.plus.tools.ViewBeanParser;

public class LayoutPreviewer {

    public static View createPreview(Context context, String scId, String xml, String xmlName) {
        try {
            String sanitizedXml = SketchwareXmlBridge.sanitizeAiXmlInput(xml);
            String stableXml = SketchwareXmlBridge.reRootWithOriginalConfig(scId, xmlName, sanitizedXml);
            
            ViewBeanParser parser = new ViewBeanParser(stableXml);
            parser.setSkipRoot(true);
            ArrayList<ViewBean> views = parser.parse();

            ViewPane pane = new ViewPane(context);
            pane.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            
            pane.initialize(scId, true);
            pane.updateRootLayout(scId, xmlName);
            pane.setResourceManager(jC.d(scId));

            for (ViewBean view : views) {
                if (views.indexOf(view) == 0) {
                    view.parent = "root";
                    view.parentType = 0;
                    view.preParent = null;
                    view.preParentType = -1;
                }
                var itemView = pane.createItemView(view);
                pane.addViewAndUpdateIndex(itemView);
                if (itemView instanceof ItemView sy) {
                    sy.setFixed(true);
                }
            }

            ScrollView scrollView = new ScrollView(context);
            scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            scrollView.addView(pane);

            return scrollView;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
