package com.asl.wallet.feature.bbps.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import com.asl.wallet.core.designsystem.component.BillLabels
import com.asl.wallet.core.designsystem.component.BillReceipt
import com.asl.wallet.core.designsystem.component.ChoiceChips
import com.asl.wallet.core.designsystem.component.Divider
import com.asl.wallet.core.designsystem.component.HelpCard
import com.asl.wallet.core.designsystem.component.KeyValue
import com.asl.wallet.core.designsystem.component.KeyValueList
import com.asl.wallet.core.designsystem.component.ListCard
import com.asl.wallet.core.designsystem.component.ListRow
import com.asl.wallet.core.designsystem.component.ServiceArt
import com.asl.wallet.core.designsystem.component.ServiceGrid
import com.asl.wallet.core.designsystem.component.ServiceItem
import com.asl.wallet.core.designsystem.component.ShopInfo
import com.asl.wallet.core.designsystem.component.SkeletonRow
import com.asl.wallet.core.designsystem.component.StatePresets
import com.asl.wallet.core.designsystem.component.StateView
import com.asl.wallet.core.designsystem.component.Status
import com.asl.wallet.core.designsystem.component.keyboardAware
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.receipt.ReceiptDoc
import com.asl.wallet.core.designsystem.theme.AslGradients
import com.asl.wallet.core.designsystem.theme.AslTheme
import com.asl.wallet.core.network.UiError
import com.asl.wallet.core.network.safeServerMessage
import com.asl.wallet.core.security.ui.SecureScreen
import com.asl.wallet.feature.bbps.R
import com.asl.wallet.feature.bbps.domain.BbpsOutcome
import com.asl.wallet.feature.bbps.domain.BbpsReceipt
import com.asl.wallet.feature.bbps.domain.Biller
import com.asl.wallet.feature.bbps.domain.BillerParam
import java.math.BigDecimal
import com.asl.wallet.core.designsystem.R as DS

// ---------------------------------------------------------------- shared bits

@Composable
private fun Scaffold(
    title: String,
    onBack: () -> Unit,
    secure: Boolean = false,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AslTheme.colors
    if (secure) SecureScreen()
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().keyboardAware()) {
        AslTopBar(title, onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 16.dp)
                .then(if (bottom == null) Modifier.navigationBarsPadding() else Modifier),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
        )
        if (bottom != null) Column(Modifier.fillMaxWidth().background(c.surface).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = bottom)
    }
}

@Composable
private fun errorText(e: UiError): String = when (e) {
    is UiError.Server -> e.message
    UiError.NoInternet -> stringResource(R.string.b_offline)
    UiError.Timeout -> stringResource(R.string.b_timeout)
    else -> stringResource(R.string.b_generic)
}

/** Bharat Connect (BBPS) mark, shown on every bill-payment screen like the old app. */
@Composable
private fun BharatConnect(modifier: Modifier = Modifier) =
    Image(painterResource(DS.drawable.classic_ic_bharat_connect), "Bharat Connect", modifier.height(34.dp), contentScale = ContentScale.Fit)

@Composable
private fun Loading() = ListCard { Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { repeat(5) { SkeletonRow() } } }

private fun rupees(a: BigDecimal?) = a?.let { Money.format(it) } ?: "—"

/** The old app's picture for a category where there is one, else a vector icon. */
private fun artOf(name: String): Pair<ServiceArt?, androidx.compose.ui.graphics.vector.ImageVector> {
    val n = name.lowercase()
    fun pic(res: Int) = ServiceArt.Picture(res)
    return when {
        "electric" in n || "power" in n -> pic(DS.drawable.classic_ic_electricity) to AslIcons.Bill
        "fastag" in n -> pic(DS.drawable.classic_ic_fastag) to AslIcons.Wallet
        "gas" in n || "lpg" in n -> pic(DS.drawable.classic_ic_gas) to AslIcons.Bill
        "loan" in n || "emi" in n -> pic(DS.drawable.classic_ic_loan) to AslIcons.Bank
        "insurance" in n || "lic" in n -> pic(DS.drawable.classic_ic_insurance) to AslIcons.Shield
        "dth" in n || "cable" in n -> pic(DS.drawable.classic_ic_dth) to AslIcons.Tv
        "mobile" in n || "postpaid" in n || "prepaid" in n -> pic(DS.drawable.classic_ic_mob_recharge) to AslIcons.Phone
        "broadband" in n || "landline" in n || "internet" in n -> null to AslIcons.Globe
        "credit" in n -> null to AslIcons.Wallet
        "education" in n || "fee" in n -> null to AslIcons.Document
        "municipal" in n || "tax" in n || "housing" in n || "rent" in n -> null to AslIcons.Home
        else -> null to AslIcons.Bill
    }
}

// ---------------------------------------------------------------- the flow

/** BBPS bill payment: categories → billers → customer details → bill → pay → receipt. [category] opens straight in. */
@Composable
fun BbpsScreen(
    category: String?,
    shop: ShopInfo,
    onBack: () -> Unit,
    onAddMoney: () -> Unit,
    onFundRequest: () -> Unit,
    onCallSupport: () -> Unit,
    onWhatsAppSupport: () -> Unit,
    vm: BbpsViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.start(category) }
    BackHandler { if (!vm.back()) onBack() }
    val back = { if (!vm.back()) onBack() }
    when (s.page) {
        BbpsPage.Categories -> CategoriesPage(s, vm, back)
        BbpsPage.Billers -> BillersPage(s, vm, back)
        BbpsPage.Details -> DetailsPage(s, vm, back)
        BbpsPage.Bill -> BillPage(s, vm, back, onAddMoney, onFundRequest)
        BbpsPage.Result -> ResultPage(s, vm, shop, onBack, onCallSupport, onWhatsAppSupport)
    }
}

@Composable
private fun CategoriesPage(s: BbpsState, vm: BbpsViewModel, onBack: () -> Unit) {
    Scaffold(stringResource(R.string.b_title), onBack) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.b_categories), style = AslTheme.type.title, color = AslTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            BharatConnect()
        }
        val e = s.error
        when {
            s.categoriesLoading -> Loading()
            e != null && s.categories.isEmpty() -> StateView(AslIcons.WifiOff, StatePresets.offlineColors, stringResource(R.string.b_no_categories), errorText(e)) {
                AslPrimaryButton(stringResource(R.string.b_retry), vm::loadCategories, icon = AslIcons.Refresh)
            }
            s.categories.isEmpty() -> StateView(AslIcons.Bill, StatePresets.emptyColors, stringResource(R.string.b_no_categories), stringResource(R.string.b_generic)) {
                AslPrimaryButton(stringResource(R.string.b_retry), vm::loadCategories, icon = AslIcons.Refresh)
            }
            else -> {
                val items = s.categories.map { cat -> artOf(cat.name).let { (art, icon) -> ServiceItem(cat.name, icon, AslGradients.Service.Bills.colors, art = art) } }
                ServiceGrid(items, onClick = { vm.openCategory(s.categories[items.indexOf(it)].name) }, columns = 3)
            }
        }
    }
}

@Composable
private fun BillersPage(s: BbpsState, vm: BbpsViewModel, onBack: () -> Unit) {
    Scaffold(s.category ?: stringResource(R.string.b_title), onBack) {
        AslTextField(s.query, vm::onQuery, stringResource(R.string.b_search_biller), leadingIcon = AslIcons.Search, imeAction = ImeAction.Search)
        val e = s.error
        when {
            s.billersLoading -> Loading()
            e != null && s.billers.isEmpty() -> StateView(AslIcons.WifiOff, StatePresets.offlineColors, stringResource(R.string.b_no_billers), errorText(e)) {
                AslPrimaryButton(stringResource(R.string.b_retry), vm::retryBillers, icon = AslIcons.Refresh)
            }
            s.shownBillers.isEmpty() -> StateView(AslIcons.Search, StatePresets.emptyColors, stringResource(R.string.b_no_billers), stringResource(R.string.b_no_billers_msg))
            else -> ListCard {
                s.shownBillers.forEachIndexed { i, b ->
                    if (i > 0) Divider()
                    ListRow(b.name, icon = artOf(b.category).second, iconColors = AslGradients.Service.Bills.colors, onClick = { vm.openBiller(b) })
                }
            }
        }
    }
}

@Composable
private fun BillerHeader(b: Biller) {
    AslCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(b.name, style = AslTheme.type.bodyStrong, color = AslTheme.colors.textPrimary)
                Text(b.category, style = AslTheme.type.caption, color = AslTheme.colors.textSecondary)
            }
            BharatConnect()
        }
    }
}

@Composable
private fun DetailsPage(s: BbpsState, vm: BbpsViewModel, onBack: () -> Unit) {
    val b = s.biller ?: return
    val problems = if (s.showErrors) s.problems() else emptyMap()
    Scaffold(stringResource(R.string.b_title), onBack, secure = true, bottom = if (s.info == null) null else ({
        AslPrimaryButton(stringResource(R.string.b_fetch), vm::fetch, loading = s.fetching, icon = AslIcons.Search)
    })) {
        BillerHeader(b)
        val e = s.error
        when {
            s.infoLoading -> Loading()
            s.info == null -> StateView(AslIcons.WifiOff, StatePresets.offlineColors, stringResource(R.string.b_generic), e?.let { errorText(it) } ?: stringResource(R.string.b_generic)) {
                AslPrimaryButton(stringResource(R.string.b_retry), vm::retryInfo, icon = AslIcons.Refresh)
            }
            else -> {
                Text(stringResource(R.string.b_enter_details), style = AslTheme.type.caption, color = AslTheme.colors.textSecondary)
                val params = s.info.params
                params.forEachIndexed { i, p ->
                    val err = problems[p.name]?.let { problemText(p, it) }
                    if (p.choices.isNotEmpty()) {
                        Text(p.name, style = AslTheme.type.label, color = AslTheme.colors.textSecondary)
                        ChoiceChips(p.choices, p.choices.indexOf(s.values[p.name]).takeIf { it >= 0 }, { vm.onValue(p, p.choices[it]) })
                        if (err != null) Text(err, style = AslTheme.type.caption, color = AslTheme.colors.error)
                    } else AslTextField(
                        s.values[p.name].orEmpty(), { vm.onValue(p, it) }, p.name + if (p.optional) "" else " *",
                        keyboardType = when { p.isMobile -> KeyboardType.Phone; p.numeric -> KeyboardType.Number; else -> KeyboardType.Text },
                        maxLength = p.maxLength ?: 60, error = err,
                        imeAction = if (i == params.lastIndex) ImeAction.Done else ImeAction.Next,
                        onImeAction = { if (i == params.lastIndex) vm.fetch() },
                    )
                }
                if (e != null && !s.fetching) AlertBanner(AlertKind.Error, errorText(e))
            }
        }
    }
}

@Composable
private fun problemText(p: BillerParam, c: BillerParam.Check): String = when (c) {
    BillerParam.Check.Missing -> stringResource(R.string.b_required, p.name)
    BillerParam.Check.TooShort -> stringResource(R.string.b_too_short, p.name)
    else -> stringResource(R.string.b_invalid, p.name)
}

@Composable
private fun BillPage(s: BbpsState, vm: BbpsViewModel, onBack: () -> Unit, onAddMoney: () -> Unit, onFundRequest: () -> Unit) {
    val b = s.biller ?: return
    val bill = s.bill ?: return
    val c = AslTheme.colors
    Scaffold(stringResource(R.string.b_bill_details), onBack, secure = true, bottom = {
        if (s.paying) Text(stringResource(R.string.b_paying), style = AslTheme.type.caption, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        AslPrimaryButton(stringResource(R.string.b_pay, s.amount?.let(::rupees) ?: ""), vm::askPay, loading = s.paying, icon = AslIcons.Shield)
    }) {
        BillerHeader(b)
        AslCard {
            KeyValueList(listOfNotNull(
                bill.customerName?.let { KeyValue(stringResource(R.string.b_customer_name), it) },
                s.info?.params?.firstOrNull()?.let { p -> s.values[p.name]?.takeIf(String::isNotBlank)?.let { KeyValue(p.name, it) } },
                bill.billNumber?.let { KeyValue(stringResource(R.string.b_bill_number), it) },
                bill.billDate?.let { KeyValue(stringResource(R.string.b_bill_date), it) },
                bill.dueDate?.let { KeyValue(stringResource(R.string.b_due_date), it) },
                bill.billPeriod?.let { KeyValue(stringResource(R.string.b_bill_period), it) },
            ) + bill.details.map { KeyValue(it.first, it.second) })
        }
        AslTextField(
            s.amountText, vm::onAmount, stringResource(R.string.b_amount), keyboardType = KeyboardType.Decimal, locked = !s.amountEditable,
            imeAction = ImeAction.Done, maxLength = 9,
            error = if (s.amountError) stringResource(R.string.b_amount_bad) else null,
            success = if (!s.amountEditable) stringResource(R.string.b_amount_fixed) else null,
        )
        s.amount?.takeIf { it.signum() > 0 }?.let { Text(AmountInWords.english(it), style = AslTheme.type.caption, color = c.textSecondary) }
        if (s.amountEditable && bill.options.isNotEmpty()) {
            ChoiceChips(bill.options.map { "${it.first} · ${rupees(it.second)}" }, bill.options.indexOfFirst { it.second.compareTo(s.amount ?: BigDecimal.ZERO) == 0 }.takeIf { it >= 0 }, { vm.pickOption(bill.options[it].second) })
        }
        s.balance?.let { Text(stringResource(R.string.b_wallet_balance, rupees(it)), style = AslTheme.type.label, color = c.textSecondary) }
    }
    if (s.confirm) AlertDialog(
        onDismissRequest = vm::dismissDialogs,
        containerColor = c.surface,
        title = { Text(stringResource(R.string.b_confirm_title), style = AslTheme.type.title, color = c.textPrimary) },
        text = { Text(stringResource(R.string.b_confirm_msg, rupees(s.amount), b.name), style = AslTheme.type.body, color = c.textSecondary) },
        confirmButton = { AslButton(stringResource(R.string.b_confirm_pay), vm::pay, compact = true) },
        dismissButton = { AslButton(stringResource(R.string.b_cancel), vm::dismissDialogs, style = AslButtonStyle.Outline, compact = true) },
    )
    if (s.lowBalance) AlertDialog(
        onDismissRequest = vm::dismissDialogs,
        containerColor = c.surface,
        title = { Text(stringResource(R.string.b_low_title), style = AslTheme.type.title, color = c.textPrimary) },
        text = { Text(stringResource(R.string.b_low_msg, rupees(s.balance)), style = AslTheme.type.body, color = c.textSecondary) },
        confirmButton = { AslButton(stringResource(R.string.b_add_money), { vm.dismissDialogs(); onAddMoney() }, compact = true) },
        dismissButton = { AslButton(stringResource(R.string.b_fund_request), { vm.dismissDialogs(); onFundRequest() }, style = AslButtonStyle.Outline, compact = true) },
    )
}

// ---------------------------------------------------------------- result: the same bill as every other service

@Composable
private fun ResultPage(s: BbpsState, vm: BbpsViewModel, shop: ShopInfo, onHome: () -> Unit, onCall: () -> Unit, onWhatsApp: () -> Unit) {
    val o = s.outcome ?: return
    val (status, word) = when (o) {
        is BbpsOutcome.Paid -> Status.Success to stringResource(R.string.b_success)
        is BbpsOutcome.Pending -> Status.Pending to stringResource(R.string.b_pending)
        is BbpsOutcome.Failed -> Status.Failed to stringResource(R.string.b_failed)
    }
    val extra = if (o is BbpsOutcome.Failed) listOf(
        stringResource(R.string.b_amount) to stringResource(if (o.debited) R.string.b_returned else R.string.b_not_taken, rupees(o.receipt.amount)),
        stringResource(R.string.b_reason) to (o.reason?.let(::safeServerMessage) ?: stringResource(R.string.b_reason_unknown)),
    ) else emptyList()
    val doc = receiptDoc(o.receipt, word, extra)
    Scaffold(stringResource(R.string.b_receipt), onHome, secure = true, bottom = {
        AslPrimaryButton(stringResource(R.string.b_another), { vm.again(sameBiller = false) })
        AslButton(stringResource(R.string.b_home), onHome, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline, icon = AslIcons.Home)
    }) {
        BillReceipt(
            shop, doc, status,
            BillLabels(
                listOf(stringResource(R.string.b_share), stringResource(R.string.b_pdf), stringResource(R.string.b_print), stringResource(R.string.b_help)),
                stringResource(R.string.b_share_title), stringResource(R.string.b_print_title), stringResource(R.string.b_print_thermal), stringResource(R.string.b_print_a4),
            ),
            fileName = "ASL-bill-payment.pdf", onHelp = onCall,
            amountWords = AmountInWords.english(o.receipt.amount), barcodeSeed = o.receipt.refId ?: o.receipt.bbpsTxnId,
            voice = if (o is BbpsOutcome.Paid) stringResource(R.string.b_voice, o.receipt.biller, com.asl.wallet.core.designsystem.component.spokenRupees(o.receipt.amount)) else null,
        )
        if (o is BbpsOutcome.Pending) {
            AlertBanner(AlertKind.Pending, stringResource(R.string.b_held), message = stringResource(R.string.b_held_msg))
            HelpCard(onComplaint = onCall, onWhatsApp = onWhatsApp, onCall = onCall, title = stringResource(R.string.b_need_help))
        }
    }
}

/** The customer's bill: no retailer commission, nothing from the provider's logs. */
@Composable
private fun receiptDoc(r: BbpsReceipt, status: String, extra: List<Pair<String, String>>) = ReceiptDoc(
    title = stringResource(R.string.b_receipt_title),
    status = status,
    amount = rupees(r.amount),
    rows = listOfNotNull(
        stringResource(R.string.b_biller) to r.biller,
        stringResource(R.string.b_category) to r.category,
        r.consumerNumber.takeIf { it.isNotBlank() }?.let { r.consumerLabel to it },
        r.customerName?.let { stringResource(R.string.b_customer_name) to it },
        r.billNumber?.let { stringResource(R.string.b_bill_number) to it },
        r.bbpsTxnId?.let { stringResource(R.string.b_bbps_txn) to it },
        r.refId?.let { stringResource(R.string.b_ref) to it },
        r.time.takeIf { it.isNotBlank() }?.let { stringResource(R.string.b_date) to it },
    ) + extra,
    footer = stringResource(R.string.b_receipt_note),
)
