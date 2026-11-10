package com.asl.wallet.core.designsystem.component

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction

/*
 * Keyboard handling. The app draws edge to edge, so the window does NOT resize for the keyboard by itself
 * (adjustResize alone does nothing): each screen root must take the keyboard's height as padding.
 */

/**
 * For a screen's root: everything (the scrolling body and the fixed bottom buttons) sits above the keyboard, and a tap
 * on empty space closes the keyboard. Taps on buttons and fields are theirs: they never reach here.
 */
fun Modifier.keyboardAware(): Modifier = composed {
    val focus = LocalFocusManager.current
    imePadding().pointerInput(Unit) { detectTapGestures(onTap = { focus.clearFocus() }) }
}

/**
 * Keyboard key behaviour for a field: Next moves to the next field (or closes the keyboard on the last one),
 * Done / Go / Search / Send close the keyboard. [extra] still runs first (e.g. submit on Done).
 */
@Composable
fun rememberImeActions(imeAction: ImeAction, extra: () -> Unit = {}): KeyboardActions {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return KeyboardActions(onAny = {
        extra()
        when (imeAction) {
            ImeAction.Next -> if (!focus.moveFocus(FocusDirection.Down)) { focus.clearFocus(); keyboard?.hide() }
            ImeAction.Previous -> focus.moveFocus(FocusDirection.Up)
            else -> { focus.clearFocus(); keyboard?.hide() }
        }
    })
}
