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
        binding.btnCerrarSesion.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        binding.btnChangePassword.setOnClickListener {
            val email = auth.currentUser?.email
            if (email != null) {
                auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Se ha enviado un correo para restablecer tu contraseña", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "No hay un correo asociado para recuperar la contraseña", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDeleteAccount.setOnClickListener {
            mostrarDialogoEliminacion()
        }
    }

    private fun mostrarDialogoEliminacion() {
        val user = auth.currentUser
        val uid = user?.uid ?: return

        AlertDialog.Builder(this)
            .setTitle("Eliminar Cuenta")
            .setMessage("¿Estás seguro? Esta acción eliminará permanentemente tu perfil y datos de iGoSafe.")
            .setPositiveButton("Eliminar") { _, _ ->
                // 1. Eliminar datos de Realtime Database
                database.getReference("usuarios").child(uid).removeValue().addOnCompleteListener {
                    // 2. Eliminar de Firebase Authentication
                    user.delete().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Cuenta eliminada con éxito", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this, LoginActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this, "Error de seguridad. Por favor, vuelve a iniciar sesión para eliminar la cuenta.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}