package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "bill_audit_logs")
data class BillAuditLog(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val saleId: String,
    val customerId: String,
    val customerName: String,
    val productName: String,
    val actionType: String, // "EDIT", "RETURN", "CANCEL"
    val previousSummary: String,
    val newSummary: String,
    val reason: String = "",
    val performedByUserId: String,
    val performedByUserName: String,
    val timestamp: Long = System.currentTimeMillis()
)
