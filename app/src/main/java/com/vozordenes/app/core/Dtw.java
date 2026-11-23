package com.vozordenes.app.core;

/** Alineamiento temporal dinámico entre dos secuencias de características. */
public final class Dtw {
    private Dtw() {}

    /** Distancia normalizada; Double.MAX_VALUE si las duraciones son incompatibles. */
    public static double distance(float[][] a, float[][] b) {
        int n = a.length, m = b.length;
        if (n == 0 || m == 0) return Double.MAX_VALUE;
        double ratio = (double) Math.max(n, m) / Math.min(n, m);
        if (ratio > 2.2) return Double.MAX_VALUE;
        int band = Math.max(Math.abs(n - m) + 5, Math.max(n, m) / 3);
        double inf = Double.MAX_VALUE / 4;
        double[] prev = new double[m + 1];
        double[] cur = new double[m + 1];
        java.util.Arrays.fill(prev, inf);
        prev[0] = 0;
        for (int i = 1; i <= n; i++) {
            java.util.Arrays.fill(cur, inf);
            int center = (int) ((long) i * m / n);
            int lo = Math.max(1, center - band);
            int hi = Math.min(m, center + band);
            for (int j = lo; j <= hi; j++) {
                double cost = local(a[i - 1], b[j - 1]);
                double best = prev[j - 1];
                if (prev[j] < best) best = prev[j];
                if (cur[j - 1] < best) best = cur[j - 1];
                cur[j] = cost + best;
            }
            double[] t = prev; prev = cur; cur = t;
        }
        double v = prev[m];
        if (v >= inf) return Double.MAX_VALUE;
        return v / (n + m);
    }

    private static double local(float[] x, float[] y) {
        double s = 0;
        for (int i = 0; i < x.length; i++) {
            double d = x[i] - y[i];
            s += d * d;
        }
        return Math.sqrt(s);
    }
}
