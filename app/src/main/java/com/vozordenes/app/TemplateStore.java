package com.vozordenes.app;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Guarda en ficheros las muestras de voz (características) de cada orden. */
public final class TemplateStore {
    private TemplateStore() {}

    public static File dir(Context c) {
        File d = new File(c.getApplicationContext().getFilesDir(), "muestras");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File[] files(Context c, final String id) {
        File[] fs = dir(c).listFiles();
        List<File> out = new ArrayList<>();
        if (fs != null) {
            for (File f : fs) if (f.getName().startsWith(id + "_")) out.add(f);
        }
        return out.toArray(new File[0]);
    }

    public static synchronized void save(Context c, String id, float[][] feats) throws IOException {
        File f = new File(dir(c), id + "_" + System.currentTimeMillis() + ".bin");
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f)));
        try {
            out.writeInt(feats.length);
            out.writeInt(feats[0].length);
            for (float[] row : feats) for (float v : row) out.writeFloat(v);
        } finally {
            out.close();
        }
    }

    public static synchronized List<float[][]> load(Context c, String id) {
        List<float[][]> res = new ArrayList<>();
        for (File f : files(c, id)) {
            try {
                DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)));
                try {
                    int n = in.readInt(), d = in.readInt();
                    float[][] m = new float[n][d];
                    for (int i = 0; i < n; i++) for (int j = 0; j < d; j++) m[i][j] = in.readFloat();
                    res.add(m);
                } finally {
                    in.close();
                }
            } catch (IOException ignored) {
            }
        }
        return res;
    }

    public static synchronized void clearAll(Context c) {
        File[] fs = dir(c).listFiles();
        if (fs != null) for (File f : fs) f.delete();
    }

    public static synchronized void writeRaw(Context c, String name, byte[] data) throws IOException {
        FileOutputStream out = new FileOutputStream(new File(dir(c), name));
        try {
            out.write(data);
        } finally {
            out.close();
        }
    }

    public static synchronized int count(Context c, String id) { return files(c, id).length; }

    public static synchronized void deleteAll(Context c, String id) {
        for (File f : files(c, id)) f.delete();
    }
}
