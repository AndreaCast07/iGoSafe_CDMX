package com.icm.igosafeapp

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class SignInValidateSms : AppCompatActivity() {

    private lateinit var editTexts: Array<EditText>
    private lateinit var btnValidar: Button
    private lateinit var timerText: TextView
    private lateinit var resendCode: TextView

    // Firebase Auth Variables
    private lateinit var auth: FirebaseAuth
    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var celularDestino: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in_validate)

        auth = FirebaseAuth.getInstance()
        celularDestino = intent.getStringExtra("CELULAR")

        initializeViews()
        setupEditTexts()
        startPhoneNumberVerification()
    }

    private fun initializeViews() {
        // Vinculamos los 6 campos (Asegúrate de tener el 6to en tu XML)
        editTexts = arrayOf(
            findViewById(R.id.editText1), findViewById(R.id.editText2),
            findViewById(R.id.editText3), findViewById(R.id.editText4),
            findViewById(R.id.editText5), findViewById(R.id.editText6)
        )
        btnValidar = findViewById(R.id.btnValidate)
        timerText = findViewById(R.id.timer)
        resendCode = findViewById(R.id.resendCode)

        btnValidar.setOnClickListener {
            val code = editTexts.joinToString("") { it.text.toString() }
            if (code.length == 6 && verificationId != null) {
                val credential = PhoneAuthProvider.getCredential(verificationId!!, code)
                signInWithPhoneAuthCredential(credential)
            } else {
                Toast.makeText(this, "Ingresa el código de 6 dígitos", Toast.LENGTH_SHORT).show()
            }
        }

        resendCode.setOnClickListener {
            if (resendToken != null) {
                resendVerificationCode()
            }
        }
    }

    // --- LÓGICA DE FIREBASE AUTH ---

    private fun startPhoneNumberVerification() {
        // iGoSafe usa el prefijo de México (+52)
        val phoneNumber = "+52$celularDestino"

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
        startCountdownTimer()
    }

    private fun resendVerificationCode() {
        val phoneNumber = "+52$celularDestino"
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .setForceResendingToken(resendToken!!)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
        startCountdownTimer()
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // Detección automática exitosa (Instant Verification)
            val code = credential.smsCode
            if (code != null) {
                llenarCamposAutomatico(code)
            }
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Log.e("iGoSafe_Auth", "Error de Firebase: ${e.message}")
            Toast.makeText(this@SignInValidateSms, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }

        override fun onCodeSent(vId: String, token: PhoneAuthProvider.ForceResendingToken) {
            verificationId = vId
            resendToken = token
            Toast.makeText(this@SignInValidateSms, "Código enviado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, "Verificación exitosa", Toast.LENGTH_SHORT).show()
                navigateToCreatePassword()
            } else {
                Toast.makeText(this, "Código incorrecto o expirado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- LÓGICA DE INTERFAZ (UX) ---

    private fun setupEditTexts() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        editTexts.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1 && index < editTexts.size - 1) {
                        editTexts[index + 1].requestFocus()
                    } else if (index == editTexts.size - 1 && s?.length == 1) {
                        imm.hideSoftInputFromWindow(editTexts.last().windowToken, 0)
                        btnValidar.performClick() // Autovalidar al terminar
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            editText.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DEL) {
                    if (editTexts[index].text.isEmpty() && index > 0) {
                        editTexts[index - 1].requestFocus()
                        true
                    } else false
                } else false
            }
        }
    }

    private fun llenarCamposAutomatico(otp: String) {
        for (i in otp.indices) {
            if (i < editTexts.size) editTexts[i].setText(otp[i].toString())
        }
    }

    private fun startCountdownTimer() {
        object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timerText.text = "Reenviar en: ${millisUntilFinished / 1000}s"
                resendCode.visibility = View.GONE
            }
            override fun onFinish() {
                timerText.text = "Ya puedes solicitar otro código"
                resendCode.visibility = View.VISIBLE
            }
        }.start()
    }

    private fun navigateToCreatePassword() {
        val intentOriginal = intent
        val nextIntent = Intent(this, CreatePassword::class.java).apply {
            putExtra("CELULAR", intentOriginal.getStringExtra("CELULAR"))
            putExtra("NOMBRE", intentOriginal.getStringExtra("NOMBRE"))
            putExtra("EMAIL", intentOriginal.getStringExtra("EMAIL"))
            putExtra("GENERO", intentOriginal.getStringExtra("GENERO"))
            putExtra("EDAD", intentOriginal.getIntExtra("EDAD", 0))
            putExtra("NACIONALIDAD", intentOriginal.getStringExtra("NACIONALIDAD"))
            putExtra("FOTO_URI", intentOriginal.getStringExtra("FOTO_URI"))
        }
        startActivity(nextIntent)
        finish()
    }
}