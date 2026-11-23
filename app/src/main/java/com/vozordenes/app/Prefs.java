package com.vozordenes.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Ajustes de la app. */
public final class Prefs {
    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    /** Tolerancia al comparar: 1.0 estricto ... 2.5 muy permisivo. */
    public static float slack(Context c) { return sp(c).getFloat("slack", 1.6f); }
    public static void setSlack(Context c, float v) { sp(c).edit().putFloat("slack", v).apply(); }

    /** Silenciar el sonido mientras se escucha (evita que el micro oiga la radio). */
    public static boolean duck(Context c) { return sp(c).getBoolean("duck", true); }
    public static void setDuck(Context c, boolean v) { sp(c).edit().putBoolean("duck", v).apply(); }

    /** Espera (ms) entre que acaba el pitido de inicio y se silencia el sonido. */
    public static int afterBeepMs(Context c) { return sp(c).getInt("after_beep", 300); }
    public static void setAfterBeepMs(Context c, int v) { sp(c).edit().putInt("after_beep", v).apply(); }
}
