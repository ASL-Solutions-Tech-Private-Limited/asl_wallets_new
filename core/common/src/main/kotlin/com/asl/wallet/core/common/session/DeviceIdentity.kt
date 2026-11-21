package com.asl.wallet.core.common.session

/**
 * Per-device values the login API wants: the current FCM push token and a stable device id.
 * Implemented in the app module (it needs the Android context and the FCM token store); injected where login is built.
 */
interface DeviceIdentity {
    /** The latest FCM registration token, or null if one has not been minted yet. */
    fun fcmToken(): String?

    /** A stable per-install id for this device (Settings.Secure.ANDROID_ID). */
    fun deviceId(): String

    /** A human-readable model name for this device, e.g. "Samsung SM-S911B". */
    fun deviceName(): String

    /** Called after the backend has accepted the token (e.g. on a successful login), so it isn't re-sent needlessly. */
    fun onFcmSynced() {}
}
