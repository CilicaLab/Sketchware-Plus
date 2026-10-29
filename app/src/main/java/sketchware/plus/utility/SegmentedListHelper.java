package sketchware.plus.utility;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SegmentedListHelper {

    public static void applySegmentedGroup(Context context, View... views) {
        if (views != null) {
            applySegmentedGroup(context, Arrays.asList(views));
        }
    }

    public static void applySegmentedGroup(Context context, List<View> views) {
        if (context == null || views == null || views.isEmpty()) return;

        List<View> visibleViews = new ArrayList<>();
        for (View v : views) {
            if (v != null && v.getVisibility() == View.VISIBLE) {
                visibleViews.add(v);
            }
        }

        int count = visibleViews.size();
        if (count == 0) return;

        float density = context.getResources().getDisplayMetrics().density;
        float largePx = 16f * density;
        float smallPx = 4f * density;

        int bgColor = getThemeColor(context, com.google.android.material.R.attr.colorSurfaceContainer);
        int rippleColor = getThemeColor(context, android.R.attr.colorControlHighlight);

        for (int position = 0; position < count; position++) {
            View view = visibleViews.get(position);

            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            if (lp != null) {
                lp.topMargin = (position == 0) ? (int)(8f * density) : (int)(2f * density);
                lp.bottomMargin = 0;
                view.setLayoutParams(lp);
            }

            float[] radii;
            if (count == 1) {
                // Single item: large corners on all sides (~16dp)
                radii = new float[]{ largePx, largePx, largePx, largePx, largePx, largePx, largePx, largePx };
            } else if (position == 0) {
                // First item: large top corners (~16dp), small bottom corners (~4dp)
                radii = new float[]{ largePx, largePx, largePx, largePx, smallPx, smallPx, smallPx, smallPx };
            } else if (position == count - 1) {
                // Last item: small top corners (~4dp), large bottom corners (~16dp)
                radii = new float[]{ smallPx, smallPx, smallPx, smallPx, largePx, largePx, largePx, largePx };
            } else {
                // Middle items: small corners (~4dp) on all sides
                radii = new float[]{ smallPx, smallPx, smallPx, smallPx, smallPx, smallPx, smallPx, smallPx };
            }

            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RECTANGLE);
            shape.setColor(bgColor);
            shape.setCornerRadii(radii);

            RippleDrawable rippleDrawable = new RippleDrawable(
                    ColorStateList.valueOf(rippleColor),
                    shape,
                    null
            );

            view.setBackground(rippleDrawable);
        }
    }

    private static int getThemeColor(Context context, int attrResId) {
        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(attrResId, typedValue, true)) {
            return typedValue.data;
        }
        return 0;
    }
}
