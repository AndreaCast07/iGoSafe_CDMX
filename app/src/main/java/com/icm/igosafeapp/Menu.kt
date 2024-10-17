package com.icm.igosafeapp

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
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
import androidx.core.content.ContextCompat
import com.icm.igosafeapp.databinding.ActivityMenuBinding

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

        // Obtener los contactos del Intent
        val contactsJson = intent.getStringExtra("contacts")
        val bundle = if (contactsJson != null) {
            Bundle().apply {
                putString("contacts", contactsJson)
            }
        } else {
            null
        }
        navController.navigate(R.id.nav_viaje, bundle)
        // Configura el listener para el NavigationView
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_contactos -> {
                    // Navega a nav_contactos con el bundle
                    navController.navigate(R.id.nav_contactos, bundle)
                    true // Indica que el evento fue manejado
                }
                R.id.nav_viaje -> {
                    // Navega a nav_viaje
                    navController.navigate(R.id.nav_viaje, bundle)
                    true // Indica que el evento fue manejado
                }
                else -> false // Para otros ítems de menú
            }
        }


    }

    private fun cambiarColorItem(navigationView: NavigationView, itemId: Int, colorId: Int) {
        val item: MenuItem = navigationView.menu.findItem(itemId)
        item.iconTintList = ColorStateList.valueOf(ContextCompat.getColor(this, colorId))
        val s = SpannableString(item.title)
        s.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, colorId)), 0, s.length, 0)
        item.title = s
    }

    override fun onBackPressed() {
        super.onBackPressed()
        val intent = Intent(this, LoginActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }
}