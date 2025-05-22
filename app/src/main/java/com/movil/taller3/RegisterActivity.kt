package com.movil.taller3

import android.Manifest
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
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
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.movil.taller3.databinding.ActivityRegisterBinding
import com.movil.taller3.models.User

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private val database = FirebaseDatabase.getInstance().reference

    // Ubicación
    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0

    // Para imagen
    private var selectedImageUri: Uri? = null
    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>

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
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Inicializar el cliente de ubicación
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = createLocationRequest()
        locationCallback = createLocationCallback()

        initListeners()

        // Para la galería
        galleryLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (it.resultCode == RESULT_OK && it.data != null) {
                selectedImageUri = it.data!!.data
                binding.imgProfile.setImageURI(selectedImageUri)
            }
        }
    }

    private fun initListeners() {
        binding.btnRegister.setOnClickListener {
            validateAndRegister()
        }

        binding.btnSelectImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            galleryLauncher.launch(intent)
        }
    }

    private fun validateAndRegister() {
        val firstName = binding.etFirstName.text.toString()
        val lastName = binding.etLastName.text.toString()
        val email = binding.etEmail.text.toString()
        val password = binding.etPassword.text.toString()
        val idNumber = binding.etIdNumber.text.toString()

        // Validación de campos
        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() ||
            password.isEmpty() || idNumber.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        // Validación de imagen
        if (selectedImageUri == null) {
            Toast.makeText(this, "Selecciona una imagen de perfil", Toast.LENGTH_SHORT).show()
            return
        }

        // Pedir permiso para la ubicación
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_DENIED
        ) {
            if (shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(
                    this,
                    "El permiso se necesita para obtener su ubicación",
                    Toast.LENGTH_LONG
                ).show()
            }
            locationPermission.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            locationSettings()
        }
    }

    private fun locationSettings() {
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
        task.addOnSuccessListener { locationSettingsResponse ->
            // All location settings are satisfied. The client can initialize location requests here.
            startLocationUpdates()
        }
        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult()
                    val isr: IntentSenderRequest =
                        IntentSenderRequest.Builder(exception.resolution).build()
                    locationSettings.launch(isr)
                } catch (sendEx: IntentSender.SendIntentException) {
                    Toast.makeText(baseContext, "No hay hardware GPS", Toast.LENGTH_LONG)
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

    private fun stopLocationUpdates() {
        locationClient.removeLocationUpdates(locationCallback)
    }

    private fun createLocationRequest(): LocationRequest {
        return LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setWaitForAccurateLocation(true)
            .setMinUpdateIntervalMillis(5000)
            .build()
    }

    private fun createLocationCallback(): LocationCallback {
        return object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                val loc = result.lastLocation
                if (loc != null) {
                    latitude = loc.latitude
                    longitude = loc.longitude
                    // para de actualizar después de obtener la primera ubicación
                    stopLocationUpdates()
                    // procede con el registro una vez obtenida la ubicación
                    registerUser()
                }
            }
        }
    }

    private fun registerUser() {
        val firstName = binding.etFirstName.text.toString()
        val lastName = binding.etLastName.text.toString()
        val email = binding.etEmail.text.toString()
        val password = binding.etPassword.text.toString()
        val idNumber = binding.etIdNumber.text.toString()

        // Crea la cuenta
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener {
                if (it.isSuccessful) {
                    // Primero sube la imagen para obtener la URL antes de registrar al usuario
                    uploadProfileImage { imageUrl ->
                        val uid = auth.currentUser?.uid ?: return@uploadProfileImage

                        val user = User(
                            firstName = firstName,
                            lastName = lastName,
                            email = email,
                            idNumber = idNumber,
                            latitude = latitude,
                            longitude = longitude,
                            imageUrl = imageUrl
                        )

                        database.child("users").child(uid).setValue(user)
                            .addOnSuccessListener {
                                startActivity(Intent(this, MainMapsActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Error al guardar datos: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                } else {
                    Toast.makeText(this, "Error: ${it.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    // Subir imagen al storage de Firebase
    private fun uploadProfileImage(onUrlReady: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        // Se sube la foto con el perfil del usuario
        val imageRef = FirebaseStorage.getInstance().reference.child("profile_images/$uid.jpg")

        // Lanza operación asíncrona
        imageRef.putFile(selectedImageUri!!)
            .addOnSuccessListener {
                // Se pide la URL pública de la imagen subida
                imageRef.downloadUrl.addOnSuccessListener { uri ->
                    // Se ejecuta la función que se pasa por parámetro
                    onUrlReady(uri.toString())
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al subir imagen", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }
}