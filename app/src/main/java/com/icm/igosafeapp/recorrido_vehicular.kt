package com.icm.igosafeapp

import android.content.Intent
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatRatingBar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.google.maps.android.PolyUtil
import com.graphhopper.jackson.ResponsePathDeserializer.decodePolyline
import com.icm.igosafeapp.manejoArchivos.CalificacionesManager
import entidades.GeoJson
import entidades.Neighborhood
import org.json.JSONObject
import org.osmdroid.views.MapView
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import javax.net.ssl.HttpsURLConnection

class recorrido_vehicular : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var terminar: Button
    private lateinit var mMap: GoogleMap
    private lateinit var mapView: MapView
    private lateinit var neighborhoods: List<Neighborhood>
    private lateinit var progressBar: ProgressBar
    private lateinit var textoRuta: TextView
    private lateinit var calificacionText: TextView
    private lateinit var startsCalificacion: AppCompatRatingBar
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var actuaLocation: Marker
    private var startLocation = LatLng(0.0, 0.0)
    private var endLocation = LatLng(0.0, 0.0)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recorrido_vehicular)

        try {
            progressBar = findViewById(R.id.progressBar)
            textoRuta = findViewById(R.id.textoRuta)
            terminar = findViewById(R.id.finalizar)
            calificacionText = findViewById(R.id.calificacionText)
            startsCalificacion = findViewById(R.id.ratingBar)

            val startLatitude = intent.getDoubleExtra("startLat", 0.0)
            val startLongitude = intent.getDoubleExtra("startLong", 0.0)
            val endLatitude = intent.getDoubleExtra("endLat", 0.0)
            val endLongitude = intent.getDoubleExtra("endLong", 0.0)

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
            Log.e("RecorridoVehicular", "Error al inicializar las vistas: ${e.message}")
        }
    }

    //Cargar el maá
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomGesturesEnabled = true
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.standard))

        if (startLocation != null && endLocation != null) {
            Log.d("RecorridoVehicular", "Start Location: $startLocation, End Location: $endLocation")
            getOSRMDistance(startLocation, endLocation) { distance, routeCoordinates ->
                if (routeCoordinates.isNotEmpty()) {
                    val calificacionManager = CalificacionesManager(this)

                    drawRoute(routeCoordinates, startLocation, endLocation)
                    val barriosPorRuta = evaluateRoute(routeCoordinates)
                    calcularPromedio(barriosPorRuta, calificacionManager) { promedioCalificacion ->
                        // Actualizar la UI con el promedio de calificación
                        //calificacionText.text = promedioCalificacion.toString()
                        calificacionText.text = String.format("%.2f", promedioCalificacion)
                        startsCalificacion.rating = promedioCalificacion.toFloat()

                        Log.d("Ruta", "La ruta pasa por los siguientes barrios: $barriosPorRuta")
                        Log.d("Ruta", "La calificación promedio de la ruta es: $promedioCalificacion")

                        // Cálculo del tiempo estimado en base a la distancia y velocidad
                        val velocidadPromedio = 5 // km/h
                        val distanciaKm = distance / 1000.0
                        val tiempoMinutos = (distanciaKm / velocidadPromedio) * 60

                        progressBar.visibility = View.GONE
                        textoRuta.text = "Distancia: ${"%.2f".format(distance / 1000.0)} km | Tiempo: ${"%.2f".format(tiempoMinutos)} minutos"
                    }
                    terminar.setOnClickListener {
                        val intent = Intent(this, review_ruta::class.java)
                        intent.putStringArrayListExtra("barriosPorRuta", ArrayList(barriosPorRuta))
                        startActivity(intent)
                    }

                }else {
                    Log.e("RecorridoVehicular", "No se pudieron obtener coordenadas de la ruta")
                }
            }
        } else {
            Log.e("RecorridoVehicular", "Las ubicaciones de inicio o fin son nulas")
        }
    }


    //Calcular promedio del viaje
    private fun obtenerOAgregarCalificacion(barrio: String, calificacionManager: CalificacionesManager, onResultado: (String, Double) -> Unit) {
        calificacionManager.obtenerCalificaciones { calificaciones ->
            val barrioExistente = calificaciones.any { it.barrio == barrio }

            if (!barrioExistente) {
                val calificacionPorDefecto = 3.0
                calificacionManager.guardarCalificacion(barrio, calificacionPorDefecto) { success ->
                    if (success) {
                        Log.d("CalificacionManager", "Barrio $barrio agregado con calificación $calificacionPorDefecto")
                        onResultado(barrio, calificacionPorDefecto)
                    } else {
                        Log.e("CalificacionManager", "Error al agregar barrio $barrio")
                        onResultado(barrio, 0.0) // Si no se pudo guardar, retornamos 0.0
                    }
                }
            } else {
                val calificacion = calificaciones.find { it.barrio == barrio }?.calificacion ?: 0.0
                onResultado(barrio, calificacion)
            }
        }
    }


    private fun calcularPromedio(barriosPorRuta: List<String>, calificacionManager: CalificacionesManager, onResultado: (Double) -> Unit) {
        var totalRating = 0.0
        var neighborhoodCount = 0

        for (barrio in barriosPorRuta) {
            obtenerOAgregarCalificacion(barrio, calificacionManager) { nombreBarrio, calificacion ->
                totalRating += calificacion
                neighborhoodCount++

                if (neighborhoodCount == barriosPorRuta.size) {
                    val averageRating = if (neighborhoodCount > 0) totalRating / neighborhoodCount else 0.0
                    onResultado(averageRating)  // Retorna el promedio a través del callback
                }
            }
        }
    }

    //Dibujar ruta
    private fun drawRoute(routeCoordinates: List<LatLng>, startLocation: LatLng, endLocation: LatLng) {
        Log.d("RecorridoVehicular", "Dibujando ruta con las coordenadas: $routeCoordinates")
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
            Log.e("RecorridoVehicular", "No se proporcionaron coordenadas para la ruta")
        }
    }

    //Cargar poligonos de barrios
    private fun loadGeoJson() {
        var reader: InputStreamReader? = null
        try {
            val inputStream = assets.open("Seguridad_Indica_Barrios_final.geojson")
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

        val urlStr = "https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}"
        Log.d("RecorridoVehicular", "OSRM URL: $urlStr")

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
                            Log.d("RecorridoVehicularOMG", "Routes length: ${routes.length()}")
                            val distance = route.getDouble("distance")
                            val geometry = route.getString("geometry")
                            Log.d("RecorridoVehicularOMG", "Gometry: $geometry")

                            val routeCoordinates = decodePolyline(geometry)
                            val barriosPorRuta = evaluateRoute(routeCoordinates)
                            obtenerCalificacionesDesdeFirebase(barriosPorRuta) { calificaciones ->
                                val barriosNoSeguros = barriosPorRuta.filter { barrio ->
                                    calificaciones[barrio]?.let { it < 2.5 } ?: false
                                }
                                if (barriosNoSeguros.isNotEmpty()) {
                                    Log.w("barriosNoSeguros", "Ruta pasa por barrios inseguros: $barriosNoSeguros")

                                    val safeRouteWithWaypoints = generateSafeRouteWithWaypoints(
                                        start,
                                        end,
                                        barriosNoSeguros,
                                        neighborhoods
                                    )

                                    runOnUiThread {
                                        callback(distance, safeRouteWithWaypoints)
                                    }
                                } else {
                                    runOnUiThread {
                                        callback(distance, routeCoordinates)
                                    }
                                }
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
                        this@recorrido_vehicular,
                        "No se encontró una ruta segura.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                runOnUiThread {
                    callback(result.first, result.second)
                }
            }
        }.execute()
    }

    private fun obtenerCalificacionesDesdeFirebase(
        barrios: List<String>,
        onComplete: (Map<String, Float>) -> Unit
    ) {
        val database = FirebaseDatabase.getInstance()
        val barrioRef = database.getReference("calificaciones")

        val calificaciones = mutableMapOf<String, Float>()
        val latch = CountDownLatch(barrios.size)  // Espera hasta que todos los barrios sean procesados

        for (barrio in barrios) {
            barrioRef.child(barrio).child("calificacion").get().addOnSuccessListener { snapshot ->
                val calificacion = snapshot.getValue(Float::class.java) ?: 0.0f
                calificaciones[barrio] = calificacion
            }.addOnFailureListener { e ->
                Log.e("FirebaseError", "Error al consultar la calificación para el barrio $barrio", e)
            }.addOnCompleteListener {
                latch.countDown()  // Decrementa el contador cuando termina el proceso para este barrio
            }
        }
        latch.await()
        onComplete(calificaciones)
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
        val newRouteUrl = "https://router.project-osrm.org/route/v1/driving/" +
                "${start.longitude},${start.latitude};$waypointsParam;" +
                "${end.longitude},${end.latitude}?steps=true"

        Log.d("RecorridoVehicular", "New Route URL: $newRouteUrl")
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