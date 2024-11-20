package com.icm.igosafeapp.manejoArchivos

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import entidades.DatosUsuario
import entidades.Usuario
import java.util.UUID


class UsuarioManager(private val context: Context) {

    private val auth = FirebaseAuth.getInstance() // Firebase Authentication
    private val database = FirebaseDatabase.getInstance().reference.child("usuarios") // Firebase Realtime Database
    private val storage = FirebaseStorage.getInstance().reference // Firebase Storage

    // Registrar usuario con foto de perfil
    fun registrarUsuario(celular: String, contrasena: String, fotoUri: Uri?, datosUsuario: DatosUsuario, onResultado: (Boolean) -> Unit) {
        val emailFicticio = "$celular@myapp.com"  // Usar el celular como correo

        // Crear usuario en Firebase Authentication
        auth.createUserWithEmailAndPassword(emailFicticio, contrasena)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                val userId = user?.uid ?: return@addOnSuccessListener onResultado(false)

                // Subir foto de perfil si se proporciona
                if (fotoUri != null) {
                    subirFotoPerfil(fotoUri) { fotoUrl ->
                        guardarDatosUsuario(
                            userId, celular, emailFicticio, datosUsuario, fotoUrl ?: "", onResultado
                        )
                    }
                } else {
                    guardarDatosUsuario(userId, celular, emailFicticio, datosUsuario, "", onResultado)
                }
            }
            .addOnFailureListener { e ->
                Log.e("UsuarioManager", "Error al crear usuario: ${e.message}")
                onResultado(false)
            }
    }
    private fun guardarDatosUsuario(
        userId: String,
        celular: String,
        email: String,
        datosUsuario: DatosUsuario,
        fotoUrl: String,
        onResultado: (Boolean) -> Unit
    ) {
        val usuarioData = mapOf(
            "datosUsuario" to mapOf(
                "nombre" to datosUsuario.nombre,
                "tipoDocumento" to datosUsuario.tipoDocumento,
                "numDocumento" to datosUsuario.documento
            ),
            "celular" to celular,
            "email" to email,
            "fotoPerfilUrl" to fotoUrl,
            "status" to "INACTIVO",
        )

        database.child(userId).setValue(usuarioData)
            .addOnSuccessListener {
                Log.d("UsuarioManager", "Usuario registrado correctamente")
                onResultado(true)
            }
            .addOnFailureListener { e ->
                Log.e("UsuarioManager", "Error al guardar datos en Firebase: ${e.message}")
                onResultado(false)
            }
    }

    // Subir la foto de perfil a Firebase Storage
    private fun subirFotoPerfil(uri: Uri, onResultado: (String?) -> Unit) {
        val storageReference = storage.child("perfilFotos/${UUID.randomUUID()}.jpg")
        storageReference.putFile(uri)
            .addOnSuccessListener { taskSnapshot ->
                taskSnapshot.metadata?.reference?.downloadUrl?.addOnSuccessListener { url ->
                    // Retornar la URL de la foto subida
                    onResultado(url.toString())
                }
            }
            .addOnFailureListener { e ->
                Log.e("UsuarioManager", "Error al subir foto de perfil: ${e.message}")
                onResultado(null)
            }
    }

    // Iniciar sesión con correo/contraseña
    fun iniciarSesion(celular: String, contrasena: String, onResultado: (Boolean) -> Unit) {
        val emailFicticio = "$celular@myapp.com"  // Usar el celular como correo

        auth.signInWithEmailAndPassword(emailFicticio, contrasena)
            .addOnSuccessListener {
                Log.d("UsuarioManager", "Inicio de sesión exitoso")
                onResultado(true)
            }
            .addOnFailureListener { e ->
                Log.e("UsuarioManager", "Error al iniciar sesión: ${e.message}")
                onResultado(false)
            }
    }

    // Verificar si el celular está registrado (usando el correo ficticio)
    fun celularRegistrado(celular: String, onResultado: (Boolean) -> Unit) {
        val emailFicticio = "$celular@myapp.com"  // Usar el celular como correo

        database.orderByChild("email").equalTo(emailFicticio)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    onResultado(snapshot.exists()) // True si el usuario existe
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("UsuarioManager", "Error al verificar celular: ${error.message}")
                    onResultado(false)
                }
            })
    }

    // Obtener datos del usuario logueado
    fun obtenerUsuarioActual(onResultado: (Usuario?) -> Unit) {
        val user = auth.currentUser
        if (user != null) {
            val userId = user.uid
            database.child(userId).get()
                .addOnSuccessListener { snapshot ->
                    val usuario = snapshot.getValue(Usuario::class.java)
                    onResultado(usuario)
                }
                .addOnFailureListener { e ->
                    Log.e("UsuarioManager", "Error al obtener usuario: ${e.message}")
                    onResultado(null)
                }
        } else {
            onResultado(null)
        }
    }

    // Cerrar sesión
    fun cerrarSesion() {
        auth.signOut()
        Log.d("UsuarioManager", "Sesión cerrada exitosamente")
    }
}

