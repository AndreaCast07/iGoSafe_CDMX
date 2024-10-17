package com.icm.igosafeapp

//import android.R
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
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
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions


class ruta_vehicular : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var iniciar: Button
    private lateinit var textDistancia: TextView
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ruta_vehicular)

        iniciar = findViewById(R.id.iniciarViaje2)
        textDistancia = findViewById((R.id.textDistancia2))

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map2) as SupportMapFragment
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