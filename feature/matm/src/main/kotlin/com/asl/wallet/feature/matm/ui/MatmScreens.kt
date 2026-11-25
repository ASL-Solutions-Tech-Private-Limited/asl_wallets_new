package com.asl.wallet.feature.matm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.asl.wallet.core.designsystem.component.SegmentedControl
import com.asl.wallet.core.designsystem.component.ShopInfo
import com.asl.wallet.core.designsystem.component.Status
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.receipt.ReceiptDoc
import com.asl.wallet.core.designsystem.theme.AslTheme
import com.asl.wallet.core.common.format.Money
import com.asl.wallet.feature.matm.R
import com.asl.wallet.feature.matm.data.rememberMatmLauncher
import com.asl.wallet.feature.matm.domain.MatmResult
import com.asl.wallet.feature.matm.domain.MatmTxnType

@Composable
fun MatmTransactionScreen(
    shop: ShopInfo,
    onBack: () -> Unit,
    onHelp: () -> Unit,
    vm: MatmViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val launch = rememberMatmLauncher(vm::onDeviceResult)

    // When the server returns the credentials, open the card reader (once).
    LaunchedEffect(state.launch) {
        state.launch?.let { req -> launch(req.init, req.at); vm.launchConsumed() }
    }

    val receipt = state.receipt
    if (receipt != null) {
        MatmReceiptScreen(shop, receipt, onHelp = onHelp, onAgain = vm::again, onDone = onBack)
        return
    }

    val c = AslTheme.colors
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        AslTopBar(stringResource(R.string.m_title), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.m_intro), style = AslTheme.type.body, color = c.textSecondary)

            SegmentedControl(
                listOf(stringResource(R.string.m_type_withdraw), stringResource(R.string.m_type_balance)),
                if (state.form.type == MatmTxnType.Balance) 1 else 0,
                { vm.setType(if (it == 1) MatmTxnType.Balance else MatmTxnType.Withdraw) },
            )

            AslTextField(
                state.form.mobile, vm::setMobile, stringResource(R.string.m_mobile),
                placeholder = stringResource(R.string.m_mobile_hint), keyboardType = KeyboardType.Phone, maxLength = 10,
                leadingIcon = AslIcons.Phone, error = state.form.mobileError?.let { stringResource(it) },
            )

            if (state.form.type == MatmTxnType.Withdraw) {
                AslTextField(
                    state.form.amount, vm::setAmount, stringResource(R.string.m_amount),
                    placeholder = stringResource(R.string.m_amount_hint), keyboardType = KeyboardType.Number, maxLength = 6,
                    leadingIcon = AslIcons.Wallet, error = state.form.amountError?.let { stringResource(it) },
                )
            }

            if (!vm.hasLocationPermission) AlertBanner(AlertKind.Info, stringResource(R.string.m_location_hint))
            state.banner?.let { AlertBanner(AlertKind.Error, it) }
            state.bannerRes?.let { AlertBanner(AlertKind.Info, stringResource(it)) }

            AslCard { Text(stringResource(R.string.m_device_note), style = AslTheme.type.caption, color = c.textSecondary) }
        }
        Column(Modifier.fillMaxWidth().background(c.surface).navigationBarsPadding().padding(16.dp)) {
            AslPrimaryButton(
                stringResource(if (state.form.type == MatmTxnType.Balance) R.string.m_cta_balance else R.string.m_cta_withdraw),
                vm::submit, enabled = !state.busy, loading = state.busy, icon = AslIcons.Atm,
            )
        }
    }
}

@Composable
private fun MatmReceiptScreen(shop: ShopInfo, receipt: MatmReceipt, onHelp: () -> Unit, onAgain: () -> Unit, onDone: () -> Unit) {
    val c = AslTheme.colors
    val r = receipt.result
    val status = if (r.status) Status.Success else Status.Failed
    val doc = buildDoc(r)
    val labels = BillLabels(
        actions = listOf(stringResource(R.string.m_act_whatsapp), stringResource(R.string.m_act_save), stringResource(R.string.m_act_print), stringResource(R.string.m_act_help)),
        shareTitle = stringResource(R.string.m_share_title),
        printTitle = stringResource(R.string.m_print_title),
        printThermal = stringResource(R.string.m_print_thermal),
        printA4 = stringResource(R.string.m_print_a4),
    )
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        AslTopBar(stringResource(R.string.m_receipt_title), onBack = onDone)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (receipt.serverWarning) AlertBanner(AlertKind.Pending, stringResource(R.string.m_server_warning))
            receipt.confirm?.commission?.let { AlertBanner(AlertKind.Success, stringResource(R.string.m_commission, Money.format(it, showPaise = false))) }
            BillReceipt(shop, doc, status, labels, fileName = "ASL-matm-${r.txnId.ifBlank { r.fpId }}.pdf", onHelp = onHelp)
        }
        Column(Modifier.fillMaxWidth().background(c.surface).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AslPrimaryButton(stringResource(R.string.m_new_txn), onAgain, icon = AslIcons.Atm)
            AslButton(stringResource(R.string.m_done), onDone, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline)
        }
    }
}

@Composable
private fun buildDoc(r: MatmResult): ReceiptDoc {
    val isBalance = r.type == 4 || r.transType.equals("BAL", ignoreCase = true)
    val rows = buildList {
        add(stringResource(R.string.m_r_type) to if (isBalance) stringResource(R.string.m_type_balance) else stringResource(R.string.m_type_withdraw))
        if (!isBalance) add(stringResource(R.string.m_r_amount) to Money.format(r.amount))
        add(stringResource(R.string.m_r_balance) to Money.format(r.balance))
        if (r.cardNum.isNotBlank()) add(stringResource(R.string.m_r_card) to maskCard(r.cardNum))
        if (r.bankName.isNotBlank()) add(stringResource(R.string.m_r_bank) to r.bankName)
        if (r.bankRrn.isNotBlank()) add(stringResource(R.string.m_r_rrn) to r.bankRrn)
        if (r.terminalId.isNotBlank()) add(stringResource(R.string.m_r_terminal) to r.terminalId)
        if (r.txnId.isNotBlank()) add(stringResource(R.string.m_r_txn) to r.txnId)
        if (r.message.isNotBlank()) add(stringResource(R.string.m_r_message) to r.message)
    }
    val statusWord = stringResource(if (r.status) R.string.m_status_success else R.string.m_status_failed)
    return ReceiptDoc(
        title = stringResource(R.string.m_receipt_title),
        status = statusWord,
        amount = if (isBalance) Money.format(r.balance) else Money.format(r.amount),
        rows = rows,
        footer = stringResource(R.string.m_receipt_footer),
    )
}

/** XXXX1042 → ••1042. The SDK already masks the number; this just tidies it. */
private fun maskCard(card: String): String {
    val last4 = card.filter { it.isLetterOrDigit() }.takeLast(4)
    return if (last4.length == 4) "•• $last4" else card
}
