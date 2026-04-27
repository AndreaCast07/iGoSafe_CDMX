package com.icm.igosafeapp

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.TimeZone
import javax.net.ssl.HttpsURLConnection
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
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

class recorrido_peatonal : AppCompatActivity(), OnMapReadyCallback {
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
    private var fullRoutePoints: List<LatLng> = emptyList()
    private var walkedPolyline: Polyline? = null
    private val walkedPoints = mutableListOf<LatLng>()

    private var startLocation = LatLng(0.0, 0.0)
    private var endLocation = LatLng(0.0, 0.0)
    private var fastestDuration: Double = 0.0
    
    @Volatile
    private var isActivityActive = true
    private val mainHandler = Handler(Looper.getMainLooper())

    data class SafetyFeature(val location: LatLng, val title: String, val type: Int)

    private val chapultepecPolygon = listOf(
        LatLng(19.4244, -99.1755), LatLng(19.4290, -99.1780),
        LatLng(19.4310, -99.1850), LatLng(19.4260, -99.1930),
        LatLng(19.4180, -99.1950), LatLng(19.4100, -99.1910),
        LatLng(19.4110, -99.1800), LatLng(19.4180, -99.1760)
    )

    private val priorityCorridors = listOf(
        listOf(LatLng(19.4325, -99.1545), LatLng(19.4270, -99.1675), LatLng(19.4235, -99.1755), LatLng(19.4210, -99.1930)), // Reforma
        listOf(LatLng(19.4208, -99.1762), LatLng(19.4115, -99.1760), LatLng(19.4085, -99.1910), LatLng(19.4042, -99.2025)), // Constituyentes
        listOf(LatLng(19.4205, -99.1760), LatLng(19.4150, -99.1762), LatLng(19.4115, -99.1760)), // Circuito Interior
        listOf(LatLng(19.4150, -99.1650), LatLng(19.4130, -99.1750), LatLng(19.4110, -99.1780))  // Michoacán
    )

    private var isFirstLocationUpdate = true
    private var isRouteDrawn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recorrido_peatonal)
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
        
        terminar.setOnClickListener { cerrarPantallaLimpia() }
        btnFastest.setOnClickListener { generarRuta(false) }
        btnSafest.setOnClickListener { generarRuta(true) }
    }

    private fun cerrarPantallaLimpia() {
        isActivityActive = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        if (::mMap.isInitialized) mMap.stopAnimation()
        
        progressBar.visibility = View.VISIBLE
        Thread {
            // Calcular barrios recorridos para la reseña en segundo plano
            val barriosRecorridos = identificarBarrios(walkedPoints)
            
            runOnUiThread {
                if (!isFinishing) {
                    val intent = Intent(this, review_ruta::class.java).apply {
                        putStringArrayListExtra("barriosPorRuta", ArrayList(barriosRecorridos))
                    }
                    startActivity(intent)
                    finish()
                }
            }
        }.start()
    }

    private fun identificarBarrios(puntos: List<LatLng>): List<String> {
        val barrios = mutableSetOf<String>()
        // Lógica simplificada: obtener el nombre del hexágono o barrio de los puntos recorridos
        puntos.forEach { 
            RiskManager.getHexAddress(it)?.let { addr -> barrios.add(addr) }
        }
        return barrios.toList()
    }

    private fun setupLocationUpdates() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (!isActivityActive) return
                val location = locationResult.lastLocation ?: return
                val currentLatLng = LatLng(location.latitude, location.longitude)
                
                actualizarPosicionUsuario(currentLatLng)
            }
        }
    }

    private fun actualizarPosicionUsuario(pos: LatLng) {
        if (!::mMap.isInitialized) return

        // Ocultar opciones de ruta solo si ya se alejó significativamente (evitar desaparición por ruido GPS)
        val startPos = startLocation
        val distanceResults = FloatArray(1)
        Location.distanceBetween(startPos.latitude, startPos.longitude, pos.latitude, pos.longitude, distanceResults)
        
        if (distanceResults[0] > 25.0) {
            findViewById<View>(R.id.routeOptions).visibility = View.GONE
        }

        // Actualizar o crear marcador de usuario
        if (userMarker == null) {
            userMarker = mMap.addMarker(MarkerOptions()
                .position(pos)
                .title("Tú")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                .anchor(0.5f, 0.5f))
        } else {
            userMarker?.position = pos
        }

        // Añadir punto a la ruta recorrida si se ha movido significativamente (ej. 2 metros)
        if (walkedPoints.isEmpty()) {
            walkedPoints.add(pos)
        } else {
            val lastPos = walkedPoints.last()
            val results = FloatArray(1)
            Location.distanceBetween(lastPos.latitude, lastPos.longitude, pos.latitude, pos.longitude, results)
            if (results[0] > 2.0) {
                walkedPoints.add(pos)
                actualizarLineaRecorrida()
            }
        }
        
        // Centrar cámara con perspectiva de navegación (inclinación)
        if (isRouteDrawn) {
            val cameraPosition = com.google.android.gms.maps.model.CameraPosition.Builder()
                .target(pos)
                .zoom(18.5f)
                .tilt(55f)
                .bearing(mMap.cameraPosition.bearing) // Mantener orientación actual
                .build()
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        } else if (isFirstLocationUpdate) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 17f))
            isFirstLocationUpdate = false
        }
    }

    private fun actualizarLineaRecorrida() {
        walkedPolyline?.remove()
        walkedPolyline = mMap.addPolyline(PolylineOptions()
            .addAll(walkedPoints)
            .color(Color.argb(100, 100, 100, 100)) // Gris suave y transparente
            .width(8f) // Más delgada para no tapar la ruta principal
            .zIndex(5f))
    }

    private fun startTracking() {
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
                .setMinUpdateDistanceMeters(2f)
                .build()
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("Peatonal", "Error de permisos: ${e.message}")
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.standard))
        mMap.addMarker(MarkerOptions().position(startLocation).title("Inicio"))
        mMap.addMarker(MarkerOptions().position(endLocation).title("Destino"))
        
        // Mover cámara al inicio con un zoom inicial decente
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startLocation, 16f))
        
        // Primero calculamos la más rápida para la base de tiempo
        Thread {
            val res = fetchOSRMRouteDirect(listOf(startLocation, endLocation))
            if (res != null) fastestDuration = res.second
            runOnUiThread { generarRuta(true) }
        }.start()
    }

    private fun fetchOSRMRouteDirect(points: List<LatLng>): Pair<List<LatLng>, Double>? {
        if (points.size < 2) return null
        try {
            val coords = points.joinToString(";") { "${it.longitude},${it.latitude}" }
            val urlStr = "https://routing.openstreetmap.de/routed-foot/route/v1/foot/$coords?overview=full&geometries=polyline&continue_straight=true"
            val conn = URL(urlStr).openConnection() as HttpsURLConnection
            conn.setRequestProperty("User-Agent", "iGoSafeApp/1.5")
            if (conn.responseCode == 200) {
                val res = conn.inputStream.bufferedReader().readText()
                val routes = JSONObject(res).optJSONArray("routes")
                if (routes != null && routes.length() > 0) {
                    val r = routes.getJSONObject(0)
                    return Pair(PolyUtil.decode(r.getString("geometry")), r.getDouble("duration"))
                }
            }
        } catch (e: Exception) {}
        return null
    }

    private fun generarRuta(optimizarSeguridad: Boolean) {
        if (!isActivityActive || isFinishing || !isOnline()) return
        isRouteDrawn = false // Pausar seguimiento para mostrar la nueva ruta
        progressBar.visibility = View.VISIBLE
        mMap.clear()
        // Eliminado el marcador de "Inicio" para evitar que se encime con el icono del usuario (Tú)
        mMap.addMarker(MarkerOptions().position(endLocation).title("Destino"))

        Thread {
            try {
                val directRoute = fetchOSRMRouteDirect(listOf(startLocation, endLocation))
                val directPoints = directRoute?.first ?: listOf(startLocation, endLocation)
                
                val finalWaypoints = mutableListOf<LatLng>()
                finalWaypoints.add(startLocation)

                if (optimizarSeguridad) {
                    val candidates = mutableListOf<LatLng>()
                    var bestCorridor: List<LatLng>? = null
                    var maxAlignment = 0f
                    for (corridor in priorityCorridors) {
                        val alignment = calculateAlignment(startLocation, endLocation, corridor)
                        if (alignment > maxAlignment) {
                            maxAlignment = alignment
                            bestCorridor = corridor
                        }
                    }

                    if (bestCorridor != null && maxAlignment > 0.3) {
                        val entryPoint = findNearestPointOnCorridor(startLocation, bestCorridor)
                        val exitPoint = findNearestPointOnCorridor(endLocation, bestCorridor)
                        candidates.add(entryPoint)
                        candidates.addAll(getIntermediateNodes(entryPoint, exitPoint, bestCorridor))
                        candidates.add(exitPoint)
                    }

                    if (directPoints.any { PolyUtil.containsLocation(it, chapultepecPolygon, false) }) {
                        val forestCenterLng = -99.185
                        if (endLocation.longitude > forestCenterLng) {
                            candidates.add(LatLng(19.4115, -99.1760))
                            candidates.add(LatLng(19.4235, -99.1755))
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
                val finalPoints = fetchOSRMRoute(cleanWaypoints(finalWaypoints))
                val dur = if (optimizarSeguridad) calculateTimeForPoints(finalPoints) else fastestDuration

                mainHandler.post {
                    if (!isActivityActive || isFinishing) return@post
                    progressBar.visibility = View.GONE
                    if (finalPoints.isNotEmpty()) {
                        fullRoutePoints = finalPoints
                        startTracking()
                        val safety = identifySafety(finalPoints)
                        val safetyCounts = safety.groupBy { it.type }.mapValues { it.value.size }
                        val summary = "Cámaras: ${safetyCounts[2] ?: 0} | Senderos: ${safetyCounts[5] ?: 0}"
                        
                        // Análisis ISM para el etiquetado inteligente
                        val analysis = RiskManager.analyzeRoute(finalPoints)

                        if (isActivityActive && !isFinishing) {
                            drawAll(finalPoints, safety)
                            
                            // Configurar explicación al tocar la tarjeta de información
                            findViewById<View>(R.id.cardInfo).setOnClickListener {
                                showSafetyExplanation(analysis)
                            }
                            
                            tipoRutaTitulo.text = if (optimizarSeguridad) analysis.label else "Peatonal más Rápida"
                            textoRuta.text = "Cámaras: ${analysis.cameraCount} | Senderos: ${analysis.pathCount}\nEntorno: ${analysis.riskLevel} | Tiempo: ${formatTiempo(dur)}"
                            
                            val diff = ((dur - fastestDuration) / 60).toInt()
                            if (optimizarSeguridad && diff > 0) {
                                diffTiempo.text = "+$diff min para priorizar tu seguridad"
                                diffTiempo.setTextColor(ContextCompat.getColor(this, R.color.naranja))
                            } else if (!optimizarSeguridad) {
                                diffTiempo.text = "Trayecto con tiempo mínimo"
                                diffTiempo.setTextColor(ContextCompat.getColor(this, R.color.azul3))
                            } else {
                                diffTiempo.text = "Ruta optimizada con seguridad máxima"
                                diffTiempo.setTextColor(ContextCompat.getColor(this, R.color.verde))
                            }

                            if (safety.isEmpty()) {
                                AlertDialog.Builder(this)
                                    .setTitle("Advertencia de Seguridad")
                                    .setMessage("No se detectó vigilancia monitoreada en este trayecto peatonal. Manténgase en zonas iluminadas.")
                                    .setPositiveButton("Entendido", null)
                                    .show()
                            }
                        }
                    }
                }
            } catch (e: Exception) { 
                mainHandler.post { 
                    if (isActivityActive) progressBar.visibility = View.GONE 
                }
            }
        }.start()
    }

    private fun calculateTimeForPoints(points: List<LatLng>): Double {
        // Estimación OSRM simplificada o re-petición si fuera necesario
        return fastestDuration * 1.15 // Aproximación para evitar exceso de peticiones
    }

    private fun calculateAlignment(start: LatLng, end: LatLng, corridor: List<LatLng>): Float {
        val corridorBounds = LatLngBounds.builder()
        corridor.forEach { corridorBounds.include(it) }
        val center = corridorBounds.build().center
        val distToCenter = FloatArray(1)
        Location.distanceBetween(start.latitude, start.longitude, center.latitude, center.longitude, distToCenter)
        return if (distToCenter[0] < 1500) 0.5f else 0f
    }

    private fun findNearestPointOnCorridor(point: LatLng, corridor: List<LatLng>): LatLng {
        return corridor.minByOrNull { 
            val res = FloatArray(1)
            Location.distanceBetween(point.latitude, point.longitude, it.latitude, it.longitude, res)
            res[0]
        } ?: corridor[0]
    }

    private fun getIntermediateNodes(start: LatLng, end: LatLng, corridor: List<LatLng>): List<LatLng> {
        val startIndex = corridor.indexOf(start)
        val endIndex = corridor.indexOf(end)
        if (startIndex == -1 || endIndex == -1 || startIndex == endIndex) return emptyList()
        return if (startIndex < endIndex) corridor.subList(startIndex + 1, endIndex) 
               else corridor.subList(endIndex + 1, startIndex).reversed()
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

    private fun fetchOSRMRoute(points: List<LatLng>): List<LatLng> {
        if (points.size < 2) return emptyList()
        try {
            val coords = points.joinToString(";") { "${it.longitude},${it.latitude}" }
            // Restauramos radiuses=unlimited para asegurar que siempre encuentre ruta, 
            // pero confiamos en cleanWaypoints para la limpieza
            val urlStr = "https://routing.openstreetmap.de/routed-foot/route/v1/foot/$coords?overview=full&geometries=polyline&continue_straight=true&radiuses=${points.map { "unlimited" }.joinToString(";")}"
            val conn = URL(urlStr).openConnection() as HttpsURLConnection
            conn.setRequestProperty("User-Agent", "iGoSafeApp/1.5")
            if (conn.responseCode == 200) {
                val res = conn.inputStream.bufferedReader().readText()
                val routes = JSONObject(res).optJSONArray("routes")
                if (routes != null && routes.length() > 0) return PolyUtil.decode(routes.getJSONObject(0).getString("geometry"))
            }
        } catch (e: Exception) {}
        return emptyList()
    }

    private fun identifySafety(path: List<LatLng>): List<SafetyFeature> {
        val list = mutableListOf<SafetyFeature>()
        // Usar los métodos optimizados de RiskManager para recuperar cámaras y senderos
        RiskManager.getCamerasOnPath(path).forEach { 
            list.add(SafetyFeature(it, "Cámara C5", 2))
        }
        RiskManager.getPathsOnPath(path).forEach { 
            list.add(SafetyFeature(it, "Sendero Seguro", 5))
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

    private fun drawAll(points: List<LatLng>, features: List<SafetyFeature>) {
        if (!isActivityActive || points.size < 2) return
        // Ruta principal más vibrante y gruesa
        mMap.addPolyline(PolylineOptions()
            .addAll(points)
            .color(Color.parseColor("#27AE60"))
            .width(20f)
            .zIndex(1f))
        
        val cameraIcon = getIconFromVector(R.drawable.ic_camera, Color.YELLOW, 70)
        val senderoIcon = getResizedBitmap(R.drawable.icon_peaton, 90)
        val patrolIcon = getResizedBitmap(R.drawable.icon_auto, 90)
        for (f in features) {
            val icon = when(f.type) {
                2 -> cameraIcon 
                4 -> patrolIcon  
                else -> senderoIcon
            }
            mMap.addMarker(MarkerOptions().position(f.location).title(f.title).icon(icon).anchor(0.5f, 0.5f))
        }
        val bounds = LatLngBounds.Builder()
        points.forEach { bounds.include(it) }
        try { 
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 160), object : GoogleMap.CancelableCallback {
                override fun onFinish() { 
                    isRouteDrawn = true 
                    // Acercamiento inmediato a la posición del usuario al terminar de dibujar
                    val currentPos = userMarker?.position ?: startLocation
                    val navPos = com.google.android.gms.maps.model.CameraPosition.Builder()
                        .target(currentPos)
                        .zoom(18.5f)
                        .tilt(55f)
                        .build()
                    mMap.animateCamera(CameraUpdateFactory.newCameraPosition(navPos))
                }
                override fun onCancel() { isRouteDrawn = true }
            })
        } catch (e: Exception) {
            isRouteDrawn = true
        }
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun formatTiempo(segundos: Double): String {
        val min = (segundos / 60).toInt()
        return if (min > 59) "${min/60}h ${min%60}min" else "${min}min"
    }

    private fun showSafetyExplanation(analysis: RiskManager.SafetyAnalysis) {
        val msg = """
            Criterios de Seguridad Aplicados:
            
            • Infraestructura: Se detectaron ${analysis.cameraCount} cámaras C5 y ${analysis.pathCount} senderos iluminados.
            • Entorno: El nivel de riesgo histórico en esta zona es ${analysis.riskLevel.lowercase()}.
            • Monitoreo: Basado en la cobertura de vigilancia y horario actual.
            
            * Ninguna ruta es 100% segura. Mantente siempre alerta a tu entorno.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("¿Por qué esta ruta?")
            .setMessage(msg)
            .setPositiveButton("Entendido", null)
            .show()
    }

    override fun onDestroy() { 
        isActivityActive = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy() 
    }
}
