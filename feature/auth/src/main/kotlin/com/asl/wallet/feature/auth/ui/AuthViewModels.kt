package com.asl.wallet.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asl.wallet.core.common.validation.PasswordRules
import com.asl.wallet.core.common.validation.PasswordStrength
import com.asl.wallet.core.common.validation.Validators
import com.asl.wallet.feature.auth.data.AuthLocalStore
import com.asl.wallet.feature.auth.data.AuthRepository
import com.asl.wallet.feature.auth.domain.ActionResult
import com.asl.wallet.feature.auth.domain.AuthError
import com.asl.wallet.feature.auth.domain.LoginId
import com.asl.wallet.feature.auth.domain.LoginOutcome
import com.asl.wallet.feature.auth.session.SessionManager
import com.asl.wallet.feature.auth.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

const val OTP_LENGTH = 4
const val RESEND_SECONDS = 60
const val OTP_TRIES = 3

/* ------------------------------------------------ Splash ------------------------------------------------ */

enum class StartDestination { Onboarding, Language, Login, Home }

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val session: SessionManager,
    private val local: AuthLocalStore,
) : ViewModel() {
    private val _destination = MutableStateFlow<StartDestination?>(null)
    val destination: StateFlow<StartDestination?> = _destination.asStateFlow()

    init {
        viewModelScope.launch {
            // Resolve where to land. There is no logo splash any more: first-time (signed-out) users
            // see the animated onboarding; returning or signed-in users go straight through.
            val state = session.restore()
            _destination.value = when {
                state is SessionState.SignedIn -> StartDestination.Home
                !local.isOnboardingSeen() -> StartDestination.Onboarding
                !local.isLanguageChosen() -> StartDestination.Language
                else -> StartDestination.Login
            }
        }
    }
}

@HiltViewModel
class LanguageViewModel @Inject constructor(private val local: AuthLocalStore) : ViewModel() {
    fun done(onDone: () -> Unit) = viewModelScope.launch { local.setLanguageChosen(); onDone() }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val local: AuthLocalStore) : ViewModel() {
    /** Mark onboarding seen so it never shows again, then continue. */
    fun finish(onDone: () -> Unit) = viewModelScope.launch { local.setOnboardingSeen(); onDone() }
}

/* ------------------------------------------------ Login ------------------------------------------------- */

data class LoginUiState(
    val loginId: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: AuthError? = null,
    val showIdError: Boolean = false,
    val lockedMessage: String? = null,
) {
    val parsedId: LoginId? get() = LoginId.parse(loginId)
    val canSubmit: Boolean get() = parsedId != null && password.isNotEmpty() && !loading
}

sealed interface AuthEvent {
    data object GoToOtp : AuthEvent
    data class SignedIn(val needsLockSetup: Boolean) : AuthEvent
    data object OtpExpired : AuthEvent
    data object PasswordReset : AuthEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val session: SessionManager,
    private val local: AuthLocalStore,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()
    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            local.lastLoginId()?.let { id -> if (_state.value.loginId.isEmpty()) _state.update { it.copy(loginId = id) } }
        }
    }

    fun onLoginIdChange(v: String) = _state.update { it.copy(loginId = v.take(64), error = null, showIdError = false) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v.take(64), error = null) }
    fun onLoginIdDone() = _state.update { it.copy(showIdError = it.loginId.isNotBlank() && it.parsedId == null) }
    fun dismissLocked() = _state.update { it.copy(lockedMessage = null) }

    fun submit() {
        val s = _state.value
        val id = s.parsedId
        if (id == null) { _state.update { it.copy(showIdError = true) }; return }
        if (!s.canSubmit) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.login(id, s.password)) {
                is LoginOutcome.SignedIn -> {
                    session.onSignedIn(r.user)
                    _state.update { it.copy(loading = false, password = "") }
                    _events.send(AuthEvent.SignedIn(needsLockSetup = !local.isAppLockSetupDone()))
                }
                LoginOutcome.OtpRequired -> {
                    _state.update { it.copy(loading = false) }
                    _events.send(AuthEvent.GoToOtp)
                }
                is LoginOutcome.Locked -> _state.update { it.copy(loading = false, password = "", lockedMessage = r.message) }
                is LoginOutcome.Failed -> _state.update { it.copy(loading = false, password = "", error = r.error) }
            }
        }
    }
}

/* ------------------------------------------------- OTP -------------------------------------------------- */

data class OtpUiState(
    val otp: String = "",
    val loading: Boolean = false,
    val error: AuthError? = null,
    val triesLeft: Int = OTP_TRIES,
    val coolDown: Int = 0,
    val resendIn: Int = RESEND_SECONDS,
    val resent: Boolean = false,
)

@HiltViewModel
class OtpViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val session: SessionManager,
    private val local: AuthLocalStore,
) : ViewModel() {
    private val _state = MutableStateFlow(OtpUiState())
    val state: StateFlow<OtpUiState> = _state.asStateFlow()
    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var resendJob: Job? = null

    init {
        if (!repository.hasPendingOtp()) viewModelScope.launch { _events.send(AuthEvent.OtpExpired) }
        startResendTimer()
    }

    fun onOtpChange(v: String) {
        val digits = v.filter(Char::isDigit).take(OTP_LENGTH)
        _state.update { it.copy(otp = digits, error = null) }
        if (digits.length == OTP_LENGTH) verify()
    }

    fun verify() {
        val s = _state.value
        if (s.loading || s.coolDown > 0 || !Validators.isOtp(s.otp, OTP_LENGTH)) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.verifyOtp(s.otp)) {
                is LoginOutcome.SignedIn -> {
                    session.onSignedIn(r.user)
                    _state.update { it.copy(loading = false) }
                    _events.send(AuthEvent.SignedIn(needsLockSetup = !local.isAppLockSetupDone()))
                }
                is LoginOutcome.Failed -> {
                    if (r.error == AuthError.OtpSessionExpired) { _events.send(AuthEvent.OtpExpired); return@launch }
                    val left = s.triesLeft - 1
                    _state.update { it.copy(loading = false, otp = "", error = r.error, triesLeft = left) }
                    if (left <= 0) startCoolDown()
                }
                is LoginOutcome.Locked -> _state.update { it.copy(loading = false, otp = "", error = AuthError.Server(r.message)) }
                LoginOutcome.OtpRequired -> _state.update { it.copy(loading = false, error = AuthError.Unknown) }
            }
        }
    }

    fun resend() {
        if (_state.value.resendIn > 0 || _state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.resendOtp()) {
                ActionResult.Ok -> { _state.update { it.copy(loading = false, resent = true, otp = "") }; startResendTimer() }
                is ActionResult.Failed -> {
                    if (r.error == AuthError.OtpSessionExpired) _events.send(AuthEvent.OtpExpired)
                    _state.update { it.copy(loading = false, error = r.error) }
                }
            }
        }
    }

    /** Leaving the OTP screen drops the in-memory password. */
    fun cancel() = repository.clearPending()

    private fun startResendTimer() {
        resendJob?.cancel()
        resendJob = viewModelScope.launch {
            for (s in RESEND_SECONDS downTo 0) { _state.update { it.copy(resendIn = s) }; if (s > 0) delay(1_000) }
        }
    }

    private fun startCoolDown() = viewModelScope.launch {
        for (s in 60 downTo 0) { _state.update { it.copy(coolDown = s) }; if (s > 0) delay(1_000) }
        _state.update { it.copy(triesLeft = OTP_TRIES, error = null) }
    }
}

/* ------------------------------------------- Forgot password -------------------------------------------- */

enum class ForgotStep { Mobile, Reset }

data class ForgotUiState(
    val step: ForgotStep = ForgotStep.Mobile,
    val mobile: String = "",
    val otp: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val loading: Boolean = false,
    val error: AuthError? = null,
    val resendIn: Int = 0,
) {
    val mobileValid get() = Validators.isMobile(mobile)
    val strength get() = PasswordRules.strength(newPassword)
    val passwordsMatch get() = newPassword.isNotEmpty() && newPassword == confirmPassword
    val passwordOk get() = strength == PasswordStrength.Fair || strength == PasswordStrength.Strong
    val canSave get() = Validators.isOtp(otp, OTP_LENGTH) && passwordOk && passwordsMatch && !loading
}

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val repository: AuthRepository,
    local: AuthLocalStore,
) : ViewModel() {
    private val _state = MutableStateFlow(ForgotUiState())
    val state: StateFlow<ForgotUiState> = _state.asStateFlow()
    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var timer: Job? = null

    init {
        viewModelScope.launch {
            val last = local.lastLoginId()
            if (last != null && Validators.isMobile(last) && _state.value.mobile.isEmpty()) _state.update { it.copy(mobile = last) }
        }
    }

    fun prefill(mobile: String?) { if (mobile != null && Validators.isMobile(mobile)) _state.update { it.copy(mobile = mobile) } }
    fun onMobile(v: String) = _state.update { it.copy(mobile = v.filter(Char::isDigit).take(10), error = null) }
    fun onOtp(v: String) = _state.update { it.copy(otp = v.filter(Char::isDigit).take(OTP_LENGTH), error = null) }
    fun onNewPassword(v: String) = _state.update { it.copy(newPassword = v.take(64), error = null) }
    fun onConfirm(v: String) = _state.update { it.copy(confirmPassword = v.take(64), error = null) }
    fun backToMobile() = _state.update { it.copy(step = ForgotStep.Mobile, otp = "", error = null) }

    fun sendCode() {
        val s = _state.value
        if (!s.mobileValid || s.loading || s.resendIn > 0) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.forgotPassword(s.mobile)) {
                ActionResult.Ok -> { _state.update { it.copy(loading = false, step = ForgotStep.Reset) }; startTimer() }
                is ActionResult.Failed -> _state.update { it.copy(loading = false, error = r.error) }
            }
        }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.resetPassword(s.mobile, s.otp, s.newPassword)) {
                ActionResult.Ok -> {
                    _state.update { it.copy(loading = false, newPassword = "", confirmPassword = "", otp = "") }
                    _events.send(AuthEvent.PasswordReset)
                }
                is ActionResult.Failed -> _state.update { it.copy(loading = false, error = r.error, otp = "") }
            }
        }
    }

    private fun startTimer() {
        timer?.cancel()
        timer = viewModelScope.launch {
            for (s in RESEND_SECONDS downTo 0) { _state.update { it.copy(resendIn = s) }; if (s > 0) delay(1_000) }
        }
    }
}

/* -------------------------------------------- App lock setup -------------------------------------------- */

@HiltViewModel
class AppLockSetupViewModel @Inject constructor(private val local: AuthLocalStore) : ViewModel() {
    fun done(onDone: () -> Unit) = viewModelScope.launch { local.setAppLockSetupDone(); onDone() }
}

/* ---------------------------------------------- Home / session ------------------------------------------ */

@HiltViewModel
class SessionViewModel @Inject constructor(private val session: SessionManager) : ViewModel() {
    val state = session.state
    fun logout() = viewModelScope.launch { session.logout() }
    fun consumeReason() = session.consumeReason()
}
