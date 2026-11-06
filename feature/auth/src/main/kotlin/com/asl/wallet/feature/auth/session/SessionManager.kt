package com.asl.wallet.feature.auth.session

import com.asl.wallet.core.common.dispatch.ApplicationScope
import com.asl.wallet.core.common.session.CurrentUser
import com.asl.wallet.core.common.session.UserSummary
import com.asl.wallet.core.common.session.SignOutCleanup
import com.asl.wallet.core.common.session.SignOutReason
import com.asl.wallet.core.common.session.TokenStore
import com.asl.wallet.core.network.interceptor.SessionEvents
import com.asl.wallet.core.security.lock.AppLockManager
import com.asl.wallet.feature.auth.data.AuthLocalStore
import com.asl.wallet.feature.auth.data.AuthRepository
import com.asl.wallet.feature.auth.domain.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionState {
    data object Unknown : SessionState
    data class SignedOut(val reason: SignOutReason? = null) : SessionState
    data class SignedIn(val user: User) : SessionState
}

/**
 * Single owner of "is someone signed in". Starts/stops the app lock with the session and wipes local
 * data on every sign-out, whatever the cause (user, expired refresh token, revoked elsewhere).
 */
@Singleton
class SessionManager @Inject constructor(
    private val tokenStore: TokenStore,
    private val local: AuthLocalStore,
    private val repository: AuthRepository,
    private val appLock: AppLockManager,
    private val cleanups: Set<@JvmSuppressWildcards SignOutCleanup>,
    sessionEvents: SessionEvents,
    @ApplicationScope private val scope: CoroutineScope,
) : CurrentUser {
    private val _state = MutableStateFlow<SessionState>(SessionState.Unknown)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    override val user: StateFlow<UserSummary?> = _state
        .map { (it as? SessionState.SignedIn)?.user?.let { u -> UserSummary(u.name, u.shopName, u.regno, u.userType, u.kycDone) } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch { sessionEvents.signOut.collect { reason -> endSession(reason) } }
    }

    /** Called by Splash. A saved session opens locked, so the phone lock is asked first. */
    suspend fun restore(): SessionState {
        val tokens = tokenStore.read()
        val user = local.cachedUser()
        val s = if (tokens != null && user != null) {
            appLock.isSessionActive = true
            appLock.lockNow()
            SessionState.SignedIn(user)
        } else {
            if (tokens != null) tokenStore.clear() // half-saved session: start clean
            SessionState.SignedOut()
        }
        _state.value = s
        return s
    }

    fun onSignedIn(user: User) {
        appLock.isSessionActive = true
        appLock.onUnlocked()
        _state.value = SessionState.SignedIn(user)
    }

    override suspend fun logout() {
        repository.logout()
        endSession(SignOutReason.UserRequested)
    }

    /** Reason is consumed once (e.g. "Session expired" dialog shown); later states have no reason. */
    fun consumeReason() {
        val s = _state.value
        if (s is SessionState.SignedOut && s.reason != null) _state.value = SessionState.SignedOut()
    }

    private suspend fun endSession(reason: SignOutReason) {
        tokenStore.clear()
        local.clearSession()
        repository.clearPending()
        cleanups.forEach { runCatching { it.clear() } }
        appLock.isSessionActive = false
        _state.value = SessionState.SignedOut(reason)
    }
}
