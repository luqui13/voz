package com.vozordenes.app.core;

import java.util.Arrays;

/**
 * Detección de voz por energía: ayuda a parar la grabación al terminar de hablar
 * y recorta el silencio de los extremos.
 */
public final class Segmenter {
    /** Tamaño de bloque de análisis: 10 ms a 16 kHz. */
    public static final int CHUNK = 160;
    private static final double ON_DB = 10.0;   // sobre el ruido de fondo para empezar
    private static final double OFF_DB = 7.0;   // por debajo de esto se considera silencio
    private static final int MIN_ON_CHUNKS = 4; // 40 ms seguidos para dar por iniciada la voz
    private static final int END_SILENCE_CHUNKS = 45; // 450 ms de silencio => fin
    /** Con ruido o música de fondo no hay silencio: el fin se busca respecto al pico de la voz. */
    private static final double REL_DB = 14.0;     // por debajo de (pico - 14 dB) cuenta como "ya no hablas"
    private static final int LONG_CHUNKS = 120;    // solo se aplica pasados 1,2 s de voz
    private static final int GAP_CHUNKS = 35;      // pausas de hasta 350 ms dentro de la frase

    private final java.util.ArrayList<Double> db = new java.util.ArrayList<>();
    private int speechStart = -1;
    private int lastLoud = -1;
    private int run = 0;
    private double peak = -1;
    private int lastRel = -1;

    public static double chunkDb(short[] pcm, int off, int len) {
        double s = 0;
        for (int i = 0; i < len; i++) s += (double) pcm[off + i] * pcm[off + i];
        return 10.0 * Math.log10(s / len + 1.0);
    }

    private double noise() {
        int n = db.size();
        double[] a = new double[n];
        for (int i = 0; i < n; i++) a[i] = db.get(i);
        Arrays.sort(a);
        return a[(int) (n * 0.15)];
    }

    /** Alimenta con el nivel (dB) de un bloque de 10 ms. Devuelve true si ya se puede parar. */
    public boolean feed(double chunkDb) {
        db.add(chunkDb);
        int idx = db.size() - 1;
        if (db.size() < 15) return false;
        double nz = noise();
        double base = Math.max(nz, 20.0);
        if (speechStart < 0) {
            if (chunkDb > base + ON_DB) {
                run++;
                if (run >= MIN_ON_CHUNKS) {
                    speechStart = idx - run + 1;
                    lastLoud = idx;
                    lastRel = idx;
                }
            } else {
                run = 0;
            }
            return false;
        }
        if (chunkDb > base + OFF_DB) lastLoud = idx;
        double sm = idx >= 2 ? (db.get(idx) + db.get(idx - 1) + db.get(idx - 2)) / 3.0 : chunkDb;
        if (sm > peak) peak = sm;
        if (sm > peak - REL_DB) lastRel = idx;
        if (idx - lastLoud >= END_SILENCE_CHUNKS) return true;             // silencio de siempre
        // con sonido de fondo continuo: han pasado >1,2 s y la voz ya bajó respecto a su pico
        return idx - speechStart >= LONG_CHUNKS && idx - lastRel >= END_SILENCE_CHUNKS;
    }

    public boolean speechDetected() { return speechStart >= 0; }

    /**
     * Devuelve {inicio, fin} en muestras del tramo con voz (con un margen), o null si no hay voz.
     */
    public static int[] trim(short[] pcm, int length) {
        int chunks = length / CHUNK;
        if (chunks < 20) return null;
        double[] d = new double[chunks];
        for (int i = 0; i < chunks; i++) d[i] = chunkDb(pcm, i * CHUNK, CHUNK);
        double[] sorted = d.clone();
        Arrays.sort(sorted);
        double nz = sorted[(int) (chunks * 0.15)];
        double base = Math.max(nz, 20.0);
        int first = -1, last = -1, run = 0;
        for (int i = 0; i < chunks; i++) {
            if (d[i] > base + ON_DB) {
                run++;
                if (run >= MIN_ON_CHUNKS && first < 0) first = i - run + 1;
            } else {
                run = 0;
            }
            if (d[i] > base + OFF_DB && first >= 0) last = i;
        }
        if (first < 0 || last < first) return null;
        if (last - first + 1 > LONG_CHUNKS) {
            // sonido de fondo continuo: quedarse con la ráfaga más fuerte (tu voz) y sus pausas cortas
            double[] sm = new double[chunks];
            for (int i = 0; i < chunks; i++) {
                int a = Math.max(0, i - 2);
                double t = 0;
                for (int k = a; k <= i; k++) t += d[k];
                sm[i] = t / (i - a + 1);
            }
            int pk = first;
            for (int i = first; i <= last; i++) if (sm[i] > sm[pk]) pk = i;
            double thr = sm[pk] - REL_DB;
            int s2 = pk, e2 = pk, gap = 0;
            for (int i = pk - 1; i >= first; i--) {
                if (sm[i] >= thr) { s2 = i; gap = 0; } else if (++gap > GAP_CHUNKS) break;
            }
            gap = 0;
            for (int i = pk + 1; i <= last; i++) {
                if (sm[i] >= thr) { e2 = i; gap = 0; } else if (++gap > GAP_CHUNKS) break;
            }
            if (e2 - s2 + 1 >= 20 && e2 - s2 < last - first) { first = s2; last = e2; }
        }
        int s = Math.max(0, first - 8) * CHUNK;
        int e = Math.min(chunks, last + 9) * CHUNK;
        return new int[]{s, e};
    }
}
