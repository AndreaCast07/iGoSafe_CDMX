package com.icm.igosafeapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.squareup.picasso.Picasso

class ProfileActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    private var userCelularFiltro: String? = null // Para poder borrar el registro del celular

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val userId = auth.currentUser?.uid
        if (userId != null) {
            cargarDatosUsuario(userId)
        } else {
            irAMain()
        }

        setupButtons()
    }

    private fun cargarDatosUsuario(uid: String) {
        database.child("usuarios").child(uid).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                // Guardamos el celular para el borrado posterior
                userCelularFiltro = snapshot.child("celularFiltro").value?.toString()

                findViewById<TextView>(R.id.tvUserName).text = snapshot.child("nombre").value?.toString() ?: "Usuario"
                findViewById<TextView>(R.id.tvUserEmail).text = snapshot.child("email").value?.toString() ?: "Sin correo"
                findViewById<TextView>(R.id.tvUserPhone).text = snapshot.child("celular").value?.toString() ?: "Sin teléfono"
                findViewById<TextView>(R.id.tvProfileAge).text = "Edad: ${snapshot.child("edad").value}"
                findViewById<TextView>(R.id.tvProfileGender).text = "Género: ${snapshot.child("genero").value}"
                findViewById<TextView>(R.id.tvProfileNationality).text = "Nacionalidad: ${snapshot.child("nacionalidad").value}"

                val fotoUrl = snapshot.child("fotoPerfilUrl").value?.toString() ?: ""
                if (fotoUrl.isNotEmpty() && fotoUrl != "null") {
                    Picasso.get().load(fotoUrl).transform(CircleTransform(120, Color.WHITE, 0f))
                        .placeholder(R.drawable.photo_original_user)
                        .into(findViewById<ImageView>(R.id.imgProfileDetail))
                }
            } else {
                Log.e("iGoSafe_Debug", "UID no encontrado en /usuarios/$uid")
                Toast.makeText(this, "Perfil incompleto. Favor de completar registro.", Toast.LENGTH_LONG).show()
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Error de red: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnCerrarSesion).setOnClickListener {
            auth.signOut()
            irAMain()
        }

        findViewById<Button>(R.id.btnChangePassword).setOnClickListener {
            auth.currentUser?.email?.let { email ->
                auth.sendPasswordResetEmail(email).addOnSuccessListener {
                    Toast.makeText(this, "Correo de recuperación enviado", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<Button>(R.id.btnDeleteAccount).setOnClickListener {
            mostrarDialogoEliminacion()
        }
    }

    private fun mostrarDialogoEliminacion() {
        val user = auth.currentUser
        val uid = user?.uid ?: return

        AlertDialog.Builder(this)
            .setTitle("Eliminar Cuenta")
            .setMessage("¿Estás seguro? Se borrarán tus datos y el número quedará libre.")
            .setPositiveButton("Eliminar") { _, _ ->
                // 1. Borramos de 'usuarios'
                database.child("usuarios").child(uid).removeValue().addOnSuccessListener {

                    // 2. Borramos de 'celulares_registrados' para que el número se pueda volver a usar
                    userCelularFiltro?.let {
                        database.child("celulares_registrados").child(it).removeValue()
                    }

                    // 3. Borramos del sistema de Autenticación
                    user.delete().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Cuenta eliminada correctamente", Toast.LENGTH_SHORT).show()
                            irAMain()
                        } else {
                            // Si falla aquí, es por seguridad de Firebase (requiere login reciente)
                            Log.e("iGoSafe_Error", "Fallo al borrar auth: ${task.exception?.message}")
                            Toast.makeText(this, "Por seguridad, cierra sesión e inicia de nuevo antes de borrar.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun irAMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}