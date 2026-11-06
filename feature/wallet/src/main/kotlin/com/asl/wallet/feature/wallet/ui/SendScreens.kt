package com.asl.wallet.feature.wallet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asl.wallet.core.common.format.AmountInWords
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
import com.asl.wallet.core.designsystem.component.HelpCard
import com.asl.wallet.core.designsystem.component.KeyValue
import com.asl.wallet.core.designsystem.component.KeyValueList
import com.asl.wallet.core.designsystem.component.ListCard
import com.asl.wallet.core.designsystem.component.ListRow
import com.asl.wallet.core.designsystem.component.OtpInput
import com.asl.wallet.core.designsystem.component.ReceiptActionBar
import com.asl.wallet.core.designsystem.component.ReceiptCard
import com.asl.wallet.core.designsystem.component.SegmentedControl
import com.asl.wallet.core.designsystem.component.ShopInfo
import com.asl.wallet.core.designsystem.component.SkeletonRow
import com.asl.wallet.core.designsystem.component.StatePresets
import com.asl.wallet.core.designsystem.component.StateView
import com.asl.wallet.core.designsystem.component.Status
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.receipt.ReceiptDoc
import com.asl.wallet.core.designsystem.receipt.ReceiptExport
import com.asl.wallet.core.designsystem.theme.AslGradients
import com.asl.wallet.core.designsystem.theme.AslTheme
import com.asl.wallet.core.security.ui.findActivity
import com.asl.wallet.feature.wallet.R
import com.asl.wallet.feature.wallet.domain.SendAccount
import com.asl.wallet.feature.wallet.domain.SendOutcome
import com.asl.wallet.feature.wallet.domain.SendReceipt

private fun SendAccount.line() = "${holder.ifBlank { bankName }} · $bankName ••$accountLast4"

@Composable
fun SendMoneyScreen(
    shop: ShopInfo,
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
    onDone: () -> Unit,
    onCallSupport: () -> Unit,
    onWhatsAppSupport: () -> Unit,
    vm: SendMoneyViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var accountSheet by remember { mutableStateOf(false) }
    val title = stringResource(R.string.w_send_bank)
    when (s.phase) {
        SendPhase.Loading -> WalletScaffold(title, onBack) { repeat(4) { SkeletonRow() } }
        SendPhase.LoadError -> WalletScaffold(title, onBack) {
            StateView(AslIcons.WifiOff, StatePresets.offlineColors, stringResource(R.string.w_load_error), s.error?.let { uiErrorText(it) } ?: "") {
                AslPrimaryButton(stringResource(R.string.w_try_again), vm::load, icon = AslIcons.Refresh)
            }
        }
        SendPhase.NoAccount -> WalletScaffold(title, onBack) {
            StateView(AslIcons.Bank, AslGradients.Service.Bills.colors, stringResource(R.string.w_no_account), stringResource(R.string.w_no_account_msg)) {
                AslPrimaryButton(stringResource(R.string.w_add_account), onAddAccount, icon = AslIcons.Plus)
            }
        }
        SendPhase.Sending -> {
            Processing(title, stringResource(R.string.w_sending, money(s.amount)), stringResource(R.string.w_dont_close),
                listOf(stringResource(R.string.w_step_otp_ok), stringResource(R.string.w_step_sending_to, s.account?.bankName.orEmpty()), stringResource(R.string.w_step_done)), current = 1,
                colors = AslGradients.Service.SendMoney.colors)
        }
        SendPhase.Result -> SendResult(s, shop, onDone, again = vm::again, onCallSupport, onWhatsAppSupport)
        SendPhase.Form -> {
            val setup = s.setup!!
            WalletScaffold(title, onBack, secure = true, bottom = {
                AslPrimaryButton(stringResource(R.string.w_continue), vm::proceed, loading = s.busy && !s.otpOpen)
            }) {
                PickerRow(stringResource(R.string.w_to_account), s.account?.line(), stringResource(R.string.w_choose_account), AslIcons.Bank,
                    error = if (s.showErrors && s.account == null) stringResource(R.string.w_account_needed) else null) { accountSheet = true }
                AmountCard(s.amount)
                AslTextField(s.amountText, vm::onAmount, stringResource(R.string.w_amount), keyboardType = KeyboardType.Decimal, maxLength = 11,
                    error = if (s.showErrors) s.problem?.let { amountProblemText(it, setup.limits) } else null)
                if (setup.modes.size > 1) SegmentedControl(setup.modes, setup.modes.indexOf(s.mode).coerceAtLeast(0), { vm.onMode(setup.modes[it]) })
                else Note(stringResource(R.string.w_via_mode, s.mode))
                Note(stringResource(R.string.w_send_limits, Money.format(setup.limits.min, showPaise = false), Money.format(setup.limits.max, showPaise = false), money(setup.balance)))
                if (setup.notes.isNotEmpty()) AslCard {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        setup.notes.forEach { Text("• $it", style = AslTheme.type.caption, color = AslTheme.colors.textSecondary) }
                    }
                }
                ErrorBanner(s.formError)
            }
            if (accountSheet) WalletSheet({ accountSheet = false }) {
                Text(stringResource(R.string.w_choose_account), style = AslTheme.type.title, color = AslTheme.colors.textPrimary)
                ListCard {
                    setup.accounts.forEachIndexed { i, a ->
                        if (i > 0) Divider()
                        ListRow("${a.bankName} · ••${a.accountLast4}", subtitle = listOf(a.holder, a.ifsc).filter { it.isNotBlank() }.joinToString(" · "),
                            icon = AslIcons.Bank, iconColors = AslGradients.Service.Bills.colors, trailing = null, onClick = { vm.onAccount(a); accountSheet = false })
                    }
                }
                AslButton(stringResource(R.string.w_add_account), { accountSheet = false; onAddAccount() }, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline, icon = AslIcons.Plus)
            }
            if (s.otpOpen) OtpSheet(s, vm)
        }
    }
}

/** 4.22 Review and 4-digit OTP. Money moves only after "Confirm and send". */
@Composable
private fun OtpSheet(s: SendState, vm: SendMoneyViewModel) = WalletSheet(vm::closeOtp, dismissible = false) {
    AslCard { KeyValueList(listOfNotNull(
        s.account?.let { KeyValue(stringResource(R.string.w_to), it.line()) },
        KeyValue(stringResource(R.string.w_mode), s.mode),
        KeyValue(stringResource(R.string.w_amount), money(s.amount), total = true),
    )) }
    Text(stringResource(R.string.w_enter_otp), style = AslTheme.type.title, color = AslTheme.colors.textPrimary)
    s.setup?.otpTo?.let { Text(stringResource(R.string.w_otp_sent_to, it), style = AslTheme.type.caption, color = AslTheme.colors.textSecondary) }
    OtpInput(s.otp, vm::onOtp, length = SendMoneyViewModel.OTP_LENGTH, isError = s.otpError != null)
    s.otpError?.let { Text(com.asl.wallet.core.network.safeServerMessage(it) ?: stringResource(R.string.w_otp_wrong), style = AslTheme.type.caption, color = AslTheme.colors.error) }
    ErrorBanner(s.otpUiError)
    if (s.resendIn > 0) Text(stringResource(R.string.w_resend_in, s.resendIn), style = AslTheme.type.caption, color = AslTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    else AslButton(stringResource(R.string.w_resend), vm::resendOtp, style = AslButtonStyle.Outline, compact = true, icon = AslIcons.Refresh, loading = s.busy)
    AslPrimaryButton(stringResource(R.string.w_confirm_send), vm::confirm, enabled = s.otp.length == SendMoneyViewModel.OTP_LENGTH)
    AslButton(stringResource(R.string.w_cancel), vm::closeOtp, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline)
}

@Composable
private fun SendResult(s: SendState, shop: ShopInfo, onDone: () -> Unit, again: () -> Unit, onCall: () -> Unit, onWhatsApp: () -> Unit) {
    val title = stringResource(R.string.w_receipt)
    val context = LocalContext.current
    when (val o = s.outcome) {
        is SendOutcome.Sent -> {
            val doc = sendDoc(o.receipt)
            var printChoice by remember { mutableStateOf(false) }
            val shareTitle = stringResource(R.string.w_share_title)
            WalletScaffold(title, onDone, secure = true, close = true, bottom = {
                AslPrimaryButton(stringResource(R.string.w_done), onDone)
            }) {
                val labels = listOf(stringResource(R.string.w_share), stringResource(R.string.w_pdf), stringResource(R.string.w_print), stringResource(R.string.w_help))
                ReceiptCard(shop, stringResource(R.string.w_sent_bank), Money.format(o.receipt.amount), Status.Success,
                    doc.rows.map { KeyValue(it.first, it.second) }, footer = doc.footer, statusLabel = stringResource(R.string.w_success),
                    amountWords = AmountInWords.english(o.receipt.amount), animate = true) {
                    ReceiptActionBar(
                        onWhatsApp = { ReceiptExport.shareText(context, shareTitle, ReceiptExport.text(shop, doc)) },
                        onSavePdf = { ReceiptExport.sharePdf(context, shop, doc, shareTitle, "ASL-send-to-bank.pdf") },
                        onPrint = { printChoice = true },
                        onHelp = onCall, labels = labels,
                    )
                }
            }
            if (printChoice) WalletSheet({ printChoice = false }) {
                Text(stringResource(R.string.w_print_title), style = AslTheme.type.title, color = AslTheme.colors.textPrimary)
                ListCard {
                    ListRow(stringResource(R.string.w_print_thermal), icon = AslIcons.Print, onClick = { printChoice = false; context.findActivity()?.let { ReceiptExport.print(it, shareTitle, ReceiptExport.html(shop, doc, true), true) } }); Divider()
                    ListRow(stringResource(R.string.w_print_a4), icon = AslIcons.Document, onClick = { printChoice = false; context.findActivity()?.let { ReceiptExport.print(it, shareTitle, ReceiptExport.html(shop, doc, false), false) } })
                }
            }
        }
        is SendOutcome.Pending -> WalletScaffold(title, null, secure = true, bottom = {
            AslPrimaryButton(stringResource(R.string.w_back_wallet), onDone)
        }) {
            ResultBlock(ResultKind.Warn, stringResource(R.string.w_transfer_pending), stringResource(R.string.w_transfer_pending_sub), listOfNotNull(
                KeyValue(stringResource(R.string.w_amount), money(o.receipt?.amount ?: s.amount)),
                KeyValue(stringResource(R.string.w_to), o.receipt?.let { "${it.bankName} ••${it.accountLast4}" } ?: s.account?.let { "${it.bankName} ••${it.accountLast4}" }.orEmpty()),
                o.receipt?.txnId?.let { KeyValue(stringResource(R.string.w_txn_id), it) },
            ))
            AlertBanner(AlertKind.Pending, stringResource(R.string.w_dont_send_again), message = stringResource(R.string.w_pending_check_reports))
            HelpCard(onComplaint = onCall, onWhatsApp = onWhatsApp, onCall = onCall, title = stringResource(R.string.w_need_help))
        }
        is SendOutcome.Failed -> WalletScaffold(title, null, secure = true, bottom = {
            AslPrimaryButton(stringResource(R.string.w_try_again), again)
            AslButton(stringResource(R.string.w_back_wallet), onDone, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline)
        }) {
            ResultBlock(ResultKind.Bad, stringResource(R.string.w_transfer_failed), o.reason?.let { com.asl.wallet.core.network.safeServerMessage(it) } ?: stringResource(R.string.w_transfer_failed_sub), listOfNotNull(
                KeyValue(stringResource(R.string.w_amount), money(s.amount)),
                s.account?.let { KeyValue(stringResource(R.string.w_to), "${it.bankName} ••${it.accountLast4}") },
            ))
            Note(stringResource(R.string.w_failed_note))
        }
        null -> Unit
    }
}

/** Receipt lines: account masked to the last 4; the retailer's charges are not shown to the customer. */
@Composable
private fun sendDoc(r: SendReceipt) = ReceiptDoc(
    title = stringResource(R.string.w_sent_bank),
    status = stringResource(R.string.w_success),
    amount = Money.format(r.amount),
    rows = listOfNotNull(
        stringResource(R.string.w_to) to r.holder,
        stringResource(R.string.w_account) to "${r.bankName} ••${r.accountLast4}",
        stringResource(R.string.w_mode) to r.mode,
        r.bankRef?.let { stringResource(R.string.w_bank_ref) to it },
        r.txnId?.let { stringResource(R.string.w_txn_id) to it },
        r.time.takeIf { it.isNotBlank() }?.let { stringResource(R.string.w_date) to it },
    ),
    footer = stringResource(R.string.w_receipt_note),
)
