package entidades

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