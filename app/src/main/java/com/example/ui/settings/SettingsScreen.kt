package com.example.ui.settings

import com.example.ui.theme.CGreenPrimary
import com.example.ui.theme.kw
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.CattleFeedRepository
import com.example.model.Permission
import com.example.model.Role
import com.example.model.User
import com.example.model.can
import com.example.sync.FirestoreSyncState
import com.example.ui.components.ResetAllShopDataConfirmationDialog
import com.example.ui.search.GlobalSearchScreen
import com.example.ui.theme.CAmber
import com.example.ui.theme.CGreen
import com.example.ui.theme.CRedUdhari
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatDarkInput
import com.example.ui.theme.FlatTextMuted
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import com.example.util.ShopAnalytics
import com.example.util.tr
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class HubBadgeStyle {
    URGENT_RED,
    WARNING_AMBER
}

private data class SettingsHubTile(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconTint: Color,
    val iconBgColor: Color,
    val badge: String? = null,
    val badgeStyle: HubBadgeStyle = HubBadgeStyle.URGENT_RED,
    val onClick: () -> Unit
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    repository: CattleFeedRepository,
    currentUser: User?,
    onSwitchUser: () -> Unit,
    onBack: (() -> Unit)? = null,
    onNavigateToCustomers: () -> Unit = {},
    onNavigateToCashbook: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToSupplierLedger: () -> Unit = {},
    onNavigateToBills: () -> Unit = {},
    onNavigateToInventory: () -> Unit = {},
    onNavigateToCollections: () -> Unit = {},
    onNavigateToStaffManagement: () -> Unit = {},
    onSelectCustomer: (String) -> Unit = { onNavigateToCustomers() },
    onOpenSetupWizard: (Int) -> Unit = {},
    onOpenWelcomeTour: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extraPrefs = remember(context) { SettingsExtraPrefsStore(context) }

    val settings by repository.settingsFlow.collectAsState(initial = null)
    val customers by repository.allCustomers.collectAsState(initial = emptyList())
    val products by repository.allProducts.collectAsState(initial = emptyList())
    val sales by repository.allSales.collectAsState(initial = emptyList())

    val syncState by com.example.PashuAaharApp.instance.firestoreSyncEngine.syncState.collectAsState()
    val isSyncing = syncState is FirestoreSyncState.Syncing

    val isLightTheme = MaterialTheme.colorScheme.background.red > 0.5f
    val isHindi = settings?.language == "hi"

    val infiniteTransition = rememberInfiniteTransition(label = "sync_spin")
    val spinningRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        ),
        label = "sync_angle"
    )
    val syncRotation = if (isSyncing) spinningRotation else 0f

    // Sub-screen navigation state (Pages 1 to 10)
    var currentSubScreen by remember { mutableStateOf(SettingsSubScreen.MAIN) }
    var selectedTemplateForEdit by remember { mutableStateOf(DEFAULT_MESSAGE_TEMPLATES.first()) }

    // Search & Modal Dialog States
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSignOutConfirmDialog by remember { mutableStateOf(false) }
    var showResetShopConfirmDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showHelpSupportDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var adminNameInput by remember(currentUser) { mutableStateOf(currentUser?.name ?: "") }

    val watchThresholdInput = remember(settings) {
        settings?.watchThreshold?.toPlainString() ?: "2000"
    }
    val criticalThresholdInput = remember(settings) {
        settings?.criticalThreshold?.toPlainString() ?: "5000"
    }

    val endOfToday = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }
    val dueCollectionsCount = remember(customers, endOfToday) {
        customers.count {
            !it.isDeleted &&
                it.totalUdhari.compareTo(BigDecimal.ZERO) > 0 &&
                (it.promiseDate == null || it.promiseDate!! <= endOfToday)
        }
    }

    val lowStockCount = remember(products, sales) {
        val reorderAlerts = try {
            ShopAnalytics.computeReorderSuggestions(products, sales)
        } catch (_: Throwable) {
            emptyList()
        }
        val lowFromAnalytics = reorderAlerts.map { it.product.id }.toSet()
        val lowFromStock = products.filter { p ->
            !p.isDeleted && p.currentStockKg <= p.kgPerBag.multiply(BigDecimal("3"))
        }.map { it.id }.toSet()
        (lowFromAnalytics + lowFromStock).size
    }

    val pendingSyncCount = remember(customers, products, sales) {
        val unsyncedCust = customers.count { it.syncStatus != com.example.model.SyncStatus.SYNCED }
        val unsyncedProd = products.count { it.syncStatus != com.example.model.SyncStatus.SYNCED }
        val unsyncedSale = sales.count { it.syncStatus != com.example.model.SyncStatus.SYNCED }
        unsyncedCust + unsyncedProd + unsyncedSale
    }

    val unreadNotificationsCount = remember(customers, products, sales, settings, syncState, currentSubScreen) {
        val items = buildLiveShopNotifications(
            customers = customers,
            products = products,
            sales = sales,
            settings = settings,
            syncState = syncState,
            extraPrefs = extraPrefs,
            onSendCustomerReminder = {},
            onNavigateToCollections = {},
            onNavigateToInventory = {},
            onDismissNotification = {},
            onBackupNow = {}
        )
        val readIds = extraPrefs.readNotificationIds
        items.count { it.id !in readIds && it.unreadDotColor != Color.Transparent }
    }

    val performSyncNow: () -> Unit = {
        if (!isSyncing) {
            scope.launch {
                val res = com.example.PashuAaharApp.instance.firestoreSyncEngine.syncNow()
                when (res) {
                    is FirestoreSyncState.Success -> {
                        val msg = if (isHindi) "क्लाउड डेटा सिंक हो गया! (${res.count} रिकॉर्ड)" else "Cloud data synced! (${res.count} records)"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    is FirestoreSyncState.Error -> {
                        val msg = if (isHindi) "सिंक समस्या: ${res.message}" else "Sync issue: ${res.message}"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    is FirestoreSyncState.Unauthenticated -> {
                        val msg = if (isHindi) "सिंक करने के लिए Google से साइन इन करें" else "Please sign in with Google to sync"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }
    }

    // Sub-screen Routing (Pages 2, 3, 4, 5, 6, 8, 9, 10 + Bill & PDF + Global Search)
    when (currentSubScreen) {
        SettingsSubScreen.GLOBAL_SEARCH -> {
            GlobalSearchScreen(
                repository = repository,
                onCancel = { currentSubScreen = SettingsSubScreen.MAIN },
                onSelectCustomer = { custId ->
                    currentSubScreen = SettingsSubScreen.MAIN
                    onSelectCustomer(custId)
                },
                onNavigateToBills = {
                    currentSubScreen = SettingsSubScreen.MAIN
                    onNavigateToBills()
                },
                onNavigateToInventory = {
                    currentSubScreen = SettingsSubScreen.MAIN
                    onNavigateToInventory()
                },
                onNavigateToSuppliers = {
                    currentSubScreen = SettingsSubScreen.MAIN
                    onNavigateToSupplierLedger()
                },
                onNavigateToCollections = {
                    currentSubScreen = SettingsSubScreen.MAIN
                    onNavigateToCollections()
                },
                onNavigateToStaff = {
                    currentSubScreen = SettingsSubScreen.MAIN
                    onNavigateToStaffManagement()
                }
            )
            return
        }

        SettingsSubScreen.STORE_PROFILE -> {
            StoreProfileSubScreen(
                settings = settings,
                currentUser = currentUser,
                extraPrefs = extraPrefs,
                onBack = { currentSubScreen = SettingsSubScreen.MAIN },
                onSaveStoreProfile = { shopName, shopType, ownerName, primaryPhone, extraPhones, village, fullAddress, openHour, closeHour, logoUri ->
                    extraPrefs.shopType = shopType
                    extraPrefs.extraPhonesCsv = extraPhones.joinToString(",")
                    extraPrefs.villageCity = village
                    extraPrefs.shopHoursOpen = openHour
                    extraPrefs.shopHoursClose = closeHour
                    extraPrefs.customLogoUri = logoUri

                    val watch = try { BigDecimal(watchThresholdInput.trim()) } catch (_: Exception) { BigDecimal("2000") }
                    val critical = try { BigDecimal(criticalThresholdInput.trim()) } catch (_: Exception) { BigDecimal("5000") }
                    val addressToSave = fullAddress.ifBlank { village }
                    scope.launch {
                        repository.updateStoreProfile(
                            shopName = shopName,
                            shopAddress = addressToSave,
                            shopPhone = primaryPhone,
                            shopGstin = settings?.shopGstin.orEmpty(),
                            shopUpiId = settings?.shopUpiId.orEmpty(),
                            watchThreshold = watch,
                            criticalThreshold = critical,
                            performedByRole = currentUser?.role
                        )
                        if (ownerName.isNotBlank() && currentUser != null && ownerName != currentUser.name) {
                            repository.updateAdminProfile(currentUser.id, ownerName)
                        }
                        val msg = if (isHindi) "दुकान प्रोफ़ाइल सेव हो गई" else "Store profile saved"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        currentSubScreen = SettingsSubScreen.MAIN
                    }
                }
            )
            return
        }

        SettingsSubScreen.UPI_GST -> {
            UpiAndGstSubScreen(
                settings = settings,
                extraPrefs = extraPrefs,
                onBack = { currentSubScreen = SettingsSubScreen.MAIN },
                onSaveUpiAndGst = { upiId, showPayNowQr, extraUpis, hasGstin, gstin, legalName, stateName, defaultHsn, registrationType, pricesIncludeGst, showGstSplit ->
                    extraPrefs.showPayNowQrOnBills = showPayNowQr
                    extraPrefs.extraUpiIdsCsv = extraUpis.joinToString(",")
                    extraPrefs.hasGstin = hasGstin
                    extraPrefs.legalName = legalName
                    extraPrefs.gstState = stateName
                    extraPrefs.defaultHsn = defaultHsn
                    extraPrefs.gstRegistrationType = registrationType
                    extraPrefs.pricesIncludeGst = pricesIncludeGst
                    extraPrefs.showGstSplitOnBills = showGstSplit

                    val watch = try { BigDecimal(watchThresholdInput.trim()) } catch (_: Exception) { BigDecimal("2000") }
                    val critical = try { BigDecimal(criticalThresholdInput.trim()) } catch (_: Exception) { BigDecimal("5000") }
                    scope.launch {
                        repository.updateStoreProfile(
                            shopName = settings?.shopName?.ifBlank { "KhataWise Store" } ?: "KhataWise Store",
                            shopAddress = settings?.shopAddress.orEmpty(),
                            shopPhone = settings?.shopPhone.orEmpty(),
                            shopGstin = gstin,
                            shopUpiId = upiId,
                            watchThreshold = watch,
                            criticalThreshold = critical,
                            performedByRole = currentUser?.role
                        )
                        val msg = if (isHindi) "UPI और GST विवरण सेव हो गया" else "UPI & GST details saved"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        currentSubScreen = SettingsSubScreen.MAIN
                    }
                }
            )
            return
        }

        SettingsSubScreen.BILL_PDF -> {
            BillAndPdfSubScreen(
                extraPrefs = extraPrefs,
                onBack = { currentSubScreen = SettingsSubScreen.MAIN },
                onSaveBillPdf = {
                    val msg = if (isHindi) "बिल और PDF सेटिंग्स सेव हो गईं" else "Bill & PDF settings saved"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    currentSubScreen = SettingsSubScreen.MAIN
                }
            )
            return
        }

        SettingsSubScreen.SYNC_BACKUP -> {
            SyncAndBackupSubScreen(
                settings = settings,
                currentUser = currentUser,
                syncState = syncState,
                pendingWaitingCount = pendingSyncCount,
                extraPrefs = extraPrefs,
                onBack = { currentSubScreen = SettingsSubScreen.MAIN },
                onSyncNow = performSyncNow,
                onTriggerBackupNow = {
                    performSyncNow()
                    val msg = if (isHindi) "Google Drive बैकअप पूरा हुआ" else "Backed up to Google Drive"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                },
                onRestoreSnapshot = { snap ->
                    performSyncNow()
                    val msg = if (isHindi) "${snap.title} से डेटा रीस्टोर किया गया" else "Restored snapshot from ${snap.title}"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            )
            return
        }

        SettingsSubScreen.THEME_LANGUAGE -> {
            ThemeAndLanguageSubScreen(
                settings = settings,
                extraPrefs = extraPrefs,
                onBack = { currentSubScreen = SettingsSubScreen.MAIN },
                onApplyThemeAndLanguage = { themeMode, languageCode, textSizeMode, indianFormat ->
                    extraPrefs.textSizeMode = textSizeMode
                    extraPrefs.indianNumberFormat = indianFormat
                    scope.launch {
                        repository.updateThemeMode(themeMode)
                        repository.updateLanguage(languageCode)
                        val msg = if (languageCode == "hi") "थीम और भाषा लागू हो गई" else "Theme & Language applied"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        currentSubScreen = SettingsSubScreen.MAIN
                    }
                }
            )
            return
        }

        SettingsSubScreen.APP_LOCK_SECURITY -> {
            AppLockAndSecuritySubScreen(
                extraPrefs = extraPrefs,
                onBack = { currentSubScreen = SettingsSubScreen.MAIN },
                onSaveSecurity = {
                    val msg = if (isHindi) "सुरक्षा सेटिंग्स सेव हो गईं" else "Security settings saved"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    currentSubScreen = SettingsSubScreen.MAIN
                },
                onChangePin = { newPin: String ->
                    val uid = currentUser?.id
                    if (!uid.isNullOrBlank()) {
                        scope.launch {
                            try {
                                repository.updateUserPin(uid, newPin)
                            } catch (_: Throwable) {}
                        }
                    }
                }
            )
            return
        }

        SettingsSubScreen.NOTIFICATIONS -> {
            NotificationsSubScreen(
                customers = customers,
                products = products,
                sales = sales,
                settings = settings,
                syncState = syncState,
                extraPrefs = extraPrefs,
                onBack = { currentSubScreen = SettingsSubScreen.MAIN },
                onOpenRemindersSettings = { currentSubScreen = SettingsSubScreen.REMINDERS },
                onNavigateToCollections = onNavigateToCollections,
                onNavigateToInventory = onNavigateToInventory,
                onBackupNow = performSyncNow
            )
            return
        }

        SettingsSubScreen.REMINDERS -> {
            RemindersSubScreen(
                extraPrefs = extraPrefs,
                currentLanguageCode = if (isHindi) "hi" else "en",
                onBack = { currentSubScreen = SettingsSubScreen.NOTIFICATIONS },
                onEditTemplate = { tpl ->
                    selectedTemplateForEdit = tpl
                    currentSubScreen = SettingsSubScreen.EDIT_TEMPLATE
                }
            )
            return
        }

        SettingsSubScreen.EDIT_TEMPLATE -> {
            EditMessageTemplateSubScreen(
                template = selectedTemplateForEdit,
                settings = settings,
                extraPrefs = extraPrefs,
                onBack = { currentSubScreen = SettingsSubScreen.REMINDERS },
                onTemplateSaved = {
                    val msg = if (isHindi) "टेम्पलेट सेव हो गया" else "Template saved"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    currentSubScreen = SettingsSubScreen.REMINDERS
                }
            )
            return
        }

        SettingsSubScreen.MAIN -> Unit
    }

    val role = currentUser?.role
    val canManageShopProfile = can(role, Permission.MANAGE_SHOP_PROFILE)

    val greenIconTint = if (isLightTheme) kw(0xFF15803D) else CGreen
    val greenIconBg = if (isLightTheme) kw(0xFFDCFCE7) else Color(0xFF133826)
    val redIconTint = if (isLightTheme) kw(0xFFB91C1C) else CRedUdhari
    val redIconBg = if (isLightTheme) kw(0xFFFEE2E2) else Color(0xFF331416)
    val amberIconTint = if (isLightTheme) kw(0xFFB45309) else CAmber
    val amberIconBg = if (isLightTheme) kw(0xFFFEF3C7) else Color(0xFF362308)
    val blueIconTint = if (isLightTheme) kw(0xFF1D4ED8) else Color(0xFF38BDF8)
    val blueIconBg = if (isLightTheme) kw(0xFFDBEAFE) else Color(0xFF0C2B4E)
    val purpleIconTint = if (isLightTheme) kw(0xFF6D28D9) else Color(0xFFA78BFA)
    val purpleIconBg = if (isLightTheme) kw(0xFFEDE9FE) else Color(0xFF281B46)

    // 8-Tile SHOP HUB Grid — all tiles share one unified green icon tile style
    val hubTiles = buildList {
        if (can(role, Permission.VIEW_CUSTOMERS)) {
            add(
                SettingsHubTile(
                    id = "customers",
                    title = tr("Customers", "ग्राहक सूची"),
                    subtitle = "${customers.count { !it.isDeleted }} ${tr("customers", "ग्राहक")}",
                    icon = Icons.Default.People,
                    iconTint = greenIconTint,
                    iconBgColor = greenIconBg,
                    onClick = onNavigateToCustomers
                )
            )
        }
        if (can(role, Permission.ACCESS_COLLECTIONS)) {
            add(
                SettingsHubTile(
                    id = "collections",
                    title = tr("Collections", "उधारी वसूली"),
                    subtitle = tr("Promises & reminders", "वायदा और रिमाइंडर"),
                    icon = Icons.Default.ContactPhone,
                    iconTint = greenIconTint,
                    iconBgColor = greenIconBg,
                    badge = if (dueCollectionsCount > 0) "$dueCollectionsCount due" else null,
                    badgeStyle = HubBadgeStyle.URGENT_RED,
                    onClick = onNavigateToCollections
                )
            )
        }
        if (can(role, Permission.ACCESS_CASHBOOK)) {
            add(
                SettingsHubTile(
                    id = "cashbook",
                    title = tr("Cashbook", "रोकड़ बही"),
                    subtitle = tr("Daily cash", "दैनिक रोकड़"),
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    iconTint = greenIconTint,
                    iconBgColor = greenIconBg,
                    onClick = onNavigateToCashbook
                )
            )
        }
        if (can(role, Permission.ACCESS_REPORTS)) {
            add(
                SettingsHubTile(
                    id = "reports",
                    title = tr("Reports", "रिपोर्ट"),
                    subtitle = tr("Profit & GST", "मुनाफा और GST"),
                    icon = Icons.Default.Assessment,
                    iconTint = greenIconTint,
                    iconBgColor = greenIconBg,
                    onClick = onNavigateToReports
                )
            )
        }
        if (can(role, Permission.ACCESS_INVENTORY)) {
            add(
                SettingsHubTile(
                    id = "inventory",
                    title = tr("Inventory", "स्टॉक"),
                    subtitle = tr("Stock alerts", "स्टॉक अलर्ट"),
                    icon = Icons.Default.Inventory,
                    iconTint = greenIconTint,
                    iconBgColor = greenIconBg,
                    badge = if (lowStockCount > 0) "$lowStockCount low" else null,
                    badgeStyle = HubBadgeStyle.WARNING_AMBER,
                    onClick = onNavigateToInventory
                )
            )
        }
        if (can(role, Permission.ACCESS_SUPPLIERS)) {
            add(
                SettingsHubTile(
                    id = "suppliers",
                    title = tr("Suppliers", "सप्लायर"),
                    subtitle = tr("Bills & khata", "बिल और खाता"),
                    icon = Icons.Default.LocalShipping,
                    iconTint = greenIconTint,
                    iconBgColor = greenIconBg,
                    onClick = onNavigateToSupplierLedger
                )
            )
        }
        if (can(role, Permission.EDIT_DELETE_BILL)) {
            add(
                SettingsHubTile(
                    id = "bills",
                    title = tr("Bills", "बिक्री बिल"),
                    subtitle = tr("Edit & return", "संशोधन व वापसी"),
                    icon = Icons.Default.Receipt,
                    iconTint = greenIconTint,
                    iconBgColor = greenIconBg,
                    onClick = onNavigateToBills
                )
            )
        }
        if (can(role, Permission.MANAGE_STAFF)) {
            add(
                SettingsHubTile(
                    id = "staff",
                    title = tr("Staff", "स्टाफ"),
                    subtitle = tr("Roles & devices", "रोल और डिवाइस"),
                    icon = Icons.Default.Security,
                    iconTint = greenIconTint,
                    iconBgColor = greenIconBg,
                    onClick = onNavigateToStaffManagement
                )
            )
        }
    }.filter { tile ->
        searchQuery.isBlank() ||
            tile.title.contains(searchQuery, ignoreCase = true) ||
            tile.subtitle.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlatDarkBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // -------------------------------------------------------------
        // Top Header: KhataWise Logo + Title on Left | Search + Bell with Badge on Right
        // -------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = FlatTextPrimary
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(kw(0xFF16A34A)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(com.example.R.drawable.khatawise_logo),
                        contentDescription = "KhataWise Logo",
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(7.dp))
                    )
                }
                Text(
                    text = "KhataWise",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 19.sp,
                        letterSpacing = (-0.25).sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Search Button (opens full-screen Global Search)
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable {
                            currentSubScreen = SettingsSubScreen.GLOBAL_SEARCH
                        }
                        .testTag("settings_search_btn"),
                    shape = CircleShape,
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = tr("Search settings and hub", "सेटिंग्स और हब खोजें"),
                            tint = FlatTextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                // Notification Bell with Red Unread Badge
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { currentSubScreen = SettingsSubScreen.NOTIFICATIONS }
                        .testTag("settings_notifications_bell_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.NotificationsNone,
                                contentDescription = tr("Notifications", "सूचनाएं"),
                                tint = FlatTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (unreadNotificationsCount > 0) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 2.dp, y = (-2).dp),
                            shape = CircleShape,
                            color = kw(0xFFDC2626)
                        ) {
                            Text(
                                text = unreadNotificationsCount.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showSearchBar) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(tr("Search settings or hub…", "सेटिंग्स या हब खोजें…")) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_search_input")
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // -------------------------------------------------------------
        // 1. HERO CARD: Store Name (largest), "Nitin Nagar · Admin" below it,
        //    and City & Phone sharing one quiet line (no UPI chip).
        // -------------------------------------------------------------
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = FlatDarkCard),
            border = BorderStroke(1.dp, FlatDarkBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayInitial = (
                    settings?.shopName?.trim()?.takeIf { it.isNotEmpty() }?.take(1)
                        ?: currentUser?.name?.trim()?.takeIf { it.isNotEmpty() }?.take(1)
                        ?: "K"
                    ).uppercase()

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(greenIconBg)
                        .clickable(enabled = canManageShopProfile) {
                            adminNameInput = currentUser?.name ?: ""
                            showEditProfileDialog = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayInitial,
                        color = greenIconTint,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Store name is the largest text
                    Text(
                        text = settings?.shopName?.ifBlank { "Shop Name" } ?: "Shop Name",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 22.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    val ownerDisplayName = currentUser?.name?.trim()?.takeIf { it.isNotEmpty() } ?: "Nitin Nagar"
                    val roleBadgeText = when (currentUser?.role) {
                        Role.ADMINISTRATOR -> tr("Admin", "एडमिन")
                        Role.MANAGER -> tr("Manager", "मैनेजर")
                        else -> tr("Staff", "स्टाफ")
                    }
                    Text(
                        text = "$ownerDisplayName · $roleBadgeText",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = FlatTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = canManageShopProfile) {
                            adminNameInput = currentUser?.name ?: ""
                            showEditProfileDialog = true
                        }
                    )

                    val cityText = extraPrefs.villageCity.trim().ifBlank {
                        settings?.shopAddress?.trim().orEmpty()
                    }
                    val phoneText = settings?.shopPhone?.trim().orEmpty()
                    val cityAndPhoneLine = listOf(cityText, phoneText)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")

                    if (cityAndPhoneLine.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = cityAndPhoneLine,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.5.sp,
                                fontFeatureSettings = "tnum"
                            ),
                            color = FlatTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (canManageShopProfile) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tr("+ Add city & phone", "+ शहर और फ़ोन जोड़ें"),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                            color = FlatTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { currentSubScreen = SettingsSubScreen.STORE_PROFILE }
                        )
                    }
                }

                if (canManageShopProfile) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { currentSubScreen = SettingsSubScreen.STORE_PROFILE }
                            .testTag("btn_edit_shop_profile"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isLightTheme) kw(0xFFF5F4EE) else FlatDarkInput,
                        border = BorderStroke(1.dp, FlatDarkBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = tr("Edit store profile", "दुकान प्रोफ़ाइल संपादित करें"),
                                tint = FlatTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // -------------------------------------------------------------
        // 2. SHOP HUB (2×4 Grid of 8 Horizontal Cards)
        // -------------------------------------------------------------
        SectionHeaderLabel(text = tr("SHOP HUB", "दुकान हब"))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            hubTiles.chunked(2).forEach { rowTiles ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowTiles.forEach { tile ->
                        val extraTestTag = if (tile.id == "staff") "hub_tile_staff_devices" else "hub_tile_${tile.id}"
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 70.dp)
                                .clickable { tile.onClick() }
                                .testTag(extraTestTag),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = FlatDarkCard),
                            border = BorderStroke(1.dp, FlatDarkBorder)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(tile.iconBgColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = tile.icon,
                                            contentDescription = tile.title,
                                            tint = tile.iconTint,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tile.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = FlatTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text(
                                            text = tile.subtitle,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.5.sp,
                                                fontFeatureSettings = "tnum"
                                            ),
                                            color = FlatTextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (tile.badge != null) {
                                    val badgeBg = when (tile.badgeStyle) {
                                        HubBadgeStyle.URGENT_RED -> kw(0xFF881313)
                                        HubBadgeStyle.WARNING_AMBER -> if (isLightTheme) kw(0xFFFEF3C7) else Color(0xFF3B2506)
                                    }
                                    val badgeFg = when (tile.badgeStyle) {
                                        HubBadgeStyle.URGENT_RED -> Color.White
                                        HubBadgeStyle.WARNING_AMBER -> if (isLightTheme) kw(0xFF92400E) else CAmber
                                    }
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 6.dp, end = 6.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = badgeBg
                                    ) {
                                        Text(
                                            text = tile.badge,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp,
                                                fontFeatureSettings = "tnum"
                                            ),
                                            fontWeight = FontWeight.Bold,
                                            color = badgeFg,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (rowTiles.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 3. STORE SECTION (Store profile, UPI & GST, Bill & PDF — no subtitles, green icon tiles)
        // -------------------------------------------------------------
        if (canManageShopProfile) {
            Spacer(modifier = Modifier.height(18.dp))
            SectionHeaderLabel(text = tr("STORE", "दुकान"))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = FlatDarkCard),
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Row 1: Store profile
                    SettingsMenuRow(
                        icon = Icons.Default.Store,
                        iconTint = greenIconTint,
                        iconBg = greenIconBg,
                        title = tr("Store profile", "दुकान प्रोफ़ाइल"),
                        subtitle = null,
                        testTag = "row_store_profile",
                        onClick = { currentSubScreen = SettingsSubScreen.STORE_PROFILE }
                    )

                    HorizontalDivider(color = FlatDarkBorder, modifier = Modifier.padding(horizontal = 14.dp))

                    // Row 2: UPI & GST
                    SettingsMenuRow(
                        icon = Icons.Default.QrCode2,
                        iconTint = greenIconTint,
                        iconBg = greenIconBg,
                        title = tr("UPI & GST", "UPI और GST"),
                        subtitle = null,
                        testTag = "row_upi_gst_details",
                        onClick = { currentSubScreen = SettingsSubScreen.UPI_GST }
                    )

                    HorizontalDivider(color = FlatDarkBorder, modifier = Modifier.padding(horizontal = 14.dp))

                    // Row 3: Bill & PDF
                    SettingsMenuRow(
                        icon = Icons.Default.Description,
                        iconTint = greenIconTint,
                        iconBg = greenIconBg,
                        title = tr("Bill & PDF", "बिल और PDF"),
                        subtitle = null,
                        testTag = "row_bill_pdf",
                        onClick = { currentSubScreen = SettingsSubScreen.BILL_PDF }
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 4. DATA SECTION (Sync / Backup)
        // -------------------------------------------------------------
        Spacer(modifier = Modifier.height(18.dp))
        SectionHeaderLabel(text = tr("DATA", "डेटा और बैकअप"))

        val lastSyncTime = settings?.lastSyncTime ?: 0L
        val lastSyncSubtitle = when (val state = syncState) {
            is FirestoreSyncState.Syncing -> tr("Syncing live cloud records…", "क्लाउड डेटा सिंक हो रहा है…")
            is FirestoreSyncState.Error -> state.message
            else -> if (lastSyncTime > 0L) {
                "${tr("Synced", "सिंक हुआ")} ${
                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(lastSyncTime))
                }"
            } else {
                tr("Synced just now", "अभी सिंक हुआ")
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .clickable { currentSubScreen = SettingsSubScreen.SYNC_BACKUP }
                .testTag("sync_data_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = FlatDarkCard),
            border = BorderStroke(1.dp, FlatDarkBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 13.dp)
                    .testTag("btn_sync_data"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(greenIconBg)
                            .clickable(enabled = !isSyncing) { performSyncNow() }
                            .testTag("hub_tile_sync_backup"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync / Backup",
                            tint = greenIconTint,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(syncRotation)
                        )
                    }
                    Column {
                        Text(
                            text = tr("Sync / Backup", "सिंक / बैकअप"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                        Text(
                            text = lastSyncSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (syncState is FirestoreSyncState.Error) CRedUdhari else FlatTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = greenIconBg
                    ) {
                        Text(
                            text = if (isSyncing) tr("Syncing", "सिंक हो रहा") else tr("Synced", "सिंक है"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = greenIconTint,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = FlatTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 5. PREFERENCES SECTION (No subtitles, unified green icon tiles)
        //    1. Theme & Language
        //    2. Notifications
        //    3. App lock & security
        // -------------------------------------------------------------
        Spacer(modifier = Modifier.height(18.dp))
        SectionHeaderLabel(text = tr("PREFERENCES", "प्राथमिकताएं"))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("hub_tile_theme_language"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = FlatDarkCard),
            border = BorderStroke(1.dp, FlatDarkBorder)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Row 1: Theme & Language
                SettingsMenuRow(
                    icon = Icons.Default.Palette,
                    iconTint = greenIconTint,
                    iconBg = greenIconBg,
                    title = tr("Theme & Language", "थीम और भाषा"),
                    subtitle = null,
                    testTag = "row_theme_preference",
                    onClick = { currentSubScreen = SettingsSubScreen.THEME_LANGUAGE }
                )

                HorizontalDivider(color = FlatDarkBorder, modifier = Modifier.padding(horizontal = 14.dp))

                // Row 2: Notifications
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 58.dp)
                        .clickable { currentSubScreen = SettingsSubScreen.NOTIFICATIONS }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("row_notifications_preference"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(greenIconBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsNone,
                                contentDescription = null,
                                tint = greenIconTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = tr("Notifications", "सूचनाएं"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (unreadNotificationsCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = redIconBg
                            ) {
                                Text(
                                    text = "$unreadNotificationsCount ${tr("new", "नए")}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = redIconTint,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = FlatTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(color = FlatDarkBorder, modifier = Modifier.padding(horizontal = 14.dp))

                // Row 3: App lock & security
                SettingsMenuRow(
                    icon = Icons.Default.Lock,
                    iconTint = greenIconTint,
                    iconBg = greenIconBg,
                    title = tr("App lock & security", "ऐप लॉक और सुरक्षा"),
                    subtitle = null,
                    testTag = "row_app_lock_security",
                    onClick = { currentSubScreen = SettingsSubScreen.APP_LOCK_SECURITY }
                )
            }
        }

        // -------------------------------------------------------------
        // 6. HELP SECTION (Help & support, About KhataWise — no subtitles, green icon tiles)
        // -------------------------------------------------------------
        Spacer(modifier = Modifier.height(18.dp))
        SectionHeaderLabel(text = tr("HELP", "सहायता"))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = FlatDarkCard),
            border = BorderStroke(1.dp, FlatDarkBorder)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsMenuRow(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    iconTint = greenIconTint,
                    iconBg = greenIconBg,
                    title = tr("Help & support", "सहायता और सपोर्ट"),
                    subtitle = null,
                    testTag = "row_help_support",
                    onClick = { showHelpSupportDialog = true }
                )

                HorizontalDivider(color = FlatDarkBorder, modifier = Modifier.padding(horizontal = 14.dp))

                SettingsMenuRow(
                    icon = Icons.Default.Info,
                    iconTint = greenIconTint,
                    iconBg = greenIconBg,
                    title = tr("About KhataWise", "KhataWise के बारे में"),
                    subtitle = null,
                    testTag = "row_about_khatawise",
                    onClick = { showAboutDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // -------------------------------------------------------------
        // 7. BOTTOM LOG OUT CARD BUTTON (Opens Page 7 Logout Modal)
        // -------------------------------------------------------------
        val logoutBorder = if (isLightTheme) kw(0xFFFECACA) else Color(0xFFEF4444).copy(alpha = 0.45f)
        val logoutBg = if (isLightTheme) kw(0xFFFFFFFF) else Color(0xFF2A1214)
        val logoutTextColor = if (isLightTheme) kw(0xFFDC2626) else Color(0xFFEF4444)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { showSignOutConfirmDialog = true }
                .testTag("btn_sign_out_device"),
            shape = RoundedCornerShape(16.dp),
            color = logoutBg,
            border = BorderStroke(1.dp, logoutBorder)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = null,
                    tint = logoutTextColor,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = tr("Log out", "लॉग आउट"),
                    color = logoutTextColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }

        if (canManageShopProfile) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showResetShopConfirmDialog = true }
                    .testTag("btn_reset_shop_data"),
                shape = RoundedCornerShape(16.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, logoutBorder)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = tr("Reset all shop data", "दुकान का सारा डेटा रीसेट करें"),
                        color = logoutTextColor,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))
    }

    // -------------------------------------------------------------
    // Edit Admin Name Dialog
    // -------------------------------------------------------------
    if (showEditProfileDialog && canManageShopProfile) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            containerColor = FlatDarkCard,
            title = {
                Text(
                    tr("Edit Admin name", "एडमिन का नाम बदलें"),
                    color = FlatTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = tr(
                            "Update your displayed name on bills and store hub.",
                            "बिल और दुकान हब पर दिखने वाला अपना नाम बदलें।"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = FlatTextSecondary
                    )
                    OutlinedTextField(
                        value = adminNameInput,
                        onValueChange = { adminNameInput = it },
                        label = { Text(tr("Admin full name", "पूरा नाम")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = adminNameInput.trim()
                        if (name.isNotBlank() && currentUser != null) {
                            scope.launch {
                                repository.updateAdminProfile(currentUser.id, name)
                                val msg = if (isHindi) "प्रोफ़ाइल अपडेट हो गई" else "Admin profile updated"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                showEditProfileDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CGreenPrimary)
                ) {
                    Text(tr("Save", "सेव करें"), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text(tr("Cancel", "रद्द करें"))
                }
            }
        )
    }

    // -------------------------------------------------------------
    // Help & Support Dialog (Includes Setup Wizard & Welcome Tour shortcuts)
    // -------------------------------------------------------------
    if (showHelpSupportDialog) {
        AlertDialog(
            onDismissRequest = { showHelpSupportDialog = false },
            containerColor = FlatDarkCard,
            title = {
                Text(
                    tr("Help & support", "सहायता और सपोर्ट"),
                    color = FlatTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = tr(
                            "Need help setting up your shop or training staff?",
                            "अपनी दुकान सेट करने या स्टाफ को सिखाने में मदद चाहिए?"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = FlatTextSecondary
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showHelpSupportDialog = false
                                onOpenWelcomeTour()
                            }
                            .testTag("row_welcome_tour"),
                        shape = RoundedCornerShape(12.dp),
                        color = FlatDarkInput
                    ) {
                        Text(
                            text = tr("★ Take the 4-slide Welcome Tour", "★ 4-स्लाइड वेलकम टूर देखें"),
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    if (canManageShopProfile) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    showHelpSupportDialog = false
                                    onOpenSetupWizard(1)
                                }
                                .testTag("row_setup_wizard"),
                            shape = RoundedCornerShape(12.dp),
                            color = FlatDarkInput
                        ) {
                            Text(
                                text = tr("1-3 Open First-time Setup Wizard", "1-3 दुकान सेटअप विज़ार्ड खोलें"),
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpSupportDialog = false }) {
                    Text(tr("Close", "बंद करें"))
                }
            }
        )
    }

    // -------------------------------------------------------------
    // About KhataWise Dialog
    // -------------------------------------------------------------
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = FlatDarkCard,
            title = {
                Text(
                    "KhataWise · Version 1.0",
                    color = FlatTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = tr(
                        "Smart billing, customer udhari ledger, inventory & GST reports built for Indian shopkeepers.",
                        "भारतीय दुकानदारों के लिए स्मार्ट बिलिंग, ग्राहक उधारी खाता, स्टॉक और GST रिपोर्ट।"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FlatTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(tr("OK", "ठीक है"))
                }
            }
        )
    }

    // -------------------------------------------------------------
    // Page 7: Log out of KhataWise? Modal Sheet Dialog
    // -------------------------------------------------------------
    if (showSignOutConfirmDialog) {
        SettingsLogoutModalDialog(
            userEmail = currentUser?.email.orEmpty(),
            pendingSyncChanges = pendingSyncCount,
            onDismiss = { showSignOutConfirmDialog = false },
            onSyncAndLogout = {
                showSignOutConfirmDialog = false
                scope.launch {
                    try {
                        com.example.PashuAaharApp.instance.firestoreSyncEngine.syncNow()
                    } catch (_: Exception) {}
                    repository.clearAllLocalDataAndSignOut()
                    val msg = if (isHindi) "सिंक और लॉग आउट हो गया।" else "Synced & logged out."
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    onSwitchUser()
                }
            },
            onLogoutAnyway = {
                showSignOutConfirmDialog = false
                scope.launch {
                    repository.clearAllLocalDataAndSignOut()
                    val msg = if (isHindi) "लॉग आउट हो गया।" else "Logged out."
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    onSwitchUser()
                }
            }
        )
    }

    // -------------------------------------------------------------
    // Reset All Shop Data Confirmation (DELETE to confirm)
    // -------------------------------------------------------------
    if (showResetShopConfirmDialog) {
        ResetAllShopDataConfirmationDialog(
            onDismiss = { showResetShopConfirmDialog = false },
            onConfirmReset = {
                showResetShopConfirmDialog = false
                scope.launch {
                    repository.clearAllLocalDataAndSignOut()
                    val msg = if (isHindi) "दुकान का डेटा रीसेट हो गया।" else "All shop data reset."
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    onSwitchUser()
                }
            }
        )
    }
}

@Composable
private fun SettingsMenuRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String?,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = FlatTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = FlatTextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SectionHeaderLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            letterSpacing = 0.8.sp
        ),
        fontWeight = FontWeight.Bold,
        color = FlatTextSecondary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}
