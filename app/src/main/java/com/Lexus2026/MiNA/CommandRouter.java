package com.Lexus2026.MiNA;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.KeyEvent;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

class CommandRouter {

    interface Callback {
        void onResult(Response r);
    }

    static class Response {
        final String text;
        final Intent intent;
        final boolean closeAfter;
        private Response(String text, Intent intent, boolean closeAfter) {
            this.text = text;
            this.intent = intent;
            this.closeAfter = closeAfter;
        }
        static Response speak(String t)          { return new Response(t, null, false); }
        static Response open(String t, Intent i) { return new Response(t, i, true); }
        static Response close(String t)          { return new Response(t, null, true); }
    }

    private static final int KEYCODE_MEDIA_PAUSE    = 127;
    private static final int KEYCODE_MEDIA_PLAY     = 126;
    private static final int KEYCODE_MEDIA_NEXT     = 87;
    private static final int KEYCODE_MEDIA_PREVIOUS = 88;

    private final Context ctx;
    private final WeatherEngine weather;
    private final NlpEngine nlp;
    private static boolean torchOn = false;
    private final Random rnd = new Random();
    private final Handler main = new Handler(Looper.getMainLooper());

    private static final java.util.HashMap<Character, String> MORSE =
	new java.util.HashMap<Character, String>();
    static {
        MORSE.put('A', ".-");    MORSE.put('B', "-...");  MORSE.put('C', "-.-.");
        MORSE.put('D', "-..");   MORSE.put('E', ".");     MORSE.put('F', "..-.");
        MORSE.put('G', "--.");   MORSE.put('H', "....");  MORSE.put('I', "..");
        MORSE.put('J', ".---");  MORSE.put('K', "-.-");   MORSE.put('L', ".-..");
        MORSE.put('M', "--");    MORSE.put('N', "-.");    MORSE.put('O', "---");
        MORSE.put('P', ".--.");  MORSE.put('Q', "--.-");  MORSE.put('R', ".-.");
        MORSE.put('S', "...");   MORSE.put('T', "-");     MORSE.put('U', "..-");
        MORSE.put('V', "...-");  MORSE.put('W', ".--");   MORSE.put('X', "-..-");
        MORSE.put('Y', "-.--");  MORSE.put('Z', "--..");
        MORSE.put('0', "-----"); MORSE.put('1', ".----"); MORSE.put('2', "..---");
        MORSE.put('3', "...--"); MORSE.put('4', "....-"); MORSE.put('5', ".....");
        MORSE.put('6', "-...."); MORSE.put('7', "--..."); MORSE.put('8', "---..");
        MORSE.put('9', "----.");
    }

    CommandRouter(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.weather = new WeatherEngine(ctx);
        this.nlp = new NlpEngine();
    }

    Response route(String raw) {
        if (raw == null) return Response.speak(Lang.get(48));
        String q = raw.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) return Response.speak(Lang.get(48));

        Lang.IntentMatch m = Lang.matchIntent(q);
        if (m != null) {
            Response r = dispatch(m, q);
            if (r != null) return r;
        }

        // Fallback: motor NLP básico
        NlpEngine.Result nr = nlp.analyze(q);
        if (nr.confidence >= NlpEngine.T_CONFIDENT) {
            Lang.IntentMatch guess = nlp.toIntentMatch(nr);
            if (guess != null) {
                Response r = dispatch(guess, q);
                if (r != null) return r;
            }
        }
        if (nr.confidence >= NlpEngine.T_HINT) {
            return Response.speak(nlp.formulateHint(nr));
        }
        return Response.speak(nlp.formulateUnknown(q));
    }

    void routeAsync(final String raw, final Callback cb) {
        new Thread(new Runnable() {
				@Override public void run() {
					final Response r = route(raw);
					main.post(new Runnable() {
							@Override public void run() { cb.onResult(r); }
						});
				}
			}, "CommandRouter").start();
    }

    private Response dispatch(Lang.IntentMatch m, String q) {
        String id = m.id;

        if ("weather_city".equals(id))       return weatherCity(m.params.get("city"));
        if ("weather_here".equals(id))       return weatherHere();
        if ("forecast_city".equals(id))      return forecastCity(m.params.get("city"));
        if ("forecast_here".equals(id))      return forecastHere();

        if ("greeting".equals(id))       return Response.speak(Lang.get(49));
        if ("ask_name".equals(id))       return Response.speak(Lang.get(50));
        if ("thanks".equals(id))         return Response.speak(Lang.get(51));
        if ("goodbye".equals(id))        return Response.close(Lang.get(52));
        if ("help".equals(id))           return Response.speak(Lang.get(87));
        if ("how_are_you".equals(id))    return Response.speak(pick(Lang.get(146), Lang.get(147), Lang.get(148)));
        if ("what_doing".equals(id))     return Response.speak(Lang.get(149));
        if ("love_you".equals(id))       return Response.speak(Lang.get(150));
        if ("insult".equals(id))         return Response.speak(Lang.get(151));
        if ("good_morning".equals(id))   return Response.speak(Lang.get(152));
        if ("good_night".equals(id))     return Response.speak(Lang.get(153));
        if ("tell_fact".equals(id))      return Response.speak(pick(Lang.get(154), Lang.get(155), Lang.get(156)));
        if ("tell_joke".equals(id))      return Response.speak(pick(Lang.get(157), Lang.get(158), Lang.get(159), Lang.get(160)));
        if ("are_you_ai".equals(id))     return Response.speak(Lang.get(161));
        if ("random_fact".equals(id))    return Response.speak(pick(Lang.get(213), Lang.get(214), Lang.get(215), Lang.get(216), Lang.get(217)));

        if ("who_are_you".equals(id))       return Response.speak(Lang.get(376));
        if ("what_do_you_like".equals(id))  return Response.speak(Lang.get(377));
        if ("are_you_hungry".equals(id))    return Response.speak(Lang.get(378));
        if ("what_do_you_eat".equals(id))   return Response.speak(Lang.get(379));
        if ("are_you_a_cat".equals(id))     return Response.speak(Lang.get(380));
        if ("say_meow".equals(id))          return Response.speak(Lang.get(381));

        if ("time".equals(id))
            return Response.speak(Lang.f(53,
										 new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date())));
        if ("date".equals(id))
            return Response.speak(Lang.f(54,
										 new SimpleDateFormat(Lang.getDateFormat(),
															  Locale.forLanguageTag(Lang.getLocaleTag()))
										 .format(new Date())));

        if ("dice".equals(id))            return Response.speak(Lang.f(91, 1 + rnd.nextInt(6)));
        if ("coin".equals(id))            return Response.speak(rnd.nextBoolean() ? Lang.get(92) : Lang.get(93));
        if ("random_number".equals(id))   return Response.speak(Lang.f(94, 1 + rnd.nextInt(100)));
        if ("choose".equals(id))          return Response.speak(chooseFrom(m.params.get("opts")));
        if ("random_password".equals(id)) {
            int len = 16;
            String np = m.params.get("n");
            if (np != null) try { len = Integer.parseInt(np.replaceAll("[^0-9]", "")); }
				catch (Exception ignored) {}
            if (len < 4) len = 4;
            if (len > 64) len = 64;
            return Response.speak(Lang.f(209, randomPassword(len)));
        }
        if ("random_uuid".equals(id))
            return Response.speak(Lang.f(210, java.util.UUID.randomUUID().toString()));
        if ("random_color".equals(id)) {
            String c = String.format(Locale.ROOT, "#%06X", rnd.nextInt(0xFFFFFF + 1));
            return Response.speak(Lang.f(211, c));
        }

        if ("battery".equals(id))         return Response.speak(Lang.f(97, batteryLevel(), batteryCharging()));
        if ("device_model".equals(id))    return Response.speak(Lang.f(100, Build.MANUFACTURER + " " + Build.MODEL));
        if ("android_version".equals(id)) return Response.speak(Lang.f(101, Build.VERSION.RELEASE));
        if ("storage".equals(id))         return Response.speak(Lang.f(102, freeStorageGb()));
        if ("ram".equals(id))             return Response.speak(Lang.f(103, freeRamMb()));
        if ("ip".equals(id))              return Response.speak(Lang.f(104, localIp()));
        if ("carrier".equals(id))         return Response.speak(Lang.f(105, carrierName()));
        if ("uptime".equals(id)) {
            long up = android.os.SystemClock.elapsedRealtime();
            long h = up / 3600000;
            long mn = (up % 3600000) / 60000;
            return Response.speak(Lang.f(218, h + "h " + mn + "m"));
        }
        if ("screen_info".equals(id)) {
            android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            return Response.speak(Lang.f(219, dm.widthPixels, dm.heightPixels));
        }
        if ("network_info".equals(id)) {
            String ssid = currentSsid();
            if (ssid == null) return Response.speak(Lang.get(223));
            return Response.speak(Lang.f(220, ssid));
        }
        if ("kernel_version".equals(id))
            return Response.speak(Lang.f(221, System.getProperty("os.version", Lang.get(292))));
        if ("sim_info".equals(id))
            return Response.speak(Lang.f(222, carrierName()));

        if ("flashlight_on".equals(id))   return setTorch(true);
        if ("flashlight_off".equals(id))  return setTorch(false);
        if ("flashlight".equals(id))      return setTorch(!torchOn);

        if ("volume_up".equals(id))       return adjustStream(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 60);
        if ("volume_down".equals(id))     return adjustStream(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 61);
        if ("volume_mute".equals(id))     return adjustStream(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 62);
        if ("volume_max".equals(id))      return setVolumeMax();
        if ("volume_notif_up".equals(id))    return adjustStream(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_RAISE, 169);
        if ("volume_notif_down".equals(id))  return adjustStream(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_LOWER, 170);
        if ("volume_call_up".equals(id))     return adjustStream(AudioManager.STREAM_RING, AudioManager.ADJUST_RAISE, 171);
        if ("volume_call_down".equals(id))   return adjustStream(AudioManager.STREAM_RING, AudioManager.ADJUST_LOWER, 172);
        if ("volume_alarm_up".equals(id))    return adjustStream(AudioManager.STREAM_ALARM, AudioManager.ADJUST_RAISE, 173);
        if ("volume_alarm_down".equals(id))  return adjustStream(AudioManager.STREAM_ALARM, AudioManager.ADJUST_LOWER, 174);

        if ("music_pause".equals(id))     return mediaAction(KEYCODE_MEDIA_PAUSE, 162);
        if ("music_resume".equals(id))    return mediaAction(KEYCODE_MEDIA_PLAY, 163);
        if ("music_next".equals(id))      return mediaAction(KEYCODE_MEDIA_NEXT, 164);
        if ("music_prev".equals(id))      return mediaAction(KEYCODE_MEDIA_PREVIOUS, 165);
        if ("now_playing".equals(id))     return Response.speak(Lang.get(166));

        if ("brightness_up".equals(id))   return adjustBrightness(+40, 124);
        if ("brightness_down".equals(id)) return adjustBrightness(-40, 125);
        if ("brightness_max".equals(id))  return setBrightness(255, 126);
        if ("brightness_auto".equals(id)) return setBrightnessAuto();

        if ("airplane_on".equals(id))     return Response.speak(Lang.get(179));
        if ("airplane_off".equals(id))    return Response.speak(Lang.get(179));
        if ("airplane_settings".equals(id)) {
            Intent i = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.get(179), i);
        }
        if ("nfc_settings".equals(id)) {
            Intent i = new Intent(Settings.ACTION_NFC_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.get(180), i);
        }
        if ("gps_settings".equals(id)) {
            Intent i = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.get(181), i);
        }

        if ("alarm".equals(id)) {
            int[] hm = parseTimeParam(m.params.get("hora"));
            if (hm == null) return Response.speak(Lang.get(67));
            Intent i = new Intent(AlarmClock.ACTION_SET_ALARM);
            i.putExtra(AlarmClock.EXTRA_HOUR, hm[0]);
            i.putExtra(AlarmClock.EXTRA_MINUTES, hm[1]);
            i.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.f(66, hm[0], hm[1]), i);
        }
        if ("timer".equals(id)) {
            int sec = parseDurationParam(m.params.get("dur"));
            if (sec <= 0) return Response.speak(Lang.get(69));
            Intent i = new Intent(AlarmClock.ACTION_SET_TIMER);
            i.putExtra(AlarmClock.EXTRA_LENGTH, sec);
            i.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            String human;
            if (sec >= 3600) human = (sec / 3600) + " " + Lang.get(88);
            else if (sec >= 60) human = (sec / 60) + " " + Lang.get(89);
            else human = sec + " " + Lang.get(90);
            return Response.open(Lang.f(68, human), i);
        }
        if ("stopwatch".equals(id)) {
            Intent i = new Intent(AlarmClock.ACTION_SHOW_TIMERS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.get(106), i);
        }
        if ("show_alarms".equals(id)) {
            Intent i = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.get(107), i);
        }

        if ("call".equals(id)) {
            String num = digitsOnly(m.params.get("num"));
            Intent i;
            String msg;
            if (num.isEmpty()) {
                i = new Intent(Intent.ACTION_DIAL);
                msg = Lang.get(70);
            } else {
                i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + num));
                msg = Lang.f(71, num);
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(msg, i);
        }
        if ("contacts".equals(id)) {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setType(ContactsContract.Contacts.CONTENT_TYPE);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.get(108), i);
        }
        if ("recent_calls".equals(id)) {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(CallLog.Calls.CONTENT_URI);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.get(109), i);
        }
        if ("emergency".equals(id)) {
            Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:911"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.get(110), i);
        }

        if ("camera".equals(id)) {
            Intent i = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.get(72), i);
        }
        if ("selfie".equals(id)) {
            Intent i = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            i.putExtra("android.intent.extras.CAMERA_FACING", 1);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.get(72), i);
        }
        if ("record_video".equals(id)) {
            Intent i = new Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.get(111), i);
        }
        if ("gallery".equals(id)) {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setType("image/*");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.get(112), i);
        }

        if ("location".equals(id)) {
            Intent i = new Intent(Intent.ACTION_VIEW,
								  Uri.parse("geo:0,0?q=" + urlenc(Lang.get(289))));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.get(113), i);
        }
        if ("navigate".equals(id)) {
            String dest = m.params.get("dest");
            if (dest != null && !dest.isEmpty()) {
                Intent i = new Intent(Intent.ACTION_VIEW,
									  Uri.parse("google.navigation:q=" + urlenc(dest)));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return Response.open(Lang.f(114, dest), i);
            }
            return null;
        }
        if ("map".equals(id)) {
            String place = m.params.get("place");
            Uri uri = (place != null && !place.isEmpty())
                ? Uri.parse("geo:0,0?q=" + urlenc(place))
                : Uri.parse("geo:0,0?q=");
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.get(115), i);
        }

        if ("calculate".equals(id)) {
            String expr = m.params.get("expr");
            if (expr == null) return null;
            return calculate(expr);
        }
        if ("imc".equals(id)) {
            String peso = m.params.get("peso");
            String altura = m.params.get("altura");
            if (peso == null || altura == null) return Response.speak(Lang.get(195));
            try {
                double p = Double.parseDouble(peso.replace(",", ".").replaceAll("[^0-9.]", ""));
                double h = Double.parseDouble(altura.replace(",", ".").replaceAll("[^0-9.]", ""));
                if (h > 3) h = h / 100.0;
                if (h <= 0) return Response.speak(Lang.get(202));
                double imc = p / (h * h);
                return Response.speak(Lang.f(194, String.format(Locale.getDefault(), "%.1f", imc)));
            } catch (Exception e) { return Response.speak(Lang.get(202)); }
        }
        if ("average".equals(id)) {
            double[] nums = parseNumbers(m.params.get("nums"));
            if (nums == null || nums.length == 0) return Response.speak(Lang.get(202));
            double s = 0; for (double v : nums) s += v;
            return Response.speak(Lang.f(196, fmt(s / nums.length)));
        }
        if ("max_min".equals(id)) {
            double[] nums = parseNumbers(m.params.get("nums"));
            if (nums == null || nums.length == 0) return Response.speak(Lang.get(202));
            double max = nums[0], min = nums[0];
            for (double v : nums) { if (v > max) max = v; if (v < min) min = v; }
            return Response.speak(Lang.f(198, fmt(max), fmt(min)));
        }
        if ("sum_list".equals(id)) {
            double[] nums = parseNumbers(m.params.get("nums"));
            if (nums == null || nums.length == 0) return Response.speak(Lang.get(202));
            double s = 0; for (double v : nums) s += v;
            return Response.speak(Lang.f(199, fmt(s)));
        }
        if ("product_list".equals(id)) {
            double[] nums = parseNumbers(m.params.get("nums"));
            if (nums == null || nums.length == 0) return Response.speak(Lang.get(202));
            double s = 1; for (double v : nums) s *= v;
            return Response.speak(Lang.f(200, fmt(s)));
        }

        if ("text_upper".equals(id)) {
            String t = m.params.get("text");
            if (t == null) return Response.speak(Lang.get(186));
            return Response.speak(Lang.f(182, t.toUpperCase(Locale.ROOT)));
        }
        if ("text_lower".equals(id)) {
            String t = m.params.get("text");
            if (t == null) return Response.speak(Lang.get(186));
            return Response.speak(Lang.f(182, t.toLowerCase(Locale.ROOT)));
        }
        if ("text_reverse".equals(id)) {
            String t = m.params.get("text");
            if (t == null) return Response.speak(Lang.get(186));
            return Response.speak(Lang.f(182, new StringBuilder(t).reverse().toString()));
        }
        if ("text_morse".equals(id)) {
            String t = m.params.get("text");
            if (t == null) return Response.speak(Lang.get(186));
            return Response.speak(Lang.f(184, toMorse(t)));
        }
        if ("text_base64".equals(id)) {
            String t = m.params.get("text");
            if (t == null) return Response.speak(Lang.get(186));
            try {
                String b = android.util.Base64.encodeToString(
					t.getBytes("UTF-8"), android.util.Base64.NO_WRAP);
                return Response.speak(Lang.f(185, b));
            } catch (Exception e) { return Response.speak(Lang.get(186)); }
        }
        if ("text_wordcount".equals(id)) {
            String t = m.params.get("text");
            if (t == null) return Response.speak(Lang.get(186));
            String[] words = t.trim().split("\\s+");
            int wc = t.trim().isEmpty() ? 0 : words.length;
            return Response.speak(Lang.f(183, wc, t.length()));
        }
        if ("decimal_to_binary".equals(id)) {
            String n = m.params.get("n");
            if (n == null) return Response.speak(Lang.get(187));
            try {
                int v = Integer.parseInt(n.replaceAll("[^0-9-]", ""));
                return Response.speak(Lang.f(188, Integer.toBinaryString(v)));
            } catch (Exception e) { return Response.speak(Lang.get(187)); }
        }
        if ("decimal_to_hex".equals(id)) {
            String n = m.params.get("n");
            if (n == null) return Response.speak(Lang.get(187));
            try {
                int v = Integer.parseInt(n.replaceAll("[^0-9-]", ""));
                return Response.speak(Lang.f(189, Integer.toHexString(v).toUpperCase(Locale.ROOT)));
            } catch (Exception e) { return Response.speak(Lang.get(187)); }
        }
        if ("prime_check".equals(id)) {
            String n = m.params.get("n");
            if (n == null) return Response.speak(Lang.get(192));
            try {
                int v = Integer.parseInt(n.replaceAll("[^0-9-]", ""));
                return Response.speak(Lang.f(isPrime(v) ? 190 : 191, v));
            } catch (Exception e) { return Response.speak(Lang.get(192)); }
        }

        if ("translate".equals(id)) {
            String t = m.params.get("text");
            if (t == null || t.isEmpty()) return null;
            Intent i = new Intent(Intent.ACTION_VIEW,
								  Uri.parse("https://translate.google.com/?text=" + urlenc(t)));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.get(116), i);
        }

        if ("search_youtube".equals(id))     return searchUrl(m.params.get("q"), 76, "https://www.youtube.com/results?search_query=");
        if ("search_wikipedia".equals(id))   return searchUrl(m.params.get("q"), 117, "https://" + Lang.getActiveLanguage() + ".wikipedia.org/wiki/Special:Search?search=");
        if ("search_playstore".equals(id))   return marketSearch(m.params.get("q"), 119);
        if ("search_amazon".equals(id))      return searchUrl(m.params.get("q"), 233, "https://www.amazon.com/s?k=");
        if ("search_mercadolibre".equals(id))return searchUrl(m.params.get("q"), 234, "https://listado.mercadolibre.com/");
        if ("search_steam".equals(id))       return searchUrl(m.params.get("q"), 242, "https://store.steampowered.com/search/?term=");
        if ("search_netflix".equals(id))     return searchUrl(m.params.get("q"), 243, "https://www.netflix.com/search?q=");
        if ("search_spotify".equals(id))     return searchUrl(m.params.get("q"), 244, "https://open.spotify.com/search/");
        if ("search_reddit".equals(id))      return searchUrl(m.params.get("q"), 245, "https://www.reddit.com/search/?q=");
        if ("search_twitter".equals(id))     return searchUrl(m.params.get("q"), 246, "https://twitter.com/search?q=");
        if ("search_tiktok".equals(id))      return searchUrl(m.params.get("q"), 247, "https://www.tiktok.com/search?q=");
        if ("search_flights".equals(id))     return searchUrl(m.params.get("q"), 240, "https://www.google.com/travel/flights?q=");
        if ("search_images".equals(id)) {
            String qq = m.params.get("q");
            if (qq == null || qq.isEmpty()) return null;
            Intent i = new Intent(Intent.ACTION_VIEW,
								  Uri.parse("https://www.google.com/search?tbm=isch&q=" + urlenc(qq)));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.f(118, qq), i);
        }
        if ("search_google".equals(id))      return searchUrl(m.params.get("q"), 77, "https://www.google.com/search?q=");

        if ("find_gas_station".equals(id))   return searchPlaces(235, Lang.get(284));
        if ("find_pharmacy".equals(id))      return searchPlaces(236, Lang.get(285));
        if ("find_restaurant".equals(id))    return searchPlaces(237, Lang.get(286));
        if ("find_hospital".equals(id))      return searchPlaces(238, Lang.get(287));
        if ("track_package".equals(id)) {
            String n = m.params.get("num");
            if (n == null || n.isEmpty()) return Response.speak(Lang.get(241));
            Intent i = new Intent(Intent.ACTION_VIEW,
								  Uri.parse("https://www.google.com/search?q=" + urlenc(Lang.get(288) + n)));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.get(239), i);
        }

        if ("create_event".equals(id)) {
            Intent i = new Intent(Intent.ACTION_INSERT);
            i.setData(CalendarContract.Events.CONTENT_URI);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.get(120), i);
        }
        if ("show_calendar".equals(id)) {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(CalendarContract.CONTENT_URI);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.get(121), i);
        }

        if ("note_add".equals(id)) {
            String t = m.params.get("texto");
            if (t == null || t.isEmpty()) return Response.speak(Lang.get(229));
            addNote(t);
            return Response.speak(Lang.get(225));
        }
        if ("note_list".equals(id))     return speakNotes();
        if ("note_clear".equals(id)) {
            setNotes("");
            return Response.speak(Lang.get(228));
        }
        if ("note_read".equals(id)) {
            String ns = m.params.get("n");
            if (ns == null) return Response.speak(Lang.get(231));
            try {
                int idx = Integer.parseInt(ns.replaceAll("[^0-9]", ""));
                return readNote(idx);
            } catch (Exception e) { return Response.speak(Lang.get(232)); }
        }

        if (id.startsWith("list_")) return handleListIntent(id, m);

        if ("launch_app".equals(id)) {
            String app = m.params.get("app");
            if (app == null || app.isEmpty()) return null;
            Intent i = findAppIntent(app);
            if (i != null) return Response.open(Lang.f(78, app), i);
            return Response.speak(Lang.f(79, app));
        }
        if ("open_url".equals(id)) {
            String url = m.params.get("url");
            if (url == null || url.isEmpty()) return null;
            url = url.replace(" ", "");
            if (!url.startsWith("http")) url = "https://" + url;
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.f(78, url), i);
        }

        if ("conv_kmh_mph".equals(id)) return convert(m.params.get("expr"), 0.621371, Lang.get(248));
        if ("conv_mph_kmh".equals(id)) return convert(m.params.get("expr"), 1.60934, Lang.get(249));
        if ("conv_oz_g".equals(id))    return convert(m.params.get("expr"), 28.3495, Lang.get(250));
        if ("conv_g_oz".equals(id))    return convert(m.params.get("expr"), 0.035274, Lang.get(251));
        if ("conv_in_cm".equals(id))   return convert(m.params.get("expr"), 2.54, Lang.get(252));
        if ("conv_cm_in".equals(id))   return convert(m.params.get("expr"), 0.393701, Lang.get(253));
        if ("conv_l_gal".equals(id))   return convert(m.params.get("expr"), 0.264172, Lang.get(254));
        if ("conv_gal_l".equals(id))   return convert(m.params.get("expr"), 3.78541, Lang.get(255));
        if ("conv_yd_m".equals(id))    return convert(m.params.get("expr"), 0.9144, Lang.get(256));
        if ("conv_m_yd".equals(id))    return convert(m.params.get("expr"), 1.09361, Lang.get(257));

        return null;
    }

    // ================================================================
    // Clima
    // ================================================================
    private Response weatherCity(String city) {
        if (city == null || city.isEmpty()) return Response.speak(Lang.get(262));
        WeatherEngine.Weather w = weather.currentForCity(city);
        if (w == null) return Response.speak(Lang.get(261));
        return Response.speak(Lang.f(259, w.city, WeatherEngine.fmtTemp(w.tempC), w.describe()));
    }
    private Response weatherHere() {
        WeatherEngine.Weather w = weather.currentForLocation();
        if (w == null) return Response.speak(Lang.get(283));
        return Response.speak(Lang.f(260, WeatherEngine.fmtTemp(w.tempC), w.describe()));
    }
    private Response forecastCity(String city) {
        if (city == null || city.isEmpty()) return Response.speak(Lang.get(262));
        WeatherEngine.Weather w = weather.forecastTomorrow(city);
        if (w == null) return Response.speak(Lang.get(266));
        return Response.speak(Lang.f(264, city, WeatherEngine.fmtTemp(w.tempC), WeatherEngine.fmtTemp(w.feelsC), w.describe()));
    }
    private Response forecastHere() {
        WeatherEngine.Weather w = weather.forecastTomorrowForLocation();
        if (w == null) return Response.speak(Lang.get(266));
        return Response.speak(Lang.f(265, WeatherEngine.fmtTemp(w.tempC), WeatherEngine.fmtTemp(w.feelsC), w.describe()));
    }

    // ================================================================
    // Handlers
    // ================================================================
    private Response setTorch(boolean on) {
        try {
            CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            if (cm == null) return Response.speak(Lang.get(58));
            String flashId = null;
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics ch = cm.getCameraCharacteristics(id);
                Boolean has = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (has != null && has) { flashId = id; break; }
            }
            if (flashId == null) return Response.speak(Lang.get(57));
            cm.setTorchMode(flashId, on);
            torchOn = on;
            return Response.speak(on ? Lang.get(55) : Lang.get(56));
        } catch (Exception e) { return Response.speak(Lang.get(59)); }
    }

    private Response adjustStream(int stream, int dir, int respId) {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return Response.speak(Lang.get(65));
            am.adjustStreamVolume(stream, dir, 0);
            return Response.speak(Lang.get(respId));
        } catch (Exception e) { return Response.speak(Lang.get(64)); }
    }

    private Response setVolumeMax() {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return Response.speak(Lang.get(65));
            int maxv = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, maxv, 0);
            return Response.speak(Lang.get(63));
        } catch (Exception e) { return Response.speak(Lang.get(64)); }
    }

    private Response mediaAction(int keyCode, int respId) {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return Response.speak(Lang.get(168));
            am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
            return Response.speak(Lang.get(respId));
        } catch (Exception e) { return Response.speak(Lang.get(168)); }
    }

    private Response adjustBrightness(int delta, int respId) {
        if (!Settings.System.canWrite(ctx)) return askBrightnessPermission();
        try {
            int mode = Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE);
            if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            }
            int cur = Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
            int next = Math.max(20, Math.min(255, cur + delta));
            Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, next);
            return Response.speak(Lang.get(respId));
        } catch (Exception e) { return Response.speak(Lang.get(127)); }
    }

    private Response setBrightness(int value, int respId) {
        if (!Settings.System.canWrite(ctx)) return askBrightnessPermission();
        try {
            Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, value);
            return Response.speak(Lang.get(respId));
        } catch (Exception e) { return Response.speak(Lang.get(127)); }
    }

    private Response setBrightnessAuto() {
        if (!Settings.System.canWrite(ctx)) return askBrightnessPermission();
        try {
            Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
            return Response.speak(Lang.get(123));
        } catch (Exception e) { return Response.speak(Lang.get(127)); }
    }

    private Response askBrightnessPermission() {
        Intent i = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return Response.open(Lang.get(122), i);
    }

    private Response calculate(String exprRaw) {
        String expr = exprRaw;
        expr = expr.replace(Lang.get(293), " + ")
			.replace(Lang.get(294), " - ")
			.replace(Lang.get(295), " * ")
			.replace(Lang.get(296), " / ")
			.replace(Lang.get(297), " ^ ")
			.replace(Lang.get(298), " % ");
        if (expr.startsWith(Lang.get(299))) {
            try {
                double a = Double.parseDouble(expr.substring(Lang.get(299).length()).trim());
                if (a < 0) return Response.speak(Lang.get(128));
                return Response.speak(Lang.f(86, fmt(Math.sqrt(a))));
            } catch (Exception ignored) {}
        }
        int pct = expr.indexOf(" % ");
        if (pct > 0) {
            try {
                double a = Double.parseDouble(expr.substring(0, pct).trim());
                double b = Double.parseDouble(expr.substring(pct + 3).trim());
                return Response.speak(Lang.f(86, fmt(a * b / 100.0)));
            } catch (Exception ignored) {}
        }
        String[] ops = { "^", "+", "-", "*", "/" };
        for (String op : ops) {
            int pos = expr.indexOf(op);
            if (pos > 0) {
                try {
                    double a = Double.parseDouble(expr.substring(0, pos).trim());
                    double b = Double.parseDouble(expr.substring(pos + 1).trim());
                    double r;
                    if (op.equals("+")) r = a + b;
                    else if (op.equals("-")) r = a - b;
                    else if (op.equals("*")) r = a * b;
                    else if (op.equals("^")) r = Math.pow(a, b);
                    else {
                        if (b == 0) return Response.speak(Lang.get(85));
                        r = a / b;
                    }
                    return Response.speak(Lang.f(86, fmt(r)));
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private Response searchUrl(String q, int respId, String base) {
        if (q == null || q.isEmpty()) return null;
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(base + urlenc(q)));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return Response.open(Lang.get(respId), i);
    }

    private Response marketSearch(String q, int respId) {
        if (q == null || q.isEmpty()) return null;
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + urlenc(q)));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return Response.open(Lang.get(respId), i);
    }

    private Response searchPlaces(int respId, String query) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + urlenc(query)));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return Response.open(Lang.get(respId), i);
    }

    private Response convert(String exprRaw, double factor, String unit) {
        if (exprRaw == null) return null;
        Double n = extractNumber(exprRaw);
        if (n == null) return null;
        return Response.speak(Lang.f(258, fmt(n * factor), unit));
    }

    private Double extractNumber(String q) {
        StringBuilder sb = new StringBuilder();
        boolean started = false;
        for (int i = 0; i < q.length(); i++) {
            char c = q.charAt(i);
            if ((c >= '0' && c <= '9') || c == '.' || c == ',' || c == '-') {
                started = true;
                sb.append(c == ',' ? '.' : c);
            } else if (started) break;
        }
        if (sb.length() == 0) return null;
        try { return Double.parseDouble(sb.toString()); }
        catch (Exception e) { return null; }
    }

    private Response handleListIntent(String id, Lang.IntentMatch m) {
        boolean shopping = id.endsWith("_shopping");
        String key    = shopping ? "lista_compra" : "lista_tareas";
        int    idName = shopping ? 138 : 139;
        String nombre = Lang.get(idName);
        if (id.startsWith("list_add_")) {
            String item = m.params.get("item");
            if (item == null || item.isEmpty()) return Response.speak(Lang.get(140));
            String lista = getList(key);
            lista = lista.isEmpty() ? item : lista + "||" + item;
            setList(key, lista);
            return Response.speak(Lang.f(141, item, nombre));
        }
        if (id.startsWith("list_clear_")) {
            setList(key, "");
            return Response.speak(Lang.f(142, nombre));
        }
        if (id.startsWith("list_show_")) {
            String lista = getList(key);
            if (lista.isEmpty()) return Response.speak(Lang.f(143, nombre));
            String[] items = lista.split("\\|\\|");
            StringBuilder sb = new StringBuilder();
            sb.append(Lang.f(144, nombre));
            for (int i = 0; i < items.length; i++)
                sb.append(" ").append(i + 1).append(". ").append(items[i]).append(".");
            return Response.speak(sb.toString());
        }
        return null;
    }

    private String getList(String key) {
        return ctx.getSharedPreferences("mina_lists", Context.MODE_PRIVATE).getString(key, "");
    }
    private void setList(String key, String value) {
        ctx.getSharedPreferences("mina_lists", Context.MODE_PRIVATE).edit().putString(key, value).apply();
    }
    private String getNotes() {
        return ctx.getSharedPreferences("mina_notes", Context.MODE_PRIVATE).getString("all", "");
    }
    private void setNotes(String v) {
        ctx.getSharedPreferences("mina_notes", Context.MODE_PRIVATE).edit().putString("all", v).apply();
    }
    private void addNote(String text) {
        String cur = getNotes();
        cur = cur.isEmpty() ? text : cur + "||" + text;
        setNotes(cur);
    }
    private Response speakNotes() {
        String cur = getNotes();
        if (cur.isEmpty()) return Response.speak(Lang.get(227));
        String[] parts = cur.split("\\|\\|");
        StringBuilder sb = new StringBuilder();
        sb.append(Lang.f(226, parts.length));
        for (int i = 0; i < parts.length; i++)
            sb.append(" ").append(i + 1).append(". ").append(parts[i]).append(".");
        return Response.speak(sb.toString());
    }
    private Response readNote(int idx) {
        String cur = getNotes();
        if (cur.isEmpty()) return Response.speak(Lang.get(227));
        String[] parts = cur.split("\\|\\|");
        if (idx < 1 || idx > parts.length) return Response.speak(Lang.get(232));
        return Response.speak(parts[idx - 1]);
    }

    private int batteryLevel() {
        try {
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            if (bm == null) return -1;
            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } catch (Exception e) { return -1; }
    }

    private String batteryCharging() {
        try {
            Intent i = ctx.registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i == null) return "";
            int status = i.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
            boolean charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
				|| status == android.os.BatteryManager.BATTERY_STATUS_FULL;
            return charging ? Lang.get(98) : Lang.get(99);
        } catch (Exception e) { return ""; }
    }

    private String freeStorageGb() {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long bytes = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
            return String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        } catch (Exception e) { return Lang.get(292); }
    }

    private String freeRamMb() {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return Lang.get(292);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return (mi.availMem / (1024 * 1024)) + " MB";
        } catch (Exception e) { return Lang.get(292); }
    }

    private String localIp() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface nif = ifaces.nextElement();
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address)
                        return addr.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return Lang.get(292);
    }

    private String carrierName() {
        try {
            android.telephony.TelephonyManager tm =
                (android.telephony.TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) return Lang.get(292);
            String n = tm.getNetworkOperatorName();
            return (n == null || n.isEmpty()) ? Lang.get(292) : n;
        } catch (Exception e) { return Lang.get(292); }
    }

    @SuppressWarnings("deprecation")
    private String currentSsid() {
        try {
            android.net.wifi.WifiManager wm =
                (android.net.wifi.WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return null;
            android.net.wifi.WifiInfo info = wm.getConnectionInfo();
            if (info == null) return null;
            String ssid = info.getSSID();
            if (ssid == null || ssid.equals("<unknown ssid>")) return null;
            return ssid.replace("\"", "");
        } catch (Exception e) { return null; }
    }

    private String chooseFrom(String opts) {
        if (opts == null || opts.isEmpty()) return Lang.get(95);
        opts = opts.replace(" o ", ",").replace(" y ", ",");
        String[] parts = opts.split(",");
        List<String> valid = new ArrayList<String>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) valid.add(t);
        }
        if (valid.isEmpty()) return Lang.get(95);
        Collections.shuffle(valid, rnd);
        return Lang.f(96, valid.get(0));
    }

    private Intent findAppIntent(String query) {
        PackageManager pm = ctx.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        String q = query.toLowerCase(Locale.getDefault()).trim();
        ResolveInfo best = null;
        int bestScore = 0;
        for (ResolveInfo ri : apps) {
            if (ri.activityInfo == null) continue;
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            if (ai == null) continue;
            String label = ai.loadLabel(pm).toString().toLowerCase(Locale.getDefault());
            String pkg   = ai.packageName.toLowerCase(Locale.getDefault());
            int score = 0;
            if (label.equals(q))          score = 100;
            else if (label.startsWith(q)) score = 80;
            else if (label.contains(q))   score = 60;
            else if (pkg.contains(q))     score = 40;
            if (score > bestScore) { bestScore = score; best = ri; }
        }
        if (best == null || bestScore < 40) return null;
        Intent launch = pm.getLaunchIntentForPackage(best.activityInfo.packageName);
        if (launch != null) launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return launch;
    }

    private String pick(String... options) { return options[rnd.nextInt(options.length)]; }

    private String urlenc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s.replace(" ", "+"); }
    }

    private String digitsOnly(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
        }
        return sb.toString();
    }

    private String toMorse(String text) {
        if (text == null) return "";
        String up = text.toUpperCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < up.length(); i++) {
            char c = up.charAt(i);
            if (c == ' ') { sb.append("/ "); continue; }
            String code = MORSE.get(c);
            if (code != null) sb.append(code).append(' ');
        }
        return sb.toString().trim();
    }

    private double[] parseNumbers(String s) {
        if (s == null) return null;
        String[] parts = s.split("[,\\s]+");
        List<Double> list = new ArrayList<Double>();
        for (String p : parts) {
            String cleaned = p.trim().replace(",", ".");
            if (cleaned.isEmpty()) continue;
            try { list.add(Double.parseDouble(cleaned)); }
            catch (Exception ignored) {}
        }
        if (list.isEmpty()) return null;
        double[] out = new double[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }

    private boolean isPrime(int n) {
        if (n < 2) return false;
        if (n < 4) return true;
        if (n % 2 == 0) return false;
        for (int i = 3; (long)i * i <= n; i += 2)
            if (n % i == 0) return false;
        return true;
    }

    private String randomPassword(int len) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++)
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    private int[] parseTimeParam(String s) {
        if (s == null) return null;
        try {
            String[] parts = s.trim().split("[:\\s]+");
            int h = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
            int m = 0;
            if (parts.length > 1) {
                String mp = parts[1].replaceAll("[^0-9]", "");
                if (!mp.isEmpty()) m = Integer.parseInt(mp);
            } else if (s.contains("y media") || s.contains("half past")) m = 30;
            else if (s.contains("y cuarto") || s.contains("quarter past")) m = 15;
            if (h >= 0 && h < 24 && m >= 0 && m < 60) return new int[]{h, m};
        } catch (Exception ignored) {}
        return null;
    }

    private int parseDurationParam(String s) {
        if (s == null) return -1;
        String[] tokens = s.split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            String digits = tokens[i].replaceAll("[^0-9]", "");
            if (digits.isEmpty()) continue;
            int n;
            try { n = Integer.parseInt(digits); } catch (Exception e) { continue; }
            if (i + 1 < tokens.length) {
                String next = tokens[i + 1].toLowerCase(Locale.getDefault());
                if (next.startsWith("seg") || next.startsWith("sec")) return n;
                if (next.startsWith("min")) return n * 60;
                if (next.startsWith("hor") || next.startsWith("hour")) return n * 3600;
            }
            return n;
        }
        return -1;
    }

    private String fmt(double r) {
        if (r == Math.floor(r) && !Double.isInfinite(r) && Math.abs(r) < 1e15)
            return String.valueOf((long) r);
        return String.valueOf(r);
    }
}
