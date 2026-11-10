package com.asl.wallet.feature.bbps.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asl.wallet.core.common.session.IdempotencyKey
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.network.UiError
import com.asl.wallet.core.network.toUiError
import com.asl.wallet.feature.bbps.data.BbpsRepository
import com.asl.wallet.feature.bbps.domain.BbpsOutcome
import com.asl.wallet.feature.bbps.domain.BbpsRules
import com.asl.wallet.feature.bbps.domain.BillCategory
import com.asl.wallet.feature.bbps.domain.BillPayment
import com.asl.wallet.feature.bbps.domain.Biller
import com.asl.wallet.feature.bbps.domain.BillerInfo
import com.asl.wallet.feature.bbps.domain.BillerParam
import com.asl.wallet.feature.bbps.domain.FetchedBill
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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

enum class BbpsPage { Categories, Billers, Details, Bill, Result }

data class BbpsState(
    val page: BbpsPage = BbpsPage.Categories,
    /** Opened straight into a category from Home: Back from its biller list leaves BBPS. */
    val direct: Boolean = false,
    val categories: List<BillCategory> = emptyList(),
    val categoriesLoading: Boolean = false,
    val category: String? = null,
    val billers: List<Biller> = emptyList(),
    val billersLoading: Boolean = false,
    val query: String = "",
    val biller: Biller? = null,
    val info: BillerInfo? = null,
    val infoLoading: Boolean = false,
    /** Typed values by parameter name. */
    val values: Map<String, String> = emptyMap(),
    val showErrors: Boolean = false,
    val fetching: Boolean = false,
    val bill: FetchedBill? = null,
    val amountText: String = "",
    val amountError: Boolean = false,
    val balance: BigDecimal? = null,
    val confirm: Boolean = false,
    val lowBalance: Boolean = false,
    val paying: Boolean = false,
    val outcome: BbpsOutcome? = null,
    val error: UiError? = null,
) {
    val shownBillers: List<Biller> get() = BbpsRules.search(billers, query)
    val amount: BigDecimal? get() = BbpsRules.amountOf(amountText)
    val amountEditable: Boolean get() = bill?.let { b -> BbpsRules.amountEditable(category ?: biller?.category.orEmpty(), b.amount) } ?: false

    fun problems(): Map<String, BillerParam.Check> =
        info?.params.orEmpty().associate { it.name to it.check(values[it.name].orEmpty()) }.filterValues { it != BillerParam.Check.Ok }
}

@HiltViewModel
class BbpsViewModel @Inject constructor(private val repo: BbpsRepository) : ViewModel() {
    private val _state = MutableStateFlow(BbpsState())
    val state: StateFlow<BbpsState> = _state.asStateFlow()
    private var started = false
    private var job: Job? = null

    /** [category] from a Home tile (Electricity, Fastag …), or null for all categories. */
    fun start(category: String?) {
        if (started) return
        started = true
        if (category != null) {
            _state.update { it.copy(direct = true) }
            openCategory(category)
        } else loadCategories()
    }

    fun loadCategories() {
        _state.update { it.copy(page = BbpsPage.Categories, categoriesLoading = true, error = null) }
        viewModelScope.launch {
            val r = repo.categories()
            _state.update { it.copy(categoriesLoading = false, categories = (r as? NetworkResult.Success)?.data.orEmpty(), error = (r as? NetworkResult.Failure)?.toUiError()) }
        }
    }

    fun openCategory(name: String) {
        _state.update { it.copy(page = BbpsPage.Billers, category = name, billers = emptyList(), query = "", billersLoading = true, error = null) }
        job?.cancel()
        job = viewModelScope.launch {
            val r = repo.billers(name)
            _state.update { it.copy(billersLoading = false, billers = (r as? NetworkResult.Success)?.data.orEmpty(), error = (r as? NetworkResult.Failure)?.toUiError()) }
        }
    }

    fun retryBillers() { _state.value.category?.let(::openCategory) }

    fun onQuery(q: String) = _state.update { it.copy(query = q.take(40)) }

    fun openBiller(b: Biller) {
        _state.update { it.copy(page = BbpsPage.Details, biller = b, info = null, values = emptyMap(), showErrors = false, bill = null, infoLoading = true, error = null) }
        job?.cancel()
        job = viewModelScope.launch {
            val r = repo.billerInfo(b)
            _state.update { it.copy(infoLoading = false, info = (r as? NetworkResult.Success)?.data, error = (r as? NetworkResult.Failure)?.toUiError()) }
        }
    }

    fun retryInfo() { _state.value.biller?.let(::openBiller) }

    fun onValue(p: BillerParam, raw: String) {
        val v = (if (p.numeric) raw.filter(Char::isDigit) else raw).let { s -> p.maxLength?.let { s.take(it) } ?: s.take(60) }
        _state.update { it.copy(values = it.values + (p.name to v), error = null) }
    }

    /** Read-only at the biller: shows the current bill. */
    fun fetch() {
        val s = _state.value
        val b = s.biller ?: return
        val info = s.info ?: return
        if (s.fetching) return
        if (s.problems().isNotEmpty()) { _state.update { it.copy(showErrors = true) }; return }
        _state.update { it.copy(fetching = true, error = null) }
        viewModelScope.launch {
            val inputs = info.params.map { it to s.values[it.name].orEmpty() }
            when (val r = repo.fetchBill(b, inputs)) {
                is NetworkResult.Success -> {
                    val bill = r.data
                    _state.update {
                        it.copy(fetching = false, page = BbpsPage.Bill, bill = bill, amountError = false,
                            amountText = bill.amount.takeIf { a -> a.signum() > 0 }?.stripTrailingZeros()?.toPlainString().orEmpty())
                    }
                    loadBalance()
                }
                is NetworkResult.Failure -> _state.update { it.copy(fetching = false, error = r.toUiError()) }
            }
        }
    }

    private fun loadBalance() = viewModelScope.launch {
        (repo.balance() as? NetworkResult.Success)?.data?.let { b -> _state.update { it.copy(balance = b) } }
    }

    fun onAmount(v: String) {
        if (!_state.value.amountEditable) return
        val clean = v.filter { it.isDigit() || it == '.' }.let { s -> if (s.count { it == '.' } > 1) s.dropLast(1) else s }.take(9)
        _state.update { it.copy(amountText = clean, amountError = false) }
    }

    fun pickOption(a: BigDecimal) { if (_state.value.amountEditable) _state.update { it.copy(amountText = a.stripTrailingZeros().toPlainString(), amountError = false) } }

    fun askPay() {
        val s = _state.value
        val a = s.amount
        if (a == null) { _state.update { it.copy(amountError = true) }; return }
        if (s.balance != null && a > s.balance) { _state.update { it.copy(lowBalance = true) }; return }
        _state.update { it.copy(confirm = true) }
    }

    fun dismissDialogs() = _state.update { it.copy(confirm = false, lowBalance = false) }

    /** Moves money: one key per confirmation, never retried. */
    fun pay() {
        val s = _state.value
        if (s.paying || !s.confirm) return
        val b = s.biller ?: return
        val bill = s.bill ?: return
        val info = s.info ?: return
        val amount = s.amount ?: return
        val p = BillPayment(b, info.params.map { it to s.values[it.name].orEmpty() }, bill, amount, IdempotencyKey.new())
        _state.update { it.copy(confirm = false, paying = true) }
        viewModelScope.launch {
            val now = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH).format(Date())
            val o = when (val r = repo.pay(p)) {
                is BbpsOutcome.Paid -> r.copy(receipt = r.receipt.copy(time = now))
                is BbpsOutcome.Pending -> r.copy(receipt = r.receipt.copy(time = now))
                is BbpsOutcome.Failed -> r.copy(receipt = r.receipt.copy(time = now))
            }
            _state.update { it.copy(paying = false, outcome = o, page = BbpsPage.Result) }
        }
    }

    /** Back inside BBPS; false = leave BBPS. */
    fun back(): Boolean {
        val s = _state.value
        if (s.paying) return true
        when (s.page) {
            BbpsPage.Categories -> return false
            BbpsPage.Billers -> {
                if (s.direct) return false
                if (s.categories.isEmpty()) loadCategories() else _state.update { it.copy(page = BbpsPage.Categories, error = null) }
            }
            BbpsPage.Details -> { job?.cancel(); _state.update { it.copy(page = BbpsPage.Billers, error = null, fetching = false) } }
            BbpsPage.Bill -> _state.update { it.copy(page = BbpsPage.Details, bill = null, error = null) }
            BbpsPage.Result -> return false
        }
        return true
    }

    /** After a result: pay another bill of the same biller ([sameBiller]) or pick another one. */
    fun again(sameBiller: Boolean) {
        _state.update { it.copy(outcome = null, bill = null, amountText = "", error = null, showErrors = false) }
        val b = _state.value.biller
        if (sameBiller && b != null) { _state.update { it.copy(values = emptyMap()) }; openBiller(b) } else _state.update { it.copy(page = BbpsPage.Billers) }
    }
}
