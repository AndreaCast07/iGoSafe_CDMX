package com.icm.igosafeapp

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class TerminosActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.terminos_activity)

        val btnEntendido: Button = findViewById(R.id.btnEntendido)

        // Simplemente cerramos la actividad para volver al formulario de registro
        btnEntendido.setOnClickListener {
            finish()
        }
    }
}