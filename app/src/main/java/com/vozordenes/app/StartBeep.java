package com.vozordenes.app;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * Pitido de inicio propio: un poco de silencio previo (para que el audio de la radio "despierte")
 * y un tono completo de 220 ms. Espera a que acabe de sonar antes de volver.
 */
public final class StartBeep {
    private StartBeep() {}

    private static final int RATE = 44100;
    private static final int PRE_MS = 60;
    private static final int TONE_MS = 220;

    private static short[] samples() {
        int pre = RATE * PRE_MS / 1000;
        int tone = RATE * TONE_MS / 1000;
        short[] b = new short[pre + tone];
        double fade = RATE * 0.012;
        for (int i = 0; i < tone; i++) {
            double env = Math.min(1.0, Math.min(i, tone - 1 - i) / fade);
            b[pre + i] = (short) (Math.sin(2 * Math.PI * 1000.0 * i / RATE) * 0.75 * env * 32767);
        }
        return b;
    }

    /** Bloqueante: vuelve cuando el pitido ha terminado de sonar. */
    public static void play() {
        AudioTrack t = null;
        try {
            short[] b = samples();
            t = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(b.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            t.write(b, 0, b.length);
            t.play();
            Thread.sleep(PRE_MS + TONE_MS + 100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException ignored) {
        } finally {
            if (t != null) {
                try { t.stop(); } catch (RuntimeException ignored) { }
                t.release();
            }
        }
    }
}
