package com.icm.igosafeapp

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
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import java.io.InputStreamReader

data class Neighborhood(
    val name: String, // CMNOMSCAT
    val tasacalificada: Double,
    val geometry: Geometry
)

data class GeoJson(
    val type: String,
    val features: List<NeighborhoodFeature>
)

data class NeighborhoodFeature(
    val type: String,
    val properties: NeighborhoodProperties,
    val geometry: Geometry
)

data class NeighborhoodProperties(
    val CMNOMSCAT: String,
    val tasacalificada: Double
)

data class Geometry(
    val type: String,
    val coordinates: List<List<List<List<Double>>>>
)


class recorrido_peatonal : AppCompatActivity() {
        private lateinit var terminar: Button
        private lateinit var mMap: GoogleMap
        private lateinit var neighborhoods: List<Neighborhood>
        private lateinit var fusedLocationClient: FusedLocationProviderClient
        private lateinit var locationCallback: LocationCallback
        private val startLocation = LocationHolder.currentLocation
        private val endLocation = LatLng(4.6289793669028345, -74.06457327691162) // Ubicación final proporcionada
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_recorrido_peatonal)
            terminar = findViewById(R.id.finalizar)


            terminar.setOnClickListener {
                // Lógica para finalizar la actividad o mostrar la ruta
                val intent = Intent(this, review_ruta::class.java)
                startActivity(intent)
            }
            loadGeoJson()
            initializeMap()
        }



        private fun initializeMap() {
            val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
            mapFragment.getMapAsync { googleMap ->
                mMap = googleMap
                drawRoute()
                evaluateRoute()
                addMarkers()
            }
        }




    private fun loadGeoJson() {
        val inputStream = assets.open("Seguridad_Indica_Barrios.geojson")
        val reader = InputStreamReader(inputStream)
        val geoJsonData = Gson().fromJson(reader, GeoJson::class.java)

        // Extrae las propiedades de cada barrio en una lista
        neighborhoods = geoJsonData.features.map {
            Neighborhood(
                name = it.properties.CMNOMSCAT,
                tasacalificada = it.properties.tasacalificada,
                geometry = it.geometry // Asumiendo que Geometry es igual
            )
        }
    }


    private fun drawRoute() {
        // Aquí deberías implementar la lógica para dibujar la ruta real
        // Por ahora, dibujamos una línea recta entre los puntos de inicio y fin
        val routePolyline = mMap.addPolyline(
            PolylineOptions()
                .add(startLocation, endLocation)
                .width(5f)
                .color(android.graphics.Color.BLUE)
        )
    }

    private fun evaluateRoute() {
        var totalRating = 0.0
        var neighborhoodCount = 0

        for (neighborhood in neighborhoods) {
            val coordinates = neighborhood.geometry.coordinates
            // Aplanar la lista de coordenadas en caso de ser un MultiPolygon
            val flattenedCoordinates = if (coordinates.size > 1) {
                // Para MultiPolygon, toma la primera serie de coordenadas
                coordinates[0] // O usa una lógica que elija el conjunto correcto
            } else {
                // Para Polygon, usa las coordenadas tal cual
                coordinates[0]
            }

            // Verificar si el punto de inicio o de fin está dentro del polígono
            if (isPointInPolygon(startLocation!!, flattenedCoordinates) ||
                isPointInPolygon(endLocation, flattenedCoordinates)) {
                totalRating += neighborhood.tasacalificada
                neighborhoodCount++
            }
        }

        val averageRating = if (neighborhoodCount > 0) totalRating / neighborhoodCount else 0.0

        println("La ruta pasa por $neighborhoodCount barrios.")
        println("La calificación promedio de la ruta es: $averageRating")
    }

    private fun addMarkers() {
        for (neighborhood in neighborhoods) {
            // Supongamos que la geometría es un polígono y deseas marcar el centro
            val coordinates = neighborhood.geometry.coordinates[0] // Asumiendo que esto es un polígono
            val latLng = LatLng(coordinates[0][1], coordinates[0][0]) // Obteniendo el primer punto como ejemplo

            mMap.addMarker(MarkerOptions().position(latLng).title(neighborhood.name))
        }
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

    data class Barrios(val geometry: JSONObject, val calificacion: Double)
