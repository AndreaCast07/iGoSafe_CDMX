package com.icm.igosafeapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.icm.igosafeapp.databinding.ActivityProfileBinding
import com.squareup.picasso.Picasso

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userId = auth.currentUser?.uid
        if (userId != null) {
            cargarDatosUsuario(userId)
        }

        setupButtons()
    }

    private fun cargarDatosUsuario(uid: String) {
        val userRef = database.getReference("usuarios").child(uid)

        userRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val nombre = snapshot.child("nombre").value.toString()
                val email = snapshot.child("email").value.toString()
                val celular = snapshot.child("celular").value.toString()
                val edad = snapshot.child("edad").value.toString()
                val genero = snapshot.child("genero").value.toString()
                val nacionalidad = snapshot.child("nacionalidad").value.toString()
                val fotoUrl = snapshot.child("fotoPerfilUrl").value.toString()

                binding.tvUserName.text = nombre
                binding.tvUserEmail.text = if (email != "null") email else "Sin correo registrado"
                binding.tvUserPhone.text = celular
                binding.tvProfileAge.text = "Edad: $edad"
                binding.tvProfileGender.text = "Género: $genero"
                binding.tvProfileNationality.text = "Nacionalidad: $nacionalidad"

                if (fotoUrl.isNotEmpty() && fotoUrl != "null") {
                    Picasso.get()
                        .load(fotoUrl)
                        .transform(CircleTransform(120))
                        .placeholder(R.drawable.photo_original_user)
                        .into(binding.imgProfileDetail)
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error al cargar la información", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupButtons() {
        // Botón Cerrar Sesión Estándar
        binding.btnCerrarSesion.setOnClickListener {
            auth.signOut()
            irAMain()
        }

        // Lógica de Cambio de Contraseña solicitada
        binding.btnChangePassword.setOnClickListener {
            val email = auth.currentUser?.email
            if (email != null) {
                auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Correo enviado. Por seguridad, la sesión se cerrará.", Toast.LENGTH_LONG).show()

                        // 1. Cerramos la sesión de Firebase
                        auth.signOut()

                        // 2. Mandamos al usuario a la pantalla principal
                        irAMain()
                    } else {
                        Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "No hay un correo asociado", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDeleteAccount.setOnClickListener {
            mostrarDialogoEliminacion()
        }
    }

    // Función auxiliar para no repetir código de navegación
    private fun irAMain() {
        val intent = Intent(this, MainActivity::class.java)
        // Limpiamos el stack de actividades para que no pueda volver atrás
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun mostrarDialogoEliminacion() {
        val user = auth.currentUser
        val uid = user?.uid ?: return

        AlertDialog.Builder(this)
            .setTitle("Eliminar Cuenta")
            .setMessage("¿Estás seguro? Esta acción eliminará permanentemente tu perfil y datos de iGoSafe.")
            .setPositiveButton("Eliminar") { _, _ ->
                database.getReference("usuarios").child(uid).removeValue().addOnCompleteListener {
                    user.delete().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Cuenta eliminada con éxito", Toast.LENGTH_SHORT).show()
                            irAMain()
                        } else {
                            Toast.makeText(this, "Vuelve a iniciar sesión para eliminar la cuenta.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}