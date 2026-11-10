package com.example.ui.ledger

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.repository.CattleFeedRepository
import com.example.model.Customer
import com.example.model.CustomerStatusChip
import com.example.model.PaymentEntry
import com.example.model.Permission
import com.example.model.Product
import com.example.model.SaleEntry
import com.example.model.User
import com.example.model.can
import com.example.model.getCustomerStatusChip
import com.example.ui.home.QuickAddCustomerDialog
import com.example.ui.theme.CAmber
import com.example.ui.theme.CAmberBg
import com.example.ui.theme.CGreen
import com.example.ui.theme.CRedUdhari
import com.example.ui.theme.CRedUdhariBg
import com.example.ui.theme.CRedUdhariBorder
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatDarkInput
import com.example.ui.theme.FlatDarkInputBorder
import com.example.ui.theme.FlatDarkNav
import com.example.ui.theme.FlatTextMuted
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusAmberBg
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusGreenBg
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusRedBg
import com.example.util.CurrencyUtils
import com.example.util.tr
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LedgerBrandGreen = Color(0xFF0E6F43)
private val LedgerMintBg = Color(0xFFE1F5EB)
private val LedgerCrimson = Color(0xFFB91C1C)
private val LedgerCrimsonSoftBg = Color(0xFFFDE8E8)
private val LedgerAvatarBg = Color(0xFFE6EEF6)
private val LedgerAvatarText = Color(0xFF1E3A5F)

private enum class CustomerFilterTab {
    ALL,
    UDHARI_ONLY,
    CLEAR
}

@Composable
fun LedgerScreen(
    repository: CattleFeedRepository,
    currentUser: User?,
    onCustomerClick: (String) -> Unit,
    onNavigateToNewBill: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    val customers by repository.allCustomers.collectAsState(initial = emptyList())
    val sales by repository.allSales.collectAsState(initial = emptyList())
    val payments by repository.allPayments.collectAsState(initial = emptyList())
    val products by repository.allProducts.collectAsState(initial = emptyList())
    val settings by repository.settingsFlow.collectAsState(initial = null)

    val canViewCustomerFull = can(currentUser?.role, Permission.VIEW_CUSTOMER_FULL)
    val canAccessCollections = can(currentUser?.role, Permission.ACCESS_COLLECTIONS)

    val watchThreshold = settings?.watchThreshold ?: BigDecimal("2000")
    val criticalThreshold = settings?.criticalThreshold ?: BigDecimal("5000")

    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var showImportCustomersFlow by remember { mutableStateOf(false) }
    var showRecordPaymentDialog by remember { mutableStateOf(false) }

    if (showAddCustomerDialog) {
        AddOrEditCustomerScreen(
            existingCustomer = null,
            currentUserId = currentUser?.id.orEmpty(),
            currentUserRole = currentUser?.role,
            onCancel = { showAddCustomerDialog = false },
            onSaveCustomer = { cust ->
                scope.launch {
                    repository.saveCustomer(cust, actorRole = currentUser?.role)
                    showAddCustomerDialog = false
                }
            }
        )
        return
    }

    if (showImportCustomersFlow) {
        ImportCustomersFlowScreen(
            existingCustomers = customers,
            currentUserId = currentUser?.id.orEmpty(),
            onClose = { showImportCustomersFlow = false },
            onImportCustomersBatch = { batch ->
                scope.launch {
                    batch.forEach { c ->
                        try {
                            repository.saveCustomer(c, actorRole = currentUser?.role)
                        } catch (_: Exception) {}
                    }
                }
            }
        )
        return
    }

    // Ensure selectedTab is valid when role cannot access Repayments tab
    val maxTabs = if (canAccessCollections) 3 else 2
    if (selectedTab >= maxTabs) {
        selectedTab = 0
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(FlatDarkBg)
        ) {
            // Header Tabs: Customers, Sales Ledger, and (for Admin/Manager) Repayments
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = FlatDarkNav,
                contentColor = FlatTextPrimary,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = LedgerBrandGreen,
                            height = 2.5.dp
                        )
                    }
                },
                divider = {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(FlatDarkBorder))
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = tr("Customers", "ग्राहक"),
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = if (selectedTab == 0) LedgerBrandGreen else FlatTextSecondary,
                                maxLines = 1
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = FlatDarkInput
                            ) {
                                Text(
                                    text = customers.size.toString(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = tr("Sales ledger", "बिक्री खाता"),
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = if (selectedTab == 1) LedgerBrandGreen else FlatTextSecondary,
                            maxLines = 1
                        )
                    }
                )
                if (canAccessCollections) {
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Text(
                                text = tr("Repayments", "वसूली"),
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = if (selectedTab == 2) LedgerBrandGreen else FlatTextSecondary,
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> CustomersTab(
                    customers = customers,
                    canViewCustomerFull = canViewCustomerFull,
                    watchThreshold = watchThreshold,
                    criticalThreshold = criticalThreshold,
                    onCustomerClick = onCustomerClick,
                    onAddCustomer = { showAddCustomerDialog = true },
                    onImportCustomers = { showImportCustomersFlow = true }
                )
                1 -> SalesTab(
                    sales = sales,
                    products = products,
                    customers = customers,
                    canViewCustomerFull = canViewCustomerFull
                )
                2 -> if (canAccessCollections) {
                    PaymentsTab(
                        payments = payments,
                        customers = customers
                    )
                }
            }
        }

        // Extended Floating Action Button ("+ Customer", "+ New Bill", or "+ Repayment")
        if (selectedTab == 0 || selectedTab == 1 || (selectedTab == 2 && canAccessCollections)) {
            Surface(
                onClick = {
                    when (selectedTab) {
                        0 -> showAddCustomerDialog = true
                        1 -> onNavigateToNewBill()
                        2 -> if (canAccessCollections) showRecordPaymentDialog = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 18.dp)
                    .height(50.dp)
                    .testTag("ledger_fab"),
                shape = RoundedCornerShape(16.dp),
                color = LedgerBrandGreen,
                contentColor = Color.White,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = when (selectedTab) {
                            0 -> "Add Customer"
                            1 -> "New Bill"
                            else -> "Record Repayment"
                        },
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = when (selectedTab) {
                            0 -> tr("Customer", "ग्राहक")
                            1 -> tr("New bill", "नया बिल")
                            else -> tr("Repayment", "वसूली")
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
            }
        }
    }

    if (showAddCustomerDialog) {
        QuickAddCustomerDialog(
            currentUserId = currentUser?.id ?: "",
            currentUserRole = currentUser?.role,
            onDismiss = { showAddCustomerDialog = false },
            onCustomerAdded = { cust ->
                scope.launch {
                    repository.saveCustomer(cust, actorRole = currentUser?.role)
                    showAddCustomerDialog = false
                }
            }
        )
    }

    if (showRecordPaymentDialog && canAccessCollections) {
        RecordPaymentDialog(
            customers = customers,
            currentUserId = currentUser?.id ?: "",
            onDismiss = { showRecordPaymentDialog = false },
            onPaymentRecorded = { custId, amt, note ->
                scope.launch {
                    repository.recordPayment(
                        customerId = custId,
                        amount = amt,
                        note = note,
                        collectedByUserId = currentUser?.id ?: "",
                        actorRole = currentUser?.role
                    )
                    showRecordPaymentDialog = false
                }
            }
        )
    }
}

@Composable
fun CustomersTab(
    customers: List<Customer>,
    canViewCustomerFull: Boolean = true,
    watchThreshold: BigDecimal,
    criticalThreshold: BigDecimal,
    onCustomerClick: (String) -> Unit,
    onAddCustomer: () -> Unit = {},
    onImportCustomers: () -> Unit = {}
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var filterTab by remember { mutableStateOf(CustomerFilterTab.ALL) }
    var sortByUdhariDesc by remember { mutableStateOf(true) }
    // Only hide Total outstanding Amount, never hide per-customer amount
    var hideTotalUdhari by remember { mutableStateOf(true) }

    val filteredCustomers = remember(customers, searchQuery, filterTab, sortByUdhariDesc, canViewCustomerFull) {
        customers
            .filter { c ->
                val matchesSearch = c.name.contains(searchQuery, ignoreCase = true) ||
                        c.village.contains(searchQuery, ignoreCase = true) ||
                        c.phone.contains(searchQuery, ignoreCase = true)
                val matchesFilter = when (filterTab) {
                    CustomerFilterTab.ALL -> true
                    CustomerFilterTab.UDHARI_ONLY -> c.totalUdhari > BigDecimal.ZERO
                    CustomerFilterTab.CLEAR -> c.totalUdhari <= BigDecimal.ZERO
                }
                matchesSearch && matchesFilter
            }
            .sortedWith(
                if (sortByUdhariDesc && canViewCustomerFull) {
                    compareByDescending<Customer> { it.totalUdhari }
                        .thenBy { it.name.lowercase() }
                } else {
                    compareBy { it.name.lowercase() }
                }
            )
    }

    val totalOutstandingUdhari = remember(customers) {
        customers.fold(BigDecimal.ZERO) { acc, c -> acc.add(c.totalUdhari) }
    }
    val dueAccountsCount = remember(customers) {
        customers.count { it.totalUdhari > BigDecimal.ZERO }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Total market udhari Card (Tap card to reveal/hide; no eye icons per user request)
        if (canViewCustomerFull) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 6.dp)
                    .clickable { hideTotalUdhari = !hideTotalUdhari }
                    .testTag("toggle_total_udhari_button"),
                shape = RoundedCornerShape(18.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = tr("Total market udhari", "कुल बाज़ार उधारी"),
                            fontSize = 13.sp,
                            color = FlatTextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (hideTotalUdhari) "₹ ••••" else CurrencyUtils.formatInr(totalOutstandingUdhari),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = LedgerCrimson,
                            modifier = Modifier.testTag("total_market_udhari_text")
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = LedgerCrimsonSoftBg
                    ) {
                        Text(
                            text = "$dueAccountsCount ${tr("accounts due", "खाते बाकी")}",
                            fontSize = 12.sp,
                            color = LedgerCrimson,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = tr("Search name, village, phone...", "नाम, गाँव, फ़ोन खोजें..."),
                    color = FlatTextMuted,
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = FlatTextSecondary
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = tr("Clear search", "सर्च साफ़ करें"), tint = FlatTextSecondary)
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = FlatDarkCard,
                unfocusedContainerColor = FlatDarkCard,
                focusedBorderColor = LedgerBrandGreen,
                unfocusedBorderColor = FlatDarkBorder,
                focusedTextColor = FlatTextPrimary,
                unfocusedTextColor = FlatTextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("customer_search_input"),
            shape = RoundedCornerShape(16.dp)
        )

        // Filter Chips ("All", "Udhari only", "Clear") + Sort Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "All" pill
                val isAll = filterTab == CustomerFilterTab.ALL
                Surface(
                    onClick = { filterTab = CustomerFilterTab.ALL },
                    shape = RoundedCornerShape(50),
                    color = if (isAll) LedgerBrandGreen else FlatDarkCard,
                    border = if (isAll) null else BorderStroke(1.dp, FlatDarkBorder)
                ) {
                    Text(
                        text = tr("All", "सभी"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAll) Color.White else FlatTextPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }

                // "Udhari only" pill
                val isUdhariOnly = filterTab == CustomerFilterTab.UDHARI_ONLY
                Surface(
                    onClick = {
                        filterTab = if (isUdhariOnly) CustomerFilterTab.ALL else CustomerFilterTab.UDHARI_ONLY
                    },
                    shape = RoundedCornerShape(50),
                    color = if (isUdhariOnly) LedgerCrimson else LedgerCrimsonSoftBg,
                    modifier = Modifier.testTag("filter_udhari_checkbox")
                ) {
                    Text(
                        text = tr("Udhari only", "केवल उधारी"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUdhariOnly) Color.White else LedgerCrimson,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }

                // "Clear" pill
                val isClearOnly = filterTab == CustomerFilterTab.CLEAR
                Surface(
                    onClick = {
                        filterTab = if (isClearOnly) CustomerFilterTab.ALL else CustomerFilterTab.CLEAR
                    },
                    shape = RoundedCornerShape(50),
                    color = if (isClearOnly) LedgerBrandGreen else LedgerMintBg
                ) {
                    Text(
                        text = tr("Clear", "क्लियर"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isClearOnly) Color.White else LedgerBrandGreen,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onImportCustomers,
                    shape = RoundedCornerShape(50),
                    color = FlatDarkInput,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier.testTag("ledger_import_customers_btn")
                ) {
                    Text(
                        text = tr("Import", "इम्पोर्ट"),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = LedgerBrandGreen,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }

                // Sort pill button
                Surface(
                    onClick = { sortByUdhariDesc = !sortByUdhariDesc },
                    shape = RoundedCornerShape(50),
                    color = FlatDarkInput,
                    border = BorderStroke(1.dp, FlatDarkBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = null,
                            tint = FlatTextPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = tr("Sort", "क्रम"),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                    }
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 92.dp, top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filteredCustomers.isEmpty()) {
                item {
                    if (searchQuery.isNotBlank()) {
                        com.example.ui.components.NoSearchResultsStateCard(
                            query = searchQuery,
                            onClearSearch = { searchQuery = "" }
                        )
                    } else {
                        com.example.ui.components.EmptyCustomersStateCard(
                            onAddCustomer = onAddCustomer,
                            onImportContacts = onImportCustomers
                        )
                    }
                }
            } else {
                items(filteredCustomers, key = { it.id }) { customer ->
                    CustomerCardItem(
                        customer = customer,
                        canViewCustomerFull = canViewCustomerFull,
                        watchThreshold = watchThreshold,
                        criticalThreshold = criticalThreshold,
                        onClick = { onCustomerClick(customer.id) },
                        onCallClick = {
                            if (customer.phone.isNotBlank()) {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.phone}"))
                                context.startActivity(intent)
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun customerInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "?"
    }
}

@Composable
fun CustomerCardItem(
    customer: Customer,
    canViewCustomerFull: Boolean = true,
    watchThreshold: BigDecimal,
    criticalThreshold: BigDecimal,
    onClick: () -> Unit,
    onCallClick: () -> Unit
) {
    val statusChip = remember(customer) { getCustomerStatusChip(customer) }
    val initials = remember(customer.name) { customerInitials(customer.name) }

    val (badgeText, badgeBg, badgeTextColor) = remember(customer, statusChip, criticalThreshold) {
        if (customer.totalUdhari <= BigDecimal.ZERO) {
            Triple("Clear", LedgerMintBg, LedgerBrandGreen)
        } else {
            val now = System.currentTimeMillis()
            val promiseDate = customer.promiseDate
            if (promiseDate != null) {
                val diffDays = ((now - promiseDate) / (24L * 60L * 60L * 1000L)).toInt()
                if (diffDays > 0) {
                    Triple("Overdue $diffDays days", LedgerCrimson, Color.White)
                } else {
                    val dueIn = (-diffDays).coerceAtLeast(1)
                    Triple("Due $dueIn days", LedgerCrimsonSoftBg, LedgerCrimson)
                }
            } else if (statusChip == CustomerStatusChip.OVER_LIMIT || customer.totalUdhari >= criticalThreshold) {
                Triple(
                    if (statusChip == CustomerStatusChip.OVER_LIMIT) "Over limit" else "Overdue",
                    LedgerCrimson,
                    Color.White
                )
            } else {
                Triple("Has Udhari", LedgerCrimsonSoftBg, LedgerCrimson)
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("customer_item_${customer.id}"),
        shape = RoundedCornerShape(18.dp),
        color = FlatDarkCard,
        border = BorderStroke(1.dp, FlatDarkBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 2-Letter Initials Avatar Circle
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(LedgerAvatarBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = LedgerAvatarText
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name & Village · Phone
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val subText = buildString {
                    if (customer.village.isNotBlank()) append(customer.village)
                    if (customer.fatherName.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append("S/o ${customer.fatherName}")
                    }
                    if (customer.phone.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(customer.phone)
                    }
                }
                if (subText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subText,
                        fontSize = 13.sp,
                        color = FlatTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Per-Customer Amount (NEVER hidden for Admin/Manager) + Status Pill
            Column(horizontalAlignment = Alignment.End) {
                if (canViewCustomerFull) {
                    Text(
                        text = if (customer.totalUdhari <= BigDecimal.ZERO) {
                            "₹ 0"
                        } else {
                            CurrencyUtils.formatInr(customer.totalUdhari)
                        },
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeBg
                ) {
                    Text(
                        text = if (!canViewCustomerFull) {
                            tr(statusChip.labelEn, statusChip.labelHi)
                        } else {
                            badgeText
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

private enum class SalesFilterType {
    ALL,
    UDHARI,
    PAID
}

private enum class RepaymentFilterType {
    ALL,
    CASH,
    UPI
}

private fun formatSectionDateHeader(timestamp: Long): String {
    val calNow = java.util.Calendar.getInstance()
    val todayYear = calNow.get(java.util.Calendar.YEAR)
    val todayDay = calNow.get(java.util.Calendar.DAY_OF_YEAR)

    val calTarget = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val targetYear = calTarget.get(java.util.Calendar.YEAR)
    val targetDay = calTarget.get(java.util.Calendar.DAY_OF_YEAR)

    return when {
        todayYear == targetYear && todayDay == targetDay -> "TODAY"
        todayYear == targetYear && todayDay - targetDay == 1 -> "YESTERDAY"
        else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp)).uppercase(Locale.getDefault())
    }
}

@Composable
fun SalesTab(
    sales: List<SaleEntry>,
    products: List<Product>,
    customers: List<Customer>,
    canViewCustomerFull: Boolean = true
) {
    var selectedFilter by remember { mutableStateOf(SalesFilterType.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedBillForDetail by remember { mutableStateOf<com.example.model.GroupedSaleBill?>(null) }

    val productMap = remember(products) { products.associateBy { it.id } }
    val customerMap = remember(customers) { customers.associateBy { it.id } }

    val allGroupedBills = remember(sales) {
        com.example.model.groupSalesIntoBills(
            sales.filter { !it.isDeleted && it.billStatus != "CANCELLED" },
            descending = true
        )
    }

    val filteredBills = remember(allGroupedBills, selectedFilter, searchQuery, productMap, customerMap) {
        allGroupedBills
            .filter { bill ->
                val matchesFilter = when (selectedFilter) {
                    SalesFilterType.ALL -> true
                    SalesFilterType.UDHARI -> bill.udhariAmount > BigDecimal.ZERO
                    SalesFilterType.PAID -> bill.udhariAmount <= BigDecimal.ZERO
                }
                val custName = customerMap[bill.customerId]?.name.orEmpty()
                val q = searchQuery.trim()
                val anyProdMatches = bill.items.any { item ->
                    productMap[item.productId]?.feedName.orEmpty().contains(q, ignoreCase = true)
                }
                val matchesSearch = q.isEmpty() ||
                    custName.contains(q, ignoreCase = true) ||
                    anyProdMatches ||
                    bill.billNumber.contains(q, ignoreCase = true)
                matchesFilter && matchesSearch
            }
    }

    val groupedSales = remember(filteredBills) {
        filteredBills.groupBy { formatSectionDateHeader(it.date) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter Pills: All | Udhari | Paid
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val filterOptions = listOf(
                SalesFilterType.ALL to tr("All", "सभी"),
                SalesFilterType.UDHARI to tr("Udhari", "उधारी"),
                SalesFilterType.PAID to tr("Paid", "भुगतान")
            )
            filterOptions.forEach { (type, label) ->
                val isSelected = selectedFilter == type
                Surface(
                    onClick = { selectedFilter = type },
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) LedgerBrandGreen else FlatDarkCard,
                    border = if (isSelected) null else BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier.testTag("sales_filter_${type.name.lowercase()}")
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else FlatTextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = tr("Search customer, item...", "ग्राहक या सामान खोजें..."),
                    color = FlatTextMuted,
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = FlatTextSecondary
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = tr("Clear search", "सर्च साफ़ करें"), tint = FlatTextSecondary)
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = FlatDarkCard,
                unfocusedContainerColor = FlatDarkCard,
                focusedBorderColor = LedgerBrandGreen,
                unfocusedBorderColor = FlatDarkBorder,
                focusedTextColor = FlatTextPrimary,
                unfocusedTextColor = FlatTextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("sales_ledger_search_input"),
            shape = RoundedCornerShape(16.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (filteredBills.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(tr("No sales entries yet", "अभी कोई बिक्री प्रविष्टि नहीं है"), color = FlatTextMuted)
                    }
                }
            } else {
                groupedSales.forEach { (sectionHeader, sectionBills) ->
                    item(key = "header_$sectionHeader") {
                        Text(
                            text = sectionHeader,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.6.sp,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp, start = 2.dp)
                        )
                    }
                    items(sectionBills, key = { it.id }) { bill ->
                        val primarySale = bill.primarySale
                        val prod = productMap[primarySale.productId]
                        val cust = customerMap[bill.customerId]
                        val itemQtyDescription = remember(bill, prod) {
                            val feedName = prod?.feedName ?: "Feed"
                            val firstDesc = if (prod != null && !prod.isLooseOnly && prod.kgPerBag > BigDecimal.ZERO &&
                                primarySale.quantityKg >= prod.kgPerBag &&
                                primarySale.quantityKg.remainder(prod.kgPerBag).compareTo(BigDecimal.ZERO) == 0
                            ) {
                                val bags = primarySale.quantityKg.divideToIntegralValue(prod.kgPerBag).toInt()
                                "$feedName × $bags ${if (bags == 1) "bag" else "bags"}"
                            } else {
                                "$feedName · ${CurrencyUtils.formatWeightKg(primarySale.quantityKg)}"
                            }
                            if (bill.extraItemCount > 0) "$firstDesc +${bill.extraItemCount}" else firstDesc
                        }

                        val hasUdhari = bill.udhariAmount > BigDecimal.ZERO
                        val isPartialUdhari = hasUdhari && bill.paidAmount > BigDecimal.ZERO
                        val (pillText, pillBg, pillColor) = when {
                            isPartialUdhari -> Triple(tr("Part udhari", "आंशिक उधारी"), LedgerCrimsonSoftBg, LedgerCrimson)
                            hasUdhari -> Triple(tr("Udhari", "उधारी"), LedgerCrimsonSoftBg, LedgerCrimson)
                            else -> Triple(tr("Paid · Cash", "भुगतान · नकद"), LedgerMintBg, LedgerBrandGreen)
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { selectedBillForDetail = bill }
                                .testTag("sale_ledger_item_${bill.id}"),
                            shape = RoundedCornerShape(18.dp),
                            color = FlatDarkCard,
                            border = BorderStroke(1.dp, FlatDarkBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left: Customer Name + Item & Quantity
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cust?.name ?: tr("Walk-in Cash", "नकद ग्राहक"),
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FlatTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = itemQtyDescription,
                                        fontSize = 13.sp,
                                        color = FlatTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Right: Total Amount + Status Pill
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = CurrencyUtils.formatInr(bill.totalSalePrice),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FlatTextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = pillBg
                                    ) {
                                        Text(
                                            text = pillText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = pillColor,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Bill Detail Dialog (Shows Bill No. and all items when clicked on a bill card)
    selectedBillForDetail?.let { bill ->
        val cust = customerMap[bill.customerId]
        val billNo = "#${bill.billNumber.ifBlank { bill.id.takeLast(4).uppercase(Locale.getDefault()) }}"
        val dateTimeFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

        AlertDialog(
            onDismissRequest = { selectedBillForDetail = null },
            containerColor = FlatDarkCard,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${tr("Bill", "बिल")} $billNo",
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (bill.udhariAmount > BigDecimal.ZERO) LedgerCrimsonSoftBg else LedgerMintBg
                    ) {
                        Text(
                            text = if (bill.udhariAmount > BigDecimal.ZERO) {
                                if (bill.paidAmount > BigDecimal.ZERO) tr("Part udhari", "आंशिक उधारी") else tr("Udhari", "उधारी")
                            } else {
                                tr("Paid", "भुगतान")
                            },
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (bill.udhariAmount > BigDecimal.ZERO) LedgerCrimson else LedgerBrandGreen,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tr("Bill Number:", "बिल नंबर:"), color = FlatTextSecondary, fontSize = 13.sp)
                        Text(billNo, fontWeight = FontWeight.Bold, color = FlatTextPrimary, fontSize = 13.5.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tr("Customer:", "ग्राहक:"), color = FlatTextSecondary, fontSize = 13.sp)
                        Text(cust?.name ?: tr("Walk-in Cash", "नकद ग्राहक"), fontWeight = FontWeight.Bold, color = FlatTextPrimary, fontSize = 13.5.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tr("Date & Time:", "दिनांक और समय:"), color = FlatTextSecondary, fontSize = 13.sp)
                        Text(dateTimeFormat.format(Date(bill.date)), color = FlatTextPrimary, fontSize = 13.sp)
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(FlatDarkBorder))
                    bill.items.forEach { itemSale ->
                        val p = productMap[itemSale.productId]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = p?.feedName ?: "Feed",
                                    fontWeight = FontWeight.SemiBold,
                                    color = FlatTextPrimary,
                                    fontSize = 13.5.sp
                                )
                                Text(
                                    text = "${CurrencyUtils.formatWeightKg(itemSale.quantityKg)} @ ${CurrencyUtils.formatInr(itemSale.pricePerKgAtSale, true)}/kg",
                                    color = FlatTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = CurrencyUtils.formatInr(itemSale.totalSalePrice),
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(FlatDarkBorder))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tr("Total Bill:", "कुल बिल:"), color = FlatTextSecondary, fontWeight = FontWeight.SemiBold)
                        Text(CurrencyUtils.formatInr(bill.totalSalePrice), fontWeight = FontWeight.Bold, color = FlatTextPrimary)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tr("Paid Amount:", "जमा राशि:"), color = FlatTextSecondary)
                        Text(CurrencyUtils.formatInr(bill.paidAmount), fontWeight = FontWeight.Bold, color = LedgerBrandGreen)
                    }
                    if (canViewCustomerFull) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(tr("Udhari Due:", "बाक़ी उधारी:"), color = FlatTextSecondary)
                            Text(CurrencyUtils.formatInr(bill.udhariAmount), fontWeight = FontWeight.Bold, color = if (bill.udhariAmount > BigDecimal.ZERO) LedgerCrimson else FlatTextSecondary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedBillForDetail = null }) {
                    Text(tr("Close", "बंद करें"), color = LedgerBrandGreen, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun PaymentsTab(
    payments: List<PaymentEntry>,
    customers: List<Customer>
) {
    var selectedFilter by remember { mutableStateOf(RepaymentFilterType.ALL) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val customerMap = remember(customers) { customers.associateBy { it.id } }

    val activePayments = remember(payments) {
        payments.filter { !it.isDeleted }.sortedByDescending { it.date }
    }

    val (todayTotal, todayCount, monthTotal, monthCount) = remember(activePayments) {
        val calNow = java.util.Calendar.getInstance()
        val curYear = calNow.get(java.util.Calendar.YEAR)
        val curMonth = calNow.get(java.util.Calendar.MONTH)
        val curDay = calNow.get(java.util.Calendar.DAY_OF_YEAR)

        var tTotal = BigDecimal.ZERO
        var tCount = 0
        var mTotal = BigDecimal.ZERO
        var mCount = 0

        val cal = java.util.Calendar.getInstance()
        activePayments.forEach { pay ->
            cal.timeInMillis = pay.date
            val y = cal.get(java.util.Calendar.YEAR)
            val m = cal.get(java.util.Calendar.MONTH)
            val d = cal.get(java.util.Calendar.DAY_OF_YEAR)
            if (y == curYear && m == curMonth) {
                mTotal = mTotal.add(pay.amount)
                mCount++
                if (d == curDay) {
                    tTotal = tTotal.add(pay.amount)
                    tCount++
                }
            }
        }
        listOf(tTotal to tCount, mTotal to mCount).let {
            Quadruple(tTotal, tCount, mTotal, mCount)
        }
    }

    val filteredPayments = remember(activePayments, selectedFilter) {
        activePayments.filter { pay ->
            val isUpi = pay.note.contains("upi", ignoreCase = true) ||
                pay.note.contains("gpay", ignoreCase = true) ||
                pay.note.contains("phonepe", ignoreCase = true) ||
                pay.note.contains("paytm", ignoreCase = true) ||
                pay.note.contains("online", ignoreCase = true) ||
                pay.note.contains("bank", ignoreCase = true)
            when (selectedFilter) {
                RepaymentFilterType.ALL -> true
                RepaymentFilterType.CASH -> !isUpi
                RepaymentFilterType.UPI -> isUpi
            }
        }
    }

    val groupedPayments = remember(filteredPayments) {
        filteredPayments.groupBy { formatSectionDateHeader(it.date) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Summary Card: RECEIVED TODAY | THIS MONTH
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 6.dp),
            shape = RoundedCornerShape(18.dp),
            color = FlatDarkCard,
            border = BorderStroke(1.dp, FlatDarkBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr("RECEIVED TODAY", "आज प्राप्त"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = CurrencyUtils.formatInr(todayTotal),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = LedgerBrandGreen,
                        modifier = Modifier.testTag("repayments_received_today_text")
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$todayCount ${if (todayCount == 1) tr("payment", "भुगतान") else tr("payments", "भुगतान")}",
                        fontSize = 12.sp,
                        color = FlatTextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(48.dp)
                        .background(FlatDarkBorder)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr("THIS MONTH", "इस महीने"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = CurrencyUtils.formatInr(monthTotal),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = LedgerBrandGreen,
                        modifier = Modifier.testTag("repayments_this_month_text")
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$monthCount ${if (monthCount == 1) tr("payment", "भुगतान") else tr("payments", "भुगतान")}",
                        fontSize = 12.sp,
                        color = FlatTextSecondary
                    )
                }
            }
        }

        // Filter Pills: All | Cash | UPI
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val filterOptions = listOf(
                RepaymentFilterType.ALL to tr("All", "सभी"),
                RepaymentFilterType.CASH to tr("Cash", "नकद"),
                RepaymentFilterType.UPI to tr("UPI", "यूपीआई")
            )
            filterOptions.forEach { (type, label) ->
                val isSelected = selectedFilter == type
                Surface(
                    onClick = { selectedFilter = type },
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) LedgerBrandGreen else FlatDarkCard,
                    border = if (isSelected) null else BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier.testTag("repayments_filter_${type.name.lowercase()}")
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else FlatTextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                    )
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (filteredPayments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(tr("No repayments recorded yet", "अभी कोई वसूली दर्ज नहीं है"), color = FlatTextMuted)
                    }
                }
            } else {
                groupedPayments.forEach { (sectionHeader, sectionPayments) ->
                    item(key = "pay_header_$sectionHeader") {
                        Text(
                            text = sectionHeader,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.6.dp.value.sp,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp, start = 2.dp)
                        )
                    }
                    items(sectionPayments, key = { it.id }) { pay ->
                        val cust = customerMap[pay.customerId]
                        val custName = cust?.name ?: tr("Customer", "ग्राहक")
                        val initials = remember(custName) { customerInitials(custName) }
                        val isUpi = pay.note.contains("upi", ignoreCase = true) ||
                            pay.note.contains("gpay", ignoreCase = true) ||
                            pay.note.contains("phonepe", ignoreCase = true) ||
                            pay.note.contains("paytm", ignoreCase = true) ||
                            pay.note.contains("online", ignoreCase = true) ||
                            pay.note.contains("bank", ignoreCase = true)
                        val modeLabel = if (isUpi) "UPI" else "Cash"
                        val currentBalance = cust?.totalUdhari ?: BigDecimal.ZERO

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("repayment_item_${pay.id}"),
                            shape = RoundedCornerShape(18.dp),
                            color = FlatDarkCard,
                            border = BorderStroke(1.dp, FlatDarkBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left: 44.dp Circular Initials Avatar
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(LedgerAvatarBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initials,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp,
                                        color = LedgerAvatarText
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Middle: Customer Name + "Cash · 10:42 AM"
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = custName,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FlatTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$modeLabel · ${timeFormat.format(Date(pay.date))}",
                                        fontSize = 13.sp,
                                        color = FlatTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // Right: +₹1,000 + Unhidden Balance or Cleared pill (NO hidden amounts!)
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "+${CurrencyUtils.formatInr(pay.amount)}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LedgerBrandGreen
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (currentBalance <= BigDecimal.ZERO) {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = LedgerMintBg
                                        ) {
                                            Text(
                                                text = tr("Cleared", "क्लियर"),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = LedgerBrandGreen,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "${tr("Balance", "बाक़ी")} ${CurrencyUtils.formatInr(currentBalance)}",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = FlatTextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun RecordPaymentDialog(
    customers: List<Customer>,
    currentUserId: String,
    onDismiss: () -> Unit,
    onPaymentRecorded: (customerId: String, amount: BigDecimal, note: String) -> Unit
) {
    var selectedCustomer by remember { mutableStateOf(if (customers.size == 1) customers.first() else null) }
    var amountInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var customerDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FlatDarkCard,
        title = { Text("Record Udhari Repayment", color = FlatTextPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text("Select Customer", style = MaterialTheme.typography.labelSmall, color = FlatTextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Box {
                    OutlinedButton(
                        onClick = { customerDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, FlatDarkInputBorder)
                    ) {
                        Text(
                            selectedCustomer?.name ?: "Select Customer",
                            color = if (selectedCustomer != null) FlatTextPrimary else FlatTextMuted
                        )
                    }
                    DropdownMenu(
                        expanded = customerDropdownExpanded,
                        onDismissRequest = { customerDropdownExpanded = false }
                    ) {
                        customers.forEach { c ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(c.name)
                                        Text(CurrencyUtils.formatInr(c.totalUdhari), color = CRedUdhari)
                                    }
                                },
                                onClick = {
                                    selectedCustomer = c
                                    customerDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                if (selectedCustomer != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Outstanding: ${CurrencyUtils.formatInr(selectedCustomer!!.totalUdhari)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CRedUdhari,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("Repayment Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = FlatDarkInput,
                        unfocusedContainerColor = FlatDarkInput,
                        focusedBorderColor = CGreen,
                        unfocusedBorderColor = FlatDarkInputBorder,
                        focusedTextColor = FlatTextPrimary,
                        unfocusedTextColor = FlatTextPrimary,
                        focusedLabelColor = CGreen,
                        unfocusedLabelColor = FlatTextSecondary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("repayment_amount_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Note (e.g. Cash, GPay, Bank)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = FlatDarkInput,
                        unfocusedContainerColor = FlatDarkInput,
                        focusedBorderColor = CGreen,
                        unfocusedBorderColor = FlatDarkInputBorder,
                        focusedTextColor = FlatTextPrimary,
                        unfocusedTextColor = FlatTextPrimary,
                        focusedLabelColor = CGreen,
                        unfocusedLabelColor = FlatTextSecondary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedCustomer == null) {
                        error = "Please choose a customer"
                        return@Button
                    }
                    val amt = try {
                        BigDecimal(amountInput.trim())
                    } catch (e: Exception) {
                        BigDecimal.ZERO
                    }
                    if (amt <= BigDecimal.ZERO) {
                        error = "Please enter a valid amount"
                        return@Button
                    }
                    onPaymentRecorded(selectedCustomer!!.id, amt, noteInput.trim())
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CGreen,
                    contentColor = Color(0xFF00220F)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("submit_repayment_btn")
            ) {
                Text("Record Payment", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, FlatDarkInputBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FlatTextSecondary)
            ) {
                Text("Cancel")
            }
        }
    )
}
