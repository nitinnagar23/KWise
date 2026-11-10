package com.example

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.HighContrastDarkColorScheme
import com.example.ui.theme.KwSurface
import com.example.ui.theme.KwThemeState
import com.example.ui.theme.kw
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @After
    fun resetTheme() {
        KwThemeState.isDark = false
    }

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun kwWhiteMapsToDarkCardSurfaceInDarkMode() {
        KwThemeState.isDark = false
        assertEquals(Color(0xFFFFFFFF), kw(0xFFFFFFFF))
        assertEquals(Color.White, KwSurface)

        KwThemeState.isDark = true
        assertEquals(Color(0xFF1A2330), kw(0xFFFFFFFF))
        assertEquals(Color(0xFF1A2330), KwSurface)
    }

    @Test
    fun kwPastelSemanticTintsPreserveHueInDarkMode() {
        KwThemeState.isDark = true

        // Green tint (0xFFDDF3E8) should remain green-dominant in dark mode
        val greenTint = kw(0xFFDDF3E8)
        assertTrue("Green tint should stay green-dominant in dark mode", greenTint.green > greenTint.red && greenTint.green > greenTint.blue)
        assertNotEquals("Green tint must not collapse to neutral gray", Color(0xFF243040), greenTint)

        // Red tint (0xFFFEE2E2) should remain red-dominant in dark mode
        val redTint = kw(0xFFFEE2E2)
        assertTrue("Red tint should stay red-dominant in dark mode", redTint.red > redTint.green && redTint.red > redTint.blue)
        assertNotEquals("Red tint must not collapse to neutral gray", Color(0xFF243040), redTint)

        // Blue tint (0xFFE0F2FE) should remain blue-dominant in dark mode
        val blueTint = kw(0xFFE0F2FE)
        assertTrue("Blue tint should stay blue-dominant in dark mode", blueTint.blue > blueTint.red)
        assertNotEquals("Blue tint must not collapse to neutral gray", Color(0xFF243040), blueTint)

        // Purple tint (0xFFEDE9FE) should remain blue/red-dominant over green in dark mode
        val purpleTint = kw(0xFFEDE9FE)
        assertTrue("Purple tint should stay purple-dominant in dark mode", purpleTint.blue > purpleTint.green && purpleTint.red > purpleTint.green)
        assertNotEquals("Purple tint must not collapse to neutral gray", Color(0xFF243040), purpleTint)
    }

    @Test
    fun darkColorSchemeSeparatesCardAndInputSurfaces() {
        assertNotEquals(
            "Dark input container should be visually distinct from dark card surface",
            HighContrastDarkColorScheme.surfaceVariant,
            HighContrastDarkColorScheme.surfaceContainerLowest
        )
    }
}

