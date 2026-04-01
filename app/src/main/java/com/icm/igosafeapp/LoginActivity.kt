package com.icm.igosafeapp

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.icm.igosafeapp.databinding.ActivityLoginBinding
import com.icm.igosafeapp.manejoArchivos.UsuarioManager

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var usuarioManager: UsuarioManager

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                val idToken = account.idToken
                if (idToken != null) {
                    firebaseAuthWithGoogle(idToken)
                } else {
                    Toast.makeText(this, "Error: Token de Google no obtenido", Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                Log.e("Login", "Google sign in failed. Code: ${e.statusCode}", e)
                val msg = when (e.statusCode) {
                    12500 -> "Error 12500: Verifique la huella SHA-1 en Firebase."
                    10 -> "Error 10: Configuración de Google Sign-In incorrecta."
                    else -> "Error de Google (Code: ${e.statusCode})"
                }
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        auth = FirebaseAuth.getInstance()
        usuarioManager = UsuarioManager(this)

        // Manejo del botón Atrás para salir de la app
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishAffinity()
            }
        })

        // LOG DIAGNÓSTICO: Imprimir el Device ID actual
        val currentDeviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        Log.d("DeviceId", "El ID de este dispositivo es: $currentDeviceId")

        // Siempre inflamos la vista primero para mantener al usuario aquí si es necesario
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val currentUser = auth.currentUser
        if (currentUser != null) {
            usuarioManager.usuarioExiste(currentUser.uid) { existe ->
                if (existe) {
                    startActivity(Intent(this, Menu::class.java))
                    finish()
                } else {
                    auth.signOut()
                    Log.d("Login", "Sesión cerrada: El usuario no tenía un perfil completo.")
                }
            }
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) 
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setupEventListeners()
    }

    private fun setupEventListeners() {
        binding.btnIniciarSesion.setOnClickListener {
            val celular = binding.inputCelular.text.toString().trim()
            val contrasena = binding.inputPassword.text.toString().trim()

            if (celular.isNotEmpty() && contrasena.isNotEmpty()) {
                usuarioManager.celularRegistrado(celular) { registrado ->
                    if (registrado) {
                        usuarioManager.iniciarSesion(celular, contrasena) { exito ->
                            if (exito) {
                                startActivity(Intent(this, Menu::class.java))
                                finish()
                            } else {
                                Toast.makeText(this, "Error al iniciar sesión. Verifique sus credenciales.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "El celular no está registrado.", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Por favor, llena todos los campos.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnGoogle.setOnClickListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }

        binding.registrarse.setOnClickListener {
            startActivity(Intent(this, SignInPhone::class.java))
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = task.result?.user
                    if (user != null) {
                        usuarioManager.usuarioExiste(user.uid) { existe ->
                            if (existe) {
                                startActivity(Intent(this, Menu::class.java))
                                finish()
                            } else {
                                val intent = Intent(this, Create_profile::class.java)
                                intent.putExtra("IS_GOOGLE", true)
                                intent.putExtra("NOMBRE", user.displayName)
                                startActivity(intent)
                                finish()
                            }
                        }
                    }
                } else {
                    Toast.makeText(this, "Error de autenticación con Firebase", Toast.LENGTH_LONG).show()
                }
            }
    }
}
