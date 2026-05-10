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
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.maps.android.PolyUtil
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.TimeZone
import javax.net.ssl.HttpsURLConnection
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.gms.location.*
import java.util.ArrayList

class recorrido_peatonal : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var terminar: Button
    private lateinit var btnFastest: Button
    private lateinit var btnSafest: Button
    private lateinit var btnIniciarNav: Button
    private lateinit var btnInfoRuta: ImageButton
    private lateinit var cardInfo: androidx.cardview.widget.CardView
    private lateinit var routeOptions: LinearLayout
    private lateinit var textoRutaLayout: LinearLayout
    private lateinit var mMap: GoogleMap
    private lateinit var progressBar: ProgressBar
    private lateinit var textoRuta: TextView
    private lateinit var tipoRutaTitulo: TextView
    private lateinit var diffTiempo: TextView

    private lateinit var navigationOverlay: RelativeLayout
    private lateinit var navDistanceToNext: TextView
    private lateinit var navInstructionText: TextView
    private lateinit var navDirectionIcon: ImageView
    private lateinit var navEtaTime: TextView
    private lateinit var navTotalDistance: TextView
    private lateinit var btnStopNav: ImageButton
    private lateinit var btnFinalizarNav: Button
    private lateinit var bannerOffRoute: androidx.cardview.widget.CardView

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var actualUserMarker: Marker? = null
    private var fullRoutePoints: List<LatLng> = emptyList()
    private var walkedPolyline: Polyline? = null
    private var walkedPoints: MutableList<LatLng> = mutableListOf()

    private lateinit var startLocation: LatLng
    private lateinit var endLocation: LatLng
    private var fastestDuration: Double = 0.0
    private var totalPlannedDistance: Double = 0.0
    private var totalWalkedDistance: Double = 0.0

    private var isActivityActive = false
    private var isNavigationActive = false
    private var navigationSteps: List<RouteStep> = emptyList()
    private var nextStepIndex: Int = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    data class SafetyFeature(val location: LatLng, val title: String, val type: Int)

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
        terminar.isEnabled = false
        terminar.alpha = 0.5f
        btnFastest = findViewById(R.id.btnFastest)
        btnSafest = findViewById(R.id.btnSafest)
        btnIniciarNav = findViewById(R.id.btnIniciarNav)
        btnInfoRuta = findViewById(R.id.btnInfoRuta)
        cardInfo = findViewById(R.id.cardInfo)
        routeOptions = findViewById(R.id.routeOptions)
        textoRutaLayout = findViewById(R.id.textoRutaLayout)

        navigationOverlay = findViewById(R.id.navigationOverlay)
        navDistanceToNext = findViewById(R.id.navDistanceToNext)
        navInstructionText = findViewById(R.id.navInstructionText)
        navDirectionIcon = findViewById(R.id.navDirectionIcon)
        navEtaTime = findViewById(R.id.navEtaTime)
        navTotalDistance = findViewById(R.id.navTotalDistance)
        btnStopNav = findViewById(R.id.btnStopNav)
        btnFinalizarNav = findViewById(R.id.btnFinalizarNav)
        bannerOffRoute = findViewById(R.id.bannerOffRoute)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationUpdates()

        startLocation = LatLng(intent.getDoubleExtra("startLat", 0.0), intent.getDoubleExtra("startLong", 0.0))
        endLocation = LatLng(intent.getDoubleExtra("endLat", 0.0), intent.getDoubleExtra("endLong", 0.0))
        
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        
        terminar.setOnClickListener { cerrarPantallaLimpia() }
        btnFastest.setOnClickListener { generarRuta(false) }
        btnSafest.setOnClickListener { generarRuta(true) }
        
        btnIniciarNav.setOnClickListener { startNavigationMode() }
        btnStopNav.setOnClickListener { stopNavigationMode() }
        btnFinalizarNav.setOnClickListener { cerrarPantallaLimpia() }
        btnInfoRuta.setOnClickListener { mostrarExplicacionRuta() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.recorridoPeatonal)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = systemBars.bottom)
            insets
        }
    }

    private fun mostrarExplicacionRuta() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Componentes de la Ruta")
        builder.setMessage("• Cámaras C5: Monitoreo activo por la Secretaría de Seguridad.\n" +
                "• Senderos Seguros: Calles con iluminación LED reforzada y mayor flujo peatonal.\n" +
                "• Entorno (CPTED): Evaluación del diseño urbano para prevenir el delito (limpieza, visibilidad y vitalidad).\n\n" +
                "iGoSafe prioriza estas variables para tu protección.")
        builder.setPositiveButton("Entendido", null)
        builder.show()
    }

    private fun startNavigationMode() {
        if (fullRoutePoints.isEmpty()) return
        
        // Validación de cercanía al origen (máximo 100 metros)
        val userLoc = walkedPoints.lastOrNull()
        if (userLoc != null) {
            val distToStart = calcularDistancia(userLoc, startLocation)
            if (distToStart > 100.0) {
                Toast.makeText(this, "Debes estar en el punto de inicio para comenzar la navegación", Toast.LENGTH_LONG).show()
                return
            }
        }
        proceedToNavigation()
    }

    private fun proceedToNavigation() {
        isNavigationActive = true
        navigationOverlay.visibility = View.VISIBLE
        
        routeOptions.visibility = View.GONE
        textoRutaLayout.visibility = View.GONE
        cardInfo.visibility = View.GONE
        terminar.visibility = View.GONE
        
        nextStepIndex = 0
        updateNavigationUI(walkedPoints.lastOrNull() ?: startLocation, 0f)
    }

    private fun stopNavigationMode() {
        isNavigationActive = false
        navigationOverlay.visibility = View.GONE
        
        // Restore general UI components
        routeOptions.visibility = View.VISIBLE
        textoRutaLayout.visibility = View.VISIBLE
        cardInfo.visibility = View.VISIBLE
        terminar.visibility = View.VISIBLE
        
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(walkedPoints.lastOrNull() ?: startLocation, 17f))
    }

    private fun cerrarPantallaLimpia() {
        if (totalPlannedDistance > 0 && totalWalkedDistance < (totalPlannedDistance * 0.10)) {
            Toast.makeText(this, "Debes recorrer al menos el 10% de la ruta para calificarla", Toast.LENGTH_LONG).show()
            isActivityActive = false
            fusedLocationClient.removeLocationUpdates(locationCallback)
            finish()
            return
        }

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
                actualizarPosicionUsuario(LatLng(location.latitude, location.longitude), location.bearing)
            }
        }
    }

    private fun actualizarPosicionUsuario(pos: LatLng, currentBearing: Float) {
        if (!::mMap.isInitialized) return

        if (actualUserMarker == null) {
            actualUserMarker = mMap.addMarker(MarkerOptions()
                .position(pos)
                .title("Tú")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
                .anchor(0.5f, 0.5f))
        } else {
            actualUserMarker?.position = pos
        }

        if (walkedPoints.isEmpty() || calcularDistancia(walkedPoints.last(), pos) > 2.0) {
            if (walkedPoints.isNotEmpty()) {
                totalWalkedDistance += calcularDistancia(walkedPoints.last(), pos).toDouble()
            }
            walkedPoints.add(pos)
            actualizarLineaRecorrida()
        }
        
        // Verificación automática de llegada al destino
        val distanciaAlDestino = calcularDistancia(pos, endLocation)
        if (distanciaAlDestino < 25.0) { // Umbral de 25 metros
            Toast.makeText(this, "¡Has llegado a tu destino!", Toast.LENGTH_LONG).show()
            cerrarPantallaLimpia()
            return
        }
        
        if (isNavigationActive) {
            updateNavigationUI(pos, currentBearing)
        }
    }

    private fun updateNavigationUI(pos: LatLng, currentBearing: Float) {
        if (navigationSteps.isEmpty()) return
        
        // 1. Detección de Desvío (Límite 20 metros para peatones)
        val isOffPath = !PolyUtil.isLocationOnPath(pos, fullRoutePoints, false, 20.0)
        
        // 2. Detección de Dirección Contraria
        val currentTarget = navigationSteps.getOrNull(nextStepIndex) ?: return
        val expectedBearing = getBearing(pos, currentTarget.location)
        var isWrongWay = false
        
        if (currentBearing != 0f) {
            val diff = Math.abs(expectedBearing - currentBearing)
            val normalizedDiff = if (diff > 180) 360 - diff else diff
            if (normalizedDiff > 140) { // Margen más amplio para peatones
                isWrongWay = true
            }
        }

        val bannerText = findViewById<TextView>(R.id.bannerOffRouteText)
        if (isWrongWay) {
            bannerText?.text = "⚠️ DIRECCIÓN CONTRARIA: Regresa a la ruta segura"
            bannerOffRoute.visibility = View.VISIBLE
        } else if (isOffPath) {
            bannerText?.text = "⚠️ Fuera de Ruta Protegida: Has abandonado el camino verificado"
            bannerOffRoute.visibility = View.VISIBLE
        } else {
            bannerOffRoute.visibility = View.GONE
        }

        if (bannerOffRoute.visibility == View.GONE && (isOffPath || isWrongWay)) {
            Toast.makeText(this, if (isWrongWay) "⚠️ Vas en dirección contraria" else "⚠️ Te has desviado de la ruta segura", Toast.LENGTH_SHORT).show()
        }

        val distToNext = calcularDistancia(pos, currentTarget.location)
        
        if (distToNext < 15.0 && nextStepIndex < navigationSteps.size - 1) {
            nextStepIndex++
        }
        
        val next = navigationSteps[nextStepIndex]
        val dNext = calcularDistancia(pos, next.location)
        
        navDistanceToNext.text = if (dNext > 1000) String.format("%.1f km", dNext/1000) else String.format("%.0f m", dNext)
        navInstructionText.text = translateManeuver(next.maneuverType, next.modifier, next.name)
        
        // 2. Alerta de "Punto Ciego" (Baja densidad de cámaras en el tramo actual)
        verificarPuntoCiego(pos)

        // Actualizar icono de dirección
        actualizarIconoNavegacion(next.maneuverType, next.modifier)
        
        // Update camera in navigation style
        val cameraPosition = CameraPosition.Builder()
            .target(pos)
            .zoom(19f)
            .tilt(45f)
            .bearing(getBearing(pos, next.location))
            .build()
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    private var lastBlindSpotAlertTime = 0L
    private fun verificarPuntoCiego(pos: LatLng) {
        val now = System.currentTimeMillis()
        if (now - lastBlindSpotAlertTime < 60000) return // Solo una alerta por minuto
        
        val nearbyCameras = RiskManager.getCamerasOnPath(listOf(pos))
        if (nearbyCameras.isEmpty()) {
            Toast.makeText(this, "⚠️ Punto Ciego: Baja densidad de cámaras. Mantente alerta.", Toast.LENGTH_LONG).show()
            lastBlindSpotAlertTime = now
        }
    }

    private fun actualizarIconoNavegacion(type: String, modifier: String?) {
        navDirectionIcon.rotation = 0f
        when (modifier) {
            "left", "slight left", "sharp left" -> navDirectionIcon.rotation = -90f
            "right", "slight right", "sharp right" -> navDirectionIcon.rotation = 90f
            "uturn" -> navDirectionIcon.rotation = 180f
            else -> navDirectionIcon.rotation = 0f
        }
    }

    private fun translateManeuver(type: String, modifier: String?, name: String): String {
        val street = if (name.isNotEmpty()) " en $name" else ""
        return when (type) {
            "turn" -> {
                when (modifier) {
                    "left" -> "Gira a la izquierda$street"
                    "right" -> "Gira a la derecha$street"
                    "slight left" -> "Gira levemente a la izquierda$street"
                    "slight right" -> "Gira levemente a la derecha$street"
                    "sharp left" -> "Gira pronunciadamente a la izquierda$street"
                    "sharp right" -> "Gira pronunciadamente a la derecha$street"
                    else -> "Gira$street"
                }
            }
            "new name" -> "Continúa por $name"
            "depart" -> "Inicia el recorrido"
            "arrive" -> "Has llegado a tu destino"
            "merge" -> "Incorpórate$street"
            "ramp" -> "Toma la rampa$street"
            "fork" -> "Bifurcación$street"
            "roundabout" -> "En la rotonda, toma la salida$street"
            else -> if (name.isNotEmpty()) "Continúa por $name" else "Continúa recto"
        }
    }

    private fun getBearing(begin: LatLng, end: LatLng): Float {
        val lat1 = begin.latitude * Math.PI / 180
        val lon1 = begin.longitude * Math.PI / 180
        val lat2 = end.latitude * Math.PI / 180
        val lon2 = end.longitude * Math.PI / 180
        val dLon = lon2 - lon1
        val y = Math.sin(dLon) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
        return ((Math.atan2(y, x) * 180 / Math.PI + 360) % 360).toFloat()
    }

    private fun calcularDistancia(p1: LatLng, p2: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results)
        return results[0]
    }

    private fun actualizarLineaRecorrida() {
        if (!::mMap.isInitialized || walkedPoints.isEmpty()) return
        walkedPolyline?.remove()
        walkedPolyline = mMap.addPolyline(PolylineOptions()
            .addAll(walkedPoints)
            .color(Color.parseColor("#E67E22")) // Naranja vibrante para el recorrido real
            .width(14f) // Un poco más grueso para que destaque
            .zIndex(10f))
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
        
        // Distinct color for start to avoid confusion with user marker
        mMap.addMarker(MarkerOptions().position(startLocation).title("Inicio").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))
        mMap.addMarker(MarkerOptions().position(endLocation).title("Destino"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startLocation, 16f))
        
        Thread {
            val res = fetchOSRMRoute(listOf(startLocation, endLocation))
            if (res.isNotEmpty()) fastestDuration = res[0].duration
            runOnUiThread { generarRuta(true) }
        }.start()
    }

    private fun generarRuta(optimizarSeguridad: Boolean) {
        if (!isActivityActive || isFinishing) return
        isRouteDrawn = false 
        progressBar.visibility = View.VISIBLE
        terminar.isEnabled = false
        terminar.alpha = 0.5f
        btnIniciarNav.visibility = View.GONE
        mMap.clear()
        mMap.addMarker(MarkerOptions().position(startLocation).title("Inicio").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))
        mMap.addMarker(MarkerOptions().position(endLocation).title("Destino"))

        Thread {
            try {
                val alternatives = fetchOSRMRoute(listOf(startLocation, endLocation))
                if (alternatives.isEmpty()) {
                    runOnUiThread { progressBar.visibility = View.GONE }
                    return@Thread
                }

                val analyzedAlternatives = alternatives.map { 
                    it to RiskManager.analyzeRoute(it.points)
                }

                val fastestResult = analyzedAlternatives.minByOrNull { it.first.duration }!!
                val directDistance = fastestResult.first.distance
                val directDuration = fastestResult.first.duration

                var finalRoute = fastestResult.first
                var finalAnalysis = fastestResult.second
                var isSafeRerouteUsed = false

                if (optimizarSeguridad) {
                    val bestDirectSafe = analyzedAlternatives.maxByOrNull { it.second.score }!!
                    
                    val safeWaypoints = mutableListOf(startLocation)
                    var bestCorridor: List<LatLng>? = null
                    var maxAlignment = 0f
                    for (corridor in priorityCorridors) {
                        val alignment = calculateAlignment(startLocation, endLocation, corridor)
                        if (alignment > maxAlignment) { maxAlignment = alignment; bestCorridor = corridor }
                    }

                    var corridorRoute: RouteData? = null
                    var corridorAnalysis: RiskManager.SafetyAnalysis? = null

                    if (bestCorridor != null && maxAlignment > 0.3) {
                        safeWaypoints.add(findNearestPointOnCorridor(startLocation, bestCorridor))
                        safeWaypoints.add(findNearestPointOnCorridor(endLocation, bestCorridor))
                        safeWaypoints.add(endLocation)
                        
                        val res = fetchOSRMRoute(safeWaypoints).firstOrNull()
                        if (res != null) {
                            corridorRoute = res
                            corridorAnalysis = RiskManager.analyzeRoute(res.points)
                        }
                    }

                    val candidates = mutableListOf(bestDirectSafe)
                    if (corridorRoute != null && corridorAnalysis != null) {
                        candidates.add(corridorRoute to corridorAnalysis)
                    }

                    val overallBestSafe = candidates
                        .filter { it.first.distance <= directDistance * 1.30 }
                        .maxByOrNull { it.second.score } ?: fastestResult
                    
                    finalRoute = overallBestSafe.first
                    finalAnalysis = overallBestSafe.second

                    if (finalRoute != fastestResult.first && 
                        finalRoute.distance <= directDistance * 1.30 &&
                        (finalAnalysis.score > fastestResult.second.score || 
                         (finalAnalysis.cameraCount + finalAnalysis.pathCount) > (fastestResult.second.cameraCount + fastestResult.second.pathCount))) {
                        isSafeRerouteUsed = true
                    } else {
                        finalRoute = fastestResult.first
                        finalAnalysis = fastestResult.second
                        isSafeRerouteUsed = false
                    }
                }

                mainHandler.post {
                    if (!isActivityActive || isFinishing) return@post
                    progressBar.visibility = View.GONE
                    if (finalRoute.points.isNotEmpty()) {
                        fullRoutePoints = finalRoute.points
                        navigationSteps = finalRoute.steps
                        totalPlannedDistance = finalRoute.distance
                        terminar.isEnabled = true
                        terminar.alpha = 1.0f
                        btnIniciarNav.visibility = View.VISIBLE
                        startTracking()
                        
                        val safetyMarkers = identifySafety(fullRoutePoints)
                        drawAll(fullRoutePoints, safetyMarkers)
                        
                        tipoRutaTitulo.text = if (optimizarSeguridad && isSafeRerouteUsed) finalAnalysis.label else if (optimizarSeguridad) "Máxima Seguridad Detectada" else "Peatonal más Rápida"
                        
                        if (optimizarSeguridad && !isSafeRerouteUsed) {
                            Toast.makeText(this@recorrido_peatonal, "La ruta más rápida ya es la opción con mayor vigilancia", Toast.LENGTH_LONG).show()
                        }
                        
                        textoRuta.text = "Cámaras: ${finalAnalysis.cameraCount} | Senderos: ${finalAnalysis.pathCount}\nEntorno: ${finalAnalysis.riskLevel} | Tiempo: ${formatTiempo(finalRoute.duration)}"
                        
                        if (finalAnalysis.cameraCount == 0 && optimizarSeguridad) {
                            AlertDialog.Builder(this@recorrido_peatonal)
                                .setTitle("Aviso de Infraestructura")
                                .setMessage("No se detectaron cámaras C5 en esta zona. Mantente en áreas iluminadas y concurridas.")
                                .setPositiveButton("Entendido", null).show()
                        }
                        
                        navEtaTime.text = formatTiempo(finalRoute.duration)
                        navTotalDistance.text = String.format("%.1f km", finalRoute.distance / 1000)

                        val diff = ((finalRoute.duration - directDuration) / 60).toInt()
                        if (optimizarSeguridad && isSafeRerouteUsed && diff > 0) {
                            diffTiempo.text = "+$diff min para priorizar tu seguridad"
                            diffTiempo.setTextColor(ContextCompat.getColor(this, R.color.naranja))
                        } else {
                            diffTiempo.text = "Ruta optimizada"
                            diffTiempo.setTextColor(ContextCompat.getColor(this, R.color.verde))
                        }

                        // Mostrar Aviso Inicial de Resiliencia
                        mostrarAvisoInicial(finalAnalysis, isSafeRerouteUsed, optimizarSeguridad)
                    }
                }
            } catch (e: Exception) { 
                Log.e("Ruta", "Error: ${e.message}")
                runOnUiThread { progressBar.visibility = View.GONE } 
            }
        }.start()
    }

    private fun mostrarAvisoInicial(analysis: RiskManager.SafetyAnalysis, safeUsed: Boolean, requestedSafe: Boolean) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isNight = hour >= 19 || hour <= 6
        
        val message = StringBuilder()
        message.append("Esta ruta ha sido optimizada utilizando datos oficiales del C5 y registros históricos. La seguridad sugerida es estadística y no garantiza la ausencia de incidentes.\n\n")
        
        if (isNight) {
            message.append("🌙 AVISO NOCTURNO: La prioridad ha cambiado para favorecer calles con Senderos Seguros e iluminación verificada.\n\n")
        }

        if (requestedSafe && !safeUsed) {
            message.append("✅ NOTA: La ruta más rápida ya es la opción con mayor vigilancia disponible en esta zona.\n\n")
        } else if (!requestedSafe) {
            message.append("⚠️ Trayecto Rápido: Esta ruta prioriza el tiempo. Podría tener menor cobertura de cámaras que una ruta protegida.\n\n")
        }

        AlertDialog.Builder(this)
            .setTitle("Contrato de Seguridad")
            .setMessage(message.toString())
            .setPositiveButton("Entendido", null)
            .show()
    }

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

    data class RouteStep(
        val distance: Double,
        val duration: Double,
        val maneuverType: String,
        val modifier: String?,
        val name: String,
        val location: LatLng
    )

    data class RouteData(
        val points: List<LatLng>,
        val distance: Double,
        val duration: Double,
        val steps: List<RouteStep>
    )

    private fun fetchOSRMRoute(points: List<LatLng>): List<RouteData> {
        if (points.size < 2) return emptyList()
        val results = mutableListOf<RouteData>()
        try {
            val coords = points.joinToString(";") { "${it.longitude},${it.latitude}" }
            val urlStr = "https://routing.openstreetmap.de/routed-foot/route/v1/foot/$coords?overview=full&geometries=polyline&continue_straight=true&alternatives=true&steps=true"
            val conn = URL(urlStr).openConnection() as HttpsURLConnection
            conn.setRequestProperty("User-Agent", "iGoSafeApp/1.5")
            if (conn.responseCode == 200) {
                val res = conn.inputStream.bufferedReader().readText()
                val routes = JSONObject(res).optJSONArray("routes")
                if (routes != null) {
                    for (i in 0 until routes.length()) {
                        val r = routes.getJSONObject(i)
                        val steps = mutableListOf<RouteStep>()
                        val legs = r.optJSONArray("legs")
                        if (legs != null) {
                            for (j in 0 until legs.length()) {
                                val legSteps = legs.getJSONObject(j).optJSONArray("steps")
                                if (legSteps != null) {
                                    for (k in 0 until legSteps.length()) {
                                        val s = legSteps.getJSONObject(k)
                                        val maneuver = s.getJSONObject("maneuver")
                                        val loc = maneuver.getJSONArray("location")
                                        steps.add(RouteStep(
                                            distance = s.getDouble("distance"),
                                            duration = s.getDouble("duration"),
                                            maneuverType = maneuver.getString("type"),
                                            modifier = maneuver.optString("modifier", ""),
                                            name = s.optString("name", ""),
                                            location = LatLng(loc.getDouble(1), loc.getDouble(0))
                                        ))
                                    }
                                }
                            }
                        }
                        results.add(RouteData(
                            PolyUtil.decode(r.getString("geometry")),
                            r.getDouble("distance"),
                            r.getDouble("duration"),
                            steps
                        ))
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
        actualizarLineaRecorrida()
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

    private fun formatTiempo(segundos: Double): String {
        val min = (segundos / 60).toInt()
        return if (min > 59) "${min/60}h ${min%60}min" else "${min}min"
    }

    override fun onDestroy() { 
        isActivityActive = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy() 
    }
}