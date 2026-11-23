package com.vozordenes.app.core;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Lee y escribe el fichero de copia de seguridad (zip). Java puro, sin Android. */
public final class BackupCore {
    private BackupCore() {}

    public static final class Data {
        public String commandsJson;
        public String settingsJson = "{}";
        public final Map<String, byte[]> samples = new LinkedHashMap<String, byte[]>();
    }

    private static final Pattern SAMPLE = Pattern.compile("[A-Za-z0-9_]{1,80}\\.bin");
    private static final int MAX_ENTRY = 4 * 1024 * 1024;
    private static final int MAX_FILES = 2000;

    public static boolean validSampleName(String n) { return SAMPLE.matcher(n).matches(); }

    public static void write(File out, String commandsJson, String settingsJson, File samplesDir) throws IOException {
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        ZipOutputStream z = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)));
        try {
            put(z, "commands.json", commandsJson.getBytes("UTF-8"));
            put(z, "settings.json", settingsJson.getBytes("UTF-8"));
            File[] fs = samplesDir.listFiles();
            if (fs != null) {
                for (File f : fs) {
                    if (f.isFile() && validSampleName(f.getName())) {
                        put(z, "muestras/" + f.getName(), readLimited(new FileInputStream(f)));
                    }
                }
            }
        } finally {
            z.close();
        }
    }

    public static Data read(File in) throws IOException {
        Data d = new Data();
        ZipInputStream z = new ZipInputStream(new FileInputStream(in));
        try {
            ZipEntry e;
            int count = 0;
            while ((e = z.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                if (++count > MAX_FILES) throw new IOException("Demasiados ficheros: no parece una copia válida");
                String name = e.getName();
                if (name.equals("commands.json")) {
                    d.commandsJson = new String(readLimited(z), "UTF-8");
                } else if (name.equals("settings.json")) {
                    d.settingsJson = new String(readLimited(z), "UTF-8");
                } else if (name.startsWith("muestras/")) {
                    String n = name.substring("muestras/".length());
                    if (validSampleName(n)) d.samples.put(n, readLimited(z));
                }
            }
        } finally {
            z.close();
        }
        if (d.commandsJson == null) throw new IOException("No es una copia de Órdenes de voz");
        return d;
    }

    private static void put(ZipOutputStream z, String name, byte[] data) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(data);
        z.closeEntry();
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        try {
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > MAX_ENTRY) throw new IOException("Fichero demasiado grande en la copia");
            }
        } finally {
            if (!(in instanceof ZipInputStream)) in.close();
        }
        return bo.toByteArray();
    }
}
