package com.asl.wallet.push

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.asl.wallet.core.common.session.DeviceIdentity

/**
 * Reads the FCM token from [FcmTokenStore] and the device id from the system, for the login request.
 * [deviceId] falls back to an empty string on the rare devices where ANDROID_ID is unavailable.
 */
class DefaultDeviceIdentity(private val context: Context) : DeviceIdentity {
    override fun fcmToken(): String? = FcmTokenStore.current(context)

    @Suppress("HardwareIds")
    override fun deviceId(): String =
        runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) }.getOrNull().orEmpty()

    override fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()
        val name = if (model.startsWith(manufacturer, ignoreCase = true)) model
            else listOf(manufacturer, model).filter { it.isNotEmpty() }.joinToString(" ")
        return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }.ifBlank { "Android" }
    }

    override fun onFcmSynced() = FcmTokenStore.markSynced(context)
}
