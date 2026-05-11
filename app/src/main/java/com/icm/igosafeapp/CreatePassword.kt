package com.icm.igosafeapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.icm.igosafeapp.manejoArchivos.UsuarioManager
import entidades.DatosUsuario

class CreatePassword : AppCompatActivity() {
    private lateinit var btnRegistro: Button
    private lateinit var campoCelular: EditText
    private lateinit var contrasena: EditText
    private lateinit var confirmarContrasena: EditText
    private lateinit var usuarioManager: UsuarioManager
    private val auth = FirebaseAuth.getInstance() // Necesario para crear la cuenta

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_password)

        usuarioManager = UsuarioManager()

        campoCelular = findViewById(R.id.celular)
        contrasena = findViewById(R.id.registrarContrasena)
        confirmarContrasena = findViewById(R.id.confirmarContrasena)
        btnRegistro = findViewById(R.id.btnRegistrarse)

        val celular = intent.getStringExtra("CELULAR") ?: ""
        val emailRecuperado = intent.getStringExtra("EMAIL") ?: ""
        val esGoogle = intent.getBooleanExtra("IS_GOOGLE", false)

        campoCelular.setText(celular)
        campoCelular.isEnabled = false

        btnRegistro.setOnClickListener {
            val pass = contrasena.text.toString().trim()
            val confirmPass = confirmarContrasena.text.toString().trim()

            // 1. Validación de contraseñas
            val passwordPattern = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{6,}$".toRegex()

            if (!pass.matches(passwordPattern)) {
                Toast.makeText(this, "La contraseña no cumple con los requisitos de seguridad.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (pass != confirmPass) {
                Toast.makeText(this, "Las contraseñas no coinciden.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnRegistro.isEnabled = false
            btnRegistro.text = "Procesando..."

            // 2. Lógica de Registro (Diferenciando Manual vs Google)
            if (esGoogle) {
                // Si es de Google, el usuario ya existe en Auth, solo guardamos datos
                guardarEnBaseDeDatos(celular, emailRecuperado)
            } else {
                // Si es manual, PRIMERO creamos la cuenta en Firebase Auth
                auth.createUserWithEmailAndPassword(emailRecuperado, pass)
                    .addOnSuccessListener {
                        // Una vez creada la cuenta en Auth, ya tenemos un UID para guardar en la DB
                        guardarEnBaseDeDatos(celular, emailRecuperado)
                    }
                    .addOnFailureListener { e ->
                        btnRegistro.isEnabled = true
                        btnRegistro.text = "Registrarse"
                        Toast.makeText(this, "Error al crear cuenta: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    private fun guardarEnBaseDeDatos(celular: String, email: String) {
        val datosUsuario = DatosUsuario(
            nombre = intent.getStringExtra("NOMBRE") ?: "Usuario",
            genero = intent.getStringExtra("GENERO") ?: "",
            edad = intent.getIntExtra("EDAD", 0),
            nacionalidad = intent.getStringExtra("NACIONALIDAD") ?: "",
            email = email,
            fotoPerfilUrl = intent.getStringExtra("FOTO_URL") ?: ""
        )

        usuarioManager.registrarUsuarioCompleto(datosUsuario, celular) { exito, mensaje ->
            runOnUiThread {
                if (exito) {
                    Toast.makeText(this, "¡Bienvenido a iGoSafe!", Toast.LENGTH_LONG).show()
                    val loginIntent = Intent(this, LoginActivity::class.java)
                    loginIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(loginIntent)
                    finish()
                } else {
                    btnRegistro.isEnabled = true
                    btnRegistro.text = "Registrarse"
                    Toast.makeText(this, "Error en DB: $mensaje", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}