package com.movil.taller3.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.JobIntentService
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.movil.taller3.AvailableUserActivity
import com.movil.taller3.LoginActivity
import com.movil.taller3.models.User


class AvailabilityListenerService : JobIntentService() {

    companion object {
        private const val JOB_ID = 1010
        private const val CHANNEL_ID = "availability_channel"

        fun enqueueWork(context: Context, work: Intent) {
            enqueueWork(context, AvailabilityListenerService::class.java, JOB_ID, work)
        }
    }

    private lateinit var usersRef: DatabaseReference
    private var listener: ChildEventListener? = null

    override fun onHandleWork(intent: Intent) {
        Log.d("AvailabilityService", "Servicio iniciado")

        usersRef = FirebaseDatabase.getInstance().getReference("users")

        listener = object : ChildEventListener {
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val user = snapshot.getValue(User::class.java)
                val becameAvailable = snapshot.child("available").getValue(Boolean::class.java) == true
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

                if (user != null && becameAvailable && snapshot.key != currentUserId) {
                    showNotification(user)
                }
            }

            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e("AvailabilityService", "Error: ${error.message}")
            }
        }

        usersRef.addChildEventListener(listener!!)
        Thread.sleep(30000) // Mantener vivo por 30 segundos para captar cambios (puedes ajustar)
        usersRef.removeEventListener(listener!!)
        Log.d("AvailabilityService", "Servicio finalizado")
    }

    private fun showNotification(user: User) {
        createNotificationChannel()

        val context = applicationContext
        val targetIntent = if (FirebaseAuth.getInstance().currentUser != null) {
            Intent(context, AvailableUserActivity::class.java).apply {
                putExtra("USER_ID", user.idNumber)
                putExtra("USER_NAME", "${user.firstName} ${user.lastName}")
            }
        } else {
            Intent(context, LoginActivity::class.java)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            targetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("${user.firstName} está disponible")
            .setContentText("Toca para hacer seguimiento")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify((1000..9999).random(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Notificaciones de disponibilidad"
            val description = "Avisos cuando un usuario se pone disponible"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                this.description = description
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
