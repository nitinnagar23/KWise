package com.example.ui.components

import com.example.ui.theme.CGreenPrimary
import com.example.ui.theme.kw
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.PairingRepository
import com.example.model.DevicePairingToken
import com.example.ui.theme.CGreen
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatTextMuted
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DevicePairingDialog(
    adminUid: String,
    storeName: String,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pairingRepository = remember { PairingRepository(context) }

    var selectedRole by remember { mutableStateOf("CASHIER") } // CASHIER or MANAGER
    var currentToken by remember { mutableStateOf<DevicePairingToken?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var remainingMillis by remember { mutableLongStateOf(0L) }

    // 1-second countdown ticker
    LaunchedEffect(currentToken) {
        val token = currentToken ?: return@LaunchedEffect
        while (true) {
            val remaining = token.expiresAt - System.currentTimeMillis()
            remainingMillis = if (remaining > 0) remaining else 0
            if (remainingMillis <= 0) break
            delay(1000L)
        }
    }

    // Observe real-time claiming status from staff device
    val liveToken by remember(currentToken?.code) {
        if (currentToken != null) {
            pairingRepository.listenToPairingToken(currentToken!!.code)
        } else {
            kotlinx.coroutines.flow.flowOf(null)
        }
    }.collectAsState(initial = currentToken)

    val activeToken = liveToken ?: currentToken
    val isClaimed = activeToken?.isClaimedOrUsed() == true
    val isExpired = remainingMillis <= 0 && !isClaimed

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag("device_pairing_dialog"),
        containerColor = FlatDarkCard,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isClaimed) CGreen.copy(alpha = 0.2f) else kw(0xFF06B6D4).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isClaimed) Icons.Default.CheckCircle else Icons.Default.Smartphone,
                        contentDescription = null,
                        tint = if (isClaimed) CGreen else kw(0xFF06B6D4),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = if (isClaimed) "Device Paired!" else "Link Staff / Counter Device",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary
                    )
                    Text(
                        text = "10-Minute Secure Pairing PIN",
                        style = MaterialTheme.typography.labelSmall,
                        color = FlatTextMuted
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(24.dp)
                            .size(36.dp),
                        color = kw(0xFF06B6D4)
                    )
                    Text("Generating secure pairing key...", color = FlatTextMuted)
                } else if (isClaimed) {
                    // Success state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CGreen.copy(alpha = 0.12f))
                            .border(1.dp, CGreen.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = CGreen,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Device Successfully Connected!",
                                fontWeight = FontWeight.Bold,
                                color = CGreen,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Paired with ${activeToken?.claimedByName ?: "Staff Phone"} (${activeToken?.assignedRole ?: "CASHIER"})",
                                color = FlatTextPrimary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    // Role selection chips (before claimed)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedRole == "CASHIER",
                            onClick = { selectedRole = "CASHIER" },
                            label = {
                                Text(
                                    text = "Cashier (Sales Only)",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = kw(0xFF06B6D4).copy(alpha = 0.2f),
                                selectedLabelColor = kw(0xFF06B6D4)
                            )
                        )
                        FilterChip(
                            selected = selectedRole == "MANAGER",
                            onClick = { selectedRole = "MANAGER" },
                            label = {
                                Text(
                                    text = "Manager (Full Access)",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = kw(0xFF10B981).copy(alpha = 0.2f),
                                selectedLabelColor = kw(0xFF10B981)
                            )
                        )
                    }

                    if (activeToken == null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Counter pairing codes expire in 10 minutes. Click below when ready to pair a staff counter phone.",
                            fontSize = 12.sp,
                            color = FlatTextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    isGenerating = true
                                    try {
                                        currentToken = pairingRepository.generatePairingCode(adminUid, storeName, selectedRole)
                                        remainingMillis = PairingRepository.PAIRING_EXPIRY_MS
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Failed to generate code: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isGenerating = false
                                    }
                                }
                            },
                            enabled = !isGenerating,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0891B2),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Generating...")
                            } else {
                                Icon(Icons.Default.Smartphone, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Generate Counter Code")
                            }
                        }
                    } else {
                        // QR Code Display
                        activeToken.code.let { code ->
                            QrCodeView(
                                data = "khatawise://pair?code=$code&store=$storeName",
                                size = 170.dp
                            )
                        }

                        // 8-digit PIN Box
                        val displayPin = activeToken.code.let {
                            when (it.length) {
                                8 -> "${it.substring(0, 4)}  ${it.substring(4)}"
                                6 -> "${it.substring(0, 3)}  ${it.substring(3)}"
                                else -> it
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(FlatDarkBg)
                                .border(1.dp, FlatDarkBorder, RoundedCornerShape(12.dp))
                                .padding(vertical = 10.dp, horizontal = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "8-DIGIT PAIRING PIN",
                                        fontSize = 11.sp,
                                        letterSpacing = 1.sp,
                                        color = FlatTextMuted
                                    )
                                    Text(
                                        text = displayPin,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 2.sp,
                                        color = if (isExpired) kw(0xFFEF4444) else kw(0xFF06B6D4)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("KhataWise Pairing PIN", activeToken.code))
                                        Toast.makeText(context, "Pairing PIN copied: ${activeToken.code}", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy PIN",
                                        tint = FlatTextMuted
                                    )
                                }
                            }
                        }

                        // 10-minute countdown indicator
                        val minutes = (remainingMillis / 1000) / 60
                        val seconds = (remainingMillis / 1000) % 60
                        val timeFormatted = String.format("%02d:%02d", minutes, seconds)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = if (isExpired) kw(0xFFEF4444) else kw(0xFFF59E0B),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isExpired) "Code expired (10 min timeout)" else "Expires in $timeFormatted",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isExpired) kw(0xFFEF4444) else kw(0xFFF59E0B)
                            )
                        }

                        if (isExpired) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isGenerating = true
                                        try {
                                            currentToken = pairingRepository.generatePairingCode(adminUid, storeName, selectedRole)
                                            remainingMillis = PairingRepository.PAIRING_EXPIRY_MS
                                        } finally {
                                            isGenerating = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0891B2),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Generate Fresh Code")
                            }
                        } else {
                            Text(
                                text = "Counter phone opens KhataWise -> 'Link Staff Device' -> enters this 8-digit PIN.",
                                fontSize = 11.sp,
                                color = FlatTextMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismissRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isClaimed) CGreenPrimary else Color(0xFF1E293B),
                    contentColor = Color.White
                )
            ) {
                Text(if (isClaimed) "Done" else "Close")
            }
        }
    )
}
