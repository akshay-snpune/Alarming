package com.alarming.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.alarming.feature.settings.AppColorTheme

private val TealLightColors = lightColorScheme(
    primary = Color(0xFF245F70),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFB9E3E4),
    onSecondary = Color(0xFF173F47),
    tertiary = Color(0xFF5F6F9B),
    background = Color(0xFFF0F7F7),
    onBackground = Color(0xFF172F34),
    surface = Color(0xFFF9FDFD),
    onSurface = Color(0xFF172F34),
    surfaceVariant = Color(0xFFDEEDEF),
    onSurfaceVariant = Color(0xFF52676B),
    outline = Color(0xFF83999C),
)

private val TealDarkColors = darkColorScheme(
    primary = Color(0xFFA3D3DF),
    onPrimary = Color(0xFF103942),
    secondary = Color(0xFF94CFD0),
    onSecondary = Color(0xFF113B40),
    tertiary = Color(0xFFBEC8F3),
    background = Color(0xFF101D22),
    onBackground = Color(0xFFDCEBED),
    surface = Color(0xFF192B30),
    onSurface = Color(0xFFDCEBED),
    surfaceVariant = Color(0xFF293D42),
    onSurfaceVariant = Color(0xFFB5C9CB),
    outline = Color(0xFF789397),
)

private val LavenderLightColors = lightColorScheme(
    primary = Color(0xFF6553A4),
    onPrimary = Color.White,
    secondary = Color(0xFFDED2F4),
    onSecondary = Color(0xFF31244A),
    tertiary = Color(0xFF8961A8),
    background = Color(0xFFF8F5FC),
    onBackground = Color(0xFF21172B),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF21172B),
    surfaceVariant = Color(0xFFEAE2F0),
    onSurfaceVariant = Color(0xFF50465A),
    outline = Color(0xFF81758B),
)

private val LavenderDarkColors = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF35275A),
    secondary = Color(0xFF4C3D67),
    onSecondary = Color(0xFFE8DDFF),
    tertiary = Color(0xFFE2B8F4),
    background = Color(0xFF1B1724),
    onBackground = Color(0xFFEAE0F4),
    surface = Color(0xFF251F30),
    onSurface = Color(0xFFEAE0F4),
    surfaceVariant = Color(0xFF383143),
    onSurfaceVariant = Color(0xFFC9BED2),
    outline = Color(0xFF93889C),
)

private val RoseLightColors = lightColorScheme(
    primary = Color(0xFFA34463),
    onPrimary = Color.White,
    secondary = Color(0xFFF2CDD7),
    onSecondary = Color(0xFF4A1D2D),
    tertiary = Color(0xFF83506A),
    background = Color(0xFFFFF5F7),
    onBackground = Color(0xFF321A22),
    surface = Color(0xFFFFFBFC),
    onSurface = Color(0xFF321A22),
    surfaceVariant = Color(0xFFF1DFE4),
    onSurfaceVariant = Color(0xFF55434A),
    outline = Color(0xFF89747B),
)

private val RoseDarkColors = darkColorScheme(
    primary = Color(0xFFFFB1C4),
    onPrimary = Color(0xFF5B1129),
    secondary = Color(0xFF704052),
    onSecondary = Color(0xFFFFD9E2),
    tertiary = Color(0xFFE7B8CF),
    background = Color(0xFF25171C),
    onBackground = Color(0xFFF4DDE3),
    surface = Color(0xFF302126),
    onSurface = Color(0xFFF4DDE3),
    surfaceVariant = Color(0xFF4A3139),
    onSurfaceVariant = Color(0xFFD7BFC6),
    outline = Color(0xFFA58A93),
)

private val GraphiteLightColors = lightColorScheme(
    primary = Color(0xFF4D5968),
    onPrimary = Color.White,
    secondary = Color(0xFFCAD6E2),
    onSecondary = Color(0xFF202A34),
    tertiary = Color(0xFF527A91),
    background = Color(0xFFF4F5F7),
    onBackground = Color(0xFF1A1D21),
    surface = Color(0xFFFBFCFD),
    onSurface = Color(0xFF1A1D21),
    surfaceVariant = Color(0xFFE1E6EB),
    onSurfaceVariant = Color(0xFF434B54),
    outline = Color(0xFF737D87),
)

private val GraphiteDarkColors = darkColorScheme(
    primary = Color(0xFFB8C7D9),
    onPrimary = Color(0xFF23303C),
    secondary = Color(0xFF42515F),
    onSecondary = Color(0xFFDCE7F4),
    tertiary = Color(0xFF9AC8DE),
    background = Color(0xFF15191E),
    onBackground = Color(0xFFE1E5EA),
    surface = Color(0xFF1D2329),
    onSurface = Color(0xFFE1E5EA),
    surfaceVariant = Color(0xFF313A43),
    onSurfaceVariant = Color(0xFFC0C9D2),
    outline = Color(0xFF8D9AA5),
)

internal fun appColorScheme(theme: AppColorTheme, darkTheme: Boolean): ColorScheme = when (theme) {
    AppColorTheme.TEAL -> if (darkTheme) TealDarkColors else TealLightColors
    AppColorTheme.LAVENDER -> if (darkTheme) LavenderDarkColors else LavenderLightColors
    AppColorTheme.ROSE -> if (darkTheme) RoseDarkColors else RoseLightColors
    AppColorTheme.GRAPHITE -> if (darkTheme) GraphiteDarkColors else GraphiteLightColors
}

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Thin,
        fontSize = 64.sp,
        lineHeight = 70.sp,
        fontFeatureSettings = "tnum",
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 36.sp,
        lineHeight = 42.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun AlarmingTheme(
    darkTheme: Boolean = false,
    colorTheme: AppColorTheme = AppColorTheme.TEAL,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = appColorScheme(colorTheme, darkTheme),
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
