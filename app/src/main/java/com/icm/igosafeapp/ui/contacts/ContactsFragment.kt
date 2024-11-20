package com.icm.igosafeapp.ui.contacts

import Contactos
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.icm.igosafeapp.adapter_contactos
import com.icm.igosafeapp.databinding.FragmentContactsBinding

class ContactsFragment : Fragment() {

    private var _binding: FragmentContactsBinding? = null
    private lateinit var searchBar: EditText
    private lateinit var contactList: ListView
    private lateinit var adapter: adapter_contactos
    private lateinit var contactNames: MutableList<String>

    private val binding get() = _binding!!
    private val REQUEST_CONTACTS_PERMISSION = 101

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContactsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Inicialización de vistas
        searchBar = binding.searchBar
        contactList = binding.contactList
        contactNames = mutableListOf()

        // Configurar el adaptador
        adapter = adapter_contactos(requireContext(), contactNames)
        contactList.adapter = adapter

        // Agregar el TextWatcher para filtrar los contactos
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter.filter(s) // Filtra los resultados
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Verificar permisos de contactos y cargar los contactos
        verificarPermisosContactos()

        return root
    }

    private fun verificarPermisosContactos() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.READ_CONTACTS),
                REQUEST_CONTACTS_PERMISSION
            )
        } else {
            cargarContactos() // Cargar los contactos si ya tiene permisos
        }
    }

    private fun cargarContactos() {
            val resolver = requireContext().contentResolver
            val cursor = resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            val firebaseDatabase = FirebaseDatabase.getInstance()
            val usuariosRef = firebaseDatabase.getReference("usuarios")

            usuariosRef.orderByChild("celular").addListenerForSingleValueEvent(object :
                ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val numerosRegistrados = mutableSetOf<String>()
                    for (usuarioSnapshot in snapshot.children) {
                        val celular = usuarioSnapshot.child("celular").getValue(String::class.java)
                        if (celular != null) {
                            numerosRegistrados.add(celular)
                        }
                    }

                    val contactosCargados = mutableListOf<String>()
                    cursor?.use {
                        while (it.moveToNext()) {
                            val nombre = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                            val numero = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))

                            // Verificar si el número está registrado en Firebase
                            if (numerosRegistrados.contains(numero)) {
                                contactosCargados.add(nombre)
                            }
                        }
                    }

                    contactNames.clear()
                    contactNames.addAll(contactosCargados)
                    adapter.notifyDataSetChanged()  // Notificar que la lista ha cambiado

                    // Aplicar filtro vacío al principio para mostrar todos los contactos
                    adapter.filter.filter("")  // Esto asegura que la lista se actualice inmediatamente
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ContactsFragment", "Error al cargar usuarios de Firebase: ${error.message}")
                }
            })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CONTACTS_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                cargarContactos() // Cargar contactos si se otorgan los permisos
            } else {
                Toast.makeText(requireContext(), "Permiso de contactos requerido para cargar contactos.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

