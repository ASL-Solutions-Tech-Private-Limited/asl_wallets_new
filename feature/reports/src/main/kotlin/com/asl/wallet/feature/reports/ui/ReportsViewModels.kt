package com.asl.wallet.feature.reports.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.network.UiError
import com.asl.wallet.core.network.toUiError
import com.asl.wallet.feature.reports.data.LedgerQuery
import com.asl.wallet.feature.reports.data.ReportQuery
import com.asl.wallet.feature.reports.data.ReportsRepository
import com.asl.wallet.feature.reports.data.StatementData
import com.asl.wallet.feature.reports.domain.Day
import com.asl.wallet.feature.reports.domain.LedgerEntry
import com.asl.wallet.feature.reports.domain.LedgerType
import com.asl.wallet.feature.reports.domain.Period
import com.asl.wallet.feature.reports.domain.Periods
import com.asl.wallet.feature.reports.domain.Preset
import com.asl.wallet.feature.reports.domain.ReportKind
import com.asl.wallet.feature.reports.domain.ReportPage
import com.asl.wallet.feature.reports.domain.ReportRow
import com.asl.wallet.feature.reports.domain.ReportRules
import com.asl.wallet.feature.reports.domain.RowStatus
import com.asl.wallet.feature.reports.domain.Summary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One list screen: filters, rows, totals and paging. [error] with rows still shown = "showing what loaded at [loadedAt]". */
data class ListState<T>(
    val period: Period,
    val status: RowStatus? = null,
    val ledgerType: LedgerType = LedgerType.All,
    val search: String = "",
    val items: List<T> = emptyList(),
    val summary: Summary? = null,
    val page: Int = 0,
    val canLoadMore: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: UiError? = null,
    val loadedAt: Long? = null,
) {
    val firstLoad: Boolean get() = loading && loadedAt == null
    val empty: Boolean get() = !loading && error == null && loadedAt != null && items.isEmpty()
    val filtered: Boolean get() = status != null || ledgerType != LedgerType.All || search.isNotBlank()
}

/**
 * Shared paging for the ledger and every service report. A newer request cancels the older one, so a slow
 * answer for old filters can never replace a newer list.
 */
@OptIn(FlowPreview::class)
abstract class PagedViewModel<T> : ViewModel() {
    var today: () -> Day = { Day.today() }
    var now: () -> Long = { System.currentTimeMillis() }
    protected val _list = MutableStateFlow(ListState<T>(Periods.default(Day.today())))
    val list: StateFlow<ListState<T>> = _list.asStateFlow()
    private var job: Job? = null

    protected abstract suspend fun fetch(s: ListState<T>, page: Int): NetworkResult<ReportPage<T>>

    protected fun watchSearch() {
        viewModelScope.launch {
            _list.map { it.search.trim() }.distinctUntilChanged().drop(1).debounce(ReportRules.SEARCH_DEBOUNCE_MS)
                .collect { q -> if (q.isEmpty() || q.length >= ReportRules.MIN_SEARCH) refresh() }
        }
    }

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            _list.update { it.copy(loading = true, loadingMore = false, error = null) }
            when (val r = fetch(_list.value, 1)) {
                is NetworkResult.Success -> _list.update {
                    it.copy(items = r.data.items, summary = r.data.summary, page = 1, canLoadMore = r.data.canLoadMore, loading = false, loadedAt = now())
                }
                // Keep what was on screen; the banner says it couldn't refresh.
                is NetworkResult.Failure -> _list.update { it.copy(loading = false, error = r.toUiError()) }
            }
        }
    }

    fun loadMore() {
        val s = _list.value
        if (s.loading || s.loadingMore || !s.canLoadMore || s.error != null) return
        job = viewModelScope.launch {
            _list.update { it.copy(loadingMore = true) }
            when (val r = fetch(s, s.page + 1)) {
                is NetworkResult.Success -> _list.update { cur ->
                    val seen = cur.items.toSet()
                    cur.copy(items = cur.items + r.data.items.filter { it !in seen }, page = s.page + 1, canLoadMore = r.data.canLoadMore && r.data.items.isNotEmpty(), loadingMore = false)
                }
                is NetworkResult.Failure -> _list.update { it.copy(loadingMore = false, error = r.toUiError()) }
            }
        }
    }

    fun setPeriod(p: Period) { _list.update { it.copy(period = p) }; refresh() }
    fun setPreset(p: Preset) = setPeriod(Periods.of(p, today()))
    fun onSearch(q: String) = _list.update { it.copy(search = ReportRules.cleanSearch(q)) }
    fun clearFilters() { _list.update { it.copy(status = null, ledgerType = LedgerType.All, search = "", period = Periods.default(today())) }; refresh() }
}

@HiltViewModel
class LedgerViewModel @Inject constructor(private val repo: ReportsRepository) : PagedViewModel<LedgerEntry>() {
    /** The entry opened from the list (6.3). */
    private val _entry = MutableStateFlow<EntryState?>(null)
    val entry: StateFlow<EntryState?> = _entry.asStateFlow()

    init { watchSearch(); refresh() }

    override suspend fun fetch(s: ListState<LedgerEntry>, page: Int) = repo.ledger(LedgerQuery(s.period, s.ledgerType, s.search, page))

    fun setType(t: LedgerType) { _list.update { it.copy(ledgerType = t) }; refresh() }

    /** Shows the row at once, then the server's full details. */
    fun open(e: LedgerEntry) {
        _entry.value = EntryState(e, loading = true)
        viewModelScope.launch {
            when (val r = repo.ledgerEntry(e.id)) {
                is NetworkResult.Success -> _entry.update { it?.takeIf { s -> s.entry.id == e.id }?.copy(entry = r.data, loading = false) }
                is NetworkResult.Failure -> _entry.update { it?.takeIf { s -> s.entry.id == e.id }?.copy(loading = false) }
            }
        }
    }

    fun closeEntry() { _entry.value = null }
}

data class EntryState(val entry: LedgerEntry, val loading: Boolean)

sealed interface ComplaintState {
    data object Editing : ComplaintState
    data object Sending : ComplaintState
    data class Error(val e: UiError) : ComplaintState
    data object Sent : ComplaintState
}

@HiltViewModel
class ReportViewModel @Inject constructor(private val repo: ReportsRepository, handle: SavedStateHandle) : PagedViewModel<ReportRow>() {
    val kind: ReportKind = runCatching { ReportKind.valueOf(handle.get<String>("kind").orEmpty()) }.getOrDefault(ReportKind.Recharge)

    private val _selected = MutableStateFlow<ReportRow?>(null)
    val selected: StateFlow<ReportRow?> = _selected.asStateFlow()
    private val _complaint = MutableStateFlow<ComplaintState?>(null)
    val complaint: StateFlow<ComplaintState?> = _complaint.asStateFlow()
    private val _complaintText = MutableStateFlow("")
    val complaintText: StateFlow<String> = _complaintText.asStateFlow()

    init { watchSearch(); refresh() }

    override suspend fun fetch(s: ListState<ReportRow>, page: Int) = repo.report(kind, ReportQuery(s.period, s.status, s.search, page))

    fun setStatus(st: RowStatus?) { _list.update { it.copy(status = st) }; refresh() }
    fun applyFilters(p: Period, st: RowStatus?) { _list.update { it.copy(period = p, status = st) }; refresh() }

    fun open(row: ReportRow) { _selected.value = row }
    fun close() { _selected.value = null; _complaint.value = null }

    fun startComplaint() { if (_selected.value?.canComplain == true) { _complaintText.value = ""; _complaint.value = ComplaintState.Editing } }
    fun onComplaintText(t: String) { _complaintText.value = ReportRules.cleanComplaint(t, kind.complaintMax) }
    fun cancelComplaint() { if (_complaint.value != ComplaintState.Sending) _complaint.value = null }

    /** Sent once: the button locks while sending, and a sent complaint shows on the row at once. */
    fun sendComplaint() {
        val row = _selected.value ?: return
        val text = _complaintText.value.trim()
        if (text.isEmpty() || _complaint.value == ComplaintState.Sending || !row.canComplain) return
        _complaint.value = ComplaintState.Sending
        viewModelScope.launch {
            when (val r = repo.complain(row, text)) {
                is NetworkResult.Success -> {
                    val updated = row.copy(complaint = com.asl.wallet.feature.reports.domain.Complaint(text, today().label(), null))
                    _selected.value = updated
                    _list.update { s -> s.copy(items = s.items.map { if (it.txnId == row.txnId) updated else it }) }
                    _complaint.value = ComplaintState.Sent
                }
                is NetworkResult.Failure -> _complaint.value = ComplaintState.Error(r.toUiError())
            }
        }
    }

    fun doneComplaint() { _complaint.value = null }
}

/** 6.1: which tiles to show, and this month's money in / out for the ledger card. */
data class HomeState(val kinds: Set<ReportKind> = emptySet(), val moneyIn: java.math.BigDecimal? = null, val moneyOut: java.math.BigDecimal? = null, val loading: Boolean = true)

@HiltViewModel
class ReportsHomeViewModel @Inject constructor(private val repo: ReportsRepository) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()
    var today: () -> Day = { Day.today() }

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val kinds = repo.kinds()
            val totals = (repo.ledger(LedgerQuery(Periods.default(today()), limit = 1)) as? NetworkResult.Success)?.data?.summary?.totals.orEmpty().toMap()
            _state.value = HomeState(kinds, totals[com.asl.wallet.feature.reports.domain.TotalKind.MoneyIn], totals[com.asl.wallet.feature.reports.domain.TotalKind.MoneyOut], loading = false)
        }
    }
}

sealed interface StatementState {
    data object Idle : StatementState
    data object Loading : StatementState
    data class Ready(val period: Period, val data: StatementData) : StatementState
    data class Error(val e: UiError) : StatementState
}

@HiltViewModel
class StatementViewModel @Inject constructor(private val repo: ReportsRepository) : ViewModel() {
    var today: () -> Day = { Day.today() }
    private val _period = MutableStateFlow(Periods.of(Preset.LastMonth, Day.today()))
    val period: StateFlow<Period> = _period.asStateFlow()
    private val _state = MutableStateFlow<StatementState>(StatementState.Idle)
    val state: StateFlow<StatementState> = _state.asStateFlow()

    fun setPreset(p: Preset) { _period.value = Periods.of(p, today()); _state.value = StatementState.Idle }
    fun setCustom(from: Day, to: Day) { _period.value = Period(from, to, Preset.Custom); _state.value = StatementState.Idle }

    /** Last 3 months = from the same day 3 months ago until today. */
    fun lastThreeMonths() { val t = today(); _period.value = Period(t.minusMonths(Periods.MAX_MONTHS).plusDays(1), t, Preset.Custom); _state.value = StatementState.Idle }

    fun make() {
        if (_state.value == StatementState.Loading) return
        val p = _period.value
        if (Periods.check(p.from, p.to, today(), lookbackLimited = false) != Periods.Check.Ok) return
        _state.value = StatementState.Loading
        viewModelScope.launch {
            _state.value = when (val r = repo.statement(p)) {
                is NetworkResult.Success -> StatementState.Ready(p, r.data)
                is NetworkResult.Failure -> StatementState.Error(r.toUiError())
            }
        }
    }

    fun reset() { _state.value = StatementState.Idle }
}
