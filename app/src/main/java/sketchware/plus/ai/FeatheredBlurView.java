package sketchware.plus.ai;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;

import eightbitlab.com.blurview.BlurView;

public class FeatheredBlurView extends BlurView {

    private Paint maskPaint;
    private float featherRadius;
    private float cornerRadius;
    private final RectF rectF = new RectF();

    public FeatheredBlurView(Context context) {
        super(context);
        init();
    }

    public FeatheredBlurView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FeatheredBlurView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null);
        float density = getResources().getDisplayMetrics().density;
        featherRadius = density * 12f;
        cornerRadius = density * 16f;

        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
            }
        });
        setClipToOutline(true);

        maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maskPaint.setColor(0xFF000000);
        maskPaint.setMaskFilter(new BlurMaskFilter(featherRadius, BlurMaskFilter.Blur.NORMAL));
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        int saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        super.draw(canvas);
        if (getWidth() > featherRadius * 2 && getHeight() > featherRadius * 2) {
            rectF.set(
                    featherRadius,
                    featherRadius,
                    getWidth() - featherRadius,
                    getHeight() - featherRadius
            );
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, maskPaint);
        }
        canvas.restoreToCount(saveCount);
    }
}
