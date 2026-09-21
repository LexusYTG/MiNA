package com.Lexus2026.MiNA;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Motor NLP avanzado (Java 7 / Android).
 * Todos los textos (palabras clave, sinónimos, stopwords) se obtienen de Lang.
 * El único fallback hardcodeado es español (vive en Lang.FALLBACK).
 */
class NlpEngine {

    static class Result {
        final String concept;
        final double confidence;
        final List<String> alternatives;
        final List<ScoredMatch> allMatches;
        final String debugQuery;

        Result(String concept, double confidence,
               List<String> alternatives,
               List<ScoredMatch> allMatches,
               String debugQuery) {
            this.concept = concept;
            this.confidence = confidence;
            this.alternatives = alternatives;
            this.allMatches = allMatches;
            this.debugQuery = debugQuery;
        }
    }

    static class ScoredMatch {
        final String concept;
        double score;
        final String matched;
        final int matchType;

        ScoredMatch(String concept, double score, String matched, int matchType) {
            this.concept = concept;
            this.score = score;
            this.matched = matched;
            this.matchType = matchType;
        }
    }

    static final int MT_EXACT   = 100;
    static final int MT_PHRASE  = 92;
    static final int MT_NGRAM   = 85;
    static final int MT_TOKEN   = 75;
    static final int MT_STEM    = 65;
    static final int MT_SYNONYM = 58;
    static final int MT_PREFIX  = 52;
    static final int MT_FUZZY1  = 45;
    static final int MT_FUZZY2  = 35;
    static final int MT_PARTIAL = 25;
    static final int MT_TRAINED = 10;

    static final double T_CONFIDENT    = 0.55;
    static final double T_HINT         = 0.15;
    static final double AMBIGUITY_GAP  = 0.12;
    static final double AMBIGUITY_PEN  = 0.75;

    private static final double TRAIN_BOOST_PER_HIT = 0.030;
    private static final double TRAIN_BOOST_MAX     = 0.150;
    private static final double TRAIN_MIN_BASE      = 0.05;

    private volatile boolean trained = false;
    private volatile String  trainedLang = null;
    private volatile int     trainedVersion = -1;

    private volatile Map<String, Set<Integer>> tokenToIds =
	Collections.unmodifiableMap(new HashMap<String, Set<Integer>>());
    private volatile Map<Integer, Set<String>> idToConcepts =
	Collections.unmodifiableMap(new HashMap<Integer, Set<String>>());
    private volatile int indexedStrings = 0;

    // Caché de sinónimos por versión de datos de Lang.
    private volatile Map<String, String> cachedSynonyms = null;
    private volatile int cachedSynonymsVersion = -1;

    NlpEngine() { }

    synchronized void forceRetrain() {
        trained = false;
        train();
    }

    private void ensureTrained() {
        if (trained && trainedLang != null
            && trainedLang.equals(Lang.getActiveLanguage())
            && trainedVersion == Lang.getDataVersion()) {
            return;
        }
        synchronized (this) {
            if (trained && trainedLang != null
                && trainedLang.equals(Lang.getActiveLanguage())
                && trainedVersion == Lang.getDataVersion()) {
                return;
            }
            train();
        }
    }

    private Map<String, String> getSynMap() {
        int v = Lang.getDataVersion();
        Map<String, String> m = cachedSynonyms;
        if (m != null && cachedSynonymsVersion == v) return m;
        m = Lang.getSynonyms();
        cachedSynonyms = m;
        cachedSynonymsVersion = v;
        return m;
    }

    private void train() {
        Map<String, Set<Integer>> newTokToIds = new HashMap<String, Set<Integer>>();
        Map<Integer, Set<String>> newIdToConcepts = new HashMap<Integer, Set<String>>();

        Map<String, Integer> kwIds = Lang.getKwIds();
        if (kwIds != null) {
            for (Map.Entry<String, Integer> e : kwIds.entrySet()) {
                String concept = e.getKey();
                Integer id = e.getValue();
                if (concept == null || id == null) continue;
                addConcept(newIdToConcepts, id, concept);
            }
        }
        for (Map.Entry<String, String> e : INTENT_BY_CONCEPT.entrySet()) {
            String concept = e.getKey();
            String intent = e.getValue();
            if (concept == null || intent == null) continue;
            Integer respId = Lang.getResponseIdForIntent(intent);
            if (respId != null && respId > 0) {
                addConcept(newIdToConcepts, respId, concept);
            }
        }

        Map<Integer, String> all = Lang.snapshotAllStrings();
        int count = 0;
        if (all != null && !all.isEmpty()) {
            for (Map.Entry<Integer, String> e : all.entrySet()) {
                Integer id = e.getKey();
                String text = e.getValue();
                if (id == null || text == null || text.isEmpty()) continue;
                // Los IDs >= 800 son datos estructurados (CSV, formatos)
                // y no deben contaminar el índice NLP.
                if (id.intValue() >= 800) continue;
                String norm = normalize(text);
                if (norm.isEmpty()) continue;
                List<String> toks = splitWhitespace(norm);
                for (int i = 0; i < toks.size(); i++) {
                    String t = toks.get(i);
                    if (t.length() < 3) continue;
                    if (!containsLetter(t)) continue;
                    String s = stem(t);
                    if (s.length() < 2) continue;
                    Set<Integer> ids = newTokToIds.get(s);
                    if (ids == null) {
                        ids = new HashSet<Integer>();
                        newTokToIds.put(s, ids);
                    }
                    ids.add(id);
                }
                count++;
            }
        }

        int threshold = Math.max(6, count / 10);
        Iterator<Map.Entry<String, Set<Integer>>> it =
            newTokToIds.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().size() > threshold) it.remove();
        }

        Map<String, Set<Integer>> frozenTok = new HashMap<String, Set<Integer>>();
        for (Map.Entry<String, Set<Integer>> e : newTokToIds.entrySet()) {
            frozenTok.put(e.getKey(),
                          Collections.unmodifiableSet(new HashSet<Integer>(e.getValue())));
        }
        Map<Integer, Set<String>> frozenId = new HashMap<Integer, Set<String>>();
        for (Map.Entry<Integer, Set<String>> e : newIdToConcepts.entrySet()) {
            frozenId.put(e.getKey(),
                         Collections.unmodifiableSet(new HashSet<String>(e.getValue())));
        }

        tokenToIds    = Collections.unmodifiableMap(frozenTok);
        idToConcepts  = Collections.unmodifiableMap(frozenId);
        indexedStrings = count;
        trainedLang   = Lang.getActiveLanguage();
        trainedVersion = Lang.getDataVersion();
        trained       = true;
    }

    private static void addConcept(Map<Integer, Set<String>> map,
                                   Integer id, String concept) {
        Set<String> s = map.get(id);
        if (s == null) {
            s = new HashSet<String>();
            map.put(id, s);
        }
        s.add(concept);
    }

    private static boolean containsLetter(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) return true;
        }
        return false;
    }

    private static List<String> splitWhitespace(String q) {
        List<String> out = new ArrayList<String>();
        if (q == null || q.isEmpty()) return out;
        String[] parts = q.split("\\s+");
        for (String p : parts) if (!p.isEmpty()) out.add(p);
        return out;
    }

    private void applyTrainingBoost(Map<String, Double> scores,
                                    List<String> tokens,
                                    List<String> stems) {
        if (tokens.isEmpty() || scores == null) return;
        Map<String, Set<Integer>> idx = tokenToIds;
        Map<Integer, Set<String>>  idc = idToConcepts;
        if (idx.isEmpty() || idc.isEmpty()) return;

        Map<String, Integer> boostCount = new HashMap<String, Integer>();

        for (int i = 0; i < tokens.size(); i++) {
            String stem = (i < stems.size()) ? stems.get(i) : null;
            if (stem == null || stem.length() < 3) continue;
            Set<Integer> ids = idx.get(stem);
            if (ids == null) ids = idx.get(tokens.get(i));
            if (ids == null) continue;
            for (Integer id : ids) {
                Set<String> concepts = idc.get(id);
                if (concepts == null) continue;
                for (String c : concepts) {
                    Integer cur = boostCount.get(c);
                    boostCount.put(c, cur == null ? 1 : cur + 1);
                }
            }
        }

        if (boostCount.isEmpty()) return;

        for (Map.Entry<String, Integer> e : boostCount.entrySet()) {
            String concept = e.getKey();
            int n = e.getValue();
            double boost = Math.min(TRAIN_BOOST_MAX, n * TRAIN_BOOST_PER_HIT);
            Double cur = scores.get(concept);
            double base = (cur == null) ? 0.0 : cur;
            if (base <= 0 && n < 2) continue;
            if (base <= 0) base = TRAIN_MIN_BASE;
            scores.put(concept, Math.min(1.0, base + boost));
        }
    }

    Result analyze(String rawQuery) {
        if (rawQuery == null) return empty("");
        String q = normalize(rawQuery);
        if (q.isEmpty()) return empty(q);

        ensureTrained();

        Map<String, String> allKw = Lang.getAllConceptKeywords();
        if (allKw.isEmpty()) return empty(q);

        List<String> tokens = tokenize(q);
        if (tokens.isEmpty()) return empty(q);

        Map<String, String> synMap = getSynMap();

        List<String> tokenStems = new ArrayList<String>(tokens.size());
        for (int i = 0; i < tokens.size(); i++) tokenStems.add(stem(tokens.get(i)));

        List<String> tokenSyns = new ArrayList<String>(tokens.size());
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            String s = synMap.get(t);
            tokenSyns.add(s != null ? s : t);
        }

        List<String> qBigrams  = ngrams(tokens, 2);
        List<String> qTrigrams = ngrams(tokens, 3);

        int totalChars = q.replace(" ", "").length();
        if (totalChars == 0) return empty(q);

        Map<String, Double> scores = new HashMap<String, Double>();

        for (Map.Entry<String, String> e : allKw.entrySet()) {
            String concept = e.getKey();
            String kws = e.getValue();
            if (kws == null || kws.isEmpty()) continue;

            double bestScore = 0;
            String bestKw = null;
            int bestType = 0;
            int matchedKwCount = 0;

            String[] parts = kws.split(",");
            for (String kwRaw : parts) {
                String kw = normalize(kwRaw);
                if (kw.isEmpty()) continue;
                ScoredMatch sm = scoreKeyword(concept, kw, q, tokens,
                                              tokenStems, tokenSyns,
                                              qBigrams, qTrigrams, totalChars,
                                              synMap);
                if (sm == null) continue;
                if (sm.score > 0.15) matchedKwCount++;
                if (sm.score > bestScore) {
                    bestScore = sm.score;
                    bestKw    = sm.matched;
                    bestType  = sm.matchType;
                }
            }

            if (bestScore <= 0) continue;
            double countBoost = Math.min(0.15, matchedKwCount * 0.05);
            double finalScore = Math.min(1.0, bestScore + countBoost);
            scores.put(concept, finalScore);
        }

        applyTrainingBoost(scores, tokens, tokenStems);

        if (scores.isEmpty()) return empty(q);

        List<ScoredMatch> allMatches = new ArrayList<ScoredMatch>(scores.size());
        for (Map.Entry<String, Double> e : scores.entrySet()) {
            allMatches.add(new ScoredMatch(e.getKey(), e.getValue(), "", MT_TRAINED));
        }
        Collections.sort(allMatches, new Comparator<ScoredMatch>() {
				@Override public int compare(ScoredMatch a, ScoredMatch b) {
					return Double.compare(b.score, a.score);
				}
			});

        ScoredMatch top = allMatches.get(0);
        double topScore    = top.score;
        double secondScore = allMatches.size() > 1 ? allMatches.get(1).score : 0.0;

        double confidence = topScore;
        double gap = topScore - secondScore;
        if (secondScore > 0 && gap < AMBIGUITY_GAP) {
            confidence *= AMBIGUITY_PEN;
        }

        List<String> alts = new ArrayList<String>();
        for (int i = 1; i < allMatches.size() && alts.size() < 3; i++) {
            alts.add(allMatches.get(i).concept);
        }

        return new Result(top.concept, confidence, alts, allMatches, q);
    }

    private Result empty(String q) {
        return new Result(null, 0, Collections.<String>emptyList(),
                          Collections.<ScoredMatch>emptyList(), q);
    }

    Lang.IntentMatch toIntentMatch(Result r) {
        if (r == null || r.concept == null) return null;
        String intentId = INTENT_BY_CONCEPT.get(r.concept);
        if (intentId == null) return null;
        Integer respId = Lang.getResponseIdForIntent(intentId);
        return new Lang.IntentMatch(intentId,
                                    respId != null ? respId : 0,
                                    new HashMap<String, String>());
    }

    /** Devuelve el intent asociado a un concepto, o null si no existe. */
    public static String intentForConcept(String concept) {
        if (concept == null) return null;
        return INTENT_BY_CONCEPT.get(concept);
    }

    String formulateHint(Result r) {
        if (r == null || r.concept == null) return Lang.get(406);
        int descId = actionDescriptionId(r.concept);
        String action = (descId > 0) ? Lang.get(descId) : r.concept;

        if (r.alternatives != null && !r.alternatives.isEmpty()
            && r.confidence < 0.35) {
            String alt = r.alternatives.get(0);
            int altDescId = actionDescriptionId(alt);
            if (altDescId > 0) {
                String altAction = Lang.get(altDescId);
                return Lang.f(725, action, altAction);
            }
        }

        int tpl;
        if (r.confidence >= T_HINT + 0.20)      tpl = 405;
        else if (r.confidence >= T_HINT + 0.10) tpl = 400;
        else if (r.confidence >= T_HINT + 0.03) tpl = 407;
        else                                    tpl = 401;

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
            if (i > 0) sb.append(i == suggestions.length - 1 ? Lang.get(730) : ", ");
            sb.append(suggestions[i]);
        }
        sb.append(".");
        return sb.toString();
    }

    private ScoredMatch scoreKeyword(String concept, String kw, String q,
                                     List<String> tokens, List<String> stems,
                                     List<String> syns, List<String> bigrams,
                                     List<String> trigrams, int totalChars,
                                     Map<String, String> synMap) {
        if (kw.isEmpty()) return null;

        if (q.equals(kw)) return new ScoredMatch(concept, 1.0, kw, MT_EXACT);

        if (kw.indexOf(' ') >= 0) {
            if (q.contains(" " + kw + " ")
                || q.startsWith(kw + " ")
                || q.endsWith(" " + kw)) {
                double ratio = (double) kw.replace(" ", "").length() / totalChars;
                double sc = 0.75 + Math.min(0.20, ratio * 0.30);
                return new ScoredMatch(concept, Math.min(1.0, sc), kw, MT_PHRASE);
            }
        }
        if (q.contains(kw)) {
            double ratio = (double) kw.replace(" ", "").length() / totalChars;
            double sc = 0.60 + Math.min(0.25, ratio * 0.35);
            return new ScoredMatch(concept, Math.min(1.0, sc), kw, MT_PHRASE);
        }

        if (kw.indexOf(' ') >= 0) {
            String ngramJoined = " " + kw + " ";
            if (containsNgram(bigrams, ngramJoined)
                || containsNgram(trigrams, ngramJoined)) {
                double ratio = (double) kw.replace(" ", "").length() / totalChars;
                double sc = 0.65 + Math.min(0.20, ratio * 0.30);
                return new ScoredMatch(concept, Math.min(1.0, sc), kw, MT_NGRAM);
            }
        }

        String kwStem = stem(kw);
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).equals(kw)) {
                double ratio = (double) kw.length() / totalChars;
                double sc = 0.68 + Math.min(0.15, ratio * 0.25);
                return new ScoredMatch(concept, Math.min(1.0, sc), kw, MT_TOKEN);
            }
        }

        if (kwStem.length() >= 3) {
            for (int i = 0; i < stems.size(); i++) {
                if (stems.get(i).equals(kwStem) && kwStem.length() >= 3) {
                    double ratio = (double) kwStem.length() / totalChars;
                    double sc = 0.55 + Math.min(0.15, ratio * 0.25);
                    return new ScoredMatch(concept, Math.min(1.0, sc), kw, MT_STEM);
                }
            }
        }

        String kwSyn = synMap != null ? synMap.get(kw) : null;
        if (kwSyn != null) {
            for (int i = 0; i < syns.size(); i++) {
                String s = syns.get(i);
                if (s.equals(kwSyn) || s.equals(kw)) {
                    double ratio = (double) kw.length() / totalChars;
                    double sc = 0.55 + Math.min(0.15, ratio * 0.25);
                    return new ScoredMatch(concept, Math.min(1.0, sc), kw, MT_SYNONYM);
                }
            }
        }

        if (kw.length() >= 4) {
            for (String tok : tokens) {
                if (tok.length() >= 4) {
                    if (kw.startsWith(tok) || tok.startsWith(kw)) {
                        int common = Math.min(kw.length(), tok.length());
                        if (common >= 4) {
                            double ratio = (double) common / totalChars;
                            double sc = 0.45 + Math.min(0.15, ratio * 0.25);
                            return new ScoredMatch(concept, Math.min(1.0, sc), kw, MT_PREFIX);
                        }
                    }
                }
            }
        }

        int kwLen = kw.length();
        int fuzzyThreshold = kwLen <= 4 ? 1 : (kwLen <= 7 ? 1 : 2);
        for (String tok : tokens) {
            if (tok.length() < 3) continue;
            int dist = levenshtein(kw, tok);
            if (dist == 1 && Math.abs(kwLen - tok.length()) <= 2) {
                double ratio = (double) kwLen / totalChars;
                double sc = 0.40 + Math.min(0.12, ratio * 0.20);
                return new ScoredMatch(concept, Math.min(1.0, sc), kw, MT_FUZZY1);
            }
            if (dist == 2 && fuzzyThreshold == 2
                && Math.abs(kwLen - tok.length()) <= 2) {
                double ratio = (double) kwLen / totalChars;
                double sc = 0.30 + Math.min(0.10, ratio * 0.18);
                return new ScoredMatch(concept, Math.min(1.0, sc), kw, MT_FUZZY2);
            }
        }

        if (kwLen >= 4) {
            for (String tok : tokens) {
                if (tok.length() >= 4) {
                    if (kw.contains(tok) || tok.contains(kw)) {
                        double ratio = (double) Math.min(kwLen, tok.length()) / totalChars;
                        double sc = 0.22 + Math.min(0.08, ratio * 0.15);
                        return new ScoredMatch(concept, Math.min(1.0, sc), kw, MT_PARTIAL);
                    }
                }
            }
        }

        return null;
    }

    static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        n = n.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        n = n.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(n.length());
        boolean lastSpace = true;
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
                lastSpace = false;
            } else {
                if (!lastSpace) {
                    sb.append(' ');
                    lastSpace = true;
                }
            }
        }
        return sb.toString().trim();
    }

    private static List<String> tokenize(String q) {
        List<String> out = new ArrayList<String>();
        if (q.isEmpty()) return out;
        String[] parts = q.split("\\s+");
        Set<String> stop = stopwordSet();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (p.length() < 2) continue;
            if (stop.contains(p)) continue;
            out.add(p);
        }
        return out;
    }

    private static Set<String> stopwordSet() {
        Set<String> s = new HashSet<String>();
        String[] sw = Lang.getStopWords();
        if (sw != null) {
            for (int i = 0; i < sw.length; i++) {
                String n = normalize(sw[i]);
                if (!n.isEmpty()) s.add(n);
            }
        }
        return s;
    }

    static String stem(String word) {
        if (word == null || word.length() < 4) return word;
        String w = word;

        if (w.length() > 4 && w.endsWith("es")) {
            String base = w.substring(0, w.length() - 2);
            if (base.length() >= 3) w = base;
        } else if (w.length() > 3 && w.endsWith("s")) {
            String base = w.substring(0, w.length() - 1);
            if (base.length() >= 3) w = base;
        }

        if (w.length() > 5 && w.endsWith("ando")) w = w.substring(0, w.length() - 4);
        else if (w.length() > 5 && w.endsWith("endo")) w = w.substring(0, w.length() - 4);
        else if (w.length() > 5 && w.endsWith("iendo")) w = w.substring(0, w.length() - 5);
        else if (w.length() > 4 && w.endsWith("ado")) w = w.substring(0, w.length() - 3);
        else if (w.length() > 4 && w.endsWith("ido")) w = w.substring(0, w.length() - 3);
        else if (w.length() > 4 && w.endsWith("cion")) w = w.substring(0, w.length() - 4);
        else if (w.length() > 4 && w.endsWith("sion")) w = w.substring(0, w.length() - 4);

        if (w.length() > 4) {
            if (w.endsWith("ar")) w = w.substring(0, w.length() - 2);
            else if (w.endsWith("er")) w = w.substring(0, w.length() - 2);
            else if (w.endsWith("ir")) w = w.substring(0, w.length() - 2);
        }

        if (w.length() > 5 && w.endsWith("ing")) w = w.substring(0, w.length() - 3);
        else if (w.length() > 4 && w.endsWith("ed")) w = w.substring(0, w.length() - 2);

        if (w.length() > 6 && w.endsWith("en")) w = w.substring(0, w.length() - 2);

        return w;
    }

    private static List<String> ngrams(List<String> tokens, int n) {
        List<String> out = new ArrayList<String>();
        if (tokens.size() < n) return out;
        for (int i = 0; i + n <= tokens.size(); i++) {
            StringBuilder sb = new StringBuilder(" ");
            for (int j = 0; j < n; j++) {
                if (j > 0) sb.append(' ');
                sb.append(tokens.get(i + j));
            }
            sb.append(' ');
            out.add(sb.toString());
        }
        return out;
    }

    private static boolean containsNgram(List<String> ngrams, String joined) {
        for (int i = 0; i < ngrams.size(); i++) {
            if (ngrams.get(i).equals(joined)) return true;
        }
        return false;
    }

    private static int levenshtein(String a, String b) {
        if (a == null || b == null) return Integer.MAX_VALUE;
        int la = a.length(), lb = b.length();
        if (la == 0) return lb;
        if (lb == 0) return la;
        if (Math.abs(la - lb) > 3) return Integer.MAX_VALUE;
        int[] prev = new int[lb + 1];
        int[] cur  = new int[lb + 1];
        for (int j = 0; j <= lb; j++) prev[j] = j;
        for (int i = 1; i <= la; i++) {
            cur[0] = i;
            for (int j = 1; j <= lb; j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                int a1 = cur[j - 1] + 1;
                int b1 = prev[j] + 1;
                int c1 = prev[j - 1] + cost;
                int m = a1 < b1 ? a1 : b1;
                cur[j] = m < c1 ? m : c1;
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[lb];
    }

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
        INTENT_BY_CONCEPT.put("recordar_evento", "create_event");
        INTENT_BY_CONCEPT.put("mi_agenda", "show_calendar");
        INTENT_BY_CONCEPT.put("traducir", "translate");
        INTENT_BY_CONCEPT.put("raiz_cuadrada", "calculate");
        INTENT_BY_CONCEPT.put("porcentaje", "calculate");
        INTENT_BY_CONCEPT.put("elevar", "calculate");
        INTENT_BY_CONCEPT.put("capital_pais", "search_google");
        INTENT_BY_CONCEPT.put("receta", "search_google");
        INTENT_BY_CONCEPT.put("significado", "search_google");
        INTENT_BY_CONCEPT.put("sinonimo", "search_google");
        INTENT_BY_CONCEPT.put("estado_animo", "how_are_you");
        INTENT_BY_CONCEPT.put("buenos_deseos", "greeting");
        INTENT_BY_CONCEPT.put("buen_fin_semana", "greeting");
        INTENT_BY_CONCEPT.put("broma", "tell_joke");
        INTENT_BY_CONCEPT.put("piropo", "love_you");
        INTENT_BY_CONCEPT.put("te_extrano", "love_you");
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
        ACTION_DESC.put("recordar_evento", 476);
        ACTION_DESC.put("mi_agenda", 477);
        ACTION_DESC.put("traducir", 478);
        ACTION_DESC.put("raiz_cuadrada", 479);
        ACTION_DESC.put("porcentaje", 480);
        ACTION_DESC.put("elevar", 481);
        ACTION_DESC.put("capital_pais", 482);
        ACTION_DESC.put("receta", 483);
        ACTION_DESC.put("significado", 484);
        ACTION_DESC.put("sinonimo", 485);
        ACTION_DESC.put("estado_animo", 486);
        ACTION_DESC.put("buenos_deseos", 487);
        ACTION_DESC.put("buen_fin_semana", 488);
        ACTION_DESC.put("broma", 489);
        ACTION_DESC.put("piropo", 490);
        ACTION_DESC.put("te_extrano", 491);
    }
}
