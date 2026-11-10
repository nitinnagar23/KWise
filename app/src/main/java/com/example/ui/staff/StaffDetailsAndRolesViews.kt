package com.example.ui.staff

import com.example.ui.theme.KwSurface
import com.example.ui.theme.kw
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Role
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import com.example.util.tr
import java.security.SecureRandom
import java.util.Locale

internal val StaffBrandGreen: Color get() = kw(0xFF0E6F43)
internal val StaffMintSoft: Color get() = kw(0xFFDDF3E8)
internal val StaffNavyPill: Color get() = Color(0xFF14305E)
internal val StaffBluePillBg: Color get() = kw(0xFFD6EEFB)
internal val StaffBluePillText: Color get() = kw(0xFF0C4A6E)
internal val StaffCashierPillBg: Color get() = kw(0xFFEEF0F6)
internal val StaffCashierPillText: Color get() = kw(0xFF1E293B)
internal val StaffAmberPillBg: Color get() = kw(0xFFFDECCE)
internal val StaffAmberPillText: Color get() = kw(0xFFB45309)
internal val StaffCrimson: Color get() = kw(0xFFB91C1C)
internal data class StaffProfileItem(
    val id: String,
    val displayName: String,
    val registeredName: String,
    val email: String,
    val deviceModel: String,
    val androidVersion: String,
    val lastSeenLabel: String,
    val joinedLabel: String,
    val role: Role,
    val hasPin: Boolean,
    val pinValue: String = "",
    val allowBiometric: Boolean = true,
    val isActive: Boolean = true,
    val isPendingApproval: Boolean = false,
    val isCurrentUser: Boolean = false
)

internal data class PairedCounterDevice(
    val id: String,
    val title: String,
    val modelAndDate: String,
    val role: Role,
    val statusText: String,
    val isActiveNow: Boolean
)

@Composable
internal fun StaffDevicesTabContent(
    pairingCode: String,
    selectedRole: String,
    remainingSeconds: Long,
    pairedDevices: List<PairedCounterDevice>,
    onSelectRole: (String) -> Unit,
    onGenerateNewCode: () -> Unit,
    onUnpairDevice: (PairedCounterDevice) -> Unit
) {
    val context = LocalContext.current
    val mins = (remainingSeconds / 60).coerceAtLeast(0)
    val secs = (remainingSeconds % 60).coerceAtLeast(0)
    val formattedCountdown = String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    val progressFraction = (remainingSeconds.toFloat() / 600f).coerceIn(0.0f, 1f)

    val copiedToastText = tr("Pairing code copied!", "पेयरिंग कोड कॉपी किया गया!")
    val whatsappShareMsg = tr(
        "Use KhataWise counter pairing code $pairingCode (valid for 10 minutes) to connect this shop device.",
        "इस दुकान डिवाइस को जोड़ने के लिए KhataWise काउंटर पेयरिंग कोड $pairingCode (10 मिनट के लिए मान्य) का उपयोग करें।"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card 1: Pair counter device
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = FlatDarkCard,
            border = BorderStroke(1.dp, FlatDarkBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("Pair counter device", "काउंटर डिवाइस जोड़ें"),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary
                    )

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = StaffBluePillBg
                    ) {
                        Text(
                            text = tr("10-min code", "10-मिनट कोड"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = StaffBluePillText,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = tr(
                        "Enter this code on the counter phone. Staff confirm the shop and it connects.",
                        "काउंटर फ़ोन पर यह कोड दर्ज करें। स्टाफ दुकान की पुष्टि करेगा और यह जुड़ जाएगा।"
                    ),
                    fontSize = 13.5.sp,
                    color = FlatTextSecondary,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = tr("DEVICE ROLE", "डिवाइस भूमिका"),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextSecondary,
                    letterSpacing = 0.8.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Segmented Role Switcher: Cashier | Manager
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = kw(0xFFF1F2F6)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isCashier = selectedRole == "CASHIER"
                        Surface(
                            onClick = { onSelectRole("CASHIER") },
                            shape = RoundedCornerShape(11.dp),
                            color = if (isCashier) StaffBrandGreen else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = tr("Cashier", "कैशियर"),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCashier) Color.White else FlatTextSecondary
                                )
                            }
                        }

                        Surface(
                            onClick = { onSelectRole("MANAGER") },
                            shape = RoundedCornerShape(11.dp),
                            color = if (!isCashier) StaffBrandGreen else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = tr("Manager", "मैनेजर"),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isCashier) Color.White else FlatTextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 6 Individual Digit Boxes
                val cleanDigits = pairingCode.padEnd(6, '–').take(6)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 6) {
                        val ch = cleanDigits[i].toString()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(58.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(kw(0xFFF0FAF5))
                                .border(1.5.dp, StaffBrandGreen, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ch,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                color = StaffBrandGreen
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Progress bar + countdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(kw(0xFFE2E8F0))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(StaffBrandGreen)
                        )
                    }

                    Text(
                        text = "${tr("Expires in", "समाप्त होगा")} $formattedCountdown",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = StaffAmberPillText
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Copy code | Share on WhatsApp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        onClick = {
                            if (pairingCode.isNotBlank()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Pairing Code", pairingCode))
                                Toast.makeText(context, copiedToastText, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("copy_pairing_code_btn"),
                        shape = RoundedCornerShape(14.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.5.dp, StaffBrandGreen)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = tr("Copy code", "कोड कॉपी करें"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = StaffBrandGreen
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (pairingCode.isNotBlank()) {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, whatsappShareMsg)
                                }
                                try {
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Pairing Code"))
                                } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("share_pairing_whatsapp_btn"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StaffBrandGreen,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = tr("Share on WhatsApp", "WhatsApp पर भेजें"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onGenerateNewCode() }
                        .padding(vertical = 4.dp)
                        .testTag("btn_generate_pairing_code"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tr("Generate new code", "नया कोड बनाएं"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = StaffBrandGreen
                    )
                }
            }
        }

        // Section: Paired devices (only real paired devices)
        Text(
            text = "${tr("Paired devices", "जुड़े हुए डिवाइस")} (${pairedDevices.size})",
            fontSize = 16.5.sp,
            fontWeight = FontWeight.Bold,
            color = FlatTextPrimary
        )

        if (pairedDevices.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Smartphone,
                        contentDescription = null,
                        tint = FlatTextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = tr("No counter devices paired yet", "अभी तक कोई काउंटर डिवाइस नहीं जुड़ा है"),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = tr(
                            "Enter the 6-digit code above on a staff phone to pair it with this shop.",
                            "इस दुकान से जोड़ने के लिए स्टाफ के फ़ोन पर ऊपर दिया गया 6-अंकीय कोड दर्ज करें।"
                        ),
                        fontSize = 13.sp,
                        color = FlatTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            pairedDevices.forEach { device ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(StaffBluePillBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Smartphone,
                                contentDescription = null,
                                tint = StaffBluePillText,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = device.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                            if (device.modelAndDate.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = device.modelAndDate,
                                    fontSize = 13.sp,
                                    color = FlatTextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StaffRoleBadgePill(role = device.role)
                                Text(
                                    text = device.statusText,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (device.isActiveNow) StaffBrandGreen else FlatTextSecondary
                                )
                            }
                        }

                        Text(
                            text = tr("Unpair", "अनपेयर"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = StaffCrimson,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onUnpairDevice(device) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
internal fun StaffRolesTabContent(
    onOpenCustomPermissions: () -> Unit
) {
    data class RoleMatrixRow(
        val label: String,
        val adminStatus: String,   // "YES", "NO", "LIMITED"
        val managerStatus: String,
        val cashierStatus: String
    )

    val rows = listOf(
        RoleMatrixRow(tr("Make bills &\ncollect payments", "बिल बनाएं और\nभुगतान प्राप्त करें"), "YES", "YES", "YES"),
        RoleMatrixRow(tr("Edit or return bills", "बिल संपादित या वापस करें"), "YES", "YES", "NO"),
        RoleMatrixRow(tr("Customers &\nudhari ledger", "ग्राहक और\nउधारी खाता"), "YES", "YES", "LIMITED"),
        RoleMatrixRow(tr("Cashbook entries", "कैशबुक प्रविष्टियाँ"), "YES", "YES", "NO"),
        RoleMatrixRow(tr("View stock", "स्टॉक देखें"), "YES", "YES", "YES"),
        RoleMatrixRow(tr("Stock in & item\nedits", "स्टॉक इन और आइटम\nसंपादन"), "YES", "YES", "NO"),
        RoleMatrixRow(tr("Suppliers &\npurchase bills", "सप्लायर और\nखरीद बिल"), "YES", "YES", "NO"),
        RoleMatrixRow(tr("Edit today's rates", "आज के भाव बदलें"), "YES", "YES", "NO"),
        RoleMatrixRow(tr("Reports, cost &\nprofit", "रिपोर्ट, लागत और\nमुनाफा"), "YES", "LIMITED", "NO"),
        RoleMatrixRow(tr("Staff, devices &\nroles", "स्टाफ, डिवाइस और\nभूमिकाएँ"), "YES", "NO", "NO"),
        RoleMatrixRow(tr("Backup, export &\nsettings", "बैकअप, एक्सपोर्ट और\nसेटिंग्स"), "YES", "NO", "NO")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Card 1: Permission Matrix Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = FlatDarkCard,
            border = BorderStroke(1.dp, FlatDarkBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 16.dp)
            ) {
                // Header Row — single-line role pills with dedicated column widths so they never wrap
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = tr("WHAT EACH ROLE\nCAN DO", "प्रत्येक भूमिका\nक्या कर सकती है"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        lineHeight = 14.sp,
                        letterSpacing = 0.4.sp,
                        modifier = Modifier.weight(1f)
                    )

                    Box(modifier = Modifier.width(62.dp), contentAlignment = Alignment.Center) {
                        StaffRoleBadgePill(role = Role.ADMINISTRATOR, compact = true)
                    }
                    Box(modifier = Modifier.width(74.dp), contentAlignment = Alignment.Center) {
                        StaffRoleBadgePill(role = Role.MANAGER, compact = true)
                    }
                    Box(modifier = Modifier.width(68.dp), contentAlignment = Alignment.Center) {
                        StaffRoleBadgePill(role = Role.STAFF, compact = true)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                rows.forEach { row ->
                    HorizontalDivider(color = kw(0xFFF1F5F9))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = row.label,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary,
                            lineHeight = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Box(modifier = Modifier.width(62.dp), contentAlignment = Alignment.Center) {
                            RoleMatrixCell(status = row.adminStatus)
                        }
                        Box(modifier = Modifier.width(74.dp), contentAlignment = Alignment.Center) {
                            RoleMatrixCell(status = row.managerStatus)
                        }
                        Box(modifier = Modifier.width(68.dp), contentAlignment = Alignment.Center) {
                            RoleMatrixCell(status = row.cashierStatus)
                        }
                    }
                }
            }
        }

        // Card 2: Limited explanation banner
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = kw(0xFFF1F2F8)
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = FlatTextPrimary)) {
                        append(tr("Limited", "Limited"))
                    }
                    append(
                        tr(
                            " means view only: a Manager sees sales reports without cost and profit, and a Cashier sees customer names and udhari status but can't edit them.",
                            " का अर्थ केवल देखना है: मैनेजर बिना लागत और मुनाफे के बिक्री रिपोर्ट देखता है, और कैशियर ग्राहक के नाम और उधारी स्थिति देखता है लेकिन उन्हें बदल नहीं सकता।"
                        )
                    )
                },
                fontSize = 13.5.sp,
                color = kw(0xFF334155),
                lineHeight = 19.sp,
                modifier = Modifier.padding(16.dp)
            )
        }

        // Card 3: Custom permissions
        Surface(
            onClick = onOpenCustomPermissions,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("custom_permissions_card"),
            shape = RoundedCornerShape(18.dp),
            color = FlatDarkCard,
            border = BorderStroke(1.dp, FlatDarkBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr("Custom permissions", "कस्टम अनुमतियाँ"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tr("Change what a single person can do", "बदलें कि एक व्यक्ति क्या कर सकता है"),
                        fontSize = 13.sp,
                        color = FlatTextSecondary
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = FlatTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun RoleMatrixCell(status: String) {
    when (status) {
        "YES" -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Allowed",
                tint = StaffBrandGreen,
                modifier = Modifier.size(20.dp)
            )
        }
        "LIMITED" -> {
            Surface(
                shape = RoundedCornerShape(50),
                color = StaffAmberPillBg
            ) {
                Text(
                    text = tr("Limited", "सीमित"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = StaffAmberPillText,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        else -> {
            Text(
                text = "—",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = kw(0xFFCBD5E1)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StaffDetailsApprovalScreen(
    staff: StaffProfileItem,
    onSaveAndApprove: (customDisplayName: String, role: Role, pin: String, allowBiometric: Boolean, isActive: Boolean) -> Unit,
    onRejectOrRemove: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    BackHandler { onBack() }

    var customDisplayName by remember(staff.id) {
        mutableStateOf(staff.displayName)
    }
    var selectedRole by remember(staff.id) { mutableStateOf(staff.role) }
    var pinDigits by remember(staff.id) {
        mutableStateOf(if (staff.hasPin) staff.pinValue.ifBlank { "1234" } else "")
    }
    var revealGeneratedPin by remember { mutableStateOf(false) }
    var allowBiometric by remember(staff.id) { mutableStateOf(staff.allowBiometric) }
    var isActive by remember(staff.id) { mutableStateOf(staff.isActive) }

    val pinGeneratedToastTemplate = tr("Generated 4-digit PIN: %s", "नया 4-अंकीय पिन: %s")

    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = StaffBrandGreen,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = kw(0xFFD1D5DB),
        uncheckedBorderColor = Color.Transparent
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlatDarkBg)
    ) {
        // Top Header: Back button + "Staff details" / "Review, name & assign role"
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
                    .testTag("staff_details_back_btn"),
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
                    text = tr("Staff details", "स्टाफ विवरण"),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextPrimary
                )
                Text(
                    text = tr("Review, name & assign role", "समीक्षा करें, नाम दें और भूमिका सौंपें"),
                    fontSize = 13.sp,
                    color = FlatTextSecondary
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Card 1: From Google & this phone (Auto — read-only, fetched via app)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr("From Google & this phone", "Google और इस फ़ोन से"),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = StaffCashierPillBg
                        ) {
                            Text(
                                text = tr("Auto", "ऑटो"),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    StaffTelemetryRow(
                        label = tr("Registered name", "पंजीकृत नाम"),
                        value = staff.registeredName.ifBlank { staff.displayName }
                    )
                    HorizontalDivider(color = kw(0xFFF1F5F9), modifier = Modifier.padding(vertical = 10.dp))

                    StaffTelemetryRow(
                        label = tr("Gmail", "जीमेल"),
                        value = staff.email.ifBlank { tr("Linked on Google Sign-In", "Google साइन-इन पर लिंक होगा") }
                    )
                    HorizontalDivider(color = kw(0xFFF1F5F9), modifier = Modifier.padding(vertical = 10.dp))

                    val modelWithOs = when {
                        staff.deviceModel.isBlank() -> tr("Detected on device login", "डिवाइस लॉगिन पर पता चलेगा")
                        staff.androidVersion.isNotBlank() && !staff.deviceModel.contains("Android", ignoreCase = true) ->
                            "${staff.deviceModel} · ${staff.androidVersion}"
                        else -> staff.deviceModel
                    }
                    StaffTelemetryRow(
                        label = tr("Mobile model", "मोबाइल मॉडल"),
                        value = modelWithOs
                    )
                    HorizontalDivider(color = kw(0xFFF1F5F9), modifier = Modifier.padding(vertical = 10.dp))

                    StaffTelemetryRow(
                        label = tr("Joined", "जुड़े"),
                        value = staff.joinedLabel
                    )
                }
            }

            // Section: CUSTOM NAME (SHOWN IN APP, BILLS & AUDIT)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = tr(
                        "CUSTOM NAME (SHOWN IN APP, BILLS & AUDIT)",
                        "कस्टम नाम (ऐप, बिल और ऑडिट में दिखाया गया)"
                    ),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextSecondary,
                    letterSpacing = 0.6.sp
                )

                OutlinedTextField(
                    value = customDisplayName,
                    onValueChange = { customDisplayName = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = FlatDarkCard,
                        unfocusedContainerColor = FlatDarkCard,
                        focusedBorderColor = StaffBrandGreen,
                        unfocusedBorderColor = kw(0xFFCBD5E1)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("staff_custom_name_input")
                )

                Text(
                    text = tr(
                        "Leave blank to use the registered name, ${staff.registeredName.ifBlank { staff.displayName }}.",
                        "पंजीकृत नाम ${staff.registeredName.ifBlank { staff.displayName }} का उपयोग करने के लिए खाली छोड़ दें।"
                    ),
                    fontSize = 12.5.sp,
                    color = FlatTextSecondary
                )
            }

            // Section: ROLE (3 Radio Cards: Manager, Cashier, Admin)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = tr("ROLE", "भूमिका"),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextSecondary,
                    letterSpacing = 0.6.sp
                )

                StaffRoleSelectionCard(
                    role = Role.MANAGER,
                    selected = selectedRole == Role.MANAGER,
                    description = tr(
                        "Runs the shop day to day. Can edit bills, stock and suppliers.",
                        "दुकान का रोज़ाना काम संभालता है। बिल, स्टॉक और सप्लायर संपादित कर सकता है।"
                    ),
                    capabilities = listOf(
                        tr("Edit bills", "बिल संपादन"),
                        tr("Stock in", "स्टॉक इन"),
                        tr("Suppliers", "सप्लायर"),
                        tr("Cashbook", "कैशबुक")
                    ),
                    onClick = { selectedRole = Role.MANAGER }
                )

                StaffRoleSelectionCard(
                    role = Role.STAFF,
                    selected = selectedRole == Role.STAFF,
                    description = tr(
                        "Counter staff. Makes bills and collects payments only.",
                        "काउंटर स्टाफ। केवल बिल बनाता है और भुगतान प्राप्त करता है।"
                    ),
                    capabilities = listOf(
                        tr("Make bills", "बिल बनाएं"),
                        tr("Collect udhari", "उधारी वसूली"),
                        tr("View stock", "स्टॉक देखें")
                    ),
                    onClick = { selectedRole = Role.STAFF }
                )

                StaffRoleSelectionCard(
                    role = Role.ADMINISTRATOR,
                    selected = selectedRole == Role.ADMINISTRATOR,
                    description = tr(
                        "Full access. Use only for co-owners.",
                        "पूर्ण पहुँच। केवल सह-मालिकों के लिए उपयोग करें।"
                    ),
                    capabilities = listOf(
                        tr("Everything", "सब कुछ"),
                        tr("Staff & settings", "स्टाफ और सेटिंग्स")
                    ),
                    onClick = { selectedRole = Role.ADMINISTRATOR }
                )
            }

            // Section: LOGIN PIN
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tr("LOGIN PIN", "लॉगिन पिन"),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextSecondary,
                    letterSpacing = 0.6.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = pinDigits,
                        onValueChange = { input ->
                            val clean = input.filter { it.isDigit() }.take(4)
                            pinDigits = clean
                            revealGeneratedPin = true
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        decorationBox = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (i in 0 until 4) {
                                    val ch = pinDigits.getOrNull(i)
                                    val displaySymbol = when {
                                        ch == null -> ""
                                        revealGeneratedPin -> ch.toString()
                                        else -> "•"
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(52.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(FlatDarkCard)
                                            .border(1.dp, kw(0xFFCBD5E1), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = displaySymbol,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FlatTextPrimary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    )

                    Surface(
                        onClick = {
                            val randomPin = String.format(Locale.getDefault(), "%04d", SecureRandom().nextInt(10000))
                            pinDigits = randomPin
                            revealGeneratedPin = true
                            Toast.makeText(
                                context,
                                String.format(Locale.getDefault(), pinGeneratedToastTemplate, randomPin),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.5.dp, StaffBrandGreen),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("auto_generate_pin_btn")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = tr("Auto-generate", "ऑटो-जनरेट"),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = StaffBrandGreen
                            )
                        }
                    }
                }
            }

            // Toggle 1: Allow biometric login
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr("Allow biometric login", "बायोमेट्रिक लॉगिन की अनुमति दें"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                        Text(
                            text = tr("Fingerprint instead of PIN", "पिन के बजाय फिंगरप्रिंट"),
                            fontSize = 12.5.sp,
                            color = FlatTextSecondary
                        )
                    }
                    Switch(
                        checked = allowBiometric,
                        onCheckedChange = { allowBiometric = it },
                        colors = switchColors
                    )
                }
            }

            // Toggle 2: Active
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr("Active", "सक्रिय"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                        Text(
                            text = tr(
                                "Turn off to block login without removing",
                                "हटाए बिना लॉगिन ब्लॉक करने के लिए बंद करें"
                            ),
                            fontSize = 12.5.sp,
                            color = FlatTextSecondary
                        )
                    }
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it },
                        colors = switchColors
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Bottom Pinned Bar: Reject | Approve & save
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FlatDarkBg)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!staff.isCurrentUser) {
                Surface(
                    onClick = onRejectOrRemove,
                    modifier = Modifier
                        .weight(0.38f)
                        .height(52.dp)
                        .testTag("staff_details_reject_btn"),
                    shape = RoundedCornerShape(14.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.5.dp, StaffCrimson)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (staff.isPendingApproval) tr("Reject", "अस्वीकार करें") else tr("Remove", "हटाएं"),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = StaffCrimson
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val resolvedDisplay = customDisplayName.trim().ifBlank { staff.registeredName.ifBlank { staff.displayName } }
                    onSaveAndApprove(resolvedDisplay, selectedRole, pinDigits, allowBiometric, isActive)
                },
                modifier = Modifier
                    .weight(if (staff.isCurrentUser) 1f else 0.62f)
                    .height(52.dp)
                    .testTag("staff_details_approve_save_btn"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StaffBrandGreen,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = if (staff.isPendingApproval) tr("Approve & save", "मंज़ूरी दें और सेव करें") else tr("Approve & save", "सेव करें"),
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StaffTelemetryRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = FlatTextSecondary,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = FlatTextPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StaffRoleSelectionCard(
    role: Role,
    selected: Boolean,
    description: String,
    capabilities: List<String>,
    onClick: () -> Unit
) {
    val cardBg = if (selected) kw(0xFFF2FAF6) else FlatDarkCard
    val borderColor = if (selected) StaffBrandGreen else FlatDarkBorder
    val borderWidth = if (selected) 2.dp else 1.dp

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (selected) 2.dp else 1.5.dp,
                        color = if (selected) StaffBrandGreen else kw(0xFFCBD5E1),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(StaffBrandGreen)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                StaffRoleBadgePill(role = role)

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = description,
                    fontSize = 13.5.sp,
                    color = FlatTextSecondary,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    capabilities.forEach { cap ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (selected) KwSurface else kw(0xFFF1F2F6)
                        ) {
                            Text(
                                text = cap,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = kw(0xFF334155),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StaffRoleBadgePill(
    role: Role,
    compact: Boolean = false
) {
    val (bg, fg, label) = when (role) {
        Role.ADMINISTRATOR -> Triple(StaffNavyPill, Color.White, tr("Admin", "एडमिन"))
        Role.MANAGER -> Triple(StaffBluePillBg, StaffBluePillText, tr("Manager", "मैनेजर"))
        Role.STAFF -> Triple(StaffCashierPillBg, StaffCashierPillText, tr("Cashier", "कैशियर"))
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = bg
    ) {
        Text(
            text = label,
            fontSize = if (compact) 11.5.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 11.dp,
                vertical = if (compact) 3.dp else 4.dp
            )
        )
    }
}
