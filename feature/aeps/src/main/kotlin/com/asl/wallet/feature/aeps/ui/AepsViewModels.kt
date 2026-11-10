package com.asl.wallet.feature.aeps.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asl.wallet.core.common.format.Money
import com.asl.wallet.core.common.session.IdempotencyKey
import com.asl.wallet.core.common.validation.Validators
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.network.UiError
import com.asl.wallet.core.network.toUiError
import com.asl.wallet.feature.aeps.data.AepsPrefs
import com.asl.wallet.feature.aeps.data.AepsRepository
import com.asl.wallet.feature.aeps.data.AepsSession
import com.asl.wallet.feature.aeps.data.LocationSource
import com.asl.wallet.feature.aeps.data.OnboardingPhotos
import com.asl.wallet.feature.aeps.data.OtpRef
import com.asl.wallet.feature.aeps.data.TxnInput
import com.asl.wallet.feature.aeps.data.ValidatedOtp
import com.asl.wallet.feature.aeps.domain.AepsConfig
import com.asl.wallet.feature.aeps.domain.AepsProvider
import com.asl.wallet.feature.aeps.domain.CustomerBank
import com.asl.wallet.feature.aeps.domain.GeoPoint
import com.asl.wallet.feature.aeps.domain.NextStep
import com.asl.wallet.feature.aeps.domain.OnboardingForm
import com.asl.wallet.feature.aeps.domain.Pid
import com.asl.wallet.feature.aeps.domain.RdDevice
import com.asl.wallet.feature.aeps.domain.TxnOutcome
import com.asl.wallet.feature.aeps.domain.TxnType
import com.asl.wallet.feature.aeps.domain.nextStep
import com.asl.wallet.feature.aeps.rd.CaptureResult
import com.asl.wallet.feature.aeps.rd.RdServices
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

/** Where the retailer must go next, or why AEPS can't open. */
sealed interface AepsRoute {
    data class Step(val step: NextStep) : AepsRoute
    data object NeedsLocationPermission : AepsRoute
    data object LocationOff : AepsRoute
}

// ---------------------------------------------------------------- 3.14 bank chooser

enum class BankBadge { Ready, LoginDue, SetupNeeded, Unavailable, Unknown }

data class ChooserState(
    val loading: Boolean = true,
    val banks: List<Pair<AepsProvider, BankBadge>> = emptyList(),
    val last: AepsProvider? = null,
    val error: UiError? = null,
)

@HiltViewModel
class AepsChooserViewModel @Inject constructor(
    private val repo: AepsRepository,
    private val prefs: AepsPrefs,
) : ViewModel() {
    private val _state = MutableStateFlow(ChooserState())
    val state: StateFlow<ChooserState> = _state.asStateFlow()

    fun load() {
        _state.value = ChooserState(loading = true)
        viewModelScope.launch {
            val last = AepsProvider.of(prefs.lastProvider())
            when (val c = repo.config()) {
                is NetworkResult.Failure -> _state.value = ChooserState(loading = false, last = last, error = c.toUiError())
                is NetworkResult.Success -> {
                    val providers = c.data.providers.ifEmpty { AepsProvider.entries.map { com.asl.wallet.feature.aeps.domain.ProviderInfo(it, true) } }
                    // Show the list at once, then fill in each bank's state.
                    _state.value = ChooserState(loading = false, last = last, banks = providers.map { it.provider to if (it.active) BankBadge.Unknown else BankBadge.Unavailable })
                    val badges = providers.map { p ->
                        async {
                            if (!p.active) p.provider to BankBadge.Unavailable
                            else when (val s = repo.status(p.provider)) {
                                is NetworkResult.Success -> p.provider to when (s.data.nextStep(p.provider)) {
                                    NextStep.Ready -> BankBadge.Ready
                                    NextStep.DailyLogin -> BankBadge.LoginDue
                                    else -> BankBadge.SetupNeeded
                                }
                                is NetworkResult.Failure -> p.provider to BankBadge.Unknown
                            }
                        }
                    }.awaitAll()
                    _state.update { it.copy(banks = badges) }
                }
            }
        }
    }

    fun choose(p: AepsProvider) { viewModelScope.launch { prefs.setLastProvider(p.id) } }
}

// ---------------------------------------------------------------- 3.15–3.16 gate: location + status

data class GateState(val checking: Boolean = true, val error: UiError? = null, val route: AepsRoute? = null)

@HiltViewModel
class AepsGateViewModel @Inject constructor(
    private val repo: AepsRepository,
    private val session: AepsSession,
    private val location: LocationSource,
) : ViewModel() {
    private val _state = MutableStateFlow(GateState())
    val state: StateFlow<GateState> = _state.asStateFlow()

    fun start(provider: AepsProvider) {
        session.provider = provider
        _state.value = GateState(checking = true)
        when {
            !location.hasPermission() -> { _state.value = GateState(checking = false, route = AepsRoute.NeedsLocationPermission); return }
            !location.isEnabled() -> { _state.value = GateState(checking = false, route = AepsRoute.LocationOff); return }
        }
        viewModelScope.launch {
            val cfg = repo.config()
            if (cfg is NetworkResult.Failure) { _state.value = GateState(checking = false, error = cfg.toUiError()); return@launch }
            when (val s = repo.status(provider)) {
                is NetworkResult.Success -> { session.status = s.data; _state.value = GateState(checking = false, route = AepsRoute.Step(s.data.nextStep(provider))) }
                is NetworkResult.Failure -> _state.value = GateState(checking = false, error = s.toUiError())
            }
        }
    }

    fun consumeRoute() = _state.update { it.copy(route = null) }
}

// ---------------------------------------------------------------- scanner choice (shared)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val session: AepsSession,
    private val prefs: AepsPrefs,
) : ViewModel() {
    private val _device = MutableStateFlow(session.device)
    val device: StateFlow<RdDevice?> = _device.asStateFlow()
    val devices: List<RdDevice> get() = session.config?.devices.orEmpty()

    init {
        if (session.device == null) viewModelScope.launch {
            val id = prefs.lastDevice()
            // Default to the last one used, else Mantra L1 (the scanner ASL retailers mostly use).
            val d = devices.firstOrNull { it.id == id } ?: devices.firstOrNull { it.name.contains("Mantra", true) } ?: devices.firstOrNull()
            session.device = d; _device.value = d
        }
    }

    fun select(d: RdDevice) { session.device = d; _device.value = d; viewModelScope.launch { prefs.setLastDevice(d.id) } }
}

// ---------------------------------------------------------------- 3.17–3.22 onboarding

enum class OnboardStep { Intro, Details, Documents, Otp, Biometric, Done }
enum class Doc { Aadhaar, Pan, Shop }

data class OnboardingState(
    val step: OnboardStep = OnboardStep.Intro,
    val form: OnboardingForm = OnboardingForm(),
    val showErrors: Boolean = false,
    val photos: Map<Doc, Int> = emptyMap(),
    val otp: String = "",
    val resendIn: Int = 0,
    val busy: Boolean = false,
    val error: String? = null,
    val uiError: UiError? = null,
    val outletId: String? = null,
) {
    val errors get() = if (showErrors) form.errors() else emptySet()
    val allPhotos: Boolean get() = Doc.entries.all { (photos[it] ?: 0) > 0 }
    val canVerifyOtp: Boolean get() = otp.length == 6 && !busy
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repo: AepsRepository,
    private val session: AepsSession,
    private val location: LocationSource,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()
    private val _next = Channel<NextStep>(Channel.BUFFERED)
    val next = _next.receiveAsFlow()
    private val photos = mutableMapOf<Doc, ByteArray>()
    private var otpRef: OtpRef? = null
    private var validated: ValidatedOtp? = null
    private var timer: Job? = null
    val provider: AepsProvider get() = session.provider ?: AepsProvider.Kotak

    init {
        viewModelScope.launch {
            val id = (repo.identity() as? NetworkResult.Success)?.data
            val prefill = session.status?.prefill ?: com.asl.wallet.feature.aeps.domain.AgentPrefill()
            val profile = com.asl.wallet.feature.aeps.domain.ProfilePrefill(
                id?.firstName.orEmpty(), id?.lastName.orEmpty(), id?.email.orEmpty(), id?.mobile.orEmpty(), id?.pan.orEmpty(),
                id?.address.orEmpty(), id?.city.orEmpty(), id?.pincode.orEmpty(), id?.shopName.orEmpty(),
            )
            _state.update { it.copy(form = OnboardingForm.from(prefill, profile).copy(aadhaar = id?.aadhaar.orEmpty())) }
        }
    }

    fun begin() = _state.update { it.copy(step = OnboardStep.Details) }
    fun edit(f: (OnboardingForm) -> OnboardingForm) = _state.update { it.copy(form = f(it.form)) }

    fun detailsDone() {
        val s = _state.value
        if (!s.form.isValid) { _state.update { it.copy(showErrors = true) }; return }
        _state.update { it.copy(step = OnboardStep.Documents, showErrors = false) }
    }

    /** [bytes] are already compressed; only their size is kept in state. */
    fun setPhoto(doc: Doc, bytes: ByteArray?) {
        if (bytes == null) { _state.update { it.copy(error = PHOTO_ERROR) }; return }
        photos[doc] = bytes
        _state.update { it.copy(photos = it.photos + (doc to bytes.size), error = null) }
    }

    fun submitDocuments() {
        val s = _state.value
        if (!s.allPhotos || s.busy) return
        _state.update { it.copy(busy = true, error = null, uiError = null) }
        viewModelScope.launch {
            val at = location.current() ?: run { _state.update { it.copy(busy = false, error = LOCATION_ERROR) }; return@launch }
            val r = repo.onboard(provider, s.form, OnboardingPhotos(photos.getValue(Doc.Aadhaar), photos.getValue(Doc.Pan), photos.getValue(Doc.Shop)), at)
            when (r) {
                is NetworkResult.Success -> {
                    photos.clear() // uploaded: the bytes leave memory
                    otpRef = r.data
                    _state.update { it.copy(busy = false, step = OnboardStep.Otp, outletId = r.data.merchantLoginId) }
                    startTimer()
                }
                is NetworkResult.Failure -> _state.update { it.copy(busy = false, uiError = r.toUiError()) }
            }
        }
    }

    fun onOtp(v: String) = _state.update { it.copy(otp = v.filter(Char::isDigit).take(6), error = null, uiError = null) }

    fun resendOtp() {
        val ref = otpRef ?: return
        if (_state.value.resendIn > 0 || _state.value.busy) return
        viewModelScope.launch {
            val at = location.current() ?: return@launch
            when (val r = repo.resendOnboardingOtp(provider, _state.value.form, ref.merchantLoginId.orEmpty(), at)) {
                is NetworkResult.Success -> { otpRef = r.data.copy(merchantLoginId = ref.merchantLoginId); startTimer() }
                is NetworkResult.Failure -> _state.update { it.copy(uiError = r.toUiError()) }
            }
        }
    }

    fun verifyOtp() {
        val ref = otpRef ?: return
        if (!_state.value.canVerifyOtp) return
        _state.update { it.copy(busy = true, uiError = null) }
        viewModelScope.launch {
            when (val r = repo.validateOnboardingOtp(provider, _state.value.otp, ref)) {
                is NetworkResult.Success -> { validated = r.data; timer?.cancel(); _state.update { it.copy(busy = false, step = OnboardStep.Biometric, otp = "") } }
                is NetworkResult.Failure -> _state.update { it.copy(busy = false, otp = "", uiError = r.toUiError()) }
            }
        }
    }

    /** WADH from the OTP response (eKYC); the scan must use it. */
    val wadh: String? get() = validated?.wadh

    fun onCapture(result: CaptureResult) {
        val v = validated ?: return
        when (result) {
            is CaptureResult.Ok -> {
                _state.update { it.copy(busy = true, error = null, uiError = null) }
                viewModelScope.launch {
                    val at = location.current() ?: run { _state.update { it.copy(busy = false, error = LOCATION_ERROR) }; return@launch }
                    when (val r = repo.onboardingBiometric(provider, _state.value.form.aadhaar, otpRef?.merchantLoginId, v, result.type, result.pid, at)) {
                        is NetworkResult.Success -> _state.update { it.copy(busy = false, step = OnboardStep.Done) }
                        is NetworkResult.Failure -> _state.update { it.copy(busy = false, uiError = r.toUiError()) }
                    }
                }
            }
            is CaptureResult.Failed -> _state.update { it.copy(error = RdServices.explain(result.code, result.info)) }
            CaptureResult.NotInstalled -> _state.update { it.copy(error = NOT_INSTALLED) }
            CaptureResult.Cancelled -> Unit
        }
    }

    /** After success, ask the server what is next (bank verification or daily login). */
    fun continueAfterDone() {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val s = repo.status(provider)) {
                is NetworkResult.Success -> { session.status = s.data; _next.trySend(s.data.nextStep(provider)) }
                is NetworkResult.Failure -> _next.trySend(NextStep.DailyLogin)
            }
            _state.update { it.copy(busy = false) }
        }
    }

    fun back() = _state.update {
        it.copy(step = when (it.step) { OnboardStep.Documents -> OnboardStep.Details; OnboardStep.Details -> OnboardStep.Intro; else -> it.step }, error = null, uiError = null)
    }

    private fun startTimer() {
        timer?.cancel()
        timer = viewModelScope.launch { for (s in RESEND_SECONDS downTo 0) { _state.update { it.copy(resendIn = s) }; if (s > 0) delay(1_000) } }
    }

    override fun onCleared() { photos.clear(); otpRef = null; validated = null }

    companion object {
        const val RESEND_SECONDS = 60
        const val PHOTO_ERROR = "That photo couldn't be used. Please take it again, in good light."
        const val LOCATION_ERROR = "Couldn't get your location. Turn on location and try again."
        const val NOT_INSTALLED = "The scanner app isn't installed. Choose another scanner or install it."
    }
}

// ---------------------------------------------------------------- 3.23–3.25 one-time checks and daily login

enum class CheckKind { BankVerification, BiometricKyc, DailyLogin }
enum class CheckPhase { SendingOtp, Otp, Scan, Done }

data class CheckState(
    val kind: CheckKind = CheckKind.DailyLogin,
    val phase: CheckPhase = CheckPhase.Scan,
    val otp: String = "",
    val resendIn: Int = 0,
    val busy: Boolean = false,
    val error: String? = null,
    val uiError: UiError? = null,
)

@HiltViewModel
class AepsCheckViewModel @Inject constructor(
    private val repo: AepsRepository,
    private val session: AepsSession,
    private val location: LocationSource,
) : ViewModel() {
    private val _state = MutableStateFlow(CheckState())
    val state: StateFlow<CheckState> = _state.asStateFlow()
    private val _next = Channel<NextStep>(Channel.BUFFERED)
    val next = _next.receiveAsFlow()
    private var ref: OtpRef? = null
    private var validated: ValidatedOtp? = null
    private var timer: Job? = null
    private var started = false
    val provider: AepsProvider get() = session.provider ?: AepsProvider.Kotak

    fun start(kind: CheckKind) {
        if (started) return
        started = true
        _state.value = CheckState(kind = kind, phase = if (kind == CheckKind.BankVerification) CheckPhase.SendingOtp else CheckPhase.Scan)
        if (kind == CheckKind.BankVerification) sendBankOtp()
    }

    fun sendBankOtp() {
        if (_state.value.resendIn > 0) return
        _state.update { it.copy(busy = true, uiError = null, error = null) }
        viewModelScope.launch {
            val at = location.current() ?: run { _state.update { it.copy(busy = false, error = OnboardingViewModel.LOCATION_ERROR) }; return@launch }
            when (val r = repo.bankSendOtp(provider, at)) {
                is NetworkResult.Success -> { ref = r.data; _state.update { it.copy(busy = false, phase = CheckPhase.Otp) }; startTimer() }
                is NetworkResult.Failure -> _state.update { it.copy(busy = false, uiError = r.toUiError()) }
            }
        }
    }

    fun onOtp(v: String) = _state.update { it.copy(otp = v.filter(Char::isDigit).take(6), uiError = null) }

    fun verifyOtp() {
        val r0 = ref ?: return
        if (_state.value.otp.length != 6 || _state.value.busy) return
        _state.update { it.copy(busy = true, uiError = null) }
        viewModelScope.launch {
            when (val r = repo.bankValidateOtp(provider, _state.value.otp, r0)) {
                is NetworkResult.Success -> { validated = r.data; timer?.cancel(); _state.update { it.copy(busy = false, phase = CheckPhase.Scan, otp = "") } }
                is NetworkResult.Failure -> _state.update { it.copy(busy = false, otp = "", uiError = r.toUiError()) }
            }
        }
    }

    /** WADH for this scan: eKYC WADH for the bank check, the status WADH for daily login when the bank sends one. */
    val wadh: String? get() = when (_state.value.kind) {
        CheckKind.BankVerification -> validated?.wadh ?: Pid.EKYC_WADH
        CheckKind.BiometricKyc -> session.status?.wadh
        CheckKind.DailyLogin -> session.status?.wadh
    }

    fun onCapture(result: CaptureResult) {
        when (result) {
            is CaptureResult.Ok -> {
                _state.update { it.copy(busy = true, error = null, uiError = null) }
                viewModelScope.launch {
                    val at = location.current() ?: run { _state.update { it.copy(busy = false, error = OnboardingViewModel.LOCATION_ERROR) }; return@launch }
                    val r = when (_state.value.kind) {
                        CheckKind.BankVerification -> repo.bankBiometric(provider, validated ?: return@launch, result.type, result.pid, at)
                        CheckKind.BiometricKyc -> repo.biometricKyc(provider, result.type, result.pid, at)
                        CheckKind.DailyLogin -> repo.dailyLogin(provider, result.type, result.pid, at)
                    }
                    when (r) {
                        is NetworkResult.Success -> {
                            _state.update { it.copy(busy = false, phase = CheckPhase.Done) }
                            val next = (repo.status(provider) as? NetworkResult.Success)?.data
                            if (next != null) session.status = next
                            _next.trySend(next?.nextStep(provider) ?: NextStep.Ready)
                        }
                        is NetworkResult.Failure -> _state.update { it.copy(busy = false, uiError = r.toUiError()) }
                    }
                }
            }
            is CaptureResult.Failed -> _state.update { it.copy(error = RdServices.explain(result.code, result.info)) }
            CaptureResult.NotInstalled -> _state.update { it.copy(error = OnboardingViewModel.NOT_INSTALLED) }
            CaptureResult.Cancelled -> Unit
        }
    }

    private fun startTimer() {
        timer?.cancel()
        timer = viewModelScope.launch { for (s in OnboardingViewModel.RESEND_SECONDS downTo 0) { _state.update { it.copy(resendIn = s) }; if (s > 0) delay(1_000) } }
    }
}

// ---------------------------------------------------------------- 3.28–3.38 transactions

enum class TxnPhase { Form, CustomerOtp, Processing, Result }

data class TxnState(
    val provider: AepsProvider = AepsProvider.Kotak,
    val types: List<TxnType> = TxnType.forProvider(AepsProvider.Kotak),
    val type: TxnType = TxnType.Withdraw,
    val aadhaar: String = "",
    val mobile: String = "",
    val bank: CustomerBank? = null,
    val recentBanks: List<CustomerBank> = emptyList(),
    val amountText: String = "",
    val quickAmounts: List<Long> = emptyList(),
    val otp: String = "",
    val otpResendIn: Int = 0,
    val phase: TxnPhase = TxnPhase.Form,
    val outcome: TxnOutcome? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val showErrors: Boolean = false,
    /** The customer's Aadhaar consent, ticked by default like the old app; scanning needs it. */
    val consent: Boolean = true,
    val walletNote: Boolean = false,
) {
    val amount: BigDecimal? get() = Money.parse(amountText)
    val aadhaarOk: Boolean get() = Validators.isAadhaar(aadhaar)
    val mobileOk: Boolean get() = Validators.isMobile(mobile)
    val amountOk: Boolean get() = !type.needsAmount || (amount?.let { it >= MIN_AMOUNT && it <= MAX_AMOUNT && it.stripTrailingZeros().scale() <= 0 } == true)
    val canScan: Boolean get() = aadhaarOk && mobileOk && bank != null && amountOk && consent && !busy
    val needsOtp: Boolean get() = type.needsCustomerOtp(amount)

    companion object { val MIN_AMOUNT = BigDecimal(100); val MAX_AMOUNT = BigDecimal(10_000) }
}

@HiltViewModel
class AepsTransactionViewModel @Inject constructor(
    private val repo: AepsRepository,
    private val session: AepsSession,
    private val location: LocationSource,
) : ViewModel() {
    private val _state = MutableStateFlow(TxnState())
    val state: StateFlow<TxnState> = _state.asStateFlow()
    private var otpTxnId: String? = null
    /** One key per attempt; kept if the same attempt is re-sent, replaced when the form changes. */
    private var attemptKey: IdempotencyKey = IdempotencyKey.new()
    private var timer: Job? = null

    val banks: List<CustomerBank> get() = session.config?.banks.orEmpty()

    init {
        val p = session.provider ?: AepsProvider.Kotak
        val cfg: AepsConfig? = session.config
        val last = session.lastCustomer
        _state.value = TxnState(
            provider = p, types = TxnType.forProvider(p),
            quickAmounts = cfg?.quickAmounts.orEmpty().ifEmpty { listOf(500L, 1000L, 2000L, 5000L, 10000L) },
            recentBanks = popular(cfg?.banks.orEmpty()),
            aadhaar = last?.aadhaar.orEmpty(), mobile = last?.mobile.orEmpty(),
            bank = last?.let { l -> cfg?.banks?.firstOrNull { it.iin == l.bankIin } },
        )
    }

    private fun changed(f: (TxnState) -> TxnState) { attemptKey = IdempotencyKey.new(); otpTxnId = null; _state.update { f(it).copy(error = null) } }

    fun selectType(t: TxnType) = changed { it.copy(type = t, walletNote = t == TxnType.Deposit, amountText = if (t.needsAmount) it.amountText else "") }
    fun onAadhaar(v: String) = changed { it.copy(aadhaar = v.filter(Char::isDigit).take(12)) }
    fun onMobile(v: String) = changed { it.copy(mobile = v.filter(Char::isDigit).take(10)) }
    fun onAmount(v: String) = changed { it.copy(amountText = v.filter(Char::isDigit).take(5)) }
    fun onQuick(a: Long) = changed { it.copy(amountText = a.toString()) }
    fun onBank(b: CustomerBank) = changed { it.copy(bank = b) }

    /** Scan pressed. Above ₹5,000 withdrawal: the customer OTP comes first. */
    fun onConsent(on: Boolean) = _state.update { it.copy(consent = on) }

    fun onScanPressed(): Boolean {
        val s = _state.value
        if (!s.canScan) { _state.update { it.copy(showErrors = true) }; return false }
        if (s.needsOtp && otpTxnId == null) { requestCustomerOtp(); return false }
        return true
    }

    fun requestCustomerOtp() {
        val s = _state.value
        val bank = s.bank ?: return
        if (s.otpResendIn > 0 || s.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val at = location.current() ?: run { _state.update { it.copy(busy = false, error = OnboardingViewModel.LOCATION_ERROR) }; return@launch }
            when (val r = repo.customerOtp(s.provider, s.type, s.aadhaar, s.mobile, bank, s.amount ?: BigDecimal.ZERO, at)) {
                is NetworkResult.Success -> { pendingOtpRef = r.data; _state.update { it.copy(busy = false, phase = TxnPhase.CustomerOtp, otp = "") }; startTimer() }
                is NetworkResult.Failure -> _state.update { it.copy(busy = false, error = (r.toUiError() as? UiError.Server)?.message ?: GENERIC_ERROR) }
            }
        }
    }

    private var pendingOtpRef: String? = null

    fun onOtp(v: String) = _state.update { it.copy(otp = v.filter(Char::isDigit).take(6)) }

    /** The bank's OTP reference goes with the transaction; the code itself goes into the PID options. */
    fun otpEntered(): Boolean {
        if (_state.value.otp.length < 4) return false
        otpTxnId = pendingOtpRef
        return true
    }

    /** Customer OTP to put into the scan (only during the above-₹5,000 flow). */
    val scanOtp: String? get() = _state.value.otp.takeIf { otpTxnId != null && it.isNotEmpty() }

    fun cancelOtp() { timer?.cancel(); otpTxnId = null; pendingOtpRef = null; _state.update { it.copy(phase = TxnPhase.Form, otp = "") } }

    fun onCapture(result: CaptureResult) {
        val s = _state.value
        val bank = s.bank ?: return
        when (result) {
            is CaptureResult.Ok -> {
                _state.update { it.copy(phase = TxnPhase.Processing, busy = true, error = null) }
                viewModelScope.launch {
                    val at: GeoPoint = location.current() ?: run { _state.update { it.copy(phase = TxnPhase.Form, busy = false, error = OnboardingViewModel.LOCATION_ERROR) }; return@launch }
                    val outcome = repo.transact(TxnInput(s.provider, s.type, s.aadhaar, s.mobile, bank, if (s.type.needsAmount) s.amount else null, result.type, result.pid, at, otpTxnId, attemptKey))
                    if (outcome is TxnOutcome.Success) session.lastCustomer = AepsSession.LastCustomer(s.aadhaar, s.mobile, bank.iin)
                    _state.update { it.copy(phase = TxnPhase.Result, outcome = outcome, busy = false, otp = "") }
                    otpTxnId = null; pendingOtpRef = null
                }
            }
            is CaptureResult.Failed -> _state.update { it.copy(phase = if (it.phase == TxnPhase.CustomerOtp) it.phase else TxnPhase.Form, error = RdServices.explain(result.code, result.info)) }
            CaptureResult.NotInstalled -> _state.update { it.copy(error = OnboardingViewModel.NOT_INSTALLED) }
            CaptureResult.Cancelled -> Unit
        }
    }

    /** "Same customer again" / "Back to AEPS": keep customer, clear amount and outcome; new attempt key. */
    fun again(type: TxnType? = null, keepCustomer: Boolean = true) {
        attemptKey = IdempotencyKey.new()
        _state.update { it.copy(phase = TxnPhase.Form, outcome = null, amountText = "", otp = "", type = type ?: it.type,
            aadhaar = if (keepCustomer) it.aadhaar else "", mobile = if (keepCustomer) it.mobile else "", bank = if (keepCustomer) it.bank else null) }
    }

    private fun startTimer() {
        timer?.cancel()
        timer = viewModelScope.launch { for (s in OnboardingViewModel.RESEND_SECONDS downTo 0) { _state.update { it.copy(otpResendIn = s) }; if (s > 0) delay(1_000) } }
    }

    override fun onCleared() { otpTxnId = null; pendingOtpRef = null }

    companion object {
        const val GENERIC_ERROR = "Something went wrong. Please try again."
        private val POPULAR = listOf("state bank of india", "punjab national bank", "bank of baroda", "canara bank", "union bank of india")
        fun popular(all: List<CustomerBank>): List<CustomerBank> =
            POPULAR.mapNotNull { p -> all.firstOrNull { it.name.lowercase().startsWith(p) } }.take(3)
        fun search(all: List<CustomerBank>, q: String): List<CustomerBank> {
            val t = q.trim().lowercase()
            if (t.isEmpty()) return all
            return all.filter { it.name.lowercase().contains(t) }.sortedBy { if (it.name.lowercase().startsWith(t)) 0 else 1 }
        }
    }
}
