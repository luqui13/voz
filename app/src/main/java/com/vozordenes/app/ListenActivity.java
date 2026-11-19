package com.vozordenes.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Icono "Escuchar" del launcher y destino del botón de voz del volante: sin pantalla,
 * empieza a escuchar y se cierra.
 */
public class ListenActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Falta el permiso de micrófono: ábrelo desde Órdenes de voz", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, MainActivity.class));
        } else {
            startForegroundService(new Intent(this, ListenService.class).setAction(ListenService.ACTION_LISTEN));
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
