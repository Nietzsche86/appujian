package id.mtsrth.exam.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.mtsrth.exam.R

/**
 * Semantic color palette supporting both Light and Dark modes
 * following the 60-30-10 Modern Emerald & Slate Prestige system.
 */
@Immutable
data class PrestigeColorPalette(
    val isDark: Boolean,
    // 30% Primary Brand
    val deepEmerald: Color,
    val darkForest: Color,
    val softMintContainer: Color,
    val softMintBorder: Color,
    val bannerGradientStart: Color,
    val bannerGradientEnd: Color,
    // 60% Neutral Surface & Ink
    val coolAlabaster: Color,
    val pureCrispWhite: Color,
    val tonalSlateSurface: Color,
    val subtleDivider: Color,
    val cardBorder: Color,
    val inkSlate: Color,
    val slateSecondary: Color,
    val slateMuted: Color,
    // 10% Semantic Status
    val vibrantTeal: Color,
    val vibrantTealSoftBg: Color,
    val coralRed: Color,
    val coralRedSoftBg: Color,
    val coralRedSoftBorder: Color,
    val coralSolidAction: Color,
    val warmAmber: Color,
    val warmAmberSoftBg: Color,
    val warmAmberSoftBorder: Color,
    // Shimmer
    val shimmerBase: Color,
    val shimmerHighlight: Color
)

val LightPrestigePalette = PrestigeColorPalette(
    isDark = false,
    deepEmerald = Color(0xFF0B6E4F),
    darkForest = Color(0xFF064E3B),
    softMintContainer = Color(0xFFE6F4EF),
    softMintBorder = Color(0xFFA7F3D0),
    bannerGradientStart = Color(0xFF064E3B),
    bannerGradientEnd = Color(0xFF0E7C5B),
    coolAlabaster = Color(0xFFF3F6F5),
    pureCrispWhite = Color(0xFFFFFFFF),
    tonalSlateSurface = Color(0xFFEAF0EE),
    subtleDivider = Color(0xFFE2E8F0),
    cardBorder = Color(0xFFDCE5E2),
    inkSlate = Color(0xFF0F172A),
    slateSecondary = Color(0xFF475569),
    slateMuted = Color(0xFF8395A7),
    vibrantTeal = Color(0xFF10B981),
    vibrantTealSoftBg = Color(0xFFECFDF5),
    coralRed = Color(0xFFDC2626),
    coralRedSoftBg = Color(0xFFFEF2F2),
    coralRedSoftBorder = Color(0xFFFECACA),
    coralSolidAction = Color(0xFFDC2626),
    warmAmber = Color(0xFFD97706),
    warmAmberSoftBg = Color(0xFFFFFBEB),
    warmAmberSoftBorder = Color(0xFFFDE68A),
    shimmerBase = Color(0xFFE7EEEC),
    shimmerHighlight = Color(0xFFFFFFFF)
)

/**
 * Natural, eye-comfortable Dark Palette (Deep Forest Slate & Soft Luminous Emerald).
 * Avoids harsh #000000 pure black or neon glare for comfortable reading.
 */
val DarkPrestigePalette = PrestigeColorPalette(
    isDark = true,
    deepEmerald = Color(0xFF28B886),
    darkForest = Color(0xFF6EE7B7),
    softMintContainer = Color(0xFF17362D),
    softMintBorder = Color(0xFF2B6B56),
    bannerGradientStart = Color(0xFF0A2920),
    bannerGradientEnd = Color(0xFF145240),
    coolAlabaster = Color(0xFF0B1311),
    pureCrispWhite = Color(0xFF131F1C),
    tonalSlateSurface = Color(0xFF1B2B27),
    subtleDivider = Color(0xFF263B35),
    cardBorder = Color(0xFF243832),
    inkSlate = Color(0xFFE8F1EC),
    slateSecondary = Color(0xFFA0B4AD),
    slateMuted = Color(0xFF728780),
    vibrantTeal = Color(0xFF34D399),
    vibrantTealSoftBg = Color(0xFF112922),
    coralRed = Color(0xFFF87171),
    coralRedSoftBg = Color(0xFF2B181A),
    coralRedSoftBorder = Color(0xFF54282C),
    coralSolidAction = Color(0xFFDC2626),
    warmAmber = Color(0xFFFBBF24),
    warmAmberSoftBg = Color(0xFF282012),
    warmAmberSoftBorder = Color(0xFF52401E),
    shimmerBase = Color(0xFF1B2B27),
    shimmerHighlight = Color(0xFF283E38)
)

val LocalPrestigeColors = staticCompositionLocalOf { LightPrestigePalette }

object PrestigeTheme {
    val colors: PrestigeColorPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalPrestigeColors.current
}

/**
 * Static Regular-weight TTF font families.
 * Only register FontWeight.Normal so Compose/Android properly synthesizes
 * Bold & SemiBold optical weights instead of flattening all weights to 400.
 */
val OutfitDisplayFontFamily = FontFamily(
    Font(R.font.outfit, FontWeight.Normal)
)

val PlusJakartaFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal)
)

val ExamTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = OutfitDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = OutfitDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = OutfitDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = OutfitDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.5.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.1).sp
    ),
    titleSmall = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.5.sp,
        lineHeight = 19.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.5.sp,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.45.sp
    )
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0B6E4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6F4EF),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = Color(0xFF10B981),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6F4EF),
    onSecondaryContainer = Color(0xFF0B6E4F),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFFBEB),
    onTertiaryContainer = Color(0xFF78350F),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEF2F2),
    onErrorContainer = Color(0xFFDC2626),
    background = Color(0xFFF3F6F5),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEAF0EE),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFDCE5E2),
    outlineVariant = Color(0xFFEAF0EE)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF28B886),
    onPrimary = Color(0xFF05241A),
    primaryContainer = Color(0xFF17362D),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF05241A),
    secondaryContainer = Color(0xFF17362D),
    onSecondaryContainer = Color(0xFF6EE7B7),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF2B1C04),
    tertiaryContainer = Color(0xFF282012),
    onTertiaryContainer = Color(0xFFFDE68A),
    error = Color(0xFFF87171),
    onError = Color(0xFF3B0C0E),
    errorContainer = Color(0xFF2B181A),
    onErrorContainer = Color(0xFFFECACA),
    background = Color(0xFF0B1311),
    onBackground = Color(0xFFE8F1EC),
    surface = Color(0xFF131F1C),
    onSurface = Color(0xFFE8F1EC),
    surfaceVariant = Color(0xFF1B2B27),
    onSurfaceVariant = Color(0xFFA0B4AD),
    outline = Color(0xFF263B35),
    outlineVariant = Color(0xFF1B2B27)
)

val ExamShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun ExamKioskTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkPrestigePalette else LightPrestigePalette
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalPrestigeColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ExamTypography,
            shapes = ExamShapes,
            content = content
        )
    }
}
