package com.icm.igosafeapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.icm.igosafeapp.databinding.ActivityTerminosBinding
import java.io.BufferedReader
import java.io.InputStreamReader

class TerminosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTerminosBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTerminosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Cargamos el texto desde el archivo .txt
        cargarTextoDesdeAssets()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAcceptTerms.setOnClickListener { finish() }
    }

    private fun cargarTextoDesdeAssets() {
        try {
            // Abrimos el archivo desde la carpeta assets
            val reader = BufferedReader(InputStreamReader(assets.open("terminos.txt")))
            val content = StringBuilder()
            var line: String? = reader.readLine()

            while (line != null) {
                content.append(line).append("\n")
                line = reader.readLine()
            }
            reader.close()

            // Lo aplicamos al TextView
            binding.tvTerminosContenido.text = content.toString()

        } catch (e: Exception) {
            binding.tvTerminosContenido.text = "Error al cargar los términos: ${e.message}"
        }
    }
}