package com.vozordenes.app;

import android.content.Context;
import android.os.Environment;

import com.vozordenes.app.core.BackupCore;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Copia de seguridad en /sdcard/VozOrdenes/ (sobrevive a desinstalar la app). */
public final class Backup {
    private Backup() {}

    public static File dir() {
        return new File(Environment.getExternalStorageDirectory(), "VozOrdenes");
    }

    public static boolean storageReady() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    public static File export(Context c) throws IOException, JSONException {
        if (!storageReady()) throw new IOException("El almacenamiento no está disponible");
        JSONObject settings = new JSONObject();
        settings.put("slack", Prefs.slack(c));
        settings.put("duck", Prefs.duck(c));
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date());
        File out = new File(dir(), "copia_" + stamp + ".zip");
        BackupCore.write(out, CommandStore.loadRaw(c), settings.toString(), TemplateStore.dir(c));
        return out;
    }

    public static List<File> list() {
        File[] fs = dir().listFiles();
        List<File> out = new ArrayList<>();
        if (fs != null) {
            for (File f : fs) if (f.isFile() && f.getName().toLowerCase(Locale.US).endsWith(".zip")) out.add(f);
        }
        Collections.sort(out, new Comparator<File>() {
            @Override public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); }
        });
        return out;
    }

    /** Sustituye las órdenes y muestras actuales. Devuelve cuántas órdenes se importaron. */
    public static int importFrom(Context c, File f) throws IOException, JSONException {
        BackupCore.Data d = BackupCore.read(f);       // valida el zip antes de tocar nada
        // validar el JSON de órdenes antes de borrar lo actual
        new org.json.JSONArray(d.commandsJson);
        TemplateStore.clearAll(c);
        for (Map.Entry<String, byte[]> e : d.samples.entrySet()) {
            TemplateStore.writeRaw(c, e.getKey(), e.getValue());
        }
        int n = CommandStore.saveRaw(c, d.commandsJson);
        try {
            JSONObject s = new JSONObject(d.settingsJson);
            if (s.has("slack")) Prefs.setSlack(c, (float) s.getDouble("slack"));
            if (s.has("duck")) Prefs.setDuck(c, s.getBoolean("duck"));
        } catch (JSONException ignored) { }
        return n;
    }
}
