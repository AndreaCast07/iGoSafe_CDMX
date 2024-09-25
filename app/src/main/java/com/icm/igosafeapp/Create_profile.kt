package com.icm.igosafeapp

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class Create_profile : AppCompatActivity() {
    private lateinit var spinnerDocumento: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_profile)

        spinnerDocumento = findViewById(R.id.selectTipoDocumento)
        cargarDatosSpinner()
    }

    private fun cargarDatosSpinner() {
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.opcionesDocumentos,
            android.R.layout.simple_spinner_item
        )

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDocumento.adapter = adapter

        spinnerDocumento.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position == 0) {
                    (view as? TextView)?.setTextColor(ContextCompat.getColor(parent.context, android.R.color.darker_gray))
                } else {
                    (view as? TextView)?.setTextColor(ContextCompat.getColor(parent.context, R.color.black)) // Cambiar al color negro o el que prefieras
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }
}
