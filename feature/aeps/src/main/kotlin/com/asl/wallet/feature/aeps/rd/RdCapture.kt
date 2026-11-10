package com.asl.wallet.feature.aeps.rd

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.asl.wallet.feature.aeps.domain.CaptureType
import com.asl.wallet.feature.aeps.domain.Pid
import com.asl.wallet.feature.aeps.domain.RdDevice
import java.security.SecureRandom

sealed interface CaptureResult {
    /** [pid] is biometric data: pass it to the server once, never log or keep it. */
    data class Ok(val pid: String, val type: CaptureType) : CaptureResult
    data class Failed(val code: String, val info: String?) : CaptureResult
    data object Cancelled : CaptureResult
    data object NotInstalled : CaptureResult
}

object RdServices {
    fun captureIntent(device: RdDevice, wadh: String?, otp: String? = null, selfPackage: String? = null): Intent {
        val options = if (device.isFace) Pid.faceOptions(txnId(), wadh.orEmpty(), otp.orEmpty()) else Pid.fingerOptions(wadh.orEmpty(), otp.orEmpty())
        return Intent(if (device.isFace) Pid.FACE_ACTION else Pid.FINGER_ACTION).apply {
            putExtra(if (device.isFace) "request" else "PID_OPTIONS", options)
            setPackage(if (device.packageName == Pid.SELF_PACKAGE) selfPackage.orEmpty() else device.packageName)
        }
    }

    fun isInstalled(context: Context, device: RdDevice): Boolean =
        captureIntent(device, null, selfPackage = context.packageName).resolveActivity(context.packageManager) != null

    fun openStore(context: Context, packageName: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).setPackage("com.android.vending")
        try { context.startActivity(market) } catch (_: ActivityNotFoundException) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))) }
        }
    }

    /**
     * A few well-known RD service codes in plain words. For anything else the RD service's own errInfo is shown,
     * because codes differ between vendors.
     */
    fun explain(code: String, info: String?): String = when (code) {
        "700" -> "Capture timed out. Ask the person to place the finger again."
        "710" -> "The scanner is being used by another app. Close it and try again."
        "720" -> "Scanner not ready. Unplug it, plug it in again and wait for the light."
        else -> info?.trim()?.takeIf { it.isNotEmpty() && it.length < 120 } ?: "Capture failed (code $code). Try again."
    }

    private fun txnId(): String = (10_000_000 + SecureRandom().nextInt(89_999_999)).toString()
}

/** Returns a function that starts a capture on [device]; [onResult] gets the parsed PID. */
@Composable
fun rememberRdCapture(onResult: (CaptureResult) -> Unit): (RdDevice, String?, String?) -> Unit {
    val callback = rememberUpdatedState(onResult)
    val pending = remember { arrayOfNulls<RdDevice>(1) }
    val selfPackage = androidx.compose.ui.platform.LocalContext.current.packageName
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val device = pending[0] ?: return@rememberLauncherForActivityResult
        pending[0] = null
        if (res.resultCode != Activity.RESULT_OK) { callback.value(CaptureResult.Cancelled); return@rememberLauncherForActivityResult }
        val xml = res.data?.getStringExtra(if (device.isFace) "response" else "PID_DATA")
        callback.value(
            when (val p = Pid.parse(xml)) {
                is Pid.Parsed.Ok -> CaptureResult.Ok(p.pidXml, if (device.isFace) CaptureType.FACE else CaptureType.FINGER)
                is Pid.Parsed.Error -> CaptureResult.Failed(p.code, p.info)
                Pid.Parsed.Invalid -> CaptureResult.Failed("-1", null)
            },
        )
    }
    return remember {
        { device, wadh, otp ->
            pending[0] = device
            try { launcher.launch(RdServices.captureIntent(device, wadh, otp, selfPackage)) } catch (_: ActivityNotFoundException) {
                pending[0] = null; callback.value(CaptureResult.NotInstalled)
            }
        }
    }
}
