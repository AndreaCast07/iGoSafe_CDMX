package com.icm.igosafeapp

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.icm.igosafeapp.manejoArchivos.UsuarioManager
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso
import entidades.DatosUsuario
import java.io.File

class Create_profile : AppCompatActivity() {

    private lateinit var txtNombre: EditText
    private lateinit var txtCelular: EditText
    private lateinit var txtEdad: EditText
    private lateinit var txtEmail: EditText
    private lateinit var spinnerGenero: Spinner
    private lateinit var spinnerNacionalidad: Spinner
    private lateinit var btnCrearPerfil: Button
    private lateinit var cbTerminos: CheckBox

    private lateinit var photoPerfil: ImageView
    private lateinit var iconCamera: ImageView
    private var pickedPhoto: Uri? = null
    private val FILE_NAME = "profile_photo.jpg"
    private lateinit var imageUrl: Uri

    private lateinit var usuarioManager: UsuarioManager
    private var isGoogleFlow: Boolean = false

    // Contratos para Cámara y Galería
    private val cameraContract = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pickedPhoto = imageUrl
            actualizarFotoEnVista(imageUrl)
        }
    }

    private val galleryContract = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            pickedPhoto = it
            actualizarFotoEnVista(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_profile)

        usuarioManager = UsuarioManager(this)
        isGoogleFlow = intent.getBooleanExtra("IS_GOOGLE", false)

        // Inicializar vistas[cite: 2, 22]
        txtNombre = findViewById(R.id.editTxtName)
        txtCelular = findViewById(R.id.editTxtCelular)
        txtEdad = findViewById(R.id.editTxtEdad)
        txtEmail = findViewById(R.id.editTextTextEmailAddress)
        spinnerGenero = findViewById(R.id.spinnerGenero)
        spinnerNacionalidad = findViewById(R.id.spinnerNacionalidad)
        photoPerfil = findViewById(R.id.photoPerfil)
        iconCamera = findViewById(R.id.iconCamera)
        btnCrearPerfil = findViewById(R.id.btnCreateProfile)
        cbTerminos = findViewById(R.id.cbTerminos)

        val celularSms = intent.getStringExtra("CELULAR") ?: ""
        if (celularSms.isNotEmpty()) {
            txtCelular.setText(celularSms)
            txtCelular.isEnabled = false
        }

        configurarSpinners()
        setupImageClickListeners()
        setupTerminosClickable()
        imageUrl = createImageUri()
        configurarBotonCrear()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val intent = Intent(this@Create_profile, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        })
    }

    // Muestra diálogo para elegir origen de imagen[cite: 22]
    private fun mostrarOpcionesImagen() {
        val opciones = arrayOf("Cámara", "Galería")
        AlertDialog.Builder(this)
            .setTitle("Seleccionar foto de perfil")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> if (checkAndRequestPermissions("CAMERA")) cameraContract.launch(imageUrl)
                    1 -> if (checkAndRequestPermissions("GALLERY")) galleryContract.launch("image/*")
                }
            }
            .show()
    }

    private fun setupImageClickListeners() {
        photoPerfil.setOnClickListener { mostrarOpcionesImagen() }
        iconCamera.setOnClickListener { mostrarOpcionesImagen() }
    }

    private fun setupTerminosClickable() {
        val texto = "Acepto los términos y condiciones"
        val spannable = SpannableString(texto)
        val linkText = "términos y condiciones"

        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                // Ir a la actividad de términos[cite: 22]
                val intent = Intent(this@Create_profile, TerminosActivity::class.java)
                startActivity(intent)
            }
            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = Color.BLUE
                ds.isUnderlineText = true
            }
        }

        val start = texto.indexOf(linkText)
        val end = start + linkText.length
        spannable.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        cbTerminos.text = spannable
        cbTerminos.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun configurarBotonCrear() {
        btnCrearPerfil.setOnClickListener {
            val nombre = txtNombre.text.toString().trim()
            val edadStr = txtEdad.text.toString().trim()
            val email = txtEmail.text.toString().trim()
            val celular = txtCelular.text.toString().trim()

            // Validaciones obligatorias[cite: 22]
            if (nombre.isEmpty() || edadStr.isEmpty() || email.isEmpty() || celular.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!cbTerminos.isChecked) {
                Toast.makeText(this, "Debes aceptar los términos y condiciones", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnCrearPerfil.isEnabled = false
            btnCrearPerfil.text = "Procesando..."

            usuarioManager.celularYaRegistrado(celular) { existe ->
                runOnUiThread {
                    if (existe && !isGoogleFlow) {
                        Toast.makeText(this@Create_profile, "Número ya registrado", Toast.LENGTH_LONG).show()
                        btnCrearPerfil.isEnabled = true
                        btnCrearPerfil.text = "Crear perfil"
                    } else {
                        continuarRegistro(nombre, edadStr.toIntOrNull() ?: 0,
                            spinnerGenero.selectedItem.toString(),
                            spinnerNacionalidad.selectedItem.toString(), celular, email)
                    }
                }
            }
        }
    }

    private fun actualizarFotoEnVista(uri: Uri) {
        val rotation = getRotationAngle(uri)
        Picasso.get()
            .load(uri)
            .rotate(rotation.toFloat())
            .memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE)
            .placeholder(R.drawable.photo_original_user)
            .into(photoPerfil)
    }

    private fun continuarRegistro(nombre: String, edad: Int, genero: String, nacionalidad: String, celular: String, email: String) {
        val datos = DatosUsuario(nombre, genero, edad, nacionalidad)
        if (isGoogleFlow) {
            usuarioManager.completarRegistroGoogle(celular, pickedPhoto, datos) { exito, _ ->
                if (exito) startActivity(Intent(this, NavigationActivity::class.java)).also { finish() }
            }
        } else {
            val intent = Intent(this, SignInValidateSms::class.java).apply {
                putExtra("CELULAR", celular)
                putExtra("NOMBRE", nombre)
                putExtra("EMAIL", email)
                putExtra("GENERO", genero)
                putExtra("EDAD", edad)
                putExtra("NACIONALIDAD", nacionalidad)
                putExtra("FOTO_URI", pickedPhoto?.toString() ?: "")
            }
            startActivity(intent)
            finish()
        }
    }

    // Funciones de utilidad (Permisos, Rotación, URI) se mantienen[cite: 22]
    private fun getRotationAngle(uri: Uri): Int {
        return try { contentResolver.openInputStream(uri)?.use {
            val exif = ExifInterface(it)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0 } catch (e: Exception) { 0 }
    }

    private fun checkAndRequestPermissions(type: String): Boolean {
        val permissions = if (type == "CAMERA") arrayOf(android.Manifest.permission.CAMERA)
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES)
        else arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)

        val toRequest = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), 10)
            return false
        }
        return true
    }

    private fun createImageUri(): Uri {
        val image = File(filesDir, FILE_NAME)
        return FileProvider.getUriForFile(this, "com.icm.igosafeapp.fileprovider", image)
    }

    private fun configurarSpinners() {
        val adapterGen = ArrayAdapter.createFromResource(this, R.array.opcionesGenero, android.R.layout.simple_spinner_item)
        adapterGen.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGenero.adapter = adapterGen

        val adapterNac = ArrayAdapter.createFromResource(this, R.array.opcionesNacionalidad, android.R.layout.simple_spinner_item)
        adapterNac.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerNacionalidad.adapter = adapterNac
    }
}