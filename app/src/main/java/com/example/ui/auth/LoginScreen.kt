package com.example.ui.auth

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.GoogleAuthManager
import com.example.data.repository.CattleFeedRepository
import com.example.data.repository.PairingRepository
import com.example.model.DevicePairingToken
import com.example.model.Role
import com.example.model.SyncStatus
import com.example.model.User
import com.example.ui.theme.CGreen
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatTextMuted
import com.example.ui.theme.FlatTextPrimary
import com.example.util.SecurityUtils
import com.example.util.findActivity
import com.example.util.tr
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(
    repository: CattleFeedRepository,
    onLoginSuccess: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val pairingRepository = remember { PairingRepository(context) }

    val allUsers by repository.allUsers.collectAsState(initial = emptyList())
    val settings by repository.settingsFlow.collectAsState(initial = null)
    val currentLanguage = settings?.language ?: "en"
    val shopName = settings?.shopName?.ifBlank { "KhataWise Store" } ?: "KhataWise Store"

    var isAuthenticating by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Staff pairing state
    var isPairingMode by remember { mutableStateOf(false) }
    var pairingCodeInput by remember { mutableStateOf("") }
    var isClaimingPairing by remember { mutableStateOf(false) }
    var pairingErrorMessage by remember { mutableStateOf<String?>(null) }
    var pendingShopConfirmationToken by remember { mutableStateOf<DevicePairingToken?>(null) }
    var waitingApprovalToken by remember { mutableStateOf<DevicePairingToken?>(null) }
    var showQrScanDialog by remember { mutableStateOf(false) }
    var qrInputText by remember { mutableStateOf("") }

    // Staff profile details auto-detected from Google account and current Android device
    val currentGoogleUser = GoogleAuthManager.currentUser.value
    var staffDisplayName by remember(currentGoogleUser) {
        mutableStateOf(currentGoogleUser?.displayName?.takeIf { it.isNotBlank() } ?: "")
    }
    var staffEmail by remember(currentGoogleUser) {
        mutableStateOf(currentGoogleUser?.email?.takeIf { it.isNotBlank() } ?: "")
    }
    val deviceModelName = remember {
        val mfr = android.os.Build.MANUFACTURER?.trim().orEmpty()
        val model = android.os.Build.MODEL?.trim().orEmpty()
        when {
            model.startsWith(mfr, ignoreCase = true) -> model
            mfr.isNotBlank() && model.isNotBlank() -> "${mfr.replaceFirstChar { it.uppercase() }} $model"
            model.isNotBlank() -> model
            else -> "Android Phone"
        }
    }
    val adminDisplayName = remember(allUsers) {
        allUsers.firstOrNull { it.role == Role.ADMINISTRATOR && !it.isDeleted }?.name?.takeIf { it.isNotBlank() }
            ?: ""
    }

    // PIN unlock full-screen state
    var showPinUnlockDialog by remember { mutableStateOf(false) }

    // New Store profile dialog state
    var showNewStoreDialog by remember { mutableStateOf(false) }
    var pendingAdminUser by remember { mutableStateOf<FirebaseUser?>(null) }
    var newStoreName by remember { mutableStateOf("") }
    var newOwnerName by remember { mutableStateOf("") }
    var newAdminPin by remember { mutableStateOf("") }

    var isHandlingPostAuth by remember { mutableStateOf(false) }

    val enterAllSixDigitsMsg = tr(
        "Please enter all 6 digits of the pairing code.",
        "कृपया पेयरिंग कोड के सभी 6 अंक दर्ज करें।"
    )

    BackHandler(enabled = isPairingMode && pendingShopConfirmationToken == null && waitingApprovalToken == null) {
        isPairingMode = false
        pairingErrorMessage = null
    }
    BackHandler(enabled = !isPairingMode && onBack != null && !showPinUnlockDialog) {
        onBack?.invoke()
    }

    fun handleGoogleAuthSuccess(firebaseUser: FirebaseUser) {
        if (isHandlingPostAuth) return
        isHandlingPostAuth = true
        isAuthenticating = true
        authError = null
        statusMessage = "Opening store register..."

        scope.launch {
            try {
                val savedUser = withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                    val existingLocalUsers = repository.getActiveUsersList()
                    val matchedUser = existingLocalUsers.find {
                        it.id == firebaseUser.uid ||
                            (!firebaseUser.email.isNullOrBlank() && it.email.equals(firebaseUser.email, ignoreCase = true)) ||
                            (!firebaseUser.displayName.isNullOrBlank() && it.name.equals(firebaseUser.displayName, ignoreCase = true))
                    }

                    val resolvedName = matchedUser?.name?.takeIf { it.isNotBlank() }
                        ?: firebaseUser.displayName?.takeIf { it.isNotBlank() }
                        ?: firebaseUser.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                        ?: "Store Admin"

                    val adminUser = User(
                        id = firebaseUser.uid,
                        name = resolvedName,
                        email = firebaseUser.email ?: (matchedUser?.email ?: ""),
                        phone = firebaseUser.phoneNumber ?: (matchedUser?.phone ?: ""),
                        deviceInfo = deviceModelName,
                        role = matchedUser?.role ?: Role.ADMINISTRATOR,
                        pinHash = matchedUser?.pinHash ?: "",
                        isActive = true,
                        isDeleted = false,
                        syncStatus = SyncStatus.SYNCED
                    )

                    val persisted = repository.insertUser(adminUser)
                    repository.setActiveUser(persisted.id)

                    com.example.PashuAaharApp.instance.firestoreSyncEngine.startRealtimeListeners(firebaseUser.uid)
                    com.example.PashuAaharApp.instance.firestoreSyncEngine.triggerSync()

                    persisted
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Welcome, ${savedUser.name}!", Toast.LENGTH_SHORT).show()
                    onLoginSuccess()
                }
            } catch (e: Exception) {
                authError = "Sign-in error: ${e.message}"
            } finally {
                isHandlingPostAuth = false
                isAuthenticating = false
                statusMessage = null
            }
        }
    }

    LaunchedEffect(Unit) {
        if (onBack != null) return@LaunchedEffect
        val localActive = repository.getActiveUser()
        if (localActive != null) {
            onLoginSuccess()
            return@LaunchedEffect
        }
        val currentUser = GoogleAuthManager.currentUser.value
        if (currentUser != null) {
            handleGoogleAuthSuccess(currentUser)
        }
    }

    // Screen 5: Full-screen PIN Unlock view (Login · PIN unlock.pdf)
    if (showPinUnlockDialog) {
        PinUnlockFullScreen(
            repository = repository,
            users = allUsers,
            shopName = shopName,
            onUnlockSuccess = {
                showPinUnlockDialog = false
                onLoginSuccess()
            },
            onDismiss = { showPinUnlockDialog = false }
        )
        return
    }

    // Screen 4: Full-screen Waiting for approval view (Login · Waiting for approval.pdf)
    val activeWaitingToken = waitingApprovalToken
    if (activeWaitingToken != null) {
        StaffWaitingForApprovalScreen(
            token = activeWaitingToken,
            adminName = adminDisplayName,
            staffName = staffDisplayName,
            staffEmail = staffEmail,
            deviceModel = deviceModelName,
            onApprovedOrContinue = {
                val gUser = GoogleAuthManager.currentUser.value
                if (gUser != null) {
                    scope.launch {
                        claimDeviceWithCode(
                            code = activeWaitingToken.code,
                            staffUser = gUser,
                            pairingRepo = pairingRepository,
                            repo = repository,
                            onSuccess = {
                                waitingApprovalToken = null
                                pendingShopConfirmationToken = null
                                onLoginSuccess()
                            },
                            onError = { err ->
                                waitingApprovalToken = null
                                pendingShopConfirmationToken = null
                                pairingErrorMessage = err
                            }
                        )
                    }
                } else {
                    waitingApprovalToken = null
                    pendingShopConfirmationToken = null
                    if (allUsers.isNotEmpty()) {
                        onLoginSuccess()
                    }
                }
            },
            onCancelRequest = {
                waitingApprovalToken = null
                pendingShopConfirmationToken = null
            }
        )
        return
    }

    // Screen 3: Full-screen Confirm & Join view (Login · Staff confirm & join.pdf)
    val shopToken = pendingShopConfirmationToken
    if (shopToken != null) {
        StaffConfirmAndJoinScreen(
            token = shopToken,
            adminName = adminDisplayName,
            staffName = staffDisplayName,
            staffEmail = staffEmail,
            deviceModel = deviceModelName,
            isSubmitting = isClaimingPairing,
            onSwitchGoogleAccount = {
                if (activity != null) {
                    GoogleAuthManager.onGoogleSignInClicked(
                        activity = activity,
                        credentialManager = credentialManager,
                        onAuthSuccess = { user ->
                            staffDisplayName = user.displayName?.takeIf { it.isNotBlank() } ?: staffDisplayName
                            staffEmail = user.email?.takeIf { it.isNotBlank() } ?: staffEmail
                        },
                        onAuthError = { err ->
                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                        },
                        scope = scope
                    )
                }
            },
            onConfirmAndJoin = {
                val gUser = GoogleAuthManager.currentUser.value
                if (gUser != null) {
                    isClaimingPairing = true
                    scope.launch {
                        claimDeviceWithCode(
                            code = shopToken.code,
                            staffUser = gUser,
                            pairingRepo = pairingRepository,
                            repo = repository,
                            onSuccess = {
                                isClaimingPairing = false
                                waitingApprovalToken = shopToken
                            },
                            onError = {
                                isClaimingPairing = false
                                waitingApprovalToken = shopToken
                            }
                        )
                    }
                } else if (activity != null) {
                    isClaimingPairing = true
                    GoogleAuthManager.onGoogleSignInClicked(
                        activity = activity,
                        credentialManager = credentialManager,
                        onAuthSuccess = { signedInStaff ->
                            staffDisplayName = signedInStaff.displayName.orEmpty()
                            staffEmail = signedInStaff.email.orEmpty()
                            scope.launch {
                                claimDeviceWithCode(
                                    code = shopToken.code,
                                    staffUser = signedInStaff,
                                    pairingRepo = pairingRepository,
                                    repo = repository,
                                    onSuccess = {
                                        isClaimingPairing = false
                                        waitingApprovalToken = shopToken
                                    },
                                    onError = {
                                        isClaimingPairing = false
                                        waitingApprovalToken = shopToken
                                    }
                                )
                            }
                        },
                        onAuthError = { err ->
                            isClaimingPairing = false
                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                        },
                        scope = scope
                    )
                }
            },
            onCancel = {
                pendingShopConfirmationToken = null
            }
        )
        return
    }

    // Screen 1 & Screen 2: Store Owner & Staff Device Code (Login · Store owner.pdf & Login · Staff device code.pdf)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to LoginMintTop,
                        0.32f to LoginMintTop,
                        0.40f to LoginPageBg,
                        1.0f to LoginPageBg
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Optional Back button on left + Language Pill ("हिंदी" / "EN") on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    Surface(
                        onClick = onBack,
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("login_back_button"),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
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
                } else {
                    Spacer(modifier = Modifier.size(42.dp))
                }

                // Language Toggle Pill ("हिंदी" / "English")
                Surface(
                    onClick = {
                        val nextLang = if (currentLanguage == "hi") "en" else "hi"
                        scope.launch {
                            repository.updateLanguage(nextLang)
                        }
                    },
                    shape = RoundedCornerShape(50),
                    color = Color.White,
                    border = BorderStroke(1.dp, LoginCardBorder),
                    shadowElevation = 1.dp,
                    modifier = Modifier.testTag("login_language_toggle_btn")
                ) {
                    Text(
                        text = if (currentLanguage == "hi") "English" else "हिंदी",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = LoginNavyText,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Brand App Icon
            Image(
                painter = painterResource(R.drawable.khatawise_logo),
                contentDescription = "KhataWise Logo",
                modifier = Modifier
                    .size(82.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .shadow(6.dp, RoundedCornerShape(20.dp))
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Two-tone "KhataWise" title ("Khata" in dark navy, "Wise" in brand green)
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = LoginNavyText)) {
                        append("Khata")
                    }
                    withStyle(SpanStyle(color = LoginBrandGreen)) {
                        append("Wise")
                    }
                },
                fontSize = 31.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = tr(
                    "Smart Shop Register & Cloud Ledger",
                    "स्मार्ट दुकान रजिस्टर और क्लाउड खाता"
                ),
                fontSize = 14.sp,
                color = LoginSubtleText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Main White Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                border = BorderStroke(1.dp, LoginCardBorder),
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Segmented Tab Switcher: Store owner | Staff device
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF1F2F6)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Store owner tab
                            Surface(
                                onClick = {
                                    isPairingMode = false
                                    authError = null
                                    pairingErrorMessage = null
                                },
                                shape = RoundedCornerShape(11.dp),
                                color = if (!isPairingMode) Color.White else Color.Transparent,
                                shadowElevation = if (!isPairingMode) 1.5.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("store_owner_tab_btn")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = tr("Store owner", "दुकान मालिक"),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!isPairingMode) LoginBrandGreen else LoginSubtleText
                                    )
                                }
                            }

                            // Staff device tab
                            Surface(
                                onClick = {
                                    isPairingMode = true
                                    authError = null
                                    pairingErrorMessage = null
                                },
                                shape = RoundedCornerShape(11.dp),
                                color = if (isPairingMode) Color.White else Color.Transparent,
                                shadowElevation = if (isPairingMode) 1.5.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("toggle_pairing_mode_btn")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = tr("Staff device", "स्टाफ डिवाइस"),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPairingMode) LoginBrandGreen else LoginSubtleText
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    if (authError != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFEE2E2)
                            ),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = tr("Authentication notice", "प्रमाणीकरण सूचना"),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF991B1B)
                                    )
                                }
                                Text(
                                    text = authError!!,
                                    fontSize = 12.5.sp,
                                    color = Color(0xFF991B1B),
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }

                    if (isHandlingPostAuth) {
                        com.example.ui.components.OpeningOrRestoringShopCard(
                            progressPercent = 62,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )
                    } else if (statusMessage != null) {
                        Text(
                            text = statusMessage!!,
                            fontSize = 13.sp,
                            color = LoginBrandGreen,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }

                    if (!isPairingMode) {
                        // ---------------------------------------------------------
                        // SCREEN 1: Store Owner Tab (Login · Store owner.pdf)
                        // ---------------------------------------------------------
                        Text(
                            text = tr("Welcome back", "वापसी पर स्वागत है"),
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = LoginNavyText
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = tr(
                                "Sign in with Google to open your shop ledger and keep real-time cloud backup on.",
                                "अपना दुकान खाता खोलने और रियल-टाइम क्लाउड बैकअप चालू रखने के लिए Google से साइन इन करें।"
                            ),
                            fontSize = 14.sp,
                            color = LoginSubtleText,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Outlined White "Continue with Google" Button
                        Surface(
                            onClick = {
                                if (!isAuthenticating && activity != null) {
                                    isAuthenticating = true
                                    authError = null
                                    GoogleAuthManager.onGoogleSignInClicked(
                                        activity = activity,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = { user ->
                                            handleGoogleAuthSuccess(user)
                                        },
                                        onAuthError = { errorMsg ->
                                            isAuthenticating = false
                                            authError = errorMsg
                                        },
                                        scope = scope,
                                        onAuthCancelled = {
                                            isAuthenticating = false
                                            authError = "Google account picker was cancelled or unavailable on this device."
                                        }
                                    )
                                }
                            },
                            enabled = !isAuthenticating && activity != null,
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("google_login_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isAuthenticating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = LoginBrandGreen,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = tr("Connecting to Google...", "Google से जुड़ रहा है..."),
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LoginNavyText
                                    )
                                } else {
                                    // Circular "G" Badge
                                    Surface(
                                        modifier = Modifier.size(26.dp),
                                        shape = CircleShape,
                                        color = Color.White,
                                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "G",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF4285F4)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = tr("Continue with Google", "Google के साथ जारी रखें"),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LoginNavyText
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 3 Feature Bullet Rows
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            StoreOwnerFeatureRow(
                                icon = Icons.Outlined.CloudQueue,
                                text = tr(
                                    "Backed up to your own Google account",
                                    "आपके अपने Google खाते में सुरक्षित बैकअप"
                                )
                            )
                            StoreOwnerFeatureRow(
                                icon = Icons.Default.Check,
                                text = tr(
                                    "Locked with biometric & PIN",
                                    "बायोमेट्रिक और पिन से सुरक्षित"
                                ),
                                onClick = { showPinUnlockDialog = true },
                                testTag = "offline_pin_unlock_btn"
                            )
                            StoreOwnerFeatureRow(
                                icon = Icons.Default.Refresh,
                                text = tr(
                                    "Works offline, syncs when online",
                                    "ऑफलाइन काम करता है, ऑनलाइन होने पर सिंक होता है"
                                )
                            )
                        }
                    } else {
                        // ---------------------------------------------------------
                        // SCREEN 2: Staff Device Tab (Login · Staff device code.pdf)
                        // ---------------------------------------------------------
                        Text(
                            text = tr("Connect this phone", "इस फ़ोन को जोड़ें"),
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = LoginNavyText
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = tr(
                                "Enter the 6-digit code from your shop admin. It works for 10 minutes.",
                                "अपने दुकान एडमिन से प्राप्त 6-अंकीय कोड दर्ज करें। यह 10 मिनट तक काम करता है।"
                            ),
                            fontSize = 14.sp,
                            color = LoginSubtleText,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        if (pairingErrorMessage != null) {
                            Text(
                                text = pairingErrorMessage!!,
                                fontSize = 12.5.sp,
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }

                        // 6 Individual Rounded Digit Boxes
                        StaffSixDigitCodeInput(
                            code = pairingCodeInput,
                            onCodeChange = { updated ->
                                pairingCodeInput = updated
                                pairingErrorMessage = null
                            }
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Primary Green "Connect device" Button
                        Button(
                            onClick = {
                                if (pairingCodeInput.length != 6) {
                                    pairingErrorMessage = enterAllSixDigitsMsg
                                    return@Button
                                }
                                isClaimingPairing = true
                                pairingErrorMessage = null
                                scope.launch {
                                    val result = pairingRepository.getPairingToken(pairingCodeInput)
                                    result.onSuccess { token ->
                                        isClaimingPairing = false
                                        pendingShopConfirmationToken = token
                                    }.onFailure { ex ->
                                        isClaimingPairing = false
                                        pairingErrorMessage = ex.localizedMessage ?: "Invalid or expired pairing code."
                                    }
                                }
                            },
                            enabled = !isClaimingPairing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LoginBrandGreen,
                                contentColor = Color.White,
                                disabledContainerColor = LoginBrandGreen.copy(alpha = 0.5f),
                                disabledContentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("claim_pairing_code_btn")
                        ) {
                            if (isClaimingPairing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = tr("Connect device", "डिवाइस जोड़ें"),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Secondary Outlined "Scan QR instead" Button
                        Surface(
                            onClick = { showQrScanDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("scan_qr_instead_btn")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    tint = LoginNavyText,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tr("Scan QR instead", "इसके बजाय QR स्कैन करें"),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LoginNavyText
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Footer Hint Text
                        Text(
                            text = tr(
                                "No code? Ask your admin to open Staff & Devices → Pair counter device.",
                                "कोड नहीं है? अपने एडमिन से स्टाफ और डिवाइस → काउंटर डिवाइस जोड़ें खोलने के लिए कहें।"
                            ),
                            fontSize = 12.5.sp,
                            color = LoginSubtleText,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            // Bottom Footer: Terms & Privacy Policy
            if (!isPairingMode) {
                Text(
                    text = tr(
                        "By continuing you agree to the Terms & Privacy Policy",
                        "जारी रखकर आप नियम और गोपनीयता नीति से सहमत होते हैं"
                    ),
                    fontSize = 12.5.sp,
                    color = LoginSubtleText,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // Dialog: Scan / Paste QR Payload
    if (showQrScanDialog) {
        AlertDialog(
            onDismissRequest = { showQrScanDialog = false },
            containerColor = Color.White,
            title = {
                Text(
                    text = tr("Scan or enter QR code", "QR कोड स्कैन या दर्ज करें"),
                    fontWeight = FontWeight.Bold,
                    color = LoginNavyText
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = tr(
                            "Paste the pairing QR link or 6-digit code from the admin phone:",
                            "एडमिन फ़ोन से पेयरिंग QR लिंक या 6-अंकीय कोड पेस्ट करें:"
                        ),
                        fontSize = 13.sp,
                        color = LoginSubtleText
                    )
                    OutlinedTextField(
                        value = qrInputText,
                        onValueChange = { qrInputText = it },
                        placeholder = { Text("khatawise://pair?code=482195") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val extractedDigits = Regex("\\d{6}").find(qrInputText)?.value
                            ?: qrInputText.filter { it.isDigit() }.take(6)
                        if (extractedDigits.isNotEmpty()) {
                            pairingCodeInput = extractedDigits
                        }
                        showQrScanDialog = false
                        qrInputText = ""
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LoginBrandGreen,
                        contentColor = Color.White
                    )
                ) {
                    Text(tr("Use code", "कोड उपयोग करें"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQrScanDialog = false }) {
                    Text(tr("Cancel", "रद्द करें"), color = LoginSubtleText)
                }
            }
        )
    }

    // Dialog: Confirm New Store Setup (First-time Admin Registration)
    if (showNewStoreDialog && pendingAdminUser != null) {
        val adminUser = pendingAdminUser!!
        AlertDialog(
            onDismissRequest = { /* Force confirmation */ },
            containerColor = FlatDarkCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Store, contentDescription = null, tint = CGreen, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Confirm Shop Details", color = FlatTextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Welcome to KhataWise! Confirm your store name to complete setup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FlatTextMuted
                    )

                    OutlinedTextField(
                        value = newStoreName,
                        onValueChange = { newStoreName = it },
                        label = { Text("Shop / Business Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CGreen,
                            unfocusedBorderColor = FlatDarkBorder,
                            focusedTextColor = FlatTextPrimary,
                            unfocusedTextColor = FlatTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_store_name_input")
                    )

                    OutlinedTextField(
                        value = newOwnerName,
                        onValueChange = { newOwnerName = it },
                        label = { Text("Owner / Admin Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CGreen,
                            unfocusedBorderColor = FlatDarkBorder,
                            focusedTextColor = FlatTextPrimary,
                            unfocusedTextColor = FlatTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newAdminPin,
                        onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) newAdminPin = it },
                        label = { Text("Security PIN (4-digit, optional)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CGreen,
                            unfocusedBorderColor = FlatDarkBorder,
                            focusedTextColor = FlatTextPrimary,
                            unfocusedTextColor = FlatTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val resolvedShop = newStoreName.trim().ifBlank { "KhataWise Store" }
                        val ownerName = newOwnerName.trim().ifBlank { adminUser.displayName ?: "Admin" }

                        scope.launch {
                            try {
                                val db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
                                val storeData = mapOf(
                                    "storeName" to resolvedShop,
                                    "ownerName" to ownerName,
                                    "email" to (adminUser.email ?: ""),
                                    "updatedAt" to System.currentTimeMillis()
                                )
                                withContext(Dispatchers.IO) {
                                    db.collection("users").document(adminUser.uid).set(storeData).await()
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("KhataWise", "Firestore store init: ${e.message}")
                            }

                            val user = User(
                                id = adminUser.uid,
                                name = ownerName,
                                role = Role.ADMINISTRATOR,
                                pinHash = if (newAdminPin.isNotEmpty()) SecurityUtils.hashPin(newAdminPin) else "",
                                isActive = true,
                                syncStatus = SyncStatus.SYNCED
                            )
                            repository.insertUser(user)
                            repository.setActiveUser(user.id)

                            showNewStoreDialog = false
                            onLoginSuccess()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CGreen),
                    modifier = Modifier.testTag("confirm_store_setup_btn")
                ) {
                    Text("Launch Register", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun StoreOwnerFeatureRow(
    icon: ImageVector,
    text: String,
    onClick: (() -> Unit)? = null,
    testTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (testTag != null) Modifier.testTag(testTag) else Modifier
            )
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onClick() }
                        .padding(vertical = 2.dp)
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFFE6F5EE)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = LoginBrandGreen,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color(0xFF334155),
            fontWeight = FontWeight.SemiBold
        )
    }
}

private suspend fun claimDeviceWithCode(
    code: String,
    staffUser: FirebaseUser,
    pairingRepo: PairingRepository,
    repo: CattleFeedRepository,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    try {
        val email = staffUser.email ?: ""
        val deviceInfo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
        val result = pairingRepo.verifyAndClaimPairingCode(
            code = code,
            staffUid = staffUser.uid,
            staffName = staffUser.displayName ?: "Counter Staff",
            staffEmail = email,
            deviceInfo = deviceInfo
        )

        result.onSuccess { token ->
            withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                val assignedRole = if (token.assignedRole == "MANAGER") Role.MANAGER else Role.STAFF
                val staffMember = User(
                    id = staffUser.uid,
                    name = staffUser.displayName ?: "Counter Staff",
                    email = email,
                    deviceInfo = deviceInfo,
                    role = assignedRole,
                    pinHash = "",
                    isActive = true,
                    isDeleted = false,
                    syncStatus = SyncStatus.SYNCED
                )

                repo.savePairedStore(
                    storeAdminUid = token.adminUid,
                    shopName = token.storeName,
                    shopAddress = token.storeAddress,
                    shopPhone = token.storePhone
                )

                val savedStaff = repo.insertUser(staffMember)
                repo.setActiveUser(savedStaff.id)

                com.example.PashuAaharApp.instance.firestoreSyncEngine.startRealtimeListeners(token.adminUid)
                com.example.PashuAaharApp.instance.firestoreSyncEngine.triggerSync()
            }

            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }.onFailure { ex ->
            onError(ex.localizedMessage ?: "Failed to redeem pairing code.")
        }
    } catch (e: Exception) {
        onError(e.localizedMessage ?: "Device pairing failed.")
    }
}
