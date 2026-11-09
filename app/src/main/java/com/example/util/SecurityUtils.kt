package com.example.util

import android.content.Context
import android.util.Base64
import com.example.model.Role
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object SecurityUtils {
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 12000
    private const val SALT_BYTES = 16
    private const val KEY_LENGTH_BITS = 256

    const val MAX_PIN_ATTEMPTS = 5
    const val LOCKOUT_DURATION_MS = 5 * 60 * 1000L // 5 minutes lockout after 5 wrong tries

    const val INVITE_PREFIX = "KW-"
    private const val HMAC_SECRET = "KhataWise_Store_Hmac_Key_v2_9f8a7b6c5d4e3f2a"

    @Volatile
    private var failedPinAttempts: Int = 0

    @Volatile
    private var lockoutUntilEpochMs: Long = 0L

    private val secureRandom = SecureRandom()

    /**
     * Hashes a 4-digit PIN using salted PBKDF2WithHmacSHA256.
     * Format: PBKDF2$<iterations>$<salt_base64>$<hash_base64>
     */
    fun hashPin(pin: String): String {
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)
        val derived = pbkdf2(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashB64 = Base64.encodeToString(derived, Base64.NO_WRAP)
        return "PBKDF2$$PBKDF2_ITERATIONS$$saltB64$$hashB64"
    }

    /**
     * Verifies a PIN against a stored PBKDF2 salted hash (or legacy SHA-256 hex hash).
     */
    fun verifyPin(pin: String, storedHash: String): Boolean {
        if (storedHash.isEmpty()) return true
        return try {
            if (storedHash.startsWith("PBKDF2$", ignoreCase = true)) {
                val parts = storedHash.split("$")
                if (parts.size != 4) return false
                val iterations = parts[1].toIntOrNull() ?: PBKDF2_ITERATIONS
                val salt = Base64.decode(parts[2], Base64.NO_WRAP)
                val expectedHash = Base64.decode(parts[3], Base64.NO_WRAP)
                val actualHash = pbkdf2(pin.toCharArray(), salt, iterations, expectedHash.size * 8)
                MessageDigest.isEqual(expectedHash, actualHash)
            } else {
                // Backwards compatibility for legacy SHA-256 hex hashes
                val digest = MessageDigest.getInstance("SHA-256")
                val actualHex = digest.digest(pin.toByteArray(StandardCharsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                MessageDigest.isEqual(
                    actualHex.toByteArray(StandardCharsets.UTF_8),
                    storedHash.toByteArray(StandardCharsets.UTF_8)
                )
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Verifies PIN while enforcing lockout after 5 wrong tries.
     */
    @Synchronized
    fun verifyPinWithLockout(
        pin: String,
        storedHash: String,
        userId: String = "default",
        nowMs: Long = System.currentTimeMillis()
    ): PinVerifyResult {
        if (isLockedOut(nowMs)) {
            return PinVerifyResult.LockedOut(remainingLockoutSeconds(nowMs))
        }
        val ok = verifyPin(pin, storedHash)
        return if (ok) {
            resetPinLockout()
            PinVerifyResult.Success
        } else {
            failedPinAttempts++
            if (failedPinAttempts >= MAX_PIN_ATTEMPTS) {
                lockoutUntilEpochMs = nowMs + LOCKOUT_DURATION_MS
                PinVerifyResult.LockedOut(remainingLockoutSeconds(nowMs))
            } else {
                PinVerifyResult.Failed(MAX_PIN_ATTEMPTS - failedPinAttempts)
            }
        }
    }

    @Synchronized
    fun verifyPinWithLockout(
        context: Context,
        userId: String,
        pin: String,
        storedHash: String
    ): PinVerifyResult = verifyPinWithLockout(pin = pin, storedHash = storedHash, userId = userId)

    @Synchronized
    fun isLockedOut(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (lockoutUntilEpochMs > nowMs) return true
        if (lockoutUntilEpochMs != 0L && nowMs >= lockoutUntilEpochMs) {
            lockoutUntilEpochMs = 0L
            failedPinAttempts = 0
        }
        return false
    }

    @Synchronized
    fun isPinLockedOut(userId: String = "default"): Boolean = isLockedOut()

    @Synchronized
    fun remainingLockoutSeconds(nowMs: Long = System.currentTimeMillis()): Int {
        val remMs = (lockoutUntilEpochMs - nowMs).coerceAtLeast(0L)
        return ((remMs + 999L) / 1000L).toInt()
    }

    @Synchronized
    fun getRemainingLockoutSeconds(userId: String = "default"): Int = remainingLockoutSeconds()

    @Synchronized
    fun getFailedAttempts(): Int = failedPinAttempts

    @Synchronized
    fun resetPinLockout() {
        failedPinAttempts = 0
        lockoutUntilEpochMs = 0L
    }

    @Synchronized
    fun resetLockout(userId: String = "default") = resetPinLockout()

    @Synchronized
    fun resetLockout(context: Context, userId: String = "default") = resetPinLockout()

    @Synchronized
    fun getLockoutStatus(context: Context, userId: String = "default"): PinVerifyResult {
        return if (isLockedOut()) {
            PinVerifyResult.LockedOut(remainingLockoutSeconds())
        } else {
            PinVerifyResult.Failed((MAX_PIN_ATTEMPTS - failedPinAttempts).coerceAtLeast(0))
        }
    }

    private fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int, bits: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, bits)
        val skf = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        return skf.generateSecret(spec).encoded
    }

    private fun computeHmacSha256(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(HMAC_SECRET.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)
        val bytes = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Generates an HMAC-SHA256 signed invite token with KW- prefix.
     * Note: Staff device onboarding primarily uses the 6-digit Firestore pairing token flow.
     */
    fun generateInviteCode(userId: String, name: String, role: Role, timestamp: Long = System.currentTimeMillis()): String {
        val canonical = "v=2|uid=$userId|name=$name|role=${role.name}|ts=$timestamp"
        val signature = computeHmacSha256(canonical)
        val json = JSONObject().apply {
            put("v", 2)
            put("uid", userId)
            put("name", name)
            put("role", role.name)
            put("ts", timestamp)
            put("sig", signature)
        }
        val encoded = Base64.encodeToString(json.toString().toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        return "$INVITE_PREFIX$encoded"
    }

    fun isLegacyPinHash(storedHash: String): Boolean {
        return storedHash.isNotEmpty() && !storedHash.startsWith("PBKDF2$", ignoreCase = true)
    }

    /**
     * Decodes and verifies the HMAC-SHA256 signature of an invite code.
     * Rejects unsigned or tampered Base64 JSON tokens.
     */
    fun parseInviteCode(code: String): ParsedInvite? {
        return try {
            val cleanCode = code.trim()
            if (!cleanCode.startsWith(INVITE_PREFIX)) return null
            val base64Str = cleanCode.substring(INVITE_PREFIX.length)
            val jsonStr = String(Base64.decode(base64Str, Base64.DEFAULT), StandardCharsets.UTF_8)
            val obj = JSONObject(jsonStr)
            val userId = obj.getString("uid")
            val name = obj.getString("name")
            val roleStr = obj.getString("role")
            val ts = obj.optLong("ts", 0L)
            val sig = obj.optString("sig", "")
            if (sig.isBlank()) return null

            val expectedSig = computeHmacSha256("v=2|uid=$userId|name=$name|role=$roleStr|ts=$ts")
            if (!MessageDigest.isEqual(sig.toByteArray(StandardCharsets.UTF_8), expectedSig.toByteArray(StandardCharsets.UTF_8))) {
                return null
            }

            val role = Role.valueOf(roleStr)
            ParsedInvite(userId = userId, name = name, role = role)
        } catch (e: Exception) {
            null
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

data class ParsedInvite(
    val userId: String,
    val name: String,
    val role: Role
)
