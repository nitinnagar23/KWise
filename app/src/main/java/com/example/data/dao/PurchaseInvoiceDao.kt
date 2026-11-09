package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.model.PurchaseInvoice
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseInvoiceDao {
    @Query("SELECT * FROM purchase_invoices WHERE isDeleted = 0 ORDER BY date DESC")
    fun getAllInvoices(): Flow<List<PurchaseInvoice>>

    @Query("SELECT * FROM purchase_invoices WHERE isDeleted = 0 ORDER BY date DESC")
    suspend fun getAllInvoicesList(): List<PurchaseInvoice>

    @Query("SELECT * FROM purchase_invoices WHERE isDeleted = 0 AND wholesalerName = :wholesalerName ORDER BY date DESC")
    fun getInvoicesForWholesaler(wholesalerName: String): Flow<List<PurchaseInvoice>>

    @Query("SELECT * FROM purchase_invoices WHERE isDeleted = 0 AND wholesalerName = :wholesalerName ORDER BY date ASC")
    suspend fun getInvoicesListForWholesaler(wholesalerName: String): List<PurchaseInvoice>

    @Query("SELECT * FROM purchase_invoices WHERE isDeleted = 0 AND id = :id LIMIT 1")
    suspend fun getInvoiceById(id: String): PurchaseInvoice?

    @Query("SELECT * FROM purchase_invoices WHERE syncStatus != 'SYNCED'")
    suspend fun getUnsyncedInvoices(): List<PurchaseInvoice>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: PurchaseInvoice)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoices(invoices: List<PurchaseInvoice>)

    @Query("UPDATE purchase_invoices SET isDeleted = 1, updatedAt = :updatedAt, syncStatus = 'PENDING' WHERE id = :invoiceId")
    suspend fun softDeleteInvoice(invoiceId: String, updatedAt: Long = System.currentTimeMillis())
}
