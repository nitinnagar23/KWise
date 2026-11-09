package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Primary agricultural greens (c-green)
val CGreen = Color(0xFF34D399)
val CGreenPrimary = Color(0xFF22C55E)
val CGreenContainer = Color(0xFF133826)
val OnCGreenContainer = Color(0xFF34D399)

// Secondary grain-adjacent warm amber (c-amber) for rates & pricing
val CAmber = Color(0xFFF59E0B)
val CAmberContainer = Color(0xFF362308)
val OnCAmberContainer = Color(0xFFFBBF24)
val CAmberBg = Color(0xFF2E1C05)
val CAmberBorder = Color(0xFF52360E)

// Red reserved strictly for Udhari alerts so it stays meaningful
val CRedUdhari = Color(0xFFF87171)
val CRedUdhariBg = Color(0xFF331416)
val CRedUdhariBorder = Color(0xFF4E1D21)

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
val ForestGreenContainer = CGreenContainer
val OnForestGreenContainer = OnCGreenContainer

// Secondary warm wheat/harvest amber
val AmberWheatSecondary = CAmber
val AmberWheatContainer = CAmberContainer
val OnAmberWheatContainer = OnCAmberContainer

// Tertiary terracotta
val TerracottaTertiary = Color(0xFFB91C1C)
val TerracottaContainer = Color(0xFFFEE2E2)

// Neutrals
val CreamBackground = Color(0xFF121413)
val SurfaceWhite = Color(0xFF181B19)
val SurfaceVariantWarm = Color(0xFF1E211F)
val OutlineWarm = Color(0xFF2A2E2C)

// Dark Palette
val ForestGreenPrimaryDark = CGreen
val DarkBackground = Color(0xFF121413)
val DarkSurface = Color(0xFF181B19)
val DarkSurfaceVariant = Color(0xFF1E211F)
val DarkOutline = Color(0xFF2A2E2C)

// Functional Status Colors
val StatusGreen = CGreen
val StatusGreenBg = CGreenContainer
val StatusAmber = CAmber
val StatusAmberBg = CAmberContainer
val StatusRed = CRedUdhari
val StatusRedBg = CRedUdhariBg
val StatusBlue = Color(0xFF60A5FA)
val StatusBlueBg: Color
    @Composable
    get() = NavyButtonBg
