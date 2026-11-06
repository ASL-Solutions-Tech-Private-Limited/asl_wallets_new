package com.asl.wallet.feature.aeps.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asl.wallet.core.common.format.AmountInWords
import com.asl.wallet.core.common.format.Masking
import com.asl.wallet.core.common.format.Money
import com.asl.wallet.core.designsystem.component.AlertBanner
import com.asl.wallet.core.designsystem.component.AlertKind
import com.asl.wallet.core.designsystem.component.AslButton
import com.asl.wallet.core.designsystem.component.AslButtonStyle
import com.asl.wallet.core.designsystem.component.AslCard
import com.asl.wallet.core.designsystem.component.AslPrimaryButton
import com.asl.wallet.core.designsystem.component.AslTextField
import com.asl.wallet.core.designsystem.component.AslTopBar
import com.asl.wallet.core.designsystem.component.BigIcon
import com.asl.wallet.core.designsystem.component.ChoiceChips
import com.asl.wallet.core.designsystem.component.Divider
import com.asl.wallet.core.designsystem.component.KeyValue
import com.asl.wallet.core.designsystem.component.KeyValueList
import com.asl.wallet.core.designsystem.component.ListCard
import com.asl.wallet.core.designsystem.component.ListRow
import com.asl.wallet.core.designsystem.component.OtpInput
import com.asl.wallet.core.designsystem.component.PassbookTable
import com.asl.wallet.core.designsystem.component.ReceiptActionBar
import com.asl.wallet.core.designsystem.component.ReceiptCard
import com.asl.wallet.core.designsystem.component.SectionHeader
import com.asl.wallet.core.designsystem.component.SegmentedControl
import com.asl.wallet.core.designsystem.component.ShopInfo
import com.asl.wallet.core.designsystem.component.StatePresets
import com.asl.wallet.core.designsystem.component.StateView
import com.asl.wallet.core.designsystem.component.Status
import com.asl.wallet.core.designsystem.component.StatusPill
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.theme.AslGradients
import com.asl.wallet.core.designsystem.theme.AslShapes
import com.asl.wallet.core.designsystem.theme.AslTheme
import com.asl.wallet.core.network.UiError
import com.asl.wallet.core.security.ui.SecureScreen
import com.asl.wallet.core.designsystem.receipt.ReceiptDoc
import com.asl.wallet.core.designsystem.receipt.ReceiptExport
import com.asl.wallet.feature.aeps.R
import com.asl.wallet.feature.aeps.data.PhotoFiles
import com.asl.wallet.feature.aeps.domain.AepsProvider
import com.asl.wallet.feature.aeps.domain.AepsReceipt
import com.asl.wallet.feature.aeps.domain.CustomerBank
import com.asl.wallet.feature.aeps.domain.NextStep
import com.asl.wallet.feature.aeps.domain.OnboardingForm
import com.asl.wallet.feature.aeps.domain.OnboardingForm.Field
import com.asl.wallet.feature.aeps.domain.RdDevice
import com.asl.wallet.feature.aeps.domain.TxnOutcome
import com.asl.wallet.feature.aeps.domain.TxnType
import com.asl.wallet.feature.aeps.rd.RdServices
import com.asl.wallet.feature.aeps.rd.rememberRdCapture
import java.io.File

// ---------------------------------------------------------------- shared bits

@Composable
private fun AepsScaffold(
    title: String,
    onBack: () -> Unit,
    right: (@Composable () -> Unit)? = null,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AslTheme.colors
    SecureScreen()
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        AslTopBar(title, onBack = onBack, actions = { right?.invoke() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp).then(if (bottom == null) Modifier.navigationBarsPadding() else Modifier),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
        )
        if (bottom != null) Column(Modifier.fillMaxWidth().background(c.surface).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = bottom)
    }
}

@Composable
private fun uiErrorText(e: UiError): String = when (e) {
    is UiError.Server -> e.message
    UiError.NoInternet -> stringResource(R.string.ae_offline)
    else -> stringResource(R.string.ae_generic)
}

@Composable
private fun ErrorBanner(error: String?, uiError: UiError?) {
    val t = error ?: uiError?.let { uiErrorText(it) } ?: return
    AlertBanner(AlertKind.Error, t)
}

@Composable
private fun LocationLine() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(AslIcons.Location, null, tint = AslTheme.colors.textSecondary, modifier = Modifier.size(14.dp))
        Text(stringResource(R.string.ae_loc_ok), style = AslTheme.type.caption, color = AslTheme.colors.textSecondary)
    }
}

@Composable
private fun ResendRow(resendIn: Int, onResend: () -> Unit) {
    if (resendIn > 0) Text(stringResource(R.string.ae_resend_in, resendIn), style = AslTheme.type.caption, color = AslTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    else AslButton(stringResource(R.string.ae_resend), onResend, style = AslButtonStyle.Outline, compact = true, icon = AslIcons.Refresh)
}

@Composable
private fun ScanCard(title: String, subtitle: String, device: RdDevice?, onChangeScanner: () -> Unit) {
    val c = AslTheme.colors
    AslCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BigIcon(if (device?.isFace == true) AslIcons.Aadhaar else AslIcons.Fingerprint, c.heroGradient, size = 84.dp)
            Text(title, style = AslTheme.type.bodyStrong, color = c.textPrimary, textAlign = TextAlign.Center)
            Text(subtitle, style = AslTheme.type.caption, color = c.textSecondary, textAlign = TextAlign.Center)
            ScannerChip(device, onChangeScanner)
        }
    }
}

@Composable
private fun ScannerChip(device: RdDevice?, onClick: () -> Unit) {
    val context = LocalContext.current
    val ok = device != null && remember(device) { RdServices.isInstalled(context, device) }
    Box(Modifier.clip(AslShapes.pill).clickable(role = Role.Button, onClick = onClick)) {
        StatusPill(
            if (device == null) stringResource(R.string.ae_no_scanner) else if (ok) stringResource(R.string.ae_ready_chip, device.name) else device.name + " · " + stringResource(R.string.ae_scanner_missing),
            if (ok) Status.Success else Status.Pending,
        )
    }
}

/** 3.26 Choose scanner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannerSheet(devices: List<RdDevice>, selected: RdDevice?, onSelect: (RdDevice) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = AslTheme.colors.surface, shape = AslShapes.sheet) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.ae_scanner), style = AslTheme.type.title, color = AslTheme.colors.textPrimary)
            ListCard {
                devices.forEachIndexed { i, d ->
                    if (i > 0) Divider()
                    val installed = remember(d) { RdServices.isInstalled(context, d) }
                    ListRow(
                        d.name, subtitle = stringResource(if (installed) R.string.ae_scanner_ready else R.string.ae_scanner_missing),
                        icon = if (d.isFace) AslIcons.Aadhaar else AslIcons.Fingerprint,
                        iconColors = if (d == selected) AslGradients.action else null,
                        trailing = {
                            if (installed) StatusPill(stringResource(R.string.ae_badge_ready), Status.Success)
                            else Text(stringResource(R.string.ae_install), style = AslTheme.type.label, color = AslTheme.colors.primary)
                        },
                        onClick = { if (installed) { onSelect(d); onDismiss() } else RdServices.openStore(context, d.packageName) },
                    )
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? { var c = this; while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }; return null }

// ---------------------------------------------------------------- 3.14 bank chooser (shown over Home)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AepsBankChooserSheet(onChosen: (AepsProvider) -> Unit, onDismiss: () -> Unit, vm: AepsChooserViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = AslTheme.colors.surface, shape = AslShapes.sheet) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.ae_choose), style = AslTheme.type.title, color = AslTheme.colors.textPrimary, modifier = Modifier.semantics { heading() })
            s.error?.let { e ->
                AlertBanner(AlertKind.Error, uiErrorText(e))
                AslButton(stringResource(R.string.ae_try_again), vm::load, style = AslButtonStyle.Outline, compact = true, icon = AslIcons.Refresh)
            }
            if (s.loading) Text("…", style = AslTheme.type.title, color = AslTheme.colors.textSecondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            if (s.banks.isNotEmpty()) ListCard {
                s.banks.forEachIndexed { i, (p, badge) ->
                    if (i > 0) Divider()
                    val enabled = badge != BankBadge.Unavailable
                    ListRow(
                        p.displayName,
                        subtitle = stringResource(if (p.supportsDeposit) R.string.ae_yes_sub else R.string.ae_kotak_sub),
                        icon = AslIcons.Fingerprint,
                        iconColors = if (p == AepsProvider.Kotak) AslGradients.Service.Aeps.colors else AslGradients.Service.AadhaarPay.colors,
                        trailing = {
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                when (badge) {
                                    BankBadge.Ready -> StatusPill(stringResource(R.string.ae_badge_ready), Status.Success)
                                    BankBadge.LoginDue -> StatusPill(stringResource(R.string.ae_badge_login), Status.Pending)
                                    BankBadge.SetupNeeded -> StatusPill(stringResource(R.string.ae_badge_setup), Status.Info)
                                    BankBadge.Unavailable -> StatusPill(stringResource(R.string.ae_badge_off), Status.Neutral)
                                    BankBadge.Unknown -> Unit
                                }
                                if (p == s.last) Text(stringResource(R.string.ae_badge_last), style = AslTheme.type.micro, color = AslTheme.colors.primary)
                            }
                        },
                        onClick = if (enabled) ({ vm.choose(p); onChosen(p) }) else null,
                    )
                }
            }
            Text(stringResource(R.string.ae_last_choice), style = AslTheme.type.caption, color = AslTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ---------------------------------------------------------------- 3.15–3.16 gate

@Composable
fun AepsGateScreen(provider: AepsProvider, onStep: (NextStep) -> Unit, onBack: () -> Unit, vm: AepsGateViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.start(provider) }
    LaunchedEffect(provider) { vm.start(provider) }
    LaunchedEffect(s.route) { (s.route as? AepsRoute.Step)?.let { vm.consumeRoute(); onStep(it.step) } }
    val title = stringResource(R.string.ae_title, provider.displayName)
    AepsScaffold(title, onBack) {
        when {
            s.route == AepsRoute.NeedsLocationPermission || s.route == AepsRoute.LocationOff -> {
                StateView(AslIcons.Location, AslGradients.Service.Recharge.colors, stringResource(R.string.ae_loc_title), stringResource(R.string.ae_loc_msg)) {
                    if (s.route == AepsRoute.NeedsLocationPermission)
                        AslPrimaryButton(stringResource(R.string.ae_loc_allow), { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }, icon = AslIcons.Location)
                    else AslPrimaryButton(stringResource(R.string.ae_loc_settings), { runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } }, icon = AslIcons.Location)
                    AslButton(stringResource(R.string.ae_try_again), { vm.start(provider) }, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline)
                    AslButton(stringResource(R.string.ae_not_now), onBack, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline)
                }
            }
            s.error != null -> StateView(AslIcons.WifiOff, StatePresets.offlineColors, stringResource(R.string.ae_error_title), uiErrorText(s.error!!)) {
                AslPrimaryButton(stringResource(R.string.ae_try_again), { vm.start(provider) }, icon = AslIcons.Refresh)
            }
            else -> Processing(stringResource(R.string.ae_checking), provider.displayName, listOf(R.string.ae_step_account, R.string.ae_step_kyc, R.string.ae_step_open), current = 1)
        }
    }
}

@Composable
private fun Processing(title: String, subtitle: String, steps: List<Int>, current: Int) {
    val c = AslTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BigIcon(AslIcons.Bank, c.heroGradient, size = 84.dp)
        Text(title, style = AslTheme.type.title, color = c.textPrimary, textAlign = TextAlign.Center)
        Text(subtitle, style = AslTheme.type.caption, color = c.textSecondary)
        AslCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                steps.forEachIndexed { i, res ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (i < current) "✓" else if (i == current) "●" else "○", color = if (i < current) c.success else if (i == current) c.primary else c.textSecondary, style = AslTheme.type.label)
                        Text(stringResource(res), style = if (i == current) AslTheme.type.label else AslTheme.type.body, color = if (i <= current) c.textPrimary else c.textSecondary)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 3.17–3.22 onboarding

@Composable
fun AepsOnboardingScreen(onNext: (NextStep) -> Unit, onBack: () -> Unit, vm: OnboardingViewModel = hiltViewModel(), scanner: ScannerViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val device by scanner.device.collectAsStateWithLifecycle()
    var showScanner by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val capture = rememberRdCapture(vm::onCapture)
    LaunchedEffect(Unit) { vm.next.collect { onNext(it) } }
    // Photos never outlive onboarding.
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { PhotoFiles.wipeAll(context) } }
    val provider = vm.provider.displayName
    val back: () -> Unit = { if (s.step == OnboardStep.Details || s.step == OnboardStep.Documents) vm.back() else onBack() }
    val stepIndex = when (s.step) { OnboardStep.Details -> 0; OnboardStep.Documents -> 1; OnboardStep.Otp -> 2; OnboardStep.Biometric -> 3; else -> -1 }

    when (s.step) {
        OnboardStep.Done -> AepsScaffold(stringResource(R.string.ae_ob_title), onBack, bottom = { AslPrimaryButton(stringResource(R.string.ae_continue), vm::continueAfterDone, loading = s.busy) }) {
            StateView(AslIcons.Check, StatePresets.okColors, stringResource(R.string.ae_done_title), stringResource(R.string.ae_done_sub, provider))
            s.outletId?.let { AslCard { KeyValueList(listOf(KeyValue(stringResource(R.string.ae_outlet), it))) } }
        }
        OnboardStep.Intro -> AepsScaffold(stringResource(R.string.ae_ob_title), onBack, bottom = { AslPrimaryButton(stringResource(R.string.ae_start), vm::begin) }) {
            Text(stringResource(R.string.ae_ob_start_title, provider), style = AslTheme.type.title, color = AslTheme.colors.textPrimary)
            Text(stringResource(R.string.ae_ob_start_sub), style = AslTheme.type.body, color = AslTheme.colors.textSecondary)
            ListCard {
                listOf(R.string.ae_ob_s1 to R.string.ae_ob_s1_sub, R.string.ae_ob_s2 to R.string.ae_ob_s2_sub, R.string.ae_ob_s3 to R.string.ae_ob_s3_sub, R.string.ae_ob_s4 to R.string.ae_ob_s4_sub)
                    .forEachIndexed { i, (t, st) -> if (i > 0) Divider(); ListRow("${i + 1}. " + stringResource(t), subtitle = stringResource(st), trailing = null) }
            }
            SectionHeader(stringResource(R.string.ae_keep_ready))
            ListCard {
                ListRow(stringResource(R.string.ae_kr_ids), subtitle = stringResource(R.string.ae_kr_ids_sub), icon = AslIcons.Aadhaar, trailing = null); Divider()
                ListRow(stringResource(R.string.ae_kr_bank), subtitle = stringResource(R.string.ae_kr_bank_sub), icon = AslIcons.Bank, trailing = null); Divider()
                ListRow(stringResource(R.string.ae_kr_scanner), subtitle = stringResource(R.string.ae_kr_scanner_sub), icon = AslIcons.Fingerprint, trailing = null)
            }
        }
        OnboardStep.Details -> AepsScaffold(stringResource(R.string.ae_ob_title), back, bottom = { AslPrimaryButton(stringResource(R.string.ae_continue), vm::detailsDone) }) {
            Stepper(stepIndex)
            if (s.errors.isNotEmpty()) AlertBanner(AlertKind.Error, stringResource(R.string.ae_fix_fields))
            DetailsForm(s.form, s.errors, vm::edit)
        }
        OnboardStep.Documents -> AepsScaffold(stringResource(R.string.ae_ob_title), back, bottom = { AslPrimaryButton(stringResource(R.string.ae_submit), vm::submitDocuments, enabled = s.allPhotos, loading = s.busy) }) {
            Stepper(stepIndex)
            Text(stringResource(R.string.ae_docs_title), style = AslTheme.type.bodyStrong, color = AslTheme.colors.textPrimary)
            Text(stringResource(R.string.ae_docs_sub), style = AslTheme.type.caption, color = AslTheme.colors.textSecondary)
            ErrorBanner(s.error, s.uiError)
            DocRow(Doc.Aadhaar, R.string.ae_doc_aadhaar, s.photos[Doc.Aadhaar], vm::setPhoto)
            DocRow(Doc.Pan, R.string.ae_doc_pan, s.photos[Doc.Pan], vm::setPhoto)
            DocRow(Doc.Shop, R.string.ae_doc_shop, s.photos[Doc.Shop], vm::setPhoto)
            LocationLine()
        }
        OnboardStep.Otp -> AepsScaffold(stringResource(R.string.ae_ob_title), onBack, bottom = { AslPrimaryButton(stringResource(R.string.ae_verify_otp), vm::verifyOtp, enabled = s.canVerifyOtp, loading = s.busy) }) {
            Stepper(stepIndex)
            Text(stringResource(R.string.ae_otp_title), style = AslTheme.type.bodyStrong, color = AslTheme.colors.textPrimary)
            Text(stringResource(R.string.ae_otp_sub_bank), style = AslTheme.type.caption, color = AslTheme.colors.textSecondary)
            OtpInput(s.otp, vm::onOtp, length = 6)
            ErrorBanner(s.error, s.uiError)
            ResendRow(s.resendIn, vm::resendOtp)
        }
        OnboardStep.Biometric -> AepsScaffold(stringResource(R.string.ae_ob_title), onBack, bottom = {
            AslPrimaryButton(stringResource(if (device?.isFace == true) R.string.ae_scan_face else R.string.ae_scan), { device?.let { capture(it, vm.wadh, null) } ?: run { showScanner = true } }, loading = s.busy, icon = AslIcons.Fingerprint)
        }) {
            Stepper(stepIndex)
            ScanCard(stringResource(R.string.ae_scan_own), stringResource(R.string.ae_scan_own_sub, provider), device) { showScanner = true }
            AslCard { KeyValueList(listOf(KeyValue(stringResource(R.string.ae_ob_s3), "✓"), KeyValue(stringResource(R.string.ae_f_aadhaar), Masking.aadhaar(s.form.aadhaar)))) }
            ErrorBanner(s.error, s.uiError)
        }
    }
    if (showScanner) ScannerSheet(scanner.devices, device, scanner::select) { showScanner = false }
}

@Composable
private fun Stepper(current: Int) {
    val c = AslTheme.colors
    val labels = listOf(R.string.ae_ob_s1, R.string.ae_ob_s2, R.string.ae_ob_s3, R.string.ae_ob_s4)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, res ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 4.dp).size(height = 4.dp, width = 1.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (i < current) c.success else if (i == current) c.primary else c.outline))
                Text(stringResource(res), style = AslTheme.type.micro, color = if (i == current) c.primary else c.textSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DetailsForm(f: OnboardingForm, errors: Set<Field>, edit: ((OnboardingForm) -> OnboardingForm) -> Unit) {
    val bad = stringResource(R.string.ae_f_invalid)
    @Composable fun field(v: String, label: Int, fld: Field?, kb: KeyboardType = KeyboardType.Text, max: Int = 80, password: Boolean = false, on: (String) -> OnboardingForm.(String) -> OnboardingForm) =
        AslTextField(v, { edit { form -> on(it)(form, it) } }, stringResource(label), error = if (fld != null && fld in errors) bad else null, keyboardType = kb, maxLength = max, password = password)
    SectionHeader(stringResource(R.string.ae_sec_personal))
    field(f.firstName, R.string.ae_f_first, Field.FirstName, max = 50) { { copy(firstName = it) } }
    field(f.lastName, R.string.ae_f_last, Field.LastName, max = 50) { { copy(lastName = it) } }
    field(f.dob, R.string.ae_f_dob, Field.Dob, KeyboardType.Number, 10) { { copy(dob = formatDob(it)) } }
    field(f.email, R.string.ae_f_email, Field.Email, KeyboardType.Email, 100) { { copy(email = it.trim()) } }
    field(f.phone, R.string.ae_f_mobile, Field.Phone, KeyboardType.Phone, 10) { { copy(phone = it.filter(Char::isDigit)) } }
    field(f.aadhaar, R.string.ae_f_aadhaar, Field.Aadhaar, KeyboardType.NumberPassword, 12, password = true) { { copy(aadhaar = it.filter(Char::isDigit)) } }
    field(f.pan, R.string.ae_f_pan, Field.Pan, max = 10) { { copy(pan = it.uppercase().filter(Char::isLetterOrDigit)) } }
    field(f.address, R.string.ae_f_address, Field.Address, max = 200) { { copy(address = it) } }
    field(f.city, R.string.ae_f_city, Field.City, max = 50) { { copy(city = it) } }
    field(f.state, R.string.ae_f_state, Field.State, max = 50) { { copy(state = it) } }
    field(f.pincode, R.string.ae_f_pincode, Field.Pincode, KeyboardType.Number, 6) { { copy(pincode = it.filter(Char::isDigit)) } }
    SectionHeader(stringResource(R.string.ae_sec_bank))
    field(f.ifsc, R.string.ae_f_ifsc, Field.Ifsc, max = 11) { { copy(ifsc = it.uppercase().filter(Char::isLetterOrDigit)) } }
    field(f.bankName, R.string.ae_f_bank, Field.BankName, max = 80) { { copy(bankName = it) } }
    field(f.bankAccount, R.string.ae_f_account, Field.BankAccount, KeyboardType.Number, 18) { { copy(bankAccount = it.filter(Char::isDigit)) } }
    field(f.holderName, R.string.ae_f_holder, Field.HolderName, max = 80) { { copy(holderName = it) } }
    SectionHeader(stringResource(R.string.ae_sec_shop))
    field(f.shopName, R.string.ae_f_shop, Field.ShopName, max = 80) { { copy(shopName = it) } }
    field(f.shopPan, R.string.ae_f_shop_pan, Field.ShopPan, max = 10) { { copy(shopPan = it.uppercase().filter(Char::isLetterOrDigit)) } }
    field(f.shopAddress, R.string.ae_f_shop_address, Field.ShopAddress, max = 200) { { copy(shopAddress = it) } }
    field(f.shopCity, R.string.ae_f_shop_city, Field.ShopCity, max = 50) { { copy(shopCity = it) } }
    field(f.shopDistrict, R.string.ae_f_shop_district, Field.ShopDistrict, max = 50) { { copy(shopDistrict = it) } }
    field(f.shopState, R.string.ae_f_shop_state, Field.ShopState, max = 50) { { copy(shopState = it) } }
    field(f.shopPincode, R.string.ae_f_shop_pincode, Field.ShopPincode, KeyboardType.Number, 6) { { copy(shopPincode = it.filter(Char::isDigit)) } }
    field(f.gstin, R.string.ae_f_gstin, null, max = 15) { { copy(gstin = it.uppercase().filter(Char::isLetterOrDigit)) } }
}

/** Typing "12051990" shows "12/05/1990". */
internal fun formatDob(raw: String): String {
    val d = raw.filter(Char::isDigit).take(8)
    return when { d.length <= 2 -> d; d.length <= 4 -> d.take(2) + "/" + d.drop(2); else -> d.take(2) + "/" + d.substring(2, 4) + "/" + d.drop(4) }
}

@Composable
private fun DocRow(doc: Doc, label: Int, sizeBytes: Int?, onPhoto: (Doc, ByteArray?) -> Unit) {
    val context = LocalContext.current
    var file by rememberSaveable { mutableStateOf<String?>(null) }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = file?.let(::File)
        if (ok && f != null) onPhoto(doc, PhotoFiles.compress(f))
        f?.delete() // the full-size original never stays on the phone
    }
    val done = (sizeBytes ?: 0) > 0
    ListCard {
        ListRow(
            stringResource(label),
            subtitle = if (done) stringResource(R.string.ae_doc_done, "%.1f MB".format(sizeBytes!! / 1_000_000f)) else stringResource(R.string.ae_doc_take),
            icon = if (done) AslIcons.Check else AslIcons.Upload,
            iconColors = if (done) AslGradients.success else null,
            trailing = { if (done) Text(stringResource(R.string.ae_change), style = AslTheme.type.label, color = AslTheme.colors.primary) },
            onClick = {
                val f = PhotoFiles.newFile(context, doc.name.lowercase())
                file = f.path
                take.launch(FileProvider.getUriForFile(context, context.packageName + ".aepsfiles", f))
            },
        )
    }
}

// ---------------------------------------------------------------- 3.23–3.25 bank verification, biometric KYC, daily login

@Composable
fun AepsCheckScreen(kind: CheckKind, onNext: (NextStep) -> Unit, onBack: () -> Unit, vm: AepsCheckViewModel = hiltViewModel(), scanner: ScannerViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val device by scanner.device.collectAsStateWithLifecycle()
    var showScanner by remember { mutableStateOf(false) }
    val capture = rememberRdCapture(vm::onCapture)
    LaunchedEffect(kind) { vm.start(kind) }
    LaunchedEffect(Unit) { vm.next.collect { onNext(it) } }
    val provider = vm.provider.displayName
    val title = when (kind) {
        CheckKind.BankVerification -> stringResource(R.string.ae_bv_title)
        CheckKind.BiometricKyc -> stringResource(R.string.ae_bk_title, provider)
        CheckKind.DailyLogin -> stringResource(R.string.ae_dl_title)
    }
    val scan: () -> Unit = { device?.let { capture(it, vm.wadh, null) } ?: run { showScanner = true } }
    AepsScaffold(title, onBack, bottom = {
        when (s.phase) {
            CheckPhase.SendingOtp -> AslPrimaryButton(stringResource(R.string.ae_sending_otp), vm::sendBankOtp, loading = s.busy)
            CheckPhase.Otp -> AslPrimaryButton(stringResource(R.string.ae_verify_scan), vm::verifyOtp, enabled = s.otp.length == 6, loading = s.busy, icon = AslIcons.Fingerprint)
            CheckPhase.Scan -> AslPrimaryButton(stringResource(if (kind == CheckKind.DailyLogin) R.string.ae_scan_my else R.string.ae_scan), scan, loading = s.busy, icon = AslIcons.Fingerprint)
            CheckPhase.Done -> AslPrimaryButton(stringResource(R.string.ae_continue), {}, loading = true)
        }
    }) {
        when (kind) {
            CheckKind.BankVerification -> AlertBanner(AlertKind.Info, stringResource(R.string.ae_bv_info, provider), message = stringResource(R.string.ae_bv_info_sub), icon = AslIcons.Bank)
            CheckKind.BiometricKyc -> AlertBanner(AlertKind.Info, stringResource(R.string.ae_bk_info), message = stringResource(R.string.ae_bk_info_sub, provider), icon = AslIcons.Shield)
            CheckKind.DailyLogin -> Unit
        }
        if (s.phase == CheckPhase.Otp) {
            Text(stringResource(R.string.ae_otp_title), style = AslTheme.type.bodyStrong, color = AslTheme.colors.textPrimary)
            OtpInput(s.otp, vm::onOtp, length = 6)
            ResendRow(s.resendIn, vm::sendBankOtp)
        } else if (s.phase != CheckPhase.SendingOtp) {
            if (kind == CheckKind.DailyLogin) ScanCard(stringResource(R.string.ae_dl_scan), stringResource(R.string.ae_dl_scan_sub, provider), device) { showScanner = true }
            else ScanCard(stringResource(R.string.ae_scan_own), stringResource(R.string.ae_scan_own_sub, provider), device) { showScanner = true }
            LocationLine()
        }
        ErrorBanner(s.error, s.uiError)
    }
    if (showScanner) ScannerSheet(scanner.devices, device, scanner::select) { showScanner = false }
}

// ---------------------------------------------------------------- 3.27–3.38 transactions and results

@Composable
fun AepsTransactionScreen(
    shop: ShopInfo,
    onHome: () -> Unit,
    onBack: () -> Unit,
    onUseKotak: () -> Unit,
    onCallSupport: () -> Unit,
    onWhatsAppSupport: () -> Unit,
    vm: AepsTransactionViewModel = hiltViewModel(),
    scanner: ScannerViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val device by scanner.device.collectAsStateWithLifecycle()
    var showScanner by remember { mutableStateOf(false) }
    var showBanks by remember { mutableStateOf(false) }
    val capture = rememberRdCapture(vm::onCapture)
    val title = stringResource(R.string.ae_title, s.provider.displayName)
    val scan: () -> Unit = { device?.let { capture(it, null, vm.scanOtp) } ?: run { showScanner = true } }

    when (s.phase) {
        TxnPhase.Processing -> AepsScaffold(title, {}) {
            Processing(stringResource(R.string.ae_processing, typeLabel(s.type)), listOfNotNull(s.bank?.name, Masking.aadhaar(s.aadhaar)).joinToString(" · "),
                listOf(R.string.ae_p_captured, R.string.ae_p_bank, R.string.ae_p_receipt), current = 1)
            AlertBanner(AlertKind.Pending, stringResource(R.string.ae_dont_close))
        }
        TxnPhase.Result -> ResultScreen(s, shop, onHome, onUseKotak, onCallSupport, onWhatsAppSupport, again = { vm.again() }, newCustomer = { vm.again(keepCustomer = false) }, onType = { vm.again(it) })
        TxnPhase.CustomerOtp -> AepsScaffold(title, vm::cancelOtp, right = { ScannerChip(device) { showScanner = true } }, bottom = {
            AslPrimaryButton(stringResource(R.string.ae_verify_scan), { if (vm.otpEntered()) scan() }, enabled = s.otp.length >= 4, icon = AslIcons.Fingerprint)
            AslButton(stringResource(R.string.ae_cancel), vm::cancelOtp, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline)
        }) {
            AmountShow(s.amount)
            Text(stringResource(R.string.ae_otp_customer), style = AslTheme.type.bodyStrong, color = AslTheme.colors.textPrimary)
            Text(stringResource(R.string.ae_otp_customer_sub, Masking.mobile(s.mobile)), style = AslTheme.type.caption, color = AslTheme.colors.textSecondary)
            OtpInput(s.otp, vm::onOtp, length = 6)
            ErrorBanner(s.error, null)
            ResendRow(s.otpResendIn, vm::requestCustomerOtp)
        }
        TxnPhase.Form -> AepsScaffold(title, onBack, right = { ScannerChip(device) { showScanner = true } }, bottom = {
            AslPrimaryButton(stringResource(R.string.ae_scan_customer), { if (vm.onScanPressed()) scan() }, loading = s.busy, icon = AslIcons.Fingerprint)
        }) {
            SegmentedControl(s.types.map { typeLabel(it) }, s.types.indexOf(s.type), { vm.selectType(s.types[it]) })
            if (s.type == TxnType.Deposit) AlertBanner(AlertKind.Pending, stringResource(R.string.ae_wallet_note), message = stringResource(R.string.ae_wallet_note_sub), icon = AslIcons.Wallet)
            AslTextField(s.aadhaar, vm::onAadhaar, stringResource(R.string.ae_c_aadhaar), keyboardType = KeyboardType.NumberPassword, maxLength = 12, password = true,
                error = if (s.showErrors && !s.aadhaarOk) stringResource(R.string.ae_aadhaar_bad) else null)
            AslTextField(s.mobile, vm::onMobile, stringResource(R.string.ae_c_mobile), keyboardType = KeyboardType.Phone, maxLength = 10,
                error = if (s.showErrors && !s.mobileOk) stringResource(R.string.ae_mobile_bad) else null)
            SectionHeader(stringResource(R.string.ae_c_bank))
            val chips = (listOfNotNull(s.bank) + s.recentBanks).distinctBy { it.iin }.take(3)
            ChoiceChips(chips.map { shortBank(it.name) } + stringResource(R.string.ae_more_banks), chips.indexOfFirst { it == s.bank }.takeIf { it >= 0 }, { i ->
                if (i < chips.size) vm.onBank(chips[i]) else showBanks = true
            })
            if (s.showErrors && s.bank == null) Text(stringResource(R.string.ae_bank_needed), style = AslTheme.type.caption, color = AslTheme.colors.error)
            s.bank?.let { Text(it.name, style = AslTheme.type.caption, color = AslTheme.colors.textSecondary) }
            if (s.type.needsAmount) {
                AslTextField(s.amountText, vm::onAmount, stringResource(R.string.ae_amount), keyboardType = KeyboardType.Number, maxLength = 5,
                    error = if (s.showErrors && !s.amountOk) stringResource(R.string.ae_amount_rule) else null)
                AmountShow(s.amount)
                ChoiceChips(s.quickAmounts.map { Money.format(java.math.BigDecimal(it), showPaise = false) }, s.quickAmounts.indexOfFirst { s.amount?.toLong() == it }.takeIf { it >= 0 }, { vm.onQuick(s.quickAmounts[it]) })
            }
            ErrorBanner(s.error, null)
            LocationLine()
        }
    }
    if (showScanner) ScannerSheet(scanner.devices, device, scanner::select) { showScanner = false }
    if (showBanks) BankPicker(vm.banks, s.recentBanks, { vm.onBank(it); showBanks = false }) { showBanks = false }
}

private fun shortBank(name: String): String = name
    .replace("State Bank of India", "SBI").replace("Punjab National Bank", "PNB").replace("Bank of Baroda", "BoB")
    .let { if (it.length > 18) it.take(16) + "…" else it }

@Composable
private fun typeLabel(t: TxnType) = stringResource(when (t) {
    TxnType.Withdraw -> R.string.ae_t_withdraw; TxnType.Balance -> R.string.ae_t_balance
    TxnType.Statement -> R.string.ae_t_statement; TxnType.Deposit -> R.string.ae_t_deposit
})

@Composable
private fun AmountShow(amount: java.math.BigDecimal?) {
    if (amount == null || amount.signum() <= 0) return
    AslCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Money.format(amount, showPaise = false), style = AslTheme.type.amountDisplay, color = AslTheme.colors.primary)
            Text(AmountInWords.english(amount), style = AslTheme.type.caption, color = AslTheme.colors.textSecondary, textAlign = TextAlign.Center)
        }
    }
}

/** 3.30 Choose customer's bank: popular first, then search. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankPicker(all: List<CustomerBank>, popular: List<CustomerBank>, onPick: (CustomerBank) -> Unit, onDismiss: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val results = remember(q, all) { AepsTransactionViewModel.search(all, q) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = AslTheme.colors.surface, shape = AslShapes.sheet) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AslTextField(q, { q = it.take(40) }, stringResource(R.string.ae_search_bank), trailingIcon = AslIcons.Search)
            if (q.isBlank() && popular.isNotEmpty()) {
                SectionHeader(stringResource(R.string.ae_recent))
                ChoiceChips(popular.map { shortBank(it.name) }, null, { onPick(popular[it]) })
            }
            SectionHeader(stringResource(R.string.ae_results))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(results, key = { it.iin }) { b ->
                    ListRow(b.name, icon = AslIcons.Bank, trailing = null, onClick = { onPick(b) })
                    Divider()
                }
            }
        }
    }
}

@Composable
private fun ResultScreen(
    s: TxnState, shop: ShopInfo, onHome: () -> Unit, onUseKotak: () -> Unit, onCall: () -> Unit, onWhatsApp: () -> Unit,
    again: () -> Unit, newCustomer: () -> Unit, onType: (TxnType) -> Unit,
) {
    val context = LocalContext.current
    when (val o = s.outcome) {
        is TxnOutcome.Success -> {
            val r = o.receipt
            val doc = receiptDoc(r)
            var printChoice by remember { mutableStateOf(false) }
            val shareTitle = stringResource(R.string.ae_receipt_share_title)
            val actionLabels = listOf(stringResource(R.string.ae_share), stringResource(R.string.ae_pdf), stringResource(R.string.ae_print), stringResource(R.string.ae_help_call))
            AepsScaffold(stringResource(R.string.ae_receipt), onHome, bottom = {
                AslPrimaryButton(stringResource(R.string.ae_same_again), again, icon = AslIcons.Refresh)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AslButton(stringResource(R.string.ae_new_customer), newCustomer, Modifier.weight(1f), style = AslButtonStyle.Outline)
                    AslButton(stringResource(R.string.ae_home), onHome, Modifier.weight(1f), style = AslButtonStyle.Outline, icon = AslIcons.Home)
                }
            }) {
                if (r.type == TxnType.Balance) {
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BigIcon(AslIcons.Check, AslGradients.success, size = 64.dp)
                        Text("₹ " + (r.bankBalance ?: "—"), style = AslTheme.type.amountDisplay, color = AslTheme.colors.textPrimary)
                        Text(stringResource(R.string.ae_balance_of, r.bankName, r.customerAadhaarMasked), style = AslTheme.type.caption, color = AslTheme.colors.textSecondary)
                    }
                }
                ReceiptCard(
                    shop, typeLabel(r.type), r.amount?.let { Money.format(it) } ?: (r.bankBalance?.let { "₹ $it" } ?: ""), Status.Success,
                    doc.rows.map { KeyValue(it.first, it.second) }, footer = doc.footer, statusLabel = stringResource(R.string.ae_success),
                    amountWords = r.amount?.takeIf { it.signum() > 0 }?.let { AmountInWords.english(it) }, animate = true,
                )
                if (r.type == TxnType.Statement) {
                    SectionHeader(stringResource(R.string.ae_statement))
                    if (r.statement.isEmpty()) Text(stringResource(R.string.ae_no_lines), style = AslTheme.type.body, color = AslTheme.colors.textSecondary)
                    else PassbookTable(doc.statementHeader, doc.statement, listOf(0.8f, 2.4f, 0.5f, 1.3f))
                }
                ReceiptActionBar(
                    onWhatsApp = { ReceiptExport.shareText(context, shareTitle, ReceiptExport.text(shop, doc)) },
                    onSavePdf = { ReceiptExport.sharePdf(context, shop, doc, shareTitle, "ASL-AEPS-receipt.pdf") },
                    onPrint = { printChoice = true },
                    onHelp = onCall,
                    labels = actionLabels,
                )
                if (r.type == TxnType.Balance) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AslButton(stringResource(R.string.ae_t_statement), { onType(TxnType.Statement) }, Modifier.weight(1f), style = AslButtonStyle.Outline)
                    AslButton(stringResource(R.string.ae_t_withdraw), { onType(TxnType.Withdraw) }, Modifier.weight(1f))
                }
            }
            if (printChoice) PrintChoiceSheet(onDismiss = { printChoice = false }) { thermal ->
                printChoice = false
                context.findActivity()?.let { ReceiptExport.print(it, shareTitle, ReceiptExport.html(shop, doc, thermal), thermal) }
            }
        }
        is TxnOutcome.Failed -> AepsScaffold(stringResource(R.string.ae_receipt), onHome, bottom = {
            AslPrimaryButton(stringResource(R.string.ae_try_finger), again, icon = AslIcons.Fingerprint)
            AslButton(stringResource(R.string.ae_back_aeps), again, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline)
        }) {
            StateView(AslIcons.Close, StatePresets.errorColors, stringResource(R.string.ae_failed), o.reason ?: stringResource(R.string.ae_failed_default))
            AslCard { KeyValueList(listOfNotNull(
                KeyValue(stringResource(R.string.ae_r_aadhaar), Masking.aadhaar(s.aadhaar)),
                s.amount?.takeIf { s.type.needsAmount }?.let { KeyValue(stringResource(R.string.ae_amount), Money.format(it) + " · " + stringResource(R.string.ae_not_debited)) },
            )) }
        }
        TxnOutcome.Pending -> AepsScaffold(stringResource(R.string.ae_receipt), onHome, bottom = {
            AslPrimaryButton(stringResource(R.string.ae_home), onHome, icon = AslIcons.Home)
        }) {
            StateView(AslIcons.Clock, StatePresets.warnColors, stringResource(R.string.ae_pending), stringResource(R.string.ae_pending_sub))
            AslCard { KeyValueList(listOfNotNull(
                s.amount?.takeIf { s.type.needsAmount }?.let { KeyValue(stringResource(R.string.ae_amount), Money.format(it)) },
                KeyValue(stringResource(R.string.ae_r_aadhaar), Masking.aadhaar(s.aadhaar)),
                KeyValue(stringResource(R.string.ae_status), stringResource(R.string.ae_status_unknown)),
            )) }
            AlertBanner(AlertKind.Pending, stringResource(R.string.ae_pending_msg))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AslButton(stringResource(R.string.ae_help_call), onCall, Modifier.weight(1f), style = AslButtonStyle.Outline, icon = AslIcons.Phone)
                AslButton("WhatsApp", onWhatsApp, Modifier.weight(1f), style = AslButtonStyle.Outline, icon = AslIcons.Chat)
            }
        }
        TxnOutcome.ProviderUnavailable, null -> AepsScaffold(stringResource(R.string.ae_title, s.provider.displayName), onHome) {
            StateView(AslIcons.Clock, AslGradients.Service.AadhaarPay.colors, stringResource(R.string.ae_yes_na), stringResource(R.string.ae_yes_na_msg)) {
                AslPrimaryButton(stringResource(R.string.ae_use_kotak), onUseKotak)
                AslButton(stringResource(R.string.ae_back), onHome, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrintChoiceSheet(onDismiss: () -> Unit, onChoose: (thermal: Boolean) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = AslTheme.colors.surface, shape = AslShapes.sheet) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.ae_print_title), style = AslTheme.type.title, color = AslTheme.colors.textPrimary)
            ListCard {
                ListRow(stringResource(R.string.ae_print_thermal), icon = AslIcons.Print, onClick = { onChoose(true) }); Divider()
                ListRow(stringResource(R.string.ae_print_a4), icon = AslIcons.Document, onClick = { onChoose(false) })
            }
            Text(stringResource(R.string.ae_print_note), style = AslTheme.type.caption, color = AslTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun receiptDoc(r: AepsReceipt): ReceiptDoc = ReceiptDoc(
    title = typeLabel(r.type),
    status = stringResource(R.string.ae_success),
    amount = r.amount?.let { Money.format(it) },
    rows = listOfNotNull(
        stringResource(R.string.ae_r_aadhaar) to r.customerAadhaarMasked,
        stringResource(R.string.ae_r_mobile) to r.customerMobileMasked,
        stringResource(R.string.ae_r_bank) to r.bankName,
        r.bankBalance?.let { stringResource(R.string.ae_r_balance) to "₹ $it" },
        r.bankRef.takeIf { it.isNotBlank() }?.let { stringResource(R.string.ae_r_ref) to it },
        stringResource(R.string.ae_r_via) to "AEPS · ${r.provider.displayName}",
        r.time.takeIf { it.isNotBlank() }?.let { stringResource(R.string.ae_r_date) to it },
    ),
    statementHeader = listOf(stringResource(R.string.ae_col_date), stringResource(R.string.ae_col_narration), "", stringResource(R.string.ae_col_amount)),
    statement = r.statement.map { listOf(it.date, it.narration, if (it.isCredit) "Cr" else "Dr", (if (it.isCredit) "+" else "−") + it.amount) },
    footer = stringResource(R.string.ae_r_note),
)

// ---------------------------------------------------------------- Profile tab row: AEPS set-up per bank

/** "AEPS set-up · Kotak: ready · Yes Bank: login due" on the Profile tab (3.1). */
@Composable
fun AepsSetupRow(onClick: () -> Unit, vm: AepsChooserViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }
    val labels = mapOf(
        BankBadge.Ready to stringResource(R.string.ae_badge_ready), BankBadge.LoginDue to stringResource(R.string.ae_badge_login),
        BankBadge.SetupNeeded to stringResource(R.string.ae_badge_setup), BankBadge.Unavailable to stringResource(R.string.ae_badge_off),
    )
    val summary = s.banks.mapNotNull { (p, b) -> labels[b]?.let { "${p.displayName}: $it" } }.joinToString(" · ")
    val due = s.banks.count { it.second == BankBadge.LoginDue || it.second == BankBadge.SetupNeeded }
    ListCard {
        ListRow(
            stringResource(R.string.ae_setup_row), subtitle = summary.ifBlank { null }, icon = AslIcons.Fingerprint, iconColors = AslGradients.Service.Aeps.colors,
            trailing = { if (due > 0) StatusPill(stringResource(R.string.ae_due_count, due), Status.Pending) },
            onClick = onClick,
        )
    }
}
