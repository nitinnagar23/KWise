package com.example.ui.cashbook

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.repository.CattleFeedRepository
import com.example.model.User
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatTextMuted
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import com.example.util.CurrencyUtils
import com.example.util.ShopAnalytics
import com.example.util.tr
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val CbBrandGreen = Color(0xFF0E6F43)
private val CbMintBg = Color(0xFFDDF3E8)
private val CbCrimsonRed = Color(0xFFB91C1C)
private val CbCrimsonSoftBg = Color(0xFFFCE4E4)
private val CbNeutralPillBg = Color(0xFFF3F3F8)
private val CbChipUnselectedBg = Color(0xFFF1F1F7)
private val CbAutoBadgeBg = Color(0xFFECECF2)
private val CbAutoBadgeText = Color(0xFF4B5563)

enum class CashbookFilter(val labelEn: String, val labelHi: String) {
    ALL("All", "सभी"),
    CASH_IN("Cash in", "कैश इन"),
    CASH_OUT("Cash out", "कैश आउट"),
    EXPENSES("Expenses", "ख़र्च")
}

data class CashbookEntry(
    val id: String,
    val date: Long,
    val title: String,
    val subtitle: String,
    val amount: BigDecimal,
    val isCashIn: Boolean,
    val tag: String,
    val isAuto: Boolean = false,
    val isExpense: Boolean = false,
    val receiptUri: String? = null
)

internal object CashbookManualPrefs {
    private const val PREFS_NAME = "khatawise_cashbook_manual_prefs"
    private const val KEY_ENTRIES = "manual_cashbook_entries_json"
    private const val KEY_OPENING_CASH = "default_opening_cash"

    fun loadOpeningCash(context: Context): BigDecimal {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_OPENING_CASH, "4200") ?: "4200"
        return raw.toBigDecimalOrNull() ?: BigDecimal("4200")
    }

    fun saveOpeningCash(context: Context, amount: BigDecimal) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_OPENING_CASH, amount.toPlainString()).apply()
    }

    fun loadManualEntries(context: Context): List<CashbookEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<CashbookEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    CashbookEntry(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        date = obj.optLong("date", System.currentTimeMillis()),
                        title = obj.optString("title", "Expense"),
                        subtitle = obj.optString("subtitle", ""),
                        amount = obj.optString("amount", "0").toBigDecimalOrNull() ?: BigDecimal.ZERO,
                        isCashIn = obj.optBoolean("isCashIn", false),
                        tag = obj.optString("tag", "EXPENSE"),
                        isAuto = false,
                        isExpense = obj.optBoolean("isExpense", true),
                        receiptUri = obj.optString("receiptUri", "").takeIf { it.isNotBlank() }
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addManualEntry(context: Context, entry: CashbookEntry) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = loadManualEntries(context).toMutableList()
        existing.add(0, entry)
        val arr = JSONArray()
        existing.take(300).forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("date", item.date)
            obj.put("title", item.title)
            obj.put("subtitle", item.subtitle)
            obj.put("amount", item.amount.toPlainString())
            obj.put("isCashIn", item.isCashIn)
            obj.put("tag", item.tag)
            obj.put("isExpense", item.isExpense)
            obj.put("receiptUri", item.receiptUri ?: "")
            arr.put(obj)
        }
        prefs.edit().putString(KEY_ENTRIES, arr.toString()).apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashbookScreen(
    repository: CattleFeedRepository,
    currentUser: User?,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val sales by repository.allSales.collectAsState(initial = emptyList())
    val payments by repository.allPayments.collectAsState(initial = emptyList())
    val invoices by repository.allPurchaseInvoices.collectAsState(initial = emptyList())
    val supplierPayments by repository.allSupplierPayments.collectAsState(initial = emptyList())
    val customers by repository.allCustomers.collectAsState(initial = emptyList())

    val customerMap = remember(customers) { customers.associateBy({ it.id }, { it.name }) }

    val dayMs = 24L * 60L * 60L * 1000L
    val todayStart = remember { ShopAnalytics.getStartOfTodayMillis(System.currentTimeMillis()) }
    var selectedDayStart by remember { mutableLongStateOf(todayStart) }
    val selectedDayEnd = selectedDayStart + dayMs - 1L

    var selectedFilter by remember { mutableStateOf(CashbookFilter.ALL) }
    var manualRefreshTick by remember { mutableIntStateOf(0) }

    var showAddEntryDialog by remember { mutableStateOf(false) }
    var addEntryInitialCashIn by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showEditOpeningDialog by remember { mutableStateOf(false) }
    var activeSubScreen by remember { mutableStateOf<String?>(null) } // "CLOSE_DAY", "DAY_CLOSED", "EXPENSE_CATEGORIES"

    val baseOpeningCash = remember(manualRefreshTick) {
        CashbookManualPrefs.loadOpeningCash(context)
    }

    val manualEntries = remember(manualRefreshTick) {
        CashbookManualPrefs.loadManualEntries(context)
    }

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.ENGLISH) }

    val allCombinedEntries = remember(sales, payments, invoices, supplierPayments, customerMap, manualEntries) {
        val list = mutableListOf<CashbookEntry>()

        // Manual Cash in / Cash out entries
        list.addAll(manualEntries)

        // Auto Sale Cash Received (consolidated per bill)
        val groupedCashBills = com.example.model.groupSalesIntoBills(
            sales.filter { !it.isDeleted && it.billStatus != "CANCELLED" },
            descending = false
        )
        groupedCashBills.filter { it.paidAmount > BigDecimal.ZERO }.forEach { bill ->
            val customerName = customerMap[bill.customerId] ?: "Walk-in"
            val shortBillNo = bill.billNumber.ifBlank { (bill.date % 10000).toString().padStart(4, '0') }
            list.add(
                CashbookEntry(
                    id = "sale_${bill.id}",
                    date = bill.date,
                    title = "Sale · Bill #$shortBillNo",
                    subtitle = "$customerName · ${timeFormat.format(Date(bill.date))}",
                    amount = bill.paidAmount,
                    isCashIn = true,
                    tag = "SALE CASH",
                    isAuto = true,
                    isExpense = false
                )
            )
        }

        // Auto Customer Udhari Repayments
        payments.filter { !it.isDeleted && it.amount > BigDecimal.ZERO }.forEach { p ->
            val customerName = customerMap[p.customerId] ?: "Customer"
            list.add(
                CashbookEntry(
                    id = "payment_${p.id}",
                    date = p.date,
                    title = "Repayment · $customerName",
                    subtitle = "Udhari · ${timeFormat.format(Date(p.date))}",
                    amount = p.amount,
                    isCashIn = true,
                    tag = "PAYMENT IN",
                    isAuto = true,
                    isExpense = false
                )
            )
        }

        // Purchase Invoice Cash Paid
        invoices.filter { it.paidAmount > BigDecimal.ZERO }.forEach { inv ->
            list.add(
                CashbookEntry(
                    id = "inv_${inv.id}",
                    date = inv.date,
                    title = "Supplier · ${inv.wholesalerName}",
                    subtitle = "Invoice #${inv.invoiceNumber} · ${timeFormat.format(Date(inv.date))}",
                    amount = inv.paidAmount,
                    isCashIn = false,
                    tag = "PURCHASE PAID",
                    isAuto = false,
                    isExpense = false
                )
            )
        }

        // Direct Supplier Payments
        supplierPayments.forEach { sp ->
            list.add(
                CashbookEntry(
                    id = "sp_${sp.id}",
                    date = sp.date,
                    title = "Supplier · ${sp.wholesalerName}",
                    subtitle = "Part payment · ${timeFormat.format(Date(sp.date))}",
                    amount = sp.amount,
                    isCashIn = false,
                    tag = "SUPPLIER PAYMENT",
                    isAuto = false,
                    isExpense = false
                )
            )
        }

        list.sortedByDescending { it.date }
    }

    val dayEntries = remember(allCombinedEntries, selectedDayStart, selectedDayEnd) {
        allCombinedEntries.filter { it.date in selectedDayStart..selectedDayEnd }
    }

    val dayCashIn = remember(dayEntries) {
        dayEntries.filter { it.isCashIn }.fold(BigDecimal.ZERO) { acc, e -> acc.add(e.amount) }
    }
    val dayCashOut = remember(dayEntries) {
        dayEntries.filter { !it.isCashIn }.fold(BigDecimal.ZERO) { acc, e -> acc.add(e.amount) }
    }
    val closingCash = remember(baseOpeningCash, dayCashIn, dayCashOut) {
        baseOpeningCash.add(dayCashIn).subtract(dayCashOut)
    }

    val upiBankToday = remember(payments, supplierPayments, selectedDayStart, selectedDayEnd) {
        val upiIn = payments.filter {
            !it.isDeleted &&
                it.date in selectedDayStart..selectedDayEnd &&
                it.note.contains("UPI", ignoreCase = true)
        }.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.amount) }

        val upiSupp = supplierPayments.filter {
            it.date in selectedDayStart..selectedDayEnd &&
                it.paymentMode.contains("UPI", ignoreCase = true)
        }.fold(BigDecimal.ZERO) { acc, sp -> acc.add(sp.amount) }

        upiIn.add(upiSupp)
    }

    val filteredEntries = remember(dayEntries, selectedFilter) {
        when (selectedFilter) {
            CashbookFilter.ALL -> dayEntries
            CashbookFilter.CASH_IN -> dayEntries.filter { it.isCashIn }
            CashbookFilter.CASH_OUT -> dayEntries.filter { !it.isCashIn }
            CashbookFilter.EXPENSES -> dayEntries.filter { !it.isCashIn && it.isExpense }
        }
    }

    val headerDateLabel = remember(selectedDayStart, todayStart) {
        val fmt = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)
        val datePart = fmt.format(Date(selectedDayStart))
        val diffDays = ((todayStart - selectedDayStart) / dayMs).toInt()
        when (diffDays) {
            0 -> "Today · $datePart"
            1 -> "Yesterday · $datePart"
            else -> datePart
        }
    }

    val cashInSavedToast = tr("Cash in entry saved", "कैश इन एंट्री सेव हो गई")
    val cashOutSavedToast = tr("Cash out entry saved", "कैश आउट एंट्री सेव हो गई")

    val closedRecordForSelectedDay = remember(selectedDayStart, manualRefreshTick) {
        ExpenseCategoryStore.getClosedDay(context, selectedDayStart)
    }

    val dayBills = remember(sales, selectedDayStart, selectedDayEnd) {
        com.example.model.groupSalesIntoBills(
            sales.filter {
                !it.isDeleted &&
                    it.billStatus != "CANCELLED" &&
                    it.date in selectedDayStart..selectedDayEnd
            },
            descending = false
        )
    }
    val dayTotalSales = remember(dayBills) {
        dayBills.fold(BigDecimal.ZERO) { acc, b -> acc.add(b.totalSalePrice) }
    }
    val dayUdhariGiven = remember(dayBills) {
        dayBills.fold(BigDecimal.ZERO) { acc, b -> acc.add(b.udhariAmount) }
    }
    val dayCollectionsReceived = remember(payments, selectedDayStart, selectedDayEnd) {
        payments.filter { !it.isDeleted && it.date in selectedDayStart..selectedDayEnd }
            .fold(BigDecimal.ZERO) { acc, p -> acc.add(p.amount) }
    }

    val monthStartMillis = remember {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    val monthEntries = remember(allCombinedEntries, monthStartMillis) {
        allCombinedEntries.filter { it.date >= monthStartMillis }
    }

    when (activeSubScreen) {
        "CLOSE_DAY" -> {
            CloseDayScreen(
                dayStartMillis = selectedDayStart,
                headerDateLabel = headerDateLabel,
                openingCash = baseOpeningCash,
                dayCashIn = dayCashIn,
                dayCashOut = dayCashOut,
                expectedCash = closingCash,
                billsCount = dayBills.size,
                totalSales = dayTotalSales,
                udhariGiven = dayUdhariGiven,
                collectionsReceived = dayCollectionsReceived,
                upiReceived = upiBankToday,
                closedByName = currentUser?.name?.takeIf { it.isNotBlank() } ?: "Ramesh",
                onBack = { activeSubScreen = null },
                onDayClosed = { record, carryForward ->
                    ExpenseCategoryStore.saveClosedDay(context, record)
                    if (carryForward) {
                        CashbookManualPrefs.saveOpeningCash(context, record.countedCash)
                    }
                    manualRefreshTick++
                    activeSubScreen = "DAY_CLOSED"
                }
            )
            return
        }

        "DAY_CLOSED" -> {
            val record = closedRecordForSelectedDay ?: ClosedDayRecord(
                dayStartMillis = selectedDayStart,
                closedAtMillis = System.currentTimeMillis(),
                closedByName = currentUser?.name?.takeIf { it.isNotBlank() } ?: "Ramesh",
                openingCash = baseOpeningCash,
                cashIn = dayCashIn,
                cashOut = dayCashOut,
                expectedCash = closingCash.setScale(0, RoundingMode.HALF_UP),
                countedCash = closingCash.setScale(0, RoundingMode.HALF_UP),
                differenceReason = "",
                differenceNote = "",
                billsCount = dayBills.size,
                totalSales = dayTotalSales,
                udhariGiven = dayUdhariGiven,
                collectionsReceived = dayCollectionsReceived,
                upiReceived = upiBankToday
            )
            val isAdmin = currentUser?.role == com.example.model.Role.ADMINISTRATOR || currentUser == null
            DayClosedSummaryScreen(
                record = record,
                isAdmin = isAdmin,
                onBack = { activeSubScreen = null },
                onReopenDay = {
                    ExpenseCategoryStore.reopenDay(context, selectedDayStart)
                    manualRefreshTick++
                    activeSubScreen = "CLOSE_DAY"
                }
            )
            return
        }

        "EXPENSE_CATEGORIES" -> {
            ExpenseCategoriesScreen(
                monthEntries = monthEntries,
                onBack = { activeSubScreen = null },
                onCategoriesChanged = { manualRefreshTick++ }
            )
            return
        }
    }

    Scaffold(
        containerColor = FlatDarkBg,
        bottomBar = {
            Surface(
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            addEntryInitialCashIn = true
                            showAddEntryDialog = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CbBrandGreen,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cashbook_add_cash_in_btn")
                    ) {
                        Text(
                            text = tr("+ Cash in", "+ कैश इन"),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Button(
                        onClick = {
                            addEntryInitialCashIn = false
                            showAddEntryDialog = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CbCrimsonRed,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cashbook_add_cash_out_btn")
                    ) {
                        Text(
                            text = tr("− Cash out", "− कैश आउट"),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Top Header: Back button + Title/Subtitle + Export/Download button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            onClick = onBack,
                            shape = RoundedCornerShape(12.dp),
                            color = FlatDarkCard,
                            border = BorderStroke(1.dp, FlatDarkBorder),
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("cashbook_back_btn")
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

                        Column {
                            Text(
                                text = tr("Cashbook", "कैशबुक"),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = tr("Daily cash & expenses", "दैनिक नकदी और ख़र्च"),
                                fontSize = 13.sp,
                                color = FlatTextSecondary
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            val reportText = buildString {
                                appendLine("KhataWise Cashbook — $headerDateLabel")
                                appendLine("Opening Cash: ${CurrencyUtils.formatInr(baseOpeningCash)}")
                                appendLine("Cash In: +${CurrencyUtils.formatInr(dayCashIn)}")
                                appendLine("Cash Out: -${CurrencyUtils.formatInr(dayCashOut)}")
                                appendLine("Closing Cash: ${CurrencyUtils.formatInr(closingCash)}")
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, reportText)
                            }
                            try {
                                context.startActivity(Intent.createChooser(shareIntent, "Export Cashbook"))
                            } catch (_: Exception) {}
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("cashbook_export_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = tr("Export cashbook", "कैशबुक डाउनलोड करें"),
                                tint = FlatTextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            // 2. Date Selector Bar: < Today · 5 Nov 2026 >
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Previous day",
                            tint = FlatTextSecondary,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable { selectedDayStart -= dayMs }
                                .testTag("cashbook_prev_day_btn")
                        )

                        Text(
                            text = headerDateLabel,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FlatTextPrimary,
                            modifier = Modifier
                                .clickable { showDatePicker = true }
                                .testTag("cashbook_date_label")
                        )

                        val canGoForward = selectedDayStart < todayStart
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next day",
                            tint = if (canGoForward) FlatTextSecondary else FlatTextMuted.copy(alpha = 0.4f),
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable(enabled = canGoForward) {
                                    if (selectedDayStart + dayMs <= todayStart) {
                                        selectedDayStart += dayMs
                                    }
                                }
                                .testTag("cashbook_next_day_btn")
                        )
                    }
                }
            }

            // 3. CLOSING CASH Hero Summary Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = tr("CLOSING CASH", "क्लोजिंग कैश"),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextSecondary,
                                    letterSpacing = 0.6.sp
                                )
                                Text(
                                    text = CurrencyUtils.formatInr(closingCash),
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = FlatTextPrimary,
                                    modifier = Modifier.testTag("cashbook_closing_cash_text")
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = tr("UPI / bank today", "यूपीआई / बैंक आज"),
                                    fontSize = 12.5.sp,
                                    color = FlatTextSecondary
                                )
                                Text(
                                    text = CurrencyUtils.formatInr(upiBankToday),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = FlatTextPrimary,
                                    modifier = Modifier.testTag("cashbook_upi_today_text")
                                )
                            }
                        }

                        // 3 Sub-cards: Opening | Cash in | Cash out
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Opening
                            Surface(
                                onClick = { showEditOpeningDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                color = CbNeutralPillBg,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("cashbook_opening_box")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = tr("Opening", "ओपनिंग"),
                                        fontSize = 12.sp,
                                        color = FlatTextSecondary
                                    )
                                    Text(
                                        text = CurrencyUtils.formatInr(baseOpeningCash),
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = FlatTextPrimary,
                                        maxLines = 1
                                    )
                                }
                            }

                            // Cash in
                            Surface(
                                onClick = { selectedFilter = CashbookFilter.CASH_IN },
                                shape = RoundedCornerShape(12.dp),
                                color = CbMintBg,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("cashbook_cash_in_box")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = tr("Cash in", "कैश इन"),
                                        fontSize = 12.sp,
                                        color = CbBrandGreen
                                    )
                                    Text(
                                        text = "+${CurrencyUtils.formatInr(dayCashIn)}",
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = CbBrandGreen,
                                        maxLines = 1
                                    )
                                }
                            }

                            // Cash out
                            Surface(
                                onClick = { selectedFilter = CashbookFilter.CASH_OUT },
                                shape = RoundedCornerShape(12.dp),
                                color = CbCrimsonSoftBg,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("cashbook_cash_out_box")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = tr("Cash out", "कैश आउट"),
                                        fontSize = 12.sp,
                                        color = CbCrimsonRed
                                    )
                                    Text(
                                        text = "−${CurrencyUtils.formatInr(dayCashOut)}",
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = CbCrimsonRed,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = FlatDarkBorder)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = {
                                    activeSubScreen = if (closedRecordForSelectedDay != null) {
                                        "DAY_CLOSED"
                                    } else {
                                        "CLOSE_DAY"
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (closedRecordForSelectedDay != null) CbMintBg else CbBrandGreen,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("cashbook_close_day_btn")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
                                ) {
                                    Text(
                                        text = if (closedRecordForSelectedDay != null) {
                                            tr("✓ Day closed · View summary", "✓ दिन बंद · सारांश देखें")
                                        } else {
                                            tr("Close day", "दिन बंद करें")
                                        },
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (closedRecordForSelectedDay != null) CbBrandGreen else Color.White
                                    )
                                }
                            }

                            Surface(
                                onClick = { activeSubScreen = "EXPENSE_CATEGORIES" },
                                shape = RoundedCornerShape(12.dp),
                                color = CbChipUnselectedBg,
                                border = BorderStroke(1.dp, FlatDarkBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("cashbook_expense_categories_btn")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
                                ) {
                                    Text(
                                        text = tr("Expense categories", "ख़र्च श्रेणियां"),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FlatTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Filter Chips: All | Cash in | Cash out | Expenses
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CashbookFilter.entries.forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Surface(
                            onClick = { selectedFilter = filter },
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) CbBrandGreen else CbChipUnselectedBg,
                            modifier = Modifier.testTag("cashbook_filter_${filter.name.lowercase()}")
                        ) {
                            Text(
                                text = tr(filter.labelEn, filter.labelHi),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else FlatTextPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }

            // 5. Grouped Transactions Card
            item {
                if (filteredEntries.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp, horizontal = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tr(
                                    "No cash entries recorded for this filter.",
                                    "इस फ़िल्टर के लिए कोई नकद एंट्री दर्ज नहीं है।"
                                ),
                                fontSize = 14.sp,
                                color = FlatTextMuted
                            )
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder)
                    ) {
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            filteredEntries.forEachIndexed { idx, entry ->
                                if (idx > 0) {
                                    HorizontalDivider(
                                        color = FlatDarkBorder.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                                CashbookEntryRow(entry = entry)
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Cash Entry Modal
    if (showAddEntryDialog) {
        AddCashEntryDialog(
            initialIsCashIn = addEntryInitialCashIn,
            selectedDayMillis = selectedDayStart,
            onDismiss = { showAddEntryDialog = false },
            onSaveEntry = { isCashIn, amount, category, note, receiptUri ->
                val now = System.currentTimeMillis()
                val entryTime = if (selectedDayStart == todayStart) {
                    now
                } else {
                    selectedDayStart + (14L * 60L * 60L * 1000L) + (30L * 60L * 1000L)
                }
                val formattedTime = timeFormat.format(Date(entryTime))
                val subtitleText = if (note.isNotBlank()) {
                    "$note · $formattedTime"
                } else if (isCashIn) {
                    "Cash in · $formattedTime"
                } else {
                    "Expense · $formattedTime"
                }
                val titleText = if (category.equals("Supplier payment", ignoreCase = true) && note.isNotBlank()) {
                    "Supplier · $note"
                } else {
                    category
                }
                val newEntry = CashbookEntry(
                    id = "manual_${UUID.randomUUID()}",
                    date = entryTime,
                    title = titleText,
                    subtitle = subtitleText,
                    amount = amount,
                    isCashIn = isCashIn,
                    tag = if (isCashIn) "CASH IN" else "EXPENSE",
                    isAuto = false,
                    isExpense = !isCashIn,
                    receiptUri = receiptUri
                )
                CashbookManualPrefs.addManualEntry(context, newEntry)
                manualRefreshTick++
                Toast.makeText(
                    context,
                    if (isCashIn) cashInSavedToast else cashOutSavedToast,
                    Toast.LENGTH_SHORT
                ).show()
                showAddEntryDialog = false
            }
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDayStart)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { picked ->
                            selectedDayStart = ShopAnalytics.getStartOfTodayMillis(picked)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(tr("Select", "चुनें"), color = CbBrandGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(tr("Cancel", "रद्द करें"), color = FlatTextSecondary)
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showEditOpeningDialog) {
        var openingInput by remember {
            mutableStateOf(baseOpeningCash.setScale(0, RoundingMode.HALF_UP).toPlainString())
        }
        AlertDialog(
            onDismissRequest = { showEditOpeningDialog = false },
            containerColor = FlatDarkCard,
            title = {
                Text(
                    text = tr("Set Opening Cash", "ओपनिंग कैश सेट करें"),
                    fontWeight = FontWeight.ExtraBold,
                    color = FlatTextPrimary
                )
            },
            text = {
                OutlinedTextField(
                    value = openingInput,
                    onValueChange = { openingInput = it.filter { c -> c.isDigit() || c == '.' } },
                    leadingIcon = { Text("₹", fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = openingInput.trim().toBigDecimalOrNull() ?: baseOpeningCash
                        CashbookManualPrefs.saveOpeningCash(context, parsed)
                        manualRefreshTick++
                        showEditOpeningDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CbBrandGreen, contentColor = Color.White)
                ) {
                    Text(tr("Save", "सेव करें"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditOpeningDialog = false }) {
                    Text(tr("Cancel", "रद्द करें"), color = FlatTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun CashbookEntryRow(entry: CashbookEntry) {
    val circleBg = if (entry.isCashIn) CbMintBg else CbCrimsonSoftBg
    val symbolColor = if (entry.isCashIn) CbBrandGreen else CbCrimsonRed
    val symbolText = if (entry.isCashIn) "↓" else "↑"
    val amountPrefix = if (entry.isCashIn) "+" else "−"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(circleBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = symbolText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = symbolColor
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = entry.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FlatTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.isAuto) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = CbAutoBadgeBg
                        ) {
                            Text(
                                text = "Auto",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = CbAutoBadgeText,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = entry.subtitle,
                    fontSize = 12.5.sp,
                    color = FlatTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(
            text = "$amountPrefix${CurrencyUtils.formatInr(entry.amount)}",
            fontSize = 15.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = symbolColor
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddCashEntryDialog(
    initialIsCashIn: Boolean,
    selectedDayMillis: Long,
    onDismiss: () -> Unit,
    onSaveEntry: (
        isCashIn: Boolean,
        amount: BigDecimal,
        category: String,
        note: String,
        receiptUri: String?
    ) -> Unit
) {
    var isCashIn by remember { mutableStateOf(initialIsCashIn) }
    var amountInput by remember { mutableStateOf("500") }

    val context = LocalContext.current
    val cashOutCategories = remember {
        ExpenseCategoryStore.getEnabledCashOutCategoryNames(context)
    }
    val cashInCategories = listOf(
        "Owner cash",
        "Other income",
        "Coin change",
        "Bank withdrawal",
        "Other"
    )
    val currentCategories = if (isCashIn) cashInCategories else cashOutCategories
    var selectedCategory by remember(isCashIn, currentCategories) {
        mutableStateOf(currentCategories.firstOrNull() ?: "Other")
    }

    var noteInput by remember { mutableStateOf("") }
    var attachedReceiptUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            attachedReceiptUri = uri
        }
    }

    val dateDisplay = remember(selectedDayMillis) {
        "Today, " + SimpleDateFormat("d MMM", Locale.ENGLISH).format(Date(selectedDayMillis))
    }
    val timeDisplay = remember {
        SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date())
    }

    val activeAccentColor = if (isCashIn) CbBrandGreen else CbCrimsonRed

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = FlatDarkCard,
            border = BorderStroke(1.dp, FlatDarkBorder),
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                // 1. Header: Add cash entry + Close X button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("Add cash entry", "कैश एंट्री जोड़ें"),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FlatTextPrimary
                    )
                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        color = CbNeutralPillBg,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("close_add_cash_entry_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = tr("Close", "बंद करें"),
                                tint = FlatTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 2. Mode Toggle: Cash in | Cash out
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CbChipUnselectedBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp)
                        ) {
                            Surface(
                                onClick = { isCashIn = true },
                                shape = RoundedCornerShape(11.dp),
                                color = if (isCashIn) CbBrandGreen else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("toggle_cash_in_tab")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 11.dp)
                                ) {
                                    Text(
                                        text = tr("Cash in", "कैश इन"),
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCashIn) Color.White else FlatTextPrimary
                                    )
                                }
                            }

                            Surface(
                                onClick = { isCashIn = false },
                                shape = RoundedCornerShape(11.dp),
                                color = if (!isCashIn) CbCrimsonRed else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("toggle_cash_out_tab")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 11.dp)
                                ) {
                                    Text(
                                        text = tr("Cash out", "कैश आउट"),
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!isCashIn) Color.White else FlatTextPrimary
                                    )
                                }
                            }
                        }
                    }

                    // 3. AMOUNT Large Box
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = tr("AMOUNT", "राशि"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = FlatDarkCard,
                            border = BorderStroke(1.5.dp, activeAccentColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "₹",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = activeAccentColor
                                )
                                BasicTextField(
                                    value = amountInput,
                                    onValueChange = { raw ->
                                        amountInput = raw.filter { it.isDigit() || it == '.' }.take(9)
                                    },
                                    textStyle = TextStyle(
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = activeAccentColor
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    cursorBrush = SolidColor(activeAccentColor),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("cash_entry_amount_input")
                                )
                            }
                        }
                    }

                    // 4. CATEGORY Chips
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = tr("CATEGORY", "श्रेणी"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            currentCategories.forEach { cat ->
                                val isSelected = selectedCategory == cat
                                Surface(
                                    onClick = { selectedCategory = cat },
                                    shape = RoundedCornerShape(50),
                                    color = if (isSelected) CbBrandGreen else CbChipUnselectedBg,
                                    modifier = Modifier.testTag("cash_cat_${cat.lowercase().replace(" ", "_")}")
                                ) {
                                    Text(
                                        text = cat,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else FlatTextPrimary,
                                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 5. NOTE (OPTIONAL)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = tr("NOTE (OPTIONAL)", "नोट (वैकल्पिक)"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        OutlinedTextField(
                            value = noteInput,
                            onValueChange = { noteInput = it },
                            placeholder = {
                                Text(
                                    text = tr("Tempo freight for feed delivery", "आहार डिलीवरी के लिए भाड़ा"),
                                    color = FlatTextMuted,
                                    fontSize = 14.5.sp
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = FlatDarkCard,
                                unfocusedContainerColor = FlatDarkCard,
                                focusedBorderColor = CbBrandGreen,
                                unfocusedBorderColor = FlatDarkBorder,
                                focusedTextColor = FlatTextPrimary,
                                unfocusedTextColor = FlatTextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cash_entry_note_input")
                        )
                    }

                    // 6. DATE & TIME Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = tr("DATE", "तारीख़"),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            OutlinedTextField(
                                value = dateDisplay,
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = FlatDarkCard,
                                    unfocusedContainerColor = FlatDarkCard,
                                    focusedBorderColor = FlatDarkBorder,
                                    unfocusedBorderColor = FlatDarkBorder,
                                    focusedTextColor = FlatTextPrimary,
                                    unfocusedTextColor = FlatTextPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = tr("TIME", "समय"),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            OutlinedTextField(
                                value = timeDisplay,
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = FlatDarkCard,
                                    unfocusedContainerColor = FlatDarkCard,
                                    focusedBorderColor = FlatDarkBorder,
                                    unfocusedBorderColor = FlatDarkBorder,
                                    focusedTextColor = FlatTextPrimary,
                                    unfocusedTextColor = FlatTextPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 7. Dashed "+ Attach photo of receipt" Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .drawBehind {
                                drawRoundRect(
                                    color = CbBrandGreen,
                                    style = Stroke(
                                        width = 1.5.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                                    ),
                                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                                )
                            }
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .testTag("attach_receipt_photo_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (attachedReceiptUri != null) {
                                tr("✓ Receipt photo attached", "✓ रसीद की फ़ोटो जुड़ गई")
                            } else {
                                tr("+ Attach photo of receipt", "+ रसीद की फ़ोटो जोड़ें")
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = CbBrandGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 8. Bottom Buttons: Cancel | Save cash out / Save cash in
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        contentPadding = PaddingValues(vertical = 13.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cancel_cash_entry_btn")
                    ) {
                        Text(
                            text = tr("Cancel", "रद्द करें"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                    }

                    Button(
                        onClick = {
                            val parsedAmt = amountInput.trim().toBigDecimalOrNull() ?: BigDecimal.ZERO
                            if (parsedAmt > BigDecimal.ZERO) {
                                onSaveEntry(
                                    isCashIn,
                                    parsedAmt,
                                    selectedCategory,
                                    noteInput.trim(),
                                    attachedReceiptUri?.toString()
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = activeAccentColor,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(vertical = 13.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("save_cash_entry_btn")
                    ) {
                        Text(
                            text = if (isCashIn) {
                                tr("Save cash in", "कैश इन सेव करें")
                            } else {
                                tr("Save cash out", "कैश आउट सेव करें")
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}
