package com.example.ui.staff

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.GoogleAuthManager
import com.example.data.repository.CattleFeedRepository
import com.example.data.repository.PairingRepository
import com.example.model.DevicePairingToken
import com.example.model.Role
import com.example.model.SyncStatus
import com.example.model.User
import com.example.ui.components.RemoveStaffConfirmationDialog
import com.example.ui.components.UnpairDeviceConfirmationDialog
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import com.example.util.SecurityUtils
import com.example.util.tr
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun StaffManagementScreen(
    repository: CattleFeedRepository,
    currentUser: User?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pairingRepo = remember { PairingRepository(context) }

    val allUsers by repository.allUsers.collectAsState(initial = emptyList())
    val settings by repository.settingsFlow.collectAsState(initial = null)
    val googleUser = GoogleAuthManager.currentUser.value

    val shopName = settings?.shopName?.ifBlank { "KhataWise Store" } ?: "KhataWise Store"
    val shopAddress = settings?.shopAddress ?: ""
    val shopPhone = settings?.shopPhone ?: ""

    val currentDeviceModel = remember {
        val mfr = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()
        when {
            model.startsWith(mfr, ignoreCase = true) -> model
            mfr.isNotBlank() && model.isNotBlank() -> "${mfr.replaceFirstChar { it.uppercase() }} $model"
            model.isNotBlank() -> model
            else -> "Android Phone"
        }
    }
    val currentAndroidVersion = remember {
        val rel = Build.VERSION.RELEASE?.trim().orEmpty()
        if (rel.isNotBlank()) "Android $rel" else "Android"
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 -> Staff, 1 -> Devices, 2 -> Roles

    // Real pending staff approval requests from Firestore (no hardcoded sample data)
    var pendingRequests by remember { mutableStateOf<List<StaffProfileItem>>(emptyList()) }

    // Custom registered-name tracking when admin sets a custom display name
    var registeredNamesMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // Format helpers
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }
    val shortDateFormatter = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    // Listen to real pending staff approval documents under users/{adminUid}/staff in Firestore
    LaunchedEffect(googleUser?.uid, currentUser?.id) {
        val adminUid = googleUser?.uid ?: currentUser?.id ?: return@LaunchedEffect
        try {
            val db = FirebaseFirestore.getInstance(context.getString(com.example.R.string.firestore_database_id))
            db.collection("users").document(adminUid).collection("staff")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val pendingList = snapshot.documents.mapNotNull { doc ->
                        val status = doc.getString("status") ?: ""
                        val isApproved = doc.getBoolean("isApproved") ?: true
                        if (status == "PENDING" || !isApproved) {
                            val uid = doc.getString("staffUid") ?: doc.id
                            val name = doc.getString("name") ?: "Staff"
                            val email = doc.getString("email") ?: ""
                            val dev = doc.getString("deviceInfo") ?: ""
                            val roleStr = doc.getString("role") ?: "CASHIER"
                            val pairedAt = doc.getLong("pairedAt") ?: System.currentTimeMillis()
                            StaffProfileItem(
                                id = uid,
                                displayName = name,
                                registeredName = doc.getString("registeredName") ?: name,
                                email = email,
                                deviceModel = dev,
                                androidVersion = doc.getString("androidVersion") ?: "",
                                lastSeenLabel = "asked ${timeFormatter.format(Date(pairedAt))}",
                                joinedLabel = "${dateFormatter.format(Date(pairedAt))} · via counter code",
                                role = if (roleStr.equals("MANAGER", true)) Role.MANAGER else Role.STAFF,
                                hasPin = false,
                                isPendingApproval = true,
                                isCurrentUser = false
                            )
                        } else null
                    }
                    pendingRequests = pendingList
                }
        } catch (_: Exception) {}
    }

    // Build real Team list strictly from Room allUsers (and currentUser if not yet persisted)
    val teamProfiles = remember(
        allUsers,
        currentUser,
        googleUser,
        registeredNamesMap,
        currentDeviceModel,
        currentAndroidVersion
    ) {
        val activeDbUsers = allUsers.filter { !it.isDeleted }
        val baseUsers = if (activeDbUsers.isNotEmpty()) {
            activeDbUsers
        } else if (currentUser != null) {
            listOf(currentUser)
        } else {
            emptyList()
        }

        baseUsers.map { user ->
            val isYou = user.id == currentUser?.id ||
                (googleUser != null && user.id == googleUser.uid) ||
                (currentUser == null && baseUsers.size == 1 && user.role == Role.ADMINISTRATOR)

            // Auto-fetched Gmail and Mobile Model for the active signed-in device
            val resolvedEmail = when {
                user.email.isNotBlank() -> user.email
                isYou && !googleUser?.email.isNullOrBlank() -> googleUser?.email.orEmpty()
                else -> ""
            }
            val resolvedDeviceModel = when {
                user.deviceInfo.isNotBlank() -> user.deviceInfo
                isYou -> currentDeviceModel
                else -> ""
            }
            val resolvedRegName = registeredNamesMap[user.id]
                ?: (if (isYou && !googleUser?.displayName.isNullOrBlank()) googleUser?.displayName!! else user.name)

            val lastSeen = when {
                isYou -> if (resolvedDeviceModel.isNotBlank()) "$resolvedDeviceModel · active now" else "active now"
                resolvedDeviceModel.isNotBlank() -> "$resolvedDeviceModel · ${dateFormatter.format(Date(user.lastActiveAt))}"
                else -> dateFormatter.format(Date(user.lastActiveAt))
            }

            StaffProfileItem(
                id = user.id,
                displayName = user.name,
                registeredName = resolvedRegName,
                email = resolvedEmail,
                deviceModel = resolvedDeviceModel,
                androidVersion = if (isYou) currentAndroidVersion else "",
                lastSeenLabel = lastSeen,
                joinedLabel = dateFormatter.format(Date(user.createdAt)),
                role = user.role,
                hasPin = user.pinHash.isNotBlank(),
                pinValue = "",
                allowBiometric = true,
                isActive = user.isActive,
                isPendingApproval = false,
                isCurrentUser = isYou
            )
        }
    }

    // Real Paired Counter Devices derived from non-admin staff devices
    val pairedDevices = remember(allUsers, currentUser, shortDateFormatter, dateFormatter) {
        allUsers
            .filter { !it.isDeleted && it.id != currentUser?.id && (it.deviceInfo.isNotBlank() || it.role != Role.ADMINISTRATOR) }
            .map { user ->
                val pairedDateStr = shortDateFormatter.format(Date(user.createdAt))
                val modelAndDate = if (user.deviceInfo.isNotBlank()) {
                    "${user.deviceInfo} · paired $pairedDateStr"
                } else {
                    "paired $pairedDateStr"
                }
                val isRecent = (System.currentTimeMillis() - user.lastActiveAt) < 15 * 60 * 1000L
                val statusText = if (isRecent && user.isActive) {
                    "Active now"
                } else {
                    "Last seen ${dateFormatter.format(Date(user.lastActiveAt))}"
                }
                PairedCounterDevice(
                    id = user.id,
                    title = user.name,
                    modelAndDate = modelAndDate,
                    role = user.role,
                    statusText = statusText,
                    isActiveNow = isRecent && user.isActive
                )
            }
    }

    // Selected staff member for full-screen Staff details & approval view
    var selectedStaffForDetails by remember { mutableStateOf<StaffProfileItem?>(null) }
    var staffToRemove by remember { mutableStateOf<StaffProfileItem?>(null) }
    var deviceToUnpair by remember { mutableStateOf<PairedCounterDevice?>(null) }
    var showAddStaffDialog by remember { mutableStateOf(false) }

    // Devices tab pairing code state (generated dynamically via SecureRandom / PairingRepository)
    var pairingAssignedRole by remember { mutableStateOf("CASHIER") }
    var activePairingToken by remember { mutableStateOf<DevicePairingToken?>(null) }
    var generatedLocalCode by remember {
        mutableStateOf(String.format(Locale.getDefault(), "%06d", SecureRandom().nextInt(1000000)))
    }
    var pairingRemainingSeconds by remember { mutableLongStateOf(600L) }

    fun generateFreshPairingCode(showToast: Boolean) {
        val randomPin = String.format(Locale.getDefault(), "%06d", SecureRandom().nextInt(1000000))
        generatedLocalCode = randomPin
        pairingRemainingSeconds = 600L
        scope.launch {
            try {
                val adminUid = googleUser?.uid ?: currentUser?.id ?: "admin"
                activePairingToken = pairingRepo.generatePairingCode(
                    adminUid = adminUid,
                    storeName = shopName,
                    storeAddress = shopAddress,
                    storePhone = shopPhone,
                    assignedRole = pairingAssignedRole
                )
            } catch (_: Exception) {}
            if (showToast) {
                Toast.makeText(context, "New 10-minute pairing code generated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Automatically generate a real pairing token in Firestore when opening the Devices tab
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && activePairingToken == null) {
            generateFreshPairingCode(showToast = false)
        }
    }

    // 1-second countdown ticker for Devices tab pairing code
    LaunchedEffect(activePairingToken, generatedLocalCode) {
        while (true) {
            delay(1000L)
            val token = activePairingToken
            if (token != null && token.isActive()) {
                val remMs = token.expiresAt - System.currentTimeMillis()
                pairingRemainingSeconds = (remMs / 1000L).coerceAtLeast(0L)
            } else {
                if (pairingRemainingSeconds > 0L) {
                    pairingRemainingSeconds -= 1L
                }
            }
        }
    }

    // Realtime listener when a Firestore pairing token is active
    LaunchedEffect(activePairingToken?.code) {
        val code = activePairingToken?.code
        if (!code.isNullOrBlank()) {
            pairingRepo.listenToPairingToken(code).collectLatest { updatedToken ->
                if (updatedToken != null) {
                    activePairingToken = updatedToken
                    if (updatedToken.status == "CLAIMED") {
                        Toast.makeText(
                            context,
                            "Counter Device connected by ${updatedToken.claimedByName ?: "Staff"}!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    val approvedToastMsg = tr("Staff details saved!", "स्टाफ विवरण सेव किया गया!")
    val rejectedToastMsg = tr("Staff removed", "स्टाफ हटाया गया")

    // Full-screen Staff details & approval view
    val detailTarget = selectedStaffForDetails
    if (detailTarget != null) {
        StaffDetailsApprovalScreen(
            staff = detailTarget,
            onSaveAndApprove = { customName, newRole, newPin, _, isActiveFlag ->
                scope.launch {
                    val existingDbUser = allUsers.find { it.id == detailTarget.id }
                    val userId = existingDbUser?.id ?: detailTarget.id.ifBlank { UUID.randomUUID().toString() }
                    val newPinHash = when {
                        newPin.length == 4 -> SecurityUtils.hashPin(newPin)
                        existingDbUser != null -> existingDbUser.pinHash
                        else -> ""
                    }

                    // Keep track of original registered name if custom name was set
                    if (detailTarget.registeredName.isNotBlank()) {
                        registeredNamesMap = registeredNamesMap + (userId to detailTarget.registeredName)
                    }

                    val userEntity = User(
                        id = userId,
                        name = customName,
                        role = newRole,
                        // Email and deviceInfo are auto-captured from Google Sign-In & device, never manually overwritten
                        email = existingDbUser?.email?.ifBlank { detailTarget.email } ?: detailTarget.email,
                        deviceInfo = existingDbUser?.deviceInfo?.ifBlank { detailTarget.deviceModel } ?: detailTarget.deviceModel,
                        pinHash = newPinHash,
                        isActive = isActiveFlag,
                        createdAt = existingDbUser?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        isDeleted = false,
                        syncStatus = SyncStatus.SYNCED
                    )
                    repository.insertUser(userEntity)

                    if (detailTarget.isPendingApproval) {
                        pendingRequests = pendingRequests.filterNot { it.id == detailTarget.id }
                    }

                    try {
                        val adminUid = googleUser?.uid ?: currentUser?.id ?: "admin"
                        val updates = hashMapOf<String, Any>(
                            "name" to customName,
                            "registeredName" to detailTarget.registeredName,
                            "email" to userEntity.email,
                            "deviceInfo" to userEntity.deviceInfo,
                            "role" to newRole.name,
                            "status" to "APPROVED",
                            "isApproved" to true,
                            "isActive" to isActiveFlag,
                            "updatedAt" to System.currentTimeMillis()
                        )
                        FirebaseFirestore.getInstance(context.getString(com.example.R.string.firestore_database_id))
                            .collection("users").document(adminUid).collection("staff")
                            .document(userId)
                            .set(updates)
                            .await()
                    } catch (_: Exception) {}

                    Toast.makeText(context, approvedToastMsg, Toast.LENGTH_SHORT).show()
                    selectedStaffForDetails = null
                }
            },
            onRejectOrRemove = {
                if (detailTarget.isPendingApproval) {
                    scope.launch {
                        pendingRequests = pendingRequests.filterNot { it.id == detailTarget.id }
                        try {
                            val adminUid = googleUser?.uid ?: currentUser?.id ?: "admin"
                            FirebaseFirestore.getInstance(context.getString(com.example.R.string.firestore_database_id))
                                .collection("users").document(adminUid).collection("staff")
                                .document(detailTarget.id)
                                .delete()
                                .await()
                        } catch (_: Exception) {}
                        Toast.makeText(context, rejectedToastMsg, Toast.LENGTH_SHORT).show()
                        selectedStaffForDetails = null
                    }
                } else if (!detailTarget.isCurrentUser) {
                    staffToRemove = detailTarget
                }
            },
            onBack = { selectedStaffForDetails = null }
        )
        staffToRemove?.let { targetStaff ->
            val roleLabel = when (targetStaff.role) {
                Role.ADMINISTRATOR -> "Admin"
                Role.MANAGER -> "Manager"
                Role.STAFF -> "Cashier"
            }
            RemoveStaffConfirmationDialog(
                staffDisplayTitle = "${targetStaff.displayName} · $roleLabel",
                deviceName = targetStaff.deviceModel,
                onDismiss = { staffToRemove = null },
                onConfirmRemove = {
                    staffToRemove = null
                    scope.launch {
                        if (!targetStaff.isCurrentUser) {
                            repository.deleteUser(targetStaff.id)
                        }
                        try {
                            val adminUid = googleUser?.uid ?: currentUser?.id ?: "admin"
                            FirebaseFirestore.getInstance(context.getString(com.example.R.string.firestore_database_id))
                                .collection("users").document(adminUid).collection("staff")
                                .document(targetStaff.id)
                                .delete()
                                .await()
                        } catch (_: Exception) {}
                        Toast.makeText(context, rejectedToastMsg, Toast.LENGTH_SHORT).show()
                        selectedStaffForDetails = null
                    }
                }
            )
        }
        return
    }

    deviceToUnpair?.let { targetDevice ->
        UnpairDeviceConfirmationDialog(
            counterTitle = targetDevice.title,
            deviceModel = targetDevice.modelAndDate.substringBefore(" · "),
            onDismiss = { deviceToUnpair = null },
            onConfirmUnpair = {
                val dev = targetDevice
                deviceToUnpair = null
                scope.launch {
                    repository.deleteUser(dev.id)
                    Toast.makeText(
                        context,
                        "${dev.title} unpaired",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    BackHandler { onBack() }

    val adminCount = teamProfiles.count { it.role == Role.ADMINISTRATOR }
    val managerCount = teamProfiles.count { it.role == Role.MANAGER }
    val cashierCount = teamProfiles.count { it.role == Role.STAFF }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FlatDarkBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onBack,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("staff_management_back_btn"),
                    shape = RoundedCornerShape(13.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Settings",
                            tint = FlatTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = tr("Staff & Devices", "स्टाफ और डिवाइस"),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FlatTextPrimary
                    )
                    Text(
                        text = tr("Roles, PINs & counter pairing", "भूमिकाएँ, पिन और काउंटर पेयरिंग"),
                        fontSize = 13.sp,
                        color = FlatTextSecondary
                    )
                }
            }

            // 3-Tab Row: Staff | Devices | Roles
            val tabs = listOf(
                Triple(0, tr("Staff", "स्टाफ"), "staff_tab_team"),
                Triple(1, tr("Devices", "डिवाइस"), "staff_tab_devices"),
                Triple(2, tr("Roles", "भूमिकाएँ"), "staff_tab_roles")
            )

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = FlatDarkBg,
                contentColor = StaffBrandGreen,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = StaffBrandGreen,
                        height = 2.5.dp
                    )
                },
                divider = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(FlatDarkBorder)
                    )
                }
            ) {
                tabs.forEach { (idx, title, tag) ->
                    val isSelected = selectedTab == idx
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTab = idx },
                        modifier = Modifier.testTag(tag),
                        text = {
                            Text(
                                text = title,
                                fontSize = 14.5.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (isSelected) StaffBrandGreen else FlatTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    // TAB 1: Staff
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 14.dp, bottom = 92.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Pending Approval Banner Card(s) — only shown when real pending requests exist
                        items(pendingRequests, key = { it.id }) { pending ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pending_approval_banner"),
                                shape = RoundedCornerShape(18.dp),
                                color = Color(0xFFFEF9EC),
                                border = BorderStroke(1.dp, Color(0xFFF3E2B3))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(9.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFD97706))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = tr(
                                                "1 person waiting for approval",
                                                "1 व्यक्ति मंज़ूरी की प्रतीक्षा कर रहा है"
                                            ),
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF78350F)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFE2E8F0)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = staffInitials(pending.displayName),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = StaffNavyPill
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = pending.displayName,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = FlatTextPrimary
                                            )
                                            if (pending.email.isNotBlank()) {
                                                Text(
                                                    text = pending.email,
                                                    fontSize = 13.sp,
                                                    color = FlatTextSecondary
                                                )
                                            }
                                            Text(
                                                text = listOf(pending.deviceModel, pending.lastSeenLabel)
                                                    .filter { it.isNotBlank() }
                                                    .joinToString(" · "),
                                                fontSize = 13.sp,
                                                color = FlatTextSecondary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            onClick = {
                                                pendingRequests = pendingRequests.filterNot { it.id == pending.id }
                                                Toast.makeText(context, rejectedToastMsg, Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp)
                                                .testTag("pending_reject_btn"),
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color.White,
                                            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = tr("Reject", "अस्वीकार करें"),
                                                    fontSize = 14.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = FlatTextPrimary
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = { selectedStaffForDetails = pending },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp)
                                                .testTag("pending_review_approve_btn"),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = StaffBrandGreen,
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Text(
                                                text = tr("Review & approve", "समीक्षा और मंज़ूरी दें"),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3 Role Count Summary Cards (Admin | Manager | Cashier)
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RoleCountSummaryCard(
                                    count = adminCount,
                                    label = tr("Admin", "एडमिन"),
                                    bgColor = Color(0xFFE8ECF4),
                                    textColor = StaffNavyPill,
                                    modifier = Modifier.weight(1f)
                                )
                                RoleCountSummaryCard(
                                    count = managerCount,
                                    label = tr("Manager", "मैनेजर"),
                                    bgColor = Color(0xFFDCF0FA),
                                    textColor = StaffBluePillText,
                                    modifier = Modifier.weight(1f)
                                )
                                RoleCountSummaryCard(
                                    count = cashierCount,
                                    label = tr("Cashier", "कैशियर"),
                                    bgColor = Color(0xFFF1F2F6),
                                    textColor = StaffCashierPillText,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Section Header: Team (N) | Tap a person to edit
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${tr("Team", "टीम")} (${teamProfiles.size})",
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = FlatTextPrimary
                                )
                                Text(
                                    text = tr("Tap a person to edit", "संपादित करने के लिए व्यक्ति पर टैप करें"),
                                    fontSize = 13.sp,
                                    color = FlatTextSecondary
                                )
                            }
                        }

                        // Team Member Cards
                        items(teamProfiles, key = { it.id }) { member ->
                            TeamMemberCard(
                                member = member,
                                onClick = { selectedStaffForDetails = member }
                            )
                        }
                    }
                }

                1 -> {
                    // TAB 2: Devices
                    val displayCode = activePairingToken?.code?.takeIf { it.length == 6 } ?: generatedLocalCode
                    StaffDevicesTabContent(
                        pairingCode = displayCode,
                        selectedRole = pairingAssignedRole,
                        remainingSeconds = pairingRemainingSeconds,
                        pairedDevices = pairedDevices,
                        onSelectRole = { role -> pairingAssignedRole = role },
                        onGenerateNewCode = {
                            generateFreshPairingCode(showToast = true)
                        },
                        onUnpairDevice = { device ->
                            deviceToUnpair = device
                        }
                    )
                }

                2 -> {
                    // TAB 3: Roles
                    StaffRolesTabContent(
                        onOpenCustomPermissions = {
                            val target = teamProfiles.firstOrNull { !it.isCurrentUser } ?: teamProfiles.firstOrNull()
                            if (target != null) {
                                selectedStaffForDetails = target
                            }
                        }
                    )
                }
            }
        }

        // Bottom-Right Floating "+ Add staff" Pill Button on Staff Tab
        if (selectedTab == 0) {
            Button(
                onClick = { showAddStaffDialog = true },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StaffBrandGreen,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 18.dp)
                    .height(50.dp)
                    .testTag("btn_add_staff_member")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = tr("Add staff", "स्टाफ जोड़ें"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }

    // Dialog: Add Staff Member (Gmail & Mobile Type are auto-captured from staff device login, not editable)
    if (showAddStaffDialog) {
        var newStaffName by remember { mutableStateOf("") }
        var newStaffPin by remember { mutableStateOf("") }
        var newStaffRole by remember { mutableStateOf(Role.STAFF) }
        var addStaffError by remember { mutableStateOf<String?>(null) }

        val requiredNameErr = tr("Staff name is required.", "स्टाफ का नाम आवश्यक है।")

        AlertDialog(
            onDismissRequest = { showAddStaffDialog = false },
            containerColor = FlatDarkCard,
            title = {
                Text(
                    text = tr("Add staff member", "नया स्टाफ सदस्य जोड़ें"),
                    color = FlatTextPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (addStaffError != null) {
                        Text(addStaffError!!, color = StaffCrimson, fontSize = 12.5.sp)
                    }

                    OutlinedTextField(
                        value = newStaffName,
                        onValueChange = { newStaffName = it; addStaffError = null },
                        label = { Text(tr("Display Name *", "डिस्प्ले नाम *")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newStaffPin,
                        onValueChange = {
                            if (it.length <= 4 && it.all { ch -> ch.isDigit() }) {
                                newStaffPin = it
                            }
                        },
                        label = { Text(tr("4-Digit Login PIN (Optional)", "4-अंकीय लॉगिन पिन (वैकल्पिक)")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = newStaffRole == Role.STAFF,
                            onClick = { newStaffRole = Role.STAFF },
                            label = { Text(tr("Cashier", "कैशियर")) }
                        )
                        FilterChip(
                            selected = newStaffRole == Role.MANAGER,
                            onClick = { newStaffRole = Role.MANAGER },
                            label = { Text(tr("Manager", "मैनेजर")) }
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF1F2F8),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = tr(
                                "Gmail and Mobile Type are fetched automatically when staff connect their phone via a 6-digit pairing code and cannot be changed manually.",
                                "जब स्टाफ 6-अंकीय पेयरिंग कोड के माध्यम से अपना फ़ोन जोड़ते हैं तो जीमेल और मोबाइल मॉडल अपने आप प्राप्त हो जाते हैं और उन्हें बदला नहीं जा सकता।"
                            ),
                            fontSize = 12.sp,
                            color = FlatTextSecondary,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newStaffName.trim()
                        if (name.isBlank()) {
                            addStaffError = requiredNameErr
                            return@Button
                        }
                        scope.launch {
                            val user = User(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                email = "",
                                deviceInfo = "",
                                role = newStaffRole,
                                pinHash = if (newStaffPin.isNotEmpty()) SecurityUtils.hashPin(newStaffPin) else "",
                                isActive = true,
                                isDeleted = false,
                                syncStatus = SyncStatus.PENDING
                            )
                            repository.insertUser(user)
                            showAddStaffDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StaffBrandGreen,
                        contentColor = Color.White
                    )
                ) {
                    Text(tr("Add staff", "स्टाफ जोड़ें"), fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddStaffDialog = false }) {
                    Text(tr("Cancel", "रद्द करें"), color = FlatTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun RoleCountSummaryCard(
    count: Int,
    label: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = bgColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = count.toString(),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = textColor.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun TeamMemberCard(
    member: StaffProfileItem,
    onClick: () -> Unit
) {
    val showRegisteredSubtitle = member.registeredName.isNotBlank() &&
        !member.registeredName.equals(member.displayName, ignoreCase = true)

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("team_member_card_${member.id}"),
        shape = RoundedCornerShape(18.dp),
        color = FlatDarkCard,
        border = BorderStroke(1.dp, FlatDarkBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Circular Initials Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = staffInitials(member.displayName),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = StaffNavyPill
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = member.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FlatTextPrimary
                    )
                    if (member.isCurrentUser) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tr("(You)", "(आप)"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = StaffBrandGreen
                        )
                    }
                }

                if (showRegisteredSubtitle) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${tr("Registered as", "पंजीकृत नाम")} ${member.registeredName}",
                        fontSize = 12.5.sp,
                        color = FlatTextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Role Badge + PIN Status Badge
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StaffRoleBadgePill(role = member.role)

                    if (member.hasPin) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = StaffMintSoft
                        ) {
                            Text(
                                text = tr("PIN set", "पिन सेट"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = StaffBrandGreen,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = StaffAmberPillBg
                        ) {
                            Text(
                                text = tr("No PIN · Set now", "कोई पिन नहीं · अभी सेट करें"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF92400E),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (member.email.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Auto-fetched Email Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Email,
                            contentDescription = null,
                            tint = FlatTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = member.email,
                            fontSize = 13.sp,
                            color = FlatTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (member.lastSeenLabel.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // Auto-fetched Device & Last Seen Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Smartphone,
                            contentDescription = null,
                            tint = FlatTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = member.lastSeenLabel,
                            fontSize = 13.sp,
                            color = FlatTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Edit staff",
                tint = FlatTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun staffInitials(name: String): String {
    val clean = name.substringBefore("·").trim()
    val parts = clean.split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase(Locale.getDefault())
        parts.size == 1 && parts[0].length >= 2 -> parts[0].take(2).uppercase(Locale.getDefault())
        parts.size == 1 -> parts[0].uppercase(Locale.getDefault())
        else -> "ST"
    }
}
