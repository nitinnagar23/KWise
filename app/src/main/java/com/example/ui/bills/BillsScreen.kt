package com.example.ui.bills

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import com.example.ui.theme.pickerThemedContext
import com.example.ui.theme.kw
import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.CattleFeedRepository
import com.example.model.BillAuditLog
import com.example.model.BillData
import com.example.model.BillItem
import com.example.model.Customer
import com.example.model.GroupedSaleBill
import com.example.model.Product
import com.example.model.PaymentStatus
import com.example.model.Role
import com.example.model.SaleEntry
import com.example.model.User
import com.example.model.buildConsolidatedBillNoMap
import com.example.model.groupSalesIntoBills
import com.example.ui.theme.CAmber
import com.example.ui.theme.CAmberBg
import com.example.ui.theme.CGreen
import com.example.ui.theme.CGreenContainer
import com.example.ui.theme.CRedUdhari
import com.example.ui.theme.CRedUdhariBg
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatTextMuted
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import com.example.util.BillGenerator
import com.example.util.CurrencyUtils
import com.example.util.tr
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val BillsGreen: Color get() = kw(0xFF0E6F43)
private val BillsGreenSoft: Color get() = kw(0xFFDDF3E8)
private val BillsRed: Color get() = kw(0xFFB91C1C)
private val BillsRedSoft: Color get() = kw(0xFFFCE4E4)
private val BillsAmber: Color get() = kw(0xFF8A4B08)
private val BillsAmberSoft: Color get() = kw(0xFFFCEBD2)
private val BillsEditedBg: Color get() = kw(0xFFE8EEF7)
private val BillsEditedText: Color get() = kw(0xFF374151)
private val BillsAvatarBg: Color get() = kw(0xFFE8EEF7)
private val BillsAvatarText: Color get() = kw(0xFF1E3A8A)
private val BillsChipIdleBg: Color get() = kw(0xFFF1F1F7)

@Composable
fun UnifiedBillInspectorOverlay(
    primarySale: SaleEntry,
    repository: CattleFeedRepository,
    currentUser: User?,
    onClose: () -> Unit,
    onOpenAllBills: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sales by repository.allSales.collectAsState(initial = emptyList())
    val customers by repository.allCustomers.collectAsState(initial = emptyList())
    val products by repository.allProducts.collectAsState(initial = emptyList())
    val auditLogs by repository.allBillAuditLogs.collectAsState(initial = emptyList())
    val settings by repository.settingsFlow.collectAsState(initial = null)

    val customerMap = remember(customers) { customers.associateBy { it.id } }
    val productMap = remember(products) { products.associateBy { it.id } }

    var activeSaleId by remember(primarySale.id) { mutableStateOf(primarySale.id) }
    val currentSale = remember(sales, activeSaleId, primarySale) {
        sales.find { it.id == activeSaleId } ?: primarySale
    }

    var saleToEdit by remember { mutableStateOf<SaleEntry?>(null) }
    var saleToReturn by remember { mutableStateOf<SaleEntry?>(null) }
    var saleToCancel by remember { mutableStateOf<SaleEntry?>(null) }
    var billDataToShare by remember { mutableStateOf<BillData?>(null) }

    when {
        saleToReturn != null -> {
            val targetSale = saleToReturn!!
            val siblingItems = remember(targetSale, sales) {
                sales.filter {
                    !it.isDeleted && it.customerId == targetSale.customerId && abs(it.date - targetSale.date) <= 2500L
                }.sortedBy { it.id }.ifEmpty { listOf(targetSale) }
            }
            val consolidatedNoMap = remember(sales) { buildConsolidatedBillNoMap(sales) }
            val shortBillNo = consolidatedNoMap[targetSale.id] ?: targetSale.id.takeLast(4).uppercase()
            BillReturnFlowScreen(
                primarySale = targetSale,
                siblingItems = siblingItems,
                shortBillNo = shortBillNo,
                customer = customerMap[targetSale.customerId],
                productMap = productMap,
                currentUser = currentUser,
                repository = repository,
                onBack = { saleToReturn = null },
                onViewBill = {
                    activeSaleId = targetSale.id
                    saleToReturn = null
                },
                onDone = { saleToReturn = null }
            )
        }

        saleToEdit != null -> {
            val targetSale = saleToEdit!!
            val siblingItems = remember(targetSale, sales) {
                sales.filter {
                    !it.isDeleted && it.customerId == targetSale.customerId && abs(it.date - targetSale.date) <= 2500L
                }.sortedBy { it.id }.ifEmpty { listOf(targetSale) }
            }
            val consolidatedNoMap = remember(sales) { buildConsolidatedBillNoMap(sales) }
            val shortBillNo = consolidatedNoMap[targetSale.id] ?: targetSale.id.takeLast(4).uppercase()
            EditBillFlowScreen(
                primarySale = targetSale,
                siblingItems = siblingItems,
                shortBillNo = shortBillNo,
                customer = customerMap[targetSale.customerId],
                products = products,
                productMap = productMap,
                currentUser = currentUser,
                repository = repository,
                onCancel = { saleToEdit = null },
                onSaved = { saleToEdit = null }
            )
        }

        billDataToShare != null -> {
            BillShareScreen(
                billData = billDataToShare!!,
                shopName = settings?.shopName?.ifBlank { "KhataWise" } ?: "KhataWise",
                shopUpiId = "shop@okaxis",
                onBack = { billDataToShare = null }
            )
        }

        else -> {
            BillDetailAndAuditScreen(
                sale = currentSale,
                allSales = sales,
                customerMap = customerMap,
                productMap = productMap,
                auditLogs = auditLogs,
                currentUser = currentUser,
                onBack = onClose,
                onShare = { billData -> billDataToShare = billData },
                onEdit = { saleToEdit = it },
                onReturn = { saleToReturn = it },
                onCancel = { saleToCancel = it },
                onOpenAllBills = onOpenAllBills
            )
        }
    }

    saleToCancel?.let { sale ->
        val consolidatedNoMap = remember(sales) { buildConsolidatedBillNoMap(sales) }
        val shortBillNo = consolidatedNoMap[sale.id] ?: sale.id.takeLast(4).uppercase()
        val toastCancelMsg = tr("Bill deleted & stock restored", "बिल हटाया गया और स्टॉक वापस किया गया")

        com.example.ui.components.DeleteBillConfirmationDialog(
            billNumber = shortBillNo,
            requireAdminPin = true,
            onReturnItemsInstead = {
                val target = sale
                saleToCancel = null
                saleToReturn = target
            },
            onConfirmDeleteBill = { reason, _ ->
                val actor = currentUser ?: User(id = "admin", name = "Admin", role = Role.ADMINISTRATOR, pinHash = "")
                scope.launch {
                    repository.cancelSaleBill(
                        saleId = sale.id,
                        reason = reason.ifBlank { "Deleted by ${actor.name}" },
                        performedBy = actor
                    )
                    saleToCancel = null
                    Toast.makeText(context, toastCancelMsg, Toast.LENGTH_SHORT).show()
                    onClose()
                }
            },
            onDismiss = { saleToCancel = null }
        )
    }
}

@Composable
fun BillsScreen(
    repository: CattleFeedRepository,
    currentUser: User?,
    onBack: () -> Unit
) {
    val sales by repository.allSales.collectAsState(initial = emptyList())
    val customers by repository.allCustomers.collectAsState(initial = emptyList())
    val products by repository.allProducts.collectAsState(initial = emptyList())
    val auditLogs by repository.allBillAuditLogs.collectAsState(initial = emptyList())
    val allUsers by repository.allUsers.collectAsState(initial = emptyList())

    var selectedSaleIdForDetail by remember { mutableStateOf<String?>(null) }
    var showAllAuditModal by remember { mutableStateOf(false) }

    val selectedSaleForDetail = remember(sales, selectedSaleIdForDetail) {
        selectedSaleIdForDetail?.let { id -> sales.find { it.id == id } }
    }

    val editedSaleIds = remember(auditLogs) {
        auditLogs.filter { it.actionType == "EDIT" }.map { it.saleId }.toSet()
    }

    when {
        showAllAuditModal -> {
            AuditLogFullScreen(
                auditLogs = auditLogs,
                allSales = sales,
                allUsers = allUsers,
                onBack = { showAllAuditModal = false }
            )
        }

        selectedSaleForDetail != null -> {
            UnifiedBillInspectorOverlay(
                primarySale = selectedSaleForDetail,
                repository = repository,
                currentUser = currentUser,
                onClose = { selectedSaleIdForDetail = null }
            )
        }

        else -> {
            BackHandler { onBack() }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FlatDarkBg)
            ) {
                // Unified 42dp rounded back-button header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onBack,
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("bills_back_btn"),
                        shape = RoundedCornerShape(13.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = tr("Back", "वापस"),
                                tint = FlatTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr("Bills", "बिल"),
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = tr("Edit, return & audit", "बदलाव, वापसी और ऑडिट"),
                            fontSize = 12.5.sp,
                            color = FlatTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        onClick = { showAllAuditModal = true },
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("bills_audit_btn"),
                        shape = RoundedCornerShape(13.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.VerifiedUser,
                                contentDescription = tr("Audit trail", "ऑडिट ट्रेल"),
                                tint = FlatTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                UnifiedBillsContent(
                    sales = sales,
                    customers = customers,
                    products = products,
                    editedSaleIds = editedSaleIds,
                    onInspectBill = { sale -> selectedSaleIdForDetail = sale.id },
                    onEmptyNewBill = onBack,
                    enablePagerSwipe = true,
                    bottomContentPadding = 28.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
fun UnifiedBillsContent(
    sales: List<SaleEntry>,
    customers: List<Customer>,
    products: List<Product>,
    editedSaleIds: Set<String> = emptySet(),
    onInspectBill: (SaleEntry) -> Unit,
    onEmptyNewBill: () -> Unit = {},
    onOpenAllBills: (() -> Unit)? = null,
    enablePagerSwipe: Boolean = true,
    bottomContentPadding: Dp = 28.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val customerMap = remember(customers) { customers.associateBy { it.id } }
    val productMap = remember(products) { products.associateBy { it.id } }

    val filterKeys = remember { listOf("All", "Paid", "Udhari", "Returned") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { filterKeys.size })
    LaunchedEffect(pagerState.currentPage) {
        if (enablePagerSwipe) {
            selectedFilter = filterKeys[pagerState.currentPage]
        }
    }
    var filterDateMillis by remember { mutableStateOf<Long?>(null) }

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dayFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    val groupedBills = remember(sales) {
        groupSalesIntoBills(sales.filter { !it.isDeleted }, descending = true)
    }
    val billNoMap = remember(sales) {
        buildConsolidatedBillNoMap(sales)
    }

    fun filterBillsForKey(filterKey: String): List<GroupedSaleBill> {
        return groupedBills.filter { bill ->
            val customerName = customerMap[bill.customerId]?.name ?: "Walk-in"
            val shortNo = bill.billNumber.ifBlank { billNoMap[bill.id] ?: bill.id.takeLast(4).uppercase() }
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                val q = searchQuery.trim()
                val anyProductMatches = bill.items.any { item ->
                    val pName = productMap[item.productId]?.feedName ?: "Item"
                    pName.contains(q, ignoreCase = true)
                }
                customerName.contains(q, ignoreCase = true) ||
                    anyProductMatches ||
                    shortNo.contains(q, ignoreCase = true) ||
                    bill.id.takeLast(6).contains(q, ignoreCase = true)
            }

            val isReturned = bill.isReturned
            val isUdhari = bill.udhariAmount > BigDecimal.ZERO && !bill.isCancelled
            val isPaid = bill.paymentStatus == PaymentStatus.PAID && !isReturned && !bill.isCancelled

            val matchesFilter = when (filterKey) {
                "Paid" -> isPaid
                "Udhari" -> isUdhari
                "Returned" -> isReturned
                else -> true
            }

            val matchesDate = if (filterDateMillis == null) {
                true
            } else {
                isSameCalendarDay(bill.date, filterDateMillis!!)
            }

            matchesSearch && matchesFilter && matchesDate
        }
    }

    val filteredSales = remember(groupedBills, searchQuery, selectedFilter, filterDateMillis, customerMap, productMap) {
        filterBillsForKey(selectedFilter)
    }

    val todayStart = remember { startOfDayMillis(System.currentTimeMillis()) }
    val yesterdayStart = remember { todayStart - 24L * 60L * 60L * 1000L }

    val summarySales = remember(filteredSales, filterDateMillis, todayStart) {
        if (filterDateMillis != null) {
            filteredSales
        } else {
            val todayOnes = filteredSales.filter { it.date >= todayStart }
            if (todayOnes.isNotEmpty()) todayOnes else filteredSales
        }
    }
    val summaryTotalAmount = remember(summarySales) {
        summarySales.fold(BigDecimal.ZERO) { acc, b ->
            if (b.isCancelled) acc else acc.add(b.totalSalePrice)
        }
    }
    val todayLabel = tr("Today", "आज")
    val allBillsLabel = tr("All bills", "सभी बिल")
    val summaryDateTitle = remember(filterDateMillis, filteredSales, todayStart, todayLabel, allBillsLabel) {
        when {
            filterDateMillis != null && isSameCalendarDay(filterDateMillis!!, System.currentTimeMillis()) -> todayLabel
            filterDateMillis != null -> dayFormat.format(Date(filterDateMillis!!))
            filteredSales.any { it.date >= todayStart } -> todayLabel
            else -> allBillsLabel
        }
    }

    @Composable
    fun BillsListPage(pageFilterKey: String) {
        val pageSales = remember(groupedBills, searchQuery, pageFilterKey, filterDateMillis, customerMap, productMap) {
            filterBillsForKey(pageFilterKey)
        }
        val pageTodayGroup = remember(pageSales, todayStart) {
            pageSales.filter { it.date >= todayStart }
        }
        val pageYesterdayGroup = remember(pageSales, todayStart, yesterdayStart) {
            pageSales.filter { it.date in yesterdayStart until todayStart }
        }
        val pageOlderGroups = remember(pageSales, yesterdayStart) {
            pageSales.filter { it.date < yesterdayStart }
                .groupBy { dayFormat.format(Date(it.date)).uppercase(Locale.getDefault()) }
        }

        if (pageSales.isEmpty()) {
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
                    com.example.ui.components.EmptyBillsTodayStateCard(
                        onNewBill = onEmptyNewBill
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = bottomContentPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(pageTodayGroup, key = { it.id }) { bill ->
                    val shortNo = bill.billNumber.ifBlank { billNoMap[bill.id] ?: bill.id.takeLast(4).uppercase() }
                    val isEdited = bill.isEdited || bill.items.any { editedSaleIds.contains(it.id) }
                    Box(modifier = Modifier.testTag("sale_ledger_item_${bill.id}")) {
                        BillSummaryCard(
                            bill = bill,
                            shortBillNo = shortNo,
                            customer = customerMap[bill.customerId],
                            product = productMap[bill.primarySale.productId],
                            timeFormatted = timeFormat.format(Date(bill.date)),
                            isEdited = isEdited,
                            onClick = { onInspectBill(bill.primarySale) }
                        )
                    }
                }

                if (pageYesterdayGroup.isNotEmpty()) {
                    item(key = "header_yesterday") {
                        Text(
                            text = tr("YESTERDAY", "कल"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 2.dp)
                        )
                    }
                    items(pageYesterdayGroup, key = { it.id }) { bill ->
                        val shortNo = bill.billNumber.ifBlank { billNoMap[bill.id] ?: bill.id.takeLast(4).uppercase() }
                        val isEdited = bill.isEdited || bill.items.any { editedSaleIds.contains(it.id) }
                        Box(modifier = Modifier.testTag("sale_ledger_item_${bill.id}")) {
                            BillSummaryCard(
                                bill = bill,
                                shortBillNo = shortNo,
                                customer = customerMap[bill.customerId],
                                product = productMap[bill.primarySale.productId],
                                timeFormatted = timeFormat.format(Date(bill.date)),
                                isEdited = isEdited,
                                onClick = { onInspectBill(bill.primarySale) }
                            )
                        }
                    }
                }

                pageOlderGroups.forEach { (dateHeader, salesInDate) ->
                    item(key = "header_$dateHeader") {
                        Text(
                            text = dateHeader,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 2.dp)
                        )
                    }
                    items(salesInDate, key = { it.id }) { bill ->
                        val shortNo = bill.billNumber.ifBlank { billNoMap[bill.id] ?: bill.id.takeLast(4).uppercase() }
                        val isEdited = bill.isEdited || bill.items.any { editedSaleIds.contains(it.id) }
                        Box(modifier = Modifier.testTag("sale_ledger_item_${bill.id}")) {
                            BillSummaryCard(
                                bill = bill,
                                shortBillNo = shortNo,
                                customer = customerMap[bill.customerId],
                                product = productMap[bill.primarySale.productId],
                                timeFormatted = timeFormat.format(Date(bill.date)),
                                isEdited = isEdited,
                                onClick = { onInspectBill(bill.primarySale) }
                            )
                        }
                    }
                }
            }
        }
    }

    Column(modifier = modifier) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = tr("Search bill no., customer, item...", "बिल नंबर, ग्राहक या आइटम खोजें..."),
                    color = FlatTextMuted,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = FlatTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = tr("Clear search", "सर्च साफ़ करें"),
                            tint = FlatTextSecondary
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = FlatDarkCard,
                unfocusedContainerColor = FlatDarkCard,
                focusedBorderColor = BillsGreen,
                unfocusedBorderColor = FlatDarkBorder,
                focusedTextColor = FlatTextPrimary,
                unfocusedTextColor = FlatTextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("bills_search_input")
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Filter chips row: All, Paid, Udhari, Returned (+ optional Audit shortcut when embedded in Ledger)
        val filterOptions = listOf(
            "All" to tr("All", "सभी"),
            "Paid" to tr("Paid", "भुगतान"),
            "Udhari" to tr("Udhari", "उधारी"),
            "Returned" to tr("Returned", "वापस")
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                filterOptions.forEachIndexed { pageIndex, (key, label) ->
                    val isSelected = if (enablePagerSwipe) pagerState.currentPage == pageIndex else selectedFilter == key
                    Surface(
                        onClick = {
                            selectedFilter = key
                            if (enablePagerSwipe) {
                                scope.launch { pagerState.animateScrollToPage(pageIndex) }
                            }
                        },
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) BillsGreen else BillsChipIdleBg,
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("bills_filter_${key.lowercase(Locale.ROOT)}")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 15.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else FlatTextPrimary,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (onOpenAllBills != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    onClick = onOpenAllBills,
                    shape = RoundedCornerShape(50),
                    color = BillsGreenSoft,
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("sales_ledger_open_all_bills_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VerifiedUser,
                            contentDescription = null,
                            tint = BillsGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = tr("Audit", "ऑडिट"),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = BillsGreen,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Date summary & Change date row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = FlatTextPrimary)) {
                        append(summaryDateTitle)
                    }
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = FlatTextSecondary)) {
                        append(" · ${summarySales.size} ${tr("bills", "बिल")} · ${CurrencyUtils.formatInr(summaryTotalAmount)}")
                    }
                },
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        if (filterDateMillis != null) {
                            filterDateMillis = null
                        } else {
                            val cal = Calendar.getInstance()
                            DatePickerDialog(
                                pickerThemedContext(context),
                                { _, y, m, d ->
                                    val picked = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, y)
                                        set(Calendar.MONTH, m)
                                        set(Calendar.DAY_OF_MONTH, d)
                                    }
                                    filterDateMillis = picked.timeInMillis
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("bills_change_date_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarToday,
                    contentDescription = null,
                    tint = BillsGreen,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = if (filterDateMillis != null) tr("All dates", "सभी तारीखें") else tr("Change date", "तारीख बदलें"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = BillsGreen,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (enablePagerSwipe) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                BillsListPage(filterKeys[pageIndex])
            }
        } else {
            BillsListPage(selectedFilter)
        }
    }
}

@Composable
fun BillSummaryCard(
    bill: GroupedSaleBill,
    shortBillNo: String,
    customer: Customer?,
    product: Product?,
    timeFormatted: String,
    isEdited: Boolean,
    onClick: () -> Unit
) {
    val primarySale = bill.primarySale
    val customerName = customer?.name?.takeIf { it.isNotBlank() } ?: "Walk-in"
    val productName = product?.feedName ?: "Cattle Feed"

    val quantityDescriptor = remember(primarySale.quantityKg, product, bill.extraItemCount) {
        val base = formatSaleItemQuantity(primarySale.quantityKg, product)
        if (bill.extraItemCount > 0) "$base +${bill.extraItemCount}" else base
    }

    val isReturned = bill.isReturned
    val isCancelled = bill.isCancelled

    val (primaryBadgeLabel, primaryBadgeBg, primaryBadgeText) = when {
        isCancelled -> Triple("Cancelled", BillsRedSoft, BillsRed)
        isReturned -> Triple("Returned", BillsAmberSoft, BillsAmber)
        bill.paymentStatus == PaymentStatus.PAID -> Triple("Paid · Cash", BillsGreenSoft, BillsGreen)
        bill.paymentStatus == PaymentStatus.PARTIAL -> Triple("Part udhari", BillsRedSoft, BillsRed)
        else -> Triple("Udhari", BillsRedSoft, BillsRed)
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bill_card_${bill.id}"),
        shape = RoundedCornerShape(16.dp),
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
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = customerName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "#$shortBillNo · $timeFormatted · $productName $quantityDescriptor",
                    fontSize = 13.sp,
                    color = FlatTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = CurrencyUtils.formatInr(bill.totalSalePrice),
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = primaryBadgeBg
                    ) {
                        Text(
                            text = primaryBadgeLabel,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryBadgeText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }

                    if (isEdited) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = BillsEditedBg
                        ) {
                            Text(
                                text = "Edited",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = BillsEditedText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BillDetailAndAuditScreen(
    sale: SaleEntry,
    allSales: List<SaleEntry>,
    customerMap: Map<String, Customer>,
    productMap: Map<String, Product>,
    auditLogs: List<BillAuditLog>,
    currentUser: User?,
    onBack: () -> Unit,
    onShare: (BillData) -> Unit,
    onEdit: (SaleEntry) -> Unit,
    onReturn: (SaleEntry) -> Unit,
    onCancel: (SaleEntry) -> Unit,
    onOpenAllBills: (() -> Unit)? = null
) {
    BackHandler { onBack() }

    val customer = customerMap[sale.customerId]
    val customerName = customer?.name?.takeIf { it.isNotBlank() } ?: "Walk-in"
    val customerVillage = customer?.village?.takeIf { it.isNotBlank() } ?: ""
    val customerPhone = customer?.phone?.takeIf { it.isNotBlank() } ?: ""

    // Group sibling line items created in the same checkout batch (same customer within 2.5 seconds)
    val siblingItems = remember(sale, allSales) {
        val matches = allSales.filter {
            !it.isDeleted && it.customerId == sale.customerId && abs(it.date - sale.date) <= 2500L
        }.sortedBy { it.id }
        if (matches.isEmpty()) listOf(sale) else matches
    }

    val billNoMap = remember(allSales) {
        buildConsolidatedBillNoMap(allSales)
    }
    val shortBillNo = billNoMap[sale.id] ?: sale.id.takeLast(4).uppercase()

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
    val relativeDateSubtitle = remember(sale.date) {
        val todayStart = startOfDayMillis(System.currentTimeMillis())
        val yesterdayStart = todayStart - 24L * 60L * 60L * 1000L
        val prefix = when {
            sale.date >= todayStart -> "Today"
            sale.date >= yesterdayStart -> "Yesterday"
            else -> dateFormat.format(Date(sale.date))
        }
        "$prefix, ${timeFormat.format(Date(sale.date))}"
    }

    val billTotal = remember(siblingItems) {
        siblingItems.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.totalSalePrice) }
    }
    val billPaid = remember(siblingItems) {
        siblingItems.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.paidAmount) }
    }
    val billUdhari = remember(siblingItems) {
        siblingItems.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.udhariAmount) }
    }

    val billAuditLogs = remember(auditLogs, siblingItems) {
        val ids = siblingItems.map { it.id }.toSet()
        auditLogs.filter { it.saleId in ids }.sortedBy { it.timestamp }
    }

    val isEdited = sale.billStatus == "EDITED" || billAuditLogs.any { it.actionType == "EDIT" }
    val isReturned = sale.billStatus == "RETURNED" || siblingItems.any { it.returnedKg > BigDecimal.ZERO }
    val isCancelled = sale.billStatus == "CANCELLED"

    val (primaryBadgeLabel, primaryBadgeBg, primaryBadgeText) = when {
        isCancelled -> Triple("Cancelled", BillsRedSoft, BillsRed)
        isReturned -> Triple("Returned", BillsAmberSoft, BillsAmber)
        billUdhari.compareTo(BigDecimal.ZERO) == 0 -> Triple("Paid · Cash", BillsGreenSoft, BillsGreen)
        billPaid > BigDecimal.ZERO -> Triple("Part udhari", BillsRedSoft, BillsRed)
        else -> Triple("Udhari", BillsRedSoft, BillsRed)
    }

    val billDataForShare = remember(siblingItems, customer, productMap, shortBillNo, billTotal, billPaid, billUdhari, currentUser) {
        val billItems = siblingItems.map { itemSale ->
            val p = productMap[itemSale.productId]
            val bagsCount = if (p != null && p.kgPerBag.compareTo(BigDecimal.ZERO) > 0 && !p.isLooseOnly) {
                itemSale.quantityKg.divide(p.kgPerBag, 0, RoundingMode.DOWN).toInt()
            } else 0
            val bagWeight = if (p != null) p.kgPerBag.multiply(BigDecimal(bagsCount)) else BigDecimal.ZERO
            val looseKg = (itemSale.quantityKg - bagWeight).max(BigDecimal.ZERO)
            BillItem(
                feedName = p?.feedName ?: "Feed Product",
                bags = bagsCount,
                looseKg = looseKg,
                totalKg = itemSale.quantityKg,
                pricePerKg = itemSale.pricePerKgAtSale,
                itemTotal = itemSale.totalSalePrice
            )
        }
        BillData(
            billNumber = shortBillNo,
            date = sale.date,
            customerName = customerName,
            customerPhone = customer?.phone ?: "",
            customerVillage = customer?.village ?: "",
            items = billItems,
            subtotal = billTotal,
            paidAmount = billPaid,
            udhariAmount = billUdhari,
            previousUdhari = customer?.totalUdhari ?: BigDecimal.ZERO,
            newTotalUdhari = customer?.totalUdhari ?: BigDecimal.ZERO,
            staffName = currentUser?.name ?: "Rakesh"
        )
    }

    val staffDisplay = remember(currentUser) {
        val name = currentUser?.name?.takeIf { it.isNotBlank() } ?: "Rakesh"
        val roleLabel = if (currentUser?.role == Role.STAFF) "Staff" else "Owner"
        "$name ($roleLabel)"
    }

    // Build chronological audit events matching Bills · Detail & audit.pdf
    val auditEvents = remember(sale, billPaid, billAuditLogs, staffDisplay, timeFormat) {
        val list = mutableListOf<Triple<String, String, Color>>()
        val createdTimeStr = timeFormat.format(Date(sale.date))
        list.add(
            Triple(
                "Bill created",
                "$createdTimeStr · $staffDisplay",
                BillsGreen
            )
        )
        billAuditLogs.forEach { log ->
            val logTimeStr = timeFormat.format(Date(log.timestamp))
            val title = when (log.actionType) {
                "EDIT" -> "Edited · ${log.productName} ${log.previousSummary} → ${log.newSummary}"
                "RETURN" -> "Returned · ${log.productName} (${log.newSummary})"
                "CANCEL" -> "Bill cancelled · ${log.reason}"
                else -> "${log.actionType} · ${log.productName}"
            }
            list.add(
                Triple(
                    title,
                    "$logTimeStr · ${log.performedByUserName}",
                    kw(0xFF6B7280)
                )
            )
        }
        if (billPaid > BigDecimal.ZERO) {
            list.add(
                Triple(
                    "Payment received · ${CurrencyUtils.formatInr(billPaid)} cash",
                    "$createdTimeStr · $staffDisplay",
                    BillsGreen
                )
            )
        }
        list
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlatDarkBg)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp)
    ) {
        // Top Header: Back button + Bill #1042 + Today, 11:20 AM
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
                    .testTag("bill_detail_back_btn"),
                shape = RoundedCornerShape(13.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = FlatTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bill #$shortBillNo",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary
                )
                Text(
                    text = relativeDateSubtitle,
                    fontSize = 13.5.sp,
                    color = FlatTextSecondary
                )
            }

            if (onOpenAllBills != null) {
                Surface(
                    onClick = {
                        onBack()
                        onOpenAllBills()
                    },
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .testTag("bill_detail_open_all_bills_btn"),
                    shape = RoundedCornerShape(12.dp),
                    color = BillsGreenSoft
                ) {
                    Text(
                        text = tr("All Bills →", "सभी बिल →"),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = BillsGreen,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            if (!isCancelled) {
                Surface(
                    onClick = { onCancel(sale) },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("bill_detail_delete_btn"),
                    shape = RoundedCornerShape(13.dp),
                    color = kw(0xFFFEE2E2),
                    border = BorderStroke(1.dp, kw(0xFFFECACA))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete bill",
                            tint = BillsRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Customer Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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
                                .background(BillsAvatarBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = customerInitials(customerName),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = BillsAvatarText
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = customerName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                            val customerSubline = listOf(customerVillage, customerPhone)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                            if (customerSubline.isNotBlank()) {
                                Text(
                                    text = customerSubline,
                                    fontSize = 13.sp,
                                    color = FlatTextSecondary
                                )
                            }
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = primaryBadgeBg
                        ) {
                            Text(
                                text = primaryBadgeLabel,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryBadgeText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }

                        if (isEdited) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = BillsEditedBg
                            ) {
                                Text(
                                    text = "Edited",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BillsEditedText,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ITEMS & Totals Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = tr("ITEMS", "आइटम"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.8.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    siblingItems.forEachIndexed { index, itemSale ->
                        val product = productMap[itemSale.productId]
                        val itemTitle = formatProductDisplayName(product)
                        val itemRateLine = formatItemQuantityAndRateLine(itemSale, product)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = itemTitle,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary
                                )
                                Text(
                                    text = itemRateLine,
                                    fontSize = 13.sp,
                                    color = FlatTextSecondary
                                )
                            }

                            Text(
                                text = CurrencyUtils.formatInr(itemSale.totalSalePrice),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                        }

                        if (index < siblingItems.lastIndex) {
                            HorizontalDivider(
                                color = FlatDarkBorder,
                                thickness = 1.dp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }

                    HorizontalDivider(
                        color = FlatDarkBorder,
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )

                    // Total Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr("Total", "कुल राशि"),
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                        Text(
                            text = CurrencyUtils.formatInr(billTotal),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                    }

                    // Paid now · Cash Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr("Paid now · Cash", "नकद जमा"),
                            fontSize = 14.5.sp,
                            color = FlatTextSecondary
                        )
                        Text(
                            text = CurrencyUtils.formatInr(billPaid),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = BillsGreen
                        )
                    }

                    // Udhari due Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr("Udhari due", "बाकी उधारी"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = BillsRed
                        )
                        Text(
                            text = CurrencyUtils.formatInr(billUdhari),
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = BillsRed
                        )
                    }
                }
            }

            // 3 Outlined Action Buttons Row: Share | Edit | Return
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onShare(billDataForShare) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("bill_detail_share_btn"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, BillsGreen),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = BillsGreen
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = BillsGreen
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tr("Share", "शेयर"),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = BillsGreen,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = { onEdit(sale) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("bill_detail_edit_btn"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, BillsGreen),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = BillsGreen
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = BillsGreen
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tr("Edit", "बदलें"),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = BillsGreen,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = { onReturn(sale) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("bill_detail_return_btn"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, BillsRed),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = BillsRed
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = BillsRed
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tr("Return", "वापसी"),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = BillsRed,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Audit trail Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr("Audit trail", "ऑडिट ट्रेल"),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                        Text(
                            text = "${auditEvents.size} events",
                            fontSize = 12.5.sp,
                            color = FlatTextSecondary
                        )
                    }

                    auditEvents.forEach { (title, subtitle, dotColor) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary
                                )
                                Text(
                                    text = subtitle,
                                    fontSize = 12.5.sp,
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

private fun customerInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase(Locale.getDefault())
        parts.size == 1 -> parts[0].take(2).uppercase(Locale.getDefault())
        else -> "WI"
    }
}

private fun formatSaleItemQuantity(quantityKg: BigDecimal, product: Product?): String {
    if (product != null && !product.isLooseOnly && product.kgPerBag > BigDecimal.ZERO) {
        val bags = quantityKg.divide(product.kgPerBag, 0, RoundingMode.DOWN).toInt()
        val remainder = quantityKg.subtract(product.kgPerBag.multiply(BigDecimal(bags)))
        if (bags > 0 && remainder.compareTo(BigDecimal.ZERO) == 0) {
            return if (bags == 1) "× 1 bag" else "× $bags bags"
        }
    }
    return "· ${CurrencyUtils.formatWeight(quantityKg)} kg"
}

private fun formatProductDisplayName(product: Product?): String {
    if (product == null) return "Cattle Feed (50 kg)"
    return if (!product.isLooseOnly && product.kgPerBag > BigDecimal.ZERO) {
        "${product.feedName} (${CurrencyUtils.formatWeight(product.kgPerBag)} kg)"
    } else {
        product.feedName
    }
}

private fun formatItemQuantityAndRateLine(sale: SaleEntry, product: Product?): String {
    if (product != null && !product.isLooseOnly && product.kgPerBag > BigDecimal.ZERO) {
        val bags = sale.quantityKg.divide(product.kgPerBag, 0, RoundingMode.DOWN).toInt()
        val remainder = sale.quantityKg.subtract(product.kgPerBag.multiply(BigDecimal(bags)))
        if (bags > 0 && remainder.compareTo(BigDecimal.ZERO) == 0) {
            val bagRate = sale.pricePerKgAtSale.multiply(product.kgPerBag)
            val bagLabel = if (bags == 1) "1 bag" else "$bags bags"
            return "$bagLabel × ${CurrencyUtils.formatInr(bagRate)}"
        }
    }
    return "${CurrencyUtils.formatWeight(sale.quantityKg)} kg × ${CurrencyUtils.formatInr(sale.pricePerKgAtSale, true)}/kg"
}

private fun startOfDayMillis(timeMillis: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = timeMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

private fun isSameCalendarDay(t1: Long, t2: Long): Boolean {
    return startOfDayMillis(t1) == startOfDayMillis(t2)
}
