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

    @Test
    fun formatWeightDoesNotProduceDuplicateKgUnit() {
        val raw = com.example.util.CurrencyUtils.formatWeight(java.math.BigDecimal("50.00"))
        assertEquals("50", raw)
        assertEquals("50 kg", "${com.example.util.CurrencyUtils.formatWeight(java.math.BigDecimal("50.00"))} kg")
        assertEquals("50 kg", com.example.util.CurrencyUtils.formatWeightKg(java.math.BigDecimal("50.00")))
    }

    @Test
    fun budgetSplitApplyLeftoverDoesNotExceedBudget() {
        val initial = com.example.util.splitBudget(
            budget = java.math.BigDecimal("500"),
            items = listOf(
                com.example.util.BudgetSplitItemInput(
                    productId = "p1",
                    productName = "Wheat Bran",
                    ratePerKg = java.math.BigDecimal("33"),
                    availableStockKg = java.math.BigDecimal("500")
                ),
                com.example.util.BudgetSplitItemInput(
                    productId = "p2",
                    productName = "Chana Churi",
                    ratePerKg = java.math.BigDecimal("37"),
                    availableStockKg = java.math.BigDecimal("500")
                )
            ),
            mode = com.example.util.BudgetSplitMode.EQUAL
        )
        val afterLeftover = com.example.util.applyLeftoverToRow(initial, "p1")
        assertTrue(
            "Total actual amount (${afterLeftover.totalActualAmount}) must not exceed budget (${afterLeftover.budget})",
            afterLeftover.totalActualAmount <= afterLeftover.budget
        )
        assertFalse(afterLeftover.isOverBudget)
    }

    @Test
    fun shopAnalyticsTreatsScaledZeroUdhariAsSettled() {
        val customer = com.example.model.Customer(
            id = "c1",
            name = "Ramesh",
            totalUdhari = java.math.BigDecimal("0.00")
        )
        val oldSale = com.example.model.SaleEntry(
            id = "s1",
            customerId = "c1",
            productId = "p1",
            quantityKg = java.math.BigDecimal("50"),
            pricePerKgAtSale = java.math.BigDecimal("20"),
            costPerKgAtSale = java.math.BigDecimal("18"),
            totalSalePrice = java.math.BigDecimal("1000"),
            paidAmount = java.math.BigDecimal("0"),
            udhariAmount = java.math.BigDecimal("1000"),
            paymentStatus = com.example.model.PaymentStatus.UNPAID,
            soldByUserId = "u1",
            date = 1_000_000L
        )
        val insights = com.example.util.ShopAnalytics.computeCustomerInsights(
            customers = listOf(customer),
            sales = listOf(oldSale),
            payments = emptyList(),
            nowMillis = 90L * 24L * 3600L * 1000L
        )
        assertEquals(0, insights.first().overdueDays)
        assertFalse(insights.first().isOverdue)
    }

    @Test
    fun groupSalesIntoBillsPreservesSequentialBillNumber() {
        val s1 = com.example.model.SaleEntry(
            id = "s1",
            billNumber = "1005",
            customerId = "c1",
            productId = "p1",
            quantityKg = java.math.BigDecimal("50"),
            pricePerKgAtSale = java.math.BigDecimal("24"),
            costPerKgAtSale = java.math.BigDecimal("20"),
            totalSalePrice = java.math.BigDecimal("1200"),
            paidAmount = java.math.BigDecimal("1200"),
            udhariAmount = java.math.BigDecimal.ZERO,
            paymentStatus = com.example.model.PaymentStatus.PAID,
            soldByUserId = "u1",
            date = 10_000L
        )
        val grouped = com.example.model.groupSalesIntoBills(listOf(s1))
        assertEquals(1, grouped.size)
        assertEquals("1005", grouped.first().billNumber)
    }
}



