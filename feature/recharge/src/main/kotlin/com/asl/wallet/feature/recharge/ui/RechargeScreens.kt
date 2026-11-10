package com.asl.wallet.feature.recharge.ui

import com.asl.wallet.core.designsystem.component.keyboardAware
import androidx.compose.foundation.layout.imePadding
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.asl.wallet.core.designsystem.component.ProcessingScreen
import com.asl.wallet.core.designsystem.component.BillLabels
import com.asl.wallet.core.designsystem.component.BillReceipt
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
import com.asl.wallet.core.designsystem.component.TransactionRow
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
import com.asl.wallet.feature.recharge.R
import com.asl.wallet.feature.recharge.domain.Operator
import com.asl.wallet.feature.recharge.domain.Plan
import com.asl.wallet.feature.recharge.domain.RechargeOutcome
import com.asl.wallet.feature.recharge.domain.RechargeReceipt
import com.asl.wallet.feature.recharge.domain.RechargeStatus
import com.asl.wallet.feature.recharge.domain.RechargeType
import java.math.BigDecimal

// ---------------------------------------------------------------- shared bits

@Composable
private fun Scaffold(
    title: String,
    onBack: (() -> Unit)?,
    secure: Boolean = false,
    close: Boolean = false,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AslTheme.colors
    if (secure) SecureScreen()
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().keyboardAware()) {
        AslTopBar(title, onBack = onBack, closeIcon = close)
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
    UiError.NoInternet -> stringResource(R.string.r_offline)
    UiError.Timeout -> stringResource(R.string.r_timeout)
    else -> stringResource(R.string.r_generic)
}

@Composable
private fun Note(text: String) = Text(text, style = AslTheme.type.caption, color = AslTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = AslTheme.colors.surface, shape = AslShapes.sheet) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

private fun rupees(a: Int) = Money.format(a.toLong())
private fun rupees(a: BigDecimal?) = a?.let { Money.format(it) } ?: "—"

/** Phone's own contact picker for one phone number: no contacts permission, nothing else is read. */
private class PickPhone : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit) = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
}

private fun readPhone(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()

// ---------------------------------------------------------------- 5.1–5.19

@Composable
fun RechargeScreen(
    startType: RechargeType,
    shop: ShopInfo,
    onBack: () -> Unit,
    onAddMoney: () -> Unit,
    onFundRequest: () -> Unit,
    onCallSupport: () -> Unit,
    onWhatsAppSupport: () -> Unit,
    vm: RechargeViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(startType) { vm.start(startType) }
    when (s.page) {
        Page.Form -> FormPage(s, vm, onBack)
        Page.Plans -> { BackHandler(onBack = vm::closePlans); PlansPage(s, vm) }
        Page.Review -> { BackHandler(onBack = vm::backToForm); ReviewPage(s, vm, onAddMoney, onFundRequest) }
        Page.Processing -> {
            SecureScreen()
            BackHandler {}
            ProcessingScreen(
                stringResource(R.string.r_title), stringResource(R.string.r_recharging, rupees(s.amount ?: 0)), stringResource(R.string.r_dont_close),
                listOf(stringResource(R.string.r_step_debit), stringResource(R.string.r_step_sending, s.operator?.name.orEmpty()), stringResource(R.string.r_step_done)),
                current = 1, colors = AslGradients.Service.Recharge.colors, icon = if (s.type == RechargeType.Dth) AslIcons.Tv else AslIcons.Phone,
            )
        }
        Page.Result -> ResultPage(s, vm, shop, onBack, onCallSupport, onWhatsAppSupport)
    }
}

@Composable
private fun FormPage(s: RechargeState, vm: RechargeViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var operatorSheet by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(PickPhone()) { uri -> uri?.let { readPhone(context, it) }?.let(vm::onNumber) }
    val mobile = s.type == RechargeType.Mobile
    val dthOff = !mobile && !s.dthAvailable

    Scaffold(stringResource(R.string.r_title), onBack, bottom = if (dthOff) null else ({
        AslPrimaryButton(stringResource(R.string.r_continue), vm::review)
    })) {
        SegmentedControl(listOf(stringResource(R.string.r_mobile), stringResource(R.string.r_dth)), if (mobile) 0 else 1, { vm.setType(if (it == 0) RechargeType.Mobile else RechargeType.Dth) })
        if (dthOff) {
            StateView(AslIcons.Tv, StatePresets.offlineColors, stringResource(R.string.r_dth_na), stringResource(R.string.r_dth_na_msg)) {
                AslPrimaryButton(stringResource(R.string.r_try_again), vm::loadOperators, loading = s.listsLoading, icon = AslIcons.Refresh)
                AslButton(stringResource(R.string.r_mobile_recharge), { vm.setType(RechargeType.Mobile) }, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline)
            }
            return@Scaffold
        }
        if (mobile) MobileFields(s, vm, onPickContact = { runCatching { pick.launch(Unit) } }, onChangeOperator = { vm.loadOperators(); operatorSheet = true })
        else DthFields(s, vm)

        if (s.number.isEmpty() && s.recent.isNotEmpty()) {
            SectionHeader(stringResource(R.string.r_recent))
            ListCard {
                s.recent.forEachIndexed { i, r ->
                    if (i > 0) Divider()
                    TransactionRow(
                        initials = r.operator.take(2).uppercase(), colors = if (r.type == RechargeType.Dth) AslGradients.Service.Wallet.colors else AslGradients.Service.Bills.colors,
                        title = (if (r.type == RechargeType.Mobile) Masking.mobile(r.number) else Masking.account(r.number)) + " · " + r.operator,
                        subtitle = listOf(r.date, Money.format(r.amount, showPaise = false)).filter { it.isNotBlank() }.joinToString(" · "),
                        amount = "", statusText = statusLabel(r.status), status = r.status.pill(), onClick = { vm.repeat(r) },
                    )
                }
            }
            Note(stringResource(R.string.r_repeat_hint))
        }
    }
    if (operatorSheet) OperatorSheet(s.lists?.mobile.orEmpty(), s.listsLoading, s.operator, onPick = { vm.chooseOperator(it); operatorSheet = false }, retry = vm::loadOperators) { operatorSheet = false }
}

@Composable
private fun statusLabel(st: RechargeStatus) = stringResource(when (st) { RechargeStatus.Success -> R.string.r_success; RechargeStatus.Pending -> R.string.r_pending; RechargeStatus.Failed -> R.string.r_failed })
private fun RechargeStatus.pill() = when (this) { RechargeStatus.Success -> Status.Success; RechargeStatus.Pending -> Status.Pending; RechargeStatus.Failed -> Status.Failed }

@Composable
private fun MobileFields(s: RechargeState, vm: RechargeViewModel, onPickContact: () -> Unit, onChangeOperator: () -> Unit) {
    val c = AslTheme.colors
    AslTextField(
        s.number, vm::onNumber, stringResource(R.string.r_mobile_no), placeholder = stringResource(R.string.r_mobile_ph), keyboardType = KeyboardType.Phone, maxLength = 10,
        trailingIcon = AslIcons.Contacts, onTrailingClick = onPickContact, trailingLabel = stringResource(R.string.r_pick_contact),
        error = if ((s.showErrors || s.number.length == 10) && !s.numberOk) stringResource(R.string.r_mobile_bad) else null,
    )
    when (val d = s.detect) {
        Detect.Idle -> Unit
        Detect.Loading -> Row(verticalAlignment = Alignment.CenterVertically) { SkeletonRow(Modifier.weight(1f)) }
        is Detect.Found -> OperatorLine(s.operator?.name.orEmpty(), d.circle, AslIcons.Phone, AslGradients.Service.Bills.colors, onChangeOperator)
        Detect.NotFound -> {
            AlertBanner(AlertKind.Pending, stringResource(R.string.r_op_not_found), message = stringResource(R.string.r_op_not_found_msg))
            AslButton(stringResource(R.string.r_choose_operator), onChangeOperator, Modifier.fillMaxWidth(), icon = AslIcons.Chevron)
        }
        is Detect.Error -> {
            AlertBanner(AlertKind.Error, errorText(d.e))
            AslButton(stringResource(R.string.r_try_again), vm::retryDetect, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline, icon = AslIcons.Refresh)
        }
    }
    if (s.showErrors && s.numberOk && s.operator == null) Text(stringResource(R.string.r_operator_needed), style = AslTheme.type.caption, color = c.error)
    if (s.operator != null) {
        AslTextField(s.amountText, vm::onAmount, stringResource(R.string.r_amount), keyboardType = KeyboardType.Number, maxLength = 5,
            error = if (s.showErrors && s.amount == null) stringResource(R.string.r_amount_bad) else null)
        s.plan?.let { p -> AlertBanner(AlertKind.Info, stringResource(R.string.r_plan_line, rupees(p.amount), p.validity), message = p.description.lineSequence().firstOrNull()) }
        AslButton(stringResource(R.string.r_browse_plans), vm::openPlans, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline, icon = AslIcons.Search)
        Note(stringResource(R.string.r_or_type))
    }
}

@Composable
private fun OperatorLine(name: String, sub: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, colors: List<androidx.compose.ui.graphics.Color>, onChange: (() -> Unit)?) {
    ListCard {
        ListRow(name, subtitle = sub, icon = icon, iconColors = colors, onClick = onChange,
            trailing = { if (onChange != null) Text(stringResource(R.string.r_change), style = AslTheme.type.label, color = AslTheme.colors.primary) })
    }
}

@Composable
private fun DthFields(s: RechargeState, vm: RechargeViewModel) {
    val c = AslTheme.colors
    val ops = s.lists?.dth.orEmpty()
    when {
        s.listsLoading && ops.isEmpty() -> repeat(2) { SkeletonRow() }
        s.listsError != null && ops.isEmpty() -> {
            AlertBanner(AlertKind.Error, errorText(s.listsError))
            AslButton(stringResource(R.string.r_try_again), vm::loadOperators, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline, icon = AslIcons.Refresh)
            return
        }
    }
    if (ops.isNotEmpty()) {
        SectionHeader(stringResource(R.string.r_dth_operator))
        ChoiceChips(ops.map { it.name }, ops.indexOfFirst { it.code == s.operator?.code }.takeIf { it >= 0 }, { vm.chooseOperator(ops[it]) })
        if (s.showErrors && s.operator == null) Text(stringResource(R.string.r_operator_needed), style = AslTheme.type.caption, color = c.error)
    }
    AslTextField(s.number, vm::onNumber, stringResource(R.string.r_subscriber_id), keyboardType = KeyboardType.Number, maxLength = 20,
        error = if (s.showErrors && !s.numberOk) stringResource(R.string.r_subscriber_bad) else null)
    Note(stringResource(R.string.r_subscriber_hint))
    when (val d = s.dth) {
        DthInfo.Idle -> AslButton(stringResource(R.string.r_get_details), vm::getDthDetails, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline, icon = AslIcons.Search, enabled = s.operator != null && s.numberOk)
        DthInfo.Loading -> { repeat(2) { SkeletonRow() }; Note(stringResource(R.string.r_getting_details)) }
        is DthInfo.Found -> AslCard { KeyValueList(listOfNotNull(
            d.c.name?.let { KeyValue(stringResource(R.string.r_customer), it) },
            d.c.pack?.let { KeyValue(stringResource(R.string.r_pack), it) },
            d.c.status?.let { KeyValue(stringResource(R.string.r_status), it) },
            d.c.balance?.let { KeyValue(stringResource(R.string.r_dth_balance), rupees(it)) },
            d.c.monthly?.let { KeyValue(stringResource(R.string.r_monthly), rupees(it)) },
            d.c.nextRecharge?.let { KeyValue(stringResource(R.string.r_next_recharge), it) },
        )) }
        is DthInfo.NotFound -> AlertBanner(AlertKind.Pending, stringResource(R.string.r_details_not_found), message = d.reason ?: stringResource(R.string.r_details_not_found_msg))
        DthInfo.Unavailable -> {
            AlertBanner(AlertKind.Info, stringResource(R.string.r_details_down), message = stringResource(R.string.r_details_down_msg))
            AslButton(stringResource(R.string.r_try_again), vm::getDthDetails, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline, compact = true, icon = AslIcons.Refresh)
        }
        DthInfo.Soon -> AlertBanner(AlertKind.Info, stringResource(R.string.r_details_soon), message = stringResource(R.string.r_details_soon_msg))
    }
    AmountCard(s.amount?.let { BigDecimal(it) })
    val monthly = (s.dth as? DthInfo.Found)?.c?.monthly?.toInt()?.takeIf { it > 0 }
    val quick = monthly?.let { listOf(it, it * 2, it * 3) } ?: listOf(200, 300, 500)
    ChoiceChips(quick.map { rupees(it) }, quick.indexOfFirst { it == s.amount }.takeIf { it >= 0 }, { vm.onAmount(quick[it].toString()) })
    AslTextField(s.amountText, vm::onAmount, stringResource(R.string.r_amount), keyboardType = KeyboardType.Number, maxLength = 5,
        error = if (s.showErrors && s.amount == null) stringResource(R.string.r_amount_bad) else null)
}

@Composable
private fun OperatorSheet(ops: List<Operator>, loading: Boolean, current: Operator?, onPick: (Operator) -> Unit, retry: () -> Unit, onDismiss: () -> Unit) = Sheet(onDismiss) {
    Text(stringResource(R.string.r_choose_operator), style = AslTheme.type.title, color = AslTheme.colors.textPrimary)
    when {
        loading -> repeat(3) { SkeletonRow() }
        ops.isEmpty() -> {
            Text(stringResource(R.string.r_ops_na), style = AslTheme.type.body, color = AslTheme.colors.textSecondary)
            AslButton(stringResource(R.string.r_try_again), retry, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline, icon = AslIcons.Refresh)
        }
        else -> ListCard {
            ops.forEachIndexed { i, o ->
                if (i > 0) Divider()
                ListRow(o.name, icon = AslIcons.Phone, iconColors = AslGradients.Service.Bills.colors, onClick = { onPick(o) },
                    trailing = { if (o.code == current?.code) androidx.compose.material3.Icon(AslIcons.Check, null, tint = AslTheme.colors.primary) })
            }
        }
    }
}

// ---------------------------------------------------------------- 5.4–5.5 plans

@Composable
private fun PlansPage(s: RechargeState, vm: RechargeViewModel) {
    val c = AslTheme.colors
    var details by remember { mutableStateOf<Plan?>(null) }
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().keyboardAware()) {
        AslTopBar(stringResource(R.string.r_plans_title, s.operator?.name.orEmpty()), onBack = vm::closePlans)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AslTextField(s.planQuery, vm::onPlanQuery, stringResource(R.string.r_search_plans), trailingIcon = AslIcons.Search)
            if (s.planQuery.isBlank() && s.plans.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(s.plans) { i, g ->
                    val on = i == s.planGroup
                    Text(
                        g.name, style = AslTheme.type.label, color = if (on) androidx.compose.ui.graphics.Color.White else c.textPrimary, maxLines = 1,
                        modifier = Modifier.background(if (on) c.primary else c.surface, AslShapes.pill).border(1.dp, if (on) c.primary else c.outline, AslShapes.pill)
                            .clickable(role = Role.Tab) { vm.onPlanGroup(i) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
        when {
            s.plansLoading -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { repeat(5) { SkeletonRow() } }
            s.plansError != null -> Column(Modifier.padding(16.dp)) {
                StateView(AslIcons.WifiOff, StatePresets.offlineColors, stringResource(R.string.r_plans_error), errorText(s.plansError)) {
                    AslPrimaryButton(stringResource(R.string.r_try_again), vm::retryPlans, icon = AslIcons.Refresh)
                }
            }
            s.visiblePlans.isEmpty() -> Column(Modifier.padding(16.dp)) { Note(stringResource(if (s.plans.isEmpty()) R.string.r_no_plans else R.string.r_no_plan_match)) }
            else -> LazyColumn(Modifier.fillMaxWidth().imePadding().navigationBarsPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(s.visiblePlans, key = { "${it.category}-${it.amount}-${it.validity}" }) { p -> PlanCard(p, onClick = { details = p }) }
            }
        }
    }
    details?.let { p ->
        Sheet({ details = null }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rupees(p.amount), style = AslTheme.type.amountDisplay, color = c.textPrimary, modifier = Modifier.weight(1f))
                StatusPill(p.validity, Status.Info)
            }
            Text(p.category, style = AslTheme.type.caption, color = c.textSecondary)
            Text(p.description, style = AslTheme.type.body, color = c.textPrimary, modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()))
            AslPrimaryButton(stringResource(R.string.r_choose_plan), { details = null; vm.pickPlan(p) })
        }
    }
}

@Composable
private fun PlanCard(p: Plan, onClick: () -> Unit) {
    val c = AslTheme.colors
    Box(Modifier.fillMaxWidth().background(c.surface, AslShapes.card).border(1.dp, c.outline, AslShapes.card).clickable(role = Role.Button, onClick = onClick).padding(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rupees(p.amount), style = AslTheme.type.bodyStrong, color = c.textPrimary, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.r_choose), style = AslTheme.type.label, color = c.primary)
            }
            Text(stringResource(R.string.r_validity, p.validity), style = AslTheme.type.label, color = c.textPrimary)
            Text(p.description, style = AslTheme.type.caption, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------- 5.6, 5.11, 5.18 review

@Composable
private fun ReviewPage(s: RechargeState, vm: RechargeViewModel, onAddMoney: () -> Unit, onFundRequest: () -> Unit) {
    val mobile = s.type == RechargeType.Mobile
    val amount = s.amount ?: 0
    Scaffold(stringResource(R.string.r_review), vm::backToForm, secure = true, bottom = {
        val short = s.shortBy
        when {
            s.balanceLoading -> AslPrimaryButton(stringResource(R.string.r_checking_balance), {}, loading = true)
            s.balanceError != null -> AslPrimaryButton(stringResource(R.string.r_try_again), vm::review, icon = AslIcons.Refresh)
            short != null -> {
                AslPrimaryButton(stringResource(R.string.r_add_money), onAddMoney, icon = AslIcons.Plus)
                AslButton(stringResource(R.string.r_fund_request), onFundRequest, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline)
            }
            else -> SlideToConfirm(stringResource(R.string.r_slide, rupees(amount)), vm::confirm, resetKey = s.page)
        }
    }) {
        AslCard { KeyValueList(listOfNotNull(
            KeyValue(stringResource(if (mobile) R.string.r_mobile else R.string.r_subscriber_id), s.number),
            KeyValue(stringResource(R.string.r_operator), "${s.operator?.name.orEmpty()} · ${stringResource(if (mobile) R.string.r_prepaid else R.string.r_dth)}"),
            s.plan?.let { KeyValue(stringResource(R.string.r_plan), it.validity) },
            s.balance?.let { KeyValue(stringResource(R.string.r_pay_from), stringResource(R.string.r_wallet_amount, rupees(it))) },
            KeyValue(stringResource(R.string.r_amount), rupees(amount), total = true),
        )) }
        AslTextField(s.customerName, vm::onCustomerName, stringResource(R.string.r_customer_name), maxLength = 60)
        s.shortBy?.let { short ->
            AlertBanner(AlertKind.Error, stringResource(R.string.r_low_balance, rupees(s.balance)), message = stringResource(R.string.r_low_balance_msg, rupees(short)), icon = AslIcons.Wallet)
        } ?: AlertBanner(AlertKind.Info, stringResource(if (mobile) R.string.r_check_number else R.string.r_check_id), message = stringResource(R.string.r_no_reverse), icon = AslIcons.Shield)
        s.balanceError?.let { AlertBanner(AlertKind.Error, errorText(it)) }
    }
}

// ---------------------------------------------------------------- 5.8–5.10 results

@Composable
private fun ResultPage(s: RechargeState, vm: RechargeViewModel, shop: ShopInfo, onHome: () -> Unit, onCall: () -> Unit, onWhatsApp: () -> Unit) {
    val title = stringResource(R.string.r_receipt)
    BackHandler { vm.again(keepDetails = false) }
    val o = s.outcome ?: return
    // Every outcome prints the same bill as Reports, stamped Success / Pending / Failed.
    val (status, word) = when (o) {
        is RechargeOutcome.Done -> Status.Success to stringResource(R.string.r_success)
        is RechargeOutcome.Pending -> Status.Pending to stringResource(R.string.r_pending)
        is RechargeOutcome.Failed -> Status.Failed to stringResource(R.string.r_failed)
    }
    val receipt = when (o) { is RechargeOutcome.Done -> o.receipt; is RechargeOutcome.Pending -> o.receipt; is RechargeOutcome.Failed -> o.receipt }
    val extra = if (o is RechargeOutcome.Failed) listOf(
        stringResource(R.string.r_amount) to stringResource(if (o.debited) R.string.r_returned else R.string.r_not_taken, rupees(receipt.amount)),
        stringResource(R.string.r_reason) to (o.reason?.let(::safeServerMessage) ?: stringResource(R.string.r_reason_unknown)),
    ) else emptyList()
    val doc = receiptDoc(receipt, word, extra)
    Scaffold(title, { vm.again(false) }, secure = true, close = o is RechargeOutcome.Done, bottom = {
        when (o) {
            is RechargeOutcome.Done -> {
                AslPrimaryButton(stringResource(R.string.r_another), { vm.again(false) })
                AslButton(stringResource(R.string.r_home), onHome, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline, icon = AslIcons.Home)
            }
            is RechargeOutcome.Pending -> AslPrimaryButton(stringResource(R.string.r_back_recharge), { vm.again(false) })
            is RechargeOutcome.Failed -> {
                AslPrimaryButton(stringResource(R.string.r_try_again), { vm.again(keepDetails = true) })
                AslButton(stringResource(R.string.r_home), onHome, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline)
            }
        }
    }) {
        BillReceipt(
            shop, doc, status,
            BillLabels(
                listOf(stringResource(R.string.r_share), stringResource(R.string.r_pdf), stringResource(R.string.r_print), stringResource(R.string.r_help)),
                stringResource(R.string.r_share_title), stringResource(R.string.r_print_title), stringResource(R.string.r_print_thermal), stringResource(R.string.r_print_a4),
            ),
            fileName = "ASL-recharge.pdf", onHelp = onCall,
            amountWords = AmountInWords.english(receipt.amount), barcodeSeed = receipt.txnId,
            voice = if (o is RechargeOutcome.Done) stringResource(R.string.r_voice, receipt.operator, com.asl.wallet.core.designsystem.component.spokenRupees(receipt.amount)) else null,
        )
        if (o is RechargeOutcome.Pending) {
            AlertBanner(AlertKind.Pending, stringResource(R.string.r_held), message = stringResource(R.string.r_held_msg))
            HelpCard(onComplaint = onCall, onWhatsApp = onWhatsApp, onCall = onCall, title = stringResource(R.string.r_need_help))
        }
    }
}

private fun masked(r: RechargeReceipt) = if (r.type == RechargeType.Mobile) Masking.mobile(r.number) else Masking.account(r.number)

/** Shared receipts mask the customer's number. */
@Composable
private fun receiptDoc(r: RechargeReceipt, status: String, extra: List<Pair<String, String>> = emptyList()) = ReceiptDoc(
    title = stringResource(if (r.type == RechargeType.Mobile) R.string.r_mobile_recharge else R.string.r_dth_recharge),
    status = status,
    amount = rupees(r.amount),
    rows = listOfNotNull(
        stringResource(if (r.type == RechargeType.Mobile) R.string.r_mobile else R.string.r_subscriber_id) to masked(r),
        r.customer?.let { stringResource(R.string.r_customer) to it },
        stringResource(R.string.r_operator) to r.operator,
        r.planLine?.let { stringResource(R.string.r_plan) to it },
        r.operatorRef?.let { stringResource(R.string.r_operator_ref) to it },
        r.txnId?.let { stringResource(R.string.r_txn_id) to it },
        r.time.takeIf { it.isNotBlank() }?.let { stringResource(R.string.r_date) to it },
    ) + extra,
    footer = stringResource(R.string.r_receipt_note),
)
