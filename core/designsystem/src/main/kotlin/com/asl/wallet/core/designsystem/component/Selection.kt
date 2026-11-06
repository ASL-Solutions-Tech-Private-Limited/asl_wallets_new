package com.asl.wallet.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.asl.wallet.core.designsystem.theme.AslGradients
import com.asl.wallet.core.designsystem.theme.AslShapes
import com.asl.wallet.core.designsystem.theme.AslTheme

internal fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onClick))

/** Single-select chips (quick amounts, banks, filters). Selected chip uses the action gradient. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceChips(
    options: List<String>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AslTheme.colors
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .clip(AslShapes.chip)
                    .then(
                        if (on) Modifier.background(Brush.linearGradient(AslGradients.action))
                        else Modifier.background(c.surface).border(1.dp, c.outline, AslShapes.chip),
                    )
                    .semantics { this.selected = on }
                    .clickable(role = Role.RadioButton) { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text(label, style = AslTheme.type.label, color = if (on) Color.White else c.textPrimary)
            }
        }
    }
}

/** Segmented control (IMPS/NEFT, AEPS type, theme). */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AslTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(c.surface, RoundedCornerShape(14.dp))
            .border(1.dp, c.outline, RoundedCornerShape(14.dp))
            .padding(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .then(if (on) Modifier.shadow(6.dp, RoundedCornerShape(11.dp), ambientColor = c.primary, spotColor = c.primary).background(Brush.linearGradient(AslGradients.action)) else Modifier)
                    .semantics { this.selected = on }
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = AslTheme.type.label, color = if (on) Color.White else c.textSecondary, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

/** On/off switch matching the design (gradient when on). */
@Composable
fun AslSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = AslTheme.colors
    val knob by animateDpAsState(if (checked) 19.dp else 3.dp, label = "knob")
    val track by animateColorAsState(if (checked) c.primary else c.outline, label = "track")
    Box(
        modifier
            .size(width = 40.dp, height = 24.dp)
            .clip(CircleShape)
            .background(if (checked) Brush.linearGradient(AslGradients.action) else Brush.linearGradient(listOf(track, track)))
            .semantics { this.selected = checked }
            .clickable(role = Role.Switch) { onCheckedChange(!checked) },
    ) {
        Box(Modifier.offset(x = knob, y = 3.dp).size(18.dp).shadow(2.dp, CircleShape).background(Color.White, CircleShape))
    }
}

/** Underlined tabs (plans, downline reports). */
@Composable
fun AslTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = AslTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(Modifier.clickable(role = Role.Tab) { onSelect(i) }.padding(vertical = 8.dp)) {
                Text(label, style = AslTheme.type.label, color = if (on) c.primary else c.textSecondary)
                if (on) Box(Modifier.align(Alignment.BottomStart).offset(y = 8.dp).height(3.dp).width(28.dp).background(c.primary, RoundedCornerShape(2.dp)))
            }
        }
    }
}
