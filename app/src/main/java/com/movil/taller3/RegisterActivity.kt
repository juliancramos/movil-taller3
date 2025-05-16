package com.movil.taller3

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.movil.taller3.databinding.ActivityRegisterBinding
import com.movil.taller3.models.User

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private val database = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        initListeners()


    }

    private fun initListeners() {
        binding.btnRegister.setOnClickListener {
            val firstName = binding.etFirstName.text.toString()
            val lastName = binding.etLastName.text.toString()
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            val idNumber = binding.etIdNumber.text.toString()

            if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() ||
                password.isEmpty() || idNumber.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            //crea la cuenta
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener {
                    if (it.isSuccessful) {
                        //si no tiene id acaba la ejecución de addOnCompleteListener
                        val uid = auth.currentUser?.uid?:return@addOnCompleteListener



                        val user = User(
                            firstName = firstName,
                            lastName = lastName,
                            email = email,
                            idNumber = idNumber
                        )

                        database.child("users").child(uid).setValue(user)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Registro exitoso", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener { error ->
                                Toast.makeText(this, "Error al guardar datos", Toast.LENGTH_LONG).show()
                            }

                    } else {
                        Toast.makeText(this, "Error: ${it.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }

        }
    }
}