package com.alarming.ui

import androidx.compose.material3.MaterialTheme
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

private val LightColors = lightColorScheme(
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

private val DarkColors = darkColorScheme(
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
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
