package com.example.ui.estimate

import com.example.ui.theme.CGreenPrimary
import com.example.ui.theme.kw
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.CattleFeedRepository
import com.example.data.repository.SaleItemRequest
import com.example.model.AppSettings
import com.example.model.Customer
import com.example.model.EstimateDraft
import com.example.model.EstimateDraftLineItem
import com.example.model.Permission
import com.example.model.Product
import com.example.model.Role
import com.example.model.User
import com.example.model.can
import com.example.model.isCustomerCreditBlocked
import com.example.ui.home.CustomerDropdownSelector
import com.example.ui.home.QuickAddCustomerDialog
import com.example.ui.theme.CAmber
import com.example.ui.theme.CGreen
import com.example.ui.theme.CRedUdhari
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatDarkInput
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import com.example.util.BudgetSplitItemInput
import com.example.util.BudgetSplitMode
import com.example.util.BudgetSplitRowResult
import com.example.util.CurrencyUtils
import com.example.util.SecurityUtils
import com.example.util.applyLeftoverToRow
import com.example.util.splitBudget
import com.example.util.tr
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val EstimateBrandGreen: Color get() = kw(0xFF0E6F43)
private val EstimateMintSurface: Color get() = kw(0xFFE1F5EB)
private val EstimateStepperMinusBg: Color get() = kw(0xFFE5E7EB)
private enum class CalculatorTabMode {
    ITEMS,
    BUDGET
}

private data class EstimateLineItem(
    val id: Long,
    val product: Product?,
    val bags: Int,
    val looseKg: String,
    val customBagPrice: String = "",
    val customLoosePrice: String = "",
    val snapshotSellingPricePerBag: BigDecimal? = null,
    val snapshotLoosePricePerKg: BigDecimal? = null
)

private data class BudgetRowState(
    val productId: String,
    val ratioText: String = "1",
    val amountText: String = "",
    val weightKgText: String = "",
    val isLocked: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstimateCalculatorScreen(
    repository: CattleFeedRepository,
    currentUser: User?,
    onBack: (() -> Unit)? = null,
    onNavigateToDailyRates: () -> Unit = {}
) {
    val products by repository.allProducts.collectAsStateWithLifecycle(initialValue = emptyList())
    val customers by repository.allCustomers.collectAsStateWithLifecycle(initialValue = emptyList())
    val recentSales by repository.allSales.collectAsStateWithLifecycle(initialValue = emptyList())
    val drafts by repository.allEstimateDrafts.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by repository.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val allUsers by repository.allUsers.collectAsStateWithLifecycle(initialValue = emptyList())

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val canOverrideRate = can(currentUser?.role, Permission.OVERRIDE_BILL_RATE)

    // Mode toggle: "Items" | "Budget"
    var calcMode by remember { mutableStateOf(CalculatorTabMode.ITEMS) }

    // Recent product IDs (most recently sold first)
    val recentProductIds = remember(recentSales) {
        recentSales.sortedByDescending { it.date }
            .map { it.productId }
            .distinct()
    }

    val sortedProductsWithRecentFirst = remember(products, recentProductIds) {
        val indexMap = recentProductIds.withIndex().associate { it.value to it.index }
        products.sortedWith(
            compareBy<Product> { indexMap[it.id] ?: Int.MAX_VALUE }
                .thenBy { it.feedName.lowercase() }
        )
    }

    // --- ITEMS MODE STATE ---
    var nextId by remember { mutableLongStateOf(2L) }
    var expandedItemId by remember { mutableLongStateOf(1L) }
    var lineItems by remember {
        mutableStateOf(
            listOf(
                EstimateLineItem(id = 1L, product = null, bags = 1, looseKg = "0")
            )
        )
    }
    var activeDraftId by remember { mutableStateOf<String?>(null) }
    var showDraftsDialog by remember { mutableStateOf(false) }
    var showSaveDraftDialog by remember { mutableStateOf(false) }
    var showVoiceBillFlow by remember { mutableStateOf(false) }
    var draftTitleInput by remember { mutableStateOf("") }
    var itemPickerTargetRowIndex by remember { mutableStateOf<Int?>(null) }

    // Synchronize lineItem product references with live database updates
    LaunchedEffect(products) {
        if (products.isNotEmpty()) {
            lineItems = lineItems.mapIndexed { idx, item ->
                val defaultProd = sortedProductsWithRecentFirst.firstOrNull() ?: products.first()
                val liveProd = if (item.product == null && idx == 0 && lineItems.size == 1) {
                    defaultProd
                } else {
                    products.find { it.id == item.product?.id } ?: item.product
                }
                item.copy(
                    product = liveProd,
                    snapshotSellingPricePerBag = item.snapshotSellingPricePerBag ?: liveProd?.bagSellingPrice,
                    snapshotLoosePricePerKg = item.snapshotLoosePricePerKg ?: liveProd?.effectiveLooseRatePerKg
                )
            }
        }
    }

    // --- BUDGET SPLIT MODE STATE ---
    val looseEligibleProducts = remember(sortedProductsWithRecentFirst) {
        sortedProductsWithRecentFirst.filter { it.effectiveLooseRatePerKg > BigDecimal.ZERO }
    }
    var budgetAmountText by remember { mutableStateOf("500") }
    var splitMode by remember { mutableStateOf(BudgetSplitMode.EQUAL) }
    var stepKg by remember { mutableStateOf(BigDecimal("0.050")) }
    var budgetRows by remember { mutableStateOf<List<BudgetRowState>>(emptyList()) }
    var leftoverAppliedProductId by remember { mutableStateOf<String?>(null) }
    var showBudgetProductPicker by remember { mutableStateOf(false) }

    // Initialize 2 default loose items in Budget mode when products load
    LaunchedEffect(looseEligibleProducts) {
        if (budgetRows.isEmpty() && looseEligibleProducts.size >= 2) {
            budgetRows = looseEligibleProducts.take(2).map { prod ->
                BudgetRowState(
                    productId = prod.id,
                    ratioText = "1",
                    amountText = "",
                    weightKgText = "2"
                )
            }
        } else if (budgetRows.isEmpty() && looseEligibleProducts.size == 1) {
            budgetRows = listOf(
                BudgetRowState(
                    productId = looseEligibleProducts.first().id,
                    ratioText = "1",
                    amountText = "",
                    weightKgText = "2"
                )
            )
        }
    }

    // Compute BudgetSplitResult via pure function
    val baseBudgetSplitResult = remember(budgetAmountText, budgetRows, splitMode, stepKg, products) {
        val budgetVal = budgetAmountText.toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
        val inputs = budgetRows.mapNotNull { rowState ->
            val prod = products.find { it.id == rowState.productId } ?: return@mapNotNull null
            BudgetSplitItemInput(
                productId = prod.id,
                productName = prod.feedName,
                ratePerKg = prod.effectiveLooseRatePerKg,
                availableStockKg = prod.currentStockKg,
                ratioWeight = rowState.ratioText.toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ONE,
                requestedAmount = rowState.amountText.toBigDecimalOrNull()?.max(BigDecimal.ZERO),
                requestedKg = rowState.weightKgText.toBigDecimalOrNull()?.max(BigDecimal.ZERO),
                isLocked = rowState.isLocked
            )
        }
        splitBudget(
            budget = budgetVal,
            items = inputs,
            mode = splitMode,
            stepKg = stepKg
        )
    }

    val budgetSplitResult = remember(baseBudgetSplitResult, leftoverAppliedProductId) {
        val targetId = leftoverAppliedProductId
        if (targetId != null) {
            applyLeftoverToRow(baseBudgetSplitResult, targetId)
        } else {
            baseBudgetSplitResult
        }
    }

    // --- ITEMS MODE TOTALS ---
    var itemsTotalPrice by remember { mutableStateOf(BigDecimal.ZERO) }
    var itemsTotalWeight by remember { mutableStateOf(BigDecimal.ZERO) }

    LaunchedEffect(lineItems, canOverrideRate) {
        var sumPrice = BigDecimal.ZERO
        var sumWeight = BigDecimal.ZERO
        lineItems.forEach { item ->
            item.product?.let { prod ->
                val looseVal = item.looseKg.toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
                val effectiveBagPrice = if (canOverrideRate) {
                    item.customBagPrice.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: prod.bagSellingPrice
                } else {
                    prod.bagSellingPrice
                }
                val effectiveLooseRate = if (canOverrideRate) {
                    item.customLoosePrice.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: prod.effectiveLooseRatePerKg
                } else {
                    prod.effectiveLooseRatePerKg
                }
                val itemWeight = if (prod.isLooseOnly) {
                    looseVal
                } else {
                    prod.kgPerBag.multiply(BigDecimal(item.bags)).add(looseVal)
                }
                val bagCost = if (prod.isLooseOnly) BigDecimal.ZERO else effectiveBagPrice.multiply(BigDecimal(item.bags))
                val looseCost = looseVal.multiply(effectiveLooseRate).setScale(2, RoundingMode.HALF_UP)

                sumWeight = sumWeight.add(itemWeight)
                sumPrice = sumPrice.add(bagCost).add(looseCost)
            }
        }
        itemsTotalWeight = sumWeight
        itemsTotalPrice = sumPrice
    }

    val activeTotalWeight = if (calcMode == CalculatorTabMode.ITEMS) itemsTotalWeight else budgetSplitResult.totalWeightKg
    val activeTotalPrice = if (calcMode == CalculatorTabMode.ITEMS) itemsTotalPrice else budgetSplitResult.totalActualAmount

    // Convert to Sale Dialog State
    var showConvertToSaleDialog by remember { mutableStateOf(false) }
    var useWalkInCustomer by remember { mutableStateOf(true) }
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var paymentType by remember { mutableStateOf("CASH") } // CASH, PARTIAL, UDHARI
    var customPaidText by remember { mutableStateOf("") }
    var showQuickAddCustomer by remember { mutableStateOf(false) }
    var showAdminPinForLimitOverride by remember { mutableStateOf(false) }
    var adminPinInput by remember { mutableStateOf("") }
    var adminPinError by remember { mutableStateOf(false) }

    val hasValidSaleItems = if (calcMode == CalculatorTabMode.ITEMS) {
        lineItems.any {
            it.product != null && (
                (!it.product.isLooseOnly && it.bags > 0) ||
                    (it.looseKg.toBigDecimalOrNull() ?: BigDecimal.ZERO) > BigDecimal.ZERO
                )
        }
    } else {
        budgetSplitResult.rows.any { it.allocatedKg > BigDecimal.ZERO }
    }

    val shareTitleLabel = tr("Share estimate", "पर्चा शेयर करें")
    val draftSavedMsg = tr("Draft saved!", "ड्राफ्ट सेव हो गया!")
    val creditBlockedMsg = tr("Credit blocked: Customer exceeds credit limit!", "उधारी ब्लॉक: ग्राहक की क्रेडिट सीमा पार हो गई है!")

    if (showVoiceBillFlow) {
        VoiceBillFlowScreen(
            products = sortedProductsWithRecentFirst,
            customers = customers,
            onClose = { showVoiceBillFlow = false },
            onAddItemsToBill = { resolvedItems, voiceCustomer ->
                if (resolvedItems.isNotEmpty()) {
                    calcMode = CalculatorTabMode.ITEMS
                    val mapped = resolvedItems.map { resolved ->
                        val newId = nextId++
                        EstimateLineItem(
                            id = newId,
                            product = resolved.product,
                            bags = resolved.bags,
                            looseKg = resolved.looseKg,
                            snapshotSellingPricePerBag = resolved.product.bagSellingPrice,
                            snapshotLoosePricePerKg = resolved.product.effectiveLooseRatePerKg
                        )
                    }
                    lineItems = mapped
                    expandedItemId = mapped.first().id
                }
                if (voiceCustomer != null) {
                    selectedCustomer = voiceCustomer
                    useWalkInCustomer = false
                }
                showVoiceBillFlow = false
            }
        )
        return
    }

    if (showDraftsDialog) {
        EstimateDraftsFullScreen(
            drafts = drafts,
            shopName = settings?.shopName ?: "KhataWise",
            onBack = { showDraftsDialog = false },
            onOpenDraft = { draft ->
                val meta = draft.parseMetadata()
                if (meta.customerId.isNotBlank()) {
                    customers.find { it.id == meta.customerId }?.let { cust ->
                        selectedCustomer = cust
                        useWalkInCustomer = false
                    }
                }
                if (meta.mode == "BUDGET" && meta.items.isNotEmpty()) {
                    calcMode = CalculatorTabMode.BUDGET
                    budgetAmountText = draft.totalAmount.stripTrailingZeros().toPlainString()
                    budgetRows = meta.items.map { dItem ->
                        BudgetRowState(
                            productId = dItem.productId,
                            ratioText = "1",
                            amountText = "",
                            weightKgText = dItem.looseKg.ifBlank { "1" }
                        )
                    }
                } else if (meta.items.isNotEmpty()) {
                    calcMode = CalculatorTabMode.ITEMS
                    lineItems = meta.items.map { dItem ->
                        val prod = products.find { it.id == dItem.productId }
                        EstimateLineItem(
                            id = nextId++,
                            product = prod,
                            bags = dItem.bags,
                            looseKg = dItem.looseKg,
                            customBagPrice = dItem.customBagPrice,
                            customLoosePrice = dItem.customLoosePrice,
                            snapshotSellingPricePerBag = prod?.bagSellingPrice,
                            snapshotLoosePricePerKg = prod?.effectiveLooseRatePerKg
                        )
                    }
                }
                activeDraftId = draft.id
                showDraftsDialog = false
            },
            onDeleteDraft = { draft ->
                scope.launch {
                    repository.deleteEstimateDraft(draft.id)
                }
            }
        )
        return
    }

    Scaffold(
        containerColor = FlatDarkBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Sticky Bottom Bar with Total Kg, Total Amount, Share and Make Sale
            Surface(
                color = FlatDarkCard,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = tr("TOTAL WEIGHT", "कुल वजन"),
                                fontSize = 11.sp,
                                color = FlatTextSecondary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = CurrencyUtils.formatWeightKg(activeTotalWeight),
                                fontSize = 20.sp,
                                color = FlatTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = tr("TOTAL AMOUNT", "कुल राशि"),
                                fontSize = 11.sp,
                                color = FlatTextSecondary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = CurrencyUtils.formatInr(activeTotalPrice),
                                fontSize = 26.sp,
                                color = EstimateBrandGreen,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("calculator_total_amount")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val shopTitle = settings?.shopName?.ifBlank { "KhataWise" } ?: "KhataWise"
                                val text = buildString {
                                    append("📋 *$shopTitle - Estimate*\n")
                                    append("------------------------\n")
                                    if (calcMode == CalculatorTabMode.ITEMS) {
                                        lineItems.forEachIndexed { i, item ->
                                            item.product?.let { prod ->
                                                val looseVal = item.looseKg.toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
                                                val effectiveBagPrice = if (canOverrideRate) {
                                                    item.customBagPrice.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: prod.bagSellingPrice
                                                } else prod.bagSellingPrice
                                                val effectiveLooseRate = if (canOverrideRate) {
                                                    item.customLoosePrice.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: prod.effectiveLooseRatePerKg
                                                } else prod.effectiveLooseRatePerKg
                                                val bagCost = if (prod.isLooseOnly) BigDecimal.ZERO else effectiveBagPrice.multiply(BigDecimal(item.bags))
                                                val looseCost = looseVal.multiply(effectiveLooseRate).setScale(2, RoundingMode.HALF_UP)
                                                val sub = bagCost.add(looseCost)
                                                if ((!prod.isLooseOnly && item.bags > 0) || looseVal > BigDecimal.ZERO) {
                                                    val qtyDesc = buildString {
                                                        if (!prod.isLooseOnly && item.bags > 0) append("${item.bags} Bags")
                                                        if (looseVal > BigDecimal.ZERO) {
                                                            if (isNotEmpty()) append(" + ")
                                                            append("${looseVal.stripTrailingZeros().toPlainString()} kg")
                                                        }
                                                    }
                                                    append("${i + 1}. *${prod.feedName}*: $qtyDesc = ${CurrencyUtils.formatInr(sub)}\n")
                                                }
                                            }
                                        }
                                    } else {
                                        append("Budget Split (${CurrencyUtils.formatInr(budgetSplitResult.budget)})\n")
                                        budgetSplitResult.rows.forEachIndexed { i, row ->
                                            if (row.allocatedKg > BigDecimal.ZERO) {
                                                append("${i + 1}. *${row.productName}*: ${CurrencyUtils.formatWeightKg(row.allocatedKg)} @ ${CurrencyUtils.formatInr(row.ratePerKg)}/kg = ${CurrencyUtils.formatInr(row.actualAmount)}\n")
                                            }
                                        }
                                    }
                                    append("------------------------\n")
                                    append("*Total Weight:* ${CurrencyUtils.formatWeightKg(activeTotalWeight)}\n")
                                    append("*Total Payable:* ${CurrencyUtils.formatInr(activeTotalPrice)}\n")
                                }
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, text)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, shareTitleLabel))
                            },
                            enabled = hasValidSaleItems,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("share_estimate_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, EstimateBrandGreen),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = EstimateBrandGreen)
                        ) {
                            Text(
                                text = tr("Share", "शेयर करें"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = { showConvertToSaleDialog = true },
                            enabled = hasValidSaleItems,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("make_sale_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EstimateBrandGreen,
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = tr("Make sale", "बिक्री बनाएं"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Header Row: "Estimate" + "Build a quote for a customer" on left, 3 icon buttons on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr("Estimate", "एस्टीमेट (पर्चा)"),
                        fontSize = 24.sp,
                        color = FlatTextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (calcMode == CalculatorTabMode.ITEMS) {
                            tr("Build a quote for a customer", "ग्राहक के लिए कोटेशन बनाएं")
                        } else {
                            tr("Split a customer's ₹ budget across loose items", "ग्राहक के बजट को खुले सामानों में बांटें")
                        },
                        fontSize = 13.sp,
                        color = FlatTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 0. Voice Bill Mic icon button
                    Surface(
                        onClick = { showVoiceBillFlow = true },
                        shape = RoundedCornerShape(12.dp),
                        color = EstimateMintSurface,
                        border = BorderStroke(1.dp, EstimateBrandGreen.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("open_voice_bill_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = tr("Voice bill", "बोलकर बिल"),
                                tint = EstimateBrandGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 1. Today's Rates icon button
                    Surface(
                        onClick = onNavigateToDailyRates,
                        shape = RoundedCornerShape(12.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("open_daily_rates_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.LocalOffer,
                                contentDescription = tr("Today's Rates", "आज के भाव"),
                                tint = CAmber,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 2. Open Drafts folder icon button
                    Surface(
                        onClick = { showDraftsDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("open_drafts_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = tr("Drafts", "ड्राफ्ट"),
                                tint = FlatTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 3. Save Draft icon button
                    Surface(
                        onClick = {
                            if (hasValidSaleItems) {
                                val firstCustName = selectedCustomer?.name?.trim()?.split(Regex("\\s+"))?.firstOrNull()
                                draftTitleInput = if (!firstCustName.isNullOrBlank()) {
                                    if (calcMode == CalculatorTabMode.BUDGET) {
                                        "₹${budgetAmountText.ifBlank { "500" }} budget · $firstCustName"
                                    } else {
                                        "$firstCustName · feed"
                                    }
                                } else {
                                    if (calcMode == CalculatorTabMode.BUDGET) {
                                        if (budgetAmountText.isBlank()) "Budget estimate" else "₹$budgetAmountText budget"
                                    } else {
                                        val firstCust = customers.firstOrNull()?.name?.trim()?.split(Regex("\\s+"))?.firstOrNull().orEmpty()
                                        if (firstCust.isBlank()) "Feed estimate" else "$firstCust · feed"
                                    }
                                }
                                showSaveDraftDialog = true
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("save_draft_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Save,
                                contentDescription = tr("Save draft", "ड्राफ्ट सेव"),
                                tint = if (hasValidSaleItems) FlatTextPrimary else FlatTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Full-Width Mode Toggle ("Items" | "Budget")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(FlatDarkInput)
                    .border(1.dp, FlatDarkBorder, RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val isItems = calcMode == CalculatorTabMode.ITEMS
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isItems) EstimateBrandGreen else Color.Transparent)
                        .clickable { calcMode = CalculatorTabMode.ITEMS }
                        .padding(vertical = 10.dp)
                        .testTag("calc_mode_items"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tr("Items", "सामान"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isItems) Color.White else FlatTextSecondary
                    )
                }

                val isBudget = calcMode == CalculatorTabMode.BUDGET
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isBudget) EstimateBrandGreen else Color.Transparent)
                        .clickable { calcMode = CalculatorTabMode.BUDGET }
                        .padding(vertical = 10.dp)
                        .testTag("calc_mode_budget"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tr("Budget", "बजट"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isBudget) Color.White else FlatTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (calcMode == CalculatorTabMode.ITEMS) {
                // ==========================================
                // ITEMS CALCULATOR MODE
                // ==========================================
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(lineItems, key = { _, it -> it.id }) { index, item ->
                        EstimateItemRowCard(
                            index = index,
                            item = item,
                            isExpanded = lineItems.size == 1 || expandedItemId == item.id,
                            onExpand = { expandedItemId = item.id },
                            canDelete = lineItems.size > 1,
                            canOverrideRate = canOverrideRate,
                            onOpenProductPicker = {
                                itemPickerTargetRowIndex = index
                            },
                            onUpdate = { updated ->
                                lineItems = lineItems.toMutableList().also { it[index] = updated }
                            },
                            onDelete = {
                                val removedId = item.id
                                val updatedList = lineItems.toMutableList().also { it.removeAt(index) }
                                lineItems = updatedList
                                if (expandedItemId == removedId) {
                                    expandedItemId = updatedList.lastOrNull()?.id ?: 1L
                                }
                            }
                        )
                    }

                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .drawBehind {
                                    drawRoundRect(
                                        color = EstimateBrandGreen,
                                        style = Stroke(
                                            width = 1.5.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                                        ),
                                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                                    )
                                }
                                .clickable {
                                    val nextProd = sortedProductsWithRecentFirst.firstOrNull { candidate ->
                                        lineItems.none { it.product?.id == candidate.id }
                                    } ?: sortedProductsWithRecentFirst.firstOrNull()
                                    val newId = nextId++
                                    lineItems = lineItems + EstimateLineItem(
                                        id = newId,
                                        product = nextProd,
                                        bags = if (nextProd?.isLooseOnly == true) 0 else 1,
                                        looseKg = if (nextProd?.isLooseOnly == true) "5" else "0",
                                        snapshotSellingPricePerBag = nextProd?.bagSellingPrice,
                                        snapshotLoosePricePerKg = nextProd?.effectiveLooseRatePerKg
                                    )
                                    expandedItemId = newId
                                }
                                .testTag("add_estimate_item_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+ ${tr("Add another item", "एक और सामान जोड़ें")}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = EstimateBrandGreen
                            )
                        }
                    }
                }
            } else {
                // ==========================================
                // BUDGET SPLIT MODE (Estimate (Budget).pdf)
                // ==========================================
                var roundingDropdownExpanded by remember { mutableStateOf(false) }
                val roundingSteps = listOf(
                    BigDecimal("0.050") to "50 g",
                    BigDecimal("0.100") to "100 g",
                    BigDecimal("0.250") to "250 g"
                )
                val currentStepLabel = roundingSteps.firstOrNull { it.first.compareTo(stepKg) == 0 }?.second ?: "50 g"

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Budget Input, Rounding Dropdown & Split By Card
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = FlatDarkCard),
                            border = BorderStroke(1.dp, FlatDarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // TOTAL BUDGET (No 100/200, 500, 1000 chips per user request)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = tr("TOTAL BUDGET", "कुल बजट"),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FlatTextSecondary,
                                        letterSpacing = 0.5.sp
                                    )
                                    OutlinedTextField(
                                        value = budgetAmountText,
                                        onValueChange = {
                                            if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) {
                                                budgetAmountText = it
                                                leftoverAppliedProductId = null
                                            }
                                        },
                                        prefix = {
                                            Text(
                                                "₹  ",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FlatTextSecondary
                                            )
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        textStyle = LocalTextStyle.current.copy(
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FlatTextPrimary
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("budget_amount_input"),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = FlatDarkCard,
                                            unfocusedContainerColor = FlatDarkCard,
                                            focusedBorderColor = EstimateBrandGreen,
                                            unfocusedBorderColor = EstimateBrandGreen,
                                            focusedTextColor = FlatTextPrimary,
                                            unfocusedTextColor = FlatTextPrimary
                                        )
                                    )
                                }

                                // ROUND WEIGHT DOWN TO (Dropdown Menu per user request)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = tr("ROUND WEIGHT DOWN TO", "वजन राउंड डाउन करें"),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FlatTextSecondary,
                                        letterSpacing = 0.5.sp
                                    )

                                    Box {
                                        Surface(
                                            onClick = { roundingDropdownExpanded = true },
                                            shape = RoundedCornerShape(12.dp),
                                            color = FlatDarkBg,
                                            border = BorderStroke(1.dp, FlatDarkBorder),
                                            modifier = Modifier.testTag("round_weight_dropdown_btn")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = currentStepLabel,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = EstimateBrandGreen
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDropDown,
                                                    contentDescription = tr("Select rounding step", "राउंडिंग स्टेप चुनें"),
                                                    tint = FlatTextSecondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = roundingDropdownExpanded,
                                            onDismissRequest = { roundingDropdownExpanded = false },
                                            modifier = Modifier.background(FlatDarkCard)
                                        ) {
                                            roundingSteps.forEach { (stepVal, label) ->
                                                val isSelected = stepKg.compareTo(stepVal) == 0
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = label,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                            color = if (isSelected) EstimateBrandGreen else FlatTextPrimary
                                                        )
                                                    },
                                                    onClick = {
                                                        stepKg = stepVal
                                                        leftoverAppliedProductId = null
                                                        roundingDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // SPLIT BY Segmented Control: Equal | Ratio | Amount ₹ | Weight kg
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = tr("SPLIT BY", "बांटने का तरीका"),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FlatTextSecondary,
                                        letterSpacing = 0.5.sp
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = FlatDarkBg,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            val modes = listOf(
                                                BudgetSplitMode.EQUAL to tr("Equal", "बराबर"),
                                                BudgetSplitMode.RATIO to tr("Ratio", "अनुपात"),
                                                BudgetSplitMode.AMOUNT to tr("Amount ₹", "राशि ₹"),
                                                BudgetSplitMode.WEIGHT to tr("Weight kg", "वजन kg")
                                            )
                                            modes.forEach { (m, label) ->
                                                val isSelected = splitMode == m
                                                Surface(
                                                    onClick = {
                                                        splitMode = m
                                                        leftoverAppliedProductId = null
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (isSelected) EstimateBrandGreen else Color.Transparent,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("split_mode_${m.name.lowercase()}")
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.padding(vertical = 9.dp, horizontal = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = label,
                                                            fontSize = 12.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isSelected) Color.White else FlatTextSecondary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Ratio preset chips when Ratio mode is active
                                if (splitMode == BudgetSplitMode.RATIO && budgetRows.size in 2..3) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = tr("Presets:", "प्रीसेट:"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = FlatTextSecondary
                                        )
                                        val presets = if (budgetRows.size == 2) {
                                            listOf(
                                                "1:1" to listOf("1", "1"),
                                                "2:1" to listOf("2", "1"),
                                                "3:2" to listOf("3", "2")
                                            )
                                        } else {
                                            listOf(
                                                "1:1:1" to listOf("1", "1", "1"),
                                                "3:3:4" to listOf("3", "3", "4"),
                                                "2:1:1" to listOf("2", "1", "1")
                                            )
                                        }
                                        presets.forEach { (presetLabel, values) ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = FlatDarkBg,
                                                border = BorderStroke(1.dp, EstimateBrandGreen.copy(alpha = 0.5f)),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        budgetRows = budgetRows.mapIndexed { idx, r ->
                                                            r.copy(ratioText = values.getOrElse(idx) { "1" })
                                                        }
                                                        leftoverAppliedProductId = null
                                                    }
                                            ) {
                                                Text(
                                                    text = presetLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = EstimateBrandGreen,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Loose items Header (Loose items (2/3) + Add loose item)
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("Loose items (${budgetRows.size}/3)", "खुला सामान (${budgetRows.size}/3)"),
                                fontSize = 17.sp,
                                color = FlatTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            if (budgetRows.size < 3 && looseEligibleProducts.size > budgetRows.size) {
                                Text(
                                    text = "+ ${tr("Add loose item", "खुला सामान जोड़ें")}",
                                    color = EstimateBrandGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showBudgetProductPicker = true }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                        .testTag("add_budget_item_button")
                                )
                            }
                        }
                    }

                    // 3. Budget Split Rows
                    itemsIndexed(budgetSplitResult.rows, key = { _, r -> r.productId }) { index, rowResult ->
                        val rowState = budgetRows.getOrNull(index) ?: return@itemsIndexed
                        val unlockedIndices = budgetRows.mapIndexedNotNull { idx, r -> if (!r.isLocked) idx else null }
                        val isAutoFilledLastAmountRow = splitMode == BudgetSplitMode.AMOUNT &&
                            !rowState.isLocked &&
                            unlockedIndices.size > 1 &&
                            index == unlockedIndices.last()

                        BudgetSplitRowCard(
                            rowResult = rowResult,
                            rowState = rowState,
                            splitMode = splitMode,
                            isAutoFilledLastAmountRow = isAutoFilledLastAmountRow,
                            canRemove = budgetRows.size > 2,
                            onToggleLock = {
                                val currentlyLocked = rowState.isLocked
                                budgetRows = budgetRows.toMutableList().also { list ->
                                    list[index] = rowState.copy(
                                        isLocked = !currentlyLocked,
                                        amountText = if (!currentlyLocked) {
                                            rowResult.targetAmount.stripTrailingZeros().toPlainString()
                                        } else {
                                            rowState.amountText
                                        }
                                    )
                                }
                                leftoverAppliedProductId = null
                            },
                            onRatioChange = { newRatio ->
                                budgetRows = budgetRows.toMutableList().also { list ->
                                    list[index] = rowState.copy(ratioText = newRatio)
                                }
                                leftoverAppliedProductId = null
                            },
                            onAmountChange = { newAmt ->
                                budgetRows = budgetRows.toMutableList().also { list ->
                                    list[index] = rowState.copy(amountText = newAmt)
                                }
                                leftoverAppliedProductId = null
                            },
                            onWeightChange = { newKg ->
                                budgetRows = budgetRows.toMutableList().also { list ->
                                    list[index] = rowState.copy(weightKgText = newKg)
                                }
                                leftoverAppliedProductId = null
                            },
                            onRemove = {
                                budgetRows = budgetRows.toMutableList().also { list ->
                                    list.removeAt(index)
                                }
                                leftoverAppliedProductId = null
                            }
                        )
                    }

                    // 4. Summary & Leftover Mint Banner + Warnings
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (budgetSplitResult.isOverBudget) {
                                    CRedUdhari.copy(alpha = 0.12f)
                                } else {
                                    EstimateMintSurface
                                }
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (budgetSplitResult.isOverBudget) CRedUdhari.copy(alpha = 0.5f) else EstimateBrandGreen.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val leftover = budgetSplitResult.leftoverRupees
                                val summaryText = if (leftover >= BigDecimal.ZERO) {
                                    tr(
                                        "Total ${CurrencyUtils.formatInr(budgetSplitResult.totalActualAmount)} · ${CurrencyUtils.formatInr(leftover)} left",
                                        "कुल ${CurrencyUtils.formatInr(budgetSplitResult.totalActualAmount)} · ${CurrencyUtils.formatInr(leftover)} शेष"
                                    )
                                } else {
                                    tr(
                                        "Total ${CurrencyUtils.formatInr(budgetSplitResult.totalActualAmount)} (Over budget by ${CurrencyUtils.formatInr(leftover.abs())})",
                                        "कुल ${CurrencyUtils.formatInr(budgetSplitResult.totalActualAmount)} (बजट से ${CurrencyUtils.formatInr(leftover.abs())} अधिक)"
                                    )
                                }
                                Text(
                                    text = summaryText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (budgetSplitResult.isOverBudget) CRedUdhari else EstimateBrandGreen,
                                    modifier = Modifier.testTag("budget_split_summary_text")
                                )

                                // One-tap buttons to add leftover rupees to an item
                                if (leftover > BigDecimal.ZERO && budgetSplitResult.rows.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        budgetSplitResult.rows.forEach { r ->
                                            OutlinedButton(
                                                onClick = {
                                                    leftoverAppliedProductId = r.productId
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, EstimateBrandGreen),
                                                modifier = Modifier
                                                    .heightIn(min = 34.dp)
                                                    .testTag("add_leftover_${r.productId}")
                                            ) {
                                                Text(
                                                    text = "+ ${CurrencyUtils.formatInr(leftover)} -> ${r.productName}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = EstimateBrandGreen,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                if (budgetSplitResult.isOverBudget) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = CRedUdhari, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = tr(
                                                "Warning: Total exceeds customer budget of ${CurrencyUtils.formatInr(budgetSplitResult.budget)}!",
                                                "चेतावनी: कुल राशि ग्राहक के ${CurrencyUtils.formatInr(budgetSplitResult.budget)} बजट से अधिक है!"
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CRedUdhari,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (budgetSplitResult.hasStockWarning) {
                                    budgetSplitResult.rows.filter { it.exceedsStock }.forEach { overRow ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Default.Warning, contentDescription = null, tint = CAmber, modifier = Modifier.size(16.dp))
                                            Text(
                                                text = tr(
                                                    "${overRow.productName}: Needed ${CurrencyUtils.formatWeightKg(overRow.allocatedKg)} exceeds stock (${CurrencyUtils.formatWeightKg(overRow.availableStockKg)})",
                                                    "${overRow.productName}: आवश्यक ${CurrencyUtils.formatWeightKg(overRow.allocatedKg)} स्टॉक (${CurrencyUtils.formatWeightKg(overRow.availableStockKg)}) से अधिक है"
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = CAmber,
                                                fontWeight = FontWeight.Bold
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

        // ==========================================
        // SEARCHABLE ITEM PICKER DIALOG (RECENT ITEMS FIRST)
        // ==========================================
        itemPickerTargetRowIndex?.let { rowIndex ->
            SearchableProductPickerDialog(
                products = sortedProductsWithRecentFirst,
                recentProductIds = recentProductIds,
                onDismiss = { itemPickerTargetRowIndex = null },
                onSelectProduct = { prod ->
                    val currentRow = lineItems.getOrNull(rowIndex)
                    if (currentRow != null) {
                        lineItems = lineItems.toMutableList().also {
                            it[rowIndex] = currentRow.copy(
                                product = prod,
                                bags = if (prod.isLooseOnly) 0 else currentRow.bags.coerceAtLeast(1),
                                looseKg = if (prod.isLooseOnly && (currentRow.looseKg.toBigDecimalOrNull() ?: BigDecimal.ZERO) <= BigDecimal.ZERO) "5" else currentRow.looseKg,
                                customBagPrice = "",
                                customLoosePrice = "",
                                snapshotSellingPricePerBag = prod.bagSellingPrice,
                                snapshotLoosePricePerKg = prod.effectiveLooseRatePerKg
                            )
                        }
                    }
                    itemPickerTargetRowIndex = null
                }
            )
        }

        // ==========================================
        // BUDGET LOOSE ITEM PICKER DIALOG
        // ==========================================
        if (showBudgetProductPicker) {
            val availableToAdd = looseEligibleProducts.filter { candidate ->
                budgetRows.none { it.productId == candidate.id }
            }
            SearchableProductPickerDialog(
                products = availableToAdd,
                recentProductIds = recentProductIds,
                title = tr("Select loose item", "खुला सामान चुनें"),
                onDismiss = { showBudgetProductPicker = false },
                onSelectProduct = { prod ->
                    if (budgetRows.size < 3) {
                        budgetRows = budgetRows + BudgetRowState(
                            productId = prod.id,
                            ratioText = "1",
                            amountText = "",
                            weightKgText = "1"
                        )
                        leftoverAppliedProductId = null
                    }
                    showBudgetProductPicker = false
                }
            )
        }

        // ==========================================
        // SAVE ESTIMATE DRAFT BOTTOM SHEET (Page 10)
        // ==========================================
        if (showSaveDraftDialog) {
            val summaryTitle = remember(calcMode, lineItems, budgetSplitResult) {
                if (calcMode == CalculatorTabMode.ITEMS) {
                    lineItems.mapNotNull { it.product?.feedName }.joinToString(", ").ifBlank { "Cattle Feed" }
                } else {
                    budgetSplitResult.rows.filter { it.allocatedKg > BigDecimal.ZERO }
                        .joinToString(", ") { it.productName }
                        .ifBlank { "Loose items" }
                }
            }
            val itemCount = if (calcMode == CalculatorTabMode.ITEMS) {
                lineItems.count { it.product != null }.coerceAtLeast(1)
            } else {
                budgetSplitResult.rows.count { it.allocatedKg > BigDecimal.ZERO }.coerceAtLeast(1)
            }
            val summarySub = "$itemCount ${if (itemCount == 1) "item" else "items"} · ${CurrencyUtils.formatWeight(activeTotalWeight)} kg"
            val sampleRateLabel = remember(lineItems, products) {
                val firstProd = lineItems.firstNotNullOfOrNull { it.product } ?: products.firstOrNull()
                if (firstProd != null && !firstProd.isLooseOnly) {
                    "${CurrencyUtils.formatInr(firstProd.bagSellingPrice)}/bag"
                } else if (firstProd != null) {
                    "${CurrencyUtils.formatInr(firstProd.effectiveLooseRatePerKg)}/kg"
                } else {
                    "₹1,350/bag"
                }
            }

            SaveAsDraftBottomSheet(
                initialDraftName = draftTitleInput,
                initialCustomer = selectedCustomer ?: customers.firstOrNull(),
                customers = customers,
                itemsSummaryTitle = summaryTitle,
                itemsCountAndWeightSubtitle = summarySub,
                totalAmount = activeTotalPrice,
                sampleBagRateLabel = sampleRateLabel,
                onDismiss = { showSaveDraftDialog = false },
                onSaveDraft = { finalNameInput, chosenCust, lockRates ->
                    scope.launch {
                        val draftItems = if (calcMode == CalculatorTabMode.ITEMS) {
                            lineItems.mapNotNull { row ->
                                val prod = row.product ?: return@mapNotNull null
                                val lockedBagRate = if (lockRates && row.customBagPrice.isBlank()) {
                                    prod.bagSellingPrice.stripTrailingZeros().toPlainString()
                                } else row.customBagPrice
                                val lockedLooseRate = if (lockRates && row.customLoosePrice.isBlank()) {
                                    prod.effectiveLooseRatePerKg.stripTrailingZeros().toPlainString()
                                } else row.customLoosePrice
                                EstimateDraftLineItem(
                                    productId = prod.id,
                                    productName = prod.feedName,
                                    bags = row.bags,
                                    looseKg = row.looseKg,
                                    customBagPrice = lockedBagRate,
                                    customLoosePrice = lockedLooseRate
                                )
                            }
                        } else {
                            budgetSplitResult.rows.filter { it.allocatedKg > BigDecimal.ZERO }.map { row ->
                                EstimateDraftLineItem(
                                    productId = row.productId,
                                    productName = row.productName,
                                    bags = 0,
                                    looseKg = row.allocatedKg.stripTrailingZeros().toPlainString(),
                                    customBagPrice = "",
                                    customLoosePrice = if (lockRates) row.ratePerKg.stripTrailingZeros().toPlainString() else ""
                                )
                            }
                        }

                        val timeStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
                        val finalTitle = finalNameInput.trim().ifBlank { "Estimate ($timeStr)" }
                        val draft = EstimateDraft(
                            id = activeDraftId ?: java.util.UUID.randomUUID().toString(),
                            title = finalTitle,
                            itemsJson = EstimateDraft.serializeItems(
                                items = draftItems,
                                mode = if (calcMode == CalculatorTabMode.BUDGET) "BUDGET" else "ITEMS",
                                customerId = chosenCust?.id ?: "",
                                customerName = chosenCust?.name ?: "",
                                lockRates = lockRates
                            ),
                            totalWeightKg = activeTotalWeight,
                            totalAmount = activeTotalPrice,
                            createdByUserId = currentUser?.id ?: ""
                        )
                        repository.saveEstimateDraft(draft)
                        activeDraftId = draft.id
                        if (chosenCust != null) {
                            selectedCustomer = chosenCust
                            useWalkInCustomer = false
                        }
                        showSaveDraftDialog = false
                        snackbarHostState.showSnackbar(draftSavedMsg)
                    }
                }
            )
        }

        // ==========================================
        // MAKE SALE DIALOG (WALK-IN OR SELECTED CUSTOMER)
        // ==========================================
        if (showConvertToSaleDialog) {
            val discrepancyWarnings = remember(lineItems, products, calcMode, budgetSplitResult) {
                val warnings = mutableListOf<String>()
                if (calcMode == CalculatorTabMode.ITEMS) {
                    lineItems.forEach { item ->
                        val prod = item.product ?: return@forEach
                        val liveProd = products.find { it.id == prod.id } ?: prod
                        val looseVal = item.looseKg.toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
                        val itemWeight = if (liveProd.isLooseOnly) looseVal else liveProd.kgPerBag.multiply(BigDecimal(item.bags)).add(looseVal)
                        if (itemWeight <= BigDecimal.ZERO) return@forEach

                        if (itemWeight > liveProd.currentStockKg) {
                            warnings.add(
                                "${liveProd.feedName}: Needed ${CurrencyUtils.formatWeightKg(itemWeight)}, only ${CurrencyUtils.formatWeightKg(liveProd.currentStockKg)} in stock!"
                            )
                        }
                        if (item.snapshotSellingPricePerBag != null &&
                            item.customBagPrice.isBlank() &&
                            liveProd.bagSellingPrice.compareTo(item.snapshotSellingPricePerBag) != 0
                        ) {
                            warnings.add(
                                "${liveProd.feedName}: Bag rate changed from ${CurrencyUtils.formatInr(item.snapshotSellingPricePerBag)} to ${CurrencyUtils.formatInr(liveProd.bagSellingPrice)}"
                            )
                        }
                        if (item.snapshotLoosePricePerKg != null &&
                            item.customLoosePrice.isBlank() &&
                            liveProd.effectiveLooseRatePerKg.compareTo(item.snapshotLoosePricePerKg) != 0
                        ) {
                            warnings.add(
                                "${liveProd.feedName}: Loose rate changed from ${CurrencyUtils.formatInr(item.snapshotLoosePricePerKg)}/kg to ${CurrencyUtils.formatInr(liveProd.effectiveLooseRatePerKg)}/kg"
                            )
                        }
                    }
                } else {
                    budgetSplitResult.rows.filter { it.exceedsStock }.forEach { row ->
                        warnings.add(
                            "${row.productName}: Needed ${CurrencyUtils.formatWeightKg(row.allocatedKg)}, only ${CurrencyUtils.formatWeightKg(row.availableStockKg)} in stock!"
                        )
                    }
                }
                warnings
            }

            val paidAmount = when (paymentType) {
                "CASH" -> activeTotalPrice
                "UDHARI" -> BigDecimal.ZERO
                "PARTIAL" -> customPaidText.toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
                else -> activeTotalPrice
            }
            val udhariGenerated = activeTotalPrice.subtract(paidAmount).max(BigDecimal.ZERO)

            val effectiveCustomer = if (useWalkInCustomer) null else selectedCustomer
            val creditBlockedForCustomer = !useWalkInCustomer &&
                effectiveCustomer != null &&
                udhariGenerated > BigDecimal.ZERO &&
                isCustomerCreditBlocked(effectiveCustomer, udhariGenerated)

            fun executeSaleConversion() {
                scope.launch {
                    val targetCustomer = if (useWalkInCustomer) {
                        repository.getOrCreateWalkInCustomer(currentUser?.id ?: "")
                    } else {
                        selectedCustomer ?: return@launch
                    }

                    val requests = if (calcMode == CalculatorTabMode.ITEMS) {
                        lineItems.mapNotNull { item ->
                            val prod = item.product ?: return@mapNotNull null
                            val looseVal = item.looseKg.toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
                            val bagsCount = if (prod.isLooseOnly) 0 else item.bags
                            if (bagsCount > 0 || looseVal > BigDecimal.ZERO) {
                                SaleItemRequest(
                                    productId = prod.id,
                                    bags = bagsCount,
                                    looseKg = looseVal,
                                    customBagPrice = if (canOverrideRate) item.customBagPrice.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } else null,
                                    customLoosePricePerKg = if (canOverrideRate) item.customLoosePrice.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } else null
                                )
                            } else null
                        }
                    } else {
                        budgetSplitResult.rows.mapNotNull { row ->
                            if (row.allocatedKg > BigDecimal.ZERO) {
                                SaleItemRequest(
                                    productId = row.productId,
                                    bags = 0,
                                    looseKg = row.allocatedKg,
                                    customBagPrice = null,
                                    customLoosePricePerKg = row.ratePerKg
                                )
                            } else null
                        }
                    }

                    if (requests.isNotEmpty()) {
                        try {
                            repository.recordMultiItemSale(
                                customerId = targetCustomer.id,
                                items = requests,
                                paidAmount = paidAmount,
                                soldByUserId = currentUser?.id ?: "",
                                performedByRole = currentUser?.role
                            )
                            activeDraftId?.let { repository.deleteEstimateDraft(it) }
                            activeDraftId = null
                            showConvertToSaleDialog = false
                            val defaultProd = sortedProductsWithRecentFirst.firstOrNull()
                            lineItems = listOf(
                                EstimateLineItem(
                                    id = nextId++,
                                    product = defaultProd,
                                    bags = if (defaultProd?.isLooseOnly == true) 0 else 1,
                                    looseKg = if (defaultProd?.isLooseOnly == true) "5" else "0",
                                    snapshotSellingPricePerBag = defaultProd?.bagSellingPrice,
                                    snapshotLoosePricePerKg = defaultProd?.effectiveLooseRatePerKg
                                )
                            )
                            snackbarHostState.showSnackbar("Sale recorded for ${targetCustomer.name}!")
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar(e.message ?: "Unable to record sale")
                        }
                    }
                }
            }

            AlertDialog(
                onDismissRequest = { showConvertToSaleDialog = false },
                containerColor = FlatDarkCard,
                title = {
                    Text(
                        text = tr("Make sale", "बिक्री बनाएं"),
                        color = FlatTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CGreen.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(tr("Total bill amount", "कुल बिल राशि"), color = FlatTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                Text(CurrencyUtils.formatInr(activeTotalPrice), color = CGreen, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (discrepancyWarnings.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CRedUdhari.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, CRedUdhari.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = tr("Rate or stock has changed", "भाव / स्टॉक में बदलाव मिला"),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CRedUdhari,
                                        fontWeight = FontWeight.Bold
                                    )
                                    discrepancyWarnings.forEach { warn ->
                                        Text("• $warn", style = MaterialTheme.typography.bodySmall, color = FlatTextPrimary)
                                    }
                                }
                            }
                        }

                        // Customer Type Toggle: Walk-in Customer vs Existing Customer
                        Text(
                            text = tr("Customer", "ग्राहक"),
                            style = MaterialTheme.typography.labelMedium,
                            color = FlatTextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = useWalkInCustomer,
                                onClick = {
                                    useWalkInCustomer = true
                                    paymentType = "CASH"
                                },
                                label = { Text(tr("Walk-in Customer", "वॉक-इन ग्राहक"), fontWeight = FontWeight.Bold) },
                                modifier = Modifier.testTag("walk_in_customer_chip")
                            )
                            FilterChip(
                                selected = !useWalkInCustomer,
                                onClick = { useWalkInCustomer = false },
                                label = { Text(tr("Select customer", "ग्राहक चुनें"), fontWeight = FontWeight.Bold) },
                                modifier = Modifier.testTag("select_customer_chip")
                            )
                        }

                        if (!useWalkInCustomer) {
                            CustomerDropdownSelector(
                                customers = customers,
                                selectedCustomer = selectedCustomer,
                                onSelectCustomer = { selectedCustomer = it },
                                onAddNewCustomer = { showQuickAddCustomer = true },
                                showExactUdhari = can(currentUser?.role, Permission.VIEW_CUSTOMER_BALANCE)
                            )
                        }

                        Text(
                            text = tr("Payment received", "प्राप्त भुगतान"),
                            style = MaterialTheme.typography.labelMedium,
                            color = FlatTextSecondary,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SalePaymentQuickChip(
                                label = tr("Full", "नकद (Full)"),
                                isSelected = paymentType == "CASH",
                                color = CGreen,
                                onClick = { paymentType = "CASH" },
                                modifier = Modifier.weight(1f)
                            )
                            if (!useWalkInCustomer) {
                                SalePaymentQuickChip(
                                    label = tr("Half", "आधा / आंशिक"),
                                    isSelected = paymentType == "PARTIAL",
                                    color = CAmber,
                                    onClick = {
                                        paymentType = "PARTIAL"
                                        if (customPaidText.isBlank()) {
                                            customPaidText = activeTotalPrice.divide(BigDecimal(2), 0, RoundingMode.HALF_UP).toPlainString()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                SalePaymentQuickChip(
                                    label = tr("Udhari", "उधारी"),
                                    isSelected = paymentType == "UDHARI",
                                    color = CRedUdhari,
                                    onClick = { paymentType = "UDHARI" },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (paymentType == "PARTIAL" && !useWalkInCustomer) {
                            OutlinedTextField(
                                value = customPaidText,
                                onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) customPaidText = it },
                                label = { Text(tr("Cash Paid Now (₹)", "अभी प्राप्त नकद (₹)")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CAmber,
                                    unfocusedBorderColor = FlatDarkBorder,
                                    focusedTextColor = FlatTextPrimary,
                                    unfocusedTextColor = FlatTextPrimary
                                )
                            )
                        }

                        if (udhariGenerated > BigDecimal.ZERO && !useWalkInCustomer) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(tr("Added to Udhari", "उधारी में जुड़ेगा"), style = MaterialTheme.typography.bodySmall, color = FlatTextSecondary)
                                Text(CurrencyUtils.formatInr(udhariGenerated), style = MaterialTheme.typography.bodyMedium, color = CRedUdhari, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (creditBlockedForCustomer) {
                                if (currentUser?.role == Role.STAFF) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(creditBlockedMsg)
                                    }
                                } else {
                                    adminPinInput = ""
                                    adminPinError = false
                                    showAdminPinForLimitOverride = true
                                }
                            } else {
                                executeSaleConversion()
                            }
                        },
                        enabled = (useWalkInCustomer || selectedCustomer != null) && activeTotalPrice > BigDecimal.ZERO,
                        colors = ButtonDefaults.buttonColors(containerColor = CGreenPrimary, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(tr("Confirm sale", "बिक्री पक्की करें"), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConvertToSaleDialog = false }) {
                        Text(tr("Cancel", "रद्द करें"), color = FlatTextSecondary)
                    }
                }
            )

            if (showAdminPinForLimitOverride) {
                AlertDialog(
                    onDismissRequest = { showAdminPinForLimitOverride = false },
                    containerColor = FlatDarkCard,
                    title = {
                        Text(
                            text = tr("Credit limit exceeded", "उधारी सीमा पार"),
                            color = FlatTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = tr(
                                    "Customer exceeds their credit limit. Enter Admin PIN to authorize this sale.",
                                    "ग्राहक अपनी उधारी सीमा से अधिक ले रहा है। अनुमति के लिए एडमिन पिन दर्ज करें।"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = FlatTextSecondary
                            )
                            OutlinedTextField(
                                value = adminPinInput,
                                onValueChange = {
                                    adminPinInput = it.filter { c -> c.isDigit() }.take(4)
                                    adminPinError = false
                                },
                                label = { Text(tr("4-Digit Admin PIN", "4-अंकों का एडमिन पिन")) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                isError = adminPinError,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (adminPinError) {
                                Text(
                                    text = tr("Invalid Admin PIN", "गलत एडमिन पिन"),
                                    color = CRedUdhari,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val admins = allUsers.filter { it.role == Role.ADMINISTRATOR && it.isActive }
                                val verified = admins.any { SecurityUtils.verifyPin(adminPinInput, it.pinHash) }
                                if (verified) {
                                    showAdminPinForLimitOverride = false
                                    executeSaleConversion()
                                } else {
                                    adminPinError = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CGreenPrimary, contentColor = Color.White)
                        ) {
                            Text(tr("Authorize", "अनुमति दें"), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAdminPinForLimitOverride = false }) {
                            Text(tr("Cancel", "रद्द करें"), color = FlatTextSecondary)
                        }
                    }
                )
            }
        }

        if (showQuickAddCustomer) {
            QuickAddCustomerDialog(
                currentUserId = currentUser?.id ?: "",
                currentUserRole = currentUser?.role,
                onDismiss = { showQuickAddCustomer = false },
                onCustomerAdded = { newCustomer ->
                    scope.launch {
                        repository.saveCustomer(newCustomer, actor = currentUser)
                        selectedCustomer = newCustomer
                        useWalkInCustomer = false
                        showQuickAddCustomer = false
                    }
                }
            )
        }
    }
}

@Composable
private fun SalePaymentQuickChip(
    label: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) color.copy(alpha = 0.2f) else FlatDarkInput,
        border = BorderStroke(1.dp, if (isSelected) color else FlatDarkBorder),
        modifier = modifier
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) color else FlatTextSecondary,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EstimateItemRowCard(
    index: Int,
    item: EstimateLineItem,
    isExpanded: Boolean,
    onExpand: () -> Unit,
    canDelete: Boolean,
    canOverrideRate: Boolean,
    onOpenProductPicker: () -> Unit,
    onUpdate: (EstimateLineItem) -> Unit,
    onDelete: () -> Unit
) {
    val prod = item.product
    val isLooseOnly = prod?.isLooseOnly == true
    val looseVal = item.looseKg.toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
    val effectiveBagPrice = if (prod != null) {
        if (canOverrideRate) {
            item.customBagPrice.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: prod.bagSellingPrice
        } else prod.bagSellingPrice
    } else BigDecimal.ZERO
    val effectiveLooseRate = if (prod != null) {
        if (canOverrideRate) {
            item.customLoosePrice.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: prod.effectiveLooseRatePerKg
        } else prod.effectiveLooseRatePerKg
    } else BigDecimal.ZERO

    val itemWeight = if (prod != null) {
        if (isLooseOnly) looseVal else prod.kgPerBag.multiply(BigDecimal(item.bags)).add(looseVal)
    } else BigDecimal.ZERO
    val subTotal = if (prod != null) {
        val bCost = if (isLooseOnly) BigDecimal.ZERO else effectiveBagPrice.multiply(BigDecimal(item.bags))
        val lCost = looseVal.multiply(effectiveLooseRate).setScale(2, RoundingMode.HALF_UP)
        bCost.add(lCost)
    } else BigDecimal.ZERO

    if (!isExpanded && prod != null) {
        val qtySummary = buildString {
            if (!isLooseOnly && item.bags > 0) {
                append("${item.bags} ${if (item.bags == 1) "bag" else "bags"}")
            }
            if (looseVal > BigDecimal.ZERO) {
                if (isNotEmpty()) append(" + ")
                append("${looseVal.stripTrailingZeros().toPlainString()} kg loose")
            }
            if (isEmpty()) {
                append("0 kg")
            }
        }
        Surface(
            onClick = onExpand,
            shape = RoundedCornerShape(16.dp),
            color = FlatDarkCard,
            border = BorderStroke(1.dp, FlatDarkBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("estimate_item_card_$index")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = prod.feedName,
                        fontSize = 15.5.sp,
                        color = FlatTextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = qtySummary,
                        fontSize = 13.sp,
                        color = FlatTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = CurrencyUtils.formatInr(subTotal),
                        fontSize = 16.sp,
                        color = FlatTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = FlatTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        return
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = FlatDarkCard),
        border = BorderStroke(1.dp, FlatDarkBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("estimate_item_card_$index")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Product Picker Box with green border + Delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.5.dp, EstimateBrandGreen),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenProductPicker() }
                        .testTag("estimate_item_picker_$index")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val selectorTitle = if (prod != null) {
                            if (prod.isLooseOnly) {
                                "${prod.feedName} · ${tr("Loose", "खुला")}"
                            } else {
                                "${prod.feedName} · ${prod.kgPerBag.stripTrailingZeros().toPlainString()} kg bag"
                            }
                        } else {
                            tr("Select item", "सामान चुनें")
                        }
                        Text(
                            text = selectorTitle,
                            fontSize = 15.sp,
                            color = FlatTextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = EstimateBrandGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (canDelete) {
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = tr("Remove item", "सामान हटाएं"),
                            tint = CRedUdhari
                        )
                    }
                }
            }

            // 2-Column Steppers for BAGS (बोरी) & LOOSE KG (खुला)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isLooseOnly) {
                    // BAGS (बोरी) Stepper
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(FlatDarkBg)
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = tr("BAGS (बोरी)", "बोरी (BAGS)"),
                            fontSize = 11.sp,
                            color = FlatTextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                onClick = { if (item.bags > 0) onUpdate(item.copy(bags = item.bags - 1)) },
                                shape = RoundedCornerShape(10.dp),
                                color = EstimateStepperMinusBg,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("bags_minus_$index")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = tr("Decrease bags", "बोरी घटाएं"),
                                        tint = FlatTextPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = item.bags.toString(),
                                fontSize = 22.sp,
                                color = FlatTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                onClick = { onUpdate(item.copy(bags = item.bags + 1)) },
                                shape = RoundedCornerShape(10.dp),
                                color = EstimateBrandGreen,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("bags_plus_$index")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = tr("Increase bags", "बोरी बढ़ाएं"),
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // LOOSE KG (खुला) Stepper
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(FlatDarkBg)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = tr("LOOSE KG (खुला)", "खुला किलो (LOOSE KG)"),
                        fontSize = 11.sp,
                        color = FlatTextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            onClick = {
                                val nextKg = (looseVal.subtract(BigDecimal.ONE)).max(BigDecimal.ZERO)
                                onUpdate(item.copy(looseKg = nextKg.stripTrailingZeros().toPlainString()))
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = EstimateStepperMinusBg,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("loose_minus_$index")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = tr("Decrease loose kg", "खुला किलो घटाएं"),
                                    tint = FlatTextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Text(
                            text = item.looseKg.ifBlank { "0" },
                            fontSize = 22.sp,
                            color = FlatTextPrimary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("loose_input_$index")
                        )

                        Surface(
                            onClick = {
                                val nextKg = looseVal.add(BigDecimal.ONE)
                                onUpdate(item.copy(looseKg = nextKg.stripTrailingZeros().toPlainString()))
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = EstimateBrandGreen,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("loose_plus_$index")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = tr("Increase loose kg", "खुला किलो बढ़ाएं"),
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Quick kg chips row: ½ | 1 | 2 | 5
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr("Quick kg", "त्वरित किलो"),
                    fontSize = 12.sp,
                    color = FlatTextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                val quickKgOptions = listOf(
                    "½" to "0.5",
                    "1" to "1",
                    "2" to "2",
                    "5" to "5"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    quickKgOptions.forEach { (label, kgVal) ->
                        val isSelectedQuick = item.looseKg == kgVal
                        Surface(
                            onClick = {
                                val nextLoose = if (item.looseKg == kgVal) "0" else kgVal
                                onUpdate(item.copy(looseKg = nextLoose))
                            },
                            shape = RoundedCornerShape(50),
                            color = if (isSelectedQuick) EstimateBrandGreen else FlatDarkInput
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelectedQuick) Color.White else FlatTextPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // Card Footer: Weight: X kg on left, ₹amount on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${tr("Weight", "वजन")}: ${CurrencyUtils.formatWeightKg(itemWeight)}",
                    fontSize = 13.5.sp,
                    color = FlatTextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = CurrencyUtils.formatInr(subTotal),
                    fontSize = 18.sp,
                    color = FlatTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun BudgetSplitRowCard(
    rowResult: BudgetSplitRowResult,
    rowState: BudgetRowState,
    splitMode: BudgetSplitMode,
    isAutoFilledLastAmountRow: Boolean,
    canRemove: Boolean,
    onToggleLock: () -> Unit,
    onRatioChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = FlatDarkCard),
        border = BorderStroke(
            1.dp,
            when {
                rowResult.exceedsStock -> CRedUdhari.copy(alpha = 0.6f)
                rowResult.isLocked -> CAmber.copy(alpha = 0.6f)
                else -> FlatDarkBorder
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rowResult.productName,
                        fontSize = 15.5.sp,
                        color = FlatTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${CurrencyUtils.formatInr(rowResult.ratePerKg)} / kg · ${CurrencyUtils.formatInr(rowResult.targetAmount)}",
                        fontSize = 13.sp,
                        color = FlatTextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${CurrencyUtils.formatWeight(rowResult.allocatedKg)} kg",
                            fontSize = 15.5.sp,
                            color = FlatTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = CurrencyUtils.formatInr(rowResult.actualAmount),
                            fontSize = 13.sp,
                            color = FlatTextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    IconButton(
                        onClick = onToggleLock,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("lock_budget_row_${rowResult.productId}")
                    ) {
                        Icon(
                            imageVector = if (rowResult.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (rowResult.isLocked) tr("Unlock row", "अनलॉक करें") else tr("Lock row", "लॉक करें"),
                            tint = if (rowResult.isLocked) CAmber else FlatTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = FlatTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Mode-specific input (Ratio / Amount / Weight) or Expanded Remove button
            if (splitMode != BudgetSplitMode.EQUAL || expanded) {
                when (splitMode) {
                    BudgetSplitMode.RATIO -> {
                        OutlinedTextField(
                            value = rowState.ratioText,
                            onValueChange = {
                                if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) onRatioChange(it)
                            },
                            enabled = !rowState.isLocked,
                            label = { Text(tr("Ratio share", "अनुपात हिस्सा")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                    BudgetSplitMode.AMOUNT -> {
                        OutlinedTextField(
                            value = if (isAutoFilledLastAmountRow) {
                                rowResult.targetAmount.stripTrailingZeros().toPlainString()
                            } else {
                                rowState.amountText
                            },
                            onValueChange = {
                                if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) onAmountChange(it)
                            },
                            enabled = !rowState.isLocked && !isAutoFilledLastAmountRow,
                            label = {
                                Text(
                                    if (isAutoFilledLastAmountRow) {
                                        tr("Auto-filled Remaining (₹)", "शेष ऑटो-भरा (₹)")
                                    } else {
                                        tr("Amount (₹)", "राशि (₹)")
                                    }
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                    BudgetSplitMode.WEIGHT -> {
                        OutlinedTextField(
                            value = rowState.weightKgText,
                            onValueChange = {
                                if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) onWeightChange(it)
                            },
                            enabled = !rowState.isLocked,
                            label = { Text(tr("Weight (kg)", "वजन (kg)")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                    BudgetSplitMode.EQUAL -> Unit
                }

                if (canRemove && expanded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onRemove) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = CRedUdhari, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(tr("Remove item", "सामान हटाएं"), color = CRedUdhari, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchableProductPickerDialog(
    products: List<Product>,
    recentProductIds: List<String>,
    title: String = tr("Select Item (Recent First)", "सामान चुनें (हाल के पहले)"),
    onDismiss: () -> Unit,
    onSelectProduct: (Product) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(products, query) {
        if (query.isBlank()) products
        else {
            val q = query.trim().lowercase()
            products.filter {
                it.feedName.lowercase().contains(q)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FlatDarkCard,
        title = {
            Text(
                text = title,
                color = FlatTextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(tr("Search item name...", "सामान का नाम खोजें...")) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CGreen) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_picker_search_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(filtered, key = { _, p -> p.id }) { _, prod ->
                        val isRecent = prod.id in recentProductIds
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = FlatDarkBg,
                            border = BorderStroke(1.dp, if (isRecent) CGreen.copy(alpha = 0.4f) else FlatDarkBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectProduct(prod) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = prod.feedName,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = FlatTextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (isRecent) {
                                            Surface(
                                                shape = CircleShape,
                                                color = CGreen.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = tr("Recent", "हाल का"),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = CGreen,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = if (prod.isLooseOnly) {
                                            "${tr("Loose", "खुला")}: ${CurrencyUtils.formatInr(prod.effectiveLooseRatePerKg)}/kg • ${tr("Stock", "स्टॉक")}: ${CurrencyUtils.formatWeightKg(prod.currentStockKg)}"
                                        } else {
                                            "${tr("Bag", "बोरी")}: ${CurrencyUtils.formatInr(prod.bagSellingPrice)} • ${tr("Loose", "खुला")}: ${CurrencyUtils.formatInr(prod.effectiveLooseRatePerKg)}/kg"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = FlatTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("Close", "बंद करें"), color = FlatTextSecondary)
            }
        }
    )
}
