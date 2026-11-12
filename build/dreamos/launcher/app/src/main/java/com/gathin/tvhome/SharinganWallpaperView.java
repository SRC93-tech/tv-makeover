package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Random;

/**
 * Full-screen dark Itachi portrait backdrop with live cursor-tracking Sharingan eyes,
 * natural random blinks, and a smooth Sharingan wake spin when Home turns on.
 * Renders at 0 fps when settled and only runs short animations during focus moves, blinks, or wake.
 */
public class SharinganWallpaperView extends View {

    // Base design frame matching res/drawable-nodpi/itachi_hero_bg.jpg (1920x1080)
    private static final float SRC_W = 1920f;
    private static final float SRC_H = 1080f;

    // Calibrated normalized eye centers & iris radius on 1920x1080
    private static final float LEFT_EYE_NX = 0.4408f;
    private static final float LEFT_EYE_NY = 0.3905f;
    private static final float RIGHT_EYE_NX = 0.5650f;
    private static final float RIGHT_EYE_NY = 0.3905f;
    private static final float IRIS_RADIUS_N = 0.0154f; // ~29.5px at 1080p: fits naturally under upper lid

    private static final float MAX_GAZE_X_N = 0.0080f;  // ~15.4px horizontal travel
    private static final float MAX_GAZE_Y_N = 0.0046f;  // ~5.0px vertical travel

    private Bitmap bgBitmap;
    private final Matrix bgMatrix = new Matrix();
    private final Paint bgPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    private final Paint bottomScrimPaint = new Paint();
    private final Paint scleraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyeShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint redAuraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint irisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint irisOuterRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pupilPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tomoePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint catchlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyelidFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lashRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path leftEyeClip = new Path();
    private final Path rightEyeClip = new Path();
    private final Path blinkRectPath = new Path();
    private final Path singleTomoePath = new Path();
    private final RectF leftSocketBounds = new RectF();
    private final RectF rightSocketBounds = new RectF();

    private float leftCx, leftCy, rightCx, rightCy, irisRadius;
    private float maxGazeX, maxGazeY, softRadiusPx;

    // Live animated state
    private float curLeftDx, curLeftDy, curRightDx, curRightDy;
    private float startLeftDx, startLeftDy, startRightDx, startRightDy;
    private float targetLeftDx, targetLeftDy, targetRightDx, targetRightDy;
    private float focusTargetX = -1f, focusTargetY = -1f;

    private float irisRotationDeg = 0f;
    private float wakePulseAlpha = 0f;
    private float blinkCloseFraction = 0f;

    private final float[] tmpOffset = new float[2];
    private ValueAnimator gazeAnim;
    private ValueAnimator spinAnim;
    private ValueAnimator blinkAnim;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private boolean active;

    private final Runnable blinkScheduler = new Runnable() {
        @Override
        public void run() {
            if (!active) return;
            triggerBlink();
            scheduleNextBlink();
        }
    };

    public SharinganWallpaperView(Context context) {
        this(context, null);
    }

    public SharinganWallpaperView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }

    private void initPaints() {
        irisOuterRimPaint.setStyle(Paint.Style.STROKE);
        irisOuterRimPaint.setColor(0xFF280202);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setColor(0xD91A0404);

        pupilPaint.setStyle(Paint.Style.FILL);
        pupilPaint.setColor(0xFF0A0303);

        tomoePaint.setStyle(Paint.Style.FILL);
        tomoePaint.setColor(0xEE0D0202);

        catchlightPaint.setStyle(Paint.Style.FILL);
        catchlightPaint.setColor(0xCCFCE7E7);

        eyelidFillPaint.setStyle(Paint.Style.FILL);
        eyelidFillPaint.setColor(0xFF151C24);

        lashRimPaint.setStyle(Paint.Style.STROKE);
        lashRimPaint.setStrokeCap(Paint.Cap.ROUND);
        lashRimPaint.setColor(0xE804060A);
    }

    private void ensureBitmap(int w, int h) {
        if (bgBitmap != null && !bgBitmap.isRecycled()) return;
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            opts.inScaled = false;
            bgBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.itachi_hero_bg, opts);
        } catch (OutOfMemoryError ignored) {
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;
        ensureBitmap(w, h);

        float bw = bgBitmap != null ? bgBitmap.getWidth() : SRC_W;
        float bh = bgBitmap != null ? bgBitmap.getHeight() : SRC_H;
        float scale = Math.max(w / bw, h / bh);
        float dx = (w - bw * scale) * 0.5f;
        float dy = (h - bh * scale) * 0.5f;
        bgMatrix.reset();
        bgMatrix.setScale(scale, scale);
        bgMatrix.postTranslate(dx, dy);

        // Compute drawn image geometry so eye sockets match bit-for-bit across 720p/1080p/4K
        float imgScale = Math.max(w / SRC_W, h / SRC_H);
        float imgW = SRC_W * imgScale;
        float imgH = SRC_H * imgScale;
        float ox = (w - imgW) * 0.5f;
        float oy = (h - imgH) * 0.5f;

        leftCx = ox + LEFT_EYE_NX * imgW;
        leftCy = oy + LEFT_EYE_NY * imgH;
        rightCx = ox + RIGHT_EYE_NX * imgW;
        rightCy = oy + RIGHT_EYE_NY * imgH;
        irisRadius = Math.max(12f, IRIS_RADIUS_N * imgW);
        maxGazeX = MAX_GAZE_X_N * imgW;
        maxGazeY = MAX_GAZE_Y_N * imgH;
        softRadiusPx = Math.max(220f, w * 0.34f);

        buildEyeSocketClip(leftEyeClip, leftSocketBounds, ox, oy, imgW, imgH, true);
        buildEyeSocketClip(rightEyeClip, rightSocketBounds, ox, oy, imgW, imgH, false);

        scleraPaint.setShader(new LinearGradient(
                0f, Math.min(leftSocketBounds.top, rightSocketBounds.top),
                0f, Math.max(leftSocketBounds.bottom, rightSocketBounds.bottom),
                new int[]{0xFF42505E, 0xFF6A7D92, 0xFF788CA2},
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP));

        eyeShadowPaint.setShader(new LinearGradient(
                0f, Math.min(leftSocketBounds.top, rightSocketBounds.top),
                0f, Math.min(leftSocketBounds.top, rightSocketBounds.top) + irisRadius * 0.65f,
                new int[]{0x7A04070C, 0x0004070C},
                null,
                Shader.TileMode.CLAMP));

        redAuraPaint.setShader(new RadialGradient(
                0f, 0f, irisRadius * 1.65f,
                new int[]{0x99EF4444, 0x38DC2626, 0x00B91C1C},
                new float[]{0.35f, 0.68f, 1f},
                Shader.TileMode.CLAMP));

        irisPaint.setShader(new RadialGradient(
                0f, 0f, irisRadius,
                new int[]{0xFFFF4D4D, 0xFFE11D2E, 0xFF991B1B, 0xFF450A0A},
                new float[]{0.12f, 0.56f, 0.86f, 1f},
                Shader.TileMode.CLAMP));

        bottomScrimPaint.setShader(new LinearGradient(
                0f, h * 0.60f, 0f, h,
                new int[]{0x00000000, 0x73020408, 0xCF020408},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));

        irisOuterRimPaint.setStrokeWidth(Math.max(1.5f, irisRadius * 0.085f));
        ringPaint.setStrokeWidth(Math.max(1.2f, irisRadius * 0.055f));
        lashRimPaint.setStrokeWidth(Math.max(2.0f, irisRadius * 0.11f));

        buildTomoePath(irisRadius);

        if (focusTargetX >= 0f && focusTargetY >= 0f) {
            updateGazeTargets();
            curLeftDx = targetLeftDx;
            curLeftDy = targetLeftDy;
            curRightDx = targetRightDx;
            curRightDy = targetRightDy;
        }
    }

    private static void buildEyeSocketClip(Path out, RectF bounds, float ox, float oy,
                                           float imgW, float imgH, boolean leftEye) {
        out.reset();
        if (leftEye) {
            float x0 = ox + 0.415f * imgW, y0 = oy + 0.382f * imgH;
            float x1 = ox + 0.440f * imgW, y1 = oy + 0.371f * imgH;
            float x2 = ox + 0.474f * imgW, y2 = oy + 0.400f * imgH;
            float x3 = ox + 0.442f * imgW, y3 = oy + 0.412f * imgH;
            out.moveTo(x0, y0);
            out.quadTo(x1, y1, x2, y2);
            out.quadTo(ox + 0.460f * imgW, oy + 0.412f * imgH, x3, y3);
            out.quadTo(ox + 0.424f * imgW, oy + 0.410f * imgH, x0, y0);
            out.close();
        } else {
            float x0 = ox + 0.588f * imgW, y0 = oy + 0.382f * imgH;
            float x1 = ox + 0.563f * imgW, y1 = oy + 0.371f * imgH;
            float x2 = ox + 0.529f * imgW, y2 = oy + 0.400f * imgH;
            float x3 = ox + 0.561f * imgW, y3 = oy + 0.412f * imgH;
            out.moveTo(x0, y0);
            out.quadTo(x1, y1, x2, y2);
            out.quadTo(ox + 0.543f * imgW, oy + 0.412f * imgH, x3, y3);
            out.quadTo(ox + 0.579f * imgW, oy + 0.410f * imgH, x0, y0);
            out.close();
        }
        out.computeBounds(bounds, true);
    }

    private void buildTomoePath(float r) {
        singleTomoePath.reset();
        // Classic Sharingan comma-head sitting on orbital ring (radius ~0.58*r) at top (0, -0.58*r)
        float ringR = r * 0.57f;
        float headR = r * 0.125f;
        float cx = 0f;
        float cy = -ringR;
        singleTomoePath.addCircle(cx, cy, headR, Path.Direction.CW);
        // Curved comma tail sweeping clockwise along orbital ring
        Path tail = new Path();
        tail.moveTo(cx + headR * 0.25f, cy - headR * 0.85f);
        tail.quadTo(cx + r * 0.28f, cy - r * 0.02f, cx + r * 0.14f, cy + r * 0.22f);
        tail.quadTo(cx + r * 0.11f, cy + r * 0.05f, cx - headR * 0.2f, cy + headR * 0.4f);
        tail.close();
        singleTomoePath.op(tail, Path.Op.UNION);
    }

    public void start() {
        active = true;
        scheduleNextBlink();
    }

    public void stop() {
        active = false;
        handler.removeCallbacks(blinkScheduler);
        if (gazeAnim != null) gazeAnim.cancel();
        if (spinAnim != null) spinAnim.cancel();
        if (blinkAnim != null) blinkAnim.cancel();
        blinkCloseFraction = 0f;
        wakePulseAlpha = 0f;
        invalidate();
    }

    public void close() {
        stop();
        if (bgBitmap != null && !bgBitmap.isRecycled()) {
            bgBitmap.recycle();
            bgBitmap = null;
        }
    }

    /** Moves Itachi's gaze smoothly toward the screen-space center of the currently focused UI element. */
    public void lookAt(float screenX, float screenY) {
        focusTargetX = screenX;
        focusTargetY = screenY;
        if (getWidth() <= 0 || getHeight() <= 0) return;
        updateGazeTargets();
        if (gazeAnim != null) gazeAnim.cancel();
        startLeftDx = curLeftDx;
        startLeftDy = curLeftDy;
        startRightDx = curRightDx;
        startRightDy = curRightDy;
        gazeAnim = ValueAnimator.ofFloat(0f, 1f);
        gazeAnim.setDuration(190L);
        gazeAnim.setInterpolator(new DecelerateInterpolator(1.6f));
        gazeAnim.addUpdateListener(anim -> {
            float f = (float) anim.getAnimatedValue();
            curLeftDx = startLeftDx + (targetLeftDx - startLeftDx) * f;
            curLeftDy = startLeftDy + (targetLeftDy - startLeftDy) * f;
            curRightDx = startRightDx + (targetRightDx - startRightDx) * f;
            curRightDy = startRightDy + (targetRightDy - startRightDy) * f;
            invalidate();
        });
        gazeAnim.start();
    }

    /** Plays the smooth Sharingan boot/wake spin + crimson glow pulse. */
    public void triggerWakeSpin() {
        if (spinAnim != null) spinAnim.cancel();
        final float startAngle = irisRotationDeg;
        spinAnim = ValueAnimator.ofFloat(0f, 1f);
        spinAnim.setDuration(1100L);
        spinAnim.setInterpolator(new DecelerateInterpolator(2.2f));
        spinAnim.addUpdateListener(anim -> {
            float f = (float) anim.getAnimatedValue();
            irisRotationDeg = EyeGazeMath.wrapDegrees(startAngle + 720f * f);
            // Pulse brightness peaks early on wake then settles softly
            wakePulseAlpha = (float) Math.sin(f * Math.PI);
            invalidate();
        });
        spinAnim.start();
    }

    /** Plays one natural eye blink (~165ms close-and-open). */
    public void triggerBlink() {
        if (blinkAnim != null && blinkAnim.isRunning()) return;
        blinkAnim = ValueAnimator.ofFloat(0f, 1f);
        blinkAnim.setDuration(165L);
        blinkAnim.addUpdateListener(anim -> {
            blinkCloseFraction = EyeGazeMath.blinkClosure((float) anim.getAnimatedValue());
            invalidate();
        });
        blinkAnim.start();
    }

    private void scheduleNextBlink() {
        handler.removeCallbacks(blinkScheduler);
        if (!active) return;
        long delayMs = 3600L + random.nextInt(3200);
        handler.postDelayed(blinkScheduler, delayMs);
    }

    private void updateGazeTargets() {
        EyeGazeMath.computeOffset(leftCx, leftCy, focusTargetX, focusTargetY,
                maxGazeX, maxGazeY, softRadiusPx, tmpOffset);
        targetLeftDx = tmpOffset[0];
        targetLeftDy = tmpOffset[1];
        EyeGazeMath.computeOffset(rightCx, rightCy, focusTargetX, focusTargetY,
                maxGazeX, maxGazeY, softRadiusPx, tmpOffset);
        targetRightDx = tmpOffset[0];
        targetRightDy = tmpOffset[1];
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bgBitmap != null && !bgBitmap.isRecycled()) {
            canvas.drawBitmap(bgBitmap, bgMatrix, bgPaint);
        } else {
            canvas.drawColor(0xFF04070D);
        }

        if (irisRadius > 0f) {
            drawEye(canvas, leftEyeClip, leftSocketBounds, leftCx + curLeftDx, leftCy + curLeftDy);
            drawEye(canvas, rightEyeClip, rightSocketBounds, rightCx + curRightDx, rightCy + curRightDy);
        }

        // Bottom dark atmospheric scrim keeps white logo icons high-contrast
        canvas.drawRect(0f, getHeight() * 0.60f, getWidth(), getHeight(), bottomScrimPaint);
    }

    private void drawEye(Canvas canvas, Path eyeClip, RectF bounds, float cx, float cy) {
        canvas.save();
        canvas.clipPath(eyeClip);

        // Uniform clean sclera base so iris never shows seams when sliding left/right
        canvas.drawRect(bounds, scleraPaint);

        // Crimson Sharingan bloom + spinning iris
        canvas.save();
        canvas.translate(cx, cy);
        redAuraPaint.setAlpha((int) (210 + 45 * wakePulseAlpha));
        canvas.drawCircle(0f, 0f, irisRadius * 1.65f, redAuraPaint);

        canvas.drawCircle(0f, 0f, irisRadius, irisPaint);
        canvas.drawCircle(0f, 0f, irisRadius - irisOuterRimPaint.getStrokeWidth() * 0.5f, irisOuterRimPaint);
        canvas.drawCircle(0f, 0f, irisRadius * 0.57f, ringPaint);

        canvas.save();
        canvas.rotate(irisRotationDeg);
        for (int i = 0; i < 3; i++) {
            canvas.drawPath(singleTomoePath, tomoePaint);
            canvas.rotate(120f);
        }
        canvas.restore();

        canvas.drawCircle(0f, 0f, irisRadius * 0.25f, pupilPaint);
        // Subtle anime glossy highlight at upper-left of iris
        canvas.drawCircle(-irisRadius * 0.34f, -irisRadius * 0.34f, Math.max(1.5f, irisRadius * 0.11f), catchlightPaint);
        canvas.restore();

        // Subtle inner upper-eyelid shadow for anime depth
        canvas.drawRect(bounds, eyeShadowPaint);

        // Eyelid blink shutter closing smoothly from top to bottom
        if (blinkCloseFraction > 0.005f) {
            float lidBottom = bounds.top + bounds.height() * blinkCloseFraction;
            blinkRectPath.reset();
            blinkRectPath.addRect(bounds.left - 2f, bounds.top - 2f, bounds.right + 2f, lidBottom, Path.Direction.CW);
            canvas.drawPath(blinkRectPath, eyelidFillPaint);
            canvas.drawLine(bounds.left, lidBottom, bounds.right, lidBottom, lashRimPaint);
        }
        canvas.restore();

        // Crisp lash border stroke on the outside edge of eye socket
        canvas.drawPath(eyeClip, lashRimPaint);
    }
}

