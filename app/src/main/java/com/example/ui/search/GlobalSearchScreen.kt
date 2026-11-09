package com.example.ui.search

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.CattleFeedRepository
import com.example.model.Customer
import com.example.model.GroupedSaleBill
import com.example.model.Product
import com.example.model.PurchaseEntry
import com.example.model.PurchaseInvoice
import com.example.model.SupplierPayment
import com.example.model.User
import com.example.model.groupSalesIntoBills
import com.example.util.CurrencyUtils
import com.example.util.ShopAnalytics
import com.example.util.tr
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class SearchFilterTab {
    ALL,
    CUSTOMERS,
    BILLS,
    ITEMS,
    SUPPLIERS
}

enum class QuickFindPreset {
    NONE,
    OVERDUE_CUSTOMERS,
    LOW_STOCK_ITEMS,
    TODAYS_BILLS,
    UNPAID_SUPPLIER_BILLS
}

data class RecentSearchEntry(
    val query: String,
    val categoryLabel: String
)

private class RecentSearchStore(context: Context) {
    private val prefs = context.getSharedPreferences("khatawise_recent_searches", Context.MODE_PRIVATE)

    fun getRecentSearches(): List<RecentSearchEntry> {
        val raw = prefs.getString("recent_list_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val q = obj.optString("query").trim()
                    val cat = obj.optString("categoryLabel").trim()
                    if (q.isNotEmpty()) {
                        add(RecentSearchEntry(q, cat.ifEmpty { "Search" }))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addRecentSearch(query: String, categoryLabel: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val existing = getRecentSearches().filterNot { it.query.equals(trimmed, ignoreCase = true) }
        val updated = listOf(RecentSearchEntry(trimmed, categoryLabel)) + existing.take(7)
        val arr = JSONArray()
        updated.forEach { item ->
            val obj = JSONObject()
            obj.put("query", item.query)
            obj.put("categoryLabel", item.categoryLabel)
            arr.put(obj)
        }
        prefs.edit().putString("recent_list_json", arr.toString()).apply()
    }

    fun clearAll() {
        prefs.edit().remove("recent_list_json").apply()
    }
}

private data class SearchSupplierSummary(
    val name: String,
    val locationOrPhone: String,
    val itemsSummary: String,
    val balancePayable: BigDecimal
)

// Palette matching the Search PDF design
private val SearchPageBg = Color(0xFFF6F5F0)
private val SearchCardBg = Color(0xFFFFFFFF)
private val SearchBorder = Color(0xFFE5E4DC)
private val SearchGreen = Color(0xFF15803D)
private val SearchGreenSoft = Color(0xFFDCFCE7)
private val SearchRedText = Color(0xFF991B1B)
private val SearchRedSoft = Color(0xFFFEE2E2)
private val SearchBlueText = Color(0xFF1E40AF)
private val SearchBlueSoft = Color(0xFFDBEAFE)
private val SearchAmberText = Color(0xFFB45309)
private val SearchAmberSoft = Color(0xFFFEF3C7)
private val SearchNavyText = Color(0xFF111827)
private val SearchSubtleText = Color(0xFF6B7280)
private val SearchChipUnselectedBg = Color(0xFFEFECE6)
private val SearchAvatarBg = Color(0xFFE2E8F0)
private val SearchAvatarText = Color(0xFF1E293B)

@Composable
fun GlobalSearchScreen(
    repository: CattleFeedRepository,
    onCancel: () -> Unit,
    onSelectCustomer: (String) -> Unit,
    onNavigateToBills: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToSuppliers: () -> Unit,
    onNavigateToCollections: () -> Unit = {},
    onNavigateToStaff: () -> Unit = {}
) {
    BackHandler { onCancel() }

    val context = LocalContext.current
    val recentStore = remember(context) { RecentSearchStore(context) }
    var recentVersion by remember { mutableIntStateOf(0) }
    val recentSearches = remember(recentVersion) { recentStore.getRecentSearches() }

    val customers by repository.allCustomers.collectAsState(initial = emptyList())
    val products by repository.allProducts.collectAsState(initial = emptyList())
    val sales by repository.allSales.collectAsState(initial = emptyList())
    val purchaseInvoices by repository.allPurchaseInvoices.collectAsState(initial = emptyList())
    val purchases by repository.allPurchases.collectAsState(initial = emptyList())
    val supplierPayments by repository.allSupplierPayments.collectAsState(initial = emptyList())
    val users by repository.allUsers.collectAsState(initial = emptyList())

    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(SearchFilterTab.ALL) }
    var activeQuickFind by remember { mutableStateOf(QuickFindPreset.NONE) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val activeCustomers = remember(customers) { customers.filter { !it.isDeleted } }
    val customersById = remember(activeCustomers) { activeCustomers.associateBy { it.id } }
    val activeProducts = remember(products) { products.filter { !it.isDeleted } }
    val productsById = remember(activeProducts) { activeProducts.associateBy { it.id } }
    val usersById = remember(users) { users.associateBy { it.id } }

    val allGroupedBills = remember(sales) {
        groupSalesIntoBills(sales.filter { !it.isDeleted && it.billStatus != "CANCELLED" }, descending = true)
    }

    val allSuppliers = remember(purchaseInvoices, purchases, supplierPayments, productsById) {
        buildSupplierSearchSummaries(
            invoices = purchaseInvoices.filter { !it.isDeleted },
            purchases = purchases.filter { !it.isDeleted },
            payments = supplierPayments.filter { !it.isDeleted },
            productsById = productsById
        )
    }

    val startOfToday = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val trimmedQuery = query.trim()
    val cleanBillQuery = trimmedQuery.removePrefix("#").trim()

    // Filtered Customers
    val matchingCustomers = remember(activeCustomers, trimmedQuery, activeQuickFind, startOfToday) {
        when {
            activeQuickFind == QuickFindPreset.OVERDUE_CUSTOMERS -> {
                activeCustomers.filter {
                    it.totalUdhari > BigDecimal.ZERO &&
                        it.promiseDate != null &&
                        it.promiseDate!! < startOfToday
                }
            }
            trimmedQuery.isNotBlank() -> {
                activeCustomers.filter { c ->
                    c.name.contains(trimmedQuery, ignoreCase = true) ||
                        c.village.contains(trimmedQuery, ignoreCase = true) ||
                        c.phone.replace(" ", "").contains(trimmedQuery.replace(" ", ""), ignoreCase = true)
                }
            }
            else -> emptyList()
        }
    }

    // Filtered Bills
    val matchingBills = remember(allGroupedBills, customersById, productsById, usersById, trimmedQuery, cleanBillQuery, activeQuickFind, startOfToday) {
        when {
            activeQuickFind == QuickFindPreset.TODAYS_BILLS -> {
                allGroupedBills.filter { it.date >= startOfToday }
            }
            trimmedQuery.isNotBlank() -> {
                allGroupedBills.filter { bill ->
                    val cust = customersById[bill.customerId]
                    val staff = usersById[bill.soldByUserId]
                    val matchesBillNo = cleanBillQuery.isNotEmpty() && bill.billNumber.contains(cleanBillQuery, ignoreCase = true)
                    val matchesCustomer = cust != null && (
                        cust.name.contains(trimmedQuery, ignoreCase = true) ||
                            cust.phone.contains(trimmedQuery, ignoreCase = true) ||
                            cust.village.contains(trimmedQuery, ignoreCase = true)
                        )
                    val matchesItem = bill.items.any { line ->
                        productsById[line.productId]?.feedName?.contains(trimmedQuery, ignoreCase = true) == true
                    }
                    val matchesStaff = staff?.name?.contains(trimmedQuery, ignoreCase = true) == true
                    matchesBillNo || matchesCustomer || matchesItem || matchesStaff
                }
            }
            else -> emptyList()
        }
    }

    // Filtered Suppliers
    val matchingSuppliers = remember(allSuppliers, trimmedQuery, activeQuickFind) {
        when {
            activeQuickFind == QuickFindPreset.UNPAID_SUPPLIER_BILLS -> {
                allSuppliers.filter { it.balancePayable > BigDecimal.ZERO }
            }
            trimmedQuery.isNotBlank() -> {
                allSuppliers.filter { sup ->
                    sup.name.contains(trimmedQuery, ignoreCase = true) ||
                        sup.locationOrPhone.contains(trimmedQuery, ignoreCase = true) ||
                        sup.itemsSummary.contains(trimmedQuery, ignoreCase = true)
                }
            }
            else -> emptyList()
        }
    }

    // Filtered Items
    val matchingItems = remember(activeProducts, sales, trimmedQuery, activeQuickFind) {
        when {
            activeQuickFind == QuickFindPreset.LOW_STOCK_ITEMS -> {
                val reorderIds = try {
                    ShopAnalytics.computeReorderSuggestions(activeProducts, sales).map { it.product.id }.toSet()
                } catch (_: Throwable) {
                    emptySet()
                }
                activeProducts.filter { p ->
                    p.id in reorderIds || p.currentStockKg <= p.kgPerBag.multiply(BigDecimal("3"))
                }
            }
            trimmedQuery.isNotBlank() -> {
                activeProducts.filter { p ->
                    p.feedName.contains(trimmedQuery, ignoreCase = true)
                }
            }
            else -> emptyList()
        }
    }

    // Filtered Staff (when searching by staff name in All tab)
    val matchingStaff = remember(users, trimmedQuery) {
        if (trimmedQuery.isBlank()) {
            emptyList()
        } else {
            users.filter { u ->
                u.name.contains(trimmedQuery, ignoreCase = true) ||
                    u.email.contains(trimmedQuery, ignoreCase = true)
            }
        }
    }

    val billNumberCatLabel = tr("Bill number", "बिल नंबर")
    val phoneNumberCatLabel = tr("Phone number", "फ़ोन नंबर")
    val itemCatLabel = tr("Item", "आइटम")
    val supplierCatLabel = tr("Supplier", "सप्लायर")
    val customerSearchCatLabel = tr("Customer search", "ग्राहक खोज")

    val inferSearchCategoryLabel: (String) -> String = { q ->
        val clean = q.trim()
        when {
            clean.startsWith("#") -> billNumberCatLabel
            clean.all { it.isDigit() || it == ' ' || it == '+' } && clean.count { it.isDigit() } >= 4 ->
                phoneNumberCatLabel
            matchingItems.any { it.feedName.contains(clean, ignoreCase = true) } &&
                matchingCustomers.isEmpty() -> itemCatLabel
            matchingSuppliers.any { it.name.contains(clean, ignoreCase = true) } &&
                matchingCustomers.isEmpty() -> supplierCatLabel
            else -> customerSearchCatLabel
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SearchPageBg)
            .testTag("global_search_screen")
    ) {
        // -------------------------------------------------------------
        // 1. TOP SEARCH INPUT BAR + CANCEL
        // -------------------------------------------------------------
        Surface(
            color = SearchPageBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = SearchCardBg,
                    border = BorderStroke(2.dp, SearchGreen)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = SearchGreen,
                            modifier = Modifier.size(22.dp)
                        )

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                    text = tr(
                                        "Search customers, bills, items…",
                                        "ग्राहक, बिल, आइटम खोजें…"
                                    ),
                                    fontSize = 15.5.sp,
                                    color = Color(0xFF9CA3AF),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = {
                                    query = it
                                    if (it.isNotBlank()) {
                                        activeQuickFind = QuickFindPreset.NONE
                                    }
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SearchNavyText
                                ),
                                cursorBrush = SolidColor(SearchGreen),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        if (trimmedQuery.isNotEmpty()) {
                                            recentStore.addRecentSearch(
                                                trimmedQuery,
                                                inferSearchCategoryLabel(trimmedQuery)
                                            )
                                            recentVersion++
                                        }
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .testTag("global_search_input")
                            )
                        }

                        if (query.isNotEmpty() || activeQuickFind != QuickFindPreset.NONE) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = tr("Clear", "साफ़ करें"),
                                tint = SearchSubtleText,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        query = ""
                                        activeQuickFind = QuickFindPreset.NONE
                                        selectedTab = SearchFilterTab.ALL
                                    }
                                    .testTag("global_search_clear_query_btn")
                            )
                        }
                    }
                }

                Text(
                    text = tr("Cancel", "रद्द करें"),
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = SearchGreen,
                    modifier = Modifier
                        .clickable { onCancel() }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                        .testTag("global_search_cancel_btn")
                )
            }
        }

        // -------------------------------------------------------------
        // 2. FILTER CHIPS ROW: All | Customers | Bills | Items | Suppliers
        // -------------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                SearchFilterTab.ALL to tr("All", "सभी"),
                SearchFilterTab.CUSTOMERS to tr("Customers", "ग्राहक"),
                SearchFilterTab.BILLS to tr("Bills", "बिल"),
                SearchFilterTab.ITEMS to tr("Items", "आइटम"),
                SearchFilterTab.SUPPLIERS to tr("Suppliers", "सप्लायर")
            ).forEach { (tab, label) ->
                val selected = selectedTab == tab
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { selectedTab = tab }
                        .testTag("search_tab_${tab.name.lowercase()}"),
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) SearchGreen else SearchChipUnselectedBg
                ) {
                    Text(
                        text = label,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else SearchNavyText,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 3. CONTENT BODY: PAGE 1 (EMPTY QUERY) vs PAGE 2 (ACTIVE RESULTS)
        // -------------------------------------------------------------
        val isShowingResults = trimmedQuery.isNotEmpty() || activeQuickFind != QuickFindPreset.NONE

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (!isShowingResults) {
                // =========================================================
                // PAGE 1: RECENT SEARCHES + QUICK FIND + YOU CAN SEARCH BY
                // =========================================================
                if (recentSearches.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = tr("RECENT SEARCHES", "हाल की खोजें"),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp,
                                color = SearchSubtleText
                            )
                            Text(
                                text = tr("Clear", "साफ़ करें"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = SearchGreen,
                                modifier = Modifier
                                    .clickable {
                                        recentStore.clearAll()
                                        recentVersion++
                                    }
                                    .padding(vertical = 2.dp, horizontal = 4.dp)
                                    .testTag("clear_recent_searches_btn")
                            )
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = SearchCardBg),
                            border = BorderStroke(1.dp, SearchBorder)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                recentSearches.forEachIndexed { index, item ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            color = SearchBorder,
                                            modifier = Modifier.padding(horizontal = 14.dp)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { query = item.query }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                modifier = Modifier.size(36.dp),
                                                shape = CircleShape,
                                                color = Color(0xFFF3F4F6)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.Schedule,
                                                        contentDescription = null,
                                                        tint = SearchSubtleText,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Column {
                                                Text(
                                                    text = item.query,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SearchNavyText
                                                )
                                                Text(
                                                    text = item.categoryLabel,
                                                    fontSize = 12.5.sp,
                                                    color = SearchSubtleText
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Default.NorthEast,
                                            contentDescription = null,
                                            tint = SearchSubtleText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // QUICK FIND (2×2 Grid)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = tr("QUICK FIND", "त्वरित खोज (QUICK FIND)"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp,
                        color = SearchSubtleText
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickFindTileCard(
                            title = tr("Overdue customers", "ओवरड्यू ग्राहक"),
                            subtitle = tr("Promise date passed", "वादे की तारीख बीत गई"),
                            icon = Icons.Outlined.CreditCard,
                            iconTint = Color(0xFFB91C1C),
                            iconBg = SearchRedSoft,
                            testTag = "quick_find_overdue_customers",
                            onClick = {
                                activeQuickFind = QuickFindPreset.OVERDUE_CUSTOMERS
                                selectedTab = SearchFilterTab.CUSTOMERS
                            },
                            modifier = Modifier.weight(1f)
                        )
                        QuickFindTileCard(
                            title = tr("Low stock items", "कम स्टॉक आइटम"),
                            subtitle = tr("Below reorder level", "रीऑर्डर स्तर से नीचे"),
                            icon = Icons.Default.Inventory2,
                            iconTint = SearchAmberText,
                            iconBg = SearchAmberSoft,
                            testTag = "quick_find_low_stock",
                            onClick = {
                                activeQuickFind = QuickFindPreset.LOW_STOCK_ITEMS
                                selectedTab = SearchFilterTab.ITEMS
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickFindTileCard(
                            title = tr("Today's bills", "आज के बिल"),
                            subtitle = tr("Sales and returns", "बिक्री और वापसी"),
                            icon = Icons.Default.Receipt,
                            iconTint = SearchGreen,
                            iconBg = SearchGreenSoft,
                            testTag = "quick_find_todays_bills",
                            onClick = {
                                activeQuickFind = QuickFindPreset.TODAYS_BILLS
                                selectedTab = SearchFilterTab.BILLS
                            },
                            modifier = Modifier.weight(1f)
                        )
                        QuickFindTileCard(
                            title = tr("Unpaid supplier bills", "बकाया सप्लायर बिल"),
                            subtitle = tr("Part paid or unpaid", "आंशिक या बकाया"),
                            icon = Icons.Default.LocalShipping,
                            iconTint = Color(0xFF1D4ED8),
                            iconBg = SearchBlueSoft,
                            testTag = "quick_find_unpaid_suppliers",
                            onClick = {
                                activeQuickFind = QuickFindPreset.UNPAID_SUPPLIER_BILLS
                                selectedTab = SearchFilterTab.SUPPLIERS
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // "You can search by" helper card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFEEF0F8)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = tr("You can search by", "आप इनसे खोज सकते हैं"),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SearchNavyText
                        )
                        Text(
                            text = tr(
                                "Name, village or phone · bill number · item name · supplier · staff name",
                                "नाम, गाँव या फ़ोन · बिल नंबर · आइटम का नाम · सप्लायर · स्टाफ का नाम"
                            ),
                            fontSize = 13.sp,
                            color = Color(0xFF4B5563),
                            lineHeight = 19.sp
                        )
                    }
                }
            } else {
                // =========================================================
                // PAGE 2: ACTIVE SEARCH RESULTS
                // =========================================================
                val showCustomersSection = selectedTab == SearchFilterTab.ALL || selectedTab == SearchFilterTab.CUSTOMERS
                val showBillsSection = selectedTab == SearchFilterTab.ALL || selectedTab == SearchFilterTab.BILLS
                val showSuppliersSection = selectedTab == SearchFilterTab.ALL || selectedTab == SearchFilterTab.SUPPLIERS
                val showItemsSection = selectedTab == SearchFilterTab.ALL || selectedTab == SearchFilterTab.ITEMS

                // 1. CUSTOMERS SECTION
                if (showCustomersSection) {
                    val displayedCustomers = if (selectedTab == SearchFilterTab.ALL) {
                        matchingCustomers.take(3)
                    } else {
                        matchingCustomers
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchSectionHeader(
                            title = tr("CUSTOMERS", "ग्राहक"),
                            seeAllCount = if (matchingCustomers.isNotEmpty() && selectedTab == SearchFilterTab.ALL) {
                                matchingCustomers.size
                            } else null,
                            onSeeAllClick = { selectedTab = SearchFilterTab.CUSTOMERS }
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = SearchCardBg),
                            border = BorderStroke(1.dp, SearchBorder)
                        ) {
                            if (displayedCustomers.isEmpty()) {
                                EmptySectionMatchRow(
                                    message = if (activeQuickFind == QuickFindPreset.OVERDUE_CUSTOMERS) {
                                        tr("No overdue customers right now.", "अभी कोई ओवरड्यू ग्राहक नहीं है।")
                                    } else {
                                        "No customers match “$trimmedQuery”."
                                    }
                                )
                            } else {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    displayedCustomers.forEachIndexed { idx, cust ->
                                        if (idx > 0) {
                                            HorizontalDivider(
                                                color = SearchBorder,
                                                modifier = Modifier.padding(horizontal = 14.dp)
                                            )
                                        }
                                        CustomerSearchResultRow(
                                            customer = cust,
                                            highlightQuery = trimmedQuery,
                                            startOfToday = startOfToday,
                                            onClick = {
                                                if (trimmedQuery.isNotEmpty()) {
                                                    recentStore.addRecentSearch(trimmedQuery, customerSearchCatLabel)
                                                    recentVersion++
                                                }
                                                onSelectCustomer(cust.id)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. BILLS SECTION
                if (showBillsSection) {
                    val displayedBills = if (selectedTab == SearchFilterTab.ALL) {
                        matchingBills.take(3)
                    } else {
                        matchingBills
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchSectionHeader(
                            title = tr("BILLS", "बिल"),
                            seeAllCount = if (matchingBills.isNotEmpty() && selectedTab == SearchFilterTab.ALL) {
                                matchingBills.size
                            } else null,
                            onSeeAllClick = { selectedTab = SearchFilterTab.BILLS }
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = SearchCardBg),
                            border = BorderStroke(1.dp, SearchBorder)
                        ) {
                            if (displayedBills.isEmpty()) {
                                EmptySectionMatchRow(
                                    message = if (activeQuickFind == QuickFindPreset.TODAYS_BILLS) {
                                        tr("No bills recorded today yet.", "आज अभी तक कोई बिल दर्ज नहीं हुआ है।")
                                    } else {
                                        "No bills match “$trimmedQuery”."
                                    }
                                )
                            } else {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    displayedBills.forEachIndexed { idx, bill ->
                                        if (idx > 0) {
                                            HorizontalDivider(
                                                color = SearchBorder,
                                                modifier = Modifier.padding(horizontal = 14.dp)
                                            )
                                        }
                                        val custName = customersById[bill.customerId]?.name ?: tr("Walk-in Customer", "नकद ग्राहक")
                                        val firstProd = productsById[bill.primarySale.productId]
                                        val itemSummary = buildBillItemSummary(bill, firstProd)
                                        BillSearchResultRow(
                                            bill = bill,
                                            customerName = custName,
                                            itemSummary = itemSummary,
                                            highlightQuery = trimmedQuery,
                                            startOfToday = startOfToday,
                                            onClick = {
                                                if (trimmedQuery.isNotEmpty()) {
                                                    recentStore.addRecentSearch(
                                                        trimmedQuery,
                                                        if (trimmedQuery.startsWith("#")) billNumberCatLabel else customerSearchCatLabel
                                                    )
                                                    recentVersion++
                                                }
                                                onNavigateToBills()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. SUPPLIERS SECTION
                if (showSuppliersSection) {
                    val displayedSuppliers = if (selectedTab == SearchFilterTab.ALL) {
                        matchingSuppliers.take(3)
                    } else {
                        matchingSuppliers
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchSectionHeader(
                            title = tr("SUPPLIERS", "सप्लायर"),
                            seeAllCount = if (matchingSuppliers.size > 3 && selectedTab == SearchFilterTab.ALL) {
                                matchingSuppliers.size
                            } else null,
                            onSeeAllClick = { selectedTab = SearchFilterTab.SUPPLIERS }
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = SearchCardBg),
                            border = BorderStroke(1.dp, SearchBorder)
                        ) {
                            if (displayedSuppliers.isEmpty()) {
                                EmptySectionMatchRow(
                                    message = if (activeQuickFind == QuickFindPreset.UNPAID_SUPPLIER_BILLS) {
                                        tr("No unpaid supplier bills.", "कोई बकाया सप्लायर बिल नहीं है।")
                                    } else {
                                        "No suppliers match “$trimmedQuery”."
                                    }
                                )
                            } else {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    displayedSuppliers.forEachIndexed { idx, sup ->
                                        if (idx > 0) {
                                            HorizontalDivider(
                                                color = SearchBorder,
                                                modifier = Modifier.padding(horizontal = 14.dp)
                                            )
                                        }
                                        SupplierSearchResultRow(
                                            supplier = sup,
                                            highlightQuery = trimmedQuery,
                                            onClick = {
                                                if (trimmedQuery.isNotEmpty()) {
                                                    recentStore.addRecentSearch(trimmedQuery, supplierCatLabel)
                                                    recentVersion++
                                                }
                                                onNavigateToSuppliers()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. ITEMS SECTION
                if (showItemsSection) {
                    val displayedItems = if (selectedTab == SearchFilterTab.ALL) {
                        matchingItems.take(3)
                    } else {
                        matchingItems
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchSectionHeader(
                            title = tr("ITEMS", "आइटम"),
                            seeAllCount = if (matchingItems.size > 3 && selectedTab == SearchFilterTab.ALL) {
                                matchingItems.size
                            } else null,
                            onSeeAllClick = { selectedTab = SearchFilterTab.ITEMS }
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = SearchCardBg),
                            border = BorderStroke(1.dp, SearchBorder)
                        ) {
                            if (displayedItems.isEmpty()) {
                                EmptySectionMatchRow(
                                    message = if (activeQuickFind == QuickFindPreset.LOW_STOCK_ITEMS) {
                                        tr("All items are well stocked.", "सभी आइटम पर्याप्त स्टॉक में हैं।")
                                    } else {
                                        "No items match “$trimmedQuery”."
                                    }
                                )
                            } else {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    displayedItems.forEachIndexed { idx, prod ->
                                        if (idx > 0) {
                                            HorizontalDivider(
                                                color = SearchBorder,
                                                modifier = Modifier.padding(horizontal = 14.dp)
                                            )
                                        }
                                        ItemSearchResultRow(
                                            product = prod,
                                            highlightQuery = trimmedQuery,
                                            onClick = {
                                                if (trimmedQuery.isNotEmpty()) {
                                                    recentStore.addRecentSearch(trimmedQuery, itemCatLabel)
                                                    recentVersion++
                                                }
                                                onNavigateToInventory()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. STAFF SECTION (When searching in All tab and staff matches)
                if (selectedTab == SearchFilterTab.ALL && matchingStaff.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchSectionHeader(
                            title = tr("STAFF", "स्टाफ"),
                            seeAllCount = null,
                            onSeeAllClick = {}
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = SearchCardBg),
                            border = BorderStroke(1.dp, SearchBorder)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                matchingStaff.forEachIndexed { idx, staff ->
                                    if (idx > 0) {
                                        HorizontalDivider(
                                            color = SearchBorder,
                                            modifier = Modifier.padding(horizontal = 14.dp)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onNavigateToStaff() }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            InitialsAvatarCircle(name = staff.name)
                                            Column {
                                                Text(
                                                    text = buildHighlightedAnnotatedString(staff.name, trimmedQuery),
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SearchNavyText
                                                )
                                                Text(
                                                    text = staff.email.ifBlank { staff.role.name },
                                                    fontSize = 12.5.sp,
                                                    color = SearchSubtleText
                                                )
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = SearchGreenSoft
                                        ) {
                                            Text(
                                                text = staff.role.name.lowercase().replaceFirstChar { it.uppercase() },
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SearchGreen,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun QuickFindTileCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SearchCardBg),
        border = BorderStroke(1.dp, SearchBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(10.dp),
                color = iconBg
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column {
                Text(
                    text = title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SearchNavyText
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = SearchSubtleText
                )
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    seeAllCount: Int?,
    onSeeAllClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp,
            color = SearchSubtleText
        )
        if (seeAllCount != null && seeAllCount > 0) {
            Text(
                text = "${tr("See all", "सभी देखें")} $seeAllCount",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = SearchGreen,
                modifier = Modifier
                    .clickable { onSeeAllClick() }
                    .padding(vertical = 2.dp, horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun EmptySectionMatchRow(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = message,
            fontSize = 14.sp,
            color = SearchSubtleText
        )
    }
}

@Composable
private fun InitialsAvatarCircle(name: String) {
    val initials = remember(name) {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "KW"
        }
    }
    Surface(
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = SearchAvatarBg
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initials,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = SearchAvatarText
            )
        }
    }
}

@Composable
private fun CustomerSearchResultRow(
    customer: Customer,
    highlightQuery: String,
    startOfToday: Long,
    onClick: () -> Unit
) {
    val subLine = buildList {
        if (customer.village.isNotBlank()) add(customer.village)
        if (customer.phone.isNotBlank()) add(customer.phone)
    }.joinToString(" · ").ifBlank { tr("Customer", "ग्राहक") }

    val settledText = tr("Settled", "चुकता")
    // Compute due/overdue/settled badge matching Screenshot 2
    val (badgeLabel, badgeBg, badgeTextColor) = remember(customer, startOfToday, settledText) {
        if (customer.totalUdhari <= BigDecimal.ZERO) {
            Triple(settledText, SearchGreenSoft, SearchGreen)
        } else if (customer.promiseDate != null) {
            val diffDays = ((customer.promiseDate!! - startOfToday) / (1000L * 60 * 60 * 24)).toInt()
            when {
                diffDays < 0 -> Triple("Overdue ${-diffDays} days", SearchRedSoft, SearchRedText)
                diffDays == 0 -> Triple("Due today", SearchRedSoft, SearchRedText)
                diffDays <= 3 -> Triple("Due $diffDays days", SearchRedSoft, SearchRedText)
                else -> Triple("In $diffDays days", SearchBlueSoft, SearchBlueText)
            }
        } else {
            Triple(CurrencyUtils.formatInr(customer.totalUdhari), SearchRedSoft, SearchRedText)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            InitialsAvatarCircle(name = customer.name)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildHighlightedAnnotatedString(customer.name, highlightQuery),
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SearchNavyText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildHighlightedAnnotatedString(subLine, highlightQuery),
                    fontSize = 13.sp,
                    color = SearchSubtleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = badgeBg
        ) {
            Text(
                text = badgeLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = badgeTextColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun BillSearchResultRow(
    bill: GroupedSaleBill,
    customerName: String,
    itemSummary: String,
    highlightQuery: String,
    startOfToday: Long,
    onClick: () -> Unit
) {
    val dateLabel = remember(bill.date, startOfToday) {
        if (bill.date >= startOfToday) {
            "Today, ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(bill.date))}"
        } else {
            SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(bill.date))
        }
    }
    val hasUdhari = bill.udhariAmount > BigDecimal.ZERO
    val titleText = "Bill #${bill.billNumber} · $customerName"
    val subText = "$dateLabel · $itemSummary"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = SearchGreenSoft
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        contentDescription = null,
                        tint = SearchGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildHighlightedAnnotatedString(titleText, highlightQuery),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SearchNavyText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildHighlightedAnnotatedString(subText, highlightQuery),
                    fontSize = 12.5.sp,
                    color = SearchSubtleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (hasUdhari) SearchRedSoft else SearchGreenSoft
        ) {
            Text(
                text = CurrencyUtils.formatInr(bill.totalSalePrice),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (hasUdhari) SearchRedText else SearchGreen,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun SupplierSearchResultRow(
    supplier: SearchSupplierSummary,
    highlightQuery: String,
    onClick: () -> Unit
) {
    val isSettled = supplier.balancePayable <= BigDecimal.ZERO
    val subtitle = buildList {
        if (supplier.locationOrPhone.isNotBlank()) add(supplier.locationOrPhone)
        if (supplier.itemsSummary.isNotBlank()) add(supplier.itemsSummary)
    }.joinToString(" · ").ifBlank { tr("Supplier", "सप्लायर") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            InitialsAvatarCircle(name = supplier.name)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildHighlightedAnnotatedString(supplier.name, highlightQuery),
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SearchNavyText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildHighlightedAnnotatedString(subtitle, highlightQuery),
                    fontSize = 13.sp,
                    color = SearchSubtleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isSettled) SearchGreenSoft else SearchRedSoft
        ) {
            Text(
                text = if (isSettled) tr("Settled", "चुकता") else CurrencyUtils.formatInr(supplier.balancePayable),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSettled) SearchGreen else SearchRedText,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun ItemSearchResultRow(
    product: Product,
    highlightQuery: String,
    onClick: () -> Unit
) {
    val isOut = product.currentStockKg <= BigDecimal.ZERO
    val isLow = !isOut && product.currentStockKg <= product.kgPerBag.multiply(BigDecimal("3"))
    val bagsCount = if (!product.isLooseOnly && product.kgPerBag > BigDecimal.ONE) {
        product.currentStockKg.divide(product.kgPerBag, 0, RoundingMode.FLOOR).toInt()
    } else 0

    val stockText = if (!product.isLooseOnly && product.kgPerBag > BigDecimal.ONE) {
        "$bagsCount bags (${CurrencyUtils.formatWeightKg(product.currentStockKg)}) · ${CurrencyUtils.formatInr(product.bagSellingPrice)}/bag"
    } else {
        "${CurrencyUtils.formatWeightKg(product.currentStockKg)} · ${CurrencyUtils.formatInr(product.effectiveLooseRatePerKg)}/kg"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = SearchAmberSoft
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = SearchAmberText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildHighlightedAnnotatedString(product.feedName, highlightQuery),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SearchNavyText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stockText,
                    fontSize = 12.5.sp,
                    color = SearchSubtleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        val (statusText, statusBg, statusFg) = when {
            isOut -> Triple(tr("Out of stock", "स्टॉक ख़त्म"), SearchRedSoft, SearchRedText)
            isLow -> Triple(tr("Low stock", "कम स्टॉक"), SearchAmberSoft, SearchAmberText)
            else -> Triple(tr("In stock", "उपलब्ध"), SearchGreenSoft, SearchGreen)
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = statusBg
        ) {
            Text(
                text = statusText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = statusFg,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

private fun buildHighlightedAnnotatedString(
    text: String,
    rawQuery: String
): AnnotatedString {
    val needle = rawQuery.trim().removePrefix("#")
    if (needle.isEmpty() || text.isEmpty()) {
        return AnnotatedString(text)
    }
    return buildAnnotatedString {
        append(text)
        var startIndex = text.indexOf(needle, ignoreCase = true)
        while (startIndex >= 0) {
            val endIndex = (startIndex + needle.length).coerceAtMost(text.length)
            addStyle(
                style = SpanStyle(
                    background = SearchGreenSoft,
                    color = SearchGreen,
                    fontWeight = FontWeight.ExtraBold
                ),
                start = startIndex,
                end = endIndex
            )
            startIndex = text.indexOf(needle, startIndex = endIndex, ignoreCase = true)
        }
    }
}

private fun buildBillItemSummary(
    bill: GroupedSaleBill,
    firstProduct: Product?
): String {
    val prodName = firstProduct?.feedName ?: "Item"
    val kgPerBag = firstProduct?.kgPerBag ?: BigDecimal("50")
    val firstQty = bill.primarySale.quantityKg
    val qtyLabel = if (firstProduct != null && !firstProduct.isLooseOnly && kgPerBag > BigDecimal.ONE &&
        firstQty.remainder(kgPerBag).compareTo(BigDecimal.ZERO) == 0
    ) {
        val bags = firstQty.divide(kgPerBag, 0, RoundingMode.FLOOR).toInt().coerceAtLeast(1)
        "$bags ${if (bags == 1) "bag" else "bags"}"
    } else {
        CurrencyUtils.formatWeightKg(firstQty)
    }
    val extra = if (bill.extraItemCount > 0) " +${bill.extraItemCount}" else ""
    return "$prodName × $qtyLabel$extra"
}

private fun buildSupplierSearchSummaries(
    invoices: List<PurchaseInvoice>,
    purchases: List<PurchaseEntry>,
    payments: List<SupplierPayment>,
    productsById: Map<String, Product>
): List<SearchSupplierSummary> {
    val supplierNames = linkedSetOf<String>()
    invoices.forEach { if (it.wholesalerName.isNotBlank()) supplierNames.add(it.wholesalerName.trim()) }
    purchases.forEach { if (it.wholesalerName.isNotBlank()) supplierNames.add(it.wholesalerName.trim()) }
    payments.forEach { if (it.wholesalerName.isNotBlank()) supplierNames.add(it.wholesalerName.trim()) }

    return supplierNames.map { name ->
        val supInvoices = invoices.filter { it.wholesalerName.equals(name, ignoreCase = true) }
        val supPurchases = purchases.filter { it.wholesalerName.equals(name, ignoreCase = true) }
        val supPayments = payments.filter { it.wholesalerName.equals(name, ignoreCase = true) }

        val phoneOrNotes = supInvoices.firstNotNullOfOrNull {
            it.wholesalerPhone.ifBlank { it.notes }
        }.orEmpty()

        val itemNames = linkedSetOf<String>()
        supInvoices.forEach { inv ->
            inv.getItems().forEach { item ->
                if (item.feedName.isNotBlank()) itemNames.add(item.feedName)
            }
        }
        supPurchases.forEach { pur ->
            productsById[pur.productId]?.feedName?.let { itemNames.add(it) }
        }

        val invoiceBalance = supInvoices.fold(BigDecimal.ZERO) { acc, inv -> acc.add(inv.balanceAmount) }
        val extraPayments = supPayments.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.amount) }
        val netBalance = invoiceBalance.subtract(extraPayments).max(BigDecimal.ZERO)

        SearchSupplierSummary(
            name = name,
            locationOrPhone = phoneOrNotes,
            itemsSummary = itemNames.take(3).joinToString(", "),
            balancePayable = netBalance
        )
    }
}
