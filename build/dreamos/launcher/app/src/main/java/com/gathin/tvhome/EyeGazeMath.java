package com.gathin.tvhome;

/**
 * Pure geometry and easing calculations for eye cursor tracking and natural blink animation.
 */
final class EyeGazeMath {
    private EyeGazeMath() {
    }

    /**
     * Computes clamped pixel offset (out[0]=dx, out[1]=dy) for an eye at (eyeX, eyeY) tracking a
     * focused UI target at (targetX, targetY), keeping iris movement smooth and bounded within the sclera.
     */
    static void computeOffset(float eyeX, float eyeY, float targetX, float targetY,
                              float maxOffsetX, float maxOffsetY, float softRadiusPx, float[] out) {
        float dx = targetX - eyeX;
        float dy = targetY - eyeY;
        float dist = (float) Math.hypot(dx, dy);
        if (dist < 1e-3f || softRadiusPx <= 0f) {
            out[0] = 0f;
            out[1] = 0f;
            return;
        }
        // Smooth asymptotic falloff (t in [0, 1)) so targets near edges give expressive gaze without clipping.
        float strength = dist / (dist + softRadiusPx);
        // Slightly boost normalized response so TV dock targets reach ~85% of full range cleanly.
        strength = Math.min(1f, strength * 1.18f);
        float ux = dx / dist;
        float uy = dy / dist;
        out[0] = ux * maxOffsetX * strength;
        out[1] = uy * maxOffsetY * strength;
    }

    /**
     * Maps normalized blink timeline t in [0, 1] -> eyelid closure fraction in [0, 1]:
     * rapid smooth close over [0..0.38], brief hold [0.38..0.48], crisp natural open [0.48..1.0].
     */
    static float blinkClosure(float t) {
        if (t <= 0f || t >= 1f) return 0f;
        if (t < 0.38f) {
            float p = t / 0.38f;
            return p * p * (3f - 2f * p);
        }
        if (t <= 0.48f) return 1f;
        float p = 1f - (t - 0.48f) / 0.52f;
        return p * p * (3f - 2f * p);
    }

    /** Normalizes any angle to [0, 360). */
    static float wrapDegrees(float deg) {
        float d = deg % 360f;
        return d < 0f ? d + 360f : d;
    }
}

