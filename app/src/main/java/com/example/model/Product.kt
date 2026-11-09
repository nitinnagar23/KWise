package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@Entity(tableName = "products")
data class Product(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val feedName: String,
    val baseUnit: String = "kg",
    val kgPerBag: BigDecimal = BigDecimal("50"),
    val currentStockKg: BigDecimal = BigDecimal.ZERO,
    val averagePurchaseCostPerKg: BigDecimal = BigDecimal.ZERO,
    val sellingPricePerKg: BigDecimal = BigDecimal.ZERO, // Base per-kg selling rate (used for whole bags: bagPrice = sellingPricePerKg * kgPerBag)
    val looseSellingPricePerKg: BigDecimal = BigDecimal.ZERO, // Custom loose selling rate per kg (e.g., ₹50/kg vs ₹49/kg bag rate)
    val isLooseOnly: Boolean = false, // True for loose commodities like Chana, Bajra, Makka
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.PENDING
) {
    /**
     * Effective loose rate per kg. If looseSellingPricePerKg is not explicitly set (> 0),
     * defaults to sellingPricePerKg.
     */
    val effectiveLooseRatePerKg: BigDecimal
        get() = if (looseSellingPricePerKg > BigDecimal.ZERO) looseSellingPricePerKg else sellingPricePerKg

    /**
     * Total selling price of 1 full bag.
     * If product is loose-only or bag weight is <= 1kg, returns the per-kg rate.
     */
    val bagSellingPrice: BigDecimal
        get() = if (!isLooseOnly && kgPerBag > BigDecimal.ONE) {
            sellingPricePerKg.multiply(kgPerBag).setScale(2, RoundingMode.HALF_UP)
        } else {
            effectiveLooseRatePerKg
        }

    /**
     * Rate for 1 kg loose.
     */
    val per1KgPrice: BigDecimal
        get() = effectiveLooseRatePerKg.setScale(2, RoundingMode.HALF_UP)

    /**
     * Rate for 5 kg loose.
     */
    val per5KgPrice: BigDecimal
        get() = effectiveLooseRatePerKg.multiply(BigDecimal(5)).setScale(2, RoundingMode.HALF_UP)
}

