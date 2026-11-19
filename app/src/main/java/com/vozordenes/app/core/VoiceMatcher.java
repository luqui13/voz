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
        public Result(Entry accepted, List<Score> ranking) {
            this.accepted = accepted; this.ranking = ranking;
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
        List<Score> ranking = new ArrayList<>();
        for (Entry e : entries) {
            if (e.samples.size() < MIN_SAMPLES) continue;
            double best = Double.MAX_VALUE;
            for (float[][] s : e.samples) best = Math.min(best, Dtw.distance(utterance, s));
            double thr = selfSpread(e.samples) * slack;
            ranking.add(new Score(e, best, thr));
        }
        Collections.sort(ranking, new Comparator<Score>() {
            @Override public int compare(Score a, Score b) { return Double.compare(a.distance, b.distance); }
        });
        Entry accepted = null;
        if (!ranking.isEmpty()) {
            Score top = ranking.get(0);
            boolean ok = top.within();
            if (ok && ranking.size() > 1) {
                Score second = ranking.get(1);
                // la segunda opción no puede estar casi igual de cerca
                if (second.distance < top.distance * 1.08) ok = false;
            }
            if (ok) accepted = top.entry;
        }
        return new Result(accepted, ranking);
    }
}
