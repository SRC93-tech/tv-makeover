package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Random;

/**
 * Full-screen ambient clock & weather view opened on Back from Home when back_opens_clock is enabled.
 * Any key returns cleanly to Home.
 */
public class ClockActivity extends Activity {

    private static final long MOVE_MS = 60_000L;
    private static final int[] PALETTE = {0xFF991B1B, 0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFD6336C};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private View content;
    private View dreamWeatherRow;
    private ImageView dreamWeatherIcon;
    private TextView dreamWeather;
    private Weather weather;
    private Config activeConfig;
    private ClockRefresh clockRefresh;
    private final Weather.Listener weatherListener = this::updateWeather;
    private GradientDrawable glow;
    private ValueAnimator glowAnim;
    private int colorIndex;
    private int glowColor;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            moveClock();
            shiftGlow();
            if (weather != null && activeConfig != null) weather.refreshIfStale(activeConfig);
            handler.postDelayed(this, MOVE_MS);
        }
    };

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dream_clock);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE);

        activeConfig = Config.load(this);
        ClockFormat.apply(getWindow().getDecorView(), activeConfig.clockFormat);
        content = findViewById(R.id.dreamContent);
        clockRefresh = new ClockRefresh(content);
        dreamWeatherRow = findViewById(R.id.dreamWeatherRow);
        dreamWeatherIcon = findViewById(R.id.dreamWeatherIcon);
        dreamWeather = findViewById(R.id.dreamWeather);
        weather = Weather.get(this);
        updateWeather();

        View ambient = findViewById(R.id.dreamAmbient);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambient.setBackground(glow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        colorIndex = 0;
        setGlow(PALETTE[colorIndex]);
    }

    @Override
    protected void onResume() {
        super.onResume();
        content.setAlpha(0f);
        if (weather != null && activeConfig != null) {
            weather.addListener(weatherListener);
            weather.refreshIfStale(activeConfig);
            updateWeather();
        }
        clockRefresh.start();
        content.post(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(800).start();
        });
        handler.postDelayed(tick, MOVE_MS);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        if (glowAnim != null) glowAnim.cancel();
        if (clockRefresh != null) clockRefresh.stop();
        if (weather != null) weather.removeListener(weatherListener);
        super.onPause();
        finish();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) finish();
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        finish();
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void updateWeather() {
        if (dreamWeatherRow == null || dreamWeather == null || activeConfig == null || weather == null) return;
        Weather.Data data = weather.current();
        if (!activeConfig.weatherEnabled || data == null) {
            dreamWeatherRow.setVisibility(View.GONE);
            return;
        }
        Calendar cal = Calendar.getInstance();
        int nowMin = Sky.minuteOfDay(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
        boolean isDay = Weather.isCurrentlyDay(data, nowMin);
        int icon = Weather.iconRes(data.code, isDay);
        if (dreamWeatherIcon != null) {
            if (icon != 0) {
                dreamWeatherIcon.setImageResource(icon);
                dreamWeatherIcon.setVisibility(View.VISIBLE);
            } else {
                dreamWeatherIcon.setVisibility(View.GONE);
            }
        }
        dreamWeather.setText(Weather.formatDreamLine(this, data, activeConfig.weatherUnit, isDay));
        dreamWeatherRow.setVisibility(View.VISIBLE);
    }

    private void moveClock() {
        content.animate().alpha(0f).setDuration(900).withEndAction(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        }).start();
    }

    private void placeClock() {
        View parent = (View) content.getParent();
        int maxX = Math.max(0, parent.getWidth() - content.getWidth());
        int maxY = Math.max(0, parent.getHeight() - content.getHeight());
        float x = maxX * (0.1f + 0.8f * random.nextFloat());
        float y = maxY * (0.1f + 0.8f * random.nextFloat());
        content.setX(x);
        content.setY(y);
        if (parent.getWidth() > 0 && parent.getHeight() > 0) {
            glow.setGradientCenter((x + content.getWidth() / 2f) / parent.getWidth(),
                    (y + content.getHeight() / 2f) / parent.getHeight());
        }
    }

    private void shiftGlow() {
        int from = glowColor;
        colorIndex = (colorIndex + 1) % PALETTE.length;
        glowAnim = ValueAnimator.ofObject(new ArgbEvaluator(), from, PALETTE[colorIndex]);
        glowAnim.setDuration(8000);
        glowAnim.addUpdateListener(a -> setGlow((int) a.getAnimatedValue()));
        glowAnim.start();
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}

