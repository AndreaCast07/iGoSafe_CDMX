package com.icm.igosafeapp

import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Pair
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

            val pairs = arrayOf(
                Pair<View,String>(logo, "logoImageTrans"),
                Pair<View,String>(logoNombre, "textTrans")
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val options = ActivityOptions.makeSceneTransitionAnimation(this, *pairs)
                startActivity(intent, options.toBundle())
            } else {
                startActivity(intent)
                finish()
            }
        }, 4000) // 4000 milisegundos de retardo
    }
}