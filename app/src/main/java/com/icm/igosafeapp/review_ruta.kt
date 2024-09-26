package com.icm.igosafeapp

import android.content.Intent
import android.os.Bundle
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

class review_ruta : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review_ruta)

        // Mostrar el popup inmediatamente al abrir la actividad
        showVentana()
    }

    fun showVentana() {
        // Obtener referencias a los elementos del popup
        val closeButton: Button = findViewById(R.id.cerrarReview)
        val ratingBar: RatingBar = findViewById(R.id.ratingRutaSegura)
        val editText: EditText =findViewById(R.id.review)

        closeButton.setOnClickListener {
            val rating = ratingBar.rating
            val reviewText = editText.text.toString()

            if (rating > 0 && reviewText.isNotBlank()) {
                Toast.makeText(this, "Reseña guardada", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, Menu::class.java)
                startActivity(intent)
            } else {

                Toast.makeText(this, "Por favor, completa el rating y el comentario.", Toast.LENGTH_SHORT).show()
            }
        }

    }
}
