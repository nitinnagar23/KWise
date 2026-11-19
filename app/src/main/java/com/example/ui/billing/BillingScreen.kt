package com.example.ui.billing

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.repository.CattleFeedRepository
import com.example.data.repository.SaleItemRequest
import com.example.model.Customer
import com.example.model.CustomerStatusChip
import com.example.model.Permission
import com.example.model.Product
import com.example.model.Role
import com.example.model.User
import com.example.model.can
import com.example.model.getCustomerStatusChip
import com.example.model.isCustomerCreditBlocked
import com.example.reminder.ReminderScheduler
import com.example.ui.home.QuickAddCustomerDialog
import com.example.ui.theme.CAmber
import com.example.ui.theme.CAmberBg
import com.example.ui.theme.CAmberBorder
import com.example.ui.theme.CGreen
import com.example.ui.theme.CRedUdhari
import com.example.ui.theme.CRedUdhariBg
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatDarkInput
import com.example.ui.theme.FlatDarkSurface
import com.example.ui.theme.FlatTextMuted
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import com.example.ui.theme.kw
import com.example.ui.theme.pickerThemedContext
import com.example.util.BillGenerator
import com.example.util.CurrencyUtils
import com.example.util.SecurityUtils
import com.example.util.tr
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val BillBrandGreen: Color get() = kw(0xFF0E6F43)
private val BillMintPillBg: Color get() = kw(0xFFE1F5EB)
private val BillCrimson: Color get() = kw(0xFFB91C1C)
private val BillCrimsonSoftBg: Color get() = kw(0xFFFDE8E8)

private val TrailingPackSizeRegex = Regex("\\s*(?:\\(\\s*\\d+(?:\\.\\d+)?\\s*(?:kg|kgs|kilo|g|gm|l|ltr)\\s*\\)|\\b\\d+(?:\\.\\d+)?\\s*(?:kg|kgs)\\b)\\s*$", RegexOption.IGNORE_CASE)

private fun splitProductDisplayAndPack(product: Product): Pair<String, String> {
    val cleanedName = product.feedName
        .replace(TrailingPackSizeRegex, "")
        .trim()
        .ifBlank { product.feedName.trim() }

    val packSubtitle = if (product.isLooseOnly) {
        "Loose · ${CurrencyUtils.formatInr(product.effectiveLooseRatePerKg)}/kg"
    } else {
        val kgStr = product.safeKgPerBag.stripTrailingZeros().toPlainString()
        "${kgStr}kg · ${CurrencyUtils.formatInr(product.bagSellingPrice)}"
    }
    return cleanedName to packSubtitle
}

@Composable
fun BillingScreen(
    repository: CattleFeedRepository,
    currentUser: User?,
    onBack: () -> Unit,
    billingViewModel: BillingViewModel = viewModel()
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val products by repository.allProducts.collectAsState(initial = emptyList())
    val customers by repository.allCustomers.collectAsState(initial = emptyList())
    val allSales by repository.allSales.collectAsState(initial = emptyList())
    val allUsers by repository.allUsers.collectAsState(initial = emptyList())

    val selectedCustomer by billingViewModel.selectedCustomer.collectAsState()
    val isWalkInCashCustomer by billingViewModel.isWalkInCashCustomer.collectAsState()
    val customerSearchQuery by billingViewModel.customerSearchQuery.collectAsState()
    val paidAmountStr by billingViewModel.paidAmountStr.collectAsState()
    val selectedPayMode by billingViewModel.selectedPayMode.collectAsState()
    val promiseDateMillis by billingViewModel.promiseDateMillis.collectAsState()
    val customBillDateMillis by billingViewModel.customBillDateMillis.collectAsState()
    val generatedBill by billingViewModel.generatedBill.collectAsState()
    val cartLines = billingViewModel.cartLines

    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var showOverLimitConfirmDialog by remember { mutableStateOf(false) }
    var showBillTimingDialog by remember { mutableStateOf(false) }
    var showRateOverrideLineId by remember { mutableStateOf<String?>(null) }
    var showCustomerPicker by remember { mutableStateOf(false) }
    var productSearchQuery by remember { mutableStateOf("") }
    var showAllProducts by remember { mutableStateOf(false) }

    val canOverrideRate = can(currentUser?.role, Permission.OVERRIDE_BILL_RATE)
    val canViewFullCustomer = can(currentUser?.role, Permission.VIEW_CUSTOMER_FULL)

    // Rank products by actual sales frequency (most frequently sold first)
    val activeProducts = remember(products, allSales) {
        val active = products.filter { !it.isDeleted }
        if (allSales.isEmpty()) {
            active
        } else {
            val freqMap = HashMap<String, Int>()
            allSales.forEach { sale ->
                if (!sale.isDeleted) {
                    freqMap[sale.productId] = (freqMap[sale.productId] ?: 0) + 1
                }
            }
            active.sortedWith(
                compareByDescending<Product> { freqMap[it.id] ?: 0 }
                    .thenBy { it.feedName.lowercase(Locale.getDefault()) }
            )
        }
    }

    val filteredFeedProducts = remember(activeProducts, productSearchQuery) {
        if (productSearchQuery.isBlank()) activeProducts
        else activeProducts.filter { it.feedName.contains(productSearchQuery.trim(), ignoreCase = true) }
    }

    val displayedGridProducts = remember(filteredFeedProducts, productSearchQuery, showAllProducts) {
        if (productSearchQuery.isNotBlank() || showAllProducts) {
            filteredFeedProducts
        } else {
            filteredFeedProducts.take(6)
        }
    }

    // Recent customers ordered by latest sale timestamp, then updatedAt
    val recentCustomers = remember(customers, allSales, customerSearchQuery) {
        val active = customers.filter { !it.isDeleted }
        val latestSaleByCustomer = HashMap<String, Long>()
        allSales.forEach { sale ->
            if (!sale.isDeleted) {
                val prev = latestSaleByCustomer[sale.customerId] ?: 0L
                if (sale.date > prev) latestSaleByCustomer[sale.customerId] = sale.date
            }
        }
        val sorted = active.sortedWith(
            compareByDescending<Customer> { latestSaleByCustomer[it.id] ?: 0L }
                .thenByDescending { it.updatedAt }
        )
        if (customerSearchQuery.isBlank()) {
            sorted.take(12)
        } else {
            val q = customerSearchQuery.trim()
            sorted.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.village.contains(q, ignoreCase = true) ||
                    it.phone.contains(q) ||
                    it.fatherName.contains(q, ignoreCase = true)
            }.take(12)
        }
    }

    val lineCalculations = billingViewModel.calculateCartLines(activeProducts, currentUser?.role)

    val grandTotal = lineCalculations.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.third.second) }
    val paidAmount = when {
        isWalkInCashCustomer -> grandTotal
        selectedPayMode == "FULL" -> grandTotal
        selectedPayMode == "HALF" -> grandTotal.divide(BigDecimal("2"), 0, RoundingMode.HALF_UP)
        selectedPayMode == "UDHARI" -> BigDecimal.ZERO
        else -> (paidAmountStr.toBigDecimalOrNull() ?: BigDecimal.ZERO).max(BigDecimal.ZERO)
    }
    val udhariDue = if (isWalkInCashCustomer) BigDecimal.ZERO else grandTotal.subtract(paidAmount).max(BigDecimal.ZERO)

    // Credit guard calculations (creditLimit > 0 means active limit; 0 means no limit)
    val currentCustomerUdhari = selectedCustomer?.totalUdhari ?: BigDecimal.ZERO
    val customerCreditLimit = selectedCustomer?.creditLimit ?: BigDecimal.ZERO
    val hasCreditLimit = !isWalkInCashCustomer && selectedCustomer != null && customerCreditLimit > BigDecimal.ZERO
    val projectedTotalUdhari = currentCustomerUdhari.add(udhariDue)
    val remainingCreditBeforeBill = if (hasCreditLimit) customerCreditLimit.subtract(currentCustomerUdhari) else BigDecimal.ZERO
    val remainingCreditAfterBill = if (hasCreditLimit) customerCreditLimit.subtract(projectedTotalUdhari) else BigDecimal.ZERO
    val isExceedingCreditLimit = !isWalkInCashCustomer &&
        selectedCustomer != null &&
        isCustomerCreditBlocked(selectedCustomer!!, udhariDue)
    val isStaffBlockedByCreditLimit = isExceedingCreditLimit && currentUser?.role == Role.STAFF

    val shortDateTimeFmt = remember { SimpleDateFormat("d MMM · h:mm a", Locale.getDefault()) }
    val billDateFmt = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    val billTimeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val promiseShortFmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val effectiveTimingMillis = customBillDateMillis ?: System.currentTimeMillis()
    val isCustomTiming = customBillDateMillis != null
    val timingLinkLabel = if (isCustomTiming) {
        "${shortDateTimeFmt.format(Date(effectiveTimingMillis))} ✎"
    } else {
        tr("Now ✎", "अभी ✎")
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.lowercase(Locale.getDefault()) ?: ""
            if (spoken.isNotBlank()) {
                val matchedCustomer = customers.firstOrNull {
                    spoken.contains(it.name.lowercase(Locale.getDefault()))
                }
                if (matchedCustomer != null) {
                    billingViewModel.selectCustomer(matchedCustomer)
                    showCustomerPicker = false
                }

                val matchedProduct = activeProducts.firstOrNull { prod ->
                    val words = prod.feedName.lowercase(Locale.getDefault()).split(" ", "(", ")", "/")
                        .filter { it.length >= 3 }
                    words.any { spoken.contains(it) }
                }
                val numberRegex = Regex("(\\d+)")
                val foundNumber = numberRegex.find(spoken)?.value?.toIntOrNull() ?: 1

                if (matchedProduct != null) {
                    if (matchedProduct.isLooseOnly || spoken.contains("kg") || spoken.contains("kilo") || spoken.contains("किलो")) {
                        cartLines.add(
                            BillCartLine(
                                productId = matchedProduct.id,
                                bagsCount = 0,
                                looseKgStr = foundNumber.toString()
                            )
                        )
                    } else {
                        cartLines.add(
                            BillCartLine(
                                productId = matchedProduct.id,
                                bagsCount = foundNumber.coerceIn(1, 100),
                                looseKgStr = ""
                            )
                        )
                    }
                    Toast.makeText(context, "Voice added: ${matchedProduct.feedName} ($foundNumber)", Toast.LENGTH_SHORT).show()
                } else {
                    billingViewModel.setCustomerSearchQuery(spoken)
                    Toast.makeText(context, "Heard: \"$spoken\"", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun executeSaveBill() {
        val validItems = cartLines.mapNotNull { line ->
            val prod = activeProducts.find { it.id == line.productId } ?: return@mapNotNull null
            val bags = if (prod.isLooseOnly) 0 else line.bagsCount.coerceAtLeast(0)
            val loose = line.looseKgStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
            if (bags <= 0 && loose <= BigDecimal.ZERO) return@mapNotNull null
            SaleItemRequest(
                productId = prod.id,
                bags = bags,
                looseKg = loose,
                customBagPrice = if (canOverrideRate) line.customBagRateStr.toBigDecimalOrNull() else null,
                customLoosePricePerKg = if (canOverrideRate) line.customLooseRateStr.toBigDecimalOrNull() else null
            )
        }

        if (validItems.isEmpty()) {
            Toast.makeText(context, "Please add at least 1 feed item with quantity > 0", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            val targetCustomer = if (isWalkInCashCustomer) {
                val existingWalkIn = customers.firstOrNull {
                    it.name.equals("Walk-in", ignoreCase = true) ||
                        it.name.equals("Walk-in Cash Customer", ignoreCase = true)
                }
                if (existingWalkIn != null) {
                    existingWalkIn
                } else {
                    val walkIn = Customer(
                        name = "Walk-in",
                        village = "Counter Cash",
                        createdByUserId = currentUser?.id ?: ""
                    )
                    repository.saveCustomer(walkIn, currentUser)
                    walkIn
                }
            } else {
                selectedCustomer
            }

            if (targetCustomer == null) {
                Toast.makeText(context, "Please select a customer or choose Walk-in Cash", Toast.LENGTH_SHORT).show()
                return@launch
            }

            if (udhariDue > BigDecimal.ZERO && promiseDateMillis != null && canViewFullCustomer) {
                val updatedCust = targetCustomer.copy(promiseDate = promiseDateMillis)
                repository.saveCustomer(updatedCust, currentUser)
                ReminderScheduler.schedulePromiseReminder(
                    context = context,
                    customerId = updatedCust.id,
                    customerName = updatedCust.name,
                    udhariAmount = CurrencyUtils.formatInr(projectedTotalUdhari),
                    promiseDateMillis = promiseDateMillis!!
                )
            }

            try {
                val (_, billData) = repository.recordMultiItemSale(
                    customerId = targetCustomer.id,
                    items = validItems,
                    paidAmount = if (isWalkInCashCustomer) grandTotal else paidAmount,
                    soldByUserId = currentUser?.id ?: "admin",
                    performedByRole = currentUser?.role,
                    customDateMillis = customBillDateMillis
                )
                billingViewModel.setGeneratedBill(billData)
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Unable to save bill", Toast.LENGTH_LONG).show()
            }
        }
    }

    val isCustomerCollapsed = !showCustomerPicker && (selectedCustomer != null || isWalkInCashCustomer)

    Scaffold(
        containerColor = FlatDarkBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FlatDarkBg)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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
                            .testTag("new_bill_back_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = tr("Back", "वापस"),
                                tint = FlatTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = tr("New sale bill", "नया बिक्री बिल"),
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.25).sp,
                                color = FlatTextPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = tr("Quick chips · Voice · Credit guard", "तेज़ चिप्स · बोलकर लिखें · उधारी जांच"),
                            fontSize = 11.5.sp,
                            color = FlatTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    onClick = {
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak product & quantity (e.g., 'Kapila 5 bag' or 'Bajra 10 kilo')")
                            }
                            voiceLauncher.launch(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Voice input is not supported on this device", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("voice_bill_input_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = tr("Voice bill input", "बोलकर बिल दर्ज करें"),
                            tint = FlatTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            // 4. TIGHT PINNED FOOTER: Total / Paid / Due on one line + Quick paid buttons + Save button
            Surface(
                color = FlatDarkSurface,
                border = BorderStroke(1.dp, FlatDarkBorder),
                shadowElevation = 10.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Single compact line: Total · Paid (editable) · Due
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("TOTAL", "कुल"),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.4.sp
                            )
                            Text(
                                text = CurrencyUtils.formatInr(grandTotal),
                                style = TextStyle(
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary,
                                    fontFeatureSettings = "tnum"
                                ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1.25f)
                        ) {
                            Text(
                                text = tr("PAID", "जमा"),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.4.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val displayedPaidStr = if (isWalkInCashCustomer) {
                                grandTotal.stripTrailingZeros().toPlainString()
                            } else if (selectedPayMode == "CUSTOM") {
                                paidAmountStr
                            } else {
                                paidAmount.stripTrailingZeros().toPlainString()
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(FlatDarkCard)
                                    .border(1.25.dp, BillBrandGreen, RoundedCornerShape(9.dp))
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "₹",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextSecondary
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                BasicTextField(
                                    value = displayedPaidStr,
                                    onValueChange = { newVal ->
                                        if (!isWalkInCashCustomer && (newVal.isEmpty() || newVal.matches(Regex("^\\d*\\.?\\d*$")))) {
                                            billingViewModel.setCustomPaidAmount(newVal)
                                        }
                                    },
                                    enabled = !isWalkInCashCustomer,
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    textStyle = TextStyle(
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FlatTextPrimary,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    cursorBrush = SolidColor(BillBrandGreen),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("paid_amount_input")
                                )
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = tr("DUE", "बाक़ी"),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.4.sp
                            )
                            Text(
                                text = CurrencyUtils.formatInr(udhariDue),
                                style = TextStyle(
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (udhariDue > BigDecimal.ZERO) BillCrimson else FlatTextSecondary,
                                    fontFeatureSettings = "tnum"
                                ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Quick Paid Buttons: Full · Half · Udhari (+ compact Promise date when due > 0)
                    if (!isWalkInCashCustomer && grandTotal > BigDecimal.ZERO) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = { billingViewModel.setPayMode("FULL", grandTotal) },
                                shape = RoundedCornerShape(9.dp),
                                color = if (selectedPayMode == "FULL") BillMintPillBg else FlatDarkCard,
                                border = BorderStroke(
                                    width = if (selectedPayMode == "FULL") 1.5.dp else 1.dp,
                                    color = if (selectedPayMode == "FULL") BillBrandGreen else FlatDarkBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .testTag("pay_mode_full_chip")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = tr("Full", "पूरा"),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedPayMode == "FULL") BillBrandGreen else FlatTextPrimary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Surface(
                                onClick = { billingViewModel.setPayMode("HALF", grandTotal) },
                                shape = RoundedCornerShape(9.dp),
                                color = if (selectedPayMode == "HALF") CAmberBg else FlatDarkCard,
                                border = BorderStroke(
                                    width = if (selectedPayMode == "HALF") 1.5.dp else 1.dp,
                                    color = if (selectedPayMode == "HALF") CAmber else FlatDarkBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .testTag("pay_mode_half_chip")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = tr("Half", "आधा"),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedPayMode == "HALF") CAmber else FlatTextPrimary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Surface(
                                onClick = { billingViewModel.setPayMode("UDHARI", grandTotal) },
                                shape = RoundedCornerShape(9.dp),
                                color = if (selectedPayMode == "UDHARI") BillCrimsonSoftBg else FlatDarkCard,
                                border = BorderStroke(
                                    width = if (selectedPayMode == "UDHARI") 1.5.dp else 1.dp,
                                    color = if (selectedPayMode == "UDHARI") BillCrimson else FlatDarkBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .testTag("pay_mode_udhari_chip")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = tr("Udhari", "उधारी"),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedPayMode == "UDHARI") BillCrimson else FlatTextPrimary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            if (udhariDue > BigDecimal.ZERO && canViewFullCustomer) {
                                Surface(
                                    onClick = {
                                        val cal = Calendar.getInstance()
                                        DatePickerDialog(
                                            pickerThemedContext(context),
                                            { _, year, month, dayOfMonth ->
                                                val chosen = Calendar.getInstance().apply {
                                                    set(year, month, dayOfMonth, 9, 0, 0)
                                                }
                                                billingViewModel.setPromiseDate(chosen.timeInMillis)
                                            },
                                            cal.get(Calendar.YEAR),
                                            cal.get(Calendar.MONTH),
                                            cal.get(Calendar.DAY_OF_MONTH)
                                        ).show()
                                    },
                                    shape = RoundedCornerShape(9.dp),
                                    color = if (promiseDateMillis != null) CAmberBg else FlatDarkCard,
                                    border = BorderStroke(1.dp, CAmberBorder),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CalendarToday,
                                            contentDescription = tr("Promise date", "वायदा तिथि"),
                                            tint = CAmber,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = if (promiseDateMillis != null) {
                                                promiseShortFmt.format(Date(promiseDateMillis!!))
                                            } else {
                                                tr("Promise", "वायदा")
                                            },
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CAmber,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Primary Save Button
                    Button(
                        onClick = {
                            if (isExceedingCreditLimit) {
                                if (currentUser?.role == Role.STAFF) {
                                    Toast.makeText(
                                        context,
                                        "Credit Blocked: Staff cannot record udhari exceeding customer credit limit.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    showOverLimitConfirmDialog = true
                                }
                            } else {
                                executeSaveBill()
                            }
                        },
                        enabled = grandTotal > BigDecimal.ZERO &&
                            (selectedCustomer != null || isWalkInCashCustomer) &&
                            !isStaffBlockedByCreditLimit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isStaffBlockedByCreditLimit) BillCrimson else BillBrandGreen,
                            contentColor = Color.White,
                            disabledContainerColor = BillBrandGreen.copy(alpha = 0.45f),
                            disabledContentColor = Color.White.copy(alpha = 0.8f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("submit_sale_button")
                    ) {
                        if (isStaffBlockedByCreditLimit) {
                            Icon(
                                Icons.Default.Block,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = if (isStaffBlockedByCreditLimit) {
                                tr("Credit Blocked (Over Limit)", "उधारी सीमा पार - बिल ब्लॉक")
                            } else if (grandTotal > BigDecimal.ZERO) {
                                tr(
                                    "Save & Share Bill · ${CurrencyUtils.formatInr(grandTotal)}",
                                    "बिल सेव करें · ${CurrencyUtils.formatInr(grandTotal)}"
                                )
                            } else {
                                tr("Save & Share Bill", "बिल सेव करें")
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // =========================================================================
            // 1. CUSTOMER CARD (Collapsed to 1 primary row after pick + Credit guard + "Now ✎")
            // =========================================================================
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder)
                ) {
                    if (isCustomerCollapsed) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Collapsed single row: "Ramu · Gohra · Due ₹3,078" ... "Change"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (isWalkInCashCustomer) {
                                    Text(
                                        text = buildAnnotatedString {
                                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = FlatTextPrimary)) {
                                                append(tr("Walk-in Cash", "नकद ग्राहक"))
                                            }
                                            withStyle(SpanStyle(color = FlatTextSecondary)) {
                                                append(" · ")
                                                append(tr("Counter cash", "काउंटर नकद"))
                                            }
                                        },
                                        fontSize = 14.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    val cust = selectedCustomer!!
                                    val statusChip = getCustomerStatusChip(cust)
                                    Text(
                                        text = buildAnnotatedString {
                                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = FlatTextPrimary)) {
                                                append(cust.name)
                                            }
                                            if (cust.village.isNotBlank()) {
                                                withStyle(SpanStyle(color = FlatTextSecondary)) {
                                                    append(" · ${cust.village}")
                                                }
                                            }
                                            if (canViewFullCustomer) {
                                                withStyle(SpanStyle(color = FlatTextSecondary)) {
                                                    append(" · ")
                                                }
                                                withStyle(
                                                    SpanStyle(
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (cust.totalUdhari > BigDecimal.ZERO) BillCrimson else BillBrandGreen
                                                    )
                                                ) {
                                                    append(
                                                        if (cust.totalUdhari > BigDecimal.ZERO) {
                                                            "Due ${CurrencyUtils.formatInr(cust.totalUdhari)}"
                                                        } else {
                                                            tr("Clear", "कोई बाक़ी नहीं")
                                                        }
                                                    )
                                                }
                                            } else {
                                                withStyle(SpanStyle(color = FlatTextSecondary)) {
                                                    append(" · ")
                                                }
                                                withStyle(
                                                    SpanStyle(
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = when (statusChip) {
                                                            CustomerStatusChip.CLEAR -> BillBrandGreen
                                                            CustomerStatusChip.HAS_UDHARI -> CAmber
                                                            CustomerStatusChip.OVER_LIMIT -> BillCrimson
                                                        }
                                                    )
                                                ) {
                                                    append(tr(statusChip.labelEn, statusChip.labelHi))
                                                }
                                            }
                                        },
                                        fontSize = 14.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = tr("Change", "बदलें"),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BillBrandGreen,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showCustomerPicker = true }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .testTag("change_customer_btn")
                                )
                            }

                            // Compact Credit Guard + "Now ✎" timing link row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (!isWalkInCashCustomer && selectedCustomer != null) {
                                    val cust = selectedCustomer!!
                                    if (canViewFullCustomer && hasCreditLimit) {
                                        val effectiveRemaining = if (udhariDue > BigDecimal.ZERO) remainingCreditAfterBill else remainingCreditBeforeBill
                                        val isOver = effectiveRemaining < BigDecimal.ZERO
                                        val isTight = !isOver && customerCreditLimit > BigDecimal.ZERO &&
                                            effectiveRemaining.multiply(BigDecimal("5")) <= customerCreditLimit
                                        val guardText = if (isOver) {
                                            "Over ${CurrencyUtils.formatInr(customerCreditLimit)} by ${CurrencyUtils.formatInr(effectiveRemaining.abs())}"
                                        } else {
                                            "${CurrencyUtils.formatInr(effectiveRemaining)} left of ${CurrencyUtils.formatInr(customerCreditLimit)}"
                                        }
                                        val guardColor = when {
                                            isOver -> BillCrimson
                                            isTight -> CAmber
                                            else -> BillBrandGreen
                                        }
                                        Text(
                                            text = guardText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = guardColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("customer_credit_guard_text")
                                        )
                                    } else if (!canViewFullCustomer) {
                                        val creditFlagBlocked = isCustomerCreditBlocked(cust, udhariDue.max(BigDecimal.ONE))
                                        Text(
                                            text = if (creditFlagBlocked) tr("Credit: Blocked", "क्रेडिट: ब्लॉक") else tr("Credit OK", "क्रेडिट: ठीक है"),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (creditFlagBlocked) BillCrimson else BillBrandGreen,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("staff_credit_flag")
                                        )
                                    } else {
                                        Text(
                                            text = tr("No credit limit", "कोई उधारी सीमा नहीं"),
                                            fontSize = 12.sp,
                                            color = FlatTextMuted,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = tr("Full cash payment", "पूरा नकद भुगतान"),
                                        fontSize = 12.sp,
                                        color = FlatTextSecondary,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = timingLinkLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isCustomTiming) BillBrandGreen else FlatTextSecondary,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isCustomTiming) BillMintPillBg else Color.Transparent)
                                        .clickable { showBillTimingDialog = true }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .testTag("bill_timing_trigger_btn")
                                )
                            }
                        }
                    } else {
                        // Expanded customer picker state
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tr("CUSTOMER", "ग्राहक"),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextSecondary,
                                    letterSpacing = 0.6.sp
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Single compact "Now ✎" timing link (replaces bulky date/time boxes)
                                    Text(
                                        text = timingLinkLabel,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isCustomTiming) BillBrandGreen else FlatTextSecondary,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isCustomTiming) BillMintPillBg else FlatDarkBg)
                                            .clickable { showBillTimingDialog = true }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .testTag("bill_timing_trigger_btn")
                                    )

                                    // Fixed single "+" icon with "Customer" label (no doubled "+")
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showAddCustomerDialog = true }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                            .testTag("new_bill_add_customer_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = BillBrandGreen,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = tr("Customer", "नया ग्राहक"),
                                            color = BillBrandGreen,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            // Segmented Toggle: Walk-in Cash | Customer
                            val walkInPillBg by animateColorAsState(
                                targetValue = if (isWalkInCashCustomer) BillBrandGreen else Color.Transparent,
                                animationSpec = tween(durationMillis = 150),
                                label = "walkInPillBg"
                            )
                            val walkInPillFg by animateColorAsState(
                                targetValue = if (isWalkInCashCustomer) Color.White else FlatTextSecondary,
                                animationSpec = tween(durationMillis = 150),
                                label = "walkInPillFg"
                            )
                            val customerPillBg by animateColorAsState(
                                targetValue = if (!isWalkInCashCustomer) BillBrandGreen else Color.Transparent,
                                animationSpec = tween(durationMillis = 150),
                                label = "customerPillBg"
                            )
                            val customerPillFg by animateColorAsState(
                                targetValue = if (!isWalkInCashCustomer) Color.White else FlatTextSecondary,
                                animationSpec = tween(durationMillis = 150),
                                label = "customerPillFg"
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = FlatDarkBg,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Surface(
                                        onClick = {
                                            billingViewModel.toggleWalkInCash(true)
                                            showCustomerPicker = false
                                        },
                                        shape = RoundedCornerShape(9.dp),
                                        color = walkInPillBg,
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 36.dp)
                                            .testTag("walk_in_cash_chip")
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = tr("Walk-in Cash", "नकद ग्राहक"),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = walkInPillFg,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Surface(
                                        onClick = { billingViewModel.toggleWalkInCash(false) },
                                        shape = RoundedCornerShape(9.dp),
                                        color = customerPillBg,
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 36.dp)
                                            .testTag("customer_mode_chip")
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = tr("Customer", "खाता ग्राहक"),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = customerPillFg,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            if (!isWalkInCashCustomer) {
                                OutlinedTextField(
                                    value = customerSearchQuery,
                                    onValueChange = { billingViewModel.setCustomerSearchQuery(it) },
                                    placeholder = {
                                        Text(
                                            tr("Search customer by name or village…", "नाम या गाँव से ग्राहक खोजें…"),
                                            fontSize = 13.5.sp,
                                            color = FlatTextMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = FlatTextMuted, modifier = Modifier.size(18.dp))
                                    },
                                    trailingIcon = {
                                        if (customerSearchQuery.isNotEmpty()) {
                                            IconButton(onClick = { billingViewModel.setCustomerSearchQuery("") }) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = FlatTextMuted, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = FlatDarkBg,
                                        unfocusedContainerColor = FlatDarkBg,
                                        focusedBorderColor = BillBrandGreen,
                                        unfocusedBorderColor = FlatDarkBorder,
                                        focusedTextColor = FlatTextPrimary,
                                        unfocusedTextColor = FlatTextPrimary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("bill_customer_search_input")
                                )

                                // Recent customers in a single horizontal row
                                if (recentCustomers.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        recentCustomers.forEach { cust ->
                                            val isSelected = selectedCustomer?.id == cust.id
                                            val statusChip = getCustomerStatusChip(cust)
                                            Surface(
                                                onClick = {
                                                    billingViewModel.selectCustomer(cust)
                                                    billingViewModel.setCustomerSearchQuery("")
                                                    showCustomerPicker = false
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isSelected) BillMintPillBg else FlatDarkBg,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSelected) BillBrandGreen else FlatDarkBorder
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                                ) {
                                                    Text(
                                                        text = cust.name,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) BillBrandGreen else FlatTextPrimary,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    val subText = if (canViewFullCustomer) {
                                                        when {
                                                            cust.totalUdhari > BigDecimal.ZERO && cust.village.isNotBlank() ->
                                                                "${cust.village} · ${CurrencyUtils.formatInr(cust.totalUdhari)}"
                                                            cust.totalUdhari > BigDecimal.ZERO ->
                                                                "Due ${CurrencyUtils.formatInr(cust.totalUdhari)}"
                                                            else -> cust.village.ifBlank { tr("Clear", "क्लियर") }
                                                        }
                                                    } else {
                                                        tr(statusChip.labelEn, statusChip.labelHi)
                                                    }
                                                    val subColor = if (canViewFullCustomer) {
                                                        if (cust.totalUdhari > BigDecimal.ZERO) BillCrimson else FlatTextSecondary
                                                    } else {
                                                        when (statusChip) {
                                                            CustomerStatusChip.CLEAR -> BillBrandGreen
                                                            CustomerStatusChip.HAS_UDHARI -> CAmber
                                                            CustomerStatusChip.OVER_LIMIT -> BillCrimson
                                                        }
                                                    }
                                                    Text(
                                                        text = subText,
                                                        fontSize = 11.sp,
                                                        color = subColor,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (selectedCustomer != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Text(
                                            text = tr("Done", "हो गया"),
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BillBrandGreen,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { showCustomerPicker = false }
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // CREDIT GUARD BILL-LEVEL WARNING (when udhari due crosses limit)
            // =========================================================================
            if (isExceedingCreditLimit) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("credit_limit_warning_card"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CRedUdhariBg),
                        border = BorderStroke(1.25.dp, CRedUdhari)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Credit Limit Exceeded",
                                tint = CRedUdhari,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (currentUser?.role == Role.STAFF) {
                                        tr("Credit Blocked · Over Limit", "उधारी सीमा पार · क्रेडिट ब्लॉक")
                                    } else {
                                        tr(
                                            "Crosses ${CurrencyUtils.formatInr(customerCreditLimit)} credit limit",
                                            "${CurrencyUtils.formatInr(customerCreditLimit)} उधारी सीमा पार"
                                        )
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CRedUdhari
                                )
                                Text(
                                    text = if (!canViewFullCustomer) {
                                        tr(
                                            "This udhari bill exceeds the customer's credit limit. Staff cannot override.",
                                            "यह उधारी बिल ग्राहक की क्रेडिट सीमा से अधिक है।"
                                        )
                                    } else {
                                        val overBy = projectedTotalUdhari.subtract(customerCreditLimit).max(BigDecimal.ZERO)
                                        "New due ${CurrencyUtils.formatInr(projectedTotalUdhari)} is ${CurrencyUtils.formatInr(overBy)} over ${selectedCustomer?.name}'s limit."
                                    },
                                    fontSize = 12.sp,
                                    color = FlatTextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 2. BILL ITEMS + RUNNING TOTAL (Placed FIRST, right under customer)
            // =========================================================================
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (cartLines.isEmpty()) {
                                    tr("BILL ITEMS", "बिल के सामान")
                                } else {
                                    tr("BILL ITEMS · ${cartLines.size}", "बिल के सामान · ${cartLines.size}")
                                },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.6.sp
                            )
                            if (cartLines.isNotEmpty()) {
                                Text(
                                    text = CurrencyUtils.formatInr(grandTotal),
                                    style = TextStyle(
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BillBrandGreen,
                                        fontFeatureSettings = "tnum"
                                    )
                                )
                            }
                        }

                        if (cartLines.isEmpty()) {
                            Text(
                                text = tr(
                                    "No items yet — tap any item below or use Voice.",
                                    "अभी कोई सामान नहीं — नीचे टैप करें या बोलकर जोड़ें।"
                                ),
                                fontSize = 13.sp,
                                color = FlatTextMuted,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        } else {
                            cartLines.forEachIndexed { index, line ->
                                val product = activeProducts.find { it.id == line.productId } ?: return@forEachIndexed
                                val calc = lineCalculations.find { it.first.id == line.id }
                                val lineTotal = calc?.third?.second ?: BigDecimal.ZERO
                                val looseVal = line.looseKgStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
                                val isExpandedOptions = showRateOverrideLineId == line.id
                                val (cleanItemName, _) = remember(product) { splitProductDisplayAndPack(product) }

                                val rateLabel = if (!product.isLooseOnly && line.bagsCount > 0 && looseVal <= BigDecimal.ZERO) {
                                    "${CurrencyUtils.formatInr(product.bagSellingPrice)}/bag"
                                } else if (product.isLooseOnly || (line.bagsCount == 0 && looseVal > BigDecimal.ZERO)) {
                                    "${CurrencyUtils.formatInr(product.effectiveLooseRatePerKg)}/kg"
                                } else {
                                    "${CurrencyUtils.formatInr(product.bagSellingPrice)}/b · ${CurrencyUtils.formatInr(product.effectiveLooseRatePerKg)}/kg"
                                }

                                val qtyDisplayText = when {
                                    !product.isLooseOnly && line.bagsCount > 0 && looseVal <= BigDecimal.ZERO ->
                                        "${line.bagsCount}"
                                    line.bagsCount == 0 && looseVal > BigDecimal.ZERO ->
                                        "${line.looseKgStr}kg"
                                    !product.isLooseOnly && line.bagsCount > 0 && looseVal > BigDecimal.ZERO ->
                                        "${line.bagsCount}b+${line.looseKgStr}k"
                                    product.isLooseOnly ->
                                        "${line.looseKgStr.ifBlank { "0" }}kg"
                                    else ->
                                        "${line.bagsCount}"
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showRateOverrideLineId = if (isExpandedOptions) null else line.id
                                            }
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Compact Name + Rate
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = cleanItemName,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FlatTextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = rateLabel,
                                                    fontSize = 11.5.sp,
                                                    color = FlatTextSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (canOverrideRate) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Override Rate",
                                                        tint = CAmber,
                                                        modifier = Modifier
                                                            .size(12.dp)
                                                            .testTag("override_rate_btn_${product.id}")
                                                    )
                                                }
                                            }
                                        }

                                        // Compact Qty Stepper
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                onClick = {
                                                    if (product.isLooseOnly || (line.bagsCount == 0 && looseVal > BigDecimal.ZERO)) {
                                                        val nextKg = looseVal.subtract(BigDecimal.ONE)
                                                        if (nextKg <= BigDecimal.ZERO && line.bagsCount <= 0) {
                                                            cartLines.removeAt(index)
                                                        } else {
                                                            cartLines[index] = line.copy(
                                                                looseKgStr = nextKg.max(BigDecimal.ZERO).stripTrailingZeros().toPlainString()
                                                            )
                                                        }
                                                    } else {
                                                        val nextBags = line.bagsCount - 1
                                                        if (nextBags <= 0 && looseVal <= BigDecimal.ZERO) {
                                                            cartLines.removeAt(index)
                                                        } else {
                                                            cartLines[index] = line.copy(bagsCount = nextBags.coerceAtLeast(0))
                                                        }
                                                    }
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = FlatDarkInput,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.Remove,
                                                        contentDescription = "Decrease quantity",
                                                        tint = FlatTextPrimary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            Text(
                                                text = qtyDisplayText,
                                                style = TextStyle(
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = FlatTextPrimary,
                                                    textAlign = TextAlign.Center,
                                                    fontFeatureSettings = "tnum"
                                                ),
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.widthIn(min = 28.dp)
                                            )

                                            Surface(
                                                onClick = {
                                                    if (product.isLooseOnly || (line.bagsCount == 0 && looseVal > BigDecimal.ZERO)) {
                                                        val nextKg = looseVal.add(BigDecimal.ONE)
                                                        cartLines[index] = line.copy(
                                                            looseKgStr = nextKg.stripTrailingZeros().toPlainString()
                                                        )
                                                    } else {
                                                        cartLines[index] = line.copy(bagsCount = line.bagsCount + 1)
                                                    }
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = BillBrandGreen,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.Add,
                                                        contentDescription = "Increase quantity",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Line Price
                                        Text(
                                            text = CurrencyUtils.formatInr(lineTotal),
                                            style = TextStyle(
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FlatTextPrimary,
                                                textAlign = TextAlign.End,
                                                fontFeatureSettings = "tnum"
                                            ),
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.widthIn(min = 64.dp)
                                        )
                                    }

                                    // Expanded Inline Editor for Loose Kg, Rate Override, and Remove
                                    if (isExpandedOptions) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = FlatDarkBg,
                                            border = BorderStroke(1.dp, FlatDarkBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = tr("Loose Weight / Custom Rate", "खुला किलो / कस्टम रेट"),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = FlatTextSecondary
                                                    )
                                                    IconButton(
                                                        onClick = { cartLines.removeAt(index) },
                                                        modifier = Modifier.size(34.dp)
                                                    ) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Remove Item", tint = BillCrimson, modifier = Modifier.size(17.dp))
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    listOf("1", "5", "10", "25").forEach { kgPreset ->
                                                        FilterChip(
                                                            selected = line.looseKgStr == kgPreset,
                                                            onClick = {
                                                                val nextVal = if (line.looseKgStr == kgPreset) "" else kgPreset
                                                                cartLines[index] = line.copy(looseKgStr = nextVal)
                                                            },
                                                            label = {
                                                                Text(
                                                                    text = "${kgPreset}kg",
                                                                    fontWeight = FontWeight.Bold,
                                                                    maxLines = 1,
                                                                    softWrap = false
                                                                )
                                                            },
                                                            colors = FilterChipDefaults.filterChipColors(
                                                                selectedContainerColor = BillMintPillBg,
                                                                selectedLabelColor = BillBrandGreen
                                                            )
                                                        )
                                                    }
                                                }

                                                OutlinedTextField(
                                                    value = line.looseKgStr,
                                                    onValueChange = { v ->
                                                        cartLines[index] = line.copy(looseKgStr = v)
                                                    },
                                                    label = { Text(tr("Loose Weight (Kg)", "खुला वज़न (किलो)")) },
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                    modifier = Modifier.fillMaxWidth()
                                                )

                                                if (canOverrideRate) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        if (!product.isLooseOnly) {
                                                            OutlinedTextField(
                                                                value = line.customBagRateStr,
                                                                onValueChange = { v -> cartLines[index] = line.copy(customBagRateStr = v) },
                                                                label = { Text("Bag ₹") },
                                                                singleLine = true,
                                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                        }
                                                        OutlinedTextField(
                                                            value = line.customLooseRateStr,
                                                            onValueChange = { v -> cartLines[index] = line.copy(customLooseRateStr = v) },
                                                            label = { Text("Loose ₹/kg") },
                                                            singleLine = true,
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (index < cartLines.lastIndex) {
                                        HorizontalDivider(color = FlatDarkBorder)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 3. ADD ITEMS (Search + 2-Column Grid of Top 6 Frequently Sold + "See all")
            // =========================================================================
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("ADD ITEMS", "सामान जोड़ें"),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.6.sp
                            )

                            if (productSearchQuery.isBlank() && activeProducts.size > 6) {
                                Text(
                                    text = if (showAllProducts) {
                                        tr("Show top 6", "शीर्ष 6 दिखाएं")
                                    } else {
                                        tr("See all (${activeProducts.size})", "सभी देखें (${activeProducts.size})")
                                    },
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BillBrandGreen,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showAllProducts = !showAllProducts }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .testTag("billing_see_all_products_btn")
                                )
                            }
                        }

                        OutlinedTextField(
                            value = productSearchQuery,
                            onValueChange = { productSearchQuery = it },
                            placeholder = {
                                Text(
                                    text = tr("Search item…", "सामान खोजें…"),
                                    color = FlatTextMuted,
                                    fontSize = 13.5.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = BillBrandGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (productSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { productSearchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = tr("Close", "बंद करें"), tint = FlatTextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = FlatDarkBg,
                                unfocusedContainerColor = FlatDarkBg,
                                focusedBorderColor = BillBrandGreen,
                                unfocusedBorderColor = FlatDarkBorder,
                                focusedTextColor = FlatTextPrimary,
                                unfocusedTextColor = FlatTextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = if (productSearchQuery.isBlank()) {
                                if (showAllProducts) {
                                    tr("All items", "सभी सामान")
                                } else {
                                    tr("Frequently sold", "अक्सर बिकने वाले")
                                }
                            } else {
                                tr("${displayedGridProducts.size} matching items", "${displayedGridProducts.size} सामान मिले")
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FlatTextSecondary
                        )

                        if (displayedGridProducts.isEmpty()) {
                            Text(
                                text = tr("No matching inventory items found.", "कोई सामान नहीं मिला।"),
                                fontSize = 13.sp,
                                color = FlatTextMuted,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            // 2-Column Grid of Short Chips with Pack Size on Second Line
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                displayedGridProducts.chunked(2).forEach { pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        pair.forEach { prod ->
                                            val cartLine = cartLines.find { it.productId == prod.id }
                                            val inCartQtyLabel = when {
                                                cartLine == null -> null
                                                prod.isLooseOnly -> {
                                                    val kg = cartLine.looseKgStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
                                                    if (kg > BigDecimal.ZERO) "${kg.stripTrailingZeros().toPlainString()}kg" else null
                                                }
                                                cartLine.bagsCount > 0 -> "×${cartLine.bagsCount}"
                                                else -> null
                                            }
                                            val isSelected = inCartQtyLabel != null
                                            val (shortName, packSubtitle) = remember(prod) { splitProductDisplayAndPack(prod) }

                                            Surface(
                                                onClick = { billingViewModel.addOrIncrementProduct(prod) },
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isSelected) BillMintPillBg else FlatDarkBg,
                                                border = BorderStroke(
                                                    width = if (isSelected) 1.25.dp else 1.dp,
                                                    color = if (isSelected) BillBrandGreen else FlatDarkBorder
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .defaultMinSize(minHeight = 56.dp)
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 11.dp, vertical = 9.dp),
                                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = shortName,
                                                            fontSize = 13.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isSelected) BillBrandGreen else FlatTextPrimary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        if (inCartQtyLabel != null) {
                                                            Surface(
                                                                shape = CircleShape,
                                                                color = BillBrandGreen
                                                            ) {
                                                                Text(
                                                                    text = inCartQtyLabel,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color.White,
                                                                    maxLines = 1,
                                                                    softWrap = false,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                        } else {
                                                            Icon(
                                                                imageVector = Icons.Default.Add,
                                                                contentDescription = null,
                                                                tint = BillBrandGreen,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }

                                                    Text(
                                                        text = packSubtitle,
                                                        fontSize = 11.5.sp,
                                                        color = if (isSelected) BillBrandGreen.copy(alpha = 0.85f) else FlatTextSecondary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                        if (pair.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
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

    // Bill Timing Dialog (opened from "Now ✎" link)
    if (showBillTimingDialog) {
        AlertDialog(
            onDismissRequest = { showBillTimingDialog = false },
            containerColor = FlatDarkCard,
            title = {
                Text(
                    text = tr("Bill date & time", "बिल की तारीख और समय"),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = effectiveTimingMillis }
                            DatePickerDialog(
                                pickerThemedContext(context),
                                { _, year, month, dayOfMonth ->
                                    cal.set(Calendar.YEAR, year)
                                    cal.set(Calendar.MONTH, month)
                                    cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    billingViewModel.setCustomBillDateMillis(cal.timeInMillis)
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = FlatDarkBg,
                        border = BorderStroke(1.dp, if (isCustomTiming) BillBrandGreen else FlatDarkBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("bill_custom_date_btn")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = BillBrandGreen, modifier = Modifier.size(18.dp))
                                Text(
                                    text = billDateFmt.format(Date(effectiveTimingMillis)),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FlatTextPrimary
                                )
                            }
                            Text(tr("Change", "बदलें"), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = BillBrandGreen)
                        }
                    }

                    Surface(
                        onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = effectiveTimingMillis }
                            TimePickerDialog(
                                pickerThemedContext(context),
                                { _, hourOfDay, minute ->
                                    cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                    cal.set(Calendar.MINUTE, minute)
                                    cal.set(Calendar.SECOND, 0)
                                    billingViewModel.setCustomBillDateMillis(cal.timeInMillis)
                                },
                                cal.get(Calendar.HOUR_OF_DAY),
                                cal.get(Calendar.MINUTE),
                                false
                            ).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = FlatDarkBg,
                        border = BorderStroke(1.dp, if (isCustomTiming) BillBrandGreen else FlatDarkBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("bill_custom_time_btn")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, tint = BillBrandGreen, modifier = Modifier.size(18.dp))
                                Text(
                                    text = billTimeFmt.format(Date(effectiveTimingMillis)),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FlatTextPrimary
                                )
                            }
                            Text(tr("Change", "बदलें"), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = BillBrandGreen)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showBillTimingDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BillBrandGreen, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(tr("Done", "हो गया"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                if (isCustomTiming) {
                    TextButton(
                        onClick = {
                            billingViewModel.setCustomBillDateMillis(null)
                            showBillTimingDialog = false
                        },
                        modifier = Modifier.testTag("bill_timing_reset_now_btn")
                    ) {
                        Text(tr("Reset to Now", "अभी का समय"), color = BillBrandGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }

    // Over-Credit-Limit Confirmation / Admin Approval Dialog
    if (showOverLimitConfirmDialog) {
        val isManager = currentUser?.role == Role.MANAGER
        var adminApprovalPin by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showOverLimitConfirmDialog = false },
            containerColor = FlatDarkCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (isManager) Icons.Default.Lock else Icons.Default.Warning, contentDescription = null, tint = CRedUdhari)
                    Text(
                        text = if (isManager) tr("Admin approval required", "एडमिन की अनुमति आवश्यक") else tr("Credit limit exceeded", "उधारी सीमा पार!"),
                        color = CRedUdhari,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "This bill brings ${selectedCustomer?.name}'s total Udhari to ${CurrencyUtils.formatInr(projectedTotalUdhari)}, which is above their limit of ${CurrencyUtils.formatInr(customerCreditLimit)}.",
                        color = FlatTextPrimary
                    )
                    if (isManager) {
                        Text(
                            text = tr(
                                "Managers need Administrator approval to override a customer's credit limit. Enter Admin PIN below:",
                                "ग्राहक की उधारी सीमा पार करने के लिए एडमिन पिन दर्ज करें:"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = CAmber
                        )
                        OutlinedTextField(
                            value = adminApprovalPin,
                            onValueChange = {
                                if (it.length <= 4 && it.all { ch -> ch.isDigit() }) {
                                    adminApprovalPin = it
                                    pinError = null
                                }
                            },
                            label = { Text(tr("Admin 4-Digit PIN", "एडमिन 4-अंकीय पिन")) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (pinError != null) {
                            Text(pinError!!, color = CRedUdhari, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                val adminPinRequiredMsg = tr(
                    "Admin must set a 4-digit PIN in Settings before approving credit overrides.",
                    "क्रेडिट ओवरराइड मंजूर करने से पहले एडमिन को सेटिंग्स में 4-अंकीय पिन सेट करना होगा।"
                )
                val invalidAdminPinMsg = tr("Invalid Administrator PIN", "गलत एडमिन पिन")
                val lockedAdminPinTemplate = tr(
                    "Too many failed attempts! Locked for %ds.",
                    "बहुत सारे गलत प्रयास! %d सेकंड के लिए लॉक।"
                )
                Button(
                    onClick = {
                        if (isManager) {
                            val adminsWithPin = allUsers.filter {
                                !it.isDeleted && it.isActive && it.role == Role.ADMINISTRATOR && it.pinHash.isNotBlank()
                            }
                            val lockoutKey = "admin_override"
                            if (adminsWithPin.isEmpty()) {
                                pinError = adminPinRequiredMsg
                            } else if (SecurityUtils.isPinLockedOut(lockoutKey)) {
                                val remSec = SecurityUtils.getRemainingLockoutSeconds(lockoutKey)
                                pinError = String.format(Locale.getDefault(), lockedAdminPinTemplate, remSec)
                            } else {
                                val approved = adminsWithPin.any { admin ->
                                    SecurityUtils.verifyPin(adminApprovalPin, admin.pinHash)
                                }
                                if (approved) {
                                    SecurityUtils.resetLockout(lockoutKey)
                                    showOverLimitConfirmDialog = false
                                    executeSaveBill()
                                } else {
                                    val res = SecurityUtils.recordFailedPinAttempt(userId = lockoutKey)
                                    pinError = if (res.isLockedOut) {
                                        String.format(
                                            Locale.getDefault(),
                                            lockedAdminPinTemplate,
                                            res.remainingLockoutSeconds
                                        )
                                    } else {
                                        invalidAdminPinMsg
                                    }
                                }
                            }
                        } else {
                            showOverLimitConfirmDialog = false
                            executeSaveBill()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C), contentColor = Color.White)
                ) {
                    Text(
                        text = if (isManager) tr("Verify Admin & Approve", "एडमिन पिन सत्यापित करें") else tr("Approve Over-Limit Sale", "उधारी बिल मंजूर करें"),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverLimitConfirmDialog = false }) {
                    Text(tr("Cancel", "रद्द करें"), color = FlatTextSecondary)
                }
            }
        )
    }

    // Add New Customer Dialog using the redesigned QuickAddCustomerDialog
    if (showAddCustomerDialog) {
        QuickAddCustomerDialog(
            currentUserId = currentUser?.id ?: "",
            currentUserRole = currentUser?.role,
            onDismiss = { showAddCustomerDialog = false },
            onCustomerAdded = { customer ->
                scope.launch {
                    repository.saveCustomer(customer, currentUser)
                    billingViewModel.selectCustomer(customer)
                    showCustomerPicker = false
                    showAddCustomerDialog = false
                }
            }
        )
    }

    // Bill Generated Dialog
    generatedBill?.let { bill ->
        AlertDialog(
            onDismissRequest = {
                billingViewModel.setGeneratedBill(null)
                onBack()
            },
            containerColor = FlatDarkCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CGreen)
                    Text(tr("Bill #${bill.billNumber} Saved!", "बिल #${bill.billNumber} सेव हो गया!"), color = FlatTextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${bill.customerName} (${bill.customerVillage.ifBlank { "Local" }})", fontWeight = FontWeight.Bold, color = FlatTextPrimary)
                    HorizontalDivider(color = FlatDarkBorder)
                    bill.items.forEach { item ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${item.feedName} (${CurrencyUtils.formatWeight(item.totalKg)}kg)", color = FlatTextSecondary, style = MaterialTheme.typography.bodySmall)
                            Text(CurrencyUtils.formatInr(item.itemTotal), color = FlatTextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider(color = FlatDarkBorder)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tr("Total Bill:", "कुल बिल:"), color = FlatTextSecondary)
                        Text(CurrencyUtils.formatInr(bill.subtotal), fontWeight = FontWeight.Bold, color = FlatTextPrimary)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tr("Paid Now:", "जमा राशि:"), color = FlatTextSecondary)
                        Text(CurrencyUtils.formatInr(bill.paidAmount), fontWeight = FontWeight.Bold, color = CGreen)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tr("Udhari Added:", "नई उधारी:"), color = FlatTextSecondary)
                        Text(CurrencyUtils.formatInr(bill.udhariAmount), fontWeight = FontWeight.Bold, color = CRedUdhari)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { BillGenerator.shareTextBill(context, bill) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BillBrandGreen),
                        border = BorderStroke(1.dp, BillBrandGreen)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "WhatsApp",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(
                        onClick = { BillGenerator.sharePictureBill(context, bill) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BillBrandGreen, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = tr("Receipt", "पर्ची फोटो"),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        billingViewModel.setGeneratedBill(null)
                        onBack()
                    }
                ) {
                    Text(
                        text = tr("Done", "बंद करें"),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        )
    }
}
