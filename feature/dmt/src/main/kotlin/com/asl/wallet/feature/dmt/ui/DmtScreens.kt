package com.asl.wallet.feature.dmt.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.asl.wallet.core.common.format.AmountInWords
import com.asl.wallet.core.common.format.Masking
import com.asl.wallet.core.common.format.Money
import com.asl.wallet.core.designsystem.component.AlertBanner
import com.asl.wallet.core.designsystem.component.AlertKind
import com.asl.wallet.core.designsystem.component.AmountCard
import com.asl.wallet.core.designsystem.component.AslButton
import com.asl.wallet.core.designsystem.component.AslButtonStyle
import com.asl.wallet.core.designsystem.component.AslCard
import com.asl.wallet.core.designsystem.component.AslPrimaryButton
import com.asl.wallet.core.designsystem.component.AslTextField
import com.asl.wallet.core.designsystem.component.AslTopBar
import com.asl.wallet.core.designsystem.component.ChoiceChips
import com.asl.wallet.core.designsystem.component.Divider
import com.asl.wallet.core.designsystem.component.HelpCard
import com.asl.wallet.core.designsystem.component.KeyValue
import com.asl.wallet.core.designsystem.component.KeyValueList
import com.asl.wallet.core.designsystem.component.ListCard
import com.asl.wallet.core.designsystem.component.ListRow
import com.asl.wallet.core.designsystem.component.OtpInput
import com.asl.wallet.core.designsystem.component.ProcessingScreen
import com.asl.wallet.core.designsystem.component.ReceiptActionBar
import com.asl.wallet.core.designsystem.component.ReceiptCard
import com.asl.wallet.core.designsystem.component.ResultKind
import com.asl.wallet.core.designsystem.component.ResultView
import com.asl.wallet.core.designsystem.component.SectionHeader
import com.asl.wallet.core.designsystem.component.SegmentedControl
import com.asl.wallet.core.designsystem.component.ShopInfo
import com.asl.wallet.core.designsystem.component.SkeletonRow
import com.asl.wallet.core.designsystem.component.SlideToConfirm
import com.asl.wallet.core.designsystem.component.StatePresets
import com.asl.wallet.core.designsystem.component.StateView
import com.asl.wallet.core.designsystem.component.Status
import com.asl.wallet.core.designsystem.component.StatusPill
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.receipt.ReceiptDoc
import com.asl.wallet.core.designsystem.receipt.ReceiptExport
import com.asl.wallet.core.designsystem.theme.AslGradients
import com.asl.wallet.core.designsystem.theme.AslShapes
import com.asl.wallet.core.designsystem.theme.AslTheme
import com.asl.wallet.core.network.UiError
import com.asl.wallet.core.network.safeServerMessage
import com.asl.wallet.core.security.ui.SecureScreen
import com.asl.wallet.core.security.ui.findActivity
import com.asl.wallet.feature.dmt.R
import com.asl.wallet.feature.dmt.domain.DmtBank
import com.asl.wallet.feature.dmt.domain.DmtOutcome
import com.asl.wallet.feature.dmt.domain.DmtReceipt
import com.asl.wallet.feature.dmt.domain.DmtRules
import com.asl.wallet.feature.dmt.domain.Gender
import com.asl.wallet.feature.dmt.domain.Recipient
import com.asl.wallet.feature.dmt.domain.RecipientForm.Field
import com.asl.wallet.feature.dmt.domain.TransferMode
import java.math.BigDecimal

// ---------------------------------------------------------------- shared bits

@Composable
private fun Scaffold(title: String, onBack: (() -> Unit)?, secure: Boolean = false, bottom: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = AslTheme.colors
    if (secure) SecureScreen()
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        AslTopBar(title, onBack = onBack)
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
    UiError.NoInternet -> stringResource(R.string.d_offline)
    UiError.Timeout -> stringResource(R.string.d_timeout)
    else -> stringResource(R.string.d_generic)
}

@Composable
private fun ErrorBanner(e: UiError?) { e?.let { AlertBanner(AlertKind.Error, errorText(it)) } }

@Composable
private fun Note(text: String) = Text(text, style = AslTheme.type.caption, color = AslTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

/**
 * [dismissible] = false for OTP sheets: a tap outside, a swipe or Back can't close them by accident (only Cancel),
 * and their window blocks screenshots (a sheet is its own window, so the screen's block doesn't cover it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, dismissible: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { if (dismissible) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { dismissible || it != SheetValue.Hidden }),
        properties = ModalBottomSheetProperties(securePolicy = if (dismissible) SecureFlagPolicy.Inherit else SecureFlagPolicy.SecureOn, shouldDismissOnBackPress = dismissible),
        containerColor = AslTheme.colors.surface, shape = AslShapes.sheet,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

private fun rupees(a: BigDecimal?) = a?.let { Money.format(it) } ?: "—"
private fun rupees(a: Int) = Money.format(a.toLong())

private class PickPhone : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit) = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
}

private fun readPhone(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull()

// ---------------------------------------------------------------- 5B.1–5B.21

@Composable
fun DmtScreen(
    shop: ShopInfo,
    onBack: () -> Unit,
    onAddMoney: () -> Unit,
    onFundRequest: () -> Unit,
    onCallSupport: () -> Unit,
    onWhatsAppSupport: () -> Unit,
    vm: DmtViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    BackHandler(enabled = s.page != DmtPage.Sending && s.page != DmtPage.Result) { if (!vm.back()) onBack() }
    when (s.page) {
        DmtPage.Mobile -> MobilePage(s, vm, onBack)
        DmtPage.Register -> RegisterPage(s, vm)
        DmtPage.Customer -> CustomerPage(s, vm)
        DmtPage.AddRecipient -> AddRecipientPage(s, vm)
        DmtPage.RecipientSaved -> Scaffold(stringResource(R.string.d_add_recipient), null, bottom = {
            AslPrimaryButton(stringResource(R.string.d_send_money), vm::sendToSaved)
            AslButton(stringResource(R.string.d_back_customer), { vm.back() }, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline)
        }) {
            ResultView(ResultKind.Ok, stringResource(R.string.d_recipient_saved), stringResource(R.string.d_ready_to_send), listOfNotNull(
                KeyValue(stringResource(R.string.d_name), (s.form.bankName ?: s.form.name).uppercase()),
                KeyValue(stringResource(R.string.d_bank), "${s.form.bank?.name.orEmpty()} ••${s.form.account.takeLast(4)}"),
                KeyValue(stringResource(R.string.d_name_check), stringResource(if (s.form.verified) R.string.d_verified else R.string.d_not_verified)),
            ))
        }
        DmtPage.Amount -> AmountPage(s, vm)
        DmtPage.Review -> ReviewPage(s, vm, onAddMoney, onFundRequest)
        DmtPage.Sending -> {
            SecureScreen()
            BackHandler {}
            ProcessingScreen(
                stringResource(R.string.d_send_money), stringResource(R.string.d_sending, rupees(s.amount ?: 0)), stringResource(R.string.d_dont_close),
                listOf(stringResource(R.string.d_step_otp), stringResource(R.string.d_step_bank, s.recipient?.bankName.orEmpty()), stringResource(R.string.d_step_done)),
                current = 1, colors = AslGradients.Service.SendMoney.colors, icon = AslIcons.Send,
            )
        }
        DmtPage.Result -> ResultPage(s, vm, shop, onBack, onCallSupport, onWhatsAppSupport)
    }
    if (s.otpFor != null) OtpSheet(s, vm)
}

@Composable
private fun MobilePage(s: DmtState, vm: DmtViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(PickPhone()) { uri -> uri?.let { readPhone(context, it) }?.let(vm::onMobile) }
    val typingDigits = s.entry.isNotEmpty() && s.entry.none(Char::isLetter)
    Scaffold(stringResource(R.string.d_title), onBack, bottom = {
        AslPrimaryButton(stringResource(R.string.d_continue), vm::lookup, loading = s.busy, enabled = s.mobileOk)
    }) {
        if (s.recent.isEmpty()) AlertBanner(AlertKind.Info, stringResource(R.string.d_intro), message = stringResource(R.string.d_intro_msg), icon = AslIcons.User)
        AslTextField(
            s.entry, vm::onMobile, stringResource(R.string.d_customer_search), placeholder = stringResource(R.string.d_search_ph), maxLength = 40,
            trailingIcon = AslIcons.Contacts, onTrailingClick = { runCatching { pick.launch(Unit) } }, trailingLabel = stringResource(R.string.d_pick_contact),
            error = if (typingDigits && (s.showErrors || s.mobile.length == 10) && !s.mobileOk) stringResource(R.string.d_mobile_bad) else null,
        )
        if (s.recent.isNotEmpty()) {
            SectionHeader(stringResource(R.string.d_recent_customers), action = if (s.entry.isBlank()) stringResource(R.string.d_clear) else null, onAction = vm::clearRecent)
            val list = s.visibleRecent
            if (list.isEmpty()) Note(stringResource(if (typingDigits) R.string.d_no_recent_number else R.string.d_no_recent_name))
            else ListCard {
                list.forEachIndexed { i, c ->
                    if (i > 0) Divider()
                    ListRow(
                        c.name.ifBlank { Masking.mobile(c.mobile) },
                        subtitle = listOfNotNull(Masking.mobile(c.mobile), whenLabel(c.at), c.last).joinToString(" · "),
                        icon = AslIcons.User, iconColors = AslGradients.Service.SendMoney.colors, onClick = { vm.pickRecent(c) },
                    )
                }
            }
        }
        ErrorBanner(s.error)
    }
}

/** "Today", "Yesterday" or "4 Nov". */
@Composable
private fun whenLabel(at: Long): String {
    val today = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = at }
    fun same(a: java.util.Calendar, b: java.util.Calendar) = a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) && a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
    val yesterday = (today.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    return when {
        same(today, then) -> stringResource(R.string.d_today)
        same(yesterday, then) -> stringResource(R.string.d_yesterday)
        else -> java.text.SimpleDateFormat("d MMM", java.util.Locale.ENGLISH).format(java.util.Date(at))
    }
}

@Composable
private fun RegisterPage(s: DmtState, vm: DmtViewModel) {
    Scaffold(stringResource(R.string.d_new_customer), { vm.back() }, bottom = {
        AslPrimaryButton(stringResource(R.string.d_send_otp_customer), vm::register, loading = s.busy)
    }) {
        AlertBanner(AlertKind.Pending, stringResource(R.string.d_not_registered, Masking.mobile(s.mobile)), message = stringResource(R.string.d_not_registered_msg), icon = AslIcons.User)
        AslTextField(s.regName, vm::onRegName, stringResource(R.string.d_customer_name), maxLength = 60,
            error = if (s.showErrors && !DmtRules.isCustomerName(s.regName)) stringResource(R.string.d_name_bad) else null)
        HeaderText(stringResource(R.string.d_gender))
        SegmentedControl(listOf(stringResource(R.string.d_male), stringResource(R.string.d_female), stringResource(R.string.d_other)), s.gender.ordinal, { vm.onGender(Gender.entries[it]) })
        ErrorBanner(s.error)
    }
}

@Composable
private fun HeaderText(t: String) = Text(t, style = AslTheme.type.label, color = AslTheme.colors.textSecondary)

@Composable
private fun CustomerCard(s: DmtState, onChange: () -> Unit) {
    val c = s.customer ?: return
    val c2 = AslTheme.colors
    AslCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ListRow(c.name.ifBlank { Masking.mobile(c.mobile) }, subtitle = stringResource(R.string.d_verified_customer, Masking.mobile(c.mobile)), icon = AslIcons.User,
                iconColors = AslGradients.Service.SendMoney.colors, onClick = onChange,
                trailing = { Text(stringResource(R.string.d_change), style = AslTheme.type.label, color = c2.primary) })
            val total = c.totalLimit; val used = c.usedLimit; val left = c.leftLimit
            if (total != null && used != null && left != null && total.signum() > 0) {
                Row { Text(stringResource(R.string.d_limit_used), style = AslTheme.type.caption, color = c2.textSecondary, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.d_of, rupees(used), rupees(total)), style = AslTheme.type.label, color = c2.textPrimary) }
                LinearProgressIndicator(progress = { (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp), color = c2.primary, trackColor = c2.outline)
                Text(stringResource(R.string.d_left_month, rupees(left)), style = AslTheme.type.caption, color = c2.textSecondary)
            }
        }
    }
}

@Composable
private fun RecipientRow(r: Recipient, onClick: () -> Unit) = ListRow(
    r.name, subtitle = "${r.bankName} · ••${r.last4}", icon = AslIcons.Bank,
    iconColors = if (r.verified) AslGradients.Service.Bills.colors else AslGradients.Service.Neutral.colors, onClick = onClick,
    trailing = { StatusPill(stringResource(if (r.verified) R.string.d_verified else R.string.d_not_verified), if (r.verified) Status.Success else Status.Neutral) },
)

@Composable
private fun CustomerPage(s: DmtState, vm: DmtViewModel) {
    Scaffold(stringResource(R.string.d_title), { vm.back() }, bottom = {
        AslButton(stringResource(R.string.d_add_recipient), vm::startAddRecipient, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline, icon = AslIcons.Plus)
    }) {
        CustomerCard(s, vm::changeCustomer)
        SectionHeader(stringResource(R.string.d_recipients))
        if (s.recipients.size > 4) AslTextField(s.query, vm::onQuery, stringResource(R.string.d_search_recipient), trailingIcon = AslIcons.Search)
        when {
            s.recipientsLoading && s.recipients.isEmpty() -> ListCard { Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { repeat(3) { SkeletonRow() } } }
            s.recipientsError != null && s.recipients.isEmpty() -> StateView(AslIcons.WifiOff, StatePresets.offlineColors, stringResource(R.string.d_load_error), errorText(s.recipientsError)) {
                AslPrimaryButton(stringResource(R.string.d_try_again), vm::loadRecipients, icon = AslIcons.Refresh)
            }
            s.recipients.isEmpty() -> StateView(AslIcons.Bank, AslGradients.Service.Bills.colors, stringResource(R.string.d_no_recipients), stringResource(R.string.d_no_recipients_msg))
            s.visibleRecipients.isEmpty() -> Note(stringResource(R.string.d_no_match))
            else -> ListCard { s.visibleRecipients.forEachIndexed { i, r -> if (i > 0) Divider(); RecipientRow(r) { vm.pickRecipient(r) } } }
        }
    }
}

// ---------------------------------------------------------------- 5B.7–5B.11 add recipient

@Composable
private fun AddRecipientPage(s: DmtState, vm: DmtViewModel) {
    var bankSheet by remember { mutableStateOf(false) }
    val f = s.form
    val p = if (s.showErrors) f.problems() else emptySet()
    val c = AslTheme.colors
    Scaffold(stringResource(R.string.d_add_recipient), { vm.back() }, secure = true, bottom = {
        AslPrimaryButton(stringResource(R.string.d_save_recipient), vm::saveRecipient, loading = s.busy, enabled = (s.nameCheck as? NameCheck.Done)?.differs != true)
    }) {
        HeaderText(stringResource(R.string.d_bank))
        ListCard { ListRow(f.bank?.name ?: stringResource(R.string.d_choose_bank), icon = AslIcons.Bank, onClick = { vm.loadBanks(); bankSheet = true }) }
        if (Field.Bank in p) Text(stringResource(R.string.d_bank_needed), style = AslTheme.type.caption, color = c.error)
        AslTextField(f.account, vm::onAccount, stringResource(R.string.d_account_no), keyboardType = KeyboardType.NumberPassword, maxLength = 18,
            error = if (Field.Account in p) stringResource(R.string.d_account_bad) else null)
        AslTextField(f.confirm, vm::onConfirm, stringResource(R.string.d_account_confirm), keyboardType = KeyboardType.Number, maxLength = 18,
            error = if (Field.Confirm in p || (f.confirm.length >= f.account.length && f.confirm.isNotEmpty() && f.confirm != f.account)) stringResource(R.string.d_account_mismatch) else null,
            success = if (f.confirm == f.account && f.account.length >= 9) stringResource(R.string.d_account_match) else null)
        AslTextField(f.ifsc, vm::onIfsc, stringResource(R.string.d_ifsc), maxLength = 11, error = if (Field.Ifsc in p) stringResource(R.string.d_ifsc_bad) else null)
        AslTextField(f.mobile, vm::onRecipientMobile, stringResource(R.string.d_recipient_mobile), keyboardType = KeyboardType.Phone, maxLength = 10,
            error = if (Field.Mobile in p) stringResource(R.string.d_mobile_bad) else null)
        AslTextField(f.name, vm::onRecipientName, stringResource(R.string.d_recipient_name), maxLength = 100,
            error = if (Field.Name in p) stringResource(R.string.d_name_bad) else null,
            success = if (f.verified) stringResource(R.string.d_name_from_bank) else null)
        when (val n = s.nameCheck) {
            NameCheck.Idle, NameCheck.Confirm -> AslButton(stringResource(R.string.d_check_name, rupees(DmtRules.NAME_CHECK_FEE)), vm::askNameCheck, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline, icon = AslIcons.Shield)
            NameCheck.Loading -> AslButton(stringResource(R.string.d_checking_name), {}, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline, loading = true)
            is NameCheck.Done -> if (n.differs) {
                AlertBanner(AlertKind.Pending, stringResource(R.string.d_name_differs), message = stringResource(R.string.d_name_differs_msg, f.name, n.bankName))
                AslPrimaryButton(stringResource(R.string.d_use_bank_name), vm::useBankName)
            } else AlertBanner(AlertKind.Success, stringResource(R.string.d_bank_says, n.bankName), message = stringResource(R.string.d_account_active))
            is NameCheck.Error -> AlertBanner(AlertKind.Error, errorText(n.e))
        }
        Note(stringResource(R.string.d_name_check_note))
        ErrorBanner(s.error)
    }
    if (s.nameCheck == NameCheck.Confirm) AlertDialog(
        onDismissRequest = vm::cancelNameCheck,
        containerColor = c.surface,
        title = { Text(stringResource(R.string.d_check_q), style = AslTheme.type.title, color = c.textPrimary) },
        text = { Text(stringResource(R.string.d_check_q_msg, rupees(DmtRules.NAME_CHECK_FEE)), style = AslTheme.type.body, color = c.textSecondary) },
        confirmButton = { AslButton(stringResource(R.string.d_check_fee, rupees(DmtRules.NAME_CHECK_FEE)), vm::runNameCheck, compact = true) },
        dismissButton = { AslButton(stringResource(R.string.d_cancel), vm::cancelNameCheck, style = AslButtonStyle.Outline, compact = true) },
    )
    if (bankSheet) BankSheet(s.banks, s.banksLoading, onPick = { vm.onBank(it); bankSheet = false }) { bankSheet = false }
}

@Composable
private fun BankSheet(all: List<DmtBank>, loading: Boolean, onPick: (DmtBank) -> Unit, onDismiss: () -> Unit) = Sheet(onDismiss) {
    var q by rememberSaveable { mutableStateOf("") }
    val results = remember(q, all) {
        val t = q.trim()
        if (t.isEmpty()) all else all.filter { it.name.startsWith(t, true) } + all.filter { !it.name.startsWith(t, true) && it.name.contains(t, true) }
    }
    Text(stringResource(R.string.d_choose_bank), style = AslTheme.type.title, color = AslTheme.colors.textPrimary)
    AslTextField(q, { q = it.take(40) }, stringResource(R.string.d_search_bank), trailingIcon = AslIcons.Search)
    when {
        loading -> repeat(3) { SkeletonRow() }
        results.isEmpty() -> Note(stringResource(R.string.d_no_match))
        else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            items(results, key = { it.id }) { b -> ListRow(b.name, icon = AslIcons.Bank, trailing = null, onClick = { onPick(b) }); Divider() }
        }
    }
}

// ---------------------------------------------------------------- 5B.13–5B.14, 5B.20–5B.21 amount and review

@Composable
private fun AmountPage(s: DmtState, vm: DmtViewModel) {
    val r = s.recipient ?: return
    Scaffold(stringResource(R.string.d_send_money), { vm.back() }, bottom = {
        AslPrimaryButton(stringResource(R.string.d_continue), vm::review, loading = s.busy, enabled = !s.overLimit)
    }) {
        CustomerCard(s, vm::changeCustomer)
        ListCard { RecipientRow(r) { vm.back() } }
        AmountCard(s.amount?.let { BigDecimal(it) })
        val quick = listOf(1000, 2000, 5000)
        ChoiceChips(quick.map(::rupees), quick.indexOf(s.amount).takeIf { it >= 0 }, { vm.onAmount(quick[it].toString()) })
        AslTextField(s.amountText, vm::onAmount, stringResource(R.string.d_amount), keyboardType = KeyboardType.Number, maxLength = 6,
            error = if (s.showErrors && s.amount == null) stringResource(R.string.d_amount_bad, rupees(DmtRules.MIN_AMOUNT)) else null)
        SegmentedControl(TransferMode.entries.map { it.api }, s.mode.ordinal, { vm.onMode(TransferMode.entries[it]) })
        if (s.overLimit) AlertBanner(AlertKind.Error, stringResource(R.string.d_over_limit), message = stringResource(R.string.d_over_limit_msg, rupees(s.customer?.leftLimit)))
        Note(stringResource(R.string.d_split_note))
        ErrorBanner(s.error)
    }
}

@Composable
private fun ReviewPage(s: DmtState, vm: DmtViewModel, onAddMoney: () -> Unit, onFundRequest: () -> Unit) {
    val r = s.recipient ?: return
    val c = s.customer ?: return
    val amount = s.amount ?: 0
    Scaffold(stringResource(R.string.d_review), vm::backFromReview, secure = true, bottom = {
        when {
            s.shortBy != null -> {
                AslPrimaryButton(stringResource(R.string.d_add_money), onAddMoney, icon = AslIcons.Plus)
                AslButton(stringResource(R.string.d_fund_request), onFundRequest, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline)
            }
            s.busy -> AslPrimaryButton(stringResource(R.string.d_sending_otp), {}, loading = true)
            else -> SlideToConfirm(stringResource(R.string.d_slide, rupees(amount)), vm::slideToSend, resetKey = s.otpFor to s.error)
        }
    }) {
        AslCard { KeyValueList(listOfNotNull(
            KeyValue(stringResource(R.string.d_customer), "${c.name} · ${Masking.mobile(c.mobile)}"),
            KeyValue(stringResource(R.string.d_to), r.name),
            KeyValue(stringResource(R.string.d_bank), "${r.bankName} ••${r.last4}"),
            KeyValue(stringResource(R.string.d_mode), s.mode.api),
            KeyValue(stringResource(R.string.d_amount), rupees(amount)),
            KeyValue(stringResource(R.string.d_charge), s.charges?.let { rupees(it.charge) } ?: stringResource(R.string.d_charge_plan)),
            KeyValue(stringResource(R.string.d_total), s.total?.let { if (s.chargesKnown) rupees(it) else stringResource(R.string.d_total_plus, rupees(it)) } ?: "—", total = true),
        )) }
        if (!s.chargesKnown) Note(stringResource(R.string.d_charge_soon))
        s.shortBy?.let { AlertBanner(AlertKind.Error, stringResource(R.string.d_low_balance, rupees(s.balance)), message = stringResource(R.string.d_low_balance_msg, rupees(it)), icon = AslIcons.Wallet) }
            ?: AlertBanner(AlertKind.Info, stringResource(R.string.d_read_out), message = stringResource(R.string.d_no_reverse), icon = AslIcons.Shield)
        ErrorBanner(s.error)
    }
}

// ---------------------------------------------------------------- OTP sheet

@Composable
private fun OtpSheet(s: DmtState, vm: DmtViewModel) = Sheet(vm::closeOtp, dismissible = false) {
    val kind = s.otpFor ?: return@Sheet
    val c = AslTheme.colors
    val (title, sub) = when (kind) {
        OtpFor.Customer -> stringResource(R.string.d_otp_customer) to stringResource(R.string.d_otp_customer_sub, Masking.mobile(s.mobile))
        OtpFor.Recipient -> stringResource(R.string.d_otp_recipient) to stringResource(R.string.d_otp_recipient_sub, Masking.mobile(s.form.mobile))
        OtpFor.Transfer -> stringResource(R.string.d_otp_transfer) to stringResource(R.string.d_otp_transfer_sub)
    }
    if (kind == OtpFor.Transfer) {
        AslCard { KeyValueList(listOf(KeyValue(stringResource(R.string.d_to), "${s.recipient?.name.orEmpty()} · ••${s.recipient?.last4.orEmpty()}"), KeyValue(stringResource(R.string.d_amount), rupees(s.amount ?: 0), total = true))) }
    }
    Text(title, style = AslTheme.type.title, color = c.textPrimary)
    Text(sub, style = AslTheme.type.caption, color = c.textSecondary)
    if (kind == OtpFor.Recipient && s.recipientExisted) AlertBanner(AlertKind.Info, stringResource(R.string.d_recipient_existed))
    OtpInput(s.otp, vm::onOtp, length = DmtViewModel.OTP_LENGTH, isError = s.otpError != null || s.otpUiError != null)
    s.otpError?.let { Text(safeServerMessage(it) ?: stringResource(R.string.d_otp_wrong), style = AslTheme.type.caption, color = c.error) }
    ErrorBanner(s.otpUiError)
    if (s.resendIn > 0) Text(stringResource(R.string.d_resend_in, s.resendIn), style = AslTheme.type.caption, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    else AslButton(stringResource(R.string.d_resend), vm::resendOtp, style = AslButtonStyle.Outline, compact = true, icon = AslIcons.Refresh, loading = s.busy)
    AslPrimaryButton(
        stringResource(when (kind) { OtpFor.Customer -> R.string.d_verify_customer; OtpFor.Recipient -> R.string.d_verify_recipient; OtpFor.Transfer -> R.string.d_confirm_send }),
        vm::submitOtp, enabled = s.otp.length == DmtViewModel.OTP_LENGTH, loading = s.busy && kind != OtpFor.Transfer,
    )
    AslButton(stringResource(R.string.d_cancel), vm::closeOtp, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline, enabled = !s.busy)
}

// ---------------------------------------------------------------- 5B.17–5B.19 results

@Composable
private fun ResultPage(s: DmtState, vm: DmtViewModel, shop: ShopInfo, onHome: () -> Unit, onCall: () -> Unit, onWhatsApp: () -> Unit) {
    val context = LocalContext.current
    val title = stringResource(R.string.d_receipt)
    BackHandler(onBack = vm::backToCustomer)
    when (val o = s.outcome) {
        is DmtOutcome.Sent -> {
            val doc = receiptDoc(o.receipt)
            var printChoice by remember { mutableStateOf(false) }
            val shareTitle = stringResource(R.string.d_share_title)
            Scaffold(title, vm::backToCustomer, secure = true, bottom = {
                AslPrimaryButton(stringResource(R.string.d_another_recipient), vm::backToCustomer)
                AslButton(stringResource(R.string.d_home), onHome, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline, icon = AslIcons.Home)
            }) {
                val labels = listOf(stringResource(R.string.d_share), stringResource(R.string.d_pdf), stringResource(R.string.d_print), stringResource(R.string.d_help))
                ReceiptCard(shop, doc.title, rupees(o.receipt.amount), Status.Success, doc.rows.map { KeyValue(it.first, it.second) }, footer = doc.footer,
                    statusLabel = stringResource(R.string.d_success), amountWords = AmountInWords.english(o.receipt.amount), barcodeSeed = o.receipt.txnId, animate = true) {
                    ReceiptActionBar(
                        onWhatsApp = { ReceiptExport.shareText(context, shareTitle, ReceiptExport.text(shop, doc)) },
                        onSavePdf = { ReceiptExport.sharePdf(context, shop, doc, shareTitle, "ASL-money-transfer.pdf") },
                        onPrint = { printChoice = true }, onHelp = onCall, labels = labels,
                    )
                }
            }
            if (printChoice) Sheet({ printChoice = false }) {
                Text(stringResource(R.string.d_print_title), style = AslTheme.type.title, color = AslTheme.colors.textPrimary)
                ListCard {
                    ListRow(stringResource(R.string.d_print_thermal), icon = AslIcons.Print, onClick = { printChoice = false; context.findActivity()?.let { ReceiptExport.print(it, shareTitle, ReceiptExport.html(shop, doc, true), true) } }); Divider()
                    ListRow(stringResource(R.string.d_print_a4), icon = AslIcons.Document, onClick = { printChoice = false; context.findActivity()?.let { ReceiptExport.print(it, shareTitle, ReceiptExport.html(shop, doc, false), false) } })
                }
            }
        }
        is DmtOutcome.Pending -> Scaffold(title, null, secure = true, bottom = {
            AslPrimaryButton(stringResource(R.string.d_back_customer), vm::backToCustomer)
        }) {
            ResultView(ResultKind.Warn, stringResource(R.string.d_pending_title), stringResource(R.string.d_pending_sub), listOfNotNull(
                KeyValue(stringResource(R.string.d_to), "${o.receipt.recipient} · ••${o.receipt.last4}"),
                KeyValue(stringResource(R.string.d_amount), rupees(o.receipt.amount)),
                o.receipt.txnId?.let { KeyValue(stringResource(R.string.d_txn_id), it) },
            ))
            AlertBanner(AlertKind.Pending, stringResource(R.string.d_held), message = stringResource(R.string.d_held_msg))
            HelpCard(onComplaint = onCall, onWhatsApp = onWhatsApp, onCall = onCall, title = stringResource(R.string.d_need_help))
        }
        is DmtOutcome.Failed -> Scaffold(title, null, secure = true, bottom = {
            AslPrimaryButton(stringResource(R.string.d_back_customer), vm::backToCustomer)
            AslButton(stringResource(R.string.d_home), onHome, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline)
        }) {
            ResultView(ResultKind.Bad, stringResource(R.string.d_failed_title), stringResource(R.string.d_failed_sub), listOfNotNull(
                KeyValue(stringResource(R.string.d_to), "${o.receipt.recipient} · ••${o.receipt.last4}"),
                KeyValue(stringResource(R.string.d_amount), stringResource(R.string.d_returned, rupees(o.receipt.amount))),
                KeyValue(stringResource(R.string.d_reason), o.reason?.let(::safeServerMessage) ?: stringResource(R.string.d_reason_unknown)),
            ))
        }
        is DmtOutcome.WrongOtp, is DmtOutcome.Refused, null -> Unit
    }
}

/** Customer receipt: masked numbers, no retailer commission. */
@Composable
private fun receiptDoc(r: DmtReceipt) = ReceiptDoc(
    title = stringResource(R.string.d_receipt_title),
    status = stringResource(R.string.d_success),
    amount = rupees(r.amount),
    rows = listOfNotNull(
        stringResource(R.string.d_sender) to "${r.customerName} · ${Masking.mobile(r.customerMobile)}",
        stringResource(R.string.d_to) to r.recipient,
        stringResource(R.string.d_bank) to "${r.bankName} ••${r.last4}",
        stringResource(R.string.d_mode) to r.mode,
        r.charge?.let { stringResource(R.string.d_charge) to rupees(it) },
        r.bankRefs.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.d_bank_ref) to it.joinToString(", ") },
        r.txnId?.let { stringResource(R.string.d_txn_id) to it },
        r.time.takeIf { it.isNotBlank() }?.let { stringResource(R.string.d_date) to it },
    ),
    footer = stringResource(R.string.d_receipt_note),
)
