package com.cilicalab.blur;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.*;
import android.widget.FrameLayout;
import androidx.recyclerview.widget.RecyclerView;

public class BlurView extends FrameLayout {
    private View target;
    private RecyclerView rv;
    private Bitmap small;
    private Canvas cap;
    private int[] p, t;
    private float scale = 0.2f;
    private int radius = 6;
    private boolean usePreDraw;
    private boolean rendering;
    private int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE;
    private boolean dirty = true;
    private final int[] a = new int[2], b = new int[2];
    private final Rect dst = new Rect();
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final float[] radii = new float[8];
    private boolean rounded;

    private final ViewTreeObserver.OnPreDrawListener pre = new ViewTreeObserver.OnPreDrawListener() {
        @Override public boolean onPreDraw() {
            if (target == null || getWidth() == 0 || getVisibility() != VISIBLE) return true;
            getLocationOnScreen(b);
            if (dirty || b[0] != lastX || b[1] != lastY) {
                lastX = b[0]; lastY = b[1]; dirty = false;
                render();
            }
            return true;
        }
    };
    private final RecyclerView.OnScrollListener scroll = new RecyclerView.OnScrollListener() {
        @Override public void onScrolled(RecyclerView r, int dx, int dy) { render(); }
    };
    private final RecyclerView.AdapterDataObserver data = new RecyclerView.AdapterDataObserver() {
        @Override public void onChanged() { post(BlurView.this::render); }
        @Override public void onItemRangeInserted(int s, int c) { post(BlurView.this::render); }
        @Override public void onItemRangeRemoved(int s, int c) { post(BlurView.this::render); }
        @Override public void onItemRangeChanged(int s, int c) { post(BlurView.this::render); }
    };

    public BlurView(Context c) { super(c); setWillNotDraw(false); }
    public BlurView(Context c, AttributeSet s) { super(c, s); setWillNotDraw(false); }

    // static or animating background
    public void setTarget(View v) {
        unhook();
        target = v; usePreDraw = true; dirty = true;
        hook();
    }

    // blur overlay sits ON TOP of the recycler, blurs the scrolling items
    public void attachToRecycler(RecyclerView r) {
        unhook();
        rv = r; target = r; usePreDraw = false; dirty = true;
        hook();
    }

    // all corners, in dp
    public void setCornerRadius(float dp) { setCorners(dp, dp, dp, dp); }

    // per corner, in dp: topLeft, topRight, bottomRight, bottomLeft
    public void setCorners(float tl, float tr, float br, float bl) {
        float d = getResources().getDisplayMetrics().density;
        radii[0] = radii[1] = tl * d;
        radii[2] = radii[3] = tr * d;
        radii[4] = radii[5] = br * d;
        radii[6] = radii[7] = bl * d;
        rounded = (tl + tr + br + bl) > 0;
        updateClip();
    }

    private void updateClip() {
        clip.reset();
        rect.set(0, 0, getWidth(), getHeight());
        clip.addRoundRect(rect, radii, Path.Direction.CW);
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        updateClip();
        dirty = true;
        if (target != null) post(this::render);
    }

    public void setRadius(int r) { radius = Math.max(1, r); }
    public void setScale(float s) { scale = s; small = null; }

    private void hook() {
        if (target == null || !isAttachedToWindow()) return;
        if (usePreDraw) target.getViewTreeObserver().addOnPreDrawListener(pre);
        else {
            rv.addOnScrollListener(scroll);
            if (rv.getAdapter() != null) {
                try { rv.getAdapter().registerAdapterDataObserver(data); } catch (Exception ignored) {}
            }
        }
        post(this::render);
    }

    private void unhook() {
        if (target == null) return;
        target.getViewTreeObserver().removeOnPreDrawListener(pre);
        if (rv != null) {
            rv.removeOnScrollListener(scroll);
            if (rv.getAdapter() != null) {
                try { rv.getAdapter().unregisterAdapterDataObserver(data); } catch (Exception ignored) {}
            }
        }
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); dirty = true; hook(); }
    @Override protected void onDetachedFromWindow() { unhook(); super.onDetachedFromWindow(); }

    private void render() {
        if (rendering || target == null || getWidth() == 0 || getHeight() == 0) return;
        rendering = true;
        try {
            int w = Math.max(1, (int) (getWidth() * scale)), h = Math.max(1, (int) (getHeight() * scale));
            if (small == null || small.getWidth() != w || small.getHeight() != h) {
                small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                cap = new Canvas(small);
                p = new int[w * h]; t = new int[w * h];
            }
            small.eraseColor(0);
            target.getLocationOnScreen(a);
            getLocationOnScreen(b);
            cap.save();
            cap.scale(scale, scale);
            cap.translate(a[0] - b[0], a[1] - b[1]);
            target.draw(cap);
            cap.restore();
            blur(w, h);
            invalidate();
        } finally {
            rendering = false;
        }
    }

    @Override public void draw(Canvas c) {
        int save = c.save();
        if (rounded) c.clipPath(clip);
        if (small != null) {
            dst.set(0, 0, getWidth(), getHeight());
            c.drawBitmap(small, null, dst, null);
        }
        super.draw(c);
        c.restoreToCount(save);
    }

    private void blur(int w, int h) {
        small.getPixels(p, 0, w, 0, 0, w, h);
        for (int i = 0; i < 2; i++) { box(p, t, w, h, radius); box(t, p, h, w, radius); }
        small.setPixels(p, 0, w, 0, 0, w, h);
    }

    private static void box(int[] s, int[] d, int w, int h, int r) {
        int n = r * 2 + 1;
        for (int y = 0; y < h; y++) {
            int A = 0, R = 0, G = 0, B = 0, row = y * w;
            for (int i = -r; i <= r; i++) {
                int px = s[row + Math.min(w - 1, Math.max(0, i))];
                A += px >>> 24; R += (px >> 16) & 255; G += (px >> 8) & 255; B += px & 255;
            }
            for (int x = 0; x < w; x++) {
                d[x * h + y] = ((A / n) << 24) | ((R / n) << 16) | ((G / n) << 8) | (B / n);
                int add = s[row + Math.min(w - 1, x + r + 1)], rem = s[row + Math.max(0, x - r)];
                A += (add >>> 24) - (rem >>> 24);
                R += ((add >> 16) & 255) - ((rem >> 16) & 255);
                G += ((add >> 8) & 255) - ((rem >> 8) & 255);
                B += (add & 255) - (rem & 255);
            }
        }
    }
}
