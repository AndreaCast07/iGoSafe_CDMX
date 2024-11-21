package com.icm.igosafeapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.squareup.picasso.Picasso
import org.json.JSONObject

class ruta_vehicular : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var iniciar: Button
    private lateinit var nombre: TextView
    private lateinit var apodo: TextView

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var userMarker: Marker? = null
    private lateinit var destinationMarker: Marker
    private lateinit var locationCallback: LocationCallback
    private var destinationLatLng: LatLng? = null

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var storage: FirebaseStorage

    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ruta_vehicular)

        iniciar = findViewById(R.id.iniciarViajev)
        nombre = findViewById(R.id.textViewNombrev)
        apodo = findViewById(R.id.Contactov)

        val nombreUsuario = intent.getStringExtra("name") ?: "Nombre no disponible"
        val apodoUsuario = intent.getStringExtra("nickname") ?: "Apodo no disponible"

        nombre.text = nombreUsuario
        apodo.text = apodoUsuario

        auth = FirebaseAuth.getInstance()
        Picasso.get().setIndicatorsEnabled(true)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        iniciar.setOnClickListener {
            val userLocation = userMarker?.position
            val destinationLocation = destinationMarker.position
            Log.e("RutaPeatonal", "enviando: $userLocation")

            val intent = Intent(this, recorrido_vehicular::class.java).apply {
                putExtra("startLat", userLocation?.latitude)
                putExtra("startLong", userLocation?.longitude)
                putExtra("endLat", destinationLocation.latitude)
                putExtra("endLong", destinationLocation.longitude)
            }
            startActivity(intent)
        }

        obtenerFotoPerfil()

    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomGesturesEnabled = true
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.standard))
    }

    //Obtener foto del usuario
    private fun obtenerFotoPerfil() {
        val userUid = FirebaseAuth.getInstance().currentUser?.uid

        if (userUid != null) {
            val userRef = FirebaseDatabase.getInstance().getReference("usuarios").child(userUid)

            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val photoUrl = snapshot.child("fotoPerfilUrl").getValue(String::class.java)
                    if (photoUrl != null) {
                        Log.d("RutaPeatonal", "URL de la foto de perfil: $photoUrl")
                        consultarUbicacionUsuario(photoUrl)
                    } else {
                        Log.e("RutaPeatonal", "No se encontró la URL de la foto")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("RutaPeatonal", "Error al acceder a los datos del usuario: ${error.message}")
                }
            })
        }
    }

    //Obtener ubicación constantemente del usuario
    private fun consultarUbicacionUsuario(photoUrl: String) {
        val userUid = FirebaseAuth.getInstance().currentUser?.uid
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(userUid!!).child("location")

        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val latitude = snapshot.child("latitude").getValue(Double::class.java)
                val longitude = snapshot.child("longitude").getValue(Double::class.java)
                Log.d("RutaPeatonal", "Latitud: $latitude, Longitud: $longitude")

                if (latitude != null && longitude != null) {
                    val userLocation = LatLng(latitude, longitude)
                    updateUserMarker(userLocation, photoUrl)

                    // Asumimos que la ubicación de destino se pasa desde los extras
                    destinationLatLng = LatLng(intent.getDoubleExtra("latitude", 0.0), intent.getDoubleExtra("longitude", 0.0))
                    destinationLatLng?.let { createDestinationMarker(it) }

                    realizarTransicionZoom(userLocation, destinationLatLng!!)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("RutaPeatonal", "Error al consultar ubicación: ${error.message}")
            }
        })
    }


    //Adaptando el marcador del usuario
    private fun updateUserMarker(location: LatLng, photoUrl: String) {
        Log.d("RutaPeatonal", "Cargando imagen para el marcador: $photoUrl")
        userMarker?.remove()
        Thread {
            try {
                val bitmap = Picasso.get()
                    .load(photoUrl)
                    .placeholder(R.drawable.photo_original_user)
                    .error(R.drawable.ic_person)
                    .transform(CircleTransform(90, borderColor = Color.BLUE, borderWidth = 8f))
                    .get()

                runOnUiThread {
                    userMarker = mMap.addMarker(MarkerOptions().position(location).icon(
                        BitmapDescriptorFactory.fromBitmap(bitmap)))!!
                    //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))
                }
            } catch (e: Exception) {
                Log.e("RutaPeatonal", "Error al cargar la imagen: ${e.message}")
            }
        }.start()
    }

    private fun createDestinationMarker(location: LatLng) {
        val photo = intent.getStringExtra("photo") ?: ""
        Thread {
            try {
                if (photo.isNullOrEmpty()) {
                    runOnUiThread {
                        destinationMarker = mMap.addMarker(
                            MarkerOptions().position(location)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                        )!!
                        //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))
                    }
                } else {
                    // Si hay una URL, cargar la imagen con Picasso
                    val bitmap = Picasso.get()
                        .load(photo)
                        .placeholder(R.drawable.photo_original_user)
                        .error(R.drawable.ic_person)
                        .transform(CircleTransform(90, borderColor = Color.RED, borderWidth = 8f))
                        .get()

                    runOnUiThread {
                        destinationMarker = mMap.addMarker(
                            MarkerOptions().position(location)
                                .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
                        )!!
                        //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))
                    }
                }
            } catch (e: Exception) {
                Log.e("RutaPeatonal", "Error al cargar la imagen de destino: ${e.message}")
            }
        }.start()
    }


    // Realiza la transición de zoom entre los dos marcadores
    private fun realizarTransicionZoom(userLocation: LatLng, destinationLocation: LatLng) {
        val handler = Handler(Looper.getMainLooper())

        handler.postDelayed({
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 18f))
        }, 0)

        handler.postDelayed({
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(destinationLocation, 18f))
        }, 3000)

        handler.postDelayed({
            val builder = LatLngBounds.Builder()
            builder.include(userLocation)
            builder.include(destinationLocation)
            val bounds = builder.build()
            val padding = 140
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
        }, 6000)
    }

}

