package com.movil.taller3

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.movil.taller3.databinding.ActivityRegisterBinding
import com.movil.taller3.models.User

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private val database = FirebaseDatabase.getInstance().reference

    private lateinit var locationPermissionLauncher: ActivityResultLauncher<String>

    // coordenadas de la ubicación actual del usuario
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0

    //para imagen
    private var selectedImageUri: Uri? = null
    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        initListeners()

        // permiso de localizacion
        locationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
            ActivityResultCallback { if (it) {
                getCurrentLocation()
            } else {
                Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
            }}
        )

        //para la galería
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
            validateAndGetLocation()
        }


        binding.btnSelectImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            galleryLauncher.launch(intent)
        }

    }


    private fun registerUser() {
        val firstName = binding.etFirstName.text.toString()
        val lastName = binding.etLastName.text.toString()
        val email = binding.etEmail.text.toString()
        val password = binding.etPassword.text.toString()
        val idNumber = binding.etIdNumber.text.toString()

        // crea la cuenta
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener {
                if (it.isSuccessful) {
                    // primero sube la imagen para obtener la url antes de registrar al usuario
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
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Error al guardar datos", Toast.LENGTH_LONG).show()
                            }
                    }
                } else {
                    Toast.makeText(this, "Error: ${it.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }
        //subir imagen al storage de firebase
        //tiene que recibir la función por parametro porque se ejecuta una función asíncrona
        private fun uploadProfileImage(onUrlReady: (String) -> Unit) {

            val uid = auth.currentUser?.uid ?: return
            //se sube la foto con el perfil del usuario
            val imageRef = FirebaseStorage.getInstance().reference.child("profile_images/$uid.jpg")

            //lanza operación asíncrona
            imageRef.putFile(selectedImageUri!!)
                .addOnSuccessListener {
                    //se pide la url publica de la imagen subida
                    imageRef.downloadUrl.addOnSuccessListener { uri ->
                        //se ejecuta la función que se pasa por parametro
                        //si acá se hiciera return en vez de ejecutar la función, entonces
                            //se haría return antes de que llegue el valor
                        onUrlReady(uri.toString())
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al subir imagen", Toast.LENGTH_SHORT).show()
                }
        }

    private fun validateAndGetLocation() {
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

        // se pide permiso para la ubicacion
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    //obtiene la ubicación actual para guardar latitud y longitud
    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        // se guardan las coordenadas en las variables para luego asignar
                        latitude = location.latitude
                        longitude = location.longitude
                        registerUser()
                    } else {
                        Toast.makeText(this, "No se pudo obtener la ubicación", Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            Toast.makeText(this, "No se pudo obtener tu ubicación", Toast.LENGTH_SHORT).show()
        }
    }

}
