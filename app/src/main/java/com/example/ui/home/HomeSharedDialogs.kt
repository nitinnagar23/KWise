package com.example.ui.home

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

private val DialogBrandGreen = Color(0xFF0E6F43)
private val DialogMintBadgeBg = Color(0xFFE1F5EB)

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
                            text = selectedCustomer?.name ?: tr("Select Customer", "ग्राहक चुनें"),
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
                            text = selectedProduct?.feedName ?: tr("Select Product", "सामान चुनें"),
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
    var name by remember { mutableStateOf("") }
    var fatherName by remember { mutableStateOf("") }
    val phoneEntries = remember {
        mutableStateListOf(TaggedMobileNumber(number = "", tag = "Self"))
    }
    var village by remember { mutableStateOf("") }
    var creditLimitInput by remember { mutableStateOf("0") }
    val canEditCreditLimit = can(currentUserRole, Permission.EDIT_CREDIT_LIMIT)

    val availableTags = listOf("Self", "Father", "Home", "Work", "WhatsApp", "Other")

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

    val allVillageHints = remember(existingCustomers) {
        val fromCustomers = existingCustomers
            .map { it.village.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
        val defaultVillages = listOf(
            "Rampur",
            "Kishanpur",
            "Sultanpur",
            "Govindpur",
            "Madhopur",
            "Chandpur",
            "Fatehpur",
            "Nawabganj"
        )
        (fromCustomers + defaultVillages).distinctBy { it.lowercase() }
    }

    val filteredVillageHints = remember(allVillageHints, village) {
        val query = village.trim()
        if (query.isEmpty() || allVillageHints.any { it.equals(query, ignoreCase = true) }) {
            allVillageHints.take(8)
        } else {
            val matching = allVillageHints.filter { it.contains(query, ignoreCase = true) }
            if (matching.isNotEmpty()) matching.take(8) else allVillageHints.take(6)
        }
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
                // Header: Title + X button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("Add New Customer", "नया ग्राहक जोड़ें"),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
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
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. CUSTOMER NAME *
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = tr("CUSTOMER NAME *", "ग्राहक का नाम *"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = {
                                Text(
                                    tr("Customer name", "ग्राहक का नाम"),
                                    color = FlatTextMuted,
                                    fontSize = 14.5.sp
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
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
                                .testTag("add_customer_name_input")
                        )
                    }

                    // 2. FATHER'S NAME
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = tr("FATHER'S NAME", "पिता का नाम"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        OutlinedTextField(
                            value = fatherName,
                            onValueChange = { fatherName = it },
                            placeholder = {
                                Text(
                                    tr("Father's name (optional)", "पिता का नाम (वैकल्पिक)"),
                                    color = FlatTextMuted,
                                    fontSize = 14.5.sp
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
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
                                .testTag("add_customer_father_input")
                        )
                    }

                    // 3. MOBILE NUMBERS with Tag chips
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "${phoneEntries.size} ${tr("added", "जोड़े गए")}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = FlatTextSecondary
                            )
                        }

                        phoneEntries.forEachIndexed { idx, entry ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = FlatDarkBg,
                                border = BorderStroke(1.dp, FlatDarkBorder)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
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
                                                    fontSize = 14.5.sp
                                                )
                                            },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
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
                                                .weight(1f)
                                                .testTag("add_customer_phone_input_$idx")
                                        )

                                        if (phoneEntries.size > 1) {
                                            Surface(
                                                onClick = { phoneEntries.removeAt(idx) },
                                                shape = RoundedCornerShape(12.dp),
                                                color = FlatDarkCard,
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

                                    // Tag row + Primary badge on first phone
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = tr("Tag", "टैग"),
                                            fontSize = 12.sp,
                                            color = FlatTextSecondary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (idx == 0) {
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = DialogMintBadgeBg
                                            ) {
                                                Text(
                                                    text = tr("Primary", "मुख्य"),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = DialogBrandGreen,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        availableTags.forEach { tagOption ->
                                            val isSelectedTag = entry.tag.equals(tagOption, ignoreCase = true)
                                            Surface(
                                                onClick = {
                                                    phoneEntries[idx] = entry.copy(tag = tagOption)
                                                },
                                                shape = RoundedCornerShape(50),
                                                color = if (isSelectedTag) DialogBrandGreen else FlatDarkInput,
                                                modifier = Modifier.testTag("phone_${idx}_tag_${tagOption.lowercase()}")
                                            ) {
                                                Text(
                                                    text = tagOption,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelectedTag) Color.White else FlatTextPrimary,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Dashed "+ Add another number" button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .drawBehind {
                                    drawRoundRect(
                                        color = DialogBrandGreen,
                                        style = Stroke(
                                            width = 1.5.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                                        ),
                                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                                    )
                                }
                                .clickable {
                                    val nextDefaultTag = if (phoneEntries.size == 1) "Father" else "Other"
                                    phoneEntries.add(TaggedMobileNumber(number = "", tag = nextDefaultTag))
                                }
                                .testTag("add_another_phone_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+ ${tr("Add another number", "एक और नंबर जोड़ें")}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = DialogBrandGreen
                            )
                        }
                    }

                    // 4. VILLAGE / AREA (with Village Hints + Free Typing)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("VILLAGE / AREA", "गाँव / क्षेत्र"),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = tr("Type or tap a village hint", "टाइप करें या गाँव चुनें"),
                                fontSize = 11.sp,
                                color = FlatTextMuted
                            )
                        }
                        OutlinedTextField(
                            value = village,
                            onValueChange = { village = it },
                            placeholder = {
                                Text(
                                    tr("Type village / area name...", "गाँव / क्षेत्र का नाम लिखें..."),
                                    color = FlatTextMuted,
                                    fontSize = 14.5.sp
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
                                focusedContainerColor = FlatDarkCard,
                                unfocusedContainerColor = FlatDarkCard,
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
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                                    .testTag("village_hints_row"),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                filteredVillageHints.forEach { hintVillage ->
                                    val isSelectedVillage = village.trim().equals(hintVillage, ignoreCase = true)
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
                                            text = hintVillage,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelectedVillage) Color.White else FlatTextPrimary,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. CREDIT LIMIT (₹, 0 = NO LIMIT)
                    if (canEditCreditLimit) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = tr("CREDIT LIMIT (₹, 0 = NO LIMIT)", "उधारी सीमा (₹, 0 = कोई सीमा नहीं)"),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            OutlinedTextField(
                                value = creditLimitInput,
                                onValueChange = { creditLimitInput = it },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
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
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Action Buttons: Cancel | Save Customer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        contentPadding = PaddingValues(vertical = 13.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cancel_add_customer_btn")
                    ) {
                        Text(
                            text = tr("Cancel", "रद्द करें"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = FlatTextPrimary
                        )
                    }

                    Button(
                        onClick = {
                            val trimmedName = name.trim()
                            if (trimmedName.isBlank()) return@Button
                            val limit = if (canEditCreditLimit) {
                                creditLimitInput.trim().toBigDecimalOrNull() ?: BigDecimal.ZERO
                            } else {
                                BigDecimal.ZERO
                            }
                            val validPhones = phoneEntries
                                .map { it.copy(number = it.number.trim()) }
                                .filter { it.number.isNotBlank() }
                            val primaryPhone = validPhones.firstOrNull()?.number ?: ""
                            val serializedPhones = Customer.serializeTaggedPhones(validPhones)

                            onCustomerAdded(
                                Customer(
                                    name = trimmedName,
                                    fatherName = fatherName.trim(),
                                    phone = primaryPhone,
                                    phonesJson = serializedPhones,
                                    village = village.trim(),
                                    creditLimit = limit,
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
                        contentPadding = PaddingValues(vertical = 13.dp),
                        modifier = Modifier
                            .weight(1.25f)
                            .testTag("save_customer_btn")
                    ) {
                        Text(
                            text = tr("Save Customer", "सेव करें"),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

