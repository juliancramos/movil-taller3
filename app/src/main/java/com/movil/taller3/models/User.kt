package com.movil.taller3.models

data class User(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val idNumber: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val imageUrl: String = ""
)
