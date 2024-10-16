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
import android.telephony.SmsManager
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
import androidx.core.content.IntentSanitizer
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.tasks.Task
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status


class SignInValidateSms : AppCompatActivity() {
    private lateinit var editTexts: Array<EditText>
    private lateinit var btnValidar: Button
    private lateinit var etPhone: EditText
    private lateinit var timerText: TextView
    private lateinit var resendCode: TextView
    private val SMS_PERMISSION_REQUEST_CODE = 100
    private lateinit var codigo:String

    private val smsConsentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val message = result.data?.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)
            extractOTPFromMessage(message)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in_validate)

        // Verificar permisos
        if (checkSmsPermission()) {
            initializeViews()
            setupEditTexts()
            setupValidateButton()
            startSmsUserConsent()
        } else {
            // Solicitar permisos si no están concedidos
            requestSmsPermission()
        }
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
        //etPhone = findViewById(R.id.etPhone)
        timerText = findViewById(R.id.timer) // Connect timer TextView
        resendCode = findViewById(R.id.resendCode) // Connect resend TextView


        resendCode.setOnClickListener {
            sendOTP()
        }
        sendOTP()
        // Optionally, start a countdown timer
        startCountdownTimer()
    }

    private fun startCountdownTimer() {
        object : CountDownTimer(3 * 60 * 1000, 1000) { // 3 minutes timer
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
            editText.addTextChangedListener(createTextWatcher(index, imm))
            editText.setOnKeyListener(createKeyListener(index))
        }
    }

    private fun createTextWatcher(index: Int, imm: InputMethodManager) = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            if (s?.length == 1 && index < editTexts.size - 1) {
                editTexts[index + 1].requestFocus()
            } else if (index == editTexts.size - 1 && s?.length == 1) {
                imm.hideSoftInputFromWindow(editTexts.last().windowToken, 0)
            }
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    private fun createKeyListener(index: Int) = { _: Any, keyCode: Int, event: KeyEvent ->
        if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DEL) {
            if (editTexts[index].text.isEmpty() && index > 0) {
                editTexts[index - 1].requestFocus()
                true
            } else false
        } else false
    }

    private fun setupValidateButton() {
        btnValidar.setOnClickListener {
            val enteredCode = editTexts.joinToString("") { it.text.toString() }
            if (enteredCode == codigo) {
                Toast.makeText(this, "Código correcto", Toast.LENGTH_SHORT).show()
                navigateToCreateProfile()
            } else {
                Toast.makeText(this, "Código incorrecto, prueba de nuvo.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun navigateToCreateProfile() {
        val celular = intent.getStringExtra("CELULAR") // Intent del celular
        val intent = Intent(this, Create_profile::class.java).apply {
            putExtra("CELULAR", celular)
        }
        startActivity(intent)
        finish()
    }

    private fun setupValidateButtons() {
        btnValidar.setOnClickListener {
            if (checkSmsPermission()) {
                sendOTP()
            } else {
                requestSmsPermission()
            }
        }
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sendOTP()
            } else {
                Toast.makeText(this, "SMS permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendOTP() {
        /*
        val phone = etPhone.text.toString()
        val message = "$otp es su código de verificación."

        val smsManager = SmsManager.getDefault()
        val parts = smsManager.divideMessage(message)
        smsManager.sendMultipartTextMessage(phone, null, parts, null, null)*/
        val otp = generateOTP()
        codigo = otp
        extractOTPFromMessage("$otp is your verification code.")

        Toast.makeText(this, "Código enviado", Toast.LENGTH_SHORT).show()
    }

    private fun generateOTP(): String {
        return (10000..99999).random().toString()
    }

    private fun startSmsUserConsent() {
        val client = SmsRetriever.getClient(this)
        val task: Task<Void> = client.startSmsUserConsent(null)
        task.addOnSuccessListener {
            Log.i("SMS started succesfully","SMS se obtuvo perfectamente")// SMS retriever started successfully
        }.addOnFailureListener {
            Log.e("SMS failed to retrieve ","SMS no encontrado, revisar")
            // Failed to start SMS retriever
        }
    }

    private val smsVerificationReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        override fun onReceive(context: Context, intent: Intent) {
            if (SmsRetriever.SMS_RETRIEVED_ACTION == intent.action) {
                val extras = intent.extras
                val status = extras?.get(SmsRetriever.EXTRA_STATUS) as Status
                when (status.statusCode) {
                    CommonStatusCodes.SUCCESS -> {
                        val consentIntent: Intent? = extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT, Intent::class.java)
                        consentIntent?.let {
                            try {
                                val sanitizedIntent = IntentSanitizer.Builder()
                                    .allowAction(SmsRetriever.SMS_RETRIEVED_ACTION)
                                    .allowExtra(SmsRetriever.EXTRA_STATUS, Status::class.java)
                                    .allowExtra(SmsRetriever.EXTRA_CONSENT_INTENT, Intent::class.java)
                                    .build()
                                    .sanitizeByThrowing(it)

                                smsConsentLauncher.launch(sanitizedIntent)
                            } catch (e: SecurityException) {
                                Toast.makeText(context, "Invalid SMS consent intent", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to launch SMS consent", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    CommonStatusCodes.TIMEOUT -> {
                        Toast.makeText(context, "SMS retrieval timed out", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }



    private fun extractOTPFromMessage(message: String?) {
        message?.let {
            val otpPattern = Regex("\\d{5}")
            val otpMatcher = otpPattern.find(it)
            otpMatcher?.value?.let { otp ->
                for (i in otp.indices) {
                    editTexts[i].setText(otp[i].toString())
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(smsVerificationReceiver, IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(smsVerificationReceiver)
    }
}