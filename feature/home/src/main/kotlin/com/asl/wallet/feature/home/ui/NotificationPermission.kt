package com.asl.wallet.feature.home.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Once per app run, so coming back to the dashboard from another screen doesn't ask again. */
private object NotificationAsk { var askedThisRun = false }

/**
 * On the dashboard (right after login or app start): asks for notification permission when it isn't granted.
 * Android 13+ only (older phones allow notifications by default). Android itself stops showing the prompt after the
 * retailer refuses twice, so this never nags.
 */
@Composable
internal fun AskNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (NotificationAsk.askedThisRun) return@LaunchedEffect
        NotificationAsk.askedThisRun = true
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
