package com.asl.wallet.feature.home.ui

import com.asl.wallet.core.designsystem.component.keyboardAware
import androidx.compose.foundation.layout.imePadding
import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.text.format.DateFormat
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asl.wallet.core.common.format.Money
import com.asl.wallet.core.designsystem.component.AlertBanner
import com.asl.wallet.core.designsystem.component.AlertKind
import com.asl.wallet.core.designsystem.component.AslBottomNav
import com.asl.wallet.core.designsystem.component.AslButton
import com.asl.wallet.core.designsystem.component.AslButtonStyle
import com.asl.wallet.core.designsystem.component.AslCard
import com.asl.wallet.core.designsystem.component.AslPrimaryButton
import com.asl.wallet.core.designsystem.component.AslTextField
import com.asl.wallet.core.designsystem.component.AslTopBar
import com.asl.wallet.core.designsystem.component.BigIcon
import com.asl.wallet.core.designsystem.component.Divider
import com.asl.wallet.core.designsystem.component.IconBox
import com.asl.wallet.core.designsystem.component.ListCard
import com.asl.wallet.core.designsystem.component.NavItem
import com.asl.wallet.core.designsystem.component.SectionHeader
import com.asl.wallet.core.designsystem.component.ServiceGrid
import com.asl.wallet.core.designsystem.component.ServiceItem
import com.asl.wallet.core.designsystem.component.SkeletonRow
import com.asl.wallet.core.designsystem.component.StatePresets
import com.asl.wallet.core.designsystem.component.StateView
import com.asl.wallet.core.designsystem.component.Status
import com.asl.wallet.core.designsystem.component.TransactionRow
import com.asl.wallet.core.designsystem.component.WalletCard
import com.asl.wallet.core.designsystem.component.WalletCardLabels
import com.asl.wallet.core.designsystem.component.shimmer
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.theme.AslGradients
import com.asl.wallet.core.designsystem.theme.AslShapes
import com.asl.wallet.core.designsystem.theme.AslTheme
import com.asl.wallet.feature.home.R
import com.asl.wallet.feature.home.domain.HomeService
import com.asl.wallet.feature.home.domain.LedgerEntry
import com.asl.wallet.feature.home.domain.ServiceCatalogue
import com.asl.wallet.feature.home.domain.ServiceDestination
import com.asl.wallet.feature.home.domain.ServiceGroup
import com.asl.wallet.feature.home.domain.ServiceKind
import kotlinx.coroutines.delay
import java.net.URI
import java.util.Calendar
import java.util.Date

// ---------------------------------------------------------------- visuals

internal fun visualOf(kind: ServiceKind): Pair<ImageVector, List<Color>> = when (kind) {
    ServiceKind.Aeps -> AslIcons.Fingerprint to AslGradients.Service.Aeps.colors
    ServiceKind.MicroAtm -> AslIcons.Atm to AslGradients.Service.MicroAtm.colors
    ServiceKind.MoneyTransfer -> AslIcons.Send to AslGradients.Service.SendMoney.colors
    ServiceKind.Upi -> AslIcons.Upi to AslGradients.Service.Upi.colors
    ServiceKind.CardToCash -> AslIcons.Qr to AslGradients.Service.Qr.colors
    ServiceKind.Bank -> AslIcons.Bank to AslGradients.Service.Bills.colors
    ServiceKind.Mobile -> AslIcons.Phone to AslGradients.Service.Recharge.colors
    ServiceKind.Dth -> AslIcons.Globe to AslGradients.Service.Wallet.colors
    ServiceKind.Electricity -> AslIcons.Bill to AslGradients.Service.Recharge.colors
    ServiceKind.Bills -> AslIcons.Bill to AslGradients.Service.Bills.colors
    ServiceKind.Fastag -> AslIcons.Wallet to AslGradients.Service.Bills.colors
    ServiceKind.Insurance -> AslIcons.Shield to AslGradients.Service.AadhaarPay.colors
    ServiceKind.Pan -> AslIcons.Document to AslGradients.Service.Aeps.colors
    ServiceKind.Aadhaar -> AslIcons.Aadhaar to AslGradients.Service.AadhaarPay.colors
    ServiceKind.Travel -> AslIcons.Globe to AslGradients.Service.Qr.colors
    ServiceKind.Gift -> AslIcons.Star to AslGradients.Service.AadhaarPay.colors
    ServiceKind.Other -> AslIcons.More to AslGradients.Service.Neutral.colors
}

@Composable
internal fun HomeService.toItem(): ServiceItem {
    val (icon, colors) = visualOf(kind)
    val tag = when (destination) {
        ServiceDestination.ComingSoon -> stringResource(R.string.tag_soon)
        is ServiceDestination.Partner -> stringResource(R.string.tag_web)
        is ServiceDestination.App -> null
    }
    return ServiceItem(name, icon, colors, tag = tag, art = artOf(this))
}

@Composable
private fun groupTitle(g: ServiceGroup) = stringResource(
    when (g) {
        ServiceGroup.Banking -> R.string.group_banking
        ServiceGroup.RechargeBills -> R.string.group_bills
        ServiceGroup.More -> R.string.group_more
    },
)

// ---------------------------------------------------------------- shell with bottom tabs

/** Classic look: the old app's bottom bar, with Reports in place of Packages (decided 7 Nov 2026). */
enum class HomeTab { Home, Wallet, Reports, Profile }

/**
 * Signed-in shell: Home · Wallet · Reports · Profile. Wallet opens the Wallet screens (it isn't a tab page),
 * so the bar stays on the tab you were on.
 */
@Composable
fun HomeShell(
    vm: HomeViewModel,
    onAllServices: () -> Unit,
    onEditServices: () -> Unit,
    onNotifications: () -> Unit,
    onOpenModule: (Int, String) -> Unit = { _, _ -> },
    extraProfileAction: (@Composable () -> Unit)? = null,
    /** The real Profile tab (Module 3), supplied by the app. */
    profileTab: (@Composable () -> Unit)? = null,
    /** Module 4: the balance opens Wallet; "Add Money" opens Add money. */
    onWallet: (() -> Unit)? = null,
    onAddMoney: (() -> Unit)? = null,
    /** Module 6: the real Reports tab, and where Home's "See all" goes (the wallet ledger). */
    reportsTab: (@Composable () -> Unit)? = null,
    onSeeAll: (() -> Unit)? = null,
    /** Classic Home's report shortcuts. */
    onReport: ((HomeReport) -> Unit)? = null,
    /** Opens Arrange Home (section order). */
    onArrange: () -> Unit = {},
    /** BBPS: straight into a category ("Electricity" …), or all categories for null. */
    onBbps: ((String?) -> Unit)? = null,
    onCallSupport: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.Home) }
    val state by vm.state.collectAsStateWithLifecycle()
    HomeEffects(vm, onOpenModule)
    AskNotificationPermission()

    Column(Modifier.fillMaxSize().background(AslTheme.colors.sheet)) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                HomeTab.Home, HomeTab.Wallet -> ClassicHomeTab(
                    state, vm, onAllServices, onEditServices, onNotifications, onWallet, onAddMoney, onSeeAll, onReport,
                    onReportsTab = { tab = HomeTab.Reports }, onArrange = onArrange, onBbps = onBbps, onCallSupport = onCallSupport,
                )
                HomeTab.Reports -> reportsTab?.invoke() ?: PlaceholderTab(stringResource(R.string.tab_reports), AslIcons.Report)
                HomeTab.Profile -> profileTab?.invoke() ?: PlaceholderTab(stringResource(R.string.tab_profile), AslIcons.User, onLogout = vm::logout, extra = extraProfileAction)
            }
        }
        AslBottomNav(
            items = listOf(
                NavItem(stringResource(R.string.tab_home), AslIcons.Home),
                NavItem(stringResource(R.string.tab_wallet), AslIcons.Wallet),
                NavItem(stringResource(R.string.tab_reports), AslIcons.Report),
                NavItem(stringResource(R.string.tab_profile), AslIcons.User),
            ),
            selected = tab.ordinal,
            onSelect = { i ->
                val t = HomeTab.entries[i]
                if (t == HomeTab.Wallet && onWallet != null) onWallet() else tab = t
            },
        )
    }
    ServiceSheetHost(state, vm)
}

/** One-time events: partner sites open in a Custom Tab (address bar visible, nothing shared). */
@Composable
fun HomeEffects(vm: HomeViewModel, onOpenModule: (Int, String) -> Unit = { _, _ -> }) {
    val context = LocalContext.current
    val noBrowser = stringResource(R.string.home_no_browser)
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is HomeEvent.OpenPartner -> if (!openPartnerSite(context, e.url)) Toast.makeText(context, noBrowser, Toast.LENGTH_SHORT).show()
                is HomeEvent.OpenModule -> onOpenModule(e.module, e.serviceKey)
            }
        }
    }
}

internal fun openPartnerSite(context: Context, url: String): Boolean = try {
    CustomTabsIntent.Builder()
        .setShowTitle(true)
        .setUrlBarHidingEnabled(false)
        .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
        .build()
        .launchUrl(context, Uri.parse(url))
    true
} catch (_: ActivityNotFoundException) {
    false
}

// ---------------------------------------------------------------- Home tab: see ClassicHome.kt

@Composable
internal fun DistributorSection(vm: HomeViewModel) {
    SectionHeader(stringResource(R.string.dist_network))
    val items = listOf(
        ServiceItem(stringResource(R.string.dist_retailers), AslIcons.User, AslGradients.Service.Aeps.colors),
        ServiceItem(stringResource(R.string.dist_fund), AslIcons.Send, AslGradients.Service.SendMoney.colors),
        ServiceItem(stringResource(R.string.dist_add), AslIcons.Plus, AslGradients.Service.Upi.colors),
        ServiceItem(stringResource(R.string.dist_report), AslIcons.Report, AslGradients.Service.Bills.colors),
    )
    ServiceGrid(items, onClick = { vm.onComingSoon(it.label) })
}

@Composable
internal fun RecentList(state: HomeUiState) {
    val c = AslTheme.colors
    when {
        state.recentLoading && state.recent.isEmpty() -> ListCard { Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { repeat(3) { SkeletonRow() } } }
        state.recent.isEmpty() && state.recentError -> AlertBanner(AlertKind.Error, stringResource(R.string.home_recent_error), icon = AslIcons.Refresh)
        state.recent.isEmpty() -> ListCard {
            Text(stringResource(R.string.home_recent_empty), style = AslTheme.type.body, color = c.textSecondary, modifier = Modifier.padding(vertical = 18.dp).fillMaxWidth(), textAlign = TextAlign.Center)
        }
        else -> ListCard {
            state.recent.forEachIndexed { i, e ->
                if (i > 0) Divider()
                LedgerRow(e)
            }
        }
    }
}

@Composable
private fun LedgerRow(e: LedgerEntry) {
    val amount = Money.format(e.amount.abs())
    TransactionRow(
        initials = e.title.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "₹" },
        colors = if (e.isCredit) AslGradients.success else AslGradients.Service.Neutral.colors,
        title = e.title,
        subtitle = listOf(e.date, e.time).filter { it.isNotBlank() }.joinToString(" · "),
        amount = (if (e.isCredit) "+ " else "− ") + amount,
        statusText = stringResource(if (e.isCredit) R.string.home_credit else R.string.home_debit),
        status = if (e.isCredit) Status.Success else Status.Neutral,
    )
}

// ---------------------------------------------------------------- service sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceSheetHost(state: HomeUiState, vm: HomeViewModel) {
    val sheet = state.sheet ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = vm::dismissSheet, sheetState = sheetState, containerColor = AslTheme.colors.surface, shape = AslShapes.sheet) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val c = AslTheme.colors
            val (icon, colors) = visualOf(sheet.service.kind)
            when (sheet) {
                is ServiceSheet.Partner -> {
                    val host = remember(sheet.url) { runCatching { URI(sheet.url).host }.getOrNull().orEmpty() }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BigIcon(icon, colors, size = 48.dp)
                        Column(Modifier.weight(1f)) {
                            Text(sheet.service.name, style = AslTheme.type.bodyStrong, color = c.textPrimary)
                            Text(host, style = AslTheme.type.caption, color = c.textSecondary)
                        }
                    }
                    AlertBanner(AlertKind.Info, stringResource(R.string.home_sheet_partner_title), message = stringResource(R.string.home_sheet_partner_msg), icon = AslIcons.Globe)
                    AslPrimaryButton(stringResource(R.string.home_sheet_partner_open, host), vm::confirmPartner, icon = AslIcons.Share)
                    AslButton(stringResource(R.string.home_cancel), vm::dismissSheet, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline)
                }
                is ServiceSheet.ComingSoon -> {
                    BigIcon(AslIcons.Clock, AslGradients.action, Modifier.align(Alignment.CenterHorizontally))
                    SheetTitle(stringResource(R.string.home_sheet_soon_title, sheet.service.name))
                    SheetBody(stringResource(R.string.home_sheet_soon_msg))
                    AslPrimaryButton(stringResource(R.string.home_ok), vm::dismissSheet)
                }
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String) = Text(
    text, style = AslTheme.type.title, color = AslTheme.colors.textPrimary, textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth().semantics { heading() },
)

@Composable
private fun SheetBody(text: String) = Text(
    text, style = AslTheme.type.body, color = AslTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
)

// ---------------------------------------------------------------- All services + search

@Composable
fun AllServicesScreen(vm: HomeViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = AslTheme.colors
    // Leaving by any route (toolbar, system Back, gesture) starts the next visit with an empty search.
    DisposableEffect(vm) { onDispose { vm.onQueryChange("") } }
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().keyboardAware()) {
        AslTopBar(stringResource(R.string.home_all_services), onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AslTextField(
                value = state.query, onValueChange = vm::onQueryChange, label = stringResource(R.string.home_search_hint),
                trailingIcon = AslIcons.Search, maxLength = 40,
            )
            if (!state.servicesFromServer && state.services.isNotEmpty()) {
                Text(stringResource(R.string.home_offline_list), style = AslTheme.type.caption, color = c.textSecondary)
            }
            val groups = state.grouped()
            if (groups.isEmpty() && state.query.isNotBlank()) {
                StateView(AslIcons.Search, StatePresets.emptyColors, stringResource(R.string.home_search_empty, state.query.trim()), stringResource(R.string.home_search_empty_hint))
            }
            groups.forEach { (g, list) ->
                SectionHeader(groupTitle(g))
                ServiceGrid(list.map { it.toItem() }, onClick = { item -> list.firstOrNull { it.name == item.label }?.let(vm::onServiceClick) })
            }
        }
    }
    HomeEffects(vm)
    ServiceSheetHost(state, vm)
}

// ---------------------------------------------------------------- Edit Home services

@Composable
fun EditServicesScreen(vm: HomeViewModel, onDone: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = AslTheme.colors
    LaunchedEffect(Unit) { if (vm.state.value.editSelection == null) vm.startEdit() }
    val sel = state.editSelection.orEmpty()
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().keyboardAware()) {
        AslTopBar(stringResource(R.string.home_edit_title), onBack = { vm.cancelEdit(); onDone() }, closeIcon = true)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.home_edit_hint, ServiceCatalogue.MAX_PINNED), style = AslTheme.type.body, color = c.textSecondary)
            Text(
                stringResource(R.string.home_edit_count, sel.size, ServiceCatalogue.MAX_PINNED),
                style = AslTheme.type.label, color = c.primary,
            )
            state.grouped(state.services).forEach { (g, list) ->
                SectionHeader(groupTitle(g))
                ListCard {
                    list.forEachIndexed { i, s ->
                        if (i > 0) Divider()
                        EditRow(s, position = sel.indexOf(s.key).takeIf { it >= 0 }?.plus(1), full = sel.size >= ServiceCatalogue.MAX_PINNED) { vm.toggleEdit(s.key) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.fillMaxWidth().background(c.surface).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AslPrimaryButton(stringResource(R.string.home_save), { vm.saveEdit(); onDone() }, enabled = sel.isNotEmpty())
            AslButton(stringResource(R.string.home_reset), vm::resetEdit, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline)
        }
    }
}

@Composable
private fun EditRow(s: HomeService, position: Int?, full: Boolean, onToggle: () -> Unit) {
    val c = AslTheme.colors
    val (icon, colors) = visualOf(s.kind)
    val enabled = position != null || !full
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Checkbox, onClick = onToggle)
            .semantics { stateDescription = if (position != null) "Selected, position $position" else "Not selected" }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(38.dp).background(Brush.linearGradient(colors), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Text(s.name, style = AslTheme.type.body, color = if (enabled) c.textPrimary else c.textSecondary, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(26.dp).clip(CircleShape)
                .then(if (position != null) Modifier.background(Brush.linearGradient(AslGradients.action)) else Modifier.border(1.5.dp, c.outline, CircleShape)),
            contentAlignment = Alignment.Center,
        ) { if (position != null) Text("$position", style = AslTheme.type.micro, color = Color.White) }
    }
}

// ---------------------------------------------------------------- Notifications (API pending)

@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val c = AslTheme.colors
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().keyboardAware()) {
        AslTopBar(stringResource(R.string.home_notifications), onBack = onBack)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            StateView(AslIcons.Bell, StatePresets.emptyColors, stringResource(R.string.notif_empty_title), stringResource(R.string.notif_empty_msg))
        }
    }
}

// ---------------------------------------------------------------- placeholder tabs

@Composable
private fun PlaceholderTab(title: String, icon: ImageVector, onLogout: (() -> Unit)? = null, extra: (@Composable () -> Unit)? = null) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().keyboardAware().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StateView(icon, StatePresets.emptyColors, stringResource(R.string.tab_soon_title, title), stringResource(R.string.tab_soon_msg))
        extra?.invoke()
        if (onLogout != null) AslButton(stringResource(R.string.tab_logout), { confirm = true }, Modifier.fillMaxWidth(), style = AslButtonStyle.Danger)
    }
    if (confirm && onLogout != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.logout_title)) },
            text = { Text(stringResource(R.string.logout_msg)) },
            confirmButton = { TextButton({ confirm = false; onLogout() }) { Text(stringResource(R.string.tab_logout), color = AslTheme.colors.error) } },
            dismissButton = { TextButton({ confirm = false }) { Text(stringResource(R.string.home_cancel)) } },
        )
    }
}
