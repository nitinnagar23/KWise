package com.example.sync

import android.content.Context
import android.util.Log
import com.example.PashuAaharApp
import com.example.R
import com.example.data.AppDatabase
import com.example.model.Customer
import com.example.model.PaymentEntry
import com.example.model.PaymentStatus
import com.example.model.Product
import com.example.model.Role
import com.example.model.SaleEntry
import com.example.model.SyncStatus
import com.example.model.User
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.Date

fun DocumentSnapshot.safeString(field: String, default: String = ""): String {
    return try {
        when (val raw = get(field)) {
            null -> default
            is String -> raw
            is Number -> raw.toString()
            is Boolean -> raw.toString()
            else -> raw.toString()
        }
    } catch (_: Throwable) {
        default
    }
}

fun DocumentSnapshot.safeNullableString(field: String): String? {
    return try {
        when (val raw = get(field)) {
            null -> null
            is String -> raw
            is Number -> raw.toString()
            is Boolean -> raw.toString()
            else -> raw.toString()
        }
    } catch (_: Throwable) {
        null
    }
}

fun DocumentSnapshot.safeLong(field: String, default: Long? = null): Long? {
    return try {
        when (val raw = get(field)) {
            null -> default
            is Number -> raw.toLong()
            is Timestamp -> raw.toDate().time
            is Date -> raw.time
            is String -> raw.trim().toLongOrNull() ?: default
            else -> default
        }
    } catch (_: Throwable) {
        default
    }
}

fun DocumentSnapshot.safeDouble(field: String, default: Double = 0.0): Double {
    return try {
        when (val raw = get(field)) {
            null -> default
            is Number -> {
                val d = raw.toDouble()
                if (d.isNaN() || d.isInfinite()) default else d
            }
            is String -> {
                val d = raw.trim().toDoubleOrNull() ?: default
                if (d.isNaN() || d.isInfinite()) default else d
            }
            else -> default
        }
    } catch (_: Throwable) {
        default
    }
}

fun DocumentSnapshot.safeBoolean(field: String, default: Boolean = false): Boolean {
    return try {
        when (val raw = get(field)) {
            null -> default
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> raw.trim().equals("true", ignoreCase = true) || raw.trim() == "1"
            else -> default
        }
    } catch (_: Throwable) {
        default
    }
}

sealed class FirestoreSyncState {
    object Idle : FirestoreSyncState()
    object Syncing : FirestoreSyncState()
    data class Success(val count: Int, val timestamp: Long = System.currentTimeMillis()) : FirestoreSyncState()
    data class Error(val message: String) : FirestoreSyncState()
    object Unauthenticated : FirestoreSyncState()
}

class FirestoreSyncEngine(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Unhandled exception in FirestoreSyncEngine scope", e)
        }
    )
) {
    private val db: FirebaseFirestore?
        get() = try {
            val dbId = context.getString(R.string.firestore_database_id).trim()
            if (dbId.isBlank() || dbId == "(default)") {
                FirebaseFirestore.getInstance()
            } else {
                FirebaseFirestore.getInstance(dbId)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "FirebaseApp not initialized yet: ${e.message}")
            null
        }

    private val localDb by lazy { AppDatabase.getInstance(context) }
    private val auth: FirebaseAuth?
        get() = try {
            Firebase.auth
        } catch (e: Throwable) {
            null
        }

    private val _syncState = MutableStateFlow<FirestoreSyncState>(FirestoreSyncState.Idle)
    val syncState: StateFlow<FirestoreSyncState> = _syncState.asStateFlow()

    private var profileListener: ListenerRegistration? = null
    private var customerListener: ListenerRegistration? = null
    private var productListener: ListenerRegistration? = null
    private var transactionListener: ListenerRegistration? = null
    private var staffListener: ListenerRegistration? = null
    private val syncMutex = kotlinx.coroutines.sync.Mutex()
    private var activeListenerUserId: String? = null

    companion object {
        private const val TAG = "KhataWiseFirestoreSync"
    }

    init {
        try {
            auth?.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                if (user != null) {
                    scope.launch {
                        try {
                            val currentSettings = localDb.settingsDao().getSettings()
                            val storeOwnerId = if (!currentSettings?.storeAdminUid.isNullOrBlank()) {
                                currentSettings!!.storeAdminUid
                            } else {
                                user.uid
                            }
                            startRealtimeListeners(storeOwnerId)
                            syncNow()
                        } catch (e: Throwable) {
                            Log.w(TAG, "Auth state listener sync error: ${e.message}", e)
                        }
                    }
                } else {
                    stopRealtimeListeners()
                    _syncState.value = FirestoreSyncState.Unauthenticated
                }
            }
        } catch (_: Throwable) {}
    }

    fun startRealtimeListeners(userId: String) {
        if (userId.isBlank()) return
        if (activeListenerUserId == userId && profileListener != null) return
        stopRealtimeListeners()
        activeListenerUserId = userId
        val firestore = db ?: return

        try {
            // 1. Realtime listener for Store Profile
            profileListener = firestore.collection("users").document(userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    scope.launch {
                        try {
                            val shopName = snapshot.safeString("shopName").ifBlank { snapshot.safeString("storeName") }
                            val shopAddress = snapshot.safeString("shopAddress")
                            val shopPhone = snapshot.safeString("shopPhone").ifBlank { snapshot.safeString("phone") }
                            val shopGstin = snapshot.safeString("shopGstin")
                            val shopUpiId = snapshot.safeString("shopUpiId")
                            val watch = snapshot.safeDouble("watchThreshold", 2000.0)
                            val critical = snapshot.safeDouble("criticalThreshold", 5000.0)

                            localDb.settingsDao().ensureDefaultSettings()
                            val current = localDb.settingsDao().getSettings()
                            if (current != null) {
                                localDb.settingsDao().updateStoreProfileFields(
                                    shopName = if (shopName.isNotBlank()) shopName else current.shopName,
                                    shopAddress = if (shopAddress.isNotBlank()) shopAddress else current.shopAddress,
                                    shopPhone = if (shopPhone.isNotBlank()) shopPhone else current.shopPhone,
                                    shopGstin = if (shopGstin.isNotBlank()) shopGstin else current.shopGstin,
                                    shopUpiId = if (shopUpiId.isNotBlank()) shopUpiId else current.shopUpiId,
                                    watchThreshold = BigDecimal.valueOf(watch),
                                    criticalThreshold = BigDecimal.valueOf(critical)
                                )
                            }
                        } catch (e: Throwable) {
                            Log.w(TAG, "Error applying store profile update", e)
                        }
                    }
                }

            // 2. Realtime listener for Remote Customers
            customerListener = firestore.collection("users").document(userId).collection("customers")
                .addSnapshotListener { snapshots, error ->
                    if (error != null || snapshots == null || snapshots.isEmpty) return@addSnapshotListener
                    scope.launch {
                        try {
                            for (doc in snapshots.documents) {
                                try {
                                    val name = doc.safeString("name")
                                    if (name.isBlank()) continue
                                    val id = doc.id
                                    val fatherName = doc.safeString("fatherName")
                                    val phone = doc.safeString("phone")
                                    val phonesJson = doc.safeString("phonesJson")
                                    val village = doc.safeString("village")
                                    val totalUdhariNum = if (doc.contains("totalUdhari")) {
                                        doc.safeDouble("totalUdhari", 0.0)
                                    } else {
                                        doc.safeDouble("currentBalance", 0.0)
                                    }
                                    val creditLimitNum = doc.safeDouble("creditLimit", 10000.0)
                                    val promiseDate = doc.safeLong("promiseDate")
                                    val createdBy = doc.safeString("createdByUserId")
                                    val createdAt = doc.safeLong("createdAt") ?: System.currentTimeMillis()
                                    val isDeleted = doc.safeBoolean("isDeleted", false)
                                    val updatedAt = doc.safeLong("updatedAt") ?: System.currentTimeMillis()

                                    val existing = localDb.customerDao().getCustomerById(id)
                                    if (existing == null) {
                                        val newCustomer = Customer(
                                            id = id,
                                            name = name,
                                            fatherName = fatherName,
                                            phone = phone,
                                            phonesJson = phonesJson,
                                            village = village,
                                            totalUdhari = BigDecimal.valueOf(totalUdhariNum),
                                            creditLimit = BigDecimal.valueOf(creditLimitNum),
                                            promiseDate = if (promiseDate != null && promiseDate > 0) promiseDate else null,
                                            createdByUserId = createdBy,
                                            createdAt = createdAt,
                                            updatedAt = updatedAt,
                                            isDeleted = isDeleted,
                                            syncStatus = SyncStatus.SYNCED
                                        )
                                        localDb.customerDao().insertCustomer(newCustomer)
                                    } else if (updatedAt > existing.updatedAt && existing.syncStatus == SyncStatus.SYNCED) {
                                        val updated = existing.copy(
                                            name = name,
                                            fatherName = if (fatherName.isNotBlank()) fatherName else existing.fatherName,
                                            phone = phone,
                                            phonesJson = if (phonesJson.isNotBlank()) phonesJson else existing.phonesJson,
                                            village = village,
                                            totalUdhari = BigDecimal.valueOf(totalUdhariNum),
                                            creditLimit = BigDecimal.valueOf(creditLimitNum),
                                            promiseDate = if (promiseDate != null && promiseDate > 0) promiseDate else existing.promiseDate,
                                            createdByUserId = if (createdBy.isNotBlank()) createdBy else existing.createdByUserId,
                                            createdAt = createdAt,
                                            updatedAt = updatedAt,
                                            isDeleted = isDeleted,
                                            syncStatus = SyncStatus.SYNCED
                                        )
                                        localDb.customerDao().updateCustomer(updated)
                                    }
                                } catch (docErr: Throwable) {
                                    Log.w(TAG, "Skipping malformed customer doc ${doc.id}: ${docErr.message}")
                                }
                            }
                        } catch (e: Throwable) {
                            Log.w(TAG, "Customer listener error", e)
                        }
                    }
                }

            // 3. Realtime listener for Remote Inventory / Products
            productListener = firestore.collection("users").document(userId).collection("inventory")
                .addSnapshotListener { snapshots, error ->
                    if (error != null || snapshots == null || snapshots.isEmpty) return@addSnapshotListener
                    scope.launch {
                        try {
                            for (doc in snapshots.documents) {
                                try {
                                    val feedName = doc.safeString("name").ifBlank { doc.safeString("feedName") }
                                    if (feedName.isBlank()) continue
                                    val id = doc.id
                                    val baseUnit = doc.safeString("baseUnit", "kg").ifBlank { "kg" }
                                    val kgPerBagNum = doc.safeDouble("kgPerBag", 50.0)
                                    val currentStockKgNum = if (doc.contains("currentStockKg")) {
                                        doc.safeDouble("currentStockKg", 0.0)
                                    } else {
                                        doc.safeDouble("stockBags", 0.0) * kgPerBagNum + doc.safeDouble("stockLooseKg", 0.0)
                                    }
                                    val sellingPrice = if (doc.contains("sellingPricePerKg")) {
                                        doc.safeDouble("sellingPricePerKg", 0.0)
                                    } else {
                                        doc.safeDouble("salePricePerKg", 0.0)
                                    }
                                    val loosePrice = doc.safeDouble("looseSellingPricePerKg", 0.0)
                                    val avgCost = doc.safeDouble("averagePurchaseCostPerKg", 0.0)
                                    val isLooseOnly = doc.safeBoolean("isLooseOnly", false)
                                    val isDeleted = doc.safeBoolean("isDeleted", false)
                                    val createdAt = doc.safeLong("createdAt") ?: System.currentTimeMillis()
                                    val updatedAt = doc.safeLong("updatedAt") ?: System.currentTimeMillis()

                                    val existing = localDb.productDao().getProductById(id)
                                    if (existing == null) {
                                        val newProd = Product(
                                            id = id,
                                            feedName = feedName,
                                            baseUnit = baseUnit,
                                            kgPerBag = BigDecimal.valueOf(kgPerBagNum),
                                            currentStockKg = BigDecimal.valueOf(currentStockKgNum),
                                            averagePurchaseCostPerKg = BigDecimal.valueOf(avgCost),
                                            sellingPricePerKg = BigDecimal.valueOf(sellingPrice),
                                            looseSellingPricePerKg = BigDecimal.valueOf(loosePrice),
                                            isLooseOnly = isLooseOnly,
                                            createdAt = createdAt,
                                            updatedAt = updatedAt,
                                            isDeleted = isDeleted,
                                            syncStatus = SyncStatus.SYNCED
                                        )
                                        localDb.productDao().insertProduct(newProd)
                                    } else if (updatedAt > existing.updatedAt && existing.syncStatus == SyncStatus.SYNCED) {
                                        val updated = existing.copy(
                                            feedName = feedName,
                                            baseUnit = baseUnit,
                                            kgPerBag = BigDecimal.valueOf(kgPerBagNum),
                                            currentStockKg = BigDecimal.valueOf(currentStockKgNum),
                                            averagePurchaseCostPerKg = BigDecimal.valueOf(avgCost),
                                            sellingPricePerKg = BigDecimal.valueOf(sellingPrice),
                                            looseSellingPricePerKg = BigDecimal.valueOf(loosePrice),
                                            isLooseOnly = isLooseOnly,
                                            updatedAt = updatedAt,
                                            isDeleted = isDeleted,
                                            syncStatus = SyncStatus.SYNCED
                                        )
                                        localDb.productDao().updateProduct(updated)
                                    }
                                } catch (docErr: Throwable) {
                                    Log.w(TAG, "Skipping malformed inventory doc ${doc.id}: ${docErr.message}")
                                }
                            }
                        } catch (e: Throwable) {
                            Log.w(TAG, "Product listener error", e)
                        }
                    }
                }

            // 4. Realtime listener for Remote Transactions (Sales and Payments)
            transactionListener = firestore.collection("users").document(userId).collection("transactions")
                .addSnapshotListener { snapshots, error ->
                    if (error != null || snapshots == null || snapshots.isEmpty) return@addSnapshotListener
                    scope.launch {
                        try {
                            for (doc in snapshots.documents) {
                                try {
                                    val id = doc.id
                                    val type = doc.safeString("type")
                                    val customerId = doc.safeString("customerId")
                                    val date = doc.safeLong("date") ?: (doc.safeLong("timestamp") ?: System.currentTimeMillis())
                                    val isDeleted = doc.safeBoolean("isDeleted", false)
                                    val createdAt = doc.safeLong("createdAt") ?: date
                                    val updatedAt = doc.safeLong("updatedAt") ?: date

                                    if (type == "SALE_CREDIT") {
                                        val productId = doc.safeString("productId")
                                        val quantityKg = BigDecimal.valueOf(doc.safeDouble("quantityKg", 0.0))
                                        val pricePerKgAtSale = BigDecimal.valueOf(doc.safeDouble("pricePerKgAtSale", 0.0))
                                        val costPerKgAtSale = BigDecimal.valueOf(doc.safeDouble("costPerKgAtSale", 0.0))
                                        val totalSalePrice = BigDecimal.valueOf(
                                            if (doc.contains("totalSalePrice")) doc.safeDouble("totalSalePrice", 0.0)
                                            else doc.safeDouble("amount", 0.0)
                                        )
                                        val paidAmount = BigDecimal.valueOf(doc.safeDouble("paidAmount", 0.0))
                                        val udhariAmount = BigDecimal.valueOf(doc.safeDouble("udhariAmount", 0.0))
                                        val paymentStatus = try {
                                            PaymentStatus.valueOf(doc.safeString("paymentStatus", "UNPAID"))
                                        } catch (_: Throwable) {
                                            PaymentStatus.UNPAID
                                        }
                                        val soldByUserId = doc.safeString("soldByUserId")
                                        val returnedKg = BigDecimal.valueOf(doc.safeDouble("returnedKg", 0.0))
                                        val billStatus = doc.safeString("billStatus", "ACTIVE").ifBlank { "ACTIVE" }

                                        val existing = localDb.saleDao().getSaleById(id)
                                        if (existing == null) {
                                            localDb.saleDao().insertSale(
                                                SaleEntry(
                                                    id = id,
                                                    date = date,
                                                    customerId = customerId,
                                                    productId = productId,
                                                    quantityKg = quantityKg,
                                                    pricePerKgAtSale = pricePerKgAtSale,
                                                    costPerKgAtSale = costPerKgAtSale,
                                                    totalSalePrice = totalSalePrice,
                                                    paidAmount = paidAmount,
                                                    udhariAmount = udhariAmount,
                                                    paymentStatus = paymentStatus,
                                                    soldByUserId = soldByUserId,
                                                    returnedKg = returnedKg,
                                                    billStatus = billStatus,
                                                    createdAt = createdAt,
                                                    updatedAt = updatedAt,
                                                    isDeleted = isDeleted,
                                                    syncStatus = SyncStatus.SYNCED
                                                )
                                            )
                                        } else if (updatedAt > existing.updatedAt && existing.syncStatus == SyncStatus.SYNCED) {
                                            localDb.saleDao().insertSale(
                                                existing.copy(
                                                    date = date,
                                                    customerId = customerId,
                                                    productId = productId,
                                                    quantityKg = quantityKg,
                                                    pricePerKgAtSale = pricePerKgAtSale,
                                                    costPerKgAtSale = costPerKgAtSale,
                                                    totalSalePrice = totalSalePrice,
                                                    paidAmount = paidAmount,
                                                    udhariAmount = udhariAmount,
                                                    paymentStatus = paymentStatus,
                                                    soldByUserId = soldByUserId,
                                                    returnedKg = returnedKg,
                                                    billStatus = billStatus,
                                                    updatedAt = updatedAt,
                                                    isDeleted = isDeleted,
                                                    syncStatus = SyncStatus.SYNCED
                                                )
                                            )
                                        }
                                    } else if (type == "PAYMENT_RECEIVED") {
                                        val amount = BigDecimal.valueOf(doc.safeDouble("amount", 0.0))
                                        val linkedSaleId = doc.safeNullableString("linkedSaleId")
                                        val note = doc.safeString("note").ifBlank { doc.safeString("description") }
                                        val collectedByUserId = doc.safeString("collectedByUserId")

                                        val existing = localDb.paymentDao().getPaymentById(id)
                                        if (existing == null) {
                                            localDb.paymentDao().insertPayment(
                                                PaymentEntry(
                                                    id = id,
                                                    date = date,
                                                    customerId = customerId,
                                                    amount = amount,
                                                    linkedSaleId = linkedSaleId,
                                                    note = note,
                                                    collectedByUserId = collectedByUserId,
                                                    createdAt = createdAt,
                                                    updatedAt = updatedAt,
                                                    isDeleted = isDeleted,
                                                    syncStatus = SyncStatus.SYNCED
                                                )
                                            )
                                        } else if (updatedAt > existing.updatedAt && existing.syncStatus == SyncStatus.SYNCED) {
                                            localDb.paymentDao().insertPayment(
                                                existing.copy(
                                                    date = date,
                                                    customerId = customerId,
                                                    amount = amount,
                                                    linkedSaleId = linkedSaleId,
                                                    note = note,
                                                    collectedByUserId = collectedByUserId,
                                                    updatedAt = updatedAt,
                                                    isDeleted = isDeleted,
                                                    syncStatus = SyncStatus.SYNCED
                                                )
                                            )
                                        }
                                    }

                                    if (customerId.isNotBlank()) {
                                        PashuAaharApp.instance.repository.recalculateCustomerUdhari(customerId, markPending = false)
                                    }
                                } catch (docErr: Throwable) {
                                    Log.w(TAG, "Skipping malformed transaction doc ${doc.id}: ${docErr.message}")
                                }
                            }
                        } catch (e: Throwable) {
                            Log.w(TAG, "Transaction listener error", e)
                        }
                    }
                }

            // 5. Realtime listener for Remote Staff Roster & Roles
            staffListener = firestore.collection("users").document(userId).collection("staff")
                .addSnapshotListener { snapshots, error ->
                    if (error != null || snapshots == null) return@addSnapshotListener
                    scope.launch {
                        try {
                            val currentSettings = localDb.settingsDao().getSettings()
                            val activeUserId = currentSettings?.activeUserId
                            val pairedStoreAdminUid = currentSettings?.storeAdminUid?.trim().orEmpty()
                            val storeAdminUid = pairedStoreAdminUid.ifBlank { userId }
                            val activeUser = if (activeUserId != null) localDb.userDao().getUserById(activeUserId) else null

                            val isStaffDevice = pairedStoreAdminUid.isNotEmpty() &&
                                activeUserId != null &&
                                activeUserId != pairedStoreAdminUid &&
                                activeUser?.role != Role.ADMINISTRATOR

                            if (isStaffDevice) {
                                val activeStaffDoc = snapshots.documents.firstOrNull {
                                    (it.safeString("staffUid") == activeUserId || it.id == activeUserId)
                                }
                                if (activeStaffDoc != null && !activeStaffDoc.safeBoolean("isActive", true)) {
                                    Log.w(TAG, "Active staff $activeUserId was revoked by Admin. Signing out.")
                                    PashuAaharApp.instance.repository.clearAllLocalDataAndSignOut()
                                    return@launch
                                }
                            }

                            for (doc in snapshots.documents) {
                                try {
                                    val staffUid = doc.safeString("staffUid").ifBlank { doc.id }
                                    val isOwnerDoc = staffUid == storeAdminUid
                                    val existing = localDb.userDao().getUserById(staffUid)
                                    val hasApprovalFields = doc.contains("isApproved") || doc.contains("status")
                                    val isLegacyDoc = !hasApprovalFields && doc.safeBoolean("isActive", true)
                                    if (!isStaffDevice && isLegacyDoc && (isOwnerDoc || existing != null)) {
                                        runCatching {
                                            doc.reference.set(
                                                mapOf("isApproved" to true, "status" to "APPROVED"),
                                                SetOptions.merge()
                                            )
                                        }
                                    }
                                    val isApproved = isOwnerDoc ||
                                        doc.safeBoolean("isApproved", false) ||
                                        doc.safeString("status", "PENDING") == "APPROVED" ||
                                        (isLegacyDoc && existing != null)
                                    if (!isApproved) continue

                                    val name = doc.safeString("name", "Staff").ifBlank { "Staff" }
                                    val email = doc.safeString("email").ifBlank { doc.safeString("claimedByEmail") }
                                    val phone = doc.safeString("phone")
                                    val deviceInfo = doc.safeString("deviceInfo")
                                    val lastActiveAt = doc.safeLong("lastActiveAt") ?: System.currentTimeMillis()
                                    val parsedRole = try {
                                        Role.valueOf(doc.safeString("role", "STAFF"))
                                    } catch (_: Throwable) {
                                        Role.STAFF
                                    }
                                    val role = if (isOwnerDoc) Role.ADMINISTRATOR else parsedRole
                                    val isActive = if (isOwnerDoc) true else doc.safeBoolean("isActive", true)
                                    val updatedAt = doc.safeLong("updatedAt") ?: System.currentTimeMillis()

                                    if (existing == null) {
                                        localDb.userDao().insertUser(
                                            User(
                                                id = staffUid,
                                                name = name,
                                                role = role,
                                                email = email,
                                                phone = phone,
                                                deviceInfo = deviceInfo,
                                                lastActiveAt = lastActiveAt,
                                                pinHash = "",
                                                isActive = isActive,
                                                isDeleted = !isActive,
                                                createdAt = updatedAt,
                                                updatedAt = updatedAt,
                                                syncStatus = SyncStatus.SYNCED
                                            )
                                        )
                                    } else {
                                        val effectiveRole = if (existing.role == Role.ADMINISTRATOR && isOwnerDoc) {
                                            Role.ADMINISTRATOR
                                        } else {
                                            role
                                        }
                                        localDb.userDao().updateUser(
                                            existing.copy(
                                                name = name,
                                                role = effectiveRole,
                                                email = if (email.isNotBlank()) email else existing.email,
                                                phone = if (phone.isNotBlank()) phone else existing.phone,
                                                deviceInfo = if (deviceInfo.isNotBlank()) deviceInfo else existing.deviceInfo,
                                                lastActiveAt = maxOf(lastActiveAt, existing.lastActiveAt),
                                                isActive = isActive,
                                                isDeleted = !isActive,
                                                updatedAt = maxOf(updatedAt, existing.updatedAt),
                                                syncStatus = SyncStatus.SYNCED
                                            )
                                        )
                                    }
                                } catch (docErr: Throwable) {
                                    Log.w(TAG, "Skipping malformed staff doc ${doc.id}: ${docErr.message}")
                                }
                            }
                        } catch (e: Throwable) {
                            Log.w(TAG, "Staff listener error", e)
                        }
                    }
                }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start realtime listeners", e)
        }
    }

    fun stopRealtimeListeners() {
        activeListenerUserId = null
        try { profileListener?.remove() } catch (_: Throwable) {}
        profileListener = null
        try { customerListener?.remove() } catch (_: Throwable) {}
        customerListener = null
        try { productListener?.remove() } catch (_: Throwable) {}
        productListener = null
        try { transactionListener?.remove() } catch (_: Throwable) {}
        transactionListener = null
        try { staffListener?.remove() } catch (_: Throwable) {}
        staffListener = null
    }

    fun triggerSync() {
        scope.launch {
            try {
                syncNow()
            } catch (e: Throwable) {
                Log.w(TAG, "Triggered sync error: ${e.message}")
            }
        }
    }

    /**
     * Comprehensive synchronization:
     * 1. First downloads remote store profile, staff, customers, inventory, and transactions so local DB is never overwritten with blanks.
     * 2. Then uploads local unsynced records to Firestore with SetOptions.merge().
     */
    suspend fun syncNow(): FirestoreSyncState = withContext(Dispatchers.IO) {
        if (!syncMutex.tryLock()) {
            return@withContext _syncState.value
        }
        try {
            val user = auth?.currentUser
            if (user == null) {
                _syncState.value = FirestoreSyncState.Unauthenticated
                return@withContext FirestoreSyncState.Unauthenticated
            }

            val firestore = db ?: run {
                val err = FirestoreSyncState.Error("Firestore not ready")
                _syncState.value = err
                return@withContext err
            }

            _syncState.value = FirestoreSyncState.Syncing
            localDb.settingsDao().ensureDefaultSettings()
            val currentSettings = localDb.settingsDao().getSettings()
            val storeUid = if (!currentSettings?.storeAdminUid.isNullOrBlank()) {
                currentSettings!!.storeAdminUid
            } else {
                user.uid
            }
            val isStoreOwner = storeUid == user.uid

            // Ensure realtime listeners are attached for this storeUid
            startRealtimeListeners(storeUid)

            var syncedCount = 0
            var lastPushError: String? = null

            // -------------------------------------------------------------
            // PHASE 1: Pull Remote Data First (Crucial for Fresh Login & Cross-Device Sync)
            // -------------------------------------------------------------
            val userDoc = try {
                firestore.collection("users").document(storeUid).get().await()
            } catch (e: Throwable) {
                Log.w(TAG, "Error pulling user profile doc", e)
                null
            }

            if (userDoc != null && userDoc.exists()) {
                val remoteShopName = userDoc.safeString("shopName").ifBlank { userDoc.safeString("storeName") }
                val remoteAddress = userDoc.safeString("shopAddress")
                val remotePhone = userDoc.safeString("shopPhone").ifBlank { userDoc.safeString("phone") }
                val remoteGstin = userDoc.safeString("shopGstin")
                val remoteUpiId = userDoc.safeString("shopUpiId")
                val remoteWatch = userDoc.safeDouble("watchThreshold", 2000.0)
                val remoteCritical = userDoc.safeDouble("criticalThreshold", 5000.0)
                val remoteUpdatedAt = userDoc.safeLong("updatedAt") ?: 0L

                val latestSettings = localDb.settingsDao().getSettings()
                if (latestSettings != null) {
                    val preferRemote = !isStoreOwner || remoteUpdatedAt > latestSettings.lastSyncTime
                    localDb.settingsDao().updateStoreProfileFields(
                        shopName = if (remoteShopName.isNotBlank() && (preferRemote || latestSettings.shopName.isBlank() || latestSettings.shopName == "KhataWise Store"))
                            remoteShopName else latestSettings.shopName,
                        shopAddress = if (remoteAddress.isNotBlank() && (preferRemote || latestSettings.shopAddress.isBlank()))
                            remoteAddress else latestSettings.shopAddress,
                        shopPhone = if (remotePhone.isNotBlank() && (preferRemote || latestSettings.shopPhone.isBlank()))
                            remotePhone else latestSettings.shopPhone,
                        shopGstin = if (remoteGstin.isNotBlank() && (preferRemote || latestSettings.shopGstin.isBlank()))
                            remoteGstin else latestSettings.shopGstin,
                        shopUpiId = if (remoteUpiId.isNotBlank() && (preferRemote || latestSettings.shopUpiId.isBlank()))
                            remoteUpiId else latestSettings.shopUpiId,
                        watchThreshold = if (remoteWatch > 0) BigDecimal.valueOf(remoteWatch) else latestSettings.watchThreshold,
                        criticalThreshold = if (remoteCritical > 0) BigDecimal.valueOf(remoteCritical) else latestSettings.criticalThreshold
                    )
                }
            }

            // Pull Staff & migrate legacy accepted staff documents missing approval fields (N24)
            try {
                val staffDocs = firestore.collection("users").document(storeUid).collection("staff").get().await()
                for (doc in staffDocs.documents) {
                    try {
                        val staffUid = doc.safeString("staffUid").ifBlank { doc.id }
                        val isOwnerDoc = staffUid == storeUid
                        val existing = localDb.userDao().getUserById(staffUid)
                        val hasApprovalFields = doc.contains("isApproved") || doc.contains("status")
                        val isLegacyDoc = !hasApprovalFields && doc.safeBoolean("isActive", true)
                        if (isStoreOwner && isLegacyDoc) {
                            runCatching {
                                doc.reference.set(
                                    mapOf("isApproved" to true, "status" to "APPROVED"),
                                    SetOptions.merge()
                                ).await()
                            }
                        }
                        val isApproved = isOwnerDoc ||
                            doc.safeBoolean("isApproved", false) ||
                            doc.safeString("status", "PENDING") == "APPROVED" ||
                            isLegacyDoc
                        if (!isApproved) continue

                        val name = doc.safeString("name", "Staff").ifBlank { "Staff" }
                        val email = doc.safeString("email").ifBlank { doc.safeString("claimedByEmail") }
                        val phone = doc.safeString("phone")
                        val deviceInfo = doc.safeString("deviceInfo")
                        val lastActiveAt = doc.safeLong("lastActiveAt") ?: System.currentTimeMillis()
                        val parsedRole = try {
                            Role.valueOf(doc.safeString("role", "STAFF"))
                        } catch (_: Throwable) {
                            Role.STAFF
                        }
                        val role = if (isOwnerDoc) Role.ADMINISTRATOR else parsedRole
                        val isActive = if (isOwnerDoc) true else doc.safeBoolean("isActive", true)
                        val updatedAt = doc.safeLong("updatedAt") ?: System.currentTimeMillis()

                        if (existing == null) {
                            localDb.userDao().insertUser(
                                User(
                                    id = staffUid,
                                    name = name,
                                    role = role,
                                    email = email,
                                    phone = phone,
                                    deviceInfo = deviceInfo,
                                    lastActiveAt = lastActiveAt,
                                    pinHash = "",
                                    isActive = isActive,
                                    isDeleted = !isActive,
                                    createdAt = updatedAt,
                                    updatedAt = updatedAt,
                                    syncStatus = SyncStatus.SYNCED
                                )
                            )
                            syncedCount++
                        } else if (existing.syncStatus == SyncStatus.SYNCED) {
                            val effectiveRole = if (existing.role == Role.ADMINISTRATOR && isOwnerDoc) {
                                Role.ADMINISTRATOR
                            } else {
                                role
                            }
                            localDb.userDao().updateUser(
                                existing.copy(
                                    name = name,
                                    role = effectiveRole,
                                    email = if (email.isNotBlank()) email else existing.email,
                                    phone = if (phone.isNotBlank()) phone else existing.phone,
                                    deviceInfo = if (deviceInfo.isNotBlank()) deviceInfo else existing.deviceInfo,
                                    lastActiveAt = maxOf(lastActiveAt, existing.lastActiveAt),
                                    isActive = isActive,
                                    isDeleted = !isActive,
                                    updatedAt = maxOf(updatedAt, existing.updatedAt),
                                    syncStatus = SyncStatus.SYNCED
                                )
                            )
                        }
                    } catch (docErr: Throwable) {
                        Log.w(TAG, "Skipping staff doc ${doc.id}: ${docErr.message}")
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Error pulling remote staff", e)
            }

            // Pull Customers
            try {
                val customerDocs = firestore.collection("users").document(storeUid).collection("customers").get().await()
                for (doc in customerDocs.documents) {
                    try {
                        val name = doc.safeString("name")
                        if (name.isBlank()) continue
                        val id = doc.id
                        val fatherName = doc.safeString("fatherName")
                        val phone = doc.safeString("phone")
                        val phonesJson = doc.safeString("phonesJson")
                        val village = doc.safeString("village")
                        val totalUdhariNum = if (doc.contains("totalUdhari")) {
                            doc.safeDouble("totalUdhari", 0.0)
                        } else {
                            doc.safeDouble("currentBalance", 0.0)
                        }
                        val creditLimitNum = doc.safeDouble("creditLimit", 10000.0)
                        val promiseDate = doc.safeLong("promiseDate")
                        val createdBy = doc.safeString("createdByUserId")
                        val createdAt = doc.safeLong("createdAt") ?: System.currentTimeMillis()
                        val isDeleted = doc.safeBoolean("isDeleted", false)
                        val updatedAt = doc.safeLong("updatedAt") ?: System.currentTimeMillis()

                        val existing = localDb.customerDao().getCustomerById(id)
                        if (existing == null) {
                            localDb.customerDao().insertCustomer(
                                Customer(
                                    id = id,
                                    name = name,
                                    fatherName = fatherName,
                                    phone = phone,
                                    phonesJson = phonesJson,
                                    village = village,
                                    totalUdhari = BigDecimal.valueOf(totalUdhariNum),
                                    creditLimit = BigDecimal.valueOf(creditLimitNum),
                                    promiseDate = if (promiseDate != null && promiseDate > 0) promiseDate else null,
                                    createdByUserId = createdBy,
                                    createdAt = createdAt,
                                    updatedAt = updatedAt,
                                    isDeleted = isDeleted,
                                    syncStatus = SyncStatus.SYNCED
                                )
                            )
                            syncedCount++
                        } else if (updatedAt > existing.updatedAt && existing.syncStatus == SyncStatus.SYNCED) {
                            localDb.customerDao().updateCustomer(
                                existing.copy(
                                    name = name,
                                    fatherName = if (fatherName.isNotBlank()) fatherName else existing.fatherName,
                                    phone = phone,
                                    phonesJson = if (phonesJson.isNotBlank()) phonesJson else existing.phonesJson,
                                    village = village,
                                    totalUdhari = BigDecimal.valueOf(totalUdhariNum),
                                    creditLimit = BigDecimal.valueOf(creditLimitNum),
                                    promiseDate = if (promiseDate != null && promiseDate > 0) promiseDate else existing.promiseDate,
                                    createdByUserId = if (createdBy.isNotBlank()) createdBy else existing.createdByUserId,
                                    createdAt = createdAt,
                                    updatedAt = updatedAt,
                                    isDeleted = isDeleted,
                                    syncStatus = SyncStatus.SYNCED
                                )
                            )
                            syncedCount++
                        }
                    } catch (docErr: Throwable) {
                        Log.w(TAG, "Skipping customer doc ${doc.id}: ${docErr.message}")
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Error pulling remote customers", e)
            }

            // Pull Inventory / Products
            try {
                val productDocs = firestore.collection("users").document(storeUid).collection("inventory").get().await()
                for (doc in productDocs.documents) {
                    try {
                        val feedName = doc.safeString("name").ifBlank { doc.safeString("feedName") }
                        if (feedName.isBlank()) continue
                        val id = doc.id
                        val baseUnit = doc.safeString("baseUnit", "kg").ifBlank { "kg" }
                        val kgPerBagNum = doc.safeDouble("kgPerBag", 50.0)
                        val currentStockKgNum = if (doc.contains("currentStockKg")) {
                            doc.safeDouble("currentStockKg", 0.0)
                        } else {
                            doc.safeDouble("stockBags", 0.0) * kgPerBagNum + doc.safeDouble("stockLooseKg", 0.0)
                        }
                        val sellingPrice = if (doc.contains("sellingPricePerKg")) {
                            doc.safeDouble("sellingPricePerKg", 0.0)
                        } else {
                            doc.safeDouble("salePricePerKg", 0.0)
                        }
                        val loosePrice = doc.safeDouble("looseSellingPricePerKg", 0.0)
                        val avgCost = doc.safeDouble("averagePurchaseCostPerKg", 0.0)
                        val isLooseOnly = doc.safeBoolean("isLooseOnly", false)
                        val isDeleted = doc.safeBoolean("isDeleted", false)
                        val createdAt = doc.safeLong("createdAt") ?: System.currentTimeMillis()
                        val updatedAt = doc.safeLong("updatedAt") ?: System.currentTimeMillis()

                        val existing = localDb.productDao().getProductById(id)
                        if (existing == null) {
                            localDb.productDao().insertProduct(
                                Product(
                                    id = id,
                                    feedName = feedName,
                                    baseUnit = baseUnit,
                                    kgPerBag = BigDecimal.valueOf(kgPerBagNum),
                                    currentStockKg = BigDecimal.valueOf(currentStockKgNum),
                                    averagePurchaseCostPerKg = BigDecimal.valueOf(avgCost),
                                    sellingPricePerKg = BigDecimal.valueOf(sellingPrice),
                                    looseSellingPricePerKg = BigDecimal.valueOf(loosePrice),
                                    isLooseOnly = isLooseOnly,
                                    createdAt = createdAt,
                                    updatedAt = updatedAt,
                                    isDeleted = isDeleted,
                                    syncStatus = SyncStatus.SYNCED
                                )
                            )
                            syncedCount++
                        } else if (updatedAt > existing.updatedAt && existing.syncStatus == SyncStatus.SYNCED) {
                            localDb.productDao().updateProduct(
                                existing.copy(
                                    feedName = feedName,
                                    baseUnit = baseUnit,
                                    kgPerBag = BigDecimal.valueOf(kgPerBagNum),
                                    currentStockKg = BigDecimal.valueOf(currentStockKgNum),
                                    averagePurchaseCostPerKg = BigDecimal.valueOf(avgCost),
                                    sellingPricePerKg = BigDecimal.valueOf(sellingPrice),
                                    looseSellingPricePerKg = BigDecimal.valueOf(loosePrice),
                                    isLooseOnly = isLooseOnly,
                                    updatedAt = updatedAt,
                                    isDeleted = isDeleted,
                                    syncStatus = SyncStatus.SYNCED
                                )
                            )
                            syncedCount++
                        }
                    } catch (docErr: Throwable) {
                        Log.w(TAG, "Skipping inventory doc ${doc.id}: ${docErr.message}")
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Error pulling remote inventory", e)
            }

            // Pull Transactions (Sales & Payments)
            try {
                val txDocs = firestore.collection("users").document(storeUid).collection("transactions").get().await()
                val affectedCustomerIds = mutableSetOf<String>()
                for (doc in txDocs.documents) {
                    try {
                        val id = doc.id
                        val type = doc.safeString("type")
                        val customerId = doc.safeString("customerId")
                        val date = doc.safeLong("date") ?: (doc.safeLong("timestamp") ?: System.currentTimeMillis())
                        val isDeleted = doc.safeBoolean("isDeleted", false)
                        val createdAt = doc.safeLong("createdAt") ?: date
                        val updatedAt = doc.safeLong("updatedAt") ?: date

                        if (type == "SALE_CREDIT") {
                            val productId = doc.safeString("productId")
                            val quantityKg = BigDecimal.valueOf(doc.safeDouble("quantityKg", 0.0))
                            val pricePerKgAtSale = BigDecimal.valueOf(doc.safeDouble("pricePerKgAtSale", 0.0))
                            val costPerKgAtSale = BigDecimal.valueOf(doc.safeDouble("costPerKgAtSale", 0.0))
                            val totalSalePrice = BigDecimal.valueOf(
                                if (doc.contains("totalSalePrice")) doc.safeDouble("totalSalePrice", 0.0)
                                else doc.safeDouble("amount", 0.0)
                            )
                            val paidAmount = BigDecimal.valueOf(doc.safeDouble("paidAmount", 0.0))
                            val udhariAmount = BigDecimal.valueOf(doc.safeDouble("udhariAmount", 0.0))
                            val paymentStatus = try {
                                PaymentStatus.valueOf(doc.safeString("paymentStatus", "UNPAID"))
                            } catch (_: Throwable) {
                                PaymentStatus.UNPAID
                            }
                            val soldByUserId = doc.safeString("soldByUserId")
                            val returnedKg = BigDecimal.valueOf(doc.safeDouble("returnedKg", 0.0))
                            val billStatus = doc.safeString("billStatus", "ACTIVE").ifBlank { "ACTIVE" }

                            val existing = localDb.saleDao().getSaleById(id)
                            if (existing == null) {
                                localDb.saleDao().insertSale(
                                    SaleEntry(
                                        id = id,
                                        date = date,
                                        customerId = customerId,
                                        productId = productId,
                                        quantityKg = quantityKg,
                                        pricePerKgAtSale = pricePerKgAtSale,
                                        costPerKgAtSale = costPerKgAtSale,
                                        totalSalePrice = totalSalePrice,
                                        paidAmount = paidAmount,
                                        udhariAmount = udhariAmount,
                                        paymentStatus = paymentStatus,
                                        soldByUserId = soldByUserId,
                                        returnedKg = returnedKg,
                                        billStatus = billStatus,
                                        createdAt = createdAt,
                                        updatedAt = updatedAt,
                                        isDeleted = isDeleted,
                                        syncStatus = SyncStatus.SYNCED
                                    )
                                )
                                syncedCount++
                                if (customerId.isNotBlank()) affectedCustomerIds.add(customerId)
                            } else if (updatedAt > existing.updatedAt && existing.syncStatus == SyncStatus.SYNCED) {
                                localDb.saleDao().insertSale(
                                    existing.copy(
                                        date = date,
                                        customerId = customerId,
                                        productId = productId,
                                        quantityKg = quantityKg,
                                        pricePerKgAtSale = pricePerKgAtSale,
                                        costPerKgAtSale = costPerKgAtSale,
                                        totalSalePrice = totalSalePrice,
                                        paidAmount = paidAmount,
                                        udhariAmount = udhariAmount,
                                        paymentStatus = paymentStatus,
                                        soldByUserId = soldByUserId,
                                        returnedKg = returnedKg,
                                        billStatus = billStatus,
                                        updatedAt = updatedAt,
                                        isDeleted = isDeleted,
                                        syncStatus = SyncStatus.SYNCED
                                    )
                                )
                                syncedCount++
                                if (customerId.isNotBlank()) affectedCustomerIds.add(customerId)
                            }
                        } else if (type == "PAYMENT_RECEIVED") {
                            val amount = BigDecimal.valueOf(doc.safeDouble("amount", 0.0))
                            val linkedSaleId = doc.safeNullableString("linkedSaleId")
                            val note = doc.safeString("note").ifBlank { doc.safeString("description") }
                            val collectedByUserId = doc.safeString("collectedByUserId")

                            val existing = localDb.paymentDao().getPaymentById(id)
                            if (existing == null) {
                                localDb.paymentDao().insertPayment(
                                    PaymentEntry(
                                        id = id,
                                        date = date,
                                        customerId = customerId,
                                        amount = amount,
                                        linkedSaleId = linkedSaleId,
                                        note = note,
                                        collectedByUserId = collectedByUserId,
                                        createdAt = createdAt,
                                        updatedAt = updatedAt,
                                        isDeleted = isDeleted,
                                        syncStatus = SyncStatus.SYNCED
                                    )
                                )
                                syncedCount++
                                if (customerId.isNotBlank()) affectedCustomerIds.add(customerId)
                            } else if (updatedAt > existing.updatedAt && existing.syncStatus == SyncStatus.SYNCED) {
                                localDb.paymentDao().insertPayment(
                                    existing.copy(
                                        date = date,
                                        customerId = customerId,
                                        amount = amount,
                                        linkedSaleId = linkedSaleId,
                                        note = note,
                                        collectedByUserId = collectedByUserId,
                                        updatedAt = updatedAt,
                                        isDeleted = isDeleted,
                                        syncStatus = SyncStatus.SYNCED
                                    )
                                )
                                syncedCount++
                                if (customerId.isNotBlank()) affectedCustomerIds.add(customerId)
                            }
                        }
                    } catch (docErr: Throwable) {
                        Log.w(TAG, "Skipping tx doc ${doc.id}: ${docErr.message}")
                    }
                }
                for (cid in affectedCustomerIds) {
                    try {
                        PashuAaharApp.instance.repository.recalculateCustomerUdhari(cid, markPending = false)
                    } catch (_: Throwable) {}
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Error pulling remote transactions", e)
            }

            // -------------------------------------------------------------
            // PHASE 2: Push Local Unsynced Records to Cloud
            // -------------------------------------------------------------
            // 1. Sync User / Store profile (Owner only)
            if (isStoreOwner) {
                try {
                    val refreshedSettings = localDb.settingsDao().getSettings()
                    val storeData = hashMapOf<String, Any>(
                        "storeName" to (refreshedSettings?.shopName?.ifBlank { "KhataWise Store" } ?: "KhataWise Store"),
                        "shopName" to (refreshedSettings?.shopName?.ifBlank { "KhataWise Store" } ?: "KhataWise Store"),
                        "shopAddress" to (refreshedSettings?.shopAddress ?: ""),
                        "shopPhone" to (refreshedSettings?.shopPhone ?: ""),
                        "shopGstin" to (refreshedSettings?.shopGstin ?: ""),
                        "shopUpiId" to (refreshedSettings?.shopUpiId ?: ""),
                        "watchThreshold" to (refreshedSettings?.watchThreshold?.toDouble() ?: 2000.0),
                        "criticalThreshold" to (refreshedSettings?.criticalThreshold?.toDouble() ?: 5000.0),
                        "ownerName" to (user.displayName ?: "Store Owner"),
                        "phone" to (user.phoneNumber ?: (refreshedSettings?.shopPhone ?: "")),
                        "updatedAt" to System.currentTimeMillis(),
                        "lastSyncedAt" to FieldValue.serverTimestamp()
                    )
                    firestore.collection("users").document(storeUid).set(storeData, SetOptions.merge()).await()
                } catch (e: Throwable) {
                    Log.w(TAG, "Error pushing store profile", e)
                    lastPushError = e.localizedMessage
                }
            }

            // 2. Sync Unsynced Customers
            val unsyncedCustomers = localDb.customerDao().getUnsyncedCustomers()
            for (c in unsyncedCustomers) {
                try {
                    val customerDoc = hashMapOf<String, Any>(
                        "id" to c.id,
                        "name" to c.name,
                        "fatherName" to c.fatherName,
                        "phone" to c.phone,
                        "phonesJson" to c.phonesJson,
                        "village" to c.village,
                        "currentBalance" to c.totalUdhari.toDouble(),
                        "totalUdhari" to c.totalUdhari.toDouble(),
                        "creditLimit" to c.creditLimit.toDouble(),
                        "promiseDate" to (c.promiseDate ?: 0L),
                        "createdByUserId" to c.createdByUserId,
                        "createdAt" to c.createdAt,
                        "updatedAt" to c.updatedAt,
                        "isDeleted" to c.isDeleted
                    )
                    firestore.collection("users").document(storeUid).collection("customers")
                        .document(c.id)
                        .set(customerDoc, SetOptions.merge())
                        .await()
                    localDb.customerDao().updateCustomer(c.copy(syncStatus = SyncStatus.SYNCED))
                    syncedCount++
                } catch (e: Throwable) {
                    Log.w(TAG, "Error pushing customer ${c.id}", e)
                    lastPushError = e.localizedMessage
                }
            }

            // 3. Sync Unsynced Sales
            val unsyncedSales = localDb.saleDao().getUnsyncedSales()
            for (s in unsyncedSales) {
                try {
                    val txDoc = hashMapOf<String, Any>(
                        "id" to s.id,
                        "customerId" to s.customerId,
                        "productId" to s.productId,
                        "type" to "SALE_CREDIT",
                        "amount" to s.totalSalePrice.toDouble(),
                        "quantityKg" to s.quantityKg.toDouble(),
                        "pricePerKgAtSale" to s.pricePerKgAtSale.toDouble(),
                        "costPerKgAtSale" to s.costPerKgAtSale.toDouble(),
                        "totalSalePrice" to s.totalSalePrice.toDouble(),
                        "paidAmount" to s.paidAmount.toDouble(),
                        "udhariAmount" to s.udhariAmount.toDouble(),
                        "paymentStatus" to s.paymentStatus.name,
                        "soldByUserId" to s.soldByUserId,
                        "returnedKg" to s.returnedKg.toDouble(),
                        "billStatus" to s.billStatus,
                        "date" to s.date,
                        "timestamp" to s.date,
                        "createdAt" to s.createdAt,
                        "updatedAt" to s.updatedAt,
                        "isDeleted" to s.isDeleted,
                        "description" to "Sale ${s.quantityKg}kg (${s.billStatus})"
                    )
                    firestore.collection("users").document(storeUid).collection("transactions")
                        .document(s.id)
                        .set(txDoc, SetOptions.merge())
                        .await()
                    localDb.saleDao().insertSale(s.copy(syncStatus = SyncStatus.SYNCED))
                    syncedCount++
                } catch (e: Throwable) {
                    Log.w(TAG, "Error pushing sale ${s.id}", e)
                    lastPushError = e.localizedMessage
                }
            }

            // 4. Sync Unsynced Payments
            val unsyncedPayments = localDb.paymentDao().getUnsyncedPayments()
            for (p in unsyncedPayments) {
                try {
                    val txDoc = hashMapOf<String, Any>(
                        "id" to p.id,
                        "customerId" to p.customerId,
                        "type" to "PAYMENT_RECEIVED",
                        "amount" to p.amount.toDouble(),
                        "linkedSaleId" to (p.linkedSaleId ?: ""),
                        "note" to p.note,
                        "collectedByUserId" to p.collectedByUserId,
                        "date" to p.date,
                        "timestamp" to p.date,
                        "createdAt" to p.createdAt,
                        "updatedAt" to p.updatedAt,
                        "isDeleted" to p.isDeleted,
                        "description" to if (p.note.isNotBlank()) p.note else "Payment received"
                    )
                    firestore.collection("users").document(storeUid).collection("transactions")
                        .document(p.id)
                        .set(txDoc, SetOptions.merge())
                        .await()
                    localDb.paymentDao().insertPayment(p.copy(syncStatus = SyncStatus.SYNCED))
                    syncedCount++
                } catch (e: Throwable) {
                    Log.w(TAG, "Error pushing payment ${p.id}", e)
                    lastPushError = e.localizedMessage
                }
            }

            // 5. Sync Unsynced Products / Inventory
            val unsyncedProducts = localDb.productDao().getUnsyncedProducts()
            for (p in unsyncedProducts) {
                try {
                    val bagCount = if (p.kgPerBag > BigDecimal.ZERO) {
                        p.currentStockKg.divide(p.kgPerBag, java.math.RoundingMode.DOWN).toInt()
                    } else {
                        0
                    }
                    val looseKg = if (p.kgPerBag > BigDecimal.ZERO) {
                        p.currentStockKg.remainder(p.kgPerBag).toDouble()
                    } else {
                        p.currentStockKg.toDouble()
                    }
                    val inventoryDoc = hashMapOf<String, Any>(
                        "id" to p.id,
                        "name" to p.feedName,
                        "feedName" to p.feedName,
                        "baseUnit" to p.baseUnit,
                        "kgPerBag" to p.kgPerBag.toDouble(),
                        "currentStockKg" to p.currentStockKg.toDouble(),
                        "stockBags" to bagCount,
                        "stockLooseKg" to looseKg,
                        "sellingPricePerKg" to p.sellingPricePerKg.toDouble(),
                        "salePricePerKg" to p.sellingPricePerKg.toDouble(),
                        "looseSellingPricePerKg" to p.looseSellingPricePerKg.toDouble(),
                        "averagePurchaseCostPerKg" to p.averagePurchaseCostPerKg.toDouble(),
                        "isLooseOnly" to p.isLooseOnly,
                        "createdAt" to p.createdAt,
                        "updatedAt" to p.updatedAt,
                        "isDeleted" to p.isDeleted
                    )
                    firestore.collection("users").document(storeUid).collection("inventory")
                        .document(p.id)
                        .set(inventoryDoc, SetOptions.merge())
                        .await()
                    localDb.productDao().insertProduct(p.copy(syncStatus = SyncStatus.SYNCED))
                    syncedCount++
                } catch (e: Throwable) {
                    Log.w(TAG, "Error pushing product ${p.id}", e)
                    lastPushError = e.localizedMessage
                }
            }

            // 6. Sync Unsynced Users / Staff (Owner only)
            if (isStoreOwner) {
                val unsyncedUsers = localDb.userDao().getUnsyncedUsers()
                for (u in unsyncedUsers) {
                    try {
                        if (u.isDeleted || !u.isActive) {
                            try {
                                firestore.collection("users").document(storeUid).collection("staff")
                                    .document(u.id)
                                    .delete()
                                    .await()
                            } catch (_: Throwable) {}
                        } else {
                            val staffDoc = hashMapOf<String, Any>(
                                "staffUid" to u.id,
                                "name" to u.name,
                                "role" to u.role.name,
                                "email" to u.email,
                                "phone" to u.phone,
                                "deviceInfo" to u.deviceInfo,
                                "lastActiveAt" to u.lastActiveAt,
                                "isActive" to u.isActive,
                                "isApproved" to true,
                                "status" to "APPROVED",
                                "updatedAt" to u.updatedAt
                            )
                            firestore.collection("users").document(storeUid).collection("staff")
                                .document(u.id)
                                .set(staffDoc, SetOptions.merge())
                                .await()
                        }
                        localDb.userDao().updateUser(u.copy(syncStatus = SyncStatus.SYNCED))
                        syncedCount++
                    } catch (e: Throwable) {
                        Log.w(TAG, "Error pushing staff ${u.id}", e)
                        lastPushError = e.localizedMessage
                    }
                }
            }

            if (lastPushError != null && syncedCount == 0) {
                val err = FirestoreSyncState.Error(lastPushError ?: "Sync failed")
                _syncState.value = err
                return@withContext err
            }

            localDb.settingsDao().ensureDefaultSettings()
            localDb.settingsDao().updateLastSyncTime(System.currentTimeMillis())

            val success = FirestoreSyncState.Success(syncedCount)
            _syncState.value = success
            success
        } catch (e: Throwable) {
            Log.e(TAG, "Firestore sync error", e)
            val err = FirestoreSyncState.Error(e.localizedMessage ?: "Sync failed")
            _syncState.value = err
            err
        } finally {
            syncMutex.unlock()
        }
    }
}
