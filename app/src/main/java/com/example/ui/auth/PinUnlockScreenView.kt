package com.example.ui.auth

import com.example.ui.theme.KwSurface
import com.example.ui.theme.kw
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.CattleFeedRepository
import com.example.model.Role
import com.example.model.User
import com.example.util.BiometricAuthManager
import com.example.util.SecurityUtils
import com.example.util.findFragmentActivity
import com.example.util.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

internal val LoginBrandGreen: Color get() = kw(0xFF0E6F43)
internal val LoginMintTop: Color get() = kw(0xFFE9F6F0)
internal val LoginPageBg: Color get() = if (com.example.ui.theme.KwThemeState.isDark) Color(0xFF0F1720) else Color(0xFFF7F8FA)
internal val LoginNavyText: Color get() = kw(0xFF0F172A)
internal val LoginSubtleText: Color get() = kw(0xFF525866)
internal val LoginCardBorder: Color get() = kw(0xFFE2E8F0)
internal val LoginNavyAvatarBg: Color get() = Color(0xFF14305E)
internal val LoginSoftBadgeBg: Color get() = kw(0xFFE8ECF4)
@Composable
internal fun PinUnlockFullScreen(
    repository: CattleFeedRepository,
    users: List<User>,
    shopName: String,
    onUnlockSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fragmentActivity = context.findFragmentActivity()

    BackHandler { onDismiss() }

    var selectedUser by remember(users) {
        mutableStateOf(users.firstOrNull { !it.isDeleted } ?: users.firstOrNull())
    }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }

    val displayUserName = selectedUser?.name?.takeIf { it.isNotBlank() } ?: tr("User", "उपयोगकर्ता")
    val firstName = remember(displayUserName) {
        displayUserName.trim().split(Regex("\\s+")).firstOrNull()?.ifBlank { displayUserName } ?: displayUserName
    }
    val userInitials = remember(displayUserName) {
        computeInitials(displayUserName, fallback = "U")
    }
    val roleLabel = when (selectedUser?.role) {
        Role.ADMINISTRATOR -> tr("Admin", "एडमिन")
        Role.MANAGER -> tr("Manager", "मैनेजर")
        else -> tr("Cashier", "कैशियर")
    }
    val resolvedShopName = shopName.takeIf { it.isNotBlank() } ?: "KhataWise Store"

    val lockedMsgTemplate = tr(
        "Too many failed attempts! Locked for %ds.",
        "बहुत सारे गलत प्रयास! %d सेकंड के लिए लॉक।"
    )
    val incorrectPinMsg = tr("Incorrect PIN. Please try again.", "गलत पिन। कृपया पुनः प्रयास करें।")
    val forgotPinToastMsg = tr(
        "Please ask your shop admin to reset your PIN in Staff & Devices, or sign in with Google.",
        "कृपया अपने दुकान एडमिन से स्टाफ और डिवाइस में अपना पिन रीसेट करने के लिए कहें, या Google से साइन इन करें।"
    )
    val biometricTitle = tr("Unlock KhataWise Register", "खातावाइज रजिस्टर अनलॉक करें")
    val biometricSubtitle = tr(
        "Use Fingerprint or Face Unlock to open your shop register",
        "अपना दुकान रजिस्टर खोलने के लिए फिंगरप्रिंट या फेस अनलॉक का उपयोग करें"
    )

    fun verifyPinNow(enteredPin: String) {
        if (isVerifying || enteredPin.length < 4) return
        isVerifying = true
        scope.launch {
            val activeUsers = repository.getActiveUsersList()
            val primaryTarget = selectedUser ?: activeUsers.firstOrNull()
            val deviceLockoutKey = "device_pin_unlock"
            val userLockoutKey = primaryTarget?.id ?: deviceLockoutKey

            if (SecurityUtils.isPinLockedOut(deviceLockoutKey) || SecurityUtils.isPinLockedOut(userLockoutKey)) {
                val remSec = maxOf(
                    SecurityUtils.getRemainingLockoutSeconds(deviceLockoutKey),
                    SecurityUtils.getRemainingLockoutSeconds(userLockoutKey)
                )
                pinError = String.format(Locale.getDefault(), lockedMsgTemplate, remSec)
                pinInput = ""
                isVerifying = false
                return@launch
            }

            var matchedUser: User? = null

            val candidates = if (primaryTarget != null) {
                listOf(primaryTarget) + activeUsers.filter { it.id != primaryTarget.id }
            } else {
                activeUsers
            }

            for (candidate in candidates) {
                if (candidate.pinHash.isBlank()) {
                    continue
                }
                if (SecurityUtils.verifyPin(enteredPin, candidate.pinHash)) {
                    matchedUser = candidate
                    if (!candidate.pinHash.startsWith("PBKDF2$")) {
                        repository.updateStaffMember(candidate.id, candidate.role, enteredPin)
                    }
                    break
                }
            }

            if (matchedUser != null) {
                SecurityUtils.resetLockout(deviceLockoutKey, context)
                SecurityUtils.resetLockout(userLockoutKey, context)
                SecurityUtils.resetLockout(matchedUser.id, context)
                repository.setActiveUser(matchedUser.id)
                pinInput = ""
                isVerifying = false
                onUnlockSuccess()
            } else {
                val deviceRes = SecurityUtils.recordFailedPinAttempt(
                    userId = deviceLockoutKey,
                    context = context
                )
                val userRes = if (userLockoutKey != deviceLockoutKey) {
                    SecurityUtils.recordFailedPinAttempt(
                        userId = userLockoutKey,
                        context = context
                    )
                } else {
                    deviceRes
                }
                val locked = deviceRes.isLockedOut || userRes.isLockedOut
                val remSec = maxOf(deviceRes.remainingLockoutSeconds, userRes.remainingLockoutSeconds)
                pinError = if (locked) {
                    String.format(Locale.getDefault(), lockedMsgTemplate, remSec)
                } else {
                    incorrectPinMsg
                }
                delay(180L)
                pinInput = ""
                isVerifying = false
            }
        }
    }

    LaunchedEffect(pinInput) {
        if (pinInput.length == 4) {
            verifyPinNow(pinInput)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to LoginMintTop,
                        0.34f to LoginMintTop,
                        0.38f to LoginPageBg,
                        1.0f to LoginPageBg
                    )
                )
            )
    ) {
        // Hidden BasicTextField to maintain compatibility with any test targeting "offline_pin_input"
        BasicTextField(
            value = pinInput,
            onValueChange = { input ->
                val digits = input.filter { it.isDigit() }.take(4)
                pinInput = digits
                pinError = null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier
                .size(1.dp)
                .alpha(0f)
                .testTag("offline_pin_input")
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Row with optional Back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("pin_unlock_back_btn"),
                    shape = RoundedCornerShape(12.dp),
                    color = KwSurface,
                    border = BorderStroke(1.dp, LoginCardBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = LoginNavyText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Initials Circle Avatar ("PC")
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(CircleShape)
                    .background(kw(0xFFE1E8F0)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = userInitials,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = LoginNavyAvatarBg
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // "Welcome back, Pooja"
            Text(
                text = tr("Welcome back, $firstName", "वापसी पर स्वागत है, $firstName"),
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = LoginNavyText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Role Pill · Shop Name ("Cashier · Grocery Store")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = LoginSoftBadgeBg
                ) {
                    Text(
                        text = roleLabel,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = LoginNavyText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = "  ·  $resolvedShopName",
                    fontSize = 13.5.sp,
                    color = LoginSubtleText,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            // 4 PIN Dots Indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 4) {
                    val isFilled = i < pinInput.length
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(if (isFilled) LoginBrandGreen else Color.Transparent)
                            .border(
                                width = 2.dp,
                                color = LoginBrandGreen,
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // "Enter your 4-digit PIN" or error message
            Text(
                text = pinError ?: tr("Enter your 4-digit PIN", "अपना 4-अंकीय पिन दर्ज करें"),
                fontSize = 13.5.sp,
                color = if (pinError != null) kw(0xFFDC2626) else LoginSubtleText,
                fontWeight = if (pinError != null) FontWeight.Bold else FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 4x3 Circular Numeric Keypad
            val digitRows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9")
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                digitRows.forEach { rowDigits ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowDigits.forEach { digit ->
                            PinKeypadCircleButton(
                                onClick = {
                                    if (pinInput.length < 4 && !isVerifying) {
                                        pinError = null
                                        pinInput += digit
                                    }
                                },
                                testTag = "pin_key_$digit"
                            ) {
                                Text(
                                    text = digit,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LoginNavyText
                                )
                            }
                        }
                    }
                }

                // Bottom Row: Biometric | 0 | Backspace
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Biometric / Fingerprint icon button
                    PinKeypadCircleButton(
                        onClick = {
                            if (fragmentActivity != null) {
                                BiometricAuthManager.authenticate(
                                    activity = fragmentActivity,
                                    title = biometricTitle,
                                    subtitle = biometricSubtitle,
                                    onSuccess = {
                                        scope.launch {
                                            val target = selectedUser ?: repository.getActiveUsersList().firstOrNull()
                                            if (target != null) {
                                                SecurityUtils.resetLockout("device_pin_unlock", context)
                                                SecurityUtils.resetLockout(target.id, context)
                                                repository.setActiveUser(target.id)
                                                onUnlockSuccess()
                                            }
                                        }
                                    },
                                    onError = { err ->
                                        pinError = err
                                    }
                                )
                            }
                        },
                        testTag = "biometric_unlock_btn"
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Biometric Unlock",
                            tint = LoginBrandGreen,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // Middle: 0
                    PinKeypadCircleButton(
                        onClick = {
                            if (pinInput.length < 4 && !isVerifying) {
                                pinError = null
                                pinInput += "0"
                            }
                        },
                        testTag = "pin_key_0"
                    ) {
                        Text(
                            text = "0",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = LoginNavyText
                        )
                    }

                    // Right: Backspace
                    PinKeypadCircleButton(
                        onClick = {
                            if (pinInput.isNotEmpty() && !isVerifying) {
                                pinError = null
                                pinInput = pinInput.dropLast(1)
                            }
                        },
                        testTag = "pin_key_backspace"
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Backspace,
                            contentDescription = "Backspace",
                            tint = kw(0xFF475569),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            // Footer: "Forgot PIN? Ask your admin to reset it"
            Text(
                text = tr(
                    "Forgot PIN? Ask your admin to reset it",
                    "पिन भूल गए? इसे रीसेट करने के लिए अपने एडमिन से कहें"
                ),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = LoginBrandGreen,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        Toast.makeText(context, forgotPinToastMsg, Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("forgot_pin_btn")
            )
        }
    }
}

@Composable
private fun PinKeypadCircleButton(
    onClick: () -> Unit,
    testTag: String,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(76.dp)
            .testTag(testTag),
        shape = CircleShape,
        color = KwSurface,
        border = BorderStroke(1.dp, kw(0xFFE5E7EB)),
        shadowElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

internal fun computeInitials(name: String, fallback: String = "SK"): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase(Locale.getDefault())
        parts.size == 1 && parts[0].length >= 2 -> parts[0].take(2).uppercase(Locale.getDefault())
        parts.size == 1 -> parts[0].uppercase(Locale.getDefault())
        else -> fallback
    }
}
