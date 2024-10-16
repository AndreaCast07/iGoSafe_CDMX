package com.icm.igosafeapp.ui.home

import Contactos
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.icm.igosafeapp.ContactosAdapter
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.icm.igosafeapp.R
import com.icm.igosafeapp.databinding.FragmentPlanearViajeBinding
import com.icm.igosafeapp.recorrido_peatonal
import com.icm.igosafeapp.recorrido_vehicular
import com.icm.igosafeapp.ruta_peatonal
import com.icm.igosafeapp.ruta_vehicular

class HomeFragment : Fragment() {
    private var _binding: FragmentPlanearViajeBinding? = null
    private val binding get() = _binding!!
    private var selectedOption: String? = null

    private val CHANNEL_ID = "ubicacion_channel"
    private val REQUEST_LOCATION_PERMISSION = 100
    private val PERMISSION_DENIED_FOREVER_KEY = "permission_denied_forever"

    private var contacts: List<Contactos> =  emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentPlanearViajeBinding.inflate(inflater, container, false)

        binding.actualLocation.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                solicitarPermisoGPS()
            }
        }
        // Recibir los contactos pasados por el Intent
        val contactsJson = arguments?.getString("contacts")
        if (contactsJson != null) {
            val type = object : TypeToken<List<Contactos>>() {}.type
            contacts = Gson().fromJson(contactsJson, type)
        }


        setupAutoCompleteTextView()
        setupButtons()
        return binding.root
    }

    private fun solicitarPermisoGPS() {
        val sharedPrefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val permissionDeniedForever = sharedPrefs.getBoolean(PERMISSION_DENIED_FOREVER_KEY, false)

        // Verifica si el permiso de localización no ha sido concedido
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Verifica si debe mostrar una justificación
            if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Solicitar el permiso de nuevo
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION)
            } else if (permissionDeniedForever) {
                // Si el permiso fue denegado permanentemente
                Toast.makeText(requireContext(), "No se puede avanzar sin el permiso de ubicación.", Toast.LENGTH_SHORT).show()
                return // Impide navegar a otra pantalla
            } else {
                // Si el permiso nunca fue solicitado antes, lo solicita por primera vez
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION)
            }
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), "Permiso de GPS concedido", Toast.LENGTH_SHORT).show()
            } else {
                // Si se niega el permiso, verificar si fue de manera permanente
                val sharedPrefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                with(sharedPrefs.edit()) {
                    putBoolean(PERMISSION_DENIED_FOREVER_KEY, !ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION))
                    apply()
                }

                if (!ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION)) {
                    // El permiso fue denegado permanentemente
                    Toast.makeText(requireContext(), "No se puede avanzar sin el permiso de ubicación.", Toast.LENGTH_SHORT).show()
                } else {
                    // El permiso fue denegado, pero no de manera permanente
                    Toast.makeText(requireContext(), "Funcionalidades limitadas", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupButtons() {
        binding.btnCaminar.setOnClickListener {
            selectedOption = "Caminar"
            binding.btnCaminar.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.azul)
            binding.btnCarro.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.azul2)
        }

        binding.btnCarro.setOnClickListener {
            selectedOption = "Carro"
            binding.btnCaminar.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.azul2)
            binding.btnCarro.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.azul)
        }

        binding.btnSolicitarUbicacion.setOnClickListener {
            // Verificar si el permiso está concedido antes de navegar
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                when (selectedOption) {
                    "Caminar" -> {
                        val intent = Intent(requireContext(), ruta_peatonal::class.java)
                        startActivity(intent)
                    }
                    "Carro" -> {
                        val intent = Intent(requireContext(), ruta_vehicular::class.java)
                        startActivity(intent)
                    }
                }
            } else {
                Toast.makeText(requireContext(), "Permiso de ubicación necesario para continuar.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupAutoCompleteTextView() {
        val adapter = ContactosAdapter(requireContext(), contacts)
        binding.contactLocation.setAdapter(adapter)

        binding.contactLocation.setOnClickListener {
            binding.contactLocation.showDropDown()
        }

        binding.contactLocation.setOnItemClickListener { parent, view, position, id ->
            val selectedContact = adapter.getItem(position)
            binding.contactLocation.setText(selectedContact?.nickname, false)
        }
    }

    /*private fun getContacts(): List<Contactos> {
        return listOf(
            Contactos(R.drawable.ic_person, "Johnny", "John Doe"),
            Contactos(R.drawable.ic_person, "Sally", "Sally Smith"),
            Contactos(R.drawable.ic_person, "Bobby", "Bobby Brown")
        )
    }*/

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
