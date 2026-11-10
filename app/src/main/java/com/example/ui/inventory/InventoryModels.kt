package com.example.ui.inventory

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.example.model.Customer
import com.example.model.Product
import com.example.model.PurchaseEntry
import com.example.model.PurchaseInvoice
import com.example.model.SaleEntry
import com.example.util.CurrencyUtils
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

// Brand & semantic colors matching the Inventory PDFs
internal val InvBrandGreen = Color(0xFF0E6F43)
internal val InvMintBg = Color(0xFFDDF3E8)
internal val InvAmberText = Color(0xFF8A4B08)
internal val InvAmberProgress = Color(0xFFD97706)
internal val InvAmberSoftBg = Color(0xFFFCEBD2)
internal val InvAmberBannerBg = Color(0xFFFEF6E7)
internal val InvAmberBannerBorder = Color(0xFFF3D59B)
internal val InvRustButton = Color(0xFFA84308)
internal val InvRedText = Color(0xFFB91C1C)
internal val InvRedSoftBg = Color(0xFFFCE4E4)
internal val InvBlueSoftBg = Color(0xFFE0F2FE)
internal val InvBlueText = Color(0xFF0369A1)
internal val InvTrackGray = Color(0xFFECECF2)
internal val InvSoftSurface = Color(0xFFF3F3F9)
internal val InvChipUnselectedBg = Color(0xFFF1F1F7)

enum class InventoryFilter(val labelEn: String, val labelHi: String) {
    ALL("All", "सभी"),
    LOW("Low", "कम"),
    OUT("Out", "ख़त्म"),
    BAGGED("Bagged", "बोरी"),
    LOOSE("Loose", "खुला")
}

enum class InventoryStockStatus {
    IN_STOCK,
    LOW,
    OUT_OF_STOCK
}

enum class UpdateStockMode {
    STOCK_IN,
    ADJUST
}

enum class StockInPaymentMode(val labelEn: String, val labelHi: String, val dbStatus: String) {
    CASH("Paid cash", "नकद भुगतान", "PAID"),
    UPI("UPI", "यूपीआई", "PAID_UPI"),
    CREDIT("On credit", "उधार पर", "UNPAID")
}

data class InventoryItemMeta(
    val hindiName: String = "",
    val category: String = "Feed",
    val reorderBags: Int = 8,
    val reorderLooseKg: BigDecimal = BigDecimal("50"),
    val supplierName: String = "",
    val barcode: String = ""
)

data class InventoryCategoryConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: Long
)

data class StockAdjustmentRecord(
    val id: String = UUID.randomUUID().toString(),
    val productId: String,
    val deltaKg: BigDecimal,
    val reason: String,
    val note: String = "",
    val photoUri: String? = null,
    val stockLossCost: BigDecimal = BigDecimal.ZERO,
    val addedToExpenses: Boolean = false,
    val actorName: String = "Nitin Nagar",
    val timestamp: Long = System.currentTimeMillis()
)

enum class MovementType {
    STOCK_IN,
    SALE_OUT,
    ADJUST,
    RETURN
}

data class InventoryMovementItem(
    val id: String,
    val productId: String = "",
    val productTitle: String = "",
    val type: MovementType,
    val title: String,
    val subtitle: String = "",
    val dateLabel: String,
    val quantityDeltaLabel: String,
    val badgeLabel: String? = null,
    val timestamp: Long
)

object InventoryMetaPrefs {
    private const val PREFS_NAME = "khatawise_inventory_meta_prefs"
    private const val KEY_ADJUSTMENTS = "stock_adjustments_json"
    private const val KEY_CATEGORIES = "inventory_categories_json"

    val defaultCategoryColors = listOf(
        0xFF0E6F43L, // Green
        0xFFD97706L, // Amber/Orange
        0xFF2563EBL, // Blue
        0xFF7C3AEDL, // Purple
        0xFF0891B2L, // Teal
        0xFFDB2777L  // Pink
    )

    private val defaultCategories = listOf(
        InventoryCategoryConfig(id = "cat_feed", name = "Feed", colorHex = 0xFF0E6F43L),
        InventoryCategoryConfig(id = "cat_khal", name = "Oil cakes (khal)", colorHex = 0xFFD97706L),
        InventoryCategoryConfig(id = "cat_grain", name = "Grain & churi", colorHex = 0xFF2563EBL),
        InventoryCategoryConfig(id = "cat_minerals", name = "Minerals & supplements", colorHex = 0xFF7C3AEDL)
    )

    fun loadCategories(context: Context): List<InventoryCategoryConfig> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CATEGORIES, null) ?: return defaultCategories
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<InventoryCategoryConfig>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val name = obj.optString("name").trim()
                if (name.isNotBlank()) {
                    list.add(
                        InventoryCategoryConfig(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            name = name,
                            colorHex = obj.optLong("colorHex", 0xFF0E6F43L)
                        )
                    )
                }
            }
            if (list.isEmpty()) defaultCategories else list
        } catch (_: Exception) {
            defaultCategories
        }
    }

    fun saveCategories(context: Context, categories: List<InventoryCategoryConfig>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        categories.forEach { cat ->
            val obj = JSONObject()
            obj.put("id", cat.id)
            obj.put("name", cat.name)
            obj.put("colorHex", cat.colorHex)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_CATEGORIES, arr.toString()).apply()
    }

    fun loadMeta(
        context: Context,
        product: Product,
        purchases: List<PurchaseEntry>,
        invoices: List<PurchaseInvoice>
    ): InventoryItemMeta {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultReorderBags = if (product.feedName.contains("Makka", ignoreCase = true)) 6 else 8
        val defaultReorderKg = BigDecimal("50")

        val latestPurchaseSupplier = purchases
            .filter { it.productId == product.id && it.wholesalerName.isNotBlank() }
            .maxByOrNull { it.date }
            ?.wholesalerName
            ?: invoices
                .filter { inv -> inv.wholesalerName.isNotBlank() }
                .maxByOrNull { it.date }
                ?.wholesalerName
            ?: ""

        val defaultCategory = when {
            product.feedName.contains("Cake", ignoreCase = true) ||
                product.feedName.contains("Khal", ignoreCase = true) ||
                product.feedName.contains("Mustard", ignoreCase = true) ||
                product.feedName.contains("Binola", ignoreCase = true) -> "Oil cakes (khal)"
            product.feedName.contains("Makka", ignoreCase = true) ||
                product.feedName.contains("Bajra", ignoreCase = true) ||
                product.feedName.contains("Jau", ignoreCase = true) ||
                product.feedName.contains("Churi", ignoreCase = true) ||
                product.feedName.contains("Bran", ignoreCase = true) ||
                product.feedName.contains("Chokar", ignoreCase = true) ||
                product.feedName.contains("Wheat", ignoreCase = true) -> "Grain & churi"
            product.feedName.contains("Mineral", ignoreCase = true) ||
                product.feedName.contains("Calcium", ignoreCase = true) ||
                product.feedName.contains("Salt", ignoreCase = true) ||
                product.feedName.contains("Namak", ignoreCase = true) -> "Minerals & supplements"
            else -> "Feed"
        }

        val hindiName = prefs.getString("${product.id}_hindiName", "") ?: ""
        val rawCategory = prefs.getString("${product.id}_category", defaultCategory) ?: defaultCategory
        val category = when (rawCategory) {
            "Grain" -> "Grain & churi"
            "Mineral" -> "Minerals & supplements"
            else -> rawCategory
        }
        val reorderBags = prefs.getInt("${product.id}_reorderBags", defaultReorderBags).coerceAtLeast(1)
        val reorderLooseKgStr = prefs.getString("${product.id}_reorderLooseKg", defaultReorderKg.toPlainString())
        val reorderLooseKg = reorderLooseKgStr?.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: defaultReorderKg
        val savedSupplier = prefs.getString("${product.id}_supplier", null)
        val supplierName = if (!savedSupplier.isNullOrBlank()) savedSupplier else latestPurchaseSupplier
        val barcode = prefs.getString("${product.id}_barcode", "") ?: ""

        return InventoryItemMeta(
            hindiName = hindiName,
            category = category,
            reorderBags = reorderBags,
            reorderLooseKg = reorderLooseKg,
            supplierName = supplierName,
            barcode = barcode
        )
    }

    fun saveMeta(context: Context, productId: String, meta: InventoryItemMeta) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("${productId}_hindiName", meta.hindiName.trim())
            .putString("${productId}_category", meta.category.trim().ifBlank { "Feed" })
            .putInt("${productId}_reorderBags", meta.reorderBags.coerceAtLeast(1))
            .putString("${productId}_reorderLooseKg", meta.reorderLooseKg.toPlainString())
            .putString("${productId}_supplier", meta.supplierName.trim())
            .putString("${productId}_barcode", meta.barcode.trim())
            .apply()
    }

    fun updateProductCategory(context: Context, productId: String, categoryName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("${productId}_category", categoryName.trim()).apply()
    }

    fun saveBarcodeForProduct(context: Context, productId: String, barcode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("${productId}_barcode", barcode.trim()).apply()
    }

    fun recordAdjustment(context: Context, record: StockAdjustmentRecord) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = loadAllAdjustmentsPublic(context).toMutableList()
        existing.add(0, record)
        val arr = JSONArray()
        existing.take(250).forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("productId", item.productId)
            obj.put("deltaKg", item.deltaKg.toPlainString())
            obj.put("reason", item.reason)
            obj.put("note", item.note)
            obj.put("photoUri", item.photoUri ?: "")
            obj.put("stockLossCost", item.stockLossCost.toPlainString())
            obj.put("addedToExpenses", item.addedToExpenses)
            obj.put("actorName", item.actorName)
            obj.put("timestamp", item.timestamp)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_ADJUSTMENTS, arr.toString()).apply()
    }

    fun loadAdjustmentsForProduct(context: Context, productId: String): List<StockAdjustmentRecord> {
        return loadAllAdjustmentsPublic(context).filter { it.productId == productId }
    }

    fun loadAllAdjustmentsPublic(context: Context): List<StockAdjustmentRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ADJUSTMENTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<StockAdjustmentRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    StockAdjustmentRecord(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        productId = obj.optString("productId", ""),
                        deltaKg = obj.optString("deltaKg", "0").toBigDecimalOrNull() ?: BigDecimal.ZERO,
                        reason = obj.optString("reason", "Damaged"),
                        note = obj.optString("note", ""),
                        photoUri = obj.optString("photoUri", "").takeIf { it.isNotBlank() },
                        stockLossCost = obj.optString("stockLossCost", "0").toBigDecimalOrNull() ?: BigDecimal.ZERO,
                        addedToExpenses = obj.optBoolean("addedToExpenses", false),
                        actorName = obj.optString("actorName", "Nitin Nagar"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}

internal fun computeStockStatus(product: Product, meta: InventoryItemMeta): InventoryStockStatus {
    if (product.currentStockKg <= BigDecimal.ZERO) {
        return InventoryStockStatus.OUT_OF_STOCK
    }
    return if (product.isLooseOnly || product.kgPerBag <= BigDecimal.ONE) {
        if (product.currentStockKg <= meta.reorderLooseKg) {
            InventoryStockStatus.LOW
        } else {
            InventoryStockStatus.IN_STOCK
        }
    } else {
        val reorderThresholdKg = product.kgPerBag.multiply(BigDecimal(meta.reorderBags))
        if (product.currentStockKg <= reorderThresholdKg) {
            InventoryStockStatus.LOW
        } else {
            InventoryStockStatus.IN_STOCK
        }
    }
}

internal fun formatProductBagsCount(product: Product): String {
    if (product.isLooseOnly || product.kgPerBag <= BigDecimal.ONE) {
        return CurrencyUtils.formatWeight(product.currentStockKg.max(BigDecimal.ZERO))
    }
    val bags = product.currentStockKg.max(BigDecimal.ZERO)
        .divide(product.kgPerBag, 1, RoundingMode.HALF_UP)
        .stripTrailingZeros()
    return if (bags.scale() <= 0) {
        bags.toBigInteger().toString()
    } else {
        bags.toPlainString()
    }
}

internal fun formatProductQuantityLabel(product: Product): String {
    return if (product.isLooseOnly || product.kgPerBag <= BigDecimal.ONE) {
        "${CurrencyUtils.formatWeight(product.currentStockKg.max(BigDecimal.ZERO))} kg"
    } else {
        val bagsStr = formatProductBagsCount(product)
        val isSingle = bagsStr == "1"
        "$bagsStr ${if (isSingle) "bag" else "bags"}"
    }
}

internal fun formatProductCardTitle(product: Product): String {
    val cleanName = product.feedName.trim()
    return if (!product.isLooseOnly && product.kgPerBag > BigDecimal.ONE && !cleanName.contains("(")) {
        "$cleanName (${CurrencyUtils.formatWeight(product.kgPerBag)} kg)"
    } else {
        cleanName
    }
}

internal fun formatProductCardSubtitle(product: Product, meta: InventoryItemMeta): String {
    return if (product.isLooseOnly || product.kgPerBag <= BigDecimal.ONE) {
        "Loose · reorder at ${CurrencyUtils.formatWeight(meta.reorderLooseKg)} kg"
    } else {
        "Bagged · reorder at ${meta.reorderBags}"
    }
}

internal fun computeStockProgressFraction(product: Product, meta: InventoryItemMeta): Float {
    if (product.currentStockKg <= BigDecimal.ZERO) return 0f
    return if (product.isLooseOnly || product.kgPerBag <= BigDecimal.ONE) {
        val fullTargetKg = meta.reorderLooseKg.multiply(BigDecimal("4")).max(BigDecimal("200"))
        product.currentStockKg.divide(fullTargetKg, 3, RoundingMode.HALF_UP).toFloat().coerceIn(0.06f, 1f)
    } else {
        val fullTargetBags = (meta.reorderBags * 3).coerceAtLeast(25)
        val fullTargetKg = product.kgPerBag.multiply(BigDecimal(fullTargetBags))
        product.currentStockKg.divide(fullTargetKg, 3, RoundingMode.HALF_UP).toFloat().coerceIn(0.06f, 1f)
    }
}

internal fun computeReorderTickFraction(product: Product, meta: InventoryItemMeta): Float {
    return if (product.isLooseOnly || product.kgPerBag <= BigDecimal.ONE) {
        val fullTargetKg = meta.reorderLooseKg.multiply(BigDecimal("4")).max(BigDecimal("200"))
        meta.reorderLooseKg.divide(fullTargetKg, 3, RoundingMode.HALF_UP).toFloat().coerceIn(0.15f, 0.7f)
    } else {
        val fullTargetBags = (meta.reorderBags * 3).coerceAtLeast(25)
        (meta.reorderBags.toFloat() / fullTargetBags.toFloat()).coerceIn(0.15f, 0.7f)
    }
}

internal fun formatRelativeDateLabel(timestamp: Long): String {
    val nowCal = Calendar.getInstance()
    val targetCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameYear = nowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR)
    val dayDiff = nowCal.get(Calendar.DAY_OF_YEAR) - targetCal.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "Today"
        sameYear && dayDiff == 1 -> "Yesterday"
        else -> SimpleDateFormat("d MMM", Locale.ENGLISH).format(Date(timestamp))
    }
}

internal fun formatQtyDeltaForProduct(product: Product, kg: BigDecimal, sign: String): String {
    val isBagged = !product.isLooseOnly && product.kgPerBag > BigDecimal.ONE
    val absKg = kg.abs()
    return if (isBagged && absKg.remainder(product.kgPerBag).compareTo(BigDecimal.ZERO) == 0 && absKg >= product.kgPerBag) {
        val bags = absKg.divide(product.kgPerBag, 0, RoundingMode.HALF_UP)
        val bagsStr = bags.toBigInteger().toString()
        val unit = if (bagsStr == "1") "bag" else "bags"
        "$sign$bagsStr $unit"
    } else if (isBagged && absKg >= product.kgPerBag) {
        val bags = absKg.divide(product.kgPerBag, 1, RoundingMode.HALF_UP).stripTrailingZeros()
        val bagsStr = if (bags.scale() <= 0) bags.toBigInteger().toString() else bags.toPlainString()
        val unit = if (bagsStr == "1") "bag" else "bags"
        "$sign$bagsStr $unit"
    } else {
        "$sign${CurrencyUtils.formatWeight(absKg)} kg"
    }
}

internal fun buildProductMovements(
    product: Product,
    meta: InventoryItemMeta,
    purchases: List<PurchaseEntry>,
    sales: List<SaleEntry>,
    customers: List<Customer>,
    adjustments: List<StockAdjustmentRecord>
): List<InventoryMovementItem> {
    val customerMap = customers.associateBy { it.id }
    val items = mutableListOf<InventoryMovementItem>()
    val prodTitle = formatProductCardTitle(product)

    purchases
        .filter { !it.isDeleted && it.productId == product.id }
        .forEach { p ->
            val supplier = p.wholesalerName.ifBlank { meta.supplierName }
            items.add(
                InventoryMovementItem(
                    id = "pur_${p.id}",
                    productId = product.id,
                    productTitle = prodTitle,
                    type = MovementType.STOCK_IN,
                    title = "Stock in · $supplier",
                    subtitle = "Stock in · $supplier",
                    dateLabel = formatRelativeDateLabel(p.date),
                    quantityDeltaLabel = formatQtyDeltaForProduct(product, p.quantityKg, "+"),
                    timestamp = p.date
                )
            )
        }

    sales
        .filter { !it.isDeleted && it.billStatus != "CANCELLED" && it.productId == product.id }
        .forEachIndexed { idx, s ->
            val custName = customerMap[s.customerId]?.name ?: "Walk-in"
            val shortBillNo = (1000 + (s.date % 900)).toString()
            items.add(
                InventoryMovementItem(
                    id = "sale_${s.id}",
                    productId = product.id,
                    productTitle = prodTitle,
                    type = MovementType.SALE_OUT,
                    title = "Bill #$shortBillNo · $custName",
                    subtitle = "Bill #$shortBillNo · $custName",
                    dateLabel = formatRelativeDateLabel(s.date),
                    quantityDeltaLabel = formatQtyDeltaForProduct(product, s.quantityKg, "−"),
                    timestamp = s.date
                )
            )
            if (s.returnedKg > BigDecimal.ZERO) {
                val cnNum = (idx + 14).toString().padStart(3, '0')
                items.add(
                    InventoryMovementItem(
                        id = "ret_${s.id}",
                        productId = product.id,
                        productTitle = prodTitle,
                        type = MovementType.RETURN,
                        title = "Credit note #CN-$cnNum · $custName",
                        subtitle = "Credit note #CN-$cnNum · $custName",
                        dateLabel = formatRelativeDateLabel(s.updatedAt.takeIf { it > 0L } ?: s.date),
                        quantityDeltaLabel = formatQtyDeltaForProduct(product, s.returnedKg, "+"),
                        timestamp = s.updatedAt.takeIf { it > 0L } ?: (s.date + 3600_000L)
                    )
                )
            }
        }

    adjustments
        .filter { it.productId == product.id }
        .forEach { adj ->
            val sign = if (adj.deltaKg >= BigDecimal.ZERO) "+" else "−"
            items.add(
                InventoryMovementItem(
                    id = "adj_${adj.id}",
                    productId = product.id,
                    productTitle = prodTitle,
                    type = MovementType.ADJUST,
                    title = "Adjust · ${adj.reason}",
                    subtitle = "${adj.reason} · ${adj.actorName}",
                    dateLabel = formatRelativeDateLabel(adj.timestamp),
                    quantityDeltaLabel = formatQtyDeltaForProduct(product, adj.deltaKg, sign),
                    badgeLabel = "Adjust",
                    timestamp = adj.timestamp
                )
            )
        }

    return items.sortedByDescending { it.timestamp }.take(20)
}

internal fun buildAllStockHistoryMovements(
    products: List<Product>,
    metaMap: Map<String, InventoryItemMeta>,
    purchases: List<PurchaseEntry>,
    purchaseInvoices: List<PurchaseInvoice>,
    sales: List<SaleEntry>,
    customers: List<Customer>,
    allAdjustments: List<StockAdjustmentRecord>
): List<InventoryMovementItem> {
    val productMap = products.associateBy { it.id }
    val customerMap = customers.associateBy { it.id }
    val items = mutableListOf<InventoryMovementItem>()

    // 1. Legacy purchases
    purchases
        .filter { !it.isDeleted }
        .forEach { p ->
            val prod = productMap[p.productId] ?: return@forEach
            val meta = metaMap[prod.id] ?: InventoryItemMeta()
            val supplier = p.wholesalerName.ifBlank { meta.supplierName }
            items.add(
                InventoryMovementItem(
                    id = "pur_${p.id}",
                    productId = prod.id,
                    productTitle = formatProductCardTitle(prod),
                    type = MovementType.STOCK_IN,
                    title = formatProductCardTitle(prod),
                    subtitle = "Stock in · $supplier",
                    dateLabel = formatRelativeDateLabel(p.date),
                    quantityDeltaLabel = formatQtyDeltaForProduct(prod, p.quantityKg, "+"),
                    timestamp = p.date
                )
            )
        }

    // 2. Purchase invoices
    purchaseInvoices
        .filter { !it.isDeleted }
        .forEach { inv ->
            val supplier = inv.wholesalerName.ifBlank { "Supplier not set" }
            inv.getItems().forEachIndexed { idx, line ->
                val prod = productMap[line.productId]
                    ?: products.find { it.feedName.equals(line.feedName, ignoreCase = true) }
                val prodTitle = prod?.let { formatProductCardTitle(it) } ?: line.feedName
                val qtyLabel = if (prod != null) {
                    formatQtyDeltaForProduct(prod, line.totalKg, "+")
                } else if (line.bags > 0) {
                    "+${line.bags} ${if (line.bags == 1) "bag" else "bags"}"
                } else {
                    "+${CurrencyUtils.formatWeight(line.totalKg)} kg"
                }
                items.add(
                    InventoryMovementItem(
                        id = "inv_${inv.id}_$idx",
                        productId = prod?.id ?: line.productId,
                        productTitle = prodTitle,
                        type = MovementType.STOCK_IN,
                        title = prodTitle,
                        subtitle = "Stock in · $supplier",
                        dateLabel = formatRelativeDateLabel(inv.date),
                        quantityDeltaLabel = qtyLabel,
                        timestamp = inv.date
                    )
                )
            }
        }

    // 3. Sales & Returns
    sales
        .filter { !it.isDeleted && it.billStatus != "CANCELLED" }
        .forEachIndexed { idx, s ->
            val prod = productMap[s.productId] ?: return@forEachIndexed
            val prodTitle = formatProductCardTitle(prod)
            val custName = customerMap[s.customerId]?.name ?: "Walk-in"
            val shortBillNo = (1000 + (s.date % 900)).toString()
            items.add(
                InventoryMovementItem(
                    id = "sale_${s.id}",
                    productId = prod.id,
                    productTitle = prodTitle,
                    type = MovementType.SALE_OUT,
                    title = prodTitle,
                    subtitle = "Bill #$shortBillNo · $custName",
                    dateLabel = formatRelativeDateLabel(s.date),
                    quantityDeltaLabel = formatQtyDeltaForProduct(prod, s.quantityKg, "−"),
                    timestamp = s.date
                )
            )
            if (s.returnedKg > BigDecimal.ZERO) {
                val cnNum = (idx + 14).toString().padStart(3, '0')
                val retTime = s.updatedAt.takeIf { it > 0L } ?: (s.date + 3600_000L)
                items.add(
                    InventoryMovementItem(
                        id = "ret_${s.id}",
                        productId = prod.id,
                        productTitle = prodTitle,
                        type = MovementType.RETURN,
                        title = prodTitle,
                        subtitle = "Credit note #CN-$cnNum · $custName",
                        dateLabel = formatRelativeDateLabel(retTime),
                        quantityDeltaLabel = formatQtyDeltaForProduct(prod, s.returnedKg, "+"),
                        timestamp = retTime
                    )
                )
            }
        }

    // 4. Adjustments
    allAdjustments.forEach { adj ->
        val prod = productMap[adj.productId] ?: return@forEach
        val prodTitle = formatProductCardTitle(prod)
        val sign = if (adj.deltaKg >= BigDecimal.ZERO) "+" else "−"
        items.add(
            InventoryMovementItem(
                id = "adj_${adj.id}",
                productId = prod.id,
                productTitle = prodTitle,
                type = MovementType.ADJUST,
                title = prodTitle,
                subtitle = "${adj.reason} · ${adj.actorName}",
                dateLabel = formatRelativeDateLabel(adj.timestamp),
                quantityDeltaLabel = formatQtyDeltaForProduct(prod, adj.deltaKg, sign),
                badgeLabel = "Adjust",
                timestamp = adj.timestamp
            )
        )
    }

    return items.sortedByDescending { it.timestamp }
}
