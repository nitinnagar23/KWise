package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.model.BillAuditLog
import kotlinx.coroutines.flow.Flow

@Dao
interface BillAuditLogDao {
    @Query("SELECT * FROM bill_audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogs(): Flow<List<BillAuditLog>>

    @Query("SELECT * FROM bill_audit_logs WHERE saleId = :saleId ORDER BY timestamp DESC")
    fun getAuditLogsForSale(saleId: String): Flow<List<BillAuditLog>>

    @Query("SELECT * FROM bill_audit_logs ORDER BY timestamp DESC LIMIT 250")
    suspend fun getRecentAuditLogsList(): List<BillAuditLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: BillAuditLog)
}
