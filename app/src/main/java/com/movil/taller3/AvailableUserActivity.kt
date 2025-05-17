package com.movil.taller3

import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.movil.taller3.databinding.ActivityAvailableUserBinding
import com.movil.taller3.models.User
import kotlin.math.roundToInt

class AvailableUserActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityAvailableUserBinding

    // Sensors
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private lateinit var sensorEventListener: SensorEventListener

    // Location
    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var currentLocationMarker: Marker? = null
    private var trackedUserMarker: Marker? = null
    private var currentLocation: LatLng? = null
    private var trackedUserId: String? = null
    private var trackedUserName: String? = null
    private var locationListener: ValueEventListener? = null

    val locationSettings = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
        ActivityResultCallback {
            if (it.resultCode == RESULT_OK) {
                startLocationUpdates()
            } else {
                Toast.makeText(this, "El GPS está apagado", Toast.LENGTH_SHORT).show()
            }
        }
    )

    val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ActivityResultCallback {
            if (it) {
                startLocationUpdates()
            } else {
                Toast.makeText(this, "No hay permiso para acceder al GPS", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAvailableUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get tracked user information from intent
        trackedUserId = intent.getStringExtra("USER_ID")
        trackedUserName = intent.getStringExtra("USER_NAME")

        // Setup map
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Setup sensors
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        sensorEventListener = createSensorEventListener()

        // Setup location services
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = createLocationRequest()
        locationCallback = createLocationCallback()

        // Setup back button
        binding.buttonBack.setOnClickListener {
            finish()
        }

        // Set user name
        binding.textViewUsername.text = trackedUserName ?: "Usuario"
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(sensorEventListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)

        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        } else {
            locationPermission.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // Start tracking user
        trackedUserId?.let { startUserTracking(it) }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
        sensorManager.unregisterListener(sensorEventListener)
        // Remove database listener
        if (locationListener != null) {
            val database = FirebaseDatabase.getInstance()
            val userRef = database.getReference("users")
            userRef.removeEventListener(locationListener!!)
        }
    }

    private fun createSensorEventListener(): SensorEventListener {
        return object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
                    val lightValue = event.values[0]
                    // Change map type based on light sensor
                    if (::mMap.isInitialized) {
                        if (lightValue < 50) {
                            // Modo nocturno para poca luz
                            mMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
                        } else {
                            // Modo normal para buena iluminación
                            mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Not needed for this implementation
            }
        }
    }

    private fun createLocationRequest(): LocationRequest {
        return LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setWaitForAccurateLocation(true)
            .setMinUpdateIntervalMillis(3000)
            .build()
    }

    private fun createLocationCallback(): LocationCallback {
        return object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                result.lastLocation?.let { location ->
                    val latLng = LatLng(location.latitude, location.longitude)
                    currentLocation = latLng

                    // Update current location marker
                    if (currentLocationMarker == null) {
                        currentLocationMarker = mMap.addMarker(
                            MarkerOptions()
                                .position(latLng)
                                .title("Mi ubicación")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                        )
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                    } else {
                        currentLocationMarker?.position = latLng
                    }

                    // Update distance calculation
                    updateDistanceToUser()

                    // Update current user location in database
                    updateMyLocationInDatabase(latLng)
                }
            }
        }
    }

    private fun updateMyLocationInDatabase(location: LatLng) {
        val database = FirebaseDatabase.getInstance()
        val currentUserID = FirebaseAuth.getInstance().currentUser?.uid

        if (currentUserID != null) {
            val userRef = database.getReference("users").child(currentUserID)
            userRef.child("latitude").setValue(location.latitude)
            userRef.child("longitude").setValue(location.longitude)
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

    private fun stopLocationUpdates() {
        locationClient.removeLocationUpdates(locationCallback)
    }

    private fun startUserTracking(userId: String) {
        val database = FirebaseDatabase.getInstance()
        val userRef = database.getReference("users")

        locationListener = userRef.orderByChild("idNumber").equalTo(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (userSnapshot in snapshot.children) {
                        val user = userSnapshot.getValue(User::class.java)
                        if (user != null) {
                            updateTrackedUserLocation(user)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("AvailableUserActivity", "Error tracking user", error.toException())
                }
            })
    }

    private fun updateTrackedUserLocation(user: User) {
        val userLocation = LatLng(user.latitude, user.longitude)

        if (trackedUserMarker == null) {
            trackedUserMarker = mMap.addMarker(
                MarkerOptions()
                    .position(userLocation)
                    .title("${user.firstName} ${user.lastName}")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )

            // Initial zoom to show both markers
            if (currentLocation != null) {
                zoomToShowBothMarkers(currentLocation!!, userLocation)
            }
        } else {
            trackedUserMarker?.position = userLocation
        }

        // Update user info in UI
        binding.textViewUsername.text = "${user.firstName} ${user.lastName}"

        // Update distance calculation
        updateDistanceToUser()
    }

    private fun updateDistanceToUser() {
        val currentLoc = currentLocation
        val trackedMarker = trackedUserMarker

        if (currentLoc != null && trackedMarker != null) {
            val results = FloatArray(1)
            Location.distanceBetween(
                currentLoc.latitude, currentLoc.longitude,
                trackedMarker.position.latitude, trackedMarker.position.longitude,
                results
            )

            val distanceInMeters = results[0]
            val formattedDistance = if (distanceInMeters < 1000) {
                "${distanceInMeters.roundToInt()} metros"
            } else {
                String.format("%.2f kilómetros", distanceInMeters / 1000)
            }

            binding.textViewDistance.text = "Distancia: $formattedDistance"
        }
    }

    private fun zoomToShowBothMarkers(location1: LatLng, location2: LatLng) {
        val bounds = LatLngBounds.Builder()
            .include(location1)
            .include(location2)
            .build()

        mMap.animateCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds,
                100  // padding
            )
        )
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true

        // Enable location display
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
        }
    }
}