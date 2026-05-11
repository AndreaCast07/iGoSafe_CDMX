package com.icm.igosafeapp

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
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
    private lateinit var layoutCodeVerification: LinearLayout
    private lateinit var tvEmailInfo: TextView
    private lateinit var otpFields: Array<EditText>
    private lateinit var btnVerifyFinish: Button

    private lateinit var auth: FirebaseAuth
    private val dbUsuarios = FirebaseDatabase.getInstance().getReference("usuarios")
    private var verificationId: String? = null
    private var emailAsociado: String = ""
    private var celularCompletoConPrefijo: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_pass)

        auth = FirebaseAuth.getInstance()
        initializeViews()
        setupOtpLogic()

        btnSearch.setOnClickListener {
            val tel = inputPhone.text.toString().trim()
            if (tel.isNotEmpty()) {
                buscarUsuarioYEnviarSms(tel)
            } else {
                inputPhone.error = "Ingresa tu número de celular"
            }
        }

        btnVerifyFinish.setOnClickListener {
            val code = otpFields.joinToString("") { it.text.toString() }
            if (code.length == 6 && verificationId != null) {
                verificarCodigoSms(code)
            } else {
                Toast.makeText(this, "Ingresa el código de 6 dígitos", Toast.LENGTH_SHORT).show()
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
            findViewById(R.id.otp5), findViewById(R.id.otp6)
        )
    }

    private fun buscarUsuarioYEnviarSms(telefonoInput: String) {
        btnSearch.isEnabled = false
        btnSearch.text = "Buscando..."

        val filtroBusqueda = telefonoInput.takeLast(10)

        dbUsuarios.orderByChild("celularFiltro").equalTo(filtroBusqueda).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val userSnap = snapshot.children.first()
                emailAsociado = userSnap.child("email").value.toString()
                celularCompletoConPrefijo = userSnap.child("celular").value.toString()

                if (emailAsociado.isNotEmpty() && emailAsociado != "null") {
                    iniciarVerificacionSms(celularCompletoConPrefijo)
                } else {
                    restablecerBoton("El usuario no tiene un correo válido")
                }
            } else {
                restablecerBoton("El número ingresado no está registrado en iGoSafe")
            }
        }.addOnFailureListener {
            restablecerBoton("Error de conexión. Revisa tu internet.")
        }
    }

    private fun restablecerBoton(mensaje: String) {
        btnSearch.isEnabled = true
        btnSearch.text = "Buscar"
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
    }

    private fun iniciarVerificacionSms(numeroDestino: String) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(numeroDestino)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    credential.smsCode?.let { llenarOtpYValidar(it) }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    restablecerBoton("Fallo al enviar SMS: ${e.localizedMessage}")
                }

                override fun onCodeSent(vId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    verificationId = vId
                    layoutCodeVerification.visibility = View.VISIBLE
                    tvEmailInfo.text = "Se ha enviado un código al número asociado."
                    Toast.makeText(this@ForgotPassActivity, "Código enviado", Toast.LENGTH_SHORT).show()
                }
            }).build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun verificarCodigoSms(code: String) {
        val credential = PhoneAuthProvider.getCredential(verificationId!!, code)
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                enviarCorreoFinal()
            } else {
                Toast.makeText(this, "Código incorrecto", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun enviarCorreoFinal() {
        auth.sendPasswordResetEmail(emailAsociado).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, "Correo enviado a $emailAsociado. Revisa tu bandeja.", Toast.LENGTH_LONG).show()
                inputPhone.postDelayed({ finish() }, 2500)
            } else {
                Toast.makeText(this, "Error al enviar el correo: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
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