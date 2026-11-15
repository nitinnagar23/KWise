package com.example.model

data class DevicePairingToken(
    val code: String = "",
    val adminUid: String = "",
    val storeName: String = "",
    val storeAddress: String = "",
    val storePhone: String = "",
    val assignedRole: String = "CASHIER",
    val status: String = "ACTIVE", // ACTIVE, CLAIMED, EXPIRED
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 10 * 60 * 1000L,
    val claimedByUid: String? = null,
    val claimedByName: String? = null,
    val claimedAt: Long? = null
) {
    fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAt
    fun isActive(): Boolean = status == "ACTIVE" && !isExpired()
    fun isClaimedOrUsed(): Boolean = status == "CLAIMED" || status == "USED"
}
