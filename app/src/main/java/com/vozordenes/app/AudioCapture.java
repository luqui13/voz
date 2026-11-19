package com.vozordenes.app;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import com.vozordenes.app.core.Segmenter;

/** Graba del micrófono hasta que termina la frase (o se agota el tiempo). */
public final class AudioCapture {
    private AudioCapture() {}

    public static final class Capture {
        public final short[] pcm;
        public final int length;
        public final boolean speech;
        Capture(short[] pcm, int length, boolean speech) {
            this.pcm = pcm; this.length = length; this.speech = speech;
        }
    }

    private static final int[] RATES = {16000, 44100, 48000, 8000};
    private static final int[] SOURCES = {MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.VOICE_RECOGNITION};

    /** Bloquea hasta terminar. Devuelve null si no se puede abrir el micrófono. */
    public static Capture record(int maxMs) {
        for (int source : SOURCES) {
            for (int rate : RATES) {
                int min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (min <= 0) continue;
                AudioRecord rec = null;
                try {
                    rec = new AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT, Math.max(min, rate / 2));
                    if (rec.getState() != AudioRecord.STATE_INITIALIZED) { rec.release(); continue; }
                    return capture(rec, rate, maxMs);
                } catch (RuntimeException e) {
                    if (rec != null) { try { rec.release(); } catch (RuntimeException ignored) { } }
                }
            }
        }
        return null;
    }

    private static Capture capture(AudioRecord rec, int rate, int maxMs) {
        int chunkIn = rate / 100;               // 10 ms en la frecuencia original
        short[] in = new short[chunkIn];
        short[] all = new short[maxMs * 16];
        int n = 0;
        Segmenter seg = new Segmenter();
        try {
            rec.startRecording();
            if (rec.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) return null;
            while (n + Segmenter.CHUNK <= all.length) {
                int got = 0;
                while (got < chunkIn) {
                    int r = rec.read(in, got, chunkIn - got);
                    if (r <= 0) return n > 0 ? new Capture(all, n, seg.speechDetected()) : null;
                    got += r;
                }
                if (rate == 16000) {
                    System.arraycopy(in, 0, all, n, Segmenter.CHUNK);
                } else {
                    double ratio = (double) rate / 16000.0;
                    for (int i = 0; i < Segmenter.CHUNK; i++) {
                        double pos = i * ratio;
                        int a = (int) pos;
                        int b = Math.min(chunkIn - 1, a + 1);
                        double fr = pos - a;
                        all[n + i] = (short) (in[a] * (1 - fr) + in[b] * fr);
                    }
                }
                double db = Segmenter.chunkDb(all, n, Segmenter.CHUNK);
                n += Segmenter.CHUNK;
                if (seg.feed(db)) break;
            }
        } catch (RuntimeException e) {
            return n > 0 ? new Capture(all, n, seg.speechDetected()) : null;
        } finally {
            try { rec.stop(); } catch (RuntimeException ignored) { }
            rec.release();
        }
        return new Capture(all, n, seg.speechDetected());
    }
}
