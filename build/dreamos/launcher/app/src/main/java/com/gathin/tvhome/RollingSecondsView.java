package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Calendar;

/**
 * Displays live clock seconds as two vertically rolling numeral drums (tens + ones),
 * aligned beside the Home clock with tabular figures and zero CPU churn between ticks.
 */
public class RollingSecondsView extends View {

    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private int curTens = -1, prevTens = -1;
    private int curOnes = -1, prevOnes = -1;
    private float animTens = 1f, animOnes = 1f;
    private float textBaselineOffset;
    private float stepHeight;
    private float charWidth;
    private float colonWidth;

    private boolean running;
    private ValueAnimator rollAnim;

    private final Runnable secondTick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            updateToNow(true);
            scheduleNextSecond();
        }
    };

    public RollingSecondsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        float textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 18f, getResources().getDisplayMetrics());
        paint.setTextSize(textSizePx);
        paint.setColor(0xD9FFFFFF);
        paint.setShadowLayer(6f * density / 2f, 0f, 1f * density / 2f, 0x66000000);
        paint.setFontFeatureSettings("tnum");
        try {
            Typeface tf = context.getResources().getFont(R.font.inter_tight_medium);
            if (tf != null) paint.setTypeface(tf);
        } catch (Exception ignored) {
        }
        measureMetrics();
        updateToNow(false);
    }

    private void measureMetrics() {
        Paint.FontMetrics fm = paint.getFontMetrics();
        float textH = fm.descent - fm.ascent;
        stepHeight = (float) Math.ceil(textH * 1.15f);
        textBaselineOffset = (stepHeight - textH) * 0.5f - fm.ascent;
        charWidth = paint.measureText("8");
        colonWidth = paint.measureText(":");
    }

    public void start() {
        if (running) return;
        running = true;
        updateToNow(false);
        scheduleNextSecond();
    }

    public void stop() {
        running = false;
        removeCallbacks(secondTick);
        if (rollAnim != null) {
            rollAnim.cancel();
            rollAnim = null;
        }
    }

    private void scheduleNextSecond() {
        removeCallbacks(secondTick);
        if (!running) return;
        long now = System.currentTimeMillis();
        long delay = 1000L - (now % 1000L);
        postDelayed(secondTick, Math.max(20L, delay + 12L));
    }

    private void updateToNow(boolean animate) {
        int sec = Calendar.getInstance().get(Calendar.SECOND);
        int tens = sec / 10;
        int ones = sec % 10;
        boolean tensChanged = curTens != -1 && tens != curTens;
        boolean onesChanged = curOnes != -1 && ones != curOnes;
        if (!animate || (!tensChanged && !onesChanged)) {
            curTens = prevTens = tens;
            curOnes = prevOnes = ones;
            animTens = animOnes = 1f;
            invalidate();
            return;
        }
        prevTens = tensChanged ? curTens : tens;
        prevOnes = onesChanged ? curOnes : ones;
        curTens = tens;
        curOnes = ones;
        animTens = tensChanged ? 0f : 1f;
        animOnes = onesChanged ? 0f : 1f;
        if (rollAnim != null) rollAnim.cancel();
        rollAnim = ValueAnimator.ofFloat(0f, 1f);
        rollAnim.setDuration(220L);
        rollAnim.setInterpolator(new DecelerateInterpolator(1.7f));
        final boolean animT = tensChanged;
        final boolean animO = onesChanged;
        rollAnim.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            if (animT) animTens = f;
            if (animO) animOnes = f;
            invalidate();
        });
        rollAnim.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = (int) Math.ceil(colonWidth + charWidth * 2f + 2f);
        int h = (int) Math.ceil(stepHeight);
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (curTens < 0 || curOnes < 0) return;
        canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight());
        paint.setAlpha(210);
        canvas.drawText(":", 0f, textBaselineOffset, paint);
        drawDrum(canvas, colonWidth, prevTens, curTens, animTens);
        drawDrum(canvas, colonWidth + charWidth, prevOnes, curOnes, animOnes);
        canvas.restore();
    }

    private void drawDrum(Canvas canvas, float x, int prevDigit, int curDigit, float progress) {
        if (progress >= 0.999f || prevDigit == curDigit) {
            paint.setAlpha(255);
            canvas.drawText(String.valueOf(curDigit), x, textBaselineOffset, paint);
            return;
        }
        // Outgoing digit slides upward & fades out
        paint.setAlpha((int) (255 * (1f - progress)));
        canvas.drawText(String.valueOf(prevDigit), x, textBaselineOffset - stepHeight * progress, paint);
        // Incoming digit rolls upward into view from bottom
        paint.setAlpha((int) (255 * progress));
        canvas.drawText(String.valueOf(curDigit), x, textBaselineOffset + stepHeight * (1f - progress), paint);
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }
}
