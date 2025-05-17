package com.movil.taller3

import android.content.IntentSender
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
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.Task
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

    // Sensores
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private lateinit var sensorEventListener: SensorEventListener

    // Localización
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

        trackedUserId = intent.getStringExtra("USER_ID")
        trackedUserName = intent.getStringExtra("USER_NAME")

        locationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = createLocationRequest()
        locationCallback = createLocationCallback()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        sensorEventListener = createSensorEventListener()

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

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

        binding.buttonBack.setOnClickListener {
            finish()
        }

        binding.textViewUsername.text = trackedUserName ?: "Usuario"
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
                    if (::mMap.isInitialized) {
                        if (lightValue < 50) {
                            mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
                        } else {
                            mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
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

                    updateDistanceToUser()

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

        Log.d("AvailableUserActivity", "Comenzando seguimiento de usuario con ID: $userId")

        // Escuchar cambios en todos los usuarios, independientemente de su idNumber o ID de nodo
        locationListener = userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var userFound = false

                for (userSnapshot in snapshot.children) {
                    val user = userSnapshot.getValue(User::class.java)

                    // Verificar la coincidencia por idNumber
                    if (user != null && user.idNumber == userId) {
                        Log.d("AvailableUserActivity", "Usuario encontrado por idNumber: ${user.firstName} ${user.lastName}")
                        updateTrackedUserLocation(user)
                        userFound = true
                        break
                    }

                    // Alternativa: verificar si el key del snapshot coincide con userId
                    if (userSnapshot.key == userId) {
                        val userByKey = userSnapshot.getValue(User::class.java)
                        if (userByKey != null) {
                            Log.d("AvailableUserActivity", "Usuario encontrado por key: ${userByKey.firstName} ${userByKey.lastName}")
                            updateTrackedUserLocation(userByKey)
                            userFound = true
                            break
                        }
                    }
                }

                if (!userFound) {
                    Log.d("AvailableUserActivity", "No se encontró al usuario con ID: $userId")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("AvailableUserActivity", "Error tracking user", error.toException())
            }
        })
    }

    private fun updateTrackedUserLocation(user: User) {
        // Verificamos que las coordenadas sean válidas
        if (user.latitude == 0.0 && user.longitude == 0.0) {
            Log.d("AvailableUserActivity", "Coordenadas inválidas para el usuario: ${user.firstName} ${user.lastName}")
            return
        }

        val userLocation = LatLng(user.latitude, user.longitude)
        Log.d("AvailableUserActivity", "Actualizando ubicación de usuario seguido: Lat=${user.latitude}, Lng=${user.longitude}")

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
            Log.d("AvailableUserActivity", "Marcador de usuario seguido actualizado")
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
            val trackedLatLng = trackedMarker.position

            Log.d("AvailableUserActivity", "Calculando distancia desde: Lat=${currentLoc.latitude}, Lng=${currentLoc.longitude}")
            Log.d("AvailableUserActivity", "Hasta: Lat=${trackedLatLng.latitude}, Lng=${trackedLatLng.longitude}")

            Location.distanceBetween(
                currentLoc.latitude, currentLoc.longitude,
                trackedLatLng.latitude, trackedLatLng.longitude,
                results
            )

            val distanceInMeters = results[0]
            val formattedDistance = if (distanceInMeters < 1000) {
                "${distanceInMeters.roundToInt()} metros"
            } else {
                String.format("%.2f kilómetros", distanceInMeters / 1000)
            }

            Log.d("AvailableUserActivity", "Distancia calculada: $formattedDistance")
            binding.textViewDistance.text = "Distancia: $formattedDistance"
        } else {
            if (currentLoc == null) {
                Log.d("AvailableUserActivity", "No hay ubicación actual para calcular distancia")
            }
            if (trackedMarker == null) {
                Log.d("AvailableUserActivity", "No hay marcador de usuario seguido para calcular distancia")
            }
        }
    }

    private fun zoomToShowBothMarkers(location1: LatLng, location2: LatLng) {
        // Verificamos que las ubicaciones sean válidas
        if ((location1.latitude == 0.0 && location1.longitude == 0.0) ||
            (location2.latitude == 0.0 && location2.longitude == 0.0)) {
            Log.d("AvailableUserActivity", "Coordenadas inválidas para centrar mapa")
            return
        }

        try {
            val bounds = LatLngBounds.Builder()
                .include(location1)
                .include(location2)
                .build()

            Log.d("AvailableUserActivity", "Centrando mapa para mostrar ambos marcadores")
            mMap.animateCamera(
                CameraUpdateFactory.newLatLngBounds(
                    bounds,
                    120  // Aumentamos el padding para mejor visualización
                )
            )
        } catch (e: Exception) {
            Log.e("AvailableUserActivity", "Error al centrar el mapa", e)

            // Si hay un error, intentamos un enfoque más simple
            val centerPoint = LatLng(
                (location1.latitude + location2.latitude) / 2,
                (location1.longitude + location2.longitude) / 2
            )
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(centerPoint, 14f))
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true

        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
        }
    }
}