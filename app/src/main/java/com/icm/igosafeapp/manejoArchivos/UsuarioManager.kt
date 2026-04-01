package com.icm.igosafeapp.manejoArchivos

import android.content.Context
import android.net.Uri
import android.provider.Settings
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

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference.child("usuarios")
    private val storage = FirebaseStorage.getInstance().reference

    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    fun dispositivoVinculado(onResultado: (Boolean) -> Unit) {
        database.orderByChild("deviceId").equalTo(deviceId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    onResultado(snapshot.exists())
                }
                override fun onCancelled(error: DatabaseError) {
                    onResultado(false)
                }
            })
    }

    fun registrarUsuario(celular: String, contrasena: String, fotoUri: Uri?, datosUsuario: DatosUsuario, onResultado: (Boolean, String?) -> Unit) {
        dispositivoVinculado { yaTieneCuenta ->
            if (yaTieneCuenta) {
                onResultado(false, "Este dispositivo ya tiene una cuenta asociada.")
                return@dispositivoVinculado
            }

            val emailFicticio = "${celular.trim()}@myapp.com"
            auth.createUserWithEmailAndPassword(emailFicticio, contrasena)
                .addOnSuccessListener { authResult ->
                    val userId = authResult.user?.uid ?: return@addOnSuccessListener onResultado(false, "Error de sistema")
                    procesarGuardado(userId, celular, emailFicticio, datosUsuario, fotoUri, onResultado)
                }
                .addOnFailureListener { e -> onResultado(false, e.message) }
        }
    }

    fun completarRegistroGoogle(celular: String, fotoUri: Uri?, datosUsuario: DatosUsuario, onResultado: (Boolean, String?) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onResultado(false, "No hay sesión activa")
        
        dispositivoVinculado { yaTieneCuenta ->
            if (yaTieneCuenta) {
                onResultado(false, "Este dispositivo ya tiene una cuenta asociada.")
                return@dispositivoVinculado
            }
            val email = auth.currentUser?.email ?: ""
            procesarGuardado(userId, celular, email, datosUsuario, fotoUri, onResultado)
        }
    }

    private fun procesarGuardado(userId: String, celular: String, email: String, datosUsuario: DatosUsuario, fotoUri: Uri?, onResultado: (Boolean, String?) -> Unit) {
        if (fotoUri != null) {
            subirFotoPerfil(fotoUri) { fotoUrl ->
                guardarDatosUsuario(userId, celular, email, datosUsuario, fotoUrl ?: "", onResultado)
            }
        } else {
            guardarDatosUsuario(userId, celular, email, datosUsuario, "", onResultado)
        }
    }

    private fun guardarDatosUsuario(userId: String, celular: String, email: String, datosUsuario: DatosUsuario, fotoUrl: String, onResultado: (Boolean, String?) -> Unit) {
        val usuarioData = mapOf(
            "datosUsuario" to mapOf(
                "nombre" to datosUsuario.nombre,
                "genero" to datosUsuario.genero,
                "edad" to datosUsuario.edad,
                "nacionalidad" to datosUsuario.nacionalidad
            ),
            "celular" to celular,
            "email" to email,
            "fotoPerfilUrl" to fotoUrl,
            "deviceId" to deviceId,
            "status" to "ACTIVO",
        )

        database.child(userId).setValue(usuarioData)
            .addOnSuccessListener { onResultado(true, null) }
            .addOnFailureListener { e -> onResultado(false, e.message) }
    }

    private fun subirFotoPerfil(uri: Uri, onResultado: (String?) -> Unit) {
        val storageReference = storage.child("perfilFotos/${UUID.randomUUID()}.jpg")
        storageReference.putFile(uri)
            .addOnSuccessListener { it.metadata?.reference?.downloadUrl?.addOnSuccessListener { url -> onResultado(url.toString()) } }
            .addOnFailureListener { onResultado(null) }
    }

    fun iniciarSesion(celular: String, contrasena: String, onResultado: (Boolean) -> Unit) {
        val emailFicticio = "${celular.trim()}@myapp.com"
        auth.signInWithEmailAndPassword(emailFicticio, contrasena)
            .addOnSuccessListener { onResultado(true) }
            .addOnFailureListener { onResultado(false) }
    }

    fun celularRegistrado(celular: String, onResultado: (Boolean) -> Unit) {
        database.orderByChild("celular").equalTo(celular.trim())
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) { onResultado(snapshot.exists()) }
                override fun onCancelled(error: DatabaseError) { onResultado(false) }
            })
    }

    fun usuarioExiste(uid: String, onResultado: (Boolean) -> Unit) {
        Log.d("UsuarioExiste", "Buscando datos para el UID: $uid")
        database.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val existe = snapshot.exists()
                Log.d("UsuarioExiste", "Resultado para $uid: $existe")
                onResultado(existe)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("UsuarioExiste", "Error de Firebase: ${error.message}")
                onResultado(false)
            }
        })
    }

    fun cerrarSesion() { auth.signOut() }
}
