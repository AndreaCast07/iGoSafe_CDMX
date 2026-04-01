package com.icm.igosafeapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status

class SignInValidateSms : AppCompatActivity() {
    private lateinit var editTexts: Array<EditText>
    private lateinit var btnValidar: Button
    private lateinit var timerText: TextView
    private lateinit var resendCode: TextView
    private val SMS_PERMISSION_REQUEST_CODE = 100
    private var codigo: String = ""

    private val smsConsentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val message = result.data?.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)
            extractOTPFromMessage(message)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in_validate)

        if (checkSmsPermission()) {
            initializeAll()
        } else {
            requestSmsPermission()
        }
    }

    private fun initializeAll() {
        initializeViews()
        setupEditTexts()
        setupValidateButton()
        startSmsUserConsent()
    }

    private fun initializeViews() {
        editTexts = arrayOf(
            findViewById(R.id.editText1),
            findViewById(R.id.editText2),
            findViewById(R.id.editText3),
            findViewById(R.id.editText4),
            findViewById(R.id.editText5)
        )
        btnValidar = findViewById(R.id.btnValidate)
        timerText = findViewById(R.id.timer)
        resendCode = findViewById(R.id.resendCode)

        resendCode.setOnClickListener { sendOTP() }
        sendOTP()
        startCountdownTimer()
    }

    private fun startCountdownTimer() {
        object : CountDownTimer(3 * 60 * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 60000
                val seconds = (millisUntilFinished % 60000) / 1000
                timerText.text = "Tiempo límite: $minutes:${if (seconds < 10) "0" else ""}$seconds minutos"
            }
            override fun onFinish() {
                timerText.text = "El tiempo ha terminado"
            }
        }.start()
    }

    private fun setupEditTexts() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        editTexts.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1 && index < editTexts.size - 1) {
                        editTexts[index + 1].requestFocus()
                    } else if (index == editTexts.size - 1 && s?.length == 1) {
                        imm.hideSoftInputFromWindow(editTexts.last().windowToken, 0)
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

    private fun setupValidateButton() {
        btnValidar.setOnClickListener {
            val enteredCode = editTexts.joinToString("") { it.text.toString() }
            if (enteredCode == codigo) {
                Toast.makeText(this, "Código correcto", Toast.LENGTH_SHORT).show()
                navigateToCreateProfile()
            } else {
                Toast.makeText(this, "Código incorrecto, prueba de nuevo.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToCreateProfile() {
        val celular = intent.getStringExtra("CELULAR")
        val intent = Intent(this, Create_profile::class.java).apply {
            putExtra("CELULAR", celular)
        }
        startActivity(intent)
        finish()
    }

    private fun checkSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS),
            SMS_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initializeAll()
            } else {
                Toast.makeText(this, "Permisos de SMS necesarios para validar automáticamente.", Toast.LENGTH_SHORT).show()
                initializeAll() // Intentamos inicializar de todos modos para permitir ingreso manual
            }
        }
    }

    private fun sendOTP() {
        val otp = (10000..99999).random().toString()
        codigo = otp
        extractOTPFromMessage("$otp is your verification code.")
        Toast.makeText(this, "Código enviado: $otp", Toast.LENGTH_LONG).show()
    }

    private fun startSmsUserConsent() {
        SmsRetriever.getClient(this).startSmsUserConsent(null)
    }

    private val smsVerificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (SmsRetriever.SMS_RETRIEVED_ACTION == intent.action) {
                val extras = intent.extras
                val status = extras?.get(SmsRetriever.EXTRA_STATUS) as? Status
                when (status?.statusCode) {
                    CommonStatusCodes.SUCCESS -> {
                        val consentIntent: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT, Intent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT)
                        }
                        consentIntent?.let { smsConsentLauncher.launch(it) }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
            registerReceiver(smsVerificationReceiver, filter, Context.RECEIVER_EXPORTED)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13
            registerReceiver(smsVerificationReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(smsVerificationReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(smsVerificationReceiver)
        } catch (e: Exception) {}
    }

    private fun extractOTPFromMessage(message: String?) {
        message?.let {
            val otpPattern = Regex("\\d{5}")
            val otpMatcher = otpPattern.find(it)
            otpMatcher?.value?.let { otp ->
                for (i in otp.indices) {
                    if (i < editTexts.size) editTexts[i].setText(otp[i].toString())
                }
            }
        }
    }
}
