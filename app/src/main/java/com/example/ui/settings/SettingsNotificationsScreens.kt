package com.example.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppSettings
import com.example.model.Customer
import com.example.model.Product
import com.example.model.SaleEntry
import com.example.sync.FirestoreSyncState
import com.example.util.CurrencyUtils
import com.example.util.ShopAnalytics
import com.example.util.tr
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class NotificationCategory {
    ALL,
    DUE,
    STOCK,
    SYNC
}

data class ShopNotificationItem(
    val id: String,
    val category: NotificationCategory,
    val isToday: Boolean,
    val title: String,
    val body: String,
    val timeLabel: String,
    val icon: ImageVector,
    val iconTint: Color,
    val iconBg: Color,
    val unreadDotColor: Color,
    val primaryActionLabel: String? = null,
    val onPrimaryAction: (() -> Unit)? = null,
    val secondaryActionLabel: String? = null,
    val onSecondaryAction: (() -> Unit)? = null
)

fun buildLiveShopNotifications(
    customers: List<Customer>,
    products: List<Product>,
    sales: List<SaleEntry>,
    settings: AppSettings?,
    syncState: FirestoreSyncState,
    extraPrefs: SettingsExtraPrefsStore,
    onSendCustomerReminder: (Customer) -> Unit,
    onNavigateToCollections: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onDismissNotification: (String) -> Unit,
    onBackupNow: () -> Unit
): List<ShopNotificationItem> {
    val dismissed = extraPrefs.dismissedNotificationIds
    val now = System.currentTimeMillis()
    val startOfToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val endOfToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    val items = mutableListOf<ShopNotificationItem>()

    // 1. Overdue customers (promiseDate < startOfToday and totalUdhari > 0)
    val overdueCustomers = customers.filter {
        !it.isDeleted &&
            it.totalUdhari > BigDecimal.ZERO &&
            it.promiseDate != null &&
            it.promiseDate!! < startOfToday
    }.sortedBy { it.promiseDate }

    overdueCustomers.take(5).forEach { cust ->
        val notifId = "overdue_${cust.id}"
        if (notifId !in dismissed) {
            val daysOverdue = (((startOfToday - (cust.promiseDate ?: startOfToday)) / (1000L * 60 * 60 * 24)).toInt())
                .coerceAtLeast(1)
            val amtFormatted = CurrencyUtils.formatInr(cust.totalUdhari)
            val promiseFormatted = SimpleDateFormat("dd MMM", Locale.getDefault())
                .format(Date(cust.promiseDate ?: now))
            items.add(
                ShopNotificationItem(
                    id = notifId,
                    category = NotificationCategory.DUE,
                    isToday = true,
                    title = "${cust.name} is $daysOverdue days overdue",
                    body = "$amtFormatted promised for $promiseFormatted.${if (cust.village.isNotBlank()) " ${cust.village}." else ""}",
                    timeLabel = "9:00 AM",
                    icon = Icons.Default.AccessTime,
                    iconTint = KwRed,
                    iconBg = KwRedSoft,
                    unreadDotColor = KwGreen,
                    primaryActionLabel = "Send reminder",
                    onPrimaryAction = { onSendCustomerReminder(cust) },
                    secondaryActionLabel = "Collect",
                    onSecondaryAction = onNavigateToCollections
                )
            )
        }
    }

    // 2. Collections due today summary
    val dueTodayCustomers = customers.filter {
        !it.isDeleted &&
            it.totalUdhari > BigDecimal.ZERO &&
            (it.promiseDate == null || it.promiseDate!! <= endOfToday)
    }
    if (dueTodayCustomers.isNotEmpty()) {
        val notifId = "collections_due_today"
        if (notifId !in dismissed) {
            val totalDueAmt = dueTodayCustomers.fold(BigDecimal.ZERO) { acc, c -> acc.add(c.totalUdhari) }
            val namesPreview = dueTodayCustomers.take(3).joinToString(", ") {
                "${it.name} (${CurrencyUtils.formatInr(it.totalUdhari)})"
            }
            items.add(
                ShopNotificationItem(
                    id = notifId,
                    category = NotificationCategory.DUE,
                    isToday = true,
                    title = "${dueTodayCustomers.size} collections due today · ${CurrencyUtils.formatInr(totalDueAmt)}",
                    body = namesPreview,
                    timeLabel = "8:00 AM",
                    icon = Icons.Default.Schedule,
                    iconTint = KwAmber,
                    iconBg = KwAmberSoft,
                    unreadDotColor = KwGreen,
                    primaryActionLabel = "View collections",
                    onPrimaryAction = onNavigateToCollections
                )
            )
        }
    }

    // 3. Low / Out of stock items
    val reorderSuggestions = try {
        ShopAnalytics.computeReorderSuggestions(products, sales)
    } catch (_: Throwable) {
        emptyList()
    }
    val lowProducts = products.filter { p ->
        !p.isDeleted && p.currentStockKg <= p.kgPerBag.multiply(BigDecimal("3"))
    }
    lowProducts.take(4).forEach { prod ->
        val notifId = "stock_${prod.id}"
        if (notifId !in dismissed) {
            val isOut = prod.currentStockKg <= BigDecimal.ZERO
            val bagsLeft = if (prod.kgPerBag > BigDecimal.ZERO) {
                prod.currentStockKg.divide(prod.kgPerBag, 0, java.math.RoundingMode.FLOOR).toInt()
            } else {
                prod.currentStockKg.toInt()
            }
            val suggestion = reorderSuggestions.find { it.product.id == prod.id }
            items.add(
                ShopNotificationItem(
                    id = notifId,
                    category = NotificationCategory.STOCK,
                    isToday = true,
                    title = if (isOut) "${prod.feedName} is out of stock" else "${prod.feedName} is running low",
                    body = if (isOut) {
                        "0 bags left.${if (suggestion != null) " Suggested reorder: ${suggestion.suggestedOrderKg.toInt()} kg." else ""}"
                    } else {
                        "$bagsLeft bags left (${prod.currentStockKg.toPlainString()} kg)."
                    },
                    timeLabel = "Yesterday",
                    icon = Icons.Default.Inventory2,
                    iconTint = if (isOut) KwRed else KwAmber,
                    iconBg = if (isOut) KwRedSoft else KwAmberSoft,
                    unreadDotColor = KwGreen,
                    primaryActionLabel = "Reorder list",
                    onPrimaryAction = onNavigateToInventory,
                    secondaryActionLabel = "Dismiss",
                    onSecondaryAction = { onDismissNotification(notifId) }
                )
            )
        }
    }

    // 4. Google Drive Backup age reminder
    val lastBackup = extraPrefs.lastDriveBackupMillis
    val backupDaysAgo = if (lastBackup > 0L) {
        ((now - lastBackup) / (1000L * 60 * 60 * 24)).toInt()
    } else {
        5
    }
    if (backupDaysAgo >= 3 && "drive_backup_reminder" !in dismissed) {
        items.add(
            ShopNotificationItem(
                id = "drive_backup_reminder",
                category = NotificationCategory.SYNC,
                isToday = false,
                title = "No Google Drive backup in $backupDaysAgo days",
                body = if (lastBackup > 0L) {
                    "Last backup was ${SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(lastBackup))}. Connect to Wi-Fi or back up now."
                } else {
                    "Keep a safe copy of your bills and customer khata on Google Drive."
                },
                timeLabel = "Yesterday",
                icon = Icons.Default.CloudOff,
                iconTint = KwAmber,
                iconBg = KwAmberSoft,
                unreadDotColor = Color.Transparent,
                secondaryActionLabel = "Back up now",
                onSecondaryAction = onBackupNow
            )
        )
    }

    // 5. Sync status notification
    val lastSyncTime = settings?.lastSyncTime ?: 0L
    if (lastSyncTime > 0L || syncState is FirestoreSyncState.Success) {
        val totalSyncedRecords = customers.size + products.size + sales.size
        items.add(
            ShopNotificationItem(
                id = "sync_success_info",
                category = NotificationCategory.SYNC,
                isToday = false,
                title = "Synced $totalSyncedRecords records",
                body = "All changes from this phone are safe in the cloud.",
                timeLabel = if (lastSyncTime > 0L) {
                    SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(lastSyncTime))
                } else {
                    "Today"
                },
                icon = Icons.Default.Check,
                iconTint = KwGreenDark,
                iconBg = KwGreenSoft,
                unreadDotColor = Color.Transparent
            )
        )
    }

    return items
}

// ============================================================================
// PAGE 8: NOTIFICATIONS SUB-SCREEN
// ============================================================================
@Composable
fun NotificationsSubScreen(
    customers: List<Customer>,
    products: List<Product>,
    sales: List<SaleEntry>,
    settings: AppSettings?,
    syncState: FirestoreSyncState,
    extraPrefs: SettingsExtraPrefsStore,
    onBack: () -> Unit,
    onOpenRemindersSettings: () -> Unit,
    onNavigateToCollections: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onBackupNow: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    var selectedCategory by remember { mutableStateOf(NotificationCategory.ALL) }
    var refreshTrigger by remember { mutableStateOf(0) }

    val sendCustomerReminder: (Customer) -> Unit = { cust ->
        val shopName = settings?.shopName?.ifBlank { "KhataWise Store" } ?: "KhataWise Store"
        val upi = settings?.shopUpiId?.ifBlank { "shop@upi" } ?: "shop@upi"
        val template = extraPrefs.getTemplateBody("overdue_notice", if (settings?.language == "hi") "hi" else "en")
        val rendered = template
            .replace("{name}", cust.name)
            .replace("{amount}", CurrencyUtils.formatInr(cust.totalUdhari).removePrefix("₹"))
            .replace(
                "{date}",
                cust.promiseDate?.let { SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(it)) } ?: "today"
            )
            .replace("{shop}", shopName)
            .replace("{upi}", upi)
            .replace("{balance}", CurrencyUtils.formatInr(cust.totalUdhari).removePrefix("₹"))

        try {
            val phoneDigits = cust.phone.filter { it.isDigit() }
            val uri = if (extraPrefs.reminderChannel == "SMS") {
                Uri.parse("smsto:$phoneDigits").buildUpon().appendQueryParameter("body", rendered).build()
            } else {
                val formattedPhone = if (phoneDigits.length == 10) "91$phoneDigits" else phoneDigits
                Uri.parse("https://wa.me/$formattedPhone?text=${Uri.encode(rendered)}")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, rendered, Toast.LENGTH_LONG).show()
        }
    }

    val allNotifications = remember(customers, products, sales, settings, syncState, refreshTrigger) {
        buildLiveShopNotifications(
            customers = customers,
            products = products,
            sales = sales,
            settings = settings,
            syncState = syncState,
            extraPrefs = extraPrefs,
            onSendCustomerReminder = sendCustomerReminder,
            onNavigateToCollections = onNavigateToCollections,
            onNavigateToInventory = onNavigateToInventory,
            onDismissNotification = { id ->
                extraPrefs.dismissedNotificationIds = extraPrefs.dismissedNotificationIds + id
                refreshTrigger++
            },
            onBackupNow = {
                extraPrefs.lastDriveBackupMillis = System.currentTimeMillis()
                onBackupNow()
                refreshTrigger++
            }
        )
    }

    val readIds = remember(refreshTrigger) { extraPrefs.readNotificationIds }

    val dueCount = allNotifications.count { it.category == NotificationCategory.DUE }
    val stockCount = allNotifications.count { it.category == NotificationCategory.STOCK }
    val syncCount = allNotifications.count { it.category == NotificationCategory.SYNC }

    val filteredNotifications = remember(allNotifications, selectedCategory) {
        if (selectedCategory == NotificationCategory.ALL) {
            allNotifications
        } else {
            allNotifications.filter { it.category == selectedCategory }
        }
    }

    val todayList = filteredNotifications.filter { it.isToday }
    val earlierList = filteredNotifications.filter { !it.isToday }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KwPageBg)
            .testTag("notifications_subscreen")
    ) {
        SettingsSubScreenHeader(
            title = tr("Notifications", "सूचनाएं"),
            subtitle = tr("Reminders and alerts", "रिमाइंडर और अलर्ट"),
            onBack = onBack,
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = tr("Mark all read", "सभी पढ़े चिह्नित करें"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = KwGreenDark,
                        modifier = Modifier
                            .clickable {
                                extraPrefs.readNotificationIds = allNotifications.map { it.id }.toSet()
                                refreshTrigger++
                            }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                            .testTag("mark_all_read_btn")
                    )

                    Surface(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOpenRemindersSettings() }
                            .testTag("open_reminders_settings_btn"),
                        shape = RoundedCornerShape(10.dp),
                        color = KwPageBg
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = tr("Reminders Settings", "रिमाइंडर सेटिंग्स"),
                                tint = KwTextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        )

        // Filter Chips Row: All | Due | Stock | Sync
        Surface(
            color = KwCardBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NotificationFilterTabChip(
                        label = tr("All", "सभी"),
                        count = allNotifications.size,
                        selected = selectedCategory == NotificationCategory.ALL,
                        onClick = { selectedCategory = NotificationCategory.ALL }
                    )
                    NotificationFilterTabChip(
                        label = tr("Due", "बकाया"),
                        count = dueCount,
                        selected = selectedCategory == NotificationCategory.DUE,
                        onClick = { selectedCategory = NotificationCategory.DUE }
                    )
                    NotificationFilterTabChip(
                        label = tr("Stock", "स्टॉक"),
                        count = stockCount,
                        selected = selectedCategory == NotificationCategory.STOCK,
                        onClick = { selectedCategory = NotificationCategory.STOCK }
                    )
                    NotificationFilterTabChip(
                        label = tr("Sync", "सिंक"),
                        count = syncCount,
                        selected = selectedCategory == NotificationCategory.SYNC,
                        onClick = { selectedCategory = NotificationCategory.SYNC }
                    )
                }
                HorizontalDivider(color = KwBorder, thickness = 1.dp)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (filteredNotifications.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = KwCardBg),
                    border = BorderStroke(1.dp, KwBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.size(54.dp),
                            shape = CircleShape,
                            color = KwGreenSoft
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsNone,
                                    contentDescription = null,
                                    tint = KwGreenDark,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = tr("All caught up!", "सब अपडेटेड है!"),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = KwTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tr(
                                "No pending alerts in this category right now.",
                                "इस श्रेणी में अभी कोई लंबित अलर्ट नहीं है।"
                            ),
                            fontSize = 13.sp,
                            color = KwTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                if (todayList.isNotEmpty()) {
                    FieldSectionLabel(text = tr("TODAY", "आज"))
                    todayList.forEach { item ->
                        NotificationCardRow(
                            item = item,
                            isRead = item.id in readIds
                        )
                    }
                }

                if (earlierList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    FieldSectionLabel(text = tr("EARLIER", "पहले"))
                    earlierList.forEach { item ->
                        NotificationCardRow(
                            item = item,
                            isRead = item.id in readIds
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationFilterTabChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = if (selected) KwNavy else KwPageBg,
        border = BorderStroke(1.dp, if (selected) KwNavy else KwBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else KwTextPrimary
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (selected) Color.White.copy(alpha = 0.2f) else KwCardBg
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else KwTextSecondary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun NotificationCardRow(
    item: ShopNotificationItem,
    isRead: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KwCardBg),
        border = BorderStroke(1.dp, KwBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = item.iconBg
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = item.iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.title,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = KwTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isRead && item.unreadDotColor != Color.Transparent) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(item.unreadDotColor)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = item.body,
                    fontSize = 13.sp,
                    color = KwTextSecondary,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.timeLabel,
                    fontSize = 11.5.sp,
                    color = KwTextMuted
                )

                if (item.primaryActionLabel != null || item.secondaryActionLabel != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.primaryActionLabel != null && item.onPrimaryAction != null) {
                            Button(
                                onClick = item.onPrimaryAction,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = KwGreen),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 14.dp,
                                    vertical = 6.dp
                                ),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    text = item.primaryActionLabel,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        if (item.secondaryActionLabel != null && item.onSecondaryAction != null) {
                            OutlinedButton(
                                onClick = item.onSecondaryAction,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, KwBorder),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 14.dp,
                                    vertical = 6.dp
                                ),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    text = item.secondaryActionLabel,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KwTextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// PAGE 9: REMINDERS (WHATSAPP & SMS) SUB-SCREEN
// ============================================================================
@Composable
fun RemindersSubScreen(
    extraPrefs: SettingsExtraPrefsStore,
    currentLanguageCode: String,
    onBack: () -> Unit,
    onEditTemplate: (MessageTemplateConfig) -> Unit
) {
    BackHandler { onBack() }

    var channel by remember { mutableStateOf(extraPrefs.reminderChannel) }
    var rem1DayBefore by remember { mutableStateOf(extraPrefs.rem1DayBeforePromise) }
    var remOnPromise by remember { mutableStateOf(extraPrefs.remOnPromiseDate) }
    var remOverdue by remember { mutableStateOf(extraPrefs.remWhenOverdue) }
    var remThankYou by remember { mutableStateOf(extraPrefs.remThankYouAfterPayment) }
    var alertTime by remember { mutableStateOf(extraPrefs.reminderAlertTime) }
    var quietHours by remember { mutableStateOf(extraPrefs.reminderQuietHours) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KwPageBg)
            .testTag("reminders_subscreen")
    ) {
        SettingsSubScreenHeader(
            title = tr("Reminders", "रिमाइंडर"),
            subtitle = tr("WhatsApp and SMS", "WhatsApp और SMS"),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Segmented Toggle: WhatsApp | SMS
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = KwCardBg,
                border = BorderStroke(1.dp, KwBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("WhatsApp", "SMS").forEach { option ->
                        val selected = channel == option
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .clickable {
                                    channel = option
                                    extraPrefs.reminderChannel = option
                                },
                            shape = RoundedCornerShape(9.dp),
                            color = if (selected) KwNavy else Color.Transparent
                        ) {
                            Text(
                                text = option,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else KwTextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 9.dp)
                            )
                        }
                    }
                }
            }

            // Info Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFECFDF5),
                border = BorderStroke(1.dp, Color(0xFFA7F3D0))
            ) {
                Text(
                    text = if (channel == "WhatsApp") {
                        tr(
                            "Messages open in your WhatsApp. You tap Send. Nothing goes out without you.",
                            "मैसेज आपके WhatsApp में खुलते हैं। आप स्वयं Send दबाते हैं। आपकी अनुमति के बिना कुछ नहीं जाता।"
                        )
                    } else {
                        tr(
                            "Messages open in your SMS app. You tap Send. Nothing goes out without you.",
                            "मैसेज आपके SMS ऐप में खुलते हैं। आप स्वयं Send दबाते हैं।"
                        )
                    },
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF065F46),
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }

            // AUTO REMINDERS FOR UDHARI
            Column {
                FieldSectionLabel(text = tr("AUTO REMINDERS FOR UDHARI", "उधारी के लिए ऑटो रिमाइंडर"))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KwCardBg),
                    border = BorderStroke(1.dp, KwBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ReminderSwitchRow(
                            title = tr("1 day before promise date", "वादे की तारीख से 1 दिन पहले"),
                            subtitle = tr("Reminder is ready for you to send", "भेजने के लिए रिमाइंडर तैयार रहता है"),
                            checked = rem1DayBefore,
                            onCheckedChange = {
                                rem1DayBefore = it
                                extraPrefs.rem1DayBeforePromise = it
                            }
                        )
                        HorizontalDivider(color = KwBorder, modifier = Modifier.padding(horizontal = 14.dp))
                        ReminderSwitchRow(
                            title = tr("On the promise date", "वादे की तारीख वाले दिन"),
                            subtitle = tr("Morning alert", "सुबह का अलर्ट"),
                            checked = remOnPromise,
                            onCheckedChange = {
                                remOnPromise = it
                                extraPrefs.remOnPromiseDate = it
                            }
                        )
                        HorizontalDivider(color = KwBorder, modifier = Modifier.padding(horizontal = 14.dp))
                        ReminderSwitchRow(
                            title = tr("When overdue", "ओवरड्यू होने पर"),
                            subtitle = tr("Every 3 days until paid", "भुगतान होने तक हर 3 दिन में"),
                            checked = remOverdue,
                            onCheckedChange = {
                                remOverdue = it
                                extraPrefs.remWhenOverdue = it
                            }
                        )
                        HorizontalDivider(color = KwBorder, modifier = Modifier.padding(horizontal = 14.dp))
                        ReminderSwitchRow(
                            title = tr("Thank you after payment", "भुगतान के बाद धन्यवाद संदेश"),
                            subtitle = tr("Receipt message after a repayment", "भुगतान मिलने के बाद रसीद संदेश"),
                            checked = remThankYou,
                            onCheckedChange = {
                                remThankYou = it
                                extraPrefs.remThankYouAfterPayment = it
                            }
                        )
                    }
                }
            }

            // ALERT TIME
            Column {
                FieldSectionLabel(text = tr("ALERT TIME", "अलर्ट का समय"))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KwCardBg),
                    border = BorderStroke(1.dp, KwBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("8 AM", "10 AM", "6 PM").forEach { slot ->
                                val selected = alertTime == slot
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            alertTime = slot
                                            extraPrefs.reminderAlertTime = slot
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) KwNavy else KwPageBg
                                ) {
                                    Text(
                                        text = slot,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.White else KwTextPrimary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 9.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = KwBorder)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr("Quiet hours", "शांत घंटे (Quiet hours)"),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KwTextPrimary
                                )
                                Text(
                                    text = tr("No alerts 9 PM – 8 AM", "रात 9 बजे से सुबह 8 बजे तक कोई अलर्ट नहीं"),
                                    fontSize = 12.sp,
                                    color = KwTextSecondary
                                )
                            }
                            Switch(
                                checked = quietHours,
                                onCheckedChange = {
                                    quietHours = it
                                    extraPrefs.reminderQuietHours = it
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = KwGreen
                                )
                            )
                        }
                    }
                }
            }

            // MESSAGE TEMPLATES
            Column {
                FieldSectionLabel(text = tr("MESSAGE TEMPLATES", "मैसेज टेम्पलेट"))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KwCardBg),
                    border = BorderStroke(1.dp, KwBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        DEFAULT_MESSAGE_TEMPLATES.forEachIndexed { idx, tpl ->
                            if (idx > 0) {
                                HorizontalDivider(
                                    color = KwBorder,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                            }
                            val bodyPreview = extraPrefs.getTemplateBody(tpl.id, currentLanguageCode)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEditTemplate(tpl) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                                    .testTag("template_row_${tpl.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tr(tpl.titleEn, tpl.titleHi),
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = KwTextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = bodyPreview,
                                        fontSize = 12.sp,
                                        color = KwTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = tr("Edit", "बदलें"),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KwGreenDark
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = tr(
                    "SMS uses your SIM. Your carrier may charge for messages.",
                    "SMS आपके SIM का उपयोग करता है। आपका ऑपरेटर संदेशों के लिए शुल्क ले सकता है।"
                ),
                fontSize = 12.sp,
                color = KwTextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun ReminderSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = KwTextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = KwTextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = KwGreen
            )
        )
    }
}

// ============================================================================
// PAGE 10: EDIT TEMPLATE SUB-SCREEN
// ============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditMessageTemplateSubScreen(
    template: MessageTemplateConfig,
    settings: AppSettings?,
    extraPrefs: SettingsExtraPrefsStore,
    onBack: () -> Unit,
    onTemplateSaved: () -> Unit
) {
    BackHandler { onBack() }

    var editingLang by remember { mutableStateOf("en") }
    var messageBody by remember(template.id, editingLang) {
        mutableStateOf(extraPrefs.getTemplateBody(template.id, editingLang))
    }

    val variables = listOf("{name}", "{amount}", "{date}", "{shop}", "{upi}", "{balance}")

    val shopNameForPreview = settings?.shopName?.ifBlank { "Khal Bhandaar 12" } ?: "Khal Bhandaar 12"
    val upiForPreview = settings?.shopUpiId?.ifBlank { "9999999999@okaxis" } ?: "9999999999@okaxis"

    val renderedSamplePreview = remember(messageBody, shopNameForPreview, upiForPreview) {
        messageBody
            .replace("{name}", "Ramesh")
            .replace("{amount}", "5,400")
            .replace("{date}", "20 Apr")
            .replace("{shop}", shopNameForPreview)
            .replace("{upi}", upiForPreview)
            .replace("{balance}", "5,400")
    }

    val charCount = renderedSamplePreview.length
    val smsSegments = ((charCount - 1).coerceAtLeast(0) / 160) + 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KwPageBg)
            .testTag("edit_template_subscreen")
    ) {
        SettingsSubScreenHeader(
            title = tr("Edit template", "टेम्पलेट बदलें"),
            subtitle = tr(template.titleEn, template.titleHi),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Segmented Language Toggle: English | हिंदी
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = KwCardBg,
                border = BorderStroke(1.dp, KwBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("en" to "English", "hi" to "हिंदी").forEach { (code, label) ->
                        val selected = editingLang == code
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .clickable {
                                    editingLang = code
                                    messageBody = extraPrefs.getTemplateBody(template.id, code)
                                },
                            shape = RoundedCornerShape(9.dp),
                            color = if (selected) KwNavy else Color.Transparent
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else KwTextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 9.dp)
                            )
                        }
                    }
                }
            }

            // MESSAGE Input Box
            Column {
                FieldSectionLabel(text = tr("MESSAGE", "संदेश"))
                OutlinedTextField(
                    value = messageBody,
                    onValueChange = { messageBody = it },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = KwCardBg,
                        unfocusedContainerColor = KwCardBg,
                        focusedBorderColor = KwGreen,
                        unfocusedBorderColor = KwGreen,
                        focusedTextColor = KwTextPrimary,
                        unfocusedTextColor = KwTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("template_message_input")
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = tr("Tap a field below to add it", "नीचे दिए गए फ़ील्ड को जोड़ने के लिए टैप करें"),
                        fontSize = 12.sp,
                        color = KwTextSecondary
                    )
                    Text(
                        text = "${messageBody.length} ${tr("characters", "अक्षर")}",
                        fontSize = 12.sp,
                        color = KwTextSecondary
                    )
                }
            }

            // Variable Chips: {name}, {amount}, {date}, {shop}, {upi}, {balance}
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                variables.forEach { token ->
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val needsSpace = messageBody.isNotEmpty() && !messageBody.endsWith(" ")
                                messageBody = if (needsSpace) "$messageBody $token" else "$messageBody$token"
                            }
                            .testTag("var_chip_$token"),
                        shape = RoundedCornerShape(8.dp),
                        color = KwGreenSoft
                    ) {
                        Text(
                            text = token,
                            fontSize = 12.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = KwGreenDark,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // PREVIEW WITH SAMPLE DATA (WhatsApp style bubble)
            Column {
                FieldSectionLabel(text = tr("PREVIEW WITH SAMPLE DATA", "सैंपल डेटा के साथ प्रीव्यू"))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFE7F8E9)
                ) {
                    Box(modifier = Modifier.padding(14.dp)) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = KwCardBg,
                            shadowElevation = 1.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = renderedSamplePreview,
                                    fontSize = 13.5.sp,
                                    color = KwTextPrimary,
                                    lineHeight = 19.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "2:30 PM",
                                    fontSize = 10.5.sp,
                                    color = KwTextMuted,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }
                }
            }

            // As SMS Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = KwCardBg),
                border = BorderStroke(1.dp, KwBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = tr("As SMS", "SMS के रूप में"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = KwTextPrimary
                        )
                        Text(
                            text = "$charCount of 160 characters · $smsSegments ${if (smsSegments == 1) "message" else "messages"}",
                            fontSize = 12.sp,
                            color = KwTextSecondary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (smsSegments == 1) KwGreenSoft else KwAmberSoft
                    ) {
                        Text(
                            text = if (smsSegments == 1) "OK" else "$smsSegments SMS",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (smsSegments == 1) KwGreenDark else KwAmber,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Reset to default
            Text(
                text = tr("Reset to default", "डिफ़ॉल्ट पर रीसेट करें"),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = KwTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        messageBody = extraPrefs.resetTemplateBody(template.id, editingLang)
                    }
                    .padding(vertical = 8.dp)
                    .testTag("reset_template_default_btn")
            )
        }

        // Bottom Bar: Cancel | Save template
        Surface(
            color = KwCardBg,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, KwBorder),
                    modifier = Modifier
                        .weight(0.4f)
                        .height(50.dp)
                ) {
                    Text(
                        text = tr("Cancel", "रद्द करें"),
                        fontWeight = FontWeight.Bold,
                        color = KwTextPrimary
                    )
                }

                Button(
                    onClick = {
                        extraPrefs.setTemplateBody(template.id, editingLang, messageBody.trim())
                        onTemplateSaved()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KwGreen),
                    modifier = Modifier
                        .weight(0.6f)
                        .height(50.dp)
                        .testTag("save_template_btn")
                ) {
                    Text(
                        text = tr("Save template", "टेम्पलेट सेव करें"),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
