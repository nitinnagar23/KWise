package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.model.DevicePairingToken
import com.example.sync.safeLong
import com.example.sync.safeNullableString
import com.example.sync.safeString
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

class PairingRepository(private val context: Context) {

    private val firestore: FirebaseFirestore?
        get() = try {
            val dbId = context.getString(R.string.firestore_database_id).trim()
            if (dbId.isBlank() || dbId == "(default)") {
                FirebaseFirestore.getInstance()
            } else {
                FirebaseFirestore.getInstance(dbId)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to get Firestore instance: ${e.message}")
            null
        }

    companion object {
        private const val TAG = "KhataWisePairing"
        const val PAIRING_EXPIRY_MS = 10 * 60 * 1000L // 10 minutes
        private const val MAX_PAIRING_ATTEMPTS = 5
        private const val PAIRING_LOCKOUT_MS = 5 * 60 * 1000L

        @Volatile
        private var failedLookupAttempts = 0

        @Volatile
        private var lookupLockoutUntilMs = 0L

        @Synchronized
        private fun checkLookupRateLimit() {
            val now = System.currentTimeMillis()
            if (lookupLockoutUntilMs > now) {
                val remSec = ((lookupLockoutUntilMs - now + 999L) / 1000L).coerceAtLeast(1L)
                throw IllegalStateException("Too many invalid pairing attempts. Please wait ${remSec}s before trying again.")
            }
            if (lookupLockoutUntilMs != 0L && now >= lookupLockoutUntilMs) {
                lookupLockoutUntilMs = 0L
                failedLookupAttempts = 0
            }
        }

        @Synchronized
        private fun recordLookupOutcome(success: Boolean) {
            if (success) {
                failedLookupAttempts = 0
                lookupLockoutUntilMs = 0L
            } else {
                failedLookupAttempts++
                if (failedLookupAttempts >= MAX_PAIRING_ATTEMPTS) {
                    lookupLockoutUntilMs = System.currentTimeMillis() + PAIRING_LOCKOUT_MS
                }
            }
        }
    }

    /**
     * Admin generates a 6-digit numeric pairing code with a 10-minute countdown.
     */
    suspend fun generatePairingCode(
        adminUid: String,
        storeName: String,
        storeAddress: String = "",
        storePhone: String = "",
        assignedRole: String = "CASHIER"
    ): DevicePairingToken {
        val db = firestore ?: throw IllegalStateException("Cloud Firestore is not available")
        val random = SecureRandom()
        val numericCode = String.format("%06d", random.nextInt(1000000))
        val safeRole = assignedRole.uppercase().takeIf { it in setOf("CASHIER", "MANAGER", "STAFF") } ?: "CASHIER"

        val now = System.currentTimeMillis()
        val token = DevicePairingToken(
            code = numericCode,
            adminUid = adminUid,
            storeName = storeName.ifBlank { "KhataWise Store" },
            storeAddress = storeAddress,
            storePhone = storePhone,
            assignedRole = safeRole,
            status = "ACTIVE",
            createdAt = now,
            expiresAt = now + PAIRING_EXPIRY_MS
        )

        val data = mapOf(
            "code" to token.code,
            "adminUid" to token.adminUid,
            "storeName" to token.storeName,
            "storeAddress" to token.storeAddress,
            "storePhone" to token.storePhone,
            "assignedRole" to token.assignedRole,
            "status" to token.status,
            "createdAt" to token.createdAt,
            "expiresAt" to token.expiresAt
        )

        db.collection("pairings").document(numericCode).set(data).await()
        Log.d(TAG, "Created 10-minute pairing token $numericCode for store $storeName")
        return token
    }

    /**
     * Inspect pairing token to display shop confirmation before claiming.
     */
    suspend fun getPairingToken(code: String): Result<DevicePairingToken> = runCatching {
        checkLookupRateLimit()
        val db = firestore ?: throw IllegalStateException("Cloud Firestore is not available")
        val cleanCode = code.trim().replace(" ", "").replace("-", "")
        if (cleanCode.length !in 6..12) {
            recordLookupOutcome(false)
            throw IllegalArgumentException("Invalid pairing code format.")
        }
        val snapshot = db.collection("pairings").document(cleanCode).get().await()
        if (!snapshot.exists()) {
            recordLookupOutcome(false)
            throw IllegalArgumentException("Invalid pairing code. Please verify the 6-digit code on the Admin device.")
        }
        val status = snapshot.safeString("status")
        val expiresAt = snapshot.safeLong("expiresAt", 0L) ?: 0L
        if (status != "ACTIVE") {
            recordLookupOutcome(false)
            throw IllegalStateException("This pairing code has already been claimed or expired.")
        }
        if (System.currentTimeMillis() >= expiresAt) {
            recordLookupOutcome(false)
            throw IllegalStateException("This pairing code expired after 10 minutes. Please request a new code.")
        }
        recordLookupOutcome(true)
        DevicePairingToken(
            code = cleanCode,
            adminUid = snapshot.safeString("adminUid"),
            storeName = snapshot.safeString("storeName", "KhataWise Store").ifBlank { "KhataWise Store" },
            storeAddress = snapshot.safeString("storeAddress"),
            storePhone = snapshot.safeString("storePhone"),
            assignedRole = snapshot.safeString("assignedRole", "CASHIER").ifBlank { "CASHIER" },
            status = status,
            createdAt = snapshot.safeLong("createdAt") ?: System.currentTimeMillis(),
            expiresAt = expiresAt
        )
    }

    /**
     * Admin UI listens in real-time to watch when staff redeems the code.
     */
    fun listenToPairingToken(code: String): Flow<DevicePairingToken?> = callbackFlow {
        val db = firestore
        if (db == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = db.collection("pairings").document(code)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Pairing token listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val token = DevicePairingToken(
                        code = snapshot.safeString("code", code).ifBlank { code },
                        adminUid = snapshot.safeString("adminUid"),
                        storeName = snapshot.safeString("storeName"),
                        assignedRole = snapshot.safeString("assignedRole", "CASHIER").ifBlank { "CASHIER" },
                        status = snapshot.safeString("status", "ACTIVE").ifBlank { "ACTIVE" },
                        createdAt = snapshot.safeLong("createdAt", 0L) ?: 0L,
                        expiresAt = snapshot.safeLong("expiresAt", 0L) ?: 0L,
                        claimedByUid = snapshot.safeNullableString("claimedByUid"),
                        claimedByName = snapshot.safeNullableString("claimedByName"),
                        claimedAt = snapshot.safeLong("claimedAt")
                    )
                    trySend(token)
                } else {
                    trySend(null)
                }
            }

        awaitClose {
            try {
                listener.remove()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Secondary / Staff device submits the 6-digit code to pair with the store.
     */
    suspend fun verifyAndClaimPairingCode(
        code: String,
        staffUid: String,
        staffName: String,
        staffEmail: String = "",
        deviceInfo: String = ""
    ): Result<DevicePairingToken> = runCatching {
        val db = firestore ?: throw IllegalStateException("Cloud Firestore is not available")
        val cleanCode = code.trim().replace(" ", "").replace("-", "")

        val docRef = db.collection("pairings").document(cleanCode)
        val snapshot = docRef.get().await()

        if (!snapshot.exists()) {
            throw IllegalArgumentException("Invalid pairing code. Please verify the 6-digit PIN on the Admin device.")
        }

        val status = snapshot.safeString("status")
        val expiresAt = snapshot.safeLong("expiresAt", 0L) ?: 0L
        val now = System.currentTimeMillis()

        if (status != "ACTIVE") {
            throw IllegalStateException("This pairing code has already been redeemed or expired.")
        }

        if (now >= expiresAt) {
            throw IllegalStateException("This pairing code expired after 10 minutes. Please ask the store admin to generate a new code.")
        }

        val adminUid = snapshot.safeString("adminUid")
        val storeName = snapshot.safeString("storeName", "KhataWise Store").ifBlank { "KhataWise Store" }
        val assignedRole = snapshot.safeString("assignedRole", "CASHIER").ifBlank { "CASHIER" }

        // Claim token in Firestore
        val updates = mapOf(
            "status" to "CLAIMED",
            "claimedByUid" to staffUid,
            "claimedByName" to staffName.ifBlank { "Staff Device" },
            "claimedByEmail" to staffEmail,
            "claimedAt" to now
        )
        docRef.update(updates).await()

        // Also record staff member under store
        if (adminUid.isNotBlank()) {
            val staffDoc = mapOf(
                "staffUid" to staffUid,
                "name" to staffName.ifBlank { "Staff Device" },
                "email" to staffEmail,
                "deviceInfo" to deviceInfo,
                "role" to assignedRole,
                "isActive" to true,
                "pairedAt" to now,
                "lastActiveAt" to now,
                "updatedAt" to now
            )
            try {
                db.collection("users").document(adminUid).collection("staff").document(staffUid).set(staffDoc).await()
            } catch (e: Throwable) {
                Log.w(TAG, "Optional staff record under admin: ${e.message}")
            }
        }

        DevicePairingToken(
            code = cleanCode,
            adminUid = adminUid,
            storeName = storeName,
            storeAddress = snapshot.safeString("storeAddress"),
            storePhone = snapshot.safeString("storePhone"),
            assignedRole = assignedRole,
            status = "CLAIMED",
            createdAt = snapshot.safeLong("createdAt") ?: now,
            expiresAt = expiresAt,
            claimedByUid = staffUid,
            claimedByName = staffName,
            claimedAt = now
        )
    }
}
