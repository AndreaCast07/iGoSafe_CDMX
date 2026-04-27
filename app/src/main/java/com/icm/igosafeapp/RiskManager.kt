package com.icm.igosafeapp

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uber.h3core.H3Core
import com.google.maps.android.PolyUtil
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

object RiskManager {
    private val h3: H3Core? by lazy {
        try {
            H3Core.newInstance()
        } catch (e: Throwable) {
            Log.e("RiskManager", "Error al inicializar H3: ${e.message}")
            null
        }
    }
    
    private const val HEX_RESOLUTION = 9 
    
    data class RiskEntry(val riesgo_delito: Int = 0, val riesgo_vial: Int = 0)
    
    // Almacenamos la lista completa para seguridad, y el índice para velocidad
    private var allCameras: List<LatLng> = emptyList()
    private var allPaths: List<LatLng> = emptyList()
    private var indexedCameras: Map<String, List<LatLng>> = emptyMap()
    private var indexedPaths: Map<String, List<LatLng>> = emptyMap()
    private var riskData: Map<String, RiskEntry> = emptyMap()

    private const val WEIGHT_CRIME = 1.2
    private const val WEIGHT_TRAFFIC = 0.5
    private const val BONUS_CAMERA = 15.0
    private const val BONUS_PATH = 25.0

    fun initialize(context: Context) {
        // Solo inicializar si no se ha hecho o si falló la carga de infraestructura
        if (riskData.isNotEmpty() && indexedCameras.isNotEmpty()) return 

        try {
            // 1. Cargar incidencia criminal H3
            val jsonRisk = context.assets.open("incidencia_h3.json").bufferedReader().use { it.readText() }
            riskData = Gson().fromJson(jsonRisk, object : TypeToken<Map<String, RiskEntry>>() {}.type)
            
            // 2. Cargar Cámaras C5
            val jsonC5 = JSONObject(context.assets.open("postes_c5.json").bufferedReader().use { it.readText() })
            val c5Array = jsonC5.getJSONArray("Poste C5")
            val cameraList = mutableListOf<LatLng>()
            val tempCameras = mutableMapOf<String, MutableList<LatLng>>()
            
            for (i in 0 until c5Array.length()) {
                val p = c5Array.getJSONObject(i)
                val pos = LatLng(p.getDouble("latitud"), p.getDouble("longitud"))
                cameraList.add(pos)
                getHexAddress(pos)?.let { hex ->
                    tempCameras.getOrPut(hex) { mutableListOf() }.add(pos)
                }
            }
            allCameras = cameraList
            indexedCameras = tempCameras

            // 3. Cargar Senderos
            val jsonPaths = JSONObject(context.assets.open("mi_calle.json").bufferedReader().use { it.readText() })
            val pathsArray = jsonPaths.getJSONArray("data")
            val pathList = mutableListOf<LatLng>()
            val tempPaths = mutableMapOf<String, MutableList<LatLng>>()
            
            for (i in 0 until pathsArray.length()) {
                val s = pathsArray.getJSONObject(i)
                if (s.optInt("senderopro") > 0 || s.optInt("senderoisn") > 0) {
                    val coords = s.getString("geo_point_2d").split(",")
                    val pos = LatLng(coords[0].toDouble(), coords[1].toDouble())
                    pathList.add(pos)
                    getHexAddress(pos)?.let { hex ->
                        tempPaths.getOrPut(hex) { mutableListOf() }.add(pos)
                    }
                }
            }
            allPaths = pathList
            indexedPaths = tempPaths

            Log.d("RiskManager", "ISM Inicializado: ${riskData.size} hex, ${allCameras.size} cámaras, ${allPaths.size} senderos")
        } catch (e: Exception) {
            Log.e("RiskManager", "Error en inicialización: ${e.message}")
        }
    }

    fun getHexAddress(location: LatLng): String? {
        return try {
            h3?.latLngToCellAddress(location.latitude, location.longitude, HEX_RESOLUTION)
        } catch (e: Exception) {
            null
        }
    }

    fun getTimeRiskFactor(): Double {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 0..5 -> 3.0
            in 22..23 -> 2.2
            in 19..21 -> 1.7
            in 6..7 -> 1.3
            else -> 1.0
        }
    }

    data class SafetyAnalysis(
        val score: Double,
        val confidence: Int,
        val cameraCount: Int,
        val pathCount: Int,
        val riskLevel: String,
        val label: String
    )

    fun getCamerasOnPath(points: List<LatLng>): List<LatLng> {
        if (points.isEmpty()) return emptyList()
        
        val uniqueHexagons = mutableSetOf<String>()
        points.forEach { pt -> getHexAddress(pt)?.let { uniqueHexagons.add(it) } }
        
        val result = mutableListOf<LatLng>()
        
        // Usar exclusivamente los datos indexados con vecinos para velocidad y ahorro de memoria
        uniqueHexagons.forEach { hex ->
            val neighbors = try { h3?.gridDisk(hex, 1) ?: listOf(hex) } catch (e: Exception) { listOf(hex) }
            neighbors.forEach { n ->
                indexedCameras[n]?.forEach { if (PolyUtil.isLocationOnPath(it, points, true, 70.0)) result.add(it) }
            }
        }

        // Limitar a las 40 cámaras más relevantes para no colapsar el mapa
        return result.distinct().take(40)
    }

    fun getPathsOnPath(points: List<LatLng>): List<LatLng> {
        if (points.isEmpty()) return emptyList()
        val uniqueHexagons = mutableSetOf<String>()
        points.forEach { pt -> getHexAddress(pt)?.let { uniqueHexagons.add(it) } }
        val result = mutableListOf<LatLng>()
        
        uniqueHexagons.forEach { hex ->
            val neighbors = try { h3?.gridDisk(hex, 1) ?: listOf(hex) } catch (e: Exception) { listOf(hex) }
            neighbors.forEach { n ->
                indexedPaths[n]?.forEach { if (PolyUtil.isLocationOnPath(it, points, true, 100.0)) result.add(it) }
            }
        }
        return result.distinct()
    }

    fun analyzeRoute(points: List<LatLng>): SafetyAnalysis {
        val camerasOnPath = getCamerasOnPath(points)
        val pathsOnPath = getPathsOnPath(points)
        
        val uniqueHexagons = mutableSetOf<String>()
        points.forEach { pt -> getHexAddress(pt)?.let { uniqueHexagons.add(it) } }

        val score = evaluateRiskFast(uniqueHexagons, camerasOnPath.size, pathsOnPath.size)
        
        val riskLevel = when {
            score > 60 -> "Inseguro"
            score > 30 -> "Precaución"
            else -> "Seguro"
        }

        val label = when {
            pathsOnPath.size > 2 -> "Ruta por Senderos Iluminados"
            camerasOnPath.size > 5 -> "Alta Cobertura de Cámaras"
            score < 15 -> "Trayecto de Máxima Confianza"
            else -> "Monitoreo de Riesgo Activo"
        }

        return SafetyAnalysis(score, 0, camerasOnPath.size, pathsOnPath.size, riskLevel, label)
    }

    private fun evaluateRiskFast(hexagons: Set<String>, cameraCount: Int, pathCount: Int): Double {
        var baseRisk = 0.0
        hexagons.forEach { hex ->
            val entry = riskData[hex] ?: RiskEntry()
            baseRisk += (entry.riesgo_delito * WEIGHT_CRIME) + (entry.riesgo_vial * WEIGHT_TRAFFIC)
        }
        val avgBaseRisk = if (hexagons.isNotEmpty()) baseRisk / hexagons.size else 0.0
        val infrastructureBonus = (cameraCount * BONUS_CAMERA) + (pathCount * BONUS_PATH)
        return ((avgBaseRisk * getTimeRiskFactor()) - infrastructureBonus).coerceAtLeast(0.0)
    }

    fun evaluateRouteRisk(points: List<LatLng>): Double {
        return analyzeRoute(points).score
    }
}
