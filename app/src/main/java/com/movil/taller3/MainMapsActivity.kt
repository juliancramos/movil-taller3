package com.movil.taller3

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.movil.taller3.databinding.ActivityMainMapsBinding
import org.json.JSONObject


class MainMapsActivity : AppCompatActivity(), OnMapReadyCallback {
    //Mapa
    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMainMapsBinding

    //Localización
    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var marcadorUbicacionActual: Marker? = null
    private var primeraUbicacion: Boolean = true

    private var available: Boolean? = false

    private val locationSettings = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
        ActivityResultCallback {
            if (it.resultCode == RESULT_OK) {
                startLocationUpdates()
            } else {
                Toast.makeText(this, "GPS no activado", Toast.LENGTH_SHORT).show()
            }
        }
    )

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ActivityResultCallback {
            if (it) {
                locationSettings()
            } else {
                Toast.makeText(this, "No hay permiso para acceder al GPS", Toast.LENGTH_SHORT).show()
            }
        }
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = createLocationRequest()
        locationCallback = createLocationCallback()
        //Obtener valor actual del usuario
        obtenerDisponibilidad()
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Pedir permiso para usar el gps
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_DENIED
        ) {
            if (shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(
                    this,
                    "El permiso se necesita para obtener su ubicación en tiempo real",
                    Toast.LENGTH_LONG
                ).show()
            }
            locationPermission.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }else
            locationSettings()
        //Cerrar sesión
        binding.signOut.setOnClickListener {
            val auth = FirebaseAuth.getInstance()
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        //cambiar disponibilidad
        binding.availableButton.setOnClickListener {
            available = !(available ?: false)
            cambiarDisponibilidad()
            actualizarDisponibilidad()
        }
        //actividad lista de usuarios
        binding.usuariosButton.setOnClickListener {

        }
    }
    private fun locationSettings() {
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
        task.addOnSuccessListener { locationSettingsResponse ->
            // All location settings are satisfied. The client can initialize
            // location requests here. // ...
            startLocationUpdates()
        }
        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    val isr: IntentSenderRequest =
                        IntentSenderRequest.Builder(exception.resolution).build()
                    locationSettings.launch(isr)
                } catch (sendEx: IntentSender.SendIntentException) {
                    Toast.makeText(baseContext, "There is no GPS hardware", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }
    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onResume() {
        super.onResume()
    }
    private fun stopLocationUpdates() {
        //detener el uso de la ubicación
        locationClient.removeLocationUpdates(locationCallback)
    }
    private fun createLocationRequest(): LocationRequest {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setWaitForAccurateLocation(true)
            .setMinUpdateIntervalMillis(5000)
            .build()
        return request
    }

    private fun createLocationCallback(): LocationCallback {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                val loc = result.lastLocation
                if (loc != null) {
                    updateUI(loc)
                }
            }
        }
        return callback
    }
    private fun updateUI(location: Location) {
        Log.e("UBI", "longitud: ${location.longitude}, latitud: ${location.latitude}")
        val latLng = LatLng(location.latitude, location.longitude)
        //si es la primera vez que obtiene la ubicación actual que mueva la cámara a la ubicación
        if(primeraUbicacion){
            marcadorUbicacionActual = mMap.addMarker(MarkerOptions().position(latLng).title("Ubicación actual"))
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
            primeraUbicacion = false
        }else{
            marcadorUbicacionActual?.remove()
            marcadorUbicacionActual = mMap.addMarker(MarkerOptions().position(latLng).title("Ubicación actual"))
        }
    }
    private fun loadLocations(context: Context){
        val filename = "locations.json"
        val inputStream = context.assets.open(filename)
        val jsonString = inputStream.bufferedReader().use { it.readText() }
        // Primero obtenemos el objeto
        val rootObject = JSONObject(jsonString)
        // Ahora sí accedemos al array dentro de ese objeto
        val jsonArray = rootObject.getJSONArray("locationsArray")

        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val lat = jsonObject.getDouble("latitude")
            val lon = jsonObject.getDouble("longitude")
            val name = jsonObject.getString("name")
            val latLng = LatLng(lat, lon)
            mMap.addMarker(MarkerOptions().position(latLng).title(name).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)))
        }
    }
    private fun obtenerDisponibilidad(){
        val database = FirebaseDatabase.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid != null) {
            val ref = database.getReference("users").child(uid).child("available")

            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    available = snapshot.getValue(Boolean::class.java)
                    available?.let { cambiarDisponibilidad() }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("RealtimeDB", "Error al leer disponible", error.toException())
                }
            })
        }
    }
    private fun actualizarDisponibilidad(){
        val database = FirebaseDatabase.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid != null) {
            val ref = database.getReference("users").child(uid).child("available")
            ref.setValue(available)
                .addOnSuccessListener {
                    Log.d("RealtimeDB", "Disponibilidad actualizada a $available")
                }
                .addOnFailureListener {
                    Log.e("RealtimeDB", "Error al actualizar disponibilidad", it)
                }
        }

    }
    private fun cambiarDisponibilidad(){
        val color = if (available == true) Color.GREEN else Color.RED
        binding.availableButton.text = if (available == true) "Disponible" else "No disponible"
        binding.availableButton.setBackgroundColor(color)
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        loadLocations(this)
        mMap?.uiSettings?.isZoomControlsEnabled = true
    }

}