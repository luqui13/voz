package com.vozordenes.app;

import android.content.Context;
import android.content.SharedPreferences;

import com.vozordenes.app.core.VoiceMatcher;

/** Guarda en disco el umbral calculado de cada orden (sobrevive a que el sistema cierre la app). */
public final class SpreadStore implements VoiceMatcher.SpreadCache {
    private final SharedPreferences sp;

    public SpreadStore(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("umbrales", Context.MODE_PRIVATE);
    }

    @Override
    public Double get(String key) {
        return sp.contains(key) ? Double.valueOf(Double.longBitsToDouble(sp.getLong(key, 0))) : null;
    }

    @Override
    public void put(String key, double value) {
        sp.edit().putLong(key, Double.doubleToLongBits(value)).apply();
    }
}
