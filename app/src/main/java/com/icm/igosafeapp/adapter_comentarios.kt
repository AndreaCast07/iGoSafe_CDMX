package com.icm.igosafeapp

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.RatingBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class adapter_comentarios(context: Context, private val comentarios: List<comentarios.Comentario>) : ArrayAdapter<comentarios.Comentario>(context, 0, comentarios) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.activity_adapter_comentarios, parent, false)

        val comentario = getItem(position)

        // Configurar el RatingBar
        val ratingBar = view.findViewById<RatingBar>(R.id.ratingBar)
        ratingBar.rating = (comentario?.rating ?: 0).toFloat()


        // Configurar el comentario
        val commentText = view.findViewById<TextView>(R.id.comment_text)
        commentText.text = comentario?.comment ?: ""

        return view
    }
}