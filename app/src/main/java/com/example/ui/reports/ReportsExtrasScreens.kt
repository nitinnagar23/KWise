package com.example.ui.reports

import com.example.ui.theme.kw
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Product
import com.example.model.PurchaseEntry
import com.example.model.PurchaseInvoice
import com.example.model.SaleEntry
import com.example.ui.cashbook.CashbookEntry
import com.example.ui.cashbook.CashbookManualPrefs
import com.example.ui.cashbook.ExpenseCategoryStore
import com.example.ui.inventory.InventoryFilter
import com.example.ui.inventory.InventoryItemMeta
import com.example.ui.inventory.InventoryMetaPrefs
import com.example.ui.inventory.InventoryStockStatus
import com.example.ui.inventory.computeStockStatus
import com.example.ui.inventory.formatProductCardTitle
import com.example.ui.inventory.formatProductQuantityLabel
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatTextMuted
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import com.example.util.CurrencyUtils
import com.example.util.tr
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val RepExtraGreen: Color get() = kw(0xFF0E6F43)
private val RepExtraMintBg: Color get() = kw(0xFFDDF3E8)
private val RepExtraMintBorder: Color get() = kw(0xFFA7DFC2)
private val RepExtraRed: Color get() = kw(0xFFB91C1C)
private val RepExtraAmber: Color get() = kw(0xFFB45309)
private val RepExtraBlueInfoBg: Color get() = kw(0xFFE0F2FE)
private val RepExtraBlueInfoText: Color get() = kw(0xFF075985)
private val RepExtraChipIdle: Color get() = kw(0xFFF1F1F7)
enum class ExtraReportPeriod(val labelEn: String, val labelHi: String) {
    THIS_MONTH("This Month", "इस महीने"),
    LAST_MONTH("Last Month", "पिछले महीने"),
    THIS_QUARTER("This Quarter", "इस तिमाही"),
    THIS_YEAR("This Year", "इस वर्ष"),
    CUSTOM("Custom", "कस्टम")
}

// ============================================================================
// PAGE 13: GST report screen
// ============================================================================
@Composable
fun GstReportScreen(
    gstinNumber: String,
    periodDateRangeText: String,
    filteredSales: List<SaleEntry>,
    filteredPurchases: List<PurchaseInvoice>,
    productsMap: Map<String, Product>,
    onBack: () -> Unit,
    onSelectCustomDate: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    var selectedPeriod by remember { mutableStateOf(ExtraReportPeriod.THIS_MONTH) }

    val groupedBills = remember(filteredSales) {
        com.example.model.groupSalesIntoBills(
            filteredSales.filter { !it.isDeleted && it.billStatus != "CANCELLED" },
            descending = true
        )
    }

    // Partition sales into 5% HSN 2309 (branded/bagged feed) vs Nil rated (loose/unbranded)
    val (taxable2309, nilRatedAmount) = remember(filteredSales, productsMap) {
        var hsn2309 = BigDecimal.ZERO
        var nilRated = BigDecimal.ZERO
        filteredSales.forEach { sale ->
            val rev = sale.totalSalePrice.max(BigDecimal.ZERO)
            val prod = productsMap[sale.productId]
            val pName = prod?.feedName ?: ""
            val isNil = prod?.isLooseOnly == true ||
                pName.contains("Gud", ignoreCase = true) ||
                pName.contains("Salt", ignoreCase = true)
            if (isNil) {
                nilRated = nilRated.add(rev)
            } else {
                hsn2309 = hsn2309.add(rev)
            }
        }
        hsn2309.setScale(0, RoundingMode.HALF_UP) to nilRated.setScale(0, RoundingMode.HALF_UP)
    }

    val totalTaxableSales = remember(taxable2309, nilRatedAmount) {
        taxable2309.add(nilRatedAmount)
    }

    // 5% GST on HSN 2309 (2.5% CGST + 2.5% SGST)
    val cgstCollected = remember(taxable2309) {
        taxable2309.multiply(BigDecimal("0.025")).setScale(0, RoundingMode.HALF_UP)
    }
    val sgstCollected = remember(taxable2309) {
        taxable2309.multiply(BigDecimal("0.025")).setScale(0, RoundingMode.HALF_UP)
    }
    val totalGstCollected = remember(cgstCollected, sgstCollected) {
        cgstCollected.add(sgstCollected)
    }

    val inputTaxOnPurchases = remember(filteredPurchases) {
        val totalPurchaseVal = filteredPurchases.fold(BigDecimal.ZERO) { acc, inv ->
            acc.add(inv.totalInvoiceCost)
        }
        totalPurchaseVal.multiply(BigDecimal("0.038")).setScale(0, RoundingMode.HALF_UP)
    }

    val netGstPayable = remember(totalGstCollected, inputTaxOnPurchases) {
        totalGstCollected.subtract(inputTaxOnPurchases).max(BigDecimal.ZERO)
    }

    val effectiveGstin = gstinNumber.ifBlank { "23AAACR5055K1Z7" }

    Scaffold(
        containerColor = FlatDarkBg,
        bottomBar = {
            ExportBottomBar(
                onExportCsv = {
                    sharePlainReport(
                        context = context,
                        title = "GST Report ($periodDateRangeText)",
                        body = buildString {
                            appendLine("GSTIN: $effectiveGstin")
                            appendLine("Taxable Sales: ${CurrencyUtils.formatInr(totalTaxableSales)} (${groupedBills.size} bills)")
                            appendLine("GST Collected: ${CurrencyUtils.formatInr(totalGstCollected)} (CGST ${CurrencyUtils.formatInr(cgstCollected)}, SGST ${CurrencyUtils.formatInr(sgstCollected)})")
                            appendLine("Input Tax on Purchases: ${CurrencyUtils.formatInr(inputTaxOnPurchases)} (${filteredPurchases.size} supplier bills)")
                            appendLine("Net GST Payable: ${CurrencyUtils.formatInr(netGstPayable)}")
                        }
                    )
                },
                onExportPdf = {
                    sharePlainReport(
                        context = context,
                        title = "GST Report PDF ($periodDateRangeText)",
                        body = "GST Report ($effectiveGstin) — Net GST Payable: ${CurrencyUtils.formatInr(netGstPayable)}"
                    )
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        onClick = onBack,
                        shape = RoundedCornerShape(12.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("gst_report_back_btn")
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
                            text = tr("GST report", "GST रिपोर्ट"),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                        Text(
                            text = "GSTR-1 & 3B summary · GSTIN $effectiveGstin",
                            fontSize = 12.5.sp,
                            color = FlatTextSecondary
                        )
                    }
                }
            }

            // 2. Period Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExtraReportPeriod.entries.forEach { period ->
                        val isSelected = selectedPeriod == period
                        Surface(
                            onClick = {
                                selectedPeriod = period
                                if (period == ExtraReportPeriod.CUSTOM) {
                                    onSelectCustomDate()
                                }
                            },
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) RepExtraGreen else RepExtraChipIdle,
                            modifier = Modifier.testTag("gst_period_${period.name.lowercase()}")
                        ) {
                            Text(
                                text = tr(period.labelEn, period.labelHi),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else FlatTextPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // 3. Date range subtitle
            item {
                Text(
                    text = periodDateRangeText,
                    fontSize = 13.sp,
                    color = FlatTextSecondary
                )
            }

            // 4. Row 1: TAXABLE SALES & GST COLLECTED
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = tr("TAXABLE SALES", "कर योग्य बिक्री"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = CurrencyUtils.formatInr(totalTaxableSales),
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = "${groupedBills.size} bills",
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = tr("GST COLLECTED", "एकत्रित GST"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = CurrencyUtils.formatInr(totalGstCollected),
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                color = RepExtraGreen
                            )
                            Text(
                                text = "CGST ${CurrencyUtils.formatInr(cgstCollected)} · SGST ${CurrencyUtils.formatInr(sgstCollected)}",
                                fontSize = 11.5.sp,
                                color = FlatTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 5. Row 2: INPUT TAX ON PURCHASES & NET GST PAYABLE
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = tr("INPUT TAX ON PURCHASES", "खरीद पर इनपुट टैक्स"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.4.sp
                            )
                            Text(
                                text = CurrencyUtils.formatInr(inputTaxOnPurchases),
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = "${filteredPurchases.size} supplier bills",
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = RepExtraMintBg,
                        border = BorderStroke(1.dp, RepExtraMintBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = tr("NET GST PAYABLE", "देय शुद्ध GST"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RepExtraGreen,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = CurrencyUtils.formatInr(netGstPayable),
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                color = RepExtraGreen
                            )
                            Text(
                                text = tr("Output minus input", "आउटपुट घटाव इनपुट"),
                                fontSize = 12.5.sp,
                                color = RepExtraGreen
                            )
                        }
                    }
                }
            }

            // 6. HSN-WISE SUMMARY Table Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = tr("HSN-WISE SUMMARY", "HSN-वार सारांश"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.5.sp
                        )

                        // Table Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "HSN · RATE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                modifier = Modifier.weight(1.45f)
                            )
                            Text(
                                text = "TAXABLE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.95f)
                            )
                            Text(
                                text = "CGST",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.75f)
                            )
                            Text(
                                text = "SGST",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.75f)
                            )
                        }

                        HorizontalDivider(color = FlatDarkBorder)

                        // Row 1: 2309 · 5%
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1.45f)) {
                                Text(
                                    text = "2309 · 5%",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary
                                )
                                Text(
                                    text = "Pashu aahar, khali, chokar",
                                    fontSize = 11.5.sp,
                                    color = FlatTextSecondary
                                )
                            }
                            Text(
                                text = CurrencyUtils.formatInr(taxable2309),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.95f)
                            )
                            Text(
                                text = CurrencyUtils.formatInr(cgstCollected),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.75f)
                            )
                            Text(
                                text = CurrencyUtils.formatInr(sgstCollected),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.75f)
                            )
                        }

                        HorizontalDivider(color = FlatDarkBorder.copy(alpha = 0.6f))

                        // Row 2: Nil rated
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1.45f)) {
                                Text(
                                    text = "Nil rated",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary
                                )
                                Text(
                                    text = "Gud, salt, unbranded grain",
                                    fontSize = 11.5.sp,
                                    color = FlatTextSecondary
                                )
                            }
                            Text(
                                text = CurrencyUtils.formatInr(nilRatedAmount),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.95f)
                            )
                            Text(
                                text = "₹0",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.75f)
                            )
                            Text(
                                text = "₹0",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.75f)
                            )
                        }

                        HorizontalDivider(color = FlatDarkBorder)

                        // Total Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("Total", "कुल"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary,
                                modifier = Modifier.weight(1.45f)
                            )
                            Text(
                                text = CurrencyUtils.formatInr(totalTaxableSales),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.95f)
                            )
                            Text(
                                text = CurrencyUtils.formatInr(cgstCollected),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.75f)
                            )
                            Text(
                                text = CurrencyUtils.formatInr(sgstCollected),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.75f)
                            )
                        }
                    }
                }
            }

            // 7. Info Banner
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = RepExtraBlueInfoBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = tr(
                            "Most cattle feed is Nil or 5% GST. Share this summary with your CA before filing.",
                            "अधिकांश पशु आहार पर Nil या 5% GST लगता है। रिटर्न भरने से पहले यह सारांश अपने CA के साथ साझा करें।"
                        ),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RepExtraBlueInfoText,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

// ============================================================================
// PAGE 14: Stock report screen
// ============================================================================
@Composable
fun StockReportScreen(
    products: List<Product>,
    purchases: List<PurchaseEntry>,
    invoices: List<PurchaseInvoice>,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    var selectedFilter by remember { mutableStateOf(InventoryFilter.ALL) }

    val metaMap = remember(products, purchases, invoices) {
        products.associate { prod ->
            prod.id to InventoryMetaPrefs.loadMeta(context, prod, purchases, invoices)
        }
    }

    val statusByProductId = remember(products, metaMap) {
        products.associate { prod ->
            val meta = metaMap[prod.id] ?: InventoryItemMeta()
            prod.id to computeStockStatus(prod, meta)
        }
    }

    val filteredProducts = remember(products, statusByProductId, selectedFilter) {
        products.filter { prod ->
            val status = statusByProductId[prod.id] ?: InventoryStockStatus.IN_STOCK
            when (selectedFilter) {
                InventoryFilter.ALL -> true
                InventoryFilter.LOW -> status == InventoryStockStatus.LOW
                InventoryFilter.OUT -> status == InventoryStockStatus.OUT_OF_STOCK
                InventoryFilter.BAGGED -> !prod.isLooseOnly && prod.kgPerBag > BigDecimal.ONE
                InventoryFilter.LOOSE -> prod.isLooseOnly || prod.kgPerBag <= BigDecimal.ONE
            }
        }
    }

    val totalCostValue = remember(products) {
        products.fold(BigDecimal.ZERO) { acc, prod ->
            val stockKg = prod.currentStockKg.max(BigDecimal.ZERO)
            val costPerKg = if (prod.averagePurchaseCostPerKg > BigDecimal.ZERO) {
                prod.averagePurchaseCostPerKg
            } else {
                prod.sellingPricePerKg
            }
            acc.add(stockKg.multiply(costPerKg))
        }.setScale(0, RoundingMode.HALF_UP)
    }

    val totalSellingValue = remember(products) {
        products.fold(BigDecimal.ZERO) { acc, prod ->
            val stockKg = prod.currentStockKg.max(BigDecimal.ZERO)
            val sellPerKg = if (prod.sellingPricePerKg > BigDecimal.ZERO) {
                prod.sellingPricePerKg
            } else {
                prod.looseSellingPricePerKg
            }
            acc.add(stockKg.multiply(sellPerKg))
        }.setScale(0, RoundingMode.HALF_UP)
    }

    val potentialProfit = remember(totalSellingValue, totalCostValue) {
        totalSellingValue.subtract(totalCostValue).max(BigDecimal.ZERO)
    }

    val marginPct = remember(potentialProfit, totalCostValue) {
        if (totalCostValue > BigDecimal.ZERO) {
            potentialProfit.multiply(BigDecimal(100)).divide(totalCostValue, 1, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }
    }

    val stockLossThisMonth = remember(products) {
        val monthStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
        }.timeInMillis
        val allAdj = InventoryMetaPrefs.loadAllAdjustmentsPublic(context)
            .filter { it.timestamp >= monthStart && it.deltaKg < BigDecimal.ZERO }
        allAdj.fold(BigDecimal.ZERO) { acc, adj ->
            if (adj.stockLossCost > BigDecimal.ZERO) {
                acc.add(adj.stockLossCost)
            } else {
                val p = products.find { it.id == adj.productId }
                val costKg = p?.averagePurchaseCostPerKg ?: BigDecimal.ZERO
                acc.add(adj.deltaKg.abs().multiply(costKg).setScale(0, RoundingMode.HALF_UP))
            }
        }
    }

    Scaffold(
        containerColor = FlatDarkBg,
        bottomBar = {
            ExportBottomBar(
                onExportCsv = {
                    sharePlainReport(
                        context = context,
                        title = "Stock Valuation Report",
                        body = buildString {
                            appendLine("Items: ${products.size}")
                            appendLine("Cost Value: ${CurrencyUtils.formatInr(totalCostValue)}")
                            appendLine("Selling Value: ${CurrencyUtils.formatInr(totalSellingValue)}")
                            appendLine("Potential Profit: ${CurrencyUtils.formatInr(potentialProfit)} ($marginPct% margin)")
                        }
                    )
                },
                onExportPdf = {
                    sharePlainReport(
                        context = context,
                        title = "Stock Report PDF",
                        body = "Stock Report — Cost: ${CurrencyUtils.formatInr(totalCostValue)} | Selling: ${CurrencyUtils.formatInr(totalSellingValue)}"
                    )
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        onClick = onBack,
                        shape = RoundedCornerShape(12.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("stock_report_back_btn")
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
                            text = tr("Stock report", "स्टॉक रिपोर्ट"),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                        Text(
                            text = "${products.size} items · Valuation & movement",
                            fontSize = 13.sp,
                            color = FlatTextSecondary
                        )
                    }
                }
            }

            // 2. KPI Cards: COST VALUE | SELLING VALUE
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = tr("COST VALUE", "लागत मूल्य"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = CurrencyUtils.formatInr(totalCostValue),
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = tr("At purchase price", "खरीद मूल्य पर"),
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = tr("SELLING VALUE", "बिक्री मूल्य"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = CurrencyUtils.formatInr(totalSellingValue),
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                color = RepExtraGreen
                            )
                            Text(
                                text = tr("At today's rates", "आज के भाव पर"),
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary
                            )
                        }
                    }
                }
            }

            // 3. Profit if all stock is sold Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = RepExtraMintBg,
                    border = BorderStroke(1.dp, RepExtraMintBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = tr("Profit if all stock is sold", "सारा स्टॉक बिकने पर मुनाफा"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = RepExtraGreen
                            )
                            Text(
                                text = "Selling minus cost · $marginPct% margin",
                                fontSize = 12.5.sp,
                                color = RepExtraGreen.copy(alpha = 0.85f)
                            )
                        }

                        Text(
                            text = CurrencyUtils.formatInr(potentialProfit),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = RepExtraGreen
                        )
                    }
                }
            }

            // 4. Filter Chips: All | Low | Out | Bagged | Loose
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InventoryFilter.entries.forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Surface(
                            onClick = { selectedFilter = filter },
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) RepExtraGreen else RepExtraChipIdle,
                            modifier = Modifier.testTag("stock_report_filter_${filter.name.lowercase()}")
                        ) {
                            Text(
                                text = tr(filter.labelEn, filter.labelHi),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else FlatTextPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // 5. ITEM | STOCK | COST | SELL Table Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ITEM",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                modifier = Modifier.weight(1.4f)
                            )
                            Text(
                                text = "STOCK",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.75f)
                            )
                            Text(
                                text = "COST",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.85f)
                            )
                            Text(
                                text = "SELL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.85f)
                            )
                        }

                        HorizontalDivider(color = FlatDarkBorder)

                        if (filteredProducts.isEmpty()) {
                            Text(
                                text = tr("No items match this filter.", "इस फ़िल्टर में कोई आइटम नहीं है।"),
                                fontSize = 13.5.sp,
                                color = FlatTextMuted,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            var totalFilteredBags = 0
                            var totalFilteredLooseKg = BigDecimal.ZERO
                            var sumFilteredCost = BigDecimal.ZERO
                            var sumFilteredSell = BigDecimal.ZERO

                            filteredProducts.forEachIndexed { idx, prod ->
                                if (idx > 0) {
                                    HorizontalDivider(color = FlatDarkBorder.copy(alpha = 0.6f))
                                }

                                val status = statusByProductId[prod.id] ?: InventoryStockStatus.IN_STOCK
                                val stockColor = when (status) {
                                    InventoryStockStatus.OUT_OF_STOCK -> RepExtraRed
                                    InventoryStockStatus.LOW -> RepExtraAmber
                                    InventoryStockStatus.IN_STOCK -> FlatTextPrimary
                                }

                                val isBagged = !prod.isLooseOnly && prod.kgPerBag > BigDecimal.ONE
                                val stockKg = prod.currentStockKg.max(BigDecimal.ZERO)
                                if (isBagged) {
                                    totalFilteredBags += stockKg.divideToIntegralValue(prod.kgPerBag).toInt()
                                } else {
                                    totalFilteredLooseKg = totalFilteredLooseKg.add(stockKg)
                                }

                                val costPerKg = if (prod.averagePurchaseCostPerKg > BigDecimal.ZERO) {
                                    prod.averagePurchaseCostPerKg
                                } else {
                                    prod.sellingPricePerKg
                                }
                                val sellPerKg = if (prod.sellingPricePerKg > BigDecimal.ZERO) {
                                    prod.sellingPricePerKg
                                } else {
                                    prod.looseSellingPricePerKg
                                }

                                val itemCost = stockKg.multiply(costPerKg).setScale(0, RoundingMode.HALF_UP)
                                val itemSell = stockKg.multiply(sellPerKg).setScale(0, RoundingMode.HALF_UP)
                                sumFilteredCost = sumFilteredCost.add(itemCost)
                                sumFilteredSell = sumFilteredSell.add(itemSell)

                                val rateSub = if (isBagged) {
                                    val bagCost = costPerKg.multiply(prod.kgPerBag).setScale(0, RoundingMode.HALF_UP)
                                    "${CurrencyUtils.formatWeight(prod.kgPerBag)} kg · ${CurrencyUtils.formatInr(bagCost)}/bag"
                                } else {
                                    "Loose · ${CurrencyUtils.formatInr(costPerKg)}/kg"
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1.4f)) {
                                        Text(
                                            text = formatProductCardTitle(prod),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FlatTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = rateSub,
                                            fontSize = 11.5.sp,
                                            color = FlatTextSecondary
                                        )
                                    }

                                    Text(
                                        text = formatProductQuantityLabel(prod),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = stockColor,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(0.75f)
                                    )

                                    Text(
                                        text = CurrencyUtils.formatInr(itemCost),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FlatTextPrimary,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(0.85f)
                                    )

                                    Text(
                                        text = CurrencyUtils.formatInr(itemSell),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FlatTextPrimary,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(0.85f)
                                    )
                                }
                            }

                            HorizontalDivider(color = FlatDarkBorder)

                            val totalStockLabel = buildString {
                                if (totalFilteredBags > 0) append("$totalFilteredBags bags")
                                if (totalFilteredLooseKg > BigDecimal.ZERO) {
                                    if (isNotEmpty()) append(" + ")
                                    append("${CurrencyUtils.formatWeight(totalFilteredLooseKg)} kg")
                                }
                                if (isEmpty()) append("0 bags")
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tr("Total", "कुल"),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary,
                                    modifier = Modifier.weight(1.2f)
                                )
                                Text(
                                    text = totalStockLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextSecondary,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(0.95f)
                                )
                                Text(
                                    text = CurrencyUtils.formatInr(sumFilteredCost),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(0.85f)
                                )
                                Text(
                                    text = CurrencyUtils.formatInr(sumFilteredSell),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RepExtraGreen,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(0.85f)
                                )
                            }
                        }
                    }
                }
            }

            // 6. Stock loss footer note
            item {
                Text(
                    text = "Stock loss this month: ${CurrencyUtils.formatInr(stockLossThisMonth)}. Adjust from Inventory → Update stock.",
                    fontSize = 12.5.sp,
                    color = FlatTextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

// ============================================================================
// PAGE 15: Expense report screen
// ============================================================================
@Composable
fun ExpenseReportScreen(
    periodDateRangeText: String,
    totalRevenue: BigDecimal,
    filteredPurchases: List<PurchaseInvoice>,
    filterStartTime: Long,
    filterEndTime: Long,
    onBack: () -> Unit,
    onOpenStockReport: () -> Unit,
    onSelectCustomDate: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    var selectedPeriod by remember { mutableStateOf(ExtraReportPeriod.THIS_MONTH) }

    val manualEntries = remember(filterStartTime, filterEndTime) {
        CashbookManualPrefs.loadManualEntries(context)
            .filter { !it.isCashIn && it.isExpense && it.date in filterStartTime..filterEndTime }
    }

    val categories = remember {
        ExpenseCategoryStore.loadCategories(context)
    }

    // Build combined expense entries from manual cashbook expenses + purchase invoice freight/hamali
    val allExpenseEntries = remember(manualEntries, filteredPurchases) {
        val list = mutableListOf<CashbookEntry>()
        list.addAll(manualEntries)

        val dateFmt = SimpleDateFormat("d MMM", Locale.ENGLISH)
        filteredPurchases.forEach { inv ->
            if (inv.gaadiKiraya > BigDecimal.ZERO) {
                list.add(
                    CashbookEntry(
                        id = "kiraya_${inv.id}",
                        date = inv.date,
                        title = "Transport",
                        subtitle = "${inv.wholesalerName} · Invoice #${inv.invoiceNumber} · ${dateFmt.format(Date(inv.date))}",
                        amount = inv.gaadiKiraya,
                        isCashIn = false,
                        tag = "EXPENSE",
                        isExpense = true
                    )
                )
            }
            val labourSum = inv.ladhan.add(inv.utaar)
            if (labourSum > BigDecimal.ZERO) {
                list.add(
                    CashbookEntry(
                        id = "labour_${inv.id}",
                        date = inv.date,
                        title = "Labour · Ladaan & Utaar",
                        subtitle = "${inv.wholesalerName} · Invoice #${inv.invoiceNumber} · ${dateFmt.format(Date(inv.date))}",
                        amount = labourSum,
                        isCashIn = false,
                        tag = "EXPENSE",
                        isExpense = true
                    )
                )
            }
        }
        list.sortedByDescending { it.amount }
    }

    val nonStockLossEntries = remember(allExpenseEntries) {
        allExpenseEntries.filter { !it.title.contains("Stock loss", ignoreCase = true) && it.tag != "STOCK LOSS" }
    }

    val stockLossTotal = remember(allExpenseEntries) {
        allExpenseEntries.filter { it.title.contains("Stock loss", ignoreCase = true) || it.tag == "STOCK LOSS" }
            .fold(BigDecimal.ZERO) { acc, e -> acc.add(e.amount) }
    }

    val totalExpenses = remember(nonStockLossEntries) {
        nonStockLossEntries.fold(BigDecimal.ZERO) { acc, e -> acc.add(e.amount) }
    }

    val pctOfSales = remember(totalExpenses, totalRevenue) {
        if (totalRevenue > BigDecimal.ZERO) {
            totalExpenses.multiply(BigDecimal(100)).divide(totalRevenue, 1, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }
    }

    data class CategoryBreakdown(
        val name: String,
        val colorHex: Long,
        val amount: BigDecimal,
        val pct: Int
    )

    val categoryBreakdowns = remember(nonStockLossEntries, categories, totalExpenses) {
        val regularCats = categories.filter { !it.isSystem }
        regularCats.map { cat ->
            val amt = nonStockLossEntries.filter { e ->
                e.title.equals(cat.name, ignoreCase = true) ||
                    (cat.name.startsWith("Labour") && e.title.startsWith("Labour", ignoreCase = true))
            }.fold(BigDecimal.ZERO) { acc, e -> acc.add(e.amount) }
            val pct = if (totalExpenses > BigDecimal.ZERO) {
                amt.multiply(BigDecimal(100)).divide(totalExpenses, 0, RoundingMode.HALF_UP).toInt()
            } else 0
            CategoryBreakdown(cat.name, cat.colorHex, amt, pct)
        }.sortedByDescending { it.amount }
    }

    val biggestCategory = categoryBreakdowns.firstOrNull { it.amount > BigDecimal.ZERO }
        ?: categoryBreakdowns.firstOrNull()

    Scaffold(
        containerColor = FlatDarkBg,
        bottomBar = {
            ExportBottomBar(
                onExportCsv = {
                    sharePlainReport(
                        context = context,
                        title = "Expense Report ($periodDateRangeText)",
                        body = buildString {
                            appendLine("Total Expenses: ${CurrencyUtils.formatInr(totalExpenses)} ($pctOfSales% of sales)")
                            categoryBreakdowns.forEach { cb ->
                                appendLine("${cb.name}: ${CurrencyUtils.formatInr(cb.amount)} (${cb.pct}%)")
                            }
                        }
                    )
                },
                onExportPdf = {
                    sharePlainReport(
                        context = context,
                        title = "Expense Report PDF ($periodDateRangeText)",
                        body = "Expense Report — Total: ${CurrencyUtils.formatInr(totalExpenses)}"
                    )
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        onClick = onBack,
                        shape = RoundedCornerShape(12.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("expense_report_back_btn")
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
                            text = tr("Expense report", "ख़र्च रिपोर्ट"),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                        Text(
                            text = tr("From Cashbook & supplier bills", "कैशबुक और सप्लायर बिलों से"),
                            fontSize = 13.sp,
                            color = FlatTextSecondary
                        )
                    }
                }
            }

            // 2. Period Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExtraReportPeriod.entries.forEach { period ->
                        val isSelected = selectedPeriod == period
                        Surface(
                            onClick = {
                                selectedPeriod = period
                                if (period == ExtraReportPeriod.CUSTOM) {
                                    onSelectCustomDate()
                                }
                            },
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) RepExtraGreen else RepExtraChipIdle,
                            modifier = Modifier.testTag("expense_period_${period.name.lowercase()}")
                        ) {
                            Text(
                                text = tr(period.labelEn, period.labelHi),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else FlatTextPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // 3. Date Range Text
            item {
                Text(
                    text = periodDateRangeText,
                    fontSize = 13.sp,
                    color = FlatTextSecondary
                )
            }

            // 4. KPI Cards: TOTAL EXPENSES | BIGGEST
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = tr("TOTAL EXPENSES", "कुल ख़र्च"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = CurrencyUtils.formatInr(totalExpenses),
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                color = RepExtraRed
                            )
                            Text(
                                text = "$pctOfSales% of sales",
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = tr("BIGGEST", "सबसे बड़ा"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = biggestCategory?.name?.substringBefore(" · ") ?: "Transport",
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (biggestCategory != null) {
                                    "${CurrencyUtils.formatInr(biggestCategory.amount)} · ${biggestCategory.pct}%"
                                } else {
                                    "₹0 · 0%"
                                },
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary
                            )
                        }
                    }
                }
            }

            // 5. BY CATEGORY Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = tr("BY CATEGORY", "श्रेणी के अनुसार"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.5.sp
                        )

                        categoryBreakdowns.forEach { cb ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(9.dp)
                                                .clip(CircleShape)
                                                .background(Color(cb.colorHex))
                                        )
                                        Text(
                                            text = cb.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FlatTextPrimary
                                        )
                                    }

                                    Text(
                                        text = "${CurrencyUtils.formatInr(cb.amount)} · ${cb.pct}%",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FlatTextPrimary
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(RepExtraChipIdle)
                                ) {
                                    val frac = (cb.pct / 100f).coerceIn(0f, 1f)
                                    if (frac > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(frac)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(50))
                                                .background(Color(cb.colorHex))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. BIGGEST ENTRIES Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = tr("BIGGEST ENTRIES", "सबसे बड़ी प्रविष्टियां"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.5.sp
                        )

                        val topEntries = nonStockLossEntries.take(5)
                        if (topEntries.isEmpty()) {
                            Text(
                                text = tr("No expenses recorded in this period.", "इस अवधि में कोई ख़र्च दर्ज नहीं है।"),
                                fontSize = 13.5.sp,
                                color = FlatTextMuted,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            topEntries.forEachIndexed { idx, entry ->
                                if (idx > 0) {
                                    HorizontalDivider(color = FlatDarkBorder.copy(alpha = 0.6f))
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = entry.title,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FlatTextPrimary
                                        )
                                        Text(
                                            text = entry.subtitle,
                                            fontSize = 12.sp,
                                            color = FlatTextSecondary
                                        )
                                    }

                                    Text(
                                        text = "−${CurrencyUtils.formatInr(entry.amount)}",
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FlatTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 7. Stock loss link banner
            item {
                Surface(
                    onClick = onOpenStockReport,
                    shape = RoundedCornerShape(16.dp),
                    color = RepExtraChipIdle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_report_stock_loss_banner")
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
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = tr("Stock loss is shown in the Stock report", "स्टॉक नुकसान स्टॉक रिपोर्ट में दिखाया गया है"),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = "${CurrencyUtils.formatInr(stockLossTotal)} this month from damaged / expired stock",
                                fontSize = 12.sp,
                                color = FlatTextSecondary
                            )
                        }

                        Text(
                            text = tr("View →", "देखें →"),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = RepExtraGreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportBottomBar(
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit
) {
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
            OutlinedButton(
                onClick = onExportCsv,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, FlatDarkBorder),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("report_export_excel_csv_btn")
            ) {
                Text(
                    text = "Excel / CSV",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary
                )
            }

            Button(
                onClick = onExportPdf,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RepExtraGreen,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("report_export_pdf_btn")
            ) {
                Text(
                    text = "PDF",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun sharePlainReport(context: Context, title: String, body: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, "$title\n\n$body")
    }
    try {
        context.startActivity(Intent.createChooser(sendIntent, title))
    } catch (_: Exception) {
        Toast.makeText(context, title, Toast.LENGTH_SHORT).show()
    }
}
