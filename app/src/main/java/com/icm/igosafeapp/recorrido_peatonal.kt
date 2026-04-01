package com.icm.igosafeapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatRatingBar
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.PolyUtil
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class recorrido_peatonal : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var terminar: Button
    private lateinit var mMap: GoogleMap
    private lateinit var progressBar: ProgressBar
    private lateinit var textoRuta: TextView
    private lateinit var calificacionText: TextView
    private lateinit var startsCalificacion: AppCompatRatingBar
    private var startLocation = LatLng(0.0, 0.0)
    private var endLocation = LatLng(0.0, 0.0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recorrido_peatonal)

        progressBar = findViewById(R.id.progressBar)
        textoRuta = findViewById(R.id.textoRuta)
        terminar = findViewById(R.id.finalizar)
        calificacionText = findViewById(R.id.calificacionText)
        startsCalificacion = findViewById(R.id.ratingBar)

        val startLatitude = intent.getDoubleExtra("startLat", 0.0)
        val startLongitude = intent.getDoubleExtra("startLong", 0.0)
        val endLatitude = intent.getDoubleExtra("endLat", 0.0)
        val endLongitude = intent.getDoubleExtra("endLong", 0.0)

        startLocation = LatLng(startLatitude, startLongitude)
        endLocation = LatLng(endLatitude, endLongitude)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        terminar.setOnClickListener {
            val intent = Intent(this, review_ruta::class.java)
            startActivity(intent)
        }

        progressBar.visibility = View.VISIBLE
        textoRuta.text = "Generando ruta peatonal..."
        calificacionText.visibility = View.GONE
        startsCalificacion.visibility = View.GONE
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.standard))

        if (startLocation.latitude == 0.0 || endLocation.latitude == 0.0) {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Ubicaciones no válidas para ruta peatonal", Toast.LENGTH_LONG).show()
            return
        }

        // Marcadores iniciales
        mMap.addMarker(MarkerOptions().position(startLocation).title("Inicio"))
        mMap.addMarker(MarkerOptions().position(endLocation).title("Destino"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startLocation, 15f))

        getOSRMDistance(startLocation, endLocation) { distance, duration, routeCoordinates ->
            runOnUiThread {
                if (!isFinishing) {
                    progressBar.visibility = View.GONE
                    if (routeCoordinates.isNotEmpty()) {
                        drawRoute(routeCoordinates)
                        
                        val distanciaKm = distance / 1000.0
                        val tiempoMinutos = duration / 60.0

                        textoRuta.text = "Distancia: ${"%.2f".format(distanciaKm)} km | Tiempo est.: ${"%.2f".format(tiempoMinutos)} min"
                    } else {
                        Toast.makeText(this, "No se pudo obtener la geometría de la ruta peatonal", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun drawRoute(routeCoordinates: List<LatLng>) {
        val polylineOptions = PolylineOptions()
            .addAll(routeCoordinates)
            .color(Color.GREEN)
            .width(14f)
            .geodesic(true)
        
        mMap.addPolyline(polylineOptions)

        val builder = LatLngBounds.Builder()
        routeCoordinates.forEach { builder.include(it) }
        try {
            val bounds = builder.build()
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
        } catch (e: Exception) {
            Log.e("RecorridoPeatonal", "Error set bounds: ${e.message}")
        }
    }

    private fun getOSRMDistance(start: LatLng, end: LatLng, callback: (Double, Double, List<LatLng>) -> Unit) {
        // Para peatonal DEBE ser openstreetmap.de ya que project-osrm.org usualmente es solo car
        val urlStr = "https://routing.openstreetmap.de/routed-foot/route/v1/foot/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=polyline&steps=false"
        
        Thread {
            var success = false
            var attempts = 0
            val maxAttempts = 3

            while (attempts < maxAttempts && !success) {
                attempts++
                var connection: HttpsURLConnection? = null
                try {
                    val url = URL(urlStr)
                    connection = url.openConnection() as HttpsURLConnection
                    connection.setRequestProperty("User-Agent", "iGoSafeApp/1.0 (Android; Mobile)")
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("Connection", "close")
                    connection.connectTimeout = 20000
                    connection.readTimeout = 20000

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val response = connection.inputStream.use { it.bufferedReader().readText() }
                        val jsonResponse = JSONObject(response)
                        val code = jsonResponse.optString("code")
                        
                        if (code == "Ok") {
                            val routes = jsonResponse.optJSONArray("routes")
                            if (routes != null && routes.length() > 0) {
                                val route = routes.getJSONObject(0)
                                val distance = route.optDouble("distance", 0.0)
                                val duration = route.optDouble("duration", 0.0)
                                val geometry = route.optString("geometry", "")
                                
                                if (geometry.isNotEmpty()) {
                                    val points = PolyUtil.decode(geometry)
                                    callback(distance, duration, points)
                                    success = true
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (attempts < maxAttempts) Thread.sleep(1000)
                } finally {
                    connection?.disconnect()
                }
            }
            if (!success) {
                callback(0.0, 0.0, emptyList())
            }
        }.start()
    }
}
