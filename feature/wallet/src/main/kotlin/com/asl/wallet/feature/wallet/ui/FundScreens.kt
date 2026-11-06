package com.asl.wallet.feature.wallet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asl.wallet.core.common.format.Money
import com.asl.wallet.core.designsystem.component.AlertBanner
import com.asl.wallet.core.designsystem.component.AlertKind
import com.asl.wallet.core.designsystem.component.AslButton
import com.asl.wallet.core.designsystem.component.AslButtonStyle
import com.asl.wallet.core.designsystem.component.AslCard
import com.asl.wallet.core.designsystem.component.AslPrimaryButton
import com.asl.wallet.core.designsystem.component.AslTextField
import com.asl.wallet.core.designsystem.component.ChoiceChips
import com.asl.wallet.core.designsystem.component.Divider
import com.asl.wallet.core.designsystem.component.KeyValue
import com.asl.wallet.core.designsystem.component.KeyValueList
import com.asl.wallet.core.designsystem.component.ListCard
import com.asl.wallet.core.designsystem.component.ListRow
import com.asl.wallet.core.designsystem.component.SkeletonRow
import com.asl.wallet.core.designsystem.component.StatePresets
import com.asl.wallet.core.designsystem.component.StateView
import com.asl.wallet.core.designsystem.component.Status
import com.asl.wallet.core.designsystem.component.StatusPill
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.theme.AslGradients
import com.asl.wallet.core.designsystem.theme.AslTheme
import com.asl.wallet.core.network.UiError
import com.asl.wallet.feature.wallet.R
import com.asl.wallet.feature.wallet.domain.CompanyBank
import com.asl.wallet.feature.wallet.domain.Day
import com.asl.wallet.feature.wallet.domain.FundMode
import com.asl.wallet.feature.wallet.domain.FundRequestForm
import com.asl.wallet.feature.wallet.domain.FundRequestForm.Field
import com.asl.wallet.feature.wallet.domain.FundRequestItem
import com.asl.wallet.feature.wallet.domain.FundStatus

@Composable
private fun modeLabel(m: FundMode) = stringResource(when (m) {
    FundMode.Cash -> R.string.w_mode_cash; FundMode.Imps -> R.string.w_mode_imps; FundMode.Neft -> R.string.w_mode_neft
    FundMode.Upi -> R.string.w_mode_upi; FundMode.Others -> R.string.w_mode_others
})

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
internal fun Day.label(): String = "$day ${MONTHS[month - 1]} $year"
private fun dateLabel(raw: String): String = Day.parse(raw)?.label() ?: raw

// ---------------------------------------------------------------- 4.12–4.14, 4.19 Fund request form

@Composable
fun FundRequestScreen(onBack: () -> Unit, onMyRequests: () -> Unit, onDone: () -> Unit, onCallSupport: () -> Unit, vm: FundRequestViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var bankSheet by remember { mutableStateOf(false) }
    var datePicker by remember { mutableStateOf(false) }
    val title = if (s.editing != null) stringResource(R.string.w_edit_request) else stringResource(R.string.w_fund_request)
    val myRequests: @Composable () -> Unit = {
        if (s.editing == null) TextButton(onMyRequests) { Text(stringResource(R.string.w_my_requests), style = AslTheme.type.label, color = AslTheme.colors.primary) }
    }

    when (s.phase) {
        FundPhase.Loading -> WalletScaffold(title, onBack) { repeat(4) { SkeletonRow() } }
        FundPhase.Unavailable -> WalletScaffold(title, onBack, right = myRequests) {
            val offline = s.error == UiError.NoInternet
            StateView(
                if (offline) AslIcons.WifiOff else AslIcons.Clock, StatePresets.offlineColors,
                stringResource(if (offline) R.string.w_offline_title else R.string.w_fund_na),
                stringResource(if (offline) R.string.w_offline else R.string.w_fund_na_msg),
            ) {
                AslPrimaryButton(stringResource(R.string.w_try_again), vm::load, icon = AslIcons.Refresh)
                AslButton(stringResource(R.string.w_call_support), onCallSupport, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline, icon = AslIcons.Phone)
            }
        }
        FundPhase.Sent -> WalletScaffold(title, null, bottom = {
            AslPrimaryButton(stringResource(R.string.w_view_requests), onMyRequests)
            AslButton(stringResource(R.string.w_back_wallet), onDone, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline)
        }) {
            val f = s.form
            ResultBlock(ResultKind.Warn, stringResource(if (s.editing != null) R.string.w_request_updated else R.string.w_request_sent), stringResource(R.string.w_waiting_admin), listOfNotNull(
                KeyValue(stringResource(R.string.w_amount), money(f.amountText.toAmount())),
                f.bank?.let { KeyValue(stringResource(R.string.w_deposited_to), "${it.name} · ••${it.last4}") },
                f.mode?.let { KeyValue(stringResource(R.string.w_mode), modeLabel(it)) },
                KeyValue(stringResource(R.string.w_utr), f.utr),
                s.sentRef?.let { KeyValue(stringResource(R.string.w_request_id), it) },
            ))
        }
        FundPhase.Form -> {
            val p = if (s.showErrors) s.problems(vm.today()) else emptySet()
            val setup = s.setup!!
            WalletScaffold(title, onBack, right = myRequests, bottom = {
                AslPrimaryButton(stringResource(if (s.editing != null) R.string.w_save_changes else R.string.w_send_request), vm::submit, loading = s.busy)
            }) {
                AlertBanner(AlertKind.Info, stringResource(R.string.w_to_admin), message = stringResource(R.string.w_to_admin_msg), icon = AslIcons.User)
                PickerRow(
                    stringResource(R.string.w_deposited_to), s.form.bank?.let { "${it.name} · ••${it.last4}" }, stringResource(R.string.w_choose_bank), AslIcons.Bank,
                    error = if (Field.Bank in p) stringResource(R.string.w_bank_needed) else null,
                ) { bankSheet = true }
                AslTextField(s.form.amountText, vm::onAmount, stringResource(R.string.w_amount), keyboardType = KeyboardType.Decimal, maxLength = 11,
                    error = if (Field.Amount in p) stringResource(R.string.w_amount_enter) else null)
                HeaderText(stringResource(R.string.w_payment_mode))
                ChoiceChips(setup.modes.map { modeLabel(it) }, setup.modes.indexOf(s.form.mode).takeIf { it >= 0 }, { vm.onMode(setup.modes[it]) })
                if (Field.Mode in p) Text(stringResource(R.string.w_mode_needed), style = AslTheme.type.caption, color = AslTheme.colors.error)
                AslTextField(s.form.utr, vm::onUtr, stringResource(R.string.w_utr), maxLength = 40,
                    error = if (Field.Utr in p) stringResource(R.string.w_utr_bad) else null)
                PickerRow(
                    stringResource(R.string.w_deposit_date), s.form.date?.label(), stringResource(R.string.w_choose_date), AslIcons.Calendar,
                    error = if (Field.Date in p) stringResource(R.string.w_date_bad) else null,
                ) { datePicker = true }
                AslTextField(s.form.remarks, vm::onRemarks, stringResource(R.string.w_remarks), maxLength = 255, imeAction = androidx.compose.ui.text.input.ImeAction.Done)
                ErrorBanner(s.submitError)
            }
            if (bankSheet) CompanyBankSheet(setup.banks, onPick = { vm.onBank(it); bankSheet = false }) { bankSheet = false }
            if (datePicker) DepositDatePicker(s.form.date, vm.today(), onPick = { vm.onDate(it); datePicker = false }) { datePicker = false }
        }
    }
}

/** 4.13 The ASL accounts to deposit into, with number and IFSC to copy. */
@Composable
private fun CompanyBankSheet(banks: List<CompanyBank>, onPick: (CompanyBank) -> Unit, onDismiss: () -> Unit) = WalletSheet(onDismiss) {
    val context = LocalContext.current
    val copied = stringResource(R.string.w_copied)
    Text(stringResource(R.string.w_where_deposit), style = AslTheme.type.title, color = AslTheme.colors.textPrimary)
    if (banks.isEmpty()) Text(stringResource(R.string.w_no_company_banks), style = AslTheme.type.body, color = AslTheme.colors.textSecondary)
    ListCard {
        banks.forEachIndexed { i, b ->
            if (i > 0) Divider()
            ListRow(
                b.name, subtitle = listOfNotNull(stringResource(R.string.w_ac, b.accountNumber), b.ifsc).joinToString(" · "),
                icon = AslIcons.Bank, iconColors = AslGradients.Service.Bills.colors, onClick = { onPick(b) },
                trailing = {
                    androidx.compose.material3.IconButton({ copy(context, "A/C", listOfNotNull(b.accountNumber, b.ifsc).joinToString(" "), copied) }) {
                        androidx.compose.material3.Icon(AslIcons.Copy, stringResource(R.string.w_copy), tint = AslTheme.colors.primary)
                    }
                },
            )
        }
    }
}

/** Deposit date: today or up to 90 days back, never in the future. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DepositDatePicker(current: Day?, today: Day, onPick: (Day) -> Unit, onDismiss: () -> Unit) {
    val dayMs = 86_400_000L
    val maxMs = today.epochDay * dayMs
    val minMs = (today.epochDay - FundRequestForm.MAX_AGE_DAYS) * dayMs
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (current ?: today).epochDay * dayMs,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in minMs..maxMs
            override fun isSelectableYear(year: Int) = year in (today.year - 1)..today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton({ state.selectedDateMillis?.let { onPick(Day.ofEpochMillisUtc(it)) } ?: onDismiss() }) { Text(stringResource(R.string.w_ok)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.w_cancel)) } },
    ) { DatePicker(state) }
}

// ---------------------------------------------------------------- 4.15–4.18 My requests

@Composable
private fun statusLabel(s: FundStatus) = stringResource(when (s) {
    FundStatus.Pending -> R.string.w_pending; FundStatus.Approved -> R.string.w_approved; FundStatus.Declined -> R.string.w_declined
})

private fun FundStatus.pill() = when (this) { FundStatus.Pending -> Status.Pending; FundStatus.Approved -> Status.Success; FundStatus.Declined -> Status.Failed }

@Composable
fun MyRequestsScreen(onBack: () -> Unit, onEdit: () -> Unit, onNewRequest: () -> Unit, vm: MyRequestsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }
    val selected = s.selected
    if (selected != null) {
        androidx.activity.compose.BackHandler(onBack = vm::close)
        RequestDetails(selected, s, vm, onEdit = { if (vm.startEdit()) onEdit() }, onNewRequest = onNewRequest)
        return
    }
    val filters = listOf(null, FundStatus.Pending, FundStatus.Approved, FundStatus.Declined)
    WalletScaffold(stringResource(R.string.w_my_requests), onBack) {
        s.summary?.let { sum ->
            AslCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.w_pending), style = AslTheme.type.caption, color = AslTheme.colors.textSecondary)
                        Text(androidx.compose.ui.res.pluralStringResource(R.plurals.w_requests, sum.pendingCount, sum.pendingCount), style = AslTheme.type.bodyStrong, color = AslTheme.colors.textPrimary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.w_pending_amount), style = AslTheme.type.caption, color = AslTheme.colors.textSecondary)
                        Text(Money.format(sum.pendingAmount), style = AslTheme.type.bodyStrong, color = AslTheme.colors.textPrimary)
                    }
                }
            }
        }
        ChoiceChips(listOf(stringResource(R.string.w_all)) + filters.drop(1).map { statusLabel(it!!) }, filters.indexOf(s.filter), { vm.load(filters[it]) })
        when {
            s.loading -> ListCard { Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { repeat(4) { SkeletonRow() } } }
            s.error != null && s.items.isEmpty() -> StateView(AslIcons.WifiOff, StatePresets.offlineColors, stringResource(R.string.w_load_error), uiErrorText(s.error ?: com.asl.wallet.core.network.UiError.Unknown)) {
                AslPrimaryButton(stringResource(R.string.w_try_again), { vm.load() }, icon = AslIcons.Refresh)
            }
            s.items.isEmpty() -> StateView(AslIcons.Document, StatePresets.emptyColors, stringResource(R.string.w_no_requests), stringResource(R.string.w_no_requests_msg)) {
                AslPrimaryButton(stringResource(R.string.w_send_request), onNewRequest)
            }
            else -> {
                ListCard {
                    s.items.forEachIndexed { i, r ->
                        if (i > 0) Divider()
                        ListRow(
                            "${Money.format(r.amount)}${r.mode?.let { " · " + modeLabel(it) }.orEmpty()}",
                            subtitle = listOf(dateLabel(r.sentDate.ifBlank { r.depositDate }), r.utr.takeIf { it.isNotBlank() }?.let { "…" + it.takeLast(4) }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                            icon = AslIcons.Document,
                            iconColors = when (r.status) { FundStatus.Approved -> AslGradients.success; FundStatus.Declined -> AslGradients.failed; else -> AslGradients.Service.Wallet.colors },
                            trailing = { StatusPill(statusLabel(r.status), r.status.pill()) },
                            onClick = { vm.open(r) },
                        )
                    }
                }
                if (s.canLoadMore) AslButton(stringResource(R.string.w_load_more), vm::loadMore, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline, loading = s.loadingMore)
            }
        }
    }
}

/** 4.16 details (edit/delete while pending), 4.17 declined with Admin's reason, 4.18 delete confirmation. */
@Composable
private fun RequestDetails(r: FundRequestItem, s: RequestsState, vm: MyRequestsViewModel, onEdit: () -> Unit, onNewRequest: () -> Unit) {
    WalletScaffold(stringResource(R.string.w_request_title, r.ref), vm::close, bottom = when (r.status) {
        FundStatus.Pending -> ({
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AslButton(stringResource(R.string.w_edit), onEdit, Modifier.weight(1f), style = AslButtonStyle.Outline, icon = AslIcons.Edit)
                AslButton(stringResource(R.string.w_delete), vm::askDelete, Modifier.weight(1f), style = AslButtonStyle.Danger, icon = AslIcons.Trash)
            }
        })
        FundStatus.Declined -> ({ AslPrimaryButton(stringResource(R.string.w_new_request), onNewRequest) })
        FundStatus.Approved -> null
    }) {
        if (r.status == FundStatus.Declined) AlertBanner(AlertKind.Error, stringResource(R.string.w_declined_admin), message = r.adminRemarks)
        AslCard { KeyValueList(listOfNotNull(
            KeyValue(stringResource(R.string.w_amount), Money.format(r.amount)),
            r.bankName?.let { KeyValue(stringResource(R.string.w_deposited_to), it + (r.accountLast4?.let { l -> " · ••$l" } ?: "")) },
            r.mode?.let { KeyValue(stringResource(R.string.w_mode), modeLabel(it)) },
            r.utr.takeIf { it.isNotBlank() }?.let { KeyValue(stringResource(R.string.w_utr), it) },
            r.depositDate.takeIf { it.isNotBlank() }?.let { KeyValue(stringResource(R.string.w_deposit_date), dateLabel(it)) },
            r.remarks?.let { KeyValue(stringResource(R.string.w_remarks), it) },
        )) }
        if (r.status != FundStatus.Declined) Timeline(r)
        ErrorBanner(s.actionError)
    }
    if (s.confirmDelete) ConfirmDialog(
        stringResource(R.string.w_delete_request_q), stringResource(R.string.w_delete_request_msg, Money.format(r.amount), r.utr.takeLast(4)),
        stringResource(R.string.w_delete), s.deleting, vm::delete, vm::cancelDelete,
    )
}

@Composable
private fun Timeline(r: FundRequestItem) {
    val c = AslTheme.colors
    val approved = r.status == FundStatus.Approved
    val steps = listOf(
        Triple(stringResource(R.string.w_tl_sent), listOf(dateLabel(r.sentDate), r.sentTime).filter { it.isNotBlank() }.joinToString(", "), true),
        Triple(stringResource(R.string.w_tl_admin), stringResource(R.string.w_tl_admin_sub), approved),
        Triple(stringResource(R.string.w_tl_added), "", approved),
    )
    AslCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            steps.forEachIndexed { i, (t, sub, done) ->
                val current = !done && (i == 0 || steps[i - 1].third)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (done) "✓" else if (current) "●" else "○", color = if (done) c.success else if (current) c.pending else c.textSecondary, style = AslTheme.type.label)
                    Column {
                        Text(t, style = AslTheme.type.label, color = if (done || current) c.textPrimary else c.textSecondary)
                        if (sub.isNotBlank()) Text(sub, style = AslTheme.type.caption, color = c.textSecondary, textAlign = TextAlign.Start)
                    }
                }
            }
        }
    }
}
