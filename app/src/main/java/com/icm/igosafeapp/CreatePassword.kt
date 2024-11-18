package com.icm.igosafeapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.icm.igosafeapp.manejoArchivos.UsuarioManager
import entidades.DatosUsuario
import entidades.Usuario
import java.io.File

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

        //Asignar celular
        val celular = intent.getStringExtra("CELULAR") ?: ""
        campoCelular.setText(celular)
        campoCelular.isEnabled = false

        usuarioManager = UsuarioManager(this)

        mostrarLayoutMenu()
    }

    private fun mostrarLayoutMenu() {
        btnRegistro.setOnClickListener {
            if (validarContrasenas()) {
                val nombre = intent.getStringExtra("NOMBRE") ?: ""
                val tipoDocumento = intent.getStringExtra("TIPO_DOCUMENTO") ?: ""
                val documento = intent.getStringExtra("NUM_DOCUMENTO") ?: ""

                val datosUsuario = DatosUsuario(
                    nombre = nombre,
                    tipoDocumento = tipoDocumento,
                    documento = documento
                )

                val usuario = Usuario(
                    celular = campoCelular.text.toString(),
                    contraseña = contrasena.text.toString(),
                    datosUsuario = datosUsuario
                )
                if (usuario.celular.isNotEmpty() && usuario.contraseña.isNotEmpty()) {
                    usuarioManager.guardarUsuario(usuario)
                } else {
                    Toast.makeText(this, "El celular y la contraseña no pueden estar vacíos.", Toast.LENGTH_SHORT).show()
                }

                //usuarioManager.guardarUsuario(usuario)

                val intent = Intent(this, LoginActivity::class.java)
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

}