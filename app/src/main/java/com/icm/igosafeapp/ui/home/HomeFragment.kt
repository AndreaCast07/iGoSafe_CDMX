package com.icm.igosafeapp.ui.home

import Contactos
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.icm.igosafeapp.ContactosAdapter
import com.icm.igosafeapp.PlaceAutocompleteAdapter
import com.icm.igosafeapp.R
import com.icm.igosafeapp.databinding.FragmentPlanearViajeBinding
import com.icm.igosafeapp.ruta_peatonal
import com.icm.igosafeapp.ruta_vehicular
import java.util.Locale
import java.util.concurrent.Executors

class HomeFragment : Fragment() {

    private var _binding: FragmentPlanearViajeBinding? = null
    private val binding get() = _binding!!
    private var selectedOption: String? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var placesClient: PlacesClient
    private var contacts: List<Contactos> = emptyList()

    // Ubicación Actual (Origen)
    private var currentLat: Double? = null
    private var currentLng: Double? = null

    // Ubicación Destino
    private var destName: String? = null
    private var destLat: Double? = null
    private var destLng: Double? = null

    // Bounds aproximados para la Ciudad de México
    private val cdmxBounds = RectangularBounds.newInstance(
        LatLng(19.0482, -99.3649), // Suroeste
        LatLng(19.5928, -98.9403)  // Noreste
    )

    private val requestLocationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            obtenerUbicacion()
        } else {
            Toast.makeText(requireContext(), "Permiso de ubicación necesario para autocompletar origen", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestContactsPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            cargarContactos()
        }
    }

    private val autocompleteLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val place = Autocomplete.getPlaceFromIntent(result.data!!)
            establecerDestino(place)
        } else if (result.resultCode == AutocompleteActivity.RESULT_ERROR) {
            val status = Autocomplete.getStatusFromIntent(result.data!!)
            Log.e("Places", "Error: ${status.statusMessage}")
            Toast.makeText(requireContext(), "Error al buscar: ${status.statusMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlanearViajeBinding.inflate(inflater, container, false)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        if (!Places.isInitialized()) {
            Places.initialize(requireContext(), getString(R.string.codigo))
        }
        placesClient = Places.createClient(requireContext())

        setupLocationCallback()
        setupClickListeners()
        setupAutoCompleteTextView()
        verificarYSolicitarPermisos()
        
        return binding.root
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (!isAdded) return
                val location = locationResult.lastLocation ?: return
                
                currentLat = location.latitude
                currentLng = location.longitude
                
                subirUbicacionAFirebase(location.latitude, location.longitude)
                
                val currentText = binding.actualLocation.text.toString()
                if (currentText.isEmpty() || currentText == getString(R.string.comentarioOrigen)) {
                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    Executors.newSingleThreadExecutor().execute {
                        try {
                            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                            if (!addresses.isNullOrEmpty()) {
                                Handler(Looper.getMainLooper()).post {
                                    if (isAdded) {
                                        binding.actualLocation.setText(addresses[0].getAddressLine(0))
                                    }
                                }
                            }
                        } catch (e: Exception) { Log.e("GPS", "Error Geocoder: ${e.message}") }
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnCaminar.setOnClickListener {
            selectedOption = "Caminar"
            actualizarEstadoBotones()
        }

        binding.btnCarro.setOnClickListener {
            selectedOption = "Carro"
            actualizarEstadoBotones()
        }

        binding.btnSolicitarUbicacion.setOnClickListener {
            if (currentLat == null) {
                Toast.makeText(requireContext(), "Obteniendo tu ubicación actual...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (destLat == null) {
                Toast.makeText(requireContext(), "Por favor, busca un destino primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedOption == null) {
                Toast.makeText(requireContext(), "Elige modo de transporte: Caminar o Carro", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intentClass = if (selectedOption == "Caminar") ruta_peatonal::class.java else ruta_vehicular::class.java
            val intent = Intent(requireContext(), intentClass).apply {
                putExtra("startLat", currentLat!!)
                putExtra("startLong", currentLng!!)
                putExtra("endLat", destLat!!)
                putExtra("endLong", destLng!!)
                putExtra("name", destName)
            }
            startActivity(intent)
        }

        binding.searchIcon.setOnClickListener {
            val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)
            val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
                .setLocationRestriction(cdmxBounds)
                .setCountries(listOf("MX"))
                .build(requireContext())
            autocompleteLauncher.launch(intent)
        }
    }

    private fun setupAutoCompleteTextView() {
        if (!isAdded) return
        
        val adapter = PlaceAutocompleteAdapter(requireContext(), placesClient)
        binding.contactLocation.setAdapter(adapter)
        
        binding.contactLocation.setOnItemClickListener { _, _, position, _ ->
            val prediction = adapter.getItem(position)
            val placeId = prediction.placeId
            val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)
            
            val request = FetchPlaceRequest.builder(placeId, fields).build()
            placesClient.fetchPlace(request).addOnSuccessListener { response ->
                establecerDestino(response.place)
                // Ocultar el teclado después de seleccionar
                binding.contactLocation.clearFocus()
            }.addOnFailureListener { e ->
                Log.e("Places", "Error fetching place details: ${e.message}")
            }
        }
    }

    private fun establecerDestino(place: Place) {
        destLat = place.latLng?.latitude
        destLng = place.latLng?.longitude
        destName = place.name ?: place.address
        // El segundo parámetro false evita que se dispare el filtro (sugerencias) de nuevo al escribir el texto
        binding.contactLocation.setText(destName, false)
        Toast.makeText(requireContext(), "Destino fijado: $destName", Toast.LENGTH_SHORT).show()
    }

    private fun actualizarEstadoBotones() {
        val colorSel = ContextCompat.getColorStateList(requireContext(), R.color.azul)
        val colorOff = ContextCompat.getColorStateList(requireContext(), R.color.azul2)
        binding.btnCaminar.backgroundTintList = if (selectedOption == "Caminar") colorSel else colorOff
        binding.btnCarro.backgroundTintList = if (selectedOption == "Carro") colorSel else colorOff
    }

    private fun verificarYSolicitarPermisos() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            obtenerUbicacion()
        }
        
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun obtenerUbicacion() {
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("GPS", "SecurityException: ${e.message}")
        }
    }

    private fun subirUbicacionAFirebase(latitude: Double, longitude: Double) {
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirebaseDatabase.getInstance().getReference("users").child(uid).child("location")
                .setValue(mapOf("latitude" to latitude, "longitude" to longitude))
        }
    }

    private fun cargarContactos() { }

    override fun onDestroyView() {
        super.onDestroyView()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        _binding = null
    }
}
