package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// Theme-aware status colours (UI guidelines §2.1, §2.2, §6.1).
// These read KwThemeState so every existing usage adapts to light/dark without code changes:
//   light = "text on white" / tint values, dark = "text on dark" / tint values.
object KwThemeState {
    var isDark by mutableStateOf(false)
}

private fun pick(light: Long, dark: Long): Color = Color(if (KwThemeState.isDark) dark else light)

// Green: money in, paid, OK, primary
val CGreen: Color get() = pick(0xFF0B6B49, 0xFF34D399)
val CGreenPrimary: Color get() = Color(0xFF0B7A55)            // strong green (buttons, toggles), same in both themes
val CGreenContainer: Color get() = pick(0xFFDDF5EA, 0xFF12372B)
val OnCGreenContainer: Color get() = pick(0xFF0B6B49, 0xFF6EE7B7)

// Amber: warning, low stock, rates, pending
val CAmber: Color get() = pick(0xFFB45309, 0xFFF59E0B)
val CAmberContainer: Color get() = pick(0xFFFDEBD0, 0xFF3A2E12)
val OnCAmberContainer: Color get() = pick(0xFF7C3A06, 0xFFFCD34D)
val CAmberBg: Color get() = pick(0xFFFFF4E5, 0xFF33270F)
val CAmberBorder: Color get() = pick(0xFFF2D3A4, 0xFF5A4417)

// Red: udhari, due, overdue, money out, danger
val CRedUdhari: Color get() = pick(0xFFB91C1C, 0xFFF87171)
val CRedUdhariBg: Color get() = pick(0xFFFDE4E4, 0xFF3B1D1F)
val CRedUdhariBorder: Color get() = pick(0xFFF5C2C2, 0xFF6B2B2E)

// Proper MaterialTheme.colorScheme role mappings (no fragile luminance checks)
val FlatDarkBg: Color
    @Composable
    get() = MaterialTheme.colorScheme.background

val FlatDarkSurface: Color
    @Composable
    get() = MaterialTheme.colorScheme.surface

val FlatDarkCard: Color
    @Composable
    get() = MaterialTheme.colorScheme.surfaceVariant

val FlatDarkBorder: Color
    @Composable
    get() = MaterialTheme.colorScheme.outline

val FlatDarkNav: Color
    @Composable
    get() = MaterialTheme.colorScheme.surfaceContainerLow

val FlatDarkInput: Color
    @Composable
    get() = MaterialTheme.colorScheme.surfaceContainerLowest

val FlatDarkInputBorder: Color
    @Composable
    get() = MaterialTheme.colorScheme.outlineVariant

val FlatTextPrimary: Color
    @Composable
    get() = MaterialTheme.colorScheme.onBackground

val FlatTextSecondary: Color
    @Composable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

val FlatTextMuted: Color
    @Composable
    get() = MaterialTheme.colorScheme.inverseOnSurface

val NavyButtonBg: Color
    @Composable
    get() = MaterialTheme.colorScheme.tertiaryContainer

val NavyButtonText: Color
    @Composable
    get() = MaterialTheme.colorScheme.onTertiaryContainer

// Compatibility tokens
val ForestGreenPrimary = CGreenPrimary
val ForestGreenLight = Color(0xFF2D6A4F)
val ForestGreenDark = Color(0xFF081C15)
val ForestGreenContainer: Color get() = CGreenContainer
val OnForestGreenContainer: Color get() = OnCGreenContainer

// Secondary warm wheat/harvest amber
val AmberWheatSecondary: Color get() = CAmber
val AmberWheatContainer: Color get() = CAmberContainer
val OnAmberWheatContainer: Color get() = OnCAmberContainer

// Tertiary terracotta
val TerracottaTertiary = Color(0xFFB91C1C)
val TerracottaContainer = Color(0xFFFDE4E4)

// Neutrals
val CreamBackground = Color(0xFF0F1720)
val SurfaceWhite = Color(0xFF1A2330)
val SurfaceVariantWarm = Color(0xFF1A2330)
val OutlineWarm = Color(0xFF2A3646)

// Dark Palette
val ForestGreenPrimaryDark: Color get() = CGreen
val DarkBackground = Color(0xFF0F1720)
val DarkSurface = Color(0xFF1A2330)
val DarkSurfaceVariant = Color(0xFF1A2330)
val DarkOutline = Color(0xFF2A3646)

// Functional Status Colors
val StatusGreen: Color get() = CGreen
val StatusGreenBg: Color get() = CGreenContainer
val StatusAmber: Color get() = CAmber
val StatusAmberBg: Color get() = CAmberContainer
val StatusRed: Color get() = CRedUdhari
val StatusRedBg: Color get() = CRedUdhariBg
val StatusBlue: Color get() = pick(0xFF134E8A, 0xFF93C5FD)
val StatusBlueBg: Color
    @Composable
    get() = NavyButtonBg
