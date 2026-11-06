package com.asl.wallet.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.theme.AslShapes
import com.asl.wallet.core.designsystem.theme.AslTheme

/**
 * Text field with the label always visible inside the box (works at "Largest" font size, unlike
 * floating labels). States: default, focused (primary ring), error (red + message), success (green message), locked.
 */
@Composable
fun AslTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    success: String? = null,
    locked: Boolean = false,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    maxLength: Int = Int.MAX_VALUE,
    trailingIcon: ImageVector? = null,
    /** Makes [trailingIcon] a button (e.g. pick a contact); [trailingLabel] is read by TalkBack. */
    onTrailingClick: (() -> Unit)? = null,
    trailingLabel: String? = null,
    singleLine: Boolean = true,
) {
    val c = AslTheme.colors
    var focused by remember { mutableStateOf(false) }
    var reveal by remember { mutableStateOf(false) }
    val borderColor = when {
        error != null -> c.error
        focused -> c.primary
        else -> c.outline
    }
    Column(modifier) {
        BasicTextField(
            value = value,
            onValueChange = { if (it.length <= maxLength) onValueChange(it) },
            enabled = !locked,
            singleLine = singleLine,
            textStyle = AslTheme.type.bodyStrong.copy(color = c.textPrimary),
            cursorBrush = SolidColor(c.primary),
            visualTransformation = if (password && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onAny = { onImeAction() }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (focused && error == null) Modifier.border(4.dp, c.tint, AslShapes.field) else Modifier)
                        .background(if (locked) c.background else c.surface, AslShapes.field)
                        .border(1.5.dp, borderColor, AslShapes.field)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = AslTheme.type.caption, color = c.textSecondary)
                        Box {
                            if (value.isEmpty() && placeholder.isNotEmpty()) {
                                Text(placeholder, style = AslTheme.type.body, color = c.textSecondary.copy(alpha = 0.7f))
                            }
                            inner()
                        }
                    }
                    when {
                        password -> Icon(
                            if (reveal) AslIcons.EyeOff else AslIcons.Eye,
                            contentDescription = if (reveal) "Hide password" else "Show password",
                            tint = c.textSecondary,
                            modifier = Modifier.size(22.dp).clickableNoRipple { reveal = !reveal },
                        )
                        locked -> Icon(AslIcons.Lock, contentDescription = "Locked", tint = c.textSecondary, modifier = Modifier.size(20.dp))
                        trailingIcon != null -> Icon(trailingIcon, contentDescription = trailingLabel, tint = if (onTrailingClick != null) c.primary else c.textSecondary,
                            modifier = Modifier.size(if (onTrailingClick != null) 24.dp else 20.dp).then(if (onTrailingClick != null) Modifier.clickableNoRipple(onTrailingClick) else Modifier))
                    }
                }
            },
        )
        when {
            error != null -> Text(error, style = AslTheme.type.caption, color = c.error, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            success != null -> Text("✓ $success", style = AslTheme.type.caption, color = c.success, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        }
    }
}

/**
 * OTP boxes (4 for login/transactions, 6 for Aadhaar). One hidden text field drives the boxes, so
 * paste and SMS autofill (one-time-code) work. The cursor box blinks.
 */
@Composable
fun OtpInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 4,
    isError: Boolean = false,
    onComplete: (String) -> Unit = {},
) {
    val c = AslTheme.colors
    val blink = rememberInfiniteTransition(label = "caret")
    val caretAlpha by blink.animateFloat(1f, 0f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "caretA")
    BasicTextField(
        value = value,
        onValueChange = { raw ->
            val digits = raw.filter(Char::isDigit).take(length)
            onValueChange(digits)
            if (digits.length == length) onComplete(digits)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        modifier = modifier.semantics { contentDescription = "One-time code, $length digits" },
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(if (length == 6) 8.dp else 10.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f)) {}
                repeat(length) { i ->
                    val ch = value.getOrNull(i)
                    val isCursor = i == value.length
                    val border = when {
                        isError -> c.error
                        ch != null || isCursor -> c.primary
                        else -> c.outline
                    }
                    Box(
                        Modifier
                            .size(width = if (length == 6) 42.dp else 52.dp, height = 56.dp)
                            .then(if (isCursor && !isError) Modifier.border(4.dp, c.tint, AslShapes.field) else Modifier)
                            .background(c.surface, AslShapes.field)
                            .border(1.5.dp, border, AslShapes.field),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (ch != null) {
                            Text(ch.toString(), style = AslTheme.type.headline, color = c.textPrimary, textAlign = TextAlign.Center)
                        } else if (isCursor) {
                            Box(Modifier.width(2.dp).height(22.dp).alpha(caretAlpha).background(c.primary))
                        }
                    }
                }
                Row(Modifier.weight(1f)) {}
            }
        },
    )
}
