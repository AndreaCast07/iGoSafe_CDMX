package com.icm.igosafeapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class CreatePassword : AppCompatActivity(){
    private lateinit var btnRegistro: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_password)

        btnRegistro = findViewById(R.id.btnRegistrarse)

        mostrarLayoutCrearPerfil()

    }

    private fun mostrarLayoutCrearPerfil(){
        btnRegistro.setOnClickListener {
            val intent = Intent(this, Create_profile::class.java)
            startActivity(intent)
        }
    }
}