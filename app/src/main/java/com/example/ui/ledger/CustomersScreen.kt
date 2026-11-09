package com.example.ui.ledger

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.CattleFeedRepository
import com.example.model.Customer
import com.example.model.CustomerStatusChip
import com.example.model.Permission
import com.example.model.User
import com.example.model.can
import com.example.model.getCustomerStatusChip
import com.example.ui.home.QuickAddCustomerDialog
import com.example.ui.theme.CAmber
import com.example.ui.theme.CAmberBg
import com.example.ui.theme.CGreen
import com.example.ui.theme.CRedUdhari
import com.example.ui.theme.CRedUdhariBg
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatDarkInput
import com.example.ui.theme.FlatDarkInputBorder
import com.example.ui.theme.FlatTextMuted
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import com.example.util.CurrencyUtils
import com.example.util.tr
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CustomersScreen(
    repository: CattleFeedRepository,
    currentUser: User?,
    onCustomerClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val customers by repository.allCustomers.collectAsState(initial = emptyList())
    val settings by repository.settingsFlow.collectAsState(initial = null)

    val canViewFull = can(currentUser?.role, Permission.VIEW_CUSTOMER_FULL)
    val canAddCustomer = can(currentUser?.role, Permission.ADD_CUSTOMER)

    var searchQuery by remember { mutableStateOf("") }
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var showImportCustomersFlow by remember { mutableStateOf(false) }

    if (showAddCustomerDialog) {
        AddOrEditCustomerScreen(
            existingCustomer = null,
            currentUserId = currentUser?.id.orEmpty(),
            currentUserRole = currentUser?.role,
            onCancel = { showAddCustomerDialog = false },
            onSaveCustomer = { newCust ->
                scope.launch {
                    repository.saveCustomer(newCust, actorRole = currentUser?.role)
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

    BackHandler { onBack() }

    val filteredSortedCustomers = remember(customers, searchQuery) {
        customers
            .filter { c ->
                searchQuery.isBlank() ||
                    c.name.contains(searchQuery, ignoreCase = true) ||
                    c.phone.contains(searchQuery, ignoreCase = true) ||
                    c.village.contains(searchQuery, ignoreCase = true)
            }
            .sortedBy { it.name.trim().lowercase() }
    }

    val groupedByLetter = remember(filteredSortedCustomers) {
        filteredSortedCustomers.groupBy { c ->
            val firstChar = c.name.trim().firstOrNull()?.uppercaseChar()
            if (firstChar != null && firstChar in 'A'..'Z') firstChar.toString() else "#"
        }.toSortedMap(compareBy { if (it == "#") "ZZZ" else it })
    }

    // Build flat index map for fast-scroll side index
    val letterHeaderIndexMap = remember(groupedByLetter) {
        val map = mutableMapOf<String, Int>()
        var currentIndex = 0
        groupedByLetter.forEach { (letter, list) ->
            map[letter] = currentIndex
            currentIndex += 1 + list.size // 1 for sticky header + items count
        }
        map
    }

    val listState = rememberLazyListState()
    val alphabetIndex = remember { ('A'..'Z').map { it.toString() } + "#" }

    Scaffold(
        containerColor = FlatDarkBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = tr("Customers Directory", "ग्राहक सूची"),
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                        Text(
                            text = "${customers.size} ${tr("total customers", "कुल ग्राहक")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = FlatTextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("customers_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = FlatTextPrimary
                        )
                    }
                },
                actions = {
                    if (canAddCustomer) {
                        Surface(
                            onClick = { showImportCustomersFlow = true },
                            shape = RoundedCornerShape(10.dp),
                            color = FlatDarkCard,
                            border = BorderStroke(1.dp, FlatDarkBorder),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .testTag("customers_import_btn")
                        ) {
                            Text(
                                text = tr("Import", "इम्पोर्ट"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = CGreen,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FlatDarkBg,
                    titleContentColor = FlatTextPrimary
                )
            )
        },
        floatingActionButton = {
            if (canAddCustomer) {
                FloatingActionButton(
                    onClick = { showAddCustomerDialog = true },
                    containerColor = CGreen,
                    contentColor = Color(0xFF00220F),
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp),
                    modifier = Modifier.testTag("customers_add_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = tr("Add Customer", "ग्राहक जोड़ें"))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(FlatDarkBg)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        tr("Search by name, phone or village...", "नाम, फ़ोन या गाँव से खोजें..."),
                        color = FlatTextMuted
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = FlatTextSecondary)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = FlatTextSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = FlatDarkInput,
                    unfocusedContainerColor = FlatDarkInput,
                    focusedBorderColor = CGreen,
                    unfocusedBorderColor = FlatDarkInputBorder,
                    focusedTextColor = FlatTextPrimary,
                    unfocusedTextColor = FlatTextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("customers_search_bar")
            )

            Box(modifier = Modifier.fillMaxSize()) {
                if (filteredSortedCustomers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        if (searchQuery.isNotBlank()) {
                            com.example.ui.components.NoSearchResultsStateCard(
                                query = searchQuery,
                                onClearSearch = { searchQuery = "" }
                            )
                        } else {
                            com.example.ui.components.EmptyCustomersStateCard(
                                onAddCustomer = { showAddCustomerDialog = true },
                                onImportContacts = { showImportCustomersFlow = true }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 28.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groupedByLetter.forEach { (letter, sectionCustomers) ->
                            stickyHeader(key = "header_$letter") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(FlatDarkBg.copy(alpha = 0.95f))
                                        .padding(vertical = 4.dp, horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = letter,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = CGreen
                                    )
                                }
                            }
                            items(sectionCustomers, key = { it.id }) { customer ->
                                DirectoryCustomerRow(
                                    customer = customer,
                                    canViewExactBalance = canViewFull,
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

                // A-Z Fast Scroll Side Index
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(26.dp)
                        .padding(vertical = 8.dp)
                        .testTag("az_fast_scroll_index"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    alphabetIndex.forEach { letter ->
                        val hasEntries = letterHeaderIndexMap.containsKey(letter)
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .clickable(enabled = hasEntries) {
                                    val targetIdx = letterHeaderIndexMap[letter] ?: return@clickable
                                    scope.launch {
                                        listState.scrollToItem(targetIdx)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = letter,
                                fontSize = 10.sp,
                                fontWeight = if (hasEntries) FontWeight.Bold else FontWeight.Normal,
                                color = if (hasEntries) CGreen else FlatTextMuted.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddCustomerDialog) {
        QuickAddCustomerDialog(
            currentUserId = currentUser?.id ?: "",
            currentUserRole = currentUser?.role,
            onDismiss = { showAddCustomerDialog = false },
            onCustomerAdded = { newCust ->
                scope.launch {
                    repository.saveCustomer(newCust, actorRole = currentUser?.role)
                    showAddCustomerDialog = false
                }
            }
        )
    }
}

@Composable
private fun DirectoryCustomerRow(
    customer: Customer,
    canViewExactBalance: Boolean,
    onClick: () -> Unit,
    onCallClick: () -> Unit
) {
    val statusChip = remember(customer) { getCustomerStatusChip(customer) }
    val (chipColor, chipBg) = when (statusChip) {
        CustomerStatusChip.OVER_LIMIT -> Pair(CRedUdhari, CRedUdhariBg)
        CustomerStatusChip.HAS_UDHARI -> Pair(CAmber, CAmberBg)
        CustomerStatusChip.CLEAR -> Pair(CGreen, Color(0xFF133826))
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = FlatDarkCard,
        border = BorderStroke(1.dp, FlatDarkBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("directory_customer_${customer.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(2.dp, chipColor, CircleShape)
                    .background(chipBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = customer.name.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = chipColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FlatTextPrimary
                )
                val subtitle = buildString {
                    if (customer.village.isNotBlank()) append(customer.village)
                    if (customer.phone.isNotBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append(customer.phone)
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = FlatTextSecondary
                    )
                }
            }

            // Right side: Admin/Manager sees balance + status chip; Staff sees ONLY status chip
            Column(horizontalAlignment = Alignment.End) {
                if (canViewExactBalance) {
                    Text(
                        text = CurrencyUtils.formatInr(customer.totalUdhari),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = chipColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = chipBg,
                    border = BorderStroke(1.dp, chipColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = tr(statusChip.labelEn, statusChip.labelHi),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = chipColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (customer.phone.isNotBlank()) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onCallClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call ${customer.name}",
                        tint = CGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
