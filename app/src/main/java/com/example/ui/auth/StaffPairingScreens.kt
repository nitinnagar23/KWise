package com.example.ui.auth

import com.example.ui.theme.KwSurface
import com.example.ui.theme.kw
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DevicePairingToken
import com.example.util.tr
import java.util.Locale

@Composable
internal fun StaffSixDigitCodeInput(
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = code,
        onValueChange = { input ->
            val filtered = input.filter { it.isDigit() }.take(6)
            onCodeChange(filtered)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .testTag("pairing_code_input"),
        decorationBox = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (index in 0 until 6) {
                    val charStr = code.getOrNull(index)?.toString() ?: ""
                    val isFilled = charStr.isNotEmpty()
                    val isCurrentSlot = index == code.length
                    val boxBg = if (isFilled) kw(0xFFF0FAF5) else kw(0xFFF8FAFC)
                    val boxBorder = when {
                        isFilled || isCurrentSlot -> LoginBrandGreen
                        else -> kw(0xFFCBD5E1)
                    }
                    val borderWidth = if (isFilled || isCurrentSlot) 1.5.dp else 1.dp

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(boxBg)
                            .border(borderWidth, boxBorder, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = charStr,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = LoginBrandGreen,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    )
}

@Composable
internal fun StaffConfirmAndJoinScreen(
    token: DevicePairingToken,
    adminName: String,
    staffName: String,
    staffEmail: String,
    deviceModel: String,
    isSubmitting: Boolean,
    onSwitchGoogleAccount: () -> Unit,
    onConfirmAndJoin: () -> Unit,
    onCancel: () -> Unit
) {
    BackHandler { onCancel() }

    val resolvedShopName = token.storeName.ifBlank { "KhataWise Store" }
    val shopInitial = resolvedShopName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "S"
    val roleLabel = when (token.assignedRole.uppercase(Locale.getDefault())) {
        "MANAGER" -> tr("Manager", "मैनेजर")
        else -> tr("Cashier", "कैशियर")
    }
    val resolvedAdminName = adminName.ifBlank { tr("Shop Admin", "दुकान एडमिन") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to LoginMintTop,
                        0.28f to LoginMintTop,
                        0.36f to LoginPageBg,
                        1.0f to LoginPageBg
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Top Bar: Back Button + "Join this shop"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onCancel,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("join_shop_back_btn"),
                    shape = RoundedCornerShape(13.dp),
                    color = KwSurface,
                    border = BorderStroke(1.dp, LoginCardBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = LoginNavyText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = tr("Join this shop", "इस दुकान से जुड़ें"),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = LoginNavyText
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card 1: Shop Summary Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = KwSurface,
                    border = BorderStroke(1.dp, LoginCardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(LoginNavyAvatarBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = shopInitial,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = resolvedShopName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = LoginNavyText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${tr("Admin", "एडमिन")}: $resolvedAdminName",
                                fontSize = 13.5.sp,
                                color = LoginSubtleText
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = kw(0xFFEEF0F6)
                        ) {
                            Text(
                                text = roleLabel,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = LoginNavyText,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Card 2: "We found these details"
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = KwSurface,
                    border = BorderStroke(1.dp, LoginCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Text(
                            text = tr("We found these details", "हमें ये विवरण मिले"),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = LoginNavyText
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = tr(
                                "From the Google account and phone you're using now.",
                                "उस Google खाते और फ़ोन से जिसका आप अभी उपयोग कर रहे हैं।"
                            ),
                            fontSize = 13.sp,
                            color = LoginSubtleText
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        StaffAutoDetailRow(
                            label = tr("Name", "नाम"),
                            value = staffName.ifBlank { tr("Sign in with Google", "Google से साइन इन करें") }
                        )

                        HorizontalDivider(
                            color = kw(0xFFF1F5F9),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        StaffAutoDetailRow(
                            label = tr("Gmail", "जीमेल"),
                            value = staffEmail.ifBlank { tr("Linked on Google Sign-In", "Google साइन-इन पर लिंक होगा") }
                        )

                        HorizontalDivider(
                            color = kw(0xFFF1F5F9),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        StaffAutoDetailRow(
                            label = tr("Phone", "फ़ोन"),
                            value = deviceModel
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = tr("Use a different Google account", "किसी दूसरे Google खाते का उपयोग करें"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = LoginBrandGreen,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSwitchGoogleAccount() }
                                .padding(vertical = 4.dp)
                                .testTag("use_different_google_account_btn")
                        )
                    }
                }

                // Card 3: Soft info banner
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = kw(0xFFF1F2F8)
                ) {
                    Text(
                        text = tr(
                            "Your admin will approve you and can give you a display name for bills.",
                            "आपका एडमिन आपको मंज़ूरी देगा और बिलों के लिए आपको एक डिस्प्ले नाम दे सकता है।"
                        ),
                        fontSize = 13.5.sp,
                        color = kw(0xFF334155),
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Actions: Confirm & join shop + Cancel
            Button(
                onClick = onConfirmAndJoin,
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LoginBrandGreen,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("confirm_shop_pairing_btn")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = tr("Confirm & join shop", "पुष्टि करें और दुकान से जुड़ें"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onCancel() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tr("Cancel", "रद्द करें"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = LoginSubtleText
                )
            }
        }
    }
}

@Composable
private fun StaffAutoDetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.5.sp,
            color = LoginSubtleText,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = LoginNavyText,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = kw(0xFFEEF0F6)
        ) {
            Text(
                text = tr("Auto", "ऑटो"),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = LoginSubtleText,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
internal fun StaffWaitingForApprovalScreen(
    token: DevicePairingToken,
    adminName: String,
    staffName: String,
    staffEmail: String,
    deviceModel: String,
    onApprovedOrContinue: () -> Unit,
    onCancelRequest: () -> Unit
) {
    val context = LocalContext.current
    BackHandler { onCancelRequest() }

    val resolvedAdminName = adminName.ifBlank { tr("Shop Admin", "दुकान एडमिन") }
    val resolvedStaffName = staffName.ifBlank { tr("Staff Member", "स्टाफ सदस्य") }
    val resolvedStaffEmail = staffEmail
    val resolvedDeviceModel = deviceModel
    val roleLabel = when (token.assignedRole.uppercase(Locale.getDefault())) {
        "MANAGER" -> tr("Manager", "मैनेजर")
        else -> tr("Cashier", "कैशियर")
    }
    val staffInitials = computeInitials(resolvedStaffName, fallback = "ST")

    val whatsappReminderText = tr(
        "Hi $resolvedAdminName, I sent a request to join ${token.storeName.ifBlank { "KhataWise Store" }} on KhataWise ($resolvedStaffName · $resolvedDeviceModel). Please approve my device.",
        "नमस्ते $resolvedAdminName, मैंने KhataWise पर ${token.storeName.ifBlank { "KhataWise Store" }} से जुड़ने का अनुरोध भेजा है ($resolvedStaffName · $resolvedDeviceModel)। कृपया मेरे डिवाइस को मंज़ूरी दें।"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to LoginMintTop,
                        0.32f to LoginMintTop,
                        0.33f to LoginPageBg,
                        1.0f to LoginPageBg
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.75f))

            // Amber Clock Circle Badge matching Login · Waiting for approval.pdf
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(kw(0xFFFDECCE)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(44.dp)) {
                    val strokePx = 3.6.dp.toPx()
                    val amberColor = kw(0xFFB45309)
                    drawCircle(
                        color = amberColor,
                        radius = size.minDimension / 2f - strokePx / 2f,
                        style = Stroke(width = strokePx)
                    )
                    val center = Offset(size.width / 2f, size.height / 2f)
                    // Vertical hand (12 o'clock)
                    drawLine(
                        color = amberColor,
                        start = center,
                        end = Offset(center.x, center.y - size.height * 0.24f),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round
                    )
                    // Lower-right hand (4 o'clock)
                    drawLine(
                        color = amberColor,
                        start = center,
                        end = Offset(center.x + size.width * 0.16f, center.y + size.height * 0.13f),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = tr("Waiting for approval", "मंज़ूरी की प्रतीक्षा है"),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = LoginNavyText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = tr(
                    "We sent your request to $resolvedAdminName. This screen opens automatically once you're approved.",
                    "हमने आपका अनुरोध $resolvedAdminName को भेज दिया है। मंज़ूरी मिलते ही यह स्क्रीन अपने आप खुल जाएगी।"
                ),
                fontSize = 14.5.sp,
                color = LoginSubtleText,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Staff Request Summary Card (tapping also allows entering when testing on single device)
            Surface(
                onClick = onApprovedOrContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("waiting_approval_staff_card"),
                shape = RoundedCornerShape(18.dp),
                color = KwSurface,
                border = BorderStroke(1.dp, LoginCardBorder)
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
                            .clip(CircleShape)
                            .background(LoginSoftBadgeBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = staffInitials,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = LoginNavyAvatarBg
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = resolvedStaffName,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = LoginNavyText
                        )
                        Text(
                            text = resolvedStaffEmail,
                            fontSize = 13.sp,
                            color = LoginSubtleText
                        )
                        Text(
                            text = "$resolvedDeviceModel · $roleLabel",
                            fontSize = 13.sp,
                            color = LoginSubtleText
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = kw(0xFFFDECCE)
                    ) {
                        Text(
                            text = tr("Pending", "लंबित"),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = kw(0xFFB45309),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Outlined Green Button: "Remind admin on WhatsApp"
            Surface(
                onClick = {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, whatsappReminderText)
                    }
                    try {
                        context.startActivity(Intent.createChooser(sendIntent, "Remind Admin"))
                    } catch (_: Exception) {}
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("remind_admin_whatsapp_btn"),
                shape = RoundedCornerShape(14.dp),
                color = KwSurface,
                border = BorderStroke(1.5.dp, LoginBrandGreen)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = tr("Remind admin on WhatsApp", "WhatsApp पर एडमिन को याद दिलाएं"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = LoginBrandGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Red Text Button: "Cancel request"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onCancelRequest() }
                    .padding(vertical = 8.dp)
                    .testTag("cancel_approval_request_btn"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tr("Cancel request", "अनुरोध रद्द करें"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = kw(0xFFB91C1C)
                )
            }
        }
    }
}
