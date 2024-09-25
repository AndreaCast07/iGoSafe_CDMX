package com.icm.igosafeapp

import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Pair
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SignInPhone : AppCompatActivity() {
    private lateinit var btnSend: Button
    private lateinit var txtIniciarSesion: TextView
    private lateinit var editTxtCelular: TextView
    private lateinit var txt: TextView
    private lateinit var logo: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in_phone)

        // Inicializar las vistas
        btnSend = findViewById(R.id.btnSendSMS)
        txtIniciarSesion = findViewById(R.id.iniciarSesion)
        editTxtCelular = findViewById(R.id.registrarCelular)
        txt = findViewById(R.id.textView2)
        logo = findViewById(R.id.logoGris)

        mostrarLayoutIniciarSesion()
        mostrarLayoutValidarRegistro()
    }

    private fun mostrarLayoutIniciarSesion() {
        txtIniciarSesion.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }

    private fun mostrarLayoutValidarRegistro() {
        btnSend.setOnClickListener {
            val intent = Intent(this, SignInValidateSms::class.java)
            startActivity(intent)
        }
    }
}