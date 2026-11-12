package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves crisp monochrome white logos for installed apps when white_logo_icons is active,
 * falling back to adaptive foreground or background-stripped monochrome silhouettes so no
 * opaque rectangular box appears.
 */
final class WhiteLogoHelper {

    private static final Map<String, Integer> BUNDLED_LOGOS = new HashMap<>();

    static {
        BUNDLED_LOGOS.put("com.google.android.youtube.tv", R.drawable.logo_youtube);
        BUNDLED_LOGOS.put("com.google.android.youtube.tvmusic", R.drawable.logo_youtube);
        BUNDLED_LOGOS.put("app.revanced.android.youtube", R.drawable.logo_youtube);
        BUNDLED_LOGOS.put("com.netflix.ninja", R.drawable.logo_netflix);
        BUNDLED_LOGOS.put("com.amazon.amazonvideo.livingroom", R.drawable.logo_prime);
        BUNDLED_LOGOS.put("in.startv.hotstar", R.drawable.logo_hotstar);
        BUNDLED_LOGOS.put("com.disney.disneyplus", R.drawable.logo_hotstar);
        BUNDLED_LOGOS.put("com.jio.media.jiotvplus", R.drawable.logo_jiotv);
        BUNDLED_LOGOS.put("com.android.vending", R.drawable.logo_playstore);
        BUNDLED_LOGOS.put("com.internet.tvbrowser", R.drawable.logo_browser);
        BUNDLED_LOGOS.put("org.videolan.vlc", R.drawable.logo_vlc);
        BUNDLED_LOGOS.put("com.mediatek.wwtv.mediaplayer", R.drawable.logo_mediaplayer);
        BUNDLED_LOGOS.put("com.mediatek.wwtv.tvcenter", R.drawable.logo_livetv);
        BUNDLED_LOGOS.put("com.mediatek.tvinput", R.drawable.logo_livetv);
        BUNDLED_LOGOS.put("com.google.android.tv", R.drawable.logo_livetv);
        BUNDLED_LOGOS.put("com.sony.dtv.tvx", R.drawable.logo_livetv);
        BUNDLED_LOGOS.put("com.esaba.downloader", R.drawable.logo_downloader);
        BUNDLED_LOGOS.put("org.localsend.localsend_app", R.drawable.logo_localsend);
        BUNDLED_LOGOS.put("app.netmirror.newtv", R.drawable.logo_netmirror);
        BUNDLED_LOGOS.put("com.spotify.tv.android", R.drawable.logo_spotify);
    }

    private WhiteLogoHelper() {
    }

    static Integer bundledRes(String pkg) {
        return pkg == null ? null : BUNDLED_LOGOS.get(pkg);
    }

    /** Returns a crisp white logo Drawable for pkg, extracting cleanly when no bundled vector exists. */
    static Drawable resolveLogo(Context ctx, String pkg, Drawable fallbackIcon, Drawable fallbackBanner) {
        Integer resId = bundledRes(pkg);
        if (resId != null) {
            return ctx.getDrawable(resId);
        }
        Drawable source = fallbackIcon != null ? fallbackIcon : fallbackBanner;
        if (source == null) return null;

        // API 26+: adaptive icon foreground contains the pure transparent-background emblem,
        // though some legacy/TV apps wrap an opaque fill inside foreground; strip corners if opaque.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source instanceof AdaptiveIconDrawable) {
            Drawable fg = ((AdaptiveIconDrawable) source).getForeground();
            if (fg != null) {
                Drawable strippedFg = stripBackgroundAndWhiten(ctx, fg, 96);
                if (strippedFg != null) return strippedFg;
                Drawable white = tintSolidWhite(ctx, fg, 96);
                if (white != null) return white;
            }
        }
        Drawable stripped = stripBackgroundAndWhiten(ctx, source, 96);
        return stripped != null ? stripped : tintSolidWhite(ctx, source, 96);
    }

    private static Drawable tintSolidWhite(Context ctx, Drawable d, int sizePx) {
        Bitmap bmp = renderToBitmap(d, sizePx);
        if (bmp == null) return null;
        Bitmap out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        c.drawBitmap(bmp, 0f, 0f, p);
        bmp.recycle();
        return new BitmapDrawable(ctx.getResources(), out);
    }

    /**
     * Strips uniform rectangular icon background if the icon's outer corners are solid opaque,
     * keeping foreground detail intact and turning it pure white.
     */
    private static Drawable stripBackgroundAndWhiten(Context ctx, Drawable d, int sizePx) {
        Bitmap bmp = renderToBitmap(d, sizePx);
        if (bmp == null) return null;
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        bmp.recycle();

        // Check corner opacity; if already transparent margins, whiten foreground alpha cleanly.
        int c0 = px[0], c1 = px[w - 1], c2 = px[(h - 1) * w], c3 = px[h * w - 1];
        boolean hasTransparentMargin = Color.alpha(c0) < 32 && Color.alpha(c1) < 32
                && Color.alpha(c2) < 32 && Color.alpha(c3) < 32;

        if (!hasTransparentMargin) {
            // Sample edge color (top-left pixel) and clear border-similar pixels
            int bgR = Color.red(c0), bgG = Color.green(c0), bgB = Color.blue(c0);
            int opaqueForeground = 0;
            for (int i = 0; i < px.length; i++) {
                int p = px[i];
                int a = Color.alpha(p);
                if (a < 24) {
                    px[i] = 0;
                    continue;
                }
                int diff = Math.abs(Color.red(p) - bgR)
                        + Math.abs(Color.green(p) - bgG)
                        + Math.abs(Color.blue(p) - bgB);
                if (diff < 72) {
                    px[i] = 0;
                } else {
                    float edgeFactor = Math.min(1f, (diff - 72) / 56f);
                    int outAlpha = Math.round(a * edgeFactor);
                    px[i] = (outAlpha << 24) | 0x00FFFFFF;
                    opaqueForeground++;
                }
            }
            // Guard against solid-color icons with almost zero distinct foreground
            if (opaqueForeground < (w * h) / 40) return null;
        } else {
            for (int i = 0; i < px.length; i++) {
                int a = Color.alpha(px[i]);
                px[i] = a > 12 ? ((a << 24) | 0x00FFFFFF) : 0;
            }
        }
        Bitmap out = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        return new BitmapDrawable(ctx.getResources(), out);
    }

    private static Bitmap renderToBitmap(Drawable d, int sizePx) {
        try {
            Drawable copy = d.getConstantState() != null ? d.getConstantState().newDrawable().mutate() : d;
            Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            copy.setBounds(0, 0, sizePx, sizePx);
            copy.draw(c);
            return bmp;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }
}

