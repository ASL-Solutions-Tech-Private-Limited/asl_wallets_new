package com.asl.wallet.core.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asl.wallet.core.designsystem.theme.AslGradients
import com.asl.wallet.core.designsystem.theme.AslMotion
import com.asl.wallet.core.designsystem.theme.AslShapes
import com.asl.wallet.core.designsystem.theme.AslTheme

enum class AslButtonStyle { Primary, Outline, Danger }

/**
 * The one button used everywhere.
 * - Primary: action gradient with a light sweep (CRED-style). Danger: red gradient. Outline: flat.
 * - Pressing scales it to 96% with a light haptic tick.
 * - [loading] keeps the size, shows a spinner and blocks taps, so double taps can't fire twice.
 */
@Composable
fun AslButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AslButtonStyle = AslButtonStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
    icon: ImageVector? = null,
) {
    val c = AslTheme.colors
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(AslMotion.MICRO, easing = AslMotion.standard), label = "press")
    val clickable = enabled && !loading

    val bg: Modifier = when (style) {
        AslButtonStyle.Primary -> Modifier.background(Brush.linearGradient(AslGradients.action), AslShapes.button)
        AslButtonStyle.Danger -> Modifier.background(Brush.linearGradient(AslGradients.failed), AslShapes.button)
        AslButtonStyle.Outline -> Modifier
            .background(c.surface, AslShapes.button)
            .border(BorderStroke(1.5.dp, c.outline), AslShapes.button)
    }
    val content = if (style == AslButtonStyle.Outline) c.primary else Color.White

    Box(
        modifier = modifier
            .scale(scale)
            .height(if (compact) 44.dp else 52.dp)
            .clip(AslShapes.button)
            .then(bg)
            .then(if (style == AslButtonStyle.Primary && clickable) sweepModifier() else Modifier)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(
                enabled = clickable,
                role = Role.Button,
                interactionSource = interaction,
                indication = null,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = content, strokeWidth = 2.5.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                Text(text, style = if (compact) AslTheme.type.label else AslTheme.type.label.copy(fontSize = 15.sp), color = content)
            }
        }
    }
}

/** The slow light sweep across primary actions. */
@Composable
internal fun sweepModifier(): Modifier {
    val t = rememberInfiniteTransition(label = "sweep")
    val x by t.animateFloat(
        initialValue = -0.6f, targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "sweepX",
    )
    return Modifier.drawWithContent {
        drawContent()
        val w = size.width
        val cx = w * x
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.30f), Color.Transparent),
                start = Offset(cx - w * 0.2f, 0f), end = Offset(cx + w * 0.2f, size.height),
            ),
        )
    }
}

/** Full-width helper. */
@Composable
fun AslPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false, icon: ImageVector? = null) =
    AslButton(text, onClick, modifier.fillMaxWidth(), AslButtonStyle.Primary, enabled, loading, icon = icon)
