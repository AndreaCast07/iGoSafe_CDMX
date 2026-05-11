package com.icm.igosafeapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.*
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.icm.igosafeapp.databinding.ActivityCreateProfileBinding
import com.icm.igosafeapp.manejoArchivos.UsuarioManager

class Create_profile : AppCompatActivity() {

    private lateinit var binding: ActivityCreateProfileBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var usuarioManager: UsuarioManager
    private val auth = FirebaseAuth.getInstance()

    // Lanzadores para Imagen
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { binding.photoPerfil.setImageURI(it) }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        bitmap?.let { binding.photoPerfil.setImageBitmap(it) }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) cameraLauncher.launch(null)
        else Toast.makeText(this, "Se requiere permiso de cámara", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar Manager
        usuarioManager = UsuarioManager()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setupSpinners()
        setupPhoneLogic()
        setupTermsCheckBox()
        manejarBotonAtras()
        verificarDatosGoogle()

        binding.iconCamera.setOnClickListener { mostrarOpcionesImagen() }
        binding.btnCreateProfile.setOnClickListener { validarYContinuar() }
    }

    private fun manejarBotonAtras() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (intent.getBooleanExtra("IS_GOOGLE", false)) {
                    auth.signOut()
                    googleSignInClient.signOut()
                }
                finish()
            }
        })
    }

    private fun verificarDatosGoogle() {
        if (intent.getBooleanExtra("IS_GOOGLE", false)) {
            binding.editTxtName.setText(intent.getStringExtra("NOMBRE"))
            binding.editTextTextEmailAddress.setText(intent.getStringExtra("EMAIL"))
            binding.editTextTextEmailAddress.isEnabled = false
        }
    }

    private fun setupSpinners() {
        val generos = arrayOf("Selecciona Género", "Femenino", "Masculino", "No binario", "Otro")
        binding.spinnerGenero.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, generos)

        val nacionalidades = arrayOf("Selecciona Nacionalidad", "Mexicana", "Estadounidense", "Española", "Colombiana", "Otra")
        binding.spinnerNacionalidad.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, nacionalidades)

        binding.spinnerNacionalidad.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                val prefijo = when (position) {
                    1 -> "+52"
                    2 -> "+1"
                    3 -> "+34"
                    4 -> "+57"
                    5 -> "+"
                    else -> ""
                }
                binding.editCountryCode.setText(prefijo)
                binding.editCountryCode.isEnabled = (position == 5)
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun setupPhoneLogic() {
        binding.editCountryCode.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val prefijo = s.toString()
                if (prefijo.isNotEmpty() && !prefijo.startsWith("+")) {
                    binding.editCountryCode.setText("+$prefijo")
                    binding.editCountryCode.setSelection(binding.editCountryCode.length())
                    return
                }
                val flag = when (prefijo) {
                    "+52" -> R.drawable.mx
                    "+1"  -> R.drawable.us
                    "+34" -> R.drawable.es
                    "+57" -> R.drawable.co
                    else  -> R.drawable.row
                }
                binding.imgFlag.setImageResource(flag)
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })
    }

    private fun setupTermsCheckBox() {
        val fullText = "Acepto los términos y condiciones de iGoSafe"
        val clickablePart = "términos y condiciones"
        val spannableString = SpannableString(fullText)
        val startIndex = fullText.indexOf(clickablePart)
        val endIndex = startIndex + clickablePart.length

        spannableString.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(this, R.color.verde)),
            startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        spannableString.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    startActivity(Intent(this@Create_profile, TerminosActivity::class.java))
                }
                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = true
                }
            },
            startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        binding.cbTerminos.text = spannableString
        binding.cbTerminos.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun mostrarOpcionesImagen() {
        val opciones = arrayOf("Cámara", "Galería")
        AlertDialog.Builder(this)
            .setTitle("Foto de perfil")
            .setItems(opciones) { _, which ->
                if (which == 0) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        cameraLauncher.launch(null)
                    } else {
                        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                } else {
                    galleryLauncher.launch("image/*")
                }
            }.show()
    }

    private fun validarYContinuar() {
        val nombre = binding.editTxtName.text.toString().trim()
        val email = binding.editTextTextEmailAddress.text.toString().trim()
        val prefijo = binding.editCountryCode.text.toString().trim()
        val celular = binding.editTxtCelular.text.toString().trim()
        val edadStr = binding.editTxtEdad.text.toString().trim()

        if (nombre.isEmpty() || email.isEmpty() || celular.isEmpty() || edadStr.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        if (!email.contains("@") || !email.endsWith(".com")) {
            Toast.makeText(this, "Correo no válido", Toast.LENGTH_SHORT).show()
            return
        }

        if (!binding.cbTerminos.isChecked) {
            Toast.makeText(this, "Acepta los términos y condiciones", Toast.LENGTH_SHORT).show()
            return
        }

        val telefonoCompleto = prefijo + celular

        // BLOQUEO DE DUPLICADOS: Antes de ir a SMS, verificamos en Firebase
        binding.btnCreateProfile.isEnabled = false
        binding.btnCreateProfile.text = "Validando número..."

        usuarioManager.verificarCelularDisponible(telefonoCompleto) { disponible ->
            runOnUiThread {
                if (disponible) {
                    // El número es nuevo, pasamos a SMS
                    val intentSms = Intent(this@Create_profile, SignInValidateSms::class.java).apply {
                        putExtra("NOMBRE", nombre)
                        putExtra("EMAIL", email)
                        putExtra("CELULAR", telefonoCompleto)
                        putExtra("GENERO", binding.spinnerGenero.selectedItem.toString())
                        putExtra("NACIONALIDAD", binding.spinnerNacionalidad.selectedItem.toString())
                        putExtra("EDAD", edadStr.toIntOrNull() ?: 0)
                        putExtra("IS_GOOGLE", intent.getBooleanExtra("IS_GOOGLE", false))
                    }
                    startActivity(intentSms)
                    // Restablecemos por si el usuario regresa
                    binding.btnCreateProfile.isEnabled = true
                    binding.btnCreateProfile.text = "Continuar"
                } else {
                    // El número ya existe, detenemos el proceso
                    Toast.makeText(this@Create_profile, "Este número de celular ya está registrado en iGoSafe", Toast.LENGTH_LONG).show()
                    binding.btnCreateProfile.isEnabled = true
                    binding.btnCreateProfile.text = "Continuar"
                }
            }
        }
    }
}