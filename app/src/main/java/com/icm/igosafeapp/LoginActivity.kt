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
        enableEdgeToEdge()
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
                Toast.makeText(this, "Funcionalidades reducidas.", Toast.LENGTH_LONG).show()
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
            val intent = Intent(this, Menu::class.java)
            startActivity(intent)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finishAffinity()
    }

}
