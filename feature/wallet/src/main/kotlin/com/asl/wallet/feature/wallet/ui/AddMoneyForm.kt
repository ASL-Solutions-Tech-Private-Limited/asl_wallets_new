package com.asl.wallet.feature.wallet.ui

import com.asl.wallet.core.designsystem.theme.accentFill
import com.asl.wallet.core.designsystem.component.rememberImeActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.asl.wallet.core.common.format.AmountInWords
import com.asl.wallet.core.common.format.Money
import com.asl.wallet.core.designsystem.R as DS
import com.asl.wallet.core.designsystem.component.WaveBand
import com.asl.wallet.core.designsystem.component.popIn
import com.asl.wallet.core.designsystem.component.pressScale
import com.asl.wallet.core.designsystem.component.reduceMotion
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.theme.AslTheme
import com.asl.wallet.feature.wallet.R
import com.asl.wallet.feature.wallet.domain.AddMoneyInfo
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Add Money form (classic look, 9 Nov 2026): the current balance counting up on a blue card, the balance after the
 * top-up sliding in, a big amount you type straight into, a limit meter, quick amounts and the PayU methods.
 */
@Composable
internal fun AddMoneyForm(
    info: AddMoneyInfo,
    amountText: String,
    amount: BigDecimal?,
    error: String?,
    onAmount: (String) -> Unit,
    onQuick: (Long) -> Unit,
) {
    val valid = amount != null && error == null && amount >= info.limits.min && amount <= info.limits.max
    BalanceHero(info.balance, amount.takeIf { valid }, Modifier.popIn(0, rise = 16.dp))
    AmountEntry(amountText, amount, error, info.limits, onAmount, Modifier.popIn(120, rise = 16.dp))
    QuickAmounts(info.limits.quickAmounts(), amount, onQuick, Modifier.popIn(220, rise = 16.dp))
    PaymentMethods(Modifier.popIn(300, rise = 16.dp))
}

// ---------------------------------------------------------------- balance card

/**
 * The blue balance card. [change] slides in the balance after it: added for a top-up, taken away for a payout
 * ([deduct]). Labels default to Add Money's.
 */
@Composable
internal fun BalanceHero(
    balance: BigDecimal?,
    change: BigDecimal?,
    modifier: Modifier,
    deduct: Boolean = false,
    title: String = stringResource(R.string.w_am_current),
    afterLabel: String = stringResource(R.string.w_am_after),
) {
    val adding = change
    val c = AslTheme.colors
    val shape = RoundedCornerShape(topStart = 6.dp, topEnd = 30.dp, bottomEnd = 6.dp, bottomStart = 30.dp)
    Box(modifier.fillMaxWidth().shadow(8.dp, shape, spotColor = Color(0xFF1976D2)).clip(shape).background(Brush.linearGradient(c.headerGradient))) {
        WaveBand(Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Image(painterResource(DS.drawable.classic_ic_wallet_menu), null, Modifier.size(22.dp))
                }
                Text(title, style = AslTheme.type.label, color = Color.White.copy(alpha = 0.9f))
            }
            Text(balance?.let { Money.format(countUp(it)) } ?: "₹ —", style = AslTheme.type.amountWallet, color = Color.White)
            AnimatedVisibility(adding != null && balance != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                val add = adding ?: BigDecimal.ZERO
                Row(
                    Modifier.padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(afterLabel, style = AslTheme.type.caption, color = Color.White.copy(alpha = 0.85f))
                    Text("→", style = AslTheme.type.label, color = Color.White)
                    Text(Money.format(countUp(if (deduct) (balance ?: BigDecimal.ZERO) - add else (balance ?: BigDecimal.ZERO) + add)), style = AslTheme.type.bodyStrong, color = Color.White)
                    Spacer(Modifier.weight(1f, fill = false))
                    Text(
                        (if (deduct) "− " else "+ ") + Money.format(add, showPaise = false), style = AslTheme.type.micro,
                        color = if (deduct) Color(0xFF4A0B0B) else Color(0xFF0B3D1C),
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (deduct) Color(0xFFFFB4A8) else Color(0xFF7CE0A3)).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

/** Glides to [target] (counts up from 0 the first time). */
@Composable
private fun countUp(target: BigDecimal): BigDecimal {
    val still = reduceMotion()
    val anim = remember { Animatable(if (still) target.toFloat() else 0f) }
    LaunchedEffect(target) { if (still) anim.snapTo(target.toFloat()) else anim.animateTo(target.toFloat(), tween(700, easing = FastOutSlowInEasing)) }
    return if (anim.value == target.toFloat()) target else BigDecimal(anim.value.toDouble()).setScale(2, RoundingMode.HALF_UP)
}

// ---------------------------------------------------------------- amount

/** The big amount you type straight into, with the amount in words and the limit bar. [ask] is the question above it. */
@Composable
internal fun AmountEntry(
    text: String,
    amount: BigDecimal?,
    error: String?,
    limits: com.asl.wallet.feature.wallet.domain.Limits,
    onAmount: (String) -> Unit,
    modifier: Modifier,
    ask: String = stringResource(R.string.w_am_ask),
    autoFocus: Boolean = true,
) {
    val c = AslTheme.colors
    val still = reduceMotion()
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (autoFocus) runCatching { focus.requestFocus() } }

    // A small bump each time the amount changes; a shake when it's out of limits.
    val bump = remember { Animatable(1f) }
    LaunchedEffect(text) { if (!still && text.isNotEmpty()) { bump.snapTo(1.06f); bump.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f)) } }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(error) { if (error != null && !still) for (v in listOf(10f, -8f, 6f, -4f, 0f)) shake.animateTo(v, tween(45)) }

    val ring by animateColorAsState(if (error != null) c.error else if (focused) c.primaryEnd else c.outline, label = "ring")
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.fillMaxWidth().graphicsLayer { translationX = shake.value * density }
            .shadow(if (focused) 6.dp else 1.dp, shape).clip(shape).background(c.surface).border(if (focused || error != null) 2.dp else 1.dp, ring, shape)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(ask, style = AslTheme.type.label, color = c.textSecondary)
        val desc = stringResource(R.string.w_am_amount_desc)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.graphicsLayer { scaleX = bump.value; scaleY = bump.value }) {
            Text("₹", style = AslTheme.type.amountDisplay.copy(fontSize = AslTheme.type.amountDisplay.fontSize * 1.15f), color = if (text.isEmpty()) c.outline else c.primary)
            Spacer(Modifier.width(6.dp))
            BasicTextField(
                value = text, onValueChange = onAmount, singleLine = true,
                textStyle = AslTheme.type.amountDisplay.copy(fontSize = AslTheme.type.amountDisplay.fontSize * 1.15f, color = c.primary, textAlign = TextAlign.Start),
                cursorBrush = SolidColor(c.primaryEnd),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = rememberImeActions(ImeAction.Done),
                modifier = Modifier.widthIn(min = 28.dp).width(IntrinsicSize.Min).focusRequester(focus).onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = desc },
                decorationBox = { inner ->
                    Box { if (text.isEmpty()) Text("0", style = AslTheme.type.amountDisplay.copy(fontSize = AslTheme.type.amountDisplay.fontSize * 1.15f), color = c.outline); inner() }
                },
            )
        }
        Text(
            amount?.takeIf { it.signum() > 0 }?.let { AmountInWords.english(it) } ?: " ",
            style = AslTheme.type.caption, color = c.textSecondary, textAlign = TextAlign.Center,
        )
        LimitMeter(amount, limits, error != null)
        AnimatedVisibility(error != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(AslIcons.Alert, null, tint = c.error, modifier = Modifier.size(14.dp))
                Text(error.orEmpty(), style = AslTheme.type.caption, color = c.error)
            }
        }
    }
}

/** A bar between the min and max: fills to the amount, green inside the limits and red outside. */
@Composable
private fun LimitMeter(amount: BigDecimal?, limits: com.asl.wallet.feature.wallet.domain.Limits, error: Boolean) {
    val c = AslTheme.colors
    val lo = limits.min; val hi = limits.max
    val target = when {
        amount == null || amount.signum() <= 0 -> 0f
        hi <= lo -> 1f
        else -> amount.toFloat().coerceIn(0f, hi.toFloat()) / hi.toFloat()
    }
    val fill by animateFloatAsState(target, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow), label = "fill")
    val inRange = amount != null && amount >= lo && amount <= hi
    val color by animateColorAsState(if (error || (amount != null && amount.signum() > 0 && !inRange)) c.error else if (inRange) c.success else c.outline, label = "meter")
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(c.tint)) {
            Box(Modifier.fillMaxWidth(fill).height(8.dp).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color))))
            // Where the minimum sits on the bar.
            val minAt = if (hi.signum() > 0) (lo.toFloat() / hi.toFloat()).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth(minAt).height(8.dp)) { Box(Modifier.align(Alignment.CenterEnd).width(2.dp).height(8.dp).background(c.primary.copy(alpha = 0.5f))) }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.w_am_min, Money.format(lo, showPaise = false)), style = AslTheme.type.micro, color = c.textSecondary)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.w_am_max, Money.format(hi, showPaise = false)), style = AslTheme.type.micro, color = c.textSecondary)
        }
    }
}

// ---------------------------------------------------------------- quick amounts and methods

@Composable
internal fun QuickAmounts(options: List<Long>, amount: BigDecimal?, onQuick: (Long) -> Unit, modifier: Modifier, title: String = stringResource(R.string.w_am_quick), plus: Boolean = true) {
    if (options.isEmpty()) return
    val c = AslTheme.colors
    val haptic = LocalHapticFeedback.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = AslTheme.type.label, color = c.textSecondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEach { v ->
                val on = amount != null && amount.compareTo(BigDecimal(v)) == 0
                val interaction = remember { MutableInteractionSource() }
                val bg by animateColorAsState(if (on) c.accentFill else c.surface, label = "chip")
                val fg by animateColorAsState(if (on) Color.White else c.primary, label = "chipText")
                val lift by animateFloatAsState(if (on) 1.06f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "lift")
                Box(
                    Modifier.weight(1f).height(48.dp).graphicsLayer { scaleX = lift; scaleY = lift }.pressScale(interaction, 0.92f)
                        .shadow(if (on) 6.dp else 0.dp, RoundedCornerShape(14.dp), spotColor = Color(0xFF1976D2))
                        .clip(RoundedCornerShape(14.dp)).background(bg).border(1.dp, if (on) c.primary else c.outline, RoundedCornerShape(14.dp))
                        .clickable(interaction, indication = null) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onQuick(v) },
                    contentAlignment = Alignment.Center,
                ) { Text((if (plus) "+ " else "") + Money.format(BigDecimal(v), showPaise = false), style = AslTheme.type.bodyStrong.copy(fontSize = AslTheme.type.body.fontSize), color = fg) }
            }
        }
    }
}

@Composable
private fun PaymentMethods(modifier: Modifier) {
    val c = AslTheme.colors
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(AslIcons.Upi to R.string.w_am_upi, AslIcons.Wallet to R.string.w_am_cards, AslIcons.Bank to R.string.w_am_netbanking).forEach { (icon, label) ->
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(c.tint).padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(icon, null, tint = c.primary, modifier = Modifier.size(16.dp))
                    Text(stringResource(label), style = AslTheme.type.micro, color = c.primary)
                }
            }
        }
    }
}
