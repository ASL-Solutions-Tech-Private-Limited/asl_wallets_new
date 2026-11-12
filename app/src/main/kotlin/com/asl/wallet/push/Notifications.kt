package com.asl.wallet.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.edit

/** Notification channels. Transaction alerts are high importance; news is default. */
object NotificationChannels {
    const val TRANSACTIONS = "asl_transactions"
    const val UPDATES = "asl_updates"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.createNotificationChannel(
            NotificationChannel(TRANSACTIONS, "Transactions & wallet", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Payments, payouts, recharges and wallet balance alerts"
            },
        )
        mgr.createNotificationChannel(
            NotificationChannel(UPDATES, "Offers & updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "News, offers and service updates"
            },
        )
    }
}

/**
 * Stores the current FCM registration token so the app can send it to the backend once signed in.
 * Kept here (not in the Messaging service) because the token arrives before Hilt graph is usable in the service.
 * Wire [pending] into the backend's device-register endpoint when it is available.
 */
object FcmTokenStore {
    private const val PREFS = "asl_fcm"
    private const val KEY_TOKEN = "token"
    private const val KEY_SYNCED = "synced"

    fun save(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_TOKEN, token); putBoolean(KEY_SYNCED, false)
        }
    }

    /** The token that still needs sending to the backend, or null if none / already sent. */
    fun pending(context: Context): String? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getString(KEY_TOKEN, null)?.takeIf { !p.getBoolean(KEY_SYNCED, false) }
    }

    fun markSynced(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_SYNCED, true) }
    }
}
