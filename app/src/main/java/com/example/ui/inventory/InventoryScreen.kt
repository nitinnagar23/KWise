package com.example.ui.inventory

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.CattleFeedRepository
import com.example.model.Permission
import com.example.model.Product
import com.example.model.SyncStatus
import com.example.model.User
import com.example.model.can
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatTextMuted
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import com.example.util.CurrencyUtils
import com.example.util.tr
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

private sealed class InventorySubScreen {
    data object ListScreen : InventorySubScreen()
    data class ItemDetail(val productId: String) : InventorySubScreen()
    data class AddOrEditItem(val productId: String? = null, val initialBarcode: String? = null) : InventorySubScreen()
    data class UpdateStock(
        val productId: String,
        val initialMode: UpdateStockMode,
        val initialReason: String = "Damaged"
    ) : InventorySubScreen()
    data object ReorderList : InventorySubScreen()
    data object BarcodeScanner : InventorySubScreen()
    data class StockHistory(val initialProductId: String? = null) : InventorySubScreen()
    data object Categories : InventorySubScreen()
}

@Composable
fun InventoryScreen(
    repository: CattleFeedRepository,
    currentUser: User?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val products by repository.allProducts.collectAsState(initial = emptyList())
    val purchases by repository.allPurchases.collectAsState(initial = emptyList())
    val purchaseInvoices by repository.allPurchaseInvoices.collectAsState(initial = emptyList())
    val sales by repository.allSales.collectAsState(initial = emptyList())
    val customers by repository.allCustomers.collectAsState(initial = emptyList())

    val canManageInventory = can(currentUser?.role, Permission.ACCESS_INVENTORY)

    var subScreen by remember { mutableStateOf<InventorySubScreen>(InventorySubScreen.ListScreen) }
    var metaRefreshTick by remember { mutableIntStateOf(0) }
    var productToDelete by remember { mutableStateOf<Product?>(null) }

    val metaMap = remember(products, purchases, purchaseInvoices, metaRefreshTick) {
        products.associate { product ->
            product.id to InventoryMetaPrefs.loadMeta(
                context = context,
                product = product,
                purchases = purchases,
                invoices = purchaseInvoices
            )
        }
    }

    val availableSuppliers = remember(purchases, purchaseInvoices, metaMap) {
        val fromPurchases = purchases.map { it.wholesalerName.trim() }
        val fromInvoices = purchaseInvoices.map { it.wholesalerName.trim() }
        val fromMeta = metaMap.values.map { it.supplierName.trim() }
        (fromMeta + fromPurchases + fromInvoices)
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    val ratesUpdatedMsg = tr("Selling rates updated", "बिक्री भाव अपडेट हो गए")
    val itemUpdatedSuffix = tr("updated", "अपडेट किया गया")
    val itemAddedSuffix = tr("added", "जोड़ा गया")
    val stockUpdatedMsg = tr("Stock updated", "स्टॉक अपडेट हो गया")
    val stockAdjustedMsg = tr("Stock adjusted", "स्टॉक समायोजित किया गया")

    when (val current = subScreen) {
        is InventorySubScreen.ListScreen -> {
            BackHandler { onBack() }
            InventoryListView(
                products = products,
                metaMap = metaMap,
                canManageInventory = canManageInventory,
                onBack = onBack,
                onSelectProduct = { prod ->
                    subScreen = InventorySubScreen.ItemDetail(prod.id)
                },
                onAddNewItem = {
                    subScreen = InventorySubScreen.AddOrEditItem(productId = null)
                },
                onOpenReorderList = {
                    subScreen = InventorySubScreen.ReorderList
                },
                onOpenBarcodeScanner = {
                    subScreen = InventorySubScreen.BarcodeScanner
                },
                onOpenStockHistory = {
                    subScreen = InventorySubScreen.StockHistory(initialProductId = null)
                },
                onOpenCategories = {
                    subScreen = InventorySubScreen.Categories
                },
                onOpenCountStock = {
                    val firstProd = products.firstOrNull()
                    if (firstProd != null) {
                        subScreen = InventorySubScreen.UpdateStock(
                            productId = firstProd.id,
                            initialMode = UpdateStockMode.ADJUST,
                            initialReason = "Count correction"
                        )
                    } else {
                        subScreen = InventorySubScreen.AddOrEditItem(productId = null)
                    }
                }
            )
        }

        is InventorySubScreen.ItemDetail -> {
            val product = products.find { it.id == current.productId }
            BackHandler {
                subScreen = InventorySubScreen.ListScreen
            }
            if (product == null) {
                subScreen = InventorySubScreen.ListScreen
            } else {
                val meta = metaMap[product.id] ?: InventoryItemMeta()
                val adjustments = remember(product.id, metaRefreshTick) {
                    InventoryMetaPrefs.loadAdjustmentsForProduct(context, product.id)
                }
                val movements = remember(product, meta, purchases, sales, customers, adjustments) {
                    buildProductMovements(
                        product = product,
                        meta = meta,
                        purchases = purchases,
                        sales = sales,
                        customers = customers,
                        adjustments = adjustments
                    )
                }

                InventoryItemDetailView(
                    product = product,
                    meta = meta,
                    movements = movements,
                    canManageInventory = canManageInventory,
                    onBack = { subScreen = InventorySubScreen.ListScreen },
                    onEditItem = {
                        subScreen = InventorySubScreen.AddOrEditItem(productId = product.id)
                    },
                    onDeleteItem = {
                        productToDelete = product
                    },
                    onOpenStockIn = {
                        subScreen = InventorySubScreen.UpdateStock(
                            productId = product.id,
                            initialMode = UpdateStockMode.STOCK_IN
                        )
                    },
                    onOpenAdjust = {
                        subScreen = InventorySubScreen.UpdateStock(
                            productId = product.id,
                            initialMode = UpdateStockMode.ADJUST,
                            initialReason = "Damaged"
                        )
                    },
                    onOpenStockHistory = {
                        subScreen = InventorySubScreen.StockHistory(initialProductId = product.id)
                    },
                    onSaveSellingRates = { new1KgRate, newBagPrice ->
                        scope.launch {
                            try {
                                repository.updateDailyRate(
                                    productId = product.id,
                                    bagPrice = newBagPrice,
                                    looseRatePerKg = new1KgRate,
                                    actor = currentUser
                                )
                            } catch (_: Throwable) {
                                val sellPerKg = if (newBagPrice != null && !product.isLooseOnly && product.kgPerBag > BigDecimal.ONE) {
                                    newBagPrice.divide(product.kgPerBag, 4, RoundingMode.HALF_UP)
                                } else {
                                    new1KgRate
                                }
                                repository.saveProduct(
                                    product.copy(
                                        sellingPricePerKg = sellPerKg,
                                        looseSellingPricePerKg = new1KgRate,
                                        updatedAt = System.currentTimeMillis(),
                                        syncStatus = SyncStatus.PENDING
                                    )
                                )
                            }
                            Toast.makeText(
                                context,
                                ratesUpdatedMsg,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
        }

        is InventorySubScreen.AddOrEditItem -> {
            val existingProduct = current.productId?.let { id -> products.find { it.id == id } }
            val existingMeta = existingProduct?.let { metaMap[it.id] }
            BackHandler {
                subScreen = if (existingProduct != null) {
                    InventorySubScreen.ItemDetail(existingProduct.id)
                } else {
                    InventorySubScreen.ListScreen
                }
            }

            InventoryAddOrEditItemView(
                existingProduct = existingProduct,
                existingMeta = existingMeta,
                availableSuppliers = availableSuppliers,
                onBack = {
                    subScreen = if (existingProduct != null) {
                        InventorySubScreen.ItemDetail(existingProduct.id)
                    } else {
                        InventorySubScreen.ListScreen
                    }
                },
                onSaveProduct = { name, hindiName, category, isLooseOnly, bagSizeKg, reorderBags, reorderLooseKg, openingBags, openingLooseKg, costPerBagOrKg, supplier, looseRate1Kg, bagSellingPrice ->
                    val effectiveBagKg = if (isLooseOnly) BigDecimal.ONE else bagSizeKg.max(BigDecimal.ONE)
                    val totalOpeningKg = if (isLooseOnly) {
                        openingLooseKg
                    } else {
                        effectiveBagKg.multiply(BigDecimal(openingBags)).add(openingLooseKg)
                    }
                    val costPerKg = if (!isLooseOnly && effectiveBagKg > BigDecimal.ONE) {
                        costPerBagOrKg.divide(effectiveBagKg, 4, RoundingMode.HALF_UP)
                    } else {
                        costPerBagOrKg
                    }
                    val sellingPricePerKg = if (!isLooseOnly && bagSellingPrice != null && effectiveBagKg > BigDecimal.ONE) {
                        bagSellingPrice.divide(effectiveBagKg, 4, RoundingMode.HALF_UP)
                    } else {
                        looseRate1Kg
                    }

                    val targetId = existingProduct?.id ?: UUID.randomUUID().toString()
                    val savedProduct = Product(
                        id = targetId,
                        feedName = name,
                        baseUnit = "kg",
                        kgPerBag = effectiveBagKg,
                        currentStockKg = totalOpeningKg,
                        averagePurchaseCostPerKg = costPerKg,
                        sellingPricePerKg = sellingPricePerKg,
                        looseSellingPricePerKg = looseRate1Kg,
                        isLooseOnly = isLooseOnly,
                        createdAt = existingProduct?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        syncStatus = SyncStatus.PENDING
                    )

                    InventoryMetaPrefs.saveMeta(
                        context = context,
                        productId = targetId,
                        meta = InventoryItemMeta(
                            hindiName = hindiName,
                            category = category,
                            reorderBags = reorderBags,
                            reorderLooseKg = reorderLooseKg,
                            supplierName = supplier
                        )
                    )
                    if (!current.initialBarcode.isNullOrBlank()) {
                        InventoryMetaPrefs.saveBarcodeForProduct(context, targetId, current.initialBarcode)
                    }
                    metaRefreshTick++

                    scope.launch {
                        repository.saveProduct(savedProduct)
                        Toast.makeText(
                            context,
                            if (existingProduct != null) {
                                "${savedProduct.feedName} $itemUpdatedSuffix"
                            } else {
                                "${savedProduct.feedName} $itemAddedSuffix"
                            },
                            Toast.LENGTH_SHORT
                        ).show()
                        subScreen = InventorySubScreen.ItemDetail(targetId)
                    }
                }
            )
        }

        is InventorySubScreen.UpdateStock -> {
            val product = products.find { it.id == current.productId }
            BackHandler {
                subScreen = if (product != null) {
                    InventorySubScreen.ItemDetail(product.id)
                } else {
                    InventorySubScreen.ListScreen
                }
            }
            if (product == null) {
                subScreen = InventorySubScreen.ListScreen
            } else {
                val meta = metaMap[product.id] ?: InventoryItemMeta()
                InventoryUpdateStockView(
                    product = product,
                    meta = meta,
                    initialMode = current.initialMode,
                    initialReason = current.initialReason,
                    availableSuppliers = availableSuppliers,
                    onBack = { subScreen = InventorySubScreen.ItemDetail(product.id) },
                    onStartFullStockCount = {
                        val idx = products.indexOfFirst { it.id == product.id }
                        val nextProd = if (idx >= 0 && idx + 1 < products.size) {
                            products[idx + 1]
                        } else {
                            products.firstOrNull()
                        }
                        if (nextProd != null) {
                            subScreen = InventorySubScreen.UpdateStock(
                                productId = nextProd.id,
                                initialMode = UpdateStockMode.ADJUST,
                                initialReason = "Count correction"
                            )
                        }
                    },
                    onConfirmStockIn = { bagsReceived, looseKgReceived, costPerBagOrKg, supplier, paymentMode ->
                        val isBagged = !product.isLooseOnly && product.kgPerBag > BigDecimal.ONE
                        val totalReceivedKg = if (isBagged) {
                            product.kgPerBag.multiply(BigDecimal(bagsReceived)).add(looseKgReceived)
                        } else {
                            looseKgReceived
                        }
                        val totalPurchaseCost = if (isBagged) {
                            val bagPart = costPerBagOrKg.multiply(BigDecimal(bagsReceived))
                            val loosePart = if (product.kgPerBag > BigDecimal.ZERO && looseKgReceived > BigDecimal.ZERO) {
                                costPerBagOrKg.divide(product.kgPerBag, 4, RoundingMode.HALF_UP).multiply(looseKgReceived)
                            } else {
                                BigDecimal.ZERO
                            }
                            bagPart.add(loosePart).setScale(2, RoundingMode.HALF_UP)
                        } else {
                            costPerBagOrKg.multiply(looseKgReceived).setScale(2, RoundingMode.HALF_UP)
                        }

                        InventoryMetaPrefs.saveMeta(
                            context = context,
                            productId = product.id,
                            meta = meta.copy(supplierName = supplier)
                        )
                        metaRefreshTick++

                        scope.launch {
                            try {
                                repository.recordPurchase(
                                    wholesalerName = supplier,
                                    productId = product.id,
                                    quantityKg = totalReceivedKg,
                                    totalPurchaseCost = totalPurchaseCost,
                                    paymentStatus = paymentMode.dbStatus,
                                    recordedByUserId = currentUser?.id ?: ""
                                )
                            } catch (_: Throwable) {
                                val newStock = product.currentStockKg.add(totalReceivedKg)
                                val newAvgCost = if (!isBagged) {
                                    costPerBagOrKg
                                } else {
                                    costPerBagOrKg.divide(product.kgPerBag, 4, RoundingMode.HALF_UP)
                                }
                                repository.saveProduct(
                                    product.copy(
                                        currentStockKg = newStock,
                                        averagePurchaseCostPerKg = newAvgCost,
                                        updatedAt = System.currentTimeMillis(),
                                        syncStatus = SyncStatus.PENDING
                                    )
                                )
                            }
                            Toast.makeText(
                                context,
                                stockUpdatedMsg,
                                Toast.LENGTH_SHORT
                            ).show()
                            subScreen = InventorySubScreen.ItemDetail(product.id)
                        }
                    },
                    onConfirmAdjust = { deltaKg, reason, note, photoUri, stockLossCost, addToExpenses ->
                        val newStock = product.currentStockKg.add(deltaKg).max(BigDecimal.ZERO)
                        val actorName = currentUser?.name?.takeIf { it.isNotBlank() } ?: "Admin"
                        InventoryMetaPrefs.recordAdjustment(
                            context = context,
                            record = StockAdjustmentRecord(
                                productId = product.id,
                                deltaKg = deltaKg,
                                reason = reason,
                                note = note,
                                photoUri = photoUri,
                                stockLossCost = stockLossCost,
                                addedToExpenses = addToExpenses,
                                actorName = actorName
                            )
                        )
                        if (addToExpenses && stockLossCost > BigDecimal.ZERO) {
                            val subtitleInfo = buildString {
                                append(reason)
                                if (note.isNotBlank()) append(" · $note")
                            }
                            com.example.ui.cashbook.CashbookManualPrefs.addManualEntry(
                                context = context,
                                entry = com.example.ui.cashbook.CashbookEntry(
                                    id = "stock_loss_${UUID.randomUUID()}",
                                    date = System.currentTimeMillis(),
                                    title = "Stock loss · ${formatProductCardTitle(product)}",
                                    subtitle = subtitleInfo,
                                    amount = stockLossCost,
                                    isCashIn = false,
                                    tag = "STOCK LOSS",
                                    isAuto = true,
                                    isExpense = true,
                                    receiptUri = photoUri
                                )
                            )
                        }
                        metaRefreshTick++

                        scope.launch {
                            repository.saveProduct(
                                product.copy(
                                    currentStockKg = newStock,
                                    updatedAt = System.currentTimeMillis(),
                                    syncStatus = SyncStatus.PENDING
                                )
                            )
                            Toast.makeText(
                                context,
                                stockAdjustedMsg,
                                Toast.LENGTH_SHORT
                            ).show()
                            subScreen = InventorySubScreen.ItemDetail(product.id)
                        }
                    }
                )
            }
        }

        is InventorySubScreen.ReorderList -> {
            val settings by repository.settingsFlow.collectAsState(initial = null)
            val shopTitle = settings?.shopName?.ifBlank { "Shop Name" } ?: "Shop Name"
            BackHandler { subScreen = InventorySubScreen.ListScreen }
            ReorderListScreen(
                products = products,
                metaMap = metaMap,
                shopName = shopTitle,
                onBack = { subScreen = InventorySubScreen.ListScreen },
                onMakePurchaseBills = { selectedPairs ->
                    val draftMsg = "${selectedPairs.size} draft purchase bill(s) created"
                    Toast.makeText(context, draftMsg, Toast.LENGTH_SHORT).show()
                    subScreen = InventorySubScreen.ListScreen
                }
            )
        }

        is InventorySubScreen.BarcodeScanner -> {
            BackHandler { subScreen = InventorySubScreen.ListScreen }
            BarcodeScannerScreen(
                products = products,
                metaMap = metaMap,
                onClose = { subScreen = InventorySubScreen.ListScreen },
                onAddToBill = { prod ->
                    Toast.makeText(context, "${formatProductCardTitle(prod)} added to bill", Toast.LENGTH_SHORT).show()
                    subScreen = InventorySubScreen.ItemDetail(prod.id)
                },
                onOpenStockIn = { prod ->
                    subScreen = InventorySubScreen.UpdateStock(
                        productId = prod.id,
                        initialMode = UpdateStockMode.STOCK_IN
                    )
                },
                onViewProduct = { prod ->
                    subScreen = InventorySubScreen.ItemDetail(prod.id)
                },
                onAddAsNewItem = { scannedCode ->
                    subScreen = InventorySubScreen.AddOrEditItem(
                        productId = null,
                        initialBarcode = scannedCode
                    )
                },
                onLinkBarcodeToProduct = { prod, scannedCode ->
                    InventoryMetaPrefs.saveBarcodeForProduct(context, prod.id, scannedCode)
                    metaRefreshTick++
                }
            )
        }

        is InventorySubScreen.StockHistory -> {
            BackHandler { subScreen = InventorySubScreen.ListScreen }
            val allAdjustments = remember(metaRefreshTick) {
                InventoryMetaPrefs.loadAllAdjustmentsPublic(context)
            }
            val allMovements = remember(products, metaMap, purchases, purchaseInvoices, sales, customers, allAdjustments) {
                buildAllStockHistoryMovements(
                    products = products,
                    metaMap = metaMap,
                    purchases = purchases,
                    purchaseInvoices = purchaseInvoices,
                    sales = sales,
                    customers = customers,
                    allAdjustments = allAdjustments
                )
            }
            StockHistoryScreen(
                allMovements = allMovements,
                products = products,
                initialProductId = current.initialProductId,
                onBack = { subScreen = InventorySubScreen.ListScreen }
            )
        }

        is InventorySubScreen.Categories -> {
            BackHandler { subScreen = InventorySubScreen.ListScreen }
            InventoryCategoriesScreen(
                products = products,
                metaMap = metaMap,
                onBack = { subScreen = InventorySubScreen.ListScreen },
                onCategoriesChanged = { metaRefreshTick++ }
            )
        }
    }

    // Delete Inventory Item Confirmation Dialog (Page 12 of KhataWise – Onboarding & states.pdf)
    productToDelete?.let { prod ->
        val itemTitle = formatProductCardTitle(prod)
        val stockQtyLabel = formatProductQuantityLabel(prod)
        val deletedToast = tr("Item deleted", "आइटम हटाया गया")
        val hiddenToast = tr("Item hidden from billing", "आइटम बिलिंग से छुपाया गया")

        com.example.ui.components.DeleteInventoryItemConfirmationDialog(
            itemName = itemTitle,
            stockSummaryText = stockQtyLabel,
            onHideItemInstead = {
                productToDelete = null
                Toast.makeText(context, hiddenToast, Toast.LENGTH_SHORT).show()
            },
            onConfirmDelete = {
                val target = prod
                productToDelete = null
                scope.launch {
                    repository.saveProduct(
                        target.copy(
                            isDeleted = true,
                            updatedAt = System.currentTimeMillis(),
                            syncStatus = SyncStatus.PENDING
                        )
                    )
                    subScreen = InventorySubScreen.ListScreen
                    Toast.makeText(context, deletedToast, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { productToDelete = null }
        )
    }
}

@Composable
private fun InventoryListView(
    products: List<Product>,
    metaMap: Map<String, InventoryItemMeta>,
    canManageInventory: Boolean,
    onBack: () -> Unit,
    onSelectProduct: (Product) -> Unit,
    onAddNewItem: () -> Unit,
    onOpenReorderList: () -> Unit,
    onOpenBarcodeScanner: () -> Unit,
    onOpenStockHistory: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenCountStock: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(InventoryFilter.ALL) }
    var reorderOnlyToggle by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    val statusByProductId = remember(products, metaMap) {
        products.associate { prod ->
            val meta = metaMap[prod.id] ?: InventoryItemMeta()
            prod.id to computeStockStatus(prod, meta)
        }
    }

    val inStockCount = remember(statusByProductId) {
        statusByProductId.values.count { it == InventoryStockStatus.IN_STOCK }
    }
    val lowStockCount = remember(statusByProductId) {
        statusByProductId.values.count { it == InventoryStockStatus.LOW }
    }
    val outOfStockCount = remember(statusByProductId) {
        statusByProductId.values.count { it == InventoryStockStatus.OUT_OF_STOCK }
    }
    val needReorderCount = lowStockCount + outOfStockCount

    // Total stock value (UNMASKED per user instruction: "Also don't mask anything")
    val totalStockValue = remember(products) {
        products.fold(BigDecimal.ZERO) { acc, prod ->
            val unitValue = if (prod.averagePurchaseCostPerKg > BigDecimal.ZERO) {
                prod.averagePurchaseCostPerKg
            } else {
                prod.sellingPricePerKg
            }
            val itemVal = prod.currentStockKg.max(BigDecimal.ZERO).multiply(unitValue)
            acc.add(itemVal)
        }.setScale(0, RoundingMode.HALF_UP)
    }

    val filteredProducts = remember(products, metaMap, statusByProductId, searchQuery, selectedFilter, reorderOnlyToggle) {
        products.filter { prod ->
            val meta = metaMap[prod.id] ?: InventoryItemMeta()
            val status = statusByProductId[prod.id] ?: InventoryStockStatus.IN_STOCK
            val matchesQuery = searchQuery.isBlank() ||
                prod.feedName.contains(searchQuery, ignoreCase = true) ||
                meta.hindiName.contains(searchQuery, ignoreCase = true) ||
                meta.category.contains(searchQuery, ignoreCase = true)

            val matchesReorderBanner = !reorderOnlyToggle ||
                status == InventoryStockStatus.LOW ||
                status == InventoryStockStatus.OUT_OF_STOCK

            val matchesFilter = when (selectedFilter) {
                InventoryFilter.ALL -> true
                InventoryFilter.LOW -> status == InventoryStockStatus.LOW
                InventoryFilter.OUT -> status == InventoryStockStatus.OUT_OF_STOCK
                InventoryFilter.BAGGED -> !prod.isLooseOnly && prod.kgPerBag > BigDecimal.ONE
                InventoryFilter.LOOSE -> prod.isLooseOnly || prod.kgPerBag <= BigDecimal.ONE
            }

            matchesQuery && matchesReorderBanner && matchesFilter
        }
    }

    Scaffold(
        containerColor = FlatDarkBg,
        floatingActionButton = {
            if (canManageInventory) {
                FloatingActionButton(
                    onClick = onAddNewItem,
                    shape = RoundedCornerShape(16.dp),
                    containerColor = InvBrandGreen,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                    modifier = Modifier.testTag("inventory_add_item_fab")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = tr("Item", "आइटम"),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold
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
            contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Header Row: Back button + Title/Subtitle (UNMASKED Stock value) + Barcode button
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
                                .testTag("inventory_back_btn")
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
                                text = tr("Inventory", "इन्वेंटरी"),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = "${products.size} ${tr("items", "आइटम")} · ${tr("Stock value", "स्टॉक मूल्य")} ${CurrencyUtils.formatInr(totalStockValue)}",
                                fontSize = 13.sp,
                                color = FlatTextSecondary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag("inventory_stock_value_subtitle")
                            )
                        }
                    }

                    Surface(
                        onClick = onOpenBarcodeScanner,
                        shape = RoundedCornerShape(12.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("inventory_scan_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = tr("Scan barcode", "बारकोड स्कैन करें"),
                                tint = FlatTextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            // 1b. Quick Actions Row: Reorder list | Count stock | Stock history | Categories | Scan barcode
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val quickActions = listOf(
                        Triple(tr("Reorder list", "री-ऑर्डर सूची"), "quick_reorder_list", onOpenReorderList),
                        Triple(tr("Count stock", "स्टॉक गिनती"), "quick_count_stock", onOpenCountStock),
                        Triple(tr("Stock history", "स्टॉक इतिहास"), "quick_stock_history", onOpenStockHistory),
                        Triple(tr("Categories", "श्रेणियां"), "quick_categories", onOpenCategories),
                        Triple(tr("Scan barcode", "बारकोड स्कैन"), "quick_scan_barcode", onOpenBarcodeScanner)
                    )
                    quickActions.forEach { (label, tag, action) ->
                        Surface(
                            onClick = action,
                            shape = RoundedCornerShape(12.dp),
                            color = FlatDarkCard,
                            border = BorderStroke(1.dp, FlatDarkBorder),
                            modifier = Modifier.testTag(tag)
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = InvBrandGreen,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // 2. Reorder Alert Banner
            if (needReorderCount > 0) {
                item {
                    Surface(
                        onClick = onOpenReorderList,
                        shape = RoundedCornerShape(16.dp),
                        color = InvAmberBannerBg,
                        border = BorderStroke(1.dp, InvAmberBannerBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("inventory_reorder_banner")
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(InvAmberProgress)
                                )
                                Text(
                                    text = "$needReorderCount ${tr("items need reorder", "आइटम री-ऑर्डर करें")}",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6B3E08)
                                )
                            }

                            Text(
                                text = tr("Reorder list →", "री-ऑर्डर सूची →"),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = InvBrandGreen
                            )
                        }
                    }
                }
            }

            // 3. 3 Summary Status Cards Row: In stock | Low | Out
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryStatusCard(
                        count = inStockCount,
                        label = tr("In stock", "स्टॉक में"),
                        bgColor = InvMintBg,
                        contentColor = InvBrandGreen,
                        onClick = {
                            reorderOnlyToggle = false
                            selectedFilter = InventoryFilter.ALL
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("summary_in_stock_card")
                    )
                    SummaryStatusCard(
                        count = lowStockCount,
                        label = tr("Low", "कम"),
                        bgColor = InvAmberSoftBg,
                        contentColor = InvAmberText,
                        onClick = {
                            reorderOnlyToggle = false
                            selectedFilter = InventoryFilter.LOW
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("summary_low_stock_card")
                    )
                    SummaryStatusCard(
                        count = outOfStockCount,
                        label = tr("Out", "ख़त्म"),
                        bgColor = InvRedSoftBg,
                        contentColor = InvRedText,
                        onClick = {
                            reorderOnlyToggle = false
                            selectedFilter = InventoryFilter.OUT
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("summary_out_stock_card")
                    )
                }
            }

            // 4. Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = tr("Search item or scan barcode...", "आइटम खोजें या बारकोड स्कैन करें..."),
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
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = tr("Clear", "साफ़ करें"),
                                    tint = FlatTextSecondary
                                )
                            }
                        }
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
                        .focusRequester(searchFocusRequester)
                        .testTag("inventory_search_input")
                )
            }

            // 5. Filter Chips Row: All | Low | Out | Bagged | Loose
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InventoryFilter.entries.forEach { filter ->
                        val isSelected = selectedFilter == filter && !reorderOnlyToggle
                        Surface(
                            onClick = {
                                reorderOnlyToggle = false
                                selectedFilter = filter
                            },
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) InvBrandGreen else InvChipUnselectedBg,
                            modifier = Modifier.testTag("inventory_filter_${filter.name.lowercase()}")
                        ) {
                            Text(
                                text = tr(filter.labelEn, filter.labelHi),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else FlatTextPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }

            // 6. Inventory Item Cards or Empty / No Search Results State
            if (filteredProducts.isEmpty()) {
                item {
                    if (searchQuery.isNotBlank()) {
                        com.example.ui.components.NoSearchResultsStateCard(
                            query = searchQuery,
                            onClearSearch = { searchQuery = "" }
                        )
                    } else {
                        com.example.ui.components.EmptyStockListStateCard(
                            onAddItem = onAddNewItem,
                            onImportExcel = onAddNewItem
                        )
                    }
                }
            } else {
                items(filteredProducts, key = { it.id }) { product ->
                    val meta = metaMap[product.id] ?: InventoryItemMeta()
                    val status = statusByProductId[product.id] ?: InventoryStockStatus.IN_STOCK
                    val progressFraction = computeStockProgressFraction(product, meta)

                    InventoryItemCard(
                        product = product,
                        meta = meta,
                        status = status,
                        progressFraction = progressFraction,
                        onClick = { onSelectProduct(product) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStatusCard(
    count: Int,
    label: String,
    bgColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = count.toString(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Text(
                text = label,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

@Composable
private fun InventoryItemCard(
    product: Product,
    meta: InventoryItemMeta,
    status: InventoryStockStatus,
    progressFraction: Float,
    onClick: () -> Unit
) {
    val barColor = when (status) {
        InventoryStockStatus.IN_STOCK -> InvBrandGreen
        InventoryStockStatus.LOW -> InvAmberProgress
        InventoryStockStatus.OUT_OF_STOCK -> Color.Transparent
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("inventory_item_card_${product.id}"),
        shape = RoundedCornerShape(18.dp),
        color = FlatDarkCard,
        border = BorderStroke(1.dp, FlatDarkBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = formatProductCardTitle(product),
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatProductCardSubtitle(product, meta),
                        fontSize = 13.sp,
                        color = FlatTextSecondary
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = formatProductQuantityLabel(product),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary
                    )
                    InventoryStatusBadge(status = status)
                }
            }

            // Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(InvTrackGray)
            ) {
                if (progressFraction > 0f && status != InventoryStockStatus.OUT_OF_STOCK) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(barColor)
                    )
                }
            }
        }
    }
}
