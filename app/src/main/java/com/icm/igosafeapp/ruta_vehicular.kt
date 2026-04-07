package com.icm.igosafeapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.squareup.picasso.Picasso

class ruta_vehicular : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var iniciar: Button
    private lateinit var nombre: TextView
    private lateinit var apodo: TextView

    private lateinit var mMap: GoogleMap
    private var userMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var hasAnimatedOnce = false
    private val transitionHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ruta_vehicular)

        iniciar = findViewById(R.id.iniciarViajev)
        nombre = findViewById(R.id.textViewNombrev)
        apodo = findViewById(R.id.Contactov)

        val destinationName = intent.getStringExtra("name") ?: "Ubicación destino"
        apodo.text = destinationName // Nombre en azul grande
        nombre.text = intent.getStringExtra("address") ?: destinationName // Dirección en gris abajo

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        iniciar.setOnClickListener {
            val sLat = intent.getDoubleExtra("startLat", 0.0)
            val sLng = intent.getDoubleExtra("startLong", 0.0)
            val eLat = intent.getDoubleExtra("endLat", 0.0)
            val eLng = intent.getDoubleExtra("endLong", 0.0)

            if (sLat != 0.0 && eLat != 0.0) {
                val intent = Intent(this, recorrido_vehicular::class.java).apply {
                    putExtra("startLat", sLat)
                    putExtra("startLong", sLng)
                    putExtra("endLat", eLat)
                    putExtra("endLong", eLng)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Cargando coordenadas...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.standard))

        val sLat = intent.getDoubleExtra("startLat", 0.0)
        val sLng = intent.getDoubleExtra("startLong", 0.0)
        val eLat = intent.getDoubleExtra("endLat", 0.0)
        val eLng = intent.getDoubleExtra("endLong", 0.0)

        if (sLat != 0.0 && eLat != 0.0) {
            val startLoc = LatLng(sLat, sLng)
            val endLoc = LatLng(eLat, eLng)

            userMarker = mMap.addMarker(MarkerOptions().position(startLoc).title("Mi ubicación").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)))
            destinationMarker = mMap.addMarker(MarkerOptions().position(endLoc).title(nombre.text.toString()).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))

            realizarTransicionZoom(startLoc, endLoc)
            obtenerFotoPerfil()
        }
    }

    private fun realizarTransicionZoom(userLoc: LatLng, destLoc: LatLng) {
        if (hasAnimatedOnce) return
        hasAnimatedOnce = true
        
        transitionHandler.postDelayed({ mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLoc, 17f)) }, 500)
        transitionHandler.postDelayed({ mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(destLoc, 17f)) }, 3000)
        transitionHandler.postDelayed({
            val bounds = LatLngBounds.Builder().include(userLoc).include(destLoc).build()
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
        }, 5500)
    }

    private fun obtenerFotoPerfil() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().getReference("usuarios").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val url = snapshot.child("fotoPerfilUrl").getValue(String::class.java) ?: ""
                    if (url.isNotEmpty()) {
                        Thread {
                            try {
                                val bitmap = Picasso.get().load(url).transform(CircleTransform(90, Color.BLUE, 8f)).get()
                                runOnUiThread { userMarker?.setIcon(BitmapDescriptorFactory.fromBitmap(bitmap)) }
                            } catch (e: Exception) {}
                        }.start()
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
}
