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

class recorrido_vehicular : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var terminar: Button
    private lateinit var mMap: GoogleMap
    private lateinit var progressBar: ProgressBar
    private lateinit var textoRuta: TextView
    private var startLocation = LatLng(0.0, 0.0)
    private var endLocation = LatLng(0.0, 0.0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recorrido_vehicular)

        progressBar = findViewById(R.id.progressBar)
        textoRuta = findViewById(R.id.textoRuta)
        terminar = findViewById(R.id.finalizar)

        startLocation = LatLng(intent.getDoubleExtra("startLat", 0.0), intent.getDoubleExtra("startLong", 0.0))
        endLocation = LatLng(intent.getDoubleExtra("endLat", 0.0), intent.getDoubleExtra("endLong", 0.0))

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        terminar.setOnClickListener {
            startActivity(Intent(this, review_ruta::class.java))
            finish()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.standard))

        if (startLocation.latitude == 0.0) {
            progressBar.visibility = View.GONE
            textoRuta.text = "Error: Ubicaciones vacías"
            return
        }

        mMap.addMarker(MarkerOptions().position(startLocation).title("Inicio"))
        mMap.addMarker(MarkerOptions().position(endLocation).title("Destino"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startLocation, 14f))

        generarRuta()
    }

    private fun generarRuta() {
        progressBar.visibility = View.VISIBLE
        textoRuta.text = "Conectando con el servidor..."

        getOSRMDistance(startLocation, endLocation) { distance, duration, routeCoordinates ->
            runOnUiThread {
                if (!isFinishing) {
                    progressBar.visibility = View.GONE
                    if (routeCoordinates.isNotEmpty()) {
                        drawRoute(routeCoordinates)
                        val km = distance / 1000.0
                        val min = duration / 60.0
                        textoRuta.text = "Vehicular: ${"%.2f".format(km)} km | ${"%.1f".format(min)} min"
                    } else {
                        textoRuta.text = "Fallo de conexión tras varios intentos. Reintenta."
                        Toast.makeText(this, "Servidor lento, intenta de nuevo.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun drawRoute(routeCoordinates: List<LatLng>) {
        mMap.addPolyline(PolylineOptions().addAll(routeCoordinates).color(Color.BLUE).width(14f))
        val builder = LatLngBounds.Builder()
        routeCoordinates.forEach { builder.include(it) }
        try { mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 150)) } catch (e: Exception) {}
    }

    private fun getOSRMDistance(start: LatLng, end: LatLng, callback: (Double, Double, List<LatLng>) -> Unit) {
        val urlStr = "https://routing.openstreetmap.de/routed-car/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=polyline&steps=false"
        
        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            var success = false
            var attempts = 0
            while (attempts < 3 && !success) {
                attempts++
                try {
                    val url = URL(urlStr)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", "iGoSafeApp/1.2 (Android Mobile Device)")
                    conn.setRequestProperty("Connection", "close")
                    conn.connectTimeout = 60000
                    conn.readTimeout = 60000
                    
                    if (conn.responseCode == 200) {
                        val response = conn.inputStream.bufferedReader().readText()
                        val json = JSONObject(response)
                        if (json.optString("code") == "Ok") {
                            val route = json.getJSONArray("routes").getJSONObject(0)
                            callback(route.getDouble("distance"), route.getDouble("duration"), PolyUtil.decode(route.getString("geometry")))
                            success = true
                        }
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.e("RouteRequest", "Timeout en intento $attempts: ${e.message}")
                    if (attempts < 3) try { Thread.sleep(1000) } catch (ie: Exception) {}
                }
            }
            if (!success) callback(0.0, 0.0, emptyList())
        }.start()
    }
}
