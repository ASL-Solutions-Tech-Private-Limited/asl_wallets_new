package com.asl.wallet.feature.wallet.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.asl.wallet.core.common.format.Money
import com.asl.wallet.core.designsystem.component.AlertBanner
import com.asl.wallet.core.designsystem.component.AlertKind
import com.asl.wallet.core.designsystem.component.AslButton
import com.asl.wallet.core.designsystem.component.AslButtonStyle
import com.asl.wallet.core.designsystem.component.AslCard
import com.asl.wallet.core.designsystem.component.AslTopBar
import com.asl.wallet.core.designsystem.component.BigIcon
import com.asl.wallet.core.designsystem.component.KeyValue
import com.asl.wallet.core.designsystem.component.KeyValueList
import com.asl.wallet.core.designsystem.component.ListCard
import com.asl.wallet.core.designsystem.component.ListRow
import com.asl.wallet.core.designsystem.component.StatePresets
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.theme.AslShapes
import com.asl.wallet.core.designsystem.theme.AslTheme
import com.asl.wallet.core.network.UiError
import com.asl.wallet.core.security.ui.SecureScreen
import com.asl.wallet.feature.wallet.R
import com.asl.wallet.feature.wallet.domain.AmountProblem
import com.asl.wallet.feature.wallet.domain.Limits
import java.math.BigDecimal

/** Top bar, scrolling body and an optional fixed bottom area. [secure] blocks screenshots (money and OTP screens). */
@Composable
internal fun WalletScaffold(
    title: String,
    onBack: (() -> Unit)?,
    secure: Boolean = false,
    close: Boolean = false,
    right: (@Composable () -> Unit)? = null,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AslTheme.colors
    if (secure) SecureScreen()
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        AslTopBar(title, onBack = onBack, closeIcon = close, actions = { right?.invoke() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp)
                .then(if (bottom == null) Modifier.navigationBarsPadding() else Modifier),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
        )
        if (bottom != null) Column(Modifier.fillMaxWidth().background(c.surface).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = bottom)
    }
}

@Composable
internal fun uiErrorText(e: UiError): String = when (e) {
    is UiError.Server -> e.message
    UiError.NoInternet -> stringResource(R.string.w_offline)
    UiError.Timeout -> stringResource(R.string.w_timeout)
    UiError.Insecure -> stringResource(R.string.w_insecure)
    UiError.Unknown -> stringResource(R.string.w_generic)
}

@Composable
internal fun ErrorBanner(e: UiError?) { e?.let { AlertBanner(AlertKind.Error, uiErrorText(it)) } }

@Composable
internal fun HeaderText(text: String) = Text(text, style = AslTheme.type.label, color = AslTheme.colors.textSecondary)

@Composable
internal fun Note(text: String) = Text(text, style = AslTheme.type.caption, color = AslTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

/** "₹ 500 to ₹ 2,000 per payment" style messages for a failed limit check. */
@Composable
internal fun amountProblemText(p: AmountProblem, limits: Limits): String {
    val lo = Money.format(limits.min, showPaise = false); val hi = Money.format(limits.max, showPaise = false)
    return when (p) {
        AmountProblem.Empty -> stringResource(R.string.w_amount_enter)
        AmountProblem.BelowMin -> stringResource(R.string.w_amount_min, lo, lo, hi)
        AmountProblem.AboveMax -> stringResource(R.string.w_amount_max, hi, lo, hi)
        AmountProblem.OverBalance -> stringResource(R.string.w_amount_balance)
    }
}

/** A field that opens a picker (bank, date): looks like a form row, reads as a button. */
@Composable
internal fun PickerRow(label: String, value: String?, placeholder: String, icon: ImageVector, error: String? = null, onClick: () -> Unit) {
    val c = AslTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HeaderText(label)
        ListCard {
            ListRow(value ?: placeholder, icon = icon, onClick = onClick,
                trailing = { androidx.compose.material3.Icon(AslIcons.Chevron, null, tint = c.textSecondary, modifier = Modifier.padding(start = 4.dp)) })
        }
        if (error != null) Text(error, style = AslTheme.type.caption, color = c.error)
    }
}

/** 4.5, 4.7, 4.23 Progress with named steps. The screen can't be left by accident while it shows. */
@Composable
internal fun Processing(barTitle: String, title: String, subtitle: String, steps: List<String>, current: Int, colors: List<Color> = AslTheme.colors.heroGradient) {
    SecureScreen()
    androidx.activity.compose.BackHandler {}
    com.asl.wallet.core.designsystem.component.ProcessingScreen(barTitle, title, subtitle, steps, current, colors)
}

internal typealias ResultKind = com.asl.wallet.core.designsystem.component.ResultKind

@Composable
internal fun ResultBlock(kind: ResultKind, title: String, subtitle: String, rows: List<KeyValue>) =
    com.asl.wallet.core.designsystem.component.ResultView(kind, title, subtitle, rows)

@Composable
internal fun ConfirmDialog(title: String, message: String, confirm: String, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = AslTheme.colors.surface,
        title = { Text(title, style = AslTheme.type.title, color = AslTheme.colors.textPrimary) },
        text = { Text(message, style = AslTheme.type.body, color = AslTheme.colors.textSecondary) },
        confirmButton = { AslButton(confirm, onConfirm, style = AslButtonStyle.Danger, compact = true, loading = busy) },
        dismissButton = { AslButton(stringResource(R.string.w_keep), onDismiss, style = AslButtonStyle.Outline, compact = true, enabled = !busy) },
    )
}

/** [dismissible] = false for OTP sheets: only their Cancel closes them, and their window blocks screenshots. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WalletSheet(onDismiss: () -> Unit, dismissible: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { if (dismissible) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { dismissible || it != androidx.compose.material3.SheetValue.Hidden }),
        properties = androidx.compose.material3.ModalBottomSheetProperties(
            securePolicy = if (dismissible) androidx.compose.ui.window.SecureFlagPolicy.Inherit else androidx.compose.ui.window.SecureFlagPolicy.SecureOn,
            shouldDismissOnBackPress = dismissible,
        ),
        containerColor = AslTheme.colors.surface, shape = AslShapes.sheet,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** 4.11 Add money opens soon: shown instead of a failure while PayU isn't switched on. Nothing is charged. */
@Composable
internal fun SoonSheet(onDismiss: () -> Unit) = WalletSheet(onDismiss) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BigIcon(AslIcons.Clock, StatePresets.warnColors, size = 60.dp)
        Text(stringResource(R.string.w_soon_title), style = AslTheme.type.title, color = AslTheme.colors.textPrimary, textAlign = TextAlign.Center)
        Text(stringResource(R.string.w_soon_msg), style = AslTheme.type.body, color = AslTheme.colors.textSecondary, textAlign = TextAlign.Center)
    }
    AslButton(stringResource(R.string.w_ok), onDismiss, Modifier.fillMaxWidth())
}

internal fun money(v: BigDecimal?): String = v?.let { Money.format(it) } ?: "—"

/** Copies a company bank detail; marked sensitive so Android 13+ hides it from the clipboard preview. */
internal fun copy(context: Context, label: String, text: String, toast: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText(label, text)
    if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    cm.setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}
