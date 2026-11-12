package com.asl.wallet.update

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the UI gate reads. [Required] blocks the whole app until the update is installed. */
enum class UpdateState { Unknown, UpToDate, Required, InProgress }

/**
 * Drives Google Play's IMMEDIATE (full-screen, blocking) in-app update.
 *
 * Fintech rule: when Play has a newer version the user must install it before using the app. If they dismiss
 * Play's screen, [state] stays [UpdateState.Required] and the app shows its own blocking "Update required" screen
 * (see UpdateRequiredScreen) whose button re-launches the Play flow via [promptUpdate].
 *
 * In-app updates only fire for builds installed from Google Play (internal testing counts). On a sideloaded or
 * emulator build [AppUpdateManager] fails the check, so the gate stays open and testers are never blocked.
 */
class AppUpdateController(
    private val activity: Activity,
    private val launcher: ActivityResultLauncher<IntentSenderRequest>,
    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(activity),
) {
    private val _state = MutableStateFlow(UpdateState.Unknown)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Call from the activity's onResume: checks Play and (re)launches the immediate flow if one is pending. */
    fun check() {
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                val resuming = info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                if ((resuming || available) && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    _state.value = UpdateState.Required
                    startFlow(info)
                } else {
                    _state.value = UpdateState.UpToDate
                }
            }
            .addOnFailureListener { _state.value = UpdateState.UpToDate } // offline / not installed from Play: don't block
    }

    /** From the "Update required" screen button: fetch fresh info and re-launch the Play flow. */
    fun promptUpdate() {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) startFlow(info)
        }
    }

    /** Wire this to the activity-result callback. A cancelled/failed immediate update keeps the gate blocking. */
    fun onFlowResult(ok: Boolean) { _state.value = if (ok) UpdateState.InProgress else UpdateState.Required }

    private fun startFlow(info: AppUpdateInfo) {
        _state.value = UpdateState.InProgress
        runCatching {
            manager.startUpdateFlowForResult(
                info,
                launcher,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
            )
        }.onFailure { _state.value = UpdateState.Required }
    }
}
