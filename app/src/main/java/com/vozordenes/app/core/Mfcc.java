package com.vozordenes.app.core;

/**
 * Extrae características MFCC (+ deltas) de audio PCM mono a 16 kHz.
 * Java puro, sin dependencias de Android.
 */
public final class Mfcc {
    public static final int SAMPLE_RATE = 16000;
    private static final int FRAME_LEN = 400;   // 25 ms
    private static final int HOP = 160;         // 10 ms
    private static final int FFT_SIZE = 512;
    private static final int NUM_FILTERS = 26;
    private static final int NUM_CEPS = 12;     // c1..c12
    public static final int DIMS = NUM_CEPS * 2; // + deltas

    private static final double[] WINDOW = new double[FRAME_LEN];
    private static final double[][] FILTERS = new double[NUM_FILTERS][FFT_SIZE / 2 + 1];
    private static final double[][] DCT = new double[NUM_CEPS][NUM_FILTERS];
    private static final double[] COS = new double[FFT_SIZE / 2];
    private static final double[] SIN = new double[FFT_SIZE / 2];

    static {
        for (int i = 0; i < FRAME_LEN; i++) {
            WINDOW[i] = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (FRAME_LEN - 1));
        }
        for (int i = 0; i < FFT_SIZE / 2; i++) {
            COS[i] = Math.cos(-2 * Math.PI * i / FFT_SIZE);
            SIN[i] = Math.sin(-2 * Math.PI * i / FFT_SIZE);
        }
        double lowMel = hzToMel(100), highMel = hzToMel(7000);
        double[] centers = new double[NUM_FILTERS + 2];
        for (int i = 0; i < centers.length; i++) {
            double mel = lowMel + (highMel - lowMel) * i / (NUM_FILTERS + 1);
            centers[i] = melToHz(mel) * FFT_SIZE / SAMPLE_RATE; // en bins
        }
        for (int m = 0; m < NUM_FILTERS; m++) {
            double l = centers[m], c = centers[m + 1], r = centers[m + 2];
            for (int k = 0; k <= FFT_SIZE / 2; k++) {
                double v = 0;
                if (k > l && k <= c) v = (k - l) / (c - l);
                else if (k > c && k < r) v = (r - k) / (r - c);
                FILTERS[m][k] = v;
            }
        }
        for (int i = 0; i < NUM_CEPS; i++) {
            for (int j = 0; j < NUM_FILTERS; j++) {
                DCT[i][j] = Math.cos(Math.PI * (i + 1) * (j + 0.5) / NUM_FILTERS);
            }
        }
    }

    private Mfcc() {}

    private static double hzToMel(double hz) { return 2595.0 * Math.log10(1.0 + hz / 700.0); }
    private static double melToHz(double mel) { return 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0); }

    /** Devuelve matriz [frames][DIMS] normalizada (media cero por dimensión). */
    public static float[][] compute(short[] pcm, int start, int end) {
        int n = end - start;
        if (n < FRAME_LEN) return null;
        int frames = 1 + (n - FRAME_LEN) / HOP;
        double[][] ceps = new double[frames][NUM_CEPS];
        double[] re = new double[FFT_SIZE];
        double[] im = new double[FFT_SIZE];
        double[] logE = new double[NUM_FILTERS];
        for (int f = 0; f < frames; f++) {
            int off = start + f * HOP;
            java.util.Arrays.fill(re, 0);
            java.util.Arrays.fill(im, 0);
            double prev = pcm[off];
            for (int i = 0; i < FRAME_LEN; i++) {
                double s = pcm[off + i];
                double v = (i == 0) ? s : s - 0.97 * prev;
                prev = s;
                re[i] = v * WINDOW[i];
            }
            fft(re, im);
            for (int m = 0; m < NUM_FILTERS; m++) {
                double e = 0;
                double[] fl = FILTERS[m];
                for (int k = 0; k <= FFT_SIZE / 2; k++) {
                    if (fl[k] != 0) e += fl[k] * (re[k] * re[k] + im[k] * im[k]);
                }
                logE[m] = Math.log(e + 1e-3);
            }
            for (int i = 0; i < NUM_CEPS; i++) {
                double s = 0;
                for (int j = 0; j < NUM_FILTERS; j++) s += DCT[i][j] * logE[j];
                ceps[f][i] = s;
            }
        }
        // normalización de media (quita el efecto del micrófono / canal)
        double[] mean = new double[NUM_CEPS];
        for (double[] c : ceps) for (int i = 0; i < NUM_CEPS; i++) mean[i] += c[i];
        for (int i = 0; i < NUM_CEPS; i++) mean[i] /= frames;
        for (double[] c : ceps) for (int i = 0; i < NUM_CEPS; i++) c[i] -= mean[i];

        float[][] out = new float[frames][DIMS];
        for (int f = 0; f < frames; f++) {
            for (int i = 0; i < NUM_CEPS; i++) {
                out[f][i] = (float) (ceps[f][i] / 10.0);
                // delta por regresión sobre +-2 tramas
                double num = 0;
                for (int t = 1; t <= 2; t++) {
                    int a = Math.min(frames - 1, f + t);
                    int b = Math.max(0, f - t);
                    num += t * (ceps[a][i] - ceps[b][i]);
                }
                out[f][NUM_CEPS + i] = (float) (num / 10.0 / 10.0 * 1.5);
            }
        }
        return out;
    }

    private static void fft(double[] re, double[] im) {
        int n = re.length;
        int j = 0;
        for (int i = 1; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            int half = len / 2;
            int step = n / len;
            for (int i = 0; i < n; i += len) {
                for (int k = 0; k < half; k++) {
                    double wr = COS[k * step], wi = SIN[k * step];
                    int a = i + k, b = i + k + half;
                    double xr = re[b] * wr - im[b] * wi;
                    double xi = re[b] * wi + im[b] * wr;
                    re[b] = re[a] - xr; im[b] = im[a] - xi;
                    re[a] += xr; im[a] += xi;
                }
            }
        }
    }
}
