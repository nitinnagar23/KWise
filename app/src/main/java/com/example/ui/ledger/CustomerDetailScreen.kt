package com.example.ui.ledger

import com.example.ui.theme.KwSurface
import com.example.ui.theme.kw
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.CattleFeedRepository
import com.example.model.Customer
import com.example.model.Permission
import com.example.model.User
import com.example.model.can
import com.example.model.canEditCustomerBasic
import com.example.reminder.ReminderScheduler
import com.example.ui.components.DeleteCustomerConfirmationDialog
import com.example.util.CurrencyUtils
import com.example.util.ShopAnalytics
import com.example.util.tr
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val DetailPageBg: Color get() = kw(0xFFF6F7F9)
private val DetailCardBorder: Color get() = kw(0xFFE2E8F0)
private val DetailNavyText: Color get() = kw(0xFF0F172A)
private val DetailSubtleText: Color get() = kw(0xFF64748B)
private val DetailBrandGreen: Color get() = kw(0xFF0E6F43)
private val DetailSoftGreen: Color get() = kw(0xFFDDF5E7)
private val DetailCrimsonRed: Color get() = kw(0xFFB91C1C)
private val DetailSoftRed: Color get() = kw(0xFFFEE2E2)
private val DetailSoftAmber: Color get() = kw(0xFFFEF3C7)
private val DetailAmberText: Color get() = kw(0xFFB45309)
private val DetailAvatarBg: Color get() = kw(0xFFE2E8F0)
private enum class CustomerDetailOverlay {
    NONE,
    STATEMENT,
    COLLECT_UDHARI,
    REPAYMENT_SUCCESS,
    EDIT_CUSTOMER
}

private enum class CustomerBillFilter {
    ALL,
    UDHARI,
    PAID
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomerDetailScreen(
    customerId: String,
    repository: CattleFeedRepository,
    currentUser: User?,
    onBack: () -> Unit,
    onNavigateToNewBill: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val customers by repository.allCustomers.collectAsState(initial = emptyList())
    val customer = customers.find { it.id == customerId && !it.isDeleted }
    val sales by repository.allSales.collectAsState(initial = emptyList())
    val payments by repository.allPayments.collectAsState(initial = emptyList())
    val products by repository.allProducts.collectAsState(initial = emptyList())
    val settings by repository.settingsFlow.collectAsState(initial = null)

    if (customer == null) {
        BackHandler { onBack() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DetailPageBg),
            contentAlignment = Alignment.Center
        ) {
            Text(tr("Customer not found", "ग्राहक नहीं मिला"), color = DetailSubtleText)
        }
        return
    }

    val canViewFull = can(currentUser?.role, Permission.VIEW_CUSTOMER_FULL)
    val canEditFull = can(currentUser?.role, Permission.EDIT_CUSTOMER_FULL)
    val canEditBasic = canEditCustomerBasic(currentUser, customer)
    val canAccessCollections = can(currentUser?.role, Permission.ACCESS_COLLECTIONS)

    val productMap = remember(products) { products.associateBy { it.id } }
    val customerSales = remember(sales, customerId) {
        sales.filter { it.customerId == customerId && !it.isDeleted && it.billStatus != "CANCELLED" }
            .sortedByDescending { it.date }
    }
    val customerPayments = remember(payments, customerId) {
        payments.filter { it.customerId == customerId && !it.isDeleted }
            .sortedByDescending { it.date }
    }

    val ledgerRowsAsc = remember(customer, customerSales, customerPayments) {
        buildCustomerLedgerRows(customer, customerSales, customerPayments)
    }
    val ledgerRowsDesc = remember(ledgerRowsAsc) {
        ledgerRowsAsc.reversed()
    }

    val customerInsight = remember(customer, customerSales, customerPayments) {
        ShopAnalytics.computeCustomerInsights(
            listOf(customer),
            customerSales,
            customerPayments
        ).firstOrNull()
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Khata, 1 = Bills, 2 = Repayments
    var selectedBillFilter by remember { mutableStateOf(CustomerBillFilter.ALL) }
    var activeOverlay by remember { mutableStateOf(CustomerDetailOverlay.NONE) }
    var lastRecordedReceipt by remember { mutableStateOf<RecordedRepaymentReceipt?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val shopName = settings?.shopName?.ifBlank { "KhataWise Store" } ?: "KhataWise Store"
    val shopUpi = settings?.shopUpiId.orEmpty()

    // Full-screen overlays for Pages 4, 5, 6, 7, 8
    when (activeOverlay) {
        CustomerDetailOverlay.STATEMENT -> {
            CustomerStatementScreen(
                customer = customer,
                ledgerRowsAsc = ledgerRowsAsc,
                settings = settings,
                onBack = { activeOverlay = CustomerDetailOverlay.NONE }
            )
            return
        }

        CustomerDetailOverlay.COLLECT_UDHARI -> {
            CollectUdhariScreen(
                customer = customer,
                onCancel = { activeOverlay = CustomerDetailOverlay.NONE },
                onConfirmCollect = { amount, mode, dateMillis, noteText, newPromiseMillis, sendWhatsapp ->
                    val beforeBal = customer.totalUdhari
                    val afterBal = beforeBal.subtract(amount).max(BigDecimal.ZERO)
                    val taggedNote = if (noteText.isNotBlank()) "[$mode] $noteText" else "[$mode] Paid at shop"
                    scope.launch {
                        val savedPayment = repository.recordPayment(
                            customerId = customer.id,
                            amount = amount,
                            note = taggedNote,
                            collectedByUserId = currentUser?.id.orEmpty(),
                            actorRole = currentUser?.role
                        )
                        if (newPromiseMillis != null || afterBal.compareTo(BigDecimal.ZERO) == 0) {
                            val updatedCust = customer.copy(
                                totalUdhari = afterBal,
                                promiseDate = if (afterBal > BigDecimal.ZERO) newPromiseMillis else null
                            )
                            repository.saveCustomer(updatedCust, actorRole = currentUser?.role)
                            if (newPromiseMillis != null && afterBal > BigDecimal.ZERO) {
                                ReminderScheduler.scheduleCustomerReminder(context, updatedCust)
                            }
                        }
                        val recDigits = savedPayment.id.filter { it.isDigit() }.takeLast(3).padStart(3, '0').ifBlank { "317" }
                        val receiptObj = RecordedRepaymentReceipt(
                            receiptNumber = "R-$recDigits",
                            customerName = customer.name,
                            customerPhone = customer.phone,
                            amountReceived = amount,
                            outstandingBefore = beforeBal,
                            balanceNow = afterBal,
                            paymentMode = mode,
                            newPromiseDateMillis = if (afterBal > BigDecimal.ZERO) newPromiseMillis else null
                        )
                        lastRecordedReceipt = receiptObj
                        activeOverlay = CustomerDetailOverlay.REPAYMENT_SUCCESS
                        if (sendWhatsapp) {
                            shareReceiptViaWhatsApp(context, receiptObj, shopName)
                        }
                    }
                }
            )
            return
        }

        CustomerDetailOverlay.REPAYMENT_SUCCESS -> {
            val receipt = lastRecordedReceipt
            if (receipt != null) {
                RepaymentReceivedSuccessScreen(
                    receipt = receipt,
                    shopName = shopName,
                    onShareWhatsapp = {
                        shareReceiptViaWhatsApp(context, receipt, shopName)
                    },
                    onViewKhata = {
                        selectedTab = 0
                        activeOverlay = CustomerDetailOverlay.NONE
                    },
                    onDone = {
                        activeOverlay = CustomerDetailOverlay.NONE
                    }
                )
                return
            }
        }

        CustomerDetailOverlay.EDIT_CUSTOMER -> {
            AddOrEditCustomerScreen(
                existingCustomer = customer,
                currentUserId = currentUser?.id.orEmpty(),
                currentUserRole = currentUser?.role,
                onCancel = { activeOverlay = CustomerDetailOverlay.NONE },
                onSaveCustomer = { updatedCustomer ->
                    scope.launch {
                        repository.saveCustomer(updatedCustomer, actorRole = currentUser?.role)
                        activeOverlay = CustomerDetailOverlay.NONE
                    }
                },
                onRequestDeleteCustomer = if (canEditFull) {
                    { showDeleteConfirmDialog = true }
                } else null
            )
            if (showDeleteConfirmDialog) {
                DeleteCustomerConfirmationDialog(
                    customerName = customer.name,
                    pendingUdhari = customer.totalUdhari,
                    onCollectUdhari = {
                        showDeleteConfirmDialog = false
                        activeOverlay = CustomerDetailOverlay.COLLECT_UDHARI
                    },
                    onConfirmDelete = {
                        showDeleteConfirmDialog = false
                        scope.launch {
                            repository.deleteCustomer(customer.id, currentUser)
                            onBack()
                        }
                    },
                    onDismiss = { showDeleteConfirmDialog = false }
                )
            }
            return
        }

        CustomerDetailOverlay.NONE -> Unit
    }

    BackHandler { onBack() }

    val shortDateFmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val dateTimeFmt = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }

    // Overdue calculation for promise date
    val promiseDateMillis = customer.promiseDate
    val daysLate = remember(promiseDateMillis, customer.totalUdhari) {
        if (promiseDateMillis != null && promiseDateMillis > 0L && customer.totalUdhari > BigDecimal.ZERO) {
            val diffMs = System.currentTimeMillis() - promiseDateMillis
            val days = TimeUnit.MILLISECONDS.toDays(diffMs).toInt()
            if (days > 0) days else 0
        } else 0
    }

    val taggedPhones = remember(customer) { customer.getTaggedPhones() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DetailPageBg)
    ) {
        // ====================================================================
        // TOP BAR (Back button, Customer Name + "Customer khata", Call & Edit)
        // ====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
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
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("customer_khata_back_btn"),
                    shape = RoundedCornerShape(13.dp),
                    color = KwSurface,
                    border = BorderStroke(1.dp, DetailCardBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = DetailNavyText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = customer.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = DetailNavyText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = tr("Customer khata", "ग्राहक खाता"),
                        fontSize = 13.sp,
                        color = DetailSubtleText
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val primaryPhone = taggedPhones.firstOrNull()?.number ?: customer.phone
                if (primaryPhone.isNotBlank()) {
                    Surface(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$primaryPhone"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("customer_call_header_btn"),
                        shape = RoundedCornerShape(13.dp),
                        color = KwSurface,
                        border = BorderStroke(1.dp, DetailCardBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Call,
                                contentDescription = "Call",
                                tint = DetailBrandGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (canEditBasic) {
                    Surface(
                        onClick = { activeOverlay = CustomerDetailOverlay.EDIT_CUSTOMER },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("edit_customer_btn"),
                        shape = RoundedCornerShape(13.dp),
                        color = KwSurface,
                        border = BorderStroke(1.dp, DetailCardBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = tr("Edit customer", "ग्राहक संपादित करें"),
                                tint = DetailNavyText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // ====================================================================
        // SCROLLABLE CONTENT (Profile Card + Tabs + Active Tab Content)
        // ====================================================================
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                if (selectedTab == 0) {
                    // ========================================================
                    // PAGE 1: FULL CUSTOMER PROFILE & OUTSTANDING CARD
                    // ========================================================
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = KwSurface,
                        border = BorderStroke(1.dp, DetailCardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(56.dp),
                                    shape = CircleShape,
                                    color = DetailAvatarBg
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = customer.getInitials(),
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DetailNavyText
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = customer.name,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DetailNavyText
                                    )
                                    val fatherVillage = buildList {
                                        if (customer.fatherName.isNotBlank()) add("S/o ${customer.fatherName}")
                                        if (customer.village.isNotBlank()) add(customer.village)
                                    }.joinToString(" · ")
                                    if (fatherVillage.isNotBlank()) {
                                        Text(
                                            text = fatherVillage,
                                            fontSize = 13.5.sp,
                                            color = DetailSubtleText
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Badges Row: Trust score + Late pill
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val score = customerInsight?.trustScore ?: 78
                                        val badgeLabel = customerInsight?.trustBadge ?: "Good"
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = DetailSoftAmber
                                        ) {
                                            Text(
                                                text = "Trust $score · $badgeLabel",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = DetailAmberText,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }

                                        if (daysLate > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = DetailSoftRed
                                            ) {
                                                Text(
                                                    text = "$daysLate days late",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = DetailCrimsonRed,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Tagged Mobile Numbers Pills
                            if (taggedPhones.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    taggedPhones.forEach { tp ->
                                        Surface(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${tp.number}"))
                                                context.startActivity(intent)
                                            },
                                            shape = RoundedCornerShape(50),
                                            color = kw(0xFFF1F5F9)
                                        ) {
                                            Text(
                                                text = "${tp.number} · ${tp.tag}",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = DetailNavyText,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = kw(0xFFF1F5F9))
                            Spacer(modifier = Modifier.height(14.dp))

                            if (canViewFull) {
                                Text(
                                    text = "OUTSTANDING",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DetailSubtleText,
                                    letterSpacing = 0.6.sp
                                )
                                Text(
                                    text = CurrencyUtils.formatInr(customer.totalUdhari),
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (customer.totalUdhari > BigDecimal.ZERO) DetailCrimsonRed else DetailBrandGreen
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                val limitVal = if (customer.creditLimit > BigDecimal.ZERO) customer.creditLimit else BigDecimal("10000")
                                val progressRatio = (customer.totalUdhari.toFloat() / limitVal.toFloat()).coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = { progressRatio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(50)),
                                    color = if (progressRatio >= 1f) DetailCrimsonRed else DetailBrandGreen,
                                    trackColor = kw(0xFFE2E8F0),
                                    strokeCap = StrokeCap.Round
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val limitText = if (customer.creditLimit > BigDecimal.ZERO) {
                                        "${CurrencyUtils.formatInr(customer.totalUdhari)} of ${CurrencyUtils.formatInr(customer.creditLimit)} limit"
                                    } else {
                                        "${CurrencyUtils.formatInr(customer.totalUdhari)} udhari · No limit"
                                    }
                                    Text(
                                        text = limitText,
                                        fontSize = 12.5.sp,
                                        color = DetailSubtleText
                                    )
                                    if (promiseDateMillis != null && promiseDateMillis > 0L) {
                                        Text(
                                            text = "Promised ${shortDateFmt.format(Date(promiseDateMillis))}",
                                            fontSize = 12.5.sp,
                                            color = DetailSubtleText
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Action Buttons: Collect udhari | New bill | WhatsApp Message
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (canAccessCollections) {
                                        Button(
                                            onClick = { activeOverlay = CustomerDetailOverlay.COLLECT_UDHARI },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = DetailBrandGreen,
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("collect_payment_btn")
                                        ) {
                                            Text(
                                                text = tr("Collect udhari", "उधारी वसूलें"),
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    Surface(
                                        onClick = onNavigateToNewBill,
                                        shape = RoundedCornerShape(14.dp),
                                        color = KwSurface,
                                        border = BorderStroke(1.5.dp, DetailBrandGreen),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("customer_new_bill_btn")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = tr("New bill", "नया बिल"),
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = DetailBrandGreen
                                            )
                                        }
                                    }

                                    Surface(
                                        onClick = {
                                            val msg = ShopAnalytics.buildSmartHindiUdhariReminder(
                                                customer = customer,
                                                shopName = shopName,
                                                shopUpiId = shopUpi
                                            )
                                            sendWhatsAppToCustomer(context, customer.phone, msg)
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        color = KwSurface,
                                        border = BorderStroke(1.dp, DetailCardBorder),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("send_hindi_whatsapp_reminder_btn")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                                contentDescription = "Send Reminder",
                                                tint = DetailBrandGreen,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // ========================================================
                    // PAGES 2 & 3: COMPACT CUSTOMER HEADER CARD
                    // ========================================================
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = KwSurface,
                        border = BorderStroke(1.dp, DetailCardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    modifier = Modifier.size(44.dp),
                                    shape = CircleShape,
                                    color = DetailAvatarBg
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = customer.getInitials(),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DetailNavyText
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = customer.village.ifBlank { customer.name },
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DetailNavyText
                                    )
                                    if (customer.phone.isNotBlank()) {
                                        Text(
                                            text = customer.phone,
                                            fontSize = 13.sp,
                                            color = DetailSubtleText
                                        )
                                    }
                                }
                            }

                            if (canViewFull) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "OUTSTANDING",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DetailSubtleText,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = CurrencyUtils.formatInr(customer.totalUdhari),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (customer.totalUdhari > BigDecimal.ZERO) DetailCrimsonRed else DetailBrandGreen
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ============================================================
                // TABS ROW: Khata | Bills | Repayments
                // ============================================================
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DetailPageBg,
                    contentColor = DetailNavyText,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = DetailBrandGreen,
                                height = 2.5.dp
                            )
                        }
                    },
                    divider = {
                        HorizontalDivider(color = DetailCardBorder)
                    }
                ) {
                    listOf(
                        0 to tr("Khata", "खाता"),
                        1 to tr("Bills", "बिल"),
                        2 to tr("Repayments", "भुगतान")
                    ).forEach { (idx, label) ->
                        val active = selectedTab == idx
                        Tab(
                            selected = active,
                            onClick = { selectedTab = idx },
                            text = {
                                Text(
                                    text = label,
                                    fontSize = 14.5.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (active) DetailBrandGreen else DetailSubtleText
                                )
                            }
                        )
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    // ========================================================
                    // TAB 0 (PAGE 1): KHATA LEDGER + "Statement →"
                    // ========================================================
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("Ledger", "लेजर"),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = DetailNavyText
                            )
                            Text(
                                text = tr("Statement →", "स्टेटमेंट →"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = DetailBrandGreen,
                                modifier = Modifier
                                    .clickable { activeOverlay = CustomerDetailOverlay.STATEMENT }
                                    .testTag("open_customer_statement_btn")
                            )
                        }
                    }

                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = KwSurface,
                            border = BorderStroke(1.dp, DetailCardBorder)
                        ) {
                            if (ledgerRowsDesc.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tr("No transactions recorded yet", "अभी तक कोई लेन-देन दर्ज नहीं है"),
                                        fontSize = 14.sp,
                                        color = DetailSubtleText
                                    )
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    ledgerRowsDesc.forEachIndexed { idx, row ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = shortDateFmt.format(Date(row.dateMillis)),
                                                fontSize = 13.sp,
                                                color = DetailSubtleText,
                                                modifier = Modifier.width(56.dp)
                                            )

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = row.title,
                                                    fontSize = 15.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = DetailNavyText
                                                )
                                                Text(
                                                    text = row.subtitle,
                                                    fontSize = 13.sp,
                                                    color = DetailSubtleText
                                                )
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                val isBill = row.isBill
                                                val amt = if (isBill) row.debitUdhari else row.creditPaid
                                                Text(
                                                    text = (if (isBill) "+" else "−") + CurrencyUtils.formatInr(amt),
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isBill) DetailCrimsonRed else DetailBrandGreen
                                                )
                                                Text(
                                                    text = "Bal ${CurrencyUtils.formatInr(row.runningBalance)}",
                                                    fontSize = 12.5.sp,
                                                    color = DetailSubtleText
                                                )
                                            }
                                        }
                                        if (idx < ledgerRowsDesc.lastIndex) {
                                            HorizontalDivider(
                                                color = kw(0xFFF1F5F9),
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // ========================================================
                    // TAB 1 (PAGE 2): CUSTOMER BILLS TAB (All | Udhari | Paid)
                    // ========================================================
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                CustomerBillFilter.ALL to tr("All", "सभी"),
                                CustomerBillFilter.UDHARI to tr("Udhari", "उधारी"),
                                CustomerBillFilter.PAID to tr("Paid", "भुगतान किया")
                            ).forEach { (filter, label) ->
                                val selected = selectedBillFilter == filter
                                Surface(
                                    onClick = { selectedBillFilter = filter },
                                    shape = RoundedCornerShape(50),
                                    color = if (selected) DetailBrandGreen else kw(0xFFEEF2F6)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.White else DetailNavyText,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    val groupedCustomerBills = com.example.model.groupSalesIntoBills(customerSales, descending = true)

                    val filteredBills = groupedCustomerBills.filter { bill ->
                        val due = bill.udhariAmount
                        when (selectedBillFilter) {
                            CustomerBillFilter.ALL -> true
                            CustomerBillFilter.UDHARI -> due > BigDecimal.ZERO
                            CustomerBillFilter.PAID -> due <= BigDecimal.ZERO
                        }
                    }

                    if (filteredBills.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tr("No bills in this view", "इस श्रेणी में कोई बिल नहीं"),
                                    fontSize = 14.sp,
                                    color = DetailSubtleText
                                )
                            }
                        }
                    } else {
                        items(filteredBills, key = { it.id }) { bill ->
                            val primarySale = bill.primarySale
                            val prod = productMap[primarySale.productId]
                            val billCode = bill.billNumber.ifBlank {
                                bill.id.filter { it.isDigit() }.takeLast(4).padStart(4, '0').ifBlank { "1042" }
                            }
                            val due = bill.udhariAmount
                            val qtySummary = remember(bill, prod) {
                                val feedName = prod?.feedName ?: "Cattle Feed"
                                val bagKg = prod?.kgPerBag ?: BigDecimal("50")
                                val firstLine = if (bagKg > BigDecimal.ONE && primarySale.quantityKg >= bagKg) {
                                    val bags = primarySale.quantityKg.divide(bagKg, 0, RoundingMode.DOWN).toInt()
                                    "$feedName × $bags ${if (bags == 1) "bag" else "bags"}"
                                } else {
                                    "$feedName · ${CurrencyUtils.formatWeightKg(primarySale.quantityKg)}"
                                }
                                if (bill.extraItemCount > 0) "$firstLine +${bill.extraItemCount}" else firstLine
                            }

                            val (badgeText, badgeBg, badgeFg) = when {
                                due > BigDecimal.ZERO && bill.paidAmount > BigDecimal.ZERO ->
                                    Triple("Part udhari · due ${CurrencyUtils.formatInr(due)}", DetailSoftRed, DetailCrimsonRed)
                                due > BigDecimal.ZERO && customer.totalUdhari <= BigDecimal.ZERO -> {
                                    val settledDate = customerPayments.firstOrNull()?.date ?: bill.date
                                    Triple("Settled ${shortDateFmt.format(Date(settledDate))}", DetailSoftGreen, DetailBrandGreen)
                                }
                                due > BigDecimal.ZERO ->
                                    Triple("Udhari", DetailSoftRed, DetailCrimsonRed)
                                else ->
                                    Triple("Paid · Cash", DetailSoftGreen, DetailBrandGreen)
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 5.dp),
                                shape = RoundedCornerShape(18.dp),
                                color = KwSurface,
                                border = BorderStroke(1.dp, DetailCardBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Bill #$billCode",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DetailNavyText
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "${shortDateFmt.format(Date(bill.date))} · $qtySummary",
                                            fontSize = 13.5.sp,
                                            color = DetailSubtleText
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = CurrencyUtils.formatInr(bill.totalSalePrice),
                                            fontSize = 16.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DetailNavyText
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = badgeBg
                                        ) {
                                            Text(
                                                text = badgeText,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = badgeFg,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = DetailSubtleText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // ========================================================
                    // TAB 2 (PAGE 3): REPAYMENTS TAB (Promise Banner + List)
                    // ========================================================
                    if (customer.totalUdhari > BigDecimal.ZERO) {
                        item {
                            val promisedAmt = customer.getPromisedAmount() ?: customer.totalUdhari
                            val promiseTitle = if (promiseDateMillis != null && promiseDateMillis > 0L) {
                                "Promised ${CurrencyUtils.formatInr(promisedAmt)} for ${shortDateFmt.format(Date(promiseDateMillis))}"
                            } else {
                                "Outstanding udhari ${CurrencyUtils.formatInr(customer.totalUdhari)}"
                            }
                            val promiseSubtitle = when {
                                daysLate > 0 -> "$daysLate days late · not paid yet"
                                promiseDateMillis != null && promiseDateMillis > 0L -> "Scheduled reminder · not paid yet"
                                else -> "Set a promise date or record repayment"
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                shape = RoundedCornerShape(18.dp),
                                color = DetailSoftRed,
                                border = BorderStroke(1.dp, kw(0xFFFECACA))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = promiseTitle,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DetailCrimsonRed
                                        )
                                        Text(
                                            text = promiseSubtitle,
                                            fontSize = 13.sp,
                                            color = DetailCrimsonRed.copy(alpha = 0.85f)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            onClick = {
                                                val cal = Calendar.getInstance()
                                                if (promiseDateMillis != null && promiseDateMillis > 0L) {
                                                    cal.timeInMillis = promiseDateMillis
                                                }
                                                DatePickerDialog(
                                                    context,
                                                    { _, y, m, d ->
                                                        cal.set(y, m, d, 9, 0, 0)
                                                        val target = cal.timeInMillis
                                                        scope.launch {
                                                            val updated = customer.copy(promiseDate = target)
                                                            repository.saveCustomer(updated, actorRole = currentUser?.role)
                                                            ReminderScheduler.scheduleCustomerReminder(context, updated)
                                                        }
                                                    },
                                                    cal.get(Calendar.YEAR),
                                                    cal.get(Calendar.MONTH),
                                                    cal.get(Calendar.DAY_OF_MONTH)
                                                ).show()
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            color = KwSurface,
                                            border = BorderStroke(1.5.dp, DetailBrandGreen),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = tr("Change date", "तारीख बदलें"),
                                                    fontSize = 14.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = DetailBrandGreen
                                                )
                                            }
                                        }

                                        if (canAccessCollections) {
                                            Button(
                                                onClick = { activeOverlay = CustomerDetailOverlay.COLLECT_UDHARI },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = DetailBrandGreen,
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(46.dp)
                                            ) {
                                                Text(
                                                    text = tr("Collect now", "अभी वसूलें"),
                                                    fontSize = 14.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val totalPaymentsSum = customerPayments.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.amount) }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("Payments received", "प्राप्त भुगतान"),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = DetailNavyText
                            )
                            Text(
                                text = "Total ${CurrencyUtils.formatInr(totalPaymentsSum)} · ${customerPayments.size} payments",
                                fontSize = 13.sp,
                                color = DetailSubtleText
                            )
                        }
                    }

                    if (customerPayments.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tr("No repayments received yet", "अभी तक कोई भुगतान प्राप्त नहीं हुआ"),
                                    fontSize = 14.sp,
                                    color = DetailSubtleText
                                )
                            }
                        }
                    } else {
                        items(customerPayments, key = { it.id }) { pay ->
                            val recDigits = pay.id.filter { it.isDigit() }.takeLast(3).padStart(3, '0').ifBlank { "316" }
                            val receiptCode = "R-$recDigits"
                            val mode = extractPaymentModeFromNote(pay.note)

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 5.dp),
                                shape = RoundedCornerShape(18.dp),
                                color = KwSurface,
                                border = BorderStroke(1.dp, DetailCardBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier.size(42.dp),
                                        shape = CircleShape,
                                        color = DetailSoftGreen
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = null,
                                                tint = DetailBrandGreen,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = dateTimeFmt.format(Date(pay.date)),
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DetailNavyText
                                        )
                                        Text(
                                            text = "$mode · Receipt #$receiptCode",
                                            fontSize = 13.sp,
                                            color = DetailSubtleText
                                        )
                                    }

                                    Text(
                                        text = CurrencyUtils.formatInr(pay.amount),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DetailBrandGreen
                                    )

                                    Surface(
                                        onClick = {
                                            val receiptObj = RecordedRepaymentReceipt(
                                                receiptNumber = receiptCode,
                                                customerName = customer.name,
                                                customerPhone = customer.phone,
                                                amountReceived = pay.amount,
                                                outstandingBefore = customer.totalUdhari.add(pay.amount),
                                                balanceNow = customer.totalUdhari,
                                                paymentMode = mode,
                                                newPromiseDateMillis = customer.promiseDate
                                            )
                                            shareReceiptViaWhatsApp(context, receiptObj, shopName)
                                        },
                                        modifier = Modifier.size(40.dp),
                                        shape = RoundedCornerShape(11.dp),
                                        color = kw(0xFFF1F5F9)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Outlined.Download,
                                                contentDescription = "Download receipt",
                                                tint = DetailNavyText,
                                                modifier = Modifier.size(18.dp)
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
}

private fun sendWhatsAppToCustomer(
    context: android.content.Context,
    rawPhone: String,
    message: String
) {
    val digits = rawPhone.filter { it.isDigit() }
    val formattedPhone = if (digits.length == 10) "91$digits" else digits
    try {
        val uri = if (formattedPhone.isNotBlank()) {
            Uri.parse("https://api.whatsapp.com/send?phone=$formattedPhone&text=${Uri.encode(message)}")
        } else {
            Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(message)}")
        }
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: Exception) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(Intent.createChooser(share, "Share on WhatsApp"))
    }
}

private fun shareReceiptViaWhatsApp(
    context: android.content.Context,
    receipt: RecordedRepaymentReceipt,
    shopName: String
) {
    val msg = buildString {
        appendLine("*$shopName — Payment Receipt #${receipt.receiptNumber}*")
        appendLine("Customer: ${receipt.customerName}")
        appendLine("Amount Received: ${CurrencyUtils.formatInr(receipt.amountReceived)} (${receipt.paymentMode})")
        appendLine("Outstanding Before: ${CurrencyUtils.formatInr(receipt.outstandingBefore)}")
        appendLine("Balance Now: ${CurrencyUtils.formatInr(receipt.balanceNow)}")
    }
    sendWhatsAppToCustomer(context, receipt.customerPhone, msg)
}
