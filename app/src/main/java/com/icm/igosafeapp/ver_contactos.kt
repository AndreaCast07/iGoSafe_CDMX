package com.icm.igosafeapp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ver_contactos : AppCompatActivity() {
    private lateinit var searchBar: EditText
    private lateinit var contactList: ListView
    private lateinit var adapter: adapter_contactos
    private lateinit var contactNames: MutableList<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ver_contactos)

        searchBar = findViewById(R.id.search_bar)
        contactList = findViewById(R.id.contact_list)

        val showFavoritesButton: Button = findViewById(R.id.show_favorites_button)
    // Lista de contactos (ejemplo)
    contactNames = mutableListOf(
    "Juan Pérez",
    "María Gómez",
    "Carlos Fernández",
    "Ana Torres",
    "Luis Martínez",
    "Sofía López"
    )

        // Adaptador para la lista
        adapter = adapter_contactos(this, contactNames)
        contactList.adapter = adapter

        // Filtrado de la lista
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter.filter(s)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        showFavoritesButton.setOnClickListener {
            openFavorites()
        }
    }
    private fun openFavorites() {
        val favoritos = adapter.getFavorites() // Asegúrate de que tu adaptador tenga este método
        val intent = Intent(this, contactos_favoritos::class.java)
        intent.putStringArrayListExtra("favoritos", ArrayList(favoritos))
        startActivity(intent)
    }
}
