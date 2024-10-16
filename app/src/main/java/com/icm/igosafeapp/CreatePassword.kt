package com.icm.igosafeapp

import Usuario
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import java.io.File

class CreatePassword : AppCompatActivity(){
    private lateinit var btnRegistro: Button
    private lateinit var campoCelular: EditText
    private lateinit var contrasena: EditText
    private lateinit var confirmarContrasena:EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_password)
        campoCelular = findViewById(R.id.celular)
        contrasena = findViewById(R.id.registrarContrasena)
        confirmarContrasena = findViewById(R.id.confirmarContrasena)
        btnRegistro = findViewById(R.id.btnRegistrarse)

        //Asignar celular
        val celular = intent.getStringExtra("CELULAR") ?: ""
        campoCelular.setText(celular)
        campoCelular.isEnabled = false

        mostrarLayoutMenu()
    }

    private fun mostrarLayoutMenu(){
        btnRegistro.setOnClickListener {
            if (validarContrasenas()) {
                val nombre = intent.getStringExtra("NOMBRE") ?: ""
                val tipoDocumento = intent.getStringExtra("TIPO_DOCUMENTO") ?: ""
                val documento = intent.getStringExtra("NUM_DOCUMENTO") ?: ""

                val usuario = Usuario(
                    celular = campoCelular.text.toString(),
                    contraseña = contrasena.text.toString(),
                    nombre = nombre,
                    tipoDocumento = tipoDocumento,
                    documento = documento
                )
                guardarUsuario(usuario)

                val intent = Intent(this, Menu::class.java)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Las contraseñas no coinciden.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validarContrasenas(): Boolean {
        return contrasena.text.toString() == confirmarContrasena.text.toString()
    }

    private fun guardarUsuario(usuario: Usuario) {
        val usuariosList = mutableListOf<Usuario>()
        val file = File(filesDir, "usuarios.json")
        if (file.exists() && file.length() > 0) {
            val jsonContent = file.readText()
            val usuarios = Gson().fromJson(jsonContent, Array<Usuario>::class.java).toList()
            usuariosList.addAll(usuarios)
        }
        usuariosList.add(usuario)
        file.writeText(Gson().toJson(usuariosList))
    }

}