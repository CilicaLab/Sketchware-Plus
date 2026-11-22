package sketchware.plus.ai;

import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import com.cilicalab.blur.BlurView;
import sketchware.plus.R;

public class BlurViewHelper {

    public static void setup(BlurView blurView, RecyclerView rv) {
        if (blurView == null || rv == null) return;
        if (blurView.getTag() == rv) return;
        blurView.setTag(rv);

        View targetView = findTargetView(rv);
        blurView.setRadius(6);
        blurView.setScale(0.2f);
        blurView.setCornerRadius(20);

        if (targetView != null) {
            blurView.setTarget(targetView);
        } else {
            blurView.attachToRecycler(rv);
        }
    }

    private static View findTargetView(View view) {
        View current = view;
        while (current != null) {
            View patternBg = current.findViewById(R.id.pattern_bg);
            if (patternBg != null) {
                return patternBg;
            }
            if (current.getParent() instanceof View) {
                current = (View) current.getParent();
            } else {
                break;
            }
        }
        return null;
    }
}
