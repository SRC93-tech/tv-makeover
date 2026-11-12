package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * DreamOS glass: rgba(217,217,217,0.71) with a 2px white inside edge, softened by the design's 2px layer blur.
 * The blur spills a few pixels past the bounds, so the parent must not clip there. It is drawn into a bitmap
 * once per size: a blur mask on every frame would cost the Mali-470 on each focus move.
 */
final class GlassDrawable extends Drawable {

    /** The design's 2px layer blur as a Skia blur radius (sigma 2). */
    private static final float BLUR_PX = 2.6f;
    private static final int MARGIN = (int) Math.ceil(BLUR_PX * 3);

    private final float density;
    private final float radius;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap cache;

    /** @param radiusPx corner radius in pixels (35px on the design: 17.5dp) */
    GlassDrawable(float density, float radiusPx) {
        this.density = density;
        this.radius = radiusPx;
        BlurMaskFilter blur = new BlurMaskFilter(BLUR_PX, BlurMaskFilter.Blur.NORMAL);
        fill.setColor(0xB5D9D9D9);
        fill.setMaskFilter(blur);
        edge.setColor(0xFFFFFFFF);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        edge.setMaskFilter(blur);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        int w = b.width() + 2 * MARGIN, h = b.height() + 2 * MARGIN;
        if (cache == null || cache.getWidth() != w || cache.getHeight() != h) {
            if (cache != null) cache.recycle();
            cache = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas g = new Canvas(cache);
            RectF shape = new RectF(MARGIN, MARGIN, w - MARGIN, h - MARGIN);
            g.drawRoundRect(shape, radius, radius, fill);
            // Figma strokes inside the shape.
            shape.inset(density / 2, density / 2);
            g.drawRoundRect(shape, radius, radius, edge);
        }
        canvas.drawBitmap(cache, b.left - MARGIN, b.top - MARGIN, null);
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
