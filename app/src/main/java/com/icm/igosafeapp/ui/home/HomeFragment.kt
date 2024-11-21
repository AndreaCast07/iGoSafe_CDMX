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
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Looper
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.icm.igosafeapp.R
import com.icm.igosafeapp.databinding.FragmentPlanearViajeBinding
import com.icm.igosafeapp.ruta_peatonal
import com.icm.igosafeapp.ruta_vehicular
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentPlanearViajeBinding? = null
    private val binding get() = _binding!!
    private var selectedOption: String? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var contacts: List<Contactos> = emptyList()
    private lateinit var database: DatabaseReference

    // Constantes para permisos
    private val REQUEST_CONTACTS_PERMISSION = 101
    private val REQUEST_LOCATION_PERMISSION = 100

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentPlanearViajeBinding.inflate(inflater, container, false)

        // Inicializar el cliente de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        // Inicializar el LocationCallback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (!isAdded) {
                    Log.w("PlanearViajeFragment", "Fragment no adjunto al contexto. Ignorando resultado de ubicación.")
                    return
                }
                for (location in locationResult.locations) {
                    val latitude = location.latitude
                    val longitude = location.longitude

                    subirUbicacionAFirebase(latitude, longitude)

                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    try {
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (addresses != null && addresses.isNotEmpty()) {
                            val address = addresses[0].getAddressLine(0)
                            binding.actualLocation.setText(address)
                        } else {
                            Log.e("HomeFragment", "No se encontraron direcciones para la ubicación.")
                        }
                    } catch (e: Exception) {
                        Log.e("HomeFragment", "Error al obtener la dirección: ${e.message}")
                    }
                }
            }
        }

        verificarYSolicitarPermisos()
        return binding.root
    }


    // Permisos
    private fun verificarYSolicitarPermisos() {
        val permisos = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION to ::obtenerUbicacion,
            Manifest.permission.READ_CONTACTS to ::cargarContactos
        )

        permisos.forEach { (permiso, accion) ->
            if (ContextCompat.checkSelfPermission(requireContext(), permiso) != PackageManager.PERMISSION_GRANTED) {
                when (permiso) {
                    Manifest.permission.READ_CONTACTS -> solicitarPermisoContactos()
                    Manifest.permission.ACCESS_FINE_LOCATION -> solicitarPermisoGPS()
                }
            } else {
                accion()
            }
        }
    }

    private fun verificarPermisosUbicacion(): Boolean {
        return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun solicitarPermisoContactos() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.READ_CONTACTS),
            REQUEST_CONTACTS_PERMISSION
        )
    }

    private fun solicitarPermisoGPS() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_LOCATION_PERMISSION
        )
    }

    //Obtener ubicación actual del usuario y caragr en el firebase
    private fun obtenerUbicacion() {
        if (verificarPermisosUbicacion()) {
            try {
                val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                    .build()
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            } catch (e: SecurityException) {
                Log.e("HomeFragment", "Error al solicitar actualizaciones de ubicación: ${e.message}")
            }
        }
    }

    private fun subirUbicacionAFirebase(latitude: Double, longitude: Double) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            val databaseReference = FirebaseDatabase.getInstance().getReference("users").child(userId)

            val ubicacionData = mapOf(
                "latitude" to latitude,
                "longitude" to longitude
            )

            databaseReference.child("location").setValue(ubicacionData)
                .addOnSuccessListener {
                    //Log.d("HomeFragment", "Ubicación subida exitosamente a Firebase.")
                }
                .addOnFailureListener { e ->
                    Log.e("HomeFragment", "Error al subir la ubicación a Firebase: ${e.message}")
                }
        } else {
            Log.e("HomeFragment", "No se encontró un usuario autenticado.")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_LOCATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    obtenerUbicacion()
                } else {
                    Toast.makeText(requireContext(), "Permiso de GPS necesario.", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_CONTACTS_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    cargarContactos()
                } else {
                    Toast.makeText(requireContext(), "Permiso de contactos necesario.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    //Cargar contactos al firebase
    private fun cargarContactos() {
        val listaContactos = mutableListOf<Contactos>()
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

        val database = FirebaseDatabase.getInstance()
        val usuariosRef = database.getReference("usuarios")

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

                cursor?.use {
                    while (it.moveToNext()) {
                        val nombreCompleto = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                        val nickname = nombreCompleto.split(" ").firstOrNull() ?: nombreCompleto
                        val numero = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))

                        if (numerosRegistrados.contains(numero)) {
                            val iconResId = R.drawable.ic_person // Recurso predeterminado
                            listaContactos.add(Contactos(iconResId, nickname, nombreCompleto))
                        }
                    }
                }

                contacts = listaContactos
                Log.d("HomeFragment", "Contactos filtrados: ${contacts.size}")

                setupAutoCompleteTextView()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("HomeFragment", "Error al cargar usuarios de Firebase", error.toException())
            }
        })
    }

    //Configuración campo destino - autocompletar
    @SuppressLint("ClickableViewAccessibility")
    private fun setupAutoCompleteTextView() {
        val adapter = ContactosAdapter(requireContext(), contacts)
        binding.contactLocation.setAdapter(adapter)

        // Muestra el menú desplegable al hacer clic
        binding.contactLocation.setOnClickListener {
            binding.contactLocation.showDropDown()
        }

        // Maneja eventos de selección
        binding.contactLocation.setOnItemClickListener { parent, view, position, id ->
            val contactoSeleccionado = adapter.getItem(position)
            val apodoSeleccionado = contactoSeleccionado?.nickname
            val nombreSeleccionado = contactoSeleccionado?.fullName ?: ""
            val numeroTelefono = adapter.getPhoneNumberFromContact(nombreSeleccionado)

            binding.contactLocation.setText(nombreSeleccionado)

            if (numeroTelefono != null) {
                obtenerInfoUsuarioFirebase(numeroTelefono, apodoSeleccionado, nombreSeleccionado)
            }
        }

        binding.searchIcon.setOnClickListener {
            val locationName = binding.contactLocation.text.toString().trim()

            if (locationName.isNotEmpty()) {
                val geocoder = Geocoder(requireContext())
                Log.d("geo", "geo: $geocoder")

                try {
                    val addresses = geocoder.getFromLocationName(locationName, 1)

                    if (addresses != null && addresses.isNotEmpty()) {
                        val address = addresses[0]
                        val nombre = address.featureName
                        val direccion = address.getAddressLine(0)
                        val latitud = address.latitude
                        val longitud = address.longitude
                        val photo = ""

                        setupButtons(direccion, nombre, latitud, longitud, photo)
                    } else {
                        Toast.makeText(requireContext(), "Lugar no encontrado", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Error al obtener la dirección", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Por favor, ingresa un lugar", Toast.LENGTH_SHORT).show()
            }
        }

    }

    //Obtener datos del usuario destino para generar la ruta en firebase
    private fun obtenerInfoUsuarioFirebase(numeroTelefono: String, apodoSeleccionado: String?, nombreSeleccionado: String) {
        val database = FirebaseDatabase.getInstance().reference
        val usuariosRef = database.child("usuarios")


        usuariosRef.orderByChild("celular").equalTo(numeroTelefono).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    val uid = dataSnapshot.children.firstOrNull()?.key
                    val firstChild = dataSnapshot.children.firstOrNull()
                    val photo = firstChild?.child("fotoPerfilUrl")?.getValue(String::class.java)
                    Log.d("homeCoord", "firstchild: $firstChild, phto: $photo")


                    if (uid != null) {
                        val usersRef = database.child("users")
                        usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(usersSnapshot: DataSnapshot) {
                                for (userSnapshot in usersSnapshot.children) {
                                    if (userSnapshot.key == uid) {
                                        val locationSnapshot = userSnapshot.child("location")
                                        val latitude = locationSnapshot.child("latitude").getValue(Double::class.java)
                                        val longitude = locationSnapshot.child("longitude").getValue(Double::class.java)

                                        if (latitude != null && longitude != null) {
                                            Log.d("homeCoord", "lat: $latitude, long: $longitude, foto: $photo")
                                            setupButtons(nombreSeleccionado, apodoSeleccionado, latitude, longitude,photo)
                                        } else {
                                            Log.e("HomeFragment", "No se encontraron coordenadas para el usuario.")
                                        }
                                        return
                                    }
                                }
                                Log.e("HomeFragment", "Usuario no encontrado en el nodo 'users'.")
                            }
                            override fun onCancelled(databaseError: DatabaseError) {
                                Log.e("HomeFragment", "Error al acceder a 'users': ${databaseError.message}")
                            }
                        })
                    } else {
                        Log.e("HomeFragment", "Usuario no encontrado en Firebase.")
                    }
                } else {
                    Log.e("HomeFragment", "Número de celular no encontrado.")
                }
            }
            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("HomeFragment", "Error al acceder a Firebase: ${databaseError.message}")
            }
        })
    }

    //Envío de datos para generar la ruta
    private fun setupButtons(name: String?, nickname: String?, latitude: Double, longitude: Double, photo: String?) {
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
                            putExtra("photo", photo)
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
                            putExtra("photo", photo)
                        }
                        startActivity(intent)
                    }
                }
            } else {
                Toast.makeText(requireContext(), "Permiso de ubicación necesario para continuar.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Detener actualizaciones de ubicación para evitar uso innecesario de recursos
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}

