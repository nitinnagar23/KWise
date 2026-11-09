package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.model.AppSettings
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun observeSettings(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: AppSettings)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ensureDefaultSettings(settings: AppSettings = AppSettings())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSettings(settings: AppSettings)

    @Query("UPDATE app_settings SET activeUserId = :userId WHERE id = 1")
    suspend fun updateActiveUserId(userId: String?)

    @Query(
        """
        UPDATE app_settings 
        SET shopName = :shopName,
            shopAddress = :shopAddress,
            shopPhone = :shopPhone,
            shopGstin = :shopGstin,
            shopUpiId = :shopUpiId,
            watchThreshold = :watchThreshold,
            criticalThreshold = :criticalThreshold
        WHERE id = 1
        """
    )
    suspend fun updateStoreProfileFields(
        shopName: String,
        shopAddress: String,
        shopPhone: String,
        shopGstin: String,
        shopUpiId: String,
        watchThreshold: BigDecimal,
        criticalThreshold: BigDecimal
    )

    @Query(
        """
        UPDATE app_settings 
        SET storeAdminUid = :storeAdminUid,
            shopName = :shopName,
            shopAddress = :shopAddress,
            shopPhone = :shopPhone
        WHERE id = 1
        """
    )
    suspend fun updatePairedStoreFields(
        storeAdminUid: String,
        shopName: String,
        shopAddress: String,
        shopPhone: String
    )

    @Query(
        """
        UPDATE app_settings 
        SET watchThreshold = :watchThreshold,
            criticalThreshold = :criticalThreshold,
            appsScriptUrl = :appsScriptUrl
        WHERE id = 1
        """
    )
    suspend fun updateThresholdsAndScript(
        watchThreshold: BigDecimal,
        criticalThreshold: BigDecimal,
        appsScriptUrl: String
    )

    @Query("UPDATE app_settings SET themeMode = :themeMode WHERE id = 1")
    suspend fun updateThemeModeField(themeMode: String)

    @Query("UPDATE app_settings SET language = :language WHERE id = 1")
    suspend fun updateLanguageField(language: String)

    @Query("UPDATE app_settings SET lastSyncTime = :timestamp WHERE id = 1")
    suspend fun updateLastSyncTime(timestamp: Long)
}

