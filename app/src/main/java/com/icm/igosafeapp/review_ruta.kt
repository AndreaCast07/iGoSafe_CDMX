package com.icm.igosafeapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.RatingBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class review_ruta : AppCompatActivity() {

    private var ratingGlobal: Float = 0f
    private var ratingInfra: Float = 0f
    private var ratingVitalidad: Float = 0f
    private var ratingEntorno: Float = 0f
    
    private var userWeight: Double = 1.0
    private var userProfileType: String = "Estándar"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review_ruta)
        cargarPerfilUsuario()
        showVentana()
    }

    private fun cargarPerfilUsuario() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().getReference("usuarios").child(userId).get()
            .addOnSuccessListener { snapshot ->
                val genero = snapshot.child("genero").value?.toString() ?: ""
                val nacionalidad = snapshot.child("nacionalidad").value?.toString() ?: ""
                
                val esMexicano = nacionalidad.equals("México", ignoreCase = true)
                val esMujer = genero.equals("Femenino", ignoreCase = true)

                // Aplicar lógica de ponderación estratégica
                when {
                    esMujer && esMexicano -> {
                        userWeight = 1.5
                        userProfileType = "Mujer Local (Estándar de Oro)"
                    }
                    esMexicano -> {
                        userWeight = 1.2
                        userProfileType = "Nacional (Sensor de Peligro)"
                    }
                    else -> {
                        userWeight = 0.8
                        userProfileType = "Extranjero (Sensor de Calidad)"
                    }
                }
                Log.d("ReviewRuta", "Perfil detectado: $userProfileType con peso $userWeight")
            }
    }

    fun showVentana() {
        val closeButton: Button = findViewById(R.id.cerrarReview)
        val rbGlobal: RatingBar = findViewById(R.id.ratingRutaSegura)
        val rbInfra: RatingBar = findViewById(R.id.ratingInfraestructura)
        val rbVitalidad: RatingBar = findViewById(R.id.ratingVitalidad)
        val rbEntorno: RatingBar = findViewById(R.id.ratingEntorno)

        val barriosPorRuta = intent.getStringArrayListExtra("barriosPorRuta")

        closeButton.setOnClickListener {
            ratingGlobal = rbGlobal.rating
            ratingInfra = rbInfra.rating
            ratingVitalidad = rbVitalidad.rating
            ratingEntorno = rbEntorno.rating

            if (ratingGlobal > 0 && ratingInfra > 0 && ratingVitalidad > 0 && ratingEntorno > 0) {
                // Guardar la reseña multidimensional
                guardarResenaEnFirebase()

                if (barriosPorRuta != null && barriosPorRuta.isNotEmpty()) {
                    // La calificación que afecta al mapa se pondera por el perfil
                    val weightedRating = (ratingGlobal * userWeight).toFloat().coerceAtMost(5.0f)
                    actualizarCalificaciones(barriosPorRuta, weightedRating) {
                        irAMenuPrincipal()
                    }
                } else {
                    irAMenuPrincipal()
                }
            } else {
                Toast.makeText(this, "Por favor, completa todas las calificaciones de la encuesta.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun guardarResenaEnFirebase() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "desconocido"
        val ref = FirebaseDatabase.getInstance().getReference("resenas_viajes").child(userId).push()
        
        val resenaData = mapOf(
            "puntuacion_global" to ratingGlobal,
            "infraestructura" to ratingInfra,
            "vitalidad" to ratingVitalidad,
            "entorno_fisico" to ratingEntorno,
            "perfil_weight" to userWeight,
            "perfil_tipo" to userProfileType,
            "fecha" to System.currentTimeMillis()
        )
        
        ref.setValue(resenaData).addOnFailureListener {
            Log.e("ReviewRuta", "Error al guardar reseña: ${it.message}")
        }
    }

    private fun irAMenuPrincipal() {
        Toast.makeText(this, "¡Gracias por tu reseña!", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, NavigationActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    private fun actualizarCalificaciones(barriosPorRuta: ArrayList<String>, rating: Float, onComplete: () -> Unit) {
        val database = FirebaseDatabase.getInstance()
        val barrioRef = database.getReference("calificaciones")
        var barriosProcesados = 0

        for (barrio in barriosPorRuta) {
            val nombreBarrio = barrio.trim()

            barrioRef.child(nombreBarrio).child("calificacion").get().addOnSuccessListener { snapshot ->
                val calificacionActual = snapshot.getValue(Float::class.java) ?: 0.0f
                val nuevaCalificacion = (calificacionActual + rating) / 2

                barrioRef.child(nombreBarrio).child("calificacion").setValue(nuevaCalificacion).addOnCompleteListener {
                    barriosProcesados++
                    if (barriosProcesados == barriosPorRuta.size) {
                        onComplete()
                    }
                }
            }.addOnFailureListener {
                Log.e("ReviewRuta", "Error al obtener datos del barrio $nombreBarrio", it)
                barriosProcesados++
                if (barriosProcesados == barriosPorRuta.size) onComplete()
            }
        }
    }
}