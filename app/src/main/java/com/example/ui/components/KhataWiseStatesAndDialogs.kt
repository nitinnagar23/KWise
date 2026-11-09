package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.sync.FirestoreSyncState
import com.example.util.CurrencyUtils
import com.example.util.tr
import kotlinx.coroutines.delay
import java.math.BigDecimal

// Colors from KhataWise – Onboarding & states.pdf
private val StateNavyText = Color(0xFF0F1D2E)
private val StateSubtleText = Color(0xFF5A6578)
private val StateCardBorder = Color(0xFFE6E8EC)
private val StateBrandGreen = Color(0xFF0E6F43)
private val StateSoftGreen = Color(0xFFDDF3E8)
private val StateCrimsonRed = Color(0xFFB91C1C)
private val StateSoftRed = Color(0xFFFEE2E2)
private val StateAmberText = Color(0xFF78350F)
private val StateSoftAmber = Color(0xFFFEF3C7)

// ============================================================================
// 1. OFFLINE & SYNC BANNERS + HEADER SYNC STATUS DOT (Page 8 of PDF)
// ============================================================================

enum class SyncBannerKind {
    NONE,
    OFFLINE,
    SYNCING,
    SYNC_FAILED,
    BACK_ONLINE,
    BACKUP_REMINDER
}

@Composable
fun OfflineAndSyncBanner(
    syncState: FirestoreSyncState,
    pendingChangesCount: Int = 0,
    lastBackupDaysAgo: Int = 0,
    onRetrySync: () -> Unit,
    onBackupNow: () -> Unit = onRetrySync,
    modifier: Modifier = Modifier
) {
    var showBackOnlineTransient by remember { mutableStateOf(false) }
    var previousWasOfflineOrSyncing by remember { mutableStateOf(false) }

    LaunchedEffect(syncState) {
        when (syncState) {
            is FirestoreSyncState.Syncing,
            is FirestoreSyncState.Error,
            is FirestoreSyncState.Unauthenticated -> {
                previousWasOfflineOrSyncing = true
            }
            is FirestoreSyncState.Success -> {
                if (previousWasOfflineOrSyncing) {
                    showBackOnlineTransient = true
                    previousWasOfflineOrSyncing = false
                    delay(3000L)
                    showBackOnlineTransient = false
                }
            }
            else -> {}
        }
    }

    val activeBanner = when {
        syncState is FirestoreSyncState.Syncing -> SyncBannerKind.SYNCING
        syncState is FirestoreSyncState.Error -> SyncBannerKind.SYNC_FAILED
        syncState is FirestoreSyncState.Unauthenticated -> SyncBannerKind.OFFLINE
        showBackOnlineTransient -> SyncBannerKind.BACK_ONLINE
        lastBackupDaysAgo >= 7 -> SyncBannerKind.BACKUP_REMINDER
        else -> SyncBannerKind.NONE
    }

    when (activeBanner) {
        SyncBannerKind.OFFLINE -> {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("banner_offline"),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFFFFBEB),
                border = BorderStroke(1.dp, Color(0xFFFDE68A))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WifiOff,
                        contentDescription = null,
                        tint = StateAmberText,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr("You're offline", "आप ऑफ़लाइन हैं"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = StateAmberText
                        )
                        Text(
                            text = tr(
                                "Bills are saved on this phone and sync later.",
                                "बिल इस फ़ोन में सेव हो रहे हैं और बाद में सिंक होंगे।"
                            ),
                            fontSize = 13.sp,
                            color = StateAmberText.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        SyncBannerKind.SYNCING -> {
            val countLabel = if (pendingChangesCount > 0) pendingChangesCount else 3
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("banner_syncing"),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFEFF6FF),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "sync_spin")
                        val angle by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1100, easing = LinearEasing)
                            ),
                            label = "spin"
                        )
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color(0xFF1E40AF),
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(angle)
                        )
                        Text(
                            text = tr(
                                "Syncing $countLabel changes...",
                                "$countLabel बदलाव सिंक हो रहे हैं..."
                            ),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1E3A8A)
                        )
                    }

                    // Progress bar track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFDBEAFE))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.58f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF1D4ED8))
                        )
                    }
                }
            }
        }

        SyncBannerKind.SYNC_FAILED -> {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("banner_sync_failed"),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFFEF2F2),
                border = BorderStroke(1.dp, Color(0xFFFECACA))
            ) {
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
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFF991B1B),
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("Couldn't sync", "सिंक नहीं हो सका"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF7F1D1D)
                            )
                            Text(
                                text = tr(
                                    "Check your internet and try again.",
                                    "अपना इंटरनेट जांचें और पुनः प्रयास करें।"
                                ),
                                fontSize = 13.sp,
                                color = Color(0xFF991B1B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Surface(
                        onClick = onRetrySync,
                        shape = RoundedCornerShape(50),
                        color = StateCrimsonRed,
                        modifier = Modifier.testTag("banner_sync_retry_btn")
                    ) {
                        Text(
                            text = tr("Retry", "पुनः प्रयास"),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        SyncBannerKind.BACK_ONLINE -> {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("banner_back_online"),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFE6F7EE),
                border = BorderStroke(1.dp, Color(0xFFBBF7D0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = StateBrandGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = tr("Back online · All synced", "वापस ऑनलाइन · सब सिंक हो गया"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF064E3B)
                        )
                        Text(
                            text = tr("Hides by itself after 3 seconds.", "3 सेकंड बाद अपने आप हट जाएगा।"),
                            fontSize = 12.5.sp,
                            color = StateBrandGreen
                        )
                    }
                }
            }
        }

        SyncBannerKind.BACKUP_REMINDER -> {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("banner_backup_reminder"),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFFFFBEB),
                border = BorderStroke(1.dp, Color(0xFFFDE68A))
            ) {
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
                        Icon(
                            imageVector = Icons.Outlined.CloudQueue,
                            contentDescription = null,
                            tint = StateAmberText,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr(
                                    "Last backup $lastBackupDaysAgo days ago",
                                    "पिछला बैकअप $lastBackupDaysAgo दिन पहले"
                                ),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = StateAmberText
                            )
                            Text(
                                text = tr("Keep your data safe.", "अपना डेटा सुरक्षित रखें।"),
                                fontSize = 13.sp,
                                color = StateAmberText.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Surface(
                        onClick = onBackupNow,
                        shape = RoundedCornerShape(50),
                        color = StateBrandGreen,
                        modifier = Modifier.testTag("banner_backup_now_btn")
                    ) {
                        Text(
                            text = tr("Back up now", "अभी बैकअप लें"),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        SyncBannerKind.NONE -> {}
    }
}

/**
 * Header Sync Status Dot Pill (Page 8 of PDF: Synced, Syncing..., N waiting to sync, Offline, Sync failed · tap to retry)
 */
@Composable
fun HeaderSyncStatusDotPill(
    syncState: FirestoreSyncState,
    pendingSyncCount: Int = 0,
    isLightTheme: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (label, dotColor, bgColor, textColor) = when {
        syncState is FirestoreSyncState.Syncing -> HeaderSyncQuad(
            label = tr("Syncing…", "सिंक हो रहा…"),
            dotColor = Color(0xFF2563EB),
            bgColor = if (isLightTheme) Color(0xFFEFF6FF) else Color(0xFF1E3A8A),
            textColor = if (isLightTheme) Color(0xFF1E40AF) else Color(0xFF93C5FD)
        )
        syncState is FirestoreSyncState.Error -> HeaderSyncQuad(
            label = tr("Sync failed · tap to retry", "सिंक विफल · टैप करें"),
            dotColor = Color(0xFFDC2626),
            bgColor = if (isLightTheme) Color(0xFFFEF2F2) else Color(0xFF3B1219),
            textColor = if (isLightTheme) Color(0xFF991B1B) else Color(0xFFFCA5A5)
        )
        pendingSyncCount > 0 -> HeaderSyncQuad(
            label = tr("$pendingSyncCount waiting to sync", "$pendingSyncCount सिंक बाकी"),
            dotColor = Color(0xFFD97706),
            bgColor = if (isLightTheme) Color(0xFFFFFBEB) else Color(0xFF2E1C05),
            textColor = if (isLightTheme) Color(0xFF92400E) else Color(0xFFFBBF24)
        )
        syncState is FirestoreSyncState.Unauthenticated -> HeaderSyncQuad(
            label = tr("Offline", "ऑफ़लाइन"),
            dotColor = Color(0xFF64748B),
            bgColor = if (isLightTheme) Color(0xFFF1F5F9) else Color(0xFF1E293B),
            textColor = if (isLightTheme) Color(0xFF334155) else Color(0xFF94A3B8)
        )
        else -> HeaderSyncQuad(
            label = tr("Synced", "सिंक है"),
            dotColor = StateBrandGreen,
            bgColor = if (isLightTheme) StateSoftGreen else Color(0xFF064E3B),
            textColor = if (isLightTheme) StateBrandGreen else Color(0xFF6EE7B7)
        )
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bgColor,
        modifier = modifier.testTag("home_sync_status_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class HeaderSyncQuad(
    val label: String,
    val dotColor: Color,
    val bgColor: Color,
    val textColor: Color
)

// ============================================================================
// 2. LOADING STATES (Page 9 of PDF)
// ============================================================================

@Composable
private fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFFE2E8F0),
            Color(0xFFF1F5F9),
            Color(0xFFE2E8F0)
        ),
        start = Offset(translateAnim - 300f, 0f),
        end = Offset(translateAnim, 0f)
    )
}

@Composable
fun ListSkeletonView(
    rowCount: Int = 3,
    modifier: Modifier = Modifier
) {
    val brush = rememberShimmerBrush()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("list_skeleton_view"),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, StateCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            repeat(rowCount) { idx ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(brush)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(145.dp)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(brush)
                            )
                            Box(
                                modifier = Modifier
                                    .width(95.dp)
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(brush)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(50))
                            .background(brush)
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardSkeletonView(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dashboard_skeleton_view"),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, StateCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(2) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, StateCardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(80.dp)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(brush)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(115.dp)
                                        .height(22.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(brush)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(64.dp)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(brush)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OpeningOrRestoringShopCard(
    progressPercent: Int = 62,
    title: String = tr("Restoring your shop…", "आपकी दुकान रीस्टोर हो रही है…"),
    subtitle: String = tr("Getting your data from Google Drive", "Google Drive से आपका डेटा लाया जा रहा है"),
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("restoring_shop_card"),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, StateCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.khatawise_logo),
                contentDescription = "KhataWise",
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = StateNavyText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                fontSize = 13.5.sp,
                color = StateSubtleText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFE2E8F0))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((progressPercent.coerceIn(5, 100)) / 100f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(StateBrandGreen)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = tr(
                    "$progressPercent% · Please keep the app open",
                    "$progressPercent% · कृपया ऐप खुला रखें"
                ),
                fontSize = 12.5.sp,
                color = StateSubtleText
            )
        }
    }
}

@Composable
fun KhataWiseActionStateButton(
    text: String,
    loadingText: String = tr("Saving…", "सेव हो रहा है…"),
    isLoading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "khatawise_action_btn"
) {
    val active = enabled && !isLoading
    val containerColor = when {
        isLoading -> StateBrandGreen.copy(alpha = 0.85f)
        active -> StateBrandGreen
        else -> Color(0xFFE2E8F0)
    }
    val contentColor = if (active || isLoading) Color.White else Color(0xFF64748B)

    Surface(
        onClick = { if (active) onClick() },
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        modifier = modifier
            .height(50.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                val infiniteTransition = rememberInfiniteTransition(label = "btn_spin")
                val angle by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing)
                    ),
                    label = "spin"
                )
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(angle)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = loadingText,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            } else {
                Text(
                    text = text,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = contentColor
                )
            }
        }
    }
}

// ============================================================================
// 3. EMPTY STATES (Page 10 of PDF)
// ============================================================================

@Composable
fun EmptyCustomersStateCard(
    onAddCustomer: () -> Unit,
    onImportContacts: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("empty_customers_card"),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, StateCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = StateSoftGreen
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.PersonAdd,
                        contentDescription = null,
                        tint = StateBrandGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = tr("No customers yet", "अभी कोई ग्राहक नहीं है"),
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = StateNavyText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = tr(
                    "Add your first customer or import from your contacts.",
                    "अपना पहला ग्राहक जोड़ें या संपर्कों से आयात करें।"
                ),
                fontSize = 14.sp,
                color = StateSubtleText,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onAddCustomer,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StateBrandGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(46.dp)
                        .testTag("empty_add_customer_btn")
                ) {
                    Text(
                        text = tr("+ Add customer", "+ ग्राहक जोड़ें"),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                if (onImportContacts != null) {
                    Surface(
                        onClick = onImportContacts,
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, StateBrandGreen),
                        modifier = Modifier
                            .height(46.dp)
                            .testTag("empty_import_customer_btn")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 18.dp)
                        ) {
                            Text(
                                text = tr("Import", "संपर्क से जोड़ें"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = StateBrandGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyBillsTodayStateCard(
    onNewBill: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("empty_bills_today_card"),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, StateCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = Color(0xFFF1F5F9)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.ReceiptLong,
                        contentDescription = null,
                        tint = StateNavyText,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = tr("No bills today", "आज कोई बिल नहीं है"),
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = StateNavyText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = tr(
                    "Your sales will show up here as soon as you make a bill.",
                    "जैसे ही आप बिल बनाएंगे, आपकी बिक्री यहाँ दिखाई देगी।"
                ),
                fontSize = 14.sp,
                color = StateSubtleText,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onNewBill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = StateBrandGreen,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(46.dp)
                    .testTag("empty_new_bill_btn")
            ) {
                Text(
                    text = tr("+ New bill", "+ नया बिल"),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
fun EmptyStockListStateCard(
    onAddItem: () -> Unit,
    onImportExcel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("empty_stock_list_card"),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, StateCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = StateSoftAmber
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Inventory2,
                        contentDescription = null,
                        tint = StateAmberText,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = tr("Your stock list is empty", "आपकी स्टॉक सूची खाली है"),
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = StateNavyText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = tr(
                    "Add items one by one, or import them from Excel.",
                    "एक-एक करके आइटम जोड़ें, या एक्सेल से आयात करें।"
                ),
                fontSize = 14.sp,
                color = StateSubtleText,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onAddItem,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StateBrandGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(46.dp)
                        .testTag("empty_add_stock_item_btn")
                ) {
                    Text(
                        text = tr("+ Add item", "+ आइटम जोड़ें"),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                if (onImportExcel != null) {
                    Surface(
                        onClick = onImportExcel,
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, StateBrandGreen),
                        modifier = Modifier
                            .height(46.dp)
                            .testTag("empty_import_excel_btn")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 18.dp)
                        ) {
                            Text(
                                text = tr("Import Excel", "एक्सेल इम्पोर्ट"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = StateBrandGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AllCaughtUpStateCard(
    title: String = tr("All caught up", "सब पूरा हो गया"),
    subtitle: String = tr("No udhari is due today. Nice work.", "आज कोई उधारी बकाया नहीं है। बहुत बढ़िया।"),
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("all_caught_up_card"),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, StateCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = StateSoftGreen
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = StateBrandGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = StateNavyText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = StateSubtleText,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun NoSearchResultsStateCard(
    query: String,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("no_search_results_card"),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, StateCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = Color(0xFFF1F5F9)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = StateNavyText,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = tr("No results for “$query”", "“$query” के लिए कोई परिणाम नहीं"),
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = StateNavyText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = tr(
                    "Check the spelling or try a village or phone number.",
                    "स्पेलिंग जांचें या गाँव का नाम या फ़ोन नंबर खोजें।"
                ),
                fontSize = 14.sp,
                color = StateSubtleText,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                onClick = onClearSearch,
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, StateBrandGreen),
                modifier = Modifier
                    .height(46.dp)
                    .testTag("clear_search_btn")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 22.dp)
                ) {
                    Text(
                        text = tr("Clear search", "खोज साफ़ करें"),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = StateBrandGreen
                    )
                }
            }
        }
    }
}

// ============================================================================
// 4. ERROR STATES (Page 11 of PDF)
// ============================================================================

@Composable
fun NoInternetErrorCard(
    onTryAgain: () -> Unit,
    onContinueOffline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("error_no_internet_card"),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, StateCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = StateSoftAmber
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.WifiOff,
                            contentDescription = null,
                            tint = StateAmberText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr("No internet", "इंटरनेट नहीं है"),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = StateNavyText
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = tr(
                            "Check your connection. You can keep making bills offline.",
                            "अपना कनेक्शन जांचें। आप ऑफ़लाइन बिल बनाना जारी रख सकते हैं।"
                        ),
                        fontSize = 14.sp,
                        color = StateSubtleText,
                        lineHeight = 20.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onTryAgain,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StateBrandGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = tr("Try again", "पुनः प्रयास करें"),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Surface(
                    onClick = onContinueOffline,
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, StateBrandGreen),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = tr("Continue offline", "ऑफ़लाइन जारी रखें"),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = StateBrandGreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CouldNotLoadErrorCard(
    title: String = tr("Couldn't load customers", "ग्राहक लोड नहीं हो सके"),
    onRetry: () -> Unit,
    onReportProblem: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("error_could_not_load_card"),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, StateCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = StateSoftRed
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = StateCrimsonRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = StateNavyText
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = tr(
                            "Something went wrong on our side. Your data is safe.",
                            "कुछ तकनीकी समस्या हुई। आपका डेटा पूरी तरह सुरक्षित है।"
                        ),
                        fontSize = 14.sp,
                        color = StateSubtleText,
                        lineHeight = 20.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StateBrandGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = tr("Retry", "पुनः प्रयास"),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Surface(
                    onClick = onReportProblem,
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, StateBrandGreen),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = tr("Report problem", "समस्या बताएं"),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = StateBrandGreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BillSyncConflictCard(
    billNumber: String,
    thisPhoneTime: String = "11:32 AM",
    thisPhoneSummary: String = "Chana Churi 5 kg",
    counterDeviceLabel: String = "Counter 1 · 11:35 AM",
    counterDeviceSummary: String = "Chana Churi 4 kg",
    onKeepSelected: (keepThisPhone: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var keepThisPhone by remember { mutableStateOf(true) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("error_bill_sync_conflict_card"),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, StateCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE0ECF8)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Devices,
                            contentDescription = null,
                            tint = Color(0xFF1E4D8C),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr(
                            "Bill #$billNumber was changed on two phones",
                            "बिल #$billNumber दो फ़ोनों पर बदला गया"
                        ),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = StateNavyText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tr("Pick the version to keep.", "रखने के लिए एक वर्शन चुनें।"),
                        fontSize = 14.sp,
                        color = StateSubtleText
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = { keepThisPhone = true },
                    shape = RoundedCornerShape(12.dp),
                    color = if (keepThisPhone) Color(0xFFF0FDF4) else Color.White,
                    border = BorderStroke(
                        if (keepThisPhone) 1.5.dp else 1.dp,
                        if (keepThisPhone) StateBrandGreen else StateCardBorder
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "${tr("This phone", "यह फ़ोन")} · $thisPhoneTime",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = StateNavyText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = thisPhoneSummary,
                            fontSize = 13.sp,
                            color = StateSubtleText
                        )
                    }
                }

                Surface(
                    onClick = { keepThisPhone = false },
                    shape = RoundedCornerShape(12.dp),
                    color = if (!keepThisPhone) Color(0xFFF0FDF4) else Color.White,
                    border = BorderStroke(
                        if (!keepThisPhone) 1.5.dp else 1.dp,
                        if (!keepThisPhone) StateBrandGreen else StateCardBorder
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = counterDeviceLabel,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = StateNavyText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = counterDeviceSummary,
                            fontSize = 13.sp,
                            color = StateSubtleText
                        )
                    }
                }
            }

            Button(
                onClick = { onKeepSelected(keepThisPhone) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = StateBrandGreen,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = tr("Keep selected", "चुना हुआ रखें"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
fun ReportsAdminOnlyErrorCard(
    adminName: String = "Admin",
    onRequestAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayAdmin = adminName.trim().ifBlank { "Admin" }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("error_reports_admin_only_card"),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, StateCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F5F9)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = StateNavyText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr("Only Admin can see Reports", "केवल एडमिन ही रिपोर्ट देख सकते हैं"),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = StateNavyText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tr(
                            "Ask $displayAdmin to give you access.",
                            "एक्सेस पाने के लिए $displayAdmin से कहें।"
                        ),
                        fontSize = 14.sp,
                        color = StateSubtleText
                    )
                }
            }

            Surface(
                onClick = onRequestAccess,
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, StateBrandGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("request_reports_access_btn")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = tr("Request access", "एक्सेस का अनुरोध करें"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = StateBrandGreen
                    )
                }
            }
        }
    }
}

@Composable
fun BackupCouldNotFinishCard(
    onAllowDriveAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("error_backup_failed_card"),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, StateCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = StateSoftAmber
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.CloudQueue,
                            contentDescription = null,
                            tint = StateAmberText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr("Backup couldn't finish", "बैकअप पूरा नहीं हो सका"),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = StateNavyText
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = tr(
                            "Allow KhataWise to use Google Drive and try again.",
                            "KhataWise को Google Drive का उपयोग करने की अनुमति दें और पुनः प्रयास करें।"
                        ),
                        fontSize = 14.sp,
                        color = StateSubtleText,
                        lineHeight = 20.sp
                    )
                }
            }

            Button(
                onClick = onAllowDriveAccess,
                colors = ButtonDefaults.buttonColors(
                    containerColor = StateBrandGreen,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = tr("Allow Drive access", "Drive एक्सेस की अनुमति दें"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

// ============================================================================
// 5. DELETE & ACTION CONFIRMATION DIALOGS (Pages 12 & 13 of PDF)
// ============================================================================

/**
 * Page 12 Dialog 1 & 2:
 * - If pendingUdhari > 0: Shows "Can't delete <Name>" with amber warning badge + "Collect udhari" CTA
 * - If pendingUdhari <= 0: Shows "Delete <Name>?" with red trash badge + "Cancel" | "Delete" buttons
 */
@Composable
fun DeleteCustomerConfirmationDialog(
    customerName: String,
    pendingUdhari: BigDecimal,
    onCollectUdhari: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val hasPendingUdhari = pendingUdhari > BigDecimal.ZERO

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("delete_customer_confirm_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (hasPendingUdhari) {
                    // Blocked: Pending Udhari
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = StateSoftAmber
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = StateAmberText,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = tr("Can't delete $customerName", "$customerName को हटाया नहीं जा सकता"),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = StateNavyText,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val formattedDue = CurrencyUtils.formatInr(pendingUdhari)
                    Text(
                        text = tr(
                            "$formattedDue udhari is still pending. Collect it or write it off first.",
                            "$formattedDue उधारी अभी भी बाक़ी है। पहले इसे वसूल करें या बट्टे खाते में डालें।"
                        ),
                        fontSize = 14.5.sp,
                        color = StateSubtleText,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onCollectUdhari,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StateBrandGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("dialog_collect_udhari_first_btn")
                    ) {
                        Text(
                            text = tr("Collect udhari", "उधारी वसूल करें"),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("dialog_cancel_blocked_delete_btn")
                    ) {
                        Text(
                            text = tr("Cancel", "रद्द करें"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = StateSubtleText
                        )
                    }
                } else {
                    // Allowed: Zero Udhari
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = StateSoftRed
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = StateCrimsonRed,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = tr("Delete $customerName?", "$customerName को हटाएं?"),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = StateNavyText,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = tr(
                            "No pending udhari. Past bills stay in your records. This can't be undone.",
                            "कोई उधारी बकाया नहीं है। पुराने बिल आपके रिकॉर्ड में रहेंगे। इसे वापस नहीं किया जा सकता।"
                        ),
                        fontSize = 14.5.sp,
                        color = StateSubtleText,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dialog_cancel_delete_customer_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = tr("Cancel", "रद्द करें"),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = StateNavyText
                                )
                            }
                        }

                        Button(
                            onClick = onConfirmDelete,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StateCrimsonRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dialog_confirm_delete_customer_btn")
                        ) {
                            Text(
                                text = tr("Delete", "हटाएं"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Page 12 Dialog 3: Delete bill #<BillNo>?
 * Reason pills (Wrong entry, Duplicate, Other), 4-box Admin PIN input, "Return items instead", "Delete bill", "Cancel".
 */
@Composable
fun DeleteBillConfirmationDialog(
    billNumber: String,
    requireAdminPin: Boolean = true,
    onReturnItemsInstead: () -> Unit,
    onConfirmDeleteBill: (reason: String, enteredPin: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedReason by remember { mutableStateOf("Wrong entry") }
    var pinInput by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("delete_bill_confirm_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = StateSoftRed
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = StateCrimsonRed,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = tr("Delete bill #$billNumber?", "बिल #$billNumber हटाएं?"),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = StateNavyText,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = tr(
                        "Stock and cashbook entries for this bill will be reversed. Returning items keeps the history.",
                        "इस बिल के लिए स्टॉक और रोकड़ बही की प्रविष्टियां वापस कर दी जाएंगी। आइटम रिटर्न करने से इतिहास सुरक्षित रहता है।"
                    ),
                    fontSize = 14.sp,
                    color = StateSubtleText,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Reason Chips: Wrong entry | Duplicate | Other
                val reasons = listOf(
                    "Wrong entry" to tr("Wrong entry", "गलत एंट्री"),
                    "Duplicate" to tr("Duplicate", "डुप्लीकेट"),
                    "Other" to tr("Other", "अन्य")
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    reasons.forEach { (key, label) ->
                        val isSelected = selectedReason == key
                        Surface(
                            onClick = { selectedReason = key },
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) StateBrandGreen else Color(0xFFF1F2F6),
                            modifier = Modifier.testTag("delete_bill_reason_${key.lowercase().replace(" ", "_")}")
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) Color.White else StateNavyText,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                if (requireAdminPin) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = tr("Admin PIN required", "एडमिन पिन आवश्यक है"),
                        fontSize = 12.5.sp,
                        color = StateSubtleText,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 4 PIN Boxes
                    Box(contentAlignment = Alignment.Center) {
                        BasicTextField(
                            value = pinInput,
                            onValueChange = { updated ->
                                pinInput = updated.filter { it.isDigit() }.take(4)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier
                                .size(1.dp)
                                .focusRequester(focusRequester)
                                .testTag("delete_bill_pin_input")
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.clickable {
                                try {
                                    focusRequester.requestFocus()
                                } catch (_: Exception) {}
                            }
                        ) {
                            for (idx in 0 until 4) {
                                val filled = idx < pinInput.length
                                val focused = idx == pinInput.length
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White,
                                    border = BorderStroke(
                                        if (focused) 1.5.dp else 1.dp,
                                        if (focused) StateBrandGreen else Color(0xFFCBD5E1)
                                    )
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (filled) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(StateNavyText)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Outlined Green "Return items instead" Button
                Surface(
                    onClick = onReturnItemsInstead,
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, StateBrandGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dialog_return_items_instead_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = tr("Return items instead", "इसके बजाय आइटम वापस (रिटर्न) करें"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = StateBrandGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Solid Red "Delete bill" Button
                Button(
                    onClick = { onConfirmDeleteBill(selectedReason, pinInput) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StateCrimsonRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dialog_confirm_delete_bill_btn")
                ) {
                    Text(
                        text = tr("Delete bill", "बिल हटाएं"),
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dialog_cancel_delete_bill_btn")
                ) {
                    Text(
                        text = tr("Cancel", "रद्द करें"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = StateSubtleText
                    )
                }
            }
        }
    }
}

/**
 * Page 12 Dialog 4: Delete <Inventory Item>?
 * Shows stock quantity, "Hide item instead", "Cancel" | "Delete".
 */
@Composable
fun DeleteInventoryItemConfirmationDialog(
    itemName: String,
    stockSummaryText: String,
    onHideItemInstead: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("delete_inventory_item_confirm_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = StateSoftRed
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = StateCrimsonRed,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = tr("Delete $itemName?", "$itemName को हटाएं?"),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = StateNavyText,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = tr(
                        "$stockSummaryText are in stock. Deleting removes it from billing and rates.",
                        "$stockSummaryText स्टॉक में हैं। इसे हटाने से यह बिलिंग और आज के भाव से हट जाएगा।"
                    ),
                    fontSize = 14.5.sp,
                    color = StateSubtleText,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onHideItemInstead,
                    modifier = Modifier.testTag("dialog_hide_item_instead_btn")
                ) {
                    Text(
                        text = tr("Hide item instead", "इसके बजाय आइटम छुपाएं"),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = StateBrandGreen
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dialog_cancel_delete_item_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = tr("Cancel", "रद्द करें"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = StateNavyText
                            )
                        }
                    }

                    Button(
                        onClick = onConfirmDelete,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StateCrimsonRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dialog_confirm_delete_item_btn")
                    ) {
                        Text(
                            text = tr("Delete", "हटाएं"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Page 13 Dialog 1: Remove <Staff Name · Counter>?
 */
@Composable
fun RemoveStaffConfirmationDialog(
    staffDisplayTitle: String,
    deviceName: String,
    onConfirmRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val cleanDevice = deviceName.trim().ifBlank { "this device" }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("remove_staff_confirm_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = StateSoftRed
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = StateCrimsonRed,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = tr("Remove $staffDisplayTitle?", "$staffDisplayTitle को हटाएं?"),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = StateNavyText,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = tr(
                        "They will be signed out of $cleanDevice and lose access. Past bills stay.",
                        "वे $cleanDevice से साइन आउट हो जाएंगे और एक्सेस खो देंगे। उनके पुराने बिल सुरक्षित रहेंगे।"
                    ),
                    fontSize = 14.5.sp,
                    color = StateSubtleText,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dialog_cancel_remove_staff_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = tr("Cancel", "रद्द करें"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = StateNavyText
                            )
                        }
                    }

                    Button(
                        onClick = onConfirmRemove,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StateCrimsonRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dialog_confirm_remove_staff_btn")
                    ) {
                        Text(
                            text = tr("Remove", "हटाएं"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Page 13 Dialog 2: Unpair <Counter Name>?
 */
@Composable
fun UnpairDeviceConfirmationDialog(
    counterTitle: String,
    deviceModel: String,
    onConfirmUnpair: () -> Unit,
    onDismiss: () -> Unit
) {
    val cleanModel = deviceModel.trim().ifBlank { counterTitle }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("unpair_device_confirm_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = StateSoftRed
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = StateCrimsonRed,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = tr("Unpair $counterTitle?", "$counterTitle को अनपेयर करें?"),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = StateNavyText,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = tr(
                        "$cleanModel will be signed out. Bills not synced yet are sent first.",
                        "$cleanModel साइन आउट हो जाएगा। जो बिल अभी तक सिंक नहीं हुए हैं वे पहले भेजे जाएंगे।"
                    ),
                    fontSize = 14.5.sp,
                    color = StateSubtleText,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dialog_cancel_unpair_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = tr("Cancel", "रद्द करें"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = StateNavyText
                            )
                        }
                    }

                    Button(
                        onClick = onConfirmUnpair,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StateCrimsonRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dialog_confirm_unpair_btn")
                    ) {
                        Text(
                            text = tr("Unpair", "अनपेयर करें"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Page 13 Dialog 3: Log out of KhataWise?
 * Shows pending changes banner, "Sync & log out" green CTA, "Log out anyway" red text button, "Cancel".
 */
@Composable
fun LogoutKhataWiseConfirmationDialog(
    pendingSyncChanges: Int = 3,
    onSyncAndLogout: () -> Unit,
    onLogoutAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("logout_khatawise_confirm_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = StateSoftAmber
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = StateAmberText,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = tr("Log out of KhataWise?", "KhataWise से लॉग आउट करें?"),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = StateNavyText,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = tr(
                        "You'll need Google sign-in to open the shop again.",
                        "दुकान फिर से खोलने के लिए आपको Google साइन-इन की आवश्यकता होगी।"
                    ),
                    fontSize = 14.5.sp,
                    color = StateSubtleText,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                if (pendingSyncChanges > 0) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFF7ED)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = tr(
                                    "$pendingSyncChanges changes are still waiting to sync",
                                    "$pendingSyncChanges बदलाव अभी भी सिंक होने बाकी हैं"
                                ),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = StateAmberText,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onSyncAndLogout,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StateBrandGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("dialog_sync_and_logout_btn")
                ) {
                    Text(
                        text = tr("Sync & log out", "सिंक करें और लॉग आउट करें"),
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onLogoutAnyway,
                    modifier = Modifier.testTag("dialog_logout_anyway_btn")
                ) {
                    Text(
                        text = tr("Log out anyway", "फिर भी लॉग आउट करें"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = StateCrimsonRed
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dialog_cancel_logout_btn")
                ) {
                    Text(
                        text = tr("Cancel", "रद्द करें"),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = StateSubtleText
                    )
                }
            }
        }
    }
}

/**
 * Page 13 Dialog 4: Reset all shop data?
 * Requires typing DELETE to enable the Reset button.
 */
@Composable
fun ResetAllShopDataConfirmationDialog(
    onConfirmReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmText by remember { mutableStateOf("") }
    val isConfirmed = confirmText.trim().equals("DELETE", ignoreCase = true)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("reset_shop_data_confirm_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = StateSoftRed
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = StateCrimsonRed,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = tr("Reset all shop data?", "दुकान का सारा डेटा रीसेट करें?"),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = StateNavyText,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = tr(
                        "Every bill, customer and entry is deleted from this phone and your Google backup. This can't be undone.",
                        "हर बिल, ग्राहक और एंट्री इस फ़ोन और आपके Google बैकअप से हटा दी जाएगी। इसे वापस नहीं किया जा सकता।"
                    ),
                    fontSize = 14.sp,
                    color = StateSubtleText,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = tr("TYPE DELETE TO CONFIRM", "पुष्टि करने के लिए DELETE लिखें"),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = StateSubtleText,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                ) {
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp)
                    ) {
                        if (confirmText.isEmpty()) {
                            Text(
                                text = "DELETE",
                                fontSize = 15.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        BasicTextField(
                            value = confirmText,
                            onValueChange = { confirmText = it.uppercase().take(10) },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = StateNavyText
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reset_shop_type_delete_input")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dialog_cancel_reset_shop_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = tr("Cancel", "रद्द करें"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = StateNavyText
                            )
                        }
                    }

                    Surface(
                        onClick = { if (isConfirmed) onConfirmReset() },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isConfirmed) StateCrimsonRed else Color(0xFFE5A3A3),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dialog_confirm_reset_shop_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = tr("Reset", "रीसेट करें"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
