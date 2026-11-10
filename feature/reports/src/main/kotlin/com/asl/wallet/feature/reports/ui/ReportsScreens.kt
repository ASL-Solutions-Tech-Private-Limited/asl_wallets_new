package com.asl.wallet.feature.reports.ui

import com.asl.wallet.core.designsystem.component.keyboardAware
import androidx.compose.foundation.layout.imePadding
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asl.wallet.core.common.format.AmountInWords
import com.asl.wallet.core.common.format.Money
import com.asl.wallet.core.designsystem.component.AlertBanner
import com.asl.wallet.core.designsystem.component.AlertKind
import com.asl.wallet.core.designsystem.component.AslButton
import com.asl.wallet.core.designsystem.component.AslButtonStyle
import com.asl.wallet.core.designsystem.component.AslCard
import com.asl.wallet.core.designsystem.component.AslPrimaryButton
import com.asl.wallet.core.designsystem.component.AslTextField
import com.asl.wallet.core.designsystem.component.AslTopBar
import com.asl.wallet.core.designsystem.component.ChoiceChips
import com.asl.wallet.core.designsystem.component.KeyValue
import com.asl.wallet.core.designsystem.component.KeyValueList
import com.asl.wallet.core.designsystem.component.ReceiptCard
import com.asl.wallet.core.designsystem.component.SectionHeader
import com.asl.wallet.core.designsystem.component.ServiceArt
import com.asl.wallet.core.designsystem.component.ServiceGrid
import com.asl.wallet.core.designsystem.R as DS
import com.asl.wallet.core.designsystem.component.ServiceItem
import com.asl.wallet.core.designsystem.component.ShopInfo
import com.asl.wallet.core.designsystem.component.SkeletonRow
import com.asl.wallet.core.designsystem.component.StatePresets
import com.asl.wallet.core.designsystem.component.StateView
import com.asl.wallet.core.designsystem.component.Status
import com.asl.wallet.core.designsystem.component.TransactionRow
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.receipt.ReceiptDoc
import com.asl.wallet.core.designsystem.receipt.ReceiptExport
import com.asl.wallet.core.designsystem.receipt.StatementDoc
import com.asl.wallet.core.designsystem.theme.AslGradients
import com.asl.wallet.core.designsystem.theme.AslShapes
import com.asl.wallet.core.designsystem.theme.AslTheme
import com.asl.wallet.core.network.UiError
import com.asl.wallet.feature.reports.R
import com.asl.wallet.feature.reports.domain.Day
import com.asl.wallet.feature.reports.domain.Field
import com.asl.wallet.feature.reports.domain.LedgerEntry
import com.asl.wallet.feature.reports.domain.LedgerType
import com.asl.wallet.feature.reports.domain.Period
import com.asl.wallet.feature.reports.domain.Periods
import com.asl.wallet.feature.reports.domain.Preset
import com.asl.wallet.feature.reports.domain.ReportKind
import com.asl.wallet.feature.reports.domain.ReportRow
import com.asl.wallet.feature.reports.domain.ReportRules
import com.asl.wallet.feature.reports.domain.RowStatus
import com.asl.wallet.feature.reports.domain.Summary
import com.asl.wallet.feature.reports.domain.TotalKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.text.DateFormat
import java.util.Date

// ---------------------------------------------------------------- shared bits

private fun rupees(a: BigDecimal?) = a?.let { Money.format(it) } ?: "—"

@Composable
private fun Scaffold(
    title: String, onBack: (() -> Unit)?, actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    bottom: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit,
) {
    val c = AslTheme.colors
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().keyboardAware()) {
        AslTopBar(title, onBack = onBack, actions = actions)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp)
                .then(if (bottom == null) Modifier.navigationBarsPadding() else Modifier),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
        )
        if (bottom != null) Column(Modifier.fillMaxWidth().background(c.surface).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = bottom)
    }
}

@Composable
private fun errorText(e: UiError): String = when (e) {
    is UiError.Server -> e.message
    UiError.NoInternet -> stringResource(R.string.rp_offline)
    UiError.Timeout -> stringResource(R.string.rp_timeout)
    else -> stringResource(R.string.rp_generic)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = AslTheme.colors.surface, shape = AslShapes.sheet) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun TopIcon(icon: ImageVector, label: String, onClick: () -> Unit) =
    IconButton(onClick = onClick) { Icon(icon, contentDescription = label, tint = androidx.compose.material3.LocalContentColor.current) }

@Composable
fun kindName(k: ReportKind): String = stringResource(when (k) {
    ReportKind.Recharge -> R.string.rp_kind_recharge
    ReportKind.Payout -> R.string.rp_kind_payout
    ReportKind.Aeps -> R.string.rp_kind_aeps
    ReportKind.Dmt -> R.string.rp_kind_dmt
    ReportKind.Matm -> R.string.rp_kind_matm
    ReportKind.Pan -> R.string.rp_kind_pan
    ReportKind.AddMoney -> R.string.rp_kind_add_money
})

/** Classic look: the old app's report icons. */
private fun kindArt(k: ReportKind): Int = when (k) {
    ReportKind.Recharge -> DS.drawable.classic_ic_mobile_report
    ReportKind.Payout -> DS.drawable.classic_ic_payout_report
    ReportKind.Aeps -> DS.drawable.classic_ic_aeps_report
    ReportKind.Dmt -> DS.drawable.classic_ic_dmt_report
    ReportKind.Matm -> DS.drawable.classic_ic_matm_report
    ReportKind.Pan -> DS.drawable.classic_ic_new_pan_a
    ReportKind.AddMoney -> DS.drawable.classic_ic_wallet_menu
}

private fun kindIcon(k: ReportKind): Pair<ImageVector, List<Color>> = when (k) {
    ReportKind.Recharge -> AslIcons.Phone to AslGradients.Service.Recharge.colors
    ReportKind.Payout -> AslIcons.Bank to AslGradients.Service.Bills.colors
    ReportKind.Aeps -> AslIcons.Fingerprint to AslGradients.Service.Aeps.colors
    ReportKind.Dmt -> AslIcons.Send to AslGradients.Service.SendMoney.colors
    ReportKind.Matm -> AslIcons.Atm to AslGradients.Service.MicroAtm.colors
    ReportKind.Pan -> AslIcons.Document to AslGradients.Service.Wallet.colors
    ReportKind.AddMoney -> AslIcons.Plus to AslGradients.success
}

@Composable
private fun statusWord(s: RowStatus) = stringResource(when (s) {
    RowStatus.Success -> R.string.rp_success; RowStatus.Pending -> R.string.rp_pending
    RowStatus.Failed -> R.string.rp_failed; RowStatus.Refunded -> R.string.rp_refunded
})

private fun RowStatus.pill() = when (this) { RowStatus.Success -> Status.Success; RowStatus.Pending -> Status.Pending; else -> Status.Failed }

@Composable
private fun fieldName(f: Field) = stringResource(when (f) {
    Field.Operator -> R.string.rp_f_operator; Field.Number -> R.string.rp_f_number; Field.SubscriberId -> R.string.rp_f_subscriber
    Field.OperatorRef -> R.string.rp_f_operator_ref; Field.Customer -> R.string.rp_f_customer; Field.Recipient -> R.string.rp_f_recipient
    Field.Bank -> R.string.rp_f_bank; Field.Account -> R.string.rp_f_account; Field.Ifsc -> R.string.rp_f_ifsc; Field.Mode -> R.string.rp_f_mode
    Field.Charge -> R.string.rp_f_charge; Field.Utr -> R.string.rp_f_utr; Field.OrderId -> R.string.rp_f_order; Field.Type -> R.string.rp_f_type
    Field.Aadhaar -> R.string.rp_f_aadhaar; Field.Rrn -> R.string.rp_f_rrn; Field.CustomerBalance -> R.string.rp_f_balance; Field.Card -> R.string.rp_f_card
    Field.Mobile -> R.string.rp_f_mobile; Field.Applicant -> R.string.rp_f_applicant; Field.PanType -> R.string.rp_f_pan_type
    Field.PanMode -> R.string.rp_f_pan_mode; Field.ApplicationNo -> R.string.rp_f_application; Field.Refund -> R.string.rp_f_refund
    Field.TxnId -> R.string.rp_f_txn; Field.Date -> R.string.rp_f_date; Field.Commission -> R.string.rp_f_commission; Field.Reason -> R.string.rp_f_reason
})

@Composable
private fun presetName(p: Preset) = stringResource(when (p) {
    Preset.Today -> R.string.rp_p_today; Preset.Yesterday -> R.string.rp_p_yesterday; Preset.Last7 -> R.string.rp_p_7
    Preset.ThisMonth -> R.string.rp_p_this_month; Preset.LastMonth -> R.string.rp_p_last_month; Preset.Custom -> R.string.rp_p_custom
})

private fun whenText(d: Day?, time: String?, today: Day): String =
    listOfNotNull(d?.let { if (it == today) null else it.shortLabel() }, time).joinToString(", ").ifEmpty { d?.shortLabel() ?: "" }

/** Period chip (opens filters) with the count / totals note on the right. */
@Composable
private fun PeriodRow(period: Period, note: String?, onClick: () -> Unit) {
    val c = AslTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(RoundedCornerShape(11.dp)).background(c.surface).border(1.dp, c.outline, RoundedCornerShape(11.dp))
                .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(AslIcons.Calendar, null, tint = c.textPrimary, modifier = Modifier.size(14.dp))
            Text(period.label(), style = AslTheme.type.label, color = c.textPrimary)
        }
        Spacer(Modifier.weight(1f))
        if (note != null) Text(note, style = AslTheme.type.caption.copy(fontWeight = AslTheme.type.label.fontWeight), color = c.textSecondary)
    }
}

@Composable
private fun TotalTiles(tiles: List<Triple<String, String, List<Color>>>) {
    if (tiles.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.forEach { (value, label, colors) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(colors)).padding(10.dp)) {
                Text(value, style = AslTheme.type.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, style = AslTheme.type.micro, color = Color.White.copy(alpha = 0.9f))
            }
        }
    }
}

@Composable
private fun RefreshBanner(error: UiError?, loadedAt: Long?, hasItems: Boolean, onRetry: () -> Unit) {
    if (error == null) return
    val at = loadedAt?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) }
    if (hasItems && at != null) {
        AlertBanner(AlertKind.Error, stringResource(R.string.rp_cant_refresh), message = stringResource(R.string.rp_showing_from, at) + " " + errorText(error), icon = AslIcons.WifiOff)
    } else {
        AlertBanner(AlertKind.Error, stringResource(R.string.rp_cant_refresh), message = errorText(error), icon = AslIcons.WifiOff)
        AslButton(stringResource(R.string.rp_try_again), onRetry, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline, icon = AslIcons.Refresh)
    }
}

@Composable
private fun EmptyState(icon: ImageVector, colors: List<Color>, filtered: Boolean, onThisMonth: () -> Unit, onClear: () -> Unit) {
    StateView(icon, colors, stringResource(R.string.rp_empty_title), stringResource(R.string.rp_empty_msg)) {
        AslPrimaryButton(stringResource(R.string.rp_empty_this_month), onThisMonth)
        if (filtered) AslButton(stringResource(R.string.rp_clear_filters), onClear, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline)
    }
}

/** Paged list with pull-to-refresh; asks for the next page near the end. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> PagedList(
    state: ListState<T>, onRefresh: () -> Unit, onMore: () -> Unit,
    header: LazyListScope.() -> Unit, rows: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= listState.layoutInfo.totalItemsCount - 4 } == true } }
    LaunchedEffect(nearEnd, state.items.size, state.canLoadMore) { if (nearEnd && state.canLoadMore) onMore() }
    PullToRefreshBox(isRefreshing = state.loading && !state.firstLoad, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(), state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            header()
            if (state.firstLoad) items(6) { SkeletonRow(Modifier.padding(vertical = 6.dp)) } else rows()
            if (state.loadingMore) item {
                Text(stringResource(R.string.rp_loading_more), style = AslTheme.type.caption, color = AslTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(4.dp))
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun SearchBox(value: String, hint: String, onChange: (String) -> Unit) {
    val short = value.isNotBlank() && value.trim().length < ReportRules.MIN_SEARCH
    AslTextField(value, onChange, stringResource(R.string.rp_search), placeholder = hint, trailingIcon = AslIcons.Search, maxLength = 40,
        error = if (short) stringResource(R.string.rp_search_min) else null)
}

/**
 * One row of a grouped card, as its own lazy item (so paging only triggers near the real end of the list).
 * The first and last rows round their corners; rows in between get a divider on top.
 */
@Composable
private fun Cell(index: Int, count: Int, content: @Composable ColumnScope.() -> Unit) {
    val c = AslTheme.colors
    val r = 18.dp
    val shape = RoundedCornerShape(topStart = if (index == 0) r else 0.dp, topEnd = if (index == 0) r else 0.dp,
        bottomStart = if (index == count - 1) r else 0.dp, bottomEnd = if (index == count - 1) r else 0.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(c.surface).padding(horizontal = 12.dp)) {
        if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.outline))
        content()
    }
}

// ---------------------------------------------------------------- 6.1 Reports tab

/** The Reports tab inside the signed-in shell (no back button). */
@Composable
fun ReportsTab(onLedger: () -> Unit, onReport: (ReportKind) -> Unit, onFundRequests: () -> Unit, onStatement: () -> Unit, vm: ReportsHomeViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = AslTheme.colors
    LaunchedEffect(Unit) { vm.load() }
    Scaffold(stringResource(R.string.rp_title), null) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(c.heroGradient))
                .clickable(role = Role.Button, onClick = onLedger).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(42.dp).background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(AslIcons.Wallet, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.rp_ledger), style = AslTheme.type.title, color = Color.White)
                Text(
                    if (s.moneyIn != null && s.moneyOut != null) stringResource(R.string.rp_ledger_sub, rupees(s.moneyIn), rupees(s.moneyOut)) else stringResource(R.string.rp_ledger_sub_plain),
                    style = AslTheme.type.caption, color = Color.White.copy(alpha = 0.88f),
                )
            }
            Icon(AslIcons.Chevron, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        SectionHeader(stringResource(R.string.rp_service_reports))
        // Fixed order; a tile shows only if the retailer has that service (all while the list loads or fails).
        val order = listOf(ReportKind.Recharge, ReportKind.Payout, ReportKind.Aeps, ReportKind.Dmt, ReportKind.AddMoney, ReportKind.Matm, ReportKind.Pan)
        val shown = order.filter { s.loading || s.kinds.isEmpty() || it in s.kinds }
        val items = shown.map { k -> kindIcon(k).let { (i, col) -> ServiceItem(kindName(k), i, col, art = ServiceArt.Picture(kindArt(k))) } }
        ServiceGrid(items, onClick = { item -> onReport(shown[items.indexOf(item)]) })
        SectionHeader(stringResource(R.string.rp_wallet))
        val wallet = listOf(
            ServiceItem(stringResource(R.string.rp_fund_requests), AslIcons.Upload, AslGradients.Service.Upi.colors, art = ServiceArt.Picture(DS.drawable.classic_ic_fund_req)),
            ServiceItem(stringResource(R.string.rp_statement), AslIcons.Document, AslGradients.Service.Wallet.colors, art = ServiceArt.Picture(DS.drawable.classic_ic_transction_report)),
        )
        ServiceGrid(wallet, onClick = { if (it == wallet[0]) onFundRequests() else onStatement() })
    }
}

// ---------------------------------------------------------------- 6.2–6.4 Ledger

@Composable
fun LedgerScreen(shop: ShopInfo, onBack: () -> Unit, vm: LedgerViewModel = hiltViewModel()) {
    val s by vm.list.collectAsStateWithLifecycle()
    val entry by vm.entry.collectAsStateWithLifecycle()
    var filters by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val e = entry
    if (e != null) { LedgerEntryPage(e, shop, vm::closeEntry); return }
    val today = remember { Day.today() }
    val c = AslTheme.colors
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().keyboardAware()) {
        AslTopBar(stringResource(R.string.rp_ledger), onBack = onBack, actions = {
            TopIcon(AslIcons.Search, stringResource(R.string.rp_search)) { searching = !searching; if (!searching) vm.onSearch("") }
            TopIcon(AslIcons.Calendar, stringResource(R.string.rp_filter)) { filters = true }
        })
        PagedList(s, vm::refresh, vm::loadMore, header = {
            item {
                Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (searching) SearchBox(s.search, stringResource(R.string.rp_search_hint_ledger), vm::onSearch)
                    ChoiceChips(listOf(stringResource(R.string.rp_all), stringResource(R.string.rp_money_in), stringResource(R.string.rp_money_out)), s.ledgerType.ordinal, { vm.setType(LedgerType.entries[it]) })
                    PeriodRow(s.period, s.summary?.count?.let { stringResource(R.string.rp_entries, it) }) { filters = true }
                    val t = s.summary?.totals.orEmpty().toMap()
                    TotalTiles(listOfNotNull(
                        t[TotalKind.MoneyIn]?.let { Triple(rupees(it), stringResource(R.string.rp_money_in), AslGradients.success) },
                        t[TotalKind.MoneyOut]?.let { Triple(rupees(it), stringResource(R.string.rp_money_out), AslGradients.failed) },
                    ))
                    RefreshBanner(s.error, s.loadedAt, s.items.isNotEmpty(), vm::refresh)
                }
            }
        }) {
            if (s.empty) item { EmptyState(AslIcons.Wallet, StatePresets.emptyColors, s.filtered, { vm.setPreset(Preset.ThisMonth) }, vm::clearFilters) }
            ReportRules.byDay(s.items) { it.day }.forEach { (d, list) ->
                item(key = "d-${d?.iso()}-${list.first().id}") { SectionHeader(if (d == today) stringResource(R.string.rp_today) else d?.label().orEmpty(), Modifier.padding(top = 12.dp, bottom = 8.dp)) }
                list.forEachIndexed { i, en -> item(key = "e-${en.id}") { Cell(i, list.size) { LedgerRow(en) { vm.open(en) } } } }
            }
        }
    }
    if (filters) FilterSheet(s.period, null, null, lookbackLimited = false, onDismiss = { filters = false }) { p, _ -> filters = false; vm.setPeriod(p) }
}

@Composable
private fun LedgerRow(e: LedgerEntry, onClick: () -> Unit) {
    val c = AslTheme.colors
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(e.description, style = AslTheme.type.body.copy(fontWeight = AslTheme.type.label.fontWeight), color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(e.time, e.reference?.let { "Ref $it" }).joinToString(" · "), style = AslTheme.type.caption, color = c.textSecondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text((if (e.isCredit) "+ " else "− ") + rupees(e.amount), style = AslTheme.type.label, color = if (e.isCredit) c.success else c.error)
            e.closing?.let { Text(stringResource(R.string.rp_bal, rupees(it)), style = AslTheme.type.caption, color = c.textSecondary) }
        }
    }
}

@Composable
private fun LedgerEntryPage(s: EntryState, shop: ShopInfo, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val e = s.entry
    val c = AslTheme.colors
    val shareTitle = stringResource(R.string.rp_share_title)
    val rows = listOfNotNull(
        KeyValue(stringResource(R.string.rp_description), e.description),
        e.opening?.let { KeyValue(stringResource(R.string.rp_opening), rupees(it)) },
        KeyValue(stringResource(if (e.isCredit) R.string.rp_credit else R.string.rp_debit), rupees(e.amount)),
        e.closing?.let { KeyValue(stringResource(R.string.rp_closing), rupees(it), total = true) },
        e.reference?.let { KeyValue(stringResource(R.string.rp_reference), it) },
        KeyValue(stringResource(R.string.rp_entry_id), e.id.toString()),
    )
    val word = stringResource(if (e.isCredit) R.string.rp_money_in else R.string.rp_money_out)
    val doc = ReceiptDoc(stringResource(R.string.rp_entry), word, (if (e.isCredit) "+ " else "− ") + rupees(e.amount),
        rows.map { it.key to it.value } + listOfNotNull(e.day?.let { stringResource(R.string.rp_f_date) to listOfNotNull(it.label(), e.time).joinToString(", ") }),
        footer = stringResource(R.string.rp_receipt_note))
    Scaffold(stringResource(R.string.rp_entry), onBack) {
        AslCard {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(word, style = AslTheme.type.caption, color = c.textSecondary)
                Text((if (e.isCredit) "+ " else "− ") + rupees(e.amount), style = AslTheme.type.headline, color = if (e.isCredit) c.success else c.error)
                Text(listOfNotNull(e.day?.label(), e.time).joinToString(", "), style = AslTheme.type.caption, color = c.textSecondary)
            }
        }
        AslCard { KeyValueList(rows) }
        if (s.loading) SkeletonRow()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AslButton(stringResource(R.string.rp_share), { ReceiptExport.shareText(context, shareTitle, ReceiptExport.text(shop, doc)) }, Modifier.weight(1f), style = AslButtonStyle.Outline, icon = AslIcons.Share)
            AslButton(stringResource(R.string.rp_save_pdf), { ReceiptExport.sharePdf(context, shop, doc, shareTitle, "ASL-ledger-${e.id}.pdf") }, Modifier.weight(1f), style = AslButtonStyle.Outline, icon = AslIcons.Document)
        }
    }
}

// ---------------------------------------------------------------- 6.6–6.13 Service reports

@Composable
fun ReportScreen(shop: ShopInfo, onBack: () -> Unit, vm: ReportViewModel = hiltViewModel()) {
    val s by vm.list.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    var filters by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val kind = vm.kind
    val row = selected
    if (row != null) { RowDetailPage(row, shop, vm); return }
    val today = remember { Day.today() }
    val c = AslTheme.colors
    val (icon, colors) = kindIcon(kind)
    val statuses = listOf(null, RowStatus.Success, RowStatus.Pending, RowStatus.Failed)
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().keyboardAware()) {
        AslTopBar(stringResource(R.string.rp_report_title, kindName(kind)), onBack = onBack, actions = {
            TopIcon(AslIcons.Search, stringResource(R.string.rp_search)) { searching = !searching; if (!searching) vm.onSearch("") }
            TopIcon(AslIcons.Calendar, stringResource(R.string.rp_filter)) { filters = true }
        })
        PagedList(s, vm::refresh, vm::loadMore, header = {
            item {
                Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (searching) SearchBox(s.search, stringResource(when (kind) {
                        ReportKind.Recharge -> R.string.rp_search_hint_recharge; ReportKind.Payout -> R.string.rp_search_hint_payout
                        ReportKind.Aeps, ReportKind.Matm -> R.string.rp_search_hint_aeps; ReportKind.Dmt -> R.string.rp_search_hint_dmt
                        ReportKind.Pan -> R.string.rp_search_hint_pan; ReportKind.AddMoney -> R.string.rp_search_hint_add_money
                    }), vm::onSearch)
                    if (kind.hasStatusFilter) ChoiceChips(
                        listOf(stringResource(R.string.rp_all), stringResource(R.string.rp_success), stringResource(R.string.rp_pending), stringResource(R.string.rp_failed)),
                        statuses.indexOf(s.status), { vm.setStatus(statuses[it]) },
                    )
                    PeriodRow(s.period, summaryNote(kind, s.summary)) { filters = true }
                    TotalTiles(summaryTiles(kind, s.summary))
                    if (kind == ReportKind.Dmt) Text(stringResource(R.string.rp_dmt_note), style = AslTheme.type.caption, color = c.textSecondary)
                    RefreshBanner(s.error, s.loadedAt, s.items.isNotEmpty(), vm::refresh)
                }
            }
        }) {
            if (s.empty) item { EmptyState(icon, colors, s.filtered, { vm.setPreset(Preset.ThisMonth) }, vm::clearFilters) }
            s.items.forEachIndexed { i, r ->
                item(key = "r-${r.txnId}-$i") {
                    Cell(i, s.items.size) {
                        TransactionRow(r.badge, colors, r.title, listOf(r.subtitle, whenText(r.day, r.time, today)).filter { it.isNotBlank() }.joinToString("\n"),
                            r.amount?.let(::rupees) ?: "—", statusWord(r.status), r.status.pill(), onClick = { vm.open(r) })
                    }
                }
            }
        }
    }
    if (filters) FilterSheet(s.period, if (kind.hasStatusFilter) s.status else null, if (kind.hasStatusFilter) statuses else null, kind.lookbackLimited,
        onDismiss = { filters = false }) { p, st -> filters = false; vm.applyFilters(p, st) }
}

@Composable
private fun summaryNote(kind: ReportKind, s: Summary?): String? {
    val count = s?.count ?: return null
    val amount = s.totals.toMap()[TotalKind.Amount]
    return if (amount != null && kind != ReportKind.Aeps && kind != ReportKind.Matm && kind != ReportKind.Dmt) stringResource(R.string.rp_count_amount, count, rupees(amount))
    else stringResource(R.string.rp_count, count)
}

@Composable
private fun summaryTiles(kind: ReportKind, s: Summary?): List<Triple<String, String, List<Color>>> {
    val t = s?.totals.orEmpty().toMap()
    return when (kind) {
        ReportKind.Aeps, ReportKind.Matm -> listOfNotNull(
            t[TotalKind.Amount]?.let { Triple(rupees(it), stringResource(R.string.rp_t_withdrawn), AslGradients.success) },
            t[TotalKind.Commission]?.let { Triple(rupees(it), stringResource(R.string.rp_t_commission), AslGradients.Service.Aeps.colors) },
        )
        ReportKind.AddMoney -> listOfNotNull(
            t[TotalKind.Success]?.let { Triple(rupees(it), stringResource(R.string.rp_t_added), AslGradients.success) },
            t[TotalKind.Pending]?.let { Triple(rupees(it), stringResource(R.string.rp_t_not_completed), AslGradients.pending) },
        )
        ReportKind.Dmt -> listOfNotNull(
            t[TotalKind.Success]?.let { Triple(rupees(it), stringResource(R.string.rp_success), AslGradients.success) },
            t[TotalKind.Failed]?.let { Triple(rupees(it), stringResource(R.string.rp_t_failed_refunded), AslGradients.failed) },
        )
        else -> emptyList()
    }
}

/** 6.14–6.17: the customer receipt for a row, its complaint, and (for the retailer only) commission and reason. */
@Composable
private fun RowDetailPage(r: ReportRow, shop: ShopInfo, vm: ReportViewModel) {
    BackHandler(onBack = vm::close)
    val context = LocalContext.current
    val c = AslTheme.colors
    val complaint by vm.complaint.collectAsStateWithLifecycle()
    val shareTitle = stringResource(R.string.rp_share_title)
    val title = kindName(r.kind)
    val dateLine = listOfNotNull(r.day?.label(), r.time).joinToString(", ").ifEmpty { null }
    val receiptRows = r.receiptFields.map { fieldName(it.first) to it.second } + listOfNotNull(dateLine?.let { stringResource(R.string.rp_f_date) to it })
    val doc = ReceiptDoc(title, statusWord(r.status), r.amount?.let(::rupees), receiptRows, footer = stringResource(R.string.rp_receipt_note))
    Scaffold(stringResource(R.string.rp_receipt), vm::close) {
        if (complaint == ComplaintState.Sent) AlertBanner(AlertKind.Success, stringResource(R.string.rp_complaint_sent))
        ReceiptCard(shop, title, r.amount?.let(::rupees) ?: "—", r.status.pill(), receiptRows.map { KeyValue(it.first, it.second) }, footer = doc.footer,
            statusLabel = statusWord(r.status), amountWords = r.amount?.takeIf { it.signum() > 0 }?.let { AmountInWords.english(it) }, barcodeSeed = r.txnId)
        if (r.status == RowStatus.Pending) AlertBanner(AlertKind.Pending, stringResource(R.string.rp_pending_title), message = stringResource(R.string.rp_pending_msg))
        val own = r.fields.filter { !it.first.customer }
        if (own.isNotEmpty()) AslCard { KeyValueList(own.map { KeyValue(fieldName(it.first), it.second) }) }
        r.complaint?.let { cp ->
            AslCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.rp_complaint_raised) + (cp.date?.let { " · $it" } ?: ""), style = AslTheme.type.label, color = c.textPrimary)
                    Text(cp.text, style = AslTheme.type.body, color = c.textSecondary)
                    Text(if (cp.reply != null) stringResource(R.string.rp_asl_reply) else stringResource(R.string.rp_asl_checking), style = AslTheme.type.label, color = if (cp.reply != null) c.success else c.pending)
                    Text(cp.reply ?: stringResource(R.string.rp_reply_here), style = AslTheme.type.body, color = c.textSecondary)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action(stringResource(R.string.rp_share), AslIcons.Chat, AslGradients.Service.Upi.colors) { ReceiptExport.shareText(context, shareTitle, ReceiptExport.text(shop, doc)) }
            Action(stringResource(R.string.rp_save_pdf), AslIcons.Document, AslGradients.Service.Bills.colors) {
                ReceiptExport.sharePdf(context, shop, doc, shareTitle, "ASL-${r.kind.name.lowercase()}-${r.txnId.filter(Char::isLetterOrDigit).take(24)}.pdf")
            }
            if (r.canComplain) Action(stringResource(R.string.rp_complaint), AslIcons.Alert, AslGradients.Service.Recharge.colors, vm::startComplaint)
        }
    }
    val cs = complaint
    if (cs != null && cs != ComplaintState.Sent) ComplaintSheet(r, cs, vm)
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Action(label: String, icon: ImageVector, colors: List<Color>, onClick: () -> Unit) {
    val c = AslTheme.colors
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp, c.outline, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(34.dp).background(Brush.linearGradient(colors), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp))
        }
        Text(label, style = AslTheme.type.micro, color = c.textPrimary)
    }
}

@Composable
private fun ComplaintSheet(r: ReportRow, cs: ComplaintState, vm: ReportViewModel) {
    val text by vm.complaintText.collectAsStateWithLifecycle()
    val reasons = listOf(stringResource(R.string.rp_c_not_received), stringResource(R.string.rp_c_pending), stringResource(R.string.rp_c_wrong), stringResource(R.string.rp_c_other))
    var picked by rememberSaveable { mutableStateOf<Int?>(null) }
    val sending = cs == ComplaintState.Sending
    Sheet(vm::cancelComplaint) {
        Text(stringResource(R.string.rp_complaint_title), style = AslTheme.type.title, color = AslTheme.colors.textPrimary)
        Text(stringResource(R.string.rp_complaint_what), style = AslTheme.type.label, color = AslTheme.colors.textSecondary)
        ChoiceChips(reasons, picked, { i -> picked = i; if (i < reasons.lastIndex) vm.onComplaintText(reasons[i]) else vm.onComplaintText("") })
        AslTextField(text, vm::onComplaintText, stringResource(R.string.rp_complaint_details), maxLength = r.kind.complaintMax, singleLine = false)
        Text(stringResource(R.string.rp_complaint_count, text.length, r.kind.complaintMax), style = AslTheme.type.caption, color = AslTheme.colors.textSecondary, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
        if (cs is ComplaintState.Error) AlertBanner(AlertKind.Error, errorText(cs.e))
        AslPrimaryButton(stringResource(R.string.rp_send_complaint), vm::sendComplaint, enabled = text.isNotBlank() && !sending, loading = sending)
    }
}

// ---------------------------------------------------------------- filters

/** One sheet for every report: date presets, custom range (≤ 3 months), and status where the API has one. */
@Composable
private fun FilterSheet(
    period: Period, status: RowStatus?, statuses: List<RowStatus?>?, lookbackLimited: Boolean,
    onDismiss: () -> Unit, onApply: (Period, RowStatus?) -> Unit,
) {
    val today = remember { Day.today() }
    val presets = listOf(Preset.Today, Preset.Yesterday, Preset.Last7, Preset.ThisMonth, Preset.LastMonth, Preset.Custom)
    var preset by remember { mutableStateOf(period.preset) }
    var from by remember { mutableStateOf(period.from) }
    var to by remember { mutableStateOf(period.to) }
    var st by remember { mutableStateOf(status) }
    var picking by remember { mutableStateOf<Int?>(null) } // 0 = from, 1 = to
    val check = Periods.check(from, to, today, lookbackLimited)
    Sheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.rp_filter), style = AslTheme.type.title, color = AslTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            TextButton({ Periods.default(today).let { preset = it.preset; from = it.from; to = it.to }; st = null }) { Text(stringResource(R.string.rp_reset)) }
        }
        Text(stringResource(R.string.rp_date), style = AslTheme.type.label, color = AslTheme.colors.textSecondary)
        ChoiceChips(presets.map { presetName(it) }, presets.indexOf(preset), { i ->
            preset = presets[i]
            if (preset != Preset.Custom) Periods.of(preset, today).let { from = it.from; to = it.to }
        })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateBox(stringResource(R.string.rp_from), from, Modifier.weight(1f)) { picking = 0 }
            DateBox(stringResource(R.string.rp_to), to, Modifier.weight(1f)) { picking = 1 }
        }
        when (check) {
            Periods.Check.Ok -> Unit
            Periods.Check.FromAfterTo -> AlertBanner(AlertKind.Error, stringResource(R.string.rp_err_from_after))
            Periods.Check.InFuture -> AlertBanner(AlertKind.Error, stringResource(R.string.rp_err_future))
            Periods.Check.TooLong -> AlertBanner(AlertKind.Error, stringResource(R.string.rp_err_long))
            Periods.Check.TooOld -> AlertBanner(AlertKind.Error, stringResource(R.string.rp_err_old))
        }
        if (statuses != null) {
            Text(stringResource(R.string.rp_status), style = AslTheme.type.label, color = AslTheme.colors.textSecondary)
            ChoiceChips(statuses.map { it?.let { s -> statusWord(s) } ?: stringResource(R.string.rp_all) }, statuses.indexOf(st), { st = statuses[it] })
        }
        AslPrimaryButton(stringResource(R.string.rp_show_results), { onApply(Period(from, to, preset), st) }, enabled = check == Periods.Check.Ok)
    }
    picking?.let { which ->
        DayPicker(if (which == 0) from else to, today, onDismiss = { picking = null }) { d ->
            picking = null; preset = Preset.Custom
            if (which == 0) from = d else to = d
        }
    }
}

@Composable
private fun DateBox(label: String, d: Day, modifier: Modifier, onClick: () -> Unit) {
    val c = AslTheme.colors
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp, c.outline, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = AslTheme.type.caption, color = c.textSecondary)
            Text(d.label(), style = AslTheme.type.label, color = c.textPrimary)
        }
        Icon(AslIcons.Calendar, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPicker(initial: Day, today: Day, onDismiss: () -> Unit, onPick: (Day) -> Unit) {
    val utc = remember(initial) {
        java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { clear(); set(initial.year, initial.month - 1, initial.day) }.timeInMillis
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = utc, selectableDates = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long) = Day.ofEpochMillisUtc(utcTimeMillis) <= today
        override fun isSelectableYear(year: Int) = year <= today.year
    })
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton({ state.selectedDateMillis?.let { onPick(Day.ofEpochMillisUtc(it)) } ?: onDismiss() }) { Text(stringResource(R.string.rp_ok)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.rp_cancel)) } },
    ) { DatePicker(state) }
}

// ---------------------------------------------------------------- 6.5 Statement PDF

@Composable
fun StatementScreen(shop: ShopInfo, onBack: () -> Unit, vm: StatementViewModel = hiltViewModel()) {
    val period by vm.period.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { Day.today() }
    var picking by remember { mutableStateOf<Int?>(null) }
    var file by remember { mutableStateOf<java.io.File?>(null) }
    var presetIdx by rememberSaveable { mutableStateOf(1) }
    val check = Periods.check(period.from, period.to, today, lookbackLimited = false)
    val shareTitle = stringResource(R.string.rp_share_title)

    // Strings for the PDF, read here (a composable context).
    val docTitle = stringResource(R.string.rp_statement_doc_title)
    val periodLine = stringResource(R.string.rp_statement_period, period.from.label(), period.to.label())
    val header = listOf(stringResource(R.string.rp_h_date), stringResource(R.string.rp_h_entry), stringResource(R.string.rp_h_dr), stringResource(R.string.rp_h_cr), stringResource(R.string.rp_h_bal))
    val openingLabel = stringResource(R.string.rp_opening_short)
    val closingLabel = stringResource(R.string.rp_closing_short)
    val footer = stringResource(R.string.rp_statement_footer)

    LaunchedEffect(state) {
        val s = state as? StatementState.Ready ?: return@LaunchedEffect
        if (s.data.entries.isEmpty()) return@LaunchedEffect
        val plain = { a: BigDecimal -> Money.format(a, withSymbol = false) }
        val e = s.data.entries
        val doc = StatementDoc(
            shop = shop, title = docTitle, periodLine = periodLine, header = header,
            rows = e.map { listOf(it.day?.let { d -> "%02d-%02d-%02d".format(d.day, d.month, d.year % 100) }.orEmpty(), it.description,
                it.debit.takeIf { d -> d.signum() > 0 }?.let(plain).orEmpty(), it.credit.takeIf { cr -> cr.signum() > 0 }?.let(plain).orEmpty(), it.closing?.let(plain).orEmpty()) },
            opening = e.first().opening?.let(plain), closing = e.last().closing?.let(plain),
            totalDebit = plain(e.fold(BigDecimal.ZERO) { acc, x -> acc + x.debit }), totalCredit = plain(e.fold(BigDecimal.ZERO) { acc, x -> acc + x.credit }),
            openingLabel = openingLabel, closingLabel = closingLabel, footer = footer,
            fileName = "ASL-statement-${s.period.from.iso()}-to-${s.period.to.iso()}.pdf",
        )
        val f = withContext(Dispatchers.IO) { runCatching { ReceiptExport.writeStatementPdf(context, doc) }.getOrNull() }
        file = f
        if (f != null) ReceiptExport.shareStatementPdf(context, f, shareTitle)
    }

    Scaffold(stringResource(R.string.rp_statement), onBack, bottom = {
        AslPrimaryButton(stringResource(R.string.rp_make_pdf), { file = null; vm.make() }, enabled = check == Periods.Check.Ok && state != StatementState.Loading,
            loading = state == StatementState.Loading, icon = AslIcons.Document)
    }) {
        Text(stringResource(R.string.rp_period), style = AslTheme.type.label, color = AslTheme.colors.textSecondary)
        ChoiceChips(listOf(stringResource(R.string.rp_p_this_month), stringResource(R.string.rp_p_last_month), stringResource(R.string.rp_p_3_months), stringResource(R.string.rp_p_custom)), presetIdx, { i ->
            presetIdx = i
            when (i) { 0 -> vm.setPreset(Preset.ThisMonth); 1 -> vm.setPreset(Preset.LastMonth); 2 -> vm.lastThreeMonths(); else -> picking = 0 }
        })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateBox(stringResource(R.string.rp_from), period.from, Modifier.weight(1f)) { picking = 0 }
            DateBox(stringResource(R.string.rp_to), period.to, Modifier.weight(1f)) { picking = 1 }
        }
        when (check) {
            Periods.Check.Ok -> Unit
            Periods.Check.FromAfterTo -> AlertBanner(AlertKind.Error, stringResource(R.string.rp_err_from_after))
            Periods.Check.InFuture -> AlertBanner(AlertKind.Error, stringResource(R.string.rp_err_future))
            else -> AlertBanner(AlertKind.Error, stringResource(R.string.rp_err_long))
        }
        Text(stringResource(R.string.rp_statement_note), style = AslTheme.type.caption, color = AslTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        when (val s = state) {
            is StatementState.Error -> AlertBanner(AlertKind.Error, errorText(s.e))
            is StatementState.Ready -> {
                if (s.data.entries.isEmpty()) AlertBanner(AlertKind.Info, stringResource(R.string.rp_statement_empty))
                else {
                    AlertBanner(AlertKind.Success, stringResource(R.string.rp_statement_ready, s.data.entries.size))
                    if (s.data.truncated) AlertBanner(AlertKind.Pending, stringResource(R.string.rp_statement_truncated, ReportRules.STATEMENT_MAX))
                    file?.let { f -> AslButton(stringResource(R.string.rp_share), { scope.launch { ReceiptExport.shareStatementPdf(context, f, shareTitle) } }, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline, icon = AslIcons.Share) }
                }
            }
            else -> Unit
        }
    }
    picking?.let { which ->
        DayPicker(if (which == 0) period.from else period.to, today, onDismiss = { picking = null }) { d ->
            picking = null; presetIdx = 3
            if (which == 0) vm.setCustom(d, period.to) else vm.setCustom(period.from, d)
        }
    }
}
