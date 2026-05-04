package com.icm.igosafeapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
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
import com.google.firebase.auth.OAuthProvider
import com.icm.igosafeapp.databinding.ActivityLoginBinding
import com.icm.igosafeapp.manejoArchivos.UsuarioManager

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var usuarioManager: UsuarioManager

    // Lanzador para el resultado de la ventana de Google
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
                Log.e("iGoSafe_Login", "Google sign in failed", e)
                Toast.makeText(this, "Error de Google (Código: ${e.statusCode})", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        usuarioManager = UsuarioManager(this)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- CONFIGURACIÓN DE GOOGLE ---
        // Se recomienda mover este ID a strings.xml por seguridad
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("371704711119-tpiktdm5u5659egicchpaq5fgqmv0dgv.apps.googleusercontent.com")
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // 1. Verificamos sesión persistente al abrir la app
        verificarSesionExistente()

        setupPasswordVisibility()
        setupEventListeners()

        // Manejo del botón atrás para cerrar la app por completo
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishAffinity()
            }
        })
    }

    private fun verificarSesionExistente() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            usuarioManager.usuarioExiste(currentUser.uid) { existe ->
                if (existe) {
                    irAlMenu()
                } else {
                    auth.signOut()
                }
            }
        }
    }

    private fun setupEventListeners() {
        // Inicio de sesión con Celular y Contraseña
        binding.btnIniciarSesion.setOnClickListener {
            val celular = binding.inputCelular.text.toString().trim()
            val contrasena = binding.inputPassword.text.toString().trim()

            if (celular.isNotEmpty() && contrasena.isNotEmpty()) {
                usuarioManager.celularRegistrado(celular) { registrado ->
                    if (registrado) {
                        usuarioManager.iniciarSesion(celular, contrasena) { exito ->
                            if (exito) {
                                irAlMenu()
                            } else {
                                Toast.makeText(this, "Contraseña incorrecta", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "El número no está registrado en iGoSafe", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show()
            }
        }

        // Inicio de sesión con Google
        binding.btnGoogle.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            }
        }

        // Inicio de sesión con Apple
        binding.btnApple.setOnClickListener {
            iniciarSesionApple()
        }

        // Recuperar contraseña
        binding.forgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPassActivity::class.java))
        }

        // Crear cuenta nueva
        binding.registrarse.setOnClickListener {
            startActivity(Intent(this, Create_profile::class.java))
        }
    }

    private fun irAlMenu() {
        val intent = Intent(this, Menu::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val user = task.result?.user
                // CORRECCIÓN: Ahora pasamos el email también
                manejarFlujoPostLogin(user?.uid, user?.displayName, user?.email)
            } else {
                Log.e("iGoSafe_Error", "Firebase Auth falló", task.exception)
                Toast.makeText(this, "Error de Firebase: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun iniciarSesionApple() {
        val provider = OAuthProvider.newBuilder("apple.com")
        provider.scopes = listOf("email", "name")
        provider.addCustomParameter("locale", "es_MX")

        // CORRECCIÓN: Manejo de resultados pendientes para mayor robustez
        val pending = auth.pendingAuthResult
        if (pending != null) {
            pending.addOnSuccessListener { authResult ->
                val user = authResult.user
                manejarFlujoPostLogin(user?.uid, user?.displayName, user?.email)
            }.addOnFailureListener { e ->
                Log.e("iGoSafe_Apple", "Error en flujo pendiente", e)
            }
        } else {
            auth.startActivityForSignInWithProvider(this, provider.build())
                .addOnSuccessListener { authResult ->
                    val user = authResult.user
                    manejarFlujoPostLogin(user?.uid, user?.displayName, user?.email)
                }
                .addOnFailureListener { e ->
                    Log.e("iGoSafe_Apple", "Error en Apple Auth", e)
                    Toast.makeText(this, "Fallo al conectar con Apple", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun manejarFlujoPostLogin(uid: String?, nombre: String?, email: String?) {
        if (uid == null) return
        usuarioManager.usuarioExiste(uid) { existe ->
            if (existe) {
                irAlMenu()
            } else {
                // CORRECCIÓN: Mandamos nombre Y email para autocompletar el perfil
                val intent = Intent(this, Create_profile::class.java).apply {
                    putExtra("IS_SOCIAL_LOGIN", true)
                    putExtra("NOMBRE", nombre)
                    putExtra("EMAIL", email)
                }
                startActivity(intent)
                finish()
            }
        }
    }

    private fun setupPasswordVisibility() {
        binding.inputPassword.setOnTouchListener { _, event ->
            val DRAWABLE_RIGHT = 2
            if (event.action == MotionEvent.ACTION_UP) {
                val drawable = binding.inputPassword.compoundDrawables[DRAWABLE_RIGHT]
                if (event.rawX >= (binding.inputPassword.right - drawable.bounds.width())) {
                    val selection = binding.inputPassword.selectionEnd

                    if (binding.inputPassword.transformationMethod is PasswordTransformationMethod) {
                        // CORRECCIÓN: Cambio de icono a 'Visible'
                        binding.inputPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                        binding.inputPassword.setCompoundDrawablesWithIntrinsicBounds(R.drawable.custom_lock_icon, 0, R.drawable.ic_eye_hide, 0)
                    } else {
                        // CORRECCIÓN: Cambio de icono a 'Oculto'
                        binding.inputPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                        binding.inputPassword.setCompoundDrawablesWithIntrinsicBounds(R.drawable.custom_lock_icon, 0, R.drawable.ic_eye_hide, 0)
                    }
                    binding.inputPassword.setSelection(selection)
                    return@setOnTouchListener true
                }
            }
            false
        }
    }
}