package com.icm.igosafeapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ruta_peatonal : AppCompatActivity() {
    lateinit var iniciar: Button
    lateinit var comentarios1:Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ruta_peatonal)

        iniciar = findViewById(R.id.iniciarViaje)
        comentarios1 = findViewById(R.id.comentarios)

        verComentarios()

        iniciar.setOnClickListener {
            // Mostrar el popup cuando se presiona el botón
            val intent = Intent(this, review_ruta::class.java)
            startActivity(intent)
        }
    }

    private fun verComentarios(){
        comentarios1.setOnClickListener {
            val intent = Intent(this, comentarios::class.java)
            startActivity(intent)
        }
    }
}