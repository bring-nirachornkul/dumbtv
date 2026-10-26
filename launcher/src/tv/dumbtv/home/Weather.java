package tv.dumbtv.home;

import android.os.Handler;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Options › Weather: the temperature beside the clock, for the city in Options › Weather city.
 * Open-Meteo answers with no key and no account: its geocoder turns the city into coordinates
 * once, then one forecast request at most every 30 minutes, and only while Home is on screen
 * with Weather turned on (Home calls refresh() from its minute tick, which stops when Home is
 * paused). The reading lives in memory only and is hidden once it is three hours old.
 */
final class Weather {
    private static final String GEOCODE = "https://geocoding-api.open-meteo.com/v1/search?count=1&format=json&name=";
    private static final String FORECAST = "https://api.open-meteo.com/v1/forecast?current=temperature_2m";
    static final long REFRESH_MS = 30 * 60 * 1000L;
    /** After a failed request, try again sooner than a full refresh. */
    static final long RETRY_MS = 5 * 60 * 1000L;
    static final long STALE_MS = 3 * 60 * 60 * 1000L;
    private static final int TIMEOUT_MS = 8000;

    private static volatile Integer degrees;
    private static volatile long readAt, nextTry;
    private static volatile boolean busy;
    /** City and unit the reading belongs to; another pair starts over. */
    private static volatile String forKey = "";
    /** The geocoder's coordinates for placeCity, kept while the city stays the same. */
    private static volatile double[] place;
    private static volatile String placeCity = "";

    private Weather() {}

    /** The last reading for city and unit, or null when there is none or it is too old. */
    static Integer degrees(String city, String unit) {
        Integer d = degrees;
        if (!key(city, unit).equals(forKey)) return null;
        return fresh(d, readAt, System.currentTimeMillis()) ? d : null;
    }

    static boolean fresh(Integer d, long readAt, long now) {
        return d != null && now - readAt < STALE_MS;
    }

    static String key(String city, String unit) {
        return city.trim().toLowerCase(java.util.Locale.ROOT) + "|" + unit;
    }

    /** Open-Meteo's temperature_unit for Options › Weather's value. */
    static String unitParam(String unit) {
        return "c".equals(unit) ? "celsius" : "fahrenheit";
    }

    /** Starts a request when one is due; done runs on handler's thread when it ends. */
    static void refresh(Handler handler, Runnable done, String city, String unit) {
        if (city.trim().isEmpty()) return;
        String k = key(city, unit);
        if (!k.equals(forKey)) {
            forKey = k;
            degrees = null;
            nextTry = 0;
            if (!city.trim().equalsIgnoreCase(placeCity)) place = null;
        }
        long now = System.currentTimeMillis();
        if (busy || now < nextTry) return;
        busy = true;
        new Thread(() -> {
            Integer d = fetch(city.trim(), unit);
            long at = System.currentTimeMillis();
            if (d != null && k.equals(forKey)) {
                degrees = d;
                readAt = at;
            }
            nextTry = at + (d != null ? REFRESH_MS : RETRY_MS);
            busy = false;
            handler.post(done);
        }, "weather").start();
    }

    private static Integer fetch(String city, String unit) {
        try {
            double[] p = place;
            if (p == null) {
                p = parsePlace(get(GEOCODE + URLEncoder.encode(city, "UTF-8")));
                if (p == null) return null;
                place = p;
                placeCity = city;
            }
            return parse(get(FORECAST + "&latitude=" + p[0] + "&longitude=" + p[1]
                    + "&temperature_unit=" + unitParam(unit)));
        } catch (Exception e) {
            return null;
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] buf = new byte[2048];
                for (int n; (n = in.read(buf)) > 0 && body.size() < 64 * 1024; ) body.write(buf, 0, n);
            }
            return body.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    /** {latitude, longitude} of the geocoder's first result, or null. */
    static double[] parsePlace(String json) {
        try {
            JSONArray results = new JSONObject(json).optJSONArray("results");
            if (results == null || results.length() == 0) return null;
            JSONObject r = results.getJSONObject(0);
            return new double[] {r.getDouble("latitude"), r.getDouble("longitude")};
        } catch (JSONException | NullPointerException e) {
            return null;
        }
    }

    /** current.temperature_2m from Open-Meteo's answer, rounded to a whole degree. */
    static Integer parse(String json) {
        try {
            double t = new JSONObject(json).getJSONObject("current").getDouble("temperature_2m");
            if (Double.isNaN(t) || t < -100 || t > 150) return null;
            return (int) Math.round(t);
        } catch (JSONException | NullPointerException e) {
            return null;
        }
    }
}
