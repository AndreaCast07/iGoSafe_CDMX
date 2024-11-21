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
import androidx.appcompat.app.AppCompatActivity.LAYOUT_INFLATER_SERVICE
import com.google.firebase.database.FirebaseDatabase

class review_ruta : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review_ruta)
        showVentana()
    }

    fun showVentana() {
        val closeButton: Button = findViewById(R.id.cerrarReview)
        val ratingBar: RatingBar = findViewById(R.id.ratingRutaSegura)
        val editText: EditText =findViewById(R.id.review)

        val barriosPorRuta = intent.getStringArrayListExtra("barriosPorRuta")

        closeButton.setOnClickListener {
            val rating = ratingBar.rating
            val reviewText = editText.text.toString()

            if (rating > 0 && reviewText.isNotBlank() && barriosPorRuta != null) {
                actualizarCalificaciones(barriosPorRuta, rating) {
                    Toast.makeText(this, "Reseña guardada", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, Menu::class.java)
                    startActivity(intent)
                    finish()
                }
            } else {
                Toast.makeText(this, "Por favor, completa el rating y el comentario.", Toast.LENGTH_SHORT).show()
            }
        }

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
            }
        }
    }
}
