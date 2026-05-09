package com.icm.igosafeapp

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.maps.android.PolyUtil
import org.json.JSONObject
import java.util.Calendar

/**
 * RiskManager optimizado: Los cálculos complejos de H3 se delegan al servidor.
 * El cliente mantiene una indexación ligera para visualización rápida.
 */
object RiskManager {

    private lateinit var functions: FirebaseFunctions
    
    data class RiskEntry(val riesgo_delito: Int = 0, val riesgo_vial: Int = 0)
    
    private var allCameras: List<LatLng> = emptyList()
    private var allPaths: List<LatLng> = emptyList()
    private var riskData: Map<String, RiskEntry> = emptyMap()

    fun initialize(context: Context) {
        functions = Firebase.functions("us-central1") // Ajustar según tu región de Firebase

        Thread {
            try {
                Log.d("RiskManager", "Cargando datos base para visualización...")
                
                // 1. Cargar Cámaras
                try {
                    val jsonC5String = context.assets.open("postes_c5.json").bufferedReader().use { it.readText() }
                    val c5Array = JSONObject(jsonC5String).getJSONArray("Poste C5")
                    val cameraList = mutableListOf<LatLng>()
                    for (i in 0 until c5Array.length()) {
                        val p = c5Array.getJSONObject(i)
                        cameraList.add(LatLng(p.getDouble("latitud"), p.getDouble("longitud")))
                    }
                    allCameras = cameraList
                } catch (e: Exception) { Log.e("RiskManager", "Error postes: ${e.message}") }

                // 2. Cargar Senderos
                try {
                    val jsonPathsString = context.assets.open("mi_calle.json").bufferedReader().use { it.readText() }
                    val pathsArray = JSONObject(jsonPathsString).getJSONArray("data")
                    val pathList = mutableListOf<LatLng>()
                    for (i in 0 until pathsArray.length()) {
                        val s = pathsArray.getJSONObject(i)
                        val geoPoint = s.optString("geo_point_2d", "")
                        if ((s.optInt("senderopro") > 0 || s.optInt("senderoisn") > 0) && geoPoint.contains(",")) {
                            val coords = geoPoint.split(",")
                            pathList.add(LatLng(coords[0].trim().toDouble(), coords[1].trim().toDouble()))
                        }
                    }
                    allPaths = pathList
                } catch (e: Exception) { Log.e("RiskManager", "Error senderos: ${e.message}") }

                Log.d("RiskManager", "RiskManager listo. Modo H3-Cloud activado.")
            } catch (e: Exception) {
                Log.e("RiskManager", "Error en inicialización: ${e.message}")
            }
        }.start()
    }

    /**
     * Envía la ruta al servidor para un análisis profundo con H3.
     */
    fun analyzeRouteOnServer(points: List<LatLng>, callback: (SafetyAnalysis?) -> Unit) {
        val routeData = points.map { mapOf("lat" to it.latitude, "lng" to it.longitude) }
        val data = hashMapOf("points" to routeData)

        functions.getHttpsCallable("analyzeSafety")
            .call(data)
            .addOnSuccessListener { result ->
                val res = result.getData() as? Map<String, Any>
                if (res != null) {
                    val analysis = SafetyAnalysis(
                        score = (res["score"] as? Number)?.toDouble() ?: 0.0,
                        confidence = 100,
                        cameraCount = (res["cameraCount"] as? Number)?.toInt() ?: 0,
                        pathCount = (res["pathCount"] as? Number)?.toInt() ?: 0,
                        riskLevel = res["level"] as? String ?: "Desconocido",
                        label = res["label"] as? String ?: "Análisis de Servidor"
                    )
                    callback(analysis)
                }
            }
            .addOnFailureListener {
                Log.e("RiskManager", "Error en servidor: ${it.message}")
                callback(null)
            }
    }

    // Métodos de utilidad local para respuesta inmediata de la UI
    fun getCamerasOnPath(points: List<LatLng>): List<LatLng> {
        if (points.isEmpty()) return emptyList()
        val bounds = getBoundingBox(points)
        // Tolerancia de 30 metros
        return allCameras.filter { bounds.contains(it) && PolyUtil.isLocationOnPath(it, points, true, 30.0) }.take(50)
    }

    fun getPathsOnPath(points: List<LatLng>): List<LatLng> {
        if (points.isEmpty()) return emptyList()
        val bounds = getBoundingBox(points)
        return allPaths.filter { bounds.contains(it) && PolyUtil.isLocationOnPath(it, points, true, 100.0) }
    }

    private fun getBoundingBox(points: List<LatLng>): LatLngBounds {
        val builder = LatLngBounds.Builder()
        points.forEach { builder.include(it) }
        return builder.build()
    }

    data class SafetyAnalysis(val score: Double, val confidence: Int, val cameraCount: Int, val pathCount: Int, val riskLevel: String, val label: String)

    private fun isNightTime(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 19 || hour <= 6
    }

    /**
     * Realiza un análisis local inmediato basado en la infraestructura.
     */
    fun analyzeRoute(points: List<LatLng>): SafetyAnalysis {
        val cameras = getCamerasOnPath(points).size
        val paths = getPathsOnPath(points).size
        val isNight = isNightTime()
        
        val camWeight = if (isNight) 3.0 else 1.0
        val pathWeight = if (isNight) 8.0 else 2.0
        
        val score = (cameras * camWeight + paths * pathWeight).coerceAtMost(100.0)
        
        val (level, label) = when {
            score >= 40 -> "Bajo" to "Ruta Protegida"
            score >= 15 -> "Medio" to "Monitoreo Constante"
            cameras > 0 || paths > 0 -> "Moderado" to "Vigilancia Parcial"
            else -> "Moderado" to "Zona Silenciosa"
        }

        return SafetyAnalysis(score, 100, cameras, paths, level, label)
    }

    fun getHexAddress(location: LatLng): String? = null
}