package entidades

data class DatosUsuario(
    val nombre: String = "",
    val genero: String = "",
    val edad: Int = 0,
    val nacionalidad: String = "",
    val email : String ="",
    val fotoPerfilUrl: String? = null

)

data class Usuario(
    val celular: String = "",
    val contraseña: String = "",
    val datosUsuario: DatosUsuario = DatosUsuario(),
    val status: String = "",
    val fotoPerfilUrl: String = "",
    val deviceId: String = ""
)