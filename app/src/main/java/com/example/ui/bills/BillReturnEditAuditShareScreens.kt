package com.example.ui.bills

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.repository.CattleFeedRepository
import com.example.model.BillAuditLog
import com.example.model.BillData
import com.example.model.Customer
import com.example.model.Product
import com.example.model.Role
import com.example.model.SaleEntry
import com.example.model.User
import com.example.model.buildConsolidatedBillNoMap
import com.example.util.BillGenerator
import com.example.util.CurrencyUtils
import com.example.util.tr
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val BPageBg = Color(0xFFF6F7F9)
private val BCardBorder = Color(0xFFE2E8F0)
private val BNavyText = Color(0xFF0F172A)
private val BSubtleText = Color(0xFF64748B)
private val BBrandGreen = Color(0xFF0E6F43)
private val BSoftGreen = Color(0xFFDDF5E7)
private val BCrimsonRed = Color(0xFFB91C1C)
private val BSoftRed = Color(0xFFFEE2E2)
private val BAmberText = Color(0xFF92400E)
private val BSoftAmber = Color(0xFFFEF3C7)
private val BEditedCardBg = Color(0xFFFFFBEB)
private val BEditedCardBorder = Color(0xFFF59E0B)
private val BInfoBlueBg = Color(0xFFE0F2FE)
private val BInfoBlueBorder = Color(0xFFBAE6FD)
private val BInfoBlueText = Color(0xFF075985)
private val BChipIdleBg = Color(0xFFEEF2F6)

// ============================================================================
// PAGES 1, 2 & 3: MULTI-STEP RETURN FLOW
// Step 1: Return items -> Step 2: Settle the return -> Step 3: Return recorded
// ============================================================================

enum class ReturnSettlementMode {
    REDUCE_UDHARI,
    REFUND_MONEY,
    SPLIT
}

@Composable
fun BillReturnFlowScreen(
    primarySale: SaleEntry,
    siblingItems: List<SaleEntry>,
    shortBillNo: String,
    customer: Customer?,
    productMap: Map<String, Product>,
    currentUser: User?,
    repository: CattleFeedRepository,
    onBack: () -> Unit,
    onViewBill: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentStep by remember { mutableIntStateOf(1) } // 1 = Choose items, 2 = Settle, 3 = Recorded

    BackHandler {
        if (currentStep == 2) {
            currentStep = 1
        } else {
            onBack()
        }
    }

    val customerName = customer?.name?.ifBlank { "Walk-in" } ?: "Walk-in"
    val originalBillTotal = remember(siblingItems) {
        siblingItems.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.totalSalePrice) }
    }
    val originalBillPaid = remember(siblingItems) {
        siblingItems.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.paidAmount) }
    }
    val originalBillDue = remember(originalBillTotal, originalBillPaid) {
        originalBillTotal.subtract(originalBillPaid).max(BigDecimal.ZERO)
    }

    // Per-item return selection & return step units (bags if bag product, else kg)
    val selectedForReturn = remember(siblingItems) {
        mutableStateMapOf<String, Boolean>().apply {
            siblingItems.forEachIndexed { index, s -> put(s.id, index == 0) }
        }
    }
    val returnUnitsMap = remember(siblingItems, productMap) {
        mutableStateMapOf<String, BigDecimal>().apply {
            siblingItems.forEach { s ->
                val p = productMap[s.productId]
                val isBagItem = p != null && !p.isLooseOnly && p.kgPerBag > BigDecimal.ONE && s.quantityKg >= p.kgPerBag
                val maxUnits = if (isBagItem && p != null) {
                    s.quantityKg.divide(p.kgPerBag, 0, RoundingMode.DOWN).max(BigDecimal.ONE)
                } else {
                    s.quantityKg.max(BigDecimal.ONE)
                }
                val defaultUnit = BigDecimal.ONE.min(maxUnits)
                put(s.id, defaultUnit)
            }
        }
    }

    var returnChargesToo by remember { mutableStateOf(false) }
    val reasonOptions = listOf("Wrong item", "Damaged", "Changed mind", "Other")
    var selectedReasonChip by remember { mutableStateOf("Wrong item") }
    var noteInput by remember { mutableStateOf("Customer asked for 1 bag only") }

    // Compute total return value across selected items
    val itemsReturnValue = siblingItems.fold(BigDecimal.ZERO) { acc, s ->
        if (selectedForReturn[s.id] == true) {
            val p = productMap[s.productId]
            val isBagItem = p != null && !p.isLooseOnly && p.kgPerBag > BigDecimal.ONE && s.quantityKg >= p.kgPerBag
            val units = returnUnitsMap[s.id] ?: BigDecimal.ONE
            val returnKg = if (isBagItem && p != null) units.multiply(p.kgPerBag).min(s.quantityKg) else units.min(s.quantityKg)
            val ratePerUnit = if (isBagItem && p != null) {
                s.pricePerKgAtSale.multiply(p.kgPerBag).setScale(0, RoundingMode.HALF_UP)
            } else {
                s.pricePerKgAtSale
            }
            val itemRetVal = if (isBagItem) {
                ratePerUnit.multiply(units)
            } else {
                returnKg.multiply(s.pricePerKgAtSale).setScale(0, RoundingMode.HALF_UP)
            }
            acc.add(itemRetVal)
        } else {
            acc
        }
    }
    val extraChargesTotal = BigDecimal("300") // Ladaan ₹100 + Delivery ₹200 optional charges
    val totalReturnValue = if (returnChargesToo) itemsReturnValue.add(extraChargesTotal) else itemsReturnValue

    // Step 2 states
    var settlementMode by remember { mutableStateOf(ReturnSettlementMode.REDUCE_UDHARI) }
    var splitRefundText by remember(totalReturnValue) {
        mutableStateOf(totalReturnValue.divide(BigDecimal("2"), 0, RoundingMode.HALF_UP).toPlainString())
    }
    var putBackInStock by remember { mutableStateOf(true) }
    var createCreditNote by remember { mutableStateOf(true) }
    val creditNoteCode = remember(shortBillNo) {
        val num = shortBillNo.filter { it.isDigit() }.takeLast(2).padStart(3, '0')
        "CN-$num"
    }

    val customerCurrentOutstanding = (customer?.totalUdhari ?: originalBillDue).max(originalBillDue)
    val udhariReductionAmount = when (settlementMode) {
        ReturnSettlementMode.REDUCE_UDHARI -> totalReturnValue
        ReturnSettlementMode.REFUND_MONEY -> BigDecimal.ZERO
        ReturnSettlementMode.SPLIT -> {
            val ref = splitRefundText.toBigDecimalOrNull()?.max(BigDecimal.ZERO)?.min(totalReturnValue) ?: BigDecimal.ZERO
            totalReturnValue.subtract(ref).max(BigDecimal.ZERO)
        }
    }
    val refundCashAmount = totalReturnValue.subtract(udhariReductionAmount).max(BigDecimal.ZERO)
    val customerOutstandingAfter = customerCurrentOutstanding.subtract(udhariReductionAmount).max(BigDecimal.ZERO)
    val billTotalAfter = originalBillTotal.subtract(totalReturnValue).max(BigDecimal.ZERO)
    val billDueAfter = billTotalAfter.subtract(originalBillPaid).max(BigDecimal.ZERO)

    // Primary returned item for stock preview
    val firstSelectedSale = siblingItems.firstOrNull { selectedForReturn[it.id] == true } ?: primarySale
    val firstSelectedProd = productMap[firstSelectedSale.productId]
    val firstIsBag = firstSelectedProd != null && !firstSelectedProd.isLooseOnly && firstSelectedProd.kgPerBag > BigDecimal.ONE && firstSelectedSale.quantityKg >= firstSelectedProd.kgPerBag
    val firstReturnedUnits = returnUnitsMap[firstSelectedSale.id] ?: BigDecimal.ONE
    val firstReturnedLabel = if (firstIsBag) {
        val b = firstReturnedUnits.toInt()
        "$b ${if (b == 1) "bag" else "bags"}"
    } else {
        "${firstReturnedUnits.stripTrailingZeros().toPlainString()} kg"
    }
    val currentStockBags = if (firstSelectedProd != null && firstSelectedProd.kgPerBag > BigDecimal.ONE) {
        firstSelectedProd.currentStockKg.divide(firstSelectedProd.kgPerBag, 0, RoundingMode.DOWN).toInt().coerceAtLeast(0)
    } else 18
    val afterStockBags = if (putBackInStock) currentStockBags + firstReturnedUnits.toInt() else currentStockBags

    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = BBrandGreen,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = Color(0xFFD1D5DB),
        uncheckedBorderColor = Color.Transparent
    )

    when (currentStep) {
        1 -> {
            // ================================================================
            // PAGE 1: RETURN ITEMS
            // ================================================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BPageBg)
                    .navigationBarsPadding()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        onClick = onBack,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("return_items_back_btn"),
                        shape = RoundedCornerShape(13.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, BCardBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = BNavyText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = tr("Return items", "सामान वापसी"),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = BNavyText
                        )
                        Text(
                            text = "Bill #$shortBillNo · $customerName",
                            fontSize = 13.sp,
                            color = BSubtleText
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
                    // Original bill summary card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, BCardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = tr("Original bill", "मूल बिल"),
                                    fontSize = 12.5.sp,
                                    color = BSubtleText
                                )
                                Text(
                                    text = CurrencyUtils.formatInr(originalBillTotal),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BNavyText
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = BSoftGreen
                                ) {
                                    Text(
                                        text = "Paid ${CurrencyUtils.formatInr(originalBillPaid)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BBrandGreen,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = BSoftRed
                                ) {
                                    Text(
                                        text = "Due ${CurrencyUtils.formatInr(originalBillDue)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BCrimsonRed,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = tr("CHOOSE ITEMS TO RETURN", "वापसी के लिए सामान चुनें"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = BSubtleText,
                        letterSpacing = 0.6.sp
                    )

                    // Selectable Item Cards
                    siblingItems.forEach { itemSale ->
                        val prod = productMap[itemSale.productId]
                        val isChecked = selectedForReturn[itemSale.id] == true
                        val isBagItem = prod != null && !prod.isLooseOnly && prod.kgPerBag > BigDecimal.ONE && itemSale.quantityKg >= prod.kgPerBag
                        val maxUnits = if (isBagItem && prod != null) {
                            itemSale.quantityKg.divide(prod.kgPerBag, 0, RoundingMode.DOWN).max(BigDecimal.ONE)
                        } else {
                            itemSale.quantityKg.max(BigDecimal.ONE)
                        }
                        val currentUnits = returnUnitsMap[itemSale.id] ?: BigDecimal.ONE
                        val unitRate = if (isBagItem && prod != null) {
                            itemSale.pricePerKgAtSale.multiply(prod.kgPerBag).setScale(0, RoundingMode.HALF_UP)
                        } else {
                            itemSale.pricePerKgAtSale
                        }
                        val boughtDesc = if (isBagItem) {
                            val bCount = maxUnits.toInt()
                            "Bought $bCount ${if (bCount == 1) "bag" else "bags"} × ${CurrencyUtils.formatInr(unitRate)}"
                        } else {
                            "Bought ${CurrencyUtils.formatWeight(itemSale.quantityKg)} kg × ${CurrencyUtils.formatInr(unitRate)}"
                        }
                        val itemReturnAmt = if (isChecked) {
                            unitRate.multiply(currentUnits).setScale(0, RoundingMode.HALF_UP)
                        } else BigDecimal.ZERO

                        val displayTitle = if (prod != null && !prod.isLooseOnly && prod.kgPerBag > BigDecimal.ONE) {
                            if (prod.feedName.contains("kg", ignoreCase = true)) prod.feedName
                            else "${prod.feedName} (${prod.kgPerBag.stripTrailingZeros().toPlainString()} kg)"
                        } else {
                            prod?.feedName ?: "Feed Item"
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White,
                            border = BorderStroke(
                                width = if (isChecked) 1.8.dp else 1.dp,
                                color = if (isChecked) BBrandGreen else BCardBorder
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedForReturn[itemSale.id] = !isChecked
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Custom Checkbox
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isChecked) BBrandGreen else Color.White)
                                            .border(
                                                width = 1.5.dp,
                                                color = if (isChecked) BBrandGreen else Color(0xFFCBD5E1),
                                                shape = RoundedCornerShape(8.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isChecked) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = displayTitle,
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BNavyText
                                        )
                                        Text(
                                            text = boughtDesc,
                                            fontSize = 13.sp,
                                            color = BSubtleText
                                        )
                                    }

                                    Text(
                                        text = if (isChecked) CurrencyUtils.formatInr(itemReturnAmt) else "—",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isChecked) BNavyText else BSubtleText
                                    )
                                }

                                if (isChecked) {
                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = tr("Return quantity", "वापसी मात्रा"),
                                            fontSize = 14.sp,
                                            color = BSubtleText,
                                            fontWeight = FontWeight.SemiBold
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            Surface(
                                                onClick = {
                                                    val next = currentUnits.subtract(BigDecimal.ONE).max(BigDecimal.ONE)
                                                    returnUnitsMap[itemSale.id] = next
                                                },
                                                modifier = Modifier.size(38.dp),
                                                shape = CircleShape,
                                                color = BChipIdleBg
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.Remove,
                                                        contentDescription = "Decrease",
                                                        tint = BNavyText,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            val unitText = if (isBagItem) {
                                                val b = currentUnits.toInt()
                                                "$b ${if (b == 1) "bag" else "bags"}"
                                            } else {
                                                "${currentUnits.stripTrailingZeros().toPlainString()} kg"
                                            }
                                            Text(
                                                text = unitText,
                                                fontSize = 15.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = BNavyText
                                            )

                                            Surface(
                                                onClick = {
                                                    val next = currentUnits.add(BigDecimal.ONE).min(maxUnits)
                                                    returnUnitsMap[itemSale.id] = next
                                                },
                                                modifier = Modifier.size(38.dp),
                                                shape = CircleShape,
                                                color = BBrandGreen
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.Add,
                                                        contentDescription = "Increase",
                                                        tint = Color.White,
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

                    // Extra Charges & "Return charges too" Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, BCardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Labour · Ladaan", fontSize = 13.5.sp, color = BSubtleText)
                                Text("₹100", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = BNavyText)
                            }
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Delivery charge", fontSize = 13.5.sp, color = BSubtleText)
                                Text("₹200", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = BNavyText)
                            }
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tr("Return charges too", "चार्ज भी वापस करें"),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BNavyText
                                    )
                                    Text(
                                        text = tr("Off keeps the charges on the bill", "बंद रखने पर चार्ज बिल में जुड़े रहेंगे"),
                                        fontSize = 12.5.sp,
                                        color = BSubtleText
                                    )
                                }
                                Switch(
                                    checked = returnChargesToo,
                                    onCheckedChange = { returnChargesToo = it },
                                    colors = switchColors
                                )
                            }
                        }
                    }

                    // REASON
                    Text(
                        text = tr("REASON", "कारण"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = BSubtleText,
                        letterSpacing = 0.6.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        reasonOptions.forEach { reasonLabel ->
                            val selected = selectedReasonChip == reasonLabel
                            Surface(
                                onClick = { selectedReasonChip = reasonLabel },
                                shape = RoundedCornerShape(50),
                                color = if (selected) BBrandGreen else BChipIdleBg
                            ) {
                                Text(
                                    text = reasonLabel,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) Color.White else BNavyText,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                                )
                            }
                        }
                    }

                    // NOTE (OPTIONAL)
                    Text(
                        text = tr("NOTE (OPTIONAL)", "नोट (वैकल्पिक)"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = BSubtleText,
                        letterSpacing = 0.6.sp
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, BCardBorder)
                    ) {
                        BasicTextField(
                            value = noteInput,
                            onValueChange = { noteInput = it },
                            textStyle = TextStyle(
                                fontSize = 14.5.sp,
                                color = BNavyText
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Bottom Bar
                Surface(
                    color = Color.White,
                    border = BorderStroke(1.dp, BCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("Return value", "वापसी राशि"),
                                fontSize = 14.sp,
                                color = BSubtleText
                            )
                            Text(
                                text = CurrencyUtils.formatInr(totalReturnValue),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = BNavyText
                            )
                        }
                        Button(
                            onClick = {
                                if (totalReturnValue > BigDecimal.ZERO) {
                                    currentStep = 2
                                } else {
                                    Toast.makeText(context, "Select at least one item to return", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BBrandGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("return_items_continue_btn")
                        ) {
                            Text(
                                text = tr("Continue", "आगे बढ़ें"),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        2 -> {
            // ================================================================
            // PAGE 2: SETTLE THE RETURN
            // ================================================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BPageBg)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        onClick = { currentStep = 1 },
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(13.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, BCardBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = BNavyText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = tr("Settle the return", "वापसी का निपटान करें"),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = BNavyText
                        )
                        Text(
                            text = "Return value ${CurrencyUtils.formatInr(totalReturnValue)}",
                            fontSize = 13.sp,
                            color = BSubtleText
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
                    Text(
                        text = tr("HOW SHOULD WE SETTLE?", "निपटान कैसे करें?"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = BSubtleText,
                        letterSpacing = 0.6.sp
                    )

                    val settleOptions = listOf(
                        Triple(
                            ReturnSettlementMode.REDUCE_UDHARI,
                            "Reduce udhari",
                            "${customerName.substringBefore(" ")}'s outstanding goes from ${CurrencyUtils.formatInr(customerCurrentOutstanding)} to ${CurrencyUtils.formatInr(customerCurrentOutstanding.subtract(totalReturnValue).max(BigDecimal.ZERO))}."
                        ),
                        Triple(
                            ReturnSettlementMode.REFUND_MONEY,
                            "Refund money",
                            "Give ${CurrencyUtils.formatInr(totalReturnValue)} back by cash or UPI. Cashbook gets a cash-out entry."
                        ),
                        Triple(
                            ReturnSettlementMode.SPLIT,
                            "Split",
                            "Part off udhari, part refund."
                        )
                    )

                    settleOptions.forEach { (mode, title, subtitle) ->
                        val selected = settlementMode == mode
                        Surface(
                            onClick = { settlementMode = mode },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White,
                            border = BorderStroke(
                                width = if (selected) 1.8.dp else 1.dp,
                                color = if (selected) BBrandGreen else BCardBorder
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .border(
                                                width = if (selected) 2.dp else 1.5.dp,
                                                color = if (selected) BBrandGreen else Color(0xFFCBD5E1),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(BBrandGreen)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = title,
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BNavyText
                                        )
                                        Text(
                                            text = subtitle,
                                            fontSize = 13.sp,
                                            color = BSubtleText
                                        )
                                    }
                                }

                                if (selected && mode == ReturnSettlementMode.SPLIT) {
                                    OutlinedTextField(
                                        value = splitRefundText,
                                        onValueChange = { splitRefundText = it },
                                        label = { Text("Cash refund amount (₹)") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Stock & Credit note switches card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, BCardBorder)
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Put $firstReturnedLabel back in stock",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BNavyText
                                    )
                                    Text(
                                        text = "${firstSelectedProd?.feedName ?: "Cattle Feed"}: $currentStockBags → ${currentStockBags + firstReturnedUnits.toInt()} bags",
                                        fontSize = 13.sp,
                                        color = BSubtleText
                                    )
                                }
                                Switch(
                                    checked = putBackInStock,
                                    onCheckedChange = { putBackInStock = it },
                                    colors = switchColors
                                )
                            }

                            HorizontalDivider(color = Color(0xFFF1F5F9))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Create credit note #$creditNoteCode",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BNavyText
                                    )
                                    Text(
                                        text = "Shareable as PDF or image",
                                        fontSize = 13.sp,
                                        color = BSubtleText
                                    )
                                }
                                Switch(
                                    checked = createCreditNote,
                                    onCheckedChange = { createCreditNote = it },
                                    colors = switchColors
                                )
                            }
                        }
                    }

                    // BILL AFTER RETURN Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, BCardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = tr("BILL AFTER RETURN", "वापसी के बाद बिल"),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = BSubtleText,
                                letterSpacing = 0.6.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Bill total", fontSize = 14.sp, color = BSubtleText)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = CurrencyUtils.formatInr(originalBillTotal),
                                        fontSize = 13.5.sp,
                                        color = BSubtleText,
                                        style = TextStyle(textDecoration = TextDecoration.LineThrough)
                                    )
                                    Text(
                                        text = CurrencyUtils.formatInr(billTotalAfter),
                                        fontSize = 16.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BNavyText
                                    )
                                }
                            }

                            HorizontalDivider(color = Color(0xFFF1F5F9))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Paid", fontSize = 14.sp, color = BSubtleText)
                                Text(
                                    text = CurrencyUtils.formatInr(originalBillPaid),
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BBrandGreen
                                )
                            }

                            HorizontalDivider(color = Color(0xFFF1F5F9))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Due on this bill", fontSize = 14.sp, color = BSubtleText)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = CurrencyUtils.formatInr(originalBillDue),
                                        fontSize = 13.5.sp,
                                        color = BSubtleText,
                                        style = TextStyle(textDecoration = TextDecoration.LineThrough)
                                    )
                                    Text(
                                        text = CurrencyUtils.formatInr(billDueAfter),
                                        fontSize = 16.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BCrimsonRed
                                    )
                                }
                            }
                        }
                    }

                    // Info banner
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = BChipIdleBg
                    ) {
                        Text(
                            text = tr(
                                "Returns are saved in the audit log with your name.",
                                "वापसी आपके नाम के साथ ऑडिट लॉग में सेव की जाती है।"
                            ),
                            fontSize = 13.sp,
                            color = BNavyText,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Bottom Bar: Back + Confirm return
                Surface(
                    color = Color.White,
                    border = BorderStroke(1.dp, BCardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { currentStep = 1 },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, BCardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BNavyText),
                            modifier = Modifier
                                .weight(0.38f)
                                .height(52.dp)
                        ) {
                            Text(tr("Back", "पीछे"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        Button(
                            onClick = {
                                val actor = currentUser ?: User(id = "admin", name = "Admin", role = Role.ADMINISTRATOR, pinHash = "")
                                val combinedReason = buildString {
                                    append(selectedReasonChip)
                                    if (noteInput.isNotBlank()) append(" — ${noteInput.trim()}")
                                }
                                val settlementLabel = when (settlementMode) {
                                    ReturnSettlementMode.REDUCE_UDHARI -> "Reduced udhari ${CurrencyUtils.formatInr(udhariReductionAmount)}"
                                    ReturnSettlementMode.REFUND_MONEY -> "Refund ${CurrencyUtils.formatInr(refundCashAmount)} · Cash"
                                    ReturnSettlementMode.SPLIT -> "Split (Udhari −${CurrencyUtils.formatInr(udhariReductionAmount)}, Refund ${CurrencyUtils.formatInr(refundCashAmount)})"
                                }
                                scope.launch {
                                    siblingItems.forEach { s ->
                                        if (selectedForReturn[s.id] == true) {
                                            val p = productMap[s.productId]
                                            val isBag = p != null && !p.isLooseOnly && p.kgPerBag > BigDecimal.ONE && s.quantityKg >= p.kgPerBag
                                            val units = returnUnitsMap[s.id] ?: BigDecimal.ONE
                                            val retKg = if (isBag && p != null) units.multiply(p.kgPerBag).min(s.quantityKg) else units.min(s.quantityKg)
                                            repository.returnSaleItems(
                                                saleId = s.id,
                                                returnKg = retKg,
                                                reason = combinedReason,
                                                performedBy = actor,
                                                putBackInStock = putBackInStock,
                                                settlementSummary = settlementLabel
                                            )
                                        }
                                    }
                                    // Record Cashbook cash-out if money was refunded
                                    if (refundCashAmount > BigDecimal.ZERO) {
                                        recordCashbookRefundEntry(
                                            context = context,
                                            billNo = shortBillNo,
                                            customerName = customerName,
                                            amount = refundCashAmount
                                        )
                                    }
                                    currentStep = 3
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BBrandGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(0.62f)
                                .height(52.dp)
                                .testTag("confirm_return_btn")
                        ) {
                            Text(tr("Confirm return", "वापसी पक्की करें"), fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
                        }
                    }
                }
            }
        }

        3 -> {
            // ================================================================
            // PAGE 3: RETURN RECORDED
            // ================================================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BPageBg)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(28.dp))

                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(BSoftGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = BBrandGreen,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = tr("Return recorded", "वापसी दर्ज हो गई"),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = BNavyText
                )

                Spacer(modifier = Modifier.height(6.dp))

                val subtitleLine = when (settlementMode) {
                    ReturnSettlementMode.REDUCE_UDHARI ->
                        "${CurrencyUtils.formatInr(totalReturnValue)} taken off $customerName's udhari"
                    ReturnSettlementMode.REFUND_MONEY ->
                        "${CurrencyUtils.formatInr(totalReturnValue)} refunded to $customerName"
                    ReturnSettlementMode.SPLIT ->
                        "${CurrencyUtils.formatInr(udhariReductionAmount)} off udhari · ${CurrencyUtils.formatInr(refundCashAmount)} refunded"
                }
                Text(
                    text = subtitleLine,
                    fontSize = 14.5.sp,
                    color = BSubtleText
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Credit note summary card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, BCardBorder)
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
                                text = "Credit note #$creditNoteCode",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = BNavyText
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = BSoftGreen
                            ) {
                                Text(
                                    text = "Issued",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BBrandGreen,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }

                        siblingItems.filter { selectedForReturn[it.id] == true }.forEach { s ->
                            val p = productMap[s.productId]
                            val isBag = p != null && !p.isLooseOnly && p.kgPerBag > BigDecimal.ONE && s.quantityKg >= p.kgPerBag
                            val units = returnUnitsMap[s.id] ?: BigDecimal.ONE
                            val unitRate = if (isBag && p != null) {
                                s.pricePerKgAtSale.multiply(p.kgPerBag).setScale(0, RoundingMode.HALF_UP)
                            } else {
                                s.pricePerKgAtSale
                            }
                            val lineAmt = unitRate.multiply(units).setScale(0, RoundingMode.HALF_UP)
                            val qtyLabel = if (isBag) {
                                val b = units.toInt()
                                "$b ${if (b == 1) "bag" else "bags"}"
                            } else {
                                "${units.stripTrailingZeros().toPlainString()} kg"
                            }
                            val itemTitle = if (p != null && !p.isLooseOnly && p.kgPerBag > BigDecimal.ONE) {
                                "${p.feedName} (${p.kgPerBag.stripTrailingZeros().toPlainString()} kg) × $qtyLabel"
                            } else {
                                "${p?.feedName ?: "Item"} × $qtyLabel"
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(itemTitle, fontSize = 14.sp, color = BSubtleText)
                                Text(
                                    text = CurrencyUtils.formatInr(lineAmt),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BNavyText
                                )
                            }
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Outstanding now", fontSize = 14.sp, color = BSubtleText)
                            Text(
                                text = CurrencyUtils.formatInr(customerOutstandingAfter),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = BCrimsonRed
                            )
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Stock", fontSize = 14.sp, color = BSubtleText)
                            Text(
                                text = "$afterStockBags bags",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = BNavyText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        val shareMsg = buildString {
                            append("*Credit Note #$creditNoteCode* (Bill #$shortBillNo)\n")
                            append("Customer: $customerName\n")
                            append("Return Value: ${CurrencyUtils.formatInr(totalReturnValue)}\n")
                            append("Outstanding Now: ${CurrencyUtils.formatInr(customerOutstandingAfter)}")
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareMsg)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Credit Note"))
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BBrandGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = tr("Share credit note on WhatsApp", "व्हाट्सएप पर क्रेडिट नोट शेयर करें"),
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(onClick = onViewBill) {
                    Text(
                        text = "View bill #$shortBillNo",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = BBrandGreen
                    )
                }

                TextButton(onClick = onDone) {
                    Text(
                        text = tr("Done", "संपन्न"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = BSubtleText
                    )
                }
            }
        }
    }
}

private fun recordCashbookRefundEntry(
    context: Context,
    billNo: String,
    customerName: String,
    amount: BigDecimal
) {
    try {
        val prefs = context.getSharedPreferences("khatawise_cashbook_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("manual_entries_json", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val obj = JSONObject().apply {
            put("id", "refund_${System.currentTimeMillis()}")
            put("date", System.currentTimeMillis())
            put("title", "Return refund · Bill #$billNo")
            put("subtitle", "$customerName · Cash refund")
            put("amount", amount.toPlainString())
            put("isCashIn", false)
            put("tag", "RETURN")
            put("isExpense", false)
        }
        arr.put(obj)
        prefs.edit().putString("manual_entries_json", arr.toString()).apply()
    } catch (_: Exception) {}
}

// ============================================================================
// PAGE 4: EDIT BILL FULL-SCREEN FLOW
// ============================================================================

private data class EditableBillLineState(
    val saleId: String?,
    val product: Product,
    val isBagUnit: Boolean,
    val originalUnits: BigDecimal,
    val currentUnits: BigDecimal,
    val originalUnitRate: BigDecimal,
    val unitRateText: String,
    val originalLineTotal: BigDecimal
)

@Composable
fun EditBillFlowScreen(
    primarySale: SaleEntry,
    siblingItems: List<SaleEntry>,
    shortBillNo: String,
    customer: Customer?,
    products: List<Product>,
    productMap: Map<String, Product>,
    currentUser: User?,
    repository: CattleFeedRepository,
    onCancel: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler { onCancel() }

    val customerName = customer?.name?.ifBlank { "Walk-in" } ?: "Walk-in"
    val dateTimeFmt = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }

    var editableLines by remember(siblingItems, productMap) {
        val initial = siblingItems.mapNotNull { s ->
            val p = productMap[s.productId] ?: products.firstOrNull() ?: return@mapNotNull null
            val isBag = !p.isLooseOnly && p.kgPerBag > BigDecimal.ONE &&
                s.quantityKg >= p.kgPerBag &&
                s.quantityKg.remainder(p.kgPerBag).compareTo(BigDecimal.ZERO) == 0
            val units = if (isBag) {
                s.quantityKg.divide(p.kgPerBag, 0, RoundingMode.DOWN)
            } else {
                s.quantityKg
            }
            val unitRate = if (isBag) {
                s.pricePerKgAtSale.multiply(p.kgPerBag).setScale(0, RoundingMode.HALF_UP)
            } else {
                s.pricePerKgAtSale.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
            }
            EditableBillLineState(
                saleId = s.id,
                product = p,
                isBagUnit = isBag,
                originalUnits = units,
                currentUnits = units,
                originalUnitRate = unitRate,
                unitRateText = unitRate.toPlainString(),
                originalLineTotal = s.totalSalePrice
            )
        }
        mutableStateOf(initial)
    }

    var showAddProductDialog by remember { mutableStateOf(false) }

    // Extra charges (Optional)
    var ladaanChargeText by remember { mutableStateOf("100") }
    var utaarChargeText by remember { mutableStateOf("") }
    var deliveryLabelText by remember { mutableStateOf("Delivery") }
    var deliveryChargeText by remember { mutableStateOf("200") }

    val extraChargesTotal = remember(ladaanChargeText, utaarChargeText, deliveryChargeText) {
        val l = ladaanChargeText.toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
        val u = utaarChargeText.toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
        val d = deliveryChargeText.toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
        l.add(u).add(d)
    }
    val initialExtraCharges = remember { BigDecimal("300") }

    val originalItemsSubtotal = remember(siblingItems) {
        siblingItems.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.totalSalePrice) }
    }
    val originalPaidTotal = remember(siblingItems) {
        siblingItems.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.paidAmount) }
    }

    val newItemsSubtotal = editableLines.fold(BigDecimal.ZERO) { acc, line ->
        val rate = line.unitRateText.toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: line.originalUnitRate
        acc.add(rate.multiply(line.currentUnits).setScale(0, RoundingMode.HALF_UP))
    }

    val wasGrandTotal = originalItemsSubtotal.add(initialExtraCharges)
    val newGrandTotal = newItemsSubtotal.add(extraChargesTotal)
    val deltaTotal = newGrandTotal.subtract(wasGrandTotal)
    val newUdhariDue = newGrandTotal.subtract(originalPaidTotal).max(BigDecimal.ZERO)

    val reasonChips = listOf("Quantity changed", "Rate changed", "Wrong item", "Other")
    var selectedReasonChip by remember { mutableStateOf("Quantity changed") }
    var customReasonNote by remember { mutableStateOf("Customer asked for 1 kg extra") }

    val savedToast = tr("Bill updated & saved in audit log", "बिल अपडेट किया गया")

    if (showAddProductDialog) {
        AlertDialog(
            onDismissRequest = { showAddProductDialog = false },
            containerColor = Color.White,
            title = {
                Text(
                    text = tr("Add item to bill", "बिल में सामान जोड़ें"),
                    fontWeight = FontWeight.Bold,
                    color = BNavyText
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    products.forEach { prod ->
                        Surface(
                            onClick = {
                                val isBag = !prod.isLooseOnly && prod.kgPerBag > BigDecimal.ONE
                                val unitRate = if (isBag) prod.bagSellingPrice else prod.effectiveLooseRatePerKg
                                editableLines = editableLines + EditableBillLineState(
                                    saleId = null,
                                    product = prod,
                                    isBagUnit = isBag,
                                    originalUnits = BigDecimal.ZERO,
                                    currentUnits = BigDecimal.ONE,
                                    originalUnitRate = unitRate,
                                    unitRateText = unitRate.stripTrailingZeros().toPlainString(),
                                    originalLineTotal = BigDecimal.ZERO
                                )
                                showAddProductDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = BPageBg,
                            border = BorderStroke(1.dp, BCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(prod.feedName, fontWeight = FontWeight.Bold, color = BNavyText)
                                Text(
                                    text = if (!prod.isLooseOnly && prod.kgPerBag > BigDecimal.ONE) {
                                        "${CurrencyUtils.formatInr(prod.bagSellingPrice)}/bag"
                                    } else {
                                        "${CurrencyUtils.formatInr(prod.effectiveLooseRatePerKg)}/kg"
                                    },
                                    fontSize = 13.sp,
                                    color = BBrandGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddProductDialog = false }) {
                    Text(tr("Cancel", "रद्द करें"), color = BSubtleText)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BPageBg)
            .navigationBarsPadding()
    ) {
        // Header
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
                    .testTag("edit_bill_back_btn"),
                shape = RoundedCornerShape(13.dp),
                color = Color.White,
                border = BorderStroke(1.dp, BCardBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = BNavyText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column {
                Text(
                    text = "Edit bill #$shortBillNo",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BNavyText
                )
                Text(
                    text = "$customerName · ${dateTimeFmt.format(Date(primarySale.date))}",
                    fontSize = 13.sp,
                    color = BSubtleText
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
            // Blue Audit Notice Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = BInfoBlueBg,
                border = BorderStroke(1.dp, BInfoBlueBorder)
            ) {
                Text(
                    text = tr(
                        "Every edit is saved in the audit log with your name and reason.",
                        "हर बदलाव आपके नाम और कारण के साथ ऑडिट लॉग में सेव होता है।"
                    ),
                    fontSize = 13.5.sp,
                    color = BInfoBlueText,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            Text(
                text = tr("ITEMS", "सामान"),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = BSubtleText,
                letterSpacing = 0.6.sp
            )

            editableLines.forEachIndexed { idx, line ->
                val currentRate = line.unitRateText.toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: line.originalUnitRate
                val lineTotal = currentRate.multiply(line.currentUnits).setScale(0, RoundingMode.HALF_UP)
                val isModified = line.saleId == null ||
                    line.currentUnits.compareTo(line.originalUnits) != 0 ||
                    currentRate.compareTo(line.originalUnitRate) != 0

                val titleStr = if (!line.product.isLooseOnly && line.product.kgPerBag > BigDecimal.ONE) {
                    if (line.product.feedName.contains("kg", ignoreCase = true)) line.product.feedName
                    else "${line.product.feedName} (${line.product.kgPerBag.stripTrailingZeros().toPlainString()} kg)"
                } else {
                    line.product.feedName
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = if (isModified) BEditedCardBg else Color.White,
                    border = BorderStroke(
                        width = if (isModified) 1.5.dp else 1.dp,
                        color = if (isModified) BEditedCardBorder else BCardBorder
                    )
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
                                text = titleStr,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = BNavyText,
                                modifier = Modifier.weight(1f)
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isModified) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = BSoftAmber
                                    ) {
                                        Text(
                                            text = "Edited",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BAmberText,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = CurrencyUtils.formatInr(lineTotal),
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BNavyText
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Quantity stepper (- 2 bags +)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    onClick = {
                                        val next = line.currentUnits.subtract(BigDecimal.ONE).max(BigDecimal.ONE)
                                        editableLines = editableLines.toMutableList().also {
                                            it[idx] = line.copy(currentUnits = next)
                                        }
                                    },
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape,
                                    color = BChipIdleBg
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Decrease",
                                            tint = BNavyText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                val unitDisplay = if (line.isBagUnit) {
                                    val b = line.currentUnits.toInt()
                                    "$b ${if (b == 1) "bag" else "bags"}"
                                } else {
                                    "${line.currentUnits.stripTrailingZeros().toPlainString()} kg"
                                }
                                Text(
                                    text = unitDisplay,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BNavyText
                                )

                                Surface(
                                    onClick = {
                                        val next = line.currentUnits.add(BigDecimal.ONE)
                                        editableLines = editableLines.toMutableList().also {
                                            it[idx] = line.copy(currentUnits = next)
                                        }
                                    },
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape,
                                    color = BBrandGreen
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Increase",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // Rate box (₹ 1,350)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, BCardBorder),
                                modifier = Modifier.width(125.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("₹", fontSize = 14.sp, color = BSubtleText, fontWeight = FontWeight.Bold)
                                    BasicTextField(
                                        value = line.unitRateText,
                                        onValueChange = { nextRate ->
                                            if (nextRate.isEmpty() || nextRate.matches(Regex("^\\d*\\.?\\d*$"))) {
                                                editableLines = editableLines.toMutableList().also {
                                                    it[idx] = line.copy(unitRateText = nextRate)
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        textStyle = TextStyle(
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BNavyText
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        if (isModified && line.saleId != null) {
                            val origUnitStr = if (line.isBagUnit) {
                                val b = line.originalUnits.toInt()
                                "$b ${if (b == 1) "bag" else "bags"}"
                            } else {
                                "${line.originalUnits.stripTrailingZeros().toPlainString()} kg"
                            }
                            Text(
                                text = "Was $origUnitStr · ${CurrencyUtils.formatInr(line.originalLineTotal)}",
                                fontSize = 12.5.sp,
                                color = BSubtleText
                            )
                        }
                    }
                }
            }

            // Dashed "+ Add item" button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .drawBehind {
                        drawRoundRect(
                            color = BBrandGreen,
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                            ),
                            cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                        )
                    }
                    .clickable { showAddProductDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+ Add item",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = BBrandGreen
                )
            }

            // EXTRA CHARGES (OPTIONAL)
            Text(
                text = tr("EXTRA CHARGES (OPTIONAL)", "अतिरिक्त शुल्क (वैकल्पिक)"),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = BSubtleText,
                letterSpacing = 0.6.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, BCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("₹", fontSize = 14.sp, color = BSubtleText)
                            BasicTextField(
                                value = ladaanChargeText,
                                onValueChange = { if (it.isEmpty() || it.all(Char::isDigit)) ladaanChargeText = it },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BNavyText),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Text("Labour · Ladaan", fontSize = 12.sp, color = BSubtleText)
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, BCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("₹", fontSize = 14.sp, color = BSubtleText)
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (utaarChargeText.isEmpty()) {
                                    Text("Optional", fontSize = 14.5.sp, color = BSubtleText)
                                }
                                BasicTextField(
                                    value = utaarChargeText,
                                    onValueChange = { if (it.isEmpty() || it.all(Char::isDigit)) utaarChargeText = it },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BNavyText),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    Text("Labour · Utaar", fontSize = 12.sp, color = BSubtleText)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, BCardBorder),
                    modifier = Modifier.weight(1.2f)
                ) {
                    BasicTextField(
                        value = deliveryLabelText,
                        onValueChange = { deliveryLabelText = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BNavyText),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, BCardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("₹", fontSize = 14.sp, color = BSubtleText)
                        BasicTextField(
                            value = deliveryChargeText,
                            onValueChange = { if (it.isEmpty() || it.all(Char::isDigit)) deliveryChargeText = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BNavyText),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Summary Comparison Card (Was / New total / Paid / Udhari due)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                border = BorderStroke(1.dp, BCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Was", fontSize = 14.sp, color = BSubtleText)
                        Text(CurrencyUtils.formatInr(wasGrandTotal), fontSize = 14.sp, color = BSubtleText)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "New total",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = BNavyText
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = CurrencyUtils.formatInr(newGrandTotal),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = BNavyText
                            )
                            if (deltaTotal.compareTo(BigDecimal.ZERO) != 0) {
                                val sign = if (deltaTotal > BigDecimal.ZERO) "+" else "−"
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (deltaTotal > BigDecimal.ZERO) BSoftAmber else BSoftGreen
                                ) {
                                    Text(
                                        text = "$sign${CurrencyUtils.formatInr(deltaTotal.abs())}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (deltaTotal > BigDecimal.ZERO) BAmberText else BBrandGreen,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Paid · Cash", fontSize = 14.sp, color = BSubtleText)
                        Text(
                            text = CurrencyUtils.formatInr(originalPaidTotal),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = BBrandGreen
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Udhari due", fontSize = 14.sp, color = BSubtleText)
                        Text(
                            text = CurrencyUtils.formatInr(newUdhariDue),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = BCrimsonRed
                        )
                    }
                }
            }

            // REASON FOR EDIT *
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = BSubtleText, fontWeight = FontWeight.Bold)) {
                        append("REASON FOR EDIT ")
                    }
                    withStyle(SpanStyle(color = BCrimsonRed, fontWeight = FontWeight.Bold)) {
                        append("*")
                    }
                },
                fontSize = 11.5.sp,
                letterSpacing = 0.6.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                reasonChips.forEach { chip ->
                    val selected = selectedReasonChip == chip
                    Surface(
                        onClick = { selectedReasonChip = chip },
                        shape = RoundedCornerShape(50),
                        color = if (selected) BBrandGreen else BChipIdleBg
                    ) {
                        Text(
                            text = chip,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else BNavyText,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, BCardBorder)
            ) {
                BasicTextField(
                    value = customReasonNote,
                    onValueChange = { customReasonNote = it },
                    textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = BNavyText),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Bottom Bar: Cancel + Save changes
        Surface(
            color = Color.White,
            border = BorderStroke(1.dp, BCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BCardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BNavyText),
                    modifier = Modifier
                        .weight(0.38f)
                        .height(52.dp)
                ) {
                    Text(tr("Cancel", "रद्द करें"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Button(
                    onClick = {
                        val actor = currentUser ?: User(id = "admin", name = "Admin", role = Role.ADMINISTRATOR, pinHash = "")
                        val finalReason = customReasonNote.trim().ifBlank { selectedReasonChip }
                        scope.launch {
                            editableLines.forEach { line ->
                                val unitRate = line.unitRateText.toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: line.originalUnitRate
                                val newQtyKg = if (line.isBagUnit) {
                                    line.currentUnits.multiply(line.product.kgPerBag)
                                } else {
                                    line.currentUnits
                                }
                                val newPricePerKg = if (line.isBagUnit && line.product.kgPerBag > BigDecimal.ZERO) {
                                    unitRate.divide(line.product.kgPerBag, 4, RoundingMode.HALF_UP)
                                } else {
                                    unitRate
                                }

                                if (line.saleId != null) {
                                    val existingSale = siblingItems.find { it.id == line.saleId }
                                    if (existingSale != null &&
                                        (existingSale.quantityKg.compareTo(newQtyKg) != 0 ||
                                            existingSale.pricePerKgAtSale.compareTo(newPricePerKg) != 0)
                                    ) {
                                        repository.editSaleBill(
                                            saleId = existingSale.id,
                                            newQuantityKg = newQtyKg,
                                            newPricePerKg = newPricePerKg,
                                            newPaidAmount = existingSale.paidAmount,
                                            reason = finalReason,
                                            performedBy = actor
                                        )
                                    }
                                } else {
                                    repository.addLineItemToExistingBill(
                                        anchorSale = primarySale,
                                        productId = line.product.id,
                                        quantityKg = newQtyKg,
                                        pricePerKg = newPricePerKg,
                                        reason = finalReason,
                                        performedBy = actor
                                    )
                                }
                            }
                            Toast.makeText(context, savedToast, Toast.LENGTH_SHORT).show()
                            onSaved()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BBrandGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(0.62f)
                        .height(52.dp)
                        .testTag("save_bill_changes_btn")
                ) {
                    Text(tr("Save changes", "बदलाव सेव करें"), fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
                }
            }
        }
    }
}

// ============================================================================
// PAGE 5: FULL-SCREEN AUDIT LOG ("Audit log · Who changed what")
// ============================================================================

@Composable
fun AuditLogFullScreen(
    auditLogs: List<BillAuditLog>,
    allSales: List<SaleEntry>,
    allUsers: List<User>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    BackHandler { onBack() }

    var selectedTypeFilter by remember { mutableStateOf("All") } // All, Edits, Returns, Deletes, Rates
    var selectedStaffName by remember { mutableStateOf("All staff") }
    var staffMenuExpanded by remember { mutableStateOf(false) }

    val periodOptions = listOf("Today", "This week", "This month", "All time")
    var selectedPeriod by remember { mutableStateOf("This month") }
    var periodMenuExpanded by remember { mutableStateOf(false) }
    var maxItemsShown by remember { mutableIntStateOf(20) }

    val billNoMap = remember(allSales) { buildConsolidatedBillNoMap(allSales) }
    val userRoleMap = remember(allUsers) {
        allUsers.associate { it.name.lowercase(Locale.ROOT) to it.role }
    }
    val staffNames = remember(auditLogs, allUsers) {
        val names = (allUsers.map { it.name } + auditLogs.map { it.performedByUserName })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        listOf("All staff") + names
    }

    val filteredLogs = remember(auditLogs, selectedTypeFilter, selectedStaffName, selectedPeriod) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val cutoffMillis = when (selectedPeriod) {
            "Today" -> cal.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.timeInMillis
            "This week" -> now - 7L * 24L * 60L * 60L * 1000L
            "This month" -> now - 31L * 24L * 60L * 60L * 1000L
            else -> 0L
        }

        auditLogs.filter { log ->
            val matchesType = when (selectedTypeFilter) {
                "Edits" -> log.actionType == "EDIT" || log.actionType == "CUSTOMER_EDIT"
                "Returns" -> log.actionType == "RETURN"
                "Deletes" -> log.actionType == "CANCEL" || log.actionType == "DELETE"
                "Rates" -> log.actionType == "RATE"
                else -> true
            }
            val matchesStaff = selectedStaffName == "All staff" ||
                log.performedByUserName.equals(selectedStaffName, ignoreCase = true)
            val matchesTime = log.timestamp >= cutoffMillis
            matchesType && matchesStaff && matchesTime
        }.sortedByDescending { it.timestamp }
    }

    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dayTimeFmt = remember { SimpleDateFormat("d MMM · h:mm a", Locale.getDefault()) }
    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BPageBg)
            .navigationBarsPadding()
    ) {
        // Header
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
                    .testTag("audit_log_back_btn"),
                shape = RoundedCornerShape(13.dp),
                color = Color.White,
                border = BorderStroke(1.dp, BCardBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = BNavyText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tr("Audit log", "ऑडिट लॉग"),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = BNavyText
                )
                Text(
                    text = tr("Who changed what", "किसने क्या बदला"),
                    fontSize = 13.sp,
                    color = BSubtleText
                )
            }

            Surface(
                onClick = {
                    val csv = buildString {
                        append("Timestamp,Action,Staff,Customer,Item,Before,After,Reason\n")
                        filteredLogs.forEach { l ->
                            append("${Date(l.timestamp)},${l.actionType},${l.performedByUserName},${l.customerName},${l.productName},\"${l.previousSummary}\",\"${l.newSummary}\",\"${l.reason}\"\n")
                        }
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, csv)
                    }
                    context.startActivity(Intent.createChooser(intent, "Export Audit Log"))
                },
                modifier = Modifier
                    .size(44.dp)
                    .testTag("audit_log_export_btn"),
                shape = RoundedCornerShape(13.dp),
                color = Color.White,
                border = BorderStroke(1.dp, BCardBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Export audit log",
                        tint = BNavyText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Row 1 Filter Chips: All | Edits | Returns | Deletes | Rates
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Edits", "Returns", "Deletes", "Rates").forEach { chip ->
                val selected = selectedTypeFilter == chip
                Surface(
                    onClick = { selectedTypeFilter = chip },
                    shape = RoundedCornerShape(50),
                    color = if (selected) BBrandGreen else BChipIdleBg
                ) {
                    Text(
                        text = chip,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else BNavyText,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Row 2 Dropdown Pills: All staff ▾ | This month ▾
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                Surface(
                    onClick = { staffMenuExpanded = true },
                    shape = RoundedCornerShape(50),
                    color = BChipIdleBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = selectedStaffName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BNavyText
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = BNavyText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                DropdownMenu(
                    expanded = staffMenuExpanded,
                    onDismissRequest = { staffMenuExpanded = false }
                ) {
                    staffNames.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                selectedStaffName = name
                                staffMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Box {
                Surface(
                    onClick = { periodMenuExpanded = true },
                    shape = RoundedCornerShape(50),
                    color = BChipIdleBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = selectedPeriod,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BNavyText
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = BNavyText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                DropdownMenu(
                    expanded = periodMenuExpanded,
                    onDismissRequest = { periodMenuExpanded = false }
                ) {
                    periodOptions.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p) },
                            onClick = {
                                selectedPeriod = p
                                periodMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tr("No audit events recorded for this filter.", "इस फ़िल्टर के लिए कोई ऑडिट लॉग नहीं मिला।"),
                    fontSize = 14.5.sp,
                    color = BSubtleText
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredLogs.take(maxItemsShown), key = { it.id }) { log ->
                    val billNo = billNoMap[log.saleId] ?: log.saleId.filter { it.isDigit() }.takeLast(4).padStart(4, '0').ifBlank { "1042" }
                    val timeLabel = if (log.timestamp >= todayStart) {
                        "Today · ${timeFmt.format(Date(log.timestamp))}"
                    } else {
                        dayTimeFmt.format(Date(log.timestamp))
                    }

                    val (iconBg, iconTint, iconVec, cardTitle) = when (log.actionType) {
                        "RETURN" -> Quad(BSoftAmber, BAmberText, Icons.AutoMirrored.Filled.Undo, "Return on bill #$billNo")
                        "CANCEL", "DELETE" -> Quad(BSoftRed, BCrimsonRed, Icons.Default.DeleteOutline, "Deleted bill #$billNo")
                        "RATE" -> Quad(BSoftAmber, BAmberText, Icons.Outlined.LocalOffer, "Rate changed")
                        "CUSTOMER_EDIT" -> Quad(BInfoBlueBg, Color(0xFF0284C7), Icons.Outlined.Edit, "Edited customer")
                        else -> Quad(BInfoBlueBg, Color(0xFF0284C7), Icons.Outlined.Edit, "Edited bill #$billNo")
                    }

                    val detectedRole = userRoleMap[log.performedByUserName.lowercase(Locale.ROOT)]
                    val (roleLabel, roleBg, roleFg) = when (detectedRole) {
                        Role.ADMINISTRATOR -> Triple("Admin", BNavyText, Color.White)
                        Role.MANAGER -> Triple("Manager", BInfoBlueBg, BInfoBlueText)
                        Role.STAFF -> Triple("Cashier", Color(0xFFF1F5F9), BSubtleText)
                        null -> if (log.performedByUserName.contains("manager", ignoreCase = true) || log.performedByUserName.contains("rohit", ignoreCase = true)) {
                            Triple("Manager", BInfoBlueBg, BInfoBlueText)
                        } else {
                            Triple("Admin", BNavyText, Color.White)
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, BCardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(iconBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = iconVec,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = cardTitle,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BNavyText
                                    )
                                    Text(
                                        text = timeLabel,
                                        fontSize = 12.sp,
                                        color = BSubtleText
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = log.performedByUserName.ifBlank { "Admin" },
                                        fontSize = 13.sp,
                                        color = BSubtleText,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = roleBg
                                    ) {
                                        Text(
                                            text = roleLabel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = roleFg,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                val detailLine = when (log.actionType) {
                                    "RATE" -> "${log.productName} ${log.previousSummary} → ${log.newSummary}"
                                    "CUSTOMER_EDIT" -> "Credit limit ${log.previousSummary} → ${log.newSummary}"
                                    "CANCEL", "DELETE" -> "${log.previousSummary} · ${log.customerName} · stock and cash reversed"
                                    "RETURN" -> "${log.productName} · ${log.newSummary}"
                                    else -> "${log.productName} ${log.previousSummary} → ${log.newSummary}"
                                }
                                Text(
                                    text = detailLine,
                                    fontSize = 13.5.sp,
                                    color = BNavyText,
                                    fontWeight = FontWeight.SemiBold
                                )

                                if (log.reason.isNotBlank() &&
                                    log.actionType != "RATE" &&
                                    log.actionType != "CUSTOMER_EDIT"
                                ) {
                                    Text(
                                        text = "“${log.reason}”",
                                        fontSize = 13.sp,
                                        fontStyle = FontStyle.Italic,
                                        color = BSubtleText
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { maxItemsShown += 20 },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, BBrandGreen),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BBrandGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(top = 4.dp)
                    ) {
                        Text(
                            text = tr("Load older entries", "पुराने लॉग लोड करें"),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

// ============================================================================
// PAGE 6: BILL SHARE SCREEN ("Bill #1042 · Share as link" — PDF | Image | Link)
// ============================================================================

@Composable
fun BillShareScreen(
    billData: BillData,
    shopName: String,
    shopUpiId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    BackHandler { onBack() }

    var selectedTab by remember { mutableIntStateOf(2) } // 0 = PDF, 1 = Image, 2 = Link (matches Page 6 default)
    val shortToken = remember(billData.billNumber, billData.date) {
        val hash = abs((billData.billNumber + billData.date.toString()).hashCode()).toString(36).take(6)
        hash.padStart(6, 'X')
    }
    var isLinkRevoked by remember { mutableStateOf(false) }
    var tokenSuffix by remember { mutableIntStateOf(0) }
    val activeLink = remember(shortToken, tokenSuffix) {
        val code = if (tokenSuffix == 0) "Xk9fQ2" else "${shortToken.take(4)}$tokenSuffix"
        "khatawise.app/b/$code"
    }

    val expiryOptions = listOf("24 hours", "7 days", "30 days", "Never")
    var selectedExpiry by remember { mutableStateOf("7 days") }

    var allowPdfDownload by remember { mutableStateOf(true) }
    var showPayByUpiButton by remember { mutableStateOf(true) }
    var showOutstandingBalance by remember { mutableStateOf(false) }

    val expiresSubtitle = remember(selectedExpiry) {
        val cal = Calendar.getInstance()
        when (selectedExpiry) {
            "24 hours" -> cal.add(Calendar.HOUR_OF_DAY, 24)
            "7 days" -> cal.add(Calendar.DAY_OF_YEAR, 7)
            "30 days" -> cal.add(Calendar.DAY_OF_YEAR, 30)
            else -> return@remember "Link never expires"
        }
        val fmt = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault())
        "Expires ${fmt.format(cal.time)}"
    }

    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = BBrandGreen,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = Color(0xFFD1D5DB),
        uncheckedBorderColor = Color.Transparent
    )

    val copiedToast = tr("Bill link copied to clipboard", "बिल लिंक कॉपी हो गया")
    val revokedToast = tr("Bill link revoked", "बिल लिंक निष्क्रिय किया गया")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BPageBg)
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("bill_share_back_btn"),
                shape = RoundedCornerShape(13.dp),
                color = Color.White,
                border = BorderStroke(1.dp, BCardBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = BNavyText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column {
                Text(
                    text = "Bill #${billData.billNumber}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BNavyText
                )
                Text(
                    text = when (selectedTab) {
                        0 -> "Share as PDF"
                        1 -> "Share as image"
                        else -> "Share as link"
                    },
                    fontSize = 13.sp,
                    color = BSubtleText
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
            // Segmented Toggle: PDF | Image | Link
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = BChipIdleBg
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(0 to "PDF", 1 to "Image", 2 to "Link").forEach { (idx, label) ->
                        val active = selectedTab == idx
                        Surface(
                            onClick = { selectedTab = idx },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) BBrandGreen else Color.Transparent
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) Color.White else BNavyText
                                )
                            }
                        }
                    }
                }
            }

            if (selectedTab == 2) {
                // BILL LINK Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, BCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "BILL LINK",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = BSubtleText,
                            letterSpacing = 0.6.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = BPageBg
                            ) {
                                Text(
                                    text = if (isLinkRevoked) "Link revoked" else activeLink,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isLinkRevoked) BCrimsonRed else BNavyText,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    if (isLinkRevoked) {
                                        isLinkRevoked = false
                                        tokenSuffix++
                                    } else {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        cm?.setPrimaryClip(ClipData.newPlainText("Bill Link", "https://$activeLink"))
                                        Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.5.dp, BBrandGreen),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = BBrandGreen),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text(
                                    text = if (isLinkRevoked) "New link" else "Copy",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Text(
                            text = "Anyone with this link can view this bill. No login needed.",
                            fontSize = 12.5.sp,
                            color = BSubtleText
                        )
                    }
                }

                // LINK EXPIRES
                Text(
                    text = "LINK EXPIRES",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = BSubtleText,
                    letterSpacing = 0.6.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    expiryOptions.forEach { opt ->
                        val active = selectedExpiry == opt
                        Surface(
                            onClick = { selectedExpiry = opt },
                            shape = RoundedCornerShape(50),
                            color = if (active) BBrandGreen else BChipIdleBg
                        ) {
                            Text(
                                text = opt,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) Color.White else BNavyText,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Text(
                    text = expiresSubtitle,
                    fontSize = 13.sp,
                    color = BSubtleText
                )
            }

            // Options Switches Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                border = BorderStroke(1.dp, BCardBorder)
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Allow PDF download",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = BNavyText
                            )
                            Text(
                                text = "Customer can save the bill",
                                fontSize = 12.5.sp,
                                color = BSubtleText
                            )
                        }
                        Switch(
                            checked = allowPdfDownload,
                            onCheckedChange = { allowPdfDownload = it },
                            colors = switchColors
                        )
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show Pay by UPI button",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = BNavyText
                            )
                            Text(
                                text = "Opens their UPI app for ${CurrencyUtils.formatInr(billData.udhariAmount)}",
                                fontSize = 12.5.sp,
                                color = BSubtleText
                            )
                        }
                        Switch(
                            checked = showPayByUpiButton,
                            onCheckedChange = { showPayByUpiButton = it },
                            colors = switchColors
                        )
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show outstanding balance",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = BNavyText
                            )
                            Text(
                                text = "Previous and total udhari",
                                fontSize = 12.5.sp,
                                color = BSubtleText
                            )
                        }
                        Switch(
                            checked = showOutstandingBalance,
                            onCheckedChange = { showOutstandingBalance = it },
                            colors = switchColors
                        )
                    }
                }
            }

            // WHAT THE CUSTOMER SEES Preview Card
            Text(
                text = "WHAT THE CUSTOMER SEES",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = BSubtleText,
                letterSpacing = 0.6.sp
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                border = BorderStroke(1.dp, BCardBorder)
            ) {
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
                        Text(
                            text = shopName.ifBlank { "Shop Name" },
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = BNavyText
                        )
                        Text(
                            text = "Bill #${billData.billNumber}",
                            fontSize = 13.5.sp,
                            color = BSubtleText
                        )
                    }

                    Text(
                        text = CurrencyUtils.formatInr(billData.subtotal),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = BNavyText
                    )

                    Text(
                        text = "Paid ${CurrencyUtils.formatInr(billData.paidAmount)} · Due ${CurrencyUtils.formatInr(billData.udhariAmount)}",
                        fontSize = 13.sp,
                        color = BSubtleText
                    )

                    if (showOutstandingBalance) {
                        Text(
                            text = "Total Udhari Balance: ${CurrencyUtils.formatInr(billData.newTotalUdhari)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BCrimsonRed
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (showPayByUpiButton) {
                            Button(
                                onClick = {},
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BBrandGreen,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Text(
                                    text = "Pay ${CurrencyUtils.formatInr(billData.udhariAmount)}",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (allowPdfDownload) {
                            OutlinedButton(
                                onClick = {
                                    shareBillAsPdfOrText(context, billData, shopName, shopUpiId, whatsappDirect = false)
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.5.dp, BBrandGreen),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = BBrandGreen),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Text(
                                    text = "Download PDF",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            if (selectedTab == 2) {
                // Viewed status & Revoke link card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, BCardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isLinkRevoked) "Link revoked" else "Viewed 2 times",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = BNavyText
                            )
                            Text(
                                text = if (isLinkRevoked) "Customers can no longer open this link" else "Last: Today, 6:10 PM",
                                fontSize = 12.5.sp,
                                color = BSubtleText
                            )
                        }

                        Text(
                            text = if (isLinkRevoked) "Restore link" else "Revoke link",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLinkRevoked) BBrandGreen else BCrimsonRed,
                            modifier = Modifier.clickable {
                                isLinkRevoked = !isLinkRevoked
                                if (isLinkRevoked) {
                                    Toast.makeText(context, revokedToast, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        // Sticky Bottom Bar: Send on WhatsApp + Share
        Surface(
            color = Color.White,
            border = BorderStroke(1.dp, BCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        when (selectedTab) {
                            0 -> shareBillAsPdfOrText(context, billData, shopName, shopUpiId, whatsappDirect = true)
                            1 -> BillGenerator.shareTextBill(context, billData)
                            else -> {
                                val msg = buildString {
                                    append("*${shopName.ifBlank { "KhataWise Store" }}* — Bill #${billData.billNumber}\n")
                                    append("Total: ${CurrencyUtils.formatInr(billData.subtotal)}\n")
                                    append("Paid: ${CurrencyUtils.formatInr(billData.paidAmount)} · Due: ${CurrencyUtils.formatInr(billData.udhariAmount)}\n")
                                    if (showOutstandingBalance) {
                                        append("Total Udhari: ${CurrencyUtils.formatInr(billData.newTotalUdhari)}\n")
                                    }
                                    append("View bill: https://$activeLink")
                                }
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, msg)
                                }
                                context.startActivity(Intent.createChooser(intent, "Send on WhatsApp"))
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BBrandGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(0.58f)
                        .height(52.dp)
                        .testTag("bill_share_whatsapp_btn")
                ) {
                    Text(
                        text = tr("Send on WhatsApp", "व्हाट्सएप पर भेजें"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = {
                        if (selectedTab == 0) {
                            shareBillAsPdfOrText(context, billData, shopName, shopUpiId, whatsappDirect = false)
                        } else {
                            BillGenerator.shareTextBill(context, billData)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, BBrandGreen),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BBrandGreen),
                    modifier = Modifier
                        .weight(0.42f)
                        .height(52.dp)
                        .testTag("bill_share_generic_btn")
                ) {
                    Text(
                        text = tr("Share", "शेयर"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun shareBillAsPdfOrText(
    context: Context,
    billData: BillData,
    shopName: String,
    shopUpiId: String,
    whatsappDirect: Boolean
) {
    try {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = android.graphics.Color.parseColor("#0E6F43")
        canvas.drawRect(0f, 0f, 595f, 12f, paint)

        paint.color = android.graphics.Color.parseColor("#0F172A")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        canvas.drawText(shopName.ifBlank { "KhataWise Store" }, 36f, 48f, paint)

        paint.textSize = 14f
        canvas.drawText("Bill #${billData.billNumber} · Customer: ${billData.customerName}", 36f, 76f, paint)

        var y = 115f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 12f
        billData.items.forEach { item ->
            canvas.drawText("${item.feedName} (${CurrencyUtils.formatWeightKg(item.totalKg)})", 36f, y, paint)
            canvas.drawText(CurrencyUtils.formatInr(item.itemTotal), 460f, y, paint)
            y += 24f
        }
        y += 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Total: ${CurrencyUtils.formatInr(billData.subtotal)}", 36f, y, paint)
        y += 22f
        canvas.drawText("Paid: ${CurrencyUtils.formatInr(billData.paidAmount)}   Due: ${CurrencyUtils.formatInr(billData.udhariAmount)}", 36f, y, paint)
        if (shopUpiId.isNotBlank()) {
            y += 24f
            canvas.drawText("Pay by UPI: $shopUpiId", 36f, y, paint)
        }

        doc.finishPage(page)
        val dir = File(context.cacheDir, "bills").apply { mkdirs() }
        val file = File(dir, "Bill_${billData.billNumber}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (whatsappDirect) {
                setPackage("com.whatsapp")
            }
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            intent.setPackage(null)
            context.startActivity(Intent.createChooser(intent, "Share Bill PDF"))
        }
    } catch (_: Exception) {
        BillGenerator.shareTextBill(context, billData)
    }
}
