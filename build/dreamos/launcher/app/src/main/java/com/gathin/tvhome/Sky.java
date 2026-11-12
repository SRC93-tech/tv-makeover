package com.gathin.tvhome;

/**
 * Time-of-day sky palette for the optional "sunset" backdrop (config.xml, sky_intensity).
 *
 * Pure maths, no Android types and no clock of its own: the caller passes minutes since midnight,
 * so the palette can be tested on the JVM. The colours are interpolated between a handful of
 * anchor points through the day, which is what makes the backdrop drift from night to dawn to
 * sunset instead of switching at fixed hours. Every colour stays dark enough for the white
 * text, the hero name and the dock band to keep their contrast.
 */
final class Sky {

    /** One anchor: a minute of the day and the values the palette takes exactly there. */
    static final class Stop {
        final int minute;
        final int top;
        final int mid;
        final int bottom;
        /** Alpha of the sun/moon glow near the horizon, 0 to 255. */
        final int sun;
        /** Horizontal position of the glow, 0 = left edge, 1 = right edge. */
        final float sunX;

        Stop(int minute, int top, int mid, int bottom, int sun, float sunX) {
            this.minute = minute;
            this.top = top;
            this.mid = mid;
            this.bottom = bottom;
            this.sun = sun;
            this.sunX = sunX;
        }
    }

    /** The palette at one instant: the three gradient colours plus the glow. */
    static final class Palette {
        final int top;
        final int mid;
        final int bottom;
        final int sun;
        final float sunX;

        Palette(int top, int mid, int bottom, int sun, float sunX) {
            this.top = top;
            this.mid = mid;
            this.bottom = bottom;
            this.sun = sun;
            this.sunX = sunX;
        }
    }

    private static final int NIGHT_TOP = 0xFF04060E;
    private static final int NIGHT_MID = 0xFF070C1A;
    private static final int NIGHT_BOTTOM = 0xFF0B1326;

    // Anchors were picked around a temperate evening: full night until half past five, dawn before
    // seven, the warmest glow between seven in the evening and a quarter to eight.
    private static final Stop[] STOPS = {
            new Stop(0, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
            new Stop(320, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
            new Stop(390, 0xFF160F2E, 0xFF3B2352, 0xFF8A4A6B, 100, 0.24f),
            new Stop(450, 0xFF12203F, 0xFF5C3A63, 0xFFD3674F, 160, 0.30f),
            new Stop(570, 0xFF0E1A33, 0xFF1F3557, 0xFF43648B, 90, 0.38f),
            new Stop(780, 0xFF0B1524, 0xFF17304F, 0xFF31587F, 60, 0.50f),
            new Stop(1020, 0xFF0D1526, 0xFF20395C, 0xFF4C5470, 90, 0.62f),
            new Stop(1140, 0xFF1B1030, 0xFF6C2E52, 0xFFE2704A, 190, 0.76f),
            new Stop(1185, 0xFF140C27, 0xFF4A2049, 0xFFA54A5C, 130, 0.82f),
            new Stop(1260, 0xFF0A0A1C, 0xFF191230, 0xFF3A1F49, 60, 0.86f),
            new Stop(1350, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
            new Stop(1440, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
    };

    private Sky() {
    }

    /** The palette at {@code minuteOfDay}, clamped to a day. */
    static Palette at(int minuteOfDay) {
        int m = Math.max(0, Math.min(1440, minuteOfDay));
        Stop a = STOPS[0];
        Stop b = STOPS[STOPS.length - 1];
        for (int i = 0; i < STOPS.length - 1; i++) {
            if (m >= STOPS[i].minute && m <= STOPS[i + 1].minute) {
                a = STOPS[i];
                b = STOPS[i + 1];
                break;
            }
        }
        float t = b.minute == a.minute ? 0f : (m - a.minute) / (float) (b.minute - a.minute);
        return new Palette(
                lerpColor(a.top, b.top, t),
                lerpColor(a.mid, b.mid, t),
                lerpColor(a.bottom, b.bottom, t),
                lerp(a.sun, b.sun, t),
                lerp(a.sunX, b.sunX, t));
    }

    /** Night overlay opacity (0..100) for auto day/night sunset mode based on local sunrise & sunset minutes. */
    static int solarIntensity(int minuteOfDay, int sunriseMin, int sunsetMin, String nightTheme) {
        if ("off".equals(nightTheme)) return 0;
        if ("night".equals(nightTheme)) return 82;
        int rise = sunriseMin >= 0 && sunriseMin < 1440 ? sunriseMin : 375;   // fallback 06:15
        int set = sunsetMin > rise && sunsetMin < 1440 ? sunsetMin : 1095;    // fallback 18:15
        int m = Math.max(0, Math.min(1440, minuteOfDay));
        int dawnStart = rise - 45;
        int dawnEnd = rise + 35;
        int duskStart = set - 45;
        int duskEnd = set + 35;
        if (m >= dawnEnd && m <= duskStart) return 0;
        if (m < dawnStart || m >= duskEnd) return 78;
        if (m < dawnEnd) {
            float t = (m - dawnStart) / (float) Math.max(1, dawnEnd - dawnStart);
            return Math.round(78f * (1f - t));
        }
        float t = (m - duskStart) / (float) Math.max(1, duskEnd - duskStart);
        return Math.round(78f * t);
    }

    /** Maps current time onto the sky stops using actual sunrise & sunset so dusk warm glow peaks at sunset. */
    static Palette atSolar(int minuteOfDay, int sunriseMin, int sunsetMin, String nightTheme) {
        if ("night".equals(nightTheme)) return at(0);
        int rise = sunriseMin >= 0 && sunriseMin < 1440 ? sunriseMin : 375;
        int set = sunsetMin > rise && sunsetMin < 1440 ? sunsetMin : 1095;
        int m = Math.max(0, Math.min(1440, minuteOfDay));
        int mapped;
        if (m < rise - 45 || m >= set + 35) {
            mapped = 0; // night indigo
        } else if (m <= rise + 35) {
            float t = (m - (rise - 45)) / 80f;
            mapped = Math.round(320 + t * 130f); // 320 -> 450 (dawn glow)
        } else if (m < set - 45) {
            float t = (m - (rise + 35)) / (float) Math.max(1, (set - 45) - (rise + 35));
            mapped = Math.round(450 + t * 570f); // 450 -> 1020 (day)
        } else {
            float t = (m - (set - 45)) / 80f;
            mapped = Math.round(1020 + t * 280f); // 1020 -> 1300 (warm sunset -> dusk night)
        }
        return at(mapped);
    }

    /** Minute of day, 0 to 1439, of a wall clock time. */
    static int minuteOfDay(int hour, int minute) {
        return Math.max(0, Math.min(23, hour)) * 60 + Math.max(0, Math.min(59, minute));
    }

    /** Blends two opaque ARGB colours channel by channel; {@code t} is 0 to 1. */
    static int lerpColor(int from, int to, float t) {
        float f = Math.max(0f, Math.min(1f, t));
        int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * f);
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * f);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * f);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerp(int from, int to, float t) {
        return Math.round(from + (to - from) * Math.max(0f, Math.min(1f, t)));
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * Math.max(0f, Math.min(1f, t));
    }
}
