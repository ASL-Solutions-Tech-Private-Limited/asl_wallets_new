package com.asl.wallet.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.asl.wallet.core.common.format.AmountInWords
import com.asl.wallet.core.common.format.Money
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.theme.AslGradients
import com.asl.wallet.core.designsystem.theme.AslShapes
import com.asl.wallet.core.designsystem.theme.AslTheme
import kotlinx.coroutines.launch
import java.math.BigDecimal
import kotlin.math.roundToInt

/** A flat card: the default container for data and forms. */
@Composable
fun AslCard(modifier: Modifier = Modifier, padding: androidx.compose.ui.unit.Dp = 14.dp, content: @Composable () -> Unit) {
    val c = AslTheme.colors
    Box(modifier.fillMaxWidth().background(c.surface, AslShapes.card).border(1.dp, c.outline, AslShapes.card).padding(padding)) { content() }
}

/** Gold chip used ONLY for money the retailer earned. */
@Composable
fun EarningsChip(text: String, modifier: Modifier = Modifier) {
    Text(
        "★ $text",
        style = AslTheme.type.micro,
        color = AslGradients.onEarnings,
        modifier = modifier.background(Brush.linearGradient(AslGradients.earnings), AslShapes.pill).padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/**
 * Wallet card: animated "night sky" gradient with a slow glint. The balance can be hidden.
 * In the app, auto-hide after 10 s is driven by the screen's ViewModel.
 */
@Composable
fun WalletCard(
    balance: BigDecimal,
    hidden: Boolean,
    onToggleHidden: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    earnedToday: String? = null,
    actions: List<Pair<String, () -> Unit>> = emptyList(),
    labels: WalletCardLabels = WalletCardLabels(),
) {
    val c = AslTheme.colors
    val t = rememberInfiniteTransition(label = "wallet")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000), RepeatMode.Reverse), label = "shift")
    val glint by t.animateFloat(-0.6f, 1.6f, infiniteRepeatable(tween(5000)), label = "glint")
    val formatted = Money.format(balance)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(16.dp, AslShapes.hero, ambientColor = c.primary, spotColor = c.brandNavy)
            .clip(AslShapes.hero)
            .drawWithContent {
                val w = size.width; val h = size.height
                drawRect(Brush.linearGradient(c.heroGradient, start = Offset(-w * shift * 0.6f, 0f), end = Offset(w * (1.4f - shift * 0.4f), h)))
                drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                    start = Offset(w * glint - w * 0.25f, 0f), end = Offset(w * glint + w * 0.25f, h)))
                drawContent()
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(labels.title, style = AslTheme.type.caption, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
            Row(
                Modifier.clickableNoRipple(onToggleHidden).semantics { role = Role.Button; contentDescription = if (hidden) labels.showBalance else labels.hideBalance },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (hidden) AslIcons.Eye else AslIcons.EyeOff, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (hidden) labels.show else labels.hide, style = AslTheme.type.caption, color = Color.White.copy(alpha = 0.85f))
            }
        }
        Text(
            if (hidden) "₹ ••••••" else formatted,
            style = AslTheme.type.amountWallet,
            color = Color.White,
            modifier = Modifier.semantics { contentDescription = if (hidden) labels.balanceHidden else labels.balanceIs(formatted) },
        )
        if (subtitle != null || earnedToday != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(subtitle.orEmpty(), style = AslTheme.type.caption, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                if (earnedToday != null) EarningsChip(earnedToday)
            }
        }
        if (actions.isNotEmpty()) {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { (label, action) ->
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.14f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                            .clickableNoRipple(action).padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, style = AslTheme.type.label, color = Color.White) }
                }
            }
        }
    }
}

/** Text on [WalletCard]; features pass translated strings. English defaults keep previews and the showcase simple. */
data class WalletCardLabels(
    val title: String = "Wallet balance",
    val show: String = "Show",
    val hide: String = "Hide",
    val showBalance: String = "Show balance",
    val hideBalance: String = "Hide balance",
    val balanceHidden: String = "Balance hidden",
    val balanceIs: (String) -> String = { "Balance $it" },
)

/** Big amount in gradient text, the amount in words underneath, and optional quick-amount chips. */
@Composable
fun AmountCard(
    amount: BigDecimal?,
    modifier: Modifier = Modifier,
    quickAmounts: List<Long> = emptyList(),
    selectedQuick: Long? = null,
    onQuickSelect: (Long) -> Unit = {},
) {
    val c = AslTheme.colors
    AslCard(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val text = amount?.let { Money.format(it, showPaise = false) } ?: "₹ 0"
            Text(
                text,
                style = AslTheme.type.amountDisplay.merge(TextStyle(brush = Brush.linearGradient(AslGradients.action))),
                textAlign = TextAlign.Center,
            )
            Text(amount?.let { AmountInWords.english(it) } ?: "Enter an amount", style = AslTheme.type.caption, color = c.textSecondary, textAlign = TextAlign.Center)
            if (quickAmounts.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                ChoiceChips(
                    options = quickAmounts.map { Money.format(it, withSymbol = false) },
                    selected = quickAmounts.indexOf(selectedQuick).takeIf { it >= 0 },
                    onSelect = { onQuickSelect(quickAmounts[it]) },
                )
            }
        }
    }
}

/** Label/value rows (charges, receipt details). A row with [total] = true gets a dashed divider and bold text. */
data class KeyValue(val key: String, val value: String, val total: Boolean = false, val valueColor: Color? = null)

@Composable
fun KeyValueList(rows: List<KeyValue>, modifier: Modifier = Modifier) {
    val c = AslTheme.colors
    // The label takes only its own width, at most ~40% (never squeezed letter by letter);
    // the value gets everything else, right-aligned to the card edge, and wraps when long.
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxKey = maxWidth * 0.4f
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { r ->
                if (r.total) Box(Modifier.fillMaxWidth().height(1.dp).background(c.outline))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(r.key, style = if (r.total) AslTheme.type.label else AslTheme.type.body, color = if (r.total) c.textPrimary else c.textSecondary,
                        modifier = Modifier.widthIn(max = maxKey))
                    Text(r.value, style = if (r.total) AslTheme.type.label else AslTheme.type.body.copy(fontWeight = AslTheme.type.label.fontWeight),
                        color = r.valueColor ?: c.textPrimary, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Slide to confirm money. One completed slide = one call to [onConfirmed]; it then locks until [resetKey] changes.
 * Releasing before the end springs the knob back. Haptics: a tick while dragging, a confirm at the end.
 * Accessibility: TalkBack users can double-tap ("Activate to …").
 */
@Composable
fun SlideToConfirm(
    text: String,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    resetKey: Any? = null,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var done by remember(resetKey) { mutableStateOf(false) }
    val offset = remember(resetKey) { Animatable(0f) }
    val knob = 48.dp
    val pad = 5.dp
    val nudge = rememberInfiniteTransition(label = "nudge")
    val hint by nudge.animateFloat(0f, 18f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "hint")

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(if (enabled) AslGradients.action else listOf(Color.Gray, Color.Gray)))
            .semantics {
                contentDescription = text
                role = Role.Button
                onClick(label = text) { if (enabled && !done) { done = true; onConfirmed() }; true }
            },
    ) {
        val maxPx = with(density) { (maxWidth - knob - pad * 2).toPx() }
        val progress = if (maxPx > 0) offset.value / maxPx else 0f
        Box(Modifier.fillMaxHeight().width(with(density) { (offset.value).toDp() } + knob + pad).background(Color.White.copy(alpha = 0.18f)))
        Text(
            if (done) "Sending…" else text,
            style = AslTheme.type.label.copy(fontSize = AslTheme.type.body.fontSize),
            color = Color.White.copy(alpha = 1f - progress * 0.6f),
            modifier = Modifier.align(Alignment.Center).padding(start = knob),
        )
        val idleHint = if (!done && enabled && offset.value == 0f) hint else 0f
        Box(
            Modifier
                .offset { IntOffset((offset.value + idleHint + with(density) { pad.toPx() }).roundToInt(), with(density) { pad.toPx() }.roundToInt()) }
                .size(knob)
                .shadow(6.dp, RoundedCornerShape(14.dp))
                .background(Color.White, RoundedCornerShape(14.dp))
                .pointerInput(enabled, done, maxPx) {
                    if (!enabled || done) return@pointerInput
                    var lastTick = 0
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offset.value >= maxPx * 0.92f) {
                                    offset.animateTo(maxPx, spring())
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    done = true
                                    onConfirmed()
                                } else {
                                    offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                }
                            }
                        },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, maxPx)) }
                            val tick = (offset.value / (maxPx / 6f)).toInt()
                            if (tick != lastTick) { lastTick = tick; haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (done) AslIcons.Check else AslIcons.Send, contentDescription = null, tint = AslTheme.colors.primary.takeUnless { AslTheme.colors.isDark } ?: Color(0xFF1565C0), modifier = Modifier.size(22.dp))
        }
    }
}
