package com.asl.wallet.feature.auth.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asl.wallet.core.designsystem.component.AslPrimaryButton
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.theme.AslGradients
import com.asl.wallet.core.designsystem.theme.AslTheme
import com.asl.wallet.feature.auth.R
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

/** One onboarding page: a hero icon with floating satellites, a title and a body line. */
private data class OnboardPage(
    val hero: ImageVector,
    val accent: List<Color>,
    val satellites: List<ImageVector>,
    val titleRes: Int,
    val bodyRes: Int,
)

private val onboardPages = listOf(
    OnboardPage(
        hero = AslIcons.Wallet,
        accent = AslGradients.Service.Wallet.colors,
        satellites = listOf(AslIcons.Send, AslIcons.Bill, AslIcons.Upi, AslIcons.Atm),
        titleRes = R.string.ob1_title, bodyRes = R.string.ob1_body,
    ),
    OnboardPage(
        hero = AslIcons.Bill,
        accent = AslGradients.Service.Recharge.colors,
        satellites = listOf(AslIcons.Phone, AslIcons.Tv, AslIcons.Qr, AslIcons.Bank),
        titleRes = R.string.ob2_title, bodyRes = R.string.ob2_body,
    ),
    OnboardPage(
        hero = AslIcons.Earnings,
        accent = AslGradients.earnings,
        satellites = listOf(AslIcons.Star, AslIcons.ArrowUp, AslIcons.Shield, AslIcons.Report),
        titleRes = R.string.ob3_title, bodyRes = R.string.ob3_body,
    ),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val pager = rememberPagerState { onboardPages.size }
    val scope = rememberCoroutineScope()
    val last = onboardPages.lastIndex
    val finish: () -> Unit = { vm.finish(onDone) }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(AslGradients.action))
            .systemBarsPadding(),
    ) {
        // A soft, slowly-rotating glow behind everything.
        val glow = rememberInfiniteTransition(label = "glow")
        val spin by glow.animateFloat(0f, 360f, infiniteRepeatable(tween(26_000), RepeatMode.Restart), label = "spin")
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = 40.dp).size(460.dp).rotate(spin)
                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent))),
        )

        Column(Modifier.fillMaxSize()) {
            // Skip (hidden on the last page so the primary action stands alone).
            Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp)) {
                if (pager.currentPage != last) {
                    Text(
                        stringResource(R.string.ob_skip),
                        style = AslTheme.type.label, color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.align(Alignment.CenterEnd).clip(CircleShape)
                            .clickable(role = Role.Button, onClick = finish).padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            HorizontalPager(pager, Modifier.weight(1f)) { page ->
                val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction)
                OnboardPageContent(onboardPages[page], offset)
            }

            // Dots.
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(onboardPages.size) { i ->
                    val on = pager.currentPage == i
                    val w by animateDpAsState(if (on) 26.dp else 8.dp, spring(), label = "dotW")
                    Box(
                        Modifier.padding(horizontal = 4.dp).size(width = w, height = 8.dp).clip(CircleShape)
                            .background(if (on) Color.White else Color.White.copy(alpha = 0.35f)),
                    )
                }
            }

            // Primary action: Next, or Get started on the last page.
            AnimatedContent(
                targetState = pager.currentPage == last,
                transitionSpec = { (fadeIn(tween(220)) togetherWith fadeOut(tween(160))) },
                label = "cta",
            ) { onLast ->
                AslPrimaryButton(
                    text = stringResource(if (onLast) R.string.ob_start else R.string.ob_next),
                    onClick = { if (onLast) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    icon = if (onLast) null else AslIcons.Chevron,
                )
            }
        }
    }
}

/** Hero + satellites with parallax, then the text, all keyed to the pager [offset] (0 = centred). */
@Composable
private fun OnboardPageContent(page: OnboardPage, offset: Float) {
    val dist = offset.absoluteValue.coerceIn(0f, 1f)
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Hero(page, offset)
        Spacer(Modifier.height(44.dp))
        Text(
            stringResource(page.titleRes),
            style = AslTheme.type.headline.copy(fontWeight = FontWeight.ExtraBold),
            color = Color.White, textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer {
                alpha = 1f - dist
                translationX = offset * 160f
            },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(page.bodyRes),
            style = AslTheme.type.body, color = Color.White.copy(alpha = 0.82f), textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer {
                alpha = 1f - dist
                translationX = offset * 90f
            },
        )
    }
}

@Composable
private fun Hero(page: OnboardPage, offset: Float) {
    val t = rememberInfiniteTransition(label = "hero")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "pulse")
    val bob by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val dist = offset.absoluteValue.coerceIn(0f, 1f)
    val scale = lerp(0.82f, 1f, 1f - dist)

    Box(
        Modifier.size(260.dp)
            .graphicsLayer {
                translationX = offset * 240f // parallax: moves faster than the text for depth
                alpha = 1f - dist * 0.6f
                scaleX = scale; scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        // Expanding rings.
        Box(
            Modifier.fillMaxSize().drawBehind {
                val base = size.minDimension / 2f
                for (i in 0..2) {
                    val p = ((pulse + i / 3f) % 1f)
                    drawCircle(
                        color = Color.White.copy(alpha = (1f - p) * 0.18f),
                        radius = base * (0.5f + p * 0.5f),
                        center = Offset(size.width / 2f, size.height / 2f),
                    )
                }
            },
        )
        // Satellites around the hero, each bobbing.
        val ring = 108.dp
        page.satellites.forEachIndexed { i, icon ->
            val angle = Math.toRadians((i * 360.0 / page.satellites.size) - 90.0)
            val dx = (ring.value * kotlin.math.cos(angle)).dp
            val dy = (ring.value * kotlin.math.sin(angle)).dp
            Satellite(icon, page.accent, Modifier.offset(x = dx, y = dy + (bob * (if (i % 2 == 0) 6 else -6)).dp))
        }
        // The hero mark.
        Box(
            Modifier.size(104.dp).clip(RoundedCornerShape(32.dp))
                .background(Brush.linearGradient(page.accent))
                .drawBehind {
                    drawCircle(Color.White.copy(alpha = 0.14f), radius = size.minDimension * 0.9f, center = Offset(size.width * 0.3f, size.height * 0.25f))
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(page.hero, null, tint = Color.White, modifier = Modifier.size(52.dp))
        }
    }
}

@Composable
private fun Satellite(icon: ImageVector, accent: List<Color>, modifier: Modifier) {
    Box(
        modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = accent.first(), modifier = Modifier.fillMaxSize())
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction
