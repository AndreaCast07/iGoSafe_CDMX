package com.icm.igosafeapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
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
import com.icm.igosafeapp.manejoArchivos.UsuarioManager

class SignInPhone : AppCompatActivity() {
    private lateinit var btnSend: Button
    private lateinit var txtIniciarSesion: TextView
    private lateinit var editTxtCelular: TextView
    private lateinit var txt: TextView
    private lateinit var logo: ImageView
    private lateinit var btnGoogleSignUp: SignInButton
    
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
                Log.e("SignInPhone", "Google sign in failed. Code: ${e.statusCode}", e)
                Toast.makeText(this, "Error de Google: ${e.statusCode}", Toast.LENGTH_LONG).show()
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

        // Inicializar las vistas
        btnSend = findViewById(R.id.btnSendSMS)
        txtIniciarSesion = findViewById(R.id.iniciarSesion)
        editTxtCelular = findViewById(R.id.registrarCelular)
        txt = findViewById(R.id.textView2)
        logo = findViewById(R.id.logoGris)
        btnGoogleSignUp = findViewById(R.id.btnGoogleSignUp)

        configurarListeners()
    }

    private fun configurarListeners() {
        txtIniciarSesion.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        btnSend.setOnClickListener {
            val celular = editTxtCelular.text.toString().trim()
            if (celular.isEmpty()) {
                Toast.makeText(this, "Por favor, ingrese su número de celular", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val celularRegex = "^[0-9]{8,}$".toRegex()
            if (!celular.matches(celularRegex)) {
                Toast.makeText(this, "El número de celular debe tener al menos 8 dígitos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, SignInValidateSms::class.java).apply {
                putExtra("CELULAR", celular)
            }
            startActivity(intent)
        }

        btnGoogleSignUp.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
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
                                // Usuario nuevo: ir a crear perfil
                                val intent = Intent(this, Create_profile::class.java)
                                intent.putExtra("IS_GOOGLE", true)
                                intent.putExtra("NOMBRE", user.displayName)
                                startActivity(intent)
                                finish()
                            }
                        }
                    }
                } else {
                    Toast.makeText(this, "Error de autenticación con Firebase", Toast.LENGTH_SHORT).show()
                }
            }
    }
}