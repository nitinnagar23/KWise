package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.util.UUID

@Entity(tableName = "purchases")
data class PurchaseEntry(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val date: Long = System.currentTimeMillis(),
    val wholesalerName: String,
    val productId: String,
    val quantityKg: BigDecimal,
    val totalPurchaseCost: BigDecimal,
    val paymentStatus: String = "PAID",
    val recordedByUserId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.PENDING
)
