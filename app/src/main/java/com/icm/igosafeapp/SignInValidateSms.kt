package com.icm.igosafeapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SignInValidateSms : AppCompatActivity() {
    private lateinit var editText1: EditText
    private lateinit var editText2: EditText
    private lateinit var editText3: EditText
    private lateinit var editText4: EditText
    private lateinit var editText5: EditText
    private lateinit var btnValidar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in_validate)

        // Inicializar variables
        editText1 = findViewById(R.id.editText1)
        editText2 = findViewById(R.id.editText2)
        editText3 = findViewById(R.id.editText3)
        editText4 = findViewById(R.id.editText4)
        editText5 = findViewById(R.id.editText5)
        btnValidar = findViewById(R.id.btnValidate)

        configurarEditTexts(arrayOf(editText1, editText2, editText3, editText4, editText5), btnValidar)
        mostrarLayoutCrearContraseña()
    }

    private fun mostrarLayoutCrearContraseña() {
        btnValidar.setOnClickListener {
            val intent = Intent(this, CreatePassword::class.java)
            startActivity(intent)
        }
    }

    private fun configurarEditTexts(editTexts: Array<EditText>, btnValidar: Button) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        for (i in editTexts.indices) {
            editTexts[i].addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    // Si el campo tiene un carácter, pasa al siguiente
                    if (s?.length == 1 && i < editTexts.size - 1) {
                        editTexts[i + 1].requestFocus()
                    }
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            // Listener para manejar la tecla de borrado
            editTexts[i].setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DEL) {
                    if (editTexts[i].text.isEmpty() && i > 0) {
                        editTexts[i - 1].requestFocus()
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }

        // En el último campo, ocultar el teclado al completar
        editTexts.last().addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s?.length == 1) {
                    imm.hideSoftInputFromWindow(editTexts.last().windowToken, 0)
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s?.isEmpty() == true && before == 1) {
                    editTexts[editTexts.size - 2].requestFocus()
                }
            }
        })
    }
}
