package com.icm.igosafeapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.icm.igosafeapp.manejoArchivos.UsuarioManager
import entidades.DatosUsuario
import entidades.Usuario

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
        val fotoUri = if (fotoUriString != null) Uri.parse(fotoUriString) else null

        btnRegistro.setOnClickListener {
            val contrasenaTexto = contrasena.text.toString()
            if (contrasenaTexto.length < 6) {
                Toast.makeText(this, "Al menos 6 caracteres.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (validarContrasenas()) {
                if (fotoUri == null) {
                    Toast.makeText(this, "No se seleccionó una foto de perfil.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val nombre = intent.getStringExtra("NOMBRE") ?: ""
                val tipoDocumento = intent.getStringExtra("TIPO_DOCUMENTO") ?: ""
                val documento = intent.getStringExtra("NUM_DOCUMENTO") ?: ""

                val datosUsuario = DatosUsuario(
                    nombre = nombre,
                    tipoDocumento = tipoDocumento,
                    documento = documento
                )

                usuarioManager.registrarUsuario(
                    celular = campoCelular.text.toString(),
                    contrasena = contrasena.text.toString(),
                    fotoUri = fotoUri,
                    datosUsuario = datosUsuario
                ) { exito ->
                    if (exito) {
                        Toast.makeText(this, "Usuario registrado con éxito", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, "Error al registrar el usuario", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Las contraseñas no coinciden.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validarContrasenas(): Boolean {
        return contrasena.text.toString() == confirmarContrasena.text.toString()
    }


    private fun mostrarLayoutMenu() {
        btnRegistro.setOnClickListener {
            if (validarContrasenas()) {
                val fotoUriString = intent.getStringExtra("FOTO_URI")
                val nombre = intent.getStringExtra("NOMBRE") ?: ""
                val tipoDocumento = intent.getStringExtra("TIPO_DOCUMENTO") ?: ""
                val documento = intent.getStringExtra("NUM_DOCUMENTO") ?: ""
                val status = "INACTIVO"
                val imageUrl =if (fotoUriString != null) Uri.parse(fotoUriString) else null

                if (campoCelular.text.isNullOrEmpty() || contrasena.text.isNullOrEmpty()) {
                    Toast.makeText(this, "El celular y la contraseña no pueden estar vacíos.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val datosUsuario = DatosUsuario(
                    nombre = nombre,
                    tipoDocumento = tipoDocumento,
                    documento = documento,
                )

                val usuario = Usuario(
                    celular = campoCelular.text.toString(),
                    contraseña = contrasena.text.toString(),
                    imageUrl = imageUrl, // Puede ser null si no se seleccionó una imagen
                    status = status,
                    datosUsuario = datosUsuario
                )

                usuarioManager.registrarUsuario(usuario.celular, usuario.contraseña, usuario.imageUrl, usuario.datosUsuario) { exito ->
                    if (exito) {
                        Toast.makeText(this, "Usuario registrado con éxito", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, LoginActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, "Error al registrar el usuario", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Las contraseñas no coinciden.", Toast.LENGTH_SHORT).show()
            }
        }
    }

}