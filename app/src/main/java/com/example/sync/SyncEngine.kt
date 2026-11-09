package com.example.sync

import android.content.Context
import com.example.data.AppDatabase
import com.example.model.Customer
import com.example.model.PaymentEntry
import com.example.model.Product
import com.example.model.PurchaseEntry
import com.example.model.SaleEntry
import com.example.model.SyncStatus
import com.example.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

sealed class SyncResult {
    data class Success(val message: String, val syncedCount: Int) : SyncResult()
    data class Error(val errorMessage: String) : SyncResult()
    object NoUrlConfigured : SyncResult()
}

class SyncEngine(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun syncNow(): SyncResult = withContext(Dispatchers.IO) {
        val settings = db.settingsDao().getSettings()
        val url = settings?.appsScriptUrl?.trim()
        if (url.isNullOrBlank()) {
            return@withContext SyncResult.NoUrlConfigured
        }

        try {
            val unsyncedCustomers = db.customerDao().getUnsyncedCustomers()
            val unsyncedSales = db.saleDao().getUnsyncedSales()
            val unsyncedPayments = db.paymentDao().getUnsyncedPayments()
            val unsyncedPurchases = db.purchaseDao().getUnsyncedPurchases()
            val unsyncedUsers = db.userDao().getUnsyncedUsers()

            val totalItems = unsyncedCustomers.size + unsyncedSales.size +
                    unsyncedPayments.size + unsyncedPurchases.size + unsyncedUsers.size

            val payload = JSONObject().apply {
                put("customers", JSONArray().apply {
                    unsyncedCustomers.forEach { c ->
                        put(JSONObject().apply {
                            put("id", c.id)
                            put("name", c.name)
                            put("phone", c.phone)
                            put("village", c.village)
                            put("photoDriveUrl", c.photoDriveUrl ?: "")
                            put("totalUdhari", c.totalUdhari.toPlainString())
                            put("promiseDate", c.promiseDate ?: 0L)
                            put("createdByUserId", c.createdByUserId)
                            put("createdAt", c.createdAt)
                            put("updatedAt", c.updatedAt)
                            put("isDeleted", c.isDeleted)
                        })
                    }
                })

                put("sales", JSONArray().apply {
                    unsyncedSales.forEach { s ->
                        put(JSONObject().apply {
                            put("id", s.id)
                            put("date", s.date)
                            put("customerId", s.customerId)
                            put("productId", s.productId)
                            put("quantityKg", s.quantityKg.toPlainString())
                            put("pricePerKgAtSale", s.pricePerKgAtSale.toPlainString())
                            put("costPerKgAtSale", s.costPerKgAtSale.toPlainString())
                            put("totalSalePrice", s.totalSalePrice.toPlainString())
                            put("paidAmount", s.paidAmount.toPlainString())
                            put("udhariAmount", s.udhariAmount.toPlainString())
                            put("paymentStatus", s.paymentStatus.name)
                            put("soldByUserId", s.soldByUserId)
                            put("createdAt", s.createdAt)
                            put("updatedAt", s.updatedAt)
                            put("isDeleted", s.isDeleted)
                        })
                    }
                })

                put("payments", JSONArray().apply {
                    unsyncedPayments.forEach { p ->
                        put(JSONObject().apply {
                            put("id", p.id)
                            put("date", p.date)
                            put("customerId", p.customerId)
                            put("amount", p.amount.toPlainString())
                            put("linkedSaleId", p.linkedSaleId ?: "")
                            put("note", p.note)
                            put("collectedByUserId", p.collectedByUserId)
                            put("createdAt", p.createdAt)
                            put("updatedAt", p.updatedAt)
                            put("isDeleted", p.isDeleted)
                        })
                    }
                })

                put("purchases", JSONArray().apply {
                    unsyncedPurchases.forEach { p ->
                        put(JSONObject().apply {
                            put("id", p.id)
                            put("date", p.date)
                            put("wholesalerName", p.wholesalerName)
                            put("productId", p.productId)
                            put("quantityKg", p.quantityKg.toPlainString())
                            put("totalPurchaseCost", p.totalPurchaseCost.toPlainString())
                            put("paymentStatus", p.paymentStatus)
                            put("recordedByUserId", p.recordedByUserId)
                            put("createdAt", p.createdAt)
                            put("updatedAt", p.updatedAt)
                            put("isDeleted", p.isDeleted)
                        })
                    }
                })

                put("users", JSONArray().apply {
                    unsyncedUsers.forEach { u ->
                        put(JSONObject().apply {
                            put("id", u.id)
                            put("name", u.name)
                            put("role", u.role.name)
                            put("pinHash", u.pinHash)
                            put("isActive", u.isActive)
                            put("createdAt", u.createdAt)
                            put("updatedAt", u.updatedAt)
                            put("isDeleted", u.isDeleted)
                        })
                    }
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = payload.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext SyncResult.Error("HTTP Error: ${response.code}")
            }

            val respJson = JSONObject(responseBody)
            if (respJson.optString("status") != "success") {
                val msg = respJson.optString("message", "Unknown backend error")
                return@withContext SyncResult.Error(msg)
            }

            // Mark pushed records as SYNCED
            unsyncedCustomers.forEach { c ->
                db.customerDao().updateCustomer(c.copy(syncStatus = SyncStatus.SYNCED))
            }
            unsyncedSales.forEach { s ->
                db.saleDao().insertSale(s.copy(syncStatus = SyncStatus.SYNCED))
            }
            unsyncedPayments.forEach { p ->
                db.paymentDao().insertPayment(p.copy(syncStatus = SyncStatus.SYNCED))
            }
            unsyncedPurchases.forEach { p ->
                db.purchaseDao().insertPurchase(p.copy(syncStatus = SyncStatus.SYNCED))
            }
            unsyncedUsers.forEach { u ->
                db.userDao().updateUser(u.copy(syncStatus = SyncStatus.SYNCED))
            }

            // Process backend recalculated products (server authoritative stock & moving average cost)
            if (respJson.has("updatedProducts")) {
                val prodsArray = respJson.getJSONArray("updatedProducts")
                for (i in 0 until prodsArray.length()) {
                    val pObj = prodsArray.getJSONObject(i)
                    val pId = pObj.optString("id")
                    val stockStr = pObj.optString("currentStockKg", "")
                    val avgCostStr = pObj.optString("averagePurchaseCostPerKg", "")
                    if (pId.isNotBlank() && stockStr.isNotBlank()) {
                        val stockVal = BigDecimal(stockStr)
                        val avgCostVal = if (avgCostStr.isNotBlank()) BigDecimal(avgCostStr) else BigDecimal.ZERO
                        db.productDao().updateProductStockAndAvgCost(
                            productId = pId,
                            stockKg = stockVal.toPlainString(),
                            avgCost = avgCostVal.toPlainString()
                        )
                    }
                }
            }

            // Update sync timestamp
            val now = System.currentTimeMillis()
            db.settingsDao().updateSettings(
                (settings ?: com.example.model.AppSettings()).copy(lastSyncTime = now)
            )

            SyncResult.Success(
                message = "Synced $totalItems records with Google Sheets successfully",
                syncedCount = totalItems
            )
        } catch (e: Exception) {
            SyncResult.Error(e.localizedMessage ?: "Sync connection failed")
        }
    }
}
