package com.icm.igosafeapp

import android.content.Context
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.util.AffineTransformation
import java.net.HttpURLConnection
import java.net.URL
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import org.json.JSONObject
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.AsyncTask
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.graphhopper.GraphHopper
import com.graphhopper.routing.util.EncodingManager
import com.graphhopper.util.PointList
import java.io.InputStreamReader
import android.graphics.Color
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.PolygonOptions
import com.google.maps.android.PolyUtil
import entidades.GeoJson
import entidades.Neighborhood
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import javax.net.ssl.HttpsURLConnection

class recorrido_peatonal : AppCompatActivity(), OnMapReadyCallback  {
    private lateinit var terminar: Button
    private lateinit var mMap: GoogleMap
    private lateinit var mapView: MapView
    private lateinit var neighborhoods: List<Neighborhood>
    private lateinit var progressBar: ProgressBar
    private lateinit var textoRuta: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var actuaLocation: Marker
    private var startLocation = LatLng(0.0, 0.0)
    private var endLocation = LatLng(0.0, 0.0)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recorrido_peatonal)

        try {
            progressBar = findViewById(R.id.progressBar)
            textoRuta = findViewById(R.id.textoRuta)
            terminar = findViewById(R.id.finalizar)

            val startLatitude = intent.getDoubleExtra("start_latitude", 0.0)
            val startLongitude = intent.getDoubleExtra("start_longitude", 0.0)
            val endLatitude = intent.getDoubleExtra("end_latitude", 0.0)
            val endLongitude = intent.getDoubleExtra("end_longitude", 0.0)

            startLocation = LatLng(startLatitude, startLongitude)
            endLocation = LatLng(endLatitude, endLongitude)

            val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
            mapFragment.getMapAsync(this)

            terminar.setOnClickListener {
                val intent = Intent(this, review_ruta::class.java)
                startActivity(intent)
            }

            loadGeoJson()
            progressBar.visibility = View.VISIBLE
            textoRuta.text = "Calculando la ruta..."


        } catch (e: Exception) {
            Log.e("RecorridoPeatonal", "Error al inicializar las vistas: ${e.message}")
        }
    }

    //Cargar el maá
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomGesturesEnabled = true
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.standard))

        if (startLocation != null && endLocation != null) {
            Log.d("RecorridoPeatonal", "Start Location: $startLocation, End Location: $endLocation")
            getOSRMDistance(startLocation, endLocation) { distance, routeCoordinates ->
                if (routeCoordinates.isNotEmpty()) {

                    drawRoute(routeCoordinates, startLocation, endLocation)
                    val barriosPorRuta = evaluateRoute(routeCoordinates)
                    val promedioCalificacion = calculateAverageRating(barriosPorRuta)

                    Log.d("Ruta", "La ruta pasa por los siguientes barrios: $barriosPorRuta")
                    Log.d("Ruta", "La calificación promedio de la ruta es: $promedioCalificacion")

                    progressBar.visibility = View.GONE
                    textoRuta.text = "Distancia: ${distance / 1000} km | Tiempo: ${distance / 1000 / 5} minutos"

                } else {
                    Log.e("RecorridoPeatonal", "No se pudieron obtener coordenadas de la ruta")
                }
            }
        } else {
            Log.e("RecorridoPeatonal", "Las ubicaciones de inicio o fin son nulas")
        }
    }

    //Calcular promedio del viaje
    private fun calculateAverageRating(barriosPorRuta: List<String>): Double {
        var totalRating = 0.0
        var neighborhoodCount = 0

        for (neighborhood in neighborhoods) {
            if (barriosPorRuta.contains(neighborhood.name)) {
                totalRating += neighborhood.tasacalificada
                neighborhoodCount++
            }
        }

        return if (neighborhoodCount > 0) totalRating / neighborhoodCount else 0.0
    }

    //Dibujar ruta
    private fun drawRoute(routeCoordinates: List<LatLng>, startLocation: LatLng, endLocation: LatLng) {
        Log.d("RecorridoPeatonal", "Dibujando ruta con las coordenadas: $routeCoordinates")
        if (routeCoordinates.isNotEmpty()) {
            mMap.addMarker(MarkerOptions().position(startLocation).title("Ubicación de Inicio"))

            mMap.addMarker(MarkerOptions().position(endLocation).title("Destino Final"))
            val polylineOptions = PolylineOptions()
                .addAll(routeCoordinates)
                .color(Color.BLACK)
                .width(10f)

            mMap.addPolyline(polylineOptions)
            val builder = LatLngBounds.Builder()
            builder.include(startLocation)
            builder.include(endLocation)
            routeCoordinates.forEach { builder.include(it) }

            val bounds = builder.build()
            val padding = 100
            val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)

            mMap.animateCamera(cameraUpdate)
        } else {
            Log.e("RecorridoPeatonal", "No se proporcionaron coordenadas para la ruta")
        }
    }

    //Cargar poligonos de barrios
    private fun loadGeoJson() {
        var reader: InputStreamReader? = null
        try {
            val inputStream = assets.open("Seguridad_Indica_Barrios_mal.geojson")
            reader = InputStreamReader(inputStream)
            val geoJsonData = Gson().fromJson(reader, GeoJson::class.java)


            if (geoJsonData.features.isNullOrEmpty()) {
                Log.e("GeoJSON", "No features found in the GeoJSON file")
                return
            }

            neighborhoods = geoJsonData.features.mapNotNull {
                try {
                    Neighborhood(
                        name = it.properties.CMNOMSCAT,
                        tasacalificada = it.properties.tasacalificada,
                        geometry = it.geometry
                    )
                } catch (e: Exception) {
                    Log.e("GeoJSON", "Error mapping feature: ${e.message}")
                    null
                }
            }

            Log.d("GeoJSON", "Loaded ${neighborhoods.size} neighborhoods")
        } catch (e: Exception) {
            Log.e("GeoJSON", "Error loading GeoJSON: ${e.message}")
        } finally {
            reader?.close()
        }
    }


    //Evaluar una ruta
    private fun evaluateRoute(routeCoordinates: List<LatLng>): List<String> {
        val barriosPorRuta = mutableListOf<String>()

        for (neighborhood in neighborhoods) {
            val coordinates = neighborhood.geometry.coordinates[0]

            for (coord in routeCoordinates) {
                if (isPointInPolygon(coord, coordinates)) {
                    barriosPorRuta.add(neighborhood.name)
                    break
                }
            }
        }
        return barriosPorRuta
    }

    //Generar ruta peatonal
    private fun getOSRMDistance(start: LatLng, end: LatLng, callback: (distance: Double, routeCoordinates: List<LatLng>) -> Unit) {

        val urlStr = "https://routing.openstreetmap.de/routed-foot/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}"
        Log.d("RecorridoPeatonal", "OSRM URL: $urlStr")

        object : AsyncTask<Void, Void, Pair<Double, List<LatLng>>>() {
            override fun doInBackground(vararg params: Void?): Pair<Double, List<LatLng>> {
                try {
                    val url = URL(urlStr)
                    val connection = url.openConnection() as HttpsURLConnection
                    connection.requestMethod = "GET"
                    connection.connect()

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val inputStream = connection.inputStream
                        val response = inputStream.bufferedReader().use { it.readText() }

                        val jsonResponse = JSONObject(response)
                        val routes = jsonResponse.getJSONArray("routes")
                        if (routes.length() > 0) {
                            val route = routes.getJSONObject(0)
                            Log.d("RecorridoPeatonalOMG", "Routes length: ${routes.length()}")
                            val distance = route.getDouble("distance")
                            val geometry = route.getString("geometry")
                            Log.d("RecorridoPeatonalOMG", "Gometry: $geometry")

                            val routeCoordinates = decodePolyline(geometry)

                            val barriosPorRuta = evaluateRoute(routeCoordinates)
                            val barriosNoSeguros = barriosPorRuta.filter { barrio ->
                                neighborhoods.any { it.name == barrio && it.tasacalificada < 2.5 }
                            }

                            if (barriosNoSeguros.isNotEmpty()) {
                                Log.w("barriosNoSeguros", "Ruta pasa por barrios inseguros: $barriosNoSeguros")

                                val safeRouteWithWaypoints = generateSafeRouteWithWaypoints(start, end, barriosNoSeguros, neighborhoods)
                                return Pair(distance, safeRouteWithWaypoints)
                            }

                            return Pair(distance, routeCoordinates)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RecorridoPeatonal", "Error: ${e.message}", e)
                    e.printStackTrace()
                }
                return Pair(0.0, emptyList())
            }

            override fun onPostExecute(result: Pair<Double, List<LatLng>>) {
                if (result.second.isEmpty()) {
                    Toast.makeText(
                        this@recorrido_peatonal,
                        "No se encontró una ruta segura.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                callback(result.first, result.second)
            }
        }.execute()
    }

    //Generar una ruta alterna evitando pasar por barrios peligrosos
    private fun generateSafeRouteWithWaypoints(start: LatLng, end: LatLng, barriosNoSeguros: List<String>, neighborhoods: List<Neighborhood>
    ): List<LatLng> {
        val safeWaypoints = mutableListOf<LatLng>()
        barriosNoSeguros.forEach { barrio ->
            val pointsAroundNeighborhood = getPointsToAvoidNeighborhood(barrio, neighborhoods)
            safeWaypoints.addAll(pointsAroundNeighborhood)
        }

        if (safeWaypoints.isEmpty()) {
            return emptyList()
        }

        val waypointsParam = safeWaypoints.joinToString(";") { "${it.longitude},${it.latitude}" }
        val newRouteUrl = "https://routing.openstreetmap.de/routed-foot/route/v1/driving/" +
                "${start.longitude},${start.latitude};$waypointsParam;" +
                "${end.longitude},${end.latitude}?steps=true"

        Log.d("RecorridoPeatonal", "New Route URL: $newRouteUrl")
        val url = URL(newRouteUrl)
        val connection = url.openConnection() as HttpsURLConnection
        connection.requestMethod = "GET"
        connection.connect()

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val inputStream = connection.inputStream
            val response = inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(response)
            val routes = jsonResponse.getJSONArray("routes")
            if (routes.length() > 0) {
                val route = routes.getJSONObject(0)
                val geometry = route.getString("geometry")
                return decodePolyline(geometry)
            }
        }

        return emptyList()
    }

    //Obtener puntos para esquivar la zona peligrosa
    private fun getPointsToAvoidNeighborhood(barrio: String, neighborhoods: List<Neighborhood>): List<LatLng> {
        val neighborhood = neighborhoods.find { it.name == barrio }

        if (neighborhood == null) {
            Log.w("RecorridoPeatonal", "Barrio no encontrado: $barrio")
            return emptyList()
        }

        val geometry = neighborhood.geometry

        val coordinates = when (geometry.type) {
            "Polygon" -> geometry.coordinates
            "MultiPolygon" -> geometry.coordinates
            else -> {
                Log.e("RecorridoPeatonal", "Tipo de geometría no soportado: ${geometry.type}")
                return emptyList()
            }
        }

        var minLng = Double.MAX_VALUE
        var maxLng = -Double.MIN_VALUE
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MIN_VALUE
        Log.d("maxLat", "lat: ${maxLat}")

        coordinates.forEach { polygon ->
            Log.d("GEOMETRY", "Coordinates: $coordinates")

            polygon.forEach { point ->
                if (point is List<*> && point.size == 2) {
                    val lng = point[0] as? Double
                    val lat = point[1] as? Double

                    if (lng != null && lat != null) {
                        minLng = minOf(minLng, lng)
                        maxLng = maxOf(maxLng, lat)
                        minLat = minOf(minLat, lat)
                        maxLat = maxOf(maxLat, lng)
                        Log.d("maxp1", "lat: ${maxLat}")
                    }
                }
            }
        }

        val displacedLatLast = minLat + 0.0001
        val displacedLngLast = minLng + 0.0001
        Log.d("p2", "long: ${displacedLngLast}")
        Log.d("p2", "lat: ${displacedLatLast}")

        return listOf(
            LatLng(displacedLatLast, displacedLngLast)
        )
    }

    //Decodificar la ruta
    private fun decodePolyline(encoded: String): List<LatLng> {
        val polyline = PolyUtil.decode(encoded)

        polyline.forEach {
            Log.d("DecodedCoordinates", "Decoded LatLng: ${it.latitude}, ${it.longitude}")
        }
        return polyline
    }


    //dentificar los puntos de un poligono
    private fun isPointInPolygon(point: LatLng, polygon: List<List<Double>>): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            if ((polygon[i][1] > point.latitude) != (polygon[j][1] > point.latitude) &&
                (point.longitude < (polygon[j][0] - polygon[i][0]) * (point.latitude - polygon[i][1]) / (polygon[j][1] - polygon[i][1]) + polygon[i][0])) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

}