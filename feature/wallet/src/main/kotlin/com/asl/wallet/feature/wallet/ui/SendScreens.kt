package com.asl.wallet.feature.wallet.ui

import com.asl.wallet.core.designsystem.theme.accentFill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.animation.togetherWith
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import java.math.BigDecimal
import com.asl.wallet.core.designsystem.component.shine
import com.asl.wallet.core.designsystem.component.pressScale
import com.asl.wallet.core.designsystem.component.popIn
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
import com.asl.wallet.core.designsystem.component.BillLabels
import com.asl.wallet.core.designsystem.component.BillReceipt
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
    LocationPrompt(s.location, vm)
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
                val amountError = if (s.showErrors) s.problem?.let { amountProblemText(it, setup.limits) } else null
                BalanceHero(
                    setup.balance, s.amount.takeIf { s.problem == null }, Modifier.popIn(0, rise = 16.dp), deduct = true,
                    title = stringResource(R.string.w_po_balance), afterLabel = stringResource(R.string.w_po_after),
                )
                SectionLabel(stringResource(R.string.w_po_send_to))
                if (setup.accounts.none { it.verified }) com.asl.wallet.core.designsystem.component.AlertBanner(
                    com.asl.wallet.core.designsystem.component.AlertKind.Pending, stringResource(R.string.w_po_none_verified), message = stringResource(R.string.w_po_waiting_note),
                )
                AccountCard(s.account, error = if (s.showErrors && s.account == null) stringResource(R.string.w_account_needed) else null,
                    onClick = { accountSheet = true }, modifier = Modifier.popIn(100, rise = 16.dp))
                AmountEntry(
                    s.amountText, s.amount, amountError, setup.limits, vm::onAmount, Modifier.popIn(180, rise = 16.dp),
                    ask = stringResource(R.string.w_po_ask), autoFocus = false,
                )
                val quick = listOf(500L, 1_000L, 2_000L, 5_000L).filter { v -> BigDecimal(v) in setup.limits.min..setup.limits.max && (setup.balance == null || BigDecimal(v) <= setup.balance) }
                QuickAmounts(quick, s.amount, { vm.onAmount(it.toString()) }, Modifier.popIn(240, rise = 16.dp), title = stringResource(R.string.w_po_quick), plus = false)
                SectionLabel(stringResource(R.string.w_po_mode))
                ModeChooser(setup.modes, s.mode, vm::onMode, Modifier.popIn(300, rise = 16.dp))
                Note(stringResource(R.string.w_po_limits, Money.format(setup.limits.min, showPaise = false), Money.format(setup.limits.max, showPaise = false)))
                if (setup.notes.isNotEmpty()) AslCard {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        setup.notes.forEach { Text("• $it", style = AslTheme.type.caption, color = AslTheme.colors.textSecondary) }
                    }
                }
                ErrorBanner(s.formError)
            }
            if (accountSheet) WalletSheet({ accountSheet = false }) {
                Text(stringResource(R.string.w_choose_account), style = AslTheme.type.title, color = AslTheme.colors.textPrimary)
                // Verified first; ones waiting for verification are shown faded and can't be chosen.
                setup.accounts.sortedBy { !it.verified }.forEachIndexed { i, a ->
                    AccountCard(a, error = null, selected = a.id == s.account?.id, compact = true,
                        onClick = { if (a.verified) { vm.onAccount(a); accountSheet = false } },
                        modifier = Modifier.popIn(i * 60, rise = 10.dp).graphicsLayer { alpha = if (a.verified) 1f else 0.6f })
                }
                if (setup.accounts.any { !it.verified }) Note(stringResource(R.string.w_po_waiting_note))
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
    (s.otpTo ?: s.setup?.otpTo)?.let { Text(stringResource(R.string.w_otp_sent_to, it), style = AslTheme.type.caption, color = AslTheme.colors.textSecondary) }
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
    val o = s.outcome ?: return
    // Every outcome prints the same bill as Reports, stamped Success / Pending / Failed.
    val doc = when (o) {
        is SendOutcome.Sent -> sendDoc(o.receipt, stringResource(R.string.w_success))
        is SendOutcome.Pending -> o.receipt?.let { sendDoc(it, stringResource(R.string.w_pending)) } ?: attemptDoc(s, stringResource(R.string.w_pending), emptyList())
        is SendOutcome.Failed -> attemptDoc(s, stringResource(R.string.w_failed), listOf(
            stringResource(R.string.w_reason) to (o.reason?.let { com.asl.wallet.core.network.safeServerMessage(it) } ?: stringResource(R.string.w_transfer_failed_sub)),
        ))
    }
    val amount = (o as? SendOutcome.Sent)?.receipt?.amount ?: (o as? SendOutcome.Pending)?.receipt?.amount ?: s.amount
    val status = when (o) { is SendOutcome.Sent -> Status.Success; is SendOutcome.Pending -> Status.Pending; is SendOutcome.Failed -> Status.Failed }
    WalletScaffold(title, if (o is SendOutcome.Sent) onDone else null, secure = true, close = o is SendOutcome.Sent, bottom = {
        when (o) {
            is SendOutcome.Sent -> AslPrimaryButton(stringResource(R.string.w_done), onDone)
            is SendOutcome.Pending -> AslPrimaryButton(stringResource(R.string.w_back_wallet), onDone)
            is SendOutcome.Failed -> {
                AslPrimaryButton(stringResource(R.string.w_try_again), again)
                AslButton(stringResource(R.string.w_back_wallet), onDone, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline)
            }
        }
    }) {
        BillReceipt(
            shop, doc, status,
            BillLabels(
                listOf(stringResource(R.string.w_share), stringResource(R.string.w_pdf), stringResource(R.string.w_print), stringResource(R.string.w_help)),
                stringResource(R.string.w_share_title), stringResource(R.string.w_print_title), stringResource(R.string.w_print_thermal), stringResource(R.string.w_print_a4),
            ),
            fileName = "ASL-payout.pdf", onHelp = onCall,
            amountWords = amount?.takeIf { it.signum() > 0 }?.let { AmountInWords.english(it) },
            barcodeSeed = (o as? SendOutcome.Sent)?.receipt?.txnId,
            voice = (o as? SendOutcome.Sent)?.let { stringResource(R.string.w_voice, com.asl.wallet.core.designsystem.component.spokenRupees(it.receipt.amount)) },
        )
        when (o) {
            is SendOutcome.Pending -> {
                AlertBanner(AlertKind.Pending, stringResource(R.string.w_dont_send_again), message = stringResource(R.string.w_pending_check_reports))
                HelpCard(onComplaint = onCall, onWhatsApp = onWhatsApp, onCall = onCall, title = stringResource(R.string.w_need_help))
            }
            is SendOutcome.Failed -> Note(stringResource(R.string.w_failed_note))
            is SendOutcome.Sent -> Unit
        }
    }
}

/** Bill for a payout with no server receipt yet (failed, or pending without an answer): what was sent, masked. */
@Composable
private fun attemptDoc(s: SendState, status: String, extra: List<Pair<String, String>>) = ReceiptDoc(
    title = stringResource(R.string.w_send_bank),
    status = status,
    amount = s.amount?.let { Money.format(it) },
    rows = listOfNotNull(
        s.account?.let { stringResource(R.string.w_to) to it.holder },
        s.account?.let { stringResource(R.string.w_account) to "${it.bankName} ••${it.accountLast4}" },
        stringResource(R.string.w_mode) to s.mode,
    ) + extra,
    footer = stringResource(R.string.w_receipt_note),
)

/** Receipt lines: account masked to the last 4; the retailer's charges are not shown to the customer. */
@Composable
private fun sendDoc(r: SendReceipt, status: String) = ReceiptDoc(
    title = stringResource(R.string.w_send_bank),
    status = status,
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

/** The payout API needs the shop's location: ask for the permission, to turn location on, or to try again. */
@Composable
private fun LocationPrompt(need: LocationNeed?, vm: SendMoneyViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { r ->
        vm.onLocationChanged(r.values.any { it })
    }
    need ?: return
    val c = AslTheme.colors
    val (title, msg) = when (need) {
        LocationNeed.Permission -> stringResource(R.string.w_loc_title) to stringResource(R.string.w_loc_permission)
        LocationNeed.TurnOn -> stringResource(R.string.w_loc_title) to stringResource(R.string.w_loc_turn_on)
        LocationNeed.NoFix -> stringResource(R.string.w_loc_nofix_title) to stringResource(R.string.w_loc_nofix)
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = vm::dismissLocation,
        containerColor = c.surface,
        icon = { androidx.compose.material3.Icon(AslIcons.Location, null, tint = c.primary) },
        title = { Text(title, style = AslTheme.type.title, color = c.textPrimary) },
        text = { Text(msg, style = AslTheme.type.body, color = c.textSecondary) },
        confirmButton = {
            when (need) {
                LocationNeed.Permission -> AslButton(stringResource(R.string.w_loc_allow), {
                    ask.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                }, compact = true)
                LocationNeed.TurnOn -> AslButton(stringResource(R.string.w_loc_settings), {
                    runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                    vm.dismissLocation()
                }, compact = true)
                LocationNeed.NoFix -> AslButton(stringResource(R.string.w_loc_ok), vm::dismissLocation, compact = true)
            }
        },
        dismissButton = { if (need != LocationNeed.NoFix) AslButton(stringResource(R.string.w_cancel), vm::dismissLocation, style = AslButtonStyle.Outline, compact = true) },
    )
}

// ---------------------------------------------------------------- Payout form pieces (classic look, 10 Nov 2026)

@Composable
private fun SectionLabel(text: String) = Text(text, style = AslTheme.type.label, color = AslTheme.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))

/** Bank badge text: well-known short names (SBI, HDFC, PNB …), else the initials of up to 3 words. */
internal fun bankMark(name: String): String {
    val n = name.uppercase().replace(Regex("[^A-Z ]"), " ").replace(Regex("\\s+"), " ").trim()
    BANK_SHORT.entries.firstOrNull { n.startsWith(it.key) }?.let { return it.value }
    val words = n.split(' ').filter { it.isNotEmpty() && it !in setOf("THE", "OF", "AND", "LTD", "LIMITED", "CO", "OP", "COOPERATIVE") }
    return words.take(3).joinToString("") { it.take(1) }.ifEmpty { "BK" }
}

private val BANK_SHORT = linkedMapOf(
    "STATE BANK OF INDIA" to "SBI", "PUNJAB NATIONAL BANK" to "PNB", "BANK OF BARODA" to "BOB", "BANK OF INDIA" to "BOI",
    "UNION BANK OF INDIA" to "UBI", "CENTRAL BANK OF INDIA" to "CBI", "INDIAN OVERSEAS BANK" to "IOB", "BANK OF MAHARASHTRA" to "BOM",
    "HDFC" to "HDFC", "ICICI" to "ICICI", "AXIS" to "AXIS", "KOTAK" to "KMB", "IDBI" to "IDBI", "IDFC" to "IDFC", "UCO" to "UCO",
    "YES BANK" to "YES", "CANARA" to "CNRB", "INDIAN BANK" to "IB", "AIRTEL PAYMENTS" to "APB", "PAYTM PAYMENTS" to "PPBL",
)

/**
 * The bank account money goes to, in full detail: bank, holder, masked number, IFSC, own / relative and verified.
 * Switching accounts slides the new one in. [selected] marks it in the chooser sheet.
 */
@Composable
private fun AccountCard(
    a: SendAccount?,
    error: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    compact: Boolean = false,
) {
    val c = AslTheme.colors
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    val ring by androidx.compose.animation.animateColorAsState(
        when { error != null -> c.error; selected -> c.primary; else -> c.outline }, label = "ring",
    )
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().pressScale(interaction, 0.97f).shadow(if (selected) 8.dp else 3.dp, shape, spotColor = androidx.compose.ui.graphics.Color(0xFF1976D2))
                .clip(shape).background(c.surface).border(if (selected || error != null) 2.dp else 1.dp, ring, shape)
                .clickable(interaction, indication = androidx.compose.material3.ripple(), role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        ) {
            androidx.compose.animation.AnimatedContent(
                a, label = "account",
                transitionSpec = {
                    (androidx.compose.animation.slideInHorizontally { it / 3 } + androidx.compose.animation.fadeIn()) togetherWith
                        (androidx.compose.animation.slideOutHorizontally { -it / 3 } + androidx.compose.animation.fadeOut())
                },
            ) { acc ->
                if (acc == null) Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(46.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.tint), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(AslIcons.Bank, null, tint = c.primary, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.w_choose_account), style = AslTheme.type.bodyStrong, color = c.textPrimary)
                        Text(stringResource(R.string.w_po_choose_sub), style = AslTheme.type.caption, color = c.textSecondary)
                    }
                    androidx.compose.material3.Icon(AslIcons.Chevron, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                } else Column {
                    // Header band: bank mark, bank name and holder on the brand gradient.
                    Box(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(c.headerGradient))) {
                        com.asl.wallet.core.designsystem.component.WaveBand(Modifier.matchParentSize())
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(androidx.compose.ui.graphics.Color.White),
                                contentAlignment = Alignment.Center,
                            ) { Text(bankMark(acc.bankName), style = AslTheme.type.bodyStrong, color = c.brandNavy) }
                            Column(Modifier.weight(1f)) {
                                Text(acc.bankName, style = AslTheme.type.bodyStrong, color = androidx.compose.ui.graphics.Color.White, maxLines = 2)
                                Text(acc.holder.ifBlank { "—" }, style = AslTheme.type.caption, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f), maxLines = 1)
                            }
                            if (selected) androidx.compose.material3.Icon(AslIcons.Check, null, tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.popIn(0).size(26.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.success).padding(5.dp))
                        }
                    }
                    // Details.
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Detail(stringResource(R.string.w_po_ac_no), "•••• •••• ${acc.accountLast4}", Modifier.weight(1f), mono = true)
                            Detail(stringResource(R.string.w_po_ifsc), acc.ifsc.ifBlank { "—" }, Modifier.weight(1f), mono = true)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill(stringResource(if (acc.owner == com.asl.wallet.feature.wallet.domain.AccountOwner.Relative) R.string.w_po_relative else R.string.w_po_own),
                                AslIcons.User, c.primary, c.tint)
                            if (acc.verified) Pill(stringResource(R.string.w_po_verified), AslIcons.Shield, c.success, c.successBg, Modifier.shine(periodMs = 3_500, alpha = 0.5f))
                            else Pill(stringResource(R.string.w_po_waiting), AslIcons.Clock, c.pending, c.pendingBg)
                            Spacer(Modifier.weight(1f))
                            if (!compact) Text(stringResource(R.string.w_po_change), style = AslTheme.type.label, color = c.primary)
                        }
                    }
                }
            }
        }
        if (error != null) Text(error, style = AslTheme.type.caption, color = c.error, modifier = Modifier.padding(start = 6.dp, top = 5.dp))
    }
}

@Composable
private fun Detail(label: String, value: String, modifier: Modifier, mono: Boolean = false) = Column(modifier) {
    Text(label.uppercase(), style = AslTheme.type.micro, color = AslTheme.colors.textSecondary)
    Text(value, style = AslTheme.type.bodyStrong.copy(fontFamily = if (mono) androidx.compose.ui.text.font.FontFamily.Monospace else null, fontSize = AslTheme.type.body.fontSize),
        color = AslTheme.colors.textPrimary, maxLines = 1)
}

@Composable
private fun Pill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) = Row(
    modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
) {
    androidx.compose.material3.Icon(icon, null, tint = fg, modifier = Modifier.size(13.dp))
    Text(text, style = AslTheme.type.micro, color = fg)
}

/** IMPS / NEFT as two cards (like DMT's choice): the chosen one lifts, gets the navy ring and a check. */
@Composable
private fun ModeChooser(modes: List<String>, selected: String, onPick: (String) -> Unit, modifier: Modifier) {
    val c = AslTheme.colors
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        modes.forEach { m ->
            val on = m == selected
            val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val lift by androidx.compose.animation.core.animateFloatAsState(if (on) 1.03f else 1f,
                androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy), label = "lift")
            val ring by androidx.compose.animation.animateColorAsState(if (on) c.primary else c.outline, label = "modeRing")
            val bg by androidx.compose.animation.animateColorAsState(if (on) c.tint else c.surface, label = "modeBg")
            val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            Box(
                Modifier.weight(1f).graphicsLayer { scaleX = lift; scaleY = lift }.pressScale(interaction, 0.95f)
                    .shadow(if (on) 6.dp else 0.dp, shape, spotColor = androidx.compose.ui.graphics.Color(0xFF1976D2)).clip(shape).background(bg).border(if (on) 2.dp else 1.dp, ring, shape)
                    .clickable(interaction, indication = null, role = androidx.compose.ui.semantics.Role.RadioButton) {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); onPick(m)
                    }
                    .padding(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    androidx.compose.material3.Icon(if (m == "IMPS") AslIcons.Send else AslIcons.Clock, null, tint = if (on) c.primary else c.textSecondary, modifier = Modifier.size(22.dp))
                    Text(m, style = AslTheme.type.title, color = if (on) c.primary else c.textPrimary)
                    Text(stringResource(if (m == "IMPS") R.string.w_po_imps_sub else R.string.w_po_neft_sub), style = AslTheme.type.caption, color = c.textSecondary)
                }
                androidx.compose.animation.AnimatedVisibility(on, Modifier.align(Alignment.TopEnd),
                    enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut()) {
                    androidx.compose.material3.Icon(AslIcons.Check, null, tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(20.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.accentFill).padding(4.dp))
                }
            }
        }
    }
}
