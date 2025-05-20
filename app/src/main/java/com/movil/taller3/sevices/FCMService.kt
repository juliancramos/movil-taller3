package com.movil.taller3

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FirebaseMsgService"
        private const val CHANNEL_ID = "user_availability_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")
        // Enviar este token al servidor para actualizarlo en la base de datos
        updateTokenInDatabase(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Verificar si el mensaje contiene datos
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }

        // Verificar si el mensaje contiene una notificación
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            it.body?.let { body ->
                sendNotification(it.title ?: "Usuario disponible", body, remoteMessage.data)
            }
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val userId = data["userId"] ?: return
        val userName = data["userName"] ?: "Usuario disponible"
        val message = data["message"] ?: "Un usuario está disponible para seguimiento"

        sendNotification(userName, message, data)
    }

    private fun sendNotification(title: String, messageBody: String, data: Map<String, String>) {
        val userId = data["userId"] ?: ""
        val userName = data["userName"] ?: ""

        // Crear intent para abrir la actividad de seguimiento cuando se toca la notificación
        val intent = if (isUserLoggedIn()) {
            Intent(this, AvailableUserActivity::class.java).apply {
                putExtra("USER_ID", userId)
                putExtra("USER_NAME", userName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            // Si no hay sesión activa, redirigir al login
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_add_reaction_24)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Crear canal de notificación para Android Oreo y superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "User Availability Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones sobre usuarios disponibles"
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun isUserLoggedIn(): Boolean {
        // Verificar si hay un usuario autenticado
        return com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null
    }

    private fun updateTokenInDatabase(token: String) {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: return
        val database = com.google.firebase.database.FirebaseDatabase.getInstance().reference
        database.child("users").child(currentUser.uid).child("fcmToken").setValue(token)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Token actualizado en la base de datos")
                } else {
                    Log.e(TAG, "Error al actualizar token", task.exception)
                }
            }
    }
}