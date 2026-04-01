package com.icm.igosafeapp

import Contactos
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.icm.igosafeapp.databinding.ActivityMenuBinding
import org.json.JSONObject
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.squareup.picasso.Picasso

class Menu : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarActivityMenu.toolbar)


        binding.appBarActivityMenu.logoBar.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .setAnchorView(R.id.logoBar).show()
        }
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_activity_menu)
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_viaje, R.id.nav_contactos, R.id.nav_favoritos
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // Cambiar color a itemPremium
        val navigationView: NavigationView = findViewById(R.id.nav_view)
        cambiarColorItem(navigationView, R.id.nav_premium, R.color.verde)

        navController.navigate(R.id.nav_viaje)
        // Configura el listener para el NavigationView
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_contactos -> {
                    // Navega a nav_contactos con el bundle
                    navController.navigate(R.id.nav_contactos)
                    true // Indica que el evento fue manejado
                }

                R.id.nav_viaje -> {
                    // Navega a nav_viaje
                    navController.navigate(R.id.nav_viaje)
                    true // Indica que el evento fue manejado
                }

                R.id.nav_favoritos -> {
                    // Navegar a fragmento de favoritos
                    navController.navigate(R.id.nav_favoritos) // Navegar al fragmento de favoritos
                    true
                }
                R.id.cerrar_sesion ->{
                    navController.CerrarSesion(this)
                    true
                }

                else -> false // Para otros ítems de menú
            }
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId != null) {
            val databaseReference: DatabaseReference =
                FirebaseDatabase.getInstance().getReference("usuarios").child(userId)

            databaseReference.child("fotoPerfilUrl").get().addOnSuccessListener { snapshot ->
                val photoUrl = snapshot.getValue(String::class.java)

                if (photoUrl != null && photoUrl.isNotEmpty()) {
                    Picasso.get()
                        .load(photoUrl)
                        .into(binding.appBarActivityMenu.fotoPerfil)
                } else {
                    // Si la URL es nula o vacía, usa una imagen predeterminada
                    binding.appBarActivityMenu.fotoPerfil.setImageResource(R.drawable.photo_original_user)
                }
            }
        }
        binding.appBarActivityMenu.fotoPerfil.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }
    }

    fun NavController.CerrarSesion(context: Context) {
        FirebaseAuth.getInstance().signOut()
        Toast.makeText(context, "Sesión cerrada correctamente", Toast.LENGTH_SHORT).show()
        val intent = Intent(context, LoginActivity::class.java) // Cambiado 'this' por 'context'
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent) // Cambiado a context.startActivity
    }

    private fun cambiarColorItem(navigationView: NavigationView, itemId: Int, colorId: Int) {
        val item: MenuItem = navigationView.menu.findItem(itemId)
        item.iconTintList = ColorStateList.valueOf(ContextCompat.getColor(this, colorId))
        val s = SpannableString(item.title)
        s.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, colorId)), 0, s.length, 0)
        item.title = s
    }

    fun androidx.navigation.NavController.cerrarSesionApp(context: android.content.Context) {
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
        android.widget.Toast.makeText(context, "Sesión cerrada correctamente", android.widget.Toast.LENGTH_SHORT).show()
        val intent = android.content.Intent(context, LoginActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
    }
}