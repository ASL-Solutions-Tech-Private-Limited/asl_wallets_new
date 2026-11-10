package com.asl.wallet.feature.dmt.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asl.wallet.core.common.dispatch.MIN_PROCESSING_MS
import com.asl.wallet.core.common.format.Money
import com.asl.wallet.core.common.session.CurrentUser
import com.asl.wallet.core.common.session.IdempotencyKey
import com.asl.wallet.feature.dmt.data.RecentCustomer
import com.asl.wallet.feature.dmt.data.RecentCustomerStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.asl.wallet.core.common.validation.Validators
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.network.UiError
import com.asl.wallet.core.network.toUiError
import com.asl.wallet.feature.dmt.data.DmtRepository
import com.asl.wallet.feature.dmt.data.TransferInput
import com.asl.wallet.feature.dmt.domain.Charges
import com.asl.wallet.feature.dmt.domain.Customer
import com.asl.wallet.feature.dmt.domain.CustomerLookup
import com.asl.wallet.feature.dmt.domain.DmtBank
import com.asl.wallet.feature.dmt.domain.DmtOutcome
import com.asl.wallet.feature.dmt.domain.DmtRules
import com.asl.wallet.feature.dmt.domain.Gender
import com.asl.wallet.feature.dmt.domain.OtpPurpose
import com.asl.wallet.feature.dmt.domain.Recipient
import com.asl.wallet.feature.dmt.domain.RecipientForm
import com.asl.wallet.feature.dmt.domain.TransferMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class DmtPage { Mobile, Register, Customer, AddRecipient, RecipientSaved, Amount, Review, Sending, Result }

/** Which OTP the sheet is collecting. */
enum class OtpFor { Customer, Recipient, Transfer }

/** The paid name check: confirm first, then the bank's answer. */
sealed interface NameCheck {
    data object Idle : NameCheck
    data object Confirm : NameCheck
    data object Loading : NameCheck
    /** [typed] is what the retailer had typed when it doesn't match the bank (shown as a warning), else null. */
    data class Done(val bankName: String, val typed: String? = null) : NameCheck {
        val differs: Boolean get() = typed != null
    }
    data class Error(val e: UiError) : NameCheck
}

data class DmtState(
    val page: DmtPage = DmtPage.Mobile,
    /** What's typed on 5B.1: a mobile number or part of a name. */
    val entry: String = "",
    val mobile: String = "",
    val recent: List<RecentCustomer> = emptyList(),
    val busy: Boolean = false,
    val error: UiError? = null,
    val showErrors: Boolean = false,
    // register
    val regName: String = "",
    val gender: Gender = Gender.Male,
    // customer
    val customer: Customer? = null,
    val recipients: List<Recipient> = emptyList(),
    val recipientsLoading: Boolean = false,
    val recipientsError: UiError? = null,
    val query: String = "",
    // add recipient
    val form: RecipientForm = RecipientForm(),
    val banks: List<DmtBank> = emptyList(),
    val banksLoading: Boolean = false,
    val nameCheck: NameCheck = NameCheck.Idle,
    // send
    val recipient: Recipient? = null,
    val amountText: String = "",
    val mode: TransferMode = TransferMode.Imps,
    val charges: Charges? = null,
    val chargesKnown: Boolean = false,
    val balance: BigDecimal? = null,
    // OTP sheet
    val otpFor: OtpFor? = null,
    val otp: String = "",
    val otpError: String? = null,
    val otpUiError: UiError? = null,
    val resendIn: Int = 0,
    /** The recipient was added earlier but never confirmed; a new OTP was sent instead of adding again. */
    val recipientExisted: Boolean = false,
    val outcome: DmtOutcome? = null,
) {
    val mobileOk: Boolean get() = Validators.isMobile(mobile)
    val amount: Int? get() = DmtRules.amountOf(amountText)
    val total: BigDecimal? get() = amount?.let { a -> charges?.total ?: BigDecimal(a) }
    /** What's missing from the wallet for amount + charge. */
    val shortBy: BigDecimal? get() = total?.let { t -> balance?.let { b -> (t - b).takeIf { it.signum() > 0 } } }
    /** Over the customer's monthly limit, when the server sent it. */
    val overLimit: Boolean get() = amount != null && customer?.leftLimit?.let { BigDecimal(amount!!) > it } == true
    val visibleRecipients: List<Recipient> get() = DmtRules.search(recipients, query)
    /** Recent customers matching what's typed: digits match the number, letters match the name. */
    val visibleRecent: List<RecentCustomer> get() {
        val t = entry.trim()
        if (t.isEmpty()) return recent
        val digits = t.filter(Char::isDigit)
        return if (t.any(Char::isLetter)) recent.filter { it.name.contains(t, ignoreCase = true) }
        else recent.filter { it.mobile.contains(digits) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DmtViewModel @Inject constructor(
    private val repo: DmtRepository,
    private val recentStore: RecentCustomerStore,
    private val currentUser: CurrentUser,
) : ViewModel() {
    private val _state = MutableStateFlow(DmtState())
    val state: StateFlow<DmtState> = _state.asStateFlow()
    private var timer: Job? = null
    /** One key per confirmed transfer: made at Review, dropped after a definite answer or a new transfer. */
    private var key: IdempotencyKey? = null
    var now: () -> Date = { Date() }
    private val regno: Long? get() = currentUser.user.value?.regno

    init {
        viewModelScope.launch {
            currentUser.user.map { it?.regno }.distinctUntilChanged()
                .flatMapLatest { r -> if (r == null) flowOf(emptyList()) else recentStore.recent(r) }
                .collect { list -> _state.update { it.copy(recent = list) } }
        }
    }

    // ------------------------------------------------------------ customer

    /** 5B.1: digits are a mobile number; letters search recent customers by name. */
    fun onMobile(v: String) {
        val entry = v.filter { it.isLetterOrDigit() || it == ' ' || it == '+' || it == '-' }.take(40)
        _state.update { it.copy(entry = entry, mobile = if (entry.any(Char::isLetter)) "" else DmtRules.cleanMobile(entry), error = null, showErrors = false) }
    }

    /** A tap on a recent customer: looked up again for today's status and limit. */
    fun pickRecent(c: RecentCustomer) {
        _state.update { it.copy(entry = c.mobile, mobile = c.mobile, error = null) }
        lookup()
    }

    fun clearRecent() { regno?.let { r -> viewModelScope.launch { recentStore.clear(r) } } }

    /** 5B.1 → found (5B.2), not registered (5B.3) or registered-but-unverified (OTP sheet). */
    fun lookup() {
        val s = _state.value
        if (!s.mobileOk) { _state.update { it.copy(showErrors = true) }; return }
        if (s.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.lookup(s.mobile)) {
                is NetworkResult.Failure -> _state.update { it.copy(busy = false, error = r.toUiError()) }
                is NetworkResult.Success -> when (val l = r.data) {
                    CustomerLookup.NotRegistered -> _state.update { it.copy(busy = false, page = DmtPage.Register) }
                    CustomerLookup.NeedsOtp -> { _state.update { it.copy(busy = false) }; openOtp(OtpFor.Customer) }
                    is CustomerLookup.Found -> openCustomer(l.customer)
                }
            }
        }
    }

    private fun openCustomer(c: Customer) {
        _state.update { it.copy(busy = false, customer = c, page = DmtPage.Customer, query = "") }
        regno?.let { r -> viewModelScope.launch { recentStore.remember(r, c.mobile, c.name, now().time) } }
        loadRecipients()
    }

    fun loadRecipients() {
        val c = _state.value.customer ?: return
        _state.update { it.copy(recipientsLoading = true, recipientsError = null) }
        viewModelScope.launch {
            when (val r = repo.recipients(c.mobile)) {
                is NetworkResult.Success -> _state.update { it.copy(recipientsLoading = false, recipients = r.data) }
                is NetworkResult.Failure -> _state.update { it.copy(recipientsLoading = false, recipientsError = r.toUiError()) }
            }
        }
    }

    fun onQuery(q: String) = _state.update { it.copy(query = q.take(30)) }

    /** Back to 5B.1, keeping the recent list. */
    fun changeCustomer() = _state.update { DmtState(entry = it.entry, mobile = it.mobile, recent = it.recent) }

    fun onRegName(v: String) = _state.update { it.copy(regName = v.filter { c -> c.isLetter() || c == ' ' || c == '.' }.take(60), error = null) }
    fun onGender(g: Gender) = _state.update { it.copy(gender = g) }

    fun register() {
        val s = _state.value
        if (!DmtRules.isCustomerName(s.regName)) { _state.update { it.copy(showErrors = true) }; return }
        if (s.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.register(s.mobile, s.regName, s.gender)) {
                is NetworkResult.Success -> { _state.update { it.copy(busy = false) }; openOtp(OtpFor.Customer) }
                is NetworkResult.Failure -> _state.update { it.copy(busy = false, error = r.toUiError()) }
            }
        }
    }

    // ------------------------------------------------------------ recipient

    fun startAddRecipient() {
        _state.update { it.copy(page = DmtPage.AddRecipient, form = RecipientForm(), nameCheck = NameCheck.Idle, showErrors = false, error = null) }
        loadBanks()
    }

    fun loadBanks() {
        if (_state.value.banks.isNotEmpty() || _state.value.banksLoading) return
        _state.update { it.copy(banksLoading = true) }
        viewModelScope.launch {
            val r = repo.banks()
            _state.update { it.copy(banksLoading = false, banks = (r as? NetworkResult.Success)?.data.orEmpty(), error = (r as? NetworkResult.Failure)?.toUiError()) }
        }
    }

    private fun editForm(f: (RecipientForm) -> RecipientForm, keepCheck: Boolean = false) =
        _state.update { it.copy(form = f(it.form).let { n -> if (keepCheck) n else n.copy(bankName = null) }, nameCheck = if (keepCheck) it.nameCheck else NameCheck.Idle, error = null) }

    fun onBank(b: DmtBank) = editForm({ it.copy(bank = b, ifsc = if (it.ifsc.isBlank()) b.ifscSample.orEmpty() else it.ifsc) })
    fun onAccount(v: String) = editForm({ it.copy(account = v.filter(Char::isDigit).take(18)) })
    fun onConfirm(v: String) = editForm({ it.copy(confirm = v.filter(Char::isDigit).take(18)) })
    fun onIfsc(v: String) = editForm({ it.copy(ifsc = v.filter(Char::isLetterOrDigit).take(11).uppercase()) })
    fun onRecipientName(v: String) = editForm({ it.copy(name = v.filter { c -> RecipientForm.isNameChar(c) }.take(100)) }, keepCheck = true)
    fun onRecipientMobile(v: String) = editForm({ it.copy(mobile = DmtRules.cleanMobile(v)) })

    /** Optional and paid (₹ 3): asks first. */
    fun askNameCheck() {
        if (!_state.value.form.canCheckName()) { _state.update { it.copy(showErrors = true) }; return }
        _state.update { it.copy(nameCheck = NameCheck.Confirm) }
    }

    fun cancelNameCheck() = _state.update { it.copy(nameCheck = NameCheck.Idle) }

    fun runNameCheck() {
        val f = _state.value.form
        if (_state.value.nameCheck != NameCheck.Confirm) return
        _state.update { it.copy(nameCheck = NameCheck.Loading) }
        viewModelScope.launch {
            when (val r = repo.checkName(f)) {
                is NetworkResult.Success -> {
                    val differs = f.name.isNotBlank() && DmtRules.namesDiffer(f.name, r.data)
                    _state.update {
                        it.copy(
                            // The bank's name always goes in the field (retailers shouldn't have to choose);
                            // a different typed name only shows as a warning under it.
                            nameCheck = NameCheck.Done(r.data, typed = if (differs) DmtRules.cleanName(f.name) else null),
                            form = it.form.copy(bankName = r.data, name = r.data),
                        )
                    }
                }
                is NetworkResult.Failure -> _state.update { it.copy(nameCheck = NameCheck.Error(r.toUiError())) }
            }
        }
    }

    fun saveRecipient() {
        val s = _state.value
        val c = s.customer ?: return
        if (s.form.problems().isNotEmpty()) { _state.update { it.copy(showErrors = true) }; return }
        if (s.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.addRecipient(c.mobile, s.form)) {
                is NetworkResult.Success -> { _state.update { it.copy(busy = false, recipientExisted = false) }; openOtp(OtpFor.Recipient) }
                // Added before but never confirmed (unconfirmed recipients aren't listed): confirm it with a new OTP.
                is NetworkResult.Failure.Api if r.message.contains("already exist", ignoreCase = true) ->
                    when (val o = repo.sendOtp(s.form.mobile, OtpPurpose.Recipient, s.form.account)) {
                        is NetworkResult.Success -> { _state.update { it.copy(busy = false, recipientExisted = true) }; openOtp(OtpFor.Recipient) }
                        is NetworkResult.Failure -> _state.update { it.copy(busy = false, error = o.toUiError()) }
                    }
                is NetworkResult.Failure -> _state.update { it.copy(busy = false, error = r.toUiError()) }
            }
        }
    }

    // ------------------------------------------------------------ OTP sheet (customer, recipient, transfer)

    private fun openOtp(kind: OtpFor) {
        _state.update { it.copy(otpFor = kind, otp = "", otpError = null, otpUiError = null) }
        startTimer()
    }

    fun onOtp(v: String) = _state.update { it.copy(otp = v.filter(Char::isDigit).take(OTP_MAX), otpError = null) }

    fun closeOtp() { timer?.cancel(); _state.update { it.copy(otpFor = null, otp = "", resendIn = 0) } }

    fun resendOtp() {
        val s = _state.value
        val kind = s.otpFor ?: return
        if (s.resendIn > 0 || s.busy) return
        _state.update { it.copy(busy = true, otpUiError = null) }
        viewModelScope.launch {
            val r = when (kind) {
                OtpFor.Customer -> repo.sendOtp(s.mobile, OtpPurpose.Customer)
                OtpFor.Recipient -> repo.sendOtp(s.form.mobile, OtpPurpose.Recipient, s.form.account)
                OtpFor.Transfer -> repo.sendTransferOtp()
            }
            _state.update { it.copy(busy = false, otpUiError = (r as? NetworkResult.Failure)?.toUiError()) }
            if (r is NetworkResult.Success) startTimer()
        }
    }

    fun submitOtp() {
        val s = _state.value
        val kind = s.otpFor ?: return
        if (s.otp.length < OTP_MIN || s.busy) return
        when (kind) {
            OtpFor.Transfer -> transfer()
            OtpFor.Customer, OtpFor.Recipient -> {
                _state.update { it.copy(busy = true, otpError = null, otpUiError = null) }
                viewModelScope.launch {
                    val r = if (kind == OtpFor.Customer) repo.verifyOtp(s.mobile, OtpPurpose.Customer, s.otp)
                    else repo.verifyOtp(s.form.mobile, OtpPurpose.Recipient, s.otp, s.form.account)
                    when (r) {
                        // Wrong or expired OTP: the sheet stays open with the server's message.
                        is NetworkResult.Failure -> _state.update { it.copy(busy = false, otp = "", otpUiError = r.toUiError()) }
                        is NetworkResult.Success -> {
                            timer?.cancel()
                            _state.update { it.copy(busy = false, otpFor = null, otp = "") }
                            if (kind == OtpFor.Customer) {
                                // Verified: look the customer up again for their name and limit.
                                _state.update { it.copy(page = DmtPage.Mobile) }
                                lookup()
                            } else {
                                _state.update { it.copy(page = DmtPage.RecipientSaved) }
                                loadRecipients()
                            }
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------ send

    fun pickRecipient(r: Recipient) = _state.update { it.copy(recipient = r, page = DmtPage.Amount, amountText = "", charges = null, chargesKnown = false, showErrors = false, error = null) }

    /** From "Recipient saved": the new recipient by account number, once the list reloads. */
    fun sendToSaved() {
        val s = _state.value
        val r = s.recipients.firstOrNull { it.accountNumber.endsWith(s.form.account.takeLast(6)) && it.accountNumber.takeLast(4) == s.form.account.takeLast(4) }
        if (r != null) pickRecipient(r) else _state.update { it.copy(page = DmtPage.Customer) }
    }

    fun onAmount(v: String) = _state.update { it.copy(amountText = v.filter(Char::isDigit).take(6).trimStart('0'), charges = null, chargesKnown = false, error = null) }
    fun onMode(m: TransferMode) = _state.update { it.copy(mode = m, charges = null, chargesKnown = false) }

    /** 5B.13 → 5B.14: charge and wallet balance, then the review. Nothing moves yet. */
    fun review() {
        val s = _state.value
        val amount = s.amount
        if (s.recipient == null || amount == null || s.overLimit) { _state.update { it.copy(showErrors = true) }; return }
        if (s.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val c = repo.charges(amount, s.mode)
            val b = repo.balance()
            when {
                c is NetworkResult.Failure -> _state.update { it.copy(busy = false, error = c.toUiError()) }
                b is NetworkResult.Failure -> _state.update { it.copy(busy = false, error = b.toUiError()) }
                else -> {
                    key = IdempotencyKey.new()
                    _state.update {
                        it.copy(busy = false, page = DmtPage.Review, charges = (c as NetworkResult.Success).data, chargesKnown = c.data != null,
                            balance = (b as NetworkResult.Success).data)
                    }
                }
            }
        }
    }

    fun backFromReview() { if (_state.value.page == DmtPage.Review) _state.update { it.copy(page = DmtPage.Amount) } }

    /** Slide to send: asks for the retailer's OTP. Money moves only after the OTP. */
    fun slideToSend() {
        val s = _state.value
        if (s.page != DmtPage.Review || s.shortBy != null || s.overLimit || s.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.sendTransferOtp()) {
                is NetworkResult.Success -> { _state.update { it.copy(busy = false) }; openOtp(OtpFor.Transfer) }
                is NetworkResult.Failure -> _state.update { it.copy(busy = false, error = r.toUiError()) }
            }
        }
    }

    private fun transfer() {
        val s = _state.value
        val c = s.customer ?: return
        val rcp = s.recipient ?: return
        val amount = s.amount ?: return
        val k = key ?: return
        _state.update { it.copy(page = DmtPage.Sending, otpFor = null) }
        viewModelScope.launch {
            val started = System.nanoTime()
            when (val out = repo.transfer(TransferInput(c, rcp, s.mode, amount, s.charges?.charge, s.otp, k))) {
                // Nothing moved: back to the OTP. A fresh key, so the server can't replay its "wrong OTP" answer.
                // Refused before any money moved: back to the amount with the server's reason.
                is DmtOutcome.Refused -> { key = null; timer?.cancel()
                    _state.update { it.copy(page = DmtPage.Amount, otp = "", error = out.reason?.let(UiError::Server) ?: UiError.Unknown) } }
                is DmtOutcome.WrongOtp -> { key = IdempotencyKey.new(); _state.update { it.copy(page = DmtPage.Review, otpFor = OtpFor.Transfer, otp = "", otpError = out.reason ?: "") } }
                else -> {
                    key = null
                    timer?.cancel()
                    if (out is DmtOutcome.Sent || out is DmtOutcome.Pending) regno?.let { r -> recentStore.noteTransfer(r, c.mobile, "${Money.format(BigDecimal(amount), showPaise = false)} to ${rcp.name}", now().time) }
                    // 5B.16 stays up at least ~2 s so an instant answer doesn't flash past; a slow bank adds nothing.
                    val left = MIN_PROCESSING_MS - (System.nanoTime() - started) / 1_000_000
                    if (left > 0) delay(left)
                    _state.update { it.copy(page = DmtPage.Result, outcome = out.stamp()) }
                }
            }
        }
    }

    private fun DmtOutcome.stamp(): DmtOutcome {
        val t = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH).format(now())
        return when (this) {
            is DmtOutcome.Sent -> DmtOutcome.Sent(receipt.copy(time = t))
            is DmtOutcome.Pending -> DmtOutcome.Pending(receipt.copy(time = t))
            is DmtOutcome.Failed -> DmtOutcome.Failed(receipt.copy(time = t), reason)
            is DmtOutcome.WrongOtp, is DmtOutcome.Refused -> this
        }
    }

    /** After a result: back to the same customer for another transfer. */
    fun backToCustomer() {
        key = null
        _state.update { it.copy(page = DmtPage.Customer, outcome = null, recipient = null, amountText = "", charges = null, chargesKnown = false, otp = "") }
        loadRecipients()
    }

    fun back(): Boolean {
        val p = _state.value.page
        _state.update {
            when (p) {
                DmtPage.Register -> it.copy(page = DmtPage.Mobile)
                DmtPage.Customer -> DmtState(entry = it.entry, mobile = it.mobile, recent = it.recent)
                DmtPage.AddRecipient, DmtPage.RecipientSaved, DmtPage.Amount -> it.copy(page = DmtPage.Customer, error = null)
                DmtPage.Review -> it.copy(page = DmtPage.Amount)
                else -> return false
            }
        }
        return true
    }

    private fun startTimer() {
        timer?.cancel()
        timer = viewModelScope.launch { for (s in RESEND_SECONDS downTo 0) { _state.update { it.copy(resendIn = s) }; if (s > 0) delay(1_000) } }
    }

    companion object {
        /** Every DMT OTP is 4 digits (customer, recipient and transfer), as in the old app on the same API. */
        const val OTP_LENGTH = 4
        const val OTP_MIN = OTP_LENGTH
        const val OTP_MAX = OTP_LENGTH
        const val RESEND_SECONDS = 30
    }
}
