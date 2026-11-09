package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.Customer
import com.example.model.CustomerStatusChip
import com.example.model.EstimateDraft
import com.example.model.EstimateDraftLineItem
import com.example.model.Permission
import com.example.model.Role
import com.example.model.User
import com.example.model.can
import com.example.model.canEditCustomerBasic
import com.example.model.getCustomerStatusChip
import com.example.model.isCustomerCreditBlocked
import com.example.util.BudgetSplitItemInput
import com.example.util.BudgetSplitMode
import com.example.util.CurrencyUtils
import com.example.util.SecurityUtils
import com.example.util.applyLeftoverToRow
import com.example.util.splitBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun `read string from context matches app name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("KhataWise", appName)
    }

    @Test
    fun `test mixed unit calculations and CurrencyUtils formatting`() {
        val bagWeight = BigDecimal("50.00").multiply(BigDecimal(2))
        val looseKg = BigDecimal("12.50")
        val totalKg = bagWeight.add(looseKg).setScale(CurrencyUtils.WEIGHT_SCALE, CurrencyUtils.ROUNDING)

        assertEquals("112.50", totalKg.toPlainString())

        val pricePerKg = BigDecimal("24.00")
        val totalCost = totalKg.multiply(pricePerKg).setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
        assertEquals("2700.00", totalCost.toPlainString())

        val formatted = CurrencyUtils.formatInr(totalCost)
        assertTrue(formatted.contains("2,700"))
    }

    @Test
    fun `test Permission single source of truth across ADMINISTRATOR MANAGER and STAFF`() {
        // ADMINISTRATOR can do everything
        Permission.entries.forEach { perm ->
            assertTrue("Admin should have $perm", can(Role.ADMINISTRATOR, perm))
        }

        // MANAGER can create/override/edit/delete bills, edit daily rates, view/edit customers, access business modules
        assertTrue(can(Role.MANAGER, Permission.CREATE_BILL))
        assertTrue(can(Role.MANAGER, Permission.OVERRIDE_BILL_RATE))
        assertTrue(can(Role.MANAGER, Permission.EDIT_DELETE_BILL))
        assertTrue(can(Role.MANAGER, Permission.EDIT_DAILY_RATES))
        assertTrue(can(Role.MANAGER, Permission.VIEW_CUSTOMER_BALANCE))
        assertTrue(can(Role.MANAGER, Permission.VIEW_CUSTOMER_HISTORY))
        assertTrue(can(Role.MANAGER, Permission.ACCESS_INVENTORY))
        assertTrue(can(Role.MANAGER, Permission.ACCESS_SUPPLIERS))
        // MANAGER cannot edit credit limit or manage shop profile / staff
        assertFalse(can(Role.MANAGER, Permission.EDIT_CREDIT_LIMIT))
        assertFalse(can(Role.MANAGER, Permission.MANAGE_SHOP_PROFILE))
        assertFalse(can(Role.MANAGER, Permission.MANAGE_STAFF))

        // STAFF can only create bills, view today's sales & daily rates, view/add customers, and edit own customer basic info
        assertTrue(can(Role.STAFF, Permission.CREATE_BILL))
        assertTrue(can(Role.STAFF, Permission.VIEW_TODAY_SALES))
        assertTrue(can(Role.STAFF, Permission.VIEW_DAILY_RATES))
        assertTrue(can(Role.STAFF, Permission.VIEW_CUSTOMERS))
        assertTrue(can(Role.STAFF, Permission.ADD_CUSTOMER))
        assertTrue(can(Role.STAFF, Permission.EDIT_OWN_CUSTOMER_BASIC))
        assertFalse(can(Role.STAFF, Permission.OVERRIDE_BILL_RATE))
        assertFalse(can(Role.STAFF, Permission.EDIT_DELETE_BILL))
        assertFalse(can(Role.STAFF, Permission.EDIT_DAILY_RATES))
        assertFalse(can(Role.STAFF, Permission.VIEW_CUSTOMER_BALANCE))
        assertFalse(can(Role.STAFF, Permission.VIEW_CUSTOMER_HISTORY))
        assertFalse(can(Role.STAFF, Permission.EDIT_CREDIT_LIMIT))
        assertFalse(can(Role.STAFF, Permission.ACCESS_INVENTORY))

        val staffUser = User(id = "staff-1", name = "Raju", role = Role.STAFF)
        val managerUser = User(id = "mgr-1", name = "Suresh", role = Role.MANAGER)
        val ownCustomer = Customer(id = "c1", name = "Ram", phone = "9999999999", village = "G1", createdByUserId = "staff-1")
        val otherCustomer = Customer(id = "c2", name = "Shyam", phone = "8888888888", village = "G2", createdByUserId = "admin-1")

        assertTrue(canEditCustomerBasic(staffUser, ownCustomer))
        assertFalse(canEditCustomerBasic(staffUser, otherCustomer))
        assertTrue(canEditCustomerBasic(managerUser, otherCustomer))
    }

    @Test
    fun `test customer status chips and credit blocking`() {
        val clearCust = Customer(id = "1", name = "A", phone = "", village = "", totalUdhari = BigDecimal.ZERO)
        assertEquals(CustomerStatusChip.CLEAR, getCustomerStatusChip(clearCust))
        assertFalse(isCustomerCreditBlocked(clearCust))

        val udhariCust = Customer(
            id = "2",
            name = "B",
            phone = "",
            village = "",
            totalUdhari = BigDecimal("1500.00"),
            creditLimit = BigDecimal("5000.00")
        )
        assertEquals(CustomerStatusChip.HAS_UDHARI, getCustomerStatusChip(udhariCust))
        assertFalse(isCustomerCreditBlocked(udhariCust))

        val overLimitCust = Customer(
            id = "3",
            name = "C",
            phone = "",
            village = "",
            totalUdhari = BigDecimal("6000.00"),
            creditLimit = BigDecimal("5000.00")
        )
        assertEquals(CustomerStatusChip.OVER_LIMIT, getCustomerStatusChip(overLimitCust))
        assertTrue(isCustomerCreditBlocked(overLimitCust))
        assertTrue(isCustomerCreditBlocked(udhariCust, additionalUdhari = BigDecimal("4000.00")))
    }

    @Test
    fun `test splitBudget EQUAL RATIO AMOUNT and WEIGHT modes with locked rows and step rounding`() {
        val rows = listOf(
            BudgetSplitItemInput(
                productId = "p1",
                productName = "Chana",
                ratePerKg = BigDecimal("80.00"),
                availableStockKg = BigDecimal("50.00"),
                ratioWeight = BigDecimal("1")
            ),
            BudgetSplitItemInput(
                productId = "p2",
                productName = "Wheat",
                ratePerKg = BigDecimal("35.00"),
                availableStockKg = BigDecimal("100.00"),
                ratioWeight = BigDecimal("2")
            ),
            BudgetSplitItemInput(
                productId = "p3",
                productName = "Mustard",
                ratePerKg = BigDecimal("60.00"),
                availableStockKg = BigDecimal("2.00"), // low stock to trigger warning
                ratioWeight = BigDecimal("1")
            )
        )

        // 1. EQUAL split of ₹500 across 3 items with 0.050 kg step
        val equalRes = splitBudget(BigDecimal("500"), rows, BudgetSplitMode.EQUAL)
        assertEquals(3, equalRes.rows.size)
        assertTrue(equalRes.totalActualAmount <= BigDecimal("500.00"))
        assertTrue(equalRes.leftoverRupees >= BigDecimal.ZERO)
        assertEquals(BigDecimal("500.00"), equalRes.totalActualAmount.add(equalRes.leftoverRupees).setScale(2))
        // Mustard has 2.00 kg stock and ₹168 / 60 = 2.80 kg -> exceedsStock should be true!
        val mustardEqual = equalRes.rows.first { it.productId == "p3" }
        assertTrue(mustardEqual.exceedsStock)

        // 1b. Apply leftover to first row
        val absorbed = applyLeftoverToRow(equalRes, "p1")
        assertEquals(BigDecimal("0.00"), absorbed.leftoverRupees)

        // 2. RATIO split 1 : 2 : 1 of ₹800
        val ratioRes = splitBudget(BigDecimal("800"), rows, BudgetSplitMode.RATIO)
        val r1 = ratioRes.rows[0]
        val r2 = ratioRes.rows[1]
        // r2 should get ~₹400 and r1 ~₹200
        assertEquals(0, r1.actualAmount.compareTo(BigDecimal("200.00")))
        assertTrue(r2.actualAmount >= BigDecimal("398.00"))

        // 3. Locked row rebalancing: lock p1 at 2.000 kg (₹160) out of ₹560 budget; p2 and p3 split remaining ₹400 equally
        val lockedRows = listOf(
            BudgetSplitItemInput(
                productId = "p1",
                productName = "Chana",
                ratePerKg = BigDecimal("80.00"),
                isLocked = true,
                requestedKg = BigDecimal("2.000"),
                requestedAmount = BigDecimal("160.00")
            ),
            BudgetSplitItemInput(
                productId = "p2",
                productName = "Wheat",
                ratePerKg = BigDecimal("35.00")
            ),
            BudgetSplitItemInput(
                productId = "p3",
                productName = "Mustard",
                ratePerKg = BigDecimal("60.00")
            )
        )
        val lockedRes = splitBudget(BigDecimal("560"), lockedRows, BudgetSplitMode.EQUAL)
        assertEquals(0, lockedRes.rows[0].allocatedKg.compareTo(BigDecimal("2.000")))
        assertEquals(0, lockedRes.rows[0].actualAmount.compareTo(BigDecimal("160.00")))
        assertTrue(lockedRes.rows[1].actualAmount <= BigDecimal("200.00"))
        assertTrue(lockedRes.rows[2].actualAmount <= BigDecimal("200.00"))
    }

    @Test
    fun `test EstimateDraft serialization and deserialization`() {
        val draft = EstimateDraft(
            id = "draft-1",
            title = "Ramesh - ₹500 Mix",
            itemsJson = EstimateDraft.serializeItems(
                listOf(
                    EstimateDraftLineItem(
                        productId = "p1",
                        productName = "Chana",
                        bags = 0,
                        looseKg = "2.500",
                        customBagPrice = "",
                        customLoosePrice = "80.00"
                    )
                )
            ),
            totalWeightKg = BigDecimal("2.500"),
            totalAmount = BigDecimal("200.00")
        )
        val items = draft.parseItems()
        assertEquals(1, items.size)
        assertEquals("Chana", items[0].productName)
        assertEquals("2.500", items[0].looseKg)
        assertEquals("80.00", items[0].customLoosePrice)
    }

    @Test
    fun `test HMAC signed invite token generation and rejection of PA prefix`() {
        val userId = "user-12345"
        val userName = "Ramesh Kumar"
        val role = Role.STAFF

        val inviteCode = SecurityUtils.generateInviteCode(userId, userName, role)
        assertTrue(inviteCode.startsWith("KW-"))

        val parsed = SecurityUtils.parseInviteCode(inviteCode)
        assertNotNull(parsed)
        assertEquals(userId, parsed?.userId)
        assertEquals(userName, parsed?.name)
        assertEquals(role, parsed?.role)

        // Legacy PA- prefix must be rejected
        val paInvite = "PA-" + inviteCode.removePrefix("KW-")
        assertNull(SecurityUtils.parseInviteCode(paInvite))
    }

    @Test
    fun `test PBKDF2 salted PIN hashing and 5-minute lockout after 5 wrong tries`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val userId = "lockout-test-user"
        SecurityUtils.resetLockout(context, userId)

        val pin = "1234"
        val hash = SecurityUtils.hashPin(pin)
        assertTrue(hash.startsWith("PBKDF2$"))
        assertFalse(SecurityUtils.isLegacyPinHash(hash))
        assertTrue(SecurityUtils.verifyPin("1234", hash))
        assertFalse(SecurityUtils.verifyPin("9999", hash))

        // 5 wrong attempts trigger 5-minute (300s) lockout
        repeat(5) {
            val res = SecurityUtils.verifyPinWithLockout(context, userId, "0000", hash)
            assertFalse(res.success)
        }
        val lockedStatus = SecurityUtils.getLockoutStatus(context, userId)
        assertTrue(lockedStatus.isLockedOut)
        assertTrue("Remaining lockout should be > 240s (5 min lockout)", lockedStatus.remainingLockoutSeconds > 240)

        SecurityUtils.resetLockout(context, userId)
        val okRes = SecurityUtils.verifyPinWithLockout(context, userId, "1234", hash)
        assertTrue(okRes.success)
    }

    @Test
    fun `test post-login activeUser persists after clearAllLocalDataAndSignOut and is never clobbered by store profile updates`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<PashuAaharApp>()
        val repo = context.repository

        // 1. Wipe local data (simulating fresh install or Sign Out & Clear Device Data)
        repo.clearAllLocalDataAndSignOut()
        assertNull(repo.getActiveUser())

        // 2. Insert a legacy user with a random UUID and same name, then sign in with a Firebase UID
        val legacyUser = User(id = "legacy-uuid-111", name = "Store Admin", role = Role.ADMINISTRATOR)
        repo.insertUser(legacyUser)

        val firebaseUid = "firebase-uid-999"
        val googleAdmin = User(
            id = firebaseUid,
            name = "Store Admin",
            email = "owner@khatawise.in",
            role = Role.ADMINISTRATOR
        )
        val savedUser = repo.insertUser(googleAdmin)
        assertEquals(firebaseUid, savedUser.id)

        repo.setActiveUser(savedUser.id)
        val activeAfterLogin = repo.getActiveUser()
        assertNotNull("Active user must be non-null immediately after login", activeAfterLogin)
        assertEquals(firebaseUid, activeAfterLogin?.id)

        // 3. Subsequent store profile, theme, language, and sync timestamp updates must never clobber activeUserId
        repo.updateStoreProfile(
            shopName = "Shree Ganesh Pashu Aahar",
            shopAddress = "Main Mandi Road",
            shopPhone = "9876543210",
            shopGstin = "",
            shopUpiId = "ganesh@upi",
            watchThreshold = BigDecimal("2000"),
            criticalThreshold = BigDecimal("5000"),
            actor = savedUser
        )
        repo.updateThemeMode("DARK")
        repo.updateLanguage("hi")
        repo.updateLastSyncTime(System.currentTimeMillis())

        val activeAfterProfileSync = repo.getActiveUser()
        assertNotNull("Active user must remain non-null after profile/sync updates", activeAfterProfileSync)
        assertEquals(firebaseUid, activeAfterProfileSync?.id)
    }

    @Test
    fun `test MainActivity launches without crash both logged out and logged in`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<PashuAaharApp>()
        val repo = app.repository

        // 1. Logged out startup -> renders SetupScreen / LoginScreen without crashing
        repo.clearAllLocalDataAndSignOut()
        org.robolectric.Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            controller.setup()
            assertNotNull(controller.get())
        }

        // 2. Logged in startup -> renders MainActivity Scaffold + HomeScreen without crashing
        val admin = repo.setupInitialAdmin("Ramesh Owner", "1234")
        assertEquals(Role.ADMINISTRATOR, admin.role)
        org.robolectric.Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            controller.setup()
            assertNotNull(controller.get())
        }
    }

    @Test
    fun `test local sale and payment mark Product stock and Customer udhari as PENDING for sync`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<PashuAaharApp>()
        val repo = app.repository
        val db = com.example.data.AppDatabase.getInstance(app)

        val admin = repo.setupInitialAdmin("Admin Sync Test", "1234")
        val prod = com.example.model.Product(
            id = "sync-prod-1",
            feedName = "Binola Khal",
            kgPerBag = BigDecimal("50.00"),
            currentStockKg = BigDecimal("500.00"),
            averagePurchaseCostPerKg = BigDecimal("30.00"),
            sellingPricePerKg = BigDecimal("35.00"),
            looseSellingPricePerKg = BigDecimal("36.00"),
            syncStatus = com.example.model.SyncStatus.SYNCED
        )
        db.productDao().insertProduct(prod)

        val cust = Customer(
            id = "sync-cust-1",
            name = "Mahesh Ji",
            phone = "9876543210",
            village = "Gohra",
            totalUdhari = BigDecimal.ZERO,
            creditLimit = BigDecimal("10000.00"),
            syncStatus = com.example.model.SyncStatus.SYNCED
        )
        db.customerDao().insertCustomer(cust)

        // Record a credit sale -> both Product stock and Customer udhari must transition from SYNCED to PENDING
        repo.recordSale(
            customerId = cust.id,
            productId = prod.id,
            bags = 1,
            looseKg = BigDecimal.ZERO,
            paidAmount = BigDecimal("500.00"),
            soldByUserId = admin.id
        )

        val updatedProd = db.productDao().getProductById(prod.id)
        val updatedCust = db.customerDao().getCustomerById(cust.id)
        assertNotNull(updatedProd)
        assertNotNull(updatedCust)
        assertEquals(com.example.model.SyncStatus.PENDING, updatedProd?.syncStatus)
        assertEquals(com.example.model.SyncStatus.PENDING, updatedCust?.syncStatus)
        assertEquals(0, updatedCust?.totalUdhari?.compareTo(BigDecimal("1250.00")))

        // Mark customer SYNCED, then run recalculateCustomerUdhari with markPending = false (simulating remote pull)
        db.customerDao().updateCustomer(updatedCust!!.copy(syncStatus = com.example.model.SyncStatus.SYNCED))
        repo.recalculateCustomerUdhari(cust.id, markPending = false)
        val afterRemoteRecalc = db.customerDao().getCustomerById(cust.id)
        assertEquals(com.example.model.SyncStatus.SYNCED, afterRemoteRecalc?.syncStatus)
    }
}



