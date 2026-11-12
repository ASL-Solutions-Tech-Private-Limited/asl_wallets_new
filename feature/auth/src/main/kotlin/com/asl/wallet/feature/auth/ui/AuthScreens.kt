package com.asl.wallet.feature.auth.ui

import com.asl.wallet.core.designsystem.component.keyboardAware
import com.asl.wallet.core.designsystem.component.StatusBarTone
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asl.wallet.core.common.validation.PasswordStrength
import com.asl.wallet.core.designsystem.component.AlertBanner
import com.asl.wallet.core.designsystem.component.AlertKind
import com.asl.wallet.core.designsystem.component.AslButton
import com.asl.wallet.core.designsystem.component.AslButtonStyle
import com.asl.wallet.core.designsystem.component.AslPrimaryButton
import com.asl.wallet.core.designsystem.component.AslTextField
import com.asl.wallet.core.designsystem.component.AslTopBar
import com.asl.wallet.core.designsystem.component.BigIcon
import com.asl.wallet.core.designsystem.component.Divider
import com.asl.wallet.core.designsystem.component.ListCard
import com.asl.wallet.core.designsystem.component.ListRow
import com.asl.wallet.core.designsystem.component.OtpInput
import com.asl.wallet.core.designsystem.component.StatePresets
import com.asl.wallet.core.designsystem.component.StateView
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.theme.AslGradients
import com.asl.wallet.core.designsystem.theme.AslShapes
import com.asl.wallet.core.designsystem.theme.AslTheme
import com.asl.wallet.core.security.lock.SystemUnlock
import com.asl.wallet.core.security.lock.UnlockResult
import com.asl.wallet.core.security.ui.SecureScreen
import com.asl.wallet.core.security.ui.findActivity
import com.asl.wallet.feature.auth.R
import com.asl.wallet.feature.auth.domain.AuthError

/* ------------------------------------------------ shared ------------------------------------------------ */

@Composable
fun authErrorText(e: AuthError): String = when (e) {
    is AuthError.Server -> e.message
    AuthError.NoInternet -> stringResource(R.string.err_no_internet)
    AuthError.Timeout -> stringResource(R.string.err_timeout)
    AuthError.Insecure -> stringResource(R.string.err_insecure)
    AuthError.Unknown -> stringResource(R.string.err_unknown)
    AuthError.OtpSessionExpired -> stringResource(R.string.err_otp_expired)
}

/** Dark "night sky" header with soft moving lights, used by Splash, Language and Login. */
@Composable
private fun AuroraBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(11000), RepeatMode.Reverse), label = "b")
    // Classic look: the old app's navy with soft blue lights.
    StatusBarTone.OnDarkHeader()
    Box(modifier.background(Color(0xFF03274B))) {
        // Soft lights drawn as radial gradients: no blur, so it works on Android 7 and costs little on budget phones.
        Box(Modifier.offset(x = (-90 + 30 * a).dp, y = (-90 + 20 * a).dp).size(320.dp)
            .background(Brush.radialGradient(listOf(Color(0xFF1565C0).copy(alpha = 0.95f), Color(0xFF1565C0).copy(alpha = 0.35f), Color.Transparent))))
        Box(Modifier.align(Alignment.TopEnd).offset(x = (110 - 30 * b).dp, y = (-10 + 20 * b).dp).size(300.dp)
            .background(Brush.radialGradient(listOf(Color(0xFF1E88E5).copy(alpha = 0.9f), Color(0xFF1E88E5).copy(alpha = 0.3f), Color.Transparent))))
        Box(Modifier.align(Alignment.BottomCenter).offset(y = (60 - 20 * a).dp).size(260.dp)
            .background(Brush.radialGradient(listOf(Color(0xFF16A9C7).copy(alpha = 0.45f), Color.Transparent))))
        content()
    }
}

@Composable
private fun AslLogo(size: Int = 52) {
    Box(
        Modifier.size(size.dp).shadow(10.dp, RoundedCornerShape((size * 0.3f).dp))
            .background(Brush.linearGradient(listOf(Color.White, Color(0xFFDDE2FF))), RoundedCornerShape((size * 0.3f).dp)),
        contentAlignment = Alignment.Center,
    ) { Text("ASL", style = AslTheme.type.label.copy(fontSize = AslTheme.type.label.fontSize * (size / 52f)), color = Color(0xFF16308C)) }
}

@Composable
private fun LanguagePill() {
    val langs = listOf("en" to "EN", "hi" to "हिं", "bn" to "বাং")
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().take(2).ifEmpty { "en" }
    Row(Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape).padding(3.dp)) {
        langs.forEach { (tag, label) ->
            val on = tag == current
            Text(
                label, style = AslTheme.type.micro, color = if (on) Color(0xFF16308C) else Color.White,
                modifier = Modifier.clip(CircleShape).background(if (on) Color.White else Color.Transparent)
                    .clickable(role = Role.Button) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

/** White card that overlaps the aurora header. */
@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = AslTheme.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).shadow(18.dp, RoundedCornerShape(22.dp))
            .background(c.surface, RoundedCornerShape(22.dp)).border(1.dp, c.outline, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun AuthScaffold(title: String, onBack: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding().keyboardAware()) {
        AslTopBar(title, onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/* ------------------------------------------------ Splash ------------------------------------------------ */

/**
 * Instant resolver that decides the first screen (Onboarding / Language / Login / Home) and routes to it.
 * It is not a splash: it shows only the themed background while the session is restored (a few milliseconds).
 */
@Composable
fun SplashScreen(onDestination: (StartDestination) -> Unit, vm: SplashViewModel = hiltViewModel()) {
    val dest by vm.destination.collectAsStateWithLifecycle()
    LaunchedEffect(dest) { dest?.let(onDestination) }
    Box(Modifier.fillMaxSize().background(AslTheme.colors.background))
}

/* ----------------------------------------------- Language ----------------------------------------------- */

@Composable
fun LanguageScreen(onDone: () -> Unit, vm: LanguageViewModel = hiltViewModel()) {
    val c = AslTheme.colors
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().take(2).ifEmpty { "en" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AuroraBox(Modifier.fillMaxWidth()) {
            Column(Modifier.statusBarsPadding().padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 64.dp)) {
                AslLogo()
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.lang_title), style = AslTheme.type.headline, color = Color.White)
                Text("ভাষা বেছে নিন · भाषा चुनें", style = AslTheme.type.caption, color = Color.White.copy(alpha = 0.85f))
            }
        }
        GlassCard(Modifier.offset(y = (-44).dp)) {
            ListCard {
                listOf(Triple("en", "English", "English"), Triple("hi", "हिन्दी", "Hindi"), Triple("bn", "বাংলা", "Bengali")).forEachIndexed { i, (tag, name, sub) ->
                    if (i > 0) Divider()
                    val on = tag == current
                    ListRow(name, subtitle = sub, icon = AslIcons.Globe, iconColors = if (on) AslGradients.action else null,
                        trailing = { if (on) Icon(AslIcons.Check, null, tint = c.primary, modifier = Modifier.size(22.dp)) },
                        onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) })
                }
            }
            Text(stringResource(R.string.lang_hint), style = AslTheme.type.caption, color = c.textSecondary)
            AslPrimaryButton(stringResource(R.string.common_continue), onClick = { vm.done(onDone) })
        }
    }
}

/* ------------------------------------------------ Login ------------------------------------------------- */

@Composable
fun LoginScreen(
    onOtp: () -> Unit,
    onSignedIn: (needsLockSetup: Boolean) -> Unit,
    onForgot: (prefill: String?) -> Unit,
    showSessionExpired: Boolean,
    onSessionExpiredShown: () -> Unit,
    vm: LoginViewModel = hiltViewModel(),
) {
    SecureScreen()
    val c = AslTheme.colors
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                AuthEvent.GoToOtp -> onOtp()
                is AuthEvent.SignedIn -> onSignedIn(e.needsLockSetup)
                else -> Unit
            }
        }
    }

    if (s.lockedMessage != null) {
        Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.Center) {
            StateView(AslIcons.Lock, StatePresets.errorColors, stringResource(R.string.locked_title), s.lockedMessage!!) {
                AslPrimaryButton(stringResource(R.string.locked_reset), onClick = { vm.dismissLocked(); onForgot(s.parsedId?.raw) })
                AslButton(stringResource(R.string.locked_back), onClick = vm::dismissLocked, style = AslButtonStyle.Outline, modifier = Modifier.fillMaxWidth())
            }
        }
        return
    }

    // Classic look: the old login (navy → blue, big logo, white panel, navy pill button, "Powered by").
    ClassicAuthBackground(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewport = maxHeight
            // The white panel always reaches the bottom edge, whatever the screen height.
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.4f).background(c.surface))
            Column(Modifier.fillMaxSize().keyboardAware().verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) { Spacer(Modifier.weight(1f)); LanguagePill() }
                LogoHero(124.dp, Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(10.dp))
                AuthSheet(Modifier.heightIn(min = viewport * 0.62f).navigationBarsPadding()) {
                    Column(Modifier.fillMaxWidth().riseIn(250), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.login_welcome_user), style = AslTheme.type.headline, color = c.primary, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.login_sub), style = AslTheme.type.body, color = c.textSecondary, textAlign = TextAlign.Center)
                    }
                    Column(Modifier.fillMaxWidth().shakeOn(s.error).riseIn(340), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AslTextField(
                            value = s.loginId, onValueChange = vm::onLoginIdChange, label = stringResource(R.string.login_id),
                            keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, onImeAction = vm::onLoginIdDone,
                            error = if (s.showIdError) stringResource(R.string.login_id_error) else null,
                        )
                        AslTextField(
                            value = s.password, onValueChange = vm::onPasswordChange, label = stringResource(R.string.login_password),
                            password = true, imeAction = ImeAction.Done, onImeAction = vm::submit,
                            error = s.error?.let { authErrorText(it) },
                        )
                        Text(
                            stringResource(R.string.login_forgot), style = AslTheme.type.label, color = c.primaryEnd,
                            modifier = Modifier.align(Alignment.End).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { onForgot(s.parsedId?.raw) }.padding(6.dp),
                        )
                    }
                    PillButton(
                        stringResource(R.string.login_sign_in), onClick = vm::submit, enabled = s.canSubmit || s.loading, loading = s.loading,
                        modifier = Modifier.align(Alignment.CenterHorizontally).riseIn(430),
                    )
                    Row(Modifier.fillMaxWidth().riseIn(500), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(AslIcons.Lock, null, tint = c.textSecondary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.login_secure), style = AslTheme.type.caption, color = c.textSecondary)
                    }
                    // Room for the "Powered by" strip pinned at the bottom of the screen.
                    Spacer(Modifier.height(96.dp))
                }
            }
            // As on the old login: "Powered by" and the partners sit at the very bottom of the screen.
            PoweredBy(Modifier.align(Alignment.BottomCenter).background(c.surface).navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 14.dp).riseIn(600))
        }
    }

    if (showSessionExpired) {
        AlertDialog(
            onDismissRequest = onSessionExpiredShown,
            icon = { BigIcon(AslIcons.Lock, AslGradients.pending, size = 56.dp) },
            title = { Text(stringResource(R.string.expired_title), style = AslTheme.type.title, textAlign = TextAlign.Center) },
            text = { Text(stringResource(R.string.expired_body), style = AslTheme.type.body, textAlign = TextAlign.Center) },
            confirmButton = { AslButton(stringResource(R.string.expired_ok), onClick = onSessionExpiredShown, compact = true) },
            containerColor = c.surface,
        )
    }
}

/* ------------------------------------------------- OTP -------------------------------------------------- */

@Composable
fun OtpScreen(onSignedIn: (needsLockSetup: Boolean) -> Unit, onBack: () -> Unit, vm: OtpViewModel = hiltViewModel()) {
    SecureScreen()
    val c = AslTheme.colors
    val s by vm.state.collectAsStateWithLifecycle()
    val leave = remember { { vm.cancel(); onBack() } }
    BackHandler(onBack = leave)
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is AuthEvent.SignedIn -> onSignedIn(e.needsLockSetup)
                AuthEvent.OtpExpired -> leave()
                else -> Unit
            }
        }
    }
    // Classic look: the old "Verify OTP" panel on the navy → blue page, with the logo above it.
    ClassicAuthBackground(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewport = maxHeight
            // The white panel always reaches the bottom edge, whatever the screen height.
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.4f).background(c.surface))
            Column(Modifier.fillMaxSize().keyboardAware().verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassBack(leave, stringResource(R.string.common_back))
                    Text(stringResource(R.string.otp_title), style = AslTheme.type.title, color = Color.White, modifier = Modifier.padding(start = 12.dp).semantics { heading() })
                }
                LogoHero(92.dp, Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(10.dp))
                AuthSheet(Modifier.heightIn(min = viewport * 0.66f).navigationBarsPadding()) {
                    Column(Modifier.fillMaxWidth().riseIn(250), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.otp_verify_title), style = AslTheme.type.headline, color = c.primary)
                        Text(stringResource(R.string.otp_enter), style = AslTheme.type.bodyStrong, color = c.textPrimary, textAlign = TextAlign.Center)
                        Text(stringResource(R.string.otp_sent), style = AslTheme.type.body, color = c.textSecondary, textAlign = TextAlign.Center)
                    }
                    Box(Modifier.fillMaxWidth().shakeOn(s.error).riseIn(340)) {
                        OtpInput(s.otp, vm::onOtpChange, length = OTP_LENGTH, isError = s.error != null)
                    }
                    val msg = when {
                        s.coolDown > 0 -> stringResource(R.string.otp_cooldown, s.coolDown)
                        s.error is AuthError.Server && s.triesLeft in 1 until OTP_TRIES -> stringResource(R.string.otp_tries_left, s.triesLeft)
                        s.error != null -> authErrorText(s.error!!)
                        s.resent -> stringResource(R.string.otp_resent)
                        else -> null
                    }
                    if (msg != null) Text(msg, style = AslTheme.type.label, color = if (s.error != null || s.coolDown > 0) c.error else c.success, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    ResendRow(s.resendIn, vm::resend, Modifier.align(Alignment.CenterHorizontally).riseIn(420))
                    PillButton(
                        stringResource(R.string.otp_verify), onClick = vm::verify, enabled = s.otp.length == OTP_LENGTH && s.coolDown == 0, loading = s.loading,
                        modifier = Modifier.align(Alignment.CenterHorizontally).riseIn(480),
                    )
                    Spacer(Modifier.height(4.dp))
                    AlertBanner(AlertKind.Info, stringResource(R.string.otp_never_share), message = stringResource(R.string.otp_never_share_sub), icon = AslIcons.Lock, modifier = Modifier.riseIn(560))
                }
            }
        }
    }
}

/** A ring empties as the resend timer runs; at zero it becomes the "Resend OTP" link. */
@Composable
private fun ResendRow(resendIn: Int, onResend: () -> Unit, modifier: Modifier = Modifier) {
    val c = AslTheme.colors
    if (resendIn > 0) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val total = remember { maxOf(resendIn, 30) }
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(
                    progress = { resendIn / total.toFloat() }, modifier = Modifier.size(28.dp), color = c.primaryEnd, trackColor = c.tint, strokeWidth = 3.dp,
                )
                Text("$resendIn", style = AslTheme.type.micro.copy(fontSize = AslTheme.type.micro.fontSize * 0.85f), color = c.primary)
            }
            Text(stringResource(R.string.otp_resend_ring, resendIn), style = AslTheme.type.caption, color = c.textSecondary)
        }
    } else {
        Text(
            stringResource(R.string.otp_resend), style = AslTheme.type.label, color = c.primaryEnd,
            modifier = modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onResend).padding(8.dp),
        )
    }
}

/* ------------------------------------------- Forgot password -------------------------------------------- */

@Composable
fun ForgotPasswordScreen(prefill: String?, onDone: () -> Unit, onBack: () -> Unit, vm: ForgotPasswordViewModel = hiltViewModel()) {
    SecureScreen()
    val c = AslTheme.colors
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(prefill) { vm.prefill(prefill) }
    LaunchedEffect(Unit) { vm.events.collect { if (it == AuthEvent.PasswordReset) onDone() } }
    val back = { if (s.step == ForgotStep.Reset) vm.backToMobile() else onBack() }
    BackHandler(onBack = back)

    AuthScaffold(stringResource(R.string.forgot_title), onBack = back) {
        StepBar(listOf(stringResource(R.string.forgot_step_mobile), stringResource(R.string.forgot_step_new)), if (s.step == ForgotStep.Mobile) 0 else 1)
        if (s.step == ForgotStep.Mobile) {
            Text(stringResource(R.string.forgot_enter_mobile), style = AslTheme.type.title, color = c.textPrimary)
            Text(stringResource(R.string.forgot_send_hint), style = AslTheme.type.body, color = c.textSecondary)
            AslTextField(s.mobile, vm::onMobile, stringResource(R.string.forgot_mobile), keyboardType = KeyboardType.Phone, maxLength = 10,
                imeAction = ImeAction.Done, onImeAction = vm::sendCode, error = s.error?.let { authErrorText(it) })
            AslPrimaryButton(stringResource(R.string.forgot_send), onClick = vm::sendCode, enabled = s.mobileValid || s.loading, loading = s.loading)
        } else {
            Text(stringResource(R.string.forgot_code_sent), style = AslTheme.type.caption, color = c.textSecondary)
            OtpInput(s.otp, vm::onOtp, length = OTP_LENGTH, isError = s.error != null)
            if (s.resendIn > 0) {
                Text(stringResource(R.string.otp_resend_in, "0:" + s.resendIn.toString().padStart(2, '0')), style = AslTheme.type.caption, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            } else {
                Text(stringResource(R.string.otp_resend), style = AslTheme.type.label, color = c.primary, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = vm::sendCode).padding(8.dp))
            }
            AslTextField(s.newPassword, vm::onNewPassword, stringResource(R.string.forgot_new), password = true)
            if (s.newPassword.isNotEmpty()) StrengthMeter(s.strength)
            Text(stringResource(R.string.forgot_rules), style = AslTheme.type.caption, color = c.textSecondary)
            AslTextField(s.confirmPassword, vm::onConfirm, stringResource(R.string.forgot_confirm), password = true, imeAction = ImeAction.Done, onImeAction = vm::save,
                success = if (s.passwordsMatch) stringResource(R.string.forgot_match) else null,
                error = if (s.confirmPassword.isNotEmpty() && !s.passwordsMatch) stringResource(R.string.forgot_no_match) else s.error?.let { authErrorText(it) })
            AslPrimaryButton(stringResource(R.string.forgot_save), onClick = vm::save, enabled = s.canSave || s.loading, loading = s.loading)
        }
    }
}

@Composable
private fun StepBar(steps: List<String>, current: Int) {
    val c = AslTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.forEachIndexed { i, label ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).background(
                    when { i < current -> Brush.linearGradient(AslGradients.success); i == current -> Brush.linearGradient(AslGradients.action); else -> Brush.linearGradient(listOf(c.outline, c.outline)) },
                ))
                Text(label, style = AslTheme.type.micro, color = if (i == current) c.primary else c.textSecondary)
            }
        }
    }
}

@Composable
private fun StrengthMeter(strength: PasswordStrength) {
    val c = AslTheme.colors
    val (fraction, color, label) = when (strength) {
        PasswordStrength.TooShort, PasswordStrength.Weak -> Triple(0.3f, c.error, stringResource(R.string.strength_weak))
        PasswordStrength.Fair -> Triple(0.65f, c.pending, stringResource(R.string.strength_fair))
        PasswordStrength.Strong -> Triple(1f, c.success, stringResource(R.string.strength_strong))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(7.dp)).background(c.outline)) {
            Box(Modifier.fillMaxWidth(fraction).height(7.dp).background(color, RoundedCornerShape(7.dp)))
        }
        Text(label, style = AslTheme.type.label, color = color)
    }
}

@Composable
fun PasswordChangedScreen(onGoToLogin: () -> Unit) {
    BackHandler(onBack = onGoToLogin)
    Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.Center) {
        StateView(AslIcons.Check, StatePresets.okColors, stringResource(R.string.changed_title), stringResource(R.string.changed_body)) {
            AslPrimaryButton(stringResource(R.string.changed_go), onClick = onGoToLogin)
        }
    }
}

/* -------------------------------------------- App lock setup -------------------------------------------- */

@Composable
fun AppLockSetupScreen(allowSkip: Boolean, onDone: () -> Unit, vm: AppLockSetupViewModel = hiltViewModel()) {
    val c = AslTheme.colors
    val context = LocalContext.current
    val activity = context.findActivity() as? FragmentActivity
    val secure = SystemUnlock.isDeviceSecure(context)
    val title = stringResource(R.string.lock_setup_confirm)
    val sub = stringResource(R.string.lock_setup_confirm_sub)
    BackHandler { /* must finish setup */ }

    AuthScaffold(stringResource(R.string.lock_setup_title), onBack = null) {
        BigIcon(AslIcons.Shield, AslGradients.action, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(stringResource(R.string.lock_setup_head), style = AslTheme.type.title, color = c.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.lock_setup_sub), style = AslTheme.type.body, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        ListCard {
            ListRow(stringResource(R.string.lock_setup_b1), subtitle = stringResource(R.string.lock_setup_b1s), icon = AslIcons.Fingerprint, iconColors = AslGradients.Service.Upi.colors, trailing = null)
            Divider()
            ListRow(stringResource(R.string.lock_setup_b2), subtitle = stringResource(R.string.lock_setup_b2s), icon = AslIcons.Lock, iconColors = AslGradients.Service.Aeps.colors, trailing = null)
            Divider()
            ListRow(stringResource(R.string.lock_setup_b3), subtitle = stringResource(R.string.lock_setup_b3s), icon = AslIcons.Shield, iconColors = AslGradients.Service.Bills.colors, trailing = null)
        }
        if (secure) {
            AslPrimaryButton(stringResource(R.string.lock_setup_cta), icon = AslIcons.Lock, onClick = {
                activity?.let { act -> SystemUnlock.prompt(act, title, sub) { r -> if (r == UnlockResult.Success) vm.done(onDone) } }
            })
        } else {
            AlertBanner(AlertKind.Pending, stringResource(R.string.lock_setup_none_title), message = stringResource(R.string.lock_setup_none_body))
            AslPrimaryButton(stringResource(R.string.lock_setup_open_settings), icon = AslIcons.Lock, onClick = {
                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
        }
        if (allowSkip) AslButton(stringResource(R.string.lock_setup_skip_uat), onClick = { vm.done(onDone) }, style = AslButtonStyle.Outline, modifier = Modifier.fillMaxWidth())
    }
}
