package com.movil.taller3.utils

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.movil.taller3.models.User
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object NotificationManager {
    private const val TAG = "NotificationManager"
    private const val FCM_API = "https://fcm.googleapis.com/fcm/send"

    // Reemplazar con tu clave de servidor de FCM
    private const val SERVER_KEY = "YOUR_FCM_SERVER_KEY"
    private const val CONTENT_TYPE = "application/json"

    private val client = OkHttpClient()

    fun sendNotificationToAllExcept(
        currentUserId: String,
        userName: String,
        message: String,
        userId: String
    ) {
        val database = FirebaseDatabase.getInstance()
        val usersRef = database.getReference("users")

        usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (userSnapshot in snapshot.children) {
                    // No enviar notificación al usuario actual
                    if (userSnapshot.key == currentUserId) continue

                    val user = userSnapshot.getValue(User::class.java)
                    user?.let {
                        if (it.fcmToken.isNotEmpty()) {
                            sendNotification(it.fcmToken, userName, message, userId, userName)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error fetching users for notifications", error.toException())
            }
        })
    }

    private fun sendNotification(
        token: String,
        title: String,
        message: String,
        userId: String,
        userName: String
    ) {
        try {
            val json = JSONObject().apply {
                put("to", token)

                // Notificación para mostrar en la bandeja
                put("notification", JSONObject().apply {
                    put("title", title)
                    put("body", message)
                    put("sound", "default")
                })

                // Datos adicionales para el manejo en el servicio
                put("data", JSONObject().apply {
                    put("userId", userId)
                    put("userName", userName)
                    put("message", message)
                })
            }

            val request = Request.Builder()
                .url(FCM_API)
                .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .addHeader("Authorization", "key=$SERVER_KEY")
                .addHeader("Content-Type", CONTENT_TYPE)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Failed to send notification", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()
                    if (response.isSuccessful) {
                        Log.d(TAG, "Notification sent successfully: $responseBody")
                    } else {
                        Log.e(TAG, "Failed to send notification: $responseBody")
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception sending notification", e)
        }
    }
}