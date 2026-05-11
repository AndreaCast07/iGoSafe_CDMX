package com.icm.igosafeapp.manejoArchivos

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import entidades.DatosUsuario

class UsuarioManager {

    private val auth = FirebaseAuth.getInstance()
    private val rootRef = FirebaseDatabase.getInstance().reference
    private val usuariosRef = rootRef.child("usuarios")

    fun verificarUsuarioPorUID(uid: String, callback: (Boolean) -> Unit) {
        usuariosRef.child(uid).get().addOnSuccessListener { snapshot ->
            val existe = snapshot.exists()
            callback(existe)
        }.addOnFailureListener { callback(false) }
    }

    /**
     * Consulta el nodo 'celulares_registrados' para ver si el número ya existe.
     * @return true si está disponible, false si ya está registrado.
     */
    fun verificarCelularDisponible(celular: String, callback: (Boolean) -> Unit) {
        val celularFiltro = celular.replace(Regex("[^0-9]"), "").takeLast(10)

        rootRef.child("celulares_registrados").child(celularFiltro).get()
            .addOnSuccessListener { snapshot ->
                // Si el snapshot NO existe, el celular está disponible
                callback(!snapshot.exists())
            }
            .addOnFailureListener {
                Log.e("iGoSafe_Error", "Error al verificar disponibilidad: ${it.message}")
                callback(false)
            }
    }

    fun buscarUsuarioPorEmail(email: String, callback: (Boolean, String?) -> Unit) {
        val emailLimpio = email.lowercase().trim()
        usuariosRef.orderByChild("email").equalTo(emailLimpio).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val uidOriginal = snapshot.children.first().key
                callback(true, uidOriginal)
            } else {
                callback(false, null)
            }
        }.addOnFailureListener { callback(false, null) }
    }

    fun registrarUsuarioCompleto(datos: DatosUsuario, celular: String, callback: (Boolean, String?) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val celularFiltro = celular.replace(Regex("[^0-9]"), "").takeLast(10)

        val infoUsuario = mutableMapOf(
            "nombre" to datos.nombre,
            "email" to datos.email?.lowercase()?.trim(),
            "genero" to datos.genero,
            "edad" to datos.edad,
            "nacionalidad" to datos.nacionalidad,
            "celular" to celular,
            "celularFiltro" to celularFiltro,
            "fotoPerfilUrl" to (datos.fotoPerfilUrl ?: "")
        )

        val childUpdates = hashMapOf<String, Any>(
            "/usuarios/$uid" to infoUsuario,
            "/celulares_registrados/$celularFiltro" to uid
        )

        rootRef.updateChildren(childUpdates)
            .addOnSuccessListener { callback(true, null) }
            .addOnFailureListener { exception -> callback(false, exception.localizedMessage) }
    }
}