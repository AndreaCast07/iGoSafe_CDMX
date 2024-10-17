package entidades

data class DatosUsuario(
    val nombre: String,
    val tipoDocumento: String,
    val documento: String
)

data class Usuario(
    val celular: String,
    val contraseña: String,
    val datosUsuario: DatosUsuario
)