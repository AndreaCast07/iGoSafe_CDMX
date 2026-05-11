package com.icm.igosafeapp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.icm.igosafeapp.databinding.ActivitySignInValidateBinding
import java.util.concurrent.TimeUnit

class SignInValidateSms : AppCompatActivity() {

    private lateinit var binding: ActivitySignInValidateBinding
    private val auth = FirebaseAuth.getInstance()
    private var verificationId: String? = null

    private lateinit var otpFields: Array<EditText>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInValidateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        otpFields = arrayOf(
            binding.editText1, binding.editText2, binding.editText3,
            binding.editText4, binding.editText5, binding.editText6
        )

        setupOtpInputs()

        val celular = intent.getStringExtra("CELULAR") ?: ""
        val phoneFull = if (celular.startsWith("+")) celular else "+52$celular"

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneFull)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onCodeSent(vId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    verificationId = vId
                    Toast.makeText(this@SignInValidateSms, "Código enviado", Toast.LENGTH_SHORT).show()
                }
                override fun onVerificationCompleted(p0: com.google.firebase.auth.PhoneAuthCredential) {
                    navigateToCreatePassword()
                }
                override fun onVerificationFailed(p0: com.google.firebase.FirebaseException) {
                    Toast.makeText(this@SignInValidateSms, "Error: ${p0.message}", Toast.LENGTH_LONG).show()
                }
            }).build()

        PhoneAuthProvider.verifyPhoneNumber(options)

        binding.btnValidate.setOnClickListener {
            validarCodigo()
        }
    }

    private fun setupOtpInputs() {
        for (i in otpFields.indices) {
            otpFields[i].addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1 && i < otpFields.size - 1) {
                        // Salta al siguiente cuadro
                        otpFields[i + 1].requestFocus()
                    } else if (s?.length == 1 && i == otpFields.size - 1) {
                        validarCodigo()
                    }
                }
            })

            otpFields[i].setOnKeyListener { v, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DEL) {
                    if (otpFields[i].text.isEmpty() && i > 0) {
                        // Regresa al cuadro anterior si el actual está vacío
                        otpFields[i - 1].requestFocus()
                        otpFields[i - 1].text = null // Borra el número anterior
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
        }
    }

    private fun validarCodigo() {
        val code = otpFields.joinToString("") { it.text.toString() }

        if (code.length == 6 && verificationId != null) {
            val credential = PhoneAuthProvider.getCredential(verificationId!!, code)
            auth.signInWithCredential(credential)
                .addOnSuccessListener {
                    Toast.makeText(this, "Verificación exitosa", Toast.LENGTH_SHORT).show()
                    navigateToCreatePassword()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Código incorrecto", Toast.LENGTH_SHORT).show()
                }
        } else if (code.length < 6) {
            Toast.makeText(this, "Ingresa los 6 dígitos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToCreatePassword() {
        val isGoogle = intent.getBooleanExtra("IS_GOOGLE", false)

        if (isGoogle) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val db = FirebaseDatabase.getInstance().getReference("usuarios")

            val datos = mapOf(
                "nombre" to intent.getStringExtra("NOMBRE"),
                "email" to intent.getStringExtra("EMAIL"),
                "celular" to intent.getStringExtra("CELULAR"),
                "celularFiltro" to intent.getStringExtra("CELULAR")?.takeLast(10),
                "genero" to intent.getStringExtra("GENERO"),
                "nacionalidad" to intent.getStringExtra("NACIONALIDAD"),
                "edad" to intent.getIntExtra("EDAD", 0)
            )

            db.child(uid).setValue(datos).addOnSuccessListener {
                Toast.makeText(this, "Registro de Google completado", Toast.LENGTH_SHORT).show()
                val intentHome = Intent(this, NavigationActivity::class.java)
                intentHome.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intentHome)
                finish()
            }
        } else {
            val next = Intent(this, CreatePassword::class.java)
            next.putExtras(intent.extras!!)
            startActivity(next)
            finish()
        }
    }
}