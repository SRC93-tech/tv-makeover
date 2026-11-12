package com.gathin.tvhome;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;
import android.util.SparseBooleanArray;

/**
 * Interface sounds (config.xml, ui_sounds): res/raw/sound_startup with the boot splash, sound_move when the remote
 * moves focus, sound_select on OK. Decoded once into a SoundPool, so a key plays without touching the disk.
 */
final class UiSounds {

    /** A startup sound still loading when the splash shows plays only if it is ready this soon after. */
    private static final long STARTUP_LATE_MS = 1_500L;

    private final SoundPool pool;
    private final int startup, move, select;
    private final SparseBooleanArray loaded = new SparseBooleanArray();
    private long startupWanted;

    UiSounds(Context ctx) {
        pool = new SoundPool.Builder()
                .setMaxStreams(3)
                // Media, not sonification: TCL keeps the system stream at 0 on the TV's speakers, and the sounds
                // should follow the TV's volume like the rest of what it plays.
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        startup = pool.load(ctx, R.raw.sound_startup, 1);
        move = pool.load(ctx, R.raw.sound_move, 1);
        select = pool.load(ctx, R.raw.sound_select, 1);
        // Load results arrive on this (main) thread's looper, so none can come before the listener is set.
        pool.setOnLoadCompleteListener((p, id, status) -> {
            if (status != 0) return;
            loaded.put(id, true);
            if (id == startup && SystemClock.elapsedRealtime() - startupWanted < STARTUP_LATE_MS) play(id);
        });
    }

    void startup() {
        if (loaded.get(startup)) play(startup);
        else startupWanted = SystemClock.elapsedRealtime();
    }

    void move() {
        play(move);
    }

    void select() {
        play(select);
    }

    void release() {
        pool.release();
    }

    private void play(int id) {
        if (loaded.get(id)) pool.play(id, 1f, 1f, 1, 0, 1f);
    }
}
