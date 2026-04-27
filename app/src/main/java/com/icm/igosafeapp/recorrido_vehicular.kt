package com.icm.igosafeapp

import android.content.Intent
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.PolyUtil
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.Polyline
import java.util.ArrayList

class recorrido_vehicular : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var terminar: Button
    private lateinit var btnFastest: Button
    private lateinit var btnSafest: Button
    private lateinit var mMap: GoogleMap
    private lateinit var progressBar: ProgressBar
    private lateinit var textoRuta: TextView
    private lateinit var tipoRutaTitulo: TextView
    private lateinit var diffTiempo: TextView
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var userMarker: com.google.android.gms.maps.model.Marker? = null
    private var walkedPolyline: Polyline? = null
    private val walkedPoints = mutableListOf<LatLng>()

    private var startLocation = LatLng(0.0, 0.0)
    private var endLocation = LatLng(0.0, 0.0)
    private var fastestDuration: Double = 0.0
    
    @Volatile
    private var isActivityActive = true
    private val mainHandler = Handler(Looper.getMainLooper())

    private val chapultepecPolygon = listOf(
        LatLng(19.4230, -99.1745), LatLng(19.4320, -99.1820),
        LatLng(19.4285, -99.1910), LatLng(19.4220, -99.2020),
        LatLng(19.4100, -99.2050), LatLng(19.4050, -99.1950),
        LatLng(19.4070, -99.1850), LatLng(19.4130, -99.1760)
    )

    private val primaryAvenues = listOf(
        listOf(LatLng(19.4325, -99.1545), LatLng(19.4270, -99.1675), LatLng(19.4235, -99.1755), LatLng(19.4210, -99.1930)), // Reforma
        listOf(LatLng(19.4208, -99.1762), LatLng(19.4115, -99.1760), LatLng(19.4085, -99.1910), LatLng(19.4042, -99.2025)), // Constituyentes
        listOf(LatLng(19.4440, -99.1700), LatLng(19.4150, -99.1762), LatLng(19.4000, -99.1850)), // Circuito Interior
        listOf(LatLng(19.4300, -99.1330), LatLng(19.4100, -99.1350), LatLng(19.3900, -99.1370)), // Tlalpan
        listOf(LatLng(19.4200, -99.1600), LatLng(19.4150, -99.1650), LatLng(19.4100, -99.1780))  // Michoacán
    )

    data class SafetyFeature(val location: LatLng, val title: String, val type: Int)

    private var isFirstLocationUpdate = true
    private var isRouteDrawn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recorrido_vehicular)
        isActivityActive = true
        progressBar = findViewById(R.id.progressBar)
        textoRuta = findViewById(R.id.textoRuta)
        tipoRutaTitulo = findViewById(R.id.tipoRutaTitulo)
        diffTiempo = findViewById(R.id.diffTiempo)
        terminar = findViewById(R.id.finalizar)
        btnFastest = findViewById(R.id.btnFastest)
        btnSafest = findViewById(R.id.btnSafest)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationUpdates()

        startLocation = LatLng(intent.getDoubleExtra("startLat", 0.0), intent.getDoubleExtra("startLong", 0.0))
        endLocation = LatLng(intent.getDoubleExtra("endLat", 0.0), intent.getDoubleExtra("endLong", 0.0))
        
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        
        terminar.setOnClickListener {
            isActivityActive = false
            fusedLocationClient.removeLocationUpdates(locationCallback)
            
            progressBar.visibility = View.VISIBLE
            Thread {
                // Pasar barrios recorridos a la reseña en segundo plano
                val barriosRecorridos = mutableSetOf<String>()
                walkedPoints.forEach { RiskManager.getHexAddress(it)?.let { addr -> barriosRecorridos.add(addr) } }
                
                runOnUiThread {
                    if (!isFinishing) {
                        val intent = Intent(this, review_ruta::class.java).apply {
                            putStringArrayListExtra("barriosPorRuta", ArrayList(barriosRecorridos.toList()))
                        }
                        startActivity(intent)
                        finish()
                    }
                }
            }.start()
        }
        btnFastest.setOnClickListener { generarRutaVehicular(false) }
        btnSafest.setOnClickListener { generarRutaVehicular(true) }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.standard))
        mMap.addMarker(MarkerOptions().position(startLocation).title("Inicio"))
        mMap.addMarker(MarkerOptions().position(endLocation).title("Destino"))
        
        // Mover cámara al inicio con un zoom inicial decente
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startLocation, 15f))

        // Primero calculamos la más rápida para tener la base de tiempo
        Thread {
            val res = fetchOSRMRoute(listOf(startLocation, endLocation))
            if (res != null) fastestDuration = res.third
            runOnUiThread { generarRutaVehicular(true) }
        }.start()
    }

    private fun generarRutaVehicular(optimizarSeguridad: Boolean) {
        if (!isActivityActive || isFinishing) return
        isRouteDrawn = false // Pausar seguimiento para mostrar la nueva ruta elegida
        progressBar.visibility = View.VISIBLE
        mMap.clear()
        // Eliminado marcador de Inicio para evitar superposición con el icono del auto (Tú)
        mMap.addMarker(MarkerOptions().position(endLocation).title("Destino"))

        Thread {
            try {
                val directRoute = fetchOSRMRoute(listOf(startLocation, endLocation))
                val directPoints = directRoute?.first ?: listOf(startLocation, endLocation)
                val finalWaypoints = mutableListOf<LatLng>()
                finalWaypoints.add(startLocation)

                if (optimizarSeguridad) {
                    val candidates = mutableListOf<LatLng>()
                    for (avenue in primaryAvenues) {
                        if (isAvenueAligned(startLocation, endLocation, avenue)) {
                            val entry = findNearestPoint(startLocation, avenue)
                            val exit = findNearestPoint(endLocation, avenue)
                            candidates.add(entry)
                            candidates.add(exit)
                            break
                        }
                    }

                    if (directPoints.any { PolyUtil.containsLocation(it, chapultepecPolygon, false) }) {
                        val forestCenterLng = -99.185
                        if (endLocation.longitude > forestCenterLng) {
                            candidates.add(LatLng(19.4115, -99.1760)) 
                            candidates.add(LatLng(19.4235, -99.1760)) 
                        } else {
                            candidates.add(LatLng(19.4170, -99.1920)) 
                            candidates.add(LatLng(19.4240, -99.1915)) 
                        }
                    }

                    for (wp in candidates) {
                        if (!PolyUtil.isLocationOnPath(wp, directPoints, false, 50.0)) {
                            finalWaypoints.add(wp)
                        }
                    }
                }

                finalWaypoints.add(endLocation)
                val refinedRoute = fetchOSRMRoute(cleanWaypoints(finalWaypoints))
                
                runOnUiThread {
                    if (!isActivityActive || isFinishing) return@runOnUiThread
                    progressBar.visibility = View.GONE
                    if (refinedRoute != null) {
                        startTracking()
                        val safetyOnPath = identifySafetyOnPath(refinedRoute.first, 100.0)
                        val safetyCounts = safetyOnPath.groupBy { it.type }.mapValues { it.value.size }
                        val summary = "C5: ${safetyCounts[2] ?: 0} | Patrullas: ${safetyCounts[4] ?: 0}"
                        
                        // Análisis ISM para el etiquetado inteligente vehicular
                        val analysis = RiskManager.analyzeRoute(refinedRoute.first)

                        if (!isFinishing) {
                            drawEverything(refinedRoute.first, safetyOnPath)
                            
                            // Explicación de criterios al tocar la tarjeta
                            findViewById<View>(R.id.cardInfo).setOnClickListener {
                                showSafetyExplanation(analysis)
                            }

                            tipoRutaTitulo.text = if (optimizarSeguridad) analysis.label else "Ruta Vehicular más Rápida"
                            textoRuta.text = "Cámaras C5: ${analysis.cameraCount} | Patrullas: ${analysis.pathCount}\nRiesgo: ${analysis.riskLevel} | Tiempo: ${formatTiempo(refinedRoute.third)}"
                            
                            val diff = ((refinedRoute.third - fastestDuration) / 60).toInt()
                            if (optimizarSeguridad && diff > 0) {
                                diffTiempo.text = "+$diff min por zonas de mayor vigilancia"
                                diffTiempo.setTextColor(ContextCompat.getColor(this, R.color.naranja))
                            } else if (!optimizarSeguridad) {
                                diffTiempo.text = "Trayecto con tiempo mínimo"
                                diffTiempo.setTextColor(ContextCompat.getColor(this, R.color.azul3))
                            } else {
                                diffTiempo.text = "Tiempo óptimo igual a la ruta rápida"
                                diffTiempo.setTextColor(ContextCompat.getColor(this, R.color.verde))
                            }

                            if (safetyOnPath.isEmpty()) {
                                AlertDialog.Builder(this)
                                    .setTitle("¡Atención!")
                                    .setMessage("No se detectaron elementos de seguridad (Cámaras o Patrullaje) en este trayecto. Proceda con precaución.")
                                    .setPositiveButton("Entendido", null)
                                    .show()
                            }
                        }
                    }
                }
            } catch (e: Exception) { 
                Log.e("Vehicular", "Error: ${e.message}")
                runOnUiThread { progressBar.visibility = View.GONE } 
            }
        }.start()
    }

    private fun isAvenueAligned(start: LatLng, end: LatLng, avenue: List<LatLng>): Boolean {
        val entry = findNearestPoint(start, avenue)
        val distToEntry = FloatArray(1)
        Location.distanceBetween(start.latitude, start.longitude, entry.latitude, entry.longitude, distToEntry)
        return distToEntry[0] < 800
    }

    private fun findNearestPoint(point: LatLng, avenue: List<LatLng>): LatLng {
        return avenue.minByOrNull { 
            val res = FloatArray(1)
            Location.distanceBetween(point.latitude, point.longitude, it.latitude, it.longitude, res)
            res[0]
        } ?: avenue[0]
    }

    private fun cleanWaypoints(points: List<LatLng>): List<LatLng> {
        if (points.size < 2) return points
        val cleaned = mutableListOf<LatLng>()
        cleaned.add(points.first())
        for (i in 1 until points.size - 1) {
            val dist = FloatArray(1)
            Location.distanceBetween(cleaned.last().latitude, cleaned.last().longitude, points[i].latitude, points[i].longitude, dist)
            if (dist[0] > 100) cleaned.add(points[i])
        }
        val distToEnd = FloatArray(1)
        Location.distanceBetween(cleaned.last().latitude, cleaned.last().longitude, points.last().latitude, points.last().longitude, distToEnd)
        if (distToEnd[0] < 10 && cleaned.size > 1) cleaned.removeAt(cleaned.size - 1)
        cleaned.add(points.last())
        return cleaned
    }

    private fun fetchOSRMRoute(waypoints: List<LatLng>): Triple<List<LatLng>, Double, Double>? {
        if (waypoints.size < 2) return null
        try {
            val coords = waypoints.joinToString(";") { "${it.longitude},${it.latitude}" }
            // Restauramos radiuses=unlimited para asegurar conexión entre puntos
            val radiuses = waypoints.map { "unlimited" }.joinToString(";")
            val urlStr = "https://routing.openstreetmap.de/routed-car/route/v1/driving/$coords?overview=full&geometries=polyline&continue_straight=true&radiuses=$radiuses"
            val conn = URL(urlStr).openConnection() as HttpsURLConnection
            conn.setRequestProperty("User-Agent", "iGoSafeApp/1.0")
            conn.connectTimeout = 15000
            if (conn.responseCode == 200) {
                val res = conn.inputStream.bufferedReader().readText()
                val routes = JSONObject(res).optJSONArray("routes")
                if (routes != null && routes.length() > 0) {
                    val route = routes.getJSONObject(0)
                    return Triple(PolyUtil.decode(route.getString("geometry")), route.getDouble("distance"), route.getDouble("duration"))
                }
            }
        } catch (e: Exception) { Log.e("OSRM_Vehicular", "Error: ${e.message}") }
        return null
    }

    private fun identifySafetyOnPath(path: List<LatLng>, bufferMeters: Double): List<SafetyFeature> {
        val list = mutableListOf<SafetyFeature>()
        // Optimización extrema para evitar cierres de la app
        // No leemos archivos JSON dentro de este bucle, usamos RiskManager
        RiskManager.getCamerasOnPath(path).forEach { 
            list.add(SafetyFeature(it, "Cámara C5", 2))
        }
        return list
    }

    private fun getIconFromVector(resId: Int, tintColor: Int, size: Int): BitmapDescriptor {
        val vectorDrawable = ContextCompat.getDrawable(this, resId)!!
        vectorDrawable.setBounds(0, 0, size, size)
        androidx.core.graphics.drawable.DrawableCompat.setTint(vectorDrawable, tintColor)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun getResizedBitmap(resId: Int, size: Int): BitmapDescriptor {
        val imageBitmap = android.graphics.BitmapFactory.decodeResource(resources, resId)
        val resizedBitmap = Bitmap.createScaledBitmap(imageBitmap, size, size, false)
        return BitmapDescriptorFactory.fromBitmap(resizedBitmap)
    }

    private fun drawEverything(points: List<LatLng>, features: List<SafetyFeature>) {
        if (points.size < 2) return
        // Ruta vehicular más definida
        mMap.addPolyline(PolylineOptions()
            .addAll(points)
            .color(Color.parseColor("#3498DB"))
            .width(18f)
            .zIndex(1f))
            
        val cameraIcon = getIconFromVector(R.drawable.ic_camera, Color.YELLOW, 70)
        val patrolIcon = getResizedBitmap(R.drawable.icon_auto, 90)
        for (f in features) {
            val icon = if (f.type == 2) cameraIcon else patrolIcon
            mMap.addMarker(MarkerOptions().position(f.location).title(f.title).icon(icon).anchor(0.5f, 0.5f))
        }
        val bounds = LatLngBounds.Builder()
        points.forEach { bounds.include(it) }
        try { 
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 150), object : GoogleMap.CancelableCallback {
                override fun onFinish() { 
                    isRouteDrawn = true 
                    // Acercamiento inmediato
                    val currentPos = userMarker?.position ?: startLocation
                    val navPos = com.google.android.gms.maps.model.CameraPosition.Builder()
                        .target(currentPos)
                        .zoom(17.5f)
                        .tilt(45f)
                        .build()
                    mMap.animateCamera(CameraUpdateFactory.newCameraPosition(navPos))
                }
                override fun onCancel() { isRouteDrawn = true }
            })
        } catch (e: Exception) {
            isRouteDrawn = true
        }
    }

    private fun setupLocationUpdates() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                val currentLatLng = LatLng(location.latitude, location.longitude)
                actualizarPosicionVehiculo(currentLatLng)
            }
        }
    }

    private fun actualizarPosicionVehiculo(pos: LatLng) {
        if (!::mMap.isInitialized) return

        // Ocultar botones de selección solo después de avanzar 30 metros
        val startPos = startLocation
        val distanceResults = FloatArray(1)
        Location.distanceBetween(startPos.latitude, startPos.longitude, pos.latitude, pos.longitude, distanceResults)
        
        if (distanceResults[0] > 30.0) {
            findViewById<View>(R.id.routeOptions).visibility = View.GONE
        }

        if (userMarker == null) {
            userMarker = mMap.addMarker(MarkerOptions()
                .position(pos)
                .icon(getResizedBitmap(R.drawable.icon_auto, 80))
                .anchor(0.5f, 0.5f))
        } else {
            userMarker?.position = pos
        }

        if (walkedPoints.isEmpty() || calcularDistancia(walkedPoints.last(), pos) > 5.0) {
            walkedPoints.add(pos)
            actualizarLineaRecorrida()
        }
        
        // Centrar cámara con perspectiva de conducción (más zoom e inclinación)
        if (isRouteDrawn) {
            val cameraPosition = com.google.android.gms.maps.model.CameraPosition.Builder()
                .target(pos)
                .zoom(17.5f)
                .tilt(45f)
                .bearing(mMap.cameraPosition.bearing)
                .build()
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        } else if (isFirstLocationUpdate) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f))
            isFirstLocationUpdate = false
        }
    }

    private fun calcularDistancia(p1: LatLng, p2: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results)
        return results[0]
    }

    private fun actualizarLineaRecorrida() {
        walkedPolyline?.remove()
        walkedPolyline = mMap.addPolyline(PolylineOptions()
            .addAll(walkedPoints)
            .color(Color.argb(90, 100, 100, 100))
            .width(8f)
            .zIndex(5f))
    }

    private fun startTracking() {
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build()
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {}
    }

    private fun showSafetyExplanation(analysis: RiskManager.SafetyAnalysis) {
        val msg = """
            Criterios de Seguridad Vehicular:
            
            • Monitoreo C5: Se detectaron ${analysis.cameraCount} cámaras de vigilancia en el trayecto.
            • Patrullaje: Se cruzan ${analysis.pathCount} sectores con vigilancia activa.
            • Riesgo Histórico: La zona tiene un nivel de riesgo ${analysis.riskLevel.lowercase()}.
            • Evaluación: Realizada según infraestructura disponible y horario.
            
            * Recuerda conducir con precaución. Ningún algoritmo sustituye tu atención al volante.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Información de Seguridad")
            .setMessage(msg)
            .setPositiveButton("Entendido", null)
            .show()
    }

    override fun onDestroy() {
        isActivityActive = false
        if (::fusedLocationClient.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun formatTiempo(segundos: Double): String {
        val min = (segundos / 60).toInt()
        return if (min > 59) "${min/60}h ${min%60}min" else "${min}min"
    }
}
