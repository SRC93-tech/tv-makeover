package com.gathin.tvhome;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextClock;

/**
 * Keeps TextClocks ticking reliably across Android TV firmware where ACTION_TIME_TICK can freeze
 * after standby on Android 8–11. On Android 12+, TextClock schedules its own updates.
 */
final class ClockRefresh implements Runnable, DisplayManager.DisplayListener {
    private static final long MARGIN_MS = 250L;

    private final View root;
    private final DisplayManager displays;
    private boolean on = true;

    ClockRefresh(View root) {
        this.root = root;
        displays = (DisplayManager) root.getContext().getSystemService(Context.DISPLAY_SERVICE);
    }

    void start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return;
        if (displays != null) displays.registerDisplayListener(this, null);
        schedule();
    }

    void stop() {
        root.removeCallbacks(this);
        if (displays != null) displays.unregisterDisplayListener(this);
    }

    @Override
    public void run() {
        redraw(root);
        schedule();
    }

    @Override
    public void onDisplayChanged(int displayId) {
        Display d = root.getDisplay();
        if (d == null || d.getDisplayId() != displayId) return;
        boolean nowOn = d.getState() == Display.STATE_ON;
        if (nowOn && !on) run();
        on = nowOn;
    }

    @Override
    public void onDisplayAdded(int displayId) {
    }

    @Override
    public void onDisplayRemoved(int displayId) {
    }

    private void schedule() {
        root.removeCallbacks(this);
        root.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L + MARGIN_MS);
    }

    private static void redraw(View v) {
        if (v instanceof TextClock) {
            TextClock c = (TextClock) v;
            c.setFormat12Hour(c.getFormat12Hour());
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) redraw(g.getChildAt(i));
        }
    }
}

