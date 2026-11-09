package com.example.model

import java.math.BigDecimal

data class BillItem(
    val feedName: String,
    val bags: Int,
    val looseKg: BigDecimal,
    val totalKg: BigDecimal,
    val pricePerKg: BigDecimal,
    val itemTotal: BigDecimal
)

data class BillData(
    val billNumber: String,
    val date: Long,
    val customerName: String,
    val customerPhone: String,
    val customerVillage: String,
    val items: List<BillItem>,
    val subtotal: BigDecimal,
    val paidAmount: BigDecimal,
    val udhariAmount: BigDecimal,
    val previousUdhari: BigDecimal,
    val newTotalUdhari: BigDecimal,
    val staffName: String
)
