package com.asl.wallet.core.designsystem.component

import androidx.compose.foundation.layout.imePadding
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.receipt.ReceiptDoc
import com.asl.wallet.core.designsystem.receipt.ReceiptExport
import com.asl.wallet.core.designsystem.theme.AslShapes
import com.asl.wallet.core.designsystem.theme.AslTheme

/** Translated words for [BillReceipt]'s buttons and print sheet. */
data class BillLabels(
    /** WhatsApp, Save PDF, Print, Help. */
    val actions: List<String>,
    val shareTitle: String,
    val printTitle: String,
    val printThermal: String,
    val printA4: String,
)

/**
 * The one customer bill every service shows, right after a transaction and from Reports: the paper slip with its
 * status stamp (Success / Pending / Failed), then WhatsApp · Save PDF · Print · Help. Print asks thermal or A4.
 * [doc] is what is shared, saved and printed, so it must hold the same rows as the slip (and never the commission).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillReceipt(
    shop: ShopInfo,
    doc: ReceiptDoc,
    status: Status,
    labels: BillLabels,
    fileName: String,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
    amountWords: String? = null,
    barcodeSeed: String? = null,
    animate: Boolean = true,
    /** Spoken after the receipt prints (see [ReceiptCard]). */
    voice: String? = null,
) {
    val context = LocalContext.current
    var printChoice by remember { mutableStateOf(false) }
    ReceiptCard(
        shop, doc.title, doc.amount ?: "—", status, doc.rows.map { KeyValue(it.first, it.second) }, modifier,
        footer = doc.footer, statusLabel = doc.status, amountWords = amountWords, barcodeSeed = barcodeSeed, animate = animate, voice = voice,
    ) {
        ReceiptActionBar(
            onWhatsApp = { ReceiptExport.shareText(context, labels.shareTitle, ReceiptExport.text(shop, doc)) },
            onSavePdf = { ReceiptExport.sharePdf(context, shop, doc, labels.shareTitle, fileName) },
            onPrint = { printChoice = true }, onHelp = onHelp, labels = labels.actions,
        )
    }
    if (printChoice) ModalBottomSheet(
        onDismissRequest = { printChoice = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AslTheme.colors.surface, shape = AslShapes.sheet,
    ) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(labels.printTitle, style = AslTheme.type.title, color = AslTheme.colors.textPrimary)
            ListCard {
                listOf(true to labels.printThermal, false to labels.printA4).forEachIndexed { i, (thermal, label) ->
                    if (i > 0) Divider()
                    ListRow(label, icon = if (thermal) AslIcons.Print else AslIcons.Document, onClick = {
                        printChoice = false
                        context.activity()?.let { ReceiptExport.print(it, labels.shareTitle, ReceiptExport.html(shop, doc, thermal), thermal) }
                    })
                }
            }
        }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
