package com.icm.igosafeapp.ui.contacts

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.icm.igosafeapp.adapter_contactos
import com.icm.igosafeapp.databinding.FragmentContactsBinding

class ContactsFragment : Fragment() {

    private var _binding: FragmentContactsBinding? = null
    private lateinit var searchBar: EditText
    private lateinit var contactList: ListView
    private lateinit var adapter: adapter_contactos
    private lateinit var contactNames: MutableList<String>

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContactsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        searchBar = binding.searchBar
        contactList = binding.contactList

        contactNames = mutableListOf(
            "Juan Pérez",
            "María Gómez",
            "Carlos Fernández",
            "Ana Torres",
            "Luis Martínez",
            "Sofía López"
        )

        adapter = adapter_contactos(requireContext(), contactNames)
        contactList.adapter = adapter

        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter.filter(s)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
