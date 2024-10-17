package com.icm.igosafeapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.MapStyleOptions

class ruta_peatonal : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var iniciar: Button
    private lateinit var textDistancia: TextView
    private lateinit var nombre: TextView
    private lateinit var apodo: TextView

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ruta_peatonal)

        iniciar = findViewById(R.id.iniciarViaje)
        textDistancia = findViewById(R.id.textDistancia)
        nombre = findViewById(R.id.textViewNombre)
        apodo = findViewById(R.id.Contacto)

        val name = intent.getStringExtra("name")
        val nickname = intent.getStringExtra("nickname")

        nombre.text = name ?: "Sin nombre"
        apodo.text = nickname ?: "Sin apodo"

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        iniciar.setOnClickListener {
            if (iniciar.text == "Iniciar Viaje") {
                // Mostrar distancia
                textDistancia.visibility = View.VISIBLE
                textDistancia.text = "Distancia: X km"

                iniciar.text = "Finalizar"
            } else {
                val intent = Intent(this, review_ruta::class.java)
                startActivity(intent)
                finish()
                }
        }

        // Configura el callback para recibir actualizaciones de la ubicación
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    actualizarUbicacionEnMapa(location)
                }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomGesturesEnabled = true
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.standard))
        obtenerUbicacionActual()
    }

    private fun obtenerUbicacionActual() {
        if (!verificarPermisosUbicacion()) {
            return
        }

        try {
            // Inicia actualizaciones en tiempo real
            val locationRequest = LocationRequest.create().apply {
                interval = 10000 // Actualiza cada 10 segundos
                fastestInterval = 5000 // Intervalo más rápido
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun verificarPermisosUbicacion(): Boolean {
        return if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            false
        } else {
            true
        }
    }

    private fun actualizarUbicacionEnMapa(location: Location) {
        val currentLatLng = LatLng(location.latitude, location.longitude)
        mMap.clear() // Elimina marcadores previos
        mMap.addMarker(MarkerOptions().position(currentLatLng).title("Ubicación Actual"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                obtenerUbicacionActual()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Detén las actualizaciones cuando la actividad no está visible
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
