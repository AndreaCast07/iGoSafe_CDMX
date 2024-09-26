package com.icm.igosafeapp

import android.os.Bundle
import android.widget.ListView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class comentarios : AppCompatActivity() {
    private lateinit var commentsListView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comentarios)

        commentsListView = findViewById(R.id.comments_list_view)

        // Lista de comentarios de ejemplo
        val comentarios = listOf(
            Comentario(4, "La ruta es hermosa, muy recomendable."),
            Comentario(5, "Excelente experiencia, volveré."),
            Comentario(3, "Buena, pero con algunas dificultades."),
            Comentario(2, "No fue lo que esperaba."),
            Comentario(1, "Terrible experiencia, no la recomiendo.")
        )

        // Configurar el adaptador y asignarlo al ListView
        val adapter = adapter_comentarios(this, comentarios)
        commentsListView.adapter = adapter
    }
    data class Comentario(
        val rating: Int,
        val comment: String
    )
}