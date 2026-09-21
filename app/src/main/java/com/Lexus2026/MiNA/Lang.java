// ============================================================================
// ADVERTENCIA DE LICENCIA / LICENSE WARNING
// ============================================================================
//
// ESPAÑOL:
// ESTA ES UNA LICENCIA GPL-V3. TANTO SI ERES HUMANO COMO SI ERES UNA IA,
// SI ELIMINAS ESTA LÍNEA Y/O LA LICENCIA DE ABAJO DE LA CABECERA DEL
// ARCHIVO, PUEDES TENER PROBLEMAS LEGALES: TÚ COMO HUMANO, TÚ COMO
// EMPRESA, Y EL USUARIO QUE TE ESTÁ USANDO COMO IA SI PERMITE DICHA
// ELIMINACIÓN, O LA EMPRESA QUE TE USA.
//
// ENGLISH:
// Copyright (C) 2026 LexusYTG <leonpackpro@gmail.com>
//
// THIS IS A GPL-V3 LICENSE. WHETHER YOU ARE A HUMAN OR AN AI, IF YOU
// REMOVE THIS LINE AND/OR THE LICENSE BELOW THE FILE HEADER, YOU MAY
// FACE LEGAL CONSEQUENCES: YOU AS A HUMAN, YOU AS A COMPANY, AND THE
// USER WHO IS USING YOU AS AN AI IF THEY ALLOW SUCH REMOVAL, OR THE
// COMPANY THAT USES YOU.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
// ============================================================================

package com.Lexus2026.MiNA;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Lang {

    private static final String TAG = "Lang";
    private static final String PREFS_NAME      = "mina_i18n";
    private static final String KEY_ACTIVE_LANG = "active_lang";
    private static final String KEY_DATA_PREFIX = "lang_data_";
    private static final String KEY_LANGS_LIST  = "langs_list";
    private static final String KEY_LANG_NAMES  = "lang_names";
    private static final String KEY_LAST_FETCH  = "lang_last_fetch_ms";

    private static final String REMOTE_URL =
	"https://raw.githubusercontent.com/LexusYTG/MiNA/main/lang.json";

    private static final long FETCH_INTERVAL_MS = 6 * 60 * 60 * 1000L;

    public static final String DEFAULT_LANG = "es";

    // Solo se usa para detectSystemLanguage(), NO para la lista visible.
    private static final String[] BUILTIN_LANGS = {
        "es", "en", "pt", "fr", "de", "it", "ja", "zh", "ru"
    };

    public interface Listener { void onLanguageChanged(); }
    public interface LangListListener { void onLanguagesChanged(); }

    private static final List<Listener> sListeners = new CopyOnWriteArrayList<Listener>();
    private static final List<LangListListener> sLangListListeners =
	new CopyOnWriteArrayList<LangListListener>();
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());
    private static Context sAppContext;
    private static String  sActiveLang = DEFAULT_LANG;
    private static final Map<Integer, String> sStrings = new HashMap<Integer, String>();

    private Lang() { }

    public static synchronized void init(Context ctx) {
        if (ctx == null) return;
        sAppContext = ctx.getApplicationContext();
        SharedPreferences sp = prefs();

        if (sp.contains(KEY_ACTIVE_LANG)) {
            sActiveLang = sp.getString(KEY_ACTIVE_LANG, DEFAULT_LANG);
        } else {
            String sysLang = detectSystemLanguage();
            sActiveLang = (sysLang != null) ? sysLang : DEFAULT_LANG;
        }

        if (!loadFromPrefs(sActiveLang)) loadFallback();

        long lastFetch = sp.getLong(KEY_LAST_FETCH, 0L);
        if (System.currentTimeMillis() - lastFetch > FETCH_INTERVAL_MS) {
            fetchRemoteAsync();
        }
    }

    public static void ensureFirstRunTranslations(long timeoutMs) {
        SharedPreferences sp = prefs();
        boolean hasData = !sp.getString(KEY_LANGS_LIST, "").isEmpty();
        if (!hasData) {
            fetchRemoteSync(timeoutMs);
        }
        synchronized (Lang.class) {
            if (!sp.contains(KEY_ACTIVE_LANG)) {
                String sysLang = detectSystemLanguage();
                if (sysLang != null) sActiveLang = sysLang;
            }
            if (loadFromPrefs(sActiveLang)) {
                sMainHandler.post(new Runnable() {
						@Override public void run() { notifyListeners(); }
					});
            }
        }
    }

    private static String detectSystemLanguage() {
        try {
            Locale loc = Locale.getDefault();
            if (loc == null) return null;
            String code = loc.getLanguage();
            if (code == null || code.isEmpty()) return null;
            code = code.toLowerCase(Locale.ROOT);

            String csv = prefs().getString(KEY_LANGS_LIST, "");
            if (!csv.isEmpty()) {
                for (String s : csv.split(",")) {
                    if (code.equals(s.trim())) return code;
                }
                return null;
            }

            for (String supported : BUILTIN_LANGS) {
                if (supported.equals(code)) return code;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void fetchRemoteSync(long timeoutMs) {
        try {
            int t = (int) Math.max(1000L, timeoutMs);
            String body = downloadWithTimeout(REMOTE_URL, t, t);
            if (body == null || body.isEmpty()) return;
            ParseResult parsed = parseLangFile(body);
            if (parsed.strings.isEmpty()) return;
            persistParsed(parsed, true);
            Log.i(TAG, "fetchRemoteSync OK: " + parsed.strings.size() + " idioma(s)");
        } catch (Exception e) {
            Log.w(TAG, "fetchRemoteSync: " + e.getMessage());
        }
    }

    private static String downloadWithTimeout(String urlStr, int connectMs, int readMs)
	throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(connectMs);
        c.setReadTimeout(readMs);
        c.setRequestProperty("User-Agent", "MiNAApp/1.0");
        c.connect();
        try {
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK)
                throw new Exception("HTTP " + c.getResponseCode());
            BufferedReader r = new BufferedReader(
                new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            r.close();
            return sb.toString();
        } finally { c.disconnect(); }
    }

    public static String get(int id) {
        String s = sStrings.get(id);
        if (s != null) return s;
        String fb = FALLBACK.get(id);
        return fb != null ? fb : "[" + id + "]";
    }

    public static String f(int id, Object... args) {
        String t = get(id);
        try { return String.format(t, args); } catch (Exception e) { return t; }
    }

    public static String getActiveLanguage() { return sActiveLang; }

    public static String getLocaleTag() {
        String l = sActiveLang;
        if ("es".equals(l)) return "es-ES";
        if ("en".equals(l)) return "en-US";
        if ("pt".equals(l)) return "pt-BR";
        if ("fr".equals(l)) return "fr-FR";
        if ("de".equals(l)) return "de-DE";
        if ("it".equals(l)) return "it-IT";
        if ("ja".equals(l)) return "ja-JP";
        if ("zh".equals(l)) return "zh-CN";
        if ("ru".equals(l)) return "ru-RU";
        return l;
    }

    /**
     * Lista de idiomas disponibles. Se obtiene EXCLUSIVAMENTE del CSV
     * persistido desde el lang.json remoto. Sin hardcodeo.
     */
    public static List<String> getAvailableLanguages() {
        TreeSet<String> set = new TreeSet<String>();
        String csv = prefs().getString(KEY_LANGS_LIST, "");
        if (!csv.isEmpty()) {
            for (String s : csv.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) set.add(t);
            }
        }
        return new ArrayList<String>(set);
    }

    /**
     * Nombre visible de un idioma. Se obtiene EXCLUSIVAMENTE del JSON de
     * nombres persistido desde el lang.json remoto. Sin hardcodeo.
     * Si no hay nombre guardado, se muestra el código en mayúsculas.
     */
    public static String getDisplayName(String langId) {
        if (langId == null || langId.isEmpty()) return "";
        String stored = getStoredLangName(langId);
        if (stored != null && !stored.isEmpty()) return stored;
        return langId.toUpperCase(Locale.ROOT);
    }

    private static String getStoredLangName(String langId) {
        String json = prefs().getString(KEY_LANG_NAMES, "");
        if (json == null || json.isEmpty()) return null;
        try {
            JSONObject obj = new JSONObject(json);
            String n = obj.optString(langId, null);
            return (n == null || n.isEmpty()) ? null : n;
        } catch (Exception e) {
            return null;
        }
    }

    public static synchronized void setLanguage(Context ctx, String langId) {
        if (langId == null || langId.isEmpty()) return;
        if (langId.equals(sActiveLang)) return;
        sActiveLang = langId;
        prefs().edit().putString(KEY_ACTIVE_LANG, langId).apply();
        if (!loadFromPrefs(langId)) loadFallback();
        notifyListeners();
    }

    public static void addListener(Listener l) {
        if (l != null && !sListeners.contains(l)) sListeners.add(l);
    }
    public static void removeListener(Listener l) {
        if (l != null) sListeners.remove(l);
    }
    public static void addLangListListener(LangListListener l) {
        if (l != null && !sLangListListeners.contains(l)) sLangListListeners.add(l);
    }
    public static void removeLangListListener(LangListListener l) {
        if (l != null) sLangListListeners.remove(l);
    }
    public static void refreshLanguages() { fetchRemoteAsync(); }

    public static void clearCache() {
        SharedPreferences.Editor ed = prefs().edit();
        ed.remove(KEY_LANGS_LIST);
        ed.remove(KEY_LANG_NAMES);
        ed.remove(KEY_LAST_FETCH);
        for (String b : BUILTIN_LANGS) ed.remove(KEY_DATA_PREFIX + b);
        ed.apply();
        loadFallback();
    }

    private static SharedPreferences prefs() {
        return sAppContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static void notifyListeners() {
        sMainHandler.post(new Runnable() {
				@Override public void run() {
					for (Listener l : sListeners) {
						try { l.onLanguageChanged(); }
						catch (Exception e) { Log.w(TAG, "listener: " + e.getMessage()); }
					}
				}
			});
    }

    private static void notifyLangListListeners() {
        sMainHandler.post(new Runnable() {
				@Override public void run() {
					for (LangListListener l : sLangListListeners) {
						try { l.onLanguagesChanged(); }
						catch (Exception e) { Log.w(TAG, "langList: " + e.getMessage()); }
					}
				}
			});
    }

    private static boolean loadFromPrefs(String langId) {
        String json = prefs().getString(KEY_DATA_PREFIX + langId, null);
        if (json == null || json.isEmpty()) return false;
        try {
            JSONObject obj = new JSONObject(json);
            Map<Integer, String> map = new HashMap<Integer, String>();
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                try { map.put(Integer.parseInt(k), obj.getString(k)); }
                catch (NumberFormatException ignored) { }
            }
            if (map.isEmpty()) return false;
            synchronized (sStrings) {
                sStrings.clear();
                sStrings.putAll(map);
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "loadFromPrefs(" + langId + "): " + e.getMessage());
            return false;
        }
    }

    private static void loadFallback() {
        synchronized (sStrings) {
            sStrings.clear();
            sStrings.putAll(FALLBACK);
        }
    }

    private static void fetchRemoteAsync() {
        new Thread(new Runnable() {
				@Override public void run() {
					try {
						String body = download(REMOTE_URL);
						if (body == null || body.isEmpty()) return;
						ParseResult parsed = parseLangFile(body);
						if (parsed.strings.isEmpty()) return;
						persistParsed(parsed, false);
						if (loadFromPrefs(sActiveLang)) notifyListeners();
						notifyLangListListeners();
					} catch (Exception e) {
						Log.w(TAG, "fetchRemote: " + e.getMessage());
					}
				}
			}, "LangFetch").start();
    }

    private static void persistParsed(ParseResult parsed, boolean blocking)
	throws Exception {
        SharedPreferences.Editor ed = prefs().edit();
        StringBuilder csv = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Map<Integer, String>> e : parsed.strings.entrySet()) {
            JSONObject obj = new JSONObject();
            for (Map.Entry<Integer, String> s : e.getValue().entrySet()) {
                obj.put(String.valueOf(s.getKey()), s.getValue());
            }
            ed.putString(KEY_DATA_PREFIX + e.getKey(), obj.toString());
            if (!first) csv.append(',');
            csv.append(e.getKey());
            first = false;
        }
        ed.putString(KEY_LANGS_LIST, csv.toString());

        JSONObject namesObj = new JSONObject();
        for (Map.Entry<String, String> n : parsed.names.entrySet()) {
            namesObj.put(n.getKey(), n.getValue());
        }
        ed.putString(KEY_LANG_NAMES, namesObj.toString());

        ed.putLong(KEY_LAST_FETCH, System.currentTimeMillis());
        if (blocking) ed.commit(); else ed.apply();
    }

    private static String download(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "MiNAApp/1.0");
        c.connect();
        try {
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK)
                throw new Exception("HTTP " + c.getResponseCode());
            BufferedReader r = new BufferedReader(
                new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            r.close();
            return sb.toString();
        } finally { c.disconnect(); }
    }

    private static final class ParseResult {
        final Map<String, Map<Integer, String>> strings =
		new HashMap<String, Map<Integer, String>>();
        final Map<String, String> names = new HashMap<String, String>();
    }

    private static ParseResult parseLangFile(String body) {
        ParseResult result = new ParseResult();
        String currentLang = null;
        Map<Integer, String> currentMap = null;

        for (String rawLine : body.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            String lower = line.toLowerCase(Locale.ROOT);

            if (lower.startsWith("langid=")) {
                int eq = line.indexOf('=');
                int brace = line.indexOf('{', eq);
                String segment = (brace > eq)
                    ? line.substring(eq + 1, brace).trim()
                    : line.substring(eq + 1).trim();

                int sp = segment.indexOf(' ');
                String code = (sp > 0) ? segment.substring(0, sp).trim() : segment;
                code = code.replace("{", "").replace("}", "").trim();
                if (code.isEmpty()) continue;

                currentLang = code;
                currentMap = new HashMap<Integer, String>();

                int lnIdx = lower.indexOf("langname=");
                if (lnIdx >= 0) {
                    String rest = line.substring(lnIdx + 9)
                        .replace("{", "").replace("}", "").trim();
                    if (!rest.isEmpty()) result.names.put(currentLang, rest);
                }
                continue;
            }

            if (lower.startsWith("langname=") && currentLang != null) {
                String rest = line.substring(9)
                    .replace("{", "").replace("}", "").trim();
                if (!rest.isEmpty()) result.names.put(currentLang, rest);
                continue;
            }

            if ("}".equals(line) && currentMap != null && currentLang != null) {
                if (!currentMap.isEmpty()) result.strings.put(currentLang, currentMap);
                currentLang = null;
                currentMap = null;
                continue;
            }

            if (currentMap != null && lower.startsWith("textid=")) {
                int eq = line.indexOf('=');
                int br1 = line.indexOf('[', eq);
                int br2 = line.lastIndexOf(']');
                if (eq >= 0 && br1 > eq && br2 > br1) {
                    try {
                        int id = Integer.parseInt(line.substring(eq + 1, br1).trim());
                        currentMap.put(id, line.substring(br1 + 1, br2));
                    } catch (NumberFormatException ignored) { }
                }
            }
        }
        return result;
    }

    private static final Map<Integer, String> FALLBACK = new HashMap<Integer, String>();
    static {
        FALLBACK.put(1000, "MiNA");
        FALLBACK.put(1001, "Asistente personal para Android");
        FALLBACK.put(1002, "Comprobando...");
        FALLBACK.put(1003, "Activo como asistente");
        FALLBACK.put(1004, "No es el asistente predeterminado");
        FALLBACK.put(1005, "MiNA es el asistente predeterminado del sistema.");
        FALLBACK.put(1006, "Pulsa «Establecer como asistente» para asignar MiNA.");
        FALLBACK.put(1007, "Acciones");
        FALLBACK.put(1008, "Establecer como asistente");
        FALLBACK.put(1009, "Abrir ajustes de asistente");
        FALLBACK.put(1010, "Probar asistente");
        FALLBACK.put(1011, "Idioma");
        FALLBACK.put(1012, "Cambiar idioma");
        FALLBACK.put(1013, "MiNA - v0.5");
        FALLBACK.put(1014, "Cómo funciona");
        FALLBACK.put(1015, "1. Establece MiNA como asistente.\n2. Mantén pulsado inicio.\n3. Di un comando.");
        FALLBACK.put(1050, "Seleccionar idioma");
        FALLBACK.put(1051, "Idioma actual: %s");
        FALLBACK.put(1052, "Cancelar");
        FALLBACK.put(1053, "Cerrar");
        FALLBACK.put(1054, "Cargando idiomas...");
        FALLBACK.put(1055, "No se pudieron cargar los idiomas");
        FALLBACK.put(1056, "Reintentar");
        FALLBACK.put(1100, "Pulsa el orbe para hablar");
        FALLBACK.put(1101, "Reconocimiento de voz no disponible");
        FALLBACK.put(1102, "Escuchando");
        FALLBACK.put(1103, "No te entendí, inténtalo de nuevo");
        FALLBACK.put(1104, "Permiso de micrófono denegado");
        FALLBACK.put(1105, "Configurar voz del sistema");
        FALLBACK.put(1106, "No pude abrir esa app o acción");
        FALLBACK.put(1107, "intent: %s");
        FALLBACK.put(1108, "Tú");
        FALLBACK.put(1109, "MiNA");
        FALLBACK.put(1150, "Falta permiso de micrófono");
        FALLBACK.put(1151, "No te entendí");
        FALLBACK.put(1152, "No escuché nada");
        FALLBACK.put(1153, "Sin conexión a internet");
        FALLBACK.put(1154, "Error de audio");
        FALLBACK.put(1155, "Motor ocupado, reintenta");
        FALLBACK.put(1156, "Error del servidor de voz");
        FALLBACK.put(1157, "Error del motor de voz");
        FALLBACK.put(1158, "Error de reconocimiento");
        FALLBACK.put(1159, "Reconocimiento no disponible");
        FALLBACK.put(1160, "No se pudo iniciar el micrófono");
        FALLBACK.put(1161, "Error de grabación");
        FALLBACK.put(1162, "No se pudo abrir el micrófono");
        FALLBACK.put(1163, "Error %d");
        FALLBACK.put(1200, "No te entendí");
        FALLBACK.put(1201, "Hola, soy MiNA. ¿En qué te ayudo?");
        FALLBACK.put(1202, "Me llamo MiNA");
        FALLBACK.put(1203, "De nada");
        FALLBACK.put(1204, "Hasta luego");
        FALLBACK.put(1205, "Son las %s");
        FALLBACK.put(1206, "Hoy es %s");
        FALLBACK.put(1207, "Linterna encendida");
        FALLBACK.put(1208, "Linterna apagada");
        FALLBACK.put(1209, "Este dispositivo no tiene linterna");
        FALLBACK.put(1210, "No tengo acceso a la cámara");
        FALLBACK.put(1211, "No pude controlar la linterna");
        FALLBACK.put(1212, "Subiendo volumen");
        FALLBACK.put(1213, "Bajando volumen");
        FALLBACK.put(1214, "Silenciado");
        FALLBACK.put(1215, "Volumen al máximo");
        FALLBACK.put(1216, "No pude cambiar el volumen");
        FALLBACK.put(1217, "No pude acceder al volumen");
        FALLBACK.put(1218, "Poniendo alarma a las %02d:%02d");
        FALLBACK.put(1219, "Dime a qué hora quieres la alarma");
        FALLBACK.put(1220, "Poniendo temporizador de %s");
        FALLBACK.put(1221, "Dime cuánto tiempo quieres el temporizador");
        FALLBACK.put(1222, "Abriendo marcador");
        FALLBACK.put(1223, "Marcando %s");
        FALLBACK.put(1224, "Abriendo cámara");
        FALLBACK.put(1225, "Abriendo ajustes de wifi");
        FALLBACK.put(1226, "Abriendo ajustes de bluetooth");
        FALLBACK.put(1227, "Abriendo ajustes");
        FALLBACK.put(1228, "Buscando en YouTube");
        FALLBACK.put(1229, "Buscando en Google");
        FALLBACK.put(1230, "Abriendo %s");
        FALLBACK.put(1231, "No encontré ninguna app llamada %s");
        FALLBACK.put(1232, "No sé cómo hacer eso todavía.");
        FALLBACK.put(1233, "Eso no lo tengo programado.");
        FALLBACK.put(1234, "Todavía no sé responder a eso.");
        FALLBACK.put(1235, "No tengo una respuesta para eso.");
        FALLBACK.put(1236, "Aún no sé hacer eso. Prueba con otra cosa.");
        FALLBACK.put(1237, "No puedo dividir por cero");
        FALLBACK.put(1238, "Son %s");
        FALLBACK.put(1239, "Puedo abrir apps, buscar en internet, poner alarmas y temporizadores, encender la linterna, controlar el volumen, abrir ajustes y la cámara, marcar números, y hacer cálculos.");
        FALLBACK.put(1240, "horas");
        FALLBACK.put(1241, "minutos");
        FALLBACK.put(1242, "segundos");
    }
}
