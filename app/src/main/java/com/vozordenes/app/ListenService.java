package com.vozordenes.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.concurrent.atomic.AtomicBoolean;

/** Escucha una orden y se cierra, mostrando un micrófono testigo. Se lanza desde ListenActivity (icono, botón del volante). */
public class ListenService extends Service {
    public static final String ACTION_LISTEN = "com.vozordenes.app.LISTEN";
    private static final String CHANNEL = "voz";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private WindowManager wm;
    private MicBadgeView badge;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Órdenes de voz", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Escuchando…")
                .build();
        startForeground(1, n);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        showBadge();
        listen();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        hideBadge();
        super.onDestroy();
    }

    /** Muestra el micrófono testigo (sin tocar ni enfocar). Si no hay permiso, no se muestra. */
    private void showBadge() {
        if (badge != null || !Settings.canDrawOverlays(this)) return;
        try {
            wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            float density = getResources().getDisplayMetrics().density;
            int size = Math.max(110, Math.round(80 * density));
            badge = new MicBadgeView(this);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(size, size,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.x = Math.round(24 * density);
            lp.y = Math.round(24 * density);
            wm.addView(badge, lp);
        } catch (RuntimeException e) {
            badge = null;
        }
    }

    private void hideBadge() {
        if (badge != null && wm != null) {
            try { wm.removeView(badge); } catch (RuntimeException ignored) { }
        }
        badge = null;
    }

    private void setBadge(final int state) {
        main.post(new Runnable() {
            @Override public void run() { if (badge != null) badge.setState(state); }
        });
    }

    private void toast(final String msg) {
        main.post(new Runnable() {
            @Override public void run() {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void listen() {
        if (!busy.compareAndSet(false, true)) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                long hold = 1000;
                try {
                    ListenPipeline.Outcome o = ListenPipeline.listenAndMatch(getApplicationContext());
                    if (o.error != null) {
                        setBadge(MicBadgeView.FAIL);
                        toast(o.error);
                        ListenPipeline.beep(ToneGenerator.TONE_PROP_NACK, 200);
                    } else if (o.command == null) {
                        setBadge(MicBadgeView.FAIL);
                        toast("No te he entendido");
                        ListenPipeline.beep(ToneGenerator.TONE_PROP_NACK, 200);
                    } else {
                        setBadge(MicBadgeView.OK);
                        toast(o.command.name);
                        final Command cmd = o.command;
                        if (Actions.T_CLOSE_APP.equals(cmd.type)) hold = 8000; // dar tiempo a cerrar y comprobar
                        main.post(new Runnable() {
                            @Override public void run() { Actions.execute(getApplicationContext(), cmd); }
                        });
                        ListenPipeline.beep(ToneGenerator.TONE_PROP_ACK, 120);
                    }
                } catch (RuntimeException e) {
                    setBadge(MicBadgeView.FAIL);
                    toast("Error: " + e.getMessage());
                } finally {
                    busy.set(false);
                    // dejar el testigo visible un momento con el resultado y cerrar
                    main.postDelayed(new Runnable() {
                        @Override public void run() { hideBadge(); stopSelf(); }
                    }, hold);
                }
            }
        }, "escuchar").start();
    }
}
