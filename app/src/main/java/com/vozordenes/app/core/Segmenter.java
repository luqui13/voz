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
    private static final int END_SILENCE_CHUNKS = 60; // 600 ms de silencio => fin

    private final java.util.ArrayList<Double> db = new java.util.ArrayList<>();
    private int speechStart = -1;
    private int lastLoud = -1;
    private int run = 0;

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
                }
            } else {
                run = 0;
            }
            return false;
        }
        if (chunkDb > base + OFF_DB) lastLoud = idx;
        return idx - lastLoud >= END_SILENCE_CHUNKS;
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
        int s = Math.max(0, first - 8) * CHUNK;
        int e = Math.min(chunks, last + 9) * CHUNK;
        return new int[]{s, e};
    }
}
