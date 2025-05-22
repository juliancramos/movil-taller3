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

//se ejecuta en segundo plano
class AvailabilityListenerService : JobIntentService() {

    //companion para métodos y constantes estáticas
    companion object {
        //identificador del service en el sistema, como si fuera una llave primaria
        private const val JOB_ID = 1010
        private const val CHANNEL_ID = "availability_channel"

        //context la actividad que llama al servicio
        //work el intent que se pasa
        fun enqueueWork(context: Context, work: Intent) {
            //lanza el servicio identificado por JOB_ID
            enqueueWork(context, AvailabilityListenerService::class.java, JOB_ID, work)
        }
    }
    //referencia a realtime database
    private lateinit var usersRef: DatabaseReference  //referencia /users
    //listener para detectar cambios en la BD
    private var listener: ChildEventListener? = null

    //función principal -> se ejecuta cuando se lanza el trabajo al servicio
    override fun onHandleWork(intent: Intent) {
        //Log.d("AvailabilityService", "Servicio iniciado")

        //refecencia a "users" en la base de datos
        usersRef = FirebaseDatabase.getInstance().getReference("users")

        //escucha cambios
            //hay onChildAdded, changed, removed, moved, cancelled
        listener = object : ChildEventListener {
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                //trae el usuario que cambió y lo mapea a la clase usuario
                val user = snapshot.getValue(User::class.java)
                //almacena si el usuario está disponible o no
                val becameAvailable = snapshot.child("available")
                    .getValue(Boolean::class.java) == true

                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

                //si el usuario está disponible envía notificación al resto
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
        //registra el listeners
        usersRef.addChildEventListener(listener!!)
        Thread.sleep(30000) // Mantener vivo por 30 segundos para captar cambios (puedes ajustar)
        usersRef.removeEventListener(listener!!)
    }

    private fun showNotification(user: User) {
        //antes de enviar crea el canal de notificación
        createNotificationChannel()
        //context del servicio
        val context = applicationContext

        //si está logeado lo manda a la vista de el usuario
        val targetIntent = if (FirebaseAuth.getInstance().currentUser != null) {
            Intent(context, AvailableUserActivity::class.java).apply {
                putExtra("USER_ID", user.idNumber)
                putExtra("USER_NAME", "${user.firstName} ${user.lastName}")
            }
        } else {
            Intent(context, LoginActivity::class.java)
        }
        //intent que se ejecuta al lanzar la aplicación
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
            //la notificación se va si el usuario la toca
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        //para acceso a notificaciones
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify((1000..9999).random(), notification)
    }

    private fun createNotificationChannel() {
        //solo crea canal de notificaciones para android 8 o superior
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
