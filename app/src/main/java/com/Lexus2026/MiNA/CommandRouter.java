package com.Lexus2026.MiNA;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

class CommandRouter {

    static class Response {
        final String text;
        final Intent intent;
        final boolean closeAfter;

        private Response(String text, Intent intent, boolean closeAfter) {
            this.text = text;
            this.intent = intent;
            this.closeAfter = closeAfter;
        }

        static Response speak(String t)             { return new Response(t, null, false); }
        static Response open(String t, Intent i)    { return new Response(t, i, true); }
        static Response close(String t)             { return new Response(t, null, true); }
    }

    private final Context ctx;
    private static boolean torchOn = false;

    CommandRouter(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    Response route(String raw) {
        if (raw == null) return Response.speak(Lang.get(1200));
        String q = raw.toLowerCase(Locale.getDefault()).trim();
        if (q.isEmpty()) return Response.speak(Lang.get(1200));

        if (hit(q, "hola", "buenas", "hey", "buenos dias", "buenas tardes", "buenas noches"))
            return Response.speak(Lang.get(1201));
        if (hit(q, "como te llamas", "tu nombre"))
            return Response.speak(Lang.get(1202));
        if (hit(q, "gracias"))
            return Response.speak(Lang.get(1203));
        if (hit(q, "adios", "chao", "hasta luego", "cierra", "cerrar"))
            return Response.close(Lang.get(1204));

        if (hit(q, "que puedes hacer", "ayuda", "comandos", "opciones"))
            return Response.speak(Lang.get(1239));

        if (hit(q, "que hora", "la hora", "hora es"))
            return Response.speak(Lang.f(1205,
										 new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date())));
        if (hit(q, "que dia", "fecha", "hoy es"))
            return Response.speak(Lang.f(1206,
										 new SimpleDateFormat("EEEE d 'de' MMMM",
															  new Locale("es", "ES")).format(new Date())));

        if (hit(q, "linterna", "flash"))
            return toggleTorch(q);

        Response vol = handleVolume(q);
        if (vol != null) return vol;

        if (hit(q, "alarma", "despertador")) {
            Response a = setAlarm(q);
            if (a != null) return a;
            return Response.speak(Lang.get(1219));
        }

        if (hit(q, "temporizador", "timer", "cuenta atras")) {
            Response t = setTimer(q);
            if (t != null) return t;
            return Response.speak(Lang.get(1221));
        }

        if (hit(q, "llama a", "llamar a", "marca el", "marcar", "dispara a"))
            return dial(q);

        if (hit(q, "abre la camara", "abrir camara", "saca una foto",
                "haz una foto", "tomar foto", "camara")) {
            Intent i = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.get(1224), i);
        }

        if (hit(q, "ajustes de wifi", "abre wifi", "configurar wifi",
                "ajustes wifi", "configuracion de wifi")) {
            Intent i = new Intent(Settings.ACTION_WIFI_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.get(1225), i);
        }
        if (hit(q, "ajustes de bluetooth", "abre bluetooth",
                "configurar bluetooth", "ajustes bluetooth")) {
            Intent i = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.get(1226), i);
        }
        if (hit(q, "ajustes del sistema", "ajustes generales",
                "abre ajustes", "configuracion del sistema")) {
            Intent i = new Intent(Settings.ACTION_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.get(1227), i);
        }

        Response calc = tryCalculate(q);
        if (calc != null) return calc;

        String ytQuery = extractAfter(q,
									  "en youtube", "youtube busca", "busca en youtube", "buscar en youtube");
        if (ytQuery != null && !ytQuery.isEmpty()) {
            Intent i = new Intent(Intent.ACTION_VIEW,
								  Uri.parse("https://www.youtube.com/results?search_query=" + urlenc(ytQuery)));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return Response.open(Lang.get(1228), i);
        }

        if (hit(q, "busca ", "buscar ", "en google ", "googlea ", "busca en google")) {
            String gQuery = extractAfter(q, "busca en google ", "googlea ",
										 "en google ", "busca ", "buscar ");
            if (gQuery != null && !gQuery.isEmpty()) {
                Intent i = new Intent(Intent.ACTION_VIEW,
									  Uri.parse("https://www.google.com/search?q=" + urlenc(gQuery)));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return Response.open(Lang.get(1229), i);
            }
        }

        if (hit(q, "abre ", "abrir ", "navega a ")) {
            String url = extractUrl(q);
            if (url != null) {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return Response.open(Lang.f(1230, url), i);
            }
        }

        if (hit(q, "abre ", "abrir ", "lanza ", "inicia ")) {
            String appName = extractAppName(q);
            if (appName != null && !appName.isEmpty()) {
                Intent i = findAppIntent(appName);
                if (i != null) return Response.open(Lang.f(1230, appName), i);
                return Response.speak(Lang.f(1231, appName));
            }
        }

        return Response.speak(pick(
								  Lang.get(1232),
								  Lang.get(1233),
								  Lang.get(1234),
								  Lang.get(1235),
								  Lang.get(1236)));
    }

    private Response toggleTorch(String q) {
        boolean wantOff = hit(q, "apaga", "apagar", "desactiva", "quita");
        try {
            CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            if (cm == null) return Response.speak(Lang.get(1210));

            String flashId = null;
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics ch = cm.getCameraCharacteristics(id);
                Boolean has = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (has != null && has) { flashId = id; break; }
            }
            if (flashId == null) return Response.speak(Lang.get(1209));

            boolean target = !wantOff && !torchOn;
            cm.setTorchMode(flashId, target);
            torchOn = target;
            return Response.speak(target ? Lang.get(1207) : Lang.get(1208));
        } catch (Exception e) {
            return Response.speak(Lang.get(1211));
        }
    }

    private Response handleVolume(String q) {
        boolean up   = hit(q, "sube el volumen", "subir volumen", "mas volumen", "volumen arriba");
        boolean down = hit(q, "baja el volumen", "bajar volumen", "menos volumen", "volumen abajo");
        boolean mute = hit(q, "silencia", "silencio", "mutea", "modo silencio");
        boolean max  = hit(q, "volumen al maximo", "volumen maximo", "sube todo el volumen");
        if (!up && !down && !mute && !max) return null;

        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return Response.speak(Lang.get(1217));
            int stream = AudioManager.STREAM_MUSIC;
            if (mute) {
                am.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0);
                return Response.speak(Lang.get(1214));
            }
            if (max) {
                int maxv = am.getStreamMaxVolume(stream);
                am.setStreamVolume(stream, maxv, 0);
                return Response.speak(Lang.get(1215));
            }
            am.adjustStreamVolume(stream,
								  up ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
            return Response.speak(up ? Lang.get(1212) : Lang.get(1213));
        } catch (Exception e) {
            return Response.speak(Lang.get(1216));
        }
    }

    private Response setAlarm(String q) {
        int[] hm = parseTime(q);
        if (hm == null) return null;
        Intent i = new Intent(AlarmClock.ACTION_SET_ALARM);
        i.putExtra(AlarmClock.EXTRA_HOUR, hm[0]);
        i.putExtra(AlarmClock.EXTRA_MINUTES, hm[1]);
        i.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return Response.open(Lang.f(1218, hm[0], hm[1]), i);
    }

    private Response setTimer(String q) {
        int seconds = parseDuration(q);
        if (seconds <= 0) return null;
        Intent i = new Intent(AlarmClock.ACTION_SET_TIMER);
        i.putExtra(AlarmClock.EXTRA_LENGTH, seconds);
        i.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String human;
        if (seconds >= 3600) human = (seconds / 3600) + " " + Lang.get(1240);
        else if (seconds >= 60) human = (seconds / 60) + " " + Lang.get(1241);
        else human = seconds + " " + Lang.get(1242);
        return Response.open(Lang.f(1220, human), i);
    }

    private Response dial(String q) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < q.length(); i++) {
            char c = q.charAt(i);
            if (c >= '0' && c <= '9') digits.append(c);
        }
        String num = digits.toString();
        Intent i;
        String msg;
        if (num.isEmpty()) {
            i = new Intent(Intent.ACTION_DIAL);
            msg = Lang.get(1222);
        } else {
            i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + num));
            msg = Lang.f(1223, num);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return Response.open(msg, i);
    }

    private Response tryCalculate(String q) {
        if (!hit(q, "cuanto es", "cuanto son", "calcula", "calculame")) return null;
        String expr = q;
        int idx = expr.indexOf("es ");
        if (idx < 0) idx = expr.indexOf("son ");
        if (idx < 0) idx = expr.indexOf("calcula ");
        if (idx < 0) idx = expr.indexOf("calculame ");
        if (idx < 0) return null;
        int sp = expr.indexOf(' ', idx + 1);
        if (sp < 0) return null;
        expr = expr.substring(sp + 1).trim();

        expr = expr.replace(" mas ", " + ")
			.replace(" menos ", " - ")
			.replace(" por ", " * ")
			.replace(" multiplicado por ", " * ")
			.replace(" dividido entre ", " / ")
			.replace(" dividido por ", " / ")
			.replace(" dividido ", " / ")
			.replace(" entre ", " / ");

        String[] ops = { "+", "-", "*", "/" };
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
                    else {
                        if (b == 0) return Response.speak(Lang.get(1237));
                        r = a / b;
                    }
                    String resStr = (r == Math.floor(r) && !Double.isInfinite(r))
						? String.valueOf((long) r)
						: String.valueOf(r);
                    return Response.speak(Lang.f(1238, resStr));
                } catch (Exception ignored) {}
            }
        }
        return null;
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
            if (label.equals(q))            score = 100;
            else if (label.startsWith(q))   score = 80;
            else if (label.contains(q))     score = 60;
            else if (pkg.contains(q))       score = 40;

            if (score > bestScore) { bestScore = score; best = ri; }
        }

        if (best == null || bestScore < 40) return null;

        Intent launch = pm.getLaunchIntentForPackage(best.activityInfo.packageName);
        if (launch != null) launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return launch;
    }

    private boolean hit(String s, String... needles) {
        for (String n : needles) if (s.contains(n)) return true;
        return false;
    }

    private String pick(String... options) {
        int i = (int)(Math.random() * options.length);
        return options[i];
    }

    private String urlenc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s.replace(" ", "+"); }
    }

    private String extractAfter(String q, String... markers) {
        for (String m : markers) {
            int i = q.indexOf(m);
            if (i >= 0) {
                String r = q.substring(i + m.length()).trim();
                if (!r.isEmpty()) return r;
            }
        }
        return null;
    }

    private String extractAppName(String q) {
        String[] prefixes = {
            "abre la ", "abre el ", "abre los ", "abre las ", "abre ",
            "abrir la ", "abrir el ", "abrir los ", "abrir las ", "abrir ",
            "lanza la ", "lanza el ", "lanza ",
            "inicia la ", "inicia el ", "inicia "
        };
        for (String p : prefixes) {
            if (q.startsWith(p)) return q.substring(p.length()).trim();
        }
        return null;
    }

    private String extractUrl(String q) {
        String app = extractAppName(q);
        if (app == null) return null;
        app = app.replace(" ", "");
        if (app.contains(".")) {
            if (!app.startsWith("http")) return "https://" + app;
            return app;
        }
        return null;
    }

    private int[] parseTime(String q) {
        int i = q.indexOf("a las ");
        if (i < 0) return null;
        String rest = q.substring(i + 6).trim();
        if (rest.isEmpty()) return null;
        try {
            StringBuilder hb = new StringBuilder();
            for (int k = 0; k < rest.length(); k++) {
                char c = rest.charAt(k);
                if (c >= '0' && c <= '9') hb.append(c);
                else break;
            }
            if (hb.length() == 0) return null;
            int h = Integer.parseInt(hb.toString());
            int m = 0;
            int colon = rest.indexOf(':');
            if (colon >= 0) {
                StringBuilder mb = new StringBuilder();
                for (int k = colon + 1; k < rest.length(); k++) {
                    char c = rest.charAt(k);
                    if (c >= '0' && c <= '9') mb.append(c);
                    else break;
                }
                if (mb.length() > 0) m = Integer.parseInt(mb.toString());
            } else if (rest.contains("y media")) {
                m = 30;
            } else if (rest.contains("y cuarto")) {
                m = 15;
            }
            if (h >= 0 && h < 24 && m >= 0 && m < 60) return new int[]{h, m};
        } catch (Exception ignored) {}
        return null;
    }

    private int parseDuration(String q) {
        String[] tokens = q.split("\\s+");
        for (int i = 0; i < tokens.length - 1; i++) {
            int n = -1;
            String digits = tokens[i].replaceAll("[^0-9]", "");
            if (digits.isEmpty()) continue;
            try { n = Integer.parseInt(digits); } catch (Exception ignored) {}
            if (n <= 0) continue;
            String next = tokens[i + 1].toLowerCase(Locale.getDefault());
            if (next.startsWith("seg")) return n;
            if (next.startsWith("min")) return n * 60;
            if (next.startsWith("hor")) return n * 3600;
        }
        return -1;
    }
}
