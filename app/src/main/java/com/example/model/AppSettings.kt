package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey
    val id: Int = 1,
    val shopName: String = "KhataWise Store",
    val shopAddress: String = "",
    val shopPhone: String = "",
    val shopGstin: String = "",
    val shopUpiId: String = "",
    val themeMode: String = "LIGHT", // "LIGHT", "DARK", "SYSTEM"
    val watchThreshold: BigDecimal = BigDecimal("2000"),
    val criticalThreshold: BigDecimal = BigDecimal("5000"),
    val appsScriptUrl: String = "",
    val activeUserId: String? = null,
    val lastSyncTime: Long = 0L,
    val storeAdminUid: String = "",
    val language: String = "en"
)
