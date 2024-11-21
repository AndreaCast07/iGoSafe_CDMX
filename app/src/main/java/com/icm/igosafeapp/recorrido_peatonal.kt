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
    private lateinit var locationCallback: LocationCallback
    private var startLocation = Marker
    private var endLocation = LatLng


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recorrido_peatonal)

        // Configura el mapa
        try {
            progressBar = findViewById(R.id.progressBar)
            textoRuta = findViewById(R.id.textoRuta)
            terminar = findViewById(R.id.finalizar)

            val startLatitude = intent.getDoubleExtra("start_latitude", 0.0)
            val startLongitude = intent.getDoubleExtra("start_longitude", 0.0)
            val endLatitude = intent.getDoubleExtra("end_latitude", 0.0)
            val endLongitude = intent.getDoubleExtra("end_longitude", 0.0)

            // Crear los marcadores en el mapa con las coordenadas recibidas
            val startLocationLatLng = LatLng(startLatitude, startLongitude)
            val endLocationLatLng = LatLng(endLatitude, endLongitude)

            startLocation = mMap.addMarker(MarkerOptions().position(startLocationLatLng).title("Inicio"))
            endLocation = mMap.addMarker(MarkerOptions().position(endLocationLatLng).title("Destino"))

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

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.mapType = GoogleMap.MAP_TYPE_NORMAL

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

    private fun evaluateRoute(routeCoordinates: List<LatLng>): List<String> {
        val barriosPorRuta = mutableListOf<String>()

        for (neighborhood in neighborhoods) {
            val coordinates = neighborhood.geometry.coordinates[0] // Asumiendo que esto es un polígono

            for (coord in routeCoordinates) {
                if (isPointInPolygon(coord, coordinates)) {
                    barriosPorRuta.add(neighborhood.name)
                    break
                }
            }
        }
        return barriosPorRuta
    }



    private fun getOSRMDistance(start: LatLng, end: LatLng, callback: (distance: Double, routeCoordinates: List<LatLng>) -> Unit) {

        val urlStr = "https://routing.openstreetmap.de/routed-foot/route/v1/drivingtra/${start.longitude},${start.latitude};${end.longitude},${end.latitude}"
        Log.d("RecorridoPeatonal", "OSRM URL: $urlStr")

        // Hacer la solicitud en segundo plano
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


                        // Parsear la respuesta JSON
                        val jsonResponse = JSONObject(response)
                        val routes = jsonResponse.getJSONArray("routes")
                        if (routes.length() > 0) {
                            val route = routes.getJSONObject(0)
                            Log.d("RecorridoPeatonalOMG", "Routes length: ${routes.length()}")
                            val distance = route.getDouble("distance") // Distancia en metros
                            val geometry = route.getString("geometry")
                            Log.d("RecorridoPeatonalOMG", "Gometry: $geometry")
                            //val coordinates = geometry.getJSONArray("coordinates")

                            val routeCoordinates = decodePolyline(geometry)

                            // Validar barrios en la ruta
                            val barriosPorRuta = evaluateRoute(routeCoordinates)
                            val barriosNoSeguros = barriosPorRuta.filter { barrio ->
                                neighborhoods.any { it.name == barrio && it.tasacalificada < 2.5 }
                            }

                            // Convertir las coordenadas a List<LatLng>
                            //val routeCoordinates = mutableListOf<LatLng>()
                            // Log adicional para verificar las coordenadas de la ruta
                            /*Log.d("RecorridoPeatonalOMG", "Route coordinates: $routeCoordinates")

                            for (i in 0 until coordinates.length()) {
                                val coord = coordinates.getJSONArray(i)
                                val lng = coord.getDouble(0)
                                val lat = coord.getDouble(1)
                                routeCoordinates.add(LatLng(lat, lng))
                            }*/
                            if (barriosNoSeguros.isNotEmpty()) {
                                // Generar puntos intermedios para evitar barrios inseguros
                                Log.w("barriosNoSeguros", "Ruta pasa por barrios inseguros: $barriosNoSeguros")

                                // Generar puntos intermedios para rodear el barrio
                                val safeRouteWithWaypoints = generateSafeRouteWithWaypoints(start, end, barriosNoSeguros, neighborhoods)
                                return Pair(distance, safeRouteWithWaypoints)
                                /*if (safeRouteWithWaypoints.isNotEmpty()) {
                                    // Volver a consultar la API con la nueva ruta que evita barrios inseguros
                                    return getNewSafeRoute(start, end, safeRouteWithWaypoints)
                                }*/
                            }

                            // Si no hay barrios inseguros, devolver la ruta original
                            return Pair(distance, routeCoordinates)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RecorridoPeatonal", "Error: ${e.message}", e)
                    e.printStackTrace()
                }
                return Pair(0.0, emptyList()) // Devuelve distancia 0 y lista vacía en caso de error
            }

            override fun onPostExecute(result: Pair<Double, List<LatLng>>) {
                //callback(result.first, result.second) // Llama al callback con la distancia y las coordenadas
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
    private fun getNewSafeRoute(start: LatLng, end: LatLng, waypoints: List<LatLng>): Pair<Double, List<LatLng>> {
        // Construir la nueva URL de la API de OSRM con los puntos intermedios generados
        val waypointsStr = waypoints.joinToString(";") { "${it.longitude},${it.latitude}" }
        Log.d("RecorridoPeatonal", "inicio: $start")
        Log.d("RecorridoPeatonal", "inicio: $waypointsStr")
        Log.d("RecorridoPeatonal", "inicio: $end")
        val urlStr = "https://router.project-osrm.org/route/v1/walking/${start.longitude},${start.latitude};$waypointsStr;${end.longitude},${end.latitude}"
        Log.d("RecorridoPeatonal", "Nueva ruta con desvíos: $urlStr")

        try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val response = inputStream.bufferedReader().use { it.readText() }

                // Parsear la respuesta JSON
                val jsonResponse = JSONObject(response)
                val routes = jsonResponse.getJSONArray("routes")
                if (routes.length() > 0) {
                    val route = routes.getJSONObject(0)
                    val distance = route.getDouble("distance") // Distancia en metros
                    val geometry = route.getString("geometry")
                    val routeCoordinates = decodePolyline(geometry)

                    return Pair(distance, routeCoordinates)
                }
            }
        } catch (e: Exception) {
            Log.e("RecorridoPeatonal", "Error al obtener nueva ruta: ${e.message}", e)
            e.printStackTrace()
        }

        // Si hubo un error al obtener la nueva ruta, devolver un valor predeterminado
        return Pair(0.0, emptyList())
    }


    private fun generateSafeRouteWithWaypoints(
        start: LatLng,
        end: LatLng,
        barriosNoSeguros: List<String>, // Lista de nombres de barrios peligrosos
        neighborhoods: List<Neighborhood> // Lista de barrios con su información, incluidas las coordenadas
    ): List<LatLng> {
        // Suponemos que tienes una función que puede generar puntos de rodeo alrededor de barrios peligrosos
        val safeWaypoints = mutableListOf<LatLng>()

        // Aquí se asume que tienes una función que obtiene los puntos de rodeo alrededor de un barrio peligroso
        barriosNoSeguros.forEach { barrio ->
            // Obtener puntos cercanos o alrededor del barrio peligroso
            val pointsAroundNeighborhood = getPointsToAvoidNeighborhood(barrio, neighborhoods)
            safeWaypoints.addAll(pointsAroundNeighborhood)
        }

        // Si no se encuentran barrios peligrosos, simplemente retornamos una lista vacía de waypoints
        if (safeWaypoints.isEmpty()) {
            return emptyList()
        }

        // Ahora, vamos a construir una nueva ruta incluyendo los puntos de rodeo generados
        // Usamos la API de OSRM para crear la ruta, incluyendo los waypoints intermedios
        val waypointsParam = safeWaypoints.joinToString(";") { "${it.longitude},${it.latitude}" }
        val newRouteUrl = "https://router.project-osrm.org/route/v1/walking/" +
                "${start.longitude},${start.latitude};$waypointsParam;" +
                "${end.longitude},${end.latitude}?steps=true"

        Log.d("RecorridoPeatonal", "New Route URL: $newRouteUrl")

        // Hacer la solicitud a la API de OSRM para obtener la nueva ruta
        val url = URL(newRouteUrl)
        val connection = url.openConnection() as HttpsURLConnection
        connection.requestMethod = "GET"
        connection.connect()

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val inputStream = connection.inputStream
            val response = inputStream.bufferedReader().use { it.readText() }

            // Parsear la respuesta JSON
            val jsonResponse = JSONObject(response)
            val routes = jsonResponse.getJSONArray("routes")
            if (routes.length() > 0) {
                val route = routes.getJSONObject(0)
                val geometry = route.getString("geometry")
                // Decode the polyline geometry to LatLng coordinates
                return decodePolyline(geometry)
            }
        }

        return emptyList() // Retorna una lista vacía si no se obtiene una ruta válida
    }





    private fun getPointsToAvoidNeighborhood(
        barrio: String,
        neighborhoods: List<Neighborhood>
    ): List<LatLng> {
        // Buscar el barrio en la lista de neighborhoods
        val neighborhood = neighborhoods.find { it.name == barrio }

        // Si no encontramos el barrio, retornamos una lista vacía
        if (neighborhood == null) {
            Log.w("RecorridoPeatonal", "Barrio no encontrado: $barrio")
            return emptyList()
        }

        // Obtener la geometría del barrio (que es un polígono o multipolígono)
        val geometry = neighborhood.geometry


        // Dependiendo del tipo de geometría (Polygon o MultiPolygon), procedemos de diferentes maneras
        val coordinates = when (geometry.type) {
            "Polygon" -> geometry.coordinates // Una sola lista de coordenadas
            "MultiPolygon" -> geometry.coordinates // Si es MultiPolygon, tomamos directamente la lista
            else -> {
                Log.e("RecorridoPeatonal", "Tipo de geometría no soportado: ${geometry.type}")
                return emptyList()
            }
        }

        // Inicializar las variables para los límites globales
        var minLng = Double.MAX_VALUE
        var maxLng = -Double.MIN_VALUE
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MIN_VALUE
        Log.d("maxLat", "lat: ${maxLat}")

        // Iteramos sobre los polígonos dentro del MultiPolygon
        coordinates.forEach { polygon ->
            Log.d("GEOMETRY", "Coordinates: $coordinates")

            polygon.forEach { point ->
                // Asegurarse de que `point` es una lista con dos elementos: longitud y latitud
                if (point is List<*> && point.size == 2) {
                    val lng = point[0] as? Double
                    val lat = point[1] as? Double

                    // Asegurarse de que las coordenadas sean válidas (no nulas)
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

        // Ahora tenemos los límites del MultiPolygon
        // Podemos generar puntos fuera de estos límites (en este caso, desplazamos un poco los puntos hacia afuera)

        // Crear dos puntos fuera del barrio (más allá del bounding box)
        val displacedLatFirst = maxLat - 0.0001  // Desplazar hacia el norte
        val displacedLngFirst = maxLng - 0.0001 // Desplazar hacia el este
        Log.d("p1", "long: ${displacedLngFirst}")
        Log.d("p1", "lat: ${displacedLatFirst}")

        val displacedLatLast = minLat + 0.0001 // Desplazar hacia el sur
        val displacedLngLast = minLng + 0.0001 // Desplazar hacia el oeste
        Log.d("p2", "long: ${displacedLngLast}")
        Log.d("p2", "lat: ${displacedLatLast}")

        // Devolver los puntos fuera del barrio
        return listOf(
            //LatLng(displacedLatFirst, displacedLngFirst),
            LatLng(displacedLatLast, displacedLngLast)
        )
    }







    private fun getPolygonForBarrio(
        barrio: String,
        neighborhoods: List<Neighborhood>,
        geometryFactory: GeometryFactory
    ): Polygon? {
        val neighborhood = neighborhoods.find { it.name == barrio }
        return neighborhood?.geometry?.let { geo ->
            // Asumiendo que las coordenadas están en la estructura [List<List<List<Double>>>]
            val coordinates = geo.coordinates.flatten().map {
                // Cada coordenada dentro de la lista es un par de [lat, lng]
                Coordinate(it[1], it[0]) // La estructura es [lat, lng], pero `Coordinate` espera [lng, lat]
            }.toTypedArray()

            // Crear el polígono con las coordenadas aplanadas
            geometryFactory.createPolygon(coordinates)
        }
    }

    private fun calculateDetour(
        start: LatLng,
        end: LatLng,
        unsafeZones: List<Polygon>
    ): List<LatLng> {
        val midLat = (start.longitude + end.latitude) / 2
        val midLng = (start.longitude + end.latitude) / 2

        val detourPoint = LatLng(midLat + 0.01, midLng + 0.01) // Desviación básica
        return listOf(start, detourPoint, end)
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        // Decodificar la geometría Polyline codificada en formato String
        val polyline = PolyUtil.decode(encoded)

        // Mostrar las coordenadas decodificadas (LatLng)
        polyline.forEach {

            // Log adicional para verificar las coordenadas decodificadas
            Log.d("DecodedCoordinates", "Decoded LatLng: ${it.latitude}, ${it.longitude}")

        }
        return polyline
    }


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