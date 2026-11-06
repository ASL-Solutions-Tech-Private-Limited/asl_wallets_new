package com.asl.wallet.core.security.integrity

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Debug
import java.io.File

enum class IntegrityIssue { Rooted, TestKeysBuild, Emulator, DebuggerAttached, AppDebuggable }

data class IntegrityReport(val issues: Set<IntegrityIssue>) {
    /** Money services are blocked on rooted or tampered phones. Emulators are blocked on live builds only. */
    fun blocksPayments(allowEmulator: Boolean): Boolean =
        issues.any { it == IntegrityIssue.Rooted || it == IntegrityIssue.TestKeysBuild || it == IntegrityIssue.DebuggerAttached } ||
            (!allowEmulator && IntegrityIssue.Emulator in issues)
}

/** What the checker reads from the device. Abstracted so the checks are unit-testable. */
interface DeviceEnvironment {
    fun fileExists(path: String): Boolean
    val buildTags: String?
    val fingerprint: String
    val hardware: String
    val model: String
    val product: String
    val manufacturer: String
    val isDebuggerConnected: Boolean
    val isAppDebuggable: Boolean
}

class AndroidDeviceEnvironment(private val context: Context) : DeviceEnvironment {
    override fun fileExists(path: String) = runCatching { File(path).exists() }.getOrDefault(false)
    override val buildTags: String? = Build.TAGS
    override val fingerprint: String = Build.FINGERPRINT.orEmpty()
    override val hardware: String = Build.HARDWARE.orEmpty()
    override val model: String = Build.MODEL.orEmpty()
    override val product: String = Build.PRODUCT.orEmpty()
    override val manufacturer: String = Build.MANUFACTURER.orEmpty()
    override val isDebuggerConnected: Boolean get() = Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    override val isAppDebuggable: Boolean get() = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}

/**
 * Local heuristics only. These raise the bar but can be bypassed by a determined attacker,
 * so from Module 1 the server also checks a Play Integrity verdict before allowing money actions.
 */
class DeviceIntegrityChecker(private val env: DeviceEnvironment) {

    fun check(isReleaseBuild: Boolean): IntegrityReport {
        val issues = mutableSetOf<IntegrityIssue>()
        if (SU_PATHS.any(env::fileExists)) issues += IntegrityIssue.Rooted
        if (env.buildTags?.contains("test-keys") == true) issues += IntegrityIssue.TestKeysBuild
        if (looksLikeEmulator()) issues += IntegrityIssue.Emulator
        if (isReleaseBuild && env.isDebuggerConnected) issues += IntegrityIssue.DebuggerAttached
        if (isReleaseBuild && env.isAppDebuggable) issues += IntegrityIssue.AppDebuggable
        return IntegrityReport(issues)
    }

    private fun looksLikeEmulator(): Boolean =
        env.fingerprint.startsWith("generic") || env.fingerprint.contains("emulator") ||
            env.hardware.contains("goldfish") || env.hardware.contains("ranchu") ||
            env.model.contains("Android SDK built for") || env.model.contains("Emulator") ||
            env.product.contains("sdk_gphone") || env.product.contains("emulator") ||
            env.manufacturer.contains("Genymotion")

    companion object {
        val SU_PATHS = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/su", "/system/bin/.ext/su",
            "/system/usr/we-need-root/su", "/data/local/su", "/data/local/bin/su", "/data/local/xbin/su",
            "/system/app/Superuser.apk", "/system/app/SuperSU.apk", "/cache/su", "/data/adb/magisk",
            "/sbin/.magisk", "/data/adb/ksu",
        )
    }
}
