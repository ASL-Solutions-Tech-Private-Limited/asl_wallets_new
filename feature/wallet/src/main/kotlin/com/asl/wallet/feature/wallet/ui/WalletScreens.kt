package com.asl.wallet.feature.wallet.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.asl.wallet.core.designsystem.component.AmountCard
import com.asl.wallet.core.designsystem.component.AslButton
import com.asl.wallet.core.designsystem.component.AslButtonStyle
import com.asl.wallet.core.designsystem.component.AslCard
import com.asl.wallet.core.designsystem.component.AslPrimaryButton
import com.asl.wallet.core.designsystem.component.AslTextField
import com.asl.wallet.core.designsystem.component.Divider
import com.asl.wallet.core.designsystem.component.KeyValue
import com.asl.wallet.core.designsystem.component.ListCard
import com.asl.wallet.core.designsystem.component.ListRow
import com.asl.wallet.core.designsystem.component.SectionHeader
import com.asl.wallet.core.designsystem.component.SkeletonRow
import com.asl.wallet.core.designsystem.component.StatePresets
import com.asl.wallet.core.designsystem.component.StateView
import com.asl.wallet.core.designsystem.component.Status
import com.asl.wallet.core.designsystem.component.TransactionRow
import com.asl.wallet.core.designsystem.component.WalletCard
import com.asl.wallet.core.designsystem.component.WalletCardLabels
import com.asl.wallet.core.designsystem.component.shimmer
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.theme.AslGradients
import com.asl.wallet.core.designsystem.theme.AslTheme
import com.asl.wallet.core.security.ui.findActivity
import com.asl.wallet.feature.wallet.R
import com.asl.wallet.feature.wallet.domain.AddMoneyResult
import com.asl.wallet.feature.wallet.domain.WalletEntry

// ---------------------------------------------------------------- 4.1–4.2 Wallet

@Composable
fun WalletScreen(
    onBack: () -> Unit,
    onAddMoney: () -> Unit,
    onFundRequest: () -> Unit,
    onSendToBank: () -> Unit,
    onBankAccounts: () -> Unit,
    /** Full history is in Reports (Module 5); until then a short note says so. */
    onViewAll: (() -> Unit)? = null,
    vm: WalletViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }
    val c = AslTheme.colors
    WalletScaffold(stringResource(R.string.w_title), onBack) {
        val o = s.overview
        when {
            s.loading && o == null -> Box(Modifier.fillMaxWidth().height(150.dp).shimmer(RoundedCornerShape(22.dp)))
            o == null -> {
                StateView(AslIcons.WifiOff, StatePresets.offlineColors, stringResource(R.string.w_balance_error), stringResource(R.string.w_balance_error_msg)) {
                    AslPrimaryButton(stringResource(R.string.w_try_again), vm::load, loading = s.loading, icon = AslIcons.Refresh)
                }
                return@WalletScaffold
            }
            else -> {
                WalletCard(
                    balance = o.balance, hidden = s.hidden, onToggleHidden = vm::toggleHidden,
                    earnedToday = if (o.commission.signum() > 0) stringResource(R.string.w_commission_chip, Money.format(o.commission, showPaise = false)) else null,
                    labels = WalletCardLabels(
                        stringResource(R.string.w_balance), stringResource(R.string.w_show), stringResource(R.string.w_hide),
                        stringResource(R.string.w_show_balance), stringResource(R.string.w_hide_balance), stringResource(R.string.w_balance_hidden),
                    ),
                )
                AslCard {
                    Row(Modifier.fillMaxWidth()) {
                        Stat(stringResource(R.string.w_etopup), Money.format(o.eTopup, showPaise = false), Modifier.weight(1f))
                        Stat(stringResource(R.string.w_sales), Money.format(o.sales, showPaise = false), Modifier.weight(1f))
                        Stat(stringResource(R.string.w_commission), Money.format(o.commission, showPaise = false), Modifier.weight(1f))
                    }
                }
            }
        }
        ListCard {
            ListRow(stringResource(R.string.w_add_money), subtitle = stringResource(R.string.w_add_money_sub), icon = AslIcons.Plus, iconColors = AslGradients.Service.Upi.colors, onClick = onAddMoney); Divider()
            ListRow(stringResource(R.string.w_fund_request), subtitle = stringResource(R.string.w_fund_request_sub), icon = AslIcons.Document, iconColors = AslGradients.Service.Wallet.colors, onClick = onFundRequest); Divider()
            ListRow(stringResource(R.string.w_send_bank), subtitle = stringResource(R.string.w_send_bank_sub), icon = AslIcons.Send, iconColors = AslGradients.Service.SendMoney.colors, onClick = onSendToBank); Divider()
            ListRow(
                stringResource(R.string.w_bank_accounts),
                subtitle = s.verifiedAccounts?.let { stringResource(R.string.w_verified_count, it) } ?: stringResource(R.string.w_bank_accounts_sub),
                icon = AslIcons.Bank, iconColors = AslGradients.Service.Bills.colors, onClick = onBankAccounts,
            )
        }
        val context = LocalContext.current
        val soon = stringResource(R.string.w_reports_soon)
        SectionHeader(stringResource(R.string.w_recent), action = stringResource(R.string.w_view_all),
            onAction = onViewAll ?: { android.widget.Toast.makeText(context, soon, android.widget.Toast.LENGTH_SHORT).show() })
        ListCard {
            when {
                s.loading && s.recent.isEmpty() -> Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { repeat(3) { SkeletonRow() } }
                s.recent.isEmpty() -> Text(stringResource(R.string.w_recent_empty), style = AslTheme.type.body, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp))
                else -> s.recent.forEachIndexed { i, e -> if (i > 0) Divider(); EntryRow(e) }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = AslTheme.type.label, color = AslTheme.colors.textPrimary, maxLines = 1)
        Text(label, style = AslTheme.type.caption, color = AslTheme.colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun EntryRow(e: WalletEntry) = TransactionRow(
    initials = if (e.isCredit) "+" else "−",
    colors = if (e.isCredit) AslGradients.success else AslGradients.Service.Neutral.colors,
    title = e.title,
    subtitle = listOf(e.date, e.time).filter { it.isNotBlank() }.joinToString(" · "),
    amount = (if (e.isCredit) "+ " else "− ") + Money.format(e.amount.abs()),
    statusText = stringResource(if (e.isCredit) R.string.w_credit else R.string.w_debit),
    status = if (e.isCredit) Status.Success else Status.Neutral,
)

// ---------------------------------------------------------------- 4.3–4.11 Add money (PayU SDK)

@Composable
fun AddMoneyScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    onCallSupport: () -> Unit,
    vm: AddMoneyViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is AddMoneyEvent.OpenPayU -> {
                    val activity: Activity? = context.findActivity()
                    if (activity == null) vm.onPayUReturn(e.order, com.asl.wallet.feature.wallet.payu.PayUReturn.Error)
                    else vm.payu.open(activity, e.order, e.production, vm.hashes) { r, payId -> vm.onPayUReturn(e.order, r, payId) }
                }
            }
        }
    }
    val title = stringResource(R.string.w_add_money)
    when (s.phase) {
        AddPhase.Preparing -> {
            Processing(title, stringResource(R.string.w_preparing), stringResource(R.string.w_dont_close),
                listOf(stringResource(R.string.w_step_order, money(s.amount)), stringResource(R.string.w_step_secure), stringResource(R.string.w_step_payu)), current = 1)
        }
        AddPhase.Confirming -> {
            Processing(title, stringResource(R.string.w_confirming), stringResource(R.string.w_few_seconds),
                listOf(stringResource(R.string.w_step_paid), stringResource(R.string.w_step_confirm), stringResource(R.string.w_step_credit)), current = 1)
        }
        AddPhase.Result -> AddMoneyResultView(s, onDone, again = vm::again, check = vm::checkAgain, onCallSupport)
        AddPhase.Form -> WalletScaffold(title, onBack, bottom = {
            val amt = s.amount
            AslPrimaryButton(
                if (amt != null) stringResource(R.string.w_pay, Money.format(amt, showPaise = false)) else stringResource(R.string.w_pay_plain),
                vm::pay, enabled = s.info != null, icon = AslIcons.Lock,
            )
            Note(stringResource(R.string.w_payu_note))
        }) {
            val info = s.info
            when {
                s.loading && info == null -> repeat(2) { SkeletonRow() }
                info == null -> {
                    StateView(AslIcons.WifiOff, StatePresets.offlineColors, stringResource(R.string.w_load_error), s.loadError?.let { uiErrorText(it) } ?: "") {
                        AslPrimaryButton(stringResource(R.string.w_try_again), vm::load, icon = AslIcons.Refresh)
                    }
                }
                else -> {
                    // Classic, animated form. Over the maximum shows at once; under the minimum only after "Pay".
                    val p = s.problem
                    val err = when {
                        s.showErrors && p != null -> amountProblemText(p, info.limits)
                        p == com.asl.wallet.feature.wallet.domain.AmountProblem.AboveMax -> amountProblemText(p, info.limits)
                        else -> null
                    }
                    AddMoneyForm(info, s.amountText, s.amount, err, onAmount = { vm.onAmount(it.take(11)) }, onQuick = vm::onQuick)
                    ErrorBanner(s.error)
                }
            }
        }
    }
    if (s.showSoon) SoonSheet(vm::dismissSoon)
}

@Composable
private fun AddMoneyResultView(s: AddMoneyState, onDone: () -> Unit, again: () -> Unit, check: () -> Unit, onCallSupport: () -> Unit) {
    val title = stringResource(R.string.w_add_money)
    when (val r = s.result) {
        is AddMoneyResult.Success -> Box(Modifier.fillMaxSize()) {
            WalletScaffold(title, null, secure = true, bottom = {
                AslPrimaryButton(stringResource(R.string.w_done), onDone)
                AslButton(stringResource(R.string.w_add_more), again, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline)
            }) {
                ResultBlock(ResultKind.Ok, stringResource(R.string.w_added, money(r.amount)), stringResource(R.string.w_added_sub), listOfNotNull(
                    s.newBalance?.let { KeyValue(stringResource(R.string.w_new_balance), money(it)) },
                    r.mode?.let { KeyValue(stringResource(R.string.w_paid_with), it) },
                    KeyValue(stringResource(R.string.w_txn_id), r.txnId),
                    r.payuRef?.let { KeyValue(stringResource(R.string.w_payu_ref), it) },
                ))
            }
            // Money is in: a one-time confetti burst over the result (none when animations are off).
            com.asl.wallet.core.designsystem.component.ConfettiBurst(Modifier.fillMaxSize())
        }
        is AddMoneyResult.Pending -> WalletScaffold(title, null, secure = true, bottom = {
            AslPrimaryButton(stringResource(R.string.w_check_again), check, loading = s.checking, icon = AslIcons.Refresh)
            AslButton(stringResource(R.string.w_back_wallet), onDone, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline)
        }) {
            ResultBlock(ResultKind.Warn, stringResource(R.string.w_pay_pending), stringResource(R.string.w_pay_pending_sub), listOf(
                KeyValue(stringResource(R.string.w_amount), money(r.amount)),
                KeyValue(stringResource(R.string.w_txn_id), r.txnId),
            ))
            AlertBanner(AlertKind.Pending, stringResource(R.string.w_debited_not_added), message = stringResource(R.string.w_call_with_id))
            AslButton(stringResource(R.string.w_call_support), onCallSupport, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline, icon = AslIcons.Phone)
        }
        is AddMoneyResult.Failed -> WalletScaffold(title, null, secure = true, bottom = {
            AslPrimaryButton(stringResource(R.string.w_try_again), again)
            AslButton(stringResource(R.string.w_back_wallet), onDone, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline)
        }) {
            ResultBlock(ResultKind.Bad, stringResource(R.string.w_pay_failed), stringResource(R.string.w_pay_failed_sub), listOf(
                KeyValue(stringResource(R.string.w_amount), money(r.amount)),
                KeyValue(stringResource(R.string.w_txn_id), r.txnId),
                KeyValue(stringResource(R.string.w_reason), r.reason?.let { com.asl.wallet.core.network.safeServerMessage(it) } ?: stringResource(R.string.w_cancelled_payu)),
            ))
            Note(stringResource(R.string.w_refund_note))
        }
        null -> Unit
    }
}
