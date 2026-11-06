package com.asl.wallet.core.designsystem.component

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.theme.AslGradients
import com.asl.wallet.core.designsystem.theme.AslShapes
import com.asl.wallet.core.designsystem.theme.AslTheme

/** Top bar: back/close, title (+ optional subtitle), trailing actions. */
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
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (onBack != null) IconBox(if (closeIcon) AslIcons.Close else AslIcons.Back, if (closeIcon) "Close" else "Back", onBack)
        Column(Modifier.weight(1f)) {
            Text(title, style = AslTheme.type.title, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            if (subtitle != null) Text(subtitle, style = AslTheme.type.caption, color = c.textSecondary, maxLines = 1)
        }
        actions()
    }
}

/** 40 dp square icon button with an outline, at least a 48 dp touch target via padding. */
@Composable
fun IconBox(icon: ImageVector, contentDescription: String, onClick: () -> Unit, badge: Boolean = false) {
    val c = AslTheme.colors
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.surface).border(1.dp, c.outline, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = c.textPrimary, modifier = Modifier.size(20.dp))
        if (badge) Box(Modifier.align(Alignment.TopEnd).offset((-8).dp, 8.dp).size(8.dp).background(Color(0xFFFF5A5F), RoundedCornerShape(50)))
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

data class ServiceItem(val label: String, val icon: ImageVector, val colors: List<Color>, val tag: String? = null, val locked: Boolean = false)

/** Four-column colour-coded service grid (Home, All services). */
@Composable
fun ServiceGrid(items: List<ServiceItem>, onClick: (ServiceItem) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { item -> ServiceTile(item, { onClick(item) }, Modifier.weight(1f), compact) }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun ServiceTile(item: ServiceItem, onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val c = AslTheme.colors
    val tile = if (compact) 44.dp else 52.dp
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClickLabel = item.label, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            Box(
                Modifier.size(tile).shadow(8.dp, AslShapes.tile, ambientColor = item.colors.first(), spotColor = item.colors.first())
                    .background(Brush.linearGradient(item.colors), AslShapes.tile),
                contentAlignment = Alignment.Center,
            ) { Icon(if (item.locked) AslIcons.Lock else item.icon, null, tint = Color.White, modifier = Modifier.size(tile * 0.45f)) }
            if (item.tag != null) {
                Text(item.tag, style = AslTheme.type.micro.copy(fontSize = AslTheme.type.micro.fontSize * 0.75f), color = AslGradients.onEarnings,
                    modifier = Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).background(Brush.linearGradient(AslGradients.earnings), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
            }
        }
        Text(item.label, style = AslTheme.type.micro.copy(fontWeight = AslTheme.type.label.fontWeight), color = c.textPrimary, textAlign = TextAlign.Center, maxLines = 2)
    }
}

data class NavItem(val label: String, val icon: ImageVector)

/** Bottom navigation: Home · Reports · Earnings · Profile, with the gradient indicator on the active tab. */
@Composable
fun AslBottomNav(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = AslTheme.colors
    Column(modifier.fillMaxWidth().background(c.surface)) {
        Divider()
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 10.dp)) {
            items.forEachIndexed { i, item ->
                val on = i == selected
                Column(
                    Modifier.weight(1f).semantics { this.selected = on }.clickable(role = Role.Tab) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(Modifier.width(26.dp).height(3.dp).background(if (on) Brush.linearGradient(AslGradients.action) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)), RoundedCornerShape(2.dp)))
                    Icon(item.icon, null, tint = if (on) c.primary else c.textSecondary, modifier = Modifier.size(22.dp))
                    Text(item.label, style = AslTheme.type.micro, color = if (on) c.primary else c.textSecondary)
                }
            }
        }
    }
}
