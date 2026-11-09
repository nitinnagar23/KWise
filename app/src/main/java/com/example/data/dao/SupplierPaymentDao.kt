package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.model.SupplierPayment
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierPaymentDao {
    @Query("SELECT * FROM supplier_payments WHERE isDeleted = 0 ORDER BY date DESC")
    fun getAllPayments(): Flow<List<SupplierPayment>>

    @Query("SELECT * FROM supplier_payments WHERE isDeleted = 0 ORDER BY date DESC")
    suspend fun getAllPaymentsList(): List<SupplierPayment>

    @Query("SELECT * FROM supplier_payments WHERE isDeleted = 0 AND wholesalerName = :wholesalerName ORDER BY date DESC")
    fun getPaymentsForWholesaler(wholesalerName: String): Flow<List<SupplierPayment>>

    @Query("SELECT * FROM supplier_payments WHERE isDeleted = 0 AND wholesalerName = :wholesalerName ORDER BY date ASC")
    suspend fun getPaymentsListForWholesaler(wholesalerName: String): List<SupplierPayment>

    @Query("SELECT * FROM supplier_payments WHERE syncStatus != 'SYNCED'")
    suspend fun getUnsyncedPayments(): List<SupplierPayment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: SupplierPayment)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(payments: List<SupplierPayment>)

    @Query("UPDATE supplier_payments SET isDeleted = 1, updatedAt = :updatedAt, syncStatus = 'PENDING' WHERE id = :paymentId")
    suspend fun softDeletePayment(paymentId: String, updatedAt: Long = System.currentTimeMillis())
}
