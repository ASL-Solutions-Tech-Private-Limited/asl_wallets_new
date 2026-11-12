package com.asl.wallet.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Keeps the app readable on tablets, foldables and landscape without per-screen tablet layouts.
 *
 * The app is phone-first (single column). On a narrow screen this is a no-op — [content] fills the width as before.
 * Once the available width passes [maxContentWidth] (a large phone in landscape, a tablet, split-screen), the content
 * is capped to that width and centred, so cards read as one tidy column instead of stretching edge to edge, and the
 * surrounding [Surface] background shows as side gutters.
 */
@Composable
fun AdaptiveContent(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 720.dp,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth > maxContentWidth
        Box((if (wide) Modifier.width(maxContentWidth) else Modifier.fillMaxWidth()).fillMaxHeight()) {
            content()
        }
    }
}
