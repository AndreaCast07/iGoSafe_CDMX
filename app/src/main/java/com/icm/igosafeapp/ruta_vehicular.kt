package com.icm.igosafeapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
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
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import org.json.JSONObject



class ruta_vehicular : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var iniciar: Button
    private lateinit var textDistancia: TextView
    private lateinit var nombre: TextView
    private lateinit var apodo: TextView

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var destinationLatLng: LatLng? = null

    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ruta_vehicular)

        iniciar = findViewById(R.id.iniciarViaje2)
        textDistancia = findViewById(R.id.textDistancia2)
        nombre = findViewById(R.id.textViewNombre2)
        apodo = findViewById(R.id.Contacto2)

        val name = intent.getStringExtra("name")
        val nickname = intent.getStringExtra("nickname")
        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)

        nombre.text = name ?: "Sin nombre"
        apodo.text = nickname ?: "Sin apodo"

        destinationLatLng = LatLng(latitude, longitude)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map2) as SupportMapFragment
        mapFragment.getMapAsync(this)

        iniciar.setOnClickListener {
            if (iniciar.text == "Iniciar Viaje") {
                // Mostrar distancia
                textDistancia.visibility = View.VISIBLE
                iniciar.text = "Finalizar"

                obtenerUbicacionActual()
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

        /*destinationLatLng?.let {
            mMap.addMarker(MarkerOptions().position(it).title("Destino"))
        }*/
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

        destinationLatLng?.let { destination ->

        }
    }

    private fun obtenerOSRMUrl(origen: LatLng, destino: LatLng, profile: String): String {
        val origenCoords = "${origen.longitude},${origen.latitude}"
        val destinoCoords = "${destino.longitude},${destino.latitude}"
        return "https://router.project-osrm.org/route/v1/car/$origenCoords;$destinoCoords?overview=full&geometries=geojson"
    }

    private fun decodificarRuta(jsonResponse: String): List<LatLng> {
        val jsonObject = JSONObject(jsonResponse)
        val routes = jsonObject.getJSONArray("routes")
        val ruta = routes.getJSONObject(0)
        val geometry = ruta.getJSONObject("geometry")
        val coordinates = geometry.getJSONArray("coordinates")

        val puntosRuta = mutableListOf<LatLng>()

        for (i in 0 until coordinates.length()) {
            val coord = coordinates.getJSONArray(i)
            val lon = coord.getDouble(0)
            val lat = coord.getDouble(1)
            puntosRuta.add(LatLng(lat, lon))
        }

        return puntosRuta
    }

    private fun dibujarRutaEnMapa(rutaCoordenadas: List<LatLng>) {
        val polylineOptions = com.google.android.gms.maps.model.PolylineOptions()
            .addAll(rutaCoordenadas)
            .color(android.graphics.Color.BLUE)
            .width(8f)

        mMap.addPolyline(polylineOptions)
    }

    private fun enfocarRutaCompleta(rutaCoordenadas: List<LatLng>) {
        val boundsBuilder = LatLngBounds.Builder()
        for (punto in rutaCoordenadas) {
            boundsBuilder.include(punto)
        }
        val bounds = boundsBuilder.build()
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100)) // 100 es el padding
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
