package com.asl.wallet.feature.recharge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asl.wallet.core.common.dispatch.atLeast
import com.asl.wallet.core.common.session.IdempotencyKey
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.network.UiError
import com.asl.wallet.core.network.toUiError
import com.asl.wallet.feature.recharge.data.RechargeInput
import com.asl.wallet.feature.recharge.data.RechargeRepository
import com.asl.wallet.feature.recharge.domain.DthCustomer
import com.asl.wallet.feature.recharge.domain.Operator
import com.asl.wallet.feature.recharge.domain.OperatorLists
import com.asl.wallet.feature.recharge.domain.Plan
import com.asl.wallet.feature.recharge.domain.PlanGroup
import com.asl.wallet.feature.recharge.domain.RecentRecharge
import com.asl.wallet.feature.recharge.domain.RechargeOutcome
import com.asl.wallet.feature.recharge.domain.RechargeRules
import com.asl.wallet.feature.recharge.domain.RechargeType
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

/** Per-flavor switches, supplied by the app module. */
data class RechargeConfig(
    /** false until the backend shares the DTH customer-info API: "Get details" then says it opens soon. */
    val dthInfoEnabled: Boolean,
)

enum class Page { Form, Plans, Review, Processing, Result }

sealed interface Detect {
    data object Idle : Detect
    data object Loading : Detect
    data class Found(val circle: String?) : Detect
    data object NotFound : Detect
    data class Error(val e: UiError) : Detect
}

sealed interface DthInfo {
    data object Idle : DthInfo
    data object Loading : DthInfo
    data class Found(val c: DthCustomer) : DthInfo
    /** No customer for this ID; [reason] is the operator's own words when safe (e.g. "Customer Id Starts with 0 and is 11 digits long"). */
    data class NotFound(val reason: String?) : DthInfo
    /** The operator's lookup is down; recharge still works. */
    data object Unavailable : DthInfo
    /** The API isn't live yet. */
    data object Soon : DthInfo
}

data class RechargeState(
    val type: RechargeType = RechargeType.Mobile,
    val page: Page = Page.Form,
    val number: String = "",
    val detect: Detect = Detect.Idle,
    val operator: Operator? = null,
    val lists: OperatorLists? = null,
    val listsLoading: Boolean = false,
    val listsError: UiError? = null,
    val amountText: String = "",
    val plan: Plan? = null,
    val plans: List<PlanGroup> = emptyList(),
    val plansLoading: Boolean = false,
    val plansError: UiError? = null,
    val planQuery: String = "",
    val planGroup: Int = 0,
    val dth: DthInfo = DthInfo.Idle,
    val customerName: String = "",
    val balance: BigDecimal? = null,
    val balanceLoading: Boolean = false,
    val balanceError: UiError? = null,
    val recent: List<RecentRecharge> = emptyList(),
    val showErrors: Boolean = false,
    val outcome: RechargeOutcome? = null,
) {
    val numberOk: Boolean get() = RechargeRules.isNumberOk(type, number)
    val amount: Int? get() = RechargeRules.amountOf(amountText)
    val shortBy: BigDecimal? get() {
        val a = amount ?: return null; val b = balance ?: return null
        return BigDecimal(a).subtract(b).takeIf { it.signum() > 0 }
    }
    val dthAvailable: Boolean get() = lists == null || lists.dth.isNotEmpty()
    val visiblePlans: List<Plan> get() =
        if (planQuery.isBlank()) plans.getOrNull(planGroup)?.plans.orEmpty() else RechargeRules.search(plans.flatMap { it.plans }, planQuery).sortedBy { it.amount }
}

@HiltViewModel
class RechargeViewModel @Inject constructor(
    private val repo: RechargeRepository,
    private val config: RechargeConfig,
) : ViewModel() {
    private val _state = MutableStateFlow(RechargeState())
    val state: StateFlow<RechargeState> = _state.asStateFlow()
    private var detectJob: Job? = null
    /** One key per confirmed recharge: made on Review, dropped only when the retailer starts a new recharge. */
    private var key: IdempotencyKey? = null
    var now: () -> Date = { Date() }

    init {
        viewModelScope.launch { (repo.recent() as? NetworkResult.Success)?.let { r -> _state.update { it.copy(recent = r.data) } } }
    }

    fun start(type: RechargeType) {
        if (_state.value.type != type) setType(type)
        if (type == RechargeType.Dth) loadOperators()
    }

    fun setType(t: RechargeType) {
        detectJob?.cancel()
        _state.update { RechargeState(type = t, lists = it.lists, recent = it.recent) }
        if (t == RechargeType.Dth) loadOperators()
    }

    fun loadOperators() {
        val s = _state.value
        if (s.listsLoading || (s.lists != null && (s.lists.mobile.isNotEmpty() || s.lists.dth.isNotEmpty()))) return
        _state.update { it.copy(listsLoading = true, listsError = null) }
        viewModelScope.launch {
            when (val r = repo.operators()) {
                is NetworkResult.Success -> _state.update { it.copy(listsLoading = false, lists = r.data) }
                is NetworkResult.Failure -> _state.update { it.copy(listsLoading = false, listsError = r.toUiError()) }
            }
        }
    }

    fun onNumber(raw: String) {
        val s = _state.value
        val v = if (s.type == RechargeType.Mobile) RechargeRules.cleanMobile(raw) else raw.filter(Char::isLetterOrDigit).take(20)
        if (v == s.number) return
        detectJob?.cancel()
        _state.update {
            it.copy(number = v, showErrors = false, dth = DthInfo.Idle,
                operator = if (it.type == RechargeType.Mobile) null else it.operator,
                detect = if (it.type == RechargeType.Mobile) Detect.Idle else it.detect,
                plan = if (it.type == RechargeType.Mobile) null else it.plan)
        }
        if (s.type == RechargeType.Mobile && RechargeRules.isNumberOk(RechargeType.Mobile, v)) detect(v)
    }

    private fun detect(mobile: String) {
        _state.update { it.copy(detect = Detect.Loading) }
        detectJob = viewModelScope.launch {
            delay(DETECT_DEBOUNCE_MS)
            val r = repo.detect(mobile)
            _state.update {
                if (it.number != mobile) it
                else when (r) {
                    is NetworkResult.Success -> r.data?.let { d -> it.copy(detect = Detect.Found(d.circle), operator = d.operator) } ?: it.copy(detect = Detect.NotFound)
                    is NetworkResult.Failure -> it.copy(detect = Detect.Error(r.toUiError()))
                }
            }
        }
    }

    fun retryDetect() { _state.value.number.takeIf { RechargeRules.isNumberOk(RechargeType.Mobile, it) }?.let(::detect) }

    /** Change operator (ported numbers) or DTH operator. */
    fun chooseOperator(op: Operator) {
        detectJob?.cancel()
        _state.update {
            it.copy(operator = op, plan = if (it.operator?.code == op.code) it.plan else null, plans = if (it.operator?.code == op.code) it.plans else emptyList(),
                dth = DthInfo.Idle, detect = if (it.type == RechargeType.Mobile && it.detect !is Detect.Found) Detect.Found(null) else it.detect)
        }
    }

    fun onAmount(v: String) {
        val t = v.filter(Char::isDigit).take(5).trimStart('0')
        _state.update { it.copy(amountText = t, plan = it.plan?.takeIf { p -> p.amount.toString() == t }) }
    }

    fun onCustomerName(v: String) = _state.update { it.copy(customerName = v.filter { c -> c.isLetter() || c == ' ' || c == '.' }.take(60)) }

    // ------------------------------------------------------------ plans

    fun openPlans() {
        val op = _state.value.operator ?: return
        _state.update { it.copy(page = Page.Plans, planQuery = "") }
        if (_state.value.plans.isNotEmpty()) return
        _state.update { it.copy(plansLoading = true, plansError = null) }
        viewModelScope.launch {
            when (val r = repo.plans(op)) {
                is NetworkResult.Success -> _state.update { it.copy(plansLoading = false, plans = r.data, planGroup = 0) }
                is NetworkResult.Failure -> _state.update { it.copy(plansLoading = false, plansError = r.toUiError()) }
            }
        }
    }

    fun retryPlans() { _state.update { it.copy(plans = emptyList()) }; openPlans() }
    fun onPlanQuery(q: String) = _state.update { it.copy(planQuery = q.take(30)) }
    fun onPlanGroup(i: Int) = _state.update { it.copy(planGroup = i, planQuery = "") }
    fun pickPlan(p: Plan) = _state.update { it.copy(plan = p, amountText = p.amount.toString(), page = Page.Form) }
    fun closePlans() = _state.update { it.copy(page = Page.Form) }

    // ------------------------------------------------------------ DTH details

    fun getDthDetails() {
        val s = _state.value
        val op = s.operator ?: run { _state.update { it.copy(showErrors = true) }; return }
        if (!s.numberOk) { _state.update { it.copy(showErrors = true) }; return }
        if (!config.dthInfoEnabled) { _state.update { it.copy(dth = DthInfo.Soon) }; return }
        _state.update { it.copy(dth = DthInfo.Loading) }
        viewModelScope.launch {
            val r = repo.dthInfo(op, s.number)
            _state.update {
                if (it.number != s.number) it else when {
                    r is NetworkResult.Failure.Api && r.code == "DTH_DOWN" -> it.copy(dth = DthInfo.Unavailable)
                    r is NetworkResult.Failure.Api && r.code == "DTH_ID" -> it.copy(dth = DthInfo.NotFound(r.message.ifBlank { null }))
                    r is NetworkResult.Failure -> it.copy(dth = DthInfo.Unavailable)
                    r is NetworkResult.Success && r.data != null -> {
                        val c = r.data!!
                        it.copy(dth = DthInfo.Found(c), customerName = c.name.orEmpty().take(60),
                            amountText = if (it.amountText.isEmpty()) c.monthly?.toInt()?.takeIf { m -> m in RechargeRules.MIN_AMOUNT..RechargeRules.MAX_AMOUNT }?.toString().orEmpty() else it.amountText)
                    }
                    else -> it.copy(dth = DthInfo.NotFound(null))
                }
            }
        }
    }

    // ------------------------------------------------------------ review, recharge

    /** 5.6 / 5.18: checks everything, then loads the balance for the review. Nothing is sent yet. */
    fun review() {
        val s = _state.value
        if (!s.numberOk || s.operator == null || s.amount == null) { _state.update { it.copy(showErrors = true) }; return }
        key = IdempotencyKey.new()
        _state.update { it.copy(page = Page.Review, balanceLoading = true, balanceError = null) }
        viewModelScope.launch {
            when (val r = repo.balance()) {
                is NetworkResult.Success -> _state.update { it.copy(balanceLoading = false, balance = r.data) }
                is NetworkResult.Failure -> _state.update { it.copy(balanceLoading = false, balanceError = r.toUiError()) }
            }
        }
    }

    fun backToForm() { if (_state.value.page == Page.Review) _state.update { it.copy(page = Page.Form) } }

    /** Slide to recharge. Runs once per review; a second slide can't send a second recharge. */
    fun confirm() {
        val s = _state.value
        if (s.page != Page.Review || s.balance == null || s.shortBy != null) return
        val op = s.operator ?: return
        val amount = s.amount ?: return
        val k = key ?: return
        key = null
        _state.update { it.copy(page = Page.Processing) }
        viewModelScope.launch {
            val out = atLeast { repo.recharge(RechargeInput(s.type, s.number, op, amount, s.customerName, s.plan?.let { "${it.validity} · ₹ ${it.amount}" }, k)) }.stamp()
            _state.update { it.copy(page = Page.Result, outcome = out) }
        }
    }

    private fun RechargeOutcome.stamp(): RechargeOutcome {
        val t = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH).format(now())
        return when (this) {
            is RechargeOutcome.Done -> RechargeOutcome.Done(receipt.copy(time = t))
            is RechargeOutcome.Pending -> RechargeOutcome.Pending(receipt.copy(time = t))
            is RechargeOutcome.Failed -> RechargeOutcome.Failed(receipt.copy(time = t), reason, debited)
        }
    }

    /** After a result: [keepDetails] for "Try again", otherwise a fresh recharge of the same type. */
    fun again(keepDetails: Boolean) {
        _state.update {
            if (keepDetails) it.copy(page = Page.Form, outcome = null, showErrors = false)
            else RechargeState(type = it.type, lists = it.lists, recent = it.recent)
        }
        viewModelScope.launch { (repo.recent() as? NetworkResult.Success)?.let { r -> _state.update { it.copy(recent = r.data) } } }
    }

    /** Tap on a recent recharge: same number, operator and amount. */
    fun repeat(r: RecentRecharge) {
        setType(r.type)
        onNumber(r.number)
        onAmount(r.amount.toInt().toString())
        if (r.type == RechargeType.Dth) {
            val op = _state.value.lists?.dth?.firstOrNull { it.code == r.operatorCode } ?: r.operatorCode?.let { Operator(it, r.operator) }
            op?.let(::chooseOperator)
        }
    }

    companion object { const val DETECT_DEBOUNCE_MS = 300L }
}
