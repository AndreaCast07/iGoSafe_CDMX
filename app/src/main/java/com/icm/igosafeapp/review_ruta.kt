package com.icm.igosafeapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.PopupWindow
import android.widget.RatingBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class review_ruta : AppCompatActivity() {

    private var ratingSeleccionado: Float = 0f
    private var comentarioEscrito: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review_ruta)
        showVentana()
    }

    fun showVentana() {
        val closeButton: Button = findViewById(R.id.cerrarReview)
        val ratingBar: RatingBar = findViewById(R.id.ratingRutaSegura)
        val editText: EditText = findViewById(R.id.review)

        val barriosPorRuta = intent.getStringArrayListExtra("barriosPorRuta")

        closeButton.setOnClickListener {
            ratingSeleccionado = ratingBar.rating
            comentarioEscrito = editText.text.toString()

            if (ratingSeleccionado > 0 && comentarioEscrito.isNotBlank()) {
                // Guardar la reseña en una ubicación general vinculada al usuario
                guardarResenaEnFirebase(ratingSeleccionado, comentarioEscrito)

                if (barriosPorRuta != null && barriosPorRuta.isNotEmpty()) {
                    actualizarCalificaciones(barriosPorRuta, ratingSeleccionado) {
                        irAMenuPrincipal()
                    }
                } else {
                    irAMenuPrincipal()
                }
            } else {
                Toast.makeText(this, "Por favor, completa el rating y el comentario.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun guardarResenaEnFirebase(rating: Float, review: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "desconocido"
        val ref = FirebaseDatabase.getInstance().getReference("resenas_viajes").child(userId).push()
        
        val resenaData = mapOf(
            "calificacion" to rating,
            "comentario" to review,
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