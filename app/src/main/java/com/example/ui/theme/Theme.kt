package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val HighContrastDarkColorScheme = darkColorScheme(
    primary = CGreen,
    onPrimary = Color(0xFF061A0E),
    primaryContainer = CGreenContainer,
    onPrimaryContainer = OnCGreenContainer,
    secondary = CAmber,
    onSecondary = Color(0xFF281804),
    secondaryContainer = CAmberContainer,
    onSecondaryContainer = OnCAmberContainer,
    tertiary = CRedUdhari,
    onTertiary = Color(0xFF38080B),
    tertiaryContainer = Color(0xFF0C2B4E),
    onTertiaryContainer = Color(0xFF93C5FD),
    error = CRedUdhari,
    onError = Color(0xFF38080B),
    errorContainer = CRedUdhariBg,
    onErrorContainer = CRedUdhari,
    background = Color(0xFF121413),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF181B19),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1E211F),
    onSurfaceVariant = Color(0xFF9CA3AF),
    surfaceContainerLowest = Color(0xFF141615),
    surfaceContainerLow = Color(0xFF161917),
    surfaceContainer = Color(0xFF1E211F),
    surfaceContainerHigh = Color(0xFF242826),
    surfaceContainerHighest = Color(0xFF2A2E2C),
    inverseOnSurface = Color(0xFF6B7280),
    outline = Color(0xFF2A2E2C),
    outlineVariant = Color(0xFF343836)
)

val CleanLightColorScheme = lightColorScheme(
    primary = Color(0xFF15803D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Color(0xFF14532D),
    secondary = Color(0xFFD97706),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF78350F),
    tertiary = Color(0xFFDC2626),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF1E40AF),
    onTertiaryContainer = Color.White,
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
    background = Color(0xFFF6F5F0),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF6B7280),
    surfaceContainerLowest = Color(0xFFF1EFEA),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF6F5F0),
    surfaceContainerHigh = Color(0xFFEFECE6),
    surfaceContainerHighest = Color(0xFFE5E2DC),
    inverseOnSurface = Color(0xFF6B7280),
    outline = Color(0xFFE6E3DA),
    outlineVariant = Color(0xFFD6D3C9)
)

@Composable
fun KhataWiseTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) HighContrastDarkColorScheme else CleanLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    KhataWiseTheme(darkTheme = darkTheme, content = content)
}
