package com.example.data

import androidx.room.TypeConverter
import com.example.model.PaymentStatus
import com.example.model.Role
import com.example.model.SyncStatus
import java.math.BigDecimal

class Converters {
    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? {
        return value?.toPlainString()
    }

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? {
        return value?.let {
            try {
                BigDecimal(it)
            } catch (e: Exception) {
                BigDecimal.ZERO
            }
        }
    }

    @TypeConverter
    fun fromRole(role: Role?): String? = role?.name

    @TypeConverter
    fun toRole(value: String?): Role? = value?.let {
        try {
            Role.valueOf(it)
        } catch (e: Exception) {
            Role.STAFF
        }
    }

    @TypeConverter
    fun fromSyncStatus(status: SyncStatus?): String? = status?.name

    @TypeConverter
    fun toSyncStatus(value: String?): SyncStatus? = value?.let {
        try {
            SyncStatus.valueOf(it)
        } catch (e: Exception) {
            SyncStatus.PENDING
        }
    }

    @TypeConverter
    fun fromPaymentStatus(status: PaymentStatus?): String? = status?.name

    @TypeConverter
    fun toPaymentStatus(value: String?): PaymentStatus? = value?.let {
        try {
            PaymentStatus.valueOf(it)
        } catch (e: Exception) {
            PaymentStatus.UNPAID
        }
    }
}
