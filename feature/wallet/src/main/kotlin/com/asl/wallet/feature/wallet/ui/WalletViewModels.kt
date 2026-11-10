package com.asl.wallet.feature.wallet.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asl.wallet.core.common.dispatch.MIN_PROCESSING_MS
import com.asl.wallet.core.common.dispatch.atLeast
import com.asl.wallet.core.common.session.IdempotencyKey
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.network.UiError
import com.asl.wallet.core.network.toUiError
import com.asl.wallet.feature.wallet.data.OrderResult
import com.asl.wallet.feature.wallet.data.WalletRepository
import com.asl.wallet.feature.wallet.data.WalletSession
import com.asl.wallet.feature.wallet.domain.AccountOwner
import com.asl.wallet.feature.wallet.domain.AddBankForm
import com.asl.wallet.feature.wallet.domain.AddMoneyInfo
import com.asl.wallet.feature.wallet.domain.AddMoneyResult
import com.asl.wallet.feature.wallet.domain.AmountProblem
import com.asl.wallet.feature.wallet.domain.BankAccount
import com.asl.wallet.feature.wallet.domain.BankOption
import com.asl.wallet.feature.wallet.domain.CompanyBank
import com.asl.wallet.feature.wallet.domain.Day
import com.asl.wallet.feature.wallet.domain.FundMode
import com.asl.wallet.feature.wallet.domain.FundRequestForm
import com.asl.wallet.feature.wallet.domain.FundRequestItem
import com.asl.wallet.feature.wallet.domain.FundRequestSetup
import com.asl.wallet.feature.wallet.domain.FundStatus
import com.asl.wallet.feature.wallet.domain.FundSummary
import com.asl.wallet.feature.wallet.domain.PayUOrder
import com.asl.wallet.feature.wallet.domain.SendAccount
import com.asl.wallet.feature.wallet.domain.SendMoneySetup
import com.asl.wallet.feature.wallet.domain.SendOutcome
import com.asl.wallet.feature.wallet.domain.WalletEntry
import com.asl.wallet.feature.wallet.domain.WalletOverview
import com.asl.wallet.feature.wallet.payu.HashSource
import com.asl.wallet.feature.wallet.payu.PayUCheckout
import com.asl.wallet.feature.wallet.payu.PayUReturn
import com.asl.wallet.feature.wallet.payu.WalletConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Amount typed by the retailer: digits and up to 2 decimals, max 8 whole digits. */
internal fun cleanAmount(raw: String): String {
    val t = raw.filter { it.isDigit() || it == '.' }
    val dot = t.indexOf('.')
    return if (dot < 0) t.take(8) else t.substring(0, dot).take(8) + "." + t.substring(dot + 1).filter(Char::isDigit).take(2)
}

internal fun String.toAmount(): BigDecimal? = toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

// ---------------------------------------------------------------- 4.1–4.2 Wallet

data class WalletState(
    val loading: Boolean = true,
    val overview: WalletOverview? = null,
    val recent: List<WalletEntry> = emptyList(),
    val verifiedAccounts: Int? = null,
    val error: UiError? = null,
    val hidden: Boolean = false,
)

@HiltViewModel
class WalletViewModel @Inject constructor(private val repo: WalletRepository) : ViewModel() {
    private val _state = MutableStateFlow(WalletState())
    val state: StateFlow<WalletState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val recent = async { repo.recent() }
            val accounts = async { repo.bankAccounts() }
            when (val o = repo.overview()) {
                // Never show ₹ 0 as if it were real: no balance means the error state (4.2).
                is NetworkResult.Failure -> _state.update { it.copy(loading = false, overview = null, error = o.toUiError()) }
                is NetworkResult.Success -> _state.update { it.copy(loading = false, overview = o.data, error = null) }
            }
            (recent.await() as? NetworkResult.Success)?.let { r -> _state.update { it.copy(recent = r.data) } }
            (accounts.await() as? NetworkResult.Success)?.let { r -> _state.update { it.copy(verifiedAccounts = r.data.count(BankAccount::verified)) } }
        }
    }

    fun toggleHidden() = _state.update { it.copy(hidden = !it.hidden) }
}

// ---------------------------------------------------------------- 4.3–4.11 Add money (PayU)

enum class AddPhase { Form, Preparing, Confirming, Result }

data class AddMoneyState(
    val loading: Boolean = true,
    val info: AddMoneyInfo? = null,
    val loadError: UiError? = null,
    val amountText: String = "",
    val showErrors: Boolean = false,
    val phase: AddPhase = AddPhase.Form,
    val error: UiError? = null,
    val result: AddMoneyResult? = null,
    val newBalance: BigDecimal? = null,
    val checking: Boolean = false,
    /** 4.11: PayU isn't switched on yet. Nothing was charged. */
    val showSoon: Boolean = false,
) {
    val amount: BigDecimal? get() = amountText.toAmount()
    val problem: AmountProblem? get() = info?.limits?.check(amount)
}

sealed interface AddMoneyEvent { data class OpenPayU(val order: PayUOrder, val production: Boolean) : AddMoneyEvent }

@HiltViewModel
class AddMoneyViewModel @Inject constructor(
    private val repo: WalletRepository,
    private val config: WalletConfig,
    private val saved: SavedStateHandle,
    /** Stateless; held here so the screen gets it without its own injection. */
    val payu: PayUCheckout,
) : ViewModel() {
    private val _state = MutableStateFlow(AddMoneyState())
    val state: StateFlow<AddMoneyState> = _state.asStateFlow()
    private val _events = Channel<AddMoneyEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** One key per payment attempt: a retry of the same "Pay" can't create a second order. */
    private var key: IdempotencyKey? = null
    val hashes = HashSource { ask -> repo.payuHash(ask) }

    init {
        load()
        // After the app was killed while PayU was open, check the payment instead of starting again.
        saved.get<String>(KEY_TXN)?.let { txn -> confirm(txn, saved.get<String>(KEY_AMOUNT)?.toBigDecimalOrNull(), PayUReturn.Finished) }
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            when (val r = repo.addMoneyInfo()) {
                is NetworkResult.Success -> _state.update { it.copy(loading = false, info = r.data) }
                is NetworkResult.Failure -> _state.update { it.copy(loading = false, loadError = r.toUiError()) }
            }
        }
    }

    fun onAmount(v: String) { key = null; _state.update { it.copy(amountText = cleanAmount(v), error = null) } }
    fun onQuick(v: Long) = onAmount(v.toString())
    fun dismissSoon() = _state.update { it.copy(showSoon = false) }

    fun pay() {
        val s = _state.value
        if (s.phase != AddPhase.Form || s.info == null) return
        if (s.problem != null) { _state.update { it.copy(showErrors = true) }; return }
        if (!config.payuEnabled) { _state.update { it.copy(showSoon = true) }; return }
        val amount = s.amount ?: return
        val k = key ?: IdempotencyKey.new().also { key = it }
        _state.update { it.copy(phase = AddPhase.Preparing, error = null) }
        viewModelScope.launch {
            when (val r = atLeast { repo.createOrder(amount, k) }) {
                is NetworkResult.Success -> when (val o = r.data) {
                    is OrderResult.Ready -> {
                        saved[KEY_TXN] = o.order.statusId; saved[KEY_AMOUNT] = amount.toPlainString()
                        _events.send(AddMoneyEvent.OpenPayU(o.order, config.payuProduction))
                    }
                    OrderResult.NotSupportedYet -> _state.update { it.copy(phase = AddPhase.Form, showSoon = true) }
                }
                // No PayU screen was opened, so nothing was paid. The same key is kept for the retry.
                is NetworkResult.Failure -> _state.update { it.copy(phase = AddPhase.Form, error = r.toUiError()) }
            }
        }
    }

    /** PayU's screens closed. Whatever they said, the server decides. */
    fun onPayUReturn(order: PayUOrder, ret: PayUReturn, mihpayid: String? = null) = confirm(order.statusId, order.amount, ret, mihpayid)

    private fun confirm(txnId: String, amount: BigDecimal?, ret: PayUReturn, mihpayid: String? = null) {
        _state.update { it.copy(phase = AddPhase.Confirming) }
        viewModelScope.launch {
            val started = System.nanoTime()
            // The app never asks the server to credit (no verify call, decided 9 Nov 2026). PayU tells the server
            // directly (success/failure URL, webhook); the server credits once. The app only reads status below.
            var result: AddMoneyResult = AddMoneyResult.Pending(txnId, amount)
            for (attempt in 0 until POLLS) {
                val r = repo.addMoneyStatus(txnId, amount)
                if (r is NetworkResult.Success) result = r.data
                if (result !is AddMoneyResult.Pending) break
                if (attempt < POLLS - 1) delay(POLL_MS)
            }
            if (ret == PayUReturn.Cancelled && result is AddMoneyResult.Pending) result = AddMoneyResult.Failed(txnId, amount, null)
            val left = MIN_PROCESSING_MS - (System.nanoTime() - started) / 1_000_000
            if (left > 0) delay(left)
            finish(result)
        }
    }

    fun checkAgain() {
        val r = _state.value.result as? AddMoneyResult.Pending ?: return
        _state.update { it.copy(checking = true) }
        viewModelScope.launch {
            // Ask by the id the status API takes (clientTxnId), kept while the payment is pending.
            val id = saved.get<String>(KEY_TXN) ?: r.txnId
            val now = (repo.addMoneyStatus(id, r.amount) as? NetworkResult.Success)?.data ?: r
            _state.update { it.copy(checking = false) }
            finish(now)
        }
    }

    private suspend fun finish(result: AddMoneyResult) {
        if (result !is AddMoneyResult.Pending) { saved.remove<String>(KEY_TXN); saved.remove<String>(KEY_AMOUNT); key = null }
        // The server's own figure after crediting wins; otherwise read the balance again.
        val balance = if (result is AddMoneyResult.Success) result.balanceAfter ?: (repo.overview() as? NetworkResult.Success)?.data?.balance else null
        _state.update { it.copy(phase = AddPhase.Result, result = result, newBalance = balance) }
    }

    fun again() { key = null; _state.update { it.copy(phase = AddPhase.Form, result = null, showErrors = false) }; load() }

    companion object {
        const val POLLS = 3
        const val POLL_MS = 3_000L
        private const val KEY_TXN = "payu_txn"
        private const val KEY_AMOUNT = "payu_amount"
    }
}

// ---------------------------------------------------------------- 4.12–4.14, 4.19 Fund request form

enum class FundPhase { Loading, Unavailable, Form, Sent }

data class FundState(
    val phase: FundPhase = FundPhase.Loading,
    val error: UiError? = null,
    val setup: FundRequestSetup? = null,
    val form: FundRequestForm = FundRequestForm(),
    val editing: FundRequestItem? = null,
    val showErrors: Boolean = false,
    val busy: Boolean = false,
    val submitError: UiError? = null,
    val sentRef: String? = null,
) {
    fun problems(today: Day) = form.problems(today)
}

@HiltViewModel
class FundRequestViewModel @Inject constructor(private val repo: WalletRepository, private val session: WalletSession) : ViewModel() {
    private val _state = MutableStateFlow(FundState(editing = session.takeEditing()))
    val state: StateFlow<FundState> = _state.asStateFlow()
    var today: () -> Day = { Day.today() }

    init { load() }

    fun load() {
        _state.update { it.copy(phase = FundPhase.Loading, error = null) }
        viewModelScope.launch {
            when (val r = repo.fundSetup()) {
                // 4.19: UAT returns 500 here today; the retailer sees a clear message, never an error code.
                is NetworkResult.Failure -> _state.update { it.copy(phase = FundPhase.Unavailable, error = r.toUiError()) }
                is NetworkResult.Success -> _state.update { s ->
                    val e = s.editing
                    val form = if (e != null && s.form == FundRequestForm()) FundRequestForm(
                        bank = r.data.banks.firstOrNull { it.id == e.bankId }, amountText = e.amount.stripTrailingZeros().toPlainString(),
                        mode = e.mode, utr = e.utr, date = Day.parse(e.depositDate), remarks = e.remarks.orEmpty(),
                    ) else s.form
                    s.copy(phase = FundPhase.Form, setup = r.data, form = form)
                }
            }
        }
    }

    fun edit(f: (FundRequestForm) -> FundRequestForm) = _state.update { it.copy(form = f(it.form), submitError = null) }
    fun onBank(b: CompanyBank) = edit { it.copy(bank = b) }
    fun onAmount(v: String) = edit { it.copy(amountText = cleanAmount(v)) }
    fun onMode(m: FundMode) = edit { it.copy(mode = m) }
    fun onUtr(v: String) = edit { it.copy(utr = v.filter { c -> c.isLetterOrDigit() || c == '-' || c == '/' }.take(40).uppercase()) }
    fun onDate(d: Day) = edit { it.copy(date = d) }
    fun onRemarks(v: String) = edit { it.copy(remarks = v.take(255)) }

    fun submit() {
        val s = _state.value
        val setup = s.setup ?: return
        if (s.busy) return
        if (s.problems(today()).isNotEmpty()) { _state.update { it.copy(showErrors = true) }; return }
        _state.update { it.copy(busy = true, submitError = null) }
        viewModelScope.launch {
            when (val r = repo.submitFund(s.form, setup.adminId, s.editing?.id)) {
                is NetworkResult.Success -> _state.update { it.copy(busy = false, phase = FundPhase.Sent, sentRef = r.data ?: s.editing?.ref) }
                is NetworkResult.Failure -> _state.update { it.copy(busy = false, submitError = r.toUiError()) }
            }
        }
    }
}

// ---------------------------------------------------------------- 4.15–4.18 My requests

data class RequestsState(
    val loading: Boolean = true,
    val filter: FundStatus? = null,
    val items: List<FundRequestItem> = emptyList(),
    val summary: FundSummary? = null,
    val page: Int = 1,
    val canLoadMore: Boolean = false,
    val loadingMore: Boolean = false,
    val error: UiError? = null,
    val selected: FundRequestItem? = null,
    val confirmDelete: Boolean = false,
    val deleting: Boolean = false,
    val actionError: UiError? = null,
)

@HiltViewModel
class MyRequestsViewModel @Inject constructor(private val repo: WalletRepository, private val session: WalletSession) : ViewModel() {
    private val _state = MutableStateFlow(RequestsState())
    val state: StateFlow<RequestsState> = _state.asStateFlow()
    private var job: Job? = null

    fun load(filter: FundStatus? = _state.value.filter) {
        job?.cancel()
        _state.update { it.copy(loading = true, filter = filter, error = null, page = 1) }
        job = viewModelScope.launch {
            when (val r = repo.fundRequests(1, filter)) {
                is NetworkResult.Success -> _state.update {
                    it.copy(loading = false, items = r.data.items, summary = r.data.summary ?: it.summary, canLoadMore = r.data.canLoadMore)
                }
                is NetworkResult.Failure -> _state.update { it.copy(loading = false, error = r.toUiError()) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (!s.canLoadMore || s.loadingMore || s.loading) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val r = repo.fundRequests(s.page + 1, s.filter)
            _state.update {
                if (r is NetworkResult.Success) it.copy(loadingMore = false, page = s.page + 1, items = (it.items + r.data.items).distinctBy(FundRequestItem::id), canLoadMore = r.data.canLoadMore)
                else it.copy(loadingMore = false, canLoadMore = false)
            }
        }
    }

    fun open(item: FundRequestItem) = _state.update { it.copy(selected = item, actionError = null) }
    fun close() = _state.update { it.copy(selected = null, confirmDelete = false) }
    fun askDelete() = _state.update { it.copy(confirmDelete = true) }
    fun cancelDelete() = _state.update { it.copy(confirmDelete = false) }

    /** Hands the pending request to the form; only pending requests can be edited. */
    fun startEdit(): Boolean {
        val item = _state.value.selected?.takeIf(FundRequestItem::editable) ?: return false
        session.editing = item
        return true
    }

    fun delete() {
        val item = _state.value.selected?.takeIf(FundRequestItem::editable) ?: return
        _state.update { it.copy(deleting = true, actionError = null) }
        viewModelScope.launch {
            when (val r = repo.deleteFund(item.id)) {
                is NetworkResult.Success -> { _state.update { it.copy(deleting = false, confirmDelete = false, selected = null) }; load() }
                is NetworkResult.Failure -> _state.update { it.copy(deleting = false, confirmDelete = false, actionError = r.toUiError()) }
            }
        }
    }
}

// ---------------------------------------------------------------- 4.20–4.26 Send to bank

enum class SendPhase { Loading, LoadError, NoAccount, Form, Sending, Result }

data class SendState(
    val phase: SendPhase = SendPhase.Loading,
    val error: UiError? = null,
    val setup: SendMoneySetup? = null,
    val account: SendAccount? = null,
    val amountText: String = "",
    val mode: String = "IMPS",
    val showErrors: Boolean = false,
    val busy: Boolean = false,
    val formError: UiError? = null,
    val otpOpen: Boolean = false,
    val otp: String = "",
    val otpError: String? = null,
    val otpUiError: UiError? = null,
    val resendIn: Int = 0,
    val outcome: SendOutcome? = null,
    /** Set when the payout can't go without the shop's location (the payout API needs it). */
    val location: LocationNeed? = null,
    /** Where the payout OTP went (masked), from the server. */
    val otpTo: String? = null,
) {
    val amount: BigDecimal? get() = amountText.toAmount()
    val problem: AmountProblem? get() = setup?.limits?.check(amount, setup.balance)
}

/** What's missing before a payout: the permission, location turned on, or a fix (no signal). */
enum class LocationNeed { Permission, TurnOn, NoFix }

@HiltViewModel
class SendMoneyViewModel @Inject constructor(
    private val repo: WalletRepository,
    private val location: com.asl.wallet.feature.wallet.data.PayoutLocation,
) : ViewModel() {
    private val _state = MutableStateFlow(SendState())
    val state: StateFlow<SendState> = _state.asStateFlow()
    private var timer: Job? = null
    /** One key per transfer: made when the retailer confirms, dropped only after a definite failure. */
    private var key: IdempotencyKey? = null
    var now: () -> Date = { Date() }

    init { load() }

    fun load() {
        _state.update { it.copy(phase = SendPhase.Loading, error = null) }
        viewModelScope.launch {
            when (val r = repo.sendSetup()) {
                is NetworkResult.Failure -> _state.update { it.copy(phase = SendPhase.LoadError, error = r.toUiError()) }
                is NetworkResult.Success -> _state.update { s ->
                    val d = r.data
                    s.copy(
                        phase = if (d.accounts.isEmpty()) SendPhase.NoAccount else SendPhase.Form, setup = d,
                        account = d.accounts.firstOrNull { it.id == s.account?.id && it.verified } ?: d.accounts.filter { it.verified }.singleOrNull(),
                        mode = d.modes.firstOrNull { it == s.mode } ?: d.modes.first(),
                    )
                }
            }
        }
    }

    /** An account waiting for verification can't be chosen. */
    fun onAccount(a: SendAccount) { if (a.verified) _state.update { it.copy(account = a, formError = null) } }
    fun onAmount(v: String) = _state.update { it.copy(amountText = cleanAmount(v), formError = null) }
    fun onMode(m: String) = _state.update { it.copy(mode = m) }

    /** 4.20 → 4.22: checks the form, then asks the server to send the OTP. No money moves yet. */
    fun proceed() {
        val s = _state.value
        if (s.busy || s.phase != SendPhase.Form) return
        val account = s.account
        val amount = s.amount
        if (account == null || !account.verified || amount == null || s.problem != null) { _state.update { it.copy(showErrors = true) }; return }
        // Checked before the OTP, so the retailer isn't sent a code for a payout that can't go.
        when {
            !location.hasPermission() -> { _state.update { it.copy(location = LocationNeed.Permission) }; return }
            !location.isEnabled() -> { _state.update { it.copy(location = LocationNeed.TurnOn) }; return }
        }
        _state.update { it.copy(busy = true, formError = null, location = null) }
        viewModelScope.launch {
            when (val r = repo.sendOtp(account, amount, s.mode)) {
                is NetworkResult.Success -> {
                    _state.update { it.copy(busy = false, otpOpen = true, otp = "", otpError = null, otpUiError = null, otpTo = r.data ?: it.otpTo) }
                    startTimer()
                }
                is NetworkResult.Failure -> _state.update { it.copy(busy = false, formError = r.toUiError()) }
            }
        }
    }

    fun resendOtp() {
        val s = _state.value
        if (s.resendIn > 0 || s.busy) return
        val account = s.account ?: return
        val amount = s.amount ?: return
        _state.update { it.copy(busy = true, otpUiError = null) }
        viewModelScope.launch {
            val r = repo.sendOtp(account, amount, s.mode)
            _state.update { it.copy(busy = false, otpUiError = (r as? NetworkResult.Failure)?.toUiError()) }
            if (r is NetworkResult.Success) startTimer()
        }
    }

    /** After the permission prompt or the location settings: carry on if it's now possible. */
    fun onLocationChanged(granted: Boolean = location.hasPermission()) {
        if (!granted) { _state.update { it.copy(location = LocationNeed.Permission) }; return }
        _state.update { it.copy(location = null) }
        proceed()
    }

    fun dismissLocation() = _state.update { it.copy(location = null) }

    fun onOtp(v: String) = _state.update { it.copy(otp = v.filter(Char::isDigit).take(OTP_LENGTH), otpError = null) }
    fun closeOtp() { timer?.cancel(); _state.update { it.copy(otpOpen = false, otp = "", resendIn = 0) } }

    /** 4.22 → 4.23: moves the money. Never retried automatically. */
    fun confirm() {
        val s = _state.value
        if (s.otp.length != OTP_LENGTH || s.phase != SendPhase.Form) return
        val account = s.account ?: return
        val amount = s.amount ?: return
        _state.update { it.copy(phase = SendPhase.Sending, otpOpen = false) }
        viewModelScope.launch {
            val started = System.nanoTime()
            // A real fix or nothing is sent: no made-up coordinates.
            val at = location.current()
            if (at == null) {
                _state.update { it.copy(phase = SendPhase.Form, otpOpen = true, location = if (location.isEnabled()) LocationNeed.NoFix else LocationNeed.TurnOn) }
                return@launch
            }
            val k = key ?: IdempotencyKey.new().also { key = it }
            val out = repo.sendMoney(account, amount, s.mode, s.otp, k, at).stamp()
            when {
                out is SendOutcome.Failed && out.reason?.contains("otp", ignoreCase = true) == true -> {
                    key = null // the server refused before moving money
                    _state.update { it.copy(phase = SendPhase.Form, otpOpen = true, otp = "", otpError = out.reason) }
                }
                else -> {
                    if (out is SendOutcome.Failed) key = null
                    timer?.cancel()
                    // 4.23 stays up at least ~2 s so an instant answer doesn't flash past.
                    val left = MIN_PROCESSING_MS - (System.nanoTime() - started) / 1_000_000
                    if (left > 0) delay(left)
                    _state.update { it.copy(phase = SendPhase.Result, outcome = out) }
                }
            }
        }
    }

    private fun SendOutcome.stamp(): SendOutcome {
        val t = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH).format(now())
        return when (this) {
            is SendOutcome.Sent -> SendOutcome.Sent(receipt.copy(time = t))
            is SendOutcome.Pending -> SendOutcome.Pending(receipt?.copy(time = t))
            is SendOutcome.Failed -> this
        }
    }

    fun again() { key = null; _state.update { it.copy(phase = SendPhase.Form, outcome = null, otp = "", amountText = "", showErrors = false) }; load() }

    private fun startTimer() {
        timer?.cancel()
        timer = viewModelScope.launch { for (s in RESEND_SECONDS downTo 0) { _state.update { it.copy(resendIn = s) }; if (s > 0) delay(1_000) } }
    }

    companion object {
        const val OTP_LENGTH = 4
        const val RESEND_SECONDS = 30
    }
}

// ---------------------------------------------------------------- 4.27, 4.32 Bank accounts

data class BanksState(
    val loading: Boolean = true,
    val accounts: List<BankAccount> = emptyList(),
    val error: UiError? = null,
    val deleting: BankAccount? = null,
    val busy: Boolean = false,
    val actionError: UiError? = null,
)

@HiltViewModel
class BankAccountsViewModel @Inject constructor(private val repo: WalletRepository) : ViewModel() {
    private val _state = MutableStateFlow(BanksState())
    val state: StateFlow<BanksState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.bankAccounts()) {
                is NetworkResult.Success -> _state.update { it.copy(loading = false, accounts = r.data) }
                is NetworkResult.Failure -> _state.update { it.copy(loading = false, error = r.toUiError()) }
            }
        }
    }

    fun askDelete(a: BankAccount) = _state.update { it.copy(deleting = a, actionError = null) }
    fun cancelDelete() = _state.update { it.copy(deleting = null) }

    fun delete() {
        val a = _state.value.deleting ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = repo.deleteBank(a)
            _state.update { it.copy(busy = false, deleting = null, actionError = (r as? NetworkResult.Failure)?.toUiError()) }
            if (r is NetworkResult.Success) load()
        }
    }
}

// ---------------------------------------------------------------- 4.28–4.31 Add bank account

enum class DocKind { Pan, Passbook }

data class AddBankState(
    val form: AddBankForm = AddBankForm(),
    val options: List<BankOption> = emptyList(),
    val optionsLoading: Boolean = false,
    val optionsError: UiError? = null,
    val showErrors: Boolean = false,
    val busy: Boolean = false,
    val error: UiError? = null,
    val photoError: DocKind? = null,
    val added: BankAccount? = null,
)

@HiltViewModel
class AddBankViewModel @Inject constructor(private val repo: WalletRepository) : ViewModel() {
    private val _state = MutableStateFlow(AddBankState())
    val state: StateFlow<AddBankState> = _state.asStateFlow()
    /** Compressed photos, in memory only; never in saved state or on disk after compression. */
    private var pan: ByteArray? = null
    private var passbook: ByteArray? = null

    init { loadOptions() }

    fun loadOptions() {
        if (_state.value.options.isNotEmpty() || _state.value.optionsLoading) return
        _state.update { it.copy(optionsLoading = true, optionsError = null) }
        viewModelScope.launch {
            when (val r = repo.bankOptions()) {
                is NetworkResult.Success -> _state.update { it.copy(optionsLoading = false, options = r.data) }
                is NetworkResult.Failure -> _state.update { it.copy(optionsLoading = false, optionsError = r.toUiError()) }
            }
        }
    }

    private fun edit(f: (AddBankForm) -> AddBankForm) = _state.update { it.copy(form = f(it.form), error = null) }
    fun onBank(b: BankOption) = edit { it.copy(bank = b) }
    fun onAccount(v: String) = edit { it.copy(account = v.filter(Char::isDigit).take(18)) }
    fun onConfirm(v: String) = edit { it.copy(confirm = v.filter(Char::isDigit).take(18)) }
    fun onIfsc(v: String) = edit { it.copy(ifsc = v.filter(Char::isLetterOrDigit).take(11).uppercase()) }
    fun onHolder(v: String) = edit { it.copy(holder = v.filter { c -> c.isLetter() || c == ' ' || c == '.' || c == '\'' }.take(100).uppercase()) }
    fun onOwner(o: AccountOwner) = edit { it.copy(owner = o) }

    fun onPhoto(kind: DocKind, bytes: ByteArray?) {
        if (bytes == null) { _state.update { it.copy(photoError = kind) }; return }
        when (kind) { DocKind.Pan -> pan = bytes; DocKind.Passbook -> passbook = bytes }
        _state.update { it.copy(photoError = null) }
        edit { if (kind == DocKind.Pan) it.copy(panPhoto = bytes.size) else it.copy(passbookPhoto = bytes.size) }
    }

    fun submit() {
        val s = _state.value
        if (s.busy) return
        val p = pan; val b = passbook
        if (s.form.problems().isNotEmpty() || p == null || b == null) { _state.update { it.copy(showErrors = true) }; return }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.addBank(s.form, p, b)) {
                is NetworkResult.Success -> { pan = null; passbook = null; _state.update { it.copy(busy = false, added = r.data) } }
                is NetworkResult.Failure -> _state.update { it.copy(busy = false, error = r.toUiError()) }
            }
        }
    }

    override fun onCleared() { pan = null; passbook = null }
}
