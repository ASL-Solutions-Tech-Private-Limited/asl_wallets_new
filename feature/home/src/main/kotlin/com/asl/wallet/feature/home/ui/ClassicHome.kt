package com.asl.wallet.feature.home.ui

import androidx.compose.runtime.mutableStateOf
import com.asl.wallet.feature.home.domain.ServiceCatalogue
import com.asl.wallet.feature.home.domain.ServiceDestination
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asl.wallet.core.common.format.Money
import com.asl.wallet.core.designsystem.R as DS
import com.asl.wallet.core.designsystem.component.AlertBanner
import com.asl.wallet.core.designsystem.component.AlertKind
import com.asl.wallet.core.designsystem.component.AslButton
import com.asl.wallet.core.designsystem.component.AslButtonStyle
import com.asl.wallet.core.designsystem.component.IconBox
import com.asl.wallet.core.designsystem.component.LocalOnHeader
import com.asl.wallet.core.designsystem.component.SectionHeader
import com.asl.wallet.core.designsystem.component.ServiceArt
import com.asl.wallet.core.designsystem.component.ServiceGrid
import com.asl.wallet.core.designsystem.component.ServiceItem
import com.asl.wallet.core.designsystem.component.StatusBarTone
import com.asl.wallet.core.designsystem.component.WaveBand
import com.asl.wallet.core.designsystem.component.headerBackground
import com.asl.wallet.core.designsystem.component.popIn
import com.asl.wallet.core.designsystem.component.pressScale
import com.asl.wallet.core.designsystem.component.reduceMotion
import com.asl.wallet.core.designsystem.component.shimmer
import com.asl.wallet.core.designsystem.component.shine
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.theme.AslGradients
import com.asl.wallet.core.designsystem.theme.AslShapes
import com.asl.wallet.core.designsystem.theme.AslTheme
import com.asl.wallet.feature.home.R
import com.asl.wallet.feature.home.domain.HomeService
import com.asl.wallet.feature.home.domain.HomeSection
import com.asl.wallet.feature.home.domain.ServiceGroup
import com.asl.wallet.feature.home.domain.ServiceKind
import kotlinx.coroutines.delay
import java.math.BigDecimal
import kotlin.math.absoluteValue

/** Banking Services shows at most this many services, then "View More". */
private const val BANKING_TILES = 7

/** Reports a Home shortcut can open. The app maps each to its screen. */
enum class HomeReport { Recharge, Aeps, Dmt, Payout, Matm, Pan, Bbps, Ledger, FundRequests, Statement }

/**
 * Classic Home (branch ui/classic-look), following the old app's FIRST dashboard (chosen 7 Nov 2026):
 * navy "Dashboard" bar, navy → blue page, a white wallet card with big rounded corners, the news line, then a white
 * sheet with Banking Services (lavender), banner slider, Recharge & Report (navy), BBPS (yellow) and Reports.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClassicHomeTab(
    state: HomeUiState,
    vm: HomeViewModel,
    onAllServices: () -> Unit,
    onEditServices: () -> Unit,
    onNotifications: () -> Unit,
    onWallet: (() -> Unit)?,
    onAddMoney: (() -> Unit)?,
    onSeeAll: (() -> Unit)?,
    onReport: ((HomeReport) -> Unit)?,
    onReportsTab: () -> Unit,
    onArrange: () -> Unit,
    onBbps: ((String?) -> Unit)? = null,
    onCallSupport: () -> Unit = {},
) {
    val c = AslTheme.colors
    StatusBarTone.OnDarkHeader()
    // Like the old app: BBPS opens only when the retailer has it. While the service list is still loading the tap goes
    // through (the server checks again).
    var bbpsOff by remember { mutableStateOf(false) }
    val bbpsOn = state.services.isEmpty() || state.services.any { it.destination == ServiceDestination.App(ServiceCatalogue.BBPS_MODULE) }
    fun bbps(category: String?) = when {
        !bbpsOn -> bbpsOff = true
        onBbps != null -> onBbps(category)
        else -> vm.onComingSoon("BBPS")
    }
    if (bbpsOff) androidx.compose.material3.AlertDialog(
        onDismissRequest = { bbpsOff = false },
        containerColor = c.surface,
        title = { Text(stringResource(R.string.classic_bbps_off_title), style = AslTheme.type.title, color = c.textPrimary) },
        text = { Text(stringResource(R.string.classic_bbps_off_msg), style = AslTheme.type.body, color = c.textSecondary) },
        confirmButton = { AslButton(stringResource(R.string.classic_call_now), { bbpsOff = false; onCallSupport() }, compact = true, icon = AslIcons.Phone) },
        dismissButton = { AslButton(stringResource(R.string.classic_close), { bbpsOff = false }, style = AslButtonStyle.Outline, compact = true) },
    )
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(c.headerGradient))) {
        DashboardBar(state, onNotifications, onArrange = { vm.startArrange(); onArrange() })
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
                WalletCard1(state, vm, onWallet, onAddMoney, Modifier.padding(horizontal = 12.dp).popIn(0, rise = 20.dp))
                NewsLine(Modifier.padding(top = 12.dp))
                Column(
                    Modifier.padding(top = 12.dp).fillMaxWidth().popIn(120, rise = 60.dp)
                        .background(c.surface, AslShapes.sheet).padding(horizontal = 8.dp).padding(top = 10.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AnimatedVisibility(state.offline, enter = fadeIn() + slideInVertically()) {
                        AlertBanner(AlertKind.Pending, stringResource(R.string.home_offline_title), message = stringResource(R.string.home_offline_msg), icon = AslIcons.WifiOff)
                    }
                    if (state.wallet == null && !state.loading) {
                        AlertBanner(AlertKind.Error, stringResource(R.string.home_error_title), message = stringResource(R.string.home_error_msg), icon = AslIcons.WifiOff)
                        AslButton(stringResource(R.string.home_retry), { vm.refresh() }, style = AslButtonStyle.Outline, compact = true, icon = AslIcons.Refresh, loading = state.refreshing)
                    }

                    fun open(kind: ServiceKind, label: String) {
                        state.services.firstOrNull { it.kind == kind }?.let(vm::onServiceClick) ?: vm.onComingSoon(label)
                    }
                    // DTH shares the recharge module with Mobile. The server usually sends one "Recharge" service
                    // (classified as Mobile), so open that same service but mark the key so Recharge opens in DTH mode.
                    fun openDth(label: String) {
                        val svc = state.services.firstOrNull { it.kind == ServiceKind.Dth }
                            ?: state.services.firstOrNull { it.kind == ServiceKind.Mobile }?.copy(key = "dth")
                        svc?.let(vm::onServiceClick) ?: vm.onComingSoon(label)
                    }
                    fun report(r: HomeReport, label: String) = onReport?.invoke(r) ?: vm.onComingSoon(label)
                    fun pic(res: Int) = ServiceArt.Picture(res)
                    val neutral = AslGradients.Service.Neutral.colors

                    // Sections in the retailer's own order (Arrange Home); each rises in a little after the one above.
                    state.sections.forEachIndexed { i, section ->
                        val delayMs = 220 + i * 80
                        when (section) {
                            HomeSection.Banking -> Card1(
                                title = stringResource(R.string.classic_banking), bg = if (c.isDark) c.background else Color(0xFFE6E6FA), titleColor = c.primary, delayMs = delayMs,
                            ) {
                                // Only banking services (recharge and bills have their own cards), up to 7 + View More.
                                if (state.services.isEmpty()) ServicesShimmer() else {
                                    val banking = state.services.filter { it.group == ServiceGroup.Banking }.take(BANKING_TILES)
                                    val more = ServiceItem(stringResource(R.string.classic_r_more), AslIcons.More, neutral, art = pic(DS.drawable.classic_ic_view_more))
                                    val items = banking.map { it.toItem() } + more
                                    ServiceGrid(items, onClick = { item ->
                                        val idx = items.indexOf(item)
                                        if (idx == items.lastIndex) onAllServices() else vm.onServiceClick(banking[idx])
                                    }, startDelayMs = delayMs + 40)
                                }
                            }

                            HomeSection.Banners -> BannerSlider(Modifier.popIn(delayMs))

                            HomeSection.Recharge -> {
                                val mobile = stringResource(R.string.classic_mobile)
                                val dth = stringResource(R.string.classic_dth)
                                val rcReport = stringResource(R.string.classic_report)
                                Card1(title = stringResource(R.string.classic_sec_recharge), bg = c.brandNavy, titleColor = Color.White, delayMs = delayMs) {
                                    val items = listOf(
                                        ServiceItem(mobile, AslIcons.Phone, neutral, art = pic(DS.drawable.classic_ic_mob_recharge)),
                                        ServiceItem(dth, AslIcons.Globe, neutral, art = pic(DS.drawable.classic_ic_dth)),
                                        ServiceItem(rcReport, AslIcons.Report, neutral, art = pic(DS.drawable.classic_ic_mobile_report)),
                                    )
                                    ServiceGrid(items, columns = 3, startDelayMs = delayMs + 40, onClick = { when (items.indexOf(it)) {
                                        0 -> open(ServiceKind.Mobile, mobile); 1 -> openDth(dth); else -> report(HomeReport.Recharge, rcReport)
                                    } })
                                }
                            }

                            HomeSection.Bbps -> {
                                val bills = stringResource(R.string.classic_bbps)
                                Card1(
                                    title = stringResource(R.string.classic_bbps_title), bg = if (c.isDark) Color(0xFF2E2810) else Color(0xFFFFE89B),
                                    titleColor = if (c.isDark) Color(0xFFFFD66B) else Color(0xFF03274B),
                                    action = stringResource(R.string.classic_view_all), onAction = { bbps(null) }, delayMs = delayMs,
                                ) {
                                    val items = listOf(
                                        ServiceItem(stringResource(R.string.classic_electricity), AslIcons.Bill, neutral, art = pic(DS.drawable.classic_ic_electricity)),
                                        ServiceItem(stringResource(R.string.classic_fastag), AslIcons.Wallet, neutral, art = pic(DS.drawable.classic_ic_fastag)),
                                        ServiceItem(stringResource(R.string.classic_lpg), AslIcons.Bill, neutral, art = pic(DS.drawable.classic_ic_gas)),
                                        ServiceItem(stringResource(R.string.classic_r_bbps), AslIcons.Report, neutral, art = pic(DS.drawable.classic_ic_bbps_report)),
                                    )
                                    ServiceGrid(items, startDelayMs = delayMs + 40, onClick = { when (items.indexOf(it)) {
                                        // The old app's category names (BillerByCategoryActivity extras).
                                        0 -> bbps("Electricity"); 1 -> bbps("Fastag"); 2 -> bbps("LPG Gas")
                                        else -> report(HomeReport.Bbps, bills)
                                    } })
                                }
                            }

                            HomeSection.Reports -> Card1(
                                title = stringResource(R.string.classic_sec_reports), bg = c.surface, titleColor = c.primary,
                                action = stringResource(R.string.classic_view_all), onAction = onReportsTab, delayMs = delayMs,
                            ) {
                                val defs = listOf(
                                    Triple(R.string.classic_r_recharge, DS.drawable.classic_ic_mobile_report, HomeReport.Recharge),
                                    Triple(R.string.classic_r_aeps, DS.drawable.classic_ic_aeps_report, HomeReport.Aeps),
                                    Triple(R.string.classic_r_dmt, DS.drawable.classic_ic_dmt_report, HomeReport.Dmt),
                                    Triple(R.string.classic_r_payout, DS.drawable.classic_ic_payout_report, HomeReport.Payout),
                                    Triple(R.string.classic_r_matm, DS.drawable.classic_ic_matm_report, HomeReport.Matm),
                                    Triple(R.string.classic_r_bbps, DS.drawable.classic_ic_bbps_report, HomeReport.Bbps),
                                    Triple(R.string.classic_r_txn, DS.drawable.classic_ic_transction_report, HomeReport.Ledger),
                                )
                                val labels = defs.map { stringResource(it.first) }
                                val items = defs.mapIndexed { j, d -> ServiceItem(labels[j], AslIcons.Report, neutral, art = pic(d.second)) } +
                                    ServiceItem(stringResource(R.string.classic_r_more), AslIcons.More, neutral, art = pic(DS.drawable.classic_ic_view_more))
                                ServiceGrid(items, startDelayMs = delayMs + 40, onClick = { item ->
                                    val j = items.indexOf(item)
                                    if (j == items.lastIndex) onReportsTab() else report(defs[j].third, labels[j])
                                })
                            }

                            HomeSection.Recent -> Column(Modifier.popIn(delayMs), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val reportsTitle = stringResource(R.string.tab_reports)
                                SectionHeader(stringResource(R.string.home_recent), Modifier.padding(horizontal = 4.dp), action = stringResource(R.string.home_see_all), onAction = { onSeeAll?.invoke() ?: vm.onComingSoon(reportsTitle) })
                                RecentList(state)
                            }
                        }
                    }

                    if (state.user?.isDistributor == true) DistributorSection(vm)

                    // Arrange Home: also in the top bar and in Profile → Settings.
                    AslButton(
                        stringResource(R.string.arrange_title), { vm.startArrange(); onArrange() }, Modifier.fillMaxWidth().padding(top = 4.dp),
                        style = AslButtonStyle.Outline, compact = true, icon = AslIcons.Arrange,
                    )
                }
            }
        }
    }
}

/** The old app's navy "Dashboard" bar (runs up behind the status bar). */
@Composable
private fun DashboardBar(state: HomeUiState, onNotifications: () -> Unit, onArrange: () -> Unit) {
    val u = state.user
    // No bar of its own: the greeting sits on the screen's blue gradient, so the header and the page read as one.
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(46.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(Color.White).padding(3.dp)) {
            Image(painterResource(DS.drawable.classic_ic_asl_logo), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            Text(stringResource(R.string.classic_welcome_short), style = AslTheme.type.caption, color = Color.White.copy(alpha = 0.75f))
            Text(u?.name.orEmpty(), style = AslTheme.type.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RoundHeaderIcon(AslIcons.Arrange, stringResource(R.string.arrange_title), onArrange)
        RoundHeaderIcon(AslIcons.Bell, stringResource(R.string.home_notifications), onNotifications)
    }
}

@Composable
private fun RoundHeaderIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier.size(42.dp).pressScale(press).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
            .clickable(interactionSource = press, indication = androidx.compose.material3.ripple(), role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(21.dp)) }
}

/** White wallet card with the old app's big top-right and bottom-left corners. */
@Composable
private fun WalletCard1(state: HomeUiState, vm: HomeViewModel, onWallet: (() -> Unit)?, onAddMoney: (() -> Unit)?, modifier: Modifier) {
    val c = AslTheme.colors
    val navy = if (c.isDark) c.textPrimary else c.brandNavy
    val shape = RoundedCornerShape(topStart = 6.dp, topEnd = 36.dp, bottomEnd = 6.dp, bottomStart = 36.dp)
    val u = state.user
    Column(
        modifier.fillMaxWidth().shadow(6.dp, shape, spotColor = Color(0xFF1976D2)).clip(shape).background(c.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            BalanceBlock(state, vm, onWallet, navy, Modifier.weight(1f))
            AddMoneyButton(onAddMoney ?: { vm.onComingSoon("") })
        }
    }
}

// ---------------------------------------------------------------- balance

@Composable
private fun BalanceBlock(state: HomeUiState, vm: HomeViewModel, onWallet: (() -> Unit)?, ink: Color, modifier: Modifier) {
    val haptic = LocalHapticFeedback.current
    var spins by remember { mutableFloatStateOf(0f) }
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(spins) { if (spins > 0f) rotation.animateTo(360f * spins, tween(800, easing = FastOutSlowInEasing)) }
    Column(
        modifier.clip(RoundedCornerShape(8.dp))
            .then(if (onWallet != null) Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.home_open_wallet), onClick = onWallet) else Modifier),
    ) {
        Text(stringResource(R.string.classic_wallet_balance), style = AslTheme.type.bodyStrong.copy(fontSize = AslTheme.type.label.fontSize), color = ink)
        val hidden = state.balanceHidden
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val w = state.wallet
            when {
                state.loading && w == null -> Box(Modifier.padding(top = 4.dp).size(120.dp, 20.dp).clip(RoundedCornerShape(6.dp)).shimmer())
                w == null -> Text("₹ —", style = AslTheme.type.title, color = ink)
                else -> Text(
                    if (hidden) "₹ ••••" else Money.format(countUp(w.balance)),
                    style = AslTheme.type.headline, color = ink,
                    modifier = Modifier.clickable(role = Role.Button, onClickLabel = stringResource(if (hidden) R.string.home_show_balance else R.string.home_hide_balance), onClick = vm::toggleBalance),
                )
            }
            val refresh = stringResource(R.string.classic_refresh)
            Image(
                painterResource(DS.drawable.classic_ic_refresh), refresh,
                Modifier.size(28.dp).clip(CircleShape).graphicsLayer { rotationZ = rotation.value }
                    .clickable(role = Role.Button, onClickLabel = refresh) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); spins += 1f; vm.refresh()
                    }
                    .padding(3.dp),
            )
            // Show/Hide toggle sits with the balance it controls (kept clear of the Add Money button).
            Row(
                Modifier.clip(RoundedCornerShape(50))
                    .background(ink.copy(alpha = 0.07f))
                    .border(1.dp, ink.copy(alpha = 0.14f), RoundedCornerShape(50))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(if (hidden) R.string.home_show_balance else R.string.home_hide_balance),
                        onClick = vm::toggleBalance,
                    ).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(if (hidden) AslIcons.Eye else AslIcons.EyeOff, null, tint = ink, modifier = Modifier.size(14.dp))
                Text(
                    stringResource(if (hidden) R.string.home_show else R.string.home_hide),
                    style = AslTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = ink,
                )
            }
        }
        state.wallet?.takeIf { it.commission.signum() > 0 }?.let {
            Row(
                Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50))
                    .background(AslTheme.colors.successBg).padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(AslIcons.ArrowUp, null, tint = AslTheme.colors.success, modifier = Modifier.size(13.dp))
                Text(
                    stringResource(R.string.classic_earned, Money.format(it.commission, showPaise = false)),
                    style = AslTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = AslTheme.colors.success,
                )
            }
        }
    }
}

/** The balance counts up from 0 the first time it shows, and glides when it changes. */
@Composable
private fun countUp(target: BigDecimal): BigDecimal {
    val still = reduceMotion()
    val anim = remember { Animatable(if (still) target.toFloat() else 0f) }
    LaunchedEffect(target) { if (still) anim.snapTo(target.toFloat()) else anim.animateTo(target.toFloat(), tween(900, easing = FastOutSlowInEasing)) }
    return if (anim.value == target.toFloat()) target else BigDecimal(anim.value.toDouble()).setScale(2, java.math.RoundingMode.HALF_UP)
}

/** The old app's navy pill button with a blue shadow, plus a light shine. */
@Composable
private fun AddMoneyButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val navy = AslTheme.colors.brandNavy
    Text(
        stringResource(R.string.classic_add_money), style = AslTheme.type.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = Color.White,
        modifier = Modifier.pressScale(interaction, 0.94f)
            .shadow(5.dp, RoundedCornerShape(50), spotColor = Color(0xFF1976D2), ambientColor = Color(0xFF1976D2))
            .clip(RoundedCornerShape(50)).background(if (AslTheme.colors.isDark) Color(0xFF1360B0) else navy)
            .shine().clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    )
}

// ---------------------------------------------------------------- news line, banners, cards

@Composable
private fun NewsLine(modifier: Modifier = Modifier) {
    val still = reduceMotion()
    Text(
        stringResource(R.string.classic_news), style = AslTheme.type.bodyStrong.copy(fontSize = AslTheme.type.label.fontSize),
        color = if (AslTheme.colors.isDark) Color(0xFFCFE0FF) else Color(0xFF02105D),
        maxLines = if (still) 3 else 1,
        modifier = modifier.fillMaxWidth().background(if (AslTheme.colors.isDark) Color(0xFF0E2541) else Color(0xFFDCDCE7)).padding(10.dp)
            .then(if (still) Modifier else Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 800, velocity = 40.dp)),
    )
}

private data class Banner(val title: Int, val sub: Int, val colors: List<Color>)

/** Full-width banner slider (old SliderView): slides every 5 s, dots at the bottom inside the card. */
@Composable
private fun BannerSlider(modifier: Modifier) {
    val banners = remember {
        listOf(
            Banner(R.string.classic_banner1_t, R.string.classic_banner1_s, listOf(Color(0xFF03274B), Color(0xFF1976D2))),
            Banner(R.string.classic_banner2_t, R.string.classic_banner2_s, listOf(Color(0xFFC2185B), Color(0xFFF37409))),
            Banner(R.string.classic_banner3_t, R.string.classic_banner3_s, listOf(Color(0xFF1D7A3D), Color(0xFF36986E))),
        )
    }
    val pager = rememberPagerState { banners.size }
    val still = reduceMotion()
    LaunchedEffect(pager) {
        if (still) return@LaunchedEffect
        while (true) { delay(5_000); pager.animateScrollToPage((pager.currentPage + 1) % banners.size, animationSpec = tween(600, easing = FastOutSlowInEasing)) }
    }
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.padding(horizontal = 4.dp).fillMaxWidth().height(124.dp).shadow(3.dp, shape).clip(shape)) {
        HorizontalPager(pager, modifier = Modifier.fillMaxSize()) { page ->
            val b = banners[page]
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(b.colors)).shine(periodMs = 5_000, alpha = 0.22f).padding(16.dp)) {
                Box(Modifier.align(Alignment.BottomEnd).offset(40.dp, 50.dp).size(150.dp).background(Color.White.copy(alpha = 0.12f), CircleShape))
                Box(Modifier.align(Alignment.TopEnd).offset(20.dp, (-30).dp).size(80.dp).background(Color.White.copy(alpha = 0.10f), CircleShape))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(b.title), style = AslTheme.type.title, color = Color.White)
                    Text(stringResource(b.sub), style = AslTheme.type.caption, color = Color.White.copy(alpha = 0.9f))
                }
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(banners.size) { i ->
                val w by animateDpAsState(if (i == pager.currentPage) 16.dp else 6.dp, tween(350), label = "dot")
                Box(Modifier.width(w).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (i == pager.currentPage) Color(0xFF5A5A5A) else Color.White))
            }
        }
    }
}

/** A dashboard card: rounded 8 dp, coloured background, title (centred, as in the old app) and an optional action. */
@Composable
private fun Card1(
    title: String,
    bg: Color,
    titleColor: Color,
    delayMs: Int,
    action: String? = null,
    onAction: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.fillMaxWidth().popIn(delayMs, rise = 24.dp).clip(shape).background(bg).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
            Text(title, style = AslTheme.type.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = AslTheme.type.body.fontSize), color = titleColor,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() })
            if (action != null) Text(
                action, style = AslTheme.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = titleColor,
                modifier = Modifier.align(Alignment.CenterEnd).clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = onAction).padding(4.dp),
            )
        }
        content()
    }
}

@Composable
private fun ServicesShimmer() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                repeat(4) { Box(Modifier.size(72.dp, 80.dp).clip(RoundedCornerShape(8.dp)).shimmer()) }
            }
        }
    }
}

// ---------------------------------------------------------------- the old app's art per service

/** Banking services use the old app's animated (Lottie) icons; the rest use its PNG icons. Null keeps the vector icon. */
internal fun artOf(s: HomeService): ServiceArt? {
    val n = s.name.lowercase()
    return when (s.kind) {
        ServiceKind.Aeps -> ServiceArt.Lottie(DS.raw.classic_lottie_biometric)
        ServiceKind.MicroAtm -> ServiceArt.Lottie(DS.raw.classic_lottie_ic_matm)
        ServiceKind.MoneyTransfer ->
            if ("payout" in n || "settlement" in n || "send to bank" in n) ServiceArt.Lottie(DS.raw.classic_lottie_transaction_process)
            else ServiceArt.Lottie(DS.raw.classic_lottie_dmt)
        ServiceKind.Upi -> ServiceArt.Lottie(DS.raw.classic_lottie_ic_payout_upi_a)
        ServiceKind.CardToCash -> ServiceArt.Lottie(DS.raw.classic_lottie_cashtocard)
        ServiceKind.Aadhaar -> ServiceArt.Picture(DS.drawable.classic_ic_aadhaar_pay)
        ServiceKind.Mobile -> ServiceArt.Picture(DS.drawable.classic_ic_mob_recharge)
        ServiceKind.Dth -> ServiceArt.Picture(DS.drawable.classic_ic_dth)
        ServiceKind.Electricity -> ServiceArt.Picture(DS.drawable.classic_ic_electricity)
        ServiceKind.Fastag -> ServiceArt.Picture(DS.drawable.classic_ic_fastag)
        ServiceKind.Bills -> ServiceArt.Picture(DS.drawable.classic_ic_bbps_report)
        ServiceKind.Pan -> ServiceArt.Picture(DS.drawable.classic_ic_new_pan_a)
        ServiceKind.Bank -> ServiceArt.Picture(DS.drawable.classic_ic_aeps)
        else -> null
    }
}
