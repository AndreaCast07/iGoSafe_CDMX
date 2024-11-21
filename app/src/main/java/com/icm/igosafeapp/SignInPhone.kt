package com.icm.igosafeapp

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Pair
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SignInPhone : AppCompatActivity() {
    private lateinit var btnSend: Button
    private lateinit var btnSend2: Button  //PRUEBA
    private lateinit var txtIniciarSesion: TextView
    private lateinit var editTxtCelular: TextView
    private lateinit var txt: TextView
    private lateinit var logo: ImageView

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in_phone)

        // Inicializar las vistas
        btnSend = findViewById(R.id.btnSendSMS)
        txtIniciarSesion = findViewById(R.id.iniciarSesion)
        editTxtCelular = findViewById(R.id.registrarCelular)
        txt = findViewById(R.id.textView2)
        logo = findViewById(R.id.logoGris)

        configurarListeners()
        //mostrarLayoutIniciarSesion()
        //mostrarLayoutValidarRegistro()
    }

    private fun configurarListeners() {
        mostrarLayoutValidarRegistro()
        mostrarLayoutIniciarSesion()
    }

    private fun mostrarLayoutIniciarSesion() {
        txtIniciarSesion.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }

    private fun mostrarLayoutValidarRegistro() {
        btnSend.setOnClickListener {
            val celular = editTxtCelular.text.toString().trim()

            // Validar que el celular no esté vacío
            if (celular.isEmpty()) {
                Toast.makeText(this, "Por favor, ingrese su número de celular", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validar que el celular contenga solo números del 0 al 9 y tenga al menos 8 dígitos
            val celularRegex = "^[0-9]{8,}$".toRegex()
            if (!celular.matches(celularRegex)) {
                Toast.makeText(this, "El número de celular debe tener al menos 8 dígitos y contener solo números", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Log.d("SignInPhone", "Button clicked with celular: $celular")

            // Si la validación es correcta, continuar con el envío
            val intent = Intent(this, SignInValidateSms::class.java).apply {
                putExtra("CELULAR", celular)
            }
            startActivity(intent)
        }
    }


}