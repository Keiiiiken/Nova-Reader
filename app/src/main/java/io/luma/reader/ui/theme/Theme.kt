package io.luma.reader.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.luma.reader.model.AppAccent
import io.luma.reader.model.AppSettings
import io.luma.reader.model.AppThemeMode

val Pine = Color(0xFF6558F5)
val PineDark = Color(0xFF3A2DB6)
val Sage = Color(0xFFE6E2FF)
val Cream = Color(0xFFFFFFFF)
val Ink = Color(0xFF101828)
val MutedInk = Color(0xFF667085)
val Warm = Color(0xFFFF715B)

private data class AccentPalette(
    val primary: Color,
    val dark: Color,
    val container: Color,
    val darkPrimary: Color,
    val darkContainer: Color
)

private fun accentPalette(accent: AppAccent) = when (accent) {
    AppAccent.BLUE -> AccentPalette(Color(0xFF155EEF), Color(0xFF1645A0), Color(0xFFE8EFFD), Color(0xFFA8C5FF), Color(0xFF203B66))
    AppAccent.VIOLET -> AccentPalette(Pine, PineDark, Sage, Color(0xFFBDB4FF), Color(0xFF4436C6))
    AppAccent.OCEAN -> AccentPalette(Color(0xFF087E8B), Color(0xFF04545D), Color(0xFFC5F0F3), Color(0xFF73D8E1), Color(0xFF075D66))
    AppAccent.FOREST -> AccentPalette(Color(0xFF247A55), Color(0xFF145036), Color(0xFFCBEBD9), Color(0xFF85D5AD), Color(0xFF205C43))
}

private fun lightColors(accent: AppAccent) = accentPalette(accent).let { palette -> lightColorScheme(
    primary = palette.primary,
    onPrimary = Color.White,
    primaryContainer = palette.container,
    onPrimaryContainer = palette.dark,
    secondary = palette.primary,
    onSecondary = Color.White,
    secondaryContainer = palette.container,
    background = Color(0xFFF7F8FC),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDF0F7),
    onSurfaceVariant = MutedInk,
    outline = Color(0xFFD0D5DD),
    outlineVariant = Color(0xFFEAECF0),
    error = Color(0xFFBA1A1A)
)}

private fun darkColors(accent: AppAccent) = accentPalette(accent).let { palette -> darkColorScheme(
    primary = palette.darkPrimary,
    onPrimary = Color(0xFF15131B),
    primaryContainer = palette.darkContainer,
    onPrimaryContainer = Color(0xFFE5E0FF),
    secondary = palette.darkPrimary,
    background = Color(0xFF10131B),
    onBackground = Color(0xFFE8E3EC),
    surface = Color(0xFF1A1F2B),
    onSurface = Color(0xFFE8E3EC),
    surfaceVariant = Color(0xFF252D3C),
    onSurfaceVariant = Color(0xFFBAC3D4),
    outline = Color(0xFF7D879B),
    outlineVariant = Color(0xFF333D50)
)}

@Composable
fun LumaTheme(
    appSettings: AppSettings = AppSettings(),
    content: @Composable () -> Unit
) {
    val darkTheme = when (appSettings.themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val colors = if (darkTheme) darkColors(appSettings.accent) else lightColors(appSettings.accent)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = LumaTypography,
        content = content
    )
}
