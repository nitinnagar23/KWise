package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.model.PurchaseEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Query("SELECT * FROM purchases WHERE isDeleted = 0 ORDER BY date DESC")
    fun getAllPurchases(): Flow<List<PurchaseEntry>>

    @Query("SELECT * FROM purchases WHERE isDeleted = 0 AND productId = :productId ORDER BY date ASC")
    suspend fun getPurchasesForProduct(productId: String): List<PurchaseEntry>

    @Query("SELECT * FROM purchases WHERE isDeleted = 0 AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getPurchasesBetween(startDate: Long, endDate: Long): List<PurchaseEntry>

    @Query("SELECT * FROM purchases WHERE syncStatus != 'SYNCED'")
    suspend fun getUnsyncedPurchases(): List<PurchaseEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: PurchaseEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchases(purchases: List<PurchaseEntry>)

    @Query("UPDATE purchases SET isDeleted = 1, updatedAt = :updatedAt, syncStatus = 'PENDING' WHERE id = :purchaseId")
    suspend fun softDeletePurchase(purchaseId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT SUM(CAST(quantityKg AS REAL)) FROM purchases WHERE isDeleted = 0 AND productId = :productId")
    suspend fun getTotalPurchasedKgForProduct(productId: String): Double?
}
