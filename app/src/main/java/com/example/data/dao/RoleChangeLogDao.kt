package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.model.RoleChangeLog
import kotlinx.coroutines.flow.Flow

@Dao
interface RoleChangeLogDao {
    @Query("SELECT * FROM role_change_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<RoleChangeLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: RoleChangeLog)
}
