package com.example.ui.billing

import com.example.ui.theme.pickerThemedContext
import com.example.ui.theme.kw
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.PersonAdd
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.repository.CattleFeedRepository
import com.example.data.repository.SaleItemRequest
import com.example.model.Customer
import com.example.model.CustomerStatusChip
import com.example.model.Permission
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
import com.example.ui.theme.CGreenContainer
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
private val BillAvatarBg: Color get() = kw(0xFFE6EEF6)
private val BillAvatarText: Color get() = kw(0xFF1E3A5F)
private fun billCustomerInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "?"
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
    val allUsers by repository.allUsers.collectAsState(initial = emptyList())

    val selectedCustomer by billingViewModel.selectedCustomer.collectAsState()
    val isWalkInCashCustomer by billingViewModel.isWalkInCashCustomer.collectAsState()
    val customerSearchQuery by billingViewModel.customerSearchQuery.collectAsState()
    val paidAmountStr by billingViewModel.paidAmountStr.collectAsState()
    val selectedPayMode by billingViewModel.selectedPayMode.collectAsState()
    val promiseDateMillis by billingViewModel.promiseDateMillis.collectAsState()
    val generatedBill by billingViewModel.generatedBill.collectAsState()
    val cartLines = billingViewModel.cartLines

    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var showOverLimitConfirmDialog by remember { mutableStateOf(false) }
    var showRateOverrideLineId by remember { mutableStateOf<String?>(null) }
    var showCustomerPicker by remember { mutableStateOf(false) }
    var productSearchQuery by remember { mutableStateOf("") }

    val canOverrideRate = can(currentUser?.role, Permission.OVERRIDE_BILL_RATE)
    val canViewFullCustomer = can(currentUser?.role, Permission.VIEW_CUSTOMER_FULL)

    val activeProducts = remember(products) { products.filter { !it.isDeleted } }
    val filteredFeedProducts = remember(activeProducts, productSearchQuery) {
        if (productSearchQuery.isBlank()) activeProducts
        else activeProducts.filter { it.feedName.contains(productSearchQuery.trim(), ignoreCase = true) }
    }

    val lineCalculations = billingViewModel.calculateCartLines(activeProducts, currentUser?.role)

    val grandTotal = lineCalculations.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.third.second) }
    val paidAmount = when (selectedPayMode) {
        "FULL" -> grandTotal
        "HALF" -> grandTotal.divide(BigDecimal("2"), 0, RoundingMode.HALF_UP)
        "UDHARI" -> BigDecimal.ZERO
        else -> (paidAmountStr.toBigDecimalOrNull() ?: BigDecimal.ZERO).max(BigDecimal.ZERO)
    }
    val udhariDue = grandTotal.subtract(paidAmount).max(BigDecimal.ZERO)

    // Credit limit check (creditLimit > 0 means active limit; 0 means no limit)
    val currentCustomerUdhari = selectedCustomer?.totalUdhari ?: BigDecimal.ZERO
    val customerCreditLimit = selectedCustomer?.creditLimit ?: BigDecimal.ZERO
    val projectedTotalUdhari = currentCustomerUdhari.add(udhariDue)
    val isExceedingCreditLimit = !isWalkInCashCustomer &&
            selectedCustomer != null &&
            isCustomerCreditBlocked(selectedCustomer!!, udhariDue)

    val isStaffBlockedByCreditLimit = isExceedingCreditLimit && currentUser?.role == Role.STAFF

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
                    performedByRole = currentUser?.role
                )
                billingViewModel.setGeneratedBill(billData)
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Unable to save bill", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        containerColor = FlatDarkBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FlatDarkBg)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                contentDescription = "Back",
                                tint = FlatTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = tr("New sale bill", "नया बिक्री बिल"),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                        Text(
                            text = tr("Quick chips · Voice · Credit guard", "तेज़ चिप्स · बोलकर लिखें · उधारी जांच"),
                            fontSize = 12.sp,
                            color = FlatTextSecondary
                        )
                    }
                }

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
                            contentDescription = "Voice Input",
                            tint = FlatTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = FlatDarkSurface,
                border = BorderStroke(1.dp, FlatDarkBorder),
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 3-Column Summary Row matching New Sale Bill.pdf (NO hidden amounts)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("TOTAL BILL", "कुल बिल"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.4.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = CurrencyUtils.formatInr(grandTotal),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Text(
                                text = tr("PAID NOW", "नकद जमा"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.4.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val displayedPaidStr = if (isWalkInCashCustomer) {
                                grandTotal.stripTrailingZeros().toPlainString()
                            } else if (selectedPayMode == "CUSTOM") {
                                paidAmountStr
                            } else {
                                paidAmount.stripTrailingZeros().toPlainString()
                            }
                            Row(
                                modifier = Modifier
                                    .widthIn(min = 104.dp, max = 136.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(FlatDarkCard)
                                    .border(1.5.dp, BillBrandGreen, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "₹ ",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextSecondary
                                )
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
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FlatTextPrimary
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
                                text = tr("UDHARI DUE", "बाक़ी उधारी"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.4.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val effectiveUdhari = if (isWalkInCashCustomer) BigDecimal.ZERO else udhariDue
                            Text(
                                text = CurrencyUtils.formatInr(effectiveUdhari),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (effectiveUdhari > BigDecimal.ZERO) BillCrimson else FlatTextSecondary
                            )
                        }
                    }

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
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("submit_sale_button")
                    ) {
                        if (isStaffBlockedByCreditLimit) {
                            Icon(
                                Icons.Default.Block,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = if (isStaffBlockedByCreditLimit) {
                                tr("Credit Blocked (Over Limit)", "उधारी सीमा पार - बिल ब्लॉक")
                            } else {
                                tr(
                                    "Save & Share Bill · ${CurrencyUtils.formatInr(grandTotal)}",
                                    "बिल सेव करें · ${CurrencyUtils.formatInr(grandTotal)}"
                                )
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1 · CUSTOMER Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("1 · CUSTOMER", "1 · ग्राहक"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.6.sp
                            )
                            TextButton(
                                onClick = { showAddCustomerDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("new_bill_add_customer_btn")
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = BillBrandGreen, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(tr("+ Customer", "+ नया ग्राहक"), color = BillBrandGreen, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Segmented Toggle: Walk-in Cash | Customer
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
                                Surface(
                                    onClick = { billingViewModel.toggleWalkInCash(true) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isWalkInCashCustomer) BillBrandGreen else Color.Transparent,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("walk_in_cash_chip")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = tr("Walk-in Cash", "नकद ग्राहक"),
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isWalkInCashCustomer) Color.White else FlatTextSecondary
                                        )
                                    }
                                }

                                Surface(
                                    onClick = { billingViewModel.toggleWalkInCash(false) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (!isWalkInCashCustomer) BillBrandGreen else Color.Transparent,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("customer_mode_chip")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = tr("Customer", "खाता ग्राहक"),
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (!isWalkInCashCustomer) Color.White else FlatTextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        if (!isWalkInCashCustomer) {
                            // Selected Customer Summary matching New Sale Bill.pdf (NO hidden amount!)
                            selectedCustomer?.let { cust ->
                                val statusChip = getCustomerStatusChip(cust)
                                val creditFlagBlocked = isCustomerCreditBlocked(cust, udhariDue.max(BigDecimal.ONE))
                                val initials = remember(cust.name) { billCustomerInitials(cust.name) }
                                val custSub = remember(cust) {
                                    buildString {
                                        if (cust.village.isNotBlank()) append(cust.village)
                                        if (cust.fatherName.isNotBlank()) {
                                            if (isNotEmpty()) append(" · ")
                                            append("S/o ${cust.fatherName}")
                                        } else if (cust.phone.isNotBlank()) {
                                            if (isNotEmpty()) append(" · ")
                                            append(cust.phone)
                                        }
                                    }.ifBlank { "Regular Customer" }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(BillAvatarBg),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = initials,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.5.sp,
                                                color = BillAvatarText
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = cust.name,
                                                fontSize = 15.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FlatTextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = custSub,
                                                fontSize = 12.5.sp,
                                                color = FlatTextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        if (canViewFullCustomer) {
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = tr("UDHARI DUE", "पुरानी उधारी"),
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = FlatTextSecondary
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = CurrencyUtils.formatInr(cust.totalUdhari),
                                                    fontSize = 15.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (cust.totalUdhari > BigDecimal.ZERO) BillCrimson else BillBrandGreen
                                                )
                                            }
                                        } else {
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = if (creditFlagBlocked) tr("Credit: Blocked", "क्रेडिट: ब्लॉक") else tr("Credit OK", "क्रेडिट: ठीक है"),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (creditFlagBlocked) BillCrimson else BillBrandGreen,
                                                    modifier = Modifier.testTag("staff_credit_flag")
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = tr(statusChip.labelEn, statusChip.labelHi),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = when (statusChip) {
                                                        CustomerStatusChip.CLEAR -> BillBrandGreen
                                                        CustomerStatusChip.HAS_UDHARI -> CAmber
                                                        CustomerStatusChip.OVER_LIMIT -> BillCrimson
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Bottom row: "Within credit limit" pill on left + "Change" button on right
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = if (creditFlagBlocked) BillCrimsonSoftBg else BillMintPillBg
                                        ) {
                                            Text(
                                                text = if (creditFlagBlocked) {
                                                    tr("Over credit limit", "उधारी सीमा पार")
                                                } else {
                                                    tr("Within credit limit", "उधारी सीमा के भीतर")
                                                },
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (creditFlagBlocked) BillCrimson else BillBrandGreen,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                            )
                                        }

                                        Text(
                                            text = if (showCustomerPicker) tr("Hide list", "सूची छुपाएं") else tr("Change", "बदलें"),
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BillBrandGreen,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { showCustomerPicker = !showCustomerPicker }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Show search input + chips when no customer is selected or when "Change" is tapped
                            if (selectedCustomer == null || showCustomerPicker) {
                                OutlinedTextField(
                                    value = customerSearchQuery,
                                    onValueChange = { billingViewModel.setCustomerSearchQuery(it) },
                                    placeholder = { Text(tr("Search customer by name or village…", "नाम या गाँव से ग्राहक खोजें…"), fontSize = 14.sp) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = FlatTextMuted) },
                                    trailingIcon = {
                                        if (customerSearchQuery.isNotEmpty()) {
                                            IconButton(onClick = { billingViewModel.setCustomerSearchQuery("") }) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = FlatTextMuted)
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("bill_customer_search_input")
                                )

                                val filteredCustomers = remember(customers, customerSearchQuery) {
                                    val active = customers.filter { !it.isDeleted }
                                    if (customerSearchQuery.isBlank()) active.take(12)
                                    else active.filter {
                                        it.name.contains(customerSearchQuery, ignoreCase = true) ||
                                            it.village.contains(customerSearchQuery, ignoreCase = true) ||
                                            it.phone.contains(customerSearchQuery)
                                    }.take(12)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    filteredCustomers.forEach { cust ->
                                        val isSelected = selectedCustomer?.id == cust.id
                                        val statusChip = getCustomerStatusChip(cust)
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                billingViewModel.selectCustomer(cust)
                                                showCustomerPicker = false
                                            },
                                            label = {
                                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                                    Text(cust.name, fontWeight = FontWeight.Bold)
                                                    if (canViewFullCustomer) {
                                                        Text(
                                                            text = if (cust.totalUdhari > BigDecimal.ZERO) {
                                                                "Due: ${CurrencyUtils.formatInr(cust.totalUdhari)}"
                                                            } else {
                                                                cust.village.ifBlank { "Clear" }
                                                            },
                                                            fontSize = 11.sp,
                                                            color = if (cust.totalUdhari > BigDecimal.ZERO) BillCrimson else FlatTextSecondary
                                                        )
                                                    } else {
                                                        Text(
                                                            text = statusChip.labelEn,
                                                            fontSize = 11.sp,
                                                            color = when (statusChip) {
                                                                CustomerStatusChip.CLEAR -> BillBrandGreen
                                                                CustomerStatusChip.HAS_UDHARI -> CAmber
                                                                CustomerStatusChip.OVER_LIMIT -> BillCrimson
                                                            }
                                                        )
                                                    }
                                                }
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = BillMintPillBg,
                                                selectedLabelColor = BillBrandGreen
                                            ),
                                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Credit Limit Warning Banner
            if (isExceedingCreditLimit) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("credit_limit_warning_card"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CRedUdhariBg),
                        border = BorderStroke(1.5.dp, CRedUdhari)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Credit Limit Exceeded",
                                tint = CRedUdhari,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (currentUser?.role == Role.STAFF) {
                                        tr("Credit Blocked: Over Limit!", "उधारी सीमा पार - क्रेडिट ब्लॉक!")
                                    } else {
                                        tr("Credit Limit Warning!", "उधारी सीमा (Credit Limit) चेतावनी!")
                                    },
                                    fontWeight = FontWeight.Bold,
                                    color = CRedUdhari
                                )
                                Text(
                                    text = if (!canViewFullCustomer) {
                                        tr(
                                            "This udhari bill exceeds the customer's credit limit. Staff cannot override credit limits.",
                                            "यह उधारी बिल ग्राहक की क्रेडिट सीमा से अधिक है। स्टाफ इसे मंजूर नहीं कर सकता।"
                                        )
                                    } else {
                                        "New Total Udhari (${CurrencyUtils.formatInr(projectedTotalUdhari)}) exceeds ${selectedCustomer?.name}'s credit limit of ${CurrencyUtils.formatInr(customerCreditLimit)}."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FlatTextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // 2 · ADD FEED / GRAIN Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = tr("2 · ADD FEED / GRAIN", "2 · पशु आहार / अनाज जोड़ें"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.6.sp
                        )

                        OutlinedTextField(
                            value = productSearchQuery,
                            onValueChange = { productSearchQuery = it },
                            placeholder = {
                                Text(
                                    text = tr("Search item...", "सामान खोजें..."),
                                    color = FlatTextMuted,
                                    fontSize = 14.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = BillBrandGreen
                                )
                            },
                            trailingIcon = {
                                if (productSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { productSearchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = tr("Close", "बंद करें"), tint = FlatTextSecondary)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = FlatDarkCard,
                                unfocusedContainerColor = FlatDarkCard,
                                focusedBorderColor = BillBrandGreen,
                                unfocusedBorderColor = BillBrandGreen,
                                focusedTextColor = FlatTextPrimary,
                                unfocusedTextColor = FlatTextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = tr("Frequently sold", "अक्सर बिकने वाले"),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FlatTextSecondary
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            filteredFeedProducts.forEach { prod ->
                                val alreadyInCart = cartLines.any { it.productId == prod.id }
                                Surface(
                                    onClick = { billingViewModel.addOrIncrementProduct(prod) },
                                    shape = RoundedCornerShape(50),
                                    color = if (alreadyInCart) BillMintPillBg else FlatDarkCard,
                                    border = BorderStroke(
                                        1.dp,
                                        if (alreadyInCart) BillBrandGreen else FlatDarkBorder
                                    ),
                                    modifier = Modifier.defaultMinSize(minHeight = 38.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "+ ${prod.feedName}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (alreadyInCart) BillBrandGreen else FlatTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // BILL ITEMS Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = tr("BILL ITEMS", "बिल के सामान"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.6.sp
                        )

                        if (cartLines.isEmpty()) {
                            Surface(
                                color = FlatDarkBg,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, FlatDarkBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = tr(
                                        "Tap any feed chip above or use Voice button to add items.",
                                        "ऊपर दिए गए किसी भी आइटम पर टैप करें या बोलकर जोड़ें।"
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = FlatTextMuted,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        } else {
                            cartLines.forEachIndexed { index, line ->
                                val product = activeProducts.find { it.id == line.productId } ?: return@forEachIndexed
                                val calc = lineCalculations.find { it.first.id == line.id }
                                val lineTotal = calc?.third?.second ?: BigDecimal.ZERO
                                val looseVal = line.looseKgStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
                                val isExpandedOptions = showRateOverrideLineId == line.id

                                val rateLabel = if (!product.isLooseOnly && line.bagsCount > 0 && looseVal <= BigDecimal.ZERO) {
                                    "${CurrencyUtils.formatInr(product.bagSellingPrice)} / bag"
                                } else if (product.isLooseOnly || (line.bagsCount == 0 && looseVal > BigDecimal.ZERO)) {
                                    "${CurrencyUtils.formatInr(product.effectiveLooseRatePerKg)} / kg"
                                } else {
                                    "${CurrencyUtils.formatInr(product.bagSellingPrice)}/bag · ${CurrencyUtils.formatInr(product.effectiveLooseRatePerKg)}/kg"
                                }

                                val qtyDisplayText = when {
                                    !product.isLooseOnly && line.bagsCount > 0 && looseVal <= BigDecimal.ZERO ->
                                        "${line.bagsCount} ${if (line.bagsCount == 1) "bag" else "bags"}"
                                    line.bagsCount == 0 && looseVal > BigDecimal.ZERO ->
                                        "${line.looseKgStr} kg"
                                    !product.isLooseOnly && line.bagsCount > 0 && looseVal > BigDecimal.ZERO ->
                                        "${line.bagsCount}b + ${line.looseKgStr}kg"
                                    product.isLooseOnly ->
                                        "${line.looseKgStr.ifBlank { "0" }} kg"
                                    else ->
                                        "${line.bagsCount} bags"
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showRateOverrideLineId = if (isExpandedOptions) null else line.id
                                            },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Left: Item Name + Rate subtitle
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = product.feedName,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FlatTextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = rateLabel,
                                                    fontSize = 12.5.sp,
                                                    color = FlatTextSecondary
                                                )
                                                if (canOverrideRate) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Override Rate",
                                                        tint = CAmber,
                                                        modifier = Modifier
                                                            .size(14.dp)
                                                            .clickable {
                                                                showRateOverrideLineId = if (isExpandedOptions) null else line.id
                                                            }
                                                            .testTag("override_rate_btn_${product.id}")
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Middle: Minus [-]  Qty  Plus [+]
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                                shape = RoundedCornerShape(10.dp),
                                                color = FlatDarkInput,
                                                modifier = Modifier.size(38.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.Remove,
                                                        contentDescription = "Decrease quantity",
                                                        tint = FlatTextPrimary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            Text(
                                                text = qtyDisplayText,
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FlatTextPrimary,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.widthIn(min = 54.dp)
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
                                                shape = RoundedCornerShape(10.dp),
                                                color = BillBrandGreen,
                                                modifier = Modifier.size(38.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.Add,
                                                        contentDescription = "Increase quantity",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        // Right: Line Total
                                        Text(
                                            text = CurrencyUtils.formatInr(lineTotal),
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FlatTextPrimary,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.widthIn(min = 66.dp)
                                        )
                                    }

                                    // Expanded Inline Editor for Loose Kg, Bag Presets, Rate Override, and Remove
                                    if (isExpandedOptions) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = FlatDarkBg,
                                            border = BorderStroke(1.dp, FlatDarkBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = tr("Loose Weight / Custom Options", "खुला किलो / कस्टम विकल्प"),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = FlatTextSecondary
                                                    )
                                                    IconButton(
                                                        onClick = { cartLines.removeAt(index) },
                                                        modifier = Modifier.size(44.dp)
                                                    ) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Remove Item", tint = BillCrimson, modifier = Modifier.size(18.dp))
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
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
                                                            label = { Text("${kgPreset}kg", fontWeight = FontWeight.Bold) }
                                                        )
                                                    }
                                                    OutlinedTextField(
                                                        value = line.looseKgStr,
                                                        onValueChange = { v ->
                                                            cartLines[index] = line.copy(looseKgStr = v)
                                                        },
                                                        label = { Text("Kg") },
                                                        singleLine = true,
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }

                                                if (canOverrideRate) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        if (!product.isLooseOnly) {
                                                            OutlinedTextField(
                                                                value = line.customBagRateStr,
                                                                onValueChange = { v -> cartLines[index] = line.copy(customBagRateStr = v) },
                                                                label = { Text("Custom Bag ₹") },
                                                                singleLine = true,
                                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                        }
                                                        OutlinedTextField(
                                                            value = line.customLooseRateStr,
                                                            onValueChange = { v -> cartLines[index] = line.copy(customLooseRateStr = v) },
                                                            label = { Text("Custom Loose ₹/kg") },
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

            // Quick Payment Mode Chips & Promise Date (when customer bill has items)
            if (!isWalkInCashCustomer && grandTotal > BigDecimal.ZERO) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedPayMode == "FULL",
                            onClick = { billingViewModel.setPayMode("FULL", grandTotal) },
                            label = { Text(tr("Full paid", "पूरा नकद"), fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BillMintPillBg,
                                selectedLabelColor = BillBrandGreen
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pay_mode_full_chip")
                        )
                        FilterChip(
                            selected = selectedPayMode == "HALF",
                            onClick = { billingViewModel.setPayMode("HALF", grandTotal) },
                            label = { Text(tr("Half (50%)", "आधा जमा"), fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CAmberBg,
                                selectedLabelColor = CAmber
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pay_mode_half_chip")
                        )
                        FilterChip(
                            selected = selectedPayMode == "UDHARI",
                            onClick = { billingViewModel.setPayMode("UDHARI", grandTotal) },
                            label = { Text(tr("Full udhari", "पूरी उधारी"), fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BillCrimsonSoftBg,
                                selectedLabelColor = BillCrimson
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pay_mode_udhari_chip")
                        )
                    }

                    if (udhariDue > BigDecimal.ZERO) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val fmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
                        OutlinedButton(
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
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CAmberBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = CAmber, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (promiseDateMillis != null) {
                                    "Promise Date (वायदा तिथि): ${fmt.format(Date(promiseDateMillis!!))}"
                                } else {
                                    tr("Set Udhari Promise Date (वायदा तिथि चुनें)", "उधारी वायदा तिथि चुनें (Promise Date)")
                                },
                                color = CAmber,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
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
                Button(
                    onClick = {
                        if (isManager) {
                            val admins = allUsers.filter { !it.isDeleted && it.isActive && it.role == Role.ADMINISTRATOR }
                            val approved = admins.any { admin ->
                                admin.pinHash.isBlank() || SecurityUtils.verifyPin(adminApprovalPin, admin.pinHash)
                            }
                            if (approved) {
                                showOverLimitConfirmDialog = false
                                executeSaveBill()
                            } else {
                                pinError = "Invalid Administrator PIN"
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
                        onClick = { BillGenerator.shareTextBill(context, bill) }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("WhatsApp")
                    }
                    Button(
                        onClick = { BillGenerator.sharePictureBill(context, bill) },
                        colors = ButtonDefaults.buttonColors(containerColor = BillBrandGreen, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr("Receipt image", "पर्ची फोटो"), fontWeight = FontWeight.Bold)
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
                    Text(tr("Done", "बंद करें"))
                }
            }
        )
    }
}
