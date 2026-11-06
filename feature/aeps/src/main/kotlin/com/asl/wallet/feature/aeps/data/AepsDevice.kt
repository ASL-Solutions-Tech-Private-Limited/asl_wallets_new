package com.asl.wallet.feature.aeps.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.asl.wallet.feature.aeps.domain.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Real device location. No fallback coordinates: AEPS waits for a real fix. */
interface LocationSource {
    fun hasPermission(): Boolean
    fun isEnabled(): Boolean
    suspend fun current(): GeoPoint?
}

@Singleton
class AndroidLocationSource @Inject constructor(@ApplicationContext private val context: Context) : LocationSource {
    private val lm get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun isEnabled() = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    override suspend fun current(): GeoPoint? {
        if (!hasPermission() || !isEnabled()) return null
        val recent = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < MAX_AGE_MS }
            .minByOrNull { it.accuracy }
        if (recent != null) return recent.toPoint()
        val provider = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
        return withTimeoutOrNull(FIX_TIMEOUT_MS) { fresh(provider) }?.toPoint()
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

    private fun Location.toPoint() = GeoPoint(latitude, longitude)

    companion object { const val MAX_AGE_MS = 2 * 60_000L; const val FIX_TIMEOUT_MS = 15_000L }
}

/** Last chosen bank and scanner. Not personal data. */
@Singleton
class AepsPrefs @Inject constructor(@AepsDataStore private val ds: DataStore<Preferences>) {
    private val providerKey = intPreferencesKey("last_provider")
    private val deviceKey = stringPreferencesKey("last_device")
    suspend fun lastProvider(): Int? = ds.data.first()[providerKey]
    suspend fun setLastProvider(id: Int) { ds.edit { it[providerKey] = id } }
    suspend fun lastDevice(): String? = ds.data.first()[deviceKey]
    suspend fun setLastDevice(id: String) { ds.edit { it[deviceKey] = id } }
}

/**
 * Document photos: taken into the app's private cache (never the gallery), shrunk to ≤ 1 MB JPEG,
 * and every file is deleted after upload or when onboarding is left.
 */
object PhotoFiles {
    const val DIR = "aeps_docs"
    const val MAX_BYTES = 1_000_000

    fun newFile(context: Context, name: String): File = File(File(context.cacheDir, DIR).apply { mkdirs() }, "$name-${System.nanoTime()}.jpg")

    fun wipeAll(context: Context) { File(context.cacheDir, DIR).listFiles()?.forEach { it.delete() } }

    /** Downscale to max 1600 px and lower quality until it fits [MAX_BYTES]. Returns null if unreadable. */
    fun compress(file: File): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        var q = 85
        var out: ByteArray
        do {
            out = ByteArrayOutputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, q, it); it.toByteArray() }
            q -= 10
        } while (out.size > MAX_BYTES && q >= 35)
        bmp.recycle()
        return out.takeIf { it.size <= MAX_BYTES }
    }
}
