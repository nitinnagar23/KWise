package com.example.ui.purchases

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PurchaseInvoice
import com.example.model.SupplierKhataSummary
import com.example.model.SupplierPayment
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatTextMuted
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import com.example.util.CurrencyUtils
import com.example.util.tr
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun PurchaseBillsTabView(
    invoices: List<PurchaseInvoice>,
    onInvoiceClick: (PurchaseInvoice) -> Unit,
    onAddPurchaseBillClick: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf("All") } // All, Unpaid, Paid
    var searchQuery by remember { mutableStateOf("") }
    val shortDateFormat = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val monthHeaderFormat = remember { SimpleDateFormat("MMMM", Locale.getDefault()) }

    val billNoMap = remember(invoices) {
        val asc = invoices.sortedBy { it.date }
        asc.mapIndexed { idx, inv ->
            inv.id to formatShortPurchaseBillNo(inv.invoiceNumber, idx + 1)
        }.toMap()
    }

    val filteredInvoices = remember(invoices, selectedFilter, searchQuery, billNoMap) {
        invoices.sortedByDescending { it.date }.filter { inv ->
            val shortNo = billNoMap[inv.id] ?: inv.invoiceNumber
            val itemsSummary = inv.getItems().joinToString(" ") { it.feedName }
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                val q = searchQuery.trim()
                inv.wholesalerName.contains(q, ignoreCase = true) ||
                    shortNo.contains(q, ignoreCase = true) ||
                    inv.invoiceNumber.contains(q, ignoreCase = true) ||
                    itemsSummary.contains(q, ignoreCase = true)
            }

            val matchesFilter = when (selectedFilter) {
                "Unpaid" -> inv.balanceAmount > BigDecimal.ZERO
                "Paid" -> inv.balanceAmount.compareTo(BigDecimal.ZERO) == 0
                else -> true
            }

            matchesSearch && matchesFilter
        }
    }

    val groupedByMonth = remember(filteredInvoices) {
        filteredInvoices.groupBy { inv ->
            monthHeaderFormat.format(Date(inv.date)).uppercase(Locale.getDefault())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FlatDarkBg)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Filter pills: All | Unpaid | Paid
            item {
                val filters = listOf(
                    "All" to tr("All", "सभी"),
                    "Unpaid" to tr("Unpaid", "बाकी"),
                    "Paid" to tr("Paid", "भुगतान")
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filters.forEach { (key, label) ->
                        val isSelected = selectedFilter == key
                        Surface(
                            onClick = { selectedFilter = key },
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) SupGreen else SupChipIdleBg,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 36.dp)
                                .testTag("purchase_bills_filter_${key.lowercase(Locale.ROOT)}")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSelected) Color.White else FlatTextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Search bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = tr("Search bill no., supplier, item...", "बिल नंबर, सप्लायर या आइटम खोजें..."),
                            color = FlatTextMuted,
                            fontSize = 14.5.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = FlatTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = FlatTextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
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
                        .testTag("purchase_bills_search_input")
                )
            }

            if (filteredInvoices.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr("No purchase bills found.", "कोई खरीद बिल नहीं मिला।"),
                            fontSize = 14.5.sp,
                            color = FlatTextMuted
                        )
                    }
                }
            } else {
                groupedByMonth.forEach { (monthHeader, monthInvoices) ->
                    item(key = "month_$monthHeader") {
                        Text(
                            text = monthHeader,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp, start = 2.dp)
                        )
                    }

                    items(monthInvoices, key = { it.id }) { invoice ->
                        val shortNo = billNoMap[invoice.id] ?: formatShortPurchaseBillNo(invoice.invoiceNumber, 1)
                        PurchaseBillRowCard(
                            invoice = invoice,
                            shortNo = shortNo,
                            shortDate = shortDateFormat.format(Date(invoice.date)),
                            showDueInPartPaid = true,
                            onClick = { onInvoiceClick(invoice) }
                        )
                    }
                }
            }
        }

        // Bottom-right + Purchase bill FAB matching Suppliers · Purchase bills.pdf
        FloatingActionButton(
            onClick = onAddPurchaseBillClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_purchase_fab"),
            containerColor = SupGreen,
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    text = tr("Purchase bill", "खरीद बिल"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
internal fun PurchaseBillRowCard(
    invoice: PurchaseInvoice,
    shortNo: String,
    shortDate: String,
    showDueInPartPaid: Boolean,
    onClick: () -> Unit
) {
    val itemsDesc = remember(invoice) {
        val list = invoice.getItems()
        if (list.isEmpty()) {
            "Cattle Feed"
        } else {
            list.joinToString(", ") { item ->
                if (item.bags > 0) "${item.feedName} × ${item.bags} bags"
                else "${item.feedName} × ${CurrencyUtils.formatWeight(item.totalKg)} kg"
            }
        }
    }
    val extraCharges = remember(invoice) {
        invoice.ladhan.add(invoice.utaar).add(invoice.gaadiKiraya)
    }
    val subtitleText = buildString {
        append("#$shortNo · $shortDate · $itemsDesc")
        if (extraCharges > BigDecimal.ZERO && showDueInPartPaid) {
            append(" · charges ${CurrencyUtils.formatInr(extraCharges)}")
        }
    }

    val isPaid = invoice.balanceAmount.compareTo(BigDecimal.ZERO) == 0
    val isPartPaid = !isPaid && invoice.paidAmount > BigDecimal.ZERO
    val badgeText = when {
        isPaid -> tr("Paid", "भुगतान")
        isPartPaid && showDueInPartPaid -> "Part paid · due ${CurrencyUtils.formatInr(invoice.balanceAmount)}"
        isPartPaid -> tr("Part paid", "आंशिक भुगतान")
        else -> tr("Unpaid", "बाकी")
    }
    val badgeBg = if (isPaid) SupGreenSoft else SupRedSoft
    val badgeColor = if (isPaid) SupGreen else SupRed

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("purchase_invoice_card_${invoice.id}"),
        shape = RoundedCornerShape(16.dp),
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = invoice.wholesalerName,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FlatTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitleText,
                    fontSize = 13.sp,
                    color = FlatTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = CurrencyUtils.formatInr(invoice.totalInvoiceCost),
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FlatTextPrimary
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeBg
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SupplierPaymentsTabView(
    payments: List<SupplierPayment>,
    invoices: List<PurchaseInvoice>,
    supplierSummaries: List<SupplierKhataSummary>,
    onPaymentClick: (SupplierPayment) -> Unit,
    onAddPaymentClick: () -> Unit
) {
    var selectedModeFilter by remember { mutableStateOf("All") } // All, Cash, UPI
    val shortDateFormat = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val monthFormat = remember { SimpleDateFormat("MMMM", Locale.getDefault()) }

    val thirtyDaysAgo = remember {
        System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L
    }

    // Paid in last 30 days (including standalone supplier payments + up-front payments on invoices)
    val paid30DaysTotal = remember(payments, invoices, thirtyDaysAgo) {
        val paySum = payments.filter { it.date >= thirtyDaysAgo }
            .fold(BigDecimal.ZERO) { acc, p -> acc.add(p.amount) }
        val invPaidSum = invoices.filter { it.date >= thirtyDaysAgo }
            .fold(BigDecimal.ZERO) { acc, inv -> acc.add(inv.paidAmount) }
        paySum.add(invPaidSum)
    }
    val payments30DaysCount = remember(payments, invoices, thirtyDaysAgo) {
        val payCnt = payments.count { it.date >= thirtyDaysAgo }
        val invPaidCnt = invoices.count { it.date >= thirtyDaysAgo && it.paidAmount > BigDecimal.ZERO }
        (payCnt + invPaidCnt).coerceAtLeast(payments.size)
    }

    val totalStillToPay = remember(supplierSummaries) {
        supplierSummaries.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.balancePayable) }
    }
    val suppliersToPayCount = remember(supplierSummaries) {
        supplierSummaries.count { it.balancePayable > BigDecimal.ZERO }
    }

    val filteredPayments = remember(payments, selectedModeFilter) {
        payments.sortedByDescending { it.date }.filter { p ->
            when (selectedModeFilter) {
                "Cash" -> p.paymentMode.equals("CASH", ignoreCase = true)
                "UPI" -> p.paymentMode.equals("UPI", ignoreCase = true)
                else -> true
            }
        }
    }

    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val weekStart = remember { todayStart - 7L * 24L * 60L * 60L * 1000L }

    val groupedPayments = remember(filteredPayments, todayStart, weekStart) {
        filteredPayments.groupBy { p ->
            when {
                p.date >= todayStart -> "TODAY"
                p.date >= weekStart -> "THIS WEEK"
                else -> monthFormat.format(Date(p.date)).uppercase(Locale.getDefault())
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FlatDarkBg)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Unified 2-Column Top Summary Card: PAID · 30 DAYS | STILL TO PAY
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(vertical = 14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = tr("PAID · 30 DAYS", "भुगतान · 30 दिन"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.6.sp
                            )
                            Text(
                                text = CurrencyUtils.formatInr(paid30DaysTotal),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SupGreen
                            )
                            Text(
                                text = "$payments30DaysCount payments",
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(FlatDarkBorder)
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = tr("STILL TO PAY", "अभी देना बाकी"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.6.sp
                            )
                            Text(
                                text = CurrencyUtils.formatInr(totalStillToPay),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (totalStillToPay > BigDecimal.ZERO) SupRed else SupGreen
                            )
                            Text(
                                text = "$suppliersToPayCount suppliers",
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary
                            )
                        }
                    }
                }
            }

            // Filter chips: All | Cash | UPI
            item {
                val modeFilters = listOf("All", "Cash", "UPI")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    modeFilters.forEach { mode ->
                        val isSelected = selectedModeFilter == mode
                        Surface(
                            onClick = { selectedModeFilter = mode },
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) SupGreen else SupChipIdleBg,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 36.dp)
                                .testTag("supplier_payments_filter_${mode.lowercase(Locale.ROOT)}")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = mode,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSelected) Color.White else FlatTextPrimary
                                )
                            }
                        }
                    }
                }
            }

            if (filteredPayments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr("No supplier payments recorded yet.", "अभी तक कोई भुगतान दर्ज नहीं किया गया है।"),
                            fontSize = 14.5.sp,
                            color = FlatTextMuted
                        )
                    }
                }
            } else {
                groupedPayments.forEach { (groupHeader, groupList) ->
                    item(key = "pay_group_$groupHeader") {
                        Text(
                            text = groupHeader,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp, start = 2.dp)
                        )
                    }

                    items(groupList, key = { it.id }) { payment ->
                        val isUpi = payment.paymentMode.equals("UPI", ignoreCase = true)
                        val modeLabel = if (isUpi) "UPI" else "Cash"
                        val noteDesc = payment.note.ifBlank { "part payment" }
                        val dateStr = shortDateFormat.format(Date(payment.date))

                        Surface(
                            onClick = { onPaymentClick(payment) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("supplier_payment_card_${payment.id}"),
                            shape = RoundedCornerShape(16.dp),
                            color = FlatDarkCard,
                            border = BorderStroke(1.dp, FlatDarkBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
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
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(SupAvatarBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = supplierInitials(payment.wholesalerName),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = SupAvatarText
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            text = payment.wholesalerName,
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = FlatTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "$modeLabel · $dateStr · $noteDesc",
                                            fontSize = 13.sp,
                                            color = FlatTextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "−${CurrencyUtils.formatInr(payment.amount)}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SupGreen
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = if (isUpi) SupBlueSoft else SupGreenSoft
                                    ) {
                                        Text(
                                            text = modeLabel,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isUpi) SupBlue else SupGreen,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom-right + Payment FAB matching Suppliers · Payments.pdf
        FloatingActionButton(
            onClick = onAddPaymentClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_supplier_payment_fab"),
            containerColor = SupGreen,
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    text = tr("Payment", "भुगतान"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
