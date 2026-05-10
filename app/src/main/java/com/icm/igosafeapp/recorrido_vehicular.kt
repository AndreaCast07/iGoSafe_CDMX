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
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.maps.android.PolyUtil
import org.json.JSONObject
import java.net.URL
import java.util.*
import javax.net.ssl.HttpsURLConnection
import java.util.ArrayList

class recorrido_vehicular : AppCompatActivity(), OnMapReadyCallback {
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

    private val primaryAvenues = listOf(
        listOf(LatLng(19.4325, -99.1545), LatLng(19.4270, -99.1675), LatLng(19.4235, -99.1755), LatLng(19.4210, -99.1930)), // Reforma Centro
        listOf(LatLng(19.4210, -99.1930), LatLng(19.4180, -99.2150), LatLng(19.3900, -99.2600)), // Reforma Lomas/Santa Fe
        listOf(LatLng(19.4208, -99.1762), LatLng(19.4115, -99.1760), LatLng(19.4085, -99.1910), LatLng(19.4042, -99.2025))  // Constituyentes
    )

    data class SafetyFeature(val location: LatLng, val title: String, val type: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recorrido_vehicular)
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
        
        terminar.setOnClickListener {
            if (totalPlannedDistance > 0 && totalWalkedDistance < (totalPlannedDistance * 0.10)) {
                Toast.makeText(this, "Debes recorrer al menos el 10% de la ruta para calificarla", Toast.LENGTH_LONG).show()
                isActivityActive = false
                fusedLocationClient.removeLocationUpdates(locationCallback)
                finish()
                return@setOnClickListener
            }

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
        
        btnIniciarNav.setOnClickListener { startNavigationMode() }
        btnStopNav.setOnClickListener { stopNavigationMode() }
        btnFinalizarNav.setOnClickListener { terminar.performClick() }
        btnInfoRuta.setOnClickListener { mostrarExplicacionRuta() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.recorridoVehicular)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = systemBars.bottom)
            insets
        }
    }

    private fun mostrarExplicacionRuta() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Seguridad Vehicular")
        builder.setMessage("• Cámaras C5: Monitoreo por radar y cámaras de vigilancia.\n" +
                "• Avenidas Principales: Se priorizan vías con mayor patrullaje y flujo constante.\n" +
                "• Riesgo Local: Análisis de incidencia delictiva por zona (H3).\n\n" +
                "Navega con la confianza de que iGoSafe vigila tu entorno.")
        builder.setPositiveButton("Entendido", null)
        builder.show()
    }

    private fun startNavigationMode() {
        // Validación de cercanía al origen (máximo 150 metros para vehículos)
        val userLoc = walkedPoints.lastOrNull()
        if (userLoc != null) {
            val distToStart = calcularDistancia(userLoc, startLocation)
            if (distToStart > 150.0) {
                Toast.makeText(this, "Acércate al punto de inicio para navegar", Toast.LENGTH_LONG).show()
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
        routeOptions.visibility = View.VISIBLE
        textoRutaLayout.visibility = View.VISIBLE
        cardInfo.visibility = View.VISIBLE
        terminar.visibility = View.VISIBLE
        
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(walkedPoints.lastOrNull() ?: startLocation, 16f))
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.standard))
        mMap.addMarker(MarkerOptions().position(startLocation).title("Inicio").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))
        mMap.addMarker(MarkerOptions().position(endLocation).title("Destino"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startLocation, 15f))
        
        Thread {
            val res = fetchOSRMRoute(listOf(startLocation, endLocation))
            if (res.isNotEmpty()) fastestDuration = res[0].duration
            runOnUiThread { generarRutaVehicular(true) }
        }.start()
    }

    private fun generarRutaVehicular(optimizarSeguridad: Boolean) {
        if (!isActivityActive || isFinishing) return
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
                    var bestAve: List<LatLng>? = null
                    for (avenue in primaryAvenues) {
                        if (isAvenueAligned(startLocation, endLocation, avenue)) {
                            bestAve = avenue
                            break
                        }
                    }

                    var corridorRoute: RouteData? = null
                    var corridorAnalysis: RiskManager.SafetyAnalysis? = null

                    if (bestAve != null) {
                        safeWaypoints.add(findNearestPoint(startLocation, bestAve))
                        safeWaypoints.add(findNearestPoint(endLocation, bestAve))
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
                         finalAnalysis.cameraCount > fastestResult.second.cameraCount)) {
                        isSafeRerouteUsed = true
                    } else {
                        finalRoute = fastestResult.first
                        finalAnalysis = fastestResult.second
                        isSafeRerouteUsed = false
                    }
                }
                
                runOnUiThread {
                    if (!isActivityActive || isFinishing) return@runOnUiThread
                    progressBar.visibility = View.GONE
                    if (finalRoute.points.isNotEmpty()) {
                        fullRoutePoints = finalRoute.points
                        totalPlannedDistance = finalRoute.distance
                        navigationSteps = finalRoute.steps
                        terminar.isEnabled = true
                        terminar.alpha = 1.0f
                        btnIniciarNav.visibility = View.VISIBLE
                        startTracking()
                        
                        val safetyOnPath = identifySafetyOnPath(finalRoute.points, 100.0)
                        drawEverything(finalRoute.points, safetyOnPath)
                        
                        tipoRutaTitulo.text = if (optimizarSeguridad && isSafeRerouteUsed) finalAnalysis.label else if (optimizarSeguridad) "Máxima Seguridad Detectada" else "Ruta Vehicular más Rápida"
                        
                        if (optimizarSeguridad && !isSafeRerouteUsed) {
                            Toast.makeText(this@recorrido_vehicular, "La ruta directa ofrece el mayor nivel de monitoreo disponible", Toast.LENGTH_LONG).show()
                        }
                        
                        textoRuta.text = "Cámaras: ${finalAnalysis.cameraCount} | Patrullas: ${finalAnalysis.pathCount}\nRiesgo: ${finalAnalysis.riskLevel} | Tiempo: ${formatTiempo(finalRoute.duration)}"

                        if (finalAnalysis.cameraCount == 0 && optimizarSeguridad) {
                            AlertDialog.Builder(this@recorrido_vehicular)
                                .setTitle("Aviso de Monitoreo")
                                .setMessage("Trayecto sin cámaras C5 detectadas. Conduce por avenidas principales.")
                                .setPositiveButton("OK", null).show()
                        }
                        
                        navEtaTime.text = formatTiempo(finalRoute.duration)
                        navTotalDistance.text = String.format("%.1f km", finalRoute.distance / 1000)

                        val diff = ((finalRoute.duration - directDuration) / 60).toInt()
                        if (optimizarSeguridad && isSafeRerouteUsed && diff > 0) {
                            diffTiempo.text = "+$diff min por vigilancia"; diffTiempo.setTextColor(ContextCompat.getColor(this, R.color.naranja))
                        } else {
                            diffTiempo.text = "Ruta optimizada"
                            diffTiempo.setTextColor(ContextCompat.getColor(this, R.color.verde))
                        }

                        // Mostrar Aviso Inicial
                        mostrarAvisoInicialVehicular(finalAnalysis, isSafeRerouteUsed, optimizarSeguridad)
                    }
                }
            } catch (e: Exception) { runOnUiThread { progressBar.visibility = View.GONE } }
        }.start()
    }

    private fun mostrarAvisoInicialVehicular(analysis: RiskManager.SafetyAnalysis, safeUsed: Boolean, requestedSafe: Boolean) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isNight = hour >= 19 || hour <= 6
        
        val message = StringBuilder()
        message.append("Esta ruta utiliza datos de infraestructura del C5 y el histórico de incidentes. La sugerencia es estadística y no sustituye el juicio del conductor.\n\n")
        
        if (isNight) {
            message.append("🌙 AVISO NOCTURNO: Se han priorizado avenidas con mayor densidad de monitoreo y patrullaje.\n\n")
        }

        if (requestedSafe && !safeUsed) {
            message.append("✅ NOTA: El trayecto más directo ya es la opción con mayor vigilancia disponible.\n\n")
        } else if (!requestedSafe) {
            message.append("⚠️ Trayecto Rápido: Prioriza el tiempo de llegada. Podría pasar por zonas con menor densidad de cámaras.\n\n")
        }

        AlertDialog.Builder(this)
            .setTitle("Contrato de Navegación Segura")
            .setMessage(message.toString())
            .setPositiveButton("Entendido", null)
            .show()
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

    private fun fetchOSRMRoute(waypoints: List<LatLng>): List<RouteData> {
        val results = mutableListOf<RouteData>()
        try {
            val coords = waypoints.joinToString(";") { "${it.longitude},${it.latitude}" }
            val urlStr = "https://routing.openstreetmap.de/routed-car/route/v1/driving/$coords?overview=full&geometries=polyline&continue_straight=true&alternatives=true&steps=true"
            val conn = URL(urlStr).openConnection() as HttpsURLConnection
            conn.setRequestProperty("User-Agent", "iGoSafeApp/1.0")
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
        actualizarLineaRecorrida()
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
                actualizarPosicionVehiculo(LatLng(location.latitude, location.longitude), location.bearing)
            }
        }
    }

    private fun actualizarPosicionVehiculo(pos: LatLng, currentBearing: Float) {
        if (!::mMap.isInitialized) return
        if (actualUserMarker == null) {
            actualUserMarker = mMap.addMarker(MarkerOptions()
                .position(pos)
                .title("Tú")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
                .anchor(0.5f, 0.5f))
        } else { actualUserMarker?.position = pos }
        if (walkedPoints.isEmpty() || calcularDistancia(walkedPoints.last(), pos) > 10.0) {
            if (walkedPoints.isNotEmpty()) {
                totalWalkedDistance += calcularDistancia(walkedPoints.last(), pos).toDouble()
            }
            walkedPoints.add(pos); actualizarLineaRecorrida()
        }
        
        // Verificación automática de llegada al destino para vehículos
        val distanciaAlDestino = calcularDistancia(pos, endLocation)
        if (distanciaAlDestino < 40.0) { // Umbral de 40 metros para vehículos
            Toast.makeText(this, "¡Has llegado a tu destino!", Toast.LENGTH_LONG).show()
            terminar.performClick()
            return
        }
        
        if (isNavigationActive) {
            updateNavigationUI(pos, currentBearing)
        }
    }

    private fun updateNavigationUI(pos: LatLng, currentBearing: Float) {
        if (navigationSteps.isEmpty()) return
        
        // 1. Detección de Desvío (Límite 30 metros para vehículos)
        val isOffPath = !PolyUtil.isLocationOnPath(pos, fullRoutePoints, false, 30.0)
        
        // 2. Detección de Dirección Contraria (Wrong Way)
        val currentTarget = navigationSteps.getOrNull(nextStepIndex) ?: return
        val expectedBearing = getBearing(pos, currentTarget.location)
        var isWrongWay = false
        
        if (currentBearing != 0f) { // Solo si el GPS reporta dirección
            val diff = Math.abs(expectedBearing - currentBearing)
            val normalizedDiff = if (diff > 180) 360 - diff else diff
            if (normalizedDiff > 130) { // Desviación de más de 130 grados
                isWrongWay = true
            }
        }

        val bannerText = findViewById<TextView>(R.id.bannerOffRouteText)
        if (isWrongWay) {
            bannerText?.text = "⚠️ DIRECCIÓN CONTRARIA: Regresa a la ruta protegida"
            bannerOffRoute.visibility = View.VISIBLE
        } else if (isOffPath) {
            bannerText?.text = "⚠️ Fuera de Ruta Protegida: Has abandonado el camino verificado"
            bannerOffRoute.visibility = View.VISIBLE
        } else {
            bannerOffRoute.visibility = View.GONE
        }

        if (bannerOffRoute.visibility == View.GONE && (isOffPath || isWrongWay)) {
            Toast.makeText(this, if (isWrongWay) "⚠️ Vas en dirección contraria" else "⚠️ Has abandonado la ruta protegida", Toast.LENGTH_SHORT).show()
        }

        val distToNext = calcularDistancia(pos, currentTarget.location)
        
        if (distToNext < 20.0 && nextStepIndex < navigationSteps.size - 1) {
            nextStepIndex++
        }
        
        val next = navigationSteps[nextStepIndex]
        val dNext = calcularDistancia(pos, next.location)
        
        navDistanceToNext.text = if (dNext > 1000) String.format("%.1f km", dNext/1000) else String.format("%.0f m", dNext)
        navInstructionText.text = translateManeuver(next.maneuverType, next.modifier, next.name)
        
        // 2. Alerta de "Punto Ciego"
        verificarPuntoCiegoVehicular(pos)

        // Actualizar icono de dirección
        actualizarIconoNavegacion(next.maneuverType, next.modifier)
        
        val cameraPosition = CameraPosition.Builder()
            .target(pos)
            .zoom(18.5f)
            .tilt(45f)
            .bearing(getBearing(pos, next.location))
            .build()
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    private var lastBlindSpotAlertTime = 0L
    private fun verificarPuntoCiegoVehicular(pos: LatLng) {
        val now = System.currentTimeMillis()
        if (now - lastBlindSpotAlertTime < 90000) return // Solo una alerta cada 1.5 min para vehículos
        
        val nearbyCameras = RiskManager.getCamerasOnPath(listOf(pos))
        if (nearbyCameras.isEmpty()) {
            Toast.makeText(this, "⚠️ Baja densidad de monitoreo C5 en este tramo.", Toast.LENGTH_LONG).show()
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
            .width(14f)
            .zIndex(10f))
    }

    private fun startTracking() {
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build()
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {}
    }

    override fun onDestroy() { isActivityActive = false; fusedLocationClient.removeLocationUpdates(locationCallback); super.onDestroy() }
    private fun formatTiempo(segundos: Double): String {
        val min = (segundos / 60).toInt()
        return if (min > 59) "${min/60}h ${min%60}min" else "${min}min"
    }
}