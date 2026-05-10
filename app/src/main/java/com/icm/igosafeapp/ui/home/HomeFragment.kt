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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import java.util.Locale
import java.util.concurrent.Executors

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import org.json.JSONArray
import org.json.JSONObject

class HomeFragment : Fragment() {

    private var _binding: FragmentPlanearViajeBinding? = null
    private val binding get() = _binding!!
    private var selectedOption: String? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var placesClient: PlacesClient

    private var currentLat: Double? = null
    private var currentLng: Double? = null
    
    private var originLat: Double? = null
    private var originLng: Double? = null
    private var originName: String? = null

    private var destName: String? = null
    private var destLat: Double? = null
    private var destLng: Double? = null
    private var destAddress: String? = null

    private val PREFS_NAME = "igosafe_prefs"
    private val RECENT_KEY = "recent_destinations"
    private val geocoderExecutor = Executors.newSingleThreadExecutor()

    data class RecentPlace(val name: String, val address: String, val lat: Double, val lng: Double)

    private val cdmxBounds = RectangularBounds.newInstance(LatLng(19.0482, -99.3649), LatLng(19.5928, -98.9403))

    private val requestLocationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) obtenerUbicacion()
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
        loadRecentDestinations()
        
        return binding.root
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (!isAdded) return
                val location = locationResult.lastLocation ?: return
                
                currentLat = location.latitude
                currentLng = location.longitude
                
                // Si el usuario no ha elegido un origen manual, usamos el GPS
                if (originLat == null) {
                    originLat = location.latitude
                    originLng = location.longitude
                    
                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    geocoderExecutor.execute {
                        try {
                            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                            if (!addresses.isNullOrEmpty()) {
                                val addr = addresses[0].getAddressLine(0)
                                Handler(Looper.getMainLooper()).post {
                                    if (isAdded && originLat == location.latitude) { // Check if still using GPS
                                        binding.actualLocation.setText(addr, false)
                                        originName = addr
                                    }
                                }
                            }
                        } catch (e: Exception) { Log.e("GPS", "Error Geocoder: ${e.message}") }
                    }
                }
                
                subirUbicacionAFirebase(location.latitude, location.longitude)
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnCaminar.setOnClickListener { selectedOption = "Caminar"; actualizarEstadoBotones() }
        binding.btnCarro.setOnClickListener { selectedOption = "Carro"; actualizarEstadoBotones() }

        binding.btnSolicitarUbicacion.setOnClickListener {
            if (originLat == null) { Toast.makeText(requireContext(), "Selecciona un origen", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (destLat == null) { Toast.makeText(requireContext(), "Busca un destino", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (selectedOption == null) { Toast.makeText(requireContext(), "Elige modo de transporte", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

            // Validación de límites CDMX
            val originPoint = LatLng(originLat!!, originLng!!)
            val destPoint = LatLng(destLat!!, destLng!!)
            
            if (originPoint.latitude < 19.04 || originPoint.latitude > 19.60 || 
                originPoint.longitude < -99.37 || originPoint.longitude > -98.94 ||
                destPoint.latitude < 19.04 || destPoint.latitude > 19.60 || 
                destPoint.longitude < -99.37 || destPoint.longitude > -98.94) {
                Toast.makeText(requireContext(), "iGoSafe solo opera dentro de la Ciudad de México", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            saveToRecent(destName ?: "", destAddress ?: "", destLat!!, destLng!!)

            val intentClass = if (selectedOption == "Caminar") ruta_peatonal::class.java else ruta_vehicular::class.java
            startActivity(Intent(requireContext(), intentClass).apply {
                putExtra("startLat", originLat!!)
                putExtra("startLong", originLng!!)
                putExtra("endLat", destLat!!)
                putExtra("endLong", destLng!!)
                putExtra("name", destName)
                putExtra("address", destAddress)
            })
        }

        binding.searchIcon.setOnClickListener {
            val fields = listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.LOCATION, Place.Field.FORMATTED_ADDRESS)
            val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields).setLocationRestriction(cdmxBounds).setCountries(listOf("MX")).build(requireContext())
            autocompleteLauncher.launch(intent)
        }
        
        binding.actualLocation.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.actualLocation.text.clear()
        }
    }

    private val autocompleteLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) establecerDestino(Autocomplete.getPlaceFromIntent(result.data!!))
    }

    private fun setupAutoCompleteTextView() {
        if (!isAdded) return
        val adapter = PlaceAutocompleteAdapter(requireContext(), placesClient)
        
        // Configurar Origen
        binding.actualLocation.setAdapter(adapter)
        binding.actualLocation.setOnItemClickListener { _, _, position, _ ->
            val request = FetchPlaceRequest.builder(adapter.getItem(position).placeId, listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.LOCATION, Place.Field.FORMATTED_ADDRESS)).build()
            placesClient.fetchPlace(request).addOnSuccessListener { 
                val p = it.place
                val address = p.formattedAddress ?: ""
                if (!address.contains("Ciudad de México") && !address.contains("CDMX") && !address.contains("Distrito Federal")) {
                    Toast.makeText(requireContext(), "El origen debe estar dentro de CDMX", Toast.LENGTH_LONG).show()
                    binding.actualLocation.text.clear()
                    originLat = null
                    originLng = null
                    return@addOnSuccessListener
                }
                originLat = p.location?.latitude
                originLng = p.location?.longitude
                originName = p.displayName ?: p.formattedAddress
                binding.actualLocation.setText(originName, false)
                binding.actualLocation.clearFocus() 
            }
        }

        // Configurar Destino
        binding.contactLocation.setAdapter(adapter)
        binding.contactLocation.setOnItemClickListener { _, _, position, _ ->
            val request = FetchPlaceRequest.builder(adapter.getItem(position).placeId, listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.LOCATION, Place.Field.FORMATTED_ADDRESS)).build()
            placesClient.fetchPlace(request).addOnSuccessListener { establecerDestino(it.place); binding.contactLocation.clearFocus() }
        }
    }

    private fun establecerDestino(place: Place) {
        val address = place.formattedAddress ?: ""
        if (!address.contains("Ciudad de México") && !address.contains("CDMX") && !address.contains("Distrito Federal")) {
            Toast.makeText(requireContext(), "iGoSafe solo opera dentro de la Ciudad de México", Toast.LENGTH_LONG).show()
            binding.contactLocation.text.clear()
            destLat = null
            destLng = null
            return
        }
        destLat = place.location?.latitude
        destLng = place.location?.longitude
        destName = place.displayName ?: place.formattedAddress
        destAddress = place.formattedAddress ?: ""
        binding.contactLocation.setText(destName, false)
    }

    private fun saveToRecent(name: String, address: String, lat: Double, lng: Double) {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val list = getRecentPlaces().toMutableList()
        
        // Evitar duplicados
        list.removeAll { it.name == name || (it.lat == lat && it.lng == lng) }
        list.add(0, RecentPlace(name, address, lat, lng))
        
        // Mantener solo 3
        val subList = if (list.size > 3) list.subList(0, 3) else list
        
        val array = JSONArray()
        subList.forEach { 
            val obj = JSONObject()
            obj.put("name", it.name)
            obj.put("address", it.address)
            obj.put("lat", it.lat)
            obj.put("lng", it.lng)
            array.put(obj)
        }
        prefs.edit().putString(RECENT_KEY, array.toString()).apply()
        loadRecentDestinations()
    }

    private fun getRecentPlaces(): List<RecentPlace> {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(RECENT_KEY, null) ?: return emptyList()
        val list = mutableListOf<RecentPlace>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(RecentPlace(obj.getString("name"), obj.getString("address"), obj.getDouble("lat"), obj.getDouble("lng")))
            }
        } catch (e: Exception) {}
        return list
    }

    private fun loadRecentDestinations() {
        binding.historyContainer.removeAllViews()
        val recent = getRecentPlaces()
        
        if (recent.isEmpty()) {
            binding.historyLabel.visibility = View.GONE
            return
        }
        binding.historyLabel.visibility = View.VISIBLE

        recent.forEach { place ->
            val view = TextView(requireContext()).apply {
                text = "${place.name}\n${place.address}"
                setPadding(16, 24, 16, 24)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(requireContext(), R.color.azul3))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                isClickable = true
                isFocusable = true
                val outValue = android.util.TypedValue()
                requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                setOnClickListener {
                    destName = place.name
                    destAddress = place.address
                    destLat = place.lat
                    destLng = place.lng
                    binding.contactLocation.setText(place.name, false)
                    actualizarEstadoBotones()
                }
            }
            binding.historyContainer.addView(view)
            
            // Separador
            val divider = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(Color.LTGRAY)
            }
            binding.historyContainer.addView(divider)
        }
    }

    private fun actualizarEstadoBotones() {
        val colorSel = ContextCompat.getColorStateList(requireContext(), R.color.azul1)
        val colorOff = ContextCompat.getColorStateList(requireContext(), R.color.azul2)
        binding.btnCaminar.backgroundTintList = if (selectedOption == "Caminar") colorSel else colorOff
        binding.btnCarro.backgroundTintList = if (selectedOption == "Carro") colorSel else colorOff
    }

    private fun verificarYSolicitarPermisos() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else obtenerUbicacion()
    }

    private fun obtenerUbicacion() {
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {}
    }

    private fun subirUbicacionAFirebase(latitude: Double, longitude: Double) {
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            // Usando de nuevo el nodo "users" específico para ubicaciones
            FirebaseDatabase.getInstance().getReference("users").child(uid).child("location")
                .setValue(mapOf("latitude" to latitude, "longitude" to longitude))
                .addOnFailureListener { Log.e("Firebase", "Fallo al subir a users: ${it.message}") }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        geocoderExecutor.shutdownNow()
        _binding = null
    }
}
