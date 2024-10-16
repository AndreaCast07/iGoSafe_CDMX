package com.icm.igosafeapp

import Contactos
import Usuario
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
import org.json.JSONObject
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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Inicializar las vistas
        logo = findViewById(R.id.logo)
        bienvenida = findViewById(R.id.titleName)
        celularInput = findViewById(R.id.inputCelular)
        passwordInput = findViewById(R.id.inputConstraseña)
        btnInciar = findViewById(R.id.btnIniciarSesion)
        txt = findViewById(R.id.textView)
        txtRegistrarse = findViewById(R.id.registrarse)

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
                val contacts = loadContactsFromAssets()

                val contactsJson = Gson().toJson(contacts)  //NUEVO
                val bundle = Bundle()
                bundle.putString("contacts", contactsJson)
                // Aquí puedes guardar los contactos en SharedPreferences o pasar a HomeFragment
                /*val intent = Intent(this, Menu::class.java).apply {
                    putExtra("contacts", Gson().toJson(contacts))
                }*/
                val intent = Intent(this, Menu::class.java)
                intent.putExtras(bundle)  // Pasa el Bundle con los contactos
                startActivity(intent)
                finish()

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

                if (buscarUsuario(celular, contrasena)) {
                    val intent = Intent(this, Menu::class.java)
                    startActivity(intent)
                    finish()
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

    private fun buscarUsuario(celular: String, contrasena: String): Boolean {
        val file = File(filesDir, "usuarios.json")
        if (file.exists() && file.length() > 0) {
            val jsonContent = file.readText()
            val usuarios = Gson().fromJson(jsonContent, Array<Usuario>::class.java).toList()

            for (usuario in usuarios) {
                if (usuario.celular == celular) {
                    return if (usuario.contraseña == contrasena) {
                        true
                    } else {
                        Toast.makeText(this, "Contraseña incorrecta.", Toast.LENGTH_SHORT).show()
                        false
                    }
                }
            }
            Toast.makeText(this, "El celular no está registrado.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No hay usuarios registrados.", Toast.LENGTH_SHORT).show()
        }
        return false
    }
    private fun loadContactsFromAssets(): List<Contactos> {
        val contactsList = mutableListOf<Contactos>()
        try {
            val inputStream = assets.open("contactos.json") // Asegúrate de que el archivo se llama contacts.json
            val json = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(json)
            val contactsArray = jsonObject.getJSONArray("contacts")

            for (i in 0 until contactsArray.length()) {
                val contactJson = contactsArray.getJSONObject(i)
                val name = contactJson.getString("name")
                val nickname = contactJson.getString("nickname")
                // Puedes usar un ícono predeterminado o cargar uno dinámicamente
                val icon = R.drawable.ic_person // Asegúrate de tener este ícono en tus recursos

                val locationJson = contactJson.getJSONObject("location")
                val latitude = locationJson.getDouble("latitude")
                val longitude = locationJson.getDouble("longitude")

                // Crea un objeto Contactos y agrégalo a la lista
                contactsList.add(Contactos(icon, nickname, name))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return contactsList
    }
}
