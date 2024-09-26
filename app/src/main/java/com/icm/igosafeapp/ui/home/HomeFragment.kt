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
import android.content.pm.PackageManager
import android.widget.Toast
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

        setupAutoCompleteTextView()
        setupButtons()
        return binding.root
    }

    private fun solicitarPermisoGPS() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(requireContext(), "Funcionalidades limitadas", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Permiso de ubicación necesario. Configúralo para habilitarlo.", Toast.LENGTH_SHORT).show()
            }
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), "Permiso de GPS concedido", Toast.LENGTH_SHORT).show()
            } else {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION)) {
                    Toast.makeText(requireContext(), "Permiso de ubicación necesario. Configúralo para habilitarlo.", Toast.LENGTH_SHORT).show()
                } else {
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
        }
    }

    private fun setupAutoCompleteTextView() {
        val contacts = getContacts()
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

    private fun getContacts(): List<Contactos> {
        return listOf(
            Contactos(R.drawable.ic_person, "Johnny", "John Doe"),
            Contactos(R.drawable.ic_person, "Sally", "Sally Smith"),
            Contactos(R.drawable.ic_person, "Bobby", "Bobby Brown")
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
