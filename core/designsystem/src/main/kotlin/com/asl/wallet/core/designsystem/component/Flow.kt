package com.asl.wallet.core.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.asl.wallet.core.designsystem.theme.AslGradients
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.theme.AslTheme

/**
 * Money in progress, as designed (5B.16 / 5.7 / 4.23): fills the screen under its top bar, centred on a soft tint —
 * an orb with two spinning rings around a glowing gradient circle, a bold title, a moving bar, and the steps
 * (✓ done, pulsing dot = now, dashed = next). Callers block Back (BackHandler) and screenshots (SecureScreen).
 */
@Composable
fun ProcessingScreen(
    barTitle: String,
    title: String,
    subtitle: String,
    steps: List<String>,
    current: Int,
    colors: List<Color> = AslTheme.colors.heroGradient,
    icon: ImageVector = AslIcons.Wallet,
) {
    val c = AslTheme.colors
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        AslTopBar(barTitle)
        ProcessingView(title, subtitle, steps, current, colors, icon, Modifier.weight(1f).fillMaxWidth().navigationBarsPadding())
    }
}

@Composable
fun ProcessingView(
    title: String,
    subtitle: String,
    steps: List<String>,
    current: Int,
    colors: List<Color> = AslTheme.colors.heroGradient,
    icon: ImageVector = AslIcons.Wallet,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val c = AslTheme.colors
    Box(modifier.background(Brush.radialGradient(listOf(c.tint, c.background), radius = 900f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Orb(colors, icon)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = AslTheme.type.headline.copy(fontWeight = FontWeight.Black), color = c.textPrimary, textAlign = TextAlign.Center)
                Text(subtitle, style = AslTheme.type.body, color = c.textSecondary, textAlign = TextAlign.Center)
            }
            LinearProgressIndicator(
                Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)),
                color = colors.first(), trackColor = c.outline, strokeCap = StrokeCap.Round,
            )
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                steps.forEachIndexed { i, s -> StepRow(s, done = i < current, now = i == current) }
            }
        }
    }
}

@Composable
private fun Orb(colors: List<Color>, icon: ImageVector) {
    val t = rememberInfiniteTransition(label = "orb")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "spin")
    val spin2 by t.animateFloat(360f, 0f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "spin2")
    val breathe by t.animateFloat(0.94f, 1.04f, infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val accent = Color(0xFF22D3EE)
    Box(Modifier.size(140.dp).semantics { contentDescription = "" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 3.dp.toPx()
            rotate(spin) { drawArc(Brush.sweepGradient(colors + colors.first()), -90f, 180f, false, style = Stroke(w, cap = StrokeCap.Round)) }
            val inset = 16.dp.toPx()
            rotate(spin2) {
                drawArc(accent, 30f, 120f, false, topLeft = Offset(inset, inset), size = Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(w, cap = StrokeCap.Round))
                drawArc(accent, 210f, 60f, false, topLeft = Offset(inset, inset), size = Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        Box(
            Modifier.size(78.dp).graphicsLayer { scaleX = breathe; scaleY = breathe }
                .shadow(24.dp, CircleShape, ambientColor = colors.last(), spotColor = colors.last())
                .clip(CircleShape).background(Brush.linearGradient(colors)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(34.dp)) }
    }
}

@Composable
private fun StepRow(text: String, done: Boolean, now: Boolean) {
    val c = AslTheme.colors
    val t = rememberInfiniteTransition(label = "step")
    val pulse by t.animateFloat(0.6f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                done -> Box(Modifier.fillMaxSize().clip(CircleShape).background(Brush.linearGradient(AslGradients.success)), contentAlignment = Alignment.Center) {
                    Icon(AslIcons.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                now -> {
                    Box(Modifier.fillMaxSize().border(2.dp, c.primary, CircleShape))
                    Box(Modifier.size(9.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }.clip(CircleShape).background(c.primary))
                }
                else -> Canvas(Modifier.fillMaxSize()) {
                    drawCircle(c.outline, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))))
                }
            }
        }
        Text(
            text, style = AslTheme.type.bodyStrong.copy(fontWeight = if (now) FontWeight.ExtraBold else FontWeight.SemiBold),
            color = if (done || now) c.textPrimary else c.textSecondary,
        )
    }
}

enum class ResultKind { Ok, Warn, Bad }

/** Result header (big icon, title, line) and its detail rows: payment done / pending / failed. */
@Composable
fun ResultView(kind: ResultKind, title: String, subtitle: String, rows: List<KeyValue>) {
    val c = AslTheme.colors
    val (icon, colors) = when (kind) {
        ResultKind.Ok -> AslIcons.Check to StatePresets.okColors
        ResultKind.Warn -> AslIcons.Clock to StatePresets.warnColors
        ResultKind.Bad -> AslIcons.Close to StatePresets.errorColors
    }
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BigIcon(icon, colors, size = 72.dp)
        Text(title, style = AslTheme.type.headline, color = c.textPrimary, textAlign = TextAlign.Center)
        Text(subtitle, style = AslTheme.type.body, color = c.textSecondary, textAlign = TextAlign.Center)
    }
    if (rows.isNotEmpty()) AslCard { KeyValueList(rows) }
}
