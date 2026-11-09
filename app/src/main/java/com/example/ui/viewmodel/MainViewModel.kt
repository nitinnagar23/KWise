package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PashuAaharApp
import com.example.data.repository.CattleFeedRepository
import com.example.model.AppSettings
import com.example.model.BillAuditLog
import com.example.model.Customer
import com.example.model.PaymentEntry
import com.example.model.Product
import com.example.model.PurchaseInvoice
import com.example.model.SaleEntry
import com.example.model.SupplierPayment
import com.example.model.User
import com.example.sync.FirestoreSyncState
import com.example.util.CustomerInsight
import com.example.util.ReorderSuggestion
import com.example.util.ShopAnalytics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository: CattleFeedRepository = (application as PashuAaharApp).repository
    private val syncEngine = (application as PashuAaharApp).firestoreSyncEngine

    val activeUser: StateFlow<User?> = repository.activeUserFlow
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val settings: StateFlow<AppSettings?> = repository.settingsFlow
        .catch { emit(AppSettings()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val products: StateFlow<List<Product>> = repository.allProducts
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<Customer>> = repository.allCustomers
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sales: StateFlow<List<SaleEntry>> = repository.allSales
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val payments: StateFlow<List<PaymentEntry>> = repository.allPayments
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val purchaseInvoices: StateFlow<List<PurchaseInvoice>> = repository.allPurchaseInvoices
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val supplierPayments: StateFlow<List<SupplierPayment>> = repository.allSupplierPayments
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val billAuditLogs: StateFlow<List<BillAuditLog>> = repository.allBillAuditLogs
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncState: StateFlow<FirestoreSyncState> = syncEngine.syncState

    // Dynamic clock ticker so startOfToday automatically rolls over past midnight
    private val _nowMillis = MutableStateFlow(System.currentTimeMillis())
    val nowMillis: StateFlow<Long> = _nowMillis.asStateFlow()

    val reorderSuggestions: StateFlow<List<ReorderSuggestion>> = combine(
        products,
        sales,
        _nowMillis
    ) { prodList, saleList, now ->
        try {
            ShopAnalytics.computeReorderSuggestions(prodList, saleList, now)
        } catch (_: Throwable) {
            emptyList()
        }
    }.catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customerInsights: StateFlow<List<CustomerInsight>> = combine(
        customers,
        sales,
        payments,
        _nowMillis
    ) { custList, saleList, payList, now ->
        try {
            ShopAnalytics.computeCustomerInsights(custList, saleList, payList, now)
        } catch (_: Throwable) {
            emptyList()
        }
    }.catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            try {
                repository.getActiveUser()
            } catch (e: Throwable) {
                Log.w("MainViewModel", "Error loading active user: ${e.message}")
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(60_000L) // Refresh every minute so midnight rollover is never stale
                _nowMillis.value = System.currentTimeMillis()
            }
        }
    }

    fun refreshClock() {
        _nowMillis.value = System.currentTimeMillis()
    }

    fun toggleLanguage() {
        viewModelScope.launch {
            try {
                val currentLang = settings.value?.language ?: "en"
                val nextLang = if (currentLang == "hi") "en" else "hi"
                repository.updateLanguage(nextLang)
            } catch (e: Throwable) {
                Log.w("MainViewModel", "Error toggling language: ${e.message}")
            }
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            try {
                repository.updateLanguage(lang)
            } catch (e: Throwable) {
                Log.w("MainViewModel", "Error setting language: ${e.message}")
            }
        }
    }

    fun triggerCloudSync() {
        try {
            syncEngine.triggerSync()
        } catch (_: Throwable) {}
    }
}
