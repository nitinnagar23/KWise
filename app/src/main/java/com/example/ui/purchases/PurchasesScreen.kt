package com.example.ui.purchases

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.PashuAaharApp
import com.example.data.repository.CattleFeedRepository
import com.example.model.PurchaseInvoice
import com.example.model.SupplierKhataSummary
import com.example.model.SupplierPayment
import com.example.model.User
import com.example.ui.theme.CGreen
import com.example.ui.theme.CRedUdhari
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatDarkInput
import com.example.ui.theme.FlatDarkInputBorder
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import com.example.util.BiometricAuthManager
import com.example.util.CurrencyUtils
import com.example.util.findFragmentActivity
import com.example.util.tr
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private enum class SuppliersSubScreen {
    MAIN_TABS,
    SUPPLIER_KHATA,
    ADD_EDIT_SUPPLIER,
    NEW_PURCHASE_BILL,
    PAY_SUPPLIER,
    SUPPLIER_STATEMENT
}

@Composable
fun PurchasesScreen(
    repository: CattleFeedRepository,
    currentUser: User?,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isUnlocked by PashuAaharApp.isPurchasesUnlocked.collectAsState()

    // Data streams
    val purchaseInvoices by repository.allPurchaseInvoices.collectAsState(initial = emptyList())
    val legacyPurchases by repository.allPurchases.collectAsState(initial = emptyList())
    val supplierPayments by repository.allSupplierPayments.collectAsState(initial = emptyList())
    val rawSupplierSummaries by repository.supplierSummaries.collectAsState(initial = emptyList())
    val products by repository.allProducts.collectAsState(initial = emptyList())
    val settings by repository.settingsFlow.collectAsState(initial = null)

    // Metadata store (city, contactPerson, phones with tags, creditPeriodDays, itemsSupplied, openingBalance)
    var metadataVersion by remember { mutableIntStateOf(0) }
    val metadataMap = remember(metadataVersion) {
        SupplierMetadataStore.loadAll(context)
    }

    // Merge repository supplier summaries with saved SupplierMetadataStore profiles (including opening balances)
    val supplierSummaries = remember(rawSupplierSummaries, metadataMap) {
        val map = mutableMapOf<String, SupplierKhataSummary>()
        rawSupplierSummaries.forEach { s ->
            val key = s.wholesalerName.trim().lowercase(Locale.ROOT)
            val meta = metadataMap[key]
            val openBal = meta?.openingBalance ?: BigDecimal.ZERO
            val phone = s.wholesalerPhone.ifBlank { meta?.primaryPhone ?: "" }
            val existing = map[key]
            if (existing == null) {
                val totalPurch = s.totalPurchases.add(openBal)
                val bal = totalPurch.subtract(s.totalPaid).max(BigDecimal.ZERO)
                map[key] = s.copy(
                    wholesalerPhone = phone,
                    totalPurchases = totalPurch,
                    balancePayable = bal
                )
            } else {
                val mergedPurch = existing.totalPurchases.add(s.totalPurchases)
                val mergedPaid = existing.totalPaid.add(s.totalPaid)
                val mergedBal = mergedPurch.subtract(mergedPaid).max(BigDecimal.ZERO)
                map[key] = existing.copy(
                    wholesalerPhone = existing.wholesalerPhone.ifBlank { phone },
                    totalPurchases = mergedPurch,
                    totalPaid = mergedPaid,
                    balancePayable = mergedBal,
                    invoiceCount = existing.invoiceCount + s.invoiceCount,
                    lastTransactionDate = maxOf(existing.lastTransactionDate, s.lastTransactionDate)
                )
            }
        }
        metadataMap.forEach { (key, meta) ->
            if (!map.containsKey(key)) {
                val openBal = meta.openingBalance.max(BigDecimal.ZERO)
                map[key] = SupplierKhataSummary(
                    wholesalerName = meta.firmName,
                    wholesalerPhone = meta.primaryPhone,
                    totalPurchases = openBal,
                    totalPaid = BigDecimal.ZERO,
                    balancePayable = openBal,
                    invoiceCount = 0,
                    lastTransactionDate = 0L
                )
            }
        }
        map.values.sortedByDescending { it.balancePayable }
    }

    // Navigation & dialog states
    // 0 -> Home, 1 -> Suppliers, 2 -> Bills, 3 -> Payments
    var selectedTab by remember { mutableIntStateOf(0) }
    var directoryInitialFilter by remember { mutableStateOf("All") }
    var currentSubScreen by remember { mutableStateOf(SuppliersSubScreen.MAIN_TABS) }
    var selectedSupplierNameForKhata by remember { mutableStateOf<String?>(null) }
    var supplierMetadataToEdit by remember { mutableStateOf<SupplierProfileMetadata?>(null) }
    var preselectedSupplierForNewBill by remember { mutableStateOf<String?>(null) }
    var preselectedSupplierForPayment by remember { mutableStateOf<SupplierKhataSummary?>(null) }
    var returnToKhataAfterSubScreen by remember { mutableStateOf(false) }

    var selectedInvoiceForDetails by remember { mutableStateOf<PurchaseInvoice?>(null) }
    var authError by remember { mutableStateOf<String?>(null) }

    // Staff access check
    if (!com.example.model.can(currentUser?.role, com.example.model.Permission.ACCESS_SUPPLIERS)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FlatDarkBg)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(FlatDarkCard)
                    .border(1.dp, FlatDarkBorder, RoundedCornerShape(14.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = CRedUdhari,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Access restricted",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = FlatTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Purchases and wholesaler ledger is restricted to managers and administrators only.",
                        color = FlatTextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        return
    }

    // Biometric PIN check
    if (!isUnlocked) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(FlatDarkBg)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(FlatDarkCard)
                    .border(1.dp, FlatDarkBorder, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(FlatDarkInput)
                            .border(1.dp, FlatDarkInputBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = CGreen,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Protected financial data",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = FlatTextPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Wholesale purchase invoices, transport charges, and supplier ledger are encrypted and require biometric or device PIN verification.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FlatTextSecondary,
                        textAlign = TextAlign.Center
                    )

                    if (authError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = authError!!,
                            color = CRedUdhari,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            val activity = context.findFragmentActivity()
                            if (activity != null) {
                                BiometricAuthManager.authenticate(
                                    activity = activity,
                                    title = "Unlock purchases and payables",
                                    subtitle = "Verify your fingerprint or lock screen PIN",
                                    onSuccess = {
                                        PashuAaharApp.setPurchasesUnlocked(true)
                                        authError = null
                                    },
                                    onError = { err ->
                                        authError = err
                                    }
                                )
                            } else {
                                PashuAaharApp.setPurchasesUnlocked(true)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SupGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("unlock_purchases_btn")
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock ledger", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        return
    }

    val existingWholesalerNames = remember(supplierSummaries, legacyPurchases, metadataMap) {
        val names = mutableSetOf<String>()
        supplierSummaries.forEach { names.add(it.wholesalerName) }
        legacyPurchases.forEach { names.add(it.wholesalerName) }
        names
            .filter { name ->
                val meta = metadataMap[name.trim().lowercase(Locale.ROOT)]
                meta?.isActiveSupplier != false
            }
            .sorted()
    }

    // Active sub-screens
    when (currentSubScreen) {
        SuppliersSubScreen.SUPPLIER_KHATA -> {
            val activeSummary = remember(supplierSummaries, selectedSupplierNameForKhata) {
                supplierSummaries.find {
                    it.wholesalerName.equals(selectedSupplierNameForKhata ?: "", ignoreCase = true)
                } ?: selectedSupplierNameForKhata?.let { SupplierKhataSummary(wholesalerName = it) }
            }
            if (activeSummary != null) {
                val meta = metadataMap[activeSummary.wholesalerName.trim().lowercase(Locale.ROOT)]
                SupplierKhataScreen(
                    summary = activeSummary,
                    repository = repository,
                    metadata = meta,
                    onBack = { currentSubScreen = SuppliersSubScreen.MAIN_TABS },
                    onEditSupplier = {
                        supplierMetadataToEdit = meta ?: SupplierProfileMetadata(
                            firmName = activeSummary.wholesalerName,
                            phones = if (activeSummary.wholesalerPhone.isNotBlank()) {
                                listOf(SupplierPhoneItem(activeSummary.wholesalerPhone, "Owner"))
                            } else emptyList()
                        )
                        returnToKhataAfterSubScreen = true
                        currentSubScreen = SuppliersSubScreen.ADD_EDIT_SUPPLIER
                    },
                    onNewPurchaseBill = {
                        preselectedSupplierForNewBill = activeSummary.wholesalerName
                        returnToKhataAfterSubScreen = true
                        currentSubScreen = SuppliersSubScreen.NEW_PURCHASE_BILL
                    },
                    onPaySupplier = {
                        preselectedSupplierForPayment = activeSummary
                        returnToKhataAfterSubScreen = true
                        currentSubScreen = SuppliersSubScreen.PAY_SUPPLIER
                    },
                    onOpenStatement = {
                        currentSubScreen = SuppliersSubScreen.SUPPLIER_STATEMENT
                    }
                )
            } else {
                currentSubScreen = SuppliersSubScreen.MAIN_TABS
            }
        }

        SuppliersSubScreen.ADD_EDIT_SUPPLIER -> {
            val toastSavedMsg = tr("Supplier saved", "सप्लायर सेव किया गया")
            val toastDeletedMsg = tr("Supplier deleted", "सप्लायर हटाया गया")
            val editingSummary = remember(supplierSummaries, supplierMetadataToEdit) {
                val targetName = supplierMetadataToEdit?.firmName ?: ""
                supplierSummaries.find { it.wholesalerName.equals(targetName, ignoreCase = true) }
            }
            AddEditSupplierScreen(
                existingMetadata = supplierMetadataToEdit,
                allProducts = products,
                balancePayable = editingSummary?.balancePayable ?: BigDecimal.ZERO,
                hasPurchaseBills = (editingSummary?.invoiceCount ?: 0) > 0,
                onBack = {
                    currentSubScreen = if (returnToKhataAfterSubScreen && selectedSupplierNameForKhata != null) {
                        SuppliersSubScreen.SUPPLIER_KHATA
                    } else {
                        SuppliersSubScreen.MAIN_TABS
                    }
                },
                onSaveSupplier = { savedMeta ->
                    SupplierMetadataStore.saveProfile(
                        context = context,
                        profile = savedMeta,
                        previousFirmName = supplierMetadataToEdit?.firmName
                    )
                    metadataVersion++
                    selectedSupplierNameForKhata = savedMeta.firmName
                    Toast.makeText(context, toastSavedMsg, Toast.LENGTH_SHORT).show()
                    currentSubScreen = if (returnToKhataAfterSubScreen) {
                        SuppliersSubScreen.SUPPLIER_KHATA
                    } else {
                        selectedTab = 1
                        SuppliersSubScreen.MAIN_TABS
                    }
                },
                onDeleteSupplier = if (supplierMetadataToEdit != null) {
                    {
                        val firmToDelete = supplierMetadataToEdit?.firmName ?: ""
                        if (firmToDelete.isNotBlank()) {
                            SupplierMetadataStore.deleteProfile(context, firmToDelete)
                            metadataVersion++
                        }
                        selectedSupplierNameForKhata = null
                        Toast.makeText(context, toastDeletedMsg, Toast.LENGTH_SHORT).show()
                        selectedTab = 1
                        currentSubScreen = SuppliersSubScreen.MAIN_TABS
                    }
                } else null
            )
        }

        SuppliersSubScreen.NEW_PURCHASE_BILL -> {
            val toastBillSavedMsg = tr("Purchase bill saved", "खरीद बिल सेव किया गया")
            NewPurchaseBillScreen(
                products = products,
                existingSuppliers = existingWholesalerNames,
                preselectedSupplier = preselectedSupplierForNewBill,
                metadataMap = metadataMap,
                onBack = {
                    currentSubScreen = if (returnToKhataAfterSubScreen && selectedSupplierNameForKhata != null) {
                        SuppliersSubScreen.SUPPLIER_KHATA
                    } else {
                        SuppliersSubScreen.MAIN_TABS
                    }
                },
                onSaveInvoice = { wholesaler, phone, invNum, items, ladhan, utaar, kiraya, paid, notes ->
                    scope.launch {
                        repository.recordPurchaseInvoice(
                            wholesalerName = wholesaler,
                            wholesalerPhone = phone,
                            invoiceNumber = invNum,
                            items = items,
                            ladhan = ladhan,
                            utaar = utaar,
                            gaadiKiraya = kiraya,
                            paidAmount = paid,
                            notes = notes,
                            recordedByUserId = currentUser?.id ?: ""
                        )
                        Toast.makeText(context, toastBillSavedMsg, Toast.LENGTH_SHORT).show()
                        currentSubScreen = if (returnToKhataAfterSubScreen && selectedSupplierNameForKhata != null) {
                            SuppliersSubScreen.SUPPLIER_KHATA
                        } else {
                            SuppliersSubScreen.MAIN_TABS
                        }
                    }
                }
            )
        }

        SuppliersSubScreen.PAY_SUPPLIER -> {
            val targetSummary = preselectedSupplierForPayment
                ?: supplierSummaries.firstOrNull { it.balancePayable > BigDecimal.ZERO }
                ?: supplierSummaries.firstOrNull()
            PaySupplierFlowScreen(
                initialSummary = targetSummary,
                allSummaries = supplierSummaries,
                allInvoices = purchaseInvoices,
                metadataMap = metadataMap,
                repository = repository,
                currentUser = currentUser,
                onBack = {
                    currentSubScreen = if (returnToKhataAfterSubScreen && selectedSupplierNameForKhata != null) {
                        SuppliersSubScreen.SUPPLIER_KHATA
                    } else {
                        SuppliersSubScreen.MAIN_TABS
                    }
                },
                onViewSupplierKhata = { wholesaler ->
                    selectedSupplierNameForKhata = wholesaler
                    currentSubScreen = SuppliersSubScreen.SUPPLIER_KHATA
                },
                onDone = {
                    currentSubScreen = if (returnToKhataAfterSubScreen && selectedSupplierNameForKhata != null) {
                        SuppliersSubScreen.SUPPLIER_KHATA
                    } else {
                        SuppliersSubScreen.MAIN_TABS
                    }
                }
            )
        }

        SuppliersSubScreen.SUPPLIER_STATEMENT -> {
            val activeSummary = remember(supplierSummaries, selectedSupplierNameForKhata) {
                supplierSummaries.find {
                    it.wholesalerName.equals(selectedSupplierNameForKhata ?: "", ignoreCase = true)
                } ?: selectedSupplierNameForKhata?.let { SupplierKhataSummary(wholesalerName = it) }
            }
            if (activeSummary != null) {
                val meta = metadataMap[activeSummary.wholesalerName.trim().lowercase(Locale.ROOT)]
                SupplierStatementScreen(
                    summary = activeSummary,
                    metadata = meta,
                    repository = repository,
                    settings = settings,
                    currentUser = currentUser,
                    onBack = {
                        currentSubScreen = SuppliersSubScreen.SUPPLIER_KHATA
                    }
                )
            } else {
                currentSubScreen = SuppliersSubScreen.MAIN_TABS
            }
        }

        SuppliersSubScreen.MAIN_TABS -> {
            if (onBack != null) {
                BackHandler { onBack() }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FlatDarkBg)
            ) {
                // Shared Top Header matching Suppliers PDFs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBack != null) {
                        Surface(
                            onClick = onBack,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("suppliers_back_btn"),
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
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr("Suppliers", "सप्लायर"),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                        Text(
                            text = tr("Purchases, payments & khata", "खरीद, भुगतान और खाता"),
                            fontSize = 13.sp,
                            color = FlatTextSecondary
                        )
                    }

                    Surface(
                        onClick = {
                            // Tapping Biometric pill locks purchases if desired
                            PashuAaharApp.setPurchasesUnlocked(false)
                        },
                        shape = RoundedCornerShape(50),
                        color = SupAvatarBg
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Biometric",
                                tint = SupAvatarText,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Biometric",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SupAvatarText
                            )
                        }
                    }
                }

                // 4-Tab Row: Home | Suppliers | Bills | Payments
                val tabs = listOf(
                    Triple(0, tr("Home", "होम"), "tab_home"),
                    Triple(1, tr("Suppliers", "सप्लायर"), "tab_hisab_kitab"),
                    Triple(2, tr("Bills", "बिल"), "tab_invoices"),
                    Triple(3, tr("Payments", "भुगतान"), "tab_payments")
                )

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = FlatDarkBg,
                    contentColor = SupGreen,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = SupGreen,
                            height = 2.5.dp
                        )
                    },
                    divider = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(FlatDarkBorder)
                        )
                    }
                ) {
                    tabs.forEach { (idx, title, tag) ->
                        val isSelected = selectedTab == idx
                        Tab(
                            selected = isSelected,
                            onClick = {
                                if (idx == 1) directoryInitialFilter = "All"
                                selectedTab = idx
                            },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) SupGreen else FlatTextSecondary,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag(tag)
                        )
                    }
                }

                when (selectedTab) {
                    0 -> {
                        SuppliersHomeTabView(
                            supplierSummaries = supplierSummaries,
                            invoices = purchaseInvoices,
                            payments = supplierPayments,
                            metadataMap = metadataMap,
                            onNewPurchaseBill = {
                                preselectedSupplierForNewBill = null
                                returnToKhataAfterSubScreen = false
                                currentSubScreen = SuppliersSubScreen.NEW_PURCHASE_BILL
                            },
                            onPaySupplier = {
                                preselectedSupplierForPayment = supplierSummaries.firstOrNull { it.balancePayable > BigDecimal.ZERO }
                                returnToKhataAfterSubScreen = false
                                currentSubScreen = SuppliersSubScreen.PAY_SUPPLIER
                            },
                            onSeeAllDue = {
                                directoryInitialFilter = "You owe"
                                selectedTab = 1
                            },
                            onSeeAllPurchases = {
                                selectedTab = 2
                            },
                            onOpenSupplierKhata = { summary ->
                                selectedSupplierNameForKhata = summary.wholesalerName
                                currentSubScreen = SuppliersSubScreen.SUPPLIER_KHATA
                            },
                            onOpenInvoiceDetails = { inv ->
                                selectedInvoiceForDetails = inv
                            }
                        )
                    }

                    1 -> {
                        SuppliersDirectoryTabView(
                            supplierSummaries = supplierSummaries,
                            invoices = purchaseInvoices,
                            metadataMap = metadataMap,
                            initialFilter = directoryInitialFilter,
                            onOpenKhata = { summary ->
                                selectedSupplierNameForKhata = summary.wholesalerName
                                currentSubScreen = SuppliersSubScreen.SUPPLIER_KHATA
                            },
                            onAddSupplierClick = {
                                supplierMetadataToEdit = null
                                returnToKhataAfterSubScreen = false
                                currentSubScreen = SuppliersSubScreen.ADD_EDIT_SUPPLIER
                            }
                        )
                    }

                    2 -> {
                        PurchaseBillsTabView(
                            invoices = purchaseInvoices,
                            onInvoiceClick = { inv -> selectedInvoiceForDetails = inv },
                            onAddPurchaseBillClick = {
                                preselectedSupplierForNewBill = null
                                returnToKhataAfterSubScreen = false
                                currentSubScreen = SuppliersSubScreen.NEW_PURCHASE_BILL
                            }
                        )
                    }

                    3 -> {
                        SupplierPaymentsTabView(
                            payments = supplierPayments,
                            invoices = purchaseInvoices,
                            supplierSummaries = supplierSummaries,
                            onPaymentClick = { payment ->
                                selectedSupplierNameForKhata = payment.wholesalerName
                                currentSubScreen = SuppliersSubScreen.SUPPLIER_KHATA
                            },
                            onAddPaymentClick = {
                                preselectedSupplierForPayment = supplierSummaries.firstOrNull { it.balancePayable > BigDecimal.ZERO }
                                returnToKhataAfterSubScreen = false
                                currentSubScreen = SuppliersSubScreen.PAY_SUPPLIER
                            }
                        )
                    }
                }
            }
        }
    }

    // Invoice Details Dialog
    selectedInvoiceForDetails?.let { invoice ->
        PurchaseInvoiceDetailsDialog(
            invoice = invoice,
            onDismiss = { selectedInvoiceForDetails = null }
        )
    }
}

@Composable
private fun SuppliersHomeTabView(
    supplierSummaries: List<SupplierKhataSummary>,
    invoices: List<PurchaseInvoice>,
    payments: List<SupplierPayment>,
    metadataMap: Map<String, SupplierProfileMetadata>,
    onNewPurchaseBill: () -> Unit,
    onPaySupplier: () -> Unit,
    onSeeAllDue: () -> Unit,
    onSeeAllPurchases: () -> Unit,
    onOpenSupplierKhata: (SupplierKhataSummary) -> Unit,
    onOpenInvoiceDetails: (PurchaseInvoice) -> Unit
) {
    val shortDateFormat = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val nowMillis = remember { System.currentTimeMillis() }
    val thirtyDaysAgo = remember { nowMillis - 30L * 24L * 60L * 60L * 1000L }

    val totalPayable = remember(supplierSummaries) {
        supplierSummaries.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.balancePayable) }
    }
    val suppliersToPayList = remember(supplierSummaries) {
        supplierSummaries.filter { it.balancePayable > BigDecimal.ZERO }
    }

    val purchases30DaysTotal = remember(invoices, thirtyDaysAgo) {
        invoices.filter { it.date >= thirtyDaysAgo }
            .fold(BigDecimal.ZERO) { acc, inv -> acc.add(inv.totalInvoiceCost) }
    }
    val bills30DaysCount = remember(invoices, thirtyDaysAgo) {
        invoices.count { it.date >= thirtyDaysAgo }
    }

    val paid30DaysTotal = remember(payments, invoices, thirtyDaysAgo) {
        val paySum = payments.filter { it.date >= thirtyDaysAgo }
            .fold(BigDecimal.ZERO) { acc, p -> acc.add(p.amount) }
        val invPaidSum = invoices.filter { it.date >= thirtyDaysAgo }
            .fold(BigDecimal.ZERO) { acc, inv -> acc.add(inv.paidAmount) }
        paySum.add(invPaidSum)
    }
    val payments30DaysCount = remember(payments, invoices, thirtyDaysAgo) {
        val payCnt = payments.count { it.date >= thirtyDaysAgo }
        val invPaidCnt = invoices.count { it.date >= thirtyDaysAgo && it.paidAmount > BigDecimal.ZERO }
        (payCnt + invPaidCnt).coerceAtLeast(payments.size)
    }

    val billNoMap = remember(invoices) {
        val asc = invoices.sortedBy { it.date }
        asc.mapIndexed { idx, inv ->
            inv.id to formatShortPurchaseBillNo(inv.invoiceNumber, idx + 1)
        }.toMap()
    }

    val recentInvoices = remember(invoices) {
        invoices.sortedByDescending { it.date }.take(3)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(FlatDarkBg)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // YOU OWE SUPPLIERS Hero Card
        item {
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
                    Text(
                        text = tr("YOU OWE SUPPLIERS", "सप्लायरों को देना है"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextSecondary,
                        letterSpacing = 0.7.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = CurrencyUtils.formatInr(totalPayable),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (totalPayable > BigDecimal.ZERO) SupRed else SupGreen
                        )
                        Text(
                            text = "${suppliersToPayList.size} suppliers to pay",
                            fontSize = 13.5.sp,
                            color = FlatTextSecondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onNewPurchaseBill,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("add_purchase_fab"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SupGreen,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tr("Purchase bill", "खरीद बिल"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = onPaySupplier,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("pay_supplier_btn"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, SupGreen),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = FlatDarkCard,
                                contentColor = SupGreen
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = tr("Pay supplier", "भुगतान करें"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = SupGreen,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // PURCHASES · 30 DAYS & PAID · 30 DAYS Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = tr("PURCHASES · 30 DAYS", "खरीद · 30 दिन"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.6.sp
                        )
                        Text(
                            text = CurrencyUtils.formatInr(purchases30DaysTotal),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                        Text(
                            text = "$bills30DaysCount bills",
                            fontSize = 12.5.sp,
                            color = FlatTextSecondary
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = tr("PAID · 30 DAYS", "भुगतान · 30 दिन"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary,
                            letterSpacing = 0.6.sp
                        )
                        Text(
                            text = CurrencyUtils.formatInr(paid30DaysTotal),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = SupGreen
                        )
                        Text(
                            text = "$payments30DaysCount payments",
                            fontSize = 12.5.sp,
                            color = FlatTextSecondary
                        )
                    }
                }
            }
        }

        // Payments due section
        if (suppliersToPayList.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("Payments due", "बकाया भुगतान"),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary
                    )
                    Text(
                        text = tr("See all →", "सभी देखें →"),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = SupGreen,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSeeAllDue() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            items(suppliersToPayList.take(4), key = { "due_${it.wholesalerName}" }) { summary ->
                val meta = metadataMap[summary.wholesalerName.trim().lowercase(Locale.ROOT)]
                val creditDays = meta?.creditPeriodDays?.takeIf { it > 0 } ?: 15
                val baseDate = if (summary.lastTransactionDate > 0L) summary.lastTransactionDate else nowMillis - 10L * 24L * 60L * 60L * 1000L
                val dueDateMillis = baseDate + creditDays * 24L * 60L * 60L * 1000L
                val diffDays = ((dueDateMillis - nowMillis) / (24L * 60L * 60L * 1000L)).toInt()
                val isLate = diffDays < 0
                val pillLabel = when {
                    diffDays < -1 -> "${abs(diffDays)} days late"
                    diffDays == -1 -> "1 day late"
                    diffDays == 0 -> "Due today"
                    diffDays == 1 -> "In 1 day"
                    else -> "In $diffDays days"
                }

                Surface(
                    onClick = { onOpenSupplierKhata(summary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(SupAvatarBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = supplierInitials(summary.wholesalerName),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SupAvatarText
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = summary.wholesalerName,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Due ${shortDateFormat.format(Date(dueDateMillis))} · credit $creditDays days",
                                    fontSize = 13.sp,
                                    color = FlatTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = CurrencyUtils.formatInr(summary.balancePayable),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = SupRed
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (isLate) SupRedSoft else SupBlueSoft
                            ) {
                                Text(
                                    text = pillLabel,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isLate) SupRed else SupBlue,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recent purchases section
        if (recentInvoices.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("Recent purchases", "हाल की खरीद"),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlatTextPrimary
                    )
                    Text(
                        text = tr("See all →", "सभी देखें →"),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = SupGreen,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSeeAllPurchases() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            items(recentInvoices, key = { "recent_${it.id}" }) { inv ->
                val shortNo = billNoMap[inv.id] ?: formatShortPurchaseBillNo(inv.invoiceNumber, 1)
                PurchaseBillRowCard(
                    invoice = inv,
                    shortNo = shortNo,
                    shortDate = shortDateFormat.format(Date(inv.date)),
                    showDueInPartPaid = false,
                    onClick = { onOpenInvoiceDetails(inv) }
                )
            }
        }
    }
}
