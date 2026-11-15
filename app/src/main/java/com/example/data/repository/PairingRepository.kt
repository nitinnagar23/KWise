package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import com.example.R
import com.example.model.DevicePairingToken
import com.example.sync.safeLong
import com.example.sync.safeNullableString
import com.example.sync.safeString
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.Locale

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
        const val PAIRING_CODE_LENGTH = 8
        const val PAIRING_EXPIRY_MS = 10 * 60 * 1000L // 10 minutes
        private const val MAX_PAIRING_ATTEMPTS = 5
        private const val PAIRING_LOCKOUT_MS = 5 * 60 * 1000L
        private const val RATE_LIMIT_PREFS = "khatawise_pairing_rate_limit"

        @Volatile
        private var failedLookupAttempts = 0

        @Volatile
        private var lookupLockoutUntilEpochMs = 0L

        @Volatile
        private var lookupLockoutStartedEpochMs = 0L

        @Volatile
        private var lookupLockoutUntilElapsedMs = 0L

        private fun safeElapsedRealtime(): Long = try {
            SystemClock.elapsedRealtime()
        } catch (_: Throwable) {
            0L
        }

        private fun resolveBootCount(context: Context?): Int {
            val ctx = context ?: runCatching { com.example.KhataWiseApp.instance.applicationContext }.getOrNull() ?: return -1
            return try {
                android.provider.Settings.Global.getInt(
                    ctx.contentResolver,
                    android.provider.Settings.Global.BOOT_COUNT,
                    -1
                )
            } catch (_: Throwable) {
                -1
            }
        }

        private fun resolvePrefs(context: Context?): SharedPreferences? = runCatching {
            val ctx = context ?: com.example.KhataWiseApp.instance.applicationContext
            ctx.getSharedPreferences(RATE_LIMIT_PREFS, Context.MODE_PRIVATE)
        }.getOrNull()

        fun generateSecurePairingCode(random: SecureRandom = SecureRandom()): String {
            return String.format(Locale.US, "%08d", random.nextInt(100_000_000))
        }

        @Synchronized
        internal fun checkLookupRateLimit(
            context: Context? = null,
            nowEpochMs: Long = System.currentTimeMillis(),
            elapsedNow: Long = safeElapsedRealtime()
        ) {
            val prefs = resolvePrefs(context)
            if (prefs != null) {
                failedLookupAttempts = prefs.getInt("failed_attempts", failedLookupAttempts)
                lookupLockoutUntilEpochMs = prefs.getLong("lockout_until_epoch", lookupLockoutUntilEpochMs)
                lookupLockoutStartedEpochMs = prefs.getLong("lockout_started_epoch", lookupLockoutStartedEpochMs)
                val persistedElapsed = prefs.getLong("lockout_until_elapsed", 0L)
                val persistedBoot = prefs.getInt("lockout_boot_count", -1)
                val currentBoot = resolveBootCount(context)
                val sameBoot = when {
                    persistedElapsed <= 0L -> false
                    currentBoot != -1 && persistedBoot != -1 -> currentBoot == persistedBoot
                    elapsedNow > 0L ->
                        elapsedNow <= persistedElapsed &&
                            (persistedElapsed - elapsedNow) <= PAIRING_LOCKOUT_MS + 5_000L
                    else -> true
                }
                if (sameBoot) {
                    lookupLockoutUntilElapsedMs = persistedElapsed
                }
            }

            if (elapsedNow > 0L && lookupLockoutUntilElapsedMs > elapsedNow) {
                val remSec = ((lookupLockoutUntilElapsedMs - elapsedNow + 999L) / 1000L).coerceAtLeast(1L)
                throw IllegalStateException("Too many invalid pairing attempts. Please wait ${remSec}s before trying again.")
            }

            // If wall clock was rolled backwards before lockout started, reset window from current clock
            if (lookupLockoutUntilEpochMs > 0L && lookupLockoutStartedEpochMs > 0L && nowEpochMs < lookupLockoutStartedEpochMs) {
                lookupLockoutStartedEpochMs = nowEpochMs
                lookupLockoutUntilEpochMs = nowEpochMs + PAIRING_LOCKOUT_MS
                prefs?.edit()
                    ?.putLong("lockout_started_epoch", lookupLockoutStartedEpochMs)
                    ?.putLong("lockout_until_epoch", lookupLockoutUntilEpochMs)
                    ?.apply()
            }

            if (lookupLockoutUntilEpochMs > nowEpochMs) {
                val remSec = ((lookupLockoutUntilEpochMs - nowEpochMs + 999L) / 1000L).coerceAtLeast(1L)
                throw IllegalStateException("Too many invalid pairing attempts. Please wait ${remSec}s before trying again.")
            }
            if (lookupLockoutUntilEpochMs != 0L && nowEpochMs >= lookupLockoutUntilEpochMs) {
                resetLookupRateLimit(context)
            }
        }

        @Synchronized
        internal fun recordLookupOutcome(
            success: Boolean,
            context: Context? = null,
            nowEpochMs: Long = System.currentTimeMillis(),
            elapsedNow: Long = safeElapsedRealtime()
        ) {
            val prefs = resolvePrefs(context)
            if (success) {
                resetLookupRateLimit(context)
            } else {
                val prev = prefs?.getInt("failed_attempts", failedLookupAttempts) ?: failedLookupAttempts
                failedLookupAttempts = prev + 1
                val bootCount = resolveBootCount(context)
                if (failedLookupAttempts >= MAX_PAIRING_ATTEMPTS) {
                    lookupLockoutStartedEpochMs = nowEpochMs
                    lookupLockoutUntilEpochMs = nowEpochMs + PAIRING_LOCKOUT_MS
                    lookupLockoutUntilElapsedMs = if (elapsedNow > 0L) elapsedNow + PAIRING_LOCKOUT_MS else 0L
                }
                prefs?.edit()
                    ?.putInt("failed_attempts", failedLookupAttempts)
                    ?.putLong("lockout_started_epoch", lookupLockoutStartedEpochMs)
                    ?.putLong("lockout_until_epoch", lookupLockoutUntilEpochMs)
                    ?.putLong("lockout_until_elapsed", lookupLockoutUntilElapsedMs)
                    ?.putInt("lockout_boot_count", bootCount)
                    ?.apply()
            }
        }

        @Synchronized
        internal fun resetLookupRateLimit(context: Context? = null) {
            failedLookupAttempts = 0
            lookupLockoutUntilEpochMs = 0L
            lookupLockoutStartedEpochMs = 0L
            lookupLockoutUntilElapsedMs = 0L
            resolvePrefs(context)?.edit()?.clear()?.apply()
        }
    }

    private suspend fun ensureAuthenticated() {
        val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
            ?: throw IllegalStateException("Firebase Authentication is not available.")
        if (auth.currentUser != null) return
        try {
            auth.signInAnonymously().await()
        } catch (e: Throwable) {
            throw IllegalStateException(
                "Please sign in with your Google account before verifying a pairing code.",
                e
            )
        }
        if (auth.currentUser == null) {
            throw IllegalStateException("Please sign in with your Google account before verifying a pairing code.")
        }
    }

    /**
     * Admin generates an 8-digit numeric pairing code with a 10-minute countdown.
     */
    suspend fun generatePairingCode(
        adminUid: String,
        storeName: String,
        storeAddress: String = "",
        storePhone: String = "",
        assignedRole: String = "CASHIER"
    ): DevicePairingToken {
        val db = firestore ?: throw IllegalStateException("Cloud Firestore is not available")
        val numericCode = generateSecurePairingCode()
        val safeRole = assignedRole.uppercase(Locale.US).takeIf { it in setOf("CASHIER", "MANAGER", "STAFF") } ?: "CASHIER"

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
        checkLookupRateLimit(context)
        val db = firestore ?: throw IllegalStateException("Cloud Firestore is not available")
        val cleanCode = code.trim().replace(" ", "").replace("-", "")
        if (cleanCode.length !in PAIRING_CODE_LENGTH..12) {
            recordLookupOutcome(false, context)
            throw IllegalArgumentException("Invalid pairing code format. Please enter all $PAIRING_CODE_LENGTH digits.")
        }
        ensureAuthenticated()
        val snapshot = db.collection("pairings").document(cleanCode).get().await()
        if (!snapshot.exists()) {
            recordLookupOutcome(false, context)
            throw IllegalArgumentException("Invalid pairing code. Please verify the $PAIRING_CODE_LENGTH-digit code on the Admin device.")
        }
        val status = snapshot.safeString("status")
        val expiresAt = snapshot.safeLong("expiresAt", 0L) ?: 0L
        if (status != "ACTIVE") {
            recordLookupOutcome(false, context)
            throw IllegalStateException("This pairing code has already been claimed or expired.")
        }
        if (System.currentTimeMillis() >= expiresAt) {
            recordLookupOutcome(false, context)
            throw IllegalStateException("This pairing code expired after 10 minutes. Please request a new code.")
        }
        recordLookupOutcome(true, context)
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
     * Secondary / Staff device submits the 8-digit code to pair with the store.
     */
    suspend fun verifyAndClaimPairingCode(
        code: String,
        staffUid: String,
        staffName: String,
        staffEmail: String = "",
        deviceInfo: String = ""
    ): Result<DevicePairingToken> = runCatching {
        checkLookupRateLimit(context)
        val db = firestore ?: throw IllegalStateException("Cloud Firestore is not available")
        val cleanCode = code.trim().replace(" ", "").replace("-", "")
        if (cleanCode.length !in PAIRING_CODE_LENGTH..12) {
            recordLookupOutcome(false, context)
            throw IllegalArgumentException("Invalid pairing code format.")
        }

        val docRef = db.collection("pairings").document(cleanCode)
        val snapshot = docRef.get().await()

        if (!snapshot.exists()) {
            recordLookupOutcome(false, context)
            throw IllegalArgumentException("Invalid pairing code. Please verify the $PAIRING_CODE_LENGTH-digit code on the Admin device.")
        }

        val status = snapshot.safeString("status")
        val expiresAt = snapshot.safeLong("expiresAt", 0L) ?: 0L
        val claimedByUid = snapshot.safeString("claimedByUid")
        val now = System.currentTimeMillis()

        val isRetryBySameClaimant = status == "CLAIMED" && claimedByUid == staffUid && staffUid.isNotBlank()
        if (status != "ACTIVE" && !isRetryBySameClaimant) {
            recordLookupOutcome(false, context)
            throw IllegalStateException("This pairing code has already been redeemed or expired.")
        }

        if (now >= expiresAt) {
            recordLookupOutcome(false, context)
            throw IllegalStateException("This pairing code expired after 10 minutes. Please ask the store admin to generate a new code.")
        }

        val adminUid = snapshot.safeString("adminUid")
        if (adminUid.isBlank()) {
            recordLookupOutcome(false, context)
            throw IllegalStateException("Invalid pairing token: missing store owner account.")
        }

        recordLookupOutcome(true, context)
        val storeName = snapshot.safeString("storeName", "KhataWise Store").ifBlank { "KhataWise Store" }
        val assignedRole = snapshot.safeString("assignedRole", "CASHIER").ifBlank { "CASHIER" }

        // Claim token in Firestore if not already claimed by this same staff user on an earlier interrupted attempt (N20)
        if (!isRetryBySameClaimant) {
            val updates = mapOf(
                "status" to "CLAIMED",
                "claimedByUid" to staffUid,
                "claimedByName" to staffName.ifBlank { "Staff Device" },
                "claimedByEmail" to staffEmail,
                "claimedAt" to now
            )
            docRef.update(updates).await()
        }

        // Record staff member under store with pairingCode proof required by Firestore rules (N1, N14, N18, N19).
        // New self-paired staff start as PENDING / isApproved = false until approved by the store admin.
        val staffDoc = mapOf(
            "staffUid" to staffUid,
            "name" to staffName.ifBlank { "Staff Device" },
            "email" to staffEmail,
            "deviceInfo" to deviceInfo,
            "role" to assignedRole,
            "pairingCode" to cleanCode,
            "isActive" to true,
            "isApproved" to false,
            "status" to "PENDING",
            "pairedAt" to now,
            "lastActiveAt" to now,
            "updatedAt" to now
        )
        db.collection("users").document(adminUid).collection("staff").document(staffUid).set(staffDoc).await()

        // Consume the pairing token so it can never be reused to re-create or restore a staff document later (N18)
        runCatching {
            docRef.update(
                mapOf(
                    "status" to "USED",
                    "usedAt" to System.currentTimeMillis()
                )
            ).await()
        }

        DevicePairingToken(
            code = cleanCode,
            adminUid = adminUid,
            storeName = storeName,
            storeAddress = snapshot.safeString("storeAddress"),
            storePhone = snapshot.safeString("storePhone"),
            assignedRole = assignedRole,
            status = "USED",
            createdAt = snapshot.safeLong("createdAt") ?: now,
            expiresAt = expiresAt,
            claimedByUid = staffUid,
            claimedByName = staffName,
            claimedAt = now
        )
    }
}
