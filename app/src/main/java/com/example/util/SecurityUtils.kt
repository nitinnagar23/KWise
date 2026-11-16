package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object SecurityUtils {
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val KEY_LENGTH_BITS = 256

    const val MAX_PIN_ATTEMPTS = 5
    const val LOCKOUT_DURATION_MS = 5 * 60 * 1000L // 5 minutes lockout after 5 wrong tries
    const val ATTEMPT_DECAY_WINDOW_MS = 15 * 60 * 1000L // 15 minutes decay for sub-lockout wrong tries (S-3)

    private const val LOCKOUT_PREFS_NAME = "khatawise_pin_lockout_prefs"

    private data class UserLockoutState(
        val failedAttempts: Int = 0,
        val lastFailedAtEpochMs: Long = 0L,
        val lockoutUntilEpochMs: Long = 0L,
        val lockoutStartedAtEpochMs: Long = 0L,
        val lockoutUntilElapsedMs: Long = 0L,
        val lockoutBootCount: Int = -1
    )

    private val userLockouts = ConcurrentHashMap<String, UserLockoutState>()
    private val secureRandom = SecureRandom()

    private fun safeElapsedRealtime(): Long {
        return try {
            SystemClock.elapsedRealtime()
        } catch (_: Throwable) {
            0L
        }
    }

    private fun resolveBootCount(context: Context? = null): Int {
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

    private fun resolveLockoutPrefs(context: Context? = null): SharedPreferences? {
        val ctx = context ?: runCatching { com.example.KhataWiseApp.instance.applicationContext }.getOrNull()
        return runCatching {
            ctx?.getSharedPreferences(LOCKOUT_PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
    }

    private fun normalizeUserKey(userId: String): String {
        return userId.trim().ifEmpty { "default" }
    }

    private fun loadUserState(
        userId: String,
        context: Context? = null,
        elapsedRealtimeMs: Long = safeElapsedRealtime()
    ): UserLockoutState {
        val key = normalizeUserKey(userId)
        val cached = userLockouts[key]
        val prefs = resolveLockoutPrefs(context)
        if (prefs != null) {
            val attemptsKey = "attempts_$key"
            val lastFailedKey = "last_failed_$key"
            val lockoutKey = "lockout_until_$key"
            val startedKey = "lockout_started_$key"
            val elapsedKey = "lockout_elapsed_$key"
            val bootKey = "lockout_boot_$key"
            if (prefs.contains(attemptsKey) || prefs.contains(lockoutKey) || prefs.contains(elapsedKey)) {
                val persistedElapsed = prefs.getLong(elapsedKey, 0L)
                val persistedBoot = prefs.getInt(bootKey, -1)
                val currentBoot = resolveBootCount(context)
                val sameBootSession = when {
                    persistedElapsed <= 0L -> false
                    currentBoot != -1 && persistedBoot != -1 -> currentBoot == persistedBoot
                    elapsedRealtimeMs > 0L ->
                        elapsedRealtimeMs <= persistedElapsed &&
                            (persistedElapsed - elapsedRealtimeMs) <= LOCKOUT_DURATION_MS + 5_000L
                    else -> true
                }
                val resolvedElapsed = if (sameBootSession) {
                    persistedElapsed
                } else {
                    cached?.lockoutUntilElapsedMs ?: 0L
                }
                val persisted = UserLockoutState(
                    failedAttempts = prefs.getInt(attemptsKey, 0),
                    lastFailedAtEpochMs = prefs.getLong(lastFailedKey, 0L),
                    lockoutUntilEpochMs = prefs.getLong(lockoutKey, 0L),
                    lockoutStartedAtEpochMs = prefs.getLong(startedKey, 0L),
                    lockoutUntilElapsedMs = resolvedElapsed,
                    lockoutBootCount = persistedBoot
                )
                userLockouts[key] = persisted
                return persisted
            }
        }
        return cached ?: UserLockoutState()
    }

    private fun saveUserState(userId: String, state: UserLockoutState, context: Context? = null) {
        val key = normalizeUserKey(userId)
        userLockouts[key] = state
        val prefs = resolveLockoutPrefs(context) ?: return
        prefs.edit()
            .putInt("attempts_$key", state.failedAttempts)
            .putLong("last_failed_$key", state.lastFailedAtEpochMs)
            .putLong("lockout_until_$key", state.lockoutUntilEpochMs)
            .putLong("lockout_started_$key", state.lockoutStartedAtEpochMs)
            .putLong("lockout_elapsed_$key", state.lockoutUntilElapsedMs)
            .putInt("lockout_boot_$key", state.lockoutBootCount)
            .apply()
    }

    private fun b64EncodeNoWrap(bytes: ByteArray): String {
        return try {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
                ?: java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    private fun b64Decode(input: String): ByteArray {
        return try {
            Base64.decode(input, Base64.DEFAULT)
                ?: java.util.Base64.getMimeDecoder().decode(input)
        } catch (_: Throwable) {
            java.util.Base64.getMimeDecoder().decode(input)
        }
    }

    /**
     * Hashes a 4-digit PIN using salted PBKDF2WithHmacSHA256 (120,000 iterations).
     * Format: PBKDF2$<iterations>$<salt_base64>$<hash_base64>
     */
    fun hashPin(pin: String): String {
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)
        val derived = pbkdf2(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val saltB64 = b64EncodeNoWrap(salt)
        val hashB64 = b64EncodeNoWrap(derived)
        return "PBKDF2$$PBKDF2_ITERATIONS$$saltB64$$hashB64"
    }

    /**
     * Verifies a PIN against a stored PBKDF2 salted hash.
     * An empty, blank, or non-PBKDF2 storedHash is rejected and returns false.
     */
    fun verifyPin(pin: String, storedHash: String): Boolean {
        if (storedHash.isBlank() || pin.isBlank()) return false
        if (!storedHash.startsWith("PBKDF2$", ignoreCase = true)) return false
        return try {
            val parts = storedHash.split("$")
            if (parts.size != 4) return false
            val iterations = parts[1].toIntOrNull()?.takeIf { it >= 10_000 } ?: return false
            val salt = b64Decode(parts[2])
            val expectedHash = b64Decode(parts[3])
            if (salt.isEmpty() || expectedHash.isEmpty()) return false
            val actualHash = pbkdf2(pin.toCharArray(), salt, iterations, expectedHash.size * 8)
            MessageDigest.isEqual(expectedHash, actualHash)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Records a single failed PIN attempt for [userId] and returns the updated [PinVerifyResult].
     * Decays sub-lockout failed attempts after [ATTEMPT_DECAY_WINDOW_MS] (15 minutes) so occasional
     * typos across hours or days do not accumulate into an unexpected lockout (S-3).
     */
    @Synchronized
    fun recordFailedPinAttempt(
        userId: String = "default",
        nowMs: Long = System.currentTimeMillis(),
        context: Context? = null,
        elapsedRealtimeMs: Long = safeElapsedRealtime()
    ): PinVerifyResult {
        val key = normalizeUserKey(userId)
        if (isLockedOut(nowMs = nowMs, userId = key, context = context, elapsedRealtimeMs = elapsedRealtimeMs)) {
            return PinVerifyResult.LockedOut(
                remainingLockoutSeconds(nowMs = nowMs, userId = key, context = context, elapsedRealtimeMs = elapsedRealtimeMs)
            )
        }
        val current = loadUserState(key, context, elapsedRealtimeMs)
        val effectiveBaseAttempts = if (
            current.lastFailedAtEpochMs > 0L &&
            nowMs > current.lastFailedAtEpochMs &&
            (nowMs - current.lastFailedAtEpochMs) >= ATTEMPT_DECAY_WINDOW_MS
        ) {
            0
        } else {
            current.failedAttempts
        }
        val nextAttempts = effectiveBaseAttempts + 1
        return if (nextAttempts >= MAX_PIN_ATTEMPTS) {
            val untilEpoch = nowMs + LOCKOUT_DURATION_MS
            val untilElapsed = if (elapsedRealtimeMs > 0L) elapsedRealtimeMs + LOCKOUT_DURATION_MS else 0L
            val bootCount = resolveBootCount(context)
            saveUserState(
                key,
                UserLockoutState(
                    failedAttempts = nextAttempts,
                    lastFailedAtEpochMs = nowMs,
                    lockoutUntilEpochMs = untilEpoch,
                    lockoutStartedAtEpochMs = nowMs,
                    lockoutUntilElapsedMs = untilElapsed,
                    lockoutBootCount = bootCount
                ),
                context
            )
            PinVerifyResult.LockedOut(
                remainingLockoutSeconds(nowMs = nowMs, userId = key, context = context, elapsedRealtimeMs = elapsedRealtimeMs)
            )
        } else {
            saveUserState(
                key,
                UserLockoutState(
                    failedAttempts = nextAttempts,
                    lastFailedAtEpochMs = nowMs,
                    lockoutUntilEpochMs = 0L,
                    lockoutStartedAtEpochMs = 0L,
                    lockoutUntilElapsedMs = 0L,
                    lockoutBootCount = -1
                ),
                context
            )
            PinVerifyResult.Failed(MAX_PIN_ATTEMPTS - nextAttempts)
        }
    }

    /**
     * Verifies PIN while enforcing per-user persistent lockout after 5 wrong tries.
     * Uses both monotonic elapsedRealtime (persisted with boot count) and wall-clock time so
     * advancing the device clock—even after killing the app—cannot end an active lockout early.
     */
    @Synchronized
    fun verifyPinWithLockout(
        pin: String,
        storedHash: String,
        userId: String = "default",
        nowMs: Long = System.currentTimeMillis(),
        context: Context? = null,
        elapsedRealtimeMs: Long = safeElapsedRealtime()
    ): PinVerifyResult {
        val key = normalizeUserKey(userId)
        if (isLockedOut(nowMs = nowMs, userId = key, context = context, elapsedRealtimeMs = elapsedRealtimeMs)) {
            return PinVerifyResult.LockedOut(
                remainingLockoutSeconds(nowMs = nowMs, userId = key, context = context, elapsedRealtimeMs = elapsedRealtimeMs)
            )
        }
        val ok = verifyPin(pin, storedHash)
        return if (ok) {
            resetLockout(userId = key, context = context)
            PinVerifyResult.Success
        } else {
            recordFailedPinAttempt(
                userId = key,
                nowMs = nowMs,
                context = context,
                elapsedRealtimeMs = elapsedRealtimeMs
            )
        }
    }

    @Synchronized
    fun verifyPinWithLockout(
        context: Context,
        userId: String,
        pin: String,
        storedHash: String
    ): PinVerifyResult = verifyPinWithLockout(
        pin = pin,
        storedHash = storedHash,
        userId = userId,
        context = context
    )

    @Synchronized
    fun isLockedOut(
        nowMs: Long = System.currentTimeMillis(),
        userId: String = "default",
        context: Context? = null,
        elapsedRealtimeMs: Long = safeElapsedRealtime()
    ): Boolean {
        val key = normalizeUserKey(userId)
        var state = loadUserState(key, context, elapsedRealtimeMs)

        // 1. Monotonic elapsedRealtime check (persisted across process death within the same boot)
        if (elapsedRealtimeMs > 0L && state.lockoutUntilElapsedMs > 0L) {
            if (elapsedRealtimeMs < state.lockoutUntilElapsedMs) {
                return true
            }
        }

        // 2. If wall clock was rolled backwards before lockout started, reset window from current clock
        if (state.lockoutUntilEpochMs > 0L && state.lockoutStartedAtEpochMs > 0L && nowMs < state.lockoutStartedAtEpochMs) {
            state = state.copy(
                lockoutStartedAtEpochMs = nowMs,
                lockoutUntilEpochMs = nowMs + LOCKOUT_DURATION_MS
            )
            saveUserState(key, state, context)
            return true
        }

        // 3. Persisted wall-clock check (survives device reboot)
        if (state.lockoutUntilEpochMs > nowMs) return true
        if (state.lockoutUntilEpochMs != 0L && nowMs >= state.lockoutUntilEpochMs) {
            saveUserState(key, UserLockoutState(), context)
        }
        return false
    }

    @Synchronized
    fun isPinLockedOut(userId: String = "default"): Boolean =
        isLockedOut(userId = userId)

    @Synchronized
    fun remainingLockoutSeconds(
        nowMs: Long = System.currentTimeMillis(),
        userId: String = "default",
        context: Context? = null,
        elapsedRealtimeMs: Long = safeElapsedRealtime()
    ): Int {
        val state = loadUserState(userId, context, elapsedRealtimeMs)
        val elapsedRemMs = if (elapsedRealtimeMs > 0L && state.lockoutUntilElapsedMs > elapsedRealtimeMs) {
            state.lockoutUntilElapsedMs - elapsedRealtimeMs
        } else {
            0L
        }
        val wallRemMs = (state.lockoutUntilEpochMs - nowMs).coerceAtLeast(0L)
        val remMs = maxOf(elapsedRemMs, wallRemMs)
        return ((remMs + 999L) / 1000L).toInt()
    }

    @Synchronized
    fun getRemainingLockoutSeconds(userId: String = "default"): Int =
        remainingLockoutSeconds(userId = userId)

    @Synchronized
    fun getFailedAttempts(userId: String = "default"): Int =
        loadUserState(userId).failedAttempts

    @Synchronized
    fun resetPinLockout() {
        userLockouts.clear()
        resolveLockoutPrefs()?.edit()?.clear()?.apply()
    }

    @Synchronized
    fun resetLockout(userId: String = "default", context: Context? = null) {
        saveUserState(userId, UserLockoutState(), context)
    }

    @Synchronized
    fun resetLockout(context: Context, userId: String = "default") {
        saveUserState(userId, UserLockoutState(), context)
    }

    @Synchronized
    fun getLockoutStatus(context: Context, userId: String = "default"): PinVerifyResult {
        val key = normalizeUserKey(userId)
        return if (isLockedOut(userId = key, context = context)) {
            PinVerifyResult.LockedOut(remainingLockoutSeconds(userId = key, context = context))
        } else {
            val attempts = loadUserState(key, context).failedAttempts
            PinVerifyResult.Failed((MAX_PIN_ATTEMPTS - attempts).coerceAtLeast(0))
        }
    }

    private fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int, bits: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, bits)
        try {
            val skf = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            return skf.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

sealed class PinVerifyResult {
    object Success : PinVerifyResult()
    data class Failed(val remainingTries: Int) : PinVerifyResult()
    data class LockedOut(val remainingSeconds: Int) : PinVerifyResult()

    val isSuccess: Boolean get() = this is Success
    val success: Boolean get() = this is Success
    val isLockedOut: Boolean get() = this is LockedOut
    val remainingLockoutSeconds: Int get() = (this as? LockedOut)?.remainingSeconds ?: 0
    val attemptsRemaining: Int get() = (this as? Failed)?.remainingTries ?: 0
}
