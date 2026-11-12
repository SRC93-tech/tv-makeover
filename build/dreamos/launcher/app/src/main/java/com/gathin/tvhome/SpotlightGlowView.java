package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Wide, ultra-soft pure-white radial spotlight glow that glides behind the focused app icon.
 * Uses a smooth Gaussian falloff that decays seamlessly to zero so no hard oval rim ever appears.
 */
public class SpotlightGlowView extends View {

    /** Normalized Y center matching the focused icon's vertical center. */
    public static final float FOCAL_Y = 0.50f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();
    private Bitmap mask;

    public SpotlightGlowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        buildTexture();
    }

    private void buildTexture() {
        int w = 256, h = 180;
        mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[w * h];

        double k = 2.6;
        double floor = Math.exp(-k);
        double norm = 1.0 - floor;
        double peakAlpha = 0.16;

        for (int y = 0; y < h; y++) {
            double dy = (y / (double) (h - 1) - 0.5) * 2.0;
            double dy2 = dy * dy;
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                double dx = (x / (double) (w - 1) - 0.5) * 2.0;
                double r2 = dx * dx + dy2;
                int a = 0;
                if (r2 < 1.0) {
                    double g = (Math.exp(-k * r2) - floor) / norm;
                    a = (int) Math.round(peakAlpha * g * 255.0);
                }
                pixels[rowOffset + x] = (a << 24) | 0x00EAF4FF;
            }
        }
        mask.setPixels(pixels, 0, w, 0, 0, w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        dst.set(0, 0, w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mask != null && !mask.isRecycled()) {
            canvas.drawBitmap(mask, null, dst, paint);
        }
    }
}

