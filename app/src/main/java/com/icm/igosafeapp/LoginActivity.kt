package com.icm.igosafeapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val passwordInput = findViewById<EditText>(R.id.inputConstraseña)

        // Detectar cuando se presiona el ícono del ojo
        passwordInput.setOnTouchListener { v, event ->
            val drawableEndIndex = 2 // Es el índice del icono drawableEnd
            if (event.rawX >= (passwordInput.right - passwordInput.compoundDrawables[drawableEndIndex].bounds.width())) {
                // Si se presiona el ícono del ojo
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        // Mostrar la contraseña cuando se presiona
                        passwordInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        // Volver a ocultar la contraseña cuando se suelta
                        passwordInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    }
                }
                true
            } else {
                false
            }
        }
    }
}