package com.vozordenes.app.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Compara lo que se acaba de decir con las muestras grabadas de cada orden.
 * El umbral de cada orden se calcula con sus propias muestras: la distancia máxima
 * entre una muestra y la más parecida de las demás, multiplicada por un margen.
 */
public final class VoiceMatcher {
    public static final int MIN_SAMPLES = 3;
    /** La 2ª opción tiene que estar al menos 8 % más lejos que la 1ª. */
    static final double MIN_RATIO = 1.08;
    /** Si la 1ª gana por mucho (2ª al menos 45 % más lejos), se tolera pasar el umbral hasta 1,5 veces. */
    static final double CLEAR_RATIO = 1.45;
    static final double CLEAR_FACTOR = 1.5;

    /** Almacén opcional (p. ej. en disco) del umbral de cada orden, para no recalcularlo cada vez. */
    public interface SpreadCache {
        Double get(String key);
        void put(String key, double value);
    }

    private static final java.util.Map<String, Double> MEMORY = new java.util.HashMap<String, Double>();

    /** Firma de las muestras de una orden: cambia si se añaden, borran o regrababan muestras. */
    static String signature(Entry e) {
        long frames = 0;
        double sum = 0;
        for (float[][] m : e.samples) {
            frames += m.length;
            if (m.length > 0) for (float v : m[0]) sum += v;
        }
        return e.id + "|" + e.samples.size() + "|" + frames + "|" + Math.round(sum * 1000);
    }

    private static synchronized double spreadFor(Entry e, SpreadCache disk) {
        String key = signature(e);
        Double v = MEMORY.get(key);
        if (v == null && disk != null) v = disk.get(key);
        if (v == null) {
            v = selfSpread(e.samples);
            if (disk != null) disk.put(key, v);
        }
        MEMORY.put(key, v);
        return v;
    }

    /** Una orden con sus muestras de voz. */
    public static final class Entry {
        public final String id;
        public final String name;
        public final List<float[][]> samples;
        public Entry(String id, String name, List<float[][]> samples) {
            this.id = id; this.name = name; this.samples = samples;
        }
    }

    public static final class Score {
        public final Entry entry;
        public final double distance;
        public final double threshold;
        public Score(Entry entry, double distance, double threshold) {
            this.entry = entry; this.distance = distance; this.threshold = threshold;
        }
        public boolean within() { return distance <= threshold; }
    }

    public static final class Result {
        /** Orden aceptada, o null si no se reconoce. */
        public final Entry accepted;
        /** Todas las órdenes ordenadas de más a menos parecida. */
        public final List<Score> ranking;
        /** Por qué se aceptó o se rechazó (para mostrarlo en la prueba). */
        public final String reason;
        public Result(Entry accepted, List<Score> ranking, String reason) {
            this.accepted = accepted; this.ranking = ranking; this.reason = reason;
        }
    }

    /** Distancia mínima entre cualquier par de muestras distintas: calibra el umbral propio. */
    public static double selfSpread(List<float[][]> samples) {
        double worst = 0;
        for (int i = 0; i < samples.size(); i++) {
            double best = Double.MAX_VALUE;
            for (int j = 0; j < samples.size(); j++) {
                if (i == j) continue;
                best = Math.min(best, Dtw.distance(samples.get(i), samples.get(j)));
            }
            if (best != Double.MAX_VALUE) worst = Math.max(worst, best);
        }
        return worst;
    }

    /**
     * @param slack multiplicador de tolerancia (1.0 estricto, 2.0 permisivo)
     */
    public static Result match(float[][] utterance, List<Entry> entries, double slack) {
        return match(utterance, entries, slack, null);
    }

    public static Result match(float[][] utterance, List<Entry> entries, double slack, SpreadCache disk) {
        List<Score> ranking = new ArrayList<>();
        for (Entry e : entries) {
            if (e.samples.size() < MIN_SAMPLES) continue;
            double best = Double.MAX_VALUE;
            for (float[][] s : e.samples) best = Math.min(best, Dtw.distance(utterance, s));
            double thr = spreadFor(e, disk) * slack;
            ranking.add(new Score(e, best, thr));
        }
        Collections.sort(ranking, new Comparator<Score>() {
            @Override public int compare(Score a, Score b) { return Double.compare(a.distance, b.distance); }
        });
        Entry accepted = null;
        String reason = "sin órdenes con muestras suficientes";
        if (!ranking.isEmpty()) {
            Score top = ranking.get(0);
            double ratio = ranking.size() > 1
                    ? ranking.get(1).distance / Math.max(top.distance, 1e-9) : Double.MAX_VALUE;
            if (top.within() && ratio >= MIN_RATIO) {
                accepted = top.entry;
                reason = "dentro del umbral";
            } else if (ranking.size() > 1 && top.distance <= top.threshold * CLEAR_FACTOR && ratio >= CLEAR_RATIO) {
                // pasa algo el umbral, pero gana con mucha ventaja a todas las demás
                accepted = top.entry;
                reason = "pasa el umbral pero gana con mucha ventaja";
            } else if (!top.within() && ratio >= MIN_RATIO) {
                reason = String.format(java.util.Locale.US, "la más cercana (%s) queda fuera del umbral: %.2f > %.2f",
                        top.entry.name, top.distance, top.threshold);
            } else {
                reason = String.format(java.util.Locale.US, "«%s» y «%s» salen casi igual de cerca",
                        top.entry.name, ranking.get(1).entry.name);
            }
        }
        return new Result(accepted, ranking, reason);
    }
}
