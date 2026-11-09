package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE isDeleted = 0")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): User?

    @Query("SELECT * FROM users WHERE name = :name LIMIT 1")
    suspend fun getUserByName(name: String): User?

    @Query("SELECT * FROM users WHERE email = :email AND email != '' LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    fun observeUserById(userId: String): Flow<User?>

    @Query("SELECT * FROM users WHERE isActive = 1 AND isDeleted = 0")
    suspend fun getActiveUsers(): List<User>

    @Query("SELECT * FROM users WHERE syncStatus != 'SYNCED'")
    suspend fun getUnsyncedUsers(): List<User>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<User>)

    @Update
    suspend fun updateUser(user: User)

    @Query("SELECT COUNT(*) FROM users WHERE isDeleted = 0")
    suspend fun getUserCount(): Int

    @Query("UPDATE users SET isDeleted = 1, isActive = 0, updatedAt = :updatedAt WHERE id = :userId")
    suspend fun softDeleteUser(userId: String, updatedAt: Long = System.currentTimeMillis())
}
