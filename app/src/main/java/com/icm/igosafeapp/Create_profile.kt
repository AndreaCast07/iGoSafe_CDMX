package com.icm.igosafeapp


import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView

import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Create_profile : AppCompatActivity() {
    private lateinit var txtNombre: EditText
    private lateinit var txtNumDocumento: EditText
    private lateinit var spinnerDocumento: Spinner
    private lateinit var btnCrearPerfil: Button

    private lateinit var photoPerfil: ImageView
    private lateinit var iconCamera: ImageView
    private var pickedPhoto: Uri? = null
    private var pickedBitMap: Bitmap? = null
    private lateinit var currentPhotoPath: String

    private lateinit var photoFile: File
    private val FILE_NAME = "profile_photo.jpg"
    private val CAMERA_REQUEST_CODE = 42
    private lateinit var imageUrl: Uri

    private val cameraContract = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoPerfil.setImageURI(null) // Clear the existing image
            photoPerfil.setImageURI(imageUrl) // Set the new image from the Uri
        } else {
            Toast.makeText(this, "Picture not taken", Toast.LENGTH_SHORT).show()
        }
    }
    companion object {
        private const val PERMISSION_REQUEST_CODE = 10
        private const val GALLERY_REQUEST_CODE = 2
        private const val CAMERA_REQUEST_CODE = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_profile)

        txtNombre = findViewById(R.id.editTxtName)
        txtNumDocumento = findViewById(R.id.editTxtDocumento)
        photoPerfil = findViewById(R.id.photoPerfil)
        iconCamera = findViewById(R.id.iconCamera)
        spinnerDocumento = findViewById(R.id.selectTipoDocumento)
        btnCrearPerfil = findViewById(R.id.btnCreateProfile)

        cargarDatosSpinner()
        setupImageClickListeners()
        imageUrl = createImageUri()
        mostrarLayoutCreatePasword()

    }

    private fun cargarDatosSpinner() {
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.opcionesDocumentos,
            android.R.layout.simple_spinner_item
        )

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDocumento.adapter = adapter

        spinnerDocumento.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position == 0) {
                    (view as? TextView)?.setTextColor(ContextCompat.getColor(parent.context, android.R.color.darker_gray))
                } else {
                    (view as? TextView)?.setTextColor(ContextCompat.getColor(parent.context, R.color.black))
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun mostrarLayoutCreatePasword() {
        btnCrearPerfil.setOnClickListener {
            if (validarCampos()) {
                val celular = intent.getStringExtra("CELULAR") ?: ""
                val nombre = txtNombre.text.toString()
                val tipoDocumento = spinnerDocumento.selectedItem.toString()
                val numDocumento = txtNumDocumento.text.toString()

                val intent = Intent(this, CreatePassword::class.java).apply {
                    putExtra("CELULAR", celular)
                    putExtra("NOMBRE", nombre)
                    putExtra("TIPO_DOCUMENTO", tipoDocumento)
                    putExtra("NUM_DOCUMENTO", numDocumento)

                    // Si no hay foto seleccionada, enviamos null.
                    putExtra("FOTO_URI", imageUrl?.toString())
                }

                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Por favor, completa todos los campos.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validarCampos(): Boolean {
        return txtNombre.text.isNotEmpty() &&
                txtNumDocumento.text.isNotEmpty() &&
                spinnerDocumento.selectedItemPosition != 0
    }



    private fun setupImageClickListeners() {
        photoPerfil.setOnClickListener {
            if (checkAndRequestPermissions()) {
                openGallery()
            }
        }

        iconCamera.setOnClickListener {
            if (checkAndRequestPermissions() ) {
                if (hasCameraHardware()) {
                    Log.d("CameraDebug", "Device has camera hardware")
                    cameraContract.launch(imageUrl)
                } else {
                    Log.e("CameraDebug", "Device does not have camera hardware")
                }
            }
        }
    }


    private fun checkAndRequestPermissions(): Boolean {
        val permissions = arrayOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.CAMERA
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE)
            return false
        }
        return true
    }



    private fun openGallery() {
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(galleryIntent, GALLERY_REQUEST_CODE)
    }

    private fun hasCameraHardware(): Boolean {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    private fun createImageUri(): Uri {
        val image = File(filesDir, FILE_NAME)
        return FileProvider.getUriForFile(
            this,
            "com.icm.igosafeapp.fileprovider",
            image
        )
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File = getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        return File.createTempFile(
            "JPEG_${timeStamp}_", // Prefix
            ".jpg", // Suffix
            storageDir // Directory
        ).apply {
            currentPhotoPath = absolutePath
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            // Permissions granted, you can proceed with camera or gallery operations
            if (permissions.contains(android.Manifest.permission.CAMERA)) {
                if (hasCameraHardware()) {
                    Log.d("CameraDebug", "Device has camera hardware")
                    cameraContract.launch(imageUrl)
                } else {
                    Log.e("CameraDebug", "Device does not have camera hardware")
                }
            } else if (permissions.contains(android.Manifest.permission.READ_EXTERNAL_STORAGE)) {
                openGallery()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val takenImage = BitmapFactory.decodeFile(photoFile.absolutePath)
            photoPerfil.setImageBitmap(takenImage)
        }
    }

    private fun getPhotoFile(fileName: String): File {
        val storageDirectory = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(fileName, ".jpg", storageDirectory)
    }

    private fun handleGalleryResult(data: Intent?) {
        pickedPhoto = data?.data
        pickedPhoto?.let { uri ->
            pickedBitMap = if (Build.VERSION.SDK_INT >= 28) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
            photoPerfil.setImageBitmap(pickedBitMap)
        }
    }

    private fun handleCameraResult() {
        val file = File(currentPhotoPath)
        pickedPhoto = FileProvider.getUriForFile(
            this,
            "com.icm.igosafeapp.fileprovider",
            file
        )
        pickedBitMap = if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(contentResolver, pickedPhoto!!)
            ImageDecoder.decodeBitmap(source)
        } else {
            MediaStore.Images.Media.getBitmap(contentResolver, pickedPhoto)
        }
        photoPerfil.setImageBitmap(pickedBitMap)
    }

}
