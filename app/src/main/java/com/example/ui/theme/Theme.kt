package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Tokens follow KhataWise UI Guidelines §2.1 (light) and §6.1 (dark).
val HighContrastDarkColorScheme = darkColorScheme(
    primary = Color(0xFF0B7A55),                 // green strong (buttons, toggles) - same in both themes
    onPrimary = Color.White,
    primaryContainer = Color(0xFF12372B),        // green tint
    onPrimaryContainer = Color(0xFF6EE7B7),      // green text (dark)
    secondary = Color(0xFFF59E0B),
    onSecondary = Color(0xFF281804),
    secondaryContainer = Color(0xFF3A2E12),      // amber tint
    onSecondaryContainer = Color(0xFFFCD34D),    // amber chip text (dark)
    tertiary = Color(0xFF2F80C9),                // blue: info, UPI, manager
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF14305E),       // admin / navy button, white text
    onTertiaryContainer = Color.White,
    error = Color(0xFFF87171),
    onError = Color(0xFF38080B),
    errorContainer = Color(0xFF3B1D1F),          // red tint
    onErrorContainer = Color(0xFFFCA5A5),        // red chip text (dark)
    background = Color(0xFF0F1720),              // bg
    onBackground = Color(0xFFE8EEF5),            // ink
    surface = Color(0xFF1A2330),                 // surface
    onSurface = Color(0xFFE8EEF5),
    surfaceVariant = Color(0xFF1A2330),          // cards
    onSurfaceVariant = Color(0xFF9AA8BA),        // muted
    surfaceContainerLowest = Color(0xFF1A2330),  // fields
    surfaceContainerLow = Color(0xFF1A2330),     // bars, bottom navigation
    surfaceContainer = Color(0xFF1F2A3A),        // menus
    surfaceContainerHigh = Color(0xFF243040),    // fill, dialogs
    surfaceContainerHighest = Color(0xFF2A3646),
    inverseSurface = Color(0xFF2A3646),          // snackbars (readable with hint-coloured text)
    inverseOnSurface = Color(0xFF8794A8),        // hint
    outline = Color(0xFF2A3646),                 // border, divider
    outlineVariant = Color(0xFF3A4A5E)           // field-border
)

val CleanLightColorScheme = lightColorScheme(
    primary = Color(0xFF0B7A55),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF5EA),
    onPrimaryContainer = Color(0xFF0B6B49),
    secondary = Color(0xFFD97706),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFDEBD0),
    onSecondaryContainer = Color(0xFF7C3A06),
    tertiary = Color(0xFF2F80C9),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF14305E),
    onTertiaryContainer = Color.White,
    error = Color(0xFFB91C1C),
    onError = Color.White,
    errorContainer = Color(0xFFFDE4E4),
    onErrorContainer = Color(0xFF991B1B),
    background = Color(0xFFF6F8FB),              // bg
    onBackground = Color(0xFF0F1F3D),            // ink
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F1F3D),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF4B5B73),        // muted
    surfaceContainerLowest = Color(0xFFFFFFFF),  // fields
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),        // menus
    surfaceContainerHigh = Color(0xFFFFFFFF),    // dialogs
    surfaceContainerHighest = Color(0xFFE3E8F0),
    inverseSurface = Color(0xFF0F1F3D),
    inverseOnSurface = Color(0xFF7B889C),        // hint
    outline = Color(0xFFE3E8F0),                 // border
    outlineVariant = Color(0xFFCBD5E1)           // field-border
)

@Composable
fun KhataWiseTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Drives the theme-aware status colours (CGreen, CAmber, CRedUdhari, ...) in Color.kt.
    KwThemeState.isDark = darkTheme
    val colorScheme = if (darkTheme) HighContrastDarkColorScheme else CleanLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    KhataWiseTheme(darkTheme = darkTheme, content = content)
}
