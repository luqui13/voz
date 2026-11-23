package com.vozordenes.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView;

import org.json.JSONException;

import com.vozordenes.app.core.VoiceMatcher;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private LinearLayout root;
    private TextView status;
    private LinearLayout commandsBox;
    private TextView slackLabel;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(32));
        sv.addView(root);
        setContentView(sv);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String s, View.OnClickListener l) {
        Button bt = new Button(this);
        bt.setText(s);
        bt.setAllCaps(false);
        bt.setOnClickListener(l);
        return bt;
    }

    private void build() {
        root.addView(text("Órdenes de voz", 22, true));
        root.addView(text("Aprende TU voz: graba cada orden varias veces y la reconocerá aunque pronuncies raro.", 14, false));

        status = text("", 14, false);
        status.setPadding(0, dp(12), 0, dp(4));
        root.addView(status);

        root.addView(button("1) Dar permisos (micrófono, llamadas, icono)", new View.OnClickListener() {
            @Override public void onClick(View v) { askPermissions(); }
        }));

        root.addView(button("Permiso de Shizuku (para cerrar apps de verdad)", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (ShizukuHelper.granted()) {
                    Toast.makeText(MainActivity.this, "Shizuku ya está concedido", Toast.LENGTH_SHORT).show();
                } else if (!ShizukuHelper.requestPermission()) {
                    Toast.makeText(MainActivity.this, "Shizuku no está arrancado: ábrelo y arráncalo primero", Toast.LENGTH_LONG).show();
                }
                refresh();
            }
        }));

        root.addView(button("Probar el cierre de una app (diagnóstico)", new View.OnClickListener() {
            @Override public void onClick(View v) { testClose(); }
        }));

        root.addView(button("Probar: decir una orden y ver qué entiende", new View.OnClickListener() {
            @Override public void onClick(View v) { testListen(); }
        }));

        TextView hb = text("Copia de seguridad", 18, true);
        hb.setPadding(0, dp(16), 0, dp(4));
        root.addView(hb);
        root.addView(button("Guardar copia de mis órdenes y voces", new View.OnClickListener() {
            @Override public void onClick(View v) { exportBackup(); }
        }));
        root.addView(button("Importar copia", new View.OnClickListener() {
            @Override public void onClick(View v) { importBackup(); }
        }));

        CheckBox duck = new CheckBox(this);
        duck.setText("Silenciar todo el sonido mientras escucho");
        duck.setChecked(Prefs.duck(this));
        duck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton c, boolean on) { Prefs.setDuck(MainActivity.this, on); }
        });
        root.addView(duck);

        slackLabel = text("", 14, false);
        slackLabel.setPadding(0, dp(8), 0, 0);
        root.addView(slackLabel);
        SeekBar bar = new SeekBar(this);
        bar.setMax(20);
        bar.setProgress(Math.round((Prefs.slack(this) - 1.0f) * 10));
        updateSlackLabel(Prefs.slack(this));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                float v = 1.0f + p / 10f;
                Prefs.setSlack(MainActivity.this, v);
                updateSlackLabel(v);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        root.addView(bar);

        final TextView beepLabel = text("", 14, false);
        beepLabel.setPadding(0, dp(12), 0, 0);
        root.addView(beepLabel);
        beepLabel.setText("Espera tras el pitido de inicio: " + Prefs.afterBeepMs(this)
                + " ms  (súbela si el pitido se corta o no se oye)");
        SeekBar beepBar = new SeekBar(this);
        beepBar.setMax(18);
        beepBar.setProgress((Prefs.afterBeepMs(this) - 100) / 50);
        beepBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean user) {
                int ms = 100 + p * 50;
                Prefs.setAfterBeepMs(MainActivity.this, ms);
                beepLabel.setText("Espera tras el pitido de inicio: " + ms
                        + " ms  (súbela si el pitido se corta o no se oye)");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
        root.addView(beepBar);
        root.addView(button("Probar el pitido de inicio (solo el sonido)", new View.OnClickListener() {
            @Override public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override public void run() { StartBeep.play(); }
                }, "pitido").start();
            }
        }));

        TextView h = text("Mis órdenes", 18, true);
        h.setPadding(0, dp(20), 0, dp(4));
        root.addView(h);
        commandsBox = new LinearLayout(this);
        commandsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(commandsBox);
        root.addView(button("+ Añadir orden", new View.OnClickListener() {
            @Override public void onClick(View v) { addCommandDialog(); }
        }));
    }

    private void updateSlackLabel(float v) {
        slackLabel.setText(String.format(Locale.US,
                "Tolerancia: %.1f  (más alta = acepta pronunciaciones más distintas, pero se equivoca más)", v));
    }

    private boolean hasMic() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean has(String perm) {
        return checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    private void refresh() {
        boolean mic = hasMic();
        boolean calls = has(Manifest.permission.CALL_PHONE) && has(Manifest.permission.ANSWER_PHONE_CALLS);
        boolean ov = Settings.canDrawOverlays(this);
        String s = "Micrófono: " + (mic ? "OK" : "FALTA")
                + "   |   Llamadas: " + (calls ? "OK" : "FALTA")
                + "   |   Icono testigo: " + (ov ? "OK" : "FALTA")
                + "\nShizuku (cerrar apps): "
                + (ShizukuHelper.granted() ? "OK" : ShizukuHelper.running() ? "arrancado, falta permiso" : "no arrancado");
        if (!mic || !calls || !ov) {
            s += "\nSi la radio no muestra los avisos, desde aShell:\n"
                    + "pm grant " + getPackageName() + " android.permission.RECORD_AUDIO\n"
                    + "pm grant " + getPackageName() + " android.permission.CALL_PHONE\n"
                    + "pm grant " + getPackageName() + " android.permission.ANSWER_PHONE_CALLS\n"
                    + "pm grant " + getPackageName() + " android.permission.WRITE_EXTERNAL_STORAGE\n"
                    + "appops set " + getPackageName() + " SYSTEM_ALERT_WINDOW allow";
        }
        s += "\n\nPara usarlo: icono «Escuchar» del launcher o el botón de voz del volante.";
        status.setText(s);

        commandsBox.removeAllViews();
        List<Command> cmds = CommandStore.load(this);
        if (cmds.isEmpty()) commandsBox.addView(text("Aún no hay órdenes. Pulsa «Añadir orden».", 14, false));
        for (final Command c : cmds) commandsBox.addView(row(c));
    }

    private View row(final Command c) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(10), 0, dp(10));
        box.addView(text(c.name, 17, true));
        int n = TemplateStore.count(this, c.id);
        String info = Actions.label(c.type) + (c.param.isEmpty() ? "" : "  →  " + c.param);
        box.addView(text(info, 13, false));
        TextView cnt = text("Muestras de voz: " + n + (n < VoiceMatcher.MIN_SAMPLES
                ? "  (faltan; graba al menos " + VoiceMatcher.MIN_SAMPLES + ")" : ""), 13, false);
        if (n < VoiceMatcher.MIN_SAMPLES) cnt.setTextColor(0xFFC62828);
        box.addView(cnt);
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.addView(button("Grabar voz", new View.OnClickListener() {
            @Override public void onClick(View v) { recordDialog(c); }
        }));
        btns.addView(button("Borrar voz", new View.OnClickListener() {
            @Override public void onClick(View v) {
                TemplateStore.deleteAll(MainActivity.this, c.id);
                refresh();
            }
        }));
        btns.addView(button("Quitar", new View.OnClickListener() {
            @Override public void onClick(View v) { confirmRemove(c); }
        }));
        box.addView(btns);
        return box;
    }

    private void confirmRemove(final Command c) {
        new AlertDialog.Builder(this)
                .setMessage("¿Quitar la orden «" + c.name + "» y sus muestras de voz?")
                .setPositiveButton("Quitar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        CommandStore.remove(MainActivity.this, c.id);
                        refresh();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ---- permisos y servicio ----

    private void askPermissions() {
        List<String> need = new ArrayList<>();
        String[] all = {Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE,
                Manifest.permission.ANSWER_PHONE_CALLS, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        for (String p : all) if (!has(p)) need.add(p);
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), 1);
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "Esta radio no tiene esa pantalla: usa el comando appops de arriba", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "Permisos concedidos", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        refresh();
        if (hasMic() && !Settings.canDrawOverlays(this)) askPermissions();
    }

    // ---- prueba de cierre ----

    private void testClose() {
        if (!ShizukuHelper.granted()) {
            Toast.makeText(this, "Primero concede el permiso de Shizuku (botón de arriba)", Toast.LENGTH_LONG).show();
            return;
        }
        final PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        Collections.sort(apps, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
            }
        });
        String[] labels = new String[apps.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = apps.get(i).loadLabel(pm).toString();
        new AlertDialog.Builder(this).setTitle("¿Qué app cierro? (ábrela antes)")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        runCloseTest(apps.get(which).activityInfo.packageName);
                    }
                }).show();
    }

    private void runCloseTest(final String pkg) {
        Toast.makeText(this, "Cerrando " + pkg + "…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() {
                final ShizukuHelper.CloseReport rep = ShizukuHelper.closeAndCheck(pkg);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        new AlertDialog.Builder(MainActivity.this).setTitle("Resultado")
                                .setMessage(rep.text).setPositiveButton("Vale", null).show();
                    }
                });
            }
        }, "prueba-cierre").start();
    }

    // ---- copia de seguridad ----

    private boolean storageOk() {
        if (has(Manifest.permission.WRITE_EXTERNAL_STORAGE)) return true;
        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
        Toast.makeText(this, "Concede el permiso de almacenamiento y vuelve a pulsar", Toast.LENGTH_LONG).show();
        return false;
    }

    private void exportBackup() {
        if (!storageOk()) return;
        try {
            File f = Backup.export(this);
            int n = CommandStore.load(this).size();
            new AlertDialog.Builder(this).setTitle("Copia guardada")
                    .setMessage(n + " órdenes con sus voces guardadas en:\n\n" + f.getAbsolutePath()
                            + "\n\nEsa carpeta no se borra al desinstalar la app. Para importarla en otra radio, "
                            + "copia este fichero a la misma carpeta.")
                    .setPositiveButton("Vale", null).show();
        } catch (IOException | JSONException e) {
            Toast.makeText(this, "No se pudo guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importBackup() {
        if (!storageOk()) return;
        final List<File> files = Backup.list();
        if (files.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("No hay copias")
                    .setMessage("No hay ningún fichero .zip en:\n" + Backup.dir().getAbsolutePath()
                            + "\n\nCopia ahí tu fichero de copia (copia_AAAAMMDD_HHMM.zip) y vuelve a pulsar.")
                    .setPositiveButton("Vale", null).show();
            return;
        }
        String[] names = new String[files.size()];
        for (int i = 0; i < names.length; i++) names[i] = files.get(i).getName();
        new AlertDialog.Builder(this).setTitle("Elige la copia a importar")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) { confirmImport(files.get(which)); }
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void confirmImport(final File f) {
        new AlertDialog.Builder(this).setTitle("Importar " + f.getName())
                .setMessage("Esto SUSTITUYE todas las órdenes y voces que tienes ahora por las de la copia. ¿Seguro?")
                .setPositiveButton("Importar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        try {
                            int n = Backup.importFrom(MainActivity.this, f);
                            Toast.makeText(MainActivity.this, "Importadas " + n + " órdenes", Toast.LENGTH_LONG).show();
                        } catch (IOException | JSONException e) {
                            Toast.makeText(MainActivity.this, "No se pudo importar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                        refresh();
                    }
                })
                .setNegativeButton("Cancelar", null).show();
    }

    // ---- añadir orden ----

    private void addCommandDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(10), dp(20), 0);

        final EditText name = new EditText(this);
        name.setHint("Nombre de la orden (ej. Siguiente, Waze, Subir)");
        box.addView(name);

        final Spinner type = new Spinner(this);
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Actions.LABELS));
        box.addView(type);

        final EditText param = new EditText(this);
        box.addView(param);
        final Button pick = button("Elegir app…", new View.OnClickListener() {
            @Override public void onClick(View v) { pickApp(param, name); }
        });
        box.addView(pick);

        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                String t = Actions.TYPES[pos];
                boolean needs = Actions.needsParam(t);
                param.setVisibility(needs ? View.VISIBLE : View.GONE);
                pick.setVisibility(Actions.T_OPEN_APP.equals(t) || Actions.T_CLOSE_APP.equals(t) ? View.VISIBLE : View.GONE);
                if (Actions.T_OPEN_URI.equals(t)) param.setHint("waze://?favorite=Casa&navigate=yes");
                else if (Actions.T_CALL.equals(t)) param.setHint("Número de teléfono, ej. 600123456");
                else if (Actions.T_WAZE_ADDR.equals(t)) param.setHint("Dirección, ej. Calle Mayor 1, Madrid");
                else if (Actions.T_WAZE_FAV.equals(t)) param.setHint("Nombre del favorito en Waze, ej. Casa");
                else param.setHint("com.paquete.de.la.app");
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });

        new AlertDialog.Builder(this)
                .setTitle("Nueva orden")
                .setView(box)
                .setPositiveButton("Crear", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String n = name.getText().toString().trim();
                        String t = Actions.TYPES[type.getSelectedItemPosition()];
                        String p = param.getText().toString().trim();
                        if (n.isEmpty()) { Toast.makeText(MainActivity.this, "Pon un nombre", Toast.LENGTH_SHORT).show(); return; }
                        if (Actions.needsParam(t) && p.isEmpty()) { Toast.makeText(MainActivity.this, "Falta el dato de la acción", Toast.LENGTH_SHORT).show(); return; }
                        Command c = CommandStore.add(MainActivity.this, n, t, p);
                        refresh();
                        recordDialog(c);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void pickApp(final EditText target, final EditText nameField) {
        final PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        Collections.sort(apps, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
            }
        });
        String[] labels = new String[apps.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = apps.get(i).loadLabel(pm).toString();
        new AlertDialog.Builder(this)
                .setTitle("Elige la app")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        ResolveInfo ri = apps.get(which);
                        target.setText(ri.activityInfo.packageName);
                        if (nameField.getText().toString().trim().isEmpty()) {
                            nameField.setText(ri.loadLabel(pm).toString());
                        }
                    }
                })
                .show();
    }

    // ---- grabar muestras ----

    private void recordDialog(final Command c) {
        if (!hasMic()) { Toast.makeText(this, "Falta el permiso de micrófono", Toast.LENGTH_LONG).show(); return; }
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Grabar «" + c.name + "»")
                .setMessage(recordMsg(c, ""))
                .setPositiveButton("Grabar muestra", null)
                .setNeutralButton("Cerrar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { refresh(); }
                })
                .setCancelable(false)
                .create();
        dlg.show();
        final Button rec = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
        rec.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                rec.setEnabled(false);
                dlg.setMessage("Di la orden después del pitido…");
                new Thread(new Runnable() {
                    @Override public void run() {
                        final ListenPipeline.Captured cap = ListenPipeline.capture(getApplicationContext());
                        String result;
                        if (cap.error != null) {
                            result = "✗ " + cap.error;
                        } else {
                            try {
                                TemplateStore.save(getApplicationContext(), c.id, cap.feats);
                                result = String.format(Locale.US, "✓ Muestra guardada (%.1f s)", cap.seconds);
                            } catch (IOException e) {
                                result = "✗ No se pudo guardar: " + e.getMessage();
                            }
                        }
                        final String res = result;
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                dlg.setMessage(recordMsg(c, res));
                                rec.setEnabled(true);
                            }
                        });
                    }
                }).start();
            }
        });
    }

    private String recordMsg(Command c, String last) {
        int n = TemplateStore.count(this, c.id);
        return (last.isEmpty() ? "" : last + "\n\n")
                + "Muestras guardadas: " + n + "\n"
                + "Graba entre " + VoiceMatcher.MIN_SAMPLES + " y 6. Pulsa, espera el pitido y di la orden como la dirías "
                + "normalmente al conducir (con el motor y la música como siempre). Varía un poco cada vez.";
    }

    // ---- probar ----

    private void testListen() {
        if (!hasMic()) { Toast.makeText(this, "Falta el permiso de micrófono", Toast.LENGTH_LONG).show(); return; }
        Toast.makeText(this, "Di una orden después del pitido", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() {
                final ListenPipeline.Outcome o = ListenPipeline.listenAndMatch(getApplicationContext());
                runOnUiThread(new Runnable() {
                    @Override public void run() { showTest(o); }
                });
            }
        }).start();
    }

    private void showTest(ListenPipeline.Outcome o) {
        StringBuilder sb = new StringBuilder();
        if (o.error != null) {
            sb.append(o.error);
        } else {
            sb.append(o.command != null ? "Entendido: " + o.command.name : "No reconocida (ninguna encaja lo bastante)");
            sb.append("\n\nMotivo: ").append(o.result.reason);
            sb.append("\n\nDistancia / umbral por orden (menor distancia = más parecida; para aceptar, distancia ≤ umbral):\n");
            for (VoiceMatcher.Score s : o.result.ranking) {
                sb.append(String.format(Locale.US, "\n%s: %.2f / %.2f %s", s.entry.name, s.distance, s.threshold,
                        s.within() ? "✓" : ""));
            }
        }
        if (o.error == null) {
            sb.append(String.format(Locale.US,
                    "\n\nTiempos: análisis de voz %d ms, comparación %d ms (+ 0,45 s de silencio al final para saber que has terminado).",
                    o.mfccMs, o.matchMs));
        }
        new AlertDialog.Builder(this).setTitle("Resultado de la prueba").setMessage(sb.toString())
                .setPositiveButton("Vale", null).show();
    }
}
