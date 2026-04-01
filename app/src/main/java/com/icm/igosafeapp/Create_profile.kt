package com.icm.igosafeapp

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
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
    private lateinit var spinnerGenero: Spinner
    private lateinit var spinnerNacionalidad: Spinner
    private lateinit var btnCrearPerfil: Button

    private lateinit var photoPerfil: ImageView
    private lateinit var iconCamera: ImageView
    private var pickedPhoto: Uri? = null
    private val FILE_NAME = "profile_photo.jpg"
    private lateinit var imageUrl: Uri
    
    private lateinit var usuarioManager: UsuarioManager
    private var isGoogleFlow: Boolean = false

    private val cameraContract = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pickedPhoto = imageUrl
            val rotation = getRotationAngle(imageUrl)
            Picasso.get()
                .load(imageUrl)
                .rotate(rotation.toFloat())
                .memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE)
                .networkPolicy(NetworkPolicy.NO_CACHE)
                .placeholder(R.drawable.photo_original_user)
                .into(photoPerfil)
        }
    }

    private val galleryContract = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            pickedPhoto = it
            val rotation = getRotationAngle(it)
            Picasso.get()
                .load(it)
                .rotate(rotation.toFloat())
                .placeholder(R.drawable.photo_original_user)
                .into(photoPerfil)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_profile)

        usuarioManager = UsuarioManager(this)
        isGoogleFlow = intent.getBooleanExtra("IS_GOOGLE", false)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val intent = Intent(this@Create_profile, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        })

        txtNombre = findViewById(R.id.editTxtName)
        txtCelular = findViewById(R.id.editTxtCelular)
        txtEdad = findViewById(R.id.editTxtEdad)
        spinnerGenero = findViewById(R.id.spinnerGenero)
        spinnerNacionalidad = findViewById(R.id.spinnerNacionalidad)
        photoPerfil = findViewById(R.id.photoPerfil)
        iconCamera = findViewById(R.id.iconCamera)
        btnCrearPerfil = findViewById(R.id.btnCreateProfile)

        if (isGoogleFlow) {
            txtNombre.setText(intent.getStringExtra("NOMBRE") ?: "")
            txtCelular.visibility = View.VISIBLE
        }

        configurarSpinners()
        setupImageClickListeners()
        imageUrl = createImageUri()
        configurarBotonCrear()
    }

    private fun configurarSpinners() {
        val adapterGen = ArrayAdapter.createFromResource(this, R.array.opcionesGenero, android.R.layout.simple_spinner_item)
        adapterGen.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGenero.adapter = adapterGen

        val adapterNac = ArrayAdapter.createFromResource(this, R.array.opcionesNacionalidad, android.R.layout.simple_spinner_item)
        adapterNac.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerNacionalidad.adapter = adapterNac
    }

    private fun configurarBotonCrear() {
        btnCrearPerfil.setOnClickListener {
            val nombre = txtNombre.text.toString().trim()
            val edadStr = txtEdad.text.toString().trim()
            val genero = spinnerGenero.selectedItem.toString()
            val nacionalidad = spinnerNacionalidad.selectedItem.toString()
            val celular = if (isGoogleFlow) txtCelular.text.toString().trim() else intent.getStringExtra("CELULAR") ?: ""

            if (nombre.isEmpty() || edadStr.isEmpty() || 
                spinnerGenero.selectedItemPosition == 0 || 
                spinnerNacionalidad.selectedItemPosition == 0 ||
                (isGoogleFlow && celular.isEmpty())) {
                Toast.makeText(this, "Por favor, completa todos los campos.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val nameParts = nombre.split(" ").filter { it.isNotEmpty() }
            if (nameParts.size < 2) {
                Toast.makeText(this, "Por favor, ingresa nombre y apellido.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!nombre.matches("^[a-zA-ZñÑáéíóúÁÉÍÓÚ\\s]+$".toRegex())) {
                Toast.makeText(this, "El nombre solo puede contener letras.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val edad = edadStr.toIntOrNull() ?: 0
            if (edad < 11 || edad > 99) {
                Toast.makeText(this, "La edad debe estar entre 11 y 99 años.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val datos = DatosUsuario(nombre, genero, edad, nacionalidad)
            btnCrearPerfil.isEnabled = false
            btnCrearPerfil.text = "Validando..."

            if (isGoogleFlow) {
                usuarioManager.completarRegistroGoogle(celular, pickedPhoto, datos) { exito, error ->
                    runOnUiThread {
                        if (exito) {
                            startActivity(Intent(this@Create_profile, Menu::class.java))
                            finish()
                        } else {
                            btnCrearPerfil.isEnabled = true
                            btnCrearPerfil.text = "Crear perfil"
                            Toast.makeText(this@Create_profile, error ?: "Error al crear perfil", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                usuarioManager.dispositivoVinculado { yaTiene ->
                    runOnUiThread {
                        if (yaTiene) {
                            Toast.makeText(this@Create_profile, "Este dispositivo ya tiene una cuenta asociada.", Toast.LENGTH_LONG).show()
                            btnCrearPerfil.isEnabled = true
                            btnCrearPerfil.text = "Crear perfil"
                        } else {
                            val intent = Intent(this@Create_profile, CreatePassword::class.java).apply {
                                putExtra("CELULAR", celular)
                                putExtra("NOMBRE", nombre)
                                putExtra("GENERO", genero)
                                putExtra("EDAD", edad)
                                putExtra("NACIONALIDAD", nacionalidad)
                                putExtra("FOTO_URI", pickedPhoto?.toString() ?: "")
                            }
                            startActivity(intent)
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun getRotationAngle(uri: Uri): Int {
        try {
            contentResolver.openInputStream(uri)?.use { 
                val exif = ExifInterface(it)
                return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
        } catch (e: Exception) { Log.e("Create_profile", "EXIF Error", e) }
        return 0
    }

    private fun setupImageClickListeners() {
        photoPerfil.setOnClickListener { if (checkAndRequestPermissions("GALLERY")) galleryContract.launch("image/*") }
        iconCamera.setOnClickListener {
            if (checkAndRequestPermissions("CAMERA")) cameraContract.launch(imageUrl)
        }
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
}
