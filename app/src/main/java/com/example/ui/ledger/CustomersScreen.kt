package com.example.ui.ledger

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.CattleFeedRepository
import com.example.model.Permission
import com.example.model.User
import com.example.model.can
import com.example.ui.theme.CGreen
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import com.example.ui.theme.kw
import com.example.util.tr
import kotlinx.coroutines.launch
import java.math.BigDecimal

@Composable
fun CustomersScreen(
    repository: CattleFeedRepository,
    currentUser: User?,
    onCustomerClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val customers by repository.allCustomers.collectAsState(initial = emptyList())
    val settings by repository.settingsFlow.collectAsState(initial = null)

    val canViewFull = can(currentUser?.role, Permission.VIEW_CUSTOMER_FULL)
    val canAddCustomer = can(currentUser?.role, Permission.ADD_CUSTOMER)

    val watchThreshold = settings?.watchThreshold ?: BigDecimal("2000")
    val criticalThreshold = settings?.criticalThreshold ?: BigDecimal("5000")

    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var showImportCustomersFlow by remember { mutableStateOf(false) }

    if (showAddCustomerDialog) {
        AddOrEditCustomerScreen(
            existingCustomer = null,
            currentUserId = currentUser?.id.orEmpty(),
            currentUserRole = currentUser?.role,
            onCancel = { showAddCustomerDialog = false },
            onSaveCustomer = { newCust ->
                scope.launch {
                    repository.saveCustomer(newCust, actorRole = currentUser?.role)
                    showAddCustomerDialog = false
                }
            }
        )
        return
    }

    if (showImportCustomersFlow) {
        ImportCustomersFlowScreen(
            existingCustomers = customers,
            currentUserId = currentUser?.id.orEmpty(),
            onClose = { showImportCustomersFlow = false },
            onImportCustomersBatch = { batch ->
                scope.launch {
                    batch.forEach { c ->
                        try {
                            repository.saveCustomer(c, actorRole = currentUser?.role)
                        } catch (_: Exception) {}
                    }
                }
            }
        )
        return
    }

    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FlatDarkBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Unified 42dp rounded back-button header (replaces legacy TopAppBar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onBack,
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("customers_back_btn"),
                    shape = RoundedCornerShape(13.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder)
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

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr("Customers", "ग्राहक"),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${customers.size} ${tr("total customers", "कुल ग्राहक")}",
                        fontSize = 12.5.sp,
                        color = FlatTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Import action kept on standalone Settings -> Customers screen only
                if (canAddCustomer) {
                    Surface(
                        onClick = { showImportCustomersFlow = true },
                        shape = RoundedCornerShape(11.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder),
                        modifier = Modifier.testTag("customers_import_btn")
                    ) {
                        Text(
                            text = tr("Import", "इम्पोर्ट"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CGreen,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                        )
                    }
                }
            }

            // Shared unified Customers view (identical design to Ledger -> Customers)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                CustomersTab(
                    customers = customers,
                    canViewCustomerFull = canViewFull,
                    watchThreshold = watchThreshold,
                    criticalThreshold = criticalThreshold,
                    onCustomerClick = onCustomerClick,
                    onAddCustomer = { showAddCustomerDialog = true },
                    onImportCustomers = { showImportCustomersFlow = true }
                )
            }
        }

        // Unified extended green FAB matching Ledger -> Customers
        if (canAddCustomer) {
            Surface(
                onClick = { showAddCustomerDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 18.dp)
                    .height(50.dp)
                    .testTag("customers_add_fab"),
                shape = RoundedCornerShape(16.dp),
                color = kw(0xFF0E6F43),
                contentColor = Color.White,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = tr("Add customer", "ग्राहक जोड़ें"),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = tr("Customer", "ग्राहक"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
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
