package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.model.Role
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object SecurityUtils {
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val KEY_LENGTH_BITS = 256

    const val MAX_PIN_ATTEMPTS = 5
    const val LOCKOUT_DURATION_MS = 5 * 60 * 1000L // 5 minutes lockout after 5 wrong tries
    private const val INVITE_MAX_AGE_MS = 24 * 60 * 60 * 1000L // 24 hours max age for local invite codes

    const val INVITE_PREFIX = "KW-"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEYSTORE_HMAC_ALIAS = "khatawise_invite_hmac_key_v3"
    private const val LOCKOUT_PREFS_NAME = "khatawise_pin_lockout_prefs"

    private data class UserLockoutState(
        val failedAttempts: Int = 0,
        val lockoutUntilEpochMs: Long = 0L
    )

    private val userLockouts = ConcurrentHashMap<String, UserLockoutState>()
    private val secureRandom = SecureRandom()

    // Ephemeral fallback key used only when AndroidKeyStore provider is unavailable (e.g. local JVM unit tests).
    // Never hard-coded in source or APK binary.
    private val fallbackHmacSecretKey: SecretKey by lazy {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        SecretKeySpec(bytes, "HmacSHA256")
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

    private fun loadUserState(userId: String, context: Context? = null): UserLockoutState {
        val key = normalizeUserKey(userId)
        val cached = userLockouts[key]
        val prefs = resolveLockoutPrefs(context)
        if (prefs != null) {
            val attemptsKey = "attempts_$key"
            val lockoutKey = "lockout_until_$key"
            if (prefs.contains(attemptsKey) || prefs.contains(lockoutKey)) {
                val persisted = UserLockoutState(
                    failedAttempts = prefs.getInt(attemptsKey, 0),
                    lockoutUntilEpochMs = prefs.getLong(lockoutKey, 0L)
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
            .putLong("lockout_until_$key", state.lockoutUntilEpochMs)
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
     * Verifies a PIN against a stored PBKDF2 salted hash (or legacy SHA-256 hex hash).
     * An empty or blank storedHash is treated explicitly as "no PIN set" and returns false.
     */
    fun verifyPin(pin: String, storedHash: String): Boolean {
        if (storedHash.isBlank() || pin.isBlank()) return false
        return try {
            if (storedHash.startsWith("PBKDF2$", ignoreCase = true)) {
                val parts = storedHash.split("$")
                if (parts.size != 4) return false
                val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: PBKDF2_ITERATIONS
                val salt = b64Decode(parts[2])
                val expectedHash = b64Decode(parts[3])
                if (salt.isEmpty() || expectedHash.isEmpty()) return false
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
     * Verifies PIN while enforcing per-user persistent lockout after 5 wrong tries.
     */
    @Synchronized
    fun verifyPinWithLockout(
        pin: String,
        storedHash: String,
        userId: String = "default",
        nowMs: Long = System.currentTimeMillis(),
        context: Context? = null
    ): PinVerifyResult {
        val key = normalizeUserKey(userId)
        if (isLockedOut(userId = key, nowMs = nowMs, context = context)) {
            return PinVerifyResult.LockedOut(remainingLockoutSeconds(userId = key, nowMs = nowMs, context = context))
        }
        val ok = verifyPin(pin, storedHash)
        return if (ok) {
            resetLockout(userId = key, context = context)
            PinVerifyResult.Success
        } else {
            val current = loadUserState(key, context)
            val nextAttempts = current.failedAttempts + 1
            if (nextAttempts >= MAX_PIN_ATTEMPTS) {
                val until = nowMs + LOCKOUT_DURATION_MS
                saveUserState(key, UserLockoutState(failedAttempts = nextAttempts, lockoutUntilEpochMs = until), context)
                PinVerifyResult.LockedOut(remainingLockoutSeconds(userId = key, nowMs = nowMs, context = context))
            } else {
                saveUserState(key, UserLockoutState(failedAttempts = nextAttempts, lockoutUntilEpochMs = 0L), context)
                PinVerifyResult.Failed(MAX_PIN_ATTEMPTS - nextAttempts)
            }
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
        context: Context? = null
    ): Boolean {
        val key = normalizeUserKey(userId)
        val state = loadUserState(key, context)
        if (state.lockoutUntilEpochMs > nowMs) return true
        if (state.lockoutUntilEpochMs != 0L && nowMs >= state.lockoutUntilEpochMs) {
            saveUserState(key, UserLockoutState(0, 0L), context)
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
        context: Context? = null
    ): Int {
        val state = loadUserState(userId, context)
        val remMs = (state.lockoutUntilEpochMs - nowMs).coerceAtLeast(0L)
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
        saveUserState(userId, UserLockoutState(0, 0L), context)
    }

    @Synchronized
    fun resetLockout(context: Context, userId: String = "default") {
        saveUserState(userId, UserLockoutState(0, 0L), context)
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

    private fun getOrCreateHmacSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(KEYSTORE_HMAC_ALIAS, null) as? SecretKey
            if (existing != null) {
                existing
            } else {
                val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
                keyGen.init(
                    KeyGenParameterSpec.Builder(
                        KEYSTORE_HMAC_ALIAS,
                        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                    ).build()
                )
                keyGen.generateKey()
            }
        } catch (_: Throwable) {
            fallbackHmacSecretKey
        }
    }

    private fun computeHmacSha256(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(getOrCreateHmacSecretKey())
        val bytes = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Generates a device-keystore HMAC-SHA256 signed invite token with KW- prefix.
     * Note: Cross-device staff onboarding uses the 6-digit Firestore pairing token flow.
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
     * Decodes and verifies the device-keystore HMAC-SHA256 signature of an invite code.
     * Rejects unsigned, expired, or tampered Base64 JSON tokens.
     */
    fun parseInviteCode(code: String, nowMs: Long = System.currentTimeMillis()): ParsedInvite? {
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
            if (sig.isBlank() || ts <= 0L) return null
            if (nowMs - ts > INVITE_MAX_AGE_MS || ts - nowMs > 5 * 60 * 1000L) return null

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
