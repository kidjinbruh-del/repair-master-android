package com.repairmaster.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Amber = Color(0xFFFFB020)
val AmberDark = Color(0xFFC77D00)
val Cyan = Color(0xFF22D3EE)
val Violet = Color(0xFFA78BFA)
val Green = Color(0xFF22D3A5)
val Red = Color(0xFFFF5C74)
val Ink = Color(0xFF0D1017)
val Panel = Color(0xFF161B26)
val Panel2 = Color(0xFF1E2432)
val Line = Color(0xFF2A3242)
val TextDim = Color(0xFF98A2B6)

private val DarkScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1200),
    secondary = Cyan,
    tertiary = Violet,
    background = Ink,
    onBackground = Color(0xFFE6EBF5),
    surface = Panel,
    onSurface = Color(0xFFE6EBF5),
    surfaceVariant = Panel2,
    onSurfaceVariant = TextDim,
    outline = Line,
    error = Red,
)

private val LightScheme = lightColorScheme(
    primary = AmberDark,
    onPrimary = Color.White,
    secondary = Color(0xFF0891B2),
    tertiary = Color(0xFF7C3AED),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF14181F),
    surface = Color.White,
    onSurface = Color(0xFF14181F),
    surfaceVariant = Color(0xFFEDEFF5),
    onSurfaceVariant = Color(0xFF5B6478),
    outline = Color(0xFFD3D8E3),
    error = Red,
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
    ),
)

@Composable
fun RepairMasterTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content,
    )
}