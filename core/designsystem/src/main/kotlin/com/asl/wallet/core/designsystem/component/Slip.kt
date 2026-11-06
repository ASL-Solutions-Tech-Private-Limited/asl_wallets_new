package com.asl.wallet.core.designsystem.component

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asl.wallet.core.designsystem.theme.AslTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class SlipColors(val paper: Color, val ink: Color, val soft: Color, val tear: Color)

/** Paper stays paper: the same warm slip in light and dark themes. */
private val Paper = SlipColors(paper = Color(0xFFFFFEFA), ink = Color(0xFF111827), soft = Color(0xFF6B7280), tear = Color(0xFFC9CCD6))
private val Mono = FontFamily.Monospace

/** A paper slip with a zig-zag torn bottom edge. */
private class TornEdgeShape(private val tooth: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = Path()
        p.moveTo(0f, 0f); p.lineTo(size.width, 0f); p.lineTo(size.width, size.height - tooth)
        val n = (size.width / (tooth * 2)).toInt().coerceAtLeast(1)
        val step = size.width / n
        var x = size.width
        repeat(n) {
            p.lineTo(x - step / 2, size.height)
            x -= step
            p.lineTo(x, size.height - tooth)
        }
        p.close()
        return Outline.Generic(p)
    }
}

/** "Remove animations" (Settings → Accessibility) sets the animator scale to 0. */
@Composable
private fun animationsOff(): Boolean {
    val context = LocalContext.current
    return remember { runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false) }
}

/**
 * The universal customer receipt, drawn as a printed paper slip. The retailer's commission must NEVER be in [rows].
 *
 * With [animate] the slip feeds out of a printer slot in short steps (light haptic ticks), then the status is
 * stamped, then [actions] slide up. It plays once per receipt (survives rotation) and is skipped when the phone's
 * animations are off. Without [animate] (e.g. opened from Reports) everything shows at once.
 */
@Composable
fun ReceiptCard(
    shop: ShopInfo,
    title: String,
    amount: String,
    status: Status,
    rows: List<KeyValue>,
    modifier: Modifier = Modifier,
    footer: String = "Computer-generated receipt · no signature needed",
    /** Translated status word; defaults to English. */
    statusLabel: String? = null,
    /** "Five thousand rupees" under the amount. */
    amountWords: String? = null,
    /** Seeds the decorative barcode (it holds no data). */
    barcodeSeed: String? = null,
    animate: Boolean = false,
    thanks: String = "Thank you!",
    actions: (@Composable () -> Unit)? = null,
) {
    val word = statusLabel ?: when (status) { Status.Success -> "Success"; Status.Pending -> "Pending"; Status.Failed -> "Failed"; else -> "" }
    val stampColor = when (status) { Status.Success -> Color(0xFF059669); Status.Pending -> Color(0xFFD97706); Status.Failed -> Color(0xFFDC2626); else -> Color(0xFF6366F1) }
    val skip = !animate || animationsOff()
    var printed by rememberSaveable { mutableStateOf(skip) }
    val feed = remember { Animatable(if (printed) 0f else -1f) }
    val stampScale = remember { Animatable(if (printed) 1f else 2.2f) }
    val stampAlpha = remember { Animatable(if (printed) 0.9f else 0f) }
    var feeding by remember { mutableStateOf(!printed) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        if (printed) return@LaunchedEffect
        delay(250)
        // Six short jerks, like paper out of a thermal printer.
        val steps = listOf(-0.86f, -0.72f, -0.54f, -0.36f, -0.16f, 0f)
        steps.forEach { target ->
            feed.animateTo(target, tween(170, easing = FastOutSlowInEasing))
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            delay(70)
        }
        feeding = false
        delay(120)
        launch { stampAlpha.animateTo(0.9f, tween(160)) }
        stampScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(120)
        printed = true
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth()) {
            PrinterSlot(feeding)
            // The slip slides down from behind the slot; clipped at the slot so it looks fed through it.
            Box(Modifier.fillMaxWidth().padding(top = 13.dp).clipToBounds().padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
                Slip(
                    shop, title, amount, amountWords, rows, footer, thanks, barcodeSeed ?: (title + amount),
                    word, stampColor, stampScale.value, stampAlpha.value,
                    Modifier.layout { m, c ->
                        val p = m.measure(c)
                        layout(p.width, p.height) { p.place(0, (p.height * feed.value).roundToInt()) }
                    },
                )
            }
        }
        if (actions != null) AnimatedVisibility(printed, enter = fadeIn(tween(250)) + slideInVertically(tween(300)) { it / 2 }) { actions() }
    }
}

@Composable
private fun PrinterSlot(busy: Boolean) {
    val blink = rememberInfiniteTransition(label = "led")
    val a by blink.animateFloat(1f, 0.2f, infiniteRepeatable(tween(180, easing = LinearEasing), RepeatMode.Reverse), label = "led")
    Box(
        Modifier.fillMaxWidth().height(26.dp).shadow(6.dp, RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1E2547), Color(0xFF2B3466))), RoundedCornerShape(14.dp)),
    ) {
        Box(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 14.dp).height(5.dp).background(Color(0xFF0A0F24), RoundedCornerShape(3.dp)))
        Box(Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 10.dp).size(6.dp).alpha(if (busy) a else 1f).background(Color(0xFF34D399), CircleShape))
    }
}

@Composable
private fun Slip(
    shop: ShopInfo, title: String, amount: String, words: String?, rows: List<KeyValue>, footer: String, thanks: String, seed: String,
    word: String, stampColor: Color, stampScale: Float, stampAlpha: Float, modifier: Modifier,
) {
    val shape = remember { TornEdgeShape(14f) }
    val spoken = buildString {
        append(title).append(", ").append(word).append(", ").append(amount).append(". ")
        rows.forEach { append(it.key).append(": ").append(it.value).append(". ") }
        append(shop.name)
    }
    Box(
        modifier.fillMaxWidth().shadow(8.dp, shape).background(Paper.paper, shape)
            .drawBehind {
                // faint thermal-paper lines
                var y = 0f
                val gap = 3.dp.toPx()
                while (y < size.height) { drawLine(Color.Black.copy(alpha = 0.018f), Offset(0f, y), Offset(size.width, y), 1f); y += gap }
            }
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp)) {
            // shop
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(32.dp).background(Brush.linearGradient(listOf(Color(0xFF1E1B4B), Color(0xFF6366F1))), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Text("ASL", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
                Text(listOf(shop.name, shop.id).filter { it.isNotBlank() }.joinToString(" · "), color = Paper.ink, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                if (shop.addressLine.isNotBlank()) Text(shop.addressLine, color = Paper.soft, fontSize = 10.5.sp, textAlign = TextAlign.Center)
            }
            TearLine()
            // service and amount
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title.uppercase(), color = Paper.soft, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp, textAlign = TextAlign.Center)
                Text(amount, color = Paper.ink, fontFamily = Mono, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 2.dp))
                if (!words.isNullOrBlank()) Text(words, color = Paper.soft, fontSize = 10.5.sp, textAlign = TextAlign.Center)
            }
            TearLine()
            // rows: label left, value right in printer type
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                rows.forEach { r ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(r.key, color = Paper.soft, fontSize = 11.5.sp, modifier = Modifier.weight(0.67f, fill = false))
                        Text(r.value, color = Paper.ink, fontFamily = Mono, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    }
                }
            }
            Barcode(seed, Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp).padding(horizontal = 6.dp).height(32.dp))
            Text(footer, color = Paper.soft, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(thanks, color = Paper.ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        // the inked status stamp, top-right corner
        if (word.isNotBlank()) Text(
            word.uppercase(), color = stampColor, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-10).dp, y = 22.dp).alpha(stampAlpha).scale(stampScale).rotate(-12f)
                .border(2.dp, stampColor.copy(alpha = 0.85f), RoundedCornerShape(7.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun TearLine() {
    Canvas(Modifier.fillMaxWidth().padding(vertical = 11.dp).height(1.5.dp)) {
        drawLine(Paper.tear, Offset(0f, 0f), Offset(size.width, 0f), size.height, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
    }
}

/** Decorative bars from a hash of [seed]; not a real barcode and carries no data. */
@Composable
private fun Barcode(seed: String, modifier: Modifier) {
    val widths = remember(seed) {
        var h = seed.hashCode().toLong() and 0xffffffffL
        List(48) { h = (h * 1103515245L + 12345L) and 0x7fffffffL; (h % 3 + 1).toInt() }
    }
    Canvas(modifier.alpha(0.85f)) {
        val unit = size.width / widths.sumOf { it + 1 }
        var x = 0f
        widths.forEach { w -> drawRect(Color(0xFF111111), Offset(x, 0f), Size(w * unit, size.height)); x += (w + 1) * unit }
    }
}
