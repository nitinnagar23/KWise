package com.example.ui.home

import com.example.ui.theme.kw
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.Customer
import com.example.model.Permission
import com.example.model.Product
import com.example.model.Role
import com.example.model.TaggedMobileNumber
import com.example.model.can
import com.example.model.getCustomerStatusChip
import com.example.ui.theme.CGreen
import com.example.ui.theme.CRedUdhari
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatDarkInput
import com.example.ui.theme.FlatDarkInputBorder
import com.example.ui.theme.FlatTextMuted
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import com.example.util.CurrencyUtils
import com.example.util.tr
import java.math.BigDecimal
import androidx.compose.ui.text.style.TextOverflow

private val DialogBrandGreen: Color get() = kw(0xFF0E6F43)
private val DialogMintBadgeBg: Color get() = kw(0xFFE1F5EB)
@Composable
fun CustomerDropdownSelector(
    customers: List<Customer>,
    selectedCustomer: Customer?,
    onSelectCustomer: (Customer) -> Unit,
    onAddNewCustomer: () -> Unit,
    showExactUdhari: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = FlatDarkInput,
            border = BorderStroke(1.dp, FlatDarkInputBorder),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = if (selectedCustomer != null) CGreen else FlatTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = selectedCustomer?.name ?: tr("Select customer", "ग्राहक चुनें"),
                            fontWeight = FontWeight.SemiBold,
                            color = if (selectedCustomer != null) FlatTextPrimary else FlatTextMuted
                        )
                        if (selectedCustomer != null) {
                            val subline = if (showExactUdhari) {
                                "${selectedCustomer.village.ifBlank { "General" }} • Udhari: ${CurrencyUtils.formatInr(selectedCustomer.totalUdhari)}"
                            } else {
                                val chip = getCustomerStatusChip(selectedCustomer)
                                "${selectedCustomer.village.ifBlank { "General" }} • ${chip.labelEn}"
                            }
                            Text(
                                text = subline,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selectedCustomer.totalUdhari > BigDecimal.ZERO) CRedUdhari else FlatTextSecondary
                            )
                        }
                    }
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = FlatTextSecondary)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(FlatDarkCard)
        ) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = CGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tr("+ Add New Customer", "+ नया ग्राहक जोड़ें"), color = CGreen, fontWeight = FontWeight.Bold)
                    }
                },
                onClick = {
                    expanded = false
                    onAddNewCustomer()
                }
            )
            customers.forEach { customer ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(customer.name, fontWeight = FontWeight.SemiBold, color = FlatTextPrimary)
                            val subline = if (showExactUdhari) {
                                "${customer.village.ifBlank { "General" }} • Udhari: ${CurrencyUtils.formatInr(customer.totalUdhari)}"
                            } else {
                                val chip = getCustomerStatusChip(customer)
                                "${customer.village.ifBlank { "General" }} • ${chip.labelEn}"
                            }
                            Text(
                                subline,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (customer.totalUdhari > BigDecimal.ZERO) CRedUdhari else FlatTextSecondary
                            )
                        }
                    },
                    onClick = {
                        onSelectCustomer(customer)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun ProductDropdownSelector(
    products: List<Product>,
    selectedProduct: Product?,
    onSelectProduct: (Product) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = FlatDarkInput,
            border = BorderStroke(1.dp, FlatDarkInputBorder),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = if (selectedProduct != null) CGreen else FlatTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = selectedProduct?.feedName ?: tr("Select product", "सामान चुनें"),
                            fontWeight = FontWeight.SemiBold,
                            color = if (selectedProduct != null) FlatTextPrimary else FlatTextMuted
                        )
                        if (selectedProduct != null) {
                            Text(
                                text = "Stock: ${CurrencyUtils.formatWeightKg(selectedProduct.currentStockKg)} • ${CurrencyUtils.formatInr(selectedProduct.sellingPricePerKg, true)}/kg",
                                style = MaterialTheme.typography.labelSmall,
                                color = FlatTextSecondary
                            )
                        }
                    }
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = FlatTextSecondary)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(FlatDarkCard)
        ) {
            products.forEach { prod ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(prod.feedName, fontWeight = FontWeight.SemiBold, color = FlatTextPrimary)
                            Text(
                                "Stock: ${CurrencyUtils.formatWeightKg(prod.currentStockKg)} • ${CurrencyUtils.formatInr(prod.sellingPricePerKg, true)}/kg",
                                style = MaterialTheme.typography.labelSmall,
                                color = FlatTextSecondary
                            )
                        }
                    },
                    onClick = {
                        onSelectProduct(prod)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddCustomerDialog(
    currentUserId: String,
    currentUserRole: Role? = null,
    onDismiss: () -> Unit,
    onCustomerAdded: (Customer) -> Unit
) {
    val isDark = com.example.ui.theme.KwThemeState.isDark
    val dupBannerBg = if (isDark) Color(0xFF2D240F) else Color(0xFFFFFBEB)
    val dupBannerBorder = if (isDark) Color(0xFF5C4718) else Color(0xFFFDE68A)
    val dupBannerText = if (isDark) Color(0xFFF5BE4F) else Color(0xFFB45309)

    var name by remember { mutableStateOf("") }
    var fatherName by remember { mutableStateOf("") }
    val phoneEntries = remember {
        mutableStateListOf(TaggedMobileNumber(number = "", tag = "Self"))
    }
    var village by remember { mutableStateOf("") }
    var selectedLimitPreset by remember { mutableStateOf("10000") }
    var creditLimitInput by remember { mutableStateOf("") }
    var autoBlockAtLimit by remember { mutableStateOf(true) }
    var reminderChannel by remember { mutableStateOf("WhatsApp") }
    var notesInput by remember { mutableStateOf("") }
    var showMoreDetails by remember { mutableStateOf(false) }

    val canEditCreditLimit = can(currentUserRole, Permission.EDIT_CREDIT_LIMIT)
    val effectiveLimit = remember(selectedLimitPreset, creditLimitInput, canEditCreditLimit) {
        if (!canEditCreditLimit) {
            BigDecimal.ZERO
        } else {
            when (selectedLimitPreset) {
                "NO_LIMIT" -> BigDecimal.ZERO
                "5000" -> BigDecimal("5000")
                "10000" -> BigDecimal("10000")
                else -> creditLimitInput.trim().toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
            }
        }
    }

    val availableTags = listOf("Self", "Father", "Home", "Work", "WhatsApp", "Other")
    val nextTagSuggestions = listOf("Father", "Home", "Work", "Other")

    val existingCustomersFlow = remember {
        try {
            com.example.PashuAaharApp.instance.repository.allCustomers
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }
    val existingCustomers by androidx.compose.runtime.produceState(
        initialValue = emptyList<Customer>(),
        key1 = existingCustomersFlow
    ) {
        existingCustomersFlow.collect { value = it }
    }

    val savedVillageEntries = remember(existingCustomers) {
        val grouped = LinkedHashMap<String, Pair<String, Int>>()
        existingCustomers
            .filter { !it.isDeleted }
            .forEach { c ->
                val clean = c.village.trim().replace(Regex("\\s+"), " ")
                if (clean.isNotBlank() && !clean.equals("Walk-in", ignoreCase = true) && !clean.equals("Counter Cash", ignoreCase = true)) {
                    val key = clean.lowercase()
                    val prev = grouped[key]
                    if (prev == null) {
                        grouped[key] = clean to 1
                    } else {
                        grouped[key] = prev.first to (prev.second + 1)
                    }
                }
            }
        grouped.values.sortedByDescending { it.second }
    }

    val filteredVillageHints = remember(savedVillageEntries, village) {
        val query = village.trim()
        if (query.isEmpty() || savedVillageEntries.any { it.first.equals(query, ignoreCase = true) }) {
            savedVillageEntries
        } else {
            val matching = savedVillageEntries.filter { it.first.contains(query, ignoreCase = true) }
            if (matching.isNotEmpty()) matching else savedVillageEntries
        }
    }

    val similarCustomers = remember(existingCustomers, name, village) {
        val qName = name.trim().replace(Regex("\\s+"), " ")
        val qVillage = village.trim().replace(Regex("\\s+"), " ")
        if (qName.length < 2) {
            emptyList()
        } else {
            val nameMatches = existingCustomers.filter { !it.isDeleted }.filter { c ->
                val cName = c.name.trim()
                cName.equals(qName, ignoreCase = true) ||
                    cName.startsWith("$qName ", ignoreCase = true) ||
                    qName.startsWith("$cName ", ignoreCase = true)
            }
            if (qVillage.isNotBlank()) {
                val sameVillage = nameMatches.filter { c ->
                    c.village.trim().equals(qVillage, ignoreCase = true) ||
                        c.village.trim().contains(qVillage, ignoreCase = true)
                }
                if (sameVillage.isNotEmpty()) sameVillage else nameMatches
            } else {
                nameMatches
            }
        }
    }

    val similarSummaryBanner = remember(similarCustomers, fatherName) {
        if (similarCustomers.isEmpty()) {
            null
        } else {
            val first = similarCustomers.first()
            val firstIdentity = buildString {
                append(first.name.trim())
                if (first.fatherName.isNotBlank()) append(" s/o ${first.fatherName.trim()}")
                if (first.village.isNotBlank()) append(", ${first.village.trim()}")
            }
            val count = similarCustomers.size
            val base = if (count == 1) {
                "1 similar customer: $firstIdentity"
            } else {
                "$count similar customers: $firstIdentity +${count - 1} more"
            }
            if (fatherName.isBlank()) "$base · Add father's name?" else base
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 18.dp),
            shape = RoundedCornerShape(24.dp),
            color = FlatDarkCard,
            border = BorderStroke(1.dp, FlatDarkBorder),
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                // Header: Title + X button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("Add customer", "नया ग्राहक जोड़ें"),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary
                    )
                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        color = FlatDarkInput,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("close_add_customer_dialog_btn")
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

                // Scrollable Form Fields
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. CUSTOMER NAME * + FATHER'S NAME (side by side)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = tr("Customer name *", "ग्राहक का नाम *"),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FlatTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                placeholder = {
                                    Text(
                                        tr("Customer name", "ग्राहक का नाम"),
                                        color = FlatTextMuted,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = FlatDarkBg,
                                    unfocusedContainerColor = FlatDarkBg,
                                    focusedBorderColor = DialogBrandGreen,
                                    unfocusedBorderColor = FlatDarkBorder,
                                    focusedTextColor = FlatTextPrimary,
                                    unfocusedTextColor = FlatTextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("add_customer_name_input")
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = tr("Father's name", "पिता का नाम"),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FlatTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            OutlinedTextField(
                                value = fatherName,
                                onValueChange = { fatherName = it },
                                placeholder = {
                                    Text(
                                        tr("Father's name", "पिता का नाम"),
                                        color = FlatTextMuted,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = FlatDarkBg,
                                    unfocusedContainerColor = FlatDarkBg,
                                    focusedBorderColor = DialogBrandGreen,
                                    unfocusedBorderColor = if (similarCustomers.isNotEmpty() && fatherName.isBlank()) dupBannerBorder else FlatDarkBorder,
                                    focusedTextColor = FlatTextPrimary,
                                    unfocusedTextColor = FlatTextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("add_customer_father_input")
                            )
                        }
                    }

                    // 2. MOBILE (+ compact Tag dropdown pill & "+ Add another number")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = tr("Mobile", "मोबाइल"),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FlatTextSecondary
                        )

                        phoneEntries.forEachIndexed { idx, entry ->
                            var tagExpanded by remember(idx, phoneEntries.size) { mutableStateOf(false) }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box {
                                    Surface(
                                        onClick = { tagExpanded = true },
                                        shape = RoundedCornerShape(50),
                                        color = if (idx == 0) DialogBrandGreen else FlatDarkInput,
                                        border = if (idx == 0) null else BorderStroke(1.dp, FlatDarkBorder),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("phone_${idx}_tag_dropdown")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = entry.tag.ifBlank { "Self" },
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (idx == 0) Color.White else FlatTextPrimary,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                tint = if (idx == 0) Color.White else FlatTextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = tagExpanded,
                                        onDismissRequest = { tagExpanded = false }
                                    ) {
                                        availableTags.forEach { tagOption ->
                                            DropdownMenuItem(
                                                text = { Text(tagOption) },
                                                onClick = {
                                                    phoneEntries[idx] = entry.copy(tag = tagOption)
                                                    tagExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = entry.number,
                                    onValueChange = { raw ->
                                        val cleaned = raw.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(15)
                                        phoneEntries[idx] = entry.copy(number = cleaned)
                                    },
                                    placeholder = {
                                        Text(
                                            "+91 98765 43210",
                                            color = FlatTextMuted,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = FlatDarkBg,
                                        unfocusedContainerColor = FlatDarkBg,
                                        focusedBorderColor = DialogBrandGreen,
                                        unfocusedBorderColor = FlatDarkBorder,
                                        focusedTextColor = FlatTextPrimary,
                                        unfocusedTextColor = FlatTextPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("add_customer_phone_input_$idx")
                                )

                                if (phoneEntries.size > 1 && idx > 0) {
                                    Surface(
                                        onClick = { phoneEntries.removeAt(idx) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = FlatDarkInput,
                                        border = BorderStroke(1.dp, FlatDarkBorder),
                                        modifier = Modifier
                                            .size(44.dp)
                                            .testTag("remove_phone_btn_$idx")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = tr("Remove number", "नंबर हटाएं"),
                                                tint = FlatTextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            text = tr(
                                "+ Add another number (Father, Home, Work...)",
                                "+ एक और नंबर जोड़ें (पिता, घर, काम...)"
                            ),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DialogBrandGreen,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val nextTag = nextTagSuggestions.getOrElse((phoneEntries.size - 1).coerceAtLeast(0)) { "Other" }
                                    phoneEntries.add(TaggedMobileNumber(number = "", tag = nextTag))
                                }
                                .padding(vertical = 2.dp)
                                .testTag("add_another_phone_btn")
                        )
                    }

                    // 3. VILLAGE / AREA (+ Saved chips + Similar customer duplicate check)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = tr("Village / area", "गाँव / क्षेत्र"),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FlatTextSecondary
                        )
                        OutlinedTextField(
                            value = village,
                            onValueChange = { village = it },
                            placeholder = {
                                Text(
                                    tr("Enter or select saved village", "गाँव / क्षेत्र का नाम लिखें"),
                                    color = FlatTextMuted,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingIcon = {
                                if (village.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = tr("Clear village", "गाँव साफ़ करें"),
                                        tint = FlatTextSecondary,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { village = "" }
                                    )
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = FlatDarkBg,
                                unfocusedContainerColor = FlatDarkBg,
                                focusedBorderColor = DialogBrandGreen,
                                unfocusedBorderColor = FlatDarkBorder,
                                focusedTextColor = FlatTextPrimary,
                                unfocusedTextColor = FlatTextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_customer_village_input")
                        )

                        if (filteredVillageHints.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .testTag("village_hints_row"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = tr("Saved:", "सेव:"),
                                    fontSize = 12.sp,
                                    color = FlatTextSecondary
                                )
                                filteredVillageHints.forEach { (hintVillage, count) ->
                                    val isSelectedVillage = village.trim().replace(Regex("\\s+"), " ")
                                        .equals(hintVillage, ignoreCase = true)
                                    Surface(
                                        onClick = { village = hintVillage },
                                        shape = RoundedCornerShape(50),
                                        color = if (isSelectedVillage) DialogBrandGreen else FlatDarkInput,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelectedVillage) DialogBrandGreen else FlatDarkBorder
                                        ),
                                        modifier = Modifier.testTag("village_hint_${hintVillage.lowercase().replace(" ", "_")}")
                                    ) {
                                        Text(
                                            text = "$hintVillage · $count",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelectedVillage) Color.White else FlatTextPrimary,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (similarSummaryBanner != null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("quick_add_similar_duplicate_banner"),
                                shape = RoundedCornerShape(12.dp),
                                color = dupBannerBg,
                                border = BorderStroke(1.dp, dupBannerBorder)
                            ) {
                                Text(
                                    text = similarSummaryBanner,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = dupBannerText,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                                )
                            }
                        }
                    }

                    // 4. CREDIT LIMIT CARD (with Auto-block inside)
                    if (canEditCreditLimit) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = FlatDarkBg,
                            border = BorderStroke(1.dp, FlatDarkBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = tr("Credit limit", "उधारी सीमा"),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        "NO_LIMIT" to tr("No limit", "कोई सीमा नहीं"),
                                        "5000" to "₹5,000",
                                        "10000" to "₹10,000",
                                        "CUSTOM" to tr("Custom", "कस्टम")
                                    ).forEach { (key, label) ->
                                        val selected = selectedLimitPreset == key
                                        Surface(
                                            onClick = { selectedLimitPreset = key },
                                            shape = RoundedCornerShape(50),
                                            color = if (selected) DialogBrandGreen else FlatDarkInput
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selected) Color.White else FlatTextPrimary,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }

                                if (selectedLimitPreset == "CUSTOM") {
                                    OutlinedTextField(
                                        value = creditLimitInput,
                                        onValueChange = { next ->
                                            if (next.all { it.isDigit() }) creditLimitInput = next
                                        },
                                        placeholder = { Text("₹ 15000", color = FlatTextMuted) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = FlatDarkCard,
                                            unfocusedContainerColor = FlatDarkCard,
                                            focusedBorderColor = DialogBrandGreen,
                                            unfocusedBorderColor = FlatDarkBorder,
                                            focusedTextColor = FlatTextPrimary,
                                            unfocusedTextColor = FlatTextPrimary
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("add_customer_credit_limit_input")
                                    )
                                }

                                HorizontalDivider(color = FlatDarkBorder)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val autoLabel = if (effectiveLimit > BigDecimal.ZERO) {
                                        tr(
                                            "Auto-block at ${CurrencyUtils.formatInr(effectiveLimit)}",
                                            "${CurrencyUtils.formatInr(effectiveLimit)} पर ऑटो-ब्लॉक"
                                        )
                                    } else {
                                        tr("Auto-block at limit", "सीमा पर ऑटो-ब्लॉक")
                                    }
                                    Text(
                                        text = autoLabel,
                                        fontSize = 13.5.sp,
                                        color = if (effectiveLimit > BigDecimal.ZERO) FlatTextPrimary else FlatTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    )
                                    Switch(
                                        checked = autoBlockAtLimit && effectiveLimit > BigDecimal.ZERO,
                                        enabled = effectiveLimit > BigDecimal.ZERO,
                                        onCheckedChange = { autoBlockAtLimit = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = DialogBrandGreen,
                                            uncheckedThumbColor = Color.White,
                                            uncheckedTrackColor = kw(0xFFD1D5DB)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // 5. COLLAPSIBLE MORE DETAILS (Reminders · Notes)
                    val dashedBorderColor = FlatDarkBorder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .drawBehind {
                                drawRoundRect(
                                    color = dashedBorderColor,
                                    style = Stroke(
                                        width = 1.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                                    ),
                                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                                )
                            }
                            .clickable { showMoreDetails = !showMoreDetails }
                            .padding(horizontal = 14.dp, vertical = 13.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("More details", "अतिरिक्त विवरण"),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = tr(
                                    "Reminders · Notes ${if (showMoreDetails) "▾" else "▸"}",
                                    "रिमाइंडर · नोट्स ${if (showMoreDetails) "▾" else "▸"}"
                                ),
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    AnimatedVisibility(visible = showMoreDetails) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = FlatDarkInput,
                                border = BorderStroke(1.dp, FlatDarkBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp)
                                ) {
                                    listOf("WhatsApp", "SMS", "Both").forEach { ch ->
                                        val selected = reminderChannel.equals(ch, ignoreCase = true)
                                        Surface(
                                            onClick = { reminderChannel = ch },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp),
                                            shape = RoundedCornerShape(9.dp),
                                            color = if (selected) DialogBrandGreen else Color.Transparent
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = ch,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (selected) Color.White else FlatTextPrimary,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = notesInput,
                                onValueChange = { notesInput = it },
                                placeholder = {
                                    Text(
                                        tr("Notes (e.g. Pays after crop sale)", "नोट्स (जैसे फसल बिकने के बाद भुगतान)"),
                                        color = FlatTextMuted,
                                        fontSize = 13.5.sp
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = FlatDarkBg,
                                    unfocusedContainerColor = FlatDarkBg,
                                    focusedBorderColor = DialogBrandGreen,
                                    unfocusedBorderColor = FlatDarkBorder,
                                    focusedTextColor = FlatTextPrimary,
                                    unfocusedTextColor = FlatTextPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action Buttons: Cancel | Save customer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(0.65f)
                            .testTag("cancel_add_customer_btn")
                    ) {
                        Text(
                            text = tr("Cancel", "रद्द करें"),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = FlatTextPrimary,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = {
                            val trimmedName = name.trim()
                            if (trimmedName.isBlank()) return@Button
                            val validPhones = phoneEntries
                                .map { it.copy(number = it.number.trim()) }
                                .filter { it.number.isNotBlank() }
                            val primaryPhone = validPhones.firstOrNull()?.number ?: ""
                            val metaJson = Customer.serializeCustomerMeta(
                                phones = validPhones,
                                autoBlockAtLimit = autoBlockAtLimit,
                                blockUdhariNow = false,
                                reminderChannel = reminderChannel,
                                notes = notesInput.trim()
                            )

                            val normalizedVillage = village.trim().replace(Regex("\\s+"), " ")
                            val canonicalVillage = savedVillageEntries
                                .firstOrNull { it.first.equals(normalizedVillage, ignoreCase = true) }
                                ?.first ?: normalizedVillage

                            onCustomerAdded(
                                Customer(
                                    name = trimmedName,
                                    fatherName = fatherName.trim(),
                                    phone = primaryPhone,
                                    phonesJson = metaJson,
                                    village = canonicalVillage,
                                    creditLimit = effectiveLimit,
                                    createdByUserId = currentUserId
                                )
                            )
                        },
                        enabled = name.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DialogBrandGreen,
                            contentColor = Color.White,
                            disabledContainerColor = DialogBrandGreen.copy(alpha = 0.4f),
                            disabledContentColor = Color.White.copy(alpha = 0.7f)
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("save_customer_btn")
                    ) {
                        Text(
                            text = tr("Save customer", "ग्राहक सेव करें"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
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

