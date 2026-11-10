package com.example.ui.reports

import com.example.ui.theme.kw
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Customer
import com.example.model.PaymentEntry
import com.example.model.Product
import com.example.model.PurchaseInvoice
import com.example.model.SaleEntry
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatTextMuted
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import com.example.util.BackupExportUtil
import com.example.util.CurrencyUtils
import com.example.util.CustomerInsight
import com.example.util.DailyProfitSummary
import com.example.util.ProductProfitSummary
import com.example.util.VillageUdhariSummary
import com.example.util.tr
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val RepGreen: Color get() = kw(0xFF0E6F43)
internal val RepGreenSoft: Color get() = kw(0xFFDDF3E8)
internal val RepMintBg: Color get() = kw(0xFFF0FAF5)
internal val RepMintBorder: Color get() = kw(0xFFC6E9D6)
internal val RepRed: Color get() = kw(0xFFB91C1C)
internal val RepRedSoft: Color get() = kw(0xFFFCE4E4)
internal val RepAmber: Color get() = kw(0xFFB45309)
internal val RepAmberBar: Color get() = kw(0xFFD97706)
internal val RepAmberSoft: Color get() = kw(0xFFFCEBD2)
internal val RepAmberCalloutBg: Color get() = kw(0xFFFEF6E7)
internal val RepAmberCalloutBorder: Color get() = kw(0xFFF5D598)
internal val RepBlue: Color get() = kw(0xFF2563EB)
internal val RepBarBlue: Color get() = if (com.example.ui.theme.KwThemeState.isDark) Color(0xFF3B82F6) else Color(0xFFBFDBFE)
internal val RepAvatarBg: Color get() = kw(0xFFE8EEF7)
internal val RepAvatarText: Color get() = kw(0xFF1E3A8A)
internal val RepChipIdleBg: Color get() = kw(0xFFF1F1F7)
internal val RepTrackBg: Color get() = kw(0xFFEAECEF)
@Composable
internal fun ReportsProfitViewTab(
    totalGrossProfit: BigDecimal,
    profitMarginPercent: BigDecimal,
    netProfitAfterExpenses: BigDecimal,
    totalAllExpenses: BigDecimal,
    productProfits: List<ProductProfitSummary>,
    productsMap: Map<String, Product>,
    dailyProfits: List<DailyProfitSummary>
) {
    // 0 -> By profit, 1 -> By margin %, 2 -> By sales
    var sortMode by remember { mutableIntStateOf(0) }
    val dayTableFormat = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }

    val sortedProducts = remember(productProfits, sortMode) {
        when (sortMode) {
            1 -> productProfits.sortedByDescending { it.marginPercent }
            2 -> productProfits.sortedByDescending { it.totalRevenue }
            else -> productProfits.sortedByDescending { it.grossProfit }
        }
    }

    val lowMarginCandidate = remember(productProfits) {
        productProfits.firstOrNull { it.marginPercent < BigDecimal("20") }
            ?: productProfits.minByOrNull { it.marginPercent }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1: GROSS PROFIT & NET PROFIT cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = tr("GROSS PROFIT", "सकल मुनाफा"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = CurrencyUtils.formatInr(totalGrossProfit),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = RepGreen
                    )
                    Text(
                        text = "$profitMarginPercent% margin",
                        fontSize = 13.sp,
                        color = FlatTextSecondary
                    )
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = tr("NET PROFIT", "शुद्ध मुनाफा"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = CurrencyUtils.formatInr(netProfitAfterExpenses),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (netProfitAfterExpenses >= BigDecimal.ZERO) RepGreen else RepRed
                    )
                    Text(
                        text = "after ${CurrencyUtils.formatInr(totalAllExpenses)} expenses",
                        fontSize = 13.sp,
                        color = FlatTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Product-wise margin section header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = tr("Product-wise margin", "आइटम के अनुसार मुनाफा"),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary
                )
                Text(
                    text = tr("Sale price vs cost at time of sale", "बिक्री के समय लागत बनाम बिक्री मूल्य"),
                    fontSize = 12.5.sp,
                    color = FlatTextSecondary
                )
            }

            Surface(
                onClick = { sortMode = (sortMode + 1) % 3 },
                shape = RoundedCornerShape(50),
                color = RepChipIdleBg
            ) {
                Text(
                    text = when (sortMode) {
                        1 -> "By margin ▾"
                        2 -> "By sales ▾"
                        else -> "By profit ▾"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }

        if (sortedProducts.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Text(
                    text = tr("No sales recorded in this period.", "इस अवधि में कोई बिक्री नहीं हुई।"),
                    fontSize = 14.sp,
                    color = FlatTextMuted,
                    modifier = Modifier.padding(20.dp)
                )
            }
        } else {
            val maxProfit = remember(sortedProducts) {
                sortedProducts.maxOfOrNull { it.grossProfit.toFloat() }?.coerceAtLeast(1f) ?: 1f
            }

            sortedProducts.forEach { pp ->
                val prod = productsMap[pp.productId]
                val displayName = if (prod != null && !prod.isLooseOnly && prod.kgPerBag > BigDecimal.ZERO) {
                    "${prod.feedName} (${CurrencyUtils.formatWeight(prod.kgPerBag)} kg)"
                } else {
                    pp.feedName
                }
                val isLowMargin = pp.marginPercent < BigDecimal("20")
                val fillFraction = (pp.grossProfit.toFloat() / maxProfit).coerceIn(0.15f, 0.92f)

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
                                    text = displayName,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary
                                )
                                Text(
                                    text = "${CurrencyUtils.formatWeight(pp.totalSoldKg)} kg · ${CurrencyUtils.formatInr(pp.totalRevenue)} sales",
                                    fontSize = 13.sp,
                                    color = FlatTextSecondary
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = CurrencyUtils.formatInr(pp.grossProfit),
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isLowMargin) RepAmber else RepGreen
                                )
                                Text(
                                    text = "${pp.marginPercent}% margin",
                                    fontSize = 12.5.sp,
                                    color = FlatTextSecondary
                                )
                            }
                        }

                        // Progress bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(RepTrackBg)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fillFraction)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isLowMargin) RepAmberBar else RepGreen)
                            )
                        }
                    }
                }
            }
        }

        // Advisory Callout Banner matching Reports · Profit view.pdf
        lowMarginCandidate?.let { candidate ->
            val extraGain = candidate.totalSoldKg.setScale(0, RoundingMode.HALF_UP).max(BigDecimal("240"))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = RepAmberCalloutBg,
                border = BorderStroke(1.dp, RepAmberCalloutBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(RepAmberBar)
                    )
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = kw(0xFF78350F))) {
                                append("${candidate.feedName} margin is ${candidate.marginPercent}%. ")
                            }
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = kw(0xFF78350F))) {
                                append("Raising the 1 kg rate by ₹1 would add about ${CurrencyUtils.formatInr(extraGain)} this month.")
                            }
                        },
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Daily revenue & margin table
        Text(
            text = tr("Daily revenue & margin", "दैनिक बिक्री और मुनाफा"),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = FlatTextPrimary
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = FlatDarkCard,
            border = BorderStroke(1.dp, FlatDarkBorder)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DAY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.weight(1.3f)
                    )
                    Text(
                        text = "REVENUE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.6.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "PROFIT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.6.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.6.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.75f)
                    )
                }

                HorizontalDivider(color = FlatDarkBorder, thickness = 1.dp)

                if (dailyProfits.isEmpty()) {
                    Text(
                        text = tr("No daily records in this period.", "इस अवधि में कोई दैनिक रिकॉर्ड नहीं है।"),
                        fontSize = 13.5.sp,
                        color = FlatTextMuted,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    dailyProfits.take(15).forEachIndexed { idx, dp ->
                        val pct = if (dp.totalRevenue > BigDecimal.ZERO) {
                            dp.grossProfit.multiply(BigDecimal("100"))
                                .divide(dp.totalRevenue, 1, RoundingMode.HALF_UP)
                        } else BigDecimal.ZERO

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dayTableFormat.format(Date(dp.dayStartMillis)),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary,
                                modifier = Modifier.weight(1.3f)
                            )
                            Text(
                                text = CurrencyUtils.formatInr(dp.totalRevenue),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FlatTextPrimary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = CurrencyUtils.formatInr(dp.grossProfit),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = RepGreen,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "$pct%",
                                fontSize = 13.sp,
                                color = FlatTextSecondary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.75f)
                            )
                        }

                        if (idx < dailyProfits.take(15).lastIndex) {
                            HorizontalDivider(color = FlatDarkBorder, thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReportsCustomerInsightsTab(
    customerInsights: List<CustomerInsight>,
    villageSummaries: List<VillageUdhariSummary>,
    totalCollectedMonth: BigDecimal,
    onCollectClick: (() -> Unit)?
) {
    val totalUdhariOutstanding = remember(customerInsights) {
        customerInsights.fold(BigDecimal.ZERO) { acc, ci -> acc.add(ci.currentUdhari) }
    }
    val customersOwingCount = remember(customerInsights) {
        customerInsights.count { it.currentUdhari > BigDecimal.ZERO }
    }

    val topBuyers = remember(customerInsights) {
        customerInsights.filter { it.customer.id != "walk_in_customer" && !it.customer.name.equals("Walk-in Customer", ignoreCase = true) }
            .sortedByDescending { it.totalPurchased }
            .take(8)
    }

    val mostOverdueCustomer = remember(customerInsights) {
        customerInsights.filter { it.currentUdhari > BigDecimal.ZERO }
            .maxWithOrNull(compareBy<CustomerInsight> { it.overdueDays }.thenBy { it.currentUdhari })
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1: TOTAL UDHARI OUTSTANDING & COLLECTED · MONTH
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = tr("TOTAL UDHARI\nOUTSTANDING", "कुल बकाया उधारी"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        lineHeight = 14.sp,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = CurrencyUtils.formatInr(totalUdhariOutstanding),
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        color = RepRed
                    )
                    Text(
                        text = "$customersOwingCount customers owe",
                        fontSize = 12.5.sp,
                        color = FlatTextSecondary
                    )
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = tr("COLLECTED · MONTH", "वसूली · इस महीने"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.6.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = CurrencyUtils.formatInr(totalCollectedMonth),
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        color = RepGreen
                    )
                    Text(
                        text = tr("Repayments received", "जमा प्राप्त"),
                        fontSize = 12.5.sp,
                        color = FlatTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Village-wise outstanding udhari
        Text(
            text = tr("Village-wise outstanding udhari", "गाँव के अनुसार बकाया उधारी"),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = FlatTextPrimary
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = FlatDarkCard,
            border = BorderStroke(1.dp, FlatDarkBorder)
        ) {
            if (villageSummaries.isEmpty()) {
                Text(
                    text = tr("No village outstanding balances.", "किसी गाँव में उधारी बकाया नहीं है।"),
                    fontSize = 14.sp,
                    color = FlatTextMuted,
                    modifier = Modifier.padding(20.dp)
                )
            } else {
                val maxVillageUdhari = villageSummaries.maxOfOrNull { it.totalUdhari.toFloat() }?.coerceAtLeast(1f) ?: 1f
                Column(modifier = Modifier.fillMaxWidth()) {
                    villageSummaries.forEachIndexed { idx, vs ->
                        val sharePct = if (totalUdhariOutstanding > BigDecimal.ZERO) {
                            vs.totalUdhari.multiply(BigDecimal("100"))
                                .divide(totalUdhariOutstanding, 0, RoundingMode.HALF_UP).toInt()
                        } else 0
                        val barFraction = (vs.totalUdhari.toFloat() / maxVillageUdhari).coerceIn(0.18f, 1f)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = vs.villageName,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FlatTextPrimary
                                    )
                                    Text(
                                        text = "${vs.customerCount} customers · $sharePct% of total",
                                        fontSize = 12.5.sp,
                                        color = FlatTextSecondary
                                    )
                                }

                                Text(
                                    text = CurrencyUtils.formatInr(vs.totalUdhari),
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RepRed
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(RepTrackBg)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(barFraction)
                                        .clip(RoundedCornerShape(50))
                                        .background(RepRed)
                                )
                            }
                        }

                        if (idx < villageSummaries.lastIndex) {
                            HorizontalDivider(color = FlatDarkBorder, thickness = 1.dp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Top buyers & trust scores
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = tr("Top buyers & trust scores", "शीर्ष खरीदार और ट्रस्ट स्कोर"),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = FlatTextPrimary
            )
            Text(
                text = tr("Trust = on-time repayments, credit use, days overdue", "ट्रस्ट = समय पर भुगतान, उधारी सीमा और बकाया दिन"),
                fontSize = 12.5.sp,
                color = FlatTextSecondary
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = FlatDarkCard,
            border = BorderStroke(1.dp, FlatDarkBorder)
        ) {
            if (topBuyers.isEmpty()) {
                Text(
                    text = tr("No customer purchase records yet.", "अभी तक किसी ग्राहक की खरीद दर्ज नहीं है।"),
                    fontSize = 14.sp,
                    color = FlatTextMuted,
                    modifier = Modifier.padding(20.dp)
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    topBuyers.forEachIndexed { idx, ci ->
                        val (scoreBg, scoreColor, labelText) = when {
                            ci.trustScore >= 85 -> Triple(RepGreenSoft, RepGreen, "Excellent")
                            ci.trustScore >= 60 -> Triple(RepAmberSoft, kw(0xFF8A4B08), "Good")
                            else -> Triple(RepRedSoft, RepRed, "Risky")
                        }
                        val village = ci.customer.village

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${idx + 1}",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                modifier = Modifier.width(20.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(RepAvatarBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = customerInitialsForReports(ci.customer.name),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RepAvatarText
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = ci.customer.name,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$village · bought ${CurrencyUtils.formatInr(ci.totalPurchased)}",
                                    fontSize = 12.5.sp,
                                    color = FlatTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = scoreBg
                                ) {
                                    Text(
                                        text = "${ci.trustScore}",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = scoreColor,
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 3.dp)
                                    )
                                }
                                Text(
                                    text = labelText,
                                    fontSize = 11.5.sp,
                                    color = FlatTextSecondary
                                )
                            }
                        }

                        if (idx < topBuyers.lastIndex) {
                            HorizontalDivider(color = FlatDarkBorder, thickness = 1.dp)
                        }
                    }
                }
            }
        }

        // Overdue Customer Alert Callout Banner matching Reports · Customer insights.pdf
        mostOverdueCustomer?.let { overdueCust ->
            val days = overdueCust.overdueDays.coerceAtLeast(14)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = kw(0xFFFCE8E8),
                border = BorderStroke(1.dp, kw(0xFFF5BABA))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = kw(0xFF7F1D1D))) {
                                append(overdueCust.customer.name)
                            }
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = kw(0xFF7F1D1D))) {
                                append(" has ${CurrencyUtils.formatInr(overdueCust.currentUdhari)} overdue for $days days. Consider pausing new udhari.")
                            }
                        },
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp)
                    )

                    if (onCollectClick != null) {
                        Button(
                            onClick = onCollectClick,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RepGreen,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(
                                text = tr("Collect", "वसूली"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReportsExportAndBackupScreen(
    context: Context,
    shopName: String,
    selectedPeriod: ReportPeriod,
    periodRangeText: String,
    lastSyncTimeMillis: Long,
    filteredSales: List<SaleEntry>,
    filteredPurchases: List<PurchaseInvoice>,
    allCustomers: List<Customer>,
    allProducts: List<Product>,
    allSales: List<SaleEntry>,
    allPayments: List<PaymentEntry>,
    productMap: Map<String, Product>,
    customerMap: Map<String, Customer>,
    onChangePeriod: (ReportPeriod) -> Unit,
    onTriggerBackupNow: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var includeHsnSales by remember { mutableStateOf(true) }
    var includeMarginStatement by remember { mutableStateOf(true) }
    var includePurchaseBills by remember { mutableStateOf(true) }
    var includeExpensesCharges by remember { mutableStateOf(false) }
    var autoBackupEnabled by remember { mutableStateOf(true) }
    var showPeriodPickerDialog by remember { mutableStateOf(false) }

    val backupDateFormat = remember { SimpleDateFormat("d MMM, hh:mm a", Locale.getDefault()) }
    val effectiveBackupMillis = remember(lastSyncTimeMillis) {
        if (lastSyncTimeMillis > 0L) lastSyncTimeMillis
        else System.currentTimeMillis() - 5L * 24L * 60L * 60L * 1000L
    }
    val daysSinceBackup = remember(effectiveBackupMillis) {
        val diff = ((System.currentTimeMillis() - effectiveBackupMillis) / (24L * 60L * 60L * 1000L)).toInt()
        when {
            diff <= 0 -> "Today"
            diff == 1 -> "1 day ago"
            else -> "$diff days ago"
        }
    }

    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = RepGreen,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = kw(0xFFD1D5DB),
        uncheckedBorderColor = Color.Transparent
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlatDarkBg)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp)
    ) {
        // Top Header matching Reports · Export & backup.pdf
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
                    .testTag("export_backup_back_btn"),
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
                    text = tr("Export & backup", "एक्सपोर्ट और बैकअप"),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary
                )
                Text(
                    text = tr("GST statement · Google Drive", "GST विवरण · Google Drive"),
                    fontSize = 13.sp,
                    color = FlatTextSecondary
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 1: PERIOD Card
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
                            text = "PERIOD",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.6.sp
                        )
                        Text(
                            text = "${selectedPeriod.label} · $periodRangeText",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                    }

                    Text(
                        text = tr("Change", "बदलें"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = RepGreen,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showPeriodPickerDialog = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Card 2: GST accountant export
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
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = tr("GST accountant export", "GST अकाउंटेंट एक्सपोर्ट"),
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                        Text(
                            text = tr(
                                "HSN 2309 sales and margin statement for your accountant.",
                                "अकाउंटेंट के लिए HSN 2309 बिक्री और मुनाफा विवरण।"
                            ),
                            fontSize = 13.sp,
                            color = FlatTextSecondary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                BackupExportUtil.exportGstReadyCsv(
                                    context = context,
                                    shopName = shopName,
                                    periodLabel = selectedPeriod.label,
                                    sales = filteredSales,
                                    purchases = filteredPurchases,
                                    products = productMap,
                                    customers = customerMap
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_gst_csv_btn"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RepGreen,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Excel / CSV",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                BackupExportUtil.exportGstReadyPdf(
                                    context = context,
                                    shopName = shopName,
                                    periodLabel = selectedPeriod.label,
                                    sales = filteredSales,
                                    purchases = filteredPurchases,
                                    products = productMap,
                                    customers = customerMap
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_gst_pdf_btn"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, RepGreen),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = FlatDarkCard,
                                contentColor = RepGreen
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                tint = RepGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "GST PDF",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = RepGreen
                            )
                        }
                    }

                    // Toggle 1: Sales with HSN code
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("Sales with HSN code", "HSN कोड के साथ बिक्री"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = "${filteredSales.size} bills",
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary
                            )
                        }
                        Switch(
                            checked = includeHsnSales,
                            onCheckedChange = { includeHsnSales = it },
                            colors = switchColors
                        )
                    }

                    HorizontalDivider(color = FlatDarkBorder, thickness = 1.dp)

                    // Toggle 2: Margin statement
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("Margin statement", "मुनाफा विवरण"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = tr("Product-wise cost and profit", "आइटम के अनुसार लागत और मुनाफा"),
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary
                            )
                        }
                        Switch(
                            checked = includeMarginStatement,
                            onCheckedChange = { includeMarginStatement = it },
                            colors = switchColors
                        )
                    }

                    HorizontalDivider(color = FlatDarkBorder, thickness = 1.dp)

                    // Toggle 3: Purchase bills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("Purchase bills", "खरीद बिल"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = "For input credit, ${filteredPurchases.size} bills",
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary
                            )
                        }
                        Switch(
                            checked = includePurchaseBills,
                            onCheckedChange = { includePurchaseBills = it },
                            colors = switchColors
                        )
                    }

                    HorizontalDivider(color = FlatDarkBorder, thickness = 1.dp)

                    // Toggle 4: Expenses & charges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("Expenses & charges", "खर्च और भाड़ा"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = tr("Freight, hamali, other", "भाड़ा, हम्माली और अन्य खर्च"),
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary
                            )
                        }
                        Switch(
                            checked = includeExpensesCharges,
                            onCheckedChange = { includeExpensesCharges = it },
                            colors = switchColors
                        )
                    }
                }
            }

            // Card 3: Google Drive backup
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
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = tr("Google Drive backup", "Google Drive बैकअप"),
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = "Last backup ${backupDateFormat.format(Date(effectiveBackupMillis))}",
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = RepAmberSoft
                        ) {
                            Text(
                                text = daysSinceBackup,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = kw(0xFF8A4B08),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            BackupExportUtil.exportFullBackupJson(
                                context = context,
                                shopName = shopName,
                                customers = allCustomers,
                                products = allProducts,
                                sales = allSales,
                                payments = allPayments
                            )
                            onTriggerBackupNow()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("drive_backup_btn"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RepGreen,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tr("Back up now", "अभी बैकअप लें"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("Auto backup", "ऑटो बैकअप"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = tr("Every Sunday night, on Wi-Fi", "हर रविवार रात, Wi-Fi पर"),
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary
                            )
                        }
                        Switch(
                            checked = autoBackupEnabled,
                            onCheckedChange = { autoBackupEnabled = it },
                            colors = switchColors
                        )
                    }
                }
            }
        }
    }

    if (showPeriodPickerDialog) {
        val options = ReportPeriod.entries
        AlertDialog(
            onDismissRequest = { showPeriodPickerDialog = false },
            containerColor = FlatDarkCard,
            title = {
                Text(
                    text = tr("Select report period", "रिपोर्ट अवधि चुनें"),
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { period ->
                        Surface(
                            onClick = {
                                onChangePeriod(period)
                                showPeriodPickerDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedPeriod == period) RepGreenSoft else RepChipIdleBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = tr(period.label, period.labelHi),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedPeriod == period) RepGreen else FlatTextPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPeriodPickerDialog = false }) {
                    Text(tr("Close", "बंद करें"), color = RepGreen, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

internal fun customerInitialsForReports(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase(Locale.getDefault())
        parts.size == 1 -> parts[0].take(2).uppercase(Locale.getDefault())
        else -> "CU"
    }
}
