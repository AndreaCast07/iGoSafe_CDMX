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
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.squareup.picasso.Picasso

class ruta_peatonal : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var iniciar: Button
    private lateinit var nombre: TextView
    private lateinit var apodo: TextView

    private lateinit var mMap: GoogleMap
    private var userMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var hasAnimatedOnce = false

    private lateinit var auth: FirebaseAuth
    private var userLocationRef: DatabaseReference? = null
    private var locationListener: ValueEventListener? = null
    
    private val transitionHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ruta_peatonal)

        iniciar = findViewById(R.id.iniciarViaje)
        nombre = findViewById(R.id.textViewNombre)
        apodo = findViewById(R.id.Contacto)

        val destinationName = intent.getStringExtra("name") ?: "Ubicación destino"
        apodo.text = destinationName // Nombre en azul grande
        nombre.text = intent.getStringExtra("address") ?: destinationName // Dirección en gris abajo

        auth = FirebaseAuth.getInstance()
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        iniciar.setOnClickListener {
            val sLat = intent.getDoubleExtra("startLat", 0.0)
            val sLng = intent.getDoubleExtra("startLong", 0.0)
            val eLat = intent.getDoubleExtra("endLat", 0.0)
            val eLng = intent.getDoubleExtra("endLong", 0.0)

            if (sLat != 0.0 && eLat != 0.0) {
                val intent = Intent(this, recorrido_peatonal::class.java).apply {
                    putExtra("startLat", sLat)
                    putExtra("startLong", sLng)
                    putExtra("endLat", eLat)
                    putExtra("endLong", eLng)
                }
                startActivity(intent)
                finish() // Finish this activity when moving to the next one
            } else {
                Toast.makeText(this, "Ubicaciones no válidas", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        transitionHandler.removeCallbacksAndMessages(null)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.standard))

        val sLat = intent.getDoubleExtra("startLat", 0.0)
        val sLng = intent.getDoubleExtra("startLong", 0.0)
        val eLat = intent.getDoubleExtra("endLat", 0.0)
        val eLng = intent.getDoubleExtra("endLong", 0.0)

        if (sLat != 0.0 && eLat != 0.0) {
            val startLoc = LatLng(sLat, sLng)
            val endLoc = LatLng(eLat, eLng)

            // Colocar marcadores de inmediato usando los datos del Intent
            userMarker = mMap.addMarker(MarkerOptions().position(startLoc).title("Mi ubicación").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)))
            destinationMarker = mMap.addMarker(MarkerOptions().position(endLoc).title(nombre.text.toString()).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))

            // Realizar zoom inmediato
            realizarTransicionZoom(startLoc, endLoc)
            
            // Intentar actualizar la foto del usuario en segundo plano (Opcional)
            obtenerFotoPerfil(startLoc)
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

    private fun obtenerFotoPerfil(currentLoc: LatLng) {
        val userUid = auth.currentUser?.uid ?: return
        FirebaseDatabase.getInstance().getReference("usuarios").child(userUid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val url = snapshot.child("fotoPerfilUrl").getValue(String::class.java) ?: ""
                    if (url.isNotEmpty()) {
                        Thread {
                            try {
                                val bitmap = Picasso.get().load(url).transform(CircleTransform(90, Color.BLUE, 8f)).get()
                                runOnUiThread {
                                    if (!isFinishing && !isDestroyed) {
                                        userMarker?.setIcon(BitmapDescriptorFactory.fromBitmap(bitmap))
                                    }
                                }
                            } catch (e: Exception) {}
                        }.start()
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
}
