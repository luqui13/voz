package com.vozordenes.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;

import com.vozordenes.app.core.Mfcc;
import com.vozordenes.app.core.Segmenter;
import com.vozordenes.app.core.VoiceMatcher;

import java.util.ArrayList;
import java.util.List;

/** Escuchar una vez: pitido, bajar música, grabar, extraer características y comparar. */
public final class ListenPipeline {
    private ListenPipeline() {}

    public static final class Captured {
        public float[][] feats;
        public String error;
        public double seconds;
        public long mfccMs;
    }

    public static final class Outcome {
        public String error;
        public VoiceMatcher.Result result;
        public Command command;
        public long mfccMs;
        public long matchMs;
    }

    /** Bloqueante: llamar desde un hilo secundario. */
    public static Captured capture(Context ctx) {
        Captured out = new Captured();
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            out.error = "Falta el permiso de micrófono";
            return out;
        }
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        int oldVol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        boolean ducked = false;
        AudioCapture.Capture cap;
        try {
            StartBeep.play();
            sleep(Prefs.afterBeepMs(ctx));
            // silencio total mientras escucha (si ya estaba silenciado, no se toca)
            if (Prefs.duck(ctx) && oldVol > 0 && !am.isStreamMute(AudioManager.STREAM_MUSIC)) {
                try {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
                    ducked = true;
                } catch (RuntimeException ignored) { }
                sleep(120);
            }
            cap = AudioCapture.record(3500);
        } finally {
            if (ducked) {
                try { am.setStreamVolume(AudioManager.STREAM_MUSIC, oldVol, 0); } catch (RuntimeException ignored) { }
            }
        }
        if (cap == null) { out.error = "No se pudo abrir el micrófono"; return out; }
        if (!cap.speech) { out.error = "No he oído nada (¿micrófono muy bajo?)"; return out; }
        int[] seg = Segmenter.trim(cap.pcm, cap.length);
        if (seg == null) { out.error = "No he oído nada claro"; return out; }
        if (seg[1] - seg[0] < 16000 / 5) { out.error = "Demasiado corto"; return out; }
        long t0 = System.currentTimeMillis();
        out.feats = Mfcc.compute(cap.pcm, seg[0], seg[1]);
        out.mfccMs = System.currentTimeMillis() - t0;
        out.seconds = (seg[1] - seg[0]) / 16000.0;
        if (out.feats == null) out.error = "Demasiado corto";
        return out;
    }

    /** Bloqueante: escucha y busca la orden. */
    public static Outcome listenAndMatch(Context ctx) {
        Outcome o = new Outcome();
        Captured c = capture(ctx);
        if (c.error != null) { o.error = c.error; return o; }
        List<Command> cmds = CommandStore.load(ctx);
        List<VoiceMatcher.Entry> entries = new ArrayList<>();
        for (Command cmd : cmds) {
            entries.add(new VoiceMatcher.Entry(cmd.id, cmd.name, TemplateStore.load(ctx, cmd.id)));
        }
        if (entries.isEmpty()) { o.error = "No hay órdenes creadas"; return o; }
        o.mfccMs = c.mfccMs;
        long t1 = System.currentTimeMillis();
        o.result = VoiceMatcher.match(c.feats, entries, Prefs.slack(ctx), new SpreadStore(ctx));
        o.matchMs = System.currentTimeMillis() - t1;
        if (o.result.ranking.isEmpty()) { o.error = "Graba al menos " + VoiceMatcher.MIN_SAMPLES + " muestras de una orden"; return o; }
        if (o.result.accepted != null) {
            for (Command cmd : cmds) if (cmd.id.equals(o.result.accepted.id)) o.command = cmd;
        }
        return o;
    }

    public static void beep(int tone, int ms) {
        try {
            ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 70);
            tg.startTone(tone, ms);
            sleep(ms + 60);
            tg.release();
        } catch (RuntimeException ignored) { }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
