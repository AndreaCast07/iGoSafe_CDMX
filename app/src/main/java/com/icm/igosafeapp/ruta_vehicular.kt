package com.icm.igosafeapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
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

class ruta_vehicular : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var iniciar: Button
    private lateinit var nombre: TextView
    private lateinit var apodo: TextView

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var userMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var hasAnimatedOnce = false

    private lateinit var auth: FirebaseAuth
    private var userLocationRef: DatabaseReference? = null
    private var locationListener: ValueEventListener? = null
    
    private val transitionHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ruta_vehicular)

        iniciar = findViewById(R.id.iniciarViajev)
        nombre = findViewById(R.id.textViewNombrev)
        apodo = findViewById(R.id.Contactov)

        nombre.text = intent.getStringExtra("name") ?: "Nombre no disponible"
        apodo.text = intent.getStringExtra("nickname") ?: "Apodo no disponible"

        auth = FirebaseAuth.getInstance()
        Picasso.get().setIndicatorsEnabled(false)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        iniciar.setOnClickListener {
            val userPos = userMarker?.position
            val destPos = destinationMarker?.position

            if (userPos != null && destPos != null) {
                val intent = Intent(this, recorrido_vehicular::class.java).apply {
                    putExtra("startLat", userPos.latitude)
                    putExtra("startLong", userPos.longitude)
                    putExtra("endLat", destPos.latitude)
                    putExtra("endLong", destPos.longitude)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Esperando ubicación...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.standard))
        obtenerFotoPerfil()
    }

    private fun obtenerFotoPerfil() {
        val userUid = auth.currentUser?.uid ?: return
        val userRef = FirebaseDatabase.getInstance().getReference("usuarios").child(userUid)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isFinishing) return
                val photoUrl = snapshot.child("fotoPerfilUrl").getValue(String::class.java) ?: ""
                consultarUbicacionUsuario(photoUrl)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun consultarUbicacionUsuario(photoUrl: String) {
        val userUid = auth.currentUser?.uid ?: return
        userLocationRef = FirebaseDatabase.getInstance().getReference("users").child(userUid).child("location")

        locationListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isFinishing) return
                val lat = snapshot.child("latitude").getValue(Double::class.java)
                val lng = snapshot.child("longitude").getValue(Double::class.java)

                if (lat != null && lng != null) {
                    val userLocation = LatLng(lat, lng)
                    updateUserMarker(userLocation, photoUrl)

                    val destLat = intent.getDoubleExtra("endLat", 0.0)
                    val destLng = intent.getDoubleExtra("endLong", 0.0)
                    
                    if (destLat != 0.0 && destLng != 0.0) {
                        val destLoc = LatLng(destLat, destLng)
                        createDestinationMarker(destLoc)
                        if (!hasAnimatedOnce) {
                            realizarTransicionZoom(userLocation, destLoc)
                            hasAnimatedOnce = true
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        userLocationRef?.addValueEventListener(locationListener!!)
    }

    private fun updateUserMarker(location: LatLng, photoUrl: String) {
        if (photoUrl.isEmpty()) {
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                userMarker?.remove()
                userMarker = mMap.addMarker(MarkerOptions().position(location).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)))
            }
            return
        }

        Thread {
            try {
                val bitmap = Picasso.get()
                    .load(photoUrl)
                    .placeholder(R.drawable.photo_original_user)
                    .error(R.drawable.ic_person)
                    .transform(CircleTransform(90, Color.BLUE, 8f))
                    .get()
                runOnUiThread {
                    if (!isFinishing) {
                        userMarker?.remove()
                        userMarker = mMap.addMarker(MarkerOptions().position(location).icon(BitmapDescriptorFactory.fromBitmap(bitmap)))
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (!isFinishing) {
                        userMarker?.remove()
                        userMarker = mMap.addMarker(MarkerOptions().position(location).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)))
                    }
                }
            }
        }.start()
    }

    private fun createDestinationMarker(location: LatLng) {
        if (destinationMarker != null) return // Ya existe
        val photo = intent.getStringExtra("photo") ?: ""
        Thread {
            try {
                val bitmap = if (photo.isNotEmpty()) {
                    Picasso.get().load(photo).placeholder(R.drawable.photo_original_user).error(R.drawable.ic_person).transform(CircleTransform(90, Color.RED, 8f)).get()
                } else null
                
                runOnUiThread {
                    if (!isFinishing && destinationMarker == null) {
                        val options = MarkerOptions().position(location)
                        if (bitmap != null) options.icon(BitmapDescriptorFactory.fromBitmap(bitmap))
                        else options.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                        destinationMarker = mMap.addMarker(options)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (!isFinishing && destinationMarker == null) {
                        destinationMarker = mMap.addMarker(MarkerOptions().position(location).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))
                    }
                }
            }
        }.start()
    }

    private fun realizarTransicionZoom(userLocation: LatLng, destinationLocation: LatLng) {
        transitionHandler.removeCallbacksAndMessages(null)
        transitionHandler.postDelayed({ 
            if (!isFinishing) mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 18f)) 
        }, 0)
        transitionHandler.postDelayed({ 
            if (!isFinishing) mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(destinationLocation, 18f)) 
        }, 3000)
        transitionHandler.postDelayed({
            if (!isFinishing) {
                val bounds = LatLngBounds.Builder().include(userLocation).include(destinationLocation).build()
                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 140))
            }
        }, 6000)
    }

    override fun onDestroy() {
        super.onDestroy()
        transitionHandler.removeCallbacksAndMessages(null)
        locationListener?.let { userLocationRef?.removeEventListener(it) }
    }
}
