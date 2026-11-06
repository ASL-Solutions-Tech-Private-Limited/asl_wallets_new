package com.asl.wallet.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.theme.AslGradients
import com.asl.wallet.core.designsystem.theme.AslShapes
import com.asl.wallet.core.designsystem.theme.AslTheme

data class ShopInfo(val name: String, val id: String, val addressLine: String)

/** The customer receipt itself is the paper slip in Slip.kt ([ReceiptCard]). */

data class ReceiptAction(val label: String, val icon: ImageVector, val colors: List<Color>, val onClick: () -> Unit)

/** WhatsApp · Save PDF · Print · Help, under every receipt. */
@Composable
fun ReceiptActionBar(
    onWhatsApp: () -> Unit, onSavePdf: () -> Unit, onPrint: () -> Unit, onHelp: () -> Unit, modifier: Modifier = Modifier,
    /** Translated labels in the order WhatsApp, Save PDF, Print, Help; English by default. */
    labels: List<String> = listOf("WhatsApp", "Save PDF", "Print", "Help"),
) {
    val actions = listOf(
        ReceiptAction(labels.getOrElse(0) { "WhatsApp" }, AslIcons.Chat, AslGradients.Service.Upi.colors, onWhatsApp),
        ReceiptAction(labels.getOrElse(1) { "Save PDF" }, AslIcons.Document, AslGradients.Service.Bills.colors, onSavePdf),
        ReceiptAction(labels.getOrElse(2) { "Print" }, AslIcons.Print, AslGradients.Service.Aeps.colors, onPrint),
        ReceiptAction(labels.getOrElse(3) { "Help" }, AslIcons.Help, AslGradients.Service.Recharge.colors, onHelp),
    )
    val c = AslTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { a ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp, c.outline, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClickLabel = a.label, onClick = a.onClick).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.size(34.dp).background(Brush.linearGradient(a.colors), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                    Icon(a.icon, null, tint = Color.White, modifier = Modifier.size(17.dp))
                }
                Text(a.label, style = AslTheme.type.micro, color = c.textPrimary)
            }
        }
    }
}

/** "Need help with this transaction?" card: complaint, WhatsApp, call. */
@Composable
fun HelpCard(onComplaint: () -> Unit, onWhatsApp: () -> Unit, onCall: () -> Unit, modifier: Modifier = Modifier, title: String = "Need help with this transaction?") {
    val c = AslTheme.colors
    AslCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = AslTheme.type.label, color = c.textPrimary, modifier = Modifier.weight(1f))
                Icon(AslIcons.Help, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Triple("Complaint", AslIcons.Alert, onComplaint), Triple("WhatsApp", AslIcons.Chat, onWhatsApp), Triple("Call", AslIcons.Phone, onCall)).forEach { (l, i, a) ->
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(c.tint).clickable(role = Role.Button, onClick = a).padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(i, null, tint = c.primary, modifier = Modifier.size(15.dp))
                        Box(Modifier.width(5.dp))
                        Text(l, style = AslTheme.type.micro, color = c.primary)
                    }
                }
            }
        }
    }
}

/** Bank-passbook style table. A row starting with "!" is highlighted (opening/closing balance). Cr green, Dr red. */
@Composable
fun PassbookTable(header: List<String>, rows: List<List<String>>, weights: List<Float>, modifier: Modifier = Modifier) {
    val c = AslTheme.colors
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, c.outline, RoundedCornerShape(12.dp)).background(c.surface)) {
        Row(Modifier.fillMaxWidth().background(c.textPrimary).padding(horizontal = 8.dp, vertical = 6.dp)) {
            header.forEachIndexed { i, h ->
                Text(h.uppercase(), style = AslTheme.type.micro.copy(fontSize = AslTheme.type.micro.fontSize * 0.9f), color = c.surface,
                    textAlign = if (i >= 2) TextAlign.End else TextAlign.Start, modifier = Modifier.weight(weights[i]))
            }
        }
        rows.forEachIndexed { r, raw ->
            val highlight = raw.firstOrNull() == "!"
            val cells = if (highlight) raw.drop(1) else raw
            Row(
                Modifier.fillMaxWidth()
                    .background(if (highlight) c.successBg else if (r % 2 == 1) c.tint.copy(alpha = 0.5f) else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                cells.forEachIndexed { i, v ->
                    val color = when {
                        i >= 2 && (v.startsWith("+") || v == "Cr") -> c.success
                        i >= 2 && (v.startsWith("−") || v.startsWith("-") || v == "Dr") -> c.error
                        else -> c.textPrimary
                    }
                    Text(v, style = if (highlight || i >= 2) AslTheme.type.caption.copy(fontWeight = AslTheme.type.label.fontWeight) else AslTheme.type.caption,
                        color = color, textAlign = if (i >= 2) TextAlign.End else TextAlign.Start, modifier = Modifier.weight(weights[i]))
                }
            }
        }
    }
}
