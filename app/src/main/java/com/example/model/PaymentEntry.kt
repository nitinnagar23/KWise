package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.util.UUID

@Entity(tableName = "payments")
data class PaymentEntry(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val date: Long = System.currentTimeMillis(),
    val customerId: String,
    val amount: BigDecimal,
    val linkedSaleId: String? = null,
    val note: String = "",
    val collectedByUserId: String,
    val paymentMode: String = "CASH",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.PENDING
)
