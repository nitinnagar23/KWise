package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.Product
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE isDeleted = 0 ORDER BY feedName ASC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    suspend fun getProductById(productId: String): Product?

    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    fun observeProductById(productId: String): Flow<Product?>

    @Query("SELECT * FROM products WHERE syncStatus != 'SYNCED'")
    suspend fun getUnsyncedProducts(): List<Product>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<Product>)

    @Update
    suspend fun updateProduct(product: Product)

    @Query("UPDATE products SET currentStockKg = :stockKg, updatedAt = :updatedAt, syncStatus = 'PENDING' WHERE id = :productId")
    suspend fun updateProductStock(productId: String, stockKg: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE products SET currentStockKg = :stockKg, averagePurchaseCostPerKg = :avgCost, updatedAt = :updatedAt, syncStatus = 'PENDING' WHERE id = :productId")
    suspend fun updateProductStockAndAvgCost(productId: String, stockKg: String, avgCost: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE products SET sellingPricePerKg = :sellingPrice, looseSellingPricePerKg = :loosePrice, updatedAt = :updatedAt, syncStatus = 'PENDING' WHERE id = :productId")
    suspend fun updateDailyRates(productId: String, sellingPrice: BigDecimal, loosePrice: BigDecimal, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE products SET isDeleted = 1, updatedAt = :updatedAt, syncStatus = 'PENDING' WHERE id = :productId")
    suspend fun softDeleteProduct(productId: String, updatedAt: Long = System.currentTimeMillis())
}
