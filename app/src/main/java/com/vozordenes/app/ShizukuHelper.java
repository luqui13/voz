package com.vozordenes.app;

import android.content.pm.PackageManager;

import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

/** Ejecuta comandos como el usuario "shell" a través de Shizuku (que debe estar arrancado). */
public final class ShizukuHelper {
    private ShizukuHelper() {}

    public static final int REQUEST_CODE = 77;

    /** Shizuku está arrancado en la radio. */
    public static boolean running() {
        try { return Shizuku.pingBinder(); } catch (Throwable t) { return false; }
    }

    /** Shizuku está arrancado y ha dado permiso a esta app. */
    public static boolean granted() {
        try {
            return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Pide a Shizuku el permiso para esta app. Devuelve false si Shizuku no está arrancado. */
    public static boolean requestPermission() {
        try {
            if (!Shizuku.pingBinder()) return false;
            Shizuku.requestPermission(REQUEST_CODE);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static final class Result {
        public boolean ran;      // el comando llegó a ejecutarse
        public int code = -1;    // código de salida
        public String text = ""; // salida y errores
    }

    private static String readAll(java.io.InputStream in) {
        try {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0 && bo.size() < 4096) bo.write(buf, 0, n);
            return bo.toString("UTF-8").trim();
        } catch (Exception e) {
            return "";
        }
    }

    /** Bloqueante (máx. 8 s): llamar fuera del hilo principal. */
    public static Result run(String... cmd) {
        Result r = new Result();
        try {
            // en la API 13 de Shizuku este método es privado: se llama por reflexión
            Method m = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            m.setAccessible(true);
            Process p = (Process) m.invoke(null, cmd, null, null);
            // Process.waitFor(timeout) no sirve con el proceso remoto de Shizuku (exitValue lanza
            // IllegalArgumentException mientras corre): se espera con un bucle propio
            long end = System.currentTimeMillis() + 8000;
            boolean done = false;
            int code = -1;
            while (System.currentTimeMillis() < end) {
                try {
                    code = p.exitValue();
                    done = true;
                    break;
                } catch (RuntimeException notYet) {
                    Thread.sleep(60);
                }
            }
            if (!done) {
                try { p.destroy(); } catch (RuntimeException ignored) { }
                r.text = "Tiempo agotado (8 s)";
                return r;
            }
            r.ran = true;
            r.code = code;
            String out = readAll(p.getInputStream());
            String err = readAll(p.getErrorStream());
            r.text = (out + " " + err).trim();
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            r.text = c.getClass().getSimpleName() + ": " + c.getMessage();
        }
        return r;
    }

    public static final class CloseReport {
        public boolean closed;
        public String text;
    }

    /** Fuerza el cierre de una app y comprueba si de verdad ha dejado de ejecutarse. Bloqueante. */
    public static CloseReport closeAndCheck(String pkg) {
        CloseReport rep = new CloseReport();
        StringBuilder sb = new StringBuilder();
        if (!pkg.matches("[A-Za-z0-9._]+")) {
            rep.text = "Nombre de paquete no válido: " + pkg;
            return rep;
        }
        Result who = run("sh", "-c", "id");
        sb.append("Shizuku ejecuta como: ").append(who.ran ? who.text : "NO SE PUDO EJECUTAR (" + who.text + ")").append("\n");
        Result st = run("sh", "-c", "am force-stop " + pkg);
        sb.append("force-stop ").append(pkg).append(": ")
                .append(st.ran ? "código " + st.code : "NO SE PUDO EJECUTAR")
                .append(st.text.isEmpty() ? "" : " (" + st.text + ")").append("\n");
        try { Thread.sleep(1500); } catch (InterruptedException ignored) { }
        Result pid = run("sh", "-c", "pidof " + pkg);
        if (!pid.ran) {
            sb.append("No se pudo comprobar si sigue abierta (").append(pid.text).append(")");
        } else if (pid.code == 0 && !pid.text.isEmpty()) {
            sb.append("La app SIGUE EN MARCHA (PID ").append(pid.text).append("): no se cerró o se ha vuelto a abrir sola.");
        } else if (pid.code == 127) {
            sb.append("No se pudo comprobar si sigue abierta (falta pidof).");
            rep.closed = st.ran && st.code == 0;
        } else {
            sb.append("La app ya no está en marcha: cerrada.");
            rep.closed = true;
        }
        rep.text = sb.toString();
        return rep;
    }
}
