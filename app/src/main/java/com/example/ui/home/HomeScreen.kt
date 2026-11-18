package com.example.ui.home

import com.example.ui.theme.kw
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.PashuAaharApp
import com.example.data.repository.CattleFeedRepository
import com.example.model.Permission
import com.example.model.Role
import com.example.model.SaleEntry
import com.example.model.User
import com.example.model.can
import com.example.sync.FirestoreSyncState
import com.example.ui.search.GlobalSearchScreen
import com.example.ui.theme.CAmber
import com.example.ui.theme.CGreen
import com.example.ui.theme.CGreenContainer
import com.example.ui.theme.CGreenPrimary
import com.example.ui.theme.CRedUdhari
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatTextMuted
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import com.example.util.CurrencyUtils
import com.example.util.ShopAnalytics
import com.example.util.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun HomeScreen(
    repository: CattleFeedRepository,
    currentUser: User?,
    onNavigateToNewBill: () -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onNavigateToDailyRates: () -> Unit,
    onNavigateToEstimate: () -> Unit = {},
    onNavigateToLedger: () -> Unit = {},
    onNavigateToPurchases: () -> Unit = {},
    onNavigateToCustomers: () -> Unit = onNavigateToLedger,
    onNavigateToInventory: () -> Unit = onNavigateToSettings,
    onNavigateToBills: () -> Unit = onNavigateToSettings,
    onNavigateToCollections: () -> Unit = onNavigateToLedger,
    onNavigateToStaff: () -> Unit = onNavigateToSettings,
    onSelectCustomer: (String) -> Unit = { onNavigateToCustomers() }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val products by repository.allProducts.collectAsState(initial = emptyList())
    val customers by repository.allCustomers.collectAsState(initial = emptyList())
    val sales by repository.allSales.collectAsState(initial = emptyList())
    val payments by repository.allPayments.collectAsState(initial = emptyList())
    val settings by repository.settingsFlow.collectAsState(initial = null)
    val syncState by PashuAaharApp.instance.firestoreSyncEngine.syncState.collectAsState()

    val isLightTheme = MaterialTheme.colorScheme.background.red > 0.5f
    val isHindi = settings?.language == "hi"

    val canEditTodaySales = can(currentUser?.role, Permission.EDIT_TODAY_SALES)
    val canAccessInventory = can(currentUser?.role, Permission.ACCESS_INVENTORY)
    val canAccessCollections = can(currentUser?.role, Permission.ACCESS_COLLECTIONS)
    val canViewCustomerFull = can(currentUser?.role, Permission.VIEW_CUSTOMER_FULL)

    // Dynamic clock so startOfToday updates automatically across midnight without going stale
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(30_000L)
        }
    }

    val startOfToday = remember(nowMillis / 60_000L) {
        ShopAnalytics.getStartOfTodayMillis(nowMillis)
    }

    val activeProducts = remember(products) { products.filter { !it.isDeleted } }
    val productsMap = remember(activeProducts) { activeProducts.associateBy { it.id } }
    val customersMap = remember(customers) { customers.associateBy { it.id } }

    val activeSales = remember(sales) {
        sales.filter { !it.isDeleted && it.billStatus != "CANCELLED" }
    }
    val todaySales = remember(activeSales, startOfToday) {
        activeSales.filter { it.date >= startOfToday }.sortedByDescending { it.date }
    }

    var showQuickCollectDialog by remember { mutableStateOf(false) }
    var showGlobalSearch by remember { mutableStateOf(false) }
    var saleToEdit by remember { mutableStateOf<SaleEntry?>(null) }

    if (showGlobalSearch) {
        GlobalSearchScreen(
            repository = repository,
            onCancel = { showGlobalSearch = false },
            onSelectCustomer = { custId ->
                showGlobalSearch = false
                onSelectCustomer(custId)
            },
            onNavigateToBills = {
                showGlobalSearch = false
                onNavigateToBills()
            },
            onNavigateToInventory = {
                showGlobalSearch = false
                onNavigateToInventory()
            },
            onNavigateToSuppliers = {
                showGlobalSearch = false
                onNavigateToPurchases()
            },
            onNavigateToCollections = {
                showGlobalSearch = false
                onNavigateToCollections()
            },
            onNavigateToStaff = {
                showGlobalSearch = false
                onNavigateToStaff()
            }
        )
        return
    }

    val primaryForestGreen = if (isLightTheme) kw(0xFF0E6F43) else CGreen
    val primaryForestGreenTextOn = if (isLightTheme) Color.White else Color(0xFF061A0E)
    val udhariCrimsonRed = if (isLightTheme) kw(0xFFB91C1C) else CRedUdhari
    val lowStockAmber = if (isLightTheme) kw(0xFFB45309) else CAmber

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(FlatDarkBg),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // -------------------------------------------------------------
        // 1. TOP HEADER: Shop Name, User · Role Pill, and Synced Pill
        // (No "Numbers hidden" tab per user instructions)
        // -------------------------------------------------------------
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = settings?.shopName?.ifBlank { "KhataWise Store" } ?: "KhataWise Store",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 23.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${currentUser?.name ?: "Admin"} ·",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FlatTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        val roleLabel = when (currentUser?.role) {
                            Role.ADMINISTRATOR -> tr("Admin", "एडमिन")
                            Role.MANAGER -> tr("Manager", "मैनेजर")
                            else -> tr("Staff", "स्टाफ")
                        }
                        val rolePillBg = if (isLightTheme) kw(0xFFE0ECF8) else Color(0xFF1E3A5F)
                        val rolePillFg = if (isLightTheme) kw(0xFF1E4D8C) else Color(0xFF93C5FD)

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = rolePillBg
                        ) {
                            Text(
                                text = roleLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = rolePillFg,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                val pendingSyncCount = remember(sales, payments, customers, products) {
                    sales.count { !it.isDeleted && it.syncStatus != com.example.model.SyncStatus.SYNCED } +
                        payments.count { !it.isDeleted && it.syncStatus != com.example.model.SyncStatus.SYNCED } +
                        customers.count { !it.isDeleted && it.syncStatus != com.example.model.SyncStatus.SYNCED } +
                        products.count { !it.isDeleted && it.syncStatus != com.example.model.SyncStatus.SYNCED }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { showGlobalSearch = true }
                            .testTag("home_search_btn"),
                        shape = CircleShape,
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = tr("Search customers, bills, and stock", "ग्राहक, बिल और स्टॉक खोजें"),
                                tint = FlatTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    com.example.ui.components.HeaderSyncStatusDotPill(
                        syncState = syncState,
                        pendingSyncCount = pendingSyncCount,
                        isLightTheme = isLightTheme,
                        onClick = {
                            PashuAaharApp.instance.firestoreSyncEngine.triggerSync()
                            val msg = if (isHindi) "क्लाउड के साथ सिंक हो रहा है…" else "Syncing with Cloud…"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // 1B. OFFLINE & SYNC BANNER (Shown under the header when offline, syncing, failed, or backup reminder)
        item {
            val pendingSyncCount = remember(sales, payments, customers, products) {
                sales.count { !it.isDeleted && it.syncStatus != com.example.model.SyncStatus.SYNCED } +
                    payments.count { !it.isDeleted && it.syncStatus != com.example.model.SyncStatus.SYNCED } +
                    customers.count { !it.isDeleted && it.syncStatus != com.example.model.SyncStatus.SYNCED } +
                    products.count { !it.isDeleted && it.syncStatus != com.example.model.SyncStatus.SYNCED }
            }
            val lastSyncMs = settings?.lastSyncTime ?: 0L
            val daysSinceBackup = if (lastSyncMs > 0L) {
                ((nowMillis - lastSyncMs) / (24L * 60L * 60L * 1000L)).toInt().coerceAtLeast(0)
            } else 0

            com.example.ui.components.OfflineAndSyncBanner(
                syncState = syncState,
                pendingChangesCount = pendingSyncCount,
                lastBackupDaysAgo = daysSinceBackup,
                onRetrySync = {
                    PashuAaharApp.instance.firestoreSyncEngine.triggerSync()
                },
                onBackupNow = {
                    PashuAaharApp.instance.firestoreSyncEngine.triggerSync()
                }
            )
        }

        // -------------------------------------------------------------
        // 2. QUICK ACTION PILLS: Today's Rates & Customers
        // -------------------------------------------------------------
        item {
            val ratesBg = if (isLightTheme) kw(0xFFFCEBD2) else Color(0xFF362308)
            val ratesFg = if (isLightTheme) kw(0xFF6B3E08) else CAmber
            val customersBg = if (isLightTheme) kw(0xFFE1F5EB) else Color(0xFF133826)
            val customersFg = if (isLightTheme) kw(0xFF0E623D) else CGreen

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onNavigateToDailyRates() }
                        .testTag("header_daily_rates_button"),
                    shape = RoundedCornerShape(16.dp),
                    color = ratesBg
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = tr("Today's Rates", "आज का भाव"),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp),
                            fontWeight = FontWeight.Bold,
                            color = ratesFg,
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onNavigateToCustomers() }
                        .testTag("header_khata_button"),
                    shape = RoundedCornerShape(16.dp),
                    color = customersBg
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = tr("Customers", "ग्राहक"),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp),
                            fontWeight = FontWeight.Bold,
                            color = customersFg,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 3. ONE LARGE "New Bill" PRIMARY BUTTON
        // -------------------------------------------------------------
        item {
            Button(
                onClick = onNavigateToNewBill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CGreenPrimary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("open_new_bill_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.ShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = tr("New bill", "नया बिल"),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // -------------------------------------------------------------
        // 5. TODAY'S SALES SECTION HEADER
        // -------------------------------------------------------------
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr("Today's Sales", "आज की बिक्री"),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 19.sp,
                        letterSpacing = (-0.2).sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary
                )
                Text(
                    text = tr("View all Khata →", "पूरा खाता देखें →"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = primaryForestGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onNavigateToLedger() }
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                )
            }
        }

        // -------------------------------------------------------------
        // 6. TODAY'S SALES LIST
        // (Per-customer transaction amount is NEVER hidden)
        // -------------------------------------------------------------
        val todayGroupedBills = com.example.model.groupSalesIntoBills(todaySales, descending = true)
        if (todayGroupedBills.isEmpty()) {
            item {
                com.example.ui.components.EmptyBillsTodayStateCard(
                    onNewBill = onNavigateToNewBill
                )
            }
        } else {
            items(todayGroupedBills, key = { "today_sale_${it.id}" }) { bill ->
                val sale = bill.primarySale
                val customer = customersMap[bill.customerId]
                val customerName = customer?.name?.ifBlank { "Customer" } ?: "Customer"
                val customerVillage = customer?.village?.trim().orEmpty()
                val feedName = productsMap[sale.productId]?.feedName ?: tr("Feed item", "पशु आहार")

                val initials = remember(customerName) {
                    val parts = customerName.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
                    when {
                        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
                        parts.size == 1 && parts[0].length >= 2 -> parts[0].take(2).uppercase()
                        parts.size == 1 -> parts[0].take(1).uppercase()
                        else -> "CU"
                    }
                }

                val avatarBg = if (isLightTheme) kw(0xFFE6EEF6) else Color(0xFF1E293B)
                val avatarFg = if (isLightTheme) kw(0xFF1E3A5F) else Color(0xFF93C5FD)

                val isUdhariSale = bill.udhariAmount > BigDecimal.ZERO
                val statusPillBg = if (isUdhariSale) {
                    if (isLightTheme) kw(0xFFFDE8E8) else Color(0xFF331416)
                } else {
                    if (isLightTheme) kw(0xFFE1F5EB) else CGreenContainer
                }
                val statusPillFg = if (isUdhariSale) {
                    udhariCrimsonRed
                } else {
                    primaryForestGreen
                }
                val statusPillLabel = when {
                    isUdhariSale && bill.paidAmount > BigDecimal.ZERO -> tr("Part udhari", "आंशिक उधारी")
                    isUdhariSale -> tr("Udhari", "उधारी")
                    else -> tr("Paid · Cash", "नकद प्राप्त")
                }

                val extraSuffix = if (bill.extraItemCount > 0) " +${bill.extraItemCount}" else ""
                val subtitleText = if (customerVillage.isNotBlank()) {
                    "$customerVillage · $feedName (${CurrencyUtils.formatWeight(sale.quantityKg)} kg)$extraSuffix"
                } else {
                    "$feedName · ${CurrencyUtils.formatWeight(sale.quantityKg)} kg$extraSuffix"
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = canEditTodaySales) { saleToEdit = sale },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = FlatDarkCard),
                    border = BorderStroke(1.dp, FlatDarkBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(avatarBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initials,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = avatarFg
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = customerName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = subtitleText,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                    color = FlatTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Always show per-customer transaction amount clearly with tabular figures
                                Text(
                                    text = CurrencyUtils.formatInr(bill.totalSalePrice),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 16.sp,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary
                                )

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = statusPillBg
                                ) {
                                    Text(
                                        text = statusPillLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = statusPillFg,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            if (canEditTodaySales) {
                                IconButton(
                                    onClick = { saleToEdit = sale },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("edit_today_sale_${bill.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = tr("Edit today's sale", "आज की बिक्री बदलें"),
                                        tint = FlatTextMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Admin/Manager Edit Today's Sale Dialog
    saleToEdit?.let { sale ->
        var qtyInput by remember(sale) { mutableStateOf(sale.quantityKg.stripTrailingZeros().toPlainString()) }
        var rateInput by remember(sale) { mutableStateOf(sale.pricePerKgAtSale.stripTrailingZeros().toPlainString()) }
        var paidInput by remember(sale) { mutableStateOf(sale.paidAmount.stripTrailingZeros().toPlainString()) }
        var reasonInput by remember(sale) { mutableStateOf("Corrected today's sale") }

        AlertDialog(
            onDismissRequest = { saleToEdit = null },
            containerColor = FlatDarkCard,
            title = {
                Text(
                    tr("Edit Today's Sale", "आज की बिक्री संशोधित करें"),
                    color = FlatTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = qtyInput,
                        onValueChange = { qtyInput = it },
                        label = { Text(tr("Quantity (kg)", "मात्रा (किग्रा)")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = rateInput,
                        onValueChange = { rateInput = it },
                        label = { Text(tr("Rate per kg (₹)", "दर प्रति किग्रा (₹)")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = paidInput,
                        onValueChange = { paidInput = it },
                        label = { Text(tr("Paid Amount (₹)", "जमा राशि (₹)")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = reasonInput,
                        onValueChange = { reasonInput = it },
                        label = { Text(tr("Edit reason", "कारण")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val user = currentUser ?: return@Button
                        val newQty = qtyInput.toBigDecimalOrNull() ?: sale.quantityKg
                        val newRate = rateInput.toBigDecimalOrNull() ?: sale.pricePerKgAtSale
                        val newPaid = paidInput.toBigDecimalOrNull() ?: sale.paidAmount
                        scope.launch {
                            repository.editSaleBill(
                                saleId = sale.id,
                                newQuantityKg = newQty,
                                newPricePerKg = newRate,
                                newPaidAmount = newPaid,
                                reason = reasonInput,
                                performedBy = user
                            )
                            saleToEdit = null
                            Toast.makeText(context, "Sale updated", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CGreenPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Text(tr("Save changes", "बदलाव सेव करें"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { saleToEdit = null }) {
                    Text(tr("Cancel", "रद्द करें"))
                }
            }
        )
    }

    // Quick Collect Udhari Dialog (Admin & Manager only)
    if (showQuickCollectDialog && canAccessCollections) {
        val customersWithDue = remember(customers) {
            customers.filter { !it.isDeleted && it.totalUdhari > BigDecimal.ZERO }
                .sortedByDescending { it.totalUdhari }
        }
        var selectedDueCustomer by remember { mutableStateOf(customersWithDue.firstOrNull()) }
        var collectAmountStr by remember { mutableStateOf("") }
        var collectNote by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showQuickCollectDialog = false },
            containerColor = FlatDarkCard,
            title = {
                Text(
                    tr("Collect udhari payment", "उधारी वसूली दर्ज करें"),
                    color = FlatTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (customersWithDue.isEmpty()) {
                        Text(
                            tr("No customers currently have pending Udhari!", "किसी भी ग्राहक पर उधारी बाक़ी नहीं है!"),
                            color = primaryForestGreen
                        )
                    } else {
                        Text(
                            tr("Select Customer:", "ग्राहक चुनें:"),
                            style = MaterialTheme.typography.labelSmall,
                            color = FlatTextSecondary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            customersWithDue.forEach { cust ->
                                FilterChip(
                                    selected = selectedDueCustomer?.id == cust.id,
                                    onClick = {
                                        selectedDueCustomer = cust
                                        collectAmountStr = cust.totalUdhari.stripTrailingZeros().toPlainString()
                                    },
                                    label = {
                                        Text("${cust.name} (${CurrencyUtils.formatInr(cust.totalUdhari)})")
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CGreenContainer,
                                        selectedLabelColor = CGreen
                                    ),
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                )
                            }
                        }

                        selectedDueCustomer?.let { cust ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = collectAmountStr == cust.totalUdhari.stripTrailingZeros().toPlainString(),
                                    onClick = {
                                        collectAmountStr = cust.totalUdhari.stripTrailingZeros().toPlainString()
                                    },
                                    label = { Text(tr("Full due", "पूरा बाक़ी")) }
                                )
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        collectAmountStr = cust.totalUdhari.divide(BigDecimal("2"), 0, RoundingMode.HALF_UP).toPlainString()
                                    },
                                    label = { Text(tr("Half (50%)", "आधा (50%)")) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = collectAmountStr,
                            onValueChange = { collectAmountStr = it },
                            label = { Text(tr("Amount Received (₹)", "प्राप्त राशि (₹)")) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("collect_udhari_amount_input")
                        )

                        OutlinedTextField(
                            value = collectNote,
                            onValueChange = { collectNote = it },
                            label = { Text(tr("Note (Cash / UPI / Ref)", "नोट (नकद / UPI)")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (customersWithDue.isNotEmpty()) {
                    Button(
                        onClick = {
                            val cust = selectedDueCustomer ?: return@Button
                            val amt = collectAmountStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
                            if (amt > BigDecimal.ZERO) {
                                scope.launch {
                                    repository.recordPayment(
                                        customerId = cust.id,
                                        amount = amt,
                                        note = collectNote.ifBlank { "Cash received" },
                                        collectedByUserId = currentUser?.id ?: "admin",
                                        performedByRole = currentUser?.role
                                    )
                                    showQuickCollectDialog = false
                                    Toast.makeText(context, "Recorded ${CurrencyUtils.formatInr(amt)} from ${cust.name}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CGreenPrimary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("confirm_collect_udhari_btn")
                    ) {
                        Text(tr("Save payment", "जमा करें"), fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickCollectDialog = false }) {
                    Text(tr("Close", "बंद करें"))
                }
            }
        )
    }
}

@Composable
private fun SyncStatusPill(
    syncState: FirestoreSyncState,
    isLightTheme: Boolean,
    onSyncClick: () -> Unit
) {
    val (label, dotColor, textColor, bgColor) = when (syncState) {
        is FirestoreSyncState.Syncing -> Quadruple(
            tr("Syncing…", "सिंक…"),
            if (isLightTheme) kw(0xFFD97706) else CAmber,
            if (isLightTheme) kw(0xFF92400E) else CAmber,
            if (isLightTheme) kw(0xFFFEF3C7) else Color(0xFF2E1C05)
        )
        is FirestoreSyncState.Success, is FirestoreSyncState.Idle -> Quadruple(
            tr("Synced", "सिंक है"),
            if (isLightTheme) kw(0xFF0E6F43) else CGreen,
            if (isLightTheme) kw(0xFF0E6F43) else CGreen,
            if (isLightTheme) kw(0xFFE1F5EB) else CGreenContainer
        )
        is FirestoreSyncState.Error, is FirestoreSyncState.Unauthenticated -> Quadruple(
            tr("Offline", "ऑफ़लाइन"),
            FlatTextSecondary,
            FlatTextSecondary,
            if (isLightTheme) kw(0xFFEAE8E1) else FlatDarkCard
        )
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = bgColor,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable { onSyncClick() }
            .testTag("home_sync_status_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

@Composable
private fun FriendlyEmptyRegisterIllustration(
    onCreateBill: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCreateBill() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = FlatDarkCard),
        border = BorderStroke(1.dp, FlatDarkBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Canvas(modifier = Modifier.size(72.dp)) {
                val stroke = Stroke(width = 3.dp.toPx())
                drawRoundRect(
                    color = kw(0xFFE1F5EB),
                    topLeft = Offset(size.width * 0.15f, size.height * 0.1f),
                    size = Size(size.width * 0.7f, size.height * 0.8f),
                    cornerRadius = CornerRadius(12.dp.toPx())
                )
                drawRoundRect(
                    color = kw(0xFF0E6F43),
                    topLeft = Offset(size.width * 0.15f, size.height * 0.1f),
                    size = Size(size.width * 0.7f, size.height * 0.8f),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = stroke
                )
                for (i in 1..3) {
                    val y = size.height * (0.25f + i * 0.15f)
                    drawLine(
                        color = kw(0xFFD97706),
                        start = Offset(size.width * 0.28f, y),
                        end = Offset(size.width * 0.72f, y),
                        strokeWidth = 3.dp.toPx()
                    )
                }
            }
            Text(
                text = tr("No Bills Recorded Today Yet", "आज अभी तक कोई बिल दर्ज नहीं है"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = FlatTextPrimary
            )
            Text(
                text = tr(
                    "Tap 'New Bill' above to record your first cattle feed or grain sale in seconds.",
                    "अपनी पहली पशु आहार या अनाज बिक्री दर्ज करने के लिए ऊपर 'नया बिल' पर टैप करें।"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = FlatTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
