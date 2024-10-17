package com.icm.igosafeapp.manejoArchivos

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import entidades.Usuario
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class UsuarioManager(private val context: Context) {
    private val file = "usuarios.json"

    init {
        // Cargar usuarios desde assets solo si no hay usuarios guardados en la memoria interna
        if (!hayUsuariosEnMemoriaInterna()) {
            cargarUsuariosDesdeAssets()
        }
    }

    // Registrarse
    fun guardarUsuario(usuario: Usuario) {
        val usuariosList: MutableList<Usuario> =
            cargarUsuariosDesdeMemoriaInterna()?.toMutableList() ?: mutableListOf()

        // Agregar el nuevo usuario a la lista
        usuariosList.add(usuario)
        Log.d("UsuarioManager", "Usuarios guardados antes de guardar: ${usuariosList.size}")

        // Guardar la lista actualizada en memoria interna
        guardarUsuariosEnMemoriaInterna(usuariosList)
    }

    private fun hayUsuariosEnMemoriaInterna(): Boolean {
        // Cargar usuarios de memoria interna y verificar si hay alguno
        return cargarUsuariosDesdeMemoriaInterna()?.isNotEmpty() == true
    }

    private fun cargarUsuariosDesdeAssets() {
        try {
            val inputStream = context.assets.open(file)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonContent = reader.use { it.readText() }

            // Debugging: imprime el contenido JSON
            Log.d("UsuarioManager", "Contenido JSON: $jsonContent")

            val usuarios = Gson().fromJson(jsonContent, Array<Usuario>::class.java)?.toList()

            // Guardar usuarios en memoria interna
            if (usuarios != null && usuarios.isNotEmpty()) {
                guardarUsuariosEnMemoriaInterna(usuarios)
            }
        } catch (e: Exception) {
            Log.e("UsuarioManager", "Error al cargar usuarios desde assets: ${e.message}", e)
        }
    }


    private fun cargarUsuariosDesdeMemoriaInterna(): List<Usuario>? {
        return try {
            val inputStream = context.openFileInput(file)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonContent = reader.use { it.readText() }
            val usuarios = Gson().fromJson(jsonContent, Array<Usuario>::class.java)?.toList()
            Log.d("UsuarioManager", "Usuarios cargados desde memoria interna: ${usuarios?.size}")
            usuarios
        } catch (e: Exception) {
            Log.e("UsuarioManager", "Error al cargar usuarios desde memoria interna: ${e.message}")
            null
        }
    }

    private fun guardarUsuariosEnMemoriaInterna(usuariosList: List<Usuario>) {
        val json = Gson().toJson(usuariosList)

        try {
            context.openFileOutput(file, Context.MODE_PRIVATE).use { output ->
                OutputStreamWriter(output).use { writer ->
                    writer.write(json)
                }
            }
            Log.d("UsuarioManager", "Usuarios guardados en memoria interna correctamente.")
        } catch (e: IOException) {
            Log.e("UsuarioManager", "Error al guardar usuarios en memoria interna: ${e.message}")
        }
    }

    // Iniciar sesión - validar credenciales
    fun verificarUsuario(celular: String, contrasena: String): Boolean {
        val usuarios = cargarUsuariosDesdeMemoriaInterna() ?: return false
        return usuarios.any { it.celular == celular && it.contraseña == contrasena }
    }

    // Validar un celular existente o no
    fun celularRegistrado(celular: String): Boolean {
        val usuarios = cargarUsuariosDesdeMemoriaInterna() ?: return false
        return usuarios.any { it.celular == celular }
    }
}