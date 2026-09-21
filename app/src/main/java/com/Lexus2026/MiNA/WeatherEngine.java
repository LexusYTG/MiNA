package com.Lexus2026.MiNA;

import android.content.Context;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

class WeatherEngine {

    static class Weather {
        double tempC;
        double feelsC;
        int    humidity;
        double windKmh;
        int    code;
        String city;

        String describe() {
            switch (code) {
                case 0:  return Lang.get(270);
                case 1:  return Lang.get(271);
                case 2:  return Lang.get(272);
                case 3:  return Lang.get(273);
                case 45:
                case 48: return Lang.get(274);
                case 51:
                case 53:
                case 55:
                case 56:
                case 57: return Lang.get(275);
                case 61:
                case 63:
                case 65:
                case 66:
                case 67: return Lang.get(276);
                case 71:
                case 73:
                case 75:
                case 77: return Lang.get(277);
                case 80:
                case 81:
                case 82: return Lang.get(278);
                case 85:
                case 86: return Lang.get(279);
                case 95:
                case 96:
                case 99: return Lang.get(280);
                default: return Lang.get(281);
            }
        }
    }

    private final Context ctx;

    WeatherEngine(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    Weather currentForCity(String city) {
        double[] coords = geocode(city);
        if (coords == null) return null;
        Weather w = currentForCoords(coords[0], coords[1]);
        if (w != null) w.city = prettyCity(city);
        return w;
    }

    Weather currentForLocation() {
        double[] c = lastLocation();
        if (c == null) return null;
        Weather w = currentForCoords(c[0], c[1]);
        if (w != null) w.city = Lang.get(282);
        return w;
    }

    Weather forecastTomorrow(String city) {
        double[] coords = geocode(city);
        if (coords == null) return null;
        return forecastDay(coords[0], coords[1], 1);
    }

    Weather forecastTomorrowForLocation() {
        double[] c = lastLocation();
        if (c == null) return null;
        return forecastDay(c[0], c[1], 1);
    }

    private Weather currentForCoords(double lat, double lon) {
        try {
            String lang = Lang.getActiveLanguage();
            String url = "https://api.open-meteo.com/v1/forecast"
                + "?latitude=" + lat
                + "&longitude=" + lon
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m"
                + "&timezone=auto";
            String body = get(url);
            if (body == null) return null;
            JSONObject root = new JSONObject(body);
            JSONObject cur = root.optJSONObject("current");
            if (cur == null) return null;

            Weather w = new Weather();
            w.tempC    = cur.optDouble("temperature_2m", 0);
            w.feelsC   = cur.optDouble("apparent_temperature", w.tempC);
            w.humidity = cur.optInt("relative_humidity_2m", 0);
            w.windKmh  = cur.optDouble("wind_speed_10m", 0);
            w.code     = cur.optInt("weather_code", -1);
            return w;
        } catch (Exception e) {
            return null;
        }
    }

    private Weather forecastDay(double lat, double lon, int dayOffset) {
        try {
            String url = "https://api.open-meteo.com/v1/forecast"
                + "?latitude=" + lat
                + "&longitude=" + lon
                + "&daily=temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max,wind_speed_10m_max"
                + "&forecast_days=" + (dayOffset + 1)
                + "&timezone=auto";
            String body = get(url);
            if (body == null) return null;
            JSONObject root = new JSONObject(body);
            JSONObject daily = root.optJSONObject("daily");
            if (daily == null) return null;

            JSONArray tmax = daily.optJSONArray("temperature_2m_max");
            JSONArray tmin = daily.optJSONArray("temperature_2m_min");
            JSONArray codes = daily.optJSONArray("weather_code");
            if (tmax == null || tmin == null || codes == null) return null;
            if (dayOffset >= tmax.length()) return null;

            Weather w = new Weather();
            w.tempC  = tmax.optDouble(dayOffset, 0);
            w.feelsC = tmin.optDouble(dayOffset, 0);
            w.code   = codes.optInt(dayOffset, -1);
            return w;
        } catch (Exception e) {
            return null;
        }
    }

    private double[] geocode(String city) {
        if (city == null || city.isEmpty()) return null;

        String limpio = city.replace(",", " ")
			.replace(".", " ")
			.replace("(", " ")
			.replace(")", " ")
			.replaceAll("\\s+", " ")
			.trim();
        if (limpio.isEmpty()) return null;

        List<String> candidatos = new ArrayList<String>();
        candidatos.add(limpio);

        String sinRuido = limpio;
        String[] ruido = Lang.getStopWords();
        for (String r : ruido) {
            if (r.isEmpty()) continue;
            sinRuido = sinRuido.replaceAll("(?i)\\b" + Pattern.quote(r) + "\\b", " ");
        }
        sinRuido = sinRuido.replaceAll("\\s+", " ").trim();
        if (!sinRuido.isEmpty() && !candidatos.contains(sinRuido)) candidatos.add(sinRuido);

        String[] palabras = limpio.split(" ");
        if (palabras.length >= 4) {
            String v = palabras[0]+" "+palabras[1]+" "+palabras[2]+" "+palabras[3];
            if (!candidatos.contains(v)) candidatos.add(v);
        }
        if (palabras.length >= 3) {
            String v = palabras[0]+" "+palabras[1]+" "+palabras[2];
            if (!candidatos.contains(v)) candidatos.add(v);
        }
        if (palabras.length >= 2) {
            String v = palabras[0]+" "+palabras[1];
            if (!candidatos.contains(v)) candidatos.add(v);
        }
        if (palabras.length >= 1) {
            String v = palabras[0];
            if (!candidatos.contains(v)) candidatos.add(v);
        }

        for (String cand : candidatos) {
            double[] r = geocodeOpenMeteo(cand);
            if (r != null) return r;
        }

        for (String cand : candidatos) {
            double[] r = geocodeAndroid(cand);
            if (r != null) return r;
        }

        return null;
    }

    private double[] geocodeOpenMeteo(String city) {
        if (city == null || city.isEmpty()) return null;
        HttpURLConnection c = null;
        try {
            String lang = Lang.getActiveLanguage();
            String url = "https://geocoding-api.open-meteo.com/v1/search"
                + "?name=" + URLEncoder.encode(city, "UTF-8")
                + "&count=1&language=" + URLEncoder.encode(lang, "UTF-8")
                + "&format=json";
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", "MiNAApp/1.0");
            c.connect();
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            BufferedReader r = new BufferedReader(
                new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            r.close();
            String body = sb.toString();
            JSONObject root = new JSONObject(body);
            JSONArray results = root.optJSONArray("results");
            if (results == null || results.length() == 0) return null;
            JSONObject first = results.getJSONObject(0);
            return new double[]{
                first.optDouble("latitude", 0),
                first.optDouble("longitude", 0)
            };
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private double[] geocodeAndroid(String city) {
        if (city == null || city.isEmpty()) return null;
        try {
            Geocoder gc = new Geocoder(ctx, Locale.getDefault());
            List<Address> list = gc.getFromLocationName(city, 1);
            if (list == null || list.isEmpty()) return null;
            Address a = list.get(0);
            if (!a.hasLatitude() || !a.hasLongitude()) return null;
            return new double[]{a.getLatitude(), a.getLongitude()};
        } catch (Exception e) {
            return null;
        }
    }

    private static final int LOCATION_TIMEOUT_MS = 8000;

    private double[] lastLocation() {
        try {
            LocationManager lm = (LocationManager)
                ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return null;

            Location best = null;
            List<String> providers = lm.getProviders(true);
            if (providers == null || providers.isEmpty()) return null;

            for (String p : providers) {
                try {
                    Location l = lm.getLastKnownLocation(p);
                    if (l == null) continue;
                    if (best == null || l.getTime() > best.getTime()) best = l;
                } catch (SecurityException ignored) {}
            }
            if (best != null) return new double[]{best.getLatitude(), best.getLongitude()};

            final Object lock = new Object();
            final Location[] result = {null};

            LocationListener oneShot = new LocationListener() {
                @Override public void onLocationChanged(Location loc) {
                    synchronized (lock) {
                        result[0] = loc;
                        lock.notifyAll();
                    }
                }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            };

            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_COARSE);
            criteria.setPowerRequirement(Criteria.POWER_LOW);
            String provider = lm.getBestProvider(criteria, true);
            if (provider == null) return null;

            try {
                lm.requestSingleUpdate(provider, oneShot, Looper.getMainLooper());
            } catch (SecurityException e) {
                return null;
            }

            synchronized (lock) {
                if (result[0] == null) {
                    try { lock.wait(LOCATION_TIMEOUT_MS); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }

            try { lm.removeUpdates(oneShot); } catch (Exception ignored) {}

            if (result[0] == null) return null;
            return new double[]{result[0].getLatitude(), result[0].getLongitude()};

        } catch (Exception e) {
            return null;
        }
    }

    private String prettyCity(String raw) {
        if (raw == null) return Lang.get(292);
        String t = raw.trim();
        if (t.isEmpty()) return Lang.get(292);
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == ' ') { sb.append(c); cap = true; }
            else if (cap) { sb.append(Character.toUpperCase(c)); cap = false; }
            else sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private String get(String urlStr) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", "MiNAApp/1.0");
            c.connect();
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            BufferedReader r = new BufferedReader(
                new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            r.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static String fmtTemp(double c) {
        return String.format(Locale.getDefault(), "%.0f", c);
    }
}
