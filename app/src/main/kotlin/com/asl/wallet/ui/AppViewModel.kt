package com.asl.wallet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asl.wallet.BuildConfig
import com.asl.wallet.core.designsystem.theme.ThemeMode
import com.asl.wallet.core.network.api.HealthApi
import com.asl.wallet.core.security.integrity.DeviceIntegrityChecker
import com.asl.wallet.core.security.integrity.IntegrityIssue
import com.asl.wallet.core.security.lock.AppLockManager
import com.asl.wallet.core.security.lock.LockState
import com.asl.wallet.data.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.asl.wallet.core.common.session.CurrentUser
import com.asl.wallet.feature.profile.data.SupportContacts
import javax.inject.Inject

data class AppUiState(
    val integrityIssues: Set<IntegrityIssue> = emptySet(),
    val integrityBlocked: Boolean = false,
    val connection: ConnectionResult? = null,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val appLock: AppLockManager,
    private val authLocal: com.asl.wallet.feature.auth.data.AuthLocalStore,
    private val healthApi: HealthApi,
    integrityChecker: DeviceIntegrityChecker,
    /** Support line for Help, receipts and AEPS "pending". */
    val support: SupportContacts,
    /** Signed-in retailer (name, shop, reg no) for receipt headers. */
    val currentUser: CurrentUser,
    /** Loaded once after sign-in so receipts can show the retailer's address. */
    private val profileRepository: com.asl.wallet.feature.profile.data.ProfileRepository,
    /** UAT-only demos (empty in release and Live builds). */
    val demoToggles: Set<@JvmSuppressWildcards com.asl.wallet.core.common.demo.DemoToggle>,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = prefs.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.Light)
    val lockState: StateFlow<LockState> = appLock.state

    private val _ui = MutableStateFlow(AppUiState())
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()

    init {
        val report = integrityChecker.check(isReleaseBuild = !BuildConfig.DEBUG)
        // Root / tampered / test-keys always block. Emulators are allowed when the flavor opts in (UAT) or on any
        // debug build (so Live can be tested on an emulator); release builds still block emulators.
        val allowEmulator = BuildConfig.ALLOW_EMULATOR || BuildConfig.DEBUG
        _ui.update { it.copy(integrityIssues = report.issues, integrityBlocked = report.blocksPayments(allowEmulator)) }

        // On each new sign-in, load the profile once and fill in the retailer's address for receipts.
        viewModelScope.launch {
            currentUser.user
                .map { it?.regno?.takeIf { r -> r != 0L } }
                .distinctUntilChanged()
                .collect { regno ->
                    if (regno == null) return@collect
                    val r = profileRepository.profile()
                    if (r is com.asl.wallet.core.network.NetworkResult.Success) {
                        val d = r.data
                        val addr = listOf(d.address, d.city, d.pincode).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
                        if (addr.isNotEmpty()) currentUser.updateAddress(addr)
                    }
                }
        }
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(mode) }

    val receiptSound: StateFlow<com.asl.wallet.core.designsystem.component.ReceiptSound> =
        prefs.receiptSound.stateIn(viewModelScope, SharingStarted.Eagerly, com.asl.wallet.core.designsystem.component.ReceiptSound())

    fun setReceiptSound(s: com.asl.wallet.core.designsystem.component.ReceiptSound) = viewModelScope.launch { prefs.setReceiptSound(s) }

    /** UAT only: lets testers continue on an emulator/rooted test phone. Live builds never offer this. */
    fun bypassIntegrity() {
        if (BuildConfig.ALLOW_INTEGRITY_BYPASS) _ui.update { it.copy(integrityBlocked = false) }
    }

    fun lockNow() = appLock.lockNow()
    fun onUnlocked() = appLock.onUnlocked()
    fun checkIdle() = appLock.checkIdle()

    /** App lock is opt-in. This is the saved choice, shown as the Settings toggle. */
    private val _appLockEnabled = MutableStateFlow(false)
    val appLockEnabled: StateFlow<Boolean> = _appLockEnabled.asStateFlow()

    init { viewModelScope.launch { _appLockEnabled.value = authLocal.isAppLockEnabled() } }

    /** Turn app lock on or off from Settings: saves the choice and arms/disarms the lock immediately. */
    fun setAppLock(enabled: Boolean) = viewModelScope.launch {
        authLocal.setAppLockEnabled(enabled)
        authLocal.setAppLockSetupDone() // the user has made a choice; don't offer again
        appLock.enabled = enabled
        _appLockEnabled.value = enabled
    }

    fun checkConnection() {
        _ui.update { it.copy(connection = ConnectionResult.Checking) }
        viewModelScope.launch {
            val start = System.currentTimeMillis()
            val result = try {
                val res = healthApi.ping()
                ConnectionResult.from(res.code(), null, System.currentTimeMillis() - start)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ConnectionResult.from(null, e, System.currentTimeMillis() - start)
            }
            _ui.update { it.copy(connection = result) }
        }
    }
}
