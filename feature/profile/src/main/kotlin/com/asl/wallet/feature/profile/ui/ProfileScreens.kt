package com.asl.wallet.feature.profile.ui

import com.asl.wallet.core.designsystem.R as DS
import androidx.compose.ui.graphics.graphicsLayer
import com.asl.wallet.core.designsystem.component.pressScale
import com.asl.wallet.core.designsystem.component.WaveBand
import com.asl.wallet.core.designsystem.component.ServiceArtView
import com.asl.wallet.core.designsystem.component.ServiceArt
import com.asl.wallet.feature.profile.domain.ServiceState
import com.asl.wallet.feature.profile.domain.ServiceEntry
import com.asl.wallet.feature.profile.domain.AppService
import com.asl.wallet.core.designsystem.component.keyboardAware
import androidx.compose.foundation.layout.imePadding
import com.asl.wallet.core.designsystem.component.shimmer
import com.asl.wallet.core.designsystem.component.popIn
import com.asl.wallet.core.designsystem.component.headerBackground
import com.asl.wallet.core.designsystem.component.StatusBarTone
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asl.wallet.core.common.validation.PasswordStrength
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
import com.asl.wallet.core.designsystem.component.SectionHeader
import com.asl.wallet.core.designsystem.component.SegmentedControl
import com.asl.wallet.core.designsystem.component.SkeletonRow
import com.asl.wallet.core.designsystem.component.StatePresets
import com.asl.wallet.core.designsystem.component.StateView
import com.asl.wallet.core.designsystem.component.Status
import com.asl.wallet.core.designsystem.component.StatusPill
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.theme.AslGradients
import com.asl.wallet.core.designsystem.theme.AslShapes
import com.asl.wallet.core.designsystem.theme.AslTheme
import com.asl.wallet.core.designsystem.theme.ThemeMode
import com.asl.wallet.core.network.UiError
import com.asl.wallet.core.security.ui.SecureScreen
import com.asl.wallet.feature.profile.R
import com.asl.wallet.feature.profile.data.SupportContacts
import com.asl.wallet.feature.profile.domain.ComplaintStatus
import com.asl.wallet.feature.profile.domain.SupportResult

// ---------------------------------------------------------------- shared bits

@Composable
private fun Screen(title: String, onBack: () -> Unit, close: Boolean = false, bottom: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = AslTheme.colors
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().keyboardAware()) {
        AslTopBar(title, onBack = onBack, closeIcon = close)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 20.dp).then(if (bottom == null) Modifier.navigationBarsPadding() else Modifier),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        if (bottom != null) Column(Modifier.fillMaxWidth().background(c.surface).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = bottom)
    }
}

@Composable
internal fun errorText(e: UiError): String = when (e) {
    is UiError.Server -> e.message
    UiError.NoInternet -> stringResource(R.string.pf_offline)
    else -> stringResource(R.string.pf_server_error)
}

@Composable
private fun FailedState(e: UiError, onRetry: () -> Unit) {
    StateView(
        if (e == UiError.NoInternet) AslIcons.WifiOff else AslIcons.Alert, StatePresets.offlineColors, stringResource(R.string.pf_load_title),
        when (e) { UiError.NoInternet -> stringResource(R.string.pf_offline); is UiError.Server -> e.message; else -> stringResource(R.string.pf_server_error) },
    ) {
        AslPrimaryButton(stringResource(R.string.pf_retry), onRetry, icon = AslIcons.Refresh)
    }
}

@Composable
private fun LoadingList() = ListCard { Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { repeat(4) { SkeletonRow() } } }

@Composable
private fun Avatar(initials: String, big: Boolean = false) {
    val s = if (big) 72.dp else 48.dp
    Box(Modifier.size(s).clip(if (big) AslShapes.hero else AslShapes.tile).background(Brush.linearGradient(AslGradients.action)), contentAlignment = Alignment.Center) {
        Text(initials, style = if (big) AslTheme.type.headline else AslTheme.type.bodyStrong, color = Color.White)
    }
}

fun openDialer(context: Context, phone: String): Boolean = launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
fun openWhatsApp(context: Context, phone: String): Boolean =
    launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + phone.filter(Char::isDigit).let { if (it.length == 10) "91$it" else it })))

private fun launch(context: Context, intent: Intent): Boolean = try { context.startActivity(intent); true } catch (_: ActivityNotFoundException) { false }

// ---------------------------------------------------------------- 3.1 Profile tab

@Composable
fun ProfileTab(
    onDetails: () -> Unit,
    onCommission: () -> Unit,
    onServices: () -> Unit,
    onChangePassword: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    versionName: String,
    isDistributor: Boolean,
    aepsRow: (@Composable () -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
    vm: ProfileViewModel = hiltViewModel(),
) {
    val c = AslTheme.colors
    val state by vm.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    // Classic look: the old app's profile header (navy → blue, rounded bottom) with the avatar, name and shop.
    StatusBarTone.OnDarkHeader()
    Column(Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().headerBackground(c.headerGradient, corner = 20.dp).statusBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.pf_title), style = AslTheme.type.title, color = Color.White, modifier = Modifier.fillMaxWidth().semantics { heading() })
            when (val s = state) {
                Loadable.Loading -> Box(Modifier.size(64.dp).clip(CircleShape).shimmer())
                is Loadable.Failed -> Unit
                is Loadable.Ready -> {
                    Box(
                        Modifier.popIn(80).size(68.dp).clip(CircleShape).background(Color.White).border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text(s.data.initials, style = AslTheme.type.title, color = c.brandNavy) }
                    Text(s.data.name.uppercase(), style = AslTheme.type.bodyStrong, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(s.data.shopName.ifBlank { null }, s.data.regno?.let { "R-$it" }).joinToString(" · "),
                        style = AslTheme.type.caption, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(if (isDistributor) R.string.pf_distributor else R.string.pf_retailer), style = AslTheme.type.micro, color = Color.White,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
        }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        (state as? Loadable.Failed)?.let { s ->
            AslCard { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertBanner(AlertKind.Error, errorText(s.error), icon = AslIcons.WifiOff)
                AslButton(stringResource(R.string.pf_retry), vm::load, style = AslButtonStyle.Outline, compact = true, icon = AslIcons.Refresh)
            } }
        }
        aepsRow?.invoke()
        SectionHeader(stringResource(R.string.pf_sec_account))
        ListCard {
            ListRow(stringResource(R.string.pf_details), subtitle = stringResource(R.string.pf_details_sub), icon = AslIcons.User, onClick = onDetails); Divider()
            ListRow(stringResource(R.string.pf_commission), subtitle = stringResource(R.string.pf_commission_sub), icon = AslIcons.Earnings, onClick = onCommission); Divider()
            ListRow(stringResource(R.string.pf_services), subtitle = stringResource(R.string.pf_services_sub), icon = AslIcons.Check, onClick = onServices)
        }
        SectionHeader(stringResource(R.string.pf_sec_security))
        ListCard {
            ListRow(stringResource(R.string.pf_password), subtitle = stringResource(R.string.pf_password_sub), icon = AslIcons.Lock, onClick = onChangePassword); Divider()
            ListRow(stringResource(R.string.pf_settings), subtitle = stringResource(R.string.pf_settings_sub), icon = AslIcons.Globe, onClick = onSettings)
        }
        SectionHeader(stringResource(R.string.pf_sec_support))
        ListCard { ListRow(stringResource(R.string.pf_help), subtitle = stringResource(R.string.pf_help_sub), icon = AslIcons.Help, onClick = onHelp) }
        extra?.invoke()
        AslButton(stringResource(R.string.pf_logout), { confirm = true }, Modifier.fillMaxWidth(), style = AslButtonStyle.Danger)
        Text(stringResource(R.string.pf_version, versionName), style = AslTheme.type.caption, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.pf_logout_title)) },
            text = { Text(stringResource(R.string.pf_logout_msg)) },
            confirmButton = { TextButton({ confirm = false; vm.logout() }) { Text(stringResource(R.string.pf_logout), color = AslTheme.colors.error) } },
            dismissButton = { TextButton({ confirm = false }) { Text(stringResource(R.string.pf_cancel)) } },
        )
    }
}

// ---------------------------------------------------------------- 3.2 Profile details (read-only)

@Composable
fun ProfileDetailsScreen(onBack: () -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    SecureScreen()
    Screen(stringResource(R.string.pf_details), onBack) {
        when (val s = state) {
            Loadable.Loading -> LoadingList()
            is Loadable.Failed -> FailedState(s.error, vm::load)
            is Loadable.Ready -> {
                val d = s.data
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Avatar(d.initials, big = true)
                    Text(d.name, style = AslTheme.type.title, color = AslTheme.colors.textPrimary)
                }
                AslCard { KeyValueList(listOf(
                    KeyValue(stringResource(R.string.pf_regno), d.regno?.let { "R-$it" } ?: "—"),
                    KeyValue(stringResource(R.string.pf_mobile), d.mobileMasked),
                    KeyValue(stringResource(R.string.pf_email), d.emailMasked),
                    KeyValue(stringResource(R.string.pf_pan), d.panMasked),
                    KeyValue(stringResource(R.string.pf_shop), d.shopName.ifBlank { "—" }),
                )) }
                SectionHeader(stringResource(R.string.pf_address_sec))
                AslCard { KeyValueList(listOf(
                    KeyValue(stringResource(R.string.pf_address), d.address.ifBlank { "—" }),
                    KeyValue(stringResource(R.string.pf_landmark), d.landmark.ifBlank { "—" }),
                    KeyValue(stringResource(R.string.pf_city), d.city.ifBlank { "—" }),
                    KeyValue(stringResource(R.string.pf_pincode), d.pincode.ifBlank { "—" }),
                )) }
                Text(stringResource(R.string.pf_readonly_note), style = AslTheme.type.caption, color = AslTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ---------------------------------------------------------------- 3.3 My commission

@Composable
fun CommissionScreen(onBack: () -> Unit, vm: CommissionViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val c = AslTheme.colors
    Screen(stringResource(R.string.pf_commission), onBack) {
        SegmentedControl(listOf(stringResource(R.string.pf_tab_banking), stringResource(R.string.pf_tab_prepaid)), tab, vm::selectTab)
        when (val s = state) {
            Loadable.Loading -> LoadingList()
            is Loadable.Failed -> FailedState(s.error, vm::load)
            is Loadable.Ready -> {
                val sections = if (tab == 0) s.data.banking else s.data.prepaid
                if (sections.isEmpty()) Text(stringResource(R.string.pf_commission_empty), style = AslTheme.type.body, color = c.textSecondary, modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                sections.forEach { sec ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader(sec.title, Modifier.weight(1f))
                        if (sec.isCharge) StatusPill(stringResource(R.string.pf_charges), Status.Pending)
                    }
                    AslCard { KeyValueList(sec.rows.map { KeyValue(it.label, it.value) }) }
                }
                Text(stringResource(R.string.pf_commission_note), style = AslTheme.type.caption, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ---------------------------------------------------------------- 3.4 Active services

/**
 * All the app's services with this retailer's status. Active → opens it ([onOpen]); Not active → contact support;
 * Coming soon → not in the app yet.
 */
@Composable
fun ActiveServicesScreen(
    onBack: () -> Unit,
    onOpen: (AppService) -> Unit = {},
    onCall: () -> Unit = {},
    onWhatsApp: () -> Unit = {},
    vm: ActiveServicesViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var ask by remember { mutableStateOf<ServiceEntry?>(null) }
    Screen(stringResource(R.string.pf_services), onBack) {
        when (val s = state) {
            Loadable.Loading -> LoadingList()
            is Loadable.Failed -> FailedState(s.error, vm::load)
            is Loadable.Ready -> {
                ServicesSummary(s.data)
                listOf(true to R.string.pf_svc_banking, false to R.string.pf_svc_bills).forEach { (banking, title) ->
                    val group = s.data.filter { it.service.banking == banking }
                    if (group.isNotEmpty()) {
                        SectionHeader(stringResource(title))
                        group.forEachIndexed { i, e ->
                            ServiceRow(e, Modifier.popIn(80 + i * 50, rise = 12.dp)) { if (e.state == ServiceState.Active) onOpen(e.service) else ask = e }
                        }
                    }
                }
            }
        }
    }
    ask?.let { e ->
        val c = AslTheme.colors
        AlertDialog(
            onDismissRequest = { ask = null },
            containerColor = c.surface,
            title = { Text(stringResource(if (e.state == ServiceState.ComingSoon) R.string.pf_svc_soon_title else R.string.pf_svc_off_title, serviceName(e.service)), style = AslTheme.type.title, color = c.textPrimary) },
            text = { Text(stringResource(if (e.state == ServiceState.ComingSoon) R.string.pf_svc_soon_msg else R.string.pf_svc_off_msg), style = AslTheme.type.body, color = c.textSecondary) },
            confirmButton = {
                if (e.state == ServiceState.NotActive) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AslButton(stringResource(R.string.pf_svc_whatsapp), { ask = null; onWhatsApp() }, style = AslButtonStyle.Outline, compact = true, icon = AslIcons.Chat)
                    AslButton(stringResource(R.string.pf_svc_call), { ask = null; onCall() }, compact = true, icon = AslIcons.Phone)
                } else AslButton(stringResource(R.string.pf_svc_ok), { ask = null }, compact = true)
            },
            dismissButton = { if (e.state == ServiceState.NotActive) TextButton({ ask = null }) { Text(stringResource(R.string.pf_cancel)) } },
        )
    }
}

@Composable
private fun serviceName(s: AppService) = stringResource(when (s) {
    AppService.Aeps -> R.string.pf_svc_aeps; AppService.MoneyTransfer -> R.string.pf_svc_dmt; AppService.Payout -> R.string.pf_svc_payout
    AppService.MicroAtm -> R.string.pf_svc_matm; AppService.UpiTransfer -> R.string.pf_svc_upi; AppService.CardToCash -> R.string.pf_svc_c2c
    AppService.AadhaarPay -> R.string.pf_svc_aadhaar_pay; AppService.MobileRecharge -> R.string.pf_svc_mobile; AppService.DthRecharge -> R.string.pf_svc_dth
    AppService.BillPayment -> R.string.pf_svc_bbps; AppService.Pan -> R.string.pf_svc_pan
})

@Composable
private fun serviceDesc(s: AppService) = stringResource(when (s) {
    AppService.Aeps -> R.string.pf_svc_aeps_d; AppService.MoneyTransfer -> R.string.pf_svc_dmt_d; AppService.Payout -> R.string.pf_svc_payout_d
    AppService.MicroAtm -> R.string.pf_svc_matm_d; AppService.UpiTransfer -> R.string.pf_svc_upi_d; AppService.CardToCash -> R.string.pf_svc_c2c_d
    AppService.AadhaarPay -> R.string.pf_svc_aadhaar_pay_d; AppService.MobileRecharge -> R.string.pf_svc_mobile_d; AppService.DthRecharge -> R.string.pf_svc_dth_d
    AppService.BillPayment -> R.string.pf_svc_bbps_d; AppService.Pan -> R.string.pf_svc_pan_d
})

private fun artOf(s: AppService): ServiceArt = when (s) {
    AppService.Aeps -> ServiceArt.Lottie(DS.raw.classic_lottie_biometric)
    AppService.MoneyTransfer -> ServiceArt.Lottie(DS.raw.classic_lottie_dmt)
    AppService.Payout -> ServiceArt.Lottie(DS.raw.classic_lottie_transaction_process)
    AppService.MicroAtm -> ServiceArt.Lottie(DS.raw.classic_lottie_ic_matm)
    AppService.UpiTransfer -> ServiceArt.Lottie(DS.raw.classic_lottie_ic_payout_upi_a)
    AppService.CardToCash -> ServiceArt.Lottie(DS.raw.classic_lottie_cashtocard)
    AppService.AadhaarPay -> ServiceArt.Picture(DS.drawable.classic_ic_aadhaar_pay)
    AppService.MobileRecharge -> ServiceArt.Picture(DS.drawable.classic_ic_mob_recharge)
    AppService.DthRecharge -> ServiceArt.Picture(DS.drawable.classic_ic_dth)
    AppService.BillPayment -> ServiceArt.Picture(DS.drawable.classic_ic_electricity)
    AppService.Pan -> ServiceArt.Picture(DS.drawable.classic_ic_new_pan_a)
}

/** "4 of 11 services active" on the brand gradient, with a bar that fills in. */
@Composable
private fun ServicesSummary(all: List<ServiceEntry>) {
    val on = all.count { it.state == ServiceState.Active }
    val fill by androidx.compose.animation.core.animateFloatAsState(
        if (all.isEmpty()) 0f else on.toFloat() / all.size, androidx.compose.animation.core.tween(900, delayMillis = 150), label = "svcFill",
    )
    val shape = RoundedCornerShape(topStart = 6.dp, topEnd = 26.dp, bottomEnd = 6.dp, bottomStart = 26.dp)
    Box(Modifier.fillMaxWidth().popIn(0, rise = 14.dp).clip(shape).background(Brush.linearGradient(AslTheme.colors.headerGradient))) {
        WaveBand(Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.pf_svc_summary, on, all.size), style = AslTheme.type.title, color = Color.White)
            Text(stringResource(R.string.pf_svc_summary_sub), style = AslTheme.type.caption, color = Color.White.copy(alpha = 0.85f))
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxWidth(fill).height(8.dp).clip(RoundedCornerShape(50)).background(Color(0xFF7CE0A3)))
            }
        }
    }
}

@Composable
private fun ServiceRow(e: ServiceEntry, modifier: Modifier, onClick: () -> Unit) {
    val c = AslTheme.colors
    val active = e.state == ServiceState.Active
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().pressScale(interaction, 0.97f).clip(shape).background(c.surface).border(1.dp, c.outline, shape)
            .clickable(interaction, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Not-active services look faded so the active ones stand out.
        Box(Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(c.tint).graphicsLayer { alpha = if (active) 1f else 0.45f }, contentAlignment = Alignment.Center) {
            ServiceArtView(artOf(e.service), c.primary, Modifier.size(36.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(serviceName(e.service), style = AslTheme.type.bodyStrong, color = if (active) c.textPrimary else c.textSecondary)
            Text(serviceDesc(e.service), style = AslTheme.type.caption, color = c.textSecondary, maxLines = 2)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (e.state) {
                ServiceState.Active -> StatusPill(stringResource(R.string.pf_active), Status.Success)
                ServiceState.NotActive -> StatusPill(stringResource(R.string.pf_svc_not_active), Status.Failed)
                ServiceState.ComingSoon -> StatusPill(stringResource(R.string.pf_svc_soon), Status.Pending)
            }
            Icon(if (active) AslIcons.Chevron else if (e.state == ServiceState.NotActive) AslIcons.Lock else AslIcons.Clock, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

// ---------------------------------------------------------------- 3.5–3.6 Change password

@Composable
fun ChangePasswordScreen(onDone: () -> Unit, onBack: () -> Unit, vm: ChangePasswordViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = AslTheme.colors
    SecureScreen()
    when (s.step) {
        ChangeStep.Done -> Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().keyboardAware().navigationBarsPadding().padding(16.dp)) {
            Spacer(Modifier.weight(1f))
            StateView(AslIcons.Check, StatePresets.okColors, stringResource(R.string.pf_pw_done), stringResource(R.string.pf_pw_done_msg)) {
                AslPrimaryButton(stringResource(R.string.pf_done), onDone)
            }
            Spacer(Modifier.weight(1f))
        }
        ChangeStep.Form -> Screen(stringResource(R.string.pf_password), onBack, bottom = {
            AslPrimaryButton(stringResource(R.string.pf_send_otp), vm::requestOtp, enabled = s.canRequest, loading = s.loading)
        }) {
            s.error?.let { AlertBanner(AlertKind.Error, errorText(it)) }
            AslTextField(s.current, vm::onCurrent, stringResource(R.string.pf_current_pw), password = true)
            AslTextField(s.new, vm::onNew, stringResource(R.string.pf_new_pw), password = true, error = if (s.sameAsCurrent) stringResource(R.string.pf_pw_same) else null)
            if (s.new.isNotEmpty()) StrengthCard(s)
            AslTextField(s.confirm, vm::onConfirm, stringResource(R.string.pf_confirm_pw), password = true, error = if (s.confirmMismatch) stringResource(R.string.pf_pw_mismatch) else null)
        }
        ChangeStep.Otp -> Screen(stringResource(R.string.pf_password), vm::backToForm, bottom = {
            AslPrimaryButton(stringResource(R.string.pf_change_pw), vm::verify, enabled = s.canVerify, loading = s.loading)
        }) {
            Text(stringResource(R.string.pf_otp_title), style = AslTheme.type.title, color = c.textPrimary)
            Text(stringResource(R.string.pf_otp_sent, s.sentTo), style = AslTheme.type.body, color = c.textSecondary)
            OtpInput(s.otp, vm::onOtp, length = ChangePasswordViewModel.OTP_LENGTH)
            when {
                s.triesLeft <= 0 -> AlertBanner(AlertKind.Error, stringResource(R.string.pf_no_tries))
                s.error != null -> AlertBanner(AlertKind.Error, if (s.error is UiError.Server) errorText(s.error!!) else stringResource(R.string.pf_tries_left, s.triesLeft))
            }
            if (s.resendIn > 0) Text(stringResource(R.string.pf_resend_in, s.resendIn), style = AslTheme.type.caption, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            else AslButton(stringResource(R.string.pf_resend), vm::requestOtp, style = AslButtonStyle.Outline, compact = true, icon = AslIcons.Refresh)
            AlertBanner(AlertKind.Info, stringResource(R.string.pf_other_phones), icon = AslIcons.Shield)
        }
    }
}

@Composable
private fun StrengthCard(s: ChangePasswordState) {
    val c = AslTheme.colors
    val (label, status) = when (s.strength) {
        PasswordStrength.TooShort -> R.string.pf_strength_short to Status.Failed
        PasswordStrength.Weak -> R.string.pf_strength_weak to Status.Failed
        PasswordStrength.Fair -> R.string.pf_strength_fair to Status.Pending
        PasswordStrength.Strong -> R.string.pf_strength_strong to Status.Success
    }
    AslCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pf_strength), style = AslTheme.type.label, color = c.textPrimary, modifier = Modifier.weight(1f))
                StatusPill(stringResource(label), status)
            }
            val k = s.checks
            listOf(
                k.length to R.string.pf_rule_len, k.digit to R.string.pf_rule_digit,
                k.symbol to R.string.pf_rule_symbol, (k.upper && k.lower) to R.string.pf_rule_case,
            ).forEach { (ok, res) ->
                Text((if (ok) "✓ " else "○ ") + stringResource(res), style = AslTheme.type.caption, color = if (ok) c.success else c.textSecondary)
            }
        }
    }
}

// ---------------------------------------------------------------- 3.7 Settings

@Composable
fun SettingsScreen(
    themeMode: ThemeMode, onTheme: (ThemeMode) -> Unit, versionName: String, onBack: () -> Unit, onArrangeHome: (() -> Unit)? = null,
    sound: com.asl.wallet.core.designsystem.component.ReceiptSound = com.asl.wallet.core.designsystem.component.ReceiptSound(),
    onSound: (com.asl.wallet.core.designsystem.component.ReceiptSound) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val langs = listOf("en" to "English", "hi" to "हिन्दी", "bn" to "বাংলা")
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().take(2).ifEmpty { "en" }
    Screen(stringResource(R.string.pf_settings), onBack) {
        SectionHeader(stringResource(R.string.pf_lang))
        ChoiceChips(langs.map { it.second }, langs.indexOfFirst { it.first == current }.coerceAtLeast(0), { i ->
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(langs[i].first))
        })
        SectionHeader(stringResource(R.string.pf_theme))
        SegmentedControl(
            listOf(stringResource(R.string.pf_theme_light), stringResource(R.string.pf_theme_dark), stringResource(R.string.pf_theme_system)),
            ThemeMode.entries.indexOf(themeMode), { onTheme(ThemeMode.entries[it]) },
        )
        if (onArrangeHome != null) {
            SectionHeader(stringResource(R.string.pf_home_layout))
            ListCard { ListRow(stringResource(R.string.pf_arrange_home), subtitle = stringResource(R.string.pf_arrange_home_sub), icon = AslIcons.Arrange, onClick = onArrangeHome) }
        }
        SectionHeader(stringResource(R.string.pf_sounds))
        ListCard {
            ListRow(stringResource(R.string.pf_sound_print), subtitle = stringResource(R.string.pf_sound_print_sub), icon = AslIcons.Print,
                trailing = { androidx.compose.material3.Switch(sound.printSound, { onSound(sound.copy(printSound = it)) }) }, onClick = { onSound(sound.copy(printSound = !sound.printSound)) })
            Divider()
            ListRow(stringResource(R.string.pf_sound_voice), subtitle = stringResource(R.string.pf_sound_voice_sub), icon = AslIcons.Chat,
                trailing = { androidx.compose.material3.Switch(sound.voice, { onSound(sound.copy(voice = it)) }) }, onClick = { onSound(sound.copy(voice = !sound.voice)) })
        }
        androidx.compose.animation.AnimatedVisibility(sound.voice) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pf_sound_speak), style = AslTheme.type.label, color = AslTheme.colors.textSecondary)
                SegmentedControl(
                    listOf(stringResource(R.string.pf_sound_amount_only), stringResource(R.string.pf_sound_date_time)),
                    if (sound.voiceWithDateTime) 1 else 0, { onSound(sound.copy(voiceWithDateTime = it == 1)) },
                )
                val sample = stringResource(R.string.pf_sound_sample)
                AslButton(stringResource(R.string.pf_sound_try), {
                    com.asl.wallet.core.designsystem.component.ReceiptVoice.speak(context,
                        if (sound.voiceWithDateTime) com.asl.wallet.core.designsystem.component.ReceiptVoice.withDateTime(context, sample) else sample)
                }, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline, icon = AslIcons.Chat, compact = true)
            }
        }
        Text(stringResource(R.string.pf_sound_note), style = AslTheme.type.caption, color = AslTheme.colors.textSecondary)
        SectionHeader(stringResource(R.string.pf_sec))
        // App lock is mandatory in this app (money app; set up at first sign-in), so it is shown, not toggled.
        ListCard {
            ListRow(stringResource(R.string.pf_applock), subtitle = stringResource(R.string.pf_applock_sub), icon = AslIcons.Lock, trailing = { StatusPill(stringResource(R.string.pf_active), Status.Success) }); Divider()
            ListRow(stringResource(R.string.pf_screens), subtitle = stringResource(R.string.pf_screens_sub), icon = AslIcons.Shield, trailing = null)
        }
        SectionHeader(stringResource(R.string.pf_about))
        AslCard { KeyValueList(listOf(KeyValue(stringResource(R.string.pf_app_version), versionName))) }
    }
}

// ---------------------------------------------------------------- 3.8 Help and support

@Composable
fun HelpScreen(contacts: SupportContacts, onRaise: () -> Unit, onMyComplaints: () -> Unit, onFeedback: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val noApp = stringResource(R.string.pf_no_app)
    Screen(stringResource(R.string.pf_help), onBack) {
        ContactButtons(contacts) { if (!it) Toast.makeText(context, noApp, Toast.LENGTH_SHORT).show() }
        SectionHeader(stringResource(R.string.pf_sec_complaints))
        ListCard {
            ListRow(stringResource(R.string.pf_raise), subtitle = stringResource(R.string.pf_raise_sub), icon = AslIcons.Alert, onClick = onRaise); Divider()
            ListRow(stringResource(R.string.pf_my_complaints), subtitle = stringResource(R.string.pf_my_complaints_sub), icon = AslIcons.Report, onClick = onMyComplaints)
        }
        SectionHeader(stringResource(R.string.pf_sec_feedback))
        ListCard { ListRow(stringResource(R.string.pf_rate), subtitle = stringResource(R.string.pf_rate_sub), icon = AslIcons.Star, onClick = onFeedback) }
        SectionHeader(stringResource(R.string.pf_faq))
        ListCard {
            listOf(R.string.pf_faq1_q to R.string.pf_faq1_a, R.string.pf_faq2_q to R.string.pf_faq2_a, R.string.pf_faq3_q to R.string.pf_faq3_a).forEachIndexed { i, (q, a) ->
                if (i > 0) Divider()
                Faq(stringResource(q), stringResource(a))
            }
        }
    }
}

@Composable
private fun ContactButtons(contacts: SupportContacts, onResult: (Boolean) -> Unit) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AslButton(stringResource(R.string.pf_call), { onResult(openDialer(context, contacts.phone)) }, Modifier.weight(1f), style = AslButtonStyle.Outline, icon = AslIcons.Phone)
        AslButton(stringResource(R.string.pf_whatsapp), { onResult(openWhatsApp(context, contacts.whatsapp)) }, Modifier.weight(1f), style = AslButtonStyle.Outline, icon = AslIcons.Chat)
    }
}

@Composable
private fun Faq(q: String, a: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize().clickable(role = Role.Button) { open = !open }.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(q, style = AslTheme.type.label, color = AslTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            Text(if (open) "−" else "+", style = AslTheme.type.title, color = AslTheme.colors.primary)
        }
        if (open) Text(a, style = AslTheme.type.body, color = AslTheme.colors.textSecondary)
    }
}

/** 3.12: shown when complaints/feedback are submitted before their API exists. Nothing typed is lost. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiNotReadySheet(title: String, contacts: SupportContacts, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val noApp = stringResource(R.string.pf_no_app)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = AslTheme.colors.surface, shape = AslShapes.sheet) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BigIcon(AslIcons.Clock, AslGradients.action, Modifier.align(Alignment.CenterHorizontally))
            Text(title, style = AslTheme.type.title, color = AslTheme.colors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.pf_api_soon_msg), style = AslTheme.type.body, color = AslTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            ContactButtons(contacts) { if (!it) Toast.makeText(context, noApp, Toast.LENGTH_SHORT).show() }
        }
    }
}

// ---------------------------------------------------------------- 3.9 Raise a complaint

@Composable
fun RaiseComplaintScreen(contacts: SupportContacts, onDone: () -> Unit, onBack: () -> Unit, vm: ComplaintViewModel = hiltViewModel()) {
    val s by vm.form.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val services = listOf("AEPS", "Micro ATM", "Money transfer", "Payout", "Recharge", "Bill pay", stringResource(R.string.pf_service_other))
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        vm.onAttachment(uri.lastPathSegment ?: "image", bytes)
    }
    val raisedMsg = s.raisedId?.let { stringResource(R.string.pf_c_done, it) }
    LaunchedEffect(s.raisedId) { if (raisedMsg != null) { Toast.makeText(context, raisedMsg, Toast.LENGTH_LONG).show(); onDone() } }
    SecureScreen()
    Screen(stringResource(R.string.pf_raise), onBack, close = true, bottom = {
        AslPrimaryButton(stringResource(R.string.pf_c_submit), vm::submit, enabled = s.canSubmit, loading = s.sending)
    }) {
        s.error?.let { AlertBanner(AlertKind.Error, it) }
        SectionHeader(stringResource(R.string.pf_c_service))
        ChoiceChips(services, services.indexOf(s.service).takeIf { it >= 0 }, { vm.onService(services[it]) })
        AslTextField(s.transactionId, vm::onTransaction, stringResource(R.string.pf_c_txn), maxLength = 40)
        AslTextField(s.description, vm::onDescription, stringResource(R.string.pf_c_what), placeholder = stringResource(R.string.pf_c_what_hint), singleLine = false, maxLength = 500)
        ListCard {
            ListRow(
                s.attachmentName ?: stringResource(R.string.pf_c_attach), subtitle = stringResource(R.string.pf_c_attach_sub), icon = AslIcons.Upload,
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
        }
    }
    if (s.apiNotReady) ApiNotReadySheet(stringResource(R.string.pf_api_soon_c), contacts, vm::dismissApiNotReady)
}

// ---------------------------------------------------------------- 3.10 My complaints

@Composable
fun MyComplaintsScreen(onBack: () -> Unit, vm: ComplaintViewModel = hiltViewModel()) {
    val list by vm.list.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(Unit) { vm.loadList() }
    Screen(stringResource(R.string.pf_my_complaints), onBack) {
        ChoiceChips(listOf(stringResource(R.string.pf_tab_open), stringResource(R.string.pf_tab_closed), stringResource(R.string.pf_tab_all)), tab, { tab = it })
        when (val r = list) {
            null -> LoadingList()
            SupportResult.ApiNotReady -> StateView(AslIcons.Clock, StatePresets.emptyColors, stringResource(R.string.pf_api_soon_c), stringResource(R.string.pf_list_soon_msg))
            is SupportResult.Error -> StateView(AslIcons.WifiOff, StatePresets.offlineColors, stringResource(R.string.pf_load_error), r.message.orEmpty()) {
                AslPrimaryButton(stringResource(R.string.pf_retry), vm::loadList)
            }
            is SupportResult.Ok -> {
                val shown = r.data.filter { when (tab) { 0 -> it.status != ComplaintStatus.Closed; 1 -> it.status == ComplaintStatus.Closed; else -> true } }
                if (shown.isEmpty()) StateView(AslIcons.Report, StatePresets.emptyColors, stringResource(R.string.pf_no_complaints), stringResource(R.string.pf_no_complaints_msg))
                else ListCard {
                    shown.forEachIndexed { i, cpl ->
                        if (i > 0) Divider()
                        val (lbl, st) = when (cpl.status) {
                            ComplaintStatus.Open -> R.string.pf_st_open to Status.Info
                            ComplaintStatus.InProgress -> R.string.pf_st_progress to Status.Pending
                            ComplaintStatus.Closed -> R.string.pf_st_closed to Status.Success
                        }
                        ListRow("${cpl.id} · ${cpl.service}", subtitle = "${cpl.summary} · ${cpl.raisedOn}", icon = AslIcons.Alert, trailing = { StatusPill(stringResource(lbl), st) })
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 3.11 Feedback

@Composable
fun FeedbackScreen(contacts: SupportContacts, onDone: () -> Unit, onBack: () -> Unit, vm: FeedbackViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = AslTheme.colors
    val tags = listOf(R.string.pf_f_tag1, R.string.pf_f_tag2, R.string.pf_f_tag3, R.string.pf_f_tag4, R.string.pf_f_tag5).map { stringResource(it) }
    val context = LocalContext.current
    val thanks = stringResource(R.string.pf_f_thanks)
    LaunchedEffect(s.sent) { if (s.sent) { Toast.makeText(context, thanks, Toast.LENGTH_SHORT).show(); onDone() } }
    Screen(stringResource(R.string.pf_rate), onBack, close = true, bottom = {
        AslPrimaryButton(stringResource(R.string.pf_f_send), vm::send, enabled = s.canSend, loading = s.sending)
    }) {
        Text(stringResource(R.string.pf_f_title), style = AslTheme.type.title, color = c.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            (1..5).forEach { n ->
                val desc = stringResource(R.string.pf_star_desc, n)
                Text(
                    if (n <= s.stars) "★" else "☆", fontSize = 36.sp, color = if (n <= s.stars) Color(0xFFF5A524) else c.textSecondary,
                    modifier = Modifier.clip(CircleShape).clickable(role = Role.Button) { vm.onStars(n) }.padding(6.dp).semantics { contentDescription = desc },
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { t ->
                        val on = t in s.tags
                        Text(t, style = AslTheme.type.label, color = if (on) Color.White else c.textPrimary,
                            modifier = Modifier.clip(AslShapes.chip).background(if (on) c.primary else c.surface).clickable(role = Role.Checkbox) { vm.toggleTag(t) }.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
            }
        }
        AslTextField(s.note, vm::onNote, stringResource(R.string.pf_f_note), singleLine = false, maxLength = 300)
        Spacer(Modifier.height(8.dp))
    }
    if (s.apiNotReady) ApiNotReadySheet(stringResource(R.string.pf_api_soon_f), contacts, vm::dismissApiNotReady)
}
