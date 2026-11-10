package com.asl.wallet.feature.auth.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.asl.wallet.core.designsystem.R as DS
import com.asl.wallet.core.designsystem.component.StatusBarTone
import com.asl.wallet.core.designsystem.component.reduceMotion
import com.asl.wallet.core.designsystem.component.shine
import com.asl.wallet.core.designsystem.theme.AslTheme
import com.asl.wallet.feature.auth.R
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/* ---------------------------------------------------------------------------------------------
 * Classic sign-in look (branch ui/classic-look): the old app's navy → blue login with its big logo, white panel,
 * navy pill button and "Powered by" partners, plus light motion. All motion stops with "Remove animations".
 * ------------------------------------------------------------------------------------------- */

private val NAVY = Color(0xFF03274B)
private val BLUE = Color(0xFF1976D2)

/** Navy → blue page with slow soft lights and faint particles drifting up. */
@Composable
internal fun ClassicAuthBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    StatusBarTone.OnDarkHeader()
    val still = reduceMotion()
    val t = rememberInfiniteTransition(label = "bg")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9_000), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(12_000), RepeatMode.Reverse), label = "b")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(16_000, easing = LinearEasing)), label = "drift")
    val la = if (still) 0.5f else a
    val lb = if (still) 0.5f else b
    Box(modifier.background(Brush.verticalGradient(listOf(NAVY, Color(0xFF0C4DA2), BLUE)))) {
        Box(
            Modifier.offset(x = (-120 + 40 * la).dp, y = (-80 + 30 * la).dp).size(340.dp)
                .background(Brush.radialGradient(listOf(Color(0xFF1E88E5).copy(alpha = 0.55f), Color.Transparent))),
        )
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = (140 - 40 * lb).dp, y = (120 + 30 * lb).dp).size(300.dp)
                .background(Brush.radialGradient(listOf(Color(0xFF4FC3F7).copy(alpha = 0.30f), Color.Transparent))),
        )
        Particles(Modifier.fillMaxSize(), if (still) 0f else drift)
        content()
    }
}

private class Dot(val x: Float, val y: Float, val r: Float, val speed: Float, val phase: Float, val alpha: Float)

@Composable
private fun Particles(modifier: Modifier, progress: Float) {
    val dots = remember {
        val rnd = Random(7)
        List(22) { Dot(rnd.nextFloat(), rnd.nextFloat(), 1.2f + rnd.nextFloat() * 2.6f, 0.4f + rnd.nextFloat() * 0.9f, rnd.nextFloat(), 0.10f + rnd.nextFloat() * 0.25f) }
    }
    Canvas(modifier) {
        dots.forEach { d ->
            val y = ((d.y - progress * d.speed) % 1f + 1f) % 1f
            val x = d.x + 0.015f * sin(2 * PI * (progress * 2 + d.phase)).toFloat()
            drawCircle(Color.White.copy(alpha = d.alpha), radius = d.r * density, center = Offset(x * size.width, y * size.height))
        }
    }
}

/** The old app's ASL logo on a white disc: springs in, a soft halo pulses around it, a shine passes over it. */
@Composable
internal fun LogoHero(size: Dp, modifier: Modifier = Modifier) {
    val still = reduceMotion()
    val enter = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
    val t = rememberInfiniteTransition(label = "halo")
    val halo by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2_400, easing = FastOutSlowInEasing)), label = "h")
    Box(modifier.size(size * 1.35f), contentAlignment = Alignment.Center) {
        if (!still) {
            Box(
                Modifier.size(size).graphicsLayer { val s = 1f + 0.3f * halo; scaleX = s; scaleY = s; alpha = (1f - halo) * 0.5f }
                    .background(Color.White.copy(alpha = 0.35f), CircleShape),
            )
        }
        Box(
            Modifier.size(size).graphicsLayer { val s = 0.6f + 0.4f * enter.value; scaleX = s; scaleY = s; alpha = enter.value.coerceIn(0f, 1f) }
                .shadow(18.dp, CircleShape, spotColor = Color(0xFF64B5F6), ambientColor = Color(0xFF64B5F6))
                .clip(CircleShape).background(Color.White).shine(periodMs = 5_000, delayMs = 1_600, alpha = 0.6f)
                .padding(size * 0.08f),
        ) { Image(painterResource(DS.drawable.classic_ic_asl_logo), stringResource(R.string.auth_logo_desc), Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
    }
}

/** White panel with big rounded top corners that rises from the bottom (old auth_top_rounded_corner_bg). */
@Composable
internal fun AuthSheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val still = reduceMotion()
    val rise = remember { Animatable(if (still) 0f else 1f) }
    LaunchedEffect(Unit) { rise.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow)) }
    val px = with(LocalDensity.current) { 220.dp.toPx() }
    Column(
        modifier.fillMaxWidth().graphicsLayer { translationY = rise.value * px; alpha = 1f - rise.value * 0.6f }
            .background(AslTheme.colors.surface, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** Fades and rises in after [delayMs] (stagger the panel's rows). */
internal fun Modifier.riseIn(delayMs: Int): Modifier = composed {
    if (reduceMotion()) return@composed this
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(420, delayMillis = delayMs, easing = FastOutSlowInEasing)) }
    val dy = with(LocalDensity.current) { 18.dp.toPx() }
    graphicsLayer { alpha = p.value; translationY = (1f - p.value) * dy }
}

/** Shakes sideways once each time [trigger] changes to a new non-null value (a wrong password or code). */
internal fun Modifier.shakeOn(trigger: Any?): Modifier = composed {
    val x = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    val still = reduceMotion()
    LaunchedEffect(trigger) {
        if (trigger == null) return@LaunchedEffect
        haptic.performHapticFeedback(HapticFeedbackType.Reject)
        if (still) return@LaunchedEffect
        for (v in listOf(14f, -12f, 9f, -6f, 3f, 0f)) x.animateTo(v, tween(55))
    }
    graphicsLayer { translationX = x.value * density }
}

/**
 * The old app's round navy button with a blue shadow, at a comfortable width. It shines while ready, squashes when
 * pressed, and shows a spinner while working. Disabled: flat grey-blue.
 */
@Composable
internal fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "press")
    val haptic = LocalHapticFeedback.current
    val active = enabled && !loading
    val shape = RoundedCornerShape(50)
    Box(
        modifier.width(240.dp).height(54.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (enabled) Modifier.shadow(8.dp, shape, spotColor = BLUE, ambientColor = BLUE) else Modifier)
            .clip(shape)
            .background(if (enabled) Brush.horizontalGradient(listOf(NAVY, Color(0xFF0C4DA2))) else Brush.horizontalGradient(listOf(Color(0xFFB9C3D3), Color(0xFFB9C3D3))))
            .then(if (active) Modifier.shine(periodMs = 3_800, delayMs = 900, alpha = 0.3f) else Modifier)
            .clickable(interaction, indication = null, enabled = active, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick); onClick()
            }
            .semantics { contentDescription = text },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.5.dp)
        else Text(text, style = AslTheme.type.bodyStrong.copy(fontWeight = FontWeight.Bold), color = if (enabled) Color.White else Color(0xFF4F5B6E))
    }
}

/** "Powered by" and the partners' logos sliding slowly across, as on the old login. */
@Composable
internal fun PoweredBy(modifier: Modifier = Modifier) {
    val still = reduceMotion()
    val logos = listOf(R.drawable.auth_partner_hdfc, R.drawable.auth_partner_dbs, R.drawable.auth_partner_fino, R.drawable.auth_partner_idfc, R.drawable.auth_partner_bbps, R.drawable.auth_partner_npci)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.auth_powered_by), style = AslTheme.type.caption, color = AslTheme.colors.textSecondary)
        // The partner logos are made for white: in dark mode they run on a light strip.
        Row(
            Modifier.fillMaxWidth()
                .then(if (AslTheme.colors.isDark) Modifier.background(Color(0xFFF2F5FA)).padding(vertical = 6.dp) else Modifier)
                .then(if (still) Modifier else Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 600, velocity = 26.dp)),
            horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            (logos + logos).forEach { res ->
                Image(painterResource(res), null, Modifier.height(34.dp).width(64.dp), contentScale = ContentScale.Fit)
            }
        }
    }
}

/** Round white-on-blue back button for the sign-in screens. */
@Composable
internal fun GlassBack(onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { androidx.compose.material3.Icon(com.asl.wallet.core.designsystem.icon.AslIcons.Back, label, tint = Color.White, modifier = Modifier.size(20.dp)) }
}
