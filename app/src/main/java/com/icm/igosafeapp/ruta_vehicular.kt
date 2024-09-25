package com.icm.igosafeapp

//import android.R
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity


class ruta_vehicular : AppCompatActivity() {
    lateinit var icono: TextView
    lateinit var iniciar: Button
    lateinit var comentarios1:Button
    lateinit var comentarios2:Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.icm.igosafeapp.R.layout.activity_ruta_vehicular)
        /*
        val textView: TextView = findViewById(R.id.rutaVehiculo)
        val drawable: Drawable? = resources.getDrawable(R.drawable.icon_auto, theme)
        drawable?.setBounds(0, 0, 5, 5)  // Ajusta el tamaño (ancho y alto en píxeles)
        textView.setCompoundDrawables(drawable, null, null, null)*/
        iniciar = findViewById(R.id.iniciarViaje)
        comentarios1 = findViewById(R.id.comentarios1)
        comentarios2 = findViewById(R.id.comentarios2)

        verComentarios()

        iniciar.setOnClickListener {
              // Mostrar el popup cuando se presiona el botón
            val intent = Intent(this, review_ruta::class.java)
            startActivity(intent)
        }
    }
    private fun verComentarios() {
        comentarios1.setOnClickListener {
            val intent = Intent(this, comentarios::class.java)
            startActivity(intent)
        }

        comentarios2.setOnClickListener {
            val intent = Intent(this, comentarios::class.java)
            startActivity(intent)
        }
    }

}