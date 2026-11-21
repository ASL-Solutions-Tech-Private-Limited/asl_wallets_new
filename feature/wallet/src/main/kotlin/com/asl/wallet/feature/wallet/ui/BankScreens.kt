package com.asl.wallet.feature.wallet.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asl.wallet.core.designsystem.component.AslButton
import com.asl.wallet.core.designsystem.component.AslButtonStyle
import com.asl.wallet.core.designsystem.component.AslPrimaryButton
import com.asl.wallet.core.designsystem.component.AslTextField
import com.asl.wallet.core.designsystem.component.Divider
import com.asl.wallet.core.designsystem.component.KeyValue
import com.asl.wallet.core.designsystem.component.ListCard
import com.asl.wallet.core.designsystem.component.ListRow
import com.asl.wallet.core.designsystem.component.SegmentedControl
import com.asl.wallet.core.designsystem.component.SkeletonRow
import com.asl.wallet.core.designsystem.component.StatePresets
import com.asl.wallet.core.designsystem.component.StateView
import com.asl.wallet.core.designsystem.component.Status
import com.asl.wallet.core.designsystem.component.StatusPill
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.theme.AslGradients
import com.asl.wallet.core.designsystem.theme.AslTheme
import com.asl.wallet.feature.wallet.R
import com.asl.wallet.feature.wallet.data.DocPhotos
import com.asl.wallet.feature.wallet.domain.AccountOwner
import com.asl.wallet.feature.wallet.domain.AddBankForm.Field
import com.asl.wallet.feature.wallet.domain.BankOption
import com.asl.wallet.feature.wallet.domain.searchBanks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------------------------------------------------------------- 4.27, 4.32 Bank accounts

@Composable
fun BankAccountsScreen(onBack: () -> Unit, onAdd: () -> Unit, vm: BankAccountsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }
    WalletScaffold(stringResource(R.string.w_bank_accounts), onBack, bottom = {
        AslPrimaryButton(stringResource(R.string.w_add_account), onAdd, icon = AslIcons.Plus)
    }) {
        when {
            s.loading && s.accounts.isEmpty() -> ListCard { Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { repeat(2) { SkeletonRow() } } }
            s.error != null && s.accounts.isEmpty() -> StateView(AslIcons.WifiOff, StatePresets.offlineColors, stringResource(R.string.w_load_error), uiErrorText(s.error ?: com.asl.wallet.core.network.UiError.Unknown)) {
                AslPrimaryButton(stringResource(R.string.w_try_again), vm::load, icon = AslIcons.Refresh)
            }
            s.accounts.isEmpty() -> StateView(AslIcons.Bank, AslGradients.Service.Bills.colors, stringResource(R.string.w_no_accounts), stringResource(R.string.w_no_accounts_msg))
            else -> {
                ListCard {
                    s.accounts.forEachIndexed { i, a ->
                        if (i > 0) Divider()
                        ListRow(
                            "${a.bankName} · ••${a.accountLast4}",
                            subtitle = listOf(a.holder, a.ifsc, if (a.owner == AccountOwner.Relative) stringResource(R.string.w_relative) else "").filter { it.isNotBlank() }.joinToString(" · "),
                            icon = AslIcons.Bank, iconColors = AslGradients.Service.Bills.colors,
                            trailing = {
                                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    StatusPill(stringResource(if (a.verified) R.string.w_verified else R.string.w_pending), if (a.verified) Status.Success else Status.Pending)
                                    IconButton({ vm.askDelete(a) }) { Icon(AslIcons.Trash, stringResource(R.string.w_delete_account_cd, a.accountLast4), tint = AslTheme.colors.textSecondary) }
                                }
                            },
                        )
                    }
                }
                Note(stringResource(R.string.w_only_verified))
                Note(stringResource(R.string.w_no_edit))
            }
        }
        ErrorBanner(s.actionError)
    }
    s.deleting?.let { a ->
        ConfirmDialog(stringResource(R.string.w_delete_account_q), stringResource(R.string.w_delete_account_msg, a.bankName, a.accountLast4),
            stringResource(R.string.w_delete), s.busy, vm::delete, vm::cancelDelete)
    }
}

// ---------------------------------------------------------------- 4.28–4.31 Add bank account

@Composable
fun AddBankScreen(onBack: () -> Unit, onDone: () -> Unit, onWallet: () -> Unit, vm: AddBankViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var bankSheet by remember { mutableStateOf(false) }

    s.added?.let { a ->
        WalletScaffold(stringResource(R.string.w_add_account), null, bottom = {
            AslPrimaryButton(stringResource(R.string.w_done), onDone)
            AslButton(stringResource(R.string.w_back_wallet), onWallet, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline)
        }) {
            ResultBlock(ResultKind.Warn, stringResource(R.string.w_account_added), stringResource(R.string.w_account_added_sub), listOf(
                KeyValue(stringResource(R.string.w_bank), a.bankName),
                KeyValue(stringResource(R.string.w_account), "••${a.accountLast4}"),
                KeyValue(stringResource(R.string.w_holder), a.holder),
                KeyValue(stringResource(R.string.w_status), stringResource(if (a.verified) R.string.w_verified else R.string.w_verification_pending)),
            ))
        }
        return
    }

    val p = if (s.showErrors) s.form.problems() else emptySet()
    val f = s.form
    WalletScaffold(stringResource(R.string.w_add_account), onBack, secure = true, bottom = {
        AslPrimaryButton(stringResource(R.string.w_add_account_btn), vm::submit, loading = s.busy)
    }) {
        PickerRow(stringResource(R.string.w_bank), f.bank?.name, stringResource(R.string.w_choose_bank), AslIcons.Bank,
            error = if (Field.Bank in p) stringResource(R.string.w_bank_needed) else null) { vm.loadOptions(); bankSheet = true }
        // Account number is masked by default with a Show/Hide toggle; the confirm field below stays visible so it can be re-typed and checked.
        AslTextField(f.account, vm::onAccount, stringResource(R.string.w_account_no), password = true, keyboardType = KeyboardType.Number, maxLength = 18,
            error = if (Field.Account in p) stringResource(R.string.w_account_bad) else null)
        AslTextField(f.confirm, vm::onConfirm, stringResource(R.string.w_account_confirm), keyboardType = KeyboardType.Number, maxLength = 18,
            error = if (Field.Confirm in p || (f.confirm.length >= f.account.length && f.confirm.isNotEmpty() && !f.confirmMatches)) stringResource(R.string.w_account_mismatch) else null,
            success = if (f.confirmMatches && f.account.length >= 9) stringResource(R.string.w_account_match) else null)
        AslTextField(f.ifsc, vm::onIfsc, stringResource(R.string.w_ifsc), maxLength = 11,
            error = if (Field.Ifsc in p) stringResource(R.string.w_ifsc_bad) else null)
        AslTextField(f.holder, vm::onHolder, stringResource(R.string.w_holder), maxLength = 100,
            error = if (Field.Holder in p) stringResource(R.string.w_holder_bad) else null)
        HeaderText(stringResource(R.string.w_whose_account))
        SegmentedControl(listOf(stringResource(R.string.w_own), stringResource(R.string.w_relative)), AccountOwner.entries.indexOf(f.owner), { vm.onOwner(AccountOwner.entries[it]) })
        ErrorBanner(s.error)
    }
    if (bankSheet) BankOptionSheet(s.options, s.optionsLoading, s.optionsError != null, vm::loadOptions, onPick = { vm.onBank(it); bankSheet = false }) { bankSheet = false }
}

/** 4.29 Searchable list of the server's banks. */
@Composable
private fun BankOptionSheet(all: List<BankOption>, loading: Boolean, failed: Boolean, retry: () -> Unit, onPick: (BankOption) -> Unit, onDismiss: () -> Unit) = WalletSheet(onDismiss) {
    var q by rememberSaveable { mutableStateOf("") }
    val results = remember(q, all) { searchBanks(all, q) }
    Text(stringResource(R.string.w_choose_bank), style = AslTheme.type.title, color = AslTheme.colors.textPrimary)
    AslTextField(q, { q = it.take(40) }, stringResource(R.string.w_search_bank), trailingIcon = AslIcons.Search)
    when {
        loading -> repeat(3) { SkeletonRow() }
        failed && all.isEmpty() -> AslButton(stringResource(R.string.w_try_again), retry, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline, icon = AslIcons.Refresh)
        results.isEmpty() -> Note(stringResource(R.string.w_no_bank_match))
        else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            items(results, key = { it.id }) { b -> ListRow(b.name, icon = AslIcons.Bank, trailing = null, onClick = { onPick(b) }); Divider() }
        }
    }
}

