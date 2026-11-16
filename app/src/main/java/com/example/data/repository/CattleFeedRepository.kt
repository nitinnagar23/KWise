package com.example.data.repository

import android.content.Context
import com.example.data.AppDatabase
import com.example.model.AppSettings
import com.example.model.BillAuditLog
import com.example.model.BillData
import com.example.model.BillItem
import com.example.model.Customer
import com.example.model.EstimateDraft
import com.example.model.PaymentEntry
import com.example.model.PaymentStatus
import com.example.model.Permission
import com.example.model.Product
import com.example.model.PurchaseEntry
import com.example.model.PurchaseInvoice
import com.example.model.PurchaseInvoiceItem
import com.example.model.Role
import com.example.model.RoleChangeLog
import com.example.model.SaleEntry
import com.example.model.SupplierKhataSummary
import com.example.model.SupplierLedgerEntry
import com.example.model.SupplierPayment
import com.example.model.SyncStatus
import com.example.model.User
import com.example.model.can
import com.example.model.canEditCustomerBasic
import com.example.model.requirePermission
import androidx.room.withTransaction
import com.example.util.CurrencyUtils
import com.example.util.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import java.util.UUID

data class SaleItemRequest(
    val productId: String,
    val bags: Int,
    val looseKg: BigDecimal,
    val customBagPrice: BigDecimal? = null,
    val customLoosePricePerKg: BigDecimal? = null
)

data class PurchaseInvoiceItemRequest(
    val productId: String,
    val bags: Int = 0,
    val looseKg: BigDecimal = BigDecimal.ZERO,
    val customBagPrice: BigDecimal? = null,
    val customPricePerKg: BigDecimal? = null
)

class CattleFeedRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val userDao = db.userDao()
    private val customerDao = db.customerDao()
    private val productDao = db.productDao()
    private val saleDao = db.saleDao()
    private val paymentDao = db.paymentDao()
    private val purchaseDao = db.purchaseDao()
    private val purchaseInvoiceDao = db.purchaseInvoiceDao()
    private val supplierPaymentDao = db.supplierPaymentDao()
    private val settingsDao = db.settingsDao()
    private val roleChangeLogDao = db.roleChangeLogDao()
    private val billAuditLogDao = db.billAuditLogDao()
    private val estimateDraftDao = db.estimateDraftDao()

    val allUsers: Flow<List<User>> = userDao.getAllUsers().catch { emit(emptyList()) }
    val allCustomers: Flow<List<Customer>> = customerDao.getAllCustomers().catch { emit(emptyList()) }
    val customersWithUdhari: Flow<List<Customer>> = customerDao.getCustomersWithUdhari().catch { emit(emptyList()) }
    val allProducts: Flow<List<Product>> = productDao.getAllProducts().catch { emit(emptyList()) }
    val allSales: Flow<List<SaleEntry>> = saleDao.getAllSales().catch { emit(emptyList()) }
    val allPayments: Flow<List<PaymentEntry>> = paymentDao.getAllPayments().catch { emit(emptyList()) }
    val allPurchases: Flow<List<PurchaseEntry>> = purchaseDao.getAllPurchases().catch { emit(emptyList()) }
    val allPurchaseInvoices: Flow<List<PurchaseInvoice>> = purchaseInvoiceDao.getAllInvoices().catch { emit(emptyList()) }
    val allSupplierPayments: Flow<List<SupplierPayment>> = supplierPaymentDao.getAllPayments().catch { emit(emptyList()) }
    val settingsFlow: Flow<AppSettings?> = settingsDao.observeSettings().catch { emit(AppSettings()) }
    val allBillAuditLogs: Flow<List<BillAuditLog>> = billAuditLogDao.getAllAuditLogs().catch { emit(emptyList()) }
    val allEstimateDrafts: Flow<List<EstimateDraft>> = estimateDraftDao.getAllDrafts().catch { emit(emptyList()) }

    suspend fun saveEstimateDraft(draft: EstimateDraft) = withContext(Dispatchers.IO) {
        estimateDraftDao.insertDraft(draft.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteEstimateDraft(draftId: String) = withContext(Dispatchers.IO) {
        estimateDraftDao.deleteDraft(draftId)
    }

    suspend fun getOrCreateWalkInCustomer(createdByUserId: String = ""): Customer = withContext(Dispatchers.IO) {
        val walkInId = "walk_in_customer"
        val existing = customerDao.getCustomerById(walkInId)
        if (existing != null) return@withContext existing
        val walkIn = Customer(
            id = walkInId,
            name = "Walk-in Customer",
            phone = "",
            village = "Walk-in",
            creditLimit = BigDecimal.ZERO,
            createdByUserId = createdByUserId
        )
        customerDao.insertCustomer(walkIn)
        walkIn
    }

    val supplierSummaries: Flow<List<SupplierKhataSummary>> = combine(
        allPurchaseInvoices,
        allSupplierPayments
    ) { invoices, payments ->
        val supplierMap = mutableMapOf<String, SupplierKhataSummary>()

        for (inv in invoices) {
            if (inv.isDeleted) continue
            val name = inv.wholesalerName.trim()
            if (name.isBlank()) continue
            val key = name.lowercase(Locale.ROOT)
            val current = supplierMap[key] ?: SupplierKhataSummary(
                wholesalerName = name,
                wholesalerPhone = inv.wholesalerPhone
            )
            val phone = if (current.wholesalerPhone.isBlank() && inv.wholesalerPhone.isNotBlank()) inv.wholesalerPhone else current.wholesalerPhone
            val totalPurchases = current.totalPurchases.add(inv.totalInvoiceCost)
            val totalPaid = current.totalPaid.add(inv.paidAmount)
            val lastDate = maxOf(current.lastTransactionDate, inv.date)
            supplierMap[key] = current.copy(
                wholesalerPhone = phone,
                totalPurchases = totalPurchases,
                totalPaid = totalPaid,
                invoiceCount = current.invoiceCount + 1,
                lastTransactionDate = lastDate
            )
        }

        for (pay in payments) {
            if (pay.isDeleted) continue
            val name = pay.wholesalerName.trim()
            if (name.isBlank()) continue
            val key = name.lowercase(Locale.ROOT)
            val current = supplierMap[key] ?: SupplierKhataSummary(wholesalerName = name)
            val totalPaid = current.totalPaid.add(pay.amount)
            val lastDate = maxOf(current.lastTransactionDate, pay.date)
            supplierMap[key] = current.copy(
                totalPaid = totalPaid,
                lastTransactionDate = lastDate
            )
        }

        supplierMap.values.map { summary ->
            val balance = summary.totalPurchases.subtract(summary.totalPaid)
            summary.copy(balancePayable = balance)
        }.sortedByDescending { it.balancePayable }
    }

    private val _cachedActiveUser = kotlinx.coroutines.flow.MutableStateFlow<User?>(null)

    val activeUserFlow: Flow<User?> = combine(
        settingsDao.observeSettings().catch { emit(null) }.flatMapLatest { settings ->
            val uid = settings?.activeUserId
            if (uid != null) {
                userDao.observeUserById(uid).catch { emit(null) }
            } else {
                flowOf(null)
            }
        },
        _cachedActiveUser
    ) { dbUser, cached ->
        dbUser ?: cached
    }.catch { emit(_cachedActiveUser.value) }

    suspend fun getActiveUser(): User? = withContext(Dispatchers.IO) {
        try {
            val settings = settingsDao.getSettings()
            val uid = settings?.activeUserId ?: return@withContext _cachedActiveUser.value
            val u = userDao.getUserById(uid) ?: _cachedActiveUser.value
            if (u != null) {
                _cachedActiveUser.value = u
            }
            u
        } catch (_: Throwable) {
            _cachedActiveUser.value
        }
    }

    suspend fun setActiveUser(userId: String) = withContext(Dispatchers.IO) {
        try {
            settingsDao.ensureDefaultSettings()
            settingsDao.updateActiveUserId(userId)
            val u = userDao.getUserById(userId)
            if (u != null) {
                _cachedActiveUser.value = u
            }
        } catch (_: Throwable) {}
    }

    suspend fun setupInitialAdmin(name: String, pin: String): User = withContext(Dispatchers.IO) {
        val user = User(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            role = Role.ADMINISTRATOR,
            pinHash = if (pin.isNotEmpty()) SecurityUtils.hashPin(pin) else "",
            isActive = true,
            syncStatus = SyncStatus.PENDING
        )
        userDao.insertUser(user)
        setActiveUser(user.id)
        user
    }

    private fun triggerAutoSync() {
        try {
            com.example.PashuAaharApp.instance.firestoreSyncEngine.triggerSync()
        } catch (_: Exception) {}
    }

    suspend fun insertUser(user: User): User = withContext(Dispatchers.IO) {
        val existingById = userDao.getUserById(user.id)
        val existingByEmail = if (user.email.isNotBlank()) userDao.getUserByEmail(user.email) else null
        val existingByName = userDao.getUserByName(user.name)

        val target = existingById ?: existingByEmail ?: existingByName
        val finalUser = if (target != null) {
            if (target.id != user.id) {
                userDao.softDeleteUser(target.id)
            }
            target.copy(
                id = user.id,
                name = user.name,
                role = user.role,
                email = if (user.email.isNotBlank()) user.email else target.email,
                phone = if (user.phone.isNotBlank()) user.phone else target.phone,
                deviceInfo = if (user.deviceInfo.isNotBlank()) user.deviceInfo else target.deviceInfo,
                lastActiveAt = maxOf(user.lastActiveAt, target.lastActiveAt),
                pinHash = if (user.pinHash.isNotBlank()) user.pinHash else target.pinHash,
                isActive = true,
                isDeleted = false,
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING
            )
        } else {
            user.copy(isActive = true, isDeleted = false)
        }
        userDao.insertUser(finalUser)
        triggerAutoSync()
        finalUser
    }

    suspend fun getActiveUsersList(): List<User> = withContext(Dispatchers.IO) {
        userDao.getActiveUsers()
    }

    suspend fun getUserById(userId: String): User? = withContext(Dispatchers.IO) {
        userDao.getUserById(userId)
    }

    private suspend fun resolveActorRole(explicitRole: Role? = null, explicitUser: User? = null): Role? {
        return explicitRole ?: explicitUser?.role ?: getActiveUser()?.role
    }

    private suspend fun enforceActiveUserPermission(
        permission: Permission,
        explicitUser: User? = null,
        explicitRole: Role? = null
    ) {
        val role = explicitRole ?: explicitUser?.role ?: getActiveUser()?.role ?: return
        requirePermission(role, permission)
    }

    suspend fun addStaffMember(name: String, role: Role, currentAdmin: User): User = withContext(Dispatchers.IO) {
        requirePermission(currentAdmin.role, Permission.MANAGE_STAFF)
        val newUserId = UUID.randomUUID().toString()
        val user = User(
            id = newUserId,
            name = name.trim(),
            role = role,
            pinHash = "",
            isActive = true,
            syncStatus = SyncStatus.PENDING
        )
        userDao.insertUser(user)

        roleChangeLogDao.insertLog(
            RoleChangeLog(
                targetUserId = user.id,
                targetUserName = user.name,
                oldRole = null,
                newRole = role,
                changedByUserId = currentAdmin.id,
                changedByUserName = currentAdmin.name
            )
        )

        user
    }

    suspend fun changeStaffRole(targetUser: User, newRole: Role, adminUser: User) = withContext(Dispatchers.IO) {
        requirePermission(adminUser.role, Permission.MANAGE_STAFF)
        val oldRole = targetUser.role
        val updated = targetUser.copy(
            role = newRole,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING
        )
        userDao.updateUser(updated)
        roleChangeLogDao.insertLog(
            RoleChangeLog(
                targetUserId = targetUser.id,
                targetUserName = targetUser.name,
                oldRole = oldRole,
                newRole = newRole,
                changedByUserId = adminUser.id,
                changedByUserName = adminUser.name
            )
        )
        triggerAutoSync()
    }

    suspend fun toggleUserActiveStatus(user: User, actor: User? = null) = withContext(Dispatchers.IO) {
        enforceActiveUserPermission(Permission.MANAGE_STAFF, actor)
        val updated = user.copy(
            isActive = !user.isActive,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING
        )
        userDao.updateUser(updated)
        triggerAutoSync()
    }

    suspend fun saveCustomer(
        customer: Customer,
        actor: User? = null,
        actorRole: Role? = null
    ) = withContext(Dispatchers.IO) {
        val currentActor = actor ?: getActiveUser()
        val role = actorRole ?: currentActor?.role
        val existing = customerDao.getCustomerById(customer.id)
        if (role != null) {
            if (existing == null) {
                requirePermission(role, Permission.ADD_CUSTOMER)
                if (customer.creditLimit.compareTo(BigDecimal.ZERO) > 0 && !can(role, Permission.EDIT_CREDIT_LIMIT)) {
                    throw SecurityException("Only Admin can set customer credit limit")
                }
            } else {
                if (existing.creditLimit.compareTo(customer.creditLimit) != 0 && !can(role, Permission.EDIT_CREDIT_LIMIT)) {
                    throw SecurityException("Only Admin can modify customer credit limit")
                }
                if (!can(role, Permission.EDIT_CUSTOMER_FULL)) {
                    if (currentActor != null && !canEditCustomerBasic(currentActor, existing)) {
                        throw SecurityException("Staff can only edit customers they created")
                    }
                    if (existing.totalUdhari.compareTo(customer.totalUdhari) != 0 || existing.promiseDate != customer.promiseDate) {
                        throw SecurityException("Staff can only edit customer name, phone, and village")
                    }
                }
            }
        }
        customerDao.insertCustomer(customer.copy(syncStatus = SyncStatus.PENDING, updatedAt = System.currentTimeMillis()))
        if (existing != null && existing.creditLimit.compareTo(customer.creditLimit) != 0) {
            val actorUser = currentActor ?: User(id = "admin", name = "Admin", role = role ?: Role.ADMINISTRATOR, pinHash = "")
            billAuditLogDao.insertAuditLog(
                BillAuditLog(
                    saleId = customer.id,
                    customerId = customer.id,
                    customerName = customer.name,
                    productName = "Credit limit",
                    actionType = "CUSTOMER_EDIT",
                    previousSummary = CurrencyUtils.formatInr(existing.creditLimit),
                    newSummary = CurrencyUtils.formatInr(customer.creditLimit),
                    reason = "Credit limit ${CurrencyUtils.formatInr(existing.creditLimit)} → ${CurrencyUtils.formatInr(customer.creditLimit)}",
                    performedByUserId = actorUser.id,
                    performedByUserName = actorUser.name
                )
            )
        }
        triggerAutoSync()
    }

    suspend fun updateCustomerDetails(
        customerId: String,
        name: String,
        phone: String,
        village: String,
        creditLimit: BigDecimal? = null,
        actor: User? = null
    ) = withContext(Dispatchers.IO) {
        val existing = customerDao.getCustomerById(customerId)
            ?: throw IllegalArgumentException("Customer not found")
        val currentActor = actor ?: getActiveUser()
        if (currentActor != null) {
            if (!can(currentActor.role, Permission.EDIT_CUSTOMER_FULL) && !canEditCustomerBasic(currentActor, existing)) {
                throw SecurityException("Not allowed to edit this customer")
            }
            if (creditLimit != null && creditLimit.compareTo(existing.creditLimit) != 0 && !can(currentActor.role, Permission.EDIT_CREDIT_LIMIT)) {
                throw SecurityException("Only Admin can modify customer credit limit")
            }
        }
        val updated = existing.copy(
            name = name.trim(),
            phone = phone.trim(),
            village = village.trim(),
            creditLimit = if (creditLimit != null && (currentActor == null || can(currentActor.role, Permission.EDIT_CREDIT_LIMIT))) {
                creditLimit
            } else {
                existing.creditLimit
            },
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING
        )
        customerDao.insertCustomer(updated)
        triggerAutoSync()
    }

    suspend fun deleteCustomer(customerId: String, actor: User? = null) = withContext(Dispatchers.IO) {
        enforceActiveUserPermission(Permission.EDIT_CUSTOMER_FULL, actor)
        customerDao.softDeleteCustomer(customerId)
        triggerAutoSync()
    }

    suspend fun saveProduct(product: Product, actor: User? = null, performedByRole: Role? = null) = withContext(Dispatchers.IO) {
        enforceActiveUserPermission(Permission.EDIT_INVENTORY, actor, performedByRole)
        if (product.kgPerBag <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Bag weight (kgPerBag) must be greater than zero")
        }
        productDao.insertProduct(product.copy(syncStatus = SyncStatus.PENDING, updatedAt = System.currentTimeMillis()))
        triggerAutoSync()
    }

    suspend fun deleteProduct(productId: String, actor: User? = null, performedByRole: Role? = null) = withContext(Dispatchers.IO) {
        enforceActiveUserPermission(Permission.EDIT_INVENTORY, actor, performedByRole)
        productDao.softDeleteProduct(productId)
        triggerAutoSync()
    }

    /**
     * Records a Mixed-Unit Sale:
     * - Supports customized whole-bag rate (e.g. ₹1960/40kg) and custom loose per-kg rate (e.g. ₹50/kg)
     * - Validates and decrements Product stock atomically
     * - Stores snapshot of pricePerKgAtSale and costPerKgAtSale
     * - Recalculates Customer totalUdhari (preserving advance credit on overpayment)
     * - Generates shared BillData with sequential billNumber
     */
    suspend fun recordSale(
        customerId: String,
        productId: String,
        bags: Int,
        looseKg: BigDecimal,
        customPricePerKg: BigDecimal? = null,
        customBagPrice: BigDecimal? = null,
        customLoosePricePerKg: BigDecimal? = null,
        paidAmount: BigDecimal,
        soldByUserId: String,
        paymentMode: String = "CASH"
    ): Pair<SaleEntry, BillData> = withContext(Dispatchers.IO) {
        val result = recordMultiItemSale(
            customerId = customerId,
            items = listOf(
                SaleItemRequest(
                    productId = productId,
                    bags = bags,
                    looseKg = looseKg,
                    customBagPrice = customBagPrice,
                    customLoosePricePerKg = customLoosePricePerKg ?: customPricePerKg
                )
            ),
            paidAmount = paidAmount,
            soldByUserId = soldByUserId,
            paymentMode = paymentMode
        )
        Pair(result.first.first(), result.second)
    }

    /**
     * Records a multi-item sale in a single atomic transaction:
     * - Validates positive quantities/rates and checks aggregate per-product stock
     * - Assigns sequential billNumber from AppSettings
     * - Generates SaleEntry for each item and deducts stock
     * - Allocates paidAmount across items (preserving any overpayment as customer advance credit)
     * - Updates customer udhari balance once
     * - Returns all created sales and consolidated BillData
     */
    suspend fun recordMultiItemSale(
        customerId: String,
        items: List<SaleItemRequest>,
        paidAmount: BigDecimal,
        soldByUserId: String,
        performedByRole: Role? = null,
        paymentMode: String = "CASH"
    ): Pair<List<SaleEntry>, BillData> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) {
            throw IllegalArgumentException("At least one item is required to record a sale")
        }
        if (paidAmount < BigDecimal.ZERO) {
            throw IllegalArgumentException("Paid amount cannot be negative")
        }

        val user = userDao.getUserById(soldByUserId) ?: getActiveUser()
        val effectiveRole = performedByRole ?: user?.role
        if (effectiveRole != null) {
            requirePermission(effectiveRole, Permission.CREATE_BILL)
        }

        val normalizedPaymentMode = paymentMode.trim().uppercase(Locale.ROOT).ifBlank { "CASH" }

        val result = db.withTransaction {
            val customer = customerDao.getCustomerById(customerId)
                ?: throw IllegalArgumentException("Customer not found")
            val now = System.currentTimeMillis()

            // First pass: validate inputs and aggregate requested weight per product across all lines
            val requestedKgByProduct = mutableMapOf<String, BigDecimal>()
            val productCache = mutableMapOf<String, Product>()

            for (itemReq in items) {
                if (itemReq.bags < 0 || itemReq.looseKg < BigDecimal.ZERO) {
                    throw IllegalArgumentException("Sale quantities cannot be negative")
                }
                if (itemReq.customBagPrice != null && itemReq.customBagPrice < BigDecimal.ZERO) {
                    throw IllegalArgumentException("Custom bag price cannot be negative")
                }
                if (itemReq.customLoosePricePerKg != null && itemReq.customLoosePricePerKg < BigDecimal.ZERO) {
                    throw IllegalArgumentException("Custom loose price cannot be negative")
                }

                val product = productCache.getOrPut(itemReq.productId) {
                    productDao.getProductById(itemReq.productId)
                        ?: throw IllegalArgumentException("Product not found: ${itemReq.productId}")
                }

                val bagWeight = product.safeKgPerBag.multiply(BigDecimal(itemReq.bags))
                val totalQuantityKg = bagWeight.add(itemReq.looseKg)
                    .setScale(CurrencyUtils.WEIGHT_SCALE, CurrencyUtils.ROUNDING)
                if (totalQuantityKg <= BigDecimal.ZERO) {
                    throw IllegalArgumentException("Sale quantity for ${product.feedName} must be greater than zero")
                }

                requestedKgByProduct[product.id] = (requestedKgByProduct[product.id] ?: BigDecimal.ZERO).add(totalQuantityKg)
            }

            // Enforce stock availability per product
            for ((prodId, totalRequestedKg) in requestedKgByProduct) {
                val product = productCache.getValue(prodId)
                if (totalRequestedKg > product.currentStockKg) {
                    throw IllegalArgumentException(
                        "Insufficient stock for ${product.feedName}: requested ${CurrencyUtils.formatWeightKg(totalRequestedKg)}, available ${CurrencyUtils.formatWeightKg(product.currentStockKg)}"
                    )
                }
            }

            // Allocate sequential bill number atomically
            settingsDao.ensureDefaultSettings()
            val currentSettings = settingsDao.getSettings()
            val assignedBillNumInt = (currentSettings?.nextBillNumber ?: 1001).coerceAtLeast(1001)
            val billNumber = assignedBillNumInt.toString()
            settingsDao.updateNextBillNumber(assignedBillNumInt + 1)

            val runningStockByProduct = productCache.mapValues { it.value.currentStockKg }.toMutableMap()
            var calculatedSubtotal = BigDecimal.ZERO
            val createdSales = mutableListOf<SaleEntry>()
            val billItems = mutableListOf<BillItem>()

            for (itemReq in items) {
                val product = productCache.getValue(itemReq.productId)

                if (effectiveRole != null && !can(effectiveRole, Permission.OVERRIDE_BILL_RATE)) {
                    val hasCustomBagRate = itemReq.customBagPrice != null &&
                        itemReq.customBagPrice.compareTo(product.bagSellingPrice) != 0
                    val hasCustomLooseRate = itemReq.customLoosePricePerKg != null &&
                        itemReq.customLoosePricePerKg.compareTo(product.effectiveLooseRatePerKg) != 0
                    if (hasCustomBagRate || hasCustomLooseRate) {
                        throw SecurityException("Staff cannot override listed billing rates")
                    }
                }

                val bagWeight = product.safeKgPerBag.multiply(BigDecimal(itemReq.bags))
                val totalQuantityKg = bagWeight.add(itemReq.looseKg)
                    .setScale(CurrencyUtils.WEIGHT_SCALE, CurrencyUtils.ROUNDING)

                val effectiveBagRate = itemReq.customBagPrice ?: product.bagSellingPrice
                val effectiveLooseRate = itemReq.customLoosePricePerKg ?: product.effectiveLooseRatePerKg

                val bagTotal = if (itemReq.bags > 0) effectiveBagRate.multiply(BigDecimal(itemReq.bags)) else BigDecimal.ZERO
                val looseTotal = if (itemReq.looseKg > BigDecimal.ZERO) effectiveLooseRate.multiply(itemReq.looseKg) else BigDecimal.ZERO
                val itemSalePrice = bagTotal.add(looseTotal).setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)

                calculatedSubtotal = calculatedSubtotal.add(itemSalePrice)

                val effectivePricePerKg = if (totalQuantityKg > BigDecimal.ZERO) {
                    itemSalePrice.divide(totalQuantityKg, CurrencyUtils.MONEY_SCALE, RoundingMode.HALF_UP)
                } else effectiveLooseRate

                val costPerKg = product.averagePurchaseCostPerKg.setScale(4, RoundingMode.HALF_UP)

                val sale = SaleEntry(
                    id = UUID.randomUUID().toString(),
                    date = now,
                    customerId = customerId,
                    productId = itemReq.productId,
                    quantityKg = totalQuantityKg,
                    pricePerKgAtSale = effectivePricePerKg,
                    costPerKgAtSale = costPerKg,
                    totalSalePrice = itemSalePrice,
                    paidAmount = BigDecimal.ZERO,
                    udhariAmount = itemSalePrice,
                    paymentStatus = PaymentStatus.UNPAID,
                    soldByUserId = soldByUserId,
                    billNumber = billNumber,
                    paymentMode = normalizedPaymentMode,
                    syncStatus = SyncStatus.PENDING
                )
                createdSales.add(sale)

                // Deduct stock from running balance so duplicate product lines accumulate accurately
                val priorStock = runningStockByProduct[product.id] ?: product.currentStockKg
                val newStock = priorStock.subtract(totalQuantityKg).setScale(CurrencyUtils.WEIGHT_SCALE, CurrencyUtils.ROUNDING)
                runningStockByProduct[product.id] = newStock
                productDao.updateProductStock(product.id, newStock.toPlainString())

                billItems.add(
                    BillItem(
                        feedName = product.feedName,
                        bags = itemReq.bags,
                        looseKg = itemReq.looseKg,
                        totalKg = totalQuantityKg,
                        pricePerKg = effectivePricePerKg,
                        itemTotal = itemSalePrice
                    )
                )
            }

            // Allocate paid amount across sales (preserving any overpayment on the final line item as advance credit)
            val normalizedPaid = paidAmount.setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
            val overallUdhari = calculatedSubtotal.subtract(normalizedPaid).max(BigDecimal.ZERO)
                .setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)

            var remainingPaidToAllocate = normalizedPaid
            val finalSales = createdSales.mapIndexed { index, s ->
                val paidForThis = if (index == createdSales.lastIndex) {
                    remainingPaidToAllocate
                } else {
                    remainingPaidToAllocate.min(s.totalSalePrice)
                }
                remainingPaidToAllocate = remainingPaidToAllocate.subtract(paidForThis).max(BigDecimal.ZERO)
                val udhariForThis = s.totalSalePrice.subtract(paidForThis).max(BigDecimal.ZERO)
                    .setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
                val status = when {
                    paidForThis >= s.totalSalePrice -> PaymentStatus.PAID
                    paidForThis > BigDecimal.ZERO -> PaymentStatus.PARTIAL
                    else -> PaymentStatus.UNPAID
                }
                s.copy(
                    paidAmount = paidForThis,
                    udhariAmount = udhariForThis,
                    paymentStatus = status
                )
            }

            for (sale in finalSales) {
                saleDao.insertSale(sale)
            }

            val newTotalUdhari = recalculateCustomerUdhariInternal(customerId)

            val billData = BillData(
                billNumber = billNumber,
                date = now,
                customerName = customer.name,
                customerPhone = customer.phone,
                customerVillage = customer.village,
                items = billItems,
                subtotal = calculatedSubtotal,
                paidAmount = normalizedPaid,
                udhariAmount = overallUdhari,
                previousUdhari = customer.totalUdhari,
                newTotalUdhari = newTotalUdhari,
                staffName = user?.name ?: "Staff"
            )

            Pair(finalSales, billData)
        }

        triggerAutoSync()
        result
    }

    suspend fun updateDailyRate(
        productId: String,
        bagPrice: BigDecimal?,
        looseRatePerKg: BigDecimal,
        actor: User? = null,
        performedByRole: Role? = null
    ) = withContext(Dispatchers.IO) {
        enforceActiveUserPermission(Permission.EDIT_DAILY_RATES, actor, performedByRole)
        val product = productDao.getProductById(productId) ?: return@withContext
        val oldBagPrice = product.bagSellingPrice
        val oldLoosePrice = product.effectiveLooseRatePerKg
        val sellingPricePerKg = if (bagPrice != null && product.safeKgPerBag > BigDecimal.ONE && !product.isLooseOnly) {
            bagPrice.divide(product.safeKgPerBag, 4, RoundingMode.HALF_UP)
        } else {
            looseRatePerKg
        }
        productDao.updateDailyRates(productId, sellingPricePerKg, looseRatePerKg)

        val actorUser = actor ?: getActiveUser() ?: User(id = "admin", name = "Admin", role = performedByRole ?: Role.ADMINISTRATOR, pinHash = "")
        val newBagPrice = bagPrice ?: sellingPricePerKg.multiply(product.safeKgPerBag)
        val prevStr = if (!product.isLooseOnly && product.safeKgPerBag > BigDecimal.ONE) {
            "bag ${CurrencyUtils.formatInr(oldBagPrice)}"
        } else {
            "${CurrencyUtils.formatInr(oldLoosePrice)}/kg"
        }
        val newStr = if (!product.isLooseOnly && product.safeKgPerBag > BigDecimal.ONE) {
            CurrencyUtils.formatInr(newBagPrice)
        } else {
            "${CurrencyUtils.formatInr(looseRatePerKg)}/kg"
        }
        if (prevStr != "bag $newStr" && prevStr != newStr) {
            billAuditLogDao.insertAuditLog(
                BillAuditLog(
                    saleId = product.id,
                    customerId = "",
                    customerName = product.feedName,
                    productName = product.feedName,
                    actionType = "RATE",
                    previousSummary = prevStr,
                    newSummary = newStr,
                    reason = "${product.feedName} $prevStr → $newStr",
                    performedByUserId = actorUser.id,
                    performedByUserName = actorUser.name
                )
            )
        }
        triggerAutoSync()
    }

    /**
     * Records a Standalone Udhari Repayment
     */
    suspend fun recordPayment(
        customerId: String,
        amount: BigDecimal,
        note: String,
        collectedByUserId: String,
        actorRole: Role? = null,
        performedByRole: Role? = null,
        paymentMode: String = "CASH"
    ): PaymentEntry = withContext(Dispatchers.IO) {
        if (amount <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Payment amount must be greater than zero")
        }
        val collector = userDao.getUserById(collectedByUserId) ?: getActiveUser()
        val effectiveRole = actorRole ?: performedByRole ?: collector?.role
        if (effectiveRole != null) {
            requirePermission(effectiveRole, Permission.ACCESS_COLLECTIONS)
        }
        val resolvedMode = when {
            paymentMode.isNotBlank() && paymentMode.uppercase(Locale.ROOT) != "CASH" -> paymentMode.trim().uppercase(Locale.ROOT)
            note.contains("[UPI]", ignoreCase = true) -> "UPI"
            note.contains("[BANK]", ignoreCase = true) -> "BANK"
            else -> paymentMode.trim().uppercase(Locale.ROOT).ifBlank { "CASH" }
        }
        val payment = PaymentEntry(
            id = UUID.randomUUID().toString(),
            date = System.currentTimeMillis(),
            customerId = customerId,
            amount = amount.setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING),
            note = note.trim(),
            collectedByUserId = collectedByUserId,
            paymentMode = resolvedMode,
            syncStatus = SyncStatus.PENDING
        )
        paymentDao.insertPayment(payment)
        recalculateCustomerUdhariInternal(customerId)
        triggerAutoSync()
        payment
    }

    /**
     * Records Purchase & Updates Moving Average Cost (at 4-decimal precision)
     * newAvgCost = ((currentStockKg * currentAvgCost) + totalCost) / (currentStockKg + newKg)
     */
    suspend fun recordPurchase(
        wholesalerName: String,
        productId: String,
        quantityKg: BigDecimal,
        totalPurchaseCost: BigDecimal,
        paymentStatus: String,
        recordedByUserId: String
    ): PurchaseEntry = withContext(Dispatchers.IO) {
        if (quantityKg <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Purchase quantity must be greater than zero")
        }
        if (totalPurchaseCost <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Total purchase cost must be greater than zero")
        }
        val recorder = userDao.getUserById(recordedByUserId) ?: getActiveUser()
        if (recorder != null) {
            requirePermission(recorder.role, Permission.EDIT_SUPPLIERS)
        }
        val product = productDao.getProductById(productId)
            ?: throw IllegalArgumentException("Product not found")

        val currentStock = product.currentStockKg.max(BigDecimal.ZERO)
        val currentAvgCost = product.averagePurchaseCostPerKg
        val newKg = quantityKg.setScale(CurrencyUtils.WEIGHT_SCALE, CurrencyUtils.ROUNDING)
        val totalCost = totalPurchaseCost.setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)

        val unitPurchasePrice = if (newKg > BigDecimal.ZERO) {
            totalCost.divide(newKg, 4, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        val currentValuation = currentStock.multiply(currentAvgCost)
        val newValuation = totalCost
        val totalWeight = currentStock.add(newKg)

        val newAvgCost = if (totalWeight > BigDecimal.ZERO) {
            currentValuation.add(newValuation).divide(totalWeight, 4, RoundingMode.HALF_UP)
        } else {
            unitPurchasePrice.setScale(4, RoundingMode.HALF_UP)
        }

        val newStock = product.currentStockKg.add(newKg).setScale(CurrencyUtils.WEIGHT_SCALE, CurrencyUtils.ROUNDING)

        // Update product stock and average cost
        productDao.updateProductStockAndAvgCost(
            productId = productId,
            stockKg = newStock.toPlainString(),
            avgCost = newAvgCost.toPlainString()
        )

        val purchase = PurchaseEntry(
            id = UUID.randomUUID().toString(),
            date = System.currentTimeMillis(),
            wholesalerName = wholesalerName.trim(),
            productId = productId,
            quantityKg = newKg,
            totalPurchaseCost = totalCost,
            paymentStatus = paymentStatus,
            recordedByUserId = recordedByUserId,
            syncStatus = SyncStatus.PENDING
        )
        purchaseDao.insertPurchase(purchase)
        triggerAutoSync()
        purchase
    }

    private suspend fun recalculateCustomerUdhariInternal(customerId: String): BigDecimal =
        recalculateCustomerUdhari(customerId, markPending = true)

    /**
     * Recalculation of customer Udhari:
     * Opening balance + sum of (totalSalePrice - paidAmount) from active sales minus standalone payments.
     * Negative values represent customer advance credit.
     */
    suspend fun recalculateCustomerUdhari(customerId: String, markPending: Boolean = true): BigDecimal = withContext(Dispatchers.IO) {
        val existingCustomer = customerDao.getCustomerById(customerId)
        val sales = saleDao.getSalesListByCustomer(customerId)
        var totalSaleUdhari = existingCustomer?.getOpeningUdhari() ?: BigDecimal.ZERO
        sales.forEach { s ->
            if (!s.isDeleted && s.billStatus != "CANCELLED") {
                val signedBalance = s.totalSalePrice.subtract(s.paidAmount)
                totalSaleUdhari = totalSaleUdhari.add(signedBalance)
            }
        }

        val payments = paymentDao.getPaymentsListByCustomer(customerId)
        var totalStandalonePayments = BigDecimal.ZERO
        payments.forEach { p ->
            if (!p.isDeleted) {
                totalStandalonePayments = totalStandalonePayments.add(p.amount)
            }
        }

        val netUdhari = totalSaleUdhari.subtract(totalStandalonePayments)
            .setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)

        val status = if (markPending) {
            "PENDING"
        } else {
            existingCustomer?.syncStatus?.name ?: "SYNCED"
        }
        customerDao.updateCustomerUdhari(
            customerId = customerId,
            totalUdhari = netUdhari.toPlainString(),
            syncStatus = status
        )
        netUdhari
    }

    suspend fun updateSettings(
        watchThreshold: BigDecimal,
        criticalThreshold: BigDecimal,
        appsScriptUrl: String = "",
        actor: User? = null,
        performedByRole: Role? = null
    ) = withContext(Dispatchers.IO) {
        enforceActiveUserPermission(Permission.MANAGE_SHOP_PROFILE, actor, performedByRole)
        settingsDao.ensureDefaultSettings()
        settingsDao.updateThresholdsAndScript(
            watchThreshold = watchThreshold,
            criticalThreshold = criticalThreshold,
            appsScriptUrl = appsScriptUrl.trim()
        )
    }

    suspend fun updateStoreProfile(
        shopName: String,
        shopAddress: String,
        shopPhone: String,
        shopGstin: String,
        shopUpiId: String,
        watchThreshold: BigDecimal,
        criticalThreshold: BigDecimal,
        actor: User? = null,
        performedByRole: Role? = null
    ) = withContext(Dispatchers.IO) {
        enforceActiveUserPermission(Permission.MANAGE_SHOP_PROFILE, actor, performedByRole)
        settingsDao.ensureDefaultSettings()
        settingsDao.updateStoreProfileFields(
            shopName = shopName.trim().ifBlank { "KhataWise Store" },
            shopAddress = shopAddress.trim(),
            shopPhone = shopPhone.trim(),
            shopGstin = shopGstin.trim(),
            shopUpiId = shopUpiId.trim(),
            watchThreshold = watchThreshold,
            criticalThreshold = criticalThreshold
        )
        triggerAutoSync()
    }

    suspend fun savePairedStore(
        storeAdminUid: String,
        shopName: String,
        shopAddress: String,
        shopPhone: String
    ) = withContext(Dispatchers.IO) {
        settingsDao.ensureDefaultSettings()
        settingsDao.updatePairedStoreFields(
            storeAdminUid = storeAdminUid.trim(),
            shopName = shopName.trim().ifBlank { "KhataWise Store" },
            shopAddress = shopAddress.trim(),
            shopPhone = shopPhone.trim()
        )
        triggerAutoSync()
    }

    suspend fun updateThemeMode(themeMode: String) = withContext(Dispatchers.IO) {
        settingsDao.ensureDefaultSettings()
        settingsDao.updateThemeModeField(themeMode)
    }

    suspend fun updateLanguage(language: String) = withContext(Dispatchers.IO) {
        settingsDao.ensureDefaultSettings()
        settingsDao.updateLanguageField(language)
    }

    suspend fun updateLastSyncTime(timestamp: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        settingsDao.ensureDefaultSettings()
        settingsDao.updateLastSyncTime(timestamp)
    }

    suspend fun deleteUser(userId: String, actor: User? = null) = withContext(Dispatchers.IO) {
        enforceActiveUserPermission(Permission.MANAGE_STAFF, actor)
        userDao.softDeleteUser(userId)
        triggerAutoSync()
    }

    suspend fun getSalesBetween(start: Long, end: Long): List<SaleEntry> = withContext(Dispatchers.IO) {
        saleDao.getSalesBetween(start, end)
    }

    suspend fun getPaymentsBetween(start: Long, end: Long): List<PaymentEntry> = withContext(Dispatchers.IO) {
        paymentDao.getPaymentsBetween(start, end)
    }

    suspend fun getPurchasesBetween(start: Long, end: Long): List<PurchaseEntry> = withContext(Dispatchers.IO) {
        purchaseDao.getPurchasesBetween(start, end)
    }

    /**
     * Records a Multi-Item Purchase Invoice:
     * - Multi-item feed support (bags & loose kg)
     * - Optional Ladhan (loading), Utaar (unloading), and Gaadi Kiraya (freight)
     * - Pro-rata landing cost (with exact remainder conservation) & 4-decimal moving-average cost update
     * - Payment & Balance tracking for Wholesaler Ledger
     */
    suspend fun recordPurchaseInvoice(
        wholesalerName: String,
        wholesalerPhone: String = "",
        invoiceNumber: String = "",
        items: List<PurchaseInvoiceItemRequest>,
        ladhan: BigDecimal = BigDecimal.ZERO,
        utaar: BigDecimal = BigDecimal.ZERO,
        gaadiKiraya: BigDecimal = BigDecimal.ZERO,
        paidAmount: BigDecimal = BigDecimal.ZERO,
        notes: String = "",
        recordedByUserId: String,
        performedByRole: Role? = null
    ): PurchaseInvoice = withContext(Dispatchers.IO) {
        if (items.isEmpty()) {
            throw IllegalArgumentException("At least one item is required for a purchase invoice")
        }
        if (ladhan < BigDecimal.ZERO || utaar < BigDecimal.ZERO || gaadiKiraya < BigDecimal.ZERO) {
            throw IllegalArgumentException("Handling charges cannot be negative")
        }
        if (paidAmount < BigDecimal.ZERO) {
            throw IllegalArgumentException("Paid amount cannot be negative")
        }

        val actorRole = resolveActorRole(performedByRole)
        requirePermission(actorRole, Permission.ACCESS_SUPPLIERS)
        val now = System.currentTimeMillis()
        val invNum = if (invoiceNumber.isNotBlank()) invoiceNumber.trim() else "PUR-${now % 1000000}"

        val normLadhan = ladhan.setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
        val normUtaar = utaar.setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
        val normGaadiKiraya = gaadiKiraya.setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
        val totalHandlingCharges = normLadhan.add(normUtaar).add(normGaadiKiraya)

        val invoice = db.withTransaction {
            var goodsSubtotal = BigDecimal.ZERO
            var totalInvoiceKg = BigDecimal.ZERO

            val calculatedItems = mutableListOf<PurchaseInvoiceItem>()

            for (itemReq in items) {
                if (itemReq.bags < 0 || itemReq.looseKg < BigDecimal.ZERO) {
                    throw IllegalArgumentException("Purchase quantities cannot be negative")
                }
                val product = productDao.getProductById(itemReq.productId)
                    ?: throw IllegalArgumentException("Product not found: ${itemReq.productId}")

                val bagKg = product.safeKgPerBag
                val bagWeight = bagKg.multiply(BigDecimal(itemReq.bags))
                val totalItemKg = bagWeight.add(itemReq.looseKg).setScale(CurrencyUtils.WEIGHT_SCALE, CurrencyUtils.ROUNDING)
                if (totalItemKg <= BigDecimal.ZERO) {
                    throw IllegalArgumentException("Purchase quantity for ${product.feedName} must be greater than zero")
                }

                // Resolve purchase rates using averagePurchaseCostPerKg (never retail sellingPricePerKg)
                val defaultCostPerKg = product.averagePurchaseCostPerKg
                val defaultCostPerBag = if (!product.isLooseOnly && bagKg > BigDecimal.ONE) {
                    defaultCostPerKg.multiply(bagKg).setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
                } else null

                val ratePerBag: BigDecimal? = when {
                    itemReq.customBagPrice != null -> itemReq.customBagPrice
                    itemReq.customPricePerKg != null && !product.isLooseOnly && bagKg > BigDecimal.ONE ->
                        itemReq.customPricePerKg.multiply(bagKg).setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
                    else -> defaultCostPerBag
                }

                val ratePerKg: BigDecimal = when {
                    itemReq.customPricePerKg != null -> itemReq.customPricePerKg
                    itemReq.customBagPrice != null && !product.isLooseOnly && bagKg > BigDecimal.ONE ->
                        itemReq.customBagPrice.divide(bagKg, 4, RoundingMode.HALF_UP)
                    else -> defaultCostPerKg
                }

                if (ratePerKg <= BigDecimal.ZERO && (ratePerBag == null || ratePerBag <= BigDecimal.ZERO)) {
                    throw IllegalArgumentException("Purchase rate must be greater than zero for ${product.feedName}")
                }

                val itemCost = if (!product.isLooseOnly && bagKg > BigDecimal.ONE && ratePerBag != null) {
                    val bagCost = ratePerBag.multiply(BigDecimal(itemReq.bags))
                    val looseCost = ratePerKg.multiply(itemReq.looseKg)
                    bagCost.add(looseCost).setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
                } else {
                    ratePerKg.multiply(totalItemKg).setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
                }

                goodsSubtotal = goodsSubtotal.add(itemCost)
                totalInvoiceKg = totalInvoiceKg.add(totalItemKg)

                calculatedItems.add(
                    PurchaseInvoiceItem(
                        productId = product.id,
                        feedName = product.feedName,
                        bags = itemReq.bags,
                        looseKg = itemReq.looseKg,
                        totalKg = totalItemKg,
                        ratePerBag = ratePerBag,
                        ratePerKg = ratePerKg,
                        itemCost = itemCost
                    )
                )
            }

            val grandTotal = goodsSubtotal.add(totalHandlingCharges).setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
            val normPaid = paidAmount.setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
            val balance = grandTotal.subtract(normPaid).max(BigDecimal.ZERO).setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)

            val paymentStatus = when {
                normPaid >= grandTotal -> PaymentStatus.PAID
                normPaid > BigDecimal.ZERO -> PaymentStatus.PARTIAL
                else -> PaymentStatus.UNPAID
            }

            var allocatedHandlingSoFar = BigDecimal.ZERO

            // Update product stock and moving average costs (conserving exact handling remainder on last item)
            calculatedItems.forEachIndexed { index, item ->
                val product = productDao.getProductById(item.productId) ?: return@forEachIndexed

                val itemHandlingShare = if (totalInvoiceKg > BigDecimal.ZERO) {
                    if (index == calculatedItems.lastIndex) {
                        totalHandlingCharges.subtract(allocatedHandlingSoFar).max(BigDecimal.ZERO)
                            .setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
                    } else {
                        val share = totalHandlingCharges.multiply(item.totalKg)
                            .divide(totalInvoiceKg, CurrencyUtils.MONEY_SCALE, RoundingMode.HALF_UP)
                        allocatedHandlingSoFar = allocatedHandlingSoFar.add(share)
                        share
                    }
                } else BigDecimal.ZERO

                val itemLandedCost = item.itemCost.add(itemHandlingShare)
                    .setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)

                val currentStock = product.currentStockKg.max(BigDecimal.ZERO)
                val currentAvgCost = product.averagePurchaseCostPerKg
                val newKg = item.totalKg

                val unitLandedRate = if (newKg > BigDecimal.ZERO) {
                    itemLandedCost.divide(newKg, 4, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO

                val currentValuation = currentStock.multiply(currentAvgCost)
                val newValuation = itemLandedCost
                val totalWeight = currentStock.add(newKg)

                val newAvgCost = if (totalWeight > BigDecimal.ZERO) {
                    currentValuation.add(newValuation).divide(totalWeight, 4, RoundingMode.HALF_UP)
                } else {
                    unitLandedRate.setScale(4, RoundingMode.HALF_UP)
                }

                val newStock = product.currentStockKg.add(newKg).setScale(CurrencyUtils.WEIGHT_SCALE, CurrencyUtils.ROUNDING)

                productDao.updateProductStockAndAvgCost(
                    productId = product.id,
                    stockKg = newStock.toPlainString(),
                    avgCost = newAvgCost.toPlainString()
                )

                val purchaseEntry = PurchaseEntry(
                    id = UUID.randomUUID().toString(),
                    date = now,
                    wholesalerName = wholesalerName.trim(),
                    productId = product.id,
                    quantityKg = newKg,
                    totalPurchaseCost = itemLandedCost,
                    paymentStatus = paymentStatus.name,
                    recordedByUserId = recordedByUserId,
                    syncStatus = SyncStatus.PENDING
                )
                purchaseDao.insertPurchase(purchaseEntry)
            }

            val createdInvoice = PurchaseInvoice(
                id = UUID.randomUUID().toString(),
                invoiceNumber = invNum,
                date = now,
                wholesalerName = wholesalerName.trim(),
                wholesalerPhone = wholesalerPhone.trim(),
                itemsJson = PurchaseInvoice.serializeItems(calculatedItems),
                ladhan = normLadhan,
                utaar = normUtaar,
                gaadiKiraya = normGaadiKiraya,
                goodsSubtotal = goodsSubtotal,
                totalInvoiceCost = grandTotal,
                paidAmount = normPaid,
                balanceAmount = balance,
                paymentStatus = paymentStatus,
                notes = notes.trim(),
                recordedByUserId = recordedByUserId,
                syncStatus = SyncStatus.PENDING
            )

            purchaseInvoiceDao.insertInvoice(createdInvoice)
            createdInvoice
        }

        triggerAutoSync()
        invoice
    }

    suspend fun recordSupplierPayment(
        wholesalerName: String,
        amount: BigDecimal,
        paymentMode: String,
        note: String,
        recordedByUserId: String,
        performedByRole: Role? = null
    ): SupplierPayment = withContext(Dispatchers.IO) {
        if (amount <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Supplier payment amount must be greater than zero")
        }
        val actorRole = resolveActorRole(performedByRole)
        requirePermission(actorRole, Permission.ACCESS_SUPPLIERS)
        val payment = SupplierPayment(
            id = UUID.randomUUID().toString(),
            date = System.currentTimeMillis(),
            wholesalerName = wholesalerName.trim(),
            amount = amount.setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING),
            paymentMode = paymentMode.trim(),
            note = note.trim(),
            recordedByUserId = recordedByUserId,
            syncStatus = SyncStatus.PENDING
        )
        supplierPaymentDao.insertPayment(payment)
        triggerAutoSync()
        payment
    }

    suspend fun softDeletePurchaseInvoice(invoiceId: String, performedByRole: Role? = null) = withContext(Dispatchers.IO) {
        val actorRole = resolveActorRole(performedByRole)
        requirePermission(actorRole, Permission.ACCESS_SUPPLIERS)

        db.withTransaction {
            val invoice = purchaseInvoiceDao.getInvoiceById(invoiceId) ?: return@withTransaction
            val items = invoice.getItems()

            // First pass: verify each product has enough stock to reverse the purchase
            val requiredKgByProduct = mutableMapOf<String, BigDecimal>()
            for (item in items) {
                if (item.totalKg > BigDecimal.ZERO) {
                    requiredKgByProduct[item.productId] =
                        (requiredKgByProduct[item.productId] ?: BigDecimal.ZERO).add(item.totalKg)
                }
            }

            for ((prodId, requiredKg) in requiredKgByProduct) {
                val product = productDao.getProductById(prodId) ?: continue
                if (product.currentStockKg < requiredKg) {
                    throw IllegalStateException(
                        "Cannot delete invoice ${invoice.invoiceNumber}: current stock for ${product.feedName} (${CurrencyUtils.formatWeightKg(product.currentStockKg)}) is less than invoice quantity (${CurrencyUtils.formatWeightKg(requiredKg)})."
                    )
                }
            }

            val totalHandlingCharges = invoice.ladhan.add(invoice.utaar).add(invoice.gaadiKiraya)
            val totalInvoiceKg = items.fold(BigDecimal.ZERO) { acc, it -> acc.add(it.totalKg) }
            var allocatedHandlingSoFar = BigDecimal.ZERO

            items.forEachIndexed { index, item ->
                val product = productDao.getProductById(item.productId) ?: return@forEachIndexed
                val itemHandlingShare = if (totalInvoiceKg > BigDecimal.ZERO) {
                    if (index == items.lastIndex) {
                        totalHandlingCharges.subtract(allocatedHandlingSoFar).max(BigDecimal.ZERO)
                            .setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
                    } else {
                        val share = totalHandlingCharges.multiply(item.totalKg)
                            .divide(totalInvoiceKg, CurrencyUtils.MONEY_SCALE, RoundingMode.HALF_UP)
                        allocatedHandlingSoFar = allocatedHandlingSoFar.add(share)
                        share
                    }
                } else BigDecimal.ZERO

                val itemLandedCost = item.itemCost.add(itemHandlingShare)
                    .setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)

                val currentStock = product.currentStockKg.max(BigDecimal.ZERO)
                val currentAvgCost = product.averagePurchaseCostPerKg
                val removedKg = item.totalKg
                val remainingStock = currentStock.subtract(removedKg).max(BigDecimal.ZERO)
                    .setScale(CurrencyUtils.WEIGHT_SCALE, CurrencyUtils.ROUNDING)

                val priorValuation = currentStock.multiply(currentAvgCost).subtract(itemLandedCost).max(BigDecimal.ZERO)
                val restoredAvgCost = if (remainingStock > BigDecimal.ZERO) {
                    priorValuation.divide(remainingStock, 4, RoundingMode.HALF_UP)
                } else {
                    currentAvgCost
                }

                productDao.updateProductStockAndAvgCost(
                    productId = product.id,
                    stockKg = remainingStock.toPlainString(),
                    avgCost = restoredAvgCost.toPlainString()
                )
            }

            val linkedPurchases = purchaseDao.getPurchasesForInvoiceWindow(invoice.wholesalerName, invoice.date)
            for (pe in linkedPurchases) {
                purchaseDao.softDeletePurchase(pe.id)
            }

            purchaseInvoiceDao.softDeleteInvoice(invoiceId)
        }

        triggerAutoSync()
    }

    suspend fun softDeleteSupplierPayment(paymentId: String, performedByRole: Role? = null) = withContext(Dispatchers.IO) {
        val actorRole = resolveActorRole(performedByRole)
        requirePermission(actorRole, Permission.ACCESS_SUPPLIERS)
        supplierPaymentDao.softDeletePayment(paymentId)
        triggerAutoSync()
    }

    suspend fun getSupplierLedger(wholesalerName: String): List<SupplierLedgerEntry> = withContext(Dispatchers.IO) {
        val invoices = purchaseInvoiceDao.getInvoicesListForWholesaler(wholesalerName)
        val payments = supplierPaymentDao.getPaymentsListForWholesaler(wholesalerName)

        val entries = mutableListOf<SupplierLedgerEntry>()
        invoices.forEach { entries.add(SupplierLedgerEntry.InvoiceEntry(it)) }
        payments.forEach { entries.add(SupplierLedgerEntry.PaymentEntry(it)) }

        entries.sortedByDescending { it.date }
    }

    suspend fun clearAllLocalDataAndSignOut() = withContext(Dispatchers.IO) {
        _cachedActiveUser.value = null
        try {
            com.example.PashuAaharApp.instance.firestoreSyncEngine.stopRealtimeListeners()
        } catch (_: Exception) {}
        try {
            db.clearAllTables()
            settingsDao.ensureDefaultSettings(AppSettings())
        } catch (_: Exception) {}
        try {
            com.example.auth.GoogleAuthManager.quickSignOut()
        } catch (_: Exception) {}
    }

    suspend fun updateStaffMember(userId: String, newRole: Role, newPin: String? = null, performedByRole: Role? = null) = withContext(Dispatchers.IO) {
        val actorRole = resolveActorRole(performedByRole)
        requirePermission(actorRole, Permission.MANAGE_STAFF)
        val user = userDao.getUserById(userId) ?: return@withContext
        val updated = user.copy(
            role = newRole,
            pinHash = if (!newPin.isNullOrBlank()) SecurityUtils.hashPin(newPin) else user.pinHash,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING
        )
        userDao.updateUser(updated)
        triggerAutoSync()
    }

    suspend fun updateAdminProfile(userId: String, name: String) = withContext(Dispatchers.IO) {
        val user = userDao.getUserById(userId) ?: return@withContext
        val updated = user.copy(
            name = name.trim(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING
        )
        userDao.updateUser(updated)
        triggerAutoSync()
    }

    suspend fun updateUserPin(userId: String, newPin: String) = withContext(Dispatchers.IO) {
        if (newPin.length != 4 || !newPin.all { it.isDigit() }) return@withContext
        val user = userDao.getUserById(userId) ?: return@withContext
        val updated = user.copy(
            pinHash = SecurityUtils.hashPin(newPin),
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING
        )
        userDao.updateUser(updated)
        SecurityUtils.resetLockout(userId)
        triggerAutoSync()
    }

    /**
     * Edits an existing Sale Bill:
     * - Rejects cancelled or deleted bills
     * - Adjusts Product stock by the difference in quantityKg (validating stock availability)
     * - Updates SaleEntry total, paid, udhari, and sets billStatus = "EDITED"
     * - Recalculates Customer Udhari (preserving any overpayment as advance credit)
     * - Records a BillAuditLog entry
     */
    suspend fun editSaleBill(
        saleId: String,
        newQuantityKg: BigDecimal,
        newPricePerKg: BigDecimal,
        newPaidAmount: BigDecimal,
        reason: String,
        performedBy: User
    ): SaleEntry? = withContext(Dispatchers.IO) {
        if (newQuantityKg <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Quantity must be greater than zero")
        }
        if (newPricePerKg < BigDecimal.ZERO) {
            throw IllegalArgumentException("Price per kg cannot be negative")
        }
        if (newPaidAmount < BigDecimal.ZERO) {
            throw IllegalArgumentException("Paid amount cannot be negative")
        }
        requirePermission(performedBy.role, Permission.EDIT_DELETE_BILL)
        val updatedSale = db.withTransaction {
            val existing = saleDao.getSaleById(saleId) ?: return@withTransaction null
            if (existing.isDeleted || existing.billStatus == "CANCELLED") {
                throw IllegalStateException("Cannot edit a cancelled or deleted bill")
            }

            val product = productDao.getProductById(existing.productId)
            val customer = customerDao.getCustomerById(existing.customerId)

            val normQty = newQuantityKg.setScale(CurrencyUtils.WEIGHT_SCALE, CurrencyUtils.ROUNDING)
            val normPrice = newPricePerKg.setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
            val normTotal = normQty.multiply(normPrice).setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
            val normPaid = newPaidAmount.setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
            val normUdhari = normTotal.subtract(normPaid).max(BigDecimal.ZERO).setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)

            val status = when {
                normPaid >= normTotal -> PaymentStatus.PAID
                normPaid > BigDecimal.ZERO -> PaymentStatus.PARTIAL
                else -> PaymentStatus.UNPAID
            }

            // Adjust stock: stock difference = oldActiveQty - newQty
            if (product != null) {
                val stockDelta = existing.quantityKg.subtract(normQty)
                val updatedStock = product.currentStockKg.add(stockDelta).setScale(CurrencyUtils.WEIGHT_SCALE, CurrencyUtils.ROUNDING)
                if (updatedStock < BigDecimal.ZERO) {
                    throw IllegalArgumentException(
                        "Insufficient stock for ${product.feedName}: available ${CurrencyUtils.formatWeightKg(product.currentStockKg)}"
                    )
                }
                productDao.updateProductStock(product.id, updatedStock.toPlainString())
            }

            val prevSummary = "${CurrencyUtils.formatWeight(existing.quantityKg)} kg @ ₹${existing.pricePerKgAtSale.stripTrailingZeros().toPlainString()}/kg • Total ${CurrencyUtils.formatInr(existing.totalSalePrice)} (Paid ${CurrencyUtils.formatInr(existing.paidAmount)})"
            val newSummary = "${CurrencyUtils.formatWeight(normQty)} kg @ ₹${normPrice.stripTrailingZeros().toPlainString()}/kg • Total ${CurrencyUtils.formatInr(normTotal)} (Paid ${CurrencyUtils.formatInr(normPaid)})"

            val edited = existing.copy(
                quantityKg = normQty,
                pricePerKgAtSale = normPrice,
                totalSalePrice = normTotal,
                paidAmount = normPaid,
                udhariAmount = normUdhari,
                paymentStatus = status,
                billStatus = "EDITED",
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING
            )
            saleDao.insertSale(edited)
            recalculateCustomerUdhariInternal(existing.customerId)

            billAuditLogDao.insertAuditLog(
                BillAuditLog(
                    saleId = existing.id,
                    customerId = existing.customerId,
                    customerName = customer?.name ?: "Customer",
                    productName = product?.feedName ?: "Feed Item",
                    actionType = "EDIT",
                    previousSummary = prevSummary,
                    newSummary = newSummary,
                    reason = reason.trim().ifBlank { "Bill modified" },
                    performedByUserId = performedBy.id,
                    performedByUserName = performedBy.name
                )
            )
            edited
        }
        if (updatedSale != null) {
            triggerAutoSync()
        }
        updatedSale
    }

    /**
     * Records a partial or full Return of items on a Sale Bill:
     * - Rejects cancelled or deleted bills
     * - Restores returnedKg back to Product stock if putBackInStock is true
     * - Reduces effective quantityKg and totalSalePrice proportionally to exact stored totalSalePrice
     * - Recalculates Customer Udhari (preserving any overpayment on paid bills as advance credit)
     * - Records a BillAuditLog entry
     */
    suspend fun returnSaleItems(
        saleId: String,
        returnKg: BigDecimal,
        reason: String,
        performedBy: User,
        putBackInStock: Boolean = true,
        settlementSummary: String = "",
        cashRefunded: BigDecimal = BigDecimal.ZERO
    ): SaleEntry? = withContext(Dispatchers.IO) {
        requirePermission(performedBy.role, Permission.EDIT_DELETE_BILL)
        var didModify = false
        val updatedSale = db.withTransaction {
            val existing = saleDao.getSaleById(saleId) ?: return@withTransaction null
            if (existing.isDeleted || existing.billStatus == "CANCELLED") {
                throw IllegalStateException("Cannot return items on a cancelled or deleted bill")
            }
            val validReturnKg = returnKg.min(existing.quantityKg).max(BigDecimal.ZERO)
                .setScale(CurrencyUtils.WEIGHT_SCALE, CurrencyUtils.ROUNDING)
            if (validReturnKg <= BigDecimal.ZERO) return@withTransaction existing

            val product = productDao.getProductById(existing.productId)
            val customer = customerDao.getCustomerById(existing.customerId)

            val remainingKg = existing.quantityKg.subtract(validReturnKg).max(BigDecimal.ZERO)
                .setScale(CurrencyUtils.WEIGHT_SCALE, CurrencyUtils.ROUNDING)
            val newTotal = if (existing.quantityKg > BigDecimal.ZERO && remainingKg > BigDecimal.ZERO) {
                existing.totalSalePrice.multiply(remainingKg)
                    .divide(existing.quantityKg, CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
            } else {
                BigDecimal.ZERO.setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
            }
            val validCashRefund = cashRefunded.max(BigDecimal.ZERO).min(existing.paidAmount)
                .setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
            val newPaid = existing.paidAmount.subtract(validCashRefund).max(BigDecimal.ZERO)
                .setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
            val newUdhari = newTotal.subtract(newPaid).max(BigDecimal.ZERO)
                .setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)

            val paymentStatus = when {
                newPaid >= newTotal -> PaymentStatus.PAID
                newPaid > BigDecimal.ZERO -> PaymentStatus.PARTIAL
                else -> PaymentStatus.UNPAID
            }

            if (product != null && putBackInStock) {
                val restoredStock = product.currentStockKg.add(validReturnKg)
                    .setScale(CurrencyUtils.WEIGHT_SCALE, CurrencyUtils.ROUNDING)
                productDao.updateProductStock(product.id, restoredStock.toPlainString())
            }

            val totalReturnedSoFar = existing.returnedKg.add(validReturnKg)
            val prevSummary = "${CurrencyUtils.formatWeight(existing.quantityKg)} kg • Total ${CurrencyUtils.formatInr(existing.totalSalePrice)}"
            val newSummary = if (settlementSummary.isNotBlank()) {
                "Returned ${CurrencyUtils.formatWeight(validReturnKg)} kg • $settlementSummary"
            } else {
                "Returned ${CurrencyUtils.formatWeight(validReturnKg)} kg • Net ${CurrencyUtils.formatWeight(remainingKg)} kg (${CurrencyUtils.formatInr(newTotal)})"
            }

            val returned = existing.copy(
                quantityKg = remainingKg,
                totalSalePrice = newTotal,
                paidAmount = newPaid,
                udhariAmount = newUdhari,
                paymentStatus = paymentStatus,
                returnedKg = totalReturnedSoFar,
                billStatus = "RETURNED",
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING
            )
            saleDao.insertSale(returned)
            recalculateCustomerUdhariInternal(existing.customerId)

            billAuditLogDao.insertAuditLog(
                BillAuditLog(
                    saleId = existing.id,
                    customerId = existing.customerId,
                    customerName = customer?.name ?: "Customer",
                    productName = product?.feedName ?: "Feed Item",
                    actionType = "RETURN",
                    previousSummary = prevSummary,
                    newSummary = newSummary,
                    reason = reason.trim().ifBlank { "Customer returned stock" },
                    performedByUserId = performedBy.id,
                    performedByUserName = performedBy.name
                )
            )
            didModify = true
            returned
        }
        if (didModify) {
            triggerAutoSync()
        }
        updatedSale
    }

    /**
     * Adds a new line item (`SaleEntry` with the same `date`, `billNumber`, and `customerId` as `anchorSale`)
     * to an existing consolidated bill during an Edit Bill flow.
     */
    suspend fun addLineItemToExistingBill(
        anchorSale: SaleEntry,
        productId: String,
        quantityKg: BigDecimal,
        pricePerKg: BigDecimal,
        reason: String,
        performedBy: User
    ): SaleEntry? = withContext(Dispatchers.IO) {
        if (anchorSale.isDeleted || anchorSale.billStatus == "CANCELLED") {
            throw IllegalStateException("Cannot add items to a cancelled or deleted bill")
        }
        if (quantityKg <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Quantity must be greater than zero")
        }
        if (pricePerKg < BigDecimal.ZERO) {
            throw IllegalArgumentException("Price per kg cannot be negative")
        }
        requirePermission(performedBy.role, Permission.EDIT_DELETE_BILL)
        val newSale = db.withTransaction {
            val product = productDao.getProductById(productId) ?: return@withTransaction null
            val customer = customerDao.getCustomerById(anchorSale.customerId)

            val normQty = quantityKg.setScale(CurrencyUtils.WEIGHT_SCALE, CurrencyUtils.ROUNDING)
            val normPrice = pricePerKg.setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
            val normTotal = normQty.multiply(normPrice).setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)

            if (normQty > product.currentStockKg) {
                throw IllegalArgumentException(
                    "Insufficient stock for ${product.feedName}: requested ${CurrencyUtils.formatWeightKg(normQty)}, available ${CurrencyUtils.formatWeightKg(product.currentStockKg)}"
                )
            }

            val updatedStock = product.currentStockKg.subtract(normQty).setScale(CurrencyUtils.WEIGHT_SCALE, CurrencyUtils.ROUNDING)
            productDao.updateProductStock(product.id, updatedStock.toPlainString())

            val created = SaleEntry(
                id = UUID.randomUUID().toString(),
                date = anchorSale.date,
                customerId = anchorSale.customerId,
                productId = product.id,
                quantityKg = normQty,
                pricePerKgAtSale = normPrice,
                costPerKgAtSale = product.averagePurchaseCostPerKg,
                totalSalePrice = normTotal,
                paidAmount = BigDecimal.ZERO,
                udhariAmount = normTotal,
                paymentStatus = PaymentStatus.UNPAID,
                soldByUserId = anchorSale.soldByUserId,
                billStatus = "EDITED",
                billNumber = anchorSale.billNumber,
                paymentMode = anchorSale.paymentMode,
                syncStatus = SyncStatus.PENDING
            )
            saleDao.insertSale(created)
            recalculateCustomerUdhariInternal(anchorSale.customerId)

            billAuditLogDao.insertAuditLog(
                BillAuditLog(
                    saleId = anchorSale.id,
                    customerId = anchorSale.customerId,
                    customerName = customer?.name ?: "Customer",
                    productName = product.feedName,
                    actionType = "EDIT",
                    previousSummary = "0 kg",
                    newSummary = "${CurrencyUtils.formatWeight(normQty)} kg (${CurrencyUtils.formatInr(normTotal)})",
                    reason = reason.trim().ifBlank { "Added item to bill" },
                    performedByUserId = performedBy.id,
                    performedByUserName = performedBy.name
                )
            )
            created
        }
        if (newSale != null) {
            triggerAutoSync()
        }
        newSale
    }

    /**
     * Cancels a Sale Bill completely:
     * - Restores remaining quantityKg back to Product stock
     * - Marks billStatus = "CANCELLED" and udhariAmount = 0
     * - Recalculates Customer Udhari
     * - Records a BillAuditLog entry
     */
    suspend fun cancelSaleBill(
        saleId: String,
        reason: String,
        performedBy: User
    ): SaleEntry? = withContext(Dispatchers.IO) {
        requirePermission(performedBy.role, Permission.EDIT_DELETE_BILL)
        var didModify = false
        val primaryUpdated = db.withTransaction {
            val existing = saleDao.getSaleById(saleId) ?: return@withTransaction null
            if (existing.billStatus == "CANCELLED") return@withTransaction existing

            val customer = customerDao.getCustomerById(existing.customerId)
            val allCustomerSales = saleDao.getSalesListByCustomer(existing.customerId)
            val siblingSales = allCustomerSales.filter {
                !it.isDeleted && it.billStatus != "CANCELLED" && (
                    (existing.billNumber.isNotBlank() && it.billNumber == existing.billNumber) ||
                        (existing.billNumber.isBlank() && it.billNumber.isBlank() && kotlin.math.abs(it.date - existing.date) <= 2500L)
                    )
            }.ifEmpty { listOf(existing) }

            var updatedPrimary: SaleEntry? = null
            for (itemSale in siblingSales) {
                val product = productDao.getProductById(itemSale.productId)
                if (product != null && itemSale.quantityKg > BigDecimal.ZERO) {
                    val restoredStock = product.currentStockKg.add(itemSale.quantityKg)
                        .setScale(CurrencyUtils.WEIGHT_SCALE, CurrencyUtils.ROUNDING)
                    productDao.updateProductStock(product.id, restoredStock.toPlainString())
                }

                val prevSummary = "${CurrencyUtils.formatWeight(itemSale.quantityKg)} kg • Total ${CurrencyUtils.formatInr(itemSale.totalSalePrice)} (Udhari ${CurrencyUtils.formatInr(itemSale.udhariAmount)})"
                val newSummary = "CANCELLED • Stock Restored (+${CurrencyUtils.formatWeight(itemSale.quantityKg)} kg)"

                val updatedSale = itemSale.copy(
                    udhariAmount = BigDecimal.ZERO,
                    billStatus = "CANCELLED",
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = SyncStatus.PENDING
                )
                saleDao.insertSale(updatedSale)
                if (itemSale.id == existing.id || updatedPrimary == null) {
                    updatedPrimary = updatedSale
                }

                billAuditLogDao.insertAuditLog(
                    BillAuditLog(
                        saleId = itemSale.id,
                        customerId = itemSale.customerId,
                        customerName = customer?.name ?: "Customer",
                        productName = product?.feedName ?: "Feed Item",
                        actionType = "CANCEL",
                        previousSummary = prevSummary,
                        newSummary = newSummary,
                        reason = reason.trim().ifBlank { "Bill cancelled" },
                        performedByUserId = performedBy.id,
                        performedByUserName = performedBy.name
                    )
                )
            }

            recalculateCustomerUdhariInternal(existing.customerId)
            didModify = true
            updatedPrimary
        }
        if (didModify) {
            triggerAutoSync()
        }
        primaryUpdated
    }
}

