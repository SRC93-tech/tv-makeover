package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executors;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private TextView wallpaperChoice;
    private TextView weatherCityChoice;
    private TextView weatherUnitChoice;
    private TextView nightThemeChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private int columns;
    private int rowsBelowDock;
    private boolean weatherEnabled;
    private String weatherCity;
    private String weatherLat;
    private String weatherUnit;
    private String nightTheme;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        weatherEnabled = config.weatherEnabled;
        weatherCity = config.weatherCity;
        weatherLat = config.weatherLat;
        weatherUnit = config.weatherUnit;
        nightTheme = config.nightTheme;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        weatherCityChoice = findViewById(R.id.weatherCityChoice);
        weatherCityChoice.setOnClickListener(v -> showCityPicker());
        weatherUnitChoice = findViewById(R.id.weatherUnitChoice);
        weatherUnitChoice.setOnClickListener(v -> cycleWeatherUnit());
        nightThemeChoice = findViewById(R.id.nightThemeChoice);
        nightThemeChoice.setOnClickListener(v -> cycleNightTheme());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            setResult(RESULT_OK);
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove("weather_enabled").remove("weather_city").remove("weather_lat")
                    .remove("weather_lon").remove("weather_unit").remove("night_theme").commit();
            Weather.get(this).clearCache();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            weatherEnabled = current.weatherEnabled;
            weatherCity = current.weatherCity;
            weatherLat = current.weatherLat;
            weatherUnit = current.weatherUnit;
            nightTheme = current.nightTheme;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            setResult(RESULT_OK);
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_SETTINGS)));
        refresh();
        columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size()
                : indexOf(images, current);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                    }
                    setResult(RESULT_OK);
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    setResult(RESULT_OK);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        setResult(RESULT_OK);
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        String cityState = !weatherEnabled ? getString(R.string.off)
                : !weatherCity.isEmpty() ? weatherCity
                : getString(R.string.weather_auto);
        weatherCityChoice.setText(getString(R.string.weather_city_state, cityState));
        String unitState = "C".equals(weatherUnit) ? getString(R.string.weather_unit_c)
                : "F".equals(weatherUnit) ? getString(R.string.weather_unit_f)
                : getString(R.string.weather_unit_auto);
        weatherUnitChoice.setText(getString(R.string.weather_unit_state, unitState));
        weatherUnitChoice.setEnabled(weatherEnabled);
        weatherUnitChoice.setAlpha(weatherEnabled ? 1f : 0.5f);
        String nightState = "night".equals(nightTheme) ? getString(R.string.night_theme_always)
                : "off".equals(nightTheme) ? getString(R.string.off)
                : getString(R.string.night_theme_auto);
        nightThemeChoice.setText(getString(R.string.night_theme_state, nightState));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private void showCityPicker() {
        String[] options = {
                getString(R.string.weather_auto),
                getString(R.string.weather_search_action),
                getString(R.string.off)
        };
        int checked = !weatherEnabled ? 2 : (weatherLat.isEmpty() && weatherCity.isEmpty() ? 0 : 1);
        new AlertDialog.Builder(this)
                .setTitle(R.string.weather_search_title)
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) {
                        weatherEnabled = true;
                        weatherCity = "";
                        weatherLat = "";
                        choices.edit().putBoolean("weather_enabled", true)
                                .putString("weather_city", "")
                                .putString("weather_lat", "")
                                .putString("weather_lon", "")
                                .apply();
                        Weather.get(this).clearCache();
                        setResult(RESULT_OK);
                        refresh();
                    } else if (which == 1) {
                        promptCityQuery();
                    } else {
                        weatherEnabled = false;
                        choices.edit().putBoolean("weather_enabled", false).apply();
                        setResult(RESULT_OK);
                        refresh();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void promptCityQuery() {
        EditText input = new EditText(this);
        input.setHint(R.string.weather_search_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        if (!weatherCity.isEmpty()) input.setText(weatherCity);
        new AlertDialog.Builder(this)
                .setTitle(R.string.weather_search_title)
                .setView(input)
                .setPositiveButton(R.string.weather_search, (dialog, which) -> {
                    String q = input.getText().toString().trim();
                    if (!q.isEmpty()) runCitySearch(q);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void runCitySearch(String query) {
        Toast.makeText(this, R.string.weather_searching, Toast.LENGTH_SHORT).show();
        Handler main = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                List<Weather.CityResult> results = Weather.searchCity(query);
                main.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (results.isEmpty()) {
                        Toast.makeText(this, R.string.weather_no_results, Toast.LENGTH_LONG).show();
                        return;
                    }
                    String[] labels = new String[results.size()];
                    for (int i = 0; i < results.size(); i++) labels[i] = results.get(i).label;
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.weather_search_title)
                            .setItems(labels, (d, idx) -> {
                                Weather.CityResult picked = results.get(idx);
                                weatherEnabled = true;
                                weatherCity = picked.name;
                                weatherLat = picked.lat;
                                choices.edit()
                                        .putBoolean("weather_enabled", true)
                                        .putString("weather_city", picked.name)
                                        .putString("weather_lat", picked.lat)
                                        .putString("weather_lon", picked.lon)
                                        .apply();
                                Weather.get(this).clearCache();
                                setResult(RESULT_OK);
                                refresh();
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(this, R.string.weather_search_error, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void cycleWeatherUnit() {
        weatherUnit = "auto".equals(weatherUnit) ? "C" : "C".equals(weatherUnit) ? "F" : "auto";
        choices.edit().putString("weather_unit", weatherUnit).apply();
        setResult(RESULT_OK);
        refresh();
    }

    private void cycleNightTheme() {
        nightTheme = "auto".equals(nightTheme) ? "night" : "night".equals(nightTheme) ? "off" : "auto";
        choices.edit().putString("night_theme", nightTheme).apply();
        setResult(RESULT_OK);
        refresh();
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY)
                || choices.contains("weather_enabled") || choices.contains("weather_city")
                || choices.contains("weather_unit") || choices.contains("night_theme");
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
