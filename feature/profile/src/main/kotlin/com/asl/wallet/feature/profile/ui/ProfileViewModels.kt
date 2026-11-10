package com.asl.wallet.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asl.wallet.core.common.session.CurrentUser
import com.asl.wallet.core.common.validation.PasswordRules
import com.asl.wallet.core.common.validation.PasswordStrength
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.network.UiError
import com.asl.wallet.core.network.toUiError
import com.asl.wallet.feature.profile.data.ProfileRepository
import com.asl.wallet.feature.profile.data.SupportRepository
import com.asl.wallet.feature.profile.domain.ActiveService
import com.asl.wallet.feature.profile.domain.Commission
import com.asl.wallet.feature.profile.domain.Complaint
import com.asl.wallet.feature.profile.domain.ComplaintDraft
import com.asl.wallet.feature.profile.domain.FeedbackDraft
import com.asl.wallet.feature.profile.domain.ProfileDetails
import com.asl.wallet.feature.profile.domain.SupportResult
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

/** Loading / loaded / failed, for screens that just show data. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val data: T) : Loadable<T>
    data class Failed(val error: UiError) : Loadable<Nothing>
}

private fun <T> NetworkResult<T>.toLoadable(): Loadable<T> = when (this) {
    is NetworkResult.Success -> Loadable.Ready(data)
    is NetworkResult.Failure -> Loadable.Failed(toUiError())
}

// ---------------------------------------------------------------- Profile tab + details

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repo: ProfileRepository,
    private val currentUser: CurrentUser,
) : ViewModel() {
    private val _state = MutableStateFlow<Loadable<ProfileDetails>>(Loadable.Loading)
    val state: StateFlow<Loadable<ProfileDetails>> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = Loadable.Loading
        viewModelScope.launch { _state.value = repo.profile().toLoadable() }
    }

    fun logout() { viewModelScope.launch { currentUser.logout() } }
}

// ---------------------------------------------------------------- My commission

@HiltViewModel
class CommissionViewModel @Inject constructor(private val repo: ProfileRepository) : ViewModel() {
    private val _state = MutableStateFlow<Loadable<Commission>>(Loadable.Loading)
    val state: StateFlow<Loadable<Commission>> = _state.asStateFlow()
    private val _tab = MutableStateFlow(0)
    val tab: StateFlow<Int> = _tab.asStateFlow()

    init { load() }
    fun load() {
        _state.value = Loadable.Loading
        viewModelScope.launch { _state.value = repo.commission().toLoadable() }
    }
    fun selectTab(i: Int) { _tab.value = i.coerceIn(0, 1) }
}

// ---------------------------------------------------------------- Active services

@HiltViewModel
class ActiveServicesViewModel @Inject constructor(private val repo: ProfileRepository) : ViewModel() {
    private val _state = MutableStateFlow<Loadable<List<com.asl.wallet.feature.profile.domain.ServiceEntry>>>(Loadable.Loading)
    val state: StateFlow<Loadable<List<com.asl.wallet.feature.profile.domain.ServiceEntry>>> = _state.asStateFlow()

    init { load() }
    fun load() {
        _state.value = Loadable.Loading
        viewModelScope.launch {
            _state.value = repo.activeServices().toLoadable().let { l ->
                if (l is Loadable.Ready) Loadable.Ready(com.asl.wallet.feature.profile.domain.ServiceStatus.entries(l.data)) else l as Loadable<Nothing>
            }
        }
    }
}

// ---------------------------------------------------------------- Change password

enum class ChangeStep { Form, Otp, Done }

data class ChangePasswordState(
    val step: ChangeStep = ChangeStep.Form,
    val current: String = "",
    val new: String = "",
    val confirm: String = "",
    val otp: String = "",
    val sentTo: String = "",
    val loading: Boolean = false,
    val error: UiError? = null,
    val resendIn: Int = 0,
    val triesLeft: Int = ChangePasswordViewModel.OTP_TRIES,
) {
    val strength: PasswordStrength get() = PasswordRules.strength(new)
    val checks get() = PasswordRules.checks(new)
    val newOk: Boolean get() = strength == PasswordStrength.Fair || strength == PasswordStrength.Strong
    val sameAsCurrent: Boolean get() = new.isNotEmpty() && new == current
    val confirmMismatch: Boolean get() = confirm.isNotEmpty() && confirm != new
    val canRequest: Boolean get() = current.isNotEmpty() && newOk && !sameAsCurrent && confirm == new && !loading
    val canVerify: Boolean get() = otp.length == ChangePasswordViewModel.OTP_LENGTH && triesLeft > 0 && !loading
}

sealed interface ChangePasswordEvent { data object Changed : ChangePasswordEvent }

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(private val repo: ProfileRepository) : ViewModel() {
    private val _state = MutableStateFlow(ChangePasswordState())
    val state: StateFlow<ChangePasswordState> = _state.asStateFlow()
    private val _events = Channel<ChangePasswordEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var timer: Job? = null

    fun onCurrent(v: String) = _state.update { it.copy(current = v.take(MAX_LEN), error = null) }
    fun onNew(v: String) = _state.update { it.copy(new = v.take(MAX_LEN), error = null) }
    fun onConfirm(v: String) = _state.update { it.copy(confirm = v.take(MAX_LEN), error = null) }
    fun onOtp(v: String) = _state.update { it.copy(otp = v.filter(Char::isDigit).take(OTP_LENGTH), error = null) }

    fun requestOtp() {
        val s = _state.value
        if (!s.canRequest && s.step == ChangeStep.Form) return
        if (s.step == ChangeStep.Otp && s.resendIn > 0) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.requestPasswordChange(s.current, s.new)) {
                is NetworkResult.Success -> {
                    _state.update { it.copy(loading = false, step = ChangeStep.Otp, sentTo = r.data.sentTo, otp = "", triesLeft = OTP_TRIES) }
                    startTimer()
                }
                is NetworkResult.Failure -> _state.update { it.copy(loading = false, error = r.toUiError()) }
            }
        }
    }

    fun verify() {
        val s = _state.value
        if (!s.canVerify) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.confirmPasswordChange(s.otp)) {
                is NetworkResult.Success -> {
                    // Passwords leave memory as soon as they are no longer needed.
                    _state.value = ChangePasswordState(step = ChangeStep.Done)
                    _events.trySend(ChangePasswordEvent.Changed)
                }
                is NetworkResult.Failure -> _state.update { it.copy(loading = false, otp = "", triesLeft = it.triesLeft - 1, error = r.toUiError()) }
            }
        }
    }

    fun backToForm() { timer?.cancel(); _state.update { it.copy(step = ChangeStep.Form, otp = "", error = null) } }

    private fun startTimer() {
        timer?.cancel()
        timer = viewModelScope.launch {
            for (sec in RESEND_SECONDS downTo 0) { _state.update { it.copy(resendIn = sec) }; if (sec > 0) delay(1_000) }
        }
    }

    override fun onCleared() { _state.value = ChangePasswordState() }

    companion object {
        /** The backend sends a 6-digit code for change-password (verified in asl-backend, 4 Nov 2026). */
        const val OTP_LENGTH = 6
        const val OTP_TRIES = 3
        const val RESEND_SECONDS = 60
        const val MAX_LEN = 64
    }
}

// ---------------------------------------------------------------- Complaints and feedback (API pending)

data class ComplaintFormState(
    val service: String = "",
    val transactionId: String = "",
    val description: String = "",
    val attachmentName: String? = null,
    val sending: Boolean = false,
    /** Shown until the merchant API exists; the draft is kept. */
    val apiNotReady: Boolean = false,
    val error: String? = null,
    val raisedId: String? = null,
) {
    val draft get() = ComplaintDraft(service, transactionId.trim().ifEmpty { null }, description)
    val canSubmit: Boolean get() = draft.isValid && !sending
}

@HiltViewModel
class ComplaintViewModel @Inject constructor(private val repo: SupportRepository) : ViewModel() {
    private val _form = MutableStateFlow(ComplaintFormState())
    val form: StateFlow<ComplaintFormState> = _form.asStateFlow()
    private val _list = MutableStateFlow<SupportResult<List<Complaint>>?>(null)
    val list: StateFlow<SupportResult<List<Complaint>>?> = _list.asStateFlow()
    private var attachment: ByteArray? = null

    fun onService(v: String) = _form.update { it.copy(service = v) }
    fun onTransaction(v: String) = _form.update { it.copy(transactionId = v.filter { c -> c.isLetterOrDigit() || c == '-' }.take(40)) }
    fun onDescription(v: String) = _form.update { it.copy(description = v.take(ComplaintDraft.MAX_TEXT)) }
    fun onAttachment(name: String?, bytes: ByteArray?) {
        attachment = bytes?.takeIf { it.size <= MAX_ATTACHMENT }
        _form.update { it.copy(attachmentName = if (attachment != null) name else null) }
    }

    fun submit() {
        val s = _form.value
        if (!s.canSubmit) return
        _form.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.raiseComplaint(s.draft.copy(attachment = attachment))) {
                is SupportResult.Ok -> { attachment = null; _form.value = ComplaintFormState(raisedId = r.data) }
                SupportResult.ApiNotReady -> _form.update { it.copy(sending = false, apiNotReady = true) }
                is SupportResult.Error -> _form.update { it.copy(sending = false, error = r.message) }
            }
        }
    }

    fun dismissApiNotReady() = _form.update { it.copy(apiNotReady = false) }

    fun loadList() { viewModelScope.launch { _list.value = repo.myComplaints() } }

    companion object { const val MAX_ATTACHMENT = 2 * 1024 * 1024 }
}

data class FeedbackState(
    val stars: Int = 0,
    val tags: Set<String> = emptySet(),
    val note: String = "",
    val sending: Boolean = false,
    val apiNotReady: Boolean = false,
    val sent: Boolean = false,
) { val canSend: Boolean get() = stars in 1..5 && !sending }

@HiltViewModel
class FeedbackViewModel @Inject constructor(private val repo: SupportRepository) : ViewModel() {
    private val _state = MutableStateFlow(FeedbackState())
    val state: StateFlow<FeedbackState> = _state.asStateFlow()

    fun onStars(n: Int) = _state.update { it.copy(stars = n.coerceIn(1, 5)) }
    fun toggleTag(t: String) = _state.update { it.copy(tags = if (t in it.tags) it.tags - t else it.tags + t) }
    fun onNote(v: String) = _state.update { it.copy(note = v.take(300)) }
    fun dismissApiNotReady() = _state.update { it.copy(apiNotReady = false) }

    fun send() {
        val s = _state.value
        if (!s.canSend) return
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            when (repo.sendFeedback(FeedbackDraft(s.stars, s.tags, s.note.trim()))) {
                is SupportResult.Ok -> _state.value = FeedbackState(sent = true)
                SupportResult.ApiNotReady -> _state.update { it.copy(sending = false, apiNotReady = true) }
                is SupportResult.Error -> _state.update { it.copy(sending = false) }
            }
        }
    }
}
