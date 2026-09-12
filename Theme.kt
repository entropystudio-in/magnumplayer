package com.cadence.player.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val LightScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = Color_White,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceVariant = LightSurface
)

private val DarkScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = Color_White,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceVariant = DarkSurface
)

private val BlackYellowScheme = darkColorScheme(
    primary = ByAccent,
    onPrimary = Color_Black,
    background = ByBackground,
    onBackground = ByOnBackground,
    surface = BySurface,
    onSurface = ByOnBackground,
    onSurfaceVariant = ByOnSurfaceVariant,
    surfaceVariant = BySurface
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp)
)

@Composable
fun CadenceTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.BLACK_YELLOW -> true
    }

    val colorScheme = when (themeMode) {
        ThemeMode.BLACK_YELLOW -> BlackYellowScheme
        else -> if (useDark) DarkScheme else LightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
