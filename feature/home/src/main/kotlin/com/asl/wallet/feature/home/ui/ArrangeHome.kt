package com.asl.wallet.feature.home.ui

import com.asl.wallet.core.designsystem.component.keyboardAware
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asl.wallet.core.designsystem.component.AslButton
import com.asl.wallet.core.designsystem.component.AslButtonStyle
import com.asl.wallet.core.designsystem.component.AslPrimaryButton
import com.asl.wallet.core.designsystem.component.AslTopBar
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.theme.AslTheme
import com.asl.wallet.feature.home.R
import com.asl.wallet.feature.home.domain.HomeSection

private val ROW_HEIGHT = 64.dp
private val ROW_GAP = 8.dp

@Composable
private fun sectionName(s: HomeSection) = stringResource(
    when (s) {
        HomeSection.Banking -> R.string.classic_banking
        HomeSection.Banners -> R.string.arrange_banners
        HomeSection.Recharge -> R.string.classic_sec_recharge
        HomeSection.Bbps -> R.string.classic_bbps_title
        HomeSection.Reports -> R.string.classic_sec_reports
        HomeSection.Recent -> R.string.home_recent
    },
)

/**
 * Arrange Home: the retailer puts the Home cards in their own order. Drag a row by its handle, or use the arrows
 * (also offered to TalkBack as "Move up" / "Move down"). Save keeps it for this account on this phone.
 */
@Composable
fun ArrangeHomeScreen(vm: HomeViewModel, onDone: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = AslTheme.colors
    LaunchedEffect(Unit) { if (vm.state.value.arranging == null) vm.startArrange() }
    val order = state.arranging ?: state.sections
    val haptic = LocalHapticFeedback.current
    val step = with(LocalDensity.current) { (ROW_HEIGHT + ROW_GAP).toPx() }

    var dragging by remember { mutableStateOf<HomeSection?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val latest by rememberUpdatedState(order)

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().keyboardAware()) {
        AslTopBar(stringResource(R.string.arrange_title), onBack = { vm.cancelArrange(); onDone() }, closeIcon = true)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(ROW_GAP),
        ) {
            Text(stringResource(R.string.arrange_hint), style = AslTheme.type.body, color = c.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
            order.forEachIndexed { i, section ->
                androidx.compose.runtime.key(section) {
                    val name = sectionName(section)
                    val isDragged = dragging == section
                    val lift by animateDpAsState(if (isDragged) 8.dp else 1.dp, label = "lift")
                    val up = stringResource(R.string.arrange_up, name)
                    val down = stringResource(R.string.arrange_down, name)
                    val position = stringResource(R.string.arrange_position, i + 1, order.size)
                    Row(
                        Modifier.fillMaxWidth().height(ROW_HEIGHT)
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer { translationY = if (isDragged) dragOffset else 0f; val s = if (isDragged) 1.02f else 1f; scaleX = s; scaleY = s }
                            .shadow(lift, RoundedCornerShape(12.dp))
                            .background(c.surface, RoundedCornerShape(12.dp))
                            .border(1.dp, if (isDragged) c.primary else c.outline, RoundedCornerShape(12.dp))
                            .semantics(mergeDescendants = true) {
                                stateDescription = position
                                customActions = listOfNotNull(
                                    if (i > 0) CustomAccessibilityAction(up) { vm.moveSection(i, i - 1); true } else null,
                                    if (i < order.lastIndex) CustomAccessibilityAction(down) { vm.moveSection(i, i + 1); true } else null,
                                )
                            }
                            .padding(start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Drag handle: hold and move; the row swaps places as it passes the next one.
                        Box(
                            Modifier.size(48.dp).pointerInput(section) {
                                detectDragGestures(
                                    onDragStart = { dragging = section; dragOffset = 0f; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                    onDragEnd = { dragging = null; dragOffset = 0f },
                                    onDragCancel = { dragging = null; dragOffset = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val from = latest.indexOf(section)
                                    when {
                                        dragOffset > step / 2 && from < latest.lastIndex -> {
                                            vm.moveSection(from, from + 1); dragOffset -= step
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        dragOffset < -step / 2 && from > 0 -> {
                                            vm.moveSection(from, from - 1); dragOffset += step
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                }
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(AslIcons.DragHandle, null, tint = c.textSecondary, modifier = Modifier.size(22.dp)) }
                        Box(
                            Modifier.size(26.dp).background(c.tint, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text("${i + 1}", style = AslTheme.type.micro, color = c.primary) }
                        Text(name, style = AslTheme.type.bodyStrong.copy(fontSize = AslTheme.type.body.fontSize), color = c.textPrimary, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        IconButton(onClick = { vm.moveSection(i, i - 1); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }, enabled = i > 0) {
                            Icon(AslIcons.ArrowUp, up, tint = if (i > 0) c.primary else c.outline, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { vm.moveSection(i, i + 1); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }, enabled = i < order.lastIndex) {
                            Icon(AslIcons.ArrowDown, down, tint = if (i < order.lastIndex) c.primary else c.outline, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(c.surface).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AslPrimaryButton(stringResource(R.string.arrange_save), { vm.saveArrange(); onDone() })
            AslButton(stringResource(R.string.arrange_reset), vm::resetArrange, Modifier.fillMaxWidth(), style = AslButtonStyle.Outline)
        }
    }
}
