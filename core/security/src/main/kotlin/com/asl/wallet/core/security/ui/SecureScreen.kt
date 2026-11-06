package com.asl.wallet.core.security.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.atomic.AtomicInteger

private val secureCount = AtomicInteger(0)

/**
 * Blocks screenshots, screen recording and the recent-apps preview while this composable is on screen.
 * Use on login, OTP, money review, receipts with personal data, KYC and AEPS screens.
 * Reference-counted so nested secure screens work.
 */
@Composable
fun SecureScreen() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        if (secureCount.incrementAndGet() == 1) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (secureCount.decrementAndGet() == 0) {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
