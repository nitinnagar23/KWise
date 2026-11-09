package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.model.PaymentEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE isDeleted = 0 ORDER BY date DESC")
    fun getAllPayments(): Flow<List<PaymentEntry>>

    @Query("SELECT * FROM payments WHERE isDeleted = 0 AND customerId = :customerId ORDER BY date DESC")
    fun getPaymentsByCustomer(customerId: String): Flow<List<PaymentEntry>>

    @Query("SELECT * FROM payments WHERE isDeleted = 0 AND customerId = :customerId")
    suspend fun getPaymentsListByCustomer(customerId: String): List<PaymentEntry>

    @Query("SELECT * FROM payments WHERE isDeleted = 0 AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getPaymentsBetween(startDate: Long, endDate: Long): List<PaymentEntry>

    @Query("SELECT * FROM payments WHERE syncStatus != 'SYNCED'")
    suspend fun getUnsyncedPayments(): List<PaymentEntry>

    @Query("SELECT * FROM payments WHERE id = :paymentId LIMIT 1")
    suspend fun getPaymentById(paymentId: String): PaymentEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(payments: List<PaymentEntry>)

    @Query("UPDATE payments SET isDeleted = 1, updatedAt = :updatedAt, syncStatus = 'PENDING' WHERE id = :paymentId")
    suspend fun softDeletePayment(paymentId: String, updatedAt: Long = System.currentTimeMillis())
}
