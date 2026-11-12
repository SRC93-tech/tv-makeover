package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Current weather and local sunrise/sunset from Open-Meteo (free, no key).
 * Cached in SharedPreferences so Home and ClockDream open with values immediately,
 * and refreshed every 30 minutes while Home is active.
 */
final class Weather {
    private static final String TAG = "TvHome.Weather";
    private static final String PREFS_CACHE = "weather_cache";
    private static final long REFRESH_EVERY_MS = 30 * 60_000L;
    private static final long AUTO_CITY_TTL_MS = 24 * 60 * 60_000L;
    private static final long FAIL_BACKOFF_MS = 2 * 60_000L;

    static final class Data {
        final String city;
        final double tempC;
        final double maxC;
        final double minC;
        final int code;
        final boolean isDay;
        final int sunriseMin;
        final int sunsetMin;

        Data(String city, double tempC, double maxC, double minC, int code, boolean isDay,
             int sunriseMin, int sunsetMin) {
            this.city = city == null ? "" : city;
            this.tempC = tempC;
            this.maxC = maxC;
            this.minC = minC;
            this.code = code;
            this.isDay = isDay;
            this.sunriseMin = sunriseMin;
            this.sunsetMin = sunsetMin;
        }
    }

    static final class CityResult {
        final String label;
        final String name;
        final String lat;
        final String lon;

        CityResult(String label, String name, String lat, String lon) {
            this.label = label;
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    interface Listener {
        void onWeatherUpdated();
    }

    private static Weather instance;

    static synchronized Weather get(Context ctx) {
        if (instance == null) instance = new Weather(ctx.getApplicationContext());
        return instance;
    }

    private final SharedPreferences cache;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private volatile Data current;
    private volatile long fetchedAt;
    private volatile long failedAt;
    private volatile String activeKey = "";
    private volatile boolean fetching;

    private Weather(Context ctx) {
        cache = ctx.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE);
        try {
            String json = cache.getString("forecast_json", null);
            String city = cache.getString("forecast_city", "");
            if (json != null) {
                current = parseForecast(json, city);
                fetchedAt = cache.getLong("forecast_at", 0L);
                activeKey = cache.getString("forecast_key", "");
            }
        } catch (Exception ignored) {
        }
    }

    Data current() {
        return current;
    }

    void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    synchronized void clearCache() {
        current = null;
        fetchedAt = 0L;
        failedAt = 0L;
        activeKey = "";
        cache.edit().clear().apply();
    }

    void refreshIfStale(Config cfg) {
        if (!cfg.weatherEnabled && !"auto".equals(cfg.nightTheme)) return;
        String cfgKey = targetKey(cfg);
        long now = System.currentTimeMillis();
        boolean keyChanged = !cfgKey.equals(activeKey);
        if (fetching) return;
        if (!keyChanged && current != null && now >= fetchedAt && (now - fetchedAt) < REFRESH_EVERY_MS) return;
        if (!keyChanged && failedAt != 0L && now >= failedAt && (now - failedAt) < FAIL_BACKOFF_MS) return;

        fetching = true;
        io.execute(() -> {
            try {
                String city = cfg.weatherCity;
                String lat = cfg.weatherLat;
                String lon = cfg.weatherLon;
                if (lat.isEmpty() || lon.isEmpty()) {
                    long geoAt = cache.getLong("auto_at", 0L);
                    String cachedLat = cache.getString("auto_lat", "");
                    String cachedLon = cache.getString("auto_lon", "");
                    String cachedCity = cache.getString("auto_city", "");
                    long t = System.currentTimeMillis();
                    if (!cachedLat.isEmpty() && !cachedLon.isEmpty() && t >= geoAt && (t - geoAt) < AUTO_CITY_TTL_MS) {
                        lat = cachedLat;
                        lon = cachedLon;
                        if (city.isEmpty()) city = cachedCity;
                    } else {
                        String[] geo = fetchGeoIp();
                        city = city.isEmpty() ? geo[0] : city;
                        lat = geo[1];
                        lon = geo[2];
                        cache.edit()
                                .putString("auto_city", geo[0])
                                .putString("auto_lat", lat)
                                .putString("auto_lon", lon)
                                .putLong("auto_at", System.currentTimeMillis())
                                .apply();
                    }
                }
                String forecastUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + lat
                        + "&longitude=" + lon
                        + "&current=temperature_2m,weather_code,is_day"
                        + "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset"
                        + "&timezone=auto&forecast_days=1";
                String body = httpGet(forecastUrl);
                Data parsed = parseForecast(body, city);
                long done = System.currentTimeMillis();
                current = parsed;
                fetchedAt = done;
                activeKey = cfgKey;
                failedAt = 0L;
                cache.edit()
                        .putString("forecast_json", body)
                        .putString("forecast_city", parsed.city)
                        .putLong("forecast_at", done)
                        .putString("forecast_key", cfgKey)
                        .apply();
                notifyListeners();
            } catch (Exception e) {
                failedAt = System.currentTimeMillis();
                Log.w(TAG, "weather fetch failed: " + e.getMessage());
            } finally {
                fetching = false;
            }
        });
    }

    private void notifyListeners() {
        main.post(() -> {
            for (Listener l : new ArrayList<>(listeners)) l.onWeatherUpdated();
        });
    }

    private static String targetKey(Config cfg) {
        return cfg.weatherCity + "|" + cfg.weatherLat + "," + cfg.weatherLon;
    }

    private static String[] fetchGeoIp() throws Exception {
        JSONObject o = new JSONObject(httpGet("https://get.geojs.io/v1/ip/geo.json"));
        String lat = o.optString("latitude", "").trim();
        String lon = o.optString("longitude", "").trim();
        String city = o.optString("city", "").trim();
        if (lat.isEmpty() || lon.isEmpty()) throw new IOException("No geolocation coordinates");
        return new String[]{city, lat, lon};
    }

    static List<CityResult> searchCity(String query) throws Exception {
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=8&format=json&language=en&name="
                + URLEncoder.encode(query.trim(), "UTF-8");
        return parseCitySearch(httpGet(url));
    }

    static List<CityResult> parseCitySearch(String body) throws Exception {
        JSONObject o = new JSONObject(body);
        JSONArray results = o.optJSONArray("results");
        List<CityResult> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; results != null && i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            String name = r.optString("name", "").trim();
            if (name.isEmpty() || !r.has("latitude") || !r.has("longitude")) continue;
            StringBuilder label = new StringBuilder(name);
            for (String part : new String[]{r.optString("admin1", ""), r.optString("country", "")}) {
                String p = part.trim();
                if (!p.isEmpty() && !p.equals(name)) label.append(", ").append(p);
            }
            if (!seen.add(label.toString())) continue;
            out.add(new CityResult(
                    label.toString(),
                    name,
                    String.format(Locale.US, "%.4f", r.getDouble("latitude")),
                    String.format(Locale.US, "%.4f", r.getDouble("longitude"))));
        }
        return out;
    }

    static Data parseForecast(String body, String cityLabel) throws Exception {
        JSONObject o = new JSONObject(body);
        JSONObject cur = o.getJSONObject("current");
        double temp = cur.getDouble("temperature_2m");
        int code = cur.getInt("weather_code");
        boolean isDay = cur.optInt("is_day", 1) == 1;
        JSONObject daily = o.optJSONObject("daily");
        double max = Double.NaN, min = Double.NaN;
        int riseMin = -1, setMin = -1;
        if (daily != null) {
            JSONArray maxArr = daily.optJSONArray("temperature_2m_max");
            JSONArray minArr = daily.optJSONArray("temperature_2m_min");
            if (maxArr != null && maxArr.length() > 0) max = maxArr.optDouble(0, Double.NaN);
            if (minArr != null && minArr.length() > 0) min = minArr.optDouble(0, Double.NaN);
            JSONArray riseArr = daily.optJSONArray("sunrise");
            JSONArray setArr = daily.optJSONArray("sunset");
            if (riseArr != null && riseArr.length() > 0) riseMin = parseIsoMinute(riseArr.optString(0, ""));
            if (setArr != null && setArr.length() > 0) setMin = parseIsoMinute(setArr.optString(0, ""));
        }
        return new Data(cityLabel, temp, max, min, code, isDay, riseMin, setMin);
    }

    /** Parses minute-of-day from Open-Meteo local ISO timestamp e.g. {@code 2026-11-12T06:24}. */
    static int parseIsoMinute(String iso) {
        if (iso == null) return -1;
        int t = iso.indexOf('T');
        if (t < 0 || t + 5 > iso.length()) return -1;
        try {
            int hh = Integer.parseInt(iso.substring(t + 1, t + 3));
            int mm = Integer.parseInt(iso.substring(t + 4, t + 6));
            return (hh >= 0 && hh <= 23 && mm >= 0 && mm <= 59) ? hh * 60 + mm : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static String formatTemp(double tempC, String unitPref) {
        if (Double.isNaN(tempC)) return "--°";
        // Many Android TV sets in metric regions use en_US as UI language; require a US/America timezone too for auto °F.
        boolean useF = "F".equalsIgnoreCase(unitPref)
                || ("auto".equalsIgnoreCase(unitPref)
                && "US".equals(Locale.getDefault().getCountry())
                && TimeZone.getDefault().getID().startsWith("America/"));
        double val = useF ? (tempC * 9.0 / 5.0 + 32.0) : tempC;
        return Math.round(val) + (useF ? "°F" : "°C");
    }

    static boolean isCurrentlyDay(Data d, int nowMinute) {
        if (d == null) return true;
        if (d.sunriseMin >= 0 && d.sunsetMin > d.sunriseMin) {
            return nowMinute >= d.sunriseMin && nowMinute < d.sunsetMin;
        }
        return d.isDay;
    }

    static int iconRes(int code, boolean isDay) {
        if (code == 0 || code == 1) {
            return isDay ? R.drawable.ic_weather_sun : R.drawable.ic_weather_moon;
        }
        if (code == 2) {
            return isDay ? R.drawable.ic_weather_partly_cloudy_day : R.drawable.ic_weather_partly_cloudy_night;
        }
        if (code == 3) return R.drawable.ic_weather_cloudy;
        if (code == 45 || code == 48) return R.drawable.ic_weather_fog;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.drawable.ic_weather_snow;
        if (code >= 95) return R.drawable.ic_weather_storm;
        if (code >= 0) return R.drawable.ic_weather_rain;
        return 0;
    }

    static String conditionText(Context ctx, int code, boolean isDay) {
        int res;
        if (code == 0) res = R.string.weather_clear;
        else if (code == 1) res = isDay ? R.string.weather_mostly_sunny : R.string.weather_mostly_clear;
        else if (code == 2) res = R.string.weather_partly_cloudy;
        else if (code == 3) res = R.string.weather_cloudy;
        else if (code == 45 || code == 48) res = R.string.weather_fog;
        else if (code >= 51 && code <= 57) res = R.string.weather_drizzle;
        else if (code == 65 || code == 67 || code == 82) res = R.string.weather_heavy_rain;
        else if ((code >= 71 && code <= 77) || code == 85 || code == 86) res = R.string.weather_snow;
        else if (code >= 95) res = R.string.weather_thunderstorm;
        else if (code >= 0) res = R.string.weather_rain;
        else return "";
        return ctx.getString(res);
    }

    static String formatHomeLine(Context ctx, Data d, String unitPref, boolean isDay) {
        if (d == null) return "";
        StringBuilder sb = new StringBuilder(formatTemp(d.tempC, unitPref));
        String cond = conditionText(ctx, d.code, isDay);
        if (!cond.isEmpty()) sb.append(' ').append(cond);
        if (!d.city.isEmpty()) sb.append("  ·  ").append(d.city);
        return sb.toString();
    }

    static String formatDreamLine(Context ctx, Data d, String unitPref, boolean isDay) {
        return formatHomeLine(ctx, d, unitPref, isDay);
    }

    private static String httpGet(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(6000);
        c.setRequestProperty("User-Agent", "DreamOS-Launcher");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            c.disconnect();
        }
    }
}

