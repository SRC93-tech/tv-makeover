package com.gathin.tvhome;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextClock;

/** Applies configured 12h / 24h mode consistently across Home and screensaver clocks. */
final class ClockFormat {
    private ClockFormat() {
    }

    static void apply(View root, String mode) {
        if (!"12".equals(mode) && !"24".equals(mode)) return;
        if (root instanceof TextClock) {
            TextClock c = (TextClock) root;
            if ("12".equals(mode)) c.setFormat24Hour(c.getFormat12Hour());
            else c.setFormat12Hour(c.getFormat24Hour());
        } else if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) apply(g.getChildAt(i), mode);
        }
    }

    static boolean is24(Context ctx, String mode) {
        return "24".equals(mode) || (!"12".equals(mode) && DateFormat.is24HourFormat(ctx));
    }
}

