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
    private lateinit var searchBar: EditText

    private val fixedFavorites = listOf(
        "Juan Pérez",
        "María Gómez",
        "Carlos Fernández",
        "Ana Torres",
        "Luis Martínez",
        "Sofía López"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)

        searchBar = binding.searchBar
        favoritesListView = binding.contactList

        val adapter = adapter_contactos(requireContext(), fixedFavorites)
        favoritesListView.adapter = adapter

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