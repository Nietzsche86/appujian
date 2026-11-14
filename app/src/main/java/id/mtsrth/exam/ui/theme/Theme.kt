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
    bannerGradientEnd = Color(0xFF0B6E4F),
    coolAlabaster = Color(0xFFF4F7F6),
    pureCrispWhite = Color(0xFFFFFFFF),
    tonalSlateSurface = Color(0xFFEBF0EE),
    subtleDivider = Color(0xFFE2E8F0),
    inkSlate = Color(0xFF0F172A),
    slateSecondary = Color(0xFF475569),
    slateMuted = Color(0xFF94A3B8),
    vibrantTeal = Color(0xFF10B981),
    vibrantTealSoftBg = Color(0xFFECFDF5),
    coralRed = Color(0xFFDC2626),
    coralRedSoftBg = Color(0xFFFEF2F2),
    coralRedSoftBorder = Color(0xFFFECACA),
    coralSolidAction = Color(0xFFDC2626),
    warmAmber = Color(0xFFD97706),
    warmAmberSoftBg = Color(0xFFFFFBEB),
    warmAmberSoftBorder = Color(0xFFFDE68A),
    shimmerBase = Color(0xFFEBF0EE),
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
    bannerGradientStart = Color(0xFF0B2B22),
    bannerGradientEnd = Color(0xFF134839),
    coolAlabaster = Color(0xFF0C1412),
    pureCrispWhite = Color(0xFF14201D),
    tonalSlateSurface = Color(0xFF1D2D29),
    subtleDivider = Color(0xFF283C36),
    inkSlate = Color(0xFFE6EFEA),
    slateSecondary = Color(0xFFA0B3AC),
    slateMuted = Color(0xFF6E827B),
    vibrantTeal = Color(0xFF34D399),
    vibrantTealSoftBg = Color(0xFF112922),
    coralRed = Color(0xFFF87171),
    coralRedSoftBg = Color(0xFF2B181A),
    coralRedSoftBorder = Color(0xFF54282C),
    coralSolidAction = Color(0xFFDC2626),
    warmAmber = Color(0xFFFBBF24),
    warmAmberSoftBg = Color(0xFF282012),
    warmAmberSoftBorder = Color(0xFF52401E),
    shimmerBase = Color(0xFF1D2D29),
    shimmerHighlight = Color(0xFF293E38)
)

val LocalPrestigeColors = staticCompositionLocalOf { LightPrestigePalette }

object PrestigeTheme {
    val colors: PrestigeColorPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalPrestigeColors.current
}

val PlusJakartaFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans, FontWeight.ExtraBold)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
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
    background = Color(0xFFF4F7F6),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEBF0EE),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFEBF0EE)
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
    background = Color(0xFF0C1412),
    onBackground = Color(0xFFE6EFEA),
    surface = Color(0xFF14201D),
    onSurface = Color(0xFFE6EFEA),
    surfaceVariant = Color(0xFF1D2D29),
    onSurfaceVariant = Color(0xFFA0B3AC),
    outline = Color(0xFF283C36),
    outlineVariant = Color(0xFF1D2D29)
)

val ExamTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    titleSmall = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp,
        lineHeight = 15.sp
    ),
    labelSmall = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    )
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
