package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Soft atmospheric crimson-and-white radial spotlight glow that tracks the focused icon.
 * Pre-renders a single lightweight hardware-cached bitmap so scrolling/gliding costs one texture draw.
 */
public class SpotlightGlowView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();
    private Bitmap mask;

    public SpotlightGlowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        buildTexture();
    }

    private void buildTexture() {
        int w = 240, h = 160;
        mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(mask);
        // Atmospheric white spotlight glow with a subtle crimson Sharingan undertone at mid-halo
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(
                w * 0.5f, h * 0.5f, w * 0.49f,
                new int[]{0x52FFFFFF, 0x2AE11D2E, 0x10B91C1C, 0x00000000},
                new float[]{0.0f, 0.32f, 0.66f, 1.0f},
                Shader.TileMode.CLAMP));
        c.scale(1f, (float) h / w);
        c.drawCircle(w * 0.5f, w * 0.5f, w * 0.49f, p);
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

