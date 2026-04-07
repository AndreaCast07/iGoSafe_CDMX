package com.icm.igosafeapp

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.BitmapDescriptor
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

class recorrido_peatonal : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var terminar: Button
    private lateinit var mMap: GoogleMap
    private lateinit var progressBar: ProgressBar
    private lateinit var textoRuta: TextView
    private var startLocation = LatLng(0.0, 0.0)
    private var endLocation = LatLng(0.0, 0.0)
    
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recorrido_peatonal)
        isActivityActive = true
        progressBar = findViewById(R.id.progressBar)
        textoRuta = findViewById(R.id.textoRuta)
        terminar = findViewById(R.id.finalizar)
        startLocation = LatLng(intent.getDoubleExtra("startLat", 0.0), intent.getDoubleExtra("startLong", 0.0))
        endLocation = LatLng(intent.getDoubleExtra("endLat", 0.0), intent.getDoubleExtra("endLong", 0.0))
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        terminar.setOnClickListener { cerrarPantallaLimpia() }
    }

    private fun cerrarPantallaLimpia() {
        isActivityActive = false
        if (::mMap.isInitialized) mMap.stopAnimation()
        startActivity(Intent(this, review_ruta::class.java))
        finish()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.standard))
        mMap.addMarker(MarkerOptions().position(startLocation).title("Inicio"))
        mMap.addMarker(MarkerOptions().position(endLocation).title("Destino"))
        generarRutaPeatonalOptimizada()
    }

    private fun generarRutaPeatonalOptimizada() {
        if (!isOnline()) return
        progressBar.visibility = View.VISIBLE
        Thread {
            try {
                val directRoute = fetchOSRMRoute(listOf(startLocation, endLocation))
                val directPoints = if (directRoute.isNotEmpty()) directRoute else listOf(startLocation, endLocation)
                
                val finalWaypoints = mutableListOf<LatLng>()
                finalWaypoints.add(startLocation)

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

                // Regla 1: Waypoint Thinning (Tolerancia 50.0)
                for (wp in candidates) {
                    if (!PolyUtil.isLocationOnPath(wp, directPoints, false, 50.0)) {
                        finalWaypoints.add(wp)
                    }
                }

                finalWaypoints.add(endLocation)
                val finalPoints = fetchOSRMRoute(cleanWaypoints(finalWaypoints))
                
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    if (finalPoints.isNotEmpty()) {
                        val safety = identifySafety(finalPoints)
                        val safetyCounts = safety.groupBy { it.type }.mapValues { it.value.size }
                        val summary = "Cámaras: ${safetyCounts[2] ?: 0} | Senderos: ${safetyCounts[5] ?: 0}"
                        if (isActivityActive && !isFinishing) {
                            drawAll(finalPoints, safety)
                            textoRuta.text = "Ruta Vigilada | $summary"
                        }
                    } else {
                        Toast.makeText(this, "No se pudo trazar una ruta segura", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) { 
                Log.e("Ruta", "Error: ${e.message}")
                mainHandler.post { if (isActivityActive) progressBar.visibility = View.GONE } 
            }
        }.start()
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
            if (dist[0] > 100) {
                cleaned.add(points[i])
            }
        }
        
        val distToEnd = FloatArray(1)
        Location.distanceBetween(cleaned.last().latitude, cleaned.last().longitude, points.last().latitude, points.last().longitude, distToEnd)
        if (distToEnd[0] < 10 && cleaned.size > 1) {
            cleaned.removeAt(cleaned.size - 1)
        }
        cleaned.add(points.last())
        return cleaned
    }

    private fun fetchOSRMRoute(points: List<LatLng>): List<LatLng> {
        if (points.size < 2) return emptyList()
        try {
            val coords = points.joinToString(";") { "${it.longitude},${it.latitude}" }
            
            // Usar radiuses = unlimited para que siempre haga snap a la calle
            val radiusesList = mutableListOf<String>()
            for (i in points.indices) {
                if (i == 0 || i == points.size - 1) {
                    radiusesList.add("unlimited")
                } else {
                    radiusesList.add("100")
                }
            }
            val radiuses = radiusesList.joinToString(";")

            val urlStr = "https://routing.openstreetmap.de/routed-foot/route/v1/foot/$coords?overview=full&geometries=polyline&continue_straight=true&radiuses=$radiuses"
            Log.d("OSRM_Peatonal", "URL Request: $urlStr")
            val conn = URL(urlStr).openConnection() as HttpsURLConnection
            conn.setRequestProperty("User-Agent", "iGoSafeApp/1.5")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            if (conn.responseCode == 200) {
                val res = conn.inputStream.bufferedReader().readText()
                val routes = JSONObject(res).optJSONArray("routes")
                if (routes != null && routes.length() > 0) {
                    return PolyUtil.decode(routes.getJSONObject(0).getString("geometry"))
                }
            }
        } catch (e: Exception) { Log.e("OSRM", "Error: ${e.message}") }
        return emptyList()
    }

    private fun calculateBearing(start: LatLng, end: LatLng): Float {
        val res = FloatArray(2)
        Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, res)
        return (res[1] + 360) % 360
    }

    private fun identifySafety(path: List<LatLng>): List<SafetyFeature> {
        val list = mutableListOf<SafetyFeature>()
        try {
            val c5 = JSONObject(assets.open("postes_c5.json").bufferedReader().readText()).getJSONArray("Poste C5")
            for (i in 0 until c5.length() step 4) {
                val p = c5.getJSONObject(i)
                val pos = LatLng(p.getDouble("latitud"), p.getDouble("longitud"))
                if (PolyUtil.isLocationOnPath(pos, path, true, 40.0)) list.add(SafetyFeature(pos, "Cámara C5", 2))
            }
            val senderos = JSONObject(assets.open("mi_calle.json").bufferedReader().readText()).getJSONArray("data")
            for (i in 0 until senderos.length()) {
                val s = senderos.getJSONObject(i)
                if (s.optInt("senderopro") > 0 || s.optInt("senderoisn") > 0) {
                    val coords = s.getString("geo_point_2d").split(",")
                    val pos = LatLng(coords[0].toDouble(), coords[1].toDouble())
                    if (PolyUtil.isLocationOnPath(pos, path, true, 60.0)) list.add(SafetyFeature(pos, "Sendero Seguro", 5))
                }
            }
        } catch (e: Exception) {}
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
        mMap.addPolyline(PolylineOptions().addAll(points).color(Color.parseColor("#2ECC71")).width(15f))
        
        val cameraIcon = getIconFromVector(R.drawable.ic_camera, Color.YELLOW, 80)
        val senderoIcon = getResizedBitmap(R.drawable.icon_peaton, 100)
        val patrolIcon = getResizedBitmap(R.drawable.icon_auto, 100)
        
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
        try { mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 160)) } catch (e: Exception) {}
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    override fun onDestroy() { isActivityActive = false; super.onDestroy() }
}
