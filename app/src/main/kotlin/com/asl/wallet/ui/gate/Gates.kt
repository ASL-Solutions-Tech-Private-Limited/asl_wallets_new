package com.asl.wallet.ui.gate

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.asl.wallet.R
import com.asl.wallet.core.designsystem.component.AslButton
import com.asl.wallet.core.designsystem.component.AslButtonStyle
import com.asl.wallet.core.designsystem.component.AslPrimaryButton
import com.asl.wallet.core.designsystem.component.BigIcon
import com.asl.wallet.core.designsystem.component.StatePresets
import com.asl.wallet.core.designsystem.component.StateView
import com.asl.wallet.core.designsystem.icon.AslIcons
import com.asl.wallet.core.designsystem.theme.AslGradients
import com.asl.wallet.core.designsystem.theme.AslTheme
import com.asl.wallet.core.security.integrity.IntegrityIssue
import com.asl.wallet.core.security.lock.SystemUnlock
import com.asl.wallet.core.security.lock.UnlockResult
import com.asl.wallet.core.security.ui.SecureScreen
import com.asl.wallet.core.security.ui.findActivity

/** Shown whenever the app lock engages. Opens the system prompt automatically. */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    SecureScreen()
    val context = LocalContext.current
    val activity = context.findActivity() as? FragmentActivity
    val secure = remember { SystemUnlock.isDeviceSecure(context) }
    var message by remember { mutableStateOf<String?>(null) }
    val title = stringResource(R.string.unlock_prompt_title)
    val subtitle = stringResource(R.string.unlock_prompt_subtitle)

    fun prompt() {
        val act = activity ?: return
        SystemUnlock.prompt(act, title, subtitle) { r ->
            when (r) {
                UnlockResult.Success -> onUnlocked()
                UnlockResult.Cancelled -> message = null
                is UnlockResult.Error -> message = r.message
            }
        }
    }
    LaunchedEffect(secure) { if (secure) prompt() }

    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF06163A), Color(0xFF16308C), Color(0xFF494FE6)))).systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BigIcon(AslIcons.Lock, AslGradients.action, size = 76.dp)
            if (secure) {
                Text(stringResource(R.string.unlock_title), style = AslTheme.type.headline, color = Color.White, textAlign = TextAlign.Center)
                if (message != null) Text(message!!, style = AslTheme.type.body, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
                AslPrimaryButton(stringResource(R.string.unlock_button), onClick = ::prompt, icon = AslIcons.Fingerprint)
            } else {
                Text(stringResource(R.string.no_screen_lock_title), style = AslTheme.type.headline, color = Color.White, textAlign = TextAlign.Center)
                Text(stringResource(R.string.no_screen_lock_body), style = AslTheme.type.body, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
                AslPrimaryButton(stringResource(R.string.open_settings), onClick = {
                    context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
            }
        }
    }
}

/** Blocks payments on rooted/tampered phones. UAT builds can continue for testing; Live cannot. */
@Composable
fun UnsafeDeviceScreen(issues: Set<IntegrityIssue>, allowBypass: Boolean, onBypass: () -> Unit) {
    val c = AslTheme.colors
    Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.Center) {
        StateView(
            icon = AslIcons.Alert,
            iconColors = StatePresets.errorColors,
            title = stringResource(R.string.unsafe_title),
            message = stringResource(R.string.unsafe_body) + "\n\n" + issues.joinToString(" · ") { it.name },
        ) {
            AslButton(stringResource(R.string.contact_support), onClick = {}, style = AslButtonStyle.Outline, modifier = Modifier.padding(0.dp))
            if (allowBypass) {
                AslButton(stringResource(R.string.continue_uat), onClick = onBypass, style = AslButtonStyle.Danger)
                Text("This button doesn't exist in Live builds.", style = AslTheme.type.caption, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
