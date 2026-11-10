package com.example.ui.purchases

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.CattleFeedRepository
import com.example.model.PurchaseInvoice
import com.example.model.SupplierKhataSummary
import com.example.model.SupplierLedgerEntry
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatTextMuted
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import com.example.util.CurrencyUtils
import com.example.util.tr
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val SupGreen = Color(0xFF0E6F43)
internal val SupGreenSoft = Color(0xFFDDF3E8)
internal val SupRed = Color(0xFFB91C1C)
internal val SupRedSoft = Color(0xFFFCE4E4)
internal val SupBlue = Color(0xFF1E40AF)
internal val SupBlueSoft = Color(0xFFE0EEFF)
internal val SupAvatarBg = Color(0xFFE8EEF7)
internal val SupAvatarText = Color(0xFF1E3A8A)
internal val SupChipIdleBg = Color(0xFFF1F1F7)

data class SupplierPhoneItem(
    val number: String,
    val tag: String = "Owner" // Owner, Office, WhatsApp, Accounts, Other
)

data class SupplierProfileMetadata(
    val firmName: String,
    val contactPerson: String = "",
    val phones: List<SupplierPhoneItem> = emptyList(),
    val city: String = "",
    val gstin: String = "",
    val itemsSupplied: List<String> = emptyList(),
    val creditPeriodDays: Int = 15, // 0 = Cash only, 7, 15, 30
    val openingBalance: BigDecimal = BigDecimal.ZERO,
    val notes: String = "",
    val isActiveSupplier: Boolean = true,
    val remindBeforeDueDate: Boolean = true
) {
    val primaryPhone: String
        get() = phones.firstOrNull()?.number?.takeIf { it.isNotBlank() } ?: ""
}

object SupplierMetadataStore {
    private const val PREF_NAME = "supplier_metadata_prefs"
    private const val KEY_PROFILES = "profiles_json"

    fun loadAll(context: Context): Map<String, SupplierProfileMetadata> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyMap()
        return try {
            val arr = JSONArray(raw)
            val map = mutableMapOf<String, SupplierProfileMetadata>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val firmName = obj.optString("firmName").trim()
                if (firmName.isBlank()) continue
                val phonesArr = obj.optJSONArray("phones") ?: JSONArray()
                val phones = mutableListOf<SupplierPhoneItem>()
                for (j in 0 until phonesArr.length()) {
                    val pObj = phonesArr.getJSONObject(j)
                    phones.add(
                        SupplierPhoneItem(
                            number = pObj.optString("number"),
                            tag = pObj.optString("tag", "Owner")
                        )
                    )
                }
                val itemsArr = obj.optJSONArray("itemsSupplied") ?: JSONArray()
                val items = mutableListOf<String>()
                for (j in 0 until itemsArr.length()) {
                    val itStr = itemsArr.optString(j)
                    if (itStr.isNotBlank()) items.add(itStr)
                }
                val profile = SupplierProfileMetadata(
                    firmName = firmName,
                    contactPerson = obj.optString("contactPerson"),
                    phones = phones,
                    city = obj.optString("city"),
                    gstin = obj.optString("gstin"),
                    itemsSupplied = items,
                    creditPeriodDays = obj.optInt("creditPeriodDays", 15),
                    openingBalance = BigDecimal(obj.optString("openingBalance", "0")),
                    notes = obj.optString("notes"),
                    isActiveSupplier = obj.optBoolean("isActiveSupplier", true),
                    remindBeforeDueDate = obj.optBoolean("remindBeforeDueDate", true)
                )
                map[firmName.lowercase(Locale.ROOT)] = profile
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveProfile(context: Context, profile: SupplierProfileMetadata, previousFirmName: String? = null) {
        val existing = loadAll(context).toMutableMap()
        if (!previousFirmName.isNullOrBlank()) {
            existing.remove(previousFirmName.trim().lowercase(Locale.ROOT))
        }
        existing[profile.firmName.trim().lowercase(Locale.ROOT)] = profile.copy(firmName = profile.firmName.trim())
        persistMap(context, existing)
    }

    fun deleteProfile(context: Context, firmName: String) {
        val existing = loadAll(context).toMutableMap()
        existing.remove(firmName.trim().lowercase(Locale.ROOT))
        persistMap(context, existing)
    }

    private fun persistMap(context: Context, map: Map<String, SupplierProfileMetadata>) {
        val arr = JSONArray()
        map.values.forEach { item ->
            val obj = JSONObject()
            obj.put("firmName", item.firmName)
            obj.put("contactPerson", item.contactPerson)
            val phonesArr = JSONArray()
            item.phones.forEach { p ->
                val pObj = JSONObject()
                pObj.put("number", p.number)
                pObj.put("tag", p.tag)
                phonesArr.put(pObj)
            }
            obj.put("phones", phonesArr)
            obj.put("city", item.city)
            obj.put("gstin", item.gstin)
            val itemsArr = JSONArray()
            item.itemsSupplied.forEach { itemsArr.put(it) }
            obj.put("itemsSupplied", itemsArr)
            obj.put("creditPeriodDays", item.creditPeriodDays)
            obj.put("openingBalance", item.openingBalance.toPlainString())
            obj.put("notes", item.notes)
            obj.put("isActiveSupplier", item.isActiveSupplier)
            obj.put("remindBeforeDueDate", item.remindBeforeDueDate)
            arr.put(obj)
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROFILES, arr.toString())
            .apply()
    }
}

internal fun supplierInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase(Locale.getDefault())
        parts.size == 1 -> parts[0].take(2).uppercase(Locale.getDefault())
        else -> "SP"
    }
}

internal fun formatShortPurchaseBillNo(invoiceNumber: String, indexHint: Int = 0): String {
    val cleaned = invoiceNumber.trim()
    if (cleaned.startsWith("P-", ignoreCase = true)) return cleaned.uppercase(Locale.ROOT)
    val digits = cleaned.filter { it.isDigit() }.takeLast(3)
    return if (digits.isNotEmpty()) {
        "P-$digits"
    } else {
        "P-${200 + indexHint}"
    }
}

@Composable
fun SuppliersDirectoryTabView(
    supplierSummaries: List<SupplierKhataSummary>,
    invoices: List<PurchaseInvoice>,
    metadataMap: Map<String, SupplierProfileMetadata>,
    initialFilter: String = "All",
    onOpenKhata: (SupplierKhataSummary) -> Unit,
    onAddSupplierClick: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember(initialFilter) { mutableStateOf(initialFilter) } // All, You owe, Settled
    var sortMode by remember { mutableIntStateOf(0) } // 0: Highest payable, 1: Recent, 2: Name A-Z
    val shortDateFormat = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    val invoiceItemsBySupplier = remember(invoices) {
        invoices.groupBy { it.wholesalerName.trim().lowercase(Locale.ROOT) }
            .mapValues { (_, invList) ->
                invList.flatMap { inv -> inv.getItems().map { it.feedName } }.distinct()
            }
    }

    val totalPayable = remember(supplierSummaries) {
        supplierSummaries.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.balancePayable) }
    }
    val toPayCount = remember(supplierSummaries) {
        supplierSummaries.count { it.balancePayable > BigDecimal.ZERO }
    }

    val filteredAndSorted = remember(supplierSummaries, searchQuery, selectedFilter, sortMode, metadataMap, invoiceItemsBySupplier) {
        val filtered = supplierSummaries.filter { summary ->
            val key = summary.wholesalerName.trim().lowercase(Locale.ROOT)
            val meta = metadataMap[key]
            val city = meta?.city ?: ""
            val phone = summary.wholesalerPhone.ifBlank { meta?.primaryPhone ?: "" }
            val itemsList = (meta?.itemsSupplied.orEmpty() + invoiceItemsBySupplier[key].orEmpty()).distinct()

            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                val q = searchQuery.trim()
                summary.wholesalerName.contains(q, ignoreCase = true) ||
                    city.contains(q, ignoreCase = true) ||
                    phone.contains(q, ignoreCase = true) ||
                    itemsList.any { it.contains(q, ignoreCase = true) }
            }

            val matchesFilter = when (selectedFilter) {
                "You owe" -> summary.balancePayable > BigDecimal.ZERO
                "Settled" -> summary.balancePayable.compareTo(BigDecimal.ZERO) == 0
                else -> true
            }

            matchesSearch && matchesFilter
        }

        when (sortMode) {
            1 -> filtered.sortedByDescending { it.lastTransactionDate }
            2 -> filtered.sortedBy { it.wholesalerName.lowercase(Locale.ROOT) }
            else -> filtered.sortedWith(
                compareByDescending<SupplierKhataSummary> { it.balancePayable }
                    .thenByDescending { it.lastTransactionDate }
            )
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Total payable summary card
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
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = tr("Total payable", "कुल देनदारी"),
                                fontSize = 13.5.sp,
                                color = FlatTextSecondary
                            )
                            Text(
                                text = CurrencyUtils.formatInr(totalPayable),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (totalPayable > BigDecimal.ZERO) SupRed else SupGreen
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (toPayCount > 0) SupRedSoft else SupGreenSoft
                        ) {
                            Text(
                                text = "$toPayCount to pay",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (toPayCount > 0) SupRed else SupGreen,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
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
                            text = tr("Search name, city, phone, item...", "नाम, शहर, फोन या आइटम खोजें..."),
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
                        .testTag("suppliers_search_input")
                )
            }

            // Filter pills + Sort button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // All chip
                        val isAll = selectedFilter == "All"
                        Surface(
                            onClick = { selectedFilter = "All" },
                            shape = RoundedCornerShape(50),
                            color = if (isAll) SupGreen else SupChipIdleBg,
                            modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = tr("All", "सभी"),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAll) Color.White else FlatTextPrimary
                                )
                            }
                        }

                        // You owe chip
                        val isYouOwe = selectedFilter == "You owe"
                        Surface(
                            onClick = { selectedFilter = "You owe" },
                            shape = RoundedCornerShape(50),
                            color = if (isYouOwe) SupRed else SupRedSoft,
                            modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = tr("You owe", "देना बाकी"),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isYouOwe) Color.White else SupRed
                                )
                            }
                        }

                        // Settled chip
                        val isSettled = selectedFilter == "Settled"
                        Surface(
                            onClick = { selectedFilter = "Settled" },
                            shape = RoundedCornerShape(50),
                            color = if (isSettled) SupGreen else SupGreenSoft,
                            modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = tr("Settled", "चुकता"),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSettled) Color.White else SupGreen
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        onClick = { sortMode = (sortMode + 1) % 3 },
                        shape = RoundedCornerShape(50),
                        color = SupChipIdleBg,
                        modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = when (sortMode) {
                                    1 -> "↓↑ Recent"
                                    2 -> "↓↑ A–Z"
                                    else -> "↓↑ Sort"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                        }
                    }
                }
            }

            if (filteredAndSorted.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr("No suppliers found.", "कोई सप्लायर नहीं मिला।"),
                            fontSize = 14.5.sp,
                            color = FlatTextMuted
                        )
                    }
                }
            } else {
                items(filteredAndSorted, key = { it.wholesalerName }) { summary ->
                    val key = summary.wholesalerName.trim().lowercase(Locale.ROOT)
                    val meta = metadataMap[key]
                    val city = meta?.city?.takeIf { it.isNotBlank() } ?: ""
                    val phone = summary.wholesalerPhone.ifBlank { meta?.primaryPhone ?: "" }
                    val itemsList = remember(meta, invoiceItemsBySupplier, key) {
                        val combined = (meta?.itemsSupplied.orEmpty() + invoiceItemsBySupplier[key].orEmpty()).distinct()
                        if (combined.isNotEmpty()) combined.joinToString(", ") else "Cattle feed, khal"
                    }
                    val lastBoughtText = if (summary.lastTransactionDate > 0L) {
                        "Last bought ${shortDateFormat.format(Date(summary.lastTransactionDate))}"
                    } else {
                        "New supplier"
                    }
                    val subLine = if (phone.isNotBlank()) "$phone · $lastBoughtText" else lastBoughtText
                    val hasPayable = summary.balancePayable > BigDecimal.ZERO

                    Surface(
                        onClick = { onOpenKhata(summary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("supplier_card_${summary.wholesalerName}"),
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
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(SupAvatarBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = supplierInitials(summary.wholesalerName),
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SupAvatarText
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = if (city.isNotBlank()) "${summary.wholesalerName} · $city" else summary.wholesalerName,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FlatTextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = itemsList,
                                        fontSize = 13.sp,
                                        color = FlatTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = subLine,
                                        fontSize = 12.5.sp,
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
                                    text = CurrencyUtils.formatInr(summary.balancePayable),
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasPayable) SupRed else SupGreen
                                )
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (hasPayable) SupRedSoft else SupGreenSoft
                                ) {
                                    Text(
                                        text = if (hasPayable) tr("You owe", "देना है") else tr("Settled", "चुकता"),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasPayable) SupRed else SupGreen,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom-right + Supplier FAB matching Suppliers · Directory.pdf
        FloatingActionButton(
            onClick = onAddSupplierClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_supplier_fab"),
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
                    text = tr("Supplier", "सप्लायर"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupplierKhataScreen(
    summary: SupplierKhataSummary,
    repository: CattleFeedRepository,
    metadata: SupplierProfileMetadata?,
    onBack: () -> Unit,
    onEditSupplier: () -> Unit,
    onNewPurchaseBill: () -> Unit,
    onPaySupplier: () -> Unit,
    onOpenStatement: () -> Unit = {}
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<SupplierLedgerEntry>>(emptyList()) }
    var newestFirst by remember { mutableStateOf(true) }
    val shortDateFormat = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    LaunchedEffect(summary.wholesalerName, summary.balancePayable, summary.totalPurchases, summary.totalPaid) {
        entries = repository.getSupplierLedger(summary.wholesalerName)
    }

    val city = metadata?.city?.takeIf { it.isNotBlank() } ?: "Mandi"
    val phone = summary.wholesalerPhone.ifBlank { metadata?.primaryPhone ?: "" }
    val headerSubtitle = if (phone.isNotBlank()) "$city · $phone" else city

    val invoiceProducts = remember(entries, metadata) {
        val fromInvoices = entries.filterIsInstance<SupplierLedgerEntry.InvoiceEntry>()
            .flatMap { it.invoice.getItems().map { item -> item.feedName } }
        (metadata?.itemsSupplied.orEmpty() + fromInvoices).distinct().ifEmpty {
            listOf("Cattle Feed", "Wheat Bran")
        }
    }

    val creditDays = metadata?.creditPeriodDays ?: 15
    val lastPurchaseDate = remember(entries, summary.lastTransactionDate) {
        entries.filterIsInstance<SupplierLedgerEntry.InvoiceEntry>()
            .maxOfOrNull { it.invoice.date } ?: summary.lastTransactionDate
    }
    val nextDueDateMillis = remember(lastPurchaseDate, creditDays) {
        if (lastPurchaseDate > 0L && creditDays > 0) {
            lastPurchaseDate + creditDays * 24L * 60L * 60L * 1000L
        } else 0L
    }

    // Compute chronological running balance for each entry
    val entriesWithRunningBalance = remember(entries, metadata, newestFirst) {
        val asc = entries.sortedBy { it.date }
        var running = metadata?.openingBalance ?: BigDecimal.ZERO
        val computed = asc.mapIndexed { idx, entry ->
            when (entry) {
                is SupplierLedgerEntry.InvoiceEntry -> {
                    running = running.add(entry.invoice.balanceAmount)
                }
                is SupplierLedgerEntry.PaymentEntry -> {
                    running = running.subtract(entry.payment.amount).max(BigDecimal.ZERO)
                }
            }
            Triple(entry, running, idx)
        }
        if (newestFirst) computed.reversed() else computed
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlatDarkBg)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp)
    ) {
        // Header matching Suppliers · Khata.pdf
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
                    .testTag("supplier_khata_back_btn"),
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
                    text = summary.wholesalerName,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = headerSubtitle,
                    fontSize = 13.sp,
                    color = FlatTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = onOpenStatement,
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("supplier_khata_statement_btn"),
                    shape = RoundedCornerShape(13.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = "Supplier statement",
                            tint = FlatTextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Surface(
                    onClick = onEditSupplier,
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("supplier_khata_edit_btn"),
                    shape = RoundedCornerShape(13.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit supplier",
                            tint = FlatTextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Surface(
                    onClick = {
                        if (phone.isNotBlank()) {
                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            context.startActivity(dialIntent)
                        }
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("supplier_khata_call_btn"),
                    shape = RoundedCornerShape(13.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Phone,
                            contentDescription = "Call supplier",
                            tint = SupGreen,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // YOU OWE Hero Card
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = tr("YOU OWE", "देना बाकी"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.7.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = CurrencyUtils.formatInr(summary.balancePayable),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (summary.balancePayable > BigDecimal.ZERO) SupRed else SupGreen
                        )

                        if (lastPurchaseDate > 0L) {
                            Text(
                                text = "Last bought ${shortDateFormat.format(Date(lastPurchaseDate))}",
                                fontSize = 13.sp,
                                color = FlatTextSecondary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        invoiceProducts.take(3).forEach { prodName ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = SupChipIdleBg
                            ) {
                                Text(
                                    text = prodName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = SupChipIdleBg
                        ) {
                            Text(
                                text = if (creditDays > 0) "Credit $creditDays days" else "Cash only",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }

                        if (summary.balancePayable > BigDecimal.ZERO && nextDueDateMillis > 0L) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = SupBlueSoft
                            ) {
                                Text(
                                    text = "Next due ${shortDateFormat.format(Date(nextDueDateMillis))}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SupBlue,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Two Action Buttons: + Purchase bill | Pay supplier
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onNewPurchaseBill,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("khata_new_purchase_btn"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SupGreen,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tr("Purchase bill", "खरीद बिल"),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onPaySupplier,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("pay_from_khata_btn"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, SupGreen),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = FlatDarkCard,
                        contentColor = SupGreen
                    )
                ) {
                    Text(
                        text = tr("Pay supplier", "भुगतान करें"),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = SupGreen
                    )
                }
            }

            // Khata Statement Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr("Khata", "खाता"),
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("Statement", "स्टेटमेंट"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = SupGreen,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onOpenStatement() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("khata_open_statement_link")
                            )
                            Text(
                                text = if (newestFirst) tr("Newest first", "नया पहले") else tr("Oldest first", "पुराना पहले"),
                                fontSize = 13.sp,
                                color = FlatTextSecondary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { newestFirst = !newestFirst }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = FlatDarkBorder, thickness = 1.dp)

                    if (entriesWithRunningBalance.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tr("No khata entries recorded yet.", "अभी तक कोई लेन-देन दर्ज नहीं है।"),
                                fontSize = 13.5.sp,
                                color = FlatTextMuted
                            )
                        }
                    } else {
                        entriesWithRunningBalance.forEachIndexed { index, (entry, runningBal, idx) ->
                            when (entry) {
                                is SupplierLedgerEntry.PaymentEntry -> {
                                    val pay = entry.payment
                                    val modeTitle = when (pay.paymentMode.uppercase(Locale.ROOT)) {
                                        "UPI" -> "UPI"
                                        "BANK", "BANK_TRANSFER" -> "Bank"
                                        "CHEQUE" -> "Cheque"
                                        else -> "Cash"
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = shortDateFormat.format(Date(pay.date)),
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = FlatTextSecondary,
                                            modifier = Modifier.width(52.dp)
                                        )

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "Payment · $modeTitle",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FlatTextPrimary
                                            )
                                            Text(
                                                text = pay.note.ifBlank { "Part payment" },
                                                fontSize = 12.5.sp,
                                                color = FlatTextSecondary
                                            )
                                        }

                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "−${CurrencyUtils.formatInr(pay.amount)}",
                                                fontSize = 15.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SupGreen
                                            )
                                            Text(
                                                text = "Bal ${CurrencyUtils.formatInr(runningBal)}",
                                                fontSize = 12.sp,
                                                color = FlatTextSecondary
                                            )
                                        }
                                    }
                                }

                                is SupplierLedgerEntry.InvoiceEntry -> {
                                    val inv = entry.invoice
                                    val shortNo = formatShortPurchaseBillNo(inv.invoiceNumber, idx + 1)
                                    val itemsDesc = inv.getItems().joinToString(", ") { item ->
                                        if (item.bags > 0) "${item.feedName} × ${item.bags} bags"
                                        else "${item.feedName} × ${CurrencyUtils.formatWeight(item.totalKg)} kg"
                                    }.ifBlank { "Cattle Feed" }
                                    val charges = inv.ladhan.add(inv.utaar).add(inv.gaadiKiraya)
                                    val chargesPart = if (charges > BigDecimal.ZERO) " + charges ${CurrencyUtils.formatInr(charges)}" else ""
                                    val paidPart = if (inv.balanceAmount.compareTo(BigDecimal.ZERO) == 0) {
                                        "paid in full"
                                    } else {
                                        "paid ${CurrencyUtils.formatInr(inv.paidAmount)}"
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = shortDateFormat.format(Date(inv.date)),
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = FlatTextSecondary,
                                            modifier = Modifier.width(52.dp)
                                        )

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "Purchase #$shortNo",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FlatTextPrimary
                                            )
                                            Text(
                                                text = "$itemsDesc$chargesPart · $paidPart",
                                                fontSize = 12.5.sp,
                                                color = FlatTextSecondary
                                            )
                                        }

                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            val addedKhata = inv.balanceAmount
                                            Text(
                                                text = if (addedKhata > BigDecimal.ZERO) {
                                                    "+${CurrencyUtils.formatInr(addedKhata)}"
                                                } else {
                                                    "₹0"
                                                },
                                                fontSize = 15.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (addedKhata > BigDecimal.ZERO) SupRed else FlatTextPrimary
                                            )
                                            Text(
                                                text = "Bal ${CurrencyUtils.formatInr(runningBal)}",
                                                fontSize = 12.sp,
                                                color = FlatTextSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            if (index < entriesWithRunningBalance.lastIndex) {
                                HorizontalDivider(color = FlatDarkBorder, thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
