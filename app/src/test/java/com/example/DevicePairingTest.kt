package com.example

import com.example.model.DevicePairingToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePairingTest {

    @Test
    fun `test 10-minute pairing token expiration logic`() {
        val now = System.currentTimeMillis()
        val activeToken = DevicePairingToken(
            code = "482915",
            adminUid = "admin_uid_123",
            storeName = "Kisan Feed & Traders",
            assignedRole = "CASHIER",
            status = "ACTIVE",
            createdAt = now,
            expiresAt = now + 10 * 60 * 1000L
        )

        assertTrue("New token should be active", activeToken.isActive())
        assertFalse("New token should not be expired", activeToken.isExpired())

        // Expired token (11 minutes old)
        val expiredToken = activeToken.copy(
            createdAt = now - 11 * 60 * 1000L,
            expiresAt = now - 1 * 60 * 1000L
        )

        assertTrue("Token past expiresAt should be expired", expiredToken.isExpired())
        assertFalse("Expired token should not be active", expiredToken.isActive())
    }

    @Test
    fun `test claimed token is no longer active`() {
        val now = System.currentTimeMillis()
        val claimedToken = DevicePairingToken(
            code = "829104",
            adminUid = "admin_uid_123",
            storeName = "Kisan Feed & Traders",
            assignedRole = "MANAGER",
            status = "CLAIMED",
            createdAt = now,
            expiresAt = now + 15 * 60 * 1000L,
            claimedByUid = "staff_uid_456",
            claimedByName = "Ramesh Staff",
            claimedAt = now + 60000L
        )

        assertFalse("Claimed token should not be active for new pairing", claimedToken.isActive())
        assertTrue("Claimed token should be recognized as connected", claimedToken.isClaimedOrUsed())
        val usedToken = claimedToken.copy(status = "USED")
        assertFalse("Used token should not be active for new pairing", usedToken.isActive())
        assertTrue("Used token should also be recognized as connected by admin dialog", usedToken.isClaimedOrUsed())
        assertEquals("MANAGER", claimedToken.assignedRole)
        assertEquals("staff_uid_456", claimedToken.claimedByUid)
    }

    @Test
    fun `test pairing code format is 8 digits`() {
        val code = com.example.data.repository.PairingRepository.generateSecurePairingCode()
        assertEquals(com.example.data.repository.PairingRepository.PAIRING_CODE_LENGTH, code.length)
        assertEquals(8, code.length)
        assertTrue(code.all { it.isDigit() })
    }
}
