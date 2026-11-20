package com.example.ui.collections

import com.example.ui.theme.kw
import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.Customer
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
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal val ColBrandGreen: Color get() = kw(0xFF0E6F43)
internal val ColMintBg: Color get() = kw(0xFFDDF3E8)
internal val ColMintBorder: Color get() = kw(0xFFB7E4CE)
internal val ColCrimson: Color get() = kw(0xFFB91C1C)
internal val ColCrimsonSoftBg: Color get() = kw(0xFFFCE4E4)
internal val ColAmber: Color get() = kw(0xFFB45309)
internal val ColAmberText: Color get() = kw(0xFF8A4B08)
internal val ColAmberSoftBg: Color get() = kw(0xFFFCEBD2)
internal val ColBlue: Color get() = kw(0xFF1E3A8A)
internal val ColBlueBadgeText: Color get() = kw(0xFF1E40AF)
internal val ColBlueSoftBg: Color get() = kw(0xFFDCEBFA)
internal val ColBlueBannerBorder: Color get() = kw(0xFFB9D7F7)
internal val ColAvatarBg: Color get() = kw(0xFFE8EEF7)
internal val ColSoftGrayBtn: Color get() = kw(0xFFF3F3F8)
internal val ColNoDateBannerBg: Color get() = kw(0xFFF1F1F8)
internal val ColNoDatePillBg: Color get() = kw(0xFFECECF2)
internal val ColNoDatePillText: Color get() = kw(0xFF374151)
data class CustomerPromiseMeta(
    val promisedAmount: BigDecimal? = null,
    val note: String = "",
    val whatsappReminder: Boolean = true
)

object CollectionsPromisePrefs {
    private const val PREFS_NAME = "khatawise_collections_promise_prefs"

    fun load(context: Context, customerId: String): CustomerPromiseMeta {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val amtStr = prefs.getString("${customerId}_amount", null)
        val amt = amtStr?.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
        val note = prefs.getString("${customerId}_note", "") ?: ""
        val wa = prefs.getBoolean("${customerId}_wa", true)
        return CustomerPromiseMeta(
            promisedAmount = amt,
            note = note,
            whatsappReminder = wa
        )
    }

    fun save(context: Context, customerId: String, meta: CustomerPromiseMeta) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("${customerId}_amount", meta.promisedAmount?.toPlainString() ?: "")
            .putString("${customerId}_note", meta.note.trim())
            .putBoolean("${customerId}_wa", meta.whatsappReminder)
            .apply()
    }
}

internal fun computeCustomerInitials(name: String): String {
    val parts = name.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
        parts.size == 1 && parts[0].length >= 2 -> parts[0].take(2).uppercase()
        parts.size == 1 -> parts[0].uppercase()
        else -> "CU"
    }
}

internal enum class QuickPromiseOption(val labelEn: String, val labelHi: String, val daysOffset: Int?) {
    TODAY("Today", "आज", 0),
    TOMORROW("Tomorrow", "कल", 1),
    IN_3_DAYS("In 3 days", "3 दिन में", 3),
    IN_1_WEEK("In 1 week", "1 हफ़्ते में", 7),
    PICK_DATE("Pick date", "तारीख़ चुनें", null)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SetPromiseDateDialog(
    customer: Customer,
    existingMeta: CustomerPromiseMeta,
    startOfToday: Long,
    statusSubtitle: String,
    onDismiss: () -> Unit,
    onSavePromise: (
        promiseDateMillis: Long,
        promisedAmount: BigDecimal?,
        note: String,
        whatsappReminder: Boolean
    ) -> Unit
) {
    val dayMs = 24L * 60L * 60L * 1000L
    val defaultTomorrowMillis = startOfToday + dayMs + (12L * 60L * 60L * 1000L)

    var selectedDateMillis by remember {
        mutableLongStateOf(
            customer.promiseDate?.takeIf { it >= startOfToday } ?: defaultTomorrowMillis
        )
    }

    val selectedQuickOption = remember(selectedDateMillis, startOfToday) {
        val diffDays = ((selectedDateMillis - startOfToday) / dayMs).toInt()
        when (diffDays) {
            0 -> QuickPromiseOption.TODAY
            1 -> QuickPromiseOption.TOMORROW
            3 -> QuickPromiseOption.IN_3_DAYS
            7 -> QuickPromiseOption.IN_1_WEEK
            else -> QuickPromiseOption.PICK_DATE
        }
    }

    var promisedAmountInput by remember {
        mutableStateOf(
            existingMeta.promisedAmount?.setScale(0, RoundingMode.HALF_UP)?.toPlainString()
                ?: customer.totalUdhari.setScale(0, RoundingMode.HALF_UP).toPlainString()
        )
    }
    var noteInput by remember { mutableStateOf(existingMeta.note) }
    var whatsappReminder by remember { mutableStateOf(existingMeta.whatsappReminder) }
    var showCalendarPicker by remember { mutableStateOf(false) }

    val fullDateFormatter = remember {
        SimpleDateFormat("EEEE, d MMM yyyy", Locale.ENGLISH)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = FlatDarkCard,
            border = BorderStroke(1.dp, FlatDarkBorder),
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                // 1. Header: Title + Close X button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("Set promise date", "भुगतान वादा तारीख़"),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary
                    )
                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        color = ColSoftGrayBtn,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("close_set_promise_dialog_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = tr("Close", "बंद करें"),
                                tint = FlatTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 2. Customer Summary Box (OUTSTANDING is UNMASKED with NO eye icon per user instructions)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = kw(0xFFF8F8FC),
                        border = BorderStroke(1.dp, FlatDarkBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(ColAvatarBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = computeCustomerInitials(customer.name),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColBlue
                                    )
                                }
                                Column {
                                    Text(
                                        text = customer.name,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FlatTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sub = buildString {
                                        if (customer.village.isNotBlank()) {
                                            append(customer.village)
                                            append(" · ")
                                        }
                                        append(statusSubtitle)
                                    }
                                    Text(
                                        text = sub,
                                        fontSize = 12.5.sp,
                                        color = FlatTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = tr("OUTSTANDING", "बकाया"),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextSecondary,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = CurrencyUtils.formatInr(customer.totalUdhari),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColCrimson,
                                    modifier = Modifier.testTag("set_promise_outstanding_text")
                                )
                            }
                        }
                    }

                    // 3. PROMISED DATE Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = tr("PROMISED DATE", "वादा तारीख़"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.5.sp
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuickPromiseOption.entries.forEach { option ->
                                val isSelected = selectedQuickOption == option
                                Surface(
                                    onClick = {
                                        if (option.daysOffset != null) {
                                            selectedDateMillis = startOfToday +
                                                (option.daysOffset * dayMs) +
                                                (12L * 60L * 60L * 1000L)
                                        } else {
                                            showCalendarPicker = true
                                        }
                                    },
                                    shape = RoundedCornerShape(50),
                                    color = if (isSelected) ColBrandGreen else ColSoftGrayBtn,
                                    modifier = Modifier.testTag("promise_quick_${option.name.lowercase()}")
                                ) {
                                    Text(
                                        text = tr(option.labelEn, option.labelHi),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else FlatTextPrimary,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                                    )
                                }
                            }
                        }

                        // Selected Date Banner
                        Surface(
                            onClick = { showCalendarPicker = true },
                            shape = RoundedCornerShape(14.dp),
                            color = ColBlueSoftBg,
                            border = BorderStroke(1.dp, ColBlueBannerBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("selected_promise_date_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarToday,
                                    contentDescription = null,
                                    tint = kw(0xFF0F4C81),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = fullDateFormatter.format(Date(selectedDateMillis)),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = kw(0xFF0F4C81)
                                )
                            }
                        }
                    }

                    // 4. PROMISED AMOUNT (OPTIONAL)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = tr("PROMISED AMOUNT (OPTIONAL)", "वादा राशि (वैकल्पिक)"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        OutlinedTextField(
                            value = promisedAmountInput,
                            onValueChange = { promisedAmountInput = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                            leadingIcon = {
                                Text(
                                    text = "₹",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextSecondary
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = FlatDarkCard,
                                unfocusedContainerColor = FlatDarkCard,
                                focusedBorderColor = ColBrandGreen,
                                unfocusedBorderColor = FlatDarkBorder,
                                focusedTextColor = FlatTextPrimary,
                                unfocusedTextColor = FlatTextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("promise_amount_input")
                        )
                    }

                    // 5. NOTE (OPTIONAL)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = tr("NOTE (OPTIONAL)", "नोट (वैकल्पिक)"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        OutlinedTextField(
                            value = noteInput,
                            onValueChange = { noteInput = it },
                            placeholder = {
                                Text(
                                    text = tr("Will pay after crop sale", "फसल बिकने के बाद देंगे"),
                                    color = FlatTextMuted,
                                    fontSize = 14.5.sp
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = FlatDarkCard,
                                unfocusedContainerColor = FlatDarkCard,
                                focusedBorderColor = ColBrandGreen,
                                unfocusedBorderColor = FlatDarkBorder,
                                focusedTextColor = FlatTextPrimary,
                                unfocusedTextColor = FlatTextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("promise_note_input")
                        )
                    }

                    // 6. WhatsApp reminder Toggle Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr("WhatsApp reminder", "व्हाट्सएप रिमाइंडर"),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary
                                )
                                Text(
                                    text = tr("Sent 1 day before the date", "तारीख़ से 1 दिन पहले भेजा जाएगा"),
                                    fontSize = 12.5.sp,
                                    color = FlatTextSecondary
                                )
                            }
                            Switch(
                                checked = whatsappReminder,
                                onCheckedChange = { whatsappReminder = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = ColBrandGreen,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = kw(0xFFD1D5DB)
                                ),
                                modifier = Modifier.testTag("promise_whatsapp_switch")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 7. Bottom Action Buttons: Cancel | Save promise
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("cancel_promise_btn")
                    ) {
                        Text(
                            text = tr("Cancel", "रद्द करें"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = {
                            val parsedAmt = promisedAmountInput.replace(",", "").trim()
                                .toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
                            onSavePromise(
                                selectedDateMillis,
                                parsedAmt,
                                noteInput.trim(),
                                whatsappReminder
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColBrandGreen,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .heightIn(min = 48.dp)
                            .testTag("save_promise_btn")
                    ) {
                        Text(
                            text = tr("Save promise", "वादा सेव करें"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    if (showCalendarPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showCalendarPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { picked ->
                            selectedDateMillis = picked
                        }
                        showCalendarPicker = false
                    }
                ) {
                    Text(tr("Select", "चुनें"), color = ColBrandGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCalendarPicker = false }) {
                    Text(tr("Cancel", "रद्द करें"), color = FlatTextSecondary)
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
fun CollectPaymentDialog(
    customer: Customer,
    onDismiss: () -> Unit,
    onConfirmCollect: (amount: BigDecimal, note: String, customDateMillis: Long?) -> Unit
) {
    UnifiedRecordPaymentDialog(
        customers = listOf(customer),
        initialCustomer = customer,
        allowCustomerChange = false,
        onDismiss = onDismiss,
        onConfirmPayment = { _, amount, note, customDateMillis ->
            onConfirmCollect(amount, note, customDateMillis)
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnifiedRecordPaymentDialog(
    customers: List<Customer>,
    initialCustomer: Customer? = null,
    allowCustomerChange: Boolean = true,
    onDismiss: () -> Unit,
    onConfirmPayment: (customer: Customer, amount: BigDecimal, note: String, customDateMillis: Long?) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val udhariCustomers = remember(customers) {
        val withDue = customers.filter { !it.isDeleted && it.totalUdhari > BigDecimal.ZERO }
            .sortedByDescending { it.totalUdhari }
        if (withDue.isNotEmpty()) withDue else customers.filter { !it.isDeleted }
    }
    var selectedCustomer by remember(initialCustomer, udhariCustomers) {
        mutableStateOf(initialCustomer ?: udhariCustomers.firstOrNull())
    }
    var showCustomerPicker by remember { mutableStateOf(false) }
    var customerSearch by remember { mutableStateOf("") }
    var amountInput by remember(selectedCustomer?.id) {
        val due = selectedCustomer?.totalUdhari ?: BigDecimal.ZERO
        mutableStateOf(
            if (due > BigDecimal.ZERO) due.setScale(0, RoundingMode.HALF_UP).toPlainString() else ""
        )
    }
    var payMode by remember { mutableStateOf("CASH") } // CASH or UPI
    var noteInput by remember { mutableStateOf("") }
    var customDateMillis by remember { mutableStateOf<Long?>(null) }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val activeTimestamp = customDateMillis ?: System.currentTimeMillis()
    val parsedAmount = amountInput.trim().toBigDecimalOrNull() ?: BigDecimal.ZERO

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = FlatDarkCard,
            border = BorderStroke(1.dp, FlatDarkBorder),
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr("Collect payment", "भुगतान प्राप्त करें"),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                        selectedCustomer?.let { cust ->
                            Text(
                                text = "${cust.name} · ${tr("Due", "बकाया")} ${CurrencyUtils.formatInr(cust.totalUdhari)}",
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        color = ColSoftGrayBtn,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = tr("Close", "बंद करें"),
                                tint = FlatTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (allowCustomerChange) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tr("CUSTOMER", "ग्राहक"),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextSecondary
                                )
                                Text(
                                    text = if (showCustomerPicker) tr("Hide", "छुपाएं") else tr("Change", "बदलें"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColBrandGreen,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showCustomerPicker = !showCustomerPicker }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            if (showCustomerPicker || selectedCustomer == null) {
                                OutlinedTextField(
                                    value = customerSearch,
                                    onValueChange = { customerSearch = it },
                                    placeholder = { Text(tr("Search customer…", "ग्राहक खोजें…"), fontSize = 13.5.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = FlatDarkBg,
                                        unfocusedContainerColor = FlatDarkBg,
                                        focusedBorderColor = ColBrandGreen,
                                        unfocusedBorderColor = FlatDarkBorder,
                                        focusedTextColor = FlatTextPrimary,
                                        unfocusedTextColor = FlatTextPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                val filtered = remember(udhariCustomers, customerSearch) {
                                    if (customerSearch.isBlank()) udhariCustomers.take(8)
                                    else udhariCustomers.filter {
                                        it.name.contains(customerSearch.trim(), ignoreCase = true) ||
                                            it.village.contains(customerSearch.trim(), ignoreCase = true)
                                    }.take(8)
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    filtered.forEach { c ->
                                        val isPicked = selectedCustomer?.id == c.id
                                        Surface(
                                            onClick = {
                                                selectedCustomer = c
                                                showCustomerPicker = false
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isPicked) ColBrandGreen else FlatDarkBg,
                                            border = BorderStroke(1.dp, if (isPicked) ColBrandGreen else FlatDarkBorder)
                                        ) {
                                            Text(
                                                text = if (c.totalUdhari > BigDecimal.ZERO) {
                                                    "${c.name} · ${CurrencyUtils.formatInr(c.totalUdhari)}"
                                                } else c.name,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isPicked) Color.White else FlatTextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                val cust = selectedCustomer!!
                                Surface(
                                    onClick = { showCustomerPicker = true },
                                    shape = RoundedCornerShape(12.dp),
                                    color = FlatDarkBg,
                                    border = BorderStroke(1.dp, FlatDarkBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (cust.village.isNotBlank()) "${cust.name} · ${cust.village}" else cust.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FlatTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = CurrencyUtils.formatInr(cust.totalUdhari),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (cust.totalUdhari > BigDecimal.ZERO) kw(0xFFB91C1C) else ColBrandGreen
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Amount + Quick chips
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = tr("AMOUNT RECEIVED (₹)", "प्राप्त राशि (₹)"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary
                        )
                        OutlinedTextField(
                            value = amountInput,
                            onValueChange = { amountInput = it.filter { c -> c.isDigit() || c == '.' } },
                            leadingIcon = {
                                Text("₹", fontWeight = FontWeight.Bold, color = FlatTextSecondary)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = FlatDarkBg,
                                unfocusedContainerColor = FlatDarkBg,
                                focusedBorderColor = ColBrandGreen,
                                unfocusedBorderColor = FlatDarkBorder,
                                focusedTextColor = FlatTextPrimary,
                                unfocusedTextColor = FlatTextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("collect_payment_amount_input")
                        )

                        val due = selectedCustomer?.totalUdhari ?: BigDecimal.ZERO
                        if (due > BigDecimal.ZERO) {
                            val halfDue = due.divide(BigDecimal("2"), 0, RoundingMode.HALF_UP)
                            val presets = listOf(
                                tr("Full", "पूरा") to due.setScale(0, RoundingMode.HALF_UP),
                                tr("Half", "आधा") to halfDue,
                                "₹500" to BigDecimal("500"),
                                "₹1,000" to BigDecimal("1000")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                presets.forEach { (label, value) ->
                                    val isMatch = parsedAmount.compareTo(value) == 0
                                    Surface(
                                        onClick = { amountInput = value.toPlainString() },
                                        shape = RoundedCornerShape(9.dp),
                                        color = if (isMatch) ColBrandGreen else FlatDarkBg,
                                        border = BorderStroke(1.dp, if (isMatch) ColBrandGreen else FlatDarkBorder),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isMatch) Color.White else FlatTextPrimary,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Mode toggle: Cash | UPI
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("CASH" to tr("Cash", "नकद"), "UPI" to tr("UPI / Bank", "यूपीआई / बैंक")).forEach { (modeKey, modeLabel) ->
                            val isSelected = payMode == modeKey
                            Surface(
                                onClick = { payMode = modeKey },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) ColBrandGreen else FlatDarkBg,
                                border = BorderStroke(1.dp, if (isSelected) ColBrandGreen else FlatDarkBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 9.dp)
                                ) {
                                    Text(
                                        text = modeLabel,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else FlatTextPrimary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    // Note
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = tr("NOTE (OPTIONAL)", "नोट (वैकल्पिक)"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary
                        )
                        OutlinedTextField(
                            value = noteInput,
                            onValueChange = { noteInput = it },
                            placeholder = { Text(tr("Cash received at counter", "काउंटर पर नकद मिला"), fontSize = 13.5.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = FlatDarkBg,
                                unfocusedContainerColor = FlatDarkBg,
                                focusedBorderColor = ColBrandGreen,
                                unfocusedBorderColor = FlatDarkBorder,
                                focusedTextColor = FlatTextPrimary,
                                unfocusedTextColor = FlatTextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("collect_payment_note_input")
                        )
                    }

                    // Timing
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("PAYMENT TIMING", "भुगतान का समय"),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary
                            )
                            if (customDateMillis != null) {
                                Text(
                                    text = tr("Reset to Now", "अभी का समय"),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColBrandGreen,
                                    modifier = Modifier.clickable { customDateMillis = null }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val cal = Calendar.getInstance().apply { timeInMillis = activeTimestamp }
                                    android.app.DatePickerDialog(
                                        com.example.ui.theme.pickerThemedContext(context),
                                        { _, y, m, d ->
                                            val updated = Calendar.getInstance().apply {
                                                timeInMillis = activeTimestamp
                                                set(Calendar.YEAR, y)
                                                set(Calendar.MONTH, m)
                                                set(Calendar.DAY_OF_MONTH, d)
                                            }
                                            customDateMillis = updated.timeInMillis
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, FlatDarkBorder),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                                    .testTag("collect_payment_custom_date_btn")
                            ) {
                                Text(
                                    text = dateFormatter.format(Date(activeTimestamp)),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FlatTextPrimary,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    val cal = Calendar.getInstance().apply { timeInMillis = activeTimestamp }
                                    android.app.TimePickerDialog(
                                        com.example.ui.theme.pickerThemedContext(context),
                                        { _, hourOfDay, minute ->
                                            val updated = Calendar.getInstance().apply {
                                                timeInMillis = activeTimestamp
                                                set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                set(Calendar.MINUTE, minute)
                                                set(Calendar.SECOND, 0)
                                            }
                                            customDateMillis = updated.timeInMillis
                                        },
                                        cal.get(Calendar.HOUR_OF_DAY),
                                        cal.get(Calendar.MINUTE),
                                        false
                                    ).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, FlatDarkBorder),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                                    .testTag("collect_payment_custom_time_btn")
                            ) {
                                Text(
                                    text = timeFormatter.format(Date(activeTimestamp)),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FlatTextPrimary,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = tr("Cancel", "रद्द करें"),
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = {
                            val cust = selectedCustomer ?: return@Button
                            if (parsedAmount > BigDecimal.ZERO) {
                                val finalNote = buildString {
                                    if (payMode == "UPI") append("UPI")
                                    if (noteInput.isNotBlank()) {
                                        if (isNotEmpty()) append(" · ")
                                        append(noteInput.trim())
                                    }
                                }.ifBlank { if (payMode == "UPI") "UPI collection" else "Cash collection" }
                                onConfirmPayment(cust, parsedAmount, finalNote, customDateMillis)
                            }
                        },
                        enabled = selectedCustomer != null && parsedAmount > BigDecimal.ZERO,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColBrandGreen,
                            contentColor = Color.White,
                            disabledContainerColor = ColBrandGreen.copy(alpha = 0.45f),
                            disabledContentColor = Color.White.copy(alpha = 0.8f)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .heightIn(min = 48.dp)
                            .testTag("confirm_collect_payment_btn")
                    ) {
                        Text(
                            text = tr("Record payment", "भुगतान सेव करें"),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
