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
import java.util.UUID

class UsuarioManager(private val context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference.child("usuarios")
    private val storage = FirebaseStorage.getInstance().reference

    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    // Ya no lo usamos para bloquear, pero lo dejamos por si alguna otra parte del código lo llama
    fun dispositivoVinculado(onResultado: (Boolean) -> Unit) {
        onResultado(false) // Forzamos que siempre diga que NO está vinculado para no bloquear
    }

    fun registrarUsuario(
        celular: String,
        contrasena: String,
        email: String,
        fotoUri: Uri?,
        datosUsuario: DatosUsuario,
        onResultado: (Boolean, String?) -> Unit
    ) {
        // ELIMINADO: Ya no llamamos a dispositivoVinculado aquí
        auth.createUserWithEmailAndPassword(email, contrasena)
            .addOnSuccessListener { authResult ->
                val userId = authResult.user?.uid ?: return@addOnSuccessListener onResultado(false, "Error de sistema")
                procesarGuardado(userId, celular, email, datosUsuario, fotoUri, onResultado)
            }
            .addOnFailureListener { e -> onResultado(false, e.message) }
    }

    fun completarRegistroGoogle(celular: String, fotoUri: Uri?, datosUsuario: DatosUsuario, onResultado: (Boolean, String?) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onResultado(false, "No hay sesión activa")

        // ELIMINADO: Bloqueo de dispositivo quitado para permitir múltiples cuentas
        val email = auth.currentUser?.email ?: ""
        procesarGuardado(userId, celular, email, datosUsuario, fotoUri, onResultado)
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
        // Seguimos guardando el deviceId como información, pero ya no es un filtro
        val usuarioData = mapOf(
            "nombre" to datosUsuario.nombre,
            "genero" to datosUsuario.genero,
            "edad" to datosUsuario.edad,
            "nacionalidad" to datosUsuario.nacionalidad,
            "celular" to celular,
            "email" to email,
            "fotoPerfilUrl" to fotoUrl,
            "deviceId" to deviceId,
            "status" to "ACTIVO"
        )

        database.child(userId).setValue(usuarioData)
            .addOnSuccessListener { onResultado(true, null) }
            .addOnFailureListener { e -> onResultado(false, e.message) }
    }

    // ... (El resto de tus funciones de subirFoto e iniciarSesion se mantienen igual)

    private fun subirFotoPerfil(uri: Uri, onResultado: (String?) -> Unit) {
        val storageReference = storage.child("perfilFotos/${UUID.randomUUID()}.jpg")
        storageReference.putFile(uri)
            .addOnSuccessListener { task ->
                task.metadata?.reference?.downloadUrl?.addOnSuccessListener { url ->
                    onResultado(url.toString())
                }
            }
            .addOnFailureListener { onResultado(null) }
    }

    fun iniciarSesion(celular: String, contrasena: String, onResultado: (Boolean) -> Unit) {
        database.orderByChild("celular").equalTo(celular.trim()).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val userSnapshot = snapshot.children.first()
                    val emailReal = userSnapshot.child("email").value.toString()

                    auth.signInWithEmailAndPassword(emailReal, contrasena)
                        .addOnSuccessListener { onResultado(true) }
                        .addOnFailureListener { onResultado(false) }
                } else {
                    onResultado(false)
                }
            }
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
        database.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { onResultado(snapshot.exists()) }
            override fun onCancelled(error: DatabaseError) { onResultado(false) }
        })
    }

    fun cerrarSesion() { auth.signOut() }

    fun celularYaRegistrado(celular: String, callback: (Boolean) -> Unit) {
        database.orderByChild("celular").equalTo(celular).get()
            .addOnSuccessListener { snapshot -> callback(snapshot.exists()) }
            .addOnFailureListener { callback(false) }
    }
}