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
    val context = LocalContext.current
    var bankSheet by remember { mutableStateOf(false) }
    var photoFor by remember { mutableStateOf<DocKind?>(null) }
    // Photos never outlive this screen.
    DisposableEffect(Unit) { onDispose { DocPhotos.wipeAll(context) } }

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
        AslTextField(f.account, vm::onAccount, stringResource(R.string.w_account_no), keyboardType = KeyboardType.NumberPassword, maxLength = 18,
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
        DocRow(stringResource(R.string.w_pan_photo), stringResource(R.string.w_pan_photo_sub), f.panPhoto, Field.PanPhoto in p || s.photoError == DocKind.Pan) { photoFor = DocKind.Pan }
        DocRow(stringResource(R.string.w_passbook_photo), stringResource(R.string.w_passbook_photo_sub), f.passbookPhoto, Field.PassbookPhoto in p || s.photoError == DocKind.Passbook) { photoFor = DocKind.Passbook }
        ErrorBanner(s.error)
    }
    if (bankSheet) BankOptionSheet(s.options, s.optionsLoading, s.optionsError != null, vm::loadOptions, onPick = { vm.onBank(it); bankSheet = false }) { bankSheet = false }
    photoFor?.let { kind -> PhotoSourceSheet(kind, onPhoto = { b -> vm.onPhoto(kind, b); photoFor = null }) { photoFor = null } }
}

@Composable
private fun DocRow(title: String, sub: String, size: Int, error: Boolean, onClick: () -> Unit) {
    val done = size > 0
    ListCard {
        ListRow(
            title,
            subtitle = if (done) stringResource(R.string.w_photo_done, "%.1f MB".format(size / 1_000_000f)) else sub,
            icon = if (done) AslIcons.Check else AslIcons.Upload,
            iconColors = if (done) AslGradients.success else if (error) AslGradients.failed else null,
            trailing = { if (done) Text(stringResource(R.string.w_change), style = AslTheme.type.label, color = AslTheme.colors.primary) },
            onClick = onClick,
        )
    }
    if (error && !done) Text(stringResource(R.string.w_photo_needed), style = AslTheme.type.caption, color = AslTheme.colors.error)
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

/** 4.30 Camera (private cache, never the gallery) or gallery (system picker, no storage permission). */
@Composable
private fun PhotoSourceSheet(kind: DocKind, onPhoto: (ByteArray?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var file by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = file?.let(::File)
        if (ok && f != null) scope.launch { onPhoto(withContext(Dispatchers.IO) { DocPhotos.fromFile(f) }) } else { f?.delete(); onDismiss() }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { onPhoto(withContext(Dispatchers.IO) { DocPhotos.fromUri(context, uri) }) } else onDismiss()
    }
    WalletSheet(onDismiss) {
        Text(stringResource(if (kind == DocKind.Pan) R.string.w_pan_photo else R.string.w_passbook_photo), style = AslTheme.type.title, color = AslTheme.colors.textPrimary)
        ListCard {
            ListRow(stringResource(R.string.w_take_photo), subtitle = stringResource(R.string.w_take_photo_sub), icon = AslIcons.Camera, iconColors = AslGradients.Service.Aeps.colors, onClick = {
                val f = DocPhotos.newFile(context, kind.name.lowercase())
                file = f.path
                camera.launch(DocPhotos.uriFor(context, f))
            }); Divider()
            ListRow(stringResource(R.string.w_from_gallery), subtitle = stringResource(R.string.w_from_gallery_sub), icon = AslIcons.Upload, iconColors = AslGradients.Service.Wallet.colors, onClick = {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
        }
    }
}
