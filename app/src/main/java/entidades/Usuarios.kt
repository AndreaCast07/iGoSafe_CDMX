package entidades

data class Usuarios (
    val name: String = "",
    val celular: String = "",
    val tipoDocumento: String = "",
    val numDocumento: String = "",
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    val status: String = "",
    val imageUrl: String = ""
)