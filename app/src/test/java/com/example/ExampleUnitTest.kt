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

    @Test
    fun verifyPinRejectsEmptyHashAndUses120kIterations() {
        assertFalse(com.example.util.SecurityUtils.verifyPin("1234", ""))
        assertFalse(com.example.util.SecurityUtils.verifyPin("1234", "   "))
        val hash = com.example.util.SecurityUtils.hashPin("4321")
        assertTrue(hash.startsWith("PBKDF2$120000$"))
        assertTrue(com.example.util.SecurityUtils.verifyPin("4321", hash))
        assertFalse(com.example.util.SecurityUtils.verifyPin("0000", hash))
    }

    @Test
    fun pinLockoutTracksAttemptsPerUserIndependently() {
        val hash = com.example.util.SecurityUtils.hashPin("1234")
        com.example.util.SecurityUtils.resetLockout("user_a")
        com.example.util.SecurityUtils.resetLockout("user_b")

        repeat(5) {
            com.example.util.SecurityUtils.verifyPinWithLockout(
                pin = "0000",
                storedHash = hash,
                userId = "user_a"
            )
        }
        assertTrue(com.example.util.SecurityUtils.isPinLockedOut("user_a"))
        assertFalse(com.example.util.SecurityUtils.isPinLockedOut("user_b"))
        assertTrue(
            com.example.util.SecurityUtils.verifyPinWithLockout(
                pin = "1234",
                storedHash = hash,
                userId = "user_b"
            ).isSuccess
        )
        com.example.util.SecurityUtils.resetLockout("user_a")
        com.example.util.SecurityUtils.resetLockout("user_b")
    }

    @Test
    fun budgetSplitAmountModeAutoFillsSingleUnlockedRow() {
        val res = com.example.util.splitBudget(
            budget = java.math.BigDecimal("500"),
            items = listOf(
                com.example.util.BudgetSplitItemInput(
                    productId = "p1",
                    productName = "Locked Item",
                    ratePerKg = java.math.BigDecimal("50"),
                    requestedAmount = java.math.BigDecimal("200"),
                    isLocked = true
                ),
                com.example.util.BudgetSplitItemInput(
                    productId = "p2",
                    productName = "Single Unlocked Item",
                    ratePerKg = java.math.BigDecimal("30"),
                    requestedAmount = java.math.BigDecimal.ZERO,
                    isLocked = false
                )
            ),
            mode = com.example.util.BudgetSplitMode.AMOUNT
        )
        assertEquals(0, res.rows[1].targetAmount.compareTo(java.math.BigDecimal("300.00")))
        assertEquals(0, res.rows[1].allocatedKg.compareTo(java.math.BigDecimal("10.000")))
    }

    @Test
    fun budgetSplitApplyLeftoverMatchesAllocatedKgTimesRate() {
        val initial = com.example.util.splitBudget(
            budget = java.math.BigDecimal("500"),
            items = listOf(
                com.example.util.BudgetSplitItemInput(
                    productId = "p1",
                    productName = "Chana",
                    ratePerKg = java.math.BigDecimal("80.00"),
                    availableStockKg = java.math.BigDecimal("500")
                ),
                com.example.util.BudgetSplitItemInput(
                    productId = "p2",
                    productName = "Wheat",
                    ratePerKg = java.math.BigDecimal("35.00"),
                    availableStockKg = java.math.BigDecimal("500")
                ),
                com.example.util.BudgetSplitItemInput(
                    productId = "p3",
                    productName = "Mustard",
                    ratePerKg = java.math.BigDecimal("60.00"),
                    availableStockKg = java.math.BigDecimal("500")
                )
            ),
            mode = com.example.util.BudgetSplitMode.EQUAL
        )
        val after = com.example.util.applyLeftoverToRow(initial, "p1")
        val p1 = after.rows.first { it.productId == "p1" }
        val expectedActual = p1.allocatedKg.multiply(p1.ratePerKg).setScale(2, java.math.RoundingMode.HALF_UP)
        assertEquals(expectedActual, p1.actualAmount)
        assertEquals(
            after.budget.subtract(after.totalActualAmount).setScale(2, java.math.RoundingMode.HALF_UP),
            after.leftoverRupees
        )
    }

    @Test
    fun pinLockoutCannotBeBypassedByJumpingWallClockForwardOrBackward() {
        val hash = com.example.util.SecurityUtils.hashPin("1234")
        val userId = "clock_tamper_user"
        com.example.util.SecurityUtils.resetLockout(userId)

        val baseWallMs = 1_700_000_000_000L
        val baseElapsedMs = 50_000L

        repeat(5) {
            com.example.util.SecurityUtils.verifyPinWithLockout(
                pin = "0000",
                storedHash = hash,
                userId = userId,
                nowMs = baseWallMs,
                elapsedRealtimeMs = baseElapsedMs
            )
        }

        // Advancing wall clock by 10 minutes while monotonic elapsedRealtime only advanced 10 seconds
        // must keep the user locked out!
        assertTrue(
            com.example.util.SecurityUtils.isLockedOut(
                nowMs = baseWallMs + 10 * 60 * 1000L,
                userId = userId,
                elapsedRealtimeMs = baseElapsedMs + 10_000L
            )
        )

        // Rolling wall clock backwards by 1 hour also keeps the user locked out
        assertTrue(
            com.example.util.SecurityUtils.isLockedOut(
                nowMs = baseWallMs - 60 * 60 * 1000L,
                userId = userId,
                elapsedRealtimeMs = baseElapsedMs + 15_000L
            )
        )

        com.example.util.SecurityUtils.resetLockout(userId)
    }

    @Test
    fun multiCandidatePinCheckCountsOnlyOneFailurePerEnteredPin() {
        val lockoutKey = "multi_candidate_lockout"
        com.example.util.SecurityUtils.resetLockout(lockoutKey)

        val candidateHashes = listOf(
            com.example.util.SecurityUtils.hashPin("1111"),
            com.example.util.SecurityUtils.hashPin("2222"),
            com.example.util.SecurityUtils.hashPin("3333")
        )

        // Two wrong entries across 3 candidates must only record 2 failed attempts (not 6!),
        // so the 3rd candidate's valid PIN ("3333") must still succeed on the 3rd try.
        repeat(2) {
            val matched = candidateHashes.any { com.example.util.SecurityUtils.verifyPin("9999", it) }
            assertFalse(matched)
            val res = com.example.util.SecurityUtils.recordFailedPinAttempt(userId = lockoutKey)
            assertFalse("Should not be locked out after ${it + 1} wrong tries", res.isLockedOut)
        }
        assertEquals(2, com.example.util.SecurityUtils.getFailedAttempts(lockoutKey))
        assertFalse(com.example.util.SecurityUtils.isPinLockedOut(lockoutKey))

        // Now entering the 3rd candidate's correct PIN succeeds
        val thirdCandidateMatched = candidateHashes.any { com.example.util.SecurityUtils.verifyPin("3333", it) }
        assertTrue(thirdCandidateMatched)
        com.example.util.SecurityUtils.resetLockout(lockoutKey)
    }

    @Test
    fun devicePinUnlockLockout_preventsUserSwitchingBypass() {
        val deviceLockoutKey = "device_pin_unlock"
        val userAKey = "staff_user_a"
        val userBKey = "staff_user_b"
        com.example.util.SecurityUtils.resetLockout(deviceLockoutKey)
        com.example.util.SecurityUtils.resetLockout(userAKey)
        com.example.util.SecurityUtils.resetLockout(userBKey)

        // 3 wrong attempts with user A selected
        repeat(3) {
            val dRes = com.example.util.SecurityUtils.recordFailedPinAttempt(userId = deviceLockoutKey)
            com.example.util.SecurityUtils.recordFailedPinAttempt(userId = userAKey)
            assertFalse(dRes.isLockedOut)
        }

        // Switch selected user to user B and make 2 more wrong attempts -> total 5 on device -> locked out!
        val fourthDeviceRes = com.example.util.SecurityUtils.recordFailedPinAttempt(userId = deviceLockoutKey)
        com.example.util.SecurityUtils.recordFailedPinAttempt(userId = userBKey)
        assertFalse(fourthDeviceRes.isLockedOut)

        val fifthDeviceRes = com.example.util.SecurityUtils.recordFailedPinAttempt(userId = deviceLockoutKey)
        com.example.util.SecurityUtils.recordFailedPinAttempt(userId = userBKey)
        assertTrue("5th failed attempt across switched users must lock the device", fifthDeviceRes.isLockedOut)
        assertTrue(fifthDeviceRes.remainingLockoutSeconds > 0L)
        assertTrue(com.example.util.SecurityUtils.isPinLockedOut(deviceLockoutKey))

        com.example.util.SecurityUtils.resetLockout(deviceLockoutKey)
        com.example.util.SecurityUtils.resetLockout(userAKey)
        com.example.util.SecurityUtils.resetLockout(userBKey)
    }

    @Test
    fun shopAnalyticsCountsConsolidatedBillsInsteadOfLineItems() {
        val customer = com.example.model.Customer(
            id = "c_multi_item",
            name = "Multi Item Buyer",
            totalUdhari = java.math.BigDecimal("100.00")
        )
        // One bill (#1010) containing 5 different line items
        val lineItemsOnSameBill = (1..5).map { idx ->
            com.example.model.SaleEntry(
                id = "sale_item_$idx",
                billNumber = "1010",
                customerId = "c_multi_item",
                productId = "p_$idx",
                quantityKg = java.math.BigDecimal("10"),
                pricePerKgAtSale = java.math.BigDecimal("20"),
                costPerKgAtSale = java.math.BigDecimal("15"),
                totalSalePrice = java.math.BigDecimal("200"),
                paidAmount = if (idx == 1) java.math.BigDecimal("900") else java.math.BigDecimal.ZERO,
                udhariAmount = if (idx == 1) java.math.BigDecimal("100") else java.math.BigDecimal.ZERO,
                paymentStatus = com.example.model.PaymentStatus.PARTIAL,
                soldByUserId = "u1",
                date = 50_000L
            )
        }
        val insights = com.example.util.ShopAnalytics.computeCustomerInsights(
            customers = listOf(customer),
            sales = lineItemsOnSameBill,
            payments = emptyList(),
            nowMillis = 100_000L
        )
        val insight = insights.first()
        // 5 line items on the same bill #1010 must count as 1 consolidated order, not 5 orders
        assertEquals(1, insight.orderCount)
        assertEquals(0, insight.totalPurchased.compareTo(java.math.BigDecimal("1000")))
        assertEquals(0, insight.totalPaid.compareTo(java.math.BigDecimal("900")))
    }

    @Test
    fun verifyPinRejectsLegacySha256AndRequiresPbkdf2() {
        val pin = "4829"
        val pbkdf2Hash = com.example.util.SecurityUtils.hashPin(pin)
        assertTrue(pbkdf2Hash.startsWith("PBKDF2$"))
        assertTrue(com.example.util.SecurityUtils.verifyPin(pin, pbkdf2Hash))
        assertFalse(com.example.util.SecurityUtils.verifyPin("0000", pbkdf2Hash))

        // Unsalted SHA-256 hex of "4829" must be rejected now that legacy SHA-256 support is removed
        val sha256Hex = java.security.MessageDigest.getInstance("SHA-256")
            .digest(pin.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertFalse("Legacy unsalted SHA-256 hex hash must be rejected", com.example.util.SecurityUtils.verifyPin(pin, sha256Hex))
    }
}
