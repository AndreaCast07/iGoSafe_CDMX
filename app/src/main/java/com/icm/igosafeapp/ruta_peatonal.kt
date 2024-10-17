package com.icm.igosafeapp
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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
import android.widget.TextView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

class ruta_peatonal : AppCompatActivity(), OnMapReadyCallback {
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
        setContentView(R.layout.activity_ruta_peatonal)

        iniciar = findViewById(R.id.iniciarViaje)
        textDistancia = findViewById(R.id.textDistancia)
        nombre = findViewById(R.id.textViewNombre)
        apodo = findViewById(R.id.Contacto)

        val name = intent.getStringExtra("name")
        val nickname = intent.getStringExtra("nickname")
        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)
        Log.d("RutaPeatonal", "Nombre: $name, Apodo: $nickname, Latitud: $latitude, Longitud: $longitude")

        nombre.text = name ?: "Sin nombre"
        apodo.text = nickname ?: "Sin apodo"

        destinationLatLng = LatLng(latitude, longitude)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
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
        obtenerUbicacionActual2()

        destinationLatLng?.let {
            Log.d("RutaPeatonal", "Añadiendo marcador para el destino: $it")
            mMap.addMarker(MarkerOptions().position(it).title("Destino"))
        } ?: run {
            Log.d("RutaPeatonal", "No se puede añadir marcador, destinationLatLng es null")
        }
    }


    private fun obtenerUbicacionActual2() {
        if (!verificarPermisosUbicacion()) {
            return
        }
        try {
            // Inicia actualizaciones en tiempo real
            val locationRequest = LocationRequest.create().apply {

                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
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
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun verificarPermisosUbicacion(): Boolean {
        return if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
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
            trazarRuta(currentLatLng, destination)  // Traza la ruta
            mostrarDistancia(location, destination)
        }
    }

    private fun mostrarDistancia(location: Location, destino: LatLng) {
        val resultados = FloatArray(1)
        Location.distanceBetween(
            location.latitude,
            location.longitude,
            destino.latitude,
            destino.longitude,
            resultados
        )
        val distanciaKm = resultados[0] / 1000
        textDistancia.text = "Distancia: %.2f km".format(distanciaKm)
    }

    private fun trazarRuta(origen: LatLng, destino: LatLng) {
        val url = obtenerOSRMUrl(origen, destino, "foot") // Modo peatonal
        val client = OkHttpClient()

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    textDistancia.text = "Error al obtener la ruta"
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.body?.let { responseBody ->
                    val jsonResponse = responseBody.string()
                    val rutaCoordenadas = decodificarRuta(jsonResponse)

                    // Dibujar la Polyline en el mapa con la ruta decodificada
                    runOnUiThread {
                        dibujarRutaEnMapa(rutaCoordenadas)

                        // Enfocar la ruta completa
                        enfocarRutaCompleta(rutaCoordenadas)
                    }
                }
            }
        })
    }

    // Nueva función para enfocar la ruta completa
    private fun enfocarRutaCompleta(rutaCoordenadas: List<LatLng>) {
        val boundsBuilder = LatLngBounds.Builder()
        for (punto in rutaCoordenadas) {
            boundsBuilder.include(punto)
        }
        val bounds = boundsBuilder.build()
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100)) // 100 es el padding
    }



    private fun obtenerOSRMUrl(origen: LatLng, destino: LatLng, profile: String): String {
        val origenCoords = "${origen.longitude},${origen.latitude}"
        val destinoCoords = "${destino.longitude},${destino.latitude}"
        return "https://router.project-osrm.org/route/v1/foot/$origenCoords;$destinoCoords?overview=full&geometries=geojson"
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
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

