package com.asl.wallet.feature.wallet.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Where the shop is, sent with every payout (the payout API asks for latitude/longitude). */
data class ShopLocation(val latitude: Double, val longitude: Double)

/**
 * The phone's real location; no made-up fallback. Same approach as AEPS's location source
 * (TODO: move both into one core module).
 */
interface PayoutLocation {
    fun hasPermission(): Boolean
    fun isEnabled(): Boolean
    suspend fun current(): ShopLocation?
}

@Singleton
class AndroidPayoutLocation @Inject constructor(@ApplicationContext private val context: Context) : PayoutLocation {
    private val lm get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun isEnabled() = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    override suspend fun current(): ShopLocation? {
        if (!hasPermission() || !isEnabled()) return null
        val recent = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < MAX_AGE_MS }
            .minByOrNull { it.accuracy }
        if (recent != null) return recent.toShop()
        val provider = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
        return withTimeoutOrNull(FIX_TIMEOUT_MS) { fresh(provider) }?.toShop()
    }

    @SuppressLint("MissingPermission")
    private suspend fun fresh(provider: String): Location? = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { cont.resume(it) }
        } else {
            @Suppress("DEPRECATION")
            lm.requestSingleUpdate(provider, object : android.location.LocationListener {
                override fun onLocationChanged(l: Location) { if (cont.isActive) cont.resume(l) }
                @Deprecated("") override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
                override fun onProviderEnabled(p: String) {}
                override fun onProviderDisabled(p: String) { if (cont.isActive) cont.resume(null) }
            }, android.os.Looper.getMainLooper())
        }
    }

    private fun Location.toShop() = ShopLocation(latitude, longitude)

    companion object { const val MAX_AGE_MS = 2 * 60_000L; const val FIX_TIMEOUT_MS = 15_000L }
}
