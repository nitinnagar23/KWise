package com.example.ui.ledger

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.model.AppSettings
import com.example.model.Customer
import com.example.model.GroupedSaleBill
import com.example.model.PaymentEntry
import com.example.model.SaleEntry
import com.example.model.groupSalesIntoBills
import com.example.ui.components.QrCodeView
import com.example.util.CurrencyUtils
import com.example.util.tr
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val KhataPageBg = Color(0xFFF6F7F9)
private val KhataCardBorder = Color(0xFFE2E8F0)
private val KhataNavyText = Color(0xFF0F172A)
private val KhataSubtleText = Color(0xFF64748B)
private val KhataBrandGreen = Color(0xFF0E6F43)
private val KhataSoftGreen = Color(0xFFDDF5E7)
private val KhataCrimsonRed = Color(0xFFB91C1C)
private val KhataAvatarBg = Color(0xFFE2E8F0)

data class CustomerLedgerRow(
    val id: String,
    val dateMillis: Long,
    val isBill: Boolean,
    val title: String,
    val subtitle: String,
    val debitUdhari: BigDecimal,
    val creditPaid: BigDecimal,
    val runningBalance: BigDecimal,
    val billShortCode: String = "",
    val receiptCode: String = "",
    val paymentMode: String = "Cash"
)

fun buildCustomerLedgerRows(
    customer: Customer,
    sales: List<SaleEntry>,
    payments: List<PaymentEntry>
): List<CustomerLedgerRow> {
    data class RawEvent(
        val id: String,
        val dateMillis: Long,
        val isBill: Boolean,
        val bill: GroupedSaleBill? = null,
        val payment: PaymentEntry? = null
    )

    val events = mutableListOf<RawEvent>()
    val groupedBills = groupSalesIntoBills(
        sales.filter { !it.isDeleted && it.billStatus != "CANCELLED" },
        descending = false
    )
    groupedBills.forEach { b ->
        events.add(RawEvent(id = "sale_${b.id}", dateMillis = b.date, isBill = true, bill = b))
    }
    payments.filter { !it.isDeleted }.forEach { p ->
        events.add(RawEvent(id = "pay_${p.id}", dateMillis = p.date, isBill = false, payment = p))
    }

    // Sort oldest to newest to compute running balance
    val sortedAsc = events.sortedWith(compareBy<RawEvent> { it.dateMillis }.thenBy { if (it.isBill) 0 else 1 })
    var runningBal = customer.getOpeningUdhari()
    val computedRows = mutableListOf<CustomerLedgerRow>()

    if (runningBal > BigDecimal.ZERO) {
        computedRows.add(
            CustomerLedgerRow(
                id = "opening_${customer.id}",
                dateMillis = customer.createdAt,
                isBill = true,
                title = "Opening udhari",
                subtitle = "Opening balance",
                debitUdhari = runningBal,
                creditPaid = BigDecimal.ZERO,
                runningBalance = runningBal,
                billShortCode = "OPEN"
            )
        )
    }

    sortedAsc.forEachIndexed { idx, ev ->
        if (ev.isBill && ev.bill != null) {
            val b = ev.bill
            val billCode = b.billNumber.ifBlank {
                b.id.filter { it.isDigit() }.takeLast(4).padStart(4, '0').ifBlank { "${1001 + idx}" }
            }
            val dueOnBill = b.udhariAmount
            runningBal = runningBal.add(dueOnBill)
            val sub = when {
                b.paidAmount > BigDecimal.ZERO && dueOnBill > BigDecimal.ZERO ->
                    "Total ${CurrencyUtils.formatInr(b.totalSalePrice)} · paid ${CurrencyUtils.formatInr(b.paidAmount)}"
                dueOnBill > BigDecimal.ZERO -> "Udhari bill"
                else -> "Paid bill"
            }
            computedRows.add(
                CustomerLedgerRow(
                    id = ev.id,
                    dateMillis = b.date,
                    isBill = true,
                    title = "Bill #$billCode",
                    subtitle = sub,
                    debitUdhari = dueOnBill,
                    creditPaid = BigDecimal.ZERO,
                    runningBalance = runningBal,
                    billShortCode = billCode
                )
            )
        } else if (!ev.isBill && ev.payment != null) {
            val p = ev.payment
            val recNum = p.id.filter { it.isDigit() }.takeLast(3).padStart(3, '0').ifBlank { "${290 + idx}" }
            val receiptCode = "R-$recNum"
            val mode = extractPaymentModeFromNote(p.note)
            runningBal = runningBal.subtract(p.amount).max(BigDecimal.ZERO)
            computedRows.add(
                CustomerLedgerRow(
                    id = ev.id,
                    dateMillis = p.date,
                    isBill = false,
                    title = "Repayment · $mode",
                    subtitle = "Receipt #$receiptCode",
                    debitUdhari = BigDecimal.ZERO,
                    creditPaid = p.amount,
                    runningBalance = runningBal,
                    receiptCode = receiptCode,
                    paymentMode = mode
                )
            )
        }
    }

    return computedRows
}

fun extractPaymentModeFromNote(note: String): String {
    val upper = note.uppercase()
    return when {
        upper.contains("[UPI]") || upper.contains("UPI") -> "UPI"
        upper.contains("[BANK]") || upper.contains("BANK") -> "Bank"
        else -> "Cash"
    }
}

fun cleanPaymentNoteText(note: String): String {
    return note
        .replace(Regex("^\\[(CASH|UPI|BANK)\\]\\s*", RegexOption.IGNORE_CASE), "")
        .trim()
}

// ============================================================================
// PAGES 4 & 5: CUSTOMER STATEMENT SCREEN (PDF & IMAGE MODES)
// ============================================================================

enum class StatementPeriodChip(val labelEn: String, val labelHi: String) {
    THIS_MONTH("This month", "इस महीने"),
    THREE_MONTHS("3 months", "3 महीने"),
    ALL_TIME("All time", "पूरा समय"),
    CUSTOM("Custom", "कस्टम")
}

@Composable
fun CustomerStatementScreen(
    customer: Customer,
    ledgerRowsAsc: List<CustomerLedgerRow>,
    settings: AppSettings?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    BackHandler { onBack() }

    var isImageMode by remember { mutableStateOf(false) }
    var selectedPeriod by remember { mutableStateOf(StatementPeriodChip.ALL_TIME) }
    var customStartMillis by remember {
        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        mutableLongStateOf(cal.timeInMillis)
    }
    var customEndMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Image mode switches (Page 5)
    var showLast4Entries by remember { mutableStateOf(true) }
    var showUpiQrCode by remember { mutableStateOf(true) }

    val filteredRowsAsc = remember(ledgerRowsAsc, selectedPeriod, customStartMillis, customEndMillis) {
        val now = System.currentTimeMillis()
        val startCutoff = when (selectedPeriod) {
            StatementPeriodChip.THIS_MONTH -> Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.timeInMillis
            StatementPeriodChip.THREE_MONTHS -> Calendar.getInstance().apply {
                add(Calendar.MONTH, -3)
            }.timeInMillis
            StatementPeriodChip.ALL_TIME -> 0L
            StatementPeriodChip.CUSTOM -> customStartMillis
        }
        val endCutoff = if (selectedPeriod == StatementPeriodChip.CUSTOM) customEndMillis else now
        ledgerRowsAsc.filter { it.dateMillis in startCutoff..endCutoff }
    }

    val shortDateFmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val fullDateFmt = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }

    val shopName = settings?.shopName?.ifBlank { "KhataWise Store" } ?: "KhataWise Store"
    val shopAddress = settings?.shopAddress?.ifBlank { customer.village } ?: customer.village
    val shopPhone = settings?.shopPhone.orEmpty()
    val shopUpi = settings?.shopUpiId?.ifBlank { "shop@upi" } ?: "shop@upi"

    val periodRangeLabel = remember(filteredRowsAsc, fullDateFmt) {
        if (filteredRowsAsc.isEmpty()) {
            fullDateFmt.format(Date())
        } else {
            val first = shortDateFmt.format(Date(filteredRowsAsc.first().dateMillis))
            val last = fullDateFmt.format(Date(filteredRowsAsc.last().dateMillis))
            "$first – $last"
        }
    }

    val totalDebit = remember(filteredRowsAsc) {
        filteredRowsAsc.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.debitUdhari) }
    }
    val totalCredit = remember(filteredRowsAsc) {
        filteredRowsAsc.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.creditPaid) }
    }

    val pdfSavedToast = tr("Statement PDF saved", "स्टेटमेंट PDF सेव किया गया")
    val imageSavedToast = tr("Statement image saved", "स्टेटमेंट इमेज सेव की गई")
    val copiedToast = tr("Statement summary copied", "स्टेटमेंट कॉपी किया गया")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KhataPageBg)
            .navigationBarsPadding()
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    onClick = onBack,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("statement_back_btn"),
                    shape = RoundedCornerShape(13.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, KhataCardBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = KhataNavyText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = tr("Statement", "स्टेटमेंट"),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = KhataNavyText
                    )
                    Text(
                        text = customer.name,
                        fontSize = 13.sp,
                        color = KhataSubtleText
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFEFF3F8)
            ) {
                Text(
                    text = "A4",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = KhataNavyText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Segmented Toggle: PDF | Image
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFEEF2F6),
                border = BorderStroke(1.dp, KhataCardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    listOf(
                        false to tr("PDF", "PDF"),
                        true to tr("Image", "इमेज")
                    ).forEach { (imgMode, label) ->
                        val selected = isImageMode == imgMode
                        Surface(
                            onClick = { isImageMode = imgMode },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag(if (imgMode) "statement_mode_image" else "statement_mode_pdf"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) KhataBrandGreen else Color.Transparent
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (selected) Color.White else KhataNavyText
                                )
                            }
                        }
                    }
                }
            }

            // Period Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatementPeriodChip.entries.forEach { chip ->
                    val selected = selectedPeriod == chip
                    Surface(
                        onClick = {
                            if (chip == StatementPeriodChip.CUSTOM) {
                                val cal = Calendar.getInstance()
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        val startCal = Calendar.getInstance().apply {
                                            set(y, m, d, 0, 0, 0)
                                        }
                                        customStartMillis = startCal.timeInMillis
                                        customEndMillis = System.currentTimeMillis()
                                        selectedPeriod = StatementPeriodChip.CUSTOM
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            } else {
                                selectedPeriod = chip
                            }
                        },
                        shape = RoundedCornerShape(50),
                        color = if (selected) KhataBrandGreen else Color.White,
                        border = if (selected) null else BorderStroke(1.dp, KhataCardBorder)
                    ) {
                        Text(
                            text = tr(chip.labelEn, chip.labelHi),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else KhataNavyText,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                }
            }

            if (!isImageMode) {
                // ============================================================
                // PAGE 4: A4 STATEMENT PAPER PREVIEW CARD
                // ============================================================
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("statement_pdf_preview_card"),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, KhataCardBorder),
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Top Green Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(KhataBrandGreen)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Store Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = KhataBrandGreen
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Outlined.ReceiptLong,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = shopName,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = KhataNavyText
                                        )
                                        val addrPhone = listOf(
                                            shopAddress,
                                            if (shopPhone.isNotBlank()) "Ph $shopPhone" else ""
                                        ).filter { it.isNotBlank() }.joinToString(" · ")
                                        if (addrPhone.isNotBlank()) {
                                            Text(
                                                text = addrPhone,
                                                fontSize = 11.5.sp,
                                                color = KhataSubtleText
                                            )
                                        }
                                        if (shopUpi.isNotBlank()) {
                                            Text(
                                                text = "UPI $shopUpi",
                                                fontSize = 11.5.sp,
                                                color = KhataSubtleText
                                            )
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "STATEMENT",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = KhataBrandGreen,
                                        letterSpacing = 0.6.sp
                                    )
                                    Text(
                                        text = "As on ${fullDateFmt.format(Date())}",
                                        fontSize = 11.5.sp,
                                        color = KhataSubtleText
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Customer & Period Gray Band
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF8FAFC)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "CUSTOMER",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = KhataSubtleText,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = customer.name,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = KhataNavyText
                                        )
                                        val custSub = listOf(customer.village, customer.phone)
                                            .filter { it.isNotBlank() }
                                            .joinToString(" · ")
                                        if (custSub.isNotBlank()) {
                                            Text(
                                                text = custSub,
                                                fontSize = 11.5.sp,
                                                color = KhataSubtleText
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "PERIOD",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = KhataSubtleText,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = periodRangeLabel,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = KhataNavyText
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Table Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("DATE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = KhataSubtleText, modifier = Modifier.width(48.dp))
                                Text("DETAILS", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = KhataSubtleText, modifier = Modifier.weight(1f))
                                Text("DEBIT", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = KhataSubtleText, textAlign = TextAlign.End, modifier = Modifier.width(54.dp))
                                Text("CREDIT", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = KhataSubtleText, textAlign = TextAlign.End, modifier = Modifier.width(54.dp))
                                Text("BALANCE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = KhataSubtleText, textAlign = TextAlign.End, modifier = Modifier.width(58.dp))
                            }

                            if (filteredRowsAsc.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tr("No transactions in this period", "इस अवधि में कोई लेन-देन नहीं"),
                                        fontSize = 12.5.sp,
                                        color = KhataSubtleText
                                    )
                                }
                            } else {
                                filteredRowsAsc.forEach { row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = shortDateFmt.format(Date(row.dateMillis)),
                                            fontSize = 11.5.sp,
                                            color = KhataSubtleText,
                                            modifier = Modifier.width(48.dp)
                                        )
                                        Text(
                                            text = row.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = KhataNavyText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = if (row.debitUdhari > BigDecimal.ZERO) CurrencyUtils.formatInr(row.debitUdhari).removePrefix("₹").trim() else "",
                                            fontSize = 11.5.sp,
                                            color = KhataNavyText,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.width(54.dp)
                                        )
                                        Text(
                                            text = if (row.creditPaid > BigDecimal.ZERO) CurrencyUtils.formatInr(row.creditPaid).removePrefix("₹").trim() else "",
                                            fontSize = 11.5.sp,
                                            color = KhataBrandGreen,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.width(54.dp)
                                        )
                                        Text(
                                            text = CurrencyUtils.formatInr(row.runningBalance).removePrefix("₹").trim(),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = KhataNavyText,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.width(58.dp)
                                        )
                                    }
                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                }
                            }

                            // Bold Total Line
                            HorizontalDivider(thickness = 1.5.dp, color = KhataNavyText)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(modifier = Modifier.width(48.dp))
                                Text(
                                    text = tr("Total", "कुल"),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = KhataNavyText,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = CurrencyUtils.formatInr(totalDebit).removePrefix("₹").trim(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = KhataNavyText,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.width(54.dp)
                                )
                                Text(
                                    text = CurrencyUtils.formatInr(totalCredit).removePrefix("₹").trim(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = KhataBrandGreen,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.width(54.dp)
                                )
                                Spacer(modifier = Modifier.width(58.dp))
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // QR Code & Total Outstanding Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    QrCodeView(
                                        data = "upi://pay?pa=$shopUpi&pn=$shopName&am=${customer.totalUdhari.toPlainString()}&cu=INR",
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Column {
                                        Text(
                                            text = tr("Scan to pay", "भुगतान के लिए स्कैन करें"),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = KhataNavyText
                                        )
                                        Text(
                                            text = "UPI $shopUpi",
                                            fontSize = 11.sp,
                                            color = KhataSubtleText
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = tr("Total outstanding", "कुल बकाया"),
                                        fontSize = 11.5.sp,
                                        color = KhataSubtleText
                                    )
                                    Text(
                                        text = CurrencyUtils.formatInr(customer.totalUdhari),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (customer.totalUdhari > BigDecimal.ZERO) KhataCrimsonRed else KhataBrandGreen
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Made with KhataWise · Smart Shop Register",
                                fontSize = 10.5.sp,
                                color = Color(0xFF94A3B8),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else {
                // ============================================================
                // PAGE 5: SHAREABLE IMAGE CARD + TOGGLES
                // ============================================================
                val recent4 = remember(filteredRowsAsc) {
                    filteredRowsAsc.takeLast(4).reversed()
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .testTag("statement_image_preview_card"),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, KhataCardBorder),
                    shadowElevation = 3.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Deep Green Top Banner
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(KhataBrandGreen)
                                .padding(20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(32.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.2f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.ReceiptLong,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = shopName,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Account statement · ${customer.name}",
                                fontSize = 13.5.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                            Text(
                                text = listOf(customer.village, "as on ${fullDateFmt.format(Date())}")
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                fontSize = 12.5.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = tr("Total outstanding", "कुल बकाया"),
                                fontSize = 12.5.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Text(
                                text = CurrencyUtils.formatInr(customer.totalUdhari),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }

                        // Lower White Section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            if (showLast4Entries && recent4.isNotEmpty()) {
                                Text(
                                    text = tr("RECENT ENTRIES", "हाल की एंट्री"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = KhataSubtleText,
                                    letterSpacing = 0.6.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                recent4.forEach { entry ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = shortDateFmt.format(Date(entry.dateMillis)),
                                            fontSize = 13.sp,
                                            color = KhataSubtleText,
                                            modifier = Modifier.width(56.dp)
                                        )
                                        Text(
                                            text = entry.title,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = KhataNavyText,
                                            modifier = Modifier.weight(1f)
                                        )
                                        val isDebit = entry.debitUdhari > BigDecimal.ZERO
                                        val amt = if (isDebit) entry.debitUdhari else entry.creditPaid
                                        Text(
                                            text = (if (isDebit) "+" else "−") + CurrencyUtils.formatInr(amt),
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isDebit) KhataCrimsonRed else KhataBrandGreen
                                        )
                                    }
                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            if (showUpiQrCode) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    QrCodeView(
                                        data = "upi://pay?pa=$shopUpi&pn=$shopName&am=${customer.totalUdhari.toPlainString()}&cu=INR",
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Pay ${CurrencyUtils.formatInr(customer.totalUdhari)} by UPI",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = KhataNavyText
                                        )
                                        Text(
                                            text = shopUpi,
                                            fontSize = 12.sp,
                                            color = KhataSubtleText
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            Text(
                                text = "Made with KhataWise",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Toggles Card (Last 4 entries & UPI QR code)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, KhataCardBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr("Last 4 entries", "अंतिम 4 एंट्री"),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = KhataNavyText
                                )
                                Text(
                                    text = tr("Off shows only the total", "बंद करने पर केवल कुल बकाया दिखेगा"),
                                    fontSize = 12.5.sp,
                                    color = KhataSubtleText
                                )
                            }
                            Switch(
                                checked = showLast4Entries,
                                onCheckedChange = { showLast4Entries = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = KhataBrandGreen
                                )
                            )
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr("UPI QR code", "UPI QR कोड"),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = KhataNavyText
                                )
                                Text(
                                    text = tr("Pay-now QR for the outstanding", "बकाया भुगतान के लिए तुरंत QR"),
                                    fontSize = 12.5.sp,
                                    color = KhataSubtleText
                                )
                            }
                            Switch(
                                checked = showUpiQrCode,
                                onCheckedChange = { showUpiQrCode = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = KhataBrandGreen
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Bottom Sticky Action Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            border = BorderStroke(1.dp, KhataCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (!isImageMode) {
                            shareOrSaveStatementPdf(
                                context = context,
                                customer = customer,
                                rows = filteredRowsAsc,
                                shopName = shopName,
                                shopAddress = shopAddress,
                                shopPhone = shopPhone,
                                shopUpi = shopUpi,
                                shareNow = true
                            )
                        } else {
                            shareOrSaveStatementImage(
                                context = context,
                                customer = customer,
                                rows = filteredRowsAsc.takeLast(4).reversed(),
                                shopName = shopName,
                                shopUpi = shopUpi,
                                showEntries = showLast4Entries,
                                shareNow = true
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KhataBrandGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(50.dp)
                        .testTag("statement_primary_share_btn")
                ) {
                    Text(
                        text = if (!isImageMode) tr("Share PDF", "PDF शेयर करें") else tr("Share image", "इमेज शेयर करें"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Surface(
                    onClick = {
                        if (!isImageMode) {
                            val file = shareOrSaveStatementPdf(
                                context = context,
                                customer = customer,
                                rows = filteredRowsAsc,
                                shopName = shopName,
                                shopAddress = shopAddress,
                                shopPhone = shopPhone,
                                shopUpi = shopUpi,
                                shareNow = false
                            )
                            if (file != null) {
                                Toast.makeText(context, pdfSavedToast, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val file = shareOrSaveStatementImage(
                                context = context,
                                customer = customer,
                                rows = filteredRowsAsc.takeLast(4).reversed(),
                                shopName = shopName,
                                shopUpi = shopUpi,
                                showEntries = showLast4Entries,
                                shareNow = false
                            )
                            if (file != null) {
                                Toast.makeText(context, imageSavedToast, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, KhataBrandGreen),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("statement_save_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (!isImageMode) tr("Save", "सेव करें") else tr("Save to gallery", "गैलरी में सेव करें"),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = KhataBrandGreen
                        )
                    }
                }

                Surface(
                    onClick = {
                        if (!isImageMode) {
                            shareOrSaveStatementPdf(
                                context = context,
                                customer = customer,
                                rows = filteredRowsAsc,
                                shopName = shopName,
                                shopAddress = shopAddress,
                                shopPhone = shopPhone,
                                shopUpi = shopUpi,
                                shareNow = true
                            )
                        } else {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val summary = "$shopName — ${customer.name}: Outstanding ${CurrencyUtils.formatInr(customer.totalUdhari)}"
                            cm?.setPrimaryClip(ClipData.newPlainText("Statement", summary))
                            Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, KhataCardBorder),
                    modifier = Modifier.size(50.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (!isImageMode) Icons.Outlined.Print else Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            tint = KhataNavyText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun shareOrSaveStatementPdf(
    context: Context,
    customer: Customer,
    rows: List<CustomerLedgerRow>,
    shopName: String,
    shopAddress: String,
    shopPhone: String,
    shopUpi: String,
    shareNow: Boolean
): File? {
    return try {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Green top bar
        paint.color = android.graphics.Color.parseColor("#0E6F43")
        canvas.drawRect(0f, 0f, 595f, 12f, paint)

        paint.color = android.graphics.Color.parseColor("#0F172A")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 18f
        canvas.drawText(shopName, 36f, 46f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 11f
        paint.color = android.graphics.Color.parseColor("#64748B")
        canvas.drawText("$shopAddress · Ph $shopPhone · UPI $shopUpi", 36f, 64f, paint)

        paint.color = android.graphics.Color.parseColor("#0F172A")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 14f
        canvas.drawText("Customer: ${customer.name} (${customer.village})", 36f, 96f, paint)

        var y = 130f
        val dateFmt = SimpleDateFormat("dd MMM", Locale.getDefault())
        rows.takeLast(22).forEach { r ->
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 11f
            paint.color = android.graphics.Color.parseColor("#334155")
            canvas.drawText(dateFmt.format(Date(r.dateMillis)), 36f, y, paint)
            canvas.drawText(r.title, 110f, y, paint)
            if (r.debitUdhari > BigDecimal.ZERO) {
                canvas.drawText("+${CurrencyUtils.formatInr(r.debitUdhari)}", 330f, y, paint)
            }
            if (r.creditPaid > BigDecimal.ZERO) {
                canvas.drawText("-${CurrencyUtils.formatInr(r.creditPaid)}", 410f, y, paint)
            }
            canvas.drawText(CurrencyUtils.formatInr(r.runningBalance), 495f, y, paint)
            y += 22f
        }

        y += 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 16f
        paint.color = android.graphics.Color.parseColor("#B91C1C")
        canvas.drawText("Total Outstanding: ${CurrencyUtils.formatInr(customer.totalUdhari)}", 36f, y, paint)

        doc.finishPage(page)
        val dir = File(context.cacheDir, "statements").apply { mkdirs() }
        val outFile = File(dir, "Statement_${customer.name.replace(" ", "_")}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()

        if (shareNow) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Statement PDF"))
        }
        outFile
    } catch (_: Exception) {
        null
    }
}

private fun shareOrSaveStatementImage(
    context: Context,
    customer: Customer,
    rows: List<CustomerLedgerRow>,
    shopName: String,
    shopUpi: String,
    showEntries: Boolean,
    shareNow: Boolean
): File? {
    return try {
        val width = 800
        val height = if (showEntries) 920 else 560
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = android.graphics.Color.WHITE
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        paint.color = android.graphics.Color.parseColor("#0E6F43")
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), 320f), 0f, 0f, paint)

        paint.color = android.graphics.Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 30f
        canvas.drawText(shopName, 48f, 70f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 24f
        canvas.drawText("Account statement · ${customer.name}", 48f, 125f, paint)

        paint.textSize = 22f
        canvas.drawText("Total outstanding", 48f, 195f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 64f
        canvas.drawText(CurrencyUtils.formatInr(customer.totalUdhari), 48f, 270f, paint)

        var y = 390f
        if (showEntries) {
            val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
            rows.forEach { r ->
                paint.color = android.graphics.Color.parseColor("#0F172A")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textSize = 24f
                canvas.drawText("${fmt.format(Date(r.dateMillis))}   ${r.title}", 48f, y, paint)
                val amtStr = if (r.debitUdhari > BigDecimal.ZERO) "+${CurrencyUtils.formatInr(r.debitUdhari)}" else "-${CurrencyUtils.formatInr(r.creditPaid)}"
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(amtStr, 560f, y, paint)
                y += 60f
            }
        }

        paint.color = android.graphics.Color.parseColor("#64748B")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 22f
        canvas.drawText("Pay by UPI: $shopUpi · Made with KhataWise", 48f, height - 48f, paint)

        val dir = File(context.cacheDir, "statements").apply { mkdirs() }
        val outFile = File(dir, "Statement_${customer.name.replace(" ", "_")}.png")
        FileOutputStream(outFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        if (shareNow) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Statement Image"))
        }
        outFile
    } catch (_: Exception) {
        null
    }
}

// ============================================================================
// PAGE 6: COLLECT UDHARI (RECORD A REPAYMENT) SCREEN
// ============================================================================

data class RecordedRepaymentReceipt(
    val receiptNumber: String,
    val customerName: String,
    val customerPhone: String,
    val amountReceived: BigDecimal,
    val outstandingBefore: BigDecimal,
    val balanceNow: BigDecimal,
    val paymentMode: String,
    val newPromiseDateMillis: Long?
)

@Composable
fun CollectUdhariScreen(
    customer: Customer,
    onCancel: () -> Unit,
    onConfirmCollect: (
        amount: BigDecimal,
        paymentMode: String,
        dateMillis: Long,
        noteText: String,
        newPromiseDateMillis: Long?,
        sendWhatsappReceipt: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    BackHandler { onCancel() }

    val outstanding = customer.totalUdhari.max(BigDecimal.ZERO)
    val halfAmount = remember(outstanding) {
        outstanding.divide(BigDecimal("2"), 0, RoundingMode.HALF_UP)
    }

    var amountInput by remember(outstanding) {
        val defaultAmt = if (outstanding > BigDecimal.ZERO) {
            outstanding.min(BigDecimal("2000")).stripTrailingZeros().toPlainString()
        } else ""
        mutableStateOf(defaultAmt)
    }
    var selectedMode by remember { mutableStateOf("Cash") }
    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var noteInput by remember { mutableStateOf("") }

    val parsedAmount = remember(amountInput) {
        amountInput.trim().toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
    }
    val balanceAfter = remember(outstanding, parsedAmount) {
        outstanding.subtract(parsedAmount).max(BigDecimal.ZERO)
    }

    var setPromiseForRest by remember { mutableStateOf(true) }
    var selectedPromiseQuickChip by remember { mutableIntStateOf(1) } // 0 = In 3 days, 1 = In 1 week, 2 = Pick date
    var customPromiseMillis by remember {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }
        mutableLongStateOf(cal.timeInMillis)
    }
    var sendWhatsappReceipt by remember { mutableStateOf(customer.phone.isNotBlank()) }

    val dateLabelFmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val timeLabelFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KhataPageBg)
            .navigationBarsPadding()
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                onClick = onCancel,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("collect_udhari_back_btn"),
                shape = RoundedCornerShape(13.dp),
                color = Color.White,
                border = BorderStroke(1.dp, KhataCardBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = KhataNavyText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column {
                Text(
                    text = tr("Collect udhari", "उधारी वसूली"),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = KhataNavyText
                )
                Text(
                    text = tr("Record a repayment", "भुगतान दर्ज करें"),
                    fontSize = 13.sp,
                    color = KhataSubtleText
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Customer Summary Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                border = BorderStroke(1.dp, KhataCardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
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
                            color = KhataAvatarBg
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = customer.getInitials(),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = KhataNavyText
                                )
                            }
                        }
                        Column {
                            Text(
                                text = customer.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = KhataNavyText
                            )
                            val sub = buildList {
                                if (customer.village.isNotBlank()) add(customer.village)
                                if (customer.promiseDate != null && customer.promiseDate > 0L) {
                                    add("promised ${dateLabelFmt.format(Date(customer.promiseDate))}")
                                }
                            }.joinToString(" · ")
                            if (sub.isNotBlank()) {
                                Text(
                                    text = sub,
                                    fontSize = 12.5.sp,
                                    color = KhataSubtleText
                                )
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "OUTSTANDING",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = KhataSubtleText,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = CurrencyUtils.formatInr(outstanding),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = KhataCrimsonRed
                        )
                    }
                }
            }

            // AMOUNT RECEIVED
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = tr("AMOUNT RECEIVED", "प्राप्त राशि"),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = KhataSubtleText,
                    letterSpacing = 0.6.sp
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(2.dp, KhataBrandGreen)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "₹ ",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = KhataBrandGreen
                        )
                        BasicTextField(
                            value = amountInput,
                            onValueChange = { next ->
                                if (next.all { it.isDigit() || it == '.' }) {
                                    amountInput = next
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = TextStyle(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = KhataBrandGreen
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("collect_udhari_amount_input")
                        )
                    }
                }
            }

            // Quick Amount Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val quickPresets = buildList {
                    if (outstanding > BigDecimal.ZERO) {
                        add("Full ${CurrencyUtils.formatInr(outstanding)}" to outstanding)
                    }
                    if (halfAmount > BigDecimal.ZERO) {
                        add("Half ${CurrencyUtils.formatInr(halfAmount)}" to halfAmount)
                    }
                    add("₹1,000" to BigDecimal("1000"))
                    add("₹2,000" to BigDecimal("2000"))
                }
                quickPresets.forEach { (chipLabel, chipVal) ->
                    val isSelected = parsedAmount.compareTo(chipVal) == 0
                    Surface(
                        onClick = { amountInput = chipVal.stripTrailingZeros().toPlainString() },
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) KhataBrandGreen else Color(0xFFEEF2F6)
                    ) {
                        Text(
                            text = chipLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isSelected) Color.White else KhataNavyText,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // PAYMENT MODE
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = tr("PAYMENT MODE", "भुगतान माध्यम"),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = KhataSubtleText,
                    letterSpacing = 0.6.sp
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFEEF2F6),
                    border = BorderStroke(1.dp, KhataCardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    ) {
                        listOf("Cash", "UPI", "Bank").forEach { mode ->
                            val selected = selectedMode == mode
                            Surface(
                                onClick = { selectedMode = mode },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(11.dp),
                                color = if (selected) KhataBrandGreen else Color.Transparent
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = mode,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (selected) Color.White else KhataNavyText
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // DATE & TIME
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = tr("DATE", "तारीख"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = KhataSubtleText,
                        letterSpacing = 0.6.sp
                    )
                    Surface(
                        onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    cal.set(Calendar.YEAR, y)
                                    cal.set(Calendar.MONTH, m)
                                    cal.set(Calendar.DAY_OF_MONTH, d)
                                    selectedDateMillis = cal.timeInMillis
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, KhataCardBorder)
                    ) {
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        ) {
                            Text(
                                text = "Today, ${dateLabelFmt.format(Date(selectedDateMillis))}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = KhataNavyText
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = tr("TIME", "समय"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = KhataSubtleText,
                        letterSpacing = 0.6.sp
                    )
                    Surface(
                        onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                            TimePickerDialog(
                                context,
                                { _, h, min ->
                                    cal.set(Calendar.HOUR_OF_DAY, h)
                                    cal.set(Calendar.MINUTE, min)
                                    selectedDateMillis = cal.timeInMillis
                                },
                                cal.get(Calendar.HOUR_OF_DAY),
                                cal.get(Calendar.MINUTE),
                                false
                            ).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, KhataCardBorder)
                    ) {
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        ) {
                            Text(
                                text = timeLabelFmt.format(Date(selectedDateMillis)),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = KhataNavyText
                            )
                        }
                    }
                }
            }

            // NOTE (OPTIONAL)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = tr("NOTE (OPTIONAL)", "नोट (वैकल्पिक)"),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = KhataSubtleText,
                    letterSpacing = 0.6.sp
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, KhataCardBorder)
                ) {
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        if (noteInput.isEmpty()) {
                            Text(
                                text = tr("Paid at shop", "दुकान पर भुगतान किया"),
                                fontSize = 14.5.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        BasicTextField(
                            value = noteInput,
                            onValueChange = { noteInput = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                color = KhataNavyText
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Balance after this payment banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = KhataSoftGreen,
                border = BorderStroke(1.dp, Color(0xFF86EFAC))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = tr("Balance after this payment", "इस भुगतान के बाद शेष उधारी"),
                            fontSize = 12.5.sp,
                            color = KhataBrandGreen
                        )
                        Text(
                            text = CurrencyUtils.formatInr(balanceAfter),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = KhataBrandGreen
                        )
                    }
                    Text(
                        text = tr("Applied to oldest bills first", "सबसे पुराने बिलों में पहले समायोजित"),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = KhataBrandGreen
                    )
                }
            }

            // Promise Date & WhatsApp Receipt Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                border = BorderStroke(1.dp, KhataCardBorder)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (balanceAfter > BigDecimal.ZERO) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tr("Set a promise date for the rest", "बाकी उधारी के लिए वायदा तारीख तय करें"),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = KhataNavyText
                                    )
                                    Text(
                                        text = tr("Reminder goes out the day before", "एक दिन पहले रिमाइंडर भेजा जाता है"),
                                        fontSize = 12.5.sp,
                                        color = KhataSubtleText
                                    )
                                }
                                Switch(
                                    checked = setPromiseForRest,
                                    onCheckedChange = { setPromiseForRest = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = KhataBrandGreen
                                    )
                                )
                            }

                            if (setPromiseForRest) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        0 to tr("In 3 days", "3 दिन में"),
                                        1 to tr("In 1 week", "1 सप्ताह में"),
                                        2 to tr("Pick date", "तारीख चुनें")
                                    ).forEach { (idx, label) ->
                                        val selected = selectedPromiseQuickChip == idx
                                        Surface(
                                            onClick = {
                                                selectedPromiseQuickChip = idx
                                                when (idx) {
                                                    0 -> {
                                                        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 3) }
                                                        customPromiseMillis = c.timeInMillis
                                                    }
                                                    1 -> {
                                                        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }
                                                        customPromiseMillis = c.timeInMillis
                                                    }
                                                    2 -> {
                                                        val c = Calendar.getInstance().apply { timeInMillis = customPromiseMillis }
                                                        DatePickerDialog(
                                                            context,
                                                            { _, y, m, d ->
                                                                c.set(y, m, d, 9, 0, 0)
                                                                customPromiseMillis = c.timeInMillis
                                                            },
                                                            c.get(Calendar.YEAR),
                                                            c.get(Calendar.MONTH),
                                                            c.get(Calendar.DAY_OF_MONTH)
                                                        ).show()
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(50),
                                            color = if (selected) KhataBrandGreen else Color(0xFFEEF2F6)
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (selected) Color.White else KhataNavyText,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("Send receipt on WhatsApp", "व्हाट्सएप पर रसीद भेजें"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = KhataNavyText
                            )
                            Text(
                                text = if (customer.phone.isNotBlank()) "To ${customer.phone}" else tr("Share payment receipt", "भुगतान रसीद शेयर करें"),
                                fontSize = 12.5.sp,
                                color = KhataSubtleText
                            )
                        }
                        Switch(
                            checked = sendWhatsappReceipt,
                            onCheckedChange = { sendWhatsappReceipt = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = KhataBrandGreen
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Bottom Sticky Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            border = BorderStroke(1.dp, KhataCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onCancel,
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = tr("Cancel", "रद्द करें"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = KhataNavyText
                        )
                    }
                }

                Button(
                    onClick = {
                        if (parsedAmount > BigDecimal.ZERO) {
                            val promiseMillis = if (balanceAfter > BigDecimal.ZERO && setPromiseForRest) {
                                customPromiseMillis
                            } else null
                            onConfirmCollect(
                                parsedAmount,
                                selectedMode,
                                selectedDateMillis,
                                noteInput.trim(),
                                promiseMillis,
                                sendWhatsappReceipt
                            )
                        }
                    },
                    enabled = parsedAmount > BigDecimal.ZERO,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KhataBrandGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("collect_udhari_submit_btn")
                ) {
                    Text(
                        text = "${tr("Collect", "वसूलें")} ${CurrencyUtils.formatInr(parsedAmount)}",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

// ============================================================================
// PAGE 7: REPAYMENT RECEIVED SUCCESS SCREEN
// ============================================================================

@Composable
fun RepaymentReceivedSuccessScreen(
    receipt: RecordedRepaymentReceipt,
    shopName: String,
    onShareWhatsapp: () -> Unit,
    onViewKhata: () -> Unit,
    onDone: () -> Unit
) {
    BackHandler { onDone() }
    val promiseDateFmt = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KhataPageBg)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(36.dp))

        // Checkmark Circle
        Surface(
            modifier = Modifier.size(88.dp),
            shape = CircleShape,
            color = KhataSoftGreen
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = KhataBrandGreen,
                    modifier = Modifier.size(42.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "${CurrencyUtils.formatInr(receipt.amountReceived)} ${tr("received", "प्राप्त हुए")}",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = KhataNavyText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "From ${receipt.customerName} · ${receipt.paymentMode} · Receipt #${receipt.receiptNumber}",
            fontSize = 14.5.sp,
            color = KhataSubtleText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Summary Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            border = BorderStroke(1.dp, KhataCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("Outstanding before", "पहले की उधारी"),
                        fontSize = 14.5.sp,
                        color = KhataSubtleText
                    )
                    Text(
                        text = CurrencyUtils.formatInr(receipt.outstandingBefore),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = KhataNavyText
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("Received", "प्राप्त राशि"),
                        fontSize = 14.5.sp,
                        color = KhataSubtleText
                    )
                    Text(
                        text = "−${CurrencyUtils.formatInr(receipt.amountReceived)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = KhataBrandGreen
                    )
                }

                HorizontalDivider(thickness = 1.5.dp, color = KhataNavyText)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("Balance now", "अब शेष उधारी"),
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = KhataNavyText
                    )
                    Text(
                        text = CurrencyUtils.formatInr(receipt.balanceNow),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (receipt.balanceNow > BigDecimal.ZERO) KhataCrimsonRed else KhataBrandGreen
                    )
                }
            }
        }

        if (receipt.newPromiseDateMillis != null && receipt.newPromiseDateMillis > 0L) {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFDBEAFE),
                border = BorderStroke(1.dp, Color(0xFF93C5FD))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = tr("Promise date set", "वायदा तारीख तय की गई"),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1E3A8A)
                    )
                    Text(
                        text = "${tr("Next payment due", "अगला भुगतान देय")} ${promiseDateFmt.format(Date(receipt.newPromiseDateMillis))}",
                        fontSize = 13.5.sp,
                        color = Color(0xFF1E40AF)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onShareWhatsapp,
            colors = ButtonDefaults.buttonColors(
                containerColor = KhataBrandGreen,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("repayment_share_whatsapp_btn")
        ) {
            Text(
                text = tr("Share receipt on WhatsApp", "व्हाट्सएप पर रसीद शेयर करें"),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onViewKhata) {
            Text(
                text = tr("View khata", "खाता देखें"),
                fontSize = 15.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = KhataBrandGreen
            )
        }

        TextButton(onClick = onDone) {
            Text(
                text = tr("Done", "पूरा हुआ"),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = KhataSubtleText
            )
        }
    }
}
