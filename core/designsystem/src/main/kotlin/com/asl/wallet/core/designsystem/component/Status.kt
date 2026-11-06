package com.asl.wallet.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.theme.AslGradients
import com.asl.wallet.core.designsystem.theme.AslShapes
import com.asl.wallet.core.designsystem.theme.AslTheme

/** Transaction / item status. Always shown with colour + dot + word, never colour alone. */
enum class Status { Success, Pending, Failed, Info, Neutral }

@Composable
fun StatusPill(text: String, status: Status, modifier: Modifier = Modifier) {
    val c = AslTheme.colors
    val (fg, bg) = when (status) {
        Status.Success -> c.success to c.successBg
        Status.Pending -> c.pending to c.pendingBg
        Status.Failed -> c.error to c.errorBg
        Status.Info -> c.primary to c.tint
        Status.Neutral -> c.textSecondary to c.outline
    }
    Row(
        modifier.background(bg, AslShapes.pill).padding(horizontal = 9.dp, vertical = 4.dp).semantics { contentDescription = "Status: $text" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (status != Status.Info && status != Status.Neutral) {
            Box(contentAlignment = Alignment.Center) {
                if (status == Status.Pending) PulseRing(fg)
                Box(Modifier.size(7.dp).background(fg, CircleShape))
            }
        }
        Text(text, style = AslTheme.type.micro, color = fg, maxLines = 1)
    }
}

/** The "still working" ring around pending dots. */
@Composable
private fun PulseRing(color: Color) {
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(0.8f, 1.8f, infiniteRepeatable(tween(1800), RepeatMode.Restart), label = "s")
    val a by t.animateFloat(0.9f, 0f, infiniteRepeatable(tween(1800), RepeatMode.Restart), label = "a")
    Box(Modifier.size(7.dp).scale(s).drawBehind { drawCircle(color.copy(alpha = a), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())) })
}

enum class AlertKind { Success, Pending, Error, Info }

@Composable
fun AlertBanner(kind: AlertKind, title: String, modifier: Modifier = Modifier, message: String? = null, icon: ImageVector? = null) {
    val c = AslTheme.colors
    val (fg, bg, defaultIcon) = when (kind) {
        AlertKind.Success -> Triple(c.success, c.successBg, AslIcons.Check)
        AlertKind.Pending -> Triple(c.pending, c.pendingBg, AslIcons.Clock)
        AlertKind.Error -> Triple(c.error, c.errorBg, AslIcons.Alert)
        AlertKind.Info -> Triple(c.primary, c.tint, AslIcons.Info)
    }
    Row(
        modifier.fillMaxWidth().background(bg, RoundedCornerShape(15.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon ?: defaultIcon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = AslTheme.type.label.copy(fontSize = AslTheme.type.body.fontSize), color = c.textPrimary)
            if (message != null) Text(message, style = AslTheme.type.caption, color = c.textSecondary)
        }
    }
}

/** Round gradient icon used at the top of state screens and dialogs. */
@Composable
fun BigIcon(icon: ImageVector, colors: List<Color>, modifier: Modifier = Modifier, size: Dp = 68.dp) {
    Box(modifier.size(size).background(Brush.linearGradient(colors), RoundedCornerShape(size / 3)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.46f))
    }
}

/**
 * Full-screen state: offline, empty, session expired, update required, unsafe phone, location needed, outside hours.
 * Always one clear next step in [actions].
 */
@Composable
fun StateView(
    icon: ImageVector,
    iconColors: List<Color>,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    val c = AslTheme.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BigIcon(icon, iconColors)
        Text(title, style = AslTheme.type.headline, color = c.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Text(message, style = AslTheme.type.body, color = c.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

object StatePresets {
    val offlineColors = AslGradients.Service.Neutral.colors
    val emptyColors = AslGradients.action
    val warnColors = AslGradients.pending
    val errorColors = AslGradients.failed
    val okColors = AslGradients.success
}

/** Shimmer placeholder in the real layout's shape. Use instead of a blank spinner. */
fun Modifier.shimmer(shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)): Modifier = composed {
    val c = AslTheme.colors
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "x")
    val base = c.outline
    val light = if (c.isDark) c.surface else Color.White
    this.drawBehind {
        val w = size.width
        drawRect(
            Brush.linearGradient(listOf(base, light.copy(alpha = 0.9f), base), start = Offset(w * x - w, 0f), end = Offset(w * x, 0f)),
        )
    }.then(Modifier.background(Color.Transparent, shape))
}

@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).shimmer())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.fillMaxWidth(0.75f).height(11.dp).clip(RoundedCornerShape(6.dp)).shimmer())
            Box(Modifier.fillMaxWidth(0.45f).height(10.dp).clip(RoundedCornerShape(6.dp)).shimmer())
        }
        Box(Modifier.size(width = 56.dp, height = 14.dp).clip(RoundedCornerShape(6.dp)).shimmer())
    }
}

