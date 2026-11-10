package com.example.ui.ledger

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.model.Customer
import com.example.model.Role
import com.example.model.SyncStatus
import com.example.model.TaggedMobileNumber
import com.example.util.CurrencyUtils
import com.example.util.tr
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.math.BigDecimal
import java.util.UUID

private val EditPageBg = Color(0xFFF6F7F9)
private val EditCardBorder = Color(0xFFE2E8F0)
private val EditNavyText = Color(0xFF0F172A)
private val EditSubtleText = Color(0xFF64748B)
private val EditBrandGreen = Color(0xFF0E6F43)
private val EditSoftGreen = Color(0xFFDDF5E7)
private val EditCrimsonRed = Color(0xFFB91C1C)
private val EditSoftRed = Color(0xFFFFF1F2)

// ============================================================================
// PAGE 8: ADD OR EDIT CUSTOMER SCREEN
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddOrEditCustomerScreen(
    existingCustomer: Customer? = null,
    currentUserId: String,
    currentUserRole: Role?,
    onCancel: () -> Unit,
    onSaveCustomer: (Customer) -> Unit,
    onRequestDeleteCustomer: (() -> Unit)? = null
) {
    BackHandler { onCancel() }

    val isEdit = existingCustomer != null
    var nameInput by remember(existingCustomer) { mutableStateOf(existingCustomer?.name.orEmpty()) }
    var fatherNameInput by remember(existingCustomer) { mutableStateOf(existingCustomer?.fatherName.orEmpty()) }
    var villageInput by remember(existingCustomer) { mutableStateOf(existingCustomer?.village.orEmpty()) }

    val mobileEntries = remember(existingCustomer) {
        val initial = existingCustomer?.getTaggedPhones()?.takeIf { it.isNotEmpty() }
            ?: listOf(TaggedMobileNumber("", "Self"))
        mutableStateListOf<TaggedMobileNumber>().apply { addAll(initial) }
    }

    val initialLimit = existingCustomer?.creditLimit ?: BigDecimal("10000")
    var selectedLimitPreset by remember(existingCustomer) {
        val preset = when {
            initialLimit.compareTo(BigDecimal.ZERO) == 0 -> "NO_LIMIT"
            initialLimit.compareTo(BigDecimal("5000")) == 0 -> "5000"
            initialLimit.compareTo(BigDecimal("10000")) == 0 -> "10000"
            initialLimit.compareTo(BigDecimal("25000")) == 0 -> "25000"
            else -> "CUSTOM"
        }
        mutableStateOf(preset)
    }
    var customLimitInput by remember(existingCustomer) {
        mutableStateOf(if (initialLimit > BigDecimal.ZERO) initialLimit.stripTrailingZeros().toPlainString() else "")
    }

    val effectiveLimit = remember(selectedLimitPreset, customLimitInput) {
        when (selectedLimitPreset) {
            "NO_LIMIT" -> BigDecimal.ZERO
            "5000" -> BigDecimal("5000")
            "10000" -> BigDecimal("10000")
            "25000" -> BigDecimal("25000")
            else -> customLimitInput.trim().toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
        }
    }

    var autoBlockAtLimit by remember(existingCustomer) {
        mutableStateOf(existingCustomer?.getAutoBlockAtLimit() ?: true)
    }
    var blockUdhariNow by remember(existingCustomer) {
        mutableStateOf(existingCustomer?.getBlockUdhariNow() ?: false)
    }
    var reminderChannel by remember(existingCustomer) {
        mutableStateOf(existingCustomer?.getReminderChannel() ?: "WhatsApp")
    }
    var notesInput by remember(existingCustomer) {
        mutableStateOf(existingCustomer?.getNotes().orEmpty())
    }

    val currentUdhari = existingCustomer?.totalUdhari ?: BigDecimal.ZERO
    val phoneTags = listOf("Self", "Father", "Home", "Work", "WhatsApp", "Other")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EditPageBg)
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
                    .testTag("edit_customer_back_btn"),
                shape = RoundedCornerShape(13.dp),
                color = Color.White,
                border = BorderStroke(1.dp, EditCardBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = EditNavyText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column {
                Text(
                    text = if (isEdit) tr("Edit customer", "ग्राहक संपादित करें") else tr("Add customer", "नया ग्राहक जोड़ें"),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = EditNavyText
                )
                if (nameInput.isNotBlank()) {
                    Text(
                        text = nameInput,
                        fontSize = 13.sp,
                        color = EditSubtleText
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // CUSTOMER NAME *
            CustomerFormInputSection(
                label = tr("CUSTOMER NAME *", "ग्राहक का नाम *"),
                value = nameInput,
                onValueChange = { nameInput = it },
                placeholder = tr("Enter customer name", "ग्राहक का नाम लिखें"),
                testTag = "customer_form_name_input"
            )

            // FATHER'S NAME
            CustomerFormInputSection(
                label = tr("FATHER'S NAME", "पिता का नाम"),
                value = fatherNameInput,
                onValueChange = { fatherNameInput = it },
                placeholder = tr("Father's name (optional)", "पिता का नाम (वैकल्पिक)"),
                testTag = "customer_form_father_input"
            )

            // MOBILE NUMBERS
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
                        color = EditSubtleText,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = "${mobileEntries.count { it.number.isNotBlank() }} ${tr("added", "जोड़े गए")}",
                        fontSize = 12.5.sp,
                        color = EditSubtleText
                    )
                }

                mobileEntries.forEachIndexed { idx, entry ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, EditCardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "+91 ",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = EditSubtleText
                                        )
                                        BasicTextField(
                                            value = entry.number,
                                            onValueChange = { next ->
                                                val digits = next.filter { it.isDigit() || it == ' ' }.take(12)
                                                mobileEntries[idx] = entry.copy(number = digits)
                                            },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                            textStyle = TextStyle(
                                                fontSize = 15.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = EditNavyText
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("customer_phone_input_$idx")
                                        )
                                    }
                                }

                                if (mobileEntries.size > 1 && idx > 0) {
                                    Surface(
                                        onClick = { mobileEntries.removeAt(idx) },
                                        modifier = Modifier.size(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFF8FAFC),
                                        border = BorderStroke(1.dp, EditCardBorder)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove number",
                                                tint = EditNavyText,
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
                                    text = tr("Tag", "टैग"),
                                    fontSize = 12.5.sp,
                                    color = EditSubtleText
                                )
                                if (idx == 0) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = EditSoftGreen
                                    ) {
                                        Text(
                                            text = tr("Primary", "मुख्य"),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EditBrandGreen,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                phoneTags.forEach { tag ->
                                    val selected = entry.tag.equals(tag, ignoreCase = true)
                                    Surface(
                                        onClick = { mobileEntries[idx] = entry.copy(tag = tag) },
                                        shape = RoundedCornerShape(50),
                                        color = if (selected) EditBrandGreen else Color(0xFFEEF2F6)
                                    ) {
                                        Text(
                                            text = tag,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) Color.White else EditNavyText,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
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
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .clickable {
                            mobileEntries.add(TaggedMobileNumber("", "Father"))
                        }
                        .testTag("add_another_phone_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRoundRect(
                            color = EditBrandGreen,
                            cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                            )
                        )
                    }
                    Text(
                        text = tr("+ Add another number", "+ एक और नंबर जोड़ें"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = EditBrandGreen
                    )
                }
            }

            // VILLAGE / AREA
            CustomerFormInputSection(
                label = tr("VILLAGE / AREA", "गाँव / क्षेत्र"),
                value = villageInput,
                onValueChange = { villageInput = it },
                placeholder = tr("Enter village or area", "गाँव या क्षेत्र लिखें"),
                testTag = "customer_form_village_input"
            )

            // Credit Limit Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                border = BorderStroke(1.dp, EditCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text(
                            text = tr("Credit limit", "उधारी सीमा"),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = EditNavyText
                        )
                        Text(
                            text = tr("Most udhari this customer can have at one time", "यह ग्राहक एक बार में अधिकतम कितनी उधारी रख सकता है"),
                            fontSize = 12.5.sp,
                            color = EditSubtleText
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "NO_LIMIT" to tr("No limit", "कोई सीमा नहीं"),
                            "5000" to "₹5,000",
                            "10000" to "₹10,000",
                            "25000" to "₹25,000",
                            "CUSTOM" to tr("Custom", "कस्टम")
                        ).forEach { (key, label) ->
                            val selected = selectedLimitPreset == key
                            Surface(
                                onClick = { selectedLimitPreset = key },
                                shape = RoundedCornerShape(50),
                                color = if (selected) EditBrandGreen else Color(0xFFEEF2F6)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) Color.White else EditNavyText,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    if (selectedLimitPreset == "CUSTOM") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("₹ ", fontWeight = FontWeight.Bold, color = EditNavyText)
                                BasicTextField(
                                    value = customLimitInput,
                                    onValueChange = { next ->
                                        if (next.all { it.isDigit() }) customLimitInput = next
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    textStyle = TextStyle(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EditNavyText
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    if (effectiveLimit > BigDecimal.ZERO) {
                        val ratio = (currentUdhari.toFloat() / effectiveLimit.toFloat()).coerceIn(0f, 1f)
                        val remaining = effectiveLimit.subtract(currentUdhari).max(BigDecimal.ZERO)
                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(50)),
                            color = if (ratio >= 1f) EditCrimsonRed else EditBrandGreen,
                            trackColor = Color(0xFFE2E8F0),
                            strokeCap = StrokeCap.Round
                        )
                        Text(
                            text = "${CurrencyUtils.formatInr(currentUdhari)} used · ${CurrencyUtils.formatInr(remaining)} left",
                            fontSize = 12.5.sp,
                            color = EditSubtleText
                        )
                    }
                }
            }

            // Auto-block at limit Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                border = BorderStroke(1.dp, EditCardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = tr("Auto-block at limit", "सीमा पूरी होने पर ऑटो-ब्लॉक"),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = EditNavyText
                        )
                        val limStr = if (effectiveLimit > BigDecimal.ZERO) CurrencyUtils.formatInr(effectiveLimit) else "limit"
                        Text(
                            text = tr(
                                "New bills must be paid in full once $limStr is reached",
                                "$limStr पूरी होने पर नए बिलों का पूरा भुगतान करना होगा"
                            ),
                            fontSize = 12.5.sp,
                            color = EditSubtleText,
                            lineHeight = 18.sp
                        )
                    }
                    Switch(
                        checked = autoBlockAtLimit,
                        onCheckedChange = { autoBlockAtLimit = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = EditBrandGreen
                        )
                    )
                }
            }

            // Block udhari now Card (Soft Red)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = EditSoftRed,
                border = BorderStroke(1.dp, Color(0xFFFECDD3))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = tr("Block udhari now", "अभी उधारी बंद करें"),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = EditCrimsonRed
                        )
                        Text(
                            text = tr(
                                "New bills must be paid in full. Existing udhari stays and can still be collected.",
                                "नए बिलों का पूरा भुगतान करना होगा। पुरानी उधारी बनी रहेगी और वसूली जा सकेगी।"
                            ),
                            fontSize = 12.5.sp,
                            color = EditSubtleText,
                            lineHeight = 18.sp
                        )
                    }
                    Switch(
                        checked = blockUdhariNow,
                        onCheckedChange = { blockUdhariNow = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = EditCrimsonRed
                        )
                    )
                }
            }

            // REMINDERS
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = tr("REMINDERS", "रिमाइंडर"),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = EditSubtleText,
                    letterSpacing = 0.6.sp
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFEEF2F6),
                    border = BorderStroke(1.dp, EditCardBorder)
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
                                    .height(44.dp),
                                shape = RoundedCornerShape(11.dp),
                                color = if (selected) EditBrandGreen else Color.Transparent
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = ch,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.White else EditNavyText
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // NOTES
            CustomerFormInputSection(
                label = tr("NOTES", "नोट्स"),
                value = notesInput,
                onValueChange = { notesInput = it },
                placeholder = tr("Pays after crop sale", "फसल बिकने के बाद भुगतान करता है"),
                testTag = "customer_form_notes_input"
            )

            // Delete Customer Section (when editing)
            if (isEdit && onRequestDeleteCustomer != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TextButton(
                        onClick = onRequestDeleteCustomer,
                        modifier = Modifier.testTag("edit_customer_delete_btn")
                    ) {
                        Text(
                            text = tr("Delete customer", "ग्राहक हटाएं"),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = EditCrimsonRed
                        )
                    }
                    if (currentUdhari > BigDecimal.ZERO) {
                        Text(
                            text = tr(
                                "Can't delete while ${CurrencyUtils.formatInr(currentUdhari)} udhari is pending.",
                                "${CurrencyUtils.formatInr(currentUdhari)} उधारी बाकी होने पर हटाया नहीं जा सकता।"
                            ),
                            fontSize = 12.5.sp,
                            color = EditSubtleText,
                            textAlign = TextAlign.Center
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
            border = BorderStroke(1.dp, EditCardBorder)
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
                            fontWeight = FontWeight.Bold,
                            color = EditNavyText
                        )
                    }
                }

                Button(
                    onClick = {
                        val trimmedName = nameInput.trim()
                        if (trimmedName.isBlank()) return@Button
                        val cleanPhones = mobileEntries
                            .map { it.copy(number = it.number.trim()) }
                            .filter { it.number.isNotBlank() }
                        val primaryPhone = cleanPhones.firstOrNull()?.number.orEmpty()
                        val metaJson = Customer.serializeCustomerMeta(
                            phones = cleanPhones,
                            autoBlockAtLimit = autoBlockAtLimit,
                            blockUdhariNow = blockUdhariNow,
                            reminderChannel = reminderChannel,
                            notes = notesInput.trim(),
                            promisedAmount = existingCustomer?.getPromisedAmount(),
                            openingUdhari = existingCustomer?.getOpeningUdhari() ?: BigDecimal.ZERO
                        )
                        val saved = (existingCustomer ?: Customer(
                            id = UUID.randomUUID().toString(),
                            name = trimmedName,
                            createdByUserId = currentUserId
                        )).copy(
                            name = trimmedName,
                            fatherName = fatherNameInput.trim(),
                            phone = primaryPhone,
                            phonesJson = metaJson,
                            village = villageInput.trim(),
                            creditLimit = effectiveLimit,
                            updatedAt = System.currentTimeMillis(),
                            syncStatus = SyncStatus.PENDING
                        )
                        onSaveCustomer(saved)
                    },
                    enabled = nameInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EditBrandGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("customer_form_save_btn")
                ) {
                    Text(
                        text = if (isEdit) tr("Save changes", "बदलाव सेव करें") else tr("Save customer", "ग्राहक सेव करें"),
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomerFormInputSection(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    testTag: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = EditSubtleText,
            letterSpacing = 0.6.sp
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            border = BorderStroke(1.dp, EditCardBorder)
        ) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.padding(horizontal = 14.dp)
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 15.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = EditNavyText
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(testTag)
                )
            }
        }
    }
}

// ============================================================================
// PAGES 9, 10, 11, 12: IMPORT CUSTOMERS FLOW (CONTACTS & EXCEL/CSV)
// ============================================================================

private enum class ImportSubStep {
    HUB,
    PICK_CONTACTS,
    EXCEL_MAPPING,
    IMPORT_SUCCESS
}

data class DeviceContactItem(
    val id: String,
    val name: String,
    val phone: String,
    val alreadyAdded: Boolean
) {
    fun initials(): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> "${parts.first().first().uppercaseChar()}${parts.last().first().uppercaseChar()}"
            parts.size == 1 -> parts.first().take(2).uppercase()
            else -> "C"
        }
    }
}

data class ParsedImportRow(
    val rowNumber: Int,
    val name: String,
    val phone: String,
    val village: String,
    val fatherName: String,
    val openingUdhari: BigDecimal,
    val creditLimit: BigDecimal,
    val status: String // "Ready", "Duplicate", "No name"
)

@Composable
fun ImportCustomersFlowScreen(
    existingCustomers: List<Customer>,
    currentUserId: String,
    onClose: () -> Unit,
    onImportCustomersBatch: (List<Customer>) -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(ImportSubStep.HUB) }

    BackHandler {
        if (currentStep == ImportSubStep.HUB) {
            onClose()
        } else {
            currentStep = ImportSubStep.HUB
        }
    }

    val existingPhoneSet = remember(existingCustomers) {
        existingCustomers
            .filter { !it.isDeleted }
            .flatMap { c ->
                val list = c.getTaggedPhones().map { it.number.filter { ch -> ch.isDigit() }.takeLast(10) }
                val main = c.phone.filter { ch -> ch.isDigit() }.takeLast(10)
                list + main
            }
            .filter { it.isNotBlank() }
            .toSet()
    }

    // Contacts state (Page 10)
    var deviceContacts by remember { mutableStateOf<List<DeviceContactItem>>(emptyList()) }
    var selectedContactIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var contactSearchQuery by remember { mutableStateOf("") }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            deviceContacts = queryRealDeviceContacts(context, existingPhoneSet)
            currentStep = ImportSubStep.PICK_CONTACTS
        } else {
            Toast.makeText(context, "Contacts permission is needed to pick phone contacts", Toast.LENGTH_SHORT).show()
        }
    }

    // Excel / CSV state (Page 11 & 12)
    var selectedFileName by remember { mutableStateOf("customers.csv") }
    var fileHeaders by remember { mutableStateOf<List<String>>(emptyList()) }
    var rawDataRows by remember { mutableStateOf<List<List<String>>>(emptyList()) }

    var nameColIndex by remember { mutableIntStateOf(0) }
    var mobileColIndex by remember { mutableIntStateOf(1) }
    var villageColIndex by remember { mutableIntStateOf(2) }
    var udhariColIndex by remember { mutableIntStateOf(3) }
    var limitColIndex by remember { mutableIntStateOf(-1) }

    var lastAddedCount by remember { mutableIntStateOf(0) }
    var lastSkippedRows by remember { mutableStateOf<List<ParsedImportRow>>(emptyList()) }
    var lastTotalOpeningUdhari by remember { mutableStateOf(BigDecimal.ZERO) }

    val csvFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val parsed = parseCsvFromUri(context, uri)
            if (parsed.first.isNotEmpty()) {
                selectedFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "customers.csv"
                fileHeaders = parsed.first
                rawDataRows = parsed.second
                nameColIndex = parsed.first.indexOfFirst {
                    it.contains("name", true) && !it.contains("father", true)
                }.takeIf { it >= 0 } ?: 0
                mobileColIndex = parsed.first.indexOfFirst {
                    it.contains("phone", true) || it.contains("mobile", true)
                }.takeIf { it >= 0 } ?: (1.coerceAtMost(parsed.first.lastIndex))
                villageColIndex = parsed.first.indexOfFirst {
                    it.contains("village", true) || it.contains("gaon", true) || it.contains("city", true)
                }.takeIf { it >= 0 } ?: (2.coerceAtMost(parsed.first.lastIndex))
                udhariColIndex = parsed.first.indexOfFirst {
                    it.contains("udhari", true) || it.contains("baki", true) || it.contains("opening", true)
                }.takeIf { it >= 0 } ?: (3.coerceAtMost(parsed.first.lastIndex))
                limitColIndex = parsed.first.indexOfFirst {
                    it.contains("limit", true)
                }
                currentStep = ImportSubStep.EXCEL_MAPPING
            } else {
                Toast.makeText(context, "Could not read columns from selected file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    when (currentStep) {
        ImportSubStep.HUB -> {
            // ================================================================
            // PAGE 9: IMPORT CUSTOMERS HUB
            // ================================================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(EditPageBg)
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
                        onClick = onClose,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("import_customers_back_btn"),
                        shape = RoundedCornerShape(13.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, EditCardBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = EditNavyText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = tr("Import customers", "ग्राहक इम्पोर्ट करें"),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = EditNavyText
                        )
                        Text(
                            text = tr("Add many at once", "एक साथ कई ग्राहक जोड़ें"),
                            fontSize = 13.sp,
                            color = EditSubtleText
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Option 1: From phone contacts
                    Surface(
                        onClick = {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_CONTACTS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                deviceContacts = queryRealDeviceContacts(context, existingPhoneSet)
                                currentStep = ImportSubStep.PICK_CONTACTS
                            } else {
                                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_from_contacts_card"),
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, EditCardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = EditSoftGreen
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.GroupAdd,
                                            contentDescription = null,
                                            tint = EditBrandGreen,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = tr("From phone contacts", "फ़ोन कॉन्टैक्ट्स से"),
                                        fontSize = 16.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EditNavyText
                                    )
                                    Text(
                                        text = tr("Pick people from your contacts", "अपने फ़ोन कॉन्टैक्ट्स से लोगों को चुनें"),
                                        fontSize = 13.sp,
                                        color = EditSubtleText
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = EditSubtleText
                            )
                        }
                    }

                    // Option 2: From Excel or CSV
                    Surface(
                        onClick = { csvFileLauncher.launch("*/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_from_excel_card"),
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, EditCardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFFDBEAFE)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.Description,
                                            contentDescription = null,
                                            tint = Color(0xFF1D4ED8),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = tr("From Excel or CSV", "Excel या CSV से"),
                                        fontSize = 16.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EditNavyText
                                    )
                                    Text(
                                        text = tr("Bring your old khata list with opening udhari", "पुरानी उधारी के साथ अपनी खाता सूची लाएं"),
                                        fontSize = 13.sp,
                                        color = EditSubtleText
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = EditSubtleText
                            )
                        }
                    }

                    // Using Excel? Info Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, EditCardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = tr("Using Excel?", "Excel का उपयोग कर रहे हैं?"),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = EditNavyText
                            )
                            Text(
                                text = tr(
                                    "Columns we can read: Name, Mobile, Village, Father's name, Credit limit and Opening udhari.",
                                    "कॉलम जो हम पढ़ सकते हैं: Name, Mobile, Village, Father's name, Credit limit और Opening udhari."
                                ),
                                fontSize = 13.5.sp,
                                color = EditSubtleText,
                                lineHeight = 19.sp
                            )
                            Surface(
                                onClick = { shareSampleCustomerCsvTemplate(context) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("download_sample_template_btn"),
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White,
                                border = BorderStroke(1.5.dp, EditBrandGreen)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = tr("Download sample template", "सैंपल टेम्पलेट डाउनलोड करें"),
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EditBrandGreen
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = tr(
                            "Duplicates are matched by mobile number and skipped.",
                            "डुप्लिकेट ग्राहकों को मोबाइल नंबर से पहचान कर छोड़ दिया जाता है।"
                        ),
                        fontSize = 12.5.sp,
                        color = EditSubtleText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }
            }
        }

        ImportSubStep.PICK_CONTACTS -> {
            // ================================================================
            // PAGE 10: PICK CONTACTS SCREEN
            // ================================================================
            val filteredContacts = remember(deviceContacts, contactSearchQuery) {
                if (contactSearchQuery.isBlank()) deviceContacts
                else deviceContacts.filter {
                    it.name.contains(contactSearchQuery, ignoreCase = true) ||
                        it.phone.contains(contactSearchQuery, ignoreCase = true)
                }
            }
            val selectableContacts = remember(filteredContacts) {
                filteredContacts.filter { !it.alreadyAdded }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(EditPageBg)
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
                        onClick = { currentStep = ImportSubStep.HUB },
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(13.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, EditCardBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = EditNavyText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = tr("Pick contacts", "कॉन्टैक्ट्स चुनें"),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = EditNavyText
                        )
                        Text(
                            text = tr("Select who to add", "चुनें कि किसे जोड़ना है"),
                            fontSize = 13.sp,
                            color = EditSubtleText
                        )
                    }
                }

                // Search bar
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, EditCardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = EditSubtleText,
                            modifier = Modifier.size(20.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            if (contactSearchQuery.isEmpty()) {
                                Text(
                                    text = tr("Search contacts...", "कॉन्टैक्ट्स खोजें..."),
                                    fontSize = 14.5.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            BasicTextField(
                                value = contactSearchQuery,
                                onValueChange = { contactSearchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 15.sp, color = EditNavyText),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // "<N> selected of <Total>" + "Select all"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedContactIds.size} selected of ${filteredContacts.size}",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = EditNavyText
                    )
                    Text(
                        text = tr("Select all", "सभी चुनें"),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = EditBrandGreen,
                        modifier = Modifier.clickable {
                            selectedContactIds = if (selectedContactIds.size == selectableContacts.size) {
                                emptySet()
                            } else {
                                selectableContacts.map { it.id }.toSet()
                            }
                        }
                    )
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, EditCardBorder)
                ) {
                    if (filteredContacts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tr("No phone contacts found on this device.", "इस फ़ोन में कोई कॉन्टैक्ट नहीं मिला।"),
                                fontSize = 14.sp,
                                color = EditSubtleText,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filteredContacts, key = { it.id }) { item ->
                                val isChecked = selectedContactIds.contains(item.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !item.alreadyAdded) {
                                            selectedContactIds = if (isChecked) {
                                                selectedContactIds - item.id
                                            } else {
                                                selectedContactIds + item.id
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (!item.alreadyAdded) {
                                        Surface(
                                            modifier = Modifier.size(26.dp),
                                            shape = RoundedCornerShape(7.dp),
                                            color = if (isChecked) EditBrandGreen else Color.White,
                                            border = if (isChecked) null else BorderStroke(1.5.dp, Color(0xFFCBD5E1))
                                        ) {
                                            if (isChecked) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.width(26.dp))
                                    }

                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        shape = CircleShape,
                                        color = Color(0xFFE2E8F0)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = item.initials(),
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = EditNavyText.copy(alpha = if (item.alreadyAdded) 0.5f else 1f)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EditNavyText.copy(alpha = if (item.alreadyAdded) 0.5f else 1f)
                                        )
                                        Text(
                                            text = item.phone,
                                            fontSize = 13.sp,
                                            color = EditSubtleText.copy(alpha = if (item.alreadyAdded) 0.6f else 1f)
                                        )
                                    }

                                    if (item.alreadyAdded) {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = Color(0xFFF1F5F9)
                                        ) {
                                            Text(
                                                text = tr("Already added", "पहले से जुड़ा है"),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = EditSubtleText,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = Color(0xFFF1F5F9))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    border = BorderStroke(1.dp, EditCardBorder)
                ) {
                    Button(
                        onClick = {
                            val chosen = deviceContacts.filter { selectedContactIds.contains(it.id) && !it.alreadyAdded }
                            if (chosen.isNotEmpty()) {
                                val created = chosen.map { c ->
                                    Customer(
                                        id = UUID.randomUUID().toString(),
                                        name = c.name.trim(),
                                        phone = c.phone.trim(),
                                        phonesJson = Customer.serializeCustomerMeta(
                                            phones = listOf(TaggedMobileNumber(c.phone.trim(), "Self"))
                                        ),
                                        createdByUserId = currentUserId,
                                        syncStatus = SyncStatus.PENDING
                                    )
                                }
                                onImportCustomersBatch(created)
                                onClose()
                            }
                        },
                        enabled = selectedContactIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EditBrandGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(52.dp)
                            .testTag("confirm_add_contacts_btn")
                    ) {
                        Text(
                            text = "Add ${selectedContactIds.size} customers",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        ImportSubStep.EXCEL_MAPPING -> {
            // ================================================================
            // PAGE 11: IMPORT FROM EXCEL / CSV COLUMN MATCHING & PREVIEW
            // ================================================================
            val seenPhonesInFile = mutableSetOf<String>()
            val parsedRows = rawDataRows.mapIndexed { idx, cols ->
                val name = cols.getOrNull(nameColIndex)?.trim().orEmpty()
                val phone = cols.getOrNull(mobileColIndex)?.trim().orEmpty()
                val village = cols.getOrNull(villageColIndex)?.trim().orEmpty()
                val udhari = cols.getOrNull(udhariColIndex)?.replace("[^0-9.]".toRegex(), "")?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val limit = if (limitColIndex >= 0) {
                    cols.getOrNull(limitColIndex)?.replace("[^0-9.]".toRegex(), "")?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                } else BigDecimal.ZERO
                val normPhone = phone.filter { it.isDigit() }.takeLast(10)
                val status = when {
                    name.isBlank() -> "No name"
                    normPhone.isNotBlank() && (existingPhoneSet.contains(normPhone) || !seenPhonesInFile.add(normPhone)) -> "Duplicate"
                    else -> "Ready"
                }
                ParsedImportRow(
                    rowNumber = idx + 2,
                    name = name,
                    phone = phone,
                    village = village,
                    fatherName = "",
                    openingUdhari = udhari,
                    creditLimit = limit,
                    status = status
                )
            }

            val readyRows = parsedRows.filter { it.status == "Ready" }
            val duplicateCount = parsedRows.count { it.status == "Duplicate" }
            val noNameCount = parsedRows.count { it.status == "No name" }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(EditPageBg)
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
                        onClick = { currentStep = ImportSubStep.HUB },
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(13.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, EditCardBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = EditNavyText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = tr("Import from Excel", "Excel से इम्पोर्ट करें"),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = EditNavyText
                        )
                        Text(
                            text = "$selectedFileName · ${rawDataRows.size} rows",
                            fontSize = 13.sp,
                            color = EditSubtleText
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
                    // Match your columns card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, EditCardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = tr("Match your columns", "अपने कॉलम मिलाएं"),
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EditNavyText
                                )
                                Text(
                                    text = tr("We guessed these. Change any that are wrong.", "हमने इनका अनुमान लगाया है। जो गलत हों उन्हें बदलें।"),
                                    fontSize = 12.5.sp,
                                    color = EditSubtleText
                                )
                            }

                            ColumnMapperRow("Name *", fileHeaders, nameColIndex, allowNotUsed = false) { nameColIndex = it }
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            ColumnMapperRow("Mobile", fileHeaders, mobileColIndex, allowNotUsed = true) { mobileColIndex = it }
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            ColumnMapperRow("Village", fileHeaders, villageColIndex, allowNotUsed = true) { villageColIndex = it }
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            ColumnMapperRow("Opening udhari", fileHeaders, udhariColIndex, allowNotUsed = true) { udhariColIndex = it }
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            ColumnMapperRow("Credit limit", fileHeaders, limitColIndex, allowNotUsed = true) { limitColIndex = it }
                        }
                    }

                    // Preview Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, EditCardBorder)
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
                                    text = tr("Preview", "प्रीव्यू"),
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EditNavyText
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(shape = RoundedCornerShape(50), color = EditSoftGreen) {
                                        Text(
                                            text = "${readyRows.size} ready",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EditBrandGreen,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                    if (duplicateCount > 0) {
                                        Surface(shape = RoundedCornerShape(50), color = Color(0xFFFEF3C7)) {
                                            Text(
                                                text = "$duplicateCount",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFB45309),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    if (noNameCount > 0) {
                                        Surface(shape = RoundedCornerShape(50), color = Color(0xFFFEE2E2)) {
                                            Text(
                                                text = "$noNameCount",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = EditCrimsonRed,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text("NAME", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EditSubtleText, modifier = Modifier.weight(1.1f))
                                Text("MOBILE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EditSubtleText, modifier = Modifier.weight(1.1f))
                                Text("VILLAGE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EditSubtleText, modifier = Modifier.weight(0.9f))
                                Text("UDHARI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EditSubtleText, textAlign = TextAlign.End, modifier = Modifier.weight(0.8f))
                                Text("STATUS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EditSubtleText, textAlign = TextAlign.End, modifier = Modifier.weight(0.9f))
                            }
                            HorizontalDivider(color = Color(0xFFF1F5F9))

                            parsedRows.take(15).forEach { r ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = r.name.ifBlank { "—" },
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EditNavyText,
                                        modifier = Modifier.weight(1.1f)
                                    )
                                    Text(
                                        text = r.phone,
                                        fontSize = 12.sp,
                                        color = EditNavyText,
                                        modifier = Modifier.weight(1.1f)
                                    )
                                    Text(
                                        text = r.village,
                                        fontSize = 12.sp,
                                        color = EditNavyText,
                                        modifier = Modifier.weight(0.9f)
                                    )
                                    Text(
                                        text = CurrencyUtils.formatInr(r.openingUdhari),
                                        fontSize = 12.sp,
                                        color = EditNavyText,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(0.8f)
                                    )
                                    Box(
                                        modifier = Modifier.weight(0.9f),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        val (bg, fg) = when (r.status) {
                                            "Ready" -> EditSoftGreen to EditBrandGreen
                                            "Duplicate" -> Color(0xFFFEF3C7) to Color(0xFFB45309)
                                            else -> Color(0xFFFEE2E2) to EditCrimsonRed
                                        }
                                        Surface(shape = RoundedCornerShape(50), color = bg) {
                                            Text(
                                                text = r.status,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = fg,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = Color(0xFFF1F5F9))
                            }
                        }
                    }

                    Text(
                        text = tr(
                            "Duplicates and rows with no name are skipped. You can fix them in the file and import again.",
                            "डुप्लिकेट और बिना नाम वाली पंक्तियां छोड़ दी जाती हैं। आप उन्हें फ़ाइल में ठीक करके फिर से इम्पोर्ट कर सकते हैं।"
                        ),
                        fontSize = 12.5.sp,
                        color = EditSubtleText,
                        lineHeight = 18.sp
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    border = BorderStroke(1.dp, EditCardBorder)
                ) {
                    Button(
                        onClick = {
                            val toCreate = readyRows.map { r ->
                                val meta = Customer.serializeCustomerMeta(
                                    phones = if (r.phone.isNotBlank()) listOf(TaggedMobileNumber(r.phone, "Self")) else emptyList(),
                                    openingUdhari = r.openingUdhari
                                )
                                Customer(
                                    id = UUID.randomUUID().toString(),
                                    name = r.name,
                                    phone = r.phone,
                                    phonesJson = meta,
                                    village = r.village,
                                    totalUdhari = r.openingUdhari,
                                    creditLimit = r.creditLimit,
                                    createdByUserId = currentUserId,
                                    syncStatus = SyncStatus.PENDING
                                )
                            }
                            lastAddedCount = toCreate.size
                            lastSkippedRows = parsedRows.filter { it.status != "Ready" }
                            lastTotalOpeningUdhari = readyRows.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.openingUdhari) }
                            if (toCreate.isNotEmpty()) {
                                onImportCustomersBatch(toCreate)
                            }
                            currentStep = ImportSubStep.IMPORT_SUCCESS
                        },
                        enabled = readyRows.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EditBrandGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(52.dp)
                            .testTag("confirm_import_excel_btn")
                    ) {
                        Text(
                            text = "Import ${readyRows.size} customers",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        ImportSubStep.IMPORT_SUCCESS -> {
            // ================================================================
            // PAGE 12: IMPORT COMPLETE SUMMARY SCREEN
            // ================================================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(EditPageBg)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Surface(
                    modifier = Modifier.size(84.dp),
                    shape = CircleShape,
                    color = EditSoftGreen
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = EditBrandGreen,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "$lastAddedCount ${tr("customers added", "ग्राहक जोड़े गए")}",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = EditNavyText
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${lastSkippedRows.size} ${tr("rows were skipped.", "पंक्तियां छोड़ दी गईं।")}",
                    fontSize = 14.5.sp,
                    color = EditSubtleText
                )

                Spacer(modifier = Modifier.height(22.dp))

                // 3 KPI Boxes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, EditCardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("ADDED", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = EditSubtleText)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$lastAddedCount", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = EditBrandGreen)
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, EditCardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("SKIPPED", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = EditSubtleText)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${lastSkippedRows.size}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1.25f),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, EditCardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("OPENING\nUDHARI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EditSubtleText, lineHeight = 12.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = CurrencyUtils.formatInr(lastTotalOpeningUdhari),
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = EditCrimsonRed,
                                maxLines = 1
                            )
                        }
                    }
                }

                if (lastSkippedRows.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, EditCardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = tr("Skipped rows", "छोड़ी गई पंक्तियां"),
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = EditNavyText
                            )
                            lastSkippedRows.take(5).forEach { skip ->
                                HorizontalDivider(color = Color(0xFFF1F5F9))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = skip.name.ifBlank { "(no name)" },
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EditNavyText
                                        )
                                        val reason = if (skip.status == "Duplicate") "mobile already used" else "name is missing"
                                        Text(
                                            text = "Row ${skip.rowNumber} · $reason",
                                            fontSize = 12.5.sp,
                                            color = EditSubtleText
                                        )
                                    }
                                    val isDup = skip.status == "Duplicate"
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = if (isDup) Color(0xFFFEF3C7) else Color(0xFFFEE2E2)
                                    ) {
                                        Text(
                                            text = skip.status,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDup) Color(0xFFB45309) else EditCrimsonRed,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = tr("Download skipped rows", "छोड़ी गई पंक्तियां डाउनलोड करें"),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = EditBrandGreen,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clickable {
                                        shareSkippedRowsCsv(context, lastSkippedRows)
                                    }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EditBrandGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("import_success_view_customers_btn")
                ) {
                    Text(
                        text = tr("View customers", "ग्राहक देखें"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = { currentStep = ImportSubStep.HUB }) {
                    Text(
                        text = tr("Import more", "और इम्पोर्ट करें"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = EditBrandGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnMapperRow(
    fieldLabel: String,
    headers: List<String>,
    selectedIndex: Int,
    allowNotUsed: Boolean,
    onSelectIndex: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val colPrefix = if (selectedIndex in headers.indices) {
        val letter = ('A' + selectedIndex)
        "Column $letter · ${headers[selectedIndex]}"
    } else {
        "Not used"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = fieldLabel,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = EditNavyText,
            modifier = Modifier.weight(1f)
        )
        Box {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, EditCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = colPrefix,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = EditNavyText
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = EditSubtleText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                if (allowNotUsed) {
                    DropdownMenuItem(
                        text = { Text("Not used") },
                        onClick = {
                            onSelectIndex(-1)
                            expanded = false
                        }
                    )
                }
                headers.forEachIndexed { idx, h ->
                    val letter = ('A' + idx)
                    DropdownMenuItem(
                        text = { Text("Column $letter · $h") },
                        onClick = {
                            onSelectIndex(idx)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun queryRealDeviceContacts(
    context: Context,
    existingPhoneSet: Set<String>
): List<DeviceContactItem> {
    val result = mutableListOf<DeviceContactItem>()
    val seenPhones = mutableSetOf<String>()
    try {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone._ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        cursor?.use { c ->
            val idIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID)
            val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (c.moveToNext()) {
                val id = if (idIdx >= 0) c.getString(idIdx).orEmpty() else UUID.randomUUID().toString()
                val name = if (nameIdx >= 0) c.getString(nameIdx).orEmpty().trim() else ""
                val rawPhone = if (numIdx >= 0) c.getString(numIdx).orEmpty().trim() else ""
                val norm = rawPhone.filter { it.isDigit() }.takeLast(10)
                if (name.isNotBlank() && norm.length >= 10 && seenPhones.add(norm)) {
                    val formatted = if (norm.length == 10) "${norm.substring(0, 5)} ${norm.substring(5)}" else rawPhone
                    result.add(
                        DeviceContactItem(
                            id = id,
                            name = name,
                            phone = formatted,
                            alreadyAdded = existingPhoneSet.contains(norm)
                        )
                    )
                }
            }
        }
    } catch (_: Exception) {
    }
    return result
}

private fun parseCsvFromUri(context: Context, uri: Uri): Pair<List<String>, List<List<String>>> {
    return try {
        val lines = mutableListOf<String>()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).useLines { seq ->
                seq.forEach { line ->
                    if (line.isNotBlank()) lines.add(line)
                }
            }
        }
        if (lines.isEmpty()) return Pair(emptyList(), emptyList())
        val delimiter = if (lines.first().contains('\t')) '\t' else ','
        val headers = lines.first().split(delimiter).map { it.trim().removeSurrounding("\"") }
        val rows = lines.drop(1).map { row ->
            row.split(delimiter).map { it.trim().removeSurrounding("\"") }
        }
        Pair(headers, rows)
    } catch (_: Exception) {
        Pair(emptyList(), emptyList())
    }
}

private fun shareSampleCustomerCsvTemplate(context: Context) {
    try {
        val csv = buildString {
            appendLine("Name,Mobile,Village,Father's name,Credit limit,Opening udhari")
            appendLine("Customer Name,9800000000,Village Name,Father Name,10000,0")
        }
        val dir = File(context.cacheDir, "templates").apply { mkdirs() }
        val file = File(dir, "khatawise_customer_template.csv")
        FileOutputStream(file).use { it.write(csv.toByteArray()) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Download Sample Template"))
    } catch (_: Exception) {
        Toast.makeText(context, "Template ready: Name, Mobile, Village, Father's name, Credit limit, Opening udhari", Toast.LENGTH_LONG).show()
    }
}

private fun shareSkippedRowsCsv(context: Context, skipped: List<ParsedImportRow>) {
    try {
        val csv = buildString {
            appendLine("Row,Name,Mobile,Village,Opening udhari,Reason")
            skipped.forEach { r ->
                appendLine("${r.rowNumber},${r.name},${r.phone},${r.village},${r.openingUdhari.toPlainString()},${r.status}")
            }
        }
        val dir = File(context.cacheDir, "templates").apply { mkdirs() }
        val file = File(dir, "skipped_customers.csv")
        FileOutputStream(file).use { it.write(csv.toByteArray()) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Download Skipped Rows"))
    } catch (_: Exception) {
    }
}
