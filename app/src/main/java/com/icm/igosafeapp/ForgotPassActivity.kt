package com.icm.igosafeapp

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.database.FirebaseDatabase
import java.util.concurrent.TimeUnit

class ForgotPassActivity : AppCompatActivity() {

    private lateinit var inputPhone: EditText
    private lateinit var btnSearch: Button

    // Componentes de la sección de verificación (XML)[cite: 3]
    private lateinit var layoutCodeVerification: LinearLayout
    private lateinit var tvEmailInfo: TextView
    private lateinit var otpFields: Array<EditText>
    private lateinit var btnVerifyFinish: Button

    // Firebase y variables de control
    private lateinit var auth: FirebaseAuth
    private val dbUsuarios = FirebaseDatabase.getInstance().getReference("usuarios")
    private var verificationId: String? = null
    private var emailAsociado: String = "" // Guardamos el email para el paso final[cite: 28]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_pass)

        auth = FirebaseAuth.getInstance()
        initializeViews()
        setupOtpLogic()

        btnSearch.setOnClickListener {
            val tel = inputPhone.text.toString().trim()
            if (tel.length == 10 && tel.all { it.isDigit() }) {
                buscarUsuarioYEnviarSms(tel)
            } else {
                inputPhone.error = "Ingresa los 10 dígitos de tu celular"
            }
        }

        // Evento final: Validar código SMS y luego enviar correo[cite: 3, 28]
        btnVerifyFinish.setOnClickListener {
            val code = otpFields.joinToString("") { it.text.toString() }
            if (code.length == 5 && verificationId != null) { // Ajustado a 5 campos del XML[cite: 3]
                verificarCodigoSms(code)
            } else {
                Toast.makeText(this, "Ingresa el código completo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initializeViews() {
        inputPhone = findViewById(R.id.inputPhoneForgot)
        btnSearch = findViewById(R.id.btnSearchPhone)
        layoutCodeVerification = findViewById(R.id.layoutCodeVerification)
        tvEmailInfo = findViewById(R.id.tvEmailInfo)
        btnVerifyFinish = findViewById(R.id.btnVerifyCodeAndFinish)

        otpFields = arrayOf(
            findViewById(R.id.otp1), findViewById(R.id.otp2),
            findViewById(R.id.otp3), findViewById(R.id.otp4),
            findViewById(R.id.otp5)
        )
    }

    private fun buscarUsuarioYEnviarSms(telefono: String) {
        btnSearch.isEnabled = false
        dbUsuarios.orderByChild("celular").equalTo(telefono).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val userSnap = snapshot.children.first()
                emailAsociado = userSnap.child("email").value.toString()

                if (emailAsociado.isNotEmpty() && emailAsociado != "null") {
                    iniciarVerificacionSms(telefono) // Primero verificamos identidad por SMS[cite: 35]
                } else {
                    btnSearch.isEnabled = true
                    Toast.makeText(this, "El usuario no tiene un correo válido", Toast.LENGTH_SHORT).show()
                }
            } else {
                btnSearch.isEnabled = true
                Toast.makeText(this, "Número no registrado", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            btnSearch.isEnabled = true
            Toast.makeText(this, "Error de conexión", Toast.LENGTH_SHORT).show()
        }
    }

    private fun iniciarVerificacionSms(telefono: String) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber("+52$telefono") // Prefijo México[cite: 35]
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // Verificación automática exitosa[cite: 35]
                    credential.smsCode?.let { llenarOtpYValidar(it) }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    btnSearch.isEnabled = true
                    Toast.makeText(this@ForgotPassActivity, "Fallo al enviar SMS: ${e.message}", Toast.LENGTH_LONG).show()
                }

                override fun onCodeSent(vId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    verificationId = vId
                    layoutCodeVerification.visibility = View.VISIBLE // Mostramos sección OTP[cite: 3]
                    Toast.makeText(this@ForgotPassActivity, "Código enviado al celular", Toast.LENGTH_SHORT).show()
                }
            }).build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun verificarCodigoSms(code: String) {
        val credential = PhoneAuthProvider.getCredential(verificationId!!, code)
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // SMS validado: Ahora sí enviamos el correo de reestablecimiento[cite: 28]
                enviarCorreoFinal()
            } else {
                Toast.makeText(this, "Código incorrecto", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun enviarCorreoFinal() {
        auth.sendPasswordResetEmail(emailAsociado).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, "Correo de reestablecimiento enviado a $emailAsociado", Toast.LENGTH_LONG).show()
                // Regresamos al Login (finish cierra la actividad actual)
                inputPhone.postDelayed({ finish() }, 2000)
            }
        }
    }

    private fun setupOtpLogic() {
        otpFields.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1 && index < otpFields.size - 1) {
                        otpFields[index + 1].requestFocus()
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
            // Soporte para borrar (regresar al cuadro anterior)[cite: 35]
            editText.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DEL) {
                    if (editText.text.isEmpty() && index > 0) {
                        otpFields[index - 1].requestFocus()
                        true
                    } else false
                } else false
            }
        }
    }

    private fun llenarOtpYValidar(otp: String) {
        for (i in otp.indices) {
            if (i < otpFields.size) otpFields[i].setText(otp[i].toString())
        }
        btnVerifyFinish.performClick()
    }
}