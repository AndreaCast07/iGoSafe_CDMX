package com.icm.igosafeapp.manejoArchivos

import android.content.Context
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import entidades.Calificacion

class CalificacionesManager(private val context: Context) {

    private val database = FirebaseDatabase.getInstance().reference.child("calificaciones") // Firebase Realtime Database

    // Guardar una calificación
    fun guardarCalificacion(barrio: String, calificacion: Double, onResultado: (Boolean) -> Unit) {
        val calificacionData = Calificacion(barrio, calificacion)

        // Guardar la calificación bajo la referencia "calificaciones/{barrio}"
        database.child(barrio).setValue(calificacionData)
            .addOnSuccessListener {
                Log.d("CalificacionManager", "Calificación guardada correctamente")
                onResultado(true)
            }
            .addOnFailureListener { e ->
                Log.e("CalificacionManager", "Error al guardar calificación: ${e.message}")
                onResultado(false)
            }
    }

    // Obtener todas las calificaciones
    fun obtenerCalificaciones(onResultado: (List<Calificacion>) -> Unit) {
        database.get()
            .addOnSuccessListener { snapshot ->
                val calificaciones = mutableListOf<Calificacion>()
                for (childSnapshot in snapshot.children) {
                    val calificacion = childSnapshot.getValue(Calificacion::class.java)
                    calificacion?.let { calificaciones.add(it) }
                }
                onResultado(calificaciones)
            }
            .addOnFailureListener { e ->
                Log.e("CalificacionManager", "Error al obtener calificaciones: ${e.message}")
                onResultado(emptyList())
            }
    }
}
