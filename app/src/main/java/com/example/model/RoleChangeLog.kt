package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "role_change_logs")
data class RoleChangeLog(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val targetUserId: String,
    val targetUserName: String,
    val oldRole: Role?,
    val newRole: Role,
    val changedByUserId: String,
    val changedByUserName: String,
    val timestamp: Long = System.currentTimeMillis()
)
