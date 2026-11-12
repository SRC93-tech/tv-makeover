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

    // Calibrated 3/4 portrait normalized eye centers & iris scale on itachi_hero_bg.jpg (1920x1080)
    private static final float LEFT_EYE_NX = 0.347f;
    private static final float LEFT_EYE_NY = 0.449f;
    private static final float RIGHT_EYE_NX = 0.517f;
    private static final float RIGHT_EYE_NY = 0.405f;
    private static final float RIGHT_IRIS_RADIUS_N = 0.0176f; // ~33.8px at 1080p
    private static final float LEFT_IRIS_SCALE_Y = 0.82f;
    private static final float LEFT_IRIS_SCALE_X = 0.82f * 0.84f; // 3/4 head foreshortening

    private static final float MAX_GAZE_X_N = 0.0108f;  // ~20.7px horizontal travel
    private static final float MAX_GAZE_Y_N = 0.0112f;  // ~12.1px vertical travel (unmistakable upward glance at top Settings)

    private Bitmap bgBitmap;
    private final Matrix bgMatrix = new Matrix();
    private final Paint bgPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    private final Paint bottomScrimPaint = new Paint();
    private final Paint leftScleraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rightScleraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyeShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outerGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint redAuraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint irisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint irisOuterRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pupilPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tomoePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint catchlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyelidFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lashRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hairStrandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path leftEyeClip = new Path();
    private final Path rightEyeClip = new Path();
    private final Path blinkRectPath = new Path();
    private final Path singleTomoePath = new Path();
    private final RectF leftSocketBounds = new RectF();
    private final RectF rightSocketBounds = new RectF();

    private float leftCx, leftCy, rightCx, rightCy, irisRadius;
    private float maxGazeX, maxGazeY, softRadiusPx;
    private float hairStartX, hairStartY, hairEndX, hairEndY;

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
        irisOuterRimPaint.setColor(0xD9450308);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setColor(0xE01A0204);

        pupilPaint.setStyle(Paint.Style.FILL);
        pupilPaint.setColor(0xFF0A0203);

        tomoePaint.setStyle(Paint.Style.FILL);
        tomoePaint.setColor(0xF50A0102);

        catchlightPaint.setStyle(Paint.Style.FILL);
        catchlightPaint.setColor(0xF5FFFFFF);

        eyelidFillPaint.setStyle(Paint.Style.FILL);
        eyelidFillPaint.setColor(0xFF1A1416);

        lashRimPaint.setStyle(Paint.Style.STROKE);
        lashRimPaint.setStrokeCap(Paint.Cap.ROUND);
        lashRimPaint.setColor(0x66040508);

        hairStrandPaint.setStyle(Paint.Style.STROKE);
        hairStrandPaint.setStrokeCap(Paint.Cap.ROUND);
        hairStrandPaint.setColor(0xE0040508);
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
        irisRadius = Math.max(12f, RIGHT_IRIS_RADIUS_N * imgW);
        maxGazeX = MAX_GAZE_X_N * imgW;
        maxGazeY = MAX_GAZE_Y_N * imgH;
        softRadiusPx = Math.max(160f, w * 0.22f);

        hairStartX = ox + 0.495f * imgW;
        hairStartY = oy + 0.376f * imgH;
        hairEndX = ox + 0.501f * imgW;
        hairEndY = oy + 0.442f * imgH;
        hairStrandPaint.setStrokeWidth(Math.max(1.8f, imgScale * 2.2f));

        buildEyeSocketClip(leftEyeClip, leftSocketBounds, ox, oy, imgW, imgH, true);
        buildEyeSocketClip(rightEyeClip, rightSocketBounds, ox, oy, imgW, imgH, false);

        leftScleraPaint.setShader(new RadialGradient(
                leftSocketBounds.centerX(), leftSocketBounds.centerY() + leftSocketBounds.height() * 0.08f,
                Math.max(10f, leftSocketBounds.width() * 0.56f),
                new int[]{0xFF3A3D44, 0xFF272A30, 0xFF101216},
                new float[]{0.0f, 0.55f, 1.0f},
                Shader.TileMode.CLAMP));

        rightScleraPaint.setShader(new RadialGradient(
                rightSocketBounds.centerX() + rightSocketBounds.width() * 0.08f,
                rightSocketBounds.centerY() + rightSocketBounds.height() * 0.05f,
                Math.max(10f, rightSocketBounds.width() * 0.52f),
                new int[]{0xFF40444C, 0xFF282B31, 0xFF101216},
                new float[]{0.0f, 0.52f, 1.0f},
                Shader.TileMode.CLAMP));

        eyeShadowPaint.setShader(new LinearGradient(
                0f, Math.min(leftSocketBounds.top, rightSocketBounds.top),
                0f, Math.min(leftSocketBounds.top, rightSocketBounds.top) + irisRadius * 0.45f,
                new int[]{0x5C04060A, 0x0004060A},
                null,
                Shader.TileMode.CLAMP));

        outerGlowPaint.setShader(new RadialGradient(
                0f, 0f, irisRadius * 2.3f,
                new int[]{0xAAFF242E, 0x4CE11420, 0x00000000},
                new float[]{0.18f, 0.60f, 1f},
                Shader.TileMode.CLAMP));

        redAuraPaint.setShader(new RadialGradient(
                0f, 0f, irisRadius * 1.85f,
                new int[]{0xFFFF454B, 0xCCF51622, 0x48B91C1C, 0x00000000},
                new float[]{0.24f, 0.50f, 0.80f, 1f},
                Shader.TileMode.CLAMP));

        irisPaint.setShader(new RadialGradient(
                0f, 0f, irisRadius,
                new int[]{0xFFFFC4C4, 0xFFFF4545, 0xFFFF1824, 0xFFA60812},
                new float[]{0.08f, 0.40f, 0.80f, 1f},
                Shader.TileMode.CLAMP));

        bottomScrimPaint.setShader(new LinearGradient(
                0f, h * 0.65f, 0f, h,
                new int[]{0x00000000, 0x4D020306, 0xBA020306},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));

        irisOuterRimPaint.setStrokeWidth(Math.max(1.4f, irisRadius * 0.075f));
        ringPaint.setStrokeWidth(Math.max(1.2f, irisRadius * 0.055f));
        lashRimPaint.setStrokeWidth(Math.max(1.4f, irisRadius * 0.06f));

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
            float x0 = ox + 0.319f * imgW, y0 = oy + 0.431f * imgH;
            out.moveTo(x0, y0);
            out.quadTo(ox + 0.339f * imgW, oy + 0.424f * imgH, ox + 0.354f * imgW, oy + 0.433f * imgH);
            out.quadTo(ox + 0.365f * imgW, oy + 0.446f * imgH, ox + 0.371f * imgW, oy + 0.463f * imgH);
            out.quadTo(ox + 0.354f * imgW, oy + 0.473f * imgH, ox + 0.336f * imgW, oy + 0.469f * imgH);
            out.quadTo(ox + 0.324f * imgW, oy + 0.454f * imgH, x0, y0);
            out.close();
        } else {
            float x0 = ox + 0.471f * imgW, y0 = oy + 0.420f * imgH;
            out.moveTo(x0, y0);
            out.quadTo(ox + 0.495f * imgW, oy + 0.391f * imgH, ox + 0.518f * imgW, oy + 0.382f * imgH);
            out.quadTo(ox + 0.539f * imgW, oy + 0.384f * imgH, ox + 0.549f * imgW, oy + 0.400f * imgH);
            out.quadTo(ox + 0.538f * imgW, oy + 0.431f * imgH, ox + 0.514f * imgW, oy + 0.437f * imgH);
            out.quadTo(ox + 0.489f * imgW, oy + 0.435f * imgH, x0, y0);
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
                maxGazeX * 0.72f, maxGazeY * 0.78f, softRadiusPx, tmpOffset);
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
            drawEye(canvas, leftEyeClip, leftSocketBounds, leftScleraPaint,
                    leftCx + curLeftDx, leftCy + curLeftDy,
                    LEFT_IRIS_SCALE_X, LEFT_IRIS_SCALE_Y, false);
            drawEye(canvas, rightEyeClip, rightSocketBounds, rightScleraPaint,
                    rightCx + curRightDx, rightCy + curRightDy,
                    1f, 1f, true);
        }

        // Bottom dark atmospheric scrim keeps white logo icons high-contrast
        canvas.drawRect(0f, getHeight() * 0.65f, getWidth(), getHeight(), bottomScrimPaint);
    }

    private void drawEye(Canvas canvas, Path eyeClip, RectF bounds, Paint socketScleraPaint,
                         float cx, float cy, float irisScaleX, float irisScaleY, boolean drawHairOver) {
        float openFraction = Math.max(0f, 1f - blinkCloseFraction);

        canvas.save();
        canvas.clipPath(eyeClip);

        // Shaded radial sclera melts into inner tear-duct & outer corner shadows seamlessly
        canvas.drawRect(bounds, socketScleraPaint);

        // Crimson Sharingan bloom + spinning iris
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(irisScaleX, irisScaleY);
        redAuraPaint.setAlpha((int) (235 + 20 * wakePulseAlpha));
        canvas.drawCircle(0f, 0f, irisRadius * 1.85f, redAuraPaint);

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
        // Crisp anime glossy highlights at upper-left + subtle secondary gleam at lower-right
        canvas.drawCircle(-irisRadius * 0.34f, -irisRadius * 0.34f, Math.max(1.8f, irisRadius * 0.12f), catchlightPaint);
        canvas.drawCircle(irisRadius * 0.26f, irisRadius * 0.30f, Math.max(1.0f, irisRadius * 0.06f), catchlightPaint);
        canvas.restore();

        // Fine wet foreground hair strand crossing inner sclera of the right eye
        if (drawHairOver) {
            canvas.drawLine(hairStartX, hairStartY, hairEndX, hairEndY, hairStrandPaint);
        }

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

        // Soft luminous crimson aura spilling gently across iris, lashes, and upper shadow
        if (openFraction > 0.02f) {
            canvas.save();
            canvas.translate(cx, cy);
            canvas.scale(irisScaleX, irisScaleY);
            outerGlowPaint.setAlpha((int) ((135 + 85 * wakePulseAlpha) * openFraction));
            canvas.drawCircle(0f, 0f, irisRadius * 2.25f, outerGlowPaint);
            canvas.restore();
        }
    }
}

