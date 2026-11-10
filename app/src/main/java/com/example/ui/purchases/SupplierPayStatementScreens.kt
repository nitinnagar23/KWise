package com.example.ui.purchases

import com.example.ui.theme.KwSurface
import com.example.ui.theme.kw
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.repository.CattleFeedRepository
import com.example.model.AppSettings
import com.example.model.PurchaseInvoice
import com.example.model.SupplierKhataSummary
import com.example.model.SupplierLedgerEntry
import com.example.model.User
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatTextMuted
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import com.example.util.CurrencyUtils
import com.example.util.tr
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val CashbookBannerBg: Color get() = kw(0xFFE0F2FE)
private val CashbookBannerBorder: Color get() = kw(0xFFBAE6FD)
private val CashbookBannerText: Color get() = kw(0xFF0369A1)
private val CashbookAddedCardBg: Color get() = kw(0xFFEEF2F8)
private val StatementTableHeaderBg: Color get() = kw(0xFFF3F5F9)
private val AmberCoinColor: Color get() = kw(0xFFF59E0B)
// ============================================================================
// PAGES 1 & 2: PAY SUPPLIER FULL-SCREEN FLOW + VOUCHER CONFIRMATION
// ============================================================================
@Composable
fun PaySupplierFlowScreen(
    initialSummary: SupplierKhataSummary?,
    allSummaries: List<SupplierKhataSummary>,
    allInvoices: List<PurchaseInvoice>,
    metadataMap: Map<String, SupplierProfileMetadata>,
    repository: CattleFeedRepository,
    currentUser: User?,
    onBack: () -> Unit,
    onViewSupplierKhata: (String) -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentStep by remember { mutableIntStateOf(1) } // 1 = Pay supplier form (Page 1), 2 = Payment recorded (Page 2)

    var selectedSummary by remember(initialSummary, allSummaries) {
        mutableStateOf(
            initialSummary
                ?: allSummaries.firstOrNull { it.balancePayable > BigDecimal.ZERO }
                ?: allSummaries.firstOrNull()
                ?: SupplierKhataSummary(wholesalerName = "Supplier 1", balancePayable = BigDecimal.ZERO)
        )
    }
    var supplierDropdownOpen by remember { mutableStateOf(false) }

    val owedBefore = selectedSummary.balancePayable.max(BigDecimal.ZERO)
    val defaultPayAmount = remember(owedBefore) {
        when {
            owedBefore <= BigDecimal.ZERO -> BigDecimal.ZERO
            owedBefore >= BigDecimal("5000") -> BigDecimal("5000")
            else -> owedBefore
        }
    }

    var amountInput by remember(selectedSummary.wholesalerName, owedBefore) {
        mutableStateOf(
            if (defaultPayAmount > BigDecimal.ZERO) {
                NumberFormat.getNumberInstance(Locale("en", "IN")).format(defaultPayAmount.toLong())
            } else ""
        )
    }

    val enteredAmount = remember(amountInput) {
        amountInput.replace(",", "").replace("₹", "").trim().toBigDecimalOrNull()?.max(BigDecimal.ZERO)
            ?: BigDecimal.ZERO
    }

    var paymentMode by remember { mutableStateOf("CASH") } // CASH, UPI, BANK
    var paymentTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var payAgainstOldestFirst by remember { mutableStateOf(true) }
    var referenceNote by remember { mutableStateOf("") }
    var attachedReceiptUri by remember { mutableStateOf<Uri?>(null) }
    var recordedVoucherNo by remember { mutableStateOf("PV-024") }

    val receiptPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            attachedReceiptUri = uri
        }
    }

    val supplierInvoices = remember(allInvoices, selectedSummary.wholesalerName) {
        allInvoices.filter {
            it.wholesalerName.equals(selectedSummary.wholesalerName, ignoreCase = true) &&
                it.balanceAmount > BigDecimal.ZERO
        }.sortedBy { it.date }
    }

    val selectedInvoiceIds = remember(supplierInvoices) {
        mutableStateListOf<String>().apply {
            addAll(supplierInvoices.map { it.id })
        }
    }

    val shortDateFmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    val meta = metadataMap[selectedSummary.wholesalerName.trim().lowercase(Locale.ROOT)]
    val creditDays = meta?.creditPeriodDays ?: 15
    val lastTxDate = if (selectedSummary.lastTransactionDate > 0L) {
        selectedSummary.lastTransactionDate
    } else {
        System.currentTimeMillis() - 9L * 24L * 60L * 60L * 1000L
    }
    val nextDueMillis = lastTxDate + creditDays * 24L * 60L * 60L * 1000L

    val remainingOwed = remember(owedBefore, enteredAmount) {
        owedBefore.subtract(enteredAmount).max(BigDecimal.ZERO)
    }

    BackHandler {
        if (currentStep == 2) onDone() else onBack()
    }

    if (currentStep == 1) {
        // ============================================================
        // PAGE 1: Pay supplier · Record a payment
        // ============================================================
        Scaffold(
            containerColor = FlatDarkBg,
            bottomBar = {
                Surface(
                    color = FlatDarkBg,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("pay_supplier_cancel_btn"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, FlatDarkBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = FlatDarkCard,
                                contentColor = FlatTextPrimary
                            )
                        ) {
                            Text(
                                text = tr("Cancel", "रद्द करें"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val errEnterValidAmt = tr("Enter a valid payment amount", "कृपया सही भुगतान राशि दर्ज करें")
                        Button(
                            onClick = {
                                if (enteredAmount <= BigDecimal.ZERO) {
                                    Toast.makeText(context, errEnterValidAmt, Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val voucherNum = "PV-${String.format(Locale.US, "%03d", (System.currentTimeMillis() / 1000 % 90 + 10).toInt())}"
                                recordedVoucherNo = voucherNum
                                val finalNote = buildString {
                                    if (referenceNote.isNotBlank()) {
                                        append(referenceNote.trim())
                                    } else {
                                        val firstBill = supplierInvoices.firstOrNull()
                                        if (firstBill != null) {
                                            val shortNo = formatShortPurchaseBillNo(firstBill.invoiceNumber, 1)
                                            append("against #$shortNo")
                                        } else {
                                            append("Voucher #$voucherNum")
                                        }
                                    }
                                }
                                scope.launch {
                                    repository.recordSupplierPayment(
                                        wholesalerName = selectedSummary.wholesalerName,
                                        amount = enteredAmount,
                                        paymentMode = paymentMode,
                                        note = finalNote,
                                        recordedByUserId = currentUser?.id ?: ""
                                    )
                                    currentStep = 2
                                }
                            },
                            modifier = Modifier
                                .weight(1.35f)
                                .height(50.dp)
                                .testTag("confirm_supplier_payment_btn"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SupGreen,
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = "Pay ${CurrencyUtils.formatInr(enteredAmount)}",
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onBack,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("pay_supplier_back_btn"),
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

                    Column {
                        Text(
                            text = tr("Pay supplier", "सप्लायर को भुगतान करें"),
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                        Text(
                            text = tr("Record a payment", "भुगतान दर्ज करें"),
                            fontSize = 13.sp,
                            color = FlatTextSecondary
                        )
                    }
                }

                // Supplier Summary Card
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        onClick = {
                            if (allSummaries.size > 1) {
                                supplierDropdownOpen = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
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
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(SupAvatarBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = supplierInitials(selectedSummary.wholesalerName),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SupAvatarText
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = selectedSummary.wholesalerName,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FlatTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (allSummaries.size > 1) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                tint = FlatTextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    val subLine = if (creditDays > 0) {
                                        "Credit $creditDays days · next due ${shortDateFmt.format(Date(nextDueMillis))}"
                                    } else {
                                        "Cash only"
                                    }
                                    Text(
                                        text = subLine,
                                        fontSize = 12.5.sp,
                                        color = FlatTextSecondary
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = tr("YOU OWE", "देना है"),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextSecondary,
                                    letterSpacing = 0.6.sp
                                )
                                Text(
                                    text = CurrencyUtils.formatInr(owedBefore),
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (owedBefore > BigDecimal.ZERO) SupRed else SupGreen
                                )
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = supplierDropdownOpen,
                        onDismissRequest = { supplierDropdownOpen = false }
                    ) {
                        allSummaries.forEach { sup ->
                            DropdownMenuItem(
                                text = {
                                    Text("${sup.wholesalerName} (${CurrencyUtils.formatInr(sup.balancePayable)})")
                                },
                                onClick = {
                                    selectedSummary = sup
                                    supplierDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                // AMOUNT PAID Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = tr("AMOUNT PAID", "भुगतान की गई राशि"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.7.sp
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(2.dp, SupGreen)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "₹ ",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = SupGreen
                            )
                            BasicTextField(
                                value = amountInput,
                                onValueChange = { raw ->
                                    val filtered = raw.filter { it.isDigit() || it == '.' || it == ',' }
                                    amountInput = filtered
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = TextStyle(
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SupGreen
                                ),
                                cursorBrush = SolidColor(SupGreen),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("supplier_pay_amount_input")
                            )
                        }
                    }

                    // Quick Amount Chips: Full ₹..., Half ₹..., ₹5,000, ₹2,000
                    val halfAmt = owedBefore.divide(BigDecimal("2"), 0, RoundingMode.HALF_UP)
                    val quickOptions = remember(owedBefore, halfAmt) {
                        buildList {
                            if (owedBefore > BigDecimal.ZERO) {
                                add("Full ${CurrencyUtils.formatInr(owedBefore)}" to owedBefore)
                                add("Half ${CurrencyUtils.formatInr(halfAmt)}" to halfAmt)
                            }
                            add("₹5,000" to BigDecimal("5000"))
                            add("₹2,000" to BigDecimal("2000"))
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickOptions.forEach { (label, valAmt) ->
                            val isSelected = enteredAmount.compareTo(valAmt) == 0 && valAmt > BigDecimal.ZERO
                            Surface(
                                onClick = {
                                    amountInput = NumberFormat.getNumberInstance(Locale("en", "IN")).format(valAmt.toLong())
                                },
                                shape = RoundedCornerShape(50),
                                color = if (isSelected) SupGreen else SupChipIdleBg
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else FlatTextPrimary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                // PAYMENT MODE Section
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = tr("PAYMENT MODE", "भुगतान का माध्यम"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.7.sp
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = SupChipIdleBg
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("CASH" to "Cash", "UPI" to "UPI", "BANK" to "Bank").forEach { (modeKey, modeLabel) ->
                                val isSelected = paymentMode == modeKey
                                Surface(
                                    onClick = { paymentMode = modeKey },
                                    shape = RoundedCornerShape(11.dp),
                                    color = if (isSelected) SupGreen else Color.Transparent,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = modeLabel,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else FlatTextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Blue Cashbook Info Banner
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = CashbookBannerBg,
                        border = BorderStroke(1.dp, CashbookBannerBorder)
                    ) {
                        val modeDesc = when (paymentMode) {
                            "UPI" -> "UPI out"
                            "BANK" -> "bank transfer out"
                            else -> "cash out"
                        }
                        Text(
                            text = "${CurrencyUtils.formatInr(enteredAmount)} will be added to the Cashbook as $modeDesc.",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CashbookBannerText,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }

                // DATE & TIME Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = tr("DATE", "तारीख"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.7.sp
                        )
                        val isToday = remember(paymentTimestamp) {
                            val nowCal = Calendar.getInstance()
                            val txCal = Calendar.getInstance().apply { timeInMillis = paymentTimestamp }
                            nowCal.get(Calendar.YEAR) == txCal.get(Calendar.YEAR) &&
                                nowCal.get(Calendar.DAY_OF_YEAR) == txCal.get(Calendar.DAY_OF_YEAR)
                        }
                        val dateLabel = if (isToday) {
                            "Today, ${shortDateFmt.format(Date(paymentTimestamp))}"
                        } else {
                            shortDateFmt.format(Date(paymentTimestamp))
                        }

                        Surface(
                            onClick = {
                                val cal = Calendar.getInstance().apply { timeInMillis = paymentTimestamp }
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        val updated = Calendar.getInstance().apply {
                                            timeInMillis = paymentTimestamp
                                            set(Calendar.YEAR, y)
                                            set(Calendar.MONTH, m)
                                            set(Calendar.DAY_OF_MONTH, d)
                                        }
                                        paymentTimestamp = updated.timeInMillis
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = FlatDarkCard,
                            border = BorderStroke(1.dp, FlatDarkBorder)
                        ) {
                            Text(
                                text = dateLabel,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                            )
                        }
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
                            letterSpacing = 0.7.sp
                        )
                        Surface(
                            onClick = {
                                val cal = Calendar.getInstance().apply { timeInMillis = paymentTimestamp }
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        val updated = Calendar.getInstance().apply {
                                            timeInMillis = paymentTimestamp
                                            set(Calendar.HOUR_OF_DAY, hour)
                                            set(Calendar.MINUTE, minute)
                                        }
                                        paymentTimestamp = updated.timeInMillis
                                    },
                                    cal.get(Calendar.HOUR_OF_DAY),
                                    cal.get(Calendar.MINUTE),
                                    false
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = FlatDarkCard,
                            border = BorderStroke(1.dp, FlatDarkBorder)
                        ) {
                            Text(
                                text = timeFmt.format(Date(paymentTimestamp)),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                            )
                        }
                    }
                }

                // PAY AGAINST Section
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = tr("PAY AGAINST", "किस बिल के विरुद्ध"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.7.sp
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = SupChipIdleBg
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                true to tr("Oldest bills first", "पुराने बिल पहले"),
                                false to tr("Choose bills", "बिल चुनें")
                            ).forEach { (isOldest, label) ->
                                val selected = payAgainstOldestFirst == isOldest
                                Surface(
                                    onClick = { payAgainstOldestFirst = isOldest },
                                    shape = RoundedCornerShape(11.dp),
                                    color = if (selected) SupGreen else Color.Transparent,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) Color.White else FlatTextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bill Allocation Preview Cards
                    if (supplierInvoices.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
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
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Bill #P-212 · ${shortDateFmt.format(Date(lastTxDate))}",
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FlatTextPrimary
                                    )
                                    Text(
                                        text = "Due ${CurrencyUtils.formatInr(owedBefore)}",
                                        fontSize = 12.5.sp,
                                        color = FlatTextSecondary
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = tr("Paying", "भुगतान"),
                                        fontSize = 11.5.sp,
                                        color = FlatTextSecondary
                                    )
                                    Text(
                                        text = CurrencyUtils.formatInr(enteredAmount.min(owedBefore.max(enteredAmount))),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SupGreen
                                    )
                                }
                            }
                        }
                    } else {
                        var remainingToAllocate = enteredAmount
                        supplierInvoices.forEachIndexed { idx, inv ->
                            val isChecked = payAgainstOldestFirst || selectedInvoiceIds.contains(inv.id)
                            val allocatedForThis = if (isChecked && remainingToAllocate > BigDecimal.ZERO) {
                                val alloc = remainingToAllocate.min(inv.balanceAmount)
                                remainingToAllocate = remainingToAllocate.subtract(alloc).max(BigDecimal.ZERO)
                                alloc
                            } else {
                                BigDecimal.ZERO
                            }
                            val shortNo = formatShortPurchaseBillNo(inv.invoiceNumber, idx + 1)

                            Surface(
                                onClick = {
                                    if (!payAgainstOldestFirst) {
                                        if (selectedInvoiceIds.contains(inv.id)) {
                                            selectedInvoiceIds.remove(inv.id)
                                        } else {
                                            selectedInvoiceIds.add(inv.id)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (!payAgainstOldestFirst) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    if (checked) selectedInvoiceIds.add(inv.id)
                                                    else selectedInvoiceIds.remove(inv.id)
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = SupGreen)
                                            )
                                        }
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = "Bill #$shortNo · ${shortDateFmt.format(Date(inv.date))}",
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FlatTextPrimary
                                            )
                                            Text(
                                                text = "Due ${CurrencyUtils.formatInr(inv.balanceAmount)}",
                                                fontSize = 12.5.sp,
                                                color = FlatTextSecondary
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = tr("Paying", "भुगतान"),
                                            fontSize = 11.5.sp,
                                            color = FlatTextSecondary
                                        )
                                        Text(
                                            text = CurrencyUtils.formatInr(allocatedForThis),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SupGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // REFERENCE / NOTE (OPTIONAL) + Attach Photo of Receipt
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = tr("REFERENCE / NOTE (OPTIONAL)", "रेफरेंस / नोट (वैकल्पिक)"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.7.sp
                    )

                    OutlinedTextField(
                        value = referenceNote,
                        onValueChange = { referenceNote = it },
                        placeholder = {
                            Text(
                                text = tr("UTR or cheque number", "UTR या चेक नंबर"),
                                color = FlatTextMuted
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = FlatDarkCard,
                            unfocusedContainerColor = FlatDarkCard,
                            focusedBorderColor = SupGreen,
                            unfocusedBorderColor = FlatDarkBorder,
                            focusedTextColor = FlatTextPrimary,
                            unfocusedTextColor = FlatTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("supplier_pay_reference_input")
                    )

                    if (attachedReceiptUri != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = SupGreenSoft,
                            border = BorderStroke(1.dp, SupGreen)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tr("Receipt photo attached", "रसीद फोटो जोड़ी गई"),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SupGreen
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove photo",
                                    tint = SupGreen,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { attachedReceiptUri = null }
                                )
                            }
                        }
                    } else {
                        DashedActionButton(
                            text = tr("+ Attach photo of receipt", "+ रसीद की फोटो जोड़ें"),
                            leadingPlus = false,
                            onClick = {
                                receiptPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                    }
                }

                // You will still owe green summary card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = SupGreenSoft,
                    border = BorderStroke(1.dp, SupGreen.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = tr("You will still owe", "अब देना बाकी रहेगा"),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SupGreen
                        )
                        Text(
                            text = CurrencyUtils.formatInr(remainingOwed),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = SupGreen
                        )
                    }
                }
            }
        }
    } else {
        // ============================================================
        // PAGE 2: Payment Confirmation Screen ("₹5,000 paid")
        // ============================================================
        val modeDisplay = when (paymentMode) {
            "UPI" -> "UPI"
            "BANK" -> "Bank"
            else -> "Cash"
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(FlatDarkBg)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Large soft-green checkmark circle
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(SupGreenSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = SupGreen,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "${CurrencyUtils.formatInr(enteredAmount)} paid",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = FlatTextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "To ${selectedSummary.wholesalerName} · $modeDisplay · Voucher #$recordedVoucherNo",
                fontSize = 13.5.sp,
                color = FlatTextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // You owed / Paid today / You owe now Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr("You owed", "पहले बकाया था"),
                            fontSize = 14.sp,
                            color = FlatTextSecondary
                        )
                        Text(
                            text = CurrencyUtils.formatInr(owedBefore),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                    }

                    HorizontalDivider(color = FlatDarkBorder, thickness = 1.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr("Paid today", "आज भुगतान किया"),
                            fontSize = 14.sp,
                            color = FlatTextSecondary
                        )
                        Text(
                            text = "−${CurrencyUtils.formatInr(enteredAmount)}",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = SupGreen
                        )
                    }

                    HorizontalDivider(color = FlatTextPrimary, thickness = 1.5.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr("You owe now", "अब देना बाकी है"),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                        Text(
                            text = CurrencyUtils.formatInr(remainingOwed),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (remainingOwed > BigDecimal.ZERO) SupRed else SupGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Added to Cashbook Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CashbookAddedCardBg
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = tr("Added to Cashbook", "कैशबुक में जोड़ा गया"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary
                    )
                    Text(
                        text = "$modeDisplay out ${CurrencyUtils.formatInr(enteredAmount)} · Supplier · ${selectedSummary.wholesalerName}",
                        fontSize = 13.sp,
                        color = FlatTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom Actions: Share payment slip | View supplier khata | Done
            Button(
                onClick = {
                    val slipText = buildString {
                        append("*Payment Voucher #$recordedVoucherNo*\n")
                        append("To: *${selectedSummary.wholesalerName}*\n")
                        append("Mode: $modeDisplay\n")
                        append("------------------------\n")
                        append("Previous Balance: ${CurrencyUtils.formatInr(owedBefore)}\n")
                        append("Paid Today: ${CurrencyUtils.formatInr(enteredAmount)}\n")
                        append("Balance Payable Now: *${CurrencyUtils.formatInr(remainingOwed)}*\n")
                    }
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, slipText)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share payment slip"))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("share_payment_slip_btn"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SupGreen,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = tr("Share payment slip", "भुगतान पर्ची शेयर करें"),
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(
                onClick = { onViewSupplierKhata(selectedSummary.wholesalerName) },
                modifier = Modifier.testTag("view_supplier_khata_after_pay_btn")
            ) {
                Text(
                    text = tr("View supplier khata", "सप्लायर खाता देखें"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = SupGreen
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            TextButton(
                onClick = onDone,
                modifier = Modifier.testTag("supplier_pay_done_btn")
            ) {
                Text(
                    text = tr("Done", "हो गया"),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextSecondary
                )
            }
        }
    }
}

// ============================================================================
// PAGE 4: SUPPLIER STATEMENT SCREEN ("Statement · Sharma Traders")
// ============================================================================
private data class StatementRowItem(
    val dateMillis: Long,
    val dateText: String,
    val detailsText: String,
    val billAmount: BigDecimal?,
    val paidAmount: BigDecimal?,
    val runningBalance: BigDecimal
)

@Composable
fun SupplierStatementScreen(
    summary: SupplierKhataSummary,
    metadata: SupplierProfileMetadata?,
    repository: CattleFeedRepository,
    settings: AppSettings?,
    currentUser: User?,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<SupplierLedgerEntry>>(emptyList()) }

    LaunchedEffect(summary.wholesalerName, summary.balancePayable, summary.totalPurchases, summary.totalPaid) {
        entries = repository.getSupplierLedger(summary.wholesalerName)
    }

    var formatTab by remember { mutableIntStateOf(0) } // 0 = PDF, 1 = Image
    var selectedPeriod by remember { mutableStateOf("All time") } // This month, 3 months, All time, Custom
    var customStartMillis by remember { mutableLongStateOf(System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L) }
    var customEndMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val nowMillis = remember { System.currentTimeMillis() }
    val shortDateFmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val fullDateFmt = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    val numFmt = remember { NumberFormat.getNumberInstance(Locale("en", "IN")) }

    val cutoffStartMillis = remember(selectedPeriod, customStartMillis, nowMillis) {
        val cal = Calendar.getInstance()
        when (selectedPeriod) {
            "This month" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.timeInMillis
            }
            "3 months" -> nowMillis - 90L * 24L * 60L * 60L * 1000L
            "Custom" -> customStartMillis
            else -> 0L
        }
    }

    val statementRows = remember(entries, metadata, cutoffStartMillis, selectedPeriod, customEndMillis) {
        val asc = entries.sortedBy { it.date }
        var running = metadata?.openingBalance ?: BigDecimal.ZERO
        val built = mutableListOf<StatementRowItem>()
        var invCounter = 1
        asc.forEach { entry ->
            when (entry) {
                is SupplierLedgerEntry.InvoiceEntry -> {
                    val inv = entry.invoice
                    val shortNo = formatShortPurchaseBillNo(inv.invoiceNumber, invCounter++)
                    running = running.add(inv.balanceAmount)
                    val itemsDesc = inv.getItems().joinToString(", ") { item ->
                        if (item.bags > 0) "${item.feedName} × ${item.bags} bags"
                        else "${item.feedName} × ${CurrencyUtils.formatWeight(item.totalKg)} kg"
                    }.ifBlank { "Cattle Feed" }
                    val charges = inv.ladhan.add(inv.utaar).add(inv.gaadiKiraya)
                    val chargesSuffix = if (charges > BigDecimal.ZERO) {
                        " (incl. charges ${CurrencyUtils.formatInr(charges)})"
                    } else ""
                    if (inv.date >= cutoffStartMillis && (selectedPeriod != "Custom" || inv.date <= customEndMillis)) {
                        built.add(
                            StatementRowItem(
                                dateMillis = inv.date,
                                dateText = shortDateFmt.format(Date(inv.date)),
                                detailsText = "Purchase #$shortNo · $itemsDesc$chargesSuffix",
                                billAmount = inv.totalInvoiceCost,
                                paidAmount = inv.paidAmount.takeIf { it > BigDecimal.ZERO },
                                runningBalance = running
                            )
                        )
                    }
                }
                is SupplierLedgerEntry.PaymentEntry -> {
                    val pay = entry.payment
                    running = running.subtract(pay.amount).max(BigDecimal.ZERO)
                    val modeLabel = when (pay.paymentMode.uppercase(Locale.ROOT)) {
                        "UPI" -> "UPI"
                        "BANK", "BANK_TRANSFER" -> "Bank"
                        "CHEQUE" -> "Cheque"
                        else -> "Cash"
                    }
                    if (pay.date >= cutoffStartMillis && (selectedPeriod != "Custom" || pay.date <= customEndMillis)) {
                        built.add(
                            StatementRowItem(
                                dateMillis = pay.date,
                                dateText = shortDateFmt.format(Date(pay.date)),
                                detailsText = "Payment · $modeLabel",
                                billAmount = null,
                                paidAmount = pay.amount,
                                runningBalance = running
                            )
                        )
                    }
                }
            }
        }
        built
    }

    val totalBillSum = remember(statementRows) {
        statementRows.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.billAmount ?: BigDecimal.ZERO) }
    }
    val totalPaidSum = remember(statementRows) {
        statementRows.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.paidAmount ?: BigDecimal.ZERO) }
    }

    val periodRangeLabel = remember(statementRows, nowMillis) {
        val firstDate = statementRows.firstOrNull()?.dateMillis ?: (nowMillis - 25L * 24L * 60L * 60L * 1000L)
        val lastDate = statementRows.lastOrNull()?.dateMillis ?: nowMillis
        "${shortDateFmt.format(Date(firstDate))} – ${fullDateFmt.format(Date(lastDate))}"
    }

    val shopName = settings?.shopName?.ifBlank { "Shop Name" } ?: "Shop Name"
    val shopCity = settings?.shopAddress?.ifBlank { "Gohra" } ?: "Gohra"
    val shopPhone = settings?.shopPhone?.ifBlank { "89410 00198" } ?: "89410 00198"
    val ownerName = currentUser?.name?.ifBlank { "Nitin Nagar" } ?: "Nitin Nagar"

    val supplierCity = metadata?.city?.ifBlank { "Gohra Mandi" } ?: "Gohra Mandi"
    val supplierPhone = summary.wholesalerPhone.ifBlank { metadata?.primaryPhone ?: "98290 40012" }
    val supplierGstin = metadata?.gstin ?: ""
    val creditDays = metadata?.creditPeriodDays ?: 15
    val nextDueDateMillis = remember(summary.lastTransactionDate, creditDays, nowMillis) {
        val base = if (summary.lastTransactionDate > 0L) summary.lastTransactionDate else nowMillis
        base + creditDays * 24L * 60L * 60L * 1000L
    }

    Scaffold(
        containerColor = FlatDarkBg,
        bottomBar = {
            Surface(
                color = FlatDarkBg,
                border = BorderStroke(1.dp, FlatDarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            shareOrSaveSupplierStatement(
                                context = context,
                                asPdf = formatTab == 0,
                                share = true,
                                shopName = shopName,
                                shopSubtitle = "$shopCity · Ph $shopPhone",
                                supplierName = summary.wholesalerName,
                                periodLabel = periodRangeLabel,
                                rows = statementRows,
                                totalBill = totalBillSum,
                                totalPaid = totalPaidSum,
                                balancePayable = summary.balancePayable,
                                preparedBy = ownerName
                            )
                        },
                        modifier = Modifier
                            .weight(1.25f)
                            .height(50.dp)
                            .testTag("supplier_statement_share_btn"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SupGreen,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = if (formatTab == 0) tr("Share PDF", "PDF शेयर करें") else tr("Share image", "इमेज शेयर करें"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            shareOrSaveSupplierStatement(
                                context = context,
                                asPdf = formatTab == 0,
                                share = false,
                                shopName = shopName,
                                shopSubtitle = "$shopCity · Ph $shopPhone",
                                supplierName = summary.wholesalerName,
                                periodLabel = periodRangeLabel,
                                rows = statementRows,
                                totalBill = totalBillSum,
                                totalPaid = totalPaidSum,
                                balancePayable = summary.balancePayable,
                                preparedBy = ownerName
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("supplier_statement_save_btn"),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, SupGreen),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = FlatDarkCard,
                            contentColor = SupGreen
                        )
                    ) {
                        Text(
                            text = tr("Save", "सेव करें"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = SupGreen
                        )
                    }

                    Surface(
                        onClick = {
                            shareOrSaveSupplierStatement(
                                context = context,
                                asPdf = true,
                                share = true,
                                shopName = shopName,
                                shopSubtitle = "$shopCity · Ph $shopPhone",
                                supplierName = summary.wholesalerName,
                                periodLabel = periodRangeLabel,
                                rows = statementRows,
                                totalBill = totalBillSum,
                                totalPaid = totalPaidSum,
                                balancePayable = summary.balancePayable,
                                preparedBy = ownerName
                            )
                        },
                        modifier = Modifier
                            .size(50.dp)
                            .testTag("supplier_statement_print_btn"),
                        shape = RoundedCornerShape(14.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Print,
                                contentDescription = "Print statement",
                                tint = FlatTextPrimary,
                                modifier = Modifier.size(21.dp)
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Bar: <- Statement (Sharma Traders) ... A4
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onBack,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("supplier_statement_back_btn"),
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
                        text = tr("Statement", "स्टेटमेंट"),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary
                    )
                    Text(
                        text = summary.wholesalerName,
                        fontSize = 13.sp,
                        color = FlatTextSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SupChipIdleBg
                ) {
                    Text(
                        text = "A4",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            // Segmented Toggle: PDF | Image
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = SupChipIdleBg
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(0 to "PDF", 1 to "Image").forEach { (idx, label) ->
                        val selected = formatTab == idx
                        Surface(
                            onClick = { formatTab = idx },
                            shape = RoundedCornerShape(11.dp),
                            color = if (selected) SupGreen else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) Color.White else FlatTextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Period Chips: This month | 3 months | All time | Custom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("This month", "3 months", "All time", "Custom").forEach { period ->
                    val isSelected = selectedPeriod == period
                    Surface(
                        onClick = {
                            if (period == "Custom") {
                                val cal = Calendar.getInstance()
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        val picked = Calendar.getInstance().apply {
                                            set(Calendar.YEAR, y)
                                            set(Calendar.MONTH, m)
                                            set(Calendar.DAY_OF_MONTH, d)
                                            set(Calendar.HOUR_OF_DAY, 0)
                                            set(Calendar.MINUTE, 0)
                                        }
                                        customStartMillis = picked.timeInMillis
                                        selectedPeriod = "Custom"
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            } else {
                                selectedPeriod = period
                            }
                        },
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) SupGreen else FlatDarkCard,
                        border = if (isSelected) null else BorderStroke(1.dp, FlatDarkBorder)
                    ) {
                        Text(
                            text = period,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else FlatTextPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // A4 Supplier Statement Preview Card (Page 4)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder),
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Top Green Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .background(SupGreen)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Shop Branding & SUPPLIER STATEMENT Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // KhataWise Green Icon Box with Amber Rupee Badge
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(SupGreen),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(7.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(14.dp)
                                                .height(2.5.dp)
                                                .background(KwSurface, RoundedCornerShape(2.dp))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(11.dp)
                                                .height(2.5.dp)
                                                .background(KwSurface, RoundedCornerShape(2.dp))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(8.dp)
                                                .height(2.5.dp)
                                                .background(KwSurface, RoundedCornerShape(2.dp))
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(3.dp)
                                            .size(13.dp)
                                            .clip(CircleShape)
                                            .background(AmberCoinColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "₹",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = shopName,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FlatTextPrimary
                                    )
                                    Text(
                                        text = "$shopCity · Ph $shopPhone",
                                        fontSize = 11.5.sp,
                                        color = FlatTextSecondary
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "SUPPLIER STATEMENT",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SupGreen,
                                    letterSpacing = 0.4.sp
                                )
                                Text(
                                    text = "As on ${fullDateFmt.format(Date(nowMillis))}",
                                    fontSize = 11.5.sp,
                                    color = FlatTextSecondary
                                )
                            }
                        }

                        HorizontalDivider(color = FlatDarkBorder, thickness = 1.dp)

                        // SUPPLIER & PERIOD Info Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "SUPPLIER",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextSecondary,
                                    letterSpacing = 0.6.sp
                                )
                                Text(
                                    text = summary.wholesalerName,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary
                                )
                                val subGstin = buildString {
                                    append(supplierCity)
                                    if (supplierPhone.isNotBlank()) append(" · $supplierPhone")
                                    if (supplierGstin.isNotBlank()) append(" · GSTIN $supplierGstin")
                                }
                                Text(
                                    text = subGstin,
                                    fontSize = 11.5.sp,
                                    color = FlatTextSecondary
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "PERIOD",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextSecondary,
                                    letterSpacing = 0.6.sp
                                )
                                Text(
                                    text = periodRangeLabel,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary
                                )
                            }
                        }

                        // Table Header Row: DATE | DETAILS | BILL | PAID | BALANCE
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(StatementTableHeaderBg)
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DATE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                modifier = Modifier.width(44.dp)
                            )
                            Text(
                                text = "DETAILS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "BILL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(50.dp)
                            )
                            Text(
                                text = "PAID",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(50.dp)
                            )
                            Text(
                                text = "BALANCE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(58.dp)
                            )
                        }

                        // Table Rows
                        if (statementRows.isEmpty()) {
                            Text(
                                text = tr("No transactions in this period.", "इस अवधि में कोई लेन-देन नहीं है।"),
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            statementRows.forEachIndexed { idx, row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = row.dateText,
                                        fontSize = 11.5.sp,
                                        color = FlatTextPrimary,
                                        modifier = Modifier.width(44.dp)
                                    )
                                    Text(
                                        text = row.detailsText,
                                        fontSize = 11.5.sp,
                                        color = FlatTextPrimary,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 4.dp)
                                    )
                                    Text(
                                        text = row.billAmount?.let { numFmt.format(it.toLong()) } ?: "",
                                        fontSize = 11.5.sp,
                                        color = FlatTextPrimary,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.width(50.dp)
                                    )
                                    Text(
                                        text = row.paidAmount?.let { numFmt.format(it.toLong()) } ?: "",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SupGreen,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.width(50.dp)
                                    )
                                    Text(
                                        text = numFmt.format(row.runningBalance.toLong()),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FlatTextPrimary,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.width(58.dp)
                                    )
                                }
                                if (idx < statementRows.lastIndex) {
                                    HorizontalDivider(color = FlatDarkBorder.copy(alpha = 0.6f), thickness = 0.8.dp)
                                }
                            }
                        }

                        HorizontalDivider(color = FlatTextPrimary, thickness = 1.5.dp)

                        // Total Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(modifier = Modifier.width(44.dp))
                            Text(
                                text = "Total",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = numFmt.format(totalBillSum.toLong()),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(50.dp)
                            )
                            Text(
                                text = numFmt.format(totalPaidSum.toLong()),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(50.dp)
                            )
                            Spacer(modifier = Modifier.width(58.dp))
                        }

                        // Amount payable to supplier (right aligned)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = tr("Amount payable to supplier", "सप्लायर को देय राशि"),
                                fontSize = 11.5.sp,
                                color = FlatTextSecondary
                            )
                            Text(
                                text = CurrencyUtils.formatInr(summary.balancePayable),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (summary.balancePayable > BigDecimal.ZERO) SupRed else SupGreen
                            )
                            if (summary.balancePayable > BigDecimal.ZERO) {
                                Text(
                                    text = "Next due ${shortDateFmt.format(Date(nextDueDateMillis))}",
                                    fontSize = 11.5.sp,
                                    color = FlatTextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Signatures Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Prepared by $ownerName",
                                fontSize = 11.sp,
                                color = FlatTextSecondary
                            )
                            Text(
                                text = "Supplier’s signature",
                                fontSize = 11.sp,
                                color = FlatTextSecondary
                            )
                        }

                        Text(
                            text = "Made with KhataWise · Smart Shop Register",
                            fontSize = 10.5.sp,
                            color = FlatTextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

private fun shareOrSaveSupplierStatement(
    context: Context,
    asPdf: Boolean,
    share: Boolean,
    shopName: String,
    shopSubtitle: String,
    supplierName: String,
    periodLabel: String,
    rows: List<StatementRowItem>,
    totalBill: BigDecimal,
    totalPaid: BigDecimal,
    balancePayable: BigDecimal,
    preparedBy: String
) {
    try {
        val dir = File(context.cacheDir, "bills").apply { mkdirs() }
        val safeName = supplierName.replace(Regex("[^a-zA-Z0-9]"), "_").ifBlank { "Supplier" }
        val file = if (asPdf) {
            File(dir, "Statement_${safeName}.pdf")
        } else {
            File(dir, "Statement_${safeName}.png")
        }

        val width = 595
        val height = (520 + rows.size * 34).coerceAtLeast(842)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Top green bar
        paint.color = android.graphics.Color.parseColor("#0E6F43")
        canvas.drawRect(0f, 0f, width.toFloat(), 10f, paint)

        // Shop header
        paint.color = android.graphics.Color.parseColor("#0F172A")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 18f
        canvas.drawText(shopName, 32f, 46f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = android.graphics.Color.parseColor("#64748B")
        paint.textSize = 12f
        canvas.drawText(shopSubtitle, 32f, 66f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = android.graphics.Color.parseColor("#0E6F43")
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 14f
        canvas.drawText("SUPPLIER STATEMENT", (width - 32).toFloat(), 46f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = android.graphics.Color.parseColor("#64748B")
        paint.textSize = 12f
        canvas.drawText("Period: $periodLabel", (width - 32).toFloat(), 66f, paint)
        paint.textAlign = Paint.Align.LEFT

        // Supplier title
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = android.graphics.Color.parseColor("#0F172A")
        paint.textSize = 16f
        canvas.drawText("Supplier: $supplierName", 32f, 106f, paint)

        var y = 142f
        paint.textSize = 12f
        paint.color = android.graphics.Color.parseColor("#475569")
        canvas.drawText("DATE", 32f, y, paint)
        canvas.drawText("DETAILS", 105f, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("BILL", 400f, y, paint)
        canvas.drawText("PAID", 475f, y, paint)
        canvas.drawText("BALANCE", (width - 32).toFloat(), y, paint)
        paint.textAlign = Paint.Align.LEFT

        y += 12f
        paint.color = android.graphics.Color.parseColor("#CBD5E1")
        canvas.drawLine(32f, y, (width - 32).toFloat(), y, paint)
        y += 24f

        val numFmt = NumberFormat.getNumberInstance(Locale("en", "IN"))
        rows.forEach { r ->
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = android.graphics.Color.parseColor("#0F172A")
            canvas.drawText(r.dateText, 32f, y, paint)
            val shortDet = if (r.detailsText.length > 34) r.detailsText.take(34) + "..." else r.detailsText
            canvas.drawText(shortDet, 105f, y, paint)

            paint.textAlign = Paint.Align.RIGHT
            r.billAmount?.let { canvas.drawText(numFmt.format(it.toLong()), 400f, y, paint) }
            r.paidAmount?.let {
                paint.color = android.graphics.Color.parseColor("#0E6F43")
                canvas.drawText(numFmt.format(it.toLong()), 475f, y, paint)
            }
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = android.graphics.Color.parseColor("#0F172A")
            canvas.drawText(numFmt.format(r.runningBalance.toLong()), (width - 32).toFloat(), y, paint)
            paint.textAlign = Paint.Align.LEFT
            y += 28f
        }

        canvas.drawLine(32f, y, (width - 32).toFloat(), y, paint)
        y += 24f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Total", 105f, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(numFmt.format(totalBill.toLong()), 400f, y, paint)
        canvas.drawText(numFmt.format(totalPaid.toLong()), 475f, y, paint)

        y += 40f
        paint.textSize = 18f
        paint.color = android.graphics.Color.parseColor("#B91C1C")
        canvas.drawText("Amount Payable: ${CurrencyUtils.formatInr(balancePayable)}", (width - 32).toFloat(), y, paint)

        y += 44f
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 11f
        paint.color = android.graphics.Color.parseColor("#64748B")
        canvas.drawText("Prepared by $preparedBy", 32f, y, paint)

        if (asPdf) {
            val pdfDoc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(width, height, 1).create()
            val page = pdfDoc.startPage(pageInfo)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            pdfDoc.finishPage(page)
            FileOutputStream(file).use { out -> pdfDoc.writeTo(out) }
            pdfDoc.close()
        } else {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }

        if (!share) {
            Toast.makeText(context, "Saved ${file.name}", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (asPdf) "application/pdf" else "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share Supplier Statement"))
    } catch (_: Exception) {
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "Supplier Statement - $supplierName\nPeriod: $periodLabel\nPayable: ${CurrencyUtils.formatInr(balancePayable)}"
            )
        }
        context.startActivity(Intent.createChooser(fallback, "Share Supplier Statement"))
    }
}
