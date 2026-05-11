package com.icm.igosafeapp

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.icm.igosafeapp.databinding.ActivityLoginBinding
import com.icm.igosafeapp.manejoArchivos.UsuarioManager

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth = FirebaseAuth.getInstance()
    private val dbUsuarios = FirebaseDatabase.getInstance().getReference("usuarios")

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var usuarioManager: UsuarioManager

    private var isPasswordVisible = false

    private val googleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.e("iGoSafe_Login", "Error de Google: ${e.message}")
                Toast.makeText(this, "No se seleccionó ninguna cuenta", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        usuarioManager = UsuarioManager()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Listeners
        binding.btnIniciarSesion.setOnClickListener { validarEIniciarSesion() }
        binding.registrarse.setOnClickListener { startActivity(Intent(this, Create_profile::class.java)) }
        binding.forgotPassword.setOnClickListener { startActivity(Intent(this, ForgotPassActivity::class.java)) }

        binding.btnGoogle.setOnClickListener {
            // Forzamos el cierre de sesión para que siempre pregunte qué cuenta usar
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                googleLauncher.launch(signInIntent)
            }
        }

        setupPasswordVisibilityToggle()
    }

    private fun setupPasswordVisibilityToggle() {
        binding.inputPassword.setOnTouchListener { _, event ->
            val DRAWABLE_RIGHT = 2
            if (event.action == MotionEvent.ACTION_UP) {
                val drawable = binding.inputPassword.compoundDrawables[DRAWABLE_RIGHT]
                if (drawable != null && event.rawX >= (binding.inputPassword.right - drawable.bounds.width() - 50)) {
                    if (isPasswordVisible) {
                        binding.inputPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                        binding.inputPassword.setCompoundDrawablesWithIntrinsicBounds(R.drawable.custom_lock_icon, 0, R.drawable.ic_eye_hide, 0)
                        isPasswordVisible = false
                    } else {
                        binding.inputPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                        binding.inputPassword.setCompoundDrawablesWithIntrinsicBounds(R.drawable.custom_lock_icon, 0, R.drawable.ic_ojo, 0)
                        isPasswordVisible = true
                    }
                    binding.inputPassword.setSelection(binding.inputPassword.text.length)
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        binding.btnIniciarSesion.isEnabled = false
        binding.btnIniciarSesion.text = "Verificando..."

        auth.signInWithCredential(credential).addOnSuccessListener { result ->
            val user = result.user
            if (user != null) {
                val googleUid = user.uid
                val googleEmail = user.email?.lowercase()?.trim() ?: ""

                // 1. ¿Existe ya el perfil con este UID de Google?
                dbUsuarios.child(googleUid).get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        irANavigation()
                    } else {
                        // 2. Si no existe, buscamos si hay datos bajo este Email (Migración)
                        dbUsuarios.orderByChild("email").equalTo(googleEmail).get().addOnSuccessListener { emailSnapshot ->
                            if (emailSnapshot.exists()) {
                                val oldUserSnap = emailSnapshot.children.first()
                                val oldUid = oldUserSnap.key
                                val datos = oldUserSnap.value

                                if (oldUid != null && datos != null && oldUid != googleUid) {
                                    // MIGRACIÓN: Movemos los datos al nuevo UID de Google
                                    val updates = hashMapOf<String, Any>()
                                    updates["/usuarios/$googleUid"] = datos

                                    val celularFiltro = oldUserSnap.child("celularFiltro").value?.toString()
                                    if (celularFiltro != null) {
                                        updates["/celulares_registrados/$celularFiltro"] = googleUid
                                    }

                                    // Borramos el rastro del UID viejo para evitar basura
                                    FirebaseDatabase.getInstance().reference.child("usuarios").child(oldUid).removeValue()

                                    FirebaseDatabase.getInstance().reference.updateChildren(updates).addOnSuccessListener {
                                        Log.d("iGoSafe", "Migración exitosa")
                                        irANavigation()
                                    }
                                } else {
                                    irANavigation()
                                }
                            } else {
                                // 3. Es realmente nuevo, mandamos a crear perfil
                                val intent = Intent(this, Create_profile::class.java).apply {
                                    putExtra("IS_GOOGLE", true)
                                    putExtra("NOMBRE", user.displayName)
                                    putExtra("EMAIL", user.email)
                                }
                                startActivity(intent)
                                finish()
                            }
                        }
                    }
                }
            }
        }.addOnFailureListener { restablecerBoton("Fallo en Google Auth") }
    }

    private fun validarEIniciarSesion() {
        val telefonoRaw = binding.inputCelular.text.toString().trim()
        val password = binding.inputPassword.text.toString().trim()

        if (telefonoRaw.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        val telefonoLimpio = telefonoRaw.replace(Regex("[^0-9]"), "")
        if (telefonoLimpio.length < 10) {
            Toast.makeText(this, "Número inválido", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnIniciarSesion.isEnabled = false
        binding.btnIniciarSesion.text = "Entrando..."

        val filtroCelular = telefonoLimpio.takeLast(10)

        // Esta es la consulta que fallaba por las reglas
        dbUsuarios.orderByChild("celularFiltro").equalTo(filtroCelular).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val userSnap = snapshot.children.first()
                    val email = userSnap.child("email").value?.toString()

                    if (email != null) {
                        auth.signInWithEmailAndPassword(email, password)
                            .addOnSuccessListener {
                                startActivity(Intent(this, NavigationActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener {
                                restablecerBoton("Contraseña incorrecta")
                            }
                    } else {
                        restablecerBoton("Error: Datos de cuenta incompletos")
                    }
                } else {
                    restablecerBoton("El número no está registrado")
                }
            }
            .addOnFailureListener { e ->
                // Si después de cambiar las reglas sigue saliendo esto,
                // el Logcat te dirá exactamente por qué (ej. falta de internet o SHA-1)
                Log.e("iGoSafe_Login", "Error de DB: ${e.message}")
                restablecerBoton("Error de conexión con el servidor")
            }
    }

    private fun irANavigation() {
        Toast.makeText(this, "Bienvenido de vuelta", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, NavigationActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun restablecerBoton(mensaje: String) {
        binding.btnIniciarSesion.isEnabled = true
        binding.btnIniciarSesion.text = "Iniciar Sesión"
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
    }
}