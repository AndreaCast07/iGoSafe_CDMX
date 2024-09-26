package com.icm.igosafeapp

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class contactos_favoritos : AppCompatActivity() {
    private lateinit var favoritesListView: ListView
    private lateinit var favorites: List<String> // Lista para almacenar los contactos favoritos
    private lateinit var searchBar: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contactos_favoritos)

        searchBar = findViewById(R.id.search_bar)

        favoritesListView = findViewById(R.id.contact_list) // Asegúrate de que este ID coincida con tu layout

        // Recibir los favoritos desde el Intent
        favorites = intent.getStringArrayListExtra("favoritos") ?: emptyList()

        // Configurar el adaptador
        val adapter = adapter_contactos(this, favorites)
        favoritesListView.adapter = adapter

        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter.filter(s)
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }
}
