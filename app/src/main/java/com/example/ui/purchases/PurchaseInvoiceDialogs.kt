package com.example.ui.purchases

import com.example.ui.theme.pickerThemedContext
import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.PurchaseInvoiceItemRequest
import com.example.model.PaymentStatus
import com.example.model.Product
import com.example.model.PurchaseInvoice
import com.example.ui.home.ProductDropdownSelector
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatTextMuted
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import com.example.util.BillGenerator
import com.example.util.CurrencyUtils
import com.example.util.tr
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class PurchaseItemEditState(
    val id: String = UUID.randomUUID().toString(),
    selectedProduct: Product? = null,
    initialBags: String = "20",
    initialLooseKg: String = "",
    initialBagRate: String = "",
    initialKgRate: String = ""
) {
    var selectedProduct by mutableStateOf(selectedProduct)
    var bagsInput by mutableStateOf(initialBags)
    var looseKgInput by mutableStateOf(initialLooseKg)
    var customBagPriceInput by mutableStateOf(initialBagRate)
    var customKgPriceInput by mutableStateOf(initialKgRate)
}

@Composable
internal fun Modifier.dashedRoundedBorder(
    color: Color,
    cornerRadius: Dp = 14.dp,
    strokeWidth: Dp = 1.5.dp
): Modifier = this.then(
    Modifier. border(0.dp, Color.Transparent)
)

@Composable
internal fun DashedActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingPlus: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FlatDarkCard)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                color = SupGreen,
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                )
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (leadingPlus) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = SupGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = text,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = SupGreen
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditSupplierScreen(
    existingMetadata: SupplierProfileMetadata?,
    allProducts: List<Product>,
    balancePayable: BigDecimal = BigDecimal.ZERO,
    hasPurchaseBills: Boolean = false,
    onBack: () -> Unit,
    onSaveSupplier: (SupplierProfileMetadata) -> Unit,
    onDeleteSupplier: (() -> Unit)? = null
) {
    BackHandler { onBack() }

    val isEditing = existingMetadata != null
    var firmName by remember(existingMetadata) { mutableStateOf(existingMetadata?.firmName ?: "") }
    var contactPerson by remember(existingMetadata) { mutableStateOf(existingMetadata?.contactPerson ?: "") }

    val phonesState = remember(existingMetadata) {
        val initial = existingMetadata?.phones?.takeIf { it.isNotEmpty() }
            ?: listOf(SupplierPhoneItem(number = "+91 ", tag = "Owner"))
        mutableStateListOf(*initial.toTypedArray())
    }

    var city by remember(existingMetadata) { mutableStateOf(existingMetadata?.city ?: "") }
    var gstin by remember(existingMetadata) { mutableStateOf(existingMetadata?.gstin ?: "") }

    val defaultSuppliedCatalog = remember(allProducts) {
        val base = listOf("Cattle Feed", "Wheat Bran", "Mustard Cake", "Chana Churi", "Makka", "Bajra")
        val fromDb = allProducts.map { it.feedName }
        (base + fromDb).distinct()
    }
    val customItemsCatalog = remember { mutableStateListOf<String>() }
    val selectedItems = remember(existingMetadata) {
        val initial = existingMetadata?.itemsSupplied ?: listOf("Cattle Feed", "Wheat Bran")
        mutableStateListOf(*initial.toTypedArray())
    }

    var showAddCustomItemDialog by remember { mutableStateOf(false) }
    var newCustomItemText by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    var creditPeriodDays by remember(existingMetadata) {
        mutableIntStateOf(existingMetadata?.creditPeriodDays ?: 15)
    }
    var openingBalanceStr by remember(existingMetadata) {
        val initBal = existingMetadata?.openingBalance ?: BigDecimal.ZERO
        mutableStateOf(if (initBal > BigDecimal.ZERO) initBal.stripTrailingZeros().toPlainString() else "")
    }
    var notes by remember(existingMetadata) { mutableStateOf(existingMetadata?.notes ?: "") }
    var isActiveSupplier by remember(existingMetadata) {
        mutableStateOf(existingMetadata?.isActiveSupplier ?: true)
    }
    var remindBeforeDueDate by remember(existingMetadata) {
        mutableStateOf(existingMetadata?.remindBeforeDueDate ?: true)
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = FlatDarkCard,
        unfocusedContainerColor = FlatDarkCard,
        focusedBorderColor = SupGreen,
        unfocusedBorderColor = FlatDarkBorder,
        focusedTextColor = FlatTextPrimary,
        unfocusedTextColor = FlatTextPrimary
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlatDarkBg)
    ) {
        // Top Header matching Suppliers · Edit supplier (Page 3)
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
                    .testTag("add_supplier_back_btn"),
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
                    text = if (isEditing) tr("Edit supplier", "सप्लायर बदलें") else tr("Add supplier", "नया सप्लायर"),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary
                )
                Text(
                    text = if (isEditing) {
                        existingMetadata?.firmName?.ifBlank { firmName }.orEmpty().ifBlank { tr("Update supplier details", "सप्लायर जानकारी") }
                    } else {
                        tr("New supplier", "नया सप्लायर")
                    },
                    fontSize = 13.sp,
                    color = FlatTextSecondary
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // SUPPLIER / FIRM NAME *
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text(
                        text = tr("SUPPLIER / FIRM NAME ", "सप्लायर / फर्म का नाम "),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.7.sp
                    )
                    Text(
                        text = "*",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = SupRed
                    )
                }
                OutlinedTextField(
                    value = firmName,
                    onValueChange = {
                        firmName = it
                        errorMsg = null
                    },
                    placeholder = { Text("Supplier name", color = FlatTextMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("supplier_firm_name_input")
                )
            }

            // CONTACT PERSON
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = tr("CONTACT PERSON", "संपर्क व्यक्ति"),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextSecondary,
                    letterSpacing = 0.7.sp
                )
                OutlinedTextField(
                    value = contactPerson,
                    onValueChange = { contactPerson = it },
                    placeholder = { Text("Contact person", color = FlatTextMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("supplier_contact_person_input")
                )
            }

            // MOBILE NUMBERS section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("MOBILE NUMBERS", "मोबाइल नंबर"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.7.sp
                    )
                    Text(
                        text = "${phonesState.size} added",
                        fontSize = 12.5.sp,
                        color = FlatTextSecondary
                    )
                }

                val phoneTags = listOf("Owner", "Office", "WhatsApp", "Accounts", "Other")

                phonesState.forEachIndexed { idx, phoneItem ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = phoneItem.number,
                                    onValueChange = { updated ->
                                        phonesState[idx] = phoneItem.copy(number = updated)
                                    },
                                    placeholder = { Text("+91 98290 40012", color = FlatTextMuted) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = fieldColors,
                                    modifier = Modifier.weight(1f)
                                )

                                if (phonesState.size > 1) {
                                    Surface(
                                        onClick = { phonesState.removeAt(idx) },
                                        modifier = Modifier.size(44.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = SupChipIdleBg
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Remove phone",
                                                tint = FlatTextPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Tag",
                                    fontSize = 12.5.sp,
                                    color = FlatTextSecondary
                                )
                                if (idx == 0) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = SupGreenSoft
                                    ) {
                                        Text(
                                            text = "Primary",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SupGreen,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                phoneTags.forEach { tagOption ->
                                    val isSelected = phoneItem.tag.equals(tagOption, ignoreCase = true)
                                    Surface(
                                        onClick = { phonesState[idx] = phoneItem.copy(tag = tagOption) },
                                        shape = RoundedCornerShape(50),
                                        color = if (isSelected) SupGreen else SupChipIdleBg
                                    ) {
                                        Text(
                                            text = tagOption,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else FlatTextPrimary,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                DashedActionButton(
                    text = tr("+ Add another number", "+ एक और नंबर जोड़ें"),
                    leadingPlus = false,
                    onClick = {
                        phonesState.add(SupplierPhoneItem(number = "+91 ", tag = "Accounts"))
                    }
                )
            }

            // CITY / AREA & GSTIN (OPTIONAL)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = tr("CITY / AREA", "शहर / मंडी"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.7.sp
                    )
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        placeholder = { Text("Gohra Mandi", color = FlatTextMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = tr("GSTIN (OPTIONAL)", "जीएसटी नंबर"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.7.sp
                    )
                    OutlinedTextField(
                        value = gstin,
                        onValueChange = { gstin = it.uppercase(Locale.ROOT) },
                        placeholder = { Text("08ABCDE1234F1Z5", color = FlatTextMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ITEMS SUPPLIED
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tr("ITEMS SUPPLIED", "सप्लाई किए जाने वाले आइटम"),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextSecondary,
                    letterSpacing = 0.7.sp
                )

                val allAvailableItems = (defaultSuppliedCatalog + customItemsCatalog + selectedItems).distinct()
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allAvailableItems.forEach { itemLabel ->
                        val isSelected = selectedItems.contains(itemLabel)
                        Surface(
                            onClick = {
                                if (isSelected) selectedItems.remove(itemLabel)
                                else selectedItems.add(itemLabel)
                            },
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) SupGreen else SupChipIdleBg
                        ) {
                            Text(
                                text = itemLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else FlatTextPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }

                    if (!isEditing) {
                        Surface(
                            onClick = { showAddCustomItemDialog = true },
                            shape = RoundedCornerShape(50),
                            color = FlatDarkCard,
                            border = BorderStroke(1.dp, FlatDarkBorder)
                        ) {
                            Text(
                                text = "+ Add item",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = SupGreen,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // CREDIT PERIOD
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tr("CREDIT PERIOD", "उधार अवधि (क्रेडिट)"),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextSecondary,
                    letterSpacing = 0.7.sp
                )

                val creditOptions = listOf(
                    0 to "Cash only",
                    7 to "7 days",
                    15 to "15 days",
                    30 to "30 days"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    creditOptions.forEach { (days, label) ->
                        val isSelected = creditPeriodDays == days
                        Surface(
                            onClick = { creditPeriodDays = days },
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) SupGreen else SupChipIdleBg,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else FlatTextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // NOTES (Full width matching Page 3)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = tr("NOTES", "नोट्स"),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextSecondary,
                    letterSpacing = 0.6.sp
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("Pays on Fridays", color = FlatTextMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Active supplier & Remind me before due date Switches Card (Page 3)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
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
                                text = tr("Active supplier", "सक्रिय सप्लायर"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = tr(
                                    "Off hides them when making purchase bills",
                                    "बंद करने पर खरीद बिल बनाते समय दिखाई नहीं देगा"
                                ),
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = isActiveSupplier,
                            onCheckedChange = { isActiveSupplier = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SupGreen
                            )
                        )
                    }

                    HorizontalDivider(color = FlatDarkBorder, thickness = 1.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("Remind me before due date", "देय तिथि से पहले याद दिलाएं"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = tr(
                                    "2 days before each payment is due",
                                    "प्रत्येक भुगतान देय होने से 2 दिन पहले"
                                ),
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = remindBeforeDueDate,
                            onCheckedChange = { remindBeforeDueDate = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SupGreen
                            )
                        )
                    }
                }
            }

            // OPENING BALANCE Card
            if (isEditing || hasPurchaseBills) {
                val lockedOpenBal = existingMetadata?.openingBalance ?: BigDecimal.ZERO
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = SupChipIdleBg,
                    border = BorderStroke(1.dp, FlatDarkBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("Opening balance", "शुरुआती बकाया"),
                                fontSize = 13.5.sp,
                                color = FlatTextSecondary
                            )
                            Text(
                                text = CurrencyUtils.formatInr(lockedOpenBal),
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                        }
                        Text(
                            text = tr(
                                "Can’t be changed after the first purchase bill.",
                                "पहले खरीद बिल के बाद इसे बदला नहीं जा सकता।"
                            ),
                            fontSize = 12.5.sp,
                            color = FlatTextSecondary
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = tr("OPENING BALANCE (YOU OWE)", "शुरुआती बकाया (देना है)"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.6.sp
                    )
                    OutlinedTextField(
                        value = openingBalanceStr,
                        onValueChange = { openingBalanceStr = it },
                        prefix = { Text("₹ ", color = FlatTextSecondary, fontWeight = FontWeight.Bold) },
                        placeholder = { Text("0", color = FlatTextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Delete supplier section (Page 3)
            if (isEditing) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = tr("Delete supplier", "सप्लायर हटाएं"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = SupRed,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (balancePayable <= BigDecimal.ZERO) {
                                    showDeleteConfirmDialog = true
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .testTag("delete_supplier_btn")
                    )
                    if (balancePayable > BigDecimal.ZERO) {
                        Text(
                            text = tr(
                                "Can’t delete while ${CurrencyUtils.formatInr(balancePayable)} is still owed. Pay it first.",
                                "जब तक ${CurrencyUtils.formatInr(balancePayable)} बकाया है तब तक हटाया नहीं जा सकता। पहले भुगतान करें।"
                            ),
                            fontSize = 12.5.sp,
                            color = FlatTextSecondary
                        )
                    }
                }
            }

            if (errorMsg != null) {
                Text(
                    text = errorMsg!!,
                    color = SupRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Sticky Bottom Bar: Cancel | Save changes / Save supplier
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = FlatDarkBg,
            border = BorderStroke(1.dp, FlatDarkBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
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

                val errRequiredFirm = tr("Please enter supplier / firm name", "कृपया सप्लायर का नाम दर्ज करें")
                Button(
                    onClick = {
                        if (firmName.isBlank()) {
                            errorMsg = errRequiredFirm
                            return@Button
                        }
                        val openBal = if (isEditing || hasPurchaseBills) {
                            existingMetadata?.openingBalance ?: BigDecimal.ZERO
                        } else {
                            openingBalanceStr.trim().replace(",", "").toBigDecimalOrNull() ?: BigDecimal.ZERO
                        }
                        val cleanedPhones = phonesState.map {
                            it.copy(number = it.number.trim())
                        }.filter { it.number.isNotBlank() && it.number != "+91" }

                        onSaveSupplier(
                            SupplierProfileMetadata(
                                firmName = firmName.trim(),
                                contactPerson = contactPerson.trim(),
                                phones = cleanedPhones,
                                city = city.trim(),
                                gstin = gstin.trim(),
                                itemsSupplied = selectedItems.toList(),
                                creditPeriodDays = creditPeriodDays,
                                openingBalance = openBal.max(BigDecimal.ZERO),
                                notes = notes.trim(),
                                isActiveSupplier = isActiveSupplier,
                                remindBeforeDueDate = remindBeforeDueDate
                            )
                        )
                    },
                    modifier = Modifier
                        .weight(1.35f)
                        .height(50.dp)
                        .testTag("save_supplier_btn"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SupGreen,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (isEditing) tr("Save changes", "बदलाव सेव करें") else tr("Save supplier", "सप्लायर सेव करें"),
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = FlatDarkCard,
            title = {
                Text(
                    text = tr("Delete supplier?", "सप्लायर हटाएं?"),
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary
                )
            },
            text = {
                Text(
                    text = tr(
                        "Remove ${firmName.ifBlank { "this supplier" }} from your directory?",
                        "क्या आप इस सप्लायर को डायरेक्टरी से हटाना चाहते हैं?"
                    ),
                    color = FlatTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteSupplier?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SupRed, contentColor = Color.White)
                ) {
                    Text(tr("Delete", "हटाएं"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(tr("Cancel", "रद्द करें"))
                }
            }
        )
    }

    if (showAddCustomItemDialog) {
        AlertDialog(
            onDismissRequest = { showAddCustomItemDialog = false },
            containerColor = FlatDarkCard,
            title = {
                Text(
                    text = tr("Add item supplied", "नया आइटम जोड़ें"),
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary
                )
            },
            text = {
                OutlinedTextField(
                    value = newCustomItemText,
                    onValueChange = { newCustomItemText = it },
                    label = { Text(tr("Item name", "आइटम का नाम")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newCustomItemText.trim()
                        if (trimmed.isNotEmpty()) {
                            customItemsCatalog.add(trimmed)
                            if (!selectedItems.contains(trimmed)) {
                                selectedItems.add(trimmed)
                            }
                        }
                        newCustomItemText = ""
                        showAddCustomItemDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SupGreen, contentColor = Color.White)
                ) {
                    Text(tr("Add", "जोड़ें"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomItemDialog = false }) {
                    Text(tr("Cancel", "रद्द करें"))
                }
            }
        )
    }
}

@Composable
fun NewPurchaseBillScreen(
    products: List<Product>,
    existingSuppliers: List<String>,
    preselectedSupplier: String? = null,
    metadataMap: Map<String, SupplierProfileMetadata> = emptyMap(),
    onBack: () -> Unit,
    onSaveInvoice: (
        wholesalerName: String,
        wholesalerPhone: String,
        invoiceNumber: String,
        items: List<PurchaseInvoiceItemRequest>,
        ladhan: BigDecimal,
        utaar: BigDecimal,
        gaadiKiraya: BigDecimal,
        paidAmount: BigDecimal,
        notes: String
    ) -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var selectedSupplier by remember(preselectedSupplier, existingSuppliers) {
        mutableStateOf(
            preselectedSupplier?.takeIf { it.isNotBlank() }
                ?: existingSuppliers.firstOrNull()
                ?: "Supplier 1"
        )
    }
    var supplierDropdownExpanded by remember { mutableStateOf(false) }
    var showNewSupplierNameDialog by remember { mutableStateOf(false) }
    var customSupplierInput by remember { mutableStateOf("") }

    var billDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val dateFormat = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    val defaultFirstProduct = remember(products) { products.firstOrNull() }
    val defaultFirstBagRate = remember(defaultFirstProduct) {
        if (defaultFirstProduct != null && !defaultFirstProduct.isLooseOnly && defaultFirstProduct.kgPerBag > BigDecimal.ONE) {
            val costBag = defaultFirstProduct.averagePurchaseCostPerKg.multiply(defaultFirstProduct.kgPerBag)
                .setScale(0, RoundingMode.HALF_UP)
            if (costBag > BigDecimal.ZERO) costBag.toPlainString()
            else defaultFirstProduct.bagSellingPrice.setScale(0, RoundingMode.HALF_UP).toPlainString()
        } else "1150"
    }

    val itemsState = remember(defaultFirstProduct) {
        if (defaultFirstProduct != null) {
            mutableStateListOf(
                PurchaseItemEditState(
                    selectedProduct = defaultFirstProduct,
                    initialBags = "20",
                    initialBagRate = defaultFirstBagRate,
                    initialKgRate = defaultFirstProduct.averagePurchaseCostPerKg.toPlainString()
                )
            )
        } else {
            mutableStateListOf()
        }
    }

    var itemBeingEdited by remember { mutableStateOf<PurchaseItemEditState?>(null) }
    var isAddingNewItem by remember { mutableStateOf(false) }

    // Extra charges matching Suppliers · New purchase bill.pdf
    var ladhanInput by remember { mutableStateOf("") }
    var utaarInput by remember { mutableStateOf("") }
    var deliveryLabelInput by remember { mutableStateOf("Delivery") }
    var deliveryAmountInput by remember { mutableStateOf("") }

    val extraCustomCharges = remember { mutableStateListOf<Pair<String, String>>() }

    var paidAmountInput by remember { mutableStateOf("") }
    var userEditedPaid by remember { mutableStateOf(false) }
    var paymentMode by remember { mutableStateOf("CASH") } // CASH or UPI
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Totals calculation
    val ladhan = remember(ladhanInput) {
        ladhanInput.trim().replace(",", "").toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
    }
    val utaar = remember(utaarInput) {
        utaarInput.trim().replace(",", "").toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
    }
    val deliveryAmt = remember(deliveryAmountInput) {
        deliveryAmountInput.trim().replace(",", "").toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
    }
    val additionalCustomTotal = extraCustomCharges.fold(BigDecimal.ZERO) { acc, pair ->
        val amt = pair.second.trim().replace(",", "").toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
        acc.add(amt)
    }
    val totalGaadiAndCustom = remember(deliveryAmt, additionalCustomTotal) {
        deliveryAmt.add(additionalCustomTotal)
    }
    val totalCharges = remember(ladhan, utaar, totalGaadiAndCustom) {
        ladhan.add(utaar).add(totalGaadiAndCustom)
    }

    var goodsSubtotal = BigDecimal.ZERO
    itemsState.forEach { item ->
        goodsSubtotal = goodsSubtotal.add(calculateItemLineTotal(item))
    }

    val grandTotal = remember(goodsSubtotal, totalCharges) {
        goodsSubtotal.add(totalCharges).setScale(0, RoundingMode.HALF_UP)
    }

    val paidAmount = remember(paidAmountInput, grandTotal, userEditedPaid) {
        if (!userEditedPaid) {
            grandTotal
        } else {
            paidAmountInput.trim().replace(",", "").toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
        }
    }

    val addedToKhata = remember(grandTotal, paidAmount) {
        grandTotal.subtract(paidAmount).max(BigDecimal.ZERO)
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = FlatDarkCard,
        unfocusedContainerColor = FlatDarkCard,
        focusedBorderColor = SupGreen,
        unfocusedBorderColor = FlatDarkBorder,
        focusedTextColor = FlatTextPrimary,
        unfocusedTextColor = FlatTextPrimary
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlatDarkBg)
    ) {
        // Header matching Suppliers · New purchase bill.pdf
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
                    .testTag("new_purchase_bill_back_btn"),
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
                    text = tr("New purchase bill", "नया खरीद बिल"),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary
                )
                Text(
                    text = tr("From supplier", "सप्लायर से खरीद"),
                    fontSize = 13.sp,
                    color = FlatTextSecondary
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // SUPPLIER & DATE Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1.1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = tr("SUPPLIER", "सप्लायर"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.7.sp
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            onClick = { supplierDropdownExpanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("purchase_wholesaler_input"),
                            shape = RoundedCornerShape(14.dp),
                            color = FlatDarkCard,
                            border = BorderStroke(1.dp, FlatDarkBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedSupplier,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = FlatTextSecondary
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = supplierDropdownExpanded,
                            onDismissRequest = { supplierDropdownExpanded = false }
                        ) {
                            val supplierChoices = existingSuppliers.distinct()
                            supplierChoices.forEach { supName ->
                                DropdownMenuItem(
                                    text = { Text(supName, fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        selectedSupplier = supName
                                        supplierDropdownExpanded = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("+ New supplier name...", color = SupGreen, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    supplierDropdownExpanded = false
                                    customSupplierInput = ""
                                    showNewSupplierNameDialog = true
                                }
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(0.9f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = tr("DATE", "तारीख"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.7.sp
                    )
                    Surface(
                        onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = billDateMillis }
                            DatePickerDialog(
                                pickerThemedContext(context),
                                { _, y, m, d ->
                                    val picked = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, y)
                                        set(Calendar.MONTH, m)
                                        set(Calendar.DAY_OF_MONTH, d)
                                    }
                                    billDateMillis = picked.timeInMillis
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder)
                    ) {
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        ) {
                            Text(
                                text = "Today, ${dateFormat.format(Date(billDateMillis))}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // ITEMS Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = tr("ITEMS", "आइटम"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.8.sp
                    )

                    itemsState.forEachIndexed { index, itemState ->
                        val prod = itemState.selectedProduct
                        val prodTitle = if (prod != null && !prod.isLooseOnly && prod.kgPerBag > BigDecimal.ZERO) {
                            "${prod.feedName} (${CurrencyUtils.formatWeight(prod.kgPerBag)} kg)"
                        } else {
                            prod?.feedName ?: "Cattle Feed (50 kg)"
                        }
                        val itemTotal = calculateItemLineTotal(itemState)
                        val itemSubtitle = formatPurchaseItemSubtitle(itemState)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    isAddingNewItem = false
                                    itemBeingEdited = itemState
                                }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = prodTitle,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary
                                )
                                Text(
                                    text = itemSubtitle,
                                    fontSize = 13.sp,
                                    color = FlatTextSecondary
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = CurrencyUtils.formatInr(itemTotal),
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Edit item",
                                    tint = FlatTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (index < itemsState.lastIndex) {
                            HorizontalDivider(color = FlatDarkBorder, thickness = 1.dp)
                        }
                    }

                    DashedActionButton(
                        text = tr("Add item", "आइटम जोड़ें"),
                        leadingPlus = true,
                        onClick = {
                            val nextProd = products.firstOrNull()
                            val bagRate = if (nextProd != null && !nextProd.isLooseOnly && nextProd.kgPerBag > BigDecimal.ONE) {
                                nextProd.averagePurchaseCostPerKg.multiply(nextProd.kgPerBag).setScale(0, RoundingMode.HALF_UP).toPlainString()
                            } else "1150"
                            val newItem = PurchaseItemEditState(
                                selectedProduct = nextProd,
                                initialBags = "10",
                                initialBagRate = bagRate,
                                initialKgRate = nextProd?.averagePurchaseCostPerKg?.toPlainString() ?: "23"
                            )
                            isAddingNewItem = true
                            itemBeingEdited = newItem
                        },
                        modifier = Modifier.testTag("add_item_row_btn")
                    )
                }
            }

            // EXTRA CHARGES Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
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
                            text = tr("EXTRA CHARGES", "अतिरिक्त खर्च (भाड़ा / मजदूरी)"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.8.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = SupChipIdleBg
                        ) {
                            Text(
                                text = tr("Optional", "वैकल्पिक"),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Row 1: LABOUR · LADAAN (LOAD) | LABOUR · UTAAR (UNLOAD)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "LABOUR · LADAAN (LOAD)",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary
                            )
                            OutlinedTextField(
                                value = ladhanInput,
                                onValueChange = { ladhanInput = it },
                                prefix = { Text("₹ ", color = FlatTextSecondary, fontWeight = FontWeight.Bold) },
                                placeholder = { Text("Optional", color = FlatTextMuted) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ladhan_input")
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "LABOUR · UTAAR (UNLOAD)",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary
                            )
                            OutlinedTextField(
                                value = utaarInput,
                                onValueChange = { utaarInput = it },
                                prefix = { Text("₹ ", color = FlatTextSecondary, fontWeight = FontWeight.Bold) },
                                placeholder = { Text("400", color = FlatTextMuted) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("utaar_input")
                            )
                        }
                    }

                    // Row 2: DELIVERY / CUSTOM CHARGE | AMOUNT
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1.1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "DELIVERY / CUSTOM CHARGE",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary
                            )
                            OutlinedTextField(
                                value = deliveryLabelInput,
                                onValueChange = { deliveryLabelInput = it },
                                placeholder = { Text("Delivery", color = FlatTextMuted) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Column(
                            modifier = Modifier.weight(0.9f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "AMOUNT",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary
                            )
                            OutlinedTextField(
                                value = deliveryAmountInput,
                                onValueChange = { deliveryAmountInput = it },
                                prefix = { Text("₹ ", color = FlatTextSecondary, fontWeight = FontWeight.Bold) },
                                placeholder = { Text("600", color = FlatTextMuted) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("gaadi_kiraya_input")
                            )
                        }
                    }

                    extraCustomCharges.forEachIndexed { idx, (label, amt) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = label,
                                onValueChange = { extraCustomCharges[idx] = it to amt },
                                placeholder = { Text("Custom charge", color = FlatTextMuted) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                modifier = Modifier.weight(1.1f)
                            )
                            OutlinedTextField(
                                value = amt,
                                onValueChange = { extraCustomCharges[idx] = label to it },
                                prefix = { Text("₹ ", color = FlatTextSecondary, fontWeight = FontWeight.Bold) },
                                placeholder = { Text("0", color = FlatTextMuted) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                modifier = Modifier.weight(0.9f)
                            )
                        }
                    }

                    Text(
                        text = tr("+ Add another charge", "+ एक और खर्च जोड़ें"),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = SupGreen,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                extraCustomCharges.add("Toll / Weighbridge" to "")
                            }
                            .padding(vertical = 2.dp)
                    )
                }
            }

            // Totals & Payment Settlement Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
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
                            text = "Items ${CurrencyUtils.formatInr(goodsSubtotal)} + charges ${CurrencyUtils.formatInr(totalCharges)}",
                            fontSize = 13.5.sp,
                            color = FlatTextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = CurrencyUtils.formatInr(grandTotal),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = if (userEditedPaid) paidAmountInput else grandTotal.toPlainString(),
                            onValueChange = {
                                userEditedPaid = true
                                paidAmountInput = it
                            },
                            prefix = { Text("₹ ", color = FlatTextSecondary, fontWeight = FontWeight.Bold) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("purchase_paid_input")
                        )

                        listOf("CASH" to "Cash", "UPI" to "UPI").forEach { (key, label) ->
                            val isSelected = paymentMode == key
                            Surface(
                                onClick = { paymentMode = key },
                                shape = RoundedCornerShape(50),
                                color = if (isSelected) SupGreen else SupChipIdleBg,
                                modifier = Modifier.height(44.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else FlatTextPrimary
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr("Paid now", "अभी दिया"),
                            fontSize = 14.sp,
                            color = FlatTextSecondary
                        )
                        Text(
                            text = CurrencyUtils.formatInr(paidAmount),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = SupGreen
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr("Added to supplier khata", "सप्लायर खाते में जोड़ा गया"),
                            fontSize = 14.sp,
                            color = FlatTextSecondary
                        )
                        Text(
                            text = CurrencyUtils.formatInr(addedToKhata),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (addedToKhata > BigDecimal.ZERO) SupRed else FlatTextPrimary
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = SupRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Bottom Bar: Cancel | Save purchase bill
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = FlatDarkBg,
            border = BorderStroke(1.dp, FlatDarkBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
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

                val errAddOneItem = tr("Please add at least one item", "कृपया कम से कम एक आइटम जोड़ें")
                Button(
                    onClick = {
                        if (itemsState.isEmpty()) {
                            errorMessage = errAddOneItem
                            return@Button
                        }
                        val reqItems = mutableListOf<PurchaseInvoiceItemRequest>()
                        for (item in itemsState) {
                            val prod = item.selectedProduct ?: products.firstOrNull() ?: continue
                            val bags = item.bagsInput.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
                            val loose = item.looseKgInput.trim().toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
                            if (bags == 0 && loose <= BigDecimal.ZERO) continue
                            val customBag = item.customBagPriceInput.trim().toBigDecimalOrNull()
                            val customKg = item.customKgPriceInput.trim().toBigDecimalOrNull()
                            reqItems.add(
                                PurchaseInvoiceItemRequest(
                                    productId = prod.id,
                                    bags = bags,
                                    looseKg = loose,
                                    customBagPrice = customBag,
                                    customPricePerKg = customKg
                                )
                            )
                        }
                        if (reqItems.isEmpty()) {
                            errorMessage = errAddOneItem
                            return@Button
                        }
                        val metaPhone = metadataMap[selectedSupplier.trim().lowercase(Locale.ROOT)]?.primaryPhone ?: ""
                        val invNo = "P-${(System.currentTimeMillis() / 1000) % 900 + 100}"
                        onSaveInvoice(
                            selectedSupplier.trim(),
                            metaPhone,
                            invNo,
                            reqItems,
                            ladhan,
                            utaar,
                            totalGaadiAndCustom,
                            paidAmount,
                            "Paid via $paymentMode"
                        )
                    },
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("submit_purchase_invoice_btn"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SupGreen,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = tr("Save purchase bill", "खरीद बिल सेव करें"),
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Add / Edit Item Modal Dialog
    itemBeingEdited?.let { editingItem ->
        AlertDialog(
            onDismissRequest = { itemBeingEdited = null },
            containerColor = FlatDarkCard,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isAddingNewItem) tr("Add item", "आइटम जोड़ें") else tr("Edit item", "आइटम बदलें"),
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary
                    )
                    if (!isAddingNewItem && itemsState.size > 1) {
                        IconButton(
                            onClick = {
                                itemsState.remove(editingItem)
                                itemBeingEdited = null
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = SupRed)
                        }
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProductDropdownSelector(
                        products = products,
                        selectedProduct = editingItem.selectedProduct,
                        onSelectProduct = { prod ->
                            editingItem.selectedProduct = prod
                            if (prod != null) {
                                if (!prod.isLooseOnly && prod.kgPerBag > BigDecimal.ONE) {
                                    editingItem.customBagPriceInput = prod.averagePurchaseCostPerKg
                                        .multiply(prod.kgPerBag)
                                        .setScale(0, RoundingMode.HALF_UP)
                                        .toPlainString()
                                }
                                editingItem.customKgPriceInput = prod.averagePurchaseCostPerKg.toPlainString()
                            }
                        }
                    )

                    val prod = editingItem.selectedProduct
                    if (prod != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!prod.isLooseOnly) {
                                OutlinedTextField(
                                    value = editingItem.bagsInput,
                                    onValueChange = { editingItem.bagsInput = it },
                                    label = { Text("Bags (${CurrencyUtils.formatWeight(prod.kgPerBag)} kg)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedTextField(
                                value = editingItem.looseKgInput,
                                onValueChange = { editingItem.looseKgInput = it },
                                label = { Text("Loose (kg)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!prod.isLooseOnly) {
                                OutlinedTextField(
                                    value = editingItem.customBagPriceInput,
                                    onValueChange = { editingItem.customBagPriceInput = it },
                                    label = { Text("Rate / bag (₹)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedTextField(
                                value = editingItem.customKgPriceInput,
                                onValueChange = { editingItem.customKgPriceInput = it },
                                label = { Text("Rate / kg (₹)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isAddingNewItem && !itemsState.contains(editingItem)) {
                            itemsState.add(editingItem)
                        }
                        itemBeingEdited = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SupGreen, contentColor = Color.White)
                ) {
                    Text(tr("Done", "ठीक है"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemBeingEdited = null }) {
                    Text(tr("Cancel", "रद्द करें"))
                }
            }
        )
    }

    if (showNewSupplierNameDialog) {
        AlertDialog(
            onDismissRequest = { showNewSupplierNameDialog = false },
            containerColor = FlatDarkCard,
            title = {
                Text(
                    text = tr("Supplier name", "सप्लायर का नाम"),
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary
                )
            },
            text = {
                OutlinedTextField(
                    value = customSupplierInput,
                    onValueChange = { customSupplierInput = it },
                    placeholder = { Text("Supplier name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customSupplierInput.isNotBlank()) {
                            selectedSupplier = customSupplierInput.trim()
                        }
                        showNewSupplierNameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SupGreen, contentColor = Color.White)
                ) {
                    Text(tr("Select", "चुनें"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewSupplierNameDialog = false }) {
                    Text(tr("Cancel", "रद्द करें"))
                }
            }
        )
    }
}

private fun calculateItemLineTotal(item: PurchaseItemEditState): BigDecimal {
    val prod = item.selectedProduct ?: return BigDecimal.ZERO
    val bags = item.bagsInput.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
    val loose = item.looseKgInput.trim().toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
    val customBag = item.customBagPriceInput.trim().toBigDecimalOrNull()
    val customKg = item.customKgPriceInput.trim().toBigDecimalOrNull()

    val effectiveBag = customBag ?: if (!prod.isLooseOnly && prod.kgPerBag > BigDecimal.ONE) prod.bagSellingPrice else null
    val effectiveKg = customKg ?: if (prod.isLooseOnly || prod.kgPerBag <= BigDecimal.ONE) {
        prod.sellingPricePerKg
    } else {
        (effectiveBag ?: prod.bagSellingPrice).divide(prod.kgPerBag, 4, RoundingMode.HALF_UP)
    }

    return if (!prod.isLooseOnly && prod.kgPerBag > BigDecimal.ONE && effectiveBag != null) {
        effectiveBag.multiply(BigDecimal(bags)).add(effectiveKg.multiply(loose)).setScale(0, RoundingMode.HALF_UP)
    } else {
        val totalKg = prod.kgPerBag.multiply(BigDecimal(bags)).add(loose)
        effectiveKg.multiply(totalKg).setScale(0, RoundingMode.HALF_UP)
    }
}

private fun formatPurchaseItemSubtitle(item: PurchaseItemEditState): String {
    val prod = item.selectedProduct ?: return "20 bags × ₹1,150"
    val bags = item.bagsInput.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
    val loose = item.looseKgInput.trim().toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
    val customBag = item.customBagPriceInput.trim().toBigDecimalOrNull()
    val effectiveBag = customBag ?: prod.bagSellingPrice

    return if (!prod.isLooseOnly && bags > 0 && loose.compareTo(BigDecimal.ZERO) == 0) {
        "$bags bags × ${CurrencyUtils.formatInr(effectiveBag)}"
    } else if (bags > 0 && loose > BigDecimal.ZERO) {
        "$bags bags + ${CurrencyUtils.formatWeight(loose)} kg"
    } else {
        "${CurrencyUtils.formatWeight(loose)} kg"
    }
}

@Composable
fun RecordSupplierPaymentDialog(
    wholesalerName: String,
    outstandingBalance: BigDecimal,
    availableSuppliers: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onRecordPayment: (selectedWholesaler: String, amount: BigDecimal, paymentMode: String, note: String) -> Unit
) {
    var chosenSupplier by remember(wholesalerName, availableSuppliers) {
        mutableStateOf(
            wholesalerName.ifBlank { availableSuppliers.firstOrNull() ?: "Supplier 1" }
        )
    }
    var supplierDropdownOpen by remember { mutableStateOf(false) }
    var amountInput by remember(outstandingBalance) {
        mutableStateOf(if (outstandingBalance > BigDecimal.ZERO) outstandingBalance.stripTrailingZeros().toPlainString() else "")
    }
    var paymentMode by remember { mutableStateOf("CASH") }
    var noteInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FlatDarkCard,
        title = {
            Column {
                Text(
                    text = tr("Pay supplier", "सप्लायर भुगतान"),
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary
                )
                Text(
                    text = chosenSupplier,
                    fontSize = 13.sp,
                    color = SupGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (wholesalerName.isBlank() && availableSuppliers.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            onClick = { supplierDropdownOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = FlatDarkBg,
                            border = BorderStroke(1.dp, FlatDarkBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(chosenSupplier, fontWeight = FontWeight.Bold, color = FlatTextPrimary)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = FlatTextSecondary)
                            }
                        }
                        DropdownMenu(
                            expanded = supplierDropdownOpen,
                            onDismissRequest = { supplierDropdownOpen = false }
                        ) {
                            availableSuppliers.forEach { sup ->
                                DropdownMenuItem(
                                    text = { Text(sup) },
                                    onClick = {
                                        chosenSupplier = sup
                                        supplierDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (outstandingBalance > BigDecimal.ZERO) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = SupRedSoft
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("You owe", fontSize = 13.sp, color = SupRed, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = CurrencyUtils.formatInr(outstandingBalance),
                                fontWeight = FontWeight.Bold,
                                color = SupRed
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text(tr("Amount (₹) *", "राशि (₹) *")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("supplier_pay_amount_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("CASH" to "Cash", "UPI" to "UPI", "BANK" to "Bank").forEach { (mode, label) ->
                        val isSelected = paymentMode == mode
                        Surface(
                            onClick = { paymentMode = mode },
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) SupGreen else SupChipIdleBg,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else FlatTextPrimary
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text(tr("Note (e.g. against #P-212, part payment)", "नोट (जैसे #P-212 या आंशिक भुगतान)")) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Text(error!!, color = SupRed, fontSize = 12.5.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountInput.trim().replace(",", "").toBigDecimalOrNull() ?: BigDecimal.ZERO
                    if (amt <= BigDecimal.ZERO) {
                        error = "Please enter valid payment amount"
                        return@Button
                    }
                    onRecordPayment(chosenSupplier, amt, paymentMode, noteInput.trim().ifBlank { "part payment" })
                },
                colors = ButtonDefaults.buttonColors(containerColor = SupGreen, contentColor = Color.White),
                modifier = Modifier.testTag("confirm_supplier_payment_btn")
            ) {
                Text(tr("Save payment", "भुगतान सेव करें"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("Cancel", "रद्द करें"), color = FlatTextSecondary)
            }
        }
    )
}

@Composable
fun PurchaseInvoiceDetailsDialog(
    invoice: PurchaseInvoice,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val items = remember(invoice) { invoice.getItems() }
    val shortNo = remember(invoice) { formatShortPurchaseBillNo(invoice.invoiceNumber, 1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FlatDarkCard,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Purchase #$shortNo",
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary
                    )
                    Text(
                        text = "${invoice.wholesalerName} · ${dateFormat.format(Date(invoice.date))}",
                        fontSize = 12.5.sp,
                        color = FlatTextSecondary
                    )
                }
                val isPaid = invoice.balanceAmount.compareTo(BigDecimal.ZERO) == 0
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isPaid) SupGreenSoft else SupRedSoft
                ) {
                    Text(
                        text = if (isPaid) "Paid" else if (invoice.paidAmount > BigDecimal.ZERO) "Part paid" else "Unpaid",
                        color = if (isPaid) SupGreen else SupRed,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEach { item ->
                    val qtyDesc = if (item.bags > 0) "${item.bags} bags" else "${CurrencyUtils.formatWeight(item.totalKg)} kg"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${item.feedName} × $qtyDesc",
                            fontSize = 13.5.sp,
                            color = FlatTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = CurrencyUtils.formatInr(item.itemCost),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                    }
                }

                val extra = invoice.ladhan.add(invoice.utaar).add(invoice.gaadiKiraya)
                if (extra > BigDecimal.ZERO) {
                    HorizontalDivider(color = FlatDarkBorder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Extra charges (Ladaan/Utaar/Delivery)", fontSize = 13.sp, color = FlatTextSecondary)
                        Text(CurrencyUtils.formatInr(extra), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FlatTextPrimary)
                    }
                }

                HorizontalDivider(color = FlatDarkBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total", fontWeight = FontWeight.Bold, color = FlatTextPrimary)
                    Text(CurrencyUtils.formatInr(invoice.totalInvoiceCost), fontWeight = FontWeight.Bold, color = FlatTextPrimary)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Paid now", fontSize = 13.sp, color = FlatTextSecondary)
                    Text(CurrencyUtils.formatInr(invoice.paidAmount), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = SupGreen)
                }
                if (invoice.balanceAmount > BigDecimal.ZERO) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Added to supplier khata", fontSize = 13.sp, color = SupRed, fontWeight = FontWeight.Bold)
                        Text(CurrencyUtils.formatInr(invoice.balanceAmount), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SupRed)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { BillGenerator.sharePurchaseInvoice(context, invoice) },
                colors = ButtonDefaults.buttonColors(containerColor = SupGreen, contentColor = Color.White)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(tr("Share", "शेयर"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("Close", "बंद करें"), color = FlatTextSecondary)
            }
        }
    )
}
