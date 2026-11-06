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
    private val healthApi: HealthApi,
    integrityChecker: DeviceIntegrityChecker,
    /** Support line for Help, receipts and AEPS "pending". */
    val support: SupportContacts,
    /** Signed-in retailer (name, shop, reg no) for receipt headers. */
    val currentUser: CurrentUser,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = prefs.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.Light)
    val lockState: StateFlow<LockState> = appLock.state

    private val _ui = MutableStateFlow(AppUiState())
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()

    init {
        val report = integrityChecker.check(isReleaseBuild = !BuildConfig.DEBUG)
        _ui.update { it.copy(integrityIssues = report.issues, integrityBlocked = report.blocksPayments(BuildConfig.ALLOW_EMULATOR)) }
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(mode) }

    /** UAT only: lets testers continue on an emulator/rooted test phone. Live builds never offer this. */
    fun bypassIntegrity() {
        if (BuildConfig.ALLOW_INTEGRITY_BYPASS) _ui.update { it.copy(integrityBlocked = false) }
    }

    fun lockNow() = appLock.lockNow()
    fun onUnlocked() = appLock.onUnlocked()
    fun checkIdle() = appLock.checkIdle()

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
