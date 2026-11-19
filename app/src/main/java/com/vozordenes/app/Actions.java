package com.vozordenes.app;

import android.content.ActivityNotFoundException;
import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.telecom.TelecomManager;
import android.view.KeyEvent;
import android.widget.Toast;

/** Acciones que puede ejecutar una orden de voz. */
public final class Actions {
    private Actions() {}

    public static final String T_OPEN_APP = "open_app";
    public static final String T_OPEN_URI = "open_uri";
    public static final String T_VOL_UP = "vol_up";
    public static final String T_VOL_DOWN = "vol_down";
    public static final String T_MUTE = "mute";
    public static final String T_NEXT = "next";
    public static final String T_PREV = "prev";
    public static final String T_PLAYPAUSE = "playpause";
    public static final String T_HOME = "home";
    public static final String T_CALL = "call";
    public static final String T_ANSWER = "answer";
    public static final String T_HANGUP = "hangup";
    public static final String T_CLOSE_APP = "close_app";
    public static final String T_MUTE_ON = "mute_on";
    public static final String T_MUTE_OFF = "mute_off";
    public static final String T_WAZE_ADDR = "waze_addr";
    public static final String T_WAZE_FAV = "waze_fav";

    public static final String[] TYPES = {
            T_OPEN_APP, T_CLOSE_APP, T_OPEN_URI, T_WAZE_ADDR, T_WAZE_FAV,
            T_VOL_UP, T_VOL_DOWN, T_MUTE_ON, T_MUTE_OFF, T_MUTE,
            T_NEXT, T_PREV, T_PLAYPAUSE, T_HOME, T_CALL, T_ANSWER, T_HANGUP
    };
    public static final String[] LABELS = {
            "Abrir una app", "Cerrar una app", "Abrir enlace (waze://, geo:, tel:...)",
            "Waze: ir a una dirección", "Waze: ir a un favorito",
            "Subir volumen", "Bajar volumen", "Silenciar", "Quitar silencio",
            "Silenciar / quitar silencio (interruptor)",
            "Siguiente (tecla multimedia)", "Anterior (tecla multimedia)",
            "Play / pausa (tecla multimedia)", "Ir al inicio",
            "Llamar a un número", "Descolgar (contestar llamada)", "Colgar"
    };

    public static String label(String type) {
        for (int i = 0; i < TYPES.length; i++) if (TYPES[i].equals(type)) return LABELS[i];
        return type;
    }

    public static boolean needsParam(String type) {
        return T_OPEN_APP.equals(type) || T_CLOSE_APP.equals(type) || T_OPEN_URI.equals(type)
                || T_CALL.equals(type) || T_WAZE_ADDR.equals(type) || T_WAZE_FAV.equals(type);
    }

    public static void execute(Context ctx, Command c) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        try {
            switch (c.type) {
                case T_OPEN_APP: {
                    Intent i = ctx.getPackageManager().getLaunchIntentForPackage(c.param);
                    if (i == null) { toast(ctx, "No encuentro la app " + c.param); return; }
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                    break;
                }
                case T_OPEN_URI: {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(c.param));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                    break;
                }
                case T_VOL_UP:
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0);
                    break;
                case T_VOL_DOWN:
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0);
                    break;
                case T_MUTE:
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
                    break;
                case T_NEXT: mediaKey(am, KeyEvent.KEYCODE_MEDIA_NEXT); break;
                case T_PREV: mediaKey(am, KeyEvent.KEYCODE_MEDIA_PREVIOUS); break;
                case T_PLAYPAUSE: mediaKey(am, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); break;
                case T_HOME: {
                    Intent i = new Intent(Intent.ACTION_MAIN);
                    i.addCategory(Intent.CATEGORY_HOME);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                    break;
                }
                case T_CALL: {
                    if (ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                        toast(ctx, "Falta el permiso de llamadas (ábrelo en Órdenes de voz → permisos)");
                        return;
                    }
                    Intent i = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(c.param)));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                    break;
                }
                case T_CLOSE_APP: closeApp(ctx, c.param); break;
                case T_MUTE_ON:
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI);
                    break;
                case T_MUTE_OFF:
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI);
                    break;
                case T_WAZE_ADDR: {
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("waze://?q=" + Uri.encode(c.param) + "&navigate=yes"));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                    break;
                }
                case T_WAZE_FAV: {
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("waze://?favorite=" + Uri.encode(c.param) + "&navigate=yes"));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                    break;
                }
                case T_ANSWER: answer(ctx, am); break;
                case T_HANGUP: hangup(ctx, am); break;
                default:
                    toast(ctx, "Acción desconocida: " + c.type);
            }
        } catch (ActivityNotFoundException e) {
            toast(ctx, "No hay ninguna app para abrir eso");
        } catch (RuntimeException e) {
            toast(ctx, "Error: " + e.getMessage());
        }
    }

    /**
     * Va al inicio y cierra la app. Con Shizuku lo hace con "am force-stop" (cierre forzado real);
     * sin Shizuku pide a Android que mate la app en segundo plano (no siempre basta).
     */
    private static void closeApp(final Context ctx, final String pkg) {
        if (pkg.equals(ctx.getPackageName())) return;
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(home);

        if (ShizukuHelper.granted()) {
            new Thread(new Runnable() {
                @Override public void run() {
                    try { Thread.sleep(400); } catch (InterruptedException ignored) { }
                    final ShizukuHelper.CloseReport rep = ShizukuHelper.closeAndCheck(pkg);
                    String[] lines = rep.text.split("\n");
                    toastMain(ctx, rep.closed ? "Cerrada: " + pkg : lines[lines.length - 1]);
                    if (!rep.closed) killBackground(ctx, pkg);
                }
            }, "cerrar").start();
            return;
        }
        if (ShizukuHelper.running()) {
            ShizukuHelper.requestPermission();
            toast(ctx, "Shizuku: concede el permiso a Órdenes de voz para poder cerrar apps de verdad");
        }
        killBackground(ctx, pkg);
    }

    private static void toastMain(final Context ctx, final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show(); }
        });
    }

    private static void killBackground(final Context ctx, final String pkg) {
        final ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        Handler h = new Handler(Looper.getMainLooper());
        long[] delays = {700, 2200};
        for (long d : delays) {
            h.postDelayed(new Runnable() {
                @Override public void run() {
                    try { am.killBackgroundProcesses(pkg); } catch (RuntimeException ignored) { }
                }
            }, d);
        }
    }

    private static boolean canAnswer(Context ctx) {
        return ctx.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED;
    }

    /** Contesta: por Telecom y, además, con la tecla de auricular (lo que usan muchas radios). */
    private static void answer(Context ctx, AudioManager am) {
        if (canAnswer(ctx)) {
            try {
                ((TelecomManager) ctx.getSystemService(Context.TELECOM_SERVICE)).acceptRingingCall();
            } catch (RuntimeException ignored) { }
        }
        mediaKey(am, KeyEvent.KEYCODE_HEADSETHOOK);
    }

    /** Cuelga: por Telecom (método oculto en Android 8) y con la tecla de auricular. */
    private static void hangup(Context ctx, AudioManager am) {
        boolean done = false;
        if (canAnswer(ctx)) {
            try {
                TelecomManager tm = (TelecomManager) ctx.getSystemService(Context.TELECOM_SERVICE);
                Object r = TelecomManager.class.getMethod("endCall").invoke(tm);
                done = Boolean.TRUE.equals(r);
            } catch (Exception ignored) { }
        }
        if (!done) mediaKey(am, KeyEvent.KEYCODE_HEADSETHOOK);
    }

    private static void mediaKey(AudioManager am, int code) {
        long t = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
    }

    private static void toast(Context ctx, String s) {
        Toast.makeText(ctx, s, Toast.LENGTH_LONG).show();
    }
}
