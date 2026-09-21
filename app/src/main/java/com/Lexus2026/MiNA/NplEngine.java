package com.Lexus2026.MiNA;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Motor NLP muy básico. No genera texto libre: puntúa la entrada del
 * usuario contra las palabras clave de cada concepto (definidas en Lang
 * por ID) y, cuando la coincidencia es parcial o nula, arma una respuesta
 * combinando fragmentos también definidos por ID en Lang.
 *
 * Todo el texto que sale de acá viene de Lang.get(id), por lo que es
 * completamente independiente del idioma activo.
 */
class NlpEngine {

    static class Result {
        final String concept;
        final double confidence;
        final List<String> alternatives;
        Result(String concept, double confidence, List<String> alternatives) {
            this.concept = concept;
            this.confidence = confidence;
            this.alternatives = alternatives;
        }
    }

    static final double T_CONFIDENT = 0.55;
    static final double T_HINT      = 0.15;

    NlpEngine() { }

    // ================================================================
    // ANALISIS
    // ================================================================

    Result analyze(String rawQuery) {
        if (rawQuery == null)
            return new Result(null, 0, Collections.<String>emptyList());
        String q = normalize(rawQuery);
        if (q.isEmpty())
            return new Result(null, 0, Collections.<String>emptyList());

        Map<String, String> allKw = Lang.getAllConceptKeywords();
        if (allKw.isEmpty())
            return new Result(null, 0, Collections.<String>emptyList());

        String[] tokens = q.split("\\s+");
        int totalChars = q.replace(" ", "").length();
        if (totalChars == 0)
            return new Result(null, 0, Collections.<String>emptyList());

        Map<String, Double> scores = new HashMap<String, Double>();
        for (Map.Entry<String, String> e : allKw.entrySet()) {
            String concept = e.getKey();
            String kws = e.getValue();
            if (kws == null || kws.isEmpty()) continue;

            double score = 0;
            for (String kwRaw : kws.split(",")) {
                String kw = normalize(kwRaw);
                if (kw.isEmpty()) continue;

                if (q.equals(kw)) {
                    score = Math.max(score, 1.0);
                    continue;
                }
                if (q.contains(kw)) {
                    double ratio = (double) kw.replace(" ", "").length() / totalChars;
                    score = Math.max(score, Math.min(0.95, 0.5 + ratio));
                    continue;
                }
                for (String tok : tokens) {
                    if (tok.length() < 3) continue;
                    if (kw.equals(tok)) {
                        score = Math.max(score, 0.7);
                    } else if (kw.startsWith(tok) || tok.startsWith(kw)) {
                        score = Math.max(score, 0.4);
                    } else if (levenshtein(kw, tok) <= 1) {
                        score = Math.max(score, 0.35);
                    }
                }
            }
            if (score > 0) scores.put(concept, score);
        }

        if (scores.isEmpty())
            return new Result(null, 0, Collections.<String>emptyList());

        List<Map.Entry<String, Double>> sorted =
            new ArrayList<Map.Entry<String, Double>>(scores.entrySet());
        Collections.sort(sorted, new Comparator<Map.Entry<String, Double>>() {
				@Override public int compare(Map.Entry<String, Double> a, Map.Entry<String, Double> b) {
					return Double.compare(b.getValue(), a.getValue());
				}
			});

        String best = sorted.get(0).getKey();
        double bestScore = sorted.get(0).getValue();
        List<String> alts = new ArrayList<String>();
        for (int i = 1; i < sorted.size() && alts.size() < 3; i++) alts.add(sorted.get(i).getKey());
        return new Result(best, bestScore, alts);
    }

    // ================================================================
    // FORMULACION DE RESPUESTAS
    // ================================================================

    Lang.IntentMatch toIntentMatch(Result r) {
        if (r.concept == null) return null;
        String intentId = INTENT_BY_CONCEPT.get(r.concept);
        if (intentId == null) return null;
        Integer respId = Lang.getResponseIdForIntent(intentId);
        return new Lang.IntentMatch(intentId, respId != null ? respId : 0,
                                    new HashMap<String, String>());
    }

    String formulateHint(Result r) {
        if (r.concept == null) return Lang.get(406);
        int descId = actionDescriptionId(r.concept);
        String action = (descId > 0) ? Lang.get(descId) : r.concept;
        int tpl;
        if (r.confidence >= T_HINT + 0.15) tpl = 405;
        else if (r.confidence >= T_HINT + 0.05) tpl = 400;
        else tpl = 407;
        return Lang.f(tpl, action);
    }

    String formulateUnknown(String rawQuery) {
        StringBuilder sb = new StringBuilder();
        sb.append(Lang.get(403));
        sb.append(" ");
        String[] suggestions = new String[]{
            Lang.get(410),
            Lang.get(468),
            Lang.get(411)
        };
        for (int i = 0; i < suggestions.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(suggestions[i]);
            if (i == suggestions.length - 1) sb.append(".");
        }
        return sb.toString();
    }

    // ================================================================
    // MAPEOS
    // ================================================================

    private static int actionDescriptionId(String concept) {
        if (concept == null) return -1;
        Integer id = ACTION_DESC.get(concept);
        return id != null ? id : -1;
    }

    private static final Map<String, String> INTENT_BY_CONCEPT =
	new HashMap<String, String>();
    static {
        INTENT_BY_CONCEPT.put("tiempo", "weather_here");
        INTENT_BY_CONCEPT.put("hora", "time");
        INTENT_BY_CONCEPT.put("fecha", "date");
        INTENT_BY_CONCEPT.put("linterna", "flashlight");
        INTENT_BY_CONCEPT.put("bateria", "battery");
        INTENT_BY_CONCEPT.put("volumen", "volume_up");
        INTENT_BY_CONCEPT.put("brillo", "brightness_up");
        INTENT_BY_CONCEPT.put("alarma", "alarm");
        INTENT_BY_CONCEPT.put("temporizador", "timer");
        INTENT_BY_CONCEPT.put("cronometro", "stopwatch");
        INTENT_BY_CONCEPT.put("ver_alarmas", "show_alarms");
        INTENT_BY_CONCEPT.put("llamar", "call");
        INTENT_BY_CONCEPT.put("contactos", "contacts");
        INTENT_BY_CONCEPT.put("llamadas_recientes", "recent_calls");
        INTENT_BY_CONCEPT.put("emergencia", "emergency");
        INTENT_BY_CONCEPT.put("camara", "camera");
        INTENT_BY_CONCEPT.put("selfie", "selfie");
        INTENT_BY_CONCEPT.put("video", "record_video");
        INTENT_BY_CONCEPT.put("galeria", "gallery");
        INTENT_BY_CONCEPT.put("ubicacion", "location");
        INTENT_BY_CONCEPT.put("mapa", "map");
        INTENT_BY_CONCEPT.put("youtube", "search_youtube");
        INTENT_BY_CONCEPT.put("wikipedia", "search_wikipedia");
        INTENT_BY_CONCEPT.put("playstore", "search_playstore");
        INTENT_BY_CONCEPT.put("amazon", "search_amazon");
        INTENT_BY_CONCEPT.put("mercadolibre", "search_mercadolibre");
        INTENT_BY_CONCEPT.put("steam", "search_steam");
        INTENT_BY_CONCEPT.put("netflix", "search_netflix");
        INTENT_BY_CONCEPT.put("spotify", "search_spotify");
        INTENT_BY_CONCEPT.put("reddit", "search_reddit");
        INTENT_BY_CONCEPT.put("twitter", "search_twitter");
        INTENT_BY_CONCEPT.put("tiktok", "search_tiktok");
        INTENT_BY_CONCEPT.put("imagenes", "search_images");
        INTENT_BY_CONCEPT.put("vuelos", "search_flights");
        INTENT_BY_CONCEPT.put("nota", "note_list");
        INTENT_BY_CONCEPT.put("lista", "list_show_todo");
        INTENT_BY_CONCEPT.put("compra", "list_show_shopping");
        INTENT_BY_CONCEPT.put("calcula", "calculate");
        INTENT_BY_CONCEPT.put("dado", "dice");
        INTENT_BY_CONCEPT.put("moneda", "coin");
        INTENT_BY_CONCEPT.put("numero_aleatorio", "random_number");
        INTENT_BY_CONCEPT.put("color_aleatorio", "random_color");
        INTENT_BY_CONCEPT.put("contrasena", "random_password");
        INTENT_BY_CONCEPT.put("ayuda", "help");
        INTENT_BY_CONCEPT.put("saludo", "greeting");
        INTENT_BY_CONCEPT.put("despedida", "goodbye");
        INTENT_BY_CONCEPT.put("gracias", "thanks");
        INTENT_BY_CONCEPT.put("ram", "ram");
        INTENT_BY_CONCEPT.put("almacenamiento", "storage");
        INTENT_BY_CONCEPT.put("modelo", "device_model");
        INTENT_BY_CONCEPT.put("version_android", "android_version");
        INTENT_BY_CONCEPT.put("ip", "ip");
        INTENT_BY_CONCEPT.put("operador", "carrier");
        INTENT_BY_CONCEPT.put("uptime", "uptime");
        INTENT_BY_CONCEPT.put("pantalla", "screen_info");
        INTENT_BY_CONCEPT.put("wifi", "network_info");
        INTENT_BY_CONCEPT.put("kernel", "kernel_version");
        INTENT_BY_CONCEPT.put("sim", "sim_info");
        INTENT_BY_CONCEPT.put("chiste", "tell_joke");
        INTENT_BY_CONCEPT.put("dato_curioso", "random_fact");
        INTENT_BY_CONCEPT.put("quien_eres", "who_are_you");
        INTENT_BY_CONCEPT.put("que_te_gusta", "what_do_you_like");
        INTENT_BY_CONCEPT.put("tienes_hambre", "are_you_hungry");
        INTENT_BY_CONCEPT.put("que_comes", "what_do_you_eat");
        INTENT_BY_CONCEPT.put("eres_gato", "are_you_a_cat");
        INTENT_BY_CONCEPT.put("miau", "say_meow");
    }

    private static final Map<String, Integer> ACTION_DESC =
	new HashMap<String, Integer>();
    static {
        ACTION_DESC.put("tiempo", 410);
        ACTION_DESC.put("hora", 411);
        ACTION_DESC.put("fecha", 412);
        ACTION_DESC.put("linterna", 413);
        ACTION_DESC.put("bateria", 414);
        ACTION_DESC.put("volumen", 415);
        ACTION_DESC.put("brillo", 416);
        ACTION_DESC.put("alarma", 417);
        ACTION_DESC.put("temporizador", 418);
        ACTION_DESC.put("cronometro", 419);
        ACTION_DESC.put("ver_alarmas", 420);
        ACTION_DESC.put("llamar", 421);
        ACTION_DESC.put("contactos", 422);
        ACTION_DESC.put("llamadas_recientes", 423);
        ACTION_DESC.put("emergencia", 424);
        ACTION_DESC.put("camara", 425);
        ACTION_DESC.put("selfie", 426);
        ACTION_DESC.put("video", 427);
        ACTION_DESC.put("galeria", 428);
        ACTION_DESC.put("ubicacion", 429);
        ACTION_DESC.put("mapa", 430);
        ACTION_DESC.put("youtube", 431);
        ACTION_DESC.put("wikipedia", 432);
        ACTION_DESC.put("playstore", 433);
        ACTION_DESC.put("amazon", 434);
        ACTION_DESC.put("mercadolibre", 435);
        ACTION_DESC.put("steam", 436);
        ACTION_DESC.put("netflix", 437);
        ACTION_DESC.put("spotify", 438);
        ACTION_DESC.put("reddit", 439);
        ACTION_DESC.put("twitter", 440);
        ACTION_DESC.put("tiktok", 441);
        ACTION_DESC.put("imagenes", 442);
        ACTION_DESC.put("vuelos", 443);
        ACTION_DESC.put("nota", 444);
        ACTION_DESC.put("lista", 445);
        ACTION_DESC.put("compra", 446);
        ACTION_DESC.put("calcula", 447);
        ACTION_DESC.put("dado", 448);
        ACTION_DESC.put("moneda", 449);
        ACTION_DESC.put("numero_aleatorio", 450);
        ACTION_DESC.put("color_aleatorio", 451);
        ACTION_DESC.put("contrasena", 452);
        ACTION_DESC.put("ayuda", 453);
        ACTION_DESC.put("saludo", 454);
        ACTION_DESC.put("despedida", 455);
        ACTION_DESC.put("gracias", 456);
        ACTION_DESC.put("ram", 457);
        ACTION_DESC.put("almacenamiento", 458);
        ACTION_DESC.put("modelo", 459);
        ACTION_DESC.put("version_android", 460);
        ACTION_DESC.put("ip", 461);
        ACTION_DESC.put("operador", 462);
        ACTION_DESC.put("uptime", 463);
        ACTION_DESC.put("pantalla", 464);
        ACTION_DESC.put("wifi", 465);
        ACTION_DESC.put("kernel", 466);
        ACTION_DESC.put("sim", 467);
        ACTION_DESC.put("chiste", 468);
        ACTION_DESC.put("dato_curioso", 469);
        ACTION_DESC.put("quien_eres", 470);
        ACTION_DESC.put("que_te_gusta", 471);
        ACTION_DESC.put("tienes_hambre", 472);
        ACTION_DESC.put("que_comes", 473);
        ACTION_DESC.put("eres_gato", 474);
        ACTION_DESC.put("miau", 475);
    }

    // ================================================================
    // UTILIDADES
    // ================================================================

    private static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        n = n.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n.toLowerCase(Locale.ROOT).trim();
    }

    private static int levenshtein(String a, String b) {
        if (a == null || b == null) return Integer.MAX_VALUE;
        int la = a.length(), lb = b.length();
        if (la == 0) return lb;
        if (lb == 0) return la;
        if (Math.abs(la - lb) > 2) return Integer.MAX_VALUE;
        int[] prev = new int[lb + 1];
        int[] cur  = new int[lb + 1];
        for (int j = 0; j <= lb; j++) prev[j] = j;
        for (int i = 1; i <= la; i++) {
            cur[0] = i;
            for (int j = 1; j <= lb; j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[lb];
    }
}
