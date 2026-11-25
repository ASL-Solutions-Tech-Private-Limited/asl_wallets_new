package com.asl.wallet.feature.matm.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.asl.wallet.feature.matm.domain.GeoPoint
import com.asl.wallet.feature.matm.domain.MatmInit
import com.asl.wallet.feature.matm.domain.MatmResult
import com.fingpay.microatmsdk.MicroAtmLoginScreen
import com.fingpay.microatmsdk.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** The outcome of launching the FingPay card reader. */
sealed interface MatmDeviceResult {
    data class Ok(val result: MatmResult) : MatmDeviceResult
    data object Cancelled : MatmDeviceResult
}

// ---------------------------------------------------------------- location

/** Real device location. mATM is geo-tagged; no fallback coordinates. */
interface LocationSource {
    fun hasPermission(): Boolean
    fun isEnabled(): Boolean
    suspend fun current(): GeoPoint?
}

@Singleton
class AndroidLocationSource @Inject constructor(@ApplicationContext private val context: Context) : LocationSource {
    private val lm get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun isEnabled() = runCatching {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }.getOrDefault(false)

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(context) }

    /**
     * Robust fix that works on the first try indoors: recent cached fix -> fused fresh fix (GPS+Wi-Fi+cell)
     * -> fused last location -> any stale last-known. See the AEPS source for the full rationale.
     */
    @SuppressLint("MissingPermission")
    override suspend fun current(): GeoPoint? {
        if (!hasPermission() || !isEnabled()) return null

        recentCached()?.let { return it.toPoint() }

        val fresh = withTimeoutOrNull(FIX_TIMEOUT_MS) {
            val cts = CancellationTokenSource()
            try {
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
            } finally {
                cts.cancel()
            }
        }
        if (fresh != null) return fresh.toPoint()

        runCatching { fused.lastLocation.await() }.getOrNull()?.let { return it.toPoint() }

        return lastKnownAnyAge()?.toPoint()
    }

    @SuppressLint("MissingPermission")
    private fun recentCached(): Location? =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < MAX_AGE_MS }
            .minByOrNull { it.accuracy }

    @SuppressLint("MissingPermission")
    private fun lastKnownAnyAge(): Location? =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

    private suspend fun <T> Task<T>.await(): T? = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener { if (cont.isActive) cont.resume(null) }
        addOnCanceledListener { if (cont.isActive) cont.resume(null) }
    }

    private fun Location.toPoint() = GeoPoint(latitude, longitude)

    companion object { const val MAX_AGE_MS = 2 * 60_000L; const val FIX_TIMEOUT_MS = 15_000L }
}

// ---------------------------------------------------------------- FingPay SDK launch

/** Builds the intent that opens the FingPay card-reader screen for [init] at [at]. */
internal fun matmIntent(context: Context, init: MatmInit, at: GeoPoint?): Intent =
    Intent(context, MicroAtmLoginScreen::class.java).apply {
        putExtra(Constants.SUPER_MERCHANTID, init.superMerchantId)
        putExtra(Constants.MERCHANT_USERID, init.merchantUserId)
        putExtra(Constants.MERCHANT_PASSWORD, init.merchantPassword)
        putExtra(Constants.AMOUNT, init.amount)
        putExtra(Constants.REMARKS, init.remarks)
        putExtra(Constants.MOBILE_NUMBER, init.mobileNumber)
        putExtra(Constants.AMOUNT_EDITABLE, init.amountEditable)
        putExtra(Constants.TXN_ID, init.txnId.ifBlank { "SF${System.currentTimeMillis()}" })
        putExtra(Constants.IMEI, init.imei)
        putExtra(Constants.LATITUDE, at?.lat ?: 0.0)
        putExtra(Constants.LONGITUDE, at?.lng ?: 0.0)
        putExtra(Constants.TYPE, if (init.type.initType == 4) Constants.BALANCE_ENQUIRY else Constants.CASH_WITHDRAWAL)
    }

/** Reads the FingPay result intent into a [MatmResult]. */
internal fun Intent?.toMatmResult(): MatmResult = MatmResult(
    status = this?.getBooleanExtra(Constants.TRANS_STATUS, false) ?: false,
    message = this?.getStringExtra(Constants.MESSAGE).orEmpty(),
    transAmount = this?.getDoubleExtra(Constants.TRANS_AMOUNT, 0.0) ?: 0.0,
    balanceAmount = this?.getDoubleExtra(Constants.BALANCE_AMOUNT, 0.0) ?: 0.0,
    bankRrn = this?.getStringExtra(Constants.RRN).orEmpty(),
    transType = this?.getStringExtra(Constants.TRANS_TYPE).orEmpty(),
    type = this?.getIntExtra(Constants.TYPE, Constants.CASH_WITHDRAWAL) ?: Constants.CASH_WITHDRAWAL,
    cardNum = this?.getStringExtra(Constants.CARD_NUM).orEmpty(),
    bankName = this?.getStringExtra(Constants.BANK_NAME).orEmpty(),
    cardType = this?.getStringExtra(Constants.CARD_TYPE).orEmpty(),
    terminalId = this?.getStringExtra(Constants.TERMINAL_ID).orEmpty(),
    txnId = this?.getStringExtra(Constants.TXN_ID).orEmpty(),
    mobileNumber = this?.getStringExtra(Constants.MOBILE_NUMBER).orEmpty(),
    fpId = this?.getStringExtra(Constants.FP_TRANS_ID).orEmpty(),
    errorCode = this?.getStringExtra(Constants.RESPONSE_CODE).orEmpty(),
)

/**
 * Returns a function that launches the FingPay card reader for a [MatmInit]; [onResult] gets the
 * parsed outcome. Mirrors the AEPS `rememberRdCapture` pattern.
 */
@Composable
fun rememberMatmLauncher(onResult: (MatmDeviceResult) -> Unit): (MatmInit, GeoPoint?) -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) callback.value(MatmDeviceResult.Ok(res.data.toMatmResult()))
        else callback.value(MatmDeviceResult.Cancelled)
    }
    return remember { { init, at -> launcher.launch(matmIntent(context, init, at)) } }
}
