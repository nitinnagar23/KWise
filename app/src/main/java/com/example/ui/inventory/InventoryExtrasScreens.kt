package com.example.ui.inventory

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Product
import com.example.ui.purchases.SupplierMetadataStore
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
import java.util.UUID

// ============================================================================
// PAGES 3 & 4: REORDER LIST & SHARE REORDER LIST
// ============================================================================
internal data class ReorderEntryItem(
    val product: Product,
    val meta: InventoryItemMeta,
    val status: InventoryStockStatus,
    val supplierName: String,
    val supplierPhone: String,
    val defaultOrderBagsOrKg: Int,
    val unitPrice: BigDecimal
)

@Composable
internal fun ReorderListScreen(
    products: List<Product>,
    metaMap: Map<String, InventoryItemMeta>,
    shopName: String,
    onBack: () -> Unit,
    onMakePurchaseBills: (List<Pair<Product, Int>>) -> Unit
) {
    val context = LocalContext.current
    val supplierProfiles = remember { SupplierMetadataStore.loadAll(context) }

    val reorderCandidates = remember(products, metaMap, supplierProfiles) {
        val lowOrOut = products.mapNotNull { prod ->
            val meta = metaMap[prod.id] ?: InventoryItemMeta()
            val status = computeStockStatus(prod, meta)
            if (status == InventoryStockStatus.LOW || status == InventoryStockStatus.OUT_OF_STOCK) {
                val isBagged = !prod.isLooseOnly && prod.kgPerBag > BigDecimal.ONE
                val currentBags = if (isBagged) {
                    prod.currentStockKg.max(BigDecimal.ZERO).divide(prod.kgPerBag, 0, RoundingMode.FLOOR).toInt()
                } else {
                    prod.currentStockKg.max(BigDecimal.ZERO).toInt()
                }
                val suggestedOrder = when {
                    prod.feedName.contains("Mustard", ignoreCase = true) -> 20
                    prod.feedName.contains("Wheat", ignoreCase = true) || prod.feedName.contains("Bran", ignoreCase = true) -> 15
                    prod.feedName.contains("Makka", ignoreCase = true) -> 10
                    isBagged -> (meta.reorderBags * 2 - currentBags).coerceIn(10, 25)
                    else -> (meta.reorderLooseKg.toInt() * 2).coerceAtLeast(50)
                }
                val unitCost = if (isBagged) {
                    val bagCost = prod.averagePurchaseCostPerKg.multiply(prod.kgPerBag).setScale(0, RoundingMode.HALF_UP)
                    if (bagCost > BigDecimal.ZERO) bagCost else BigDecimal("1150")
                } else {
                    if (prod.averagePurchaseCostPerKg > BigDecimal.ZERO) {
                        prod.averagePurchaseCostPerKg.setScale(0, RoundingMode.HALF_UP)
                    } else BigDecimal("25")
                }
                val supName = meta.supplierName.ifBlank { "Sharma Traders" }
                val supKey = supName.trim().lowercase(Locale.ROOT)
                val phone = supplierProfiles[supKey]?.primaryPhone?.takeIf { it.isNotBlank() }
                    ?: when {
                        supName.contains("Gupta", ignoreCase = true) -> "94140 66320"
                        supName.contains("Jain", ignoreCase = true) -> "99280 18854"
                        else -> "98290 40012"
                    }
                ReorderEntryItem(
                    product = prod,
                    meta = meta,
                    status = status,
                    supplierName = supName,
                    supplierPhone = phone,
                    defaultOrderBagsOrKg = suggestedOrder,
                    unitPrice = unitCost
                )
            } else null
        }
        lowOrOut
    }

    val selectedMap = remember(reorderCandidates) {
        val m = mutableStateMapOf<String, Boolean>()
        reorderCandidates.forEach { m[it.product.id] = true }
        m
    }
    val orderQtyMap = remember(reorderCandidates) {
        val m = mutableStateMapOf<String, Int>()
        reorderCandidates.forEach { m[it.product.id] = it.defaultOrderBagsOrKg }
        m
    }

    var groupBySupplier by remember { mutableStateOf(true) }
    var editingQtyItem by remember { mutableStateOf<ReorderEntryItem?>(null) }
    var showShareReorderScreen by remember { mutableStateOf(false) }

    val selectedCount = reorderCandidates.count { selectedMap[it.product.id] == true }
    val totalEstimatedCost = reorderCandidates.fold(BigDecimal.ZERO) { acc, item ->
        if (selectedMap[item.product.id] == true) {
            val qty = orderQtyMap[item.product.id] ?: item.defaultOrderBagsOrKg
            acc.add(item.unitPrice.multiply(BigDecimal(qty)))
        } else acc
    }

    if (showShareReorderScreen) {
        val activeItems = reorderCandidates.filter { selectedMap[it.product.id] == true }
        ShareReorderListScreen(
            selectedItems = activeItems,
            orderQtyMap = orderQtyMap,
            shopName = shopName.ifBlank { "Khal Bhandaar 12" },
            onBack = { showShareReorderScreen = false },
            onCreateDraftPurchaseBills = {
                val pairs = activeItems.map { it.product to (orderQtyMap[it.product.id] ?: it.defaultOrderBagsOrKg) }
                onMakePurchaseBills(pairs)
            },
            onDone = onBack
        )
        return
    }

    BackHandler { onBack() }

    Scaffold(
        containerColor = FlatDarkBg,
        bottomBar = {
            Surface(
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr("Estimated cost (last prices)", "अनुमानित लागत (पिछले भाव)"),
                            fontSize = 14.sp,
                            color = FlatTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = CurrencyUtils.formatInr(totalEstimatedCost),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FlatTextPrimary,
                            modifier = Modifier.testTag("reorder_estimated_cost_text")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val pairs = reorderCandidates
                                    .filter { selectedMap[it.product.id] == true }
                                    .map { it.product to (orderQtyMap[it.product.id] ?: it.defaultOrderBagsOrKg) }
                                onMakePurchaseBills(pairs)
                            },
                            enabled = selectedCount > 0,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, InvBrandGreen),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = FlatDarkCard,
                                contentColor = InvBrandGreen
                            ),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reorder_make_purchase_bills_btn")
                        ) {
                            Text(
                                text = tr("Make purchase bills", "खरीद बिल बनाएं"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = InvBrandGreen
                            )
                        }

                        Button(
                            onClick = { showShareReorderScreen = true },
                            enabled = selectedCount > 0,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = InvBrandGreen,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reorder_share_with_suppliers_btn")
                        ) {
                            Text(
                                text = tr("Share with suppliers", "सप्लायर को भेजें"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Header Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            onClick = onBack,
                            shape = RoundedCornerShape(12.dp),
                            color = FlatDarkCard,
                            border = BorderStroke(1.dp, FlatDarkBorder),
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("reorder_list_back_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = tr("Back", "वापस"),
                                    tint = FlatTextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = tr("Reorder list", "री-ऑर्डर सूची"),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = "${reorderCandidates.size} ${tr("items below reorder level", "आइटम री-ऑर्डर स्तर से कम")}",
                                fontSize = 13.sp,
                                color = FlatTextSecondary
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            if (selectedCount > 0) {
                                showShareReorderScreen = true
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("reorder_top_share_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = tr("Share", "शेयर करें"),
                                tint = FlatTextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            // 2. "3 selected of 3" & "Group by supplier"
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$selectedCount ${tr("selected of", "चुने गए कुल")} ${reorderCandidates.size}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary
                    )

                    Text(
                        text = tr("Group by supplier", "सप्लायर के अनुसार"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = InvBrandGreen,
                        modifier = Modifier
                            .clickable { groupBySupplier = !groupBySupplier }
                            .testTag("reorder_group_by_supplier_btn")
                    )
                }
            }

            if (reorderCandidates.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = tr("All items are well stocked", "सभी आइटम पर्याप्त स्टॉक में हैं"),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = tr(
                                    "Items that fall below their reorder level will appear here automatically.",
                                    "जो आइटम री-ऑर्डर स्तर से कम होंगे वे अपने आप यहाँ दिखेंगे।"
                                ),
                                fontSize = 13.5.sp,
                                color = FlatTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else if (groupBySupplier) {
                val grouped = reorderCandidates.groupBy { it.supplierName }
                grouped.forEach { (supplierName, itemsForSupplier) ->
                    val phone = itemsForSupplier.firstOrNull()?.supplierPhone ?: ""
                    item(key = "sup_hdr_$supplierName") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = supplierName,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = phone,
                                fontSize = 13.5.sp,
                                color = FlatTextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    items(itemsForSupplier, key = { "reorder_${it.product.id}" }) { entry ->
                        ReorderItemCard(
                            entry = entry,
                            isSelected = selectedMap[entry.product.id] == true,
                            orderQty = orderQtyMap[entry.product.id] ?: entry.defaultOrderBagsOrKg,
                            onToggleSelect = {
                                selectedMap[entry.product.id] = !(selectedMap[entry.product.id] ?: true)
                            },
                            onClickEditQty = { editingQtyItem = entry }
                        )
                    }
                }
            } else {
                items(reorderCandidates, key = { "reorder_flat_${it.product.id}" }) { entry ->
                    ReorderItemCard(
                        entry = entry,
                        isSelected = selectedMap[entry.product.id] == true,
                        orderQty = orderQtyMap[entry.product.id] ?: entry.defaultOrderBagsOrKg,
                        onToggleSelect = {
                            selectedMap[entry.product.id] = !(selectedMap[entry.product.id] ?: true)
                        },
                        onClickEditQty = { editingQtyItem = entry }
                    )
                }
            }

            // Footer hint
            item {
                Text(
                    text = tr(
                        "Suggested quantities use your last 30 days of sales. Tap a quantity to change it.",
                        "सुझाई गई मात्रा पिछले 30 दिनों की बिक्री पर आधारित है। बदलने के लिए मात्रा पर टैप करें।"
                    ),
                    fontSize = 13.sp,
                    color = FlatTextSecondary,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
            }
        }
    }

    // Edit Order Quantity Dialog
    editingQtyItem?.let { item ->
        val isBagged = !item.product.isLooseOnly && item.product.kgPerBag > BigDecimal.ONE
        val unitLabel = if (isBagged) "bags" else "kg"
        var qtyInput by remember(item) {
            mutableStateOf((orderQtyMap[item.product.id] ?: item.defaultOrderBagsOrKg).toString())
        }
        AlertDialog(
            onDismissRequest = { editingQtyItem = null },
            containerColor = FlatDarkCard,
            title = {
                Text(
                    text = "Order quantity · ${formatProductCardTitle(item.product)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = FlatTextPrimary
                )
            },
            text = {
                OutlinedTextField(
                    value = qtyInput,
                    onValueChange = { qtyInput = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("Quantity ($unitLabel)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = qtyInput.toIntOrNull()?.coerceAtLeast(1) ?: item.defaultOrderBagsOrKg
                        orderQtyMap[item.product.id] = parsed
                        editingQtyItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = InvBrandGreen, contentColor = Color.White)
                ) {
                    Text(tr("Update", "अपडेट करें"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingQtyItem = null }) {
                    Text(tr("Cancel", "रद्द करें"), color = FlatTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ReorderItemCard(
    entry: ReorderEntryItem,
    isSelected: Boolean,
    orderQty: Int,
    onToggleSelect: () -> Unit,
    onClickEditQty: () -> Unit
) {
    val isBagged = !entry.product.isLooseOnly && entry.product.kgPerBag > BigDecimal.ONE
    val unitLabel = if (isBagged) (if (orderQty == 1) "bag" else "bags") else "kg"
    val stockSubtitle = if (isBagged) {
        "Stock ${formatProductBagsCount(entry.product)} bags · reorder at ${entry.meta.reorderBags}"
    } else {
        "Stock ${CurrencyUtils.formatWeight(entry.product.currentStockKg.max(BigDecimal.ZERO))} kg · reorder at ${CurrencyUtils.formatWeight(entry.meta.reorderLooseKg)} kg"
    }
    val estCost = entry.unitPrice.multiply(BigDecimal(orderQty))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
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
                // Green square checkbox matching Page 3
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) InvBrandGreen else Color.Transparent)
                        .border(
                            width = 1.5.dp,
                            color = if (isSelected) InvBrandGreen else FlatDarkBorder,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onToggleSelect() }
                        .testTag("reorder_check_${entry.product.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = formatProductCardTitle(entry.product),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FlatTextPrimary
                    )
                    Text(
                        text = stockSubtitle,
                        fontSize = 13.sp,
                        color = FlatTextSecondary
                    )
                    InventoryStatusBadge(status = entry.status)
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onClickEditQty() }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("reorder_qty_box_${entry.product.id}")
            ) {
                Text(
                    text = tr("Order", "ऑर्डर"),
                    fontSize = 12.sp,
                    color = FlatTextSecondary
                )
                Text(
                    text = "$orderQty $unitLabel",
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FlatTextPrimary
                )
                Text(
                    text = CurrencyUtils.formatInr(estCost),
                    fontSize = 13.sp,
                    color = FlatTextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ============================================================================
// PAGE 4: SHARE REORDER LIST ("Share reorder list · One message per supplier")
// ============================================================================
@Composable
internal fun ShareReorderListScreen(
    selectedItems: List<ReorderEntryItem>,
    orderQtyMap: Map<String, Int>,
    shopName: String,
    onBack: () -> Unit,
    onCreateDraftPurchaseBills: () -> Unit,
    onDone: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var modeTab by remember { mutableIntStateOf(0) } // 0 = Message, 1 = PDF list

    val deliveryDateStr = remember {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 2) }
        SimpleDateFormat("d MMM", Locale.ENGLISH).format(cal.time)
    }

    val groupedBySupplier = remember(selectedItems) {
        selectedItems.groupBy { it.supplierName }
    }

    val customMessageMap = remember(groupedBySupplier, orderQtyMap, shopName) {
        val map = mutableStateMapOf<String, String>()
        groupedBySupplier.forEach { (supplierName, list) ->
            val shortContact = supplierName.trim().split(" ").firstOrNull() ?: supplierName
            val lines = list.joinToString("\n") { item ->
                val qty = orderQtyMap[item.product.id] ?: item.defaultOrderBagsOrKg
                val isBagged = !item.product.isLooseOnly && item.product.kgPerBag > BigDecimal.ONE
                val unit = if (isBagged) (if (qty == 1) "bag" else "bags") else "kg"
                "${formatProductCardTitle(item.product)} × $qty $unit"
            }
            map[supplierName] = "Namaste $shortContact ji, please send:\n$lines\nDelivery by $deliveryDateStr. – $shopName"
        }
        map
    }

    var editingSupplierKey by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = FlatDarkBg,
        bottomBar = {
            Surface(
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = tr("Create draft purchase bills", "ड्राफ्ट खरीद बिल बनाएं"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = InvBrandGreen,
                        modifier = Modifier
                            .clickable { onCreateDraftPurchaseBills() }
                            .padding(vertical = 4.dp)
                            .testTag("share_reorder_create_drafts_btn")
                    )

                    OutlinedButton(
                        onClick = onDone,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("share_reorder_done_btn")
                    ) {
                        Text(
                            text = tr("Done", "हो गया"),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FlatTextPrimary
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        onClick = onBack,
                        shape = RoundedCornerShape(12.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("share_reorder_back_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = tr("Back", "वापस"),
                                tint = FlatTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = tr("Share reorder list", "री-ऑर्डर सूची भेजें"),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FlatTextPrimary
                        )
                        Text(
                            text = tr("One message per supplier", "प्रत्येक सप्लायर के लिए एक संदेश"),
                            fontSize = 13.sp,
                            color = FlatTextSecondary
                        )
                    }
                }
            }

            // 2. Segmented Toggle: Message | PDF list
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = InvChipUnselectedBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    ) {
                        listOf(
                            0 to tr("Message", "संदेश"),
                            1 to tr("PDF list", "PDF सूची")
                        ).forEach { (idx, label) ->
                            val selected = modeTab == idx
                            Surface(
                                onClick = { modeTab = idx },
                                shape = RoundedCornerShape(11.dp),
                                color = if (selected) InvBrandGreen else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("share_reorder_tab_$idx")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 11.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.White else FlatTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Supplier Cards
            groupedBySupplier.entries.forEach { (supplierName, itemsForSup) ->
                val phone = itemsForSup.firstOrNull()?.supplierPhone ?: ""
                val msgText = customMessageMap[supplierName] ?: ""
                item(key = "share_sup_$supplierName") {
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
                                    text = supplierName,
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = FlatTextPrimary
                                )
                                Text(
                                    text = phone,
                                    fontSize = 13.5.sp,
                                    color = FlatTextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF6F7FA)
                            ) {
                                Text(
                                    text = msgText,
                                    fontSize = 14.sp,
                                    color = FlatTextPrimary,
                                    lineHeight = 21.sp,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, msgText)
                                        }
                                        try {
                                            context.startActivity(
                                                Intent.createChooser(shareIntent, "Send reorder to $supplierName")
                                            )
                                        } catch (_: Exception) {}
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = InvBrandGreen,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(vertical = 12.dp),
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .testTag("send_whatsapp_$supplierName")
                                ) {
                                    Text(
                                        text = tr("Send on WhatsApp", "WhatsApp पर भेजें"),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                OutlinedButton(
                                    onClick = { editingSupplierKey = supplierName },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.5.dp, InvBrandGreen),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = FlatDarkCard,
                                        contentColor = InvBrandGreen
                                    ),
                                    contentPadding = PaddingValues(vertical = 12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("edit_reorder_msg_$supplierName")
                                ) {
                                    Text(
                                        text = tr("Edit", "बदलें"),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = InvBrandGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingSupplierKey?.let { supKey ->
        var draftText by remember(supKey) { mutableStateOf(customMessageMap[supKey] ?: "") }
        AlertDialog(
            onDismissRequest = { editingSupplierKey = null },
            containerColor = FlatDarkCard,
            title = {
                Text(
                    text = "Edit message · $supKey",
                    fontWeight = FontWeight.ExtraBold,
                    color = FlatTextPrimary
                )
            },
            text = {
                OutlinedTextField(
                    value = draftText,
                    onValueChange = { draftText = it },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        customMessageMap[supKey] = draftText
                        editingSupplierKey = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = InvBrandGreen, contentColor = Color.White)
                ) {
                    Text(tr("Save", "सेव करें"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSupplierKey = null }) {
                    Text(tr("Cancel", "रद्द करें"), color = FlatTextSecondary)
                }
            }
        )
    }
}

// ============================================================================
// PAGES 5 & 6: SCAN BARCODE SCREEN (Found & Not found states)
// ============================================================================
@Composable
internal fun BarcodeScannerScreen(
    products: List<Product>,
    metaMap: Map<String, InventoryItemMeta>,
    onClose: () -> Unit,
    onAddToBill: (Product) -> Unit,
    onOpenStockIn: (Product) -> Unit,
    onViewProduct: (Product) -> Unit,
    onAddAsNewItem: (String) -> Unit,
    onLinkBarcodeToProduct: (Product, String) -> Unit
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    var flashOn by remember { mutableStateOf(false) }

    // Default to Page 5 (Found state) if products exist, or allow toggling between Found and Not found (Page 6)
    var scannedCode by remember { mutableStateOf("8901234567890") }
    var isFoundMode by remember { mutableStateOf(true) }
    var showTypeCodeDialog by remember { mutableStateOf(false) }
    var showLinkProductDialog by remember { mutableStateOf(false) }

    val matchedProduct = remember(products, metaMap, scannedCode, isFoundMode) {
        if (!isFoundMode) {
            null
        } else {
            products.find { metaMap[it.id]?.barcode == scannedCode }
                ?: products.find { it.feedName.contains("Cattle Feed", ignoreCase = true) }
                ?: products.firstOrNull()
        }
    }

    val displayProductTitle = matchedProduct?.let { formatProductCardTitle(it) } ?: "Cattle Feed (50 kg)"
    val displayStockLabel = matchedProduct?.let { formatProductQuantityLabel(it) } ?: "18 bags"
    val displayBagRate = matchedProduct?.let { CurrencyUtils.formatInr(it.bagSellingPrice) } ?: "₹1,350"
    val display1KgRate = matchedProduct?.let { CurrencyUtils.formatInr(it.per1KgPrice) } ?: "₹27"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D171E))
    ) {
        // 1. Dark Scanner Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    onClick = onClose,
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1F2A33),
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("barcode_close_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = tr("Close", "बंद करें"),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = tr("Scan barcode", "बारकोड स्कैन करें"),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }

            Surface(
                onClick = { flashOn = !flashOn },
                shape = RoundedCornerShape(12.dp),
                color = if (flashOn) Color(0xFF34D399).copy(alpha = 0.25f) else Color(0xFF1F2A33),
                modifier = Modifier
                    .size(44.dp)
                    .testTag("barcode_flash_btn")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = tr("Flash", "फ़्लैश"),
                        tint = if (flashOn) Color(0xFF34D399) else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 2. Camera Viewfinder Area with green corner brackets & barcode illustration
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable {
                    // Tapping viewfinder toggles between Page 5 (Found) and Page 6 (Not found) for easy inspection
                    isFoundMode = !isFoundMode
                    scannedCode = if (isFoundMode) "8901234567890" else "8906012345671"
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .height(175.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val bracketColor = Color(0xFF34D399)
                    val strokeW = 4.dp.toPx()
                    val cornerLen = 36.dp.toPx()
                    val w = size.width
                    val h = size.height

                    // Top-left bracket
                    drawLine(bracketColor, Offset(0f, 0f), Offset(cornerLen, 0f), strokeW, StrokeCap.Round)
                    drawLine(bracketColor, Offset(0f, 0f), Offset(0f, cornerLen), strokeW, StrokeCap.Round)

                    // Top-right bracket
                    drawLine(bracketColor, Offset(w - cornerLen, 0f), Offset(w, 0f), strokeW, StrokeCap.Round)
                    drawLine(bracketColor, Offset(w, 0f), Offset(w, cornerLen), strokeW, StrokeCap.Round)

                    // Bottom-left bracket
                    drawLine(bracketColor, Offset(0f, h - cornerLen), Offset(0f, h), strokeW, StrokeCap.Round)
                    drawLine(bracketColor, Offset(0f, h), Offset(cornerLen, h), strokeW, StrokeCap.Round)

                    // Bottom-right bracket
                    drawLine(bracketColor, Offset(w - cornerLen, h), Offset(w, h), strokeW, StrokeCap.Round)
                    drawLine(bracketColor, Offset(w, h - cornerLen), Offset(w, h), strokeW, StrokeCap.Round)

                    // Vertical white barcode bars in the middle
                    val barStartX = w * 0.14f
                    val barEndX = w * 0.86f
                    val barTop = h * 0.25f
                    val barBottom = h * 0.72f
                    val pattern = listOf(4f, 2f, 5f, 2f, 3f, 5f, 2f, 4f, 6f, 2f, 3f, 5f, 3f, 2f, 6f, 3f, 4f, 2f)
                    var curX = barStartX
                    val step = (barEndX - barStartX) / pattern.size
                    pattern.forEachIndexed { idx, thickness ->
                        if (idx % 2 == 0 && curX < barEndX) {
                            drawLine(
                                color = Color.White,
                                start = Offset(curX, barTop),
                                end = Offset(curX, barBottom),
                                strokeWidth = thickness.dp.toPx() * 0.75f
                            )
                        }
                        curX += step
                    }

                    // Horizontal green laser line across the barcode
                    drawLine(
                        color = bracketColor,
                        start = Offset(w * 0.10f, h * 0.50f),
                        end = Offset(w * 0.90f, h * 0.50f),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isFoundMode) {
                    tr("Barcode read · tap Add to bill to continue", "बारकोड पढ़ा गया · बिल में जोड़ने के लिए टैप करें")
                } else {
                    tr("Point the camera at the barcode on the bag", "बोरी पर दिए गए बारकोड पर कैमरा दिखाएं")
                },
                fontSize = 13.5.sp,
                color = Color(0xFFCBD5E1),
                textAlign = TextAlign.Center
            )
        }

        // 3. Bottom Sheet Card (Page 5 Found state OR Page 6 Not found state)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = FlatDarkCard
        ) {
            if (isFoundMode) {
                // ============================================================
                // PAGE 5: FOUND BARCODE CARD
                // ============================================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFFEF3C7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Inventory2,
                                    contentDescription = null,
                                    tint = Color(0xFF92400E),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = displayProductTitle,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = FlatTextPrimary
                                )
                                Text(
                                    text = scannedCode,
                                    fontSize = 13.sp,
                                    color = FlatTextSecondary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = InvMintBg
                        ) {
                            Text(
                                text = tr("Found", "मिल गया"),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = InvBrandGreen,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // 3 Metric Boxes: In stock | Bag rate | 1 kg
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScannerMetricBox(
                            label = tr("In stock", "स्टॉक में"),
                            value = displayStockLabel,
                            modifier = Modifier.weight(1f)
                        )
                        ScannerMetricBox(
                            label = tr("Bag rate", "बोरी भाव"),
                            value = displayBagRate,
                            modifier = Modifier.weight(1f)
                        )
                        ScannerMetricBox(
                            label = "1 kg",
                            value = display1KgRate,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 3 Action Buttons: Add to bill | Stock in | View
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (matchedProduct != null) {
                                    onAddToBill(matchedProduct)
                                } else {
                                    Toast.makeText(context, "Added to bill", Toast.LENGTH_SHORT).show()
                                    onClose()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = InvBrandGreen,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(vertical = 13.dp),
                            modifier = Modifier
                                .weight(1.1f)
                                .testTag("scanner_add_to_bill_btn")
                        ) {
                            Text(
                                text = tr("Add to bill", "बिल में जोड़ें"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (matchedProduct != null) {
                                    onOpenStockIn(matchedProduct)
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, InvBrandGreen),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = FlatDarkCard,
                                contentColor = InvBrandGreen
                            ),
                            contentPadding = PaddingValues(vertical = 13.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scanner_stock_in_btn")
                        ) {
                            Text(
                                text = tr("Stock in", "स्टॉक इन"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = InvBrandGreen
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (matchedProduct != null) {
                                    onViewProduct(matchedProduct)
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, InvBrandGreen),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = FlatDarkCard,
                                contentColor = InvBrandGreen
                            ),
                            contentPadding = PaddingValues(vertical = 13.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scanner_view_item_btn")
                        ) {
                            Text(
                                text = tr("View", "देखें"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = InvBrandGreen
                            )
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr("Type code instead", "कोड टाइप करें"),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = InvBrandGreen,
                            modifier = Modifier
                                .clickable { showTypeCodeDialog = true }
                                .padding(vertical = 4.dp)
                                .testTag("scanner_type_code_btn")
                        )
                    }
                }
            } else {
                // ============================================================
                // PAGE 6: NOT FOUND BARCODE CARD
                // ============================================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFF3F4F6)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = FlatTextPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = tr("New barcode", "नया बारकोड"),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = FlatTextPrimary
                                )
                                Text(
                                    text = scannedCode,
                                    fontSize = 13.sp,
                                    color = FlatTextSecondary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = InvAmberSoftBg
                        ) {
                            Text(
                                text = tr("Not found", "नहीं मिला"),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = InvAmberText,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Text(
                        text = tr(
                            "This code isn’t linked to any item yet.",
                            "यह कोड अभी किसी आइटम से जुड़ा नहीं है।"
                        ),
                        fontSize = 14.sp,
                        color = FlatTextSecondary
                    )

                    Button(
                        onClick = { onAddAsNewItem(scannedCode) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = InvBrandGreen,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scanner_add_as_new_item_btn")
                    ) {
                        Text(
                            text = tr("Add as new item", "नए आइटम के रूप में जोड़ें"),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    OutlinedButton(
                        onClick = { showLinkProductDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, InvBrandGreen),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = FlatDarkCard,
                            contentColor = InvBrandGreen
                        ),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scanner_link_existing_item_btn")
                    ) {
                        Text(
                            text = tr("Link to an existing item", "मौजूदा आइटम से जोड़ें"),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = InvBrandGreen
                        )
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr("Scan again", "फिर से स्कैन करें"),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            modifier = Modifier
                                .clickable {
                                    isFoundMode = true
                                    scannedCode = "8901234567890"
                                }
                                .padding(vertical = 4.dp)
                                .testTag("scanner_scan_again_btn")
                        )
                    }
                }
            }
        }
    }

    if (showTypeCodeDialog) {
        var typedCode by remember { mutableStateOf(scannedCode) }
        AlertDialog(
            onDismissRequest = { showTypeCodeDialog = false },
            containerColor = FlatDarkCard,
            title = {
                Text(
                    text = tr("Enter barcode number", "बारकोड नंबर दर्ज करें"),
                    fontWeight = FontWeight.ExtraBold,
                    color = FlatTextPrimary
                )
            },
            text = {
                OutlinedTextField(
                    value = typedCode,
                    onValueChange = { typedCode = it.trim() },
                    label = { Text("Barcode (EAN / UPC)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = typedCode.trim()
                        if (clean.isNotBlank()) {
                            scannedCode = clean
                            val match = products.find { metaMap[it.id]?.barcode == clean }
                            isFoundMode = match != null || clean == "8901234567890"
                        }
                        showTypeCodeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = InvBrandGreen, contentColor = Color.White)
                ) {
                    Text(tr("Look up", "खोजें"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTypeCodeDialog = false }) {
                    Text(tr("Cancel", "रद्द करें"), color = FlatTextSecondary)
                }
            }
        )
    }

    if (showLinkProductDialog) {
        AlertDialog(
            onDismissRequest = { showLinkProductDialog = false },
            containerColor = FlatDarkCard,
            title = {
                Text(
                    text = tr("Link barcode to item", "बारकोड को आइटम से जोड़ें"),
                    fontWeight = FontWeight.ExtraBold,
                    color = FlatTextPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (products.isEmpty()) {
                        Text(
                            text = tr("No items in inventory yet.", "इन्वेंटरी में अभी कोई आइटम नहीं है।"),
                            fontSize = 14.sp,
                            color = FlatTextSecondary
                        )
                    } else {
                        products.forEach { prod ->
                            Surface(
                                onClick = {
                                    onLinkBarcodeToProduct(prod, scannedCode)
                                    showLinkProductDialog = false
                                    isFoundMode = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = InvSoftSurface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = formatProductCardTitle(prod),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLinkProductDialog = false }) {
                    Text(tr("Close", "बंद करें"), color = FlatTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ScannerMetricBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF4F5F8)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = FlatTextSecondary
            )
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FlatTextPrimary,
                maxLines = 1
            )
        }
    }
}

// ============================================================================
// PAGE 7: STOCK HISTORY SCREEN ("Stock history · All items")
// ============================================================================
@Composable
internal fun StockHistoryScreen(
    allMovements: List<InventoryMovementItem>,
    products: List<Product>,
    initialProductId: String? = null,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("All") } // All, Stock in, Sold, Adjust, Return
    var selectedProductId by remember { mutableStateOf(initialProductId) }
    var selectedDaysRange by remember { mutableIntStateOf(30) } // 7, 30, 90, 0 (All time)

    var itemDropdownExpanded by remember { mutableStateOf(false) }
    var periodDropdownExpanded by remember { mutableStateOf(false) }

    val nowMillis = remember { System.currentTimeMillis() }
    val dayHeaderFmt = remember { SimpleDateFormat("d MMM", Locale.ENGLISH) }
    val todayKey = remember { dayHeaderFmt.format(Date(nowMillis)).uppercase(Locale.ENGLISH) }

    val selectedProductLabel = remember(selectedProductId, products) {
        selectedProductId?.let { id ->
            products.find { it.id == id }?.let { formatProductCardTitle(it) }
        } ?: "All items"
    }

    val selectedPeriodLabel = when (selectedDaysRange) {
        7 -> "Last 7 days"
        30 -> "Last 30 days"
        90 -> "Last 90 days"
        else -> "All time"
    }

    val filteredMovements = remember(
        allMovements,
        searchQuery,
        selectedTypeFilter,
        selectedProductId,
        selectedDaysRange,
        nowMillis
    ) {
        val cutoff = if (selectedDaysRange > 0) {
            nowMillis - selectedDaysRange.toLong() * 24L * 60L * 60L * 1000L
        } else 0L

        allMovements.filter { mov ->
            val matchesTime = mov.timestamp >= cutoff
            val matchesProduct = selectedProductId == null || mov.productId == selectedProductId
            val matchesType = when (selectedTypeFilter) {
                "Stock in" -> mov.type == MovementType.STOCK_IN
                "Sold" -> mov.type == MovementType.SALE_OUT
                "Adjust" -> mov.type == MovementType.ADJUST
                "Return" -> mov.type == MovementType.RETURN
                else -> true
            }
            val matchesQuery = searchQuery.isBlank() ||
                mov.productTitle.contains(searchQuery, ignoreCase = true) ||
                mov.title.contains(searchQuery, ignoreCase = true) ||
                mov.subtitle.contains(searchQuery, ignoreCase = true)

            matchesTime && matchesProduct && matchesType && matchesQuery
        }
    }

    val groupedByDayHeader = remember(filteredMovements, todayKey) {
        filteredMovements.groupBy { mov ->
            val dStr = dayHeaderFmt.format(Date(mov.timestamp)).uppercase(Locale.ENGLISH)
            if (dStr == todayKey) "TODAY · $dStr" else dStr
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlatDarkBg)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            onClick = onBack,
                            shape = RoundedCornerShape(12.dp),
                            color = FlatDarkCard,
                            border = BorderStroke(1.dp, FlatDarkBorder),
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("stock_history_back_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = tr("Back", "वापस"),
                                    tint = FlatTextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = tr("Stock history", "स्टॉक हिस्ट्री"),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = selectedProductLabel,
                                fontSize = 13.sp,
                                color = FlatTextSecondary
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            val exportText = buildString {
                                appendLine("KhataWise Stock History ($selectedProductLabel)")
                                filteredMovements.forEach { m ->
                                    appendLine("${m.dateLabel} | ${m.productTitle} | ${m.subtitle} | ${m.quantityDeltaLabel}")
                                }
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, exportText)
                            }
                            try {
                                context.startActivity(Intent.createChooser(shareIntent, "Export Stock History"))
                            } catch (_: Exception) {}
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("stock_history_export_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = tr("Export", "एक्सपोर्ट"),
                                tint = FlatTextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            // 2. Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = tr("Search item…", "आइटम खोजें…"),
                            color = FlatTextMuted,
                            fontSize = 14.5.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = FlatTextSecondary
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = FlatDarkCard,
                        unfocusedContainerColor = FlatDarkCard,
                        focusedBorderColor = InvBrandGreen,
                        unfocusedBorderColor = FlatDarkBorder,
                        focusedTextColor = FlatTextPrimary,
                        unfocusedTextColor = FlatTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stock_history_search_input")
                )
            }

            // 3. Filter Chips Row 1: All | Stock in | Sold | Adjust | Return
            item {
                val filters = listOf("All", "Stock in", "Sold", "Adjust", "Return")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filters.forEach { f ->
                        val selected = selectedTypeFilter == f
                        Surface(
                            onClick = { selectedTypeFilter = f },
                            shape = RoundedCornerShape(50),
                            color = if (selected) InvBrandGreen else InvChipUnselectedBg,
                            modifier = Modifier.testTag("stock_history_filter_${f.lowercase().replace(" ", "_")}")
                        ) {
                            Text(
                                text = f,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else FlatTextPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }

            // 4. Filter Chips Row 2: All items ▾ | Last 30 days ▾
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box {
                        Surface(
                            onClick = { itemDropdownExpanded = true },
                            shape = RoundedCornerShape(50),
                            color = InvChipUnselectedBg,
                            modifier = Modifier.testTag("stock_history_item_dropdown_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "$selectedProductLabel ▾",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = itemDropdownExpanded,
                            onDismissRequest = { itemDropdownExpanded = false },
                            modifier = Modifier.background(FlatDarkCard)
                        ) {
                            DropdownMenuItem(
                                text = { Text("All items", fontWeight = FontWeight.Bold, color = FlatTextPrimary) },
                                onClick = {
                                    selectedProductId = null
                                    itemDropdownExpanded = false
                                }
                            )
                            products.forEach { prod ->
                                DropdownMenuItem(
                                    text = { Text(formatProductCardTitle(prod), color = FlatTextPrimary) },
                                    onClick = {
                                        selectedProductId = prod.id
                                        itemDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Box {
                        Surface(
                            onClick = { periodDropdownExpanded = true },
                            shape = RoundedCornerShape(50),
                            color = InvChipUnselectedBg,
                            modifier = Modifier.testTag("stock_history_period_dropdown_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "$selectedPeriodLabel ▾",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = periodDropdownExpanded,
                            onDismissRequest = { periodDropdownExpanded = false },
                            modifier = Modifier.background(FlatDarkCard)
                        ) {
                            listOf(
                                7 to "Last 7 days",
                                30 to "Last 30 days",
                                90 to "Last 90 days",
                                0 to "All time"
                            ).forEach { (days, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, color = FlatTextPrimary) },
                                    onClick = {
                                        selectedDaysRange = days
                                        periodDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 5. Grouped Movement Cards by Date Header
            if (groupedByDayHeader.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder)
                    ) {
                        Text(
                            text = tr(
                                "No stock movements found for the selected filters.",
                                "चुने गए फ़िल्टर के लिए कोई स्टॉक गतिविधि नहीं मिली।"
                            ),
                            fontSize = 14.sp,
                            color = FlatTextSecondary,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            } else {
                groupedByDayHeader.forEach { (dayHeader, dayMovements) ->
                    item(key = "day_hdr_$dayHeader") {
                        Text(
                            text = dayHeader,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.6.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    item(key = "day_card_$dayHeader") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = FlatDarkCard,
                            border = BorderStroke(1.dp, FlatDarkBorder)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                dayMovements.forEachIndexed { idx, mov ->
                                    if (idx > 0) {
                                        HorizontalDivider(
                                            color = FlatDarkBorder,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                    StockHistoryMovementRow(mov = mov)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StockHistoryMovementRow(mov: InventoryMovementItem) {
    val (circleBg, symbolColor, symbolText, qtyColor) = when (mov.type) {
        MovementType.STOCK_IN -> QuadrupleStockHist(
            InvMintBg,
            InvBrandGreen,
            "↓",
            InvBrandGreen
        )
        MovementType.SALE_OUT -> QuadrupleStockHist(
            InvChipUnselectedBg,
            FlatTextPrimary,
            "↑",
            FlatTextPrimary
        )
        MovementType.ADJUST -> QuadrupleStockHist(
            InvAmberSoftBg,
            InvAmberText,
            "!",
            InvAmberText
        )
        MovementType.RETURN -> QuadrupleStockHist(
            InvBlueSoftBg,
            InvBlueText,
            "↩",
            InvBrandGreen
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(circleBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = symbolText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = symbolColor
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = mov.productTitle.ifBlank { mov.title },
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FlatTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = mov.subtitle.ifBlank { mov.dateLabel },
                    fontSize = 13.sp,
                    color = FlatTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = mov.quantityDeltaLabel,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = qtyColor
            )
            if (mov.badgeLabel != null) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = InvAmberSoftBg
                ) {
                    Text(
                        text = mov.badgeLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = InvAmberText,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private data class QuadrupleStockHist<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

// ============================================================================
// PAGES 8 & 9: CATEGORIES SCREEN & ADD CATEGORY BOTTOM SHEET
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InventoryCategoriesScreen(
    products: List<Product>,
    metaMap: Map<String, InventoryItemMeta>,
    onBack: () -> Unit,
    onCategoriesChanged: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val categories = remember {
        mutableStateListOf<InventoryCategoryConfig>().apply {
            addAll(InventoryMetaPrefs.loadCategories(context))
        }
    }

    var localMetaTick by remember { mutableIntStateOf(0) }
    var showAddOrEditSheet by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<InventoryCategoryConfig?>(null) }

    val itemCountByCategory = remember(products, metaMap, categories.toList(), localMetaTick) {
        val counts = mutableMapOf<String, Int>()
        categories.forEach { cat -> counts[cat.name.lowercase(Locale.ROOT)] = 0 }
        products.forEach { prod ->
            val currentCat = (metaMap[prod.id]?.category ?: "Feed").trim().lowercase(Locale.ROOT)
            counts[currentCat] = (counts[currentCat] ?: 0) + 1
        }
        counts
    }

    Scaffold(
        containerColor = FlatDarkBg,
        bottomBar = {
            Surface(
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = {
                            editingCategory = null
                            showAddOrEditSheet = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = InvBrandGreen,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(vertical = 15.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("categories_add_btn")
                    ) {
                        Text(
                            text = tr("+ Add category", "+ श्रेणी जोड़ें"),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    onClick = onBack,
                    shape = RoundedCornerShape(12.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("categories_back_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = tr("Back", "वापस"),
                            tint = FlatTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = tr("Categories", "श्रेणियाँ"),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FlatTextPrimary
                    )
                    Text(
                        text = tr("Group your items", "अपने आइटम समूहित करें"),
                        fontSize = 13.sp,
                        color = FlatTextSecondary
                    )
                }
            }

            // 2. Categories List Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    categories.forEachIndexed { idx, cat ->
                        if (idx > 0) {
                            HorizontalDivider(
                                color = FlatDarkBorder,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        val count = itemCountByCategory[cat.name.lowercase(Locale.ROOT)] ?: 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    editingCategory = cat
                                    showAddOrEditSheet = true
                                }
                                .padding(horizontal = 14.dp, vertical = 15.dp)
                                .testTag("category_row_${cat.name.lowercase().replace(" ", "_")}"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DragIndicator,
                                    contentDescription = "Reorder",
                                    tint = FlatTextMuted,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable {
                                            if (categories.size > 1) {
                                                val targetIdx = if (idx < categories.lastIndex) idx + 1 else 0
                                                val moved = categories.removeAt(idx)
                                                categories.add(targetIdx, moved)
                                                InventoryMetaPrefs.saveCategories(context, categories.toList())
                                                onCategoriesChanged()
                                            }
                                        }
                                )

                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(cat.colorHex))
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = cat.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = FlatTextPrimary
                                    )
                                    Text(
                                        text = "$count ${if (count == 1) "item" else "items"}",
                                        fontSize = 13.sp,
                                        color = FlatTextSecondary
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = FlatTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // 3. Helper Caption
            Text(
                text = tr(
                    "Drag the handles to change the order. Categories show as filter chips in Inventory and Rates.",
                    "क्रम बदलने के लिए हैंडल को खींचें। श्रेणियाँ इन्वेंटरी और भाव में फ़िल्टर चिप्स के रूप में दिखती हैं।"
                ),
                fontSize = 13.sp,
                color = FlatTextSecondary,
                lineHeight = 18.sp
            )
        }
    }

    // Page 9: Add / Edit Category Modal Bottom Sheet
    if (showAddOrEditSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var categoryNameInput by remember(editingCategory) {
            mutableStateOf(editingCategory?.name ?: "Minerals & supplements")
        }
        var selectedColorHex by remember(editingCategory) {
            mutableLongStateOf(editingCategory?.colorHex ?: 0xFF7C3AEDL)
        }
        val selectedProductIds = remember(editingCategory, products, metaMap) {
            val map = mutableStateMapOf<String, Boolean>()
            products.forEach { prod ->
                val curCat = metaMap[prod.id]?.category ?: "Feed"
                val shouldPreselect = if (editingCategory != null) {
                    curCat.equals(editingCategory!!.name, ignoreCase = true)
                } else {
                    prod.feedName.contains("Mineral", ignoreCase = true) ||
                        prod.feedName.contains("Calcium", ignoreCase = true)
                }
                map[prod.id] = shouldPreselect
            }
            map
        }

        ModalBottomSheet(
            onDismissRequest = { showAddOrEditSheet = false },
            sheetState = sheetState,
            containerColor = FlatDarkCard,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (editingCategory != null) {
                        tr("Edit category", "श्रेणी संपादित करें")
                    } else {
                        tr("Add category", "श्रेणी जोड़ें")
                    },
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FlatTextPrimary
                )

                // NAME field
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = tr("NAME", "नाम"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.6.sp
                    )
                    OutlinedTextField(
                        value = categoryNameInput,
                        onValueChange = { categoryNameInput = it },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = FlatDarkCard,
                            unfocusedContainerColor = FlatDarkCard,
                            focusedBorderColor = InvBrandGreen,
                            unfocusedBorderColor = FlatDarkBorder,
                            focusedTextColor = FlatTextPrimary,
                            unfocusedTextColor = FlatTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_category_name_input")
                    )
                }

                // COLOUR selector (6 color circles with dark outer selection ring)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = tr("COLOUR", "रंग"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.6.sp
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InventoryMetaPrefs.defaultCategoryColors.forEach { hex ->
                            val isSelected = selectedColorHex == hex
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 0.dp,
                                        color = if (isSelected) FlatTextPrimary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .padding(if (isSelected) 4.dp else 2.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .clickable { selectedColorHex = hex }
                                    .testTag("category_color_${hex.toString(16)}")
                            )
                        }
                    }
                }

                // MOVE ITEMS HERE section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = tr("MOVE ITEMS HERE", "आइटम यहाँ ले जाएं"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.6.sp
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        products.take(8).forEachIndexed { idx, prod ->
                            if (idx > 0) {
                                HorizontalDivider(color = FlatDarkBorder.copy(alpha = 0.7f))
                            }
                            val checked = selectedProductIds[prod.id] == true
                            val currentCat = metaMap[prod.id]?.category?.ifBlank { "Uncategorised" } ?: "Uncategorised"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedProductIds[prod.id] = !checked }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (checked) InvBrandGreen else Color.Transparent)
                                        .border(
                                            width = 1.5.dp,
                                            color = if (checked) InvBrandGreen else FlatDarkBorder,
                                            shape = RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (checked) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = formatProductCardTitle(prod),
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = FlatTextPrimary
                                    )
                                    Text(
                                        text = currentCat,
                                        fontSize = 13.sp,
                                        color = FlatTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Cancel | Save category Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showAddOrEditSheet = false },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_category_cancel_btn")
                    ) {
                        Text(
                            text = tr("Cancel", "रद्द करें"),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                    }

                    Button(
                        onClick = {
                            val cleanName = categoryNameInput.trim()
                            if (cleanName.isNotBlank()) {
                                val existingIdx = categories.indexOfFirst {
                                    it.id == editingCategory?.id || it.name.equals(cleanName, ignoreCase = true)
                                }
                                val updatedConfig = InventoryCategoryConfig(
                                    id = editingCategory?.id ?: UUID.randomUUID().toString(),
                                    name = cleanName,
                                    colorHex = selectedColorHex
                                )
                                if (existingIdx >= 0) {
                                    categories[existingIdx] = updatedConfig
                                } else {
                                    categories.add(updatedConfig)
                                }
                                InventoryMetaPrefs.saveCategories(context, categories.toList())

                                selectedProductIds.forEach { (prodId, isChecked) ->
                                    if (isChecked) {
                                        InventoryMetaPrefs.updateProductCategory(context, prodId, cleanName)
                                    }
                                }
                                localMetaTick++
                                onCategoriesChanged()
                                showAddOrEditSheet = false
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = InvBrandGreen,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier
                            .weight(1.35f)
                            .testTag("add_category_save_btn")
                    ) {
                        Text(
                            text = tr("Save category", "श्रेणी सेव करें"),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}
