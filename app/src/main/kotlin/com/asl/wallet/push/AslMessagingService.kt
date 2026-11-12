package com.asl.wallet.push

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.asl.wallet.MainActivity
import com.asl.wallet.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random

/**
 * Receives FCM pushes. A notification payload is shown by the system when the app is backgrounded; this service
 * builds the notification itself so it also shows while the app is foregrounded and for data-only messages.
 */
class AslMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Persist; the app sends it to the backend once the device-register endpoint is wired and the user is signed in.
        FcmTokenStore.save(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        NotificationChannels.ensure(applicationContext)

        val data = message.data
        val title = message.notification?.title ?: data["title"] ?: getString(R.string.app_name)
        val body = message.notification?.body ?: data["body"] ?: data["message"] ?: return
        val channel = when (data["channel"]) {
            "updates", "offers" -> NotificationChannels.UPDATES
            else -> NotificationChannels.TRANSACTIONS
        }

        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data["route"]?.let { putExtra("route", it) }
        }
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching { NotificationManagerCompat.from(this).notify(Random.nextInt(), notification) }
    }
}
