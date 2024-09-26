package com.icm.igosafeapp.ui.favorites

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.icm.igosafeapp.adapter_contactos
import com.icm.igosafeapp.databinding.FragmentFavoritesBinding

class favoritesFragment : Fragment() {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!
    private lateinit var favoritesListView: ListView
    private lateinit var favorites: List<String> // Lista para almacenar los contactos favoritos
    private lateinit var searchBar: EditText

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflar el layout del fragmento
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)

        // Inicializar los elementos de la interfaz
        searchBar = binding.searchBar // Acceder al EditText usando binding
        favoritesListView = binding.contactList // Acceder a ListView usando binding

        // Recibir los favoritos desde el argumento o guardarlos en una lista vacía
        favorites = arguments?.getStringArrayList("favoritos") ?: emptyList()

        // Configurar el adaptador
        val adapter = adapter_contactos(requireContext(), favorites)
        favoritesListView.adapter = adapter

        // Agregar un TextWatcher para filtrar la lista
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter.filter(s)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        return binding.root

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}