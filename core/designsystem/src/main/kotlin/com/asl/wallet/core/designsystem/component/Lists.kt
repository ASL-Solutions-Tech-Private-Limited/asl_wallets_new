package com.asl.wallet.core.designsystem.component

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.theme.AslGradients
import com.asl.wallet.core.designsystem.theme.AslShapes
import com.asl.wallet.core.designsystem.theme.AslTheme

/** Inside a classic header: icon buttons and icons turn white-on-blue. */
val LocalOnHeader = staticCompositionLocalOf { false }

/**
 * Top bar (classic look): the old app's toolbar, a navy → blue gradient with rounded bottom corners that runs up
 * behind the status bar. Back/close, title (+ optional subtitle), trailing actions.
 */
@Composable
fun AslTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    closeIcon: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = AslTheme.colors
    StatusBarTone.OnDarkHeader()
    CompositionLocalProvider(LocalOnHeader provides true, LocalContentColor provides Color.White) {
        Row(
            modifier.fillMaxWidth().padding(bottom = 10.dp).headerBackground(c.headerGradient, corner = 16.dp).padding(horizontal = 14.dp).padding(top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onBack != null) IconBox(if (closeIcon) AslIcons.Close else AslIcons.Back, if (closeIcon) "Close" else "Back", onBack)
            Column(Modifier.weight(1f)) {
                Text(title, style = AslTheme.type.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                if (subtitle != null) Text(subtitle, style = AslTheme.type.caption, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
            }
            actions()
        }
    }
}

/** 40 dp square icon button, at least a 48 dp touch target via padding. White-on-blue inside a classic header. */
@Composable
fun IconBox(icon: ImageVector, contentDescription: String, onClick: () -> Unit, badge: Boolean = false) {
    val c = AslTheme.colors
    val onHeader = LocalOnHeader.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.size(40.dp).clip(shape)
            .then(
                if (onHeader) Modifier.background(Color.White.copy(alpha = 0.14f)).border(1.dp, Color.White.copy(alpha = 0.28f), shape)
                else Modifier.background(c.surface).border(1.dp, c.outline, shape),
            )
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (onHeader) Color.White else c.textPrimary, modifier = Modifier.size(20.dp))
        if (badge) Box(Modifier.align(Alignment.TopEnd).offset((-8).dp, 8.dp).size(8.dp).background(Color(0xFFFF5A5F), RoundedCornerShape(50)))
    }
}

/**
 * A labelled button for the blue top bar ("My requests"): white text and icon on a see-through white pill, so it
 * reads clearly on the gradient. Off the header it falls back to the primary colour.
 */
@Composable
fun HeaderAction(label: String, onClick: () -> Unit, icon: ImageVector? = null) {
    val c = AslTheme.colors
    val onHeader = LocalOnHeader.current
    val ink = if (onHeader) Color.White else c.primary
    Row(
        Modifier.height(36.dp).clip(RoundedCornerShape(50))
            .background(if (onHeader) Color.White.copy(alpha = 0.16f) else c.tint)
            .border(1.dp, if (onHeader) Color.White.copy(alpha = 0.35f) else c.outline, RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = ink, modifier = Modifier.size(16.dp))
        Text(label, style = AslTheme.type.label, color = ink, maxLines = 1)
    }
}

/** Section heading: "SERVICES", with an optional action on the right. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    val c = AslTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = AslTheme.type.micro, color = c.textSecondary, modifier = Modifier.weight(1f).semantics { heading() })
        if (action != null) Text(action, style = AslTheme.type.label, color = c.primary, modifier = Modifier.clickable(role = Role.Button, onClick = onAction).padding(4.dp))
    }
}

/** Menu/list row with a soft or gradient icon tile. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColors: List<Color>? = null,
    trailing: (@Composable () -> Unit)? = { Icon(AslIcons.Chevron, null, tint = AslTheme.colors.textSecondary, modifier = Modifier.size(18.dp)) },
    onClick: (() -> Unit)? = null,
) {
    val c = AslTheme.colors
    Row(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (iconColors != null) Brush.linearGradient(iconColors) else Brush.linearGradient(listOf(c.tint, c.tint))),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = if (iconColors != null) Color.White else c.primary, modifier = Modifier.size(19.dp)) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = AslTheme.type.body.copy(fontWeight = AslTheme.type.label.fontWeight), color = c.textPrimary)
            if (subtitle != null) Text(subtitle, style = AslTheme.type.caption, color = c.textSecondary)
        }
        trailing?.invoke()
    }
}

/** Rows inside one outlined card, separated by dividers. */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = AslTheme.colors
    Column(modifier.fillMaxWidth().background(c.surface, AslShapes.card).border(1.dp, c.outline, AslShapes.card).padding(horizontal = 14.dp, vertical = 2.dp)) { content() }
}

@Composable
fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(AslTheme.colors.outline))

/** Transaction row used in Home, Reports and histories. */
@Composable
fun TransactionRow(
    initials: String,
    colors: List<Color>,
    title: String,
    subtitle: String,
    amount: String,
    statusText: String,
    status: Status,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val c = AslTheme.colors
    Row(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
            Text(initials, style = AslTheme.type.micro, color = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = AslTheme.type.body.copy(fontWeight = AslTheme.type.label.fontWeight), color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = AslTheme.type.caption, color = c.textSecondary, maxLines = 2)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(amount, style = AslTheme.type.label.copy(fontSize = AslTheme.type.body.fontSize), color = c.textPrimary)
            StatusPill(statusText, status)
        }
    }
}

/**
 * A service on a grid. [art] is the old app's Lottie or PNG icon when there is one; otherwise [icon] is drawn in
 * the service colour. [highlight] makes the tag a pulsing red badge (e.g. "NEW").
 */
data class ServiceItem(
    val label: String,
    val icon: ImageVector,
    val colors: List<Color>,
    val tag: String? = null,
    val locked: Boolean = false,
    val art: ServiceArt? = null,
    val highlight: Boolean = false,
)

/** Four-column service grid (Home, All services). Tiles pop in one after another; [startDelayMs] offsets a later grid. */
@Composable
fun ServiceGrid(items: List<ServiceItem>, onClick: (ServiceItem) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false, startDelayMs: Int = 150, columns: Int = 4) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // [columns] is the phone baseline; wider screens (tablet, landscape) add columns, never fewer, and never
        // more than there are items — so a 3-item row never spreads into empty gaps on a big screen.
        val cols = maxOf(columns, (maxWidth / 112.dp).toInt()).coerceAtMost(maxOf(columns, items.size))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.chunked(cols).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEachIndexed { i, item ->
                        ServiceTile(item, { onClick(item) }, Modifier.weight(1f).popIn(startDelayMs + (r * cols + i) * 50), compact)
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * Classic tile, as on the old app's first dashboard: a light #F4FBFF tile with the service icon and its name
 * inside. Squashes and ticks when pressed.
 */
@Composable
fun ServiceTile(item: ServiceItem, onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val c = AslTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(8.dp)
    Box(modifier.pressScale(interaction)) {
        Column(
            Modifier.fillMaxWidth().height(if (compact) 80.dp else 88.dp).clip(shape)
                .background(if (c.isDark) c.surface else Color(0xFFF4FBFF))
                .clickable(interaction, indication = null, role = Role.Button, onClickLabel = item.label) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick()
                }
                .padding(horizontal = 3.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        ) {
            val art = if (item.locked) ServiceArt.Vector(AslIcons.Lock) else item.art ?: ServiceArt.Vector(item.icon)
            // Same 34 dp slot for every icon, so labels line up whether the art is a picture or an animation.
            // Dark mode: the old app's icons (drawn for white) sit on a soft light circle so dark icons stay visible.
            Box(Modifier.size(40.dp).then(if (c.isDark && art !is ServiceArt.Vector) Modifier.clip(CircleShape).background(Color(0xFFE9F1FB)) else Modifier),
                contentAlignment = Alignment.Center) {
                ServiceArtView(art, item.colors.first(), Modifier.size(if (art is ServiceArt.Lottie) 40.dp else 30.dp))
            }
            Text(
                // Bold and a touch bigger so names read clearly on the light tile (user, 7 Nov 2026).
                item.label, style = AslTheme.type.caption.copy(fontWeight = FontWeight.SemiBold, lineHeight = AslTheme.type.caption.fontSize * 1.18f),
                color = if (c.isDark) c.textPrimary else c.brandNavy, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.tag != null) TileTag(item.tag, item.highlight, Modifier.align(Alignment.TopEnd).offset((-2).dp, (-5).dp))
    }
}

@Composable
private fun TileTag(text: String, highlight: Boolean, modifier: Modifier) {
    val pulse = if (highlight && !reduceMotion()) {
        val t = rememberInfiniteTransition(label = "tag")
        t.animateFloat(1f, 1.15f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "s").value
    } else 1f
    Text(
        text, style = AslTheme.type.micro.copy(fontSize = AslTheme.type.micro.fontSize * 0.72f), color = Color.White,
        modifier = modifier.graphicsLayer { scaleX = pulse; scaleY = pulse }
            .background(if (highlight) Color(0xFFCE0E00) else Color(0xFF5B6576), RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

data class NavItem(val label: String, val icon: ImageVector)

/**
 * Bottom navigation (classic look): the old app's navy bar. A soft highlight slides to the chosen tab and its icon
 * lifts slightly.
 */
@Composable
fun AslBottomNav(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = AslTheme.colors
    val haptic = LocalHapticFeedback.current
    val idle = Color(0xFF9FB3CC)
    BoxWithConstraints(modifier.fillMaxWidth().background(c.brandNavy).navigationBarsPadding()) {
        val w = maxWidth / items.size
        val x by animateDpAsState(w * selected + (w - 56.dp) / 2, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "pill")
        Box(Modifier.offset(x = x, y = 8.dp).size(56.dp, 30.dp).background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(15.dp)))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp)) {
            items.forEachIndexed { i, item ->
                val on = i == selected
                val lift by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "lift")
                Column(
                    Modifier.weight(1f).semantics { this.selected = on }
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(i)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        item.icon, null, tint = if (on) Color.White else idle,
                        modifier = Modifier.size(22.dp).graphicsLayer { translationY = -3.dp.toPx() * lift; val s = 1f + 0.1f * lift; scaleX = s; scaleY = s },
                    )
                    Text(item.label, style = AslTheme.type.micro, color = if (on) Color.White else idle)
                }
            }
        }
    }
}
