package com.asl.wallet.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asl.wallet.core.designsystem.R

/* ---------------------------------------------------------------------------------------------
 * Design tokens from the approved Module 0 design system (design/asl_wallet_m0_design_system_v1.html).
 * Screens must use these tokens only: no hex colours or raw font sizes in feature code.
 * ------------------------------------------------------------------------------------------- */

@Immutable
data class AslColors(
    val brandNavy: Color,
    val primary: Color,
    val primaryEnd: Color,
    val background: Color,
    val surface: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val tint: Color,
    val success: Color, val successBg: Color,
    val pending: Color, val pendingBg: Color,
    val error: Color, val errorBg: Color,
    val heroGradient: List<Color>,
    val isDark: Boolean,
)

val LightAslColors = AslColors(
    brandNavy = Color(0xFF03274B), primary = Color(0xFF494FE6), primaryEnd = Color(0xFF8B5CFF),
    background = Color(0xFFF3F5FC), surface = Color(0xFFFFFFFF), outline = Color(0xFFE4E8F3),
    textPrimary = Color(0xFF0D1830), textSecondary = Color(0xFF5A6882), tint = Color(0xFFEEEFFF),
    success = Color(0xFF138A4B), successBg = Color(0xFFE3F5EB),
    pending = Color(0xFFA86200), pendingBg = Color(0xFFFFF2DC),
    error = Color(0xFFC62828), errorBg = Color(0xFFFDE7E7),
    heroGradient = listOf(Color(0xFF03274B), Color(0xFF16308C), Color(0xFF494FE6), Color(0xFF7B5CFF)),
    isDark = false,
)

val DarkAslColors = AslColors(
    brandNavy = Color(0xFF0E2340), primary = Color(0xFF8A8FFF), primaryEnd = Color(0xFFA38BFF),
    background = Color(0xFF070D19), surface = Color(0xFF111C30), outline = Color(0xFF21304A),
    textPrimary = Color(0xFFE8EEF8), textSecondary = Color(0xFF93A2BA), tint = Color(0xFF1B2452),
    success = Color(0xFF3CC27A), successBg = Color(0xFF0F2E22),
    pending = Color(0xFFF2B44B), pendingBg = Color(0xFF33270F),
    error = Color(0xFFFF6B6B), errorBg = Color(0xFF371616),
    heroGradient = listOf(Color(0xFF0B1A3A), Color(0xFF1A2C7A), Color(0xFF4A46D8), Color(0xFF7452F0)),
    isDark = true,
)

/** Gradients are for brand, action and celebration surfaces only; data and forms stay on flat surfaces. */
object AslGradients {
    val action = listOf(Color(0xFF494FE6), Color(0xFF6A55FF), Color(0xFF8B5CFF))
    val earnings = listOf(Color(0xFFFFC861), Color(0xFFFF8A3D))
    val success = listOf(Color(0xFF0F9D58), Color(0xFF34D399))
    val pending = listOf(Color(0xFFD97706), Color(0xFFFBBF24))
    val failed = listOf(Color(0xFFDC2626), Color(0xFFFB7185))
    val onEarnings = Color(0xFF3A1E00)

    /** One colour per service so retailers recognise services by colour first. */
    enum class Service(val colors: List<Color>) {
        Aeps(listOf(Color(0xFF494FE6), Color(0xFF8B5CFF))),
        MicroAtm(listOf(Color(0xFF0EA5A4), Color(0xFF22D3EE))),
        SendMoney(listOf(Color(0xFFFF5A7A), Color(0xFFFF9A6B))),
        Upi(listOf(Color(0xFF0F9D58), Color(0xFF4ADE80))),
        Recharge(listOf(Color(0xFFF59E0B), Color(0xFFFBBF24))),
        Bills(listOf(Color(0xFF2563EB), Color(0xFF38BDF8))),
        AadhaarPay(listOf(Color(0xFFC026D3), Color(0xFFF472B6))),
        Neutral(listOf(Color(0xFF334155), Color(0xFF64748B))),
        Wallet(listOf(Color(0xFF7C3AED), Color(0xFFC084FC))),
        Qr(listOf(Color(0xFF0891B2), Color(0xFF67E8F9))),
    }

    fun brush(colors: List<Color>): Brush = Brush.linearGradient(colors)
}

val NotoSans = FontFamily(
    Font(R.font.noto_sans_regular, FontWeight.Normal),
    Font(R.font.noto_sans_medium, FontWeight.Medium),
    Font(R.font.noto_sans_semibold, FontWeight.SemiBold),
    Font(R.font.noto_sans_bold, FontWeight.Bold),
    Font(R.font.noto_sans_extrabold, FontWeight.ExtraBold),
    Font(R.font.noto_sans_black, FontWeight.Black),
)

/** Type scale (sp scales with the phone's font setting). Minimum anywhere is [micro] = 11.5 sp. */
@Immutable
data class AslType(
    val amountDisplay: TextStyle = style(32, FontWeight.Black, -0.4),
    val amountWallet: TextStyle = style(28, FontWeight.Black, -0.4),
    val headline: TextStyle = style(22, FontWeight.ExtraBold),
    val title: TextStyle = style(18, FontWeight.ExtraBold),
    val bodyStrong: TextStyle = style(16, FontWeight.Bold),
    val body: TextStyle = style(14, FontWeight.Medium),
    val label: TextStyle = style(13, FontWeight.Bold),
    val caption: TextStyle = style(12, FontWeight.SemiBold),
    val micro: TextStyle = style(11.5, FontWeight.ExtraBold),
)

private fun style(size: Number, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = NotoSans,
    fontWeight = weight,
    fontSize = size.toFloat().sp,
    lineHeight = (size.toFloat() * 1.35f).sp,
    letterSpacing = tracking.toFloat().sp,
)

object AslShapes {
    val chip = RoundedCornerShape(12.dp)
    val field = RoundedCornerShape(14.dp)
    val button = RoundedCornerShape(16.dp)
    val card = RoundedCornerShape(18.dp)
    val hero = RoundedCornerShape(22.dp)
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val tile = RoundedCornerShape(17.dp)
    val pill = RoundedCornerShape(50)
}

object AslSpacing {
    val xxs = 4.dp; val xs = 8.dp; val sm = 12.dp; val md = 16.dp; val lg = 20.dp; val xl = 24.dp; val xxl = 32.dp
    val gutter = 16.dp
    val minTouch = 48.dp
}

object AslMotion {
    const val MICRO = 120
    const val STANDARD = 280
    const val EMPHASIS = 450
    val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
}

private val LocalAslColors = staticCompositionLocalOf { LightAslColors }
private val LocalAslType = staticCompositionLocalOf { AslType() }

object AslTheme {
    val colors: AslColors @Composable get() = LocalAslColors.current
    val type: AslType @Composable get() = LocalAslType.current
}

enum class ThemeMode { Light, Dark, System }

@Composable
fun AslTheme(mode: ThemeMode = ThemeMode.Light, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val c = if (dark) DarkAslColors else LightAslColors
    val type = AslType()
    val scheme = if (dark) {
        darkColorScheme(primary = c.primary, background = c.background, surface = c.surface, onSurface = c.textPrimary,
            onBackground = c.textPrimary, outline = c.outline, error = c.error, secondary = c.primaryEnd)
    } else {
        lightColorScheme(primary = c.primary, background = c.background, surface = c.surface, onSurface = c.textPrimary,
            onBackground = c.textPrimary, outline = c.outline, error = c.error, secondary = c.primaryEnd)
    }
    val m3Type = Typography(
        headlineSmall = type.headline, titleLarge = type.title, titleMedium = type.bodyStrong,
        bodyLarge = type.bodyStrong, bodyMedium = type.body, labelLarge = type.label, labelMedium = type.caption,
        bodySmall = type.caption, labelSmall = type.micro,
    )
    CompositionLocalProvider(LocalAslColors provides c, LocalAslType provides type) {
        MaterialTheme(colorScheme = scheme, typography = m3Type, content = content)
    }
}
