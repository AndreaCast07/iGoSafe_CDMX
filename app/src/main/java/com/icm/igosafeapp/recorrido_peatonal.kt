package com.icm.igosafeapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class recorrido_peatonal : AppCompatActivity() {
    lateinit var terminar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recorrido_peatonal)
        terminar = findViewById(R.id.finalizar)

        terminar.setOnClickListener {
            // Mostrar el popup cuando se presiona el botón
            val intent = Intent(this, review_ruta::class.java)
            startActivity(intent)
        }
    }
}