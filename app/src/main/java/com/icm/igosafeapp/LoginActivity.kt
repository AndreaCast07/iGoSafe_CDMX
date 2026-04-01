package com.icm.igosafeapp

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
                Log.e("Login", "Google sign in failed", e)
                Toast.makeText(this, "Error de Google (Code: ${e.statusCode})", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        usuarioManager = UsuarioManager(this)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        verificarSesionExistente()

        setupPasswordVisibility()
        setupEventListeners()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishAffinity()
            }
        })

        val currentDeviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        Log.d("DeviceId", "ID: $currentDeviceId")
    }

    private fun verificarSesionExistente() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            usuarioManager.usuarioExiste(currentUser.uid) { existe ->
                if (existe) {
                    startActivity(Intent(this, Menu::class.java))
                    finish()
                } else {
                    auth.signOut()
                }
            }
        }
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
                                Toast.makeText(this, "Credenciales incorrectas", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "El número no está registrado", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
            }
        }

        // Google Sign-In
        binding.btnGoogle.setOnClickListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }

        // Apple Sign-In
        binding.btnApple.setOnClickListener {
            iniciarSesionApple()
        }

        // Recuperar Contraseña
        binding.forgotPassword.setOnClickListener {
            mostrarDialogoRecuperacion()
        }

        // Ir a Registro
        binding.registrarse.setOnClickListener {
            startActivity(Intent(this, SignInPhone::class.java))
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val user = task.result?.user
                manejarFlujoPostLogin(user?.uid, user?.displayName)
            }
        }
    }

    private fun iniciarSesionApple() {
        val provider = OAuthProvider.newBuilder("apple.com")
        provider.scopes = listOf("email", "name")

        auth.startActivityForSignInWithProvider(this, provider.build())
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                manejarFlujoPostLogin(user?.uid, user?.displayName)
            }
            .addOnFailureListener { e ->
                Log.e("AppleAuth", "Error", e)
                Toast.makeText(this, "Error al conectar con Apple", Toast.LENGTH_SHORT).show()
            }
    }

    private fun manejarFlujoPostLogin(uid: String?, nombre: String?) {
        if (uid == null) return
        usuarioManager.usuarioExiste(uid) { existe ->
            if (existe) {
                startActivity(Intent(this, Menu::class.java))
                finish()
            } else {
                val intent = Intent(this, Create_profile::class.java).apply {
                    putExtra("IS_SOCIAL_LOGIN", true)
                    putExtra("NOMBRE", nombre)
                }
                startActivity(intent)
                finish()
            }
        }
    }

    private fun mostrarDialogoRecuperacion() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Recuperar contraseña")

        val input = EditText(this)
        input.hint = "Correo electrónico"
        builder.setView(input)

        builder.setPositiveButton("Enviar") { _, _ ->
            val email = input.text.toString().trim()
            if (email.isNotEmpty()) {
                auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Enlace enviado a su correo", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        builder.setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
        builder.show()
    }

    private fun setupPasswordVisibility() {
        binding.inputPassword.setOnTouchListener { _, event ->
            val DRAWABLE_RIGHT = 2
            if (event.action == MotionEvent.ACTION_UP) {
                val drawable = binding.inputPassword.compoundDrawables[DRAWABLE_RIGHT]
                if (event.rawX >= (binding.inputPassword.right - drawable.bounds.width())) {
                    val selection = binding.inputPassword.selectionEnd
                    if (binding.inputPassword.transformationMethod is PasswordTransformationMethod) {
                        binding.inputPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                        binding.inputPassword.setCompoundDrawablesWithIntrinsicBounds(R.drawable.custom_lock_icon, 0, R.drawable.ic_eye_hide, 0)
                    } else {
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