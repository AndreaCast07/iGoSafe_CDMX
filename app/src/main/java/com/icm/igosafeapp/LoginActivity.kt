package com.icm.igosafeapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {
    private lateinit var logo: ImageView
    private lateinit var bienvenida: TextView
    private lateinit var celularInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var txtOlvidarContraseña: TextView
    private lateinit var btnInciar: Button
    private lateinit var txt: TextView
    private lateinit var txtRegistrarse: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Inicializar las vistas
        logo = findViewById(R.id.logo)
        bienvenida = findViewById(R.id.titleName)
        celularInput = findViewById(R.id.inputCelular)
        passwordInput = findViewById(R.id.inputConstraseña)
        txtOlvidarContraseña = findViewById(R.id.OlvidarContraseña)
        btnInciar = findViewById(R.id.btnIniciarSesion)
        txt = findViewById(R.id.textView)
        txtRegistrarse = findViewById(R.id.registrarse)

        ocultarContrasena()
        mostrarLayoutRegisterPhone()
        mostrarLayoutForgetPassword()

        //cambiar a crear perfil provisional
        txt.setOnClickListener {
            val intent = Intent(this, Create_profile::class.java)
            startActivity(intent)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun ocultarContrasena() {
        passwordInput.setOnTouchListener { _, event ->
            val drawableEndIndex = 2
            if (event.rawX >= (passwordInput.right - passwordInput.compoundDrawables[drawableEndIndex].bounds.width())) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        passwordInput.inputType =
                            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    }

                    MotionEvent.ACTION_UP -> {
                        passwordInput.inputType =
                            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    }
                }
                true
            } else {
                false
            }
        }
    }

    private fun mostrarLayoutRegisterPhone() {
        txtRegistrarse.setOnClickListener {
            val intent = Intent(this, SignInPhone::class.java)
            startActivity(intent)
        }
    }

    private fun mostrarLayoutForgetPassword() {
        txtOlvidarContraseña.setOnClickListener {

        }
    }
}
