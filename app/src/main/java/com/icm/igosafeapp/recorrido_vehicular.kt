package com.icm.igosafeapp

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.PolyUtil
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection
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
    private var actualUserMarker: Marker? = null
    private var walkedPolyline: Polyline? = null
    private var walkedPoints: MutableList<LatLng> = mutableListOf()

    private lateinit var startLocation: LatLng
    private lateinit var endLocation: LatLng
    private var fastestDuration: Double = 0.0

    private var isActivityActive = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val primaryAvenues = listOf(
        listOf(LatLng(19.4325, -99.1545), LatLng(19.4270, -99.1675), LatLng(19.4235, -99.1755), LatLng(19.4210, -99.1930)), // Reforma Centro
        listOf(LatLng(19.4210, -99.1930), LatLng(19.4180, -99.2150), LatLng(19.3900, -99.2600)), // Reforma Lomas/Santa Fe
        listOf(LatLng(19.4208, -99.1762), LatLng(19.4115, -99.1760), LatLng(19.4085, -99.1910), LatLng(19.4042, -99.2025))  // Constituyentes
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
            val barriosRecorridos = mutableSetOf<String>()
            walkedPoints.forEach { RiskManager.getHexAddress(it)?.let { addr -> barriosRecorridos.add(addr) } }
            val intent = Intent(this, review_ruta::class.java).apply { putStringArrayListExtra("barriosPorRuta", ArrayList(barriosRecorridos.toList())) }
            startActivity(intent)
            finish()
        }
        btnFastest.setOnClickListener { generarRutaVehicular(false) }
        btnSafest.setOnClickListener { generarRutaVehicular(true) }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.recorridoVehicular)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = systemBars.bottom)
            insets
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.standard))
        mMap.addMarker(MarkerOptions().position(startLocation).title("Inicio"))
        mMap.addMarker(MarkerOptions().position(endLocation).title("Destino"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startLocation, 15f))
        
        Thread {
            val res = fetchOSRMRoute(listOf(startLocation, endLocation))
            if (res.isNotEmpty()) fastestDuration = res[0].third
            runOnUiThread { generarRutaVehicular(true) }
        }.start()
    }

    private fun generarRutaVehicular(optimizarSeguridad: Boolean) {
        if (!isActivityActive || isFinishing) return
        isRouteDrawn = false 
        progressBar.visibility = View.VISIBLE
        mMap.clear()
        mMap.addMarker(MarkerOptions().position(startLocation).title("Inicio").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)))
        mMap.addMarker(MarkerOptions().position(endLocation).title("Destino"))

        Thread {
            try {
                // Obtenemos alternativas directas
                val alternatives = fetchOSRMRoute(listOf(startLocation, endLocation))
                if (alternatives.isEmpty()) {
                    runOnUiThread { progressBar.visibility = View.GONE }
                    return@Thread
                }

                // Analizamos seguridad de alternativas
                val analyzedAlternatives = alternatives.map { 
                    it to RiskManager.analyzeRoute(it.first)
                }

                val fastestResult = analyzedAlternatives.minByOrNull { it.first.third }!!
                val directDuration = fastestResult.first.third

                var finalPoints: List<LatLng>
                var finalDuration: Double
                var finalAnalysis: RiskManager.SafetyAnalysis
                var isSafeRerouteUsed = false

                if (optimizarSeguridad) {
                    val bestDirectSafe = analyzedAlternatives.maxByOrNull { it.second.score }!!
                    
                    val safeWaypoints = mutableListOf(startLocation)
                    var bestAve: List<LatLng>? = null
                    for (avenue in primaryAvenues) {
                        if (isAvenueAligned(startLocation, endLocation, avenue)) {
                            bestAve = avenue
                            break
                        }
                    }

                    var corridorRoute: Triple<List<LatLng>, Double, Double>? = null
                    var corridorAnalysis: RiskManager.SafetyAnalysis? = null

                    if (bestAve != null) {
                        safeWaypoints.add(findNearestPoint(startLocation, bestAve))
                        safeWaypoints.add(findNearestPoint(endLocation, bestAve))
                        safeWaypoints.add(endLocation)

                        val res = fetchOSRMRoute(safeWaypoints).firstOrNull()
                        if (res != null) {
                            corridorRoute = res
                            corridorAnalysis = RiskManager.analyzeRoute(res.first)
                        }
                    }

                    val candidates = mutableListOf(bestDirectSafe)
                    if (corridorRoute != null && corridorAnalysis != null) {
                        candidates.add(corridorRoute to corridorAnalysis)
                    }

                    val overallBestSafe = candidates.maxByOrNull { it.second.score }!!
                    
                    finalPoints = overallBestSafe.first.first
                    finalDuration = overallBestSafe.first.third
                    finalAnalysis = overallBestSafe.second

                    if (finalPoints != fastestResult.first.first && 
                        (finalAnalysis.score > fastestResult.second.score || 
                         finalAnalysis.cameraCount > fastestResult.second.cameraCount)) {
                        isSafeRerouteUsed = true
                    } else {
                        finalPoints = fastestResult.first.first
                        finalDuration = fastestResult.first.third
                        finalAnalysis = fastestResult.second
                        isSafeRerouteUsed = false
                    }
                } else {
                    finalPoints = fastestResult.first.first
                    finalDuration = fastestResult.first.third
                    finalAnalysis = fastestResult.second
                    isSafeRerouteUsed = false
                }
                
                runOnUiThread {
                    if (!isActivityActive || isFinishing) return@runOnUiThread
                    progressBar.visibility = View.GONE
                    if (finalPoints.isNotEmpty()) {
                        startTracking()
                        val safetyOnPath = identifySafetyOnPath(finalPoints, 100.0)
                        drawEverything(finalPoints, safetyOnPath)
                        
                        tipoRutaTitulo.text = if (optimizarSeguridad && isSafeRerouteUsed) finalAnalysis.label else if (optimizarSeguridad) "Máxima Seguridad Detectada" else "Ruta Vehicular más Rápida"
                        textoRuta.text = "Cámaras: ${finalAnalysis.cameraCount} | Patrullas: ${finalAnalysis.pathCount}\nRiesgo: ${finalAnalysis.riskLevel} | Tiempo: ${formatTiempo(finalDuration)}"
                        
                        val diff = ((finalDuration - directDuration) / 60).toInt()
                        if (optimizarSeguridad && isSafeRerouteUsed && diff > 0) {
                            diffTiempo.text = "+$diff min por vigilancia"; diffTiempo.setTextColor(ContextCompat.getColor(this, R.color.naranja))
                        } else {
                            diffTiempo.text = if (optimizarSeguridad) "Ruta con seguridad máxima" else "Trayecto rápido"; diffTiempo.setTextColor(ContextCompat.getColor(this, R.color.verde))
                        }
                    }
                }
            } catch (e: Exception) { runOnUiThread { progressBar.visibility = View.GONE } }
        }.start()
    }

    private fun isAvenueAligned(start: LatLng, end: LatLng, avenue: List<LatLng>): Boolean {
        val entry = findNearestPoint(start, avenue)
        val distToEntry = FloatArray(1)
        Location.distanceBetween(start.latitude, start.longitude, entry.latitude, entry.longitude, distToEntry)
        return distToEntry[0] < 2000
    }

    private fun findNearestPoint(point: LatLng, avenue: List<LatLng>): LatLng {
        var minDistance = Double.MAX_VALUE
        var nearest = avenue[0]
        for (p in avenue) {
            val dist = FloatArray(1)
            Location.distanceBetween(point.latitude, point.longitude, p.latitude, p.longitude, dist)
            if (dist[0] < minDistance) { minDistance = dist[0].toDouble(); nearest = p }
        }
        return nearest
    }

    private fun fetchOSRMRoute(waypoints: List<LatLng>): List<Triple<List<LatLng>, Double, Double>> {
        val results = mutableListOf<Triple<List<LatLng>, Double, Double>>()
        try {
            val coords = waypoints.joinToString(";") { "${it.longitude},${it.latitude}" }
            val urlStr = "https://routing.openstreetmap.de/routed-car/route/v1/driving/$coords?overview=full&geometries=polyline&continue_straight=true&alternatives=true"
            val conn = URL(urlStr).openConnection() as HttpsURLConnection
            conn.setRequestProperty("User-Agent", "iGoSafeApp/1.0")
            if (conn.responseCode == 200) {
                val res = conn.inputStream.bufferedReader().readText()
                val routes = JSONObject(res).optJSONArray("routes")
                if (routes != null) {
                    for (i in 0 until routes.length()) {
                        val route = routes.getJSONObject(i)
                        results.add(Triple(PolyUtil.decode(route.getString("geometry")), route.getDouble("distance"), route.getDouble("duration")))
                    }
                }
            }
        } catch (e: Exception) {}
        return results
    }

    private fun identifySafetyOnPath(path: List<LatLng>, bufferMeters: Double): List<SafetyFeature> {
        val list = mutableListOf<SafetyFeature>()
        RiskManager.getCamerasOnPath(path).forEach { list.add(SafetyFeature(it, "Cámara C5", 2)) }
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
        mMap.addPolyline(PolylineOptions().addAll(points).color(Color.parseColor("#3498DB")).width(18f).zIndex(1f))
        val cameraIcon = getIconFromVector(R.drawable.ic_camera, Color.YELLOW, 70)
        features.forEach { mMap.addMarker(MarkerOptions().position(it.location).title(it.title).icon(cameraIcon).anchor(0.5f, 0.5f)) }
        val bounds = LatLngBounds.Builder()
        points.forEach { bounds.include(it) }
        try { mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 150)) } catch (e: Exception) {}
    }

    private fun setupLocationUpdates() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                actualizarPosicionVehiculo(LatLng(location.latitude, location.longitude))
            }
        }
    }

    private fun actualizarPosicionVehiculo(pos: LatLng) {
        if (!::mMap.isInitialized) return
        if (actualUserMarker == null) {
            actualUserMarker = mMap.addMarker(MarkerOptions().position(pos).title("Tú").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)).anchor(0.5f, 0.5f))
        } else { actualUserMarker?.position = pos }
        if (walkedPoints.isEmpty() || calcularDistancia(walkedPoints.last(), pos) > 10.0) {
            walkedPoints.add(pos); actualizarLineaRecorrida()
        }
        
        // Se elimina el seguimiento automático forzado para permitir que el usuario vea el origen y la ruta completa
        /*
        if (isRouteDrawn) {
            val cameraPosition = com.google.android.gms.maps.model.CameraPosition.Builder().target(pos).zoom(17.5f).tilt(45f).bearing(mMap.cameraPosition.bearing).build()
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        } else if (isFirstLocationUpdate) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f)); isFirstLocationUpdate = false
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
        walkedPolyline = mMap.addPolyline(PolylineOptions().addAll(walkedPoints).color(Color.argb(90, 100, 100, 100)).width(8f).zIndex(5f))
    }

    private fun startTracking() {
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build()
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {}
    }

    private fun showSafetyExplanation(analysis: RiskManager.SafetyAnalysis) {
        AlertDialog.Builder(this).setTitle("Información").setMessage("Basado en infraestructura.").setPositiveButton("OK", null).show()
    }

    override fun onDestroy() { isActivityActive = false; fusedLocationClient.removeLocationUpdates(locationCallback); super.onDestroy() }
    private fun formatTiempo(segundos: Double): String {
        val min = (segundos / 60).toInt()
        return if (min > 59) "${min/60}h ${min%60}min" else "${min}min"
    }
}