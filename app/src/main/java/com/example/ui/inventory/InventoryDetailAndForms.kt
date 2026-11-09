package com.example.ui.inventory

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
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
import com.example.model.Product
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
import java.util.Date
import java.util.Locale

@Composable
internal fun InventoryStatusBadge(status: InventoryStockStatus) {
    val (bgColor, textColor, label) = when (status) {
        InventoryStockStatus.IN_STOCK -> Triple(InvMintBg, InvBrandGreen, tr("In stock", "स्टॉक में"))
        InventoryStockStatus.LOW -> Triple(InvAmberSoftBg, InvAmberText, tr("Low", "कम"))
        InventoryStockStatus.OUT_OF_STOCK -> Triple(InvRedSoftBg, InvRedText, tr("Out of stock", "स्टॉक ख़त्म"))
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = bgColor
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
internal fun InventoryItemDetailView(
    product: Product,
    meta: InventoryItemMeta,
    movements: List<InventoryMovementItem>,
    canManageInventory: Boolean,
    onBack: () -> Unit,
    onEditItem: () -> Unit,
    onDeleteItem: () -> Unit = {},
    onOpenStockIn: () -> Unit,
    onOpenAdjust: () -> Unit,
    onOpenStockHistory: (() -> Unit)? = null,
    onSaveSellingRates: (loose1KgRate: BigDecimal, bagPrice: BigDecimal?) -> Unit
) {
    val status = remember(product, meta) { computeStockStatus(product, meta) }
    val isBagged = !product.isLooseOnly && product.kgPerBag > BigDecimal.ONE
    val progressFraction = remember(product, meta) { computeStockProgressFraction(product, meta) }
    val tickFraction = remember(product, meta) { computeReorderTickFraction(product, meta) }

    val costPerBagOrKg = remember(product) {
        if (isBagged) {
            product.averagePurchaseCostPerKg.multiply(product.kgPerBag).setScale(0, RoundingMode.HALF_UP)
        } else {
            product.averagePurchaseCostPerKg.setScale(2, RoundingMode.HALF_UP)
        }
    }

    val marginPercentStr = remember(product) {
        val sellKg = if (product.sellingPricePerKg > BigDecimal.ZERO) {
            product.sellingPricePerKg
        } else {
            product.effectiveLooseRatePerKg
        }
        val costKg = product.averagePurchaseCostPerKg
        if (sellKg > BigDecimal.ZERO && costKg > BigDecimal.ZERO) {
            val pct = sellKg.subtract(costKg)
                .multiply(BigDecimal("100"))
                .divide(sellKg, 1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
            "${pct.toPlainString()} %"
        } else {
            "0 %"
        }
    }

    var showEditRatesDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlatDarkBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Top Header
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
                        .testTag("inventory_detail_back_btn")
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
                        text = product.feedName,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FlatTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isBagged) {
                            "Bagged · ${CurrencyUtils.formatWeight(product.kgPerBag)} kg bag"
                        } else {
                            "Loose · sold in kg"
                        },
                        fontSize = 13.sp,
                        color = FlatTextSecondary
                    )
                }
            }

            if (canManageInventory) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = onEditItem,
                        shape = RoundedCornerShape(12.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("inventory_detail_edit_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = tr("Edit item", "आइटम संपादित करें"),
                                tint = FlatTextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = onDeleteItem,
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFEE2E2),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("inventory_detail_delete_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Outlined.Delete,
                                contentDescription = tr("Delete item", "आइटम हटाएं"),
                                tint = Color(0xFFB91C1C),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. CURRENT STOCK Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = FlatDarkCard,
            border = BorderStroke(1.dp, FlatDarkBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = tr("CURRENT STOCK", "वर्तमान स्टॉक"),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextSecondary,
                    letterSpacing = 0.6.sp
                )

                Text(
                    text = formatProductQuantityLabel(product),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FlatTextPrimary,
                    modifier = Modifier.testTag("detail_current_stock_text")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isBagged) {
                            "= ${CurrencyUtils.formatWeight(product.currentStockKg.max(BigDecimal.ZERO))} kg"
                        } else {
                            tr("Loose stock", "खुला स्टॉक")
                        },
                        fontSize = 13.5.sp,
                        color = FlatTextSecondary
                    )
                    InventoryStatusBadge(status = status)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Progress bar with vertical reorder threshold tick mark
                val fillColor = when (status) {
                    InventoryStockStatus.IN_STOCK -> InvBrandGreen
                    InventoryStockStatus.LOW -> InvAmberProgress
                    InventoryStockStatus.OUT_OF_STOCK -> Color.Transparent
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                ) {
                    val barHeight = 6.dp.toPx()
                    val barTop = (size.height - barHeight) / 2f
                    drawRoundRect(
                        color = InvTrackGray,
                        topLeft = Offset(0f, barTop),
                        size = Size(size.width, barHeight),
                        cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)
                    )
                    if (progressFraction > 0f && status != InventoryStockStatus.OUT_OF_STOCK) {
                        drawRoundRect(
                            color = fillColor,
                            topLeft = Offset(0f, barTop),
                            size = Size(size.width * progressFraction, barHeight),
                            cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)
                        )
                    }
                    val tickX = size.width * tickFraction
                    drawLine(
                        color = Color(0xFF1F2937),
                        start = Offset(tickX, 0f),
                        end = Offset(tickX, size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                }

                Text(
                    text = if (isBagged) {
                        "Reorder when below ${meta.reorderBags} bags"
                    } else {
                        "Reorder when below ${CurrencyUtils.formatWeight(meta.reorderLooseKg)} kg"
                    },
                    fontSize = 12.5.sp,
                    color = FlatTextSecondary
                )
            }
        }

        // 3. Action Buttons Row: "+ Stock in" | "Adjust"
        if (canManageInventory) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onOpenStockIn,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InvBrandGreen,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(vertical = 13.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("detail_stock_in_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tr("Stock in", "स्टॉक जोड़ें"),
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onOpenAdjust,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, InvBrandGreen),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = FlatDarkCard,
                        contentColor = InvBrandGreen
                    ),
                    contentPadding = PaddingValues(vertical = 13.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("detail_adjust_btn")
                ) {
                    Text(
                        text = tr("Adjust", "समायोजन"),
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = InvBrandGreen
                    )
                }
            }
        }

        // 4. Selling rates Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = FlatDarkCard,
            border = BorderStroke(1.dp, FlatDarkBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("Selling rates", "बिक्री भाव"),
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary
                    )
                    Text(
                        text = tr("Edit ›", "बदलें ›"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = InvBrandGreen,
                        modifier = Modifier
                            .clickable { showEditRatesDialog = true }
                            .testTag("detail_edit_rates_btn")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1 kg box
                    RatePillBox(
                        label = "1 kg",
                        priceText = CurrencyUtils.formatInr(product.per1KgPrice),
                        modifier = Modifier.weight(1f)
                    )
                    // 5 kg box
                    RatePillBox(
                        label = "5 kg",
                        priceText = CurrencyUtils.formatInr(product.per5KgPrice),
                        modifier = Modifier.weight(1f)
                    )
                    // Bag box (or 10 kg for loose-only)
                    if (isBagged) {
                        RatePillBox(
                            label = "Bag · ${CurrencyUtils.formatWeight(product.kgPerBag)} kg",
                            priceText = CurrencyUtils.formatInr(product.bagSellingPrice),
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        RatePillBox(
                            label = "10 kg",
                            priceText = CurrencyUtils.formatInr(product.effectiveLooseRatePerKg.multiply(BigDecimal("10"))),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 5. Cost & supplier Card (UNMASKED per user instruction: "Also don't mask anything")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = InvSoftSurface,
            border = BorderStroke(1.dp, FlatDarkBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("Cost & supplier", "लागत और सप्लायर"),
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = FlatTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = tr("Only you", "केवल आप"),
                            fontSize = 12.5.sp,
                            color = FlatTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isBagged) tr("Cost / bag", "लागत / बोरी") else tr("Cost / kg", "लागत / किलो"),
                            fontSize = 12.5.sp,
                            color = FlatTextSecondary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = CurrencyUtils.formatInr(costPerBagOrKg),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FlatTextPrimary,
                            modifier = Modifier.testTag("detail_cost_value_text")
                        )
                    }

                    Column(modifier = Modifier.weight(0.9f)) {
                        Text(
                            text = tr("Margin", "मुनाफा"),
                            fontSize = 12.5.sp,
                            color = FlatTextSecondary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = marginPercentStr,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FlatTextPrimary,
                            modifier = Modifier.testTag("detail_margin_value_text")
                        )
                    }

                    Column(modifier = Modifier.weight(1.2f)) {
                        Text(
                            text = tr("Supplier", "सप्लायर"),
                            fontSize = 12.5.sp,
                            color = FlatTextSecondary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = meta.supplierName.ifBlank { "Sharma Traders" },
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FlatTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("detail_supplier_value_text")
                        )
                    }
                }
            }
        }

        // 6. Recent movement Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = FlatDarkCard,
            border = BorderStroke(1.dp, FlatDarkBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("Recent movement", "हाल की गतिविधि"),
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary
                    )
                    if (onOpenStockHistory != null) {
                        Text(
                            text = tr("Stock history ›", "स्टॉक हिस्ट्री ›"),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = InvBrandGreen,
                            modifier = Modifier
                                .clickable { onOpenStockHistory() }
                                .testTag("detail_open_stock_history_btn")
                        )
                    }
                }

                if (movements.isEmpty()) {
                    Text(
                        text = tr(
                            "No stock movements recorded yet for this item.",
                            "इस आइटम के लिए अभी कोई स्टॉक गतिविधि दर्ज नहीं है।"
                        ),
                        fontSize = 13.5.sp,
                        color = FlatTextSecondary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    movements.forEachIndexed { idx, mov ->
                        if (idx > 0) {
                            HorizontalDivider(color = FlatDarkBorder.copy(alpha = 0.7f))
                        }
                        val (circleBg, symbolColor, symbolText, qtyColor) = when (mov.type) {
                            MovementType.STOCK_IN -> Quadruple(
                                InvMintBg,
                                InvBrandGreen,
                                "↓",
                                InvBrandGreen
                            )
                            MovementType.SALE_OUT -> Quadruple(
                                InvChipUnselectedBg,
                                FlatTextPrimary,
                                "↑",
                                FlatTextPrimary
                            )
                            MovementType.ADJUST -> Quadruple(
                                InvAmberSoftBg,
                                InvAmberText,
                                "!",
                                InvAmberText
                            )
                            MovementType.RETURN -> Quadruple(
                                InvBlueSoftBg,
                                InvBlueText,
                                "↩",
                                InvBrandGreen
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
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
                                        .size(38.dp)
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
                                Column {
                                    Text(
                                        text = mov.title,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FlatTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = mov.dateLabel,
                                        fontSize = 12.5.sp,
                                        color = FlatTextSecondary
                                    )
                                }
                            }

                            Text(
                                text = mov.quantityDeltaLabel,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = qtyColor
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showEditRatesDialog) {
        QuickSellingRatesDialog(
            productName = product.feedName,
            isBagged = isBagged,
            kgPerBag = product.kgPerBag,
            initial1KgRate = product.per1KgPrice,
            initialBagPrice = if (isBagged) product.bagSellingPrice else null,
            onDismiss = { showEditRatesDialog = false },
            onSave = { new1Kg, newBag ->
                onSaveSellingRates(new1Kg, newBag)
                showEditRatesDialog = false
            }
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun RatePillBox(
    label: String,
    priceText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = InvSoftSurface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = FlatTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = priceText,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FlatTextPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun QuickSellingRatesDialog(
    productName: String,
    isBagged: Boolean,
    kgPerBag: BigDecimal,
    initial1KgRate: BigDecimal,
    initialBagPrice: BigDecimal?,
    onDismiss: () -> Unit,
    onSave: (loose1KgRate: BigDecimal, bagPrice: BigDecimal?) -> Unit
) {
    var rate1KgInput by remember {
        mutableStateOf(initial1KgRate.stripTrailingZeros().toPlainString())
    }
    var bagPriceInput by remember {
        mutableStateOf(
            initialBagPrice?.setScale(0, RoundingMode.HALF_UP)?.toPlainString()
                ?: initial1KgRate.multiply(kgPerBag).setScale(0, RoundingMode.HALF_UP).toPlainString()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FlatDarkCard,
        title = {
            Text(
                text = "${tr("Set selling rates", "बिक्री भाव सेट करें")} · $productName",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = FlatTextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = rate1KgInput,
                    onValueChange = { rate1KgInput = it },
                    label = { Text(tr("1 kg Loose Rate (₹)", "1 किलो खुला भाव (₹)")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val parsed1Kg = rate1KgInput.trim().toBigDecimalOrNull() ?: BigDecimal.ZERO
                Text(
                    text = "5 kg rate: ${CurrencyUtils.formatInr(parsed1Kg.multiply(BigDecimal(5)))}",
                    fontSize = 13.sp,
                    color = FlatTextSecondary,
                    fontWeight = FontWeight.Medium
                )
                if (isBagged) {
                    OutlinedTextField(
                        value = bagPriceInput,
                        onValueChange = { bagPriceInput = it },
                        label = {
                            Text(
                                "${tr("Bag Rate", "बोरी भाव")} (${CurrencyUtils.formatWeight(kgPerBag)} kg) (₹)"
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val new1Kg = rate1KgInput.trim().toBigDecimalOrNull() ?: initial1KgRate
                    val newBag = if (isBagged) bagPriceInput.trim().toBigDecimalOrNull() else null
                    onSave(new1Kg, newBag)
                },
                colors = ButtonDefaults.buttonColors(containerColor = InvBrandGreen, contentColor = Color.White)
            ) {
                Text(tr("Save rates", "भाव सेव करें"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("Cancel", "रद्द करें"), color = FlatTextPrimary)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InventoryAddOrEditItemView(
    existingProduct: Product?,
    existingMeta: InventoryItemMeta?,
    availableSuppliers: List<String>,
    onBack: () -> Unit,
    onSaveProduct: (
        name: String,
        hindiName: String,
        category: String,
        isLooseOnly: Boolean,
        bagSizeKg: BigDecimal,
        reorderBags: Int,
        reorderLooseKg: BigDecimal,
        openingBags: Int,
        openingLooseKg: BigDecimal,
        costPerBagOrKg: BigDecimal,
        supplier: String,
        looseRate1Kg: BigDecimal,
        bagSellingPrice: BigDecimal?
    ) -> Unit
) {
    val context = LocalContext.current
    val isEditing = existingProduct != null

    var itemName by remember { mutableStateOf(existingProduct?.feedName ?: "") }
    var hindiName by remember { mutableStateOf(existingMeta?.hindiName ?: "") }
    var selectedCategory by remember { mutableStateOf(existingMeta?.category ?: "Feed") }
    var isLooseOnly by remember { mutableStateOf(existingProduct?.isLooseOnly ?: false) }

    var bagSizeInput by remember {
        mutableStateOf(
            existingProduct?.kgPerBag?.stripTrailingZeros()?.toPlainString() ?: "50"
        )
    }
    var reorderInput by remember {
        mutableStateOf(
            if (existingProduct?.isLooseOnly == true) {
                existingMeta?.reorderLooseKg?.stripTrailingZeros()?.toPlainString() ?: "50"
            } else {
                (existingMeta?.reorderBags ?: 8).toString()
            }
        )
    }

    val initialOpeningBags = remember(existingProduct) {
        if (existingProduct == null) {
            "18"
        } else if (existingProduct.isLooseOnly || existingProduct.kgPerBag <= BigDecimal.ONE) {
            "0"
        } else {
            existingProduct.currentStockKg
                .divideToIntegralValue(existingProduct.kgPerBag)
                .toPlainString()
        }
    }
    val initialOpeningLooseKg = remember(existingProduct) {
        if (existingProduct == null) {
            "0"
        } else if (existingProduct.isLooseOnly || existingProduct.kgPerBag <= BigDecimal.ONE) {
            existingProduct.currentStockKg.stripTrailingZeros().toPlainString()
        } else {
            existingProduct.currentStockKg
                .remainder(existingProduct.kgPerBag)
                .stripTrailingZeros()
                .toPlainString()
        }
    }

    var openingBagsInput by remember { mutableStateOf(initialOpeningBags) }
    var openingLooseKgInput by remember { mutableStateOf(initialOpeningLooseKg) }

    val initialCostDisplay = remember(existingProduct) {
        if (existingProduct == null) {
            "1150"
        } else if (!existingProduct.isLooseOnly && existingProduct.kgPerBag > BigDecimal.ONE) {
            existingProduct.averagePurchaseCostPerKg
                .multiply(existingProduct.kgPerBag)
                .setScale(0, RoundingMode.HALF_UP)
                .toPlainString()
        } else {
            existingProduct.averagePurchaseCostPerKg
                .stripTrailingZeros()
                .toPlainString()
        }
    }
    var costInput by remember { mutableStateOf(initialCostDisplay) }

    val supplierOptions = remember(availableSuppliers) {
        val defaults = listOf("Sharma Traders", "Goyal Feeds", "Kisan Agro", "Mandi Wholesaler")
        (availableSuppliers + defaults).filter { it.isNotBlank() }.distinctBy { it.lowercase() }
    }
    var supplierName by remember {
        mutableStateOf(existingMeta?.supplierName ?: supplierOptions.firstOrNull() ?: "Sharma Traders")
    }
    var supplierDropdownExpanded by remember { mutableStateOf(false) }

    var looseRate1Kg by remember {
        mutableStateOf(existingProduct?.per1KgPrice ?: BigDecimal("27"))
    }
    var bagSellingPrice by remember {
        mutableStateOf<BigDecimal?>(
            if (existingProduct != null && !existingProduct.isLooseOnly) {
                existingProduct.bagSellingPrice
            } else {
                BigDecimal("1350")
            }
        )
    }
    var showRatesDialog by remember { mutableStateOf(false) }

    val categories = listOf("Feed", "Grain", "Mineral", "Other")
    val emptyNameToastMsg = tr("Please enter item name", "कृपया आइटम का नाम दर्ज करें")

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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_item_cancel_btn")
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
                            val trimmedName = itemName.trim()
                            if (trimmedName.isBlank()) {
                                Toast.makeText(
                                    context,
                                    emptyNameToastMsg,
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            val bagKg = if (isLooseOnly) {
                                BigDecimal.ONE
                            } else {
                                bagSizeInput.trim().toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal("50")
                            }
                            val rBags = if (isLooseOnly) 8 else (reorderInput.trim().toIntOrNull()?.coerceAtLeast(1) ?: 8)
                            val rLooseKg = if (isLooseOnly) {
                                reorderInput.trim().toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal("50")
                            } else {
                                BigDecimal("50")
                            }
                            val openBags = if (isLooseOnly) 0 else (openingBagsInput.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0)
                            val openLoose = openingLooseKgInput.trim().toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
                            val cleanedCost = costInput.replace(",", "").trim().toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO

                            onSaveProduct(
                                trimmedName,
                                hindiName.trim(),
                                selectedCategory,
                                isLooseOnly,
                                bagKg,
                                rBags,
                                rLooseKg,
                                openBags,
                                openLoose,
                                cleanedCost,
                                supplierName.trim().ifBlank { "Sharma Traders" },
                                looseRate1Kg,
                                if (isLooseOnly) null else bagSellingPrice
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = InvBrandGreen,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier
                            .weight(1.25f)
                            .testTag("add_item_save_btn")
                    ) {
                        Text(
                            text = tr("Save item", "आइटम सेव करें"),
                            fontSize = 15.5.sp,
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
            // Top Header
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
                        .testTag("add_item_back_btn")
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
                        text = if (isEditing) tr("Edit item", "आइटम संपादित करें") else tr("Add item", "आइटम जोड़ें"),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FlatTextPrimary
                    )
                    Text(
                        text = if (isEditing) tr("Update stock item", "स्टॉक आइटम अपडेट करें") else tr("New stock item", "नया स्टॉक आइटम"),
                        fontSize = 13.sp,
                        color = FlatTextSecondary
                    )
                }
            }

            // ITEM NAME *
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel(text = tr("ITEM NAME *", "आइटम का नाम *"))
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    placeholder = {
                        Text("Cattle Feed", color = FlatTextMuted, fontSize = 15.sp)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = inventoryTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_item_name_input")
                )
            }

            // HINDI NAME (OPTIONAL)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel(text = tr("HINDI NAME (OPTIONAL)", "हिंदी नाम (वैकल्पिक)"))
                OutlinedTextField(
                    value = hindiName,
                    onValueChange = { hindiName = it },
                    placeholder = {
                        Text("पशु आहार", color = FlatTextMuted, fontSize = 15.sp)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = inventoryTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_item_hindi_name_input")
                )
            }

            // CATEGORY
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel(text = tr("CATEGORY", "श्रेणी"))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                        Surface(
                            onClick = { selectedCategory = cat },
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) InvBrandGreen else InvChipUnselectedBg,
                            modifier = Modifier.testTag("category_chip_${cat.lowercase()}")
                        ) {
                            Text(
                                text = cat,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else FlatTextPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }

            // SOLD AS
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel(text = tr("SOLD AS", "बिक्री का प्रकार"))
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
                        val looseSelected = isLooseOnly
                        Surface(
                            onClick = { isLooseOnly = true },
                            shape = RoundedCornerShape(11.dp),
                            color = if (looseSelected) InvBrandGreen else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sold_as_loose_only_btn")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 11.dp)
                            ) {
                                Text(
                                    text = tr("Loose kg only", "केवल खुला किलो"),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (looseSelected) Color.White else FlatTextPrimary
                                )
                            }
                        }

                        Surface(
                            onClick = { isLooseOnly = false },
                            shape = RoundedCornerShape(11.dp),
                            color = if (!looseSelected) InvBrandGreen else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sold_as_bag_plus_loose_btn")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 11.dp)
                            ) {
                                Text(
                                    text = tr("Bag + loose kg", "बोरी + खुला किलो"),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!looseSelected) Color.White else FlatTextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // BAG SIZE & REORDER BELOW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isLooseOnly) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FieldLabel(text = tr("BAG SIZE", "बोरी का वज़न"))
                        OutlinedTextField(
                            value = bagSizeInput,
                            onValueChange = { bagSizeInput = it.filter { c -> c.isDigit() || c == '.' } },
                            trailingIcon = {
                                Text(
                                    text = "kg",
                                    fontSize = 13.5.sp,
                                    color = FlatTextSecondary,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = inventoryTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_item_bag_size_input")
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FieldLabel(text = tr("REORDER BELOW", "री-ऑर्डर सीमा"))
                    OutlinedTextField(
                        value = reorderInput,
                        onValueChange = { reorderInput = it.filter { c -> c.isDigit() || c == '.' } },
                        trailingIcon = {
                            Text(
                                text = if (isLooseOnly) "kg" else "bags",
                                fontSize = 13.5.sp,
                                color = FlatTextSecondary,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = inventoryTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_item_reorder_input")
                    )
                }
            }

            // OPENING BAGS & OPENING LOOSE KG
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isLooseOnly) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FieldLabel(text = tr("OPENING BAGS", "शुरुआती बोरी"))
                        OutlinedTextField(
                            value = openingBagsInput,
                            onValueChange = { openingBagsInput = it.filter { c -> c.isDigit() } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = inventoryTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_item_opening_bags_input")
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FieldLabel(text = tr("OPENING LOOSE KG", "शुरुआती खुला किलो"))
                    OutlinedTextField(
                        value = openingLooseKgInput,
                        onValueChange = { openingLooseKgInput = it.filter { c -> c.isDigit() || c == '.' } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = inventoryTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_item_opening_loose_input")
                    )
                }
            }

            // COST PER BAG (ONLY YOU) & SUPPLIER (Unmasked)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FieldLabel(
                        text = if (isLooseOnly) {
                            tr("COST PER KG (ONLY YOU)", "प्रति किलो लागत (केवल आप)")
                        } else {
                            tr("COST PER BAG (ONLY YOU)", "प्रति बोरी लागत (केवल आप)")
                        }
                    )
                    OutlinedTextField(
                        value = costInput,
                        onValueChange = { costInput = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
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
                        colors = inventoryTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_item_cost_input")
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FieldLabel(text = tr("SUPPLIER", "सप्लायर"))
                    Box {
                        OutlinedTextField(
                            value = supplierName,
                            onValueChange = { supplierName = it },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = FlatTextSecondary,
                                    modifier = Modifier.clickable {
                                        supplierDropdownExpanded = !supplierDropdownExpanded
                                    }
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = inventoryTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_item_supplier_input")
                        )
                        DropdownMenu(
                            expanded = supplierDropdownExpanded,
                            onDismissRequest = { supplierDropdownExpanded = false },
                            modifier = Modifier.background(FlatDarkCard)
                        ) {
                            supplierOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option,
                                            color = FlatTextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    onClick = {
                                        supplierName = option
                                        supplierDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Set selling rates card
            Surface(
                onClick = { showRatesDialog = true },
                shape = RoundedCornerShape(16.dp),
                color = InvAmberBannerBg,
                border = BorderStroke(1.dp, InvAmberBannerBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_item_set_rates_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocalOffer,
                            contentDescription = null,
                            tint = InvAmberText,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = tr("Set selling rates", "बिक्री भाव सेट करें"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6B3E08)
                            )
                            Text(
                                text = tr(
                                    "1 kg, 5 kg and per bag, in Today's rates",
                                    "1 किलो, 5 किलो और प्रति बोरी, आज के भाव में"
                                ),
                                fontSize = 12.5.sp,
                                color = InvAmberText
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = InvAmberText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    if (showRatesDialog) {
        val parsedBagKg = bagSizeInput.trim().toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal("50")
        QuickSellingRatesDialog(
            productName = itemName.ifBlank { "New Item" },
            isBagged = !isLooseOnly,
            kgPerBag = parsedBagKg,
            initial1KgRate = looseRate1Kg,
            initialBagPrice = bagSellingPrice,
            onDismiss = { showRatesDialog = false },
            onSave = { new1Kg, newBag ->
                looseRate1Kg = new1Kg
                bagSellingPrice = newBag
                showRatesDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InventoryUpdateStockView(
    product: Product,
    meta: InventoryItemMeta,
    initialMode: UpdateStockMode,
    initialReason: String = "Damaged",
    availableSuppliers: List<String>,
    onBack: () -> Unit,
    onStartFullStockCount: (() -> Unit)? = null,
    onConfirmStockIn: (
        bagsReceived: Int,
        looseKgReceived: BigDecimal,
        costPerBagOrKg: BigDecimal,
        supplier: String,
        paymentMode: StockInPaymentMode
    ) -> Unit,
    onConfirmAdjust: (
        deltaKg: BigDecimal,
        reason: String,
        note: String,
        photoUri: String?,
        stockLossCost: BigDecimal,
        addToExpenses: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val isBagged = !product.isLooseOnly && product.kgPerBag > BigDecimal.ONE
    var mode by remember { mutableStateOf(initialMode) }

    var bagsCount by remember {
        mutableIntStateOf(
            if (initialMode == UpdateStockMode.STOCK_IN && isBagged) 20 else if (isBagged) 1 else 0
        )
    }
    var looseKgCount by remember {
        mutableIntStateOf(
            if (!isBagged && initialMode == UpdateStockMode.STOCK_IN) 50 else if (!isBagged) 5 else 0
        )
    }

    val defaultCostPerBagOrKg = remember(product) {
        val raw = if (isBagged) {
            product.averagePurchaseCostPerKg.multiply(product.kgPerBag).setScale(0, RoundingMode.HALF_UP)
        } else {
            product.averagePurchaseCostPerKg.setScale(0, RoundingMode.HALF_UP)
        }
        if (raw > BigDecimal.ZERO) raw.toPlainString() else "1150"
    }
    var costPerUnitInput by remember { mutableStateOf(defaultCostPerBagOrKg) }

    val effectiveCostPerKg = remember(product) {
        if (product.averagePurchaseCostPerKg > BigDecimal.ZERO) {
            product.averagePurchaseCostPerKg
        } else if (isBagged && product.kgPerBag > BigDecimal.ZERO) {
            BigDecimal("1150").divide(product.kgPerBag, 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal("23")
        }
    }

    val todayFormatted = remember {
        "Today, " + SimpleDateFormat("d MMM", Locale.ENGLISH).format(Date())
    }

    val supplierOptions = remember(availableSuppliers, meta) {
        val defaults = listOf(meta.supplierName, "Sharma Traders", "Goyal Feeds", "Kisan Agro")
        (defaults + availableSuppliers).filter { it.isNotBlank() }.distinctBy { it.lowercase() }
    }
    var selectedSupplier by remember { mutableStateOf(meta.supplierName.ifBlank { "Sharma Traders" }) }
    var supplierDropdownExpanded by remember { mutableStateOf(false) }

    var selectedPayment by remember { mutableStateOf(StockInPaymentMode.CASH) }

    // Adjust mode state (Pages 1 & 2 of KhataWise – Inventory, cash & reports extras.pdf)
    var adjustReason by remember { mutableStateOf(initialReason) }
    val adjustReasons = listOf("Damaged", "Spilled", "Expired", "Count correction", "Other")
    val isCountCorrection = mode == UpdateStockMode.ADJUST && adjustReason == "Count correction"

    var adjustNote by remember(adjustReason) {
        mutableStateOf(
            if (adjustReason == "Count correction") "" else "Torn bag in godown"
        )
    }
    var attachedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var addToExpensesAsStockLoss by remember { mutableStateOf(true) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            attachedPhotoUri = uri
        }
    }

    // Count stock mode inputs (Page 2: SYSTEM SAYS vs YOU COUNTED)
    val systemStockKg = remember(product.currentStockKg) {
        product.currentStockKg.max(BigDecimal.ZERO)
    }
    val initialCountedBagsStr = remember(product) {
        if (!isBagged) {
            "0"
        } else {
            val fullBags = systemStockKg.divideToIntegralValue(product.kgPerBag).toInt()
            (fullBags - 1).coerceAtLeast(0).toString()
        }
    }
    val initialCountedKgStr = remember(product) {
        if (!isBagged) {
            systemStockKg.subtract(BigDecimal("5")).max(BigDecimal.ZERO).stripTrailingZeros().toPlainString()
        } else {
            val rem = systemStockKg.remainder(product.kgPerBag)
            if (rem > BigDecimal.ZERO) rem.stripTrailingZeros().toPlainString() else "20"
        }
    }
    var countedBagsInput by remember(product.id) { mutableStateOf(initialCountedBagsStr) }
    var countedLooseKgInput by remember(product.id) { mutableStateOf(initialCountedKgStr) }

    val countedTotalKg = remember(countedBagsInput, countedLooseKgInput, isBagged, product.kgPerBag) {
        val b = countedBagsInput.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
        val k = countedLooseKgInput.trim().toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
        if (isBagged) {
            product.kgPerBag.multiply(BigDecimal(b)).add(k)
        } else {
            k
        }
    }

    val countDiffKg = remember(countedTotalKg, systemStockKg) {
        countedTotalKg.subtract(systemStockKg)
    }

    val entryDeltaKg = remember(
        bagsCount,
        looseKgCount,
        isBagged,
        product.kgPerBag,
        mode,
        isCountCorrection,
        countDiffKg
    ) {
        if (isCountCorrection) {
            countDiffKg
        } else {
            val totalKg = if (isBagged) {
                product.kgPerBag.multiply(BigDecimal(bagsCount)).add(BigDecimal(looseKgCount))
            } else {
                BigDecimal(looseKgCount)
            }
            if (mode == UpdateStockMode.ADJUST) {
                totalKg.negate()
            } else {
                totalKg
            }
        }
    }

    val afterStockKg = remember(product.currentStockKg, entryDeltaKg) {
        product.currentStockKg.add(entryDeltaKg).max(BigDecimal.ZERO)
    }

    val afterStockLabel = remember(afterStockKg, isBagged, product.kgPerBag) {
        if (isBagged) {
            val bags = afterStockKg.divide(product.kgPerBag, 1, RoundingMode.HALF_UP).stripTrailingZeros()
            val bagsStr = if (bags.scale() <= 0) bags.toBigInteger().toString() else bags.toPlainString()
            "$bagsStr ${if (bagsStr == "1") "bag" else "bags"}"
        } else {
            "${CurrencyUtils.formatWeight(afterStockKg)} kg"
        }
    }

    val stockLossAtCost = remember(entryDeltaKg, effectiveCostPerKg) {
        val removedKg = if (entryDeltaKg < BigDecimal.ZERO) entryDeltaKg.abs() else BigDecimal.ZERO
        removedKg.multiply(effectiveCostPerKg).setScale(0, RoundingMode.HALF_UP)
    }

    val purchaseTotal = remember(bagsCount, looseKgCount, costPerUnitInput, isBagged, product.kgPerBag) {
        val unitCost = costPerUnitInput.replace(",", "").trim().toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
        if (isBagged) {
            val bagCost = unitCost.multiply(BigDecimal(bagsCount))
            val looseCost = if (product.kgPerBag > BigDecimal.ZERO && looseKgCount > 0) {
                unitCost.divide(product.kgPerBag, 4, RoundingMode.HALF_UP).multiply(BigDecimal(looseKgCount))
            } else {
                BigDecimal.ZERO
            }
            bagCost.add(looseCost).setScale(0, RoundingMode.HALF_UP)
        } else {
            unitCost.multiply(BigDecimal(looseKgCount)).setScale(0, RoundingMode.HALF_UP)
        }
    }

    val removeQtyDesc = remember(bagsCount, looseKgCount, isBagged) {
        when {
            isBagged && bagsCount > 0 && looseKgCount > 0 -> "$bagsCount ${if (bagsCount == 1) "bag" else "bags"} + $looseKgCount kg"
            isBagged && bagsCount > 0 -> "$bagsCount ${if (bagsCount == 1) "bag" else "bags"}"
            looseKgCount > 0 -> "$looseKgCount kg"
            isBagged -> "1 bag"
            else -> "1 kg"
        }
    }

    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = InvBrandGreen,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = Color(0xFFD1D5DB),
        uncheckedBorderColor = Color.Transparent
    )

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
                    if (mode == UpdateStockMode.STOCK_IN) {
                        val hasValidQty = bagsCount > 0 || looseKgCount > 0
                        Button(
                            onClick = {
                                if (!hasValidQty) return@Button
                                val unitCost = costPerUnitInput.replace(",", "").trim()
                                    .toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
                                onConfirmStockIn(
                                    if (isBagged) bagsCount else 0,
                                    BigDecimal(looseKgCount),
                                    unitCost,
                                    selectedSupplier.trim().ifBlank { "Sharma Traders" },
                                    selectedPayment
                                )
                            },
                            enabled = hasValidQty,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = InvBrandGreen,
                                contentColor = Color.White,
                                disabledContainerColor = InvBrandGreen.copy(alpha = 0.4f),
                                disabledContentColor = Color.White.copy(alpha = 0.7f)
                            ),
                            contentPadding = PaddingValues(vertical = 15.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("update_stock_submit_btn")
                        ) {
                            Text(
                                text = "Add $removeQtyDesc to stock",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    } else if (isCountCorrection) {
                        // Page 2 bottom button: "Save count"
                        Button(
                            onClick = {
                                onConfirmAdjust(
                                    entryDeltaKg,
                                    adjustReason,
                                    adjustNote.trim().ifBlank { "Monthly count" },
                                    attachedPhotoUri?.toString(),
                                    stockLossAtCost,
                                    addToExpensesAsStockLoss && stockLossAtCost > BigDecimal.ZERO
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = InvBrandGreen,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(vertical = 15.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_count_submit_btn")
                        ) {
                            Text(
                                text = tr("Save count", "गिनती सेव करें"),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    } else {
                        // Page 1 bottom buttons: Cancel | Remove 1 bag (in rust/amber #A84308)
                        val hasValidQty = bagsCount > 0 || looseKgCount > 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onBack,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, FlatDarkBorder),
                                contentPadding = PaddingValues(vertical = 14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("adjust_stock_cancel_btn")
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
                                    if (!hasValidQty) return@Button
                                    onConfirmAdjust(
                                        entryDeltaKg,
                                        adjustReason,
                                        adjustNote.trim(),
                                        attachedPhotoUri?.toString(),
                                        stockLossAtCost,
                                        addToExpensesAsStockLoss && stockLossAtCost > BigDecimal.ZERO
                                    )
                                },
                                enabled = hasValidQty,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = InvRustButton,
                                    contentColor = Color.White,
                                    disabledContainerColor = InvRustButton.copy(alpha = 0.4f),
                                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                                ),
                                contentPadding = PaddingValues(vertical = 14.dp),
                                modifier = Modifier
                                    .weight(1.35f)
                                    .testTag("update_stock_submit_btn")
                            ) {
                                Text(
                                    text = "Remove $removeQtyDesc",
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
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
            // 1. Top Header ("Update stock" or "Count stock" when Count correction is selected)
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
                        .testTag("update_stock_back_btn")
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
                        text = if (isCountCorrection) {
                            tr("Count stock", "स्टॉक गिनें")
                        } else {
                            tr("Update stock", "स्टॉक अपडेट करें")
                        },
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FlatTextPrimary
                    )
                    Text(
                        text = formatProductCardTitle(product),
                        fontSize = 13.sp,
                        color = FlatTextSecondary
                    )
                }
            }

            // 2. Mode Toggle: Stock in | Adjust
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
                    val isStockIn = mode == UpdateStockMode.STOCK_IN
                    Surface(
                        onClick = { mode = UpdateStockMode.STOCK_IN },
                        shape = RoundedCornerShape(11.dp),
                        color = if (isStockIn) InvBrandGreen else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_stock_in_tab")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 11.dp)
                        ) {
                            Text(
                                text = tr("Stock in", "स्टॉक इन"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isStockIn) Color.White else FlatTextPrimary
                            )
                        }
                    }

                    Surface(
                        onClick = { mode = UpdateStockMode.ADJUST },
                        shape = RoundedCornerShape(11.dp),
                        color = if (!isStockIn) InvBrandGreen else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_adjust_tab")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 11.dp)
                        ) {
                            Text(
                                text = tr("Adjust", "समायोजन"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isStockIn) Color.White else FlatTextPrimary
                            )
                        }
                    }
                }
            }

            if (mode == UpdateStockMode.STOCK_IN) {
                // 3. Current stock -> After this entry Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
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
                        Column {
                            Text(
                                text = tr("Current stock", "वर्तमान स्टॉक"),
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatProductQuantityLabel(product),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = FlatTextPrimary
                            )
                        }

                        Text(
                            text = "→",
                            fontSize = 18.sp,
                            color = FlatTextSecondary,
                            fontWeight = FontWeight.Bold
                        )

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = tr("After this entry", "इस एंट्री के बाद"),
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = afterStockLabel,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = InvBrandGreen,
                                modifier = Modifier.testTag("after_stock_entry_text")
                            )
                        }
                    }
                }

                // 4. QUANTITY RECEIVED Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = tr("QUANTITY RECEIVED", "प्राप्त मात्रा"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.6.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (isBagged) {
                                QuantityStepperBox(
                                    title = "BAGS",
                                    value = bagsCount,
                                    onValueChange = { bagsCount = it.coerceAtLeast(0) },
                                    modifier = Modifier.weight(1f),
                                    tagPrefix = "stepper_bags"
                                )
                            }

                            QuantityStepperBox(
                                title = "LOOSE KG",
                                value = looseKgCount,
                                onValueChange = { looseKgCount = it.coerceAtLeast(0) },
                                modifier = Modifier.weight(1f),
                                tagPrefix = "stepper_loose_kg"
                            )
                        }
                    }
                }

                // 5. COST PER BAG & DATE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FieldLabel(
                            text = if (isBagged) tr("COST PER BAG", "प्रति बोरी लागत") else tr("COST PER KG", "प्रति किलो लागत")
                        )
                        OutlinedTextField(
                            value = costPerUnitInput,
                            onValueChange = { costPerUnitInput = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
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
                            colors = inventoryTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("stock_in_cost_input")
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FieldLabel(text = tr("DATE", "तारीख़"))
                        OutlinedTextField(
                            value = todayFormatted,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = inventoryTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("stock_in_date_box")
                        )
                    }
                }

                // 6. SUPPLIER
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel(text = tr("SUPPLIER", "सप्लायर"))
                    Box {
                        OutlinedTextField(
                            value = selectedSupplier,
                            onValueChange = { selectedSupplier = it },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = FlatTextSecondary,
                                    modifier = Modifier.clickable {
                                        supplierDropdownExpanded = !supplierDropdownExpanded
                                    }
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = inventoryTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("stock_in_supplier_input")
                        )
                        DropdownMenu(
                            expanded = supplierDropdownExpanded,
                            onDismissRequest = { supplierDropdownExpanded = false },
                            modifier = Modifier.background(FlatDarkCard)
                        ) {
                            supplierOptions.forEach { sup ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = sup,
                                            color = FlatTextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    onClick = {
                                        selectedSupplier = sup
                                        supplierDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 7. PAYMENT: Paid cash | UPI | On credit
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel(text = tr("PAYMENT", "भुगतान"))
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
                            StockInPaymentMode.entries.forEach { payMode ->
                                val isSelected = selectedPayment == payMode
                                Surface(
                                    onClick = { selectedPayment = payMode },
                                    shape = RoundedCornerShape(11.dp),
                                    color = if (isSelected) InvBrandGreen else Color.Transparent,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("payment_mode_${payMode.name.lowercase()}")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 11.dp)
                                    ) {
                                        Text(
                                            text = tr(payMode.labelEn, payMode.labelHi),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else FlatTextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 8. Purchase total (only you)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = InvSoftSurface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr("Purchase total (only you)", "कुल खरीद राशि (केवल आप)"),
                            fontSize = 14.sp,
                            color = FlatTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = CurrencyUtils.formatInr(purchaseTotal),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FlatTextPrimary,
                            modifier = Modifier.testTag("stock_in_purchase_total_text")
                        )
                    }
                }
            } else if (!isCountCorrection) {
                // ================================================================
                // PAGE 1: UPDATE STOCK (ADJUST - Damaged / Spilled / Expired / Other)
                // ================================================================
                // 3. Current stock -> After this entry Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
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
                        Column {
                            Text(
                                text = tr("Current stock", "वर्तमान स्टॉक"),
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatProductQuantityLabel(product),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = FlatTextPrimary
                            )
                        }

                        Text(
                            text = "→",
                            fontSize = 18.sp,
                            color = FlatTextSecondary,
                            fontWeight = FontWeight.Bold
                        )

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = tr("After this entry", "इस एंट्री के बाद"),
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = afterStockLabel,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = InvRustButton,
                                modifier = Modifier.testTag("after_stock_entry_text")
                            )
                        }
                    }
                }

                // 4. REASON Chips: Damaged | Spilled | Expired | Count correction | Other
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel(text = tr("REASON", "कारण"))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        adjustReasons.forEach { reasonOption ->
                            val isSelected = adjustReason == reasonOption
                            Surface(
                                onClick = { adjustReason = reasonOption },
                                shape = RoundedCornerShape(50),
                                color = if (isSelected) InvBrandGreen else InvChipUnselectedBg,
                                modifier = Modifier.testTag("adjust_reason_${reasonOption.lowercase().replace(" ", "_")}")
                            ) {
                                Text(
                                    text = reasonOption,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else FlatTextPrimary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                                )
                            }
                        }
                    }
                }

                // 5. QUANTITY TO REMOVE Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = tr("QUANTITY TO REMOVE", "हटाने की मात्रा"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.6.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (isBagged) {
                                QuantityStepperBox(
                                    title = "BAGS",
                                    value = bagsCount,
                                    onValueChange = { bagsCount = it.coerceAtLeast(0) },
                                    modifier = Modifier.weight(1f),
                                    tagPrefix = "stepper_bags"
                                )
                            }

                            QuantityStepperBox(
                                title = "LOOSE KG",
                                value = looseKgCount,
                                onValueChange = { looseKgCount = it.coerceAtLeast(0) },
                                modifier = Modifier.weight(1f),
                                tagPrefix = "stepper_loose_kg"
                            )
                        }
                    }
                }

                // 6. NOTE Input
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel(text = tr("NOTE", "नोट"))
                    OutlinedTextField(
                        value = adjustNote,
                        onValueChange = { adjustNote = it },
                        placeholder = {
                            Text(
                                text = tr("Torn bag in godown", "गोदाम में फटी बोरी"),
                                color = FlatTextMuted,
                                fontSize = 14.5.sp
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = inventoryTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("adjust_stock_note_input")
                    )
                }

                // 7. Dashed "+ Attach photo" Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .drawBehind {
                            drawRoundRect(
                                color = InvBrandGreen,
                                style = Stroke(
                                    width = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f), 0f)
                                ),
                                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                            )
                        }
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .testTag("adjust_attach_photo_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (attachedPhotoUri != null) {
                            tr("✓ Photo attached", "✓ फ़ोटो जुड़ गई")
                        } else {
                            tr("+ Attach photo", "+ फ़ोटो जोड़ें")
                        },
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = InvBrandGreen
                    )
                }

                // 8. Stock loss at cost & Add to expenses as Stock loss Card
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
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("Stock loss at cost", "लागत पर स्टॉक नुकसान"),
                                fontSize = 14.sp,
                                color = FlatTextSecondary
                            )
                            Text(
                                text = CurrencyUtils.formatInr(stockLossAtCost),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = InvRedText,
                                modifier = Modifier.testTag("adjust_stock_loss_cost_text")
                            )
                        }

                        HorizontalDivider(color = FlatDarkBorder)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = tr("Add to expenses as Stock loss", "स्टॉक नुकसान के रूप में ख़र्च में जोड़ें"),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = FlatTextPrimary
                                )
                                Text(
                                    text = tr("Shows in Cashbook and expense report", "कैशबुक और ख़र्च रिपोर्ट में दिखेगा"),
                                    fontSize = 12.5.sp,
                                    color = FlatTextSecondary
                                )
                            }

                            Switch(
                                checked = addToExpensesAsStockLoss,
                                onCheckedChange = { addToExpensesAsStockLoss = it },
                                colors = switchColors,
                                modifier = Modifier.testTag("adjust_add_to_expenses_switch")
                            )
                        }
                    }
                }
            } else {
                // ================================================================
                // PAGE 2: COUNT STOCK (ADJUST - Count correction)
                // ================================================================
                // 3. REASON Chips: Damaged | Spilled | Expired | Count correction | Other
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel(text = tr("REASON", "कारण"))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        adjustReasons.forEach { reasonOption ->
                            val isSelected = adjustReason == reasonOption
                            Surface(
                                onClick = { adjustReason = reasonOption },
                                shape = RoundedCornerShape(50),
                                color = if (isSelected) InvBrandGreen else InvChipUnselectedBg,
                                modifier = Modifier.testTag("adjust_reason_${reasonOption.lowercase().replace(" ", "_")}")
                            ) {
                                Text(
                                    text = reasonOption,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else FlatTextPrimary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                                )
                            }
                        }
                    }
                }

                // 4. SYSTEM SAYS Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
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
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = tr("SYSTEM SAYS", "सिस्टम स्टॉक"),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.6.sp
                            )
                            Text(
                                text = formatProductQuantityLabel(product),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = FlatTextPrimary
                            )
                        }

                        Text(
                            text = "${CurrencyUtils.formatWeight(systemStockKg)} KG",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FlatTextSecondary
                        )
                    }
                }

                // 5. YOU COUNTED Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
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
                            text = tr("YOU COUNTED", "आपने गिना"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.6.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (isBagged) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = FlatDarkCard,
                                    border = BorderStroke(1.dp, FlatDarkBorder)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 13.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        BasicTextField(
                                            value = countedBagsInput,
                                            onValueChange = { raw ->
                                                countedBagsInput = raw.filter { it.isDigit() }.take(5)
                                            },
                                            textStyle = TextStyle(
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = FlatTextPrimary
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            cursorBrush = SolidColor(InvBrandGreen),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("counted_bags_input")
                                        )
                                        Text(
                                            text = "bags",
                                            fontSize = 14.sp,
                                            color = FlatTextSecondary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = FlatDarkCard,
                                border = BorderStroke(1.dp, FlatDarkBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 13.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BasicTextField(
                                        value = countedLooseKgInput,
                                        onValueChange = { raw ->
                                            countedLooseKgInput = raw.filter { it.isDigit() || it == '.' }.take(6)
                                        },
                                        textStyle = TextStyle(
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = FlatTextPrimary
                                        ),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        cursorBrush = SolidColor(InvBrandGreen),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("counted_kg_input")
                                    )
                                    Text(
                                        text = "kg",
                                        fontSize = 14.sp,
                                        color = FlatTextSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // 6. Difference Card
                val isShort = countDiffKg < BigDecimal.ZERO
                val diffBg = if (isShort) Color(0xFFFCE8E8) else InvMintBg
                val diffBorder = if (isShort) Color(0xFFF5BABA) else Color(0xFFB8E6CE)
                val diffColor = if (isShort) InvRedText else InvBrandGreen
                val diffSign = if (countDiffKg < BigDecimal.ZERO) "−" else if (countDiffKg > BigDecimal.ZERO) "+" else ""

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = diffBg,
                    border = BorderStroke(1.dp, diffBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = tr("Difference", "अंतर"),
                                fontSize = 12.5.sp,
                                color = diffColor
                            )
                            Text(
                                text = "$diffSign${CurrencyUtils.formatWeight(countDiffKg.abs())} kg",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = diffColor,
                                modifier = Modifier.testTag("count_difference_text")
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Counted ${CurrencyUtils.formatWeight(countedTotalKg)} kg",
                                fontSize = 13.sp,
                                color = Color(0xFF4B1D1D),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "System ${CurrencyUtils.formatWeight(systemStockKg)} kg",
                                fontSize = 13.sp,
                                color = Color(0xFF4B1D1D),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // 7. Stock loss at cost Card
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
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("Stock loss at cost", "लागत पर स्टॉक नुकसान"),
                                fontSize = 14.sp,
                                color = FlatTextSecondary
                            )
                            Text(
                                text = CurrencyUtils.formatInr(stockLossAtCost),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = InvRedText
                            )
                        }

                        HorizontalDivider(color = FlatDarkBorder)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = tr("Add to expenses as Stock loss", "स्टॉक नुकसान के रूप में ख़र्च में जोड़ें"),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = FlatTextPrimary
                                )
                                Text(
                                    text = "${CurrencyUtils.formatInr(effectiveCostPerKg.setScale(0, RoundingMode.HALF_UP))} per kg",
                                    fontSize = 12.5.sp,
                                    color = FlatTextSecondary
                                )
                            }

                            Switch(
                                checked = addToExpensesAsStockLoss,
                                onCheckedChange = { addToExpensesAsStockLoss = it },
                                colors = switchColors
                            )
                        }
                    }
                }

                // 8. NOTE Field
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel(text = tr("NOTE", "नोट"))
                    OutlinedTextField(
                        value = adjustNote,
                        onValueChange = { adjustNote = it },
                        placeholder = {
                            Text(
                                text = tr("Monthly count", "मासिक गिनती"),
                                color = FlatTextMuted,
                                fontSize = 14.5.sp
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = inventoryTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("count_stock_note_input")
                    )
                }

                // 9. "Start full stock count for all items" link
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tr("Start full stock count for all items", "सभी आइटम की पूरी स्टॉक गिनती शुरू करें"),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = InvBrandGreen,
                        modifier = Modifier
                            .clickable {
                                if (onStartFullStockCount != null) {
                                    onStartFullStockCount()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Save this item count to continue to the next item",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            .testTag("start_full_stock_count_btn")
                    )
                }
            }
        }
    }
}

@Composable
private fun QuantityStepperBox(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF6F6FA)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = FlatTextSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { onValueChange((value - 1).coerceAtLeast(0)) },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFE7E7F0),
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("${tagPrefix}_minus")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease",
                            tint = FlatTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                BasicTextField(
                    value = value.toString(),
                    onValueChange = { raw ->
                        val parsed = raw.filter { it.isDigit() }.take(4).toIntOrNull() ?: 0
                        onValueChange(parsed)
                    },
                    textStyle = TextStyle(
                        fontSize = 23.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FlatTextPrimary,
                        textAlign = TextAlign.Center
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    cursorBrush = SolidColor(InvBrandGreen),
                    modifier = Modifier
                        .width(56.dp)
                        .testTag("${tagPrefix}_input")
                )

                Surface(
                    onClick = { onValueChange(value + 1) },
                    shape = RoundedCornerShape(10.dp),
                    color = InvBrandGreen,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("${tagPrefix}_plus")
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

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        color = FlatTextSecondary,
        letterSpacing = 0.5.sp
    )
}

@Composable
private fun inventoryTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = FlatDarkCard,
    unfocusedContainerColor = FlatDarkCard,
    focusedBorderColor = InvBrandGreen,
    unfocusedBorderColor = FlatDarkBorder,
    focusedTextColor = FlatTextPrimary,
    unfocusedTextColor = FlatTextPrimary
)
