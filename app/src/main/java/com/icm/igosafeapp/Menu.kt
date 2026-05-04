package com.icm.igosafeapp

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth

class Menu : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        auth = FirebaseAuth.getInstance()

        // 1. Configuración de la Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // CORRECCIÓN: Esto quita el "iGoSafeApp" extra de la izquierda
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // 2. Clic en el círculo de perfil para ir a ProfileActivity
        val fotoPerfil: ImageView = findViewById(R.id.fotoPerfil)
        fotoPerfil.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }

        // 3. Configuración del Sidebar (Drawer)
        drawerLayout = findViewById(R.id.drawer_layout)
        val navigationView: NavigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)

        // Botón de "hamburguesa" sincronizado
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // 4. Abrir el menú lateral automáticamente al iniciar
        if (savedInstanceState == null) {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    // Maneja los clics en las opciones del menú lateral (IDs del XML real)
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_viaje -> {
                // Ya estamos en la pantalla principal
            }
            R.id.nav_favoritos -> {
                Toast.makeText(this, "Favoritos próximamente", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_contactos -> {
                Toast.makeText(this, "Contactos de emergencia", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_premium -> {
                Toast.makeText(this, "iGoSafe Premium", Toast.LENGTH_SHORT).show()
            }
            R.id.cerrar_sesion -> {
                cerrarSesion()
            }
        }

        // Cerramos el menú después de elegir
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun cerrarSesion() {
        auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // Cerrar el menú si está abierto al presionar "atrás"
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}