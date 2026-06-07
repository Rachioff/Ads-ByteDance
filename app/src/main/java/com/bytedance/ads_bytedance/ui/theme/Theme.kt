package com.bytedance.ads_bytedance.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════════════════════
// Light ColorScheme — 字节风格浅色主题
// ═══════════════════════════════════════════════════════

private val LightColorScheme = lightColorScheme(
    primary = Blue600,
    onPrimary = White,
    primaryContainer = Blue100,
    onPrimaryContainer = Blue600,

    secondary = Indigo500,
    onSecondary = White,
    secondaryContainer = Indigo100,
    onSecondaryContainer = Indigo500,

    background = Gray50,
    onBackground = Gray900,

    surface = White,
    onSurface = Gray900,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray600,

    outline = Gray200,
    outlineVariant = Gray100,

    error = ErrorRed,
    onError = White,
    errorContainer = LikeRedBg,
    onErrorContainer = ErrorRed
)

// ═══════════════════════════════════════════════════════
// Dark ColorScheme — 字节风格深色主题
// ═══════════════════════════════════════════════════════

private val DarkColorScheme = darkColorScheme(
    primary = Blue400,
    onPrimary = Gray900,
    primaryContainer = Blue600,
    onPrimaryContainer = Blue100,

    secondary = Indigo500,
    onSecondary = White,
    secondaryContainer = Indigo500,
    onSecondaryContainer = Indigo100,

    background = DarkBg,
    onBackground = DarkText,

    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkCard,
    onSurfaceVariant = DarkTextSecondary,

    outline = DarkBorder,
    outlineVariant = DarkBorder,

    error = ErrorRed,
    onError = White,
    errorContainer = Color(0xFF3B1515),
    onErrorContainer = LikeRed
)

// ═══════════════════════════════════════════════════════
// Theme Composable
// ═══════════════════════════════════════════════════════

@Composable
fun AdsByteDanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // 设置系统状态栏颜色匹配主题
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
