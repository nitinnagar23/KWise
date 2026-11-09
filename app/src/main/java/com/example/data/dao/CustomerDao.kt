package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.Customer
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE isDeleted = 0 AND CAST(totalUdhari AS REAL) > 0 ORDER BY CAST(totalUdhari AS REAL) DESC")
    fun getCustomersWithUdhari(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE id = :customerId LIMIT 1")
    suspend fun getCustomerById(customerId: String): Customer?

    @Query("SELECT * FROM customers WHERE id = :customerId LIMIT 1")
    fun observeCustomerById(customerId: String): Flow<Customer?>

    @Query("SELECT * FROM customers WHERE syncStatus != 'SYNCED'")
    suspend fun getUnsyncedCustomers(): List<Customer>

    @Query("SELECT * FROM customers WHERE isDeleted = 0 AND promiseDate IS NOT NULL AND promiseDate >= :startTime AND promiseDate <= :endTime")
    suspend fun getCustomersWithPromiseDateInRange(startTime: Long, endTime: Long): List<Customer>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<Customer>)

    @Update
    suspend fun updateCustomer(customer: Customer)

    @Query("UPDATE customers SET totalUdhari = :totalUdhari, updatedAt = :updatedAt, syncStatus = :syncStatus WHERE id = :customerId")
    suspend fun updateCustomerUdhari(
        customerId: String,
        totalUdhari: String,
        updatedAt: Long = System.currentTimeMillis(),
        syncStatus: String = "PENDING"
    )

    @Query("UPDATE customers SET isDeleted = 1, updatedAt = :updatedAt, syncStatus = 'PENDING' WHERE id = :customerId")
    suspend fun softDeleteCustomer(customerId: String, updatedAt: Long = System.currentTimeMillis())
}
