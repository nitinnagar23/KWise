package com.example.ui.cashbook

import com.example.ui.theme.kw
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.text.style.TextOverflow

private val CbBrandGreen: Color get() = kw(0xFF0E6F43)
private val CbMintBg: Color get() = kw(0xFFDDF3E8)
private val CbMintBorder: Color get() = kw(0xFFA7DFC2)
private val CbCrimsonRed: Color get() = kw(0xFFB91C1C)
private val CbCrimsonSoftBg: Color get() = kw(0xFFFCE4E4)
private val CbAmberText: Color get() = kw(0xFFB45309)
private val CbAmberSoftBg: Color get() = kw(0xFFFEF3C7)
private val CbAmberBorder: Color get() = kw(0xFFF59E0B)
private val CbNeutralPillBg: Color get() = kw(0xFFF3F3F8)
private val CbChipUnselectedBg: Color get() = kw(0xFFF1F1F7)
data class ExpenseCategoryConfig(
    val name: String,
    val colorHex: Long,
    val enabled: Boolean = true,
    val isSystem: Boolean = false,
    val systemSubtitle: String = ""
)

data class ClosedDayRecord(
    val dayStartMillis: Long,
    val closedAtMillis: Long,
    val closedByName: String,
    val openingCash: BigDecimal,
    val cashIn: BigDecimal,
    val cashOut: BigDecimal,
    val expectedCash: BigDecimal,
    val countedCash: BigDecimal,
    val differenceReason: String,
    val differenceNote: String,
    val billsCount: Int,
    val totalSales: BigDecimal,
    val udhariGiven: BigDecimal,
    val collectionsReceived: BigDecimal,
    val upiReceived: BigDecimal
)

object ExpenseCategoryStore {
    private const val PREFS_NAME = "khatawise_expense_categories_prefs"
    private const val KEY_CATEGORIES = "expense_categories_json"
    private const val KEY_CLOSED_DAYS = "closed_days_json"

    val defaultCategories = listOf(
        ExpenseCategoryConfig("Transport", 0xFFEA580C, enabled = true),
        ExpenseCategoryConfig("Labour · Ladaan & Utaar", 0xFF7C3AED, enabled = true),
        ExpenseCategoryConfig("Tea & snacks", 0xFFD97706, enabled = true),
        ExpenseCategoryConfig("Electricity", 0xFF2563EB, enabled = true),
        ExpenseCategoryConfig("Rent", 0xFFDB2777, enabled = true),
        ExpenseCategoryConfig(
            name = "Supplier payment",
            colorHex = 0xFF0E6F43,
            enabled = true,
            isSystem = true,
            systemSubtitle = "From supplier bills"
        ),
        ExpenseCategoryConfig(
            name = "Stock loss",
            colorHex = 0xFFB91C1C,
            enabled = true,
            isSystem = true,
            systemSubtitle = "From damaged / expired stock"
        )
    )

    fun loadCategories(context: Context): List<ExpenseCategoryConfig> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CATEGORIES, null) ?: return defaultCategories
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<ExpenseCategoryConfig>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val name = obj.optString("name", "").trim()
                if (name.isNotEmpty()) {
                    list.add(
                        ExpenseCategoryConfig(
                            name = name,
                            colorHex = obj.optLong("colorHex", 0xFF0E6F43),
                            enabled = obj.optBoolean("enabled", true),
                            isSystem = obj.optBoolean("isSystem", false),
                            systemSubtitle = obj.optString("systemSubtitle", "")
                        )
                    )
                }
            }
            if (list.isEmpty()) defaultCategories else list
        } catch (_: Exception) {
            defaultCategories
        }
    }

    fun saveCategories(context: Context, categories: List<ExpenseCategoryConfig>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        categories.forEach { cat ->
            val obj = JSONObject()
            obj.put("name", cat.name)
            obj.put("colorHex", cat.colorHex)
            obj.put("enabled", cat.enabled)
            obj.put("isSystem", cat.isSystem)
            obj.put("systemSubtitle", cat.systemSubtitle)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_CATEGORIES, arr.toString()).apply()
    }

    fun getEnabledCashOutCategoryNames(context: Context): List<String> {
        return loadCategories(context)
            .filter { it.enabled && it.name != "Stock loss" }
            .map {
                if (it.name.startsWith("Labour")) "Labour" else it.name
            }
            .distinct() + listOf("Other")
    }

    fun saveClosedDay(context: Context, record: ClosedDayRecord) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = loadClosedDaysMap(context).toMutableMap()
        existing[record.dayStartMillis] = record
        persistClosedDaysMap(prefs, existing)
    }

    fun reopenDay(context: Context, dayStartMillis: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = loadClosedDaysMap(context).toMutableMap()
        existing.remove(dayStartMillis)
        persistClosedDaysMap(prefs, existing)
    }

    fun getClosedDay(context: Context, dayStartMillis: Long): ClosedDayRecord? {
        return loadClosedDaysMap(context)[dayStartMillis]
    }

    private fun loadClosedDaysMap(context: Context): Map<Long, ClosedDayRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CLOSED_DAYS, null) ?: return emptyMap()
        return try {
            val arr = JSONArray(raw)
            val map = mutableMapOf<Long, ClosedDayRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val dayStart = obj.optLong("dayStartMillis", 0L)
                if (dayStart > 0L) {
                    map[dayStart] = ClosedDayRecord(
                        dayStartMillis = dayStart,
                        closedAtMillis = obj.optLong("closedAtMillis", System.currentTimeMillis()),
                        closedByName = obj.optString("closedByName", "Admin"),
                        openingCash = obj.optString("openingCash", "0").toBigDecimalOrNull() ?: BigDecimal.ZERO,
                        cashIn = obj.optString("cashIn", "0").toBigDecimalOrNull() ?: BigDecimal.ZERO,
                        cashOut = obj.optString("cashOut", "0").toBigDecimalOrNull() ?: BigDecimal.ZERO,
                        expectedCash = obj.optString("expectedCash", "0").toBigDecimalOrNull() ?: BigDecimal.ZERO,
                        countedCash = obj.optString("countedCash", "0").toBigDecimalOrNull() ?: BigDecimal.ZERO,
                        differenceReason = obj.optString("differenceReason", ""),
                        differenceNote = obj.optString("differenceNote", ""),
                        billsCount = obj.optInt("billsCount", 0),
                        totalSales = obj.optString("totalSales", "0").toBigDecimalOrNull() ?: BigDecimal.ZERO,
                        udhariGiven = obj.optString("udhariGiven", "0").toBigDecimalOrNull() ?: BigDecimal.ZERO,
                        collectionsReceived = obj.optString("collectionsReceived", "0").toBigDecimalOrNull() ?: BigDecimal.ZERO,
                        upiReceived = obj.optString("upiReceived", "0").toBigDecimalOrNull() ?: BigDecimal.ZERO
                    )
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun persistClosedDaysMap(
        prefs: android.content.SharedPreferences,
        map: Map<Long, ClosedDayRecord>
    ) {
        val arr = JSONArray()
        map.values.forEach { r ->
            val obj = JSONObject()
            obj.put("dayStartMillis", r.dayStartMillis)
            obj.put("closedAtMillis", r.closedAtMillis)
            obj.put("closedByName", r.closedByName)
            obj.put("openingCash", r.openingCash.toPlainString())
            obj.put("cashIn", r.cashIn.toPlainString())
            obj.put("cashOut", r.cashOut.toPlainString())
            obj.put("expectedCash", r.expectedCash.toPlainString())
            obj.put("countedCash", r.countedCash.toPlainString())
            obj.put("differenceReason", r.differenceReason)
            obj.put("differenceNote", r.differenceNote)
            obj.put("billsCount", r.billsCount)
            obj.put("totalSales", r.totalSales.toPlainString())
            obj.put("udhariGiven", r.udhariGiven.toPlainString())
            obj.put("collectionsReceived", r.collectionsReceived.toPlainString())
            obj.put("upiReceived", r.upiReceived.toPlainString())
            arr.put(obj)
        }
        prefs.edit().putString(KEY_CLOSED_DAYS, arr.toString()).apply()
    }
}

// ============================================================================
// PAGE 10: Close day screen
// ============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloseDayScreen(
    dayStartMillis: Long,
    headerDateLabel: String,
    openingCash: BigDecimal,
    dayCashIn: BigDecimal,
    dayCashOut: BigDecimal,
    expectedCash: BigDecimal,
    billsCount: Int,
    totalSales: BigDecimal,
    udhariGiven: BigDecimal,
    collectionsReceived: BigDecimal,
    upiReceived: BigDecimal,
    closedByName: String,
    onBack: () -> Unit,
    onDayClosed: (ClosedDayRecord, Boolean) -> Unit
) {
    BackHandler { onBack() }

    val denominations = remember { listOf(500, 200, 100, 50, 20, 10) }

    // Pre-populate denomination counts that approximate expectedCash so the user can immediately adjust
    val denomCounts = remember(expectedCash) {
        val map = mutableStateMapOf<Int, String>()
        var remaining = expectedCash.setScale(0, RoundingMode.HALF_UP).toInt().coerceAtLeast(0)
        denominations.forEach { d ->
            val count = remaining / d
            map[d] = count.toString()
            remaining %= d
        }
        map
    }

    var enterTotalInstead by remember { mutableStateOf(false) }
    var directTotalInput by remember(expectedCash) {
        mutableStateOf(expectedCash.setScale(0, RoundingMode.HALF_UP).toPlainString())
    }

    val countedCash = remember(
        enterTotalInstead,
        directTotalInput,
        denomCounts[500],
        denomCounts[200],
        denomCounts[100],
        denomCounts[50],
        denomCounts[20],
        denomCounts[10]
    ) {
        if (enterTotalInstead) {
            directTotalInput.trim().toBigDecimalOrNull() ?: BigDecimal.ZERO
        } else {
            var sum = 0L
            denominations.forEach { d ->
                val c = denomCounts[d]?.trim()?.toLongOrNull() ?: 0L
                sum += d * c
            }
            BigDecimal.valueOf(sum)
        }
    }

    val diffAmount = remember(countedCash, expectedCash) {
        countedCash.subtract(expectedCash.setScale(0, RoundingMode.HALF_UP))
    }

    val differenceReasons = remember {
        listOf("Change given", "Missed entry", "Not sure")
    }
    var selectedReason by remember { mutableStateOf("Missed entry") }
    var differenceNote by remember { mutableStateOf("Tea expense not entered") }
    var carryForwardOpening by remember { mutableStateOf(true) }

    val tomorrowDateLabel = remember(dayStartMillis) {
        val fmt = SimpleDateFormat("d MMM", Locale.ENGLISH)
        fmt.format(Date(dayStartMillis + 24L * 60L * 60L * 1000L))
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
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("close_day_cancel_btn")
                    ) {
                        Text(
                            text = tr("Cancel", "रद्द करें"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = {
                            val record = ClosedDayRecord(
                                dayStartMillis = dayStartMillis,
                                closedAtMillis = System.currentTimeMillis(),
                                closedByName = closedByName.ifBlank { "Admin" },
                                openingCash = openingCash,
                                cashIn = dayCashIn,
                                cashOut = dayCashOut,
                                expectedCash = expectedCash.setScale(0, RoundingMode.HALF_UP),
                                countedCash = countedCash,
                                differenceReason = selectedReason,
                                differenceNote = differenceNote.trim(),
                                billsCount = billsCount,
                                totalSales = totalSales,
                                udhariGiven = udhariGiven,
                                collectionsReceived = collectionsReceived,
                                upiReceived = upiReceived
                            )
                            onDayClosed(record, carryForwardOpening)
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CbBrandGreen,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .heightIn(min = 48.dp)
                            .testTag("close_day_confirm_btn")
                    ) {
                        Text(
                            text = tr("Close day", "दिन बंद करें"),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            .testTag("close_day_back_btn")
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
                            text = tr("Close day", "दिन बंद करें"),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                        Text(
                            text = headerDateLabel,
                            fontSize = 13.sp,
                            color = FlatTextSecondary
                        )
                    }
                }
            }

            // 2. CASH THE BOOK EXPECTS Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = tr("CASH THE BOOK EXPECTS", "किताब के अनुसार नकद"),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.6.sp
                            )
                            Text(
                                text = CurrencyUtils.formatInr(expectedCash),
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CbNeutralPillBg,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = tr("Opening", "ओपनिंग"),
                                        fontSize = 12.sp,
                                        color = FlatTextSecondary
                                    )
                                    Text(
                                        text = CurrencyUtils.formatInr(openingCash),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FlatTextPrimary
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CbMintBg,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = tr("Cash in", "कैश इन"),
                                        fontSize = 12.sp,
                                        color = CbBrandGreen
                                    )
                                    Text(
                                        text = "+${CurrencyUtils.formatInr(dayCashIn)}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CbBrandGreen
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CbCrimsonSoftBg,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = tr("Cash out", "कैश आउट"),
                                        fontSize = 12.sp,
                                        color = CbCrimsonRed
                                    )
                                    Text(
                                        text = "−${CurrencyUtils.formatInr(dayCashOut)}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CbCrimsonRed
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Section Header: Count your cash | Enter total instead
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("Count your cash", "अपना कैश गिनें"),
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary
                    )
                    Text(
                        text = if (enterTotalInstead) {
                            tr("Count notes instead", "नोट गिनें")
                        } else {
                            tr("Enter total instead", "सीधे कुल राशि लिखें")
                        },
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = CbBrandGreen,
                        modifier = Modifier
                            .clickable { enterTotalInstead = !enterTotalInstead }
                            .testTag("close_day_toggle_total_mode")
                    )
                }
            }

            // 4. Denomination Counter Card OR Direct Total Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (enterTotalInstead) {
                            Text(
                                text = tr("TOTAL CASH COUNTED", "कुल गिना गया कैश"),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary
                            )
                            OutlinedTextField(
                                value = directTotalInput,
                                onValueChange = { raw ->
                                    directTotalInput = raw.filter { it.isDigit() }.take(9)
                                },
                                leadingIcon = {
                                    Text("₹", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CbBrandGreen)
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("close_day_direct_total_input")
                            )
                        } else {
                            denominations.forEach { denom ->
                                val countStr = denomCounts[denom] ?: "0"
                                val countVal = countStr.trim().toLongOrNull() ?: 0L
                                val lineTotal = BigDecimal.valueOf(denom * countVal)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = CbNeutralPillBg,
                                        modifier = Modifier.width(68.dp)
                                    ) {
                                        Text(
                                            text = "₹$denom",
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FlatTextPrimary,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }

                                    Text(
                                        text = "×",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FlatTextSecondary
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = FlatDarkCard,
                                        border = BorderStroke(1.dp, FlatDarkBorder),
                                        modifier = Modifier.width(96.dp)
                                    ) {
                                        BasicTextField(
                                            value = countStr,
                                            onValueChange = { raw ->
                                                denomCounts[denom] = raw.filter { it.isDigit() }.take(5)
                                            },
                                            textStyle = TextStyle(
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FlatTextPrimary,
                                                textAlign = TextAlign.Center
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            cursorBrush = SolidColor(CbBrandGreen),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp, horizontal = 8.dp)
                                                .testTag("denom_input_$denom")
                                        )
                                    }

                                    Text(
                                        text = CurrencyUtils.formatInr(lineTotal),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FlatTextPrimary,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.width(86.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = FlatDarkBorder, modifier = Modifier.padding(vertical = 4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("Cash counted", "गिना गया कैश"),
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = CurrencyUtils.formatInr(countedCash),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = CbBrandGreen,
                                modifier = Modifier.testTag("close_day_counted_cash_text")
                            )
                        }
                    }
                }
            }

            // 5. Difference Card
            item {
                val isExact = diffAmount.compareTo(BigDecimal.ZERO) == 0
                val cardBg = if (isExact) CbMintBg else CbAmberSoftBg
                val cardBorder = if (isExact) CbMintBorder else CbAmberBorder
                val textColor = if (isExact) CbBrandGreen else CbAmberText

                val diffLabel = when {
                    diffAmount < BigDecimal.ZERO -> "Short ${CurrencyUtils.formatInr(diffAmount.abs())}"
                    diffAmount > BigDecimal.ZERO -> "Extra ${CurrencyUtils.formatInr(diffAmount)}"
                    else -> "Matched ₹0"
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
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
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Text(
                                text = "Expect ${CurrencyUtils.formatInr(expectedCash)} · Counted ${CurrencyUtils.formatInr(countedCash)}",
                                fontSize = 12.5.sp,
                                color = textColor.copy(alpha = 0.85f)
                            )
                        }

                        Text(
                            text = diffLabel,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.testTag("close_day_diff_text")
                        )
                    }
                }
            }

            // 6. REASON FOR DIFFERENCE Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = tr("REASON FOR DIFFERENCE", "अंतर का कारण"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.5.sp
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            differenceReasons.forEach { reason ->
                                val selected = selectedReason == reason
                                Surface(
                                    onClick = { selectedReason = reason },
                                    shape = RoundedCornerShape(50),
                                    color = if (selected) CbBrandGreen else CbChipUnselectedBg,
                                    modifier = Modifier.testTag("diff_reason_${reason.lowercase().replace(" ", "_")}")
                                ) {
                                    Text(
                                        text = reason,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.White else FlatTextPrimary,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = differenceNote,
                            onValueChange = { differenceNote = it },
                            placeholder = {
                                Text(
                                    text = tr("Add note (e.g. Tea expense not entered)", "नोट लिखें"),
                                    color = FlatTextMuted,
                                    fontSize = 14.sp
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = FlatDarkCard,
                                unfocusedContainerColor = FlatDarkCard,
                                focusedBorderColor = CbBrandGreen,
                                unfocusedBorderColor = FlatDarkBorder,
                                focusedTextColor = FlatTextPrimary,
                                unfocusedTextColor = FlatTextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("close_day_reason_note_input")
                        )
                    }
                }
            }

            // 7. Start tomorrow with ₹... Switch Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
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
                                text = "Start tomorrow with ${CurrencyUtils.formatInr(countedCash)}",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = "Sets opening cash for $tomorrowDateLabel",
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary
                            )
                        }

                        Switch(
                            checked = carryForwardOpening,
                            onCheckedChange = { carryForwardOpening = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CbBrandGreen,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = kw(0xFFD1D5DB)
                            ),
                            modifier = Modifier.testTag("close_day_carry_forward_switch")
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// PAGE 11: Day closed summary screen
// ============================================================================
@Composable
fun DayClosedSummaryScreen(
    record: ClosedDayRecord,
    isAdmin: Boolean,
    onBack: () -> Unit,
    onReopenDay: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    val dateStr = remember(record.dayStartMillis) {
        SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date(record.dayStartMillis))
    }
    val timeStr = remember(record.closedAtMillis) {
        SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(record.closedAtMillis))
    }
    val diffAmount = remember(record.countedCash, record.expectedCash) {
        record.countedCash.subtract(record.expectedCash)
    }

    val adminOnlyToast = tr("Only Admin can reopen a closed day.", "केवल एडमिन ही बंद दिन को फिर से खोल सकता है।")

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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val summaryText = buildString {
                                    appendLine("KhataWise Day Closed — $dateStr")
                                    appendLine("Closed by ${record.closedByName} at $timeStr")
                                    appendLine("Bills: ${record.billsCount} | Sales: ${CurrencyUtils.formatInr(record.totalSales)}")
                                    appendLine("Expected Cash: ${CurrencyUtils.formatInr(record.expectedCash)}")
                                    appendLine("Counted Cash: ${CurrencyUtils.formatInr(record.countedCash)}")
                                    appendLine("UPI Received: ${CurrencyUtils.formatInr(record.upiReceived)}")
                                }
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, summaryText)
                                }
                                try {
                                    context.startActivity(Intent.createChooser(sendIntent, "Share summary"))
                                } catch (_: Exception) {}
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, FlatDarkBorder),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("day_closed_share_btn")
                        ) {
                            Text(
                                text = tr("Share summary", "सारांश साझा करें"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = {
                                if (isAdmin) {
                                    onReopenDay()
                                } else {
                                    Toast.makeText(context, adminOnlyToast, Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CbBrandGreen,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("day_closed_reopen_btn")
                        ) {
                            Text(
                                text = tr("Reopen day", "दिन फिर से खोलें"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Text(
                        text = tr("Only Admin can reopen a closed day.", "केवल एडमिन ही बंद दिन को फिर से खोल सकता है।"),
                        fontSize = 12.sp,
                        color = FlatTextSecondary
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Checkmark + Title + Subtitle
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(CbMintBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = CbBrandGreen,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Text(
                        text = tr("Day closed", "दिन बंद हो गया"),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary
                    )

                    Text(
                        text = "$dateStr · closed by ${record.closedByName} at $timeStr",
                        fontSize = 13.sp,
                        color = FlatTextSecondary
                    )
                }
            }

            // 2. SALES Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = tr("SALES", "बिक्री"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.6.sp
                        )

                        DayClosedKeyValueRow(
                            label = tr("Bills", "बिल"),
                            value = record.billsCount.toString(),
                            valueColor = FlatTextPrimary
                        )
                        DayClosedKeyValueRow(
                            label = tr("Sales", "बिक्री"),
                            value = CurrencyUtils.formatInr(record.totalSales),
                            valueColor = FlatTextPrimary
                        )
                        DayClosedKeyValueRow(
                            label = tr("Udhari given", "उधारी दी गई"),
                            value = CurrencyUtils.formatInr(record.udhariGiven),
                            valueColor = CbCrimsonRed
                        )
                        DayClosedKeyValueRow(
                            label = tr("Collections received", "वसूली प्राप्त"),
                            value = CurrencyUtils.formatInr(record.collectionsReceived),
                            valueColor = CbBrandGreen
                        )
                    }
                }
            }

            // 3. CASH Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = tr("CASH", "नकद"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.6.sp
                        )

                        DayClosedKeyValueRow(
                            label = tr("Opening", "ओपनिंग"),
                            value = CurrencyUtils.formatInr(record.openingCash),
                            valueColor = FlatTextPrimary
                        )
                        DayClosedKeyValueRow(
                            label = tr("Cash in (cash sales + collections)", "कैश इन (नकद बिक्री + वसूली)"),
                            value = "+${CurrencyUtils.formatInr(record.cashIn)}",
                            valueColor = CbBrandGreen
                        )
                        DayClosedKeyValueRow(
                            label = tr("Cash out (expenses + supplier)", "कैश आउट (ख़र्च + सप्लायर)"),
                            value = "−${CurrencyUtils.formatInr(record.cashOut)}",
                            valueColor = CbCrimsonRed
                        )

                        HorizontalDivider(color = FlatDarkBorder, modifier = Modifier.padding(vertical = 2.dp))

                        DayClosedKeyValueRow(
                            label = tr("Expected", "अपेक्षित"),
                            value = CurrencyUtils.formatInr(record.expectedCash),
                            valueColor = FlatTextPrimary,
                            labelBold = true
                        )
                        DayClosedKeyValueRow(
                            label = tr("Counted", "गिना गया"),
                            value = CurrencyUtils.formatInr(record.countedCash),
                            valueColor = FlatTextPrimary,
                            labelBold = true
                        )

                        val diffNoteLabel = record.differenceNote.ifBlank { record.differenceReason }
                        val diffRowLabel = if (diffNoteLabel.isNotBlank()) {
                            "Difference ($diffNoteLabel)"
                        } else {
                            "Difference"
                        }
                        val diffValStr = when {
                            diffAmount < BigDecimal.ZERO -> "−${CurrencyUtils.formatInr(diffAmount.abs())}"
                            diffAmount > BigDecimal.ZERO -> "+${CurrencyUtils.formatInr(diffAmount)}"
                            else -> "₹0"
                        }
                        DayClosedKeyValueRow(
                            label = diffRowLabel,
                            value = diffValStr,
                            valueColor = if (diffAmount.compareTo(BigDecimal.ZERO) == 0) CbBrandGreen else CbAmberText
                        )
                    }
                }
            }

            // 4. UPI received Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier.fillMaxWidth()
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
                                text = tr("UPI received", "UPI प्राप्त"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                            Text(
                                text = tr("Not part of the cash count", "नकद गिनती का हिस्सा नहीं है"),
                                fontSize = 12.5.sp,
                                color = FlatTextSecondary
                            )
                        }

                        Text(
                            text = CurrencyUtils.formatInr(record.upiReceived),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayClosedKeyValueRow(
    label: String,
    value: String,
    valueColor: Color,
    labelBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (labelBold) FontWeight.Bold else FontWeight.SemiBold,
            color = if (labelBold) FlatTextPrimary else FlatTextSecondary,
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        )
        Text(
            text = value,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

// ============================================================================
// PAGE 12: Expense categories screen
// ============================================================================
@Composable
fun ExpenseCategoriesScreen(
    monthEntries: List<CashbookEntry>,
    onBack: () -> Unit,
    onCategoriesChanged: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    var refreshTick by remember { mutableIntStateOf(0) }
    val categories = remember(refreshTick) {
        ExpenseCategoryStore.loadCategories(context)
    }

    var newCategoryName by remember { mutableStateOf("") }
    val categoryAddedToast = tr("Expense category added", "ख़र्च श्रेणी जोड़ी गई")

    // Compute monthly spend per category from actual cashbook entries
    val spendByCategory = remember(monthEntries, categories) {
        val map = mutableMapOf<String, BigDecimal>()
        categories.forEach { cat ->
            val sum = monthEntries.filter { entry ->
                !entry.isCashIn && (
                    entry.title.equals(cat.name, ignoreCase = true) ||
                        (cat.name.startsWith("Labour") && entry.title.startsWith("Labour", ignoreCase = true)) ||
                        (cat.name == "Supplier payment" && (entry.tag == "SUPPLIER PAYMENT" || entry.tag == "PURCHASE PAID" || entry.title.startsWith("Supplier", ignoreCase = true))) ||
                        (cat.name == "Stock loss" && (entry.title.contains("Stock loss", ignoreCase = true) || entry.tag == "STOCK LOSS"))
                    )
            }.fold(BigDecimal.ZERO) { acc, e -> acc.add(e.amount) }
            map[cat.name] = sum
        }
        map
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
                            val trimmed = newCategoryName.trim()
                            if (trimmed.isNotEmpty()) {
                                val palette = listOf(0xFF0891B2, 0xFFEA580C, 0xFF7C3AED, 0xFFD97706, 0xFF2563EB, 0xFFDB2777)
                                val colorHex = palette[categories.size % palette.size]
                                val updated = categories.toMutableList()
                                // Insert before system categories
                                val sysIdx = updated.indexOfFirst { it.isSystem }.let { if (it == -1) updated.size else it }
                                updated.add(
                                    sysIdx,
                                    ExpenseCategoryConfig(
                                        name = trimmed,
                                        colorHex = colorHex,
                                        enabled = true,
                                        isSystem = false
                                    )
                                )
                                ExpenseCategoryStore.saveCategories(context, updated)
                                newCategoryName = ""
                                refreshTick++
                                onCategoriesChanged()
                                Toast.makeText(context, categoryAddedToast, Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CbBrandGreen,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("expense_categories_add_btn")
                    ) {
                        Text(
                            text = tr("+ Add category", "+ श्रेणी जोड़ें"),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            .testTag("expense_categories_back_btn")
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
                            text = tr("Expense categories", "ख़र्च श्रेणियां"),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                        Text(
                            text = tr("Used in Cashbook and reports", "कैशबुक और रिपोर्ट में उपयोग किया जाता है"),
                            fontSize = 13.sp,
                            color = FlatTextSecondary
                        )
                    }
                }
            }

            // 2. Categories List Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        categories.forEachIndexed { idx, cat ->
                            if (idx > 0) {
                                HorizontalDivider(
                                    color = FlatDarkBorder.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }

                            val catSpend = spendByCategory[cat.name] ?: BigDecimal.ZERO
                            val subtitleText = if (cat.isSystem) {
                                "${cat.systemSubtitle} · ${CurrencyUtils.formatInr(catSpend)}"
                            } else {
                                "This month ${CurrencyUtils.formatInr(catSpend)}"
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(Color(cat.colorHex))
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = cat.name,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FlatTextPrimary
                                            )
                                            if (cat.isSystem) {
                                                Surface(
                                                    shape = RoundedCornerShape(50),
                                                    color = CbNeutralPillBg
                                                ) {
                                                    Text(
                                                        text = "System",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = FlatTextSecondary,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = subtitleText,
                                            fontSize = 12.5.sp,
                                            color = FlatTextSecondary
                                        )
                                    }
                                }

                                if (cat.isSystem) {
                                    Text(
                                        text = tr("Always on", "हमेशा चालू"),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FlatTextSecondary
                                    )
                                } else {
                                    Switch(
                                        checked = cat.enabled,
                                        onCheckedChange = { checked ->
                                            val updated = categories.map {
                                                if (it.name == cat.name) it.copy(enabled = checked) else it
                                            }
                                            ExpenseCategoryStore.saveCategories(context, updated)
                                            refreshTick++
                                            onCategoriesChanged()
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = CbBrandGreen,
                                            uncheckedThumbColor = Color.White,
                                            uncheckedTrackColor = kw(0xFFD1D5DB)
                                        ),
                                        modifier = Modifier.testTag("expense_cat_switch_${cat.name.lowercase().replace(" ", "_")}")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Helper text
            item {
                Text(
                    text = tr(
                        "Turning a category off hides it when adding a new expense. Past entries stay in reports.",
                        "किसी श्रेणी को बंद करने पर नया ख़र्च जोड़ते समय वह छुप जाती है। पुरानी प्रविष्टियां रिपोर्ट में रहती हैं।"
                    ),
                    fontSize = 12.5.sp,
                    color = FlatTextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // 4. New category Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = tr("New category", "नई श्रेणी"),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                        Text(
                            text = tr("NAME", "नाम"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        OutlinedTextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it },
                            placeholder = {
                                Text(
                                    text = tr("e.g. Packing", "जैसे पैकिंग"),
                                    color = FlatTextMuted,
                                    fontSize = 14.5.sp
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = FlatDarkCard,
                                unfocusedContainerColor = FlatDarkCard,
                                focusedBorderColor = CbBrandGreen,
                                unfocusedBorderColor = FlatDarkBorder,
                                focusedTextColor = FlatTextPrimary,
                                unfocusedTextColor = FlatTextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("new_expense_category_input")
                        )
                    }
                }
            }
        }
    }
}
