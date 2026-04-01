package com.icm.igosafeapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.icm.igosafeapp.manejoArchivos.UsuarioManager
import entidades.DatosUsuario

class CreatePassword : AppCompatActivity(){
    private lateinit var btnRegistro: Button
    private lateinit var campoCelular: EditText
    private lateinit var contrasena: EditText
    private lateinit var confirmarContrasena:EditText

    private lateinit var usuarioManager: UsuarioManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_password)
        
        campoCelular = findViewById(R.id.celular)
        contrasena = findViewById(R.id.registrarContrasena)
        confirmarContrasena = findViewById(R.id.confirmarContrasena)
        btnRegistro = findViewById(R.id.btnRegistrarse)

        val celular = intent.getStringExtra("CELULAR") ?: ""
        campoCelular.setText(celular)
        campoCelular.isEnabled = false

        usuarioManager = UsuarioManager(this)

        val fotoUriString = intent.getStringExtra("FOTO_URI")
        val fotoUri = if (!fotoUriString.isNullOrEmpty()) Uri.parse(fotoUriString) else null

        btnRegistro.setOnClickListener {
            val contrasenaTexto = contrasena.text.toString().trim()
            
            if (contrasenaTexto.length < 6) {
                Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (contrasenaTexto != confirmarContrasena.text.toString().trim()) {
                Toast.makeText(this, "Las contraseñas no coinciden.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val datosUsuario = DatosUsuario(
                nombre = intent.getStringExtra("NOMBRE") ?: "Usuario",
                genero = intent.getStringExtra("GENERO") ?: "",
                edad = intent.getIntExtra("EDAD", 0),
                nacionalidad = intent.getStringExtra("NACIONALIDAD") ?: ""
            )

            Log.d("CreatePassword", "Iniciando registro para $celular")
            btnRegistro.isEnabled = false
            btnRegistro.text = "Registrando..."

            usuarioManager.registrarUsuario(
                celular = celular,
                contrasena = contrasenaTexto,
                fotoUri = fotoUri,
                datosUsuario = datosUsuario
            ) { exito, error ->
                runOnUiThread {
                    btnRegistro.isEnabled = true
                    btnRegistro.text = "Registrarse"
                    if (exito) {
                        Toast.makeText(this@CreatePassword, "¡Usuario registrado con éxito!", Toast.LENGTH_LONG).show()
                        val intent = Intent(this@CreatePassword, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@CreatePassword, "Error: $error", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
