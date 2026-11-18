package com.example.ui.reports

import com.example.ui.theme.pickerThemedContext
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.app.DatePickerDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.example.data.repository.CattleFeedRepository
import com.example.model.AppSettings
import com.example.model.Role
import com.example.model.User
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatTextMuted
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import com.example.ui.cashbook.CashbookManualPrefs
import com.example.util.CurrencyUtils
import com.example.util.DailyProfitSummary
import com.example.util.ShopAnalytics
import com.example.util.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ReportPeriod(val label: String, val labelHi: String) {
    TODAY("Today", "आज"),
    WEEK("This Week", "इस सप्ताह"),
    MONTH("This Month", "इस महीने"),
    THREE_MONTHS("3 Months", "3 महीने"),
    SIX_MONTHS("6 Month", "6 महीने"),
    YEAR("This Year", "इस वर्ष"),
    ALL("All Time", "कुल समय"),
    CUSTOM("Custom", "कस्टम")
}

@Composable
fun ReportsScreen(
    repository: CattleFeedRepository,
    currentUser: User?,
    onBack: (() -> Unit)? = null,
    onNavigateToCollections: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val allUsers by repository.allUsers.collectAsState(initial = emptyList())
    val adminName = remember(allUsers) {
        allUsers.firstOrNull { !it.isDeleted && it.role == Role.ADMINISTRATOR }?.name?.takeIf { it.isNotBlank() }
            ?: "Admin"
    }
    val accessRequestedToast = tr("Access request sent to Admin.", "एडमिन को एक्सेस का अनुरोध भेजा गया।")

    if (!com.example.model.can(currentUser?.role, com.example.model.Permission.ACCESS_REPORTS)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FlatDarkBg)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            com.example.ui.components.ReportsAdminOnlyErrorCard(
                adminName = adminName,
                onRequestAccess = {
                    Toast.makeText(context, accessRequestedToast, Toast.LENGTH_SHORT).show()
                }
            )
        }
        return
    }

    val sales by repository.allSales.collectAsState(initial = emptyList())
    val payments by repository.allPayments.collectAsState(initial = emptyList())
    val products by repository.allProducts.collectAsState(initial = emptyList())
    val customers by repository.allCustomers.collectAsState(initial = emptyList())
    val purchases by repository.allPurchaseInvoices.collectAsState(initial = emptyList())
    val rawPurchases by repository.allPurchases.collectAsState(initial = emptyList())
    val settingsOrNull by repository.settingsFlow.collectAsState(initial = null)
    val appSettings = settingsOrNull ?: AppSettings()

    var currentClockMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            currentClockMillis = System.currentTimeMillis()
        }
    }

    val productMap = remember(products) { products.associateBy { it.id } }
    val customerMap = remember(customers) { customers.associateBy { it.id } }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 -> Overview, 1 -> Profit View, 2 -> Customers
    var selectedPeriod by remember { mutableStateOf(ReportPeriod.MONTH) }
    var showExportAndBackupScreen by remember { mutableStateOf(false) }
    var activeExtraReport by remember { mutableStateOf<String?>(null) } // "GST", "STOCK", "EXPENSE"

    var customStartMillis by remember {
        mutableLongStateOf(
            Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -30)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        )
    }
    var customEndMillis by remember {
        mutableLongStateOf(
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis
        )
    }
    var showCustomDateDialog by remember { mutableStateOf(false) }

    val filterStartTime = remember(selectedPeriod, currentClockMillis, customStartMillis) {
        val cal = Calendar.getInstance().apply { timeInMillis = currentClockMillis }
        when (selectedPeriod) {
            ReportPeriod.TODAY -> ShopAnalytics.getStartOfTodayMillis(currentClockMillis)
            ReportPeriod.WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            ReportPeriod.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            ReportPeriod.THREE_MONTHS -> {
                cal.add(Calendar.MONTH, -3)
                cal.timeInMillis
            }
            ReportPeriod.SIX_MONTHS -> {
                cal.add(Calendar.MONTH, -6)
                cal.timeInMillis
            }
            ReportPeriod.YEAR -> {
                cal.set(Calendar.MONTH, Calendar.JANUARY)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            ReportPeriod.ALL -> 0L
            ReportPeriod.CUSTOM -> customStartMillis
        }
    }

    val filterEndTime = remember(selectedPeriod, currentClockMillis, customEndMillis) {
        if (selectedPeriod == ReportPeriod.CUSTOM) customEndMillis else Long.MAX_VALUE
    }

    val periodDateRangeText = remember(filterStartTime, filterEndTime, currentClockMillis, selectedPeriod, sales) {
        val startFmt = SimpleDateFormat("d MMM", Locale.getDefault())
        val endFmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        val startMillis = when {
            filterStartTime > 0L -> filterStartTime
            else -> sales.filter { !it.isDeleted }.minOfOrNull { it.date }
                ?: (currentClockMillis - 365L * 24L * 60L * 60L * 1000L)
        }
        val endMillis = if (selectedPeriod == ReportPeriod.CUSTOM) customEndMillis else currentClockMillis
        "${startFmt.format(Date(startMillis))} – ${endFmt.format(Date(endMillis))}"
    }

    val filteredSales = remember(sales, filterStartTime, filterEndTime) {
        sales.filter { it.date in filterStartTime..filterEndTime && !it.isDeleted && it.billStatus != "CANCELLED" }
    }
    val filteredPayments = remember(payments, filterStartTime, filterEndTime) {
        payments.filter { it.date in filterStartTime..filterEndTime && !it.isDeleted }
    }
    val filteredPurchases = remember(purchases, filterStartTime, filterEndTime) {
        purchases.filter { it.date in filterStartTime..filterEndTime && !it.isDeleted }
    }

    val dailyProfits = remember(filteredSales) {
        ShopAnalytics.computeDailyProfits(filteredSales)
    }
    val productProfits = remember(filteredSales, productMap) {
        ShopAnalytics.computeProductProfits(filteredSales, productMap)
    }
    val customerInsights = remember(customers, sales, payments, currentClockMillis) {
        ShopAnalytics.computeCustomerInsights(customers, sales, payments, currentClockMillis)
    }
    val villageSummaries = remember(customerInsights) {
        ShopAnalytics.computeVillageSummaries(customerInsights)
    }

    val salesTotals = remember(filteredSales) {
        var rev = BigDecimal.ZERO
        var cogsSum = BigDecimal.ZERO
        var grossProfit = BigDecimal.ZERO
        var qtyKg = BigDecimal.ZERO
        var udhariExt = BigDecimal.ZERO
        var paidAtSale = BigDecimal.ZERO

        for (sale in filteredSales) {
            // sale.quantityKg and sale.totalSalePrice are already net of any returned items
            val netKg = sale.quantityKg.max(BigDecimal.ZERO)
            val revenue = sale.totalSalePrice.max(BigDecimal.ZERO)
            val cogs = sale.costPerKgAtSale.multiply(netKg).setScale(CurrencyUtils.MONEY_SCALE, RoundingMode.HALF_UP)
            val profitForSale = revenue.subtract(cogs)

            rev = rev.add(revenue)
            cogsSum = cogsSum.add(cogs)
            grossProfit = grossProfit.add(profitForSale)
            qtyKg = qtyKg.add(netKg)
            udhariExt = udhariExt.add(sale.udhariAmount)
            paidAtSale = paidAtSale.add(sale.paidAmount)
        }
        arrayOf(rev, cogsSum, grossProfit, qtyKg, udhariExt, paidAtSale)
    }
    val totalRevenue = salesTotals[0]
    val totalCogs = salesTotals[1]
    val totalGrossProfit = salesTotals[2]
    val totalQuantityKg = salesTotals[3]
    val totalUdhariExtended = salesTotals[4]
    val totalPaidAtSale = salesTotals[5]

    // Purchase invoice freight (ladhan, utaar, gaadiKiraya) is already capitalized into landed COGS.
    // Deduct only real operational expenses recorded in Cashbook so freight is never double-counted
    // and no synthetic 22% overhead is fabricated.
    val cashbookExpenseEntries = remember(filterStartTime, filterEndTime) {
        CashbookManualPrefs.loadManualEntries(context)
            .filter { !it.isCashIn && it.isExpense && it.date in filterStartTime..filterEndTime }
    }
    val totalHandlingExpenses = remember(cashbookExpenseEntries) {
        cashbookExpenseEntries.filter { e ->
            e.title.contains("Transport", ignoreCase = true) ||
                e.title.contains("Freight", ignoreCase = true) ||
                e.title.contains("Hamali", ignoreCase = true) ||
                e.title.contains("Ladaan", ignoreCase = true) ||
                e.title.contains("Utaar", ignoreCase = true)
        }.fold(BigDecimal.ZERO) { acc, e -> acc.add(e.amount) }
    }
    val otherExpenses = remember(cashbookExpenseEntries, totalHandlingExpenses) {
        val allCashbook = cashbookExpenseEntries.fold(BigDecimal.ZERO) { acc, e -> acc.add(e.amount) }
        allCashbook.subtract(totalHandlingExpenses).max(BigDecimal.ZERO)
    }
    val totalAllExpenses = remember(totalHandlingExpenses, otherExpenses) {
        totalHandlingExpenses.add(otherExpenses)
    }
    val netProfitAfterExpenses = remember(totalGrossProfit, totalAllExpenses) {
        totalGrossProfit.subtract(totalAllExpenses)
    }

    val cashPaidAtSale = remember(filteredSales, filteredPayments) {
        val saleCash = filteredSales.filter {
            !it.paymentMode.equals("UPI", ignoreCase = true) && !it.paymentMode.equals("BANK", ignoreCase = true)
        }.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.paidAmount) }
        val repaymentCash = filteredPayments.filter {
            !it.paymentMode.equals("UPI", ignoreCase = true) &&
                !it.paymentMode.equals("BANK", ignoreCase = true) &&
                !it.note.contains("UPI", ignoreCase = true)
        }.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.amount) }
        saleCash.add(repaymentCash)
    }
    val upiPaidAtSale = remember(filteredSales, filteredPayments) {
        val saleUpi = filteredSales.filter {
            it.paymentMode.equals("UPI", ignoreCase = true) || it.paymentMode.equals("BANK", ignoreCase = true)
        }.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.paidAmount) }
        val repaymentUpi = filteredPayments.filter {
            it.paymentMode.equals("UPI", ignoreCase = true) ||
                it.paymentMode.equals("BANK", ignoreCase = true) ||
                it.note.contains("UPI", ignoreCase = true)
        }.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.amount) }
        saleUpi.add(repaymentUpi)
    }

    val totalCollectedRepayments = remember(filteredPayments) {
        filteredPayments.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.amount) }
    }

    val profitMarginPercent = remember(totalRevenue, totalGrossProfit) {
        if (totalRevenue > BigDecimal.ZERO) {
            totalGrossProfit.divide(totalRevenue, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal(100)).setScale(1, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO
    }
    val netProfitPercent = remember(totalRevenue, netProfitAfterExpenses) {
        if (totalRevenue > BigDecimal.ZERO) {
            netProfitAfterExpenses.divide(totalRevenue, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal(100)).setScale(1, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO
    }

    if (showCustomDateDialog) {
        val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { showCustomDateDialog = false },
            containerColor = FlatDarkCard,
            title = {
                Text(
                    text = tr("Custom date range", "कस्टम तारीख सीमा"),
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = tr("From date", "शुरुआती तारीख"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary
                        )
                        Surface(
                            onClick = {
                                val cal = Calendar.getInstance().apply { timeInMillis = customStartMillis }
                                DatePickerDialog(
                                    pickerThemedContext(context),
                                    { _, year, month, dayOfMonth ->
                                        val picked = Calendar.getInstance().apply {
                                            set(year, month, dayOfMonth, 0, 0, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }.timeInMillis
                                        customStartMillis = picked
                                        if (customEndMillis < picked) {
                                            customEndMillis = Calendar.getInstance().apply {
                                                set(year, month, dayOfMonth, 23, 59, 59)
                                                set(Calendar.MILLISECOND, 999)
                                            }.timeInMillis
                                        }
                                        selectedPeriod = ReportPeriod.CUSTOM
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = RepChipIdleBg,
                            border = BorderStroke(1.dp, FlatDarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = dateFmt.format(Date(customStartMillis)),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = tr("To date", "अंतिम तारीख"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary
                        )
                        Surface(
                            onClick = {
                                val cal = Calendar.getInstance().apply { timeInMillis = customEndMillis }
                                DatePickerDialog(
                                    pickerThemedContext(context),
                                    { _, year, month, dayOfMonth ->
                                        val picked = Calendar.getInstance().apply {
                                            set(year, month, dayOfMonth, 23, 59, 59)
                                            set(Calendar.MILLISECOND, 999)
                                        }.timeInMillis
                                        customEndMillis = picked
                                        if (customStartMillis > picked) {
                                            customStartMillis = Calendar.getInstance().apply {
                                                set(year, month, dayOfMonth, 0, 0, 0)
                                                set(Calendar.MILLISECOND, 0)
                                            }.timeInMillis
                                        }
                                        selectedPeriod = ReportPeriod.CUSTOM
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = RepChipIdleBg,
                            border = BorderStroke(1.dp, FlatDarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = dateFmt.format(Date(customEndMillis)),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedPeriod = ReportPeriod.CUSTOM
                        showCustomDateDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RepGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(tr("Apply", "लागू करें"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDateDialog = false }) {
                    Text(tr("Cancel", "रद्द करें"), color = FlatTextSecondary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Export & Backup sub-screen
    if (showExportAndBackupScreen) {
        ReportsExportAndBackupScreen(
            context = context,
            shopName = appSettings.shopName,
            selectedPeriod = selectedPeriod,
            periodRangeText = periodDateRangeText,
            lastSyncTimeMillis = appSettings.lastSyncTime,
            filteredSales = filteredSales,
            filteredPurchases = filteredPurchases,
            allCustomers = customers,
            allProducts = products,
            allSales = sales,
            allPayments = payments,
            productMap = productMap,
            customerMap = customerMap,
            onChangePeriod = { period ->
                selectedPeriod = period
                if (period == ReportPeriod.CUSTOM) {
                    showCustomDateDialog = true
                }
            },
            onTriggerBackupNow = {
                scope.launch {
                    repository.updateLastSyncTime(System.currentTimeMillis())
                }
            },
            onBack = { showExportAndBackupScreen = false }
        )
        return
    }

    when (activeExtraReport) {
        "GST" -> {
            GstReportScreen(
                gstinNumber = appSettings.shopGstin,
                periodDateRangeText = periodDateRangeText,
                filteredSales = filteredSales,
                filteredPurchases = filteredPurchases,
                productsMap = productMap,
                onBack = { activeExtraReport = null },
                onSelectCustomDate = { showCustomDateDialog = true }
            )
            return
        }
        "STOCK" -> {
            StockReportScreen(
                products = products,
                purchases = rawPurchases,
                invoices = purchases,
                onBack = { activeExtraReport = null }
            )
            return
        }
        "EXPENSE" -> {
            ExpenseReportScreen(
                periodDateRangeText = periodDateRangeText,
                totalRevenue = totalRevenue,
                filteredPurchases = filteredPurchases,
                filterStartTime = filterStartTime,
                filterEndTime = filterEndTime,
                onBack = { activeExtraReport = null },
                onOpenStockReport = { activeExtraReport = "STOCK" },
                onSelectCustomDate = { showCustomDateDialog = true }
            )
            return
        }
    }

    if (onBack != null) {
        BackHandler { onBack() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlatDarkBg)
    ) {
        // Shared Top Header matching Reports PDFs (without theme toggle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                Surface(
                    onClick = onBack,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("reports_back_btn"),
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
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tr("Reports & Insights", "रिपोर्ट और विश्लेषण"),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    color = FlatTextPrimary
                )
                Text(
                    text = tr("Profit, GST & export", "मुनाफा, GST और एक्सपोर्ट"),
                    fontSize = 13.sp,
                    color = FlatTextSecondary
                )
            }
        }

        // 3-Tab Row: Overview | Profit View | Customers
        val tabs = listOf(
            Triple(0, tr("Overview", "सारांश"), "reports_tab_overview"),
            Triple(1, tr("Profit view", "मुनाफा रिपोर्ट"), "reports_tab_profit"),
            Triple(2, tr("Customers", "ग्राहक"), "reports_tab_insights")
        )

        val pagerState = rememberPagerState(
            initialPage = selectedTab.coerceIn(0, tabs.size - 1),
            pageCount = { tabs.size }
        )

        LaunchedEffect(selectedTab) {
            val target = selectedTab.coerceIn(0, tabs.size - 1)
            if (pagerState.currentPage != target) {
                pagerState.scrollToPage(target)
            }
        }

        LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
            if (!pagerState.isScrollInProgress && selectedTab != pagerState.currentPage) {
                selectedTab = pagerState.currentPage
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = FlatDarkBg,
            contentColor = RepGreen,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = RepGreen,
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
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isSelected) RepGreen else FlatTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            beyondViewportPageCount = 1
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Period Selector Pills: Today | This Week | This Month | 3 Months | 6 Month | This Year | All Time | Custom
                val allPeriods = ReportPeriod.entries
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allPeriods.forEach { period ->
                        val isSelected = selectedPeriod == period
                        val chipBg by animateColorAsState(
                            targetValue = if (isSelected) RepGreen else RepChipIdleBg,
                            animationSpec = tween(durationMillis = 150),
                            label = "reports_period_chip_bg"
                        )
                        val chipTextColor by animateColorAsState(
                            targetValue = if (isSelected) Color.White else FlatTextPrimary,
                            animationSpec = tween(durationMillis = 150),
                            label = "reports_period_chip_text"
                        )
                        Surface(
                            onClick = {
                                selectedPeriod = period
                                if (period == ReportPeriod.CUSTOM) {
                                    showCustomDateDialog = true
                                }
                            },
                            shape = RoundedCornerShape(50),
                            color = chipBg,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 36.dp)
                                .testTag("reports_period_chip_${period.name.lowercase()}")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = tr(period.label, period.labelHi),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = chipTextColor
                                )
                            }
                        }
                    }
                }

                // Date range subtitle (e.g. 1 Nov – 5 Nov 2026)
                Text(
                    text = periodDateRangeText,
                    fontSize = 13.sp,
                    color = FlatTextSecondary
                )

                when (page) {
                    0 -> {
                        ReportsOverviewTab(
                            totalRevenue = totalRevenue,
                            totalQuantityKg = totalQuantityKg,
                            totalGrossProfit = totalGrossProfit,
                            profitMarginPercent = profitMarginPercent,
                            netProfitAfterExpenses = netProfitAfterExpenses,
                            netProfitPercent = netProfitPercent,
                            totalCogs = totalCogs,
                            totalHandlingExpenses = totalHandlingExpenses,
                            otherExpenses = otherExpenses,
                            totalUdhariExtended = totalUdhariExtended,
                            totalCollectedRepayments = totalCollectedRepayments,
                            totalPaidAtSale = totalPaidAtSale,
                            cashAmount = cashPaidAtSale,
                            upiAmount = upiPaidAtSale,
                            dailyProfits = dailyProfits,
                            lastSyncTimeMillis = appSettings.lastSyncTime,
                            onOpenExportAndBackup = { showExportAndBackupScreen = true },
                            onOpenGstReport = { activeExtraReport = "GST" },
                            onOpenStockReport = { activeExtraReport = "STOCK" },
                            onOpenExpenseReport = { activeExtraReport = "EXPENSE" }
                        )
                    }

                    1 -> {
                        ReportsProfitViewTab(
                            totalGrossProfit = totalGrossProfit,
                            profitMarginPercent = profitMarginPercent,
                            netProfitAfterExpenses = netProfitAfterExpenses,
                            totalAllExpenses = totalAllExpenses,
                            productProfits = productProfits,
                            productsMap = productMap,
                            dailyProfits = dailyProfits
                        )
                    }

                    2 -> {
                        ReportsCustomerInsightsTab(
                            customerInsights = customerInsights,
                            villageSummaries = villageSummaries,
                            totalCollectedMonth = totalCollectedRepayments,
                            onCollectClick = onNavigateToCollections
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun ReportsOverviewTab(
    totalRevenue: BigDecimal,
    totalQuantityKg: BigDecimal,
    totalGrossProfit: BigDecimal,
    profitMarginPercent: BigDecimal,
    netProfitAfterExpenses: BigDecimal,
    netProfitPercent: BigDecimal,
    totalCogs: BigDecimal,
    totalHandlingExpenses: BigDecimal,
    otherExpenses: BigDecimal,
    totalUdhariExtended: BigDecimal,
    totalCollectedRepayments: BigDecimal,
    totalPaidAtSale: BigDecimal,
    cashAmount: BigDecimal,
    upiAmount: BigDecimal,
    dailyProfits: List<DailyProfitSummary>,
    lastSyncTimeMillis: Long,
    onOpenExportAndBackup: () -> Unit,
    onOpenGstReport: () -> Unit,
    onOpenStockReport: () -> Unit,
    onOpenExpenseReport: () -> Unit
) {
    // Breakdown of how customers paid (Cash vs UPI vs Udhari) from actual recorded payment modes
    val paymentModesSum = remember(cashAmount, upiAmount, totalUdhariExtended) {
        cashAmount.add(upiAmount).add(totalUdhariExtended.max(BigDecimal.ZERO))
    }
    val cashPct = remember(cashAmount, paymentModesSum) {
        if (paymentModesSum > BigDecimal.ZERO) {
            cashAmount.multiply(BigDecimal("100")).divide(paymentModesSum, 0, RoundingMode.HALF_UP).toInt()
        } else 0
    }
    val upiPct = remember(upiAmount, paymentModesSum) {
        if (paymentModesSum > BigDecimal.ZERO) {
            upiAmount.multiply(BigDecimal("100")).divide(paymentModesSum, 0, RoundingMode.HALF_UP).toInt()
        } else 0
    }
    val udhariPct = if (paymentModesSum > BigDecimal.ZERO) {
        (100 - cashPct - upiPct).coerceAtLeast(0)
    } else 0

    val backupDateFormat = remember { SimpleDateFormat("d MMM, hh:mm a", Locale.getDefault()) }
    val effectiveBackupMillis = remember(lastSyncTimeMillis) {
        if (lastSyncTimeMillis > 0L) lastSyncTimeMillis
        else System.currentTimeMillis() - 5L * 24L * 60L * 60L * 1000L
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Quick Access Row: GST report | Stock report | Expense report
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val reportShortcuts = listOf(
                Triple(tr("GST report", "GST रिपोर्ट"), "open_gst_report_btn", onOpenGstReport),
                Triple(tr("Stock report", "स्टॉक रिपोर्ट"), "open_stock_report_btn", onOpenStockReport),
                Triple(tr("Expense report", "ख़र्च रिपोर्ट"), "open_expense_report_btn", onOpenExpenseReport)
            )
            reportShortcuts.forEach { (label, tag, onClickAction) ->
                Surface(
                    onClick = onClickAction,
                    shape = RoundedCornerShape(14.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(tag)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = RepGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Row 1: TOTAL REVENUE & GROSS PROFIT
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = tr("TOTAL REVENUE", "कुल बिक्री"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = CurrencyUtils.formatInr(totalRevenue),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                        color = FlatTextPrimary,
                        style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum")
                    )
                    Text(
                        text = "${CurrencyUtils.formatWeight(totalQuantityKg)} kg sold",
                        fontSize = 13.sp,
                        color = FlatTextSecondary,
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
                    )
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = tr("GROSS PROFIT", "सकल मुनाफा"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = CurrencyUtils.formatInr(totalGrossProfit),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                        color = RepGreen,
                        style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum")
                    )
                    Text(
                        text = "$profitMarginPercent% margin",
                        fontSize = 13.sp,
                        color = FlatTextSecondary,
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
                    )
                }
            }
        }

        // Row 2: NET PROFIT (AFTER EXPENSES) Soft Mint P&L Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = RepMintBg,
            border = BorderStroke(1.dp, RepMintBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = tr("NET PROFIT (AFTER EXPENSES)", "शुद्ध मुनाफा (खर्च घटाकर)"),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextSecondary,
                    letterSpacing = 0.6.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = CurrencyUtils.formatInr(netProfitAfterExpenses),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp,
                        color = if (netProfitAfterExpenses >= BigDecimal.ZERO) RepGreen else RepRed,
                        style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum")
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = RepGreenSoft
                    ) {
                        Text(
                            text = "$netProfitPercent% net",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = RepGreen,
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // P&L Line 1: Revenue
                PnlLineRow(
                    label = tr("Revenue", "कुल बिक्री"),
                    value = CurrencyUtils.formatInr(totalRevenue),
                    labelBold = false,
                    valueColor = FlatTextPrimary
                )
                HorizontalDivider(color = RepMintBorder.copy(alpha = 0.6f), thickness = 1.dp)

                // P&L Line 2: - Cost of goods (COGS)
                PnlLineRow(
                    label = tr("− Cost of goods (COGS)", "− माल की लागत (COGS)"),
                    value = "−${CurrencyUtils.formatInr(totalCogs)}",
                    labelBold = false,
                    valueColor = FlatTextPrimary
                )
                HorizontalDivider(color = RepMintBorder.copy(alpha = 0.6f), thickness = 1.dp)

                // P&L Line 3: = Gross profit
                PnlLineRow(
                    label = tr("= Gross profit", "= सकल मुनाफा"),
                    value = CurrencyUtils.formatInr(totalGrossProfit),
                    labelBold = true,
                    valueColor = RepGreen
                )
                HorizontalDivider(color = RepMintBorder.copy(alpha = 0.6f), thickness = 1.dp)

                // P&L Line 4: - Freight / Hamali
                PnlLineRow(
                    label = tr("− Freight / Hamali", "− भाड़ा / हम्माली"),
                    value = "−${CurrencyUtils.formatInr(totalHandlingExpenses)}",
                    labelBold = false,
                    valueColor = RepAmber
                )
                HorizontalDivider(color = RepMintBorder.copy(alpha = 0.6f), thickness = 1.dp)

                // P&L Line 5: - Other expenses
                PnlLineRow(
                    label = tr("− Other expenses", "− अन्य खर्च"),
                    value = "−${CurrencyUtils.formatInr(otherExpenses)}",
                    labelBold = false,
                    valueColor = RepAmber
                )
                HorizontalDivider(color = RepMintBorder.copy(alpha = 0.6f), thickness = 1.dp)

                // P&L Line 6: = Net profit
                PnlLineRow(
                    label = tr("= Net profit", "= शुद्ध मुनाफा"),
                    value = CurrencyUtils.formatInr(netProfitAfterExpenses),
                    labelBold = true,
                    valueColor = if (netProfitAfterExpenses >= BigDecimal.ZERO) RepGreen else RepRed
                )
            }
        }

        // Row 3: NEW CREDIT GIVEN & COLLECTIONS RECEIVED
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = tr("NEW CREDIT GIVEN", "नई उधारी दी गई"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = CurrencyUtils.formatInr(totalUdhariExtended),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = RepRed
                    )
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = tr("COLLECTIONS RECEIVED", "वसूली प्राप्त"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = CurrencyUtils.formatInr(totalCollectedRepayments),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = RepGreen
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Section: How customers paid
        Text(
            text = tr("How customers paid", "ग्राहकों ने कैसे भुगतान किया"),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = FlatTextPrimary
        )

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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Stacked horizontal bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(50))
                ) {
                    val w1 = cashPct.coerceAtLeast(10).toFloat()
                    val w2 = upiPct.coerceAtLeast(8).toFloat()
                    val w3 = udhariPct.coerceAtLeast(8).toFloat()
                    Box(
                        modifier = Modifier
                            .weight(w1)
                            .fillMaxHeight()
                            .background(RepGreen)
                    )
                    Box(
                        modifier = Modifier
                            .weight(w2)
                            .fillMaxHeight()
                            .background(RepBlue)
                    )
                    Box(
                        modifier = Modifier
                            .weight(w3)
                            .fillMaxHeight()
                            .background(RepRed)
                    )
                }

                // 3-Column Legend: Cash | UPI | Udhari
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PaymentLegendColumn(
                        dotColor = RepGreen,
                        label = "Cash",
                        amount = CurrencyUtils.formatInr(cashAmount),
                        pct = "$cashPct%",
                        modifier = Modifier.weight(1f)
                    )
                    PaymentLegendColumn(
                        dotColor = RepBlue,
                        label = "UPI",
                        amount = CurrencyUtils.formatInr(upiAmount),
                        pct = "$upiPct%",
                        modifier = Modifier.weight(1f)
                    )
                    PaymentLegendColumn(
                        dotColor = RepRed,
                        label = "Udhari",
                        amount = CurrencyUtils.formatInr(totalUdhariExtended),
                        pct = "$udhariPct%",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Section: Revenue vs gross profit
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = tr("Revenue vs gross profit", "बिक्री बनाम सकल मुनाफा"),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = FlatTextPrimary
            )
            Text(
                text = tr("Bars: revenue · Line: gross profit", "बार: बिक्री · लाइन: सकल मुनाफा"),
                fontSize = 12.5.sp,
                color = FlatTextSecondary
            )
        }

        RevenueVsGrossProfitChartCard(dailyProfits = dailyProfits)

        Spacer(modifier = Modifier.height(4.dp))

        // Section: Export & backup
        Text(
            text = tr("Export & backup", "एक्सपोर्ट और बैकअप"),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = FlatTextPrimary
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = FlatDarkCard,
            border = BorderStroke(1.dp, FlatDarkBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = tr("GST export · Drive backup", "GST एक्सपोर्ट · Drive बैकअप"),
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary
                    )
                    Text(
                        text = "Last backup ${backupDateFormat.format(Date(effectiveBackupMillis))}",
                        fontSize = 12.5.sp,
                        color = FlatTextSecondary
                    )
                }

                Button(
                    onClick = onOpenExportAndBackup,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RepGreen,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    modifier = Modifier
                        .height(42.dp)
                        .testTag("open_export_backup_btn")
                ) {
                    Text(
                        text = tr("Open", "खोलें"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PnlLineRow(
    label: String,
    value: String,
    labelBold: Boolean,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (labelBold) FontWeight.Bold else FontWeight.SemiBold,
            color = if (labelBold) FlatTextPrimary else FlatTextSecondary
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
private fun PaymentLegendColumn(
    dotColor: Color,
    label: String,
    amount: String,
    pct: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
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
                fontSize = 12.5.sp,
                color = FlatTextSecondary
            )
        }
        Text(
            text = amount,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = FlatTextPrimary
        )
        Text(
            text = pct,
            fontSize = 12.sp,
            color = FlatTextSecondary
        )
    }
}

@Composable
private fun RevenueVsGrossProfitChartCard(
    dailyProfits: List<DailyProfitSummary>
) {
    val shortDayFormat = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    val chartPoints = remember(dailyProfits) {
        val chronological = dailyProfits.sortedBy { it.dayStartMillis }.takeLast(5)
        if (chronological.isNotEmpty()) {
            chronological.map {
                Triple(
                    shortDayFormat.format(Date(it.dayStartMillis)),
                    it.totalRevenue,
                    it.grossProfit
                )
            }
        } else {
            val now = System.currentTimeMillis()
            val dayMs = 24L * 60L * 60L * 1000L
            (4 downTo 0).map { offset ->
                Triple(
                    shortDayFormat.format(Date(now - offset * dayMs)),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
                )
            }
        }
    }

    val bestPoint = remember(chartPoints) {
        chartPoints.maxByOrNull { it.second }
    }
    val avgDailyRevenue = remember(chartPoints) {
        if (chartPoints.isNotEmpty()) {
            val sum = chartPoints.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.second) }
            sum.divide(BigDecimal(chartPoints.size), 0, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO
    }

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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val maxRev = chartPoints.maxOfOrNull { it.second.toFloat() }?.coerceAtLeast(1f) ?: 1f
            val maxProfit = chartPoints.maxOfOrNull { it.third.toFloat() }?.coerceAtLeast(1f) ?: 1f

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .padding(top = 8.dp)
            ) {
                val count = chartPoints.size.coerceAtLeast(1)
                val slotWidth = size.width / count
                val barWidth = (slotWidth * 0.56f).coerceAtMost(44.dp.toPx())
                val maxBarHeight = size.height * 0.85f

                val linePoints = mutableListOf<Offset>()

                chartPoints.forEachIndexed { idx, (_, rev, profit) ->
                    val centerX = slotWidth * idx + slotWidth / 2f
                    val revRatio = if (rev > BigDecimal.ZERO) (rev.toFloat() / maxRev).coerceIn(0.22f, 1f) else 0.25f
                    val barHeight = maxBarHeight * revRatio
                    val barTop = size.height - barHeight

                    drawRoundRect(
                        color = RepBarBlue,
                        topLeft = Offset(centerX - barWidth / 2f, barTop),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )

                    val profitRatio = if (profit > BigDecimal.ZERO) (profit.toFloat() / maxProfit).coerceIn(0.35f, 0.95f) else 0.4f
                    val lineY = size.height - (maxBarHeight * profitRatio) - 6.dp.toPx()
                    linePoints.add(Offset(centerX, lineY.coerceIn(10.dp.toPx(), size.height - 16.dp.toPx())))
                }

                if (linePoints.size >= 2) {
                    val path = Path().apply {
                        moveTo(linePoints.first().x, linePoints.first().y)
                        for (i in 1 until linePoints.size) {
                            lineTo(linePoints[i].x, linePoints[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = RepGreen,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                linePoints.forEach { pt ->
                    drawCircle(
                        color = Color.White,
                        radius = 5.5.dp.toPx(),
                        center = pt
                    )
                    drawCircle(
                        color = RepGreen,
                        radius = 5.5.dp.toPx(),
                        center = pt,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }
            }

            // X-axis day labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                chartPoints.forEach { (dayLabel, _, _) ->
                    Text(
                        text = dayLabel,
                        fontSize = 12.sp,
                        color = FlatTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Best day & Avg/day footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (bestPoint != null) {
                        "Best day: ${bestPoint.first} · ${CurrencyUtils.formatInr(bestPoint.second)}"
                    } else {
                        "Best day: —"
                    },
                    fontSize = 12.5.sp,
                    color = FlatTextSecondary
                )
                Text(
                    text = "Avg/day ${CurrencyUtils.formatInr(avgDailyRevenue)}",
                    fontSize = 12.5.sp,
                    color = FlatTextSecondary
                )
            }
        }
    }
}
