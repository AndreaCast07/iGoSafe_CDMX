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
import com.icm.igosafeapp.ContactosAdapter
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.icm.igosafeapp.R
import com.icm.igosafeapp.databinding.FragmentPlanearViajeBinding
import com.icm.igosafeapp.ruta_peatonal
import com.icm.igosafeapp.ruta_vehicular
import org.json.JSONObject
import java.util.Locale

class HomeFragment : Fragment() {
    private var _binding: FragmentPlanearViajeBinding? = null
    private val binding get() = _binding!!
    private var selectedOption: String? = null

    private val CHANNEL_ID = "ubicacion_channel"
    private val REQUEST_LOCATION_PERMISSION = 100
    private val PERMISSION_DENIED_FOREVER_KEY = "permission_denied_forever"

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var contacts: List<Contactos> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlanearViajeBinding.inflate(inflater, container, false)

        // Inicializa el cliente de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        // Inicializa el LocationCallback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    try {
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (addresses != null && addresses.isNotEmpty()) {
                            val address = addresses[0].getAddressLine(0)
                            binding.actualLocation.setText(address) // Establece la dirección en el EditText
                        } else {
                            Log.e("HomeFragment", "No se encontraron direcciones para la ubicación.")
                        }
                    } catch (e: Exception) {
                        Log.e("HomeFragment", "Error al obtener la dirección: ${e.message}")
                    }
                }
            }
        }

        // Configurar el listener para el EditText
        binding.actualLocation.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                solicitarPermisoGPS()
            }
        }

        // Recibir los contactos pasados por el Intent
        arguments?.getString("contacts")?.let { contactsJson ->
            val type = object : TypeToken<List<Contactos>>() {}.type
            contacts = Gson().fromJson(contactsJson, type)
            Log.d("HomeFragment", "Contactos cargados: ${contacts.size}")
        } ?: run {
            Log.e("HomeFragment", "No se recibieron contactos")
        }

        setupAutoCompleteTextView()

        // Verifica si ya se tiene el permiso de localización al crear la vista
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            obtenerUbicacion()
        } else {
            solicitarPermisoGPS() // Solicitar permiso si no se tiene
        }

        return binding.root
    }


    private fun solicitarPermisoGPS() {
        val sharedPrefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val permissionDeniedForever = sharedPrefs.getBoolean(PERMISSION_DENIED_FOREVER_KEY, false)

        // Verifica si el permiso de localización no ha sido concedido
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Solicitar el permiso de nuevo
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION)
            } else if (permissionDeniedForever) {
                Toast.makeText(requireContext(), "No se puede avanzar sin el permiso de ubicación.", Toast.LENGTH_SHORT).show()
                return
            } else {
                // Solicitar permiso por primera vez
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION)
            }
        }
    }

    private fun obtenerUbicacion() {
        if (!verificarPermisosUbicacion()) {
            return
        }


        // Inicia actualizaciones en tiempo real
        try {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, // Prioridad de alta precisión
                1000 // Intervalo de 10 segundos
            ).setMinUpdateIntervalMillis(5000) // Intervalo más rápido de 5 segundos
                .build()

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("HomeFragment", "Error al solicitar actualizaciones de ubicación: ${e.message}")
        }
    }

    private fun verificarPermisosUbicacion(): Boolean {
        return if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
            false
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            Log.d("HomeFragment", "onRequestPermissionsResult called")
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), "Permiso de GPS concedido", Toast.LENGTH_SHORT).show()
                obtenerUbicacion() // Llama a obtenerUbicacion si se concede el permiso
            } else {
                val sharedPrefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                with(sharedPrefs.edit()) {
                    putBoolean(PERMISSION_DENIED_FOREVER_KEY, !ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION))
                    apply()
                }

                if (!ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION)) {
                    Toast.makeText(requireContext(), "No se puede avanzar sin el permiso de ubicación.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Funcionalidades limitadas", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadContactNameAndNickname(selectedNickname: String?): Pair<String?, String?> {
        var name: String? = null
        var nickname: String? = null

        try {
            val inputStream = requireActivity().assets.open("contactos.json")
            val json = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(json)
            val contactsArray = jsonObject.getJSONArray("contacts")

            for (i in 0 until contactsArray.length()) {
                val contactJson = contactsArray.getJSONObject(i)
                val currentNickname = contactJson.getString("nickname")

                // Log para verificar los valores en cada iteración
                Log.d("loadContactNameAndNickname", "Comparando -> selectedNickname: $selectedNickname, currentNickname: $currentNickname")

                // Comparación insensible a mayúsculas/minúsculas
                if (currentNickname.equals(selectedNickname, ignoreCase = true)) {
                    name = contactJson.getString("name")
                    nickname = currentNickname
                    Log.d("loadContactNameAndNickname", "Nombre encontrado: $name, Apodo: $nickname")
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Log final para verificar lo que se va a retornar
        Log.d("loadContactNameAndNickname", "Retornando -> Nombre: $name, Apodo: $nickname")
        return Pair(name, nickname)
    }



    private fun setupButtons(name: String?, nickname: String?, latitude: Double, longitude: Double) {
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
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                when (selectedOption) {
                    "Caminar" -> {
                        Log.d("SetupButtons", "Enviando a ruta_peatonal -> Name: $name, Nickname: $nickname, Latitude: $latitude, Longitude: $longitude")
                        val intent = Intent(requireContext(), ruta_peatonal::class.java).apply {
                            putExtra("name", name)
                            putExtra("nickname", nickname)
                            putExtra("latitude", latitude)
                            putExtra("longitude", longitude)
                        }
                        startActivity(intent)
                    }
                    "Carro" -> {
                        Log.d("SetupButtons", "Enviando a ruta_vehicular -> Name: $name, Nickname: $nickname, Latitude: $latitude, Longitude: $longitude")
                        val intent = Intent(requireContext(), ruta_vehicular::class.java).apply {
                            putExtra("name", name)
                            putExtra("nickname", nickname)
                            putExtra("latitude", latitude)
                            putExtra("longitude", longitude)
                        }
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
            val selectedNickname = selectedContact?.nickname

            binding.contactLocation.setText(selectedContact?.nickname, false)

            val (latitude, longitude) = loadContactLocation(selectedNickname)

            val (name, nickname) = loadContactNameAndNickname(selectedNickname)

// Proporcionar valores predeterminados si son nulos
            setupButtons(name, nickname, latitude ?: 0.0, longitude ?: 0.0)



            if (latitude != null && longitude != null) {
                Log.d("HomeFragment", "Latitud: $latitude, Longitud: $longitude")
            } else {
                Log.e("HomeFragment", "No se encontraron coordenadas para el contacto seleccionado.")
            }
        }
    }

    private fun loadContactLocation(selectedNickname: String?): Pair<Double?, Double?> {
        var latitude: Double? = null
        var longitude: Double? = null
        try {
            val inputStream = requireActivity().assets.open("contactos.json") // Asegúrate de que el archivo se llama contactos.json
            val json = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(json)
            val contactsArray = jsonObject.getJSONArray("contacts")

            for (i in 0 until contactsArray.length()) {
                val contactJson = contactsArray.getJSONObject(i)
                val nickname = contactJson.getString("nickname")

                // Verifica si el apodo coincide con el contacto seleccionado
                if (nickname == selectedNickname) {
                    val locationJson = contactJson.getJSONObject("location")
                    latitude = locationJson.getDouble("latitude")
                    longitude = locationJson.getDouble("longitude")
                    Log.d("LoadContactLocation", "Latitud: $latitude, Longitud: $longitude")
                    break // Salir del bucle una vez que se encuentra el contacto
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Log.d("LoadContactLocation", "Retornando -> Latitud: $latitude, Longitud: $longitude")
        return Pair(latitude, longitude) // Devuelve un par con latitud y longitud
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        fusedLocationClient.removeLocationUpdates(locationCallback) // Detener actualizaciones al destruir la vista
    }
}
