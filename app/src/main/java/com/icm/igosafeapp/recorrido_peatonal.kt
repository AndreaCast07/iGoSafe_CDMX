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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
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
    private var actualUserMarker: com.google.android.gms.maps.model.Marker? = null
    private var fullRoutePoints: List<LatLng> = emptyList()
    private var walkedPolyline: Polyline? = null
    private var walkedPoints: MutableList<LatLng> = mutableListOf()

    private lateinit var startLocation: LatLng
    private lateinit var endLocation: LatLng
    private var fastestDuration: Double = 0.0

    private var isActivityActive = false
    private val mainHandler = Handler(Looper.getMainLooper())

    data class SafetyFeature(val location: LatLng, val title: String, val type: Int)

    private val chapultepecPolygon = listOf(
        LatLng(19.4230, -99.1750), LatLng(19.4210, -99.1850), LatLng(19.4150, -99.1950),
        LatLng(19.4050, -99.1900), LatLng(19.4100, -99.1750), LatLng(19.4230, -99.1750)
    )

    private val priorityCorridors = listOf(
        listOf(LatLng(19.4325, -99.1545), LatLng(19.4270, -99.1675), LatLng(19.4235, -99.1755), LatLng(19.4210, -99.1930)), // Reforma Centro
        listOf(LatLng(19.4210, -99.1930), LatLng(19.4180, -99.2150), LatLng(19.3900, -99.2600)), // Paseo de la Reforma (Lomas/Santa Fe)
        listOf(LatLng(19.4208, -99.1762), LatLng(19.4115, -99.1760), LatLng(19.4085, -99.1910), LatLng(19.4042, -99.2025)), // Constituyentes
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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.recorridoPeatonal)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = systemBars.bottom)
            insets
        }
    }

    private fun cerrarPantallaLimpia() {
        isActivityActive = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        progressBar.visibility = View.VISIBLE
        
        Thread {
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
                actualizarPosicionUsuario(LatLng(location.latitude, location.longitude))
            }
        }
    }

    private fun actualizarPosicionUsuario(pos: LatLng) {
        if (!::mMap.isInitialized) return

        if (actualUserMarker == null) {
            actualUserMarker = mMap.addMarker(MarkerOptions()
                .position(pos)
                .title("Tú")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                .anchor(0.5f, 0.5f))
        } else {
            actualUserMarker?.position = pos
        }

        if (walkedPoints.isEmpty() || calcularDistancia(walkedPoints.last(), pos) > 2.0) {
            walkedPoints.add(pos)
            actualizarLineaRecorrida()
        }
        
        // Se elimina el seguimiento automático forzado para permitir que el usuario vea el origen y la ruta completa
        /*
        if (isRouteDrawn) {
            val cameraPosition = com.google.android.gms.maps.model.CameraPosition.Builder()
                .target(pos).zoom(18.5f).tilt(55f).bearing(mMap.cameraPosition.bearing).build()
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        } else if (isFirstLocationUpdate) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 17f))
            isFirstLocationUpdate = false
        }
        */
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
            .color(Color.argb(100, 100, 100, 100))
            .width(8f).zIndex(5f))
    }

    private fun startTracking() {
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
                .setMinUpdateDistanceMeters(2f).build()
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {}
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.standard))
        mMap.addMarker(MarkerOptions().position(startLocation).title("Inicio"))
        mMap.addMarker(MarkerOptions().position(endLocation).title("Destino"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startLocation, 16f))
        
        Thread {
            val res = fetchOSRMRoute(listOf(startLocation, endLocation))
            if (res.first.isNotEmpty()) fastestDuration = res.second
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
        if (!isActivityActive || isFinishing) return
        isRouteDrawn = false 
        progressBar.visibility = View.VISIBLE
        mMap.clear()
        mMap.addMarker(MarkerOptions().position(startLocation).title("Inicio").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)))
        mMap.addMarker(MarkerOptions().position(endLocation).title("Destino"))

        Thread {
            try {
                // Obtenemos todas las alternativas para la ruta directa
                val alternatives = fetchOSRMRoute(listOf(startLocation, endLocation))
                if (alternatives.isEmpty()) {
                    runOnUiThread { progressBar.visibility = View.GONE }
                    return@Thread
                }

                // Analizamos la seguridad de cada alternativa
                val analyzedAlternatives = alternatives.map { 
                    it to RiskManager.analyzeRoute(it.first)
                }

                // La ruta más rápida absoluta
                val fastestResult = analyzedAlternatives.minByOrNull { it.first.second }!!
                val directDuration = fastestResult.first.second

                var finalPoints: List<LatLng>
                var dur: Double
                var finalAnalysis: RiskManager.SafetyAnalysis
                var isSafeRerouteUsed = false

                if (optimizarSeguridad) {
                    // Buscamos la mejor alternativa directa en seguridad
                    val bestDirectSafe = analyzedAlternatives.maxByOrNull { it.second.score }!!
                    
                    // También probamos la ruta por corredores prioritarios (Segura por infraestructura)
                    val safeWaypoints = mutableListOf(startLocation)
                    var bestCorridor: List<LatLng>? = null
                    var maxAlignment = 0f
                    for (corridor in priorityCorridors) {
                        val alignment = calculateAlignment(startLocation, endLocation, corridor)
                        if (alignment > maxAlignment) { maxAlignment = alignment; bestCorridor = corridor }
                    }

                    var corridorRoute: Pair<List<LatLng>, Double>? = null
                    var corridorAnalysis: RiskManager.SafetyAnalysis? = null

                    if (bestCorridor != null && maxAlignment > 0.3) {
                        safeWaypoints.add(findNearestPointOnCorridor(startLocation, bestCorridor))
                        safeWaypoints.add(findNearestPointOnCorridor(endLocation, bestCorridor))
                        safeWaypoints.add(endLocation)
                        
                        val res = fetchOSRMRoute(safeWaypoints).firstOrNull()
                        if (res != null) {
                            corridorRoute = res
                            corridorAnalysis = RiskManager.analyzeRoute(res.first)
                        }
                    }

                    // Comparamos la mejor alternativa directa vs la ruta del corredor
                    val candidates = mutableListOf(bestDirectSafe)
                    if (corridorRoute != null && corridorAnalysis != null) {
                        candidates.add(corridorRoute to corridorAnalysis)
                    }

                    val overallBestSafe = candidates.maxByOrNull { it.second.score }!!
                    
                    finalPoints = overallBestSafe.first.first
                    dur = overallBestSafe.first.second
                    finalAnalysis = overallBestSafe.second

                    // Determinamos si realmente hay un beneficio de seguridad vs la más rápida
                    if (finalPoints != fastestResult.first.first && 
                        (finalAnalysis.score > fastestResult.second.score || 
                         (finalAnalysis.cameraCount + finalAnalysis.pathCount) > (fastestResult.second.cameraCount + fastestResult.second.pathCount))) {
                        isSafeRerouteUsed = true
                    } else {
                        // Si no hay mejora real, nos quedamos con la más rápida pero evaluamos su seguridad
                        finalPoints = fastestResult.first.first
                        dur = fastestResult.first.second
                        finalAnalysis = fastestResult.second
                        isSafeRerouteUsed = false
                    }
                } else {
                    // Modo Más Rápida
                    finalPoints = fastestResult.first.first
                    dur = fastestResult.first.second
                    finalAnalysis = fastestResult.second
                    isSafeRerouteUsed = false
                }

                mainHandler.post {
                    if (!isActivityActive || isFinishing) return@post
                    progressBar.visibility = View.GONE
                    if (finalPoints.isNotEmpty()) {
                        fullRoutePoints = finalPoints
                        startTracking()
                        val safetyMarkers = identifySafety(finalPoints)

                        drawAll(finalPoints, safetyMarkers)
                        
                        tipoRutaTitulo.text = if (optimizarSeguridad && isSafeRerouteUsed) finalAnalysis.label else if (optimizarSeguridad) "Máxima Seguridad Detectada" else "Peatonal más Rápida"
                        textoRuta.text = "Cámaras: ${finalAnalysis.cameraCount} | Senderos: ${finalAnalysis.pathCount}\nEntorno: ${finalAnalysis.riskLevel} | Tiempo: ${formatTiempo(dur)}"
                        
                        val diff = ((dur - directDuration) / 60).toInt()
                        
                        if (optimizarSeguridad && isSafeRerouteUsed && diff > 0) {
                            diffTiempo.text = "+$diff min para priorizar tu seguridad"
                            diffTiempo.setTextColor(ContextCompat.getColor(this, R.color.naranja))
                        } else if (!optimizarSeguridad) {
                            if (diff > 0 && finalPoints != fastestResult.first.first) {
                                // Caso raro donde la rápida seleccionada no es la absoluta
                                diffTiempo.text = "Trayecto optimizado"
                            } else {
                                diffTiempo.text = "Trayecto con tiempo mínimo"
                            }
                            diffTiempo.setTextColor(ContextCompat.getColor(this, R.color.azul3))
                        } else {
                            diffTiempo.text = "Ruta optimizada con seguridad máxima"
                            diffTiempo.setTextColor(ContextCompat.getColor(this, R.color.verde))
                        }

                        if (safetyMarkers.isEmpty() && optimizarSeguridad) {
                            AlertDialog.Builder(this).setTitle("Advertencia de Seguridad")
                                .setMessage("No se detectó vigilancia monitoreada en esta zona. Manténgase alerta.")
                                .setPositiveButton("Entendido", null).show()
                        }
                    }
                }
            } catch (e: Exception) { runOnUiThread { progressBar.visibility = View.GONE } }
        }.start()
    }

    private fun calculateTimeForPoints(points: List<LatLng>): Double = fastestDuration * 1.15

    private fun calculateAlignment(start: LatLng, end: LatLng, corridor: List<LatLng>): Float {
        val bounds = try { LatLngBounds.builder().include(start).include(end).build() } catch (e: Exception) { return 0f }
        var pointsInside = 0
        corridor.forEach { if (bounds.contains(it)) pointsInside++ }
        val ratio = pointsInside.toFloat() / corridor.size
        val dStart = FloatArray(1)
        Location.distanceBetween(start.latitude, start.longitude, corridor.first().latitude, corridor.first().longitude, dStart)
        return if (dStart[0] < 3000) ratio + 0.3f else ratio
    }

    private fun findNearestPointOnCorridor(point: LatLng, corridor: List<LatLng>): LatLng {
        var minDistance = Double.MAX_VALUE
        var nearest = corridor[0]
        for (p in corridor) {
            val dist = FloatArray(1)
            Location.distanceBetween(point.latitude, point.longitude, p.latitude, p.longitude, dist)
            if (dist[0] < minDistance) { minDistance = dist[0].toDouble(); nearest = p }
        }
        return nearest
    }

    private fun getIntermediateNodes(start: LatLng, end: LatLng, corridor: List<LatLng>): List<LatLng> = emptyList()

    private fun cleanWaypoints(points: List<LatLng>): List<LatLng> = points

    private fun fetchOSRMRoute(points: List<LatLng>): List<Pair<List<LatLng>, Double>> {
        if (points.size < 2) return emptyList()
        val results = mutableListOf<Pair<List<LatLng>, Double>>()
        try {
            val coords = points.joinToString(";") { "${it.longitude},${it.latitude}" }
            val urlStr = "https://routing.openstreetmap.de/routed-foot/route/v1/foot/$coords?overview=full&geometries=polyline&continue_straight=true&alternatives=true"
            val conn = URL(urlStr).openConnection() as HttpsURLConnection
            conn.setRequestProperty("User-Agent", "iGoSafeApp/1.5")
            if (conn.responseCode == 200) {
                val res = conn.inputStream.bufferedReader().readText()
                val routes = JSONObject(res).optJSONArray("routes")
                if (routes != null) {
                    for (i in 0 until routes.length()) {
                        val r = routes.getJSONObject(i)
                        results.add(Pair(PolyUtil.decode(r.getString("geometry")), r.getDouble("duration")))
                    }
                }
            }
        } catch (e: Exception) {}
        return results
    }

    private fun identifySafety(path: List<LatLng>): List<SafetyFeature> {
        val list = mutableListOf<SafetyFeature>()
        RiskManager.getCamerasOnPath(path).forEach { list.add(SafetyFeature(it, "Cámara C5", 2)) }
        RiskManager.getPathsOnPath(path).forEach { list.add(SafetyFeature(it, "Sendero Seguro", 5)) }
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
        mMap.addPolyline(PolylineOptions().addAll(points).color(Color.parseColor("#27AE60")).width(20f).zIndex(1f))
        val cameraIcon = getIconFromVector(R.drawable.ic_camera, Color.YELLOW, 70)
        val senderoIcon = getResizedBitmap(R.drawable.icon_peaton, 90)
        for (f in features) {
            val icon = if (f.type == 2) cameraIcon else senderoIcon
            mMap.addMarker(MarkerOptions().position(f.location).title(f.title).icon(icon).anchor(0.5f, 0.5f))
        }
        val bounds = LatLngBounds.Builder()
        points.forEach { bounds.include(it) }
        try { mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 160)) } catch (e: Exception) {}
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
        AlertDialog.Builder(this).setTitle("¿Por qué esta ruta?").setMessage("Análisis basado en infraestructura detectada.").setPositiveButton("Entendido", null).show()
    }

    override fun onDestroy() { 
        isActivityActive = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy() 
    }
}