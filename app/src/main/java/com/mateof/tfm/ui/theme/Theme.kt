package com.mateof.tfm.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Blue = Color(0xFF4DA8FF)
private val BlueDeep = Color(0xFF1B6FD8)
private val Teal = Color(0xFF37D6C0)
private val Amber = Color(0xFFFFC24D)

private val DarkColors = darkColorScheme(
    primary = Blue,
    onPrimary = Color(0xFF00243F),
    primaryContainer = Color(0xFF14456E),
    onPrimaryContainer = Color(0xFFCBE4FF),
    secondary = Teal,
    onSecondary = Color(0xFF00312B),
    secondaryContainer = Color(0xFF0E4A42),
    onSecondaryContainer = Color(0xFFBFF2E9),
    tertiary = Amber,
    onTertiary = Color(0xFF3F2B00),
    background = Color(0xFF0F1420),
    onBackground = Color(0xFFE3E6EE),
    surface = Color(0xFF131A28),
    onSurface = Color(0xFFE3E6EE),
    surfaceVariant = Color(0xFF1D2637),
    onSurfaceVariant = Color(0xFFAAB4C6),
    surfaceContainer = Color(0xFF171F30),
    surfaceContainerHigh = Color(0xFF1C2537),
    surfaceContainerHighest = Color(0xFF222C41),
    outline = Color(0xFF46516A),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF400010)
)

private val LightColors = lightColorScheme(
    primary = BlueDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001C38),
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF00251F),
    tertiary = Color(0xFFB07C00),
    background = Color(0xFFF7F9FD),
    onBackground = Color(0xFF171C24),
    surface = Color.White,
    onSurface = Color(0xFF171C24),
    surfaceVariant = Color(0xFFE4E9F2),
    onSurfaceVariant = Color(0xFF444D5E),
    outline = Color(0xFF737D91),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp)
)

@Composable
fun TfmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
