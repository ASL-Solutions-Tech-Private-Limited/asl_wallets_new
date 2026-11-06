package com.asl.wallet.core.security.lock

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

fun interface Clock { fun nowMillis(): Long }

object SystemClock : Clock { override fun nowMillis() = android.os.SystemClock.elapsedRealtime() }

enum class LockState { Unlocked, Locked }

/**
 * Decides when the app must be unlocked again with the phone's screen lock.
 * Rules (decision log): lock after [backgroundTimeoutMs] in the background, or after [idleTimeoutMs] without touches.
 * The lock only applies while a session is active ([isSessionActive]); before login there is nothing to protect.
 *
 * Pure logic with an injectable [Clock], so the rules are unit-tested.
 */
class AppLockManager(
    private val clock: Clock = SystemClock,
    val backgroundTimeoutMs: Long = 30_000,
    val idleTimeoutMs: Long = 5 * 60_000,
) {
    private val _state = MutableStateFlow(LockState.Unlocked)
    val state: StateFlow<LockState> = _state.asStateFlow()

    @Volatile var isSessionActive: Boolean = false
        set(value) { field = value; if (!value) _state.value = LockState.Unlocked }

    private var backgroundedAt: Long? = null
    private var lastInteractionAt: Long = clock.nowMillis()

    /** Call on cold start once a session is restored, so the app opens locked. */
    fun lockNow() { if (isSessionActive) _state.value = LockState.Locked }

    fun onBackgrounded() { backgroundedAt = clock.nowMillis() }

    fun onForegrounded() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (isSessionActive && clock.nowMillis() - since >= backgroundTimeoutMs) _state.value = LockState.Locked
    }

    fun onUserInteraction() {
        val now = clock.nowMillis()
        if (isSessionActive && _state.value == LockState.Unlocked && now - lastInteractionAt >= idleTimeoutMs) {
            _state.value = LockState.Locked
        }
        lastInteractionAt = now
    }

    /** Polled by the UI every few seconds so an idle screen locks even without a touch. */
    fun checkIdle() {
        if (isSessionActive && _state.value == LockState.Unlocked && clock.nowMillis() - lastInteractionAt >= idleTimeoutMs) {
            _state.value = LockState.Locked
        }
    }

    fun onUnlocked() {
        lastInteractionAt = clock.nowMillis()
        _state.value = LockState.Unlocked
    }
}

/** Connects [AppLockManager] to the whole-app foreground/background lifecycle (ProcessLifecycleOwner). */
class AppLockLifecycleObserver(private val manager: AppLockManager) : DefaultLifecycleObserver {
    override fun onStop(owner: LifecycleOwner) = manager.onBackgrounded()
    override fun onStart(owner: LifecycleOwner) = manager.onForegrounded()
}

sealed interface UnlockResult {
    data object Success : UnlockResult
    data object Cancelled : UnlockResult
    data class Error(val message: String) : UnlockResult
}

/** Shows the Android system unlock prompt: fingerprint/face, or the phone's PIN/pattern/password. No ASL PIN. */
object SystemUnlock {

    fun isDeviceSecure(context: Context): Boolean =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure

    fun prompt(activity: FragmentActivity, title: String, subtitle: String, onResult: (UnlockResult) -> Unit) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(UnlockResult.Success)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                onResult(if (cancelled) UnlockResult.Cancelled else UnlockResult.Error(errString.toString()))
            }
            // onAuthenticationFailed = one bad finger; the system prompt stays open, nothing to do.
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setConfirmationRequired(false)
            .apply {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                    else -> @Suppress("DEPRECATION") setDeviceCredentialAllowed(true)
                }
            }
            .build()
        prompt.authenticate(info)
    }
}
