package com.icm.igosafeapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.BitmapDescriptor
import android.graphics.Color
import android.location.Location
import android.os.Bundle
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

class recorrido_vehicular : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var terminar: Button
    private lateinit var mMap: GoogleMap
    private lateinit var progressBar: ProgressBar
    private lateinit var textoRuta: TextView
    private var startLocation = LatLng(0.0, 0.0)
    private var endLocation = LatLng(0.0, 0.0)

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
        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.standard))
        mMap.addMarker(MarkerOptions().position(startLocation).title("Inicio"))
        mMap.addMarker(MarkerOptions().position(endLocation).title("Destino"))
        generarRutaVehicularVigilada()
    }

    private fun generarRutaVehicularVigilada() {
        progressBar.visibility = View.VISIBLE
        Thread {
            try {
                val directRoute = fetchOSRMRoute(listOf(startLocation, endLocation))
                val directPoints = directRoute?.first ?: listOf(startLocation, endLocation)
                
                val finalWaypoints = mutableListOf<LatLng>()
                finalWaypoints.add(startLocation)

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

                // Regla 1: Waypoint Thinning (Tolerancia 50.0)
                for (wp in candidates) {
                    if (!PolyUtil.isLocationOnPath(wp, directPoints, false, 50.0)) {
                        finalWaypoints.add(wp)
                    }
                }

                finalWaypoints.add(endLocation)
                val refinedRoute = fetchOSRMRoute(cleanWaypoints(finalWaypoints))
                
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    if (refinedRoute != null) {
                        val safetyOnPath = identifySafetyOnPath(refinedRoute.first, 100.0)
                        val safetyCounts = safetyOnPath.groupBy { it.type }.mapValues { it.value.size }
                        val summary = "C5: ${safetyCounts[2] ?: 0} | Patrullas: ${safetyCounts[4] ?: 0} | Senderos: ${safetyCounts[5] ?: 0}"
                        if (!isFinishing) {
                            drawEverything(refinedRoute.first, safetyOnPath)
                            textoRuta.text = "$summary\nDistancia: ${"%.2f km".format(refinedRoute.second/1000)} | Tiempo: ${formatTiempo(refinedRoute.third)}"
                        }
                    } else {
                        Toast.makeText(this, "No se pudo generar la ruta vehicular segura", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) { 
                Log.e("Vehicular", "Fatal Error: ${e.message}")
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

    private fun fetchOSRMRoute(waypoints: List<LatLng>): Triple<List<LatLng>, Double, Double>? {
        if (waypoints.size < 2) return null
        try {
            val coords = waypoints.joinToString(";") { "${it.longitude},${it.latitude}" }
            
            val radiusesList = mutableListOf<String>()
            for (i in waypoints.indices) {
                if (i == 0 || i == waypoints.size - 1) {
                    radiusesList.add("unlimited")
                } else {
                    radiusesList.add("100")
                }
            }
            val radiuses = radiusesList.joinToString(";")

            val urlStr = "https://routing.openstreetmap.de/routed-car/route/v1/driving/$coords?overview=full&geometries=polyline&continue_straight=true&radiuses=$radiuses"
            Log.d("OSRM_Vehicular", "URL Request: $urlStr")
            
            val conn = URL(urlStr).openConnection() as HttpsURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "iGoSafeApp/1.0")
            
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val routes = json.optJSONArray("routes")
                if (routes != null && routes.length() > 0) {
                    val route = routes.getJSONObject(0)
                    val pts = PolyUtil.decode(route.getString("geometry"))
                    val dist = route.getDouble("distance")
                    val dur = route.getDouble("duration")
                    return Triple(pts, dist, dur)
                }
            } else {
                Log.e("OSRM_Vehicular", "HTTP Error: ${conn.responseCode}")
            }
        } catch (e: Exception) { 
            Log.e("OSRM_Vehicular", "Request Exception: ${e.message}") 
        }
        return null
    }

    private fun calculateBearing(start: LatLng, end: LatLng): Float {
        val res = FloatArray(2)
        Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, res)
        return (res[1] + 360) % 360
    }

    private fun identifySafetyOnPath(path: List<LatLng>, bufferMeters: Double): List<SafetyFeature> {
        val list = mutableListOf<SafetyFeature>()
        try {
            val c5 = JSONObject(assets.open("postes_c5.json").bufferedReader().readText()).getJSONArray("Poste C5")
            for (i in 0 until c5.length() step 4) {
                val p = c5.getJSONObject(i)
                val pos = LatLng(p.getDouble("latitud"), p.getDouble("longitud"))
                if (PolyUtil.isLocationOnPath(pos, path, true, bufferMeters)) {
                    list.add(SafetyFeature(pos, "Cámara C5", 2))
                }
            }
            val cq = JSONArray(assets.open("cuadrantes.json").bufferedReader().readText())
            for (i in 0 until cq.length()) {
                val p = cq.getJSONObject(i)
                val coords = p.getString("geo_point_2d").split(",")
                val pos = LatLng(coords[0].toDouble(), coords[1].toDouble())
                if (PolyUtil.isLocationOnPath(pos, path, true, bufferMeters + 20.0)) {
                    list.add(SafetyFeature(pos, "Sector: ${p.optString("sector")}", 4))
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

    private fun drawEverything(points: List<LatLng>, features: List<SafetyFeature>) {
        if (points.size < 2) return
        mMap.addPolyline(PolylineOptions().addAll(points).color(Color.BLUE).width(14f))
        
        val cameraIcon = getIconFromVector(R.drawable.ic_camera, Color.YELLOW, 80)
        val patrolIcon = getResizedBitmap(R.drawable.icon_auto, 100)
        val senderoIcon = getResizedBitmap(R.drawable.icon_peaton, 100)
        
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
        try { mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 150)) } catch (e: Exception) {}
    }

    private fun formatTiempo(segundos: Double): String {
        val min = (segundos / 60).toInt()
        return if (min > 59) "${min/60}h ${min%60}min" else "${min}min"
    }
}
