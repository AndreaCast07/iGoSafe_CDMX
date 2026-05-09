package com.icm.igosafeapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.icm.igosafeapp.manejoArchivos.UsuarioManager

class SignInPhone : AppCompatActivity() {
    private lateinit var btnSend: Button
    private lateinit var txtIniciarSesion: TextView
    private lateinit var editTxtCelular: EditText // Cambiado a EditText para mejor manejo de input
    private lateinit var logo: ImageView
    private lateinit var btnGoogleSignUp: SignInButton

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var usuarioManager: UsuarioManager
    private val database = FirebaseDatabase.getInstance().getReference("usuarios")

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                val idToken = account.idToken
                if (idToken != null) {
                    firebaseAuthWithGoogle(idToken)
                }
            } catch (e: ApiException) {
                Log.e("SignInPhone", "Google sign in failed", e)
            }
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in_phone)

        auth = FirebaseAuth.getInstance()
        usuarioManager = UsuarioManager(this)

        // Configurar Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Inicializar vistas
        btnSend = findViewById(R.id.btnSendSMS)
        txtIniciarSesion = findViewById(R.id.iniciarSesion)
        editTxtCelular = findViewById(R.id.registrarCelular)
        logo = findViewById(R.id.logoGris)
        btnGoogleSignUp = findViewById(R.id.btnGoogleSignUp)

        configurarListeners()
    }

    private fun configurarListeners() {
        txtIniciarSesion.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        btnSend.setOnClickListener {
            val celular = editTxtCelular.text.toString().trim()

            // 1. Validación de formato (10 dígitos para México/iGoSafe)
            if (celular.length != 10 || !celular.all { it.isDigit() }) {
                editTxtCelular.error = "Ingresa los 10 dígitos de tu celular"
                return@setOnClickListener
            }

            // 2. Obtener el Device ID (Solo para registro, no para bloqueo)
            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

            // 3. Validar disponibilidad del Celular en Firebase antes de seguir
            validarCelularYProceder(celular, deviceId)
        }

        btnGoogleSignUp.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }

    private fun validarCelularYProceder(celular: String, deviceId: String) {
        btnSend.isEnabled = false // Evitar clics dobles

        database.orderByChild("celular").equalTo(celular).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                // El celular ya está en iGoSafe
                Toast.makeText(this, "Este número ya tiene una cuenta. Inicia sesión.", Toast.LENGTH_LONG).show()
                btnSend.isEnabled = true
            } else {
                // TODO OK: No checamos Device ID, solo pasamos a validar el SMS
                val intent = Intent(this, SignInValidateSms::class.java).apply {
                    putExtra("CELULAR", celular)
                    putExtra("DEVICE_ID", deviceId)
                }
                startActivity(intent)
                btnSend.isEnabled = true
            }
        }.addOnFailureListener {
            btnSend.isEnabled = true
            Toast.makeText(this, "Error de conexión", Toast.LENGTH_SHORT).show()
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
                                startActivity(Intent(this, NavigationActivity::class.java))
                                finish()
                            } else {
                                val intent = Intent(this, Create_profile::class.java)
                                intent.putExtra("IS_GOOGLE", true)
                                intent.putExtra("NOMBRE", user.displayName)
                                intent.putExtra("EMAIL", user.email)
                                startActivity(intent)
                                finish()
                            }
                        }
                    }
                }
            }
    }
}