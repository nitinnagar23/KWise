package com.example.ui.rates

import com.example.ui.theme.kw
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.CattleFeedRepository
import com.example.model.Permission
import com.example.model.Product
import com.example.model.User
import com.example.model.can
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatDarkInput
import com.example.ui.theme.FlatTextMuted
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import com.example.util.CurrencyUtils
import com.example.util.tr
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val RatesBrandGreen: Color get() = kw(0xFF0E6F43)
private val RatesMintBg: Color get() = kw(0xFFE1F5EB)
private val RatesLowStockBg: Color get() = kw(0xFFFDEFD9)
private val RatesLowStockText: Color get() = kw(0xFFB45309)
private val RatesCrimson: Color get() = kw(0xFFB91C1C)
private val RatesCrimsonSoftBg: Color get() = kw(0xFFFDE8E8)
private val RatesEditBtnBg: Color get() = kw(0xFFF1EFF7)
private val RatesInfoBoxBg: Color get() = kw(0xFFF1F1F8)
private val RatesInfoBoxBorder: Color get() = kw(0xFFE2E2F0)
enum class CommodityFilter {
    ALL,
    LOOSE_ONLY,
    BAGGED,
    IN_STOCK_ONLY
}

data class RateCardDisplayConfig(
    val show1Kg: Boolean = true,
    val show5Kg: Boolean = true,
    val showBag: Boolean = true,
    val custom5KgPrice: BigDecimal? = null
)

private object RateDisplayPrefs {
    private const val PREFS_NAME = "daily_rate_display_prefs"

    fun load(context: Context, product: Product): RateCardDisplayConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasSaved = prefs.contains("${product.id}_show1Kg")
        val defaultBag = !product.isLooseOnly && product.kgPerBag > BigDecimal.ONE
        val default5Kg = !product.feedName.contains("Wheat Bran", ignoreCase = true) &&
            !product.feedName.contains("Choker", ignoreCase = true)

        if (!hasSaved) {
            return RateCardDisplayConfig(
                show1Kg = true,
                show5Kg = default5Kg,
                showBag = defaultBag,
                custom5KgPrice = null
            )
        }
        val show1Kg = prefs.getBoolean("${product.id}_show1Kg", true)
        val show5Kg = prefs.getBoolean("${product.id}_show5Kg", default5Kg)
        val showBag = prefs.getBoolean("${product.id}_showBag", defaultBag)
        val custom5KgStr = prefs.getString("${product.id}_custom5Kg", null)
        val custom5Kg = custom5KgStr?.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }

        // Ensure at least one rate is enabled
        val anyEnabled = show1Kg || show5Kg || showBag
        return RateCardDisplayConfig(
            show1Kg = if (anyEnabled) show1Kg else true,
            show5Kg = show5Kg,
            showBag = showBag,
            custom5KgPrice = custom5Kg
        )
    }

    fun save(context: Context, productId: String, config: RateCardDisplayConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("${productId}_show1Kg", config.show1Kg)
            .putBoolean("${productId}_show5Kg", config.show5Kg)
            .putBoolean("${productId}_showBag", config.showBag)
            .putString("${productId}_custom5Kg", config.custom5KgPrice?.toPlainString() ?: "")
            .apply()
    }
}

@Composable
fun DailyRatesScreen(
    repository: CattleFeedRepository,
    currentUser: User?,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val allProducts by repository.allProducts.collectAsState(initial = emptyList())
    val products = remember(allProducts) { allProducts.filter { !it.isDeleted } }

    val canEditRates = can(currentUser?.role, Permission.EDIT_DAILY_RATES)

    var selectedFilter by remember { mutableStateOf(CommodityFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var prefsVersion by remember { mutableIntStateOf(0) }

    // Editing / Adding rate state (opens full-screen Edit rate page from Edit_rate.pdf)
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var isAddingNewRate by remember { mutableStateOf(false) }

    val todayDateStr = remember {
        SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())
    }

    val filteredProducts = remember(products, selectedFilter, searchQuery, prefsVersion) {
        products.filter { prod ->
            val cfg = RateDisplayPrefs.load(context, prod)
            val matchesFilter = when (selectedFilter) {
                CommodityFilter.ALL -> true
                CommodityFilter.LOOSE_ONLY -> !cfg.showBag || prod.isLooseOnly || prod.kgPerBag <= BigDecimal.ONE
                CommodityFilter.BAGGED -> cfg.showBag && !prod.isLooseOnly && prod.kgPerBag > BigDecimal.ONE
                CommodityFilter.IN_STOCK_ONLY -> prod.currentStockKg > BigDecimal.ZERO
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                prod.feedName.contains(searchQuery.trim(), ignoreCase = true)
            }
            matchesFilter && matchesSearch
        }
    }

    val savedRateMsg = tr("Rate saved", "भाव सेव हो गया")
    val deletedItemMsg = tr("Item deleted", "आइटम हटा दिया गया")

    if (editingProduct != null || isAddingNewRate) {
        val targetProduct = editingProduct
        BackHandler {
            editingProduct = null
            isAddingNewRate = false
        }
        EditRateFullScreen(
            product = targetProduct,
            initialConfig = if (targetProduct != null) {
                RateDisplayPrefs.load(context, targetProduct)
            } else {
                RateCardDisplayConfig(show1Kg = true, show5Kg = true, showBag = true)
            },
            onBack = {
                editingProduct = null
                isAddingNewRate = false
            },
            onSave = { updatedProduct, displayConfig ->
                scope.launch {
                    try {
                        // Save display config
                        RateDisplayPrefs.save(context, updatedProduct.id, displayConfig)
                        prefsVersion++

                        // Persist product changes and rates
                        if (can(currentUser?.role, Permission.EDIT_INVENTORY)) {
                            repository.saveProduct(
                                updatedProduct,
                                actor = currentUser,
                                performedByRole = currentUser?.role
                            )
                        } else {
                            // Manager/Admin with EDIT_DAILY_RATES permission
                            val existing = products.find { it.id == updatedProduct.id }
                            if (existing != null) {
                                repository.updateDailyRate(
                                    productId = updatedProduct.id,
                                    bagPrice = if (displayConfig.showBag) updatedProduct.bagSellingPrice else null,
                                    looseRatePerKg = updatedProduct.effectiveLooseRatePerKg,
                                    actor = currentUser,
                                    performedByRole = currentUser?.role
                                )
                            }
                        }
                        editingProduct = null
                        isAddingNewRate = false
                        snackbarHostState.showSnackbar(savedRateMsg)
                    } catch (_: Throwable) {
                        editingProduct = null
                        isAddingNewRate = false
                    }
                }
            },
            onDelete = if (targetProduct != null && can(currentUser?.role, Permission.EDIT_INVENTORY)) {
                {
                    scope.launch {
                        try {
                            repository.deleteProduct(
                                productId = targetProduct.id,
                                actor = currentUser,
                                performedByRole = currentUser?.role
                            )
                            editingProduct = null
                            isAddingNewRate = false
                            snackbarHostState.showSnackbar(deletedItemMsg)
                        } catch (_: Throwable) {
                            editingProduct = null
                            isAddingNewRate = false
                        }
                    }
                }
            } else null
        )
        return
    }

    fun sharePriceList() {
        val sb = StringBuilder()
        sb.appendLine("🏪 *TODAY'S RATES (आज के भाव)* 🏪")
        sb.appendLine("📅 *Date:* $todayDateStr")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━")
        products.forEachIndexed { index, product ->
            val cfg = RateDisplayPrefs.load(context, product)
            val looseRate = product.effectiveLooseRatePerKg.setScale(2, RoundingMode.HALF_UP)
            val per5Kg = cfg.custom5KgPrice ?: looseRate.multiply(BigDecimal(5)).setScale(2, RoundingMode.HALF_UP)
            val bagRate = product.bagSellingPrice

            sb.appendLine("${index + 1}. *${product.feedName}*")
            if (cfg.show1Kg) {
                sb.appendLine("   • *1 kg:* ${CurrencyUtils.formatInr(looseRate)}")
            }
            if (cfg.show5Kg) {
                sb.appendLine("   • *5 kg:* ${CurrencyUtils.formatInr(per5Kg)}")
            }
            if (cfg.showBag && !product.isLooseOnly && product.kgPerBag > BigDecimal.ONE) {
                sb.appendLine("   • *Bag (${product.kgPerBag.stripTrailingZeros().toPlainString()} kg):* ${CurrencyUtils.formatInr(bagRate)}")
            }
            sb.appendLine()
        }
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━")

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Today's Rates")
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share Rate List via"))
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = FlatDarkBg,
        floatingActionButton = {
            if (canEditRates) {
                Surface(
                    onClick = {
                        editingProduct = null
                        isAddingNewRate = true
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = RatesBrandGreen,
                    contentColor = Color.White,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("add_commodity_fab")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add rate",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = tr("Add rate", "नया भाव"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(FlatDarkBg)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Top Header: Back Button + "Today's rates" / "Live price list · N items" + Share Button
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
                    if (onBack != null) {
                        Surface(
                            onClick = onBack,
                            shape = RoundedCornerShape(13.dp),
                            color = FlatDarkCard,
                            border = BorderStroke(1.dp, FlatDarkBorder),
                            modifier = Modifier.size(44.dp)
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
                    }

                    Column {
                        Text(
                            text = tr("Today's rates", "आज के भाव"),
                            style = TextStyle(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp,
                                color = FlatTextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${tr("Live price list", "लाइव रेट लिस्ट")} · ${products.size} ${tr("items", "आइटम")}",
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = FlatTextSecondary,
                                fontFeatureSettings = "tnum"
                            )
                        )
                    }
                }

                Surface(
                    onClick = { sharePriceList() },
                    shape = RoundedCornerShape(13.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("share_rates_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = tr("Share rate list", "भाव सूची शेयर करें"),
                            tint = FlatTextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = tr("Search commodity...", "अनाज या खल खोजें..."),
                        color = FlatTextMuted,
                        fontSize = 14.sp
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
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = FlatTextSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = FlatDarkCard,
                    unfocusedContainerColor = FlatDarkCard,
                    focusedBorderColor = RatesBrandGreen,
                    unfocusedBorderColor = FlatDarkBorder,
                    focusedTextColor = FlatTextPrimary,
                    unfocusedTextColor = FlatTextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_commodity_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Category Filter Pills: All | Loose grain | Bagged | In stock
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val filters = listOf(
                    CommodityFilter.ALL to tr("All", "सभी"),
                    CommodityFilter.LOOSE_ONLY to tr("Loose grain", "खुला अनाज"),
                    CommodityFilter.BAGGED to tr("Bagged", "बोरी बंद"),
                    CommodityFilter.IN_STOCK_ONLY to tr("In stock", "स्टॉक में")
                )
                filters.forEach { (filter, label) ->
                    val isSelected = selectedFilter == filter
                    val pillBg by animateColorAsState(
                        targetValue = if (isSelected) RatesBrandGreen else FlatDarkInput,
                        animationSpec = tween(durationMillis = 150),
                        label = "rateFilterBg_${filter.name}"
                    )
                    val pillFg by animateColorAsState(
                        targetValue = if (isSelected) Color.White else FlatTextPrimary,
                        animationSpec = tween(durationMillis = 150),
                        label = "rateFilterFg_${filter.name}"
                    )
                    Surface(
                        onClick = { selectedFilter = filter },
                        shape = RoundedCornerShape(50),
                        color = pillBg,
                        modifier = Modifier.defaultMinSize(minHeight = 38.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = pillFg
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Rate Cards List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredProducts, key = { it.id }) { product ->
                    val displayConfig = remember(product, prefsVersion) {
                        RateDisplayPrefs.load(context, product)
                    }
                    CommodityRateCard(
                        product = product,
                        displayConfig = displayConfig,
                        canEditRates = canEditRates,
                        onEditClick = { editingProduct = product }
                    )
                }
            }
        }
    }
}

@Composable
private fun CommodityRateCard(
    product: Product,
    displayConfig: RateCardDisplayConfig,
    canEditRates: Boolean,
    onEditClick: () -> Unit
) {
    val stockKg = product.currentStockKg
    val isBagProduct = !product.isLooseOnly && product.kgPerBag > BigDecimal.ONE
    val isOutOfStock = stockKg <= BigDecimal.ZERO
    val isLowStock = !isOutOfStock && (
        (isBagProduct && stockKg <= product.kgPerBag.multiply(BigDecimal("3"))) ||
            (!isBagProduct && stockKg <= BigDecimal("50"))
        )

    val (stockLabel, stockBg, stockTextColor) = when {
        isOutOfStock -> Triple(tr("Out of stock", "स्टॉक ख़त्म"), RatesCrimsonSoftBg, RatesCrimson)
        isLowStock -> Triple(tr("Low stock", "कम स्टॉक"), RatesLowStockBg, RatesLowStockText)
        else -> Triple(tr("In stock", "स्टॉक में"), RatesMintBg, RatesBrandGreen)
    }

    val per1Kg = product.effectiveLooseRatePerKg.setScale(2, RoundingMode.HALF_UP)
    val per5Kg = displayConfig.custom5KgPrice
        ?: per1Kg.multiply(BigDecimal(5)).setScale(2, RoundingMode.HALF_UP)
    val bagPrice = product.bagSellingPrice

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = FlatDarkCard,
        border = BorderStroke(1.dp, FlatDarkBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("rate_card_${product.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card Header: Product Name + Stock Status Pill + Edit Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = product.feedName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = stockBg
                    ) {
                        Text(
                            text = stockLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = stockTextColor,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                        )
                    }

                    if (canEditRates) {
                        Surface(
                            onClick = onEditClick,
                            shape = RoundedCornerShape(10.dp),
                            color = RatesEditBtnBg,
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("edit_rate_btn_${product.id}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = tr("Edit rate", "भाव बदलें"),
                                    tint = FlatTextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Rate Tiles Row (1 kg | 5 kg | Bag · X kg) — Concentric radius: 18dp outer - 8dp visual inset = 10dp inner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (displayConfig.show1Kg) {
                    RateUnitTile(
                        label = "1 kg",
                        priceText = CurrencyUtils.formatInr(per1Kg),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (displayConfig.show5Kg) {
                    RateUnitTile(
                        label = "5 kg",
                        priceText = CurrencyUtils.formatInr(per5Kg),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (displayConfig.showBag && product.kgPerBag > BigDecimal.ONE) {
                    RateUnitTile(
                        label = "Bag · ${product.kgPerBag.stripTrailingZeros().toPlainString()} kg",
                        priceText = CurrencyUtils.formatInr(bagPrice),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun RateUnitTile(
    label: String,
    priceText: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(FlatDarkBg)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 12.sp,
                color = FlatTextSecondary,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum"
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = priceText,
            style = TextStyle(
                fontSize = 17.sp,
                color = FlatTextPrimary,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum"
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EditRateFullScreen(
    product: Product?,
    initialConfig: RateCardDisplayConfig,
    onBack: () -> Unit,
    onSave: (Product, RateCardDisplayConfig) -> Unit,
    onDelete: (() -> Unit)?
) {
    val isNew = product == null
    val targetId = product?.id ?: UUID.randomUUID().toString()

    var itemName by remember(product) { mutableStateOf(product?.feedName ?: "") }

    val initial1Kg = remember(product) {
        product?.effectiveLooseRatePerKg?.stripTrailingZeros()?.toPlainString() ?: "27"
    }
    val initial5Kg = remember(product, initialConfig) {
        val five = initialConfig.custom5KgPrice
            ?: product?.effectiveLooseRatePerKg?.multiply(BigDecimal(5))
            ?: BigDecimal("135")
        five.stripTrailingZeros().toPlainString()
    }
    val initialBagKg = remember(product) {
        val kg = product?.kgPerBag ?: BigDecimal("50")
        if (kg <= BigDecimal.ONE) "50" else kg.stripTrailingZeros().toPlainString()
    }
    val initialBagPrice = remember(product) {
        if (product != null && !product.isLooseOnly && product.kgPerBag > BigDecimal.ONE) {
            product.bagSellingPrice.stripTrailingZeros().toPlainString()
        } else {
            val l = product?.effectiveLooseRatePerKg ?: BigDecimal("27")
            l.multiply(BigDecimal("50")).stripTrailingZeros().toPlainString()
        }
    }

    var show1Kg by remember(initialConfig) { mutableStateOf(initialConfig.show1Kg) }
    var show5Kg by remember(initialConfig) { mutableStateOf(initialConfig.show5Kg) }
    var showBag by remember(initialConfig) { mutableStateOf(initialConfig.showBag) }

    var price1KgInput by remember(product) { mutableStateOf(initial1Kg) }
    var price5KgInput by remember(product) { mutableStateOf(initial5Kg) }
    var bagWeightInput by remember(product) { mutableStateOf(initialBagKg) }
    var bagPriceInput by remember(product) { mutableStateOf(initialBagPrice) }

    var isInStock by remember(product) {
        mutableStateOf(product == null || product.currentStockKg > BigDecimal.ZERO)
    }

    val enabledCount = (if (show1Kg) 1 else 0) + (if (show5Kg) 1 else 0) + (if (showBag) 1 else 0)

    Scaffold(
        containerColor = FlatDarkBg,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FlatDarkBg)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val cleanName = itemName.trim().ifBlank { product?.feedName ?: "Cattle Feed" }
                        val parsed1Kg = price1KgInput.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
                            ?: product?.effectiveLooseRatePerKg
                            ?: BigDecimal("27")
                        val parsed5Kg = price5KgInput.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
                            ?: parsed1Kg.multiply(BigDecimal(5))
                        val parsedBagKg = bagWeightInput.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ONE }
                            ?: BigDecimal("50")
                        val parsedBagPrice = bagPriceInput.replace(",", "").trim().toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
                            ?: parsed1Kg.multiply(parsedBagKg)

                        val sellingPricePerKg = if (showBag && parsedBagKg > BigDecimal.ONE) {
                            parsedBagPrice.divide(parsedBagKg, 4, RoundingMode.HALF_UP)
                        } else {
                            parsed1Kg
                        }

                        val updatedStockKg = if (isInStock) {
                            val existingStock = product?.currentStockKg ?: BigDecimal.ZERO
                            if (existingStock > BigDecimal.ZERO) existingStock else parsedBagKg.multiply(BigDecimal("10"))
                        } else {
                            BigDecimal.ZERO
                        }

                        val savedProduct = (product ?: Product(id = targetId, feedName = cleanName)).copy(
                            feedName = cleanName,
                            kgPerBag = if (showBag) parsedBagKg else BigDecimal.ONE,
                            sellingPricePerKg = sellingPricePerKg,
                            looseSellingPricePerKg = parsed1Kg,
                            isLooseOnly = !showBag,
                            currentStockKg = updatedStockKg,
                            updatedAt = System.currentTimeMillis()
                        )

                        val default5Kg = parsed1Kg.multiply(BigDecimal(5)).setScale(2, RoundingMode.HALF_UP)
                        val custom5Kg = if (parsed5Kg.setScale(2, RoundingMode.HALF_UP).compareTo(default5Kg) != 0) {
                            parsed5Kg.setScale(2, RoundingMode.HALF_UP)
                        } else null

                        onSave(
                            savedProduct,
                            RateCardDisplayConfig(
                                show1Kg = show1Kg,
                                show5Kg = show5Kg,
                                showBag = showBag,
                                custom5KgPrice = custom5Kg
                            )
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RatesBrandGreen,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("save_rate_btn_$targetId")
                ) {
                    Text(
                        text = tr("Save rate", "भाव सेव करें"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_rate_item_btn")
                    ) {
                        Text(
                            text = tr("Delete item", "आइटम हटाएं"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = RatesCrimson
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(FlatDarkBg)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // Top Header: Back Button + "Edit rate" / Item Name
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    onClick = onBack,
                    shape = RoundedCornerShape(12.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier.size(42.dp)
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

                Column {
                    Text(
                        text = if (isNew) tr("Add rate", "नया भाव जोड़ें") else tr("Edit rate", "भाव बदलें"),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary
                    )
                    Text(
                        text = itemName.ifBlank { tr("New commodity", "नया सामान") },
                        fontSize = 13.sp,
                        color = FlatTextSecondary
                    )
                }
            }

            // ITEM NAME Section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = tr("ITEM NAME", "सामान का नाम"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextSecondary,
                    letterSpacing = 0.6.sp
                )
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = FlatDarkCard,
                        unfocusedContainerColor = FlatDarkCard,
                        focusedBorderColor = RatesBrandGreen,
                        unfocusedBorderColor = FlatDarkBorder,
                        focusedTextColor = FlatTextPrimary,
                        unfocusedTextColor = FlatTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_rate_item_name_input")
                )
            }

            // RATES SHOWN TO CUSTOMERS Section
            Text(
                text = tr("RATES SHOWN TO CUSTOMERS", "ग्राहकों को दिखाए जाने वाले भाव"),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = FlatTextSecondary,
                letterSpacing = 0.6.sp
            )

            // 1. "1 kg" Rate Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder),
                modifier = Modifier.fillMaxWidth()
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
                        Column {
                            Text(
                                text = "1 kg",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = tr("Price for 1 kg", "1 किलो का भाव"),
                                fontSize = 13.sp,
                                color = FlatTextSecondary
                            )
                        }

                        Switch(
                            checked = show1Kg,
                            onCheckedChange = { next ->
                                if (next || enabledCount > 1) {
                                    show1Kg = next
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = RatesBrandGreen,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = FlatDarkBorder
                            )
                        )
                    }

                    if (show1Kg) {
                        CompactRateInputBox(
                            prefix = "₹",
                            value = price1KgInput,
                            onValueChange = { next ->
                                if (next.isEmpty() || next.matches(Regex("^\\d*\\.?\\d*$"))) {
                                    price1KgInput = next
                                    val dec = next.toBigDecimalOrNull()
                                    if (dec != null) {
                                        price5KgInput = dec.multiply(BigDecimal(5)).stripTrailingZeros().toPlainString()
                                    }
                                }
                            },
                            modifier = Modifier
                                .width(115.dp)
                                .testTag("loose_rate_input_$targetId")
                        )
                    }
                }
            }

            // 2. "5 kg" Rate Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder),
                modifier = Modifier.fillMaxWidth()
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
                        Column {
                            Text(
                                text = "5 kg",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = tr("Price for 5 kg", "5 किलो का भाव"),
                                fontSize = 13.sp,
                                color = FlatTextSecondary
                            )
                        }

                        Switch(
                            checked = show5Kg,
                            onCheckedChange = { next ->
                                if (next || enabledCount > 1) {
                                    show5Kg = next
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = RatesBrandGreen,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = FlatDarkBorder
                            )
                        )
                    }

                    if (show5Kg) {
                        CompactRateInputBox(
                            prefix = "₹",
                            value = price5KgInput,
                            onValueChange = { next ->
                                if (next.isEmpty() || next.matches(Regex("^\\d*\\.?\\d*$"))) {
                                    price5KgInput = next
                                }
                            },
                            modifier = Modifier
                                .width(120.dp)
                                .testTag("five_kg_rate_input_$targetId")
                        )
                    }
                }
            }

            // 3. "Per bag" Rate Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder),
                modifier = Modifier.fillMaxWidth()
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("Per bag", "प्रति बोरी"),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = tr("Turn off if not sold in fixed bags", "यदि बोरी में नहीं बिकता तो बंद करें"),
                                fontSize = 13.sp,
                                color = FlatTextSecondary
                            )
                        }

                        Switch(
                            checked = showBag,
                            onCheckedChange = { next ->
                                if (next || enabledCount > 1) {
                                    showBag = next
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = RatesBrandGreen,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = FlatDarkBorder
                            )
                        )
                    }

                    if (showBag) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CompactRateInputBox(
                                suffix = "kg",
                                value = bagWeightInput,
                                onValueChange = { next ->
                                    if (next.isEmpty() || next.matches(Regex("^\\d*\\.?\\d*$"))) {
                                        bagWeightInput = next
                                    }
                                },
                                modifier = Modifier
                                    .width(115.dp)
                                    .testTag("bag_weight_input_$targetId")
                            )

                            CompactRateInputBox(
                                prefix = "₹",
                                value = bagPriceInput,
                                onValueChange = { next ->
                                    val cleaned = next.replace(",", "")
                                    if (cleaned.isEmpty() || cleaned.matches(Regex("^\\d*\\.?\\d*$"))) {
                                        bagPriceInput = cleaned
                                    }
                                },
                                modifier = Modifier
                                    .width(135.dp)
                                    .testTag("bag_rate_input_$targetId")
                            )
                        }
                    }
                }
            }

            // Info Box: "At least one rate must stay on. The last one can’t be turned off."
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = RatesInfoBoxBg,
                border = BorderStroke(1.dp, RatesInfoBoxBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = FlatTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = tr(
                            "At least one rate must stay on. The last one can’t be turned off.",
                            "कम से कम एक भाव चालू रहना चाहिए। आखिरी भाव बंद नहीं किया जा सकता।"
                        ),
                        fontSize = 13.sp,
                        color = FlatTextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            // 4. "In stock" Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr("In stock", "स्टॉक में उपलब्ध"),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                        Text(
                            text = tr("Customers see a green tag", "ग्राहकों को हरा टैग दिखेगा"),
                            fontSize = 13.sp,
                            color = FlatTextSecondary
                        )
                    }

                    Switch(
                        checked = isInStock,
                        onCheckedChange = { isInStock = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = RatesBrandGreen,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = FlatDarkBorder
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CompactRateInputBox(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    suffix: String? = null
) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(FlatDarkCard)
            .border(1.dp, FlatDarkBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (prefix != null) {
                Text(
                    text = prefix,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextSecondary
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary
                ),
                cursorBrush = SolidColor(RatesBrandGreen),
                modifier = Modifier.weight(1f, fill = false)
            )
            if (suffix != null) {
                Text(
                    text = suffix,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary
                )
            }
        }
    }
}
