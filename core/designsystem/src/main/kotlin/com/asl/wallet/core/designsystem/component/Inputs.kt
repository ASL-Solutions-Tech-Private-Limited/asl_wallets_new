package com.asl.wallet.core.designsystem.component

import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

import androidx.compose.ui.platform.LocalDensity

import androidx.compose.foundation.layout.ime

import androidx.compose.foundation.layout.WindowInsets

import androidx.compose.foundation.relocation.bringIntoViewRequester

import androidx.compose.foundation.relocation.BringIntoViewRequester

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.lerp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
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
 * Text field (classic look, refreshed 9 Nov 2026): taller, with a floating label. The label rests large inside an
 * empty field and glides up when the field is focused or filled, so it is always visible (works at the largest font
 * size too). Focus: the border turns blue with a soft glow. A leading icon is added for passwords (lock), phone
 * numbers (phone) and login IDs (person), or pass [leadingIcon]. While typing, a clear button and (with a
 * [maxLength]) a counter appear. Errors shake the field once and slide in with an icon.
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
    /** How the text is shown (e.g. [GroupedDigits] for Aadhaar); the value itself is unchanged. */
    display: VisualTransformation? = null,
    /** Bigger, spaced-out text for numbers that must be read back easily (Aadhaar). */
    large: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    maxLength: Int = Int.MAX_VALUE,
    trailingIcon: ImageVector? = null,
    /** Makes [trailingIcon] a button (e.g. pick a contact); [trailingLabel] is read by TalkBack. */
    onTrailingClick: (() -> Unit)? = null,
    trailingLabel: String? = null,
    singleLine: Boolean = true,
    /** Shown on the left; by default chosen from the field type (null = none). */
    leadingIcon: ImageVector? = defaultLeading(password, keyboardType),
) {
    val c = AslTheme.colors
    var focused by remember { mutableStateOf(false) }
    var reveal by remember { mutableStateOf(false) }
    val still = reduceMotion()
    val spec = if (still) snap<Float>() else tween<Float>(200)

    val floating = focused || value.isNotEmpty()
    val p by animateFloatAsState(if (floating) 1f else 0f, spec, label = "label")
    val glow by animateFloatAsState(if (focused && error == null) 1f else 0f, spec, label = "glow")
    val borderColor by animateColorAsState(
        when { error != null -> c.error; focused -> c.primaryEnd; locked -> c.outline.copy(alpha = 0.6f); else -> c.outline },
        if (still) snap() else tween(200), label = "border",
    )
    val labelColor by animateColorAsState(
        when { error != null -> c.error; focused -> c.primaryEnd; else -> c.textSecondary },
        if (still) snap() else tween(200), label = "labelColor",
    )
    // Shake once when an error appears.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(error) {
        if (error != null && !still) for (v in listOf(10f, -8f, 6f, -4f, 2f, 0f)) shake.animateTo(v, tween(45))
    }
    val shape = RoundedCornerShape(14.dp)
    // Once the keyboard is up, scroll this field (with its error line) into view above it.
    val inView = remember { BringIntoViewRequester() }
    val keyboardUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(focused, keyboardUp) { if (focused && keyboardUp) { delay(120); inView.bringIntoView() } }

    Column(modifier.bringIntoViewRequester(inView).graphicsLayer { translationX = shake.value * density }) {
        BasicTextField(
            value = value,
            onValueChange = { if (it.length <= maxLength) onValueChange(it) },
            enabled = !locked,
            singleLine = singleLine,
            textStyle = AslTheme.type.bodyStrong.copy(color = if (locked) c.textSecondary else c.textPrimary)
                .let { if (large) it.copy(fontSize = 20.sp, letterSpacing = 1.2.sp) else it },
            cursorBrush = SolidColor(c.primaryEnd),
            visualTransformation = if (password && !reveal) PasswordVisualTransformation() else display ?: VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                // A password field keeps a number pad when its content is numeric (e.g. a masked account number).
                keyboardType = when {
                    password && (keyboardType == KeyboardType.Number || keyboardType == KeyboardType.NumberPassword || keyboardType == KeyboardType.Phone) -> KeyboardType.NumberPassword
                    password -> KeyboardType.Password
                    else -> keyboardType
                },
                imeAction = imeAction,
                autoCorrectEnabled = false,
            ),
            keyboardActions = rememberImeActions(imeAction, onImeAction),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        // Glow drawn just outside the field: edges stay aligned with other components, and it never covers the border.
                        .drawBehind {
                            if (glow > 0f) {
                                val g = 4.dp.toPx()
                                drawRoundRect(
                                    c.tint.copy(alpha = glow), topLeft = Offset(-g, -g), size = Size(size.width + 2 * g, size.height + 2 * g),
                                    cornerRadius = CornerRadius(18.dp.toPx()),
                                )
                            }
                        }
                        .background(if (locked) c.background else c.surface, shape)
                        .border(if (focused || error != null) 2.dp else 1.5.dp, borderColor, shape)
                        .padding(start = if (leadingIcon != null) 12.dp else 16.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        Icon(leadingIcon, null, tint = if (focused) c.primaryEnd else c.textSecondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                    }
                    Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        // Floating label: large in the middle of an empty field, small on top once focused or filled.
                        Text(
                            label,
                            style = lerp(AslTheme.type.body.copy(fontSize = AslTheme.type.bodyStrong.fontSize), AslTheme.type.caption, p),
                            color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer { translationY = (1f - p) * 10.dp.toPx() },
                        )
                        Box(Modifier.padding(top = 18.dp).graphicsLayer { alpha = if (floating) 1f else 0f }) {
                            if (value.isEmpty() && placeholder.isNotEmpty() && floating) {
                                Text(placeholder, style = AslTheme.type.body, color = c.textSecondary.copy(alpha = 0.6f), maxLines = 1)
                            }
                            inner()
                        }
                    }
                    when {
                        password -> Icon(
                            if (reveal) AslIcons.EyeOff else AslIcons.Eye,
                            contentDescription = if (reveal) "Hide password" else "Show password",
                            tint = if (reveal) c.primaryEnd else c.textSecondary,
                            modifier = Modifier.size(40.dp).clip(CircleShape).clickableNoRipple { reveal = !reveal }.padding(9.dp),
                        )
                        locked -> Icon(AslIcons.Lock, contentDescription = "Locked", tint = c.textSecondary, modifier = Modifier.size(20.dp))
                        trailingIcon != null -> Icon(
                            trailingIcon, contentDescription = trailingLabel, tint = if (onTrailingClick != null) c.primaryEnd else c.textSecondary,
                            modifier = Modifier.size(40.dp).clip(CircleShape).then(if (onTrailingClick != null) Modifier.clickableNoRipple(onTrailingClick) else Modifier).padding(9.dp),
                        )
                        focused && value.isNotEmpty() -> Icon(
                            AslIcons.Close, contentDescription = "Clear $label", tint = c.textSecondary,
                            modifier = Modifier.size(36.dp).clip(CircleShape).clickableNoRipple { onValueChange("") }.padding(10.dp),
                        )
                    }
                }
            },
        )
        val counter = maxLength != Int.MAX_VALUE && maxLength < 1000 && focused
        AnimatedVisibility(error != null || success != null || counter, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    error != null -> {
                        Icon(AslIcons.Alert, null, tint = c.error, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(error, style = AslTheme.type.caption, color = c.error, modifier = Modifier.weight(1f))
                    }
                    success != null -> {
                        Icon(AslIcons.Check, null, tint = c.success, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(success, style = AslTheme.type.caption, color = c.success, modifier = Modifier.weight(1f))
                    }
                    else -> Spacer(Modifier.weight(1f))
                }
                if (counter) Text("${value.length} / $maxLength", style = AslTheme.type.caption, color = c.textSecondary)
            }
        }
    }
}

/** The leading icon a field gets from its type: lock for passwords, phone for numbers, person for login IDs. */
fun defaultLeading(password: Boolean, keyboardType: KeyboardType): ImageVector? = when {
    password -> AslIcons.Lock
    keyboardType == KeyboardType.Phone -> AslIcons.Phone
    keyboardType == KeyboardType.Email -> AslIcons.User
    else -> null
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
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    BasicTextField(
        value = value,
        onValueChange = { raw ->
            val digits = raw.filter(Char::isDigit).take(length)
            onValueChange(digits)
            // All digits in: the keyboard goes away so the Verify button shows.
            if (digits.length == length) { keyboard?.hide(); onComplete(digits) }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = rememberImeActions(ImeAction.Done),
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
                            .background(if (ch != null && !isError) c.tint else c.surface, AslShapes.field)
                            .border(1.5.dp, border, AslShapes.field),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (ch != null) {
                            // Each digit pops in as it is typed (classic look); still when animations are off.
                            val still = reduceMotion()
                            val pop = remember { androidx.compose.animation.core.Animatable(if (still) 1f else 0.4f) }
                            LaunchedEffect(Unit) { pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 500f)) }
                            Text(
                                ch.toString(), style = AslTheme.type.headline, color = c.textPrimary, textAlign = TextAlign.Center,
                                modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
                            )
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

/**
 * Shows digits in groups ("123456781234" → "1234 5678 1234") while the value stays digits only, and keeps the cursor
 * in the right place across the added spaces.
 */
class GroupedDigits(private val group: Int = 4) : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val raw = text.text
        val out = buildString { raw.forEachIndexed { i, ch -> if (i > 0 && i % group == 0) append(' '); append(ch) } }
        val map = object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = if (offset <= 0) 0 else (offset + (offset - 1) / group).coerceAtMost(out.length)
            override fun transformedToOriginal(offset: Int): Int = (offset - offset / (group + 1)).coerceIn(0, raw.length)
        }
        return androidx.compose.ui.text.input.TransformedText(androidx.compose.ui.text.AnnotatedString(out), map)
    }
}
