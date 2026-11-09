package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.model.SaleEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales WHERE isDeleted = 0 ORDER BY date DESC")
    fun getAllSales(): Flow<List<SaleEntry>>

    @Query("SELECT * FROM sales WHERE isDeleted = 0 AND customerId = :customerId ORDER BY date DESC")
    fun getSalesByCustomer(customerId: String): Flow<List<SaleEntry>>

    @Query("SELECT * FROM sales WHERE isDeleted = 0 AND customerId = :customerId")
    suspend fun getSalesListByCustomer(customerId: String): List<SaleEntry>

    @Query("SELECT * FROM sales WHERE isDeleted = 0 AND soldByUserId = :userId ORDER BY date DESC")
    fun getSalesByUser(userId: String): Flow<List<SaleEntry>>

    @Query("SELECT * FROM sales WHERE isDeleted = 0 AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getSalesBetween(startDate: Long, endDate: Long): List<SaleEntry>

    @Query("SELECT * FROM sales WHERE isDeleted = 0 AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun observeSalesBetween(startDate: Long, endDate: Long): Flow<List<SaleEntry>>

    @Query("SELECT * FROM sales WHERE id = :saleId LIMIT 1")
    suspend fun getSaleById(saleId: String): SaleEntry?

    @Query("SELECT * FROM sales WHERE syncStatus != 'SYNCED'")
    suspend fun getUnsyncedSales(): List<SaleEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSales(sales: List<SaleEntry>)

    @Query("UPDATE sales SET isDeleted = 1, updatedAt = :updatedAt, syncStatus = 'PENDING' WHERE id = :saleId")
    suspend fun softDeleteSale(saleId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT SUM(CAST(quantityKg AS REAL)) FROM sales WHERE isDeleted = 0 AND productId = :productId")
    suspend fun getTotalSoldKgForProduct(productId: String): Double?
}
