package com.icm.igosafeapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.Manifest

class modalidad : AppCompatActivity() {
    private val CHANNEL_ID = "ubicacion_channel"
    private val REQUEST_LOCATION_PERMISSION = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_modalidad)

        // Referencia al botón solicitaUbi
        val solicitaUbiButton: Button = findViewById(R.id.solicitaUbi)

        // Acción del botón para mostrar la notificación
        solicitaUbiButton.setOnClickListener {
            solicitarPermisoGPS()
        }
    }

    private fun solicitarPermisoGPS() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Si no tiene permiso, solicitarlo
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION)
        } else {
            // Si ya tiene permiso, mostrar la notificación
            mostrarNotificacion()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // Permiso concedido, mostrar notificación
                mostrarNotificacion()
            } else {
                // Permiso denegado, mostrar mensaje o deshabilitar funcionalidad
                // Puedes mostrar un mensaje con Toast si es necesario
            }
        }
    }
    private fun mostrarNotificacion() {
        // Crear intents y pending intents para aceptar y rechazar
        val aceptarIntent = Intent(this, ruta_peatonal::class.java)
        val aceptarPendingIntent = crearPendingIntent(aceptarIntent)

        val rechazarIntent = Intent(this, ruta_vehicular::class.java)
        val rechazarPendingIntent = crearPendingIntent(rechazarIntent)

        // Crear la notificación
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_location_on_24) // Asegúrate de tener un ícono en drawable
            .setContentTitle("Solicitud de Ubicación")
            .setContentText("¿Aceptas compartir tu ubicación?")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, "Aceptar", aceptarPendingIntent)  // Botón de Aceptar sin ícono
            .addAction(0, "Rechazar", rechazarPendingIntent)  // Botón de Rechazar sin ícono
            .setAutoCancel(true)

        // Crear el canal de notificación si es necesario (Android 8.0 o superior)
        crearCanalNotificacion()

        // Mostrar la notificación
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(0, builder.build())
    }

    private fun crearPendingIntent(intent: Intent): PendingIntent {
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }



    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, "Canal de Ubicación", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
    }


}