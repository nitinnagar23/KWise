package com.example.ui.settings

import com.example.ui.theme.kw
import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.model.AppSettings
import com.example.model.User
import com.example.sync.FirestoreSyncState
import com.example.util.tr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SettingsSubScreen {
    MAIN,
    GLOBAL_SEARCH,
    STORE_PROFILE,
    UPI_GST,
    BILL_PDF,
    SYNC_BACKUP,
    THEME_LANGUAGE,
    APP_LOCK_SECURITY,
    NOTIFICATIONS,
    REMINDERS,
    EDIT_TEMPLATE
}

data class MessageTemplateConfig(
    val id: String,
    val titleEn: String,
    val titleHi: String,
    val defaultBodyEn: String,
    val defaultBodyHi: String
)

val DEFAULT_MESSAGE_TEMPLATES = listOf(
    MessageTemplateConfig(
        id = "due_reminder",
        titleEn = "Due reminder",
        titleHi = "बकाया रिमाइंडर",
        defaultBodyEn = "Namaste {name} ji, ₹{amount} is due on {date} at {shop}. Pay by UPI: {upi}",
        defaultBodyHi = "नमस्ते {name} जी, {shop} पर {date} को ₹{amount} देय है। UPI से भुगतान करें: {upi}"
    ),
    MessageTemplateConfig(
        id = "overdue_notice",
        titleEn = "Overdue notice",
        titleHi = "ओवरड्यू सूचना",
        defaultBodyEn = "Namaste {name} ji, ₹{amount} is overdue at {shop}. Please clear your pending balance ₹{balance} via UPI: {upi}",
        defaultBodyHi = "नमस्ते {name} जी, {shop} पर ₹{amount} ओवरड्यू है। कृपया बकाया ₹{balance} का भुगतान करें: {upi}"
    ),
    MessageTemplateConfig(
        id = "payment_received",
        titleEn = "Payment received",
        titleHi = "भुगतान प्राप्त",
        defaultBodyEn = "Thank you {name} ji. Received ₹{amount} at {shop}. Remaining balance: ₹{balance}.",
        defaultBodyHi = "धन्यवाद {name} जी। {shop} पर ₹{amount} प्राप्त हुए। शेष बकाया: ₹{balance}।"
    ),
    MessageTemplateConfig(
        id = "bill_share",
        titleEn = "Bill share",
        titleHi = "बिल शेयर",
        defaultBodyEn = "Bill from {shop} for ₹{amount} on {date}. Current balance: ₹{balance}. Pay via UPI: {upi}",
        defaultBodyHi = "{shop} से {date} का ₹{amount} का बिल। कुल बकाया: ₹{balance}। UPI: {upi}"
    ),
    MessageTemplateConfig(
        id = "statement",
        titleEn = "Statement",
        titleHi = "खाता विवरण",
        defaultBodyEn = "Khata statement from {shop} for {name}. Current balance: ₹{balance}. Pay by UPI: {upi}",
        defaultBodyHi = "{shop} से {name} का खाता विवरण। वर्तमान बकाया: ₹{balance}। UPI: {upi}"
    )
)

data class BackupHistoryEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val timestampMillis: Long,
    val sizeLabel: String,
    val statusDotColorHex: Long
)

class SettingsExtraPrefsStore(context: Context) {
    private val prefs = context.getSharedPreferences("khatawise_settings_extra_prefs", Context.MODE_PRIVATE)

    var shopType: String
        get() = prefs.getString("shop_type", "Feed & grain") ?: "Feed & grain"
        set(value) = prefs.edit().putString("shop_type", value).apply()

    var villageCity: String
        get() = prefs.getString("village_city", "") ?: ""
        set(value) = prefs.edit().putString("village_city", value).apply()

    var extraPhonesCsv: String
        get() = prefs.getString("extra_phones_csv", "") ?: ""
        set(value) = prefs.edit().putString("extra_phones_csv", value).apply()

    var shopHoursOpen: String
        get() = prefs.getString("shop_hours_open", "8:00 AM") ?: "8:00 AM"
        set(value) = prefs.edit().putString("shop_hours_open", value).apply()

    var shopHoursClose: String
        get() = prefs.getString("shop_hours_close", "8:00 PM") ?: "8:00 PM"
        set(value) = prefs.edit().putString("shop_hours_close", value).apply()

    var customLogoUri: String
        get() = prefs.getString("custom_logo_uri", "") ?: ""
        set(value) = prefs.edit().putString("custom_logo_uri", value).apply()

    // UPI & GST
    var showPayNowQrOnBills: Boolean
        get() = prefs.getBoolean("show_pay_now_qr_on_bills", true)
        set(value) = prefs.edit().putBoolean("show_pay_now_qr_on_bills", value).apply()

    var extraUpiIdsCsv: String
        get() = prefs.getString("extra_upi_ids_csv", "") ?: ""
        set(value) = prefs.edit().putString("extra_upi_ids_csv", value).apply()

    var hasGstin: Boolean
        get() = prefs.getBoolean("has_gstin", true)
        set(value) = prefs.edit().putBoolean("has_gstin", value).apply()

    var legalName: String
        get() = prefs.getString("legal_name", "") ?: ""
        set(value) = prefs.edit().putString("legal_name", value).apply()

    var gstState: String
        get() = prefs.getString("gst_state", "Rajasthan") ?: "Rajasthan"
        set(value) = prefs.edit().putString("gst_state", value).apply()

    var defaultHsn: String
        get() = prefs.getString("default_hsn", "2309") ?: "2309"
        set(value) = prefs.edit().putString("default_hsn", value).apply()

    var gstRegistrationType: String
        get() = prefs.getString("gst_registration_type", "Regular") ?: "Regular"
        set(value) = prefs.edit().putString("gst_registration_type", value).apply()

    var pricesIncludeGst: Boolean
        get() = prefs.getBoolean("prices_include_gst", true)
        set(value) = prefs.edit().putBoolean("prices_include_gst", value).apply()

    var showGstSplitOnBills: Boolean
        get() = prefs.getBoolean("show_gst_split_on_bills", true)
        set(value) = prefs.edit().putBoolean("show_gst_split_on_bills", value).apply()

    // Bill & PDF
    var billPaperSize: String
        get() = prefs.getString("bill_paper_size", "A5") ?: "A5"
        set(value) = prefs.edit().putString("bill_paper_size", value).apply()

    var billLanguage: String
        get() = prefs.getString("bill_language", "English") ?: "English"
        set(value) = prefs.edit().putString("bill_language", value).apply()

    var billPrefix: String
        get() = prefs.getString("bill_prefix", "INV-") ?: "INV-"
        set(value) = prefs.edit().putString("bill_prefix", value).apply()

    var billShowLogo: Boolean
        get() = prefs.getBoolean("bill_show_logo", true)
        set(value) = prefs.edit().putBoolean("bill_show_logo", value).apply()

    var billShowShopAddressPhone: Boolean
        get() = prefs.getBoolean("bill_show_shop_address_phone", true)
        set(value) = prefs.edit().putBoolean("bill_show_shop_address_phone", value).apply()

    var billShowGstin: Boolean
        get() = prefs.getBoolean("bill_show_gstin", true)
        set(value) = prefs.edit().putBoolean("bill_show_gstin", value).apply()

    var billShowShopHours: Boolean
        get() = prefs.getBoolean("bill_show_shop_hours", true)
        set(value) = prefs.edit().putBoolean("bill_show_shop_hours", value).apply()

    var billShowCustomerDetails: Boolean
        get() = prefs.getBoolean("bill_show_customer_details", true)
        set(value) = prefs.edit().putBoolean("bill_show_customer_details", value).apply()

    var billShowItemHsnGst: Boolean
        get() = prefs.getBoolean("bill_show_item_hsn_gst", true)
        set(value) = prefs.edit().putBoolean("bill_show_item_hsn_gst", value).apply()

    var billShowWeightBreakdown: Boolean
        get() = prefs.getBoolean("bill_show_weight_breakdown", true)
        set(value) = prefs.edit().putBoolean("bill_show_weight_breakdown", value).apply()

    var billShowRateDiscount: Boolean
        get() = prefs.getBoolean("bill_show_rate_discount", true)
        set(value) = prefs.edit().putBoolean("bill_show_rate_discount", value).apply()

    var billShowUdhariSummary: Boolean
        get() = prefs.getBoolean("bill_show_udhari_summary", true)
        set(value) = prefs.edit().putBoolean("bill_show_udhari_summary", value).apply()

    var billShowPaymentDetails: Boolean
        get() = prefs.getBoolean("bill_show_payment_details", true)
        set(value) = prefs.edit().putBoolean("bill_show_payment_details", value).apply()

    var billShowPromiseDate: Boolean
        get() = prefs.getBoolean("bill_show_promise_date", true)
        set(value) = prefs.edit().putBoolean("bill_show_promise_date", value).apply()

    var billShowSignatureFooter: Boolean
        get() = prefs.getBoolean("bill_show_signature_footer", true)
        set(value) = prefs.edit().putBoolean("bill_show_signature_footer", value).apply()

    // Sync / Backup
    var autoSync: Boolean
        get() = prefs.getBoolean("auto_sync", true)
        set(value) = prefs.edit().putBoolean("auto_sync", value).apply()

    var syncWifiOnly: Boolean
        get() = prefs.getBoolean("sync_wifi_only", false)
        set(value) = prefs.edit().putBoolean("sync_wifi_only", value).apply()

    var driveBackupFrequency: String
        get() = prefs.getString("drive_backup_freq", "Daily") ?: "Daily"
        set(value) = prefs.edit().putString("drive_backup_freq", value).apply()

    var driveBackupWifiOnly: Boolean
        get() = prefs.getBoolean("drive_backup_wifi_only", true)
        set(value) = prefs.edit().putBoolean("drive_backup_wifi_only", value).apply()

    var lastDriveBackupMillis: Long
        get() = prefs.getLong("last_drive_backup_millis", 0L)
        set(value) = prefs.edit().putLong("last_drive_backup_millis", value).apply()

    // Theme & Display
    var textSizeMode: String
        get() = prefs.getString("text_size_mode", "Normal") ?: "Normal"
        set(value) = prefs.edit().putString("text_size_mode", value).apply()

    var indianNumberFormat: Boolean
        get() = prefs.getBoolean("indian_number_format", true)
        set(value) = prefs.edit().putBoolean("indian_number_format", value).apply()

    // App lock & security
    var lockAppEnabled: Boolean
        get() = prefs.getBoolean("lock_app_enabled", true)
        set(value) = prefs.edit().putBoolean("lock_app_enabled", value).apply()

    var unlockFingerprint: Boolean
        get() = prefs.getBoolean("unlock_fingerprint", true)
        set(value) = prefs.edit().putBoolean("unlock_fingerprint", value).apply()

    var appLockPin: String
        get() = prefs.getString("app_lock_pin_hash", "") ?: ""
        set(value) {
            val hashed = when {
                value.isBlank() -> ""
                value.startsWith("PBKDF2$", ignoreCase = true) -> value
                else -> com.example.util.SecurityUtils.hashPin(value)
            }
            prefs.edit().remove("app_lock_pin").putString("app_lock_pin_hash", hashed).apply()
        }

    var autoLockAfter: String
        get() = prefs.getString("auto_lock_after", "1 minute") ?: "1 minute"
        set(value) = prefs.edit().putString("auto_lock_after", value).apply()

    var askPinForReportsCashbook: Boolean
        get() = prefs.getBoolean("ask_pin_reports_cashbook", true)
        set(value) = prefs.edit().putBoolean("ask_pin_reports_cashbook", value).apply()

    var adminPinForDeletesReturns: Boolean
        get() = prefs.getBoolean("admin_pin_deletes_returns", true)
        set(value) = prefs.edit().putBoolean("admin_pin_deletes_returns", value).apply()

    var blockScreenshots: Boolean
        get() = prefs.getBoolean("block_screenshots", false)
        set(value) = prefs.edit().putBoolean("block_screenshots", value).apply()

    // Notifications & Reminders
    var dismissedNotificationIds: Set<String>
        get() = prefs.getStringSet("dismissed_notif_ids", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("dismissed_notif_ids", value).apply()

    var readNotificationIds: Set<String>
        get() = prefs.getStringSet("read_notif_ids", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("read_notif_ids", value).apply()

    var reminderChannel: String
        get() = prefs.getString("reminder_channel", "WhatsApp") ?: "WhatsApp"
        set(value) = prefs.edit().putString("reminder_channel", value).apply()

    var rem1DayBeforePromise: Boolean
        get() = prefs.getBoolean("rem_1day_before", true)
        set(value) = prefs.edit().putBoolean("rem_1day_before", value).apply()

    var remOnPromiseDate: Boolean
        get() = prefs.getBoolean("rem_on_promise", true)
        set(value) = prefs.edit().putBoolean("rem_on_promise", value).apply()

    var remWhenOverdue: Boolean
        get() = prefs.getBoolean("rem_when_overdue", false)
        set(value) = prefs.edit().putBoolean("rem_when_overdue", value).apply()

    var remThankYouAfterPayment: Boolean
        get() = prefs.getBoolean("rem_thank_you", true)
        set(value) = prefs.edit().putBoolean("rem_thank_you", value).apply()

    var reminderAlertTime: String
        get() = prefs.getString("reminder_alert_time", "10 AM") ?: "10 AM"
        set(value) = prefs.edit().putString("reminder_alert_time", value).apply()

    var reminderQuietHours: Boolean
        get() = prefs.getBoolean("reminder_quiet_hours", true)
        set(value) = prefs.edit().putBoolean("reminder_quiet_hours", value).apply()

    fun getTemplateBody(templateId: String, lang: String): String {
        val def = DEFAULT_MESSAGE_TEMPLATES.find { it.id == templateId }
        val fallback = if (lang == "hi") def?.defaultBodyHi.orEmpty() else def?.defaultBodyEn.orEmpty()
        return prefs.getString("tpl_${templateId}_$lang", null) ?: fallback
    }

    fun setTemplateBody(templateId: String, lang: String, body: String) {
        prefs.edit().putString("tpl_${templateId}_$lang", body).apply()
    }

    fun resetTemplateBody(templateId: String, lang: String): String {
        prefs.edit().remove("tpl_${templateId}_$lang").apply()
        return getTemplateBody(templateId, lang)
    }
}

// Shared design tokens matching the PDF's crisp warm off-white surface & forest green palette
internal val KwPageBg: Color get() = if (com.example.ui.theme.KwThemeState.isDark) Color(0xFF0F1720) else Color(0xFFF6F5F0)
internal val KwCardBg: Color get() = kw(0xFFFFFFFF)
internal val KwBorder: Color get() = kw(0xFFE5E4DC)
internal val KwTextPrimary: Color get() = kw(0xFF111827)
internal val KwTextSecondary: Color get() = kw(0xFF6B7280)
internal val KwTextMuted: Color get() = kw(0xFF9CA3AF)
internal val KwGreen: Color get() = kw(0xFF16A34A)
internal val KwGreenDark: Color get() = kw(0xFF15803D)
internal val KwGreenSoft: Color get() = kw(0xFFDCFCE7)
internal val KwAmber: Color get() = kw(0xFFD97706)
internal val KwAmberSoft: Color get() = kw(0xFFFEF3C7)
internal val KwRed: Color get() = kw(0xFFDC2626)
internal val KwRedSoft: Color get() = kw(0xFFFEE2E2)
internal val KwNavy: Color get() = if (com.example.ui.theme.KwThemeState.isDark) Color(0xFF14305E) else Color(0xFF0F172A)
@Composable
internal fun SettingsSubScreenHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Surface(
        color = KwCardBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onBack() }
                            .testTag("subscreen_back_btn"),
                        shape = RoundedCornerShape(12.dp),
                        color = KwPageBg
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = tr("Back", "वापस"),
                                tint = KwTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = KwTextPrimary
                        )
                        Text(
                            text = subtitle,
                            fontSize = 12.5.sp,
                            color = KwTextSecondary
                        )
                    }
                }

                if (trailingContent != null) {
                    trailingContent()
                }
            }
            HorizontalDivider(color = KwBorder, thickness = 1.dp)
        }
    }
}

@Composable
internal fun FieldSectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = KwTextSecondary,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

// ============================================================================
// PAGE 2: STORE PROFILE SUB-SCREEN
// ============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoreProfileSubScreen(
    settings: AppSettings?,
    currentUser: User?,
    extraPrefs: SettingsExtraPrefsStore,
    onBack: () -> Unit,
    onSaveStoreProfile: (
        shopName: String,
        shopType: String,
        ownerName: String,
        primaryPhone: String,
        extraPhones: List<String>,
        village: String,
        fullAddress: String,
        openHour: String,
        closeHour: String,
        logoUri: String
    ) -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    var shopName by remember(settings) {
        mutableStateOf(settings?.shopName?.ifBlank { "Shop Name" } ?: "Shop Name")
    }
    var shopType by remember { mutableStateOf(extraPrefs.shopType) }
    var ownerName by remember(currentUser) {
        mutableStateOf(currentUser?.name?.ifBlank { "Shop Owner" } ?: "Shop Owner")
    }
    var primaryPhone by remember(settings) {
        val raw = settings?.shopPhone.orEmpty().removePrefix("+91").trim()
        mutableStateOf(raw)
    }
    val extraPhones = remember {
        val list = extraPrefs.extraPhonesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        mutableStateListOf<String>().apply { addAll(list) }
    }
    var showAddPhoneDialog by remember { mutableStateOf(false) }
    var newPhoneInput by remember { mutableStateOf("") }

    var villageCity by remember(settings) {
        val savedVillage = extraPrefs.villageCity
        val fallback = settings?.shopAddress?.substringBefore(",")?.trim().orEmpty()
        mutableStateOf(savedVillage.ifBlank { fallback })
    }
    var fullAddress by remember(settings) {
        mutableStateOf(settings?.shopAddress.orEmpty())
    }
    var openHour by remember { mutableStateOf(extraPrefs.shopHoursOpen) }
    var closeHour by remember { mutableStateOf(extraPrefs.shopHoursClose) }
    var customLogoUri by remember { mutableStateOf(extraPrefs.customLogoUri) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            customLogoUri = uri.toString()
        }
    }

    val shopTypes = listOf(
        "Kirana / Grocery" to tr("Kirana / Grocery", "किराना / ग्रोसरी"),
        "Feed & grain" to tr("Feed & grain", "खल-चारा और अनाज"),
        "General store" to tr("General store", "जनरल स्टोर"),
        "Other" to tr("Other", "अन्य")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KwPageBg)
            .testTag("store_profile_subscreen")
    ) {
        SettingsSubScreenHeader(
            title = tr("Store profile", "दुकान प्रोफ़ाइल"),
            subtitle = tr("Shows on bills and PDFs", "बिल और PDF पर दिखता है"),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Shop logo Card
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(68.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = KwGreen
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.khatawise_logo),
                                contentDescription = "Shop Logo",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(60.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr("Shop logo", "दुकान का लोगो"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = KwTextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tr(
                                "Shown on bills, statements and QR pages",
                                "बिल, खाता विवरण और QR पेज पर दिखता है"
                            ),
                            fontSize = 12.sp,
                            color = KwTextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, KwGreen),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 14.dp,
                                    vertical = 6.dp
                                ),
                                modifier = Modifier.defaultMinSize(minHeight = 38.dp)
                            ) {
                                Text(
                                    text = tr("Change", "बदलें"),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KwGreenDark,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = tr("Remove", "हटाएं"),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = KwRed,
                                modifier = Modifier
                                    .clickable {
                                        customLogoUri = ""
                                        Toast.makeText(
                                            context,
                                            if (extraPrefs.billLanguage == "हिंदी") "लोगो रीसेट किया गया" else "Logo reset to default",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            )
                        }
                    }
                }
            }

            // SHOP NAME *
            Column {
                FieldSectionLabel(text = tr("SHOP NAME *", "दुकान का नाम *"))
                OutlinedTextField(
                    value = shopName,
                    onValueChange = { shopName = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = KwCardBg,
                        unfocusedContainerColor = KwCardBg,
                        focusedBorderColor = KwGreen,
                        unfocusedBorderColor = KwBorder,
                        focusedTextColor = KwTextPrimary,
                        unfocusedTextColor = KwTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("store_profile_shop_name")
                )
            }

            // SHOP TYPE
            Column {
                FieldSectionLabel(text = tr("SHOP TYPE", "दुकान का प्रकार"))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    shopTypes.forEach { (key, label) ->
                        val selected = shopType.equals(key, ignoreCase = true)
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { shopType = key },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) KwNavy else KwCardBg,
                            border = BorderStroke(1.dp, if (selected) KwNavy else KwBorder)
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else KwTextPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }

            // OWNER NAME (with "From Google" badge)
            Column {
                FieldSectionLabel(text = tr("OWNER NAME", "मालिक का नाम"))
                OutlinedTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = KwPageBg,
                            modifier = Modifier.padding(end = 10.dp)
                        ) {
                            Text(
                                text = tr("From Google", "Google से"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = KwTextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = KwCardBg,
                        unfocusedContainerColor = KwCardBg,
                        focusedBorderColor = KwGreen,
                        unfocusedBorderColor = KwBorder,
                        focusedTextColor = KwTextPrimary,
                        unfocusedTextColor = KwTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // SHOP MOBILE NUMBERS
            Column {
                FieldSectionLabel(text = tr("SHOP MOBILE NUMBERS", "दुकान के मोबाइल नंबर"))
                OutlinedTextField(
                    value = primaryPhone,
                    onValueChange = { primaryPhone = it.filter { ch -> ch.isDigit() || ch == ' ' }.take(12) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        Text(
                            text = "+91",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = KwTextSecondary,
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                        )
                    },
                    trailingIcon = {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = KwGreenSoft,
                            modifier = Modifier.padding(end = 10.dp)
                        ) {
                            Text(
                                text = tr("Primary", "मुख्य"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = KwGreenDark,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = KwCardBg,
                        unfocusedContainerColor = KwCardBg,
                        focusedBorderColor = KwGreen,
                        unfocusedBorderColor = KwBorder,
                        focusedTextColor = KwTextPrimary,
                        unfocusedTextColor = KwTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("store_profile_phone")
                )

                extraPhones.forEachIndexed { idx, ph ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = KwCardBg,
                        border = BorderStroke(1.dp, KwBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "+91 $ph",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = KwTextPrimary
                            )
                            Text(
                                text = tr("Remove", "हटाएं"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = KwRed,
                                modifier = Modifier.clickable { extraPhones.removeAt(idx) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                DashedGreenActionButton(
                    text = tr("+ Add another number", "+ एक और नंबर जोड़ें"),
                    onClick = {
                        newPhoneInput = ""
                        showAddPhoneDialog = true
                    }
                )
            }

            // VILLAGE / CITY
            Column {
                FieldSectionLabel(text = tr("VILLAGE / CITY", "गाँव / शहर"))
                OutlinedTextField(
                    value = villageCity,
                    onValueChange = { villageCity = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = KwCardBg,
                        unfocusedContainerColor = KwCardBg,
                        focusedBorderColor = KwGreen,
                        unfocusedBorderColor = KwBorder,
                        focusedTextColor = KwTextPrimary,
                        unfocusedTextColor = KwTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // FULL ADDRESS
            Column {
                FieldSectionLabel(text = tr("FULL ADDRESS", "पूरा पता"))
                OutlinedTextField(
                    value = fullAddress,
                    onValueChange = { fullAddress = it },
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = KwCardBg,
                        unfocusedContainerColor = KwCardBg,
                        focusedBorderColor = KwGreen,
                        unfocusedBorderColor = KwBorder,
                        focusedTextColor = KwTextPrimary,
                        unfocusedTextColor = KwTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("store_profile_address")
                )
            }

            // SHOP HOURS (SHOWN ON BILL FOOTER)
            Column {
                FieldSectionLabel(
                    text = tr("SHOP HOURS (SHOWN ON BILL FOOTER)", "दुकान का समय (बिल के नीचे दिखेगा)")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = openHour,
                        onValueChange = { openHour = it },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = KwCardBg,
                            unfocusedContainerColor = KwCardBg,
                            focusedBorderColor = KwGreen,
                            unfocusedBorderColor = KwBorder,
                            focusedTextColor = KwTextPrimary,
                            unfocusedTextColor = KwTextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = closeHour,
                        onValueChange = { closeHour = it },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = KwCardBg,
                            unfocusedContainerColor = KwCardBg,
                            focusedBorderColor = KwGreen,
                            unfocusedBorderColor = KwBorder,
                            focusedTextColor = KwTextPrimary,
                            unfocusedTextColor = KwTextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Bottom Sticky Bar: Cancel | Save changes
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
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(0.4f)
                        .height(50.dp)
                ) {
                    Text(
                        text = tr("Cancel", "रद्द करें"),
                        fontWeight = FontWeight.Bold,
                        color = KwTextPrimary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = {
                        onSaveStoreProfile(
                            shopName.trim().ifEmpty { "KhataWise Store" },
                            shopType,
                            ownerName.trim(),
                            primaryPhone.trim(),
                            extraPhones.toList(),
                            villageCity.trim(),
                            fullAddress.trim(),
                            openHour.trim(),
                            closeHour.trim(),
                            customLogoUri
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KwGreen),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(0.6f)
                        .height(50.dp)
                        .testTag("store_profile_save_btn")
                ) {
                    Text(
                        text = tr("Save changes", "बदलाव सेव करें"),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    if (showAddPhoneDialog) {
        AlertDialog(
            onDismissRequest = { showAddPhoneDialog = false },
            title = { Text(tr("Add mobile number", "मोबाइल नंबर जोड़ें"), fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPhoneInput,
                    onValueChange = { newPhoneInput = it.filter { ch -> ch.isDigit() }.take(10) },
                    label = { Text(tr("10-digit mobile number", "10 अंकों का मोबाइल नंबर")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPhoneInput.isNotBlank()) {
                            extraPhones.add(newPhoneInput.trim())
                            showAddPhoneDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KwGreen)
                ) {
                    Text(tr("Add", "जोड़ें"), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPhoneDialog = false }) {
                    Text(tr("Cancel", "रद्द करें"))
                }
            }
        )
    }
}

@Composable
private fun DashedGreenActionButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(kw(0xFFF0FDF4))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                color = KwGreen,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx(), 12.dp.toPx())
            )
        }
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = KwGreenDark
        )
    }
}

// ============================================================================
// PAGE 3: UPI & GST SUB-SCREEN
// ============================================================================
@Composable
fun UpiAndGstSubScreen(
    settings: AppSettings?,
    extraPrefs: SettingsExtraPrefsStore,
    onBack: () -> Unit,
    onSaveUpiAndGst: (
        upiId: String,
        showPayNowQr: Boolean,
        extraUpis: List<String>,
        hasGstin: Boolean,
        gstin: String,
        legalName: String,
        stateName: String,
        defaultHsn: String,
        registrationType: String,
        pricesIncludeGst: Boolean,
        showGstSplit: Boolean
    ) -> Unit
) {
    BackHandler { onBack() }

    var upiId by remember(settings) {
        mutableStateOf(settings?.shopUpiId.orEmpty())
    }
    var showPayNowQr by remember { mutableStateOf(extraPrefs.showPayNowQrOnBills) }
    val extraUpis = remember {
        val list = extraPrefs.extraUpiIdsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        mutableStateListOf<String>().apply { addAll(list) }
    }
    var showAddUpiDialog by remember { mutableStateOf(false) }
    var newUpiInput by remember { mutableStateOf("") }

    var hasGstin by remember(settings) {
        mutableStateOf(extraPrefs.hasGstin || !settings?.shopGstin.isNullOrBlank())
    }
    var gstin by remember(settings) {
        mutableStateOf(settings?.shopGstin.orEmpty())
    }
    var legalName by remember(settings) {
        val saved = extraPrefs.legalName
        val fallback = settings?.shopName.orEmpty()
        mutableStateOf(saved.ifBlank { fallback })
    }
    var gstState by remember { mutableStateOf(extraPrefs.gstState) }
    var showStateDropdown by remember { mutableStateOf(false) }
    var defaultHsn by remember { mutableStateOf(extraPrefs.defaultHsn) }
    var registrationType by remember { mutableStateOf(extraPrefs.gstRegistrationType) }
    var pricesIncludeGst by remember { mutableStateOf(extraPrefs.pricesIncludeGst) }
    var showGstSplit by remember { mutableStateOf(extraPrefs.showGstSplitOnBills) }

    val indianStates = listOf(
        "Rajasthan", "Gujarat", "Madhya Pradesh", "Uttar Pradesh",
        "Haryana", "Punjab", "Maharashtra", "Bihar", "Karnataka", "Delhi"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KwPageBg)
            .testTag("upi_gst_subscreen")
    ) {
        SettingsSubScreenHeader(
            title = tr("UPI & GST", "UPI और GST"),
            subtitle = tr("Payments and tax details", "भुगतान और टैक्स विवरण"),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // UPI Section
            Column {
                FieldSectionLabel(text = "UPI")
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
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Column {
                            FieldSectionLabel(text = tr("UPI ID", "UPI आईडी"))
                            OutlinedTextField(
                                value = upiId,
                                onValueChange = { upiId = it },
                                placeholder = { Text("yourname@okaxis", color = KwTextMuted) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                trailingIcon = {
                                    if (upiId.contains("@")) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = KwGreenSoft,
                                            modifier = Modifier.padding(end = 10.dp)
                                        ) {
                                            Text(
                                                text = tr("Verified", "सत्यापित"),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = KwGreenDark,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = KwCardBg,
                                    unfocusedContainerColor = KwCardBg,
                                    focusedBorderColor = KwGreen,
                                    unfocusedBorderColor = KwBorder,
                                    focusedTextColor = KwTextPrimary,
                                    unfocusedTextColor = KwTextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("upi_gst_upi_input")
                            )
                        }

                        // QR Preview Box
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = KwPageBg
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(56.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, KwBorder)
                                ) {
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp)
                                    ) {
                                        val s = size.width / 5f
                                        val color = Color(0xFF0F172A)
                                        // Top-left finder
                                        drawRect(color, Offset(0f, 0f), Size(s * 2f, s * 2f))
                                        drawRect(Color.White, Offset(s * 0.4f, s * 0.4f), Size(s * 1.2f, s * 1.2f))
                                        drawRect(color, Offset(s * 0.7f, s * 0.7f), Size(s * 0.6f, s * 0.6f))
                                        // Top-right finder
                                        drawRect(color, Offset(s * 3f, 0f), Size(s * 2f, s * 2f))
                                        drawRect(Color.White, Offset(s * 3.4f, s * 0.4f), Size(s * 1.2f, s * 1.2f))
                                        drawRect(color, Offset(s * 3.7f, s * 0.7f), Size(s * 0.6f, s * 0.6f))
                                        // Bottom-left finder
                                        drawRect(color, Offset(0f, s * 3f), Size(s * 2f, s * 2f))
                                        drawRect(Color.White, Offset(s * 0.4f, s * 3.4f), Size(s * 1.2f, s * 1.2f))
                                        drawRect(color, Offset(s * 0.7f, s * 3.7f), Size(s * 0.6f, s * 0.6f))
                                        // Bottom-right modules
                                        drawRect(color, Offset(s * 3f, s * 3f), Size(s * 0.8f, s * 0.8f))
                                        drawRect(color, Offset(s * 4.1f, s * 4.1f), Size(s * 0.9f, s * 0.9f))
                                        drawRect(color, Offset(s * 2.3f, s * 2.3f), Size(s * 0.6f, s * 0.6f))
                                    }
                                }

                                Text(
                                    text = tr(
                                        "This QR prints on every bill, statement and share image.",
                                        "यह QR हर बिल, खाता विवरण और शेयर इमेज पर छपता है।"
                                    ),
                                    fontSize = 12.5.sp,
                                    color = KwTextSecondary,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Show Pay now QR on bills switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr("Show Pay now QR on bills", "बिल पर 'Pay now' QR दिखाएं"),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KwTextPrimary
                                )
                                Text(
                                    text = tr("For bills with balance due", "बकाया राशि वाले बिलों के लिए"),
                                    fontSize = 12.sp,
                                    color = KwTextSecondary
                                )
                            }
                            Switch(
                                checked = showPayNowQr,
                                onCheckedChange = { showPayNowQr = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = KwGreen,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = kw(0xFFD1D5DB)
                                )
                            )
                        }

                        extraUpis.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = item,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = KwTextPrimary
                                )
                                Text(
                                    text = tr("Remove", "हटाएं"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KwRed,
                                    modifier = Modifier.clickable { extraUpis.removeAt(index) }
                                )
                            }
                        }

                        HorizontalDivider(color = KwBorder)

                        Text(
                            text = tr("+ Add another UPI ID", "+ एक और UPI ID जोड़ें"),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = KwGreenDark,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    newUpiInput = ""
                                    showAddUpiDialog = true
                                }
                                .padding(vertical = 4.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // GST Section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FieldSectionLabel(text = "GST")

                // Card 1: I have a GSTIN
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("I have a GSTIN", "मेरे पास GSTIN है"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = KwTextPrimary
                            )
                            Text(
                                text = tr("Adds GSTIN and HSN to bills", "बिल में GSTIN और HSN जोड़ता है"),
                                fontSize = 12.sp,
                                color = KwTextSecondary
                            )
                        }
                        Switch(
                            checked = hasGstin,
                            onCheckedChange = { hasGstin = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = KwGreen,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = kw(0xFFD1D5DB)
                            )
                        )
                    }
                }

                // Card 2: GSTIN Details
                if (hasGstin) {
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
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column {
                                FieldSectionLabel(text = "GSTIN")
                                OutlinedTextField(
                                    value = gstin,
                                    onValueChange = { gstin = it.uppercase().take(15) },
                                    placeholder = { Text("08ABCDE1234F1Z5", color = KwTextMuted) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = KwCardBg,
                                        unfocusedContainerColor = KwCardBg,
                                        focusedBorderColor = KwGreen,
                                        unfocusedBorderColor = KwBorder,
                                        focusedTextColor = KwTextPrimary,
                                        unfocusedTextColor = KwTextPrimary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("upi_gst_gstin_input")
                                )
                            }

                            Column {
                                FieldSectionLabel(text = tr("LEGAL NAME", "कानूनी नाम (Legal Name)"))
                                OutlinedTextField(
                                    value = legalName,
                                    onValueChange = { legalName = it },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = KwCardBg,
                                        unfocusedContainerColor = KwCardBg,
                                        focusedBorderColor = KwGreen,
                                        unfocusedBorderColor = KwBorder,
                                        focusedTextColor = KwTextPrimary,
                                        unfocusedTextColor = KwTextPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    FieldSectionLabel(text = tr("STATE", "राज्य"))
                                    Box {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(54.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { showStateDropdown = true },
                                            shape = RoundedCornerShape(12.dp),
                                            color = KwCardBg,
                                            border = BorderStroke(1.dp, KwBorder)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = gstState,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = KwTextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = null,
                                                    tint = KwTextSecondary
                                                )
                                            }
                                        }
                                        DropdownMenu(
                                            expanded = showStateDropdown,
                                            onDismissRequest = { showStateDropdown = false }
                                        ) {
                                            indianStates.forEach { st ->
                                                DropdownMenuItem(
                                                    text = { Text(st) },
                                                    onClick = {
                                                        gstState = st
                                                        showStateDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    FieldSectionLabel(text = tr("DEFAULT HSN", "डिफ़ॉल्ट HSN"))
                                    OutlinedTextField(
                                        value = defaultHsn,
                                        onValueChange = { defaultHsn = it.filter { ch -> ch.isDigit() }.take(8) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = KwCardBg,
                                            unfocusedContainerColor = KwCardBg,
                                            focusedBorderColor = KwGreen,
                                            unfocusedBorderColor = KwBorder,
                                            focusedTextColor = KwTextPrimary,
                                            unfocusedTextColor = KwTextPrimary
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            Column {
                                FieldSectionLabel(text = tr("REGISTRATION TYPE", "पंजीकरण प्रकार"))
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = KwPageBg
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf(
                                            "Regular" to tr("Regular", "रेगुलर"),
                                            "Composition" to tr("Composition", "कम्पोज़िशन")
                                        ).forEach { (key, label) ->
                                            val selected = registrationType == key
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(9.dp))
                                                    .clickable { registrationType = key },
                                                shape = RoundedCornerShape(9.dp),
                                                color = if (selected) KwCardBg else Color.Transparent,
                                                shadowElevation = if (selected) 1.dp else 0.dp
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                                    color = if (selected) KwTextPrimary else KwTextSecondary,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.padding(vertical = 9.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Card 3: Prices include GST & Show GST split on bills
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KwCardBg),
                    border = BorderStroke(1.dp, KwBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr("Prices include GST", "कीमतों में GST शामिल है"),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KwTextPrimary
                                )
                                Text(
                                    text = tr(
                                        "Off adds GST on top of the item price",
                                        "बंद करने पर आइटम कीमत के ऊपर GST जुड़ेगा"
                                    ),
                                    fontSize = 12.sp,
                                    color = KwTextSecondary
                                )
                            }
                            Switch(
                                checked = pricesIncludeGst,
                                onCheckedChange = { pricesIncludeGst = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = KwGreen,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = kw(0xFFD1D5DB)
                                )
                            )
                        }

                        HorizontalDivider(color = KwBorder, modifier = Modifier.padding(horizontal = 14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr("Show GST split on bills", "बिल पर GST विभाजन दिखाएं"),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KwTextPrimary
                                )
                                Text(
                                    text = tr("CGST and SGST lines", "CGST और SGST की अलग लाइनें"),
                                    fontSize = 12.sp,
                                    color = KwTextSecondary
                                )
                            }
                            Switch(
                                checked = showGstSplit,
                                onCheckedChange = { showGstSplit = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = KwGreen,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = kw(0xFFD1D5DB)
                                )
                            )
                        }
                    }
                }

                Text(
                    text = tr(
                        "Each item can have its own GST rate in Inventory.",
                        "इन्वेंट्री में हर आइटम की अपनी अलग GST दर हो सकती है।"
                    ),
                    fontSize = 12.sp,
                    color = KwTextSecondary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Sticky Bottom Bar
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
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(0.4f)
                        .height(50.dp)
                ) {
                    Text(
                        text = tr("Cancel", "रद्द करें"),
                        fontWeight = FontWeight.Bold,
                        color = KwTextPrimary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = {
                        onSaveUpiAndGst(
                            upiId.trim(),
                            showPayNowQr,
                            extraUpis.toList(),
                            hasGstin,
                            if (hasGstin) gstin.trim() else "",
                            legalName.trim(),
                            gstState,
                            defaultHsn.trim(),
                            registrationType,
                            pricesIncludeGst,
                            showGstSplit
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KwGreen),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(0.6f)
                        .height(50.dp)
                        .testTag("upi_gst_save_btn")
                ) {
                    Text(
                        text = tr("Save", "सेव करें"),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    if (showAddUpiDialog) {
        AlertDialog(
            onDismissRequest = { showAddUpiDialog = false },
            title = { Text(tr("Add another UPI ID", "एक और UPI ID जोड़ें"), fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newUpiInput,
                    onValueChange = { newUpiInput = it.trim() },
                    label = { Text("example@upi") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newUpiInput.isNotBlank()) {
                            extraUpis.add(newUpiInput.trim())
                            showAddUpiDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KwGreen)
                ) {
                    Text(tr("Add", "जोड़ें"), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddUpiDialog = false }) {
                    Text(tr("Cancel", "रद्द करें"))
                }
            }
        )
    }
}

// ============================================================================
// PAGE 4: SYNC / BACKUP SUB-SCREEN
// ============================================================================
@Composable
fun SyncAndBackupSubScreen(
    settings: AppSettings?,
    currentUser: User?,
    syncState: FirestoreSyncState,
    pendingWaitingCount: Int,
    extraPrefs: SettingsExtraPrefsStore,
    onBack: () -> Unit,
    onSyncNow: () -> Unit,
    onTriggerBackupNow: () -> Unit,
    onRestoreSnapshot: (BackupHistoryEntry) -> Unit
) {
    BackHandler { onBack() }

    var autoSync by remember { mutableStateOf(extraPrefs.autoSync) }
    var wifiOnly by remember { mutableStateOf(extraPrefs.syncWifiOnly) }
    var backupFreq by remember { mutableStateOf(extraPrefs.driveBackupFrequency) }
    var backupWifiOnly by remember { mutableStateOf(extraPrefs.driveBackupWifiOnly) }
    var lastBackupMillis by remember { mutableStateOf(extraPrefs.lastDriveBackupMillis) }

    val lastSyncTime = settings?.lastSyncTime ?: 0L
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()) }

    val lastSyncFormatted = if (lastSyncTime > 0L) {
        timeFormatter.format(Date(lastSyncTime))
    } else {
        tr("Just now", "अभी")
    }

    val isSyncing = syncState is FirestoreSyncState.Syncing
    val userEmail = currentUser?.email?.ifBlank { "Signed-in Google Account" } ?: "Signed-in Google Account"
    val deviceModel = remember {
        val manuf = Build.MANUFACTURER?.replaceFirstChar { it.uppercase() }.orEmpty()
        val model = Build.MODEL.orEmpty()
        if (model.startsWith(manuf, ignoreCase = true)) model else "$manuf $model".trim()
    }

    // Build dynamic restore & history lists based on actual sync/backup timestamps
    val snapshots = remember(lastSyncTime, lastBackupMillis) {
        buildList {
            if (lastBackupMillis > 0L) {
                add(
                    BackupHistoryEntry(
                        id = "latest_drive",
                        title = dateFormatter.format(Date(lastBackupMillis)),
                        subtitle = "Google Drive · Automatic",
                        timestampMillis = lastBackupMillis,
                        sizeLabel = "Cloud snapshot",
                        statusDotColorHex = 0xFF16A34A
                    )
                )
            }
            if (lastSyncTime > 0L) {
                add(
                    BackupHistoryEntry(
                        id = "latest_sync",
                        title = dateFormatter.format(Date(lastSyncTime)),
                        subtitle = "Cloud Firestore · Live sync",
                        timestampMillis = lastSyncTime,
                        sizeLabel = "Synced",
                        statusDotColorHex = 0xFF16A34A
                    )
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KwPageBg)
            .testTag("sync_backup_subscreen")
    ) {
        SettingsSubScreenHeader(
            title = tr("Sync / Backup", "सिंक / बैकअप"),
            subtitle = tr("Keep your data safe", "अपना डेटा सुरक्षित रखें"),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Mint Status Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = kw(0xFFECFDF5),
                border = BorderStroke(1.dp, kw(0xFFA7F3D0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = KwGreen
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = when {
                                    isSyncing -> tr("Syncing…", "सिंक हो रहा है…")
                                    pendingWaitingCount > 0 -> tr("$pendingWaitingCount waiting to sync", "$pendingWaitingCount सिंक होने बाकी")
                                    else -> tr("All synced", "सब सिंक हो गया")
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = kw(0xFF065F46)
                            )
                            Text(
                                text = tr(
                                    "Last synced $lastSyncFormatted · $pendingWaitingCount waiting",
                                    "अंतिम सिंक $lastSyncFormatted · $pendingWaitingCount बाकी"
                                ),
                                fontSize = 12.sp,
                                color = kw(0xFF047857)
                            )
                        }
                    }

                    Button(
                        onClick = onSyncNow,
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KwGreen),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 14.dp,
                            vertical = 8.dp
                        ),
                        modifier = Modifier.testTag("sync_subscreen_sync_now_btn")
                    ) {
                        Text(
                            text = tr("Sync now", "अभी सिंक करें"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // 2. Auto sync & Wi-Fi only switches
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = KwCardBg),
                border = BorderStroke(1.dp, KwBorder)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("Auto sync", "ऑटो सिंक"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = KwTextPrimary
                            )
                            Text(
                                text = tr(
                                    "Sends changes as soon as you're online",
                                    "ऑनलाइन आते ही बदलाव तुरंत भेजता है"
                                ),
                                fontSize = 12.sp,
                                color = KwTextSecondary
                            )
                        }
                        Switch(
                            checked = autoSync,
                            onCheckedChange = {
                                autoSync = it
                                extraPrefs.autoSync = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = KwGreen,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = kw(0xFFD1D5DB)
                            )
                        )
                    }

                    HorizontalDivider(color = KwBorder, modifier = Modifier.padding(horizontal = 14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("Wi-Fi only", "केवल Wi-Fi पर"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = KwTextPrimary
                            )
                            Text(
                                text = tr("Saves mobile data", "मोबाइल डेटा बचाता है"),
                                fontSize = 12.sp,
                                color = KwTextSecondary
                            )
                        }
                        Switch(
                            checked = wifiOnly,
                            onCheckedChange = {
                                wifiOnly = it
                                extraPrefs.syncWifiOnly = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = KwGreen,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = kw(0xFFD1D5DB)
                            )
                        )
                    }
                }
            }

            // 3. DEVICES
            Column {
                FieldSectionLabel(text = tr("DEVICES", "डिवाइस"))
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                modifier = Modifier.size(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = KwGreenSoft
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        tint = KwGreenDark,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "${tr("This phone", "यह फ़ोन")} · ${deviceModel.ifBlank { "Android" }}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KwTextPrimary
                                )
                                Text(
                                    text = "${currentUser?.name ?: "Owner"} · ${currentUser?.role?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Admin"}",
                                    fontSize = 12.sp,
                                    color = KwTextSecondary
                                )
                            }
                        }
                        Text(
                            text = tr("Just now", "अभी"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = KwGreenDark
                        )
                    }
                }
            }

            // 4. GOOGLE DRIVE BACKUP
            Column {
                FieldSectionLabel(text = tr("GOOGLE DRIVE BACKUP", "GOOGLE DRIVE बैकअप"))
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userEmail,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KwTextPrimary
                                )
                                val backupStatusText = if (lastBackupMillis > 0L) {
                                    "${tr("Last backup", "अंतिम बैकअप")} ${dateFormatter.format(Date(lastBackupMillis))}"
                                } else {
                                    tr("No Google Drive backup yet", "अभी तक कोई Drive बैकअप नहीं")
                                }
                                Text(
                                    text = backupStatusText,
                                    fontSize = 12.sp,
                                    color = KwTextSecondary
                                )
                            }

                            val daysAgo = if (lastBackupMillis > 0L) {
                                ((System.currentTimeMillis() - lastBackupMillis) / (1000L * 60 * 60 * 24)).toInt()
                            } else {
                                -1
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (daysAgo == 0) KwGreenSoft else KwAmberSoft
                            ) {
                                Text(
                                    text = when {
                                        daysAgo == 0 -> tr("Today", "आज")
                                        daysAgo > 0 -> "$daysAgo days ago"
                                        else -> tr("Pending", "बाकी")
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (daysAgo == 0) KwGreenDark else KwAmber,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Segmented toggle: Daily | Weekly | Off
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = KwPageBg
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    "Daily" to tr("Daily", "रोज़ाना"),
                                    "Weekly" to tr("Weekly", "साप्ताहिक"),
                                    "Off" to tr("Off", "बंद")
                                ).forEach { (key, label) ->
                                    val selected = backupFreq == key
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                backupFreq = key
                                                extraPrefs.driveBackupFrequency = key
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (selected) KwCardBg else Color.Transparent,
                                        shadowElevation = if (selected) 1.dp else 0.dp
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 13.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (selected) KwTextPrimary else KwTextSecondary,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = tr("Back up on Wi-Fi only", "केवल Wi-Fi पर बैकअप लें"),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = KwTextPrimary
                            )
                            Switch(
                                checked = backupWifiOnly,
                                onCheckedChange = {
                                    backupWifiOnly = it
                                    extraPrefs.driveBackupWifiOnly = it
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = KwGreen,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = kw(0xFFD1D5DB)
                                )
                            )
                        }

                        Button(
                            onClick = {
                                val now = System.currentTimeMillis()
                                lastBackupMillis = now
                                extraPrefs.lastDriveBackupMillis = now
                                onTriggerBackupNow()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KwGreen),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("backup_now_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = tr("Back up now", "अभी बैकअप लें"),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 5. RESTORE FROM BACKUP
            Column {
                FieldSectionLabel(text = tr("RESTORE FROM BACKUP", "बैकअप से रीस्टोर करें"))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KwCardBg),
                    border = BorderStroke(1.dp, KwBorder)
                ) {
                    if (snapshots.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tr(
                                    "No backups found yet. Tap 'Back up now' above to create a snapshot.",
                                    "अभी तक कोई बैकअप नहीं है। स्नैपशॉट बनाने के लिए ऊपर 'अभी बैकअप लें' दबाएं।"
                                ),
                                fontSize = 13.sp,
                                color = KwTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            snapshots.forEachIndexed { idx, snap ->
                                if (idx > 0) {
                                    HorizontalDivider(
                                        color = KwBorder,
                                        modifier = Modifier.padding(horizontal = 14.dp)
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = snap.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = KwTextPrimary
                                        )
                                        Text(
                                            text = "${snap.sizeLabel} · ${snap.subtitle}",
                                            fontSize = 12.sp,
                                            color = KwTextSecondary
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { onRestoreSnapshot(snap) },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, KwGreen),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                            horizontal = 14.dp,
                                            vertical = 6.dp
                                        ),
                                        modifier = Modifier.defaultMinSize(minHeight = 38.dp)
                                    ) {
                                        Text(
                                            text = tr("Restore", "रीस्टोर"),
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = KwGreenDark,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = tr(
                        "Restoring replaces data on this phone. Unsynced changes are sent first.",
                        "रीस्टोर करने से इस फ़ोन का डेटा अपडेट हो जाएगा। अनसिंक बदलाव पहले भेजे जाते हैं।"
                    ),
                    fontSize = 11.5.sp,
                    color = KwTextSecondary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            // 6. HISTORY
            Column {
                FieldSectionLabel(text = tr("HISTORY", "इतिहास"))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KwCardBg),
                    border = BorderStroke(1.dp, KwBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(KwGreen)
                                )
                                Text(
                                    text = tr("Synced all records", "सभी रिकॉर्ड सिंक किए गए"),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = KwTextPrimary
                                )
                            }
                            Text(
                                text = lastSyncFormatted,
                                fontSize = 12.sp,
                                color = KwTextSecondary
                            )
                        }

                        if (lastBackupMillis > 0L) {
                            HorizontalDivider(color = KwBorder, modifier = Modifier.padding(horizontal = 14.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(KwGreen)
                                    )
                                    Text(
                                        text = tr("Google Drive backup saved", "Google Drive बैकअप सेव हुआ"),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = KwTextPrimary
                                    )
                                }
                                Text(
                                    text = timeFormatter.format(Date(lastBackupMillis)),
                                    fontSize = 12.sp,
                                    color = KwTextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ============================================================================
// PAGE 5: THEME & LANGUAGE SUB-SCREEN
// ============================================================================
@Composable
fun ThemeAndLanguageSubScreen(
    settings: AppSettings?,
    extraPrefs: SettingsExtraPrefsStore,
    onBack: () -> Unit,
    onApplyThemeAndLanguage: (
        themeMode: String,
        languageCode: String,
        textSizeMode: String,
        indianNumberFormat: Boolean
    ) -> Unit
) {
    BackHandler { onBack() }

    var selectedTheme by remember(settings?.themeMode) {
        mutableStateOf(settings?.themeMode?.uppercase() ?: "LIGHT")
    }
    var selectedLang by remember(settings?.language) {
        mutableStateOf(settings?.language ?: "en")
    }
    var textSizeMode by remember { mutableStateOf(extraPrefs.textSizeMode) }
    var indianFormat by remember { mutableStateOf(extraPrefs.indianNumberFormat) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KwPageBg)
            .testTag("theme_language_subscreen")
    ) {
        SettingsSubScreenHeader(
            title = tr("Theme & Language", "थीम और भाषा"),
            subtitle = tr("Look and words", "दिखावट और भाषा"),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. THEME (3 preview cards: Light, Dark, Auto)
            Column {
                FieldSectionLabel(text = tr("THEME", "थीम"))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ThemePreviewCard(
                        title = tr("Light", "लाइट"),
                        modeKey = "LIGHT",
                        selected = selectedTheme == "LIGHT",
                        onClick = { selectedTheme = "LIGHT" },
                        modifier = Modifier.weight(1f)
                    )
                    ThemePreviewCard(
                        title = tr("Dark", "डार्क"),
                        modeKey = "DARK",
                        selected = selectedTheme == "DARK",
                        onClick = { selectedTheme = "DARK" },
                        modifier = Modifier.weight(1f)
                    )
                    ThemePreviewCard(
                        title = tr("Auto", "ऑटो"),
                        modeKey = "SYSTEM",
                        selected = selectedTheme == "SYSTEM",
                        onClick = { selectedTheme = "SYSTEM" },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = tr(
                        "Auto follows your phone's setting. Dark is easier on the eyes at night.",
                        "ऑटो आपके फ़ोन की सेटिंग का पालन करता है। रात में डार्क मोड आँखों के लिए आरामदायक है।"
                    ),
                    fontSize = 12.sp,
                    color = KwTextSecondary
                )
            }

            // 2. APP LANGUAGE
            Column {
                FieldSectionLabel(text = tr("APP LANGUAGE", "ऐप की भाषा"))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KwCardBg),
                    border = BorderStroke(1.dp, KwBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LanguageOptionRow(
                            title = "English",
                            subtitle = "Menus and buttons in English",
                            selected = selectedLang == "en",
                            onClick = { selectedLang = "en" }
                        )
                        HorizontalDivider(color = KwBorder, modifier = Modifier.padding(horizontal = 14.dp))
                        LanguageOptionRow(
                            title = "हिंदी",
                            subtitle = "मेन्यू और बटन हिंदी में",
                            selected = selectedLang == "hi",
                            onClick = { selectedLang = "hi" }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = tr(
                        "Bill language is set separately in Bill & PDF.",
                        "बिल की भाषा 'Bill & PDF' में अलग से सेट की जाती है।"
                    ),
                    fontSize = 12.sp,
                    color = KwTextSecondary
                )
            }

            // 3. DISPLAY
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FieldSectionLabel(text = tr("DISPLAY", "डिस्प्ले"))
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
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Column {
                            FieldSectionLabel(text = tr("TEXT SIZE", "अक्षरों का आकार"))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = KwPageBg
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(
                                        "Small" to tr("Small", "छोटा"),
                                        "Normal" to tr("Normal", "सामान्य"),
                                        "Large" to tr("Large", "बड़ा")
                                    ).forEach { (key, label) ->
                                        val selected = textSizeMode == key
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { textSizeMode = key },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (selected) KwCardBg else Color.Transparent,
                                            shadowElevation = if (selected) 1.dp else 0.dp
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 13.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                                color = if (selected) KwTextPrimary else KwTextSecondary,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                        }
                                    }
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
                                    text = tr("Indian number format", "भारतीय संख्या प्रारूप"),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KwTextPrimary
                                )
                                Text(
                                    text = tr("1,14,500 instead of 114,500", "114,500 के बजाय 1,14,500"),
                                    fontSize = 12.sp,
                                    color = KwTextSecondary
                                )
                            }
                            Switch(
                                checked = indianFormat,
                                onCheckedChange = { indianFormat = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = KwGreen,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = kw(0xFFD1D5DB)
                                )
                            )
                        }
                    }
                }

                // Live Preview Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KwCardBg),
                    border = BorderStroke(1.dp, KwBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Text(
                            text = tr("Preview", "प्रीव्यू"),
                            fontSize = 11.5.sp,
                            color = KwTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val previewFontSize = when (textSizeMode) {
                            "Small" -> 19.sp
                            "Large" -> 26.sp
                            else -> 22.sp
                        }
                        Text(
                            text = if (indianFormat) "₹1,14,500" else "₹114,500",
                            fontSize = previewFontSize,
                            fontWeight = FontWeight.Bold,
                            color = KwTextPrimary
                        )
                        Text(
                            text = tr("Total purchases · 30 days", "कुल खरीद · 30 दिन"),
                            fontSize = 12.5.sp,
                            color = KwTextSecondary
                        )
                    }
                }
            }
        }

        // Bottom Apply button
        Surface(
            color = KwCardBg,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = {
                        onApplyThemeAndLanguage(
                            selectedTheme,
                            selectedLang,
                            textSizeMode,
                            indianFormat
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KwGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("theme_lang_apply_btn")
                ) {
                    Text(
                        text = tr("Apply", "लागू करें"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(
    title: String,
    modeKey: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag("theme_card_$modeKey"),
        shape = RoundedCornerShape(14.dp),
        color = KwCardBg,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) KwGreen else KwBorder)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Miniature UI preview box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, KwBorder, RoundedCornerShape(8.dp))
            ) {
                when (modeKey) {
                    "LIGHT" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFF9FAFB))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(34.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(KwGreen)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFFD1D5DB))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFFE5E7EB))
                            )
                        }
                    }
                    "DARK" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0F1720))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(34.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF22C55E))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF334155))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF1E293B))
                            )
                        }
                    }
                    else -> {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(Color(0xFFF9FAFB))
                                    .padding(6.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(20.dp)
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(KwGreen)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFFD1D5DB))
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(Color(0xFF0F1720))
                                    .padding(6.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(20.dp)
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color(0xFF22C55E))
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFF334155))
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (selected) KwGreenDark else KwTextPrimary
            )
        }
    }
}

@Composable
private fun LanguageOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = KwTextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = KwTextSecondary
            )
        }

        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(
                    width = if (selected) 6.dp else 1.5.dp,
                    color = if (selected) KwGreen else KwTextMuted,
                    shape = CircleShape
                )
        )
    }
}

// ============================================================================
// PAGE 6: APP LOCK & SECURITY SUB-SCREEN (Without Hide Numbers per user request)
// ============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppLockAndSecuritySubScreen(
    extraPrefs: SettingsExtraPrefsStore,
    onBack: () -> Unit,
    onSaveSecurity: () -> Unit,
    onChangePin: ((String) -> Unit)? = null
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    var lockApp by remember { mutableStateOf(extraPrefs.lockAppEnabled) }
    var useFingerprint by remember { mutableStateOf(extraPrefs.unlockFingerprint) }
    var autoLockAfter by remember { mutableStateOf(extraPrefs.autoLockAfter) }
    var askPinReportsCashbook by remember { mutableStateOf(extraPrefs.askPinForReportsCashbook) }
    var adminPinDeletesReturns by remember { mutableStateOf(extraPrefs.adminPinForDeletesReturns) }
    var blockScreenshots by remember { mutableStateOf(extraPrefs.blockScreenshots) }

    var showChangePinDialog by remember { mutableStateOf(false) }
    var newPinInput by remember { mutableStateOf("") }

    val autoLockChoices = listOf(
        "Right away" to tr("Right away", "तुरंत"),
        "1 minute" to tr("1 minute", "1 मिनट"),
        "5 minutes" to tr("5 minutes", "5 मिनट"),
        "Never" to tr("Never", "कभी नहीं")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KwPageBg)
            .testTag("app_lock_security_subscreen")
    ) {
        SettingsSubScreenHeader(
            title = tr("App lock & security", "ऐप लॉक और सुरक्षा"),
            subtitle = tr("Who can open the app", "ऐप कौन खोल सकता है"),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card 1: Lock the app
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr("Lock the app", "ऐप लॉक करें"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = KwTextPrimary
                        )
                        Text(
                            text = tr("Ask to unlock every time it opens", "हर बार खोलने पर अनलॉक मांगें"),
                            fontSize = 12.sp,
                            color = KwTextSecondary
                        )
                    }
                    Switch(
                        checked = lockApp,
                        onCheckedChange = { lockApp = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = KwGreen,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = kw(0xFFD1D5DB)
                        )
                    )
                }
            }

            // UNLOCK WITH
            Column {
                FieldSectionLabel(text = tr("UNLOCK WITH", "अनलॉक का तरीका"))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KwCardBg),
                    border = BorderStroke(1.dp, KwBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr("Fingerprint", "फिंगरप्रिंट"),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KwTextPrimary
                                )
                                Text(
                                    text = tr("Fastest way to open", "खोलने का सबसे तेज़ तरीका"),
                                    fontSize = 12.sp,
                                    color = KwTextSecondary
                                )
                            }
                            Switch(
                                checked = useFingerprint,
                                onCheckedChange = { useFingerprint = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = KwGreen,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = kw(0xFFD1D5DB)
                                )
                            )
                        }

                        HorizontalDivider(color = KwBorder, modifier = Modifier.padding(horizontal = 14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr("PIN", "पिन (PIN)"),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KwTextPrimary
                                )
                                Text(
                                    text = tr("4 digits · backup for fingerprint", "4 अंक · फिंगरप्रिंट का विकल्प"),
                                    fontSize = 12.sp,
                                    color = KwTextSecondary
                                )
                            }
                            Text(
                                text = tr("Change PIN", "PIN बदलें"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = KwGreenDark,
                                modifier = Modifier
                                    .clickable {
                                        newPinInput = ""
                                        showChangePinDialog = true
                                    }
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // AUTO-LOCK AFTER
            Column {
                FieldSectionLabel(text = tr("AUTO-LOCK AFTER", "ऑटो-लॉक का समय"))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    autoLockChoices.forEach { (key, label) ->
                        val selected = autoLockAfter == key
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { autoLockAfter = key },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) KwNavy else KwCardBg,
                            border = BorderStroke(1.dp, if (selected) KwNavy else KwBorder)
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else KwTextPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }

            // EXTRA PROTECTION (Explicitly omitting "Hide numbers" per user instruction)
            Column {
                FieldSectionLabel(text = tr("EXTRA PROTECTION", "अतिरिक्त सुरक्षा"))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KwCardBg),
                    border = BorderStroke(1.dp, KwBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr("Ask PIN for Reports and Cashbook", "रिपोर्ट और कैशबुक के लिए PIN पूछें"),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KwTextPrimary
                                )
                                Text(
                                    text = tr("Even when the app is unlocked", "ऐप अनलॉक होने पर भी"),
                                    fontSize = 12.sp,
                                    color = KwTextSecondary
                                )
                            }
                            Switch(
                                checked = askPinReportsCashbook,
                                onCheckedChange = { askPinReportsCashbook = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = KwGreen,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = kw(0xFFD1D5DB)
                                )
                            )
                        }

                        HorizontalDivider(color = KwBorder, modifier = Modifier.padding(horizontal = 14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr("Admin PIN for deletes and returns", "डिलीट और रिटर्न के लिए एडमिन PIN"),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KwTextPrimary
                                )
                                Text(
                                    text = tr("Staff can't do these alone", "स्टाफ अकेले यह नहीं कर सकता"),
                                    fontSize = 12.sp,
                                    color = KwTextSecondary
                                )
                            }
                            Switch(
                                checked = adminPinDeletesReturns,
                                onCheckedChange = { adminPinDeletesReturns = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = KwGreen,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = kw(0xFFD1D5DB)
                                )
                            )
                        }

                        HorizontalDivider(color = KwBorder, modifier = Modifier.padding(horizontal = 14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr("Block screenshots", "स्क्रीनशॉट ब्लॉक करें"),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KwTextPrimary
                                )
                                Text(
                                    text = tr("Hides the app in recent apps", "रीसेंट ऐप्स में स्क्रीन छुपाता है"),
                                    fontSize = 12.sp,
                                    color = KwTextSecondary
                                )
                            }
                            Switch(
                                checked = blockScreenshots,
                                onCheckedChange = { blockScreenshots = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = KwGreen,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = kw(0xFFD1D5DB)
                                )
                            )
                        }
                    }
                }
            }

            // WRONG PIN
            Column {
                FieldSectionLabel(text = tr("WRONG PIN", "गलत पिन"))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KwCardBg),
                    border = BorderStroke(1.dp, KwBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Text(
                            text = tr(
                                "Lock for 5 minutes after 5 wrong tries",
                                "5 गलत प्रयासों के बाद 5 मिनट के लिए लॉक करें"
                            ),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = KwTextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tr(
                                "Admin can reset staff PINs in Staff & Devices.",
                                "एडमिन 'Staff & Devices' में स्टाफ पिन रीसेट कर सकता है।"
                            ),
                            fontSize = 12.sp,
                            color = KwTextSecondary
                        )
                    }
                }
            }
        }

        // Bottom Save button
        Surface(
            color = KwCardBg,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = {
                        extraPrefs.lockAppEnabled = lockApp
                        extraPrefs.unlockFingerprint = useFingerprint
                        extraPrefs.autoLockAfter = autoLockAfter
                        extraPrefs.askPinForReportsCashbook = askPinReportsCashbook
                        extraPrefs.adminPinForDeletesReturns = adminPinDeletesReturns
                        extraPrefs.blockScreenshots = blockScreenshots
                        onSaveSecurity()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KwGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("security_save_btn")
                ) {
                    Text(
                        text = tr("Save", "सेव करें"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    if (showChangePinDialog) {
        val pinUpdatedMsg = tr("4-digit PIN updated", "4-अंकों का PIN अपडेट हो गया")
        AlertDialog(
            onDismissRequest = { showChangePinDialog = false },
            title = { Text(tr("Change 4-digit PIN", "4-अंकों का PIN बदलें"), fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPinInput,
                    onValueChange = { newPinInput = it.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text(tr("New 4-digit PIN", "नया 4-अंकों का PIN")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPinInput.length == 4) {
                            extraPrefs.appLockPin = newPinInput
                            onChangePin?.invoke(newPinInput)
                            Toast.makeText(context, pinUpdatedMsg, Toast.LENGTH_SHORT).show()
                            showChangePinDialog = false
                        }
                    },
                    enabled = newPinInput.length == 4,
                    colors = ButtonDefaults.buttonColors(containerColor = KwGreen)
                ) {
                    Text(tr("Update PIN", "PIN बदलें"), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePinDialog = false }) {
                    Text(tr("Cancel", "रद्द करें"))
                }
            }
        )
    }
}

// ============================================================================
// BILL & PDF SUB-SCREEN (Accessible from STORE -> Bill & PDF)
// ============================================================================
@Composable
fun BillAndPdfSubScreen(
    extraPrefs: SettingsExtraPrefsStore,
    onBack: () -> Unit,
    onSaveBillPdf: () -> Unit
) {
    BackHandler { onBack() }

    var paperSize by remember { mutableStateOf(extraPrefs.billPaperSize) }
    var billLang by remember { mutableStateOf(extraPrefs.billLanguage) }
    var billPrefix by remember { mutableStateOf(extraPrefs.billPrefix) }

    // Bill Element Toggles
    var showLogo by remember { mutableStateOf(extraPrefs.billShowLogo) }
    var showShopAddressPhone by remember { mutableStateOf(extraPrefs.billShowShopAddressPhone) }
    var showGstin by remember { mutableStateOf(extraPrefs.billShowGstin) }
    var showShopHours by remember { mutableStateOf(extraPrefs.billShowShopHours) }
    var showCustomerDetails by remember { mutableStateOf(extraPrefs.billShowCustomerDetails) }
    var showItemHsnGst by remember { mutableStateOf(extraPrefs.billShowItemHsnGst) }
    var showWeightBreakdown by remember { mutableStateOf(extraPrefs.billShowWeightBreakdown) }
    var showRateDiscount by remember { mutableStateOf(extraPrefs.billShowRateDiscount) }
    var showGstSplit by remember { mutableStateOf(extraPrefs.showGstSplitOnBills) }
    var showUdhariSummary by remember { mutableStateOf(extraPrefs.billShowUdhariSummary) }
    var showPaymentDetails by remember { mutableStateOf(extraPrefs.billShowPaymentDetails) }
    var showPayNowQr by remember { mutableStateOf(extraPrefs.showPayNowQrOnBills) }
    var showPromiseDate by remember { mutableStateOf(extraPrefs.billShowPromiseDate) }
    var showSignatureFooter by remember { mutableStateOf(extraPrefs.billShowSignatureFooter) }

    val includeEveryElement = showLogo &&
        showShopAddressPhone &&
        showGstin &&
        showShopHours &&
        showCustomerDetails &&
        showItemHsnGst &&
        showWeightBreakdown &&
        showRateDiscount &&
        showGstSplit &&
        showUdhariSummary &&
        showPaymentDetails &&
        showPayNowQr &&
        showPromiseDate &&
        showSignatureFooter

    val enabledElementsCount = listOf(
        showLogo,
        showShopAddressPhone,
        showGstin,
        showShopHours,
        showCustomerDetails,
        showItemHsnGst,
        showWeightBreakdown,
        showRateDiscount,
        showGstSplit,
        showUdhariSummary,
        showPaymentDetails,
        showPayNowQr,
        showPromiseDate,
        showSignatureFooter
    ).count { it }

    fun setAllElements(enabled: Boolean) {
        showLogo = enabled
        showShopAddressPhone = enabled
        showGstin = enabled
        showShopHours = enabled
        showCustomerDetails = enabled
        showItemHsnGst = enabled
        showWeightBreakdown = enabled
        showRateDiscount = enabled
        showGstSplit = enabled
        showUdhariSummary = enabled
        showPaymentDetails = enabled
        showPayNowQr = enabled
        showPromiseDate = enabled
        showSignatureFooter = enabled
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KwPageBg)
            .testTag("bill_pdf_subscreen")
    ) {
        SettingsSubScreenHeader(
            title = tr("Bill & PDF", "बिल और PDF"),
            subtitle = tr("Paper size, language, prefix and printed bill elements", "पेपर साइज़, भाषा, प्रीफ़िक्स और बिल पर दिखने वाले तत्व"),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                FieldSectionLabel(text = tr("PAPER SIZE", "पेपर का आकार"))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("A5", "A4", "Thermal 3\"").forEach { sizeOption ->
                        val selected = paperSize == sizeOption
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { paperSize = sizeOption },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) KwNavy else KwCardBg,
                            border = BorderStroke(1.dp, if (selected) KwNavy else KwBorder)
                        ) {
                            Text(
                                text = sizeOption,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else KwTextPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }

            Column {
                FieldSectionLabel(text = tr("BILL LANGUAGE", "बिल की भाषा"))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("English", "हिंदी").forEach { langOption ->
                        val selected = billLang == langOption
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { billLang = langOption },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) KwNavy else KwCardBg,
                            border = BorderStroke(1.dp, if (selected) KwNavy else KwBorder)
                        ) {
                            Text(
                                text = langOption,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else KwTextPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }

            Column {
                FieldSectionLabel(text = tr("BILL NUMBER PREFIX", "बिल नंबर प्रीफ़िक्स"))
                OutlinedTextField(
                    value = billPrefix,
                    onValueChange = { billPrefix = it.uppercase().take(8) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = KwCardBg,
                        unfocusedContainerColor = KwCardBg,
                        focusedBorderColor = KwGreen,
                        unfocusedBorderColor = KwBorder,
                        focusedTextColor = KwTextPrimary,
                        unfocusedTextColor = KwTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ================================================================
            // ELEMENTS SHOWN ON BILL (Master Toggle + Individual Element Toggles)
            // ================================================================
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FieldSectionLabel(text = tr("ELEMENTS SHOWN ON BILL", "बिल पर दिखने वाले तत्व"))
                    Text(
                        text = "$enabledElementsCount / 14 ${tr("shown", "चालू")}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = KwGreenDark
                    )
                }

                // Master Toggle: Include every element
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { setAllElements(!includeEveryElement) }
                        .testTag("bill_toggle_include_all_card"),
                    shape = RoundedCornerShape(14.dp),
                    color = if (includeEveryElement) KwGreenSoft else KwCardBg,
                    border = BorderStroke(1.dp, if (includeEveryElement) KwGreen else KwBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = tr("Include every element", "सभी तत्व शामिल करें"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (includeEveryElement) KwGreenDark else KwTextPrimary
                            )
                            Text(
                                text = tr(
                                    "Show all store, customer, item, tax, QR and udhari details on bills & PDFs",
                                    "बिल और PDF पर दुकान, ग्राहक, आइटम, टैक्स, QR और उधारी की पूरी जानकारी दिखाएं"
                                ),
                                fontSize = 12.sp,
                                color = KwTextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                        Switch(
                            checked = includeEveryElement,
                            onCheckedChange = { setAllElements(it) },
                            modifier = Modifier.testTag("bill_toggle_include_all"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = KwGreen,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = kw(0xFFD1D5DB)
                            )
                        )
                    }
                }

                // Individual Toggles Card — Store & Header Elements
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = KwCardBg,
                    border = BorderStroke(1.dp, KwBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BillElementToggleRow(
                            title = tr("Store logo & emblem", "दुकान का लोगो"),
                            subtitle = tr("Print your store logo at the top of the invoice", "बिल के शीर्ष पर दुकान का लोगो छापें"),
                            checked = showLogo,
                            onCheckedChange = { showLogo = it },
                            testTag = "bill_toggle_logo"
                        )
                        HorizontalDivider(color = KwBorder, modifier = Modifier.padding(horizontal = 16.dp))
                        BillElementToggleRow(
                            title = tr("Store address & phone numbers", "दुकान का पता और फ़ोन नंबर"),
                            subtitle = tr("Village/city address and primary/alternate mobile numbers", "पता और मुख्य/वैकल्पिक मोबाइल नंबर"),
                            checked = showShopAddressPhone,
                            onCheckedChange = { showShopAddressPhone = it },
                            testTag = "bill_toggle_address_phone"
                        )
                        HorizontalDivider(color = KwBorder, modifier = Modifier.padding(horizontal = 16.dp))
                        BillElementToggleRow(
                            title = tr("GSTIN & legal registration", "GSTIN और कानूनी पंजीकरण"),
                            subtitle = tr("Print GSTIN, state code and legal business name", "GSTIN, राज्य कोड और कानूनी नाम दिखाएं"),
                            checked = showGstin,
                            onCheckedChange = { showGstin = it },
                            testTag = "bill_toggle_gstin"
                        )
                        HorizontalDivider(color = KwBorder, modifier = Modifier.padding(horizontal = 16.dp))
                        BillElementToggleRow(
                            title = tr("Store timings", "दुकान खुलने का समय"),
                            subtitle = tr("Opening and closing hours in the header", "हेडर में खुलने और बंद होने का समय"),
                            checked = showShopHours,
                            onCheckedChange = { showShopHours = it },
                            testTag = "bill_toggle_shop_hours"
                        )
                        HorizontalDivider(color = KwBorder, modifier = Modifier.padding(horizontal = 16.dp))
                        BillElementToggleRow(
                            title = tr("Customer village & phone", "ग्राहक का गाँव और फ़ोन"),
                            subtitle = tr("Customer name, village/city and phone number", "ग्राहक का नाम, गाँव/शहर और मोबाइल नंबर"),
                            checked = showCustomerDetails,
                            onCheckedChange = { showCustomerDetails = it },
                            testTag = "bill_toggle_customer_details"
                        )
                    }
                }

                // Individual Toggles Card — Line Items & Tax Breakdown
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = KwCardBg,
                    border = BorderStroke(1.dp, KwBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BillElementToggleRow(
                            title = tr("Item HSN code & GST %", "आइटम HSN कोड और GST %"),
                            subtitle = tr("Show HSN/SAC code and tax rate column per item", "प्रत्येक आइटम के साथ HSN कोड और टैक्स दर दिखाएं"),
                            checked = showItemHsnGst,
                            onCheckedChange = { showItemHsnGst = it },
                            testTag = "bill_toggle_item_hsn"
                        )
                        HorizontalDivider(color = KwBorder, modifier = Modifier.padding(horizontal = 16.dp))
                        BillElementToggleRow(
                            title = tr("Bag & loose weight breakdown", "बोरी और खुले वज़न का विवरण"),
                            subtitle = tr("Show bag count × bag kg + loose kg on each line", "बोरी संख्या × वज़न और खुला वज़न अलग-अलग दिखाएं"),
                            checked = showWeightBreakdown,
                            onCheckedChange = { showWeightBreakdown = it },
                            testTag = "bill_toggle_weight_breakdown"
                        )
                        HorizontalDivider(color = KwBorder, modifier = Modifier.padding(horizontal = 16.dp))
                        BillElementToggleRow(
                            title = tr("Item rate & discount", "आइटम दर और छूट (Discount)"),
                            subtitle = tr("Show per-bag/per-kg rate and line-level discounts", "प्रति बोरी/किलो दर और छूट दिखाएं"),
                            checked = showRateDiscount,
                            onCheckedChange = { showRateDiscount = it },
                            testTag = "bill_toggle_rate_discount"
                        )
                        HorizontalDivider(color = KwBorder, modifier = Modifier.padding(horizontal = 16.dp))
                        BillElementToggleRow(
                            title = tr("GST tax split (CGST + SGST)", "GST टैक्स विभाजन (CGST + SGST)"),
                            subtitle = tr("Print taxable value, CGST and SGST breakdown table", "कर योग्य मूल्य, CGST और SGST तालिका छापें"),
                            checked = showGstSplit,
                            onCheckedChange = { showGstSplit = it },
                            testTag = "bill_toggle_gst_split"
                        )
                    }
                }

                // Individual Toggles Card — Udhari, Payment, QR & Footer
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = KwCardBg,
                    border = BorderStroke(1.dp, KwBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BillElementToggleRow(
                            title = tr("Previous & closing udhari balance", "पिछली और कुल बकाया उधारी"),
                            subtitle = tr("Show previous balance, current bill due and total udhari", "पुराना बकाया, इस बिल का बकाया और कुल उधारी दिखाएं"),
                            checked = showUdhariSummary,
                            onCheckedChange = { showUdhariSummary = it },
                            testTag = "bill_toggle_udhari_summary"
                        )
                        HorizontalDivider(color = KwBorder, modifier = Modifier.padding(horizontal = 16.dp))
                        BillElementToggleRow(
                            title = tr("Payment mode & paid amount", "भुगतान का तरीका और जमा राशि"),
                            subtitle = tr("Cash, UPI or split payment received on the bill", "नकद, UPI या आंशिक भुगतान का विवरण"),
                            checked = showPaymentDetails,
                            onCheckedChange = { showPaymentDetails = it },
                            testTag = "bill_toggle_payment_details"
                        )
                        HorizontalDivider(color = KwBorder, modifier = Modifier.padding(horizontal = 16.dp))
                        BillElementToggleRow(
                            title = tr("UPI 'Pay now' QR code", "UPI 'Pay now' QR कोड"),
                            subtitle = tr("Scannable UPI QR code for instant digital payment", "तुरंत भुगतान के लिए स्कैन करने योग्य UPI QR कोड"),
                            checked = showPayNowQr,
                            onCheckedChange = { showPayNowQr = it },
                            testTag = "bill_toggle_pay_now_qr"
                        )
                        HorizontalDivider(color = KwBorder, modifier = Modifier.padding(horizontal = 16.dp))
                        BillElementToggleRow(
                            title = tr("Promise date & due reminder", "वादा तारीख़ और बकाया रिमाइंडर"),
                            subtitle = tr("Print promised repayment date when bill has udhari", "उधारी वाले बिल पर भुगतान की वादा तारीख़ छापें"),
                            checked = showPromiseDate,
                            onCheckedChange = { showPromiseDate = it },
                            testTag = "bill_toggle_promise_date"
                        )
                        HorizontalDivider(color = KwBorder, modifier = Modifier.padding(horizontal = 16.dp))
                        BillElementToggleRow(
                            title = tr("Authorized signature & thank-you note", "हस्ताक्षर और धन्यवाद संदेश"),
                            subtitle = tr("Footer with signatory line, terms and thank-you message", "फुटर में हस्ताक्षर स्थान, शर्तें और धन्यवाद संदेश"),
                            checked = showSignatureFooter,
                            onCheckedChange = { showSignatureFooter = it },
                            testTag = "bill_toggle_signature_footer"
                        )
                    }
                }
            }
        }

        Surface(
            color = KwCardBg,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = {
                        extraPrefs.billPaperSize = paperSize
                        extraPrefs.billLanguage = billLang
                        extraPrefs.billPrefix = billPrefix.ifBlank { "INV-" }
                        extraPrefs.billShowLogo = showLogo
                        extraPrefs.billShowShopAddressPhone = showShopAddressPhone
                        extraPrefs.billShowGstin = showGstin
                        extraPrefs.billShowShopHours = showShopHours
                        extraPrefs.billShowCustomerDetails = showCustomerDetails
                        extraPrefs.billShowItemHsnGst = showItemHsnGst
                        extraPrefs.billShowWeightBreakdown = showWeightBreakdown
                        extraPrefs.billShowRateDiscount = showRateDiscount
                        extraPrefs.showGstSplitOnBills = showGstSplit
                        extraPrefs.billShowUdhariSummary = showUdhariSummary
                        extraPrefs.billShowPaymentDetails = showPaymentDetails
                        extraPrefs.showPayNowQrOnBills = showPayNowQr
                        extraPrefs.billShowPromiseDate = showPromiseDate
                        extraPrefs.billShowSignatureFooter = showSignatureFooter
                        onSaveBillPdf()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KwGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("bill_pdf_save_btn")
                ) {
                    Text(
                        text = tr("Save", "सेव करें"),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BillElementToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = KwTextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = KwTextSecondary,
                lineHeight = 16.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = KwGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = kw(0xFFD1D5DB)
            )
        )
    }
}

// ============================================================================
// PAGE 7: LOG OUT OF KHATAWISE? SHEET / MODAL DIALOG
// ============================================================================
@Composable
fun SettingsLogoutModalDialog(
    userEmail: String,
    pendingSyncChanges: Int,
    onSyncAndLogout: () -> Unit,
    onLogoutAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {}
                    .testTag("logout_khatawise_confirm_dialog"),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = KwCardBg
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Bottom-sheet drag handle
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(kw(0xFFD1D5DB))
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = KwAmberSoft
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = KwAmber,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = tr("Log out of KhataWise?", "KhataWise से लॉग आउट करें?"),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = KwTextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val displayEmail = userEmail.ifBlank { "your Google account" }
                    Text(
                        text = tr(
                            "You'll sign out $displayEmail on this phone. You'll need Google sign-in to open the shop again.",
                            "आप इस फ़ोन पर $displayEmail से साइन आउट हो जाएंगे। दुकान फिर से खोलने के लिए Google साइन-इन आवश्यक होगा।"
                        ),
                        fontSize = 13.5.sp,
                        color = KwTextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp
                    )

                    if (pendingSyncChanges > 0) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = KwAmberSoft,
                            border = BorderStroke(1.dp, kw(0xFFFDE68A))
                        ) {
                            Text(
                                text = tr(
                                    "$pendingSyncChanges changes are waiting to sync. Sync first so nothing is lost.",
                                    "$pendingSyncChanges बदलाव सिंक होने बाकी हैं। पहले सिंक करें ताकि कुछ भी न खोए।"
                                ),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = kw(0xFF92400E),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = onSyncAndLogout,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KwGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_sync_and_logout")
                    ) {
                        Text(
                            text = tr("Sync & log out", "सिंक करें और लॉग आउट करें"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    TextButton(
                        onClick = onLogoutAnyway,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 44.dp)
                            .testTag("btn_logout_anyway")
                    ) {
                        Text(
                            text = tr("Log out anyway", "फिर भी लॉग आउट करें"),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = KwRed,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    TextButton(
                        onClick = onDismiss,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 42.dp)
                            .testTag("btn_logout_cancel")
                    ) {
                        Text(
                            text = tr("Cancel", "रद्द करें"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = KwTextSecondary,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
