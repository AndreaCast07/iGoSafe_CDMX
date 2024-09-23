package com.icm.igosafeapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        setContentView(R.layout.activity_main)

        //Animaciones
        val animacionArriba = AnimationUtils.loadAnimation(this, R.anim.desplazamiento_arriba)
        val animacionAbajo = AnimationUtils.loadAnimation(this, R.anim.desplazamiento_abajo)

        val logo: ImageView = findViewById(R.id.logoColor)
        val logoNombre: ImageView = findViewById(R.id.logoName)
        logo.animation = animacionArriba
        logoNombre.animation = animacionAbajo

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }, 4000) // 4000 milisegundos de retardo
    }
}