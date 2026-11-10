package com.asl.wallet.core.designsystem.component

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlin.math.PI
import kotlin.math.sin

/* ---------------------------------------------------------------------------------------------
 * Classic look (branch ui/classic-look): the old ASL Wallet app's look, with light motion.
 * Every effect here is decoration only and switches off when the phone's "Remove animations" is on.
 * ------------------------------------------------------------------------------------------- */

/** True when the phone's "Remove animations" setting is on. */
@Composable
fun reduceMotion(): Boolean {
    val context = LocalContext.current
    return remember { runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false) }
}

/**
 * Screens with a blue header need white status-bar icons. Each one that is on screen registers here;
 * the app reads [darkHeaders] and sets the icons. A counter (not a flag) so crossfading screens can't undo each other.
 */
object StatusBarTone {
    var darkHeaders by mutableIntStateOf(0)
        private set

    @Composable
    fun OnDarkHeader() {
        DisposableEffect(Unit) {
            darkHeaders++
            onDispose { darkHeaders-- }
        }
    }
}

/** Paints [colors] behind this element and up through the status bar, with [corner] rounding at the bottom only. */
fun Modifier.headerBackground(colors: List<Color>, corner: Dp = 0.dp, vertical: Boolean = false): Modifier = composed {
    val top = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    drawBehind {
        val r = corner.toPx()
        val brush = if (vertical) Brush.verticalGradient(colors, startY = -top, endY = size.height)
        else Brush.linearGradient(colors, start = Offset(0f, -top), end = Offset(size.width, size.height))
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f, top = -top, right = size.width, bottom = size.height,
                    topLeftCornerRadius = CornerRadius.Zero, topRightCornerRadius = CornerRadius.Zero,
                    bottomLeftCornerRadius = CornerRadius(r, r), bottomRightCornerRadius = CornerRadius(r, r),
                ),
            )
        }
        drawPath(path, brush)
    }
}

/** Two soft wave bands drifting sideways at the bottom of a header (the old dashboard's waves, now moving). */
@Composable
fun WaveBand(modifier: Modifier, color: Color = Color.White) {
    val still = reduceMotion()
    val t = rememberInfiniteTransition(label = "waves")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "w1")
    val b by t.animateFloat(1f, 0f, infiniteRepeatable(tween(22_000, easing = LinearEasing)), label = "w2")
    Canvas(modifier) {
        fun wave(phase: Float, amp: Float, base: Float, alpha: Float) {
            val p = Path().apply {
                moveTo(0f, size.height)
                var x = 0f
                while (x <= size.width) {
                    val y = base + amp * sin(2 * PI * (x / size.width * 1.3 + phase)).toFloat()
                    lineTo(x, y); x += 6f
                }
                lineTo(size.width, size.height); close()
            }
            drawPath(p, color.copy(alpha = alpha))
        }
        wave(if (still) 0f else a, size.height * 0.12f, size.height * 0.45f, 0.10f)
        wave(if (still) 0.5f else b, size.height * 0.10f, size.height * 0.62f, 0.08f)
    }
}

/** A light diagonal shine that sweeps across every [periodMs] (buttons, banner cards). */
fun Modifier.shine(periodMs: Int = 4_000, delayMs: Int = 1_200, alpha: Float = 0.35f): Modifier = composed {
    if (reduceMotion()) return@composed this
    val t = rememberInfiniteTransition(label = "shine")
    val x by t.animateFloat(
        -0.6f, 1.6f,
        infiniteRepeatable(tween(periodMs, delayMillis = delayMs, easing = LinearEasing), RepeatMode.Restart), label = "x",
    )
    drawWithContent {
        drawContent()
        val w = size.width * 0.35f
        val start = x * size.width
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = alpha), Color.Transparent),
                start = Offset(start, 0f), end = Offset(start + w, size.height),
            ),
            topLeft = Offset(start - w, 0f), size = Size(w * 3, size.height),
        )
    }
}

/** Entrance: rises and fades in after [delayMs] (stagger tiles by index). Visible at once when animations are off. */
fun Modifier.popIn(delayMs: Int = 0, rise: Dp = 14.dp): Modifier = composed {
    if (reduceMotion()) return@composed this
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(420, delayMillis = delayMs, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    val risePx = with(LocalDensity.current) { rise.toPx() }
    graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * risePx
        val s = 0.85f + 0.15f * p.value
        scaleX = s; scaleY = s
    }
}

/** Squashes slightly while pressed (pair with the same [interaction] on the clickable). */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.9f): Modifier = composed {
    val down by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (down) pressed else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press")
    graphicsLayer { scaleX = s; scaleY = s }
}

/** What a service tile shows: the old app's Lottie animation, its PNG icon, or a vector icon. */
sealed interface ServiceArt {
    data class Lottie(@RawRes val res: Int) : ServiceArt
    data class Picture(@DrawableRes val res: Int) : ServiceArt
    data class Vector(val icon: ImageVector) : ServiceArt
}

@Composable
fun ServiceArtView(art: ServiceArt, tint: Color, modifier: Modifier) {
    when (art) {
        is ServiceArt.Lottie -> {
            val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(art.res))
            val still = reduceMotion()
            LottieAnimation(
                composition, modifier = modifier,
                iterations = if (still) 1 else LottieConstants.IterateForever,
                isPlaying = !still,
            )
        }
        is ServiceArt.Picture -> Image(painterResource(art.res), null, modifier, contentScale = ContentScale.Fit)
        is ServiceArt.Vector -> Icon(art.icon, null, modifier, tint = tint)
    }
}

/**
 * A one-time confetti burst from the top centre (e.g. money added). Decoration only: it draws over [modifier]'s
 * area, takes no touches, and does nothing when animations are off.
 */
@Composable
fun ConfettiBurst(modifier: Modifier, pieces: Int = 70, seed: Int = 3) {
    if (reduceMotion()) return
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(2_600, easing = LinearEasing)) }
    val bits = remember {
        val rnd = kotlin.random.Random(seed)
        val colors = listOf(Color(0xFF1E88E5), Color(0xFFFFC107), Color(0xFF43A047), Color(0xFFE53935), Color(0xFF8E24AA), Color(0xFFFF7043))
        List(pieces) {
            val angle = (-90 + (rnd.nextFloat() - 0.5f) * 150f) * (PI.toFloat() / 180f)
            val speed = 0.55f + rnd.nextFloat() * 0.75f
            floatArrayOf(kotlin.math.cos(angle) * speed, sin(angle) * speed, rnd.nextFloat() * 360f, (rnd.nextFloat() - 0.5f) * 900f, 5f + rnd.nextFloat() * 7f) to colors[rnd.nextInt(colors.size)]
        }
    }
    if (t.value >= 1f) return
    Canvas(modifier) {
        val p = t.value
        val origin = Offset(size.width / 2f, size.height * 0.18f)
        bits.forEach { (b, color) ->
            val x = origin.x + b[0] * size.width * 0.75f * p
            val y = origin.y + b[1] * size.height * 0.6f * p + 0.5f * 1.4f * size.height * p * p
            val rot = b[2] + b[3] * p
            val w = b[4] * density
            rotate(rot, Offset(x, y)) {
                drawRect(color.copy(alpha = (1f - p).coerceIn(0f, 1f)), topLeft = Offset(x - w / 2, y - w / 4), size = Size(w, w / 2))
            }
        }
    }
}
