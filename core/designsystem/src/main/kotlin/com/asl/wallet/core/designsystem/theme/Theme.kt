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

/**
 * Fill for selected / active marks that carry white content (check badges, the chosen chip, the active step):
 * brand navy in light mode, a brighter blue in dark mode where navy would vanish into the background.
 */
val AslColors.accentFill: Color get() = if (isDark) Color(0xFF1E6FC0) else brandNavy

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
    /** Classic look: the old app's service-screen background and toolbar (navy → blue). */
    val headerGradient: List<Color>,
    /** Classic look: Home's top band (old dashboard blue). */
    val homeGradient: List<Color>,
    /** Classic look: Home's grey rounded sheet. */
    val sheet: Color,
    val isDark: Boolean,
)

/* Classic look (branch ui/classic-look): the old ASL Wallet app's palette, navy #03274B and blues #0C4DA2 → #1E88E5. */
val LightAslColors = AslColors(
    brandNavy = Color(0xFF03274B), primary = Color(0xFF03274B), primaryEnd = Color(0xFF1976D2),
    background = Color(0xFFEEF0F4), surface = Color(0xFFFFFFFF), outline = Color(0xFFDDE2EA),
    textPrimary = Color(0xFF0B1F38), textSecondary = Color(0xFF5B6576), tint = Color(0xFFE4F3FF),
    success = Color(0xFF1D7A3D), successBg = Color(0xFFE4F6EA),
    pending = Color(0xFFC77700), pendingBg = Color(0xFFFFF3E0),
    error = Color(0xFFC62828), errorBg = Color(0xFFFFEAEA),
    heroGradient = listOf(Color(0xFF03274B), Color(0xFF0C4DA2), Color(0xFF1565C0), Color(0xFF1E88E5)),
    headerGradient = listOf(Color(0xFF03274B), Color(0xFF1976D2)),
    homeGradient = listOf(Color(0xFF0C4DA2), Color(0xFF1565C0), Color(0xFF1E88E5)),
    sheet = Color(0xFFEDEDED),
    isDark = false,
)

val DarkAslColors = AslColors(
    brandNavy = Color(0xFF041A33), primary = Color(0xFF64A8F0), primaryEnd = Color(0xFF8CC2F7),
    background = Color(0xFF0A1220), surface = Color(0xFF131E2F), outline = Color(0xFF243247),
    textPrimary = Color(0xFFE7EDF6), textSecondary = Color(0xFF97A4B8), tint = Color(0xFF14304F),
    success = Color(0xFF4CC285), successBg = Color(0xFF10301F),
    pending = Color(0xFFF2B44B), pendingBg = Color(0xFF33270F),
    error = Color(0xFFFF6B6B), errorBg = Color(0xFF371616),
    heroGradient = listOf(Color(0xFF020F1F), Color(0xFF062E5C), Color(0xFF0B4A8F), Color(0xFF1360B0)),
    headerGradient = listOf(Color(0xFF020F1F), Color(0xFF0B4A8F)),
    homeGradient = listOf(Color(0xFF062E5C), Color(0xFF0B4A8F), Color(0xFF1360B0)),
    sheet = Color(0xFF0E1726),
    isDark = true,
)

/** Gradients are for brand, action and celebration surfaces only; data and forms stay on flat surfaces. */
object AslGradients {
    /** Main buttons: the old app's navy, shading to blue. */
    val action = listOf(Color(0xFF03274B), Color(0xFF0C4DA2), Color(0xFF1565C0))
    val earnings = listOf(Color(0xFFFFC861), Color(0xFFFF8A3D))
    val success = listOf(Color(0xFF0F9D58), Color(0xFF34D399))
    val pending = listOf(Color(0xFFD97706), Color(0xFFFBBF24))
    val failed = listOf(Color(0xFFDC2626), Color(0xFFFB7185))
    val onEarnings = Color(0xFF3A1E00)

    /** One colour per service so retailers recognise services by colour first. */
    enum class Service(val colors: List<Color>) {
        Aeps(listOf(Color(0xFF1565C0), Color(0xFF2979FF))),
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

/**
 * Gilroy, the old app's font (chosen 7 Nov 2026; ASL must hold a mobile-app licence for it). It has Latin only:
 * Hindi and Bengali text falls back to the phone's own fonts automatically.
 */
/** Poppins: the old app's font for section titles, tiles, buttons and body text (Gilroy is for headings and amounts). */
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_bold, FontWeight.ExtraBold),
    Font(R.font.poppins_bold, FontWeight.Black),
)

val Gilroy = FontFamily(
    Font(R.font.gilroy_light, FontWeight.Light),
    Font(R.font.gilroy_regular, FontWeight.Normal),
    Font(R.font.gilroy_medium, FontWeight.Medium),
    Font(R.font.gilroy_semi_bold, FontWeight.SemiBold),
    Font(R.font.gilroy_bold, FontWeight.Bold),
    Font(R.font.gilroy_heavy, FontWeight.ExtraBold),
    Font(R.font.gilroy_heavy, FontWeight.Black),
)

/** Type scale (sp scales with the phone's font setting). Minimum anywhere is [micro] = 11.5 sp. */
@Immutable
data class AslType(
    // As in the old app: Gilroy for headings and amounts, Poppins for everything else.
    val amountDisplay: TextStyle = style(32, FontWeight.Bold, 0.0),
    val amountWallet: TextStyle = style(28, FontWeight.Bold, 0.0),
    val headline: TextStyle = style(22, FontWeight.Bold, 0.4),
    val title: TextStyle = style(18, FontWeight.Bold, 0.5),
    val bodyStrong: TextStyle = style(16, FontWeight.Bold, 0.4),
    val body: TextStyle = style(14, FontWeight.Normal, font = Poppins),
    val label: TextStyle = style(13, FontWeight.Medium, font = Poppins),
    val caption: TextStyle = style(12, FontWeight.Normal, font = Poppins),
    val micro: TextStyle = style(11.5, FontWeight.Medium, font = Poppins),
)

private fun style(size: Number, weight: FontWeight, tracking: Double = 0.0, font: FontFamily = Gilroy) = TextStyle(
    fontFamily = font,
    fontWeight = weight,
    fontSize = size.toFloat().sp,
    lineHeight = (size.toFloat() * 1.35f).sp,
    letterSpacing = tracking.toFloat().sp,
)

object AslShapes {
    val chip = RoundedCornerShape(10.dp)
    val field = RoundedCornerShape(10.dp)
    val button = RoundedCornerShape(12.dp)
    val card = RoundedCornerShape(12.dp)
    val hero = RoundedCornerShape(16.dp)
    val sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val tile = RoundedCornerShape(14.dp)
    /** The old app's toolbar: square top, rounded bottom. */
    val header = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
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
