package com.icm.igosafeapp

import android.annotation.SuppressLint
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.icm.igosafeapp.manejoArchivos.UsuarioManager
import entidades.Usuario
import java.io.File

class LoginActivity : AppCompatActivity() {
    private lateinit var logo: ImageView
    private lateinit var bienvenida: TextView
    private lateinit var celularInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var txtOlvidarContraseña: TextView
    private lateinit var btnInciar: Button
    private lateinit var txt: TextView
    private lateinit var txtRegistrarse: TextView

    private lateinit var usuarioManager: UsuarioManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        // Inicializar las vistas
        logo = findViewById(R.id.logo)
        bienvenida = findViewById(R.id.titleName)
        celularInput = findViewById(R.id.inputCelular)
        passwordInput = findViewById(R.id.inputConstraseña)
        btnInciar = findViewById(R.id.btnIniciarSesion)
        txt = findViewById(R.id.textView)
        txtRegistrarse = findViewById(R.id.registrarse)

        usuarioManager = UsuarioManager(this)

        checkPermissions()

        ocultarContrasena()
        mostrarLayoutRegisterPhone()
        mostrarLayoutMenu()

    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestContactsPermission()
        } else {
        }
    }

    private fun requestContactsPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_CONTACTS)) {
            Toast.makeText(this, "Necesitamos acceso a los contactos para funcionalidades completas.", Toast.LENGTH_LONG).show()
        }
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            } else {
                // Permiso denegado
                Toast.makeText(this, "Funcionalidades reducidas.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 777
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
            Log.d("LoginActivity", "Botón de registrarse clickeado")
            val intent = Intent(this, SignInPhone::class.java)
            startActivity(intent)
        }
    }


    private fun mostrarLayoutMenu() {
        btnInciar.setOnClickListener {
            if (validarCampos()) {
                val celular = celularInput.text.toString()
                val contrasena = passwordInput.text.toString()

                if (usuarioManager.celularRegistrado(celular)) {
                    if (usuarioManager.verificarUsuario(celular, contrasena)) {
                        val intent = Intent(this, Menu::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, "Contraseña incorrecta.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "El celular no está registrado.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Por favor, llena todos los campos.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finishAffinity()
    }

    private fun validarCampos(): Boolean {
        return celularInput.text.toString().isNotEmpty() && passwordInput.text.toString().isNotEmpty()
    }

}
